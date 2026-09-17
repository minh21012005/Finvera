# Feature Specification: Configurable Alerts

**Feature Directory**: `032-configurable-alerts`  
**Created**: 2026-09-16  
**Status**: Draft  
**SRS References**: SRS Section 35; SRS-ALR-01  
**Input**: User description: "Implement the next roadmap capability, configurable alerts, through the repository's full SDD workflow."

## Scope Summary *(mandatory)*

This feature lets the authenticated owner define deterministic conditions over
accepted market, strategy, portfolio, and research facts, then receive durable
in-app notifications when those conditions become true. Each alert exposes its
condition, latest evaluation, data time, trigger reason, and delivery state so
the owner can understand why a notification appeared.

Alerts support ongoing research without placing orders or asking an AI model to
decide whether a condition is satisfied. Evaluation is bounded, repeat-safe,
and honest about missing or stale data. A condition produces one notification
per true episode and rearms only after it becomes false, preventing repeated
notifications for an unchanged state.

### In Scope

- Create, list, inspect, enable, disable, and delete owner-scoped alerts.
- Support price threshold, RSI threshold, MACD crossover, moving-average
  crossover, volume spike, breakout, breakdown, market-regime change,
  strategy-signal, portfolio-risk, and new-document conditions.
- Evaluate alerts from accepted, completed facts with declared timestamps,
  source identity, units, and rule versions.
- Deliver durable in-app notifications, list them newest first, expose unread
  counts, and let the owner mark one or all notifications read.
- Expose latest evaluation status, last evaluated fact time, last trigger time,
  stable reason codes, and delivery outcome.
- Bound active alerts, evaluation work, retry attempts, and retained delivery
  attempts while preserving notification truth.

### Out of Scope

- Email, Telegram, SMS, mobile push, web push, or other external delivery
  providers in this feature slice.
- Broker integration, automatic orders, autonomous trading, or guaranteed
  investment outcomes.
- User-authored formulas, arbitrary scripts, compound Boolean expressions, or
  AI-generated alert conditions.
- Intraday tick/order-book alerts or guarantees tighter than the accepted data
  source's declared delay.
- Shared alerts, public alert templates, alert recommendations, or alerts owned
  by another user.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure and Manage Deterministic Alerts (Priority: P1)

As the owner, I want to create and manage bounded alert conditions so that the
system continuously watches facts I care about without requiring me to inspect
each screen manually.

**Why this priority**: A valid, inspectable alert definition is the smallest
useful unit and is required before evaluation or notification delivery.

**Independent Test**: Create one valid alert of each supported type, retrieve
the stored definitions, disable and re-enable one, delete another, and verify
that invalid, excessive, unknown-symbol, and non-owned operations are rejected
without changing another resource.

**Acceptance Scenarios**:

1. **Given** a supported condition and valid owner-scoped target, **When** the
   owner creates an alert, **Then** the system stores one enabled alert with an
   explicit condition summary, evaluation state, and creation time.
2. **Given** an existing alert, **When** the owner disables it, **Then** no new
   evaluations or notifications are produced until it is enabled again.
3. **Given** a disabled alert, **When** the owner enables it, **Then** it becomes
   eligible for a fresh bounded evaluation without replaying old fact events.
4. **Given** an invalid threshold, unsupported target, malformed condition, or
   exceeded active-alert quota, **When** creation is attempted, **Then** it is
   rejected with a stable validation reason and no partial alert is stored.
5. **Given** an unknown or non-owned alert identifier, **When** it is read,
   changed, or deleted, **Then** the response reveals no ownership or existence
   information.

---

### User Story 2 - Receive Accurate In-App Notifications (Priority: P2)

As the owner, I want one clear notification when an alert condition becomes
true so that I can promptly review the supporting facts and decide what to do.

**Why this priority**: Notifications deliver the main ongoing-use value, but
they are only trustworthy once alert definitions and condition semantics are
stable.

**Independent Test**: Feed independently calculated false, true, repeated-true,
false, and true-again fact sequences for every condition family; verify exactly
two notifications, correct evidence, no duplicates, and no trigger from stale
or missing facts.

**Acceptance Scenarios**:

1. **Given** an enabled alert whose latest eligible facts make its condition
   transition from false to true, **When** evaluation completes, **Then** one
   in-app notification is created with the fact time, observed value, threshold
   or transition, source, and rule version.
