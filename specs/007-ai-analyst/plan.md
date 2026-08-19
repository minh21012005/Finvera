# Implementation Plan: AI Analyst

**Feature Directory**: `specs/007-ai-analyst`
**Date**: 2026-08-20
**Spec**: [spec.md](spec.md)
**Status**: Draft — design complete, not yet implemented. `tasks.md` has
not been generated.

## Summary

Deliver Finvera's seventh vertical slice and MVP capstone: a single
conversational entry point that answers a natural-language investment
question by orchestrating Features 001-006's existing deterministic
services and Feature 006's RAG capability as an explicit, allowlisted set
of tools — never a second calculation engine. Every structured claim in an
answer is attributed to and verified against the exact tool call that
produced it (never recomputed by the LLM); every document/news claim keeps
Feature 006's citation shape unchanged. The same orchestrator also explains
an existing deterministic output in plain language from caller-supplied
evidence, and converts a natural-language screening criterion into the
exact structured filters Feature 003's engine already accepts.

This feature owns no new authoritative data — it is read-only end to end,
adding only a thin audit/observability log of what each answer's tool calls
did. It is the second feature to implement anything in `finvera-ai`,
populating the `chat`, `analysis`, and `orchestration` packages
`finvera-ai/AGENTS.md` already reserved, and extends Feature 006's
bidirectional internal-API authentication pattern to a new,
key-authenticated tool surface rather than introducing a second trust
mechanism.

## Technical Context

**Affected projects**: `finvera-be` (new, thin `analyst` module: audit
logging plus the `/internal/v1/tools/*` controllers), `finvera-ai` (second
real implementation: `app/features/chat`, `app/features/analysis`,
`app/features/orchestration`, reusing `app/features/rag`/
`app/infrastructure/llm` unmodified), `finvera-fe` (new `analyst` feature
folder; an "AI Analysis" entry point on the Stock Detail page per SRS
§6.2/`SRS-STK-03`, plus a standalone conversational view).

**Languages and versions**: unchanged — Java 21/Spring Boot 4.1.x,
TypeScript 5/React 19/Vite, Python 3.13/FastAPI, PostgreSQL 17. No new
runtime dependency beyond what Feature 006 already introduced (Gemini SDK,
already used for orchestration's native function-calling per research
R-005).

**Primary dependencies**: none new to `finvera-ai` beyond Feature 006's
existing Gemini SDK usage (function-calling is a capability of the same
provider/SDK, not a new dependency). New to `finvera-be`: none beyond
extending the existing `finvera-ai` internal HTTP client
(`contracts/internal-api.openapi.yaml`) with the new tool-facing routes it
now also hosts.

**Storage and state**: PostgreSQL (new `analyst_query`/`analyst_tool_call`
audit tables only, forward Flyway from `V007`) is authoritative for the
audit log; this feature introduces **no** new authoritative financial or
content data (DATA-001) and no new Qdrant collection — the Research/RAG
tool reuses Feature 006's `research_chunks_v1` unmodified.

**Interfaces**: new owner-only public REST endpoints under
`/api/v1/analyst/*` (`contracts/public-api.openapi.yaml`); new internal,
network-restricted tool endpoints hosted by `finvera-be`, plus new
orchestration/explanation endpoints hosted by `finvera-ai`
(`contracts/internal-api.openapi.yaml`); a new behavioral contract for tool
dispatch, attribution verification, explanation faithfulness, and NL-filter
conversion (`contracts/orchestration-v1.md`).

**Testing**: unit tests for allowlist enforcement/attribution verification/
faithfulness-checking (`orchestration-v1`), integration tests for the new
bidirectional internal-API endpoints (both directions, from the start —
research R-003), Spring MVC/security contract tests, Vitest component
tests, Playwright P1-P4, and a versioned AI evaluation dataset for
tool-selection correctness, attribution accuracy, refusal, and
prompt-injection resistance (Constitution Principle VI).

