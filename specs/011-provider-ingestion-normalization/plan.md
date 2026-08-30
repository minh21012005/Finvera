# Implementation Plan: Provider Ingestion Normalization

**Feature**: `011-provider-ingestion-normalization` · 2026-08-30 · **Spec**: `spec.md` · **Research**: `research.md`

## Summary

All fixes live at the ingestion boundary (`tools/market-data/vnstock-export`)
plus one summary-rule amendment; the database schema, valuation, screener and
API contracts do not change. A provider-schema fixture makes the mapping
auditable in CI without provider calls.

## Constitution Check

| Principle | Assessment |
|---|---|
| I | New rule ids: `provider-ratio-facts-v2`, `kbs-fcf-ocf-plus-capex-v2`, `kbs-trailing-ratio-as-annualized-v1`. `fundamental-summary-v2` amended before first execution (documented in research R-003). ✔ |
| II | Every reinterpreted fact carries `derivation`/`quality_reason`; unverified shares flagged. ✔ |
| III | Exporter → canonical package → existing import; no module boundary crossed. ✔ |
| V | spec → research → plan → contracts → tasks → code. ✔ |
| VI | Fixtures taken verbatim from probe values (VNM/MBB/SSI/BVH). ✔ |
| VIII | Unverifiable inputs left out; nothing estimated. ✔ |

## Design

### Bars (`export_daily_bars.fetch_rows`)

`KBS_END_PADDING_DAYS = 3`; provider request uses `end + padding`; rows with
`time > end` are dropped before packaging. `export_all_symbols` unchanged.

### Profile (`export_equity_profile`)

Drop `freeFloatRatio` from the record; `share_fields` returns
`(shares, quality_reason)` where reason is `None`,
`SHARES_OUTSTANDING_UNAVAILABLE`, or `SHARES_OUTSTANDING_UNVERIFIED`
(|shares − charter_capital×1e9/par| / shares > 1 %). Importer keeps the record
reason when shares are present. Parser no longer reads `freeFloatRatio`.

### Fundamentals (`export_fundamentals`)

- `RATIO_ANNUAL_ONLY_IDS` (skipped in quarter columns) and
  `RATIO_QUARTER_ONLY_IDS` (skipped in annual columns) per contract.
- Quarter columns: `roe_trailling` additionally emits `ROE`, `roa_trailling`
  emits `ROA`, both with `derivation = kbs-trailing-ratio-as-annualized-v1`.
- `DIVIDEND_YIELD`: value as reported; no derivation.
- `INCOME_STATEMENT_MAP` + insurance/securities ids; `(metricCode, column)`
  dedupe.
- FCF v2 id lists; `FCF_DERIVATION = kbs-fcf-ocf-plus-capex-v2`.
- `TOOL_VERSION = 0.6.0`.

### Summary (`FundamentalSummaryCalculator`, v2)

`ANNUAL_ONLY_CODES = {ROCE, NIM, TOTAL_ASSET_TURNOVER, INVENTORY_TURNOVER,
RECEIVABLES_TURNOVER, DIVIDEND_YIELD, PS, TOTAL_ASSETS_GROWTH_PERCENT,
EQUITY_GROWTH_PERCENT}` read via `addLatestMetricWithAnnualFallback`; when
the annual row is used the summary metric's reason becomes `ANNUAL_BASIS`.

### Schema fixture

`tests/provider_schema_fixture.json`: per company type, the item ids observed
2026-08-30 for income statement / ratio / cash flow. Test asserts every id in
the exporter maps exists in the fixture for at least one type, and that every
"concept" (net profit, revenue, EPS, OCF, capex) is mapped for every type
except the documented exclusions.

## Test strategy

| Req | Test |
|---|---|
| FR-001 | monkeypatched `Market().equity().ohlcv` captures requested end and returns rows up to 08-28 + a fake 09-01 row → package ends 08-28 |
| FR-002 | overview with `free_float_percentage` garbage → no free float; shares consistent / inconsistent cases |
| FR-003 | quarter frame: `roe` skipped, `roe_trailling` → ROE+ROE_TTM; annual frame: `roe_trailling` skipped, `dividend_yield` 7.92 as is |
| FR-004 | BVH/SSI frames → NET_PROFIT/REVENUE/OPERATING_PROFIT |
| FR-005 | SSI cash flow → FCF v2; bank frame → none |
| FR-006 | summary: newest quarter lacks DIVIDEND_YIELD, annual has 7.92 → DEFINED, ANNUAL_BASIS |
| FR-007 | schema fixture test |

## Rollout

Fundamentals 0.6.0 forces a one-time re-export of every symbol (quarter and
annual passes). The owner's pending `-FullRefresh` (Feature 010) covers it.
Bars: next routine run fetches the missing sessions through the lookback.
