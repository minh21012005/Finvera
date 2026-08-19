# Fixture-mode acceptance evidence

**Feature:** `003-stock-screener`
**Executed:** 2026-08-19
**Scope:** loopback-only development (`127.0.0.1`); no external live provider
activation and no remote deployment. The screener adds no provider of its
own — it reads only tables Features 001/002 already accept and persist.

## Automated evidence

| Boundary | Command/evidence | Result |
|---|---|---|
| Spring domain, API, security, persistence, replay, performance, fault-injection | `finvera-be\.\mvnw.cmd test` | PASS — 318 tests, 0 failures/errors (full repository suite, includes Features 001/002 unchanged) |
| `screener-v1` engine (all four filter categories, Breakout/Trend, S-4 exclusions, replay) | `ScreenerV1Tests` | PASS — 22/22 |
| Two-pass orchestration (Market/Price/Technical/Fundamental, coherence key, sorting) | `ScreenerServiceTests` | PASS — 9/9 |
| Contract/security (`POST /screener/executions`), including CSRF-required-even-when-authenticated | `ScreenerControllerTests` | PASS — 5/5 |
| Owner-only negative security | `ScreenerSecurityTests` | PASS — 1/1, `ScreenerService` never invoked when unauthenticated |
| Latency smoke (NFR-001) | `ScreenerPerformanceTests` | PASS — p95 within the 5-second baseline across 20 multi-category screens over a 60-instrument universe |
| Fault injection — degraded category vs. legitimate empty result (NFR-002) | `ScreenerFailureTests` | PASS — 2/2; also caught and fixed a real defect (see `research.md` R-002 amendment, `tasks.md` T028) |
| Persisted replay determinism (SC-003) | `ScreenerReplayDeterminismTests` | PASS — 1/1, identical match set/values/coherence key across two runs |
| `fundamental-summary-v1` `REVENUE_GROWTH_PERCENT` extension | `FundamentalSummaryTests` | PASS — 16/16 (12 pre-existing + 4 new), 0 regressions to `EPS_GROWTH_PERCENT` |
| Architecture boundaries (`stock` module reads `market` only via the published interface) | `StockModuleArchitectureTests`, `MarketModuleArchitectureTests` | PASS — 4/4, 3/3 |
| React unit/component | `finvera-fe\npm run test` | PASS — 45 tests in 10 files (8 new in `screener.test.tsx`) |
| React static quality | `finvera-fe\npm run lint` | PASS — 0 errors, 0 warnings |
| React production build | `finvera-fe\npm run build` | PASS — `tsc --noEmit` + `vite build` both clean |
| Browser journeys (P1-P3) & accessibility | `finvera-fe\npx playwright test` | PASS — 42 E2E journeys total (30 pre-existing Features 001/002 + 12 new screener journeys), 0 axe violations |
| Production-bundle secret scan | `rg -a -l -i '(tcbs.{0,24}(api.?key\|token\|secret)\|iotp.{0,24}[:=])' finvera-fe\dist` | PASS — 0 matches; broader manual scan's one hit inspected and confirmed benign (login form `password` field, `credentials: "same-origin"` fetch option) |

## Scenarios exercised (spec.md acceptance criteria)

