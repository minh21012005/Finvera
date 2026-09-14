# Tasks: Historical Strategy Backtesting

**Spec**: [spec.md](spec.md)  
**Plan**: [plan.md](plan.md)  
**Contracts**: [public API](contracts/public-api.openapi.yaml), [financial v1](contracts/backtest-financial-v1.md)

## Phase 1: Setup and Baselines

- [x] T001 Record pre-feature backend and frontend quality-gate baselines in `specs/031-historical-strategy-backtesting/quickstart.md`
- [x] T002 [P] Add independent engine, ledger, pyramiding, and metric golden vectors under `finvera-be/src/test/resources/backtest/`
- [x] T003 [P] Add all-eight-strategy point-in-time compatibility fixtures under `finvera-be/src/test/resources/backtest/strategies/`
- [x] T004 Validate the OpenAPI document and financial examples in `specs/031-historical-strategy-backtesting/contracts/public-api.openapi.yaml`

## Phase 2: Foundational Data and Boundaries

- [x] T005 [DATA-007, DATA-008, DATA-009] Implement shared immutable backtest value types and stable enums in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/domain/BacktestTypes.java`
- [x] T006 [FR-005, FR-014, FR-015, DATA-012] Create additive run/result schema, checks, owner/idempotency/claim indexes, and child uniqueness constraints in `finvera-be/src/main/resources/db/migration/V022__create_backtest_schema.sql`
- [x] T007 [P] Map run lifecycle and frozen assumptions in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/entity/BacktestRunEntity.java`
- [x] T008 [P] Map tranche trades in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/entity/BacktestTradeEntity.java`
- [x] T009 [P] Map equity points in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/entity/BacktestEquityPointEntity.java`
- [x] T010 [P] Map metrics, entry events, and evidence in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/entity/BacktestResultEntities.java`
- [x] T011 [FR-005, FR-013, FR-015] Implement owner-qualified listing/detail, atomic claim, heartbeat, stale recovery, and terminal persistence queries in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/repository/BacktestRunRepository.java`
- [x] T012 [P] Implement stable paged child repositories in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/repository/BacktestTradeRepository.java` and sibling result repositories
- [x] T013 [DATA-001-DATA-006] Add a cutoff-aware immutable historical snapshot application contract in `finvera-be/src/main/java/com/minhnb/finvera_be/stock/service/BacktestHistoryDataService.java`
- [x] T014 [DATA-001, DATA-004, DATA-005] Add completed-session and lot-rule evidence application contract in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/BacktestMarketRuleService.java`
- [x] T015 [SEC-001-SEC-004] Define validated public request/response/problem DTOs matching OpenAPI in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/dto/BacktestDtos.java`
- [x] T016 Validate V022 constraints, ownership indexes, immutable terminal rows, and migration compatibility in `finvera-be/src/test/java/com/minhnb/finvera_be/backtest/BacktestPersistenceTests.java`

## Phase 3: User Story 1 - Run a Reproducible Backtest (P1)

**Goal**: Produce exact asynchronous trades and equity from any supported strategy with no future-data read.  
**Independent Test**: All eight strategy fixtures and financial vectors yield exact next-open fills, tranche quantities, costs, exits, cash, equity, and fingerprints; incoherent data withholds.

- [x] T017 [P] [US1] Specify engine golden/property tests for event order, gaps, stop-first, terminal close, corporate-action discontinuities, and ledger identities in `finvera-be/src/test/java/com/minhnb/finvera_be/backtest/domain/BacktestEngineV1Tests.java`
- [x] T018 [P] [US1] Specify pyramiding boundary tests for episodes, 0.5 ATR, four tranches, and rejection reasons in `finvera-be/src/test/java/com/minhnb/finvera_be/backtest/domain/PyramidingV1Tests.java`
- [x] T019 [P] [US1] Specify aggregate-heat and `position-sizing-v1` compatibility tests in `finvera-be/src/test/java/com/minhnb/finvera_be/backtest/domain/BacktestSizingCompatibilityTests.java`
- [x] T020 [P] [US1] Specify future-revision, warm-up, and all-eight-strategy parity tests in `finvera-be/src/test/java/com/minhnb/finvera_be/backtest/BacktestStrategyCompatibilityTests.java`
- [x] T021 [US1] Implement deterministic session event ordering, fills, cash ledger, and equity invariants in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/domain/BacktestEngineV1.java`
- [x] T022 [US1] Implement false-to-true episode detection and bounded anti-martingale additions in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/domain/PyramidingV1.java`
- [x] T023 [US1] Implement remaining aggregate-risk calculation and pure sizing delegation in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/domain/BacktestSizingPolicyV1.java`
- [x] T024 [US1] Implement cutoff-aware bar/indicator revision selection, coherence checks, warm-up, adjustment-factor discontinuity evidence, and selected-input fingerprint in `finvera-be/src/main/java/com/minhnb/finvera_be/stock/service/BacktestHistoryDataService.java`
- [x] T025 [US1] Implement session/lot lookup and unavailable evidence in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/BacktestMarketRuleService.java`
- [x] T026 [US1] Orchestrate snapshot, all-eight-strategy evaluation, engine, sizing, and typed terminal outcomes in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/service/BacktestExecutionService.java`
- [x] T027 [US1] Implement create validation, idempotency, frozen cutoff, and after-commit dispatch in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/service/BacktestCommandService.java`
- [x] T028 [US1] Add authenticated 202 create endpoint and typed errors in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/controller/BacktestController.java`
- [x] T029 [US1] Add create form for strategy, period, capital, tranche/aggregate risk, and explicit costs in `finvera-fe/src/features/backtest/components/backtest-create-form.tsx`
- [x] T030 [US1] Implement strict request/summary/problem decoders and create client in `finvera-fe/src/features/backtest/api/backtest.ts`
- [x] T031 [US1] Validate the happy path plus incoherent-history and unsupported-corporate-action withholding in `finvera-be/src/test/java/com/minhnb/finvera_be/backtest/BacktestCreateIntegrationTests.java`

