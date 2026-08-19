# Data Model: Portfolio and Watchlist Management

**Feature**: `005-portfolio-watchlist`
**Owner**: `finvera-be` / `portfolio` module (new — research R-001)
**System of record**: PostgreSQL
**Migration**: forward-only Flyway, starting at `V005`

## Relationship to Features 001-004

This feature **reuses without modification**:

| Existing table/interface | Role here |
|---|---|
| `market_instrument`, `equity_profile` (Feature 001/002) | Instrument identity, sector classification (research R-007), and the check that a transaction's symbol is a supported instrument (FR-018). |
| `equity_daily_bar` (Feature 002) | Latest accepted close for unrealized P/L, and historical accepted closes for performance-history reconstruction (research R-006). |
| Feature 001's market calendar and index snapshot read interfaces | Trading-date iteration (research R-006) and the VN-Index benchmark series (FR-015). |
| Feature 004's `strategy_signal` current rows (via a published read method, mirroring Feature 004's own `MarketReferenceDataService` extension pattern) | Watchlist item signal/risk display (research R-009) and the portfolio risk-exposure rollup (research R-008). |
| `OwnerProperties.id` (auth module) | The single configured owner identity every `owner_id` column is checked against (research R-002). |

No Feature 001-004 table is altered. Nothing in this feature recomputes an
indicator, a signal, a risk score, or a sector classification (FR-018 /
Assumptions reuse discipline).

## Enumerations

| Type | Values | Notes |
|---|---|---|
| `TransactionType` | `BUY`, `SELL`, `DEPOSIT`, `WITHDRAW`, `VOID` | research R-003. |
| `PortfolioTotalsAvailability` | `DEFINED`, `NOT_APPLICABLE`, `MISSING` | Reused from Feature 002 `stock.domain.model.StockTypes`, not redefined — used wherever a computed figure (e.g., return with zero net-contributed capital) is undefined rather than zero. |

## `portfolio`

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `owner_id` | UUID | Research R-002; indexed. |
| `name` | varchar(120) | Not blank; unique per `owner_id`. |
| `created_at` | timestamptz | UTC. |
| `deleted_at` | timestamptz nullable | Soft delete — a deleted portfolio's transactions remain queryable for audit but are excluded from the owner's active portfolio list; hard deletion is not offered (consistent with Constitution immutability discipline extended to the container, not only the ledger). |

## `portfolio_transaction`

