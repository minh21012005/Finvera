# Feature Specification: Stock Detail and Analysis

**Feature Directory**: `002-stock-detail-analysis`
**Created**: 2026-08-18
**Status**: Draft
**SRS References**: Sections 6.1, 6.2 (Overview/Technical/Fundamental/Valuation
subset), 7 (Technical Analysis, core indicators), 9 (Fundamental Analysis),
10 (Valuation Analysis), 36, 47 (MVP-2), 48, 54
**Input**: User description: "Stock Detail and Analysis (MVP-2). The owner
opens a single stock's detail page to research it before deciding whether to
investigate further. In scope: current price and session status, a price
chart, core technical indicators, core fundamental metrics, and a valuation
view showing whether the stock is over/under/fairly valued against a stated
deterministic method. All facts must carry source, observed-at/effective-at
time, and freshness state exactly like 001-market-overview already
established. Out of scope: the screener itself (MVP-3), strategy/signal/risk
(MVP-4), portfolio/watchlist, news/RAG, AI commentary. Same private
single-owner deployment as feature 001."

## Scope Summary *(mandatory)*

In the same private single-owner deployment established by
`001-market-overview`, Finvera's owner needs to open one supported Vietnamese
stock and understand its current price and trading activity, its recent price
history, its technical condition, its fundamental health, and whether its
current valuation looks expensive, cheap, or fair relative to its own history
and its sector/market — before deciding whether to screen, compare, or track
it further.

The stock detail view reuses the data-provenance and freshness vocabulary
`001-market-overview` already established (`CURRENT`, `DELAYED`, `STALE`,
`PARTIAL`, `UNAVAILABLE`), the same TCBS-live/Vnstock-historical provider
split, and the same owner-only private access model. It distinguishes observed
facts, deterministic calculations, and deterministic assessments from each
other, and it never fabricates a value it cannot support with accepted data.

### In Scope

- One stock overview per supported symbol: symbol, company name, current
  price, absolute and percentage change, market capitalization, sector,
  trading volume, session status, and freshness state.
- A daily historical price chart (OHLCV) for a bounded lookback window, with
  its corporate-action adjustment status disclosed.
- Core deterministic technical indicators on the daily timeframe: trend
  (MA20, MA50, MA200), momentum (RSI, MACD), volatility (Bollinger Bands,
  ATR), and volume (average volume, relative volume).
- Core fundamental metrics from the latest accepted reporting period: revenue,
  revenue growth, gross profit, operating profit, net profit, EPS, ROE, ROA,
  debt-to-equity, operating margin, free cash flow, and dividend.
- Valuation metrics (P/E, P/B, EV/EBITDA, PEG, dividend yield) and one overall
  deterministic valuation classification compared against the stock's own
  history and sector/market average.
- Safe degraded states for partial, stale, corrected, insufficient-history,
  and unavailable stock data, and for an unknown or unsupported symbol.

### Out of Scope

- The stock screener: filtering or ranking many stocks at once (MVP-3, a
  separate feature).
- Peer comparison across multiple selected companies (SRS Section 11).
- Multi-factor stock scoring and its component scores (SRS Section 12).
- Multi-timeframe analysis beyond the daily timeframe: intraday, weekly, and
  monthly evaluation and cross-timeframe alignment summaries (SRS Section 8).
- Price-structure detection (support/resistance, breakout/breakdown,
  Fibonacci retracement/extension) and candlestick pattern recognition (SRS
  Sections 7.5-7.6).
- The Financials, News, Research, and AI Analysis stock-page sections (SRS
  Section 6.2), natural-language explanations, and LLM-generated commentary.
- Strategy, signal, and risk scenarios (MVP-4); portfolio, watchlist, and
  journal actions; alerts and notifications.
- Automated order execution or investment recommendations.
- Multi-user or public delivery, invitations, sharing links, exports, and any
  redistribution of TCBS- or Vnstock-derived data.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Understand a Stock's Current Price and Recent History (Priority: P1)

