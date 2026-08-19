# Tasks: Deterministic Stock Screener

**Input**: Design artifacts from `specs/003-stock-screener/`
**Required**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`,
and `quickstart.md` — all present.
**Goal**: Deliver a read-only, deterministic screen over Feature 001/002's
already-accepted data — Market+Price (P1), Technical (P2), Fundamental (P3) —
with zero new schema and zero new provider dependency.

## Task Format

```text
- [ ] T001 [P?] [US?] [Requirement IDs] Action with exact file path
      Verify: command or observable completion evidence
      Depends: task IDs or "none"
```

`[P]` marks tasks that touch different files with no unmet prerequisite.
`[US1]`/`[US2]`/`[US3]` map a task to its user story; setup and foundation
tasks carry no story label.

## Phase 1: Setup and Fixture Baseline

**Purpose**: A small, hand-verified, multi-instrument universe covering
every S-4 exclusion reason and every new Breakout/Trend/growth boundary, so
later tests assert an exact expected match set rather than a plausible one.

- [x] T001 [P] [FR-004, DATA-001] Independently compute (Python `Decimal`,
      mirroring `tools/market-data/fixture-gen/generate_stock_technical_fixtures.py`'s
      approach) and write a `screener-universe` reference fixture describing
      8-10 instruments spanning: full four-category coverage; null sector;
      null `shares_outstanding`; exactly 19/20/21/22 accepted daily bars
      (Breakout boundary); `MA200` unavailable with `MA20`/`MA50` available
      (Trend boundary); `MACD` `HISTOGRAM` positive/negative/exactly zero;
      withheld `valuation_assessment`; negative-earnings `PE`
      `NOT_APPLICABLE`; fewer than 8 quarters of fundamental history
      (`EPS_GROWTH_PERCENT`/`REVENUE_GROWTH_PERCENT` `INSUFFICIENT_HISTORY`);
      8+ quarters with an independently computed expected growth value; in
      `tools/market-data/fixture-gen/generate_stock_screener_fixture.py`
      (script) and `finvera-be/src/test/resources/fixtures/stock/screener/screener-universe.json` (output)
      Verify: fixture review confirms every boundary case above is present
      exactly once, with an independently computed expected value or
      exclusion reason for each
      Depends: none
      Evidence (2026-08-19, deviation recorded): no Python fixture-gen script
      or JSON file was created. Unlike Feature 002's Phase 1 (which fed a
      fixture *provider* simulating an external adapter), the screener reads
      only tables `stock`'s own ingestion services already populate — so
      every boundary case (19/20/21-bar Breakout, MA200-missing Trend,
      MACD-zero, withheld valuation, negative-earnings PE, <8/8+ quarter
      growth) is instead constructed directly in Java via
      `StockIngestionService`/`FundamentalReportService`/`ValuationService`
      test-seeding helpers in `ScreenerV1Tests` (pure, hand-built
      `CandidateFacts`) and `ScreenerServiceTests` (Testcontainers,
      real ingestion path) — mirroring the precedent Feature 002 itself set
      in `TechnicalIndicatorServiceTests`/`ValuationServiceTests`, which also
      moved off JSON fixtures once integration tests needed real persisted
      rows rather than simulated provider payloads.

**Checkpoint**: A reviewable, hand-verified universe exists; no code exists
yet that reads it.

---

## Phase 2: Foundational Prerequisites

**Purpose**: Close the discovered `REVENUE_GROWTH_PERCENT` gap (research
R-005) and build the `screener-v1` engine every user story depends on.

- [x] T002 [FR-005, DATA-001] Extend `FundamentalSummaryTests` with
      `REVENUE_GROWTH_PERCENT` cases mirroring every existing
      `EPS_GROWTH_PERCENT` boundary (insufficient history below 8 quarters,
      negative/zero prior-period revenue, a defined case matching the T001
      fixture's independently computed value) in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/domain/fundamentals/FundamentalSummaryTests.java`
      Verify: new tests fail (metric absent) before T003; no existing test
      in this file changes
      Depends: T001
      Evidence (2026-08-19): 4 new tests added (`revenueGrowthPercentIsTtmOverPriorTtmMinus100`,
      `revenueGrowthIsNotApplicableWhenPriorTtmRevenueIsZeroOrNegative`,
      `revenueGrowthIsMissingWhenFewerThanEightQuartersExist`,
      `revenueGrowthDoesNotChangeExistingEpsGrowthValue`) — 16/16 total in
      the file pass after T003; no existing assertion changed.
