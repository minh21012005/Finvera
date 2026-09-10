# Feature Specification: AI Analyst Discovery & Multi-Archetype Screening Expansion

**Feature Directory**: `027-analyst-discovery-expansion`  
**Created**: 2026-09-10  
**Status**: Specified  
**SRS References**: Section 11 (AI Analyst), Section 4 (Transparency & Guardrails), Section 3.2 (Stock Screener & Strategy Signals)  
**SRS Requirement IDs**: SRS-AI-01, SRS-AI-02, SRS-AI-03, SRS-SCR-01, SRS-SIG-01  
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
- Rule-based and LLM prompt enhancement in `screener_conversion.py` supporting 4 standard archetypes:
  - `GROWTH`: ROE ≥ 15%, EPS growth ≥ 10%, Debt/Equity ≤ 1.0, exchange HOSE.
  - `VALUE`: P/E ≤ 12, P/B ≤ 1.5, ROE ≥ 12%, MarketCap ≥ 1,000B VND.
  - `DIVIDEND`: P/E ≤ 15, positive earnings, MarketCap ≥ 2,000B VND.
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

**Independent Test**: Send query *"Tìm cho tôi các cổ phiếu cơ bản tốt để đầu tư nắm giữ dài hạn"* to Analyst. Verify `SCREENING` is called with filters containing `roeMin: "15"`, `earningsGrowthPercentMin: "10"`, `debtToEquityMax: "1.0"`.

**Acceptance Scenarios**:
1. **Given** a natural language query specifying "dài hạn" or "tăng trưởng", **When** `convert_natural_language_to_filters` runs, **Then** it produces calibrated `fundamental` criteria with confidence ≥ 0.75 and an informative disclosure note.
2. **Given** a query asking for "cổ phiếu giá rẻ" or "định giá hấp dẫn", **When** converter runs, **Then** it produces `peMax: "12"`, `pbMax: "1.5"`, and `roeMin: "12"`.

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
- **FR-005**: `finvera-ai`'s `screener_conversion.py` MUST support Archetype Mapping for common investment styles:
  - `GROWTH`: `roeMin: "15"`, `earningsGrowthPercentMin: "10"`, `debtToEquityMax: "1.0"`.
  - `VALUE`: `peMax: "12"`, `pbMax: "1.5"`, `roeMin: "12"`, `marketCapMin: "1000000000000"`.
  - `MOMENTUM`: `maRelationship: ["PRICE_ABOVE_MA20"]`, `rsiMin: "50"`, `rsiMax: "68"`, `relativeVolumeMin: "1.2"`.
- **FR-006**: `finvera-ai`'s synthesis prompt MUST direct the model to present `STRATEGY_SCAN` matches with structured actionable sections: Entry Price, Stop-loss, Take-profit, and Risk/Reward assessment.

### Financial and Data Correctness

- **DATA-001**: All entry, stop-loss, and take-profit prices MUST be sourced directly from the deterministic `StrategySignalV1` engine response, formatted in VND with dot thousand separators.
- **DATA-002**: The model MUST NOT guarantee investment returns or issue unconditional trading mandates, maintaining calibrated conditional language (*"nhà đầu tư có thể cân nhắc"*, *"ngưỡng quản trị rủi ro"*).

### Security and Privacy

- **SEC-001**: Internal endpoints MUST require `X-Internal-Api-Key` header matching the shared secret and validate owner identity.
