# Tasks: Feature 013 — TCBS Thesis Live-Path Field Audit

Dependency order. `[P]` marks tasks with no shared file and no unmet prerequisite.

| ID | Req | Task | Path | Done when |
|---|---|---|---|---|
| T001 | DATA-001 | Code-vs-contract pass over mapper, live services, repository exclusions | `finvera-be/src/main/java/.../market/**` (read-only) | research R-001…R-003 recorded — **done 2026-08-31** |
| T002 | SEC-001, DATA-002 | Build the read-only live capture tool | `tools/verification/tcbs_live_capture.py` | Runs from the provider-poc env; token exchange preflighted; no secret in stdout or output — **done 2026-09-04** |
| T003 | NFR-001 | Determine the transport rules the server enforces | same file | 1002-on-RFC-ping identified and encoded (`ping_interval=None`); reconnect keeps a partial capture — **done 2026-09-04** |
| T004 | SC-1, DATA-002 | Owner-run capture during an open session (VNM/MBB/ACV + 4 indexes, ≥60 s) | `tools/verification/out/tcbs_live_capture_2026-09-04T1106.json` | Authenticated, 0 disconnects, 55 `s|6` + 52 `s|8` — **done 2026-09-04 11:06 ICT** |
| T005 | DATA-002, SC-2 | Compare every captured price/level with the VCI anchor | same evidence file | 6/6 instruments SAME_UNIT, max deviation 1.6e-5 — **done** |
| T006 | DATA-005 | Capture the `tickerCommons` REST snapshot and compare its price fields with the same anchor | `--rest-only` run, evidence file | 21/21 price fields SAME_UNIT vs VCI; ceil/floor sit on the venue band — **done 2026-09-04 14:49 ICT** |
| T007 | DATA-001, DATA-003, DATA-004, NFR-001 | Amend the contract: field tables (unit/type/semantics) for `s|4`/`s|6`/`s|8`/`tickerCommons`, control frames, RFC-ping clause, reference-price chain, mid-session `s|4` absence | `specs/001-market-overview/contracts/tcbs-thesis-live-overlay.md` | Field tables for `s|6`/`s|8`/`tickerCommons`, control frames, RFC-ping clause, reference chain — **done** (amended in place, version unchanged) |
| T008 | DATA-006 | Pin the fixture from real captured frames | `finvera-be/src/test/resources/fixtures/market/tcbs/tcbs-frame-fixture.json` | 8 verbatim frames + the audited `tickerCommons` response; no `s|4` captured, recorded honestly — **done** |
| T009 | DATA-006, SC-4 | Fixture test through the production mapper | `finvera-be/src/test/java/com/minhnb/finvera_be/market/provider/tcbs/TcbsThesisFrameFixtureTests.java` | 7/7 green: unit magnitude, mixed typing, index mapping, derived reference, absent `change`, REST band identity — **done** |
| T010 | DATA-007, SC-5 | Live-vs-EOD reconciliation for 3 symbols | research.md | VNM/MBB exact on price **and** volume; ACV +0.24 % explained by the UPCoM 15:00 close (research R-011) — **done** |
| T011 | SC-4 | Backend suite | `finvera-be` | **705/705 green, BUILD SUCCESS** (2026-09-04) |
| T012 | SC-6 | Close P2-01 with evidence; changelog row | `docs/REMEDIATION_PLAN.md` | Status line names both evidence files and the new contract clauses — **done** |
| T013 | — | Record the audit outcome in research.md (R-004…R-009) | `specs/013-tcbs-live-field-audit/research.md` | R-004…R-011 recorded with observed values and evidence files — **done** |
