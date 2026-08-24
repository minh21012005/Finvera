# ADR-0009: Use Vnstock as Primary Private Market Provider

**Status**: Superseded in part by ADR-0010  
**Date**: 2026-08-24  
**Decision owners**: Finvera maintainer  
**Related feature**: `001-market-overview`

## Context

Feature 001 initially treated TCBS as the live private provider and Vnstock as
an offline historical bootstrap. Official TCBS endpoint review later showed
that the available TCBS REST index path returns stock-basket constituents, not
index-level VN-Index/VN30/HNX/UPCOM snapshots. TCBS WebSocket can emit current
index values, but the documented stream does not expose timestamp, sequence, or
revision semantics sufficient for immutable completed-session reconciliation.

Vnstock documents KBS-backed index and equity market APIs, including historical
OHLCV and current quote/summary methods. The maintainer's current Vnstock
Community entitlement permits personal, non-commercial use with 60
requests/minute, which is enough for bounded owner-operated local packages and
low-frequency polling experiments. Vnstock is an API connector, not the owner
or redistributor of the underlying market data.

## Decision

Finvera will use Vnstock/KBS as the primary historical and completed-session
provider for Feature 001. ADR-0010 subsequently adds TCBS Thesis as the
current-session live overlay.

- Vnstock runs only in local owner-operated tools under `tools/market-data`.
- The tools emit canonical, checksummed, decimal-string packages.
- Spring Boot validates and imports those packages through an explicit
  application boundary; it does not call Vnstock directly and never exposes
  provider credentials or raw payloads to the browser.
- The incorrect TCBS REST/Ouranos index runtime is removed. ADR-0010
  reintroduces TCBS only through the separately documented Thesis price-board
  WebSocket contract.
- Vnstock Community data must be labelled according to proven freshness. Do not
  claim guaranteed realtime streaming. Vnstock Pipeline realtime WebSocket is a
  separate Sponsor-only capability and requires a future contract before use.
- Public, commercial, or multi-user display still requires explicit data
  rights from an appropriate provider.

## Consequences

The private MVP becomes simpler: no TCBS API key, no iOTP renewal, no eight-hour
token lifecycle, and no misleading REST index ingestion. The tradeoff is that
market data enters through an operator-controlled package/import workflow, not
through a continuously connected realtime provider. UI sections must show
source, freshness, and degraded states accurately.

## References

- [Vnstock repository](https://github.com/thinh-vu/vnstock)
- [Vnstock license](https://www.vnstocks.com/onboard/giay-phep-su-dung)
- [Vnstock market data docs](https://vnstocks.com/docs/vnstock-data/market-layer-v3)
- [Vnstock realtime pipeline docs](https://vnstocks.com/docs/vnstock-pipeline/ket-noi-du-lieu-realtime)
- [ADR-0003](0003-use-tcbs-for-private-market-data-v1.md)
- [ADR-0004](0004-use-vnstock-for-private-historical-bootstrap.md)
