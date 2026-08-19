# Quickstart and Acceptance: Deterministic Stock Screener

**Feature**: `003-stock-screener`
**Status**: Implemented and verified (fixture baseline). See
`validation/fixture-acceptance.md` for the recorded 2026-08-19 evidence
(commands run, pass/fail, and the scenario-to-test traceability table). No
provider gate is opened by this feature; Feature 002's G-01 to G-04 and
Feature 001's T051 ingress deferral remain unchanged and still govern
release readiness.

Every command below runs against `127.0.0.1` only, per the same Tailscale
Serve-only ingress runbook Features 001/002 already require.

## Prerequisites

1. Features 001 and 002 are running locally and their quickstarts pass. This
   feature adds no new schema, provider, or authentication path — it reads
   the tables Feature 002 already populates.
2. Fixture mode is active (`finvera.stock.provider-mode=fixture`, inherited
   from Feature 002; unchanged by this feature).
3. At least one fixture universe exists with several instruments carrying
   known, hand-verified `equity_profile`, `equity_daily_bar`,
   `technical_indicator_result`, `fundamental_summary`, and
   `valuation_assessment` values, so a screen's expected match set can be
   asserted exactly (research R-012).
4. At least one fixture instrument has 21 or more accepted daily bars (the
   Breakout minimum, `screener-v1.md`) and at least one has exactly 20, to
   exercise the insufficiency boundary.

## Configuration

This feature adds **no new configuration key**. It reuses
`finvera.stock.provider-mode` and every existing Feature 002 flag unchanged;
screening never calls a live provider regardless of those flags (FR-007).

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

Feature-scoped backend suites:

```powershell
cd D:\Finvera\finvera-be
.\mvnw.cmd '-Dtest=ScreenerV1Tests,FundamentalSummaryTests,ScreenerServiceTests,ScreenerControllerTests,ScreenerSecurityTests,ScreenerPerformanceTests,ScreenerFailureTests,ScreenerReplayDeterminismTests' test
```

## Local runtime

```powershell
# terminal 1, from finvera-be/
.\mvnw.cmd spring-boot:run

# terminal 2, from finvera-fe/
npm run dev
```

Sign in as the configured owner through the existing login, then open the
screener route and submit a screen.

---

## P1 acceptance — Market and Price screening

**Path**: `POST /api/v1/screener/executions` with only `market`/`price`
filters populated.

| Step | Expected |
|---|---|
| Submit an exchange + market-cap-range + price-range filter against the fixture universe | Exactly the instruments satisfying all three appear, each with `matchedValues` showing the qualifying exchange, market cap, and price (FR-002, FR-003, FR-008). |
| Submit a filter combination no fixture instrument satisfies | `totalMatchCount = 0`, `matches = []`, HTTP 200 — not an error (FR-013). |
| Submit a price filter against an instrument whose latest bar is stale/unavailable | That instrument is absent from matches; its exclusion is reflected in the `PRICE` `CategoryDisclosure`'s `excludedCount` (S-4, DATA-003). |
| Select a matched row | Navigates to that stock's existing `002-stock-detail-analysis` detail page (FR-012). |
| Submit `priceMin > priceMax` | HTTP 400, `reasonCode = INVALID_FILTER_RANGE`, no query executed (research R-010). |
| Submit an empty `ScreenRequest` | The full `LISTED` candidate universe is returned, paginated (research R-007, R-009). |

**Timing**: record the p95 of 20 consecutive screen executions over the
representative fixture universe. Target: 95% within 5 seconds (NFR-001,
SC-002).

**Reconciliation**: independently apply the same filters directly against
the fixture data (e.g. a throwaway SQL query or spreadsheet check) and
confirm 0 duplicated or silently dropped securities versus the API result
(SC-001, MVP-SC-05).

---

## P2 acceptance — add Technical filters

**Path**: same endpoint, `technical` filters added.

