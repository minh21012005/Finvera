# Implementation Plan: [FEATURE]

**Feature Directory**: `[specs/NNN-feature-name]`  
**Date**: [DATE]  
**Spec**: [link to spec.md]  
**Status**: Draft

## Summary

[Primary requirement, affected user journey, and concise technical approach.]

## Technical Context

**Affected Projects**: [finvera-fe / finvera-be / finvera-ai / infrastructure]  
**Languages/Versions**: [read committed manifests; use NEEDS CLARIFICATION only
when a version change is proposed]  
**Primary Dependencies**: [existing and proposed dependencies]  
**Storage/State**: [PostgreSQL / Redis / Qdrant / files / N/A]  
**Interfaces**: [public REST / internal REST / event / AI tool / N/A]  
**Testing/Evaluation**: [unit, property, contract, integration, E2E, AI eval]  
**Target Environment**: [browser/server/container/cloud constraints]  
**Performance Goals**: [measurable feature-specific targets]  
**Availability/Degradation**: [dependency failures and required fallback]  
**Scale/Scope**: [users, symbols, documents, records, request rate]  
**Open Technical Unknowns**: [NEEDS CLARIFICATION items resolved in research.md]

## Constitution Check

*GATE: Complete before Phase 0 research and repeat after Phase 1 design. A
failed item blocks implementation unless the exception is explicitly approved
and recorded in Complexity Tracking or an ADR.*

- [ ] **Deterministic core**: All authoritative financial outputs are computed
  by deterministic, versioned logic with reproducible factors.
- [ ] **Evidence and time**: Sources, as-of/effective times, units, freshness,
  and citations are represented; stale/conflicting data behavior is defined.
- [ ] **Boundaries and ownership**: Browser -> Spring -> AI boundary is intact;
  module/service and PostgreSQL/Redis/Qdrant ownership is explicit.
- [ ] **Security and privacy**: Authentication, authorization, object ownership,
  data minimization, secrets, retention, and abuse threats are addressed.
- [ ] **Traceability**: Stories, requirements, contracts, tests, and tasks can be
  linked by stable IDs; no clarification marker remains.
- [ ] **Risk-based tests**: Numerical boundaries, authorization, contracts,
  persistence, failure paths, and AI evaluation are covered as applicable.
- [ ] **Resilience and observability**: Timeouts, retries, fallback, health,
  logs/metrics, correlation, and sensitive-data exclusions are defined.
- [ ] **Modular simplicity**: No unjustified service, Kafka use, datastore,
  framework, provider, or abstraction is introduced.

**Pre-research result**: [PASS/FAIL with notes]  
**Post-design result**: [PASS/FAIL with notes]

## System Context and Boundaries

### Affected User and System Flows

```text
[Concrete flow for this feature]
```

### Ownership

| Concern | Owning project/module | Reason |
|---|---|---|
| [capability/data] | [path/module] | [boundary rationale] |

### Interface Changes

| Interface | Change | Version/Compatibility | Contract Artifact |
|---|---|---|---|
| [public/internal/event/tool] | [summary] | [strategy] | [link] |

## Phase 0: Research

Resolve every technical unknown in `research.md` using:

```text
Decision: [chosen option]
Rationale: [evidence and trade-off]
Alternatives considered: [options and why not chosen]
Risks/validation: [what could disprove the decision]
```

Mandatory research topics when applicable:

- market-data source, licensing, freshness, calendar, and corporate actions;
- financial formula, precision, rounding, benchmark, and adjustment policy;
- external API/model/embedding/reranker behavior and limits;
- dependency additions/upgrades and compatibility;
- security/privacy/regulatory ambiguity;
- performance assumptions that determine design.

## Phase 1: Design and Contracts

### Data Model

Create `data-model.md` with entities/value objects, ownership, relationships,
validation, state transitions, precision/units, time semantics, provenance,
indexes, retention, migration, and rollback/rebuild behavior.

### Contracts

Create versioned artifacts under `contracts/` for every changed API, event, AI
tool, or provider boundary. Define authentication, request/response schemas,
errors, timestamps, provenance, pagination/idempotency, timeouts, and degraded
responses where relevant.

### Security, Privacy, and AI Safety

[Threats, ownership enforcement, data flows to external providers, prompt
injection/tool controls, secret handling, audit and retention decisions.]

### Observability and Operations

[Logs, metrics, traces/correlation, health indicators, alerts, dashboards,
dependency budgets, runbook impact, and data that MUST NOT be logged.]

### Test and Evaluation Strategy

| Requirement IDs | Test level | Fixture/dataset | Expected evidence |
|---|---|---|---|
| [IDs] | [unit/contract/integration/E2E/eval] | [source] | [assertion/metric] |

For AI features, state dataset version, retrieval and citation metrics,
groundedness/refusal thresholds, injection cases, model variability controls,
and human-review criteria.

### Rollout, Migration, and Rollback

[Deployment order, flags/compatibility window, data migration/backfill,
re-indexing, rollback triggers, and recovery steps.]

### Quickstart Acceptance

Create `quickstart.md` with runnable prerequisites, commands, P1 happy path,
critical failure/authorization path, and expected observable results. Link to
contracts rather than duplicating implementation.

## Project Structure

### Feature Documentation

```text
specs/[NNN-feature]/
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
[List only real repository paths and planned files/modules. Remove unused
projects.]
```

**Structure Decision**: [Why this placement preserves ownership and modularity.]

## Traceability Summary

| Requirement IDs | Design/Contract | Test/Evaluation | Planned Task Group |
|---|---|---|---|
| [IDs] | [links] | [strategy] | [phase/story] |

## Complexity Tracking

> Fill only for a constitution violation or material complexity addition.

| Violation/Addition | Why Required Now | Simpler Alternative Rejected | Approval/ADR | Removal or Review Trigger |
|---|---|---|---|---|
| [item] | [evidence] | [reason] | [link] | [trigger/date] |

