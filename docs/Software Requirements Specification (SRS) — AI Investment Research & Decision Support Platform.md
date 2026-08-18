# Software Requirements Specification (SRS)

## AI Investment Research & Decision Support Platform

Product Name: Finvera
Product Type: AI-Powered Investment Research & Decision Support Platform
Target Market: Vietnamese Stock Market
Frontend: Next.js + TypeScript
Core Backend: Spring Boot Modular Monolith
AI Service: Python + FastAPI
Database: PostgreSQL
Vector Database: Qdrant
Cache: Redis
Messaging: Kafka (optional — not a default dependency; see section 43)
Platform: Web Application  
Architecture: Modular Monolith + AI Microservice

---

# 1. Introduction

## 1.1 Purpose

The AI Investment Research & Decision Support Platform is a web-based system designed to help users analyze and research the Vietnamese stock market through a combination of:

- Market data analysis
- Technical analysis
- Fundamental analysis
- Stock screening
- Investment strategy analysis
- Risk assessment
- Portfolio monitoring
- Financial news and research document analysis
- Retrieval-Augmented Generation (RAG)
- Large Language Model (LLM)-based explanations and research assistance

The system is intended to act as an **investment research and decision-support platform**, helping users understand market conditions, evaluate stocks, identify potential setups, assess risks, and understand the reasoning behind system-generated signals.

The system is not intended to guarantee investment returns or act as an autonomous trading system in the initial version.

---

## 1.2 Requirement Keywords

Normative statements in this document use the following keywords. They are
interpreted consistently throughout, and a feature specification may tighten a
`should` or `may` into a firm commitment but may not weaken a `shall`.

| Keyword | Meaning |
|---|---|
| `shall` | A mandatory capability. Removing it changes the product. |
| `should` | A strong recommendation. A feature may deviate only with a recorded rationale. |
| `may` | An optional capability. Its absence is not a defect. |

A statement that combines these keywords contradictorily (for example
"shall optionally") is a documentation defect and must be rewritten as one of
the three forms above.

---

## 1.3 Scope

The system covers the following major functional areas:

1. Market Intelligence
2. Stock Analysis
3. Technical Analysis
4. Fundamental Analysis
5. Stock Screener
6. Strategy and Signal Engine
7. Risk Analysis
8. Portfolio and Watchlist Management
9. News and Research Intelligence
10. AI Analyst
11. RAG-based Financial Document Analysis
12. Alert and Notification System
13. Backtesting
14. User Management and Authentication

The initial release will focus on research and decision support rather than automated order execution.

---

# 2. Product Vision

The platform aims to provide a unified workspace where a user can move from:

**Market → Stock → Analysis → Strategy → Risk → Evidence → Decision**

Instead of relying on a single AI-generated recommendation, the system combines deterministic quantitative analysis with AI-based interpretation.

The fundamental design principle is:

```text
Raw Data
   ↓
Data Processing
   ↓
Quantitative Analysis
   ↓
Signal / Scenario
   ↓
Evidence
   ↓
AI Explanation
   ↓
User Decision
```

The AI layer should primarily explain, summarize, retrieve, correlate, and interpret evidence rather than arbitrarily generate investment signals without supporting data.

---

# 3. Target Users

## 3.1 Individual Investors

Users who need a centralized platform to monitor stocks, analyze market conditions, and manage a personal watchlist or portfolio.

## 3.2 Fundamental Investors

Users interested in:

- Financial statements
- Company performance
- Valuation
- Earnings growth
- Management commentary
- Annual and quarterly reports

## 3.3 Technical Investors

Users interested in:

- Price action
- Indicators
- Volume
- Trend
- Support/resistance
- Breakouts
- Multi-timeframe analysis
- Trading strategies

## 3.4 Research-oriented Users

Users who want to interact with financial reports, news, and research documents through an AI assistant.

---

# 4. Functional Requirements

## 4.1 User Management and Authentication

The system shall provide:

- User registration
- User login
- Logout
- JWT-based authentication
- Token refresh
- Password management
- User profile management

The system shall protect user-specific resources such as:

- Portfolio
- Positions
- Watchlists
- Investment journal
- Alerts
- AI conversation history

---

# 5. Market Intelligence

## 5.1 Market Overview

The system shall provide an overview of the Vietnamese stock market.

Supported market indices include:

- VN-Index
- VN30
- HNX Index
- UPCOM Index

The market overview shall display:

- Current index value
- Price change
- Percentage change
- Trading volume
- Trading value
- Market breadth
- Advancing stocks
- Declining stocks
- Unchanged stocks

---

## 5.2 Sector Analysis

The system shall provide sector-level performance analysis.

Initial supported sectors may include:

- Banking
- Securities
- Real Estate
- Technology
- Industrial
- Consumer
- Energy
- Materials
- Healthcare
- Utilities

The system shall provide:

- Sector performance
- Sector momentum
- Sector liquidity
- Relative strength
- Leading stocks
- Weak stocks

---

## 5.3 Market Regime Detection

The system shall classify the current market regime.

Supported regimes:

```text
BULL
EARLY_BULL
SIDEWAYS
EARLY_BEAR
BEAR
```

The Market Regime Engine shall consider factors such as:

- Index trend
- Moving averages
- Momentum
- Market breadth
- Trading volume
- Volatility
- Sector strength

The system shall provide:

- Current regime
- Regime score
- Confidence score
- Supporting factors

Example:

```text
Market Regime: EARLY BULL
Score: 74/100
Confidence: 81%

Supporting Factors:
+ VN-Index above MA20
+ VN-Index above MA50
+ Positive market breadth
+ Improving liquidity
+ Strong banking sector
```

---

# 6. Stock Analysis

## 6.1 Stock Overview

The system shall provide a dedicated analysis page for each supported stock.

The stock page shall include:

- Symbol
- Company name
- Current price
- Price change
- Percentage change
- Market capitalization
- Sector
- Trading volume
- Overall stock score
- Risk classification
- Trend classification
- Valuation classification

---

## 6.2 Stock Analysis Sections

Each stock shall support the following sections:

```text
Overview
Technical
Fundamental
Valuation
Financials
News
Research
AI Analysis
```

---

# 7. Technical Analysis

The Technical Analysis Engine shall calculate indicators from historical market data.

## 7.1 Trend Indicators

The system shall support:

- SMA
- EMA
- MA20
- MA50
- MA200

