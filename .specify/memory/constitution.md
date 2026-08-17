<!--
Sync Impact Report
- Version change: 1.0.0 -> 1.0.1
- Modified principles: none.
- Clarified section: Product and Technology Constraints now identifies the
  frontend as a React SPA built with Vite, per ADR-0006.
- Added sections: none.
- Removed sections: none.
- Templates reviewed: plan, spec, and tasks templates require no framework-
  specific change.
- Runtime guidance updated: AGENTS.md, finvera-fe/AGENTS.md,
  docs/PROJECT_CONTEXT.md, and Feature 001 design artifacts.
- Follow-up TODOs: none.
-->

# Finvera Constitution

## Core Principles

### I. Deterministic Finance Core

All indicators, ratios, scores, screen filters, strategy conditions, signals,
risk measures, position sizes, portfolio metrics, and backtest results MUST be
calculated by deterministic, versioned code. Every such result MUST expose its
inputs, formula/rule version, assumptions, units, and contributing factors at a
level sufficient for reproduction. Monetary and precision-sensitive operations
MUST use declared decimal precision and rounding; binary floating point is
forbidden for authoritative financial values.

An LLM MAY explain or summarize a deterministic result but MUST NOT originate,
silently alter, or replace it. This separation is non-negotiable because users
must be able to inspect why a scenario exists and reproduce critical outputs.

### II. Evidence, Provenance, and Temporal Truth

Every market fact and AI assertion MUST be traceable to structured data or a
retrievable document source. Relevant records MUST preserve source identity,
observation/effective/ingestion times, timezone, unit, currency, exchange, and
adjustment status. Document-derived answers MUST cite stable document and
location metadata when available. Conflicting, missing, delayed, stale, or
estimated data MUST be surfaced rather than hidden.

Vector retrieval and cache entries are derived data and MUST be rebuildable from
their sources of truth. No design may allow Qdrant, Redis, or an LLM transcript
to become the only authoritative copy of business data.

### III. Explicit Service and Data Boundaries

Spring Boot MUST remain the public API and authorization boundary. The browser
MUST NOT invoke the internal Python AI service or infrastructure stores
directly. PostgreSQL is the transactional source of truth, Qdrant is the vector
retrieval index, Redis is cache/ephemeral state, and Kafka is an optional
transport used only when a planned feature demonstrates a concrete need.

The Spring application MUST remain a domain-oriented modular monolith until
measured scaling, availability, ownership, or deployment constraints justify
extraction through an ADR. Backend modules MUST collaborate through explicit
application interfaces or documented events, never by reaching into another
module's repositories or tables. Public APIs, inter-service APIs, and events
MUST have versioned, testable contracts.

### IV. Security, Privacy, and Responsible Decision Support

Authentication, authorization, object ownership, input validation, secret
management, rate limiting where appropriate, and least privilege MUST be
enforced at server trust boundaries. User portfolios, journals, alerts,
watchlists, conversations, and documents are private by default. Sensitive data,
credentials, tokens, and private prompt content MUST NOT appear in source,
client bundles, telemetry, or logs.

Retrieved text and external content MUST be treated as untrusted data and MUST
NOT authorize tools or override system policy. AI outputs MUST use calibrated
language, identify material assumptions and risks, and MUST NOT claim guaranteed
returns, autonomous execution, or personalized fiduciary authority. Finvera is
research and decision support, not an investment guarantee or trading bot.

### V. Specification and Traceability Before Code

Every feature MUST begin with a specification that defines prioritized,
independently testable user journeys, explicit scope, acceptance scenarios,
edge/failure cases, measurable outcomes, assumptions, and stable requirement
IDs. A plan MUST resolve technical unknowns, define data and interface contracts,
pass the Constitution Check, and identify migrations, security controls,
observability, rollout, and validation before production implementation begins.

Tasks, code, tests, contracts, and delivered behavior MUST remain traceable to
requirements. Unresolved `[NEEDS CLARIFICATION]` markers block implementation.
If discovery changes expected behavior, the spec and plan MUST be amended before
or with the code; undocumented behavior is not an acceptable specification.

### VI. Risk-Based Testing and Reproducibility

Tests are mandatory for business behavior and MUST be proportional to harm and
uncertainty. Deterministic finance logic requires unit plus boundary/property
tests with fixed fixtures. Authorization, persistence, migrations, retrieval,
and module boundaries require integration tests. External and inter-service
interfaces require contract tests. P1 user journeys require end-to-end
validation when their participating services are available.

AI functionality MUST be evaluated on versioned datasets for retrieval quality,
citation validity, groundedness, structured-output validity, refusal behavior,
and prompt-injection/tool-abuse resistance. Backtests MUST prevent look-ahead
bias, declare transaction cost/slippage assumptions, and document survivorship
and corporate-action handling. Tests MUST be deterministic unless their plan
explicitly defines statistical tolerances and seeded evaluation conditions.

