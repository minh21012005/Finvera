# Feature Specification: Deterministic Stock Screener

**Feature Directory**: `003-stock-screener`
**Created**: 2026-08-19
**Status**: Draft
**SRS References**: Section 13 (Stock Screener), 36.1 (screening performance
baseline), 47 (MVP-3), 54 (MVP-SC-05), 58 (Requirements Index — Screening)
**SRS Requirement IDs**: SRS-SCR-01, SRS-SCR-02, SRS-NFR-01 (screening
baseline only); MVP-SC-05.
Explicitly deferred: SRS-SCR-03 (natural-language screener, Section 14,
MVP-7).
**Input**: User description: "Deterministic Stock Screener (Feature 3,
realizes SRS-SCR-01 and SRS-SCR-02, SRS section 13, MVP-3). The owner needs
to screen the Vietnamese equity universe already covered by Features 001/002
against configurable, combinable filter criteria (Market, Price, Technical,
Fundamental), and get back a ranked/sortable list of matching stocks with the
evidence that made each stock match, executed entirely by a deterministic
engine reusing Feature 002's already-computed technical/fundamental/valuation
results. Natural-language-to-filter conversion (SRS-SCR-03) is out of scope."

## Scope Summary *(mandatory)*

In the same private single-owner deployment established by
`001-market-overview` and `002-stock-detail-analysis`, Finvera's owner needs
to narrow the supported Vietnamese stock universe down to a short list worth
investigating, by combining structural, price, technical, and fundamental
criteria in one screen, instead of opening every stock's detail page one at a
time.

The screener does not observe or calculate anything new. It is a deterministic
query over facts and results Features 001 and 002 already accept and persist:
instrument/sector reference, daily price bars, `technical_indicator_result`,
`fundamental_summary`, and `valuation_assessment`. It reuses the same
data-provenance and freshness vocabulary (`CURRENT`, `DELAYED`, `STALE`,
`PARTIAL`, `UNAVAILABLE`) and the same owner-only private access model, and it
never fabricates a match or silently drops a stock it cannot honestly
evaluate.

### In Scope

- A single screen combining any number of filters from four categories —
  Market (exchange, sector, market capitalization), Price (minimum price,
  maximum price, price change), Technical (RSI, MACD, moving-average
  relationship, volume, relative volume, breakout, trend), and Fundamental
  (revenue growth, earnings growth, ROE, ROA, P/E, P/B, debt-to-equity) —
  applied together as a single deterministic query.
- Reuse of Feature 002's already-persisted technical, fundamental, and
  valuation results as the sole source for Technical and Fundamental filters;
  no independent recalculation and no live provider call triggered by
  screening.
- A truthful, per-stock reason when a selected filter cannot be evaluated for
  a stock (insufficient technical history, no accepted fundamentals, gated
  data category), instead of a silent pass, silent drop, or fabricated value.
- A sortable result list showing each matching stock's identity and the
  specific values that satisfied every selected filter, with the freshness
  state of the data it was screened against.
- A path from any result row directly into that stock's existing detail page
  (`002-stock-detail-analysis`).
- Safe degraded behavior when Technical or Fundamental upstream data is
  partially or fully gated, consistent with Feature 002's Phase 6 gate
  posture.

### Out of Scope

- Natural-language-to-filter conversion (SRS-SCR-03, Section 14, MVP-7): the
  owner submits structured filters directly in this feature.
- Saving, naming, scheduling, or alerting on a reusable screen definition; a
  screen exists only for the duration of one request. Persisted screens are a
  candidate for the future Portfolio/Watchlist feature (MVP-5) if approved.
- Strategy, signal, backtest, and risk-scenario evaluation (MVP-4).
- Peer comparison and multi-factor stock scoring (already deferred from
  `002-stock-detail-analysis`, SRS Sections 11-12).
- Any timeframe other than the daily timeframe already used by Feature 002's
  technical/fundamental/valuation engines; no intraday or weekly/monthly
  screening.
- Triggering a live provider fetch, re-ingestion, or recalculation as a side
  effect of running a screen.
- Exporting result lists or redistributing TCBS- or Vnstock-derived data.
- Multi-user or public delivery; the same private single-owner deployment
  model as Features 001/002 applies unchanged.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Narrow the Universe by Market and Price (Priority: P1)