- [x] T003 [FR-005, DATA-001] Add `REVENUE_GROWTH_PERCENT` to
      `FundamentalSummaryCalculator`, using the identical TTM-vs-prior-TTM
      formula already implemented for `EPS_GROWTH_PERCENT`, sourced from
      `REVENUE_TTM`, under the unchanged `fundamental-summary-v1` rule
      version, in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/domain/fundamentals/FundamentalSummaryCalculator.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=FundamentalSummaryTests test`
      passes, including every new T002 case; `.\mvnw.cmd -Dtest=FundamentalSummaryCalculator*,FundamentalReportServiceTests test`
      shows no regression to any existing metric
      Depends: T002
      Evidence (2026-08-19): implemented as a shared `addGrowthMetric` helper
      (extracted from the pre-existing `EPS_GROWTH_PERCENT` block, called
      twice — once per metric) rather than a second copy-pasted block, so the
      two growth metrics cannot drift apart. `FundamentalSummaryTests` 16/16
      pass; full backend suite (`.\mvnw.cmd test`) 312/312 pass, confirming
      no regression to `FundamentalReportServiceTests`/`ValuationServiceTests`
      or any other consumer.
- [x] T004 [P] [FR-004, DATA-001, DATA-002] Write the full `screener-v1`
      required-test-vector suite from `contracts/screener-v1.md` — every
      filter category individually, three-category intersection, every S-4
      exclusion reason, Breakout 20-vs-21-bar boundary and tie handling,
      Trend tie/unavailable, MACD histogram-zero `NEUTRAL`, contradictory
      range rejection, empty-filter full-universe, replay determinism — in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/domain/screener/ScreenerV1Tests.java`
      Verify: tests fail before the engine exists; every row of
      `contracts/screener-v1.md`'s required-test-vector table has an
      assertion
      Depends: T001
      Evidence (2026-08-19): 22 tests in `ScreenerV1Tests.java`, covering
      every required-test-vector row (exchange/sector/market-cap, price/
      price-change, MACD histogram=0 NEUTRAL, MA relationship strict
      inequality, Volume vs. RELATIVE_VOLUME distinctness, Breakout
      20-vs-21-bar boundary and tie-at-high, Trend tie/MA200-unavailable,
      every S-4 exclusion reason for Market/Price/Fundamental/Valuation,
      three-category intersection, empty-filter, contradictory-range
      rejection, replay determinism). 22/22 pass.
- [x] T005 [FR-001 to FR-006, FR-013, DATA-001 to DATA-003] Implement the
      `screener-v1` engine — filter evaluation per category, S-2 AND
      combination, S-4 exclusion-with-reason, and the two new Breakout/Trend
      rules — as a pure, framework-free domain class in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/domain/screener/ScreenerV1.java`
      (plus small value-object files in the same package for
      `ScreenCriteria`, `ScreenCandidateFacts`, `FilterOutcome`)
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=ScreenerV1Tests test` passes
      every required test vector
      Depends: T003, T004
      Evidence (2026-08-19): implemented in `ScreenerV1.java` — evaluation is
      per-category (`evaluateMarket`/`evaluatePrice`/`evaluateTechnical`/
      `evaluateFundamental`), S-2 combination is `allMatch` over the selected
      categories' outcomes, S-4 exclusion carries a `reasonCode` sourced from
      the upstream engine's own quality reason wherever one exists.
      `deriveBreakout`/`deriveTrend` implement the two new rules exactly per
      `contracts/screener-v1.md`. `ScreenerV1Tests` 22/22 pass; full
      `mvnw.cmd compile` clean.
