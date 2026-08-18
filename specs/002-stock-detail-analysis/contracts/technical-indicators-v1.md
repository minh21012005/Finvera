# Calculation Contract: `technical-indicators-v1`

**Feature**: `002-stock-detail-analysis`
**Rule version**: `technical-indicators-v1`
**Owner**: `finvera-be` / `stock` module, domain layer
**Status**: Normative. This file is the single authority for these formulas.

## Scope and authority

This contract defines every technical indicator Feature 002 publishes. The
implementation, the fixtures, and the API descriptions all cite this file. If
code and this file disagree, this file is correct and the code is defective —
`AGENTS.md` forbids editing a specification to make an implementation conform.

A change to any formula, parameter, minimum bar count, precision, or rounding
mode below creates `technical-indicators-v2`. Existing `v1` results are never
recomputed under a new version; both versions coexist and each result records the
version that produced it.

## Universal rules

**U-1 Input series.** All indicators consume the accepted daily bar series for
one instrument, ordered ascending by `trading_date`, containing only completed
accepted sessions. Non-trading days are absent from the series; the series is
never padded with synthetic bars, carried-forward values, or zeros.

**U-2 Price basis.** All price-derived indicators use `adjusted_close` when the
series `adjustment_status` is `ADJUSTED`, and `raw_close` when it is `RAW`. A
single calculation never mixes the two (DATA-008, research R-004). The
`adjustment_status` of the source series is recorded on every result.

**U-3 Arithmetic.** All arithmetic is decimal, never binary floating point.
Intermediate values are carried at a scale of **12** decimal places using
`HALF_UP` rounding at each division. No intermediate value is rounded to display
precision.

**U-4 Rounding.** Rounding to display precision happens exactly once, at the
presentation boundary, using `HALF_UP`. The stored value is the unrounded
value at scale 12. Comparisons, band decisions, and direction derivations use the
stored unrounded value (DATA-004).

**U-5 Evaluation window.** Recursively smoothed indicators (RSI, MACD, ATR) are
computed over the fixed window of the last `W = 250` completed accepted sessions
ending at the as-of trading date, seeded at the first bar of that window. If
fewer than 250 bars exist, the indicator is `UNAVAILABLE`. Non-recursive
indicators use only their own period. Rationale is recorded in research R-003.

**U-6 Insufficiency.** When the required bar count is not met, the indicator
result is persisted with a null value and
`quality_reason = 'INSUFFICIENT_HISTORY'`, carrying `requiredBars` and
`availableBars`. It is never partially computed, extrapolated, or defaulted to
zero (FR-006, DATA-007). Insufficiency of one indicator never suppresses another.

**U-7 Notation.** `C[i]` is the close of bar `i`, `H[i]` the high, `L[i]` the
low, `V[i]` the volume. `i = n` is the as-of bar, `i = n-1` the prior bar. All
sums are inclusive of both endpoints.

## Indicator definitions

### MA20, MA50, MA200 — simple moving average

```text
MA(p)[n] = ( sum of C[i] for i = n-p+1 .. n ) / p
```

| Property | MA20 | MA50 | MA200 |
|---|---|---|---|
| `indicator_code` | `MA20` | `MA50` | `MA200` |
| Period `p` | 20 | 50 | 200 |
| Minimum bars | 20 | 50 | 200 |
| Components | `VALUE` | `VALUE` | `VALUE` |
| Unit | VND | VND | VND |
| Display precision | 2 | 2 | 2 |

### RSI(14) — relative strength index, Wilder smoothing

Over the fixed 250-bar window, let `D[i] = C[i] - C[i-1]` for
`i = w+1 .. n`, where `w` is the first bar of the window. Define
`gain[i] = max(D[i], 0)` and `loss[i] = max(-D[i], 0)`.

Seed at the 14th change in the window:

```text
avgGain[w+14] = ( sum of gain[i] for i = w+1 .. w+14 ) / 14
avgLoss[w+14] = ( sum of loss[i] for i = w+1 .. w+14 ) / 14
```

Then for every `i` from `w+15` to `n`:

