# Feature Specification: AI Conversation History

**Feature Directory**: `029-ai-conversation-history`

**Created**: 2026-09-12

**Status**: Specified

**SRS References**: Section 4.1 (protected user resources), Section 23.2 (AI
Conversation History), Section 30 (AI Analyst), Section 36 (non-functional
requirements), SRS-CONV-01, SRS-AIA-01, SRS-NFR-03, SRS-NFR-04,
SRS-NFR-05, SRS-NFR-06, SRS-NFR-08

**Input**: User wants AI Analyst conversations to be stored, reopened, and
continued over time, with a bounded sliding context window so long histories do
not make every request slower, more expensive, or less reliable.

## Scope Summary *(mandatory)*

AI Analyst currently answers one question at a time. Although its request shape
can carry a small number of earlier turns, the browser does not build that
history and the system does not provide conversations the owner can reopen.
Closing or refreshing the page therefore loses the useful research trail and a
follow-up question cannot reliably refer to what was discussed before.

This feature gives the authenticated owner durable, private conversation
threads. The owner can ask a first question, continue with follow-ups, browse
older threads, reopen the complete visible transcript, rename a thread, and
delete it. Stored answers retain the public evidence and timestamps that were
shown when the answer was produced so the owner can review what the system said
and what supported it at that time.

The complete transcript is retained for display, but only a bounded recent
window is supplied as conversational context for a new question. Historical
answers are context, never current market evidence: when a follow-up requires a
current price, ratio, signal, portfolio value, news item, or document fact, AI
Analyst must use its existing tools and citations again rather than trusting a
number copied from an earlier answer.

### In Scope

- Start a conversation by asking the first question; an abandoned empty thread
  is not created.
- Continue a conversation with follow-up questions that can refer to recent
  completed turns.
- List the owner's conversations newest-active first and reopen the complete
  stored transcript with pagination.
- Generate an initial title deterministically from the first question and let
  the owner rename it.
- Delete a conversation and its private message, evidence, and linked
  conversation-specific audit content.
- Preserve each completed assistant answer, its structured evidence, document
  citations, coverage/limitation state, and generation timestamp as the
  historical record shown to the owner.
- Show failed or interrupted attempts honestly and allow a later new attempt
  without duplicating the prior successful answer.
- Build a bounded sliding context from the most recent completed exchanges,
  without AI-generated long-term summaries.
- Keep the existing standalone Analyst endpoint compatible during migration to
  the conversation experience.

### Out of Scope

- Searching across conversation text.
- Sharing, collaboration, public links, or access by another owner.
- Editing a sent user message, branching from an older message, or regenerating
  an answer in place.
- Importing or reconstructing full conversations from existing audit rows,
  which do not contain complete answers and citations.
- AI-generated long-term memory, rolling conversation summaries, user profiling,
  or personalized analytics.
- Export to PDF, document, or external chat service.
- Replacing current tool calls with values remembered from conversation history.
- Changes to the deterministic financial calculations or tool allowlist.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Continue a Durable Conversation (Priority: P1)

As the owner, I want my first question and later follow-ups saved in one thread
so that I can conduct a multi-step analysis without re-explaining the recent
discussion or losing it after a refresh.

**Why this priority**: This is the smallest slice that changes AI Analyst from a
one-shot form into a useful conversation while preserving the existing grounded
answer behavior.

**Independent Test**: Ask an initial question, ask at least six follow-ups,
refresh the page, reopen the thread, and verify that the full visible transcript
remains while a new follow-up receives only the bounded recent completed context
and still calls tools for current facts.

**Acceptance Scenarios**:

1. **Given** no active conversation, **When** the owner submits a valid first
   question, **Then** one conversation and one ordered user/assistant exchange
   are created and the streamed answer behaves like the existing Analyst answer.
2. **Given** a conversation with completed exchanges, **When** the owner asks a
   referential follow-up such as “so với mã vừa nói thì sao?”, **Then** the
   bounded recent context is supplied in chronological order and the answer is
   stored as the next exchange.
