# Private Market Overview ingress runbook

**Applies to:** Feature 001 while TCBS iFlash and Vnstock-derived data remain
licensed for the configured owner's private use only.  This is a release gate,
not an optional hardening guide.

## Local-development deferral

**Owner decision (2026-08-17):** defer Tailscale installation and Serve
configuration until deployment or remote/multi-device access is needed. During
this local-only development period, every Finvera process MUST remain bound to
`127.0.0.1`. Do not configure router port forwarding, public DNS, Cloudflare
Tunnel/ngrok or another public tunnel, Tailscale Funnel, or any LAN/WAN ingress.

This deferral does not waive the private-ingress requirement. Complete the
remaining sections of this runbook and the T051 task before deployment or any
remote access.

## Invariant

The browser reaches Finvera only through a tailnet-only HTTPS endpoint provided
by Tailscale Serve. Spring Boot listens on `127.0.0.1` only and no Tailscale
Funnel, public reverse proxy, port-forwarding rule, public DNS record, or direct
LAN/WAN application port is permitted.

The Spring configuration enforces the backend half of this invariant with
`server.address: 127.0.0.1`. A local reverse proxy, if used to serve the SPA and
route `/api` to Spring, must itself listen only on `127.0.0.1`. The operator's
tailnet policy must permit the Finvera HTTPS port only to the configured owner
identity and device; do not rely on the app login as the sole network control.

## Prepare secrets and local processes

1. In a password manager, generate a unique owner password of at least 20
   characters. Do not paste it into source files, terminal history, issue
   trackers, shell profiles, or `.env` files committed to Git.
2. Generate the owner UUID locally with PowerShell:

   ```powershell
   [guid]::NewGuid().ToString()
   ```

3. In an approved local secret-management workflow, create a BCrypt password
   hash (cost 12 or higher) from that password. Store only the hash and the
   owner UUID/username in the deployment secret store. Never log either the
   password or TCBS API key/iOTP/token.
4. Supply the following deployment-only environment variables through that
   secret store: `FINVERA_DATABASE_URL`, `FINVERA_DATABASE_USERNAME`,
   `FINVERA_DATABASE_PASSWORD`, `FINVERA_OWNER_ID`, `FINVERA_OWNER_USERNAME`,
   `FINVERA_OWNER_PASSWORD_HASH`, `FINVERA_MARKET_INDEX_CONTRACTED_DELAY`, and
   (for live TCBS ingestion — see "Activate live TCBS ingestion" below)
   `FINVERA_TCBS_API_KEY`.
5. Start Spring Boot without changing `server.address`; confirm its listener is
   loopback-only. Start the SPA/static server and any local reverse proxy on
   `127.0.0.1` only. The reverse proxy must forward `/api` to
   `http://127.0.0.1:8080` and serve the SPA from the same HTTPS origin so the
   Strict session cookie remains same-site.

## Configure tailnet-only ingress

Perform these commands on the Finvera host after authenticating the host to the
intended tailnet. They change Tailscale state and therefore must be performed by
the deployment operator, not by CI.

```powershell
# Remove any prior public Funnel configuration before enabling the private route.
tailscale funnel reset

# Remove stale Serve routes, then proxy the private HTTPS hostname to the local
# SPA/reverse-proxy listener. Replace 8081 only with another loopback port.
tailscale serve reset
tailscale serve --bg --https=443 http://127.0.0.1:8081

# Record the effective configuration as deployment evidence.
tailscale serve status --json
tailscale funnel status --json
```

`tailscale serve` is tailnet-only; `tailscale funnel` makes a service publicly
reachable. Do not run `tailscale funnel` for Finvera. In the tailnet policy,
allow only the named owner principal/device to reach this host's HTTPS service;
deny all other principals. Do not add a Funnel node attribute or public ACL
exception.

## Activate live TCBS ingestion

Complete this section only after the sections above pass (loopback-only
processes at minimum; tailnet-only ingress before any remote/multi-device
access). Live mode adds one outbound host
(`https://openapi.tcbs.com.vn`, already allowlisted by
`contracts/tcbs-iflash-adapter.md`) and one owner-only renewal endpoint; it
does not change the ingress invariant above.

1. Obtain a TCBS iFlash OpenAPI key for the owner's own account. Store it only
   in the deployment secret store as `FINVERA_TCBS_API_KEY`; never commit it,
   log it, or place it in a request the browser can read.
2. Set `FINVERA_MARKET_PROVIDER_MODE=live` and
   `FINVERA_MARKET_PROVIDER_LIVE_ENABLED=true`. Leave
   `FINVERA_MARKET_TCBS_POLL_INTERVAL_MS` at its default (60000) unless a
   different cadence has been evaluated against the confirmed-safe TCBS rate
   probe (5 requests/second) in `contracts/tcbs-iflash-adapter.md`.
3. Start (or restart) the backend. `TcbsLivePollingScheduler` starts polling
   immediately but every tick reports `PROVIDER_AUTH_REQUIRED` and skips until
   step 4 completes — the app does not crash or block other features while
   unauthenticated (Constitution Principle VII).
