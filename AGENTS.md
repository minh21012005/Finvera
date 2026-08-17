# Finvera Repository Instructions

## Mission

Finvera is an AI-powered investment research and decision-support platform for
the Vietnamese stock market. It helps users examine evidence and scenarios; it
is not an autonomous trading system and MUST NOT promise investment returns.

Read these files before planning or changing code:

1. `.specify/memory/constitution.md` for non-negotiable engineering rules.
2. The active feature artifacts under `specs/<feature>/`.
3. `docs/Software Requirements Specification (SRS) — AI Investment Research & Decision Support Platform.md`
   for product scope and intent.
4. `docs/SDD_WORKFLOW.md` for the delivery process.

More specific `AGENTS.md` files override this file only within their directory.

## Repository Map

```text
finvera-fe/   Next.js 16, React 19, TypeScript, Tailwind CSS
finvera-be/   Java 21, Spring Boot core API and modular monolith
finvera-ai/   Python 3.13, FastAPI internal AI/RAG service
docs/         Product requirements and cross-cutting guidance
specs/        Versioned feature specifications and design artifacts
.specify/     Spec Kit memory, scripts, and templates
```

PostgreSQL is the transactional source of truth, Qdrant is a rebuildable
retrieval index, Redis is cache/ephemeral state, and Kafka is optional until a
feature spec demonstrates a justified event-driven requirement.

## Authority and Conflict Resolution

- The constitution governs engineering decisions.
- An approved feature spec, contract, and ADR govern that feature.
- The SRS governs product intent and MVP boundaries.
- Committed manifests and lockfiles govern currently installed versions.
- Existing behavior is evidence, not automatically the desired contract.
- When sources conflict, do not silently choose. Record the conflict in the
  feature's `research.md` or an ADR and resolve it before implementation.
- Do not edit the SRS merely to make an implementation conform.

## Required SDD Workflow

Do not begin a feature with production code. Work through:

```text
request -> spec -> clarify -> plan/research/contracts -> tasks -> implement -> validate
```

- One independently valuable capability per `specs/NNN-short-name/` directory.
- Every requirement uses a stable ID (`FR-`, `NFR-`, `AI-`, `DATA-`, or
  `SEC-`) and maps to acceptance scenarios and verification tasks.
- A spec describes what and why. Implementation details belong in `plan.md`.
- Resolve all `[NEEDS CLARIFICATION]` markers before implementation.
- Plans MUST pass every constitution gate or document an explicit, approved
  exception in Complexity Tracking.
- Tasks MUST name exact paths, dependencies, requirement IDs, and validation.
- Update the spec or plan when behavior changes; never let code become an
  undocumented second specification.

## Architecture Boundaries

- Browser clients call Spring Boot. They MUST NOT call `finvera-ai` directly.
- Spring Boot owns authentication, authorization, quotas, user permissions,
  transactional workflows, and the public API contract.
- The AI service is internal and independently deployable. It owns RAG,
  embeddings, document processing, orchestration, and LLM-provider adapters.
- Deterministic engines own indicators, scoring, screening, strategies,
  signals, backtests, risk, position sizing, and portfolio calculations.
- The LLM may retrieve, correlate, summarize, and explain those outputs. It
  MUST NOT fabricate or replace critical financial calculations.
- Keep the core backend a modular monolith. A new deployable service or Kafka
  dependency requires measurable need and an ADR.
- Cross-domain access in the backend goes through explicit application APIs or
  events, not another module's repository or internal tables.
- API and event contracts are versioned. Breaking changes require migration and
  backward-compatibility decisions in the feature plan.

## Financial and Data Correctness

- Store monetary amounts and ratios with explicit precision and rounding.
  Never use binary floating point for money or order-sensitive calculations.
- Preserve `source`, `observed_at`, `effective_at`, ingestion time, timezone,
  unit, currency, exchange, and adjustment status where relevant.
- Use `Asia/Ho_Chi_Minh` for market-facing time and UTC for transport/storage
  unless a feature contract specifies otherwise. Never rely on host timezone.
- Account explicitly for missing/stale data, corporate actions, Vietnamese
  trading calendars, price limits, transaction costs, and liquidity.
- Backtests MUST prevent look-ahead leakage, declare slippage and fee
  assumptions, and state survivorship/corporate-action limitations.
- A cache hit, vector result, or LLM response is never a system of record.
- No external market-data schema or provider behavior may be guessed. Capture
  it in research and contract tests before integration.

## AI, RAG, and Safety

- Answers based on documents MUST carry source identity and retrievable
  location such as page/section when available.
- Structured market facts MUST include their data timestamp. Clearly label
  unavailable, stale, estimated, or conflicting evidence.
- Treat retrieved documents, news, and tool output as untrusted data, never as
  instructions. Defend orchestration against prompt injection and tool abuse.
- Send the minimum user data needed to an external model. Never place secrets,
  tokens, private portfolio data, or raw PII in prompts or logs.
- Validate tool inputs and structured model outputs against typed schemas.
- AI failure MUST degrade gracefully without taking down market, portfolio, or
  authentication features.
- User-facing analysis MUST use calibrated language, disclose key assumptions
  and risks, and avoid personalized guarantees or directives to buy/sell.

## Security and Privacy

- Deny access by default and enforce ownership checks server-side for every
  portfolio, position, watchlist, journal, alert, and AI conversation.
- Validate all inputs at trust boundaries. Use parameterized persistence APIs.
- Keep secrets in environment/secret stores; commit only safe examples.
- Do not log credentials, JWTs, document contents containing private data, or
  full prompts/responses that may contain PII.
- New external integrations require timeout, bounded retry, rate-limit,
  circuit/fallback behavior, and secret-handling decisions in the plan.
- Authentication or data-access changes require negative authorization tests.

## Testing and Quality Gates

Tests are required in proportion to risk:

- Unit and property/boundary tests for deterministic financial calculations.
- Contract tests for public APIs, Spring-to-AI calls, and external providers.
- Integration tests for persistence, authorization, migrations, and retrieval.
- End-to-end tests for each P1 user journey when the necessary services exist.
- Evaluation datasets and measurable groundedness/citation checks for AI
  behavior; snapshot wording alone is not a sufficient AI test.

Before handing off a change, run all relevant commands from the service root:

```powershell
# Frontend
npm run lint
npm run build

# Backend
.\mvnw.cmd test

# AI service (until a test runner is added)
uv run python -m compileall .
```

When a feature adds Python tests, add the test dependency to `pyproject.toml`
and use `uv run pytest`. Never claim a check passed if it was not run; report
the command and blocker.

## Implementation Discipline

- Prefer vertical slices that deliver one independently testable user story.
- Keep domain logic out of controllers, route handlers, UI components, and LLM
  prompts. Make calculations pure where practical.
- Use schema migrations for persisted data. Never rely on automatic destructive
  schema changes outside disposable local environments.
- Add structured logs, metrics, and health/failure signals at new boundaries.
  Never make observability contain sensitive payloads.
- Avoid speculative abstractions and infrastructure. Implement only approved
  scope, especially for Kafka, microservice extraction, and predictive ML.
- Preserve unrelated user changes and do not rewrite generated/lock files
  unless the feature requires it.

## Definition of Done

A feature is complete only when:

- Its spec has no unresolved clarification and acceptance scenarios pass.
- Contracts, data model, migrations, and operational notes match the code.
- Requirement IDs are traceable to tasks and tests.
- Security, financial correctness, AI grounding, and failure paths were tested.
- Relevant quality commands pass and results are reported.
- Documentation and ADRs reflect durable decisions.
- No secret, placeholder, fabricated market fact, or unapproved scope remains.

