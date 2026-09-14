# Feature Specification: Historical Strategy Backtesting

**Feature Directory**: `031-historical-strategy-backtesting`  
**Created**: 2026-09-13  
**Status**: Draft  
**SRS References**: SRS Section 19; SRS-BKT-01; SRS-BKT-02  
**Input**: User description: "Implement the next roadmap capability, historical strategy backtesting, through the repository's full SDD workflow."

## Scope Summary *(mandatory)*

This feature lets the authenticated owner test one supported deterministic
long-equity strategy on one Vietnamese listed equity over an explicit completed
daily-session period. The owner declares capital, risk sizing, fees, tax, and
slippage, then receives a reproducible result containing trades, an equity
curve, performance measures, assumptions, versions, and data provenance.

The first slice is a research and decision-support tool. It prevents future
information from influencing earlier decisions, uses the same strategy and
position-sizing semantics as the interactive product, and withholds misleading
results when the historical series or adjustment basis is unsuitable. It does
not claim that simulated performance will be achieved in live trading.

## Clarifications

### Session 2026-09-13

- Q: Which initial strategy scope should be accepted? → A: Support all eight existing `strategy-signal-v1` strategies; each run selects one.
- Q: Which deterministic entry/exit and same-bar ambiguity policy should govern the first slice? → A: Enter at the next eligible session open, exit fully at stop-loss or target1, resolve an indeterminate same-bar stop/target touch as stop-first, and close an open terminal position at the final-session close.
- Q: How should qualifying signals be treated while a position is open? → A: Support bounded pyramiding in the first slice under an explicit versioned pyramiding policy.
- Q: What constitutes a new pyramiding entry and how should its tranches and exits be represented? → A: Require a new false-to-true signal episode and a next-open fill at least `0.5 × ATR14` above the latest entry; allow at most four independently recorded tranches.
- Q: How should risk budget be allocated across pyramid tranches? → A: Declare per-tranche and aggregate open-risk rates; size each tranche by the smaller of its own budget and remaining aggregate risk, subject to available cash.

### In Scope

- Create an owner-scoped asynchronous backtest selecting any one of all eight
  `strategy-signal-v1` strategies, one symbol, daily sessions, and an explicit
  inclusive date range.
- Configure initial capital, risk per trade, all applicable transaction costs,
  entry and exit slippage, and the deterministic execution policy.
- Reuse versioned strategy-signal and position-sizing behavior without changing
  their calculations inside the backtest.
- Show lifecycle state, progress, failure or withholding reasons, and allow the
  owner to retrieve completed results.
- Show trades, daily equity, cash and open-position value, total return, CAGR,
  win rate, profit factor, maximum drawdown, Sharpe ratio, average trade return,
  and trade count.
- Expose the historical data range, source, accepted-time boundary, adjustment
  status, rule versions, cost assumptions, execution assumptions, warnings,
  and survivorship/corporate-action limitations.

### Out of Scope

- Multiple-symbol, portfolio, sector, or whole-universe backtests.
- Intraday bars, short selling, margin, leverage, derivatives, odd lots,
  partial fills, discretionary partial exits, or order-book simulation.
- Custom strategy creation, parameter optimization, walk-forward optimization,
  Monte Carlo analysis, benchmark comparison, or AI-selected parameters.
- Live orders, broker integration, scheduled automatic runs, alerts, or using a
  backtest result as a promise or personalized instruction to trade.
- Reconstructing delisted-symbol universes for the single-symbol first slice.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run a Reproducible Backtest (Priority: P1)

As the owner, I want to run a strategy over a chosen symbol and historical
period with explicit capital, sizing, and costs so that I can inspect how the
rules would have behaved under declared assumptions.

**Why this priority**: A reproducible run with financially coherent trades is
the smallest useful backtesting capability and the basis for every metric.

**Independent Test**: Submit a valid fixture whose signals, next eligible
execution sessions, sized quantities, costs, and exits are independently known;
verify the completed trade ledger and ending cash exactly match the fixture.

**Acceptance Scenarios**:

1. **Given** sufficient accepted adjusted daily history and valid assumptions,
   **When** the owner starts a run, **Then** the system accepts it as an
   asynchronous job and eventually returns the reproducible completed ledger.
