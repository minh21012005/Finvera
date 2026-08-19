# Quickstart and Acceptance: Strategy, Signal, and Risk Scenarios

**Feature**: `004-strategy-signal-risk`
**Status**: Implemented and verified against fixture-mode data. Every
command below is real; see `validation/fixture-acceptance.md` for the
recorded evidence.

Every command below runs against `127.0.0.1` only, per the same Tailscale
Serve-only ingress runbook Features 001-003 already require.

## Prerequisites

1. Features 001-003 are running locally and their quickstarts pass. This
   feature adds no new provider and no new authentication path — it reads
   tables Features 001-003 already populate.
2. Fixture mode is active (unchanged from Feature 002/003).
3. At least one fixture instrument per strategy with a hand-verified
   trigger case and a non-trigger case (research R-009); the three
   crossing strategies additionally need a "false yesterday, true today"
   fixture pair.

## Configuration

No new configuration key. Reuses `finvera.stock.provider-mode` and every
existing flag unchanged; this feature calls no provider, live or fixture.

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
.\mvnw.cmd '-Dtest=StrategySignalV1Tests,StrategySignalServiceTests,SignalControllerTests,StrategyScanServiceTests' test
```

## Local runtime

```powershell
# terminal 1, from finvera-be/
.\mvnw.cmd spring-boot:run

# terminal 2, from finvera-fe/
npm run dev
```

---

## P1 acceptance — a stock's current signals

**Path**: `GET /api/v1/stocks/{symbol}/signals`

| Step | Expected |
|---|---|
| Open a symbol whose accepted data triggers Trend Following | Exactly one signal for `TREND_FOLLOWING` with direction, entry zone, stop, targets, risk/reward, risk score/level, factors, evidence, as-of time (FR-001-FR-003). |
| Open a symbol triggering no strategy | Every strategy shows `NO_SIGNAL`, not an error, not a fabricated setup (FR-004). |
| Open a symbol with fewer bars than a strategy's minimum | That strategy shows `INSUFFICIENT_HISTORY`; every other evaluable strategy still shows its correct result (FR-005). |
| Open a symbol satisfying two strategies at once | Both signals appear, each correctly attributed (FR-002). |
| Read the section copy | States a deterministic scenario, never a guarantee or instruction (FR-013). |

---

## P2 acceptance — risk factor transparency

**Path**: same response, `riskFactors` on a signal.

| Step | Expected |
|---|---|
| Open a signal with all six factors available | Each factor shows its own value and score; overall score/level published (FR-006). |
| Open a signal with the market-regime factor unavailable but 4+ others available | That factor `MISSING` with a reason; overall score computed from the rest (FR-007). |
| Open a signal with only 3 of 6 factors available | Overall score/level `null`, `INSUFFICIENT_RISK_FACTORS`; the signal itself still shown (FR-007). |
| Reload the same accepted inputs | Identical factor values and overall score (FR-008). |

---

## P3 acceptance — strategy scan

**Path**: `POST /api/v1/strategies/{strategyCode}/scan`

| Step | Expected |
|---|---|
| Scan a strategy with known triggering fixtures | Exactly those stocks appear, each with its own signal summary (FR-009). |
| Scan a strategy with zero triggers in the fixture universe | Specific empty-result state, not an error (FR-010). |
| Scan with an insufficient-history candidate present | That exclusion is disclosed and distinguishable from a genuine empty result (FR-011). |

---

## Degraded and failure paths

| Scenario | Expected |
|---|---|
| Cross-source conflict on a bar a strategy depends on | That strategy withheld with `SOURCE_CONFLICT`; unaffected strategies still usable. |
| Market regime assessment stale/unavailable | `MARKET_REGIME` factor unavailable with a reason; overall score computed from remaining factors or withheld per the four-of-six floor. |
| A correction lands on a contributing bar | The affected signal is recalculated with a new as-of indication; the superseded revision stays queryable. |
| Repeated identical requests | No side effect; identical results every time. |

## Authorization checks

| Check | Expected |
|---|---|
| Unauthenticated request to either endpoint | HTTP 401. |
| POST without a CSRF token, even with a valid session | HTTP 403 — verified from the first implementation, not discovered afterward (Feature 003's T030 follow-up finding). |
| Response, log, and metric inspection | No credential, token, or raw provider payload. |

## Accessibility

| Check | Expected |
|---|---|
| Direction, risk level, and signal strength | Each has a text or icon indicator independent of colour (NFR-003). |

## Release gates that remain open

This feature opens no new gate and closes none of Features 001-003's. Its
release posture is exactly Feature 003's: Feature 002's G-01 to G-04 and
Feature 001's T051 Tailscale ingress runbook remain the governing
pre-deployment gates.
