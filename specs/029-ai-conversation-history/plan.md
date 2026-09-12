# Implementation Plan: AI Conversation History

**Feature Directory**: `specs/029-ai-conversation-history`

**Date**: 2026-09-12

**Spec**: [spec.md](spec.md)

**Status**: Planned

## Summary

Add durable, owner-scoped AI Analyst conversations without changing the
financial engines or making the AI service stateful. Spring Boot persists one
conversation and ordered question/answer exchange records in PostgreSQL,
constructs a deterministic bounded recent-history window, and passes only that
window through the existing internal `priorTurns` field. The React Analyst page
adds conversation navigation and renders stored exchanges. FastAPI validates
and labels historical context as untrusted context in planning and synthesis;
current facts still require existing tools and citations.

Full visible history and model context are deliberately separate:

```text
PostgreSQL: full transcript until owner delete
AI request: newest completed whole exchanges selected by context-window-v1
```

The new public API is additive. Existing `POST /api/v1/analyst/ask` remains a
stateless compatibility path and creates no conversation.

## Technical Context

**Affected Projects**: `finvera-be`, `finvera-fe`, `finvera-ai`

**Languages/Versions**: Java 21 with the committed Spring Boot 4.1.x parent;
TypeScript with React 19.2.8 and Vite 8.2.1; Python 3.13 with FastAPI/Pydantic
versions governed by `finvera-ai/pyproject.toml`

**Primary Dependencies**: Existing Spring Data JPA, Flyway, validation,
security, SSE, React/Vitest, FastAPI/Pydantic; no new dependency

**Storage/State**: PostgreSQL is authoritative; no Redis/Qdrant conversation
state and no historical backfill

**Interfaces**: Additive public REST/SSE conversation API; compatible semantic
tightening of the existing Spring-to-AI `priorTurns` field

**Testing/Evaluation**: Pure selector boundary/property tests, persistence and
migration integration tests, negative authorization tests, controller/contract
tests, frontend interaction/accessibility tests, AI prompt/evaluation tests,
and one end-to-end quickstart

**Target Environment**: Existing private browser -> Spring Boot -> internal
FastAPI deployment

**Performance Goals**: Conversation list/transcript visible within 1 second at
p95 for the declared local fixture; persistence adds at most 500 ms to first
stream event at p95

**Availability/Degradation**: History reads and management remain available
when FastAPI/LLM/tools are unavailable. An accepted failed ask reaches an honest
terminal state. No queue or broker is added.

**Scale/Scope**: Current private owner, designed and tested for 10,000
conversations and 10,000 exchanges per conversation with bounded pages

**Open Technical Unknowns**: None. Decisions R-001 through R-010 in
`research.md` resolve storage, context, privacy, streaming, compatibility, and
rollout.

## Constitution Check

- [x] **Deterministic core**: No financial formula changes. Context selection,
  titles, ordering, and state transitions are deterministic and versioned.
- [x] **Evidence and time**: Stored public snapshots retain original claim and
  citation metadata and financial as-of times; historical prose is not current
  evidence.
- [x] **Boundaries and ownership**: Browser -> Spring -> AI is preserved;
  PostgreSQL/Spring own conversation truth and FastAPI remains stateless.
- [x] **Security and privacy**: Every object read/mutation is owner scoped;
  only bounded context leaves Spring; hard delete and log exclusions are
  defined.
- [x] **Traceability**: SRS-CONV-01 maps through feature requirements, public
  and context contracts, test strategy, and task groups. No clarification marker
  remains.
- [x] **Risk-based tests**: Migration, state/concurrency, idempotency,
  authorization, SSE, prompt injection, stale facts, and accessibility have
  explicit tests.
- [x] **Resilience and observability**: Read paths do not need AI; accepted
  attempts have terminal/reconciliation behavior and privacy-safe metrics.
- [x] **Modular simplicity**: Existing modules, database, internal request, and
  SSE pipeline are reused; no service, broker, cache, vector collection, or
  dependency is introduced.

**Pre-research result**: PASS. The feature is additive and the research list
targets the privacy, temporal-truth, lifecycle, and context risks.

**Post-design result**: PASS. The data model and contracts resolve every gate;
no complexity exception or ADR is needed.

## System Context and Boundaries

### Affected User and System Flows