2. **Given** a signal calculated from session D, **When** the simulation
   executes it, **Then** no price or fact first known after the permitted
   decision boundary influences that signal or its sizing.
3. **Given** the same immutable input data, versions, and assumptions, **When**
   the run is repeated, **Then** all trades, equity values, and metrics match.
4. **Given** missing, conflicting, stale, or unsuitable historical data, **When**
   a run reaches the affected interval, **Then** it produces no fabricated
   trade or metric and reports a stable withholding or failure reason.
5. **Given** independently prepared triggering and non-triggering histories for
   each supported strategy, **When** each strategy is backtested, **Then** all
   eight use their published entry conditions and minimum-history rules.

---

### User Story 2 - Understand Performance and Risk (Priority: P2)

As the owner, I want a transparent performance report and equity path so that I
can judge return, drawdown, consistency, and the effect of costs together.

**Why this priority**: Aggregate return alone can hide severe drawdowns, sparse
trading, and cost sensitivity.

**Independent Test**: Use fixed trade-ledger fixtures including wins, losses,
no trades, zero variance, less than one year, and multi-year periods; verify
each published metric against an independent decimal calculation or a declared
unavailable reason.

**Acceptance Scenarios**:

1. **Given** a completed run with closed trades, **When** the owner opens the
   result, **Then** every required performance measure shows its value, unit,
   formula version, and applicable warning.
2. **Given** a run for which a metric is mathematically undefined, **When** the
   result is shown, **Then** that metric is marked unavailable with a reason
   rather than represented as zero or infinity.
3. **Given** an open position at the requested end boundary, **When** the run is
   finalized, **Then** the declared terminal-position policy is applied and is
   visible in both the trade ledger and assumptions.

---

### User Story 3 - Monitor and Revisit Runs (Priority: P3)

As the owner, I want to monitor and revisit my own backtests so that a long run
does not block normal product use and its evidence remains auditable.

**Why this priority**: Historical computation can be expensive and must remain
separate from ordinary transactional requests.

**Independent Test**: Start, poll, list, and retrieve owner-scoped runs; verify
state transitions, bounded progress, indistinguishable cross-owner access
failure, and graceful recovery from interrupted execution.

**Acceptance Scenarios**:

1. **Given** an accepted run, **When** computation is ongoing, **Then** the owner
   sees a stable state and progress without holding the original request open.
2. **Given** a completed or failed run, **When** the owner returns later,
   **Then** the owner can retrieve its inputs, state, evidence, and available
   result or reason.
3. **Given** another identity or an unknown run identifier, **When** it is
   requested, **Then** the response does not reveal whether another owner's run
   exists.

### Edge and Failure Cases *(mandatory)*

- The date range is reversed, includes the current incomplete session, spans no
  completed trading session, or lacks the strategy's warm-up history.
- The first eligible execution session is missing, suspended, has unusable
  prices, or lies outside the requested range.
- Entry, stop, and target can be touched within one daily bar but their intraday
  order is unknowable.
- Capital or risk cannot fund one standard lot after costs and slippage.
- Corporate-action adjustment is missing, mixed, revised, or conflicts across
  accepted sources during the calculation window or warm-up window.
- An adjustment factor changes between a signal and its next-session execution,
  or while one or more raw-price tranches remain open.
- A trade or one of several tranches remains open at the requested end date; no signal occurs; all trades
  win; all trades lose; profit factor or Sharpe ratio is undefined.
- A duplicate submission or worker retry occurs, or execution is interrupted
  after partial internal progress.
- Rules or accepted historical data change after a completed run.
- The owner attempts a cross-owner read, or submits a range large enough to
  breach the configured bounded-work limit.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST let the authenticated owner create a backtest by
  selecting one supported strategy, one supported equity symbol, and an
  inclusive completed-session start and end date.
- **FR-002**: The first slice MUST support all eight strategies defined by
  `strategy-signal-v1`: `TREND_FOLLOWING`, `MOMENTUM`, `BREAKOUT`, `PULLBACK`,
  `MEAN_REVERSION`, `MA_CROSSOVER`, `MACD_BASED`, and `RSI_BASED`; every run
  selects exactly one strategy and its declared rule version.
