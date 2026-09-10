# Tasks: AI Analyst Discovery & Multi-Archetype Screening Expansion

**Input**: `specs/027-analyst-discovery-expansion/spec.md`, `specs/027-analyst-discovery-expansion/plan.md`  
**Goal**: Implement and verify `STRATEGY_SCAN` tool and Archetype-based screener mapping for AI Analyst.

---

## Phase 1: Backend Strategy Scan Tool Endpoint (`finvera-be`)

- [x] T001 [FR-001, FR-002, SEC-001] Add `POST /tools/strategies/scan` in `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/controller/InternalToolController.java` and `ToolDelegateService.java` calling `StrategyScanService`.
      Verify: `.\mvnw.cmd test-compile`
      Depends: none

- [x] T002 [FR-001, FR-002, SEC-001] Write integration test in `finvera-be/src/test/java/com/minhnb/finvera_be/analyst/StrategyScanInternalToolTests.java` verifying authentication, parameter handling, and `ScanResponse` JSON shape.
      Verify: `.\mvnw.cmd test -Dtest=StrategyScanInternalToolTests`
      Depends: T001

---

## Phase 2: AI Orchestration Tool Definition & Dispatch (`finvera-ai`)

- [x] T003 [FR-003] Add `ToolName.STRATEGY_SCAN` and `StrategyScanToolArgs` schema in `finvera-ai/app/features/orchestration/allowlist.py`.
      Verify: `uv run python -c "from app.features.orchestration.allowlist import ToolName, ToolArgsSchemas; assert ToolName.STRATEGY_SCAN in ToolArgsSchemas"`
      Depends: none

- [x] T004 [FR-003, SEC-001] Implement `STRATEGY_SCAN` dispatch logic in `finvera-ai/app/features/orchestration/dispatch.py` calling `{base_url}/tools/strategies/scan`.
      Verify: `uv run pytest app/features/orchestration/tests/test_dispatch.py`
      Depends: T003

- [x] T005 [FR-003, FR-004] Register `STRATEGY_SCAN` in `TOOL_DECLARATIONS`, update `plan_tools()` keyword heuristic, and update `_offline_synthesize()` in `finvera-ai/app/features/chat/service.py`.
      Verify: `uv run pytest app/features/chat/tests/test_feature015_modes_and_templates.py`
      Depends: T004

---

## Phase 3: Screener Multi-Archetype Conversion (`finvera-ai`)

- [x] T006 [FR-005] Upgrade `_rule_based_extract()` and `CONVERSION_SYSTEM_PROMPT` in `finvera-ai/app/features/orchestration/screener_conversion.py` with Growth, Value, Dividend, and Momentum archetype definitions.
      Verify: `uv run pytest app/features/orchestration/tests/test_screener_conversion.py`
      Depends: none

- [x] T007 [FR-005] Add comprehensive unit tests in `finvera-ai/app/features/orchestration/tests/test_screener_conversion.py` covering archetype keywords ("dài hạn", "tăng trưởng", "giá trị", "cổ tức", "lướt sóng").
      Verify: `uv run pytest app/features/orchestration/tests/test_screener_conversion.py`
      Depends: T006

---

## Phase 4: End-to-End Orchestration & Verification

- [x] T008 [US1, FR-003, FR-004, DATA-001, DATA-002] Create unit test in `finvera-ai/app/features/orchestration/tests/test_strategy_scan_tool.py` testing tool execution, offline synthesis, and attribution verification for `STRATEGY_SCAN`.
      Verify: `uv run pytest app/features/orchestration/tests/test_strategy_scan_tool.py`
      Depends: T005

- [x] T009 [Constitution Gate] Run full test suites across both services to verify zero regressions.
      Verify: `.\mvnw.cmd test` in `finvera-be` and `uv run pytest` in `finvera-ai`
      Depends: T002, T007, T008
