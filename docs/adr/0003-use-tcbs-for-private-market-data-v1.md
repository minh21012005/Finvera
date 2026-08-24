# ADR-0003: Use TCBS iFlash for Private Market Data v1

**Status**: Superseded by ADR-0009 and ADR-0010
**Date**: 2026-08-17  
**Decision owners**: Finvera maintainer  
**Related feature**: `001-market-overview`

## Context

The initial Market Overview needs real Vietnamese market data. The maintainer
has selected TCBS iFlash and intends to run Finvera privately at first. TCBS
documents API-key/iOTP token acquisition and a maximum eight-hour access-token
lifetime. The TCBS iFlash Open API terms supplied by the maintainer constrain
use to the client's own securities-transaction purpose and require written
approval before sharing original or processed information with third parties.

## Decision

Finvera originally selected TCBS iFlash as its v1 **read-only market-data
provider** only for one configured owner's private/personal deployment.

- The adapter permits approved market-data operations only; it never calls
  trading, account, cash, portfolio, or order APIs.
- The owner manually completes iOTP for each token renewal. The application
  never stores, generates, or automates iOTP; access tokens are runtime-only.
- The deployment has no public ingress, and Spring plus private-network access
  enforce the single-owner policy. No invitations, sharing links, exports,
  webhooks, or third-party delivery may disclose TCBS-derived data.
- Before public or multi-user delivery, Finvera must procure a provider with
  explicit display/redistribution rights, build its adapter, pass contract
  tests, and record a replacement ADR. A feature flag is not enough.

## Consequences

This decision is no longer active for runtime implementation. Official endpoint
review and owner activation evidence on 2026-08-24 showed that TCBS REST
`tickerCommons?index={N}` returns constituent/equity rows, not authoritative
VN-Index/VN30/HNX/UPCOM index-level snapshots. Maintaining TCBS also keeps an
OTP/token operational burden that is unnecessary for the private MVP.

TCBS artifacts remain historical evidence. ADR-0010 reintroduces only the
official Thesis price-board WebSocket as a private live overlay; it does not
restore the invalid REST index mapping described here.

This is an engineering/product-boundary decision, not legal advice or a
substitute for TCBS written approval. Any change in usage, deployment, or data
delivery requires a fresh terms review.

## References

- [TCBS iFlash workflow](https://developers.tcbs.com.vn/docs/v1.0.0/workflow/)
- [TCBS token endpoint](https://developers.tcbs.com.vn/docs/v1.0.0/auth/token/)
- TCBS iFlash Open API Terms and Conditions supplied by the maintainer, clauses
  2(c), 2(d), and 3(c).
