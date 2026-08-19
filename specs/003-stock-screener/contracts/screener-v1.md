# Calculation Contract: `screener-v1`

**Feature**: `003-stock-screener`
**Rule version**: `screener-v1`
**Owner**: `finvera-be` / `stock` module, domain layer (`stock.domain.screener`)
**Status**: Normative. This file is the single authority for filter
evaluation semantics. If code and this file disagree, this file is correct
and the code is defective (`AGENTS.md`).

## Scope and authority

This contract defines every filter category, field, operator, and
combination rule the screener evaluates, and the two new derived conditions
(Breakout, Trend) this feature introduces. It does **not** redefine any
formula already normative in
[`technical-indicators-v1.md`](../../002-stock-detail-analysis/contracts/technical-indicators-v1.md)
or [`valuation-v1.md`](../../002-stock-detail-analysis/contracts/valuation-v1.md)
— this contract only says which already-published field a filter reads and
how it is compared.

A change to any operand definition, threshold, lookback window, or
combination rule below creates `screener-v2`. Existing screen executions are
not retroactively recomputed (there is nothing to recompute — screens are not
persisted, R-006) but a version change must still be reflected in the
response's `ruleVersion` field so a client can tell which semantics produced
a given result.

## Universal rules

**S-1 Read-only.** Every filter reads only already-accepted, current-revision
facts and results. No filter triggers ingestion, provider access, or
recalculation of a technical, fundamental, or valuation result (FR-007,
DATA-001).

**S-2 Combination.** Every selected filter, across every category, combines
with logical AND. A stock matches only if it satisfies every selected filter.
There is no OR or grouped boolean logic in `screener-v1` (FR-001).

**S-3 Arithmetic.** Every numeric comparison uses the same decimal
representation as its source value (scale 12 for technical/valuation
component values per `technical-indicators-v1` U-3, the source column's
declared scale for daily-bar and fundamental values). No comparison converts
through binary floating point (DATA-002).

**S-4 Applicability.** A filter's source value carries a
`MetricApplicability`/`DataStatus` outcome from its engine of record. When
that outcome is not a defined, current, published value, the stock is
**excluded** from the match set for that filter with the source engine's own
reason code — never silently passed, never silently failed, never defaulted
to a comparable value (DATA-003, FR-006). This applies uniformly to every
filter category:

| Source condition | Filter outcome | Reason code (from source engine) |
|---|---|---|
| Indicator `data_status != CURRENT` and no value component published | Excluded | `INSUFFICIENT_HISTORY` (technical-indicators-v1 U-6) |
| Fundamental summary metric `applicability != DEFINED` | Excluded | The summary metric's own `quality_reason` |
| Valuation assessment withheld (`classification is null`) | Excluded from every valuation-derived filter | The assessment's own `reason_codes` |
| Daily bar for the latest session missing/unavailable | Excluded from every Price/Volume filter | `PRICE_UNAVAILABLE` |
| Instrument not `LISTED` (research R-007) | Not a candidate at all | n/a — never evaluated |

**S-5 Reproducibility.** Given an identical filter set and unchanged accepted
inputs, a screen execution MUST return an identical match set and identical
reported matching values (FR-010, DATA-004). The response's coherence key
(research R-008) is computed over the exact current-revision row ids/revisions
every matched stock's evaluation read.

## Filter categories

### Market

| Filter | Source | Operator |
|---|---|---|
| `exchange` | `market_instrument.exchange` (Feature 001, unchanged) | equals one of a provided set |
| `sector` | `equity_profile.sector_reference_id` (current row) | equals one of a provided set of `sector_reference.id` |
| `marketCapMin`/`marketCapMax` | Computed: latest accepted `equity_daily_bar.close_price` × current `equity_profile.shares_outstanding`, same formula `StockOverviewService` already uses | inclusive range |

A stock with a null `sector_reference_id` or null `shares_outstanding` is
excluded from a `sector` or market-cap filter respectively, per S-4, using
the same `quality_reason` `equity_profile` already carries for that null.

### Price

| Filter | Source | Operator |
|---|---|---|
| `priceMin`/`priceMax` | Latest accepted `equity_daily_bar.close_price` | inclusive range |
| `priceChangePercentMin`/`priceChangePercentMax` | Percentage change vs. the previous valid official close — the identical basis Feature 002 FR-002 already defines for the stock overview | inclusive range |

### Technical

All indicator-sourced fields read the instrument's **current**
`technical_indicator_result`/`technical_indicator_value` rows for the stated
`indicator_code`/`component_code`. No indicator is recalculated (research
R-004).

| Filter | Source | Operator |
|---|---|---|
| `rsiMin`/`rsiMax` | `RSI14` `VALUE` component | inclusive range |
| `macdSignal` | `MACD` `HISTOGRAM` component sign: `BULLISH` (`> 0`), `BEARISH` (`< 0`), `NEUTRAL` (`= 0`) | equals |
| `maRelationship` | Comparison between two operands from `{ LATEST_CLOSE, MA20, MA50, MA200 }` (e.g. `PRICE_ABOVE_MA50`, `MA20_ABOVE_MA50`, `MA50_ABOVE_MA200`); `LATEST_CLOSE` is the same value the Price category uses | one of the named relationships, evaluated as a strict `>` or `<` comparison between the two named operands |
| `volumeMin`/`volumeMax` | Latest accepted `equity_daily_bar.volume` (raw session volume, distinct from relative volume — research R-004/clarified 2026-08-19) | inclusive range |
| `relativeVolumeMin`/`relativeVolumeMax` | `RELATIVE_VOLUME` `VALUE` component | inclusive range |
| `breakout` | See **Breakout condition** below (new in this contract) | equals `BREAKOUT_UP` / `BREAKOUT_DOWN` |
| `trend` | See **Trend direction** below (new in this contract) | equals `UPTREND` / `DOWNTREND` / `SIDEWAYS` |