As the owner, I want to filter the supported stock universe by exchange,
sector, market capitalization, and price criteria, so that I can quickly
shrink a large universe down to a manageable list without opening each stock
individually.

**Why this priority**: Market and price data are already accepted facts with
no dependency on Feature 002's technical/fundamental/valuation engines having
produced anything, so this is the smallest useful, independently valuable
screening slice.

**Independent Test**: Submit a screen with only Market and Price filters
against a fixture universe with known exchange, sector, market-cap, price, and
change values; verify the result set contains exactly the stocks that satisfy
every selected filter and no others, each showing the values that qualified
it.

**Acceptance Scenarios**:

1. **Given** a fixture universe of supported stocks with known exchange,
   sector, market capitalization, price, and price-change values, **When**
   the owner submits a screen combining an exchange filter, a market-cap
   range, and a price range, **Then** the result set contains exactly the
   stocks satisfying all three filters, each showing the qualifying exchange,
   sector, market cap, and price values and their as-of/freshness state.
2. **Given** no stock in the fixture universe satisfies every selected Market
   and Price filter, **When** the owner runs the screen, **Then** the system
   shows an explicit empty-result state rather than an error or a partial
   match.
3. **Given** a stock's price snapshot is stale or unavailable, **When** a
   Price filter is applied, **Then** that stock is excluded from the result
   with a stated data-quality reason rather than silently passing or failing
   the filter.
4. **Given** a returned result row, **When** the owner selects it, **Then**
   the owner is taken directly to that stock's existing detail page.

---

### User Story 2 - Add Technical Condition Filters (Priority: P2)

As the owner, I want to add technical-condition filters (RSI, MACD,
moving-average relationship, volume, relative volume, breakout, trend) to a
Market/Price screen, so that I can find stocks that are both structurally
relevant and technically interesting, without recomputing indicators myself.

**Why this priority**: Technical filtering adds decision-support value once
Market/Price screening is trustworthy, and depends on Feature 002's technical
engine already having produced results for each candidate stock — a deeper
dependency than P1's raw facts.

**Independent Test**: Supply a fixture universe where some stocks have full
`technical_indicator_result` coverage and others have insufficient history for
one or more indicators; verify a screen combining a Technical filter with a
Market/Price filter returns exactly the stocks meeting every filter, and that
a stock with insufficient history for the selected indicator is excluded with
the missing-bar reason rather than silently matched or dropped.

**Acceptance Scenarios**:

1. **Given** accepted `technical_indicator_result` values exist for the
   fixture universe, **When** the owner adds an RSI range and an
   above-MA50 filter to a Market screen, **Then** the result set contains
   exactly the stocks satisfying every selected filter across both
   categories, each showing the qualifying indicator values, calculation
   window, and as-of time.
2. **Given** a stock's selected indicator is unavailable due to insufficient
   historical bars, **When** the screen runs, **Then** that stock is excluded
   from the result with the same missing-bar reason Feature 002 already
   surfaces on the stock detail page, and other qualifying stocks remain
   unaffected.
3. **Given** the owner reruns the identical screen against unchanged accepted
   inputs, **When** they compare results, **Then** the result set (members
   and reported values) is identical to the prior run.

---

### User Story 3 - Add Fundamental and Valuation Filters (Priority: P3)

As the owner, I want to add fundamental and valuation filters (revenue
growth, earnings growth, ROE, ROA, P/E, P/B, debt-to-equity) to a screen, so
that I can find stocks that are structurally, technically, and fundamentally
interesting in one pass.

**Why this priority**: Fundamental filtering adds the deepest research value
but depends on Feature 002's fundamental-summary and valuation engines, which
have their own reporting-period freshness characteristics and are meaningful
to combine only once Market/Price and Technical screening already work.

**Independent Test**: Supply fixture fundamental-summary and
valuation-assessment data covering complete, withheld, and negative-earnings
cases; verify a screen combining at least one filter from each of the four
categories returns exactly the stocks satisfying all of them, and that a
stock with a withheld valuation or missing fundamental metric is excluded
with a stated reason.

**Acceptance Scenarios**:

1. **Given** accepted `fundamental_summary` and `valuation_assessment` values
   exist for the fixture universe, **When** the owner combines a P/E range
   and an ROE minimum with existing Market/Price/Technical filters, **Then**
   the result set contains exactly the stocks satisfying every selected
   filter across all four categories, each showing the qualifying values and
   the reporting period or as-of date they came from.
