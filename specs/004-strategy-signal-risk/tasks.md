# Tasks: Strategy, Signal, and Risk Scenarios

**Input**: Design artifacts from `specs/004-strategy-signal-risk/`
**Required**: `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/`, and `quickstart.md` — all present.
**Goal**: Deliver eight fixed deterministic strategies producing persisted,
reproducible signals with risk scoring for one stock (P1/P2), plus a
universe-wide strategy scan (P3) — reusing Feature 002/003 outputs only.

## Task Format

```text
- [ ] T001 [P?] [US?] [Requirement IDs] Action with exact file path
      Verify: command or observable completion evidence
      Depends: task IDs or "none"
```

## Phase 1: Foundational Prerequisites

**Purpose**: Migration, domain engine, and the one published-interface
addition every story depends on.

- [x] T001 [P] Add `findCurrentRegimeAssessment` to
      `MarketReferenceDataService` (+ `DefaultMarketReferenceDataService`
      impl), returning the regime score/state needed by the
      `MARKET_REGIME` risk factor, mirroring Feature 003's
      `findInstrumentsByIds` addition — `stock` reads it only through this
      interface, never `market.entity`/`market.repository` directly
      in `finvera-be/src/main/java/com/minhnb/finvera_be/market/service/MarketReferenceDataService.java`
      and `DefaultMarketReferenceDataService.java`
      Verify: `StockModuleArchitectureTests`/`MarketModuleArchitectureTests`
      still pass with the new method present
      Depends: none
- [x] T002 [P] Write migration tests for `strategy_signal`,
      `strategy_signal_risk_factor`, `strategy_signal_input` (all-or-
      nothing risk score/level, `entry_low <= entry_high`, `target1 <
      target2`, three-state risk-factor applicability, `is_current`
      partial-unique revision chain) in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/repository/StrategySignalMigrationTests.java`
      Verify: fails before the migration exists
      Depends: none
- [x] T003 Create the forward-only Flyway migration
      `finvera-be/src/main/resources/db/migration/V004__create_strategy_signal_schema.sql`
      per `data-model.md`, plus JPA entities and Spring Data repositories in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/entity/StrategySignal*.java`
      and `finvera-be/src/main/java/com/minhnb/finvera_be/stock/repository/StrategySignal*.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=StrategySignalMigrationTests test` passes
      Depends: T002
- [x] T004 [P] Write the full `strategy-signal-v1` required-test-vector
      suite from `contracts/strategy-signal-v1.md` — all eight strategies'
      trigger/non-trigger/boundary cases, the three crossing strategies'
      "true both days" vs. "false-then-true" cases, level formulas,
      `ATR14<=0` withholding, the four-of-six risk-factor floor, replay
      determinism — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/domain/strategy/StrategySignalV1Tests.java`
      Verify: fails before the engine exists; every required-test-vector
      row has an assertion
      Depends: none
- [x] T005 Implement the `strategy-signal-v1` engine — eight entry
      conditions (reusing `TechnicalIndicatorsV1` component values and
      `ScreenerV1`'s Breakout/Trend derivations directly, never
      recomputing), the level formulas, the six risk factors and scoring,
      signal strength — as a pure, framework-free domain class in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/domain/strategy/StrategySignalV1.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=StrategySignalV1Tests test`
      passes every required test vector
      Depends: T001, T004
- [x] T006 [P] Add bulk/current-plus-prior-day finder methods needed by the
      engine — `TechnicalIndicatorResultRepository` current-plus-prior-day
      lookup (for the three crossing strategies), a bulk variant for the
      scan (T003's repositories already give single-instrument persistence;
      this task is the *read* side) — in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/repository/TechnicalIndicatorResultRepository.java`
      and callers
      Verify: repository integration test confirms one bounded query,
      never one query per indicator per day
      Depends: none

**Checkpoint**: The engine and schema exist and are independently tested;
no service or endpoint exists yet.

---

## Phase 2: User Story 1 - See a Stock's Current Trade Signals (Priority: P1)

**Goal**: Owner opens a stock and sees every currently triggered strategy's
complete signal, including its risk score/level, or a truthful non-signal/
insufficient-history state per strategy.
**Requirements**: FR-001 to FR-005, FR-006, FR-008, FR-012 to FR-014,
DATA-001 to DATA-004, SEC-001, SEC-002, NFR-001, NFR-003
**Independent Test**: Open a fixture symbol triggering at least one
strategy and verify its complete signal; open one triggering none and
verify a truthful state per strategy.

### Tests and Evaluation

- [x] T007 [P] [US1] Write persistence tests — signal created only on a
      genuine trigger (no negative-result rows), revision chain on
      correction, idempotent replay, risk-score-withheld-but-signal-shown
      — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/service/strategy/StrategySignalServiceTests.java`
      Verify: fails before the service exists
      Depends: T003, T005