2. **Given** a condition that remains true across repeated evaluations, **When**
   no new false state has occurred, **Then** no duplicate notification is
   created.
3. **Given** a previously triggered condition that later becomes false, **When**
   it becomes true again on a newer eligible fact, **Then** exactly one new
   notification is created for the new episode.
4. **Given** missing, stale, conflicting, partial, or unsupported input, **When**
   evaluation runs, **Then** the alert records a withheld evaluation with a
   stable reason and sends no notification.
5. **Given** a newly enabled alert, **When** its current state is already true,
   **Then** it may notify once from the latest eligible fact but never replays
   older historical episodes.
6. **Given** a crossover or regime-change condition, **When** only one eligible
   point or state is available, **Then** evaluation is withheld rather than
   inventing a prior state.

---

### User Story 3 - Review and Operate the Notification Inbox (Priority: P3)

As the owner, I want to review notification and evaluation history so that I
can distinguish unread events, delivery failures, disabled alerts, and stale
data without losing the evidence behind prior notifications.

**Why this priority**: An auditable inbox makes alerts usable over time and
provides a safe operational path when evaluation or delivery fails.

**Independent Test**: Generate notifications and withheld evaluations, revisit
them after alert disablement or deletion, mark one and all as read, and verify
pagination, unread counts, evidence immutability, retry bounds, and ownership.

**Acceptance Scenarios**:

1. **Given** delivered notifications, **When** the owner opens the inbox,
   **Then** notifications are ordered newest first and show read state, alert
   snapshot, trigger evidence, and delivery time.
2. **Given** unread notifications, **When** the owner marks one or all as read,
   **Then** the unread count changes idempotently without altering trigger
   evidence.
3. **Given** an alert later changed, disabled, or deleted, **When** an earlier
   notification is revisited, **Then** its condition and evidence snapshot
   remain available and unchanged.
4. **Given** an interrupted evaluation or delivery attempt, **When** recovery
   runs, **Then** work is retried only within the declared limit and never
   creates duplicate notifications.

### Edge and Failure Cases *(mandatory)*

- A target symbol is delisted, suspended, unknown, or has no current accepted
  fact for the condition.
- Data arrives late, out of order, corrected, duplicated, or with a different
  adjustment basis from the condition's required series.
- Threshold equality occurs exactly at the declared decimal precision.
- A crossover has no valid prior point, or a moving-average/volume/breakout
  lookback is incomplete.
- The market is closed, a weekend or holiday occurs, or no newer completed
  session exists.
- Portfolio positions are empty, their required price/risk inputs are partial,
  or the target portfolio is deleted.
- A new document lacks the selected symbol/category association or is later
  superseded.
- The same accepted fact is evaluated concurrently or delivered repeatedly.
- An alert is disabled or deleted while evaluation is already in progress.
- Active-alert, page-size, threshold, lookback, or retained-history bounds are
  reached.
- An unknown or non-owned alert/notification ID is requested.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The owner MUST be able to create, list, inspect, enable, disable,
  and delete only their own alerts.
- **FR-002**: Each alert MUST select exactly one supported condition type and
  MUST expose a human-readable deterministic summary of its target, operator,
  threshold or transition, and applicable window.
- **FR-003**: The system MUST support price-above and price-below conditions in
  VND per share, using inclusive equality at the threshold.
- **FR-004**: The system MUST support RSI-above and RSI-below conditions using
  a threshold from 0 through 100 and the accepted RSI value for the completed
  session.
- **FR-005**: The system MUST support bullish and bearish MACD-line/signal-line
  crossovers and moving-average crossovers using two consecutive eligible
  completed-session observations.
- **FR-006**: The system MUST support a volume-spike condition comparing the
  completed session's volume with a declared multiple of its eligible trailing
  20-session average, excluding the current session from that average.
- **FR-007**: The system MUST support breakout and breakdown conditions when the
  completed session close crosses the prior eligible 20-session high or low,
  excluding the current session from the boundary.
- **FR-008**: The system MUST support transition into a selected published
  market-regime label and MUST require distinct previous and current eligible
  regime assessments.
- **FR-009**: The system MUST support a selected published strategy signal for
  one symbol and trigger only from an accepted triggered signal result.
- **FR-010**: The system MUST support portfolio concentration-above conditions
  for a selected owned portfolio, using the largest position's current-value
  share of total invested position value.
