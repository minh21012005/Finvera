# ADR-0003: Use TCBS iFlash for Private Market Data v1

**Status**: Accepted  
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

Finvera will use TCBS iFlash as its v1 **read-only market-data provider** only
for one configured owner's private/personal deployment.

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

This allows real private data use with the maintainer's existing TCBS account,
but deliberately prevents public hosting and other users while TCBS is the
source. Live ingestion stops after token expiry until the owner renews it; the
UI shows accepted data's actual freshness and `PROVIDER_AUTH_REQUIRED`.

This is an engineering/product-boundary decision, not legal advice or a
substitute for TCBS written approval. Any change in usage, deployment, or data
delivery requires a fresh terms review.

## References

- [TCBS iFlash workflow](https://developers.tcbs.com.vn/docs/v1.0.0/workflow/)
- [TCBS token endpoint](https://developers.tcbs.com.vn/docs/v1.0.0/auth/token/)
- TCBS iFlash Open API Terms and Conditions supplied by the maintainer, clauses
  2(c), 2(d), and 3(c).
