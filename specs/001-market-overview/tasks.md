# Tasks: Market Overview

**Input**: Design artifacts from `specs/001-market-overview/`  
**Required**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, contracts,
and `quickstart.md`  
**Goal**: Deliver a private, fixture-first market overview without guessing
TCBS or Vnstock behavior. External provider gates T045/T047 remain outside the
MVP fixture critical path; T048 is implemented as a local-only import boundary
but is not provider-ready activation.

## Task Format

Each executable line uses a checkbox, a stable task ID, optional parallel and
user-story tags, requirement IDs, an exact path, verification evidence, and
dependencies.

## Phase 1: Setup and Contract Baseline

**Purpose**: Establish test, migration, and fixture infrastructure without
activating an external market provider.

- [x] T001 [DATA-003, DATA-007, NFR-003] Add Spring Boot Flyway, PostgreSQL Flyway, validation, Actuator, ArchUnit, and Testcontainers PostgreSQL dependencies while removing unused Kafka dependencies in `finvera-be/pom.xml`
      Verify: `cd finvera-be; .\mvnw.cmd dependency:tree` resolves one coherent dependency graph and contains no Kafka runtime dependency
      Depends: none
- [x] T002 [P] [NFR-005, SC-007] Migrate the untouched frontend scaffold to React/Vite and add Vitest, React Testing Library, jsdom, Playwright, axe accessibility tooling, test scripts, and configuration in `finvera-fe/package.json`, `finvera-fe/vite.config.ts`, `finvera-fe/vitest.config.ts`, `finvera-fe/vitest.setup.ts`, `finvera-fe/playwright.config.ts`, `finvera-fe/index.html`, `finvera-fe/src/main.tsx`, and `finvera-fe/src/app.tsx`
      Verify: `cd finvera-fe; npm install; npm run test` starts the configured empty test suite without configuration errors
      Depends: none
- [x] T003 [P] [FR-001, FR-004, DATA-001, DATA-007] Create sanitized fixtures in `finvera-be/src/test/resources/fixtures/market/index-complete.json`, `index-delayed.json`, `index-stale.json`, `index-missing.json`, `index-correction.json`, `breadth.json`, and `regime.json`
      Verify: fixture review finds exactly four supported index codes, decimal strings, explicit provenance/times, missing reasons, and no credential/raw provider payload
      Depends: none
- [x] T004 [SEC-002, SEC-005, NFR-007] Define safe fixture-only defaults, owner placeholders, timezone, provider mode, and schema validation in `finvera-be/src/main/resources/application.yaml` and `finvera-be/src/test/resources/application-test.yaml`
      Verify: repository search finds no real secret and test profile selects fixture provider with `Asia/Ho_Chi_Minh`
      Depends: T001

**Checkpoint**: Dependencies, safe configuration, and deterministic fixtures
are reviewable; no live provider operation exists.

---

## Phase 2: Foundational Prerequisites

**Purpose**: Build the security, persistence, domain primitives, fixture port,
and failure semantics required by every story.

- [x] T005 [SEC-001, SEC-004, SEC-006, SC-008] Write owner login/session/logout, CSRF, fixation, cookie, expiry, rate-limit, non-owner, and TCBS-renewal redaction tests in `finvera-be/src/test/java/com/minhnb/finvera_be/auth/controller/OwnerAccessSecurityTests.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=OwnerAccessSecurityTests test` fails because owner security is not implemented
      Depends: T004
- [x] T006 [SEC-001, SEC-006] Implement configured owner identity, bcrypt verification, rotated in-memory session, secure cookie policy, CSRF, absolute expiry, and deny-by-default authorization in `finvera-be/src/main/java/com/minhnb/finvera_be/auth/config/OwnerProperties.java`, `config/OwnerSecurityConfiguration.java`, `service/OwnerSessionService.java`, `filter/OwnerSessionExpiryFilter.java`, `dto/*`, and `controller/OwnerAccessController.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=OwnerAccessSecurityTests test` passes login/session/logout and negative authorization cases
      Depends: T005
- [x] T007 [SEC-004, SEC-006, NFR-006] Implement bounded login throttling and a provider-renewal placeholder that returns `PROVIDER_AUTH_REQUIRED` without accepting/storing OTP while TCBS is gated in `finvera-be/src/main/java/com/minhnb/finvera_be/auth/service/LoginThrottle.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/market/controller/TcbsRenewalController.java`
      Verify: security tests prove uniform invalid-login errors, 429 behavior, and zero OTP/token content in captured logs
      Depends: T006
- [x] T008 [P] [NFR-006] Implement RFC 9457 problem mapping and correlation IDs in `finvera-be/src/main/java/com/minhnb/finvera_be/shared/api/ProblemDetailsAdvice.java` and `CorrelationIdFilter.java` with tests in `finvera-be/src/test/java/com/minhnb/finvera_be/shared/api/ProblemDetailsTests.java`
      Verify: targeted MVC tests assert stable codes and correlation IDs without sensitive details
      Depends: T004
- [x] T009 [DATA-001, DATA-002, DATA-006, DATA-009] Write migration-from-empty, constraint, precision, correction-link, and repository integration tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/repository/MarketMigrationTests.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=MarketMigrationTests test` fails because the market migration is absent
      Depends: T001
- [x] T010 [DATA-001, DATA-002, DATA-003, DATA-006, DATA-009] Create forward-only market schema, constraints, indexes, and seed reference versions in `finvera-be/src/main/resources/db/migration/V001__create_market_overview_schema.sql`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=MarketMigrationTests test` passes against Testcontainers PostgreSQL
      Depends: T009
- [x] T011 [DATA-001, DATA-006, DATA-009] Implement explicit JPA mappings and repositories without exposing entities through controllers in `finvera-be/src/main/java/com/minhnb/finvera_be/market/entity/MarketObservationEntity.java`, `MarketIndexSnapshotEntity.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/market/repository/MarketObservationRepository.java`, and `MarketIndexSnapshotRepository.java`
      Verify: repository integration tests round-trip UTC instants, exact decimals, immutable revisions, and input links
      Depends: T010
- [x] T012 [P] [DATA-002, DATA-003, DATA-004, DATA-007] Implement framework-free market value objects and enums using `BigDecimal` in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/model/MarketTypes.java`, `DecimalValue.java`, and `ObservationMetadata.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest='*MarketValueObjectTests' test` passes precision, zero/null, unit, timezone, and enum boundaries
      Depends: T003
- [x] T013 [P] [FR-004, FR-006, DATA-002, DATA-005] Write calendar, session, and freshness boundary tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/domain/time/MarketTimePolicyTests.java`
      Verify: tests fail at exact contracted-delay +30s/+5m and closed/non-trading-day cases before implementation
      Depends: T003