3. **Given** a long conversation, **When** another question is sent, **Then** the
   full transcript remains visible but only the most recent whole exchanges that
   fit the disclosed window limits are used as context.
4. **Given** an earlier answer contains a market value, **When** the owner asks
   for its current value, **Then** the system obtains fresh evidence through the
   appropriate tool and does not cite the earlier prose as the source.
5. **Given** the AI request fails or the stream is interrupted, **When** the
   transcript is reopened, **Then** the attempt is visibly marked incomplete or
   failed and no partial text is presented as a verified completed answer.

---

### User Story 2 - Browse and Reopen Past Research (Priority: P2)

As the owner, I want to browse previous conversations and reopen their messages
and evidence so that I can revisit earlier research without asking the same
questions again.

**Why this priority**: Persistence has limited value unless the owner can find
and inspect the saved record after leaving the page.

**Independent Test**: Create conversations at different times, load the list in
multiple pages, reopen an older item, and verify ordering, title, timestamps,
messages, evidence, and unavailable states.

**Acceptance Scenarios**:

1. **Given** multiple conversations, **When** the owner opens AI Analyst,
   **Then** conversations appear by most recent activity with stable pagination.
2. **Given** a selected conversation with more messages than one page, **When**
   the owner requests older messages, **Then** earlier messages are prepended
   without duplicates, gaps, or changed order.
3. **Given** a saved completed answer, **When** it is reopened, **Then** its
   answer, citations, structured evidence, coverage state, and original
   timestamps match the stored historical response.

---

### User Story 3 - Organize and Delete Conversations (Priority: P3)

As the owner, I want to rename and delete conversations so that the history
stays understandable and private.

**Why this priority**: Automatic titles make the feature usable without setup;
manual organization and deletion complete the ownership lifecycle.

**Independent Test**: Verify the deterministic initial title, rename it at its
length boundaries, delete the thread, and confirm that neither its transcript
nor linked private audit content can be retrieved afterward.

**Acceptance Scenarios**:

1. **Given** a new conversation, **When** its first question is accepted,
   **Then** a readable title is derived from that question without another AI
   call and may later be changed by the owner.
2. **Given** an owned conversation, **When** the owner renames it with a valid
   title, **Then** the new title appears in the list and transcript view.
3. **Given** an owned conversation, **When** the owner confirms deletion,
   **Then** the conversation, messages, evidence, and linked private audit
   content are no longer retrievable.

### Edge and Failure Cases *(mandatory)*

- Blank questions and titles, questions above the existing accepted length, and
  titles outside their limits are rejected without creating or altering data.
- A missing, malformed, deleted, or another owner's conversation identifier
  returns a non-disclosing not-found response.
- Repeating the same client request after a network retry does not create a
  duplicate message or run the AI operation twice.
- Reusing a client request identifier with a different question, symbol, or
  target conversation is rejected and never replays an unrelated answer.
- Only one generation may be active in a conversation; a concurrent second
  submission receives a retryable busy state rather than interleaved messages.
- Failed, cancelled, or partial assistant attempts are excluded from future
  context and never displayed as a completed verified answer.
- If the AI service or a tool is unavailable, the existing truthful degradation
  behavior is stored and shown; market, portfolio, and authentication features
  remain available.
- A new question that exceeds the context budget is still processed on its own;
  older pairs are omitted without truncating or rewriting the visible history.
- Conversation deletion during an active generation is rejected until the
  attempt reaches a terminal state.
- Pagination remains stable when another conversation receives a new message.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST create a durable conversation only when the owner
  submits its first valid question and MUST return its identity before answer
  content is streamed.
- **FR-002**: The system MUST append every accepted user question and its
  corresponding assistant attempt in an unambiguous chronological order.
- **FR-003**: The system MUST allow the owner to continue an existing
  conversation without resending conversation history from the browser.
- **FR-004**: The system MUST list conversations by most recent activity using
  stable bounded pagination.
- **FR-005**: The system MUST return an owned conversation's messages in stable
  chronological order using bounded pagination.
- **FR-006**: The system MUST preserve and redisplay the complete public result
  of each completed answer, including evidence, citations, coverage, limitations,
  and original timestamps.
