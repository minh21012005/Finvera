# Research: Sector-basis consistency (P2-11)

Date: 2026-09-06. Method: `tools/verification/sector_basis_study.py`, read-only against the
post-crawl VCI database (bars through 2026-08-28). For every sector with ≥ 9 current LISTED members
(so each subject has ≥ 8 peers, the contract's `N_min`) it computes each member's P/E percentile
inside its own sector twice — once exactly as served today, once with the whole cross-section on
one fiscal-year ruler — and reports the difference.

Fidelity note: the "as served" side reads `EPS_TTM` from the persisted `fundamental_summary`, which
is the number `ValuationService`'s Q-55 bulk path actually feeds into Basis B; it is not
re-derived. The fiscal-year side is price / newest current ANNUAL EPS, the same definition
`valuation-v3` uses for Basis A.

## R-001 Composition and magnitude

26 sectors qualify; 1,190 subjects compared. Five largest:

| sector | members | quarter-TTM | annual | pool mixed/FY | compared | median \|shift\| | p90 | max | >10 pp | >20 pp |
|---|---|---|---|---|---|---|---|---|---|---|
| 2350 Xây dựng và Vật liệu | 301 | 159 | 142 | 229/238 | 224 | 4.04 pp | 18.41 | 68.03 | 55 | 18 |
| 8630 Bất động sản | 124 | 79 | 45 | 104/107 | 101 | 5.20 pp | 29.37 | 67.16 | 33 | 16 |
| 2770 Vận tải | 110 | 61 | 49 | 99/100 | 99 | 4.83 pp | 21.15 | 61.38 | 32 | 10 |
| 3570 Sản xuất thực phẩm | 100 | 56 | 44 | 83/81 | 79 | 6.71 pp | 27.71 | 52.99 | 23 | 8 |
| 7570 Nước & Khí đốt | 88 | 37 | 51 | 79/78 | 78 | 7.73 pp | 23.84 | 89.69 | 28 | 10 |

Across all 26: **750 / 1,470 members (51.0 %) priced on quarter-TTM**, the rest on an annual
figure (`ANNUAL_BASIS` or `PROVIDER_TRAILING_EPS`). |percentile shift| **median 6.56 pp, p90 27.25,
max 100**; 425 subjects (35.7 %) move more than 10 pp, 184 more than 20 pp. A uniform fiscal-year
rule would drop only **8** constituents from the pools for want of an annual EPS — so option (b) is
technically feasible; the question is whether it is right.

Composite-score effect: Basis B carries weight 0.40 and P/E roughly half of the weight inside it,
so a percentile shift of *x* pp moves the 0-100 score by ≈ 0.2 *x* — **≈ 0.8 point at the median,
2.3 at p90, 6.3 at the maximum**. Enough to cross a band boundary (35.5 / 64.5) in the tail, not in
the typical case.

## R-002 The mix disperses; it does not tilt

The decisive question for (a) vs (b): does the mixed ruler systematically favour one group?

| | mixed rule (served today) | uniform fiscal-year rule |
|---|---|---|
| quarter-TTM members (n = 635), mean percentile | 49.9 | 49.5 |
| annual-ruler members (n = 555), mean percentile | 49.3 | 48.1 |

Both groups land in the same place under both rules. The per-company ruler gap is real but has no
consistent direction: for members where both rulers are computable, `PE_fy / PE_headline` has
median ×1.080 with an interquartile range of **0.925 – 1.337** (quarter-TTM members) and ×1.065,
IQR 0.852 – 1.445 (provider-trailing members). Some companies' fiscal-year multiple is 7 % lower,
others 34 % higher.

This is the opposite of what Feature 023 found for Basis A, where the bias was directional: a
growing company's TTM P/E is systematically below its own fiscal-year history, so it looked cheap
against itself for a reason that had nothing to do with price. Here the same ±8 % gap exists, but
it points in both directions and cancels at the group level. **The defect is dispersion, not
bias.**

*(An earlier decomposition in this study — holding the subject on one ruler while switching the
pool to the other — was discarded as unsound: it compares a subject and a pool measured
differently, so its −4.1 pp mean only reflects the fiscal-year pool sitting higher overall, not any
relative distortion. The tool still prints it, labelled; only R-002's group comparison and R-001's
coherent shift answer the question.)*

## R-003 Uniform fiscal-year would be worse where it matters most

The ten largest individual moves are all companies whose recent earnings differ sharply from their
last full fiscal year, and in most of them the fiscal-year number is the less informative one:

| symbol | headline P/E (ruler) | fiscal-year P/E | percentile mixed → FY |
|---|---|---|---|
| XPH | 1.71 (provider trailing) | 4,833.33 | 0.00 → 100.00 |
| DHB | 3.65 (provider trailing) | 276.92 | 6.56 → 98.36 |
| HHS | 107.87 (provider trailing) | 1.26 | 91.67 → 0.00 |
| PPY | 3.71 (quarter-TTM) | 50.00 | 6.41 → 96.10 |
| ASP | 3.92 (quarter-TTM) | 36.90 | 7.69 → 92.21 |

XPH was checked to the raw rows: its FY2025 report is coherent (NET_PROFIT 36,812,591 over
12,972,475 shares → EPS 3, `TRAILING_EPS` 2.84), and the 8,472 trailing EPS that produces the 1.71
headline comes from the provider's newest quarterly report (2026-Q2, observed 2026-08-14) — a real
one-off in a micro-cap, not a Finvera defect. Ranking XPH at percentile 100 (the most expensive
name in its sector) on a denominator of 3 VND per share would be a worse answer than the 1.71 the
fresh figure gives. A uniform fiscal-year rule buys internal consistency by throwing away the
fresher and more informative number for the 51 % who have one.

## R-004 Recommendation: option (a)

Keep the freshest ruler per constituent; disclose the composition of the cross-section (how many
constituents on each ruler) so the percentile can be read for what it is. Do **not** put Basis B on
the fiscal-year basis.

Consequence to state plainly, not hide: after Feature 023 a stock detail page carries two
percentiles measured differently — own-history on the fiscal-year ruler (both sides consistent) and
sector on the freshest-available ruler (both sides "now", mixed across companies). Each is
internally coherent for the question it answers; the disclosure has to name which is which.

## R-005 Defect found while measuring — Q-60 (recorded, not fixed here)

`FundamentalSummaryCalculator` takes the four newest QUARTER reports whenever four exist, without
checking that they are contiguous or that they are newer than the available annual report:

```java
if (quarterReports.size() >= 4) { currentTtmPeriods = quarterReports.subList(0, 4); }
else if (!annualReports.isEmpty()) { /* annual, ANNUAL_BASIS */ }
```

Measured on the live database: of 779 instruments whose served `EPS_TTM` is a quarter sum,
**771 are sound**, **8 sum non-contiguous quarters**, and **19 sum quarters older than that
instrument's own newest annual report** — 24 distinct LISTED instruments in total (some flagged
both ways). That count is **EPS-only**; scoping the fix showed the same window also feeds revenue,
profit, EBITDA and dividend aggregates, taking the true population to **60** (specs/025 R-001). Staleness reaches **2,557 days**: KHD, SDY and SD7 are served a "TTM" built from 2018
quarters while their FY2025 annual reports sit current in the same table.

