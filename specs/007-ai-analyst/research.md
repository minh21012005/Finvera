# Research: AI Analyst

**Feature**: `007-ai-analyst`
**Status**: Complete for fixture-mode implementation. This feature is the
second to implement anything in `finvera-ai` (after Feature 006) and the
first to populate its `chat`, `analysis`, and `orchestration` packages;
several decisions here extend Feature 006's cross-feature precedents rather
than inventing new ones.

Format: `Decision / Rationale / Alternatives considered / Risks and
validation`.

## R-001: Module and package placement

**Decision**: A new `finvera-be` module, `analyst`, layered per ADR-0007
(`domain`, `service`, `repository`, `entity`, `controller`, `dto`, plus a
`provider` package for the `finvera-ai` HTTP client), owning only
audit/observability data (`AnalystQuery`, `AnalystToolCall` — DATA-001, no
new authoritative business data) and the tool-endpoint controllers
`finvera-ai`'s orchestrator calls back into. In `finvera-ai`, use the
package structure `finvera-ai/AGENTS.md` already prescribes:
`app/features/chat/` (the conversational ask entry point, US1/US2),
`app/features/analysis/` (deterministic-output explanation, US3),
`app/features/orchestration/` (tool allowlist, selection, dispatch, and
the natural-language screener-filter conversion used by US4), reusing
`app/features/rag/` and `app/infrastructure/llm/`/`qdrant/` exactly as
Feature 006 left them.

**Rationale**: `finvera-ai/AGENTS.md`'s package list was written before this
feature existed and already names `chat`, `analysis`, and `orchestration` —
this feature is simply the first to populate them, so following that
structure exactly is the correct default, the same reasoning Feature 006's
research R-001 used for `document`/`rag`. On the `finvera-be` side, this
feature owns no new financial data (DATA-001), so its module is
intentionally thin: audit logging and the tool-facing controllers only.

**Alternatives considered**: Folding tool-call audit logging into each
owning feature's own module (e.g. logging a Technical-tool call inside
Feature 002's `stock` module) — rejected; the audit record's identity is
"one step of one AI Analyst answer," not "one Feature 002 read," and
scattering it across five modules would make AI-002's auditability
requirement harder to query and reason about as a whole.

## R-002: Owner-scoping and identity propagation across three hops

**Decision**: Reuse Feature 005's exact owner-scoping pattern (`owner_id`
column, service-layer comparison, mismatch treated identically to "not
found" — Feature 005 research R-002), but propagate it explicitly at every
hop rather than relying on session state, because only the first hop has
one: `finvera-be`'s public `/analyst/ask` endpoint resolves `ownerId` from
the authenticated session (SEC-001, unchanged), then includes it as a
required field on its internal call to `finvera-ai`'s orchestration
endpoint. `finvera-ai`'s orchestrator, in turn, includes that same
`ownerId` as a required field on every tool call back into `finvera-be` —
mirroring the explicit `ownerId` field Feature 006's own internal contract
already requires on `/internal/v1/retrieve` and `/internal/v1/synthesize`,
for the identical reason: `finvera-ai` has no session to derive it from.