## 7.2 Momentum Indicators

The system shall support:

- RSI
- MACD
- Stochastic

## 7.3 Volatility Indicators

The system shall support:

- Bollinger Bands
- ATR

## 7.4 Volume Analysis

The system shall support:

- Average Volume
- Relative Volume
- Volume Spike
- Volume Trend

## 7.5 Price Structure

The system shall support:

- Support levels
- Resistance levels
- Breakout detection
- Breakdown detection
- Fibonacci retracement
- Fibonacci extension

## 7.6 Candlestick Analysis

The system may identify common candlestick patterns including:

- Doji
- Hammer
- Inverted Hammer
- Engulfing
- Morning Star
- Evening Star
- Shooting Star

---

# 8. Multi-Timeframe Analysis

The Technical Analysis Engine shall support multiple timeframes:

```text
Intraday
Daily
Weekly
Monthly
```

The system shall independently evaluate technical conditions for each timeframe.

Example:

```text
Daily: Bullish
Weekly: Bullish
Monthly: Neutral
```

The system shall generate a multi-timeframe summary describing potential alignment or conflict between timeframes.

---

# 9. Fundamental Analysis

The system shall analyze company fundamentals.

## 9.1 Financial Metrics

Supported metrics include:

- Revenue
- Revenue growth
- Gross profit
- Operating profit
- Net profit
- EPS
- ROE
- ROA
- Debt-to-equity
- Operating margin
- Free cash flow
- Dividend
- Cash flow metrics

---

# 10. Valuation Analysis

The system shall support:

- P/E
- P/B
- EV/EBITDA
- PEG
- Dividend Yield

The platform shall compare valuation metrics against:

- Historical valuation
- Sector average
- Selected peer companies
- Market average where applicable

---

# 11. Peer Comparison

Users shall be able to compare multiple companies.

Example:

```text
FPT
CMG
VGI
CTR
```

Supported comparison dimensions:

- Revenue
- Profit
- EPS
- EPS growth
- ROE
- ROA
- P/E
- P/B
- Debt
- Margin
- Market capitalization

The system shall present both tabular and graphical comparisons.

---

# 12. Stock Scoring

The system shall calculate a multi-factor stock score.

Potential components:

```text
Technical Score
Fundamental Score
Valuation Score
Momentum Score
Volume Score
Sector Score
Risk Score
```

The system shall produce an overall score.

Example:

```text
Technical       82
Fundamental     76
Valuation       64
Momentum        87
Volume          79
Sector          81
Risk            58

Overall         77
```

The system shall also expose the factors contributing to the score.

---

# 13. Stock Screener

The system shall provide configurable stock screening.

Supported filters may include:

### Market

- Exchange
- Sector
- Market capitalization

### Price

- Minimum price
- Maximum price
- Price change

### Technical

- RSI
- MACD
- MA relationship
- Volume
- Relative volume
- Breakout
- Trend

### Fundamental

- Revenue growth
- Earnings growth
- ROE
- ROA
- P/E
- P/B
- Debt-to-equity

Users shall be able to combine multiple filters.

---

# 14. Natural Language Screener

The AI system may convert natural-language requests into structured screening conditions.

Example:

```text
"Find Vietnamese stocks above MA50,
with strong volume and positive momentum."
```

The AI shall convert this into structured filters before executing the screener.

The final filtering shall be executed by the deterministic Screener Engine rather than by the LLM itself.

---

# 15. Strategy Engine

The system shall support configurable investment strategies.

Initial strategy types may include:

- Trend Following
- Momentum
- Breakout
- Pullback
- Mean Reversion
- Moving Average Crossover
- MACD-based Strategy
- RSI-based Strategy

A strategy shall contain:

```text
Strategy Name
Conditions
Entry Rules
Exit Rules
Risk Rules
Timeframe
```

---

# 16. Signal Engine

The Signal Engine shall evaluate stocks against strategy conditions.

A signal shall contain:

```text
Symbol
Strategy
Direction
Signal Strength
Entry Zone
Stop Loss
Take Profit
Risk/Reward
Risk Level
Supporting Evidence
Created At
```

Example:

```text
Direction: LONG

Entry Zone:
155,000 – 157,000

Stop Loss:
149,000

Target 1:
165,000

Target 2:
172,000

Risk/Reward:
1 : 2.4

Risk:
MEDIUM
```

Signals shall represent system-generated scenarios or setups rather than guarantees of future price movement.

---

# 17. Risk Engine

The Risk Engine shall evaluate trade and portfolio risk.

Factors may include:

- Historical volatility
- ATR
- Drawdown
- Liquidity
- Stop-loss distance
- Market regime
- Position concentration
- Sector concentration

The system shall provide:

```text
Risk Score
Risk Level
Risk Factors
```

Potential classifications:

```text
LOW
MEDIUM
HIGH
```

---

# 18. Position Sizing

The system may calculate suggested position sizes based on:

- Available capital
- Maximum acceptable trade risk
- Stop-loss distance
- Portfolio exposure

Where position sizing is offered, its calculation shall be deterministic,
reproducible, and transparent about every input and assumption it uses.

---

# 19. Backtesting Engine

The system shall provide historical backtesting for supported strategies.

Users shall be able to configure:

- Strategy
- Symbol
- Time period
- Initial capital
- Position sizing
- Transaction cost assumptions

The backtesting engine shall calculate:

- Total return
- CAGR
- Win rate
- Profit factor
- Maximum drawdown
- Sharpe ratio
- Average trade
- Number of trades

The engine shall account for relevant factors such as:

- Transaction costs
- Slippage
- Corporate actions
- Historical data correctness
- Look-ahead bias prevention
- Survivorship bias considerations

---

# 20. Portfolio Management

Users shall be able to maintain one or more portfolios.

Portfolio functionality shall include:

- Holdings
- Cash
- Positions
- Entry price
- Current price
- Quantity
- Unrealized P/L
- Realized P/L
- Allocation
- Performance

---

# 21. Portfolio Analytics

The system shall provide:

- Portfolio return
- Drawdown
- Risk exposure
- Stock concentration
- Sector concentration
- Benchmark comparison
- Portfolio performance history

Potential benchmark:

```text
Portfolio Performance
vs
VN-Index
```

---

# 22. Watchlist

Users shall be able to create and manage watchlists.

Each watchlist item may display:

- Symbol
- Current price
- Daily change
- Technical trend
- Overall score
- Signal
- Risk level
- Volume condition

