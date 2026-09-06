# Tasks: Feature 025 — TTM window eligibility (`fundamental-summary-v3`)

| ID | Req | Task | Path | Done when |
|---|---|---|---|---|
| T001 | — | Spec, research (scope measured), contract, plan | `specs/025-fundamental-summary-ttm-eligibility/**` | Written; constitution check passed — **done 2026-09-06** |
| T002 | FR-001, FR-002, FR-006, NFR-001 | `quarterWindowEligible(window, annualReports)`: E-1 on fiscal-quarter indices, E-2 vs the newest annual `periodEnd`; used by both the 4- and 8-quarter windows | `.../stock/domain/fundamentals/FundamentalSummaryCalculator.java` | **done** — `quarterWindowEligible` (E-1 on fiscal-quarter indices, E-2 vs newest annual `periodEnd`), used by the 4- and 8-quarter windows |
| T003 | FR-003, FR-004, FR-005, DATA-002 | Rejection outcomes: annual fallback with `ANNUAL_BASIS`; `MISSING` + `QUARTER_WINDOW_INELIGIBLE` when no annual; summary-level reason; `RULE_VERSION = fundamental-summary-v3` | same file | **done** — annual fallback + `QUARTER_WINDOW_INELIGIBLE`; withheld aggregates when no annual; `RULE_VERSION = fundamental-summary-v3` |
| T004 | SC-1, DATA-001 | Eight contract vectors + the v2-parity vector | `finvera-be/src/test/java/.../fundamentals/FundamentalSummaryTests.java` | **done** — `FundamentalSummaryTests` **34/34** (27 pre-existing unchanged + 7 new: parity, E-2 stale, E-1 gap, no-annual withhold, trailing-EPS survives, growth window, snapshots untouched) |
| T005 | SC-4 | Verifier: independent E-1/E-2, target v3 | `tools/verification/verify_calcs.py` | **code done** — independent E-1/E-2 in `summary_v2`, targets v3 rows; green run recorded after the owner's refresh |
| T006 | — | Sector study reads v3 summaries | `tools/verification/sector_basis_study.py` | **done** — reads v3 summaries; marker kept as the regression signal |
| T007 | FR-005 | FE wording for `QUARTER_WINDOW_INELIGIBLE` + inventory test | `finvera-fe/src/shared/format/reason-codes.ts`, `.test.ts` | **done** — wording + inventory; vitest **158/158** |
| T008 | SC-2 | Backend suite; AI suite | `finvera-be`, `finvera-ai` | **done** — backend **720/720, BUILD SUCCESS** (Docker up; a first run showed 20 Testcontainers errors purely because Docker Desktop was stopped); AI **152 passed, 1 skipped** |
| T009 | SC-3, SC-5 | Post-refresh: re-run the scope probe, close Q-60 with numbers | research.md, `docs/REMEDIATION_PLAN.md` | Owner runs `.\refresh-data.ps1`; zero flagged windows recorded |
