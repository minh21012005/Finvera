# Finvera Project: Live Deployment Status

**Date**: 2026-08-22
**Summary**: Feature 001 (Market Overview) and Feature 002 (Stock Detail) are
now both fully implemented and wired for live data. All four Feature 002
provider gates are closed (the owner ran the G-03 probe successfully). No
implementation work remains on either feature — only owner-side activation
steps (credentials, one OTP renewal, running the Vnstock exporters once) and
the unrelated T073 usability trials.

---

## 📊 Feature 001: Market Overview — live wiring complete

| Component | Status |
|-----------|--------|
| TCBS live HTTP client + OTP session lifecycle (`TcbsHttpRestClient`, `TcbsHttpSessionState`) | ✅ Done, tested |
| Owner-only renewal endpoint (`TcbsRenewalService`/`TcbsRenewalController`) | ✅ Done, tested |
| Live polling scheduler (`TcbsLivePollingScheduler`) | ✅ Done, tested |
| Spring wiring + config (`MarketConfiguration`, `application.yaml`, `.env*`) | ✅ Done |
| Runbook (`docs/runbooks/private-market-overview.md`) | ✅ Updated |

### To actually go live (owner-only)

```bash
# finvera-be/.env
FINVERA_TCBS_API_KEY=<owner's real TCBS iFlash key>   # currently blank
FINVERA_MARKET_PROVIDER_MODE=live                       # currently fixture
FINVERA_MARKET_PROVIDER_LIVE_ENABLED=true                # currently false
```

Start the backend, then call the renewal endpoint once with a real OTP — see
`docs/runbooks/private-market-overview.md` → "Activate live TCBS ingestion."

**Known pre-existing failure, unrelated to this work** (confirmed via `git
stash` against the unmodified codebase): `PortfolioSchemaMigrationTests.
enforcesCompositePrimaryKeyOnWatchlistItemPreventingDuplicates` — `column
"base_currency" of relation "market_instrument" does not exist`. A portfolio
schema/migration mismatch this session did not touch or introduce.

---

## 📊 Feature 002: Stock Detail and Analysis — all gates closed, all code shipped

### Gate decisions — all four resolved

| Gate | Decision | Status |
|------|----------|--------|
| G-01 fundamentals | Narrower scope (no balance-sheet metrics) | ✅ Closed |
| G-02 corporate actions | RAW-only, permanently | ✅ Closed |
| G-03 per-stock quotes | Owner ran `poc_tcbs.py --quote-symbols VNM,TCB,HPG` — PASS | ✅ Closed |
| G-04 sector classification | KBS taxonomy accepted | ✅ Closed |

Full rationale and evidence in `specs/002-stock-detail-analysis/research.md` R-012.

### What shipped

| Item | What it is | Status |
|------|-----------|--------|
| Fundamentals import (T058) | `export_fundamentals.py` → `FundamentalReportImportService` | ✅ Implemented, tested |
| Corporate actions (T060) | No adapter needed — RAW fallback already covers it | ✅ Closed as docs-only |
| Daily-bar import (T062 Vnstock half) | `export_daily_bars.py` → `StockHistoryImportService` | ✅ Implemented, tested |
| Sector reference import (T063) | `export_sector_reference.py` → `SectorReferenceImportService` | ✅ Implemented, tested |
| Sector cross-section valuation (T064) | Real peer data feeds `ValuationService`'s `sectorSeries`, gated by `finvera.stock.provider.sector-basis-enabled` | ✅ Implemented, tested |
| TCBS per-stock quote adapter (T062 TCBS half) | `TcbsStockQuoteProvider`, wired into `StockOverviewService` | ✅ Implemented, tested |
| Usability trials (T073) | Owner-only manual timed trials | 🔲 Unrelated to providers, not attempted |

**Bonus fix found while implementing T064**: a latent, pre-existing bug in
`ValuationService.persistAssessment` — it stored `sector_reference_id` on
every classified instrument's assessment regardless of whether the sector
basis was actually used, violating the database's own invariant the moment a
real "classified but below the 8-constituent floor" case was ever exercised.
Fixed to persist the id only when the basis was actually consumed.

### How the two live-gated features are switched on

Both stay off by default and are independent flags:

```bash
# finvera-be/.env
FINVERA_STOCK_QUOTE_LIVE_ENABLED=true     # live TCBS quote refresh in StockOverviewService
FINVERA_STOCK_SECTOR_BASIS_ENABLED=true   # sector cross-section valuation (validate latency in non-prod first)
```

`FINVERA_STOCK_QUOTE_LIVE_ENABLED` additionally requires Feature 001's own
live mode (`FINVERA_MARKET_PROVIDER_MODE=live`) to already be on, since it
reuses that same TCBS session rather than opening a second one.

### Owner action needed: run the new exporters at least once

Each new Vnstock import pipeline needs one real run to prove out end-to-end
(unit tests cover the Java-side logic, but nothing here has run against a
real Vnstock response yet):

```powershell
cd tools/market-data/vnstock-export
uv run python export_daily_bars.py --symbol VNM --start 2025-01-01 --end 2026-08-01
uv run python export_fundamentals.py --symbol VNM --period quarter
uv run python export_sector_reference.py --scheme-version 4.0.6   # confirm via `uv pip show vnstock`
```

Review each output package, then point the matching
`FINVERA_STOCK_IMPORT_*_PACKAGE_PATH` / `*_ENABLED` env vars at it and start
the backend once to import.

---

## What's left (owner-only, no more code)

1. **Feature 001**: set `FINVERA_TCBS_API_KEY` + the two mode flags, do one OTP renewal.
2. **Feature 002 live quotes/sector basis**: flip the two flags above once ready.
3. **Feature 002 historical data**: run the three exporters above once each and import.
4. **T073**: three timed usability trials, whenever convenient — unrelated to any of the above.