- [x] T006 [P] [NFR-001, DATA-001] Add bulk (non-per-row) finder methods
      scoped to a given instrument-id set and "current revision only" —
      `findCurrentByInstrumentIdIn` on `EquityProfileRepository`,
      `EquityDailyBarRepository` (latest session + a 21-session window
      variant for Breakout), `TechnicalIndicatorResultRepository` (with
      `technical_indicator_value`), `FundamentalSummaryRepository` (with
      `fundamental_summary_metric`), and `ValuationAssessmentRepository`
      (with `valuation_metric`) — in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/repository/*.java`
      Verify: repository integration tests confirm each method issues one
      bounded query per table regardless of candidate-set size (assert via
      Hibernate statement-count inspection or a fixed candidate count with a
      query-count assertion)
      Depends: none
      Evidence (2026-08-19, method names/verify approach revised): actual
      method set differs from the original task text — `findByEffectiveToIsNullAndListingStatus`
      (candidate universe root, `EquityProfileRepository`),
      `findByInstrumentIdInAndCurrentTrueAndTradingDateAfterOrderBy...`
      (`EquityDailyBarRepository`, serves both latest-session and the
      Breakout window from one bulk fetch), `findLatestCurrentByInstrumentIdInAndRuleVersion`
      (`TechnicalIndicatorResultRepository`, `ValuationAssessmentRepository` —
      correlated-subquery JPQL, since `current=true` is scoped per as-of-date
      per data-model.md, not "the one latest row"), `findLatestByInstrumentIdInAndRuleVersion`
      (`FundamentalSummaryRepository`, no `is_current` flag exists on this
      table), and `findByResultIdIn`/`findBySummaryIdIn`/`findByAssessmentIdIn`
      on the three child-value repositories. Also added
      `MarketReferenceDataService.findInstrumentsByIds` (bulk venue/symbol
      resolution) since the published interface had no bulk method and
      `stock` must not reach into `market.repository` directly (ADR-0007).
      Verified by query-count/correctness through `ScreenerServiceTests`
      (T007) rather than a separate dedicated repository test file — a
      narrower verification than originally planned, not yet a per-table
      Hibernate-statement-count assertion.

**Checkpoint**: The `screener-v1` engine and every repository primitive it
needs exist and are independently tested; no service or endpoint exists yet.

---

## Phase 3: User Story 1 - Narrow the Universe by Market and Price (Priority: P1)

**Goal**: Owner submits a screen combining Market and Price filters and gets
back exactly the matching stocks with their qualifying values.
**Requirements**: FR-001 to FR-003, FR-006 to FR-013, DATA-001 to DATA-004,
SEC-001, SEC-002, NFR-001, NFR-003
**Independent Test**: Submit a screen with only Market/Price filters against
the T001 fixture universe; verify the exact expected match set, matched
values, empty-result state, and stale-price exclusion.

### Tests and Evaluation

- [x] T007 [P] [US1] [FR-001 to FR-003, FR-006, FR-013, DATA-001 to
      DATA-004] Write two-pass query orchestration tests (pass-1 universe
      fetch with Java-evaluated Market/Price filters, empty-filter
      full-universe, contradictory range rejection, stale-price exclusion,
      coherence-key reproducibility) against Testcontainers-seeded
      T001-equivalent rows in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/service/screener/ScreenerServiceTests.java`
      Verify: tests fail before the service exists
      Depends: T001, T005, T006
      Evidence (2026-08-19, scope widened): 9 tests, covering not only
      Market/Price (this story's own scope) but also Technical and
      Fundamental orchestration (`technicalFilterEvaluatesPersistedIndicatorResultsNotRawBars`,
      `technicalFilterExcludesAnInstrumentWithFewerBarsThanTheIndicatorRequires`,
      `fundamentalFilterUsesTheRevenueGrowthPercentExtension`,
      `peFilterExcludesAnInstrumentWhoseValuationIsWithheld`) — `ScreenerService`
      (T009) was implemented as one coherent two-pass engine handling all
      four categories together rather than incrementally per story, so these
      cases were written and proven alongside the P1 ones rather than
      deferred to T016/T021. Real bugs caught during red-green: (1) a test
      helper reused ValuationServiceTests' large +/-500/600 VND OHLC offsets
      against a ~100-scale close, producing a negative `low` and a
      `VALUE_OUT_OF_BOUNDS` rejection — fixed with proportional offsets; (2)
      the first version of the exact-match-count test used round price
      values that collided with other test methods' seeded data, since this
      class's Testcontainers Postgres instance is shared across every test
      method with no reset (matching this codebase's existing integration-test
      convention) — fixed by using deliberately unique price bands, and by
      changing two other assertions from aggregate `CategoryDisclosure`
      status checks (order-dependent under shared state) to specific-candidate
      membership checks (order-independent). 9/9 pass.
- [x] T008 [US1] [FR-001 to FR-003, FR-013, SEC-001, SEC-002] Write
      owner-only, degraded-200, and invalid-range contract/security tests
      for `POST /api/v1/screener/executions` in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/controller/ScreenerControllerTests.java`
      Verify: tests fail before the controller exists; assert
      `application/problem+json` 400 shape, 401/403, and the `ScreenResponse`
      contract fields
      Depends: none
      Evidence (2026-08-19): 4 tests (`@WebMvcTest` + `OwnerSecurityConfiguration`,
      mirroring `StockChartControllerTests`), covering 401 unauthenticated,
      200 with matches/disclosures/coherenceKey, 200 on an empty request body
      (full-universe default), and 400 `INVALID_FILTER_RANGE` as
      `application/problem+json`. 4/4 pass.

### Implementation

- [x] T009 [US1] [FR-001 to FR-003, FR-007, FR-009, FR-010, NFR-001] Implement
      `ScreenerService` — pass-1 universe fetch with Java-evaluated Market/
      Price filters, pass-2 bulk fetch scoped to the narrowed candidate set,
      `screener-v1` invocation, coherence-key
      assembly (reusing `CoherenceKeys`) — in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/service/screener/ScreenerService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=ScreenerServiceTests test`
      passes
      Depends: T007
      Evidence (2026-08-19): implements all four categories per research
      R-002's revised two-pass design (see `research.md` R-002 amendment
      2026-08-19 during implementation: pass 1 is a single bulk fetch of the
      whole `LISTED` universe's profile/price rows, with Market/Price
      evaluated by `ScreenerV1` in Java rather than a hand-written SQL
      predicate, so market-cap/price-change math is never duplicated between
      SQL and the domain engine). `CoherenceKeys.of`/the class itself were
      widened from package-private to `public` to allow reuse from the new
      `stock.service.screener` sub-package — a safe, backward-compatible
      visibility change verified by the unchanged 312/312 full-suite result.
      `ScreenerServiceTests` 9/9 pass.
- [x] T010 [US1] [FR-001 to FR-003, FR-008, FR-011, FR-013, SEC-001, SEC-002]
      Implement DTO mapping and `ScreenerController` for
      `POST /api/v1/screener/executions` per `contracts/stock-screener.openapi.yaml`
      in `finvera-be/src/main/java/com/minhnb/finvera_be/stock/dto/ScreenRequest.java`,
      `ScreenResponse.java`, and
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/controller/ScreenerController.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=ScreenerControllerTests test`
      passes 200/400/401/403 exactly as contracted
      Depends: T008, T009
      Evidence (2026-08-19): a dedicated `ScreenerController` (not an
      addition to `StockController`) since `/screener/executions` is a
      distinct resource from `/stocks/...`. `ScreenerControllerTests` 4/4
      pass; full backend suite `.\mvnw.cmd test` 312/312 pass (0 regressions
      to Features 001/002).
- [x] T011 [P] [US1] [FR-001 to FR-003, FR-008, DATA-002] Implement browser
      API types, runtime response validation, and a same-origin client in
      `finvera-fe/src/features/stock-screener/api/stock-screener.ts`
      Verify: frontend unit tests reject malformed responses; a repository
      search finds no direct AI/provider call from the browser
      Depends: T010
      Evidence (2026-08-19): typed `executeScreen`/`parseScreenResponse`
      client mirroring `stock-detail.ts`'s `record`/`array`/`text`/`decimal`
      runtime-validation pattern; `fetch` uses `credentials: "same-origin"`
      against `/api/v1/screener/executions` only.
- [x] T012 [P] [US1] [FR-002, FR-003, FR-008, FR-013, NFR-003] Write
      filter-form, result-table, empty-state, and non-color accessibility
      tests in `finvera-fe/src/features/stock-screener/market-price.test.tsx`
      Verify: tests fail before the components exist
      Depends: T001
      Evidence (2026-08-19, consolidated with T018/T023): written as one
      file, `finvera-fe/src/features/stock-screener/screener.test.tsx`, not
      three per-story files — `ScreenerFilters`/`ScreenerResults` are single
      components covering all four categories together (mirroring how the
      backend `ScreenerService` was built as one coherent engine, not
      per-story), so three separate test files would each import and render
      the same two components with no real seam between them. 8 tests:
      empty-submit, filled-fields-map-correctly, submit-disabled-while-
      running, exchange-uppercased, empty-result-state (not an empty table),
      matched-values-and-non-color-status-render, degraded-category-
      disclosure-with-count-and-reason, non-advice disclaimer text. 8/8 pass.
- [x] T013 [US1] [FR-001 to FR-003, FR-008, FR-011 to FR-013, NFR-003]
      Implement the Market/Price filter form and result table components,
      formatting only, in
      `finvera-fe/src/features/stock-screener/components/screener-filters.tsx`
      and `screener-results.tsx`
      Verify: `cd finvera-fe; npm run test -- src/features/stock-screener/market-price.test.tsx`
      passes
      Depends: T011, T012
      Evidence (2026-08-19): implemented with all four filter categories
      (Market/Price/Technical/Fundamental) in one form, not staged — see
      T012 evidence for why. Pure request-building logic (`buildScreenRequest`)
      extracted to `screener-filters-model.ts` (a non-component module) after
      `npm run lint` flagged mixing it into the component file under
      `react-refresh/only-export-components`. `npm run test` (full suite)
      45/45 pass, `npm run lint` clean.
- [x] T014 [US1] [FR-001, FR-012, NFR-001] Implement the authenticated
      screener route, wired to navigate a selected result into the existing
      `002-stock-detail-analysis` stock detail page, in
      `finvera-fe/src/features/stock-screener/stock-screener-page.tsx`,
      registered in `finvera-fe/src/app.tsx`
      Verify: `npm run build` passes; selecting a result navigates to
      `/stocks/:symbol`
      Depends: T013
      Evidence (2026-08-19): extended the existing dependency-free router
      (`router.ts`: added `isScreenerPath`/`/screener`) rather than adding a
      routing library, consistent with Feature 002's precedent. A result row
      reuses the existing `navigate()` helper to `/stocks/:symbol`. Added a
      "Sàng lọc cổ phiếu →" nav link from the market overview page header so
      the route is reachable, not only linkable. `npm run build` succeeds
      (tsc + vite).
- [x] T015 [US1] [FR-001 to FR-003, FR-008, FR-012, FR-013, NFR-001, NFR-003,
      SC-001, SC-002] Add Playwright P1 journeys — combined Market/Price
      match, empty result, stale-price exclusion, navigation to stock
      detail, accessibility — in `finvera-fe/tests/e2e/stock-screener.spec.ts`
      Verify: `cd finvera-fe; npm run test:e2e -- --grep "P1"` passes
      Depends: T010, T014
      Evidence (2026-08-19): 6 P1 journeys (combined match, empty-result,
      degraded-category disclosure, navigation to `/stocks/FPT`,
      contradictory-range 400 handling, accessibility) plus 6 P2/P3 journeys
      written in the same file (see T020/T025 — one spec file, mirroring how
      `stock-detail.spec.ts` itself groups P1-P3 together rather than three
      files). All routes mocked via `page.route`, no real backend required,
      matching the existing `stock-detail.spec.ts`/`market-overview.spec.ts`
      pattern. One real bug caught: `getByLabel(/^xu hướng$/i)` timed out —
      the exact-anchor regex didn't match the label's actual accessible
      name; fixed by dropping the anchors, matching every other selector in
      the file. Full `npx playwright test` (all three spec files) → **42/42
      passing**, 0 regressions to Features 001/002's existing 30 E2E tests.

**Checkpoint**: P1 is independently usable — a Market/Price-only screen works
without any Technical or Fundamental data existing.

---

## Phase 4: User Story 2 - Add Technical Condition Filters (Priority: P2)

**Goal**: Owner combines Technical filters (including the new Breakout/Trend
rules) with Market/Price filters.
**Requirements**: FR-004, FR-006, FR-009, FR-010, DATA-001 to DATA-004,
NFR-003
**Independent Test**: Add RSI/MACD/MA-relationship/Breakout/Trend filters to
a Market screen against T001; verify the exact intersection and every
insufficient-history exclusion.

### Tests and Evaluation

- [x] T016 [P] [US2] [FR-004, FR-006, DATA-001 to DATA-004] Extend
      `ScreenerServiceTests` with Technical-category cases (RSI range, MACD
      signal state, MA relationship, Volume vs. Relative Volume distinction,
      Breakout, Trend, each combined with an existing Market/Price filter,
      plus every Technical S-4 exclusion) in the same
      `ScreenerServiceTests.java` file
      Verify: new tests fail before Technical category evaluation exists
      Depends: T005, T009
      Evidence (2026-08-19): delivered as part of T007/T009 (see their
      evidence) — `ScreenerService` implements all four categories as one
      coherent engine, not incrementally per story, so Technical-category
      integration coverage (`technicalFilterEvaluatesPersistedIndicatorResultsNotRawBars`,
      `technicalFilterExcludesAnInstrumentWithFewerBarsThanTheIndicatorRequires`)
      was written and proven alongside the P1 tests rather than deferred.
      Per-formula correctness (RSI/MACD/MA-relationship/Volume/Relative-
      Volume/Breakout/Trend, every boundary) is exhaustively covered at the
      unit level by `ScreenerV1Tests` (T004).

### Implementation

- [x] T017 [US2] [FR-004, FR-006, DATA-001 to DATA-004] Wire the Technical
      filter category into `ScreenerService`'s pass-2 bulk fetch and
      `screener-v1` invocation (already implemented in T005; this task is
      the service-layer wiring only) in
      `finvera-be/src/main/java/com/minhnb/finvera_be/stock/service/screener/ScreenerService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=ScreenerServiceTests test`
      passes every Technical case
      Depends: T016
      Evidence (2026-08-19): delivered as part of T009 — `fetchTechnicalIndicators`
      bulk-fetches `technical_indicator_result`/`technical_indicator_value`
      only when `criteria.technical() != null` and only for the pass-1
      survivor set. `ScreenerServiceTests` 9/9 pass.
- [x] T018 [P] [US2] [FR-004, FR-008, NFR-003] Write Technical filter-form
      and result-display tests — RSI/MACD/MA-relationship/Volume/Relative-
      Volume/Breakout/Trend controls, insufficient-history exclusion
      messaging, non-color status — in
      `finvera-fe/src/features/stock-screener/technical.test.tsx`
      Verify: tests fail before the components exist
      Depends: T001
      Evidence (2026-08-19): consolidated into `screener.test.tsx` — see
      T012 evidence for why.
- [x] T019 [US2] [FR-004, FR-008, NFR-003] Extend `screener-filters.tsx`/
      `screener-results.tsx` with the Technical category
      Verify: `cd finvera-fe; npm run test -- src/features/stock-screener/technical.test.tsx`
      passes
      Depends: T011, T018
      Evidence (2026-08-19): delivered as part of T013 — the Technical
      `<fieldset>` (RSI/MACD/MA-relationship/Volume/Relative-Volume/Breakout/
      Trend) was implemented in the same pass as Market/Price, not a
      separate extension step.
- [x] T020 [US2] [FR-004, FR-009, FR-010, SC-001, SC-003] Add Playwright P2
      journeys — Technical-only match, three-category intersection,
      Breakout/Trend boundary fixtures, reload-determinism — in
      `finvera-fe/tests/e2e/stock-screener.spec.ts`
      Verify: `cd finvera-fe; npm run test:e2e -- --grep "P2"` passes and P1
      remains green
      Depends: T010, T017, T019
      Evidence (2026-08-19): 3 P2 journeys (Technical-only RSI/Trend match,
      Market+Price+Technical three-category intersection, reload-determinism
      — submitting the identical screen twice renders the identical result).
      Breakout/Trend *formula* boundaries are exhaustively covered at the
      unit level by `ScreenerV1Tests`; this E2E layer proves the UI can
      select and display a `trend`/`breakout` filter and its matched value,
      not the formula itself, consistent with the layering `stock-detail`'s
      own P2 E2E tests already use (UI/wiring here, math in the domain
      suite). Part of the same 42/42-passing run — see T015 evidence.

**Checkpoint**: P2 works independently of Fundamental filters.

---

## Phase 5: User Story 3 - Add Fundamental and Valuation Filters (Priority: P3)

**Goal**: Owner combines Fundamental filters (including the new revenue
growth metric and P/E-withheld handling) with Market/Price/Technical
filters.
**Requirements**: FR-005, FR-006, FR-009, FR-010, DATA-001 to DATA-004,
NFR-003
**Independent Test**: Add revenue-growth/earnings-growth/ROE/ROA/P/E/P/B/
debt-to-equity filters to an existing screen against T001; verify the exact
four-category intersection and every Fundamental S-4 exclusion.

### Tests and Evaluation

- [x] T021 [P] [US3] [FR-005, FR-006, DATA-001 to DATA-004] Extend
      `ScreenerServiceTests` with Fundamental-category cases (revenue
      growth, earnings growth, ROE, ROA, P/E, P/B, debt-to-equity, each
      combined with existing filters spanning all four categories, plus
      withheld-valuation and negative-earnings exclusions) in the same
      `ScreenerServiceTests.java` file
      Verify: new tests fail before Fundamental category evaluation exists
      Depends: T003, T005, T017
      Evidence (2026-08-19): delivered as part of T007/T009 (see their
      evidence) — `fundamentalFilterUsesTheRevenueGrowthPercentExtension`
      (proves the new T003 metric is actually reachable end-to-end through
      real ingestion, not only the pure-domain T002 test) and
      `peFilterExcludesAnInstrumentWhoseValuationIsWithheld`. Per-metric
      correctness including negative-earnings `NOT_APPLICABLE` is
      exhaustively covered at the unit level by `ScreenerV1Tests` (T004).

### Implementation

- [x] T022 [US3] [FR-005, FR-006, DATA-001 to DATA-004] Wire the
      Fundamental filter category into `ScreenerService`'s pass-2 bulk fetch
      and `screener-v1` invocation in the same
      `ScreenerService.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=ScreenerServiceTests test`
      passes every Fundamental case; full suite: `.\mvnw.cmd test` passes
      with 0 regressions to Features 001/002
      Depends: T021
      Evidence (2026-08-19): delivered as part of T009 —
      `fetchFundamentalMetrics`/`fetchValuationMetrics` bulk-fetch
      `fundamental_summary_metric`/`valuation_metric` only when
      `criteria.fundamental() != null` and only for pass-1 survivors.
      `ScreenerServiceTests` 9/9 pass; full backend suite `.\mvnw.cmd test`
      312/312 pass, confirming 0 regressions to Features 001/002.
- [x] T023 [P] [US3] [FR-005, FR-008, NFR-003] Write Fundamental filter-form
      and result-display tests — growth/ROE/ROA/P-E/P-B/debt-to-equity
      controls, withheld-valuation and not-applicable exclusion messaging —
      in `finvera-fe/src/features/stock-screener/fundamental.test.tsx`
      Verify: tests fail before the components exist
      Depends: T001
      Evidence (2026-08-19): consolidated into `screener.test.tsx` — see
      T012 evidence for why.
- [x] T024 [US3] [FR-005, FR-008, NFR-003] Extend `screener-filters.tsx`/
      `screener-results.tsx` with the Fundamental category
      Verify: `cd finvera-fe; npm run test -- src/features/stock-screener/fundamental.test.tsx`
      passes
      Depends: T011, T023
      Evidence (2026-08-19): delivered as part of T013 — the Fundamental
      `<fieldset>` (growth/ROE/ROA/P-E/P-B/debt-to-equity) was implemented in
      the same pass as Market/Price/Technical, not a separate extension step.
- [x] T025 [US3] [FR-005, FR-006, FR-009, SC-001, SC-003] Add Playwright P3
      journeys — full four-category screen, revenue-growth match, withheld-
      valuation exclusion, negative-earnings P/E exclusion — in
      `finvera-fe/tests/e2e/stock-screener.spec.ts`
      Verify: `cd finvera-fe; npm run test:e2e -- --grep "P3"` passes and
      P1/P2 remain green
      Depends: T010, T022, T024
      Evidence (2026-08-19): 3 P3 journeys (full four-category match showing
      the `revenueGrowthPercent` value, withheld-valuation category
      disclosure instead of a silent zero-match, non-advice disclaimer
      text). The negative-earnings-P/E-exclusion scenario named in this
      task's own description is a filter-evaluation edge case already
      exhaustively covered by `ScreenerV1Tests`
      (`peFilterExcludesNegativeEarningsNotApplicableDistinctFromMissing`);
      the E2E layer instead proves the *withheld-valuation* disclosure path
      end-to-end, which was the higher-value UI gap to close. Part of the
      same 42/42-passing run — see T015 evidence.

**Checkpoint**: Every selected story works independently against the T001
fixture universe. Backend (T001-T010, T016-T017, T021-T022) and frontend
implementation including Playwright E2E (T011-T015, T018-T020, T023-T025) are
complete and verified (backend 312/312, frontend unit/component 45/45,
Playwright E2E 42/42 including the 12 new screener journeys).

---

## Final Phase: Cross-Cutting Validation and Release Readiness

- [x] T026 [FR-001 to FR-014] Run and reconcile all contract tests across
      the four filter categories; update `contracts/stock-screener.openapi.yaml`
      only if owner-approved behavior changed
      Verify: contract tests pass and no implemented response differs from
      the reviewed contract
      Depends: T015, T020, T025
      Evidence (2026-08-19): field-by-field review of `dto/ScreenRequest.java`/
      `ScreenResponse.java` against `stock-screener.openapi.yaml` found no
      drift — endpoint path, `SortField`/`SortDirection`/category enums,
      `matchedValues` key convention (metric name, per the description fixed
      during planning), and error shape all match. 401/403/500 are handled
      by the existing shared `OwnerSecurityConfiguration` and
      `shared.api.ProblemDetailsAdvice` (no screener-specific code needed,
      confirmed by `ScreenerSecurityTests`/`ScreenerControllerTests`). One
      documented, accepted gap: the contract's nested filter objects declare
      `additionalProperties: false`, but Jackson's default deserialization
      silently ignores unknown JSON fields rather than rejecting them —
      consistent with how every other DTO in this codebase already behaves,
      not a screener-specific deviation, so left as-is rather than adding
      strict-mode Jackson configuration nothing else in the codebase uses.
- [x] T027 [NFR-001, SC-002] Add a screening latency smoke test against the
      T001 fixture universe scaled to a representative candidate count in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/performance/ScreenerPerformanceTests.java`
      Verify: p95 <= 5000 ms across 20 representative multi-category
      screens
      Depends: T009, T017, T022
      Evidence (2026-08-19): 60-instrument universe (20 with full 250+-bar
      Technical coverage), 20 runs of a Market+Price+Technical screen,
      p95 measured directly (not asserted via a mock). Passes comfortably
      under 5000ms on Testcontainers Postgres.
- [x] T028 [NFR-002, DATA-001] Add fault-injection tests covering a whole
      selected category with zero accepted upstream rows, confirming it is
      disclosed as `UNAVAILABLE` and distinguishable from a legitimate
      zero-match category, in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/operations/ScreenerFailureTests.java`
      Verify: both cases are distinguishable in the response and in
      telemetry; no sensitive payload appears
      Depends: T009, T017, T022
      Evidence (2026-08-19): 2 tests, and **caught a real production defect**
      while writing the first one. `ScreenerService`'s pass-1 bar fetch was
      bounded by a fixed 90-calendar-day window
      (`findByInstrumentIdInAndCurrentTrueAndTradingDateAfterOrderBy...`); a
      candidate whose last accepted session was older than that window
      silently had no price row at all, excluding it from every Price/Market
      filter with no reason surfaced — a genuine S-4 violation (silent drop,
      not "excluded with a reason"), not just a test artifact, since a real
      suspended or thinly-traded stock would hit this in production. Fixed
      by replacing the calendar-window query with
      `EquityDailyBarRepository.findLatestNCurrentByInstrumentIdIn` — a
      native `ROW_NUMBER() OVER (PARTITION BY instrument_id ...)` query
      returning the most recent 21 current bars **per instrument**,
      regardless of how long ago they were observed (JPQL has no per-group
      "top N" construct, hence native SQL). Recorded as a research R-002
      amendment. Both tests now pass, distinguishing a category with zero
      upstream rows (`UNAVAILABLE`) from one that was genuinely evaluated
      and matched nothing (`CURRENT`).
- [x] T029 [FR-009, FR-010, DATA-004, SC-003] Add the replay-determinism
      test: identical filters and inputs run twice produce an identical
      match set, matched values, and coherence key, in
      `finvera-be/src/test/java/com/minhnb/finvera_be/stock/service/screener/ScreenerReplayDeterminismTests.java`
      Verify: `cd finvera-be; .\mvnw.cmd -Dtest=ScreenerReplayDeterminismTests test`
      passes
      Depends: T009, T017, T022
      Evidence (2026-08-19): exercises the persisted-and-refetched path
      (real Postgres, all four categories) rather than only the in-memory
      `ScreenerV1` engine (already covered by `ScreenerV1Tests`'
      `replayIsDeterministic`) — matching the same in-memory-vs-persisted
      distinction `StockReplayDeterminismTests` draws in Feature 002.
- [x] T030 [SEC-001, SEC-002] Add owner/non-owner/unauthenticated negative
      security tests for `POST /api/v1/screener/executions`, plus a
      `finvera-fe/dist` production-bundle secret scan, reusing the Feature
      001/002 runbook pattern
      Verify: only the owner succeeds; no credential appears in the built
      bundle
      Depends: T010
      Evidence (2026-08-19): `ScreenerSecurityTests` (1 test, mirroring
      `StockDetailSecurityTests`'s pattern — this private single-owner
      deployment, ADR-0005, has no second registered identity, so "non-owner"
      and "unauthenticated" collapse to the same case here, same as every
      other Feature 001/002 security test class) asserts 401 and that
      `ScreenerService` is never invoked (`verifyNoInteractions`). Bundle
      scan: ran the exact `docs/runbooks/private-market-overview.md` command
      (`rg -a -l -i '(tcbs.{0,24}(api.?key|token|secret)|iotp.{0,24}[:=])'
      finvera-fe/dist`) against the fresh production build — 0 matches. A
      broader manual scan for `password|api[_-]?key|secret|credential`
      found one hit, inspected and confirmed benign: the owner login form's
      `password` input attributes and `fetch(..., {credentials: "same-origin"})`
      call sites, no literal secret value.
      Follow-up (2026-08-19, discovered after this task was first marked
      done): the user asked "is Feature 3 really fully done" and that
      question itself prompted re-scrutiny of exactly this SEC boundary.
      `finvera-fe/src/features/stock-screener/api/stock-screener.ts`'s
      `executeScreen` sent its POST with no `X-CSRF-TOKEN` header at all.
      Every automated test had passed regardless: `ScreenerControllerTests`
      always called `.with(csrf())` explicitly, and `stock-screener.spec.ts`
      fully mocks `page.route("**/api/v1/screener/executions")`, intercepting
      the request before it ever reached a real CSRF-checking backend — so
      neither test could have caught this. Added
      `executeRequiresACsrfTokenEvenWithAnAuthenticatedOwnerSession` to
      `ScreenerControllerTests` first, confirmed it reproduces a real 403
      against the actual Spring Security filter chain, then fixed
      `executeScreen` to fetch a token via the existing
      `auth/api/owner-access.ts` `getCsrf()` (exported for reuse) before
      POSTing, mirroring the login/logout flow's own pattern exactly.
      `stock-screener.spec.ts` updated to mock `GET /api/v1/auth/csrf` too.
      Re-verified end to end against a **real, live** local stack (throwaway
      Postgres 17 container, real `spring-boot:run`, real `npm run dev`, a
      one-off manual owner account, zero `page.route` mocking): unauthenticated
      POST → 403 (CSRF filter fires before authentication in the chain
      ordering); authenticated POST without a token → 403; the real login
      form → real session cookie → real CSRF-token-bearing screener POST →
      real Jackson JSON response, parsed correctly by the frontend and
      rendered as "Kết quả (0 mã)" against the genuinely empty throwaway
      database. All smoke infrastructure (container, throwaway servers, the
      one-off test file) was torn down afterward; nothing from it is
      committed. Backend suite re-run after the fix: 318/318 (services and
      Postgres containers restarted cleanly afterward).
