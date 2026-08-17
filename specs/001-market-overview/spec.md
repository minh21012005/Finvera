# Feature Specification: Market Overview

**Feature Directory**: `001-market-overview`  
**Created**: 2026-08-17  
**Status**: Draft — Awaiting Product Review  
**SRS References**: Sections 5.1, 5.3, 36.1, 36.3, 36.5, 47 (MVP-1), 48,
and 54 (criteria 1-2)  
**Input**: User description: "Create the first Finvera feature specification
for the market overview; stop before planning, tasks, or implementation."

## Scope Summary *(mandatory)*

In the initial private version, Finvera's owner needs a concise, trustworthy
view of the Vietnamese equity market before researching individual stocks. This feature enables the owner to open the
market dashboard and understand the latest state of the four initial benchmark
indices, the balance between advancing and declining stocks, and the current
market regime.

The dashboard distinguishes observed market facts from deterministic regime
assessment. Every displayed value identifies when it was observed and whether
it is current, delayed, stale, partial, or unavailable, so users do not mistake
old or incomplete data for a live market condition.

### In Scope

- Current overview for VN-Index, VN30, HNX Index, and UPCOM Index.
- Index value, absolute and percentage change, trading volume, and trading
  value for the applicable market session.
- Consolidated advancing, declining, and unchanged stock counts across HOSE,
  HNX, and UPCOM, with each eligible security counted once.
- Vietnamese market session status and data freshness state.
- Deterministic market regime classification, score, confidence, and supporting
  factors when sufficient input data exists.
- Safe degraded states for partial, stale, corrected, and unavailable market
  data.

### Out of Scope

- Sector performance, sector rotation, and leading/weak stock lists.
- Per-sector, per-index, and per-venue breadth drill-downs beyond the initial
  consolidated market breadth.
- Historical or intraday charts and technical indicator drill-downs.
- Individual stock search, detail, scoring, screening, signals, or alerts.
- News, daily AI briefings, natural-language explanations, or LLM-generated
  market commentary.
- Portfolio, watchlist, journal, personalization, and notification behavior.
- Automated order execution or investment recommendations.
- Multi-user or public delivery, invitations, sharing links, exports, and any
  redistribution of TCBS- or Vnstock-derived data.
- Any trading, account, cash, portfolio, or order API.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Understand the Main Indices (Priority: P1)

As an investor, I want to see the latest state of the four supported Vietnamese
market indices so that I can quickly understand the direction and activity of
the broad market before researching a stock.

**Why this priority**: Index direction and liquidity are the minimum useful
market context and deliver value independently of breadth or regime analysis.

**Independent Test**: Open the market dashboard using a complete snapshot and
verify that all four indices show their value, change, percentage change,
volume, trading value, session status, and as-of time. Repeat with delayed data
and verify that the facts remain visible but are clearly labeled delayed.

**Acceptance Scenarios**:

1. **Given** an accepted snapshot exists for all four supported indices,
   **When** the user opens the dashboard, **Then** each index displays its
   current value, absolute change, percentage change, trading volume, trading
   value, session status, as-of time, and freshness state.
2. **Given** the market is closed or it is a non-trading day, **When** the user
   opens the dashboard, **Then** the latest accepted session data remains
   visible and the dashboard identifies the market as closed and shows the
   snapshot's trading date and as-of time.
3. **Given** an index snapshot is delayed or stale, **When** the user views that
   index, **Then** the dashboard visibly labels its freshness state and does not
   present the value as live/current.
4. **Given** one index is unavailable while others are available, **When** the
   user opens the dashboard, **Then** available indices remain usable and the
   unavailable index shows a specific unavailable state without a fabricated or
   zero value.

---

### User Story 2 - Assess Market Breadth (Priority: P2)

As an investor, I want to compare advancing, declining, and unchanged stocks so
that I can tell whether headline index movement is broadly supported or driven
by a narrow part of the market.

**Why this priority**: Breadth provides important confirmation or contradiction
for index direction but is still useful without the regime assessment.