## Phase 4: User Story 2 - Understand Performance and Risk (P2)

**Goal**: Expose exact metrics, trades, equity and limitations without NaN, infinity, or hidden assumptions.  
**Independent Test**: Golden ledgers for win/loss/flat/leap/multi-year/no-trade cases produce all eight exact values or stable unavailable reasons and an accessible result view.

- [x] T032 [P] [US2] Specify metric golden/property tests including all undefined cases in `finvera-be/src/test/java/com/minhnb/finvera_be/backtest/domain/BacktestMetricsV1Tests.java`
- [x] T033 [P] [US2] Specify immutable evidence, pagination, and terminal-availability contract tests in `finvera-be/src/test/java/com/minhnb/finvera_be/backtest/BacktestResultContractTests.java`
- [x] T034 [US2] Implement total return, CAGR, win rate, profit factor, drawdown, Sharpe, average return, and count in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/domain/BacktestMetricsV1.java`
- [x] T035 [US2] Assemble immutable metrics, evidence, warnings, limitations, trades, equity, and entry events in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/service/BacktestQueryService.java`
- [x] T036 [US2] Add owner-qualified detail and paged trades/equity/events endpoints in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/controller/BacktestController.java`
- [x] T037 [US2] Extend strict frontend decoders for detail, metric availability, evidence, trade, equity, and event enums in `finvera-fe/src/features/backtest/api/backtest.ts`
- [x] T038 [P] [US2] Implement accessible equity chart with a text/table equivalent in `finvera-fe/src/features/backtest/components/backtest-equity-chart.tsx`
- [x] T039 [P] [US2] Implement metric, assumptions, provenance, cost, and limitation panels in `finvera-fe/src/features/backtest/components/backtest-result.tsx`
- [x] T040 [US2] Implement paged trade and rejected-entry audit views in `finvera-fe/src/features/backtest/components/backtest-ledger.tsx`
- [x] T041 [US2] Validate exact result rendering, unavailable metrics, warnings, and keyboard/text equivalents in `finvera-fe/src/features/backtest/backtest-page.test.tsx`

## Phase 5: User Story 3 - Monitor and Revisit Runs (P3)

**Goal**: List, poll and recover durable owner runs without blocking ordinary use or leaking ownership.  
**Independent Test**: Owner can revisit state/results; cross-owner IDs are indistinguishable; duplicate/stale runs publish one result with bounded recovery.

- [x] T042 [P] [US3] Specify simultaneous claim, heartbeat, stale recovery, second-attempt failure, and atomic rollback tests in `finvera-be/src/test/java/com/minhnb/finvera_be/backtest/BacktestWorkerRecoveryTests.java` and `finvera-be/src/test/java/com/minhnb/finvera_be/backtest/BacktestRepositoryIntegrationTests.java`
- [x] T043 [P] [US3] Specify negative authorization tests for every run and child route in `finvera-be/src/test/java/com/minhnb/finvera_be/backtest/BacktestSecurityTests.java`
- [x] T044 [US3] Implement bounded executor, atomic claim, heartbeat, retry, and recovery scan in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/service/BacktestWorker.java`
- [x] T045 [US3] Add safe bounded worker configuration with a maintenance override in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/config/BacktestWorkerConfig.java`
- [x] T046 [US3] Implement owner-qualified list/status/result queries and paging in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/service/BacktestQueryService.java`
- [x] T047 [US3] Implement run list, progress polling, terminal states, and retry-safe navigation in `finvera-fe/src/features/backtest/components/backtest-page.tsx`
- [x] T048 [US3] Register authenticated navigation and route in `finvera-fe/src/router.ts`
- [x] T049 [US3] Validate polling, revisit, empty, withheld, failed, and owner session flows in `finvera-fe/src/features/backtest/backtest-page.test.tsx`

