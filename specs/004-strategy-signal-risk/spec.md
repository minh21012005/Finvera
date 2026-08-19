# Feature Specification: Strategy, Signal, and Risk Scenarios

**Feature Directory**: `004-strategy-signal-risk`
**Created**: 2026-08-19
**Status**: Draft
**SRS References**: Section 15 (Strategy Engine), 16 (Signal Engine), 17
(Risk Engine), 36.1 (performance), 47 (MVP-4), 54 (MVP-SC-06, MVP-SC-07), 58
(Requirements Index)
**SRS Requirement IDs**: SRS-STR-01, SRS-SIG-01, SRS-SIG-02, SRS-RSK-01;
MVP-SC-06, MVP-SC-07.
Explicitly deferred: SRS-RSK-02 (Section 18, Position Sizing — needs
`Available capital`/`Portfolio exposure`, which do not exist until MVP-5);
Section 19 (Backtesting Engine, a later feature); Sections 20-22 (Portfolio
Management, Portfolio Analytics, Watchlist — MVP-5).
**Input**: User description: "Strategy, Signal, and Risk Scenarios
(Feature 4, MVP-4). The owner opens a supported stock and sees whether any
of eight fixed, deterministic technical strategies (Trend Following,
Momentum, Breakout, Pullback, Mean Reversion, MA Crossover, MACD-based,
RSI-based) currently produces a trade signal — direction, entry zone, stop
loss, take-profit targets, risk/reward, a deterministic risk score and
level with named contributing factors, and the supporting evidence, all
reusing Feature 002's technical-indicators-v1 outputs and Feature 003's
Breakout/Trend derivations without recomputing them. The owner can also
screen the supported universe for every stock currently triggering one
chosen strategy. No custom strategy builder, no position sizing, no
portfolio/watchlist, no backtesting — same private single-owner deployment
as Features 001-003."

## Scope Summary *(mandatory)*

In the same private single-owner deployment established by Features
001-003, Finvera's owner needs to know whether a stock they are researching
currently presents an actionable, well-defined trade setup under one of a
fixed set of well-known technical strategies — and, if so, exactly what
that setup is (direction, entry, stop, targets, risk/reward), how risky it
is and why, and which accepted facts support it — before deciding whether
to act on it. The owner also needs to find which stocks in the supported
universe currently trigger a specific strategy, so research can start from
"what looks interesting right now" as well as from "is this specific stock
interesting."

Every strategy condition, every signal level, and every risk score is
produced by deterministic, versioned rules reusing Feature 002's
`technical-indicators-v1` results and Feature 003's `screener-v1`
Breakout/Trend derivations exactly as already computed and persisted — this
feature introduces new *combination and level-calculation* rules, not new
raw indicator math. A signal is presented as a scenario with disclosed
assumptions, never a guaranteed outcome or an instruction to trade.

### In Scope

- Eight fixed, named, versioned strategy definitions on the daily timeframe:
  Trend Following, Momentum, Breakout, Pullback, Mean Reversion, Moving
  Average Crossover, MACD-based, and RSI-based — each with a deterministic
  entry condition, stop-loss rule, take-profit rule, and applicable
  timeframe. No owner-defined custom strategy.
- For one supported stock, evaluation of all eight strategies against its
  latest accepted daily data, producing zero or more current signals, each
  with: symbol, strategy, direction, signal strength, entry zone, stop
  loss, take-profit target(s), risk/reward ratio, a deterministic risk
  score and level (`LOW`/`MEDIUM`/`HIGH`) with named contributing risk
  factors, supporting evidence (the specific accepted values that triggered
  it), and its as-of/created time.
- Trade-level risk scoring reusing already-accepted/computed factors:
  historical volatility, ATR, drawdown, liquidity (volume-based), stop-loss
  distance, and the current market regime (Feature 001). Position
  concentration and sector concentration are portfolio-level factors and
  are out of scope (no portfolio exists yet).
- A strategy screen: given one chosen strategy, the set of supported stocks
  currently triggering it, reusing Feature 003's screener execution pattern
  over the same candidate universe.
- Safe, truthful non-signal and degraded states: a strategy that does not
  currently trigger is absent, not fabricated; a stock without enough
  accepted history for a strategy's required indicators is excluded with a
  reason, consistent with Feature 002/003's `INSUFFICIENT_HISTORY` handling.
