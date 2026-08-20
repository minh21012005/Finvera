# Tasks: News and Financial-Report RAG

**Input**: Design artifacts from `specs/006-news-document-rag/`
**Required**: `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/`, and `quickstart.md` — all present.
**Goal**: Deliver an owner-curated research corpus with asynchronous
ingestion into a rebuildable vector index (P1: cited retrieval), a
citation-verified LLM-synthesized answer over that corpus (P2), and
AI-classified news (P3) — the platform's first real `finvera-ai`
implementation.

## Task Format

```text
- [ ] T001 [P?] [US?] [Requirement IDs] Action with exact file path
      Verify: command or observable completion evidence
      Depends: task IDs or "none"
```

## Phase 1: Foundational Prerequisites

**Purpose**: Migration, the `finvera-ai` package scaffold (first real
code in that service), the internal API boundary, the `rag-v1`
chunking/embedding/retrieval engine, and the async ingestion pipeline —
everything every user story depends on.

- [X] T001 [P] [DATA-001 to DATA-003] Write migration tests for
      `research_document`, `news_article`, `news_article_symbol`,
      `research_chunk` (type-specific not-null groups, the "exactly one
      parent" check on `research_chunk`, cascade-delete behavior,
      `(owner_id, idempotency_key)` uniqueness and not-null on
      `research_document`/`news_article` — research R-013) in
      `finvera-be/src/test/java/com/minhnb/finvera_be/research/repository/ResearchSchemaMigrationTests.java`
      Verify: fails before the migration exists
      Depends: none
- [X] T002 [DATA-001 to DATA-003] Create the forward-only Flyway migration
      `finvera-be/src/main/resources/db/migration/V006__create_research_schema.sql`
      per `data-model.md` (including both tables' `idempotency_key` column
      and unique index), plus JPA entities and Spring Data repositories in
      `finvera-be/src/main/java/com/minhnb/finvera_be/research/entity/*.java`
      and `.../research/repository/*.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=ResearchSchemaMigrationTests test` passes
      Depends: T001
- [X] T003 [P] Reuse Feature 005's owner-scoping enforcement (research
      R-002) for the `research` module — an `OwnerScopedAccess`-style
      guard and a module architecture test asserting no direct
      cross-module entity/repository access, in
      `finvera-be/src/main/java/com/minhnb/finvera_be/research/service/OwnerScopedResearchAccess.java`
      and
      `finvera-be/src/test/java/com/minhnb/finvera_be/research/ResearchModuleArchitectureTests.java`
      Verify: unit tests confirm a wrong-owner id and a nonexistent id
      produce the identical outcome, matching Feature 005's own tests
      Depends: T002
- [X] T004 [P] Scaffold the `finvera-ai` package structure
      `finvera-ai/AGENTS.md` already specifies — `app/api`, `app/core`
      (settings, internal-API-key auth dependency), `app/features/document`,
      `app/features/rag`, `app/infrastructure/llm`,
      `app/infrastructure/qdrant`, `app/infrastructure/loaders` — plus a
      FastAPI app entrypoint replacing the placeholder `main.py`; this is
      the first real code in this service
      Verify: `cd finvera-ai; uv run python -m compileall .` passes; the
      app starts and responds to a health check
      Depends: none
- [X] T005 [P] Add `pypdf`, a Qdrant client, and a Gemini SDK to
      `finvera-ai/pyproject.toml`; pin the exact generally-available
      Gemini generation and embedding model versions (ADR-0008, plan.md
      Open Item 1) in `app/core/settings.py`
      Verify: `cd finvera-ai; uv sync` succeeds; `uv run python -m
      compileall .` passes
      Depends: T004
- [X] T006 [P] [SEC-001, SEC-002] Implement the internal-API-key
      authentication **in both directions** (research R-003, hardened
      during pre-implementation analysis — F1): (a) `finvera-ai`'s
      inbound dependency (`app/core/auth.py`, rejecting any request
      missing or presenting an invalid `X-Internal-Api-Key`); (b)
      `finvera-be`'s own inbound filter/interceptor protecting every path
      under `/internal/v1/**` it hosts (currently only the ingestion
      callback), identical rejection behavior — this is the one `finvera-be`
      does not merely call, it also receives; (c) the `finvera-be`
      internal-API HTTP client that attaches the key on every outbound
      call (provider package, explicit timeout/bounded retry per
      Constitution Principle VII) in
      `finvera-be/src/main/java/com/minhnb/finvera_be/research/provider/ResearchAiClient.java`
      and
      `finvera-be/src/main/java/com/minhnb/finvera_be/research/config/InternalApiKeyFilter.java`
      Verify: an integration test confirms a missing/invalid key is
      rejected on `finvera-ai`'s endpoints, on `finvera-be`'s own hosted
      callback endpoint, and that the outbound client attaches a valid key
      Depends: T004, T005
