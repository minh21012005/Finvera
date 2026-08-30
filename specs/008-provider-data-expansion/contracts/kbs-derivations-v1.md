# Contract: KBS derived fundamental facts (`kbs-derivations-v1`)

**Feature**: `008-provider-data-expansion` · **Status**: Accepted 2026-08-30
**Owner**: `tools/market-data/vnstock-export/export_fundamentals.py` (producer),
`finvera-be` fundamentals import (consumer). Amends `vnstock-fundamentals-v1`
additively; the Java catalog (`fundamental-metric-catalog-v1`) is unchanged.

## U-1 Determinism and precision

Every derivation is decimal arithmetic on provider values converted with
`Decimal(str(value))`; the result is quantised once to 6 decimal places
(`ROUND_HALF_EVEN` per Python `Decimal` default) before packaging. No float.

## U-2 Same-period rule

A derived value exists for a period column only when **every** input exists
for that exact column. Inputs are never carried across periods, interpolated,
or defaulted. A period with a missing input produces **no record**; the
consumer reports the metric `MISSING`/`NOT_REPORTED`.

## U-3 Provenance

Each derived record carries `"derivation": "<rule id>"`, included in its
checksummed `canonicalRecord`. `finvera-be` stores the rule id as
`fundamental_report_metric.quality_reason` on the `DEFINED` row.

## Rules

### `kbs-ebitda-margin-x-net-revenue-v1` → `EBITDA`

```text
EBITDA(p) = ratio.ebitda_net_revenue(p) / 100 × income_statement.net_revenue(p)
```

- `ebitda_net_revenue` is a percentage ("Tỷ lệ lãi EBITDA").
- `net_revenue` is the `revenue` row whose label contains "thuần" when the
  frame carries both gross and net rows; the gross row is dropped and is
  **never** mapped to `REVENUE`. A frame with a single `revenue` row uses it.
- Emitted per quarter or per year, whichever the source frame is.
- `EBITDA_TTM` = existing `fundamental-summary-v1` four-quarter sum.

### `kbs-fcf-ocf-plus-capex-v1` → `FREE_CASH_FLOW`

```text
FREE_CASH_FLOW(p) = cash_flow.operating_cash_flow(p)
                  + cash_flow.payment_for_fixed_assets_constructions_and_other_long_term_assets(p)
```

- The capex item is provider-signed negative (outflow); the rule adds it and
  never flips signs.
- Available only where the provider publishes cash flow (annual for KBS
  community). Banks lacking the capex item yield no record.

## Period columns

`^(\d{4})-Q([1-4])$` → `QUARTER`; `^(\d{4})(?:-Năm)?$` → `ANNUAL`. Any other
column (e.g. pandas `_1` duplicates) is ignored.

## Versioning

A change to any formula, rounding, input selection, or sign convention is a
new rule id (`…-v2`); existing accepted rows keep their recorded id.

## Test vectors

| Case | Inputs | Expected |
|---|---|---|
| EBITDA VNM 2026-Q2 | margin 21.73, net revenue 16,968,084,098,000 | `3687164674495.400000` |
| EBITDA with margin but no revenue for the column | — | no record |
| FCF VNM 2025 | OCF 8,827,273,177,000; capex −1,456,914,053,000 | `7370359124000.000000` |
| Bank without capex row | OCF only | no record |
| Two `revenue` rows | gross + "thuần" | only net → `REVENUE` |

## Amendment 2026-08-30 (Feature 011) — `kbs-fcf-ocf-plus-capex-v2` → `FREE_CASH_FLOW`

Same formula, sign convention and same-period rule as v1; the input ids are
now ordered lists (first present wins):

- OCF: `operating_cash_flow`, then `net_cash_flows_from_securities_trading_activities` (securities).
- capex: `payment_for_fixed_assets_constructions_and_other_long_term_assets`, then
  `n_1_payment_for_fixed_assets_constructions_and_other_long_term_assets` (insurance).

Banks (`purchase_of_fixed_assets`) are deliberately excluded — deposit-driven
OCF makes the figure meaningless. Rows derived under v1 keep their id.
