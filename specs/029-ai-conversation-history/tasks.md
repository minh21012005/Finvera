# Tasks: AI Conversation History

**Input**: Design artifacts from `specs/029-ai-conversation-history/`

**Required**: `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/`, and `quickstart.md`

**Goal**: Deliver durable owner-scoped AI conversations with bounded sliding
context and exact historical answer/evidence replay

## Task Format

```text
- [ ] T001 [P?] [US?] [Requirement IDs] Action with exact file path
      Verify: command or observable completion evidence
      Depends: task IDs or "none"
```

## Phase 1: Contract and Compatibility Baseline

**Purpose**: Freeze the additive API and the unchanged standalone behavior
before production changes.

- [ ] T001 [FR-001-FR-011, DATA-001-DATA-005, SEC-001-SEC-004, AI-001-AI-005] Parse and review `specs/029-ai-conversation-history/contracts/public-api.openapi.yaml` and `specs/029-ai-conversation-history/contracts/context-window-v1.md` against `spec.md`, including external Feature 007 schema references and error/status invariants.
      Verify: YAML parses; contract review records no unresolved schema/reference or requirement mismatch in `specs/029-ai-conversation-history/quickstart.md`
      Depends: none

- [ ] T002 [P] [FR-011] Add regression assertions proving `POST /api/v1/analyst/ask` remains stateless and keeps its existing SSE request/event contract in `finvera-be/src/test/java/com/minhnb/finvera_be/analyst/AnalystControllerTests.java` and `finvera-fe/src/features/analyst/ask-analyst.test.tsx`.
      Verify: focused backend and frontend Feature 007 tests fail if the legacy endpoint creates or requires a conversation
      Depends: T001

- [ ] T003 [P] [AI-001-AI-005, SEC-004] Add internal-contract boundary tests for 0-5 `priorTurns`, aggregate size validation, chronological ordering, and zero-history compatibility in `finvera-ai/app/features/chat/tests/test_conversation_context.py`.
      Verify: `uv run pytest app/features/chat/tests/test_conversation_context.py` fails until context-window-v1 validation exists
      Depends: T001

**Checkpoint**: New public behavior is additive, and old standalone behavior is
protected before implementation.

---

## Phase 2: Foundational Persistence, Policies, and Privacy

**Purpose**: Establish schema, pure policies, repository boundaries, and safe
signals required by every story.

- [ ] T004 [DATA-001, DATA-002, DATA-004, DATA-005, FR-009, FR-010, SEC-001] Add forward migration tests for table shape, checks, indexes, cascade direction, no-backfill behavior, and compatibility after V020 in `finvera-be/src/test/java/com/minhnb/finvera_be/analyst/AnalystConversationMigrationTests.java`.
      Verify: `.\mvnw.cmd test "-Dtest=AnalystConversationMigrationTests"` fails before V021 and asserts unrelated legacy audit rows survive
      Depends: T001

- [ ] T005 [DATA-001, DATA-002, DATA-004, DATA-005, FR-009, FR-010, SEC-001] Create `finvera-be/src/main/resources/db/migration/V021__create_analyst_conversation_history.sql` with conversation/exchange constraints, owner-request idempotency, one-processing partial index, keyset indexes, nullable audit link, and cascade semantics from `data-model.md`.
      Verify: `.\mvnw.cmd test "-Dtest=AnalystConversationMigrationTests"`
      Depends: T004

- [ ] T006 [P] [FR-001-FR-010, DATA-001-DATA-005, SEC-001] Add lifecycle enums, JPA entities, and owner-qualified repositories in `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/domain/ConversationExchangeStatus.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/entity/AnalystConversationEntity.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/entity/AnalystConversationExchangeEntity.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/repository/AnalystConversationRepository.java`, and `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/repository/AnalystConversationExchangeRepository.java`.
      Verify: backend compilation plus repository slice tests confirm no unbounded or non-owner read method is exposed to the service
      Depends: T005

- [ ] T007 [P] [AI-001, AI-002, AI-005, FR-007] Write pure boundary/property tests for title normalization and context-window-v1 in `finvera-be/src/test/java/com/minhnb/finvera_be/analyst/ConversationTitlePolicyTests.java` and `finvera-be/src/test/java/com/minhnb/finvera_be/analyst/ConversationContextWindowPolicyTests.java`.
      Verify: focused tests initially fail for 0/1/5/6/10/11 pairs, exact 12,000, overflow-stop/contiguous-suffix, failed, Unicode, oversized-newest-empty, and title 79/80/81/120/121 boundaries
      Depends: T001

