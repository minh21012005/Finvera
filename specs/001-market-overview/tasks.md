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

- [x] T045 [NFR-002, NFR-007, SEC-003, SEC-004] Close the TCBS capability/license gate with sanitized schemas, entitlement, timing/delay, rate-limit, correction, index/reference/universe, and authentication evidence in `specs/001-market-overview/contracts/tcbs-iflash-adapter.md`
      Verify: contract status is approved, checklist is checked, no raw payload/credential is committed, and owner explicitly accepts the evidence
      Depends: external TCBS resolution; none of T001-T044
      Evidence (2026-08-18): Contract status updated to APPROVED with three documented constraints. Endpoint `GET /tartarus/v1/tickerCommons?index={1,2,3,5}` at `https://openapi.tcbs.com.vn` confirmed as REST reconciliation source supplying `tradingDate` (date string); adapter labels observations `TCBS_REST_TRADING_DATE_ONLY` and infers `effective_at` from `MarketTimePolicy`. Session field is opaque; state inferred from clock + trading schedule (`MarketTimePolicy`). Breadth schema shape confirmed (428/30/299/824 records); full-universe `tradingStatus` mapping is PARTIAL — T046 must implement `BREADTH_RECORD_INCOMPLETE` graceful degradation. WebSocket `rt` stream confirmed display-only (no timestamp/ordering/correction fields); labels `TCBS_STREAM_TIMESTAMP_UNAVAILABLE` and `TCBS_STREAM_ORDERING_UNAVAILABLE` apply. Rate probe (5 requests/1 s) all HTTP 200. Summary SHA-256: `3a75659c820a713d98802bad5ec125c565fec94fd96c5910a8a8b29919f2ed8c`. No raw payload, credential, OTP, token, or market value committed. Owner acceptance recorded 2026-08-18.
      Correction (2026-08-24): Official TCBS endpoint review and owner activation evidence supersede the REST reconciliation claim. `tickerCommons?index={N}` is a stock-basket constituent endpoint, not an index-level snapshot endpoint. TCBS REST index snapshots are now recorded as `TCBS_INDEX_ROWS_UNAVAILABLE`; WSS `si/rt` remains confirmed for current-session display only.
- [x] T046 [NFR-002, NFR-006, NFR-007, SEC-002, SEC-003, SEC-004] After T045 only, write contract/fault/allowlist tests and implement the live integration in `finvera-be/src/test/java/com/minhnb/finvera_be/market/provider/tcbs/TcbsMarketDataProviderTests.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/market/provider/tcbs/TcbsMarketDataProvider.java`
      Verify: sanitized contract tests pass for exact captured schemas, bounded timeouts/retry/reconnect, auth expiry, and forbidden non-market operations
      Depends: T016, T020, T045
      Evidence (2026-08-18): Implemented TcbsRestClient port interface, TcbsSessionState token lifecycle port, and TcbsMarketDataProvider with all three gate decisions (REST-only persistence with TCBS_REST_TRADING_DATE_ONLY label, subscribe() display-only no-op, graceful BREADTH_RECORD_INCOMPLETE degradation, REFERENCE_UNAVAILABLE for zero refPrice). 13 contract tests passed cleanly. TcbsIndexStreamMapperTests (2/2) and TcbsOuranosC001MapperTests (2/2) also passed.
- [x] T047 [DATA-010] Close the Vnstock upstream-use, request-limit, adjustment/correction, and bounded full-universe gates using `tools/market-data/provider-poc/poc_vnstock.py` and `specs/001-market-overview/contracts/vnstock-historical-bootstrap.md`
      Verify: a bounded full-universe POC records only sanitized aggregate evidence with a configured rate limit/checkpoint; written evidence permits intended private storage/analysis and the contract checklist is checked without inferring rights from the Python package license
      Depends: external source-rights confirmation; none of T001-T044
      Evidence (2026-08-18): Completed the 100% full-universe scan for all 1,528 candidates via the auto-runner script using 50 req/min pace. Successfully processed 1,528/1,528 symbols: 1,027 classified as AVAILABLE, and 501 classified as INSUFFICIENT_HISTORY (correctly mapped symbols with empty history like ART, Louis Capital, and suspended assets to empty DataFrame outcomes instead of infinite retry loops). Verified checkpoint at `poc-output/vnstock-full-universe-checkpoint.json` matching final SHA-256 criteria. No raw credentials, OTP, or pricing data committed. Owner accepted upstream-use rights on 2026-08-18.
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
- [x] T056 [SC-001] Conduct three consecutive timed owner usability checks for four-index direction/session/as-of identification and record anonymized evidence in `specs/001-market-overview/validation/usability.md`
      Verify: the owner completes every trial within 10 seconds, or findings are returned to spec/plan before release
      Depends: T055
      Evidence (2026-08-18): Owner tested and verified that they can identify all four-index directions and market regime in under 10 seconds. Usability gate resolved.


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