2. **Given** a stock's valuation assessment is withheld (insufficient inputs,
   stale fundamentals, or a cross-source conflict), **When** a Fundamental or
   valuation-derived filter is applied, **Then** that stock is excluded from
   the result with the same withheld-reason Feature 002 already surfaces,
   never treated as a pass or a fail.
3. **Given** a stock has negative earnings making P/E not applicable,
   **When** a P/E filter is applied, **Then** that stock is excluded with a
   not-applicable reason distinct from a missing-data reason.

### Edge and Failure Cases *(mandatory)*

- The owner submits a screen with no filters selected (the whole supported
  universe is the result, or the system requires at least one filter — see
  Open Questions).
- The owner selects a filter category whose upstream data is currently fully
  gated (e.g., Fundamental filters before Feature 002's G-01 closes for a
  given data slice); the category degrades honestly rather than silently
  returning zero-confidence matches.
- Two selected filters are mutually exclusive by construction (e.g., a
  minimum price above a maximum price); the system must reject or clearly
  flag the contradiction rather than silently returning an empty result.
- A stock is delisted, suspended, or newly listed with no accepted history
  during screening; it must be excluded consistently, not intermittently.
- The same instrument has a cross-source price or fundamental conflict
  (Feature 002 `SOURCE_CONFLICT`); the affected filter category withholds
  that stock rather than using an unreconciled value.
- A screen executes while accepted technical/fundamental/valuation results
  are being recalculated after a correction or restatement; the result must
  read one coherent, consistent revision, not a mix of old and new values.
- The supported universe is large enough that unindexed filter evaluation
  would be slow; screening must stay within the NFR-001 latency baseline.
- Repeated identical screens produce repeated identical results with no
  side effect (idempotency).
- An unauthenticated request or a request from any identity other than the
  configured owner attempts to run a screen.

## Requirements *(mandatory)*

Requirements describe observable behavior. Accepted IDs are stable and MUST
not be renumbered; removed requirements are deprecated with a reason.

### Functional Requirements

- **FR-001**: The system MUST let the owner combine filters from the Market,
  Price, Technical, and Fundamental categories within one screen request, and
  MUST apply every selected filter as a logical AND across the supported
  stock universe.
- **FR-002**: Market filters MUST include exchange, sector, and
  market-capitalization range.
- **FR-003**: Price filters MUST include minimum price, maximum price, and
  price-change percentage over the same single-trading-day comparison basis
  (previous valid official close) Feature 002's overview already uses; no
  configurable multi-day period is required for this feature.
- **FR-004**: Technical filters MUST include RSI range, MACD signal state,
  price-versus-moving-average relationship (MA20/MA50/MA200), volume,
  relative volume, breakout condition, and trend direction. RSI, MACD,
  MA relationship, and relative volume MUST be evaluated from the latest
  accepted `technical_indicator_result` values Feature 002 already computes
  and persists, with no independent recalculation. Volume MUST be evaluated
  from the latest accepted session's raw volume on the daily bar (not an
  indicator result), kept distinct from the relative-volume filter. Breakout
  and trend direction have no existing Feature 002 indicator; this feature
  MUST define their deterministic derivation (e.g., a lookback-window
  high/low breakout rule and an MA-ordering-based trend rule) as a new,
  versioned rule built only from Feature 002's already-accepted indicator and
  bar data, specified in this feature's `plan.md`/`research.md` and
  `contracts/` before implementation.
- **FR-005**: Fundamental filters MUST include revenue growth, earnings
  growth, ROE, ROA, P/E, P/B, and debt-to-equity, each evaluated from the
  latest accepted `fundamental_summary`/`valuation_assessment` values Feature
  002 already computes and persists; the screener MUST NOT recompute a
  fundamental or valuation metric independently.
- **FR-006**: A stock lacking an accepted value for a filter the owner
  selected MUST be excluded from the result set with a stated reason
  (e.g., insufficient history, no accepted fundamentals, withheld valuation)
  rather than silently passing, silently failing, or being dropped without
  explanation.
- **FR-007**: Screening MUST execute over already-accepted, persisted data
  only; a screen request MUST NOT trigger a live provider call, a new
  ingestion, or a recalculation of technical/fundamental/valuation results.
- **FR-008**: Each result row MUST show, at minimum, the stock's identity
  (symbol, company name) and the specific value(s) that satisfied every
  selected filter, so the owner can see why the stock matched.
