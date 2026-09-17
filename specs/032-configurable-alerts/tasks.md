# Tasks: Configurable Alerts

**Input**: Design documents from `specs/032-configurable-alerts/`  
**Tests**: Required by the specification and constitution for deterministic finance, ownership, persistence, recovery, and P1 journeys.

## Phase 1: Setup

- [x] T001 Create the layered alert module package skeleton under `finvera-be/src/main/java/com/minhnb/finvera_be/alert/`
- [x] T002 [P] Add default bounded worker settings to `finvera-be/src/main/resources/application.yaml` and `.env.example`
- [x] T003 [P] Add alert route recognition and navigation placeholders in `finvera-fe/src/router.ts`, `finvera-fe/src/app.tsx`, and `finvera-fe/src/features/auth/owner-access-gate.tsx`

## Phase 2: Foundational

- [x] T004 Implement V023 definitions, evaluations, notifications, delivery attempts, checks, and dedupe indexes in `finvera-be/src/main/resources/db/migration/V023__create_alert_schema.sql` (FR-012, FR-018, DATA-009-DATA-010)
- [x] T005 [P] Implement typed condition/evidence/evaluation domain models and pure validation in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/domain/AlertDomain.java` (FR-002-FR-011, DATA-003-DATA-005)
- [x] T006 [P] Implement alert configuration and bounded scheduler properties in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/config/AlertProperties.java` (FR-018-FR-019, NFR-002)
- [x] T007 Implement JPA entities and repositories in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/entity/` and `finvera-be/src/main/java/com/minhnb/finvera_be/alert/repository/` (FR-001, FR-012-FR-019)
- [x] T008 [P] Extend stock/market application contracts and implementations in `finvera-be/src/main/java/com/minhnb/finvera_be/stock/service/StockAlertDataService.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/MarketAlertDataService.java` (FR-003-FR-009, DATA-001-DATA-008)
- [x] T009 [P] Add owner-scoped portfolio/research application contracts in `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/service/PortfolioAlertDataService.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/research/service/ResearchAlertDataService.java` (FR-010-FR-011, SEC-001-SEC-002)
- [x] T010 Add deterministic boundary tests for all condition families in `finvera-be/src/test/java/com/minhnb/finvera_be/alert/domain/AlertConditionEvaluatorTests.java` (FR-003-FR-011, DATA-002-DATA-007, SC-002-SC-004)

## Phase 3: User Story 1 â€” Configure and Manage Alerts (P1)

**Goal**: Owners can create, list, inspect, enable, disable, and delete every supported typed definition.

**Independent test**: Create every type, inspect summaries, toggle and delete resources, and reject invalid/foreign/quota operations atomically.

- [x] T011 [P] [US1] Define validated public request/response DTOs in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/dto/AlertDtos.java` (FR-001-FR-011, SEC-003)
- [x] T012 [P] [US1] Add alert service exception reason codes in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/service/AlertExceptions.java` and mappings in `finvera-be/src/main/java/com/minhnb/finvera_be/shared/api/ProblemDetailsAdvice.java` (SEC-002-SEC-003)
- [x] T013 [US1] Implement owner-scoped lifecycle, target resolution, summaries, quotas, pagination, and enable baselines in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/service/AlertManagementService.java` (FR-001-FR-002, FR-013-FR-014, FR-016-FR-019)
- [x] T014 [US1] Implement `/api/v1/alerts` management endpoints in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/controller/AlertController.java` (FR-001, FR-016-FR-017)
- [x] T015 [US1] Add controller, persistence, quota, and negative authorization tests in `finvera-be/src/test/java/com/minhnb/finvera_be/alert/AlertManagementIntegrationTests.java` (FR-001-FR-002, FR-019, SEC-001-SEC-003, SC-001, SC-006)
- [x] T016 [P] [US1] Implement runtime-validating alert API client in `finvera-fe/src/features/alerts/api/alerts.ts` (FR-001-FR-002, SEC-003)
- [x] T017 [US1] Implement accessible condition form and definition management UI in `finvera-fe/src/features/alerts/components/alerts-page.tsx` (FR-001-FR-011, NFR-004)
- [x] T018 [US1] Add alert management component tests in `finvera-fe/src/features/alerts/alerts-page.test.tsx` (SC-001, NFR-004)

## Phase 4: User Story 2 â€” Accurate In-App Notifications (P2)

**Goal**: Enabled definitions evaluate accepted facts and atomically deliver one notification per true episode or document event.

**Independent test**: Feed false/true/true/false/true and duplicate/concurrent facts for all families and observe exactly two evidence-complete notifications with stable withholding.

- [x] T019 [P] [US2] Implement pure versioned evaluators and evidence builders in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/service/AlertFactResolver.java` (FR-003-FR-012, DATA-001-DATA-009)
- [x] T020 [US2] Implement source-fact orchestration and typed unavailable outcomes in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/service/AlertFactResolver.java` (FR-003-FR-011, DATA-001-DATA-008)
- [x] T021 [US2] Implement transactional fact dedupe, episode transitions, immutable notification creation, and in-app delivery in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/service/AlertEvaluationService.java` (FR-012-FR-014, FR-017-FR-018, DATA-007-DATA-010)
- [x] T022 [US2] Implement bounded due-claim scheduling, lease recovery, retry, and isolation in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/service/AlertWorker.java` (FR-018, NFR-002-NFR-003, NFR-006)
- [x] T023 [P] [US2] Implement privacy-safe metrics and logs in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/service/AlertObservabilityService.java` (SEC-004, NFR-005)
- [x] T024 [US2] Add episode, event, correction, concurrency, disable/delete race, and retry integration tests in `finvera-be/src/test/java/com/minhnb/finvera_be/alert/AlertEvaluationIntegrationTests.java` and `AlertWorkerRecoveryTests.java` (SC-002-SC-005, SC-008)

