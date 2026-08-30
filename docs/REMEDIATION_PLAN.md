# Finvera Quality Remediation Plan

**Status**: Living document
**Opened**: 2026-08-30
**Applies to**: `finvera-be`, `finvera-fe`, `finvera-ai`, `tools/market-data`

## What this document is

A tracked backlog of defects and gaps found in the 2026-08-30 full-system
review, ordered so we can implement them one at a time. Each entry has a stable
`Q-` identifier, the evidence it was confirmed with, and the `specs/<feature>/`
directory that owns its SDD paperwork.

It is **not** a specification. Every fix still follows `docs/SDD_WORKFLOW.md`:
a fix that restores conformance to an already-approved contract is recorded as a
new `R-` decision plus a `T` task in the owning feature; a fix that changes
agreed behaviour amends `spec.md`/`plan.md`/`contracts/` first. `Q-` ids live
only here and are never referenced from production code.

## How to use it

1. Pick the next `TODO` entry in priority order.
2. Read its **SDD home** and add the `R-`/`T` records there.
3. Implement, then fill in **Verification** with the command actually run.
4. Flip **Status** and add the completion date.

### Status legend

| Status | Meaning |
|---|---|
| `TODO` | Not started |
| `WIP` | In progress |
| `DONE` | Implemented, verified, SDD records written |
| `DEFERRED` | Consciously postponed, with the reason recorded |

### Confidence legend

Severity is what it costs if it is real. Confidence is how sure we are it is.

| Confidence | Meaning |
|---|---|
| `CONFIRMED` | Reproduced against real data, a failing test, or a query result quoted below |
| `CODE-READ` | Established by reading the code and the contract; not yet reproduced |
| `LATENT` | The defect is in the code but currently unreachable; it activates on a plausible near-term change |

---

## Priority order

| Group | Theme | Entries | Why this order |
|---|---|---|---|
| **A** | Price units and charting | Q-01 | Wrong money on screen today, smallest blast radius |
| **B** | Release gate | Q-02 … Q-05 | The build is red; nothing else can be verified honestly until it is green |
| **C** | Missing-data truthfulness | Q-06 … Q-12 | Silent zeros and false `CURRENT` labels across four modules |
| **D** | Provider data expansion | Q-13 … Q-17 | Unlocks calculations that are currently permanently withheld |
| **E** | Security and AI grounding | Q-18 … Q-22 | Real, but bounded by the private single-owner deployment |
| **F** | Hygiene and performance | Q-23 … Q-29 | No user-visible incorrectness |

---

## Group A — Price units and charting

### Q-01 · Client-side price-unit heuristic — `DONE` (2026-08-30)

- **Severity**: High · **Confidence**: `CONFIRMED`
- **Where**: `finvera-fe/src/features/stock-detail/components/stock-chart.tsx`
- **SDD home**: `specs/002-stock-detail-analysis/` → `research.md` R-016, `tasks.md` T080

