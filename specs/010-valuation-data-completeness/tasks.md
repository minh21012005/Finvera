# Tasks: Valuation Data Completeness

Written before implementation (2026-08-30).

- [x] T001 [FR-001, DATA-001] `tools/market-data/vnstock-export/export_equity_profile.py`: fetch `Company(kbs).overview()` per symbol; emit `sharesOutstanding` (int) and `freeFloatRatio` (percent) when present, `qualityReason` null in that case; tolerate a failed overview (keep the old reason); bump tool version to `0.2.0`.
      Verify: new `tests/test_export_equity_profile.py` (record with shares; record without → reason kept).
      Evidence: `fetch_overview` / `share_fields` / `build_records(..., overview_lookup)`; `python -m pytest` 26/26 (2026-08-30). Overview calls paced by `--requests-per-minute` (default 30) with a final `Outstanding shares present for N/M` line.
- [x] T002 [FR-001] `EquityProfileImportPackageParser` reads the optional fields; `EquityProfileImportService` creates a new effective-dated revision when the current row has no shares or a different value (closing the old row, carrying `sector_reference_id`), `UPDATED` status; unchanged → `ALREADY_PRESENT`.
      Verify: `EquityProfileImportServiceTests` (create, revise, unchanged).
      Evidence: `EquityProfileEntity.closeAt`, parser reads `sharesOutstanding`/`freeFloatRatio`, `ProfileStatus.UPDATED`; `revisesTheCurrentProfileWhenOutstandingSharesArrive` — 5/5 green. Name-only changes never revise (kept `ALREADY_PRESENT`).
- [x] T003 [FR-002] `export_fundamentals.py`: map `earning_per_share_vnd` → `EPS`.
      Verify: exporter test.
      Evidence: `test_bank_eps_item_id_is_mapped_and_normalized_like_non_bank_eps` (4050730 → 4050.73).
- [x] T004 [FR-003, DATA-002] `FundamentalSummaryCalculator` → `fundamental-summary-v2`: TTM from latest annual when < 4 quarters (reason `ANNUAL_BASIS`); growth from latest vs prior annual when < 8 quarters; 8-quarter path unchanged.
      Verify: `FundamentalSummaryTests` (new cases) + updated `RULE_VERSION` expectations in `FundamentalReportServiceTests`.
      Evidence: `RULE_VERSION = fundamental-summary-v2`, `ANNUAL_BASIS`; tests `v2UsesTheLatestAnnualReportForTtm…`, `v2AnnualGrowthIsNotApplicable…`, `v2KeepsTheQuarterlyRule…` — 22/22; both test classes now reference the constant.
- [x] T005 [FR-004, NFR-001] Full gates; `docs/REMEDIATION_PLAN.md` entry Q-31 (valuation publish rate) recorded with the post-refresh measurement plan (SC-001..003 need the owner's refresh).
      Evidence: backend 662/662, exporter 26/26 (FE/AI untouched by this feature); `docs/REMEDIATION_PLAN.md` Q-31 + measurement SQL. SC-001..003 open until the owner runs `.
efresh-data.ps1 -FullRefresh`.

| Requirement | Tasks |
|---|---|
| FR-001, DATA-001 | T001, T002 |
| FR-002 | T003 |
| FR-003, DATA-002 | T004 |
| FR-004, NFR-001 | T005 |
