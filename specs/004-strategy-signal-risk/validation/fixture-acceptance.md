# Fixture-mode acceptance evidence

**Feature:** `004-strategy-signal-risk`
**Executed:** 2026-08-19
**Scope:** loopback-only development (`127.0.0.1`); no external live provider
activation and no remote deployment. This feature adds no provider of its
own — it reads only tables Features 001-003 already accept and persist.

## Automated evidence

| Boundary | Command/evidence | Result |
|---|---|---|
| Spring domain, API, security, persistence, replay, performance, fault-injection | `finvera-be\.\mvnw.cmd test` | PASS — 384 tests, 0 failures/errors (full repository suite, includes Features 001-003 unchanged) |
| `strategy-signal-v1` engine (all 8 strategies, boundary/crossing/ATR-invalid/risk-floor/replay required-test-vectors) | `StrategySignalV1Tests` | PASS — 31/31 |
| Single-stock signal orchestration (persist-only-on-trigger, non-persistence of non-triggers, idempotent replay, revision-chain supersession) | `StrategySignalServiceTests` | PASS — 4/4 |
| Universe scan orchestration (exact match set, empty result, insufficient-history exclusion count, pagination) | `StrategyScanServiceTests` | PASS — 4/4 |
| Contract/security `GET /stocks/{symbol}/signals` | `SignalControllerTests` | PASS — 5/5 |
| Contract/security `POST /strategies/{strategyCode}/scan`, including CSRF-required-even-when-authenticated verified from the first implementation | `StrategyScanControllerTests` | PASS — 4/4 |
| Owner-only negative security, both endpoints | `StrategySignalSecurityTests` | PASS — 3/3, neither service invoked when unauthenticated/CSRF-missing |
| `MarketReferenceDataService.findCurrentRegimeAssessment` + `market`/`stock` architecture boundary | `MarketModuleArchitectureTests`, `StockModuleArchitectureTests` | PASS — 3/3, 4/4 |
| `V004` migration (three-table schema, all-or-nothing risk score/level, level ordering checks, three-state risk-factor applicability, revision-chain partial-unique index) | `StrategySignalMigrationTests` | PASS — 8/8 |
| Latency smoke (NFR-001, NFR-002) | `StrategySignalPerformanceTests` | PASS — p95 within the 3-second baseline for a single-stock signal view (repeated reads) and within the 5-second baseline for a strategy scan over a 60-instrument universe |
| Fault injection — cross-source conflict, stale regime, corrected-input recalculation | `StrategySignalFailureTests` | PASS — 3/3; no strategy ever reports a fabricated `SIGNAL` from disputed data |
| Persisted replay determinism (U-6) | `StrategySignalReplayDeterminismTests` | PASS — 2/2, identical levels/risk/coherence key across two runs (signal view and scan) |
| React unit/component | `finvera-fe\npm run test` | PASS — 62 tests in 13 files (17 new: 6 `signals.test.tsx`, 5 `risk-detail.test.tsx`, 6 `strategy-scan.test.tsx`) |
| React static quality | `finvera-fe\npm run lint` | PASS — 0 errors, 0 warnings |
| React production build | `finvera-fe\npm run build` | PASS — `tsc --noEmit` + `vite build` both clean |
| Browser journeys (P1-P3) & accessibility | `finvera-fe\npx playwright test` | PASS — 55 E2E journeys total (42 pre-existing Features 001-003 + 13 new: 9 `stock-signals.spec.ts` P1/P2 + 4 `stock-strategy.spec.ts` P3) |
| Production-bundle secret scan | manual `grep -iE 'password\|secret\|api[_-]?key\|BEGIN (RSA\|EC\|PRIVATE) KEY\|bearer [a-z0-9]\|AKIA[0-9A-Z]{16}' finvera-fe\dist` | PASS — every `password` hit inspected and confirmed benign (Feature 001's login form field/label/`autoComplete`, pre-existing and unrelated to this feature); no strategy/signal/risk data or CSRF token literal present |

## Scenarios exercised (spec.md acceptance criteria)

| Story | Scenario | Evidence |
|---|---|---|
| P1 | A triggered strategy shows direction, entry zone, stop, targets, risk/reward, risk score/level, evidence, as-of time | `StrategySignalV1Tests`; `StrategySignalServiceTests.trendFollowingTriggersAndIsPersistedWithLevelsAndSixRiskFactors`; `stock-signals.spec.ts` P1 |
| P1 | A non-triggering strategy shows `NO_SIGNAL`, not an error, and is never persisted | `StrategySignalV1Tests`; `StrategySignalServiceTests.aNonTriggeringStrategyIsReportedLiveAndNeverPersisted`; `stock-signals.spec.ts` P1; `signals.test.tsx` |
| P1 | Fewer bars than a strategy's minimum shows `INSUFFICIENT_HISTORY`; other evaluable strategies stay correct | `StrategySignalV1Tests`; `stock-signals.spec.ts` P1; `signals.test.tsx` |
| P1 | Two strategies triggering at once are both shown, each correctly attributed | `stock-signals.spec.ts` P1 multi-trigger journey |
| P1 | Section copy states a deterministic scenario, never a guarantee or instruction | `signals.test.tsx`; `stock-signals.spec.ts` P1 |
| P2 | Each of the six risk factors shows its own value and score when available | `StrategySignalV1Tests.riskFactorsCoverAllSixCodesWithScoresAndLevels`; `risk-detail.test.tsx`; `stock-signals.spec.ts` P2 |
| P2 | An unavailable factor discloses its reason; overall score still computed from the rest (4-of-6 floor) | `StrategySignalV1Tests`; `StrategySignalFailureTests.aStaleRegimeAssessmentIsDisclosedUnavailableWhileTheOverallScoreStillPublishesFromTheRest`; `risk-detail.test.tsx`; `stock-signals.spec.ts` P2 |
| P2 | Fewer than four factors withholds the overall score/level; the signal itself stays shown | `StrategySignalV1Tests.riskScoreIsWithheldWithFewerThanFourAvailableFactors`; `risk-detail.test.tsx`; `stock-signals.spec.ts` P2 |
| P2 | Reload reproduces identical factor values and overall score | `StrategySignalV1Tests.riskAssessmentReplaysIdentically`; `StrategySignalReplayDeterminismTests`; `stock-signals.spec.ts` P2 |
| P3 | Scan finds exactly the triggering stocks, each with its own signal summary | `StrategyScanServiceTests.scanFindsExactlyTheTriggeringInstrumentsForTrendFollowing`; `stock-strategy.spec.ts` P3 |
| P3 | Zero triggers in the universe is a specific empty-result state, not an error | `StrategyScanServiceTests.scanReturnsASpecificEmptyResultWhenNoCandidateTriggers`; `strategy-scan.test.tsx`; `stock-strategy.spec.ts` P3 |
| P3 | Insufficient-history exclusions are disclosed and distinguishable from a genuine empty result | `StrategyScanServiceTests.scanDisclosesInsufficientHistoryExclusionsSeparatelyFromGenuineNonTriggers`; `strategy-scan.test.tsx`; `stock-strategy.spec.ts` P3 |
| P3 | Navigation from a scan match into the existing stock detail page | `stock-strategy.spec.ts` P3 navigation journey |
| Degraded | Cross-source conflict withholds the affected strategy, never fabricates a signal | `StrategySignalV1Tests.strategyIsWithheldOnSourceConflictInADependency`; `StrategySignalFailureTests.aCrossSourceConflictWithholdsAffectedStrategiesRatherThanFabricatingASignal` |
| Degraded | A correction on a contributing bar recalculates the signal with a new revision; the superseded row stays queryable | `StrategySignalServiceTests.aCorrectedContributingIndicatorSupersedesThePreviousSignalRevision`; `StrategySignalFailureTests.aCorrectedContributingBarRecalculatesTheSignalWithANewAsOfIndicationAndKeepsThePriorRevisionQueryable` |
| Degraded | Repeated identical requests produce no side effect and identical results | `StrategySignalServiceTests.repeatedReadsAreIdempotentAndDoNotCreateDuplicateCurrentRevisions`; `StrategySignalReplayDeterminismTests` |
| Auth | Unauthenticated request to either endpoint is HTTP 401 | `StrategySignalSecurityTests`; `SignalControllerTests`; `StrategyScanControllerTests` |
| Auth | `POST` scan without a CSRF token, even with a valid session, is HTTP 403 — verified from the first implementation | `StrategyScanControllerTests.scanRequiresACsrfTokenEvenWithAnAuthenticatedOwnerSession`; `StrategySignalSecurityTests.scanWithAnAuthenticatedSessionButNoCsrfTokenIsDeniedWithoutInvokingTheService` |
| Auth | No credential, token, or raw provider payload in responses, logs, or the production bundle | `StrategySignalFailureTests`; production-bundle secret scan above |
| Accessibility | Direction, risk level, and signal strength each carry a text/icon indicator independent of colour | `signals.test.tsx`; `risk-detail.test.tsx`; `StockSignals`/`StrategyScanResults` render text labels alongside `risk-level-badge`/`status-pill` classes |

## Gates and manual steps intentionally still open

- No new provider gate is opened by this feature (research R-008); Feature
  002's G-01 to G-04 posture is unchanged.
- Feature 001's T051 Tailscale Serve-only ingress runbook remains a
  mandatory pre-deployment gate, inherited unchanged.
- No owner-timed usability trial is required by this feature's spec; none
  is recorded here.
- No fresh live-stack (real Postgres + real `spring-boot:run` + real
  `npm run dev`, zero mocking) smoke pass was run for this feature. Feature
  003's own such pass is what surfaced the missing-CSRF-header defect this
  feature deliberately built in from the start (`quickstart.md`'s
  Authorization checks table, `StrategyScanControllerTests`,
  `StrategySignalSecurityTests`) — the same gap class is therefore already
  closed by construction and covered by MockMvc's real Spring Security
  filter chain, not only by application-layer assertions. If the owner
  wants the live-stack pass repeated regardless, it is not yet done.

Fixture acceptance proves deterministic product behavior only. It does not
prove live-data entitlement, freshness, or deployment readiness.