| Step | Expected |
|---|---|
| Add an RSI range and `maRelationship: PRICE_ABOVE_MA50` to a Market screen | Exactly the intersection matches, each showing the qualifying RSI value and confirming the MA relationship (FR-004). |
| Filter on `trend` against an instrument with fewer than 200 accepted bars | That instrument is excluded with `INSUFFICIENT_HISTORY`; other qualifying instruments are unaffected. |
| Filter on `breakout` against the 20-bar and 21-bar fixtures | 20 bars → excluded (`INSUFFICIENT_HISTORY`); 21 bars → evaluated as `BREAKOUT_UP`/`BREAKOUT_DOWN`/absent per `screener-v1.md`. |
| Filter on `macdSignal: NEUTRAL` against a fixture with `HISTOGRAM = 0` | That instrument matches; a nonzero histogram of either sign does not. |
| Re-run the identical screen against unchanged fixtures | Identical `matches`, identical `matchedValues`, identical `coherenceKey` (FR-010, research R-008). |

---

## P3 acceptance — add Fundamental filters

**Path**: same endpoint, `fundamental` filters added.

| Step | Expected |
|---|---|
| Add a P/E range and an ROE minimum to a Market/Price/Technical screen | Exactly the intersection across all four categories matches, each showing the qualifying values and reporting-period/as-of identity (FR-005, SC-001). |
| Filter on `revenueGrowthPercentMin` | Uses the new `REVENUE_GROWTH_PERCENT` summary metric (research R-005); confirm the fixture's independently-computed expected growth value matches exactly. |
| Filter on `earningsGrowthPercentMin` | Uses the existing `EPS_GROWTH_PERCENT` metric unchanged. |
| Filter on `peMax` against an instrument whose `valuation_assessment` is withheld | That instrument is excluded from the match set, never treated as a pass or a fail (S-4). |
| Filter on `peMax` against an instrument with negative earnings (`PE` = `NOT_APPLICABLE`) | Excluded with a `NOT_APPLICABLE`-derived reason, distinct from a missing-data exclusion. |

---

## Degraded and failure paths

| Scenario | Expected |
|---|---|
| A selected Fundamental filter's upstream table has zero accepted rows across the whole candidate universe | The `FUNDAMENTAL` `CategoryDisclosure` reports `UNAVAILABLE` with a reason; the response is still HTTP 200, not an error (NFR-002, research R-011). |
| A gated Feature 002 category (e.g. G-01 fundamentals still open in a real deployment) | Same as above — the screener does not distinguish "gated" from "genuinely no accepted data yet" at the API level; both surface as an honest unavailable category. |
| Cross-source conflict on a consumed daily bar or fundamental report | The affected instrument is excluded from every filter that reads the conflicted table, consistent with Feature 002's existing `SOURCE_CONFLICT` withholding. |
| Screen executes mid-recalculation (a correction just landed) | The response reads one coherent set of current-revision rows; it never mixes an old and a new revision for the same instrument. |
| Repeated identical screens | No side effect; identical results every time (idempotency). |

## Authorization checks

| Check | Expected |
|---|---|
| Unauthenticated request to `/screener/executions` | HTTP 401, no data body. |
| Authenticated non-owner identity | HTTP 403 (SEC-001). |
| Response, log, and metric inspection | No credential, token, or raw provider payload appears (SEC-002). |

## Accessibility

| Check | Expected |
|---|---|
| Match, exclusion, and freshness status | Each has a text or icon indicator independent of colour (NFR-003). |
| Keyboard-only navigation | The filter form and result table are reachable and operable. |

## Operational evidence to record

For each acceptance run, record: date, fixture set identity, pass/fail per
table row, measured p95, and `ruleVersion` (`screener-v1`) in effect. Do not
record cookies, tokens, or response bodies.

## Release gates that remain open

This feature opens no new gate and closes none of Feature 002's. Its release
posture is exactly Feature 002's:

1. Gates G-01 to G-04 (Feature 002 `research.md` R-012) still govern whether
   real Fundamental/Technical/Quote data exists to screen against in a live
   deployment; this feature's own behavior under a still-open gate is
   defined in "Degraded and failure paths" above.
2. Feature 001's T051 Tailscale Serve-only ingress runbook remains a
   mandatory pre-deployment gate.
3. Public or multi-user delivery still requires a commercially licensed
   market-data provider.
