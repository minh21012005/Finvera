# Implementation Plan: Strategy, Signal, and Risk Scenarios

**Feature Directory**: `specs/004-strategy-signal-risk`
**Date**: 2026-08-19
**Spec**: [spec.md](spec.md)
**Status**: Draft

## Summary

Deliver Finvera's fourth vertical slice: for a supported stock, evaluate
eight fixed, deterministic technical strategies against its latest
accepted daily data, and for every one that currently triggers, produce a
complete signal — direction, entry zone, stop loss, take-profit targets,
risk/reward, a factor-based risk score and level, supporting evidence, and
an as-of time. The owner can also scan the supported universe for every
stock currently triggering one chosen strategy.

Every strategy condition, signal level, and risk factor is built
exclusively from values Features 001-003 already accept, compute, and
persist — this feature adds new *combination and level-calculation* rules
(`strategy-signal-v1`), not new raw indicator math. No new module, no new
provider, no new gate.

## Technical Context

**Affected projects**: `finvera-be` (extend `stock` module with a new
`strategy` sub-package), `finvera-fe` (new `stock-strategy` or extended
`stock-detail` feature). No change to `finvera-ai`. No new deployable unit.

**Languages and versions**: unchanged — Java 21/Spring Boot 4.1.x,
TypeScript 5/React 19/Vite, PostgreSQL 17.

**Primary dependencies**: unchanged. Reuses `DecimalMath`, `StockTypes`,
`CoherenceKeys`, `ScreenerV1`'s Breakout/Trend derivations, and Feature
001's regime assessment read path.

**Storage and state**: PostgreSQL. Forward-only Flyway from `V004`: three
new tables (`strategy_signal`, `strategy_signal_risk_factor`,
`strategy_signal_input`). No existing table altered.

**Interfaces**: two new owner-only REST endpoints —
`GET /api/v1/stocks/{symbol}/signals`,
`POST /api/v1/strategies/{strategyCode}/scan`.

**Testing**: pure unit/property tests for `strategy-signal-v1` (all eight
strategies, level formulas, risk-factor scoring, the four-of-six risk
floor), DB integration tests (persistence, revision chains, the
non-trigger-is-not-persisted rule), Spring MVC/security contract tests,
Vitest component tests, Playwright P1-P3.

**Performance goals**: NFR-001 — 95% of single-stock signal views within 3
seconds. NFR-002 — 95% of strategy scans within 5 seconds, matching Feature
003's screening baseline.

**Availability and degradation**: a strategy with insufficient history is
excluded, not the whole view; a risk score below the four-of-six factor
floor is withheld while the signal itself still publishes (FR-007); a
cross-source conflict withholds only the affected strategy/factor.

**Scale and scope**: one stock or one strategy scan at a time, daily
timeframe only, eight fixed strategies, `LONG` direction only, no position
sizing, no portfolio-aware risk factors.

**Open technical unknowns**: none blocking — research.md resolves every
formula (strategy conditions R-002, levels R-003, risk score R-004, signal
strength R-005) before this plan was written, per Constitution Principle V.

## Constitution Check

### Pre-research gate

- [x] **I. Deterministic finance core** — `strategy-signal-v1` specifies
  every strategy condition, level formula, and risk factor before code,
  each versioned.
- [x] **II. Evidence, provenance, temporal truth** — every signal records
  its exact contributing inputs (`strategy_signal_input`) and as-of time;
  a withheld factor/strategy states its reason.
- [x] **III. Explicit boundaries** — browser to Spring only; no new
  module; reads `market`'s regime assessment only through a published
  interface extension (research R-001 lineage), never its repository.
- [x] **IV. Security, privacy, responsible decision support** — no new
  auth path; FR-013 mandates non-guarantee, non-instruction framing for
  every signal.
- [x] **V. Specification and traceability before code** — spec, research,
  data model, and two contracts exist; no unresolved clarification marker.
- [x] **VI. Risk-based testing** — numerical boundary (strategy thresholds,
  the risk-factor floor), persistence/revision, authorization, and
  degradation risks each have a named test level below.