As the owner, I want to open a supported stock and see its current price,
change, trading activity, and recent price history, so that I can quickly
understand where it stands before deciding whether to look deeper.

**Why this priority**: Price, change, and recent history are the minimum
useful research context for a single stock and deliver value independently of
technical, fundamental, or valuation analysis.

**Independent Test**: Open a supported symbol with a complete overview and
chart fixture and verify all required overview fields and the price chart
render correctly with as-of time and freshness. Repeat with a delayed fixture,
a closed-market fixture, and an unknown symbol, and verify the correct
labeled/degraded state in each case.

**Acceptance Scenarios**:

1. **Given** an accepted overview and chart snapshot exists for a supported
   symbol, **When** the owner opens that stock's detail page, **Then** it
   shows symbol, company name, current price, absolute change, percentage
   change, market capitalization, sector, trading volume, session status,
   as-of time, freshness state, and a daily OHLCV chart with its adjustment
   status.
2. **Given** the market is closed or it is a non-trading day, **When** the
   owner opens the stock detail page, **Then** the latest accepted session
   data remains visible and the page identifies the market as closed and
   shows the snapshot's trading date and as-of time.
3. **Given** the overview snapshot is delayed or stale, **When** the owner
   views the page, **Then** the freshness state is visibly labeled and the
   value is not presented as live/current.
4. **Given** the owner enters an unknown or unsupported symbol, **When** they
   request its detail page, **Then** the system shows a specific
   not-found/unsupported state without fabricating placeholder data.
5. **Given** the chart is unavailable while overview facts are available,
   **When** the owner opens the page, **Then** the overview remains usable and
   the chart shows a specific unavailable state.

---

### User Story 2 - Assess Technical Condition (Priority: P2)

As the owner, I want to see a stock's core technical indicators, so that I can
judge its trend, momentum, volatility, and trading activity without
recomputing them myself.

**Why this priority**: Technical condition adds decision-support value once
the underlying price facts are trustworthy and visible, and is independently
useful without fundamental or valuation analysis.

**Independent Test**: Supply a fixture with enough historical bars for every
supported indicator and verify each indicator's value, calculation window, and
as-of time render. Supply a newly listed symbol with fewer bars than an
indicator's minimum lookback and verify that indicator shows unavailable with
a missing-bar reason while other indicators remain usable.

**Acceptance Scenarios**:

1. **Given** sufficient accepted historical bars exist for a supported symbol,
   **When** the owner views its technical section, **Then** MA20, MA50, MA200,
   RSI, MACD, Bollinger Bands, ATR, average volume, and relative volume each
   show a value, calculation window, rule version, and as-of time.
2. **Given** an indicator's minimum historical lookback is not met, **When**
   the owner views the technical section, **Then** that indicator shows
   unavailable with the missing-bar reason and every other indicator remains
   usable.
3. **Given** the owner reloads the same accepted historical inputs and rule
   version, **When** they view the technical section, **Then** every indicator
   value is identical to the prior view.
4. **Given** a technical indicator is shown, **When** the owner reviews it,
   **Then** it is presented as a calculated value, not a trading instruction.

---

### User Story 3 - Assess Fundamental Health and Valuation (Priority: P3)

As the owner, I want to see a stock's fundamental metrics and a transparent
valuation classification, so that I can understand the company's financial
health and whether its price looks expensive, cheap, or fair, without treating
it as a guaranteed judgment.

**Why this priority**: Fundamental and valuation assessment adds the deepest
research value but depends on fundamental-report data with different
freshness characteristics than live price, and is meaningful only after price
and technical context already exist.

**Independent Test**: Provide fixed, versioned fundamental-report and
valuation-comparison fixtures for over-valued, fair-valued, under-valued, and
insufficient-data cases. Verify the same inputs and rule version always
produce the same classification and metrics, and that insufficient data
produces an unavailable assessment with a reason.

**Acceptance Scenarios**:

