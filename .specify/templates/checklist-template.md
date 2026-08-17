# [CHECKLIST TYPE] Checklist: [FEATURE NAME]

**Purpose**: [What decision/readiness this checklist validates]  
**Created**: [DATE]  
**Feature**: [link to spec.md]  
**Inputs Reviewed**: [spec/plan/contracts/tasks or other artifacts]

<!-- Generate concrete, answerable items from feature artifacts. Retain relevant
Finvera categories below and remove categories that truly do not apply. A
checked item means evidence was reviewed, not merely that text exists. -->

## Scope and User Value

- [ ] CHK001 P1 is the smallest independently valuable and demonstrable journey.
- [ ] CHK002 In-scope and adjacent out-of-scope behavior are explicit.
- [ ] CHK003 Acceptance scenarios cover success and material failure states.
- [ ] CHK004 Success criteria are measurable and technology-agnostic.

## Requirement Quality and Traceability

- [ ] CHK005 Requirements are unambiguous, testable, and use stable IDs.
- [ ] CHK006 No unresolved `[NEEDS CLARIFICATION]` marker remains.
- [ ] CHK007 Every requirement maps to a scenario and verification measure.
- [ ] CHK008 Assumptions and dependencies are explicit and non-contradictory.

## Financial Data and Correctness

- [ ] CHK009 Source, freshness, as-of/effective time, timezone, units, currency,
  precision, and rounding are specified where applicable.
- [ ] CHK010 Missing, stale, delayed, duplicated, and conflicting data behavior
  is specified.
- [ ] CHK011 Corporate actions, trading calendar, costs, liquidity, and bias are
  addressed where they can affect results.
- [ ] CHK012 Deterministic outputs expose factors, assumptions, and rule/formula
  version sufficient for reproduction.

## Architecture and Contracts

- [ ] CHK013 Browser -> Spring -> AI and datastore ownership boundaries hold.
- [ ] CHK014 Public/internal API, event, and tool contracts are versioned and
  define errors, timestamps, provenance, and compatibility.
- [ ] CHK015 New infrastructure or dependencies have evidence and a rejected
  simpler alternative.
- [ ] CHK016 Migration, rebuild/re-index, rollout, and rollback are specified.

## Security, Privacy, and AI Safety

- [ ] CHK017 Authentication, object ownership, least privilege, and negative
  authorization scenarios are explicit.
- [ ] CHK018 PII/private financial data, secrets, retention, external model data
  flow, and logging exclusions are explicit.
- [ ] CHK019 AI output is grounded/cited, uncertainty-aware, and separated from
  deterministic calculations.
- [ ] CHK020 Prompt injection, unsafe tool use, invalid structured output, and
  model/provider failure are covered where applicable.

## Quality and Operations

- [ ] CHK021 Tests/evaluations match feature risk and cover requirement IDs.
- [ ] CHK022 Performance and quality thresholds have measurable verification.
- [ ] CHK023 Timeouts, bounded retry, degradation, health, metrics, logs, and
  correlation behavior are defined.
- [ ] CHK024 Quickstart proves P1 plus a critical negative/failure path.

## Findings

| Item | Status | Evidence / Required Change | Owner |
|---|---|---|---|
| [CHK-ID] | [Pass/Fail/N/A] | [link or concrete finding] | [owner] |

## Notes

- Use `N/A` only with a concrete reason.
- Link evidence instead of copying large sections.
- Unresolved critical failures block the next SDD gate.