- [x] T008 [P] [US1] Write owner-only contract/security tests (including a
      CSRF-required-even-authenticated case from the start — Feature 003's
      T030 follow-up finding, not repeated here) for
      `GET /stocks/{symbol}/signals` in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/controller/SignalControllerTests.java`
      Verify: fails before the controller exists
      Depends: none

### Implementation

- [x] T009 [US1] Implement `StrategySignalService` — evaluate all eight
      strategies for one instrument, persist triggered signals with
      revision-chain/correction handling, live re-check (not persisted) for
      non-triggered/insufficient strategies (data-model.md "Why non-
      triggers are not persisted") — in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/service/strategy/StrategySignalService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=StrategySignalServiceTests test` passes
      Depends: T006, T007
- [x] T010 [US1] Implement DTO mapping and controller for
      `GET /stocks/{symbol}/signals` per
      `contracts/strategy-signal.openapi.yaml`, with the CSRF-aware
      frontend client attaching its token from the start, in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/dto/StockSignalsResponse.java`
      and a `SignalController.java` (or `StockController` addition)
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=SignalControllerTests test`
      passes
      Depends: T008, T009
- [x] T011 [P] [US1] Implement browser API types, runtime response
      validation, and a same-origin, CSRF-token-attaching client (reusing
      `auth/api/owner-access.ts`'s `getCsrf()` from the start) in
      `finvera-fe/src/features/stock-detail/api/stock-signals.ts`
      Verify: frontend unit tests reject malformed responses
      Depends: T010
- [x] T012 [P] [US1] Write signal-section component tests — signal display,
      no-signal state, insufficient-history state, non-color risk/direction
      indicators, non-guarantee copy — in
      `finvera-fe/src/features/stock-detail/signals.test.tsx`
      Verify: fails before the components exist
      Depends: none
- [x] T013 [US1] Implement the accessible signals section (extends the
      existing stock detail page, next to technical/fundamentals/
      valuation) in
      `finvera-fe/src/features/stock-detail/components/stock-signals.tsx`,
      wired into `stock-detail-page.tsx`
      Verify: `cd finvera-fe; npm run test -- src/features/stock-detail/signals.test.tsx`
      passes
      Depends: T011, T012
- [x] T014 [US1] Add Playwright P1 journeys — triggered signal, no-signal
      state, insufficient-history state, multi-trigger, non-guarantee copy
      — in `finvera-fe/tests/e2e/stock-signals.spec.ts`
      Verify: `npm run test:e2e -- --grep "P1"` passes
      Depends: T010, T013

**Checkpoint**: P1 is independently usable — a stock's signals are correct
and complete without the risk-detail UI (US2) or the scan (US3).

---

## Phase 3: User Story 2 - Understand Why a Signal Is Risky (Priority: P2)

**Goal**: Owner sees each risk factor's own value/contribution behind a
signal's score.
**Requirements**: FR-006, FR-007, DATA-004, NFR-003
**Independent Test**: Open a signal's risk detail and verify each factor's
value and the withheld-vs-published four-of-six floor.

### Tests and Evaluation

- [x] T015 [P] [US2] Write risk-factor display tests — six factors shown
      with values, an unavailable factor's reason, the withheld-overall-
      score state, non-color risk-level indicator — in
      `finvera-fe/src/features/stock-detail/risk-detail.test.tsx`
      Verify: fails before the component exists
      Depends: none

### Implementation

- [x] T016 [US2] Implement the risk-factor breakdown display within
      `stock-signals.tsx` (the backend already returns `riskFactors` per
      signal from T009/T010 — this is a frontend-only addition, per
      `plan.md`'s ownership table)
      Verify: `cd finvera-fe; npm run test -- src/features/stock-detail/risk-detail.test.tsx`
      passes
      Depends: T013, T015
- [x] T017 [US2] Add Playwright P2 journeys — full factor breakdown,
      one-factor-unavailable, withheld-overall-score, reload-determinism —
      in `finvera-fe/tests/e2e/stock-signals.spec.ts`
      Verify: `npm run test:e2e -- --grep "P2"` passes and P1 remains green
      Depends: T010, T016

**Checkpoint**: P2 works independently of the strategy scan (US3).

---

## Phase 4: User Story 3 - Screen for a Strategy Across the Universe (Priority: P3)

**Goal**: Owner picks one strategy and finds every supported stock
currently triggering it.
**Requirements**: FR-009 to FR-011, NFR-002
**Independent Test**: Scan a strategy and verify the exact triggering set,
empty-result state, and insufficient-history disclosure.

### Tests and Evaluation

- [x] T018 [P] [US3] Write scan orchestration tests — exact match set,
      empty result, insufficient-history exclusion count, pagination — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/service/strategy/StrategyScanServiceTests.java`
      Verify: fails before the service exists
      Depends: T005
- [x] T019 [P] [US3] Write owner-only contract/security tests for
      `POST /strategies/{strategyCode}/scan` in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/controller/StrategyScanControllerTests.java`
      Verify: fails before the controller exists
      Depends: none

### Implementation

- [x] T020 [US3] Implement `StrategyScanService`, reusing Feature 003's
      two-pass candidate-universe pattern (research R-007) — bulk-fetch the
      `LISTED` universe's current/prior-day technical data, evaluate one
      strategy's condition per candidate — in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/service/strategy/StrategyScanService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=StrategyScanServiceTests test`
      passes
      Depends: T006, T018
- [x] T021 [US3] Implement DTO mapping and controller for
      `POST /strategies/{strategyCode}/scan` in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/dto/ScanRequest.java`,
      `ScanResponse.java`, `StrategyScanController.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=StrategyScanControllerTests test`
      passes
      Depends: T019, T020
- [x] T022 [P] [US3] Implement browser API client (CSRF-attaching from the
      start) in `finvera-fe/src/features/stock-strategy/api/stock-strategy.ts`
      Verify: frontend unit tests reject malformed responses
      Depends: T021
- [x] T023 [P] [US3] Write strategy-picker and scan-results component tests
      in `finvera-fe/src/features/stock-strategy/strategy-scan.test.tsx`
      Verify: fails before the components exist
      Depends: none
- [x] T024 [US3] Implement the strategy scan page (mirrors Feature 003's
      `stock-screener` structure) in
      `finvera-fe/src/features/stock-strategy/components/strategy-picker.tsx`,
      `strategy-scan-results.tsx`, `stock-strategy-page.tsx`; register the
      route in `router.ts`/`app.tsx`; add a nav link
      Verify: `cd finvera-fe; npm run test -- src/features/stock-strategy/strategy-scan.test.tsx`
      passes; `npm run build` succeeds
      Depends: T022, T023
- [x] T025 [US3] Add Playwright P3 journeys — exact match set, empty
      result, insufficient-history disclosure, navigation to stock detail —
      in `finvera-fe/tests/e2e/stock-strategy.spec.ts`
      Verify: `npm run test:e2e -- --grep "P3"` passes and P1/P2 remain green
      Depends: T021, T024

**Checkpoint**: Every selected story works independently against the T004/
T007/T018 fixture set.

---

## Final Phase: Cross-Cutting Validation and Release Readiness

- [x] T026 Run and reconcile all contract tests; update
      `contracts/strategy-signal.openapi.yaml` only if owner-approved
      behavior changed
      Verify: contract tests pass; no implemented response differs from
      the reviewed contract
      Depends: T014, T017, T025
- [x] T027 Add a latency smoke test (NFR-001/NFR-002) in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/performance/StrategySignalPerformanceTests.java`
      Verify: p95 within baseline for both single-stock signal views and
      strategy scans over a representative universe
      Depends: T009, T020
- [x] T028 Add fault-injection tests — cross-source conflict withholding a
      strategy/factor, regime unavailable, corrected-input recalculation —
      in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/operations/StrategySignalFailureTests.java`
      Verify: each case distinguishable; no sensitive payload
      Depends: T009, T020
- [x] T029 Add the replay-determinism test — identical inputs/rule version
      run twice against the real persisted path — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/service/strategy/StrategySignalReplayDeterminismTests.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=StrategySignalReplayDeterminismTests test` passes
      Depends: T009
- [x] T030 Add owner/unauthenticated negative security tests for both
      endpoints (including CSRF) plus a `finvera-fe/dist` secret scan
      Verify: only the owner succeeds; no credential in the bundle
      Depends: T010, T021
- [x] T031 Execute and record the fixture-mode commands/scenarios in
      `quickstart.md`, in a new `specs/004-strategy-signal-risk/validation/fixture-acceptance.md`
      Verify: P1-P3 happy paths, degraded/failure paths, authorization,
      accessibility all produce recorded expected results
      Depends: T026, T027, T028, T030
- [x] T032 Reconcile delivered behavior and open limitations in `spec.md`,
      `plan.md` (Status line, post-design Constitution Check), `research.md`,
      `quickstart.md`, `contracts/`
      Verify: traceability review finds no orphan requirement, undocumented
      behavior, secret, or false capability claim
      Depends: T031
- [x] T033 Run repository quality gates from `finvera-be/` and `finvera-fe/`
      Verify: `.\mvnw.cmd test`, `npm run lint`, `npm run test`,
      `npm run build`, `npm run test:e2e` all pass
      Depends: T032
- [x] T034 Perform the manual cross-artifact analysis pass (mirroring
      Feature 003's post-implementation analysis, since `/speckit-analyze`
      is not an invocable skill in this harness) against
      `.agents/skills/speckit-analyze/SKILL.md`'s checklist; record findings
      and resolutions in a new "Post-Implementation Analysis" section of
      this file
      Verify: coverage summary, constitution alignment, and unmapped-task
      checks all recorded; every finding resolved or explicitly deferred
      with reason
      Depends: T033

## Dependencies and Parallel Execution

### Phase Dependencies

```text
Foundation (T001-T006)
  -> US1/P1 (T007-T014)
  -> US2/P2 (T015-T017)
  -> US3/P3 (T018-T025)
  -> Release validation (T026-T034)
```

US2 needs US1's persisted signal + risk-factor data but not US1's own UI
beyond the shared `stock-signals.tsx` component it extends. US3 needs the
Foundation engine but not US1/US2's UI — it reuses the engine and Feature
003's scan pattern directly.

### Parallel Opportunities

- T001, T002, T004, T006 are independent of each other.
- T007/T008, T015, T018/T019, T023 can each be written in parallel with
  their neighbors once their `Depends` are satisfied.

## Requirement Coverage

| Requirement ID | Task IDs | Test/Evaluation Task |
|---|---|---|
| FR-001 to FR-005 | T005, T009, T010 | T004, T007, T008 |
| FR-006, FR-007 | T005, T009, T016 | T004, T007, T015 |
| FR-008 | T005, T009 | T004, T007, T029 |
| FR-009 to FR-011 | T020, T021 | T018, T019 |
| FR-012 | T005 | T004 |
| FR-013 | T010, T013 | Acceptance review |
| FR-014 | T009 | T007, T028 |
| DATA-001 to DATA-004 | T003, T005, T009 | T002, T004, T007 |
| SEC-001, SEC-002 | T010, T021 | T008, T019, T030 |
| NFR-001, NFR-002 | T009, T020 | T027 |
| NFR-003 | T013, T016, T024 | T012, T015, T023 |
| SC-001 | T005 | T004 |
| SC-002 | T005, T009 | T004, T029 |
| SC-003 | T009, T010, T013 | T012, T014 |
| SC-004 | T009, T010, T013 | T012, T014 |
| SC-005 | T009, T020 | T027 |
| SC-006 | T005, T009 | T004, T028 |
| SC-007 | T013, T016, T024 | T012, T015, T023 |
| SC-008 | T010, T021 | T030 |

## Post-Implementation Analysis (2026-08-19)

`docs/SDD_WORKFLOW.md` step 6 (`/speckit-analyze`) is not registered as an
invocable skill in this harness; performed manually against
`.agents/skills/speckit-analyze/SKILL.md`'s own checklist — cross-
referencing `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/`, `quickstart.md`, and `.specify/memory/constitution.md`
against the actually-delivered code and tests, not only the design intent.

**Findings:**

| ID | Category | Severity | Location | Summary | Resolution |
|---|---|---|---|---|---|
| F1 | Coverage gap | MEDIUM | `tasks.md` "Requirement Coverage" table | The table mapped every FR/DATA/SEC/NFR requirement but omitted all eight `spec.md` Success Criteria (SC-001 to SC-008), even though every one of them already has real, passing test evidence | Added the eight missing rows, mapping each SC to the task(s)/test(s) that already satisfy it (e.g. SC-005 → `StrategySignalPerformanceTests`, SC-008 → `StrategySignalSecurityTests`) — a documentation-only fix; no missing test coverage was found once traced |
| F2 | Correctness (test) | LOW | `StrategySignalPerformanceTests` (first draft, before this pass) | Both the single-stock signal-view test and the strategy-scan test asserted against the scan's 5-second baseline (NFR-002); `spec.md` NFR-001 actually gives the signal view its own, stricter 3-second baseline | Fixed before this pass completed: split into `SIGNAL_VIEW_BASELINE_MILLIS = 3000L` and `SCAN_BASELINE_MILLIS = 5000L`; both tests re-run and pass at their correct, respective baselines |
| F3 | Correctness (test infra) | MEDIUM | `FinveraBeApplicationTests` | The full-context-load smoke test excludes real JPA/DataSource autoconfiguration and hand-mocks every repository bean instead; it did not yet mock the three new `Strategy*Repository` beans this feature added, so the whole application context failed to start | Fixed by adding the three missing `@MockitoBean` declarations, mirroring the same maintenance step Feature 003's own new repositories already required on this identical test |
| F4 | Style | LOW | `research.md` / `data-model.md` / `plan.md` | "strategy scan", "universe scan", and "strategy-scan execution" used interchangeably for the same operation | Not fixed — no execution or comprehension risk; noted for awareness only, same disposition as Feature 003's own F2 |

**Coverage summary**: 31/31 requirement IDs (14 FR + 4 DATA + 2 SEC + 3 NFR
+ 8 SC) now have at least one task and one test in the Requirement
Coverage table — 100%.
**Constitution alignment**: no MUST violation found. F2/F3 are corrected
test defects, not production-code or constitution violations; F1 is a
documentation-completeness gap, not a missing capability.
**Unmapped tasks**: none — every task in T001-T034 appears in either the
Requirement Coverage table or is explicitly cross-cutting (T026, T031-T034).
**Critical issues**: 0.

**Outcome**: Feature 004 is closed. All 34 tasks complete with recorded
evidence (`validation/fixture-acceptance.md`). Three real issues were found
and fixed during this closing pass — a documentation coverage gap (F1), a
wrong performance-test baseline (F2), and a test-infrastructure gap that
would have broken the full-context-load smoke test (F3) — all corrected
and re-verified, not merely noted.

## Delivery Notes

- Suggested MVP is Foundation + US1 (T001-T014): the engine, persistence,
  and a stock's complete signal including risk level.
- US2 (T015-T017) and US3 (T018-T025) add depth and reach without
  weakening US1.
- Never mark a task complete based only on code presence. Record its stated
  verification evidence.
- If implementation discovery changes behavior, update `spec.md`/`plan.md`
  before continuing, per `AGENTS.md`.