| Story | Scenario | Evidence |
|---|---|---|
| P1 | Combined Market+Price screen returns exactly the matching set with qualifying values | `ScreenerServiceTests.marketAndPriceScreenReturnsExactlyTheMatchingInstruments`; `stock-screener.spec.ts` P1 |
| P1 | Empty filter set returns the full `LISTED` candidate universe | `ScreenerServiceTests.emptyRequestReturnsTheFullListedCandidateUniverse`; `ScreenerV1Tests.noFiltersSelectedMatchesEveryCandidate` |
| P1 | No match is a specific empty state, not an error | `ScreenerV1Tests`; `stock-screener.spec.ts` P1; `screener.test.tsx` |
| P1 | Contradictory range rejected before any query runs | `ScreenerV1Tests.contradictoryRangeIsRejectedBeforeAnyCandidateIsEvaluated`; `ScreenerServiceTests.contradictoryPriceRangeIsRejectedBeforeAnyQueryRuns`; `ScreenerControllerTests`; `stock-screener.spec.ts` P1 |
| P1 | Navigate from a result into the existing stock detail page | `stock-screener.spec.ts` P1 navigation journey |
| P2 | Technical filters (RSI/MACD/MA-relationship/Volume/Relative-Volume/Breakout/Trend) read persisted indicator results, never recompute | `ScreenerV1Tests`; `ScreenerServiceTests.technicalFilterEvaluatesPersistedIndicatorResultsNotRawBars` |
| P2 | Insufficient-history candidate excluded, not silently passed/failed | `ScreenerV1Tests`; `ScreenerServiceTests.technicalFilterExcludesAnInstrumentWithFewerBarsThanTheIndicatorRequires`; `ScreenerFailureTests` |
| P2 | Three-category intersection matches correctly | `ScreenerV1Tests.threeCategoryIntersectionMatchesOnlyStocksSatisfyingEveryFilter`; `stock-screener.spec.ts` P2 |
| P2 | Reproducible across repeated runs | `ScreenerV1Tests.replayIsDeterministic`; `ScreenerServiceTests.repeatedIdenticalScreensProduceAnIdenticalCoherenceKey`; `ScreenerReplayDeterminismTests` |
| P3 | `REVENUE_GROWTH_PERCENT` (new) and `EPS_GROWTH_PERCENT` (existing) both reachable end-to-end | `ScreenerServiceTests.fundamentalFilterUsesTheRevenueGrowthPercentExtension`; `FundamentalSummaryTests` |
| P3 | Withheld valuation excludes a stock from P/E-P/B filters entirely, never partially | `ScreenerV1Tests.peFilterExcludesWithheldValuationEntirely`; `ScreenerServiceTests.peFilterExcludesAnInstrumentWhoseValuationIsWithheld`; `stock-screener.spec.ts` P3 |
| P3 | Negative-earnings P/E is `NOT_APPLICABLE`, distinct from missing | `ScreenerV1Tests.peFilterExcludesNegativeEarningsNotApplicableDistinctFromMissing` |
| All | Non-advice framing present in the UI | `screener.test.tsx`; `stock-screener.spec.ts` (all three P1/P2/P3 groups) |
| All | Non-color status indicators | `screener.test.tsx`; `ScreenerResults` renders text labels alongside `status-pill` classes |

## Live-stack smoke verification (2026-08-19, beyond the automated suite)

Every automated test above exercises a mocked or in-process boundary
somewhere (MockMvc, `page.route`, Testcontainers-with-Spring-context). To
close that gap, a one-off manual pass ran the real, live components
together: a throwaway local Postgres 17 container, the real `spring-boot:run`
backend, and the real `npm run dev` frontend, with zero network mocking.
This is exactly how it caught a defect the fully-mocked suite structurally
could not: `executeScreen`'s missing CSRF header (see `tasks.md` T030
follow-up, `plan.md` header). Verified against the live stack, post-fix:

| Check | Result |
|---|---|
| Unauthenticated `POST /screener/executions` | 403 (CSRF filter fires before authentication in the real chain ordering) |
| Authenticated session, no CSRF token | 403 |
| Real login form → real session cookie → real CSRF fetch → real screener POST | 200, correct `ScreenResponse` JSON, correctly parsed and rendered by the real frontend as "Kết quả (0 mã)" against the genuinely empty throwaway database |
| Selected filter against zero candidates | `categoryDisclosures` correctly reports `UNAVAILABLE`/`NO_CANDIDATES`, matching the fixture-mode test expectation |

All smoke infrastructure (the Postgres container, both dev-server processes,
the one-off local owner account, and the throwaway test file that drove
this) was torn down afterward; none of it is committed or persists.

## Gates and manual steps intentionally still open

- No new provider gate is opened by this feature (research R-011); Feature
  002's G-01 to G-04 posture is unchanged.
- Feature 001's T051 Tailscale Serve-only ingress runbook remains a
  mandatory pre-deployment gate, inherited unchanged.
- No owner-timed usability trial is required by this feature's spec (unlike
  Feature 002's SC-001); none is recorded here.

Fixture acceptance proves deterministic product behavior only. It does not
prove live-data entitlement, freshness, or deployment readiness.
