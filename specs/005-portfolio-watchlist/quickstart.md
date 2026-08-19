# Quickstart and Acceptance: Portfolio and Watchlist Management

**Feature**: `005-portfolio-watchlist`
**Status**: Draft — plan stage. Commands below are the intended
acceptance path; mark them "verified" only once real test evidence exists,
per `AGENTS.md`'s "never claim a check passed if it was not run" rule.

Every command below runs against `127.0.0.1` only, per the same Tailscale
Serve-only ingress runbook Features 001-004 already require.

## Prerequisites

1. Features 001-004 are running locally and their quickstarts pass. This
   feature adds no new provider and no new authentication path — it reads
   tables Features 001-004 already populate and adds the platform's first
   owner-scoped write tables (research R-002).
2. Fixture mode is active (unchanged from Features 002-004).
3. At least one fixture portfolio ledger per FIFO scenario (research
   R-010): simple buy/sell, partial close, backdated insert, a voided
   entry, an over-sell rejection, and an over-withdraw rejection.
4. At least two fixture instruments in different sectors, one with a
   current Feature 004 signal and one without, for watchlist and
   risk-exposure acceptance.

## Configuration

One new configuration key: `finvera.portfolio.max-performance-history-span-days`,
default `730` (research R-006's bound on the performance-history/drawdown
query). No new provider flag; this feature calls no external provider,
live or fixture.

## Quality commands

```powershell
cd D:\Finvera\finvera-be
.\mvnw.cmd test

cd D:\Finvera\finvera-fe
npm run lint
npm run test
npm run build
npx playwright test
```

Feature-scoped backend suites (once implemented):

```powershell
cd D:\Finvera\finvera-be
.\mvnw.cmd '-Dtest=PortfolioAnalyticsV1Tests,PortfolioTransactionServiceTests,PortfolioControllerTests,WatchlistServiceTests,WatchlistControllerTests,PortfolioSecurityTests' test
```

## Local runtime

```powershell
# terminal 1, from finvera-be/
.\mvnw.cmd spring-boot:run

# terminal 2, from finvera-fe/
npm run dev
```

---

## P1 acceptance — record transactions and see current holdings

**Path**: `POST /api/v1/portfolios`, `POST /api/v1/portfolios/{id}/transactions`, `GET /api/v1/portfolios/{id}/positions`

| Step | Expected |
|---|---|
| Create a portfolio, `DEPOSIT`, then `BUY` a supported symbol | Cash balance reduced correctly; one open position at the entered quantity/price as cost basis; unrealized P/L against the latest accepted price (FR-001, FR-002, FR-006). |
| `SELL` part of the position at a different price | Remaining quantity and average cost basis update by FIFO; a realized P/L is recorded for the closed lot; unrealized P/L reflects only the still-open lots (`portfolio-analytics-v1` FIFO section). |
| Attempt to `SELL` more than the held quantity | Rejected with `INSUFFICIENT_POSITION`; zero ledger/position/balance change (FR-003). |
| `VOID` a mistaken transaction | A new `VOID` entry is recorded referencing the original; the original remains queryable via the ledger list, neither row is edited or deleted (FR-005). |
| Resend the exact same `POST .../transactions` request with the same `Idempotency-Key` (simulating a client retry) | Rejected `409 DUPLICATE_SUBMISSION` referencing the original transaction id; no second ledger entry, no change to position/cash (FR-002, SC-007, research R-011). |
| Record a second, separately confirmed transaction with identical symbol/quantity/price/`executedAt` but a **fresh** `Idempotency-Key` | Accepted as a genuine second transaction — not treated as a duplicate (research R-011). |
| Reload the holdings view with unchanged inputs | Every position's quantity, cost basis, unrealized/realized P/L, and allocation are identical to the prior view (FR-016). |

---

## P2 acceptance — watchlist with live context

**Path**: `POST /api/v1/watchlists`, `POST /api/v1/watchlists/{id}/items`, `GET /api/v1/watchlists/{id}`

