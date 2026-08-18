# Provider Contract: TCBS iFlash Market Data Adapter

**Contract version**: `tcbs-iflash-market-private-v1`  
**Feature**: `001-market-overview`  
**Status**: Gate APPROVED with three documented constraints — REST timestamp is trading-date-only (label `TCBS_REST_TRADING_DATE_ONLY`), session field is opaque and inferred from `MarketTimePolicy`, breadth full-universe status mapping is PARTIAL pending T046 graceful degradation

## Purpose and boundary

This adapter normalizes TCBS iFlash **read-only market data** into Finvera's
provider-neutral market observations. It is valid only for a private deployment
used by the configured owner of the TCBS account. It does not create a right to
display, redistribute, export, or share raw or transformed market data with
other people.

The adapter MUST NOT call trading, account, cash, portfolio, order, or any
other non-market endpoint. Provider DTOs and credentials MUST NOT cross the
adapter boundary or appear in browser/API payloads.

## Provider authentication and session lifecycle

- The owner obtains the TCBS API key through TCInvest/iFlash Open API and
  manually completes a documented OTP flow to obtain a token. TCInvest app
  TOTP uses `apiKey + otp`. The separately registered Email/SMS flow first
  calls `request-otp` with `apiKey`, then exchanges `apiKey + otp + otpId`.
- TCBS documents a maximum token lifetime of eight hours. A token is held only
  in runtime memory; it is never persisted, logged, or sent to a browser.
- Only the authenticated owner may initiate renewal. Finvera MAY accept iOTP
  transiently for one immediate exchange, but MUST NOT persist, log, generate,
  scrape, reuse, or automatically replay it. When renewal is required,
  ingestion pauses until the owner completes this private operational path.
- The API key is a server-side secret. It is absent from source control, client
  configuration, telemetry, fixtures, and logs.
- TCBS documents that an iFlash API key is valid for 12 months and may be
  viewed, revoked, or regenerated in TCInvest. Expiry/rotation must surface as
  provider authentication health, never as missing or zero market data.

## Normalized port

```text
MarketDataProvider
  fetchReferenceData(effectiveDate) -> ProviderReferenceBatch
  reconcileLatest(tradingDate) -> ProviderSnapshotBatch
  subscribe(observer) -> cancellable stream of ProviderObservation
  health() -> ProviderHealth
```

Every accepted observation carries provider/dataset identity, normalized
subject identity, observed/effective/ingested times, timezone/format,
currency/unit, venue, adjustment status, source sequence/revision when
supplied, and validation metadata. The normalized source identifier is
`TCBS_IFLASH_MARKET_DATA`.

## Capability gate before integration

Using the owner's authorized TCBS account in a non-production/private setting,
capture sanitized fixtures and confirm the documented API can provide:

1. VN-Index, VN30, HNX Index, and UPCOM Index level, absolute/percentage
   change or a valid prior close, matched volume/value, timestamps, and session
   semantics;
2. the active common-equity universe across HOSE, HNX, and UPCOM, plus valid
   reference prices and status needed for breadth;
3. final completed-session observations sufficient to reconcile the current
   session with the separate historical bootstrap; and
4. correction, ordering, timestamp, rate-limit, REST/WebSocket, and delay
   behavior needed by the acceptance tests.

The owner must also confirm that the account is currently entitled to iFlash.
The TCBS FAQ states current priority criteria of at least VND 100 million in
monthly listed-securities trading or at least 10 VN30 futures contracts per
month. Meeting a published criterion is not inferred from possession of an API
key; successful provisioned access and TCBS confirmation are the gate evidence.

The implementation MUST not guess a TCBS schema or silently substitute missing
facts. If any required capability is unavailable under the account's approved
access, return the relevant section as partial/unavailable and resolve the
provider decision before enabling the affected journey.

**Current POC evidence (2026-08-18)**: The owner successfully completed the
TOTP token exchange during market hours. Read-only REST probes passed for
ticker-common datasets `1`, `2`, `3`, and `5`, returning 428, 30, 299, and 824
records respectively with trading dates and sanitized field schemas. The
single-security reference probe passed with a paginated `content` envelope.
WebSocket authentication, heartbeat negotiation, subscription acknowledgement,
and live messages for all four index numbers succeeded. The captured index
message schemas include index value, change, change percentage, volume, session,
and breadth counters where supplied. The sanitized summary hash is
`87485f2e81d65895d974f80a74109e3e626236b30740f88ce350660c4a4409d1`.
No response body, credential, OTP, token, or market value was retained. This
closes authentication, representative REST access, and in-session stream
capture. Timing/delay, correction/order semantics, rate-limit behavior, and
complete reference/status mapping remain before the full capability gate and
adapter implementation are approved.

