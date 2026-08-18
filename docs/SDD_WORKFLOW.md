# Finvera Spec-Driven Development Workflow

## Purpose

This repository uses Spec Kit from the repository root. The active configuration
is in `.specify/`, agent skills are in `.agents/skills/`, and feature artifacts
are stored in `specs/`.

Do not run the workflow from the legacy nested `my-project/` scaffold. It is
retained only to avoid deleting pre-existing repository content and may be
removed in a separate cleanup change after confirming it is no longer needed.

## One Feature, One Directory

Each feature uses `specs/NNN-short-name/`:

```text
specs/NNN-short-name/
├── spec.md
├── checklists/
│   └── requirements.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/
├── quickstart.md
└── tasks.md
```

Keep features narrow enough that the P1 user story is independently valuable
and demonstrable. A cross-service feature is valid when the user journey truly
requires it; the plan must still make ownership and contracts explicit.

## Workflow

### 1. Specify

Invoke `$speckit-specify` with the user outcome, actors, scope, and known
constraints. The generated `spec.md` describes what and why, not framework or
class design.

Before moving on, ensure:

- P1/P2/P3 stories are ordered by value and independently testable.
- Given/When/Then scenarios cover success, failure, authorization, and stale or
  missing data where relevant.
- Requirements have stable IDs and measurable outcomes.
- Data provenance/freshness and AI grounding behavior are explicit.
- Out-of-scope behavior is named.

### 2. Clarify

Use `$speckit-clarify` for decisions that materially change scope, security,
financial semantics, or user experience. Resolve every
`[NEEDS CLARIFICATION]` before implementation. Put reasonable, non-material
defaults in Assumptions instead of blocking progress.

### 3. Plan

Use `$speckit-plan`. Planning produces `research.md`, `data-model.md`,
`contracts/`, and `quickstart.md` in addition to `plan.md`.

Research is mandatory for unknown provider behavior, data licensing, market
calendar/corporate-action semantics, dependency changes, and AI model choices.
Record each decision with rationale and alternatives.

Run the Constitution Check twice: before research and after design. A failed
gate blocks implementation unless an explicit exception is approved and
documented.

### 4. Review the Contracts

Before task generation, verify that:

- browser-to-Spring and Spring-to-AI boundaries are respected;
- API/event/tool schemas include versioning, errors, timestamps, provenance,
  pagination/idempotency where relevant;
- the data model declares precision, units, timezone, state transitions,
  ownership, retention, and migrations;
- dependency failures and graceful degradation are testable;
- AI evidence and citation structures are separate from generated prose.

### 5. Generate Tasks

Use `$speckit-tasks`. Every task must contain:

- an ID, user-story label, requirement IDs, and exact file paths;
- dependencies and `[P]` only when files and prerequisites do not conflict;
- an observable completion condition or command;
- tests/evaluations appropriate to the risk.

Implement one complete vertical story before optional breadth. Infrastructure
tasks are foundational only if a user story cannot work without them.

### 6. Analyze

Use `$speckit-analyze` to check cross-artifact consistency. Fix gaps such as a
requirement without tasks, a contract without tests, or a task that introduces
unapproved scope before writing production code.

### 7. Implement and Validate

Use `$speckit-implement` or execute `tasks.md` manually in dependency order.
Mark a task complete only after its verification passes. Keep specs, contracts,
and plans synchronized with discoveries.

Minimum service checks are listed in `AGENTS.md`. Run the feature's
`quickstart.md` as the final acceptance path and record any unavailable external
dependency or skipped check honestly.

## Requirement Traceability

Use these prefixes:

| Prefix | Meaning | Example |
|---|---|---|
| `FR-` | User-visible functional behavior | `FR-003` |
| `NFR-` | Performance, availability, accessibility, operability | `NFR-002` |
| `DATA-` | Source, quality, precision, time, retention | `DATA-004` |
| `SEC-` | Authentication, authorization, privacy, abuse | `SEC-002` |
| `AI-` | Retrieval, grounding, model, orchestration behavior | `AI-005` |
| `SC-` | Measurable success criterion | `SC-001` |

These prefixes are **feature-scoped**: they are numbered independently inside
each `specs/<feature>/` directory, so `FR-003` in one feature is unrelated to
`FR-003` in another.

Product-level capabilities carry `SRS-` identifiers assigned in section 58 of
the SRS (for example `SRS-MKT-05`, `SRS-NFR-07`). They live in a separate
namespace and are never renumbered. A feature spec should list the `SRS-` IDs
it realizes in its **SRS References** header, then define its own feature-scoped
requirements with testable detail.

Reference these IDs in plan decisions, contract descriptions, task text, test
names/display names where practical, and review notes. Do not renumber accepted
IDs; deprecate them with a reason.

## Decision Records

Use an ADR under `docs/adr/` for a durable cross-feature decision: a new
deployable service, database/broker, public contract strategy, provider, major
security model, or irreversible data representation. Feature-local choices stay
in `research.md` unless they become repository-wide policy.

An ADR records context, decision, alternatives, consequences, migration, and
status. It does not replace acceptance requirements.

## Definition of Ready

A feature is ready to implement when:

- specification checklist passes and clarifications are resolved;
- Constitution Check passes after design;
- research decisions and contracts are complete;
- tasks cover all in-scope requirements and acceptance scenarios;
- test data and external dependencies are obtainable;
- rollout, migration, and fallback are understood.

## Definition of Done

Use the repository `AGENTS.md` definition plus the feature's measurable success
criteria. “Code compiles” is not sufficient for a financial or AI feature.

