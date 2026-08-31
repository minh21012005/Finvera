# Contract: vci-fundamentals-v1

**Producer**: `tools/market-data/vnstock-export/export_fundamentals_vci.py` (tool `finvera-vnstock-exporter`, `upstreamSource = VNSTOCK_VCI`, package contract `vnstock-fundamentals-v1` unchanged).
**Consumer**: `FundamentalReportImportService` → `StockIngestionService` (with rule I-1 below) → `fundamental-summary-v2` → `valuation-v2`, screener, AI tools.
**Provider path**: vnstock ≥ 4.0.7, `Finance(symbol, source="vci").income_statement / balance_sheet / cash_flow`, `period ∈ {year, quarter}`. The VCI `ratio` frame is **not** consumed (malformed in vnstock 4.0.7; ratios are derived below).

## Units and periods

| Rule | Statement |
|---|---|
| U-1 | Statement values are raw VND (`unitScale = 1`); `eps_basic_vnd` is VND per share. No scaling is applied. |
| U-2 | Period label `YYYY` → `ANNUAL` FY (Jan 1 – Dec 31); `YYYY-QN` → `QUARTER` (calendar quarter). Fiscal years other than calendar are not distinguished by the provider and not claimed by Finvera. |
| U-3 | `observedAt` = period end + 45 days capped at export time (existing importer convention; VCI carries no publication date). |
| U-4 | `reportKind = UNKNOWN`, `auditStatus = UNKNOWN` (not asserted by the provider). |

## Company type detection (first match wins)

| Type | Detector (item ids present in the yearly income statement) |
|---|---|
| BANK | `net_interest_income` |
| INSURER | `net_sales_from_insurance_business` |
| BROKER | `operating_sales` |
| NON_FINANCIAL | `net_sales` (default) |

The type is recorded on every record (`companyType`) and in the package.

## Statement facts (metric code ← item id; first occurrence of an id wins)

| Metric | NON_FINANCIAL | BANK | INSURER | BROKER | Report |
|---|---|---|---|---|---|
| REVENUE | `net_sales` | `total_operating_income` | `net_sales_from_insurance_business` | `net_sales` | INCOME_STATEMENT |
| GROSS_PROFIT | `gross_profit` | — | `gross_insurance_operating_profit` | `gross_profit` | INCOME_STATEMENT |
| OPERATING_PROFIT | `operating_profit_loss` | `net_operating_profit_before_allowance_for_credit_losses` | — | `operating_profit_loss` | INCOME_STATEMENT |
| NET_PROFIT | `net_profit_loss_after_tax` | `net_profit_loss_after_tax` | `profit_after_tax` | `net_profit_loss_after_tax` | INCOME_STATEMENT |
| EPS | `eps_basic_vnd` (> 0 only; 0.0 means "not reported" for the period and is **dropped**) | same | same | same | INCOME_STATEMENT |
| EQUITY_ATTRIBUTABLE_TO_PARENT | `owners_equity` − `minority_interests` | `owners_equity` − `minority_interest` | `owners_equity` | `owners_equity` | BALANCE_SHEET |
| TOTAL_DEBT | `short_term_borrowings` + `long_term_borrowings` | — | — | `short_term_borrowings` + `long_term_borrowings` | BALANCE_SHEET |
| CASH_AND_EQUIVALENTS | `cash_and_cash_equivalents` | — | `cash_and_cash_equivalents` | `cash_and_cash_equivalents` | BALANCE_SHEET |

"—" = not emitted for that type (the catalog meaning does not apply). Missing
ids produce no record — never a zero.

## Internal inputs (not emitted as metrics; used by derivations)

| Input | NON_FINANCIAL | BANK | INSURER | BROKER |
|---|---|---|---|---|
| parent profit | `attributable_to_parent_company` | `attributable_to_parent_company` | `net_profit_attributable_to_shareholders_of_the_group` (else `…_of_the_parent`) | `net_profit_loss_after_tax` |
| total assets | `total_assets` (BS) | same | same | same |
| share capital | `paid_in_capital` else `common_shares` | `charter_capital` | `paid_in_capital` else `common_shares` | `paid_in_capital` |
| treasury shares | `treasury_shares` (negative or zero) | same | same | same |
| OCF | `net_cash_inflows_outflows_from_operating_activities` | `net_cash_from_operating_activities` | as NON_FINANCIAL | as NON_FINANCIAL |
| capex | `purchases_of_fixed_assets_and_other_long_term_assets` (negative) | same | same | same |
| D&A | `depreciation_and_amortization` (CF) | — | — | — |