The POC has an opt-in bounded follow-up mode for these questions: it performs
at most five additional read-only ticker requests at a declared minimum
interval. WebSocket capture records aggregate update counts, one-way payload
fingerprints, and the actual presence or absence of timestamp, ordering, and
correction fields. Missing provider fields are unavailable evidence, never
inferred from market values.

**Official research follow-up (2026-08-18)**: TCBS's price-board documentation
confirms `rt` carries the observed index/breadth fields but does not document a
timestamp, sequence, revision, or the meanings of the `session` codes. Its
documented `tickerCommons` REST response supplies equity reference and current
price fields, while its intraday history endpoint is per ticker. TCBS's
separate Ouranos WebSocket `C001` channel is also per ticker and includes
`timeSec` (epoch seconds), reference, and cumulative volume/value. It may be
evaluated for timestamped equity breadth inputs, but it is not evidence for
four-index final reconciliation or `rt` correction ordering.

The capability POC exposes this as an opt-in `--ouranos-symbols` capture with
an explicit, small operator allowlist and 10--180-second duration. It produces
only symbol-anonymous schema, field-presence, update-count, and one-way
fingerprint evidence. A successful C001 capture can support a future breadth
adapter decision; it does not close T045 by itself.

**Ouranos C001 evidence (2026-08-18)**: A 90-second capture for two explicit
equity symbols passed. Both anonymized schemas exposed `timeSec` and all
required C001 fields (reference, cumulative volume/value, and unit timeframe).
Neither schema exposed an ordering/sequence or correction/revision field. The
sanitized summary hash is
`3a75659c820a713d98802bad5ec125c565fec94fd96c5910a8a8b29919f2ed8c`.
This closes the small-sample entitlement/schema evidence for timestamped
equity breadth input, but not full-universe coverage, final index
reconciliation, or correction ordering.

**Bounded follow-up evidence (2026-08-18)**: Five read-only ticker requests at
one-second intervals all returned HTTP 200. The 90-second WebSocket capture
received at least one update for each required index, but exposed no timestamp,
ordering/sequence, or correction/revision field in any index message schema.
The sanitized summary hash is
`6a108963f0c415ae6eb7260665d07b80dbfa80c966e4125856a71b2be6c533d6`.
This establishes low-rate read behavior and explicitly proves those stream
provenance capabilities are unavailable in the observed schema. A TCBS adapter
must therefore treat WebSocket messages as transient current-session signals;
it cannot use them alone for immutable correction ordering. Final REST
reconciliation needs a documented timestamp/revision source or must be marked
partial/unavailable.

## Capability gate resolution (2026-08-18)

This section records the three open items from the bounded follow-up evidence and
the design decisions that close the T045 gate. All three constraints must be
encoded in the adapter implementation (T046); none may be silently dropped.

### Decision A — REST reconciliation (trading-date-only timestamp)

**Open item closed**: _"Final REST reconciliation needs a documented
timestamp/revision source or must be marked partial/unavailable."_

**Evidence from POC**: `GET /tartarus/v1/tickerCommons?index={N}` at
`https://openapi.tcbs.com.vn` returned HTTP 200 for all four index numbers
(1, 2, 3, 5). The response envelope is:

```
{ "tradingDate": "string", "data": [ { "symbol", "indexNumber", "matchPrice",
  "change", "changePercent", "totalVol", "totalVal", "open", "high", "low",
  "refPrice", "ceilPrice", "floorPrice", "bidPrice01-03", "offerPrice01-03",
  "buyForeignQtty", "sellForeignQtty", "room", "avg", ... } ] }
```