```text
avgGain[i] = ( avgGain[i-1] * 13 + gain[i] ) / 14
avgLoss[i] = ( avgLoss[i-1] * 13 + loss[i] ) / 14
```

Final value:

```text
if avgLoss[n] = 0 and avgGain[n] = 0  ->  RSI = 50
if avgLoss[n] = 0 and avgGain[n] > 0  ->  RSI = 100
otherwise  RS = avgGain[n] / avgLoss[n]
           RSI = 100 - ( 100 / ( 1 + RS ) )
```

| Property | Value |
|---|---|
| `indicator_code` | `RSI14` |
| Period | 14 |
| Minimum bars | 250 (fixed window, rule U-5) |
| Components | `VALUE` |
| Range | 0 to 100 inclusive |
| Unit | index points, dimensionless |
| Display precision | 2 |

The zero-loss branches are stated explicitly because the naive `RS` division is
undefined there. `avgLoss = 0` with `avgGain = 0` means a completely flat window,
which is neutral, not overbought.

### MACD(12, 26, 9) — moving average convergence divergence

Exponential moving average with smoothing factor `k(p) = 2 / (p + 1)`, seeded
with the simple average of the first `p` values inside the fixed window:

```text
EMA(p)[w+p-1] = ( sum of C[i] for i = w .. w+p-1 ) / p
EMA(p)[i]     = C[i] * k(p) + EMA(p)[i-1] * ( 1 - k(p) )      for i > w+p-1
```

```text
macdLine[i]  = EMA(12)[i] - EMA(26)[i]                        for i >= w+25
signal[w+33] = ( sum of macdLine[i] for i = w+25 .. w+33 ) / 9
signal[i]    = macdLine[i] * k(9) + signal[i-1] * ( 1 - k(9) ) for i > w+33
histogram[i] = macdLine[i] - signal[i]
```

Published value is the triple at `i = n`.

| Property | Value |
|---|---|
| `indicator_code` | `MACD` |
| Parameters | fast 12, slow 26, signal 9 |
| Minimum bars | 250 (fixed window, rule U-5) |
| Components | `MACD_LINE`, `SIGNAL`, `HISTOGRAM` |
| Unit | VND |
| Display precision | 2 |

All three components are published together or the indicator is unavailable.
A partially available MACD is not a valid result.

### Bollinger Bands(20, 2) — population standard deviation

```text
middle[n] = MA20[n]
variance  = ( sum of ( C[i] - middle[n] )^2 for i = n-19 .. n ) / 20
sigma     = square root of variance
upper[n]  = middle[n] + 2 * sigma
lower[n]  = middle[n] - 2 * sigma
bandwidth[n] = ( upper[n] - lower[n] ) / middle[n] * 100
```

The divisor is `20`, the population form, matching the standard definition. The
square root is evaluated to scale 12 with `HALF_UP`.

| Property | Value |
|---|---|
| `indicator_code` | `BBANDS` |
| Parameters | period 20, multiplier 2, population standard deviation |
| Minimum bars | 20 |
| Components | `UPPER`, `MIDDLE`, `LOWER`, `BANDWIDTH` |
| Unit | VND for bands; percent for bandwidth |
| Display precision | 2 |

`bandwidth` is `NOT_APPLICABLE` when `middle[n] = 0`; the three bands remain
published. A zero middle band is only reachable with a degenerate series and is
handled rather than allowed to divide by zero.

### ATR(14) — average true range, Wilder smoothing

True range for every bar after the first in the window:

```text
TR[i] = max( H[i] - L[i], abs( H[i] - C[i-1] ), abs( L[i] - C[i-1] ) )
```

Seed and smooth exactly as RSI does:

```text
ATR[w+14] = ( sum of TR[i] for i = w+1 .. w+14 ) / 14
ATR[i]    = ( ATR[i-1] * 13 + TR[i] ) / 14                     for i > w+14
```