- Reproducibility: identical accepted inputs and an unchanged rule version
  always reproduce an identical signal and risk assessment.

### Out of Scope

- A custom strategy builder or owner-editable entry/exit conditions (SRS
  §15's "configurable" is satisfied by choosing among eight fixed,
  versioned strategies for this feature, not an open-ended condition
  editor).
- Position sizing (SRS §18 / SRS-RSK-02): needs `Available capital` and
  `Portfolio exposure`, which do not exist before Portfolio/Watchlist
  (MVP-5). Revisit then.
- Portfolio-level risk factors (position concentration, sector
  concentration): same reason — no portfolio holdings exist yet.
- Backtesting (SRS §19): historical strategy performance testing is a
  separate, later feature.
- Portfolio, watchlist, and journal actions; alerts and notifications
  (MVP-5).
- Any timeframe other than daily; intraday, weekly, and monthly strategy
  evaluation are deferred, consistent with Features 002/003.
- Automated order execution, autonomous trading, or any instruction that a
  signal be acted upon.
- AI-generated commentary or natural-language strategy interpretation.
- Multi-user or public delivery; the same private single-owner deployment
  model as Features 001-003 applies unchanged.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See a Stock's Current Trade Signals (Priority: P1)

As the owner, I want to open a supported stock and see whether any of the
eight fixed strategies currently produces a complete, well-defined signal —
including its risk level — so that I can judge whether a specific,
actionable setup exists before deciding whether to investigate further.

**Why this priority**: A signal without its risk level is not the signal
SRS §16 defines (`Risk Level` is a field of the signal itself, not a
separate concern), so the smallest coherent, independently valuable slice
already bundles strategy evaluation, signal assembly, and trade-level risk
scoring together, on one stock.

**Independent Test**: Open a supported symbol whose accepted daily history
triggers at least one strategy; verify the signal shows direction, entry
zone, stop loss, take-profit target(s), risk/reward, risk score, risk
level, named risk factors, supporting evidence, and as-of time. Open a
symbol that triggers no strategy and verify a truthful no-signal state.
Open a symbol with insufficient accepted history for a strategy's required
indicators and verify that strategy is excluded with a reason while others
remain evaluable.

**Acceptance Scenarios**:

1. **Given** a supported stock's accepted daily data currently satisfies one
   strategy's entry condition, **When** the owner opens that stock's
   strategy/signal view, **Then** it shows exactly one signal for that
   strategy with direction, entry zone, stop loss, at least one take-profit
   target, risk/reward ratio, risk score, risk level, the named factors
   contributing to that risk level, the accepted values that satisfied the
   entry condition, and the as-of time.
2. **Given** a supported stock's accepted daily data satisfies no strategy's
   entry condition, **When** the owner opens its strategy/signal view,
   **Then** it shows a specific "no current signal" state, not an error and
   not a fabricated setup.
3. **Given** a stock has fewer accepted daily bars than one strategy's
   required indicators need, **When** the owner opens its strategy/signal
   view, **Then** that strategy is excluded with a stated insufficient-
   history reason while every other evaluable strategy still produces its
   correct result.
4. **Given** more than one strategy's entry condition is satisfied at once
   for the same stock, **When** the owner views the page, **Then** every
   triggered strategy's signal is shown, each correctly attributed to its
   own strategy.
5. **Given** a signal is displayed, **When** the owner reviews it, **Then**
   it is presented as a deterministic scenario with disclosed assumptions,
   never a guarantee of future price movement or an instruction to trade.

---

### User Story 2 - Understand Why a Signal Is Risky (Priority: P2)

As the owner, I want to see the individual risk factors behind a signal's
risk score, so that I can judge whether the specific source of risk (e.g.
high volatility vs. thin liquidity vs. an unfavorable market regime)
matters to my own judgment, not just trust a single number.

**Why this priority**: Factor-level transparency adds decision-support
depth once a signal with a risk level already exists (US1), and is
meaningful only in that context.

**Independent Test**: Open a displayed signal's risk detail and verify each
named risk factor shows its own contributing value and how it affected the
score, reproducibly from the same accepted inputs.

**Acceptance Scenarios**:

1. **Given** a displayed signal, **When** the owner opens its risk detail,
   **Then** each risk factor (historical volatility, ATR, drawdown,
   liquidity, stop-loss distance, market regime) shows its own accepted
   value and its contribution to the overall risk score.
