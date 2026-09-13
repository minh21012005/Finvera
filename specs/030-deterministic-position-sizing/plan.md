# Implementation Plan: Deterministic Position Sizing

**Feature Directory**: `specs/030-deterministic-position-sizing`  
**Date**: 2026-09-12  
**Spec**: [spec.md](spec.md)  
**Status**: Ready for tasks

## Summary

Deliver one stateless, authenticated position-sizing calculator with manual and
portfolio-linked modes. A pure versioned Java domain engine computes the maximum
cash-funded long-equity quantity from risk, affordability, optional exposure,
cost, slippage, and standard-lot constraints. Spring resolves trusted market,
portfolio, and signal inputs and returns a fully auditable breakdown; React
collects explicit assumptions and renders results without recalculation.

## Technical Context

**Affected Projects**: `finvera-be`, `finvera-fe`; no `finvera-ai` change  
**Languages/Versions**: Java 21 / Spring Boot 4.1.0; TypeScript 5 / React 19.2.8 / Vite 8.2.1  
**Primary Dependencies**: Existing Spring validation/security/actuator, portfolio application services, `MarketReferenceDataService`, `StockReferenceDataService`; no new dependency  
**Storage/State**: No new persistence or migration; portfolio ledger and market/stock tables remain upstream systems of record  
**Interfaces**: Authenticated public REST `POST /api/v1/position-sizing/calculate`; existing portfolio and stock application APIs  
**Testing/Evaluation**: Pure unit and property/boundary tests, controller contract/security tests, portfolio/signal integration tests, React API/component tests, Playwright P1 journey  
**Target Environment**: Existing private browser and Spring deployment; Asia/Ho_Chi_Minh for market interpretation and UTC timestamps in transport  
**Performance Goals**: p95 ≤1 second manual; p95 ≤2 seconds portfolio-linked  
**Availability/Degradation**: Manual mode remains available when portfolio/signal reads fail; selected trusted inputs withhold rather than fabricate missing facts  
**Scale/Scope**: Single owner, one symbol and one synchronous calculation per request, no batch sizing  
**Open Technical Unknowns**: None; R-001 through R-009 in `research.md` resolve formula, rule, boundary, and integration choices

## Constitution Check

- [x] **Deterministic core**: `PositionSizingV1` is pure, versioned, and owns all authoritative arithmetic.
- [x] **Evidence and time**: Responses carry source, as-of time, coherence key, venue, lot-rule source/effective date, units, and freshness/reasons.
- [x] **Boundaries and ownership**: Browser calls Spring; positioning uses published portfolio/stock/market application interfaces, never their repositories.
- [x] **Security and privacy**: Endpoint is authenticated, ownership is server-enforced, and logs/metric tags exclude financial values.
- [x] **Traceability**: Five clarifications are recorded, no marker remains, and stable IDs map to contracts and planned tests.
- [x] **Risk-based tests**: Formula, decimal, floor/lot, tied-cap, invalid, staleness, ownership, contract, UI, and E2E cases are planned.
- [x] **Resilience and observability**: No external call is introduced; failures use bounded reasons and latency/outcome metrics.
- [x] **Modular simplicity**: No datastore, service, queue, AI call, or library is added.

**Pre-research result**: PASS — market-lot currency and cross-module snapshot semantics required research.  
**Post-design result**: PASS — research, formula, OpenAPI, and value-object model resolve them without an exception or ADR.

## System Context and Boundaries

### Affected User and System Flows

```text
Owner -> React form -> Spring positioning controller
  -> manual normalization
  -> market instrument/venue lookup
  -> optional current-signal resolution
  -> optional owner-scoped coherent portfolio snapshot
  -> PositionSizingV1 pure calculation
  -> transparent result/withholding response -> React breakdown
```

### Ownership

| Concern | Owning project/module | Reason |
|---|---|---|
| Formula and orchestration | `finvera-be/positioning` | New deterministic capability and public boundary |
| Instrument identity/venue | `finvera-be/market` application API | Existing reference-data authority |
| Signal levels/freshness | `finvera-be/stock` application API | Feature 004 remains calculation authority |
| Cash/value/exposure snapshot | `finvera-be/portfolio` application API | Feature 005 ledger remains source of truth and enforces ownership |
| Input/result rendering | `finvera-fe/features/position-sizing` | Presentation only; no authoritative calculation |

### Interface Changes

| Interface | Change | Version/Compatibility | Contract Artifact |
|---|---|---|---|
| Public REST | Add stateless calculate endpoint | Additive `/api/v1`; existing clients unaffected | `contracts/position-sizing.openapi.yaml` |
| Financial contract | Add deterministic sizing formula | `position-sizing-v1`; changes require a new version | `contracts/position-sizing-v1.md` |
| Portfolio application API | Add owner-scoped immutable sizing snapshot read | Additive internal Java interface | `data-model.md` / R-006 |
| Stock application API | Add current signal selector/read for sizing | Additive internal Java interface | `data-model.md` / R-007 |

## Phase 0: Research