- [ ] T008 [AI-001, AI-002, AI-005, FR-007] Implement dependency-free `ConversationTitlePolicy` and `ConversationContextWindowPolicy` in `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/domain/` with rule version and selection metadata.
      Verify: `.\mvnw.cmd test "-Dtest=ConversationTitlePolicyTests,ConversationContextWindowPolicyTests"`
      Depends: T007

- [ ] T009 [P] [NFR-005, SEC-003, AI-001] Extend bounded context and stale-processing settings in `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/config/AnalystProperties.java`, `finvera-be/src/main/resources/application.yaml`, and `finvera-be/src/test/resources/application-test.yaml`; add privacy-safe meter names without content tags.
      Verify: configuration tests cover defaults/invalid bounds and a static review finds no title/question/answer/citation metric tag or log argument
      Depends: T001

**Checkpoint**: The database enforces ownership-related structure,
idempotency, and lifecycle invariants; pure bounded policies pass independently.

---

## Phase 3: User Story 1 - Continue a Durable Conversation (Priority: P1)

**Goal**: Create on first question, persist terminal exchanges, continue using
context-window-v1, and retain current evidence rules.

**Requirements**: FR-001-FR-003, FR-006, FR-009-FR-011, DATA-002-DATA-004,
SEC-001-SEC-004, AI-001-AI-005, NFR-002, NFR-005

**Independent Test**: Initial ask plus six follow-ups survives refresh; full
history is stored while only the bounded recent context is sent, and a
current-price follow-up calls the current tool.

### Tests and Evaluation

- [ ] T010 [US1] [FR-001-FR-003, FR-006, FR-009-FR-011, DATA-002-DATA-004, SEC-001-SEC-004, NFR-002, NFR-005] Add service lifecycle, final-snapshot, same-payload replay, mismatched-idempotency conflict, concurrency, stale-processing, failure/cancel, log-canary, and legacy-regression tests in `finvera-be/src/test/java/com/minhnb/finvera_be/analyst/AnalystConversationServiceTests.java`.
      Verify: `.\mvnw.cmd test "-Dtest=AnalystConversationServiceTests"` fails before conversation orchestration exists
      Depends: T006, T008, T009

- [ ] T011 [P] [US1] [FR-001-FR-003, FR-006, FR-009-FR-011, SEC-001, SEC-002] Add public DTO/validation and SSE controller contract tests for accepted-first, final/error, new/existing/unknown/non-owned/busy requests in `finvera-be/src/test/java/com/minhnb/finvera_be/analyst/AnalystConversationControllerTests.java`.
      Verify: `.\mvnw.cmd test "-Dtest=AnalystConversationControllerTests"` fails before the new API exists
      Depends: T006, T008

- [ ] T012 [P] [US1] [AI-003, AI-004, DATA-003, SEC-004] Add versioned stale-fact, referential-follow-up, history-injection, zero-history, and tool-outage cases in `finvera-ai/app/features/chat/tests/fixtures/conversation_context_v1.json` and assertions in `finvera-ai/app/features/chat/tests/test_conversation_context.py`.
      Verify: targeted pytest fails if prior prose becomes evidence/instruction or if current-fact tools are skipped
      Depends: T003

- [ ] T013 [P] [US1] [FR-001-FR-003, FR-006, FR-009, NFR-004] Add frontend API/component tests for new chat, accepted-first streaming, follow-up, exact reopened result rendering, processing/failed/cancelled states, and legacy cards in `finvera-fe/src/features/analyst/conversation-history.test.tsx`.
      Verify: `npm test -- --run src/features/analyst/conversation-history.test.tsx` fails before UI/API changes
      Depends: T001

### Implementation

- [ ] T014 [US1] [FR-001-FR-003, FR-006, FR-009-FR-011, DATA-002-DATA-005, SEC-001-SEC-004, NFR-002, NFR-005] Implement transactional create/continue/idempotent replay, context selection, sanitized final persistence, audit linkage/deletion support, stale reconciliation, and terminal failure callbacks in `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/service/AnalystConversationService.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/service/AnalystQueryService.java`.
      Verify: `.\mvnw.cmd test "-Dtest=AnalystConversationServiceTests,AnalystQueryServiceTests"`
      Depends: T010

