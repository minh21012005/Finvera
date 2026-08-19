# Research: Portfolio and Watchlist Management

**Feature**: `005-portfolio-watchlist`
**Status**: Complete for fixture-mode implementation. No new provider
dependency and no new gate is introduced by this feature.

Format: `Decision / Rationale / Alternatives considered / Risks and
validation`.

## R-001: Module placement

**Decision**: A new top-level `finvera-be` module, `portfolio`, layered per
ADR-0007 (`domain`, `service`, `repository`, `entity`, `controller`, `dto`),
owning both Portfolio/Transaction/Position/Analytics and Watchlist/
Watchlist Item.

**Rationale**: Unlike Feature 004 (which extended `stock` because every
input it read was already `stock`/`market` data), this feature introduces
the platform's **first owner-scoped, owner-written** data — nobody but the
owner creates a portfolio, records a transaction, or manages a watchlist.
No existing module owns this aggregate, so a new module is the correct
default (Constitution Principle III), not an unjustified addition.
Bundling Portfolio and Watchlist in one module rather than two mirrors
SRS's own MVP-5 grouping and keeps two small, related, owner-scoped
aggregates from becoming two near-empty modules (Constitution Principle
VIII); they share nothing structurally beyond "owner-scoped list," so if
Watchlist later grows independent complexity (e.g., its own alerts), it can
be split out with an ADR then.

**Alternatives considered**: Extending `stock` — rejected; holdings and
watchlists are owner data, not stock reference data, and `stock` already
carries four features' worth of sub-packages. Two separate modules
(`portfolio`, `watchlist`) — rejected for now as premature separation with
no current distinct ownership/deployment need.

## R-002: Owner-scoping model

