# Feature Specification: Deterministic Position Sizing

**Feature Directory**: `030-deterministic-position-sizing`

**Created**: 2026-09-12

**Status**: Draft

**SRS References**: Section 4.1 (protected user resources), Section 18
(Position Sizing), Section 36 (non-functional requirements), SRS-RSK-02,
SRS-PF-01, SRS-PF-02, SRS-NFR-03, SRS-NFR-04, SRS-NFR-06

**Input**: The owner wants Finvera to calculate a transparent, deterministic
position size from available capital, maximum acceptable trade risk, entry and
stop prices, portfolio exposure, and applicable Vietnamese market constraints.
The calculation will become the sizing contract reused by historical
backtesting.

## Scope Summary *(mandatory)*

An entry signal does not tell the owner how many shares can be considered
without exceeding a chosen loss budget or portfolio limit. Doing this manually
also makes it easy to omit affordability, existing exposure, transaction-cost,
or market-lot constraints and to obtain a different answer from the same
inputs.

This feature provides a deterministic long-position sizing scenario for a
supported Vietnamese equity. The owner supplies or selects the financial
inputs, and the system returns the greatest permitted whole-lot quantity after
applying every declared constraint. The result exposes the risk budget,
per-share loss, capital required, estimated maximum loss, resulting exposure,
binding constraint, rounding, source, freshness, and all assumptions so the
owner can independently reproduce it.

The output is decision support rather than an instruction to trade. When an
input is invalid, stale, missing, internally inconsistent, or leaves less than
one permitted lot, the system withholds a positive quantity and gives a stable,
specific reason instead of silently weakening a constraint.

### In Scope

- Calculate a long-equity position size for one supported HOSE, HNX, or UPCOM
  symbol at a time.
- Offer both a standalone manual calculator and an optional portfolio-linked
  mode; both modes use the same sizing engine and financial contract.
- Accept an explicit entry price, stop price, available capital, maximum trade
  risk, and applicable position/exposure limits.
- Incorporate declared acquisition, exit, tax, fee, and slippage assumptions
  wherever they affect affordability or loss at the stop.
- Apply available-capital, risk-budget, existing-symbol-exposure, and total
  deployment caps, identify every binding cap, then apply the market-lot floor.
- Round only downward to a valid whole-lot quantity and show the quantity lost
  to rounding.
- Reuse accepted portfolio balances and exposures when a portfolio is selected,
  subject to the unresolved interaction choice below.
- Show a complete calculation breakdown, data provenance, timestamps,
  units, currency, rule version, warnings, and withholding reasons.
- Expose one versioned sizing contract for later reuse by backtesting.

### Out of Scope

- Placing, routing, scheduling, or monitoring an order.
- A buy/sell recommendation, guaranteed outcome, or personalized risk tolerance.
- Short selling, margin, derivatives, leverage, pyramiding, or multi-leg trades.
- Portfolio optimization or allocation across multiple candidate symbols.
- Automatically choosing the owner's risk tolerance, entry, or stop price.
- Automatically deriving an entry or stop from an AI answer.
- Persisting, naming, comparing, or sharing sizing scenarios in the first slice.
- Backtesting, alerts, investment-journal entries, or broker integration.
- Odd-lot execution unless a later versioned market-rule contract explicitly
  adds it.

## Clarifications

### Session 2026-09-12

- Q: Which interaction modes must the first release support? → A: Both a
  standalone manual calculator and an optional portfolio-linked mode, using the
  same sizing engine and financial contract.
- Q: How may the owner declare maximum trade risk? → A: The owner chooses
  exactly one of a fixed VND amount or a percentage of capital for each
  scenario. In portfolio-linked mode, the percentage applies to total portfolio
  value while cash remains a separate affordability constraint.
- Q: Are symbol-concentration and total-deployment exposure limits required? →
  A: They are optional, have no hidden defaults, and each omitted limit must be
  identified as `NOT_APPLIED`; risk budget and affordability remain mandatory,
  as do market-lot validation and final floor rounding.
- Q: How must fees, taxes, and slippage be handled? → A: The owner must either
  declare each applicable cost assumption or explicitly choose to exclude all
  costs. Blank cost inputs are invalid, and exclusion must be disclosed in the
  result.
- Q: How may entry and stop prices be supplied? → A: The owner may enter them
  manually or explicitly import them from a current Feature 004 signal.
  Imported values retain source and as-of time and require owner confirmation
  before calculation.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Calculate a Reproducible Position Size (Priority: P1)