**Independent Test**: Supply a known consolidated HOSE/HNX/UPCOM eligible-stock
universe with positive, negative, unchanged, and unclassified constituents.
Verify each security is counted once, the displayed counts and coverage
reconcile with the known snapshot, and missing constituent data produces a
partial status rather than misleading complete totals.

**Acceptance Scenarios**:

1. **Given** complete eligible-stock data exists for a market snapshot, **When**
   the user views market breadth, **Then** advancing, declining, and unchanged
   counts are shown together with the eligible total and as-of time.
2. **Given** the headline index is positive but declining stocks outnumber
   advancing stocks, **When** the user views the dashboard, **Then** both facts
   are displayed without converting them into an unsupported recommendation.
3. **Given** one or more eligible stocks cannot be classified, **When** breadth
   is shown, **Then** the dashboard marks breadth as partial and identifies the
   number of unclassified stocks.
4. **Given** no valid breadth snapshot exists, **When** the user opens the
   dashboard, **Then** breadth is shown as unavailable while index snapshots
   remain usable.

---

### User Story 3 - Understand the Market Regime (Priority: P3)

As an investor, I want a transparent classification of the current market
regime so that I can understand the system's quantitative view and the evidence
behind it without treating it as a guaranteed forecast.

**Why this priority**: Regime classification adds decision-support value after
the underlying market facts are trustworthy and visible.

**Independent Test**: Provide fixed, versioned input snapshots for each
supported regime and insufficient-data cases. Verify that the same inputs and
rule version always produce the same label, score, confidence, and factors, and
that insufficient data produces an unavailable assessment with reasons.

**Acceptance Scenarios**:

1. **Given** all required regime inputs meet the approved quality and freshness
   policy, **When** the dashboard displays the assessment, **Then** it shows one
   of `BULL`, `EARLY_BULL`, `SIDEWAYS`, `EARLY_BEAR`, or `BEAR`, plus a 0-100
   regime score, a 0-100 confidence score, the as-of time, rule version, and
   supporting factors.
2. **Given** the owner reloads the same accepted inputs and rule version,
   **When** they view the regime assessment, **Then** they receive the same label, score,
   confidence, and factors.
3. **Given** required inputs are missing, stale beyond policy, or mutually
   inconsistent, **When** the dashboard evaluates the regime, **Then** it shows
   the assessment as unavailable or partial, identifies the reason, and does
   not invent a label or confidence value.
4. **Given** a valid regime is displayed, **When** the user reviews it, **Then**
   the dashboard describes it as a quantitative market assessment rather than
   a prediction, guarantee, or instruction to buy or sell.

### Edge and Failure Cases *(mandatory)*

- The provider publishes duplicate, out-of-order, or corrected snapshots for
  the same index and observation time.
- One index is fresh while another is delayed, stale, or unavailable.
- The market is pre-open, in continuous trading, in an intermission, at close,
  on a holiday, or crossing the local trading-date boundary.
- An index has no valid previous official close, making change calculations
  unavailable.
- Trading volume or value is legitimately zero versus absent or invalid.
- Breadth counts do not reconcile with the eligible universe because of
  suspended, newly listed, or unpriced securities.
- Input values have more precision than the displayed values, or percentage
  rounding could visually contradict absolute direction.
- A correction changes breadth or regime after an earlier snapshot was shown.
- TCBS live facts and Vnstock completed-session history disagree for the same
  subject and effective trading date.
- The regime engine has sufficient index data but insufficient breadth,
  liquidity, volatility, or sector-strength inputs.
- Repeated refreshes occur while no new accepted snapshot is available.
- The TCBS access token expires and the owner has not manually renewed it with
  iOTP.
- An external AI service is unavailable; the non-AI market dashboard must
  continue to work.

## Requirements *(mandatory)*

Requirements describe observable behavior. Accepted IDs are stable and MUST
not be renumbered; removed requirements are deprecated with a reason.

### Functional Requirements

- **FR-001**: The system MUST provide one market overview containing VN-Index,
  VN30, HNX Index, and UPCOM Index.
- **FR-002**: For each supported index, the system MUST display the latest
  accepted index value, absolute change, percentage change, trading volume,
  trading value, trading date, as-of time, market session status, and freshness
  state.
