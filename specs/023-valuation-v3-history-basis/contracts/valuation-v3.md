# Calculation Contract: `valuation-v3`

**Feature**: `023-valuation-v3-history-basis` · Accepted 2026-09-05 (owner decision on specs/017)
**Supersedes** `valuation-v2` (specs/012 contracts/valuation-v2.md) for all assessments calculated
after adoption; v2 rows remain readable under their `rule_version` and are never rewritten. Every
section of v1 and v2 applies unchanged **except** Basis A, amended below. The normative
implementation stays in `ValuationV1.java` (class name historical; `RULE_VERSION` is the
authority).

## Why a new version

`valuation-v2` ranks today's metric value — computed on quarter-TTM aggregates where four quarters
are visible — against an own-history series whose points fall back to fiscal-year aggregates
wherever fewer than four quarters were visible at that date. Measured on the VCI universe
(specs/017 R-004): the two bases differ by a median 7.6 % on the same date (p90 31 %) and move the
percentile by up to 58 points. This contract makes both sides of the rank use one basis.

## Metric classes

| Class | Metrics | Own-history basis |
|---|---|---|
| **flow** | `PE`, `PEG`, `EV_EBITDA`, `PS`, `DIVIDEND_YIELD` | `FISCAL_YEAR` |
| **point-in-time** | `PB` | `LATEST_REPORT` |

A metric is *flow* when at least one input is a period aggregate (`EPS_TTM`, `EPS_GROWTH_PERCENT`,
`EBITDA_TTM`, `REVENUE_TTM`, `DIVIDEND_PER_SHARE_TTM` / annual-scoped `DIVIDEND_YIELD`).
`PB`'s inputs (`BVPS`, else `EQUITY_ATTRIBUTABLE_TO_PARENT` / shares) are balance-sheet snapshots.

## Fiscal-year aggregate mode (calculation service, not a summary contract change)

`FundamentalSummaryCalculator` gains an aggregate basis parameter:

- `PREFER_QUARTERS` — the persisted summary's own rule: the four newest quarters when visible,
  else the latest annual report labelled `ANNUAL_BASIS`; growth from eight quarters when visible,
  else annual over prior annual. (Since `fundamental-summary-v3`, specs/025, that window must
  also be eligible — consecutive quarters, not superseded by an annual report. Whatever the
  summary contract says at the time is what this mode means; it is not restated here.)
- `FISCAL_YEAR` — the aggregate metrics (`NET_PROFIT_TTM`, `EPS_TTM`, `REVENUE_TTM`,
  `EBITDA_TTM`, `DIVIDEND_PER_SHARE_TTM`) come from the **latest visible ANNUAL report regardless of
  how many quarters are visible**, labelled `ANNUAL_BASIS`; growth metrics are **annual over prior
  annual** regardless of quarter count. **The provider trailing-EPS fallback is disabled** in this
  mode (it is a TTM figure); without an annual EPS, `EPS_TTM` is `MISSING` / `NO_DATA`. Snapshot
  metrics are unaffected. Visibility (`observed_at` ≤ 23:59:59 VN of the as-of date), the
  newest-first ordering, freshness and every other rule are identical in both modes.

The persisted fundamental summary keeps using `PREFER_QUARTERS`. Its rule version was unchanged by
this feature; it later moved to `fundamental-summary-v3` for an unrelated defect (specs/025, Q-60),
which does not affect anything specified here.

## Basis A — amended definition

For each metric *m* and an assessment at price *P* on date *d*:

- **Series** `S_A(m)`: for each of the last `H = 750` accepted sessions, *m* computed with that
  session's close and the summary of reports visible at that session, calculated in **`FISCAL_YEAR`
  mode**. (Point-in-time metrics are unaffected by the mode by construction.)
- **Comparison value** `C_A(m)`: *m* computed at *P* with the summary of **all currently accepted
  reports in `FISCAL_YEAR` mode**. For point-in-time metrics `C_A(m)` equals the headline value.
- **Headline value** `value(m)`: unchanged from v2 (`PREFER_QUARTERS` summary).
- **Percentile** `percentileRank(C_A(m), S_A(m))` (v1 formula, unchanged) exists iff
  `value(m)` is `DEFINED` **and** `C_A(m)` is `DEFINED` **and** `|S_A(m)| ≥ H_min = 500`.

Weights, bands, the coverage gate, the confidence formula and Basis B are unchanged. "Qualifying"
in the coverage gate keeps its v2 wording — `DEFINED` with a percentile in at least one basis —
and therefore now depends on `C_A(m)` for Basis A.

## Per-metric disclosure (new fields)

| Field | Type | Meaning |
|---|---|---|
| `ownHistoryBasis` | `FISCAL_YEAR` \| `LATEST_REPORT` \| null | Basis of the own-history rank. Present whenever Basis A was evaluated for the metric (series ≥ 500 points and headline `DEFINED`), even if the rank could not be produced. Null when Basis A does not apply to the row. |
| `ownHistoryComparisonValue` | decimal (24,12) \| null | `C_A(m)` — the number actually ranked. Null when not `DEFINED`. For `LATEST_REPORT` metrics equals `value`. |

