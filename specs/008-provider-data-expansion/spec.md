# Feature Specification: Provider Data Expansion

**Feature Directory**: `008-provider-data-expansion`
**Created**: 2026-08-30
**Status**: Clarified 2026-08-30 (research R-001/R-006 closed both open questions; FR-001 narrowed — see research "Spec amendments")
**SRS References**: Section 9.1 (fundamental metrics), Section 10 (valuation
metrics — EV/EBITDA is listed but never publishable today), Section 6.1
(per-stock overview), the price-limit/suspension line of the data-quality
section, 58 (Requirements Index — Fundamentals, Valuation, Stock)
**SRS Requirement IDs**: SRS-FUN-01, SRS-VAL-01, SRS-STK-01 (extension of
already-realized capabilities; no new SRS capability is claimed).
**Input**: User description: "The 2026-08-30 system review measured that
Finvera maps roughly 13 of the 133+ fields the accepted providers already
return (`docs/REMEDIATION_PLAN.md` E-3). Several deterministic outputs the
product already promises are therefore permanently withheld — most visibly
`EV_EBITDA`, which carries 0.20 of the valuation score's designed weight and has
never once published, and `FREE_CASH_FLOW`, which is catalogued, allowed,
summarised and displayed but has no mapping. Vietnamese price limits (ceiling /
floor) and foreign-ownership room are standard decision inputs the live
provider already streams and nothing consumes. Owner decision (2026-08-30):
handle this as a new feature with the full SDD cycle, not as bug fixes."

## Scope Summary *(mandatory)*

Finvera's deterministic engines were designed against a richer fact set than
the import pipelines actually deliver. The valuation rule expects EBITDA, total
debt and cash to compute enterprise value; the fundamentals summary expects
free cash flow; the overview knows nothing of the day's price limits. Each of
those facts is available from a provider Finvera has already accepted under an
approved research gate — they were simply never mapped, and in one case
(`balance_sheet`) never probed.

This feature closes that gap in evidence order: probe what the provider
actually returns, record the confirmed schema, map only confirmed fields into
the existing catalog and contracts, and let the existing engines publish what
they were always specified to publish. It adds no new calculation rule and no
new provider; it makes already-approved rules reachable.

The owner sees the difference as: valuation assessments that include EV/EBITDA
(and disclose when they cannot), a fundamentals section whose free-cash-flow
row is a number rather than "not reported", and an overview that shows the
session's ceiling/floor and foreign room next to the price.

### In Scope

- Probing and recording the Vnstock/KBS `balance_sheet` dataset (never probed)
  — probed 2026-08-30: unavailable (research R-001); no balance-sheet mapping.
- Deriving `EBITDA` for the `EBITDA_TTM` → `EV_EBITDA` chain from confirmed
  provider fields, under a recorded, versioned derivation rule.
- Mapping `FREE_CASH_FLOW` from confirmed cash-flow items under a recorded rule.
- Surfacing ceiling price, floor price and foreign-ownership room from the
  live TCBS overlay and the per-stock quote snapshot on the stock overview.
- Extending the metric catalog, provider contracts and import packages so
  every new fact carries the same provenance, unit and precision guarantees
  as existing facts.

### Out of Scope

- Any change to a calculation formula or rule version (`valuation-v1`,
  `fundamental-summary-v1`, `technical-indicators-v1` are unchanged; a metric
  that becomes available flows through their existing logic).
- Order-book depth (`bidPrice01-03` / `offerPrice01-03`), `matchQtty`, and the
  broader KBS ratio family (`beta`, `ps_ratio`, liquidity/efficiency ratios).
  Constitution VIII: no user story needs them yet. They are recorded in
  `research.md` as confirmed-available for a future feature.
- New screener or strategy filters built on the new facts (Feature 003/004
  amendments would follow, separately, if wanted).
- Any provider not already accepted under Feature 001/002 gates.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Valuation publishes with EV/EBITDA (Priority: P1)

As the owner, when I open a stock whose provider reports a positive EBITDA and a
balance sheet, I want the valuation assessment to include the EV/EBITDA metric
and its percentile, so the score reflects the full designed weight instead of
silently running on 80% of it.