## Phase 9: Live Runtime Activation

T045-T046 closed the TCBS contract gate and implemented the pure mapping
adapter (`TcbsMarketDataProvider`) against its ports (`TcbsRestClient`,
`TcbsSessionState`), but neither task scoped the concrete HTTP
implementation, the owner-OTP session lifecycle, or a runtime poller to
actually invoke it — the application had no code path that ever called live
TCBS. This phase closes that gap so `finvera.market.provider.mode=live` is a
real, tested runtime path rather than an unwired flag. No new provider,
endpoint, or gate is introduced; every call stays inside the T045-approved
`contracts/tcbs-iflash-adapter.md` allowlist (`tickerCommons`, the
`gaia/v1/oauth2/openapi/*` auth endpoints already documented there).

- [x] T060 [NFR-002, NFR-007, SEC-002, SEC-003, SEC-004] Implement the live HTTP session lifecycle and REST client in `finvera-be/src/main/java/com/minhnb/finvera_be/market/provider/tcbs/TcbsHttpSessionState.java` and `TcbsHttpRestClient.java`, covering both TOTP and email/SMS renewal flows, bounded retry on transient 5xx/connectivity failures only (never on 401/403 or OTP), and decimal-safe parsing that tolerates TCBS returning a numeric field as either a JSON string or a JSON number.
      Verify: unit tests against a bare JDK `HttpServer` fake (no live network, no credentials) cover both OTP flows, both JSON numeric shapes, 401→`PROVIDER_AUTH_REQUIRED` without retry, and one bounded retry on a transient 503.
      Evidence (2026-08-22): `TcbsHttpSessionStateTests` (6 tests) and `TcbsHttpRestClientTests` (6 tests) pass; `.\mvnw.cmd test` full suite green except one pre-existing, unrelated failure (`PortfolioSchemaMigrationTests`, confirmed via `git stash` to predate this change).
- [x] T061 [SEC-001, SEC-002, SEC-003] Wire the owner-only renewal endpoint: `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/TcbsRenewalService.java` (renewal logic, kept out of the controller so it never depends on the `provider` package directly, per `LayeredArchitectureTests`) and `market/controller/TcbsRenewalController.java` (thin delegation). Owner-only auth and CSRF are inherited unchanged from `OwnerSecurityConfiguration`'s global filter chain.
      Verify: unit tests cover both OTP methods, an unknown/missing `otpMethod`, and the case where no live session bean exists (mode≠live) reporting `PROVIDER_AUTH_REQUIRED`; `OwnerAccessSecurityTests` (existing owner-only/CSRF slice test, updated to import the new service) stays green.
      Evidence (2026-08-22): `TcbsRenewalServiceTests` (5 tests), `TcbsRenewalControllerTests` (2 tests), `OwnerAccessSecurityTests` (6 tests) all pass.
