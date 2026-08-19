# Research: Deterministic Stock Screener

**Feature**: `003-stock-screener`
**Status**: Complete for fixture-mode implementation. No new provider evidence
gate is opened by this feature; it inherits Feature 002's open gates G-01 to
G-04 as-is (see R-011).

Format: `Decision / Rationale / Alternatives considered / Risks and
validation`.

## R-001: Module placement

**Decision**: Implement the screener as a new `screener` sub-package inside
the existing `finvera-be` `stock` Spring module
(`stock.domain.screener`, `stock.service.screener` or a `ScreenerService`,
`stock.dto` additions, and a new controller class in `stock.controller`) —
**not** a new top-level Spring module and **not** a new deployable service.

**Rationale**: Every aggregate the screener reads — `equity_profile`,
`sector_reference`, `equity_daily_bar`, `technical_indicator_result`,
`fundamental_summary`, `valuation_assessment` — is already owned by `stock`.
Feature 002's own Complexity Tracking justified creating `stock` as separate
from `market` because the two own *different* aggregates with different
providers and refresh rates. That reasoning does not apply here: the screener
introduces no new aggregate, no new provider, and no new write path. It is a
pure read-side composition over data `stock` already owns and already
computes with its own versioned engines. Constitution Principle VIII
(Modular Simplicity) requires the least complex design that still satisfies
correctness — a new module here would only be complexity for its own sake.

**Alternatives considered**:
- A new top-level `screener` module — rejected; would duplicate repository
  access to `stock`'s own tables across a module boundary for no ownership
  benefit, and would need a published cross-module interface (ADR-0007
  precedent) for data `stock` already owns internally.
- Extending `market` — rejected for the same reason Feature 002 rejected it:
  `market` does not own any of the data the screener reads.

**Risks/validation**: If a future feature (e.g., MVP-4 Strategy/Signal
Engine) needs the same filter-evaluation primitives against non-`stock` data,
revisit this placement then. Nothing here blocks extracting a shared library
later.

## R-002: Screening execution strategy

**Decision**: Evaluate a screen in two passes, with **every filter —
including Market and Price — evaluated by the same `screener-v1` Java domain
engine**, never by a hand-written SQL predicate. Only the *fetch* boundary is
staged in two passes, not the filter logic itself:

1. **Pass 1 — bulk-fetch the candidate universe.** One bounded query fetches
   the current `equity_profile` row for every `LISTED` instrument (research
   R-007), and one further bulk query fetches the most recent 21 current
   `equity_daily_bar` rows **per instrument** (not a calendar-date window —
   see the amendment below) for the whole universe, serving both the
   "latest session" fields Price/Market need and the Breakout 20-session
   lookback in one fetch. `screener-v1` then evaluates every selected
   Market and Price filter (including the market-cap formula, latest close
   × `shares_outstanding`) against this set in Java, producing the pass-1
   candidate instrument-id set. No SQL expression duplicates a filter
   formula.
2. **Pass 2 — bulk-fetch engine-derived data for the narrowed set only.**
   Technical and Fundamental filters read one bulk repository fetch per
   required table (`technical_indicator_result` + `technical_indicator_value`,
   `fundamental_summary` + `fundamental_summary_metric`,
   `valuation_assessment` + `valuation_metric`), scoped to exactly the
   pass-1 candidate ids, and **only for the categories the request actually
   selected** — never one query per stock, and never fetched for a category
   the owner did not filter on. Breakout/Trend (R-003) reuse the bar/
   indicator data pass 1/pass 2 already fetched; they read no separate
   table of their own.

**Amendment (2026-08-19, discovered during implementation):** the first
implementation bounded the pass-1 bar fetch by a fixed calendar window
("current bars from the last 90 days"), reasoning that it was cheap and
`LocalDate.now()`-relative. `ScreenerFailureTests`
(`aFullyEvaluatedCategoryThatSimplyMatchesNothingIsCurrentNotUnavailable`)
caught the real defect this caused: a stock whose last accepted session is
older than the window — a suspended or thinly-traded stock, or simply a
fixture seeded with an older `tradingDate` — silently has no candidate row
at all in Price/Market evaluation, which is indistinguishable from that
stock never having existed. That violates S-4 (a value must be *excluded
with a reason*, never silently absent) and is inconsistent with Feature
002's own stock detail page, which still shows a stale last-accepted price
with its true freshness disclosed rather than nothing. The fix,
`EquityDailyBarRepository.findLatestNCurrentByInstrumentIdIn`, fetches the
most recent 21 current bars **per instrument** via a native
`ROW_NUMBER() OVER (PARTITION BY instrument_id ...)` query (JPQL has no
per-group "top N" construct) — bounded by instrument count and a fixed bar
count per instrument, never by how long ago the instrument last traded.