**Performance goals**: NFR-001 — 90% of `/analyst/ask` responses begin
streaming within 20 seconds. NFR-002 — the orchestrator or any tool being
unavailable leaves Features 001-006 fully usable and the endpoint states
unavailability. NFR-003 — the tool-call bound is enforced and observable.

**Availability and degradation**: a failed/timed-out individual tool call
degrades only the part of the answer that depended on it (disclosed,
FR-012); Features 001-006 remain fully usable independent of this feature
(Constitution Principle VII), identical in spirit to Feature 006's own
degradation posture.

**Scale and scope**: one owner, one question at a time realistically for a
private single-owner deployment, up to `finvera.analyst.max-tool-calls`
(default 10) tool calls per question, no multi-user delivery.

**Open technical unknowns**: none blocking — `research.md` resolves module
placement, identity propagation across three hops, the new internal tool
API and its bidirectional auth, the tool taxonomy/endpoint mapping, the
orchestration mechanism (native function calling) and its bound,
non-orchestrated explanation design, NL-to-filter conversion and
validation, the streaming event shape, structured-claim attribution
verification, tool-call failure handling, audit/observability logging
(distinct from the conversation persistence this feature explicitly
excludes), the idempotency non-requirement, and the public API shape —
all before this plan was written, per Constitution Principle V.

## Constitution Check

### Pre-research gate

- [x] **I. Deterministic finance core** — this feature computes no
  financial value of its own; every structured claim MUST be verified
  against a deterministic tool's actual output (FR-003, AI-001,
  `orchestration-v1` U-5), never recomputed or approximated by the LLM.
- [x] **II. Evidence, provenance, temporal truth** — every structured claim
  carries the underlying tool's as-of time (DATA-002); every document/news
  claim keeps Feature 006's citation and provenance guarantees unchanged;
  Qdrant involvement is entirely delegated to the unmodified Research/RAG
  tool.
- [x] **III. Explicit boundaries** — the browser calls only `finvera-be`
  (SEC-001); `finvera-ai`'s orchestrator never reads PostgreSQL directly,
  reaching structured data only through `finvera-be`'s new internal tool
  endpoints (research R-003), extending rather than weakening Feature 006's
  boundary.
- [x] **IV. Security, privacy, responsible decision support** — owner-only
  access reuses Features 005/006's enforcement, propagated explicitly at
  every hop since `finvera-ai` has no session (research R-002); answers
  remain decision support, never a buy/sell instruction (spec.md
  Assumptions).
- [x] **V. Specification and traceability before code** — spec, research
  (thirteen decisions), data model, and two contracts exist; no unresolved
  clarification marker.
- [x] **VI. Risk-based testing** — tool-selection correctness, attribution
  accuracy, refusal, and prompt-injection resistance (via the reused
  Research/RAG tool) each have a named evaluation approach (research R-005/
  R-009) before implementation.
- [x] **VII. Resilience and observability** — a single tool's failure or
  the overall tool-call bound each have a defined, disclosed degradation
  path (research R-010) that never fails the entire answer or hangs
  indefinitely.
- [x] **VIII. Modular simplicity** — no new LLM/embedding provider, no new
  vector collection, no new trust mechanism beyond extending Feature 006's
  existing internal-API key pattern, no persisted conversation store; the
  new `finvera-be` module is intentionally thin (audit plus tool
  controllers only).

**Result**: PASS.

### Post-design gate

Completed after Phase 1 design (2026-08-20), reviewed against the actual
data model and contracts produced:

- [x] **I. Deterministic finance core** — confirmed unchanged;
  `orchestration-v1`'s attribution-verification steps (1-6) are the
  concrete, code-level mechanism, not merely a stated intent; U-4 scopes
  its only non-decimal arithmetic (screener-conversion confidence) to a
  non-authoritative value.
- [x] **II. Evidence, provenance, temporal truth** — `data-model.md`'s
  `analyst_tool_call.arguments`/`sequence_no` make every claim's
  provenance reconstructable; `DATA-002` requires every tool result to
  retain its own as-of time unmodified.