| symbol | quarters summed | newest annual | served EPS_TTM | FY2025 EPS |
|---|---|---|---|---|
| KHD | 2018-03-31 … 2018-12-31 | 2025-12-31 | 5,007 | 4,406 |
| SDY | 2018-03-31 … 2018-12-31 | 2025-12-31 | **−1,680** | **+1,418** |
| SD7 | 2018-03-31 … 2018-12-31 | 2025-12-31 | 85 | 72 |
| APP | 2022-03-31 … 2023-06-30 (non-contiguous) | 2025-12-31 | 130 | 760 |

SDY is the sharpest: the served figure is a loss, so P/E and PEG are withheld as
`NEGATIVE_OR_ZERO_EPS`, while the company's latest annual report shows a profit. Nothing warns the
reader — `basis_period_label` says `2025` (it is taken from the newest report overall, which *is*
the 2025 annual) and `data_status` says `DELAYED`, so the label contradicts the number underneath
it.

Blast radius: none of the 24 currently has a **published** valuation classification, so no
label is shown for them; but their `EPS_TTM` is displayed in the fundamentals section, handed to
the analyst tool, and enters the sector cross-section of **891 other instruments** that share a
sector with at least one of them.

Suggested rule (**implemented 2026-09-06** as contract `fundamental-summary-v3`, specs/025): the four quarters
qualify only when they are contiguous **and** their newest period end is not older than the newest
annual report's; otherwise fall back to the annual figure and label it `ANNUAL_BASIS`, exactly as
the "fewer than four quarters" branch already does. Cost: 60 instruments change basis; the 1,139
sound windows are untouched.