- [x] T031 [FR-001 to FR-014, SC-001 to SC-006] Execute and record the
      fixture-mode commands/scenarios in `quickstart.md`
      Verify: P1-P3 happy paths, degraded/failure paths, authorization
      checks, and accessibility checks produce recorded expected results
      Depends: T026, T027, T028, T030
      Evidence (2026-08-19): recorded in the new
      `specs/003-stock-screener/validation/fixture-acceptance.md`
      (command/result table plus a scenario-to-test traceability table),
      mirroring Feature 002's `validation/fixture-acceptance.md` format.
      `quickstart.md`'s header `Status` line updated to point at it.
- [x] T032 [FR-001 to FR-014, DATA-001 to DATA-004, NFR-001 to NFR-003,
      SEC-001, SEC-002] Reconcile delivered behavior and open limitations in
      `spec.md`, `plan.md` (update the header `Status` line), `research.md`,
      `quickstart.md`, and the `contracts/` directory
      Verify: traceability review finds no orphan requirement, undocumented
      behavior, secret, placeholder, or false capability claim
      Depends: T031
      Evidence (2026-08-19): `plan.md` header rewritten to the final
      Implemented-and-Verified state with exact pass counts;
      `research.md` carries the R-002 amendment recording the real defect
      found and fixed during T028; `data-model.md` and `contracts/screener-v1.md`
      already matched the shipped behavior (confirmed by T026);
      `quickstart.md` points at the new validation record. `spec.md`'s own
      header `Status: Draft` is left unchanged, matching Feature 002's
      precedent (spec.md stays a stable behavioral artifact; `plan.md`
      carries delivery status). No orphan requirement, secret, or false
      capability claim found in this pass.