- [x] **III. Explicit boundaries** — `contracts/internal-api.openapi.yaml`
  confirms `finvera-ai` never reads/writes PostgreSQL directly in any path
  (every structured-data read is a `/tools/*` call back to `finvera-be`);
  the public contract confirms the browser never receives a `finvera-ai`
  URL, credential, or direct reference.
- [x] **IV. Security, privacy, responsible decision support** — every new
  internal endpoint, in both directions, requires `X-Internal-Api-Key`
  (research R-003) with `ownerId` explicitly propagated and re-validated at
  each hop (research R-002); `SEC-003` bounds what reaches the LLM
  provider.
- [x] **V. Specification and traceability before code** — `data-model.md`
  and both contracts trace every FR/DATA/AI requirement to a concrete
  field, rule, or endpoint.
- [x] **VI. Risk-based testing** — the Test and Evaluation Strategy table
  below names a level for every requirement group, including the
  allowlist-enforcement and attribution-verification boundaries.
- [x] **VII. Resilience and observability** — `orchestration-v1`'s tool
  dispatch algorithm defines the bound and per-call timeout up front, not
  discovered after implementation; `analyst_tool_call` gives every failure
  a queryable audit trail.
- [x] **VIII. Modular simplicity** — the design introduces exactly the
  package structure `finvera-ai/AGENTS.md` already reserved, one thin new
  `finvera-be` module, zero new provider/broker/datastore. `Complexity
  Tracking` below records the one genuinely new surface this feature adds
  (the `/internal/v1/tools/*` API) and why it is the minimum needed rather
  than a broader alternative.

**Result**: PASS.

## System Context and Boundaries

```text
                 owner (browser, existing session)
                              |
             POST /analyst/ask (streamed), /analyst/explanations
                              |
                              v
             finvera-be / analyst (new, thin module)
       resolves ownerId from session; writes analyst_query/
       analyst_tool_call (audit only, no business data)
                              |
        internal API, X-Internal-Api-Key, private network only
                              v
       finvera-ai / app/features/chat, analysis, orchestration
   (second real implementation; reuses app/features/rag unmodified)
                              |
          tool dispatch (orchestration-v1), ownerId propagated
                              |
              /------------------------------\
              v                                v
  finvera-be /internal/v1/tools/*     finvera-ai /internal/v1/retrieve,
  (Market/Stock/Technical/            /synthesize (Research/RAG tool,
   Fundamental/Valuation/             Feature 006, unchanged)
   Portfolio/News/Screening)
              |                                |
       reads Features 001-005's         reads research_chunks_v1 /
       existing services, unmodified    research_document / news_article
                              |
                              v
     React SPA: new "Analyst" feature (chat entry point, Stock Detail
     "AI Analysis" tab, explanation triggers on existing signal/valuation
     displays)

TCBS / Redis / Kafka / new PostgreSQL business tables / new Qdrant
collection: no path in this feature
```

### Ownership

| Concern | Owner | Reason |
|---|---|---|
| Audit log (`analyst_query`, `analyst_tool_call`) | `finvera-be/.../analyst/entity`, `.../service` | Observability only (Constitution Principle VII); never a second copy of authoritative data. |
| Tool-facing controllers (`/internal/v1/tools/*`) | `finvera-be/.../analyst/controller` | Spring remains the sole boundary to every existing service's data (Constitution Principle III); each controller delegates to its owning feature's existing service, unmodified. |
| Orchestration, attribution verification, explanation faithfulness, NL-filter conversion (`orchestration-v1`) | `finvera-ai/app/features/orchestration`, `app/features/chat`, `app/features/analysis` | The AI/orchestration boundary `finvera-ai/AGENTS.md` already assigns this to `finvera-ai`. |
| Research/RAG tool execution | `finvera-ai/app/features/rag` (Feature 006, unchanged) | Reused as-is; no duplicated retrieval/synthesis logic. |
| Every tool's underlying computation (indicators, valuation, signals, screener, portfolio analytics) | Features 001-005's existing modules, unmodified | This feature never recomputes a value Features 001-005 already own (DATA-001). |
| REST mapping, security, internal-API client | `finvera-be/.../analyst/controller`, `.../provider` | Spring remains the sole public boundary. |
| Presentation and formatting | `finvera-fe/src/features/analyst` | UI renders attributed claims/citations as given; never recomputes one. |

