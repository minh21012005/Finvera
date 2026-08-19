# Research: Strategy, Signal, and Risk Scenarios

**Feature**: `004-strategy-signal-risk`
**Status**: Complete for fixture-mode implementation. No new provider
dependency and no new gate is introduced by this feature.

Format: `Decision / Rationale / Alternatives considered / Risks and
validation`.

## R-001: Module placement

**Decision**: Extend the existing `finvera-be` `stock` module with a new
`strategy` sub-package (`stock.domain.strategy`, `stock.service.strategy`,
`stock.dto`, `stock.controller` additions) — not a new module, not a new
deployable service.

**Rationale**: Every input this feature reads (`technical_indicator_result`,
Feature 003's Breakout/Trend derivations, daily bars, Feature 001's regime
assessment via the published `MarketReferenceDataService`-style interface)
is already owned by `stock`/`market`. This is the same reasoning Feature
003's research R-001 already applied to itself; nothing about Strategy/
Signal/Risk introduces a new aggregate, provider, or write path that would
justify a new module (Constitution Principle VIII).

**Alternatives considered**: a new `strategy` top-level module — rejected
for the same reason Feature 003 rejected one.

## R-002: The eight strategy definitions

**Decision**: Each of the eight SRS §15 strategies is a fixed, versioned,
deterministic entry condition built **only** from already-accepted/
persisted values — Feature 002's `technical-indicators-v1` results and
Feature 003's `screener-v1` Breakout/Trend derivations — evaluated on the
instrument's latest accepted daily data. Three strategies (Moving Average
Crossover, MACD-based, RSI-based) are defined as **crossing** events, which
additionally read the immediately preceding trading date's accepted
`technical_indicator_result` (not recomputed — the already-persisted prior
row), because "the fast MA is above the slow MA" and "the fast MA just
crossed above the slow MA today" are different, and only the second is a
timely entry trigger; the first could have been true for months.

Full formulas, minimum bar counts, and required test vectors are normative
in [contracts/strategy-signal-v1.md](contracts/strategy-signal-v1.md). Summary:

| Strategy | Entry condition (informal) | Minimum bars |
|---|---|---|
| Trend Following | `Trend = UPTREND` (screener-v1) and price still above `MA20` | 200 |
| Momentum | `RSI14 >= 60` and `MACD` `HISTOGRAM > 0` | 250 |
| Breakout | `Breakout = BREAKOUT_UP` (screener-v1) and `RELATIVE_VOLUME >= 1.5` | 21 |
| Pullback | Uptrend intact (`MA20 > MA50 > MA200`), `RSI14` in `[40, 55]`, price still above `MA50` | 250 |
| Mean Reversion | `RSI14 <= 30` and price below `BBANDS` `LOWER` | 250 |
| MA Crossover | `MA20` crosses above `MA50` today (was `<=` yesterday) | 50 (+1 prior day) |
| MACD-based | `MACD` `HISTOGRAM` crosses above 0 today | 250 (+1 prior day) |
| RSI-based | `RSI14` crosses above 30 today (exits oversold) | 250 (+1 prior day) |

**Rationale**: Each formula is a textbook definition of its named strategy
type, expressed only in terms of Feature 002/003's already-accepted
outputs — no new raw indicator math (FR-012). Using the *crossing* form for
the three threshold/MA-relationship strategies (rather than "is currently
above/below") avoids a signal that stays "triggered" for weeks after the
actual entry moment, which would misrepresent a stale setup as a fresh one.

**Alternatives considered**: A single shared "trend + momentum + breakout"
composite condition — rejected; SRS §15 names eight distinct strategies
and conflating them would lose exactly the "which specific setup is this"
information FR-003 requires. Using "is above" instead of "crosses above"
for MA/MACD/RSI-based strategies — rejected: it would re-trigger the same
signal identically for many consecutive days with no new information,
which also breaks the reproducibility framing (FR-008 says identical inputs
reproduce identical *results*, not that a stale condition should keep
re-announcing itself as fresh).

**Risks/validation**: `contracts/strategy-signal-v1.md`'s required-test-
vector table must cover each strategy's exact boundary (e.g., RSI exactly
30 vs 30.000001, a same-day MA20/MA50 tie) so no off-by-one produces a
fabricated or missed signal.

## R-003: Signal levels — entry zone, stop loss, take profit, risk/reward

**Decision**: One uniform, ATR-anchored level framework applied to every
triggered signal regardless of which strategy fired:

