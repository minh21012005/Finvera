# Quickstart and Acceptance: News and Financial-Report RAG

**Feature**: `006-news-document-rag`
**Status**: Draft — plan stage. This is the first feature exercising
`finvera-ai`; commands below are the intended acceptance path, not yet
executed. Mark them "verified" only once real test evidence exists.

Every command below runs against `127.0.0.1` only, per the same Tailscale
Serve-only ingress runbook Features 001-005 already require. `finvera-ai`
binds to the private network only (research R-003) — it is never reachable
from the browser or the public tailnet.

## Prerequisites

1. Features 001-005 are running locally and their quickstarts pass. This
   feature adds no new browser-facing authentication path.
2. `finvera-ai`'s dependencies are installed (`uv sync` from `finvera-ai/`)
   and Qdrant is running locally (fixture/dev instance).
3. `GEMINI_API_KEY` (or equivalent credential, per ADR-0002/ADR-0008) and
   `FINVERA_INTERNAL_API_KEY` (research R-003, shared between
   `finvera-be` and `finvera-ai`) are set with no default value
   (`docs/ARCHITECTURE.md` §8).
4. The evaluation fixture set exists (research R-010): 5-10 known-content
   documents/articles, matched evaluation queries with known correct
   passages, 2-3 no-relevant-content queries, 2-3 prompt-injection
   fixtures, and one unparseable (no-text) PDF.

## Configuration

New configuration keys (`finvera.research.*` in `finvera-be`;
`app.core.settings` in `finvera-ai`, per its own `AGENTS.md` structure):
embedding/LLM model version pins (ADR-0008), `finvera-ai` base URL and
internal API key, `research_chunks_v1` Qdrant collection name, and the
ingestion-processing timeout (research R-004).

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
.\mvnw.cmd '-Dtest=ResearchDocumentServiceTests,NewsArticleServiceTests,ResearchControllerTests,ResearchSecurityTests' test

cd D:\Finvera\finvera-ai
uv run pytest app/features/document app/features/rag -v
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

## P1 acceptance — ingest a document and find cited passages

**Path**: `POST /api/v1/research/documents`, `POST /api/v1/research/retrieve`

| Step | Expected |
|---|---|
| Upload a fixture PDF with known content and metadata | `PENDING`, then `PROCESSING`, then `READY` (FR-001, FR-003). |
| Retrieve a query whose answer is in that document | Correct passage returned with document identity, page/section citation (FR-005). |
| Upload a no-text (scanned) PDF | Reaches `FAILED` with a stated reason, never `READY` with empty content (FR-004). |
| Retrieve with a symbol/type/date filter | Only matching documents' passages appear (FR-006). |
| Retrieve a query with nothing relevant in the corpus | Truthful empty result, not a fabricated passage (FR-008). |
| Delete the document | It disappears from retrieval; its chunks/vectors are gone (FR-016, DATA-004). |

---

## P2 acceptance — ask a question and get a cited answer

**Path**: `POST /api/v1/research/ask`

| Step | Expected |
|---|---|
| Ask a question answerable from a `READY` document | Streamed answer; every material claim cites a real retrieved passage (FR-009, AI-001). |
| Ask a question with nothing relevant in the corpus | `refused = true`; states no relevant information was found, not a general-knowledge answer (FR-010, AI-002). |
| Ask for a structured value (e.g., "what is FPT's current P/E") | Identified as outside this capability, not approximated from text (FR-011). |
| Ask a question that retrieves a prompt-injection fixture document | System behavior unaffected by the embedded instruction (FR-012, AI-003). |
| Ask the same question twice with an unchanged corpus | Same cited passage set both times; wording may differ (FR-018, `rag-v1` U-6). |

---

## P3 acceptance — submit and understand news

**Path**: `POST /api/v1/research/news`, `GET /api/v1/research/news`

| Step | Expected |
|---|---|
| Submit a news article about a known company | Classified with entities/symbols, sector, sentiment, impact, category, each labeled AI-derived (FR-013). |
| Submit an article whose sentiment/impact is ambiguous | Shows `MISSING`/undetermined rather than a guess (FR-014). |
| Retrieve/ask a query relevant to both a document and an article | Both appear, each correctly attributed (FR-005). |
| Browse/filter by symbol, category, sentiment, date | Result set matches exactly the applied filters (FR-015). |

---

## Degraded and failure paths

| Scenario | Expected |
|---|---|
| `finvera-ai`/Qdrant unavailable during ingestion | Item stays `PENDING`/`FAILED` with a reason; Features 001-005 remain fully usable. |
| No ingestion callback arrives within the processing timeout | Item marked `FAILED` with `PROCESSING_TIMEOUT` (research R-004), never stuck `PROCESSING` forever. |
| `finvera-ai`/Qdrant unavailable during retrieval/ask | `503` states retrieval is degraded, never a silent empty-corpus result. |
| A retried document/news submission with the same `Idempotency-Key` | `409 DUPLICATE_SUBMISSION` referencing the original item; no duplicate ingestion job or corpus entry (research R-013). |
| A second, separately confirmed submission with identical fields but a **fresh** `Idempotency-Key` | Accepted as a genuine second item, not treated as a duplicate. |

## Authorization checks

| Check | Expected |
|---|---|
| Unauthenticated request to any `/research/*` endpoint | HTTP 401. |
| Any state-changing request without `X-CSRF-TOKEN` | HTTP 403, no state change. |
| A direct request to `finvera-ai`'s internal API without `X-Internal-Api-Key`, or from outside the private network | Rejected; the browser cannot reach `finvera-ai` at all (SEC-001). |
| A forged callback to `finvera-be`'s own `PATCH /internal/v1/ingestions/{id}/callback` without a valid `X-Internal-Api-Key` | Rejected `401` — `finvera-be` validates this exactly like `finvera-ai` validates its own endpoints, not merely trusted by network position (research R-003). |
| Any request missing `Idempotency-Key` on `POST /research/documents`/`POST /research/news` | HTTP 400, no state change — the header is required, not optional (research R-013). |
| Response, log, and export inspection | No credential, token, or another owner's content; provider requests carry only the minimum content needed (SEC-002). |

## Accessibility

| Check | Expected |
|---|---|
| Ingestion status, sentiment, impact states | Each has a text or icon indicator independent of colour (NFR-004). |

## AI evaluation (Constitution Principle VI)

| Check | Expected |
|---|---|
| Retrieval evaluation dataset (research R-010) | At least 80% of queries return a correct passage in the top-3 (SC-002). |
| Groundedness/citation-validity check | 100% of answer citations reference actually-retrieved passages (SC-003). |
| Refusal check | 100% of no-relevant-content queries produce a refusal, not a fabricated answer (SC-003). |
| Prompt-injection fixtures | 0% alter system/model behavior (SC-004). |

## Release gates that remain open

This feature opens no new release gate for Features 001-005. It **does**
open one new pre-deployment gate of its own: `finvera-ai` must be
confirmed reachable only on the private network before this feature's
retrieval/ask endpoints are enabled — mirroring Feature 001's T051
Tailscale ingress runbook, extended to the new internal service.
