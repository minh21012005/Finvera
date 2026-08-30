# Tasks: Provider Data Expansion

**Input**: `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/kbs-derivations-v1.md`, `quickstart.md`.
**Process note**: T004–T009 code landed on 2026-08-30 before `plan.md` and this
file existed (owner instruction: implement, backfill artifacts). Their
verification evidence is real; the ordering deviation is recorded in
`plan.md` Constitution Check (V).

## Phase 1: Research and clarification

- [x] T001 [DATA-001] Add the read-only evidence probe `tools/market-data/provider-poc/probe_balance_sheet.py` and run it for VNM/FPT/HPG/MBB, quarter and year.
      Verify: probe prints per-dataset row counts; no credential or raw payload in output. Evidence: research R-001..R-004 (balance_sheet=0 everywhere; cash_flow annual-only; `ebitda_net_revenue` present; `YYYY-Năm` columns).
- [x] T002 [FR-001, DATA-001] Record decisions R-001..R-006 in `research.md` and amend `spec.md` (FR-001 narrowed, SC-001 re-scoped, both open questions resolved).
      Verify: `spec.md` contains no `[NEEDS CLARIFICATION]` marker.
- [x] T003 Write `plan.md`, `data-model.md`, `contracts/kbs-derivations-v1.md`, `quickstart.md` (retroactive).

## Phase 2: US1 — EBITDA facts (P1) and US2 — Free cash flow (P2)

- [x] T004 [US1, FR-001, DATA-002, DATA-003, DATA-005] `tools/market-data/vnstock-export/export_fundamentals.py`: accept `YYYY-Năm` annual columns; map only the net `revenue` row when both gross and net exist; add `derive_ebitda` (`kbs-ebitda-margin-x-net-revenue-v1`) and `derive_free_cash_flow` (`kbs-fcf-ocf-plus-capex-v1`), each emitting a record with `derivation` only when every input exists for the same period; bump `TOOL_VERSION` to `0.4.0`.
      Verify: `uv run --project ..\provider-poc pytest tests` — 18 passed (4 new: net-revenue selection, EBITDA same-period rule + vector `3687164674495.400000`, FCF annual vector `7370359124000.000000`, bank without capex → no FCF).
- [x] T005 [US2, DATA-001] `export_all_symbols.py`: export an annual package per symbol in addition to the requested period (`fundamentals_annual` checkpoint, honoured by `is_finished`).
      Verify: covered by T004 test run (module imports) and the quickstart step 2 file pair.
- [x] T006 [US1, US2, DATA-002] `finvera-be` import path: `MetricPeriodRecord` gains optional `derivation`; `FundamentalReportImportPackageParser` reads it; `FundamentalReportImportService` passes it as the `DEFINED` metric's quality reason.
      Verify: `FundamentalReportImportServiceTests` 3/3, `StockIngestionServiceTests` 12/12.
- [x] T007 [US2, FR-002] `FundamentalSummaryCalculator`: `FREE_CASH_FLOW` read from the newest period, else the latest accepted `ANNUAL` report (`addLatestMetricWithAnnualFallback`); basis period unchanged.
      Verify: `FundamentalSummaryTests` 19/19 incl. `freeCashFlowFallsBackToTheLatestAnnualReportWhenTheNewestQuarterLacksIt`; `FundamentalReportTests` 14/14.

## Phase 3: US3 — Session price limits and foreign room (P3)

- [x] T008 [US3, FR-003, DATA-004] `TcbsThesisFrameMapper.EquityReferenceUpdate` carries `ceilingPrice`/`floorPrice`; `TcbsLiveEquityQuoteService` keeps them (and `tickerCommons.room`) in the per-trading-date session cache; `LiveStockQuoteService.LiveQuote`, `StockQuoteProvider.QuoteObservation`, `TcbsStreamStockQuoteProvider` pass them through.
      Verify: `TcbsThesisFrameMapperTests`, `TcbsLiveEquityQuoteServiceTests` (limits present on the live quote; reset on rollover).
- [x] T009 [US3, FR-003] `StockOverviewService`/`StockOverviewResponse`: `price.ceilingPrice`, `price.floorPrice`, `price.foreignRoom`, `price.limitState` (`AT_CEILING`/`AT_FLOOR`/null); reason `PRICE_LIMITS_UNAVAILABLE` when no live overlay; OpenAPI `specs/002-stock-detail-analysis/contracts/stock-detail.openapi.yaml` amended additively; `finvera-fe` overview renders "Trần / Sàn / Room NN" with a textual at-limit cue.
      Verify: `StockOverviewServiceTests`, FE `overview-chart.test.tsx` (limits row), `npm run lint/build`.

## Phase 4: Validation

- [x] T010 Run the full gates: exporter pytest, `.\mvnw.cmd test`, FE test/lint/build; record counts in `docs/REMEDIATION_PLAN.md` (Q-13..Q-17 statuses).
      Verify (2026-08-30): exporter `pytest tests` 18/18; `finvera-be` full suite 654/654 + `StockOverviewLimitsTests` 2/2 (BUILD SUCCESS); `finvera-fe` vitest 127/127, lint clean, build clean. Recorded in `docs/REMEDIATION_PLAN.md` (Q-13..Q-16 done, Q-17 deferred).

## Requirement traceability

| Requirement | Tasks | Tests |
|---|---|---|
| FR-001 | T002, T004, T006 | exporter EBITDA tests; `FundamentalSummaryTests` |
| FR-002 | T004, T005, T007 | exporter FCF tests; annual-fallback test |
| FR-003 | T008, T009 | mapper/service/overview tests |
| FR-004 | — (catalog codes pre-exist, V003) | `StockMigrationTests` catalog count |
| FR-005 | T004 | exporter prints sample rows (existing behaviour) |
| DATA-001..006 | T001, T004, T006, T008 | as above |
| SEC-001/002 | no change | existing negative tests |
| NFR-001..003 | T005, T006 | idempotent re-import (existing `packageSha256` tests) |
