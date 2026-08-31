# Research: Fundamental statements from VCI

Method: owner-authorized read-only probes on 2026-08-31 (vnstock 4.0.7 in an
ephemeral environment; fixtures captured to the exporter test directory),
judged against audited figures the maintainer can vouch for and against the
data's own arithmetic identities. No label was trusted without an anchor.

## R-001 — Why VCI (decision context)

KBS statement pages carry another period's content (Feature 011 R-009):
yearly frames mirrored, quarterly frames permuted — 20/20 symbols, 0/4
quarter labels correct; quarterly ratio frame shifted one period. vnstock
4.0.7 does not change it (the KBS API pairs `Head` and `Content`
inconsistently). ADR-0011 records the options; VCI was chosen.

## R-002 — Anchors: VCI labels are right

| Anchor | VCI | Reference |
|---|---|---|
| VNM net revenue FY2022 / 2023 / 2024 / 2025 | 59,956.2 / 60,368.9 / 61,782.6 / 63,645.9 bn | audited FY2022–FY2024 figures; FY2025 = Σ 4 quarters |
| VNM net profit FY2025 | 9,413.6 bn = Σ(2025-Q1…Q4) **to the VND** | arithmetic identity |
| VNM 2024 quarters (net revenue) | Q1 14,125 / Q2 16,656 / Q3 15,549 / Q4 15,453 → 61,783 | equals audited FY2024 |
| MBB EPS FY2022 | 3,856 | audited |
| MBB total operating income / net profit FY2025 | 67,693.0 / 27,383.0 bn = Σ 4 quarters to the VND | identity |
| HPG parent profit FY2023 / FY2024 | 6,835.1 / 12,021.4 bn | audited |
| FPT parent profit FY2022–FY2024 | 5,310.1 / 6,465.2 / 7,856.8 bn | audited |
| Shares from balance sheet | VNM `paid_in_capital` 20,899,554,450,000 / 10,000 = **2,089,955,445** = profile `shares_outstanding`; BVH 742,322,764 = profile; MBB `charter_capital` 80,550 bn → 8,055,000,000 vs profile 8,054,999,909; SSI 2026-Q2 `paid_in_capital` 25,030.9 bn − treasury 19.1 bn → 2,501.18 m vs profile 2,501.10 m | par value 10,000 VND (Law on Securities) |

## R-003 — What VCI returns (vnstock 4.0.7, community)

| Dataset | Periods | Labels | Unit | Notes |
|---|---|---|---|---|
| `income_statement(year)` | 8 (2018–2025) | `YYYY` | VND | ids differ by company type (R-004) |
| `income_statement(quarter)` | 8 (2024-Q3…2026-Q2) | `YYYY-QN` | VND | contiguous, Q4 present |
| `balance_sheet(year/quarter)` | 8 / 8 | same | VND | **available** (KBS had none): total assets, liabilities, borrowings, cash, equity, paid-in/charter capital, treasury shares, minority interest |
| `cash_flow(year/quarter)` | 8 / 8 | same | VND | OCF, capex, dividends paid, D&A; quarterly available (KBS quarterly cash flow was null) |
| `ratio(year/quarter)` | malformed | 16 columns all named `2018` / only `2018-Q1…Q4` | — | vnstock flattens VCI's multi-index wrongly → **not ingested**; ratios are derived in Finvera (contract) |
| `eps_basic_vnd` | statement row | VND per share | — | quarterly EPS present for non-financials and insurers; **0.0 for banks and some brokers in quarters** → trailing EPS derivation needed (R-005) |

Timing: 32 calls, mean 2.28 s, max 6.61 s, no rate-limit response at 0.6 s
spacing. A full symbol needs 6 calls (3 statements × 2 periods); 1,522
symbols ≈ 9,100 calls ≈ 6 h sequential at the observed latency — run
overnight or with 2–3 workers (rate limit to be observed on the first full
pass; the exporter keeps Q-39 pacing).

## R-004 — Item ids by company type (from the fixtures)

