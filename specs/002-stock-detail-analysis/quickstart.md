# Quickstart and Acceptance: Stock Detail and Analysis

**Feature**: `002-stock-detail-analysis`
**Status**: Implemented and Verified (Fixture Baseline). Gates G-01 to G-04 and T051 remain open release blockers.

Every command below runs against `127.0.0.1` only. The T051 ingress deferral is
still in force: do not create a port forward, public DNS record, tunnel, or
Tailscale Funnel while running any of this.

## Prerequisites

1. Feature 001 is running locally and its quickstart passes. This feature reuses
   its owner session, its instrument reference data, and its accepted
   observations.
2. PostgreSQL 17 is reachable through the Feature 001 connection settings, and
   Flyway has applied migrations through `V003`.
3. Fixture mode is active. No gate in research R-012 is closed, so every provider
   adapter resolves to a fixture double.
4. At least one fixture symbol with 250 or more accepted completed daily bars is
   loaded, so the recursive indicators are exercisable.

## Configuration

Feature 002 adds these deployment-only settings. Defaults keep every live path
off, matching the Feature 001 convention that a live provider is opt-in.

| Key | Default | Meaning |
|---|---|---|
| `FINVERA_STOCK_QUOTE_LIVE_ENABLED` | `false` | Gate G-03. Also requires `FINVERA_MARKET_PROVIDER_MODE=live` (Feature 001) — reuses that TCBS session. |
| `FINVERA_STOCK_SECTOR_BASIS_ENABLED` | `false` | Gate G-04. |
| `FINVERA_STOCK_QUOTE_CONTRACTED_DELAY` | none | Required when quotes are live; no hard-coded fallback. |
| `FINVERA_STOCK_CHART_MAX_WINDOW` | `2Y` | Upper bound on the chart lookback. |

Fundamentals (G-01) and corporate actions (G-02) have no equivalent flag:
fundamentals go live purely by importing data (`FINVERA_STOCK_IMPORT_
FUNDAMENTALS_*`, see `research.md` T058), and corporate actions has no live
adapter at all — the owner declined to build one (gate G-02, closed
RAW-only-permanently) — so there is nothing for a flag to gate. An earlier
`FINVERA_STOCK_PROVIDER_MODE`/`FINVERA_STOCK_FUNDAMENTALS_ENABLED`/
`FINVERA_STOCK_CORPORATE_ACTIONS_ENABLED` were removed as dead configuration
once this became clear (2026-08-22) — nothing in code ever read them.

No secret is added by this feature. It reuses the Feature 001 provider
credentials and introduces no new external host.

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
.\mvnw.cmd '-Dtest=StockDetailPerformanceTests,StockDetailFailureTests,StockReplayDeterminismTests,StockDetailSecurityTests' test
```

## Local runtime

```powershell
# terminal 1, from finvera-be/
.\mvnw.cmd spring-boot:run

