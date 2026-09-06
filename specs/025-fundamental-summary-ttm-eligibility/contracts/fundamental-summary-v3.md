# Calculation Contract: `fundamental-summary-v3`

**Feature**: `025-fundamental-summary-ttm-eligibility` · Accepted 2026-09-06
**Supersedes** `fundamental-summary-v2` for all summaries calculated after adoption; v2 rows remain
readable under their `rule_version` and are never rewritten. Every rule of v2 applies unchanged
**except** the window-selection rules amended below. The normative implementation stays in
`FundamentalSummaryCalculator`.

## Why a new version

v2 selects the TTM window by counting: *"if four quarterly reports exist, use the four newest"*. It
never checks that the four are consecutive, nor that a fresher annual report exists. Measured on
the live universe (specs/025 research R-001): 60 LISTED instruments carry a window that is
non-contiguous, years older than their own current annual report, or both — up to 2,557 days stale,
one spanning eight years. The figures are mislabelled as well as wrong: `basisPeriodLabel` names the
newest report overall (the annual), so nothing contradicts the number underneath it.

## Window eligibility (new)

Let the **quarter window** be the *n* newest `QUARTER` reports in the v2 newest-first order
(*n* = 4 for the TTM aggregates, *n* = 8 for period-over-period growth). Define the quarter index of
a report as `fiscalYear * 4 + fiscalQuarter`.

The window is **eligible** only when both hold:

| Rule | Definition | Rejected as |
|---|---|---|
| **E-1 Contiguity** | The *n* quarter indices, newest first, decrease by exactly 1 at every step. | `NON_CONTIGUOUS` |
| **E-2 Not superseded** | No accepted `ANNUAL` report has a `periodEnd` later than the newest quarter's `periodEnd`. | `STALE_VS_ANNUAL` |

E-1 is decided on fiscal-quarter indices, never on day spans: a company's fiscal quarters need not
be calendar quarters (research R-003).

## Consequences of rejection

| Situation | Result |
|---|---|
| 4-quarter window rejected, an annual report exists | Aggregates come from the **latest annual report**, labelled `ANNUAL_BASIS` — the same branch v2 already uses for "fewer than four quarters". |
| 4-quarter window rejected, **no** annual report exists | `NET_PROFIT_TTM`, `EPS_TTM`, `REVENUE_TTM`, `EBITDA_TTM`, `DIVIDEND_PER_SHARE_TTM` are `MISSING` with quality reason `QUARTER_WINDOW_INELIGIBLE`. The provider trailing-EPS fallback still applies to `EPS_TTM` where the newest report carries `TRAILING_EPS` (v2 rule, unchanged). |
| 8-quarter growth window rejected | Growth falls back to **annual over prior annual** with `ANNUAL_BASIS` when two annual reports exist (the v2 branch), else `MISSING` / `INSUFFICIENT_HISTORY`. |
| Window eligible | Byte-identical to v2. |

Snapshot metrics (`BVPS`, `EQUITY_ATTRIBUTABLE_TO_PARENT`, `TOTAL_DEBT`, `CASH_AND_EQUIVALENTS`,
provider ratios) are read from the newest visible report and are **not** affected by window
eligibility — they never aggregated periods in the first place.

## Reason codes

| Code | Level | Meaning | Blocking |
|---|---|---|---|
| `QUARTER_WINDOW_INELIGIBLE` | summary (`SummaryResult.reasonCodes`), and metric-level where an aggregate is `MISSING` for this cause | The quarterly window failed E-1 or E-2; the figures come from the annual report instead, or are withheld when there is none. | No |

`ANNUAL_BASIS` keeps its v2 meaning — "this aggregate came from an annual report" — and is the
label on the values themselves. The new code says *why* the quarterly path was not taken; the two
are complementary and both are emitted (research R-006).

## Aggregate basis parameter

`AggregateBasis.FISCAL_YEAR` (contract `valuation-v3`) is unchanged and unaffected: it already
ignores quarters entirely. `AggregateBasis.PREFER_QUARTERS` is where E-1 and E-2 apply.

## Required test vectors (in addition to v2's)

| Test | Inputs | Assertion |
|---|---|---|
| Eligible window unchanged (DATA-001) | four consecutive quarters through 2026-Q2 (1,084 / 1,224 / 1,051 / 1,369), FY2025 annual EPS 4,028 | `EPS_TTM = 4,728`, `qualityReason = null`, no `QUARTER_WINDOW_INELIGIBLE` — identical to v2 |
| Stale window with a fresher annual (E-2) | four consecutive quarters ending 2018-12-31, annual FY2025 EPS 1,418 | `EPS_TTM = 1,418`, `ANNUAL_BASIS`, summary carries `QUARTER_WINDOW_INELIGIBLE` |
| Non-contiguous window (E-1) | quarters 2026-Q2, 2026-Q1, 2025-Q3, 2025-Q2 (2025-Q4 missing), annual FY2025 | annual used, `ANNUAL_BASIS`, `QUARTER_WINDOW_INELIGIBLE` |
| Ineligible with no annual (FR-004) | four quarters 2022-Q1, 2023-Q1, 2024-Q1, 2025-Q1, no annual report | `EPS_TTM`, `REVENUE_TTM`, `NET_PROFIT_TTM` `MISSING` with `QUARTER_WINDOW_INELIGIBLE` |
| Trailing-EPS fallback still applies | ineligible window, no annual, newest report carries `TRAILING_EPS` | `EPS_TTM` DEFINED with `PROVIDER_TRAILING_EPS`; `REVENUE_TTM` still `MISSING` |
| Growth window (FR-006) | eight quarters with a gap, two annual reports | growth is annual-over-annual with `ANNUAL_BASIS` |
| Snapshot metrics untouched | any ineligible window | `BVPS` and the provider ratios keep their v2 values and labels |
| Rule version | any | every result reports `fundamental-summary-v3` |

## Independent verification

`tools/verification/verify_calcs.py` implements E-1 and E-2 in its own `summary_v2` recomputation
(renamed for the version it targets) and compares against the stored rows;
`tools/verification/sector_basis_study.py` keeps its `[Q-60 …]` marker so a regression is visible in
the sector study too.
