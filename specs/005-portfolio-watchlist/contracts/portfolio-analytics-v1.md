# Calculation Contract: `portfolio-analytics-v1`

**Feature**: `005-portfolio-watchlist`
**Rule version**: `portfolio-analytics-v1`
**Owner**: `finvera-be` / `portfolio` module, domain layer
(`portfolio.domain.analytics`)
**Status**: Normative. This file is the single authority for FIFO
cost-basis matching, P/L, allocation, return, drawdown, concentration, and
risk-exposure formulas. If code and this file disagree, this file is
correct and the code is defective (`AGENTS.md`).

## Scope and authority

This contract does not redefine any formula already normative in
[`technical-indicators-v1.md`](../../002-stock-detail-analysis/contracts/technical-indicators-v1.md)
or [`strategy-signal-v1.md`](../../004-strategy-signal-risk/contracts/strategy-signal-v1.md)
— it only says which already-published value each analytics figure reads,
and defines the new formulas this feature itself introduces (cost basis,
P/L, return, drawdown, concentration, risk rollup). A change to any formula
below creates `portfolio-analytics-v2`.

Idempotent transaction recording (research R-011) is an API/persistence
concern, not a calculation formula, and is normative in
[`data-model.md`](../data-model.md) (`portfolio_transaction.idempotency_key`)
and
[`portfolio-watchlist.openapi.yaml`](portfolio-watchlist.openapi.yaml)
(the `Idempotency-Key` header) — this file only notes that the FIFO/cash
replay in U-1 always operates over a ledger that is already free of
duplicate submissions by the time it runs.

## Universal rules

**U-1 Inputs.** Every figure in this contract is computed from a
portfolio's own `portfolio_transaction` rows plus Feature 001's market
calendar/index data, Feature 002's accepted daily bars and sector
classification, and Feature 004's current `strategy_signal` rows. Nothing
here recomputes an indicator, a signal, or a sector classification
independently (FR-018/reuse-discipline Assumptions).

**U-2 Arithmetic.** All arithmetic is decimal (`BigDecimal`), never binary
floating point, at scale-6 precision for monetary/price values and scale-4
for percentages/ratios, consistent with `technical-indicators-v1` U-2/U-3.

**U-3 Replay order.** Every derivation replays a portfolio's non-`VOID`ed
transactions ordered by `executed_at`, then `sequence_no` (data-model.md,
research R-003) — never `entry_at` (recording time) and never database
insertion order alone.

**U-4 As-of basis.** "Current price" for any open position is the
instrument's latest accepted daily bar close (Feature 002), the same basis
`strategy-signal-v1` U-4 uses. A historical point in a performance-history
series uses that trading date's own accepted close, never the latest price
applied retroactively.

**U-5 Voided transactions.** A `VOID` transaction and the transaction it
voids are both excluded from every replay used to compute a position,
total, or analytics figure (research R-003); both remain individually
queryable via the raw ledger endpoint (FR-005).

**U-6 Void validity.** A `VOID` referencing a `BUY` whose resulting lot has
already been partially or fully consumed by a later, non-voided `SELL` at
the time the `VOID` is submitted MUST be rejected with `LOT_ALREADY_CONSUMED`
— voiding it would make the already-recorded `SELL`'s realized P/L
irreproducible from the remaining ledger. The owner must first void the
dependent `SELL`(s), then the `BUY`, preserving replay consistency at every
point in the ledger's history.

**U-7 Reproducibility.** Given an identical set of non-voided transactions,
an unchanged rule version, and unchanged accepted prices/sectors/signals,
every position, total, and analytics figure is byte-identical across
repeated computation (FR-016).

## FIFO cost basis and P/L

For one instrument within one portfolio, replay non-voided `BUY`/`SELL`
transactions in `U-3` order, maintaining an ordered queue of open lots
`(quantity_remaining, unit_cost = price + fee/quantity)`:

- A `BUY` appends a new lot `(quantity, price + fee/quantity)`.
- A `SELL` of quantity `q` consumes lots from the front of the queue
  (oldest first). For each consumed `(qty_i, cost_i)`:
  `realizedPL += qty_i * (sellNetPrice − cost_i)`, where
  `sellNetPrice = price − fee/quantity` (the sale's own fee is spread
  across the units it sold). A `SELL` whose `q` exceeds the queue's total
  remaining quantity is rejected before any lot is consumed (FR-003,
  `INSUFFICIENT_POSITION`).