- **FR-003**: Absolute and percentage index change MUST use the previous valid
  official close as the comparison basis; if that basis is unavailable, change
  fields MUST be unavailable rather than inferred.
- **FR-004**: The system MUST distinguish at least `CURRENT`, `DELAYED`, `STALE`,
  `PARTIAL`, and `UNAVAILABLE` data states using user-readable labels.
- **FR-005**: The failure or absence of one index or breadth dataset MUST NOT
  prevent valid datasets from being viewed.
- **FR-006**: The latest accepted trading session MUST remain viewable when the
  market is closed or the current date is not a trading day.
- **FR-007**: The system MUST display one consolidated breadth view containing
  advancing, declining, unchanged, eligible, and unclassified counts across
  HOSE, HNX, and UPCOM when breadth data exists; each eligible security MUST be
  counted once, and VN30 membership MUST NOT duplicate a HOSE-listed security.
- **FR-008**: Breadth classification MUST compare each eligible stock's accepted
  price with its previous valid official close for the same snapshot policy.
- **FR-009**: The system MUST mark breadth as partial when one or more eligible
  stocks cannot be classified and MUST show the unclassified count.
- **FR-010**: When sufficient approved inputs exist, the system MUST classify
  the market as exactly one of `BULL`, `EARLY_BULL`, `SIDEWAYS`, `EARLY_BEAR`,
  or `BEAR`.
- **FR-011**: A market regime assessment MUST include a 0-100 regime score, a
  0-100 confidence score, its as-of time, deterministic rule version, and
  positive, negative, or neutral supporting factors.
- **FR-012**: The system MUST NOT publish a regime label or confidence score
  when minimum input quality or freshness conditions are not met; it MUST show
  the unavailable/partial reason instead.
- **FR-013**: Given identical accepted inputs and the same rule version, the
  market regime result MUST be identical.
- **FR-014**: Regime presentation MUST identify the output as quantitative
  decision support and MUST NOT present it as a guaranteed forecast or a
  buy/sell instruction.
- **FR-015**: When accepted source data is corrected, the overview MUST show the
  corrected facts and any resulting breadth or regime assessment with a new
  as-of/update indication.
- **SEC-001**: While the active source is TCBS iFlash, the overview MUST be
  accessible only to the single configured owner identity; self-registration,
  invitations, shared links, and access by every other identity MUST be denied.
- **SEC-002**: While the active sources are TCBS iFlash and Vnstock, the system MUST NOT
  expose raw provider data, transformed market data, provider credentials, or
  tokens through a public endpoint, export, webhook, or third-party delivery.
- **SEC-003**: The TCBS adapter MUST allow only approved read-only market-data
  operations and MUST NOT invoke trading, account, cash, portfolio, or order
  operations.
- **SEC-004**: Only the authenticated owner may initiate TCBS token renewal.
  The system MAY accept an iOTP transiently for immediate exchange with TCBS,
  but MUST NOT persist, log, generate, reuse, or automatically replay it.
- **SEC-005**: The private deployment MUST reject public ingress and admit only
  the configured owner through a Tailscale tailnet; Tailscale Funnel and direct
  public exposure of frontend/backend ports MUST remain disabled.
- **SEC-006**: Spring Security MUST authenticate the configured local owner with
  an adaptive password hash and a rotated server-side session. The session
  cookie MUST be `Secure`, `HttpOnly`, and `SameSite=Strict`; state-changing
  requests MUST require CSRF validation, and login attempts MUST be rate-limited.

### Data and Financial Semantics

- **DATA-001**: Every index, breadth, and regime dataset MUST retain and expose
  enough provenance to identify its accepted source and observation time.
- **DATA-002**: Market-facing dates and times MUST be interpreted in
  `Asia/Ho_Chi_Minh`; every displayed snapshot MUST include an unambiguous
  trading date and as-of time.
- **DATA-003**: Index level, absolute change, percentage change, volume, and
  trading value MUST declare consistent display units and precision; trading
  value MUST be identified as Vietnamese đồng or a clearly labeled VND scale.
- **DATA-004**: Direction and breadth classification MUST use unrounded source
  values; rounding occurs only for display and MUST NOT reverse the represented
  direction.
