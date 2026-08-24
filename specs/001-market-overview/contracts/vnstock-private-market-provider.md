# Provider Contract: Vnstock Private Market Package

**Contract version**: `vnstock-market-private-package-v1`  
**Feature**: `001-market-overview`  
**Status**: Approved for private, owner-operated package import; not approved
for public redistribution or guaranteed realtime claims.

## Purpose and boundary

Vnstock/KBS is Finvera's primary private Feature 001 provider after ADR-0009.
It runs only as a local owner-operated exporter under `tools/market-data`.
Spring Boot does not call Vnstock directly. The browser never calls Vnstock.

The exporter writes a canonical JSON package. Spring validates the package
checksum, schema, decimal strings, source, subject identities, dates, and
provenance before creating immutable accepted observations in PostgreSQL.

## Confirmed provider capabilities

Official Vnstock documentation identifies:

- `Market.index(symbol).ohlcv(...)` for historical index OHLCV;
- `Market.index(symbol).quote()` for current index quote;
- `Market.index(symbol).summary()` for index summary;
- `Market.equity(symbol).ohlcv(...)` for historical equity OHLCV;
- `Market.equity(symbol).quote()` for current equity quote;
- Community entitlement of 60 requests/minute for personal/non-commercial
  usage.

The existing Finvera POC has already verified KBS historical access for
VNINDEX, HNXINDEX, UPCOMINDEX, selected equities, and a completed full-universe
equity scan. VN30 support and current quote column semantics must still be
validated by a sanitized owner-run probe before any quote/realtime claim.

## Package schema

Top-level required fields:

- `contractVersion`: exactly `vnstock-market-private-package-v1`;
- `toolName`: `finvera-vnstock-exporter`;
- `toolVersion`;
- `upstreamSource`: `VNSTOCK_KBS`;
- `generatedAt`, `rangeStart`, `rangeEnd`;
- `packageSha256`;
- `canonicalPayload`: canonical JSON over `records` and `indexRecords`;
- `records`: equity daily-history records, may be empty;
- `indexRecords`: index daily snapshot records, may be empty.

At least one of `records` or `indexRecords` must be non-empty.

### `indexRecords[]`

Each index record contains:

- `code`: one of `VN_INDEX`, `VN30`, `HNX_INDEX`, `UPCOM_INDEX`;
- `providerSymbol`: the exact Vnstock symbol used by the exporter;
- `tradingDate`;
- `observedAt`: UTC instant;
- `sessionState`: `CLOSED` for completed daily OHLCV packages unless a later
  quote contract proves current-session semantics;
- `dataStatus`: `CURRENT`, `DELAYED`, `STALE`, `PARTIAL`, or `UNAVAILABLE`;
- `level`: decimal string with maximum six fractional digits;
- `referenceLevel`: previous completed close from the same Vnstock/KBS series,
  decimal string with maximum six fractional digits;
- `matchedVolume`: optional non-negative integer when Vnstock supplies volume;
- `matchedValueVnd`: optional decimal string with maximum four fractional
  digits when Vnstock supplies value;
- `reasonCodes`: includes `VNSTOCK_DAILY_CLOSE_REFERENCE_DERIVED`;
- `canonicalRecord`: canonical JSON over the record minus `canonicalRecord`.

The first row of an OHLCV series is not exported as an index snapshot because
there is no same-source previous close to use as the reference basis.

## Freshness and realtime rule

Vnstock Community packages are batch/polling data. The UI may show them as
current only when the package's `observedAt` and trading date satisfy
Finvera's freshness policy. Do not call the data realtime unless a future
contract validates Vnstock Pipeline realtime or exact current quote semantics.

## Prohibited behavior

- No raw Vnstock responses in source, DB, logs, or API responses.
- No API key in source or browser bundle.
- No automatic unbounded scraping, high-frequency polling, or hidden retries.
- No public/multi-user display without separate data rights.
