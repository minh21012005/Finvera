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
Gemini (LLM) / embedding provider
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

- **Resolved — backend platform**: Finvera uses Java 21 and Spring Boot 4.1.x;
  the current verified pin is 4.1.0. Only verified generally available 4.1.x
  patches may be adopted. See [ADR-0001](adr/0001-use-spring-boot-4.md).
- Kafka is described both as available and optional/later. It is not a default
  dependency for designs; a feature must justify its use with volume, ordering,
  replay, or decoupling requirements.
- **Resolved for Feature 1 private v1 — market-data provider**: TCBS iFlash is
  the read-only live provider for one owner-only/private deployment; Vnstock is
  a conditional offline historical-bootstrap tool, not a runtime service.
  Public or multi-user delivery requires a separately licensed provider and
  ADR. See [ADR-0003](adr/0003-use-tcbs-for-private-market-data-v1.md) and
  [ADR-0004](adr/0004-use-vnstock-for-private-historical-bootstrap.md).
- **Resolved for Feature 1 private v1 — owner access**: Tailscale Serve/private
  tailnet is the only ingress, with Funnel disabled; Spring independently
  authenticates one configured local owner through a secure server session.
  See [ADR-0005](adr/0005-use-tailscale-and-local-owner-session.md).
- Data licensing beyond the private TCBS use, update latency, exchange calendar,
  adjusted-price policy, and corporate-action source are unresolved. The first
  market-data feature must research and contract them.
- **Resolved — initial LLM provider**: Gemini is the initial LLM provider;
  model/version selection, privacy review, quotas, cost, and fallback belong to
  the first AI feature plan. See [ADR-0002](adr/0002-use-gemini-as-initial-llm-provider.md).
- Embedding model/provider, reranking, and document storage remain unresolved.
  The first RAG feature plan must benchmark and select them independently of the
  Gemini decision.
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