- [X] T007 [P] [FR-005 to FR-008, FR-012, AI-003] Write the full `rag-v1`
      required-test-vector suite from `contracts/rag-v1.md` — chunking
      (page/paragraph boundaries, overlap, sub-20-token discard), the
      rerank score formula, citation verification (steps 1-5, including
      an out-of-range `blockRef`), a prompt-injection fixture, and replay
      determinism (U-6) — in `finvera-ai/app/features/rag/tests/`
      Verify: fails before the engine exists; every required-test-vector
      row from `rag-v1.md` has an assertion
      Depends: T004
- [X] T008 [FR-001, FR-003, FR-004] Implement PDF/text extraction and
      chunking (`app/infrastructure/loaders/pdf.py`,
      `app/features/document/chunking.py`) per `rag-v1.md`'s chunking
      rules, including the unparseable-PDF failure path
      Verify: unit tests confirm known-content PDF/text fixtures chunk
      correctly and a no-text PDF fails with a stated reason
      Depends: T005, T007
- [X] T009 [DATA-002] Implement the Qdrant collection setup
      (`app/infrastructure/qdrant/collection.py`, `research_chunks_v1`
      per `data-model.md`) and the Gemini embedding adapter
      (`app/infrastructure/llm/embedding.py`, document vs. query task
      type, ADR-0008)
      Verify: integration test against a local/fixture Qdrant confirms a
      point upsert and a filtered nearest-neighbor query round-trip
      Depends: T005
- [X] T010 [FR-003, FR-005 to FR-007] Implement retrieval and the
      deterministic reranker (`app/features/rag/retrieval.py`) per
      `rag-v1.md`'s scoring formula, and
      `POST /internal/v1/retrieve` per `contracts/internal-api.openapi.yaml`
      Verify: `cd finvera-ai; uv run pytest app/features/rag` passes the
      T007 suite's retrieval/rerank cases
      Depends: T008, T009
- [X] T011 [FR-003, NFR-003] Implement ingestion orchestration
      (`app/features/document/ingestion.py`: parse -> chunk -> embed ->
      index, run via FastAPI `BackgroundTasks`, research R-004) and
      `POST /internal/v1/ingestions` per
      `contracts/internal-api.openapi.yaml`
      Verify: an integration test submits a fixture document and observes
      the background job reach a terminal state without blocking the
      request
      Depends: T008, T009, T006
- [X] T012 [FR-003, NFR-003, SEC-001] Implement the `finvera-be` side of
      the ingestion callback —
      `PATCH /internal/v1/ingestions/{researchItemId}/callback` handler
      (behind T006's inbound `InternalApiKeyFilter` — this endpoint MUST
      NOT be reachable without a valid key) persisting `READY`'s chunks or
      `FAILED`'s reason, plus a bounded processing-timeout job (default 10
      minutes, `finvera.research.ingestion-timeout`) marking a stuck
      `PROCESSING` item `FAILED` with `PROCESSING_TIMEOUT` (research
      R-004) — in
      `finvera-be/src/main/java/com/minhnb/finvera_be/research/service/IngestionCallbackService.java`
      Verify: integration tests cover a successful callback, a `FAILED`
      callback, a timeout with no callback at all, and a callback attempt
      with a missing/invalid key rejected before reaching the service
      Depends: T002, T006, T011

**Checkpoint**: The `rag-v1` engine, schema, internal API, and async
ingestion pipeline exist and are independently tested; no public endpoint
exists yet.

---

## Phase 2: User Story 1 - Ingest a Document and Find Cited Passages (Priority: P1)

**Goal**: Owner uploads a research document and retrieves ranked, cited
passages from their own corpus.
**Requirements**: FR-001, FR-003, FR-004, FR-005 to FR-008, FR-015 to
FR-018, DATA-001 to DATA-004, SEC-001, SEC-002, NFR-001, NFR-004
**Independent Test**: Upload a document with known content; once `READY`,
retrieve a query whose answer is in it and verify the correct cited
passage; upload a no-text PDF and verify `FAILED`.

### Tests and Evaluation

