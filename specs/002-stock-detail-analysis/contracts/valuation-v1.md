# Calculation Contract: `valuation-v1`

**Feature**: `002-stock-detail-analysis`
**Rule version**: `valuation-v1`
**Owner**: `finvera-be` / `stock` module, domain layer
**Status**: Normative. This file is the single authority for this methodology.

## Scope and authority

This contract defines the five published valuation metrics and the single overall
valuation classification required by FR-008, FR-009, and FR-010. The
implementation, the fixtures, and the API descriptions cite this file. If code
and this file disagree, this file is correct.

Any change to a formula, weight, threshold, publishability floor, or
applicability rule creates `valuation-v2`. Existing `v1` assessments are never
recomputed under a new version.

## What this contract does and does not claim

The classification is a **relative expensiveness measurement**: it states where
the stock's current ratios sit against its own accepted history and against its
sector cross-section. It is not a forecast of future price, not an estimate of
intrinsic value, and not a recommendation to buy or sell (FR-015). `UNDER_VALUED`
means "cheap relative to the disclosed comparison basis", nothing more.

Absolute thresholds are deliberately absent. A P/E of 12 is unremarkable for a
Vietnamese bank and expensive for a cyclical industrial; a fixed cut-off would be
wrong in opposite directions for different sectors while looking authoritative.

## Universal rules

**U-1 Arithmetic.** Decimal only, never binary floating point. Intermediates are
carried at scale 12 with `HALF_UP` at each division. Display rounding happens
once at the presentation boundary.

**U-2 Classification uses unrounded values.** The band decision reads the
unrounded score; the displayed integer score is derived from the same value. The
two can never disagree (DATA-004).

**U-3 Distinct absence semantics.** A metric is `DEFINED`, `NOT_APPLICABLE`
(mathematically undefined for this company, such as P/E with negative earnings),
or `MISSING` (an input was not accepted). These three are never collapsed, and
none of them becomes zero (DATA-007).

**U-4 Inputs are accepted facts only.** Every input is an accepted price
observation, accepted daily bar, or accepted `fundamental_summary` revision.
No input is estimated, forecast, or interpolated.

## Inputs

| Input | Source | Notes |
|---|---|---|
| `price` | Latest accepted price observation or completed daily bar | Same value the overview section displays. |
| `sharesOutstanding` | `equity_profile` | Effective-dated; the revision effective at `as_of` is used. |
| `epsTtm` | `fundamental_summary` | Trailing twelve months, research R-006. |
| `epsGrowthPercent` | `fundamental_summary` | Year-over-year TTM EPS growth in percentage points. |
| `equityAttributableToParent` | `fundamental_summary` | Latest accepted reporting period. |
| `ebitdaTtm` | `fundamental_summary` | Trailing twelve months. |
| `totalDebt` | `fundamental_summary` | Short-term plus long-term interest-bearing debt. |
| `cashAndEquivalents` | `fundamental_summary` | Including short-term investments where the catalog defines it. |
| `dividendPerShareTtm` | `fundamental_summary` | Cash dividends per share, trailing twelve months. |

## Metric definitions

```text
marketCap = price * sharesOutstanding
bvps      = equityAttributableToParent / sharesOutstanding
ev        = marketCap + totalDebt - cashAndEquivalents
```

| Metric | `metric_code` | Formula | `NOT_APPLICABLE` when | Precision |
|---|---|---|---|---|
| Price to earnings | `PE` | `price / epsTtm` | `epsTtm <= 0` | 2 |
| Price to book | `PB` | `price / bvps` | `bvps <= 0` | 2 |
| Enterprise value to EBITDA | `EV_EBITDA` | `ev / ebitdaTtm` | `ebitdaTtm <= 0` | 2 |
| PEG | `PEG` | `PE / epsGrowthPercent` | `PE` is not `DEFINED`, or `epsGrowthPercent <= 0` | 2 |
| Dividend yield | `DIVIDEND_YIELD` | `dividendPerShareTtm / price * 100` | `price = 0` | 2 |

