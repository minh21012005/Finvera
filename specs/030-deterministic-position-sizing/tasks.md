# Tasks: Deterministic Position Sizing

**Input**: Design artifacts from `specs/030-deterministic-position-sizing/`  
**Required**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, contracts, quickstart  
**Goal**: Deliver a deterministic, transparent, stateless sizing calculator

## Phase 1: Setup and Contract Baseline

- [x] T001 [FR-001, FR-013, DATA-002, DATA-003, SC-001, SC-004, SC-008] Add independently computed normal, cap-tie, exact-lot, sub-lot, precision, and overflow fixtures in `finvera-be/src/test/resources/positioning/position-sizing-v1-fixtures.json`
      Verify: fixture review recomputes every expected value from `contracts/position-sizing-v1.md`
      Depends: none
- [x] T002 [P] [FR-004, FR-006, FR-014, SEC-001, SC-002, SC-003, SC-005] Add request/response and problem-details contract tests in `finvera-be/src/test/java/com/minhnb/finvera_be/positioning/PositionSizingControllerTests.java`
      Verify: tests fail because the endpoint does not exist
      Depends: none
- [x] T003 [P] [FR-004, FR-005, DATA-001, DATA-008] Add strict frontend decoder/client tests in `finvera-fe/src/features/position-sizing/api/position-sizing.test.ts`
      Verify: tests fail because the client does not exist
      Depends: none

**Checkpoint**: Financial and public contracts are executable before behavior.

---

## Phase 2: Foundational Prerequisites

- [x] T004 [FR-001, FR-002, FR-003, FR-007, FR-008, FR-015, FR-016, FR-017, DATA-002, DATA-003, DATA-004, DATA-005, DATA-008, DATA-009, SC-001, SC-002, SC-004] Add table-driven, boundary, invariant, and randomized property tests for the pure engine in `finvera-be/src/test/java/com/minhnb/finvera_be/positioning/PositionSizingV1Tests.java`
      Verify: tests fail before `PositionSizingV1` exists and cover every candidate plus lot floor
      Depends: T001
- [x] T005 [FR-001, FR-002, FR-003, FR-007, FR-008, FR-013, FR-015, FR-016, FR-017, DATA-002, DATA-003, DATA-004, DATA-005, DATA-008, DATA-009] Implement immutable inputs/results, validation, `market-lot-v1`, and the exact pure formula in `finvera-be/src/main/java/com/minhnb/finvera_be/positioning/domain/PositionSizingV1.java`
      Verify: `cd finvera-be; .\mvnw.cmd "-Dtest=PositionSizingV1Tests" test` passes
      Depends: T004
- [x] T006 [P] [FR-009, FR-010, DATA-001, DATA-006, DATA-007, SEC-002] Define and test the owner-scoped coherent snapshot application interface in `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/service/PortfolioSizingDataService.java` and `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/service/PortfolioSizingDataServiceTests.java`
      Verify: owned snapshot is coherent; missing/deleted/foreign resources are indistinguishable
      Depends: T001
- [x] T007 [P] [FR-018, DATA-001, DATA-007, DATA-010] Define and test current-long-signal resolution in `finvera-be/src/main/java/com/minhnb/finvera_be/stock/service/StockSizingDataService.java` and `finvera-be/src/test/java/com/minhnb/finvera_be/stock/service/StockSizingDataServiceTests.java`
      Verify: current selector resolves; stale/absent/non-long/mismatched selectors do not
      Depends: T001
- [x] T008 [FR-004, FR-005, FR-006, FR-014, SEC-003] Implement typed request/response parsing and bounded validation reasons in `finvera-be/src/main/java/com/minhnb/finvera_be/positioning/dto/PositionSizingDtos.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/positioning/service/PositionSizingExceptions.java`
      Verify: T002 accepts contract shapes and rejects additional/mutually inconsistent input
      Depends: T002, T005
- [x] T009 [SEC-004, NFR-004, NFR-006] Add privacy-safe outcome/reason counters and duration timer design in `finvera-be/src/main/java/com/minhnb/finvera_be/positioning/service/PositionSizingMetrics.java`
      Verify: unit test asserts bounded tags and absence of financial values/identifiers
      Depends: T008

