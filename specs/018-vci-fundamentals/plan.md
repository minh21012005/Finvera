# Implementation Plan: Fundamental statements from VCI

**Feature**: `018-vci-fundamentals` · 2026-08-31 · **Spec**: `spec.md` · **Research**: `research.md` · **Contract**: `contracts/vci-fundamentals-v1.md` · **ADR**: docs/adr/0011

## Summary

A new exporter module builds the existing fundamentals package from VCI
statements with company-type mapping and published derivations; the importer
gains one rule (a newer source supersedes the older source's row); the crawl
switches to the VCI pass; the universe is re-exported and everything downstream
recomputes through the existing warmups.

## Constitution Check

| Principle | Assessment |
|---|---|
| I | Derivations are explicit formulas in Decimal (scale 6) with rule ids; period identity anchored to audited figures and to Σ quarters = FY. ✔ |
| II | `sourceReport`, `companyType`, rule ids on every fact; v1's reduced ratio set is disclosed as MISSING/NOT_REPORTED, never zero. ✔ |
| III | Exporter is a separate module (`export_fundamentals_vci.py`); backend change limited to `StockIngestionService.ingestFundamentalReport` + a repository finder. ✔ |
| V | ADR → spec → research → contract → plan → tasks → code → re-crawl → verify. ✔ |
| VI | Fixture-based exporter tests with the anchor set; ingestion supersession test; existing suites. ✔ |
| VIII | Package contract, importer, calculators, warmups unchanged. ✔ |

## Changes

### Tooling
- `tools/market-data/provider-poc/pyproject.toml`: `vnstock==4.0.7` (VCI Finance broken in 4.0.6); lockfile updated; existing exporter tests must stay green (KBS bars/listing/profile paths unchanged).
- `tools/market-data/vnstock-export/export_fundamentals_vci.py` (new, TOOL_VERSION 1.0.0, SOURCE `VNSTOCK_VCI`): `detect_company_type`, `mapping_for(type)`, `statement_records`, `shares_by_period`, `derive_*` per contract, `build_metric_records(symbol, frames)` returning records with `canonicalRecord`, reusing `export_fundamentals.build_package`/`canonical_json`/`period_bounds`.
- `tests/fixtures/vci/*.json` trimmed from the 2026-08-31 probe (4 company types); `tests/test_export_fundamentals_vci.py` asserting the contract's anchor set, quarter sums, derivation formulas, first-occurrence rule, zero-EPS drop.
- `export_all_symbols.py`: fundamentals pass calls the VCI module for both periods (6 calls/symbol), `CALLS_PER_DATASET` updated, checkpoint keys `fundamentals_vci`, staleness rule reused; KBS fundamentals pass removed.

### Backend
- `FundamentalReportRepository.findFirstByInstrumentIdAndPeriodTypeAndFiscalYearAndFiscalQuarterAndReportKindAndCurrentTrue`.
- `StockIngestionService.ingestFundamentalReport`: rule I-1 — different-source current row → superseded (`SOURCE_SUPERSEDED` as the restatement reason), out-of-order guard only for same source. Test in `StockIngestionServiceTests`.
- FE dictionary: rule ids `vci-*` and `SOURCE_SUPERSEDED` worded (contract reason-code-presentation-v1 append).

### Verification and rollout
1. Exporter tests; backend targeted + full suites; FE dictionary test.
2. Owner: `.\refresh-data.ps1` (toolVersion change → full fundamentals re-export ≈ 6 h at observed latency; daily bars incremental). The KBS `fundamentals-*.json` packages are ignored by the new pass.
3. After import: `verify_calcs.py` provider check rewritten to anchors; SC-002 quarter-sum query; `history_basis_study.py`; AI e2e; REMEDIATION Q-57 → DONE.