- [X] T013 [P] [US1] [FR-001, FR-003, FR-004, FR-016] Write
      `ResearchDocumentService` tests — submit (file and pasted-text
      paths), status transitions, delete with chunk/vector cascade, a
      replayed `Idempotency-Key` rejected `DUPLICATE_SUBMISSION` with zero
      additional entry, a different key with identical fields accepted as
      genuine (research R-013) — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/research/service/ResearchDocumentServiceTests.java`
      Verify: fails before the service exists
      Depends: T002, T012
- [X] T014 [P] [US1] [FR-005 to FR-008, FR-015] Write `RetrievalService`
      integration tests — filter application, `content_text` resolution
      from PostgreSQL, empty-result truthfulness — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/research/service/RetrievalServiceTests.java`
      Verify: fails before the service exists
      Depends: T010
- [X] T015 [P] [US1] [SEC-001, SEC-002] Write owner-only contract/security
      tests for `POST/GET /research/documents`, `DELETE
      /research/documents/{id}`, `POST /research/retrieve` in
      `finvera-be/src/test/java/com/minhnb/finvera_be/research/controller/ResearchDocumentControllerTests.java`
      Verify: fails before the controller exists
      Depends: T003

### Implementation

- [X] T016 [US1] [FR-001, FR-003, FR-004, FR-016, FR-018] Implement
      `ResearchDocumentService` (submit via T006's client, checking
      `(owner_id, idempotency_key)` before creating any row or ingestion
      job — research R-013 — list, get, delete with
      `/internal/v1/vectors` cleanup) in
      `finvera-be/src/main/java/com/minhnb/finvera_be/research/service/ResearchDocumentService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=ResearchDocumentServiceTests test` passes
      Depends: T003, T013
- [X] T017 [US1] [FR-005 to FR-008, FR-015] Implement `RetrievalService`
      (call T010's `/internal/v1/retrieve`, resolve `content_text` and
      format `location` from `research_chunk.pageNumber`/`paragraphIndex`
      — the sole authoritative source, never a Qdrant-derived string, F4
      — for returned chunk ids, assemble cited `Passage` responses) in
      `finvera-be/src/main/java/com/minhnb/finvera_be/research/service/RetrievalService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=RetrievalServiceTests test` passes
      Depends: T010, T014
- [X] T018 [US1] [FR-001 to FR-008, FR-015, FR-016, SEC-001, SEC-002]
      Implement DTO mapping and controllers for `/research/documents`,
      `/research/documents/{id}`, `/research/retrieve` per
      `contracts/public-api.openapi.yaml` — including binding the required
      `Idempotency-Key` header on `submitDocument` and mapping
      `DUPLICATE_SUBMISSION` to `409` (research R-013) — in
      `finvera-be/src/main/java/com/minhnb/finvera_be/research/dto/*.java`
      and `ResearchDocumentController.java`, `ResearchRetrievalController.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=ResearchDocumentControllerTests test` passes
      Depends: T015, T016, T017