**Checkpoint**: Pure engine and narrow cross-module interfaces unblock stories.

---

## Phase 3: User Story 1 - Calculate a Reproducible Position Size (P1)

**Goal**: Manual calculator returns the greatest permitted standard-lot quantity or a precise withholding result.  
**Requirements**: FR-001–FR-008, FR-011–FR-017, DATA-001–DATA-005, DATA-008–DATA-009, SEC-001, SEC-003–SEC-004, NFR-001, NFR-003–NFR-006  
**Independent Test**: Run every v1 manual fixture twice and verify exact outputs, disclosures, invariants, and no side effect.

- [x] T010 [US1] [FR-001–FR-008, FR-011–FR-017, DATA-001–DATA-005, DATA-008–DATA-009, SC-001–SC-004] Add manual orchestration, validation, exclusion, and repeatability tests in `finvera-be/src/test/java/com/minhnb/finvera_be/positioning/PositionSizingServiceTests.java`
      Verify: tests fail before the service exists
      Depends: T005, T008, T009
- [x] T011 [US1] [FR-001–FR-008, FR-011–FR-017, DATA-001–DATA-005, DATA-008–DATA-009] Implement manual orchestration and evidence mapping in `finvera-be/src/main/java/com/minhnb/finvera_be/positioning/service/PositionSizingService.java`
      Verify: `cd finvera-be; .\mvnw.cmd "-Dtest=PositionSizingV1Tests,PositionSizingServiceTests" test` passes
      Depends: T010
- [x] T012 [US1] [FR-004, FR-006, FR-011, FR-012, SEC-001, SEC-003, NFR-004] Implement authenticated stateless endpoint and problem mapping in `finvera-be/src/main/java/com/minhnb/finvera_be/positioning/controller/PositionSizingController.java` and `finvera-be/src/main/java/com/minhnb/finvera_be/shared/api/ProblemDetailsAdvice.java`
      Verify: `cd finvera-be; .\mvnw.cmd "-Dtest=PositionSizingControllerTests" test` passes
      Depends: T011
- [x] T013 [P] [US1] [FR-004, FR-005, FR-006, DATA-001, DATA-008] Implement strict API types/decoder and problem handling in `finvera-fe/src/features/position-sizing/api/position-sizing.ts`
      Verify: `cd finvera-fe; npm test -- src/features/position-sizing/api/position-sizing.test.ts` passes
      Depends: T003, T012
- [x] T014 [US1] [FR-001, FR-004–FR-008, FR-011, FR-014–FR-017, NFR-005, SC-002, SC-003, SC-007] Add manual form/result component tests in `finvera-fe/src/features/position-sizing/position-sizing.test.tsx`
      Verify: tests fail before the page exists and cover fixed/percent risk, cost choice, optional caps, bindings, warnings, and withholding
      Depends: T013
- [x] T015 [US1] [FR-001, FR-004–FR-008, FR-011, FR-014–FR-017, NFR-005] Implement accessible manual form and transparent result breakdown in `finvera-fe/src/features/position-sizing/components/position-sizing-page.tsx`
      Verify: T014 passes with keyboard-accessible controls and non-color statuses
      Depends: T014
- [x] T016 [US1] [FR-011, FR-012, FR-014, SEC-001] Wire `/position-sizing` navigation and owner gate in `finvera-fe/src/router.ts`, `finvera-fe/src/app.tsx`, and `finvera-fe/src/features/auth/owner-access-gate.tsx`
      Verify: frontend route test shows authenticated page and no portfolio/order mutation
      Depends: T015

**Checkpoint**: P1 manual calculator is independently usable.

---

## Phase 4: User Story 2 - Size Against an Existing Portfolio (P2)

**Goal**: Optional portfolio mode applies coherent owned cash and exposure.  
**Requirements**: FR-009, FR-010, FR-014–FR-016, DATA-001, DATA-006–DATA-008, SEC-002–SEC-004, NFR-002, NFR-004, NFR-006  
**Independent Test**: Owned, capped, insufficient, stale, missing, and foreign portfolio fixtures return the expected result without leaking values.

