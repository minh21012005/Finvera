# Finvera Core Backend Instructions

This file extends the repository-level `AGENTS.md` for `finvera-be/`.

## Responsibility

The Spring Boot application is Finvera's public API boundary and transactional
core. It owns authentication, authorization, user-scoped resources, domain
workflows, deterministic analytics, and calls to the internal AI service.

## Structure

- Organize code first by business module (`auth`, `user`, `market`, `stock`,
  `technical`, `fundamental`, `screener`, `strategy`, `signal`, `risk`,
  `portfolio`, `watchlist`, `news`, `alert`, `ai`).
- Within each module use the ADR-0007 layers: `controller`, `dto`, `service`,
  `repository`, and `entity`; use `domain` for pure deterministic rules,
  `provider` for external integrations, and `config` when needed.
- Dependencies flow `controller -> service -> repository -> entity`.
  Controllers MUST NOT call repositories or expose entities. Services own use
  cases and transaction boundaries. DTOs define transport data only.
- A module MUST NOT access another module's repository or database internals.
  Use an explicit application interface or a documented domain event.
- Keep controllers thin: validate transport input, invoke one use case, and map
  the result/error to the versioned API contract.
- Keep JPA entities from leaking into API payloads. Use explicit request and
  response models.

## Java and Persistence

- Target Java 21 and the Spring Boot version pinned in `pom.xml`.
- Use `BigDecimal` with declared scale/rounding for financial values. Do not use
  `double` or `float` for money, valuation, position sizing, or P/L.
- Make transaction boundaries explicit at application-service level.
- Every schema change requires a forward migration and an integration test.
  Do not enable destructive automatic DDL in shared environments.
- Avoid unbounded queries and N+1 loading. Pagination and deterministic sorting
  are required for collections exposed by APIs.
- Persist instants in UTC and retain market timezone/effective-date semantics in
  the domain model.

## API and Security

- Public endpoints live under `/api/v1` until a versioning ADR changes it.
- Apply authentication and object-level ownership checks in backend code; UI
  visibility is not authorization.
- Return a consistent error envelope with stable machine-readable codes and a
  request/correlation identifier.
- Validate inbound AI-service responses before exposing them to clients.
- HTTP clients require explicit connect/read timeouts, bounded retry only for
  safe operations, and graceful fallback.

## Verification

- Write unit tests for domain rules and boundary/property tests for numerical
  calculations.
- Use integration tests for repositories, migrations, security, and module
  boundaries; use contract tests for `finvera-ai` interactions.
- Do not hardcode credential-shaped username/password pairs, API keys, tokens,
  OTPs, or password hashes, including in tests. Generate test authentication
  values at runtime and let Testcontainers supply database credentials.
- Run `.\mvnw.cmd test` from `finvera-be/` before handoff.