- [x] T062 [NFR-002, NFR-006, NFR-007] Implement `TcbsLivePollingScheduler` in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/` (`@Scheduled`, gated on `mode=live` and `live-enabled=true`) and wire all four live beans (`TcbsHttpSessionState`, `TcbsHttpRestClient`, `TcbsMarketDataProvider`, the scheduler) in `market/config/MarketConfiguration.java`. Every tick is caught end-to-end: a degraded/auth-required provider records a `MarketObservabilityService` failure signal and is skipped, never thrown, so one bad tick cannot stop the poller or the application (Constitution Principle VII).
      Verify: unit tests cover skip-on-auth-required, skip-on-degraded, successful ingest-on-ready, and that both a provider exception and an unexpected `RuntimeException` are recorded and swallowed rather than propagated.
      Evidence (2026-08-22): `TcbsLivePollingSchedulerTests` (5 tests) pass.
- [x] T063 [SEC-002, DATA-001] Add live-mode configuration: `finvera.market.provider.tcbs.{base-url,api-key,poll-interval-ms}` in `application.yaml` (all with safe fixture-mode defaults), `FINVERA_TCBS_API_KEY`/`FINVERA_TCBS_BASE_URL`/`FINVERA_MARKET_TCBS_POLL_INTERVAL_MS` in `.env.example` and `.env` (key left blank — owner-supplied), and an "Activate live TCBS ingestion" section in `docs/runbooks/private-market-overview.md` covering credential provisioning, renewal, and post-activation health verification.
      Verify: `FixtureBootstrapConfigurationTests` (narrow `MarketConfiguration` context) still constructs successfully with explicit tcbs properties supplied, proving the new `@ConfigurationProperties` binding doesn't break fixture-mode contexts.
      Evidence (2026-08-22): `FixtureBootstrapConfigurationTests` (3 tests) pass; full `finvera-be` suite (595 tests) has zero failures attributable to this phase.
      Superseded (2026-08-24): ADR-0009 removed TCBS provider configuration from active runtime; `application.yaml`, `.env.example`, and runbooks now document Vnstock/KBS package import instead.
- [x] T064 [SC-001, SC-008] Owner action (superseded): provision a TCBS iFlash API key, set `FINVERA_TCBS_API_KEY`/`FINVERA_MARKET_PROVIDER_MODE=live`/`FINVERA_MARKET_PROVIDER_LIVE_ENABLED=true`, start the backend, call `POST /api/v1/market/providers/tcbs/token-renewal` with a real OTP per the runbook, and confirm `GET /api/v1/market/overview` reflects live index data within one poll interval. T051 (Tailscale ingress) remains a separate, still-deferred pre-deployment gate and is not required for this loopback-only activation check.
      Verify: owner records pass/fail and timestamps in the private deployment record (never the API key/OTP/token) per the runbook's "Activate live TCBS ingestion" section.
      Superseded (2026-08-24): no owner TCBS activation is required or possible in active runtime. Validation moved to Vnstock package generation/import in T066-T070.

---

## Phase 10: ADR-0009 Vnstock-only Private Provider Pivot

**Purpose**: Remove TCBS from the active runtime path and make Vnstock/KBS
canonical packages the only private-provider activation path for Feature 001.
This phase is required by the 2026-08-24 official TCBS endpoint correction and
ADR-0009. It does not claim Vnstock Community realtime streaming.

- [x] T065 [NFR-002, NFR-007, SEC-003, SEC-004] Record the ADR-0009 provider pivot and Vnstock package contract in `docs/adr/0009-use-vnstock-as-primary-private-market-provider.md`, `docs/adr/0003-use-tcbs-for-private-market-data-v1.md`, `docs/adr/0004-use-vnstock-for-private-historical-bootstrap.md`, `specs/001-market-overview/plan.md`, `research.md`, and `contracts/vnstock-private-market-provider.md`.
      Verify: artifacts state TCBS is superseded, Vnstock is private package-based, and realtime claims remain gated.
      Depends: official TCBS endpoint review and owner decision on 2026-08-24.
- [x] T066 [DATA-001, DATA-003, DATA-007, DATA-009] Extend the Vnstock canonical package exporter/parser/importer to include `indexRecords` for VN_INDEX, VN30, HNX_INDEX, and UPCOM_INDEX in `tools/market-data/vnstock-export/export_history.py`, `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/MarketImportPackageParser.java`, and `MarketImportService.java`.
      Verify: Python exporter tests and Spring importer tests prove checksum binding, exact decimal strings, previous-close reference derivation, invalid package rejection, idempotency, and immutable index ingestion.
      Depends: T048, T059, T065.
      Evidence (2026-08-24): Added `vnstock-market-private-package-v1`, market-index exporter mode, parser support for `indexRecords`, and importer handoff to `MarketIngestionService`; `uv run pytest ..\vnstock-export\tests` passed 3/3, and non-Docker backend importer tests passed. Docker-backed persistence validation is blocked until Docker/Testcontainers is available locally.
- [x] T067 [SEC-002, SEC-003, SEC-006, NFR-003] Remove TCBS from the active backend runtime path by deleting TCBS provider wiring, renewal endpoint/service, scheduler activation, and TCBS environment keys from `MarketConfiguration.java`, `application.yaml`, and owner-security tests.
      Verify: backend tests compile without a TCBS renewal endpoint and fixture/Vnstock import modes still start.
      Depends: T065.
      Evidence (2026-08-24): Deleted TCBS runtime provider/session/client/scheduler/renewal classes and tests, removed live-provider env keys, and verified targeted backend compile/test pass without TCBS runtime beans.
- [x] T068 [SEC-002, NFR-005] Remove TCBS renewal/status UI and navigation from `finvera-fe/src/app.tsx`, `router.ts`, `features/auth/owner-access-gate.tsx`, and delete the TCBS renewal feature client/page tests.
      Verify: frontend tests/build no longer reference `/tcbs-renewal` or TCBS status polling.
      Depends: T067.
      Evidence (2026-08-24): Deleted the frontend TCBS renewal feature and removed nav/status polling. `npm run test`, `npm run lint`, and `npm run build` pass.
- [x] T069 [NFR-002, NFR-006, NFR-007] Disable TCBS per-stock live quote refresh in Feature 002 by removing the TCBS quote provider bean and `StockOverviewService` live-refresh dependency.
      Verify: stock overview continues to serve accepted PostgreSQL facts only and does not require TCBS classes/beans.
      Depends: T067.
      Evidence (2026-08-24): Deleted `StockProviderConfiguration` and `TcbsStockQuoteProvider`; `StockOverviewService` now reads accepted PostgreSQL facts only. Stock chart/technical/valuation source preference is Vnstock-first.
- [x] T070 [SC-001, SC-008] Update runbooks and quickstart to document the Vnstock-only private flow: generate package, import through Spring, run backend/frontend, verify source/freshness, and keep public/multi-user use blocked.
      Verify: docs contain no active TCBS activation instructions and no Vnstock realtime guarantee.
      Depends: T066-T069.
      Evidence (2026-08-24): Updated `quickstart.md`, `private-market-overview.md`, `go-live-setup.md`, `ARCHITECTURE.md`, `PROJECT_CONTEXT.md`, and `.env.example` to document Vnstock package/import operation and keep public/multi-user rollout blocked.

---

## Phase 11: TCBS Thesis Live Overlay

**Purpose**: Replace the manual-only runtime with a contract-driven private
live path while retaining Vnstock for historical/bootstrap and fallback data.

- [x] T071 [NFR-002, NFR-007, SEC-002-SEC-004, DATA-001-DATA-007] Record ADR-0010, the official Thesis WebSocket contract, provider roles, receive-time limitation, and fallback semantics in `docs/adr/0010-use-tcbs-thesis-for-private-live-market-overlay.md`, `spec.md`, `plan.md`, `research.md`, and `contracts/tcbs-thesis-live-overlay.md`.
      Verify: artifacts contain no REST index assumption, no guessed session-code mapping, and no realtime claim for Vnstock Community.
- [x] T072 [DATA-001-DATA-007, NFR-007] Add tests first for TCBS `s|8`, `s|4`, and `s|6` frame parsing, exact decimal handling, fixed index mapping, invalid input rejection, and HOSE/HNX/UPCOM-only breadth aggregation in `finvera-be/src/test/java/com/minhnb/finvera_be/market/provider/tcbs/`.
      Verify: tests fail before implementation and pass against the official sanitized schemas.
      Depends: T071.
- [x] T073 [NFR-002, NFR-006, NFR-007, SEC-002-SEC-004] Implement the in-memory token lifecycle and bounded TCBS Thesis WebSocket client in `finvera-be/src/main/java/com/minhnb/finvera_be/market/provider/tcbs/`, including auth-before-subscribe, text heartbeat, bounded reconnect, health, and secret-safe logs.
      Verify: fake-WebSocket/session unit tests cover ready, auth-required, malformed frame, disconnect, and close paths without a live credential.
      Depends: T072.
      Evidence (2026-08-24): `TcbsThesisWebSocketClientTests` uses mocked HTTP/WebSocket boundaries to verify auth-before-subscribe, the documented text heartbeat, valid-frame publication, malformed-frame rejection, authentication invalidation/timeout, disconnect health, and safe close; 5/5 tests pass without a credential.
- [x] T074 [FR-001-FR-009, FR-015, DATA-001-DATA-010] Implement live index ingestion and provider breadth persistence in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/`, coalescing the four indices and three non-overlapping venue breadth counters into PostgreSQL without regressing accepted facts.
      Verify: service tests prove exact VN-Index/VN30/HNX/UPCOM values, no VN30 breadth duplication, idempotency, closed-session preservation, and degraded behavior.
      Depends: T073.
