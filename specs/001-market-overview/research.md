# Research: Market Overview

**Feature**: `001-market-overview`  
**Date**: 2026-08-17  
**Status**: Fixture implementation validated; TCBS capability and Vnstock upstream-use gates remain before live implementation

This document resolves the technical dependencies identified by the feature
specification. Provider documentation establishes technical feasibility, not a
right to redistribute market data.

## R-001 — Initial Market-Data Provider

**Decision**: Implement a replaceable `MarketDataProvider` port with TCBS
iFlash Market Data as the first read-only adapter. It is permitted only for the
configured owner's private/personal deployment. Use documented REST market
reads and, after the capability gate, the documented price-board WebSocket.
Do not call trading, account, cash, portfolio, or order APIs. Use Vnstock only
for owner-operated historical bootstrap/gap recovery. Do not permit public or
multi-user delivery while either personal-use source is active.

**Rationale**: TCBS documents API-key/iOTP token acquisition, market-data REST
operations, and a price-board WebSocket. Its terms supplied by the owner limit
use to the client's own securities-transaction purpose and require written
approval before giving original or processed information to third parties.

**Alternatives considered**:

- A commercial source such as FiinGroup is a future public/multi-user candidate,
  but procurement and display/redistribution terms must be negotiated.
- Undocumented or reverse-engineered public endpoints were rejected because
  their schemas, availability, correction behavior, and usage rights are not a
  dependable contract.
- Building a provider-neutral ingestion platform before the first adapter was
  rejected as speculative. The port normalizes only Feature 1 needs.

**Risks/validation**: TCBS requires an account, API key, and owner-initiated
OTP. The documented token lifetime is at most eight hours; Finvera cannot
automate the OTP flow, so expiry pauses live ingestion and must surface
`PROVIDER_AUTH_REQUIRED`. Before any real adapter task begins, capture
sanitized fixtures and prove exact dataset entitlement, schemas, rate limits,
latency/delay, index/symbol mapping, history depth, correction behavior,
reference prices, and access constraints. If the account cannot supply a
required fact or delivery becomes public/multi-user, select a separately
licensed provider and amend this research, contract, and ADR.

The first sanitized TCBS TOTP probe on 2026-08-17 returned HTTP 400 at token
exchange; no market endpoint was called. A diagnostic rerun returned provider
code `203033`. A separate Email/SMS `request-otp` attempt returned HTTP 500
with provider code `203147`, before TCBS sent or the owner entered an OTP.
TCBS's public token documentation does not map either code. The probe supports
both documented TCInvest-app TOTP and registered Email/SMS OTP flows, and
preserves only an allowlisted code/schema on failure. The owner must verify
account/OpenAPI enrollment and the registered OTP method with TCBS before
retrying the probe and before the live capability gate can be assessed.

**Subsequent sanitized evidence (2026-08-17):** The owner later completed the
TOTP exchange successfully. Representative read-only REST datasets and
WebSocket authentication/subscription acknowledgement passed without retaining
credentials, payloads, or market values. No index update arrived during the
after-hours capture, so the four-index stream schema, in-session timing,
correction, rate-limit, and remaining universe semantics are still unconfirmed.
This advances but does not close T045; see the adapter contract for the exact
sanitized counts and summary hash.