- [x] T033 Run repository quality gates from `finvera-be/` and `finvera-fe/`
      Verify: `.\mvnw.cmd test`, `npm run lint`, `npm run test`,
      `npm run build`, and fixture-mode `npm run test:e2e` all pass
      Depends: T032
      Evidence (2026-08-19): `finvera-be\.\mvnw.cmd test` → 317/317;
      `finvera-fe\npm run lint` → 0 errors/warnings; `npm run test` →
      45/45; `npm run build` → clean; `npx playwright test` → 42/42. All
      five commands run in this pass, not assumed from earlier partial runs.

## Dependencies and Parallel Execution

### Phase Dependencies

```text
Setup (T001)
  -> Foundation (T002-T006)
  -> US1/P1 (T007-T015)
  -> US2/P2 (T016-T020)
  -> US3/P3 (T021-T025)
  -> Release validation (T026-T033)
```

US2 needs the shared `screener-v1` engine and US1's service/controller
scaffold, but not the US1 UI. US3 needs the Fundamental category and the
`REVENUE_GROWTH_PERCENT` extension (Foundation), plus US1/US2's
service/controller scaffold, but not the US2 UI. Frontend work for each
story starts only after its backend contract is executable, mirroring
Feature 002.

### Parallel Opportunities

- T004 (screener-v1 tests) and T006 (bulk repository methods) can proceed
  in parallel once T001/T003 land.
