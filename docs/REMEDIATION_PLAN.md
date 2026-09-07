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
explicitly out of scope). The two `[NEEDS CLARIFICATION]` markers were closed on
2026-08-30 (probe: the KBS balance sheet was unavailable, so EV_EBITDA stayed
honestly withheld) and then SUPERSEDED on 2026-08-31 by the VCI migration:
Features 018/019/022 map the full VCI balance sheet (TOTAL_DEBT,
CASH_AND_EQUIVALENTS -> EV_EBITDA publishes), 16 derived ratios and cash
dividends. Q-13..Q-17 are all `DONE`; the KBS-era field counts below describe
the retired provider and are kept as history.

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
| **Q-31** | `DONE` (2026-08-30; measured after the owner's `-FullRefresh` + `-Cleanup` run, see Evidence) | High | `CONFIRMED` | `valuation-v1` publishes for **7 / 3,050** current assessments. Measured causes (specs/010 R-001): `equity_profile.shares_outstanding` null for 1,524/1,524 (exporter never read `Company(kbs).overview()`); bank EPS `item_id` `earning_per_share_vnd` unmapped (483 `NO_DATA`); provider returns 4 periods, so the 8-quarter growth rule is unreachable (growth `MISSING` 1,543/1,543). Fix: profile exporter 0.2.0 + effective-dated profile revisions; EPS id mapped; `fundamental-summary-v2` annual fallback labelled `ANNUAL_BASIS`. |
| **Q-32** | `DONE` (2026-08-30, Feature 011 T001) | High | `CONFIRMED` (probe table, specs/011 R-001) | Daily bars: the provider's `end` is not inclusive (end=Fri → last bar Thu; end=Sat → Thu). `export_all_symbols` defaults `--end` to today, so a routine refresh never imports the newest session and freshness reads `DELAYED` for a day that existed. Fix: request `end + 3` and cut back to `end`. Extended 2026-08-30 to `export_history.py` (index levels showed the same one-session lag: `index_snapshot` max 08-27 vs session 08-28). DB evidence: 1,264 current valuation assessments carried `PRICE_STALE`. |
| **Q-33** | `DONE` (2026-08-30, Feature 011 T002) | Critical (unreleased) | `CONFIRMED` | Feature 010 exporter emitted vnstock's `free_float_percentage`, which is really `shares × par` (2.09e13 for VNM); `equity_profile.free_float_ratio` has `check (between 0 and 1)` → the next import would have failed. Provider has no free float; field removed, share count cross-checked with charter capital / par. |
| **Q-34** | `DONE` (2026-08-30, Feature 011 T003/T005, contract `provider-ratio-facts-v2`) | High | `CONFIRMED` | KBS quarter columns are single-quarter values for ROE/ROA/ROCE/NIM/turnovers/P/S/dividend yield (VNM ROE 6.86 vs trailing 26.37); Finvera showed and screened them as annualized. Annual dataset carries `roe_trailling = 0.0` placeholders that were stored as `ROE_TTM = 0`. The Feature 009 dividend-yield ×100 rule was fitted to a coincidence and withdrawn. |
| **Q-35** | `DONE` (2026-08-30, Feature 011 T004) | Medium | `CONFIRMED` | Insurance (`profit_after_tax`, `total_net_revenue_from_insurance_business`) and securities (`revenue_from_securities_business_01_11`, `net_profit_from_securities_business_…`) statement ids were unmapped → no NET_PROFIT/REVENUE for BVH, no REVENUE/OPERATING_PROFIT for SSI. FCF derivation v2 accepts the securities OCF id and insurance capex id. |
| **Q-36** | `DONE` (2026-08-30, Feature 011 T006) | Medium | `PROCESS` | No regression guard on the provider's item-id schema — the bank-EPS rename went unnoticed. `tests/provider_schema_fixture.json` + `test_provider_schema.py` pin the observed ids per company type and required concept coverage. |
| **Q-37** | `DONE` (2026-08-30, owner approved: `$historyStartDate = 2023-01-01`; exporters re-fetch the whole range once when `rangeStart` moves earlier — `export_all_symbols`, `export_history`; test `test_earlier_start_than_existing_file_triggers_a_full_range_refetch`; takes effect on the next refresh after the running one) | Medium | `MEASURED` | Own-history valuation basis needs 500 sessions with a visible report. Bars start at `$historyStartDate = 2024-01-01` (refresh-data.ps1:69): only 746 / 1,430 instruments have ≥ 500 bars, and no history point can exist before the first visible annual report (FY2022 → visible ~Mar 2023). Setting the start to **2023-01-01** adds ~250 sessions per instrument at no fundamentals cost (provider returns only four fiscal years anyway) and is the last lever left for the history basis. Cost: one full bar re-fetch (~1,500 calls). Not changed without the owner's go-ahead. |
| **Q-38** | `DONE` (verified on the post-crawl database 2026-09-04: **1,522 / 1,522 current LISTED profiles carry a sector**, 100 % under one scheme `VCI_ICB_L3` — the >=95 % criterion met with no exception; code 2026-08-31, Feature 020 / ADR-0012; was `ACCEPTED — unavailable` on 2026-08-30 because VCI listing failed inside vnstock 4.0.6) | Medium | `MEASURED` (owner-authorized probe, vnstock 4.0.7) | VCI `Listing(source="vci")` publishes the ICB classification for all three exchanges: level 3 covers **1,524 / 1,524** current LISTED profiles (UPCOM 820, HOSE 405, HNX 299), 37 groups in the universe, 29 with ≥ 8 constituents (1,501 instruments) — against `KBS_INDUSTRY` 697 / 1,524. New exporter `export_sector_reference_vci.py` (scheme `VCI_ICB_L3`, package contract unchanged, rules S-1…S-6 in specs/020 contracts/vci-icb-sector-reference-v1.md; live package 2026-08-31: 1,522 equities, 36 sectors, 7 below the floor, VNH duplicate reported), 5 anchored tests; `refresh-data.ps1` exports it every run (3 calls) and prefers it over a stale pin to the KBS file (warning, not silent); `verify_calcs.py` `sector` gate ≥ 95 % under one scheme. **Owner action: the plain `.\refresh-data.ps1` imports it (stage 4) and the next warmup recomputes sector percentiles; then `verify_calcs.py` → `sector` OK.** |
| **Q-39** | `DONE` (2026-08-30) | High | `CONFIRMED` (owner's console + checkpoint: 2 / 99 symbols `failed:RetryError` in the running refresh) | `export_all_symbols.py` paced 2 s per symbol, but one symbol costs ~20 provider calls (vnstock fetches KBS statements one period per page) against the Community limit of 60/min. vnai retries only twice with 1–2 s back-off, so an exhausted retry surfaced as `RetryError` and was recorded as a permanent failure that later runs never retried. Fix: `wait_for_quota` reads vnai's minute counter and waits for room before every dataset; rate-limit failures are retried once after the window resets and are treated as transient by `is_finished` (retried on the next run); `--retry-failed` now also clears annual failures. Tests 39/39. |
| **Q-40** | `DONE` (2026-08-30) | Medium | `CONFIRMED` (owner console: profile export re-ran on the second refresh) | `export_equity_profile.py` had no reuse: every refresh spent ~51 min re-calling `overview()` for 1,522 symbols although share counts change only on corporate actions. Fix: reuse the package on disk when same tool version and ≤ 30 days old, fetch only symbols absent from it; `-FullRefresh` passes `--full-refresh`. |
| **Q-41** | `OPEN — low` | Low | `CODE-READ` | Import stage re-reads every `daily-bars-*.json` (full history per symbol, ~1M records after the 2023 start) each run; each record is a single indexed duplicate lookup (`uq_ingestion_fallback`), so it is correct and bounded but O(total history) rather than O(new days). `ValuationWarmupService` skips only when an assessment already exists for today's bar date, i.e. practically never; harmless (`ValuationService` writes a new row only when linked inputs differ) but not a real saving. Optimize later if stage 6/7 durations become a problem. |
| **Q-42** | `DONE` (2026-08-31, Feature 012 `valuation-v2`: coverage = qualifying / obtainable weight; NOT_APPLICABLE and structural EV/EBITDA leave the denominator, MISSING stays; `REDUCED_METRIC_SET` disclosed; confidence unchanged. SC-001 (≥ 50 % published) measured after the next refresh) | Medium | `MEASURED` | `valuation-v1` publishes only when qualifying weight ≥ 0.50 over PE 0.40 / PB 0.30 / EV_EBITDA 0.20 / PEG 0.10. EV/EBITDA can never be DEFINED (no balance sheet at the provider), so a loss-making company (PE `NOT_APPLICABLE`, 222 today) can never publish even with PB and PEG. Options: keep (loss-makers unpublished is defensible) or `valuation-v2` renormalising the floor over metrics the provider can supply. Not changed without a rule-version decision. |
| **Q-43** | `DONE` (2026-08-31, specs/007 T048) | High | `CONFIRMED` (owner test: MBB "giải thích định giá" answered that the classification was not supplied) | The FE sent the AI only `PE`/`PB`/`DIVIDEND_YIELD` values as evidence — no label, score, confidence, basis, percentiles or disclosure codes — so a faithful model could not explain the result. Numbers were correct (P/E 5.2, P/B 1.27, yield 1.19 % annual). Fix: `explain-evidence.ts` builders for valuation and signals; prompt names the result factor. Second owner test then hit the Q-21 numeric guard: the model rounds restated figures (78,47 % → 78,5 %, 0,5714… → 0,57) and the guard demanded exact digit strings → `fabricated_numbers` now accepts a reading within half a unit of its last decimal of some evidence figure and small counting integers (≤ 10); a genuinely new figure (15 %, 27.500 ₫) still fails. AI 87/87. |
| **Q-44** | `DONE` (2026-08-31, specs/007 T049) | High | `CODE-READ` + owner symptom | Backend tool payloads fed to the AI were wrong or empty: withheld valuations reported as `FAIR_VALUE`; `PE_RATIO`/`PB_RATIO`/`REVENUE_GROWTH` aliases never matched catalog codes (fields always null); unavailable price sent as `"0"`; technical picked an arbitrary first signal and sent reason codes instead of risk values; no score/confidence/percentiles/reason codes anywhere. Fixed with additive DTOs + contract; AI dispatch made concurrent and synthesis prompt switched to JSON. Tool selection itself (Gemini native function-calling behind allowlist/schema validation, claim-tag verification on synthesis) was reviewed and is sound. Owner VIC test after the fix: 19/19 figures verified against the DB; added `priceTradingDate` + `inputBasis` to the valuation payload and grouped verified claims per sentence in the UI. |
| **Q-45** | `DONE` (2026-08-31) | High | `MEASURED` (independent recomputation: NBW MA20 33,750 stored vs 32,133 from current bars; 63 instruments with ≥ 250 bars still `INSUFFICIENT_HISTORY`; 3,150 current results older than their bars) | `TechnicalIndicatorWarmupService` skipped an instrument whenever the latest result date ≥ latest bar date — blind to a provider **correction** of an old bar (NBW 2026-07-02 close 33,400 → 31,800, revision 2) and to a history **backfill** (Q-37 added 2023 bars; instruments crossed the 250-bar floor). Fix: also recompute when any current bar was accepted after the latest result (`findLatestAcceptedAtByInstrumentIdIn`). Takes effect on the next warmup; until then 5 instruments show stale MA/RSI and 63 show INSUFFICIENT where indicators are now computable. |
| **Q-46** | `DONE` (2026-08-31) | Low | `MEASURED` | `EPS_TTM` silently fell back to the provider's `TRAILING_EPS` when quarterly EPS was absent — not only banks/securities: also HPG, VIC, PVS, GVT, KMR in the sample (KBS reports `earning_per_share_vnd` for a minority of industrials); value correct, basis undisclosed. Now labelled `PROVIDER_TRAILING_EPS` (specs/010 research R-005 amendment). Follow-up the same day: the persisted `fundamental_summary_metric` rows were only rewritten when the contributing report set changed, so a calculator change could never reach the screener/verifier tables; `persistSummary` now also compares the metric rows (`metricsUnchanged`). Also: under an annual TTM basis `labelAnnualBasis` overwrote the `PROVIDER_TRAILING_EPS` label with `ANNUAL_BASIS` (a provider trailing figure is not an annual sum); an already-disclosed label is now kept. Label reaches the DB on the next `-WarmupOnly -ForceWarmup` (Q-48). |
| **Q-47** | `DONE` (2026-08-31) | Medium | `MEASURED` (BVH own-history PE percentile 43.08 vs 43.81 depending on row order) | `FundamentalSummaryCalculator` picked the "newest report" by `period_end` only; an annual report and its Q4 tie, and the winner depended on repository row order (BVH FY2025 vs 2025-Q4 carry different provider `TRAILING_EPS`). Deterministic tie-break added: period end desc → QUARTER before ANNUAL → report id (specs/010 research R-005 amendment (b)); verifier mirrors it. Affects only instruments without quarterly EPS at dates where the annual/Q4 pair is the newest visible report; assessments recompute on the next `-WarmupOnly -ForceWarmup` (Q-48). |
| **Q-48** | `DONE` (2026-08-31) | Medium | `MEASURED` (`valuation_warmup total=1524 succeeded=0 skipped=1524` right after the Q-46/Q-47 calculator changes) | `ValuationWarmupService` skipped every instrument "already assessed today" — a calendar rule, blind to bars/reports imported later the same day (same defect class as Q-45) and to a calculator fix within the same rule version. Fix: skip only when assessed today **and** no current bar or report was accepted after the assessment's `calculated_at` (`inputsRevisedSince`); new owner switch `-ForceWarmup` (`FINVERA_STOCK_VALUATION_WARMUP_FORCE`) recomputes everything once. Technical warmup already recomputes on revised bars; a technical calculator change is rolled out by a rule-version bump. |
| **Q-49** | `DONE` (2026-08-31) | **High** | `MEASURED` (Feature 015 first capture: every VALUATION tool call → HTTP 401; backend log `cannot execute INSERT in a read-only transaction` in `ValuationService.persistAssessment`) | `ToolDelegateService` was `@Transactional(readOnly = true)` at class level while valuation/fundamentals/technical services materialise a revision chain on read. Read-only + Hibernate MANUAL flush: the fundamentals tool "succeeded" but silently dropped its writes; the valuation tool's `saveAndFlush` failed. The 500 was forwarded to `/error`, which sits behind the OWNER rule, so finvera-ai received **401 AUTHENTICATION_REQUIRED** — the AI Analyst could never answer a valuation question, and the real cause was only in the server log. Fix: writable `@Transactional` on the three materialising tools; `ProblemDetailsAdvice` catch-all 500 `SERVER_ERROR` + `ResponseStatusException` passthrough (specs/015 research R-001). Verified: `/internal/v1/tools/stocks/VNM/valuation` → 200 FAIR_VALUED. |
| **Q-50** | `DONE` (2026-08-31) | High | `MEASURED` (Q04 TECHNICAL, Q08 SCREENING, Q10 PORTFOLIO: tool SUCCEEDED, answer refused) | Offline templates cited `signal.direction` (payload has `signals[]` since T049) or produced no claims (SCREENING, PORTFOLIO) → `verify_attribution` refused with 0 surviving claims. Fix: templates with verifiable claims for every tool; `get_nested_value` supports `list[i]` / `.length`; honest "no positions" sentence. |
| **Q-51** | `DONE` (2026-08-31) | Medium | `MEASURED` (finvera-ai log `429 RESOURCE_EXHAUSTED … limit: 5`; answers like `17300.000000 (-0.574713%)` served as if from the model) | Gemini free tier is 5 req/min; the service fell back to keyword planning + templates **silently**. Fix: one retry honouring the provider's `retryDelay` (≤ 60 s); `OFFLINE_TEMPLATE_DISCLOSURE` sentence; `synthesisMode`/`plannerMode` on the final event (OpenAPI internal+public); FE "[Chế độ suy giảm]" notice; vi-VN formatting in templates. |
| **Q-52** | `DONE` (2026-08-31) | Medium | `MEASURED` (Q01 "Giá cổ phiếu VNM hôm nay" on a 28/08 close flagged DELAYED; Q05/Q07 never worded `ANNUAL_BASIS` / `PROVIDER_TRAILING_EPS`) | Synthesis rules 8–10: state the data date/status (no "hôm nay" unless tradingDate is today), word every basis/withhold reason, no inferred "đang lỗ/có lãi"; templates carry `theo phiên <date>` and the basis phrases. |
| **Q-53** | `DONE` (2026-08-31) | **High** | `MEASURED` (214 `fundamental_summary` revisions for 105 instruments in one hour after Q-49 made the tool transactions writable; MBB 5×; bank valuation tool > 10 s → dispatcher TIMEOUT) | The Q-46 follow-up `metricsUnchanged` compared calculator values at scale 12 with the `numeric(28,6)` column ("-2.7735753908" ≠ "-2.773575"), so every read re-persisted a summary — invisible while the tools ran read-only (writes silently dropped), a write storm once they could commit; peers' re-persist on every valuation read is also what made bank valuations slow. Fix: `metricRowKey` rounds both sides to the persisted scale; test proves two more reads create no revision. Junk revisions are superseded rows — `.\refresh-data.ps1 -CleanupOnly` removes them. |
| **Q-54** | `DONE` (2026-08-31) | Medium | `MEASURED` (SCREENING tool → 500 `IllegalStateException: Duplicate key` in `ScreenerService.fetchFundamentalMetrics`; KLB had two revisions with the same `calculated_at` from two concurrent valuation reads) | `Collectors.toMap` without a merge function crashed the whole screen (public screener too) when sibling revisions tie on `calculated_at`. Fix: deterministic merge (smallest id). Root cause of the siblings is Q-53's churn under concurrent dispatch. |
| **Q-55** | `DONE` (2026-08-31) | **High** | `MEASURED` (valuation tool: MBB 8.5 s, VCB 8.0 s, HPG 17.7 s with sector basis on — past the AI tool timeout of 10 s; after the fix MBB 0.42 s, VCB 0.20 s, HPG 0.25 s, same classifications/scores) | `buildSectorSeries` loaded each peer's **full bar history** (~900 rows) to read one close and recomputed each peer's fundamental summary report-by-report. Now two bulk reads: latest current bar per peer (`findLatestNCurrentByInstrumentIdIn(ids, 1)`) and the peers' persisted current summaries (`fundamental-summary-v2` revision chain; fallback to computing when none). Contract `valuation-v2` amendment 2026-08-31 (Q-55). AI tool timeout 10 → 20 s, ask budget 30 → 60 s as headroom (finvera-ai settings). Also fixes the public stock-detail valuation latency for sector-classified symbols. |
| **Q-56** | `DONE` (2026-08-31) | Medium | `MEASURED` (online capture with the new key: "[T2:metrics[0].ownHistoryPercentile=32.87]" and a stray "]" left in two answers) | The citation-tag regexes accepted only `[\w.]+` field paths, while `get_nested_value` (Q-50) now resolves list steps, so the model's `metrics[0].…` tags were neither extracted as claims nor stripped from the text. Fix: `TAG_FIELD` shared by extraction and stripping (`[\w.]+(?:\[\d+\][\w.]*)*`). Same capture: 4 of 20 provider calls answered `503 UNAVAILABLE high demand` → one 5 s retry added. Operational note (not a code defect): the owner's new provider project no longer serves `gemini-2.5-flash` (404 "no longer available to new users"); `GEMINI_GENERATION_MODEL` switched to `gemini-3.6-flash` in `.env`, `.env.example` and the settings default. |
| **Q-57** | `DONE` (verified on the post-crawl database 2026-09-04: fundamentals are **21,446 current reports, 100 % `VNSTOCK_VCI`, zero current `VNSTOCK_KBS` rows**, and `verify_calcs.py` reports **664 checks / 0 failures** including all 21 audited-figure anchors — the mirrored-year defect cannot survive an anchor check; code 2026-08-31, ADR-0011 approved) | **Critical** | `MEASURED` — raw KBS pages probed (owner-authorized) and judged against a second source (VCI, vnstock 4.0.7) and audited figures: **yearly** income/cash-flow pages carry FY2022 under `2025-Năm` … FY2025 under `2022-Năm` (mirror); **quarterly** pages are permuted — DB label 2025-Q3 = actual 2026-Q2, 2025-Q4 = 2025-Q1, 2026-Q1 = 2025-Q4, 2026-Q2 = 2025-Q3 — **20/20 symbols, 0/4 labels correct**; quarterly ratio labels are shifted (yearly-2025 BVPS/trailing EPS sit under `2026-Q1`). KBS's own `Head` (YearPeriod, ReportDate) is right; the `Content` of each page is another period's — a provider defect vnstock's own source flags ("KBS bug: duplicated IDs and mixed-up values"). | Consequences: every fundamentals fact in the database except the yearly ratio frame is attached to the wrong period: quarterly TTM windows are not contiguous (VNM EPS_TTM 4,350 vs true 4,728), annual metrics are mirrored, growth/`ANNUAL_BASIS`/own-history valuation points are wrong. Prices, indices (year-end anchors exact), breadth, technical indicators and the yearly ratio frame are verified. Exporter 0.7.0 mirrors yearly labels (contract `kbs-yearly-statement-orientation-v1`) — correct for the yearly frames but **insufficient**: quarters cannot be relabelled by rule. **Decision (ADR-0011, owner-approved): all fundamentals now come from VCI statements (Feature 018)** — `export_fundamentals_vci.py` (contract `vci-fundamentals-v1`: company-type mapping, per-period shares from paid-in/charter capital at par, derived TRAILING_EPS/BVPS/ROE/ROA/margins/D-E/FCF/EBITDA with rule ids), ingestion rule I-1 (`SOURCE_SUPERSEDED`: VCI rows retire KBS rows per period), crawl switched, vnstock 4.0.7. Anchors: audited FY figures, FY2025 = Σ quarters to the VND, shares = profile. **Owner action: the plain `.\refresh-data.ps1` (no flags) — first VCI pass ≈ 6 h (6 calls × 1,522 symbols at ~2.3 s; daily bars stay incremental); the import stage itself retires every current statement row not from the primary source (`SOURCE_RETIRED`, idempotent) so KBS rows VCI does not replace (annual-only symbols such as A32/ACE/BCP) never stay current; then `python tools/verification/verify_calcs.py` — the `anchors` section must show 0 diffs and 0 current `VNSTOCK_KBS` rows.** Owner re-export note (this sentence was split from the row by the same typo; rejoined 2026-09-06): `.\refresh-data.ps1`** — the exporter's `toolVersion` bump (0.7.0) makes it re-export every symbol's fundamentals (≈ 1,522 × 6 provider calls at 60/min ≈ 2.5 h; daily bars stay incremental) — after which restatements (`CORRECTED`) flow through summaries/valuations via the warmup; then re-run `verify_calcs.py` and `history_basis_study.py`. |
| **Q-58** | `DONE` (2026-08-31, specs/018 research R-008, tests in `test_export_all_symbols.py`) | High | `MEASURED` (owner's crawl log + checkpoint) | `export_all_symbols.py` recorded a dropped connection (`DCH daily_bars: FAILED (ValueError)` ← `ConnectionResetError 10054` wrapped by vnstock) as a **settled** failure, never retried without `--retry-failed`; the same checkpoint held 153 `fundamentals: failed:ValueError` entries written by the aborted first VCI run (before `NoStatementsAvailable` existed) and 51 old `daily_bars` failures — all skipped forever, i.e. silently missing data. Fix: F-1 `classify_failure` walks the exception chain and message → `NetworkError`, retried in-run (5 s, 20 s) then recorded as transient; F-2 every recorded failure carries `<key>_failed_tool_version` and settles only for the exporter version that produced it (pre-existing entries are retried once). 63/63 exporter tests. Owner action: none — the next plain refresh retries ≈ 200 unsettled entries (~25 min). |
| **Q-59** | `DONE` (2026-08-31, migration V016, Feature 021) | Medium | `LATENT` (surfaced by the Feature 021 test suite) | `StockHistoryImportService` has accepted `adjustmentStatus = PROVIDER_ADJUSTED` in bar packages since Feature 011, but the V003 check constraints on `equity_daily_bar` and `technical_indicator_result` only allowed ADJUSTED/RAW/NOT_APPLICABLE/UNKNOWN — any package honestly labelled PROVIDER_ADJUSTED would have died at the INSERT. Fix: V016 extends both checks; `StockTypes.AdjustmentStatus` gains PROVIDER_ADJUSTED; `StockChartAssembler` serves a uniform provider-adjusted series as-is under its honest label (mixed windows still disclose `ADJUSTMENT_BASIS_UNAVAILABLE`); chart/technical/ingestion mappings and the FE type/labels follow. |
| **Q-60** | `DONE` (found 2026-09-06 while measuring P2-11, specs/024 R-005; fixed and verified the same day by Feature 025, contract `fundamental-summary-v3`) | **High** | `MEASURED` on the live database | `FundamentalSummaryCalculator` takes the four newest QUARTER reports whenever four exist, without checking they are contiguous or newer than the available annual report. Of 779 instruments whose served `EPS_TTM` is a quarter sum, 8 sum non-contiguous quarters and 19 sum quarters older than that instrument's own newest annual report - **24 distinct LISTED instruments**, staleness up to **2,557 days** (KHD/SDY/SD7 are served a TTM built from 2018 quarters while their FY2025 annuals are current in the same table). **SDY is served EPS_TTM -1,680, so P/E and PEG are withheld as lossmaking, while its FY2025 report shows +1,418.** `basis_period_label` says `2025` and `data_status` says `DELAYED`, so nothing contradicts the number. None of the 24 has a published valuation classification today, but their EPS_TTM is shown in the fundamentals section, handed to the analyst tool, and enters the sector cross-section of **891 other instruments**. Fix (needs a `fundamental-summary-v3` contract): four quarters qualify only when contiguous and not older than the newest annual report; otherwise fall back to annual with `ANNUAL_BASIS`, as the "fewer than four quarters" branch already does. **Implemented 2026-09-06 (specs/025)**: `quarterWindowEligible` enforces E-1 (four/eight consecutive fiscal quarters, decided on `fiscalYear*4+fiscalQuarter` indices, not day spans) and E-2 (no annual report ends later than the newest quarter in the window); rejection falls back to the annual figure with `ANNUAL_BASIS` plus the new summary reason `QUARTER_WINDOW_INELIGIBLE`, or withholds the aggregates when no annual exists (1 instrument, PLO). The eight-quarter growth window is guarded the same way (20 non-contiguous + 23 stale windows). Scope re-measured while fixing: **60 instruments**, not the 24 first counted - the same window also feeds NET_PROFIT_TTM (43), REVENUE_TTM (43), EBITDA_TTM (30) and DIVIDEND_PER_SHARE_TTM (29). `FundamentalSummaryTests` 34/34 incl. a v2-parity vector for the 1,139 sound windows; FE 158/158; verifier recomputes E-1/E-2 independently. Activates on the owner's next `.\refresh-data.ps1` (summaries recompute on read, so no gap); then re-run the scope probe (zero flagged windows expected) to close. **VERIFIED after the refresh (specs/025 R-007)**: on the 60 affected instruments the count of quarter-summed, unlabelled aggregates is **0** for all five metrics (was 43 profit / 43 revenue / 30 EBITDA / 29 DPS / 24 EPS); exactly 60 summaries carry `QUARTER_WINDOW_INELIGIBLE` - the rule fires on the intended population and nowhere else; the 1,139 sound windows are untouched. SDY now serves EPS_TTM **+1,418** instead of **-1,680** (its P/E is no longer withheld as lossmaking); PLO, the one instrument with no annual report, withholds its aggregates rather than summing scattered quarters. `verify_calcs.py`, recomputing E-1/E-2 independently: **784 checks, 0 failures**. |
| **Q-61** | `DONE` (found and fixed 2026-09-07 while reviewing the owner's crawl output; specs/026 R-008, T012) | **High** | `MEASURED` (checkpoint scan + live provider probes) | `export_daily_bars.build_package` raised a **bare `ValueError`** when a symbol had fewer than `MIN_RECORDS = 20` completed sessions, and `export_all_symbols.classify_failure` recorded that as `failed:ValueError`, which `settled_failure` treats as **permanent**. A symbol crawled while it was still too newly listed was therefore locked out of the daily-bar pipeline **forever** - no chart, no technical indicator, no valuation - and was never asked again as it accumulated sessions, because `daily_bars_failed_tool_version` still matched the current exporter version. Measured on the 2026-09-07 checkpoint: **48 symbols** hold `failed:ValueError` (46 UPCoM, 2 HOSE), **42 of them settled on 2026-09-01 and never retried since**; 20 of the 48 have `fundamentals: done`, i.e. the company files statements but has no price series at all. Direct provider probes proved both halves of the defect: **DMX exported cleanly (20 sessions, 2026-08-06 -> 2026-09-07) while its 2026-09-01 failure still stood**, and **LPS returned 12 sessions** - it crosses the threshold within weeks and would have been locked out identically. This is the same shape of fact the code already handles correctly for `NoStatementsAvailable` ("not a defect of the symbol but a state that changes when the company files"). **Fix**: `InsufficientSessions(ValueError)` raised with the actual session count, and a `RECHECK_DAYS_BY_FAILURE` map giving it a **7-day** window (20 sessions ~ 4 trading weeks) beside `NoStatementsAvailable`'s 35. It is recorded, not retried in-run: waiting 65 s does not make a session appear. **Verification**: exporter suite **94/94** (was 92 - one test on the `MIN_RECORDS` boundary and the new classification, one on the per-class re-check windows); re-probing LPS against the live provider now raises `InsufficientSessions: LPS: 12 completed sessions available, at least 20 are required`. **Owner action**: the new class only governs failures recorded from now on, so one `--retry-failed` run re-opens the 48 entries already settled (~880 provider calls, ~22 min). |
| **Q-62** | `TODO` | Medium | `MEASURED` (2026-09-07 checkpoint + scan of every `daily-bars-*.json` on disk) | `daily_bars_current` requires `latest_record_date >= args.end`: the symbol must have a bar **on the run's end date**. A delisted, suspended or simply untraded symbol can never satisfy that, so it is never `is_finished`. **603 of 1,522** symbols hold a complete `done` daily-bar package whose newest session predates `--end` - ART 2022-11-18, TTZ 2022-12-02, NDF 2023-02-24, TTB 2023-07-06 - against 870 packages that do carry the current session. Consequences: the run reports `Checkpoint total attempted: 889/1522` instead of converging; **every** subsequent run re-fetches those 603 symbols (~1,200 provider calls, ~30 min at the 40/min ceiling); and the exporter's own closing line "it exits immediately once nothing is left" can never become true - which contradicts Feature 026's stated goal. No stored data is wrong: this is a completeness/convergence defect, not a correctness one. **Candidate fix**: settle on "the provider was asked on `--end`" (compare a recorded `daily_bars_checked_at` with `args.end`) rather than "the symbol traded on `--end`", which keeps freshness for actively traded names while letting a stopped symbol settle. **SDD home**: `specs/026-refresh-automation`. |

---

## Independent recomputation 2026-08-31 (owner request: "data must be clean before anything else")

Method: `tools/verification/verify_calcs.py` — textbook formulas in plain float arithmetic (not the Java code, not the contract text) over 24 instruments (VNM, MBB, SSI, BVH, HPG, VIC, FPT, ACV, PVS, VCB, MWG, GAS + 12 random with ≥ 300 bars) plus a provider re-fetch for 4 instruments.

| Layer | Checks | Result |
|---|---|---|
| Provider → DB (income statement, ratio, bars for VNM/MBB/BVH/SSI: EPS ÷1000, net profit, revenue incl. insurance/securities ids, BVPS, trailing EPS, ROE, dividend yield, OHLCV ×1000) | 74 | **74 exact** |
| Technical indicators (MA20/50/200, RSI14, MACD line/signal/hist, ATR14 + %, BBANDS ×4, AVG_VOLUME20, RELATIVE_VOLUME) | ~230 | exact to 1e-6 for every instrument whose bars were not revised/backfilled after the last warmup; NBW/BTW exposed **Q-45** |
| Fundamental summary v2 (EPS/NET_PROFIT/REVENUE TTM, EPS & revenue growth incl. `ANNUAL_BASIS`) | ~120 | exact; the only "difference" was the undisclosed trailing-EPS fallback → **Q-46** |
| Valuation v2 (PE, PB, PEG, NOT_APPLICABLE cases, own-history PE percentile rebuilt from scratch with the observed_at visibility rule, band from score, displayed score) | ~130 | exact (percentiles within 0.01) |
| Breadth 2026-08-28 (advance/decline vs prior close over the 1,524 current profiles; universe = instruments with status ACTIVE or UNKNOWN, as `HistoricalMarketBreadthReconciliationService` — the KBS listing exporter deliberately writes `UNKNOWN`) | 1 | **326/375/243/580/1,524 exact** |

### Post-warmup re-verification 2026-08-31 15:14 (owner ran `.\refresh-data.ps1 -WarmupOnly`)

| Check | Result |
|---|---|
| Technical warmup | `total=1524 succeeded=350 skipped=1174 failed=0` — the 350 are exactly the instruments with a bar accepted after their last result (Q-45 rule). |
| NBW (provider-corrected 2026-07-02 bar) | MA20 32,133.5 / RSI14 48.328522 — equal to the independent recomputation (was 33,750 / 48.329238). |
| Instruments with ≥ 250 bars but RSI14 `INSUFFICIENT_HISTORY` | **0** (was 63). |
| Current results whose `calculated_at` is older than their newest bar | 230 — all instruments whose recomputed indicators were **identical** to the stored row (the 2023 backfill re-imported the same bars): `TechnicalIndicatorService` persists idempotently and keeps the old revision (DATA-006). They will be re-evaluated on each warmup until a new bar arrives (~230 × one `findBySymbol`, seconds). Not a defect. |
| Verifier re-run (610 checks) | Technical, valuation PE/PB/PEG/bands, breadth: **0 differences**. Remaining 9: 8 × `EPS_TTM` where value matches but the stored `quality_reason` is still empty (label re-persist needs the next warmup — Q-46 follow-up), 1 × BVH percentile (Q-47). |
| **Final run 15:55 after `-WarmupOnly -ForceWarmup`** (`valuation_warmup succeeded=1524 skipped=0`) | **619 checks, 0 differences** — every `EPS_TTM` fallback now carries `PROVIDER_TRAILING_EPS`, BVH own-history percentile 43.076 = independent value, breadth exact. |
| Breadth 2026-08-28 | 326/375/243/580/1,524 exact under the correct universe (the first verifier draft filtered `status='ACTIVE'` and reported 0/0/0/0/0 — a tool bug, not a data one; the number recorded above came from a direct SQL check). |


Contract review (financial correctness, not just code-vs-contract): MA, Wilder RSI/ATR (SMA seed, 250-bar window), SMA-seeded EMA MACD 12/26/9, population-σ Bollinger 20/2, relative volume vs prior-20 average, PE/PB/PEG/dividend-yield definitions, mid-rank percentile, advance/decline vs previous close, strategy levels (±0.25/2/4/6 ATR, R:R 2.0) all match standard definitions. Judgement parameters (bands 35.5/64.5, confidence weights, regime factor weights) are documented choices, not errors. One methodological caveat recorded, not changed: `EPS_TTM` as the sum of four reported quarterly EPS assumes a stable share count (VNM 4,350 vs provider trailing 4,159.65); both are defensible, ours is the statement-derived one.

## Phase 2 — remaining options (planned 2026-08-31, nothing started)

Everything below is optional: no open item is a known defect. Each entry is a
self-contained SDD feature (spec → research → plan → contracts → tasks) so it
can be picked up independently. Order is my recommendation; effort is
wall-clock for one person, excluding owner-run refreshes.

| ID | Feature dir | Scope | Method (evidence first, code second) | Acceptance | Effort | Depends on | Status |
|---|---|---|---|---|---|---|---|
| **P2-01** | `013-tcbs-live-field-audit` | Re-audit the TCBS live path (`market/provider/tcbs`, `stock/provider/tcbs`, contracts `tcbs-thesis-live-overlay.md`, `tcbs-iflash-adapter.md`, `stock-data-provider.md`) with the same field-by-field method used for KBS in Feature 011: WS frames `s\|4` (ceil/floor/ref), `s\|6` (trades), `s\|8` (index), REST `tickerCommons`. | Owner-operated read-only capture of 1 session (~5 min of frames) for VNM/MBB/ACV + VN-Index; dump every field per frame type; compare against `TcbsThesisFrameMapper` / `TcbsLiveEquityQuoteService` mappings: unit (VND vs ×1000), reference price semantics, volume (shares vs lots), timestamp zone, session-state codes, index level/reference. Pin a frame fixture (`tests/…/tcbs-frame-fixture.json`) like `provider_schema_fixture.json`. | Every consumed field has a documented unit and a fixture test; any mismatch fixed with a rule id; live overview vs EOD close reconciled for 3 symbols within contract tolerance. | 1 day (+ one owner capture during a session) | none | **DONE 2026-09-04** — full audit (specs/013: spec/plan/tasks/research). Code-vs-contract: conforming, **no defect**. Two owner-run live captures (11:06 and 14:44 ICT) anchored every consumed field against an independent provider (VCI) and against label-free arithmetic identities: stream `matchPrice` and all four index levels SAME_UNIT (R-004), all 21 `tickerCommons` price fields SAME_UNIT with ceil/floor on the venue price-limit band (R-010), and live-vs-EOD exact on price *and* volume for VNM/MBB, ACV +0.24 % explained by UPCoM's 15:00 close (R-011). Contract `tcbs-thesis-private-live-v1` amended in place (version unchanged — clarifying only): per-field unit/type tables for `s|6`/`s|8`/`tickerCommons`, the reference-price chain, server control frames `d|33`/`d|34`, and the RFC-ping prohibition (the server closes with 1002 — R-009). Fixture `fixtures/market/tcbs/tcbs-frame-fixture.json` + `TcbsThesisFrameFixtureTests` 7/7 pin it; backend 705/705. Evidence (gitignored, local): `tools/verification/out/tcbs_live_capture_2026-09-04T{1106,1444}.json`, reproducible with `tools/verification/tcbs_live_capture.py`. |
| **P2-02** | `014-reason-code-presentation` | FE renders reason codes as raw strings in several places (`market-overview/*`, `portfolio/holdings-table.tsx`, stock detail). New codes from Features 010–012 (`ANNUAL_BASIS`, `REDUCED_METRIC_SET`, `SHARES_OUTSTANDING_UNVERIFIED`, `kbs-*` rule ids, `TTM`-basis ROE) need human wording. | Inventory every reason/quality code the API can emit (grep contracts + Java constants); one label map per feature (`format/*-format.ts`) with Vietnamese wording + a fallback that shows the raw code; snapshot tests for the map's completeness against the OpenAPI enum lists. | No raw `SNAKE_CASE` code visible in the UI for any code in the contracts; unknown code still visible (never hidden). | 0.5 day | none | **DONE 2026-08-31** — `src/shared/format/reason-codes.ts` (114 codes, contract `reason-code-presentation-v1`), `<ReasonCodes>` keeps the code as `data-reason-code`/`title`; 20+ render sites routed; four `statusLabel` copies removed; explain evidence hands the AI every engine note; `finvera-ai` offline sentence worded. FE 145/145, AI 90/90. |
| **P2-03** | `015-analyst-e2e-on-real-data` | The AI/Analyst path (`finvera-ai/app/features/{analysis,orchestration,rag,chat}`, backend `/internal/v1/tools`) was hardened in Group E but never exercised end-to-end on the post-refresh database (ROE now TTM, growth `ANNUAL_BASIS`, valuation v2 codes). | Owner runs 10 scripted questions (screen by ROE, explain VNM valuation, compare MBB/VCB, a loss-maker, an UPCoM name with no sector, a bank without revenue); capture tool calls + answers; check each number against the DB and that every `ANNUAL_BASIS`/`REDUCED_METRIC_SET` fact is attributed (`explain.py` honest attribution); add these as golden tests in `finvera-ai` with recorded tool responses. | 10/10 answers numerically faithful and basis-disclosed; golden tests green. | 1 day | P2-02 optional | **DONE 2026-08-31** — specs/015: six captures on the real stack (research R-001…R-006); 7 defects fixed (Q-49…Q-56); online run 10/10 faithful (0 unsupported numbers, 0 basis misses, 0 refusals); golden replay tests (40) in finvera-ai; `tools/verification/analyst_e2e.py` kept for the owner. |
| **P2-04** | `016-import-warmup-incrementality` (Q-41) | Stage 6 re-reads every bar package in full; valuation warmup recomputes every instrument. | (a) `StockIngestionService` daily-bar import: skip records older than `lastAcceptedDate − lookback` per instrument (one indexed query) — keeps the 90-day correction window; (b) `ValuationWarmupService`: skip when latest bar id, latest summary id and profile revision match the stored assessment's `valuation_assessment_input` links. Measure stage 6/7 durations before/after from the backend log. | Stage 6 ≤ 5 min and stage 7 ≤ 15 min on a no-change day; identical assessment rows (replay determinism test) | 1 day | do only if stage 6/7 > 40 min in practice | not started |
| **P2-05** | `017-history-basis-consistency` | Own-history PE/PB mixes current quarter-TTM inputs with annual-basis historical points (research 012 R-002 caveat). | Research only first: measure for 20 symbols how much the annual-basis PE differs from a quarter-TTM PE on the same date where both exist (2025-Q4 vs FY2025); decide whether to (a) label history points with basis and exclude mixed comparisons, or (b) accept with a disclosure code. No code until measured. | Decision recorded with numbers; if (a), contract `valuation-v3` | 0.5 day research | none | **DECIDED + IMPLEMENTED 2026-09-05** (Feature 023, contract `valuation-v3`). Re-measured on the VCI universe (specs/017 R-004/R-005): |PE_annual/PE_quarter − 1| median 7.6 %, p90 31.1 %, max 73.4 %; percentile shift up to |58.3| pp (VCB); 468/1,522 instruments have no quarterly EPS and were already consistent, the other 1,054 carried the bias. Owner chose **(b)**: Basis A ranks a fiscal-year-basis comparison value against a fiscal-year-basis series for flow metrics (PE, PEG, EV/EBITDA, PS, yield); PB unchanged (`LATEST_REPORT`); headline values stay quarter-TTM; each metric row exposes `ownHistoryBasis` + `ownHistoryComparisonValue`; reason codes `HISTORY_FISCAL_YEAR_BASIS` / `HISTORY_COMPARISON_UNAVAILABLE`; V019; OpenAPI additive; FE accepts v2 and v3. Suites: ValuationV1 33/33, FundamentalSummary 27/27, ValuationService 6/6, backend full 713/713, AI 152 passed, FE 158/158 + lint/build. **ACTIVATED 2026-09-06** by the owner's refresh: 1,522 assessments under `valuation-v3`, 1,076 published, **0 contract-invariant violations**. VNM shows the correction concretely - headline P/E 13.09 (quarter-TTM), value actually ranked 15.37 (fiscal-year), own-history percentile **14.2 -> 66.1**. Across the 899 instruments with a P/E percentile under both rule versions: median |shift| **7.93 pp**, p90 46.2, max 96.1, **45 % moved more than 10 pp**. `verify_calcs.py` 784 checks / 0 failures. The planned classification-drift count (quickstart §5) was **not computable** - the surviving v2 rows come from the 2026-09-01/02 stale-price snapshot where only 2 of 1,524 published - so the percentile comparison replaces it (specs/023 R-011). SC-5 met. Follow-up: **P2-11** (sector basis). |
| **P2-06** | G-11 (question) | KBS "Tăng trưởng doanh thu thuần" ≠ statement arithmetic (VNM +3.02 % vs −0.7 %). | Owner supplies VNM's audited FY2025 revenue (or one other independent source); compare with the `2025-Năm` column to learn what KBS's annual column really is. Not blocking: Finvera uses statement arithmetic, not KBS growth. | Column semantics documented in 011 research | 1 h | owner input | **RESOLVED 2026-08-31** by Q-57: KBS's growth row was right; our statement arithmetic used mirrored years. |
| **P2-07** | Provider tier decision | Community tier: 60 req/min, 4 fiscal periods, no balance sheet (balance sheet and UPCoM sectors now come from VCI — Features 018/020). | Owner decision, not engineering: a Sponsor tier (180–600 req/min) cuts a full re-export from ~8 h to ~1–2 h and may expose more periods; a second provider would be an ADR (`docs/adr`) with a new field-by-field audit (Feature 011 method) before any mapping. | ADR written if pursued | — | owner | **DECLINED 2026-08-31** by the owner during Feature 022 (community tier kept; pacing already sized for it). Revisit only if a re-crawl's wall-clock becomes a real constraint. |
| **P2-08** | `020-vci-icb-sector-reference` | UPCoM (820 instruments) had no sector basis; KBS taxonomy proprietary, HOSE/HNX only. | Probe VCI ICB coverage over the universe (L2 vs L3), anchored exporter, unchanged import contract, verifier gate. | ≥ 95 % of LISTED profiles under `VCI_ICB_L3`; UPCoM stocks show sector, percentile and peers. | 0.5 d | Feature 018 (vnstock 4.0.7) | **CODE DONE** 2026-08-31 — activates on the owner's next refresh (Q-38) |
| **P2-09** | `022-price-based-metrics` | DIVIDEND_YIELD / PS / BETA went dark with the KBS ratio frame. | Owner scope decision 2026-08-31: restore yield (exporter 1.2.0 derives DIVIDEND_PER_SHARE from cash dividends -> existing summary/valuation chain) and add PS as an informational valuation metric (weight 0, prices loss-makers; V017); **BETA deferred deliberately** (no anchored definition adds decision value; "khong du thua"). | Yield + PS visible with rule ids and disclosures after the crawl; verifier recomputes both. | done | Features 018/019 | **CODE DONE** 2026-08-31 (SC-4 post-crawl) |
| **P2-10** | `021-vci-single-provider` (ADR-0013) | Owner directive 2026-08-31: standardize daily bars, index history, instrument reference and equity profiles on VCI (fundamentals/sectors already are). | Probes done (specs/021 research R-001…R-005: closes tick-equal, ≤0.22% adjustment-rounding on history, 8-year depth, UPCOMINDEX, issue_share). Per-dataset contracts + anchored exporters + full re-crawl; never mix KBS history with VCI increments. | verify_calcs green on VCI bars; single provider in the nightly refresh; KBS kept as documented fallback. | 1–2 d + re-crawl | Features 018/020, Q-58 | **DONE 2026-09-04** — verified on the post-crawl database: daily bars **1,744,688 rows, 100 % `VNSTOCK_VCI`** (zero current KBS), index snapshots **7,640 rows, 100 % VCI**, profiles **1,522, 100 % VCI**, fundamentals **21,446, 100 % VCI**; `verify_calcs.py` **664 checks / 0 failures** (anchors 21, provider cross-check 26, sector, breadth, summary 132, technical 330, valuation 153). Single provider achieved; KBS remains documented fallback only. |
| **P2-11** | sector-basis-consistency (follow-up of Feature 023) | Basis B (sector cross-section) compares peers' headline multiples, which are quarter-TTM for companies with quarterly EPS and annual for the rest — the same class of mixed comparison Feature 023 removed from Basis A, but *between* companies (specs/023 research R-009). | Research first, code never before measurement: for 5 sectors, recompute each peer's PE on the fiscal-year basis from `fundamental_report` (bulk, Q-55 path) and measure how far sector percentiles move; decide between disclosure and a `valuation-v4` sector rule. | Decision recorded with numbers | 0.5 day research | Feature 023 live (v3 rows exist) | **RESEARCHED 2026-09-06** (specs/024): 26 sectors, 1,190 subjects. Moving the whole cross-section to one fiscal-year ruler shifts the P/E sector percentile by median 6.56 pp (p90 27.25, max 100; ~0.8 point of the 0-100 score at the median). But the mix **disperses without tilting**: quarter-TTM members average percentile 49.9 vs annual members 49.3 today, 49.5 vs 48.1 under a uniform rule - so the directional bias that justified Feature 023 for Basis A does not exist here, and uniform FY is demonstrably worse where the gap is largest (XPH: headline P/E 1.71 on fresh earnings vs 4,833 on an FY EPS of 3 VND, percentile 0 -> 100). **CLOSED 2026-09-06 - measured, no change.** Owner rejected (b) as recommended, and declined the disclosure half of (a) as well: an extra line on every valuation is not worth its space when the effect has no direction and moves the score by under a point at the median ("du va chinh xac, khong du thua"). Basis B is untouched - no code, no contract, no UI. Reopen trigger: `tools/verification/sector_basis_study.py` is kept; if a provider change ever pulls the two groups' mean percentiles apart (specs/024 R-002), the argument that closed this changes and the measurement is one command away. |
| **P2-12** | exporter concurrency (measured 2026-09-06 during the owner's refresh) | `export_all_symbols.py` walks the universe strictly one symbol at a time, so every provider round-trip and every read timeout is paid in series. Measured mid-run: **~199 symbols/hour, ~18 s per symbol**, of which ~2 s is the fixed inter-symbol sleep (`--requests-per-minute 30`) and the rest is waiting on VCI. Provider call rate was **7.3 per minute against a 60/min quota — 12 % of the allowance**, so the run is latency-bound, not quota-bound. Read timeouts (`trading.vietcap.com.vn`, 30 s each, vnstock's default) are retried correctly - only 1 of 1,473 daily-bar symbols settled as RetryError - but the retry ladder (vnai 2 attempts, then our 5 s and 20 s waits, each attempt able to burn 30 s) can cost ~3.5 minutes on one stubborn dataset. | Run N symbols concurrently behind the existing shared quota gate (`wait_for_quota`), so waiting overlaps instead of queueing; 5-6 workers keeps the call rate near 40/min, under the limit. Do **not** weaken the retries - they are what keeps the data complete. Checkpoint writes must stay safe under concurrency. Also: let the crawl tee its output to a log file, so the timeout rate can be counted instead of estimated. | A full crawl finishes in ~1.5 h instead of ~6 h, with the same completeness | 0.5-1 day | none | **IMPLEMENTED 2026-09-06 (Feature 026, specs/026)** - and the review that preceded it found something worse than the slowness: the four short exporters in stage 1 made **7 provider calls with no retry at all**, so with `$ErrorActionPreference = "Stop"` a single 30-second read timeout ended the whole six-hour refresh in its first minute. Surviving stage 1 was luck. Shipped: (a) `provider_retry.py` - one shared definition of "the provider blinked", now wrapping all 7 calls, with the same 5 s / 20 s ladder the crawl already used and no retry on a schema failure; (b) the orchestrator records completed stages in `refresh-state.json` and resumes there, retries a failed stage up to 3 times, fails a silent stage after 15 minutes instead of waiting out a 6-hour timeout, and follows the backend log from a byte offset instead of re-reading it every 3 seconds; (c) `--workers` (default 5) with our own `TokenBucket` ceiling (default 40/min, below the provider's 60 - vnai's counter is mutated from several threads without a lock and can under-count, specs/026 R-003), plus a locked, atomically-replaced checkpoint. Resume never reuses state that is stale (>12 h), from different parameters, or unreadable: it starts clean and prints why. Exporter suite **92/92**. The end-to-end speed-up is measured on the owner's next refresh. |

Recommended order (P2-01, P2-02, P2-03 done): P2-05 → P2-04 only if measured slow → P2-06/P2-07 when the owner has input.

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

**valuation-v2 measurement 2026-08-31 00:05 (owner ran `-WarmupOnly`):** latest per instrument 1,524 — published **701 (46.0 %)**, up from 650 (42.7 %); 305 of them on a reduced metric set (69 with P/E not applicable, 236 with PEG only — the flag was then narrowed to core metrics, see contract v2). v1 rows retained (4,583). SC-002: **0** assessments published with a `MISSING` core metric. Confidence: reduced-set avg 63.6 (45–69) vs full-set 68.4 (49–74). Still withheld among the 888 with a basis: `PRICE_STALE` 123 (no bar for ≥ 2 sessions — illiquid), `INSUFFICIENT_METRIC_COVERAGE` 77 (typically P/E `DEFINED` but with < 500 history points, i.e. a recently profitable company, plus PEG missing growth), `CORE_METRIC_UNAVAILABLE` 4. SC-001 (≥ 50 %) is **not** met and cannot be met by rule: 636 instruments have no comparison basis and 507 have no recent trade; both are data facts, not gates.

**Post-refresh measurement 2026-08-31 (owner ran `-FullRefresh` then `-Cleanup` with history start 2023-01-01):**

| Criterion | Before | After |
|---|---|---|
| Profiles with `shares_outstanding` (010 SC-001 ≥ 95 %) | 0 / 1,524 | **1,522 / 1,524** ✔ (free float never emitted) |
| Valuation published, latest per instrument (010 SC-002 ≥ 50 %) | 7 / 1,524 (0.5 %) | **650 / 1,524 (42.7 %)** — below target; 888 have the own-history basis, 650 of them publish |
| `EPS_GROWTH_PERCENT` DEFINED (010 SC-003 ≥ 60 %) | 0 | **1,225 / 1,523 (80 %)** ✔ (`ANNUAL_BASIS`); 245 NOT_APPLICABLE (prior ≤ 0), 53 MISSING |
| `EPS_TTM` DEFINED | 951 | **1,523** (287 on `ANNUAL_BASIS`) |
| Bars at last session 2026-08-28 (011 SC-001) | max 08-26 | **944 / 1,451**; the 507 behind have no provider bar that day (illiquid; PGI/VCF/OPC files also end 08-27) + 22 rate-limited symbols retried next run |
| `ROE_TTM = 0` from annual reports (011 SC-002) | present | **0** ✔; `DIVIDEND_YIELD` = provider annual (VNM 7.92) ✔; DAN keeps 3 old quarter rows (its re-export failed at the provider) |
| Insurance/securities `NET_PROFIT`/`REVENUE` (011 SC-003) | missing | **BVH, SSI DEFINED** ✔; MBB revenue `NO_DATA` by design |
| Instruments with ≥ 500 bars (Q-37) | 746 | **908**; history_point_count = 750 for 492, ≥ 450 for 931 |
| Annual reports | 0 | 1,522 instruments, 4 fiscal years for 1,502 |
| Regime 2026-08-28 | — | `EARLY_BULL`, `PARTIAL` (580 / 1,524 unclassified = no bar that day; 62 % classified ≥ 50 % floor) |

Why valuation stops at 42.7 % (measured, none are bugs): `NO_COMPARISON_BASIS` 636 (thin names with < 500 bars-with-report); among the 888 with a basis, 181 fail `INSUFFICIENT_METRIC_COVERAGE` (negative EPS → PE `NOT_APPLICABLE`; PB alone weighs 0.30 < 0.50 because EV/EBITDA is structurally unavailable), 78 `PRICE_STALE` (no bar on 08-28). See Q-42.

After Feature 011 (2026-08-30): exporter 35/35 (+8), backend 663/663 (+1), FE 129/129, lint/build clean. Plan: 35 done (Q-31 measurement pending refresh). Fundamentals tool 0.6.0 forces a full re-export on the owner's next `-FullRefresh`.

After Feature 010 (2026-08-30): backend 662/662 (+3 v2 summary, +1 profile revision), exporter 26/26 (+3). Plan: 30 done (Q-31 awaits the post-refresh measurement). Measurement plan once the owner runs `.\refresh-data.ps1 -FullRefresh`: `select count(*) filter (where shares_outstanding is not null) from equity_profile where effective_to is null` (SC-001 ≥ 95 %); `select published, count(*) from valuation_assessment where is_current group by 1` (SC-002 ≥ 50 %); `EPS_GROWTH_PERCENT` `DEFINED` share among instruments with ≥ 2 annual reports (SC-003 ≥ 60 %).

`finvera-fe`: `npx vitest run` 126/126, `npm run lint` clean, `npm run build`
clean. `finvera-ai`: `uv run pytest` 81/81.

---

## Changelog

| Date | Change |
|---|---|
| 2026-08-30 | Opened from the full-system review. Q-01 completed (R-016, T080). |
| 2026-08-31 | Independent recomputation of every calculation layer (607 checks) + provider re-fetch (74/74); Q-45 stale-warmup and Q-46 EPS disclosure fixed; verifier kept as `tools/verification/verify_calcs.py`. |
| 2026-08-31 | Post-warmup re-verification (610 checks, 0 numeric differences); Q-46 follow-up (summary rows re-persist when the calculator output changes); Q-47 deterministic newest-report tie-break; verifier breadth universe and EPS fallback corrected. |
| 2026-08-31 | Q-48: valuation warmup skip rule made input-aware + `-ForceWarmup` switch (second `-WarmupOnly` run skipped 1,524/1,524 and could not roll out Q-46/Q-47). |
| 2026-08-31 | Feature 014 (P2-02) reason-code presentation: one FE dictionary + component, every render site routed, unknown codes never hidden; AI offline sentence worded. |
| 2026-08-31 | Feature 015 (P2-03) first e2e capture on the real stack found Q-49 (valuation tool 401 = read-only tx + /error masking), Q-50 (template refusals), Q-51 (silent degraded mode under 429), Q-52 (data date / basis wording); all fixed with tests; golden replay tests added. Second capture found Q-53 (summary re-persist storm from the scale-12 vs scale-6 comparison), Q-54 (screener duplicate-key crash) and Q-55 (sector-peer valuation 8-18 s → 0.2-0.4 s). Online capture with the owner's new key: Q-56 (list-index citation tags not stripped), 503 retry, model switched to gemini-3.6-flash. Final: P2-03 DONE — 10/10 faithful online, golden fixture frozen. |
| 2026-08-31 | Feature 018 (ADR-0011): fundamentals rebuilt from VCI statements with derived ratios; exporter/crawl/ingestion/FE/verifier done; universe re-crawl pending (owner). |
| 2026-08-31 | P2-05 research exposed **Q-57**: KBS yearly income/cash-flow columns are mirrored (FY2022 under "2025-Năm"); exporter 0.7.0 fixes the orientation with a provenance rule id; G-11 resolved. Follow-up the same evening: raw-page probe + VCI cross-check show the **quarterly** pages are permuted for 20/20 symbols — KBS fundamentals unusable by label; ADR drafted to rebuild fundamentals from VCI (owner decision). |
| 2026-08-31 | specs/007 T050: Analyst answer layout (newlines preserved by the tag stripper, safe markdown renderer, grouped verified claims). |
| 2026-08-31 | Q-44: AI tool payload audit (valuation/fundamentals/stock/technical) + concurrent dispatch. |
| 2026-08-31 | Q-43: explain evidence now carries the result being explained (valuation + signals). |
| 2026-08-31 | Phase 2 plan written (P2-01..P2-07): TCBS live audit, reason-code presentation, analyst e2e, import/warmup incrementality, history-basis consistency, G-11, provider tier. |
| 2026-08-31 | v2 measured: 46.0 % published (SC-001 not met — basis/price limits, not gates); `REDUCED_METRIC_SET` narrowed to core metrics; backend 666/666. |
| 2026-08-31 | Feature 012 `valuation-v2` implemented (spec → research → contract → plan → tasks → code); Q-42 done. |
| 2026-08-31 | Post-refresh measurement recorded (Evidence); Q-42 opened; UNVERIFIED share rule relaxed for treasury shares (AAM 10.45M vs 12.3M charter). |
| 2026-08-31 | **Q-58** (exporter failure classes): dropped connections are transient and retried; settled failures are scoped to the exporter version (153 + 51 silently-skipped checkpoint entries will be retried on the next refresh). specs/018 research R-008. |
| 2026-08-31 | **Feature 020 / ADR-0012** (closes Q-38): sector reference from VCI ICB level 3 for all exchanges — exporter, fixtures/tests, refresh step, verifier `sector` gate, live package 1,522 / 1,522 equities. Also added to `verify_calcs.py`: KBS↔VCI close/index cross-check (independent provider) and the NET_PROFIT/EPS-vs-shares identity. Both activate on the owner's next refresh. |
| 2026-08-31 | **Feature 019** (contract vci-derived-ratios-v1): 16 ratio codes (liquidity, leverage, coverage, turnovers, ROCE, balance growth, NIM, CIR, LDR) derived from VCI statement lines with rule ids, per company type; exporter 1.1.0, 69/69 exporter tests, FE dictionary + inventory extended. DIVIDEND_YIELD/PS/BETA deferred to P2-09 (need a price). Backfill rides the next full crawl. |
| 2026-08-31 | **ADR-0013 / Feature 021 opened** (owner directive): standardize all market data on VCI. Probes: KBS↔VCI closes tick-equal on last sessions, ≤0.22% adjustment-rounding on 1-year history (MSB worst), VN30 'mismatch' was a verifier query bug (intraday ticks vs CLOSED row — fixed), UPCOMINDEX + issue_share + foreign room available on VCI. verify_calcs: provider cross-check hardened (CLOSED rows, thin-trading, 0.5% adjustment tolerance). |
| 2026-08-31 | **Feature 021 implemented**: four exporters switched to VCI (1.0.0, both range ends clamped, HSX→HOSE venue map, issue_share cross-check vs market cap), universe filters on VCI labels, `SOURCE_PREFERENCE` ranks VNSTOCK_VCI first, daily-bar source retirement inside the default import, EN names never erased by an absent value, **Q-59** (PROVIDER_ADJUSTED label vs V003 checks) fixed via V016. Suites: exporter 73/73, FE 146/146, backend green pending final targeted run. Activates fully on the owner's next full re-crawl. |
| 2026-08-31 | **Post-refactor audit** (owner request: đảm bảo data chính xác sau refactor VCI): live package↔DB comparison across every dataset — closes/indices/shares/fundamentals verified (specs/021 R-008). Two findings fixed: **R-006** VCI emits flat zero-volume filler bars for no-trade sessions on thin UPCoM symbols → dropped at the exporter (a bar with any real volume is kept); **R-007** provider-delisted symbols (DAN, DVT) stayed LISTED → profile exporter emits DELISTED records and the importer revises on status change, carrying known share counts forward. Exporter suite 75/75, profile import tests green. |
| 2026-08-31 | Owner-authorized dev reset for the VCI standardization (no personal data, pre-launch): all 4,320 KBS-era local packages deleted (1.5 GB, regenerated by the next crawl) and the `finvera` DB `public` schema dropped and recreated empty (43 tables -> 0; Flyway V001-V016 rebuilds on the next backend start). Next plain `.\refresh-data.ps1` rebuilds everything from scratch under VCI. |
| 2026-08-31 | **Feature 022** (closes P2-09): DIVIDEND_PER_SHARE derived from VCI cash dividends (rule vci-dps-cash-dividends-over-shares-v1, exporter 1.2.0) restoring the dormant DIVIDEND_YIELD chain; PS added to valuation-v2 as an informational metric (weight 0, V017); BETA deferred and tier upgrade declined (owner decisions); **history start moved 2023-01-01 -> 2019-01-01** (full-cycle own-history basis, zero extra provider calls); verifier recomputes PS + yield. Suites: exporter 76/76, ValuationV1 27/27, valuation integration green, FE 50/50. |
| 2026-09-07 | **Q-61 fixed; Q-62 opened.** Reviewing the owner's crawl tail (`889/1522`, 275 symbols with a failed dataset) turned up two distinct defects in the exporter's notion of "finished". **Q-61**: the `MIN_RECORDS = 20` guard raised a bare `ValueError`, which the checkpoint settled permanently - 48 symbols locked out of the daily-bar pipeline, 42 of them untouched since 2026-09-01, and DMX already exportable while its failure still stood (probed live). Fixed with an `InsufficientSessions` class re-checked every 7 days, mirroring `NoStatementsAvailable`'s 35 (specs/026 R-008, T012; exporter suite 94/94). **Q-62** (open): `daily_bars_current` demands a bar dated `--end`, which 603 delisted/untraded symbols can never have, so the crawl re-fetches them every run and never converges. |
| 2026-09-06 | **Feature 026 - the refresh can now finish unattended.** Reviewing the pipeline for the speed work turned up the real fragility: stage 1's four exporters had zero exception handling around 7 provider calls, so one timeout aborted everything before the checkpointed crawl even started. All 7 now share one retry definition; the orchestrator resumes from a stage state file, retries a failed stage 3 times, detects a stalled backend in 15 minutes rather than 6 hours, and reads its logs incrementally; the crawl runs 5 symbols at a time behind our own 40/min token bucket with an atomic checkpoint. `--workers 1` reproduces the old behaviour exactly. Exporter suite 92/92 (76 + 9 retry + 7 concurrency). |
| 2026-09-06 | **Post-refresh verification: Feature 023 and Feature 025 both live and confirmed on real data.** Bars to 2026-09-04; 1,519 summaries under `fundamental-summary-v3`, 1,522 assessments under `valuation-v3` (1,076 published, 0 invariant violations). Q-60 gone: zero unlabelled quarter-sums remain on the 60 affected instruments and SDY reads +1,418 instead of -1,680. valuation-v3's effect measured in the wild: median P/E percentile shift 7.93 pp, 45 % of 899 instruments moved > 10 pp, VNM 14.2 -> 66.1. `verify_calcs.py` **784 checks, 0 failures**. Three differences it first reported were all checker bugs, now fixed: Basis A is per-metric (a 500-point floor P/B clears and P/E does not), and the breadth check had a **hardcoded session date** that compared a 2026-09-01 snapshot against bars the latest crawl had revised - it now takes the newest snapshot, and 2026-09-04 reconciles exactly. |
| 2026-09-06 | **P2-11 closed with no change.** Having measured it (specs/024), the owner declined both rebuilding Basis B and disclosing the cross-section's basis mix: the mix disperses without tilting either group, so the disclosure would add UI text without changing a reader's decision. The engine is unchanged; the study tool and its numbers stay as the record and as the reopen trigger. |
| 2026-09-06 | **Q-60 fixed - Feature 025 / `fundamental-summary-v3`.** A quarterly window is no longer chosen by counting reports: it must be consecutive fiscal quarters and must not be older than an annual report already held. v2 served 60 LISTED instruments a "TTM" summed from non-contiguous or years-stale quarters (worst 2,557 days; WTC's four "newest" quarters spanned eight years) while a current annual report sat unused, and SDY was shown lossmaking (EPS_TTM -1,680) against a profitable FY2025 (+1,418). Rejection now falls back to the annual figure with `ANNUAL_BASIS` + `QUARTER_WINDOW_INELIGIBLE`, or withholds where no annual exists. Backend  34/34 and full suite **720/720**, FE 158/158, AI 152 passed; verifier and the sector study updated to v3. Activates on the next refresh. |
| 2026-09-06 | **P2-11 researched (specs/024) and Q-60 opened.** Sector-basis measurement on 26 sectors / 1,190 subjects: a uniform fiscal-year cross-section would move the P/E sector percentile by median 6.56 pp (p90 27.25), but the mixed ruler **disperses without tilting** either group (49.9 vs 49.3 percentile today; 49.5 vs 48.1 uniform), and uniform FY is worse for the biggest movers (XPH 1.71 -> 4,833 on an FY EPS of 3 VND). Recommendation: keep the freshest ruler per constituent, disclose the composition - do not rebuild Basis B; owner decision pending. The measurement exposed **Q-60**: 24 LISTED instruments served an `EPS_TTM` summed from non-contiguous or years-stale quarters (up to 2,557 days) while a fresher annual report sits current; SDY is shown lossmaking (-1,680) against a profitable FY2025 (+1,418). New read-only tool `tools/verification/sector_basis_study.py`. |
| 2026-09-05 | **P2-05 decided and implemented — Feature 023 / `valuation-v3`**: `history_basis_study.py` re-run on the clean VCI universe (median FY-vs-TTM P/E gap 7.6 %, p90 31 %, max 73 %; percentile moves up to 58 pp; 31 % of instruments annual-only and already consistent). Owner chose to fix the comparison, not footnote it: own-history percentiles of flow metrics now rank a fiscal-year value against a fiscal-year series; the ranked value and its basis are stored (V019), served (OpenAPI additive, `ruleVersion` v2|v3), rendered ("theo P/E năm …") and handed to the analyst tool. SDD artifacts in `specs/023-valuation-v3-history-basis/` (spec, research R-001…R-010, contract, data-model, plan, quickstart, tasks); specs/017 closed. Backend 713/713, AI 152, FE 158/158 + lint/build. Activates on the next refresh; P2-11 opened for the sector basis. |
| 2026-09-04 | **Post-crawl evidence recorded** (the docs were behind reality): the from-scratch VCI crawl had already completed and `verify_calcs.py` was green (664 checks / 0 failures, run 2026-09-02) without anyone closing the items that were waiting on it. Measured on the live database and recorded: bars 1,744,688 / indices 7,640 / profiles 1,522 / fundamentals 21,446, **all 100 % `VNSTOCK_VCI` with zero current KBS rows**, and sector coverage 1,522/1,522 under `VCI_ICB_L3`. **Q-57, Q-38 and P2-10 closed**; P2-07 marked declined per the owner's Feature 022 decision. Still open: P2-05 (now unblocked -- re-run `history_basis_study.py` on the re-exported universe), P2-04 (conditional on measured slowness), and a refresh run: the newest bar is 2026-08-28. |
| 2026-09-04 | **P2-01 closed / Feature 013 complete**: the TCBS Thesis live path audited against the live provider during two open sessions. No code defect; the contract was under-describing the provider and now carries per-field units (anchored on VCI + the price-limit band identity), the real reference-price chain (`s|4` is never replayed mid-session), the server control frames, and the provider's RFC-ping prohibition. Pinned frame fixture + 7 tests; backend 705/705. New owner tool `tools/verification/tcbs_live_capture.py`. |
| 2026-08-31 | **P2-01 code-vs-contract audit** of the TCBS Thesis live path: every statically checkable contract rule conforms (frame mapper, session-date semantics incl. Q-24/Q-25 rules, dedupe, VN30-excluded breadth consolidation, iFlash legacy exclusion, no secret logging). One open item: the base-VND price-unit anchor needs a single spot-check during a trading session (specs/013 R-004). |
| 2026-08-30 | Q-39: quota-aware pacing + transient rate-limit retries in the universe exporter (effective from the next run). |
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
