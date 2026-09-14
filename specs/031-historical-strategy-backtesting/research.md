# Research: Historical Strategy Backtesting

**Feature**: `031-historical-strategy-backtesting`  
**Date**: 2026-09-13  
**Status**: Complete

## R-001: Architecture and execution boundary

**Decision**: Add a layered `backtest` module to the Spring modular monolith.
Persist a run before returning, then claim and execute it through a bounded
in-process worker with PostgreSQL-backed lifecycle and recovery. Do not add
Kafka, Redis state, a new service, or any AI dependency.

**Rationale**: Backtests are asynchronous and durable, but current single-owner
scale does not justify another deployable. PostgreSQL claim/state transitions
make retries and restarts observable and idempotent.

**Alternatives considered**: Synchronous HTTP blocks ordinary work; an
untracked thread loses jobs; Kafka or a separate service adds unjustified
operations and an ADR requirement.

**Risks/validation**: Kill a worker after claim and verify stale recovery never
publishes partial output or duplicates children. Bound the queue, date span,
attempts, and concurrent workers.

## R-002: Historical point-in-time input policy

**Decision**: Freeze `dataCutoffAcceptedAt` when a run is created. For every
trading date, select the latest accepted revision whose `accepted_at` is not
later than that cutoff and preserve the selected bar/indicator identities in a
run fingerprint. Strategy decisions may read only effective dates up to the
decision date.

**Rationale**: A fixed cutoff makes one run internally coherent and prevents a
correction accepted during execution from changing later portions of the same
run. Persisted outputs remain immutable after completion.

**Alternatives considered**: Current rows only rewrite history across reruns;
acceptance cutoff alone without selected-input identity is hard to audit;
copying all input rows into new snapshot tables is excessive for the first
slice.

**Risks/validation**: Fixtures include a later correction for an earlier bar and
prove the earlier-cutoff run cannot see it while a later run can.

## R-003: Strategy evaluation and warm-up

**Decision**: Support all eight `strategy-signal-v1` strategies by invoking its
pure evaluator for each completed symbol session. Read historical accepted
indicator results rather than recomputing formulas. Load enough prior sessions
for the selected strategy and ATR/level readiness, but begin trades and returns
only at the requested start date.

**Rationale**: Reusing the normative evaluator prevents the current product and
backtest from developing different entry rules. Warm-up is input preparation,
not simulated performance.

**Alternatives considered**: Persisted triggered signals may be incomplete for
past dates; reimplementing strategy predicates duplicates authority; recomputing
indicators inside backtesting violates module ownership.

**Risks/validation**: One trigger/non-trigger and exact minimum-history boundary
per strategy, plus equality with direct `StrategySignalV1` fixtures.

## R-004: Entry, exit, gap, and daily-bar ambiguity

**Decision**: A signal on completed D creates a pending entry for the next
eligible symbol session. Fill at raw open with entry slippage. For every open
tranche on a session: a gap through stop exits at that open; a gap through
target exits at that open; otherwise a low/high touch fills at its stop/target
level with exit slippage. If both touch and sequence is unknowable, stop wins.
Existing-tranche exits at the open are processed before a new entry at that
open; a newly entered tranche is exposed to the remainder of that daily bar.
Final open tranches close at raw final-session close.

**Rationale**: Next-open execution blocks same-close look-ahead. Explicit gap
and ordering rules remove optimistic discretion from OHLC-only data.

**Alternatives considered**: Same-close execution leaks information; target
first inflates results; ignoring gaps fabricates fills; entry-zone limit orders
would test a different execution policy.

**Risks/validation**: Test normal touches, both touched, gap stop, gap target,
entry-and-exit same bar, missing next session, and terminal liquidation.

## R-005: Pyramiding policy

**Decision**: Version `pyramiding-v1` permits at most four independent open
tranches. An add requires a new false-to-true strategy episode and its effective
next-open entry to be at least `0.5 * ATR14(signal date)` above the latest open
tranche fill. Each tranche retains its own stop, target, cost, and exit. Record
every rejected add with a stable reason.

**Rationale**: This captures favorable anti-martingale scaling without treating
a multi-day true state as daily permission to buy. Independent tranches preserve
the exact economics of each signal.