As the owner, I want to enter a proposed trade and my explicit limits so that I
can see the largest whole-lot quantity that respects all of them.

**Why this priority**: This is the smallest useful position-sizing capability
and establishes the deterministic contract required by backtesting.

**Independent Test**: Enter independently computed fixtures covering a normal
result, each individual binding constraint, an exact-lot boundary, and a
sub-lot result; verify the quantity and every intermediate value match the
fixture and the same inputs always reproduce the same result.

**Acceptance Scenarios**:

1. **Given** valid long-trade inputs and sufficient capital, **When** the owner
   calculates the scenario, **Then** the system returns the greatest permitted
   whole-lot quantity and a complete reproducible breakdown.
2. **Given** the risk budget permits fewer shares than the capital and exposure
   limits, **When** the scenario is calculated, **Then** risk is identified as
   the binding constraint and the final quantity does not exceed the budget.
3. **Given** multiple limits permit the same lowest quantity, **When** the
   scenario is calculated, **Then** all tied binding constraints are shown.
4. **Given** the unrounded permitted quantity falls between market lots,
   **When** the scenario is calculated, **Then** it is rounded down and both the
   unrounded limit and discarded remainder are disclosed.
5. **Given** the result is below one permitted lot, **When** the scenario is
   calculated, **Then** no positive suggestion is published and the precise
   withholding reason is shown.
6. **Given** the owner omits an optional exposure limit, **When** the scenario
   is calculated, **Then** that constraint is shown as `NOT_APPLIED`, no default
   is silently substituted, and the result still applies mandatory risk,
   affordability constraints and market-lot floor rule.

---

### User Story 2 - Size Against an Existing Portfolio (Priority: P2)

As the owner, I want sizing to account for my actual cash and existing exposure
so that a mathematically affordable trade cannot silently breach my declared
portfolio limits.

**Why this priority**: Feature 005 now supplies the dependency that originally
blocked position sizing, and portfolio-aware caps prevent a risk-only formula
from overstating the usable quantity.

**Independent Test**: Use portfolios with no holding, an existing holding in
the same symbol, insufficient cash, and partial/missing market coverage; verify
that accepted owner-scoped portfolio facts constrain the result and that
unavailable required facts withhold it.

**Acceptance Scenarios**:

1. **Given** an owned portfolio with accepted cash and position values,
   **When** it is used for a scenario, **Then** the calculation identifies the
   portfolio source and as-of time and includes current symbol exposure.
2. **Given** the proposed position would breach the declared maximum symbol
   concentration, **When** the scenario is calculated, **Then** the quantity is
   capped below that limit after lot rounding and the exposure cap is shown as
   binding.
3. **Given** portfolio cash is lower than the risk-derived capital requirement,
   **When** the scenario is calculated, **Then** available cash caps the result.
4. **Given** a portfolio identifier belonging to another owner, **When** it is
   requested, **Then** the response does not disclose whether that portfolio
   exists.
5. **Given** a required portfolio value is missing, stale, or conflicting,
   **When** the scenario is calculated, **Then** no portfolio-aware positive
   quantity is published and the affected input is identified.

---

### User Story 3 - Understand and Reuse the Sizing Decision (Priority: P3)

As the owner, I want to understand why the quantity was selected so that I can
audit the scenario and later compare it with a backtest using the same rules.

**Why this priority**: A number without its assumptions is unsafe and cannot
serve as a stable dependency for backtesting.

**Independent Test**: Review successful and withheld scenarios and verify that
every input, assumption, intermediate value, cap, timestamp, warning, and rule
version needed to independently reproduce the outcome is present and readable.

**Acceptance Scenarios**:

1. **Given** a completed scenario, **When** the owner opens its breakdown,
   **Then** every supplied and derived value is shown with a label, unit, source,
   and applicable as-of time.
2. **Given** non-zero cost or slippage assumptions, **When** the scenario is
   calculated, **Then** their effects on affordability and estimated stop loss
   are separately visible.
3. **Given** zero cost assumptions, **When** the result is displayed, **Then**
   the interface accepts them only after the owner explicitly chooses to
   exclude costs and states that exclusion rather than implying no costs exist.
4. **Given** one or more cost inputs are blank and the owner has not chosen to
   exclude costs, **When** calculation is requested, **Then** the scenario is
   rejected and no quantity is published.
