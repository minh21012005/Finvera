# Backtest Financial Contract v1

**Engine version**: `backtest-engine-v1`  
**Metrics version**: `backtest-metrics-v1`  
**Pyramiding version**: `pyramiding-v1`  
**Dependencies**: `strategy-signal-v1`, `position-sizing-v1`, `market-lot-v1`

## Time and input boundary

`dataCutoffAcceptedAt` is frozen at create time. A decision on trading date D
may read only facts effective on or before D and accepted on or before the
cutoff. Warm-up precedes `reportingStart` but cannot create trades or returns.

For each completed symbol session D:

1. Compare the adjustment factor with the prior eligible session. If it changed
   while a tranche is open or after a pending signal was created, withhold with
   `CORPORATE_ACTION_UNSUPPORTED`.
2. Process gap and in-bar exits of existing tranches under the rules below.
3. Execute a pending signal from the prior eligible session at D open.
4. Apply D's remaining high/low range to a newly opened tranche.
5. Mark all open tranches at D close on the selected series basis and publish the daily equity point.
6. Evaluate D's completed facts to create at most one pending entry for the next
   eligible symbol session.

No decision uses D+1 data before step 2 on D+1.

## Entry and exit fills

For selected-basis open `O`, target `T`, stop `S`, entry slippage `es`, and exit slippage
`xs`:

```text
effectiveEntry = O * (1 + es)
gapStopFill    = O * (1 - xs) when O <= S
gapTargetFill  = O * (1 - xs) when O >= T
stopTouchFill  = S * (1 - xs) when low <= S
targetTouchFill= T * (1 - xs) when high >= T
terminalFill   = finalClose * (1 - xs)
```

If stop and target are both reachable in one bar and the open establishes
neither gap outcome, use stop. Apply exit fee and sell tax to gross exit value.
Existing gap exits precede a new open fill. New tranches may stop or target in
their entry bar. Missing/unusable next-session open cancels the pending entry
with `EXECUTION_PRICE_UNAVAILABLE`; it is never shifted across an unknown gap.

## Signal episodes and pyramiding

A signal episode starts when the selected strategy changes from a completed
`NO_SIGNAL` evaluation to `SIGNAL`. The first in-range signal may open a tranche.
While tranches are open, an addition also requires:

```text
effectiveEntry >= latestOpenTrancheEffectiveEntry + 0.5 * signalDateATR14
openTrancheCount < 4
```

Persistent `SIGNAL` on consecutive sessions is one episode. A pending entry is
not duplicated. Each tranche keeps its signal date, entry/exit evidence, stop,
target1, quantity, costs, and P/L. Stable rejected-event reasons include:
`NOT_NEW_SIGNAL_EPISODE`, `PYRAMID_PRICE_STEP_NOT_MET`,
`MAX_TRANCHES_REACHED`, `AGGREGATE_RISK_EXHAUSTED`, `BELOW_STANDARD_LOT`, and
`EXECUTION_PRICE_UNAVAILABLE`.

## Aggregate risk and sizing

At a candidate entry:

```text
currentEquity    = cash + sum(openQty * currentRawClose)
aggregateCapVnd  = currentEquity * maxAggregateOpenRiskRate
openRiskVnd      = sum(max(0, qty * (acquisitionUnitCost - netStopProceedsPerShare)))
remainingRiskVnd = max(0, aggregateCapVnd - openRiskVnd)
perTrancheVnd    = currentEquity * riskPerTrancheRate
newRiskBudgetVnd = min(perTrancheVnd, remainingRiskVnd)
```

If `newRiskBudgetVnd <= 0`, reject the addition. Otherwise invoke
`position-sizing-v1` with current equity, current cash, the effective entry and
signal stop, declared costs, standard lot, and no portfolio concentration caps.
Its quantity and postconditions are authoritative.

`market-lot-v1` is the current standard-board-lot assumption (100 shares) and
is applied consistently to every simulated session. It is a declared
counterfactual for dates before that rule's current source decision, not a
claim about the historical lot regime. Results MUST disclose
`CURRENT_MARKET_LOT_APPLIED_HISTORICALLY`; a later point-in-time lot ledger
requires a new versioned contract.

## Cash, equity, and P/L

```text
entryDebit       = qty * effectiveEntry * (1 + entryFeeRate)
grossExit        = qty * effectiveExit
exitDeductions   = grossExit * (exitFeeRate + sellTaxRate)
netExitCredit    = grossExit - exitDeductions
netPnL           = netExitCredit - entryDebit
cashAfterEntry   = cashBefore - entryDebit
cashAfterExit    = cashBefore + netExitCredit
positionValue[D] = sum(openQty * selectedBasisClose[D])
equity[D]        = cash[D] + positionValue[D]
```

Each debit/credit occurs exactly once. Explicit cost exclusion follows
`position-sizing-v1` and must emit `COSTS_EXCLUDED`.

## Metrics

Let `E0` be initial capital, `EN` terminal equity, `days` elapsed calendar days,
`r[d] = equity[d] / equity[d-1] - 1`, and `pnl[t]`/`cost[t]` refer to a closed
tranche.

```text
totalReturn = EN / E0 - 1
CAGR = (EN / E0) ^ (365 / days) - 1
tradeReturn[t] = pnl[t] / cost[t]
winRate = count(pnl[t] > 0) / count(closed t)
profitFactor = sum(positive pnl) / abs(sum(negative pnl))
averageTradeReturn = mean(tradeReturn[t])
sampleStdDev = sqrt(sum((r[d] - mean(r))^2) / (count(r) - 1))
Sharpe = mean(r) / sampleStdDev * sqrt(252)
drawdown[d] = equity[d] / max(equity[0..d]) - 1
maximumDrawdown = min(drawdown[d])
```

Ratios output scale 8, HALF_EVEN. Money uses decimal precision 34 internally.
CAGR is unavailable for non-positive terminal equity or fewer than one elapsed
day. Win rate and average trade are unavailable with zero closed tranches.
Profit factor is unavailable with zero closed or zero losing tranches, and is
zero if losses exist with no wins. Sharpe is unavailable with fewer than two
daily returns or zero sample deviation. Undefined values are `null` with a
stable reason, never NaN or infinity.

## Terminal behavior

Close every open tranche at final selected-basis close with normal exit slippage, fee, and
tax and reason `END_OF_PERIOD`. A coherent no-signal run completes with zero
trades, flat cash/equity, zero total return and drawdown, and unavailable
trade-dependent metrics. Input incoherence produces `WITHHELD`, not an empty
successful result.

An adjustment-factor change during warm-up or while flat is permitted. A change
between signal and entry or while any tranche is open withholds the whole run;
the engine never derives share or cash transformations from an opaque factor.

## Price-basis limitation

The selected series must be uniformly `RAW` or `PROVIDER_ADJUSTED` and match
the indicator basis. VCI `PROVIDER_ADJUSTED` OHLC is a normalized simulation
basis; it is not a literal historical cash execution price and emits
`PROVIDER_ADJUSTED_EXECUTION_BASIS`. Mixed or dual `ADJUSTED` rows are withheld
until a raw/adjusted conversion and complete corporate-action ledger are
versioned.
