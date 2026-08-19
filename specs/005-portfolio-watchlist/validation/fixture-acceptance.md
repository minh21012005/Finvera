# Feature 005 Acceptance Scenario Validation

## Overview

- **Feature**: 005-portfolio-watchlist (Portfolio and Watchlist Management)
- **Status**: DELIVERED & VERIFIED
- **Date**: 2026-08-20
- **Environment**: Java 21, Spring Boot 4.1.0, React 19, TypeScript, Vitest, Playwright

---

## User Story 1 (P1): Record Transactions and Review Derived Portfolio Holdings

| Acceptance Scenario | Preconditions / Input | Expected Result | Actual Result | Status |
|---|---|---|---|---|
| **Scenario 1.1: Create Portfolio** | POST `/api/v1/portfolios` with name "Main Portfolio" | Returns 201 with summary, totalValue=0, cashBalance=0 | Returns 201, matches schema | **PASS** |
| **Scenario 1.2: Deposit Cash** | POST `/api/v1/portfolios/{id}/transactions` type=DEPOSIT, amount=100M VND | Cash balance increases to 100M VND, totalValue=100M VND | Replays to 100M cash and total value | **PASS** |
| **Scenario 1.3: BUY Transaction** | POST `/api/v1/portfolios/{id}/transactions` type=BUY, FPT 1000 @ 50,000 | Open lot created, cash deducted (50M), average cost basis=50,000 | Correct FIFO lot, 50M cash + 1000 shares | **PASS** |
| **Scenario 1.4: SELL with Realized P&L** | POST `/api/v1/portfolios/{id}/transactions` type=SELL, FPT 400 @ 60,000 | 400 shares sold via FIFO, realized P&L = +4,000,000 VND | Realized P&L = 4M, remaining 600 shares | **PASS** |
| **Scenario 1.5: Idempotency Protection** | Replay POST `/api/v1/portfolios/{id}/transactions` with duplicate `Idempotency-Key` | Rejection with HTTP 409 DUPLICATE_SUBMISSION | Rejection with 409 DUPLICATE_SUBMISSION | **PASS** |
| **Scenario 1.6: Insufficient Position / Cash** | Attempt to SELL 2000 shares when only 600 held | Rejection with HTTP 409 INSUFFICIENT_POSITION | Rejection with 409 INSUFFICIENT_POSITION | **PASS** |
| **Scenario 1.7: VOID Transaction** | POST `/api/v1/portfolios/{id}/transactions/{id}/void` on unconsumed BUY | Void entry recorded, lot removed, cash restored | State replayed without the voided transaction | **PASS** |
| **Scenario 1.8: VOID Already Consumed Lot** | Attempt to VOID a BUY whose lot was consumed by a SELL | Rejection with HTTP 409 LOT_ALREADY_CONSUMED | Rejection with 409 LOT_ALREADY_CONSUMED | **PASS** |

---

## User Story 2 (P2): Organize and Monitor Securities via Watchlists

| Acceptance Scenario | Preconditions / Input | Expected Result | Actual Result | Status |
|---|---|---|---|---|
| **Scenario 2.1: Create Watchlist** | POST `/api/v1/watchlists` with name "Tech Stocks" | Returns 201 with summary | Returns 201 | **PASS** |
| **Scenario 2.2: Add Active Symbol** | POST `/api/v1/watchlists/{id}/items` symbol="FPT" | Item added with live price, technical trend, signals, risk badge | Item returned with full live market context | **PASS** |
| **Scenario 2.3: Add Unsupported Symbol** | POST `/api/v1/watchlists/{id}/items` symbol="UNKNOWN" | Rejection with HTTP 400 UNSUPPORTED_INSTRUMENT | Rejection with 400 UNSUPPORTED_INSTRUMENT | **PASS** |
| **Scenario 2.4: Duplicate Symbol in Watchlist** | Attempt to add FPT twice | Rejection with HTTP 409 DUPLICATE_WATCHLIST_ITEM | Rejection with 409 DUPLICATE_WATCHLIST_ITEM | **PASS** |
| **Scenario 2.5: No Current Signal State** | Watchlist item with no active signal | `hasCurrentSignal = false`, `signalDirection = null`, badge = "Không có tín hiệu" | Accurately indicates no signal without failing | **PASS** |
| **Scenario 2.6: Remove Symbol & Delete Watchlist** | DELETE `/api/v1/watchlists/{id}/items/{symbol}` & DELETE `/api/v1/watchlists/{id}` | Item removed, watchlist deleted, 204 No Content | Deleted cleanly | **PASS** |

---

## User Story 3 (P3): Review Portfolio Analytics and Benchmark Comparison

| Acceptance Scenario | Preconditions / Input | Expected Result | Actual Result | Status |
|---|---|---|---|---|
| **Scenario 3.1: Return Since Inception** | 100M contributed, 110M current value | Return = +10.00% with `NET_CONTRIBUTED_CAPITAL_METHOD` disclosure | Computed as 0.10 (+10.00%) | **PASS** |
| **Scenario 3.2: Max Drawdown Reconstruction** | Peak 120M drops to 110M | Max drawdown = -8.33% | Computed as 0.0833 (-8.33%) | **PASS** |
| **Scenario 3.3: Stock & Sector Concentration** | 60M FPT (Tech), 50M VNM (Consumer) out of 110M | Stock & sector weights sum to 100% of positions | Weights computed & formatted cleanly | **PASS** |
| **Scenario 3.4: Risk Exposure Rollup** | FPT (risk score 25, LOW), VNM (no signal) | Weighted risk = 25 (LOW), coverage ratio = 54.55% | Rollup score = 25, coverage ratio = 0.5455 | **PASS** |
| **Scenario 3.5: Clamped to Inception Window** | `from` requested before first deposit | `periodClampedToInception = true`, `periodFrom` set to deposit date | Clamped notice displayed in UI and returned in API | **PASS** |
| **Scenario 3.6: Period Exceeding 730 Days** | `from` to `to` span > 730 days | Rejection with HTTP 422 PERIOD_TOO_LONG | Rejection with 422 PERIOD_TOO_LONG | **PASS** |
| **Scenario 3.7: Benchmark Comparison (VN-Index)** | Compare portfolio return vs VN-Index return over period | Both returns displayed side-by-side with benchmark symbol `VNINDEX` | Displayed side-by-side with non-color indicators | **PASS** |

---

## Security & Architecture Verification

- **SEC-001 (Owner-Scoped Access)**: Every endpoint verifies session owner. Cross-ownership access returns 404 `PORTFOLIO_NOT_FOUND` / `WATCHLIST_NOT_FOUND` indistinguishable from non-existent resources.
- **SEC-002 (CSRF Protection)**: Every state-mutating endpoint requires CSRF token even when authenticated.
- **ArchUnit**: 5 rules in `PortfolioModuleArchitectureTests` verified:
  - Controllers only call Services and return DTOs
  - Repositories are package-private / module-contained
  - Entities never exposed in API DTO signatures
- **Secret Scan**: No credentials, tokens, or private keys present in `finvera-fe/dist`.