### Interface Changes

| Interface | Change | Version and compatibility | Contract Artifact |
|---|---|---|---|
| Public REST | Add owner-only `/analyst/ask` (streamed) and `/analyst/explanations` | Additive under `/api/v1` | [public-api.openapi.yaml](contracts/public-api.openapi.yaml) |
| Internal REST | Add `finvera-ai`-hosted `/analyst/ask`, `/analyst/explain`; add `finvera-be`-hosted `/tools/*` (nine tool endpoints) | New paths under existing `/internal/v1`; bidirectional, network-restricted | [internal-api.openapi.yaml](contracts/internal-api.openapi.yaml) |
| Behavioral rules | Add one versioned rule set | `orchestration-v1` | [orchestration-v1.md](contracts/orchestration-v1.md) |
| Database | Add `analyst_query`, `analyst_tool_call` (audit only) | Forward Flyway from `V007`; no existing table altered | [data-model.md](data-model.md) |
| Vector index | None — reuses Feature 006's `research_chunks_v1` unmodified | N/A | N/A |

## Phase 0: Research

Complete in [research.md](research.md). Thirteen decisions: module/package
placement (a thin `finvera-be analyst` module plus `finvera-ai`'s
already-reserved `chat`/`analysis`/`orchestration` packages), owner-scoping
propagated explicitly across three hops, a new bidirectional internal tool
API extending Feature 006's authentication pattern, the SRS-§31 tool
taxonomy's mapping onto Features 001-006, native function-calling
orchestration with a code-enforced allowlist and bound, a non-orchestrated
explanation design with its own faithfulness check, natural-language
screener-filter conversion validated against Feature 003's real schema
before execution, a `tool_call`-extended SSE event shape, programmatic
structured-claim attribution verification (with public-shape resolution as
`finvera-be`'s responsibility, mirroring Feature 006's own citation
resolution), tool-call failure/bound handling, audit/observability logging
explicitly distinct from the conversation persistence this feature
excludes, the decision that no `Idempotency-Key` is needed (unlike Feature
005/006's state-creating submissions), and the public API shape.

## Phase 1: Design and Contracts

### Data model

[data-model.md](data-model.md): two additive PostgreSQL tables, both audit/
observability only (`analyst_query`, `analyst_tool_call`) — no new
authoritative financial or content table, no new Qdrant collection. Every
structured value an answer cites is read live from Features 001-005 at
answer time; every document/news value is read live through Feature 006's
existing tables.

### Contracts

- **orchestration-v1.md** — tool allowlist/dispatch, structured-claim
  attribution verification, explanation faithfulness check, NL-filter
  conversion and validation, streaming event shape, and required test
  vectors.
- **internal-api.openapi.yaml** — the bidirectional `finvera-be`/
  `finvera-ai` conversation: orchestrated ask, non-orchestrated
  explanation, and nine `finvera-be`-hosted tool endpoints.
- **public-api.openapi.yaml** — the browser-facing `/analyst/ask`
  (streamed) and `/analyst/explanations` endpoints.

### Security, Privacy, and AI Safety

- Reuse the existing owner session and CSRF controls exactly (SEC-001);
  the browser never reaches `finvera-ai` (Constitution Principle III).
- **Extended, not new**: Feature 006's internal API key
  (`X-Internal-Api-Key`, research R-003) now also gates every
  `/internal/v1/tools/*` endpoint and `/internal/v1/analyst/*`, in both
  directions, from the start — this feature does not repeat Feature 006's
  own pre-implementation-analysis discovery that a hosted-but-unauthenticated
  direction is a real forgery risk; every new endpoint here is
  key-validated on day one.
- Explicit `ownerId` propagation (research R-002) at every hop, since only
  the first hop has a session; a tool call carrying a substituted
  `ownerId` is rejected identically to an invalid argument
  (`orchestration-v1` U-2).
- Structured-claim attribution verification (`orchestration-v1` steps 1-6)
  and the explanation faithfulness check are deterministic, code-level
  checks, not merely prompt instructions — the concrete implementation of
  AI-001/FR-003/FR-006.
- Document/news content reached via the Research/RAG tool remains framed
  as untrusted data exactly per Feature 006's `rag-v1` U-3/AI-003 —
  unchanged by being combined with structured-tool output in the same
  answer.
- SEC-003 bounds what reaches the LLM provider: only the current
  question's tool results and, for explanation, the caller-supplied
  evidence factors — never a secret, token, or another owner's/feature's
  data.
- No `Idempotency-Key` is required (research R-012): this feature creates
  no owner-visible corpus/transactional state, only an audit-log row, so
  Feature 005/006's idempotency mechanism does not apply here.

### Observability and Operations

Matches Features 002-006's established baseline (global
`CorrelationIdFilter`, shared `ProblemDetailsAdvice`, failure-class
visibility in the response itself) on the public API. **New for this
feature**: `analyst_tool_call` gives every answer a queryable, per-call
audit trail (tool name, validated arguments, outcome, latency) without
logging full question/answer content (research R-011, AI-002) — the
concrete implementation of `finvera-ai/AGENTS.md`'s "store... latency/
token/error metrics without logging sensitive content" for a multi-tool
answer specifically. A tool-call bound reached, or a tool-call failure
rate rising, are health signals worth alerting on, not merely UI states.

