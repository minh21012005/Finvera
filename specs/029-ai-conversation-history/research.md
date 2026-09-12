# Research: AI Conversation History

**Feature**: `029-ai-conversation-history`

**Date**: 2026-09-12

## R-001 — Transactional owner of conversation history

**Decision**: Spring Boot's `analyst` module owns conversations and their
complete user-visible exchange history in PostgreSQL. The browser accesses it
only through owner-authenticated public APIs. `finvera-ai` remains stateless
with respect to conversation truth and receives only a bounded transient
context for one request.

**Rationale**: Conversation history is private user data and must use the same
authentication, ownership, transaction, deletion, and public-contract boundary
as portfolios and research documents. PostgreSQL is already the transactional
source of truth. This preserves Architecture B-1, B-2, B-3, and Constitution
Principles II-IV.

**Alternatives considered**:

- Store history in `finvera-ai`: rejected because it creates a second owner and
  authorization boundary and makes the AI service a source of business truth.
- Store history in the browser: rejected because refresh/device changes lose
  history and the client can forge prior turns.
- Store history in Redis or Qdrant: rejected because both are derived or
  ephemeral stores and cannot own private conversation records.

**Risks/validation**: Negative integration tests must cover every object-level
operation. No browser request may supply authoritative prior turns.

## R-002 — Bounded sliding context window

**Decision**: Persist the full transcript, but construct `context-window-v1`
from completed exchanges only. Starting with the newest exchange and moving
backward, include whole exchanges while all three default limits hold:

1. at most 10 recent completed exchanges are considered;
2. at most 5 exchanges are included;
3. included question plus answer text totals at most 12,000 Unicode characters.

Stop at the first exchange that would exceed the size budget so the selected
window is a contiguous recent suffix. Reverse the selected exchanges back into
chronological order before sending them to `finvera-ai`. Always send the current
question separately. Never split, truncate, summarize, or rewrite a stored
historical exchange. If the newest exchange does not fit, send an empty history.

**Rationale**: This is a deterministic sliding window that is easy to reproduce
and bound without a provider-specific tokenizer or an extra network call. It
also matches the existing internal `priorTurns` ceiling of ten and the current
prompt's preference for five recent exchanges.

**Alternatives considered**:

- Send the entire transcript: rejected due to unbounded latency, cost, privacy
  exposure, and eventual model-context overflow.
- Fixed five exchanges with no size budget: rejected because answer lengths vary
  and a small exchange count can still be very large.
- Provider token-count endpoint: rejected because it adds latency and makes a
  local persistence feature depend on another provider call.
- Rolling AI summary: rejected for v1 because summary drift can turn an old
  interpretation into false memory and another source of unsupported facts.
- Embedding retrieval over old chats: rejected as unnecessary infrastructure
  for the initial recency-based follow-up journey.

**Risks/validation**: Boundary/property tests cover 0/1/5/6/10/11 exchanges,
exactly 12,000 characters, one-character overflow, failed exchanges, and an
oversized newest exchange. They also prove no older exchange is selected after
a newer candidate fails the budget. The rule version and included/omitted
counts are observable in accepted stream metadata without exposing content in
logs.

## R-003 — Historical prose is context, not evidence

**Decision**: Previous questions and answers are labeled as untrusted historical
conversation in every model-facing prompt. They may resolve conversational
references, but they cannot authorize tools, alter policies, or support current
financial/document claims. The existing tool, attribution, citation, and
refusal rules continue to govern the new answer. A current-fact follow-up must
call the appropriate tool again.

**Rationale**: Prices, signals, ratios, portfolio values, news, and documents can
change. Reusing an old assistant sentence as truth would violate temporal truth
and turn an LLM transcript into a system of record.

**Alternatives considered**:

- Treat verified old answers as reusable evidence: rejected because verification
  was tied to the old as-of time and source state.