| Property | Value |
|---|---|
| `indicator_code` | `ATR14` |
| Period | 14 |
| Minimum bars | 250 (fixed window, rule U-5) |
| Components | `VALUE`, `PERCENT_OF_CLOSE` |
| Unit | VND for value; percent for `PERCENT_OF_CLOSE` |
| Display precision | 2 |

```text
PERCENT_OF_CLOSE = ATR[n] / C[n] * 100
```

`PERCENT_OF_CLOSE` is `NOT_APPLICABLE` when `C[n] = 0`.

### Average volume(20) and relative volume

```text
averageVolume20[n] = ( sum of V[i] for i = n-19 .. n ) / 20
priorAverage20[n]  = ( sum of V[i] for i = n-20 .. n-1 ) / 20
relativeVolume[n]  = V[n] / priorAverage20[n]
```

`averageVolume20` deliberately **includes** the as-of session, because it
describes recent activity. `relativeVolume` deliberately **excludes** it, because
comparing today's volume against an average that already contains today's volume
damps exactly the signal the reader is looking for. Both windows are stated
explicitly so the difference is intentional and testable, not an accident.

| Property | `AVG_VOLUME20` | `RELATIVE_VOLUME` |
|---|---|---|
| Minimum bars | 20 | 21 |
| Components | `VALUE` | `VALUE` |
| Unit | shares | ratio, dimensionless |
| Display precision | 0 | 2 |

`relativeVolume` is `NOT_APPLICABLE` when `priorAverage20[n] = 0`, which occurs
for a symbol suspended across the whole prior window.

## Result contract

Every persisted result carries:

| Field | Meaning |
|---|---|
| `indicator_code` | One of the codes above. |
| `rule_version` | `technical-indicators-v1`. |
| `as_of_trading_date` | The trading date of bar `n`. |
| `window_start_date`, `window_end_date` | Inclusive bounds of the bars actually consumed. |
| `input_bar_count` | Number of bars consumed. |
| `input_set_hash` | SHA-256 over the canonical ordered `(trading_date, value)` list of consumed bars. |
| `adjustment_status` | `ADJUSTED` or `RAW`, from the source series. |
| `data_status` | `CURRENT`, `DELAYED`, `STALE`, `PARTIAL`, or `UNAVAILABLE`. |
| `quality_reason` | Required when a value is absent. |

`input_set_hash` is what makes SC-003 verifiable: a replay recomputes the value
from the bars identified by the hash and asserts exact equality with the stored
decimal.

## Reason codes

| Code | Meaning |
|---|---|
| `INSUFFICIENT_HISTORY` | Fewer accepted bars than the indicator's minimum. |
| `ADJUSTMENT_BASIS_UNAVAILABLE` | Series served raw because the adjustment chain is incomplete. |
| `NOT_APPLICABLE` | The component is mathematically undefined for this input, such as a zero denominator. |
| `SOURCE_CONFLICT` | A material cross-source disagreement affects a consumed bar (DATA-010). |
| `PROVIDER_AUTH_REQUIRED` | The live source needs owner token renewal; accepted history remains valid. |
| `CALCULATION_FAILED` | The calculation raised an internal error; no value is published. |

`SOURCE_CONFLICT` on any consumed bar withholds the affected indicator entirely
rather than computing around the disputed bar.

## Required test vectors

Implementation is not complete until these pass.

| Test | Assertion |
|---|---|
| Golden vectors | Each indicator matches a hand-computed value on a fixed 250-bar series, to scale 12. |
| Flat series | RSI = 50, MACD histogram = 0, ATR > 0 only if intraday range is non-zero. |
| Monotonic rising series | RSI = 100, all MAs strictly ascending. |
| Bar-count boundaries | 19/20, 49/50, 199/200, 249/250 bars produce unavailable/available exactly at the threshold. |
| Split in window | Raw and adjusted runs differ; neither run mixes bases; `adjustment_status` matches the basis used. |
| Zero denominators | Bandwidth, ATR percent, and relative volume return `NOT_APPLICABLE`, never an exception and never zero. |
| Replay determinism | Recomputation from `input_set_hash` yields exactly the stored value. |
| Independence | With MA200 unavailable, MA20 and MA50 still publish. |