**Rationale**: SRS §36.4 treats AI Analyst answers the same as portfolios,
documents, and other private-by-default resources; Feature 006 already
proved the "explicit `ownerId` field on every internal request" pattern
works for a two-hop call (`finvera-be` → `finvera-ai`) — this feature only
extends it to a three-hop call (`finvera-be` → `finvera-ai` →
`finvera-be`'s own tool endpoints), with no new design needed.

**Risks/validation**: `tasks.md`'s security tests must confirm a tool call
carrying an `ownerId` other than the one the original session resolved is
rejected — not merely that the outer `/analyst/ask` endpoint is
session-gated. A compromised or buggy orchestration step must not be able
to widen its own access by supplying a different `ownerId` on a tool call.

## R-003: A new internal tool-invocation API, extending Feature 006's bidirectional pattern

**Decision**: `finvera-ai`'s orchestrator reaches `finvera-be`'s structured
data through new, read-only, versioned internal endpoints under
`/internal/v1/tools/*` (Constitution Principle III: the browser never
reaches `finvera-ai`, and `finvera-ai` never reaches PostgreSQL directly —
it reaches `finvera-be`'s existing service layer through this new
surface, not a new database connection). This makes the internal API
between the two services bidirectional in the same sense Feature 006
research R-003 already established: `finvera-be` calls `finvera-ai`'s
`/internal/v1/analyst/ask` to start orchestration, and `finvera-ai` calls
back into `finvera-be`'s `/internal/v1/tools/*` for every structured-data
tool, plus the already-existing `/internal/v1/retrieve` /
`/internal/v1/synthesize` (hosted by `finvera-ai`, unchanged) for the
Research/RAG tool. **Every request, in either direction, MUST carry and
have validated the shared `X-Internal-Api-Key`** — no tool endpoint is
trusted by network position alone, extending Feature 006's own hardening
(its F1 finding) to this feature's new endpoints from the start rather than
needing a second pre-implementation-analysis fix.

**Rationale**: Introducing a second trust mechanism (mTLS, OAuth
client-credentials) for what is still a private single-owner deployment on
one trusted network would be disproportionate complexity (Constitution
Principle VIII) with no new threat this feature introduces — it is the same
threat model Feature 006 already accepted and hardened.

**Alternatives considered**: Giving `finvera-ai` its own read replica or
direct PostgreSQL credentials for structured data — rejected outright; it
would let an AI-service compromise or bug read (or, if misconfigured,
write) `finvera-be`'s authoritative data directly, violating Constitution
Principle III's explicit boundary and Principle I's deterministic-core
guarantee, for a latency saving this deployment's scale does not need.
Reusing `finvera-be`'s existing **public** REST endpoints for tool calls
(with the internal key swapped in for session/CSRF) — rejected; the public
endpoints are shaped for one authenticated browser session's own data with
no `ownerId` parameter, and reusing them would either require adding an
`ownerId` override (a privilege-escalation footgun on a browser-facing
endpoint) or maintaining two parameter contracts on one endpoint; a
dedicated internal surface keeps the browser-facing contract unchanged and
the tool contract explicit.

**Risks/validation**: `tasks.md`'s security tests must exercise a missing/
invalid `X-Internal-Api-Key` against every new `/internal/v1/tools/*`
endpoint and against `/internal/v1/analyst/ask`, not only against the
already-covered Feature 006 endpoints.

## R-004: Tool taxonomy and endpoint mapping

**Decision**: SRS §31's nine named tools map onto Features 001-006's
existing capabilities through the new internal tool surface as follows;
each tool is read-only from the orchestrator's perspective.

| SRS Tool | Backing feature | Internal endpoint |
|---|---|---|
| Market | Feature 001 | `GET /internal/v1/tools/market/overview` |
| Stock | Feature 002 | `GET /internal/v1/tools/stocks/{symbol}` |
| Technical Analysis | Feature 002 (indicators) + Feature 004 (signals/risk factors) | `GET /internal/v1/tools/stocks/{symbol}/technical` |
| Fundamental Analysis | Feature 002 | `GET /internal/v1/tools/stocks/{symbol}/fundamentals` |
| Valuation | Feature 002 | `GET /internal/v1/tools/stocks/{symbol}/valuation` |
| Portfolio | Feature 005 | `GET /internal/v1/tools/portfolios/positions`, `GET /internal/v1/tools/portfolios/analytics` (owner-scoped, no portfolio id needed — the configured owner's own portfolio) |
| News | Feature 006 | `GET /internal/v1/tools/research/news` (metadata/browse only; content retrieval goes through the Research/RAG tool) |
| Research/RAG | Feature 006 | Existing `/internal/v1/retrieve` and `/internal/v1/synthesize`, unchanged |
| Screening | Feature 003 | `POST /internal/v1/tools/screener/executions`, accepting the exact structured filter shape Feature 003's engine already accepts |

**Rationale**: Every row reuses an existing deterministic engine unmodified
(DATA-001); the internal endpoints are thin, owner-scoped, read-only
mirrors of logic Features 001-006 already implement and test, not new
business logic. Folding Feature 004's signal/risk-factor data into the
Technical Analysis tool (rather than a tenth, unnamed tool) matches the
interpretation already recorded in `spec.md`'s Assumptions and the
checklist's Notes.

**Alternatives considered**: A single generic
`POST /internal/v1/tools/invoke {toolName, arguments}` dispatcher endpoint
instead of one endpoint per tool — rejected; per-tool endpoints let each
one declare its own typed request/response schema in
`contracts/internal-api.openapi.yaml` (consistent with `finvera-ai/
AGENTS.md`'s "complete type annotations and Pydantic models at every API/
tool boundary"), where a generic dispatcher would need a second, looser
validation layer to recover the same guarantee.

## R-005: Orchestration mechanism

**Decision**: Tool selection uses the LLM provider's native function/tool
calling (Gemini, ADR-0002 — the same provider Feature 006 already uses for
synthesis), with the nine tools declared as typed function schemas and the
model choosing zero or more to call before producing a final answer.
`finvera-ai`'s `orchestration` package enforces the allowlist and argument
validation **in code**, not only via the schema declaration — a tool name
or argument shape the model proposes that does not match an allowlisted,
validated tool/schema is rejected before any call is made (FR-002, AI-002).
A single question may involve multiple sequential tool calls (e.g. Stock
then Technical then Valuation) bounded by `finvera.analyst.max-tool-calls`
(default **10** — enough to cover one call to every one of the nine tools
plus one follow-up, while still bounding worst-case latency/cost).

**Rationale**: Native function calling is the standard, well-supported
mechanism for this exact problem and avoids hand-rolling a ReAct-style
prompt loop and its own parsing fragility. Enforcing the allowlist in code
rather than trusting the model's own schema adherence is the same
defense-in-depth reasoning Feature 006 research R-008 already applied to
citation verification: prompt-level constraints reduce but never guarantee
compliance.

**Alternatives considered**: A hand-written orchestration prompt with
free-form tool-call text parsed by regex — rejected; strictly worse than
native function calling on both reliability and implementation effort, with
no offsetting benefit. Unbounded tool-call loops — rejected outright;
directly risks NFR-001/NFR-002 and unbounded provider cost (FR-011).

**Risks/validation**: `contracts/orchestration-v1.md`'s required test-vector
table must include a case where the model proposes a tool/argument outside
the allowlist (or malformed), proving the code-level check rejects it
rather than forwarding it, and a case that reaches the tool-call bound,
proving a disclosed partial answer rather than a hang.

**`priorTurns` behavior, when the client supplies it.** A client-supplied
`priorTurns` list (FR-013's own opt-in exception to "no server-side
memory") is included as **read-only prior context** in the synthesis
prompt only — it never widens, narrows, or otherwise alters tool-call
allowlist enforcement (U-1), `ownerId` propagation (U-2), or
attribution/citation verification (U-5), all of which apply identically to
every request regardless of whether `priorTurns` is present. Concretely:
`priorTurns` may influence which tools the model chooses to call (e.g. a
follow-up question implicitly about the same symbol) and how the answer's
prose refers back to earlier turns, but every claim in the new answer is
still verified against only *this request's own* tool calls and
retrieval — never against a claim made in a prior turn, which the server
never persisted and cannot re-verify.

## R-006: Deterministic-output explanation is not orchestrated

**Decision**: US3's explanation capability (`POST /analyst/explanations` on
`finvera-be`, internally `POST /internal/v1/analyst/explain` on
`finvera-ai`) takes the deterministic output's own evidence factors as
**input** — supplied by the caller from an already-fetched Feature 002/004
response (e.g. a signal's `evidenceFactors` the Stock Detail page already
has) — rather than the orchestrator re-fetching or re-deriving them. The
LLM call is a single, non-orchestrated generation over exactly the supplied
factors, schema-validated (Pydantic: `{explanation: str, factorsReferenced:
list[str]}`), with a post-hoc code-level check that every value in
`factorsReferenced` actually appears in the supplied input factor list —
any explanation that references a factor not supplied is rejected and
retried once, then surfaced as a generic explanation-unavailable state
rather than passed through with an invented factor (FR-006).

**Rationale**: SRS §32's own framing is "transform these structured factors
into human-readable explanations" — the factors are already given, not
discovered; adding tool orchestration here would be unjustified complexity
(Constitution Principle VIII) for a capability that needs none. The
post-hoc faithfulness check is the direct structural analogue of Feature
006 research R-008's citation verification, applied to "did the model stay
within its given evidence" instead of "did the model stay within its
retrieved passages."

**Alternatives considered**: Routing explanation requests through the same
general orchestrator as US1/US2 (letting the model "decide" to call a
Technical/Fundamental tool to re-fetch the evidence) — rejected; it adds a
network round-trip and a chance of the freshly-fetched evidence
subtly differing from what the owner is actually looking at on screen
(e.g. a price tick between page load and explanation request), which would
make the explanation describe a different moment than the one displayed.

## R-007: Natural-language screener conversion, validated before execution

**Decision**: A natural-language screening criterion is converted to
Feature 003's exact structured filter shape by a single, non-iterative
Gemini call (schema-validated Pydantic output matching Feature 003's own
filter schema), invoked as the Screening tool inside the same orchestrator
US1/US2 use (R-005) — not a separate endpoint. Before Feature 003's engine
ever runs the converted filters, `finvera-ai` attaches a `confidence` and,
when confidence is below a stated floor, an `ambiguityNote` describing what
was assumed; `finvera-be` includes the exact structured filters used (and
any `ambiguityNote`) in the tool-call metadata surfaced to the owner
(FR-008, FR-009), and the filters themselves are validated against Feature
003's real filter schema before being executed by its real engine —
identical enforcement to a filter the owner entered by hand, with no
LLM-specific execution path (FR-007).

**Rationale**: Reusing Feature 003's engine unmodified is what makes SC-004
("100% of conversions produce results identical to entering the equivalent
structured filters directly") achievable at all — any parallel filtering
logic would risk drifting from Feature 003's own deterministic behavior.
Surfacing the converted filters (rather than only the resulting stock list)
is what FR-008 requires and is the only way an ambiguous conversion can be
verified by the owner instead of silently trusted.

**Alternatives considered**: Executing the screener call without surfacing
the converted filters, showing only results — rejected outright; directly
violates FR-008 and removes the owner's only way to catch a
misinterpreted criterion. An interactive clarification round-trip (the
system pausing to ask the owner a follow-up question before executing,
FR-009's second stated option) — rejected for this MVP: it would require
the server to hold conversational state across two requests, directly
contradicting FR-013's no-server-side-memory design and research R-011's
audit-only (not conversational) logging. The disclosed-interpretation path
satisfies FR-009 without that state, so it is the only branch implemented;
revisit the interactive branch only alongside a deliberate, separately
scoped decision to support multi-turn state (SRS-JRN-01, Post-MVP), not as
an incidental side effect of this feature.

## R-008: Streaming event shape

**Decision**: `POST /analyst/ask` streams Server-Sent Events extending
Feature 006's `delta`/`final` shape (`contracts/rag-v1.md`) with one new
event type: `tool_call` (`{type: "tool_call", toolName, arguments,
status: "started"|"succeeded"|"failed"}`), emitted as each tool call begins
and resolves, before any `delta` events for the portion of the answer that
depends on it. `delta` events relay generated text verbatim; the `final`
event carries the complete attributed answer: structured claims linked to
their tool call by index, document/news claims carrying Feature 006's
existing citation shape unchanged.

**Rationale**: SRS §31's own example workflow is presented as a visible
sequence of tool calls ending in a final response — surfacing `tool_call`
events lets the frontend show real-time progress ("Đang tra cứu dữ liệu kỹ
thuật...") and gives AI-002's auditability a visible, not just logged,
counterpart. Extending Feature 006's event shape rather than inventing a
new one keeps one SSE contract vocabulary across both AI features.

**Risks/validation**: `contracts/orchestration-v1.md`'s test vectors must
confirm `tool_call` events never leak another owner's arguments/results
(there being only one owner today does not excuse the contract from
declaring the shape correctly) and that a failed tool call's `status:
"failed"` event correlates with the disclosed-degraded language in the
final answer (FR-012).

## R-009: Structured-claim attribution verification

**Decision**: Mirroring Feature 006 research R-008's citation verification,
every structured/numeric claim in a synthesized answer is
**programmatically checked** after generation: the model's output is a
typed structure associating each such claim with a tool-call index and the
specific field/value from that tool's actual response; `finvera-ai` verifies
the claimed value matches the real tool response field (exact match for
strings/enums, rounding-tolerant match for decimals) before the claim is
allowed into the final answer. A claim that fails this check is dropped and
the answer is regenerated once, then, if still failing, the claim is
replaced with a disclosed limitation rather than passed through
(FR-003, AI-001).

**Rationale**: The same reasoning Feature 006 R-008 already established for
document citations applies identically here: prompt instructions reduce but
never guarantee that the model reports a tool's output faithfully, and
FR-003/AI-001's "never recomputed or approximated" guarantee is only real
if enforced in code.

**Risks/validation**: `contracts/orchestration-v1.md`'s test-vector table
must include a case where the model is coerced into misstating a tool's
actual numeric result, proving the post-hoc check catches and replaces it.

**Public-shape resolution is `finvera-be`'s responsibility, not a
pass-through** (the same class of decision Feature 006 research R-008
recorded as its F3 finding, applied here from the start rather than fixed
after the fact). `finvera-ai`'s `/internal/v1/analyst/ask` final event
identifies each structured claim only by `{sequenceNo, fieldPath,
claimedValue}` — the minimum needed for U-5's verification, and all
`finvera-ai` can know without a human-readable tool/field naming scheme
that belongs to `finvera-be`. `finvera-be` MUST resolve each `sequenceNo`
to its own `analyst_tool_call` row (already written during dispatch,
research R-001) to recover the human-presentable `toolName` and a display
label before emitting the public stream's final event — exactly as it
already resolves Feature 006's `chunkId` document citations.

## R-010: Tool-call failure and bound handling

**Decision**: A tool call that errors or times out (per-call timeout
`finvera.analyst.tool-call-timeout`, default 10 seconds, matching NFR-001's
per-tool budget within the overall 20-second streaming-start target) is
recorded with `status: "failed"` and a reason; the orchestrator continues
with the tools that did succeed and the final answer explicitly discloses
which part is degraded/unavailable (FR-012) rather than silently omitting
it. Reaching `finvera.analyst.max-tool-calls` (R-005) produces a disclosed
partial answer using whatever tool results were already gathered (FR-011).

**Rationale**: Constitution Principle VII requires resilience and
observability — a single failing tool (e.g. Feature 001's market service
briefly degraded) must not fail the entire AI Analyst answer when other
needed tools succeeded, mirroring how Features 001-006 already degrade
gracefully rather than failing closed on a single dependency.

## R-011: Audit and observability logging, distinct from conversation persistence

**Decision**: `finvera-be`'s `analyst_query`/`analyst_tool_call` tables
(R-001) store: a truncated/hashed question preview (not the full raw
question text or full raw answer text), timestamps, outcome
(`COMPLETED`/`PARTIAL`/`REFUSED`/`FAILED`), and per tool call: tool name,
validated arguments, outcome, latency, and error reason where applicable —
satisfying Constitution Principle VII and `finvera-ai/AGENTS.md`'s
"store model/prompt/retrieval versions and latency/token/error metrics
without logging sensitive content" (AI-002). This is explicitly **not** the
conversation/thread persistence SRS-JRN-01 defers to Post-MVP: it is never
read back to reconstruct context for a later question (FR-013), only used
for observability/debugging.

**Rationale**: `spec.md`'s own Assumptions already draw this distinction;
this decision makes it concrete enough to implement without conflating an
audit log with the conversation memory the spec explicitly excludes.

**Alternatives considered**: Storing the full question/answer text for
richer debugging — rejected as unnecessary exposure of potentially
sensitive financial-interest content in a store with a different retention
posture than the conversation-persistence feature that would actually need
it; a truncated preview plus structured tool-call metadata is enough to
debug orchestration behavior without keeping a full private-conversation
archive.

## R-012: No idempotency key on `/analyst/ask` or `/analyst/explanations`

**Decision**: Unlike Feature 005/006's state-creating submission endpoints
(`Idempotency-Key`, research R-011/R-013), `/analyst/ask` and
`/analyst/explanations` require no idempotency mechanism: they create no
owner-visible corpus or transactional state — only an audit-log row
(R-011), and a retried request producing a second audit row is harmless (no
duplicate financial/portfolio/document entity is ever created). This
matches Feature 006's own `/research/ask` and `/research/retrieve`, which
also carry no `Idempotency-Key` for the identical reason.

**Rationale**: The idempotency pattern exists specifically to prevent
duplicate **corpus/transactional** entries from a retried write (Feature
005 research R-011's original motivation); it does not apply to a
read/synthesis request whose only side effect is an audit log, so adding it
here would be unjustified complexity (Constitution Principle VIII) with no
corresponding risk.

## R-013: Public API shape

**Decision**: `finvera-be` exposes owner-only REST endpoints under
`/api/v1/analyst/ask` (`POST`, SSE-streamed, reusing the session/CSRF/
problem-details conventions Features 001-006 already established) and
`/api/v1/analyst/explanations` (`POST`, synchronous JSON, bounded by a
tighter latency target since it takes its evidence as input rather than
fetching it — no orchestration round-trip). No new endpoint mutates any
Feature 001-006 data; this feature is read-only end to end.

**Rationale**: Matches the existing API convention set exactly, extending
Feature 006's own `/research/ask` SSE precedent rather than introducing a
new response-shape convention.