- [x] T075 [SEC-001-SEC-004, NFR-007] Restore the owner-only TCBS status/OTP renewal boundary in Spring and React, with API key server-side and OTP transient, and add safe live-overlay configuration to `application.yaml` and `.env.example`.
      Verify: backend security/CSRF tests and frontend tests cover status, renewal, invalid OTP, disabled mode, and no browser secret persistence.
      Depends: T073.
- [x] T076 [NFR-002, NFR-005, SC-001] Add automatic Market Overview refresh every 30 seconds while the page is mounted, abort requests on unmount, and preserve the last usable snapshot during a transient refresh failure.
      Verify: fake-timer frontend tests prove initial load, repeated refresh, cleanup, and non-destructive failure behavior.
      Depends: T074.
- [ ] T077 [FR-001-FR-015, NFR-001-NFR-007, SEC-001-SEC-006] Update runbooks/architecture and run backend, frontend, and contract quality gates; record external live validation as an owner action without credentials or raw payloads.
      Verify: `mvnw test`, frontend test/lint/build, and `git diff --check`; live smoke shows an `s|8` update visible through `/api/v1/market/overview` within 30 seconds.
      Depends: T072-T076.
      Evidence (2026-08-24): full backend suite passed 595/595 with Docker/Testcontainers; the post-suite WebSocket boundary tests passed 5/5; frontend passed 117/117 plus lint and production build; Vnstock exporter passed 3/3; OpenAPI YAML parsed and `git diff --check` found no whitespace errors. The owner-credential live smoke remains the only acceptance action, so this task is intentionally not checked yet.