---

# 23. Investment Journal

Users may record investment decisions.

A journal entry may contain:

```text
Symbol
Entry Price
Quantity
Strategy
Reason
Market Regime
Expected Scenario
Stop Loss
Take Profit
Notes
Timestamp
```

The system may later use journal data to provide personalized analytics.

Example:

```text
Trend Following:
Win Rate = 63%

Breakout:
Win Rate = 48%
```

---

# 24. News Aggregation

The system shall collect and normalize financial news from supported external sources.

News shall be categorized as:

```text
Company
Sector
Market
Macro
Regulation
```

Each article may contain:

- Title
- Source
- Published time
- Content or relevant excerpt
- Related stocks
- Sector
- Sentiment
- Potential impact classification

---

# 25. News Intelligence

The AI/NLP subsystem may perform:

- Entity extraction
- Stock symbol extraction
- Company identification
- Sector classification
- Sentiment classification
- Potential market impact classification

Example:

```text
News
↓
Entity Extraction
↓
FPT / Technology
↓
Sentiment: Positive
↓
Potential Impact: Medium
```

---

# 26. Research Document Management

The system shall support ingestion of documents such as:

- Annual reports
- Quarterly reports
- Financial reports
- Economic reports
- Investor presentations
- Corporate disclosures
- Other supported research documents

Documents shall be stored with metadata including:

```text
Document ID
Company
Symbol
Document Type
Year
Quarter
Source
Publication Date
```

---

# 27. RAG System

The RAG subsystem shall be implemented in the Python AI Service.

Processing pipeline:

```text
Document
   ↓
Document Loader
   ↓
Text Extraction
   ↓
Cleaning
   ↓
Chunking
   ↓
Metadata Extraction
   ↓
Embedding
   ↓
Qdrant
```

---

# 28. Vector Database

The system shall use **Qdrant** as the dedicated vector database for the AI/RAG subsystem.

Qdrant shall store:

- Vector embeddings
- Document chunk identifiers
- Metadata required for retrieval

Example metadata:

```text
document_id
company
symbol
document_type
year
quarter
page
section
source
```

Qdrant shall act as a retrieval index rather than the primary source of truth.

---

# 29. RAG Retrieval

The retrieval pipeline shall support:

```text
User Question
      ↓
Query Analysis
      ↓
Metadata Filtering
      ↓
Semantic / Hybrid Retrieval
      ↓
Qdrant
      ↓
Reranking
      ↓
Context Construction
      ↓
LLM
```

The system should preserve document metadata so that answers can cite the original source.

Example citation:

```text
FPT Annual Report 2025
Page 87
Business Results
```

---

# 30. AI Analyst

The platform shall provide an AI Analyst capable of answering investment research questions.

Example queries:

```text
"Phân tích FPT hiện tại."

"Tại sao FPT tăng hôm nay?"

"FPT đang overbought không?"

"So sánh FPT và CMG."

"Tóm tắt báo cáo tài chính gần nhất."

"Rủi ro lớn nhất của FPT là gì?"

"Những cổ phiếu nào đang có momentum tốt?"
```

---

# 31. AI Orchestration

The AI Service shall provide an orchestration layer capable of selecting appropriate tools.

Possible tools:

```text
Market Tool
Stock Tool
Technical Analysis Tool
Fundamental Analysis Tool
Valuation Tool
Portfolio Tool
News Tool
Research/RAG Tool
Screening Tool
```

Example workflow:

```text
User Question
      ↓
AI Orchestrator
      ↓
get_stock(FPT)
      ↓
get_technical(FPT)
      ↓
get_fundamental(FPT)
      ↓
get_valuation(FPT)
      ↓
get_news(FPT)
      ↓
search_reports(FPT)
      ↓
LLM
      ↓
Final Response
```

---

# 32. AI Explanation

The AI shall explain deterministic system outputs.

For example:

```text
Technical Signal:
Bullish
```

with evidence:

```text
+ Price above MA50
+ MACD bullish crossover
+ Volume expansion
```

The AI shall transform these structured factors into human-readable explanations.

The LLM shall not replace the deterministic calculation engine for indicators or strategy rules.

---

# 33. Structured Data vs RAG

The system shall clearly separate structured-data queries from document-retrieval queries.

### Structured data

Use application tools/services for:

- Price
- OHLCV
- RSI
- MACD
- Moving averages
- P/E
- EPS
- ROE
- Portfolio
- Signals

### RAG

Use document retrieval for:

- Annual report statements
- Management commentary
- Business strategy
- Economic reports
- Research documents
- News content

The AI Analyst may combine both sources.

---

# 34. Daily Market Briefing

The system may generate an AI-powered daily market briefing.

Potential content:

```text
Market Regime
VN-Index Performance
Market Breadth
Strong Sectors
Weak Sectors
Unusual Volume
Important News
Macro Events
Stocks to Monitor
Key Risks
```

The briefing shall reference supporting data and sources where applicable.

---

# 35. Alert System

Users shall be able to configure alerts.

Supported conditions may include:

```text
Price threshold
RSI threshold
MACD crossover
MA crossover
Volume spike
Breakout
Breakdown
Market regime change
Strategy signal
Portfolio risk condition
```

Supported delivery channels:

- Web notifications
- Email
- Telegram
- Future mobile push notifications

---

# 36. Non-Functional Requirements

The thresholds in this section are **product baselines**, measured under normal
operating conditions. A feature specification shall restate the thresholds that
apply to it, and may tighten them, but shall not silently relax one. Where a
capability cannot meet a baseline, the feature plan shall record the deviation
and its rationale.

## 36.1 Performance

The system shall meet these baselines:

| Measure | Baseline |
|---|---|
| Primary read view (dashboard, stock detail, watchlist) | 95% of visits usable within 3 seconds |
| Accepted market update becoming visible | 99% within the declared source-delay policy plus 30 seconds |
| Screening or filtering over the supported universe | 95% of queries return within 5 seconds |
| Interactive AI answer | 95% begin streaming or return within 15 seconds |

The system should cache market data where the cache cannot become a system of
record, and should query historical series through bounded, paginated access.

Heavy AI, embedding, backtesting, and document-processing work shall run
asynchronously and shall not block ordinary transactional request processing.

---

## 36.2 Scalability

The architecture shall allow the platform to evolve from:

```text
Modular Monolith + AI Service
```

to:

