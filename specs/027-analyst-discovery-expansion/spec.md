# Feature Specification: AI Analyst Discovery & Multi-Archetype Screening Expansion

**Feature Directory**: `027-analyst-discovery-expansion`  
**Created**: 2026-09-10  
**Status**: Specified  
**SRS References**: Sections 13–16 (Screener, Natural-Language Screener, Strategy and Signal) and Sections 30–32 (AI Analyst)
**SRS Requirement IDs**: SRS-SCR-01, SRS-SCR-03, SRS-STR-01, SRS-SIG-01, SRS-AIA-01, SRS-AIA-02, SRS-AIA-03
**Input**: User request to support open-ended discovery questions (e.g. *"Lọc cho tôi trên thị trường có mã cổ phiếu nào có tín hiệu tốt để trading ngắn hạn ko, nếu ko thì vào thời điểm nào, chiến lược ra sao"*, as well as long-term investment, growth, and value archetypes).

## Scope Summary *(mandatory)*

Currently, when a user asks AI Analyst open-ended discovery questions without specifying numeric thresholds (e.g., "mã nào có tín hiệu tốt để trading ngắn hạn", "cổ phiếu nào tiềm năng để tích sản dài hạn"), the natural-language screener conversion flags the query as vague and outputs an empty filter set `{}`. This causes the backend screener to return an arbitrary default list of 50 listed stocks without technical signal evaluations, entry/exit prices, or investment context.

This feature expands AI Analyst with two primary discovery capabilities:
1. **Quantitative Strategy Scan Tool (`STRATEGY_SCAN`)**: Connects AI Analyst directly to the backend's existing deterministic `StrategyScanService` (8 strategies: `MOMENTUM`, `BREAKOUT`, `TREND_FOLLOWING`, `PULLBACK`, `MEAN_REVERSION`, `MA_CROSSOVER`, `MACD_BASED`, `RSI_BASED`). When a user asks about short-term trading or technical signals, the router invokes `STRATEGY_SCAN`, receiving candidates evaluated by the deterministic engine with exact entry levels, stop-loss, take-profit, signal strength (`STRONG`, `MODERATE`), and risk levels (`LOW`, `MEDIUM`, `HIGH`).
2. **Archetype-Based Screener Mapping**: Enhances `screener_conversion.py` so that when a user asks about well-defined investment archetypes (e.g., "đầu tư dài hạn", "tăng trưởng", "đầu tư giá trị", "cổ phiếu cổ tức cao"), the system maps the query to standardized, calibrated quantitative filters (ROE, EPS growth, debt ratio, valuation multiples) rather than returning an empty filter `{}`.

### In Scope

- Internal tool endpoint in Spring Boot: `POST /tools/strategies/scan` returning paginated, sorted `ScanMatch` items with entry levels, stop-loss, take-profit, and risk assessments.
- Orchestration tool declaration: `STRATEGY_SCAN` in `finvera-ai` (`allowlist.py`, `service.py`, `dispatch.py`).
- Tool proposal prompt and keyword heuristics updated to route short-term trading, breakout, momentum, and technical setup questions to `STRATEGY_SCAN`.
- Rule-based and LLM prompt enhancement in `screener_conversion.py` supporting 5 disclosed, versioned discovery archetypes:
  - `GROWTH`: revenue growth ≥ 10%, EPS growth ≥ 10%, ROE ≥ 15%.
  - `LONG_TERM_QUALITY`: revenue growth ≥ 5%, EPS growth ≥ 5%, ROE ≥ 15%, MarketCap ≥ 1,000B VND.
  - `VALUE`: published `UNDER_VALUED` classification, ROE ≥ 12%, MarketCap ≥ 1,000B VND.
  - `DIVIDEND`: dividend yield ≥ 3%, positive/defined P/E, MarketCap ≥ 2,000B VND.
  - `MOMENTUM_SCREEN`: Price above MA20, RSI 50–68, Relative Volume ≥ 1.2x.
- Attribution verification support: Ensuring `STRATEGY_SCAN` claims are verified against authentic tool response data without wiping out the model's analytical narrative.

