---
description: "Executable, traceable task list for a Finvera feature"
---

# Tasks: AI Analyst

**Input**: Design artifacts from `specs/007-ai-analyst/`
**Required**: `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/`, `quickstart.md`
**Goal**: Deliver independently testable user stories with evidence

## Task Format

```text
- [ ] T001 [P?] [US?] [Requirement IDs] Action with exact file path
      Verify: command or observable completion evidence
      Depends: task IDs or "none"
```

- `[P]` means the task can run in parallel without file or prerequisite
  conflicts. Omit it otherwise.
- `[US1]`..`[US4]` map implementation to user stories. Setup and shared
  foundation tasks do not need a story label.
- Every behavior task references requirement IDs.
- Tests are written before or alongside the behavior they cover.

## Phase 1: Setup and Contract Baseline

**Purpose**: Establish only the structure and contracts required by this
feature.

- [X] T001 [none] Machine-validate `contracts/orchestration-v1.md`'s
      required test-vector table is complete and `contracts/internal-
      api.openapi.yaml`/`contracts/public-api.openapi.yaml` parse with zero
      missing `$ref`s
      Verify: `python -c "import yaml; yaml.safe_load(open(...))"` plus the
      `$ref`-resolution check used for Features 005/006, run against both
      files in `specs/007-ai-analyst/contracts/`
      Depends: none
- [X] T002 [DATA-001] Add Flyway migration
      `finvera-be/src/main/resources/db/migration/V007__create_analyst_audit_tables.sql`
      creating `analyst_query`/`analyst_tool_call` exactly per
      `data-model.md` (audit-only, no business data)
      Verify: migration test asserting the schema (columns, checks,
      indexes) matches `data-model.md`
      Depends: T001
- [X] T003 [none] Scaffold the `finvera-be` `analyst` module package
      structure (`analyst/{entity,repository,service,controller,dto,
      provider}`) per ADR-0007 layering, empty of business logic
      Verify: `.\mvnw.cmd compile`
      Depends: T002
- [X] T004 [P] [none] Scaffold `finvera-ai/app/features/{chat,analysis,
      orchestration}/` per `finvera-ai/AGENTS.md`'s already-reserved
      structure, empty of business logic
      Verify: `uv run python -m compileall .`
      Depends: none
- [X] T005 [P] [none] Scaffold `finvera-fe/src/features/analyst/` folder
      structure
      Verify: `npm run build`
      Depends: none

**Checkpoint**: Contracts are reviewable and no speculative infrastructure
was introduced.

---

## Phase 2: Foundational Prerequisites

**Purpose**: Complete prerequisites that block every in-scope user story.

- [X] T006 [SEC-002] Extend Feature 006's `InternalApiKeyFilter`
      (`finvera-be`) and its `finvera-ai` counterpart to require and
      validate `X-Internal-Api-Key` on every new path this feature adds —
      `/internal/v1/tools/**` (hosted by `finvera-be`) and
      `/internal/v1/analyst/**` (hosted by `finvera-ai`) — from the start,
      in both directions (research R-003)
      Verify: security test — a request to each new endpoint with a
      missing/invalid key returns `401` on both services
      Depends: T003, T004
- [X] T007 [FR-002] Implement the `ToolName` allowlist and code-level
      argument-schema validation in
      `finvera-ai/app/features/orchestration/allowlist.py`
      (`orchestration-v1` U-1) — rejects any proposed tool/argument the
      model emits that does not match the fixed allowlist
      Verify: unit test — a proposed call with an unlisted tool name or a
      malformed argument is rejected before dispatch
      Depends: T004