### Test and Evaluation Strategy

| Requirement IDs | Test level | Fixture/dataset | Expected evidence |
|---|---|---|---|
| FR-001 to FR-003, AI-001 | Unit, integration | `orchestration-v1` structured-only question fixtures | Correct tool(s) dispatched; claims exactly match tool output |
| FR-004 | Unit, integration | Feature 006's `rag-eval-v1` fixtures reused via the Research/RAG tool | Document/news claims keep Feature 006's citation shape |
| FR-005, AI-004 | Unit | Out-of-allowlist question fixtures | "Outside current capability" response, never fabricated |
| FR-006, `orchestration-v1` faithfulness check | Unit, eval | Known signal/valuation evidence-factor fixtures | Explanation references only supplied factors; unverifiable case returns generic-unavailable |
| FR-007 to FR-009 | Unit, integration | Unambiguous and ambiguous NL screening-criterion fixtures | Converted filters shown; results identical to Feature 003's direct execution; ambiguous case discloses `ambiguityNote` |
| FR-010 | Unit | Combined structured+document question fixtures | Distinct, non-blended attribution |
| FR-011, NFR-003 | Integration | Fixture forcing >`max-tool-calls` | Bound enforced; disclosed partial answer |
| FR-012 | Integration | Simulated tool timeout/failure | Disclosed degraded answer; other successful tool results still used |
| FR-013 | Unit | Sequential unrelated questions | No cross-question memory leak |
| FR-014 | Manual, Playwright | `/analyst/ask` request | Streaming begins before full generation completes |
| FR-015, SEC-001 to SEC-003 | Security integration, negative (bidirectional, from the start) | Missing session/CSRF/internal key on every new endpoint, both `finvera-ai`-hosted and `finvera-be`-hosted; substituted `ownerId` on a tool call | Only the owner/authorized service succeeds; substituted `ownerId` rejected |
| DATA-001 to DATA-003 | Unit, integration | Live tool-call fixtures | No new authoritative copy created; as-of time retained; conflicting sources both surfaced |
| AI-002 | Unit, integration | Multi-tool question fixtures | `analyst_tool_call` rows correctly record order/arguments/outcome without full content |
| AI-003 | Eval | Feature 006's prompt-injection fixtures, reached via the Research/RAG tool | 0% behavior deviation (SC-005) |
| NFR-001, NFR-002 | Integration, timing | Representative multi-tool fixture set; simulated tool/provider outage | p90 within baseline; Features 001-006 remain usable |
| NFR-004 | Component, Playwright, manual | All attribution/citation/degraded states | Non-colour indicator |