- [ ] T015 [US1] [FR-001-FR-003, FR-006, FR-009-FR-011, NFR-002] Refactor the existing stream pipeline to accept server-owned prior turns and terminal lifecycle callbacks without duplicating orchestration in `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/service/AnalystService.java`.
      Verify: `AnalystServiceTests`, `AnalystConversationServiceTests`, and legacy `AnalystControllerTests` pass; standalone asks create no conversation
      Depends: T014, T002

- [ ] T016 [US1] [FR-001-FR-003, FR-006, FR-009, FR-010, SEC-001, SEC-002] Add `AnalystConversationDtos` and the conversation stream endpoint in `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/dto/AnalystConversationDtos.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/controller/AnalystConversationController.java`.
      Verify: `.\mvnw.cmd test "-Dtest=AnalystConversationControllerTests,AnalystConversationServiceTests,AnalystControllerTests"`
      Depends: T011, T015

- [ ] T017 [US1] [AI-001-AI-005, DATA-003, SEC-004] Validate context-window-v1 bounds and inject explicitly untrusted historical context into every relevant planner/synthesis prompt in `finvera-ai/app/features/chat/service.py`, without changing tool allowlist or accepting history as claim evidence.
      Verify: `uv run pytest app/features/chat/tests/test_conversation_context.py app/features/chat/tests/test_ask_orchestration.py app/features/orchestration/tests/test_attribution.py`
      Depends: T012

- [ ] T018 [US1] [FR-001-FR-003, FR-006, FR-009, FR-010] Implement typed conversation stream/list/read methods and remove client-authoritative `priorTurns` from the new flow in `finvera-fe/src/features/analyst/api/analyst.ts`.
      Verify: TypeScript compile plus conversation API mocks match `conversation-history-v1`
      Depends: T016

- [ ] T019 [US1] [FR-001-FR-003, FR-006, FR-009, NFR-004] Convert `finvera-fe/src/features/analyst/components/AskAnalyst.tsx` into a multi-exchange transcript flow and add `finvera-fe/src/features/analyst/components/ConversationTranscript.tsx` for complete/processing/failed/cancelled answer and evidence display.
      Verify: `npm test -- --run src/features/analyst/conversation-history.test.tsx src/features/analyst/ask-analyst.test.tsx`
      Depends: T013, T018

**Checkpoint**: P1 works independently. A durable first/follow-up conversation
uses bounded context, survives refresh via API, and keeps current evidence rules.

---

## Phase 4: User Story 2 - Browse and Reopen Past Research (Priority: P2)

**Goal**: Browse stable conversation pages and prepend older exchanges without
duplicates or reordered history.

**Requirements**: FR-004-FR-006, DATA-002, SEC-001, SEC-002, NFR-001,
NFR-003-NFR-005

**Independent Test**: Multiple conversation and exchange pages reopen the exact
stored historical response at scale and across same-time ordering boundaries.

### Tests and Evaluation

- [ ] T020 [P] [US2] [FR-004-FR-006, DATA-002, SEC-001, SEC-002, NFR-001, NFR-003] Add owner-qualified keyset/cursor repository and service tests, including tied timestamps and 10,000-record fixtures, in `finvera-be/src/test/java/com/minhnb/finvera_be/analyst/AnalystConversationPaginationTests.java` and `finvera-be/src/test/java/com/minhnb/finvera_be/analyst/AnalystConversationPerformanceTests.java`.
      Verify: focused tests fail for offset/unbounded/non-owner queries and record p95 read evidence
      Depends: T006

- [ ] T021 [P] [US2] [FR-004-FR-006, NFR-004] Extend frontend tests for list paging, active selection, refresh/reopen, older-exchange prepend, exact evidence/as-of rendering, empty/loading/error states, keyboard navigation, and focus in `finvera-fe/src/features/analyst/conversation-history.test.tsx`.
      Verify: focused Vitest fails before sidebar/read behavior exists
      Depends: T013

### Implementation

- [ ] T022 [US2] [FR-004-FR-006, DATA-002, SEC-001, SEC-002, NFR-001, NFR-003, NFR-005] Implement validated opaque cursors, bounded owner-qualified list/exchange queries, exact public snapshot mapping, and read timing metrics in `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/repository/AnalystConversationRepository.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/repository/AnalystConversationExchangeRepository.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/service/AnalystConversationService.java`, and `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/controller/AnalystConversationController.java`.
      Verify: `.\mvnw.cmd test "-Dtest=AnalystConversationPaginationTests,AnalystConversationPerformanceTests,AnalystConversationControllerTests"`
      Depends: T020, T016