- [x] T017 [US2] [FR-009, FR-010, FR-014–FR-016, DATA-001, DATA-006–DATA-008, SEC-002, SC-005] Add portfolio-mode service/integration/security cases in `finvera-be/src/test/java/com/minhnb/finvera_be/positioning/PositionSizingPortfolioIntegrationTests.java` and `finvera-be/src/test/java/com/minhnb/finvera_be/positioning/PositionSizingSecurityTests.java`
      Verify: tests fail before portfolio orchestration is wired
      Depends: T006, T011
- [x] T018 [US2] [FR-009, FR-010, FR-014–FR-016, DATA-001, DATA-006–DATA-008, SEC-002–SEC-004] Wire coherent portfolio snapshot resolution and truthful withholding in `finvera-be/src/main/java/com/minhnb/finvera_be/positioning/service/PositionSizingService.java`
      Verify: T017 passes and foreign/missing portfolio responses are identical
      Depends: T017
- [x] T019 [P] [US2] [FR-009, FR-010, FR-014–FR-016, DATA-001, DATA-006–DATA-008] Add portfolio-mode UI tests in `finvera-fe/src/features/position-sizing/position-sizing.test.tsx`
      Verify: tests fail before portfolio selection/source/status rendering exists
      Depends: T013, T017
- [x] T020 [US2] [FR-009, FR-010, FR-014–FR-016, DATA-001, DATA-006–DATA-008, NFR-005] Implement optional portfolio selection, sourced locked fields, staleness, and exposure rendering in `finvera-fe/src/features/position-sizing/components/position-sizing-page.tsx`
      Verify: T019 passes and switching back to manual clears trusted portfolio state
      Depends: T018, T019

**Checkpoint**: P2 works without weakening P1.

---

## Phase 5: User Story 3 - Understand and Reuse the Decision (P3)

**Goal**: Signal import and complete audit breakdown preserve provenance and future backtest compatibility.  
**Requirements**: FR-004, FR-005, FR-011, FR-013, FR-018, DATA-001, DATA-004, DATA-005, DATA-010, NFR-003, NFR-005  
**Independent Test**: Import/confirm each entry basis, reject changed/stale signal, inspect every source/version/assumption, and compare engine with compatibility fixture.

- [x] T021 [US3] [FR-018, DATA-001, DATA-007, DATA-010] Add signal import/orchestration and entry-basis tests in `finvera-be/src/test/java/com/minhnb/finvera_be/positioning/PositionSizingSignalIntegrationTests.java`
      Verify: tests fail before signal resolution is wired
      Depends: T007, T011
- [x] T022 [US3] [FR-018, DATA-001, DATA-007, DATA-010] Wire server-resolved current signal selection, confirmation, midpoint, and evidence in `finvera-be/src/main/java/com/minhnb/finvera_be/positioning/service/PositionSizingService.java`
      Verify: T021 passes; forged/stale/unconfirmed signal never yields a positive result
      Depends: T021
- [x] T023 [P] [US3] [FR-004, FR-005, FR-011, FR-018, DATA-001, DATA-010, NFR-005] Add signal-picker and full-breakdown UI cases in `finvera-fe/src/features/position-sizing/position-sizing.test.tsx`
      Verify: tests fail before explicit import/confirm/source rendering exists
      Depends: T013, T021
- [x] T024 [US3] [FR-004, FR-005, FR-011, FR-018, DATA-001, DATA-010, NFR-005] Implement signal selection/confirmation and full provenance/version display in `finvera-fe/src/features/position-sizing/components/position-sizing-page.tsx`
      Verify: T023 passes; editing imported values switches visibly to manual
      Depends: T022, T023
- [x] T025 [US3] [FR-013, NFR-003, SC-008] Add a compatibility test comparing public orchestration with direct pure-engine invocation in `finvera-be/src/test/java/com/minhnb/finvera_be/positioning/PositionSizingCompatibilityTests.java`
      Verify: interactive adapter and direct engine return identical financial fields
      Depends: T022

**Checkpoint**: All stories work and expose reproducible evidence.

---

## Final Phase: Cross-Cutting Validation and Release Readiness

- [x] T090 [FR-001–FR-018, DATA-001–DATA-010, SEC-001–SEC-004] Reconcile OpenAPI/financial contracts against backend and frontend decoders in `specs/030-deterministic-position-sizing/contracts/`
      Verify: OpenAPI parses; contract/controller/client tests pass with no orphan field or reason
      Depends: T016, T020, T024, T025