- **FR-011**: The system MUST support a new-document condition for a selected
  symbol and optional document category, triggering once for each newly
  accepted matching document after the alert's evaluation baseline.
- **FR-012**: An enabled condition MUST create at most one notification for one
  true episode or immutable document event and MUST rearm state-based
  conditions only after an eligible false evaluation.
- **FR-013**: Enabling a new or previously disabled alert MUST evaluate only the
  latest eligible fact set and MUST NOT replay older episodes or documents.
- **FR-014**: A disabled or deleted alert MUST produce no new notification;
  deletion MUST preserve previously delivered notification snapshots.
- **FR-015**: The owner MUST be able to list in-app notifications newest first,
  inspect one notification, view an unread count, and mark one or all as read.
- **FR-016**: Alert lists, notification lists, and evaluation history MUST use
  bounded pagination and stable ordering.
- **FR-017**: Each alert MUST expose its enabled state, latest evaluation
  outcome, stable reason, evaluated fact time, evaluated time, last trigger
  time, and last delivery outcome.
- **FR-018**: Evaluation and delivery retries MUST be bounded, repeat-safe, and
  incapable of publishing two notifications for the same alert episode/event.
- **FR-019**: The system MUST enforce a maximum of 100 non-deleted alerts and
  50 enabled alerts per owner.

### Data and Financial Semantics

- **DATA-001**: Every evaluation MUST use accepted source-of-record facts and
  preserve source, observed/effective time, accepted time, unit, adjustment
  basis, and calculation rule version where applicable.
- **DATA-002**: Market and indicator conditions MUST use completed-session facts
  and MUST NOT use a later fact to evaluate an earlier condition boundary.
- **DATA-003**: Monetary values, ratios, percentages, and threshold comparisons
  MUST use declared decimal precision and deterministic rounding; authoritative
  comparisons MUST NOT use binary floating point.
- **DATA-004**: Price thresholds MUST declare VND per share and the selected
  price adjustment basis; values from incompatible bases MUST NOT be compared.
- **DATA-005**: RSI, MACD, moving-average, volume, breakout, strategy, regime,
  and portfolio inputs MUST expose the versioned calculation or classification
  rule that produced them.
- **DATA-006**: Missing, stale, conflicting, partial, inapplicable, and invalid
  facts MUST remain distinct outcomes and MUST NOT be coerced to zero or false.
- **DATA-007**: A correction with a newer accepted time MUST be evaluated as a
  new fact version while deduplication continues to prevent duplicate delivery
  for the same immutable version and episode.
- **DATA-008**: Market-facing dates and session boundaries MUST use
  Asia/Ho_Chi_Minh; stored transport timestamps MUST remain unambiguous UTC
  instants.
- **DATA-009**: Each notification MUST persist an immutable snapshot of the
  alert summary and trigger evidence needed to understand the decision even if
  the source alert is later changed or deleted.
- **DATA-010**: Alert definitions and notifications MUST be retained until the
  owner explicitly deletes the alert or a later approved retention feature is
  introduced; deleting an alert MUST not delete prior notifications.

### Security and Privacy

- **SEC-001**: Authentication and server-side ownership checks MUST protect
  every alert, evaluation, notification, unread-count, and mutation operation.
- **SEC-002**: Unknown and non-owned identifiers MUST produce
  indistinguishable responses and MUST reveal no target, condition, portfolio,
  symbol, threshold, notification, or existence information.
- **SEC-003**: Condition inputs, identifiers, thresholds, operators, lookbacks,
  page sizes, and state transitions MUST be validated and bounded at the public
  trust boundary.
- **SEC-004**: Logs and metrics MUST exclude financial thresholds, portfolio
  values, document contents, full notification evidence, owner identifiers,
  and private target details; bounded types, states, reason codes, counts, and
  durations MAY be recorded.

### Non-Functional Requirements

- **NFR-001**: At least 95% of valid alert create, read, and state-change actions
  MUST become usable within one second under the documented single-owner load.
- **NFR-002**: At least 99% of enabled alerts MUST finish evaluation within 30
  seconds after the relevant accepted fact becomes eligible, excluding the
  source's declared delay.
- **NFR-003**: At least 99% of successfully triggered in-app notifications MUST
  become visible within five seconds after evaluation commits.
