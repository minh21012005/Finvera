# Research: Extended Fundamentals and Screening

**Feature**: `009-extended-fundamentals-screening` · 2026-08-30
**Evidence**: `tools/market-data/provider-poc/probe_balance_sheet.py --period quarter`
over VNM, FPT, HPG, MBB on pinned `vnstock==4.0.6`, KBS, Community tier. Only
`item_id`s, labels, and sample magnitudes are recorded.

## R-001 — Provider ratio dataset shape

58 rows for non-financial companies, 32 for banks; columns arrive unordered
with pandas-deduplicated names (`'2026-Q2', '2025-Q4', '2026-Q1', '2025-Q4_1'`)
which the exporter's period parser already ignores (Feature 008 R-003). Values
are plain numbers; unit is implied per row (established below from label and
magnitude, cross-checked against VNM's published 2026-Q2 figures).

## R-002 — Mapped rows (decision)

| `item_id` | Metric code | Unit | VNM 2026-Q2 | Category |
|---|---|---|---|---|
| `gross_margin` | `GROSS_MARGIN` | PERCENT | 41.8 | profitability |
| `net_margin` | `NET_MARGIN` | PERCENT | 14.81 | profitability |
| `roe_trailling` | `ROE_TTM` | PERCENT | 26.37 | profitability |
| `roa_trailling` | `ROA_TTM` | PERCENT | 15.73 | profitability |
| `return_on_capital_employed_roce` | `ROCE` | PERCENT | 8.6 | profitability |
| `short_term_ratio` | `CURRENT_RATIO` | RATIO | 2.24 | liquidity |
| `quick_ratio` | `QUICK_RATIO` | RATIO | 1.87 | liquidity |
| `cash_ratio` | `CASH_RATIO` | RATIO | 0.3 | liquidity |
| `interest_coverage` | `INTEREST_COVERAGE` | RATIO | 42.42 | liquidity |
| `total_asset_turnover` | `TOTAL_ASSET_TURNOVER` | RATIO | 0.31 | efficiency |
| `inventory_turnover` | `INVENTORY_TURNOVER` | RATIO | 1.48 | efficiency |
| `receivables_turnover` | `RECEIVABLES_TURNOVER` | RATIO | 3.63 | efficiency |
| `debt_to_assets` | `DEBT_TO_ASSETS` | PERCENT | 14.86 | leverage |
| `liabilities_to_equity` | `LIABILITIES_TO_EQUITY` | PERCENT | 46.7 | leverage |
| `equity_to_assets` | `EQUITY_TO_ASSETS` | PERCENT | 68.17 | leverage |
| `beta` | `BETA` | RATIO | 0.52 | market |
| `ps_ratio` | `PS` | RATIO | 7.4 | market |
| `total_assets` (growth row) | `TOTAL_ASSETS_GROWTH_PERCENT` | PERCENT | −3.47 | growth |
| `owners_equity` (growth row) | `EQUITY_GROWTH_PERCENT` | PERCENT | 7.69 | growth |
| `net_interest_margin_nim` | `NIM` | PERCENT | MBB 1.0 | bank |
| `cost_income_ratio_cir` | `COST_INCOME_RATIO` | PERCENT | MBB 29.14 | bank |
| `outstanding_loans_customer_deposits` | `LOAN_TO_DEPOSIT` | PERCENT | MBB 81.92 | bank |

Unit rule: rows whose label reads "Tỷ suất/Tỷ lệ/Tăng trưởng/Tỷ số … trên …"
with magnitudes in the tens are percent points; liquidity/turnover/coverage,
beta and P/S are plain ratios (magnitudes ≈ 0.3–42, labels "Tỷ số thanh
toán", "Vòng quay", "Beta", "P/S"). `debt_to_equity` (already mapped) is
percent (21.8) — consistent with its existing `RATIO/…/scale 4` catalog row
being **mis-declared**; see R-004.

## R-003 — Excluded rows (decision)

- `pe_ratio`, `pb_ratio`, `ev_ebit`, `ev_ebitda`: Finvera computes `PE`/`PB`/
  `EV_EBITDA` under `valuation-v1` at its own current price; a provider value
  at period-end price under the same or a similar name would be a second
  definition (Constitution I "single formula version").
- `accrual_ratio_cf`, `cash_to_income`, `net_cash_flows_short_term_liabilities`,
  `accrual_ratio_balance_sheet_method`, `accrual_ratio_cash_flow_method`,
  `cash_return_to_assets`, `cash_return_on_equity`, `cash_to_income_2`,
  `debt_coverage`, `cash_flow_per_share_cps`: **`0.0` for every probed
  company and period** — the provider is not computing them on the
  Community tier. A zero that means "not computed" must not be stored as a
  fact (ARCHITECTURE.md §4 invariant #4).
- `days_of_*`, `number_of_days_of_payables`, `quick_ratio_except_…_reference`,
  `payables_turnover`, `fixed_asset_turnover`, `equity_turnover`,
  `short_term_liabilities_*`, `charter_capital` growth, the remaining growth
  rows, and bank rows beyond NIM/CIR/LDR: available, unit-known, but no user
  story needs them yet (Constitution VIII). Recorded here for a later feature.

## R-004 — Two pre-existing unit defects surfaced by the probe

1. **`dividend_yield` is a fraction** (VNM 0.04, FPT 0.02, MBB 0.01, HPG 0.0)
   while `fundamental_metric_catalog` declares `DIVIDEND_YIELD` as `PERCENT`
   scale 4 (V014). Stored as 0.04 → displayed "0,04 %". Fix at the exporter
   boundary: multiply by 100 (`kbs-dividend-yield-fraction-to-percent`),
   recorded on the record as `derivation`. Existing rows are corrected by the
   forced re-export (tool version bump) and revision chain.
2. **`debt_to_equity` is percent points** (VNM 21.8, i.e. 21.8 %) but the
   catalog declares unit `RATIO` scale 4 and the screener filter is documented
   as a plain ratio. Display therefore reads "21,8" where "21,8 %" is meant,
   and a user filtering `debtToEquityMax = 1` gets nothing. Fix: catalog row
   re-declared `PERCENT` (migration V015 `update`), FE label/format follows the
   catalog unit; the stored numbers are already percent so no data change.

## R-005 — Storage and provenance

New codes are added to `fundamental-metric-catalog-v1` (additive, V015) and
`FundamentalReportAcceptance.ALLOWED_METRIC_CODES` / unscaled set. Each
imported provider ratio carries `quality_reason = PROVIDER_REPORTED` on its
`DEFINED` row so a reader can tell a provider-computed ratio from a Finvera
computation. `FundamentalSummaryCalculator` copies each new metric from the
newest accepted period (`addLatestMetric`) — no TTM, no derivation.

## R-006 — Screener filters

Seven new inclusive-range pairs read the new summary codes through the
existing `evaluateSummaryMetric` path (same `MISSING`-is-excluded-with-reason
semantics; `screener-v1` contract amended additively, rule version unchanged
because no evaluation semantics change — only the filter vocabulary grows,
exactly as `REVENUE_GROWTH_PERCENT` did in R-005 of Feature 003).

## Constitution check

I ✔ (no new formula; provider facts stored as observed and labelled) ·
II ✔ (unit per row recorded; `PROVIDER_REPORTED` provenance) · III ✔ ·
VI ✔ (exporter, acceptance, summary, screener, FE tests) · VIII ✔ (rows without a
story excluded and listed).