4. **Given** the same resolved inputs and sizing-rule version are evaluated
   through the public calculator workflow and directly through the reusable
   engine, **When** their outputs are compared, **Then** every financial field
   is identical.

### Edge and Failure Cases *(mandatory)*

- Available capital or maximum risk is zero, negative, missing, or above an
  accepted input bound.
- Entry price is zero/negative, stop price is equal to or above entry for a long
  scenario, or either value has unsupported precision.
- Declared costs make loss per share zero/negative or make one lot unaffordable.
- Cost inputs are incomplete and explicit cost exclusion was not selected.
- A percentage is outside its accepted range, or an amount-based risk budget
  conflicts with a percentage-based value supplied for the same scenario.
- One or both optional exposure limits are omitted; omission is distinct from a
  zero limit and must not be replaced by a default.
- Existing symbol exposure already equals or exceeds its declared cap.
- Portfolio total value is zero/negative, portfolio cash is negative, or the
  ledger and accepted prices cannot produce a complete current state.
- The symbol is unsupported, inactive, has no accepted venue, or has an unknown
  market-lot rule.
- Accepted market or portfolio facts are stale, corrected, or conflicting.
- Very large decimal inputs would overflow supported precision.
- Two constraints tie, or rounding down changes which constraint appears most
  restrictive.
- A repeated identical calculation returns the same output without creating
  mutable business state.
- The portfolio is deleted or changed while a portfolio-aware calculation is
  being prepared; the result uses one disclosed coherent snapshot or fails.
- An unauthenticated request or another owner's portfolio is supplied.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST calculate the maximum permitted long-equity
  quantity from explicit available capital, maximum trade risk, entry price,
  stop price, declared costs, applicable exposure limits, existing exposure,
  and the accepted market-lot rule.
- **FR-002**: The system MUST independently evaluate risk-budget,
  affordability, plus symbol-exposure and total-deployment constraints when
  supplied, and MUST choose the lowest permitted quantity among those applied
  quantity caps before market-lot rounding.
- **FR-003**: The system MUST round a permitted quantity downward to a whole
  valid market lot and MUST NOT round upward across any constraint.
- **FR-004**: The system MUST return the final quantity, unrounded limits,
  quantity before and after lot rounding, binding constraints, risk budget,
  per-share stop loss including declared costs, required capital, estimated
  maximum loss at the stop, projected gross symbol/deployment exposure on the
  disclosed pre-trade portfolio-value basis, and remaining capital.
- **FR-005**: The system MUST expose every input and assumption used, identify
  whether each value was owner-entered or system-sourced, and identify the
  sizing and market-rule versions.
- **FR-006**: The system MUST withhold a positive quantity and return one or more
  stable reason codes when any required input is invalid, missing, stale,
  conflicting, or cannot support one valid lot.
- **FR-007**: The owner MUST explicitly choose the maximum acceptable trade risk
  for each scenario as exactly one of a fixed VND amount or a percentage of the
  applicable capital base; the system MUST reject a request that supplies both
  or neither and MUST NOT infer personal risk tolerance from AI, portfolio
  history, or prior scenarios.
- **FR-008**: The owner MUST either declare every applicable acquisition fee,
  exit fee, sell tax, entry slippage, and exit slippage assumption or explicitly
  choose to exclude all costs. Blank cost inputs without that explicit choice
  MUST be rejected.
- **FR-009**: The system MUST support a portfolio-aware calculation that uses a
  coherent owner-scoped portfolio snapshot for available cash, total value, and
  existing symbol exposure.
- **FR-010**: The system MUST reject or withhold a portfolio-aware result when a
  required portfolio input lacks a coherent accepted value; it MUST NOT replace
  the missing value with zero.
- **FR-011**: The result MUST be presented as a scenario based on supplied
  assumptions and MUST NOT be phrased as an order instruction, personalized
  recommendation, or guarantee.
- **FR-012**: The first slice MUST perform a calculation without persisting a
  sizing scenario; repeated requests MUST have no portfolio-ledger or order
  side effect.
- **FR-013**: The versioned sizing behavior MUST be reusable by later
  backtesting without duplicating or changing its financial formula.
- **FR-014**: The system MUST support both standalone manual and optional
  portfolio-linked calculations through the same sizing behavior; selecting a
  portfolio changes the source of applicable inputs, not the financial formula.
