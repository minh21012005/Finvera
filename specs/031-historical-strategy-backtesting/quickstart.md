# Quickstart: Historical Strategy Backtesting

## Validation record — 2026-09-14

- Feature-focused backend: **40 passed, 0 failed/error/skipped**. This includes
  deterministic engine and decimal metrics, 100-run replay identity, all eight strategies, point-in-time input,
  create/result contracts, ownership/security, bounded telemetry, ten-year p95,
  simultaneous claim, heartbeat, stale recovery, second-attempt failure and
  atomic rollback.
- V022 and all preceding Flyway migrations applied successfully to PostgreSQL
  17.11 through Testcontainers. The persistence and repository integration
  suites passed **6/6** with no skips.
- Full backend: **839 passed, 0 failed/error/skipped** in 6m34s before the final
  test-only Sharpe vector was added; the production code was unchanged after
  this full run and the final feature suite then passed 40/40. Cached Spring
  contexts with background schedulers emitted warnings while trying to access
  already-closed Testcontainers; these did not fail assertions but are existing
  test-infrastructure noise and a source of suite latency.
- Frontend feature: **7/7**; full frontend: **35 files, 184/184 tests**. Polling,
  terminal stop, revisit, empty, withheld, failed and session-error flows pass.
- Frontend lint and TypeScript/Vite production build pass. The generated main
  chunk is 539.71 kB and still triggers the existing >500 kB advisory.
- Playwright backtest E2E: **1/1**, including keyboard operation and zero axe
  violations. OpenAPI 3.1 YAML parses successfully with all five paths.
- `git diff --check` reports no whitespace errors. Owner comprehension review
  remains the sole manual acceptance item in `validation/owner-review.md`.

## Prerequisites

- Docker Desktop is available for PostgreSQL integration tests.
- Accepted daily bars and historical technical indicators exist for the fixture
  symbol, including the selected strategy's warm-up.
- Feature 004 strategy contracts and Feature 030 sizing contracts pass.

## Pre-feature baseline — 2026-09-13

- Backend: 797 tests passed; 0 failures, errors, or skips.
- Frontend: 34 files and 177 tests passed; lint and production build passed.
- Feature 030 E2E passed. These counts are baseline evidence, not Feature 031
  completion evidence.

## Targeted validation

```powershell
cd finvera-be
.\mvnw.cmd "-Dtest=Backtest*Tests" test

cd ..\finvera-fe
npm test -- src/features/backtest
```

## P1 financial journey

Create a ten-year-or-shorter run for each of the eight strategies with initial
capital, per-tranche and aggregate risk, and all five cost rates. Verify the
request returns a queued run within one second, polling reaches a terminal state,
and completed fixtures match `contracts/backtest-financial-v1.md` exactly.

Check next-open entry, gap exits, stop-first ambiguity, final liquidation,
false-to-true signal episodes, the exact `0.5 * ATR14` pyramid boundary, four
open tranches, aggregate heat, standard-lot floors, and cash identities.

## Metrics and evidence

Verify all eight metrics against independent golden vectors. Exercise no trades,
all wins, all losses, flat equity, leap-day, short-period, and undefined Sharpe
or profit factor. Inspect source, accepted-time cutoff, selected-input
fingerprint, adjustment basis, rule versions, costs, warnings, and survivorship
limitations.

## Security and recovery

- A second owner and an unknown UUID receive indistinguishable not-found results.
- Duplicate idempotency keys return one run.
- Concurrent claims execute once; forced interruption recovers once and fails
  after the second unsuccessful attempt.
- No partial trade/equity/metric child is visible before `COMPLETED`.
- Logs and metrics contain no capital, rate, symbol, trade ledger, or owner data.

## Full gates

```powershell
cd finvera-be
.\mvnw.cmd test

cd ..\finvera-fe
npm run lint
npm test
npm run build
npm run test:e2e -- --grep "backtest"
```

Record counts, timings, skips, and environmental blockers here after
implementation. Do not mark delivered until numerical, look-ahead,
authorization, persistence, recovery, performance, and owner-comprehension
evidence pass.
