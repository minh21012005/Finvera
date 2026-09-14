# Implementation Plan: Historical Strategy Backtesting

**Feature Directory**: `specs/031-historical-strategy-backtesting`  
**Date**: 2026-09-13  
**Spec**: [spec.md](spec.md)  
**Status**: Design complete

## Summary

Deliver an owner-scoped asynchronous daily backtest for every existing
`strategy-signal-v1` strategy on one symbol. A pure versioned engine evaluates
point-in-time inputs, executes next-open entries, supports bounded pyramiding,
delegates quantities to `position-sizing-v1`, and atomically publishes an
immutable trade, equity, metric, and evidence result. Spring Boot owns the API,
job lifecycle, authorization, and PostgreSQL persistence; React owns the UI.

## Technical Context

**Affected Projects**: `finvera-be`, `finvera-fe`, documentation  
**Languages/Versions**: Java 21/Spring Boot and React 19/TypeScript/Vite from
committed manifests  
**Primary Dependencies**: Existing Spring Web, Security, Data JPA, Validation,
Micrometer, and frontend stack; no new runtime dependency  
**Storage/State**: PostgreSQL only  
**Interfaces**: Owner-authenticated public REST v1  
**Testing/Evaluation**: Financial unit/property/golden, compatibility, contract,
PostgreSQL concurrency, authorization, recovery, performance, component, E2E  
**Target Environment**: Existing browser and Spring/container deployment;
Asia/Ho_Chi_Minh market dates and UTC transport/storage instants  
**Performance Goals**: Create p95 <=1 second; <=10-year run terminal p95 <=60
seconds in the reference environment  
**Availability/Degradation**: Incoherent history withholds; stale run is
reclaimed once and fails after its second unsuccessful attempt  
**Scale/Scope**: Single owner; one symbol/strategy per run; daily, <=10 years,
<=4 open tranches, page size <=500, bounded worker concurrency  
**Open Technical Unknowns**: None; R-001 through R-014 resolve them.

## Constitution Check

- [x] **Deterministic core**: Versioned backtest, metric and pyramiding rules
  reuse strategy and sizing contracts.
- [x] **Evidence and time**: Cutoff, selected inputs, effective dates, sources,
  adjustment, units, versions and limitations are modeled.
- [x] **Boundaries and ownership**: Browser calls Spring; `backtest` consumes
  explicit stock/market application APIs and owns its tables.
- [x] **Security and privacy**: Owner-qualified reads, indistinguishable 404,
  bounded input, retention and payload-safe telemetry are defined.
- [x] **Traceability**: Stable requirements map to contracts/tests/tasks and no
  clarification remains.
- [x] **Risk-based tests**: Numerical, look-ahead, persistence, concurrency,
  authorization, recovery, performance and E2E coverage is planned.
- [x] **Resilience and observability**: Durable claims, heartbeat, two attempts,
  atomic publication and safe metrics are defined.
- [x] **Modular simplicity**: No new deployable, Kafka, datastore, AI or provider.

**Pre-research result**: PASS; owner choices resolved before planning.  
**Post-design result**: PASS; Phase 1 preserves every gate without exception.

## System Context and Boundaries

### Affected User and System Flows

```text
Owner -> React -> POST /api/v1/backtests -> validate/resolve -> QUEUED -> 202
worker -> atomic claim -> historical snapshot -> strategy -> backtest engine
       -> positioning per entry -> metrics -> atomic terminal publication
Owner -> list/poll/detail/page trades/equity/events through owner-qualified API
```

### Ownership

| Concern | Owning module | Reason |
|---|---|---|
| Lifecycle, engine, metrics, persistence, API | `finvera-be/backtest` | New coherent capability |
| Bars, indicators, instrument evidence | `finvera-be/stock` application API | Existing data owner |
| Session and lot evidence | `finvera-be/market` application API | Existing rule owner |
| Quantity calculation | `finvera-be/positioning` domain API | Authoritative formula |
| User workflow | `finvera-fe/src/features/backtest` | Feature-local UI |

The backtest module MUST NOT access another module's repositories or entities.

### Interface Changes

| Interface | Change | Compatibility | Contract |
|---|---|---|---|
| Public REST | Create/list/detail and paged children | Additive `/api/v1` | [OpenAPI](contracts/public-api.openapi.yaml) |
| Financial behavior | Execution, pyramiding, ledger, metrics | New immutable v1 | [Financial](contracts/backtest-financial-v1.md) |
| Stock/market APIs | Cutoff-aware history and rule evidence | Internal additive | R-002/R-003 |
| Positioning | Existing pure calculation per entry | No change | Feature 030 |

