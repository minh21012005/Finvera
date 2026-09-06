# Research: Feature 025 — TTM window eligibility (Q-60)

Date: 2026-09-06. The defect was found while measuring P2-11 (specs/024 R-005); this file records
its full extent, measured read-only on the post-crawl VCI database (bars through 2026-08-28), plus
the design decisions the numbers settle.

## R-001 Extent: 60 instruments, and it is not only EPS

A quarter window is unsound when the four newest quarterly reports are **not four consecutive
fiscal quarters** (`NON_CONTIGUOUS`) or when an accepted **annual report ends later** than the
newest quarter in the window (`STALE_VS_ANNUAL`).

| Category | Instruments |
|---|---|
| `STALE_VS_ANNUAL` only | 29 |
| `NON_CONTIGUOUS` only | 16 |
| both | 15 |
| **total flagged** | **60** |
| of those, an annual report exists to fall back to | 59 |
| of those, no annual report at all (PLO) | 1 |
| sound windows that must stay untouched | **1,139** |

Worst staleness 2,557 days (KHD, SDY, SD7, CCM: four quarters ending 2018-12-31 against a current
FY2025 annual). Worst spread: WTC's four "newest" quarters run 2018-06-30 … 2026-06-30 — four
quarters drawn from eight years and summed into a "twelve-month" figure.

The earlier P2-11 note said 24 instruments; that was the subset whose **`EPS_TTM`** is a quarter
sum. Counting every aggregate the same window feeds raises it to 60:

| Served metric on a flagged instrument | rows | carrying no basis label |
|---|---|---|
| `EPS_TTM` | 50 | 24 |
| `NET_PROFIT_TTM` | 43 | 43 |
| `REVENUE_TTM` | 43 | 43 |
| `EBITDA_TTM` | 30 | 30 |
| `DIVIDEND_PER_SHARE_TTM` | 29 | 29 |

(The 26 `EPS_TTM` rows that *are* labelled fell through to `PROVIDER_TRAILING_EPS` because those
instruments report no quarterly EPS — their profit and revenue TTMs still come from the bad
window.) Downstream, these figures reach the fundamentals section, the screener's revenue-growth
filters, `EV_EBITDA` / `PS` / dividend yield in valuation, the analyst tool, and the sector
cross-section of 891 other instruments.

## R-002 The growth window has the same blind spot

Period-over-period growth uses the eight newest quarters under the same "count only" test. Of
1,167 instruments with eight quarters, **20** have a non-contiguous eight-quarter window and **23**
one older than their newest annual report. The fix therefore has to apply to both windows or the
defect simply moves.

## R-003 Contiguity is decided on fiscal-quarter indices, not on dates

A day-span test (four consecutive quarter ends span ~273 days) is what the measurement scripts use,
but it is fuzzy at the boundaries and wrong for a company whose fiscal quarters are not calendar
quarters. The calculator already carries `fiscalYear` and `fiscalQuarter` on every `ReportPeriod`,
so eligibility is decided exactly: sort newest first and require the index
`fiscalYear * 4 + fiscalQuarter` to decrease by exactly one at each step. No date arithmetic, no
calendar assumption, and the same rule works for an eight-quarter window.

## R-004 What to do when the window is rejected and no annual exists

Exactly one instrument (PLO) is in this position today. Two options were weighed:

- **Keep the quarters, add a disclosure code.** Rejected. A sum of four quarters drawn from 2022,
  2023, 2024 and 2025 is not a twelve-month figure of anything; labelling it does not make it one,
  and the label would sit on a number the screener and the AI would still consume as a TTM.
- **Report the aggregates `MISSING` with `QUARTER_WINDOW_INELIGIBLE`.** Chosen. It matches the
  constitution's "missing is not zero, and wrong is not missing" posture and the existing
  `INSUFFICIENT_HISTORY` behaviour for fewer than four quarters. PLO loses TTM aggregates it never
  legitimately had; its snapshot metrics (BVPS, equity, ratios) are unaffected.

## R-005 Why a new rule version rather than an amendment

The output changes for 60 instruments, so a consumer cannot assume a `fundamental-summary-v2` row
and a recomputed one mean the same thing: the version has to move to `fundamental-summary-v3`.
Rollout is safe because `FundamentalReportService.findBySymbol` **computes and persists the summary
on every read**, so nothing is left empty between deploy and the owner's refresh; the persisted row
is a cache and an audit trail.

One transitional cost, recorded rather than discovered later: `ValuationService`'s Q-55 bulk path
reads peers' summaries by rule version. Until each peer has been read once (or the refresh's
warmup has run), that lookup misses and the peer falls back to the per-peer computing path — the
8–18 s valuation Q-55 fixed. The owner runs the refresh straight after this change, which
repopulates every summary, so the window is short; it is not a correctness issue.

## R-006 Basis label semantics stay as they are

When the quarterly window is rejected the aggregates carry `ANNUAL_BASIS`, the same label the
"fewer than four quarters" branch already uses. Downstream consumers — `valuation-v3`'s
FISCAL_YEAR mode, the screener, the FE dictionary, the analyst attribution — all key on that label
already, and giving the same fact two names would fracture them. *Why* the window was rejected is
carried separately as the summary-level reason `QUARTER_WINDOW_INELIGIBLE`, which is new and
additive.