Sources: [TCBS iFlash workflow](https://developers.tcbs.com.vn/docs/v1.0.0/workflow/),
[TCBS token endpoint](https://developers.tcbs.com.vn/docs/v1.0.0/auth/token/),
and TCBS iFlash Open API Terms and Conditions supplied by the owner (clauses
2(c), 2(d), and 3(c)).

### R-001A — Historical Bootstrap Source

**Decision**: Use a pinned Vnstock release as an offline, operator-invoked
historical bootstrap and bounded gap-recovery tool. Export a canonical,
checksummed package and import it through a validated Spring Boot boundary.
Vnstock MUST NOT run inside `finvera-ai`, write PostgreSQL directly, serve live
requests, or become another deployable service.

**Rationale**: TCBS's documented price-history endpoint is intraday and does
not establish the 271 completed daily sessions required by `market-regime-v1`.
Vnstock documents equity and index OHLCV access and is explicitly oriented to
personal, non-commercial research. An offline bootstrap preserves the Spring
ownership boundary and avoids a permanent Python market-data service.

**Alternatives considered**:

- Deferring regime assessment was rejected for now because the owner chose to
  retain the full Feature 001 scope.
- Running Vnstock in `finvera-ai` was rejected because market ingestion is not
  an AI/RAG responsibility.
- A new Python microservice was rejected as unjustified complexity.
- FireAnt remains a possible personal-use alternative but its API data rights
  and historical endpoint entitlement require direct confirmation.
- FiinGroup remains the preferred future commercial/public replacement.

**Technical gate evidence (2026-08-17)**: The isolated, sanitized probe under
`tools/market-data/provider-poc` pinned Vnstock `4.0.6` and selected upstream
source `KBS`. It returned 652 daily rows for VNINDEX, HNXINDEX, and UPCOMINDEX;
652 rows for representative HOSE/HNX equities VNM/SHS; and 647 rows for UPCOM
equity MCH. All samples contained `time/open/high/low/close/volume` with no
nulls. The reference probes found 1,526 stock symbols: 404 HOSE, 299 HNX, and
823 UPCOM. The latest local sanitized rerun, using the owner's Vnstock
Community entitlement (60 requests/minute), has SHA-256
`2ded0d53a27c2e2319a3dcab65ab42e8981b0196c60885b36b9e6024d186344c`.

This proves representative 271-session coverage, not a full-universe import.
It also shows that Vnstock exposes historical prices as binary `float64` and
does not expose matched trading value in the tested OHLCV shape. The canonical
exporter MUST convert provider values through their decimal string form before
validation/calculation. A missing liquidity component may use the explicit
four-of-five regime renormalization rule; it must never be synthesized from
volume or set to zero.

**Bounded full-universe probe decision (2026-08-17)**: T047 adds an explicit
non-production probe mode rather than beginning the importer. It defaults to
30 requests/minute under the owner's 60 requests/minute Community entitlement,
requires `--full-universe`, supports `--max-symbols` and `--resume`, and writes
only aggregate counts plus one-way symbol fingerprints in its gitignored
checkpoint. This permits measured coverage evidence without retaining raw
provider payloads or silently treating a partial batch as import approval.
The checkpointed batches have processed 56 of 1,526 eligible histories at 30
requests/minute: 42 usable and 14 explicitly `INSUFFICIENT_HISTORY`, with no
provider failure. The latest sanitized summary SHA-256 is
`a118e2cb6565fca08dab61747c42eb441c96091ac6ec7cb4da918a79131812fd`.

**Remaining risks/validation**: Vnstock is an extraction tool, not the data
owner. Its software license alone does not establish upstream display rights or
an SLA. On 2026-08-18, the owner confirmed KBS accepts the intended private
storage and analysis use; this is limited to the contract's local/private
boundary and does not permit public display, redistribution, multi-user, or
remote use. Request limits, adjustment/correction semantics, and bounded
full-universe coverage still require evidence before this provider can be
called ready. These semantic checks remain blocking even though the
representative technical coverage probe passed.

Source: [Vnstock repository and license summary](https://github.com/thinh-vu/vnstock).

## R-002 — Module and Data Ownership

**Decision**: The Spring Boot `market` module owns ingestion, normalization,
validation, calendar/session interpretation, accepted snapshots, breadth, regime
calculation, and the public overview use case. PostgreSQL stores authoritative
accepted records and versioned policies. Redis may cache only the latest
assembled overview after correctness is established; Redis is not required for
the first implementation. Kafka and `finvera-ai` are not used.

**Rationale**: These are deterministic market capabilities behind the public
API boundary. Keeping them in one modular-monolith vertical slice avoids
distributed consistency and preserves reproducibility. The browser calls Spring
Boot only.

**Alternatives considered**:

- A standalone ingestion microservice and Kafka were rejected because Feature 1
  has no measured throughput, team-ownership, or independent-deployment need.
- The Python AI service was rejected because no generative or retrieval behavior
  is involved.

**Risks/validation**: Package-boundary tests MUST prevent market domain logic
from depending on web, JPA, provider, Redis, Kafka, or AI types.

## R-003 — Ingestion, Corrections, and Ordering

**Decision**: Normalize each provider message into an immutable observation,
validate it, and accept it transactionally. The natural idempotency key is
`source + dataset + subject + tradingDate + observedAt + sourceSequence` where
available, with a canonical payload hash as fallback. A later ingestion cannot
replace a record with an earlier `observedAt`. A source correction creates a new
revision linked to the superseded observation; derived snapshots are recomputed
and retain their exact input references.

REST reconciliation runs at startup, after stream recovery, and after a detected
sequence gap. Streaming consumers use bounded reconnect with jitter and do not
invent values while disconnected.

**Rationale**: Immutable revisions make corrections and out-of-order delivery
auditable and satisfy DATA-006, DATA-009, and FR-015.

**Alternatives considered**: Updating the latest row in place was rejected
because it destroys reproducibility and hides corrections.

**Risks/validation**: Captured, sanitized provider fixtures MUST cover duplicate,
out-of-order, sequence-gap, correction, invalid numeric, and missing-field cases.

## R-004 — Freshness and Degraded-State Policy

**Decision**: Freshness is evaluated per dataset against a versioned feed
policy and session state:

- `CURRENT`: active-session age is at most contracted delay plus 30 seconds.
- `DELAYED`: age exceeds the current threshold but is at most contracted delay
  plus 5 minutes.
- `STALE`: age exceeds the delayed ceiling during an active session.
- `PARTIAL`: the record is timely but required subjects or fields are missing,
  or breadth contains unclassified eligible securities.
- `UNAVAILABLE`: no valid accepted record exists for the requested section.

Quality and freshness are separate axes internally. The API exposes a single
most-actionable `dataStatus` using severity `UNAVAILABLE > PARTIAL > STALE >
DELAYED > CURRENT`, plus reason codes. After a venue is closed or on a
non-trading day, its latest completed accepted session remains valid with
session state `CLOSED` or `NON_TRADING_DAY`; wall-clock age alone does not make
it stale. A provider-declared delay is configuration, never assumed to be zero.

**Rationale**: This directly operationalizes DATA-005 and avoids presenting a
closed market as a broken live feed.

**Alternatives considered**: One global TTL and hard-coded “real-time” labels
were rejected because licenses and datasets can have different delays.

**Risks/validation**: Boundary tests at exactly `delay + 30s` and `delay + 5m`
are required. The configured delay must be confirmed during provider onboarding.

## R-005 — Calendar and Session Semantics

**Decision**: Interpret market-facing time in `Asia/Ho_Chi_Minh`, store instants
in UTC, and persist a versioned calendar by venue and effective period. Weekdays
are candidates only; official holiday, extraordinary-session, interruption,
and schedule notices override defaults. Model HOSE/HNX/UPCOM windows separately.
Provider session state is an input observation, while Finvera's accepted,
versioned calendar is business truth.

Initial normal-session configuration is based on current published rules: HNX
uses morning and afternoon continuous sessions, a closing auction, then PLO;
UPCOM uses morning and afternoon continuous sessions. KRX guidance retains a
HOSE opening auction and HOSE/HNX closing auction. Exact windows remain data,
not constants in regime or freshness code.

**Rationale**: Venue schedules and exceptional days can change. Versioning
preserves historical interpretation.

**Alternatives considered**: Treating every Monday-Friday as open and using one
shared venue schedule were rejected.

**Risks/validation**: Before production, import and review the official calendar
for the deployment year and define an operator procedure for exchange notices.

Sources: [SSI KRX equity-market FAQ](https://www.ssi.com.vn/en/individual-customer/krx-equity-market-faq),
[HNX transaction rules](https://www.ssi.com.vn/en/individual-customer/transaction-regulation-on-hnx),
[UPCOM transaction rules](https://www.ssi.com.vn/en/individual-customer/transaction-regulation-on-upcom).

## R-006 — Breadth Universe and Corporate Actions

**Decision**: Breadth v1 is the union of active common equities on HOSE, HNX,
and UPCOM for the trading session. Exclude ETFs, funds, warrants, bonds,
derivatives, and delisted/inactive instruments. Deduplicate by ISIN; if ISIN is
absent, use `venue + symbol`. VN30 membership is an attribute and never creates
another security.

Classify with the latest accepted same-session matched/close price against the
provider/exchange official reference price using unrounded decimals:

- greater than reference: advancing;
- less than reference: declining;
- equal to reference, including no trade where current/reference are valid:
  unchanged;
- missing/invalid price or reference, unresolved identity, or ineligible state:
  unclassified.

The provider's official same-day reference price is authoritative after
validation because it incorporates applicable ex-right adjustments. Finvera
does not recalculate it for breadth. Raw official closes/reference prices and
historically adjusted series are stored separately; adjusted history never
overwrites raw observations.

**Rationale**: This prevents duplicate VN30 counts and avoids comparing an
ex-right price to an unadjusted previous close.

**Alternatives considered**: Counting index constituents and venues separately,
and recomputing ex-right reference prices, were rejected due duplication and
corporate-action risk.

**Risks/validation**: Reconcile `advancing + declining + unchanged +
unclassified = eligible` for every fixture and validate ex-dividend examples.

Sources: [VSDC ex-right reference-price explanation](https://vsd.vn/en/ad/195688),
[SSI equities trading rules](https://www.ssi.com.vn/en/individual-customer/equities-trading).

## R-007 — Deterministic Market-Regime Methodology v1

**Decision**: Publish rule version `market-regime-v1`. It uses completed daily
data through the assessment's declared as-of point and five normalized 0–100
components:

| Component | Weight | Deterministic v1 score |
|---|---:|---|
| Trend | 35% | Mean of `close > SMA20`, `SMA20 > SMA50`, `SMA50 > SMA200`, and `SMA20(today) > SMA20(20 sessions ago)`, each mapped to 0 or 100. |
| Breadth | 25% | Mean of current A/D ratio score and percentage of eligible stocks above SMA50. A/D ratio score is `100 * advancing / (advancing + declining)`; denominator zero is missing. |
| Momentum | 15% | Mean of Wilder RSI14 mapped linearly from 30→0 to 70→100 (clamped) and 20-session VN-Index simple return mapped linearly from -10%→0 to +10%→100 (clamped). |
| Liquidity | 15% | VN-Index market matched value divided by its trailing 20-session median, mapped linearly from 0.5×→0 to 1.5×→100 (clamped). Put-through value is excluded. |
| Volatility | 10% | Inverse percentile rank of VN-Index 20-session population standard deviation of simple daily returns within the trailing 252 completed sessions: `100 - percentile`. Equal values use mid-rank. Square root and intermediate decimal precision are fixed by the rule implementation; binary floating point is forbidden. |

The weighted sum is rounded to the nearest integer with `HALF_UP` only after
all component calculations. Labels are: `BEAR` 0–29, `EARLY_BEAR` 30–44,
`SIDEWAYS` 45–55, `EARLY_BULL` 56–70, and `BULL` 71–100.

Confidence is an assessment-quality score, not a probability of future return:

`confidence = 0.50 * completeness + 0.30 * factorAgreement + 0.20 * boundaryDistance`

- `completeness`: sum of the original weights of usable components multiplied
  by 100. VN-Index current data and breadth are mandatory regardless of score.
- `factorAgreement`: assign each component `POSITIVE` when its normalized score
  is greater than 55, `NEGATIVE` when less than 45, otherwise `NEUTRAL`; sum
  effective weights by bucket and multiply the largest bucket mass by 100.
- `boundaryDistance`: use the unrounded weighted score and boundaries 29.5,
  44.5, 55.5, and 70.5. Within an interior band, divide distance to the nearest
  boundary by that band's maximum center-to-boundary distance (7.5, 5.5, or
  7.5); in BEAR/BULL divide distance to the single internal boundary by 29.5.
  Clamp the result to 0–100.

Publish a regime only when VN-Index, consolidated breadth, and at least four of
five components are usable, all required inputs are no worse than `DELAYED`,
and completeness is at least 80. Missing component weight is proportionally
renormalized; the assessment records this fact. Otherwise return a reason-coded
partial/unavailable state and no label, score, or confidence. Each component
becomes a supporting factor with direction, observations, thresholds, weight,
and contribution.

**Rationale**: The method is deterministic, explainable, compact, and covers
trend, participation, momentum, liquidity, and risk without an ML model. Sector
strength is deferred because sector analysis is outside Feature 1.

**Alternatives considered**:

- An LLM label was rejected as non-reproducible and outside scope.
- A trained regime model was rejected because no approved labeled dataset,
  evaluation target, or model-governance need exists.
- Confidence as prediction probability was rejected because v1 is descriptive,
  not calibrated to future outcomes.

**Risks/validation**: Thresholds are a v1 product hypothesis. Validate against
versioned historical fixtures for determinism, boundary behavior, obvious
pathologies, and label stability. Any formula/threshold change requires a new
rule version and replay comparison; do not mutate historical assessments.

## R-008 — Persistence, Precision, and Migrations

**Decision**: Use PostgreSQL and Flyway SQL migrations under
`src/main/resources/db/migration`. Add the Boot 4 Flyway starter and PostgreSQL
Flyway database module. Set Hibernate schema handling to validation outside
disposable tests. Use `BigDecimal`; detailed scales and units are declared in
`data-model.md`. Use Testcontainers PostgreSQL for migrations/repository tests,
not H2.

**Rationale**: Spring Boot 4.1 documents a dedicated Flyway starter and a
database-specific PostgreSQL module. Testing against PostgreSQL preserves
production decimal, timestamp, constraint, and SQL behavior.

**Alternatives considered**: Hibernate auto-DDL was rejected as destructive and
non-auditable. Liquibase is capable but adds no benefit for this initial
SQL-centric schema. H2 was rejected because it is not the production dialect.

**Risks/validation**: Pin a Testcontainers version compatible with Java 21 and
Boot 4 during task planning; run migration-from-empty and context-load tests.

Sources: [Spring Boot database initialization](https://docs.spring.io/spring-boot/4.1-SNAPSHOT/how-to/data-initialization.html),
[Testcontainers PostgreSQL module](https://java.testcontainers.org/modules/databases/postgres/),
[Testcontainers JUnit 5 integration](https://java.testcontainers.org/test_framework_integration/junit_5/).

## R-009 — Public API and UI Delivery

**Decision**: Add `GET /api/v1/market/overview`. It returns one assembled
snapshot with section-level `dataStatus`, timestamps, source labels, units,
reason codes, and nullable facts. Partial success is HTTP 200; no value is
fabricated. The endpoint is private: Spring permits only the configured owner
and returns the common 401/403 envelope to every other caller. Unexpected
server failure uses the common error envelope. The initial React/Vite page is a
client-side route that calls only Spring Boot through a typed API client.

Use Vitest + React Testing Library for formatter, presentation, and asynchronous
page-state components, and Playwright for the P1 browser flow.

**Rationale**: One coherent response prevents the UI from mixing incompatible
as-of snapshots. Vite keeps browser-facing tests independent of a frontend
server runtime.

**Alternatives considered**: One endpoint per card was rejected because it
increases cross-section consistency risk. Browser-to-provider calls violate the
system boundary.

**Risks/validation**: Contract tests must verify nullable/missing semantics,
degraded 200 responses, owner-only authorization, and denied public access.
Accessibility tests must verify that status and direction are not conveyed by
color alone.

Repository-wide frontend delivery decision:
[ADR-0006](../../docs/adr/0006-use-react-vite-for-private-web-client.md).

## R-010 — Resilience, Security, and Observability

**Decision**: Keep provider credentials server-side in environment/secret
storage. The adapter uses explicit connect/read/overall timeouts, retries only
idempotent REST reads with exponential backoff and jitter, and bounded stream
reconnect. Persist the last accepted facts; provider failure never converts
missing values to zero. The overview remains independent of Gemini, embeddings,
Qdrant, and `finvera-ai`.

TCBS API keys are secrets and tokens exist only in runtime memory. The owner
manually initiates renewal; an iOTP may pass transiently through the private,
owner-authorized Spring renewal boundary for immediate exchange, but is never
persisted, logged, generated, reused, or automatically replayed.
When a token expires, the adapter emits `PROVIDER_AUTH_REQUIRED`, pauses live
ingestion, and serves only last accepted facts with their true freshness.
Deployment has no public ingress and both network and Spring authorization
enforce the single-owner policy.

Emit structured metrics for provider connection, ingest lag, accepted/rejected
records, sequence gaps, last successful observation by dataset, calculation
failures, section status, and API latency. Logs include correlation ID, dataset,
subject, source, reason code, and timestamps—but never credentials, signed
payloads, full provider messages, or private user data.

**Rationale**: Operators must distinguish source, quality, calculation, and API
failures while preserving secrets.

**Alternatives considered**: Unlimited retry and logging raw payloads were
rejected due cascading failure and credential/licensing exposure.

**Risks/validation**: Exact timeout and rate budgets depend on the provider
contract and MUST be filled in configuration during onboarding, with safe
nonzero defaults and fault-injection tests.

## R-011 — Private Owner Authentication and Ingress

**Decision**: Expose Finvera only through Tailscale Serve/private tailnet
routing; never enable Funnel or directly publish frontend/backend ports. Spring
Security independently authenticates one configured local owner using a stable
UUID subject, normalized username, and an offline-generated `{bcrypt}` password
hash (cost 12) held in the deployment secret store.

Successful login rotates a server-side session. `FINVERA_SESSION` is Secure,
HttpOnly, SameSite=Strict, scoped to `/`, idle-expires after 30 minutes, and has
an absolute eight-hour lifetime. State-changing requests use a same-origin CSRF
token/header. Authentication failures use bounded backoff/rate limiting without
revealing whether the username or password was wrong.

The same-origin private route sends `/api/*` to Spring and other paths to the
React SPA. Tailscale is network reachability only: Spring does not trust an
unverified proxy identity header, and every protected API checks the local
owner session. No self-registration, password-reset email, invitation, API
token, or second account exists in v1.

**Rationale**: This is the smallest defense-in-depth design for one owner. It
keeps authorization in Spring, avoids an external identity provider and Redis,
and makes a leaked tailnet device insufficient without the application password.

**Alternatives considered**:

- Tailscale identity alone was rejected because network admission must not
  replace the Spring authorization boundary.
- Google/Auth0/Keycloak OIDC was deferred as unnecessary for one private owner.
- HTTP Basic and a long-lived bearer token were rejected due weaker session
  revocation and browser security ergonomics.
- Public reverse proxy plus a secret URL was rejected as public ingress and not
  authentication.

**Risks/validation**: Negative tests cover direct-port/public access, Funnel
disabled, session fixation, cookie flags, CSRF, logout/invalidation,
idle/absolute expiry, rate limiting, and secret/iOTP log redaction.