2. **Given** the current market regime (Feature 001) is degraded or
   unavailable, **When** the owner views a signal's risk detail, **Then**
   the market-regime factor is shown as unavailable with a reason, and the
   remaining factors and overall score are computed from the factors that
   are available, or the whole score is withheld if too few factors are
   available — never silently substituting a default.
3. **Given** identical accepted inputs and an unchanged rule version,
   **When** the owner reloads the risk detail, **Then** every factor value
   and the overall score are identical to the prior view.

---

### User Story 3 - Screen for a Strategy Across the Universe (Priority: P3)

As the owner, I want to pick one strategy and see every supported stock
currently triggering it, so that research can start from "what looks
interesting right now" as well as from a specific stock I already have in
mind.

**Why this priority**: A universe-wide strategy scan depends on the
per-stock strategy evaluation US1 already delivers, applied across many
stocks — genuinely useful, but only once evaluating one stock at a time is
correct and trustworthy.

**Independent Test**: Select a strategy and confirm the result set contains
exactly the supported stocks whose accepted daily data currently triggers
that strategy's entry condition, matching what US1 would show for each of
those stocks individually.

**Acceptance Scenarios**:

1. **Given** a chosen strategy and the supported universe, **When** the
   owner runs the scan, **Then** the result set contains exactly the
   stocks currently triggering that strategy, each showing its own signal
   summary (direction, entry zone, risk level).
2. **Given** no supported stock currently triggers the chosen strategy,
   **When** the owner runs the scan, **Then** it shows a specific
   empty-result state, not an error.
3. **Given** a stock is excluded from the scan because it lacks enough
   accepted history for the chosen strategy, **When** the owner views the
   scan's disclosure, **Then** that exclusion is visible and distinguishable
   from "the universe simply has no match."

### Edge and Failure Cases *(mandatory)*

- A stock is newly listed or suspended, with fewer accepted bars than any
  strategy's minimum lookback.
- Two or more strategies trigger simultaneously with conflicting
  directions for the same stock (e.g., one long-biased, one signaling
  weakness) — both are shown, neither is suppressed to force a single
  answer.
- A strategy's computed stop-loss or take-profit level would be
  mathematically invalid (e.g., zero or negative ATR-derived distance) —
  the signal is withheld with a reason, never displayed with a nonsensical
  level.
- The stock's price or a technical indicator it depends on is withheld due
  to a cross-source conflict (Feature 002 `SOURCE_CONFLICT`) — every
  strategy that depends on that value is withheld, not silently using a
  stale or substitute value.
- Feature 001's market regime assessment is stale, withheld, or
  unavailable at the moment a signal's risk score is computed.
- The owner requests a strategy/signal view or scan while accepted data is
  being recalculated after a correction — the response reads one coherent
  revision, never a mix of old and new values.
- Repeated identical requests produce repeated identical results with no
  side effect.
- An unauthenticated request or a request from any identity other than the
  configured owner.

## Requirements *(mandatory)*

Requirements describe observable behavior. Accepted IDs are stable and MUST
not be renumbered; removed requirements are deprecated with a reason.

### Functional Requirements

- **FR-001**: The system MUST define exactly eight named strategies (Trend
  Following, Momentum, Breakout, Pullback, Mean Reversion, Moving Average
  Crossover, MACD-based, RSI-based), each with a deterministic, versioned
  entry condition, stop-loss rule, take-profit rule, and daily timeframe.
- **FR-002**: For a supported stock, the system MUST evaluate all eight
  strategies against its latest accepted daily data and produce a signal
  for every strategy whose entry condition is currently satisfied.
- **FR-003**: Each signal MUST include symbol, strategy name, direction,
  signal strength, entry zone, stop loss, at least one take-profit target,
  risk/reward ratio, risk score, risk level, the named risk factors
  contributing to that level, the supporting accepted evidence, and an
  as-of/created time.
- **FR-004**: A strategy whose entry condition is not currently satisfied
  for a stock MUST produce no signal for that strategy, and MUST NOT be
  presented as an absent, withheld, or degraded signal — no current signal
  is not a failure.
- **FR-005**: When a strategy's required indicator(s) lack enough accepted
  history for a stock, that strategy MUST be excluded with a stated
  insufficient-history reason; every other evaluable strategy for that
  stock MUST remain usable.
