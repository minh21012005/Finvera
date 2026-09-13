# Quickstart: Deterministic Position Sizing

## Prerequisites

- Docker Desktop is available for backend Testcontainers.
- Backend/frontend dependencies and Feature 004/005 fixtures are available.
- Review `contracts/position-sizing-v1.md` before accepting numerical output.

## Targeted validation

```powershell
cd finvera-be
.\mvnw.cmd "-Dtest=PositionSizingV1Tests,PositionSizingControllerTests,PositionSizingServiceTests,PositionSizingSecurityTests,PositionSizingPerformanceTests" test

cd ..\finvera-fe
npm test -- src/features/position-sizing/position-sizing.test.tsx src/features/position-sizing/api/position-sizing.test.ts
```

## P1 manual journey

Enter a manual scenario with explicit costs. Verify risk and cash candidates are
separate, omitted exposure caps are `NOT_APPLIED`, final quantity is the lowest
candidate floored to 100 shares, required cash/risk stay within limits, and all
values show units and sources. Repeat it; all financial outputs must match while
only `calculatedAt` may differ.

## Critical variants

- Fixed VND versus percentage risk; both/neither is rejected.
- Explicit cost exclusion emits `COSTS_EXCLUDED`; incomplete cost input is 422.
- Risk/cash below one lot returns `WITHHELD` without a positive quantity.
- Equal candidate caps are all binding.
- Current long signal plus entry basis and confirmation resolves on the server;
  an edited imported value becomes manual.
- Owned portfolio supplies one coherence key for cash/value/exposure.
- Foreign/missing portfolio UUIDs return the same 404.
- Stale/missing portfolio or signal facts withhold; manual mode remains usable.

## Performance, accessibility, and privacy

- At least 100 warmed requests per mode: manual p95 ≤1s; portfolio p95 ≤2s.
- Complete and inspect the form by keyboard; statuses do not rely on color.
- Logs/metrics contain no symbol, portfolio UUID, capital, prices, rates,
  quantity, or full request/response.
- Conduct the ten-scenario owner comprehension review and record whether every
  binding cap and non-increase reason is understood in `validation/owner-review.md`.

## Full gates

```powershell
cd finvera-be
.\mvnw.cmd test

cd ..\finvera-fe
npm run lint
npm test
npm run build
npm run test:e2e -- --grep "position sizing"
```

Record counts, timings, skips, and blockers after implementation. Do not mark
delivered until contract, financial, authorization, and journey evidence pass.

## Validation record — 2026-09-13

| Check | Result |
|---|---|
| Backend Feature 030 targeted suite | PASS — 41 tests, including HTTP-boundary performance, provenance, incoherent-snapshot, privacy, and shared error-handler regression checks |
| Frontend Feature 030 API/component suite | PASS — 8 tests |
| Full frontend Vitest | PASS — 34 files, 177 tests |
| Frontend lint | PASS |
| Frontend production build | PASS — existing bundle-size warning remains (`524.34 kB`) |
| Feature 030 Playwright + axe scan scoped to `main` | PASS — 1 test; no feature-surface accessibility violation |
| OpenAPI YAML parse | PASS — OpenAPI 3.1, 17 schemas; money/rate precision patterns exercised |
| Full backend Maven suite | PASS — 797 tests, 0 failures, 0 errors, 0 skipped; Docker-backed Testcontainers/Flyway coverage included; 6 min 15 s |
| Owner comprehension review | PENDING — see `validation/owner-review.md` |

An initial whole-page axe scan also found pre-existing navigation/footer issues:
the global navigation uses `role=tablist` with link children, and three global
text styles do not meet contrast thresholds. They are outside the Feature 030
`main` surface and remain a separate accessibility debt; the feature result is
not being used to claim that the entire application shell passes axe.
