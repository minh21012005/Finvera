# Tasks: Feature 019 — Derived Fundamental Ratios

| ID | Task | Status | Evidence |
|---|---|---|---|
| T001 | Research: id inventory per company type, signs, unit conventions, anchor magnitudes | DONE 2026-08-31 | research.md R-001…R-005 |
| T002 | Contract `vci-derived-ratios-v1` (formulas, rule ids, presence matrix, anchors) | DONE | contracts/ |
| T003 | Fixture extension (VNM/MBB/BVH/SSI, lines from the 2026-08-31 capture) | DONE | tests/fixtures/vci/*.json |
| T004 | `derive_ratios()` in `export_fundamentals_vci.py`; TOOL_VERSION 1.1.0 | DONE | exporter |
| T005 | Tests: exact anchors, presence/absence matrix, TTM break, `-end` fallback | DONE | pytest suite green |
| T006 | FE `reason-codes.ts` rule ids + inventory test | DONE | vitest green |
| T007 | Docs: REMEDIATION_PLAN changelog, P2-09 follow-up row | DONE | REMEDIATION_PLAN.md |
| T008 | Owner: next full crawl re-exports under 1.1.0; spot-check summary coverage (SC-2) | PENDING owner | — |