- [ ] T023 [US2] [FR-004-FR-006, NFR-004] Add responsive `ConversationSidebar` and connect list/select/older-page state in `finvera-fe/src/features/analyst/components/ConversationSidebar.tsx`, `finvera-fe/src/features/analyst/components/ConversationTranscript.tsx`, and `finvera-fe/src/features/analyst/components/AskAnalyst.tsx`.
      Verify: `npm test -- --run src/features/analyst/conversation-history.test.tsx src/features/analyst/ask-analyst.test.tsx`; keyboard and 414px layout assertions pass
      Depends: T021, T022

**Checkpoint**: P2 works independently after foundation; full history remains
bounded per request and exact per stored page.

---

## Phase 5: User Story 3 - Organize and Delete Conversations (Priority: P3)

**Goal**: Deterministic automatic titles plus owner rename and complete hard
delete lifecycle.

**Requirements**: FR-007, FR-008, DATA-001, DATA-005, SEC-001-SEC-003,
NFR-004, NFR-005

**Independent Test**: Rename at boundaries, reject cross-owner access, block a
processing delete, then delete terminal conversation and verify every linked
private row disappears.

### Tests and Evaluation

- [ ] T024 [P] [US3] [FR-007, FR-008, DATA-001, DATA-005, SEC-001-SEC-003, NFR-005] Add rename/delete/controller/cascade/log-canary tests in `finvera-be/src/test/java/com/minhnb/finvera_be/analyst/AnalystConversationAuthorizationTests.java` and `finvera-be/src/test/java/com/minhnb/finvera_be/analyst/AnalystConversationServiceTests.java`.
      Verify: focused backend tests fail before lifecycle endpoints and complete cascade exist
      Depends: T005, T008, T014

- [ ] T025 [P] [US3] [FR-007, FR-008, NFR-004] Add frontend rename, confirmation, busy-delete, success focus, and failure-state tests in `finvera-fe/src/features/analyst/conversation-history.test.tsx`.
      Verify: focused Vitest fails before controls exist
      Depends: T021

### Implementation

- [ ] T026 [US3] [FR-007, FR-008, DATA-001, DATA-005, SEC-001-SEC-003, NFR-005] Implement owner-qualified rename, processing-delete rejection, transactional hard delete, sanitized logging, and DTO/controller mappings in `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/service/AnalystConversationService.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/controller/AnalystConversationController.java`.
      Verify: `.\mvnw.cmd test "-Dtest=AnalystConversationAuthorizationTests,AnalystConversationServiceTests,AnalystConversationControllerTests,AnalystConversationMigrationTests"`
      Depends: T024, T022

- [ ] T027 [US3] [FR-007, FR-008, NFR-004] Add accessible rename and confirmed-delete controls, optimistic UI only after success, and predictable focus restoration in `finvera-fe/src/features/analyst/components/ConversationSidebar.tsx` and `finvera-fe/src/features/analyst/components/AskAnalyst.tsx`.
      Verify: `npm test -- --run src/features/analyst/conversation-history.test.tsx`
      Depends: T025, T026

**Checkpoint**: Every selected story works; the owner controls title and
retention without leaking or orphaning private content.

---

## Final Phase: Cross-Cutting Validation and Release Readiness

- [ ] T090 [FR-001-FR-011, DATA-001-DATA-005] Reconcile public/internal contracts, migration, DTOs, stored response schema, and no-backfill behavior across `specs/029-ai-conversation-history/contracts/`, `finvera-be/src/main/resources/db/migration/V021__create_analyst_conversation_history.sql`, and Feature 007 contracts.
      Verify: OpenAPI parses, V021 Flyway validation passes, and a contract review finds no client-owned history or response-shape drift
      Depends: T017, T023, T027

- [ ] T091 [SEC-001-SEC-004, AI-001-AI-005, NFR-005] Execute cross-owner, prompt-injection, stale-fact, duplicate/concurrency, cancellation, log-canary, and stale-processing recovery checks and document operator behavior in `docs/runbooks/ai-conversation-history.md`.
      Verify: backend/AI negative suites pass and canary strings are absent from captured logs/metrics
      Depends: T090

- [ ] T092 [NFR-001-NFR-004, SC-001-SC-008] Run every automated and manual acceptance path in `specs/029-ai-conversation-history/quickstart.md`, including three P1 repetitions and the 10,000-record performance fixture, and record exact evidence there.
      Verify: quickstart contains dated commands, totals, measurements, and honest skipped/live-provider notes
      Depends: T091

