---
description: "Executable, traceable task list for a Finvera feature"
---

# Tasks: [FEATURE NAME]

**Input**: Design artifacts from `specs/[NNN-feature-name]/`  
**Required**: `spec.md`, `plan.md`, and applicable research, data model,
contracts, and quickstart artifacts  
**Goal**: Deliver independently testable user stories with evidence

## Task Format

```text
- [ ] T001 [P?] [US?] [Requirement IDs] Action with exact file path
      Verify: command or observable completion evidence
      Depends: task IDs or "none"
```

- `[P]` means the task can run in parallel without file or prerequisite
  conflicts. Omit it otherwise.
- `[US1]`, `[US2]`, and so on map implementation to user stories. Setup and
  shared foundation tasks do not need a story label.
- Every behavior task MUST reference requirement IDs.
- Every task MUST name exact paths and a verification method.
- Tests are mandatory when behavior, financial logic, authorization, storage,
  contracts, or AI behavior changes. Write them before or alongside behavior.

## Phase 1: Setup and Contract Baseline

**Purpose**: Establish only the structure and contracts required by this
feature.

- [ ] T001 [Requirement IDs] [Concrete setup/contract task with exact path]
      Verify: [command or review evidence]
      Depends: none

**Checkpoint**: Contracts are reviewable and no speculative infrastructure was
introduced.

---

## Phase 2: Foundational Prerequisites

**Purpose**: Complete prerequisites that block every in-scope user story, such
as a migration, provider fixture, shared authorization rule, or error envelope.

- [ ] T002 [Requirement IDs] [Foundational task with exact path]
      Verify: [specific test/command]
      Depends: T001

Include as applicable:

- schema migration plus rollback/rebuild strategy;
- typed public/internal API or event contract;
- source/provenance and time/precision primitives;
- authentication/ownership enforcement;
- external adapter with timeout/failure behavior;
- privacy-safe logging/metrics and health signal;
- deterministic fixtures or versioned AI evaluation dataset.

**Checkpoint**: P1 can be implemented and tested without another foundational
change.

---

## Phase 3: User Story 1 - [Title] (Priority: P1)

**Goal**: [Outcome delivered by this story]  
**Requirements**: [IDs]  
**Independent Test**: [Runnable proof from spec/quickstart]

### Tests and Evaluation

- [ ] T010 [P] [US1] [IDs] [Unit/property/contract/integration/E2E/eval task]
      Verify: test fails for the missing or incorrect behavior
      Depends: [IDs]

### Implementation

- [ ] T011 [US1] [IDs] [Smallest deterministic/domain behavior task and path]
      Verify: [targeted test]
      Depends: T010
- [ ] T012 [US1] [IDs] [Adapter/API/UI integration task and path]
      Verify: [targeted test or command]
      Depends: T011
- [ ] T013 [US1] [IDs] Add failure, authorization, staleness, and observability
      behavior in [exact paths]
      Verify: [negative tests and metric/log assertion]
      Depends: T012

**Checkpoint**: P1 passes its independent test and can be demonstrated without
P2 or P3.

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Outcome]  
**Requirements**: [IDs]  
**Independent Test**: [Runnable proof]

### Tests and Evaluation

- [ ] T020 [P] [US2] [IDs] [Test/evaluation task with exact path]
      Verify: test fails for missing behavior
      Depends: [foundational task IDs]

### Implementation

- [ ] T021 [US2] [IDs] [Implementation task with exact path]
      Verify: [targeted test]
      Depends: T020

**Checkpoint**: P2 works independently and P1 remains green.

---

## Phase 5: User Story 3 - [Title] (Priority: P3)

**Goal**: [Outcome]  
**Requirements**: [IDs]  
**Independent Test**: [Runnable proof]

### Tests and Evaluation

- [ ] T030 [P] [US3] [IDs] [Test/evaluation task with exact path]
      Verify: test fails for missing behavior
      Depends: [foundational task IDs]

### Implementation

- [ ] T031 [US3] [IDs] [Implementation task with exact path]
      Verify: [targeted test]
      Depends: T030

**Checkpoint**: Every selected story works independently.

---

## Final Phase: Cross-Cutting Validation and Release Readiness

- [ ] T090 [Requirement IDs] Run and reconcile all contract tests across changed
      boundaries
      Verify: [commands]
      Depends: [story completion tasks]
- [ ] T091 [Requirement IDs] Validate migrations, backfill/re-indexing, rollback,
      and dependency-degradation path
      Verify: [commands/scenario]
      Depends: [relevant tasks]
- [ ] T092 [Requirement IDs] Run `quickstart.md` including P1 and critical
      negative scenario
      Verify: expected results recorded
      Depends: T090, T091
- [ ] T093 [Requirement IDs] Update specs, contracts, ADRs, and operator/user
      documentation in [exact paths]
      Verify: traceability review has no orphan IDs
      Depends: T092
- [ ] T094 Run relevant repository quality gates
      Verify: `npm run lint`, `npm run build`, `.\mvnw.cmd test`, and/or
      `uv run pytest`/compile checks pass as applicable
      Depends: T093

## Dependencies and Parallel Execution

### Phase Dependencies

```text
Setup -> Foundation -> independently selectable story phases -> Final validation
```

Document real cross-story dependencies here. Do not claim stories are
independent if their acceptance tests require another story.

### Parallel Opportunities

- Mark `[P]` only when tasks touch different files/resources and have no unmet
  contract or data dependency.
- Tests for distinct modules may run in parallel after their fixtures/contracts
  exist.
- Frontend, backend, and AI implementation may proceed in parallel only after
  the relevant versioned interface contract is approved.
- Tasks editing the same migration, manifest, generated contract, or shared
  schema are not parallel.

## Requirement Coverage

| Requirement ID | Task IDs | Test/Evaluation Task | Status |
|---|---|---|---|
| [ID] | [tasks] | [task] | Planned |

All in-scope IDs MUST appear exactly once in this coverage table with at least
one implementation task and one verification path.

## Delivery Notes

- Prefer completing and validating P1 before optional stories.
- Never mark a task done based only on code presence.
- Record skipped/unavailable checks and their blocker; do not report them as
  passing.
- If implementation discovery changes behavior, update `spec.md`/`plan.md`
  before continuing.