`shares(period) = (share capital + treasury shares) / 10 000` (treasury is
stored negative). Emitted as a fact only through the derivations below. VCI rounds bank
statements to thousands of VND, so a bank's share count can differ from the profile by
up to ~100 shares (MBB 8,054,999,900 vs 8,054,999,909) — immaterial, documented.

## Derived facts (rule id = `quality_reason`; Decimal arithmetic, scale 6; emitted only when every input exists for the period)

| Metric | Rule id | Formula | Periods |
|---|---|---|---|
| TRAILING_EPS | `vci-trailing-eps-parent-profit-over-shares-v1` | Σ parent profit of the 4 most recent quarters ending at the period ÷ shares(period) | QUARTER (needs 4 consecutive quarters); ANNUAL = FY parent profit ÷ shares(FY end) |
| BVPS | `vci-bvps-parent-equity-over-shares-v1` | EQUITY_ATTRIBUTABLE_TO_PARENT ÷ shares(period) | both |
| ROE (annual) | `vci-roe-parent-profit-over-average-equity-v1` | FY parent profit ÷ average(equity at FY start, FY end) × 100; if the prior FY balance is absent → ÷ end equity with rule id suffix `-end` | ANNUAL |
| ROE_TTM, ROE (quarter) | `vci-roe-parent-profit-over-average-equity-v1` (`-end` when the five balances are not all present) | Σ 4 consecutive quarters parent profit ÷ average of the 5 period-end equities × 100 | QUARTER |
| ROA / ROA_TTM | `vci-roa-net-profit-over-average-assets-v1` | NET_PROFIT ÷ average total assets × 100 (same window rules) | both |
| GROSS_MARGIN | `vci-margin-v1` | GROSS_PROFIT ÷ REVENUE × 100 | both |
| OPERATING_MARGIN | `vci-margin-v1` | OPERATING_PROFIT ÷ REVENUE × 100 | both |
| NET_MARGIN | `vci-margin-v1` | NET_PROFIT ÷ REVENUE × 100 | both |
| DEBT_TO_EQUITY | `vci-debt-to-equity-v1` | TOTAL_DEBT ÷ EQUITY_ATTRIBUTABLE_TO_PARENT (ratio, not percent) | both (types with TOTAL_DEBT) |
| FREE_CASH_FLOW | `vci-fcf-ocf-plus-capex-v1` | OCF + capex (capex negative) | both |
| EBITDA | `vci-ebitda-operating-profit-plus-da-v1` | OPERATING_PROFIT + D&A | both, NON_FINANCIAL only |

Percent-unit codes (`ROE`, `ROA`, `ROE_TTM`, `ROA_TTM`, margins) are emitted in
percent as the catalog defines. Ratios with a zero or negative denominator are
not emitted (a `NOT_APPLICABLE` is the summary layer's decision, not the
exporter's).

## Not emitted in v1 (become MISSING / NOT_REPORTED downstream)

`PS`, `ROCE`, turnovers, `NIM`, `COST_INCOME_RATIO`, `LOAN_TO_DEPOSIT`,
`BETA`, provider growth rows, `DIVIDEND_PER_SHARE`, `DIVIDEND_YIELD`,
liquidity/coverage ratios. Each is a candidate for a later derivation rule.

## Ingestion rule

| Rule | Statement |
|---|---|
| I-1 | If a current `fundamental_report` exists for the same (instrument, periodType, fiscalYear, fiscalQuarter, reportKind) from a **different** source, the incoming report supersedes it: previous row `current = false`, reason `SOURCE_SUPERSEDED`, incoming accepted as `CORRECTED` (revision +1). The out-of-order guard applies only within the same source. |
| I-2 | Everything else (acceptance, catalog checks, summary recomputation, warmup detection of new `accepted_at`) is unchanged. |

## Anchor set (asserted by the exporter tests on the captured fixtures)

VNM FY2025 REVENUE 63,645,886,756,227 = Σ quarters; NET_PROFIT 9,413,589,731,xxx = Σ
quarters; shares(FY2025) 2,089,955,445; MBB REVENUE FY2025 67,693,015,000,000;
BVH REVENUE FY2025 40,948,251,401,814, EPS 3,821; SSI NET_PROFIT FY2025
4,106,880,733,899, shares(2026-Q2) ≈ 2,501.18 m; FY2024 VNM revenue 61,782,609,528,xxx
under label 2024 (not 2023 — the KBS mirror must not reappear).

## Versioning

Adding a mapping or a derivation = v1 append; changing a formula, a detector or
the supersession rule = v2.
