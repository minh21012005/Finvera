# Fixture-mode acceptance evidence

**Feature:** `002-stock-detail-analysis`  
**Executed:** 2026-08-19  
**Scope:** loopback-only development (`127.0.0.1`); no external live provider activation and no remote deployment.

## Automated evidence

| Boundary | Command/evidence | Result |
|---|---|---|
| Spring domain, API, security, persistence, replay | `finvera-be/.\mvnw.cmd test` | PASS — 258 tests, 0 failures/errors |
| Performance latency smoke & Coherence keys | `StockDetailPerformanceTests` | PASS — p95 <= 500ms across all 5 sections, mutation detected by coherence key |
| Failure taxonomy & safe telemetry | `StockDetailFailureTests` | PASS — 6 failure classes distinguished, credentials/tokens redacted |
| Determinism replay | `StockReplayDeterminismTests` | PASS — 100% exact bitwise decimal recomputation from recorded inputs |
| Negative security & forbidden operations | `StockDetailSecurityTests` | PASS — unauthenticated denied 401 across all endpoints; trading/cash operations unreachable |
| React unit/component | `finvera-fe/npm test` | PASS — 37 tests in 9 files |
| React static quality | `finvera-fe/npm run lint` | PASS — 0 errors |
| React production bundle & secret scan | `finvera-fe/npm run build` | PASS — 0 secrets in dist bundle |
| Browser journeys & Accessibility | `finvera-fe/npx playwright test` | PASS — 30 E2E journeys (P1, P2, P3, accessibility 0 violations) |
| Python AI Service Syntax | `finvera-ai/uv run python -m compileall .` | PASS — 0 errors |

## Gates intentionally still open

- G-01 (T057/T058): Vnstock Finance live schema, period semantics, and restatement evidence are not closed; fixture adapter active.
- G-02 (T059/T060): Corporate actions split/dividend basis gate is not closed; historical series served as RAW with `ADJUSTMENT_BASIS_UNAVAILABLE`.
- G-03 (T061/T062): TCBS per-stock quote coverage gate is not closed; accepted daily bar close served.
- G-04 (T063/T064): Sector reference coverage gate is not closed; valuation publishes on own-history basis alone with `SECTOR_BASIS_INSUFFICIENT` disclosed.
- T051: Tailscale Serve private ingress deferral remains in force.
- SC-001 manual owner timing is recorded in `usability.md`.

Fixture acceptance proves deterministic product behavior only. It does not prove live-data entitlement, freshness, redistribution rights, or deployment readiness.