- [x] T014 [FR-004, FR-006, DATA-002, DATA-005] Implement versioned calendar/session and freshness policies in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/time/MarketTimePolicy.java` and `MarketFreshnessPolicy.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=MarketTimePolicyTests test` passes in a non-Vietnam host timezone
      Depends: T013
- [x] T015 [P] [FR-005, DATA-001, DATA-007, NFR-004, NFR-007, SEC-003] Write provider-contract allowlist, fixture mapping, degraded-state, auth-required, and forbidden-operation tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/provider/FixtureMarketDataProviderTests.java`
      Verify: tests fail before the provider-neutral contract and fixture integration exist
      Depends: T003
- [x] T016 [FR-005, DATA-001, DATA-007, NFR-004, NFR-007, SEC-003] Implement the read-only provider contract and fixture integration in `finvera-be/src/main/java/com/minhnb/finvera_be/market/provider/MarketDataProvider.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/market/provider/fixture/FixtureMarketDataProvider.java`
      Verify: fixture provider tests pass with no TCBS, AI, Redis, Qdrant, or Kafka dependency
      Depends: T012, T014, T015
- [x] T017 [P] [FR-015, DATA-006, DATA-007] Write duplicate, out-of-order, correction, invalid-number, and idempotency tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/service/MarketIngestionServiceTests.java`
      Verify: tests fail before ingestion ordering/correction behavior exists
      Depends: T003, T011
- [x] T018 [FR-015, DATA-006, DATA-007] Implement transactional normalization, validation, immutable acceptance, and correction recomputation orchestration in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/MarketIngestionService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=MarketIngestionServiceTests test` passes and older observations never regress accepted state
      Depends: T016, T017
- [x] T019 [P] [NFR-004] Add module/package boundary tests preventing market domain dependencies on web, JPA, provider, Kafka, Redis, Qdrant, or AI types in `finvera-be/src/test/java/com/minhnb/finvera_be/architecture/MarketModuleArchitectureTests.java`
      Verify: ArchUnit test passes and fails when a forbidden dependency fixture is introduced
      Depends: T001, T012
- [x] T020 [NFR-006, SC-008] Implement privacy-safe market metrics, health/failure reason taxonomy, and structured logging fields in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/MarketObservabilityService.java`
      Verify: observability tests distinguish auth/connectivity/stale/invalid/calculation/API failures and find no secret/raw payload
      Depends: T008, T016, T018

**Checkpoint**: The fixture provider can feed immutable accepted observations
through secure, observable, deterministic foundations.

---

## Phase 3: User Story 1 - Understand the Main Indices (Priority: P1)

**Goal**: Show a coherent, owner-only, degraded-safe view of all four indices.  
**Requirements**: FR-001-FR-006, FR-015, DATA-001-DATA-007, NFR-001-NFR-005,
SEC-001, SEC-002, SEC-005, SEC-006  
**Independent Test**: Load complete and delayed four-index fixtures, then prove
all required facts/statuses render; remove UPCOM and prove three usable cards
plus one explicit unavailable card.

### Tests and Evaluation

- [x] T021 [P] [US1] [FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-015, DATA-001, DATA-002, DATA-003, DATA-004, DATA-005, DATA-006, DATA-007] Write index calculation, stable ordering, coherent revision, missing-basis, unavailable, closed, delayed/stale, and correction tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/domain/index/IndexOverviewTests.java`
      Verify: tests fail before index domain behavior exists
      Depends: T012, T014
- [x] T022 [P] [US1] [FR-001, FR-002, FR-004, FR-005, SEC-001, SEC-002, SEC-006] Write owner-only `GET /api/v1/market/overview` contract/security tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/controller/MarketOverviewControllerTests.java`
      Verify: tests fail before the use case/controller exists and assert nullable decimal strings plus degraded HTTP 200
      Depends: T006, T008, T003

### Implementation

- [x] T023 [US1] [FR-001, FR-002, FR-003, FR-004, FR-006, FR-015, DATA-003, DATA-004, DATA-007] Implement exact index change/direction and four-index overview domain models in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/index/IndexOverview.java` and `IndexOverviewCalculator.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=IndexOverviewTests test` passes all numerical and degraded boundaries
      Depends: T021
- [x] T024 [US1] [FR-001, FR-002, FR-005, FR-006, FR-015, DATA-001, DATA-002, DATA-005, DATA-006, NFR-003] Implement coherent overview query/assembler and latest-revision persistence queries in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/MarketOverviewService.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/market/repository/MarketOverviewRepository.java`
      Verify: application integration tests return one coherent trading date/revision and explicit unavailable sections
      Depends: T011, T018, T023
- [x] T025 [US1] [FR-001, FR-002, FR-004, FR-005, DATA-001, DATA-002, DATA-003, SEC-001, SEC-002] Implement explicit API DTO mapping, ETag, and controller in `finvera-be/src/main/java/com/minhnb/finvera_be/market/dto/MarketOverviewResponse.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/market/controller/MarketOverviewController.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=MarketOverviewApiTests test` passes 200/304/401/403 and exact contract cases
      Depends: T022, T024
- [x] T026 [P] [US1] [FR-001, FR-002, FR-004, DATA-001, DATA-002, DATA-003, SEC-002] Implement browser API types, runtime validation, and same-origin Spring client in `finvera-fe/src/features/market-overview/api/market-overview.ts`
      Verify: frontend unit tests reject malformed responses and repository search finds no provider/browser-direct call
      Depends: T025, T002
- [x] T027 [P] [US1] [FR-002, FR-004, DATA-003, DATA-004, DATA-007, NFR-005, SC-007] Write locale formatter, index-card, freshness, missing-value, and non-color accessibility tests in `finvera-fe/src/features/market-overview/index-overview.test.tsx`
      Verify: `cd finvera-fe; npm run test -- src/features/market-overview/index-overview.test.tsx` fails before components exist
      Depends: T002, T003
- [x] T028 [US1] [FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, DATA-003, DATA-007, NFR-005] Implement exact locale formatting and accessible four-index cards in `finvera-fe/src/features/market-overview/format/market-format.ts` and `finvera-fe/src/features/market-overview/components/index-overview.tsx`
      Verify: targeted component tests pass without recomputing authoritative financial values
      Depends: T026, T027
- [x] T029 [US1] [FR-001, FR-004, FR-005, NFR-001, NFR-004, SEC-001] Implement the authenticated client-side route with loading/error/content states in `finvera-fe/src/features/market-overview/market-overview-page.tsx` and register it in `finvera-fe/src/app.tsx`
      Verify: production build succeeds and the page uses only the Spring API client
      Depends: T028
- [x] T030 [US1] [FR-001, FR-002, FR-004, FR-005, FR-006, NFR-001, NFR-004, NFR-005, SC-001, SC-002, SC-004, SC-005, SC-006, SC-007, SC-008] Add Playwright P1 complete/delayed/closed/missing-index, owner-denied, AI-offline, and accessibility journeys in `finvera-fe/tests/e2e/market-overview.spec.ts`
      Verify: `cd finvera-fe; npm run test:e2e -- --grep "P1"` passes against fixture mode
      Depends: T025, T029

**Checkpoint**: P1 is independently usable in private fixture mode without
TCBS, Vnstock, or AI availability.

---

## Phase 4: User Story 2 - Assess Market Breadth (Priority: P2)

**Goal**: Show reconciled consolidated breadth with explicit partial coverage.  
**Requirements**: FR-007-FR-009, DATA-004, DATA-007, DATA-008, NFR-003,
NFR-005  
**Independent Test**: Use a known three-venue universe including a VN30 member,
excluded instrument types, missing reference, and ex-right cases; reconcile
each security exactly once.

### Tests and Evaluation

- [x] T031 [P] [US2] [FR-007, FR-008, FR-009, DATA-004, DATA-007, DATA-008] Write breadth universe, ISIN/fallback deduplication, classification, ex-right, missing-reference, and reconciliation property tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/domain/breadth/BreadthCalculatorTests.java`
      Verify: tests fail before breadth policies exist and assert `advancing + declining + unchanged + unclassified = eligible`
      Depends: T012, T003