**Decision**: Every `portfolio` and `watchlist` row carries an `owner_id`
column (`UUID`, not a foreign key to a `users` table, since none exists in
this private single-owner deployment — ADR-0005). The service layer
compares it against the session's authenticated owner id
(`OwnerProperties.id`) on every read and write; a mismatch is treated
identically to "not found" (never a distinct 403 that would confirm another
owner's resource exists).

**Rationale**: This is the first feature to persist owner-scoped rows —
`market`/`stock` data is global reference data with no ownership column at
all. Storing `owner_id` now, even though today's deployment only ever has
one configured owner (ADR-0005), keeps the schema honest about what the
data actually is and costs nothing; the alternative (an implicit
"everything belongs to the one owner" model with no column) would silently
break the moment the identity model changes, since `SEC-001` already
mandates ownership be enforced, not assumed. This is `market`/`stock`-only
data, so introducing a first owner-scoping pattern here — the same
reasoning that justified this feature's own new module (R-001) — is a
genuine, current requirement, not speculative multi-tenancy.

**Alternatives considered**: No `owner_id` column, relying entirely on
"only one owner session can ever exist" — rejected; Constitution Principle
IV requires object ownership enforced at the server trust boundary, and an
implicit single-row assumption is not an enforced check, it is an
accident that happens to hold today.

## R-003: Transaction ledger and FIFO cost basis

**Decision**: `PortfolioTransaction` is immutable and append-only, with
types `BUY`, `SELL`, `DEPOSIT`, `WITHDRAW`, and `VOID`. A `VOID` entry
carries `voids_transaction_id` (required, referencing an earlier,
not-already-voided entry in the same portfolio) and no symbol/quantity/
price of its own. Deriving positions and cash **replays every
non-`VOID` transaction in chronological order (`executed_at`, then a
single global, strictly monotonic `sequence_no` as the deterministic
tie-break — spec.md's same-timestamp edge case; a global sequence is
sufficient because any subset of a strictly increasing sequence, such as
one portfolio's own rows, is itself strictly increasing — no per-portfolio
sequence generator is needed, see data-model.md), skipping any transaction
that a `VOID` entry references.** `BUY` opens or adds to a FIFO lot;
`SELL` consumes the oldest open lot(s) first, realizing P/L on each
consumed lot at that lot's own entry price.

**Rationale**: `VOID`-as-a-typed-entry is simpler and more auditable than
computing an "opposite" transaction (a voided `BUY` does not need to
compute a synthetic `SELL` at the original price — it is simply excluded
from replay), and it satisfies FR-005 exactly: the original stays queryable
and unedited, the correction is a new row, and which entries cancel which
is explicit and typed rather than inferred from matching amounts. FIFO is
the industry-standard, deterministic, simplest-to-audit lot-matching
convention (spec.md Assumptions); replaying in `executed_at` order (not
insertion order) is required because a backdated entry (spec.md edge case)
must slot into cost-basis history at its true chronological position, not
whenever the owner happened to type it in.

**Alternatives considered**: A generic "reversing transaction" that negates
the original's fields — rejected; it would need type-specific inverse
logic (what "reverses" a `DEPOSIT`? A `WITHDRAW` of the same amount — but
then two independently-typed rows exist with no explicit link other than
matching amounts, which is fragile and not provably a correction rather
than a coincidence). LIFO or specific-lot selection — rejected per spec.md
Assumptions (no owner-facing lot-selection UI in this feature).

**Risks/validation**: `contracts/portfolio-analytics-v1.md`'s required
test-vector table must cover a backdated transaction reordering an
already-recorded later trade's FIFO consumption, and a `VOID` of a `BUY`
that had already been partially sold (must be rejected — voiding a
transaction whose resulting lot was already partially consumed by a later
`SELL` would make history internally inconsistent; see U-6 below).

## R-004: Positions and totals are always computed on demand, never stored

**Decision**: `Position`, portfolio totals (value, unrealized/realized
P/L, allocation), and analytics have **no dedicated storage** — every read
replays the requesting portfolio's own transaction ledger (R-003) against
Feature 002's latest accepted price. No materialized/cached position table
exists.

**Rationale**: FR-006 already forbids treating quantity/cost basis as an
independently editable field; the simplest way to guarantee that is to
never store a derived value at all, only recompute it, which by
construction cannot drift from the ledger (Constitution Principle II: a
cache or derived table is never the only authoritative copy, and here it
is not even a cache — it is recomputed every time). At this deployment's
actual scale (one owner, realistically low hundreds to low thousands of
transactions per portfolio over years — ADR-0005), replaying a portfolio's
full ledger on every request is computationally trivial and comfortably
inside NFR-001's 3-second budget; this is not the same scale problem a
multi-tenant platform would have.

**Alternatives considered**: A materialized `position` table updated on
each transaction — rejected as premature optimization (Constitution
Principle VIII) with a real risk of dual-write drift between the ledger and
the materialized view; revisit only if measured latency at real portfolio
sizes ever approaches the NFR budget.

**Risks/validation**: `StrategySignalPerformanceTests`-style timing tests
(research R-009) must exercise a realistically large fixture ledger (e.g.,
500+ transactions across 20+ symbols) to confirm the on-demand replay
still meets NFR-001/NFR-002 before this assumption is trusted in
production.

## R-005: Portfolio return methodology

**Decision**: Net-contributed-capital return (spec.md Assumptions),
computed as:

```text
NetContributedCapital(asOf) =
    Σ DEPOSIT.amount (executed_at <= asOf, not voided)
  − Σ WITHDRAW.amount (executed_at <= asOf, not voided)

ReturnSinceInception =
    (TotalValue(now) − NetContributedCapital(now)) / NetContributedCapital(now)

ReturnOverPeriod(T0, T1) =
    (TotalValue(T1) − TotalValue(T0) − NetContributed(T0, T1])
    / (TotalValue(T0) + NetContributed(T0, T1])
```

where `NetContributed(T0, T1]` is net deposits minus withdrawals strictly
after `T0` up to and including `T1`. Both are `null`/unavailable (not
zero) when their denominator is `<= 0` (e.g., a period with no prior
capital and no contribution during it).

**Rationale**: True time-weighted return (Modified Dietz, XIRR) would
require sub-period valuation at every cash-flow date to neutralize the
timing distortion large deposits/withdrawals cause — materially more
computation and a second precision-sensitive algorithm for a first version.
The net-contributed-capital formula is simple, fully reproducible from the
ledger, and matches common retail-portfolio-tracker practice; its known
distortion under large mid-period cash flows is disclosed to the user
(spec.md Assumptions, DATA-004-adjacent calibrated-language requirement)
rather than hidden.

**Alternatives considered**: True time-weighted return — deferred, not
rejected outright; revisit if net-contributed-capital return proves
materially misleading once real usage accumulates deposit/withdrawal
patterns. Simple absolute P/L percentage ignoring cash flows entirely
(unrealized + realized P/L over cost basis only, ignoring idle cash) —
rejected: SRS §21 explicitly wants *portfolio* return, which must account
for cash sitting in the portfolio, not just invested capital.

## R-006: Drawdown and performance history — bounded, single-pass reconstruction

**Decision**: Maximum drawdown and the performance-history time series are
computed by **one forward scan** over the requested, bounded period's
trading calendar (Feature 001's calendar): maintain a running position/cash
state seeded from the ledger's replay up to the period start, then for
each subsequent trading date apply that date's transactions (if any) and
value open positions using Feature 002's accepted close for that date
(bulk-fetched once per distinct symbol for the whole period, mirroring
Feature 003's bulk-fetch pattern), producing one `TotalValue` point per
trading date. Drawdown is the maximum peak-to-trough decline over that
same series. The endpoint requires an explicit period (default: since
inception, capped at `finvera.portfolio.max-performance-history-span-days`,
**default 730 days** — roughly two years, chosen because it comfortably
covers the multi-year drawdown/return comparisons SRS §21 anticipates
while keeping the forward scan's cost bounded and predictable at this
deployment's realistic scale, research R-004) — never an unbounded "every
day since account creation with no limit" query, consistent with SRS
§36.1's "query historical series through bounded, paginated access." A
requested period start earlier than the portfolio's actual first
transaction is clamped to that first transaction's date, never rejected
and never computed as though pre-inception history existed
(`portfolio-analytics-v1.md` F6/`periodClampedToInception`).

**Rationale**: A single forward scan is `O(transactions + trading_days ×
distinct_symbols_held)`, not `O(trading_days × full_ledger_replay)`, and
reuses the exact same bulk-fetch discipline Feature 003 already
established for candidate universes — no new performance pattern is
invented. Bounding the period keeps the endpoint's cost proportional to
what was actually asked for. This is also **why NFR-002's 3-second target
is achievable at the same tier as a simple read view rather than needing
the 5-second screening-tier baseline**: unlike Feature 003/004's scans,
which traverse the whole supported universe, this scan traverses one
portfolio's own small transaction history over a capped window — closer
in shape to Feature 004's single-stock signal view (NFR-001, 3 seconds)
than to a universe-wide scan. NFR-002 is only actually met, however, if
the 730-day default (or whatever value operations later configures) keeps
`trading_days × distinct_symbols_held` small — T033's latency smoke test
against a realistically large fixture ledger is the gate that confirms
this before the target is trusted, not merely asserted.

**Alternatives considered**: A separately maintained daily valuation
snapshot table, updated by a background job — rejected as premature
infrastructure (Constitution Principle VIII); revisit only if on-demand
reconstruction is measured to be too slow at real data volumes.

## R-007: Concentration

**Decision**: `StockConcentration(symbol) = positionValue(symbol) /
TotalValue`; `SectorConcentration(sector) = Σ positionValue(symbols in
that sector) / TotalValue`, reusing Feature 001/002's existing sector
classification on `market_instrument`/`equity_profile` without introducing
a new taxonomy.

**Rationale**: Directly satisfies SRS §21's "Stock concentration" and
"Sector concentration" with no new classification system, consistent with
FR-013's reuse discipline.

## R-008: Portfolio risk exposure rollup

**Decision**: For each open position whose symbol currently has one or
more Feature 004 signals, that position's own risk contribution is the
**highest** `risk_score` among its current signals (the most conservative
available reading, since a position can be flagged risky by any one
triggered strategy). The portfolio's `riskExposureScore` is the
allocation-weighted average of covered positions' risk contributions,
weighted by each position's share of **total position value** (cash
excluded — cash carries no market risk to roll up). `coverageRatio =
coveredPositionsValue / totalPositionsValue` is always reported alongside
the score, and the score/level are computed only from covered positions —
never treating an uncovered position as risk-free. `riskLevel` bands reuse
`strategy-signal-v1`'s own `LOW [0,33] / MEDIUM (33,66] / HIGH (66,100]`
so a portfolio-level band means the same thing a signal-level band already
means to the owner.

**Rationale**: SRS §21 names "Risk exposure" without a formula; reusing
Feature 004's already-computed, already-explained risk scores (rather than
inventing a second, portfolio-specific risk model) satisfies FR-014's
reuse discipline and Constitution Principle VIII. The "highest score, not
average" choice per position is a deliberate conservative default, since a
position genuinely is as risky as its riskiest currently-triggered
strategy signal, not diluted by strategies that did not trigger.

**Alternatives considered**: Averaging all a position's current signal
scores — rejected as understating risk for a position with one severe
warning and several mild ones. Treating an uncovered position (no current
Feature 004 signal) as zero risk — rejected; FR-014 explicitly requires
stating the uncovered proportion rather than silently assuming safety.

## R-009: Watchlist items — live read, no persistence of computed state

**Decision**: A watchlist stores only `(watchlist_id, symbol,
added_at)` membership rows. Every displayed field (price, daily change,
trend, signal, risk level, volume condition) is read live at request time
directly from Features 001-004's own already-persisted current results —
mirroring Feature 004's own "live re-check, not a second table" pattern
(its research R-006/data-model.md "why non-triggers are not persisted").

**Rationale**: A watchlist item is a view, not a dated historical record
(unlike a signal, which SRS gives a `Created At` field) — there is nothing
to reproduce later that isn't already reproducible from Features 001-004's
own persisted state. Persisting a computed snapshot per watchlist item
would create a second, driftable copy of data already owned elsewhere.

**Alternatives considered**: Caching each item's computed fields on add and
refreshing periodically — rejected; introduces staleness with no benefit
over a live read at this data volume (a watchlist has at most a few dozen
symbols).

## R-010: Fixture strategy

**Decision**: Small, hand-constructed, independently-computed transaction
ledgers (mirroring Feature 004's R-009 direct-seeding approach, not JSON
provider fixtures) — one ledger per FIFO scenario (simple buy/sell,
partial close, backdated insert, voided entry, over-sell rejection,
over-withdraw rejection, a replayed `Idempotency-Key` (R-011), a
period-`from`-before-inception analytics request (`portfolio-analytics-v1.md`
F6)), plus fixture portfolios spanning multiple symbols/sectors for
concentration and analytics tests, reusing Feature 001-004's own existing
fixture instruments/prices/signals rather than inventing a parallel
universe.

## R-011: Idempotent transaction recording

**Decision**: `recordTransaction` and `voidTransaction` both require a
client-supplied `Idempotency-Key` header, unique per `(portfolio_id,
idempotency_key)` (data-model.md). A replay of an already-used key for the
same portfolio is rejected with `DUPLICATE_SUBMISSION`, referencing the
original transaction's id, before any FIFO/cash effect is applied — never
silently creating a second ledger entry and never silently returning the
original as if the retry were a no-op success. The frontend client
generates one key per logical submit action (e.g., one UUID created when
the owner presses "Record transaction," held in that form submission's own
state) and reuses it only across that action's own network retries; a
separately decided, later transaction — even one with identical symbol,
quantity, price, and `executedAt` — always uses a fresh key.

**Rationale**: FR-002 places no uniqueness constraint on a transaction's
own business fields (the owner may legitimately buy the same quantity of
the same stock at the same price twice in one day), yet SC-007 requires a
"duplicate submission" (an accidental retry) to be rejected. Without a
client-supplied key, the server has no way to tell these two cases apart —
matching on business-field equality would incorrectly reject a second,
genuine trade. A per-attempt idempotency key is the standard solution to
exactly this ambiguity (the same pattern payment APIs use for the same
reason) and is the minimal mechanism that actually closes the gap, rather
than merely restating the requirement without a way to satisfy it.

**Alternatives considered**: Detecting duplicates by matching
`(portfolio_id, transactionType, instrumentSymbol, quantity, price,
executedAt)` — rejected; cannot distinguish a retry from a genuine second
identical trade, so it would either miss real retries (if made lenient) or
reject legitimate trades (if made strict). Making the header optional —
rejected; an optional key that the client sometimes omits leaves exactly
the gap this decision exists to close, so the contract requires it on
every call.

**Risks/validation**: `contracts/portfolio-analytics-v1.md`'s required
test-vector table must cover both a same-key replay (rejected) and a
different-key call with otherwise-identical fields (accepted as a genuine
second transaction), so the boundary between "retry" and "real repeat
trade" is proven, not assumed.
