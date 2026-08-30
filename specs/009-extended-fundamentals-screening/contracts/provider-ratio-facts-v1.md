# Contract: Provider-reported ratio facts (`provider-ratio-facts-v1`)

**Feature**: `009-extended-fundamentals-screening` · Accepted 2026-08-30
**Producer**: `tools/market-data/vnstock-export/export_fundamentals.py` (KBS `ratio`
dataset) · **Consumer**: `finvera-be` fundamentals import, summary, screener.
Amends `vnstock-fundamentals-v1` additively; catalog version stays
`fundamental-metric-catalog-v1` (rows added by V015).

## U-1 Stored as observed

A provider ratio is stored exactly as the provider reports it (decimal string,
`numeric(28,6)`), never recomputed, and carries `quality_reason =
PROVIDER_REPORTED` on its `DEFINED` row so readers can distinguish it from a
Finvera calculation.

## U-2 Units are fixed per code, never inferred

| Code | `item_id` | Unit | Sign |
|---|---|---|---|
| `GROSS_MARGIN` | `gross_margin` | PERCENT | ANY |
| `NET_MARGIN` | `net_margin` | PERCENT | ANY |
| `ROE_TTM` | `roe_trailling` | PERCENT | ANY |
| `ROA_TTM` | `roa_trailling` | PERCENT | ANY |
| `ROCE` | `return_on_capital_employed_roce` | PERCENT | ANY |
| `CURRENT_RATIO` | `short_term_ratio` | RATIO | NON_NEGATIVE |
| `QUICK_RATIO` | `quick_ratio` | RATIO | NON_NEGATIVE |
| `CASH_RATIO` | `cash_ratio` | RATIO | NON_NEGATIVE |
| `INTEREST_COVERAGE` | `interest_coverage` | RATIO | ANY |
| `TOTAL_ASSET_TURNOVER` | `total_asset_turnover` | RATIO | NON_NEGATIVE |
| `INVENTORY_TURNOVER` | `inventory_turnover` | RATIO | NON_NEGATIVE |
| `RECEIVABLES_TURNOVER` | `receivables_turnover` | RATIO | NON_NEGATIVE |
| `DEBT_TO_ASSETS` | `debt_to_assets` | PERCENT | NON_NEGATIVE |
| `LIABILITIES_TO_EQUITY` | `liabilities_to_equity` | PERCENT | NON_NEGATIVE |
| `EQUITY_TO_ASSETS` | `equity_to_assets` | PERCENT | NON_NEGATIVE |
| `BETA` | `beta` | RATIO | ANY |
| `PS` | `ps_ratio` | RATIO | NON_NEGATIVE |
| `TOTAL_ASSETS_GROWTH_PERCENT` | `total_assets` (growth table row) | PERCENT | ANY |
| `EQUITY_GROWTH_PERCENT` | `owners_equity` (growth table row) | PERCENT | ANY |
| `NIM` | `net_interest_margin_nim` | PERCENT | ANY |
| `COST_INCOME_RATIO` | `cost_income_ratio_cir` | PERCENT | NON_NEGATIVE |
| `LOAN_TO_DEPOSIT` | `outstanding_loans_customer_deposits` | PERCENT | NON_NEGATIVE |

Corrections to existing codes:

- `DIVIDEND_YIELD` (`dividend_yield`): provider value is a **fraction**; the
  exporter multiplies by 100 and stamps `derivation =
  kbs-dividend-yield-fraction-to-percent` (research R-004.1).
- `DEBT_TO_EQUITY`: catalog unit re-declared `PERCENT` (values already are).

All of the above are in the **unscaled** set: they never inherit statement
`unitScale`.

## U-3 Exclusions

Provider valuation ratios (`pe_ratio`, `pb_ratio`, `ev_ebit`, `ev_ebitda`) and
the cash-flow-derived family the provider returns as unconditional `0.0`
(research R-003) are never mapped. A future change to either list is a new
contract version.

## U-4 Screener filters (amends `screener-v1` additively)

| Filter | Summary code | Operator |
|---|---|---|
| `psMin`/`psMax` | `PS` | inclusive range |
| `betaMin`/`betaMax` | `BETA` | inclusive range |
| `grossMarginMin`/`Max` | `GROSS_MARGIN` | inclusive range |
| `netMarginMin`/`Max` | `NET_MARGIN` | inclusive range |
| `currentRatioMin`/`Max` | `CURRENT_RATIO` | inclusive range |
| `interestCoverageMin`/`Max` | `INTEREST_COVERAGE` | inclusive range |
| `debtToAssetsMin`/`Max` | `DEBT_TO_ASSETS` | inclusive range |

Semantics identical to existing summary-metric filters: `MISSING`/
`NOT_APPLICABLE` → candidate excluded with the metric's own reason; `min > max`
→ `INVALID_FILTER_RANGE`.
