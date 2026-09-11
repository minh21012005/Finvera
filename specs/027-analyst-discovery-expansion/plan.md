# Implementation Plan: AI Analyst Discovery & Multi-Archetype Screening Expansion

**Feature**: `027-analyst-discovery-expansion` · 2026-09-10 · **Spec**: `spec.md`  
**Contracts touched**:
- `specs/007-ai-analyst/contracts/internal-api.openapi.yaml` (additive: `/tools/strategies/scan` endpoint & `STRATEGY_SCAN` tool definition)

## Summary

This plan connects AI Analyst to the backend's deterministic `StrategyScanService` via an internal tool endpoint, enabling real-time scanning of quantitative setups (Momentum, Breakout, Pullback, RSI Oversold bounce, etc.) with deterministic entry, stop-loss, and take-profit levels. Additionally, it upgrades `screener_conversion.py` with multi-archetype quantitative filter templates (Growth, Value, Dividend, Momentum) so that open-ended style questions return meaningful results rather than empty `{}` filters.

## Constitution Check

| Principle | Assessment |
|---|---|
| **Principle I: Financial Integrity** | All strategy calculations, price levels (entry, stop, target), and risk scores are executed by the existing deterministic `StrategySignalV1` engine in Java; the LLM merely retrieves and explains these numbers without fabricating calculations. ✔ |
| **Principle II: AI Transparency & Calibrated Language** | The Analyst communicates recommendations as conditional setups (*"Nhà đầu tư có thể cân nhắc"*, *"Vùng hỗ trợ cắt lỗ"*), citing strategy code, signal strength, and risk level, never promising returns or issuing buy/sell mandates. ✔ |
| **Principle III: Modular Monolith** | Reuses existing `StrategyScanService` in `finvera-be` without adding microservices or external message queues. ✔ |
| **Principle IV: Security by Default** | Internal endpoints authenticate via `X-Internal-Api-Key` and enforce owner identity checks. ✔ |
| **Principle V: Spec-Driven Discipline** | Changes are bounded strictly to `027-analyst-discovery-expansion` with unambiguous requirements (FR-001 … FR-006). ✔ |
| **Principle VI: Verification Quality Gates** | Spring Boot controller & service tests, Python unit and orchestration tests. ✔ |

---

## Architectural Changes

### 1. Spring Boot Backend (`finvera-be`)

- **`InternalToolController.java`**: Add endpoint:
  ```java
  @PostMapping("/tools/strategies/scan")
  public ResponseEntity<ScanResponse> scanStrategy(
          @RequestParam(name = "ownerId", required = false) UUID ownerId,
          @RequestParam("strategyCode") StrategyCode strategyCode,
          @RequestParam(name = "limit", defaultValue = "5") int limit)
  ```
- **`ToolDelegateService.java`**: Delegate call to `strategyScanService.scan(strategyCode, limit, 0)`.

### 2. AI & Orchestration Service (`finvera-ai`)

- **`allowlist.py`**:
  - Add `ToolName.STRATEGY_SCAN = "STRATEGY_SCAN"`.
  - Add `StrategyScanToolArgs(BaseModel)` with `strategyCode: str` and `limit: Optional[int] = 5`.
- **`dispatch.py`**:
  - Add dispatch branch calling `POST {self.base_url}/tools/strategies/scan` with query params `strategyCode` and `limit`.
- **`service.py`**:
  - Register `STRATEGY_SCAN` in `TOOL_DECLARATIONS` with parameter `strategyCode` enum (`MOMENTUM`, `BREAKOUT`, `TREND_FOLLOWING`, `PULLBACK`, `RSI_BASED`, `MACD_BASED`, `MA_CROSSOVER`, `MEAN_REVERSION`).
  - Update `plan_tools()` keyword heuristic to detect terms: *"tín hiệu"*, *"trading"*, *"lướt sóng"*, *"ngắn hạn"*, *"breakout"*, *"vượt đỉnh"*, *"bắt đáy"*, *"mua mới"*, *"chiến lược"*.
  - Update `_offline_synthesize()` to support `STRATEGY_SCAN` template formatting and structured claims.
- **`screener_conversion.py`**:
  - Enhance `_rule_based_extract()` and `CONVERSION_SYSTEM_PROMPT` to recognize:
    - **Growth archetype**: `tăng trưởng`, `growth` -> revenue growth >= 10%, EPS growth >= 10%, ROE >= 15%.
    - **Long-term quality archetype**: `dài hạn`, `tích sản`, `nắm giữ` -> revenue growth >= 5%, EPS growth >= 5%, ROE >= 15%, market cap >= 1,000B VND.
    - **Value archetype**: `giá trị`, `định giá hấp dẫn` -> published `UNDER_VALUED`, ROE >= 12%, market cap >= 1,000B VND.
    - **Dividend archetype**: `cổ tức`, `cổ tức cao`, `tiền mặt` -> dividend yield >= 3%, defined positive P/E, market cap >= 2,000B VND.
    - **Momentum screener**: `maRelationship: ["PRICE_ABOVE_MA20"]`, `rsiMin: "50"`, `rsiMax: "68"`, `relativeVolumeMin: "1.2"`.
  - Explicit metric constraints in the user's query take precedence over both lower and upper preset bounds for that metric, preventing contradictory filters.
  - Extend `ScreenRequest`/`ScreenerV1` additively with `valuationClassification` and `dividendYieldMin/Max`.
  - Keep `DEBT_TO_EQUITY` as percent points and normalize the VCI `debt/equity` derivation by multiplying by 100 before persistence.

---

## Verification Plan

1. **Backend Integration Test**:
   - `StrategyScanInternalToolTests`: Test `POST /tools/strategies/scan` returns 200 with valid `ScanResponse` containing matches and signal levels.
2. **AI Service Pytest**:
   - `test_screener_conversion.py`: Test rule-based extraction for Growth, Value, Dividend, and Momentum archetypes.
   - `test_strategy_scan_dispatch.py`: Test `STRATEGY_SCAN` tool dispatch, offline synthesis, and attribution verification.
   - Full test suite: `uv run pytest`.
