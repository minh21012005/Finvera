# Research: Feature 019 — Derived Fundamental Ratios

Date: 2026-08-31. Basis: full VCI statement captures for the four company-type anchors
(VNM non-financial, MBB bank, BVH insurer, SSI broker), yearly (8 periods to 2018) and quarterly
(8 quarters to 2024-Q3), saved during the Feature 018/019 probes; catalog V015; KBS-era contract
provider-ratio-facts-v1/v2 (unit conventions the product already renders).

## R-001 Every target ratio is arithmetic over lines VCI serves

The 18 KBS ratio codes decompose into statement lines that exist in the VCI frames with stable
`item_id`s (full inventories in the capture: VNM IS 25 / BS 122 / CF 41 ids; MBB 26/86/52;
BVH 84/151/49; SSI 79/208/148). No target ratio needs the malformed VCI ratio frame.
DIVIDEND_YIELD, PS and BETA need a **price** and are excluded (spec out-of-scope; `dividends_paid`
is available in every cash flow if a later feature derives yield in the valuation layer).

## R-002 Unit conventions are fixed by what the product already stores (V015)

PERCENT (store ×100): ROCE, DEBT_TO_ASSETS, LIABILITIES_TO_EQUITY, EQUITY_TO_ASSETS,
TOTAL_ASSETS_GROWTH_PERCENT, EQUITY_GROWTH_PERCENT, NIM, COST_INCOME_RATIO, LOAN_TO_DEPOSIT.
RATIO (store as-is): CURRENT_RATIO, QUICK_RATIO, CASH_RATIO, INTEREST_COVERAGE, the three
turnovers. All are in `PROVIDER_RATIO_CODES` → ingestion never applies `unitScale` to them, and
`ALLOWED_METRIC_CODES` already contains every code — **no backend change**.

## R-003 Signs and id quirks that shape the formulas

| Finding | Consequence |
|---|---|
| VCI expense lines are **negative** (cost_of_sales, general_and_admin_expenses, interest_expenses, fees_and_commission_expenses) | formulas use `abs()` on expense inputs; documented per rule |
| `interest_expenses` (VNM IS) is the finance-cost detail line of `financial_expenses` | EBIT = pre-tax + |interest_expenses|; coverage denominator = |interest_expenses|; emitted only when the line is non-zero |
| MBB BS carries both gross (`loans_and_advances_to_customers`) and net (`…_net`) loans | LDR uses gross loans (Circular 26 convention); NIM earning assets use the **net** interest-bearing lines |
| Earning assets for NIM: `balances_with_other_credit_institutions` + `placements_with_and_loans_to_other_credit_institutions` + `trading_securities_net` + `investment_securities` + `loans_and_advances_to_customers_net` (ids present on MBB; absent ids contribute nothing only when *some* are present — all-absent means no NIM) | definition recorded in the rule id `vci-nim-earning-assets-v1`; NIM is definition-sensitive, so the denominator is spelled out in the contract rather than "whatever the provider meant" |
| SSI (broker) statements are the post-2016 securities-company layout: no `inventories_net`, receivables under `receivables_from_2016`-family ids, interest cost in `interests_expenses` | brokers get liquidity + leverage + growth + asset turnover; inventory/receivables turnover and ROCE are **absent** (FR-005), interest coverage only when the pre-tax and interest ids both exist |
| BVH (insurer) has a full corporate-layout BS (current assets/liabilities present) but no meaningful COGS/interest split | insurers get liquidity + leverage + growth + asset turnover; no coverage/turnover/ROCE |
| Quarterly balance-sheet columns are period-end snapshots; quarterly flows are single-quarter | flow-based quarterly ratios are TTM over 4 consecutive quarters with 5-point average balances (existing `window`/`average` helpers), else 2-point for annual, else `-end` fallback |

## R-004 Magnitude sanity (anchors, FY2025 capture)

Computed by hand from the captured FY2025 lines while designing the formulas: MBB CIR =
19,681/67,693 = 29.1 %, LDR = 1,084,019/921,368 = 117.7 %, NIM ≈ 3.3 % (net_interest_income
51,610 bn over ~1.55 × 10¹⁵ VND average earning assets); VNM current ratio = 36,261/18,520 = 1.96,
inventory turnover ≈ 5.5, ROCE ≈ 33 %; SSI current ratio = 89,323/61,902 = 1.44. These live in the tests as
hand-computed exact expectations from fixture numbers (SC-1) and as documented ranges (SC-3) —
they are not ingestion filters.

## R-005 Why derive rather than re-ingest

Constitution I (a stored fact is the fact it claims to be) and the Q-57 lesson: a provider's
pre-computed ratio is an opaque claim tied to that provider's period labelling. A derivation from
already-anchored statement lines is reproducible, carries a rule id the FE can explain
(`reason-codes.ts`), and the verifier can recompute it from the same raw facts.