Invariant: `ownHistoryPercentile != null ⇒ ownHistoryBasis != null ∧ ownHistoryComparisonValue != null`.

## Reason codes — added (both non-blocking)

| Code | Level | Meaning |
|---|---|---|
| `HISTORY_FISCAL_YEAR_BASIS` | assessment | Basis A was used and at least one flow metric carries an own-history percentile; those percentiles rank a fiscal-year-basis value against a fiscal-year-basis series. |
| `HISTORY_COMPARISON_UNAVAILABLE` | assessment | At least one metric had a `DEFINED` headline value and a series of ≥ 500 points but no `DEFINED` fiscal-year comparison value (e.g. TTM EPS > 0 while the latest FY EPS ≤ 0, or no annual EPS at all); that metric carries no own-history percentile. |

`HISTORY_SHARES_OUTSTANDING_HELD_CURRENT` (v1) continues to apply.

## Persistence

`valuation_metric` gains `own_history_basis varchar(32)` (check in `FISCAL_YEAR`, `LATEST_REPORT`)
and `own_history_comparison_value numeric(24,12)`, with the invariant above as a table check.
`rule_version = 'valuation-v3'`. Migration `V019`.

## API (additive, `stock-detail.openapi.yaml` 1.x)

`ValuationResponse.ruleVersion` may now be `valuation-v3`; metric items gain the two nullable
fields. Clients MUST accept both `valuation-v2` and `valuation-v3` rows. The analyst tool
`MetricFactDto` carries the same two fields.

## Required test vectors (in addition to v1's and v2's)

| Test | Inputs | Assertion |
|---|---|---|
| Growth company ranks the FY value | price 62,300; TTM EPS 4,728; FY EPS 4,028; a 600-point FY-basis PE series in which 15.47 and 13.18 fall at different ranks | `PE.value = 13.176818950931`, `PE.ownHistoryComparisonValue = 15.466732869911`, `PE.ownHistoryBasis = FISCAL_YEAR`, percentile = `percentileRank(15.4667…, series)`, **not** of 13.18; `HISTORY_FISCAL_YEAR_BASIS` present |
| Turnaround | TTM EPS 1,000 (DEFINED), FY EPS −500, PB defined with 600 points | `PE` DEFINED, `PE.ownHistoryPercentile = null`, `PE.ownHistoryBasis = FISCAL_YEAR`, `PE.ownHistoryComparisonValue = null`; `HISTORY_COMPARISON_UNAVAILABLE` present; `PB.ownHistoryPercentile` present |
| Annual-only company (regression, DATA-003) | no quarterly EPS; FY EPS present; same series | `C_A(PE) = value(PE)`; percentile identical to v2's for the same inputs |
| Point-in-time metric | any | `PB.ownHistoryBasis = LATEST_REPORT`, `PB.ownHistoryComparisonValue = PB.value` |
| Trailing-EPS exclusion | no quarterly EPS, no annual EPS, provider `TRAILING_EPS` present | headline `PE` DEFINED (`PROVIDER_TRAILING_EPS`), `C_A(PE)` absent, `HISTORY_COMPARISON_UNAVAILABLE` |
| Replay | identical inputs twice | identical decimals, new fields included |
| Rule version | any | every result reports `valuation-v3` |

## Independent verification

`tools/verification/verify_calcs.py` (valuation section) rebuilds `S_A(PE)` and `C_A(PE)` from raw
`fundamental_report` / `equity_daily_bar` rows with its own fiscal-year rule, checks
`own_history_comparison_value = price / FY EPS` exactly and the stored percentile within 0.6 pp.
`tools/verification/history_basis_study.py` remains the measurement tool for the residual FY-vs-TTM
gap; after adoption it reports the gap as informational (the rank no longer mixes bases).

## Known consequences

- **Turnarounds lose Basis A for P/E.** A company whose TTM EPS turned positive while its latest
  fiscal-year EPS is still ≤ 0 has a `DEFINED` headline P/E but no fiscal-year comparison value,
  so P/E carries no own-history percentile (`HISTORY_COMPARISON_UNAVAILABLE`). P/E can still
  qualify through Basis B (sector). Without a sector basis the coverage gate may withhold the
  assessment (`INSUFFICIENT_METRIC_COVERAGE`) — v2 would have published it on a percentile that
  compared a positive TTM P/E with a history of fiscal-year P/Es that never existed for the loss
  years, which is exactly the comparison this contract refuses to make. Withholding is the honest
  outcome; the reason codes say why.

## Known, disclosed limitations

- The fiscal-year denominator can be up to ~9 months (occasionally ~15 months, before an annual
  report is published) older than the headline's TTM denominator. That is inherent to ranking on
  the series' basis and is why the comparison value is exposed rather than implied.
- Basis B (sector cross-section) still compares peers' headline values of mixed basis. Out of
  scope here; see specs/023 research R-009 and docs/REMEDIATION_PLAN.md.