# terminal 2, from finvera-fe/
npm run dev
```

Sign in as the configured owner through the Feature 001 login, then open the
stock detail route for a fixture symbol.

---

## P1 acceptance — current price and recent history

**Path**: `GET /api/v1/stocks/{symbol}` and `GET /api/v1/stocks/{symbol}/chart`

| Step | Expected |
|---|---|
| Open a complete fixture symbol | Symbol, company name, current price, absolute change, percentage change, market cap, sector, volume, session status, trading date, as-of time, and freshness state are all present (FR-001). |
| Inspect the chart | Daily OHLCV renders ascending by date, with the series `adjustmentStatus` visible (FR-003, DATA-008). |
| Load the market-closed fixture | The latest accepted session stays visible, and the page identifies the market as closed with the snapshot's trading date (FR-013). |
| Load the delayed and stale fixtures | The freshness label changes and the value is never described as live (FR-004, DATA-005). |
| Request an unknown symbol | HTTP 404 with a specific unsupported state; no placeholder stock is rendered (FR-016). |
| Disable the chart fixture only | Overview stays fully usable; the chart shows its own unavailable state (FR-012). |
| Remove the reference-price fixture | Change fields are unavailable with a reason, not inferred and not zero (FR-002, DATA-007). |

**Timing**: record the p95 of 20 consecutive overview loads. The target is a
usable overview within 3 seconds (NFR-001, SC-005).

**Usability**: in three consecutive timed trials, the owner identifies the
stock's price, direction, and session status within 10 seconds (SC-001).

**Fact accuracy**: compare every displayed overview and chart value against the
accepted fixture source at the declared display precision. All must match, and no
missing value may render as zero (SC-002).

---

## P2 acceptance — technical condition

**Path**: `GET /api/v1/stocks/{symbol}/technical`

| Step | Expected |
|---|---|
| Open a symbol with 250 or more bars | MA20, MA50, MA200, RSI14, MACD, BBANDS, ATR14, AVG_VOLUME20, and RELATIVE_VOLUME each show a value, window, rule version, and as-of time (FR-005). |
| Open the 199-bar fixture | MA200 is unavailable with `INSUFFICIENT_HISTORY` carrying `requiredBars` and `availableBars`; every other indicator remains usable (FR-006). |
| Open the 249-bar fixture | RSI14, MACD, and ATR14 are unavailable; the non-recursive indicators still publish (research R-003). |
| Reload the same fixture | Every value is identical to the previous load (FR-011, SC-003). |
| Open the split-in-window fixture | The series and every indicator report the same `adjustmentStatus`; no spliced series is produced (DATA-008, research R-004). |
| Read the section copy | It presents calculated values, not trading instructions (FR-015). |

**Determinism evidence**: run the replay test and confirm each persisted result
recomputes to the exact stored decimal from its `input_set_hash`.

---

## P3 acceptance — fundamentals and valuation

**Path**: `GET /api/v1/stocks/{symbol}/fundamentals` and
`GET /api/v1/stocks/{symbol}/valuation`

| Step | Expected |
|---|---|
| Open the complete fundamentals fixture | Revenue, revenue growth, gross profit, operating profit, net profit, EPS, ROE, ROA, debt-to-equity, operating margin, free cash flow, and dividend appear with the reporting period identity (FR-007). |
| Open the valuation section | P/E, P/B, EV/EBITDA, PEG, dividend yield, exactly one classification, the score, the confidence, and the disclosed comparison basis all appear (FR-008, FR-009). |
| Open the negative-earnings fixture | P/E and PEG are `NOT_APPLICABLE`, never negative and never zero; P/B still contributes. |
| Open the 7-constituent sector fixture | Only the own-history basis is used, `SECTOR_BASIS_INSUFFICIENT` is reported, and the disclosure names the basis actually used. |
| Open the stale-fundamentals fixture | The classification is withheld with `FUNDAMENTALS_STALE`; score and confidence are also null (FR-010). |
| Open the band-boundary fixtures | Unrounded scores 35.4, 35.5, 64.4, and 64.5 map to UNDER, FAIR, FAIR, and OVER, and the displayed integer never contradicts the label. |
| Read the section copy | It describes a quantitative assessment, not a prediction or an instruction (FR-015). |

**Fact accuracy**: every fundamental and valuation value matches the accepted
fixture source at its declared precision, with no missing value shown as zero
(SC-002).

---

## Degraded and failure paths

Across every row below, the view must show the correct data-quality state and
must never fabricate a fact, an indicator value, or a valuation classification
(SC-004).

| Scenario | Expected |
|---|---|
| Restated reporting period | The corrected figures appear with a new as-of indication, and the superseded revision remains queryable (FR-014). |
| Duplicate or out-of-order snapshot | The view never regresses to older accepted facts (DATA-006). |
| Cross-source conflict on a consumed bar | A reconciliation audit row exists and every dependent indicator and valuation result is withheld with `SOURCE_CONFLICT` (DATA-010). |
| Provider timeout | Last accepted facts stay visible with their true freshness; nothing is zeroed. |
| Provider auth expiry | Affected section reports `PROVIDER_AUTH_REQUIRED`; accepted history stays readable and nothing claims to be live (NFR-007). |
| Fundamentals unavailable | Overview, chart, and technical sections stay fully usable (FR-012). |
| All AI capabilities disabled | Every P1 to P3 journey works, and no AI-related error appears (NFR-004, SC-006). |
| Suspended or newly listed symbol | Available sections publish; unavailable ones state their reason. |

## Authorization and secret checks

| Check | Expected |
|---|---|
| Unauthenticated request to any stock endpoint | HTTP 401, no data body. |
| Authenticated non-owner identity | HTTP 403 (SEC-001). |
| State-changing request without a CSRF token | HTTP 403 and no state change (SEC-004). |
| Response, log, and metric inspection | No credential, token, iOTP value, or raw provider payload appears (SEC-002, SC-008). |
| Frontend production build scan | No provider credential in `finvera-fe/dist`, using the scan from the Feature 001 runbook. |
| Adapter negative test | No trading, account, cash, or order operation is reachable (SEC-003). |

## Accessibility

| Check | Expected |
|---|---|
| Direction, freshness, trend, and valuation states | Each has a text or icon indicator independent of colour (NFR-005, SC-007). |
| Keyboard-only navigation | Every section and the symbol lookup are reachable and operable. |
| Screen-reader labels | Exact values, units, and freshness meaning are preserved, not paraphrased away. |

## Operational evidence to record

For each acceptance run, record only: date, fixture set identity, pass or fail
per table row, measured p95, and the rule versions in effect. Do not record
cookies, tokens, provider payloads, or response bodies.

## Release gates that remain open

This quickstart passing on fixtures does **not** authorize deployment.

1. Gates G-01 to G-04 in research R-012 must close with owner-accepted evidence
   before any live adapter is enabled.
2. T051, the Tailscale Serve-only ingress runbook, remains a mandatory
   pre-deployment gate for this feature as it is for Feature 001.
3. Public or multi-user delivery still requires a commercially licensed
   market-data provider; neither TCBS nor Vnstock grants that right here.
