# Quickstart: Configurable Alerts

## Prerequisites

- PostgreSQL and the existing Finvera backend dependencies are available.
- Migration V023 has applied.
- Accepted completed daily bars and any required indicator/signal/regime facts exist.
- Set `FINVERA_ALERT_WORKER_ENABLED=true` (the application default).

## Automated verification

```powershell
cd finvera-be
.\mvnw.cmd test

cd ..\finvera-fe
npm run lint
npm run test
npm run build
```

## P1 acceptance path

1. Sign in through the owner session and open `/alerts`.
2. Create a `PRICE_ABOVE` alert for an active symbol with a positive VND threshold and explicit `RAW` or `PROVIDER_ADJUSTED` basis.
3. Confirm the response and list show the server-generated summary, enabled state, `UNKNOWN` episode, and due evaluation state.
4. Allow the worker to evaluate the latest completed accepted bar. Confirm the detail shows outcome, fact time, evaluated time, source, unit, rule version, and either the observed comparison or a stable withheld reason.
5. When the condition is true, open the inbox and verify one unread notification. Mark it read and confirm unread count decrements idempotently.
6. Re-run against the same true fact and confirm no duplicate. Feed an eligible false then a newer true fact and confirm exactly one second notification.
7. Disable the alert and confirm no further evaluation. Re-enable it and confirm only current facts are considered. Delete it and confirm old notifications still render unchanged.

Use [public-api.openapi.yaml](./contracts/public-api.openapi.yaml) for exact request/response shapes.

## Critical negative paths

- Request an alert and notification belonging to a second owner and request random UUIDs. All paths must return the same 404 reason and disclose no payload.
- Create 101 retained definitions or enable a 51st definition. The API must reject atomically with a stable quota reason.
- Evaluate missing, stale, partial, mismatched-basis, and incomplete-history facts. Each must be `WITHHELD`, must retain the stable reason, and must produce no notification.
- Inject an unexpected source exception twice. Verify exactly two total attempts, terminal `FAILED` for that work item, no duplicate notification, and normal market/portfolio/research endpoints remain usable.

## Observable results

- Metrics expose due age, evaluation duration, outcome/reason counts, triggers, retries, and terminal failures without owner IDs or condition values.
- Logs include correlation and stable state/reason only; thresholds, portfolio values, document contents, and evidence snapshots are absent.
- With 50 enabled definitions, 99% finish within 30 seconds of fact eligibility in the reference environment.