- [x] **VII. Resilience and observability** — degradation paths (FR-005,
  FR-007, DATA-003) are defined; this feature's read-only endpoints follow
  the same observability baseline research established for Feature 003
  (global `CorrelationIdFilter` + response-carried failure-class
  visibility), consistent with Feature 002's own read-path precedent —
  decided up front this time, not discovered afterward.
- [x] **VIII. Modular simplicity** — no new module, datastore, broker, or
  AI dependency; equal-weighted risk scoring and signal-strength-tied-to-
  risk (research R-004/R-005) are each the simpler alternative,
  deliberately chosen over a more elaborate design.

**Result**: PASS.

### Post-design gate

*(To be completed after implementation, mirroring Feature 003's practice of
keeping this section honest rather than pre-filled.)*

## System Context and Boundaries

```text
                 owner (browser, existing session)
                              |
              GET /stocks/{symbol}/signals
              POST /strategies/{strategyCode}/scan
                              |
                              v
        finvera-be / stock / strategy (new sub-package)
                              |
        read: technical_indicator_result (current + prior day),
              equity_daily_bar, screener-v1 Breakout/Trend,
              Feature 001 regime assessment
                              |
                              v
        strategy-signal-v1 engine (Java, pure, deterministic):
          8 entry conditions -> levels -> 6 risk factors -> score/level
                              |
                              v
        persist strategy_signal / _risk_factor / _input (revision chain)
                              |
                              v
        React SPA stock-detail "Signals" section + a strategy-scan view

finvera-ai / TCBS / Vnstock / Redis / Qdrant / Kafka: no path in this feature
```

### Ownership

| Concern | Owner | Reason |
|---|---|---|
| Strategy conditions, level formulas, risk scoring (`strategy-signal-v1`) | `finvera-be/.../stock/domain/strategy` | Pure, deterministic, framework-independent — same pattern as `technical-indicators-v1`/`valuation-v1`/`screener-v1`. |
| Persistence, revision chains, correction recalculation | `finvera-be/.../stock/service/strategy` | Transaction boundary, mirroring `TechnicalIndicatorService`/`ValuationService`. |
| Universe scan orchestration | `finvera-be/.../stock/service/strategy` | Reuses Feature 003's two-pass pattern (research R-007). |
| REST mapping and security | `finvera-be/.../stock/controller`, `stock/dto` | Spring remains the sole public boundary. |
| Presentation and formatting | `finvera-fe/src/features/stock-detail` (extended) or a new `stock-strategy` folder | UI formats contract data; never recalculates a level or score. |
| Every read table/derivation | Feature 001/002/003 | Reused, not duplicated. |
| AI and RAG | Not affected | No AI path exists in this feature. |

### Interface Changes

