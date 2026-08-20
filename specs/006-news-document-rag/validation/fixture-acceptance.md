# Fixture-Mode Acceptance Validation: Feature 006 (News & Document RAG)

**Target Feature**: `006-news-document-rag`
**Environment**: Local Fixture Mode (`fixture-acceptance`)
**Execution Date**: 2026-08-20
**Validation Type**: End-to-End User Story Walkthrough (P1, P2, P3) & Operational Failure Scenarios

---

## 1. User Story Acceptance Testing (P1 - P3)

### Scenario 1: User Story 1 (P1 - Ingest Document & Retrieve Passages)
- **Action**: Submit a 10-page financial report PDF and execute semantic passage search.
- **Expected Outcome**:
  - `POST /api/v1/research/documents` returns `201 Created` with `ingestionStatus = PENDING`.
  - Backend background ingestion chunks text per page, computes Gemini embeddings, indexes vectors into Qdrant collection `research_chunks_v1`.
  - Webhook callback updates document status to `READY`.
  - `GET /api/v1/research/passages` returns top passages with similarity score, page number, and authoritative text from PostgreSQL.
- **Result**: **PASS** (Covered by `ResearchDocumentControllerTests`, `RetrievalServiceTests`, `test_rag_v1_vectors.py`, Playwright E2E `research-documents.spec.ts`).

---

### Scenario 2: User Story 2 (P2 - Ask a Question & Get a Cited Answer)
- **Action**: Ask "Doanh thu của FPT năm 2025 đạt bao nhiêu?" and "Tính chỉ số RSI của FPT".
- **Expected Outcome**:
  - `POST /api/v1/research/ask` streams Server-Sent Events (`text/event-stream`).
  - `delta` events stream Markdown answer text with glowing pulsing cursor.
  - `final` event carries verified citations resolving to `Page X` in PostgreSQL.
  - Zero hallucination: out-of-range blocks stripped; zero-evidence queries result in truthful refusal banner.
  - RSI query triggers redirection disclaimer to deterministic engine.
- **Result**: **PASS** (Covered by `ResearchAskControllerTests`, `AskServiceTests`, `test_synthesis.py`, Playwright E2E `research-ask.spec.ts`).

---

### Scenario 3: User Story 3 (P3 - Submit & Understand News)
- **Action**: Paste a news article about company performance and inspect automated classification.
- **Expected Outcome**:
  - `POST /api/v1/research/news` accepts body with mandatory `Idempotency-Key` header.
  - Gemini classification adapter extracts Category (`COMPANY`), Sentiment (`POSITIVE`), Impact (`HIGH`), Sector (`Technology`), Entities (`FPT`).
  - Low-confidence or unclassifiable text preserves `applicability = MISSING` with accessible `Chưa xác định` badge.
  - Deleting article cascades to all chunks and deletes vectors from Qdrant.
- **Result**: **PASS** (Covered by `NewsArticleControllerTests`, `NewsArticleServiceTests`, `test_news_classification.py`, Playwright E2E `research-news.spec.ts`).

---

## 2. Operational & Security Verification

| Test Scenario | Verification Method | Result |
| :--- | :--- | :---: |
| **Owner Isolation (SEC-001)** | Unauthenticated request returns `401 Unauthorized`. Owner A cannot access Owner B's documents/articles. | **PASS** |
| **CSRF Enforcement (SEC-002)** | State-modifying requests without valid CSRF header return `403 Forbidden`. | **PASS** |
| **Internal API Key (SEC-001)** | Bidirectional calls (`finvera-be` $\leftrightarrow$ `finvera-ai`) reject missing/invalid `X-Internal-Api-Key` with `401`. | **PASS** |
| **Idempotency Replay (FR-018)** | Replayed `Idempotency-Key` returns `409 Conflict` (`DUPLICATE_SUBMISSION`) with original item ID. | **PASS** |
| **Failure Degradation (NFR-004)** | Microservice outage returns RFC 7807 problem details (`RETRIEVAL_UNAVAILABLE`), never silent empty result. | **PASS** |
| **Processing Timeout Reaper** | Timed-out ingestion jobs are marked `FAILED` with `PROCESSING_TIMEOUT`. | **PASS** |
| **Secret Scan (T045)** | Zero secrets or credential strings present in production JS bundle. | **PASS** |
