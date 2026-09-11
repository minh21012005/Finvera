# Feature Specification: AI Analyst Peer Comparison Tool (COMPARE)

**Feature Directory**: `028-peer-comparison-tool`  
**Created**: 2026-09-10  
**Status**: Specified  
**SRS References**: Section 11 (Peer Comparison), Sections 30-32 (AI Analyst and tool orchestration), Section 4 (Transparency & Calibrated Support)
**SRS Requirement IDs**: SRS-CMP-01, SRS-AIA-01, SRS-AIA-02, SRS-AIA-03
**Input**: User requirement to enable AI Analyst to answer multi-stock comparison and evaluation queries (e.g., *"So sánh SSI và VND xem mã nào tốt hơn để đầu tư"*, *"Giữa HPG, HSG và NKG mã nào đang có định giá và tăng trưởng hấp dẫn hơn?"*).

---

## Scope Summary *(mandatory)*

Investors frequently compare multiple ticker symbols before executing investment decisions—often choosing between direct industry peers (e.g. SSI vs. VND, HPG vs. NKG) or evaluating alternatives in different sectors. Previously, when a user asked a comparative question mentioning 2 or more stocks, the AI Analyst router was forced to call single-stock tools (`STOCK`, `TECHNICAL`, `FUNDAMENTAL`, `VALUATION`) separately for only one symbol or sequentially burn tool call limits without a unified cross-stock comparison view.

This feature introduces a dedicated, high-performance comparative tool capability:
1. **Multi-Stock Comparison Tool (`COMPARE`)**:
   - Backend endpoint `POST /internal/v1/tools/stocks/compare` accepting a list of 2 to 5 ticker symbols (`symbols: ["HPG", "NKG", "HSG"]`).
   - Returns a structured side-by-side snapshot covering:
     - **Market & Price Stats**: Current price, percentage change, trading volume, and market capitalization.
     - **Valuation & Multiples**: P/E, P/B, valuation score, relative sector percentiles, and the published valuation-v3 classification (`UNDER_VALUED`, `FAIR_VALUED`, `OVER_VALUED`).
     - **Fundamental Quality & Growth**: ROE, ROA, EPS TTM, Revenue TTM, Net Profit TTM, Revenue Growth %, EPS Growth %, Debt-to-Equity, and available operating/gross/net margins.
     - **Technical & Strategy Signals**: RSI (14), Moving Average status (above/below MA20/MA50), active strategy setup (if any), and overall quantitative risk score/level.
2. **Intelligent Comparative Intent Routing**:
   - Updates `plan_tools()` and the LLM tool proposal prompt to automatically recognize multi-symbol queries containing comparative keywords (*"so sánh"*, *"giữa ... và ..."*, *"nên chọn mã nào"*, *"mã nào tốt hơn"*, *"đối đầu"*, *"compare"*).
   - Injects the normalized list of symbols directly into `COMPARE(symbols=[...])`.
3. **Structured Comparative Synthesis & Calibrated Trade-Off Analysis**:
   - Synthesizes a structured Markdown comparison table for clear readability.
   - Highlights relative trade-offs across 3 key dimensions: (1) Valuation attractiveness, (2) Fundamental quality & earnings momentum, (3) Technical timing & risk.
   - Strictly adheres to the Finvera Constitution: Provides balanced, evidence-based trade-off analysis without making prescriptive guarantees or issuing buy/sell mandates.

---

### In Scope

- Internal tool endpoint in Spring Boot: `POST /internal/v1/tools/stocks/compare` accepting `{ symbols: List<String> }` with `ownerId` and `X-Internal-Api-Key` authentication.
- Backend aggregation service method `ToolDelegateService.compareStocks(List<String> symbols)` aggregating existing module data for each symbol within the bounded five-symbol request.
- Orchestration tool declaration: `ToolName.COMPARE` in `finvera-ai` (`allowlist.py`, `service.py`, `dispatch.py`).
- Pydantic schema validation: `CompareToolArgs` validating between 2 and 5 ticker symbols, normalizing uppercase tickers.
- Tool router heuristic in `plan_tools()` detecting multiple distinct symbols and comparative terms.
- Deterministic offline synthesis formatting side-by-side comparison tables and extracting structured attribution claims (`RawStructuredClaim`) for verification.
- Unit and integration tests in both `finvera-be` and `finvera-ai`.

---

### Out of Scope

- Automated portfolio rebalancing execution or broker order routing.
- Fabricating subjective stock recommendations or ranking without quantitative underlying data.
- Comparing more than 5 stocks in a single request (queries with >5 stocks will be capped to top 5).
- Graphical comparison. SRS-CMP-01 was amended to tabular-only by the
  2026-09-11 product-scope decision; the delivered table is the complete
  currently planned presentation scope.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Head-to-Head Peer Comparison (Priority: P1)

As an equity investor, I want to ask AI Analyst to compare two direct competitors (e.g. *"So sánh SSI và VND"*) so that I can see their valuation, growth, and technical levels side-by-side to make an informed decision.

**Why this priority**: Head-to-head comparison is one of the most common natural workflows for retail and quantitative investors evaluating watchlist candidates.

**Independent Test**:
Query: *"So sánh SSI và VND"*
Expected Tool Call: `COMPARE(symbols=["SSI", "VND"])`
Expected Synthesis: A clear Markdown comparative table with P/E, P/B, ROE, Revenue Growth, RSI, and active signals, followed by a balanced breakdown of strengths and risks for both stocks.