```text
API Gateway
├── Core Service
├── Market Data Service
├── AI Service
├── Document Service
└── Backtesting Service
```

without requiring a complete redesign.

---

## 36.3 Availability

Core authentication, market-data, stock-analysis, and portfolio functionality
shall remain usable when the LLM provider, embedding provider, vector database,
news source, or notification channel is degraded or unavailable.

AI functionality shall degrade gracefully: the system shall present an explicit
unavailable or partial state rather than an empty view, a fabricated answer, or
an error that blocks non-AI capabilities.

| Measure | Baseline |
|---|---|
| Non-AI capabilities usable during a full AI outage | 100% of P1 journeys |
| Degraded state labelled rather than silently empty | 100% of affected views |

Every external call shall declare an explicit timeout, bounded retry for safe
operations only, and a defined fallback path.

---

## 36.4 Security

The system shall implement:

- Authentication and server-side authorization at every trust boundary
- Object-level ownership checks for every user-scoped resource
- A session or token mechanism with secure handling and rotation
  (see section 57 for the currently accepted mechanism)
- Input validation and parameterized persistence access
- API protection and rate limiting on authentication and abuse-prone endpoints
- Secret management outside source control

User portfolios, positions, watchlists, journals, alerts, conversations, and
documents shall be private by default and denied unless explicitly authorized.

Retrieved documents, news, and external tool output shall be treated as
untrusted data. They shall not authorize tools, alter system policy, or be
executed as instructions.

The system shall send the minimum user data required to an external model and
shall not place secrets, tokens, private portfolio data, or raw personal data
into prompts, telemetry, or logs.

| Measure | Baseline |
|---|---|
| Cross-user or unauthenticated access to a private resource | 0 successful attempts in authorization tests |
| Secrets, tokens, or personal data present in logs, responses, or client bundles | 0 occurrences |
| Authentication or data-access change shipped without a negative authorization test | 0 |

---

## 36.5 Observability

The system shall emit structured logs, application metrics, health signals, and
correlation identifiers at every new boundary, sufficient to diagnose latency,
errors, data staleness, and dependency health.

Operational monitoring shall distinguish at minimum: provider unavailability,
provider authentication failure, stale data, invalid or rejected snapshots,
calculation failure, and user-facing delivery failure.

Observability shall not capture secrets, tokens, raw personal data, or private
financial and document payloads.

| Measure | Baseline |
|---|---|
| Failure classes distinguishable without inspecting payloads | 100% of the classes listed above |
| Sensitive values present in telemetry | 0 occurrences |

Potential technologies:

```text
Spring Actuator
Prometheus
Grafana
```

---

## 36.6 Accessibility and Localization

The primary audience is Vietnamese retail and research investors, and the
product presents direction, risk, and confidence information that must not be
misread.

- Direction, freshness, risk, breadth, confidence, and valuation state shall be
  distinguishable without relying on color alone.
- Interactive views shall preserve keyboard focus, semantic structure,
  sufficient contrast, and reduced-motion behavior.
- Charts shall provide a keyboard-accessible summary or equivalent textual
  evidence.
- Monetary and numeric values shall use locale-aware formatting for Vietnamese
  users while preserving the exact value where rounding could mislead.

| Measure | Baseline |
|---|---|
| Directional, freshness, risk, and confidence states carrying a non-color indicator | 100% |
| Charts without a textual or keyboard-accessible equivalent | 0 |

---

## 36.7 Data Quality, Provenance, and Temporal Integrity

Every displayed market fact is an observation with a source and a time, not an
ambient truth.

- Each market, fundamental, and derived value shall retain its source identity,
  observation time, effective time, and ingestion time where relevant.
- Market-facing dates and times shall be interpreted in `Asia/Ho_Chi_Minh`;
  transport and storage shall use UTC unless a feature contract records another
  deliberate representation. The host timezone shall never be relied upon.
- Every dataset shall expose a user-visible freshness state that distinguishes
  at minimum current, delayed, stale, partial, and unavailable data.
- Monetary amounts, ratios, and order-sensitive calculations shall use declared
  decimal precision and rounding. Binary floating point shall not be used for
  authoritative financial values.
- Zero, missing, invalid, and not-applicable values shall remain
  distinguishable. A missing value shall never be displayed as zero.
- Rounding shall occur only for display and shall never reverse the direction
  or classification a value represents.
- Corporate actions, trading-calendar boundaries, suspensions, price limits, and
  data corrections shall be handled explicitly rather than assumed away.
- Duplicate, out-of-order, and corrected snapshots shall not cause a view to
  regress silently to older facts.
- Deterministic results shall record the rule version and input references
  needed to reproduce them.

| Measure | Baseline |
|---|---|
| Displayed facts carrying source and as-of time | 100% |
| Missing or invalid financial values rendered as zero | 0 |
| Deterministic results reproducible from recorded inputs and rule version | 100% |

---

## 36.8 Privacy, Retention, and Data Rights

- Market data may be used only within the rights actually granted by its
  provider. Display, storage, redistribution, and export rights shall be
  confirmed per provider and recorded in a decision record before integration,
  never inferred from a software license.
- Personal data, portfolio contents, journals, and conversations shall be
  collected and retained only as long as the feature that owns them requires,
  with the retention period stated by that feature.
- Private user data shall not be sent to an external model, notification
  channel, or third party beyond what the invoked capability requires, and the
  user shall be able to understand what leaves the system.
- Deletion of a user-owned resource shall remove its derived copies, including
  cache and vector-index entries, within a period the owning feature declares.

| Measure | Baseline |
|---|---|
| External providers integrated without recorded data-usage rights | 0 |
| Private user data leaving the system beyond the invoked capability | 0 occurrences |

---

# 37. System Architecture

## 37.1 High-Level Architecture

```text
                         ┌───────────────┐
                         │    Next.js    │
                         │   TypeScript  │
                         └───────┬───────┘
                                 │
                                 ▼
                     ┌────────────────────┐
                     │ Spring Boot Core   │
                     │  Modular Monolith  │
                     └─────────┬──────────┘
                               │
                         REST / gRPC
                               │
                               ▼
                     ┌────────────────────┐
                     │ Python AI Service  │
                     │      FastAPI       │
                     └──────┬───────┬─────┘
                            │       │
                            ▼       ▼
                         Qdrant    LLM
```

---

# 38. Spring Boot Architecture