1. **Given** an accepted fundamental report exists for the latest reporting
   period, **When** the owner views the fundamental section, **Then** it shows
   revenue, revenue growth, gross profit, operating profit, net profit, EPS,
   ROE, ROA, debt-to-equity, operating margin, free cash flow, dividend, and
   the reporting period identity.
2. **Given** current price and accepted fundamentals meet the valuation
   input policy, **When** the owner views the valuation section, **Then** it
   shows P/E, P/B, EV/EBITDA, PEG, dividend yield, and exactly one overall
   classification of `OVER_VALUED`, `FAIR_VALUED`, or `UNDER_VALUED`, with the
   comparison basis (own history and/or sector/market average) disclosed.
3. **Given** required valuation inputs are missing, stale beyond policy, or
   the fundamental report is unavailable, **When** the owner views the
   valuation section, **Then** it shows the assessment as unavailable,
   identifies the reason, and does not invent a classification.
4. **Given** a valuation classification is displayed, **When** the owner
   reviews it, **Then** the page describes it as a quantitative assessment
   rather than a prediction, guarantee, or instruction to buy or sell.

### Edge and Failure Cases *(mandatory)*

- The requested symbol does not exist, is not on a supported exchange, or is
  currently unsupported by Finvera's reference data.
- A stock is suspended, halted, or newly listed with fewer historical sessions
  than an indicator's minimum lookback.
- A stock is delisted or its trading status changes between requests.
- A corporate action (split, stock dividend, cash dividend, rights issue)
  occurs inside the chart's lookback window, and adjusted/unadjusted series
  must not be silently mixed.
- The provider publishes a duplicate, out-of-order, or corrected price or
  fundamental-report snapshot for the same date/period.
- TCBS live price and Vnstock historical price disagree for the same subject
  and effective trading date.
- One section (chart, technical, fundamental, valuation) is unavailable while
  others remain available.
- A fundamental metric or valuation input is legitimately zero, negative
  (e.g., negative earnings making P/E undefined), or not applicable, versus
  missing or invalid.
- Sector or market average reference data needed for valuation comparison is
  incomplete for the stock's sector.
- Repeated refreshes occur while no new accepted snapshot is available.
- The TCBS access token expires and the owner has not manually renewed it.
- An external AI service is unavailable; the non-AI stock detail view must
  continue to work.

## Requirements *(mandatory)*

Requirements describe observable behavior. Accepted IDs are stable and MUST
not be renumbered; removed requirements are deprecated with a reason.

### Functional Requirements

- **FR-001**: The system MUST provide one stock overview per supported symbol
  containing symbol, company name, current price, absolute change, percentage
  change, market capitalization, sector, trading volume, trading date, as-of
  time, market session status, and freshness state.
- **FR-002**: Absolute and percentage price change MUST use the previous valid
  official close as the comparison basis; if that basis is unavailable, change
  fields MUST be unavailable rather than inferred.
- **FR-003**: The system MUST provide a daily OHLCV price chart for a bounded
  lookback window and MUST disclose whether the series is adjusted for
  corporate actions.
- **FR-004**: The system MUST distinguish at least `CURRENT`, `DELAYED`,
  `STALE`, `PARTIAL`, and `UNAVAILABLE` data states using user-readable
  labels, consistent with `001-market-overview`.
- **FR-005**: When sufficient historical bars exist, the system MUST calculate
  and display MA20, MA50, MA200, RSI, MACD, Bollinger Bands, ATR, average
  volume, and relative volume, each with its value, calculation window, rule
  version, and as-of time.
- **FR-006**: When an indicator's minimum historical lookback is not met, that
  indicator MUST show unavailable with a missing-bar reason instead of a
  fabricated or partially computed value; other indicators MUST remain usable.
- **FR-007**: The system MUST display revenue, revenue growth, gross profit,
  operating profit, net profit, EPS, ROE, ROA, debt-to-equity, operating
  margin, free cash flow, and dividend from the latest accepted fundamental
  reporting period, identifying that period.
