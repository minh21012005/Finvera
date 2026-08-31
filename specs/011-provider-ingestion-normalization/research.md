# Research: Provider Ingestion Normalization

**Feature**: `011-provider-ingestion-normalization` · 2026-08-30
**Method**: owner-authorized, read-only probes against the accepted provider
(vnstock 4.0.6 / KBS) for a representative sample — VNM, FPT, HPG
(non-financial), MBB, VCB (banks), SSI (securities), BVH (insurance), VIC (real
estate), PVS (HNX), ACV (UPCoM) — every dataset (income statement, ratio, cash
flow, balance sheet; quarter and year), company overview, daily bars, listing.
Raw KBS pages were also read through vnstock's `_fetch_financial_data` to check
labels against values. Probe scripts are session-local; no database access, no
credentials.

## R-001 — Daily bars: the provider's `end` date is not inclusive

| requested `end` (VNM, start 2026-08-24) | last bar returned |
|---|---|
| 2026-08-27 (Thu) | 2026-08-27 |
| 2026-08-28 (Fri) | 2026-08-27 |
| 2026-08-29 (Sat) | 2026-08-27 |
| 2026-08-30 (Sun) | 2026-08-28 |
| 2026-08-31, 2026-09-02 | 2026-08-28 (no future rows) |

`export_all_symbols.py` defaults `--end` to today, so a routine run never
receives the most recent session (the full-universe checkpoint shows
`daily_bars_range` ending 2026-08-26 while VNM's package stops at 2026-08-25).
The lag self-heals only on the next run through the 90-day lookback, and
`StockFreshnessPolicy` then reports `DELAYED` for a day that was available.

**Decision**: request `end + 3 days` from the provider and keep only rows with
`tradingDate <= end`. Verified that the provider returns nothing after the last
completed session, so padding cannot import future or partial bars. Bars tool
version unchanged (record shape and unit untouched).

## R-002 — Company overview: `free_float_percentage` is not a free float

vnstock maps KBS `KLCPNY` → `free_float_percentage` and `SFV` → `free_float`.
Observed for all ten symbols: `free_float_percentage == outstanding_shares ×
par_value` (VNM 20,899,554,450,000 = 2,089,955,445 × 10,000) and `free_float ==
10000` (the par value). The provider exposes **no** free-float figure.
`equity_profile.free_float_ratio` carries `check (between 0 and 1)`; the
Feature 010 exporter as first written would have violated it on the next
import.

`outstanding_shares` is internally consistent for every symbol:
`charter_capital (bn VND) × 1e9 / par_value` matches to the rounding of the
displayed charter capital (VNM 20,900 bn → 2.09e9).

**Decision**: never emit `freeFloatRatio`. Emit `sharesOutstanding` and verify
it against `charter_capital / par_value`; a mismatch > 1 % keeps the count but
stamps `SHARES_OUTSTANDING_UNVERIFIED`.

## R-003 — Ratio dataset: quarter columns are period-scoped for flow ratios

Quarter vs annual values (same symbol, same provider row):

| item_id | VNM 2026-Q2 | VNM 2025-Năm | MBB 2026-Q2 | MBB 2025-Năm |
|---|---|---|---|---|
| `roe` | 6.86 | 26.64 | 4.27 | 20.67 |
| `roa` | 4.55 | 17.37 | — | — |
| `roe_trailling` | 26.37 | **0.0** | 20.22 | **0.0** |
| `return_on_capital_employed_roce` | 8.6 | 33.55 | | |
| `net_interest_margin_nim` | | | 1.0 | 3.89 |
| `total_asset_turnover` | 0.31 | 1.17 | | |
| `inventory_turnover` | 1.48 | 5.98 | | |
| `ps_ratio` | 7.4 | 2.01 | | |
| `dividend_yield` | 0.04 | 7.92 | 0.01 | 1.19 |
| `gross_margin` / `net_margin` / `ebit_margin` | 41.8 / 14.81 / 18.88 | 41.18 / 14.79 / 18.82 | | |
| `cost_income_ratio_cir` | | | 29.14 | 29.07 |
| `debt_to_equity`, liquidity ratios, `bvps`, `beta` | point-in-time, consistent | | | |

Consequences of the current mapping:

- `ROE`/`ROA` in Finvera come from the newest report, which is a quarter →
  VNM shows ROE 6.86 % while its trailing ROE is 26 %; the screener filter
  "ROE ≥ 15" excludes it. Same for `ROCE`, `NIM`, turnovers, `PS` (price over
  one quarter's revenue, ×4 too high).
- `roe_trailling`/`roa_trailling` are **0.0 placeholders** in the annual
  dataset; the Feature 008 annual pass stored `ROE_TTM = 0` facts.
- `dividend_yield` quarter columns are unit-inconsistent: the column holding
  the most recent Q1 equals the annual percent (7.92) while other quarters read
  ≈ annual/100 (0.04, 0.05). The Feature 009 ×100 "fraction→percent"
  derivation was fitted to that coincidence and is wrong (VNM 2026-Q2 → 4 %
  vs the provider's own 7.92 % for 2025). Annual columns are consistently
  percent.

**Decision** (`provider-ratio-facts-v2`):

- Quarter columns: `roe_trailling` → `ROE` **and** `ROE_TTM`; `roa_trailling`
  → `ROA` and `ROA_TTM` (rule id `kbs-trailing-ratio-as-annualized-v1` on the
  `ROE`/`ROA` rows). Quarter-scoped `roe`, `roa`, `roce`, `nim`, turnovers,
  `dividend_yield`, `ps_ratio`, growth rows are **not** emitted from quarter
  columns.
- Annual columns: `roe`/`roa` → `ROE`/`ROA`; `roce`, `nim`, turnovers,
  `dividend_yield` (as reported, percent, no ×100), `ps_ratio`, growth rows.
  `roe_trailling`/`roa_trailling` never emitted from annual columns.
- Same-period ratios (margins, leverage, liquidity, coverage, BVPS, trailing
  EPS, beta, CIR, LDR) unchanged in both.
- `fundamental-summary-v2` reads the annual-only codes with the existing
  annual fallback and labels them `ANNUAL_BASIS` (v2 has not been executed
  against any database yet; its definition is amended here, not re-versioned).

## R-004 — Ratio dataset reliability: four columns, three usable

vnstock's `ratio()` requests `page_size = 4`, the mode its own source flags as
"KBS bug: duplicated IDs and mixed-up values". Raw pages read one at a time
(`page_size = 1`) for VNM and MBB give `2026-Q2`, `2026-Q1`, `2025-Q4`, then a
stale copy labelled `2025-Q4` again (page 4) — the `_1` column. The three
labelled columns match the raw single pages value-for-value (BVPS 18160 /
16499 / 18002), so labels are trustworthy; the fourth column is not and stays
excluded by the period regex. Income statements fetched one page at a time
reach `2025-Q3` on page 6 (page 4–5 are the same stale copies). Documented as
a provider limit; no code change.

## R-005 — Cash flow: quarterly is null at the source; balance sheet absent

Raw `LCTT` with `termType=2` returns 50 rows for VNM with every `Value1`
null, 0 rows for MBB. vnstock's empty quarter frame is therefore correct.
`CDKT` (balance sheet) returns nothing for any symbol (Feature 008 R-001
re-confirmed). No change.

## R-006 — Statement item ids differ by company type

| Concept | non-financial | bank | securities | insurance |
|---|---|---|---|---|
| net profit | `net_profit` | `net_profit` | `net_profit` | `profit_after_tax` (only id) |
| revenue | `revenue` (net row) | — (no revenue concept) | `revenue_from_securities_business_01_11` (`revenue` row is null) | `total_net_revenue_from_insurance_business` |
| operating profit | `operating_profit` | `operating_profit_before_provision_for_credit_losses` (pre-provision, not comparable) | `net_profit_from_securities_business_20_50_40_60_61_62` ("VII. Kết quả hoạt động") | `operating_profit` |
| EPS | `earnings_per_share_vnd` | `earning_per_share_vnd` (annual only; quarters null) | `earning_per_share_vnd` (annual only) | none in statement (`trailing_eps` in ratio) |
| OCF (annual) | `operating_cash_flow` | `operating_cash_flow` | `net_cash_flows_from_securities_trading_activities` | `operating_cash_flow` |
| capex (annual) | `payment_for_fixed_assets_constructions_and_other_long_term_assets` | `purchase_of_fixed_assets` | `payment_for_fixed_assets_…` | `n_1_payment_for_fixed_assets_…` |

**Decision**: map the insurance and securities revenue/profit ids listed above
(first emitted code per (metric, period) wins; the ids are disjoint across
company types in the sample). Bank revenue and bank operating profit stay
unmapped (no comparable concept). `kbs-fcf-ocf-plus-capex-v2` accepts the
securities OCF id and the insurance capex id; banks are deliberately excluded
(deposit-driven OCF makes "free cash flow" meaningless).

## R-007 — Provider growth rows disagree with statement arithmetic

Ratio row `net_revenue` ("Tăng trưởng doanh thu thuần") reports +3.02 % for
VNM 2025 while the provider's own income statement gives 59,956 bn vs 60,369
bn (−0.7 %). The basis of the provider's growth rows is undocumented; they
are not mapped. Finvera growth stays statement-derived
(`fundamental-summary-v2`). Open question G-11 for the owner: which revenue
row KBS uses for its growth figures.

## R-008 — Listing universe

`Listing(kbs).symbols_by_exchange()` returns 3,418 rows: 1,522 `stock`,
1,469 `corpbond`, 295 `cw`, 95 `bond`, 23 `fund`, 14 `future`; exchanges
HOSE 723, HNX 394, UPCOM 818, XHNF 14. The exporters already filter
`type == "stock"`; the 14 `XHNF` rows are futures. No change.

## R-009 — Yearly statement frames are mirrored at the provider (Q-57, 2026-08-31)

Found while measuring Feature 017 (history-basis consistency): DBC's "FY2025"
net profit read 5.2 bn against a Q4-2025 alone of 508 bn. Cross-checking the
2026-08-30 owner probe (`probe_mapping_audit.json`) against audited figures:
KBS's yearly **income statement** and **cash flow** columns are labelled in the
mirror image of their contents (`2025-Năm` = FY2022 … `2022-Năm` = FY2025), for
non-financials, banks, insurers and brokers alike; the yearly **ratio** frame is
correct. vnstock 4.0.6 (`explorer/kbs/financial.py`) pairs `Value{i}` with the
`Head` list sorted by `ID`, and for yearly statements that order is reversed
relative to the values. Consequences: every `ANNUAL_BASIS` metric, annual FCF
and ~99 % of own-history valuation points were built on the wrong years; the
earlier "provider vs DB 74/74" check trusted the labels and could not see it;
G-11 (KBS revenue growth +3.02 % vs our −0.7 % for VNM) is explained — KBS's
growth row was right, our arithmetic used mirrored years. Contract
`kbs-yearly-statement-orientation-v1`; exporter 0.7.0 mirrors the labels and
tags the records; universe re-export required (T008).

### R-009 addendum — quarterly pages are permuted, not mirrored (same evening)

Raw KBS pages for VNM (page_size=1, exactly as vnstock fetches): head says
`2026-Q2 / ReportDate 2026-07-30` but the content is 2025-Q3 (VCI: net sales
16,953.2 bn, EPS 1,084); `2026-Q1` holds 2025-Q4; `2025-Q4` holds **2025-Q1**
(12,934.5 bn / 1,587 bn / EPS 674); `2025-Q3` (page 6) holds 2026-Q2. Cross-check
of the database against VCI for 20 symbols (VNM, HPG, MBB, FPT, VCB, SSI, MWG, GAS,
ACV, PVS, DBC, DHC, VIC, BVH, CTG, VHM, TCB, MSN, VRE, POW): the same permutation
every time, **0/4 quarter labels correct**. The quarterly ratio frame is shifted as
well (yearly-2025 BVPS and trailing EPS appear under `2026-Q1`). vnstock 4.0.7 does
not change this (the KBS API itself pairs `Head` and `Content` inconsistently).
Consequence: no label-based rule can repair KBS quarterly statements; the mirror
rule of contract `kbs-yearly-statement-orientation-v1` stands for the yearly frames
only. VCI (working in vnstock 4.0.7) returns correctly labelled income statement,
balance sheet, cash flow and ratios, 8 periods deep, in raw VND — see the ADR.

## Constitution check

I ✔ mapping rules are versioned (`provider-ratio-facts-v2`,
`kbs-fcf-ocf-plus-capex-v2`, `kbs-trailing-ratio-as-annualized-v1`); II ✔
every reinterpretation carries a rule id or reason; VI ✔ fixed fixtures from
the probes above; VIII ✔ nothing estimated — unverifiable inputs (free float,
bank revenue, provider growth) are left out.
