# Implementation Plan: Valuation Data Completeness

**Feature**: `010-valuation-data-completeness` · 2026-08-30 · **Spec**: `spec.md` · **Research**: `research.md`

## Summary

Three changes, each independently valuable: (1) outstanding shares / free
float into `equity_profile` via provider overview with effective-dated
revisions; (2) bank EPS `item_id`; (3) `fundamental-summary-v2` annual
fallback for TTM and growth with per-metric basis disclosure. No valuation
formula change; no migration (columns exist).

## Constitution Check

| Principle | Assessment |
|---|---|
| I | Rule change is a new version (`fundamental-summary-v2`); v1 rows kept. Fallback is decimal, deterministic, disclosed. ✔ |
| II | `ANNUAL_BASIS` reason per metric; profile revision chain (`effective_from/to`). ✔ |
| III | exporter → import boundaries → summary; no new module. ✔ |
| V | spec/research/plan/tasks before code. ✔ |
| VI | v2 unit tests (fixed fixtures for 3-quarter+annual, 8-quarter, prior ≤ 0); importer revision test; exporter tests. ✔ |
| VIII | no estimation of shares; no partial-quarter mixing. ✔ |

## Data model (delta)

- `equity_profile`: no schema change. Importer closes the current row
  (`effective_to = record.effectiveFrom`) and inserts a new row carrying the
  existing `sector_reference_id` when shares arrive or change. `quality_reason`
  null once shares are present.
- `fundamental_summary.rule_version = 'fundamental-summary-v2'` (varchar, no
  check constraint). `fundamental_summary_metric.quality_reason = 'ANNUAL_BASIS'`
  on fallback-derived `DEFINED` rows.

## Contracts

- `vnstock-equity-profile-v1` package: optional `sharesOutstanding` (integer),
  `freeFloatRatio` (percent decimal string); `qualityReason` null when shares
  present. Additive.
- `fundamental-summary` rule: v2 documented in `research.md` R-005 and the
  calculator Javadoc; Feature 002 contracts reference the summary by rule
  version only.

## Data flow

```
Company(kbs).overview() ─► export_equity_profile (sharesOutstanding, freeFloatRatio)
   ─► EquityProfileImportService (revision on change) ─► equity_profile
income_statement earning_per_share_vnd ─► EPS (banks)
FundamentalSummaryCalculator v2 ─► *_TTM / *_GROWTH_PERCENT with ANNUAL_BASIS fallback
   ─► ValuationService (PE/PB/PEG inputs now DEFINED; marketCap from shares)
   ─► ScreenerService growth filters
```

## Test strategy

| Req | Test |
|---|---|
| FR-001 | `EquityProfileImportServiceTests` (create with shares; revise existing row; unchanged → ALREADY_PRESENT); exporter unit test with a fake overview |
| FR-002 | exporter test: `earning_per_share_vnd` → `EPS` ÷ 1000 |
| FR-003 | `FundamentalSummaryTests`: annual TTM fallback, annual YoY growth, 8-quarter path unchanged, prior ≤ 0 → NOT_APPLICABLE, basis period unchanged |
| FR-004 | existing `ValuationService*`/`Screener*` suites green on v2 |

## Rollout

Profile exporter tool version bump forces one re-export (one overview call per
symbol, paced by `--requests-per-minute`, default 30 like the bar exporter ≈ 50 min; the refresh script runs the profile exporter unconditionally, so no checkpoint change is needed). Fundamentals
tool version unchanged (map addition only affects banks' EPS; a `-FullRefresh`
or the Q-30 staleness rule picks it up — owner runs `-FullRefresh` once).
Summary/valuation warmup recomputes everything under v2.
