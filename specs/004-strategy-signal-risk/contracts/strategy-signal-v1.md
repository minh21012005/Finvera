# Calculation Contract: `strategy-signal-v1`

**Feature**: `004-strategy-signal-risk`
**Rule version**: `strategy-signal-v1`
**Owner**: `finvera-be` / `stock` module, domain layer
(`stock.domain.strategy`)
**Status**: Normative. This file is the single authority for every
strategy's entry condition, every signal level formula, and the risk
score. If code and this file disagree, this file is correct and the code
is defective (`AGENTS.md`).

## Scope and authority

This contract does not redefine any formula already normative in
[`technical-indicators-v1.md`](../../002-stock-detail-analysis/contracts/technical-indicators-v1.md)
or [`screener-v1.md`](../../003-stock-screener/contracts/screener-v1.md) —
it only says which already-published value each strategy/level/factor
reads. A change to any entry condition, level formula, factor formula, or
threshold below creates `strategy-signal-v2`.

## Universal rules

**U-1 Inputs.** Every strategy, level, and risk factor reads only the
instrument's current accepted `technical_indicator_result` (and, for the
three crossing strategies, the immediately preceding trading date's
accepted result for the same indicator), the current accepted daily bar,
`screener-v1`'s Breakout/Trend derivations, and Feature 001's current
regime assessment. Nothing here recomputes an indicator, a Breakout
condition, or a Trend direction independently (FR-012).

**U-2 Arithmetic.** All arithmetic is decimal (`BigDecimal`), never binary
floating point, at the same scale-12 precision `technical-indicators-v1`
U-3 already establishes for its own component values.

**U-3 Direction.** Every strategy in this version produces `LONG` signals
only (spec.md Assumptions). A strategy whose classic definition is
expressed bearishly (none of the eight are) would produce no signal rather
than a `SHORT` one.

**U-4 As-of basis.** Every strategy is evaluated against the instrument's
**latest accepted completed session** — the same "current price" basis
Feature 002's overview uses (its latest accepted daily bar), never an
intraday or provisional value.

