# Tasks: Feature 021 — Single Provider (VCI)

| ID | Task | Status | Evidence |
|---|---|---|---|
| T001 | Probes: depth, indices (UPCOMINDEX), overview `issue_share`, 1-year close cross-check, buffer-before-start quirk | DONE 2026-08-31 | research.md R-001…R-004 |
| T002 | ADR-0013 accepted; spec/plan/tasks | DONE | docs/adr/0013, this dir |
| T003 | Switch `export_daily_bars` / `export_history` / `export_instrument_reference` / `export_equity_profile` to VCI (1.0.0 each; PROVIDER_ADJUSTED; range clamps; venue map; issue_share cross-check) | DONE | patch + suite |
| T004 | Exporter tests updated for VCI semantics | DONE | pytest green |
| T005 | Backend: SOURCE_PREFERENCE ranks VNSTOCK_VCI first (chart, technicals, valuation) | DONE | Maven tests |
| T006 | Backend: KBS bar retirement where a current VCI bar covers the same (instrument, date); EN-name preservation in profile import | DONE | Maven tests |
| T007 | `verify_calcs.py` cross-check direction flip (stored=VCI, reference=KBS) | DONE | script |
| T008 | Owner re-crawl (bars + profiles + market overview + fundamentals 1.1.0 in one pass) | PENDING owner | — |
| T009 | Post-crawl verification: verify_calcs fully green, UI spot-check, evidence rows (Q-57, Q-38, P2-10) | PENDING | — |
| T010 | Audit finding R-006: drop VCI no-trade filler bars (volume ≤ 0) in export_daily_bars | DONE 2026-08-31 | exporter suite 75/75 |
| T011 | Audit finding R-007: delisting propagation (exporter DELISTED records; importer revises on status change, carries shares forward) | DONE 2026-08-31 | exporter suite + EquityProfileImportServiceTests |
| T012 | Post-refactor audit: live package↔DB comparison for bars/indices/instruments/profiles/fundamentals (R-008) | DONE 2026-08-31 | research R-008 |

