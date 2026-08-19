# Data Model: Deterministic Stock Screener

**Feature**: `003-stock-screener`
**Owner**: `finvera-be` / `stock` module (extended, not a new module — research
R-001)
**System of record**: PostgreSQL (read-only for this feature)
**Migration**: **none**. This feature adds no table, no column, and no
constraint. See "The one additive change" below for the single domain-code
addition it does require.

## Relationship to Features 001/002

This feature is **entirely read-only** over tables Features 001/002 already
created and populate. Nothing here is a new source of truth.

| Existing table | Role for the screener |
|---|---|
| `market_instrument` | Instrument identity and exchange; candidate universe root. |
| `equity_profile` (current row) | Sector, market cap input (`shares_outstanding`), `listing_status` gate (research R-007). |
| `sector_reference` | Sector filter operand and display name. |
| `equity_daily_bar` (current, latest 21 rows per instrument — research R-002 amendment) | Price, price-change basis, raw session volume, and the 21-session window Breakout reads. Fetched by most-recent-21-per-instrument, never by a calendar-date window, so a stale-but-accepted last price is still found. |
| `technical_indicator_result` / `technical_indicator_value` (current rows) | RSI, MACD histogram, MA20/50/200, relative volume — Trend and Breakout are derived from these plus `equity_daily_bar`, not persisted separately (research R-003). |
| `fundamental_summary` / `fundamental_summary_metric` (current rows) | Earnings growth, ROE, ROA, debt-to-equity, and the new revenue growth metric below. |
| `valuation_assessment` / `valuation_metric` (current rows) | P/E, P/B, and the all-or-nothing publishability gate (research R-004). |

No row in any of these tables is written, updated, or superseded by this
feature. Every read targets the same "current revision" partial-unique-index
pattern Feature 002 already established, so no new index is needed either.

## The one additive change: `REVENUE_GROWTH_PERCENT`

Research R-005 found that Feature 002's `fundamental-summary-v1` engine
computes `EPS_GROWTH_PERCENT` but no revenue-growth equivalent, even though
Feature 002's own spec named "revenue growth" as in scope. This feature adds
one new `fundamental_summary_metric` row per instrument, computed by
`FundamentalSummaryCalculator` (owned by `stock`, extended here — not a new
class), with `metric_code = 'REVENUE_GROWTH_PERCENT'`, using the identical
formula and applicability handling already implemented for
`EPS_GROWTH_PERCENT`, sourced from `REVENUE_TTM` instead of `EPS_TTM`.

**Why no migration**: `fundamental_summary_metric.metric_code` is a
free-form `varchar`, not foreign-keyed to `fundamental_metric_catalog` — the
existing `*_TTM` and `EPS_GROWTH_PERCENT` codes already prove this pattern.
Adding a new code is a pure domain-code change persisted through the
already-existing `FundamentalReportService.persistSummary` write path.

**Why the same rule version**: `fundamental-summary-v1` gains new coverage,
not a changed formula for any existing metric — Architecture §4's "a formula
change creates a new rule version" governs changed formulas, not additive
ones (the same precedent under which Feature 002 itself added
`EPS_GROWTH_PERCENT`).

## Transient request/response model (research R-006)

A screen and its result exist only for the duration of one request. Nothing
below is persisted; these are application-layer Java records mapped 1:1 to
[`contracts/stock-screener.openapi.yaml`](contracts/stock-screener.openapi.yaml)'s
`ScreenRequest`/`ScreenResponse` schemas.

### `ScreenCriteria` (request)

The owner's selected filters, grouped by category exactly as
`screener-v1.md` defines them (Market, Price, Technical, Fundamental), plus
sort field/direction and pagination (`limit`/`offset`). Every field is
optional; an empty `ScreenCriteria` selects the full candidate universe
(research R-007).

### `ScreenCandidate` (internal, per-instrument working state)

Not exposed in the API. The application-layer composition unit produced by
research R-002's two-pass evaluation: an instrument id, its current
`equity_profile`/`equity_daily_bar` row (pass 1), and — only for instruments
still in the candidate set after pass 1 — its current
`technical_indicator_result`/`fundamental_summary`/`valuation_assessment`
rows fetched in bulk (pass 2). Discarded once the response is assembled.

### `ScreenMatch` (response row)

One matched stock: identity (symbol, company name, exchange, sector name),
the specific value(s) that satisfied every selected filter
(`matchedValues`, keyed by filter field name — FR-008), its overall
`DataStatus`, and the `asOfTradingDate` its price basis came from. Never
constructed for a stock excluded under `screener-v1.md` S-4.

### `CategoryDisclosure` (response, one per filtered category)

Whether a category the request actually filtered on was fully, partially,
or not evaluable across the candidate universe (NFR-002, research R-011),
with a count of candidates excluded specifically because of that category
and a reason code. Distinguishes "your Fundamental filter matched nothing"
from "Fundamental data isn't available to filter on right now" — the two
are not the same claim and must not be collapsed into one empty result.

### Coherence key (research R-008)

Computed by the existing `CoherenceKeys` helper (Feature 002,
`stock` module) over the exact set of current-revision row ids the matched
stocks' evaluation actually read: the `equity_profile` and `equity_daily_bar`
row for every match, plus the `technical_indicator_result`,
`fundamental_summary`, and `valuation_assessment` row for every match whose
selected filters actually consulted that table. Two identical requests
against unchanged accepted data produce an identical key (FR-010).

## Candidate universe query (research R-007)

```text
candidates =
  market_instrument m
  JOIN equity_profile p ON p.instrument_id = m.id AND p.effective_to IS NULL
  WHERE p.listing_status = 'LISTED'
```

Every screen — including one with zero selected filters — starts from this
set. A non-`LISTED` instrument is never a candidate, regardless of what it
would otherwise match (research R-007); it does not appear in any
`CategoryDisclosure` exclusion count either, since it was never a candidate
to begin with.

## Read models

| Read model | Composition | Endpoint |
|---|---|---|
| `ScreenResult` | `ScreenMatch[]` (research R-002 two-pass evaluation over the candidate universe) + `CategoryDisclosure[]` + coherence key | `POST /screener/executions` |

This is the only read model this feature adds. It introduces no aggregate,
no revision chain, and no retention policy of its own — retention for every
underlying fact/result remains exactly what Feature 001/002 already defined.