**Why this priority**: `EV_EBITDA` is 0.20 of the valuation score by contract
and has never published. This is the largest single correctness gap the review
found in a number the owner already acts on.

**Independent Test**: import a fundamentals package for one symbol whose probe
evidence contains EBITDA-derivable and balance-sheet items; open its valuation;
`EV_EBITDA` is `DEFINED` with a value, an own-history percentile once ≥500
sessions exist, and an `effectiveWeight`; the disclosure list no longer shows
`MISSING_EBITDA`/`MISSING_EV_INPUTS` for that symbol.

**Acceptance Scenarios**:

1. **Given** a symbol with four consecutive quarterly `EBITDA` facts, **When**
   the fundamentals summary is computed, **Then** `EBITDA_TTM` is `DEFINED`
   and equals their sum; **and** valuation still discloses `EV_EBITDA` as
   `MISSING` / `MISSING_EV_INPUTS` (no balance sheet), never a fabricated EV.
2. **Given** a symbol whose EBITDA-derivation inputs are absent or whose derived
   EBITDA ≤ 0, **When** valuation is computed, **Then** `EV_EBITDA` is `MISSING`
   or `NOT_APPLICABLE` with the existing reason codes and the remaining metrics
   renormalize exactly as today — never a fabricated EBITDA.
3. **Given** a historical session inside the own-history window, **When** the
   history series is built, **Then** the EBITDA/debt/cash used are the values
   observed as of that session (no look-ahead), consistent with valuation-v1
   Basis A.

---

### User Story 2 - Free cash flow is a number (Priority: P2)

As the owner, I want the fundamentals section's free-cash-flow row to show the
provider-confirmed figure with its period and provenance, instead of
"NOT_REPORTED", so cash generation is visible next to earnings.

**Why this priority**: the row already exists in the UI and the catalog; the
gap is purely a missing mapping, so the value-to-effort ratio is high.

**Independent Test**: import a fundamentals package for a symbol whose
cash-flow probe contains operating cash flow and capital-expenditure items;
the fundamentals section shows `FREE_CASH_FLOW = OCF − CapEx` for the latest
accepted period with `DEFINED` applicability.

**Acceptance Scenarios**:

1. **Given** both derivation inputs are `DEFINED` for a period, **When** the
   report is accepted, **Then** `FREE_CASH_FLOW` is `DEFINED` and equals the
   recorded derivation at declared precision.
2. **Given** either input is missing, **When** the report is accepted, **Then**
   `FREE_CASH_FLOW` is `MISSING` with a reason naming the absent input; it is
   never computed from a partial pair.

---

### User Story 3 - Price limits and foreign room on the overview (Priority: P3)

As the owner, I want the stock overview to show the session's ceiling and
floor prices and the remaining foreign-ownership room, so I can see at a
glance whether a move is limit-bound or whether foreign demand is capped.

**Why this priority**: standard Vietnamese trading context the live provider
already streams; the constitution asks price limits to be accounted for
explicitly, and today nothing in the system knows them.

**Independent Test**: with the TCBS live overlay enabled for a symbol, the
overview shows ceiling, floor and foreign room with the quote's timestamp; with
the overlay disabled or the frame absent, those fields are `null` with a
reason, never `0`.

**Acceptance Scenarios**:

1. **Given** an `s|4` reference frame with `ceilPrice`/`floorPrice` and a
   `tickerCommons` snapshot with `room`, **When** the overview is read, **Then**
   the three values are shown with the live quote's `observedAt` and source label.
2. **Given** the last price equals the ceiling (or floor), **When** the overview
   is read, **Then** a textual "at ceiling"/"at floor" cue accompanies the
   direction indicator (colour is never the only carrier).
3. **Given** no live frame for the symbol, **When** the overview is read,
   **Then** ceiling/floor/room are `null` with `LIVE_OVERLAY_UNAVAILABLE`.

### Edge and Failure Cases *(mandatory)*

- A probe returns an `item_id` that is ambiguous (two "revenue" rows exist
  today): the field is **not** mapped; the ambiguity is recorded and the metric
  stays unmapped until resolved.