`research.md` records R-001 module placement, R-002 formula, R-003 precision,
R-004 costs, R-005 market lot, R-006 portfolio snapshot, R-007 signal import,
R-008 stateless failure behavior, and R-009 verification evidence.

## Phase 1: Design and Contracts

### Data Model

`data-model.md` defines immutable request/result values and upstream snapshots.
No entity, table, migration, retention, or backfill is needed.

### Contracts

- `contracts/position-sizing-v1.md` owns formula, precision, rounding, costs,
  exposure, reasons, and reproducibility.
- `contracts/position-sizing.openapi.yaml` owns authentication, mutually
  exclusive shapes, response, validation, and non-disclosing errors.

### Security, Privacy, and AI Safety

Spring Security protects the endpoint. `PortfolioSizingDataService` resolves the
authenticated owner; owner ID and portfolio values are not accepted as trusted
browser facts. Missing/deleted/foreign portfolios share one 404. Signal
references are re-resolved by stock. No data reaches an LLM or external provider.
Logs contain correlation ID, mode, outcome, bounded reason, version, and latency.

### Observability and Operations

Record `finvera.position_sizing.calculation` with bounded `mode`/`outcome` and
`finvera.position_sizing.duration` with `mode`; reasons use a fixed enum. Never
tag symbol, portfolio ID, capital, prices, rates, exposure, or quantity. The
runbook requires lot-rule review when exchange rules change.

### Test and Evaluation Strategy

| Requirement IDs | Test level | Fixture/dataset | Expected evidence |
|---|---|---|---|
| FR-001–FR-008, FR-013, FR-015–FR-017; DATA-002–DATA-005, DATA-008–DATA-009 | Unit/property | v1 arithmetic and generated boundaries | Exact results; never exceeds a cap; repeatable |
| FR-009–FR-010, FR-014–FR-016; DATA-001, DATA-006–DATA-008 | Integration | Portfolio ledger and fresh/stale/missing prices | Coherent owned snapshot or withholding |
| FR-018; DATA-001, DATA-010 | Contract/integration | Current/stale/absent/modified signals | Server-resolved provenance and confirmation |
| SEC-001–SEC-004 | Controller/security/log | Anonymous, foreign portfolio, malformed input | 401/404/422 without disclosure |
| NFR-001–NFR-006 | Performance/UI/E2E | Manual and linked scenarios | p95 targets, accessible breakdown, bounded metrics |

### Rollout, Migration, and Rollback

Deploy the additive backend before exposing frontend navigation. No schema
change/backfill exists. Rollback hides/removes the route and endpoint without
touching portfolio/market data. A market-rule change adds a new version.

### Quickstart Acceptance

`quickstart.md` covers manual, portfolio cap, cost exclusion, signal confirmation,
invalid/authorization paths, performance, and repository gates.

## Project Structure

### Feature Documentation

```text
specs/030-deterministic-position-sizing/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/requirements.md
├── contracts/position-sizing-v1.md
├── contracts/position-sizing.openapi.yaml
└── tasks.md
```

### Source Code Affected

```text
finvera-be/src/main/java/com/minhnb/finvera_be/positioning/
├── controller/PositionSizingController.java
├── domain/PositionSizingV1.java
├── dto/PositionSizingDtos.java
└── service/PositionSizingService.java
finvera-be/src/main/java/com/minhnb/finvera_be/portfolio/service/PortfolioSizingDataService.java
finvera-be/src/main/java/com/minhnb/finvera_be/stock/service/StockSizingDataService.java
finvera-be/src/main/java/com/minhnb/finvera_be/shared/api/ProblemDetailsAdvice.java
finvera-be/src/test/java/com/minhnb/finvera_be/positioning/
finvera-fe/src/features/position-sizing/
finvera-fe/src/router.ts
finvera-fe/src/app.tsx
finvera-fe/src/features/auth/owner-access-gate.tsx
finvera-fe/tests/e2e/position-sizing.spec.ts
docs/runbooks/position-sizing.md
```

**Structure Decision**: A small layered `positioning` module owns the workflow
and pure contract. Portfolio and stock publish minimal read interfaces,
preserving ADR-0007 and preventing cross-repository access.

## Traceability Summary

| Requirement IDs | Design/Contract | Test/Evaluation | Planned Task Group |
|---|---|---|---|
| FR-001–FR-008, FR-013, FR-015–FR-017; DATA-002–DATA-005, DATA-008–DATA-009 | Financial contract | Domain unit/property | Foundation, US1 |
| FR-009–FR-010, FR-014–FR-016; DATA-001, DATA-006–DATA-008 | OpenAPI/data model | Portfolio integration/security | US2 |
| FR-018; DATA-001, DATA-010 | OpenAPI/signal snapshot | Signal integration/UI | US1, US3 |
| SEC-001–SEC-004 | OpenAPI/security design | Negative controller/log | Foundation, US2, final |
| NFR-001–NFR-006 | Plan/quickstart | Performance/accessibility/E2E | US3, final |

## Complexity Tracking

No constitution violation, new dependency, deployable service, datastore, or
event infrastructure is introduced.

