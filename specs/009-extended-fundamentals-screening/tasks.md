# Tasks: Extended Fundamentals and Screening

**Input**: `spec.md`, `research.md`, `plan.md`, `contracts/provider-ratio-facts-v1.md`.
Written before implementation (2026-08-30).

## Phase 1: Catalog and acceptance

- [x] T001 [FR-001, DATA-001, DATA-003] Migration `finvera-be/src/main/resources/db/migration/V015__extend_fundamental_metric_catalog.sql`: insert the 22 codes from the contract U-2 table (category `RATIO`, unit/sign per table, scale 4) and `update` `DEBT_TO_EQUITY.unit_type = 'PERCENT'`.
      Verify: `StockMigrationTests` catalog count 18 → 40; `DEBT_TO_EQUITY` row reads `PERCENT`.
- [x] T002 [DATA-003] `FundamentalReportAcceptance`: add the 22 codes to `ALLOWED_METRIC_CODES` and to `UNSCALED_METRIC_CODES`.
      Verify: `FundamentalReportTests` — a new code with `unitScale=1000` is accepted unscaled.

## Phase 2: Exporter

- [x] T003 [FR-001, FR-004, DATA-004] `export_fundamentals.py`: extend `RATIO_MAP` with the 22 `item_id`s; treat growth-table `total_assets`/`owners_equity` rows; multiply `dividend_yield` by 100 with `derivation = kbs-dividend-yield-fraction-to-percent`; never map the zero-only cash-flow family; bump `TOOL_VERSION` to `0.5.0`.
      Verify: exporter tests — mapping with units, dividend yield 0.04 → 4, zero family absent.
- [x] T004 [DATA-002] `FundamentalReportImportService`: stamp `PROVIDER_REPORTED` as the quality reason of directly mapped ratio rows (derived rows keep their rule id).
      Verify: `FundamentalReportImportServiceTests`.

## Phase 3: Summary and stock page (US1)

- [x] T005 [FR-001, FR-002] `FundamentalSummaryCalculator.addLatestMetric` for the 22 codes; FE `FUNDAMENTAL_METRIC_LABELS` + category grouping in `stock-fundamentals.tsx`.
      Verify: `FundamentalSummaryTests`; FE `fundamentals-valuation.test.tsx`.

## Phase 4: Screener (US2)

- [x] T006 [FR-003] `ScreenerV1.FundamentalFilter` gains a `RatioFilters` component (7 pairs) with a backward-compatible 14-arg constructor; `evaluateFundamental` evaluates them via `evaluateSummaryMetric`; `validateRanges` covers them; `ScreenRequest.FundamentalFilter` DTO + `toDomain`.
      Verify: `ScreenerV1Tests` — match, `MISSING` → unavailable with reason, `min > max` rejected.
- [x] T007 [FR-003] Contracts: `specs/003-stock-screener/contracts/screener-v1.md` table + `stock-screener.openapi.yaml` `FundamentalFilter` (additive); FE `screener-filters-model.ts`, `api` type, `screener-filters.tsx` inputs.
      Verify: FE tests, lint, build.

## Phase 5: Validation

- [x] T008 Full gates: exporter pytest, `.\mvnw.cmd test`, FE test/lint/build; record in `docs/REMEDIATION_PLAN.md` (Q-17 → done) and this file.
      Evidence (2026-08-30): exporter 23/23 (two pre-existing assertions updated to the contract: dividend yield 0.04 → 4.000000, `EV_EBITDA` no longer imported); targeted backend 80/80 (`StockMigrationTests` catalog 40, `FundamentalReportTests`, `FundamentalSummaryTests`, `ScreenerV1Tests` incl. MISSING-excluded case, `ScreenerServiceTests`, controllers); FE 129/129, lint clean, build clean. Full backend suite: **659/659, BUILD SUCCESS**. FE re-verified after the category grouping (FR-002): 129/129, lint clean, build clean.

## Traceability

| Requirement | Tasks |
|---|---|
| FR-001 | T001, T002, T003, T005 |
| FR-002 | T005 |
| FR-003 | T006, T007 |
| FR-004 | T003 |
| DATA-001..004 | T001–T004 |
| NFR-001/002 | T006 (unchanged fetch), existing idempotency tests |
