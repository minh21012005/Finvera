# Finvera Project: Live Deployment Status

**Date**: 2026-08-22
**Summary**: Feature 001 (Market Overview) is fully wired for live TCBS
ingestion — code, tests, config, and runbook are done; only the owner's
credential setup and one OTP renewal remain. Feature 002 (Stock Detail) has
all four provider gates resolved (three closed outright, one closeable after
one owner-run probe) and three of the four import pipelines implemented;
one piece (sector cross-section valuation wiring) is deliberately deferred as
a focused follow-up given its algorithmic/performance complexity.

---

## 📊 Feature 001: Market Overview — live wiring complete

| Component | Status |
|-----------|--------|
| TCBS live HTTP client + OTP session lifecycle (`TcbsHttpRestClient`, `TcbsHttpSessionState`) | ✅ Done, tested |
| Owner-only renewal endpoint (`TcbsRenewalService`/`TcbsRenewalController`) | ✅ Done, tested |
| Live polling scheduler (`TcbsLivePollingScheduler`) | ✅ Done, tested |
| Spring wiring + config (`MarketConfiguration`, `application.yaml`, `.env*`) | ✅ Done |
| Runbook (`docs/runbooks/private-market-overview.md`) | ✅ Updated |
| Tests | ✅ 22 new unit tests, no live network |

### To actually go live (owner-only)

```bash
# finvera-be/.env or .env.local
FINVERA_TCBS_API_KEY=<owner's real TCBS iFlash key>   # currently blank
FINVERA_MARKET_PROVIDER_MODE=live                       # currently fixture
FINVERA_MARKET_PROVIDER_LIVE_ENABLED=true                # currently false
```

Start the backend, then call the renewal endpoint once with a real OTP — see
`docs/runbooks/private-market-overview.md` → "Activate live TCBS ingestion."
No more code is needed for Feature 001.

**Known pre-existing failure, unrelated to this work** (confirmed via `git
stash` against the unmodified codebase): `PortfolioSchemaMigrationTests.
enforcesCompositePrimaryKeyOnWatchlistItemPreventingDuplicates` — `column
"base_currency" of relation "market_instrument" does not exist`. A portfolio
schema/migration mismatch this session did not touch or introduce.

---

## 📊 Feature 002: Stock Detail and Analysis

### Gate decisions (owner, 2026-08-22) — all four resolved

| Gate | Decision | Status |
|------|----------|--------|
| G-01 fundamentals | Narrower scope (no balance-sheet metrics) | ✅ Closed |
| G-02 corporate actions | RAW-only, permanently | ✅ Closed |
| G-03 per-stock quotes | Endpoint confirmed via TCBS docs; needs one owner-run probe | 🟡 One step from closed |
| G-04 sector classification | KBS taxonomy accepted | ✅ Closed |

Full rationale in `specs/002-stock-detail-analysis/research.md` R-012.

### What shipped this session

| Item | What it is | Status |
|------|-----------|--------|
| Fundamentals import (T058) | `export_fundamentals.py` → `FundamentalReportImportService` | ✅ Implemented, tested |
| Corporate actions (T060) | No adapter needed — RAW fallback already covers it | ✅ Closed as docs-only |
| Daily-bar import (T062 Vnstock half) | `export_daily_bars.py` → `StockHistoryImportService` | ✅ Implemented, tested |
| Sector reference import (T063) | `export_sector_reference.py` → `SectorReferenceImportService` | ✅ Implemented, tested |
| TCBS per-stock quote adapter (T062 TCBS half) | `TcbsStockQuoteProvider` | 🔲 **Owner action required first** — see below |
| Sector cross-section valuation wiring (T064) | Wire real peer data into `ValuationService`'s `sectorSeries` | 🔲 **Deliberately deferred** — see below |
| Usability trials (T073) | Owner-only manual timed trials | 🔲 Unrelated to providers, not attempted |

### Owner action needed: close G-03

```powershell
cd tools/market-data/provider-poc
.\.venv\Scripts\python.exe .\poc_tcbs.py --quote-symbols VNM,TCB
```

Review `poc-output/tcbs-capability-summary.json`'s new
`ticker_commons_quote_symbols` section and share the sanitized result so it
can be recorded in `research.md` R-012 G-03 — that's what unblocks
`TcbsStockQuoteProvider`.

### Why T064 (sector cross-section valuation) was deliberately left for later

`ValuationService.findBySymbol` currently hardcodes an empty sector series.
Wiring in real data means computing every peer instrument's current metrics
cross-sectionally — a new algorithm, not a simple lookup — with real latency
implications for large sectors (up to ~83 constituents per the G-04
evidence). Rather than rush a correctness- and performance-sensitive change
to shared valuation logic in the same pass as everything else, this is
flagged as the one clearly-scoped remaining task, worth its own focused
session. Data for it (sector reference + equity profile links) is already
importable via the pipeline above; only the valuation-side wiring is left.

### Owner action needed: run the new exporters at least once

Each new import pipeline needs one real run to prove out end-to-end (unit
tests cover the Java-side logic, but nothing here has been run against a
real Vnstock/TCBS response yet):

```powershell
cd tools/market-data/vnstock-export
uv run python export_daily_bars.py --symbol VNM --start 2025-01-01 --end 2026-08-01
uv run python export_fundamentals.py --symbol VNM --period quarter
uv run python export_sector_reference.py --scheme-version 4.0.6   # confirm via `uv pip show vnstock`
```

Review each output package, then point the matching
`FINVERA_STOCK_IMPORT_*_PACKAGE_PATH` / `*_ENABLED` env vars at it and start
the backend once to import.
