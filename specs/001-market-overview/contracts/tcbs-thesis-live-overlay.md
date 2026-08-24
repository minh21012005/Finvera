# Provider Contract: TCBS Thesis Private Live Overlay

**Contract version**: `tcbs-thesis-private-live-v1`  
**Feature**: `001-market-overview`  
**Status**: Approved for owner-only private runtime

## Endpoints and authentication

- Token exchange: `POST https://openapi.tcbs.com.vn/gaia/v1/oauth2/openapi/token`
- Cash price board: `wss://openapi.tcbs.com.vn/ws/thesis/v1/stream/normal`
- Auth frame: `d|a|||base64(token)`
- Successful auth: `d|0|{"success":true,"error":null}`
- Heartbeat: client sends text frame `d|p|||` every two seconds; RFC WebSocket
  ping is not used as a substitute.

The client waits for successful authentication before subscribing. Tokens are
memory-only and expire no later than eight hours after exchange.

## Allowlisted subscriptions

Index/breadth:

```text
d|s|si|rt|1,2,3,5
```

Index mapping is fixed: `1 -> VN_INDEX`, `2 -> VN30`, `3 -> HNX_INDEX`, and
`5 -> UPCOM_INDEX`.

On-demand equity symbol:

```text
d|s|tk|bp+tm|TCB
d|u|tk|bp+tm|TCB
```

Only normalized, validated active symbols from Finvera reference data may enter
the subscription set. Duplicate requests MUST NOT resend a subscription. The
set is bounded by `max-dynamic-symbols`; at capacity the client partially
unsubscribes the least recently used symbol before adding the new symbol. The
retained set is replayed after every successful reconnect authentication.
Whole-exchange equity subscription and trading, account, order, cash, and
portfolio channels are prohibited.

## Accepted inbound frames

`s|8|JSON` is an index update. Required fields are `indexNumber`, `index`, and
`change`. Optional fields are `changePercent`, `volume`, `value`, `increase`,
`decrease`, `notChange`, `ceilIncrease`, `floorDecrease`, and opaque `session`.
The reference level is deterministically `index - change`; both values use
`BigDecimal`. VN30 breadth is never added to consolidated breadth because its
members overlap HOSE. Consolidated breadth is the sum of index numbers 1, 3,
and 5 only.

`s|4|JSON` supplies an equity's official `refPrice`. `s|6|JSON` supplies
`symbol`, `matchPrice`, optional `matchQtty`, `change`, `changePercent`,
`totalVolume`, and `totalValue`. A quote becomes publishable only after both a
positive reference price and a non-negative matched price are known.

Provider values may be JSON strings or numbers. Missing/invalid values remain
unavailable and are never converted to zero.

## Time, ordering, persistence, and freshness

The official `s|8`/`s|6` schemas do not include a timestamp, sequence, or
revision. Finvera therefore:

- records the backend receive instant as `observedAt` and stores UTC;
- derives the trading date/session from the versioned Vietnam market clock;
- labels observations `TCBS_STREAM_RECEIVE_TIME` and
  `TCBS_STREAM_ORDERING_UNAVAILABLE`;
- never uses these messages to rewrite a completed historical Vnstock bar;
- coalesces writes into bounded intervals and applies the existing immutable
  ingestion/deduplication policy.

No new message outside an active session is expected. The last accepted facts
remain queryable after close and freshness is evaluated normally.

## Deprecated-source exclusion

`TCBS_IFLASH_MARKET_DATA` identifies the retired REST/Ouranos adapter, not this
Thesis contract. Its legacy index snapshots are invalid because equity rows were
previously misclassified as indices. They MUST be excluded from current/historical
index selection and removed by a source-scoped migration. Corresponding ingestion
records remain as rejected audit evidence with no raw provider payload. The
repair MUST NOT match or modify `TCBS_IFLASH_THESIS`, Vnstock/KBS, or fixture
sources.

## Resilience and secret handling

- Connect timeout: 10 seconds; authentication timeout: 10 seconds.
- Heartbeat interval: 2 seconds.
- Reconnect delay: exponential, capped at 30 seconds, with no tight loop.
- Authentication failure changes health to `AUTH_REQUIRED` and stops reconnect
  until renewal.
- Connectivity/parsing failure changes health to `DEGRADED` without deleting
  accepted PostgreSQL facts.
- Logs contain bounded reason codes and counts only; never API key, OTP, token,
  auth frame, or raw JSON payload.
