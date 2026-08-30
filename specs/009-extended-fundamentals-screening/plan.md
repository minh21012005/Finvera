# Implementation Plan: Extended Fundamentals and Screening

**Feature**: `009-extended-fundamentals-screening` · 2026-08-30
**Spec**: `spec.md` · **Research**: `research.md` · **Contract**:
`contracts/provider-ratio-facts-v1.md` (+ additive amendments to Feature 003
`screener-v1.md` / `stock-screener.openapi.yaml`)

## Summary

Map 22 confirmed KBS ratio rows into the existing fundamentals catalog as
provider-reported facts, surface them by category on the stock page, add seven
screener filter pairs, and correct two unit defects (`DIVIDEND_YIELD` fraction,
`DEBT_TO_EQUITY` mis-declared unit). No formula, no new rule version, no new
provider or table.

## Constitution Check (before research → after design)

| Principle | Assessment |
|---|---|
| I Deterministic core | Provider ratios are stored as observed and labelled `PROVIDER_REPORTED`; Finvera-defined ratios (`PE`/`PB`/`EV_EBITDA`/`PEG`) keep their single `valuation-v1` definition; provider equivalents are deliberately not imported. ✔ |
| II Provenance | unit + period + source per row; unit table recorded from probe. ✔ |
| III Boundaries | exporter → existing import boundary → summary → screener. ✔ |
| V Spec before code | spec, research, plan, contract, tasks precede code this time. ✔ |
| VI Testing | exporter unit tests (map, dividend ×100, zero-family exclusion), `FundamentalReportTests` (allowlist/unscaled), `FundamentalSummaryTests`, `ScreenerV1Tests` (new filters incl. MISSING exclusion), migration test count, FE tests. ✔ |
| VIII Simplicity | rows without a story listed and excluded (R-003). ✔ |

## Data model (delta)

- `fundamental_metric_catalog`: **V015** inserts the 22 codes (`RATIO`
  category; `PERCENT` or `RATIO` unit per R-002; scale 4; sign `ANY` except
  liquidity/turnover `NON_NEGATIVE`) and **updates** `DEBT_TO_EQUITY.unit_type`
  to `PERCENT` (R-004.2). Catalog version unchanged (additive).
- `fundamental_report_metric.quality_reason` = `PROVIDER_REPORTED` on the new
  `DEFINED` rows; `kbs-dividend-yield-fraction-to-percent` on corrected
  `DIVIDEND_YIELD` rows.
- `fundamental_summary_metric`: new codes copied from the newest period.
- No screener persistence change.

## Data flow

```
KBS ratio ─► export_fundamentals.RATIO_MAP (+22) ─► package (derivation for dividend yield)
          ─► FundamentalReportAcceptance (allowlist + unscaled set) ─► fundamental_report_metric
          ─► FundamentalSummaryCalculator.addLatestMetric (+22) ─► fundamental_summary_metric
          ─► FundamentalReportService (unit from catalog) ─► stock page (grouped)
          ─► ScreenerService.fetchFundamentalMetrics (unchanged bulk query) ─► ScreenerV1 (+7 filters)
```

## Contracts

- `contracts/provider-ratio-facts-v1.md` (this feature): code ↔ `item_id` ↔ unit
  table, `PROVIDER_REPORTED` rule, exclusions.
- Feature 003 `screener-v1.md` table: seven rows added; `stock-screener.openapi.yaml`
  `FundamentalFilter`: seven `*Min/*Max` string pairs, all optional.
- Feature 002 `stock-detail.openapi.yaml`: unchanged (metrics are an open list
  keyed by catalog code).

## Test strategy

| Req | Test |
|---|---|
| FR-001, DATA-001..003 | exporter `test_extended_ratios_are_mapped_with_units`, `FundamentalReportTests` allowlist/unscaled, `StockMigrationTests` catalog count 18 → 40 |
| FR-002 | FE `fundamentals-valuation.test.tsx` grouping/labels |
| FR-003 | `ScreenerV1Tests` new filters: match, MISSING → unavailable, range validation |
| FR-004 | exporter `test_dividend_yield_fraction_becomes_percent`; SC-003 by DB after refresh |
| DATA-004 | exporter `test_zero_only_cash_flow_ratio_family_is_not_mapped` |

## Rollout / rollback

Exporter `TOOL_VERSION` 0.4.0 → 0.5.0 forces re-export; import creates one
revision per period (existing chain); V015 is additive plus one `update` on a
catalog row (reversible by a follow-up migration). FE reads units from the
catalog, so no client change is needed for the unit corrections beyond labels.