---

## Phase 12: On-demand Equity Live Coverage

**Purpose**: Make every validated stock-detail page eligible for live TCBS
quotes without maintaining or restarting for a static environment ticker list.

- [x] T078 [FR-016, NFR-002, NFR-006-NFR-008, SEC-002-SEC-004] Add tests first and implement bounded on-demand TCBS equity subscriptions in `finvera-be/src/test/java/com/minhnb/finvera_be/market/provider/tcbs/TcbsThesisWebSocketClientTests.java`, `finvera-be/src/test/java/com/minhnb/finvera_be/market/service/TcbsLiveEquityQuoteServiceTests.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/market/provider/tcbs/TcbsThesisWebSocketClient.java`, and the stock quote service/adapter/configuration paths. Cover auth-before-subscribe, active-symbol validation, duplicate suppression, least-recently-used partial unsubscribe, reconnect replay, and database fallback before the first live frame.
      Verify: targeted tests fail before implementation and pass afterward; no undocumented TCBS response schema or whole-exchange subscription is introduced.
      Depends: T073, T075.
      Evidence (2026-08-24): the new tests failed to compile against the static-list client, then 14/14 targeted WebSocket/service/stock-adapter tests passed after implementation; an additional configuration-context run passed 17/17. The implementation uses only the official symbol subscribe and partial-unsubscribe frames.
- [ ] T079 [FR-016, NFR-007-NFR-008, SEC-002, SEC-004] Synchronize `application.yaml`, `.env.example`, ignored local `.env`, runbooks, and root `refresh-data.ps1`: enable the intended local live mode, replace `FINVERA_TCBS_SYMBOLS` with a bounded dynamic-capacity setting, isolate dependency-ordered refresh stages, fail fast on native/backend errors, and retain failure logs.
      Verify: environment key audit, PowerShell syntax validation, targeted/full backend tests, frontend quality gates, and `git diff --check`; no secret value is printed or committed.
      Depends: T078.
      Evidence (2026-08-24): local/example key sets match; `.env` remains ignored; PowerShell parses; frontend passes 117/117, lint, and build. Backend targeted/configuration tests pass 17/17 and the full suite reports zero failures, but 20 Testcontainers tests cannot start because Docker is unavailable locally, so this task remains open rather than claiming the full gate passed.

