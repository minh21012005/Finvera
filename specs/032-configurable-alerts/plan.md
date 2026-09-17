# Implementation Plan: Configurable Alerts

**Feature Directory**: `specs/032-configurable-alerts`  
**Date**: 2026-09-16  
**Spec**: [spec.md](./spec.md)  
**Status**: Approved for implementation

## Summary

Add owner-scoped deterministic alerts and a durable in-app notification inbox. A bounded Spring scheduler polls at most 50 enabled definitions, obtains immutable accepted facts through published market, stock, portfolio, and research application interfaces, evaluates versioned pure rules, and atomically records the evaluation, episode transition, and notification. The React SPA provides alert management and inbox workflows. No LLM, Kafka, Redis, external notification provider, or new deployable service is introduced.

## Technical Context

**Affected Projects**: `finvera-be`, `finvera-fe`, documentation  
**Languages/Versions**: Java 21 / Spring Boot 4.1.0; TypeScript 5 / React 19.2.8 / Vite 8.2.1  
**Primary Dependencies**: Existing Spring Web MVC, Security, Validation, Data JPA, Flyway, PostgreSQL driver, Micrometer; existing React and lucide-react; no additions  
**Storage/State**: PostgreSQL is authoritative for definitions, evaluations, notifications, and delivery state  
**Interfaces**: Authenticated public REST `/api/v1/alerts` and `/api/v1/notifications`; in-process published application interfaces for source facts  
**Testing/Evaluation**: Pure rule boundary/property tests, controller contract and negative authorization tests, PostgreSQL migration/persistence integration tests, worker recovery/concurrency tests, React component/API validation tests, accessibility assertions, quickstart acceptance  
**Target Environment**: Existing browser SPA and Spring server/container; UTC storage and `Asia/Ho_Chi_Minh` market semantics  
**Performance Goals**: management p95 under 1 second; 99% of eligible alerts evaluated within 30 seconds; committed notifications visible within 5 seconds  
**Availability/Degradation**: Each alert is isolated. Missing/stale/partial input becomes `WITHHELD`; transient evaluation failures receive at most one retry and then `FAILED`; other product modules remain available.  
**Scale/Scope**: One current private owner, future multi-owner enforcement; 100 retained and 50 enabled alerts per owner; page size 1-100; scheduler batch 50 and one worker by default  
**Open Technical Unknowns**: None; decisions are resolved in [research.md](./research.md)

## Constitution Check

- [x] **Deterministic core**: `alert-condition-v1` uses `BigDecimal`, fixed windows, and published versioned facts; the LLM is absent.
- [x] **Evidence and time**: evaluations and notifications retain fact identity, source, effective/accepted times, unit, adjustment basis, and rule version.
- [x] **Boundaries and ownership**: browser calls Spring; Alerts consumes published application interfaces and owns only alert tables.
- [x] **Security and privacy**: every public path authenticates and scopes by owner; unknown and foreign IDs share a 404 response; telemetry excludes payload values.
- [x] **Traceability**: the spec has stable IDs and no clarification marker; contracts and tasks map to them.
- [x] **Risk-based tests**: numeric equality, crossover history, deduplication, ownership, migration, recovery, and accessibility are covered.
- [x] **Resilience and observability**: retry is bounded, failures are isolated, and metrics use condition/outcome/reason labels only.
- [x] **Modular simplicity**: one module and one scheduler reuse existing infrastructure; no provider, broker, cache, AI path, or dependency is added.

**Pre-research result**: PASS. Existing services and infrastructure are sufficient; source contract gaps require additive application-interface methods.  
**Post-design result**: PASS. Data model, APIs, failure semantics, security, observability, migration, rollback, and validation are explicit; no exception or ADR is required.

## System Context and Boundaries

### Affected User and System Flows

