# Implementation Plan: AI Analyst Peer Comparison Tool (COMPARE)

**Feature**: `028-peer-comparison-tool` · 2026-09-10 · **Spec**: `spec.md`  
**Contracts touched**:
- `specs/007-ai-analyst/contracts/internal-api.openapi.yaml` (additive: `/tools/stocks/compare` endpoint & `COMPARE` tool definition)

## Summary

This plan implements a dedicated comparative tool `COMPARE` in Finvera, enabling the AI Analyst to accept queries comparing 2 to 5 stock ticker symbols (e.g. *"So sánh SSI và VND"*, *"Giữa HPG, HSG và NKG mã nào đang có định giá và tăng trưởng tốt hơn?"*). The backend consolidates market overview, valuation assessment, fundamental metrics, and technical signals into a single unified comparative response. Finvera AI orchestrates the call, formats side-by-side comparative Markdown tables, and provides balanced, non-prescriptive trade-off analysis strictly backed by deterministic underlying facts.

---

## Constitution Check

| Principle | Assessment |
|---|---|
| **Principle I: Financial Integrity** | All financial figures (P/E, P/B, ROE, EPS, RSI, revenue growth) come deterministically from the existing domain services (`ValuationService`, `FundamentalReportService`, `TechnicalIndicatorService`, `StockOverviewService`). No numbers are fabricated by the LLM. ✔ |
| **Principle II: AI Transparency & Calibrated Language** | Comparative synthesis frames differences as trade-offs (*"Mã A có định giá P/E hấp dẫn hơn nhưng đòn bẩy cao hơn mã B"*), citing concrete metrics without issuing buy/sell mandates or guaranteeing returns. ✔ |
| **Principle III: Modular Monolith** | Reuses existing modular domain services in `finvera-be` through `ToolDelegateService` without adding new microservices or messaging queues. ✔ |
| **Principle IV: Security by Default** | The `/tools/stocks/compare` endpoint requires `X-Internal-Api-Key` and enforces caller `ownerId` validation. ✔ |
| **Principle V: Spec-Driven Discipline** | Work is tracked under `specs/028-peer-comparison-tool` with unambiguous functional requirements FR-001 through FR-006. ✔ |
| **Principle VI: Verification Quality Gates** | Unit tests in `finvera-be` (`StockCompareInternalToolTests`) and `finvera-ai` (`test_compare_tool.py`) alongside full test suite execution. ✔ |

---

## Architectural Changes

### 1. Spring Boot Backend (`finvera-be`)

#### DTOs & Models:
- In `com.minhnb.finvera_be.analyst.dto.ToolResponseDtos`:
  - Add `StockCompareRequest`:
    ```java
    public record StockCompareRequest(List<String> symbols) {}
    ```
  - Add `StockComparisonToolResponse`:
    ```java
    public record StockComparisonToolResponse(
        List<StockComparisonItemDto> items,
        Instant asOf
    ) {}
    ```
  - Add `StockComparisonItemDto`:
    ```java
    public record StockComparisonItemDto(
        String symbol,
        String companyName,
        String exchange,
        String price,
        String changePercent,
        Long volume,
        String marketCap,
        String pe,
        String pb,
        String valuationClassification,
        String valuationScore,
        String roe,
        String roa,
        String eps,
        String epsTtm,
        String revenueGrowthPercent,
        String epsGrowthPercent,
        String rsi14,
        String trend,
        String primarySignal,
        String riskLevel,
        String dataStatus,
        List<String> reasonCodes
    ) {}
    ```

#### Service Layer (`ToolDelegateService.java`):
- Add method `compareStocks(List<String> rawSymbols)`:
  - Normalize and deduplicate symbols, clamp between 2 and 5 symbols.
  - For each symbol, retrieve:
    - `StockOverview` (via `stockOverviewService`)
    - `FundamentalsToolResponse` (via `getFundamentals`)
    - `ValuationToolResponse` (via `getValuation`)
    - `TechnicalToolResponse` (via `getTechnical`)
  - Assemble into `StockComparisonItemDto` and return `StockComparisonToolResponse`.