- After replay, `Position.quantity = Σ quantity_remaining` across
  surviving lots; `Position.averageCostBasis = Σ(qty_i * cost_i) /
  Σ qty_i` across surviving lots (a quantity-weighted average of the
  still-open lots' own unit costs — not a running average recomputed on
  every trade, which would let a later sale's realized P/L depend on a
  since-changed average rather than each lot's own original cost).
- `Position.unrealizedPL = Position.quantity * (currentPrice −
  Position.averageCostBasis)`.
- `Position.realizedPL` is the running sum above across every `SELL` for
  that instrument in that portfolio, including fully-closed positions
  (an instrument with `quantity = 0` after replay still has a nonzero
  `realizedPL` if it was ever sold at a gain/loss).

## Cash balance

```text
CashBalance(asOf) =
    Σ DEPOSIT.amount (executed_at <= asOf, not voided)
  − Σ WITHDRAW.amount (executed_at <= asOf, not voided)
  − Σ (BUY.quantity * BUY.price + BUY.fee) (executed_at <= asOf, not voided)
  + Σ (SELL.quantity * SELL.price − SELL.fee) (executed_at <= asOf, not voided)
```

A `WITHDRAW` whose `amount` exceeds `CashBalance` at its own `executed_at`
(computed by replaying every earlier transaction first) is rejected before
being recorded (FR-004, `INSUFFICIENT_CASH_BALANCE`). A `SELL` whose
quantity exceeds the position's held quantity as of its own `executed_at`
is rejected the same way (FR-003, `INSUFFICIENT_POSITION`) — both checks
replay the ledger **as of the new transaction's own chronological
position**, not merely "as of now," so a backdated entry is validated
against the state that actually existed at that historical moment.

## Portfolio totals

```text
TotalPositionsValue(asOf) = Σ Position.quantity(asOf) * closePrice(symbol, asOf)   [research R-006]
TotalValue(asOf) = TotalPositionsValue(asOf) + CashBalance(asOf)
Allocation(symbol, asOf) = (Position.quantity(asOf) * closePrice(symbol, asOf)) / TotalValue(asOf)
```

`Allocation` is `UNAVAILABLE` (not `0`) when `TotalValue(asOf) <= 0`.

## Return (research R-005)

```text
NetContributedCapital(asOf) =
    Σ DEPOSIT.amount (executed_at <= asOf, not voided)
  − Σ WITHDRAW.amount (executed_at <= asOf, not voided)

ReturnSinceInception =
    (TotalValue(now) − NetContributedCapital(now)) / NetContributedCapital(now)

ReturnOverPeriod(T0, T1) =
    (TotalValue(T1) − TotalValue(T0) − NetContributed(T0, T1])
    / (TotalValue(T0) + NetContributed(T0, T1])
```

Both are `UNAVAILABLE` (never `0`) when their denominator is `<= 0`
(FR-011). Every returned return figure MUST be accompanied by the
`NET_CONTRIBUTED_CAPITAL_METHOD` disclosure code so the client can render
the required distortion-under-large-cashflow disclosure (spec.md
Assumptions).

## Drawdown and performance history (research R-006)

For a bounded period `[T0, T1]` (default: portfolio inception to today,
capped at `finvera.portfolio.max-performance-history-span-days`,
default 730 days, never unbounded):

**F6 — `T0` clamping.** If the caller's requested period start predates
the portfolio's actual first non-voided transaction, `T0` is silently
replaced with that first transaction's `executedAt` date — never rejected,
and never computed as though history existed before it (spec.md edge
case). The response's `periodFrom` always states the `T0` actually used;
`periodClampedToInception` is `true` whenever the resolved `T0` differs
from the caller's requested `from`.

1. Compute `TotalValue` at `T0` (as resolved above) by full replay (`U-3`).
2. Walk Feature 001's trading calendar dates in `(T0, T1]` in order; for
   each date, apply that date's transactions (if any) to the running
   position/cash state, then price open positions at that date's accepted
   close (bulk-fetched once per distinct symbol for the whole period —
   research R-006), producing one `(date, TotalValue)` point.
3. `MaxDrawdown = max` over all `t1 <= t2` in the series of
   `(peakValue(t1) − TotalValue(t2)) / peakValue(t1)`, where
   `peakValue(t1) = max(TotalValue(t))` for `t` in `[T0, t1]`.

A date with no accepted close for a held symbol (a data gap) carries that
date's point forward from the last known accepted close for that symbol
only, and the point is flagged `PARTIAL` with the affected symbol named —
never silently dropped or zero-filled.

## Concentration (research R-007)

```text
StockConcentration(symbol) = Position.value(symbol) / TotalValue
SectorConcentration(sector) = Σ Position.value(symbol) for symbol in sector / TotalValue
```

Reuses Feature 001/002's existing sector classification unmodified.

## Portfolio risk exposure (research R-008)

For each open position whose symbol has one or more Feature 004 current
`strategy_signal` rows:

```text
positionRiskContribution(symbol) = max(riskScore) across that symbol's current signals
coveredPositionsValue = Σ Position.value(symbol) for symbols with >= 1 current signal
riskExposureScore = Σ [Position.value(symbol) * positionRiskContribution(symbol)]
                     for covered symbols
                   / coveredPositionsValue
coverageRatio = coveredPositionsValue / TotalPositionsValue
```

`riskExposureScore`/`riskExposureLevel` are `UNAVAILABLE` (not `0` or
`LOW`) when `coveredPositionsValue = 0` (no held position currently has a
signal). `riskExposureLevel` bands reuse `strategy-signal-v1`'s own
`LOW [0,33] / MEDIUM (33,66] / HIGH (66,100]`.

## Benchmark comparison

```text
BenchmarkReturn(T0, T1) = (indexClose(T1) − indexClose(T0)) / indexClose(T0)
```

Using Feature 001's persisted VN-Index snapshot closes for the same `[T0,
T1]` the portfolio's `ReturnOverPeriod` used. `BenchmarkReturn` is a simple
price return (no contribution adjustment, since an index has none),
labeled distinctly from the portfolio's own contribution-adjusted return
so the two are never implied to be computed identically.

## Required test-vector table

Implementation and fixtures MUST cover at least:

| Case | Expected outcome |
|---|---|
| Simple `DEPOSIT` + `BUY` + partial `SELL` at a different price | Correct remaining quantity, average cost basis, unrealized P/L, realized P/L on the closed lot |
| `SELL` exceeding held quantity | Rejected, `INSUFFICIENT_POSITION`, zero ledger/position change |
| `WITHDRAW` exceeding cash balance | Rejected, `INSUFFICIENT_CASH_BALANCE`, zero change |
| Backdated `BUY` inserted between two already-recorded later transactions for the same symbol | FIFO lots and all downstream P/L recompute in `executed_at` order, not insertion order |
| `VOID` of a `BUY` with no dependent `SELL` | The `BUY` excluded from replay; position/cash reflect its absence; both rows remain individually queryable |
| `VOID` of a `BUY` whose lot was already partially sold | Rejected, `LOT_ALREADY_CONSUMED` |
| `VOID` of an already-voided transaction, or a `VOID` of a `VOID` | Rejected |
| Two transactions with identical `executed_at` for the same symbol | Deterministic `sequence_no` tie-break order applied consistently |
| Return computed with `NetContributedCapital <= 0` | `UNAVAILABLE`, never `0` |
| Performance-history period spanning a data gap for one held symbol | That point `PARTIAL`, forward-filled from last known close, symbol named |
| Risk exposure with 2 covered and 1 uncovered position | Score computed only from covered positions; `coverageRatio` correctly excludes the uncovered one |
| Identical ledger, prices, sectors, and signals evaluated twice | Identical position, totals, and analytics figures (U-7) |
| `recordTransaction`/`voidTransaction` replayed with the same `Idempotency-Key` on the same portfolio | Second call rejected, `DUPLICATE_SUBMISSION`, zero additional ledger/position/balance change; a second call with a **different** key but identical symbol/quantity/price/`executedAt` succeeds as a genuinely separate transaction (research R-011) |
| Analytics requested with `from` before the portfolio's first transaction | `periodFrom` clamped to the actual inception date, `periodClampedToInception = true`, no fabricated pre-inception data point (F6) |
| Analytics requested with `from` on or after the portfolio's first transaction | `periodFrom` equals the requested `from`, `periodClampedToInception = false` |