- Strip all numbers from context: rejected because it harms natural follow-ups
  without addressing non-numeric stale claims.

**Risks/validation**: Versioned AI evaluations include stale numeric history,
prompt-injection text in both roles, referential follow-ups, and tool outages.

## R-004 — Store a sanitized public answer snapshot

**Decision**: Each completed exchange stores answer text plus a versioned JSON
snapshot of the public final-event metadata: structured claims, document claims,
tool-call status summary, refusal/bound flags, rule version, synthesis/planner
modes, and claim coverage. Raw model prompts, raw model responses, raw provider
payloads, and full tool results are not stored as conversation history.

**Rationale**: Reopening must reproduce what the user saw, including citations
and limitations. Storing the already-sanitized public result achieves that
without duplicating authoritative market data or retaining provider internals.
Keeping answer text as a first-class field supports bounded context construction
without parsing JSON.

**Alternatives considered**:

- Store answer text only: rejected because citations and coverage would be lost.
- Store raw tool/provider payloads: rejected due to data minimization, retention,
  schema coupling, and duplication of authoritative records.
- Fully normalize every claim into conversation-owned tables: rejected as more
  joins and migrations for data that is displayed as an immutable response
  snapshot and already has a versioned public schema.

**Risks/validation**: DTO validation must reject an invalid snapshot before
marking an exchange completed. Reopened output is compared field-for-field with
the original public final event.

## R-005 — Exchange-based persistence model

**Decision**: Persist one `analyst_conversation_exchange` per accepted question,
containing the user question and one assistant attempt. Status is `PROCESSING`,
`COMPLETED`, `FAILED`, or `CANCELLED`. The UI projects each exchange as a user
message followed by an assistant state/message.

**Rationale**: The existing AI contract and context are strict question/answer
pairs. An exchange row makes ordering, idempotency, one-active-generation,
context filtering, and deletion simpler than coordinating two message rows plus
a generation entity. Editing, branching, multiple assistant candidates, and
system messages are explicitly out of scope.

**Alternatives considered**:

- Separate row per message: valid for a general chat platform, but adds lifecycle
  complexity that the approved scope does not use.
- Persist only completed answers: rejected because a disconnect would lose the
  accepted user question and provide no honest failure state.

**Risks/validation**: The public contract exposes user/assistant message-shaped
fields even though storage is paired. A future branching feature would require a
new data-model decision rather than silently overloading this table.

## R-006 — Idempotency and conversation serialization

**Decision**: Every streamed conversation request includes a browser-generated
UUID `clientRequestId`. A unique `(owner_id, client_request_id)` constraint
identifies retries even when the first request creates the conversation. A
partial unique index permits at most one `PROCESSING` exchange per conversation.

On a duplicate completed request with the same normalized question, symbol, and
new/existing-conversation intent, return the stored accepted metadata and final
event without invoking AI again. On a duplicate processing request or another
new question while processing, return a retryable `409` problem response. Reuse
of the identifier with any different payload is a non-retryable
`IDEMPOTENCY_KEY_REUSED` conflict and never reveals/replays the stored answer.

**Rationale**: SSE/network retries otherwise duplicate private messages and
expensive model/tool work. Serial order is more understandable than interleaved
answers and avoids ambiguous context.

**Alternatives considered**:

- Server-generated request ID only: rejected because the browser cannot safely
  retry a request whose response was lost.
- Allow parallel questions in one thread: rejected because completion order and
  the context snapshot become ambiguous.
- Global request UUID uniqueness with no owner component: rejected because a
  collision could disclose another owner's request existence.

**Risks/validation**: Concurrency integration tests use separate transactions
and verify one exchange and at most one AI invocation. Mismatched-payload tests
verify no stored content is returned.

## R-007 — Create-on-first-question, deterministic title, and keyset pagination

