# ADR-0011: Use VCI (via vnstock) for Fundamental Statements and Ratios

**Status**: Accepted — owner approved 2026-08-31 ("impl theo hướng chuẩn nhất"); implemented by Feature 018  
**Date**: 2026-08-31  
**Decision owners**: Finvera maintainer  
**Related specs**: `011-provider-ingestion-normalization` (research R-009, contract `kbs-yearly-statement-orientation-v1`), `017-history-basis-consistency`, docs/REMEDIATION_PLAN.md Q-57  
**Note**: ADR-0010 (TCBS Thesis live overlay) is referenced by ADR-0003/0009 but has no file in this directory; this ADR does not depend on it.

## Context

Feature 017's measurement exposed that every fundamentals fact Finvera holds
from the KBS financial-statement endpoints is attached to the wrong period:

| KBS endpoint (vnstock 4.0.6 / 4.0.7, page_size = 1) | What the page label says | What the page contains | Evidence |
|---|---|---|---|
| Income statement, yearly | `2025-Năm`, ReportDate 2026-02-27 | **FY2022** (VNM net revenue 59,956 bn) | audited figures; VCI; 1,388/1,451 instruments fit the mirror |
| Cash flow, yearly | same | mirrored the same way (PBT rows equal the income statement column for column) | probe |
| Income statement, quarterly | `2026-Q2`, `2026-Q1`, `2025-Q4`, `2025-Q3` | 2025-Q3, 2025-Q4, **2025-Q1**, 2026-Q2 | VCI cross-check, 20/20 symbols, 0/4 labels correct |
| Ratio, quarterly | `2026-Q1` | FY2025-end values (BVPS, trailing EPS) | exact equality with the yearly ratio row |
| Ratio, yearly | `2024-Năm` | FY2024 (FPT P/E 26.7 = the 2024 peak) | correct |

The KBS API returns a correct `Head` (YearPeriod, TermName, ReportDate) with
another period's `Content` — the defect vnstock's own source comments on
("KBS bug: duplicated IDs and mixed-up values"). The yearly mirror can be
undone by rule (exporter 0.7.0); the quarterly permutation cannot be trusted
as a rule. Consequences today: TTM windows are not contiguous (VNM EPS_TTM
4,350 vs true 4,728), annual metrics are mirrored, `ANNUAL_BASIS` growth,
own-history valuation points, screener fundamentals and AI answers built on
them are wrong. Prices, indices, breadth, technical indicators and the yearly
ratio frame are verified correct and unaffected.

VCI, reachable through the same vnstock package (4.0.7 — 4.0.6 raises
`UnboundLocalError` for VCI Finance), returns income statement, **balance
sheet** (absent from KBS), cash flow and ratios with labels verified against
audited FY2022–FY2025 figures for VNM, HPG, FPT, MBB; 8 periods in the
community edition (years back to 2018, quarters back to 2024-Q3) versus KBS's
4; values in raw VND; ratios as fractions with an explicit `ratioType`
(`RATIO_TTM`). Quarterly frames are contiguous and consistent with the yearly
ones (four 2024 quarters sum to FY2024 for VNM).

## Decision Drivers

- Constitution I/II: a stored fact must be the fact it claims to be; period identity is part of the fact.
- Owner instruction 2026-08-31: "sai data là không thể được".
- Verifiability: labels must be checkable against an independent anchor, not only against the same provider.
- Community-tier constraints: request budget, 8-period depth, personal non-commercial use (ADR-0009 terms apply to VCI as another vnstock source).

## Considered Options

### Option A: Move all fundamentals to VCI (recommended)

Income statement, balance sheet, cash flow and ratios from `Finance(source="vci")`
via vnstock 4.0.7; KBS stays for daily bars, index history, listing and company
overview (all anchored and correct). New exporter module + contract
`vci-fundamentals-v1` written field-by-field with audited anchors (the Feature 011
method), new derivation ids, the fundamental metric catalog extended for balance
sheet items (total debt, cash → EV/EBITDA, net debt), 8-quarter TTM/growth become
real (fewer `ANNUAL_BASIS`), own-history basis can be quarterly for 8 quarters.
Cost: ~2 days engineering + a universe re-crawl (≈ 1,522 × 8 calls); all
fundamentals-derived rows rebuilt (summaries, valuations, screener). Risk: VCI
rate limits/entitlement unknown until probed; vnstock upgrade to 4.0.7 for the
exporter project.

### Option B: Keep KBS and repair by rule

Yearly mirror is deterministic and already implemented; the quarterly
permutation observed is consistent across 20 symbols today but has no
documented semantics and could change with any provider page. Cost: low today;
risk: silent recurrence, no balance sheet, 4 periods only, still no independent
anchor. Rejected.

### Option C: Keep KBS values, relabel by matching against VCI

Double the calls, VCI becomes the truth anyway. Rejected as strictly worse than A.

## Decision

**Option A**, accepted 2026-08-31. Scope: all fundamentals datasets; market/price datasets
unchanged. VCI probed the same day (32 calls, mean 2.3 s, no rate-limit response) — see
specs/018-vci-fundamentals/research.md. Ratios are derived in Finvera (the VCI ratio frame is
malformed in vnstock 4.0.7), a refinement of the option as written.

## Consequences

### Positive
- Period identity verified against audited figures; contiguous quarterly windows; balance sheet unlocks EV/EBITDA and net-debt metrics; 8-period depth improves growth and own-history bases.

### Negative / Trade-offs
- Second provider dependency for fundamentals; a full re-crawl; every fundamentals contract (008/009/010/011/012) is superseded by `vci-fundamentals-v1` for the statement facts; rule ids from KBS derivations retire.

### Risks and Mitigations
- VCI quota or terms may be tighter → probe before build; pace as Q-39 does.
- Field semantics (fractions vs percent, parent vs total profit) → field-by-field audit with anchors, fixtures, and the `verify_calcs.py` provider check rewritten to compare against **audited anchors**, never labels alone.

## Migration and Rollback

1. Probe VCI quota/terms; upgrade `tools/market-data/provider-poc` to vnstock 4.0.7 (also fixes the Finance VCI bug).
2. Feature 018: spec → research (field audit with anchors) → contract → exporter/importer → tests → owner re-crawl → rebuild.
3. The default fundamentals import ends by retiring every current row of a non-primary source (`SOURCE_RETIRED`, nothing deleted, idempotent), so the plain refresh command is sufficient; until the re-crawl completes the product still serves KBS fundamentals (owner informed; personal system).
4. Rollback: KBS exporter and packages remain; revision chains keep every prior fact.

## Validation

- Anchor set (VNM, HPG, FPT, MBB, MWG, DBC audited FY2022–FY2025; four 2024 quarters summing to FY2024) checked on every export.
- `verify_calcs.py` provider check re-implemented against anchors; `history_basis_study.py` re-run; AI e2e re-run.
- Review after the first full month of daily refreshes.