The Spring Boot application shall follow a modular domain-oriented structure.

```text
Spring Boot Core
│
├── auth
├── user
├── market
├── stock
├── technical
├── fundamental
├── screener
├── strategy
├── signal
├── risk
├── portfolio
├── watchlist
├── news
├── alert
└── ai
```

The initial implementation shall use a modular monolith rather than immediately splitting all domains into independent microservices.

---

# 39. Python AI Service Architecture

The Python service shall use FastAPI and be structured around AI capabilities.

```text
Python AI Service
│
├── api
├── core
├── features
│   ├── chat
│   ├── rag
│   ├── embeddings
│   ├── document
│   ├── analysis
│   └── orchestration
└── infrastructure
    ├── llm
    ├── qdrant
    ├── loaders
    └── external_services
```

---

# 40. Data Architecture

## 40.1 PostgreSQL

PostgreSQL shall act as the primary transactional database.

Potential entities:

```text
User
Stock
Company
MarketData
FinancialStatement
TechnicalIndicator
Strategy
Signal
Portfolio
Position
Transaction
Watchlist
News
ResearchDocument
Alert
AIConversation
```

---

## 40.2 Redis

Redis shall be used for:

- Cache
- Frequently accessed market data
- API response caching
- Temporary state
- Rate limiting
- Potential session-related use cases

---

## 40.3 Qdrant

Qdrant shall store:

- Document embeddings
- News embeddings
- Financial report embeddings
- Research document chunks
- Associated retrieval metadata

---

# 41. External Integrations

The system may integrate with external providers for:

## Market Data

- Vietnamese stock market data provider
- Historical OHLCV
- Intraday data
- Financial data

## News

- Financial news APIs
- Supported news sources

## AI

- Gemini as the initial LLM provider, behind a replaceable provider adapter

## Notifications

- Email provider
- Telegram Bot API
- Future mobile notification services

---

# 42. Data Ingestion Pipeline

The data ingestion subsystem shall follow:

```text
External Data Provider
        ↓
Data Ingestion
        ↓
Normalization
        ↓
Validation
        ↓
PostgreSQL
        ↓
Analytics Engine
```

Market data ingestion shall support scheduled updates and, where available, streaming or near-real-time updates.

---

# 43. Event-Driven Extensions

The architecture should support future event-driven processing.

Potential events:

```text
MARKET_PRICE_UPDATED
TECHNICAL_SIGNAL_CREATED
NEWS_PUBLISHED
MARKET_REGIME_CHANGED
ALERT_TRIGGERED
DOCUMENT_INGESTED
RAG_INDEX_UPDATED
```

Kafka is **not** a default dependency of the initial architecture. The events
listed above are conceptual extension points, not an implemented transport.

Kafka may be adopted later only if event volume, ordering, replay, or
decoupling requirements justify it, and its adoption requires a documented
decision record.

---

# 44. Technology Stack

## Frontend

```text
Next.js
TypeScript
React
TradingView Lightweight Charts / ECharts
```

## Backend

```text
Java 21
Spring Boot 4.1.x
Spring Security
Spring Data JPA
Hibernate
REST API
```

## AI Service

```text
Python
FastAPI
Pydantic
LLM SDK
RAG Framework
Embedding Model
NLP Libraries
```

## Storage

```text
PostgreSQL
Redis
Qdrant
```

## Messaging

```text
Kafka (optional; adopted only under section 43)
```

## Infrastructure

```text
Docker
AWS
Nginx
```

## Monitoring

```text
Spring Actuator
Prometheus
Grafana
```

---

# 45. API Communication

The initial communication model between Spring Boot and the AI Service shall use REST APIs.

Example:

```text
POST /api/v1/ai/analyze-stock
POST /api/v1/ai/chat
POST /api/v1/ai/summarize-report
POST /api/v1/ai/search-research
POST /api/v1/ai/explain-signal
```

Spring Boot shall act as the primary API boundary for the frontend.

The frontend shall not directly access the internal AI Service in the initial architecture.

Preferred communication:

```text
Next.js
   ↓
Spring Boot
   ↓
Python AI Service
```

---

# 46. AI Service Security Boundary

The Python AI Service should preferably be an internal service.

The frontend shall not directly invoke:

```text
Python AI Service
```

Instead:

```text
Frontend
   ↓
Spring Boot Authorization
   ↓
AI Service
```

This allows Spring Boot to enforce:

- User authorization
- Usage limits
- Portfolio permissions
- AI quotas
- Request validation

---

# 47. MVP Scope

The first MVP shall focus on seven capabilities:

## MVP-1

Market Dashboard

## MVP-2

Stock Detail and Analysis

Including:

- Price
- Chart
- Technical indicators
- Fundamental metrics
- Valuation

## MVP-3

Stock Screener

## MVP-4

Strategy and Signal Engine

## MVP-5

Portfolio and Watchlist

## MVP-6

News and Financial Report RAG

## MVP-7

AI Analyst

---

# 48. Features Excluded From Initial MVP

The following shall not be part of the initial MVP:

- Automated broker order execution
- Fully autonomous AI trading
- Mobile application
- Complex predictive ML models
- Advanced high-frequency trading
- Large-scale microservice decomposition
- Automatic investment execution

These may become future extensions.

---

# 49. Future Roadmap

The phases below group capabilities **thematically** and do not restate the
MVP delivery sequence. Where this section and section 47 disagree on ordering,
**section 47 governs MVP sequencing**. In particular, Watchlist and Portfolio
are delivered together as MVP-5 even though this roadmap discusses them under
different themes.

## Phase 1 — Core Platform

```text
Market Data
Stock Analysis
Technical Analysis
Fundamental Analysis
Screener
Watchlist
```

## Phase 2 — Strategy

```text
Strategy Engine
Signal Engine
Risk Engine
Backtesting
```

## Phase 3 — AI

```text
AI Analyst
RAG
Financial Report Q&A
News Intelligence
AI Explanation
```

## Phase 4 — Personalization

```text
Portfolio
Investment Journal
Personalized Alerts
Personalized AI Analysis
```

## Phase 5 — Advanced Platform

```text
Mobile Application
Advanced ML
Event-driven architecture
Additional market data
Broker integration
```

---

# 50. Architectural Principles

The system shall follow these principles.

### Principle 1 — Deterministic Analysis First

Indicators, calculations, risk rules and strategy conditions should be generated by deterministic engines.