```text
Owner browser
  -> Spring conversation API
       -> authenticate + owner scope
       -> create/load conversation and PROCESSING exchange
       -> select context-window-v1 from completed exchanges
       -> persist accepted state
       -> send accepted SSE event
       -> existing Analyst orchestration pipeline
            -> internal FastAPI request with current question + bounded priorTurns
            -> existing allowlisted Spring tools / RAG
            -> verified public final result
       -> persist COMPLETED/FAILED/CANCELLED exchange
       -> send final/error SSE event

Owner browser
  -> Spring list/read/rename/delete APIs
       -> PostgreSQL only; no AI dependency
```

### Ownership

| Concern | Owning project/module | Reason |
|---|---|---|
| Conversation and exchange lifecycle | `finvera-be/.../analyst` | Spring owns authentication, transactions, public API, and private business state. |
| Conversation storage and audit link | PostgreSQL via `finvera-be` | Transactional source of truth and cascade deletion. |
| Context selection | `finvera-be/.../analyst/domain` | Pure, versioned data-minimization rule over owned records. |
| Tool planning and answer synthesis with history | `finvera-ai/app/features/chat` | AI prompt/orchestration concern; receives no full transcript. |
| Conversation navigation and display | `finvera-fe/src/features/analyst` | Browser presentation only; never constructs authoritative history. |
| Financial/document truth | Existing Features 001-006, 008-028 | Conversation snapshots do not replace authoritative sources. |

### Interface Changes

| Interface | Change | Version/Compatibility | Contract Artifact |
|---|---|---|---|
| Public `/api/v1/analyst/conversations/**` | Add list, conversation ask stream, exchange page, rename, and delete | Additive `conversation-history-v1`; session/CSRF rules unchanged | [public-api.openapi.yaml](contracts/public-api.openapi.yaml) |
| Public `/api/v1/analyst/ask` | No behavior change; remains standalone and stateless | Fully compatible during rollout | Feature 007 public contract |
| Internal `/internal/v1/orchestrate/ask` | `priorTurns` becomes server-selected rather than browser-authoritative; shape remains bounded list | Compatible v1 shape; producer and validation semantics tightened | [context-window-v1.md](contracts/context-window-v1.md) and Feature 007 internal OpenAPI |
| SSE event stream | New conversation stream starts with `accepted`, then reuses tool/delta/final shapes and may end with sanitized `error` | New endpoint, so no old-client parsing break | [public-api.openapi.yaml](contracts/public-api.openapi.yaml) |

## Phase 0: Research

Research is complete in [research.md](research.md):

- R-001 places authoritative history in Spring/PostgreSQL.
- R-002 fixes the bounded sliding-window algorithm and rejects rolling summary.
- R-003 defines historical prose as untrusted context rather than evidence.
- R-004 defines the sanitized public answer snapshot.
- R-005 chooses one exchange row per question/answer lifecycle.
- R-006 defines owner-scoped idempotency and one active generation.
- R-007 defines create-on-first-question, deterministic title, and keyset pages.
- R-008 defines retain-until-delete and transactional cascade.
- R-009 defines stream/disconnect terminal behavior and stale reconciliation.
- R-010 preserves the standalone endpoint and requires no new infrastructure.

No provider API or new dependency is introduced. The only model-facing unknown
was context sizing; a provider-independent deterministic character budget was
chosen so correctness does not rely on guessing a tokenizer API.

## Phase 1: Design and Contracts

### Data Model

[data-model.md](data-model.md) defines:

- `analyst_conversation` as the owner-scoped thread and keyset-list anchor;
- `analyst_conversation_exchange` as the ordered question/assistant lifecycle;
- a nullable conversation-exchange link on existing `analyst_query` so hard
  deletion also removes conversation-specific audit previews/tool calls;
- state constraints, idempotency, one-processing-exchange enforcement, times,
  sanitized JSON snapshot, migration, and non-destructive rollback.

Migration `V021__create_analyst_conversation_history.sql` follows the existing
V020 migration. There is no backfill because existing audit rows cannot
reconstruct full answers or citations.

### Contracts

- [public-api.openapi.yaml](contracts/public-api.openapi.yaml) is the additive
  browser-to-Spring contract.
- [context-window-v1.md](contracts/context-window-v1.md) is the versioned
  selector/data-minimization contract for the existing Spring-to-AI request.
- Feature 007's public final result remains the canonical answer shape. The
  conversation API stores and returns that sanitized shape rather than inventing
  a second claim/citation vocabulary.