- **FR-007**: The system MUST derive an initial title from normalized first-
  question text without an additional AI call and MUST allow the owner to rename
  it to a non-blank title of at most 120 characters.
- **FR-008**: The system MUST allow the owner to delete a conversation after an
  explicit confirmation and make it unavailable to all subsequent reads.
- **FR-009**: The system MUST represent assistant attempts with processing,
  completed, failed, or cancelled states and MUST exclude non-completed attempts
  from future conversational context.
- **FR-010**: The system MUST use an owner-scoped client request identifier to
  make repeated submissions idempotent and MUST prevent concurrent generations
  from interleaving within one conversation. Reuse with a different request
  payload MUST be rejected as a non-retryable conflict.
- **FR-011**: The existing standalone Analyst question flow MUST remain usable
  during the frontend migration and MUST NOT silently create saved conversations.

### Data and Financial Semantics

- **DATA-001**: Complete conversation content MUST remain stored until explicit
  owner deletion; no automatic expiration is applied in this feature.
- **DATA-002**: Each message MUST preserve its creation/completion times and each
  completed answer MUST preserve the public evidence references and financial
  as-of times returned with that answer.
- **DATA-003**: A stored answer is a historical transcript, not a source of
  current financial truth; current or changed facts MUST be obtained again from
  the existing authoritative tools.
- **DATA-004**: Existing audit-only query records MUST NOT be fabricated into
  conversations or backfilled as full messages.
- **DATA-005**: Deleting a conversation MUST remove its messages, stored public
  evidence payloads, and linked conversation-specific query/tool-call audit
  content as one owner-visible operation.

### Security and Privacy

- **SEC-001**: Every create, read, continue, rename, and delete operation MUST
  require authentication and enforce conversation ownership on the server.
- **SEC-002**: Access to a missing and a non-owned conversation MUST be
  indistinguishable to the caller, and negative cross-owner tests MUST cover all
  conversation and message operations.
- **SEC-003**: Logs, metrics, traces, errors, and audit previews MUST NOT contain
  full questions, answers, citations, or stored private transcript content.
- **SEC-004**: Only the bounded context needed for the current AI request MAY be
  sent outside the transactional service; the full stored transcript MUST NOT be
  sent by default.

### AI and Retrieval Behavior

- **AI-001**: Context selection MUST use a versioned bounded sliding-window rule
  that chooses the newest whole completed user/assistant exchanges, preserves
  chronological order, applies both a pair-count ceiling and a total-size budget,
  and never rewrites the stored transcript.
- **AI-002**: The first version MUST NOT create or use an AI-generated rolling
  summary or long-term user memory.
- **AI-003**: Historical user and assistant text MUST be treated as untrusted
  conversational context, never as instructions that can change system policy,
  authorize tools, or replace evidence.
- **AI-004**: Questions requiring current or document-backed facts MUST retain
  the existing allowlisted-tool, attribution, citation, and refusal behavior
  regardless of what older messages claim.
- **AI-005**: The current question MUST always be retained; if no historical
  exchange fits the configured budget, the request MUST proceed without prior
  turns and disclose no false continuity.

### Non-Functional Requirements

- **NFR-001**: For the planned single-owner scale, 95% of conversation-list and
  first-page transcript reads MUST become visible within 1 second under normal
  local operating conditions.
- **NFR-002**: Saving the accepted user message and processing state MUST add no
  more than 500 milliseconds before the first stream event in 95% of normal
  requests.
- **NFR-003**: Lists MUST support at least 10,000 conversations and each
  conversation at least 10,000 messages without loading an unbounded collection.
- **NFR-004**: All controls and message states MUST be keyboard operable, visibly
  labeled without relying on color, and rendered in the existing Vietnamese
  locale conventions.
- **NFR-005**: Operators MUST be able to distinguish persistence, ownership,
  context-selection, AI dependency, cancellation, and duplicate-request failures
  using privacy-safe signals and correlation identifiers.

### Key Entities

- **Conversation**: An owner-scoped ordered research thread with a generated or
  owner-edited title, creation time, latest activity time, and lifecycle.