The chart counted bars with `close >= 1000` and then multiplied every sub-1000
value by 1000, or divided every `>=1000` value by 1000, whichever side won the
vote. That is both alternatives R-015 explicitly rejected ("normalize only in
the UI", "infer units from arbitrary price magnitude") and a client-computed
authoritative value, which `ARCHITECTURE.md` section 6 forbids.

Both providers already agree on base VND/share (see [Evidence E-1](#e-1--price-units-by-source)),
so there was nothing to compensate for. 30 of 1,430 instruments close below
1,000 VND and were charted 1000x too high whenever their window was
predominantly above 1,000 VND.

- **Verification**: stashing the fix makes 3 of 4 new guards fail and restoring
  it makes all 13 pass; `npx vitest run` 126/126, `npm run lint`, `npm run build`
  all clean.

---

## Group B — Release gate

**2026-08-30: gate is green — 643 run, 0 failures, 0 errors, BUILD SUCCESS**
(was 7 failures + 3 errors). Q-02 and Q-03 were production defects; Q-04 and
Q-05 were test debt. During Q-04 the `FixtureRuntimeBootstrapServiceTests`
failures turned out to be a real regression, not test debt: the startup
`marketRegimeReadRepair` runner shadowed the fixture bootstrap's published
assessment with a withheld LIVE-basis one (fixed by gating the runner off when
the bootstrap is enabled — specs/001 T087).

### Q-02 · `stock` module reaches into `market` persistence — `DONE` (2026-08-30)

- **Severity**: Medium · **Confidence**: `CONFIRMED` (failing ArchUnit rule)
- **Where**: `finvera-be/.../stock/service/StockHistoryImportService.java:54,58,60`
- **SDD home**: `specs/002-stock-detail-analysis/`
- **Violates**: Constitution III; `ARCHITECTURE.md` B-5

`StockHistoryImportService` constructs `market.entity.MarketImportBatchEntity`
and calls `market.repository.MarketImportBatchRepository` directly.
`StockModuleArchitectureTests.STOCK_DOES_NOT_REACH_INTO_MARKET_PERSISTENCE`
fails with 5 violations.

- **Fix**: publish a `MarketImportBatchService` application interface in
  `market/service/` exposing `existsByPackageSha256` and a `record`/`open` call,
  and have `StockHistoryImportService` depend on that instead. Mirrors the
  existing `MarketReferenceDataService` / `StockReferenceDataService` pattern.
- **Verify**: the ArchUnit rule passes; `StockHistoryImportServiceTests` still pass.
- **Verification**: `MarketImportBatchService` + `DefaultMarketImportBatchService`
  published in `market/service/` mirroring the `MarketReferenceDataService`
  precedent; `StockModuleArchitectureTests` 4/4, `StockHistoryImportServiceTests`
  3/3. SDD: specs/002 T081.

### Q-03 · `contextLoads` smoke test broken by retention cleanup — `DONE` (2026-08-30)

- **Severity**: Medium · **Confidence**: `CONFIRMED`
- **Where**: `finvera-be/.../shared/maintenance/DataRetentionCleanupService.java`
  vs `finvera-be/src/test/java/com/minhnb/finvera_be/FinveraBeApplicationTests.java`
- **SDD home**: `specs/002-stock-detail-analysis/` (regression from commit `1a36f08`)

`DataRetentionCleanupService` is an unconditional `@Service` requiring a
`JdbcTemplate`, but the smoke test excludes `DataSourceAutoConfiguration`. The
context fails with `NoSuchBeanDefinitionException`.

- **Fix**: prefer making the service conditional so it is only created when the
  cleanup is actually enabled — `DataRetentionCleanupConfiguration` already has
  `@ConditionalOnProperty("finvera.data-retention.cleanup.enabled")`, so the
  service should sit behind the same gate rather than being always-on. Adding a
  `@MockitoBean JdbcTemplate` to the test hides the coupling instead of removing it.
- **Verification (2026-08-30)**: service registration moved into
  `DataRetentionCleanupConfiguration` behind the same enablement gate
  (`@ConditionalOnMissingBean` keeps the config test's mock override working);
  `contextLoads` + both cleanup test classes pass. SDD: specs/002 T081.

### Q-04 · Stale and invalid test fixtures — `DONE` (2026-08-30)

- **Severity**: Low · **Confidence**: `CONFIRMED`
- **SDD home**: owning feature per test

| Test | Cause |
|---|---|
| `StockMigrationTests.createsAllFifteenStockTables…` | Expects 15 stock tables; V004+ brought it to 18 |
| `StrategySignalFailureTests.aLiveRegimeAssessmentIsIgnored…` | Fixture label `"BULLISH"`; the enum and DB check allow only `BULL`/`EARLY_BULL`/`SIDEWAYS`/`EARLY_BEAR`/`BEAR` |
| `TcbsLiveEquityQuoteServiceTests.exposesOnlyAccepted…` | Stub returns `null` from `resolveSession`, which production never does → NPE at `TcbsLiveEquityQuoteService.java:134` |
| `FixtureRuntimeBootstrapServiceTests` (×2) | `regime.label` null after bootstrap — needs investigation, may be a real fixture-mode regression |
| `StockDetailFailureTests.safeTelemetryRedacts…` | Expects `source=UNRECOGNIZED` in captured telemetry; nothing captured |

`StrategySignalFailureTests.aStaleRegimeAssessment…` is order-dependent: it does
not clear the regime row another test in the same class inserts for a later
trading date, so `findCurrentRegimeAssessment("EOD")` returns the wrong row.
Fix by scoping or cleaning fixture state, not by loosening the assertion.

- **Verification (2026-08-30)**: all five fixtures repaired without weakening any
  assertion — catalog count 18 (V014), label `BULL`, `resolveSession` stub
  broadened to production's never-null contract, batch-ordered cleanup for the
  regime scenarios, DEBUG capture for the redaction test; the
  `FixtureRuntimeBootstrap` pair was a real regression fixed in
  `MarketConfiguration` (specs/001 T087). SDD: specs/002 T081, specs/004 T035.

### Q-05 · Cross-source conflict detection is untested — `DONE` (2026-08-30)

- **Severity**: Medium · **Confidence**: `CONFIRMED`
- **Where**: `StockIngestionServiceTests.detectsAProductionSourceFamilyConflictAndRetainsBothProvenances:167`
- **SDD home**: `specs/002-stock-detail-analysis/` (DATA-010)

The fixture builds a bar with `close=80` outside `[low=98, high=101]`, so OHLC
validation now rejects it and the test never reaches its conflict assertion. The
validation is correct; the fixture is not. Consequence: **the DATA-010
cross-source reconciliation path has no passing test guarding it.**

- **Fix**: repair the fixture to a valid OHLC bar that still diverges from the
  TCBS bar's close, so the `SOURCE_CONFLICT` decision is genuinely exercised.
- **Verification (2026-08-30)**: fixture close `98.5` (inside `[98, 101]`) vs
  TCBS `100.5`; `StockIngestionServiceTests` 12/12 with the DATA-010 branch
  genuinely reached. SDD: specs/002 T081.

---

## Group C — Missing-data truthfulness

One recurring defect, found independently in four modules:

> Missing data is silently dropped from a sample or treated as zero, and the
> result is still published as `CURRENT`.

This violates `ARCHITECTURE.md` section 4 invariant #4 ("Missing, zero, invalid,
and not-applicable are four different things") and Constitution II.

### Q-06 · EOD breadth drops instruments with no price and still reports `CURRENT` — `DONE` (2026-08-30)

- **Severity**: High · **Confidence**: `CONFIRMED` (see [Evidence E-2](#e-2--instruments-without-a-current-session-bar))
- **Where**: `finvera-be/.../market/service/HistoricalMarketBreadthReconciliationService.java:124`
- **SDD home**: `specs/001-market-overview/`

```java
if (currentClose == null || previousClose == null) {
    continue;   // dropped from inputs, rather than counted as unclassified
}
```

Because dropped instruments never enter `inputs`, `eligible` counts only
instruments that *have* data, `unclassified` is always `0`, and
`statusForEndOfDay()` therefore always returns `CURRENT`. The `MISSING_PRICE` /
`MISSING_PRIOR_CLOSE` reason codes at lines 155-159 are unreachable.

706 instruments currently lack a bar for the latest session (94 have no bar at
all). Breadth is being computed on the remainder and labelled complete.

- **Fix**: add the instrument to `inputs` with a null price so `BreadthCalculator`
  classifies it `MISSING_PRICE`, letting `unclassified` and the reason codes work
  as designed and `statusForEndOfDay` return `PARTIAL`.
- **Verify**: an integration test with a universe where some instruments lack the
  session's bar produces `PARTIAL` with `MISSING_PRICE`, and `eligible` equals the
  full universe size.
- **Verification (2026-08-30)**: instruments now stay in the universe with null
  prices; the service test that had enshrined the drop (`eligible=2` over a
  3-instrument universe) now asserts `Result(0,2,0,1,3,[MISSING_PRIOR_CLOSE])`,
  plus a new stale-bar `MISSING_PRICE` scenario. R-007C conformance restored —
  the research already mandated "do not drop the instrument". SDD: specs/001
  R-012, T088.

### Q-07 · Regime v2 has no minimum breadth coverage floor — `DONE` (2026-08-30)

- **Severity**: High · **Confidence**: `CODE-READ`
- **Where**: `finvera-be/.../market/service/LiveMarketRegimeReconciliationService.java:145`
- **SDD home**: `specs/001-market-overview/`

The only guard is `advancing + declining > 0`. One advancing instrument and zero
declining yields `BREADTH = 100` carrying weight 0.25 of the published regime
score. Combined with Q-06 the sample can be arbitrarily small and unrepresentative
while the assessment still publishes.

- **Fix**: require a contracted minimum classified fraction of the eligible
  universe before the `BREADTH` component is admitted; withhold with a reason code
  otherwise. Threshold is configuration, never a hard-coded constant
  (`ARCHITECTURE.md` section 8).
- **Depends on**: Q-06 (needs an honest `eligible`/`unclassified` split to measure against).
- **Verification (2026-08-30)**: `finvera.market.regime.min-breadth-classified-fraction`
  (default 0.5) admits BREADTH only when `(adv+dec+unch)/eligible` meets the
  floor; below it, market-regime-v2's own `AGGREGATE_BREADTH_COMPONENT_UNAVAILABLE`
  fires — no rule-version change. Below-floor and exactly-at-floor tests added
  (9/9). SDD: specs/001 R-012, T088.

### Q-08 · Breadth trading date is the max across the whole universe — `DONE` (2026-08-30)

- **Severity**: Medium · **Confidence**: `CODE-READ`
- **Where**: `HistoricalMarketBreadthReconciliationService.java:78-82`
- **SDD home**: `specs/001-market-overview/`

A single instrument with a bar dated later than the rest sets the trading date
for the entire breadth calculation, leaving only that instrument with data. With
Q-06 unfixed this publishes as `CURRENT`.

- **Fix**: derive the session from the market calendar, or require a quorum of
  instruments sharing the date before accepting it.
- **Verification (2026-08-30)**: session anchored to the universe-consensus date
  (mode of latest bar dates, later date wins ties); a future-dated rogue bar
  test proves it cannot re-anchor the session (4/4). SDD: specs/001 R-012 pt 3,
  T089.

### Q-09 · Portfolio totals silently exclude unpriced positions — `DONE` (2026-08-30)

- **Severity**: High · **Confidence**: `CODE-READ`
- **Where**: `finvera-be/.../portfolio/domain/analytics/PortfolioAnalyticsV1.java:485-500`;
  `portfolio/service/PositionService.java`
- **SDD home**: `specs/005-portfolio-watchlist/` — also needs a **contract change**

`totalPositionsValue` accumulates only positions where `priceAvailable`, so
`totalValue = pricedPositions + cash`. A holding with no accepted price
contributes `0` and the owner is shown a definite total. Individual positions
carry `priceStatus: "MISSING"`, but `PositionsResponse` and
`PortfolioSummaryResponse` have no portfolio-level status field at all.

`contracts/portfolio-analytics-v1.md` does not define behaviour when a close
price is missing — a genuine spec gap, so amend the contract before the code.

- **Fix**: add a portfolio-level `dataStatus` + `reasonCodes` to both responses;
  return `PARTIAL` when any open position is unpriced; never let a missing price
  read as zero value.
- **Verification (2026-08-30)**: contract U-8 added to `portfolio-analytics-v1.md`
  and `PortfolioDataStatus`/`dataStatus`/`reasonCodes`/`priceDataStatus`/
  `priceTradingDate` to the OpenAPI schema first; `PositionService` emits
  `PARTIAL` + `POSITION_PRICE_UNAVAILABLE` (new unit test proves cash-only total
  with the unpriced holding disclosed, never zero-valued); FE holdings table and
  portfolio list render a text cue. SDD: specs/005 R-012, T041.

### Q-10 · Portfolio has no freshness evaluation at all — `DONE` (2026-08-30)

- **Severity**: High · **Confidence**: `CONFIRMED` (`StockFreshnessPolicy` has zero references under `portfolio/`)
- **Where**: `portfolio/service/PositionService.java`, `portfolio/service/WatchlistService.java:176`
- **SDD home**: `specs/005-portfolio-watchlist/`

`findLatestDailyBars` returns the latest bar regardless of age, and
`WatchlistService` assigns `dataStatus = "CURRENT"` merely because a bar exists.
A suspended or delisted instrument's two-year-old close is used as the current
price, marked current, and added to net worth. Every other module
(`StockOverviewService`, `TechnicalIndicatorService`, `ValuationService`,
`ScreenerService`) routes through `StockFreshnessPolicy`; portfolio does not.

- **Fix**: evaluate each position's and watchlist item's price through
  `StockFreshnessPolicy.evaluateDailyBarSeries` and surface `DELAYED`/`STALE`.
- **Verification (2026-08-30)**: both services now resolve the venue session via
  `MarketReferenceDataService.resolveSession` and apply the shared policy
  (`POSITION_PRICE_DELAYED/STALE`, `PRICE_DELAYED/STALE`); new tests cover a
  two-month-old close reported STALE and a previous-session close reported
  DELAYED. SDD: specs/005 R-012, T041.

### Q-11 · Watchlist and stock detail disagree on "daily change %" — `DONE` (2026-08-30)

- **Severity**: Medium · **Confidence**: `CODE-READ`
- **Where**: `portfolio/service/WatchlistService.java:180-186` vs
  `stock/domain/overview/StockOverviewCalculator.java:42-45`
- **SDD home**: `specs/005-portfolio-watchlist/`

| Screen | Formula |
|---|---|
| Stock detail | `(last − referencePrice) / referencePrice` |
| Watchlist | `(close − **open**) / open` |

Two different numbers for the same symbol on the same day. The field is named
`dailyChangePercent` while the code comment calls it "approximate".
`DailyBarReference.referencePrice` exists but is `null` for Vnstock/KBS imports
(R-015 update: KBS does not publish a historical reference price), so the correct
basis is the **prior accepted close** — exactly what EOD breadth already uses.

- **Fix**: use `referencePrice` when present, else prior accepted close; never
  today's open. Reuse the existing calculator rather than a second formula.
- **Verification (2026-08-30)**: contract U-9; `WatchlistService` fetches two bars
  per instrument in one bulk call and uses reference price, else prior close.
  `WatchlistServiceTests` proves +0.99% (prior-close basis) where the open basis
  would have shown +2.00%, and `null` rather than `0` with no basis. SDD:
  specs/005 R-012, T041.

### Q-12 · `benchmarkReturn` reports `"0"` when VN-Index data is missing — `DONE` (2026-08-30)

- **Severity**: Medium · **Confidence**: `CODE-READ`
- **Where**: `portfolio/service/PortfolioAnalyticsService.java:268`; contract
  `specs/005-portfolio-watchlist/contracts/portfolio-watchlist.openapi.yaml:699`
- **SDD home**: `specs/005-portfolio-watchlist/` — **contract change required**

```java
benchResult.benchmarkReturn() != null ? formatDecimal(...) : "0"
```

"VN-Index unknown" is presented as "VN-Index was flat". `ARCHITECTURE.md`
section 5 states an unavailable fact is `null` with a reason code and never `0`.
The OpenAPI schema forces this by declaring `benchmarkReturn` non-nullable, so
**the contract is defective too** and must be fixed first.

- **Fix**: make `benchmarkReturn` nullable in the contract, return `null` plus a
  reason code, and have the client render an explicit unavailable state.
- **Verification (2026-08-30)**: OpenAPI `benchmarkReturn` nullable + `reasonCode`;
  both service paths emit `null` + `BENCHMARK_UNAVAILABLE`; the analytics view
  shows "Không có dữ liệu VN-Index cho kỳ này" instead of 0.00%. SDD: specs/005
  R-012, T041.

---

## Group D — Provider data expansion

**SDD home**: new feature `specs/008-provider-data-expansion/` (owner decision,
2026-08-30). Full `spec → clarify → plan/research/contracts → tasks → implement`
cycle, because this adds capability rather than restoring conformance.

**2026-08-30 status**: `spec.md` drafted (US1 EV/EBITDA, US2 free cash flow,
US3 price limits + foreign room; order-book depth and the wider ratio family
explicitly out of scope). Two `[NEEDS CLARIFICATION]` markers block
implementation until the owner runs the `balance_sheet` probe: (1) whether KBS
exposes interest-bearing debt separately from total liabilities (valuation-v1's
`totalDebt` definition), (2) whether session price limits are persisted on the
accepted price observation. Q-13..Q-17 stay `TODO` until `plan.md`/`research.md`
close those markers.

We currently map **13 of 133+** available provider fields
([Evidence E-3](#e-3--provider-field-coverage)).

### Q-13 · `balance_sheet` was never probed — `DONE` (2026-08-30: probed; unavailable — specs/008 R-001)

- **Severity**: High · **Confidence**: `CONFIRMED` (no `balance_sheet` section in `poc-output/item-labels.txt`)

`EQUITY_ATTRIBUTABLE_TO_PARENT`, `TOTAL_DEBT`, and `CASH_AND_EQUIVALENTS` are all
in the metric catalog and in `FundamentalReportAcceptance.ALLOWED_METRIC_CODES`,
and `ValuationV1.computeMetrics` needs all three for
`ev = marketCap + totalDebt − cashAndEquivalents`. None has a provider mapping,
so `ev` is always `null`.

- **Fix**: extend `poc_vnstock_fundamentals.py` to probe `balance_sheet`, record
  the confirmed `item_id` set in the new feature's `research.md`, then map.

### Q-14 · `EBITDA` has no mapping, so `EV_EBITDA` never publishes — `DONE` for EBITDA/EBITDA_TTM; `EV_EBITDA` stays withheld by evidence (2026-08-30, specs/008 R-001/R-003, T004–T007)

- **Severity**: High · **Confidence**: `CONFIRMED`

`EBITDA` is catalogued and allowed, `FundamentalSummaryCalculator` derives
`EBITDA_TTM` from it, and `EV_EBITDA` carries **weight 0.20** of the valuation
score. Nothing maps to `EBITDA`, so the metric is permanently `MISSING` and
**every published valuation runs on at most 0.80 of its designed weight**
(PE 0.40 + PB 0.30 + PEG 0.10).

- **Fix**: derive from the provider's `ebitda_net_revenue` (EBITDA margin) and
  `revenue`, or map a direct balance/income item if Q-13's probe finds one.
  Record the derivation as a versioned rule; do not guess.

### Q-15 · `CASH_FLOW_MAP` is empty, so `FREE_CASH_FLOW` never publishes — `DONE` (2026-08-30, specs/008 R-002, T004–T007; annual-only)

- **Severity**: Medium · **Confidence**: `CONFIRMED`
- **Where**: `tools/market-data/vnstock-export/export_fundamentals.py:47`

`FREE_CASH_FLOW` is catalogued, allowed, summarised and displayed, but
`CASH_FLOW_MAP: dict[str, str] = {}`. The provider exposes 50 cash-flow items
including `operating_cash_flow` and
`payment_for_fixed_assets_constructions_and_other_long_term_assets`.

- **Fix**: map both and define FCF = operating cash flow − capital expenditure as
  a versioned rule in the feature contract.

### Q-16 · Unused TCBS fields: price limits, foreign room, order book — `DONE` for ceiling/floor/room (2026-08-30, specs/008 R-006, T008–T009); order book stays out of scope

- **Severity**: Medium · **Confidence**: `CONFIRMED` (schema in `poc-output/tcbs-capability-summary.json`)

| Frame / endpoint | Available but unmapped |
|---|---|
| `s\|4` equity reference | `ceilPrice`, `floorPrice` |
| `s\|6` equity trade | `matchQtty` |
| `s\|8` index | `ceilIncrease`, `floorDecrease` |
| REST `tickerCommons` | `avg`, `open`, `high`, `low`, `room`, `buyForeignQtty`, `sellForeignQtty`, `bidPrice01-03`, `offerPrice01-03`, `nextCeilPrice`, `nextFloorPrice`, `nextRefPrice` |

Price limits matter: `AGENTS.md` requires accounting explicitly for Vietnamese
price limits, and nothing in the system currently knows a ceiling or floor.
Foreign room is a standard Vietnamese decision input.

- **Fix**: extend `TcbsThesisFrameMapper` and the quote provider contract; add
  ceiling/floor to the overview response; treat the order book as out of scope
  unless a user story needs it.

### Q-17 · Unused KBS ratio family — `DONE` (2026-08-30 via Feature 009: 22 provider-reported ratio codes, 7 screener filters; two unit defects fixed — `DIVIDEND_YIELD` fraction, `DEBT_TO_EQUITY` unit; provider valuation ratios and the zero-only cash-flow family stay excluded by contract)

- **Severity**: Low · **Confidence**: `CONFIRMED`

Available and unmapped: `beta`, `ps_ratio`, `gross_margin`, `net_margin`,
`cash_ratio`, `quick_ratio`, `short_term_ratio`, `interest_coverage`,
`inventory_turnover`, `receivables_turnover`, `total_asset_turnover`,
`roe_trailling`, `roa_trailling`, `cash_flow_per_share_cps`, and the growth
family. These would give the screener and strategy engines liquidity, efficiency,
and solvency filters they currently lack.

- **Fix**: add only what an approved user story needs (Constitution VIII).

---

## Group E — Security and AI grounding

### Q-18 · Hardcoded default for the internal API key — `DONE` (2026-08-30)

- **Severity**: Medium · **Confidence**: `CONFIRMED` (startup `WARN` observed in the test run)
- **Where**: `research/config/ResearchProperties.java:19`, `analyst/config/AnalystProperties.java:19`
- **Violates**: Constitution *Configuration* — "Secrets: No default value. A missing secret must fail startup, not fall back to something weak."

Both default to `"dev-internal-key-change-in-prod"` and only log a warning.

- **Fix**: fail startup when the key is unset.
- **Verification (2026-08-30)**: both Spring property records and the FastAPI
  `Settings` now throw on blank or the old placeholder; the WARN runner is gone;
  test profiles supply a test secret. Owner `.env` files already hold real keys.
  SDD: specs/006 T051, specs/007 T046.

### Q-19 · `/internal/v1/**` is `permitAll()` in Spring Security — `DONE` (2026-08-30)

- **Severity**: Medium · **Confidence**: `CODE-READ`
- **Where**: `auth/config/OwnerSecurityConfiguration.java:43`; `research/config/InternalApiKeyFilter.java`

All protection rests on a single filter that matches on the **raw**
`getRequestURI()` while Spring Security matches on the **normalized** path — two
different path notions guarding the same endpoints. The filter fails closed
today, so this is defence-in-depth rather than a known hole.

- **Fix**: `.requestMatchers("/internal/v1/**").hasRole("INTERNAL_SERVICE")` so
  authorization does not depend on the filter alone; use a constant-time
  comparison for the key. Add a negative authorization test
  (`AGENTS.md` requires one for any auth change).
- **Verification (2026-08-30)**: `hasRole("INTERNAL_SERVICE")` on `/internal/v1/**`;
  filter fails closed without properties and uses `MessageDigest.isEqual`
  (`hmac.compare_digest` on the AI side); existing negative 401 tests still pass
  (`InternalIngestionCallbackControllerSecurityTests`, `InternalToolControllerTests`).
  SDD: specs/006 T051.

### Q-20 · RAG keeps uncited claims in the answer text — `DONE` (2026-08-30)

- **Severity**: Medium · **Confidence**: `CODE-READ`
- **Where**: `finvera-ai/app/features/rag/citations.py:60`, `.../synthesis.py:142`
- **Contract**: `specs/006-news-document-rag/contracts/rag-v1.md:112` — "if a claim ends with zero valid `blockRefs`, that claim is **removed from the answer**"

Dropped claims are removed from the citation list only; `verification.answer`
returns the model's full prose. Worse, `extract_claims_and_citations` creates a
claim only for sentences containing `[Block N]`, so a sentence with no citation
at all is never examined and reaches the user verbatim as long as one other
sentence cites validly.

- **Fix**: rebuild the answer from surviving claims, or drop non-conforming
  sentences, so delivered prose and verified citations cannot diverge.
- **Verification (2026-08-30)**: `answer` rebuilt from surviving claims; new test
  proves an uncited "will double next year" sentence is dropped while the cited
  revenue claim survives. SDD: specs/006 T052.

### Q-21 · `verify_faithfulness` is weak and can over-attribute — `DONE` (2026-08-30)

- **Severity**: Medium · **Confidence**: `CODE-READ`
- **Where**: `finvera-ai/app/features/analysis/explain.py:76-80`

Only 13 hardcoded indicator codes are blocked; there is no check for fabricated
numbers. And `referenced_codes if referenced_codes else [all allowed factors]`
means a model that referenced nothing is reported as having referenced
everything.

- **Fix**: return an empty attribution when nothing was referenced, and add a
  numeric-claim check for values not present in the supplied evidence.
- **Verification (2026-08-30)**: both implemented; "tăng 15% quý tới" now fails,
  `1,25` vs `1.25` passes; no-reference returns `(False, [])`. SDD: specs/007 T047.

### Q-22 · Tool `symbol` is unvalidated before URL interpolation — `DONE` (2026-08-30)

- **Severity**: Low · **Confidence**: `CODE-READ`
- **Where**: `finvera-ai/app/features/orchestration/allowlist.py` (`SymbolToolArgs`),
  used in `dispatch.py`

`symbol` is length-bounded and upper-cased but has no character-set constraint,
then goes straight into the request path. Contained by the internal boundary and
server-side ownership checks, but the input should be typed properly.

- **Fix**: `pattern=r"^[A-Z0-9]{1,20}$"`.
- **Verification (2026-08-30)**: enforced inside the normalising validator (after
  upper-casing) on all three symbol-bearing tool schemas; `VNM/../portfolios` is
  rejected as `INVALID_ARGUMENTS`. SDD: specs/007 T047.

---

## Group F — Hygiene and performance

| ID | Status | Severity | Confidence | Item |
|---|---|---|---|---|
| **Q-23** | `DONE` (2026-08-30, specs/002 T082) | High | `LATENT` | `ValuationService.java:398,502` — `case "EBITDA_TTM", "EV_EBITDA" -> ebitdaTtm` assigns the EV/EBITDA **ratio** into the absolute **EBITDA** field. Unreachable today only because `EV_EBITDA` is absent from `ALLOWED_METRIC_CODES`, while `export_fundamentals.py:46` already emits it — adding one allowlist entry (which Q-14 may well do) silently corrupts every valuation. **Fix before Group D.** |
| **Q-24** | `DONE` (2026-08-30, specs/001 T090) | Medium | `CODE-READ` | `TcbsLiveEquityQuoteService.java:110-112` — `sessionFacts`/`referencePrices` are never reset per trading date, so after a day rollover the live open/high/low and reference price are the previous session's. Reference price feeds the displayed change %. |
| **Q-25** | `DONE` (2026-08-30, specs/002 R-017, T084 — policy v2 compares the reference only when both sides carry one; `toFact` passes the real reference price) | Low | `CODE-READ` | `StockIngestionService.java:313` — `new Fact(bar.getClosePrice(), bar.getOpenPrice(), status)` passes the **open** price into `SourceReconciliationPolicy.Fact`'s **reference** slot. Undocumented; the entity has `getReferencePrice()`. |
| **Q-26** | `DONE` (2026-08-30, specs/002 T083) | Low | `CODE-READ` | `ValuationV1.java:177-201` — basis-B effective weights are computed but never stored, so `MetricResult.effectiveWeight` is `null` whenever only the sector basis is used. Violates Constitution I's "contributing factors" requirement. |
| **Q-27** | `DONE` (2026-08-30, specs/002 T083) | Low | `CODE-READ` | `ValuationService.buildOwnHistorySeries` applies today's `sharesOutstanding` to every historical point. Documented in the Javadoc but never surfaced to the user as a reason code. |
| **Q-28** | `DONE` (2026-08-30, specs/002 T084 + specs/005 T042: bulk metric fetch for own-history; single replay per date in performance history) | Low | `CODE-READ` | Performance: `ValuationService.findBySymbol` runs up to 750 `FundamentalSummaryCalculator.calculate` passes plus an N+1 metric fetch on every read; `buildSectorSeries` calls `findBySymbol` per peer. `PortfolioAnalyticsV1.calculatePerformanceHistory` replays holdings twice per trading date. |
| **Q-29** | `DONE` (2026-08-30: ARCHITECTURE §3 module map, logs untracked, zero-reference guard in specs/001 T090) | Low | `CONFIRMED` | `ARCHITECTURE.md` section 3's module map omits `portfolio/`, `research/`, `analyst/`. `finvera-be/local-import.out.log` and `.err.log` are tracked in git despite `*.log` in `.gitignore`. `BreadthCalculator.java:47` accepts a zero reference price, which would classify every instrument `ADVANCING`. |
| **Q-30** | `DONE` (2026-08-30, specs/008 T011: fiscal-period staleness rule + `--full-refresh`) | Medium | `CONFIRMED` | `export_all_symbols.py` — fundamentals packages are checkpoint-"current" until the exporter version changes; unlike daily bars there is no date-aware staleness, so a new quarterly/annual report is never re-exported by a routine run. 2026-08-30: `--full-refresh` now forces fundamentals (and the new annual pass) to re-export; a proper fix is a fiscal-period staleness rule (re-export when `today > last period end + ~45 days`). |
| **Q-31** | `DONE` code-side (2026-08-30, Feature 010 T001–T004; SC-001..003 measured after the owner's next `-FullRefresh`) | High | `CONFIRMED` | `valuation-v1` publishes for **7 / 3,050** current assessments. Measured causes (specs/010 R-001): `equity_profile.shares_outstanding` null for 1,524/1,524 (exporter never read `Company(kbs).overview()`); bank EPS `item_id` `earning_per_share_vnd` unmapped (483 `NO_DATA`); provider returns 4 periods, so the 8-quarter growth rule is unreachable (growth `MISSING` 1,543/1,543). Fix: profile exporter 0.2.0 + effective-dated profile revisions; EPS id mapped; `fundamental-summary-v2` annual fallback labelled `ANNUAL_BASIS`. |
| **Q-32** | `DONE` (2026-08-30, Feature 011 T001) | High | `CONFIRMED` (probe table, specs/011 R-001) | Daily bars: the provider's `end` is not inclusive (end=Fri → last bar Thu; end=Sat → Thu). `export_all_symbols` defaults `--end` to today, so a routine refresh never imports the newest session and freshness reads `DELAYED` for a day that existed. Fix: request `end + 3` and cut back to `end`. Extended 2026-08-30 to `export_history.py` (index levels showed the same one-session lag: `index_snapshot` max 08-27 vs session 08-28). DB evidence: 1,264 current valuation assessments carried `PRICE_STALE`. |
| **Q-33** | `DONE` (2026-08-30, Feature 011 T002) | Critical (unreleased) | `CONFIRMED` | Feature 010 exporter emitted vnstock's `free_float_percentage`, which is really `shares × par` (2.09e13 for VNM); `equity_profile.free_float_ratio` has `check (between 0 and 1)` → the next import would have failed. Provider has no free float; field removed, share count cross-checked with charter capital / par. |
| **Q-34** | `DONE` (2026-08-30, Feature 011 T003/T005, contract `provider-ratio-facts-v2`) | High | `CONFIRMED` | KBS quarter columns are single-quarter values for ROE/ROA/ROCE/NIM/turnovers/P/S/dividend yield (VNM ROE 6.86 vs trailing 26.37); Finvera showed and screened them as annualized. Annual dataset carries `roe_trailling = 0.0` placeholders that were stored as `ROE_TTM = 0`. The Feature 009 dividend-yield ×100 rule was fitted to a coincidence and withdrawn. |
| **Q-35** | `DONE` (2026-08-30, Feature 011 T004) | Medium | `CONFIRMED` | Insurance (`profit_after_tax`, `total_net_revenue_from_insurance_business`) and securities (`revenue_from_securities_business_01_11`, `net_profit_from_securities_business_…`) statement ids were unmapped → no NET_PROFIT/REVENUE for BVH, no REVENUE/OPERATING_PROFIT for SSI. FCF derivation v2 accepts the securities OCF id and insurance capex id. |
| **Q-36** | `DONE` (2026-08-30, Feature 011 T006) | Medium | `PROCESS` | No regression guard on the provider's item-id schema — the bank-EPS rename went unnoticed. `tests/provider_schema_fixture.json` + `test_provider_schema.py` pin the observed ids per company type and required concept coverage. |
| **Q-37** | `DONE` (2026-08-30, owner approved: `$historyStartDate = 2023-01-01`; exporters re-fetch the whole range once when `rangeStart` moves earlier — `export_all_symbols`, `export_history`; test `test_earlier_start_than_existing_file_triggers_a_full_range_refetch`; takes effect on the next refresh after the running one) | Medium | `MEASURED` | Own-history valuation basis needs 500 sessions with a visible report. Bars start at `$historyStartDate = 2024-01-01` (refresh-data.ps1:69): only 746 / 1,430 instruments have ≥ 500 bars, and no history point can exist before the first visible annual report (FY2022 → visible ~Mar 2023). Setting the start to **2023-01-01** adds ~250 sessions per instrument at no fundamentals cost (provider returns only four fiscal years anyway) and is the last lever left for the history basis. Cost: one full bar re-fetch (~1,500 calls). Not changed without the owner's go-ahead. |
| **Q-38** | `ACCEPTED — unavailable` (2026-08-30: KBS `sector/stock` returns the same 29 symbols per industry for every list parameter; VCI listing/overview fail inside vnstock 4.0.6 with `UnboundLocalError`; TCBS public overview answers 403. No trustworthy source reachable → UPCoM stays unclassified and the sector basis honestly does not apply) | Medium | `CONFIRMED` (probe) | Sector basis: KBS `symbols_by_industries()` classifies 697 symbols = HOSE + HNX; all 818 UPCoM stocks have no sector (827 / 1,524 profiles without `sector_reference_id`), so the sector cross-section basis can never apply to them. 22 of 25 sectors have ≥ 8 constituents. A second classification source (VCI failed in the probe with an upstream error) would be a provider decision, not a mapping fix. |

---

## Evidence

### E-1 · Price units by source

Queried against the private local database, 2026-08-30.

```
equity_daily_bar (is_current)
source       | bars    | min_close  | max_close     | avg_close
VNSTOCK_KBS  | 632956  | 180.000000 | 685000.000000 | 21216.39

equity_price_observation
source              | count | min          | max           | avg
TCBS_IFLASH_THESIS  | 1843  | 20850.000000 | 232400.000000 | 165383.61

index_snapshot
source              | count | min        | max
TCBS_IFLASH_THESIS  | 2713  | 127.070000 | 1977.660000
VNSTOCK_KBS         | 2634  | 84.410000  | 2096.760000
```

Both equity sources are base VND/share. No `TCBS_IFLASH_STOCK_DATA` rows remain.
Vnstock/KBS board units are converted once in
`export_daily_bars.normalize_kbs_price`; TCBS Thesis publishes base VND and
`TcbsThesisFrameMapper` deliberately performs no conversion.

Instruments closing below 1,000 VND — the ones the removed heuristic inflated:

```
under_1000 | at_or_over_1000 | total
30         | 1400            | 1430

ACM/QBS/CAD/PXM/DCT/LO5/DFF/HKB/ATA  UPCOM  400
PPI/FTM/LUT                          UPCOM  500
```

### E-2 · Instruments without a current-session bar

From `tmp/missing-breadth-price.csv` (706 rows):

```
UPCOM 552 | HNX 117 | HOSE 37
94 instruments have no accepted daily bar at all
```

These are the instruments EOD breadth silently drops while still reporting
`CURRENT` (Q-06).

### E-3 · Provider field coverage

From `tools/market-data/provider-poc/poc-output/item-labels.txt`:

| Dataset | Provider items | Mapped | Coverage |
|---|---|---|---|
| KBS `income_statement` | 25 | 5 | 20% |
| KBS `ratio` | 58 | 8 | 14% |
| KBS `cash_flow` | 50 | 0 | 0% |
| KBS `balance_sheet` | never probed | 0 | — |
| **Total** | **133+** | **13** | **~10%** |

### E-4 · Test baseline

`cd finvera-be; .\mvnw.cmd test` on 2026-08-30 (review baseline):

```
Tests run: 643, Failures: 7, Errors: 3, Skipped: 0
BUILD FAILURE
```

After Groups B and C (2026-08-30):

```
Tests run: 645, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

`finvera-fe` after Groups A and C: `npx vitest run` 127/127, lint clean, build clean.

After Groups E/F and Feature 008 (2026-08-30): backend 654/654 (+2 limits tests), exporter 18/18, AI 85/85, FE 127/127.

After Feature 009 and Q-25/Q-28/Q-30 (2026-08-30): backend 659/659, exporter 23/23, AI 85/85, FE 129/129. Plan: 29 done, 0 deferred, 0 open.

Feature-readiness measurement 2026-08-30 (pre-refresh DB): technical indicators computed for 1,428 instruments (250-session family INSUFFICIENT_HISTORY for ~412 thin/new listings — legitimate); regime v2 `EARLY_BULL` CURRENT 100 % completeness; 4 indices 2024-01-03..2026-08-27; strategy signals 10 LONG, no risk withholds; fundamentals summaries 1,355 CURRENT / 185 STALE / 3 DELAYED; valuation history_point_count ≤ 133 for 1,178 instruments and null for 1,858 (quarter-only history — the annual pass in the running refresh is what unlocks ≥ 500); BVPS present on 1,333 instruments' reports but only 198 summaries (packages exported before tool 0.5.0 — full refresh re-exports).

After Feature 011 (2026-08-30): exporter 35/35 (+8), backend 663/663 (+1), FE 129/129, lint/build clean. Plan: 35 done (Q-31 measurement pending refresh). Fundamentals tool 0.6.0 forces a full re-export on the owner's next `-FullRefresh`.

After Feature 010 (2026-08-30): backend 662/662 (+3 v2 summary, +1 profile revision), exporter 26/26 (+3). Plan: 30 done (Q-31 awaits the post-refresh measurement). Measurement plan once the owner runs `.
efresh-data.ps1 -FullRefresh`: `select count(*) filter (where shares_outstanding is not null) from equity_profile where effective_to is null` (SC-001 ≥ 95 %); `select published, count(*) from valuation_assessment where is_current group by 1` (SC-002 ≥ 50 %); `EPS_GROWTH_PERCENT` `DEFINED` share among instruments with ≥ 2 annual reports (SC-003 ≥ 60 %).

`finvera-fe`: `npx vitest run` 126/126, `npm run lint` clean, `npm run build`
clean. `finvera-ai`: `uv run pytest` 81/81.

---

## Changelog

| Date | Change |
|---|---|
| 2026-08-30 | Opened from the full-system review. Q-01 completed (R-016, T080). |
| 2026-08-30 | Feature-readiness audit on the live DB (see Evidence): Q-37 history start → 2023-01-01 (done), Q-38 UPCoM sectors accepted as unavailable after three source probes. |
| 2026-08-30 | Feature 011 implemented after a field-by-field provider audit (10 symbols × every dataset + raw KBS pages): bars end-date lag, bogus free float, period-scoped ratios, annual `0.0` placeholders, withdrawn dividend ×100, insurance/securities ids, FCF v2, schema fixture. Q-32..Q-36. |
| 2026-08-30 | Feature 010 implemented (spec → research → plan → tasks → code): outstanding shares/free float via provider overview with profile revisions, bank EPS id, `fundamental-summary-v2` annual fallback. Q-31 opened and closed code-side. |
| 2026-08-30 | Feature 009 implemented (spec → research → plan → contract → tasks → code): extended fundamentals + screener filters; Q-17 done. |
| 2026-08-30 | Q-25, Q-28, Q-30 done (reconciliation v2, N+1/double-replay removal, fiscal-period staleness). Remaining: Q-17 deferred by design. |
| 2026-08-30 | Feature 008 implemented (US1 EBITDA facts, US2 free cash flow, US3 price limits/room); balance sheet confirmed unavailable so EV_EBITDA remains honestly withheld. Q-13..Q-16 done, Q-17 deferred. |
| 2026-08-30 | Group F: Q-24, Q-26, Q-27, Q-29 done; Q-25 deferred (needs reconciliation-v2 decision); Q-28 (performance) remains. |
| 2026-08-30 | Feature 008 `spec.md` drafted (Group D entry point); Q-25 deferred with rationale; Q-29 docs/git hygiene done. |
| 2026-08-30 | Group E complete: Q-20..Q-22 — RAG answer rebuilt from verified claims, faithfulness numeric guard + honest attribution, symbol charset (specs/006 T052, specs/007 T047); `uv run pytest` 85/85. |
| 2026-08-30 | Q-18/Q-19 done: internal API key fails fast on both services, `/internal/v1/**` role-gated, constant-time compare (specs/006 T051, specs/007 T046). |
| 2026-08-30 | Q-23 (latent EV_EBITDA/ebitdaTtm conflation) removed ahead of Group D (specs/002 T082). Full backend suite 645/645 after Group C. |
| 2026-08-30 | Group C complete: Q-09..Q-12 — portfolio/watchlist missing-price and freshness disclosure, one daily-change basis, null benchmark (contracts amended first; specs/005 R-012, T041). |
| 2026-08-30 | Group B complete: Q-02..Q-05 — backend suite 643/643 green (was 7F+3E). Q-06..Q-08 complete: honest EOD breadth coverage, regime coverage floor, consensus session date (specs/001 R-012, T087-T089; specs/002 T081; specs/004 T035). |
