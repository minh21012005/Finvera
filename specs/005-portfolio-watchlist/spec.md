# Feature Specification: Portfolio and Watchlist Management

**Feature Directory**: `005-portfolio-watchlist`
**Created**: 2026-08-19
**Status**: Draft
**SRS References**: Section 20 (Portfolio Management), 21 (Portfolio
Analytics), 22 (Watchlist), 36.1 (performance), 47 (MVP-5), 54 (MVP success
criteria), 58 (Requirements Index)
**SRS Requirement IDs**: SRS-PF-01, SRS-PF-02, SRS-WL-01.
Explicitly deferred: SRS-RSK-02 (Section 18, Position Sizing — its blocking
inputs, `Available capital` and `Portfolio exposure`, exist once this
feature ships, but sizing is a Strategy/Signal-engine capability that
belongs with Feature 004's risk engine, not bundled into portfolio/watchlist
tracking; revisit as its own small follow-up); SRS-SCO-01 (Section 12,
composite multi-factor stock score — still Post-MVP, so Watchlist's
optional "Overall score" column, SRS §22, is omitted until that engine
exists); SRS-JRN-01 (Section 23, Investment Journal — Post-MVP);
SRS-ALR-01 (Section 35, Alerts — Post-MVP); SRS-BKT-01/02 (Section 19,
Backtesting — Post-MVP, and itself depends on position sizing).
**Input**: User description: "Portfolio and Watchlist Management (Feature
5, MVP-5). The owner records buy/sell transactions per portfolio to build a
transaction-ledger-based holdings view with deterministic cost basis,
realized and unrealized P/L, allocation, and performance; sees portfolio
analytics (return, drawdown, risk exposure, stock/sector concentration,
VN-Index benchmark comparison, performance history); and creates/manages
watchlists whose items show live market and analysis context (price, daily
change, technical trend, signal, risk level, volume condition) reusing
Features 001-004's existing calculations without recomputation. Same
private single-owner deployment as Features 001-004."

## Scope Summary *(mandatory)*

In the same private single-owner deployment established by Features
001-004, Finvera's owner needs a truthful record of what they actually
own and did, not just what the market or the strategy engine suggests. The
owner records each buy, sell, deposit, and withdrawal as it happens; the
system derives current holdings, cost basis, realized and unrealized
profit/loss, and allocation from that history, and surfaces portfolio-level
analytics (return, drawdown, concentration, risk exposure, and comparison
against the VN-Index) so the owner can judge how their actual decisions
performed over time.

Independently, the owner also needs a place to track candidate stocks they
are researching but have not (yet) bought — a watchlist showing each
symbol's live price, trend, signal, and risk context, reusing Features
001-004's already-computed results exactly as they stand, without
recomputing any indicator, signal, or risk score.

Every holding value, P/L figure, and analytics number is derived
deterministically and reproducibly from an immutable transaction ledger and
already-accepted market facts — never a value the owner types in and the
system merely stores at face value, and never silently recomputed with
different assumptions between two views of the same data.

### In Scope

- One or more named portfolios per owner (create, rename, delete).
- An immutable transaction ledger per portfolio: `BUY`, `SELL`, `DEPOSIT`
  (cash in), and `WITHDRAW` (cash out) entries, each with symbol (for
  `BUY`/`SELL`), quantity, price, fee, currency, and executed time.
  Mistakes are corrected only by recording an explicit reversing entry
  referencing the original; nothing is edited or deleted in place.