### VII. Resilience and Observability

Core authentication, market, and portfolio capabilities MUST remain usable when
LLM, embedding, Qdrant, news, notification, or market-provider dependencies are
degraded, wherever the feature contract permits. Every external call MUST have
explicit timeouts, bounded retry for safe operations, cancellation, and a
defined failure/fallback path. Heavy AI and document work MUST NOT block normal
transactional request processing.

New boundaries MUST emit structured, privacy-safe logs, metrics, health signals,
and correlation identifiers sufficient to diagnose latency, errors, staleness,
and dependency health. Observability MUST NOT capture secrets, tokens, raw PII,
or private financial/document payloads.

### VIII. Modular Simplicity

Implement the smallest coherent vertical slice that satisfies an approved user
journey. New services, brokers, databases, frameworks, abstractions, predictive
models, and background infrastructure MUST have a current requirement and a
documented simpler alternative. Automated order execution, autonomous trading,
mobile applications, high-frequency trading, and large-scale microservice
decomposition remain out of MVP scope unless the constitution and SRS are
explicitly amended.

Simplicity does not excuse boundary violations or missing controls. It means
choosing the least complex design that still satisfies correctness, security,
operability, and testability.

## Product and Technology Constraints

- Product focus: Vietnamese equity research and decision support.
- Delivery shape: React SPA built with Vite, Spring Boot modular monolith, and
  an independently deployable internal FastAPI AI service. SSR or another
  frontend runtime requires an approved feature need and ADR.
- Current runtime/dependency versions are those pinned by committed manifests
  and lockfiles. A version change requires research, compatibility validation,
  and inclusion in the feature plan.
- Spring Boot authorizes all user-facing requests and calls the AI service over
  a versioned internal API; frontend-to-AI calls are prohibited.
- PostgreSQL owns transactional entities and document metadata. Redis contents
  and Qdrant collections MUST be disposable/reconstructable.
- Market-facing dates use explicit Vietnamese market time
  (`Asia/Ho_Chi_Minh`); transport and storage use UTC unless a contract records
  another deliberate representation.
- Data-provider, exchange-calendar, corporate-action, licensing, retention, and
  regulatory assumptions MUST be resolved in feature research rather than
  guessed in code.
- User-facing output MUST distinguish observed facts, deterministic analyses,
  scenarios, AI interpretation, and unavailable information.

## Delivery Gates

### Specification Gate

A feature cannot enter planning until its P1 journey is independently testable,
scope and exclusions are explicit, requirements and outcomes are measurable,
data freshness/provenance needs are stated, and no material ambiguity remains.

### Design Gate

A feature cannot enter implementation until the plan contains:

- a passed Constitution Check;
- resolved research decisions and alternatives;
- service/module ownership and data flow;
- versioned API/event/tool contracts where applicable;
- data model, precision, time, provenance, migration, and retention decisions;
- a threat/privacy review and AI abuse analysis where applicable;
- a test/evaluation strategy mapped to requirement IDs;
- observability, dependency failure, rollout, rollback, and compatibility plans.

### Implementation Gate

Tasks MUST be executable, ordered, mapped to user stories and requirement IDs,
and include tests before or alongside behavior. A task cannot be marked complete
without its stated verification evidence.

### Release Gate

Relevant automated checks and acceptance scenarios MUST pass. Contracts and
documentation MUST match delivered behavior, migrations and rollback MUST be
reviewed, no secret or unresolved placeholder may remain, and known limitations
MUST be visible to users or operators where they affect decisions.

## Governance

This constitution supersedes conflicting repository guidance. The SRS defines
product intent; approved specs and ADRs refine it but cannot waive constitutional
principles silently.

Amendments require:

1. A written proposal explaining the change and rationale.
2. An impact report covering templates, active specs, code, data, security, and
   migration needs.
3. Explicit maintainer approval before dependent implementation.
4. Updates to all affected guidance and templates in the same change.

Constitution versions follow semantic versioning:

- MAJOR for removal or incompatible redefinition of a principle/governance rule.
- MINOR for a new principle, gate, or materially expanded obligation.
- PATCH for clarification that does not change required behavior.

Every feature plan MUST evaluate compliance before research and again after
design. Any exception MUST be explicit, narrowly scoped, time-bounded when
possible, and recorded in Complexity Tracking or an ADR with an owner and
remediation plan. Reviewers MUST reject undocumented exceptions.

**Version**: 1.0.1 | **Ratified**: 2026-08-17 | **Last Amended**: 2026-08-17