- [x] T032 [US2] [FR-007, FR-008, FR-009, DATA-004, DATA-007, DATA-008] Implement versioned breadth universe and calculation policies in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/breadth/BreadthUniversePolicy.java` and `BreadthCalculator.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=BreadthCalculatorTests test` passes with unrounded decimals and one identity once
      Depends: T031
- [x] T033 [US2] [FR-007, FR-009, DATA-001, DATA-008, NFR-003] Persist immutable breadth/input links and add breadth to the coherent assembler/API mapping in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/BreadthService.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/market/repository/MarketBreadthRepository.java`, and `finvera-be/src/main/java/com/minhnb/finvera_be/market/dto/MarketOverviewResponse.java`
      Verify: integration/API tests preserve universe hash/input IDs and return PARTIAL with unclassified count
      Depends: T011, T024, T032
- [x] T034 [P] [US2] [FR-007, FR-009, NFR-005, SC-007] Write breadth complete/partial/unavailable and non-color accessibility tests in `finvera-fe/src/features/market-overview/breadth-overview.test.tsx`
      Verify: targeted test fails before breadth UI exists
      Depends: T002, T003
- [x] T035 [US2] [FR-007, FR-009, DATA-008, NFR-005] Implement accessible breadth presentation in `finvera-fe/src/features/market-overview/components/breadth-overview.tsx`
      Verify: component tests pass and display eligible/unclassified/universe/as-of/source without unsupported advice
      Depends: T026, T033, T034
- [x] T036 [US2] [FR-007, FR-008, FR-009, SC-002, SC-004, SC-006, SC-007] Add consolidated breadth and partial/unavailable Playwright journeys in `finvera-fe/tests/e2e/market-overview.spec.ts`
      Verify: `cd finvera-fe; npm run test:e2e -- --grep "P2"` passes and P1 remains green
      Depends: T035

**Checkpoint**: P2 reconciles independently and does not require a regime.

---

## Phase 5: User Story 3 - Understand the Market Regime (Priority: P3)

**Goal**: Produce and explain reproducible `market-regime-v1` assessments.  
**Requirements**: FR-010-FR-015, DATA-009, DATA-010, NFR-003, NFR-005  
**Independent Test**: Replay versioned bullish/bearish/boundary/missing/conflict
fixtures twice and prove exact output or reason-coded withholding.

### Tests and Evaluation

- [x] T037 [P] [US3] [FR-010, FR-011, FR-012, FR-013, DATA-009] Create versioned regime replay/boundary fixtures and write BigDecimal SMA, Wilder RSI, return, median, population-standard-deviation, percentile, component, label, confidence, and renormalization tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/domain/regime/MarketRegimeV1Tests.java`
      Verify: tests fail before the engine exists and cover 29/30/44/45/55/56/70/71, zero A/D denominator, exactly 80% completeness, and one missing component
      Depends: T003, T012
- [x] T038 [US3] [FR-010, FR-011, FR-012, FR-013, DATA-003, DATA-009] Implement exact reusable decimal time-series calculations in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/regime/math/DecimalTimeSeries.java`
      Verify: targeted numerical tests pass with declared scale/rounding and no `double`/`float`
      Depends: T037
- [x] T039 [US3] [FR-010, FR-011, FR-012, FR-013, FR-014, DATA-009] Implement `market-regime-v1`, publishability, confidence, supporting factors, and disclaimer codes in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/regime/MarketRegimeV1.java` and `RegimeAssessment.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=MarketRegimeV1Tests test` passes deterministic replay and withholding cases
      Depends: T032, T038
- [x] T040 [P] [US3] [DATA-009, DATA-010, FR-012, FR-015] Write immutable assessment/input-link, corrected-history replay, and cross-source `SOURCE_CONFLICT` tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/service/RegimeAssessmentServiceTests.java`
      Verify: tests fail before assessment persistence/reconciliation exists and never average conflicting sources
      Depends: T011, T037