```text
entryLow  = C[n] - 0.25 * ATR14[n]
entryHigh = C[n] + 0.25 * ATR14[n]
stopLoss  = C[n] - 2 * ATR14[n]
target1   = C[n] + 2 * (C[n] - stopLoss)   =  C[n] + 4 * ATR14[n]
target2   = C[n] + 3 * (C[n] - stopLoss)   =  C[n] + 6 * ATR14[n]
riskReward = (target1 - C[n]) / (C[n] - stopLoss)   =  2.0, by construction
```

**Rationale**: A volatility-anchored ("N-based"/ATR) stop is a standard,
well-documented systematic-trading convention (e.g., the Turtle Trading
"2N stop", Van Tharp's R-multiple framework) that scales the stop distance
to the instrument's own recent volatility rather than a fixed percentage —
appropriate across eight structurally different strategies without needing
eight different stop conventions. `riskReward` is **constant at 2.0 for
target1 / 3.0 for target2 by construction**, which is intentional, not an
oversight: every signal uses the same disclosed risk-management framework,
so risk/reward is a stated *policy*, not a per-signal differentiator — what
actually differentiates signals is which strategy fired, the entry
condition's own supporting evidence, and the risk score (R-004). SRS §16's
worked example (`1 : 2.4`) is illustrative of the *shape* of a signal, not
a literal formula this feature must reproduce exactly.

**Alternatives considered**: A percentage-based stop (e.g., stop = 5% below
entry) — rejected; ignores the instrument's own volatility, so the same 5%
would be tight for a calm blue-chip and loose for a volatile small-cap,
producing inconsistent real risk despite an identical stated percentage.
A per-strategy stop/target convention (e.g., Breakout stops below the
breakout level, Mean Reversion targets the Bollinger midline) — rejected
for this feature as materially more design and test surface for a first
version; revisit if the owner finds the uniform framework too coarse once
used.

**Risks/validation**: A zero or negative `ATR14` (only possible in a
degenerate flat-price fixture) must withhold the signal per FR-003's edge
case ("mathematically invalid level"), never divide by zero or emit a
nonsensical stop.

## R-004: Risk score and level

**Decision**: A weighted 0-100 risk score from six named factors, each
independently computed from an already-accepted value and individually
disclosed (FR-006/FR-007), banded into `LOW` (`0-33`), `MEDIUM` (`34-66`),
`HIGH` (`67-100`):

| Factor | Source | Higher factor score means |
|---|---|---|
| Historical volatility | `ATR14 / close` (percent-of-close, already a technical-indicators-v1 component: `ATR14` `PERCENT_OF_CLOSE`) | Higher relative volatility |
| ATR (absolute) | `ATR14` value, normalized against the stock's own trailing range | Wider absolute average true range |
| Drawdown | Percent decline from the highest close in the last 250 accepted sessions to the current close | Deeper recent drawdown |
| Liquidity | Inverse of `RELATIVE_VOLUME` and `AVG_VOLUME20` — thin, below-average liquidity scores higher risk | Thinner liquidity |
| Stop-loss distance | `(C[n] - stopLoss) / C[n]` — the R-003 stop expressed as a percent of price | Wider stop distance as a percent of price |
| Market regime | Feature 001's current regime assessment score (bearish/high-volatility regimes score higher) | A less favorable regime |

Each factor is scored 0-100 on its own declared scale (exact thresholds in
`contracts/strategy-signal-v1.md`), then combined with a fixed, disclosed
weighting (equal-weighted, `1/6` each, unless a factor is unavailable — see
below) into the overall score.

**Rationale**: SRS §17 names exactly these factors (minus position/sector
concentration, deferred per spec.md's Out of Scope). Reusing `ATR14`
`PERCENT_OF_CLOSE` (already computed by `technical-indicators-v1`) for the
volatility factor avoids a second volatility calculation. Equal weighting
is the simplest defensible starting point absent any evidence favoring one
factor over another; Constitution Principle VIII favors the least complex
design that still satisfies correctness.

**Missing-factor handling (FR-007)**: if a factor's own input is
unavailable (e.g., Feature 001's regime assessment is withheld), that
factor is disclosed as unavailable and the remaining factors are
re-weighted proportionally (equal weight among whatever is available) —
**unless fewer than four of the six factors are available**, in which case
the whole risk score is withheld (not published on a thin, unrepresentative
subset), consistent with `valuation-v1`'s own withhold-below-a-floor
precedent (its `N_min`/`H_min` floors).

**Alternatives considered**: A machine-learned risk model — rejected
outright; Constitution Principle I forbids anything but deterministic,
versioned code for a risk measure. Weighting factors by presumed
importance (e.g., double-weighting drawdown) — rejected for v1 absent any
stated evidence for a different weighting; equal-weight is the transparent,
auditable default and the weighting itself is versioned, so a future
`risk-engine-v2` can change it deliberately with a recorded rationale.

**Risks/validation**: the four-of-six floor must be exercised by a fixture
(exactly 3 available factors withholds the whole score; exactly 4 publishes
it) so the boundary is proven, not assumed.

## R-005: Signal Strength

**Decision**: `Signal Strength` (`WEAK`/`MODERATE`/`STRONG`) is derived
directly from the same signal's risk level, inverted: `LOW` risk → `STRONG`
strength, `MEDIUM` risk → `MODERATE`, `HIGH` risk → `WEAK`.

**Rationale**: SRS §16 lists `Signal Strength` as a field but does not
define its formula. Rather than inventing a second, independent per-
strategy "conviction" heuristic (which would need its own eight formulas,
one per strategy, doubling the design and test surface R-002 already
established), tying it to the already-designed, factor-based risk score
is simpler, consistent (a cleaner setup — tighter stop relative to
volatility, healthy liquidity, favorable regime — is both lower-risk and
more conventionally "stronger"), and requires no new machinery
(Constitution Principle VIII).

**Alternatives considered**: A per-strategy margin-past-threshold heuristic
(e.g., how far past 30 the RSI crossed) — rejected for this feature as
disproportionate complexity for an SRS field with no normative definition;
revisit if the owner finds strength-tied-to-risk uninformative in practice.

## R-006: Persistence and reproducibility

**Decision**: Signals and risk assessments are persisted, immutable,
revision-chained results — the same pattern as Feature 002's
`technical_indicator_result`/`valuation_assessment` — not computed
transiently per request. A `strategy_signal` row exists per (instrument,
strategy, as-of trading date, rule version) when that strategy's condition
was satisfied that day; a linked `risk_assessment` row carries the score/
level/factors for that signal.

**Rationale**: SRS §16 names `Created At` as a signal field, implying a
signal is a dated event, not a live-recomputed view — and Constitution
Principle II (evidence/provenance) and the reproducibility requirements
(FR-008, DATA-001) are most naturally satisfied by the same immutable,
input-linked persistence model every other Feature 002/003 derived result
already uses, rather than inventing a transient-only model for this
feature alone (which Feature 003's screener legitimately does, but a
screener result is explicitly *not* meant to be a dated historical record
— a signal explicitly is, per SRS's own `Created At` field).

**Alternatives considered**: Transient, recomputed-per-request signals
(Feature 003's own pattern) — rejected specifically because SRS models a
signal as a created, dated artifact, unlike a screen.

## R-007: Strategy scan (User Story 3) execution strategy

**Decision**: Reuse Feature 003's `screener-v1` two-pass execution pattern
(research R-002 there): fetch the candidate universe's current daily
bars/indicators in bulk, evaluate the chosen strategy's condition in Java
against every candidate, and return the triggering subset. Because a
signal is normally persisted per R-006, the scan can either read
already-persisted current-day signals for the chosen strategy directly
(cheaper, if the owner has already opened enough individual stocks that
day) or evaluate on demand for candidates with no persisted signal yet for
today's as-of date — the scan is defined to always evaluate on demand
across the full candidate set, both for simplicity (one code path, not two)
and because persisted signals are a side effect of viewing a stock, not
something a scan should depend on having happened first.

**Rationale**: Directly reuses a proven pattern (Feature 003) rather than
inventing a second universe-scanning strategy; keeps the eight strategy
conditions defined exactly once (R-002) and evaluated identically whether
reached from a single-stock view or a universe scan.

## R-008: Inherited gates and dependencies

**Decision**: This feature opens no new provider gate. It depends entirely
on already-accepted Feature 001/002/003 data being present; a stock with
no accepted technical history simply cannot produce a signal (FR-005),
exactly like Feature 003's own gated-category behavior.

## R-009: Fixture strategy

**Decision**: Small, hand-constructed, independently-computed daily-bar
series per strategy (mirroring Feature 003's direct-seeding approach via
`StockIngestionService`/`TechnicalIndicatorService`, not JSON provider
fixtures) — one series per strategy that deliberately triggers it, one
that does not, and the three crossing-strategy series additionally
constructed so the prior day does *not* satisfy the condition and today
does, to prove the crossing logic rather than a persistent "is above"
condition.