- **FR-006**: The system MUST compute a deterministic risk score and one of
  `LOW`/`MEDIUM`/`HIGH` risk level for every produced signal, from named,
  individually inspectable factors (historical volatility, ATR, drawdown,
  liquidity, stop-loss distance, market regime).
- **FR-007**: When a risk factor's own input is unavailable, that factor
  MUST be shown as unavailable with a reason rather than defaulted; the
  system MUST NOT publish an overall risk score computed from a value it
  does not have.
- **FR-008**: Given identical accepted inputs and an unchanged rule
  version, repeated evaluation of the same stock MUST produce identical
  signals, risk scores, and risk levels.
- **FR-009**: The system MUST let the owner select one strategy and
  retrieve exactly the supported stocks currently triggering it, each with
  its own signal summary.
- **FR-010**: A strategy scan with zero currently-triggering stocks MUST
  show a specific empty-result state, not an error.
- **FR-011**: A stock excluded from a strategy scan for insufficient
  accepted history MUST be distinguishable from a scan that genuinely found
  no match.
- **FR-012**: No strategy, signal, or risk computation MUST recompute a
  technical indicator, Breakout condition, or Trend direction independently
  of Feature 002's `technical-indicators-v1` or Feature 003's `screener-v1`
  results already accepted and persisted.
- **FR-013**: Signal and risk presentation MUST identify the output as a
  deterministic decision-support scenario with disclosed assumptions, and
  MUST NOT present it as a guaranteed outcome, a prediction, or an
  instruction to buy or sell.
- **FR-014**: When accepted source data a signal or risk score depends on
  is corrected, the affected signal and risk assessment MUST be
  recalculated and shown with a new as-of indication; the superseded
  result MUST remain queryable, not deleted.

### Data and Financial Semantics

- **DATA-001**: Every produced signal and risk assessment MUST retain the
  exact accepted input references (technical indicator results, Breakout/
  Trend derivations, market regime assessment) and rule version needed to
  reproduce it exactly.
- **DATA-002**: Signal price levels (entry, stop, targets) and risk scores
  MUST use the same declared decimal precision as their source values;
  computations MUST NOT use binary floating point.
- **DATA-003**: A cross-source conflict (Feature 002 `SOURCE_CONFLICT`) on
  a value a strategy or risk factor depends on MUST withhold that
  strategy's signal or that risk factor, never silently use a disputed or
  stale value.
- **DATA-004**: Zero, missing, and not-applicable risk-factor or signal-
  level values MUST remain distinguishable; a missing value MUST NOT be
  replaced by zero.

### Security and Privacy

- **SEC-001**: Strategy, signal, and risk endpoints MUST be accessible only
  to the single configured owner identity, under the same authenticated
  server-side session and CSRF controls established in Features 001-003;
  no separate authentication path may be introduced.
- **SEC-002**: The system MUST NOT expose raw provider data, credentials,
  or tokens through signal/risk responses, exports, or any third-party
  delivery path.

### Non-Functional Requirements

- **NFR-001**: At least 95% of single-stock strategy/signal views MUST
  return within 3 seconds under normal operating conditions, consistent
  with Feature 002's primary-read-view baseline (SRS §36.1).
- **NFR-002**: At least 95% of strategy-scan executions over the supported
  universe MUST return within 5 seconds, consistent with Feature 003's
  screening baseline (SRS §36.1).
- **NFR-003**: Direction, risk level, and signal-strength states MUST be
  understandable without relying on color alone.

### Key Entities

- **Strategy**: One of the eight fixed, named, versioned rule sets
  defining an entry condition, stop-loss rule, take-profit rule, and
  timeframe; reference data, not per-stock data.
- **Signal**: A reproducible result for one stock and strategy: direction,
  levels, risk/reward, risk score/level, contributing factors, supporting
  evidence, rule version, and as-of time.