```text
Owner -> React Alerts page -> Spring /api/v1/alerts -> PostgreSQL
                                      |
Scheduled Alert worker -> claim due definition -> owning-module application APIs
                       -> alert-condition-v1 -> evaluation + episode transition
                       -> notification + IN_APP delivery record (one transaction)
Owner -> React inbox -> Spring /api/v1/notifications -> PostgreSQL
```

### Ownership

| Concern | Owning project/module | Reason |
|---|---|---|
| Definitions, evaluations, notifications, delivery attempts | `finvera-be/alert` | Alert lifecycle and delivery are one transactional domain. |
| Instruments, completed bars, indicators, signals | `finvera-be/market`, `finvera-be/stock` | Existing source-of-record owners expose immutable references. |
| Regime observations | `finvera-be/market` | Market owns accepted regime calculation and labels. |
| Portfolio concentration snapshot | `finvera-be/portfolio` | Portfolio owns private transactions, valuation, and owner validation. |
| Accepted research documents | `finvera-be/research` | Research owns document ingestion status and metadata. |
| Alert management and inbox UI | `finvera-fe/features/alerts` | Browser workflow; no direct data-store or AI access. |

### Interface Changes

| Interface | Change | Version/Compatibility | Contract Artifact |
|---|---|---|---|
| Public REST | Add alert CRUD/state/evaluation and notification inbox endpoints | Additive `/api/v1`; no existing route changes | [public-api.openapi.yaml](./contracts/public-api.openapi.yaml) |
| Stock application API | Add completed technical-series snapshots carrying fact metadata | Additive Java interface method | [source-facts-v1.md](./contracts/source-facts-v1.md) |
| Market application API | Add current/previous eligible EOD regime observations with deterministic labels | Additive Java interface method | [source-facts-v1.md](./contracts/source-facts-v1.md) |
| Portfolio application API | Add owner-scoped concentration snapshot | New in-process service | [source-facts-v1.md](./contracts/source-facts-v1.md) |
| Research application API | Add accepted matching documents after a baseline | New in-process service | [source-facts-v1.md](./contracts/source-facts-v1.md) |

## Phase 0: Research

All decisions, alternatives, and validation risks are recorded in [research.md](./research.md). The design relies on accepted internal data and adds no external provider or licensing surface.

## Phase 1: Design and Contracts

### Data Model

[data-model.md](./data-model.md) defines definition/evaluation/notification/delivery entities, typed condition payloads, episode state, leases, unique deduplication keys, decimal precision, timestamps, provenance, indexes, retention, and V023 migration behavior.

### Contracts

- [public-api.openapi.yaml](./contracts/public-api.openapi.yaml) is the public versioned REST contract.
- [source-facts-v1.md](./contracts/source-facts-v1.md) is the in-process source-fact contract between modules.

### Security, Privacy, and AI Safety

Spring Security authenticates every route. Services derive `ownerId` from the authenticated session and never accept it in request payloads. Portfolio targets are validated through an owner-scoped portfolio service; all alert/notification reads and writes use `(id, owner_id)`, and missing/foreign IDs return the same `RESOURCE_NOT_FOUND`. Condition payloads are discriminated and allowlisted; page sizes, strings, decimal ranges, and state changes are bounded. Logs and metrics contain correlation ID, condition type, outcome, stable reason, counts, and duration only. No external service, document text, prompt, AI model, secret, threshold, portfolio value, evidence JSON, or owner ID enters telemetry.

### Observability and Operations

Micrometer counters record evaluations by condition type/outcome/reason, triggers, retries, and terminal failures. Timers record evaluation duration and due-alert age; a gauge reports due definitions. Structured logs record alert ID only as a one-way truncated hash plus stable state/reason and correlation ID. The worker is enabled by default with configurable poll delay, batch, lease, and a maximum of two total attempts. A runbook covers backlog, stale leases, failure reasons, safe disablement, and replay constraints.

### Test and Evaluation Strategy