### Out of Scope

- Modifying the underlying mathematical logic of `StrategySignalV1` or `ScreenerV1` (they remain the single deterministic engines of record).
- Direct trading execution or order placement (Finvera is strictly a decision-support and research platform).
- Real-time intraday tick streaming (historical bars and daily closes remain the source of record).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Short-Term Trading Discovery via Strategy Scan (Priority: P1)

As an active trader, I want to ask AI Analyst for stocks with good short-term trading signals so that I can see the top candidates along with specific entry points, stop-loss, and take-profit targets.

**Why this priority**: Solves the immediate user problem where questions about trading setups were previously answered with a blank filter or a random list of stocks without price levels.

**Independent Test**: Send query *"Lọc cho tôi trên thị trường có mã cổ phiếu nào có tín hiệu tốt để trading ngắn hạn ko"* to the Analyst SSE stream. Verify `tool_call` emits `STRATEGY_SCAN` (e.g. `MOMENTUM` or `BREAKOUT`), and the final answer includes specific candidate stocks with entry price, stop-loss, take-profit, and calibrated risk notes.

**Acceptance Scenarios**:
1. **Given** a listed stock (e.g. KBC) has triggered a `MOMENTUM` or `BREAKOUT` signal in daily data, **When** user asks for short-term trading signals, **Then** AI Analyst executes `STRATEGY_SCAN`, retrieves the match, and explains the setup with entry/stop/target numbers matching the tool payload.
2. **Given** no stocks trigger a signal for a requested strategy, **When** `STRATEGY_SCAN` returns 0 matches, **Then** AI Analyst clearly states that no stocks currently meet the strategy entry criteria today, advising patience or waiting for confirmation.

---

### User Story 2 - Long-Term & Archetype Investment Screening (Priority: P1)

As a fundamental investor, I want to ask for stocks suitable for long-term holding, sustainable growth, or value investing so that AI Analyst screens the market against robust financial health standards instead of giving up.

**Why this priority**: Fundamental and long-term investors represent a core user segment who need disciplined screening based on financial ratios rather than technical indicators.

**Independent Test**: Send query *"Tìm cho tôi các cổ phiếu cơ bản tốt để đầu tư nắm giữ dài hạn"* to Analyst. Verify `SCREENING` is called with the `LONG_TERM_QUALITY` filters: `roeMin: "15"`, `revenueGrowthPercentMin: "5"`, `earningsGrowthPercentMin: "5"`, and `marketCapMin: "1000000000000"`.

**Acceptance Scenarios**:
1. **Given** a natural language query specifying "dài hạn" or "tăng trưởng", **When** `convert_natural_language_to_filters` runs, **Then** it selects the distinct `LONG_TERM_QUALITY` or `GROWTH` profile with confidence ≥ 0.75 and discloses the exact heuristic filters.
2. **Given** a query asking for "cổ phiếu giá trị" or "định giá hấp dẫn", **When** converter runs, **Then** it filters on published `UNDER_VALUED` classification, `roeMin: "12"`, and `marketCapMin: "1000000000000"` without applying universal P/E/P/B cut-offs.

---

## Edge and Failure Cases *(mandatory)*