## Phase 0: Research

[research.md](research.md) resolves durable local execution, point-in-time
selection, eight-strategy reuse, conservative fill order, pyramiding and heat,
ledger/metrics, persistence/recovery, pagination, and no-trade/withholding.
No external schema is introduced or guessed.

## Phase 1: Design and Contracts

### Data Model

[data-model.md](data-model.md) defines lifecycle, trades, equity, metrics,
events and evidence with precision, indexes, ownership, immutability, retention,
migration and rollback.

### Contracts

- [public-api.openapi.yaml](contracts/public-api.openapi.yaml): request,
  lifecycle, pagination, typed responses and problems.
- [backtest-financial-v1.md](contracts/backtest-financial-v1.md): event order,
  fills, gaps, pyramiding, risk, cash, metrics and terminal behavior.

### Security, Privacy, and AI Safety

Every route is authenticated. Services resolve by owner and run ID before
children; unknown/non-owned IDs share one 404. Create bounds dates, decimals,
enums, symbol and idempotency key. Telemetry excludes owner, symbol, capital,
rates, trades, payloads and fingerprints. No AI or external provider is called.

### Observability and Operations

Add created/terminal/recovery counters, bounded reason codes, queue/execution
timers, queued/running gauges and oldest-queue age. Correlation/run IDs may be
logged. A runbook covers claim timeout, recovery, stuck queues, rollback and
financial invariant response.

### Test and Evaluation Strategy

| Requirements | Level | Expected evidence |
|---|---|---|
| FR-002/006, DATA-002/003 | Eight-strategy compatibility/property | Evaluator parity and zero future reads |
| FR-003/004/007/008, DATA-006-008 | Pure golden/property | Exact fills, heat, ledger and invariants |
| FR-009-012, DATA-009-011 | Metric golden/property | Exact values or typed unavailability |
| FR-005/014/015, NFR-004 | PostgreSQL concurrency | Atomic one-result publication/recovery |
| FR-013, SEC-001/002 | Security integration | Indistinguishable cross-owner denial |
| SEC-003/004, NFR-005 | Contract/log capture | Typed problems and safe telemetry |
| NFR-001/002 | HTTP/worker performance | Recorded p95 thresholds |
| NFR-003, SC-007 | Component/E2E/owner review | Keyboard/text chart/comprehension |

### Rollout, Migration, and Rollback

Apply additive V022; validate migration, API and financial fixtures with one
bounded worker enabled; then deploy frontend. Application rollback leaves
dormant tables/results intact. Disable the worker on an invariant, future-read,
duplicate-publication or performance breach.
Never rewrite completed results. No backfill is required.

### Quickstart Acceptance

[quickstart.md](quickstart.md) covers all eight strategies, numerical and metric
boundaries, authorization, recovery, performance and owner review.

## Project Structure

### Feature Documentation

```text
specs/031-historical-strategy-backtesting/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── checklists/requirements.md
├── contracts/backtest-financial-v1.md
├── contracts/public-api.openapi.yaml
└── tasks.md
```

### Source Code Affected

```text
finvera-be/src/main/java/com/minhnb/finvera_be/backtest/
finvera-be/src/main/java/com/minhnb/finvera_be/{stock,market}/service/
finvera-be/src/main/resources/db/migration/V022__create_backtest_schema.sql
finvera-be/src/test/java/com/minhnb/finvera_be/backtest/
finvera-fe/src/features/backtest/
finvera-fe/tests/e2e/backtest.spec.ts
docs/runbooks/backtesting.md
docs/{ARCHITECTURE,FEATURE_ROADMAP,PROJECT_CONTEXT}.md
```

**Structure Decision**: ADR-0007 layers with dependencies only on explicit
application APIs and the pure positioning domain preserve current ownership.

## Traceability Summary

| Requirements | Design | Verification | Task group |
|---|---|---|---|
| FR-001-008, DATA-001-008 | Financial/model/API | Engine/compatibility | Foundation + US1 |
| FR-009-012, DATA-009-013 | Metric/result model | Golden/property | US2 |
| FR-005/013-016, SEC/NFR | Lifecycle/API/operations | Persistence/security/recovery | US3 + polish |
| FR-017, NFR-003, SC-007 | UI/evidence | Component/E2E/owner | US2/US3 + validation |

## Complexity Tracking

No constitution violation or exception. Owner-approved pyramiding remains in
one pure versioned engine with a four-tranche bound.