```text
Data → Calculation → Signal
```

The LLM should not independently calculate critical financial metrics.

### Principle 2 — AI as Intelligence Layer

AI should be responsible for:

- Explanation
- Summarization
- Retrieval
- Natural-language interaction
- Correlation of multiple evidence sources
- Research assistance

### Principle 3 — Evidence-Based AI

AI-generated explanations should be grounded in:

```text
Structured Data
+
Quantitative Analysis
+
News
+
Research Documents
```

### Principle 4 — Clear Data Ownership

```text
PostgreSQL
→ Transactional source of truth

Qdrant
→ Vector retrieval index

Redis
→ Cache
```

### Principle 5 — Modular Monolith First

The core Spring Boot application should begin as a modular monolith and evolve into microservices only when scaling or domain boundaries justify the separation.

### Principle 6 — AI as Independent Service

The AI layer should remain independently deployable:

```text
Spring Boot
    +
Python FastAPI AI Service
```

This enables independent development, scaling and experimentation.

---

# 51. Example End-to-End AI Analysis Flow

User:

```text
"FPT hiện tại có đáng theo dõi không?
Phân tích kỹ thuật, cơ bản và tin tức."
```

System:

```text
Next.js
   ↓
Spring Boot
   ↓
AI Service
   ↓
AI Orchestrator
   │
   ├── Stock Tool
   ├── Technical Tool
   ├── Fundamental Tool
   ├── Valuation Tool
   ├── News Tool
   └── RAG Tool
   │
   ├── PostgreSQL
   ├── Qdrant
   └── LLM
   ↓
Evidence-based Analysis
   ↓
Spring Boot
   ↓
Next.js
```

The final response should contain:

```text
Market Context
Technical Analysis
Fundamental Analysis
Valuation
Recent News
Key Risks
Supporting Evidence
AI Explanation
```

---

# 52. Example Signal Generation Flow

```text
Market Data
     ↓
Technical Engine
     ↓
Fundamental Engine
     ↓
Risk Engine
     ↓
Strategy Engine
     ↓
Signal
     ↓
Evidence
     ↓
AI Explanation
```

Example:

```text
Symbol: FPT

Strategy:
Momentum

Signal:
WATCH / POTENTIAL LONG

Entry Zone:
...

Stop Loss:
...

Target:
...

Risk:
MEDIUM

Evidence:
+ Price above MA50
+ MACD bullish
+ Volume expansion
+ Strong sector momentum

AI Explanation:
...
```

---

# 53. Product Positioning

The product should be positioned as:

**AI-powered Investment Research & Decision Support Platform**

rather than:

**AI Trading Bot**

The central value proposition is:

> Help users analyze the Vietnamese stock market faster, understand why a stock or strategy receives a particular assessment, discover relevant information, and make better-informed investment decisions.

---

# 54. MVP Success Criteria

Each criterion below is measurable and verifiable from the user's perspective.
The numbering is stable: criterion *N* keeps its meaning across revisions, so a
feature specification may cite "section 54 criterion N" as a durable reference.

A feature specification shall restate the criteria it delivers as its own
`SC-` requirements with concrete fixtures and thresholds. These are product-level
outcomes, not a substitute for feature-level acceptance.

| # | ID | Capability | Measurable criterion |
|---|---|---|---|
| 1 | MVP-SC-01 | Open the market dashboard | The user identifies index direction, session status, and as-of time for every supported index within 10 seconds, in 3 consecutive timed trials |
| 2 | MVP-SC-02 | Understand the current market regime | 100% of published regime assessments show a label, score, confidence, as-of time, rule version, and supporting factors, and are reproducible from their recorded inputs |
| 3 | MVP-SC-03 | Search for a Vietnamese stock | 95% of lookups of a supported symbol return its detail view within 3 seconds; an unsupported symbol returns an explicit not-found state with no fabricated data |
| 4 | MVP-SC-04 | View technical and fundamental analysis | 100% of displayed indicator and fundamental values match the accepted source to the declared precision, each carrying its calculation window, rule version, and as-of time |
| 5 | MVP-SC-05 | Screen stocks using multiple conditions | A screen combining at least 3 filters returns a result set that reconciles exactly with the same filters applied to the accepted source data, with 0 duplicated or silently dropped securities |
| 6 | MVP-SC-06 | Review a strategy-generated signal | 100% of signals expose their strategy, rule version, triggering conditions, and supporting evidence, and are reproducible from their recorded inputs |
| 7 | MVP-SC-07 | Understand entry, stop-loss, target and risk assumptions | 100% of signals state entry zone, stop loss, target, risk/reward, and the assumptions behind them, and are labelled as scenarios rather than guarantees |
| 8 | MVP-SC-08 | Read related news | 100% of displayed articles carry source identity, publication time, and the basis for their stock or sector association |
| 9 | MVP-SC-09 | Ask AI questions about a stock | 95% of answers begin returning within 15 seconds, and 100% cite the structured facts or documents they rely on |
| 10 | MVP-SC-10 | Ask questions about financial reports using RAG | 100% of document-derived answers carry a resolvable citation identifying document and location; unsupported questions produce an explicit refusal rather than an ungrounded answer |
| 11 | MVP-SC-11 | Receive explanations grounded in evidence | 0 answers assert a market fact or calculation absent from the retrieved evidence, measured on a versioned evaluation dataset |
| 12 | MVP-SC-12 | Track a personal watchlist and portfolio | 100% of watchlist and portfolio resources are readable and writable only by their owner, with 0 successful cross-user accesses in authorization tests |

Across every criterion, the MVP shall also demonstrate that:

- the P1 journeys of each delivered capability remain usable while all AI
  capabilities are unavailable;
- 100% of stale, partial, corrected, and unavailable data scenarios show the
  correct data-quality state and never fabricate a fact, count, label, or
  confidence value;
- no response, log, export, or client bundle contains a provider credential,
  token, or raw provider payload.

---

# 55. Final Architecture Summary