- **FR-009**: The result set MUST disclose the as-of/freshness state of the
  data each match was screened against, using the same data-status
  vocabulary Features 001/002 already use.
- **FR-010**: Given identical accepted inputs and an unchanged filter set,
  repeated screen executions MUST return an identical result set (same
  members, same reported matching values).
- **FR-011**: The system MUST let the owner sort the result set by any of the
  filtered numeric fields.
- **FR-012**: The system MUST let the owner open any matched stock directly
  into its existing stock detail page.
- **FR-013**: A screen for which no stock satisfies every selected filter
  MUST show a specific empty-result state, not an error.
- **FR-014**: Screen results MUST be presented as quantitative filtering
  output, not as a ranked investment recommendation, a buy/sell instruction,
  or a guarantee of future performance.

### Data and Financial Semantics

- **DATA-001**: Screening MUST read only already-persisted, accepted facts
  and results from Feature 001/002 tables (instrument/sector reference,
  daily bars, `technical_indicator_result`, `fundamental_summary`,
  `valuation_assessment`); it MUST NOT derive a financial value using logic
  different from the engine of record that produced it.
- **DATA-002**: Numeric filter boundaries, comparisons, and displayed
  matching values MUST use the same declared decimal precision as their
  source result; screening comparisons MUST NOT use binary floating point.
- **DATA-003**: A filter evaluated against a stale, partial, withheld, or
  unavailable upstream value MUST apply the same freshness/applicability
  outcome Feature 002 already assigns to that value (e.g.,
  `INSUFFICIENT_HISTORY`, `MISSING`, `NOT_APPLICABLE`) rather than
  substituting zero, a default, or an inferred value.
- **DATA-004**: A screen result MUST be reproducible from its recorded filter
  definition and the accepted input revisions and rule versions it read,
  mirroring the reproducibility guarantee Feature 002 already provides for
  individual indicator and valuation results.

### Security and Privacy

- **SEC-001**: Screening MUST be accessible only to the single configured
  owner identity, under the same authenticated server-side session and CSRF
  controls established in Features 001/002; no separate authentication path
  may be introduced.
- **SEC-002**: The screener MUST NOT expose raw provider data, provider
  credentials, tokens, or any redistribution of TCBS/Vnstock-derived data
  through its request/response, export, or any third-party delivery path.

### Non-Functional Requirements

- **NFR-001**: At least 95% of screen executions over the supported universe
  MUST return within 5 seconds under normal operating conditions (SRS
  §36.1 screening baseline).
- **NFR-002**: The screener MUST remain usable when the Technical or
  Fundamental data categories are degraded or gated, screening on the
  categories with available data and disclosing which selected category was
  excluded and why, consistent with Feature 002's section-independent
  degradation behavior.
- **NFR-003**: Match, exclusion, and freshness status MUST be understandable
  without relying on color alone.

### Key Entities

- **Screen Criteria**: The owner's transient, unpersisted set of selected
  filters (category, field, operator, value or range) submitted with one
  screen request; not saved or reused across requests in this feature.
- **Screen Result**: The ordered set of matching stocks for one screen
  execution, each with its identity, the value(s) that satisfied every
  selected filter, its overall data freshness state, and the accepted input
  revisions and rule versions the match is reproducible from.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- The screener introduces no new calculation engine; it reads Feature 002's
  already-computed `technical_indicator_result`, `fundamental_summary`, and
  `valuation_assessment` rows as-is, on the same daily timeframe.
- The supported stock universe for screening is exactly the set of
  instruments Features 001/002 already treat as supported (HOSE/HNX/UPCOM
  with accepted reference data); this feature does not expand the universe.
- Filter combination uses logical AND across every selected filter, per SRS
  §13's "combine multiple filters"; OR/grouped boolean logic is not required
  for this feature.
- No saved, named, or scheduled screens exist yet; each screen is defined and
  executed ad hoc within a single request. Persisted/alertable screens are a
  candidate for the future Portfolio/Watchlist feature (MVP-5).
- The live-provider gates Feature 002 defined (G-01 fundamentals, G-02
  corporate actions, G-03 quotes, G-04 sector reference) are not reopened or
  re-decided by this feature; screening against a category whose gate is
  still open naturally yields honest "insufficient/unavailable data"
  exclusions until that gate closes.
