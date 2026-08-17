# Fixture Runtime Bootstrap Contract

**Version:** `market-fixture-runtime-v1`  
**Scope:** local development only; never a market-data provider.

## Package invariants

- `packageSha256` is lowercase SHA-256 of the compact UTF-8 JSON serialization of the `payload` object, preserving the contract field order, and exactly 64 hexadecimal characters.
- Every index observation is ingested through `MarketIngestionService` with source `FINVERA_FIXTURE`.
- Each `instruments` entry supplies an immutable instrument identity plus the exact
  current/reference decimal pair from which bootstrap creates an accepted equity
  observation and breadth classification. Bootstrap persists the resulting
  instrument/observation links; breadth counts are never supplied precomputed.
- `regimeScores` contains deterministic normalized inputs, not a precomputed
  regime result. Bootstrap runs the approved regime engine and persists links to
  the accepted VN-Index snapshot, breadth snapshot, and canonical fixture input
  set hash together with all calculated factors.
- Bootstrap is enabled only when `finvera.market.provider.mode=fixture` and an explicit development flag is true. It is disabled for every other mode.
- Replaying the same package is idempotent; a malformed package rolls back all of its writes.

## Canonical shape

```json
{
  "contractVersion": "market-fixture-runtime-v1",
  "packageSha256": "64 lowercase hex characters",
  "payload": {
    "source": "FINVERA_FIXTURE",
    "tradingDate": "YYYY-MM-DD",
    "asOf": "UTC instant",
    "sessionState": "PRE_OPEN | OPEN | BREAK | INTERRUPTED | CLOSED | NON_TRADING_DAY | UNKNOWN",
    "indices": [
      {
        "code": "VN_INDEX | VN30 | HNX_INDEX | UPCOM_INDEX",
        "level": "decimal string",
        "referenceLevel": "decimal string",
        "matchedVolume": "integer string",
        "matchedValueVnd": "decimal string"
      }
    ],
    "instruments": [
      {
        "id": "UUID",
        "isin": "fixture identity",
        "venue": "HOSE | HNX | UPCOM",
        "symbol": "fixture symbol",
        "price": "decimal string",
        "reference": "decimal string"
      }
    ],
    "regimeScores": {
      "TREND": "decimal string",
      "BREADTH": "decimal string",
      "MOMENTUM": "decimal string",
      "LIQUIDITY": "decimal string",
      "VOLATILITY": "decimal string"
    }
  }
}
```

No provider credential, token, iOTP, raw external payload, or real market fact belongs in this package.