4. As the logged-in owner, call the renewal endpoint with a current OTP from
   the TCInvest app (or the registered email/SMS flow):

   ```powershell
   # After fetching a CSRF token and authenticating as the owner (see the SPA
   # login flow or curl equivalent used elsewhere in this runbook):
   curl.exe -X POST https://<tailnet-host>/api/v1/market/providers/tcbs/token-renewal `
     -H "X-CSRF-TOKEN: <token>" -H "Content-Type: application/json" `
     -b "FINVERA_SESSION=<session-cookie>" `
     -d '{"otpMethod":"totp","otp":"<current-6-digit-code>"}'
   ```

   A `204`/empty success response means the token was accepted; the session
   is held in memory only and is never returned in the response. TCBS caps
   the token at 8 hours, after which the owner must repeat this step — expect
   to do this once per working session, not once per deployment.
5. Confirm activation: `GET /actuator/health` should report the market
   observability indicator as `UP`/`READY` within one poll interval (default
   60s), and `GET /api/v1/market/overview` should reflect real index levels
   instead of the fixture package's synthetic values.
6. Record only pass/fail and timestamps for this activation in the private
   deployment record — never the API key, OTP, or bearer token.

## Required release verification

Perform the checks from the owner device and a separate non-owner device. Store
only pass/fail, timestamp, host name, and the Tailscale status JSON in the
private deployment record; redact hostnames if that record is shared. Never
store cookies, passwords, iOTP values, TCBS API keys, access tokens, or response
bodies.

| Check | Expected result | Evidence to record |
|---|---|---|
| `tailscale funnel status --json` | No active Funnel endpoint | command timestamp + `PASS` |
| `tailscale serve status --json` | HTTPS route targets only `127.0.0.1` | command timestamp + `PASS` |
| Owner tailnet device opens the HTTPS hostname | Market page is reachable and requires owner login | timestamp + `PASS` |
| Non-owner tailnet device opens the same hostname | Network policy denies access | timestamp + `PASS` |
| Device outside the tailnet opens the hostname | No public route/connectivity | timestamp + `PASS` |
| Host LAN/WAN address on backend port 8080 | Connection is refused; loopback remains the only listener | timestamp + `PASS` |
| Browser network inspector after login | `FINVERA_SESSION` has `Secure`, `HttpOnly`, `SameSite=Strict` | timestamp + `PASS` |
| State-changing request without `X-CSRF-TOKEN` | HTTP 403 and no state change | timestamp + `PASS` |
| Same request with a freshly fetched CSRF token and valid owner session | Contracted request succeeds | timestamp + `PASS` |

If any check fails, do not enable provider ingestion or give the endpoint to
anyone. Correct the configuration, reset Serve/Funnel, and repeat the entire
table.

## Source and client-bundle secret check

Before every private release, run the automated backend security tests and the
frontend production build. Scan only for matching file names, never print file
contents that could contain a secret:

```powershell
cd D:\Finvera\finvera-be
.\mvnw.cmd '-Dtest=OwnerAccessSecurityTests,MarketOverviewControllerTests,TcbsRenewalControllerTests,TcbsRenewalServiceTests,TcbsHttpSessionStateTests,TcbsHttpRestClientTests' test

cd D:\Finvera\finvera-fe
npm run build

cd D:\Finvera
rg -l -i --glob '!**/node_modules/**' --glob '!**/target/**' --glob '!**/.git/**' '(tcbs.{0,24}(api.?key|token|secret)|iotp.{0,24}[:=])' .
rg -a -l -i '(tcbs.{0,24}(api.?key|token|secret)|iotp.{0,24}[:=])' finvera-fe\dist
```

The scans may list safe contract/test files that contain field names. Inspect
only the listed paths through a secret-safe review process; a real credential,
token, OTP, or password in source, logs, API responses, or `dist` is a release
blocker. Rotate the exposed credential and invalidate affected sessions before
continuing.

## Rollback and incident response

1. Immediately remove all externally reachable routes:

   ```powershell
   tailscale serve reset
   tailscale funnel reset
   ```

2. Stop the local SPA/reverse proxy. Keep Spring bound to loopback while
   investigating; do not open port 8080 as a workaround.
3. Revoke/rotate the TCBS API key and owner password if either may have been
   exposed. Invalidate owner sessions by restarting the backend after the
   corrected secret configuration is in place.
4. Preserve only sanitized diagnostics: correlation IDs, timestamps, endpoint
   path, status code, and configuration state. Do not attach headers, cookies,
   request bodies, provider responses, or credentials to incident records.
5. Repeat every verification in the release table before restoring Serve.

## Operational limitations

This runbook does not grant multi-user or public redistribution rights. A
public/multi-user rollout requires a separately licensed market-data provider,
a new adapter contract, an ADR, and a revised ingress/authentication design.

## References

- [Tailscale Serve CLI reference](https://tailscale.com/docs/reference/tailscale-cli/serve)
- [Tailscale Funnel CLI reference](https://tailscale.com/docs/reference/tailscale-cli/funnel)