- Derived positions: quantity held, FIFO-matched average cost basis,
  current price (Feature 002's latest accepted price), unrealized P/L,
  realized P/L from closed lots, and allocation percentage of total
  portfolio value — all computed from the ledger, never independently
  editable fields.
- Portfolio totals: total value (holdings market value plus cash balance),
  total unrealized P/L, total realized P/L.
- Portfolio analytics: return since inception and over a selectable period,
  maximum drawdown, a performance-history view of portfolio value over
  time, stock concentration, sector concentration (reusing Features
  001/002's sector classification), an allocation-weighted portfolio risk
  exposure rollup (reusing Feature 004's per-position risk levels where
  available), and comparison against the VN-Index benchmark (Feature 001)
  over the same period.
- Watchlists: create, rename, delete; add and remove symbols. Each item
  shows symbol, current price, daily change, technical trend, signal (if
  any), risk level (if any), and volume condition, each reused as-is from
  Features 001-004's already-persisted results.
- Safe, truthful unavailable states: a watchlist item whose underlying
  trend/signal/risk data does not exist or is stale shows a stated reason,
  never a fabricated or default value; a `SELL` that exceeds currently held
  quantity is rejected with a reason.
- Reproducibility: identical accepted ledger entries and price history
  always reproduce identical holdings, P/L, allocation, and analytics.

### Out of Scope

- Position sizing suggestions (SRS §18 / SRS-RSK-02): a Strategy/Signal
  capability that now has its blocking inputs available but is deliberately
  kept as a separate follow-up rather than bundled into this feature.
- Composite/overall stock score (SRS-SCO-01, §12, Post-MVP): Watchlist's
  optional "Overall score" column is omitted until that engine exists.
- Backtesting (SRS §19, Post-MVP).
- Investment Journal (SRS §23, Post-MVP) and Alerts/notifications (SRS §35,
  Post-MVP).
- Broker or exchange transaction import/sync; every transaction is entered
  by the owner. No automated order execution or trading of any kind.
- Multi-currency portfolios; every amount is Vietnamese Dong (VND),
  consistent with the Vietnamese-equity-only scope of Features 001-004.
- True time-weighted return (TWR/Modified Dietz/XIRR); this feature uses a
  simpler net-contributed-capital return methodology (see Assumptions).
- Symbols outside the Feature 001-003 supported stock universe; a
  transaction referencing an unsupported symbol is rejected with a reason.
- AI-generated commentary or natural-language explanation of portfolio
  performance (deferred to MVP-7 AI Analyst).
- Multi-user or public delivery; the same private single-owner deployment
  model as Features 001-004 applies unchanged.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record Transactions and See Current Holdings (Priority: P1)

As the owner, I want to record my buy/sell trades and cash movements and
see my current holdings with cost basis, unrealized P/L, and realized P/L,
so that I have a trustworthy record of what I actually own and how it has
performed, independent of what the market dashboard or strategy engine
currently suggests.

**Why this priority**: Without a recorded transaction history, "portfolio"
has no meaning — every other capability in this feature (analytics,
allocation, benchmark comparison) is derived from this ledger, so it is the
smallest useful slice and the one every later story depends on.

**Independent Test**: Create a portfolio, record a `DEPOSIT`, a `BUY` for a
supported symbol, and a partial `SELL` of the same symbol at a different
price; verify the resulting position shows the correct remaining quantity,
FIFO-matched average cost basis, current unrealized P/L against the latest
accepted price, and realized P/L from the closed portion — all reproducible
from the same three ledger entries.

**Acceptance Scenarios**:

1. **Given** a new portfolio with no transactions, **When** the owner
   records a `DEPOSIT` followed by a `BUY` for a supported symbol,
   **Then** the portfolio shows the correct remaining cash balance, one
   open position with the entered quantity and price as its cost basis,
   and an unrealized P/L computed against the latest accepted price.
2. **Given** an open position, **When** the owner records a `SELL` for
   part of the held quantity at a different price, **Then** the remaining
   position quantity and average cost basis update by FIFO lot matching,
   a realized P/L is recorded for the closed lot, and the unrealized P/L
   of the remaining quantity reflects only the still-open lots.
3. **Given** an open position, **When** the owner attempts to `SELL` more
   than the currently held quantity, **Then** the transaction is rejected
   with a stated reason and no ledger entry, position, or balance changes.
4. **Given** a previously recorded transaction was entered in error,
   **When** the owner corrects it, **Then** the correction is recorded as
   a new reversing entry referencing the original; the original entry
   remains queryable and neither entry is edited or deleted in place.
5. **Given** identical accepted transactions and an unchanged latest
   accepted price, **When** the owner reloads the holdings view, **Then**
   every position's quantity, cost basis, unrealized P/L, realized P/L,
   and allocation percentage are identical to the prior view.

---

### User Story 2 - Track Research Candidates in a Watchlist (Priority: P2)

As the owner, I want to add stocks I am researching to a watchlist and see
each one's live price, trend, signal, and risk context in one place, so
that I can compare candidates without opening each stock's detail page
individually, and without recomputing anything the platform has already
calculated.

**Why this priority**: A watchlist is independently valuable and has no
dependency on any portfolio holding existing, but ranks below recording
actual transactions (US1) because tracking what one actually owns is the
more central "did I make a good decision" record this feature exists to
provide.

**Independent Test**: Create a watchlist, add a supported symbol that
currently has a Feature 004 signal and one that does not, and verify each
item shows symbol, current price, daily change, technical trend, volume
condition, and either its signal/risk level or a truthful absence/
unavailable state — with none of these values recomputed, only reused from
what Features 001-004 already persisted.

**Acceptance Scenarios**:

1. **Given** a watchlist with a supported symbol added, **When** the owner
   views it, **Then** the item shows current price, daily change,
   technical trend, volume condition, and, if Feature 004 currently
   produces one, its signal direction and risk level — each matching
   exactly what that symbol's own detail/signal view already shows.
2. **Given** a watchlist item's symbol currently has no Feature 004
   signal, **When** the owner views the watchlist, **Then** that item
   shows a specific no-signal state, not an error and not a fabricated
   signal.
3. **Given** a watchlist item's symbol lacks enough accepted history for
   its technical trend or volume condition, **When** the owner views the
   watchlist, **Then** that field shows a stated insufficient-history
   reason rather than a default or fabricated value.
4. **Given** an existing watchlist, **When** the owner removes a symbol or
   deletes the whole watchlist, **Then** the removal takes effect
   immediately and does not affect any other watchlist or any portfolio.

---

### User Story 3 - Review Portfolio Analytics and Benchmark Comparison (Priority: P3)

As the owner, I want to see my portfolio's return, drawdown, concentration,
risk exposure, and performance against the VN-Index over time, so that I
can judge how my actual trading decisions have performed, not just what my
current holdings are worth right now.

**Why this priority**: Analytics is a deeper, historical view built on top
of the holdings US1 already derives from the ledger; it is genuinely
useful only once a transaction history and its resulting positions exist.

**Independent Test**: With a portfolio holding at least two positions
across two sectors and a recorded deposit/withdrawal history spanning
several days, open the analytics view and verify return, drawdown, stock
and sector concentration, risk exposure, and the VN-Index comparison are
all computed and internally consistent with the same period's transaction
and price history.

**Acceptance Scenarios**:

1. **Given** a portfolio with a transaction history spanning multiple
   days, **When** the owner opens portfolio analytics, **Then** it shows
   return since inception, return over a selectable shorter period,
   maximum drawdown, and a performance-history view of portfolio value
   over time, all reconstructed from the ledger and historical accepted
   prices.
2. **Given** a portfolio holding positions in more than one stock and
   sector, **When** the owner views concentration, **Then** stock
   concentration and sector concentration are each shown as a percentage
   of total portfolio value that sums correctly across all positions.
3. **Given** at least one held position currently has a Feature 004 risk
   level and at least one does not, **When** the owner views risk
   exposure, **Then** the rollup is computed only from positions that have
   one, and the proportion of portfolio value without current risk
   coverage is stated, not silently ignored.
4. **Given** a selected comparison period, **When** the owner views the
   VN-Index benchmark comparison, **Then** the portfolio's return and the
   VN-Index's return over the identical period are shown side by side,
   reusing Feature 001's persisted index data.
5. **Given** the same transaction and price history, **When** the owner
   reloads analytics, **Then** every metric is identical to the prior
   view (reproducibility).

### Edge and Failure Cases *(mandatory)*

- The owner records a transaction dated earlier than an already-recorded
  later transaction for the same symbol (a backdated entry) — FIFO
  matching and all downstream P/L recompute correctly in chronological
  order, not entry order.
- A `WITHDRAW` is attempted for more cash than the portfolio's current
  balance — rejected with a stated reason, no partial withdrawal applied.
- A held symbol's latest accepted price is stale, delayed, or withheld due
  to a Feature 002 cross-source conflict — the affected position's
  unrealized P/L and the portfolio total show a stated unavailable/stale
  reason rather than a fabricated or last-known value silently reused as
  current.
- A watchlist symbol becomes unsupported or delisted after being added —
  the item remains listed with a stated unavailable reason rather than
  silently disappearing.
- The owner adds the same symbol to a watchlist twice, or to two different
  watchlists — each list's membership is independent; no duplicate-item
  error is required within a list, but a second add to the same list is a
  no-op, not a duplicate row.
- A sector classification (Feature 001/002) a concentration calculation
  depends on is corrected — the affected concentration figures recompute
  and MUST NOT silently keep the superseded classification.
- The owner requests analytics for a period before the portfolio's first
  transaction — the response states that no history exists for that
  period rather than showing zero or fabricated values.
- Two transactions are recorded with the exact same timestamp for the same
  symbol — a stated, deterministic tie-break order (e.g., ledger insertion
  order) is applied consistently, never ambiguous or randomized.
- Repeated identical transaction submission (e.g., a retried request) does
  not create duplicate ledger entries.
- An unauthenticated request or a request from any identity other than the
  configured owner, against any portfolio, transaction, or watchlist
  endpoint.

## Requirements *(mandatory)*

Requirements describe observable behavior. Accepted IDs are stable and MUST
not be renumbered; removed requirements are deprecated with a reason.

### Functional Requirements

- **FR-001**: The system MUST let the owner create, rename, and delete one
  or more named portfolios.
- **FR-002**: The system MUST let the owner record `BUY`, `SELL`,
  `DEPOSIT`, and `WITHDRAW` transactions in a portfolio, each as an
  immutable ledger entry with symbol (for `BUY`/`SELL`), quantity, price,
  fee, currency, and executed time.
- **FR-003**: A `SELL` transaction whose quantity exceeds the position's
  currently held quantity as of that transaction's chronological position
  MUST be rejected with a stated reason and MUST NOT alter the ledger,
  position, or cash balance.
- **FR-004**: A `WITHDRAW` transaction that exceeds the portfolio's current
  cash balance MUST be rejected with a stated reason and MUST NOT partially
  apply.
- **FR-005**: The owner MUST correct a mistaken transaction only by
  recording an explicit reversing entry that references the original;
  the system MUST NOT allow an existing ledger entry to be edited or
  deleted in place.
- **FR-006**: The system MUST derive, from the transaction ledger in
  chronological (executed-time) order, each position's held quantity,
  FIFO-matched average cost basis, current price (Feature 002's latest
  accepted price), unrealized P/L, and realized P/L from closed lots —
  never accepting quantity or cost basis as an independently editable
  field.
- **FR-007**: The system MUST compute each portfolio's total value
  (position market values plus cash balance), total unrealized P/L, total
  realized P/L, and per-position allocation percentage of total portfolio
  value.
- **FR-008**: The system MUST let the owner create, rename, and delete
  watchlists, and add and remove symbols from them.
- **FR-009**: Each watchlist item MUST show symbol, current price, daily
  change, technical trend, signal (if any), risk level (if any), and
  volume condition, each reused exactly from Features 001-004's existing
  persisted results, never recomputed independently.
- **FR-010**: When a watchlist item's underlying trend, signal, risk, or
  volume data is unavailable or the symbol lacks sufficient accepted
  history, that field MUST show a stated reason rather than a fabricated
  or default value.
- **FR-011**: The system MUST compute portfolio return since inception and
  over a selectable shorter period, using net-contributed-capital
  methodology (Assumptions), from the transaction ledger and historical
  accepted prices.
- **FR-012**: The system MUST compute maximum drawdown and a
  performance-history view of portfolio value over time, reconstructed
  from the transaction ledger and historical accepted prices, not from a
  separately maintained mutable snapshot.
- **FR-013**: The system MUST compute stock concentration and sector
  concentration (reusing Feature 001/002 sector classification) as a
  percentage of total portfolio value.
- **FR-014**: The system MUST compute an allocation-weighted portfolio
  risk-exposure rollup from each held position's current Feature 004 risk
  level where available, and MUST state the proportion of portfolio value
  without current risk coverage rather than silently excluding it.
- **FR-015**: The system MUST compare portfolio return against the
  VN-Index benchmark (Feature 001) over the same selected period.
- **FR-016**: Given identical accepted transactions, prices, sector
  classifications, and risk levels, repeated computation of holdings,
  P/L, allocation, and analytics MUST produce identical results.
- **FR-017**: When a price, sector classification, or risk level a
  computed holding or analytics value depends on is corrected upstream,
  the affected value MUST be recalculated and MUST NOT silently keep the
  superseded value.
- **FR-018**: A transaction referencing a symbol outside the Feature
  001-003 supported stock universe MUST be rejected with a stated reason.

### Data and Financial Semantics

- **DATA-001**: Every transaction, position, P/L, and analytics value MUST
  use declared decimal precision and rounding; computations MUST NOT use
  binary floating point.
- **DATA-002**: Every transaction MUST retain its executed time and its
  ingestion (recorded) time as distinct values, and every reversing entry
  MUST retain a reference to the original entry it corrects.
- **DATA-003**: Position cost basis MUST use FIFO lot matching applied in
  chronological (executed-time) order, deterministic and reproducible from
  the ordered transaction history.
- **DATA-004**: A missing, stale, or cross-source-conflicted current price,
  sector classification, or risk level MUST be surfaced with a reason and
  MUST NOT be treated as zero or silently substituted with a stale value.
- **DATA-005**: All monetary amounts are Vietnamese Dong (VND); the system
  MUST NOT accept or display a transaction in another currency.

### Security and Privacy

- **SEC-001**: Portfolio, transaction, and watchlist endpoints MUST be
  accessible only to the single configured owner identity, under the same
  authenticated server-side session and CSRF controls established in
  Features 001-004; no separate authentication path may be introduced.
- **SEC-002**: The system MUST NOT expose another portfolio's, another
  watchlist's, raw provider, credential, or token data through any
  response, log, or export.

### Non-Functional Requirements

- **NFR-001**: At least 95% of portfolio holdings views and watchlist
  views MUST return within 3 seconds under normal operating conditions,
  consistent with SRS §36.1's primary-read-view baseline (which names
  watchlist explicitly).
- **NFR-002**: At least 95% of portfolio analytics views MUST return
  within 3 seconds under normal operating conditions, the same
  primary-read-view baseline as holdings and watchlist.
- **NFR-003**: P/L sign, allocation, drawdown, and risk-exposure states
  MUST be understandable without relying on color alone.

### Key Entities

- **Portfolio**: An owner-named container of transactions and cash;
  identity, name, creation time, and ownership.
- **Transaction**: One immutable ledger entry (`BUY`, `SELL`, `DEPOSIT`,
  `WITHDRAW`) with symbol (where applicable), quantity, price, fee,
  currency, executed time, entry time, and an optional reference to the
  original entry it reverses.
- **Position**: A derived (not independently stored/editable) view of one
  symbol's held quantity, FIFO cost basis, current price, unrealized P/L,
  realized P/L, and allocation, recomputed from a portfolio's transaction
  history and current/historical accepted prices.
- **Watchlist**: An owner-named, ordered set of symbols with no relation
  to any portfolio's holdings.
- **Watchlist Item**: One symbol's live market and analysis context as
  reused from Features 001-004, plus its membership in a specific
  watchlist.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- **FIFO cost-basis matching.** SRS §20 requires "Entry price" and
  "Realized P/L" but does not name a lot-matching method. FIFO is the
  industry-standard default for retail portfolio tracking, deterministic,
  and simplest to reproduce and audit; LIFO or specific-lot selection are
  not offered in this feature.
- **Net-contributed-capital return, not true time-weighted return.**
  Portfolio return is computed as (current total value minus net capital
  contributed, where net capital contributed is cumulative deposits minus
  cumulative withdrawals) divided by net capital contributed. This is
  simpler to compute and explain than Modified Dietz/XIRR/true
  time-weighted return, at the cost of being distorted by the timing of
  large deposits/withdrawals within a period. This limitation MUST be
  disclosed alongside the return figure, consistent with Constitution
  Principle IV's calibrated-language requirement. True time-weighted
  return is deferred to a later refinement if it proves materially
  misleading in practice.
- **Position Sizing (SRS §18/SRS-RSK-02) is a deliberate, separate
  follow-up**, not part of this feature, even though `Available capital`
  and `Portfolio exposure` become available here — it is a Strategy/
  Signal-engine capability (Feature 004's risk engine) that would blur
  this feature's scope; keeping it separate is a reasonable default per
  Constitution Principle VIII (Modular Simplicity), confirmed with the
  owner before drafting.
- **VND-only, single-currency portfolios.** Consistent with the
  Vietnamese-equity-only scope of Features 001-004; no FX conversion is
  introduced.
- **Manual transaction entry only.** No broker/exchange import or sync;
  consistent with the private, read-only-provider deployment model
  established by ADR-0003 and ADR-0005 — nothing in this feature places
  or reads real brokerage orders.
- **Watchlist "Overall score" (SRS §22) is omitted**, not merely
  unavailable per item, because the composite stock-scoring engine
  (SRS-SCO-01, §12) does not exist yet (Post-MVP); this mirrors Feature
  004's pattern of naming an SRS-listed field currently unavailable due to
  an inter-feature dependency rather than fabricating a placeholder score.
- A signal or risk level shown on a watchlist item is a deterministic
  evaluation of already-accepted facts, not a forecast, and confers no
  fiduciary or execution authority (Constitution Principle IV) — the same
  disclosure discipline as Feature 004.
- No LLM or generative AI capability is required to deliver this feature.

### Dependencies

- Feature 001's persisted index/benchmark data (VN-Index) and instrument
  sector classification.
- Feature 002's persisted latest accepted price, daily bars (for
  historical performance reconstruction), technical trend, volume
  condition, and sector classification.
- Feature 003's supported-universe definition (a transaction may only
  reference a supported symbol).
