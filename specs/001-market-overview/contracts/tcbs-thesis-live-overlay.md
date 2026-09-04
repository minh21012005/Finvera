# Provider Contract: TCBS Thesis Private Live Overlay

**Contract version**: `tcbs-thesis-private-live-v1`  
**Feature**: `001-market-overview`  
**Status**: Approved for owner-only private runtime  
**Amended**: 2026-09-04 — clarifying only, no field changes meaning and nothing
persisted changes shape, so the version is unchanged. The amendment records what
a live session proved rather than what provider documentation claimed: the
observed field inventory with units, the reference-price chain, the server
control frames and the RFC-ping prohibition. Audit: Feature
`013-tcbs-live-field-audit` (P2-01); evidence
`tools/verification/out/tcbs_live_capture_2026-09-04T1106.json`.

## Endpoints and authentication

- Token exchange: `POST https://openapi.tcbs.com.vn/gaia/v1/oauth2/openapi/token`
- Cash price board: `wss://openapi.tcbs.com.vn/ws/thesis/v1/stream/normal`
- Auth frame: `d|a|||base64(token)`
- Successful auth: `d|0|{"success":true,"error":null}`
- Heartbeat: client sends text frame `d|p|||` every two seconds. An RFC
  WebSocket ping frame is **not** an acceptable substitute and MUST NOT be sent:
  the server answers one by closing the connection with status 1002 (protocol
  error), even after a successful authentication (audit 2026-09-04, research
  R-009). This is a provider constraint, not a Finvera style preference. The
  production client uses `java.net.http.WebSocket`, which never sends unsolicited
  pings; any other client library must have its automatic ping disabled.

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

### Reference-price chain

`s|4` is **not** replayed for a symbol subscribed after the session has opened
(audit 2026-09-04: zero `s|4` frames in 90 seconds for three symbols subscribed
mid-session). The reference that makes a quote publishable is therefore taken
from the first of these that yields a positive value, in order:

1. `s|4.refPrice`, valid only for the trading date it arrived in;
2. the derived `s|6.matchPrice − s|6.change`, when `change` is present;
3. the `tickerCommons` REST snapshot.

`change` is optional and really is absent sometimes — a captured MBB `s|6` had
neither `change` nor `changePercent` — so step 2 is a fallback, not a guarantee.
For a mid-session symbol the REST snapshot is also the only source of ceiling,
floor and foreign room. A quote with no reference from any step is not published;
nothing is defaulted to zero.

### Observed field inventory (audited 2026-09-04, open session)

Units and JSON types below were captured live and anchored against an
independent provider (VCI), not read from provider documentation
(specs/013-tcbs-live-field-audit, research R-004…R-009; evidence
`tools/verification/out/tcbs_live_capture_2026-09-04T1106.json`). Both JSON
types must stay acceptable for every numeric field regardless of which one the
provider currently sends.

`s|6` — equity trade:

| Field | JSON type observed | Unit / semantics | Consumed |
|---|---|---|---|
| `symbol` | string | ticker, `[A-Z0-9]{1,10}` | yes |
| `matchPrice` | string | **base VND per share** (VNM 61900 = VCI 61.9 × 1000) | yes |
| `matchQtty` | string | shares of the latest match (multiples of 100) | no |
| `change` | number | base VND per share vs the session reference; optional | yes |
| `changePercent` | number | **percent, already scaled** (1.1437 = +1.14 %); optional | mapped, then ignored |
| `totalVolume` | string | shares traded in the session, not lots | yes |
| `totalValue` | string | base VND traded in the session | yes |

`totalValue / totalVolume` must stay a plausible session VWAP against
`matchPrice`; the ingestion guard rejects a ratio outside 0.02–50×, which is what
would catch a one-sided unit change.

`s|8` — index update:

| Field | JSON type observed | Unit / semantics | Consumed |
|---|---|---|---|
| `indexNumber` | number | 1 VN, 2 VN30, 3 HNX, 5 UPCoM — **verified against VCI levels**, not documentation | yes |
| `index` | number | index points | yes |
| `change` | number | index points vs reference | yes |
| `changePercent` | number | percent, already scaled | mapped, then **recomputed** from level/reference and discarded |
| `volume` | number (may be scientific notation, e.g. `1.6469416E7`) | shares | yes |
| `value` | number (may be scientific notation) | base VND | yes |
| `increase` / `decrease` / `notChange` | number | instrument counts; jointly present or jointly absent | yes (1+3+5 only) |
| `ceilIncrease` / `floorDecrease` | number | instrument counts; **never sent for VN30** | no |
| `session` | string (`"5"`, `"O"` observed) | opaque provider session code | carried, never interpreted |

`s|4` — equity reference: no frame was observed in the 2026-09-04 window, so its
field types rest on the original R-015 evidence and remain as specified above.
The mapper's handling is exercised by unit tests rather than by the pinned
fixture; a future capture that catches one at session open should extend the
fixture.

`tickerCommons` — REST snapshot, `GET /tartarus/v1/tickerCommons?tickers=…`,
Bearer-authenticated with the same token as the stream. Response is
`{ "tradingDate": "dd/MM/yyyy", "data": [ … ] }`; every numeric arrives as a JSON
number (note `matchQtty`, which the stream sends as a string).

| Field | Unit / semantics | Consumed |
|---|---|---|
| `symbol` | ticker | yes |
| `refPrice` | base VND per share — the official session reference | yes |
| `ceilPrice` / `floorPrice` | base VND per share; sit exactly on the venue price-limit band around `refPrice`, rounded to tick (HOSE ±7 %, HNX ±10 %, UPCoM ±15 %) | yes |
| `matchPrice` | base VND per share; equals the stream's `matchPrice` | yes |
| `open` / `high` / `low` | base VND per share | yes |
| `avg` | base VND per share (session VWAP) | no |
| `totalVol` | shares | yes |
| `totalVal` | base VND | yes |
| `room` | **shares** of remaining foreign room, not a percentage | yes |
| `change` / `changePercent` | base VND / percent already scaled | no |
| `bidPrice01…03`, `offerPrice01…03`, `bidQtty01…03`, `offerQtty01…03` | base VND / shares — order-book ladder | no |
| `buyForeignQtty` / `sellForeignQtty` | shares | no |
| `matchQtty` | shares | no |
| `indexNumber` | same numbering as `s\|8` (VNM/MBB → 1, ACV → 5) | no |

Because these values are stored with no conversion, the price-limit band identity
above is the standing check on their unit: if `ceilPrice / refPrice − 1` stops
matching the venue's band, the snapshot's unit or the reference has changed.

### Server control frames

Besides the `d|0|` authentication response, the server sends `d|33|<n>` once
immediately after the socket opens and `d|34|<n>` about every five seconds as its
own heartbeat. Neither is an authentication response. A client MUST treat every
unrecognized `d|`-prefixed frame as an ignorable control frame and MUST NOT infer
authentication, subscription or session state from one.

### Pinned fixture

`finvera-be/src/test/resources/fixtures/market/tcbs/tcbs-frame-fixture.json`
holds verbatim frames from the audited session, replayed through the production
mapper by `TcbsThesisFrameFixtureTests`. Values in it are never hand-edited: it
exists so that a provider unit, type or index-number change fails the build
instead of reaching PostgreSQL.

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