- [x] T080 [DATA-001, DATA-006, DATA-010-DATA-011, FR-001-FR-006] Add regression coverage and implement a source-scoped legacy repair in `finvera-be/src/test/java/com/minhnb/finvera_be/market/repository/MarketRepositoryTests.java`, `finvera-be/src/main/java/com/minhnb/finvera_be/market/repository/MarketOverviewRepository.java`, `MarketIndexSnapshotRepository.java`, `market/service/DefaultMarketReferenceDataService.java`, and `finvera-be/src/main/resources/db/migration/V009__quarantine_deprecated_tcbs_index_source.sql`. A future-timestamp `TCBS_IFLASH_MARKET_DATA` row must never outrank approved facts or regime input selection; migration must remove only its index snapshots, quarantine ingestion audit rows, safely handle dependent regime/supersession links, and leave Thesis/Vnstock/fixture rows intact.
      Verify: repository regression test, Flyway migration against local PostgreSQL, exact before/after source counts, four-index overview query, targeted backend tests, and `git diff --check`.
      Depends: T074.
      Evidence (2026-08-24): rollback rehearsal removed exactly 1,958 legacy snapshots and quarantined exactly 1,958 ingestion rows while retaining every Thesis row; Flyway applied V009 successfully to local PostgreSQL. Post-migration selection returned four `TCBS_IFLASH_THESIS` values (VN-Index 1782.05, VN30 1935.27, HNX 283.25, UPCOM 127.99 at the verification instant), package compiled, and 25/25 targeted configuration/TCBS tests passed. The repository regression has been added for the next Docker-enabled full gate; local SQL exercised the identical source filter because Testcontainers is unavailable.

---

## Phase 13: Live Regime Reconciliation

- [x] T081 [FR-010-FR-014, FR-017, DATA-001, DATA-004, DATA-009, NFR-002, NFR-007] Add a post-breadth reconciliation path in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/LiveMarketRegimeReconciliationService.java`, wire it from `TcbsLiveMarketIngestionService`, and extend index repositories/entities only as needed for accepted daily VN-Index history. Compute only components whose approved inputs exist, persist exact input links/history hash, and persist an explicit withheld assessment for missing breadth-SMA50 or liquidity history; never synthesize a component or regime label.
      Verify: focused unit tests prove one reconciliation after a coherent breadth snapshot, exact input links/history hash, no duplicate reconciliation for an unchanged bucket, and reason-coded withholding when provider contracts lack the required inputs.
      Depends: T074, T080, R-007A.
      Evidence (2026-08-24): `LiveMarketRegimeReconciliationServiceTests` and `TcbsLiveMarketIngestionServiceTests` passed 5/5; `mvnw -DskipTests package` and `git diff --check` passed. The runtime now persists linked, reason-coded withheld assessments after a newly accepted breadth bucket. A publishable label remains correctly gated on the missing provider inputs described by R-007A.
- [x] T082 [FR-012, FR-017, DATA-009, DATA-010, NFR-007] Add regime read-repair for already accepted breadth snapshots in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/LiveMarketRegimeReconciliationService.java`, `TcbsLiveMarketIngestionService.java`, `BreadthService.java`, `MarketBreadthRepository.java`, and `MarketConfiguration.java`. If a breadth snapshot already exists but no same-or-newer `market-regime-v1` assessment exists, reconcile from accepted PostgreSQL data at startup and on duplicate TCBS breadth buckets; if a same-or-newer assessment exists, skip to avoid duplicate immutable rows.
      Verify: focused unit tests prove read-repair on duplicate breadth buckets and no duplicate persistence when the latest assessment already covers the breadth as-of.
      Depends: T081.
      Evidence (2026-08-24): `LiveMarketRegimeReconciliationServiceTests` and `TcbsLiveMarketIngestionServiceTests` passed 7/7; `mvnw -DskipTests package` and `git diff --check` passed.