**Rationale**: This mirrors the "bounded, paginated access" performance
guidance in `docs/PROJECT_CONTEXT.md`/SRS §36.1 without introducing a new
denormalized read table. Narrowing with cheap SQL predicates before doing
richer in-memory evaluation keeps the 95%-within-5-seconds baseline (NFR-001)
achievable at the Vietnamese-market scale (low thousands of instruments)
without a new cache or search index. Every filter still reads only
already-accepted, current-revision facts (DATA-001); no filter triggers a
provider call or a recalculation (FR-007).

**Alternatives considered**:
- One large SQL query joining every table — rejected for this MVP: it would
  require hand-written SQL duplicating each engine's applicability/precision
  rules (MetricApplicability three-state logic, BigDecimal-safe comparisons)
  in two places (SQL and Java), which is exactly the kind of parallel
  authoritative logic Constitution Principle I warns against ("every result
  ... calculated by deterministic, versioned code", not scattered across a
  query and a domain class). Keeping filter evaluation in the same Java
  domain layer that already owns `MetricApplicability` semantics is simpler
  and safer, even if marginally more data crosses the JDBC boundary.
- A precomputed screener cache/materialized view refreshed on ingestion —
  rejected as unjustified complexity (Constitution Principle VIII, and
  Architecture §11 "Redis as required state" is deliberately absent); revisit
  only if a measured NFR-001 violation demonstrates the need, with an ADR.

**Risks/validation**: If the supported universe grows materially beyond the
scale this reasoning assumes, add a covering index or narrow the in-memory
step further before considering a cache. `StockDetailPerformanceTests`-style
smoke tests (T-numbered in `tasks.md`) validate the 5-second baseline against
a representative fixture universe.

## R-003: Breakout and Trend derivation (new `screener-v1` rules)

**Decision**: Feature 002's `technical-indicators-v1` contract defines no
breakout or trend indicator. This feature defines two new, versioned,
deterministic rules — published in
[contracts/screener-v1.md](contracts/screener-v1.md) as part of the screener
engine, not as new rows in `technical_indicator_result` — computed only from
already-accepted `equity_daily_bar` rows and existing indicator values:

- **Trend direction** (`UPTREND` / `DOWNTREND` / `SIDEWAYS` / unavailable):
  derived purely from the ordering of the stock's current `MA20`, `MA50`, and
  `MA200` values (already computed and persisted by Feature 002).
  `UPTREND` when `MA20 > MA50 > MA200`; `DOWNTREND` when
  `MA20 < MA50 < MA200`; otherwise `SIDEWAYS`. Unavailable, with the same
  `INSUFFICIENT_HISTORY` reason, when any of the three is unavailable — MA200
  has the deepest lookback, so it is normally the limiting indicator.
- **Breakout condition** (`BREAKOUT_UP` / `BREAKOUT_DOWN` / `NONE` /
  unavailable): the latest accepted session's close compared against the
  highest high / lowest low of the prior 20 accepted sessions (a fixed
  20-session lookback, the same window `AVG_VOLUME20`/`RELATIVE_VOLUME`
  already use, for consistency and so the same minimum-history threshold
  applies). `BREAKOUT_UP` when the latest close exceeds the highest high of
  the preceding 20 sessions; `BREAKOUT_DOWN` when it is below the lowest low
  of the preceding 20 sessions; `NONE` otherwise. Unavailable, with
  `INSUFFICIENT_HISTORY`, when fewer than 21 accepted sessions exist.

**Rationale**: Both formulas are standard, widely-understood technical
analysis conventions, expressed with values Feature 002 already computes or
already persists as raw bars — no new provider data, no new ingestion, no
new persisted indicator table. Keeping the 20-session window aligned with
the existing volume indicators avoids introducing a third distinct lookback
convention into the same feature set.

