# Fixture-mode acceptance evidence

**Feature:** `001-market-overview`  
**Executed:** 2026-08-17  
**Scope:** loopback-only development; no TCBS/Vnstock activation and no remote deployment.

## Automated evidence

| Boundary | Command/evidence | Result |
|---|---|---|
| Spring domain, API, security, persistence | `finvera-be/.\mvnw.cmd test` | PASS — 83 tests, 0 failures/errors |
| Runtime fixture + PostgreSQL | `FixtureRuntimeBootstrapServiceTests` with PostgreSQL 17 Testcontainers | PASS — owner-authenticated HTTP overview, four indices, six breadth inputs, five regime factors, provenance links, idempotent replay |
| Fixture activation safety | `FixtureBootstrapConfigurationTests` | PASS — disabled by default and for non-fixture provider mode |
| React unit/component | `finvera-fe/npm run test` | PASS — 19 tests in 6 files |
| React static quality | `finvera-fe/npm run lint` | PASS |
| React production bundle | `finvera-fe/npm run build` | PASS |
| Browser journeys | `finvera-fe/npm run test:e2e` | PASS — 10 Chromium journeys |

The full quality gate was rerun after artifact reconciliation and final reason-code
alignment on 2026-08-17 with the same passing counts.

The browser journeys cover P1 complete/delayed/closed/missing-index and denied
owner access; P2 complete/partial/unavailable breadth; and P3 deterministic
replay, withheld/conflicting inputs, correction, disclosure, and automated
accessibility checks. Browser request observation confirms these fixtures do
not contact an AI service or external market provider.

## Gates intentionally still open

- T045/T046: TCBS live schema, in-session timing/correction/rate-limit evidence
  and the live adapter are not approved or implemented.
- T047/T048: Vnstock upstream-use/full-universe gates and canonical importer are
  not approved or implemented.
- T051: Tailscale Serve/private-ingress validation is deferred until deployment
  or remote/multi-device access. Local acceptance is restricted to `127.0.0.1`.
- SC-001 manual owner timing is recorded separately by T056; automated browser
  completion is not represented as a human usability trial.

Fixture acceptance proves deterministic product behavior only. It does not
prove live-data entitlement, freshness, redistribution rights, or deployment
readiness.