**Alternatives considered**: Daily additions overtrade; averaging down increases
risk against the move; moving existing stops/targets invents an unapproved
position-management rule.

**Risks/validation**: Cover state true-true, false-true, exact 0.5 ATR, one tick
below, fifth tranche, and independently timed exits.

## R-006: Aggregate risk and sizing

**Decision**: Before each entry, compute current equity and:

```text
aggregateRiskCap = currentEquity * maxAggregateOpenRiskRate
openRisk = sum(max(0, trancheQty * (acquisitionUnitCost - netStopProceedsPerShare)))
remainingRisk = max(0, aggregateRiskCap - openRisk)
trancheRisk = min(currentEquity * riskPerTrancheRate, remainingRisk)
```

Invoke `position-sizing-v1` with `capitalBase=currentEquity`, available cash,
the new effective entry/stop inputs, `trancheRisk`, declared costs, and no
portfolio concentration caps. Reject additions below one lot.

**Rationale**: Per-tranche sizing preserves the shared contract while aggregate
heat prevents four full-risk entries from multiplying risk without bound.

**Alternatives considered**: Four reserved equal buckets strand risk; no heat
cap is unsafe; using initial capital forever ignores realized portfolio change.

**Risks/validation**: Verify cap equality, reduced final tranche, insufficient
heat, insufficient cash, tightened/widened equity, and exact lot floors.

## R-007: Costs and cash ledger

**Decision**: Apply entry slippage before entry fee and exit slippage before
exit fee/sell tax, exactly as `position-sizing-v1`. Debit acquisition cost once;
credit net exit proceeds once. Explicit exclusion is the only accepted all-zero
cost path and emits `COSTS_EXCLUDED`.

**Rationale**: Interactive sizing and simulation must share loss and
affordability semantics.

**Alternatives considered**: End-of-run cost subtraction distorts sizing and
cash; mixed percentage/fixed costs are outside the approved input contract.

**Risks/validation**: Ledger identities and independently calculated cost
vectors cover every component and zero/excluded cases.

## R-008: Metric contract

**Decision**: Use `backtest-metrics-v1`:

- `totalReturn = endingEquity / initialCapital - 1`.
- `CAGR = (endingEquity / initialCapital)^(365 / elapsedCalendarDays) - 1`;
  unavailable for non-positive equity or elapsed days below 1.
- closed-tranche `tradeReturn = netPnL / acquisitionCost`.
- `winRate = count(netPnL > 0) / closedTrancheCount`; break-even is not a win.
- `profitFactor = grossPositivePnL / abs(grossNegativePnL)`; unavailable with no
  closed trades or no losing trades, and zero when losses exist but wins do not.
- `averageTradeReturn` is the arithmetic mean of closed-tranche returns.
- daily return is `equity[d] / equity[d-1] - 1`; Sharpe is mean divided by
  sample standard deviation times `sqrt(252)`, risk-free rate zero; unavailable
  with fewer than two returns or zero deviation.
- drawdown is `equity / runningPeak - 1`; maximum drawdown is the minimum value.

Money stays decimal. Ratios are calculated at decimal precision 34 and exposed
at scale 8 using HALF_EVEN. Versioned deterministic square-root/power routines
must be isolated and tested against independent vectors; display rounding never
feeds another calculation.

**Rationale**: The formulas define annualization, denominators, edge cases, and
closed-tranche semantics that the SRS otherwise leaves ambiguous.

**Alternatives considered**: Trade-based Sharpe ignores idle periods; population
deviation overstates Sharpe; representing infinity or NaN breaks typed APIs.

**Risks/validation**: Golden vectors cover no trades, all wins, all losses,
break-even, flat equity, less than one year, leap days, and multi-year periods.

## R-009: Persistence and result immutability

**Decision**: Store run, assumptions/evidence, tranches, equity points, metric
values, and rejected-entry events in PostgreSQL. Children are written to staging
collections inside one terminal transaction, protected by run-scoped unique
keys. Only that transaction changes `RUNNING` to `COMPLETED`; `WITHHELD` and
`FAILED` have no published partial result.

**Rationale**: Results must be revisitable and audit-friendly. Atomic terminal
publication plus uniqueness makes retries safe.

