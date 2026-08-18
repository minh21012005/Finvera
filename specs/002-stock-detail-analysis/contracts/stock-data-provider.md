# Provider Contract: Stock Data Ports

**Feature**: `002-stock-detail-analysis`
**Contract version**: `stock-data-private-v1`
**Owner**: `finvera-be` / `stock` module, provider layer
**Status**: Ports defined; gates G-01 to G-04 OPEN. No live integration is
authorized by this document.

## Purpose and boundary

This contract defines the **outbound** ports through which the `stock` module
obtains external facts, and the acceptance checks every inbound record must pass
before it becomes an accepted observation. It deliberately says nothing about any
provider's wire format: a port exists so that a provider can be replaced without
touching domain, persistence, or API code.

Boundary rules, inherited and non-negotiable:

- Only `finvera-be` calls an external market-data provider. The browser never
  does, and `finvera-ai` never does (Constitution Principle III).
- Provider credentials and tokens live in server-side configuration or the
  secret store only. They never appear in a response, a log line, a client
  bundle, a fixture, or a database column (SEC-002).
- Ports are **read-only**. No port exposes an order, trading, account, cash, or
  portfolio operation, and the adapter allowlist must make invoking one
  impossible rather than merely discouraged (SEC-003).
- Outbound calls are permitted only to the TLS hosts already on the Feature 001
  allowlist. This feature adds no new external host.

## Ports

### `StockReferenceProvider`

```text
findInstrument(symbol)      -> InstrumentReference
listSectorClassification()  -> SectorClassification[]
```

`InstrumentReference` carries symbol, venue, company name, listing status,
shares outstanding, sector code, classification scheme and version, and a source
revision. Shares outstanding and sector are nullable with a reason; neither is
ever defaulted.

### `StockQuoteProvider`

```text
getQuote(symbol)  -> QuoteObservation
```

`QuoteObservation` carries last or matched price, official reference price,
session volume, session value, observed-at instant, and the provider's session
indication. This extends the Feature 001 TCBS adapter from index subjects to
instrument subjects (research R-001); it is a contract amendment to
[001 contracts/tcbs-iflash-adapter.md](../../001-market-overview/contracts/tcbs-iflash-adapter.md),
not a new provider relationship.

### `StockHistoryProvider`

```text
getDailyBars(symbol, fromDate, toDate)  -> DailyBar[]
```

`DailyBar` carries trading date, open, high, low, close, volume, value, and the
provider's adjustment indication. Bars arrive either live or inside an offline
canonical package under
[001 contracts/vnstock-historical-bootstrap.md](../../001-market-overview/contracts/vnstock-historical-bootstrap.md);
both paths pass the same acceptance checks.

### `CorporateActionProvider`

```text
getCorporateActions(symbol, fromDate, toDate)  -> CorporateAction[]
```

`CorporateAction` carries type, ex-date, record and payment dates, ratio or cash
amount, and source revision. The adapter computes no adjustment factor; the
domain derives it, so the factor is reproducible from accepted facts rather than
trusted from a provider (research R-004).

### `FundamentalReportProvider`

```text
getReports(symbol, periodType, fromPeriod, toPeriod)  -> FundamentalReport[]
```

`FundamentalReport` carries the period identity (type, fiscal year, quarter,
start and end dates), report kind, audit status, currency, the source's unit
scale, and a list of `(sourceLineItem, value)` pairs **before** mapping to
Finvera metric codes. Mapping happens in the adapter against the versioned
`fundamental_metric_catalog`, and an unmapped line item is dropped with a counted
metric, never guessed into the nearest-looking code.

This port has **no accepted implementation**. Gate G-01 in
[research.md](../research.md) governs it.

## Acceptance checks

Every inbound record passes all of these before it is accepted. A failure
produces a rejected `ingestion_record` with a stable reason code and never a
partial write.