- **FR-003**: The owner MUST declare positive initial capital, a positive
  `riskPerTrancheRate`, and a positive `maxAggregateOpenRiskRate` that is not
  lower than the per-tranche rate. Before every entry, the simulation MUST
  calculate remaining aggregate risk as the aggregate cap on current equity
  less the sum of non-negative loss-at-stop across open tranches. The new
  tranche's risk budget MUST be the smaller of its per-tranche budget and that
  remaining risk, and the versioned position-sizing behavior MUST also constrain
  it by current simulated cash and the standard lot. An entry with insufficient
  remaining risk or cash for one lot MUST be rejected with a stable reason.
- **FR-004**: The owner MUST either declare every entry fee, exit fee, sell tax,
  entry slippage, and exit slippage rate or explicitly exclude costs with
  visible warnings; a declared all-zero cost set MUST NOT bypass exclusion.
- **FR-005**: Runs MUST execute asynchronously with stable states `QUEUED`,
  `RUNNING`, `COMPLETED`, `WITHHELD`, and `FAILED`, plus bounded progress and a
  terminal reason when no result can be published.
- **FR-006**: The simulation MUST evaluate strategy inputs in completed-session
  order and MUST NOT use a later session's bar, indicator, signal, revision, or
  outcome to make an earlier decision.
- **FR-007**: A signal based on completed session D MUST enter the full sized
  position at the next eligible session's open adjusted by entry slippage. The
  position MUST exit fully at stop-loss or target1 adjusted by applicable exit
  slippage and costs. When both levels fall within the same daily bar and their
  intraday order is unknowable, stop-loss MUST be treated as occurring first.
  Any position still open at the reporting end MUST close at the final eligible
  session's close and be identified as a terminal-period exit.
- **FR-008**: The first slice MUST support bounded pyramiding while a position
  is open. An addition MUST follow a new signal episode in which the selected
  strategy became false and later true again, and its next-session opening fill
  after slippage MUST be at least `0.5 × ATR14` above the most recent entry
  fill. A position MUST contain no more than four open tranches. Every tranche
  MUST retain its own entry, quantity, costs, stop, target, and exit, while the
  run also reports the aggregate position. Continuing daily true conditions do
  not create new episodes. The engine MUST record accepted and rejected
  additions with stable reasons.
- **FR-009**: The engine MUST record every simulated tranche entry and exit with trading
  dates, unadjusted executable prices, quantity, gross value, each cost,
  realized profit/loss, return, exit reason, and rule versions.
- **FR-010**: The engine MUST produce one end-of-session equity point for every
  simulated session, separating cash, open-position market value, and total
  equity without allowing future facts into valuation.
- **FR-011**: Completed results MUST include total return, CAGR, win rate, profit
  factor, maximum drawdown, Sharpe ratio, average trade return, and trade count;
  undefined metrics MUST be unavailable with stable reasons.
- **FR-012**: Each result MUST expose all owner inputs, effective assumptions,
  strategy/sizing/metric/market-rule versions, processed period, warm-up period,
  source evidence, accepted-time boundary, warnings, and limitations.
- **FR-013**: The owner MUST be able to list and retrieve only their own runs,
  including terminal results and non-sensitive failure or withholding reasons.
- **FR-014**: Repeated execution from identical immutable inputs and rule
  versions MUST produce identical financial outputs.
- **FR-015**: Worker retry or duplicate delivery MUST NOT create duplicate
  trades, equity points, or contradictory terminal states.
- **FR-016**: Backtest processing MUST NOT block ordinary market, portfolio,
  authentication, or AI requests.
- **FR-017**: Results MUST be presented as historical simulations with visible
  assumptions and risks, without promises or directives to buy or sell.

### Data and Financial Semantics

- **DATA-001**: Every run MUST identify symbol, exchange, source, observation and
  acceptance boundaries, requested period, actual processed sessions, data
  status, adjustment basis, currency VND, and standard-lot rule evidence.