| Requirement IDs | Test level | Fixture/dataset | Expected evidence |
|---|---|---|---|
| FR-003-FR-011, DATA-002-DATA-007 | unit/property | Fixed bars, indicator series, signals, regimes, portfolios, documents | Exact inclusive boundaries, crossings, windows, precision, and withheld reasons |
| FR-012-FR-014, FR-018, SC-002-SC-004, SC-008 | integration | PostgreSQL duplicate/concurrent/recovery fixtures | One notification per episode/event and bounded terminal recovery |
| FR-001-FR-002, FR-015-FR-019, SEC-001-SEC-003 | contract/integration | Same-owner, foreign-owner, unknown-ID, quota, pagination fixtures | Contract shape, stable errors, indistinguishable 404s, bounded pages |
| DATA-001, DATA-008-DATA-010 | integration | Corrected accepted facts and deleted-alert fixtures | Immutable evidence and explicit source/time/version/unit survive deletion |
| NFR-001-NFR-003, NFR-005-NFR-006 | worker/integration | 50 enabled alerts and injected source failures | Timing budgets, isolation, metrics, and bounded retry |
| NFR-004, SC-007 | frontend component/E2E | Representative alert/inbox states | Keyboard operation, text state/reasons, readable evidence |

No AI evaluation is applicable.

### Rollout, Migration, and Rollback

Deploy backend migration V023 and additive source APIs before the frontend. V023 creates empty tables and indexes, so no backfill or dual-read window is needed. The worker is enabled by default; operators can set `FINVERA_ALERT_WORKER_ENABLED=false` to stop evaluation while retaining management and inbox access. Rollback first disables the worker and frontend route, then rolls back the application. Database tables remain for forward recovery; destructive down migration is intentionally omitted. A corrected fact is picked up by its newer accepted timestamp without historical replay.

### Quickstart Acceptance

[quickstart.md](./quickstart.md) contains build/test commands, migration checks, the P1 create/evaluate/inbox path, negative ownership checks, deduplication/rearm checks, and observable worker results.

## Project Structure

### Feature Documentation

```text
specs/032-configurable-alerts/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md
```

### Source Code Affected

```text
finvera-be/src/main/java/com/minhnb/finvera_be/alert/{controller,dto,service,repository,entity,domain,config}/
finvera-be/src/main/java/com/minhnb/finvera_be/{stock,market,portfolio,research}/service/
finvera-be/src/main/java/com/minhnb/finvera_be/shared/api/ProblemDetailsAdvice.java
finvera-be/src/main/resources/application.yaml
finvera-be/src/main/resources/db/migration/V023__create_alert_schema.sql
finvera-be/src/test/java/com/minhnb/finvera_be/alert/
finvera-fe/src/features/alerts/{api,components}/
finvera-fe/src/{app.tsx,router.ts}
finvera-fe/src/features/auth/owner-access-gate.tsx
docs/runbooks/configurable-alerts.md
docs/{ARCHITECTURE.md,FEATURE_ROADMAP.md,PROJECT_CONTEXT.md}
```

**Structure Decision**: Alerts is a layered Spring business module. The scheduler and public API share the same application service and transactional model, while source data remains behind the owning modules' published interfaces. The frontend remains a feature folder in the existing SPA.

## Traceability Summary

| Requirement IDs | Design/Contract | Test/Evaluation | Planned Task Group |
|---|---|---|---|
| FR-001-FR-002, FR-019, SEC-001-SEC-003 | REST contract, Alert Definition | controller/persistence/authorization | Setup + US1 |
| FR-003-FR-011, DATA-001-DATA-008 | source-facts contract, evaluator | unit/property/contract | Foundational + US2 |
| FR-012-FR-014, FR-017-FR-018 | episode/lease model | recovery/concurrency integration | US2 |
| FR-015-FR-016, DATA-009-DATA-010 | Notification model, REST contract | persistence/API/frontend | US3 |
| NFR-001-NFR-006, SC-001-SC-008 | operations/test plans | integration, component, quickstart | Polish + validation |

## Complexity Tracking

No constitution violation or material new infrastructure is introduced.