- **Message**: One ordered user question or assistant attempt inside a
  conversation, with role, lifecycle state, visible content, and timestamps.
- **Stored Answer Evidence**: The public structured claims, document citations,
  tool status summary, coverage, rule version, and as-of metadata associated
  with a completed assistant message; it is a historical display record rather
  than a new source of financial truth.
- **Context Window**: A transient, versioned selection of recent complete
  exchanges used only for one new AI request.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- The current private authenticated-owner model remains unchanged, while all
  object ownership rules are still implemented and tested as if multiple owners
  can exist.
- Conversation content is retained until explicit deletion; automatic retention
  can be introduced only by a later product decision with visible policy.
- The initial title is the normalized first question shortened at a word boundary
  to at most 80 characters; manual titles allow 120 characters.
- The first context rule considers at most the ten latest completed exchanges,
  includes at most five, and applies a 12,000-character aggregate budget. Exact
  limits remain configurable but their defaults and rule version are testable.
- Whole historical exchanges are either included or omitted; visible stored
  messages are never shortened to fit AI context.
- A failed stream keeps the user message and a non-completed assistant status so
  the owner can see what happened and submit a new attempt.

### Dependencies

- Feature 007's authenticated Analyst streaming flow, audit metadata, bounded
  `priorTurns` internal contract, attribution, citation, and graceful-degradation
  behavior.
- Feature 005's established owner-scoped access pattern.
- Existing authoritative market, stock, portfolio, news, research, screening,
  strategy, and comparison tools; this feature does not change their semantics.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In three consecutive trials, the owner can ask a first question,
  add a follow-up, refresh, reopen the conversation, and see the complete ordered
  transcript and evidence with no missing or duplicated message.
- **SC-002**: Boundary tests over conversations longer than five exchanges and
  12,000 context characters select exactly the newest whole completed exchanges
  allowed by context-window-v1 while keeping the current question.
- **SC-003**: 100% of tested current-fact follow-ups call an authoritative tool
  and contain no claim whose only source is an older assistant message.
- **SC-004**: 100% of create/read/continue/rename/delete cross-owner attempts are
  denied without revealing whether the target exists.
- **SC-005**: Repeating an accepted client request identifier creates exactly one
  user message and at most one AI execution in all retry and concurrency tests.
- **SC-006**: After confirmed deletion, the conversation, messages, stored
  evidence, and linked private audit content are absent from owner reads and
  persistence verification.
- **SC-007**: Conversation list and first transcript page meet NFR-001 with the
  stated 10,000-conversation/10,000-message fixture.
- **SC-008**: AI service failure, tool failure, client cancellation, and stream
  disconnection each leave an explicit terminal or recoverable state while
  unrelated market, portfolio, and authentication journeys remain usable.

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001, FR-002, FR-003 | US1 / Scenarios 1-3 | SC-001, SC-002 |
| FR-006, DATA-002, DATA-003 | US1 Scenario 4; US2 Scenario 3 | SC-001, SC-003 |
| FR-009, AI-005 | US1 Scenario 5; edge failures | SC-002, SC-008 |
| FR-010 | Duplicate/concurrent edge cases | SC-005 |
| FR-011 | Existing standalone flow | Regression verification |
| FR-004, FR-005, NFR-001, NFR-003 | US2 Scenarios 1-2 | SC-007 |
| FR-007 | US3 Scenarios 1-2 | Title boundary verification |
| FR-008, DATA-001, DATA-005 | US3 Scenario 3 | SC-006 |
| DATA-004 | Migration/rollout scenario | No fabricated backfill |
| SEC-001, SEC-002 | All stories / unauthorized edges | SC-004 |
| SEC-003, SEC-004, NFR-005 | Privacy/degradation edges | Log/metric inspection |
| AI-001, AI-002 | US1 Scenarios 2-3 | SC-002 |
| AI-003, AI-004 | US1 Scenario 4 / injection cases | SC-003 |
| NFR-002 | US1 Scenario 1 | Stream timing measurement |
| NFR-004 | US1-US3 | Accessibility verification |