## Phase 6: Polish, Operations, and Validation

- [x] T050 [P] [FR-016, NFR-001, NFR-002] Measure create and <=10-year worker p95 without logging financial payloads in `finvera-be/src/test/java/com/minhnb/finvera_be/backtest/BacktestPerformanceTests.java`
- [x] T051 [P] [SEC-004, NFR-005] Add bounded counters/timers/gauges and privacy tests in `finvera-be/src/main/java/com/minhnb/finvera_be/backtest/service/BacktestMetrics.java`
- [x] T052 Add queue/recovery/invariant/rollback operations and data limitations in `docs/runbooks/backtesting.md`
- [x] T053 Update module boundary and decision index in `docs/ARCHITECTURE.md`
- [x] T054 Update delivery status and durable capability summary in `docs/FEATURE_ROADMAP.md` and `docs/PROJECT_CONTEXT.md`
- [x] T055 Validate P1 keyboard flow, equity text equivalent, stop-first explanation, and all-eight-strategy selection in `finvera-fe/tests/e2e/backtest.spec.ts`
- [x] T056 Run and record targeted/full backend, frontend, lint, build, OpenAPI, migration, performance, and E2E gates in `specs/031-historical-strategy-backtesting/quickstart.md`
- [ ] T057 Conduct and record the ten-scenario owner comprehension review for SC-007 in `specs/031-historical-strategy-backtesting/validation/owner-review.md`

## Dependencies and Parallel Execution

```text
T001-T004 -> T005-T016 -> US1(T017-T031) -> US2(T032-T041)
                                      \-> US3(T042-T049)
US2 + US3 -> T050-T057
```

- T002/T003 and entity mappings T007-T010 can run in parallel.
- US1 test specifications T017-T020 can run in parallel before engine work.
- US2 metric/backend and UI presentation files allow the marked parallel work.
- US3 recovery and authorization tests can run in parallel.
- Suggested first independently valuable slice is Setup + Foundation + US1;
  it creates and completes an auditable run even before the full result UI.

## Requirement Coverage

| Requirements | Implementation | Verification |
|---|---|---|
| FR-001-FR-008 | T005-T030 | T002-T004, T016-T020, T031 |
| FR-009-FR-012 | T008-T010, T032-T040 | T032, T033, T041 |
| FR-013-FR-017 | T011, T028, T036, T044-T049 | T031, T042, T043, T049, T055 |
| DATA-001-DATA-013 | T005-T014, T021-T026, T034-T035 | T002, T003, T016-T020, T032, T033 |
| SEC-001-SEC-004 | T011, T015, T027-T028, T036, T044-T046, T051 | T016, T031, T043, T051 |
| NFR-001-NFR-005 | T044-T052 | T042, T043, T049-T051, T055-T057 |
| SC-001-SC-008 | All phases | T016-T020, T031-T033, T041-T043, T049-T057 |

## Delivery Rules

- Write financial, future-read, authorization, and recovery tests before their
  production behavior and never mark them complete from code presence alone.
- Do not access stock/market repositories from the backtest module.
- Do not publish partial children or retry indefinitely.
- Update spec/contract/plan first if any financial behavior changes.
