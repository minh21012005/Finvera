# Finvera Project: Live Deployment Status

**Date**: 2026-08-22
**Summary**: Feature 001 (Market Overview) is fully wired for live TCBS
ingestion — code, tests, config, and runbook are done; only the owner's
credential setup and one OTP renewal remain. Feature 002 (Stock Detail) has
all four provider gates resolved (three closed outright, one closeable after
one owner-run probe), and every implementation task that doesn't require that
owner-run probe is now done — including sector cross-section valuation wiring
(T064), completed in a follow-up pass. The only Feature 002 code still
blocked is the TCBS live per-stock quote adapter (T062's TCBS half), which
needs the owner's G-03 probe evidence first.

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
| Sector cross-section valuation wiring (T064) | Real peer data now feeds `ValuationService`'s `sectorSeries`, gated by `finvera.stock.provider.sector-basis-enabled` | ✅ Implemented, tested |
| TCBS per-stock quote adapter (T062 TCBS half) | `TcbsStockQuoteProvider` | 🔲 **Owner action required first** — see below |
| Usability trials (T073) | Owner-only manual timed trials | 🔲 Unrelated to providers, not attempted |

**Bonus fix while implementing T064**: found and fixed a latent, pre-existing
bug in `ValuationService.persistAssessment` — it stored `sector_reference_id`
on every classified instrument's assessment regardless of whether the sector
basis was actually used, violating the database's own invariant the moment a
real "classified but below the 8-constituent floor" case was ever exercised
(nothing before T064 ever populated `sector_reference_id`, so this was never
triggered). Now persists the id only when the basis was actually consumed.

### Owner action needed: close G-03

```powershell
cd tools/market-data/provider-poc
.\.venv\Scripts\python.exe .\poc_tcbs.py --quote-symbols VNM,TCB
```

Review `poc-output/tcbs-capability-summary.json`'s new
`ticker_commons_quote_symbols` section and share the sanitized result so it
can be recorded in `research.md` R-012 G-03 — that's what unblocks
`TcbsStockQuoteProvider`.

### Sector cross-section valuation (T064) — how it's gated

`ValuationService` computes every sector peer's current metrics via the same
`ValuationV1.computeMetrics` formulas the subject instrument itself uses,
bounded to 100 peers (deterministic cap, sorted by instrument id — the
largest real sector seen in the G-04 evidence has ~83). It only runs when
`finvera.stock.provider.sector-basis-enabled=true`, which stays `false` by
default. Per the task's own instruction, flip it in a non-production profile
first and watch request latency for a large sector before enabling it in
production — the two new tests prove correctness, not production-scale
latency, which only real data can confirm.

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
