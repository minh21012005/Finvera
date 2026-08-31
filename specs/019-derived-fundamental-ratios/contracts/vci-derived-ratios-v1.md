# Contract: vci-derived-ratios-v1

Extends `vci-fundamentals-v1` (package shape, period parsing, `record` fields, `-end` suffix and
TTM window conventions unchanged). Defines the ratio metrics the exporter derives from VCI
statement lines. Expense lines in VCI frames are negative; `|x|` below means absolute value.
A ratio is emitted only when every required input resolves for the period and every denominator
is > 0; otherwise it is absent — missing, never zero, never proxied.

## Period semantics

- **Balance ratios** (liquidity, leverage) use the period's end-of-period balance sheet column.
- **Flow ratios** (turnovers, coverage, ROCE, NIM, CIR): ANNUAL = the year's flows over 2-point
  average balances (prior year-end, year-end; `-end` suffix when the prior column is missing);
  QUARTER = TTM flows over 4 consecutive quarters (existing `window`) with 5-point average
  balances (`-end` fallback to the period-end balance). A broken quarter window → absent.
- **Growth** (percent, ANNUAL only): `(x_y − x_{y−1}) / |x_{y−1}| × 100`, prior year > 0 required.

## Rules

| Metric (unit) | Company types | Formula | Rule id |
|---|---|---|---|
| CURRENT_RATIO (ratio) | NON_FINANCIAL, INSURER, BROKER | `current_assets / current_liabilities` | `vci-current-ratio-v1` |
| QUICK_RATIO (ratio) | NON_FINANCIAL, INSURER | `(current_assets − inventory) / current_liabilities`; inventory id `inventories_net` (INSURER: `inventories`) must resolve | `vci-quick-ratio-v1` |
| CASH_RATIO (ratio) | NON_FINANCIAL, INSURER, BROKER | `cash_and_cash_equivalents / current_liabilities` | `vci-cash-ratio-v1` |
| DEBT_TO_ASSETS (%) | NON_FINANCIAL, BROKER | `TOTAL_DEBT / total_assets × 100` (TOTAL_DEBT = st + lt borrowings, the existing fact) | `vci-debt-to-assets-v1` |
| LIABILITIES_TO_EQUITY (%) | all | `total liabilities / owners_equity × 100`; liabilities id `liabilities` (BANK: `total_liabilities`) | `vci-liabilities-to-equity-v1` |
| EQUITY_TO_ASSETS (%) | all | `owners_equity / total_assets × 100` | `vci-equity-to-assets-v1` |
| INTEREST_COVERAGE (ratio) | NON_FINANCIAL | `(pre_tax + \|interest\|) / \|interest\|`; ids `net_accounting_profit_loss_before_tax`, `interest_expenses`; interest ≠ 0 | `vci-interest-coverage-v1` |
| TOTAL_ASSET_TURNOVER (ratio) | all | `REVENUE(flow) / avg(total_assets)` | `vci-asset-turnover-v1` |
| INVENTORY_TURNOVER (ratio) | NON_FINANCIAL | `\|cost_of_sales\|(flow) / avg(inventories_net)` | `vci-inventory-turnover-v1` |
| RECEIVABLES_TURNOVER (ratio) | NON_FINANCIAL | `REVENUE(flow) / avg(trade_accounts_receivable)` | `vci-receivables-turnover-v1` |
| ROCE (%) | NON_FINANCIAL | `(pre_tax + \|interest\|)(flow) / avg(total_assets − current_liabilities) × 100` | `vci-roce-v1` |
| TOTAL_ASSETS_GROWTH_PERCENT (%) | all | growth of `total_assets` | `vci-balance-growth-yoy-v1` |
| EQUITY_GROWTH_PERCENT (%) | all | growth of `owners_equity` | `vci-balance-growth-yoy-v1` |
| NIM (%) | BANK | `net_interest_income(flow) / avg(earning assets) × 100`; earning assets = Σ of the ids that resolve among `balances_with_other_credit_institutions`, `placements_with_and_loans_to_other_credit_institutions`, `trading_securities_net`, `investment_securities`, `loans_and_advances_to_customers_net` (at least the loans line must resolve) | `vci-nim-earning-assets-v1` |
| COST_INCOME_RATIO (%) | BANK | `\|general_and_admin_expenses\| / total_operating_income × 100` | `vci-cir-v1` |
| DIVIDEND_PER_SHARE (VND/share) | all | `\|dividends_paid\|(period) / shares(period)`; CF id `dividends_paid`; caveat: the consolidated line includes dividends paid to minority holders, so this is a slight over-statement for groups with large minorities (Feature 022; feeds summary DIVIDEND_PER_SHARE_TTM -> valuation DIVIDEND_YIELD) | `vci-dps-cash-dividends-over-shares-v1` |
| LOAN_TO_DEPOSIT (%) | BANK | `loans_and_advances_to_customers (gross) / deposits_from_customers × 100` | `vci-ldr-v1` |

Not derived here (and why): DIVIDEND_YIELD and PS need a price and are computed in the valuation
layer (Feature 022: yield from DIVIDEND_PER_SHARE above; PS from REVENUE_TTM); BETA deferred (owner
scope decision 2026-08-31); bank/insurer INTEREST_COVERAGE (interest is operating for them); broker/insurer
inventory & receivables turnover and ROCE (post-2016 securities layout / insurer layout lack the
canonical lines); bank liquidity ratios (no current-asset split on bank balance sheets).

## Anchors (fixtures, FY2025)

VNM: CURRENT_RATIO 36,261,180,908,033 / 18,520,286,019,795 = 1.957810…; QUICK, CASH, coverage,
turnovers, ROCE hand-computed the same way in the tests. MBB: CIR 19,681,153 / 67,693,015 ×100 =
29.0741 %; LDR 1,084,019,370 / 921,368,132 ×100 = 117.6533 %; NIM per the earning-asset sum.
SSI: CURRENT_RATIO 1.4430; no turnover/ROCE/coverage records. BVH: liquidity + leverage + growth
only. Tests assert exact decimals from fixture inputs (quantised to the exporter's 6 dp).