- [ ] T093 [SRS-CONV-01] Update `docs/FEATURE_ROADMAP.md`, `docs/ARCHITECTURE.md`, `docs/PROJECT_CONTEXT.md`, and Feature 029 status/traceability to reflect the delivered behavior and operational limits.
      Verify: no Feature 029 requirement or contract is orphaned and roadmap status matches validation evidence
      Depends: T092

- [ ] T094 Run relevant repository quality gates for all affected services.
      Verify: from service roots, `.\mvnw.cmd test`; `uv run pytest` and `uv run python -m compileall .`; `npm run lint`, `npm run build`, and `npm test` all pass with exact totals recorded
      Depends: T093

## Dependencies and Parallel Execution

### Phase Dependencies

```text
T001
├── T002 legacy contract guard
├── T003 AI context tests
├── T004 -> T005 migration
├── T007 -> T008 pure policies
└── T009 config/metrics

Foundation T005/T006/T008/T009
  -> US1 T010-T019
  -> US2 T020-T023 (uses foundation; UI integration follows US1 API)
  -> US3 T024-T027 (uses foundation; delete integration follows service/API)
  -> T090 -> T091 -> T092 -> T093 -> T094
```

P1 is implemented and validated before P2/P3 are required for release review.
US2 repository tests and US3 title/delete tests can begin after foundation, but
their final controller/UI integration depends on the P1 service/API structure.

### Parallel Opportunities

- T002 and T003 touch different services after T001.
- T006, T007, and T009 can proceed after their stated contract/migration
  prerequisites without editing the same files.
- T011, T012, and T013 are backend, AI, and frontend tests that can proceed in
  parallel after foundation/contract prerequisites.
- T020 and T021 can proceed in parallel.
- T024 and T025 can proceed in parallel.
- T090-T094 remain sequential release gates.

## Requirement Coverage

| Requirement ID | Task IDs | Test/Evaluation Task | Status |
|---|---|---|---|
| FR-001-FR-003 | T001, T006, T010-T016, T018-T019 | T010, T011, T013 | Planned |
| FR-004-FR-005 | T001, T006, T020-T023 | T020, T021 | Planned |
| FR-006 | T001, T010-T016, T018-T023 | T010, T011, T013, T020-T021 | Planned |
| FR-007 | T001, T007-T008, T024-T027 | T007, T024-T025 | Planned |
| FR-008 | T001, T024-T027 | T024-T025 | Planned |
| FR-009-FR-010 | T001, T004-T006, T010-T016, T018-T019 | T004, T010-T011, T013 | Planned |
| FR-011 | T001-T002, T010, T014-T016 | T002, T010-T011 | Planned |
| DATA-001-DATA-005 | T001, T004-T006, T010, T014, T020, T024, T026, T090 | T004, T010, T020, T024 | Planned |
| SEC-001-SEC-002 | T001, T006, T010-T011, T014, T016, T020, T022, T024, T026, T091 | T010-T011, T020, T024, T091 | Planned |
| SEC-003-SEC-004 | T001, T003, T009-T010, T012, T014, T017, T024, T026, T091 | T003, T010, T012, T024, T091 | Planned |
| AI-001-AI-002 | T001, T003, T007-T008, T012, T017, T091 | T003, T007, T012, T091 | Planned |
| AI-003-AI-005 | T001, T003, T012, T017, T091 | T003, T012, T091 | Planned |
| NFR-001, NFR-003 | T020, T022, T092 | T020, T092 | Planned |
| NFR-002 | T010, T014-T016, T092 | T010, T092 | Planned |
| NFR-004 | T013, T019, T021, T023, T025, T027, T092 | T013, T021, T025, T092 | Planned |
| NFR-005 | T009-T010, T014, T020, T022, T024, T026, T091 | T010, T020, T024, T091 | Planned |
| SC-001-SC-008 | T092 | T092 | Planned |
| SRS-CONV-01 | T001, T004-T027, T090-T094 | T092, T094 | Planned |

## Delivery Notes

- Complete and validate P1 before optional organization breadth.
- Do not mark persistence complete from entity presence alone; migration,
  idempotency, terminal state, exact replay, and owner-isolation evidence are
  required.
- Never record raw prompt/response/tool payloads while debugging this feature.
- If implementation discovery changes context bounds, retention, deletion,
  concurrency, or evidence semantics, update `spec.md`, `research.md`, contracts,
  and this task list before code continues.