| Finvera code | Non-financial (VNM) | Bank (MBB) | Insurer (BVH) | Broker (SSI) |
|---|---|---|---|---|
| detection | `net_sales` & `cost_of_sales` | `net_interest_income` | `net_sales_from_insurance_business` | `operating_sales` |
| REVENUE | `net_sales` | `total_operating_income` | `net_sales_from_insurance_business` | `net_sales` |
| GROSS_PROFIT | `gross_profit` | — | `gross_insurance_operating_profit` | `gross_profit` |
| OPERATING_PROFIT | `operating_profit_loss` | `net_operating_profit_before_allowance_for_credit_losses` | — | `operating_profit_loss` |
| NET_PROFIT | `net_profit_loss_after_tax` | `net_profit_loss_after_tax` | `profit_after_tax` | `net_profit_loss_after_tax` |
| parent profit (derivation input) | `attributable_to_parent_company` | `attributable_to_parent_company` | `net_profit_attributable_to_shareholders_of_the_group` | `net_profit_loss_after_tax` − minority row (`business_income_tax_deferred` is mislabelled for the minority line in SSI's frame — use total NPAT) |
| EPS | `eps_basic_vnd` | `eps_basic_vnd` (annual only) | `eps_basic_vnd` | `eps_basic_vnd` |
| D&A (EBITDA input) | CF `depreciation_and_amortization` | — | — | — |
| TOTAL_ASSETS | BS `total_assets` | `total_assets` | `total_assets` | `total_assets` |
| equity (BVPS/ROE input) | BS `owners_equity` − `minority_interests` | `owners_equity` − `minority_interest` | `owners_equity` | `owners_equity` |
| shares | `paid_in_capital` (or `common_shares`) − `treasury_shares` | `charter_capital` − `treasury_shares` | `paid_in_capital` − `treasury_shares` | `paid_in_capital` − `treasury_shares` |
| TOTAL_DEBT | `short_term_borrowings` + `long_term_borrowings` | — (not meaningful) | — | `short_term_borrowings` + `long_term_borrowings` |
| CASH_AND_EQUIVALENTS | `cash_and_cash_equivalents` | — | `cash_and_cash_equivalents` | `cash_and_cash_equivalents` |
| OCF | CF `net_cash_inflows_outflows_from_operating_activities` | `net_cash_from_operating_activities` | same as non-fin | same as non-fin |
| capex | CF `purchases_of_fixed_assets_and_other_long_term_assets` | `purchases_of_fixed_assets_and_other_long_term_assets` | same | same |
| dividends paid | CF `dividends_paid` | `dividends_paid` | same | same |

Ids repeat within a frame in a few places (SSI `short_term_borrowings`,
`business_income_tax_deferred`); the exporter takes the **first** occurrence
and records the ambiguity in the contract.

## R-005 — Derivations needed (why)

- Banks report EPS only annually → quarterly EPS_TTM must be derived:
  `TRAILING_EPS = Σ parent profit (4 quarters) / shares(period end)`.
- KBS BVPS used total equity; the standard definition (equity attributable to
  the parent / shares outstanding) is adopted and documented; P/B values move
  accordingly for companies with minority interests.
- EBITDA = operating profit + D&A from the cash-flow statement (non-financials
  only), replacing KBS's margin-based approximation.
- ROE/ROA TTM = parent profit TTM / average of period-end equity/assets over
  the five balance sheets spanning the window (fallback: end values when the
  window is incomplete, flagged).

## R-006 — Ingestion consequence

`fundamental_report` is keyed with `source`; VCI rows would otherwise sit
beside current KBS rows for the same period. Decision (FR-005): a different
source's current row is superseded when the new source lands
(`SOURCE_SUPERSEDED`), which retires every KBS statement/ratio fact once the
universe is re-imported. Revision chains keep the KBS rows queryable.

## R-007 — Symbols with annual-only statements on VCI (seen during the first crawl)

A32, ACE, BCP (and, by the checkpoint, a sizeable share of small UPCoM names)
return **no quarterly** income statement / balance sheet / cash flow from VCI
while their yearly statements are complete (8 years). The exporter records a
named, settled failure (`NoStatementsAvailable`) for the quarterly package and
writes the annual one; `fundamental-summary-v2` then works on the annual basis
(`ANNUAL_BASIS`, disclosed) — never on a KBS quarter, because the default import retires the KBS rows for
those periods (contract I-3). Such a symbol is
not a settled failure: the crawl stamps `fundamentals_checked_at` and tries again
automatically once 35 days have passed (`UNAVAILABLE_RECHECK_DAYS`), so a company
that starts filing quarterly statements is picked up without any flag. First-crawl
observation: 192 of the first ~280 symbols (alphabetically A–B, mostly small UPCoM
names) were annual-only; the universe-wide share is recorded in T007 from the
checkpoint.

## R-008 — Failure classes in the universe exporter (found 2026-08-31 during the first VCI crawl)

**Observation.** `[289/1520] DCH daily_bars: FAILED (ValueError)` with the provider log
`API request failed: ('Connection aborted.', ConnectionResetError(10054, ...))`. vnstock wraps a
dropped connection in a `ValueError`; the exporter recorded `failed:ValueError`, which
`is_finished` treats as a *settled* failure — never retried without `--retry-failed`. The same
checkpoint held 153 `fundamentals: failed:ValueError` entries written by the run that was stopped
before `NoStatementsAvailable` existed (annual-only symbols), also settled forever, and 51
`daily_bars: failed:ValueError` entries of unknown age.

**Root cause.** Two conflations: (1) a network event was classified by the wrapper's exception
type instead of by what happened; (2) a settled failure was not tied to the exporter version that
produced it, so a fix in the exporter could never un-settle it.

**Rules (export_all_symbols.py, tests in test_export_all_symbols.py).**

| Rule | Statement |
|---|---|
| F-1 network is transient | `classify_failure` walks the exception cause/context chain; a connection/timeout type or a message matching the network pattern (`connection aborted`, `forcibly closed`, `max retries`, `timed out`, `api request failed`, …) is `NetworkError`, retried in-run after 5 s and 20 s, and if still failing recorded as `failed:NetworkError` — transient, retried on the next run. |
| F-2 version-scoped settlement | Every recorded failure carries `<key>_failed_tool_version`; `is_finished` settles a failure only when that version equals the current exporter version for the dataset. Entries without the field (all pre-existing ones) are retried once and then settle with the version. |
| F-3 unchanged | `NoStatementsAvailable` still re-checks after 35 days (R-007); rate limits still wait a full quota window. |

**Consequence for the running crawl.** The running process keeps the old code; the next
`.efresh-data.ps1` retries the ~200 unsettled entries once (≈ 3 calls each, ~25 min) and DCH's
bars are filled then. No owner flag is needed.

