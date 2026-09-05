# Research: Feature 023 — Own-History Percentile on One Basis

Date: 2026-09-05. Inputs: specs/017 research (R-001…R-005, the measurement that motivates this
feature), the `valuation-v1`/`v2` contracts, `ValuationV1`, `ValuationService`,
`FundamentalSummaryCalculator`, the stock-detail OpenAPI contract, the FE parser, the analyst
tool DTOs and `tools/verification/verify_calcs.py`. Every decision below cites what was read or
measured; nothing about provider behavior is assumed.

## R-001 The measurement (from specs/017, VCI universe)

Median annual-basis share of the 750-point series 73 % (65–83 % where quarters exist, 100 %
otherwise); |PE_annual / PE_quarter − 1| median 7.6 %, p90 31.1 %, max 73.4 %; today's percentile
moves up to |58.3| pp (VCB) depending on basis. 468 / 1,522 instruments (30.7 %) have no quarterly
EPS at all and are already consistent (annual vs annual); 1,054 (69.3 %) carry the bias. Owner
decision 2026-09-05: option (b).

## R-002 Which metrics are "flow" and which are "point-in-time"

Read from `ValuationV1.computeMetrics` inputs and `FundamentalSummaryCalculator`:

| Metric | Inputs | Class | Why |
|---|---|---|---|
| PE | price, `EPS_TTM` | **flow** | EPS_TTM is a four-quarter sum or the latest FY figure |
| PEG | PE, `EPS_GROWTH_PERCENT` | **flow** | both inputs are period aggregates |
| EV_EBITDA | price×shares, `TOTAL_DEBT`, `CASH_AND_EQUIVALENTS`, `EBITDA_TTM` | **flow** | EBITDA_TTM is a period aggregate (debt/cash are snapshots but the ratio's denominator is flow) |
| PS | price×shares, `REVENUE_TTM` | **flow** | period aggregate |
| DIVIDEND_YIELD | `DIVIDEND_PER_SHARE_TTM` / price, or the annual-scoped `DIVIDEND_YIELD` | **flow** | period aggregate |
| PB | price, `BVPS` (else equity / shares) | **point-in-time** | balance-sheet snapshot of the newest visible report, no aggregation |

Only flow metrics change basis. PB keeps v2 semantics under the explicit label `LATEST_REPORT`.

## R-003 What "fiscal-year basis" means, precisely, in the existing calculator

`FundamentalSummaryCalculator.calculate` already contains the annual path: when fewer than four
quarters are visible it takes the TTM aggregates from the latest ANNUAL report and labels them
`ANNUAL_BASIS`; growth falls back to annual-over-prior-annual when fewer than eight quarters are
visible. The fiscal-year mode needed here is that same path **forced on regardless of quarter
count**, for the aggregate metrics only (`NET_PROFIT_TTM`, `EPS_TTM`, `REVENUE_TTM`, `EBITDA_TTM`,
`DIVIDEND_PER_SHARE_TTM`, `EPS_GROWTH_PERCENT`, `REVENUE_GROWTH_PERCENT`). Snapshot metrics
(`BVPS`, `EQUITY_ATTRIBUTABLE_TO_PARENT`, `TOTAL_DEBT`, `CASH_AND_EQUIVALENTS`, ratios) are read
from the newest visible report as today — they carry no basis.

Consequence for the persisted summary: none. `fundamental-summary-v2`'s output and rule version
are untouched; the mode is a calculation service consumed only by `valuation-v3`, and it is
specified there.

## R-004 The provider trailing-EPS fallback must stay out of the fiscal-year basis

`addEpsTtmMetric` falls back to the provider's `TRAILING_EPS` (labelled `PROVIDER_TRAILING_EPS`)
when neither four quarters nor an annual EPS is available. That figure is a TTM number; letting it
into a fiscal-year series would re-create the mixed basis this feature removes. Checked against
the data: every instrument sampled in specs/017 that lacks quarterly EPS (MBB, SSI, PVS, HPG) has
**8 / 8 annual reports with EPS**, so excluding the fallback in fiscal-year mode costs nothing
measurable. Rule: in fiscal-year mode a point without annual EPS contributes no PE/PEG point, and
a company without annual EPS today gets no fiscal-year comparison value (disclosed, FR-003).

## R-005 The frontend refuses unknown rule versions — a coordinated change, not an optional one

`finvera-fe/src/features/stock-detail/api/stock-detail.ts:440` throws
`Unsupported valuation ruleVersion` for anything but `valuation-v2`, and the type at line 174 is
the literal `"valuation-v2"`. Shipping the backend alone would blank the valuation section for
every stock. The FE change (accept `valuation-v3`, keep accepting `valuation-v2`) ships in the same
change set; both live in this monorepo.

## R-006 Rollout mechanics: a rule-version bump is a full recompute

`ValuationWarmupService` looks up existing assessments by `ValuationV1.RULE_VERSION`; after the
bump no instrument has a v3 row, so stage 7 of the owner's `refresh-data.ps1` recomputes all 1,522
(exactly how `valuation-v2` rolled out, specs/012 plan). `valuation-v2` rows stay current under
their own version and remain readable; nothing is deleted or rewritten. No literal
`'valuation-v2'` exists in production SQL or Java outside `ValuationV1.RULE_VERSION`; the FE
literal (R-005) and test fixtures are the only other occurrences.

## R-007 What the percentile compares after this change

Both sides of the rank use the same definition at every date: *price on that date / the latest
fiscal-year figure visible on that date*. Today's comparison value is therefore not the headline
P/E; it can be up to ~9 months staler in its denominator (FY2025 EPS while trading in Sep 2026),
and it must be shown, not hidden, which is why the metric row exposes `ownHistoryComparisonValue`
and `ownHistoryBasis` (FR-004) and the assessment carries `HISTORY_FISCAL_YEAR_BASIS` (FR-005).

Consistency check on the universe split (R-001): for the 468 annual-only instruments the
fiscal-year comparison value equals the headline (both annual), so v3 reproduces v2's percentile
exactly — a regression vector in the contract (DATA-003).

## R-008 Quarterly depth grows on its own; this is a step, not a terminus

The provider serves eight quarters per crawl and Finvera supersedes rather than deletes, so each
crawl's new quarters are added to the retained ones. When quarterly EPS covers the whole 750-bar
window for an instrument, a future rule could rank quarter-TTM against quarter-TTM. That is
explicitly **not** this feature; it is noted so nobody reads `FISCAL_YEAR` as a permanent design
choice rather than the best consistent basis available today.

## R-009 Sector basis carries the same class of issue — out of scope, recorded

Basis B compares peers' headline values, which are TTM for some companies and annual for others in
the same cross-section. The remedy would be per-peer fiscal-year inputs read from
`fundamental_report` in bulk (the Q-55 bulk path already exists for summaries). It needs its own
measurement (how far do peer rankings move?) before any code — the same discipline as specs/017.
Recorded as a follow-up in docs/REMEDIATION_PLAN.md rather than folded in silently.

## R-010 Design consequence surfaced by the test vectors: turnarounds

Writing the contract's turnaround vector made a consequence explicit: a company whose TTM EPS is
positive while the latest fiscal-year EPS is still ≤ 0 keeps a `DEFINED` headline P/E but gets **no
own-history percentile for P/E** (no fiscal-year comparison value exists). If the sector basis
qualifies P/E, the assessment publishes; without it, the coverage gate can withhold with
`INSUFFICIENT_METRIC_COVERAGE`. `valuation-v2` would have published such a company on a percentile
that ranked a positive TTM P/E inside a history whose loss-year points had no P/E at all — the very
mixed comparison this feature removes. Recorded in the contract under "Known consequences"; the
FE already renders both reason codes with wording.