## Phase 5: User Story 3 â€” Notification Inbox (P3)

**Goal**: Owners can inspect durable notifications/evaluations and operate unread state without changing historical evidence.

**Independent test**: List and inspect notification/evaluation history, mark one/all read, delete the source alert, and confirm stable evidence, pagination, count, and ownership.

- [x] T025 [US3] Implement owner-scoped evaluation history, inbox pagination, unread count, and idempotent read operations in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/service/NotificationService.java` (FR-015-FR-017, DATA-009-DATA-010)
- [x] T026 [US3] Implement notification and evaluation endpoints in `finvera-be/src/main/java/com/minhnb/finvera_be/alert/controller/NotificationController.java` and `AlertController.java` (FR-015-FR-017)
- [x] T027 [US3] Add inbox persistence, deletion-retention, pagination, and negative authorization tests in `finvera-be/src/test/java/com/minhnb/finvera_be/alert/NotificationIntegrationTests.java` (FR-014-FR-016, SEC-001-SEC-002, SC-006-SC-008)
- [x] T028 [P] [US3] Extend the frontend API client for evaluations/inbox/read operations in `finvera-fe/src/features/alerts/api/alerts.ts` (FR-015-FR-017)
- [x] T029 [US3] Implement accessible inbox, unread badge, evidence detail, and evaluation history in `finvera-fe/src/features/alerts/components/alerts-page.tsx` (FR-015-FR-017, NFR-004)
- [x] T030 [US3] Add inbox/read/error/retention component tests in `finvera-fe/src/features/alerts/alerts-page.test.tsx` (NFR-004, SC-007)

## Phase 6: Polish and Cross-Cutting Validation

- [x] T031 [P] Add alert operations runbook in `docs/runbooks/configurable-alerts.md` and synchronize `docs/ARCHITECTURE.md`, `docs/FEATURE_ROADMAP.md`, and `docs/PROJECT_CONTEXT.md` (NFR-005-NFR-006)
- [x] T032 [P] Add architecture and contract conformance tests in `finvera-be/src/test/java/com/minhnb/finvera_be/alert/AlertArchitectureTests.java` and `AlertApiContractTests.java` (DATA-001, SEC-001-SEC-004)
- [x] T033 Validate backend with `cd finvera-be; .\mvnw.cmd test` and record evidence in `specs/032-configurable-alerts/tasks.md`
- [x] T034 Validate frontend with `cd finvera-fe; npm run lint; npm run test; npm run build` and record evidence in `specs/032-configurable-alerts/tasks.md`
- [ ] T035 Execute `specs/032-configurable-alerts/quickstart.md`, verify migration/worker timing/privacy, and mark every completed task in `specs/032-configurable-alerts/tasks.md` (SC-001-SC-008)

## Dependencies

- Setup (T001-T003) precedes Foundation (T004-T010).
- Foundation blocks all stories.
- US1 (T011-T018) supplies persisted definitions and is required by US2.
- US2 (T019-T024) supplies evaluations/notifications and is required by US3.
- US3 (T025-T030) completes the inbox journey.
- Polish and final validation (T031-T035) follow all stories.

## Parallel opportunities

- T002 and T003 can run in parallel after T001; T005, T006, T008, and T009 use separate files after schema semantics are known.
- Within US1, backend DTO/error work (T011-T012) and frontend client scaffolding (T016) can proceed independently.
- Within US2, pure evaluator work (T019) and observability (T023) can proceed independently before orchestration integration.
- Within US3, backend inbox service (T025) and frontend client extension (T028) can proceed independently against the contract.

## Implementation strategy

The independently demonstrable MVP is US1 after the foundation: all supported definitions can be safely managed. Complete US2 next for actual ongoing value, then US3 for durable review and operations. Tests precede or accompany each high-risk deterministic, ownership, persistence, and recovery behavior.

## Validation evidence

- Backend targeted alert/security/architecture/context tests pass after the final lease-token and stale-data corrections.
- Full backend suite: `.\mvnw.cmd test` passed 872 tests with zero failures/errors on 2026-09-17 after the final alert corrections.
- After final condition-contract corrections, the complete alert-focused suite passed 32/32 tests, including latest-two MACD selection, explicit adjustment basis, strategy absence, document target rules, decimal normalization, the composite document cursor, and event-state preservation.
- `IngestionTimeoutSchedulerTests` passed against PostgreSQL 17 after loading all repositories, validating the updated research cursor query; an initial sandbox-only run could not access Docker and was superseded by the successful permitted run.
- Flyway applied all 23 migrations, including V023, against PostgreSQL 17 Testcontainers and Hibernate validated the mappings.
- Frontend: `npm run lint` passed; full Vitest passed 191/191; `npm run build` passed (existing bundle-size advisory only).
- T035 remains open until reference-environment worker timing and privacy acceptance are recorded by following `quickstart.md`.
