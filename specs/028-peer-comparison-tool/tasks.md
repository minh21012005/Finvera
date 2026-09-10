# Tasks: AI Analyst Peer Comparison Tool (COMPARE)

**Input**: `specs/028-peer-comparison-tool/spec.md`, `specs/028-peer-comparison-tool/plan.md`  
**Goal**: Implement and verify the `COMPARE` peer comparison tool for AI Analyst across Spring Boot backend and AI orchestration.

---

## Phase 1: Backend Comparison Tool Endpoint (`finvera-be`)

- [x] T001 [FR-001, FR-002] Add comparison DTOs (`StockCompareRequest`, `StockComparisonToolResponse`, `StockComparisonItemDto`) in `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/dto/ToolResponseDtos.java` and implement `compareStocks(List<String> symbols)` in `ToolDelegateService.java`.
      Verify: `.\mvnw.cmd test-compile`
      Depends: none

- [x] T002 [FR-001, SEC-001] Add `POST /tools/stocks/compare` in `finvera-be/src/main/java/com/minhnb/finvera_be/analyst/controller/InternalToolController.java`.
      Verify: `.\mvnw.cmd test-compile`
      Depends: T001

- [x] T003 [FR-001, FR-002, SEC-001] Write integration test in `finvera-be/src/test/java/com/minhnb/finvera_be/analyst/StockCompareInternalToolTests.java` verifying authentication, validation, and multi-symbol JSON response.
      Verify: `.\mvnw.cmd test -Dtest=StockCompareInternalToolTests`
      Depends: T002

---

## Phase 2: AI Orchestration Tool Definition & Dispatch (`finvera-ai`)

- [x] T004 [FR-003] Add `ToolName.COMPARE` and `CompareToolArgs` schema in `finvera-ai/app/features/orchestration/allowlist.py`.
      Verify: `uv run python -c "from app.features.orchestration.allowlist import ToolName, ToolArgsSchemas; assert ToolName.COMPARE in ToolArgsSchemas"`
      Depends: none

- [x] T005 [FR-003, SEC-001] Implement `COMPARE` dispatch logic in `finvera-ai/app/features/orchestration/dispatch.py` calling `{base_url}/tools/stocks/compare`.
      Verify: `uv run pytest app/features/orchestration/tests/test_dispatch.py`
      Depends: T004

- [x] T006 [FR-004, FR-005, FR-006] Register `COMPARE` in `TOOL_DECLARATIONS`, update `plan_tools()` multi-symbol heuristic, and update `_offline_synthesize()` to render comparative tables and attribution claims in `finvera-ai/app/features/chat/service.py`.
      Verify: `uv run pytest app/features/chat/tests/test_feature015_modes_and_templates.py`
      Depends: T005

---

## Phase 3: Verification & Quality Gates

- [x] T007 [US1, FR-004, FR-005, DATA-001] Create comprehensive unit test in `finvera-ai/app/features/orchestration/tests/test_compare_tool.py` testing tool selection, validation, table synthesis, and attribution claims.
      Verify: `uv run pytest app/features/orchestration/tests/test_compare_tool.py`
      Depends: T006

- [x] T008 [Constitution Gate] Run full test suites across both services to verify zero regressions.
      Verify: `.\mvnw.cmd test` in `finvera-be` and `uv run pytest` in `finvera-ai`
      Depends: T003, T007