- **FR-008**: The system MUST calculate and display P/E, P/B, EV/EBITDA, PEG,
  and dividend yield from current price and the latest accepted fundamentals.
- **FR-009**: When minimum required valuation inputs meet the approved
  quality and freshness policy, the system MUST classify the stock's overall
  valuation as exactly one of `OVER_VALUED`, `FAIR_VALUED`, or `UNDER_VALUED`
  using a deterministic, versioned method and MUST disclose the comparison
  basis used (own historical range and/or sector/market average).
- **FR-010**: The system MUST NOT publish a valuation classification when
  minimum input quality or freshness conditions are not met; it MUST show the
  unavailable/partial reason instead.
- **FR-011**: Given identical accepted inputs and the same rule version, every
  technical indicator result and the valuation classification MUST be
  identical across repeated views.
- **FR-012**: The failure or absence of one stock-detail section (chart,
  technical, fundamental, valuation) MUST NOT prevent other available
  sections from being viewed.
- **FR-013**: The latest accepted daily session MUST remain viewable when the
  market is closed or the current date is not a trading day.
- **FR-014**: When accepted source data (price or fundamentals) is corrected,
  the stock detail view MUST show the corrected facts and any resulting
  recalculated indicator or valuation result with a new as-of/update
  indication.
- **FR-015**: Technical and valuation presentation MUST identify the output as
  quantitative decision support and MUST NOT present it as a guaranteed
  forecast or a buy/sell instruction.
- **FR-016**: The system MUST let the owner look up a supported stock by
  symbol and MUST show a specific not-found/unsupported state for an unknown
  or unsupported symbol without fabricating placeholder data.

### Data and Financial Semantics

- **DATA-001**: Every price, chart, indicator, fundamental, and valuation fact
  MUST retain and expose enough provenance to identify its accepted source and
  observation time.
- **DATA-002**: Stock-facing dates and times MUST be interpreted in
  `Asia/Ho_Chi_Minh`; every displayed snapshot MUST include an unambiguous
  trading date or reporting period and as-of time.
- **DATA-003**: Price, indicator, fundamental, and valuation values MUST
  declare consistent display units and precision; monetary values MUST be
  identified as Vietnamese đồng or a clearly labeled VND scale and MUST use
  declared decimal precision, never binary floating point.
- **DATA-004**: Technical calculation and valuation classification MUST use
  unrounded source values; rounding occurs only for display and MUST NOT
  reverse the represented direction or classification.
- **DATA-005**: Freshness MUST be evaluated against an approved threshold for
  the dataset and market-session state; the resulting freshness state MUST be
  visible to the user.
- **DATA-006**: Duplicate and out-of-order price or fundamental-report
  snapshots MUST NOT cause the stock detail view to regress silently to older
  accepted facts.
- **DATA-007**: Zero, missing, invalid, and not-applicable values MUST remain
  distinguishable; missing or invalid financial values MUST NOT be replaced
  by zero.
- **DATA-008**: The price chart and every technical indicator MUST declare
  whether the underlying series is adjusted for corporate actions and MUST
  NOT silently mix adjusted and unadjusted values within one series.
- **DATA-009**: Technical indicator results and valuation assessments MUST
  retain the exact accepted input snapshot references, calculation window,
  and rule version needed to reproduce the result.
- **DATA-010**: Live TCBS observations and Vnstock historical observations for
  the same stock and date MUST retain distinct source identities. A material
  cross-source conflict MUST be surfaced and MUST prevent publication of an
  affected indicator or valuation result until resolved by an approved
  reconciliation rule.

### Security and Privacy

- **SEC-001**: While the active source is TCBS iFlash, stock detail data MUST
  be accessible only to the single configured owner identity established in
  `001-market-overview`; self-registration, invitations, shared links, and
  access by every other identity MUST be denied.
- **SEC-002**: The system MUST NOT expose raw provider data, transformed
  stock data, provider credentials, or tokens through a public endpoint,
  export, webhook, or third-party delivery.