- **FR-015**: For percentage-based risk in standalone mode, the declared manual
  capital is the risk base. For percentage-based risk in portfolio-linked mode,
  the coherent total portfolio value is the risk base and available cash MUST
  remain an independent affordability constraint.
- **FR-016**: Symbol-concentration and total-deployment limits MUST be optional
  with no hidden defaults. Every result MUST identify each optional limit as
  `APPLIED` or `NOT_APPLIED`, and an omitted limit MUST NOT be interpreted as
  zero or as an implicit percentage.
- **FR-017**: When the owner explicitly excludes costs, the result MUST identify
  every excluded cost category, mark the calculation as excluding transaction
  costs, and warn that affordability and estimated stop loss may be understated.
- **FR-018**: The owner MUST be able to enter entry and stop prices manually or
  explicitly import them from a current Feature 004 signal. Imported values
  MUST retain signal identity, source, and as-of time and MUST require explicit
  confirmation before calculation; absence of a signal MUST NOT prevent manual
  sizing.

### Data and Financial Semantics

- **DATA-001**: Every system-sourced symbol, venue, price, portfolio balance,
  position value, and exposure MUST carry its source and accepted as-of time;
  user-entered values MUST be labeled as such.
- **DATA-002**: Money and prices MUST use VND with declared decimal precision;
  ratios MUST declare whether they are decimal fractions or percentages; binary
  floating point MUST NOT be used for order-sensitive calculations.
- **DATA-003**: Intermediate arithmetic MUST retain sufficient declared
  precision, while final quantity conversion and lot conversion MUST use floor
  rounding only. Display rounding MUST NOT alter the computed result.
- **DATA-004**: The sizing contract MUST define how acquisition costs, exit
  costs, sell tax, and entry/exit slippage affect capital required and estimated
  loss at the stop; no declared cost may be silently ignored and no omitted cost
  may silently become zero.
- **DATA-005**: The system MUST use a versioned market-rule source for the
  symbol's venue, tradable state, and standard lot size and MUST withhold the
  result when that rule is unavailable or not applicable.
- **DATA-006**: A portfolio-aware calculation MUST use one coherent portfolio
  snapshot; cash, total value, existing quantity, market value, and exposure
  MUST NOT be mixed across undisclosed effective times.
- **DATA-007**: Corrected upstream facts MUST affect subsequent calculations;
  a stateless result MUST NOT silently reuse a superseded snapshot.
- **DATA-008**: Missing, stale, conflicting, not-applicable, and zero values MUST
  remain distinct states and MUST never be substituted for one another.
- **DATA-009**: A fixed risk budget MUST be denominated in VND. A percentage
  risk budget MUST identify its capital base and return both the accepted rate
  and derived VND amount in the calculation breakdown.
- **DATA-010**: Manually entered entry/stop values and signal-imported values
  MUST remain distinguishable. Editing an imported value MUST change its source
  to owner-entered while retaining the originating signal only as non-authority
  context.

### Security and Privacy

- **SEC-001**: Only an authenticated owner may use position sizing.
- **SEC-002**: Portfolio-aware calculations MUST enforce portfolio ownership on
  the server and use the same non-disclosing response for missing, deleted, and
  another owner's portfolio.
- **SEC-003**: Inputs and derived results MUST be validated at every trust
  boundary and MUST NOT permit modification of portfolio, transaction, market,
  or order data.
- **SEC-004**: Logs, metrics, and errors MUST NOT contain private portfolio
  values or the complete calculation payload; operational signals MUST use
  bounded reason categories.

### Non-Functional Requirements

- **NFR-001**: At least 95% of valid standalone sizing interactions MUST show a
  result or a specific withholding reason within one second under the declared
  single-owner operating load.
- **NFR-002**: At least 95% of valid portfolio-aware sizing interactions MUST
  show a result or a specific withholding reason within two seconds under the
  declared single-owner operating load.
- **NFR-003**: 100% of versioned financial fixtures and boundary/property tests
  MUST reproduce their independently computed expected quantities and must
  never exceed any declared constraint after rounding.
- **NFR-004**: A sizing calculation failure MUST remain isolated from portfolio,
  market, strategy, AI Analyst, and authentication capabilities.
- **NFR-005**: Quantity, warnings, binding constraints, and unavailable states
  MUST be understandable without relying only on color and usable by keyboard.
