# Data Model: AI Conversation History

**Feature**: `029-ai-conversation-history`

**Decision basis**: `research.md` R-001, R-004 through R-009

## Ownership and source of truth

PostgreSQL owns conversation and exchange records inside the Spring `analyst`
module. `finvera-ai`, Redis, Qdrant, browser state, and LLM responses are not
conversation systems of record. Every repository query includes owner scope at
the service boundary even in the current private-owner deployment.

## Entity: `analyst_conversation`

| Column | Type | Null | Rule |
|---|---|---:|---|
| `id` | UUID | No | Server-generated primary key. |
| `owner_id` | UUID | No | Authenticated owner; indexed with activity ordering. |
| `title` | VARCHAR(120) | No | Trimmed, non-blank; automatic first title is at most 80 characters. |
| `title_source` | VARCHAR(16) | No | `AUTO` or `OWNER`; check constrained. |
| `created_at` | TIMESTAMPTZ | No | UTC transport/storage instant from injected clock. |
| `updated_at` | TIMESTAMPTZ | No | Changes on title or exchange lifecycle update. |
| `last_activity_at` | TIMESTAMPTZ | No | Used with `id` for conversation ordering/cursor. |
| `next_sequence_no` | BIGINT | No | Starts at 1; allocated while the conversation row is locked. |

Indexes and constraints:

- primary key `(id)`;
- index `(owner_id, last_activity_at DESC, id DESC)`;
- `length(btrim(title)) BETWEEN 1 AND 120`;
- `next_sequence_no >= 1`.

There is no soft-delete column. Successful deletion removes the row.

## Entity: `analyst_conversation_exchange`

One exchange is one accepted user question plus its single assistant attempt.
The API may render it as two message bubbles.

| Column | Type | Null | Rule |
|---|---|---:|---|
| `id` | UUID | No | Server-generated primary key. |
| `conversation_id` | UUID | No | FK to conversation with `ON DELETE CASCADE`. |
| `owner_id` | UUID | No | Duplicated deliberately for owner-scoped idempotency and defense in depth; must match the conversation owner in service logic. |
| `sequence_no` | BIGINT | No | Monotonic exchange order inside the conversation. |
| `client_request_id` | UUID | No | Stable browser-generated retry identity. |
| `question` | VARCHAR(2000) | No | Trimmed accepted question. |
| `symbol` | VARCHAR(20) | Yes | Optional normalized current symbol context; not authoritative evidence. |
| `status` | VARCHAR(16) | No | `PROCESSING`, `COMPLETED`, `FAILED`, or `CANCELLED`. |
| `answer` | TEXT | Yes | Present only for `COMPLETED`; sanitized public answer text. |
| `response_schema_version` | VARCHAR(40) | Yes | `analyst-conversation-answer-v1` for completed snapshots. |
| `response_metadata` | JSONB | Yes | Sanitized public final-event fields excluding duplicated answer text. |
| `failure_code` | VARCHAR(64) | Yes | Stable non-sensitive code for failed/cancelled state; no raw exception. |
| `context_rule_version` | VARCHAR(40) | No | `context-window-v1`. |
| `context_included_count` | SMALLINT | No | Selected prior exchanges, 0-5. |
| `context_omitted_count` | INTEGER | No | Completed candidates omitted by count/size bounds. |
| `created_at` | TIMESTAMPTZ | No | Accepted time. |
| `completed_at` | TIMESTAMPTZ | Yes | Terminal-state time. |

Indexes and constraints:

- unique `(conversation_id, sequence_no)`;
- unique `(owner_id, client_request_id)`;
- index `(conversation_id, sequence_no DESC, id DESC)`;
- partial unique index on `(conversation_id)` where `status = 'PROCESSING'`;
- question length 1-2000 after trim;
- `context_included_count BETWEEN 0 AND 5` and omitted count non-negative;
- state shape:
  - `PROCESSING`: answer, metadata, failure, completion time are null;
  - `COMPLETED`: answer, schema version, metadata, completion time present and
    failure null;
  - `FAILED`/`CANCELLED`: answer and metadata null, failure code and completion
    time present.