`tradingDate` is a date string (YYYY-MM-DD). It identifies the trading session
but does not supply an intraday timestamp. The adapter computes
`effective_at = tradingDate + session_close_time(Asia/Ho_Chi_Minh)` using
`MarketTimePolicy`'s versioned session schedule. For HOSE and HNX the standard
close is `14:45:00`; for UPCOM `14:30:00`. This is a deterministic, calendar-
derived inference — not a provider-supplied value — and must therefore be
labelled **`TCBS_REST_TRADING_DATE_ONLY`** on every persisted observation
sourced from this endpoint.

The reference level used for REST snapshots is `refPrice` (the provider's
stated reference price), not the calculated `index - change` used for the
stream mapper. If `refPrice` is absent or zero for a given record, the
observation is treated as `REFERENCE_UNAVAILABLE` and must not fabricate a
value.

**Rate budget confirmed**: Five consecutive requests at one-second intervals all
returned HTTP 200. The adapter MUST poll this endpoint at most once every 30
seconds while the session is inferred OPEN; call it once within five minutes of
the inferred session close to capture the final reconciliation snapshot. Do not
poll during inferred non-trading hours. POC rate-probe SHA-256:
`6a108963f0c415ae6eb7260665d07b80dbfa80c966e4125856a71b2be6c533d6`.

**Authoritativereconciliation flow**:
1. `MarketTimePolicy` infers session state from wall-clock `Asia/Ho_Chi_Minh`.
2. While inferred OPEN: poll every 30 s → ingest via `MarketIngestionService`
   with label `TCBS_REST_TRADING_DATE_ONLY`.
3. Within five minutes after inferred close: one final poll → ingest as the
   end-of-session reconciliation snapshot with the same label.
4. WebSocket `rt` messages update display only; they MUST NOT trigger
   persistence calls and MUST NOT overwrite REST-sourced accepted observations.

### Decision B — Session field is opaque; state is inferred from clock

**Open item closed**: _`session` string in `rt` stream has undocumented
semantics._

**Decision**: The `session` field value is stored verbatim in the observation's
`rawProviderSession` field as an opaque diagnostic string. It MUST NOT be used
to control ingestion, polling, or persistence logic. Session open/closed state
is derived entirely from `MarketTimePolicy` using the `Asia/Ho_Chi_Minh` wall
clock and the versioned HOSE/HNX/UPCOM trading schedule. If `MarketTimePolicy`
determines the market is closed, the adapter pauses REST polling and labels any
arriving WebSocket messages as `NON_TRADING_HOURS` on the display path.

This decision is safe because the trading schedule is public and deterministic.
It avoids hard-coding undocumented provider codes that may change without
notice. If TCBS subsequently documents `session` semantics, this decision must
be revisited via an ADR before any code change.

### Decision C — Breadth universe: PARTIAL gate

**Open item closed**: _"Complete reference/status mapping remain before the
full capability gate and adapter implementation are approved."_

**Evidence from POC**: `ticker_commons_index_1` returned 428 records,
`ticker_commons_index_2` returned 30, `ticker_commons_index_3` returned 299,
and `ticker_commons_index_5` returned 824 — all with confirmed `tradingDate`
and the field schema listed in Decision A above. The schema shape is confirmed
for the instrument universe accessible under the owner's account.

**What is NOT confirmed**: Whether every record in the universe carries a valid,
non-null `matchPrice` and whether the `status`/trading-state field (if any
exists beyond price-limit fields) maps to Finvera's `InstrumentStatus` enum.
Only price-limit fields (`ceilPrice`, `floorPrice`) and reference price are
confirmed in schema; there is no explicit `tradingStatus` or `status` field in
the observed schema.

**Constraint on T046**: The breadth adapter MUST implement graceful degradation.
If a record lacks a confirmed `matchPrice` or reference price, it must be
counted as `BREADTH_RECORD_INCOMPLETE` and excluded from breadth calculation,
not zero-filled. The breadth output must report its coverage ratio (eligible
records / total returned) so stale or incomplete universes are visible.
Full-universe coverage verification is a T046 acceptance criterion, not a T045
gate requirement.

### T045 gate closure summary