**Alternatives considered**: JSON-only result limits queries and constraints;
incremental visible writes expose partial truth; cache storage is not a record.

**Risks/validation**: Persistence tests force rollback immediately before
terminal update, retry the claim, and assert one immutable result set.

## R-010: Lifecycle, idempotency, and recovery

**Decision**: Create accepts an optional owner-scoped idempotency key. A claim
uses an atomic state/version guard. A heartbeat updates progress; a recovery
scan may requeue stale `RUNNING` jobs up to two total attempts, then marks them
`FAILED`. Terminal states never transition. Cancellation is deferred.

**Rationale**: At-least-once local dispatch and process restarts require a
durable source of truth and bounded retry.

**Alternatives considered**: Infinite retry never finishes; no retry loses
transiently interrupted work; cancellation adds race semantics without being
required by the accepted spec.

**Risks/validation**: Duplicate keys, simultaneous claims, stale heartbeat,
second failure, and terminal replay are covered under concurrency.

## R-011: API shape and pagination

**Decision**: Public v1 endpoints create, list runs, read status/summary, and
page trades/equity/events independently. Default page size 100, maximum 500;
sort order is stable. Create returns 202 with run identity and status location.

**Rationale**: Ten years of daily equity should not be embedded in every status
response. Separate pages keep polling bounded.

**Alternatives considered**: One giant response violates bounded access;
streaming is unnecessary for daily single-symbol runs.

**Risks/validation**: Contract tests cover bounds, stable ordering, terminal
availability, malformed enums/decimals, and indistinguishable ownership errors.

## R-012: Data limitations and no-trade result

**Decision**: Missing/coherence defects that prevent faithful evaluation make
the run `WITHHELD`. A valid coherent period with zero signals is a `COMPLETED`
run with zero trades, a flat equity curve, total return zero, drawdown zero, and
trade-dependent metrics unavailable. Every result discloses survivorship and
available suspension/delisting limitations.

**Rationale**: “Strategy did nothing” is valid evidence; “data could not prove
what happened” is not.

**Alternatives considered**: Treating no trades as failure hides valid strategy
behavior; skipping defective dates silently introduces selection bias.

**Risks/validation**: Paired no-signal and missing-data fixtures must terminate
differently with stable reasons.

## R-013: Price basis and corporate-action discontinuities

**Decision**: Use one uniform accepted series for strategy evaluation, fills,
and marks. `RAW` is a quoted-price simulation. VCI `PROVIDER_ADJUSTED` is an
explicit normalized-price simulation and emits
`PROVIDER_ADJUSTED_EXECUTION_BASIS`; it is not described as a literal historical
cash ledger. Mixed bases and dual `ADJUSTED` rows are withheld. If an available
factor changes between signal and entry, or during an open tranche, withhold as
`CORPORATE_ACTION_UNSUPPORTED`.

**Rationale**: Canonical rows expose a factor but not a complete typed event
ledger for split ratios, cash dividends, rights, ex-dates, and entitlements.
VCI canonical bars deliberately contain provider-adjusted OHLC with no separate
raw close/factor pair (ADR-0013). Transforming shares or cash from an opaque or
missing factor would guess provider meaning.

**Alternatives considered**: Mixing adjusted signals with raw fills creates
incomparable levels; treating provider-adjusted values as literal quoted fills
hides a material limitation; rejecting all VCI history would make the feature
unusable despite a coherent normalized series.

**Risks/validation**: Allow a factor change while flat, but withhold changes
between signal/entry and during an open tranche. Replace this policy only after
a researched corporate-action event contract exists.

## R-014: Historical lot convention

**Decision**: `market-lot-v1` applies the current 100-share standard lot across
the full simulation and always emits `CURRENT_MARKET_LOT_APPLIED_HISTORICALLY`.

**Rationale**: The repository has a current venue rule but no accepted,
point-in-time lot-rule ledger. Guessing historical effective dates would violate
the data contract.

**Alternatives considered**: Unversioned date tables are unauditable; ignoring
lots diverges from `position-sizing-v1`; withholding every earlier run provides
little value when the counterfactual is explicit.

**Risks/validation**: Results must not call this historical lot evidence. A
future dated rule ledger requires a new contract version.