**Decision**: The conversation-aware stream endpoint accepts an optional
conversation ID. If absent, it creates the conversation and first exchange in
one transaction. No empty conversation API is added. The automatic title is
normalized first-question text, cut at a word boundary to 80 characters, with a
120-character owner-edit limit.

Conversation pagination uses `(last_activity_at, id)` descending. Exchange
pagination uses `(sequence_no, id)` and returns chronological items plus an
opaque older-page cursor. Defaults are 20/max 50 conversations and 50/max 100
exchanges.

**Rationale**: This avoids abandoned empty rows, avoids a title-generation model
call, and prevents offset drift when a conversation receives a new message.

**Alternatives considered**:

- Create an empty thread when “New chat” is clicked: rejected because navigation
  creates junk records.
- AI-generated titles: rejected due to extra latency/cost and possible leakage.
- Offset pagination: rejected because ordering changes whenever a conversation
  becomes active.

**Risks/validation**: Cursor decoding is validated and errors do not reveal
object existence. Same-timestamp fixtures prove stable ordering.

## R-008 — Retention and deletion

**Decision**: Conversation content has no automatic expiration in v1 and remains
until explicit owner deletion. Delete is a hard transactional cascade over the
conversation, exchanges, stored answer snapshots, and linked new conversation
query/tool-call audit rows. Legacy audit-only rows are not backfilled or linked.
Deletion is rejected while an exchange is processing.

**Rationale**: “History” must persist predictably, while an explicit delete must
actually remove private content. Existing audit previews contain question text,
so retaining linked rows would violate the user-visible deletion expectation.

**Alternatives considered**:

- Soft delete: rejected because it retains private content with no approved
  restore or compliance use case.
- Automatic 30/90-day expiration: rejected because the user requested durable
  history and no retention period was selected.
- Keep audit rows after deletion: rejected for conversation-linked requests;
  aggregate privacy-safe metrics can remain without those rows.

**Risks/validation**: Migration and service integration tests verify cascade
direction and that unrelated legacy audit rows remain unchanged.

## R-009 — Stream interruption and terminal states

**Decision**: Persist the question and `PROCESSING` exchange before the first SSE
event. A normal final event atomically stores the public result and marks
`COMPLETED`. Provider/tool terminal failure marks `FAILED`; explicit client stop
marks `CANCELLED` when cancellation reaches the server. A transport disconnect
must not turn partial delta text into a completed answer. v1 does not add a
durable background job; the server attempts to finish the in-flight call and
persist its terminal result within the existing timeout.

**Rationale**: This preserves truthful state without Kafka, a queue, or another
service. Raw deltas are transient display data and are not a verified answer.

**Alternatives considered**:

- Store every delta: rejected due to write amplification and partial-text
  ambiguity.
- Continue through a durable background worker: rejected as unnecessary
  infrastructure for the current private single-owner scale.
- Delete failed attempts: rejected because it hides what happened.

**Risks/validation**: Tests cover final, failure, timeout, explicit abort, and
emitter I/O failure. Any process crash can leave `PROCESSING`; startup/read-side
reconciliation marks exchanges older than the ask timeout plus grace as failed.

## R-010 — Compatibility and rollout

**Decision**: Add conversation APIs without changing the behavior of
`POST /api/v1/analyst/ask`. The frontend switches to the new conversation stream
after the backend and migration are available. The internal Spring-to-AI v1
request keeps the `priorTurns` field but changes its producer from browser input
to the server-owned selector. No new library, datastore, service, Kafka topic,
or ADR is required.

**Rationale**: Additive rollout avoids breaking current clients and permits a
simple rollback to the standalone screen. The architectural boundaries do not
change.

**Alternatives considered**:

- Mutate `/analyst/ask` to create conversations: rejected because old clients
  would create unexpected saved data.
- Replace the existing internal contract version: rejected because its shape and
  bounded semantics already support the required data.

**Risks/validation**: Contract and regression tests prove both public flows.
There is no historical backfill because audit rows cannot reconstruct answers.