- Feature 004's persisted per-stock signal and risk-level results, reused
  as-is for watchlist items and the portfolio risk-exposure rollup.
- A versioned deterministic FIFO cost-basis and P/L computation
  specification, and a versioned portfolio-return/drawdown/concentration
  computation specification — both to be resolved in this feature's
  `research.md`/`contracts/` before implementation, consistent with
  Constitution Principle I.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Across versioned transaction-ledger fixtures covering
  simple and partial-close FIFO scenarios, 100% of computed positions,
  cost bases, unrealized P/L, and realized P/L match independently
  computed expected values.
- **SC-002**: Across repeated computation with an unchanged accepted
  ledger and price history, 100% of holdings, P/L, allocation, and
  analytics results are identical (reproducibility).
- **SC-003**: 100% of watchlist items display trend, signal, risk level,
  and volume condition values that match exactly what each symbol's own
  Feature 002-004 views already show, with zero independently recomputed
  values.
- **SC-004**: Across insufficient-history, stale-price, and
  missing-risk-coverage fixtures, 100% of affected fields are shown with a
  stated reason, never fabricated or defaulted.
- **SC-005**: Across versioned analytics fixtures, 100% of return,
  drawdown, concentration, and benchmark-comparison figures match
  independently computed expected values for the same period.
