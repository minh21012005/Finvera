# Research: Provider Data Expansion

**Feature**: `008-provider-data-expansion` · **Created**: 2026-08-30
**Evidence tooling**: `tools/market-data/provider-poc/probe_balance_sheet.py`
(new, read-only) on pinned `vnstock==4.0.6`, KBS source, Community tier; raw
probe output kept outside the repository. No credential, token, or raw payload
appears here — only `item_id`s, labels, units and sample magnitudes.

## R-001 — KBS `balance_sheet` is not available (closes Open Question 1)

**Finding**: `Finance(symbol, source="kbs").balance_sheet(period=…)` returns an
**empty frame for every probed symbol (VNM, FPT, HPG, MBB) and both periods**.
vnstock does implement the call (`explorer/kbs/financial.py`, report type
`CDKT`, request `{'type': 'CDKT', 'unit': 1000, 'termtype': …}`), and the API
answers with no rows. The earlier VCI capability probe
(`poc-output/vci/vnstock-fundamentals-capability-summary.json`) fails at
construction (`UnboundLocalError`) for every statement in this pinned version,
so no alternative accepted source exists either.

**Decision**: `EQUITY_ATTRIBUTABLE_TO_PARENT`, `TOTAL_DEBT` and
`CASH_AND_EQUIVALENTS` **cannot be imported** in this feature. Consequently
`ev = marketCap + totalDebt − cashAndEquivalents` stays unbuildable and
`EV_EBITDA` remains `MISSING` with valuation-v1's existing `MISSING_EV_INPUTS`
disclosure. `spec.md` FR-001 is **narrowed** (see "Spec amendments" below):
US1 delivers `EBITDA`/`EBITDA_TTM` as accepted, displayed facts and keeps the
EV/EBITDA gap honestly disclosed; publishing `EV_EBITDA` is deferred until a
source with a balance sheet is accepted under its own gate.

**Alternatives rejected**:

- Deriving `TOTAL_DEBT = debt_to_equity% × (BVPS × shares)`: `debt_to_equity`
  ("Nợ vay trên VCSH") is indeed interest-bearing debt over equity, so this
  half is derivable — but `CASH_AND_EQUIVALENTS` has no ratio path
  (`cash_ratio` divides by current liabilities, also unavailable), so EV would
  still be incomplete. Deriving one input to still withhold the metric adds
  reproducibility surface for no published output.
- Using KBS's own `ev_ebitda` ratio as the metric: it is computed by the
  provider against *its* period-end market value, not Finvera's current
  price, so it would silently disagree with `PE`/`PB` computed at today's
  close and would change the contract's formula. Rejected without a
  valuation-v2 decision.

## R-002 — Cash flow is annual-only; FCF derivation (closes US2 inputs)

**Finding**: `cash_flow(period="quarter")` returns an empty frame; `period="year"`
returns 50 items with columns `['item', 'item_id', '2025-Năm', '2024-Năm', …]`.
Confirmed `item_id`s and sample magnitudes (base VND, negative = outflow):

| `item_id` | Label | VNM 2025 | FPT 2025 |
|---|---|---|---|
| `operating_cash_flow` | Lưu chuyển tiền thuần từ HĐKD | 8,827,273,177,000 | 5,053,831,757,000 |
| `payment_for_fixed_assets_constructions_and_other_long_term_assets` | 1. Tiền chi để mua sắm, xây dựng TSCĐ… | −1,456,914,053,000 | −3,215,243,201,000 |
| `depreciation_of_fixed_assets_and_investment_properties` | Khấu hao TSCĐ và BĐSĐT | 2,095,449,859,000 | 1,833,064,499,000 |
| `investing_cash_flow` | Lưu chuyển tiền thuần từ HĐ đầu tư | 3,472,771,515,000 | 5,757,231,937,000 |

MBB (bank) reports `operating_cash_flow` and `investing_cash_flow` but **no**
capex or depreciation rows — bank statements differ in shape (G-01 already
noted this), so FCF is legitimately `MISSING` for banks.

**Decision** (`kbs-fcf-ocf-plus-capex-v1`):
`FREE_CASH_FLOW = operating_cash_flow + payment_for_fixed_assets…` (the capex
item is already signed negative by the provider, so the derivation is an
addition; the exporter never flips signs). Emitted only when **both** inputs are
present for the same period column; otherwise not emitted (Java then reports
`MISSING`/`NOT_REPORTED`). Because the fact is annual-only, the fundamentals
summary reads it from the **latest accepted annual report** when the newest
period (a quarter) lacks it — recorded as a rule change in
`fundamental-summary-v1`'s *selection* logic only, not its arithmetic.

## R-003 — EBITDA derivation from ratio × net revenue (US1 inputs)