A company with negative earnings does not get a negative P/E published. A
negative P/E is arithmetically computable and financially meaningless, and it
would sort as "extremely cheap" in any percentile comparison. It is
`NOT_APPLICABLE` with the reason recorded.

## Scored metrics

Only `PE`, `PB`, `EV_EBITDA`, and `PEG` enter the score. For all four, a higher
value means more expensive, so they share one direction and need no sign
handling.

`DIVIDEND_YIELD` is **displayed but not scored**. Its direction is inverted —
higher yield means cheaper — and mixing an inverted metric into the same weighted
mean is a well-known source of sign defects. It carries independent information
the reader can use directly, and excluding it costs the score nothing that P/E
and P/B do not already capture.

| Metric | Base weight |
|---|---|
| `PE` | 0.40 |
| `PB` | 0.30 |
| `EV_EBITDA` | 0.20 |
| `PEG` | 0.10 |

## Comparison bases

### Basis A — the stock's own accepted ratio history

The ratio history for one metric is the series of that metric evaluated at each
of the last `H = 750` completed accepted sessions ending at `as_of`, using each
session's accepted close and the `fundamental_summary` revision that was
effective on that session.

- Points where the metric is `NOT_APPLICABLE` or `MISSING` are excluded, not
  zero-filled.
- The basis is available for a metric only when at least `H_min = 500` points
  remain after exclusion.

### Basis B — the sector cross-section

The cross-section for one metric is the latest accepted value of that metric for
every **other** instrument sharing the subject's `sector_code` under the same
classification scheme and version.

- The subject is excluded from its own cross-section.
- The basis is available for a metric only when at least `N_min = 8` comparable
  constituents have a `DEFINED` value.

The 8-constituent floor exists because a median over three or four companies is
noise wearing a statistic's clothing. Below the floor, the basis is reported
unavailable rather than computed on thin data (gate G-04 in research R-012).

### Percentile rank

For a value `x` against a set `S`:

```text
percentileRank(x, S) = 100 * ( count(s in S where s < x)
                               + 0.5 * count(s in S where s = x) ) / size(S)
```

The half-weight for ties keeps a series of identical values centred at 50 rather
than at 0 or 100.

## Score and classification

For each available basis, over the metrics that are `DEFINED` **and** have that
basis available:

```text
effectiveWeight(m) = baseWeight(m) / sum of baseWeight over qualifying metrics
basisScore         = sum of ( effectiveWeight(m) * percentileRank(m) )
```

Basis weights, renormalized over available bases:

| Basis | Base weight |
|---|---|
| A, own history | 0.60 |
| B, sector cross-section | 0.40 |

```text
valuationScore = sum of ( effectiveBasisWeight(b) * basisScore(b) )
```

`valuationScore` is in `[0, 100]`, where higher means more expensive.

Bands, evaluated on the **unrounded** score:

| Condition | `classification` |
|---|---|
| `valuationScore < 35.5` | `UNDER_VALUED` |
| `35.5 <= valuationScore < 64.5` | `FAIR_VALUED` |
| `valuationScore >= 64.5` | `OVER_VALUED` |

```text
displayedScore = round HALF_UP of valuationScore to an integer
```

The half-point boundaries are chosen so the displayed integer never contradicts
the band: a score of 35.5 displays as 36 and reads `FAIR_VALUED`; 64.4 displays
as 64 and reads `FAIR_VALUED`; 64.5 displays as 65 and reads `OVER_VALUED`.

## Publishability

A classification is published only when **all** of the following hold. Otherwise
the assessment is persisted with a null classification, a null score, and reason
codes (FR-010).

| Condition | Reason code when it fails |
|---|---|
| Price `data_status` is not `UNAVAILABLE` and not `STALE` | `PRICE_STALE` or `PRICE_UNAVAILABLE` |
| Fundamental summary `data_status` is not `UNAVAILABLE` and not `STALE` | `FUNDAMENTALS_STALE` or `FUNDAMENTALS_UNAVAILABLE` |
| At least one basis is available | `NO_COMPARISON_BASIS` |
| At least one of `PE` or `PB` is `DEFINED` | `CORE_METRIC_UNAVAILABLE` |
| Combined base weight of scored, qualifying metrics is at least 0.50 | `INSUFFICIENT_METRIC_COVERAGE` |
| No consumed input carries `SOURCE_CONFLICT` | `SOURCE_CONFLICT` |