#### Controller Layer (`InternalToolController.java`):
- Add endpoint:
  ```java
  @PostMapping("/stocks/compare")
  public ResponseEntity<StockComparisonToolResponse> compareStocks(
          @RequestParam(name = "ownerId", required = false) UUID ownerId,
          @RequestBody StockCompareRequest request) {
      requireOwner(ownerId);
      return ResponseEntity.ok(toolDelegateService.compareStocks(request != null ? request.symbols() : List.of()));
  }
  ```

---

### 2. AI & Orchestration Service (`finvera-ai`)

#### Schema & Allowlist (`allowlist.py`):
- Add `ToolName.COMPARE = "COMPARE"` to `ToolName`.
- Add `CompareToolArgs(BaseModel)`:
  ```python
  class CompareToolArgs(BaseModel):
      owner_id: uuid.UUID
      symbols: List[str] = Field(..., min_length=2, max_length=5)

      @field_validator("symbols")
      @classmethod
      def normalize_symbols(cls, v: List[str]) -> List[str]:
          cleaned = [_normalize_symbol(s) for s in v if s and s.strip()]
          unique = list(dict.fromkeys(cleaned))
          if len(unique) < 2:
              raise ValueError("Comparison requires at least 2 distinct symbols")
          return unique[:5]
  ```
- Register `ToolName.COMPARE: CompareToolArgs` in `TOOL_ARG_SCHEMAS`.

#### Dispatcher (`dispatch.py`):
- Handle `ToolName.COMPARE`:
  ```python
  elif tool_name == ToolName.COMPARE:
      resp = await client.post(
          f"{self.base_url}/tools/stocks/compare",
          params=params,
          json={"symbols": arguments.get("symbols", [])},
          headers=headers,
      )
  ```

#### Chat Service & Prompt Heuristics (`service.py`):
- Register `COMPARE` in `TOOL_DECLARATIONS` with parameter `symbols` (array of strings).
- In `plan_tools()`:
  - Extract all candidate ticker symbols from the query (e.g. `tickers = ["SSI", "VND"]`).
  - If `len(tickers) >= 2` and any comparative phrase matches (*"SO SÁNH"*, *"GIỮA"*, *"VÀ"*, *"NÊN CHỌN"*, *"TỐT HƠN"*, *"COMPARE"*, *"HƠN"*, *"ĐỐI ĐẦU"*):
    - Propose `{"tool_name": "COMPARE", "arguments": {"symbols": tickers[:5]}}`.
- In `_offline_synthesize()`:
  - Format a clean Markdown table comparing Symbol, Price, Change %, P/E, P/B, Valuation, ROE, Rev Growth, RSI, Signals.
  - Emit `RawStructuredClaim` objects for each cell of the comparative table for complete attribution verification.
  - Include balanced analytical summary highlighting trade-offs without prescriptive mandates.

---

## Verification Plan

1. **Backend Integration Tests**:
   - `StockCompareInternalToolTests`: Test `POST /internal/v1/tools/stocks/compare` with valid API key, missing API key, missing/mismatched ownerId, and valid multi-stock response payload.
2. **AI Service Pytest**:
   - `test_compare_tool.py`:
     - Test `plan_tools()` comparative intent detection for multi-symbol queries.
     - Test `validate_tool_call` on `COMPARE` schema validation and normalization.
     - Test `_offline_synthesize` formatting comparative markdown tables and generating structured claims.
     - Test graceful degradation when one stock has missing metrics.
3. **Full Regression Suites**:
   - Run `.\mvnw.cmd test "-Dtest=InternalToolControllerTests,StrategyScanInternalToolTests,ToolDelegateServiceTests,StockCompareInternalToolTests"`.
   - Run `uv run pytest`.