- [x] T091 [SEC-004, NFR-001, NFR-002, NFR-004, NFR-006, SC-006] Add privacy and warmed p95 tests in `finvera-be/src/test/java/com/minhnb/finvera_be/positioning/PositionSizingPerformanceTests.java`
      Verify: manual p95 ≤1s, linked p95 ≤2s, bounded metrics/logs contain no financial input
      Depends: T090
- [x] T092 [NFR-005, SC-003] Add P1 browser journey and accessibility assertions in `finvera-fe/tests/e2e/position-sizing.spec.ts`
      Verify: `cd finvera-fe; npm run test:e2e -- --grep "position sizing"` passes
      Depends: T090
- [ ] T093 [SC-007] Conduct and record an owner comprehension review of ten representative scenarios in `specs/030-deterministic-position-sizing/validation/owner-review.md`
      Verify: owner correctly identifies every binding cap and explains why quantity cannot increase in 10/10 cases, or findings are remediated and repeated
      Depends: T091, T092
- [x] T094 [FR-011, FR-013, DATA-005, SEC-004, NFR-006] Add operator rule-review/failure guidance in `docs/runbooks/position-sizing.md` and update `docs/ARCHITECTURE.md`, `docs/FEATURE_ROADMAP.md`, and `docs/PROJECT_CONTEXT.md`
      Verify: docs name rule versions, primary-source review evidence, metrics, rollback, and decision-support boundary
      Depends: T090, T091, T092
- [ ] T095 [SC-001–SC-008] Run and record repository quality gates in `specs/030-deterministic-position-sizing/quickstart.md`
      Verify: backend targeted/full tests, frontend targeted/full tests, lint, build, and E2E results are recorded accurately
      Depends: T093, T094, T096-T103

## Dependencies and Parallel Execution

```text
T001–T003 -> T004–T009 -> US1(T010–T016)
                          -> US2(T017–T020)
                          -> US3(T021–T025)
                          -> T090–T095
```

- T002/T003 and T006/T007 can proceed in parallel after their stated baselines.
- Backend and frontend story tests can proceed in parallel after OpenAPI/types exist.
- US2 and US3 adapters can proceed after US1 service, but their edits to the same service/page must be serialized.
- Suggested MVP is T001–T016: the standalone manual calculator.

## Requirement Coverage

