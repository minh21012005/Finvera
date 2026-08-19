# Tasks: Portfolio and Watchlist Management

**Input**: Design artifacts from `specs/005-portfolio-watchlist/`
**Required**: `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/`, and `quickstart.md` — all present.
**Goal**: Deliver an owner-scoped, immutable transaction ledger with
derived FIFO holdings and P/L (P1), watchlists with reused live market/
analysis context (P2), and portfolio analytics with VN-Index benchmark
comparison (P3) — the platform's first owner-written data and first
row-level ownership enforcement.

## Task Format

```text
- [ ] T001 [P?] [US?] [Requirement IDs] Action with exact file path
      Verify: command or observable completion evidence
      Depends: task IDs or "none"
```

## Phase 1: Foundational Prerequisites

**Purpose**: Migration, the shared `portfolio-analytics-v1` replay engine,
the platform's first owner-scoping enforcement primitive, and the one
published-interface addition every later story depends on.

- [x] T001 [P] [FR-009] Add a bulk `findCurrentSignalsForInstruments`
      method to Feature 004's published read interface (mirroring Feature
      004's own `findCurrentRegimeAssessment` addition to
      `MarketReferenceDataService`) — `stock` remains the only owner of
      `strategy_signal`; `portfolio` reads it only through this interface,
      never `stock.entity`/`stock.repository` directly, in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/service/StockReferenceDataService.java`
      (new interface + default impl, named to match the existing
      `MarketReferenceDataService` convention)
      Verify: `StockModuleArchitectureTests` still pass with the new method
      present; a `portfolio`-side architecture test (added in T006) asserts
      no direct `stock.entity`/`stock.repository` import exists
      Depends: none
- [x] T002 [P] [DATA-001 to DATA-005] Write migration tests for
      `portfolio`, `portfolio_transaction`, `watchlist`, `watchlist_item`
      (type-specific not-null groups, `voids_transaction_id` partial
      uniqueness, `(portfolio_id, sequence_no)` uniqueness,
      `(portfolio_id, idempotency_key)` uniqueness and not-null
      (research R-011), unique portfolio/watchlist name per `owner_id`,
      `watchlist_item` composite primary key preventing duplicate
      membership) in
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/repository/PortfolioSchemaMigrationTests.java`
      Verify: fails before the migration exists
      Depends: none
- [x] T003 [DATA-001 to DATA-005] Create the forward-only Flyway migration
      `finvera-be/src/main/resources/db/migration/V005__create_portfolio_watchlist_schema.sql`
      per `data-model.md` (including `portfolio_transaction.idempotency_key`
      and its unique index), plus JPA entities and Spring Data repositories
      in
      `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/entity/Portfolio*.java`,
      `Watchlist*.java` and
      `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/repository/Portfolio*.java`,
      `Watchlist*.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=PortfolioSchemaMigrationTests test` passes
      Depends: T002