Immutable, append-only. One row per recorded ledger entry (research R-003).

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `portfolio_id` | UUID | FK `portfolio`. |
| `sequence_no` | bigint | A single global, database-generated sequence (`bigserial`) — **not** scoped per portfolio; scoping is unnecessary, since any subset of a strictly increasing sequence (e.g., one portfolio's own rows) is itself strictly increasing, which is all the deterministic same-timestamp tie-break (research R-003) requires. Unique per `portfolio_id` (i.e., globally unique, trivially so within one portfolio too). |
| `idempotency_key` | varchar(100) | Not null (research R-011); the client-supplied `Idempotency-Key` this entry was recorded with, echoed back in every response. Unique per `(portfolio_id, idempotency_key)` — a repeat is rejected as `DUPLICATE_SUBMISSION` before any FIFO/cash effect is applied, never silently deduplicated into a no-op success. |
| `transaction_type` | varchar(16) | `TransactionType`. |
| `instrument_id` | UUID nullable | FK `market_instrument`; required for `BUY`/`SELL`, null for `DEPOSIT`/`WITHDRAW`/`VOID`. |
| `quantity` | numeric(20,4) nullable | `> 0`; required for `BUY`/`SELL`, null otherwise. |
| `price` | numeric(20,6) nullable | `> 0`; required for `BUY`/`SELL`, null otherwise. |
| `fee` | numeric(20,6) | `>= 0`; default `0`. Meaningful for `BUY`/`SELL`; `0` for other types. |
| `amount` | numeric(20,6) nullable | Cash amount; required for `DEPOSIT`/`WITHDRAW`, null otherwise. |
| `currency` | varchar(3) | Fixed `VND` (DATA-005); stored, not hard-coded, so a future currency requires only a value change, not a schema change. |
| `executed_at` | timestamptz | UTC; owner-declared trade/cash-flow time — MAY be backdated relative to `entry_at` (research R-003, DATA-002). |
| `entry_at` | timestamptz | UTC; server-assigned ingestion time — always "now" at recording, never backdated. |
| `voids_transaction_id` | UUID nullable | FK `portfolio_transaction` (same `portfolio_id`); required and only meaningful for `transaction_type = VOID`; the referenced transaction MUST NOT itself be `VOID` and MUST NOT already have a `VOID` referencing it. |
| `void_reason` | varchar(200) nullable | Required when `transaction_type = VOID`. |

Checks:
`(transaction_type IN ('BUY','SELL')) = (instrument_id IS NOT NULL AND quantity IS NOT NULL AND price IS NOT NULL)`;
`(transaction_type IN ('DEPOSIT','WITHDRAW')) = (amount IS NOT NULL)`;
`(transaction_type = 'VOID') = (voids_transaction_id IS NOT NULL AND void_reason IS NOT NULL)`.

Indexes: `(portfolio_id, sequence_no)` unique, the replay-order read path
(research R-003/R-004); `(portfolio_id, executed_at)` for chronological
range reads (research R-006); `(portfolio_id, idempotency_key)` unique,
the duplicate-submission check (research R-011); partial unique index on
`voids_transaction_id` (a transaction can be voided at most once).

**No `updated_at` or mutable field exists on this table.** A correction is
always a new row (`VOID`, then optionally a new correct entry) — FR-005.

## `watchlist`

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `owner_id` | UUID | Research R-002; indexed. |
| `name` | varchar(120) | Not blank; unique per `owner_id`. |
| `created_at` | timestamptz | UTC. |

## `watchlist_item`

| Field | Type | Constraints |
|---|---|---|
| `watchlist_id` | UUID | FK `watchlist`; part of primary key. |
| `instrument_id` | UUID | FK `market_instrument`; part of primary key. Adding an already-present symbol is a no-op (spec.md edge case), enforced by this composite key. |
| `added_at` | timestamptz | UTC. |

No other field is stored (research R-009); every displayed value is read
live from Features 001-004 at request time.

## Read models (never stored)

These are computed on demand (research R-004/R-006/R-008) and exist only as
API/service-layer shapes, not tables.

| Read model | Composition | Endpoint |
|---|---|---|
| `Position` | FIFO replay (research R-003) of one portfolio's non-`VOID`ed `BUY`/`SELL` rows for one instrument, priced against Feature 002's latest accepted close | `GET /portfolios/{id}/positions` |
| `PortfolioTotals` | Sum of every open `Position`'s market value, plus cash balance derived from `DEPOSIT`/`WITHDRAW`/`BUY`/`SELL` replay, total unrealized/realized P/L | `GET /portfolios/{id}` |
| `PortfolioAnalytics` | Return since inception and over a selected period (research R-005), max drawdown and performance-history series (research R-006), stock/sector concentration (research R-007), risk-exposure rollup (research R-008), VN-Index benchmark comparison | `GET /portfolios/{id}/analytics` |
| `WatchlistView` | `watchlist_item` membership joined, at read time, with each symbol's current price/change (Feature 002), trend (Feature 002/003), current signal/risk level (Feature 004), and volume condition (Feature 002/003) | `GET /watchlists/{id}` |

**Coherence key**: reuses Feature 002's `CoherenceKeys` helper, computed
over the contributing transaction row ids (for `PortfolioTotals`/
`Position`) or the contributing signal/price row ids (for `WatchlistView`),
the same pattern Features 002-004 already established.

## Why nothing here is materialized

Every number a portfolio or watchlist view shows is reproducible from
`portfolio_transaction` (a portfolio's own truth) plus Features 001-004's
already-accepted, already-persisted facts (research R-004). Storing a
derived position, total, or analytics value would create a second copy
that could drift from the ledger it was computed from — exactly what
Constitution Principle II forbids for derived data. If a future measured
performance problem justifies a materialized snapshot, it is added as a
pure, rebuildable optimization layer over this same ledger, never as a
new source of truth.
