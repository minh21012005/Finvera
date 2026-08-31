# Plan: Feature 021 — Single Provider (VCI)

## Approach

Switch the four remaining KBS exporters to `source="vci"` under their existing package contracts
(field-compatible per research R-003), bump tool versions to 1.0.0, and make the backend treat
`VNSTOCK_VCI` as the preferred bar source. Full re-crawl per dataset (FR-002); revision chains
supersede; a bar-source retirement pass (mirroring Feature 018's fundamentals retirement) marks
KBS bars not-current **only where a current VCI bar exists for the same instrument and date**, so
a symbol VCI cannot serve keeps its KBS chart honestly.

## Components

| Layer | Change |
|---|---|
| `export_daily_bars.py` 1.0.0 | Quote(source="vci"); both ends of the range clamped (VCI returns a pre-start buffer); `adjustmentStatus = PROVIDER_ADJUSTED`; board-unit ×1000 unchanged |
| `export_history.py` 1.0.0 | index + equity closes via Quote(vci); `cut_to_range`; incremental index merge refuses packages from another upstreamSource (one full re-fetch) |
| `export_instrument_reference.py` 1.0.0 | Listing(vci), type STOCK, venue map HSX→HOSE |
| `export_equity_profile.py` 1.0.0 | Listing(vci) universe; Company(vci).overview: `issue_share` (first non-null among duplicated columns) cross-checked against market_cap/current_price (>1 % → UNVERIFIED, count kept) |
| `export_all_symbols.py` | unchanged — TOOL_VERSION bumps make every symbol's bars stale, and Q-58's version-scoped failures retry old failures |
| Backend `StockChartService` / `TechnicalIndicatorService` / `ValuationService` | `SOURCE_PREFERENCE` gains `VNSTOCK_VCI` at rank 0 (KBS second: still served where VCI has no bar) |
| Backend bar retirement | after the daily-bar import: bulk-mark current `VNSTOCK_KBS` bars superseded where a current `VNSTOCK_VCI` bar exists for the same (instrument, date); `quality_reason = SOURCE_RETIRED`; idempotent, logged |
| Backend `EquityProfileImportService` | preserve an existing non-null `companyNameEn` when the incoming record has none (VCI listing has no EN names; unknown ≠ removed) |
| Adjustment label (Q-59) | migration V016 extends the V003 checks to `PROVIDER_ADJUSTED`; `StockTypes.AdjustmentStatus` gains the value; `StockChartAssembler` serves a uniform provider-adjusted series as-is under its honest label; chart/technical/ingestion mappings + FE type and labels follow |
| `verify_calcs.py` | provider cross-check flips reference: stored (VCI) vs KBS probe; same 0.5 % adjustment tolerance |
| Docs | ADR-0013 (done), REMEDIATION P2-10, changelog |

## Constitution check

Provenance: every row keeps its `source`; retirement supersedes, never deletes. Missing ≠ zero:
KBS bars stay current where VCI has none. Determinism: per-dataset contracts + anchored tests.
No `Q-` ids in production code (rules cite ADR-0013 / specs/021).

## Rollout

1. Ship exporters + backend; suites green (pytest, Maven, vitest).
2. Owner: let the running fundamentals crawl finish, then `.\refresh-data.ps1` again — stage 1
   re-exports instrument reference/profiles/market overview from VCI, `export_all_symbols` re-crawls
   bars (tool version stale) and the fundamentals 1.1.0 ratio backfill in the same pass (~6–8 h).
3. Post-crawl: `verify_calcs.py` fully green; UI spot-checks; then Q-57/Q-38/P2-10 evidence rows.
