# Quickstart and Acceptance: Market Overview

**Feature:** `001-market-overview`

**Current provider decision:** ADR-0010 uses the official TCBS Thesis WebSocket
for the private live overlay. ADR-0009 retains Vnstock/KBS for history,
completed-session fallback, and reproducible bootstrap packages.

## Prerequisites

- Java 21 and the repository Maven wrapper.
- Node.js compatible with the committed React/Vite project and `npm`.
- PostgreSQL for local runtime, configured only through environment/secret
  values.
- A loopback-only owner access path for local acceptance.
- A configured local Vnstock Community API key when generating private market
  packages.

Gemini, an embedding model, Qdrant, Kafka, and `finvera-ai` are not
prerequisites. A TCBS API key and current TOTP are required only when enabling
the optional live overlay.

## Runtime modes

```text
FINVERA_MARKET_PROVIDER_MODE=fixture
```

Use this for deterministic fixture acceptance.

```text
FINVERA_MARKET_PROVIDER_MODE=vnstock-package-private
FINVERA_MARKET_IMPORT_ENABLED=true
FINVERA_MARKET_IMPORT_PACKAGE_PATH=<local package path>
```

Use this to import a reviewed Vnstock/KBS market package into PostgreSQL. Turn
`FINVERA_MARKET_IMPORT_ENABLED` back to `false` after import unless you
intentionally want startup to re-check the same package.

No provider credential, package path, token, OTP, or raw market payload may use
a `VITE_*` variable or appear in frontend configuration.

For live private data, additionally configure only in the backend environment:

```text
FINVERA_TCBS_LIVE_ENABLED=true
FINVERA_TCBS_API_KEY=<secret>
FINVERA_TCBS_MAX_DYNAMIC_SYMBOLS=100
FINVERA_STOCK_QUOTE_LIVE_ENABLED=true
```

Start the backend, sign in as the owner, open `/settings/live-data`, and submit
the current TOTP. Spring exchanges it for an in-memory token and connects to
the official Thesis WebSocket. The browser polls Finvera endpoints every 30
seconds; it never connects to TCBS directly. After restart or token expiry, the
owner renews the session again. Without a live stream, the last accepted
PostgreSQL snapshot/history remains available and is labelled stale as needed.

## Generate a Vnstock market package

From `tools/market-data/vnstock-export/`:

```powershell
uv run --project ..\provider-poc python .\export_history.py `
  --market-overview `
  --start 2024-01-01 `
  --end 2026-08-24 `
  --output .\output
```

The package contract is `vnstock-market-private-package-v1`. It contains
`indexRecords` for `VN_INDEX`, `VN30`, `HNX_INDEX`, and `UPCOM_INDEX`.
`referenceLevel` is derived from the previous completed daily close and the
record carries `VNSTOCK_DAILY_CLOSE_REFERENCE_DERIVED`.

## Start local runtime

Backend from `finvera-be/`:

```powershell
.\mvnw.cmd spring-boot:run
```

Frontend from `finvera-fe/`:

```powershell
npm run dev
```

Keep both services bound to `127.0.0.1` during local development. Tailscale is
required only before deployment or remote/multi-device access.

## P1 acceptance: four main indices

1. Log in as the configured owner.
2. Open the market overview page.
3. Confirm exactly four index cards appear in stable order: VN-Index, VN30,
   HNX-Index, UPCOM-Index.
4. Confirm each available card shows level, absolute change, percentage change,
   matched volume/value when present, source, session state, trading date,
   as-of timestamp, and data status.
5. Confirm missing provider fields are shown as unavailable/partial, never as
   zero placeholders.

Expected evidence: HTTP 200 from `GET /api/v1/market/overview`; no browser
request to Vnstock, KBS, TCBS, or `finvera-ai`.

## P2 acceptance: breadth

1. Import a package with enough accepted equity observations for the configured
   breadth universe.
2. Open consolidated breadth on the overview page.
3. Verify `advancing + declining + unchanged + unclassified = eligible`.
4. Verify the UI identifies universe version, source, as-of time, and data
   status.

If equity coverage is insufficient, breadth must degrade with
`BREADTH_NOT_AVAILABLE` or a precise partial reason. It must not fabricate a
market-wide breadth count from incomplete provider data.

## P3 acceptance: deterministic regime

1. Import a package with enough accepted index history for regime evaluation.
2. Open the overview twice using identical accepted inputs.
3. Verify identical label, score, confidence, factor list, weights, and as-of
   time.
4. Verify confidence is labeled assessment quality, not forecast probability.
5. Verify the decision-support disclaimer is present and there is no buy/sell
   instruction.

If the minimum input quality is not met, the regime section is withheld with a
specific reason code. It must not infer or predict missing facts.

## Quality commands

From `finvera-be/`:

```powershell
.\mvnw.cmd test
```

From `finvera-fe/`:

```powershell
npm run test
npm run lint
npm run build
```

From `tools/market-data/provider-poc/`:

```powershell
uv run pytest ..\vnstock-export\tests
```

## Operational evidence

Record pass/fail, command names, timestamps, package checksum, and accepted
batch counts only. Do not store raw package contents, credentials, cookies,
tokens, provider responses, or private user data in docs or logs.