| Interface | Change | Version and compatibility | Contract Artifact |
|---|---|---|---|
| Public REST | Add two owner-only endpoints | Additive under `/api/v1` | [strategy-signal.openapi.yaml](contracts/strategy-signal.openapi.yaml) |
| Calculation rules | Add one versioned rule set | `strategy-signal-v1` | [strategy-signal-v1.md](contracts/strategy-signal-v1.md) |
| `market` published interface | Add a regime-assessment read method (mirroring Feature 003's `findInstrumentsByIds` addition) | Additive to `MarketReferenceDataService` | Recorded in `tasks.md` at implementation time, per Feature 003's own precedent for this kind of small interface growth |
| Database | Add `strategy_signal`, `strategy_signal_risk_factor`, `strategy_signal_input` | Forward Flyway from `V004`; no existing table altered | [data-model.md](data-model.md) |

## Phase 0: Research

Complete in [research.md](research.md). Nine decisions: module placement,
the eight strategy definitions (including why three are "crossing" events
reading the prior day), the uniform ATR-anchored level framework and why
risk/reward is constant by construction, the six-factor equal-weighted
risk score and its four-of-six publish floor, signal strength tied to risk
level, persisted (not transient) signals, the strategy-scan execution
strategy reusing Feature 003, inherited gates, and the fixture strategy.

## Phase 1: Design and Contracts

### Data model

[data-model.md](data-model.md): three additive tables. Load-bearing
constraints: a signal is persisted only on a genuine trigger (no
negative-result rows); risk score/level are nullable together, never one
without the other; every signal links its exact contributing inputs.

### Contracts

- **strategy-signal-v1.md** — eight entry conditions, the level formulas,
  the six risk factors and their scoring, signal strength, and required
  test vectors.
- **strategy-signal.openapi.yaml** — two endpoints, per-strategy
  evaluation status (not just triggered signals), scan pagination and
  exclusion disclosure, problem-details errors.

### Security, privacy, and AI safety

- Reuse the existing owner session and CSRF controls exactly (SEC-001);
  the frontend client attaches the CSRF token from first implementation —
  Feature 003's T030 follow-up finding is not repeated here.
- No new external host; this feature calls no provider, live or fixture.
- FR-013 requires calibrated, non-guarantee, non-instruction language on
  every signal.

### Observability and operations

Matches Feature 003's corrected baseline (its `plan.md` Observability
amendment): global `CorrelationIdFilter` on every request, shared
`ProblemDetailsAdvice` for errors, and failure-class visibility carried in
the response itself (`EvaluationStatus`, per-factor `applicability`) rather
than a separate telemetry channel — consistent with every other read-only
endpoint in Features 002/003. No bespoke counters are planned; if a real
operational need for them emerges later, add them across every read
endpoint at once, not only this feature's.

### Test and evaluation strategy

| Requirement IDs | Test level | Fixtures | Expected evidence |
|---|---|---|---|
| FR-001 to FR-005 | Unit, property | One triggering + one non-triggering fixture per strategy; each strategy's own bar-count boundary | Exact trigger/no-trigger per strategy; correct `INSUFFICIENT_HISTORY` at the boundary |
| FR-002 (multi-trigger) | Unit | A fixture satisfying 2+ strategies at once | Every triggered strategy's own signal present |
| FR-006, FR-007 | Unit, boundary | 3-of-6 vs. 4-of-6 available risk factors | Score/level withheld vs. published at the exact floor |
| FR-008 | Property, replay | Identical inputs run twice | Identical signal, levels, risk score/level |
| FR-009 to FR-011 | Integration | Universe scan against a known fixture set | Exact match set; empty-result state; insufficient-history exclusion disclosed |
| FR-012 | Architecture/structural | N/A | No indicator/Breakout/Trend recomputation reachable from `strategy-signal-v1` |
| FR-013 | Component, acceptance review | All signal states | Non-guarantee framing present |
| FR-014 | Integration | A correction to a contributing bar | New signal revision; superseded stays queryable |
| DATA-001 to DATA-004 | Unit, integration | Cross-source conflict, missing regime | Withheld with reason; never zero/defaulted |
| SEC-001, SEC-002 | Security integration, negative | Unauthenticated, missing CSRF | Only owner succeeds |
| NFR-001, NFR-002 | Integration, timing | Representative fixture universe | p95 within baseline |
| NFR-003 | Component, Playwright, manual | All direction/risk/strength states | Non-colour indicator |

### Rollout, migration, and rollback

1. Apply Flyway `V004`. Validate the engine, persistence, contracts, and
   security against fixtures.
2. Deploy the frontend behind the existing build.
3. No gate closure needed; this feature opens none.

**Rollback**: no migration to reverse destructively; application code
rolls back to a version compatible with `V004`. A defective rule version
produces `strategy-signal-v2` and parallel results, never rewriting `v1`
history.

### Quickstart acceptance

[quickstart.md](quickstart.md) defines prerequisites, commands, P1-P3
acceptance paths, degraded paths, authorization checks, and accessibility
checks.

## Project Structure

### Feature documentation