- [X] T019 [P] [US1] Implement browser API types, runtime response
      validation, and a same-origin, CSRF-token-attaching client (reusing
      `auth/api/owner-access.ts`'s `getCsrf()`) that generates one
      `Idempotency-Key` (UUID) per logical submit action and resends the
      same key only on that action's own retries (research R-013) in
      `finvera-fe/src/features/research/api/documents.ts`,
      `retrieve.ts`
      Verify: frontend unit tests reject malformed responses and confirm a
      retried submit reuses the same key while a fresh submit generates a
      new one
      Depends: T018
- [X] T020 [P] [US1] Write document-upload and retrieval-results component
      tests — upload form (file and pasted text), status display,
      citation display, empty-result state, non-color status indicator —
      in `finvera-fe/src/features/research/documents.test.tsx`
      Verify: fails before the components exist
      Depends: none
- [X] T021 [US1] Implement the documents/retrieval UI — upload form,
      document list, retrieval search box and cited results — in
      `finvera-fe/src/features/research/components/document-upload.tsx`,
      `document-list.tsx`, `retrieval-results.tsx`,
      `research-page.tsx`; register the route and nav link
      Verify: `cd finvera-fe; npm run test -- src/features/research/documents.test.tsx`
      passes
      Depends: T019, T020
- [X] T022 [US1] Add Playwright P1 journeys — upload, wait for `READY`,
      retrieve with a correct citation, upload a no-text PDF and see
      `FAILED`, filter by symbol/type/date, delete and confirm absence —
      in `finvera-fe/tests/e2e/research-documents.spec.ts`
      Verify: `npm run test:e2e -- --grep "P1"` passes
      Depends: T018, T021

**Checkpoint**: P1 is independently usable — ingestion and cited
retrieval work without question-answering (US2) or news (US3).

---

## Phase 3: User Story 2 - Ask a Question and Get a Cited Answer (Priority: P2)

**Goal**: Owner asks a question and receives a streamed, citation-
verified answer grounded in their own corpus, or a truthful refusal.
**Requirements**: FR-009 to FR-012, FR-018, AI-001 to AI-003, SEC-001,
SEC-002, NFR-002, NFR-004
**Independent Test**: Ask a question answerable from a `READY` document
and verify every claim cites a real retrieved passage; ask an unanswerable
question and verify refusal; verify a prompt-injection fixture does not
alter behavior.

### Tests and Evaluation

- [X] T023 [P] [US2] [FR-009 to FR-012, AI-001 to AI-003] Write synthesis
      and citation-verification tests — grounded answer with correct
      citations, refusal on no-relevant-content, a structured-financial-
      value question identified as out of scope, an out-of-range
      `blockRef` stripped per `rag-v1` step 3, a prompt-injection fixture
      — in `finvera-ai/app/features/rag/tests/test_synthesis.py`
      Verify: fails before synthesis exists; every `rag-v1` synthesis
      test vector has an assertion
      Depends: T007, T010
- [X] T024 [P] [US2] [SEC-001, SEC-002] Write owner-only contract/security
      tests for the streamed `POST /research/ask` in
      `finvera-be/src/test/java/com/minhnb/finvera_be/research/controller/ResearchAskControllerTests.java`
      Verify: fails before the controller exists
      Depends: T003

### Implementation

- [X] T025 [US2] [FR-009 to FR-012, AI-001 to AI-003] Implement Gemini
      synthesis and the citation-verification algorithm (`rag-v1` steps
      1-5) in `app/features/rag/synthesis.py`, and
      `POST /internal/v1/synthesize` (SSE) per
      `contracts/internal-api.openapi.yaml`
      Verify: `cd finvera-ai; uv run pytest app/features/rag/tests/test_synthesis.py`
      passes
      Depends: T023
- [X] T026 [US2] [FR-009 to FR-012, FR-018, SEC-001, SEC-002] Implement
      `AskService` (call `RetrievalService`, resolve passage text, call
      T025's `/synthesize`) and the streamed `POST /research/ask`
      controller per `contracts/public-api.openapi.yaml`. **`delta` events
      relay verbatim; the `final` event does not** (F3) — each
      `internal-api`'s `Citation {chunkId, claimText}` MUST be resolved via
      `research_chunk` to the parent document/article before building the
      public `Citation {claimText, sourceType, sourceId, sourceTitle,
      location, source}`, with `location` formatted from
      `research_chunk.pageNumber`/`paragraphIndex` (never a Qdrant-derived
      string — F4) — in
      `finvera-be/src/main/java/com/minhnb/finvera_be/research/service/AskService.java`
      and `ResearchAskController.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=ResearchAskControllerTests test`
      passes, including an assertion that the final event's citations carry
      full source metadata, not raw `chunkId`s
      Depends: T017, T024, T025
- [X] T027 [P] [US2] Implement a browser SSE-consuming API client (CSRF-
      attaching) in `finvera-fe/src/features/research/api/ask.ts`
      Verify: frontend unit tests reject malformed stream events
      Depends: T026
- [X] T028 [P] [US2] Write ask-view component tests — streamed answer
      display, citation list, refusal state, non-color indicators — in
      `finvera-fe/src/features/research/ask.test.tsx`
      Verify: fails before the components exist
      Depends: none
- [X] T029 [US2] Implement the ask UI (question box, streamed answer,
      citation list, refusal state) in
      `finvera-fe/src/features/research/components/ask-panel.tsx`, wired
      into `research-page.tsx`
      Verify: `cd finvera-fe; npm run test -- src/features/research/ask.test.tsx`
      passes
      Depends: T027, T028
- [X] T030 [US2] Add Playwright P2 journeys — grounded answer with
      citations, refusal on no-evidence, structured-value question
      identified as out of scope — in
      `finvera-fe/tests/e2e/research-ask.spec.ts`
      Verify: `npm run test:e2e -- --grep "P2"` passes and P1 remains
      green
      Depends: T026, T029

**Checkpoint**: P2 works on top of US1's retrieval; independent of news
(US3).

---

## Phase 4: User Story 3 - Submit and Understand News (Priority: P3)

**Goal**: Owner submits a news article and sees it classified and made
retrievable/citable the same way as a document.
**Requirements**: FR-002, FR-003, FR-013 to FR-015, SEC-001, SEC-002,
NFR-001, NFR-004
**Independent Test**: Submit a news article about a known company; verify
classification fields and that it becomes retrievable/citable identically
to a document.

### Tests and Evaluation

- [X] T031 [P] [US3] [FR-002, FR-013, FR-014] Write news classification
      tests — entities/symbols, sector, sentiment, impact, category, and
      the `MISSING`/undetermined path on low confidence — in
      `finvera-ai/app/features/document/tests/test_news_classification.py`
      Verify: fails before classification exists
      Depends: T007, T011
- [X] T032 [P] [US3] [FR-002, FR-003, FR-015] Write `NewsArticleService`
      tests — submit, status transitions, list/filter by symbol/category/
      sentiment/date, the same `Idempotency-Key` replay/fresh-key behavior
      as `ResearchDocumentServiceTests` (research R-013) — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/research/service/NewsArticleServiceTests.java`
      Verify: fails before the service exists
      Depends: T002, T012
- [X] T033 [P] [US3] [SEC-001, SEC-002] Write owner-only contract/security
      tests for `/research/news` in
      `finvera-be/src/test/java/com/minhnb/finvera_be/research/controller/NewsArticleControllerTests.java`
      Verify: fails before the controller exists
      Depends: T003

### Implementation

- [X] T034 [US3] [FR-013, FR-014] Implement news classification
      (`app/features/document/classification.py`, a schema-validated
      Gemini call) wired into T011's ingestion pipeline for
      `itemType = NEWS_ARTICLE`
      Verify: `cd finvera-ai; uv run pytest app/features/document/tests/test_news_classification.py`
      passes
      Depends: T031
- [X] T035 [US3] [FR-002, FR-003, FR-015] Implement `NewsArticleService`
      (submit — checking `(owner_id, idempotency_key)` per research R-013
      before creating any row or ingestion job — list/filter, get, delete
      with vector cleanup) in
      `finvera-be/src/main/java/com/minhnb/finvera_be/research/service/NewsArticleService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=NewsArticleServiceTests test` passes
      Depends: T032, T034
- [X] T036 [US3] [FR-002, FR-013 to FR-015, SEC-001, SEC-002] Implement
      DTO mapping and controller for `/research/news` per
      `contracts/public-api.openapi.yaml` — including binding the required
      `Idempotency-Key` header on `submitNewsArticle` and mapping
      `DUPLICATE_SUBMISSION` to `409` (research R-013) — in
      `finvera-be/src/main/java/com/minhnb/finvera_be/research/dto/NewsArticle*.java`
      and `NewsArticleController.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=NewsArticleControllerTests test` passes
      Depends: T033, T035
- [X] T037 [P] [US3] Implement browser API client for news (generating and
      reusing an `Idempotency-Key` per submit action, identically to
      T019 — research R-013) in
      `finvera-fe/src/features/research/api/news.ts`
      Verify: frontend unit tests reject malformed responses
      Depends: T036
- [X] T038 [P] [US3] Write news component tests — submit form, list with
      filters, classification display including undetermined state — in
      `finvera-fe/src/features/research/news.test.tsx`
      Verify: fails before the components exist
      Depends: none
- [X] T039 [US3] Implement the news UI — submit form, filterable list,
      classification display — in
      `finvera-fe/src/features/research/components/news-submit.tsx`,
      `news-list.tsx`; wire into `research-page.tsx`
      Verify: `cd finvera-fe; npm run test -- src/features/research/news.test.tsx`
      passes; `npm run build` succeeds
      Depends: T037, T038
- [X] T040 [US3] Add Playwright P3 journeys — submit and see
      classification, undetermined-confidence display, cross-type
      retrieval (document + article), filter by symbol/category/
      sentiment/date — in `finvera-fe/tests/e2e/research-news.spec.ts`
      Verify: `npm run test:e2e -- --grep "P3"` passes and P1/P2 remain
      green
      Depends: T036, T039

**Checkpoint**: Every selected story works independently against the
T007/T013/T023/T031 fixture set.

---

## Final Phase: Cross-Cutting Validation and Release Readiness

- [ ] T041 [Requirement IDs: all] Run and reconcile all three contracts
      (`rag-v1.md`, `internal-api.openapi.yaml`, `public-api.openapi.yaml`);
      update only if owner-approved behavior changed
      Verify: contract tests pass; no implemented response differs from
      the reviewed contracts
      Depends: T022, T030, T040
- [ ] T042 [NFR-001, NFR-002, NFR-003] Add latency smoke tests against a
      realistically sized fixture corpus in
      `finvera-be/src/test/java/com/minhnb/finvera_be/research/performance/ResearchPerformanceTests.java`
      and `finvera-ai/app/features/rag/tests/test_performance.py`
      Verify: p95 within baseline for retrieval, ask-stream start, and
      confirmation ingestion never blocks an unrelated request
      Depends: T016, T017, T025, T034
- [ ] T043 [DATA-004, FR-016] Add fault-injection tests — Qdrant/embedding
      provider unavailable during ingestion and retrieval, a missing
      ingestion callback past the processing timeout, deletion removing
      all chunks/vectors — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/research/operations/ResearchFailureTests.java`
      Verify: each case distinguishable; Features 001-005 unaffected; no
      sensitive payload
      Depends: T012, T016, T017
- [ ] T044 [FR-018] Add the replay-determinism test (`rag-v1` U-6) —
      identical corpus/`embedding_version`/query run twice — in
      `finvera-ai/app/features/rag/tests/test_determinism.py`
      Verify: `cd finvera-ai; uv run pytest app/features/rag/tests/test_determinism.py`
      passes
      Depends: T010, T025
- [ ] T045 [SEC-001, SEC-002] Add the platform's first internal-API-key
      negative security tests — missing/invalid `X-Internal-Api-Key` on
      **every `finvera-ai`-hosted endpoint AND on `finvera-be`'s own
      hosted callback endpoint** (F1's exact gap: a one-directional test
      suite would not catch a forged callback) — alongside owner/
      unauthenticated negative tests on every public endpoint and a
      missing-`Idempotency-Key` negative test (research R-013), plus a
      `finvera-fe/dist` secret scan
      Verify: only the owner/authorized internal caller succeeds in either
      direction; no credential in the bundle or in `finvera-ai` logs
      Depends: T006, T012, T018, T026, T036
- [ ] T046 [Requirement IDs: all AI-] Build and run the `rag-eval-v1`
      evaluation dataset (research R-010) end to end — retrieval quality
      (SC-002), citation validity/groundedness and refusal (SC-003),
      prompt-injection resistance (SC-004) — and record results in
      `specs/006-news-document-rag/validation/rag-eval-v1-results.md`
      Verify: 80%+ top-3 relevance, 100% citation validity, 100% correct
      refusal on no-evidence fixtures, 0% injection-induced deviation; any
      miss is fixed and rerun, not merely noted
      Depends: T022, T030, T040
- [ ] T047 [Requirement IDs: all] Execute and record the fixture-mode
      commands/scenarios in `quickstart.md`, in a new
      `specs/006-news-document-rag/validation/fixture-acceptance.md`
      Verify: P1-P3 happy paths, degraded/failure paths, authorization,
      and accessibility all produce recorded expected results
      Depends: T041, T042, T043, T045, T046
- [ ] T048 Reconcile delivered behavior and open limitations in `spec.md`,
      `plan.md` (Status line, post-design Constitution Check already
      recorded), `research.md`, `quickstart.md`, `contracts/`
      Verify: traceability review finds no orphan requirement,
      undocumented behavior, secret, or false capability claim
      Depends: T047
- [ ] T049 Run repository quality gates from `finvera-be/`, `finvera-fe/`,
      and `finvera-ai/`
      Verify: `.\mvnw.cmd test`, `npm run lint`, `npm run test`,
      `npm run build`, `npm run test:e2e`, `uv run python -m compileall .`,
      `uv run pytest` all pass
      Depends: T048
- [ ] T050 Perform the manual cross-artifact analysis pass (mirroring
      Features 004/005's post-implementation analysis, since
      `/speckit-analyze` is not an invocable skill in this harness)
      against `.agents/skills/speckit-analyze/SKILL.md`'s checklist;
      record findings and resolutions in a new "Post-Implementation
      Analysis" section of this file
      Verify: coverage summary, constitution alignment, and unmapped-task
      checks all recorded; every finding resolved or explicitly deferred
      with reason
      Depends: T049

## Dependencies and Parallel Execution

### Phase Dependencies

```text
Foundation (T001-T012)
  -> US1/P1 (T013-T022)
  -> US2/P2 (T023-T030)
  -> US3/P3 (T031-T040)
  -> Release validation (T041-T050)
```

US2 needs US1's `RetrievalService` (T017) to supply passages but not US1's
own UI beyond the shared `research-page.tsx` it extends. US3 needs
Foundation's ingestion pipeline (T011/T012) but not US1/US2's retrieval or
synthesis UI — it reuses the identical retrieval/synthesis engine once
submitted, exactly like a document.

### Parallel Opportunities

- T001, T003, T004, T005, T007 are independent of each other.
- T013/T014/T015, T023/T024, T031/T032/T033 can each be written in
  parallel with their neighbors once their `Depends` are satisfied.
- US1 (T013-T022) and the early parts of US3 (T031-T034, classification)
  may proceed in parallel by different people once Foundation is
  complete, since news classification does not depend on US1's retrieval
  UI.

## Requirement Coverage

| Requirement ID | Task IDs | Test/Evaluation Task |
|---|---|---|
| FR-001, FR-003, FR-004 | T008, T011, T016 | T007, T013 |
| FR-002 | T035 | T032 |
| FR-005 to FR-007 | T010, T017 | T007, T014 |
| FR-008 | T010, T017 | T007, T014 |
| FR-009 to FR-011 | T025, T026 | T023 |
| FR-012 | T025 | T007, T023 |
| FR-013, FR-014 | T034 | T031 |
| FR-015 | T017, T035 | T014, T032 |
| FR-016 | T016, T035 | T013, T043 |
| FR-017 | T003 | Feature 005 reuse (no new test) |
| FR-018 | T010, T025 | T007, T044 |
| DATA-001 to DATA-003 | T002, T008 | T001, T007 |
| DATA-004 | T016, T035 | T043 |
| SEC-001, SEC-002 | T003, T006, T018, T026, T036 | T015, T024, T033, T045 |
| AI-001, AI-002 | T025 | T023, T046 |
| AI-003 | T025 | T007, T023, T046 |
| AI-004 | T034 | T031, T046 |
| NFR-001, NFR-002, NFR-003 | T011, T017, T026 | T042 |
| NFR-004 | T021, T029, T039 | T020, T028, T038 |
| SC-001 | T016, T035 | T013, T032 |
| SC-002 | T010 | T046 |
| SC-003 | T025 | T023, T046 |
| SC-004 | T025 | T023, T046 |
| SC-005 | T011, T017, T026 | T042 |
| SC-006 | T016, T035 | T043 |
| SC-007 | T003, T006 | T045 |
| SC-008 | T021, T029, T039 | T020, T028, T038 |

## Pre-Implementation Analysis (2026-08-20)

`docs/SDD_WORKFLOW.md` step 6 (`/speckit-analyze`) is not registered as an
invocable skill in this harness; performed manually against
`.agents/skills/speckit-analyze/SKILL.md`'s own checklist, before any
production code exists — cross-referencing `spec.md`, `plan.md`,
`research.md`, `data-model.md`, `contracts/`, and this file, including a
machine check that both OpenAPI contracts parse and every `$ref` resolves,
and a machine diff of every `spec.md` requirement ID against this file's
Requirement Coverage table.

**Findings, all resolved by direct edits (not merely noted) before
implementation began:**

| ID | Category | Severity | Location | Summary | Resolution |
|---|---|---|---|---|---|
| F1 | Security gap | HIGH | research R-003; `internal-api.openapi.yaml` callback path; T006/T012/T045 | The ingestion callback is hosted by `finvera-be` and called by `finvera-ai`, but only `finvera-ai`'s inbound key validation and `finvera-be`'s outbound client were planned — nothing validated the key on `finvera-be`'s own hosted endpoint, and the security test task was scoped to "every `finvera-ai` endpoint" only. A forged callback could have injected fabricated "READY" content, undermining the citation-trust chain (AI-001/AI-002) | Rewrote research R-003 to require bidirectional validation explicitly; expanded T006 to implement `finvera-be`'s own inbound filter; T012 now depends on it and states the endpoint is unreachable without a valid key; T045 broadened to test both directions |
| F2 | Underspecification / latent conflict | HIGH | spec.md edge case; quickstart.md; `public-api.openapi.yaml` submission endpoints | spec.md/quickstart.md require a retried submission not to create a duplicate corpus entry, but no mechanism existed to distinguish a retry from a genuine second submission (same class of gap as Feature 005's F1) | Added research R-013 (reusing Feature 005's R-011 pattern exactly): required `Idempotency-Key` header on `submitDocument`/`submitNewsArticle`, `(owner_id, idempotency_key)` unique constraint in `data-model.md`, `409 DUPLICATE_SUBMISSION`; wired through T001/T002/T013/T016/T018/T019/T032/T035/T036/T037 |
| F3 | Inconsistency | MEDIUM | `internal-api.openapi.yaml` `AnswerResult.citations` vs `public-api.openapi.yaml` `AskFinalResult.citations`; T026 | The two citation schemas have genuinely different shapes (`{chunkId, claimText}` vs. full source metadata); T026 said "relay the SSE stream," which is incorrect for the `final` event | Added an explicit resolution requirement to research R-008 and both contracts' descriptions; rewrote T026 to require citation resolution via `research_chunk` for the `final` event only, `delta` events still relay verbatim |
| F4 | Underspecification | MEDIUM | data-model.md Qdrant payload `page_or_section` vs. `research_chunk.pageNumber`/`paragraphIndex`; `internal-api.openapi.yaml` `RetrievedChunkMeta` | Citation location existed in two parallel representations (a Qdrant-formatted string and Postgres integers) with no stated single source of truth, risking drift | Removed `pageOrSection` from `RetrievedChunkMeta` entirely; research R-006 and `data-model.md` now state Postgres's integer fields are the sole authority; T017/T026 updated to format `location` from them |
| F5 | Documentation drift | LOW | plan.md Open Item #2 (no suggested default) | Inconsistent with the resolved-default pattern Feature 005 established for its own configurable bound | Added a concrete default: `finvera.research.ingestion-timeout` = 10 minutes (research R-004, plan.md, T012) |
| F6 | Clarity | LOW | `AnswerResult`/`AskFinalResult` schemas | `rag-v1.md` step 4 already guarantees `citations` is empty when `refused = true`, but the schema did not say so | Added an explicit `description` constraint to both schemas |
| F7 (bonus) | Inconsistency | LOW | research.md R-003 referenced a nonexistent filename (`internal-ai-api.openapi.yaml` vs. the real `internal-api.openapi.yaml`) | Found while rewriting R-003 for F1 | Corrected the filename reference |
| F8 (bonus) | Inconsistency | LOW | research.md R-004 named a stale, never-implemented callback path (`PATCH /internal/v1/research-items/{id}/ingestion-status`) instead of the actual normative path used everywhere else | Found while rewriting R-004 for F5 | Corrected to `PATCH /internal/v1/ingestions/{researchItemId}/callback`, matching the contract and every other reference |

**Coverage summary**: 40/40 requirement IDs (18 FR + 4 DATA + 2 SEC + 4 AI
+ 4 NFR + 8 SC) confirmed present in the Requirement Coverage table above
via a machine diff against `spec.md`, after accounting for range notation
— 100%.
**Constitution alignment**: no MUST violation found in delivered design.
F1 touched Principle IV's ownership-enforcement intent but was a planning
gap caught and closed before any code existed, not a violation in force.
**Unmapped tasks**: none — the 15 tasks absent from the Requirement
Coverage table (T004, T005, T009, T012, T019, T022, T027, T030, T037,
T040, T041, T047-T050) are foundational infrastructure, frontend/E2E
companions to an already-mapped backend task, or final-phase cross-cutting
validation, mirroring exactly which tasks Features 004/005's own tasks.md
left unmapped.
**Critical issues**: 0.

**Outcome**: All eight findings were fixed in the design artifacts
(`research.md`, `data-model.md`, `contracts/`, `plan.md`, `quickstart.md`,
this file) before Foundation work begins. F1 and F2 were the most
consequential — F1 closes a real forged-callback vector that would have
undermined this feature's entire grounding/citation trust model, and F2
closes the same class of unimplementable-success-criterion gap already
found and fixed in Feature 005.

## Delivery Notes

- Suggested MVP is Foundation + US1 (T001-T022): ingestion, the vector
  index, and cited retrieval — the smallest slice for which "grounded
  search over my own corpus" is real and demonstrable.
- US2 (T023-T030) adds the synthesized-answer layer on top of US1's
  retrieval; US3 (T031-T040) adds a second content type and its own
  classification. Either can follow the other since news classification
  does not depend on question-answering.
- This is the first feature with real evaluation-gated release criteria
  (T046) beyond conventional tests — do not treat the AI evaluation
  dataset as optional polish; SC-002/SC-003/SC-004 are release-blocking
  success criteria, not aspirational metrics.
- Never mark a task complete based only on code presence. Record its
  stated verification evidence.
- If implementation discovery changes behavior, update `spec.md`/`plan.md`
  before continuing, per `AGENTS.md`.