`classification`, `score`, and `confidence` are published together or withheld
together. A partial publication — a label with no score, or a score with no
label — is not a valid state, mirroring the `market-regime-v1` rule.

## Confidence

`confidence` is a 0-100 **data-quality** measure, not a probability that the
classification is correct.

```text
metricCoverage = sum of baseWeight over scored metrics that are DEFINED
                 and have at least one basis available            -> 0..1
basisCoverage  = number of available bases / 2                    -> 0..1
historyDepth   = min( available Basis A points / 750, 1 )         -> 0..1

confidence = round HALF_UP of
             100 * ( 0.45 * metricCoverage
                   + 0.35 * basisCoverage
                   + 0.20 * historyDepth )
```

The UI must present it as data completeness, never as certainty about the
valuation judgement.

## Disclosure

Every published assessment discloses:

- which bases were used, and for the sector basis, the scheme, version, and
  comparable constituent count;
- each metric's value, applicability state, and percentile in each basis used;
- `rule_version`, `as_of`, the contributing `fundamental_summary` revision, the
  price observation used, and the reporting period identity;
- the FR-015 wording that this is quantitative decision support rather than a
  forecast, guarantee, or instruction.

An assessment that used only Basis A must say so. A reader who does not know the
comparison basis cannot interpret the label, and hiding it would make the
classification look more general than it is.

## Reason codes

| Code | Meaning |
|---|---|
| `PRICE_STALE`, `PRICE_UNAVAILABLE` | Price input failed its freshness floor. |
| `FUNDAMENTALS_STALE`, `FUNDAMENTALS_UNAVAILABLE` | Fundamental input failed its freshness floor, research R-010. |
| `NO_COMPARISON_BASIS` | Neither own history nor sector cross-section qualified. |
| `SECTOR_BASIS_INSUFFICIENT` | Fewer than 8 comparable constituents; Basis B excluded. |
| `HISTORY_BASIS_INSUFFICIENT` | Fewer than 500 defined history points; Basis A excluded. |
| `CORE_METRIC_UNAVAILABLE` | Neither P/E nor P/B is defined. |
| `INSUFFICIENT_METRIC_COVERAGE` | Scored metric weight below 0.50. |
| `SOURCE_CONFLICT` | A consumed input has an unresolved cross-source conflict, DATA-010. |
| `NOT_APPLICABLE` | Per-metric; the metric is undefined for this company. |
| `CALCULATION_FAILED` | Internal error; nothing is published. |

## Required test vectors

| Test | Assertion |
|---|---|
| Band boundaries | Unrounded scores 35.4, 35.5, 64.4, 64.5 map to UNDER, FAIR, FAIR, OVER, and the displayed integers agree. |
| Negative earnings | `PE` and `PEG` are `NOT_APPLICABLE`; `PB` still scores; nothing is negative or zero-filled. |
| Single basis | With sector at 7 constituents, only Basis A is used, `SECTOR_BASIS_INSUFFICIENT` is reported, and the disclosure names Basis A alone. |
| No basis | Both bases unavailable withholds classification, score, and confidence together. |
| Weight renormalization | With `EV_EBITDA` and `PEG` missing, `PE` and `PB` renormalize to 0.571428571429 and 0.428571428571 at scale 12. |
| Coverage floor | Only `PEG` defined, weight 0.10, withholds with `INSUFFICIENT_METRIC_COVERAGE`. |
| Tie handling | A history where every point equals the current value yields percentile 50. |
| Stale fundamentals | A report 300 days old withholds with `FUNDAMENTALS_STALE`, research R-010. |
| Restatement | A restated report produces a new assessment revision; the superseded one remains readable. |
| Replay determinism | Recomputation from recorded inputs and `rule_version` yields the exact stored decimals. |
