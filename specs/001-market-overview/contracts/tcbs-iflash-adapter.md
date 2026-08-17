# Provider Contract: TCBS iFlash Market Data Adapter

**Contract version**: `tcbs-iflash-market-private-v1`  
**Feature**: `001-market-overview`  
**Status**: Design contract; implementation is blocked until the capability gate passes

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

**Current POC evidence (2026-08-17)**: After earlier sanitized failures, the
owner successfully completed the TOTP token exchange. Read-only REST probes
passed for ticker-common datasets `1`, `2`, `3`, and `5`, returning respectively
427, 30, 299, and 823 records with trading dates and the captured field schemas.
The single-security reference probe also passed and returned a paginated
`content` envelope. WebSocket authentication, heartbeat negotiation, and the
subscription acknowledgement succeeded, proving provisioned stream access;
no index update arrived during the 20-second probe run after market hours, so
the four index message schemas remain unconfirmed. The sanitized summary hash
is `bc58fdd4c2973ea1dc185cd660a598ad16dde3f58b062c75b9fe988a1553564e`.
No response body, credential, OTP, token, or market value was retained. This
closes the authentication and representative REST-access questions but does
not close the complete capability gate: an in-session stream capture plus the
remaining timing, correction, rate-limit, universe/status, and semantic
mapping checks are still required.

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