- Provider unit ambiguity (VND vs thousands vs millions) for balance-sheet
  items: `unitScale` is confirmed against a known symbol's published statement
  before any package is imported; per-share and ratio items never inherit it.
- EBITDA derivation with negative margin or zero revenue: `NOT_APPLICABLE`, and
  `EV_EBITDA` follows valuation-v1's existing `NEGATIVE_OR_ZERO_EBITDA` path.
- A balance-sheet period exists without the matching income-statement period:
  the fundamentals summary treats the missing side as `MISSING`; no cross-period
  substitution.
- Ceiling/floor frames arrive before the day's reference frame, or for a
  suspended symbol: the limit values are held as facts of the session they were
  received in, never carried across a trading-date boundary (the same day-reset
  rule Q-24 introduces for the live quote cache).
- A restated balance sheet supersedes an earlier revision exactly as income
  statements do today (immutable revision chain, `supersedes_id`).
- Live overlay disabled, TCBS auth expired, or Vnstock package absent: every
  new field degrades to `null` + reason; no existing section changes behaviour.
- Re-importing the same package: idempotent (existing `packageSha256` guard).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST publish `EBITDA` (per period) and `EBITDA_TTM`
  as accepted, provenance-carrying facts for any symbol whose provider reports
  an EBITDA margin and net revenue for the same period, and MUST keep
  `EV_EBITDA` withheld with `MISSING_EV_INPUTS` — never an approximated
  enterprise value — until a balance-sheet source is accepted under its own
  gate (research R-001: the accepted provider returns no balance sheet).
- **FR-002**: The system MUST publish `FREE_CASH_FLOW` in the fundamentals
  summary for any accepted period where both derivation inputs are `DEFINED`.
- **FR-003**: The stock overview MUST show ceiling price, floor price and
  foreign-ownership room when the live overlay supplies them, with timestamp
  and source label, and a textual at-limit cue when the last price touches a
  limit.
- **FR-004**: Every newly mapped metric MUST appear in the metric catalog with
  display names in Vietnamese and English, unit, scale and sign policy before
  it can be imported.
- **FR-005**: The owner-operated exporters MUST print a reviewable sample of
  each newly mapped value against the symbol's known published figures before
  import, as the existing fundamentals exporter does for unit scale.

### Data and Financial Semantics

- **DATA-001**: No provider field MAY be mapped until its `item_id`, unit,
  period shape and sample values are recorded from a probe run in
  `research.md`; "similar-looking" fields are never guessed (AGENTS.md).
- **DATA-002**: Derived facts (`EBITDA`, `FREE_CASH_FLOW`) MUST record their
  derivation rule identifier and contributing input metric codes so the value
  is reproducible from accepted inputs.
- **DATA-003**: All new monetary facts are base VND at `numeric(28,6)`; ratios
  and per-share facts never inherit statement `unitScale` (Feature 002
  R-015/T074 rule).
- **DATA-004**: Every new fact carries `source`, `observedAt`, ingestion time
  and reporting period or trading date; live-overlay limit/room facts are tied
  to the trading date of the session they were received in.
- **DATA-005**: A missing derivation input yields `MISSING` with the input
  named; a non-positive derived EBITDA yields `NOT_APPLICABLE`; neither is
  ever `0`.
- **DATA-006**: Historical (Basis A) valuation points MUST use only facts whose
  `observedAt` precedes the session they price (existing look-ahead guard
  applies unchanged to the new inputs).

### Security and Privacy

- **SEC-001**: New provider fields flow only through the existing read-only,
  operation-allowlisted adapters; no new endpoint, credential or outbound host.
- **SEC-002**: Logs and metrics for the new datasets carry counts, reason codes
  and source labels only — never raw provider payloads.

### Non-Functional Requirements

- **NFR-001**: Enabling the new mappings MUST NOT change the measured latency
  budget of the valuation warmup by more than the additional metric fetch
  itself (no new per-instrument N+1; balance-sheet items ride the existing
  per-report metric fetch).
- **NFR-002**: Every new fact is disposable/reimportable: dropping and
  re-importing the packages reproduces identical accepted rows.
- **NFR-003**: Observability distinguishes "provider lacks the field" from
  "field present but unmapped" from "mapped but rejected" per dataset.