```text
                            USER
                              │
                              ▼
                     ┌────────────────┐
                     │    Next.js     │
                     │   TypeScript   │
                     └───────┬────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │   Spring Boot Core   │
                  │   Modular Monolith   │
                  │                      │
                  │ Auth / User          │
                  │ Market               │
                  │ Stock                │
                  │ Technical            │
                  │ Fundamental          │
                  │ Screener             │
                  │ Strategy             │
                  │ Signal               │
                  │ Risk                 │
                  │ Portfolio            │
                  │ Watchlist             │
                  │ News                 │
                  │ Alert                │
                  └──────────┬───────────┘
                             │
                           REST 
                             │
                             ▼
                  ┌──────────────────────┐
                  │   Python FastAPI     │
                  │      AI Service      │
                  │                      │
                  │ AI Orchestrator      │
                  │ LLM                  │
                  │ RAG                  │
                  │ Embeddings           │
                  │ NLP                  │
                  │ Document Processing  │
                  └──────────┬───────────┘
                             │
                  ┌──────────┴──────────┐
                  ▼                     ▼
            ┌──────────┐          ┌───────────┐
            │ Qdrant   │          │    LLM    │
            │ Vector DB│          │ Provider  │
            └──────────┘          └───────────┘

                  ┌──────────────────────┐
                  │     PostgreSQL       │
                  │ Transactional Data   │
                  └──────────────────────┘

                  ┌──────────────────────┐
                  │        Redis         │
                  │ Cache / Temporary    │
                  └──────────────────────┘

                  ┌──────────────────────┐
                  │      Kafka        │
                  │ Async Events / Jobs  │
                  └──────────────────────┘
```

# 56. Key Design Decision

The current architecture is intentionally:

```text
Spring Boot Modular Monolith
             +
Python AI Microservice
             +
PostgreSQL
             +
Qdrant
             +
Redis
             +
Optional Kafka
```

rather than a full microservice architecture.

This provides a practical balance between:

- maintainability
- development speed
- clear domain boundaries
- AI-specific infrastructure
- future scalability
- suitability for a portfolio/production-oriented project

The system can later evolve into independently deployed Market Data, AI, Document Processing and Backtesting services without changing the fundamental product architecture.

---

# 57. Superseding Architecture Decisions

This section is a pointer index, not a change of product intent. The baseline
choices recorded earlier in this SRS remain the original product statement;
where an accepted Architecture Decision Record (ADR) refines or replaces one,
**the ADR governs the engineering decision** while this SRS continues to govern
product scope and intent.

Nothing in this section waives a constitutional principle. Read it together
with `.specify/memory/constitution.md` and `docs/PROJECT_CONTEXT.md`.

| SRS baseline | Section(s) | Superseded by | Current decision |
|---|---|---|---|
| Next.js frontend | Header, 37, 44, 45, 51, 55 | [ADR-0006](adr/0006-use-react-vite-for-private-web-client.md) | React SPA built with Vite; Spring Boot remains the auth and public API boundary |
| JWT-based authentication and token refresh | 4.1, 36.4 | [ADR-0005](adr/0005-use-tailscale-and-local-owner-session.md) | Rotated server-side session with a `Secure`/`HttpOnly`/`SameSite=Strict` cookie and CSRF validation for the private owner deployment |
| Open user registration | 4.1 | [ADR-0005](adr/0005-use-tailscale-and-local-owner-session.md) | Single configured owner identity; self-registration, invitations, and shared links are denied while provider licensing remains private-use only |
| AWS deployment | 44 | [ADR-0005](adr/0005-use-tailscale-and-local-owner-session.md) | Private owner-only deployment reached through a Tailscale tailnet with Funnel disabled; no public ingress |
| Generic "Vietnamese stock market data provider" | 41 | [ADR-0003](adr/0003-use-tcbs-for-private-market-data-v1.md), [ADR-0004](adr/0004-use-vnstock-for-private-historical-bootstrap.md) | TCBS iFlash as the read-only live source and Vnstock as an offline historical bootstrap tool, both restricted to private single-owner use |
| Unspecified LLM provider | 41 | [ADR-0002](adr/0002-use-gemini-as-initial-llm-provider.md) | Gemini as the initial provider behind a replaceable adapter |
| Spring Boot version unpinned beyond 4.1.x | 44 | [ADR-0001](adr/0001-use-spring-boot-4.md) | Java 21 with the verified Spring Boot 4.1.x pin recorded in the committed manifests |
| Module structure without an internal layering rule | 38 | [ADR-0007](adr/0007-use-layered-architecture-within-backend-modules.md) | Layered packages (`controller`, `dto`, `service`, `repository`, `entity`) inside each business module, plus optional `domain`, `provider`, and `config` |

## Cross-cutting constraints

Sections 36.4 through 36.8 state the product-level obligations for security,
observability, accessibility, data provenance and temporal integrity, and
privacy, retention, and data rights. They are baselines, not full engineering
rules.

The detailed, non-negotiable engineering rules behind them live in
`.specify/memory/constitution.md`, and each feature makes them concrete under
`specs/<feature>/`. Where a feature needs a value this SRS does not fix — a
specific freshness threshold, a retention period, a provider's data-usage
rights, an exchange calendar, or a corporate-action source — it shall resolve
that value in its own research and plan artifacts and record the decision.

A feature shall not infer permission from this document's silence.

---

# 58. Requirements Index

## Purpose and namespace

This index assigns a stable identifier to each capability this SRS defines, so
a feature specification can cite product intent precisely and survive future
section renumbering.

These `SRS-` identifiers live in their own namespace. They are **not** the same
as the feature-level `FR-`, `NFR-`, `DATA-`, `SEC-`, `AI-`, and `SC-` prefixes
defined in `docs/SDD_WORKFLOW.md`, which are scoped to a single
`specs/<feature>/` directory and numbered independently there. A feature
specification should record the `SRS-` identifiers it realizes in its
**SRS References** header, and then define its own `FR-`/`NFR-` requirements
with testable detail.

Identifiers are stable. A capability that is removed shall be marked deprecated
with a reason rather than renumbered or reused.

## Foundation

| ID | Section | Capability | MVP |
|---|---|---|---|
| SRS-AUTH-01 | 4.1 | User registration, login, logout, and session or token lifecycle | Foundational enabler |
| SRS-AUTH-02 | 4.1 | Protection of user-scoped resources by owner | Foundational enabler |

## Market intelligence

| ID | Section | Capability | MVP |
|---|---|---|---|
| SRS-MKT-01 | 5.1 | Market overview for the supported benchmark indices | MVP-1 |
| SRS-MKT-02 | 5.1 | Consolidated market breadth (advancing, declining, unchanged) | MVP-1 |
| SRS-MKT-03 | 5.2 | Sector performance, momentum, liquidity, and relative strength | Post-MVP |
| SRS-MKT-04 | 5.2 | Leading and weak stock lists per sector | Post-MVP |
| SRS-MKT-05 | 5.3 | Deterministic market regime classification with score, confidence, and supporting factors | MVP-1 |

