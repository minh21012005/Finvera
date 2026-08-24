# Private Market Overview runbook

**Applies to:** Feature `001-market-overview` after ADR-0010. TCBS Thesis is
the private live overlay; Vnstock/KBS remains the historical and
completed-session package source.

## Local-development deferral

**Owner decision (2026-08-17):** defer Tailscale installation and Serve
configuration until deployment or remote/multi-device access is needed. During
local development, every Finvera process must remain bound to `127.0.0.1`.

Do not configure router port forwarding, public DNS, Cloudflare Tunnel/ngrok,
Tailscale Funnel, or another LAN/WAN ingress while the project is still a
private development system.

## Runtime invariant

The browser calls Spring Boot only. It never calls TCBS, Vnstock, KBS, or any
provider endpoint directly. Live TCBS frames and reviewed Vnstock packages are
validated and normalized by Spring before accepted observations are written to
PostgreSQL. The TCBS token is memory-only; the API key and TOTP never reach the
browser bundle or database.

PostgreSQL remains the source of truth for the UI. Raw provider payloads,
provider credentials, API keys, cookies, and local export outputs are not
committed.

## Required environment

Supply these values from the local shell, IntelliJ run configuration, or a
private secret store:

```text
FINVERA_DATABASE_URL
FINVERA_DATABASE_USERNAME
FINVERA_DATABASE_PASSWORD
FINVERA_OWNER_ID
FINVERA_OWNER_USERNAME
FINVERA_OWNER_PASSWORD_HASH
FINVERA_MARKET_INDEX_CONTRACTED_DELAY
FINVERA_MARKET_PROVIDER_MODE=fixture | vnstock-package-private
FINVERA_MARKET_FIXTURE_BOOTSTRAP_ENABLED=false
FINVERA_MARKET_IMPORT_ENABLED=true only while importing a reviewed package
FINVERA_MARKET_IMPORT_PACKAGE_PATH=<local path to generated market package>
FINVERA_TCBS_LIVE_ENABLED=false | true
FINVERA_TCBS_API_KEY=<secret; required only when live is enabled>
FINVERA_TCBS_MAX_DYNAMIC_SYMBOLS=100
FINVERA_STOCK_QUOTE_LIVE_ENABLED=false | true
```

No provider secret or package path may use a `VITE_*` variable.

## Generate a Vnstock market package

Run this from `tools/market-data/vnstock-export/` after your Vnstock Community
API key is configured locally:

```powershell
uv run --project ..\provider-poc python .\export_history.py `
  --market-overview `
  --start 2024-01-01 `
  --end 2026-08-24 `
  --output .\output
```

The output file name is:

```text
output\market-overview-2024-01-01-2026-08-24.json
```

The package uses contract version `vnstock-market-private-package-v1` and
contains `indexRecords` for `VN_INDEX`, `VN30`, `HNX_INDEX`, and `UPCOM_INDEX`.
The exporter derives `referenceLevel` from the previous completed daily close
and records `VNSTOCK_DAILY_CLOSE_REFERENCE_DERIVED` in `reasonCodes`.

## Import into Spring

1. Review the generated package locally. Do not paste raw market values or raw
   package content into Git, issues, or logs.
2. Start PostgreSQL and configure the backend environment:

   ```powershell
   $env:FINVERA_MARKET_PROVIDER_MODE = "vnstock-package-private"
   $env:FINVERA_MARKET_IMPORT_ENABLED = "true"
   $env:FINVERA_MARKET_IMPORT_PACKAGE_PATH = "D:\Finvera\tools\market-data\vnstock-export\output\market-overview-2024-01-01-2026-08-24.json"
   ```

3. Start Spring Boot from `finvera-be/`.
4. Confirm the import batch is accepted and `GET /api/v1/market/overview`
   returns Vnstock/KBS-sourced index facts instead of fixture values.
5. Turn `FINVERA_MARKET_IMPORT_ENABLED` back to `false` after the import unless
   you intentionally want startup to re-check the same package. Re-importing
   the same checksum is idempotent.

## Run the frontend

From `finvera-fe/`:

```powershell
npm run dev
```

Open the local Vite URL from the same machine. The UI reads Spring responses
only and refreshes Market Overview and a mounted Stock Detail overview every
30 seconds while retaining the last usable snapshot on transient failures.

## Enable and renew the TCBS live overlay

1. Set `FINVERA_TCBS_LIVE_ENABLED=true`, the server-side API key, and
   `FINVERA_TCBS_MAX_DYNAMIC_SYMBOLS` to a positive maximum number of on-demand
   stock subscriptions. Opening an active stock detail registers that symbol
   automatically. Set
   `FINVERA_STOCK_QUOTE_LIVE_ENABLED=true` to overlay those accepted prices on
   Stock Detail.
2. Restart Spring because these are backend startup settings.
3. Sign in as the owner and open `/settings/live-data`.
4. Submit the current TCInvest TOTP. It is exchanged once and discarded; the
   resulting token remains in memory for at most eight hours.
5. Confirm status becomes `READY`. During an open session, new `s|8` index and
   `s|4`/`s|6` quote frames are stored automatically. No tool or backend restart
   is needed for each update.

If a database created before migration V009 ever shows equity-like values such
as `27650` as an index level, restart the current backend once so Flyway can
apply `V009__quarantine_deprecated_tcbs_index_source.sql`. Do not manually
delete Thesis rows; the migration targets only retired source
`TCBS_IFLASH_MARKET_DATA` and preserves its rejected ingestion audit trail.

Outside trading hours or during provider interruption, no new push is expected.
Spring continues serving the latest accepted PostgreSQL snapshot and applies
its freshness labels. Restarting Spring requires a fresh TOTP renewal.

## Required validation

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

Before any private release, scan file names only; do not print potential secret
contents:

```powershell
cd D:\Finvera
rg -l -i --glob '!**/node_modules/**' --glob '!**/target/**' --glob '!**/.git/**' '(api.?key|token|secret|iotp|password)' .
rg -a -l -i '(api.?key|token|secret|iotp|password)' finvera-fe\dist
```

Expected result: any match is either a safe example, a test field name, or a
documentation warning. A real credential, token, OTP, password, raw package, or
provider response in source/logs/dist is a blocker.

## Deployment ingress gate

Tailscale Serve remains deferred for local development. Before remote or
multi-device access:

1. Keep Spring bound to `127.0.0.1`.
2. Disable Tailscale Funnel.
3. Expose only a tailnet-only HTTPS Serve route to the local SPA/reverse proxy.
4. Restrict the tailnet ACL to the configured owner device/principal.
5. Verify non-owner tailnet and public internet access are denied.

This provider decision does not grant public redistribution rights. A public or
multi-user rollout still requires a separately licensed market-data contract, a
new adapter contract, an ADR, and a revised security model.
