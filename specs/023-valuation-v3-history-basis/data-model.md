# Data Model: Feature 023 — Own-History Percentile on One Basis

## `valuation_metric` (amended, migration V019)

| Column | Type | Nullable | Rule |
|---|---|---|---|
| `own_history_basis` | `varchar(32)` | yes | `FISCAL_YEAR` for flow metrics, `LATEST_REPORT` for point-in-time metrics; present whenever Basis A was evaluated for the row; null on v2 rows and where Basis A does not apply |
| `own_history_comparison_value` | `numeric(24,12)` | yes | the value that was ranked (`C_A(m)`); same precision as `value`; null when not `DEFINED` |

Check added:

```sql
check (own_history_basis is null or own_history_basis in ('FISCAL_YEAR', 'LATEST_REPORT'))
```

The contract invariant `own_history_percentile != null ⇒ basis and comparison value != null` is
deliberately **not** a SQL check: `valuation-v2` rows carry percentiles with both new columns null
and must stay readable, and the rule version that would scope the check lives on the parent table.
The invariant is enforced where v3 rows are constructed (`ValuationMetricEntity`) and asserted by
the integration test. Recorded here so nobody "tightens" the migration later and breaks v2 history.

`valuation_assessment`: no schema change. New rows carry `rule_version = 'valuation-v3'`; the
partial unique index `(instrument_id, rule_version, as_of_trading_date) where is_current` already
separates v2 and v3 revision chains.

## Domain records (`ValuationV1`)

- `Inputs` gains `Map<String, MetricValue> ownHistoryComparison` — per metric code, the
  fiscal-year-mode `MetricValue` (value + applicability + quality reason). Builder default: empty
  map, which means "no comparison value available" (a flow metric then gets no Basis A percentile).
  Point-in-time metrics ignore the map and compare on their headline value.
- `MetricResult` gains `String ownHistoryBasis`, `BigDecimal ownHistoryComparisonValue`.
- `RULE_VERSION = "valuation-v3"`.
- Reason codes `HISTORY_FISCAL_YEAR_BASIS`, `HISTORY_COMPARISON_UNAVAILABLE`.

## `FundamentalSummaryCalculator`

- `enum AggregateBasis { PREFER_QUARTERS, FISCAL_YEAR }`.
- `calculate(reports, asOfDate)` ≡ `calculate(reports, asOfDate, PREFER_QUARTERS)` (unchanged
  output; `RULE_VERSION` unchanged).
- `calculate(reports, asOfDate, FISCAL_YEAR)`: aggregates from the latest visible ANNUAL report,
  growth annual-over-annual, no provider trailing-EPS fallback.

## Transport

- `StockValuationResponse.MetricResponse` (+`ownHistoryBasis`, `ownHistoryComparisonValue`, both
  nullable strings; comparison value displayed at 2 decimals like `value`).
- `ToolResponseDtos.MetricFactDto` (+ the same two fields).
- OpenAPI `stock-detail.openapi.yaml`: metric item gains the two nullable properties;
  `ruleVersion` enum gains `valuation-v3`.
- FE `ValuationMetric` type (+ the two fields), `ruleVersion: "valuation-v2" | "valuation-v3"`.

## Provenance and time

Unchanged: `observedAt` visibility boundary per session, `as_of`, `calculated_at`, revision chain
via `supersedes_id`, `valuation_assessment_input` links (`PRICE_CURRENT`, `FUNDAMENTAL_SUMMARY`,
`EQUITY_PROFILE`, `OWN_HISTORY_SERIES` hash). The own-history fingerprint now hashes fiscal-year
series values, so the idempotency check naturally distinguishes v2 and v3 series.