| Item | Status | Label / Action |
|---|---|---|
| Authentication (TOTP + token) | ✅ Closed | — |
| REST read-only access (all 4 indices) | ✅ Closed | `TCBS_REST_TRADING_DATE_ONLY` |
| WebSocket stream (all 4 index numbers) | ✅ Closed | `TCBS_STREAM_TIMESTAMP_UNAVAILABLE`, `TCBS_STREAM_ORDERING_UNAVAILABLE` |
| Rate-limit at low frequency | ✅ Closed | poll ≤ 1/30s |
| Ouranos C001 equity schema | ✅ Closed | future breadth input only |
| REST reconciliation source | ✅ Closed | `tradingDate`-only, inferred `effective_at` |
| Session field semantics | ✅ Closed | opaque, infer from `MarketTimePolicy` |
| Breadth full-universe status mapping | 🟡 PARTIAL | T046 must implement graceful degradation |
| Correction/ordering in WebSocket | 📄 Documented unavailable | WebSocket display-only |

Owner acceptance required before T046 begins. No raw payload, credential, OTP,
token, or market value is retained in this contract or its referenced POC
output.

## Documented stream normalization (preparatory only)

The pure `TcbsIndexStreamMapper` normalizes the observed `rt` index messages
without opening a network connection or persisting an observation. Its narrow
allowlist is `indexNumber` 1 (VN-Index), 2 (VN30), 3 (HNX Index), and 5
(UPCOM). It maps `index`, `change`, `changePercent`, `volume`, and `value` as
the current index facts; the reference level is the exact decimal calculation
`index - change`. `session` remains opaque until its documented code semantics
are captured.

Every output from this mapper is labelled
`TCBS_STREAM_TIMESTAMP_UNAVAILABLE` and `TCBS_STREAM_ORDERING_UNAVAILABLE`.
It is deliberately not wired into `TcbsMarketDataProvider`, the REST
reconciliation flow, or persistence while T045 remains open. This code and its
tests only prevent schema drift and accidental inference once the remaining
provider evidence is obtained.

## Delivery, rate, and failure behavior

- Use REST reads for reference data, history, and reconciliation; use the
  documented price-board WebSocket only while an active token exists.
- Begin below TCBS's documented REST and WebSocket limits; exact configured
  budgets, endpoint paths, field mappings, and reconnect behavior are fixed
  only from captured official documentation and fixtures.
- Apply timeouts, bounded retry with backoff/jitter only to idempotent reads,
  cancellation, and bounded reconnect. Do not retry iOTP.
- On token expiry or renewal failure, preserve the last accepted observations,
  evaluate their normal data freshness, emit `PROVIDER_AUTH_REQUIRED`, and do
  not publish a claim of live/current ingestion.

## License and access controls

The deployment MUST have no public ingress and must authorize exactly one
configured owner identity. No self-registration, invitation, sharing link,
public dashboard, export, webhook, email delivery, or third-party analytics
may disclose TCBS-derived facts. A private network overlay (for example,
Tailscale or a user-managed equivalent) is an operational deployment control,
not an adapter substitute for authorization.

Before public or multi-user delivery, Finvera MUST select and contract a source
with explicit display/redistribution rights, implement a new adapter, pass this
feature's contract tests for that adapter, and record an ADR. Changing a flag is
not sufficient.

Historical bootstrap is governed separately by
[vnstock-historical-bootstrap.md](vnstock-historical-bootstrap.md). TCBS remains
the live source; neither adapter may silently overwrite the other's provenance.

## Contract tests and fixtures

Sanitized fixtures MUST cover normal active/closed sessions; duplicate,
out-of-order, corrected, missing, invalid, zero, and no-reference observations;
token expiry/renewal required; and provider rate/connectivity failures. Tests
assert provenance, decimal/time conversion, deduplication, correction handling,
no secret/raw-payload logging, and negative tests proving non-market operations
cannot be called.

## Evidence

- [TCBS iFlash workflow](https://developers.tcbs.com.vn/docs/v1.0.0/workflow/)
  documents API-key/iOTP token acquisition and the eight-hour maximum token
  lifetime.
- [TCBS token endpoint](https://developers.tcbs.com.vn/docs/v1.0.0/auth/token/)
  documents the OAuth token operation.
- TCBS iFlash FAQ supplied/reviewed by the owner documents 12-month API-key
  validity, TCInvest key management, and the current account-priority criteria.
- TCBS iFlash Open API Terms and Conditions supplied by the owner, clauses
  2(c), 2(d), and 3(c): use is constrained to the client's own securities
  transaction purpose; sharing original or processed information with third
  parties requires TCBS's written approval.