| Step | Expected |
|---|---|
| Create a watchlist, add a symbol with a current Feature 004 signal | Item shows price, daily change, trend, volume condition, signal direction, and risk level, matching that symbol's own Feature 002-004 views exactly (FR-008, FR-009). |
| Add a symbol with no current signal | Item shows a specific no-signal state, not an error (FR-009). |
| Add a symbol with insufficient accepted history for trend/volume | That field shows a stated reason, not a default (FR-010). |
| Remove a symbol, then delete the whole watchlist | Both take effect immediately; no other watchlist or any portfolio is affected (spec.md edge case). |

---

## P3 acceptance — portfolio analytics and benchmark comparison

**Path**: `GET /api/v1/portfolios/{id}/analytics`

| Step | Expected |
|---|---|
| Open analytics for a portfolio with a multi-day transaction history | Return since inception, return over a selectable period, max drawdown, and a performance-history series all appear, reconstructed from the ledger and historical accepted prices (FR-011, FR-012). |
| Open analytics for a portfolio holding two stocks in two sectors | Stock and sector concentration each sum to the correct share of total portfolio value (FR-013). |
| Open analytics with one covered and one uncovered position (Feature 004 signal present vs. absent) | Risk exposure computed only from the covered position; `coverageRatio` states the uncovered proportion (FR-014). |
| Compare against VN-Index for the same period | Portfolio return and VN-Index return are shown side by side, both computed over the identical period (FR-015). |
| Request analytics with `from` earlier than the portfolio's first transaction | `periodFrom` is clamped to the actual inception date, `periodClampedToInception = true`, no fabricated pre-inception data point (`portfolio-analytics-v1` F6). |
| Reload analytics with unchanged inputs | Every metric is identical to the prior view (FR-016). |

---

## Degraded and failure paths

| Scenario | Expected |
|---|---|
| A held symbol's latest accepted price is stale/withheld (Feature 002 `SOURCE_CONFLICT`) | The affected position's unrealized P/L and portfolio total show a stated unavailable/stale reason, never a fabricated or silently reused value. |
| A watchlist symbol becomes unsupported/delisted after being added | Item remains listed with a stated unavailable reason, not silently dropped. |
| A sector classification a concentration figure depends on is corrected | Affected concentration figures recompute; the superseded classification is never silently kept (FR-017). |
| Analytics requested for a period before the portfolio's first transaction | States no history exists for that period, never zero or fabricated values. |
| A `VOID` targets a `BUY` whose lot was already partially sold | Rejected with `LOT_ALREADY_CONSUMED` (`portfolio-analytics-v1` U-6). |
| Repeated identical transaction submission with the same `Idempotency-Key` (e.g., a retried request) | `409 DUPLICATE_SUBMISSION`, no duplicate ledger entry (research R-011). |

## Authorization checks

| Check | Expected |
|---|---|
| Unauthenticated request to any endpoint | HTTP 401. |
| Any state-changing request without `X-CSRF-TOKEN`, even with a valid session | HTTP 403, no state change. |
| `recordTransaction`/`voidTransaction` without an `Idempotency-Key` header, even with a valid session and CSRF token | HTTP 400, no state change — the header is required, not optional (research R-011). |
| A request for a portfolio/watchlist/transaction the owner does not own | Indistinguishable from 404 — never a distinct 403 that confirms existence (SEC-002, research R-002). |
| Response, log, and export inspection | No credential, token, another portfolio's data, or raw provider payload. |

## Accessibility

| Check | Expected |
|---|---|
| P/L sign, allocation, drawdown, risk-exposure states | Each has a text or icon indicator independent of colour (NFR-003). |

## Release gates that remain open

This feature opens no new gate and closes none of Features 001-004's. Its
release posture is exactly Feature 004's: Feature 002's G-01 to G-04 and
Feature 001's T051 Tailscale ingress runbook remain the governing
pre-deployment gates.