### Security, Privacy, and AI Safety

- Use `OwnerScopedAccess` at every controller entry and owner-qualified service
  queries. Return the same `404` for absent/non-owned IDs.
- Apply CSRF to conversation ask, rename, and delete. Reads require the existing
  authenticated session.
- Do not accept browser-supplied `priorTurns` on the new endpoint. Derive context
  only from completed owned exchanges.
- Enforce `(owner_id, client_request_id)` idempotency and a single processing
  exchange per thread in both schema and service behavior.
- Store only the sanitized public final result. Never store/log raw provider
  prompts, raw responses, raw tool payloads, full questions/answers in telemetry,
  or citation text beyond the private database row returned to the owner.
- Label history in every AI prompt as untrusted context. Existing allowlist,
  typed args, attribution, injection defense, and refusal policies remain in
  force.
- Hard delete conversation-linked content and audit rows. Document that external
  database backups have their own operator retention.

### Observability and Operations

Add privacy-safe counters/timers only:

- `analyst.conversation.created`
- `analyst.conversation.ask.completed{outcome}`
- `analyst.conversation.context.included`
- `analyst.conversation.context.omitted`
- `analyst.conversation.request.duplicate{state}`
- `analyst.conversation.read.duration`
- `analyst.conversation.deleted`
- `analyst.conversation.stale_processing.reconciled`

Structured logs may include correlation ID, owner-safe internal identifier,
conversation/exchange UUID, rule version, counts, state, duration, and reason
code. They must not include question, title, answer, evidence/citation content,
raw exception bodies from providers, or client request payloads.

History list/read/delete remains healthy when FastAPI or the LLM is down. Add no
new health component because there is no new external dependency. Operational
documentation adds migration, stale-processing, deletion, and metrics checks.

### Test and Evaluation Strategy

| Requirement IDs | Test level | Fixture/dataset | Expected evidence |
|---|---|---|---|
| FR-001-FR-003, FR-009, NFR-002 | Backend service/controller + frontend integration | First ask, follow-up, final, failure, disconnect fixtures | Accepted event precedes delta; durable ordered terminal exchange; timing budget measured. |
| FR-004, FR-005, NFR-001, NFR-003 | Repository/service performance integration | 10,000 conversations and 10,000 exchanges with tied timestamps | Stable keyset pages, no duplicates/gaps, bounded query count, p95 target. |
| FR-006, DATA-002, DATA-003 | Contract/persistence/frontend | Full/partial/refused results with structured/document claims | Reopened final result equals original sanitized public response; as-of values unchanged. |
| FR-007 | Pure unit + UI | whitespace, Unicode, 79/80/81/120/121-char titles | Deterministic title and validation boundaries. |
| FR-008, DATA-001, DATA-005 | Migration/persistence integration | Conversation plus linked audit/tool rows and unrelated legacy audit | Complete cascade; unrelated rows retained; processing delete blocked. |
| FR-010 | Transaction/concurrency integration | duplicate completed, duplicate processing, concurrent distinct IDs | One exchange and one AI call; stable 409/replay behavior. |
| FR-011 | Existing regression suite | Feature 007 standalone fixtures | No saved thread; old SSE contract remains green. |
| SEC-001, SEC-002 | Negative authorization/controller/repository tests | Two owners and unknown UUID | All object operations return non-disclosing denial. |
| SEC-003, SEC-004, NFR-005 | Log/metric integration inspection | unique secret-like prompt/citation markers | Markers absent; counts/reason/correlation present. |
| AI-001, AI-002, AI-005 | Pure property/boundary + internal contract | 0/1/5/6/10/11, exact/overflow, failed/oversized pairs | Exact context-window-v1 selection; no summary or truncated turn. |
| AI-003, AI-004 | Versioned AI evaluation | `conversation_context_v1.json` with stale facts and injection attempts | Current-fact tools invoked; history cannot override policy or become claim evidence. |
| NFR-004 | Frontend component/accessibility | empty/loading/processing/completed/failed/delete states | Keyboard operation, focus management, labels and non-colour states pass. |

AI evaluation is deterministic at the orchestration boundary: use a versioned
fixture dataset and fake adapter/tool responses. Live-model wording is not a
release gate; tool selection, prompt labeling, citation/claim validation,
refusal, and stale-fact behavior are.

