# Research: Valuation Data Completeness

**Feature**: `010-valuation-data-completeness` · 2026-08-30

## R-001 — Measured state of the private database (evidence)

```
valuation_assessment (current):  published=false 3,040 · published=true 7 (+3/+2 edge)
reason codes:  INSUFFICIENT_METRIC_COVERAGE 3,043 · NO_COMPARISON_BASIS 3,040 ·
               CORE_METRIC_UNAVAILABLE 1,775 · HISTORY_BASIS_INSUFFICIENT 1,192
fundamental_summary_metric: EPS_GROWTH_PERCENT MISSING 1,543/1,543 · REVENUE_GROWTH_PERCENT MISSING 1,543/1,543
                            EPS_TTM DEFINED 951 · MISSING NO_DATA 483 · MISSING INSUFFICIENT_HISTORY 109
                            BVPS DEFINED 198 · EQUITY_ATTRIBUTABLE_TO_PARENT MISSING 1,543
equity_profile (current):   shares_outstanding IS NULL for 1,524 / 1,524
fundamental_report (current): QUARTER only, avg 3.82 periods per instrument (max 4); ANNUAL 0
```

## R-002 — Provider period limit is 4, not 8

`Finance(kbs).<statement>(period=…)` returns **4** period columns for every
dataset (quarter and year). The public methods do not forward `limit`; calling
`_fetch_series_data(limit=8)` directly still returns 4. The Community banner
("tối đa 8 kỳ") overstates what the API delivers. Ratio quarter columns are
`['2026-Q2','2025-Q4','2026-Q1','2025-Q4_1']` — the `_1` column duplicates
`2026-Q2` values (KBS page bug noted in vnstock's own source), so the ratio
dataset effectively has **3** distinct quarters and no 2025-Q3.

**Consequences**: the 8-quarter growth rule (`fundamental-summary-v1`) can
never be satisfied on this tier; a first-run database has ≤ 4 quarters, so the
own-history basis (500 sessions) has at most ~1 year of visible reports.
History accumulates across refreshes (older periods stay `current`), but only
from the date Finvera started importing.

## R-003 — Outstanding shares are available (decision: map)

`Company(symbol, source="kbs").overview()` returns `outstanding_shares`
(VNM 2,089,955,445; MBB 8,054,999,909), `listed_volume`, `charter_capital`,
`free_float_percentage`, `free_float`. The equity-profile exporter only used
`Listing.symbols_by_exchange()` and recorded `SHARES_OUTSTANDING_UNAVAILABLE`.
**Correction (Feature 011 R-002)**: `free_float_percentage`/`free_float` are
mis-mapped by vnstock (shares × par, and par value); no free float is emitted.
Decision: one overview call per symbol in `export_equity_profile.py`; the
importer creates a new effective-dated profile revision when the current row
has no shares or a different value (closing the old row with `effective_to`).

## R-004 — Bank EPS item id (decision: map both)

Non-financial income statements use `earnings_per_share_vnd`; banks use
`earning_per_share_vnd` (MBB). Both map to `EPS` with the same ÷1000
per-share normalisation (Feature 002 T074).

## R-005 — `fundamental-summary-v2` (rule version change)

Constitution I: a formula change is a new rule version with parallel results.

| Metric | v1 | v2 |
|---|---|---|
| `*_TTM` | sum of 4 latest quarters; annual only when **no** quarters | 4 quarters if visible; **else latest annual report** (never a partial quarter set), reason `ANNUAL_BASIS` |
| `EPS_GROWTH_PERCENT`, `REVENUE_GROWTH_PERCENT` | TTM vs prior TTM (needs 8 quarters) | 8 quarters if visible; **else latest annual vs prior annual** (`(cur/prior − 1)×100`, `NOT_APPLICABLE` when prior ≤ 0), reason `ANNUAL_BASIS` |
| everything else | unchanged | unchanged |

**Amendment 2026-08-31 (independent recomputation)**: when fewer than four
quarterly `EPS` values exist but the newest report carries the provider's
`TRAILING_EPS`, `EPS_TTM` is that figure and is labelled `PROVIDER_TRAILING_EPS`
(banks and securities firms report no quarterly EPS). It is never mixed with a
quarterly sum. This path pre-dated v2 but was undisclosed.

`RULE_VERSION` constant becomes `fundamental-summary-v2`; existing v1 rows
remain for reproducibility; warmup recomputes. `valuation-v1` is untouched —
it simply receives `DEFINED` inputs more often, and its own-history basis sees
annual reports as visible periods for historical sessions.

**Rejected**: mixing 2–3 quarters with an annual remainder (non-standard,
unexplainable); estimating shares from `NET_PROFIT / EPS` (EPS uses weighted
average shares; a derived count would silently disagree with the provider's).

## Constitution check

I ✔ versioned rule; II ✔ basis disclosed per metric; III ✔; VI ✔ v2 tests with
fixed fixtures; VIII ✔ minimal: two data mappings + one fallback rule.
