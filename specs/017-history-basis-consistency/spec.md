# Feature Specification: Own-history basis consistency (research)

**Feature Directory**: `017-history-basis-consistency`
**Created**: 2026-08-31 · **Status**: Research done — decision deferred (blocked on Q-57 re-export)
**SRS References**: Section 10 (valuation) · **SRS Requirement IDs**: SRS-VAL-01
**Input**: docs/REMEDIATION_PLAN.md Phase 2 item **P2-05**; `valuation-v2` own-history
percentiles compare today's quarter-TTM P/E with a history whose points fall back to
the annual basis when fewer than four quarters are visible (fundamental-summary-v2).

## Scope Summary *(mandatory)*

Measure — not change — how the own-history series is composed and how far the
annual-basis points sit from what the quarterly basis would give, then decide
between (a) labelling history points with their basis and excluding mixed
comparisons (`valuation-v3`) or (b) keeping the mixed series with a disclosure
code. Tool: `tools/verification/history_basis_study.py` (read-only, rebuilds the
series exactly like `ValuationService.buildOwnHistorySeries`).

### In Scope
- The measurement and its record (research.md).
- The decision, once the inputs are trustworthy.

### Out of Scope
- Any change to `valuation-v2` before the decision.

## Findings (summary — details in research.md)

1. **Composition**: with the provider's four fiscal periods, quarterly EPS exists
   only for the last four quarters, all visible from ~2026-08-14; so **~99 % of
   the 750 own-history points are annual-basis** for every symbol (the last ~11
   sessions are quarter-TTM). The comparison today is "quarter-TTM P/E vs a
   history of FY-EPS P/E", uniformly, not a sporadic mix.
2. **The measurement exposed Q-57**: the annual income-statement/cash-flow rows
   carried mirrored fiscal years (FY2022 under 2025 …), so every annual-basis
   point was built on the wrong year. Differences measured today (median 32 %,
   outliers > 10,000 %) are dominated by that defect and are not a basis effect.

## Decision

Deferred. Re-run the study after the universe is re-exported with correct
years (Q-57). Expected options then:
- (a) if |PE_annual / PE_quarter − 1| is small and stable (median < 10 %):
  accept, add disclosure code `HISTORY_ANNUAL_BASIS` with the annual share;
- (b) otherwise: `valuation-v3` computes the own-history comparison on one
  basis — today's P/E on the **same FY basis** as the series (P/E on latest FY
  EPS) for the percentile, while the headline P/E stays quarter-TTM.

## Success Criteria
| ID | Criterion |
|---|---|
| SC-001 | Study re-run after Q-57 with the table recorded in research.md. |
| SC-002 | Decision (a) or (b) recorded with numbers; if (b), contract `valuation-v3` written before code. |
