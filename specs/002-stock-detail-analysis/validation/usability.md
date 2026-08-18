# Owner usability validation

**Feature:** `002-stock-detail-analysis`  
**Success criterion:** SC-001  
**Task:** T073  
**Status:** Awaiting three manual owner trials

This check must be performed by the owner against the locally running fixture
application. Automated Playwright timing is useful performance evidence but is
not a substitute for a human comprehension trial.

## Setup

1. Start PostgreSQL and the backend in explicit fixture mode as documented in
   `../quickstart.md`.
2. Start the Vite client on `127.0.0.1` and sign in as the configured local
   owner.
3. Open a supported stock detail view (`/stocks/FPT`).
4. For each trial, begin timing when the stock detail view becomes visible.
5. Stop timing after identifying the stock's price, direction, and session status.
   Do not record credentials or screenshots containing them.

## Evidence

| Trial | Date/time (Asia/Ho_Chi_Minh) | Duration (seconds) | Price identified correctly | Direction identified correctly | Session status identified | Notes |
|---|---|---:|---|---|---|---|
| 1 |  |  |  |  |  |  |
| 2 |  |  |  |  |  |  |
| 3 |  |  |  |  |  |  |

## Decision

T073 and SC-001 pass only when every row is complete, every answer is correct,
and each duration is at most 10 seconds. If any trial fails, leave T073 open
and return the finding to `spec.md` and `plan.md` before changing the UI.