**Acceptance Scenarios**:
1. **Given** two valid symbols "SSI" and "VND", **When** user asks *"So sánh SSI và VND"*, **Then** system executes `COMPARE(symbols=["SSI", "VND"])`, displays a side-by-side comparison table, and outlines relative strengths without declaring an absolute buy mandate.
2. **Given** three valid symbols "HPG", "HSG", "NKG", **When** user asks *"Giữa HPG, HSG và NKG thì mã nào đang có định giá và tăng trưởng tốt hơn?"*, **Then** system extracts all 3 symbols and returns a 3-column comparative summary.

---

### User Story 2 - Comparative Queries with Partial Data (Priority: P2)

As an investor, I want comparisons to succeed gracefully even if one company has a withheld valuation or missing quarterly report so that I still get comparative data for the metrics that are available.

**Why this priority**: Certain ticker symbols (e.g. newly listed or under restructuring) may have missing or withheld valuation metrics; the system must not crash or fail the entire comparison.

**Independent Test**: Compare a stock with complete data (e.g. FPT) against a stock with missing/withheld valuation (e.g. ROS or a symbol with incomplete data).
**Acceptance Scenarios**:
1. **Given** Symbol A has full data and Symbol B has `peRatio: null` (withheld with reason code), **When** comparison is generated, **Then** Symbol B's cell displays `"N/A (chưa đủ dữ liệu)"` while Symbol A displays its valid P/E, with full analytical commentary intact.

---

## Edge and Failure Cases *(mandatory)*

- **EF-001 (Invalid or Unknown Symbol)**: If one of the requested symbols does not exist in the database, the comparison displays the valid symbols and includes an alert note that the unknown symbol could not be found.
- **EF-002 (Single Symbol Input)**: If user asks "So sánh VNM" without specifying a second symbol, the tool validator defaults to fetching stock detail or asks user to name the comparison benchmark.
- **EF-003 (Different Sectors)**: When comparing stocks in fundamentally different sectors (e.g. VCB in Banking vs. HPG in Manufacturing), the synthesis explicitly warns the user that P/E and debt ratios may not be directly comparable across sectors due to structural business differences.
- **EF-004 (Excessive Symbols)**: When user lists more than 5 symbols (e.g. 8 symbols), the tool truncates the list to the first 5 symbols to preserve latency and cognitive clarity.

---

## Functional Requirements *(mandatory)*

- **FR-001 [Backend Comparison Tool Endpoint]**: `InternalToolController` MUST expose `POST /internal/v1/tools/stocks/compare` accepting `symbols` (list of strings) and `ownerId`, protected by `InternalApiKeyFilter`.
- **FR-002 [Batch Data Resolution]**: `ToolDelegateService.compareStocks` MUST resolve price overview, valuation assessment, fundamental report, and technical indicator/signals for each symbol. The unified response MUST expose revenue TTM, net profit TTM, debt-to-equity, available operating/gross/net margins, and P/E/P/B sector percentiles without estimating missing values.
- **FR-003 [AI Tool Declaration & Schema]**: `finvera-ai` MUST register `ToolName.COMPARE = "COMPARE"` with schema `CompareToolArgs(symbols: List[str], min_length=2)` and deterministically normalize, deduplicate, and cap a longer proposal to the first 5 symbols.
- **FR-004 [Comparative Intent Router]**: `plan_tools()` MUST identify multiple ticker symbols in the query accompanied by comparative trigger phrases (`"so sánh"`, `"giữa ... và ..."`, `"nên mua ... hay ..."`, `"compare"`, `"đối đầu"`) and route to `COMPARE`.
- **FR-005 [Side-by-Side Synthesis & Claim Verification]**: `_offline_synthesize()` and the online synthesis prompt MUST format markdown tables comparing price, P/E, P/B, valuation and sector percentiles, ROE, revenue/EPS growth, revenue/net profit TTM, debt-to-equity, available margins, RSI, and signals. Every displayed structured data point MUST emit a `RawStructuredClaim`; missing values remain visibly unavailable.
- **FR-006 [Constitutional Guardrail on Prescriptive Advice]**: The comparison analysis MUST weigh relative trade-offs and provide scenario considerations, strictly avoiding prescriptive directives like *"Khuyên bạn nên mua X ngay lập tức"* or predicting guaranteed investment yields.

---

## Non-Functional & Security Requirements

- **SEC-001**: Endpoint MUST require `X-Internal-Api-Key` and enforce valid `ownerId` check.
- **DATA-001**: Monetary and financial ratio precision MUST be preserved from underlying services without binary floating-point roundoff distortions.
- **PERF-001**: `compareStocks` for up to 5 symbols MUST execute in under 1,500ms under standard local workloads.

---

## Success Criteria *(mandatory)*

1. Users can query head-to-head stock comparisons in natural Vietnamese and receive structured multi-dimensional comparison tables within 3 seconds.
2. 100% of structured numerical facts in the comparative response are traceable to verified backend database values via `RawStructuredClaim`.
3. Zero regressions across existing single-stock tools (`STOCK`, `TECHNICAL`, `FUNDAMENTAL`, `VALUATION`, `STRATEGY_SCAN`).
4. Both Spring Boot backend tests and Python pytest test suites pass completely.
5. Contract tests prove the comparison payload preserves canonical debt-to-equity percent points (`100` means debt equals equity), published valuation-v3 labels, sector percentiles, and the expanded fundamental fields.

### Product Decision — Attribution Visibility (2026-09-10)

- Attribution is diagnostic metadata, not a gate that hides model output. Online responses remain complete so the operator can inspect the exact AI response.
- Structured comparison fields still carry claim coverage and per-source freshness metadata. The verifier MUST NOT rewrite or suppress model prose solely because part of it is unattributed.