- **SEC-003**: Stock-data adapters MUST allow only approved read-only
  operations (price, historical bars, fundamentals) and MUST NOT invoke
  trading, account, cash, portfolio, or order operations.
- **SEC-004**: Stock detail endpoints MUST enforce the same authenticated
  server-side session and CSRF controls established for the owner in
  `001-market-overview`; no separate authentication path may be introduced.

### Non-Functional Requirements

- **NFR-001**: Under normal operating conditions, at least 95% of stock
  detail visits MUST show a usable overview within 3 seconds of the owner's
  request.
- **NFR-002**: At least 99% of accepted live stock price updates MUST become
  visible within the approved source-delay policy plus 30 seconds; the UI
  MUST NOT call the feed contractually real-time unless TCBS grants that
  entitlement.
- **NFR-003**: For a shared as-of snapshot, 100% of displayed price, change,
  indicator, fundamental, and valuation values MUST be internally consistent
  to their declared precision.
- **NFR-004**: The stock detail view MUST remain available in a clearly
  labeled degraded state when AI capabilities are unavailable.
- **NFR-005**: Direction, freshness, trend, and valuation meaning MUST be
  understandable without relying on color alone.
- **NFR-006**: Operational monitoring MUST be able to distinguish source
  unavailability, stale data, invalid snapshots, calculation failure, and
  user-facing delivery failure without exposing provider credentials or
  private user data.
- **NFR-007**: When TCBS authentication expires, accepted stock facts remain
  visible with their true freshness and a `PROVIDER_AUTH_REQUIRED` reason; the
  system MUST NOT claim live updates until the owner renews the token.

### Key Entities

- **Stock**: A supported HOSE/HNX/UPCOM-listed equity identified by symbol,
  with company name, sector, exchange, and listing lifecycle status.
- **Stock Price Snapshot**: An accepted OHLCV observation for a stock and
  trading date, including change basis, liquidity facts, corporate-action
  adjustment status, source, and freshness state.
- **Technical Indicator Result**: A reproducible calculation for one stock and
  indicator, including value(s), calculation window, rule version, input
  snapshot references, and as-of time.
- **Fundamental Report**: Accepted fundamental metrics for a stock and
  reporting period, including source, effective/observation time, and
  provenance.
- **Valuation Assessment**: A reproducible classification for one stock,
  including contributing metrics, comparison basis, rule version, quality
  state, and as-of time.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- This feature extends the same private, single-owner deployment as
  `001-market-overview` using the owner's TCBS iFlash account and Vnstock's
  personal, non-commercial license; it does not reopen that access-model
  decision.
- MVP-2 scope is bounded to exactly what SRS Section 47 lists for it: price,
  chart, technical indicators, fundamental metrics, and valuation. Peer
  comparison, stock scoring, multi-timeframe analysis, price-structure/
  candlestick detection, and the Financials/News/Research/AI Analysis stock
  sections are separate future features per the MVP delivery order.
- The chart and technical indicators use the daily timeframe only for this
  feature; intraday, weekly, and monthly evaluation are deferred.
- The supported stock universe is HOSE/HNX/UPCOM-listed equities with accepted
  reference and historical data, consistent with the breadth universe policy
  established in `001-market-overview`.
- Sector/market average comparison for valuation reuses the same sector
  reference/classification data as the market breadth feature; selecting
  specific peer companies is deferred to the future Peer Comparison feature.
- Valuation classification is a descriptive quantitative assessment, not a
  forecast of future returns or a personalized recommendation.
- No LLM or generative AI capability is required to deliver this feature.

### Dependencies

- The TCBS iFlash and Vnstock historical-bootstrap capability/contract gates
  already established in `001-market-overview`, extended to per-stock
  instruments (not only indices).
- Sufficient historical OHLCV bars per stock to satisfy each indicator's
  minimum lookback (e.g., 200 sessions for MA200).
