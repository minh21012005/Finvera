# ADR-0007: Use Layered Architecture Within Backend Modules

**Status**: Accepted  
**Date**: 2026-08-17  
**Decision owners**: Project owner, backend implementation owner  
**Related specs**: `specs/001-market-overview/plan.md`

## Context

Finvera remains a modular monolith organized by business capability. The first
market implementation introduced `application/port/out` and
`infrastructure/*` packages. The project owner requires a conventional Spring
layered structure so navigation, onboarding, review, and future feature work
use familiar controller, service, repository, entity, and DTO boundaries.

This decision concerns source-code organization inside each business module;
it does not weaken module ownership, deterministic domain rules, API contracts,
or the rule that JPA entities must never become HTTP payloads.

## Decision Drivers

- Predictable Spring Boot structure for a small development team.
- Thin controllers and explicit service-level transaction boundaries.
- Clear separation between API DTOs and persistence entities.
- Domain-oriented modular monolith required by the constitution.
- Minimal architecture ceremony for the current private MVP.

## Considered Options

### Option A: Hexagonal ports and adapters inside every module

This provides strong dependency inversion but adds interfaces and mapping
layers even where there is only one use case and one persistence technology.

### Option B: Layered architecture inside each business module

Each module uses conventional layers, while pure deterministic calculations
remain under `domain` and replaceable external sources remain under `provider`.

## Decision

Adopt package-by-business-module, then package-by-layer:

```text
<module>/
  controller/   HTTP boundary; request validation and response mapping only
  dto/          versioned request/response transport models
  service/      use cases, orchestration, authorization, transactions
  repository/   Spring Data/JPA access owned by the module
  entity/       persistence mappings; never exposed by controllers
  domain/       optional pure deterministic rules and value objects
  provider/     optional replaceable external-data integrations
  config/       optional module configuration
```

Dependencies flow `controller -> service -> repository -> entity`. Controllers
MUST NOT access repositories or entities directly. Services MAY call domain
rules and provider contracts. DTO mapping happens at the controller/service
boundary. Cross-module collaboration goes through the owning module's service
API or a documented event, never another module's repository.

Interfaces are added when there is an actual replaceability or module-boundary
need; repositories do not require a second adapter wrapper solely for symmetry.

## Consequences

### Positive

- Familiar locations and dependency direction.
- Less scaffolding for straightforward CRUD and query use cases.
- Explicit protection against entity leakage and fat controllers.
- Pure financial domain code remains isolated and testable.

### Negative / Trade-offs

- Services are aware of Spring Data repository contracts.
- Replacing persistence technology may require service/repository changes.
- Existing Feature 001 paths and tests need a one-time refactor.

### Risks and Mitigations

- Layered code can become an anemic CRUD structure: deterministic finance rules
  stay in `domain`, while services only orchestrate them.
- Services can grow too large: split by cohesive use case, not generic manager
  classes.
- Entities can leak into APIs: explicit DTOs and architecture tests prohibit
  controller dependencies on `entity` and `repository`.

## Migration and Rollback

Feature 001 market code is migrated before T017/T018. Later modules adopt the
layout when first implemented or next materially changed. Rollback would
require a superseding ADR; no database or API migration is involved.

## Validation

- ArchUnit rejects controller-to-repository/entity dependencies.
- Repository integration tests run on PostgreSQL Testcontainers.
- `./mvnw test` passes after package migration.
- Feature task paths and backend repository instructions match this ADR.
