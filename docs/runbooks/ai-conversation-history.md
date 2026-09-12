# AI Conversation History Runbook

**Feature**: `specs/029-ai-conversation-history/`  
**Public contract**: `conversation-history-v1`  
**Context rule**: `context-window-v1`

## Runtime ownership

PostgreSQL is the source of truth for conversations, exchanges, public answer
snapshots, and links to Analyst audit rows. Spring Boot authenticates the owner
and exposes the public API. `finvera-ai` receives only the current question and
a bounded history window; it stores no conversation state.

## Migration and rollout

Flyway migration `V021__create_analyst_conversation_history.sql` is additive.
It creates `analyst_conversation` and `analyst_conversation_exchange`, then
adds nullable `analyst_query.conversation_exchange_id`. It performs no backfill,
so existing Analyst audit records and `/api/v1/analyst/ask` remain intact.

Before rollout, back up PostgreSQL and run the backend test suite. After
rollout, verify Flyway reports version 021 and call the list endpoint as the
authenticated owner. Application rollback is safe because older code ignores
the additive tables and nullable column. Keep the schema during rollback; do
not run a destructive down migration.

## Configuration

| Setting under `finvera.analyst` | Default | Contract |
|---|---:|---|
| `conversation-context-candidates` | 10 | 1-10 completed exchanges read from PostgreSQL |
| `conversation-context-included` | 5 | 1-5, never greater than candidates |
| `conversation-context-characters` | 12000 | 1-12000 Unicode code points |
| `conversation-stale-grace` | 60s | Added to `ask-timeout` before recovery |

Lower values reduce prompt size. Raising a value beyond its maximum requires a
new context-rule version and updated evaluation evidence.

## Failure and recovery

- The system persists `PROCESSING` before emitting the first `accepted` event.
- Provider, tool, or orchestration failure becomes `FAILED` with a sanitized
  reason code. Partial deltas never become a completed stored answer.
- A client disconnect observed by Spring becomes `CANCELLED`; if completion
  wins the race, the immutable terminal state remains `COMPLETED`.
- A processing exchange older than `ask-timeout + stale-grace` becomes
  `FAILED/STALE_PROCESSING` when the next request for that owned conversation
  is prepared.
- A completed duplicate `clientRequestId` replays its stored result without an
  AI call. A processing duplicate or parallel question returns retryable 409.
  Reusing the ID with another payload is non-retryable.

Use correlation ID, conversation/exchange UUID, state, rule version, counts,
and duration for investigation. Never add question, answer, title, citation,
prompt, tool payload, cookie, credential, or provider response to logs or
metric tags.

## Monitoring

Content-free metrics use `finvera.analyst.conversation.*` for accepted/created,
completed outcome, included/omitted context counts, replay, sanitized request
conflicts and failures, cancellation, deletion, stale recovery, list/read
latency, and final-persistence timing. Standard HTTP server metrics distinguish
non-disclosing ownership failures by response status; use the correlation ID to
trace one request without logging its content. Monitor sustained failure growth,
stale recovery, and persistence latency. History reads and management remain
available while AI is unavailable.

## Retention and deletion

Version 1 has no automatic expiry. Owner-confirmed deletion is blocked during
processing. Successful deletion removes the conversation, exchanges, stored
public snapshots, linked Analyst queries, and linked tool-call audits through
database cascades. Unrelated legacy audit rows remain.

Deletion cannot remove bytes already captured in a database backup. Backup
retention and access are deployment responsibilities; apply the configured
backup lifecycle and do not claim immediate backup erasure.

## Verification

Run `specs/029-ai-conversation-history/quickstart.md`. The fixture with 10,000
conversations and 10,000 exchanges must keep p95 list/transcript reads within
one second and accepted persistence within 500 milliseconds under the local
test profile.
