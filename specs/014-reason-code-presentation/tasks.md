# Tasks: Reason-code presentation

Written before implementation (2026-08-31).

- [x] T001 [FR-001, FR-002, FR-003, FR-005] `finvera-fe/src/shared/format/reason-codes.ts` + `shared/components/reason-codes.tsx`: dictionary (contract v1), `reasonCodeLabel`, `describeReasonCodes`, `applicabilityNote`, `dataStatusLabel` with fallback, `<ReasonCodes>` / `<ReasonCode>` retaining the code as `data-reason-code` + `title`.
      Verify: `shared/format/reason-codes.test.ts` — every inventory code (research R-002) resolves to wording ≠ code; unknown → raw code; empty list → fallback sentence; note wording.
      Evidence: 114 codes in dictionary = inventory (two-way check); 7/7 module tests.
- [x] T002 [FR-004, FR-006] Route `stock-detail` through the module: `stock-format.ts` (`dataStatusLabel` re-export, `applicabilityReasonLabel` → `applicabilityNote`), `explain-evidence.ts` (all engine notes, NOT_APPLICABLE wording), `stock-{overview,technical,fundamentals,valuation,signals,chart}.tsx`.
      Verify: `technical.test.tsx`, `risk-detail.test.tsx`, `signals.test.tsx`, `fundamentals-valuation.test.tsx`, `overview-chart.test.tsx`, `explain-evidence.test.ts` — assertions on wording + `data-reason-code`.
- [x] T003 [FR-004, FR-005] `market-overview`: drop the four `statusLabel` copies; unavailability notices, quality notes and the disclaimer via the module.
      Verify: `index-overview.test.tsx`, `breadth-overview.test.tsx`, `regime-overview.test.tsx`, `market-overview-page.test.tsx` (unknown code `RENORMALIZED_MISSING_VOLATILITY` still visible as itself).
- [x] T004 [FR-004] `stock-screener/components/screener-results.tsx`, `portfolio/components/{holdings-table,portfolio-list}.tsx`: wording for status token and every reason code.
      Verify: `screener.test.tsx`, `holdings.test.tsx`.
- [x] T005 [SC-003, SC-004] `npm test`, `npm run lint`, `npm run build`; grep `src/features/**/*.tsx` (non-test) for `reasonCodes.join(` and `(${…reasonCode})` → none.
      Evidence: vitest 145/145 (30 files), eslint 0, tsc 0, vite build OK; SC-003 grep empty; 9 component tests moved from raw-code text to wording + `title`/`data-reason-code`.
- [x] T006 [FR-007] `finvera-ai/app/features/chat/service.py`: offline valuation-withheld sentence uses the five-entry Vietnamese map; unknown → raw code. Test in `tests/test_ask_orchestration.py`.
      Verify: `uv run pytest`.
      Evidence: `test_offline_valuation_withheld_sentence_words_reason_codes`; 90/90.
- [x] T007 `docs/REMEDIATION_PLAN.md`: P2-02 → DONE with evidence; changelog.
      Evidence: Phase 2 table row + changelog 2026-08-31.

| Requirement | Tasks |
|---|---|
| FR-001, FR-002, FR-003, FR-005 | T001 |
| FR-004 | T002, T003, T004 |
| FR-006 | T002 |
| FR-007 | T006 |
| SC-001, SC-002 | T001 |
| SC-003, SC-004 | T005 |
