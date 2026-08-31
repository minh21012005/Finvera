# Tasks: Feature 020 — Sector Reference from VCI ICB

| ID | Task | Status | Evidence |
|---|---|---|---|
| T001 | Probe VCI listing frames; measure coverage/group sizes at L2 vs L3 over Finvera's universe | DONE 2026-08-31 | research R-001/R-002: 1,524/1,524 covered; L3 37 groups, 29 ≥ 8 |
| T002 | Spec, research, contract `vci-icb-sector-reference-v1`, ADR-0012 | DONE | this directory; docs/adr/0012 |
| T003 | Exporter `export_sector_reference_vci.py` (S-1…S-6, audit block, coverage floor) | DONE | live run: 1,522 equities, 36 sectors, 7 below floor, VNH duplicate reported |
| T004 | Trimmed fixtures + 5 anchored unit tests | DONE | `uv run --project ../provider-poc python -m pytest tests` → 63 passed |
| T005 | `refresh-data.ps1`: export VCI sector package in stage 1; messages; pinned-path warning | DONE | see script |
| T006 | `verify_calcs.py` `sector_coverage_check()` | DONE | pre-refresh run reports 697/1,524 (expected DIFF until the package is imported) |
| T007 | Owner refresh → import → verifier ≥ 95 % under `VCI_ICB_L3`; UI spot-check ACV/A32/MBB peers | PENDING owner | — |
| T008 | REMEDIATION_PLAN Q-38 → DONE with evidence after T007 | PENDING | — |
