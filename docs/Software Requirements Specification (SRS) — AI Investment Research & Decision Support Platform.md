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
Messaging: Kafka
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

## 1.2 Scope

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

The calculation shall be deterministic and transparent.

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

The system shall optionally generate an AI-powered daily market briefing.

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

## 36.1 Performance

The system should provide:

- Fast dashboard response
- Cached market data where appropriate
- Asynchronous processing for heavy AI/document workloads
- Efficient historical data querying

LLM and document-processing operations should not block ordinary transactional operations unnecessarily.

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

Core market-data and portfolio functionality should remain available even when external LLM providers are unavailable.

AI functionality should degrade gracefully.

---

## 36.4 Security

The system shall implement:

- Authentication
- Authorization
- JWT
- Secure token handling
- Input validation
- API protection
- Rate limiting where required
- Secure secret management

AI services shall not expose private user information unnecessarily.

---

## 36.5 Observability

The system should support:

- Structured logging
- Application metrics
- Error tracking
- Health checks
- AI request monitoring
- External API monitoring

Potential technologies:

```text
Spring Actuator
Prometheus
Grafana
```

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

- Gemini or another LLM provider

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

Kafka may be introduced initially.

Kafka may be considered later if event volume and streaming requirements justify it.

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
Spring Boot 3
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
Kafka
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

The MVP should demonstrate that a user can:

1. Open the market dashboard.
2. Understand the current market regime.
3. Search for a Vietnamese stock.
4. View technical and fundamental analysis.
5. Screen stocks using multiple conditions.
6. Review a strategy-generated signal.
7. Understand entry, stop-loss, target and risk assumptions.
8. Read related news.
9. Ask AI questions about a stock.
10. Ask questions about financial reports using RAG.
11. Receive explanations grounded in structured market data and source documents.
12. Track a personal watchlist and portfolio.

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