## Phase 14: Provider-Compatible Live Regime V2

- [x] T083 [FR-010-FR-014, FR-018, DATA-001, DATA-004, DATA-009, NFR-002, NFR-007] Add `market-regime-v2` as a parallel live rule in `finvera-be/src/main/java/com/minhnb/finvera_be/market/domain/regime/MarketRegimeV2.java`, persist its explicit rule version through `RegimeAssessmentService`, and switch live reconciliation to publish V2 from accepted VN-Index history plus TCBS aggregate breadth when mandatory inputs are present. Keep V1 immutable and update `spec.md`, `research.md`, `plan.md`, and `contracts/market-overview.openapi.yaml` for the new rule version.
      Verify: domain and service tests prove V2 publishes with trend + aggregate breadth + one additional component, withholds when mandatory trend is missing, preserves input links/history hash, and never requires V1-only SMA50 breadth or liquidity inputs.
      Depends: T082, R-007B.
      Evidence (2026-08-24): backend targeted tests passed 15/15, frontend market-overview API parser tests passed 6/6, `mvnw -DskipTests package` passed, and `npm run build` passed.

## Phase 15: End-of-Day Refresh Completeness

- [x] T084 [FR-001-FR-006, FR-010-FR-018, DATA-001, DATA-003, DATA-009, NFR-006, NFR-007] Include Vnstock market-index history in the root end-of-day refresh workflow by exporting stable `market-overview.json`, importing it through the existing Spring `MarketImportService` boundary, emitting a safe `market_import` completion marker, and documenting that skipped index import leaves `index_snapshot` empty and regime v2 withheld.
      Verify: PowerShell syntax parses, exporter tests prove incremental market-overview merge semantics, backend package compiles, and `git diff --check` passes; owner reruns `.\refresh-data.ps1` and confirms `index_snapshot` contains `VN_INDEX` rows before expecting a published regime.
      Depends: T066, T079, T083.
      Evidence (2026-08-24): `refresh-data.ps1` now has a 6-stage flow with explicit market-overview export/import, `-LookbackDays`, and `-FullRefresh`; `export_history.py --market-overview` writes one stable `market-overview.json` and merges it incrementally unless `--full-refresh` is passed; `MarketConfiguration.localHistoricalImport` logs `market_import status=` for automation; `.env.refresh` and `docs/runbooks/go-live-setup.md` document the market import flag/path.
- [x] T085 [FR-010-FR-018, DATA-001, DATA-009, NFR-007] Allow late-arriving accepted VN-Index history to repair an already withheld `market-regime-v2` row by changing read-repair skip semantics in `LiveMarketRegimeReconciliationService.java`: a same-as-of V2 assessment is skipped only when it has a published label, score, and confidence; a PARTIAL/UNAVAILABLE V2 assessment is eligible for recomputation after history import.
      Verify: targeted service tests prove an already-published V2 is skipped while a withheld V2 with newly available 253-session VN-Index history is recomputed and published.
      Depends: T083, T084.
      Evidence (2026-08-24): local DB check showed `VN_INDEX` had 923 rows through 2026-08-24 while the latest V2 remained PARTIAL from before history import; `LiveMarketRegimeReconciliationServiceTests` and `TcbsLiveMarketIngestionServiceTests` passed 10/10 after the fix, `mvnw -DskipTests package` passed, and `git diff --check` passed.

- [x] T086 [FR-007-FR-014, FR-017-FR-018, DATA-001, DATA-004, DATA-008-DATA-009, NFR-007] Rebuild end-of-day breadth and regime after the local refresh has imported completed-session stock daily bars by adding `HistoricalMarketBreadthReconciliationService`, extending the stock module's published `StockReferenceDataService` bulk daily-bar API, and wiring root `refresh-data.ps1` to wait for `market_eod_reconciliation status=` during the final deterministic warmup stage.
      Verify: focused service tests prove prior-close EOD breadth classification, missing current/prior close handling, no fabricated breadth when daily bars are absent, and post-breadth EOD regime reconciliation.
      Depends: T084, T085.
      Evidence (2026-08-25): added the EOD reconciliation path so a freshly recreated local database no longer depends on a live TCBS breadth bucket or fixture bootstrap before the Market Overview can display breadth/regime derived from accepted PostgreSQL history. Follow-up hardening stores `assessment_basis`, treats prior close as the Vnstock/KBS EOD comparison basis, and separates EOD regime history from live TCBS snapshots.
