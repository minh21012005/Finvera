# Tasks: Provider Ingestion Normalization

Written before implementation (2026-08-30).

- [x] T001 [FR-001] `export_daily_bars.fetch_rows`: request `end + KBS_END_PADDING_DAYS (3)`, drop rows after `end`.
      Verify: `test_export_daily_bars` — provider stub receives padded end; rows after `end` dropped; 2026-08-28 kept for `end = 2026-08-30`.
      Evidence: `KBS_END_PADDING_DAYS = 3`, post-filter in `fetch_rows`; `test_fetch_rows_pads_the_provider_end_date_and_cuts_back_to_the_requested_end`. Same padding applied to `export_history.py` (equity + index) — `test_index_fetch_pads_the_provider_end_and_cuts_back`.
- [x] T002 [FR-002] `export_equity_profile`: remove `freeFloatRatio`; `share_fields` → `(shares, reason)` with charter-capital/par consistency check; importer keeps the record reason; parser stops reading `freeFloatRatio`.
      Verify: `test_export_equity_profile` (garbage free float ignored; consistent → reason None; inconsistent → UNVERIFIED); `EquityProfileImportServiceTests`.
      Evidence: `share_fields` → `(shares, reason)`; parser passes `null` free float; importer keeps `record.qualityReason()`; tests 5/5 + 4 exporter profile tests.
- [x] T003 [FR-003] `export_fundamentals`: period-scoped ratio rules (`RATIO_ANNUAL_ONLY_IDS`, `RATIO_QUARTER_ONLY_IDS`), trailing → `ROE`/`ROA` with rule id, dividend yield as reported, `TOOL_VERSION 0.6.0`.
      Verify: contract test vectors in `test_export_fundamentals`.
      Evidence: `RATIO_ANNUAL_ONLY_IDS`/`RATIO_QUARTER_ONLY_IDS`/`TRAILING_AS_ANNUALIZED`; `test_ratio_period_scope_contract_v2_vectors`, `test_dividend_yield_is_taken_as_reported_from_annual_columns_only`; TOOL_VERSION 0.6.0.
- [x] T004 [FR-004, FR-005] insurance/securities statement ids + (code, period) dedupe; FCF v2 id lists; contract `kbs-derivations-v1.md` amended with the v2 rule.
      Verify: BVH/SSI fixtures; bank yields no FCF.
      Evidence: `INCOME_STATEMENT_MAP` +4 ids, `(metricCode, column)` dedupe, `FCF_OCF_IDS`/`FCF_CAPEX_IDS`, `kbs-derivations-v1.md` amendment; `test_insurance_and_securities_statement_ids_are_mapped_and_deduped`, `test_free_cash_flow_v2_covers_securities_and_insurance_ids_but_not_banks`.
- [x] T005 [FR-006] `FundamentalSummaryCalculator`: `ANNUAL_ONLY_CODES` via annual fallback + `ANNUAL_BASIS`.
      Verify: `FundamentalSummaryTests` new case; existing cases green.
      Evidence: `ANNUAL_ONLY_CODES` + `addAnnualScopedMetric`; `annualOnlyRatiosFallBackToTheLatestAnnualReportWithAnnualBasis` — 23/23.
- [x] T006 [FR-007] `tests/provider_schema_fixture.json` + `test_provider_schema.py`.
      Verify: every mapped id exists in the fixture; concept coverage per company type.
      Evidence: fixture generated from the 2026-08-30 probe (7 company types, 10 symbols); 3 tests green.
- [x] T007 Full gates (exporter, backend, FE label tweak for ROE/ROA "12 tháng"); `docs/REMEDIATION_PLAN.md` Q-32..Q-36; Feature 010 research R-003 corrected (no free float).
      Evidence: exporter 34/34, backend 663/663, FE 129/129 + lint/build clean; Q-32..Q-36 in REMEDIATION_PLAN; 009 research R-004.1 marked withdrawn; FE labels "ROE 12 tháng".

| Requirement | Tasks |
|---|---|
| FR-001 | T001 |
| FR-002 | T002 |
| FR-003 | T003 |
| FR-004, FR-005 | T004 |
| FR-006 | T005 |
| FR-007 | T006 |
| DATA-001, NFR-001 | T003, T007 |

- [x] T008 [Q-57] `export_fundamentals.py` 0.7.0: `orient_statement_frames` mirrors yearly income/cash-flow labels (contract `kbs-yearly-statement-orientation-v1`), annual statement records carry `derivation=kbs-yearly-statement-labels-mirrored-v1`; FE dictionary wording added.
      Verify: `test_yearly_statement_labels_are_mirrored_and_ratio_left_alone`, `test_quarterly_frames_and_single_year_frames_are_never_relabelled` — exporter suite 43/43.
      Owner action: run the normal `.efresh-data.ps1` (the 0.7.0 toolVersion bump re-exports every symbol's fundamentals, ≈ 2.5 h), then `python tools/verification/history_basis_study.py` and `verify_calcs.py`.