Dataset version: `analyst-eval-v1`, extending Feature 006's
`rag-eval-v1` fixtures with structured-tool and combined-source cases
(research R-005/R-009). Attribution-accuracy threshold: 100% (SC-001,
SC-002). Out-of-capability threshold: 100% (SC-003). NL-conversion
threshold: 100% match to direct execution on unambiguous fixtures (SC-004).
Injection-resistance threshold: 0% deviation (SC-005). Model variability is
controlled by testing tool selection and attribution, not exact generated
wording (`orchestration-v1` U-6) — human review is required only if an
automated eval run's attribution-accuracy rate regresses below 100%.

### Rollout, Migration, and Rollback

1. Apply Flyway `V007`. No Qdrant change — Feature 006's collection is
   reused unmodified.
2. Deploy `finvera-be`'s new `/internal/v1/tools/*` endpoints behind the
   private network only (confirming `X-Internal-Api-Key` is required end
   to end on the new surface, exactly as Feature 006's rollout confirmed
   it for ingestion/retrieval/synthesis), then deploy `finvera-ai`'s
   orchestration endpoints, then `finvera-be`'s public `/analyst/*`
   endpoints and the frontend.
3. Gate: every new `/internal/v1/tools/*` and `/internal/v1/analyst/*`
   endpoint must be confirmed to reject a missing/invalid
   `X-Internal-Api-Key` before `/analyst/ask`/`/analyst/explanations` are
   enabled for real use — the same class of pre-enablement gate Feature
   006's rollout already established, applied to this feature's larger
   endpoint surface from the start.

**Rollback**: no destructive migration to reverse; application code rolls
back to a version compatible with `V007`. `analyst_query`/
`analyst_tool_call` are pure audit data with no downstream dependency —
dropping and recreating them loses only historical observability, never
business state. A defective `orchestration-v1` rule creates
`orchestration-v2`, requiring no re-indexing (this feature owns no index).

### Quickstart Acceptance

[quickstart.md](quickstart.md) defines prerequisites, commands, P1-P4
acceptance paths, degraded paths, the platform's bidirectional-from-the-start
internal-API authorization checks for this feature's new endpoints, and the
AI evaluation gate.

## Project Structure

### Feature Documentation

```text
specs/007-ai-analyst/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── orchestration-v1.md
│   ├── internal-api.openapi.yaml
│   └── public-api.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md   (generated by /speckit-tasks)
```

### Source Code Affected

```text
finvera-be/src/
├── main/java/com/minhnb/finvera_be/analyst/    # new, thin module
│   ├── service/       # AnalystQueryService (audit), nine ToolService
│   │                  # delegates (thin wrappers over Features 001-005's
│   │                  # existing services), AnalystExplanationService
│   ├── provider/      # finvera-ai internal API HTTP client (extends
│   │                  # Feature 006's client)
│   ├── controller/    # AnalystController (public), ToolController(s),
│   │                  # AnalystOrchestrationController (internal, hosted)
│   ├── dto/
│   ├── entity/, repository/   # 2 new audit tables
│   └── resources/db/migration/V007__*.sql
└── test/...

finvera-ai/app/
├── features/
│   ├── chat/            # new — /analyst/ask orchestration entry point
│   ├── analysis/        # new — /analyst/explain, faithfulness check
│   ├── orchestration/   # new — allowlist, dispatch, attribution
│   │                    # verification, NL-filter conversion
│   └── rag/             # unchanged (Feature 006)
├── infrastructure/
│   └── llm/             # unchanged; function-calling uses the same
│                         # Gemini adapter Feature 006 already added
└── main.py

finvera-fe/src/features/
└── analyst/    # chat entry point, explanation triggers, NL screener
                 # entry (new); Stock Detail page gains an "AI Analysis"
                 # tab (SRS-STK-03) that composes this feature's components
```