- [X] T008 [none, research R-002] Implement explicit `ownerId` propagation
      and mismatch rejection across all three hops (`finvera-be` ->
      `finvera-ai` -> `finvera-be`'s tool endpoints), in
      `finvera-be/.../analyst/service` and
      `finvera-ai/app/features/orchestration/dispatch.py`
      (`orchestration-v1` U-2)
      Verify: integration test — a tool call carrying an `ownerId` other
      than the originating session's is rejected identically to an invalid
      argument
      Depends: T006
- [X] T009 [none] Add `finvera.analyst.max-tool-calls` (default 10,
      research R-005) and `finvera.analyst.tool-call-timeout` (default
      10s, research R-010) configuration to `finvera-be`'s and
      `finvera-ai`'s settings
      Verify: config test — defaults load; both values are overridable
      Depends: T003
- [X] T010 [DATA-001, DATA-002] Implement the nine thin `ToolService`
      delegates in `finvera-be/.../analyst/service/` (Market, Stock,
      Technical, Fundamental, Valuation, Portfolio positions/analytics,
      News, Screening), each calling its owning Feature 001-005 service
      unmodified, exposed via `/internal/v1/tools/*` controllers in
      `finvera-be/.../analyst/controller/ToolController.java`
      Verify: integration test per tool — response exactly matches calling
      the owning feature's existing service directly, including its
      unmodified `asOf`/effective-time field (DATA-002)
      Depends: T006, T009
- [X] T011 [AI-002] Implement `AnalystQueryService`/`AnalystToolCallService`
      audit-logging (research R-011) in
      `finvera-be/.../analyst/service/AnalystQueryService.java` — writes
      `analyst_query` (truncated `questionPreview` + `questionHash`, never
      full text) and `analyst_tool_call` rows
      Verify: unit test — the full raw question/answer text is never
      persisted, only the truncated preview and hash
      Depends: T002
- [X] T012 [none, research R-005] Implement the orchestration dispatch loop
      in `finvera-ai/app/features/orchestration/dispatch.py` (Gemini
      native function-calling, allowlist/bound enforcement from T007/T009,
      per-call timeout, calling back into `finvera-be`'s `/tools/*` and
      Feature 006's existing `/internal/v1/retrieve`/`/synthesize`
      unchanged)
      Verify: `orchestration-v1.md`'s required test-vector table cases for
      allowlist rejection, bound reached, and per-call timeout
      Depends: T007, T008, T010
- [X] T013 [AI-001, DATA-002] Implement structured-claim attribution
      verification (`orchestration-v1` steps 1-6) in
      `finvera-ai/app/features/orchestration/attribution.py`, including
      setting each surviving claim's `asOf` programmatically from its tool
      call's own response `asOf` (never model-generated)
      Verify: unit test — a claim that misstates a dispatched tool's actual
      response value is dropped, never passed through; a surviving claim's
      `asOf` exactly equals its tool call's actual response `asOf`
      Depends: T012

**Checkpoint**: US1 can be implemented and tested without another
foundational change.

---

## Phase 3: User Story 1 - Ask a Question and Get a Tool-Grounded Answer (Priority: P1)

**Goal**: A question answerable from structured tools returns an answer
whose claims are provably attributed to and verified against real tool
output.
**Requirements**: FR-001, FR-002, FR-003, FR-005, FR-011 to FR-015,
AI-001, AI-004, SEC-001 to SEC-003, NFR-001, NFR-003, NFR-004
**Independent Test**: Ask a question answerable entirely from structured
tools for a known symbol; verify the answer's claims exactly match the
underlying tool output, each attributed. Ask a question needing an
unlisted capability and verify a stated limitation.

### Tests and Evaluation

- [X] T014 [P] [US1] [FR-001, FR-002, FR-003, AI-001] Contract/integration
      test for `POST /internal/v1/analyst/ask` — a single-structured-tool
      question, asserting the returned claim exactly matches that tool's
      actual response field
      Verify: test fails before T012/T013 exist; passes after
      Depends: T012, T013
- [X] T015 [P] [US1] [FR-005, AI-004] Unit test — a question needing no
      allowlisted tool returns the "outside current capability" state
      (`orchestration-v1` step 5), not a fabricated answer
      Depends: T012
- [X] T016 [P] [US1] [FR-011, NFR-003] Integration test — a question
      engineered to require more than `finvera.analyst.max-tool-calls`
      dispatches stops at the bound; `toolCallBoundReached = true`;
      disclosed partial answer
      Depends: T009, T012
- [X] T017 [P] [US1] [FR-012] Integration test — one allowlisted tool call
      simulated to fail/time out; answer discloses that part as degraded
      while other successful tool results still contribute
      Depends: T012
- [X] T018 [P] [US1] [FR-013] Unit test — two sequential, unrelated
      questions (no `priorTurns` supplied) show no cross-question memory:
      the second question's dispatch/attribution is unaffected by the
      first
      Depends: T012
- [X] T019 [P] [US1] [FR-014, NFR-001] Integration test — `/analyst/ask`
      begins streaming (`tool_call` or `delta` event) within the
      20-second/NFR-001 budget for a representative multi-tool question
      Depends: T012

### Implementation

- [X] T020 [US1] [FR-001, FR-014] Implement `finvera-be`'s public
      `POST /api/v1/analyst/ask` in
      `finvera-be/.../analyst/controller/AnalystController.java` — session/
      CSRF-gated, resolves `ownerId`, calls `finvera-ai`'s
      `/internal/v1/analyst/ask`. **`tool_call` and `delta` events relay
      verbatim; the `final` event is NOT a pass-through** — it MUST be
      built via T021's resolution step before being written to the
      response stream (mirrors Feature 006's F3 finding: "relay" applies
      only to the events whose internal and public shapes are already
      identical)
      Verify: contract test against `public-api.openapi.yaml`, asserting
      the `final` event never reaches the client unresolved
      Depends: T014
- [X] T021 [US1] [FR-003, DATA-002] Implement `finvera-be`'s resolution of
      the internal `StructuredClaim{sequenceNo, fieldPath, claimedValue,
      asOf}` into the public `StructuredClaim{toolName, sourceField,
      asOf}` shape (research R-009's public-resolution note), cross-
      referencing each claim's `sequenceNo` against the `ToolCallResult`
      already streamed in `tool_call` events to recover `toolName` and
      copying `asOf` unmodified (never regenerated or defaulted), before
      emitting the public `final` event
      Verify: unit test — public shape and `asOf` correctly resolved for
      every surviving structured claim
      Depends: T020
- [X] T022 [US1] [SEC-001, SEC-002, SEC-003] Wire session/CSRF/owner-only
      enforcement on `/analyst/ask` and confirm provider requests carry
      only the current question's tool results/context (data-minimization
      review)
      Verify: security tests (unauthenticated -> 401, missing CSRF -> 403,
      cross-owner attempt -> 401/404-equivalent)
      Depends: T020
- [X] T023 [US1] Implement the frontend Analyst chat entry point
      (`finvera-fe/src/features/analyst/AskAnalyst.tsx`): question input,
      streamed answer rendering, `tool_call` progress indicators,
      per-claim attribution display
      Verify: Vitest component test; manual streaming check against the
      local runtime
      Depends: T020
- [X] T024 [US1] [NFR-004] Add non-colour indicators for attribution/
      degraded/limitation states in the chat component
      Verify: accessibility review
      Depends: T023

**Checkpoint**: P1 passes its independent test and can be demonstrated
without P2, P3, or P4.

---

## Phase 4: User Story 2 - Combine Structured Data and Document Retrieval (Priority: P2)

**Goal**: A question needing both a computed value and document/news
content returns both, distinctly attributed.
**Requirements**: FR-004, FR-010, AI-003, DATA-003
**Independent Test**: Ask a question needing both a structured tool value
and a retrieved document/news passage; verify both appear, each correctly
and distinctly attributed.

### Tests and Evaluation

- [X] T025 [P] [US2] [FR-004, FR-010] Integration test — a combined
      question dispatches both a structured tool and the Research/RAG
      tool; the answer contains both, distinctly attributed, never blended
      Depends: T012, T013
- [X] T026 [P] [US2] [AI-003] Eval test reusing Feature 006's
      prompt-injection fixtures, reached via the Research/RAG tool inside
      an orchestrated question; 0% behavior deviation
      Depends: T012
- [X] T027 [P] [US2] [orchestration-v1 U-6] Property/replay test — the
      same combined question, run twice against an unchanged data
      snapshot, selects the same tools and produces the same attributed
      values both times; prose may differ
      Depends: T012, T013

### Implementation

- [X] T028 [US2] [FR-004] Wire the Research/RAG tool inside
      `finvera-ai/app/features/orchestration/dispatch.py` to call Feature
      006's existing `/internal/v1/retrieve`/`/synthesize` unchanged, with
      `documentClaims` resolved to the public citation shape identically
      to Feature 006's own resolution
      Verify: T025 passes
      Depends: T012, T020
- [X] T029 [US2] [DATA-003] Implement conflicting-source disclosure in the
      synthesis prompt framing and response assembly — a structured value
      and a document claim disagreeing on the same fact are both surfaced,
      never silently reconciled
      Verify: fixture test with a deliberately conflicting structured/
      document pair
      Depends: T028

**Checkpoint**: P2 works independently and P1 remains green.

---

## Phase 5: User Story 3 - Get a Deterministic Output Explained in Plain Language (Priority: P3)

**Goal**: An existing signal/indicator/valuation/risk factor is explained
using only its own supplied evidence.
**Requirements**: FR-006
**Independent Test**: Request an explanation of an existing deterministic
output; verify the explanation references only the supplied evidence
factors.

### Tests and Evaluation

- [X] T030 [P] [US3] [FR-006] Unit test — an explanation attempt that
      references a factor not in the supplied input is rejected, retried
      once, then returns the generic explanation-unavailable state on a
      second failure
      Depends: T004

### Implementation

- [X] T031 [US3] [FR-006] Implement `finvera-ai`'s non-orchestrated
      `POST /internal/v1/analyst/explain` in
      `finvera-ai/app/features/analysis/explain.py` (single generation
      call plus the faithfulness check, `orchestration-v1`)
      Verify: T030 passes
      Depends: T030
- [X] T032 [US3] [FR-006] Implement `finvera-be`'s
      `POST /api/v1/analyst/explanations` in
      `finvera-be/.../analyst/controller/AnalystController.java` — session/
      CSRF-gated, calls `finvera-ai`'s `/internal/v1/analyst/explain`, no
      orchestration
      Verify: contract test against `public-api.openapi.yaml`
      Depends: T031
- [X] T033 [US3] Add an "Explain" trigger on the existing signal/valuation
      displays (`finvera-fe/src/features/stock-detail` and
      `strategy-signal` components from Features 002/004), sending their
      already-fetched evidence factors to
      `finvera-fe/src/features/analyst/ExplainButton.tsx`
      Verify: Vitest component test
      Depends: T032

**Checkpoint**: US3 works independently.

---

## Phase 6: User Story 4 - Describe a Screening Criterion in Plain Language (Priority: P4)

**Goal**: A natural-language screening criterion converts to the exact
structured filters Feature 003's engine accepts.
**Requirements**: FR-007, FR-008, FR-009
**Independent Test**: Submit an unambiguous natural-language criterion;
verify the converted filters are shown and the results match Feature 003's
screener given those same filters directly.

### Tests and Evaluation

- [X] T034 [P] [US4] [FR-007, FR-008] Integration test — an unambiguous
      natural-language criterion converts to structured filters shown in
      the `tool_call` event, and the results are identical to calling
      Feature 003's screener directly with those filters
      Depends: T012
- [X] T035 [P] [US4] [FR-009] Unit test — a criterion the conversion scores
      below the 0.6 confidence floor carries a disclosed `ambiguityNote`,
      never a silent guess
      Depends: T012

### Implementation

- [X] T036 [US4] [FR-007] Implement natural-language-to-filter conversion
      in `finvera-ai/app/features/orchestration/screener_conversion.py`
      (`orchestration-v1` NL-conversion steps 1-2), validated against
      Feature 003's real filter schema before dispatch
      Verify: T034, T035 pass
      Depends: T012
- [X] T037 [US4] [FR-007] Implement `finvera-be`'s
      `POST /internal/v1/tools/screener/executions` in
      `finvera-be/.../analyst/controller/ToolController.java`, delegating
      to Feature 003's existing screener engine unmodified
      Verify: integration test — identical results to Feature 003's own
      public endpoint given the same filters
      Depends: T010, T036
- [X] T038 [US4] [FR-008, FR-009] Surface the converted filters and any
      `ambiguityNote` in the Screening `tool_call` event's UI rendering
      Verify: Vitest/Playwright component test
      Depends: T037, T023

**Checkpoint**: Every selected story works independently.

---

## Final Phase: Cross-Cutting Validation and Release Readiness

- [X] T039 [none] Run and reconcile all contract tests across changed
      boundaries (`orchestration-v1.md`'s full test-vector table,
      `internal-api.openapi.yaml`, `public-api.openapi.yaml`)
      Verify: `uv run pytest`, Spring contract tests, and the YAML/`$ref`
      validation script all pass
      Depends: T014-T038
- [X] T040 [DATA-001] Validate the `V007` migration and its rollback: drop
      and recreate `analyst_query`/`analyst_tool_call`, confirming no
      business-data dependency on either table
      Verify: migration up/down test
      Depends: T002
- [X] T041 [SEC-001, SEC-002, FR-015] Run a bidirectional internal-API
      security sweep across every new endpoint added by this feature
      (`/internal/v1/tools/*` and `/internal/v1/analyst/*`, both hosting
      directions), plus the `ownerId`-substitution case
      Verify: security integration tests — every case rejects correctly
      Depends: T006, T008
- [X] T042 [none] Run `quickstart.md`'s P1 to P4 acceptance paths, degraded
      paths, and authorization checks end to end
      Verify: expected results recorded
      Depends: T039, T041
- [X] T043 [SC-001 to SC-005] Run the `analyst-eval-v1` AI evaluation
      dataset (structured-only, combined, out-of-capability, NL-screener,
      and reused Feature 006 injection fixtures) against SC-001 to SC-005's
      thresholds
      Verify: eval report meets 100%/100%/100%/100%/0%-deviation
      thresholds respectively
      Depends: T039
- [X] T044 [none] Traceability review: confirm every FR/DATA/SEC/AI/NFR ID
      in `spec.md` maps to at least one task above and no orphan IDs remain
      Verify: cross-reference against the Requirement Coverage table below
      Depends: T042, T043
- [X] T045 [none] Run relevant repository quality gates
      Verify: `.\mvnw.cmd test`, `npm run lint`, `npm run build`,
      `npx playwright test`, `uv run python -m compileall .`,
      `uv run pytest`
      Depends: T044

## Dependencies and Parallel Execution

### Phase Dependencies

```text
Setup -> Foundation -> US1 (P1) -> US2 (P2) / US3 (P3) / US4 (P4) -> Final validation
```

US2, US3, and US4 each depend only on Foundation (T006-T013) plus, for
their frontend tasks, US1's chat entry point (T023) as a mounting surface
— **not** on each other's business logic; US3's explanation path in
particular never calls the orchestrator at all (research R-006). They are
listed after US1 by priority order, not by a hard implementation
dependency, except where a task explicitly names one (T033/T038 depend on
T023 for UI composition only).

### Parallel Opportunities

- T004 and T005 may run in parallel with each other and with T001-T003
  (different services/files).
- Every `[P]`-marked test task within a phase may run in parallel once its
  `Depends` tasks are complete.
- Frontend (T023, T033, T038) may proceed in parallel with `finvera-ai`
  orchestration work (T012-T013, T031, T036) once the relevant contract
  (`orchestration-v1.md`, both OpenAPI files) is approved, per T001.
- T006 (shared `InternalApiKeyFilter` extension) is not parallel with any
  other task touching the same filter class.

## Requirement Coverage

| Requirement ID | Task IDs | Test/Evaluation Task | Status |
|---|---|---|---|
| FR-001 | T014, T020 | T014 | Planned |
| FR-002 | T007, T014 | T014 | Planned |
| FR-003 | T013, T014, T021 | T014 | Planned |
| FR-004 | T025, T028 | T025 | Planned |
| FR-005 | T015 | T015 | Planned |
| FR-006 | T030, T031, T032, T033 | T030 | Planned |
| FR-007 | T034, T036, T037 | T034 | Planned |
| FR-008 | T034, T038 | T034 | Planned |
| FR-009 | T035, T038 | T035 | Planned |
| FR-010 | T025, T029 | T025 | Planned |
| FR-011 | T016 | T016 | Planned |
| FR-012 | T017 | T017 | Planned |
| FR-013 | T018 | T018 | Planned |
| FR-014 | T019, T020 | T019 | Planned |
| FR-015 | T022, T041 | T041 | Planned |
| DATA-001 | T010, T040 | T040 | Planned |
| DATA-002 | T010, T013, T021 | T013 | Planned |
| DATA-003 | T029 | T029 | Planned |
| SEC-001 | T022, T041 | T041 | Planned |
| SEC-002 | T006, T041 | T041 | Planned |
| SEC-003 | T022 | T022 | Planned |
| AI-001 | T013, T014 | T014 | Planned |
| AI-002 | T011 | T011 | Planned |
| AI-003 | T026 | T026 | Planned |
| AI-004 | T015 | T015 | Planned |
| NFR-001 | T019 | T019 | Planned |
| NFR-002 | T017, T042 | T042 | Planned |
| NFR-003 | T009, T016 | T016 | Planned |
| NFR-004 | T024 | T024 | Planned |

All in-scope IDs appear at least once with an implementation task and a
verification path. Tasks with no requirement tag (T001, T003, T004, T005,
T008, T012, T023, T027, T038's UI portion, T039, T043, T044, T045) are
setup, cross-cutting mechanism, orchestration-internal, or validation work
that no single requirement ID owns — the same pattern Features 005/006
already established for foundational/validation tasks.

## Pre-Implementation Analysis (2026-08-20)

A self-critical pass (`.agents/skills/speckit-analyze/SKILL.md`'s Detection
Passes — Duplication, Ambiguity, Underspecification, Constitution
Alignment, Coverage Gaps, Inconsistency) was run across `spec.md`,
`research.md`, `data-model.md`, both OpenAPI contracts, `orchestration-v1.md`,
`plan.md`, and `tasks.md` after task generation, mirroring Features
005/006's own pre-implementation gate. All findings below were fixed before
this feature was considered plan-complete.

| ID | Category | Severity | Location(s) | Summary | Resolution |
|---|---|---|---|---|---|
| F1 | Coverage Gap | HIGH | `contracts/internal-api.openapi.yaml`, `contracts/public-api.openapi.yaml` `StructuredClaim`; `contracts/orchestration-v1.md` | `spec.md`'s DATA-002 requires every structured claim to retain its tool's as-of/effective time, but neither the internal nor public `StructuredClaim` schema carried any timestamp field — the requirement was stated but not actually satisfiable by the designed contracts. | Added a required `asOf` field to both `StructuredClaim` schemas, set programmatically (never model-generated) in `orchestration-v1.md` step 3; `tasks.md` T013/T021 and the DATA-002 coverage row updated to verify it. |
| F2 | Ambiguity | MEDIUM | `tasks.md` T020 | "Relays the SSE stream" implied the whole response was a pass-through, the same class of ambiguity Feature 006's F3 finding caught in its own T026 — but only `tool_call`/`delta` events are verbatim; the `final` event requires T021's cross-reference resolution first. | Reworded T020 to explicitly state the `final` event is not a pass-through and must be built via T021 before being written to the stream. |
| F3 | Underspecification | MEDIUM | `spec.md` FR-009; `research.md` R-007 | FR-009 offers two alternative resolutions for an ambiguous NL-screening criterion ("disclosed interpretation" or "ask for clarification"), but only the disclosed-interpretation path was designed, with no recorded rationale for not building the clarification round-trip. | Added an "Alternatives considered" entry to research R-007 explaining the interactive-clarification branch was rejected because it would require server-side conversational state, directly contradicting FR-013/research R-011's stateless, audit-only design — revisit only alongside a deliberate multi-turn decision (SRS-JRN-01, Post-MVP). |
| F4 | Inconsistency | LOW | `spec.md` SEC-002 | SEC-002's wording named only the `finvera-ai`-to-`finvera-be` tool-call direction, under-describing that the same key requirement also covers the new `finvera-be`-to-`finvera-ai` orchestration/explanation endpoints — narrower than what `research.md` R-003 and `tasks.md` T006 actually specify/implement. | Broadened SEC-002's wording to explicitly name both new hosting directions. |
| F5 | Underspecification | LOW | Both OpenAPI contracts' `priorTurns` field; `research.md` | `priorTurns` was declared in both request schemas with no behavioral definition anywhere of how it affects tool selection, synthesis, or verification when supplied. | Added a paragraph to research R-005 defining `priorTurns` as read-only prior context that never alters allowlist enforcement, `ownerId` propagation, or attribution/citation verification — every claim is still verified only against the current request's own tool calls. |

**Coverage**: 29/29 spec.md requirement IDs (FR-001 to FR-015, DATA-001 to
DATA-003, SEC-001 to SEC-003, AI-001 to AI-004, NFR-001 to NFR-004) map to
at least one implementation task and one verification task in the
Requirement Coverage table above.

**Constitution alignment**: No MUST-principle violation found. Principle
I's deterministic-core boundary and Principle III's explicit
browser/AI-service/data boundary were the two principles most load-bearing
for this feature's design; both are enforced in code
(`orchestration-v1.md` attribution verification; the new
`/internal/v1/tools/*` surface), not merely stated.

**Unmapped tasks**: None beyond the deliberately requirement-tag-free
setup/cross-cutting/validation tasks already listed above (the same
pattern Features 005/006 established).

**Critical issues**: 0.

**Outcome**: All five findings were fixed in place across `spec.md`,
`research.md`, both OpenAPI contracts, `orchestration-v1.md`, and
`tasks.md`; both contracts re-validated after edits (11 paths/21 schemas/26
refs for `internal-api.openapi.yaml`, 2 paths/12 schemas/16 refs for
`public-api.openapi.yaml`, zero missing `$ref`s in either). This feature's
planning artifacts are internally consistent and ready for `/speckit-
implement` whenever implementation begins.

## Delivery Notes

- Prefer completing and validating P1 (US1) before US2/US3/US4; US3
  (explanation) has the fewest dependencies and may be pulled forward if
  useful, since it never touches the orchestrator.
- Never mark a task done based only on code presence.
- Record skipped/unavailable checks and their blocker; do not report them
  as passing.
- If implementation discovery changes behavior, update `spec.md`/`plan.md`
  before continuing.

## Post-Implementation Analysis — retroactively added (2026-08-21)

Unlike Features 005/006, this file shipped with all 45 tasks marked `[X]`
and no post-implementation cross-artifact analysis section — the gap
itself became one of the first findings below. This section is added
retroactively, alongside the remediation it documents, so the omission
does not repeat silently on the next feature.

## Post-Review Remediation (2026-08-21)

An independent, evidence-based code-quality review (four parallel reviews
of `finvera-ai` orchestration, `finvera-be` schema/security,
`finvera-be` service/controller, and `finvera-fe`, each checked against
the exact text of `orchestration-v1.md` and `data-model.md`) found that
this feature's actually-delivered behavior diverged from both its own
contracts and, in two cases, from being reachable by a user at all —
despite every task above being marked complete and (per this file's own
now-added retroactive analysis) 100% of requirement IDs mapping to a task.
The two most severe findings were re-verified by hand against the running
application (a live browser check with the auth gate mocked, screenshotted)
rather than trusted from the review alone. All findings below were fixed
in place, not merely noted, per `AGENTS.md`.

**Foundational findings** (not bugs fixable by a small patch — a real gap
between the contract's design and what was built):

| Finding | Evidence | Fix |
|---|---|---|
| Orchestration never called an LLM for tool selection or answer synthesis on the core ASK path (US1/US2). `chat/service.py`'s `plan_tools()` was pure Vietnamese-keyword regex matching; the "answer" was hand-written Python f-string templates per tool type. `orchestration-v1.md`'s entire dispatch algorithm begins "proposedCalls = model's function-calling output" — this was not what the code did. U-5 attribution verification was checking claims the same code had just mechanically derived from the tool data it verified against, so it could never catch a real misstatement. | Read `chat/service.py` directly line by line to confirm; `self.llm_adapter` was invoked in exactly one place (screener NL conversion) in the whole ask path. | Implemented real Gemini native function-calling for tool proposal (`GeminiGenerationAdapter.propose_tool_calls`, new) and real LLM-driven synthesis with inline `[T<seq>:<field>=<value>]` claim tags, extracted and verified against actual tool output (never trusted). `plan_tools()` is kept, unchanged, as the deterministic offline/test fallback when no real provider is configured or the online call fails — preserving every existing test's determinism while making the actual online path real. Regression tests added in `finvera-ai/app/features/chat/tests/test_llm_orchestration.py` covering: real LLM proposal used when online, fallback to the heuristic on failure, a misstated claim being dropped, and a mid-stream synthesis failure falling back to the offline templates without a mixed/partial answer. |
| The RESEARCH_RAG tool was unreachable in production. `dispatch.py` called `retrieve_ranked_chunks` with a signature that doesn't exist (async keyword args vs. the real sync `RetrieveRequest`-based function) and read response fields (`document_id`, `source_type`, `title`, `content_text`) that don't exist on `RankedChunk`. Even fixed, the architecture had no path to real chunk text: Qdrant/`RankedChunk` never carries `content_text` (data-model.md) — only finvera-be's Postgres-backed `RetrievalService` can resolve it, and finvera-ai's orchestrator never called out to it. | Read `dispatch.py`, `retrieval.py`, and `data-model.md` directly; confirmed every test exercising this path mocks `OrchestrationDispatcher` entirely, hiding the break completely. | Added `POST /internal/v1/tools/research/retrieve` (`InternalToolController`), reusing Feature 006's own `RetrievalService.retrievePassages` unmodified (same owner-scoped Postgres resolution `/research/retrieve` already uses). `dispatch.py`'s special-cased, broken RESEARCH_RAG branch was deleted entirely — it now routes through `BackendToolClient` exactly like the other eight tools, which is simultaneously the fix and a simplification. `PassageResponse` gained a `chunkId` field (needed for citation block-mapping); `RetrievalService` gained `resolveChunkCitation(chunkId, ownerId)`, and `AskService.java` (Feature 006) was refactored to use it too, removing its own duplicate inline resolution logic. |

**Critical/High findings:**

| Finding | Fix |
|---|---|
| Citation/structured-claim resolution repeated Feature 006's own F3/F4 bug, unfixed: `DocumentClaimDto` (bare `{chunkId, claimText}`) was passed straight through as the public shape with no `sourceType/sourceId/sourceTitle/location/source` resolution; `PublicStructuredClaimDto` was missing the contract-required `sequenceNo` and leaked the raw internal `fieldPath`/`claimedValue` instead of a human `sourceField` label. | `AnalystService.java` now resolves each document claim's `chunkId` via `RetrievalService.resolveChunkCitation` (owner-scoped) into the full public `Citation` shape, and resolves `sourceField` via a curated field-path-to-Vietnamese-label map (fallback: humanized raw path). New tests in `AnalystServiceTests.java` capture and assert on the *actual* SSE JSON sent (via an `ArgumentCaptor<SseEmitter.SseEventBuilder>`), not just "an emitter was returned" — closing the same superficial-test gap Feature 006 had. |
| The public `AskAnalystRequest` contract declared `additionalProperties: false` with only `question`/`priorTurns`, but the frontend (and the already-wired finvera-be/finvera-ai DTOs) send/accept `symbol` — a real, useful, already end-to-end feature the contract simply never documented. | Updated `public-api.openapi.yaml` to add `symbol` as a documented optional field, rather than deleting the working feature to match an incomplete contract. |
| The owner's full raw question (up to 2000 chars) was persisted verbatim into `analyst_tool_call.arguments` (jsonb) whenever the RESEARCH_RAG tool fired, defeating AI-002's "preview + hash only, never full text" design for a routine case. Separately (found while fixing this): `arguments` was serialized via Java's `Map.toString()` (e.g. `{query=...}`), which is not valid JSON — the jsonb column would reject it outright for any tool call with non-trivial arguments. | `AnalystService.serializeArgumentsForAudit` now truncates any string argument value over 300 chars (matching `question_preview`'s own limit) and serializes via the real `ObjectMapper`, not `toString()`. Regression test added asserting the persisted value is valid, parseable JSON with the long value truncated to exactly 300 chars. |
| `ownerId` was accepted but structurally unused on 7 of 9 `/internal/v1/tools/*` endpoints (never read in the delegate body; portfolio/news endpoints ignored the parameter entirely) — U-2's "reject a mismatched ownerId identically to an invalid argument" had no enforcement path at this hop, mitigated only by the deployment being single-tenant today, not by design. | Added a shared `requireOwner(ownerId)` check (via `OwnerScopedAccess`) at the top of every `InternalToolController` endpoint, rejecting a missing or mismatched `ownerId` with 404 (matching the "collapse to not-found" convention Features 005/006 already establish). New tests cover missing-ownerId and mismatched-ownerId rejection. |
| **The entire AI Analyst page was unreachable from the running application.** `AnalystPage`/`AskAnalyst` was fully built and unit-tested in isolation, but `router.ts` had no `/analyst` path and `app.tsx` never imported or rendered `AnalystPage` — on top of the already-known dead `ExplainButton`, the primary chat interface itself had no route. Found while writing this remediation's own E2E test, after all four original review agents and the initial manual review missed it. | Added `isAnalystPath`/`ANALYST_PATH` to `router.ts`, wired `AnalystPage` into `app.tsx`, added an "AI Analyst →" nav button on the market overview page matching the existing nav pattern. Verified directly in a live browser (dev server + Playwright, auth gate mocked since finvera-be wasn't running for this check): `/analyst` renders the full chat UI correctly end-to-end at the DOM level. New `router.test.ts` regression test. New `tests/e2e/analyst-ask.spec.ts` P1 journey (question submission and nav-link click) for when the full stack is available. |
| The "Explain" trigger (T033, marked `[X]`) was never actually wired into any real signal/valuation display — `ExplainButton` was fully built and tested in isolation but had zero callers outside its own component/test files. | Wired into `stock-signals.tsx` (`SIGNAL`, using only `DEFINED`-applicability risk factors as evidence) and `stock-valuation.tsx` (`VALUATION_CLASSIFICATION`, using only `DEFINED` metrics), both now threading `symbol` from `stock-detail-page.tsx`. Existing signal/valuation test files updated for the new required `symbol` prop. |
| SSE `ToolCallEvent.status` only had two frontend-typed states (`SUCCEEDED`/`FAILED`) though the contract and backend both emit a `STARTED` state first — every in-flight tool call rendered with the failure color and the literal label "[Thất bại]" (Failed). Cards were also never de-duplicated by `sequenceNo`, so the same call produced two cards. | Added the `STARTED` state with its own (amber, "[Đang xử lý…]") styling; `onToolCall` now replaces the existing card by `sequenceNo` instead of always appending. New tests cover both. |
| `streamAskAnalyst` (frontend) had no handling for a stream that ends without ever sending a `final` event — the exact bug class already found and fixed in Feature 006's `research/api/ask.ts`, reintroduced here. | Added the same `sawFinalEvent` tracking + `onError` fallback Feature 006 already uses. |
| `attribution.py`'s document-claim verification was a from-scratch reimplementation instead of delegating to rag-v1's own `verify_citation_claims`/`extract_claims_and_citations` (orchestration-v1 step 4 explicitly requires delegation, not reimplementation); prompt-injection defense was a separate ad hoc regex rather than rag-v1's numbered inert-data-block framing. | The new online synthesis path imports and calls Feature 006's real `extract_claims_and_citations`/`verify_citation_claims` unchanged for `[Block N]`-tagged document claims, and frames RAG passages as the same kind of numbered inert data block rag-v1 uses (U-3). `attribution.py`'s `DocumentClaim` model was slimmed to match the internal contract exactly (`{chunkId, claimText}`), and `verify_attribution` now accepts already-verified document claims rather than re-verifying them itself. |

**Medium fixes:** `BackendToolClient`'s SCREENING branch was dropping `params` (and therefore `ownerId`) from its outbound call, the only tool branch doing so — fixed. Added `tests/e2e/analyst-ask.spec.ts` (Feature 7 had no E2E spec at all despite T038 asking for one).

**Explicitly not changed:** `priorTurns` is fully supported end-to-end (contract, both DTOs, orchestrator) but the frontend never populates it — left as-is since multi-turn conversation is out of MVP scope (SRS-JRN-01); this is inert plumbing, not a defect. The non-constant-time internal-API-key string comparison (`finvera-ai/app/core/auth.py`) is a pre-existing pattern shared with Feature 006, low risk in this private single-owner deployment, and out of this remediation's scope.

**Final verification:** `finvera-ai` 79/79 tests pass; `finvera-be` 571 tests run, 570 pass — the one failure (`PortfolioSchemaMigrationTests`, a Feature 005 test fixture referencing a `base_currency` column that does not exist on `market_instrument`) is pre-existing, confirmed unrelated via `git blame`/scope, and untouched by this session; `finvera-fe` 113/113 tests pass, lint clean, production build clean, and the critical routing fix was independently confirmed in a live browser session (screenshotted).
