# Data Model: Configurable Alerts

## Shared conventions

- UUID primary keys; owner IDs are copied from the authenticated principal.
- `TIMESTAMPTZ` stores UTC instants; trading dates are `DATE` interpreted in `Asia/Ho_Chi_Minh`.
- Financial values are canonical decimal strings in condition/evidence JSON and `NUMERIC(34,12)` where indexed relational values are required. Java uses `BigDecimal` only.
- JSON snapshots are immutable business evidence, not arbitrary executable expressions.
- Rule version is `alert-condition-v1`; every upstream fact retains its own producer version.

## AlertDefinition (`alert_definition`)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key |
| `owner_id` | UUID | Required; unique pair with ID |
| `name` | varchar(120) | Trimmed, 1-120 |
| `condition_type` | varchar(40) | Supported enum only |
| `condition_version` | varchar(40) | `alert-condition-v1` |
| `condition_payload` | jsonb | Canonical typed payload object |
| `condition_summary` | varchar(500) | Server-generated deterministic Vietnamese summary |
| `enabled` | boolean | New definitions default true |
| `episode_state` | varchar(12) | `UNKNOWN`, `FALSE`, `TRUE`; event alerts remain `UNKNOWN` |
| `episode_sequence` | bigint | Starts 0; increments on a new true episode |
| `baseline_at` | timestamptz | Creation or most recent enable time |
| `last_fact_key` | varchar(200) | Last immutable evaluated source key, nullable |
| `last_fact_at` | timestamptz | Effective/observed time, nullable |
| `last_evaluated_at` | timestamptz | Nullable |
| `last_outcome` | varchar(16) | `FALSE`, `TRUE`, `WITHHELD`, `FAILED`, nullable |
| `last_reason_code` | varchar(64) | Required for withheld/failed, otherwise nullable |
| `last_triggered_at` | timestamptz | Nullable |
| `last_delivery_outcome` | varchar(16) | `DELIVERED`, `FAILED`, nullable |
| `next_evaluation_at` | timestamptz | Due cursor when enabled |
| `lease_token` | UUID | Nullable worker claim token |
| `lease_until` | timestamptz | Nullable; stale leases are reclaimable |
| `attempt_count` | smallint | 0-2 for current claimed work |
| `row_version` | bigint | Optimistic version |
| `created_at`, `updated_at`, `deleted_at` | timestamptz | Soft deletion; deleted implies disabled |

Indexes: owner stable page `(owner_id, created_at DESC, id DESC)` excluding deleted; worker due `(next_evaluation_at, id)` where enabled and not deleted; lease expiry. Quotas are enforced in a serializable/locked owner-scoped create/state-change transaction.

## Condition payloads

All payloads include `type`. Symbol conditions store normalized `symbol` and where relevant `adjustmentBasis` (`RAW` or `PROVIDER_ADJUSTED`). Instrument IDs are resolved at the application boundary and captured in evaluation evidence. Operators are encoded into type names to keep the payload closed.

| Types | Additional fields and validation |
|---|---|
| `PRICE_ABOVE`, `PRICE_BELOW` | `thresholdVnd > 0`, `adjustmentBasis` |
| `RSI_ABOVE`, `RSI_BELOW` | `threshold` in `[0,100]` |
| `MACD_BULLISH_CROSS`, `MACD_BEARISH_CROSS` | no free parameters; MACD line against signal line |
| `MA_BULLISH_CROSS`, `MA_BEARISH_CROSS` | `shortWindow`, `longWindow` from available set `{20,50,200}`, short < long |
| `VOLUME_SPIKE` | `multiple > 0`, fixed `lookbackSessions=20` |
| `BREAKOUT`, `BREAKDOWN` | fixed `lookbackSessions=20`, `adjustmentBasis` |
| `MARKET_REGIME_CHANGE` | `targetLabel` in `BULL, EARLY_BULL, SIDEWAYS, EARLY_BEAR, BEAR`, fixed `assessmentBasis=EOD` |
| `STRATEGY_SIGNAL` | `strategyCode` from published stock enum |
| `PORTFOLIO_CONCENTRATION_ABOVE` | owned `portfolioId`, `thresholdPercent` in `(0,100]` |
| `NEW_DOCUMENT` | required resolved symbol/instrument; optional `documentType` |