- **DATA-002**: A decision for session D MUST use only facts whose effective
  trading date is at or before D and whose accepted-time value is at or before
  the run's frozen data cutoff.
- **DATA-003**: Warm-up history MAY precede the requested reporting start but
  MUST affect only indicator readiness; no trade or reported return may begin
  before the requested start.
- **DATA-004**: The run MUST use one coherent accepted historical price series.
  Missing, conflicting, or mixed adjustment bases that could change decisions
  MUST withhold the affected run rather than silently combine data.
- **DATA-005**: Corporate actions MUST be handled only through a declared,
  coherent adjustment basis; the result MUST state remaining limitations and
  MUST NOT infer unknown actions. Because current canonical data does not
  identify enough event terms to transform shares and cash reliably, a run MUST
  be `WITHHELD` with `CORPORATE_ACTION_UNSUPPORTED` if an available adjustment factor
  changes between signal and execution or while any tranche is open. Adjusted
  history may still supply warm-up and decisions while no simulated position
  crosses the discontinuity.
- **DATA-006**: Strategy inputs, fills, and marks MUST use one coherent accepted
  OHLC basis. `RAW` rows represent historical quoted prices. For VCI
  `PROVIDER_ADJUSTED` rows, stored OHLC is a normalized simulation basis rather
  than a claim of literal historical cash execution; results MUST disclose this
  limitation. A dual raw/adjusted series is withheld until a versioned
  conversion and corporate-action ledger is available.
- **DATA-007**: Money and rates MUST use decimal arithmetic with explicit
  precision and rounding; share quantities MUST follow exact mathematical floor
  and standard-lot rules from the selected sizing contract.
- **DATA-008**: The equity identity MUST hold at every point: total equity equals
  cash plus marked open-position value, and costs/taxes MUST reduce cash exactly
  once.
- **DATA-009**: Metrics MUST use one versioned convention defining annualization,
  daily risk-free assumption, return sampling, zero/negative denominators,
  closed-trade inclusion, and rounding.
- **DATA-010**: Profit factor is gross winning realized P/L divided by absolute
  gross losing realized P/L; win rate and average trade use closed trades only;
  edge cases MUST follow declared unavailable rules.
- **DATA-011**: Maximum drawdown MUST be derived from the complete ordered equity
  series using prior running peaks and MUST never be positive.
- **DATA-012**: Results MUST preserve an immutable input fingerprint and rule/data
  snapshot identity sufficient to explain reproducibility; later corrections
  MUST NOT silently rewrite a completed result.
- **DATA-013**: The result MUST disclose that a current single-symbol test does
  not eliminate survivorship bias and MUST identify unavailable delisting or
  suspension history where relevant.

### Security and Privacy

- **SEC-001**: Authentication and server-side ownership checks MUST protect
  create, list, status, result, and any cancellation operation.
- **SEC-002**: Unknown and non-owned run identifiers MUST produce
  indistinguishable responses and MUST NOT reveal another owner's inputs,
  symbol choices, capital, trades, or results.
- **SEC-003**: Inputs MUST be schema validated and bounded by permitted date
  span, work size, rates, money precision, supported symbols, and rule versions.
- **SEC-004**: Logs and metrics MUST exclude owner financial inputs, full
  requests/results, positions, and trade ledgers; they MAY contain correlation
  identifiers, states, durations, stable reason codes, and bounded counts.

### Non-Functional Requirements

- **NFR-001**: At least 95% of valid create requests MUST return a job reference
  and initial state within one second under the documented single-owner load.
- **NFR-002**: For a supported symbol with ten years or fewer of daily sessions,
  at least 95% of runs MUST reach a terminal state within 60 seconds under the
  documented reference environment.
- **NFR-003**: Status and result views MUST be keyboard operable, readable
  without color dependence, and expose text equivalents for the equity curve.
- **NFR-004**: Interrupted processing MUST leave a recoverable or safely failed
  run; retry MUST preserve idempotency and must never publish partial results as
  completed.
- **NFR-005**: Operators MUST be able to observe queue time, execution time,
  terminal state, stable failure reason, processed-session count, and retry
  count without exposing sensitive payloads.

### Key Entities

