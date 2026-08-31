# Tasks: AI Analyst end-to-end on real data

Written 2026-08-31 after the first capture (research.md) and before the fixes.

- [x] T001 [FR-001, FR-002, FR-003, FR-005] `tools/verification/analyst_e2e.py`: ten questions, SSE capture, direct tool re-fetch, number fidelity (vi-VN + en-US readings, date tokens masked), basis-disclosure check, JSON recording + markdown table.
      Evidence: first run 16:28 (research.md R-001) exposed Q-49…Q-52; parser false positives fixed (`readings()`), verified on the earlier tokens.
- [x] T002 [Q-49] Backend: `ToolDelegateService` writable transaction for technical/fundamentals/valuation; `ProblemDetailsAdvice` catch-all 500 + `ResponseStatusException` passthrough; `synthesisMode`/`plannerMode` passthrough; OpenAPI (internal + public) amended.
      Verify: `ToolDelegateServiceTests.materialisingToolsRunInAWritableTransaction`, `ProblemDetailsAdviceTests` (2), `AnalystControllerTests`, `InternalToolControllerTests` — green (mvn exit 0).
- [x] T003 [Q-50, Q-51, Q-52] finvera-ai: quota-aware retry, offline templates with verifiable claims for every tool, degraded-mode disclosure + modes, list-aware `get_nested_value`, synthesis rules 8–10, vi-VN formatting.
      Verify: `test_feature015_modes_and_templates.py` (5) + suite 95/95.
- [x] T004 FE: `synthesisMode`/`plannerMode` type; "[Chế độ suy giảm]" notice; test.
      Verify: vitest 146/146, eslint 0, tsc 0, build OK.
- [x] T005 [SC-001, SC-002] Re-run the capture on the restarted stack; every question: 0 unsupported numbers, 0 basis misses (or explained), no refusal after a successful tool.
      Evidence: research R-002…R-006 — six captures; runs 5 (online) and 6: 10/10 answered, 0 refusals, 0 basis misses, 0 unsupported after parser fixes; found and fixed Q-53, Q-54, Q-55, Q-56 on the way.
- [x] T006 [SC-003, FR-004] Golden fixture `app/features/chat/tests/golden/analyst_e2e_2026-08-31.json` + `test_golden_e2e.py` (attribution replay, offline synthesis on recorded responses, fabrication guard on recorded answers, planner fallback coverage).
      Evidence: 40 parametrised golden tests (10 questions × 4) on the R-006 recording; `fabricated_numbers` taught count words ("12 tháng", "8 quý"); AI suite 136 passed / 1 skipped.
- [x] T007 [SC-004] Backend full suite; `docs/REMEDIATION_PLAN.md` Q-49…Q-52 rows, P2-03 → DONE with the per-question table; research.md R-002 (post-fix run).
      Evidence: backend 683/683 (after Q-53/54/55), FE 146/146, AI 136+1; REMEDIATION Q-49…Q-56, P2-03 DONE; research R-001…R-006.

| Requirement | Tasks |
|---|---|
| FR-001, FR-002, FR-003, FR-005 | T001, T005 |
| FR-004 | T006 |
| SC-001, SC-002 | T005 |
| SC-003 | T006 |
| SC-004 | T007 |