- **NFR-006**: Operators MUST be able to observe bounded counts and latency for
  successful, withheld, invalid, unauthorized, and failed calculations without
  exposing financial inputs.

### Key Entities

- **Sizing Scenario Input**: The immutable set of owner-entered and optionally
  system-sourced values evaluated for one calculation, including exactly one
  fixed-VND or percentage-based risk budget. It is not persisted in the first
  slice.
- **Portfolio Sizing Snapshot**: An owner-scoped, coherent read of available
  cash, portfolio value, current symbol position, and exposure used by one
  calculation. It does not replace the portfolio ledger as source of truth.
- **Sizing Result**: The reproducible output containing permitted quantity,
  intermediate limits, binding constraints, financial effects, disclosures,
  reason codes, provenance, timestamps, and rule versions. It is not an order.
- **Sizing Constraint**: A named independently evaluated cap such as risk,
  affordability, exposure, deployment, or market lot applicability.
- **Market Lot Rule**: The accepted, versioned rule that connects a symbol and
  venue to the whole-lot quantity permitted for this calculation.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- The first version covers cash-funded long positions only.
- The owner explicitly supplies risk and exposure limits; Finvera may offer
  neutral input conveniences but does not infer personal suitability. Exposure
  limits may be omitted only with an explicit `NOT_APPLIED` disclosure.
- Zero costs are accepted only through an explicit exclude-costs choice and are
  disclosed with their effect on the result.
- Standard-lot sizing is the safe baseline; odd-lot liquidity and execution are
  outside this version.
- The calculation is stateless, but all inputs and output details are returned
  so the caller can inspect or later attach them to a separate journal feature.

### Dependencies

- Feature 004's accepted strategy/signal levels may be displayed as selectable
  inputs but never silently become the user's chosen entry or stop. The owner
  explicitly imports and confirms those values before calculation.
- Feature 005's owner-scoped portfolio application API supplies coherent cash,
  total value, position, and exposure facts without direct repository access.
- Existing market/reference application APIs supply supported-symbol, venue,
  tradable-state, freshness, and provenance facts.
- Research must establish the applicable Vietnamese standard-lot rules and
  their effective/version dates from authoritative sources before planning.
- A versioned position-sizing financial contract and independently computed
  fixtures are required before production implementation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For 100% of accepted normal and boundary fixtures, owners receive
  the independently reproducible greatest whole-lot quantity that breaches none
  of the declared constraints.
- **SC-002**: For 100% of withheld fixtures, the owner receives no positive
  quantity and sees every reason necessary to correct or understand the input.
- **SC-003**: Across successful scenarios, 100% show all input values, units,
  sources, timestamps where applicable, intermediate limits, binding
  constraints, rounding effects, cost assumptions, and rule versions.
- **SC-004**: Repeating the same scenario against the same accepted snapshot and
  rule version produces an identical result in 100% of reproducibility tests.
- **SC-005**: 100% of authorization tests prevent one owner from using or
  inferring another owner's portfolio values.
- **SC-006**: At least 95% of standalone and portfolio-aware interactions meet
  their one-second and two-second response targets respectively.
- **SC-007**: In an owner review of at least ten representative scenarios, the
  owner can correctly identify the binding constraint and explain why the
  quantity cannot be increased in every case.
- **SC-008**: The public calculator orchestration and direct reusable engine
  invocation produce exactly the same financial fields from the same resolved
  sizing input and rule version, proving the contract is reusable by a later
  backtest.

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001–FR-004, FR-016 | US1 / Scenarios 1–6 | SC-001, SC-003 |
| FR-005–FR-008, FR-015, FR-017–FR-018 | US1 / Scenarios 1–5; US3 / Scenarios 1–4 | SC-001–SC-004 |
| FR-009–FR-010, FR-014 | US2 / Scenarios 1–5 | SC-001, SC-002, SC-005 |
| FR-011–FR-012 | US1 / Scenario 1; US3 / Scenario 1 | SC-003, side-effect verification |
| FR-013 | US3 / Scenario 4 | SC-008 |
| DATA-001–DATA-010 | US1–US3; edge/failure cases | SC-001–SC-004, financial fixture audit |
| SEC-001–SEC-004 | US2 / Scenario 4; edge/failure cases | SC-005, negative authorization and log audit |
| NFR-001–NFR-006 | US1–US3 | SC-001, SC-006, SC-007, operational validation |