**Finding**: no income-statement item carries EBITDA. The ratio dataset
carries `ebitda_net_revenue` ("Tỷ lệ lãi EBITDA", **percent**, VNM 2026-Q2 =
21.73) and `ebit_margin` (18.88 %). Income statement carries **two rows with
`item_id = "revenue"`**: "1. Doanh thu bán hàng và cung cấp dịch vụ" (gross,
16,953,231,538,000) and "3. Doanh thu thuần về bán hàng và cung cấp dịch vụ"
(net, 16,968,084,098,000 for VNM 2026-Q2). Ratio quarter columns are
unordered and pandas-deduplicated (`'2026-Q2', '2025-Q4', '2026-Q1',
'2025-Q4_1'`); the exporter's period parser already ignores non-matching
columns, so `_1` duplicates are skipped.

**Decision** (`kbs-ebitda-margin-x-net-revenue-v1`):
`EBITDA(period) = ebitda_net_revenue(period) / 100 × net_revenue(period)`,
rounded once to 6 decimal places, emitted only when both are present for the
same period. `net_revenue` is the `revenue` row whose label contains
"thuần"; the gross row is dropped. **This also fixes a latent exporter defect**:
both `revenue` rows were previously mapped to `REVENUE` for the same period,
leaving the persisted `REVENUE` value dependent on row order.

`EBITDA_TTM` then follows the existing four-quarter sum; if any quarter lacks
the ratio point (ratio coverage is not guaranteed for every quarter), TTM is
`MISSING`/`INSUFFICIENT_HISTORY` — honest, never interpolated.

**Rejected**: `EBIT + depreciation` (depreciation is annual-only, would force
annual EBITDA and break the quarterly TTM chain).

## R-004 — Annual period columns were never importable (latent defect)

**Finding**: KBS annual frames label periods `YYYY-Năm`; the exporter's
`YEAR_COLUMN = ^\d{4}$` never matched, so `--period year` exports produced no
records. Quarter exports (the refresh default) were unaffected.

**Decision**: accept `^(\d{4})(?:-Năm)?$` as `ANNUAL`; `export_all_symbols.py`
exports **both** a quarter and an annual package per symbol (annual is the
only carrier of cash-flow facts). `StockImportConfiguration`'s directory scan
already imports every `fundamentals-*.json`, and quarter/annual reports are
distinct `PeriodKey`s, so no Java import change is needed for coexistence.

## R-005 — Derivation provenance on the wire and in the row

**Decision**: derived records carry `"derivation": "<rule-id>"` in the package
(inside `canonicalRecord`, so it is checksummed), and Java stores it as the
metric's `quality_reason` (`fundamental_report_metric.quality_reason`,
varchar(64)) on a `DEFINED` row. The column was already nullable free text on
`DEFINED` rows; this gives DATA-002 reproducibility without a migration.

## R-006 — Session price limits and foreign room (US3)

**Finding** (existing evidence, `poc-output/tcbs-capability-summary.json`):
`s|4` reference frames carry `ceilPrice`/`floorPrice`; REST `tickerCommons`
items carry `ceilPrice`, `floorPrice`, `room`, all numeric; Feature 001's
contract already parses `refPrice` from the same frame in base VND (R-015
sanity check applies).

**Decision**: map `ceilPrice`/`floorPrice` from `s|4` and `room` from the
snapshot into the live quote's session facts, subject to the same
trading-date reset as the reference price (Q-24). They ride the existing
`equity_price_observation`-independent session cache — **not persisted**
(closes Open Question 2: the observation row records the accepted matched
price; limits are session context shown with the live quote's timestamp and
disclosed `PRICE_LIMITS_UNAVAILABLE` when no live frame exists). Persisting
them can be revisited if a user story needs limit history.

## Spec amendments recorded by this research

- FR-001 → "The system MUST publish `EBITDA`/`EBITDA_TTM` as accepted facts
  and MUST keep `EV_EBITDA` withheld with `MISSING_EV_INPUTS` until a
  balance-sheet source is accepted; it MUST NOT approximate EV." US1's
  acceptance scenario 1 becomes: `EBITDA_TTM` is `DEFINED` for a symbol with
  four consecutive quarterly ratio points, `EV_EBITDA` still discloses the
  missing EV inputs.
- Open Question 1 → resolved (unavailable). Open Question 2 → resolved
  (not persisted).
- SC-001 → measures the share of listed symbols with `EBITDA_TTM = DEFINED`
  (today zero) instead of `EV_EBITDA`.

## Constitution check (post-research)

- I Deterministic core: derivations are versioned rule ids, decimal only, no
  LLM. ✔
- II Provenance: rule id + inputs stored per row; probe evidence recorded. ✔
- III Boundaries: no new module/provider/host. ✔
- VI Testing: exporter unit tests for both derivations, the net-revenue
  selection and the annual column; Java tests for annual fallback and
  derivation pass-through; mapper/service tests for limits. ✔
- VIII Simplicity: out-of-scope fields stay unmapped. ✔
