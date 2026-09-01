# Tasks: Fundamental statements from VCI

Written 2026-08-31 before implementation.

- [x] T001 [FR-007 prereq] `provider-poc`: vnstock 4.0.6 → 4.0.7; existing exporter suite (43) green; KBS bars/profile paths unaffected.
      Evidence: `uv add vnstock==4.0.7`; 43/43; KBS `fetch_rows('VNM', 2026-08-20..28)` returns 8 bars, close 62.3 = DB.
- [x] T002 [FR-001, FR-002, FR-003, FR-004, FR-006] `export_fundamentals_vci.py` + `tests/fixtures/vci/{VNM,MBB,BVH,SSI}.json` + `tests/test_export_fundamentals_vci.py` (anchor set, Σ quarters = FY, derivations, first-occurrence, zero-EPS drop, company-type detection).
      Evidence: 12 new tests, exporter suite 55/55; derived TRAILING_EPS reproduces KBS's yearly trailing EPS to the cent (VNM 4,502.58 / 4,494.02 / 4,074.74), FY2025 = Σ quarters to the VND (VNM, MBB), shares = profile (VNM exact, BVH exact, MBB within provider rounding); live export VNM: 145 quarter / 144 annual records.
- [x] T003 [FR-007] `export_all_symbols.py`: VCI fundamentals pass (year + quarter), checkpoint keys, calls-per-dataset, staleness; tests updated.
      Evidence: fundamentals pass delegates to `export_fundamentals_vci.export_symbol` (same file names → import stage unchanged); `FUNDAMENTALS_TOOL_VERSION = 1.0.0` forces the full re-export; suite 55/55.
- [x] T004 [FR-005] Backend: repository finder + `SOURCE_SUPERSEDED` rule in `StockIngestionService.ingestFundamentalReport`; `StockIngestionServiceTests` case; full suite.
      Evidence: `aNewerSourceSupersedesTheOlderSourcesCurrentReportForTheSamePeriod` (VCI over KBS → CORRECTED rev 2, `SOURCE_SUPERSEDED`; same-source older observation still OUT_OF_ORDER); targeted suites green.
- [x] T005 [US-4] FE dictionary + contract v1 rows for `vci-*` rule ids and `SOURCE_SUPERSEDED`; vitest.
      Evidence: 12 rule ids worded; dictionary two-way test green; FE 146/146, eslint 0.
- [x] T006 Docs: `docs/REMEDIATION_PLAN.md` Q-57 status + owner runbook line; `tools/verification/verify_calcs.py` provider check -> anchors (audited values, share anchors, quarter-sum diagnostics); README.
      Evidence: `verify_calcs.py` gained `anchors_check()` -- audited FY figures for VNM/MBB/BVH/SSI, VNM/MBB anchor quarter sums, share anchors from `EQUITY_ATTRIBUTABLE_TO_PARENT / BVPS`, FY2025 quarter-sum diagnostics, no current `VNSTOCK_KBS` rows; run before the re-crawl it reports exactly the KBS defect (16 diffs: mirrored years, 0/3 quarter sums, 11,152 KBS rows) -- the acceptance gate for T007.
- [x] T008 [SC-003] Source retirement is part of the **default** fundamentals import: `FundamentalSourceRetirementService.retireAllExcept(primary-source)` runs at the end of `stockFundamentalsImport` (`finvera.stock.fundamentals.primary-source = VNSTOCK_VCI`), stage 6 of `refresh-data.ps1` waits for its log line; no owner flag (the explicit `retire.enabled/source` runner stays as a recovery knob). `NoStatementsAvailable` named failure in the exporter (A32/ACE/BCP: VCI has annual but no quarterly statements).
      Evidence: `retiringASourceMarksItsCurrentReportsNotCurrentWithoutDeletingThem`, `retireAllExceptKeepsThePrimarySourceAndIsIdempotent`; ingestion suites green; script parses, switch removed. Crawl: `failed:NoStatementsAvailable` is re-checked after 35 days (`fundamentals_checked_at`), tests `test_provider_unavailable_statements_are_rechecked_after_the_window`; exporter suite 56/56.
- [ ] T007 [SC-001…SC-004] Owner re-crawl + re-import; verify (anchors, quarter-sum query, KBS rows retired, suites, AI e2e); record evidence; Q-57 → DONE; Feature 017 decision re-measured.

| Requirement | Tasks |
|---|---|
| FR-001–FR-004, FR-006 | T002 |
| FR-005 | T004 |
| FR-007 | T001, T003 |
| US-4 | T005 |
| SC-001–SC-004 | T006, T007 |
