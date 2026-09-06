# Plan: Feature 025 — TTM window eligibility (`fundamental-summary-v3`)

## Approach

One guard, applied where the window is chosen. `FundamentalSummaryCalculator.calculate` already
branches "four quarters, else annual"; the change adds an eligibility test to the condition and a
third outcome (withhold) for the case where neither path is sound. No formula, weight, floor or
snapshot rule moves. Eligibility is decided from the reports already in hand — fiscal-quarter
indices for contiguity, the newest annual `periodEnd` for supersession — so no extra query.

## Components

| Layer | Change |
|---|---|
| `stock/domain/fundamentals/FundamentalSummaryCalculator.java` | `RULE_VERSION = fundamental-summary-v3`; `quarterWindowEligible(window, annualReports)` implementing E-1/E-2; TTM branch consults it; growth branch consults it for the 8-quarter window; `QUARTER_WINDOW_INELIGIBLE` on the summary and on withheld aggregates. |
| `tools/verification/verify_calcs.py` | independent E-1/E-2 in the recomputation; target `fundamental-summary-v3`. |
| `tools/verification/sector_basis_study.py` | read v3 summaries; keep the `[Q-60 …]` marker as a regression signal. |
| `finvera-fe/src/shared/format/reason-codes.ts` (+ its inventory test) | wording for `QUARTER_WINDOW_INELIGIBLE`. |
| Tests | `FundamentalSummaryTests`: the eight contract vectors. Existing v2 vectors must pass unchanged (DATA-001). |
| Docs | Q-60 closed in `docs/REMEDIATION_PLAN.md` with post-refresh numbers; changelog. |

## What is deliberately not touched

- The `FISCAL_YEAR` aggregate basis (`valuation-v3`) — it never reads quarters.
- Snapshot metrics and provider ratios — they never aggregated periods.
- `valuation-v3`, the screener, the AI: they consume `ANNUAL_BASIS` and `MISSING` already, so the
  corrected values flow through their existing paths without a change.

## Constitution check

- **I. Deterministic finance core** — eligibility is a pure function of the reports; same inputs,
  same window, same decimals. A v2-parity vector guards the 1,139 sound windows.
- **II. Evidence, provenance, temporal truth** — the point of the change: a twelve-month figure may
  only be built from twelve contiguous months, and never in preference to a fresher annual report.
  Rejection is disclosed (`QUARTER_WINDOW_INELIGIBLE`), not silent. Missing where wrong is the
  alternative.
- **III. Boundaries** — a pure domain calculator changes; no controller, DTO or transport shape
  moves.
- **V. Spec before code** — spec, research and contract precede implementation.
- **VI. Risk-based testing** — one unit vector per contract row plus the parity vector; the
  independent verifier recomputes the rule from raw rows.
- **VIII. Modular simplicity** — one private predicate, no new class, no new dependency, no
  migration (the rule version is a column value, not a schema change).
- Complexity tracking: none.

## Risks

- **Transitional slowness, not correctness.** Peers' summaries are looked up by rule version; until
  each has been recomputed (on read, or by the refresh's warmup) the sector basis falls back to the
  per-peer computing path (research R-005). The owner refreshes immediately after, so the window is
  short.
- **60 instruments change their served figures** — that is the fix, and SDY's sign flip
  (−1,680 → +1,418) is the reason it matters. Recorded post-refresh under SC-3.

## Rollout

1. Ship the calculator + tests + verifier; suites green.
2. Owner: `.\refresh-data.ps1` — recomputes every summary under v3 and every assessment under
   `valuation-v3` in the same pass (both were waiting on the same refresh).
3. Post-refresh: re-run the scope probe (zero flagged windows expected, 1,139 sound windows
   untouched), `verify_calcs.py` summary section green, close Q-60 with the numbers.