- **Backtest Run**: Owner-scoped request, frozen assumptions, version and data
  identities, lifecycle state, progress, and terminal outcome.
- **Backtest Trade**: One fully opened and closed simulated long position linked
  to a run, including execution evidence, quantity, costs, P/L, and exit reason.
- **Equity Point**: End-of-session cash, marked position value, and total equity
  for one processed trading session.
- **Backtest Metrics**: Versioned aggregate measures and per-metric availability
  reasons derived from the terminal trade and equity series.
- **Backtest Evidence**: Sources, time boundaries, adjustment basis, rule
  versions, assumptions, warnings, and known data limitations for one run.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- The first slice is long-only, cash-funded, one symbol, one strategy, and daily
  frequency, with bounded concurrent tranches only through the declared
  pyramiding policy.
- Strategy decisions use completed end-of-day information, so a signal cannot
  execute at the same session's close.
- Initial cash equals initial capital; realized proceeds become available after
  the simulated exit without modeling exchange settlement delay.
- No risk-free return is earned on idle cash; the Sharpe risk-free rate defaults
  to zero unless the metric contract later identifies a trustworthy dated
  source and makes it configurable.
- The owner keeps completed and failed runs until an explicit later retention or
  deletion feature is specified; this slice does not add sharing or export.
- `market-lot-v1` applies the current 100-share lot to every historical session
  and is disclosed as a counterfactual rather than historical lot evidence.

### Dependencies

- `strategy-signal-v1` for strategy conditions and signal levels.
- `position-sizing-v1` and `market-lot-v1` for quantities and lot handling.
- Accepted daily bars, technical indicators, Vietnamese trading-session data,
  instrument reference data, and corporate-action adjustment status.
- Existing owner authentication and authorization behavior.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every normative financial fixture, 100% of entries, exits,
  quantities, costs, cash balances, equity points, and terminal equity match an
  independently calculated expected result.
- **SC-002**: Look-ahead detection fixtures fail whenever any decision reads a
  value outside its permitted point-in-time boundary, with zero undetected
  future-data reads across the accepted test set.
- **SC-003**: All eight required metrics match independent fixtures within their
  declared decimal precision, and every undefined case is unavailable with the
  expected reason rather than NaN, infinity, or a fabricated zero.
- **SC-004**: Repeating 100 identical frozen-input runs yields identical trades,
  equity series, metrics, versions, and evidence fingerprints in all 100 cases.
- **SC-005**: At least 95% of valid create requests return a run reference within
  one second, and at least 95% of supported runs up to ten years reach a terminal
  state within 60 seconds in the documented reference environment.
- **SC-006**: Cross-owner and unknown-run tests disclose no existence or result
  information in 100% of tested create/read/list/status paths.
- **SC-007**: In ten representative owner-review scenarios, the owner correctly
  identifies the execution timing, binding sizing limit, exit reason, total
  costs, maximum drawdown, and major data limitation in all ten cases.
- **SC-008**: An interrupted or duplicate-delivered run never exposes partial
  data as completed and creates no duplicate trade or equity point in all
  recovery fixtures.

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001–FR-004 | US1 / Scenarios 1–4 | SC-001, SC-004 |
| FR-005–FR-008 | US1 / Scenarios 1–4; US3 / Scenario 1 | SC-001, SC-002, SC-005 |
| FR-009–FR-012 | US1 / Scenario 3; US2 / Scenarios 1–3 | SC-001, SC-003, SC-007 |
| FR-013–FR-017 | US3 / Scenarios 1–3 | SC-004–SC-008 |
| DATA-001–DATA-006 | US1 / Scenarios 2–4; edge cases | SC-001, SC-002, SC-007 |
| DATA-007–DATA-011 | US1 / Scenario 3; US2 / Scenarios 1–3 | SC-001, SC-003 |
| DATA-012–DATA-013 | US1 / Scenario 3; US3 / Scenario 2 | SC-004, SC-007 |
| SEC-001–SEC-004 | US3 / Scenario 3; edge cases | SC-006, SC-008 |
| NFR-001–NFR-005 | US3 / Scenarios 1–3 | SC-005, SC-008 |
