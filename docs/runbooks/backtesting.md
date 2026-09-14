# Historical strategy backtesting

Feature 031 runs one of the eight `strategy-signal-v1` strategies over a
completed-session date range. The owner creates a run at `/backtests`; Spring
freezes `dataCutoffAcceptedAt`, persists `QUEUED`, and returns immediately.
The enabled-by-default worker claims each run atomically and publishes all
children and the terminal state in one transaction.

## Enablement

```text
FINVERA_BACKTEST_WORKER_ENABLED=true
FINVERA_BACKTEST_WORKER_CONCURRENCY=1
FINVERA_BACKTEST_WORKER_STALE_AFTER=5m
```

Keep concurrency low because each run reads up to ten years of daily history.
Set `FINVERA_BACKTEST_WORKER_ENABLED=false` only during maintenance or incident
response; while disabled, newly created runs remain `QUEUED`.
The executor and its queue are bounded. A rejected local dispatch leaves the
run `QUEUED`; the next poll may claim it. A stale first attempt is requeued.
The second stale or failed attempt becomes `FAILED`; retry is never unbounded.
An active worker writes a heartbeat every 30 seconds by default. Keep the
heartbeat interval comfortably below `stale-after`; both values are configurable
with `FINVERA_BACKTEST_WORKER_HEARTBEAT_DELAY` and
`FINVERA_BACKTEST_WORKER_STALE_AFTER`.

## Financial behavior

- Decisions use facts effective by session D and revisions selected at or
  before the frozen cutoff. Existing indicator `calculated_at` is its persisted
  publication instant because the current indicator schema has no separate
  `accepted_at` column.
- A signal enters at the next eligible session open. A missing or unusable open
  cancels that entry and never shifts it to a later session.
- Existing exits happen before entries. Gap prices use the open; if stop and
  target are both touched within a daily bar, stop wins.
- New false-to-true episodes may pyramid only after a 0.5 ATR price step, with
  at most four independent tranches and an aggregate open-risk cap.
- Remaining tranches close at the final selected-basis close. Fees, sell tax, and slippage
  are charged exactly once; excluding costs produces a visible warning.
- An adjustment-factor change while an entry is pending or a tranche is open
  yields `WITHHELD/CORPORATE_ACTION_UNSUPPORTED` because the source does not
  provide enough event detail to transform shares and cash safely.
- `market-lot-v1` applies the current 100-share standard lot to all sessions.
  Historical periods show `CURRENT_MARKET_LOT_APPLIED_HISTORICALLY`; this is a
  disclosed counterfactual, not a claim about the historical lot regime.
- VCI `PROVIDER_ADJUSTED` OHLC is a normalized simulation basis. The UI emits
  `PROVIDER_ADJUSTED_EXECUTION_BASIS`; values must not be read as literal
  historical cash executions. Mixed or dual price bases are withheld.

## Operations

Observe `finvera.backtest.runs{status=...}`, `finvera.backtest.terminal`, and
`finvera.backtest.execution`. Logs and metrics contain identifiers, states,
reason codes, counts, and timing only; they must not include financial payloads.

If queue age grows, check worker enablement, executor saturation, PostgreSQL,
and repeated reason codes. Do not edit terminal rows or child ledgers. For a
stuck `RUNNING` row, first confirm its heartbeat is older than `stale-after`;
recovery requeues attempt one or safely fails attempt two.

Ledger invariants are `equity = cash + open position value`, every debit and
credit occurs once, quantities are whole lots, and terminal open quantity is
zero. An invariant or persistence failure must roll back the result transaction
and must never expose partial children as `COMPLETED`.