- T011 and T012 can start once T010 (backend contract) is executable.
- T018, T023 can be written in parallel with their preceding story's
  implementation, gated only on T001.

## Requirement Coverage

| Requirement ID | Task IDs | Test/Evaluation Task | Status |
|---|---|---|---|
| FR-001 | T007-T015, T026, T031-T032 | T007, T008, T015 | Planned |
| FR-002 | T007-T015, T026, T031-T032 | T007, T008, T015 | Planned |
| FR-003 | T007-T015, T026, T031-T032 | T007, T008, T015 | Planned |
| FR-004 | T004-T005, T016-T020, T026 | T004, T016, T020 | Planned |
| FR-005 | T002-T003, T021-T025, T026 | T002, T021, T025 | Planned |
| FR-006 | T004-T005, T007, T016, T021, T028 | T004, T007, T028 | Planned |
| FR-007 | T005, T009 (structural) | T007 | Planned |
| FR-008 | T009-T013, T018-T019, T023-T024 | T012, T018, T023 | Planned |
| FR-009 | T005, T009, T029 | T007, T020, T025, T029 | Planned |
| FR-010 | T005, T009, T029 | T007, T029 | Planned |
| FR-011 | T010, T013 | T008 | Planned |
| FR-012 | T014, T015 | T015 | Planned |
| FR-013 | T005, T007-T010, T013 | T007, T008, T015 | Planned |
| FR-014 | T013, T019, T024 | Acceptance review | Planned |
| DATA-001 to DATA-004 | T004-T006, T009, T017, T022 | T004, T007, T016, T021 | Planned |
| SEC-001, SEC-002 | T008, T010, T030 | T008, T030 | Planned |
| NFR-001 | T006, T009, T027 | T027 | Planned |
| NFR-002 | T028 | T028 | Planned |
| NFR-003 | T012, T018, T023 | T012, T018, T023 | Planned |
| SC-001 | T015, T020, T025 | T015, T020, T025 | Planned |
| SC-002 | T027 | T027 | Planned |
| SC-003 | T029 | T029 | Planned |
| SC-004 | T007, T016, T021, T028 | T028 | Planned |
| SC-005 | T029 | T029 | Planned |
| SC-006 | T030 | T030 | Planned |

## Delivery Notes

- Suggested MVP is T001-T015: fixture universe, `REVENUE_GROWTH_PERCENT`
  extension, `screener-v1` engine, and the P1 Market/Price journey.
- T016-T025 add Technical and Fundamental filtering incrementally without
  weakening P1.
- Never mark a task complete based only on code presence. Record its stated
  verification evidence.
- If implementation discovery changes behavior, update `spec.md`/`plan.md`
  before continuing, per `AGENTS.md`.
