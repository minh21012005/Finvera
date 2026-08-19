# Behavioral Contract: `orchestration-v1`

**Feature**: `007-ai-analyst`
**Rule version**: `orchestration-v1`
**Owner**: `finvera-ai` / `app/features/chat`, `app/features/analysis`,
`app/features/orchestration`
**Status**: Normative. This file is the single authority for tool-call
allowlisting/dispatch, structured-claim attribution verification, the
deterministic-output explanation faithfulness check, and natural-language
screener-filter conversion. If code and this file disagree, this file is
correct and the code is defective (`AGENTS.md`). It does not make the LLM's
generated wording itself deterministic — see U-6. It does not redefine
`rag-v1` (`../../006-news-document-rag/contracts/rag-v1.md`), which remains
the sole authority for the Research/RAG tool's own retrieval, reranking,
and citation-verification behavior; this contract only governs how that
tool is invoked and how its output is attributed alongside the other eight.

## Scope and authority

This contract governs the **deterministic, code-level** parts of
orchestration: tool allowlist enforcement, argument validation, per-claim
attribution verification, the explanation faithfulness check, and NL-to-
filter conversion validation. A change to the allowlist, the attribution
verification algorithm, or the conversion validation creates
`orchestration-v2`.

## Universal rules

**U-1 Allowlist enforcement is code, not prompt.** The nine tools (`ToolName`,
`data-model.md`) are declared to the model as typed function schemas, but
every proposed tool call is **additionally** validated in code against the
same fixed allowlist and its typed argument schema before any call is made
(research R-005). A proposed tool name or argument shape that fails this
check is rejected and never invoked; the orchestrator continues with
whatever valid calls remain rather than failing the entire request.

**U-2 Ownership propagation.** Every tool call carries the `ownerId`
resolved from the originating session at the very first hop (research
R-002); no tool call may substitute a different `ownerId`, and any attempt
to do so is rejected identically to an invalid argument (U-1).

**U-3 Untrusted tool content.** Content returned by the News or
Research/RAG tool is framed exactly as `rag-v1` U-3 requires (a numbered,
inert data block) when placed into the synthesis prompt — this contract
does not weaken that guarantee by combining it with structured-tool output;
structured-tool output (numbers, enums, classifications) carries no
free-text content and is not subject to U-3's framing requirement.

**U-4 Arithmetic.** No score, confidence, or ranking value computed by this
contract (screener-conversion confidence, U-3's framing) is an
authoritative financial value; Constitution Principle I's decimal-precision
requirement governs only the values tools themselves return (unmodified,
per FR-003), not this contract's own orchestration-internal scores.

**U-5 Attribution ground truth.** A structured claim is valid **only** if
its cited tool-call `sequence_no` refers to a call that actually succeeded
in the current request and its cited value matches that call's actual
response field (exact match for strings/enums, rounding-tolerant match for
decimals) — never merely "plausible given the tool's general purpose."

**U-6 Non-reproducibility of generated wording, reproducibility of tool
results and attribution.** Given an unchanged underlying data snapshot
(prices, fundamentals, portfolio state, corpus) and an unchanged
`orchestration-v1` rule version, the same question MUST select the same
tools with the same arguments and MUST attribute the same values (U-5 is
satisfied identically). The LLM's generated prose MAY differ between runs —
but every structured claim in any run MUST satisfy U-5 against that run's
own tool-call results, and every document/news claim MUST satisfy `rag-v1`
U-5 against that run's own retrieval. This governs attribution
reproducibility, not prose reproducibility.

## Tool dispatch

```text
proposedCalls = model's function-calling output for the current step
                (zero or more {toolName, arguments})

for each call in proposedCalls:
    if call.toolName not in ToolName allowlist: reject (U-1)
    if call.arguments does not match that tool's typed schema: reject (U-1)
    if call.arguments.ownerId != session ownerId: reject (U-2)
    otherwise: dispatch to the tool's internal endpoint (data-model.md's
        ToolName -> endpoint mapping, research R-004), record an
        analyst_tool_call row with the resolved sequence_no

if len(dispatched calls so far, this request) > finvera.analyst.max-tool-calls:
    stop dispatching further calls; mark tool_call_bound_reached = true
    (FR-011); proceed to synthesis with whatever results were gathered
```

A tool call that fails or times out (`finvera.analyst.tool-call-timeout`,
default 10s) is recorded `FAILED` with a reason (research R-010) and
excluded from the result set synthesis may cite; the orchestrator does not
retry a failed call automatically within the same request.

## Structured-claim attribution verification

1. The synthesis prompt supplies, for every successfully dispatched tool
   call, its `sequence_no` and its full typed response as structured
   context (not free text) — distinct from `rag-v1`'s numbered data blocks,
   since structured-tool output requires no untrusted-content framing
   (U-3).
2. Request a schema-validated response:
   `{answer: str, structuredClaims: list[{claimText: str, sequenceNo: int,
   fieldPath: str, claimedValue: str}], documentClaims: list[{claimText:
   str, blockRefs: list[int]}], refused: bool}` — `documentClaims` uses
   `rag-v1`'s own citation-verification shape unchanged when the
   Research/RAG tool was called.
3. For every `structuredClaims` entry: verify `sequenceNo` refers to a
   `SUCCEEDED` call in this request, verify `fieldPath` exists on that
   call's actual response, and verify `claimedValue` matches the real value
   at `fieldPath` (U-5). A claim failing any check is dropped. A claim that
   survives has its `asOf` **set programmatically** from that tool call's
   own response `asOf` field (data-model.md, DATA-002) — never generated or
   claimed by the model itself, so it cannot drift from the real value the
   same way `claimedValue` is verified rather than trusted.
4. For every `documentClaims` entry, apply `rag-v1`'s own steps 3-5
   unchanged (delegated, not reimplemented).
5. If, after steps 3-4, `answer` has zero surviving claims (structured and
   document combined) and at least one tool was called, the response is
   the refusal/limitation state (AI-004) rather than an unattributed
   answer. If **no** tool was applicable to the question at all (the model
   proposed zero calls), the response is the "outside current capability"
   state (FR-005) rather than an unassisted general-knowledge answer.
6. Every surviving claim's attribution (tool name + field, or document
   citation) is included in the final response, distinct per claim
   (FR-010) — never merged into one unattributed statement.

