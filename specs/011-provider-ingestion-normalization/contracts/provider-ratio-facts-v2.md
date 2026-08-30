# Contract: Provider-reported ratio facts (`provider-ratio-facts-v2`)

**Feature**: `011-provider-ingestion-normalization` · Accepted 2026-08-30
**Supersedes**: `provider-ratio-facts-v1` (Feature 009) for packages with
`toolVersion >= 0.6.0`. Rows already imported under v1 keep their recorded
provenance. **Producer**: `export_fundamentals.py` · **Consumer**: `finvera-be`
fundamentals import, summary, screener. Catalog unchanged.

## U-1 Stored as observed (unchanged)

## U-2 Period scope decides which columns a ratio may come from

KBS quarter columns hold **single-quarter** values for flow-over-stock ratios
(research R-003). A ratio is emitted only from the columns where its meaning
matches the catalog:

| Code | `item_id` | Quarter columns | Annual columns |
|---|---|---|---|
| `ROE` | `roe_trailling` (quarter) / `roe` (annual) | ✔ trailing, `derivation = kbs-trailing-ratio-as-annualized-v1` | ✔ |
| `ROA` | `roa_trailling` (quarter) / `roa` (annual) | ✔ trailing, same rule id | ✔ |
| `ROE_TTM`, `ROA_TTM` | `roe_trailling`, `roa_trailling` | ✔ | ✘ (provider placeholder 0.0) |
| `ROCE` | `return_on_capital_employed_roce` | ✘ | ✔ |
| `NIM` | `net_interest_margin_nim` | ✘ | ✔ |
| `TOTAL_ASSET_TURNOVER`, `INVENTORY_TURNOVER`, `RECEIVABLES_TURNOVER` | as v1 | ✘ | ✔ |
| `PS` | `ps_ratio` | ✘ | ✔ |
| `DIVIDEND_YIELD` | `dividend_yield` | ✘ | ✔ **as reported (percent)** — the v1 ×100 derivation is withdrawn |
| `TOTAL_ASSETS_GROWTH_PERCENT`, `EQUITY_GROWTH_PERCENT` | growth rows | ✘ | ✔ |
| margins, leverage, liquidity, `INTEREST_COVERAGE`, `BVPS`, `TRAILING_EPS`, `BETA`, `COST_INCOME_RATIO`, `LOAN_TO_DEPOSIT`, `DEBT_TO_EQUITY` | as v1 | ✔ | ✔ |

Units and signs per code are those of v1 U-2.

## U-3 Exclusions (unchanged, plus)

Provider valuation ratios, the zero-only cash-flow family, and the provider's
growth rows other than the two above are never mapped (research R-007).

## U-4 Consumer rule

`fundamental-summary-v2` reads the annual-only codes from the newest report
when present, else from the latest annual report, and marks the latter
`ANNUAL_BASIS`.

## Test vectors (from the 2026-08-30 probe)

| Case | Input | Expected |
|---|---|---|
| VNM quarter `roe` 6.86, `roe_trailling` 26.37 | quarter frame | `ROE = 26.37` (rule id), `ROE_TTM = 26.37`; no 6.86 row |
| VNM annual `roe` 26.64, `roe_trailling` 0.0 | annual frame | `ROE = 26.64`; no `ROE_TTM` row |
| VNM annual `dividend_yield` 7.92 | annual frame | `DIVIDEND_YIELD = 7.92`, no derivation |
| VNM quarter `dividend_yield` 0.04 | quarter frame | no row |
| MBB quarter `net_interest_margin_nim` 1.0 / annual 3.89 | both | only `NIM = 3.89` (annual) |
