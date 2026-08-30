# Implementation Plan: Provider Data Expansion

**Feature**: `008-provider-data-expansion` · **Created**: 2026-08-30
**Status**: Implemented 2026-08-30 (US1, US2, US3); plan/contracts/tasks were
written retroactively on the owner's instruction after research closed the
open questions — recorded here so the process deviation is visible, not hidden.
**Spec**: `spec.md` · **Research**: `research.md`

## Summary

Map three confirmed provider facts into the existing pipelines without adding
a rule version, provider, host, or table: derived `EBITDA` (ratio margin ×
net revenue), derived `FREE_CASH_FLOW` (operating cash flow + signed capex,
annual-only), and session price limits / foreign room from the TCBS live
overlay. The balance-sheet gap is recorded as unavailable; `EV_EBITDA` stays
honestly withheld.

## Constitution Check

| Principle | Before research | After design |
|---|---|---|
| I Deterministic core | derivations are pure decimal, versioned rule ids | ✔ `kbs-ebitda-margin-x-net-revenue-v1`, `kbs-fcf-ocf-plus-capex-v1`, rounded once at scale 6 |
| II Provenance / temporal truth | each derived record must carry rule id + inputs | ✔ `derivation` in the checksummed package record → `quality_reason` on the `DEFINED` metric row; limits carry the live quote's `observedAt` and reset per trading date |
| III Boundaries | no new module/provider/host | ✔ exporter + existing import boundary; market→stock via existing `LiveStockQuoteService` |
| IV Security | read-only adapters; no payload logging | ✔ no new endpoint; limits ride existing allowlisted frames |
| V Spec before code | **violated** — code for US1/US2 landed before this plan | recorded; artifacts completed the same day, tasks list actual verification |
| VI Testing | exporter unit tests, Java summary/parser tests, mapper/service tests, FE test | ✔ (see tasks) |
| VII Resilience | absent inputs → `MISSING`; absent live frame → `null` + reason | ✔ |
| VIII Simplicity | no unmapped-field speculation; balance-sheet derivation chain rejected | ✔ |

## Module ownership and data flow

```
KBS (vnstock 4.0.6, community)
  income_statement + ratio ──► export_fundamentals.derive_ebitda ──┐
  cash_flow (annual only)  ──► export_fundamentals.derive_free_cash_flow ─┤ package v1 (+ "derivation")
                                                                          ▼
      FundamentalReportImportPackageParser ─► FundamentalReportImportService ─► StockIngestionService
                                                                          ▼
      fundamental_report_metric (EBITDA / FREE_CASH_FLOW rows, quality_reason = rule id)
                                                                          ▼
      FundamentalSummaryCalculator: EBITDA_TTM (4-quarter sum), FREE_CASH_FLOW (newest, else latest annual)
                                                                          ▼
      ValuationService (EV_EBITDA still MISSING_EV_INPUTS) · fundamentals section (FCF row)

TCBS Thesis s|4 (ceilPrice/floorPrice) + tickerCommons (room)
  ─► TcbsThesisFrameMapper ─► TcbsLiveEquityQuoteService session cache (per trading date)
  ─► LiveStockQuoteService.LiveQuote ─► TcbsStreamStockQuoteProvider.QuoteObservation
  ─► StockOverviewService ─► StockOverviewResponse.price.{ceilingPrice,floorPrice,foreignRoom,limitState}
  ─► finvera-fe stock overview
```

## Decisions carried from research

- R-001 balance sheet unavailable → no `TOTAL_DEBT`/`CASH`/`EQUITY` mapping.
- R-002 FCF annual-only; summary falls back to latest annual report.
- R-003 EBITDA from margin × net revenue; gross `revenue` row dropped.
- R-004 `YYYY-Năm` annual columns; `export_all_symbols.py` emits an annual
  package per symbol; the import directory scan picks both up.
- R-005 provenance via `derivation` → `quality_reason` (no migration).
- R-006 limits/room are session context, not persisted.

## Data, precision, time

No schema change. New metric rows use existing catalog codes (`EBITDA`,
`FREE_CASH_FLOW`, V003) at `numeric(28,6)` base VND; per-share/ratio rules
untouched. Limits are `BigDecimal` base VND, room is a share count (`Long`),
both tied to the live quote's session and `observedAt`.

## Security and privacy

Unchanged trust boundaries. The probe script prints ids/labels/magnitudes only.

## Test strategy (requirement → test)

| Req | Test |
|---|---|
| FR-001, DATA-002/005 | `test_ebitda_is_derived_from_margin_times_net_revenue_only_where_both_exist`, `test_only_net_revenue_row_is_mapped_to_revenue`, `FundamentalReportImportServiceTests` (derivation pass-through), `FundamentalSummaryTests` |
| FR-002, DATA-001/005 | `test_free_cash_flow_is_ocf_plus_signed_capex_and_accepts_annual_nam_columns`, `test_bank_without_capex_row_yields_no_fcf`, `FundamentalSummaryTests.freeCashFlowFallsBackToTheLatestAnnualReport…` |
| FR-003, DATA-004 | `TcbsThesisFrameMapperTests` (ceil/floor), `TcbsLiveEquityQuoteServiceTests` (limits per session), `StockOverviewServiceTests`/FE overview test |
| FR-004 | catalog rows exist (V003) — asserted by `StockMigrationTests` count |
| NFR-002 | re-import idempotency already covered by `packageSha256` guard tests |

## Rollout / rollback

Exporter `TOOL_VERSION` 0.3.0 → 0.4.0 forces re-export of every fundamentals
package on the next refresh (existing checkpoint rule). Rollback = revert the
exporter version and packages; accepted rows are revision-chained and never
edited in place. Live limits are read-only overlay fields; disabling the
overlay removes them.

## Complexity tracking

None beyond the recorded process deviation (Constitution V).