## Stock analysis

| ID | Section | Capability | MVP |
|---|---|---|---|
| SRS-STK-01 | 6.1 | Per-stock overview: identity, price, change, capitalization, sector, volume | MVP-2 |
| SRS-STK-02 | 6.1 | Overall stock score, risk, trend, and valuation classification on the stock page | MVP-2 (valuation, trend) / Post-MVP (composite score) |
| SRS-STK-03 | 6.2 | Stock page sections: Overview, Technical, Fundamental, Valuation, Financials, News, Research, AI Analysis | MVP-2 (first four) / MVP-6 and MVP-7 (remainder) |
| SRS-TEC-01 | 7.1 | Trend indicators (SMA, EMA, MA20/50/200) | MVP-2 |
| SRS-TEC-02 | 7.2 | Momentum indicators (RSI, MACD, Stochastic) | MVP-2 |
| SRS-TEC-03 | 7.3 | Volatility indicators (Bollinger Bands, ATR) | MVP-2 |
| SRS-TEC-04 | 7.4 | Volume analysis (average, relative, spike, trend) | MVP-2 |
| SRS-TEC-05 | 7.5 | Price structure: support, resistance, breakout, breakdown, Fibonacci | Post-MVP |
| SRS-TEC-06 | 7.6 | Candlestick pattern identification | Post-MVP |
| SRS-TEC-07 | 8 | Multi-timeframe evaluation and alignment summary | Post-MVP |
| SRS-FUN-01 | 9.1 | Fundamental financial metrics for the latest accepted reporting period | MVP-2 |
| SRS-VAL-01 | 10 | Valuation metrics (P/E, P/B, EV/EBITDA, PEG, dividend yield) | MVP-2 |
| SRS-VAL-02 | 10 | Valuation comparison against history, sector, peers, and market | MVP-2 (history, sector) / Post-MVP (selected peers) |
| SRS-CMP-01 | 11 | Multi-company peer comparison, tabular and graphical | Post-MVP |
| SRS-SCO-01 | 12 | Multi-factor stock score with exposed contributing factors | Post-MVP |

## Screening

| ID | Section | Capability | MVP |
|---|---|---|---|
| SRS-SCR-01 | 13 | Deterministic screening across market, price, technical, and fundamental filters | MVP-3 |
| SRS-SCR-02 | 13 | Combination of multiple filters in one screen | MVP-3 |
| SRS-SCR-03 | 14 | Natural-language to structured-filter conversion, executed by the deterministic engine | MVP-7 |

## Strategy, signal, and risk

| ID | Section | Capability | MVP |
|---|---|---|---|
| SRS-STR-01 | 15 | Configurable strategies with entry, exit, risk rules, and timeframe | MVP-4 |
| SRS-SIG-01 | 16 | Signal generation with direction, levels, risk/reward, and supporting evidence | MVP-4 |
| SRS-SIG-02 | 16 | Signals presented as scenarios rather than guarantees | MVP-4 |
| SRS-RSK-01 | 17 | Trade and portfolio risk scoring with named risk factors | MVP-4 |
| SRS-RSK-02 | 18 | Deterministic, transparent position sizing (optional capability) | MVP-4 |
| SRS-BKT-01 | 19 | Historical backtesting with configurable cost and sizing assumptions | Post-MVP |
| SRS-BKT-02 | 19 | Look-ahead, survivorship, corporate-action, slippage, and cost handling | Post-MVP |

## Portfolio and personalization

| ID | Section | Capability | MVP |
|---|---|---|---|
| SRS-PF-01 | 20 | Portfolio holdings, positions, cash, and realized/unrealized P/L | MVP-5 |
| SRS-PF-02 | 21 | Portfolio analytics, concentration, and benchmark comparison | MVP-5 |
| SRS-WL-01 | 22 | Watchlist creation and per-item market and analysis context | MVP-5 |
| SRS-JRN-01 | 23 | Investment journal entries and later personalized analytics | Post-MVP |
| SRS-ALR-01 | 35 | Configurable alerts and supported delivery channels | Post-MVP |

## News, documents, and retrieval

| ID | Section | Capability | MVP |
|---|---|---|---|
| SRS-NWS-01 | 24 | News aggregation, normalization, and categorization | MVP-6 |
| SRS-NWS-02 | 25 | Entity, sentiment, and impact classification over news | MVP-6 |
| SRS-DOC-01 | 26 | Research document ingestion with structured metadata | MVP-6 |
| SRS-RAG-01 | 27 | Document processing pipeline through to embedding | MVP-6 |
| SRS-RAG-02 | 28 | Vector index storing embeddings and retrieval metadata, never as source of truth | MVP-6 |
| SRS-RAG-03 | 29 | Retrieval pipeline with filtering, reranking, and citable source metadata | MVP-6 |

## AI capabilities

| ID | Section | Capability | MVP |
|---|---|---|---|
| SRS-AIA-01 | 30 | AI Analyst answering investment research questions | MVP-7 |
| SRS-AIA-02 | 31 | Tool-selecting orchestration over allowlisted capabilities | MVP-7 |
| SRS-AIA-03 | 32 | AI explanation of deterministic outputs without replacing the calculation | MVP-7 |
| SRS-AIA-04 | 33 | Separation of structured-data queries from document retrieval | MVP-7 |
| SRS-AIA-05 | 34 | Daily market briefing (optional capability) | Post-MVP |

## Cross-cutting

| ID | Section | Capability |
|---|---|---|
| SRS-NFR-01 | 36.1 | Performance baselines for read views, updates, screening, and AI responses |
| SRS-NFR-02 | 36.2 | Architectural evolution without redesign |
| SRS-NFR-03 | 36.3 | Availability and graceful degradation during dependency outage |
| SRS-NFR-04 | 36.4 | Security, authorization, untrusted-content handling, and minimal data exposure |
| SRS-NFR-05 | 36.5 | Observability and failure-class distinguishability |
| SRS-NFR-06 | 36.6 | Accessibility and Vietnamese locale formatting |
| SRS-NFR-07 | 36.7 | Data quality, provenance, precision, and temporal integrity |
| SRS-NFR-08 | 36.8 | Privacy, retention, and provider data rights |