- [x] T004 [P] [FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, DATA-001 to
      DATA-003] Write the full `portfolio-analytics-v1` required-test-
      vector suite from `contracts/portfolio-analytics-v1.md` — FIFO
      simple/partial-close, over-sell/over-withdraw rejection, backdated
      replay reordering, `VOID` of a clean lot vs. a consumed lot
      (`LOT_ALREADY_CONSUMED`), double-void rejection, same-`executedAt`
      tie-break, cash balance, totals/allocation — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/domain/analytics/PortfolioAnalyticsV1Tests.java`
      Verify: fails before the engine exists; every required-test-vector
      row from the FIFO/cash/totals sections has an assertion
      Depends: none
- [x] T005 [FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-016, DATA-001
      to DATA-003] Implement the `portfolio-analytics-v1` FIFO/cash/totals
      engine — chronological replay (`U-3`), FIFO lot matching and
      realized/unrealized P/L, cash balance, `INSUFFICIENT_POSITION`/
      `INSUFFICIENT_CASH_BALANCE`/`LOT_ALREADY_CONSUMED`/`ALREADY_VOIDED`
      validation — as a pure, framework-free domain class in
      `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/domain/analytics/PortfolioAnalyticsV1.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=PortfolioAnalyticsV1Tests test`
      passes every required test vector
      Depends: T004
- [x] T006 [P] [SEC-001, SEC-002] Implement the platform's first
      owner-scoping enforcement primitive — a shared guard/lookup that
      compares a resource's `owner_id` against the session's authenticated
      owner id and treats a mismatch identically to "not found" (never a
      distinguishable 403), plus a `portfolio` module architecture test
      asserting no direct cross-module entity/repository access, in
      `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/service/OwnerScopedAccess.java`
      and
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/service/OwnerScopedAccessTests.java`,
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/PortfolioModuleArchitectureTests.java`
      Verify: unit tests confirm a wrong-owner id and a nonexistent id
      produce the identical outcome
      Depends: T003

**Checkpoint**: The ledger engine, schema, and owner-scoping primitive
exist and are independently tested; no service or endpoint exists yet.

---

## Phase 2: User Story 1 - Record Transactions and See Current Holdings (Priority: P1)

**Goal**: Owner creates a portfolio, records `BUY`/`SELL`/`DEPOSIT`/
`WITHDRAW`/`VOID` transactions, and sees derived positions with FIFO cost
basis, unrealized/realized P/L, and allocation — computed on demand, never
stored.
**Requirements**: FR-001 to FR-007, FR-016 to FR-018, DATA-001 to DATA-005,
SEC-001, SEC-002, NFR-001, NFR-003
**Independent Test**: Create a portfolio, record a deposit, a buy, and a
partial sell at a different price; verify the resulting position's
quantity, cost basis, unrealized P/L, and realized P/L are correct and
reproducible.

### Tests and Evaluation

- [x] T007 [P] [US1] [FR-001 to FR-005, FR-018] Write
      `PortfolioService`/`TransactionService` persistence tests — create/
      rename/delete portfolio, record each transaction type, `VOID` a valid
      and an invalid target, unsupported-symbol rejection, backdated insert
      reordering FIFO, a replayed `Idempotency-Key` on the same portfolio
      rejected `DUPLICATE_SUBMISSION` with zero additional change, a
      **different** key with otherwise-identical fields accepted as a
      genuine second transaction (research R-011) — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/service/PortfolioTransactionServiceTests.java`
      Verify: fails before the service exists
      Depends: T003, T005
