# Configurable Alerts Runbook

Feature 032 evaluates owner-scoped deterministic conditions against accepted Finvera facts and delivers durable in-app notifications. It does not call an LLM or an external notification provider.

## Runtime configuration

| Environment variable | Default | Purpose |
|---|---:|---|
| `FINVERA_ALERT_WORKER_ENABLED` | `true` | Enables evaluation and lease recovery. Management and inbox APIs remain available when disabled. |
| `FINVERA_ALERT_WORKER_POLL_DELAY` | `10s` | Delay between due-work polls. |
| `FINVERA_ALERT_WORKER_RECOVERY_DELAY` | `30s` | Delay between expired-lease recovery passes. |
| `FINVERA_ALERT_WORKER_LEASE_DURATION` | `60s` | Maximum ownership time for a claimed item before recovery. |
| `FINVERA_ALERT_WORKER_BATCH_SIZE` | `50` | Maximum definitions claimed per poll. |
| `FINVERA_ALERT_WORKER_MAX_ATTEMPTS` | `2` | Total attempts, including the first attempt. |

The worker is enabled in normal operation. Disable it only for maintenance or an incident; doing so pauses evaluation but does not fabricate results or remove stored evidence.

## Expected behavior

- PostgreSQL claims due rows with `FOR UPDATE SKIP LOCKED`, so multiple application instances do not intentionally process the same definition.
- A state condition creates one notification when it moves from false/unknown to true and rearms after an eligible false result. Each accepted matching document or published strategy signal is a separate event.
- An absent strategy signal is `WITHHELD` because the stock module publishes triggered signals but no durable negative evaluation fact.
- Missing, stale, mixed-basis, incomplete, or inapplicable facts produce `WITHHELD` and no notification.
- A transient exception is retried once. A second failure records `FAILED` and releases the definition for a later scheduled fact evaluation.
- Definition deletion is soft. Existing evaluations and notifications remain readable and immutable.

## Diagnosis

Inspect Micrometer metrics prefixed with `finvera.alert`. Labels are bounded to condition type, outcome, and stable reason; condition values and owner data are excluded. Check application logs for `alert_evaluation_failed`, then inspect the definition's latest evaluation through `/api/v1/alerts/{id}/evaluations`.

If due work grows, verify the worker flag, database availability, lease expiry, and source-fact freshness. Do not delete lease or evidence rows manually. Restarting the application is safe: expired leases are recovered on the configured recovery cycle.

## Safe operations

1. Disable the worker for planned database maintenance.
2. Keep the management and inbox endpoints available so stored evidence remains inspectable.
3. Apply maintenance and verify V023 plus source datasets.
4. Re-enable the worker. Due definitions resume in bounded batches.

There is no destructive down migration. Application rollback leaves alert data in place for a forward recovery.