`response_metadata` contains only the validated public DTO representation:

```json
{
  "structuredClaims": [],
  "documentClaims": [],
  "refused": false,
  "toolCalls": [],
  "toolCallBoundReached": false,
  "ruleVersion": "orchestration-v1",
  "synthesisMode": "ONLINE",
  "plannerMode": "MODEL",
  "claimCoverage": "FULL"
}
```

It never contains raw prompts, raw model/provider responses, raw tool-result
payloads, credentials, or private data from another owner.

## Existing entity extension: `analyst_query`

Add nullable `conversation_exchange_id UUID` referencing
`analyst_conversation_exchange(id) ON DELETE CASCADE`, plus a unique partial
index for non-null values.

- Existing audit rows remain null and unchanged.
- New conversation-backed asks set this field.
- Standalone `/api/v1/analyst/ask` continues to create a null-linked audit row.
- Deleting a conversation cascades through exchange to its linked query, whose
  existing FK then cascades to tool-call audit rows.

The existing `question_preview` and `question_hash` remain audit fields. They do
not replace the conversation question and are removed with a linked conversation
delete.

## Relationships

```text
owner
  1
  └── * analyst_conversation
          1
          └── * analyst_conversation_exchange
                    0..1
                    └── 1 analyst_query
                              1
                              └── * analyst_tool_call
```

## State transitions

```text
new valid request
  -> PROCESSING
       -> COMPLETED
       -> FAILED
       -> CANCELLED
```

Terminal exchange states are immutable. Retrying creates no row when the same
`client_request_id` is used; a deliberate new attempt uses a new request ID and
the next sequence number.

An exchange left `PROCESSING` past `askTimeout + 60 seconds` is reconciled to
`FAILED` with `failure_code = STALE_PROCESSING` before it can block another
question. Reconciliation records counts only, never content.

## Sliding-window derivation

For a new exchange at sequence `n`:

1. Query at most 10 `COMPLETED` exchanges with sequence `< n`, newest first.
2. Starting at the newest, add the whole `question` + `answer` pair if:
   - fewer than 5 pairs have been selected; and
   - aggregate Unicode character count would remain `<= 12,000`.
3. If a pair does not fit, stop. Omit it and every older candidate so the window
   remains a contiguous suffix of recent completed history; no text is
   truncated.
4. Reverse selected pairs into chronological order.
5. Send only those pairs plus the current question to `finvera-ai`. If the
   newest completed pair alone exceeds the budget, send no prior turns.

The character count uses Unicode code points after stored text normalization.
It is not a financial calculation and does not use model-generated summaries.

## Time semantics

- All persisted instants are UTC `TIMESTAMPTZ`.
- Public transport uses RFC 3339 instants.
- The frontend displays them in `Asia/Ho_Chi_Minh` using existing locale
  conventions.
- Evidence `asOf` values are preserved from the original final result and are
  never replaced by message time.

## Retention and deletion

- No automatic expiration in v1.
- Owner-confirmed delete hard-deletes the conversation and cascades all related
  exchanges, response snapshots, linked queries, and linked tool-call audits.
- Deletion is refused while a `PROCESSING` exchange exists.
- Legacy audit rows and unrelated conversations remain untouched.
- Database backup retention is an operator concern and must be documented in the
  deployment runbook; the application cannot claim immediate removal from an
  already-created external backup.

## Migration and rollback

Planned migration: `V021__create_analyst_conversation_history.sql`, after the
current V020 migration.

Forward migration creates the two tables, constraints/indexes, and nullable
audit link. It performs no data backfill. Rollback of application code is safe
because all changes are additive and the old endpoint ignores the new tables.
The schema is retained during rollback; destructive down-migration is not used.