| Requirement | Implementation tasks | Verification tasks |
|---|---|---|
| FR-001 | T005, T011, T015 | T001, T004, T010, T014, T090 |
| FR-002 | T005, T011 | T004, T010, T090 |
| FR-003 | T005, T011 | T001, T004, T010, T090 |
| FR-004 | T008, T011, T013, T015, T024 | T002, T003, T010, T014, T023, T090 |
| FR-005 | T008, T011, T013, T015, T024 | T003, T010, T014, T023, T090 |
| FR-006 | T008, T011, T012, T015 | T002, T010, T014, T090 |
| FR-007 | T005, T011, T015 | T004, T010, T014, T090 |
| FR-008 | T005, T011, T015 | T004, T010, T014, T090 |
| FR-009 | T006, T018, T020 | T017, T019, T090 |
| FR-010 | T006, T018, T020 | T017, T019, T090 |
| FR-011 | T011, T012, T015, T016, T024 | T010, T014, T023, T090, T093 |
| FR-012 | T011, T012, T016 | T010, T090 |
| FR-013 | T005 | T001, T004, T025, T090, T093 |
| FR-014 | T011, T016, T018, T020 | T010, T014, T017, T019, T090 |
| FR-015 | T005, T011, T018 | T004, T010, T017, T090 |
| FR-016 | T005, T011, T015, T018, T020 | T004, T010, T014, T017, T019, T090 |
| FR-017 | T005, T011, T015 | T004, T010, T014, T090 |
| FR-018 | T007, T022, T024 | T021, T023, T090 |
| DATA-001 | T006, T007, T011, T018, T022, T024 | T003, T010, T017, T021, T023, T090 |
| DATA-002 | T005 | T001, T004, T010, T090 |
| DATA-003 | T005 | T001, T004, T010, T090 |
| DATA-004 | T005, T011 | T004, T010, T090 |
| DATA-005 | T005 | T004, T090, T093 |
| DATA-006 | T006, T018 | T017, T090 |
| DATA-007 | T006, T007, T018, T022 | T017, T021, T090 |
| DATA-008 | T005, T008, T011, T018 | T003, T004, T010, T017, T090 |
| DATA-009 | T005, T011 | T004, T010, T090 |
| DATA-010 | T007, T022, T024 | T021, T023, T090 |
| SEC-001 | T012, T016 | T002, T090 |
| SEC-002 | T006, T018 | T017, T090 |
| SEC-003 | T008, T012, T018 | T002, T010, T017, T090 |
| SEC-004 | T009, T011, T018 | T009, T017, T090, T091, T093 |
| NFR-001 | T011 | T091 |
| NFR-002 | T018 | T091 |
| NFR-003 | T005 | T004, T025, T090 |
| NFR-004 | T009, T012, T018 | T002, T017, T091 |
| NFR-005 | T015, T020, T024 | T014, T019, T023, T092 |
| NFR-006 | T009 | T009, T091, T094 |
| SC-001 | T005, T011 | T001, T004, T010, T095 |
| SC-002 | T008, T011 | T002, T004, T010, T014, T095 |
| SC-003 | T011, T015, T024 | T002, T010, T014, T092, T095 |
| SC-004 | T005, T011 | T001, T004, T010, T095 |
| SC-005 | T006, T018 | T002, T017, T095 |
| SC-006 | T011, T018 | T091, T095 |
| SC-007 | T015, T020, T024 | T014, T093, T095 |
| SC-008 | T005, T011 | T001, T025, T095 |

## Delivery Notes

- Complete and validate P1 before portfolio and signal modes.
- Never mark a financial task complete from code presence alone.
- Record skipped checks and blockers; do not report them as passing.
- If implementation changes behavior, update spec/plan/contracts first.

## Phase 7: Convergence

- [x] T096 [CRITICAL] Add accepted-time and source evidence for symbol, venue, instrument status, and market-lot rule per DATA-001 and Constitution II (partial)
      Verify: calculated responses expose non-null accepted/reviewed time and authority for every system-sourced market fact
- [x] T097 Reject incomplete or incoherent portfolio snapshots before arithmetic per FR-010, DATA-006, and DATA-008 (partial)
      Verify: null totals/cash/as-of/coherence and inconsistent deployed values withhold `PORTFOLIO_DATA_UNAVAILABLE` without HTTP 500
      Depends: T096
- [x] T098 Preserve an imported signal as non-authoritative context when the owner overrides entry/stop manually per DATA-010 (partial)
      Verify: UI, request contract, result evidence, and tests distinguish authoritative manual prices from originating signal context
      Depends: T096
- [x] T099 Enforce every response enum, timestamp, decimal, and status invariant in the frontend decoder per SEC-003 and T090 (partial)
      Verify: decoder tests reject unknown constraint/unit/reason/warning values, malformed timestamps, and inconsistent calculated/withheld shapes
- [x] T100 Measure warmed manual and portfolio latency through the authenticated HTTP/application boundary per NFR-001, NFR-002, and SC-006 (partial)
      Verify: recorded p95 includes request decoding, security, controller, orchestration, and the owner-scoped portfolio application adapter; the underlying repository fixture is deterministic and production database/network latency remains covered by runtime metrics
      Depends: T097
- [x] T101 Encode money precision/scale and rate precision/scale separately in OpenAPI per DATA-002 and DATA-003 (partial)
      Verify: contract validation rejects values beyond precision 20, money scale 6, or rate scale 8
- [x] T102 Reconcile and enforce explicit all-zero cost semantics per FR-008, DATA-004, and the approved spec assumption (contradicts)
      Verify: five declared zero rates cannot bypass the explicit cost-exclusion warning path
- [x] T103 Reconcile T093-T095 dependency/status tracking and record the convergence validation outcome per SC-001-SC-008 (partial)
      Verify: completed technical gates and pending owner comprehension acceptance are represented without contradictory task ordering
      Depends: T096-T102
