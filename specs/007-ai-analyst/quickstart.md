# Quickstart and Acceptance: AI Analyst

**Feature**: `007-ai-analyst`
**Status**: Draft — plan stage. This is the second feature exercising
`finvera-ai`; commands below are the intended acceptance path, not yet
executed. Mark them "verified" only once real test evidence exists.

Every command below runs against `127.0.0.1` only, per the same Tailscale
Serve-only ingress runbook Features 001-006 already require. `finvera-ai`
binds to the private network only (research R-003), unchanged from Feature
006 — it is never reachable from the browser or the public tailnet.

## Prerequisites

1. Features 001-006 are running locally and their quickstarts pass — this
   feature adds no new browser-facing authentication path and orchestrates
   their existing capabilities rather than replacing any of them.
2. `finvera-ai`'s dependencies are unchanged from Feature 006 (`uv sync`
   from `finvera-ai/`); no new dependency is introduced (research R-005:
   function-calling uses the same Gemini SDK Feature 006 already added).
3. `GEMINI_API_KEY` and `FINVERA_INTERNAL_API_KEY` (research R-003, shared
   between `finvera-be` and `finvera-ai`, now also gating the new
   `/internal/v1/tools/*` and `/internal/v1/analyst/*` paths) are set with
   no default value (`docs/ARCHITECTURE.md` §8).
4. Feature 006's own quickstart corpus (a small set of `READY` documents/
   articles) exists, so the Research/RAG tool has something real to
   retrieve during US2/US4 fixtures.
5. At least one Feature 004 signal and one Feature 002 valuation exist for
   a supported symbol, so US1/US3 fixtures have real deterministic output
   to attribute/explain.