- An accepted fundamental-report data source, its provenance, and its
  reporting-period/fiscal-calendar semantics; this is a new data category
  beyond what `001-market-overview` researched and MUST be resolved in this
  feature's `research.md` before implementation, including whether TCBS
  iFlash, Vnstock, or another approved source supplies it.
- A corporate-action/price-adjustment data source for splits, stock and cash
  dividends, and rights issues affecting historical price series.
- A versioned deterministic technical-indicator calculation specification
  (formulas, rounding, precision) consistent with Constitution Principle I.
- A versioned deterministic valuation-classification methodology defining
  required inputs, comparison basis, thresholds, and minimum data quality.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In usability validation, the owner can identify a stock's
  price, direction, and session status within 10 seconds in each of three
  consecutive timed trials.
- **SC-002**: Across approved reference fixtures, 100% of displayed price,
  chart, indicator, fundamental, and valuation facts match the accepted
  source to the declared display precision, with no missing value
  represented as zero.
- **SC-003**: Across versioned indicator and valuation fixtures, 100% of
  results are reproducible from their recorded inputs and rule version.
- **SC-004**: Across stale, partial, corrected, insufficient-history, and
  unavailable data scenarios, 100% of views show the correct data-quality
  state and never fabricate a fact, indicator value, or valuation
  classification.
- **SC-005**: At least 95% of normal stock detail visits present a usable
  overview within 3 seconds, and at least 99% of accepted updates appear
  within the approved source-delay policy plus 30 seconds.
- **SC-006**: The P1-P3 journeys remain usable while all AI capabilities are
  unavailable, with no incorrect AI-related error shown on the stock detail
  view.
- **SC-007**: Accessibility review confirms that 100% of directional,
  freshness, trend, and valuation states have a non-color indicator.
- **SC-008**: Authorization tests show that the configured owner receives the
  stock detail view while every other identity and public ingress path are
  denied; no response, log, export, or client bundle contains TCBS
  credentials, tokens, or raw TCBS/Vnstock provider payloads.

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001, FR-002, FR-003 | US1 / Scenario 1 | SC-001, SC-002 |
| FR-004 | US1 / Scenario 3 | SC-004 |
| FR-013 | US1 / Scenario 2 | SC-001 |
| FR-016 | US1 / Scenario 4 | SC-004 |
| FR-012 | US1 / Scenario 5; US2-US3 partial cases | SC-004 |
| FR-005, FR-006 | US2 / Scenarios 1 and 2 | SC-002, SC-004 |
| FR-011 | US2 / Scenario 3; US3 / Scenario 2 | SC-003 |
| FR-007, FR-008, FR-009 | US3 / Scenarios 1 and 2 | SC-002, SC-003 |
| FR-010 | US3 / Scenario 3 | SC-004 |
| FR-014 | US1 / Scenario 3; correction edge case | SC-004 |
| FR-015 | US2 / Scenario 4; US3 / Scenario 4 | Acceptance review |
| DATA-001, DATA-002 | US1-US3 | SC-002, SC-003, SC-004 |
| DATA-003, DATA-004 | US1-US3 | SC-002 |
| DATA-005 | US1 / Scenario 3 | SC-004 |
| DATA-006, DATA-007 | Edge and failure cases | SC-002, SC-004 |
| DATA-008 | US1 / Scenario 1; corporate-action edge case | SC-002, SC-004 |
| DATA-009 | US2-US3 | SC-003 |
| DATA-010 | Cross-source conflict edge case | SC-002, SC-004 |
| NFR-001, NFR-002 | US1 / Scenario 1 | SC-005 |
| NFR-003 | US1-US3 | SC-002, SC-003 |
| NFR-004 | AI outage edge case | SC-006 |
| NFR-005 | US1-US3 | SC-007 |
| NFR-006 | All failure scenarios | Operational acceptance review |
| NFR-007 | TCBS token-expiry edge case | Contract and degraded-state tests |
| SEC-001-SEC-004 | Owner-only access and provider boundary | SC-008; authorization and adapter-negative tests |