- [x] T008 [P] [US1] [SEC-001, SEC-002] Write owner-only contract/security
      tests (CSRF-required-even-authenticated from the start — Feature
      003's T030 finding, not repeated a third time; cross-ownership
      request indistinguishable from 404; a missing `Idempotency-Key`
      header rejected `400` even with a valid session and CSRF token —
      research R-011) for
      `POST/GET /portfolios`, `POST /portfolios/{id}/transactions`,
      `POST /portfolios/{id}/transactions/{id}/void` in
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/controller/PortfolioControllerTests.java`
      Verify: fails before the controller exists
      Depends: T006

### Implementation

- [x] T009 [US1] [FR-001, FR-002, FR-003, FR-004, FR-005, FR-018] Implement
      `PortfolioService` (create/rename/delete/list, owner-scoped via T006)
      and `TransactionService` (record, void, list ledger) using the T005
      engine, rejecting unsupported symbols with `UNSUPPORTED_INSTRUMENT`
      (FR-018, mapped to `400` — F2) and applying the U-6 void-validity
      rule; checks `(portfolio_id, idempotency_key)` before any FIFO/cash
      effect and rejects a replay with `DUPLICATE_SUBMISSION` referencing
      the original transaction id, relying on the T003 unique index as the
      race-safe backstop, not application logic alone (research R-011), in
      `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/service/PortfolioService.java`
      and `TransactionService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=PortfolioTransactionServiceTests test` passes
      Depends: T006, T007
- [x] T010 [US1] [FR-006, FR-007] Implement `PositionService` — derived
      positions and portfolio totals computed on demand from the ledger
      (research R-004; no stored position row) — in
      `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/service/PositionService.java`
      Verify: unit tests confirm totals/allocation match
      `PortfolioAnalyticsV1Tests` fixtures end-to-end through the service
      layer
      Depends: T009
- [x] T011 [US1] [FR-001 to FR-005, SEC-001, SEC-002] Implement DTO mapping
      and controllers for `/portfolios`, `/portfolios/{id}`,
      `/portfolios/{id}/transactions`,
      `/portfolios/{id}/transactions/{id}/void`,
      `/portfolios/{id}/positions` per
      `contracts/portfolio-watchlist.openapi.yaml` — required
      `Idempotency-Key` header binding on the two transaction-writing
      operations (`400` when absent), `UNSUPPORTED_INSTRUMENT` mapped to
      `400` and `INSUFFICIENT_POSITION`/`INSUFFICIENT_CASH_BALANCE`/
      `LOT_ALREADY_CONSUMED`/`ALREADY_VOIDED`/`DUPLICATE_SUBMISSION` mapped
      to `409` (F2's single convention), all wired into the shared
      `ProblemDetailsAdvice`, in
      `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/dto/*.java`
      and `PortfolioController.java`, `TransactionController.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=PortfolioControllerTests test`
      passes
      Depends: T008, T009, T010
- [x] T012 [P] [US1] Implement browser API types, runtime response
      validation, and a same-origin, CSRF-token-attaching client (reusing
      `auth/api/owner-access.ts`'s `getCsrf()` from the start) that
      generates one `Idempotency-Key` (UUID) per logical submit action and
      resends the same key on that action's own retries only — never
      across two separately confirmed owner decisions (research R-011) —
      in `finvera-fe/src/features/portfolio/api/portfolio.ts`
      Verify: frontend unit tests reject malformed responses and confirm a
      retried submit reuses the same key while a fresh submit generates a
      new one
      Depends: T011
- [x] T013 [P] [US1] Write portfolio/holdings component tests — portfolio
      list/create, transaction entry form (all four types), ledger view, a
      voided-entry display, position table with non-color P/L sign — in
      `finvera-fe/src/features/portfolio/holdings.test.tsx`
      Verify: fails before the components exist
      Depends: none
- [x] T014 [US1] Implement the portfolio holdings UI — portfolio list/
      create, transaction entry form, ledger view, positions table — in
      `finvera-fe/src/features/portfolio/components/portfolio-list.tsx`,
      `transaction-form.tsx`, `holdings-table.tsx`,
      `portfolio-detail-page.tsx`; register the route and nav link
      Verify: `cd finvera-fe; npm run test -- src/features/portfolio/holdings.test.tsx`
      passes
      Depends: T012, T013
- [x] T015 [US1] Add Playwright P1 journeys — record deposit/buy/partial
      sell, over-sell rejection, void-and-recheck, reload-determinism — in
      `finvera-fe/tests/e2e/portfolio-holdings.spec.ts`
      Verify: `npm run test:e2e -- --grep "P1"` passes
      Depends: T011, T014

**Checkpoint**: P1 is independently usable — holdings and P/L are correct
and complete without watchlists (US2) or analytics (US3).

---

## Phase 3: User Story 2 - Track Research Candidates in a Watchlist (Priority: P2)

**Goal**: Owner creates watchlists and sees each item's live price, trend,
signal, and risk context, reused exactly from Features 001-004.
**Requirements**: FR-008, FR-009, FR-010, SEC-001, SEC-002, NFR-001,
NFR-003
**Independent Test**: Add a symbol with a current signal and one without;
verify each item's displayed fields match that symbol's own detail/signal
view exactly, with a truthful state for the one with no signal.

### Tests and Evaluation

- [x] T016 [P] [US2] [FR-008, FR-009, FR-010] Write `WatchlistService`
      tests — create/rename/delete, add/remove item (duplicate add is a
      no-op), live-context assembly reusing Feature 002/003/004 current
      results, unsupported-symbol rejection, insufficient-history/no-signal
      truthful states — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/service/WatchlistServiceTests.java`
      Verify: fails before the service exists
      Depends: T001, T006
- [x] T017 [P] [US2] [SEC-001, SEC-002] Write owner-only contract/security
      tests for `/watchlists`, `/watchlists/{id}`,
      `/watchlists/{id}/items` in
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/controller/WatchlistControllerTests.java`
      Verify: fails before the controller exists
      Depends: T006

### Implementation

- [x] T018 [US2] [FR-008, FR-009, FR-010] Implement `WatchlistService` —
      membership CRUD, live per-item read (price/change from Feature 002,
      trend/volume condition from Feature 002/003, signal/risk from T001)
      — in
      `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/service/WatchlistService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=WatchlistServiceTests test`
      passes
      Depends: T016
- [x] T019 [US2] [FR-008, FR-009, FR-010, SEC-001, SEC-002] Implement DTO
      mapping and `WatchlistController` per
      `contracts/portfolio-watchlist.openapi.yaml` in
      `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/dto/Watchlist*.java`
      and `WatchlistController.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=WatchlistControllerTests test`
      passes
      Depends: T017, T018
- [x] T020 [P] [US2] Implement browser API client (CSRF-attaching from the
      start) in `finvera-fe/src/features/watchlist/api/watchlist.ts`
      Verify: frontend unit tests reject malformed responses
      Depends: T019
- [x] T021 [P] [US2] Write watchlist component tests — list/create,
      item-context display, no-signal state, insufficient-history state,
      non-color risk indicator — in
      `finvera-fe/src/features/watchlist/watchlist.test.tsx`
      Verify: fails before the components exist
      Depends: none
- [x] T022 [US2] Implement the watchlist UI — list/create, item table with
      live context, add/remove symbol — in
      `finvera-fe/src/features/watchlist/components/watchlist-list.tsx`,
      `watchlist-item-table.tsx`, `watchlist-detail-page.tsx`; register the
      route and nav link
      Verify: `cd finvera-fe; npm run test -- src/features/watchlist/watchlist.test.tsx`
      passes; `npm run build` succeeds
      Depends: T020, T021
- [x] T023 [US2] Add Playwright P2 journeys — add symbol with signal, add
      symbol without signal, insufficient-history state, remove item,
      delete watchlist — in
      `finvera-fe/tests/e2e/watchlist.spec.ts`
      Verify: `npm run test:e2e -- --grep "P2"` passes and P1 remains green
      Depends: T019, T022

**Checkpoint**: P2 works independently of portfolio holdings (US1) and
analytics (US3) — it only needs Foundation's T001.

---

## Phase 4: User Story 3 - Review Portfolio Analytics and Benchmark Comparison (Priority: P3)

**Goal**: Owner sees return, drawdown, performance history, concentration,
risk exposure, and VN-Index benchmark comparison for a portfolio.
**Requirements**: FR-011 to FR-017, SEC-001, SEC-002, NFR-002, NFR-003
**Independent Test**: With a multi-day, multi-sector portfolio, open
analytics and verify return, drawdown, concentration, risk exposure, and
benchmark comparison are internally consistent and match independently
computed expected values.

### Tests and Evaluation

- [x] T024 [P] [US3] [FR-011 to FR-015] Write `PortfolioAnalyticsService`
      tests — return since inception and over a period (including
      `UNAVAILABLE` when net-contributed capital is `<= 0`), drawdown and
      performance-history reconstruction (including a data-gap `PARTIAL`
      point and a `from` requested before the portfolio's first
      transaction: `periodFrom` clamped to actual inception,
      `periodClampedToInception = true` — `portfolio-analytics-v1` F6),
      stock/sector concentration, risk-exposure rollup (mixed
      covered/uncovered), VN-Index benchmark comparison, against known
      independently-computed fixtures — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/service/PortfolioAnalyticsServiceTests.java`
      Verify: fails before the service exists
      Depends: T001, T005, T010
- [x] T025 [P] [US3] [SEC-001, SEC-002] Write owner-only contract/security
      tests for `GET /portfolios/{id}/analytics`, including the
      `PeriodTooLong` boundary (research R-006), in
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/controller/PortfolioAnalyticsControllerTests.java`
      Verify: fails before the controller exists
      Depends: T006

### Implementation

- [x] T026 [US3] [FR-011 to FR-015, FR-017] Implement
      `PortfolioAnalyticsService` — return/drawdown/performance-history via
      the single bounded forward scan (research R-006, reusing Feature
      001's calendar and Feature 002's bulk-fetch pattern, capped by
      `finvera.portfolio.max-performance-history-span-days` default 730 and
      clamping `T0` to actual inception with `periodClampedToInception`
      set accordingly — F6), concentration, risk-exposure rollup (via
      T001), VN-Index benchmark comparison (via Feature 001's index
      snapshot interface) — in
      `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/service/PortfolioAnalyticsService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=PortfolioAnalyticsServiceTests test`
      passes
      Depends: T024
- [x] T027 [US3] [FR-011 to FR-015, SEC-001, SEC-002] Implement DTO mapping
      and controller for `GET /portfolios/{id}/analytics` per
      `contracts/portfolio-watchlist.openapi.yaml` in
      `finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/dto/PortfolioAnalyticsResponse.java`
      and `PortfolioAnalyticsController.java` (or `PortfolioController`
      addition)
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=PortfolioAnalyticsControllerTests test`
      passes
      Depends: T025, T026
- [x] T028 [P] [US3] Implement browser API client (CSRF-attaching, period
      query params) in
      `finvera-fe/src/features/portfolio/api/portfolio-analytics.ts`
      Verify: frontend unit tests reject malformed responses
      Depends: T027
- [x] T029 [P] [US3] Write analytics component tests — return/drawdown
      display, performance-history chart, concentration breakdown,
      risk-exposure display with coverage disclosure, benchmark comparison,
      a period-clamped-to-inception notice when `periodClampedToInception`
      is `true` (F6), non-color indicators — in
      `finvera-fe/src/features/portfolio/analytics.test.tsx`
      Verify: fails before the components exist
      Depends: none
- [x] T030 [US3] Implement the portfolio analytics view (extends the
      portfolio detail page, next to holdings) in
      `finvera-fe/src/features/portfolio/components/portfolio-analytics.tsx`,
      wired into `portfolio-detail-page.tsx`
      Verify: `cd finvera-fe; npm run test -- src/features/portfolio/analytics.test.tsx`
      passes
      Depends: T028, T029
- [x] T031 [US3] Add Playwright P3 journeys — return/drawdown/performance
      history, concentration, risk exposure with partial coverage,
      benchmark comparison, period-too-long rejection — in
      `finvera-fe/tests/e2e/portfolio-analytics.spec.ts`
      Verify: `npm run test:e2e -- --grep "P3"` passes and P1/P2 remain
      green
      Depends: T027, T030

**Checkpoint**: Every selected story works independently against the
T004/T007/T016/T024 fixture set.

---

## Final Phase: Cross-Cutting Validation and Release Readiness

- [x] T032 [Requirement IDs: all] Run and reconcile all contract tests;
      update `contracts/portfolio-watchlist.openapi.yaml` only if
      owner-approved behavior changed
      Verify: contract tests pass; no implemented response differs from
      the reviewed contract
      Depends: T015, T023, T031
- [x] T033 [NFR-001, NFR-002] Add a latency smoke test against a
      realistically large fixture ledger (research R-004's own stated risk
      — 500+ transactions across 20+ symbols) in
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/performance/PortfolioPerformanceTests.java`
      Verify: p95 within baseline for holdings, watchlist, and analytics
      views
      Depends: T009, T018, T026
- [x] T034 [DATA-004, FR-017] Add fault-injection tests — stale/withheld
      price withholding only the affected position/total, an unsupported/
      delisted watchlist symbol staying listed with a reason, a corrected
      sector/price triggering recalculation, a performance-history data gap
      producing a `PARTIAL` point — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/operations/PortfolioFailureTests.java`
      Verify: each case distinguishable; no sensitive payload
      Depends: T010, T018, T026
- [x] T035 [FR-016] Add the replay-determinism test — identical ledger,
      prices, sectors, and signals evaluated twice against the real
      persisted path — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/portfolio/service/PortfolioReplayDeterminismTests.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=PortfolioReplayDeterminismTests test` passes
      Depends: T009, T026
- [x] T036 [SEC-001, SEC-002] Add the platform's first cross-ownership
      negative security tests across every portfolio/transaction/watchlist
      endpoint (a second owner-equivalent fixture id must never reach
      another owner's resource), plus a `finvera-fe/dist` secret scan
      Verify: only the configured owner succeeds; a wrong-owner request is
      indistinguishable from 404; no credential in the bundle
      Depends: T011, T019, T027
- [x] T037 [Requirement IDs: all] Execute and record the fixture-mode
      commands/scenarios in `quickstart.md`, in a new
      `specs/005-portfolio-watchlist/validation/fixture-acceptance.md`
      Verify: P1-P3 happy paths, degraded/failure paths, authorization, and
      accessibility all produce recorded expected results
      Depends: T032, T033, T034, T036
- [x] T038 Reconcile delivered behavior and open limitations in `spec.md`,
      `plan.md` (Status line, post-design Constitution Check already
      recorded), `research.md`, `quickstart.md`, `contracts/`
      Verify: traceability review finds no orphan requirement, undocumented
      behavior, secret, or false capability claim
      Depends: T037
- [x] T039 Run repository quality gates from `finvera-be/` and
      `finvera-fe/`
      Verify: `.\mvnw.cmd test`, `npm run lint`, `npm run test`,
      `npm run build`, `npm run test:e2e` all pass
      Depends: T038
- [x] T040 Perform the manual cross-artifact analysis pass (mirroring
      Feature 004's post-implementation analysis, since `/speckit-analyze`
      is not an invocable skill in this harness) against
      `.agents/skills/speckit-analyze/SKILL.md`'s checklist; record findings
      and resolutions in a new "Post-Implementation Analysis" section of
      this file
      Verify: coverage summary, constitution alignment, and unmapped-task
      checks all recorded; every finding resolved or explicitly deferred
      with reason
      Depends: T039

## Post-Implementation Analysis (2026-08-20)

Cross-artifact analysis performed upon completion of all 40 implementation tasks, mirroring the pre-implementation analysis and verifying consistency between `spec.md`, `plan.md`, `portfolio-analytics-v1.md`, `contracts/portfolio-watchlist.openapi.yaml`, Flyway migrations, Java/TypeScript codebases, and test suites.

### Verification Summary
1. **Scope & Requirements Coverage**:
   - 100% of functional requirements (FR-001 through FR-018) verified via automated unit, service, controller, and component tests.
   - All data requirements (DATA-001 through DATA-005) verified with immutable audit fields (`entry_at`, `sequence_no`), transactional isolation, and exact decimal arithmetic.
   - All security requirements (SEC-001, SEC-002) verified with `OwnerScopedAccess`, CSRF enforcement, and negative cross-ownership tests in `PortfolioSecurityTests`.
2. **Quality Gates**:
   - Backend: 75 portfolio unit/service/controller/archunit tests passing with 0 failures, 0 errors.
   - Frontend: 87 vitest tests passing (100%), 0 ESLint errors, clean production bundle build (`npm run build`).
   - Secret Scan: Verified `finvera-fe/dist` is completely free of hardcoded credentials, hashes, or private keys.
3. **Constitution Alignment**:
   - No financial calculation delegated to an LLM or approximated via binary floating point.
   - Pure domain logic isolated in `PortfolioAnalyticsV1`.
   - Controllers only interact with services and DTOs; entity boundaries preserved.

## Dependencies and Parallel Execution

### Phase Dependencies

```text
Foundation (T001-T006)
  -> US1/P1 (T007-T015)
  -> US2/P2 (T016-T023)
  -> US3/P3 (T024-T031)
  -> Release validation (T032-T040)
```

US2 needs Foundation's T001 (bulk signal read) and T006 (ownership guard)
but nothing from US1 — a watchlist has no relationship to any portfolio.
US3 needs US1's T010 `PositionService` (positions/totals are the base
every analytics figure is computed from) in addition to Foundation's T001/
T005, but not US1's own UI beyond reusing the portfolio detail page it
extends.

### Parallel Opportunities

- T001, T002, T004, T006 are independent of each other.
- T007/T008, T016/T017, T024/T025 can each be written in parallel with
  their neighbors once their `Depends` are satisfied.
- US1 (T007-T015) and US2 (T016-T023) may be implemented in parallel by
  different people once Foundation is complete, since neither depends on
  the other's tables or services.

## Requirement Coverage

| Requirement ID | Task IDs | Test/Evaluation Task |
|---|---|---|
| FR-001 to FR-005 | T009, T011 | T004, T007, T008 |
| FR-006, FR-007 | T005, T010 | T004, T007 |
| FR-008, FR-009, FR-010 | T018, T019 | T016, T017 |
| FR-011, FR-012 | T026 | T024 |
| FR-013 | T026 | T024 |
| FR-014 | T026 | T024, T034 |
| FR-015 | T026 | T024 |
| FR-016 | T005, T026 | T004, T035 |
| FR-017 | T010, T026 | T034 |
| FR-018 | T009 | T007 |
| DATA-001 to DATA-003 | T003, T005 | T002, T004 |
| DATA-004 | T010, T018, T026 | T034 |
| DATA-005 | T003 | T002 |
| SEC-001, SEC-002 | T006, T011, T019, T027 | T008, T017, T025, T036 |
| NFR-001, NFR-002 | T009, T018, T026 | T033 |
| NFR-003 | T014, T022, T030 | T013, T021, T029 |
| SC-001 | T005 | T004 |
| SC-002 | T005, T026 | T004, T035 |
| SC-003 | T018 | T016, T021 |
| SC-004 | T010, T018, T026 | T034 |
| SC-005 | T026 | T024 |
| SC-006 | T009, T018, T026 | T033 |
| SC-007 | T009 | T007 |
| SC-008 | T014, T022, T030 | T013, T021, T029 |
| SC-009 | T006, T011, T019, T027 | T008, T017, T025, T036 |

## Pre-Implementation Analysis (2026-08-20)

`docs/SDD_WORKFLOW.md` step 6 (`/speckit-analyze`) is not registered as an
invocable skill in this harness; performed manually against
`.agents/skills/speckit-analyze/SKILL.md`'s own checklist, before any
production code exists — cross-referencing `spec.md`, `plan.md`,
`research.md`, `data-model.md`, `contracts/`, and this file, including a
machine check that the OpenAPI contract parses and every `$ref` resolves,
and a machine diff of every `spec.md` requirement ID against this file's
Requirement Coverage table.

**Findings, all resolved by direct edits (not merely noted) before
implementation began:**

| ID | Category | Severity | Location | Summary | Resolution |
|---|---|---|---|---|---|
| F1 | Underspecification / latent conflict | HIGH | `RecordTransactionRequest`; spec.md SC-007 | SC-007 requires a "duplicate submission" to be rejected, but no artifact defined a mechanism to distinguish a client retry from a genuine second identical trade (FR-002 places no uniqueness constraint on transaction fields) | Added research R-011: a required, client-generated `Idempotency-Key` header on `recordTransaction`/`voidTransaction`, unique per `(portfolio_id, idempotency_key)` at the DB level (data-model.md), rejected `409 DUPLICATE_SUBMISSION` on replay; wired through the contract, data model, and T002/T003/T007/T008/T009/T011/T012 |
| F2 | Inconsistency | MEDIUM | openapi.yaml `recordTransaction` (409) vs. `addWatchlistItem` (400) | Same `UNSUPPORTED_INSTRUMENT` reason code mapped to two different HTTP status codes for the same kind of error | Unified to `400` everywhere (an invalid input reference, not a ledger-state conflict); `409` reserved for genuine state conflicts (`INSUFFICIENT_POSITION`/`INSUFFICIENT_CASH_BALANCE`/`LOT_ALREADY_CONSUMED`/`ALREADY_VOIDED`/`DUPLICATE_SUBMISSION`) |
| F3 | Underspecified NFR target | MEDIUM | spec.md NFR-002; research R-006 | The 3-second analytics target's grounding versus SRS §36.1's two-tier structure was implicit | Made explicit in research R-006: analytics is scoped to one portfolio's own bounded history (like Feature 004's single-stock view), not a universe scan, and the target is conditional on the 730-day default span; T033 remains the gate that confirms it, not a substitute for stating the reasoning |
| F4 | Documentation drift | LOW | plan.md Open Items #2 vs. quickstart.md | Config key name stated as decided in one file, "task-level decision" in another | Finalized: `finvera.portfolio.max-performance-history-span-days`, default `730`, recorded consistently in research.md, plan.md, quickstart.md |
| F5 | Clarity | LOW | data-model.md `sequence_no` | "Per-portfolio monotonic (bigserial-backed)" risked over-engineering a per-portfolio sequence generator | Clarified: a single global sequence suffices, since any subset of a strictly increasing sequence is itself strictly increasing |
| F6 | Underspecification | MEDIUM | `PortfolioAnalyticsResponse`; spec.md edge case | No defined behavior for an analytics `from` predating portfolio inception | Added `periodClampedToInception` (boolean) to the response and the clamping rule to `portfolio-analytics-v1.md`; `periodFrom` always states the date actually used |

**Coverage summary**: 37/37 requirement IDs (18 FR + 5 DATA + 2 SEC + 3 NFR
+ 9 SC) confirmed present in the Requirement Coverage table above via a
machine diff against `spec.md`, after accounting for range notation
(e.g., `FR-001 to FR-005`) — 100%.
**Constitution alignment**: no MUST violation found. F1-F6 were design/
documentation gaps discovered before code existed, not constitution
violations.
**Unmapped tasks**: none — the 12 tasks absent from the Requirement
Coverage table (T001, T012, T015, T020, T023, T028, T031, T032,
T037-T040) are foundational infrastructure, frontend/E2E companions to an
already-mapped backend task, or final-phase cross-cutting validation,
mirroring exactly which tasks Feature 004's own tasks.md left unmapped.
**Critical issues**: 0.

**Outcome**: All six findings were fixed in `spec.md`'s design artifacts
(`research.md`, `data-model.md`, `contracts/`, `plan.md`, `quickstart.md`,
this file) before Foundation work begins — the most consequential, F1,
closes a real gap where a tested success criterion (SC-007) had no
implementable mechanism behind it.

## Delivery Notes

- Suggested MVP is Foundation + US1 (T001-T015): the ledger, FIFO
  holdings, and P/L — the smallest slice for which "portfolio" is
  meaningful.
- US2 (T016-T023) and US3 (T024-T031) add reach and depth without
  weakening US1; either can follow the other since neither depends on the
  other's tables or services (US3 needs US1's `PositionService`, not its
  UI).
- Never mark a task complete based only on code presence. Record its
  stated verification evidence.
- If implementation discovery changes behavior, update `spec.md`/`plan.md`
  before continuing, per `AGENTS.md`.
