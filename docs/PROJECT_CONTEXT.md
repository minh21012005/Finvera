# Finvera Project Context

## Product Boundary

Finvera is a web platform for researching the Vietnamese stock market and
supporting user decisions. It combines deterministic quantitative analysis with
AI-assisted retrieval and explanation. The MVP does not place broker orders,
trade autonomously, provide high-frequency trading, or promise returns.

The intended user journey is:

```text
Market -> Stock -> Analysis -> Strategy -> Risk -> Evidence -> User decision
```

## System Map

```text
Browser (Next.js)
        |
        v
Spring Boot modular monolith ----------------> PostgreSQL
        |                                      transactional truth
        |-------------------------------> Redis
        |                                  cache / ephemeral state
        |
        v internal versioned API
FastAPI AI service --------------------------> Qdrant
        |                                      retrieval index
        v
LLM / embedding providers
```

Spring Boot is the only public backend boundary. It authorizes requests and owns
transactional/domain workflows. FastAPI remains internal and owns AI-specific
capabilities. Qdrant and Redis contain derived data and cannot be authoritative.

## Capability Ownership

| Capability | Primary owner | AI role |
|---|---|---|
| Auth, users, portfolios, watchlists, alerts | Spring Boot | None or explanation only |
| Market, stock, fundamental, valuation data | Spring Boot | Summarize returned facts |
| Indicators, scores, screeners, strategies | Spring Boot deterministic engine | Explain factors |
| Signals, risk, sizing, backtests | Spring Boot deterministic engine | Explain assumptions and evidence |
| Document ingestion and retrieval | FastAPI + source metadata in PostgreSQL | Parse, embed, retrieve, rerank |
| News/document interpretation | FastAPI | Extract, classify, summarize with citations |
| AI analyst orchestration | FastAPI behind Spring Boot | Select allowlisted tools and synthesize evidence |

## MVP Delivery Order

The SRS lists seven MVP capabilities. Implement them as independently testable
vertical slices rather than building every infrastructure layer up front:

1. Market dashboard and regime evidence.
2. Stock detail with technical, fundamental, and valuation evidence.
3. Deterministic stock screener.
4. Strategy, signal, and risk scenarios.
5. Portfolio and watchlist.
6. News and financial-report RAG with citations.
7. AI analyst grounded in structured tools plus retrieval.

Authentication, data ingestion, data quality, observability, and security are
foundational enablers and belong in the first feature that needs them.

## Source-of-Truth Rules

| Question | Authority |
|---|---|
| Product vision, MVP, exclusions | SRS |
| Non-negotiable engineering rules | `.specify/memory/constitution.md` |
| Feature behavior and acceptance | Approved `specs/<feature>/spec.md` |
| Technical design and contracts | Feature plan/artifacts and accepted ADRs |
| Installed versions | Committed manifests and lockfiles |
| Runtime behavior | Tests and code, unless they conflict with an approved spec |

Conflicts must be documented and resolved; precedence is not permission to hide
an inconsistency.

## Known Baseline Decisions and Gaps

- The SRS names Spring Boot 3, while `finvera-be/pom.xml` currently pins Spring
  Boot 4.1.0. Treat the manifest as the current build baseline and decide any
  version change through a researched feature/ADR.
- Kafka is described both as available and optional/later. It is not a default
  dependency for designs; a feature must justify its use with volume, ordering,
  replay, or decoupling requirements.
- The market-data provider, data licensing, update latency, exchange calendar,
  adjusted-price policy, and corporate-action source are unresolved. The first
  market-data feature must research and contract them.
- LLM, embedding, reranking, and document storage providers are unresolved.
  Provider selection belongs to the first AI/RAG feature plan.
- Performance, retention, RPO/RTO, rate limits, AI quotas, and model-quality
  thresholds need measurable values in the features that depend on them.
- The SRS file contains mojibake in some arrows/box drawing and Vietnamese text.
  Treat meaning carefully and fix encoding in a dedicated documentation change.

## Domain Safety Vocabulary

Use language that distinguishes these concepts:

- **Fact**: observed sourced data with an as-of time.
- **Metric**: deterministic calculation from declared inputs.
- **Signal/scenario**: rule output with assumptions, not a prediction guarantee.
- **Evidence**: facts/documents supporting an analysis.
- **AI explanation**: grounded interpretation of evidence, not authoritative
  financial computation.
- **Confidence**: calibrated model/engine uncertainty with a defined method; it
  must not be a decorative percentage.

