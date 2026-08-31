# Contract: kbs-yearly-statement-orientation-v1

**Scope**: every annual (`period="year"`) row the exporter takes from the KBS
**income statement** and **cash flow** endpoints via vnstock 4.0.6
(`Finance(source="kbs").income_statement / .cash_flow`). Not the ratio
endpoint; not quarterly frames.

## Fact (Feature 011 research R-009, 2026-08-31)

vnstock pairs `Value1…Value4` with the response `Head` entries sorted by `ID`.
For yearly *statements* the KBS payload lists the heads in the opposite order
from the values, so the frame column labelled `"<Y>-Năm"` holds fiscal year
`2022 + 2025 − Y`:

| label KBS gives | figure it actually holds | evidence |
|---|---|---|
| `2025-Năm` | FY2022 | VNM net revenue 59,956 bn = audited FY2022; MBB EPS 3,856 = FY2022; HPG parent profit 8,483 bn = FY2022 |
| `2024-Năm` | FY2023 | VNM 60,369 bn; HPG 6,835 bn; FPT 6,465 bn |
| `2023-Năm` | FY2024 | VNM 61,783 bn; HPG 12,021 bn; FPT 7,857 bn |
| `2022-Năm` | FY2025 | VNM 63,646 bn (KBS's own growth row +3.02 % = 63,646 / 61,783 — G-11 explained) |

Cash flow carries the same mirror (its `profit_before_tax` row equals the
income statement's column for column). The yearly **ratio** frame is
correctly labelled (FPT P/E 26.7 under `2024-Năm` is the 2024 peak; HPG P/E
23.8 under `2023-Năm` is the low-profit 2023) and is **not** touched.
Database signature before the fix: of 838 instruments with positive Q3+Q4 2025,
737 had "FY2022" ≥ that half-year and 280 had "FY2025" below it.

## Rule

| # | Rule |
|---|---|
| O-1 | After fetching a yearly income-statement or cash-flow frame, the exporter mirrors the sequence of year labels (`[2025, 2024, 2023, 2022] → [2022, 2023, 2024, 2025]`, positionally) before any mapping. Values are never moved or altered; only labels change. |
| O-2 | Frames with fewer than two annual columns, quarterly frames and the ratio frame are returned unchanged. |
| O-3 | Every annual `INCOME_STATEMENT` / `CASH_FLOW` record produced from a mirrored frame carries `derivation = "kbs-yearly-statement-labels-mirrored-v1"`, which the importer stores as the fact's `quality_reason` (provenance, Constitution II). Derived annual facts (EBITDA, FCF) are computed from the relabelled columns and keep their own derivation ids. |
| O-4 | The rule is versioned with the provider library: if vnstock or KBS changes the head/value pairing, this contract is superseded (v2), never silently adjusted. Verification recipe: VNM `revenue` under the relabelled `2022-Năm` must read 59,956,247,197,000. |

## Consequences

Annual-basis metrics (`ANNUAL_BASIS` TTM and YoY growth, `PS`, turnover
ratios, annual FCF, own-history valuation points before the four visible
quarters) were computed on mislabelled years until the universe is
re-exported and re-imported (restatements, `CORRECTED`). See
docs/REMEDIATION_PLAN.md Q-57.