- **Risk Assessment**: The named-factor breakdown and overall score/level
  behind one signal's risk, each factor tied to its own accepted input.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- **Direction is LONG-only for this feature.** Vietnamese retail brokerage
  accounts do not generally support short-selling on HOSE/HNX/UPCOM; a
  SHORT-direction signal would suggest an action the owner cannot actually
  take through a normal account, which would be misleading decision
  support rather than helpful. SRS §16's `Direction` field allows for
  future expansion (e.g., derivatives, margin short where legally
  available) but this feature emits `LONG` only. A strategy whose classic
  definition is inherently bearish-only (none of the eight named
  strategies strictly requires shorting to express — e.g., "Mean
  Reversion" and "Pullback" can both be expressed as long-side setups)
  produces no signal when its own condition is bearish, rather than a
  SHORT signal the owner cannot act on.
- Every strategy, signal, and risk-factor formula reuses Feature 002's
  `technical-indicators-v1` (MA20/50/200, RSI14, MACD, BBANDS, ATR14,
  AVG_VOLUME20, RELATIVE_VOLUME) and Feature 003's `screener-v1` Breakout/
  Trend derivations as-is; this feature defines new *combination and
  level-calculation* rules (entry/stop/target/risk-score formulas) on top
  of them, not new raw indicator math — the same reuse discipline Feature
  003 applied to Feature 002.
- The supported stock universe and daily timeframe are exactly Feature
  001-003's existing scope; no new universe or timeframe is introduced.
- A signal is a deterministic evaluation of already-accepted facts, not a
  forecast, and confers no fiduciary or execution authority (Constitution
  Principle IV).
- No LLM or generative AI capability is required to deliver this feature.

### Dependencies

- Feature 002's persisted `technical_indicator_result`/
  `technical_indicator_value` and daily bars.
- Feature 003's `screener-v1` Breakout/Trend derivation logic and its
  two-pass universe-scanning pattern (research R-002), reused for User
  Story 3's strategy scan.
- Feature 001's persisted market regime assessment, reused as one risk
  factor.
- A versioned deterministic strategy-condition specification (entry/exit/
  risk rules per named strategy), a versioned signal level-calculation
  specification (entry zone, stop-loss distance, take-profit targets,
  risk/reward), and a versioned risk-scoring specification (factor
  formulas, weighting, band thresholds) — all to be resolved in this
  feature's `research.md`/`contracts/` before implementation, consistent
  with Constitution Principle I.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Across versioned strategy/signal/risk fixtures covering every
  strategy's trigger and non-trigger cases, 100% of results match the
  independently computed expected direction, levels, risk/reward, risk
  score, and risk level.
- **SC-002**: Across repeated evaluations with unchanged accepted inputs
  and rule version, 100% of signals and risk assessments are identical
  (MVP-SC-06 lineage).
- **SC-003**: 100% of displayed signals state their strategy, rule version,
  triggering conditions, and supporting evidence (MVP-SC-06).
- **SC-004**: 100% of displayed signals state entry zone, stop loss,
  target(s), risk/reward, and the assumptions behind them, and are labeled
  as scenarios rather than guarantees (MVP-SC-07).
- **SC-005**: At least 95% of single-stock strategy/signal views return
  within 3 seconds, and at least 95% of strategy-scan executions return
  within 5 seconds.
- **SC-006**: Across insufficient-history, source-conflict, and
  regime-unavailable fixtures, 100% of affected strategies/factors are
  withheld with a truthful reason, never fabricated or defaulted.
- **SC-007**: Accessibility review confirms 100% of direction, risk-level,
  and signal-strength states have a non-color indicator.
- **SC-008**: Authorization tests show only the configured owner can reach
  any strategy/signal/risk endpoint; no response, log, or export contains a
  credential, token, or raw provider payload.

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001, FR-002, FR-003 | US1 / Scenario 1 | SC-001, SC-003, SC-004 |
| FR-004 | US1 / Scenario 2 | SC-006 |
| FR-005 | US1 / Scenario 3 | SC-006 |
| FR-002 (multi-trigger) | US1 / Scenario 4 | SC-001 |
| FR-013 | US1 / Scenario 5; US2 acceptance review | SC-004 |
| FR-006, FR-007 | US2 / Scenarios 1-2 | SC-001, SC-006 |
| FR-008 | US2 / Scenario 3; US1 reproducibility | SC-002 |
| FR-009, FR-010, FR-011 | US3 / Scenarios 1-3 | SC-001, SC-006 |
| FR-012 | All stories; edge cases | SC-001 |
| FR-014 | Correction edge case | SC-002, SC-006 |
| DATA-001, DATA-002 | US1-US3 | SC-001, SC-002 |
| DATA-003, DATA-004 | Edge cases; US1/US3 exclusion scenarios | SC-006 |
| SEC-001, SEC-002 | Owner-only access edge case | SC-008 |
| NFR-001, NFR-002 | US1, US3 timing | SC-005 |
| NFR-003 | US1-US3 | SC-007 |
