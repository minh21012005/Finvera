# Quickstart: AI Conversation History

## Purpose

Validate the first durable multi-turn Analyst journey, context-window-v1,
owner isolation, idempotency, deletion, failure handling, and compatibility
without exposing private prompts or credentials in recorded evidence.

## Prerequisites

- PostgreSQL is available to backend integration tests.
- Safe test-only owner credentials and internal keys are supplied through the
  existing test configuration; do not paste or commit real credentials.
- V021 follows V020 and Flyway validation is clean.
- Backend, frontend, and AI dependencies are installed from committed manifests.
- For the deterministic acceptance path, AI/provider calls use the feature's
  fake adapter and fixture tool responses. A live-provider check is optional and
  never replaces deterministic gates.

## 1. Contract and artifact checks

From the repository root:

```powershell
python -c "import yaml; yaml.safe_load(open(r'specs/029-ai-conversation-history/contracts/public-api.openapi.yaml', encoding='utf-8')); print('OpenAPI YAML: PASS')"
rg -n "\[NEEDS CLARIFICATION" specs/029-ai-conversation-history
```

Expected:

- OpenAPI parses.
- The clarification search returns no match.
- `context-window-v1.md`, `data-model.md`, `plan.md`, and `tasks.md` use the same
  10-candidate, 5-included, 12,000-character defaults.

## 2. Focused backend checks

From `finvera-be`:

```powershell
.\mvnw.cmd test "-Dtest=AnalystConversationMigrationTests,ConversationContextWindowPolicyTests,AnalystConversationServiceTests,AnalystConversationControllerTests,AnalystConversationAuthorizationTests"
```

Expected:

- V021 creates valid tables, constraints, indexes, cascade direction, and
  nullable audit linkage after V020.
- The selector passes 0/1/5/6/10/11-exchange, exact-budget, overflow,
  failed-state, and oversized-newest boundaries.
- First ask creates exactly one conversation/exchange and sends `accepted`
  before answer events.
- A follow-up derives history from storage; browser-supplied prior turns do not
  exist on the new request.
- Same request ID replays a completed result without another AI call; processing
  duplicates and parallel questions receive retryable `409`.
- All non-owned IDs behave like missing IDs.
- Deletion cascades exchange, stored snapshot, linked query, and linked tool-call
  audit while preserving unrelated legacy audit rows.

## 3. Focused AI checks

From `finvera-ai`:

```powershell
uv run pytest app/features/chat/tests/test_conversation_context.py app/features/chat/tests/test_ask_orchestration.py app/features/orchestration/tests/test_attribution.py
```

Expected:

- Invalid/out-of-budget `priorTurns` fail schema/policy validation.
- Eligible context is labeled as untrusted historical conversation in every
  prompt stage that uses it.
- A stale numeric prior answer cannot satisfy a current-fact question; the
  allowlisted current-data tool is invoked.
- User/assistant history containing tool instructions cannot alter the allowlist
  or bypass attribution/citation/refusal behavior.
- Zero-history requests remain compatible with Feature 007 behavior.

## 4. Focused frontend checks

From `finvera-fe`:

```powershell
npm test -- --run src/features/analyst/conversation-history.test.tsx src/features/analyst/ask-analyst.test.tsx
```

Expected:

- New chat starts locally and creates a server conversation only on first send.
- `accepted` selects the new conversation before deltas render.
- List and older-message pagination add no duplicates or reordered exchanges.
- Refresh/reopen renders the original answer, evidence, coverage, timestamps,
  and failed states.
- Rename and confirmed delete are keyboard operable; focus moves predictably.
- Busy, unavailable, failed, cancelled, and empty states have text labels and do
  not depend on color.

## 5. P1 end-to-end acceptance

Use deterministic fixtures and an authenticated test owner:

1. Open AI Analyst with no selected conversation.
2. Ask `Phân tích FPT hiện tại` using a new client request UUID.
3. Verify `accepted` provides a new conversation/exchange, an automatic title,
   and `context-window-v1` with zero included exchanges.
4. Verify the final answer and its evidence are visible and stored.
5. Ask six follow-ups. Include one old artificial price in a prior fixture and
   finish with `Giá hiện tại là bao nhiêu?`.
6. Verify full history remains visible, the newest permitted whole exchanges are
   selected, and the current STOCK tool result supports the price.
7. Refresh and reopen the thread from the history list.
8. Verify the transcript and public final metadata match what was originally
   shown.

Pass condition: SC-001, SC-002, and SC-003 pass in three consecutive runs with
no missing/duplicate exchange and no prior prose used as current evidence.

## 6. Critical negative paths

### Duplicate and concurrency

- Repeat a completed `clientRequestId`: one exchange exists and the stored final
  is replayed without a second AI invocation.
- Repeat while processing: receive `REQUEST_IN_PROGRESS` with `retryable=true`.
- Send a different request to the same processing conversation: receive
  `CONVERSATION_BUSY`; no exchange is added.
- Reuse a completed request ID with a different question, symbol, or target:
  receive non-retryable `IDEMPOTENCY_KEY_REUSED`; no stored answer is disclosed
  and no AI call runs.

### Authorization

For list, transcript, continue, rename, and delete, exercise an ID owned by a
second fixture owner and an unknown ID. Both return the same non-disclosing
result; no content appears in body or logs.

### Failure and recovery

- Force AI connection failure after `accepted`: exchange becomes `FAILED`, no
  delta is stored as a completed answer, and a new request can follow.
- Force explicit abort: exchange becomes `CANCELLED` when server cancellation is
  observed.
- Seed an expired `PROCESSING` exchange: reconciliation marks it
  `STALE_PROCESSING` and unblocks a new request.

### Deletion

- Delete is blocked while processing.
- After terminal state, confirm deletion and query the database using IDs only.
  Conversation, exchanges, linked query, and tool calls are absent; unrelated
  legacy audit remains.

## 7. Privacy-safe observability check

Use unique canary strings in fixture question, answer, title, citation, and fake
provider error. Capture test logs and metrics.

Expected:

- No canary string appears in logs, metrics, exception details, or traces.
- Correlation ID, conversation/exchange UUID, rule version, counts, duration,
  outcome, and sanitized reason code are present where applicable.

## 8. Performance fixture

Run `AnalystConversationPerformanceTests` against 10,000 conversations and a
10,000-exchange target conversation.

Expected:

- List and first exchange page p95 <= 1 second under the documented local test
  profile.
- Persistence-to-accepted-event p95 overhead <= 500 ms with AI time excluded.
- Queries load only the requested page or ten context candidates.

## 9. Full quality gates

Backend:

```powershell
cd finvera-be
.\mvnw.cmd test
```

AI service:

```powershell
cd finvera-ai
uv run pytest
uv run python -m compileall .
```

Frontend:

```powershell
cd finvera-fe
npm run lint
npm run build
npm test
```

Record exact totals and any skipped live dependency honestly. Do not mark the
feature complete if migration, authorization, context, idempotency, deletion,
AI stale-fact/injection, or P1 acceptance fails.
