# Research: Own-history basis consistency

Method: `tools/verification/history_basis_study.py` — rebuilds, for 20 symbols
(12 fixed + 8 random with ≥ 750 bars), the own-history series exactly as
`ValuationService.buildOwnHistorySeries` does (visibility by `observed_at` ≤
23:59:59 VN of each bar date; EPS_TTM = four newest visible quarters else the
newest visible annual EPS; Q-47 tie-break), and reports per symbol: how many
points are annual-basis, and on dates where both bases are computable, the
relative gap |PE_annual / PE_quarter − 1| and the shift of today's P/E
percentile if annual points were dropped. Read-only; 2026-08-31 database.

## R-001 — Composition of the series (measured 2026-08-31, before the Q-57 fix)

| symbol | points | annual-basis | % | both-basis dates | median gap | percentile shift if annual dropped |
|---|---|---|---|---|---|---|
| VNM | 750 | 739 | 99 % | 11 | 19.8 % | −1.0 pp |
| MBB | 750 | 750 | 100 % | 0 | – | – |
| SSI | 750 | 750 | 100 % | 0 | – | – |
| BVH | 750 | 0 (no EPS rows at all; production uses provider trailing EPS) | 0 % | 0 | – | – |
| HPG | 750 | 750 | 100 % | 0 | – | – |
| VIC | 750 | 750 | 100 % | 0 | – | – |
| FPT | 750 | 739 | 99 % | 11 | 17.2 % | +79.0 pp |
| ACV | 750 | 739 | 99 % | 11 | 43.8 % | +93.8 pp |
| PVS | 750 | 750 | 100 % | 0 | – | – |
| VCB | 750 | 739 | 99 % | 11 | 15.9 % | +0.8 pp |
| MWG | 750 | 739 | 99 % | 11 | 116.1 % | +47.2 pp |
| GAS | 750 | 739 | 99 % | 11 | 32.2 % | −13.9 pp |
| HHG, LSG, TOS, DQC | 750 | 750 | 100 % | 0 | – | – |
| DHC | 750 | 739 | 99 % | 11 | 10.5 % | +52.3 pp |
| DBC | 750 | 739 | 99 % | 11 | **17,047.6 %** | +56.2 pp |
| ILA | 750 | 739 | 99 % | 0 | – | – |
| BTS | 750 | 740 | 99 % | 10 | 43.8 % | −17.9 pp |

Reading: the provider supplies four fiscal periods, so quarterly EPS exists only
for the last four quarters and all four are visible only from ~2026-08-14; the
own-history series is therefore **uniformly annual-basis** (FY EPS) except the
last ~11 sessions. Symbols without quarterly EPS (banks, brokers, many
industrials) are annual-basis (or provider-trailing in production) throughout.
"Percentile shift if annual dropped" is meaningless with 11 remaining points and
is kept only to show the sensitivity.

## R-002 — The measurement exposed a data defect (Q-57)

DBC: "FY2025" net profit 5.19 bn vs Q4-2025 alone 508 bn; MWG "FY2024" profit
168 bn (MWG's real FY2023). Cross-checked against audited figures and the owner
probe: KBS yearly income-statement and cash-flow columns are labelled in the
mirror image of their contents (FY2022 under `2025-Năm` … FY2025 under
`2022-Năm`); the ratio frame is correct. Every annual-basis point above — and
every `ANNUAL_BASIS` metric in the product — was therefore built on the wrong
year. Fixed at the exporter (011 R-009, contract
`kbs-yearly-statement-orientation-v1`); the universe must be re-exported. The
gaps in R-001 are dominated by this defect (DBC's 17,048 % is FY2022's 5 bn
standing in for FY2025's 1,507 bn) and say nothing yet about the basis question.

## R-003 — Decision

Deferred until the re-export lands. Re-run the study; then choose (a) disclose
(`HISTORY_ANNUAL_BASIS`, share of annual points) if the FY-vs-TTM gap is small
and stable, or (b) `valuation-v3`: compute the own-history percentile of today's
P/E **on the FY basis** (price / latest FY EPS, the same basis as the series),
keeping quarter-TTM for the headline P/E. Either way the current mixed
comparison is disclosed, never silent.