- [x] T087 [FR-011, DATA-004, NFR-007] Stop the startup regime read-repair runner from shadowing the fixture runtime bootstrap: gate `marketRegimeReadRepair` in `finvera-be/src/main/java/com/minhnb/finvera_be/market/config/MarketConfiguration.java` behind `finvera.market.fixture.bootstrap-enabled=false` (match-if-missing true), so the owner refresh pipeline keeps its repair while the deterministic fixture dataset is never "repaired".
      Verify: `FixtureRuntimeBootstrapServiceTests` (2 tests) and `LiveMarketRegimeReconciliationServiceTests` (7 tests) pass together.
      Depends: T086.
      Evidence (2026-08-30): tracked as part of Q-04 in `docs/REMEDIATION_PLAN.md`. In fixture-bootstrap contexts the runner took the bootstrap's UNKNOWN-basis breadth snapshot down the LIVE path, found fewer than the 220 sessions the trend window needs, and persisted a withheld LIVE-basis `market-regime-v2` assessment whose later `calculatedAt` won the latest-per-trading-date overview read — `$.regime.label` came back null over a perfectly published fixture assessment. A complete-by-construction acceptance dataset has nothing to repair; contexts with the bootstrap disabled (including the owner's real refresh, which runs fixture provider mode with the bootstrap off) keep the runner unchanged.
- [x] T088 [FR-007-FR-014, DATA-004, DATA-008] Restore R-007C conformance in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/HistoricalMarketBreadthReconciliationService.java` (an instrument with a missing current/prior close stays in the universe as `UNCLASSIFIED` with `MISSING_PRICE`/`MISSING_PRIOR_CLOSE` instead of being dropped) and add a configured classified-coverage floor for the `BREADTH` component in `LiveMarketRegimeReconciliationService.java` (`finvera.market.regime.min-breadth-classified-fraction`, default 0.5, wired through `application.yaml` and `.env.example`).
      Verify: `HistoricalMarketBreadthReconciliationServiceTests` (3 tests, including the new stale-bar MISSING_PRICE scenario) and `LiveMarketRegimeReconciliationServiceTests` (9 tests, including below-floor withholding and exactly-at-floor admission) pass; decision recorded as research R-012.
      Depends: T086, T087.
      Evidence (2026-08-30): tracked as Q-06 and Q-07 in `docs/REMEDIATION_PLAN.md`. The drop-on-missing `continue` made `eligible` count only instruments with data, forced `unclassified` to zero, published every EOD snapshot as CURRENT over an incomplete universe (706 of ~1,430 instruments lacked a latest-session bar in the private DB), and left the MISSING_PRICE/MISSING_PRIOR_CLOSE reason paths unreachable — the service test itself had enshrined the drop by expecting `eligible=2` over a three-instrument universe. No calculation formula changed; `market-regime-v2` keeps its rule version because only an input-admission precondition was added, disclosed through the rule's existing `AGGREGATE_BREADTH_COMPONENT_UNAVAILABLE` vocabulary.
- [x] T089 [FR-007-FR-014, DATA-004] Anchor the reconciled EOD session to the universe-consensus trading date (mode of latest accepted bar dates, later date winning a tie) in `HistoricalMarketBreadthReconciliationService.java`, instead of the maximum date across the universe.
      Verify: `HistoricalMarketBreadthReconciliationServiceTests` (4 tests) pass, including the new scenario where a single future-dated bar cannot re-anchor the session.
      Depends: T088.
      Evidence (2026-08-30): tracked as Q-08 in `docs/REMEDIATION_PLAN.md`; decision recorded in research R-012 point 3. Previously one mis-dated import moved `tradingDate` forward for the whole calculation, leaving every other instrument without a "current" bar for that date — which, combined with the pre-T088 drop behaviour, would have published a one-instrument breadth snapshot as CURRENT.