- **DATA-005**: Freshness MUST be evaluated against an approved threshold for
  the dataset and market-session state; the resulting freshness state MUST be
  visible to the user.
- **DATA-006**: Duplicate and out-of-order snapshots MUST NOT cause the
  dashboard to regress silently to older market facts.
- **DATA-007**: Zero, missing, invalid, and not-applicable values MUST remain
  distinguishable; missing or invalid financial values MUST NOT be replaced by
  zero.
- **DATA-008**: The consolidated breadth universe and its inclusion/exclusion
  policy MUST be identifiable for each breadth snapshot so its HOSE, HNX, and
  UPCOM securities can be reconciled without duplicates.
- **DATA-009**: Regime assessments MUST retain the exact accepted input snapshot
  references and rule version needed to reproduce the result.
- **DATA-010**: Live TCBS observations and Vnstock historical observations MUST
  retain distinct source identities. A material cross-source conflict MUST be
  surfaced and MUST prevent publication of an affected regime assessment until
  resolved by an approved reconciliation rule.

### Non-Functional Requirements

- **NFR-001**: Under normal operating conditions, at least 95% of dashboard
  visits MUST show a usable market overview within 3 seconds of the user's
  request.
- **NFR-002**: At least 99% of accepted live market updates MUST become visible
  within the approved source-delay policy plus 30 seconds; the UI MUST NOT call
  the feed contractually real-time unless TCBS grants that entitlement.
- **NFR-003**: For a shared as-of snapshot, 100% of displayed index changes,
  breadth totals, freshness labels, and regime inputs MUST be internally
  consistent to their declared precision.
- **NFR-004**: The market overview MUST remain available in a clearly labeled
  degraded state when AI capabilities are unavailable.
- **NFR-005**: Direction, freshness, breadth, confidence, and regime meaning
  MUST be understandable without relying on color alone.
- **NFR-006**: Operational monitoring MUST be able to distinguish source
  unavailability, stale data, invalid snapshots, calculation failure, and
  user-facing delivery failure without exposing provider credentials or private
  user data.
- **NFR-007**: When TCBS authentication expires, accepted facts remain visible
  with their true freshness and a `PROVIDER_AUTH_REQUIRED` reason; the system
  MUST NOT claim live updates until the owner renews the token.

### Key Entities

- **Market Index**: One supported benchmark, its identity, display name,
  exchange/market context, and lifecycle as a supported index.
- **Market Snapshot**: An accepted observation of an index for a trading date
  and as-of time, including value, change basis, liquidity facts, source, and
  freshness state.
- **Market Breadth Snapshot**: Counts of advancing, declining, unchanged,
  eligible, and unclassified stocks for a declared universe and as-of time.
- **Market Session**: The trading date and state used to interpret whether data
  is pre-open, active, interrupted, closed, or on a non-trading day.
- **Market Regime Assessment**: A reproducible classification derived from
  accepted input snapshots, including score, confidence, factors, rule version,
  quality state, and as-of time.
- **Supporting Factor**: A positive, negative, or neutral deterministic factor
  contributing to a regime assessment, with a human-readable description and
  traceable input.
- **Owner Session**: Ephemeral authenticated access for the one configured local
  owner; it is not a general user-registration or multi-user account model.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- This version is a private, personal deployment for exactly one configured
  owner using that owner's TCBS iFlash account and Vnstock's personal,
  non-commercial software license. Public or multi-user delivery
  requires a separately approved provider with display/redistribution rights
  and a new feature/ADR decision.
- Data may be real-time or delayed depending on licensing, but its actual
  freshness is always disclosed and it is never mislabeled as real-time.
- The initial supported indices are fixed to VN-Index, VN30, HNX Index, and
  UPCOM Index.
- The provider's accepted official close and trading-session definitions are
  authoritative only after they pass Finvera's data validation policy.
- Market regime is a descriptive quantitative assessment, not a forecast of
  future returns.
- No LLM or generative AI capability is required to deliver this feature.

### Dependencies