- [x] T041 [US3] [FR-010, FR-011, FR-012, FR-013, FR-015, DATA-009, DATA-010, NFR-003] Implement assessment orchestration, persistence, source reconciliation, correction replay, and overview/API mapping in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/RegimeAssessmentService.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/market/repository/RegimeAssessmentRepository.java`, and `finvera-be/src/main/java/com/minhnb/finvera_be/market/dto/MarketOverviewResponse.java`
      Verify: targeted application/API tests pass with exact input IDs/rule version and withhold conflicts
      Depends: T033, T039, T040
- [x] T042 [P] [US3] [FR-010, FR-011, FR-012, FR-014, NFR-005, SC-007] Write regime complete/renormalized/withheld, factor, disclaimer, and non-color accessibility tests in `finvera-fe/src/features/market-overview/regime-overview.test.tsx`
      Verify: targeted test fails before regime UI exists
      Depends: T002, T003
- [x] T043 [US3] [FR-010, FR-011, FR-012, FR-014, DATA-009, NFR-005] Implement accessible regime/factor presentation in `finvera-fe/src/features/market-overview/components/regime-overview.tsx`
      Verify: component tests label confidence as assessment quality and contain no prediction or buy/sell instruction
      Depends: T026, T041, T042
- [x] T044 [US3] [FR-010, FR-011, FR-012, FR-013, FR-014, FR-015, SC-003, SC-004, SC-007] Add deterministic replay, insufficient/conflicting data, correction, and disclaimer Playwright journeys in `finvera-fe/tests/e2e/market-overview.spec.ts`
      Verify: `cd finvera-fe; npm run test:e2e -- --grep "P3"` passes twice with identical fixture output
      Depends: T043

**Checkpoint**: Every selected story works in deterministic fixture mode.

---

## Phase 6: Gated Provider Activation (Deferred)

**Purpose**: Keep real integrations explicit and blocked until their contracts
and usage rights pass. These tasks MUST NOT be started merely because fixture
mode is complete.
- [x] T004 [SEC-002, SEC-005, NFR-007] Define safe fixture-only defaults, owner placeholders, timezone, provider mode, and schema validation in `finvera-be/src/main/resources/application.yaml` and `finvera-be/src/test/resources/application-test.yaml`
      Verify: repository search finds no real secret and test profile selects fixture provider with `Asia/Ho_Chi_Minh`
      Depends: T001

**Checkpoint**: Dependencies, safe configuration, and deterministic fixtures
are reviewable; no live provider operation exists.

---

## Phase 2: Foundational Prerequisites

**Purpose**: Build the security, persistence, domain primitives, fixture port,
and failure semantics required by every story.

- [x] T005 [SEC-001, SEC-004, SEC-006, SC-008] Write owner login/session/logout, CSRF, fixation, cookie, expiry, rate-limit, non-owner, and TCBS-renewal redaction tests in `finvera-be/src/test/java/com/minhnb/finvera_be/auth/controller/OwnerAccessSecurityTests.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=OwnerAccessSecurityTests test` fails because owner security is not implemented
      Depends: T004
- [x] T006 [SEC-001, SEC-006] Implement configured owner identity, bcrypt verification, rotated in-memory session, secure cookie policy, CSRF, absolute expiry, and deny-by-default authorization in `finvera-be/src/main/java/com/minhnb/finvera_be/auth/config/OwnerProperties.java`, `config/OwnerSecurityConfiguration.java`, `service/OwnerSessionService.java`, `filter/OwnerSessionExpiryFilter.java`, `dto/*`, and `controller/OwnerAccessController.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=OwnerAccessSecurityTests test` passes login/session/logout and negative authorization cases
      Depends: T005
- [x] T007 [SEC-004, SEC-006, NFR-006] Implement bounded login throttling and a provider-renewal placeholder that returns `PROVIDER_AUTH_REQUIRED` without accepting/storing OTP while TCBS is gated in `finvera-be/src/main/java/com/minhnb/finvera_be/auth/service/LoginThrottle.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/market/controller/TcbsRenewalController.java`
      Verify: security tests prove uniform invalid-login errors, 429 behavior, and zero OTP/token content in captured logs
      Depends: T006
- [x] T008 [P] [NFR-006] Implement RFC 9457 problem mapping and correlation IDs in `finvera-be/src/main/java/com/minhnb/finvera_be/shared/api/ProblemDetailsAdvice.java` and `CorrelationIdFilter.java` with tests in `finvera-be/src/test/java/com/minhnb/finvera_be/shared/api/ProblemDetailsTests.java`
      Verify: targeted MVC tests assert stable codes and correlation IDs without sensitive details
      Depends: T004
- [x] T009 [DATA-001, DATA-002, DATA-006, DATA-009] Write migration-from-empty, constraint, precision, correction-link, and repository integration tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/repository/MarketMigrationTests.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=MarketMigrationTests test` fails because the market migration is absent
      Depends: T001
- [x] T010 [DATA-001, DATA-002, DATA-003, DATA-006, DATA-009] Create forward-only market schema, constraints, indexes, and seed reference versions in `finvera-be/src/main/resources/db/migration/V001__create_market_overview_schema.sql`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=MarketMigrationTests test` passes against Testcontainers PostgreSQL
      Depends: T009
- [x] T011 [DATA-001, DATA-006, DATA-009] Implement explicit JPA mappings and repositories without exposing entities through controllers in `finvera-be/src/main/java/com/minhnb/finvera_be/market/entity/MarketObservationEntity.java`, `MarketIndexSnapshotEntity.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/market/repository/MarketObservationRepository.java`, and `MarketIndexSnapshotRepository.java`
      Verify: repository integration tests round-trip UTC instants, exact decimals, immutable revisions, and input links
      Depends: T010
- [x] T012 [P] [DATA-002, DATA-003, DATA-004, DATA-007] Implement framework-free market value objects and enums using `BigDecimal` in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/model/MarketTypes.java`, `DecimalValue.java`, and `ObservationMetadata.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest='*MarketValueObjectTests' test` passes precision, zero/null, unit, timezone, and enum boundaries
      Depends: T003
- [x] T013 [P] [FR-004, FR-006, DATA-002, DATA-005] Write calendar, session, and freshness boundary tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/domain/time/MarketTimePolicyTests.java`
      Verify: tests fail at exact contracted-delay +30s/+5m and closed/non-trading-day cases before implementation
      Depends: T003
- [x] T014 [FR-004, FR-006, DATA-002, DATA-005] Implement versioned calendar/session and freshness policies in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/time/MarketTimePolicy.java` and `MarketFreshnessPolicy.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=MarketTimePolicyTests test` passes in a non-Vietnam host timezone
      Depends: T013
- [x] T015 [P] [FR-005, DATA-001, DATA-007, NFR-004, NFR-007, SEC-003] Write provider-contract allowlist, fixture mapping, degraded-state, auth-required, and forbidden-operation tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/provider/FixtureMarketDataProviderTests.java`
      Verify: tests fail before the provider-neutral contract and fixture integration exist
      Depends: T003
- [x] T016 [FR-005, DATA-001, DATA-007, NFR-004, NFR-007, SEC-003] Implement the read-only provider contract and fixture integration in `finvera-be/src/main/java/com/minhnb/finvera_be/market/provider/MarketDataProvider.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/market/provider/fixture/FixtureMarketDataProvider.java`
      Verify: fixture provider tests pass with no TCBS, AI, Redis, Qdrant, or Kafka dependency
      Depends: T012, T014, T015
- [x] T017 [P] [FR-015, DATA-006, DATA-007] Write duplicate, out-of-order, correction, invalid-number, and idempotency tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/service/MarketIngestionServiceTests.java`
      Verify: tests fail before ingestion ordering/correction behavior exists
      Depends: T003, T011
- [x] T018 [FR-015, DATA-006, DATA-007] Implement transactional normalization, validation, immutable acceptance, and correction recomputation orchestration in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/MarketIngestionService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=MarketIngestionServiceTests test` passes and older observations never regress accepted state
      Depends: T016, T017
- [x] T019 [P] [NFR-004] Add module/package boundary tests preventing market domain dependencies on web, JPA, provider, Kafka, Redis, Qdrant, or AI types in `finvera-be/src/test/java/com/minhnb/finvera_be/architecture/MarketModuleArchitectureTests.java`
      Verify: ArchUnit test passes and fails when a forbidden dependency fixture is introduced
      Depends: T001, T012
- [x] T020 [NFR-006, SC-008] Implement privacy-safe market metrics, health/failure reason taxonomy, and structured logging fields in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/MarketObservabilityService.java`
      Verify: observability tests distinguish auth/connectivity/stale/invalid/calculation/API failures and find no secret/raw payload
      Depends: T008, T016, T018

**Checkpoint**: The fixture provider can feed immutable accepted observations
through secure, observable, deterministic foundations.

---

## Phase 3: User Story 1 - Understand the Main Indices (Priority: P1)

**Goal**: Show a coherent, owner-only, degraded-safe view of all four indices.  
**Requirements**: FR-001-FR-006, FR-015, DATA-001-DATA-007, NFR-001-NFR-005,
SEC-001, SEC-002, SEC-005, SEC-006  
**Independent Test**: Load complete and delayed four-index fixtures, then prove
all required facts/statuses render; remove UPCOM and prove three usable cards
plus one explicit unavailable card.

### Tests and Evaluation

- [x] T021 [P] [US1] [FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-015, DATA-001, DATA-002, DATA-003, DATA-004, DATA-005, DATA-006, DATA-007] Write index calculation, stable ordering, coherent revision, missing-basis, unavailable, closed, delayed/stale, and correction tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/domain/index/IndexOverviewTests.java`
      Verify: tests fail before index domain behavior exists
      Depends: T012, T014
- [x] T022 [P] [US1] [FR-001, FR-002, FR-004, FR-005, SEC-001, SEC-002, SEC-006] Write owner-only `GET /api/v1/market/overview` contract/security tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/controller/MarketOverviewControllerTests.java`
      Verify: tests fail before the use case/controller exists and assert nullable decimal strings plus degraded HTTP 200
      Depends: T006, T008, T003

### Implementation

- [x] T023 [US1] [FR-001, FR-002, FR-003, FR-004, FR-006, FR-015, DATA-003, DATA-004, DATA-007] Implement exact index change/direction and four-index overview domain models in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/index/IndexOverview.java` and `IndexOverviewCalculator.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=IndexOverviewTests test` passes all numerical and degraded boundaries
      Depends: T021
- [x] T024 [US1] [FR-001, FR-002, FR-005, FR-006, FR-015, DATA-001, DATA-002, DATA-005, DATA-006, NFR-003] Implement coherent overview query/assembler and latest-revision persistence queries in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/MarketOverviewService.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/market/repository/MarketOverviewRepository.java`
      Verify: application integration tests return one coherent trading date/revision and explicit unavailable sections
      Depends: T011, T018, T023
- [x] T025 [US1] [FR-001, FR-002, FR-004, FR-005, DATA-001, DATA-002, DATA-003, SEC-001, SEC-002] Implement explicit API DTO mapping, ETag, and controller in `finvera-be/src/main/java/com/minhnb/finvera_be/market/dto/MarketOverviewResponse.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/market/controller/MarketOverviewController.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=MarketOverviewApiTests test` passes 200/304/401/403 and exact contract cases
      Depends: T022, T024
- [x] T026 [P] [US1] [FR-001, FR-002, FR-004, DATA-001, DATA-002, DATA-003, SEC-002] Implement browser API types, runtime validation, and same-origin Spring client in `finvera-fe/src/features/market-overview/api/market-overview.ts`
      Verify: frontend unit tests reject malformed responses and repository search finds no provider/browser-direct call
      Depends: T025, T002
- [x] T027 [P] [US1] [FR-002, FR-004, DATA-003, DATA-004, DATA-007, NFR-005, SC-007] Write locale formatter, index-card, freshness, missing-value, and non-color accessibility tests in `finvera-fe/src/features/market-overview/index-overview.test.tsx`
      Verify: `cd finvera-fe; npm run test -- src/features/market-overview/index-overview.test.tsx` fails before components exist
      Depends: T002, T003
- [x] T028 [US1] [FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, DATA-003, DATA-007, NFR-005] Implement exact locale formatting and accessible four-index cards in `finvera-fe/src/features/market-overview/format/market-format.ts` and `finvera-fe/src/features/market-overview/components/index-overview.tsx`
      Verify: targeted component tests pass without recomputing authoritative financial values
      Depends: T026, T027
- [x] T029 [US1] [FR-001, FR-004, FR-005, NFR-001, NFR-004, SEC-001] Implement the authenticated client-side route with loading/error/content states in `finvera-fe/src/features/market-overview/market-overview-page.tsx` and register it in `finvera-fe/src/app.tsx`
      Verify: production build succeeds and the page uses only the Spring API client
      Depends: T028
- [x] T030 [US1] [FR-001, FR-002, FR-004, FR-005, FR-006, NFR-001, NFR-004, NFR-005, SC-001, SC-002, SC-004, SC-005, SC-006, SC-007, SC-008] Add Playwright P1 complete/delayed/closed/missing-index, owner-denied, AI-offline, and accessibility journeys in `finvera-fe/tests/e2e/market-overview.spec.ts`
      Verify: `cd finvera-fe; npm run test:e2e -- --grep "P1"` passes against fixture mode
      Depends: T025, T029

**Checkpoint**: P1 is independently usable in private fixture mode without
TCBS, Vnstock, or AI availability.

---

## Phase 4: User Story 2 - Assess Market Breadth (Priority: P2)

**Goal**: Show reconciled consolidated breadth with explicit partial coverage.  
**Requirements**: FR-007-FR-009, DATA-004, DATA-007, DATA-008, NFR-003,
NFR-005  
**Independent Test**: Use a known three-venue universe including a VN30 member,
excluded instrument types, missing reference, and ex-right cases; reconcile
each security exactly once.

### Tests and Evaluation

- [x] T031 [P] [US2] [FR-007, FR-008, FR-009, DATA-004, DATA-007, DATA-008] Write breadth universe, ISIN/fallback deduplication, classification, ex-right, missing-reference, and reconciliation property tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/domain/breadth/BreadthCalculatorTests.java`
      Verify: tests fail before breadth policies exist and assert `advancing + declining + unchanged + unclassified = eligible`
      Depends: T012, T003
- [x] T032 [US2] [FR-007, FR-008, FR-009, DATA-004, DATA-007, DATA-008] Implement versioned breadth universe and calculation policies in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/breadth/BreadthUniversePolicy.java` and `BreadthCalculator.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=BreadthCalculatorTests test` passes with unrounded decimals and one identity once
      Depends: T031
- [x] T033 [US2] [FR-007, FR-009, DATA-001, DATA-008, NFR-003] Persist immutable breadth/input links and add breadth to the coherent assembler/API mapping in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/BreadthService.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/market/repository/MarketBreadthRepository.java`, and `finvera-be/src/main/java/com/minhnb/finvera_be/market/dto/MarketOverviewResponse.java`
      Verify: integration/API tests preserve universe hash/input IDs and return PARTIAL with unclassified count
      Depends: T011, T024, T032
- [x] T034 [P] [US2] [FR-007, FR-009, NFR-005, SC-007] Write breadth complete/partial/unavailable and non-color accessibility tests in `finvera-fe/src/features/market-overview/breadth-overview.test.tsx`
      Verify: targeted test fails before breadth UI exists
      Depends: T002, T003
- [x] T035 [US2] [FR-007, FR-009, DATA-008, NFR-005] Implement accessible breadth presentation in `finvera-fe/src/features/market-overview/components/breadth-overview.tsx`
      Verify: component tests pass and display eligible/unclassified/universe/as-of/source without unsupported advice
      Depends: T026, T033, T034
- [x] T036 [US2] [FR-007, FR-008, FR-009, SC-002, SC-004, SC-006, SC-007] Add consolidated breadth and partial/unavailable Playwright journeys in `finvera-fe/tests/e2e/market-overview.spec.ts`
      Verify: `cd finvera-fe; npm run test:e2e -- --grep "P2"` passes and P1 remains green
      Depends: T035

**Checkpoint**: P2 reconciles independently and does not require a regime.

---

## Phase 5: User Story 3 - Understand the Market Regime (Priority: P3)

**Goal**: Produce and explain reproducible `market-regime-v1` assessments.  
**Requirements**: FR-010-FR-015, DATA-009, DATA-010, NFR-003, NFR-005  
**Independent Test**: Replay versioned bullish/bearish/boundary/missing/conflict
fixtures twice and prove exact output or reason-coded withholding.

### Tests and Evaluation

- [x] T037 [P] [US3] [FR-010, FR-011, FR-012, FR-013, DATA-009] Create versioned regime replay/boundary fixtures and write BigDecimal SMA, Wilder RSI, return, median, population-standard-deviation, percentile, component, label, confidence, and renormalization tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/domain/regime/MarketRegimeV1Tests.java`
      Verify: tests fail before the engine exists and cover 29/30/44/45/55/56/70/71, zero A/D denominator, exactly 80% completeness, and one missing component
      Depends: T003, T012
- [x] T038 [US3] [FR-010, FR-011, FR-012, FR-013, DATA-003, DATA-009] Implement exact reusable decimal time-series calculations in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/regime/math/DecimalTimeSeries.java`
      Verify: targeted numerical tests pass with declared scale/rounding and no `double`/`float`
      Depends: T037
- [x] T039 [US3] [FR-010, FR-011, FR-012, FR-013, FR-014, DATA-009] Implement `market-regime-v1`, publishability, confidence, supporting factors, and disclaimer codes in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/regime/MarketRegimeV1.java` and `RegimeAssessment.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=MarketRegimeV1Tests test` passes deterministic replay and withholding cases
      Depends: T032, T038
- [x] T040 [P] [US3] [DATA-009, DATA-010, FR-012, FR-015] Write immutable assessment/input-link, corrected-history replay, and cross-source `SOURCE_CONFLICT` tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/service/RegimeAssessmentServiceTests.java`
      Verify: tests fail before assessment persistence/reconciliation exists and never average conflicting sources
      Depends: T011, T037
- [x] T041 [US3] [FR-010, FR-011, FR-012, FR-013, FR-015, DATA-009, DATA-010, NFR-003] Implement assessment orchestration, persistence, source reconciliation, correction replay, and overview/API mapping in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/RegimeAssessmentService.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/market/repository/RegimeAssessmentRepository.java`, and `finvera-be/src/main/java/com/minhnb/finvera_be/market/dto/MarketOverviewResponse.java`
      Verify: targeted application/API tests pass with exact input IDs/rule version and withhold conflicts
      Depends: T033, T039, T040
- [x] T042 [P] [US3] [FR-010, FR-011, FR-012, FR-014, NFR-005, SC-007] Write regime complete/renormalized/withheld, factor, disclaimer, and non-color accessibility tests in `finvera-fe/src/features/market-overview/regime-overview.test.tsx`
      Verify: targeted test fails before regime UI exists
      Depends: T002, T003
- [x] T043 [US3] [FR-010, FR-011, FR-012, FR-014, DATA-009, NFR-005] Implement accessible regime/factor presentation in `finvera-fe/src/features/market-overview/components/regime-overview.tsx`
      Verify: component tests label confidence as assessment quality and contain no prediction or buy/sell instruction
      Depends: T026, T041, T042
- [x] T044 [US3] [FR-010, FR-011, FR-012, FR-013, FR-014, FR-015, SC-003, SC-004, SC-007] Add deterministic replay, insufficient/conflicting data, correction, and disclaimer Playwright journeys in `finvera-fe/tests/e2e/market-overview.spec.ts`
      Verify: `cd finvera-fe; npm run test:e2e -- --grep "P3"` passes twice with identical fixture output
      Depends: T043

**Checkpoint**: Every selected story works in deterministic fixture mode.

---

## Phase 6: Gated Provider Activation (Deferred)

**Purpose**: Keep real integrations explicit and blocked until their contracts
and usage rights pass. These tasks MUST NOT be started merely because fixture
mode is complete.

- [x] T045 [NFR-002, NFR-007, SEC-003, SEC-004] Close the TCBS capability/license gate with sanitized schemas, entitlement, timing/delay, rate-limit, correction, index/reference/universe, and authentication evidence in `specs/001-market-overview/contracts/tcbs-iflash-adapter.md`
      Verify: contract status is approved, checklist is checked, no raw payload/credential is committed, and owner explicitly accepts the evidence
      Depends: external TCBS resolution; none of T001-T044
      Evidence (2026-08-18): Contract status updated to APPROVED with three documented constraints. Endpoint `GET /tartarus/v1/tickerCommons?index={1,2,3,5}` at `https://openapi.tcbs.com.vn` confirmed as REST reconciliation source supplying `tradingDate` (date string); adapter labels observations `TCBS_REST_TRADING_DATE_ONLY` and infers `effective_at` from `MarketTimePolicy`. Session field is opaque; state inferred from clock + trading schedule (`MarketTimePolicy`). Breadth schema shape confirmed (428/30/299/824 records); full-universe `tradingStatus` mapping is PARTIAL — T046 must implement `BREADTH_RECORD_INCOMPLETE` graceful degradation. WebSocket `rt` stream confirmed display-only (no timestamp/ordering/correction fields); labels `TCBS_STREAM_TIMESTAMP_UNAVAILABLE` and `TCBS_STREAM_ORDERING_UNAVAILABLE` apply. Rate probe (5 requests/1 s) all HTTP 200. Summary SHA-256: `3a75659c820a713d98802bad5ec125c565fec94fd96c5910a8a8b29919f2ed8c`. No raw payload, credential, OTP, token, or market value committed. Owner acceptance recorded 2026-08-18.
- [x] T046 [NFR-002, NFR-006, NFR-007, SEC-002, SEC-003, SEC-004] After T045 only, write contract/fault/allowlist tests and implement the live integration in `finvera-be/src/test/java/com/minhnb/finvera_be/market/provider/tcbs/TcbsMarketDataProviderTests.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/market/provider/tcbs/TcbsMarketDataProvider.java`
      Verify: sanitized contract tests pass for exact captured schemas, bounded timeouts/retry/reconnect, auth expiry, and forbidden non-market operations
      Depends: T016, T020, T045
      Evidence (2026-08-18): Implemented TcbsRestClient port interface, TcbsSessionState token lifecycle port, and TcbsMarketDataProvider with all three gate decisions (REST-only persistence with TCBS_REST_TRADING_DATE_ONLY label, subscribe() display-only no-op, graceful BREADTH_RECORD_INCOMPLETE degradation, REFERENCE_UNAVAILABLE for zero refPrice). 13 contract tests passed cleanly. TcbsIndexStreamMapperTests (2/2) and TcbsOuranosC001MapperTests (2/2) also passed.
- [ ] T047 [DATA-010] Close the Vnstock upstream-use, request-limit, adjustment/correction, and bounded full-universe gates using `tools/market-data/provider-poc/poc_vnstock.py` and `specs/001-market-overview/contracts/vnstock-historical-bootstrap.md`
      Verify: a bounded full-universe POC records only sanitized aggregate evidence with a configured rate limit/checkpoint; written evidence permits intended private storage/analysis and the contract checklist is checked without inferring rights from the Python package license
      Depends: external source-rights confirmation; none of T001-T044
      Interim evidence: the owner confirmed KBS accepts the contract's private storage/analysis use on 2026-08-18. Bounded POC implementation and 30 requests/minute checkpoint migration/resume validation processed 56 of 1,526 eligible histories (42 usable; 14 explicit `INSUFFICIENT_HISTORY`) with no provider failure; full coverage and adjustment/correction semantics remain open.
- [X] T048 [DATA-001, DATA-003, DATA-007, DATA-009, DATA-010] Implement the canonical decimal-string local-only exporter and Spring importer boundary in `tools/market-data/vnstock-export/export_history.py` and `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/MarketImportService.java` with tests in `tools/market-data/vnstock-export/tests/` and `finvera-be/src/test/java/com/minhnb/finvera_be/market/service/MarketImportServiceTests.java`. The owner-approved local exception permits implementation but not a claim that T047 passed.
      Verify: package checksum/schema/provenance/271-session/idempotency/conflict tests pass; neither tool nor package writes PostgreSQL directly; exporter defaults to manual local operation and cannot expose/publicly distribute data.
      Depends: T011, T039, T059
      Validation (2026-08-18): exporter tests pass 2/2; Spring unit tests cover checksum, schema, idempotency, and rejection paths. PostgreSQL/Testcontainers integration remains environment-dependent and was not claimed successful when Docker was unavailable.

- [X] T059 [DATA-001, DATA-003, DATA-007, DATA-009] Implement the provider-neutral canonical historical-package validator and atomic Spring import core in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/MarketImportService.java` with tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/service/MarketImportServiceTests.java` and `MarketImportPersistenceTests.java`; it accepts only reviewed package records and does not invoke Vnstock.
      Verify: unit tests cover checksum, decimal-string, date-order, provenance, idempotency, and no-write-on-validation-failure; PostgreSQL integration verifies persisted immutable facts and idempotent package replay.
      Depends: T011, T039

**Checkpoint**: Provider work is activated only by approved evidence; fixture
completion is never misrepresented as live-data readiness.

---

## Final Phase: Cross-Cutting Validation and Release Readiness

- [x] T049 [FR-001-FR-015, DATA-001-DATA-010, SEC-001-SEC-006] Run and reconcile backend/API/frontend contract tests across fixture-mode boundaries and update `specs/001-market-overview/contracts/market-overview.openapi.yaml` only if approved behavior changed
      Verify: contract tests pass and no implemented response differs from the reviewed contract
      Depends: T030, T036, T044
- [x] T050 [NFR-001, NFR-002, NFR-003, SC-005] Add coherent-overview API and accepted-update latency smoke tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/performance/MarketOverviewPerformanceTests.java`
      Verify: representative fixture read p95 is <=500 ms and accepted fixture updates satisfy configured delay +30s in the test environment
      Depends: T024, T033, T041
- [ ] T051 [SEC-001, SEC-002, SEC-005, SEC-006, SC-008] Document and validate Tailscale Serve-only ingress, Funnel/direct-port denial, owner secret generation, cookie/CSRF checks, and rollback in `docs/runbooks/private-market-overview.md`
      Verify: operator checklist records denied public/direct access and no secret appears in repository, logs, responses, or client bundle
      Depends: T006, T030
      **Deferred by owner during local development (2026-08-17):** do not install or configure Tailscale yet. Keep every local process bound to `127.0.0.1`; do not create router port forwards, public DNS, public tunnels, Tailscale Funnel, or any public/LAN ingress. T051 remains a mandatory pre-deployment/pre-multi-device release gate and must be validated before this private system is deployed or accessed remotely.
- [x] T052 [NFR-004, NFR-006, NFR-007, SC-006, SC-008] Add fault-injection and safe telemetry tests in `finvera-be/src/test/java/com/minhnb/finvera_be/market/operations/MarketOverviewFailureTests.java`
      Verify: tests distinguish provider/auth/stale/invalid/calculation/delivery failures, survive AI outage, and expose no sensitive payload
      Depends: T020, T041
- [x] T053 [FR-001-FR-015, SC-001-SC-008] Execute and update fixture-mode commands/scenarios in `specs/001-market-overview/quickstart.md`, explicitly retaining TCBS/Vnstock production blockers
      Verify: P1-P3 happy paths and critical negative paths produce recorded expected results without claiming provider gates passed
      Depends: T049, T050, T052, T058. T051 is not required for loopback-only local acceptance, but remains a mandatory pre-deployment/pre-remote-access release gate.
      Evidence (2026-08-17): `validation/fixture-acceptance.md`; backend 83/83, frontend 19/19, lint/build, and Playwright 10/10 passed. TCBS, Vnstock, Tailscale, and manual usability gates remain explicitly open.
- [x] T054 [FR-001-FR-015, DATA-001-DATA-010, NFR-001-NFR-007, SEC-001-SEC-006] Reconcile delivered behavior and limitations in `specs/001-market-overview/spec.md`, `plan.md`, `research.md`, `quickstart.md`, `contracts/market-overview.openapi.yaml`, `docs/adr/0006-use-react-vite-for-private-web-client.md`, and `docs/runbooks/private-market-overview.md`
      Verify: traceability review has no orphan requirement, undocumented behavior, secret, placeholder, or false live-provider claim
      Depends: T053
      Evidence (2026-08-17): cross-artifact analysis mapped all 46 requirement/success IDs to tasks, found no unresolved clarification, orphan requirement, constitution violation, placeholder implementation, or public-contract drift. The artifacts now consistently label fixture behavior as validated and TCBS, Vnstock, Tailscale, and manual usability as open gates.
- [x] T055 Run repository quality gates from `finvera-be/` and `finvera-fe/`
      Verify: `.\mvnw.cmd test`, `npm run test`, `npm run lint`, `npm run build`, and fixture-mode `npm run test:e2e` pass; blocked external-provider checks are reported, not passed
      Depends: T054
      Evidence (2026-08-17): backend 83/83 tests passed; frontend 19/19 tests passed; ESLint and Vite production build passed; Playwright Chromium 10/10 passed. External-provider and remote-ingress checks remain open and were not represented as passed.
- [ ] T056 [SC-001] Conduct three consecutive timed owner usability checks for four-index direction/session/as-of identification and record anonymized evidence in `specs/001-market-overview/validation/usability.md`
      Verify: the owner completes every trial within 10 seconds, or findings are returned to spec/plan before release
      Depends: T055

## Dependencies and Parallel Execution

### Phase Dependencies

```text
Setup (T001-T004)
  -> Foundation (T005-T020)
  -> US1/P1 (T021-T030)
  -> US2/P2 (T031-T036)
  -> US3/P3 (T037-T044)
  -> Fixture release validation (T049-T056)

External TCBS gate T045 -> T046 (not on fixture MVP path)
External Vnstock gate T047 -> historical provider activation (T048 remains
local-only and may be validated independently; neither is on fixture MVP path)
T051 -> private deployment/remote-access release (not local fixture acceptance)
```

US2 requires shared observation foundations but not the US1 UI. US3 requires
the breadth domain result and historical fixtures but not the US2 UI. Frontend
work starts only after its reviewed backend contract behavior is available.

### Parallel Opportunities

- T001 and T002 can run in parallel; T003 is independent of both.
- Security T005-T008, persistence T009-T011, domain primitives/time T012-T014,
  and provider-contract tests T015 can progress in parallel where dependencies allow.
- For US1, T021/T022/T027 can be written in parallel; T026 can start after the
  backend contract is executable.
- T031 and T034 can run in parallel for US2.
- T037/T040/T042 can run in parallel after their listed foundations exist.
- T045 and T047 are independent external gates, but neither authorizes the
  corresponding implementation task until its own evidence is approved.

## Requirement Coverage

| Requirement ID | Task IDs | Test/Evaluation Task | Status |
|---|---|---|---|
| FR-001-FR-006 | T003, T021-T030, T049, T053-T054 | T021, T022, T027, T030 | Fixture validated |
| FR-007-FR-009 | T031-T036, T049, T053-T054 | T031, T034, T036 | Fixture validated |
| FR-010-FR-014 | T037-T044, T049, T053-T054 | T037, T040, T042, T044 | Fixture validated |
| FR-015 | T017-T018, T021, T023-T024, T040-T041, T044, T049, T053-T054 | T017, T021, T040, T044 | Fixture validated |
| DATA-001-DATA-007 | T001, T003, T009-T18, T021-T26, T031-T33, T037-T41, T048-T49, T053-T054 | T009, T012-T013, T015, T017, T021-T022, T031, T037, T040 | Fixture validated; live/import path gated |
| DATA-008 | T031-T035, T049, T053-T054 | T031, T034, T036 | Fixture validated |
| DATA-009 | T009-T11, T037-T41, T048-T49, T053-T054 | T009, T037, T040 | Fixture validated |
| DATA-010 | T040-T041, T047-T049, T053-T054 | T040, T048 | Planned; live/import path gated |
| NFR-001-NFR-003 | T001, T024, T030, T033, T041, T045-T046, T050, T054-T055 | T030, T046, T050 | Planned; live NFR-002 gated |
| NFR-004 | T015-T016, T019, T030, T052, T054-T055 | T015, T019, T030, T052 | Fixture validated |
| NFR-005 | T002, T027-T030, T034-T036, T042-T044, T054-T055 | T027, T030, T034, T036, T042, T044 | Fixture validated |
| NFR-006 | T007-T008, T020, T046, T052, T054-T055 | T020, T046, T052 | Planned |
| NFR-007 | T004, T015-T16, T045-T046, T052, T054-T055 | T015, T046, T052 | Planned; TCBS path gated |
| SEC-001-SEC-006 | T004-T008, T015-T016, T022, T025-T026, T030, T045-T046, T049, T051-T055 | T005, T015, T022, T030, T046, T051-T052 | Planned; TCBS renewal path gated |
| SC-001 | T030, T053, T056 | T030, T056 | Planned |
| SC-002 | T030, T036, T053 | T030, T036 | Fixture validated |
| SC-003 | T037-T044, T053 | T037, T040, T044 | Fixture validated |
| SC-004 | T030, T036, T044, T053 | T030, T036, T044 | Fixture validated |
| SC-005 | T030, T050, T053 | T030, T050 | Fixture validated |
| SC-006 | T030, T036, T052-T053 | T030, T036, T052 | Fixture validated |
| SC-007 | T002, T027-T030, T034-T036, T042-T044, T053 | T027, T030, T034, T036, T042, T044 | Fixture validated |
| SC-008 | T005, T020, T030, T051-T053 | T005, T020, T030, T051-T052 | Planned |

## Delivery Notes

- Suggested MVP is T001-T030: private owner auth plus the P1 four-index journey
  running entirely from deterministic fixtures.
- T031-T044 add breadth and regime incrementally without weakening P1.
- T045-T047 remain incomplete until external evidence exists; T048's local
  implementation MUST be labeled as such and MUST NOT be deployed as a
  live/public market-data product.
- Never mark a task complete based only on code presence. Record its stated
  verification evidence.
- If discovery changes behavior, update `spec.md`/`plan.md` before continuing.

## Phase 8: Convergence

- [x] T057 [FR-001, FR-002, SEC-001, SEC-006, SC-001] Implement an owner-only browser login/session flow in `finvera-fe/src/features/auth/` and integrate it with `finvera-fe/src/app.tsx` so the SPA obtains CSRF, authenticates with Spring, presents authenticated/loading/denied states, and never persists credentials or session secrets in browser storage. (missing; CRITICAL; US1/AC1, T029)
      Verify: Vitest tests cover CSRF-before-login, successful session bootstrap, denied login, logout, and no credential/token persistence; authenticated fixture-mode page renders after the mocked Spring contract succeeds.
- [x] T058 [FR-001-FR-015, DATA-001-DATA-010, NFR-004, NFR-007, SC-001-SC-004] Implement explicit fixture-mode runtime bootstrap/replay behind a development-only Spring configuration in `finvera-be/src/main/java/com/minhnb/finvera_be/market/config/` and `market/service/` so accepted fixture observations, breadth, and regime data are loaded atomically into PostgreSQL for local acceptance journeys without a provider credential or production activation. (missing; CRITICAL; US1/AC1, plan: fixture-first implementation)
      Verify: Spring integration tests start fixture mode with PostgreSQL, expose an owner-authorized complete overview, replay deterministically without duplicate/regressing records, and prove the bootstrap is disabled outside explicit fixture mode.
      Evidence (2026-08-17): `FixtureRuntimeBootstrapServiceTests` passed against PostgreSQL 17 Testcontainers for owner-authenticated HTTP overview, persisted provenance, and idempotent replay; `FixtureBootstrapConfigurationTests` proved default/non-fixture disablement. Full `finvera-be/.\mvnw.cmd test`: 83 tests, 0 failures/errors.