| # | Check | Reject reason |
|---|---|---|
| A-1 | Response size and shape are within configured bounds | `PAYLOAD_REJECTED` |
| A-2 | Symbol resolves to a known active `market_instrument` | `UNKNOWN_INSTRUMENT` |
| A-3 | Trading date is a known trading day for the venue | `NON_TRADING_DAY` |
| A-4 | Timestamps parse and are not implausibly future-dated | `INVALID_TIMESTAMP` |
| A-5 | Prices and volumes are non-negative and within configured bounds | `VALUE_OUT_OF_BOUNDS` |
| A-6 | Bar satisfies `low <= open,close <= high` | `INVALID_OHLC` |
| A-7 | Currency is VND and unit scale is declared | `UNIT_UNDECLARED` |
| A-8 | Period identity is complete and internally consistent | `INVALID_PERIOD` |
| A-9 | Record is not a duplicate of an accepted one | `DUPLICATE` |
| A-10 | Record is not older than the accepted revision for the same key, unless flagged as a correction | `OUT_OF_ORDER` |
| A-11 | No credential-shaped field is present anywhere in the mapped record | `PAYLOAD_REJECTED` |

A-6 exists because a swapped high and low is the most common provider mapping
defect and it produces an ATR that looks plausible while being wrong. Catching it
at the boundary is far cheaper than explaining it later.

A-11 is a defence in depth: if a provider ever echoes a token into a data field,
that record must not reach the database, where it would then be readable through
the API.

## Corrections and cross-source reconciliation

A record that matches an accepted key but carries different values is a
**correction** only when the source marks it as one or when its source sequence
is strictly newer. It creates a new revision linked by `supersedes_id`, triggers
recalculation of every dependent indicator and valuation result, and never
overwrites (FR-014).

When TCBS and Vnstock both supply a completed-session bar for the same instrument
and trading date, both provenances are retained. A material difference beyond the
configured tolerance writes a `source_reconciliation_audit` row with decision
`SOURCE_CONFLICT` and withholds every derived result that consumed the disputed
bar (DATA-010). The adapter never silently picks a winner.

## Delivery, rate, and failure behaviour

- Every outbound call has a bounded connect and read timeout, and a bounded
  retry with backoff and jitter. An exhausted retry is a normal degraded state,
  not an exception surfaced to the owner.
- Per-symbol request cost must be compatible with NFR-001. A stock detail view
  must not fan out into an unbounded number of provider calls; batch or cache
  reference data.
- Provider unavailability preserves the last accepted facts with their true
  freshness state. It never produces a zero, a placeholder, or a fabricated bar.
- Expired provider authentication yields `PROVIDER_AUTH_REQUIRED` on the affected
  section while accepted history stays fully readable (NFR-007).

## Secrets and logging

Permitted in logs: source label, dataset, subject symbol, trading date or period
label, reason code, correlation id, timing, and counts.

Forbidden in logs, metrics, traces, API responses, fixtures, and database
columns: API keys, iOTP values, access tokens, session cookies, full provider
request or response payloads, and any provider response header.

Fixtures are sanitized before they enter the repository. A fixture that ever
contained a credential is regenerated, not edited.

## Gate status

| Gate | Blocks | Status |
|---|---|---|
| G-01 fundamental report source | `FundamentalReportProvider`, US3 | OPEN |
| G-02 corporate action basis | `CorporateActionProvider`, adjusted series | OPEN |
| G-03 per-stock quote coverage | `StockQuoteProvider` live path | OPEN |
| G-04 sector reference coverage | Sector comparison basis | OPEN |

Until a gate closes, its port is implemented **against fixtures only**. Fixture
data is never presented to the owner as live provider data, and the deployment
flag that enables a live adapter stays off. Gate evidence and the owner's
acceptance are recorded in [research.md](../research.md) R-012.

## Contract tests

| Test | Assertion |
|---|---|
| Port isolation | Domain and application code compile with every adapter replaced by a fixture double. |
| Allowlist | An attempt to invoke a non-approved provider operation fails to compile or is rejected at the adapter boundary. |
| Acceptance matrix | Each check A-1 to A-11 has a rejecting fixture and asserts its exact reason code. |
| Correction | A newer corrected record creates a revision and recalculates dependents; the superseded row remains readable. |
| Conflict | Conflicting TCBS and Vnstock bars produce an audit row and withhold dependent results. |
| Secret leakage | A fixture with an injected token-shaped field is rejected, and no log line, response, or column contains it. |
| Degradation | Timeout, 5xx, and auth-expiry fixtures each produce their documented state with last accepted facts intact. |
