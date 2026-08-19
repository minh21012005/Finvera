# Data Model: AI Analyst

**Feature**: `007-ai-analyst`
**Owner**: `finvera-be` / `analyst` module (new — research R-001), audit/
observability data only. `finvera-ai` owns no new persistent store for this
feature — it reuses Feature 006's `research_chunks_v1` Qdrant collection
unmodified via the Research/RAG tool and holds no state of its own between
requests.
**System of record**: This feature introduces **no new authoritative
financial or content data** (DATA-001). Every structured value an answer
cites is read live from Features 001-005's existing authoritative tables at
answer time; every document/news value is read live through Feature 006's
existing `research_document`/`news_article`/`research_chunk` tables. The
only tables this feature owns are audit/observability records of what an
answer's tool calls did, not a second copy of what they returned.
**Migration**: forward-only Flyway, starting at `V007`.

## Relationship to Features 001-006

This feature **reuses without modification**:

| Existing table/interface | Role here |
|---|---|
| `OwnerProperties.id` (auth module) | The configured owner identity every `owner_id` column here is checked against (Feature 005 research R-002, propagated explicitly per research R-002). |
| Feature 001's market overview service | Backs the Market tool (research R-004). |
| Feature 002's stock/technical/fundamental/valuation services | Back the Stock, Technical, Fundamental, and Valuation tools. |
| Feature 004's signal/risk-factor evidence shape | Backs the Technical tool's signal data and US3's explanation input (research R-006). |
| Feature 003's screener filter schema and engine | Backs the Screening tool; the natural-language conversion (research R-007) produces exactly this schema, never a parallel one. |
| Feature 005's portfolio analytics/positions services | Back the Portfolio tool. |
| Feature 006's `research_document`/`news_article`/`research_chunk` tables, `/internal/v1/retrieve`, `/internal/v1/synthesize`, and citation shape | Back the News and Research/RAG tools, reused byte-for-byte (research R-004). |

No Feature 001-006 table is altered.

## Enumerations

| Type | Values | Notes |
|---|---|---|
| `AnalystRequestType` | `ASK`, `EXPLAIN` | Discriminates a full orchestrated question (US1/US2/US4) from a non-orchestrated deterministic-output explanation (US3, research R-006); an `EXPLAIN` request has zero associated tool calls by design. |
| `AnalystQueryOutcome` | `COMPLETED`, `PARTIAL`, `REFUSED`, `FAILED` | `PARTIAL` when the tool-call bound is reached (FR-011) or a tool failed but others succeeded (FR-012); `REFUSED` when no allowlisted tool could answer (AI-004); `FAILED` when the request could not be processed at all (e.g. provider unavailable). |
| `ToolName` | `MARKET`, `STOCK`, `TECHNICAL`, `FUNDAMENTAL`, `VALUATION`, `PORTFOLIO`, `NEWS`, `RESEARCH_RAG`, `SCREENING` | SRS §31's nine named tools (research R-004); the fixed allowlist enforced in code (FR-002). |
| `ToolCallStatus` | `SUCCEEDED`, `FAILED` | Terminal status of one tool call (research R-010); a call still in flight is not persisted until it resolves. |

## `analyst_query`

One row per `/analyst/ask` or `/analyst/explanations` request — the
top-level audit record an answer's tool calls (if any) belong to.

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `owner_id` | UUID | Research R-002; indexed. |
| `request_type` | varchar(16) | `AnalystRequestType`. |
| `question_preview` | varchar(300) | Truncated question text for debugging; never the full raw text (research R-011, SEC-003). |
| `question_hash` | char(64) | SHA-256 of the full question text, for correlation without storing it in full. |
| `outcome` | varchar(16) | `AnalystQueryOutcome`. |
| `tool_call_bound_reached` | boolean | Not null; `false` for `EXPLAIN` requests (research R-005/R-010). |
| `requested_at` | timestamptz | UTC. |
| `completed_at` | timestamptz nullable | UTC; set when the outcome is terminal. |

Checks: `(request_type = 'EXPLAIN') = (tool_call_bound_reached = false)` is
enforced at the service layer (an `EXPLAIN` request never invokes the
orchestrator, so it can never reach the bound) rather than as a hard SQL
constraint, since a boolean-vs-enum SQL check adds no real safety here.

Indexes: `(owner_id, requested_at)` for observability queries.

## `analyst_tool_call`

One row per orchestrator-issued tool call belonging to an `ASK` (or `ASK`
with a Screening sub-call, US4) `analyst_query` — the audit trail
underlying an answer's per-claim attribution (AI-002).

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `analyst_query_id` | UUID | FK `analyst_query`; indexed. |
| `sequence_no` | smallint | 0-based order within the query (research R-008's `tool_call` event ordering). |
| `tool_name` | varchar(32) | `ToolName`. |
| `arguments` | jsonb | The code-validated arguments actually used (research R-005) — never the model's raw, unvalidated proposal. |
| `status` | varchar(16) | `ToolCallStatus`. |
| `failure_reason` | varchar(200) nullable | Required when `status = FAILED` (research R-010). |
| `latency_ms` | integer | Not null. |
| `called_at` | timestamptz | UTC. |

Checks: `(status = 'FAILED') = (failure_reason IS NOT NULL)`.

Indexes: `(analyst_query_id, sequence_no)` unique.

## Read models (never persisted beyond the request/response and the audit rows above)

| Read model | Composition | Endpoint |
|---|---|---|
| `OrchestrationResult` | A synthesized, streamed answer whose structured claims are each linked to an `analyst_tool_call` (by `sequence_no`), verified against that call's actual result, and carry that call's own `asOf` unmodified (research R-009, DATA-002), and whose document/news claims carry Feature 006's existing citation shape unchanged | `POST /api/v1/analyst/ask` (streamed; research R-013) |
| `ExplanationResult` | A natural-language explanation of a caller-supplied deterministic output, plus the list of input evidence factors it actually referenced, verified against the supplied input (research R-006) | `POST /api/v1/analyst/explanations` |

Neither is persisted as reusable conversation/thread state (spec.md
Assumptions, FR-013); `analyst_query`/`analyst_tool_call` are audit
metadata only and are never read back to reconstruct context for a later
request.
