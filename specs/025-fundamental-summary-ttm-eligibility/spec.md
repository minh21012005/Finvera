# Feature 025: TTM Window Eligibility (`fundamental-summary-v3`)

**Status**: Specified 2026-09-06
**Closes**: docs/REMEDIATION_PLAN.md **Q-60**
**Contracts**: `fundamental-summary-v3` (this feature), supersedes `fundamental-summary-v2`
**SRS References**: SRS-FUN-01 (fundamental facts), SRS-VAL-01 · **Found by**: specs/024 research R-005

## Problem

`FundamentalSummaryCalculator` builds the trailing-twelve-month aggregates from "the four newest
quarterly reports" whenever four exist:

```java
if (quarterReports.size() >= 4) { currentTtmPeriods = quarterReports.subList(0, 4); }
else if (!annualReports.isEmpty()) { /* latest annual, labelled ANNUAL_BASIS */ }
```

It never checks that those four quarters are **consecutive**, nor that they are **newer than the
annual report sitting in the same table**. For a company that stopped filing quarterly — common
among small caps on UPCoM — the four newest quarters can be years old, and the calculator prefers
them over a current annual report.

Measured on the live database (specs/025 research R-001): **60 LISTED instruments** are affected.
KHD, SDY, SD7 and CCM are served a "TTM" summed from **2018** quarters while their FY2025 annual
reports are current in the same table — 2,557 days stale. WTC's four "newest" quarters span
2018-06-30 to 2026-06-30: four quarters drawn from eight years, added together and presented as a
twelve-month figure.

The result is not merely stale, it is **mislabelled**: `basis_period_label` reports `2025` and
`data_status` reports `DELAYED`, both taken from the newest report overall (the annual), so nothing
contradicts the number underneath. SDY is served `EPS_TTM = −1,680`, so P/E and PEG are withheld as
`NEGATIVE_OR_ZERO_EPS`, while its FY2025 report shows `+1,418`: the user is told a profitable
company is lossmaking, on 2018 data, with no warning.

## Scope

In scope: which reports may form the TTM and growth windows in `FundamentalSummaryCalculator`, the
disclosure when a window is rejected, the contract version, and the tools that verify it.

Out of scope, deliberately: the formulas themselves (a sum of four contiguous quarters stays a sum
of four contiguous quarters), the snapshot metrics, `valuation-v3`'s `FISCAL_YEAR` mode (it already
ignores quarters), and the sector-basis question answered in specs/024.

## Requirements

- **FR-001** The four newest quarterly reports MUST form the TTM window only when they are four
  **consecutive** fiscal quarters.
- **FR-002** The TTM window MUST additionally be rejected when an accepted annual report covers a
  period ending **after** the newest quarter in the window — a fresher annual figure is never
  passed over for staler quarters.
- **FR-003** When the quarterly window is rejected and an annual report exists, the aggregates MUST
  come from the latest annual report and be labelled `ANNUAL_BASIS`, exactly as the existing
  "fewer than four quarters" branch does.
- **FR-004** When the quarterly window is rejected and no annual report exists, the affected
  aggregates MUST be `MISSING` with reason `QUARTER_WINDOW_INELIGIBLE`. A window of four quarters
  scattered across several years is not a twelve-month figure, and missing is honest where wrong
  is not.
- **FR-005** A summary whose quarterly window was rejected MUST carry the summary-level reason code
  `QUARTER_WINDOW_INELIGIBLE`, so the cause is visible and not merely implied by `ANNUAL_BASIS`.
- **FR-006** The same two eligibility rules MUST apply to the eight-quarter window used for
  period-over-period growth; when it is rejected, growth falls back to annual-over-prior-annual
  (the existing branch).
- **DATA-001** Instruments whose four newest quarters are already consecutive and not older than
  their newest annual report MUST produce byte-identical output to `fundamental-summary-v2`.
- **DATA-002** The rule version becomes `fundamental-summary-v3`; v2 rows remain readable and are
  never rewritten.
- **NFR-001** Eligibility MUST be decided from the reports already loaded — no additional query per
  instrument.

## Success criteria

- **SC-1** Unit vectors for every contract row: contiguous+fresh (unchanged), non-contiguous with
  annual, stale-vs-annual with annual, ineligible without annual, eight-quarter growth window,
  and a v2-parity vector.
- **SC-2** Backend suite green; the AI service suite green.
- **SC-3** After the owner's refresh: the 60 flagged instruments are re-measured — none is left
  with a quarter window that is non-contiguous or older than its own newest annual report, and the
  1,139 sound windows are untouched.
- **SC-4** `verify_calcs.py` recomputes the eligibility rule independently and its `summary`
  section stays green.
- **SC-5** Q-60 closed in docs/REMEDIATION_PLAN.md with the post-refresh numbers.

## Acceptance scenarios

1. **Given** VNM (four consecutive quarters through 2026-Q2, newest annual FY2025), **when** the
   summary is computed, **then** `EPS_TTM` is the four-quarter sum 4,728 with no basis label —
   identical to v2.
2. **Given** SDY (four quarters ending 2018-12-31, annual FY2025 current), **when** the summary is
   computed, **then** the aggregates come from FY2025 labelled `ANNUAL_BASIS`, the summary carries
   `QUARTER_WINDOW_INELIGIBLE`, and `EPS_TTM` is `+1,418` rather than `−1,680`.
3. **Given** WTC (four quarters spanning 2018-06-30 … 2026-06-30 with FY2025 current), **when** the
   summary is computed, **then** the window is rejected as non-contiguous and FY2025 is used.
4. **Given** PLO (four non-contiguous quarters, no annual report at all), **when** the summary is
   computed, **then** the TTM aggregates are `MISSING` with `QUARTER_WINDOW_INELIGIBLE` — never a
   sum of quarters from different years.
