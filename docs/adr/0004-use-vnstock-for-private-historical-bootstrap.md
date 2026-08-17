# ADR-0004: Use Vnstock for Private Historical Bootstrap

**Status**: Accepted conditionally  
**Date**: 2026-08-17  
**Decision owners**: Finvera maintainer  
**Related feature**: `001-market-overview`

## Context

ADR-0003 selects TCBS iFlash for live private market data. TCBS's documented
price-history operation covers intraday matches and does not establish the
completed daily OHLCV depth required by `market-regime-v1`, including SMA200
and a 252-session volatility window.

Vnstock documents historical equity and index OHLCV access and a custom license
focused on personal, non-commercial use. It is an extraction tool and does not
own the underlying provider data, so its license does not independently grant
storage, display, or redistribution rights for that data.

## Decision

Use a pinned Vnstock release only as an offline owner-operated historical
bootstrap and bounded gap-recovery tool for the private deployment.

- The exporter produces a versioned, checksummed canonical package with exact
  Vnstock version and upstream-source identity.
- Spring Boot validates and atomically imports that package; the exporter never
  writes PostgreSQL directly.
- Vnstock does not run in `finvera-ai`, in the browser, or as a permanent
  deployable service.
- TCBS remains the live source. Overlapping completed-session facts are retained
  separately and reconciled; material conflict withholds the affected regime.
- Production implementation is conditional on sanitized fixtures proving at
  least 271 completed sessions, usable adjustment/provenance semantics, request
  limits, and owner acceptance/verification of software and upstream terms.
- Public or multi-user delivery requires replacement with a commercial source
  granting explicit display and redistribution rights.

## Consequences

Feature 001 can retain its deterministic regime scope without coupling Spring
to Python or adding a market microservice. The tradeoff is a manual operational
bootstrap, two-source reconciliation, and no SLA or public-use right. Missing
or conflicting history degrades the regime only; live indices and breadth stay
usable when TCBS data is valid.

## Alternatives

- Defer Market Regime: simpler, but the owner chose to retain Feature 001 scope.
- Run Vnstock in `finvera-ai`: rejected because market ingestion is outside the
  AI/RAG boundary.
- Add a Python market service: rejected as unjustified deployment complexity.
- FireAnt: requires confirmation of API entitlement and data rights.
- FiinGroup: preferred future commercial/public source, but procurement and
  cost are deferred for the private MVP.

## Reference

- [Vnstock repository](https://github.com/thinh-vu/vnstock)