**U-5 Insufficiency.** When a strategy's required indicator(s) lack the
bar count `technical-indicators-v1` itself requires (20/50/200 for simple
MAs, the fixed 250-session window for recursively-smoothed RSI/MACD/ATR
per its U-5, or 21 sessions for `screener-v1`'s Breakout), that strategy is
`INSUFFICIENT_HISTORY` for that instrument — never partially evaluated.

**U-6 Reproducibility.** Given an identical rule version and unchanged
accepted inputs, every triggered strategy reproduces an identical signal
(direction, levels, risk score/level) — S-5-equivalent to `screener-v1`.

## Strategy entry conditions

Notation follows `technical-indicators-v1` U-7 (`C`, `H`, `L`, `V` at index
`n` = the as-of bar, `n-1` = the immediately preceding accepted bar).
`RSI`, `MA20`/`MA50`/`MA200`, `MACD.HISTOGRAM`, `BBANDS.LOWER`,
`RELATIVE_VOLUME` are `technical-indicators-v1` component values at the
stated index. `Trend[n]` and `Breakout[n]` are `screener-v1`'s derivations
(`contracts/screener-v1.md`) at index `n`.

| Strategy | `strategy_code` | Entry condition | Min. bars |
|---|---|---|---|
| Trend Following | `TREND_FOLLOWING` | `Trend[n] = UPTREND` and `C[n] > MA20[n]` | 200 |
| Momentum | `MOMENTUM` | `RSI14[n] >= 60` and `MACD.HISTOGRAM[n] > 0` | 250 |
| Breakout | `BREAKOUT` | `Breakout[n] = BREAKOUT_UP` and `RELATIVE_VOLUME[n] >= 1.5` | 21 |
| Pullback | `PULLBACK` | `MA20[n] > MA50[n] > MA200[n]` and `40 <= RSI14[n] <= 55` and `C[n] > MA50[n]` | 250 |
| Mean Reversion | `MEAN_REVERSION` | `RSI14[n] <= 30` and `C[n] < BBANDS.LOWER[n]` | 250 |
| MA Crossover | `MA_CROSSOVER` | `MA20[n-1] <= MA50[n-1]` and `MA20[n] > MA50[n]` | 50 (+ prior day) |
| MACD-based | `MACD_BASED` | `MACD.HISTOGRAM[n-1] <= 0` and `MACD.HISTOGRAM[n] > 0` | 250 (+ prior day) |
| RSI-based | `RSI_BASED` | `RSI14[n-1] <= 30` and `RSI14[n] > 30` | 250 (+ prior day) |

A strategy whose minimum bar count is unmet is `INSUFFICIENT_HISTORY`
(FR-005). A strategy whose bars are sufficient but whose condition is
false produces no signal (FR-004) — this is not an error and not
persisted (`data-model.md`).

Every threshold above is a **strict** inequality except where explicitly
written as `<=`/`>=`; a value exactly on a non-strict boundary (e.g.
`RSI14 = 60.000000` for Momentum) does **not** satisfy the condition.

## Signal levels

```text
entryLow   = C[n] - 0.25 * ATR14[n]
entryHigh  = C[n] + 0.25 * ATR14[n]
stopLoss   = C[n] - 2 * ATR14[n]
target1    = C[n] + 4 * ATR14[n]     ( = entry + 2 * (entry - stopLoss) )
target2    = C[n] + 6 * ATR14[n]     ( = entry + 3 * (entry - stopLoss) )
riskReward = 2.0000                  ( target1's R-multiple, constant by construction — research R-003 )
```

`ATR14[n]` is read from the current accepted `technical-indicators-v1`
result (never recomputed). If `ATR14[n] <= 0` (a degenerate flat-price
fixture; not reachable from any real accepted market data), the signal is
withheld with `INVALID_LEVELS` rather than dividing or producing a
non-positive stop distance.

## Risk factors and score

Each factor is scored `0`-`100` on its own scale, `100` = highest risk
contribution:

| `factor_code` | Input | Scoring |
|---|---|---|
| `VOLATILITY` | `ATR14` `PERCENT_OF_CLOSE` component | Linear `0` at `<=2%`, `100` at `>=10%`, clamped |
| `ATR` | `ATR14` value relative to the instrument's own trailing-250-session average `ATR14` (a ratio, not an absolute VND figure, so it is comparable across price levels) | Linear `0` at ratio `<=0.75`, `100` at ratio `>=1.5`, clamped |
| `DRAWDOWN` | Percent decline from the highest accepted close in the trailing 250 sessions to `C[n]` | Linear `0` at `0%` drawdown, `100` at `>=30%` drawdown, clamped |
| `LIQUIDITY` | `RELATIVE_VOLUME[n]` (inverse — thin liquidity scores higher risk) | Linear `100` at `<=0.5`, `0` at `>=1.5`, clamped |
| `STOP_DISTANCE` | `(C[n] - stopLoss) / C[n]` (the level formula's own stop, expressed as a percent of price) | Linear `0` at `<=3%`, `100` at `>=15%`, clamped |
| `MARKET_REGIME` | Feature 001's current regime assessment score, inverted (a bearish/high-volatility regime scores higher risk here) | Direct: `100 - regimeScore` when the regime score is itself `0`(most bearish)-`100`(most bullish) |

`overallScore` = the mean of every **available** factor's score
(equal-weighted, research R-004). If fewer than four of the six factors
are `DEFINED`, `overallScore`/`riskLevel` are both `null` and the signal's
`reason_codes` include `INSUFFICIENT_RISK_FACTORS` — the signal itself
(direction/levels) is still published; only the risk score/level is
withheld, per FR-007's distinction between "a signal exists" and "its risk
is knowable right now."

`riskLevel` bands: `LOW` = `overallScore` in `[0, 33]`; `MEDIUM` = `(33,
66]`; `HIGH` = `(66, 100]`.

`signalStrength` (research R-005): `STRONG` when `riskLevel = LOW`,
`MODERATE` when `MEDIUM`, `WEAK` when `HIGH`; `null` when `riskLevel` is
`null`.

## Required test-vector table

Implementation and fixtures MUST cover at least:

| Case | Expected outcome |
|---|---|
| Each of the eight strategies, one triggering fixture and one non-triggering fixture | Exactly the triggering fixture produces a signal for that strategy |
| Each strategy at its own minimum-bar boundary (one bar short, exact minimum) | One bar short → `INSUFFICIENT_HISTORY`; exact minimum → evaluated |
| Each of the three crossing strategies: condition true both today and yesterday | No signal — a stale "still above" state is not a fresh cross |
| Each of the three crossing strategies: condition false yesterday, true today | Signal produced |
| `RSI14` exactly `60.000000` for Momentum | No signal (strict `>=` boundary honored, but exactly-equal case documented and asserted) |
| `ATR14 <= 0` fixture | Signal withheld with `INVALID_LEVELS`, no divide-by-zero |
| Exactly 3 of 6 risk factors available vs. exactly 4 | 3 → risk score/level withheld, signal still shown; 4 → published |
| Cross-source conflict on a bar a strategy depends on | That strategy withheld with `SOURCE_CONFLICT`, others unaffected |
| Identical inputs and rule version evaluated twice | Identical signal, levels, risk score/level (U-6) |
