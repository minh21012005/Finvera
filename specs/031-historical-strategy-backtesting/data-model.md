# Data Model: Historical Strategy Backtesting

**Feature**: `031-historical-strategy-backtesting`  
**Status**: Phase 1 design

## Ownership and lifecycle

All entities are owned by one authenticated user through `backtest_run.owner_id`.
Child records inherit ownership only through their run and are never queried
without an owner-qualified run lookup.

```text
QUEUED -> RUNNING -> COMPLETED
                  -> WITHHELD
                  -> FAILED
RUNNING -- stale heartbeat and attempts < 2 --> QUEUED
RUNNING -- stale heartbeat and attempts = 2 --> FAILED
```

Terminal states are immutable. A completed result becomes visible in the same
transaction that writes all child rows and changes the run to `COMPLETED`.

## BacktestRun

| Field | Semantics and validation |
|---|---|
| `id` | Stable UUID |
| `ownerId` | Required owner; every read is owner-qualified |
| `idempotencyKey` | Optional owner-scoped opaque key, 1-100 characters |
| `status` | `QUEUED`, `RUNNING`, `COMPLETED`, `WITHHELD`, `FAILED` |
| `version` | Optimistic transition guard |
| `strategyCode` | One of all eight `strategy-signal-v1` codes |
| `strategyRuleVersion` | Exactly the accepted strategy version |
| `symbol`, `instrumentId`, `venue` | Resolved active equity identity |
| `reportingStart`, `reportingEnd` | Inclusive completed-session range |
| `initialCapitalVnd` | Decimal(20,6), positive |
| `riskPerTrancheRate` | Decimal(20,8), `(0,1]` |
| `maxAggregateOpenRiskRate` | Decimal(20,8), `[riskPerTrancheRate,1]` |
| five cost rates | Decimal(20,8), `[0,1)`; complete set or explicit exclusion |
| `costsExcluded` | Required boolean; true is the sole all-zero-cost path |
| rule versions | Engine, metric, pyramiding, sizing, lot-rule identifiers |
| `dataCutoffAcceptedAt` | UTC instant frozen at create |
| `inputFingerprint` | SHA-256 over canonical request, selected accepted input identities, and versions; populated before terminal publication |
| `processedSessions`, `totalSessions` | Non-negative progress; processed never exceeds total |
| `attemptCount` | Claim count, maximum 2 |
| `heartbeatAt` | UTC instant while running |
| `reasonCode` | Stable terminal WITHHELD/FAILED reason; no sensitive text |
| `createdAt`, `startedAt`, `completedAt` | UTC lifecycle instants |

Constraints/indexes:

- Unique `(owner_id, idempotency_key)` where key is present.
- Index `(owner_id, created_at desc, id desc)` for stable listing.
- Claim index `(status, created_at)` and stale scan index `(status, heartbeat_at)`.
- Terminal consistency checks require completion time and permitted reason/result
  combinations.

## BacktestTrade

One row represents one independently entered and exited tranche.

| Field | Semantics |
|---|---|
| `id`, `runId`, `sequence` | Stable identity; sequence unique per run |
| `signalDate`, `entryDate`, `exitDate` | Completed trading dates, ordered |
| `strategyCode`, `strategyRuleVersion` | Entry authority |
| `signalAtr14Vnd`, `stopPriceVnd`, `targetPriceVnd` | Signal-level evidence |
| `quantity`, `lotSize` | Positive exact shares and applicable standard lot |
| raw/effective entry and exit prices | Decimal money with fill-basis separation |
| entry fee, exit fee, sell tax | Exact VND ledger deductions |
| acquisitionCostVnd`, `netExitProceedsVnd`, `netPnlVnd` | Decimal ledger values |
| `tradeReturnRate` | Decimal(34,8), net P/L divided by acquisition cost |
| `exitReason` | `STOP_LOSS`, `TARGET1`, `END_OF_PERIOD` |
| `entryBarId`, `exitBarId` | Exact accepted market inputs |

Unique `(run_id, sequence)` and `(run_id, signal_date, entry_date, sequence)`
protect retry idempotency. No trade exists without a completed exit.

## BacktestEquityPoint

| Field | Semantics |
|---|---|
| `runId`, `tradingDate` | Unique ordered point |
| `cashVnd` | Cash after all session fills |
| `openPositionValueVnd` | Sum of open quantities at the disclosed selected-basis close |
| `totalEquityVnd` | Exactly cash plus open-position value |
| `openTrancheCount` | `0..4` |
| `dailyReturnRate` | Null for first point; otherwise equity ratio change |
| `dailyBarId` | Accepted valuation bar identity |

Index `(run_id, trading_date)` supports stable range pagination.

## BacktestMetric

One row per run and metric code:

`TOTAL_RETURN`, `CAGR`, `WIN_RATE`, `PROFIT_FACTOR`, `MAXIMUM_DRAWDOWN`,
`SHARPE_RATIO`, `AVERAGE_TRADE_RETURN`, `TRADE_COUNT`.

| Field | Semantics |
|---|---|
| `runId`, `metricCode` | Unique metric identity |
| `value` | Decimal(34,8), or null only when unavailable |
| `unit` | `RATE`, `RATIO`, or `COUNT` |
| `availability` | `DEFINED` or `UNAVAILABLE` |
| `reasonCode` | Required exactly when unavailable |
| `metricRuleVersion` | `backtest-metrics-v1` |

## BacktestEntryEvent

Audits every post-signal candidate not represented by a trade.

| Field | Semantics |
|---|---|
| `runId`, `sequence`, `signalDate`, `executionDate` | Stable ordering and time |
| `outcome` | `REJECTED` or `CANCELLED` |
| `reasonCode` | Stable pyramiding/sizing/execution reason |
| `openTrancheCount` | Count at decision time |
| `availableCashVnd`, `remainingRiskVnd` | Optional decimal decision evidence |

No full request payload or arbitrary provider text is stored.

## BacktestEvidence

One bounded key/value record per fact needed to audit the run: selected bar and
indicator identity digest, source set, adjustment status, accepted-time cutoff,
actual processed range, warm-up range, calendar/lot authority and acceptance
time, all rule versions, cost warning, survivorship statement, and known
suspension/delisting limitations. Evidence keys are versioned and values have
an explicit unit or `TEXT`.

An adjustment-factor discontinuity crossed by a pending signal or open tranche
terminates the run as `WITHHELD/CORPORATE_ACTION_UNSUPPORTED`; no partial child
result is published.

## Retention, migration, and rollback

- Migration `V022__create_backtest_schema.sql` adds only new tables and indexes;
  no existing market, stock, portfolio, or positioning row is rewritten.
- First-slice runs have no delete endpoint and remain owner-visible. A future
  retention/deletion policy requires a spec and migration.
- Application rollback leaves additive tables intact. Queued/running rows can
  remain dormant; old code does not read them. A forward fix may recover them.
- Results are systems of record and are not rebuilt from corrected market data;
  owners create a new run with a new cutoff.