- **NFR-004**: Alert management and the notification inbox MUST be keyboard
  operable, readable without color dependence, and expose text for every state,
  reason, threshold, and trigger value.
- **NFR-005**: Operators MUST be able to observe bounded queue age, evaluation
  duration, evaluated/triggered/withheld/failed counts, retry count, and stable
  reasons without exposing sensitive payloads.
- **NFR-006**: Evaluation or delivery failure MUST degrade independently and
  MUST NOT block market, portfolio, research, authentication, or ordinary
  transactional requests.

### Key Entities

- **Alert Definition**: Owner-scoped condition, target, lifecycle state,
  deterministic summary, current episode state, evaluation cursor, and latest
  operational outcome.
- **Alert Evaluation**: One bounded evaluation against a specific immutable
  fact set, with outcome, reason, evidence identity, timing, and retry state.
- **Notification**: Durable owner-scoped in-app delivery containing an immutable
  condition/evidence snapshot, read state, and delivery time.
- **Delivery Attempt**: Bounded attempt to publish one notification through the
  in-app channel, with stable outcome and no sensitive payload in telemetry.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- In-app notification is the only delivery channel in this feature; external
  channels require separate provider, secret, privacy, retry, and contract work.
- Alert conditions are deterministic single-condition definitions rather than
  arbitrary user expressions.
- The current private deployment has one authenticated owner, but ownership is
  modeled and enforced for future multi-owner operation.
- State-based alerts trigger once on the first eligible true evaluation and
  rearm after an eligible false evaluation; event alerts trigger once per
  immutable accepted event.
- Existing notifications remain available after their alert is deleted because
  they are historical owner records.

### Dependencies

- Accepted daily bars, technical indicators, strategy signals, market regime
  assessments, portfolio positions and valuations, and research-document
  metadata from their owning modules.
- Existing owner authentication and server-side ownership behavior.
- Existing source freshness, market-calendar, adjustment-basis, and rule-version
  contracts from market, stock, portfolio, and research features.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The owner can create, inspect, disable, enable, and delete every
  supported alert type in acceptance testing with 100% correct persisted
  summaries and ownership behavior.
- **SC-002**: Across independently calculated false/true/true/false/true
  fixtures for every state-based condition family, exactly two notifications
  are produced with zero duplicates or missed eligible episodes.
- **SC-003**: Across duplicate and concurrently delivered fact fixtures, each
  alert episode or document event produces no more than one notification in
  100% of cases.
- **SC-004**: Every missing, stale, conflicting, partial, unsupported, and
  insufficient-history fixture produces the expected withheld reason and zero
  fabricated notifications.
- **SC-005**: At least 95% of management actions complete within one second,
  99% of eligible alerts evaluate within 30 seconds of accepted-fact
  eligibility, and 99% of committed triggers appear in the inbox within five
  seconds in the documented reference environment.
- **SC-006**: Unknown and cross-owner alert and notification tests disclose no
  existence or private condition information in 100% of tested paths.
- **SC-007**: In ten representative owner-review scenarios, the owner correctly
  identifies the triggering fact, threshold or transition, data time, source,
  rule version, and any withholding reason in all ten cases.
- **SC-008**: An interrupted or retried evaluation/delivery creates no duplicate
  notification and reaches a bounded terminal or recoverable state in all
  recovery fixtures.

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001–FR-002, FR-019 | US1 / Scenarios 1–5 | SC-001, SC-006 |
| FR-003–FR-011 | US1 / Scenario 1; US2 / Scenarios 1 and 6 | SC-001–SC-004 |
| FR-012–FR-014, FR-018 | US2 / Scenarios 1–5; US3 / Scenario 4 | SC-002, SC-003, SC-008 |
| FR-015–FR-017 | US3 / Scenarios 1–3 | SC-001, SC-005, SC-007 |
| DATA-001–DATA-005 | US2 / Scenarios 1 and 6 | SC-002, SC-004, SC-007 |
| DATA-006–DATA-008 | US2 / Scenarios 3–6; edge cases | SC-003, SC-004 |
| DATA-009–DATA-010 | US3 / Scenarios 1–3 | SC-001, SC-007 |
| SEC-001–SEC-004 | US1 / Scenario 5; US3 / all scenarios | SC-006, SC-008 |
| NFR-001–NFR-006 | US1–US3 | SC-005, SC-007, SC-008 |

