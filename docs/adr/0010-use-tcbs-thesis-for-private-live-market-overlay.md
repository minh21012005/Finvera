# ADR-0010: Use TCBS Thesis for the Private Live Market Overlay

**Status**: Accepted  
**Date**: 2026-08-24  
**Decision owners**: Finvera maintainer  
**Related features**: `001-market-overview`, `002-stock-detail`

## Context

ADR-0009 removed an incorrect TCBS runtime implementation after the
`tickerCommons?index=...` REST resource was shown to contain constituent
equities rather than authoritative index rows. That removal left Finvera with
an operator-run Vnstock package workflow, which is suitable for historical
bootstrap but cannot keep a live dashboard current.

The official TCBS price-board specification separately documents the Thesis
cash-market WebSocket at
`wss://openapi.tcbs.com.vn/ws/thesis/v1/stream/normal`. It provides matched
stock prices (`s|6`) and index/breadth updates (`s|8`) for VN-Index, VN30,
HNX, and UPCOM. This is a different contract from the Ouranos one-minute
history stream previously integrated.

## Decision

Finvera will use a hybrid private-provider model:

- TCBS Thesis WebSocket is the primary current-session source for index,
  exchange breadth, and on-demand stock quote updates.
- Vnstock/KBS packages remain the primary historical/bootstrap source and the
  completed-session fallback/reconciliation source.
- Spring Boot owns the TCBS connection, authentication, normalization,
  persistence, reconnect policy, and health status. The browser never connects
  to TCBS.
- TCBS messages without a provider timestamp or sequence use the server's UTC
  receive time and carry `TCBS_STREAM_RECEIVE_TIME` and
  `TCBS_STREAM_ORDERING_UNAVAILABLE`. They are not authoritative historical
  correction evidence.
- The owner supplies a transient OTP to renew the in-memory token, whose
  maximum configured lifetime is eight hours. API keys remain environment
  secrets; OTPs and tokens are never persisted or logged.
- Outside trading hours, Finvera serves the latest accepted PostgreSQL
  snapshot and labels its session/freshness instead of treating the absence of
  WebSocket pushes as failure.

## Consequences

Market and selected stock prices update without running a Python exporter or
restarting Spring. The application must maintain heartbeat, reconnect with
bounded backoff, expose authentication health, and preserve Vnstock data when
TCBS is unavailable. Public or multi-user display remains prohibited until
provider display/redistribution rights are approved.

The retired source identifier `TCBS_IFLASH_MARKET_DATA` is explicitly
ineligible for index selection. V009 removes its previously misclassified
materialized index facts while retaining rejected ingestion audit records; this
does not apply to `TCBS_IFLASH_THESIS`.

## References

- [TCBS price-board WebSocket specification](https://developers.tcbs.com.vn/file/Websocket_bang_gia_1.0.0.pdf)
- [TCBS token exchange](https://developers.tcbs.com.vn/docs/v1.0.0/auth/token/)
- [TCBS Ouranos history stream](https://developers.tcbs.com.vn/docs/v1.0.0/stock/ws-history/)
- [ADR-0009](0009-use-vnstock-as-primary-private-market-provider.md)
