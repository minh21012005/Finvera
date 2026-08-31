# Tasks: Feature 022 — Price-Based Metrics + History Depth

| ID | Task | Status | Evidence |
|---|---|---|---|
| T001 | Trace the dormant DIVIDEND_YIELD chain (summary DPS_TTM → valuation yield) and the missing input | DONE 2026-08-31 | spec Problem section |
| T002 | Exporter 1.2.0: `DIVIDEND_PER_SHARE` derivation + contract row + tests (all 4 company types) | DONE | exporter suite 76/76 |
| T003 | ValuationV1: PS informational metric + Inputs/Builder/ComputedMetrics + reasons | DONE | ValuationV1Tests 27/27 |
| T004 | ValuationService: REVENUE_TTM input on subject + persisted-peers paths; V017 admits 'PS' | DONE | ValuationService(SectorBasis)Tests green |
| T005 | FE: ValuationMetricCode + PS label + 3 reason codes + DPS rule id; inventory test | DONE | vitest 50/50 |
| T006 | History start 2023-01-01 → 2019-01-01 in refresh-data.ps1 (full-cycle own-history basis) | DONE | script parses; rationale in spec |
| T007 | `verify_calcs.py` recomputes PS + DIVIDEND_YIELD | DONE | patch |
| T008 | Contract docs: valuation-v2 addendum; vci-derived-ratios-v1 DPS row; BETA deferral recorded | DONE | contracts |
| T009 | Post-crawl: SC-4 spot-check + verifier green | PENDING owner crawl | — |