## AlertEvaluation (`alert_evaluation`)

| Field | Type | Rules |
|---|---|---|
| `id`, `alert_id`, `owner_id` | UUID | Alert/owner composite foreign key; owner copied for scoped reads |
| `fact_key` | varchar(200) | Hash/key of immutable upstream IDs and accepted versions |
| `fact_at`, `accepted_at`, `evaluated_at` | timestamptz | Effective, ingestion, and evaluation times |
| `outcome` | varchar(16) | `FALSE`, `TRUE`, `WITHHELD`, `FAILED` |
| `reason_code` | varchar(64) | Stable reason where applicable |
| `evidence_snapshot` | jsonb | Immutable, typed evidence without document body |
| `attempt_count` | smallint | 1-2 |
| `duration_ms` | integer | Nonnegative |

Unique `(alert_id, fact_key)` prevents duplicate evaluation persistence. Stable page index `(alert_id, evaluated_at DESC, id DESC)`.

Evidence contains condition summary, source IDs, source name, effective/accepted timestamps, value/threshold/transition, unit, adjustment basis, producer rule versions, and status/reasons. It never contains research document text.

## Notification (`alert_notification`)

| Field | Type | Rules |
|---|---|---|
| `id`, `owner_id` | UUID | Owner-scoped resource |
| `alert_id` | UUID | Foreign key without cascade; soft-deleted parent remains |
| `evaluation_id` | UUID | Source evaluation |
| `episode_sequence` | bigint | Present for state conditions |
| `event_key` | varchar(200) | Present for event conditions |
| `title`, `message` | varchar | Server-generated deterministic text |
| `condition_snapshot`, `evidence_snapshot` | jsonb | Immutable |
| `triggered_at`, `delivered_at` | timestamptz | Required |
| `read_at` | timestamptz | Nullable; marking read is idempotent |

Partial unique indexes enforce `(alert_id, episode_sequence)` when sequence is present and `(alert_id, event_key)` when event key is present. Owner inbox index is `(owner_id, delivered_at DESC, id DESC)`; unread count uses a partial owner index where `read_at IS NULL`.

## DeliveryAttempt (`alert_delivery_attempt`)

One row per notification/channel/attempt. Channel is currently `IN_APP`; attempt number is 1; outcome is `DELIVERED` or `FAILED`; timestamps and a stable reason are stored. Unique `(notification_id, channel, attempt_no)`.

## State transitions

```text
create -> enabled, UNKNOWN, baseline=now, due=now
enabled -> disabled: clear lease/due, keep history
disabled -> enabled: UNKNOWN, baseline=now, due=now, clear retry state
enabled -> soft deleted: disabled + deleted_at, preserve history

eligible FALSE: episode_state=FALSE
eligible TRUE from UNKNOWN/FALSE: episode_sequence++, state=TRUE, notification
eligible TRUE from TRUE: state remains TRUE, no notification
WITHHELD: preserve episode_state; record reason
transient error attempt 1: due for one retry
transient error attempt 2: FAILED; later normal polling remains possible
```

Document events and published strategy-signal events do not alter episode state. Each distinct accepted event after the baseline/cursor produces one notification, bounded to the worker batch; the cursor advances only after transactional persistence. Because the stock module does not persist negative strategy evaluations, absence of a published signal is `WITHHELD`, not `FALSE`.

## Migration and retention

`V023__create_alert_schema.sql` creates empty tables, checks, foreign keys, and indexes. No backfill is required. Alert definitions are soft deleted. Evaluations, notifications, and delivery attempts have no automatic expiry. Application rollback leaves tables intact and disables the worker; forward deployment resumes safely from due/lease state.