- **EF-001 (No Signal Matches)**: When `STRATEGY_SCAN` returns `matches: []` (`totalMatchCount: 0`), the synthesis prompt must acknowledge 0 triggered signals and refrain from inventing hypothetical ticker candidates.
- **EF-002 (Invalid / Unsupported Strategy Code)**: If the model proposes an invalid strategy code, the backend defaults safely to `MOMENTUM` or rejects with HTTP 400 without crashing.
- **EF-003 (Empty Listed Universe / Provider Lag)**: If market daily bars are temporarily missing, `StrategyScanService` reports `excludedForInsufficientHistoryCount` without failing the HTTP request.
- **EF-004 (Attribution Preservation)**: Numbers present in `STRATEGY_SCAN` response data (entry price, stop-loss, take-profit, risk score) must be fully verifiable by `statement_numbers_are_attributed` without false positive rejections.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `finvera-be` MUST expose `POST /tools/strategies/scan` accepting `strategyCode` (enum `StrategyCode`) and `limit` (1 to 20, default 5), authorized with `X-Internal-Api-Key` and `ownerId`.
- **FR-002**: `finvera-be` `POST /tools/strategies/scan` MUST return `ScanMatch` objects containing `symbol`, `companyName`, `exchange`, `direction`, `levels` (`entryPrice`, `stopLoss`, `takeProfit`), `signalStrength`, `riskLevel`, `overallScore`, and `supportingEvidence`.
- **FR-003**: `finvera-ai` MUST include `ToolName.STRATEGY_SCAN` in its allowlist and declare it in `TOOL_DECLARATIONS` for Gemini function-calling with parameter `strategyCode` (`MOMENTUM`, `BREAKOUT`, `TREND_FOLLOWING`, `PULLBACK`, `RSI_BASED`, `MACD_BASED`, `MA_CROSSOVER`, `MEAN_REVERSION`).
- **FR-004**: `finvera-ai`'s deterministic planner `plan_tools()` MUST route queries containing short-term trading keywords (*"tín hiệu"*, *"trading"*, *"lướt sóng"*, *"ngắn hạn"*, *"breakout"*, *"vượt đỉnh"*, *"bắt đáy"*) to `STRATEGY_SCAN`.
- **FR-005**: `finvera-ai`'s `screener_conversion.py` MUST support the versioned Archetype v2 mappings defined in `research.md`:
  - `GROWTH`: `revenueGrowthPercentMin: "10"`, `earningsGrowthPercentMin: "10"`, `roeMin: "15"`.
  - `LONG_TERM_QUALITY`: `revenueGrowthPercentMin: "5"`, `earningsGrowthPercentMin: "5"`, `roeMin: "15"`, `marketCapMin: "1000000000000"`.
  - `VALUE`: `valuationClassification: ["UNDER_VALUED"]`, `roeMin: "12"`, `marketCapMin: "1000000000000"`.
  - `DIVIDEND`: `dividendYieldMin: "3"`, `peMin: "0"`, `marketCapMin: "2000000000000"`.
  - `MOMENTUM`: `maRelationship: ["PRICE_ABOVE_MA20"]`, `rsiMin: "50"`, `rsiMax: "68"`, `relativeVolumeMin: "1.2"`.
- **FR-006**: `finvera-ai`'s synthesis prompt MUST direct the model to present `STRATEGY_SCAN` matches with structured actionable sections: Entry Price, Stop-loss, Take-profit, and Risk/Reward assessment.

### Financial and Data Correctness

- **DATA-001**: All entry, stop-loss, and take-profit prices MUST be sourced directly from the deterministic `StrategySignalV1` engine response, formatted in VND with dot thousand separators.
- **DATA-002**: The model MUST NOT guarantee investment returns or issue unconditional trading mandates, maintaining calibrated conditional language (*"nhà đầu tư có thể cân nhắc"*, *"ngưỡng quản trị rủi ro"*).
- **DATA-003**: `DEBT_TO_EQUITY` MUST use canonical percent points across providers (`100` means debt equals equity); provider adapters MUST normalize before persistence.

## Success Criteria *(mandatory)*

1. All five archetype queries deterministically produce their documented v2 filters and explicit user bounds take precedence.
2. Value queries use the published valuation classification rather than fixed P/E/P/B thresholds.
3. Debt/Equity has one canonical unit across KBS, VCI, catalog metadata and screener inputs.
4. Existing strategy-scan behavior and all relevant backend/Python/exporter tests pass.

### Product Decision — Attribution Visibility (2026-09-10)

- Attribution reports whether structured claims are supported by tool data, but it is not a content-suppression gate.
- In online mode, the complete model response remains visible so the operator can inspect exactly what the AI produced. Coverage and attribution metadata MUST remain visible alongside that response.
- Prompts continue to request calibrated language under DATA-002; the verifier does not rewrite, truncate, or refuse the response solely because prose is unattributed.

### Security and Privacy

- **SEC-001**: Internal endpoints MUST require `X-Internal-Api-Key` header matching the shared secret and validate owner identity.