### Rollout, Migration, and Rollback

1. Apply V021; it adds tables, indexes, and a nullable audit link without
   modifying existing rows.
2. Deploy backend conversation APIs and keep `/analyst/ask` unchanged.
3. Deploy FastAPI context validation/prompt changes; requests with zero history
   remain compatible.
4. Deploy frontend conversation UI and switch its sends to the new endpoint.
5. Verify quickstart, owner isolation, final replay, metrics, and deletion.

Rollback frontend first to restore the standalone UI, then backend/AI code if
needed. Leave additive V021 schema in place; no destructive down-migration or
conversation-data deletion occurs during rollback. No historical backfill or
re-indexing is required.

Rollout must stop if owner isolation fails, final snapshots cannot be reopened,
duplicate requests execute AI twice, current-fact follow-ups reuse historical
prose as evidence, or standalone Analyst regresses.

### Quickstart Acceptance

[quickstart.md](quickstart.md) defines prerequisites, focused automated checks,
the P1 browser/API journey, duplicate/authorization/deletion scenarios, context
boundary verification, degraded AI behavior, and full quality gates.

## Project Structure

### Feature Documentation

```text
specs/029-ai-conversation-history/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── context-window-v1.md
│   └── public-api.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code Affected

```text
finvera-be/src/main/resources/db/migration/
└── V021__create_analyst_conversation_history.sql

finvera-be/src/main/java/com/minhnb/finvera_be/analyst/
├── config/AnalystProperties.java
├── controller/AnalystConversationController.java
├── domain/ConversationContextWindowPolicy.java
├── domain/ConversationExchangeStatus.java
├── domain/ConversationTitlePolicy.java
├── dto/AnalystConversationDtos.java
├── entity/AnalystConversationEntity.java
├── entity/AnalystConversationExchangeEntity.java
├── repository/AnalystConversationRepository.java
├── repository/AnalystConversationExchangeRepository.java
├── service/AnalystConversationService.java
├── service/AnalystQueryService.java
└── service/AnalystService.java

finvera-be/src/test/java/com/minhnb/finvera_be/analyst/
├── AnalystConversationMigrationTests.java
├── AnalystConversationControllerTests.java
├── AnalystConversationServiceTests.java
├── ConversationContextWindowPolicyTests.java
├── ConversationTitlePolicyTests.java
├── AnalystConversationAuthorizationTests.java
├── AnalystConversationPaginationTests.java
└── AnalystConversationPerformanceTests.java

finvera-ai/app/features/chat/
├── service.py
└── tests/
    ├── test_conversation_context.py
    └── fixtures/conversation_context_v1.json

finvera-fe/src/features/analyst/
├── api/analyst.ts
├── components/AskAnalyst.tsx
├── components/ConversationSidebar.tsx
├── components/ConversationTranscript.tsx
└── conversation-history.test.tsx

docs/runbooks/
└── ai-conversation-history.md
```

**Structure Decision**: All transactional behavior stays in the existing
backend analyst module and follows ADR-0007 layers. The AI service receives a
bounded value object and adds no state. The frontend remains one feature module.
The public final result is reused across live and reopened views.

## Traceability Summary

| Requirement IDs | Design/Contract | Test/Evaluation | Planned Task Group |
|---|---|---|---|
| FR-001-FR-003, FR-009-FR-011 | R-005, R-006, R-009, R-010; public API | backend lifecycle/SSE, frontend, compatibility | Foundation, US1 |
| FR-004-FR-005 | R-007; public API/data model | keyset repository/service/UI tests | US2 |
| FR-006, DATA-002-DATA-004 | R-003, R-004; stored snapshot schema | exact replay and fresh-tool evaluation | US1, US2 |
| FR-007-FR-008, DATA-001, DATA-005 | R-007, R-008; public API/data model | title, cascade, busy-delete tests | US3 |
| SEC-001-SEC-004 | R-001, R-003, R-008 | negative authorization and log inspection | Foundation, all stories |
| AI-001-AI-005 | R-002, R-003; context-window-v1 | property/boundary and versioned AI evaluation | Foundation, US1 |
| NFR-001-NFR-005 | pagination, metrics, UI, runbook | performance, accessibility, degradation | US1-US3, final |

## Complexity Tracking

No constitution violation or material complexity addition is required. The
feature adds two tables and additive endpoints inside the existing analyst
module and reuses current infrastructure.