6. The evaluation fixture set exists (research R-005/R-009, extending
   Feature 006's `rag-eval-v1`): structured-only question fixtures with
   known-correct tool outputs, combined structured+document fixtures,
   out-of-capability fixtures, unambiguous and ambiguous NL-screener
   fixtures, and evidence-factor sets for explanation fixtures.

## Configuration

New configuration keys (`finvera.analyst.*` in `finvera-be`;
`app.core.settings` in `finvera-ai`): `max-tool-calls` (default 10,
research R-005), `tool-call-timeout` (default 10s, research R-010), and the
already-shared `finvera-ai` base URL/internal API key from Feature 006.

## Quality commands

```powershell
cd D:\Finvera\finvera-be
.\mvnw.cmd test

cd D:\Finvera\finvera-fe
npm run lint
npm run test
npm run build
npx playwright test

cd D:\Finvera\finvera-ai
uv run python -m compileall .
uv run pytest
```

Feature-scoped suites (once implemented):

```powershell
cd D:\Finvera\finvera-be
.\mvnw.cmd '-Dtest=AnalystQueryServiceTests,ToolControllerTests,AnalystControllerTests,AnalystSecurityTests' test

cd D:\Finvera\finvera-ai
uv run pytest app/features/chat app/features/analysis app/features/orchestration -v
```

## Local runtime

```powershell
# terminal 1, from finvera-be/
.\mvnw.cmd spring-boot:run

# terminal 2, from finvera-ai/
uv run uvicorn app.main:app --host 127.0.0.1 --port 8001

# terminal 3, from finvera-fe/
npm run dev
```

---

## P1 acceptance — ask a question and get a tool-grounded answer

**Path**: `POST /api/v1/analyst/ask`

| Step | Expected |
|---|---|
| Ask a question answerable from one structured tool (e.g. current price for a known symbol) | Streamed answer; the claim exactly matches calling that tool's own endpoint directly, attributed to it (FR-001 to FR-003). |
| Ask a question needing multiple structured tools (e.g. "FPT đang overbought không?") | `tool_call` events for each dispatched tool; final answer's claims each attributed to the correct tool. |
| Ask a question needing a capability outside the allowlist | States outside current capability, never approximates (FR-005). |
| Simulate one allowlisted tool failing/timing out mid-question | Answer discloses that part as degraded; other successful tool results still contribute (FR-012). |
| Force a question past `finvera.analyst.max-tool-calls` | Disclosed partial answer, never an indefinite wait (FR-011). |

---

## P2 acceptance — combine structured data and document retrieval

**Path**: `POST /api/v1/analyst/ask`

| Step | Expected |
|---|---|
| Ask a question needing both a structured tool and the Research/RAG tool (e.g. "Rủi ro lớn nhất của FPT là gì?" against a `READY` document) | Answer contains both a tool-attributed claim and a Feature-006-style document citation, distinctly attributed (FR-004, FR-010). |
| Ask the same combined question twice with an unchanged data snapshot | Same tools called, same attributed values both times; wording may differ (`orchestration-v1` U-6). |
| Ask a question that retrieves a prompt-injection fixture document via the Research/RAG tool | System/orchestrator behavior unaffected (AI-003). |

---

## P3 acceptance — explain a deterministic output

**Path**: `POST /api/v1/analyst/explanations`

| Step | Expected |
|---|---|
| Request an explanation for a known signal with its evidence factors | Explanation references only the supplied factors (FR-006). |
| Simulate a faithfulness-check failure twice | Generic explanation-unavailable state, never a factor-inventing explanation passed through. |

---

## P4 acceptance — describe a screening criterion in plain language

**Path**: `POST /api/v1/analyst/ask` (Screening tool)

| Step | Expected |
|---|---|
| Submit an unambiguous natural-language criterion | Converted structured filters shown in the `tool_call` event; results identical to entering those filters directly into Feature 003's screener (FR-007, FR-008, SC-004). |
| Submit an ambiguous natural-language criterion | `ambiguityNote` disclosed, never a silent guess (FR-009). |

---

## Degraded and failure paths

| Scenario | Expected |
|---|---|
| `finvera-ai`'s orchestrator is unavailable | `/analyst/ask`/`/analyst/explanations` state unavailability (`503`); Features 001-006 remain fully usable. |
| One allowlisted tool's underlying feature is degraded | Only that tool's contribution is disclosed as degraded; the rest of the answer proceeds normally. |
| The LLM provider is unavailable mid-stream | `503` before streaming begins, or a disclosed incomplete state if already streaming — never a silently truncated answer presented as complete. |

## Authorization checks

| Check | Expected |
|---|---|
| Unauthenticated request to `/analyst/ask` or `/analyst/explanations` | HTTP 401. |
| Any `POST` request without `X-CSRF-TOKEN` | HTTP 403, no state change. |
| A direct request to any new `/internal/v1/tools/*` or `/internal/v1/analyst/*` endpoint without `X-Internal-Api-Key`, from either service | Rejected `401` — every new endpoint in this feature is key-validated from the start, in both directions (research R-003), never trusted by network position alone. |
| A tool call carrying an `ownerId` different from the session that started the request | Rejected, treated as an invalid argument (research R-002, `orchestration-v1` U-2). |
| Response, log, and export inspection | No credential, token, or another owner's content; `analyst_tool_call` records arguments/outcome only, never full question/answer text (research R-011). |

## Accessibility

| Check | Expected |
|---|---|
| Attribution, citation, and degraded/limitation states | Each has a text or icon indicator independent of colour (NFR-004). |

## AI evaluation (Constitution Principle VI)

| Check | Expected |
|---|---|
| Structured-only fixture set | 100% of claims exactly match the underlying tool's actual output (SC-001). |
| Combined structured+document fixture set | 100% distinct, correct attribution (SC-002). |
| Out-of-capability fixture set | 100% stated-limitation response (SC-003). |
| Unambiguous NL-screener fixture set | 100% identical results to direct Feature 003 execution (SC-004). |
| Prompt-injection fixtures (via the Research/RAG tool) | 0% behavior deviation (SC-005). |

## Release gates that remain open

This feature opens no new release gate for Features 001-006. It extends
Feature 006's private-network-only gate for `finvera-ai` to this feature's
larger internal endpoint surface (`/internal/v1/tools/*`,
`/internal/v1/analyst/*`) — that reachability confirmation must be repeated
for the new endpoints before `/analyst/ask`/`/analyst/explanations` are
enabled for real use.