```text
specs/004-strategy-signal-risk/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── strategy-signal-v1.md
│   └── strategy-signal.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source code affected

```text
finvera-be/src/
├── main/java/com/minhnb/finvera_be/stock/
│   ├── domain/strategy/          # strategy-signal-v1 engine (new)
│   ├── service/strategy/         # persistence, scan orchestration (new)
│   ├── controller/               # StockController or new StrategyController addition
│   ├── dto/                      # signal/scan DTOs (new)
│   ├── entity/, repository/      # 3 new tables (new)
│   └── resources/db/migration/V004__*.sql
└── test/...

finvera-fe/src/features/
├── stock-detail/components/stock-signals.tsx   # per-stock signals section (extends 002)
└── stock-strategy/                              # strategy scan page (new, mirrors 003's stock-screener)
```

**Structure decision**: extend the existing `stock` module (research
R-001); extend the existing `stock-detail` frontend feature for the
per-stock signals view (natural home next to technical/fundamentals/
valuation) and add a small new `stock-strategy` folder for the universe
scan (mirrors Feature 003's own `stock-screener` folder). No Python is
added, and `finvera-ai` is untouched.

## Traceability Summary

| Requirement IDs | Design artifact | Test evidence | Planned task group |
|---|---|---|---|
| FR-001 to FR-005 | `strategy-signal-v1.md` strategy table | Unit, boundary | Foundation, US1 |
| FR-006, FR-007 | `strategy-signal-v1.md` risk-factor section | Unit, boundary | US1, US2 |
| FR-008, FR-014 | research R-006 persistence model | Property, replay, integration | US1 |
| FR-009 to FR-011 | research R-007 scan strategy | Integration | US3 |
| FR-012 | research R-002 reuse discipline | Structural | Foundation |
| FR-013 | `disclaimerCode` | Component, acceptance review | US1-US3 |
| DATA-001 to DATA-004 | `strategy_signal_input`, applicability fields | Unit, integration | Foundation |
| SEC-001, SEC-002 | Inherited session, CSRF attached from the start | Security integration | Foundation |
| NFR-001, NFR-002 | Persisted results, two-pass scan | Timing | Cross-cutting |
| NFR-003 | Non-colour status contract | Component, Playwright, manual | Each story |

## Complexity Tracking

| Addition | Why Required Now | Simpler Alternative Rejected | Approval/ADR | Removal or Review Trigger |
|---|---|---|---|---|
| A new `strategy` sub-package rather than a new module | Every input is already owned by `stock`/`market`; no new aggregate or provider | A new top-level module | Constitution Principle VIII; research R-001; no ADR needed | Revisit if a future feature needs these primitives outside `stock` |
| Three new tables for persisted signals | SRS §16 models a signal as a dated, created event (`Created At`), and reproducibility (FR-008/DATA-001) needs input-linked persistence, the same reasoning every other Feature 002/003 derived result already follows | Transient, recomputed-per-request signals (Feature 003's own screener pattern) | research R-006 | Review if signal history is never actually read by any later feature |
| Uniform ATR-anchored levels across all eight strategies (one formula, not eight) | Keeps the design/test surface proportional; a volatility-anchored stop is a defensible, well-established convention across structurally different setups | A per-strategy stop/target convention | research R-003 | Revisit if the owner finds the uniform framework too coarse in practice |
| Signal Strength tied to Risk Level (not an independent per-strategy formula) | SRS names the field with no normative formula; reusing the already-designed risk score avoids inventing and testing eight more heuristics | A per-strategy margin-past-threshold heuristic | research R-005 | Revisit if risk-tied strength proves uninformative in practice |

No new service, broker, datastore, ML model, cache, or AI dependency is
introduced.

## Open Items Carried Into Tasks

1. `MarketReferenceDataService` needs one additive method to read Feature
   001's current regime assessment (mirroring Feature 003's
   `findInstrumentsByIds` addition) — a foundation task.
2. The exact frontend placement (a new "Signals" tab within the existing
   stock-detail page, vs. a dedicated route) is a task-level UI decision,
   constrained to: formatting only, never recomputing a level or score.