- **SC-006**: At least 95% of portfolio holdings, watchlist, and analytics
  views return within 3 seconds.
- **SC-007**: Rejection tests (over-sell, over-withdraw, unsupported
  symbol, duplicate submission) show 100% of invalid transactions are
  rejected with a stated reason and produce zero ledger, position, or
  balance changes.
- **SC-008**: Accessibility review confirms 100% of P/L, allocation,
  drawdown, and risk-exposure states have a non-color indicator.
- **SC-009**: Authorization tests show only the configured owner can reach
  any portfolio/transaction/watchlist endpoint, and no response, log, or
  export contains another portfolio's data, a credential, or a token.

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001, FR-002, FR-006, FR-007 | US1 / Scenarios 1-2 | SC-001, SC-002 |
| FR-003 | US1 / Scenario 3 | SC-007 |
| FR-004 | Edge case (over-withdraw) | SC-007 |
| FR-005 | US1 / Scenario 4 | SC-002 |
| FR-016 (reproducibility) | US1 / Scenario 5 | SC-002 |
| FR-008, FR-009 | US2 / Scenario 1 | SC-003 |
| FR-010 | US2 / Scenarios 2-3 | SC-004 |
| US2 removal behavior | US2 / Scenario 4 | SC-002 |
| FR-011, FR-012 | US3 / Scenario 1 | SC-005 |
| FR-013 | US3 / Scenario 2 | SC-005 |
| FR-014 | US3 / Scenario 3 | SC-004, SC-005 |
| FR-015 | US3 / Scenario 4 | SC-005 |
| FR-016 (analytics) | US3 / Scenario 5 | SC-002 |
| FR-017 | Edge case (corrected sector/price) | SC-002, SC-004 |
| FR-018 | Edge case (unsupported symbol) | SC-007 |
| DATA-001, DATA-003, DATA-005 | US1, US3 | SC-001, SC-005 |
| DATA-002 | US1 / Scenario 4; Edge cases | SC-001, SC-007 |
| DATA-004 | Edge cases; US2/US3 unavailable scenarios | SC-004 |
| SEC-001, SEC-002 | Owner-only access edge case | SC-009 |
| NFR-001, NFR-002 | US1-US3 timing | SC-006 |
| NFR-003 | US1-US3 | SC-008 |