- A TCBS iFlash account, API key, owner-initiated iOTP access, and successful
  provider capability/contract-fixture gate for live market facts.
- A pinned Vnstock version and successful historical bootstrap gate proving
  completed-session OHLCV coverage, provenance, rate limits, and source terms.
- A private owner-only access path with no public ingress.
- A Tailscale tailnet restricted to the owner's approved identities/devices,
  with Serve/private routing only and Funnel disabled.
- An approved Vietnamese exchange calendar and market-session policy.
- Agreed data freshness thresholds for each dataset and session state.
- An approved breadth-universe inclusion/exclusion policy.
- A versioned deterministic market-regime methodology defining required inputs,
  scoring, confidence calibration, minimum data quality, and supporting factors.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In usability validation, at least 90% of target users can identify
  the direction, session status, and as-of time of all four indices within 10
  seconds of opening the dashboard.
- **SC-002**: Across approved reference fixtures, 100% of displayed index facts
  and breadth counts match the accepted source to the declared display
  precision, with no missing value represented as zero.
- **SC-003**: Across versioned regime fixtures, 100% of assessments are
  reproducible from their recorded inputs and rule version, and every published
  assessment contains a valid label, score, confidence, as-of time, and
  supporting factors.
- **SC-004**: Across stale, partial, corrected, out-of-order, and unavailable
  data scenarios, 100% of views show the correct data-quality state and never
  fabricate a fact, breadth count, regime label, or confidence score.
- **SC-005**: At least 95% of normal dashboard visits present a usable overview
  within 3 seconds, and at least 99% of accepted updates appear within the
  approved source-delay policy plus 30 seconds.
- **SC-006**: The P1 and P2 journeys remain usable while all AI capabilities are
  unavailable, with no incorrect AI-related error shown on the market
  dashboard.
- **SC-007**: Accessibility review confirms that 100% of directional,
  freshness, breadth, confidence, and regime states have a non-color indicator.
- **SC-008**: Authorization and deployment tests show that the configured owner
  receives the overview while every other identity and public ingress path are
  denied; no response, log, export, or client bundle contains TCBS credentials,
  tokens, iOTP, or raw TCBS/Vnstock provider payloads.

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001, FR-002 | US1 / Scenario 1 | SC-001, SC-002 |
| FR-003 | US1 / Scenarios 1 and 4 | SC-002, SC-004 |
| FR-004, FR-005 | US1 / Scenarios 3 and 4 | SC-004 |
| FR-006 | US1 / Scenario 2 | SC-001 |
| FR-007, FR-008 | US2 / Scenario 1 | SC-002 |
| FR-009 | US2 / Scenario 3 | SC-004 |
| FR-010, FR-011 | US3 / Scenario 1 | SC-003 |
| FR-012 | US3 / Scenario 3 | SC-004 |
| FR-013 | US3 / Scenario 2 | SC-003 |
| FR-014 | US3 / Scenario 4 | Acceptance review |
| FR-015 | US1 / Scenario 3; edge correction case | SC-004 |
| DATA-001, DATA-002 | US1-US3 | SC-002, SC-003, SC-004 |
| DATA-003, DATA-004 | US1-US2 | SC-002 |
| DATA-005 | US1 / Scenario 3 | SC-004 |
| DATA-006, DATA-007 | Edge and failure cases | SC-002, SC-004 |
| DATA-008 | US2 / Scenarios 1 and 3 | SC-002, SC-004 |
| DATA-009 | US3 / Scenarios 1 and 2 | SC-003 |
| DATA-010 | US3 / Scenario 3; cross-source conflict edge case | SC-003, SC-004 |
| NFR-001, NFR-002 | US1 / Scenario 1 | SC-005 |
| NFR-003 | US1-US3 | SC-002, SC-003 |
| NFR-004 | AI outage edge case | SC-006 |
| NFR-005 | US1-US3 | SC-007 |
| NFR-006 | All failure scenarios | Operational acceptance review |
| NFR-007 | TCBS token-expiry edge case | Contract and degraded-state tests |
| SEC-001–SEC-006 | Owner-only access and TCBS boundary | SC-008; authorization, session/CSRF, deployment, and adapter-negative tests |