### Key Entities

- **Balance-sheet report metric**: a per-period accepted fact on the existing
  fundamental report, joining the income-statement and ratio metrics under the
  same revision chain; new codes only, no new entity.
- **Derived fundamental metric**: an accepted metric whose value is computed
  from other accepted metrics of the same report under a named rule; carries
  its rule id and input codes.
- **Session price limits**: the ceiling/floor pair and foreign room observed
  for a symbol within one trading session from the live overlay; ephemeral in
  memory like the current session facts, persisted only as part of the
  accepted price observation if the plan decides so.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- The KBS `balance_sheet` dataset follows the same wide `item_id` × period
  shape the other three datasets confirmed in Feature 002 G-01.
- The provider's `ebitda_net_revenue` ratio and `revenue` are per-period and
  same-period, making `EBITDA = margin × revenue` a valid derivation for the
  period; the probe confirms or refutes this before mapping.
- Ceiling/floor from TCBS `s|4` are already base VND (same unit as
  `refPrice`, confirmed by the existing R-015 sanity check).

### Dependencies

- Owner-run probe of `balance_sheet` and a re-run of the cash-flow/ratio probe
  on pinned `vnstock==4.0.6` (owner-only: provider access, credentials).
- Feature 002 valuation-v1 contract (unchanged) and its `ValuationV1`
  engine; Feature 001 TCBS Thesis overlay contract (extended, not replaced).
- Q-23 (`docs/REMEDIATION_PLAN.md`) is already closed: `EV_EBITDA` can no
  longer be misread into `ebitdaTtm` when it enters the allowlist.

### Resolved clarifications (2026-08-30)

- KBS `balance_sheet` returns no rows in the pinned version for every probed
  symbol and period; VCI fails at construction. No balance-sheet field is
  mapped; FR-001 narrowed accordingly (research R-001).
- Session price limits and foreign room are session context, not accepted
  price facts: kept in the live session cache with the Q-24 trading-date
  reset, disclosed `PRICE_LIMITS_UNAVAILABLE` when absent (research R-006).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After a full-universe fundamentals refresh, the share of listed
  symbols whose fundamentals summary has `EBITDA_TTM = DEFINED` is reported,
  and it is non-zero (today: exactly zero); `EV_EBITDA` stays disclosed as
  withheld for 100% of symbols with the reason `MISSING_EV_INPUTS`.
- **SC-002**: For every symbol where `EV_EBITDA` publishes, the value equals an
  independent recomputation from the stored inputs at scale 12 (golden-vector
  test), and the valuation score changes only through the contract's existing
  renormalization.
- **SC-003**: `FREE_CASH_FLOW` is `DEFINED` for every accepted period whose two
  inputs are `DEFINED`, and `MISSING` with a named input otherwise — verified by
  a fixture with each combination.
- **SC-004**: With the live overlay on, the overview shows ceiling/floor/room
  for a subscribed symbol within the existing quote freshness budget; with it
  off, the fields are `null` with a reason and no existing field regresses.
- **SC-005**: Every newly mapped field has a recorded probe sample in
  `research.md` and a catalog entry before its first import (audit check).

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001 | US1 / 1-3 | SC-001, SC-002 |
| FR-002 | US2 / 1-2 | SC-003 |
| FR-003 | US3 / 1-3 | SC-004 |
| FR-004 | US1, US2 | SC-005 |
| FR-005 | US1, US2 | SC-005 (exporter sample printed before import) |
| DATA-001 | all | SC-005 |
| DATA-002 | US1, US2 | SC-002, SC-003 |
| DATA-003 | US1, US2 | SC-002 (unit-scale fixture) |
| DATA-004 | US3 | SC-004 |
| DATA-005 | US1 / 2, US2 / 2 | SC-003 |
| DATA-006 | US1 / 3 | SC-002 (history golden vector) |
| SEC-001, SEC-002 | all | negative tests: no new host/endpoint; log redaction test |
| NFR-001 | US1 | warmup timing comparison before/after |
| NFR-002 | all | re-import idempotency test |
| NFR-003 | all | observability counters per dataset |