**Structure decision**: a new, deliberately thin `finvera-be` module
(research R-001) because no existing module owns cross-feature
orchestration audit data or the tool-facing controllers; the `finvera-ai`
package layout is not a new decision but the first real use of the
`chat`/`analysis`/`orchestration` packages `finvera-ai/AGENTS.md` already
specified. One new frontend feature folder, consistent with Features
002-006's own one-folder-per-surface convention, composed into the
existing Stock Detail page rather than duplicating its layout.

## Traceability Summary

| Requirement IDs | Design artifact | Test evidence | Planned task group |
|---|---|---|---|
| FR-001 to FR-003, AI-001 | `orchestration-v1.md` dispatch + attribution verification | Unit, integration | Foundation, US1 |
| FR-004 | `orchestration-v1.md` U-3; Feature 006 `rag-v1.md` reused | Unit, integration | US2 |
| FR-005, AI-004 | `orchestration-v1.md` step 5 | Unit | US1 |
| FR-006 | `orchestration-v1.md` explanation faithfulness check | Unit, eval | US3 |
| FR-007 to FR-009 | `orchestration-v1.md` NL-filter conversion | Unit, integration | US4 |
| FR-010 | `orchestration-v1.md` step 6 | Unit, integration | US2 |
| FR-011, NFR-003 | `orchestration-v1.md` tool dispatch bound | Integration | Foundation |
| FR-012 | research R-010; `orchestration-v1.md` | Integration | Foundation |
| FR-013 | research R-011 (audit, not memory) | Unit | Foundation |
| FR-014 | `internal-api.openapi.yaml`/`public-api.openapi.yaml` SSE shape | Manual, Playwright | Foundation |
| FR-015, SEC-001 to SEC-003 | research R-002/R-003; both contracts | Security integration, negative | Foundation |
| DATA-001 to DATA-003 | `data-model.md`; research R-004 | Unit, integration | Foundation |
| AI-002 | `data-model.md` `analyst_tool_call` | Unit, integration | Foundation |
| AI-003 | Feature 006 `rag-v1.md` (reused) | Eval | US2 |
| NFR-001, NFR-002 | research R-005/R-010 | Timing | Cross-cutting |
| NFR-004 | Non-colour status contract | Component, Playwright, manual | Each story |

## Complexity Tracking

| Addition | Why Required Now | Simpler Alternative Rejected | Approval/ADR | Removal or Review Trigger |
|---|---|---|---|---|
| A new `/internal/v1/tools/*` API surface (nine endpoints) | FR-002/SRS §31 require the orchestrator to reach Features 001-005's data through an explicit, typed boundary (Constitution Principle III); no existing internal surface covers structured data | Direct PostgreSQL access from `finvera-ai`, or reusing public session-based endpoints with an ownerId override | Constitution Principle III; research R-003 | N/A — this is Constitution Principle III's boundary applied to a genuinely new need, not a deviation |
| Native LLM function-calling for orchestration (research R-005) | SRS §31 requires tool-selecting orchestration; function-calling is the standard mechanism already supported by the existing provider | A hand-rolled ReAct-style prompt loop | research R-005 | Revisit only if the provider's function-calling proves unreliable in practice |

No new LLM/embedding provider, no new vector collection, no message
broker, no persisted conversation store, and no second trust mechanism
beyond extending Feature 006's existing internal-API key is introduced.

## Open Items Carried Into Tasks

1. The exact `finvera.analyst.max-tool-calls` (default 10, research R-005)
   and `finvera.analyst.tool-call-timeout` (default 10s, research R-010)
   values are foundation-task-level configuration, constrained to: bounded
   and configurable, never unbounded, consistent with Feature 006's own
   `finvera.research.ingestion-timeout` precedent.
2. The exact frontend placement of the "AI Analysis" tab on the Stock
   Detail page (SRS-STK-03) versus a standalone conversational view is a
   task-level UI decision, constrained to: both surfaces call the same
   `/analyst/ask` endpoint, never a second orchestration path.
3. Whether the Screening tool's confidence floor (0.6, `orchestration-v1`)
   needs to be configurable versus hardcoded is a task-level decision,
   constrained to: the same value must be used by tests and production
   code, never silently diverge.