**Alternatives considered**:
- Extending `technical-indicators-v1` (Feature 002's contract) with two new
  indicator codes and persisting them in `technical_indicator_result` —
  rejected for this feature: it would require touching Feature 002's
  already-shipped, already-tested contract and migration for a capability
  only the screener needs today. If a future feature (stock detail page,
  strategy conditions) needs breakout/trend as a persisted, displayed
  indicator, promoting this rule into `technical-indicators-v1` v2 is the
  right time to revisit this decision.
- A configurable lookback window chosen per screen — rejected for MVP-3;
  SRS §13 states no such requirement and it would need its own minimum-history
  policy per chosen window, adding complexity with no stated user need.

**Risks/validation**: `contracts/screener-v1.md` required-test-vector table
must include boundary cases (exactly 20 vs. 21 sessions, a tie at the
lookback high, all three MAs unavailable) so no silent off-by-one or
fabricated match is possible.

## R-004: MACD signal state, MA relationship, and Volume filter semantics

**Decision**:
- **MACD signal state** (`BULLISH` / `BEARISH` / `NEUTRAL` / unavailable):
  read directly from the current `MACD` result's `HISTOGRAM` component
  (already computed by `technical-indicators-v1`). `BULLISH` when
  `HISTOGRAM > 0`; `BEARISH` when `HISTOGRAM < 0`; `NEUTRAL` when
  `HISTOGRAM = 0`; unavailable when the MACD result itself is unavailable.
- **MA relationship**: a comparison between two of `{latest close, MA20,
  MA50, MA200}`, e.g. "price above MA50" or "MA20 above MA50", using the
  operand pairs `contracts/screener-v1.md` enumerates. Each side reads an
  already-accepted/persisted value (latest daily bar close or an MA
  indicator value); the comparison itself introduces no new calculation.
- **Volume filter** (distinct from **Relative volume**, resolved with the
  user on 2026-08-19, recorded in `spec.md` § Resolved Clarifications):
  "Volume" compares against the latest accepted session's raw `volume`
  column on `equity_daily_bar` (not an indicator result); "Relative volume"
  continues to use Feature 002's existing `RELATIVE_VOLUME` indicator result
  unchanged.

**Rationale**: All three filters are direct reads or simple comparisons over
values Feature 002 already computes and persists; none requires new
calculation logic beyond a comparison operator, keeping FR-004's "MUST NOT
recompute an indicator independently" constraint intact.

**Alternatives considered**: none material — these are direct field reads,
not open design questions once the two clarified points above were resolved.

## R-005: Fundamental "growth" filters — a discovered gap

**Decision**: While researching where "revenue growth" and "earnings growth"
would be read from, this research found that Feature 002's
`fundamental-summary-v1` engine (`FundamentalSummaryCalculator.java`)
computes only **`EPS_GROWTH_PERCENT`** as a period-over-period growth metric
(current TTM vs. the prior TTM four quarters back). It does **not** compute a
`REVENUE_GROWTH_PERCENT` or a net-profit/earnings growth metric, even though
Feature 002's own `spec.md` FR-007 listed "revenue growth" as an in-scope
fundamental field — a gap in Feature 002's delivery, discovered here rather
than left silently unaddressed (AGENTS.md: "If discovery changes expected
behavior, the spec and plan MUST be amended before or with the code").

This feature resolves the gap as follows:
- **Earnings growth** filter maps to the existing `EPS_GROWTH_PERCENT`
  summary metric as-is. EPS growth (net profit ÷ share count, period over
  period) is the standard "earnings growth" measure in retail/professional
  screeners; no new calculation is introduced.
- **Revenue growth** filter requires a small, additive extension to
  `FundamentalSummaryCalculator`: add a `REVENUE_GROWTH_PERCENT` summary
  metric computed with the **exact same formula already implemented** for
  `EPS_GROWTH_PERCENT` (current `REVENUE_TTM` vs. the TTM from four quarters
  prior, same 8-quarters-required threshold, same
  `NEGATIVE_OR_ZERO_PRIOR_EPS`-equivalent → `NOT_APPLICABLE`/
  `INSUFFICIENT_HISTORY` handling), applied to `REVENUE_TTM` instead of
  `EPS_TTM`. This is a task in Feature 003's Foundation phase (owned by the
  `stock` module, same file Feature 002 already owns), not something the
  screener computes itself.

**Rationale**: `fundamental_summary_metric.metric_code` is a free-form
`varchar`, not foreign-keyed to `fundamental_metric_catalog` (confirmed by
reading `data-model.md` and `FundamentalSummaryCalculator.java`:
`EPS_GROWTH_PERCENT`, `*_TTM` codes already exist as summary-only codes with
no catalog entry). Adding `REVENUE_GROWTH_PERCENT` needs **no schema
migration** — it is a domain-code change persisted through the same
`fundamental_summary_metric` table Feature 002 already writes via
`FundamentalReportService.persistSummary`. It does not change any existing
metric's value or formula, so it stays under the same `fundamental-summary-v1`
rule version rather than requiring a `v2` (Architecture §4 "Versioned rules"
governs a *formula change* to an existing result, not additive new coverage
— the same precedent by which Feature 002 itself added `EPS_GROWTH_PERCENT`
without a rule-version bump).

**Alternatives considered**:
- Silently reusing `EPS_GROWTH_PERCENT` for a "revenue growth" filter label —
  rejected: factually wrong, and would violate DATA-001 ("MUST NOT derive a
  financial value using logic different from the engine of record" — there
  would be no engine of record for revenue growth at all, only a mislabeled
  substitute).
- Computing revenue growth ad hoc inside the screener from raw
  `fundamental_report_metric` rows — rejected: would duplicate
  `fundamental-summary-v1`'s TTM/prior-TTM logic in a second place, exactly
  the anti-pattern R-002 already rejects for SQL vs. domain-layer logic.
- Deferring "revenue growth" out of scope entirely — rejected: SRS §13 names
  it explicitly and the fix is small, additive, and low-risk against an
  already-shipped engine.

**Risks/validation**: `FundamentalSummaryTests` (Feature 002, extended here)
must gain a `REVENUE_GROWTH_PERCENT` case mirroring every existing
`EPS_GROWTH_PERCENT` boundary (insufficient history, negative/zero prior
revenue, defined case) before this filter is considered implemented.

## R-006: No persisted screen or result state

**Decision**: A screen (its criteria) and a screen result are both fully
transient — request in, response out, nothing written to PostgreSQL. No new
Flyway migration is needed for this feature.

**Rationale**: `spec.md`'s Out of Scope explicitly excludes saved, named, or
scheduled screens for this feature (deferred to a possible future
Portfolio/Watchlist feature, MVP-5). Persisting an unused capability now
would be exactly the kind of speculative infrastructure Constitution
Principle VIII forbids ("New services, ... predictive models, and background
infrastructure MUST have a current requirement").

**Alternatives considered**: persisting every screen execution for audit —
rejected; no requirement calls for it, and Feature 001/002's existing
structured logs already give operational visibility into what was queried
(R-009 below) without a new table.

**Risks/validation**: none — this is the simpler path and is trivially
reversible (a future feature can add persistence without touching this
one's read-only logic).

## R-007: Supported universe for screening

**Decision**: The screener's candidate universe is exactly the instruments
Feature 001/002 already treat as supported and currently listed: active
`market_instrument` rows with a current `equity_profile` row whose
`listing_status = 'LISTED'`. Suspended, halted, delisted, and unknown-status
instruments are excluded from every screen outright (not shown as
"excluded with a reason" per stock — they are not a candidate at all, since
screening a non-tradable instrument has no decision-support value).

**Rationale**: Reuses the existing reference-data listing status Feature 002
already models (`equity_profile.listing_status`); introduces no new universe
concept.

**Alternatives considered**: including non-`LISTED` instruments with a
status flag — rejected; SRS §13 describes screening as narrowing "the
supported universe" for investigation, and a halted or delisted stock is not
a candidate for that decision by definition.

## R-008: Screen-level reproducibility (coherence key)

**Decision**: Reuse Feature 002's existing `CoherenceKeys` helper (a
SHA-256-based hash over contributing row ids/revisions, already used by
`StockOverviewService`/`StockChartService`) to compute one coherence key per
screen response, over the exact set of current-revision rows every matched
stock's evaluation actually read. Two identical screens against unchanged
accepted data produce an identical coherence key (verifying FR-010/DATA-004
reproducibility); a changed key signals the underlying data moved between
runs.

**Rationale**: Consistent with the existing convention (Architecture §5
"Caching: `ETag` plus `304`; the validator is derived from the accepted
revision the response was built from") rather than inventing a new
reproducibility mechanism for this feature alone.

**Alternatives considered**: no coherence key, relying only on `SC-005`'s
test-level reproducibility check — rejected; the existing pattern is cheap
to reuse and gives the owner (and automated tests) a cheap equality check
without recomparing full result bodies.

## R-009: API shape — request body, pagination, sorting

**Decision**: `POST /api/v1/screener/executions` (a query operation
expressed as a resource-creation-shaped POST, matching the existing
`/api/v1` convention for operations too structured for query-string
encoding), request body carries the selected filters per category, `sort`
(field + direction, defaulting to `symbol asc`), and `limit`/`offset`
(default `limit=50`, max `limit=200`). Response carries the matched page,
`totalMatchCount`, per-category degraded/excluded disclosure, and the
coherence key (R-008). No filters selected returns the full candidate
universe (R-007), paginated the same way.

**Rationale**: A `GET` with dozens of optional range/enum parameters across
four categories is unwieldy and awkward to validate/version compared to a
typed request body; a POST here does not mutate any stored resource (R-006),
so it is a read operation shaped as a request body, an already-accepted REST
pattern for structured search. Pagination follows
`docs/ARCHITECTURE.md`'s "query historical series through bounded, paginated
access" guidance applied to a potentially-large cross-sectional result set,
even though Feature 002's own `GET /stocks` search endpoint did not need
this (its result set — matching a symbol prefix — is naturally small).

**Alternatives considered**: `GET /api/v1/screener?rsiMin=...&rsiMax=...` —
rejected; four filter categories times several fields each makes query-string
encoding error-prone and hard to evolve without breaking existing clients.

## R-010: Contradictory filter validation

**Decision**: A screen request with an internally contradictory range on
the same field (e.g., `priceMin > priceMax`) is rejected with `HTTP 400`
and a stable `reasonCode` (`INVALID_FILTER_RANGE`) before any query runs,
per RFC 9457 `application/problem+json` (Architecture §5).

**Rationale**: Matches the edge case named in `spec.md` ("must reject or
clearly flag the contradiction rather than silently returning an empty
result") and the existing error-shape convention; a silently-empty result
for a client bug is indistinguishable from a legitimate zero-match screen,
which would violate FR-013's "specific empty-result state... not an error"
guarantee for the *legitimate* empty case.

## R-011: Inherited gates and degraded-category behavior

**Decision**: This feature does not open, close, or re-decide Feature 002's
Phase 6 gates (G-01 fundamentals, G-02 corporate actions, G-03 quotes, G-04
sector reference). Screening against a filter category whose upstream data a
gate still blocks naturally yields honest exclusions (DATA-003) or, if an
entire selected category has zero evaluable candidates because its upstream
table has no accepted rows at all, the response discloses that whole
category as `UNAVAILABLE` (NFR-002) rather than silently returning zero
matches indistinguishable from a legitimate empty result.

**Rationale**: Consistent with `spec.md`'s Assumptions and with Feature 002's
own established gate posture; this feature adds no new provider dependency
of any kind, so it has no gate of its own to open.

## R-012: Fixture strategy

**Decision**: Reuse Feature 002's existing fixture-mode persisted rows
(fixture-mode ingestion already populates `equity_profile`,
`equity_daily_bar`, `technical_indicator_result`, `fundamental_summary`,
`valuation_assessment` in the Testcontainers-backed integration test
database) rather than inventing a parallel screener-only fixture format. New
fixtures needed for this feature are narrow: a small multi-instrument
universe with known, hand-verified cross-sectional values (so a screen's
expected match set can be asserted exactly), plus the boundary cases R-003
names (19/20/21-session breakout history, all-MA-unavailable trend, tied
breakout high).

**Rationale**: Feature 002's ingestion path is the single source of truth
for how these tables get populated; a second, screener-specific fixture
format would risk drifting from what production ingestion actually writes.