## Deterministic-output explanation faithfulness check

1. The caller supplies the deterministic output's own evidence factors as
   typed input (research R-006) — the orchestrator is not invoked; this is
   a single, non-orchestrated generation call.
2. Request a schema-validated response: `{explanation: str,
   factorsReferenced: list[str]}`.
3. For every entry in `factorsReferenced`, verify it is a member of the
   supplied input factor list (exact match). An entry that is not is
   evidence of an invented factor.
4. If every `factorsReferenced` entry passes step 3, return the
   explanation. Otherwise, regenerate once with the same input; if the
   retry also fails step 3, return a generic explanation-unavailable state
   (FR-006) rather than an explanation citing an unsupplied factor.

## Natural-language screener-filter conversion

1. Convert the natural-language criterion to Feature 003's exact
   structured filter schema via a single, non-iterative generation call
   (research R-007), with a `confidence` (0.0-1.0) and, when confidence is
   below **0.6**, an `ambiguityNote` describing the assumption made.
2. Validate the converted filters against Feature 003's real filter schema
   (the same validation a hand-entered filter set undergoes) before
   dispatch; a filter set that fails schema validation is treated as a
   failed Screening tool call (research R-010), never partially executed.
3. Dispatch the validated filters to Feature 003's screener engine
   unmodified (`POST /internal/v1/tools/screener/executions`); the
   returned stock list and the exact filters used (plus any
   `ambiguityNote`) are both included in the tool-call result surfaced to
   the owner (FR-008, FR-009).

## Streaming event shape

Reuses `rag-v1`'s `delta`/`final` SSE event shape (research R-008), adding
one event type:

```text
{"type": "tool_call", "sequenceNo": int, "toolName": str,
 "arguments": object, "status": "started" | "succeeded" | "failed"}
```

emitted once with `status: "started"` when a call is dispatched and once
more with its terminal status when it resolves, before any `delta` events
whose content depends on that call's result. The `final` event carries the
complete attributed answer per the verification steps above.

## Required test-vector table

Implementation and fixtures MUST cover at least:

| Case | Expected outcome |
|---|---|
| A question answerable from exactly one structured tool | That tool dispatched; answer's claim matches the tool's actual response field exactly |
| A question needing two structured tools plus the Research/RAG tool | All three dispatched; answer contains claims attributed to each, never blended |
| A question needing no allowlisted tool | Zero calls dispatched; "outside current capability" response (FR-005), not a general-knowledge answer |
| The model proposes a tool name outside the allowlist | Rejected before dispatch (U-1); orchestrator continues with valid calls |
| The model proposes valid tool arguments with a substituted `ownerId` | Rejected (U-2); treated as an invalid argument |
| A tool call times out | Recorded `FAILED`; answer discloses that part as degraded (FR-012); other successful calls still contribute |
| A question requiring more calls than `finvera.analyst.max-tool-calls` | Dispatch stops at the bound; `tool_call_bound_reached = true`; disclosed partial answer (FR-011) |
| A synthesis attempt where the model misstates a dispatched tool's actual numeric result | That claim dropped per verification step 3; not passed through |
| A surviving structured claim's tool call | `asOf` on the claim exactly equals that tool call's own response `asOf` (DATA-002), set programmatically, never generated by the model |
| A deterministic-output explanation request whose model output references a factor not in the supplied input | Rejected per faithfulness-check step 3; retried once, then generic-unavailable if still failing |
| An unambiguous natural-language screening criterion | Converted filters shown; results identical to entering those filters directly into Feature 003's screener |
| An ambiguous natural-language screening criterion (confidence < 0.6) | `ambiguityNote` present and disclosed; never a silent guess |
| Identical question, unchanged tool data, and unchanged `orchestration-v1` version, run twice | Same tools, same arguments, same attributed values both times (U-6); prose may differ |
