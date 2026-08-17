# Quickstart and Acceptance: Market Overview

**Feature**: `001-market-overview`  
**Current phase**: Design only — commands and journeys below become executable
after `tasks.md` is approved and implementation is complete.

## Prerequisites

- Java 21 and the repository Maven wrapper.
- Node.js compatible with the committed React/Vite project and `npm`.
- A Docker-compatible container runtime for PostgreSQL/Testcontainers.
- PostgreSQL for local runtime, configured only through environment/secret
  values.
- Sanitized provider contract fixtures for normal and failure scenarios.
- A TCBS iFlash API key and owner iOTP access only after the gate in
  [tcbs-iflash-adapter.md](contracts/tcbs-iflash-adapter.md) is approved.
  Fixture mode must support development without provider credentials.
- A pinned Vnstock environment only after
  [vnstock-historical-bootstrap.md](contracts/vnstock-historical-bootstrap.md)
  passes its license, coverage, and sanitized-fixture gate.
- A private owner-only access path with no public ingress.
- Tailscale installed on the host and owner device; Serve/private routing only,
  ACL/grants limited to the owner, and Funnel disabled.

Gemini, an embedding model, Qdrant, Kafka, and `finvera-ai` are not prerequisites.

## Planned Configuration

The implementation will document exact environment names in safe example
configuration. At minimum it needs:

```text
PostgreSQL URL / database / user / password
market timezone = Asia/Ho_Chi_Minh
market provider mode = fixture | tcbs-iflash-private
historical bootstrap mode = fixture | vnstock-offline-package
contracted delay per dataset
calendar and session policy version
configured owner identity and private-access policy
owner UUID, normalized username, and offline-generated {bcrypt} password hash
session idle timeout = 30 minutes; absolute lifetime = 8 hours
TCBS API key and endpoints (tcbs-iflash-private mode only)
canonical import package contract/version and maximum accepted size
```

No provider secret, token, iOTP, Vnstock credential, or raw package may use a
`NEXT_PUBLIC_*` variable or appear in frontend configuration. TCBS tokens are
runtime-only; iOTP is transiently exchanged only after owner authorization and
is never stored, logged, generated, reused, or automated.

## Quality Commands

From `finvera-be/`:

```powershell
.\mvnw.cmd test
```

Expected after implementation: unit/boundary tests, provider contract tests,
Flyway migration tests, PostgreSQL repository tests, API/security tests, and
application context loading pass. Docker must be available for Testcontainers.

From `finvera-fe/`:

```powershell
npm run test
npm run lint
npm run build
npm run test:e2e
```

Expected after implementation: Vitest component/presentation-state tests,
lint, production build, and Playwright P1 journey pass. `test` and `test:e2e`
scripts do not exist yet; their creation belongs to implementation tasks.

## Local Runtime

After PostgreSQL and safe local configuration are available:

```powershell
# terminal 1, from finvera-be/
.\mvnw.cmd spring-boot:run

# terminal 2, from finvera-fe/
npm run dev
```

Use fixture provider mode for deterministic acceptance. The frontend calls the
Spring endpoint defined in
[market-overview.openapi.yaml](contracts/market-overview.openapi.yaml); it does
not call fixture files or the provider directly.

## P1 Happy Path — Four Main Indices

Fixture: a coherent active-session snapshot containing VN-Index, VN30, HNX
Index, and UPCOM Index with complete level, change, volume, value, source, and
time data.

1. Open the market overview page as an authorized Finvera user.
2. Confirm exactly four index cards appear in stable order.
3. Confirm every card shows value, absolute/percentage change, matched volume,
   matched value with VND unit, session state, as-of time, source, and
   `CURRENT` status.
4. Confirm direction has an icon/text label and is understandable without
   red/green color.
5. Inspect `GET /api/v1/market/overview`; all sections belong to the declared
   trading date/coherent revision and decimal facts match the fixture.

Expected evidence: HTTP 200, usable page within the NFR-001 target, no browser
request to TCBS or `finvera-ai`, and no missing value represented as zero.

## P2 Happy Path — Consolidated Breadth

Fixture: known eligible common equities across all three venues, including a
VN30 member, advance/decline/unchanged cases, and no invalid record.