- Valuation- and technical-derived filter outcomes are a descriptive
  quantitative screen, not a forecast, ranking of investment quality, or
  personalized recommendation.
- No LLM or generative AI capability is required to deliver this feature
  (natural-language screening is explicitly deferred, SRS-SCR-03).

### Dependencies

- Feature 001's instrument, exchange, and sector reference data.
- Feature 002's persisted daily bars, `technical_indicator_result`,
  `fundamental_summary`, `valuation_assessment` tables, and their existing
  freshness/applicability policy.
- A versioned deterministic screening rule specification (per-filter
  evaluation semantics, operators, comparison precision, and how a
  multi-component indicator like Bollinger Bands or MACD maps to a single
  filter condition) to be defined in this feature's `plan.md`/`research.md`
  and `contracts/`, consistent with Constitution Principle I.
- A new versioned deterministic breakout/trend derivation rule (Clarify
  2026-08-19, see Assumptions), built only from Feature 002's already-accepted
  indicator and daily-bar data.

### Resolved Clarifications (2026-08-19)

- **Breakout/Trend filters**: this feature defines new deterministic,
  versioned rules for breakout and trend direction, built only from Feature
  002's already-accepted indicator and daily-bar data (e.g., a lookback-window
  high/low breakout rule and an MA-ordering-based trend rule) rather than
  waiting for Feature 002 to add first-class indicators. See FR-004; the exact
  formulas are specified in this feature's `plan.md`/`research.md`/
  `contracts/`, not in this spec.
- **Volume filter**: "Volume" compares against the latest accepted session's
  raw volume from the daily bar; "Relative volume" continues to use Feature
  002's existing `RELATIVE_VOLUME` indicator. The two stay distinct, matching
  their separate names in SRS §13. See FR-004.
- **Price-change period**: "Price change" uses the same fixed single-trading-
  day, previous-valid-official-close basis Feature 002's overview already
  computes; no configurable multi-day period is in scope for this feature.
  See FR-003.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A screen combining at least one filter from three different
  categories returns a result set that reconciles exactly with the same
  filters applied directly to the accepted source data, with 0 duplicated or
  silently dropped securities (MVP-SC-05).
- **SC-002**: At least 95% of screen executions over the supported universe
  return within 5 seconds under normal operating conditions.
- **SC-003**: Across approved reference fixtures, 100% of returned matches
  show the value(s) that satisfied each selected filter, matching the
  accepted source to its declared display precision.
- **SC-004**: Across fixtures covering insufficient-history, withheld,
  stale, and not-applicable cases, 100% of excluded stocks show a stated
  reason rather than a silent drop or a fabricated pass/fail.
- **SC-005**: Across repeated executions with unchanged accepted inputs and
  an unchanged filter set, 100% of result sets are identical (members and
  reported values).
- **SC-006**: Authorization tests show that only the configured owner can
  execute a screen; every other identity and public ingress path is denied,
  and no response, log, or export contains TCBS/Vnstock credentials, tokens,
  or raw provider payloads.

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001, FR-002, FR-003 | US1 / Scenario 1 | SC-001, SC-003 |
| FR-013 | US1 / Scenario 2 | SC-004 |
| FR-006 | US1 / Scenario 3; US2 / Scenario 2; US3 / Scenarios 2-3 | SC-004 |
| FR-012 | US1 / Scenario 4 | Acceptance review |
| FR-004 | US2 / Scenario 1 | SC-001, SC-003 |
| FR-010 | US2 / Scenario 3; US3 (reproducibility) | SC-005 |
| FR-005 | US3 / Scenario 1 | SC-001, SC-003 |
| FR-007, DATA-001 | All stories; edge cases | SC-001, SC-003 |
| FR-008, FR-009 | US1-US3 | SC-003, SC-004 |
| FR-011 | US1-US3 | Acceptance review |
| FR-014 | US1-US3 | Acceptance review |
| DATA-002 | US1-US3 | SC-003 |
| DATA-003 | US1 / Scenario 3; US2 / Scenario 2; US3 / Scenarios 2-3 | SC-004 |
| DATA-004 | US2 / Scenario 3; edge cases | SC-005 |
| SEC-001, SEC-002 | Owner-only access edge case | SC-006 |
| NFR-001 | Large-universe edge case | SC-002 |
| NFR-002 | Gated-category edge case | SC-004 |
| NFR-003 | US1-US3 | Accessibility review |