#### Breakout condition (new)

```text
priorHigh[n] = max( H[i] for i = n-20 .. n-1 )
priorLow[n]  = min( L[i] for i = n-20 .. n-1 )

BREAKOUT_UP    if C[n] > priorHigh[n]
BREAKOUT_DOWN  if C[n] < priorLow[n]
NONE           otherwise
```

Using the same notation as `technical-indicators-v1` U-7 (`C`/`H`/`L` = the
daily bar's close/high/low, `n` = the as-of bar). The lookback is a fixed 20
prior completed accepted sessions, **excluding** the as-of session itself,
matching `AVG_VOLUME20`/`RELATIVE_VOLUME`'s existing window for consistency.

- **Minimum bars**: 21 accepted sessions (20 lookback + the as-of session).
  Fewer than 21 → excluded with `INSUFFICIENT_HISTORY`, same as any
  under-lookback technical filter (S-4).
- **Tie handling**: `C[n] == priorHigh[n]` or `C[n] == priorLow[n]` is
  `NONE`, not a breakout — the condition requires strictly exceeding the
  prior range, not matching it.
- **Basis**: uses the same adjusted-vs-raw basis selection
  `technical-indicators-v1` U-2 already defines for the instrument's series;
  a breakout is never evaluated by splicing adjusted and raw bars.

#### Trend direction (new)

```text
UPTREND    if MA20 > MA50 > MA200
DOWNTREND  if MA20 < MA50 < MA200
SIDEWAYS   otherwise (all three defined but not strictly ordered either way)
```

- Requires `MA20`, `MA50`, and `MA200` all `DEFINED` for the instrument. If
  any is unavailable, trend is excluded with `INSUFFICIENT_HISTORY` (MA200's
  200-bar minimum is normally the limiting factor).
- Strict inequality only; any tie among the three values yields `SIDEWAYS`,
  not an error.

### Fundamental

All fundamental-sourced fields read the instrument's **current**
`fundamental_summary`/`fundamental_summary_metric` rows; P/E and P/B read
the current `valuation_assessment`/`valuation_metric` rows. No metric is
recalculated (research R-004).

| Filter | Source | Operator |
|---|---|---|
| `revenueGrowthPercentMin`/`Max` | `fundamental_summary_metric` code `REVENUE_GROWTH_PERCENT` — **new metric, research R-005**, computed by the same TTM-vs-prior-TTM formula already implemented for `EPS_GROWTH_PERCENT`, applied to `REVENUE_TTM` | inclusive range |
| `earningsGrowthPercentMin`/`Max` | `fundamental_summary_metric` code `EPS_GROWTH_PERCENT` (existing; "earnings growth" maps to EPS growth per research R-005) | inclusive range |
| `roeMin`/`roeMax` | `fundamental_summary_metric` code `ROE` | inclusive range |
| `roaMin`/`roaMax` | `fundamental_summary_metric` code `ROA` | inclusive range |
| `peMin`/`peMax` | `valuation_metric` code `PE` on the current `valuation_assessment` | inclusive range |
| `pbMin`/`pbMax` | `valuation_metric` code `PB` on the current `valuation_assessment` | inclusive range |
| `debtToEquityMin`/`Max` | `fundamental_summary_metric` code `DEBT_TO_EQUITY` | inclusive range |

A `PE`/`PB` filter excludes a stock whose current `valuation_assessment` is
withheld entirely (`classification is null`), not only a stock whose `PE`/
`PB` metric row is individually `NOT_APPLICABLE` — the valuation-v1
publishability gate is all-or-nothing (Feature 002 `valuation-v1` contract),
and the screener does not partially trust a withheld assessment.

## Required test-vector table

Implementation and fixtures MUST cover at least:

| Case | Expected outcome |
|---|---|
| Every filter category individually, one satisfying and one non-satisfying stock | Exactly the satisfying stock matches |
| Three filters spanning three different categories combined | Exactly the intersection matches (SC-001) |
| A stock missing the value a selected filter needs (each of: insufficient technical history, no accepted fundamentals, withheld valuation, null sector, null shares outstanding) | Excluded with the named reason, never a silent pass/fail |
| Breakout at exactly 20 vs. exactly 21 accepted sessions | 20 sessions → `INSUFFICIENT_HISTORY`; 21 sessions → evaluated |
| Breakout tie (`close == priorHigh`) | `NONE`, not `BREAKOUT_UP` |
| Trend with all three MAs tied or non-monotonic | `SIDEWAYS` |
| Trend with `MA200` unavailable, `MA20`/`MA50` available | Excluded with `INSUFFICIENT_HISTORY` |
| MACD histogram exactly `0` | `NEUTRAL`, not `BULLISH`/`BEARISH` |
| `priceMin > priceMax` in the same request | Request rejected, `INVALID_FILTER_RANGE` (research R-010), no query executed |
| No filters selected | Full candidate universe (research R-007/R-009), not an error |
| Replay: identical filters and inputs run twice | Identical match set, identical reported values, identical coherence key |