1. Open the overview and locate consolidated breadth.
2. Verify the VN30 member is counted once as its HOSE security.
3. Verify `advancing + declining + unchanged = eligible` and unclassified is 0.
4. Verify the UI identifies the universe version, source, as-of time, and
   `CURRENT` status.

Expected evidence: displayed counts exactly reconcile with the fixture and use
unrounded source values for classification.

## P3 Happy Path — Deterministic Regime

Fixture: a complete versioned history with known `market-regime-v1` output.

1. Open the overview twice using identical accepted inputs.
2. Verify identical label, score, confidence, factors, weights, and as-of time.
3. Verify confidence is labeled assessment quality, not forecast probability.
4. Verify the decision-support disclaimer is present and there is no buy/sell
   instruction.

Expected evidence: API and UI match the approved regime fixture exactly; every
factor can be traced to recorded input IDs and the rule version.

### Historical bootstrap and reconciliation

Import an approved canonical Vnstock fixture containing at least 271 completed
sessions. Expected: Spring verifies contract version, checksum, tool/upstream
source, date coverage, counts, decimal strings, and adjustment status before an
atomic import. Reimporting the same checksum is idempotent. A conflicting
completed-session TCBS/Vnstock fact preserves both observations, emits
`SOURCE_CONFLICT`, and withholds the affected regime instead of averaging or
silently overwriting data.

## Critical Degraded and Failure Paths

### One index unavailable

Remove the UPCOM Index observation while retaining the other three. Expected:
HTTP 200; three usable cards; UPCOM card `UNAVAILABLE` with null facts and a
reason code; no zero placeholder; overview status reflects degradation.

### Delayed, stale, and closed data

Evaluate fixtures exactly at and around contracted delay +30 seconds and +5
minutes. Expected boundary states follow research R-004. A completed closed-day
snapshot remains labeled `CLOSED`, not stale merely because wall-clock time
passes.

### Partial breadth

Remove a valid official reference price for one eligible security. Expected:
that security is unclassified; reconciliation still holds; breadth is
`PARTIAL`; regime is withheld if minimum quality is not met.

### Correction and out-of-order delivery

Deliver an older record after a newer accepted record, then a valid correction.
Expected: the older record cannot regress the page; the correction creates a
new revision and updates dependent results with a visible updated as-of/revision.

### Provider and AI outage

Disconnect provider fixture transport while accepted data exists and disable
all AI services. Expected: last accepted facts remain visible with accurate
freshness/degraded labels; no AI error appears; provider failure is observable.

### TCBS token expiry

Expire the runtime TCBS token while accepted data exists. Expected: no new
provider request is treated as live; last accepted facts remain visible with
their normal freshness and `PROVIDER_AUTH_REQUIRED`. The owner must manually
renew with iOTP. The private renewal action may transiently accept the owner-entered iOTP for one
immediate TCBS exchange, but it must not persist, log, reuse, or retry that OTP.

### Authentication

Call the endpoint without credentials, with an invalid token, and with a valid
non-owner identity; then call as the configured owner. Expected: standard
401/403 error envelope for every denied call and 200 only for the owner. Verify
there is no public ingress, sharing link, export, market-provider credential,
or raw payload in responses or logs.

Use [private-owner-access.openapi.yaml](contracts/private-owner-access.openapi.yaml)
to validate CSRF acquisition, owner login, session status, logout, and TCBS
renewal. Verify session rotation, `FINVERA_SESSION` Secure/HttpOnly/
SameSite=Strict, 30-minute idle and eight-hour absolute expiry, uniform invalid
credential errors, and bounded login rate limiting. Direct host ports and
Tailscale Funnel must be unreachable; Tailscale access alone must not bypass
the Spring owner login.

## Operational Evidence

During the paths above, verify metrics/logs distinguish:

- source authentication/connectivity failure;
- ingest lag and stale data;
- rejected/invalid or out-of-order records;
- breadth/regime calculation failure;
- public API latency/failure.

Correlation IDs, dataset, subject, reason code, and timestamps are allowed.
Credentials, tokens, signed requests, full provider payloads, and private user
data are forbidden.
