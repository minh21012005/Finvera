# Implementation Plan: Market Overview

**Feature Directory**: `specs/001-market-overview`  
**Date**: 2026-08-17  
**Spec**: [spec.md](spec.md)  
**Status**: Approved — Fixture-first Implementation

## Summary

Deliver a trustworthy Vietnamese market overview as the first vertical slice of
Finvera: four benchmark indices, consolidated HOSE/HNX/UPCOM breadth, and a
versioned deterministic regime assessment with explicit session, freshness,
provenance, and degraded states.

The Spring Boot `market` module will ingest and normalize licensed read-only
market data, persist immutable accepted observations in PostgreSQL, calculate
breadth and `market-regime-v1`, and expose one coherent
`GET /api/v1/market/overview` response. A React SPA built with Vite will render
the response without recalculating authoritative financial facts. TCBS iFlash is the initial
read-only adapter behind a provider-neutral contract. A pinned Vnstock tool provides
offline historical bootstrap packages through a validated Spring import
boundary. Both are restricted to an owner-only private deployment and mandatory
fixture/license gates. The AI service, Gemini, embeddings, Qdrant, Redis,
and Kafka are not required.

## Technical Context

**Affected Projects**: `finvera-be`, `finvera-fe`; PostgreSQL runtime/test
configuration, TCBS connectivity, and an offline Vnstock bootstrap tool  
**Languages/Versions**: Java 21 + Spring Boot 4.1.0; TypeScript 5 + React
19.2.8 + Vite (pinned by T002); PostgreSQL version to be pinned by infrastructure task
without changing application semantics  
**Primary Dependencies**: Existing Spring MVC, Security, JPA, PostgreSQL;
proposed Spring Boot Flyway starter + Flyway PostgreSQL module; Testcontainers
PostgreSQL/JUnit for tests; proposed Vitest + React Testing Library and
Playwright for frontend tests. No Kafka usage despite its current scaffold
dependency; removal can be a baseline-cleanup task if no other approved feature
needs it.  
**Storage/State**: PostgreSQL is authoritative. No required Redis/Qdrant/Kafka
state; fixture data is test/development-only.  
**Interfaces**: Versioned owner-only REST API, internal outbound
`MarketDataProvider` contract, and versioned historical import-package schema; no
internal AI/event interface.  
**Testing/Evaluation**: Pure unit and numerical boundary/property tests;
sanitized provider contract fixtures; Flyway/JPA integration tests on
Testcontainers PostgreSQL; Spring MVC/security contract tests; Vitest component
tests; Playwright P1 E2E and accessibility checks.  
**Target Environment**: Modern browser; Java server with outbound TLS and,
for TCBS, owner-initiated iOTP renewal and private owner-only ingress; UTC host-safe operation with explicit
`Asia/Ho_Chi_Minh` market semantics.  
**Performance Goals**: NFR-001—95% of visits usable within 3 seconds;
NFR-002—99% of accepted updates visible within contracted delay +30 seconds.
The API design target is p95 <=500 ms from an already accepted snapshot,
leaving UI/network budget; calculations occur at ingestion/reconciliation, not
per request.  
**Availability/Degradation**: Last accepted facts remain visible with current,
delayed, stale, partial, or unavailable section status. AI outage has no effect.
Provider outage never creates zero/fabricated facts.  
**Scale/Scope**: Four indices; all active common equities on HOSE/HNX/UPCOM;
one consolidated breadth and one regime assessment per coherent revision;
shared read-mostly overview. No tick archive, chart delivery, HFT, or order flow.  
**Open Technical Unknowns**: Owner authentication/private ingress are resolved
by R-011. A sanitized Vnstock `4.0.6`/KBS probe passed representative
271-session history coverage on 2026-08-17. TCBS live field behavior and the
remaining Vnstock upstream-use, request-limit, adjustment/correction, and
full-universe checks still require gates. Production adapters/importers remain
blocked until those gates pass; deterministic fixture/domain work may proceed
only after `tasks.md`. Failure defers the affected journey or selects a
licensed provider through a research/contract/ADR amendment. Neither personal
source is permitted for public/multi-user delivery.

## Constitution Check

- [x] **Deterministic core**: Breadth and `market-regime-v1` have exact,
  versioned formulas, thresholds, input links, and reproducible rounding.
- [x] **Evidence and time**: Source, observation/effective/ingestion time,
  trading date, timezone, unit, adjustment status, corrections, and degraded
  behavior are modeled.
- [x] **Boundaries and ownership**: Browser → Spring boundary is intact;
  Spring `market` owns the feature; PostgreSQL is authoritative; AI/Redis/Qdrant/
  Kafka are absent. The module follows ADR-0007 layered packages; controllers
  do not access repositories/entities and services own transactions.
- [x] **Security and privacy**: Tailscale private ingress plus a Spring-managed
  local owner session, CSRF, rate limiting, secure cookie, and transient iOTP
  boundary are defined. Credentials remain server-only and raw private material
  is absent from owner-facing payloads and logs.
- [x] **Traceability**: All stories and requirement families map to design,
  contracts, and tests; no clarification marker remains.
- [x] **Risk-based tests**: Numerical boundaries, provider/API contracts,
  migrations, persistence, security, corrections, accessibility, and failure
  paths are covered.
- [x] **Resilience and observability**: Bounded timeouts/retries/reconnect,
  reconciliation, last-accepted fallback, quality states, metrics, and safe logs
  are defined.
- [x] **Modular simplicity**: One modular-monolith slice and one provider port;
  no new service, broker, datastore, ML model, or AI dependency.

**Pre-research result**: PASS for the fixture-first milestone. Provider roles,
boundaries, and explicit activation gates are defined; no live integration is
authorized by this result.
**Post-design result**: PASS for T001-T044 and fixture validation T049-T056
under the approved, narrow Complexity Tracking exception. TCBS live task T046
and Vnstock import task T048 remain blocked by T045 and T047 respectively.

## System Context and Boundaries

### Affected User and System Flows

```text
private TCBS iFlash / deterministic fixtures / offline Vnstock package
        |
        v
Spring market.provider integration
        |
        v
normalize -> validate -> immutable PostgreSQL observations
        |                         |
        |                         +-> breadth-v1
        |                         +-> market-regime-v1
        v
market service overview assembler
        |
        v
GET /api/v1/market/overview
        |
        v
React SPA route -> accessible index/breadth/regime/degraded UI

finvera-ai / Gemini / embeddings / Qdrant / Kafka: no path in this feature
```

### Ownership

| Concern | Owning project/module | Reason |
|---|---|---|
| Provider authentication and DTO mapping | `finvera-be/.../market/provider` | Keeps source details and secrets behind a replaceable provider contract. |
| Historical package export | `tools/market-data/vnstock-export` | Offline owner tool; emits canonical packages and has no database/network-serving authority. |
| Historical package validation/import | `finvera-be/.../market/service` | Spring validates provenance/schema and owns the atomic PostgreSQL write. |
| Market validation, time, breadth, regime rules | `finvera-be/.../market/domain` | Pure deterministic business behavior. |
| Ingestion/reconciliation/overview use cases | `finvera-be/.../market/service` | Transaction and orchestration boundary. |
| Accepted observations and derived assessments | PostgreSQL via `market/repository` and `market/entity` | Auditable transactional source of truth. |
| Public REST mapping/security | `finvera-be/.../market/controller` and `market/dto` | Spring remains public/API access boundary. |
| Market presentation and formatting | `finvera-fe/src/features/market-overview` | UI consumes contract and does not calculate financial truth. |
| AI/RAG | Not affected | Feature is deterministic and remains usable during AI outage. |

### Interface Changes

| Interface | Change | Version/Compatibility | Contract Artifact |
|---|---|---|---|
| Public REST | Add coherent market overview GET endpoint | Additive `/api/v1`; response contract 1.0 | [market-overview.openapi.yaml](contracts/market-overview.openapi.yaml) |
| Private owner access | Add local session, CSRF, login/logout/status, and TCBS renewal | Private contract 1.0; replaces bearer assumption for Feature 001 | [private-owner-access.openapi.yaml](contracts/private-owner-access.openapi.yaml) |
| Market provider | Add internal read-only provider contract and TCBS integration | Internal `tcbs-iflash-market-private-v1`; provider replaceable | [tcbs-iflash-adapter.md](contracts/tcbs-iflash-adapter.md) |
| Historical bootstrap | Add operator-only canonical package import | `vnstock-history-private-bootstrap-v1`; no runtime Python service | [vnstock-historical-bootstrap.md](contracts/vnstock-historical-bootstrap.md) |
| Database | Add market/calendar/observation/derived tables | Forward Flyway migrations; no existing business data | [data-model.md](data-model.md) |

## Phase 0: Research

Research is complete in [research.md](research.md). Decisions include:

- TCBS iFlash owner-only live target and capability gate;
- pinned Vnstock offline historical bootstrap, upstream-license/fixture gate,
  and cross-source reconciliation; commercial provider migration before public delivery;
- immutable ingestion/correction ordering and source reconciliation;
- versioned freshness, calendar/session, breadth-universe, and corporate-action
  policies;
- exact deterministic `market-regime-v1` formulas and publishability threshold;
- PostgreSQL/Flyway/Testcontainers and React/Vite test stacks;
- resilience, secrets, monitoring, and public API degraded semantics.

## Phase 1: Design and Contracts

### Data Model

[data-model.md](data-model.md) defines reference/calendar entities, immutable
provider observations, exact breadth/regime input links, precision and time
semantics, indexes, correction chains, retention gates, and rebuild/migration
behavior.

Key design constraints:

- unrounded `numeric` values drive direction and classification;
- all accepted facts retain source and observation/ingestion/effective context;
- correction creates a revision and recomputation, never in-place mutation;
- one breadth security identity per ISIN or venue+symbol fallback;
- regime label/score/confidence are all withheld if publishability fails.

### Contracts

- [market-overview.openapi.yaml](contracts/market-overview.openapi.yaml) defines
  authentication, coherent HTTP 200 degraded responses, nullable exact decimals,
  section status, timestamps, source labels, warnings, and common errors.
- [private-owner-access.openapi.yaml](contracts/private-owner-access.openapi.yaml)
  defines local-owner session login/logout/status, CSRF, secure-cookie behavior,
  and the transient TCBS iOTP renewal boundary.
- [tcbs-iflash-adapter.md](contracts/tcbs-iflash-adapter.md) defines the private
  provider mapping, acceptance checks, timeouts/retry/reconciliation, fixture
  suite, secret handling, and gate that blocks unlicensed live integration.
- [vnstock-historical-bootstrap.md](contracts/vnstock-historical-bootstrap.md)
  defines the offline canonical package, validation, license/fixture gate, and
  TCBS reconciliation behavior without adding a Python runtime service.

### Security, Privacy, and AI Safety

- Configure one immutable owner UUID, normalized username, and offline-generated
  `{bcrypt}` password hash for this private deployment; deny all
  other identities, registration, invitations, sharing, and public ingress.
- Route only through Tailscale Serve with Funnel/direct public ports disabled.
  Enforce owner access in Spring Security and the private network boundary. UI
  visibility is not authorization.
- Keep the TCBS API key and runtime-only access token in server-side
  secret/configuration facilities only. Never store or automate iOTP.
- Rotate the server session on login; use a Secure/HttpOnly/SameSite=Strict
  cookie, 30-minute idle and eight-hour absolute expiry, CSRF on state changes,
  logout invalidation, and bounded login backoff/rate limiting.
- Permit outbound calls only to configured TLS provider hosts. Validate size,
  schema, identity, timestamps, numeric bounds, and source ordering before data
  enters the accepted domain.
- Do not retain full raw provider payloads by default; any retention requires
  licensing approval and redaction. Public provenance is an allowlisted source
  label/dataset, not raw provider metadata.
- No prompt, LLM, embedding, retrieval, or untrusted document path exists.
  Regime copy must state quantitative decision support, not advice/guarantee.

### Observability and Operations

Structured signals:

- counters: received, accepted, duplicate, rejected, corrected records by
  source/dataset/reason; reconnects, reconciliation runs/failures, sequence gaps;
- gauges: last provider success, last accepted observation, ingest lag,
  unclassified breadth count/ratio, latest section status;
- timers: provider REST/token calls, normalization/calculation, DB transaction,
  overview API latency;
- health: provider auth/connectivity, data freshness by dataset, database,
  calculation pipeline; AI health is irrelevant to this endpoint.

Alert candidates: no accepted active-session update past delayed ceiling;
missing venue/index; repeated authentication/sequence failures; breadth
unclassified above policy threshold; calculation/API error-rate or latency SLO
breach.

Every request/ingest path carries a correlation/ingestion ID. Logs allow source,
dataset, subject, reason code, and timestamps; they prohibit credentials, tokens,
signed material, full provider payloads, and user-private data. Add an operator
runbook for provider outage, calendar override, correction replay, and rollback.

### Test and Evaluation Strategy

| Requirement IDs | Test level | Fixture/dataset | Expected evidence |
|---|---|---|---|
| FR-001–FR-006, DATA-001–DATA-007 | Domain unit + provider/API contract + DB integration + Playwright P1 | Four-index active, closed, delayed, stale, missing, correction, out-of-order fixtures | Exact facts/units/times; stable order; no zero fabrication; correct status and correction revision. |
| FR-007–FR-009, DATA-004, DATA-008 | Unit/property + provider contract + DB integration + component/E2E | Deduplicated three-venue universe including VN30, excluded types, ex-right, missing reference | Reconciliation invariant; one identity once; unrounded direction; partial/unclassified visible. |
| FR-010–FR-015, DATA-009 | Pure numerical boundary/property + replay fixture + API/UI | Versioned bullish/bearish/boundary/missing/corrected histories | 100% deterministic replay; exact label bands/rounding; withheld insufficient result; traceable factors and disclaimer. |
| NFR-001–NFR-003 | Integration/load-smoke + timing instrumentation | Representative accepted overview/read workload and ingest fixture | API p95 design target <=500 ms; accepted update path meets delay +30 s; coherent response revision. |
| NFR-004 | Integration/E2E fault test | All AI dependencies disabled | Market overview fully independent and usable. |
| NFR-005 | Component + Playwright accessibility assertions/manual review | All direction/status/regime states | Text/icon/semantic state independent of color; accessible labels preserve exact meaning. |
| NFR-006 | Fault-injection + telemetry assertions | Auth, timeout, sequence gap, invalid, calculation, API failure | Distinct safe metrics/logs with no credentials/private/raw payload. |
| NFR-007, SEC-001â€“SEC-006 | Security integration + negative API/adapter/deployment tests | Owner/non-owner, public ingress, session/CSRF, iOTP renewal, forbidden TCBS operation, secret-log capture | Only owner succeeds; no public delivery; secure session/CSRF enforced; iOTP is transient; forbidden calls and leakage are impossible. |
| DATA-010 | Import/provider contract + reconciliation property tests | Matching and conflicting completed-session TCBS/Vnstock fixtures | Both provenances preserved; material conflict yields `SOURCE_CONFLICT` and withholds regime. |

Additional boundaries include exact freshness thresholds, regime 29/30/44/45/
55/56/70/71 bands, score clamping, decimal rounding, zero A/D denominator,
missing one/five components, exactly 80% completeness, duplicate identity, and
venue-calendar transitions.

### Rollout, Migration, and Rollback

1. Approve provider onboarding/licensing or switch the adapter decision before
   starting live integration; deterministic fixture work may proceed meanwhile.
2. Add PostgreSQL/Flyway test baseline and apply forward market migrations.
3. Seed/version supported indices, calendar/session policy, breadth policy, and
   regime rule metadata.
4. Deploy backend with ingestion disabled; validate migrations, fixture adapter,
   calculations, API contract, metrics, and security.
5. Import an approved Vnstock canonical package containing at least 271
   completed sessions, then enable private TCBS ingestion in non-production and
   reconcile overlapping completed-session facts against approved fixtures.
6. Enable production ingestion with overview hidden behind a server-side feature
   flag until four indices, breadth coverage, freshness, and historical factors
   pass acceptance.
7. Deploy frontend, enable read traffic, and monitor freshness/error/latency and
   count reconciliation.

Rollback disables the UI flag and provider ingestion first, preserving accepted
PostgreSQL observations. Roll back application code only to a version compatible
with the applied forward migration; do not automatically drop tables. Re-enable
the last verified rule version/read path after diagnosis. A bad source correction
is superseded by a reviewed new revision, not deleted. Redis, if later added, is
discarded/rebuilt.

### Quickstart Acceptance

[quickstart.md](quickstart.md) defines planned prerequisites and commands, P1–P3
happy paths, freshness boundaries, partial/unavailable/correction/provider and
AI outage paths, authorization, and expected operational evidence. It clearly
marks commands that do not exist until implementation.

## Project Structure

### Feature Documentation

```text
specs/001-market-overview/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── market-overview.openapi.yaml
│   └── tcbs-iflash-adapter.md
├── checklists/
│   └── requirements.md
└── tasks.md                         # created only in the next SDD phase
```

### Source Code Affected

```text
finvera-be/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/minhnb/finvera_be/market/
    │   │   ├── domain/              # decimals, time, breadth, regime rules
    │   │   ├── application/         # ingestion/reconcile/overview use cases
    │   │   ├── api/                 # /api/v1 contract DTO/controller mapping
    │   │   └── infrastructure/
    │   │       ├── persistence/     # JPA adapters only
    │   │       └── provider/        # fixture and TCBS iFlash adapters
    │   └── resources/
    │       ├── application*.yaml
    │       └── db/migration/        # forward Flyway SQL
    └── test/
        ├── java/com/minhnb/finvera_be/market/
        └── resources/fixtures/market/

finvera-fe/
├── package.json
├── vite.config.ts
├── vitest.config.ts
├── playwright.config.ts
├── src/
│   ├── main.tsx                     # Browser entry
│   ├── app.tsx                      # Client-side route shell
│   └── features/market-overview/
│   ├── api/                         # explicit API DTO/client mapping
│   ├── components/                  # cards, breadth, regime, status
│   ├── format/                      # locale/unit formatting only
│   └── market-overview-page.tsx     # loading/error/content states
└── tests/
    ├── market-overview/
    └── e2e/market-overview.spec.ts
```

**Structure Decision**: This is one domain-oriented Spring module and one
frontend feature. Domain logic remains framework-independent; JPA/provider/web
types stay in adapters. The UI only formats contract data. No source changes are
planned in `finvera-ai`. The only Python addition is an isolated offline tool at
`tools/market-data/vnstock-export/`; it is not deployed and cannot write the
database directly.

## Traceability Summary

| Requirement IDs | Design/Contract | Test/Evaluation | Planned Task Group |
|---|---|---|---|
| FR-001–FR-006, DATA-001–DATA-007 | Index/session/observation model; public API | Index provider/API/integration/component/P1 E2E | Foundation → US1 |
| FR-007–FR-009, DATA-008 | Instrument, equity observation, breadth model; provider contract | Breadth property/reconciliation/provider/UI tests | Foundation → US2 |
| FR-010–FR-015, DATA-009 | Regime assessment/input/factor model and R-007 methodology | Versioned numerical replay/boundary/API/UI tests | Foundation → US3 |
| NFR-001–NFR-003 | Precomputed accepted read path, coherent response | Timing/load-smoke and consistency assertions | Cross-cutting performance |
| NFR-004 | No AI path/dependency | AI-disabled integration/E2E | US1 acceptance |
| NFR-005 | Status/direction contract and accessible UI structure | Component/Playwright/manual accessibility | Each user story |
| NFR-006 | Observability and failure taxonomy | Fault injection and telemetry assertions | Operations/hardening |
| NFR-007, SEC-001â€“SEC-006 | Private owner policy, session/CSRF, renewal boundary, adapter allowlist | Negative security/deployment/secret tests | Security foundation â†’ hardening |
| DATA-010 | Historical import batch and source reconciliation | Package contract/conflict/property tests | Foundation â†’ US3 |

## Complexity Tracking

| Violation/Addition | Why Required Now | Simpler Alternative Rejected | Approval/ADR | Removal or Review Trigger |
|---|---|---|---|---|
| Fixture-first milestone while live provider gates remain open | The owner explicitly directed implementation to continue while TCBS provisioning and Vnstock rights are resolved separately. T001-T044 and T049-T056 can be validated without representing fixtures as live data. | Blocking deterministic domain, security, persistence, API, and UI work on unrelated external onboarding | Owner approval, 2026-08-17; provider decisions remain governed by ADR-0003/0004 | Exception ends at fixture-mode validation; T045-T048 and any live deployment remain blocked until their individual gates pass. |

The provider port is the minimum seam needed to avoid coupling authoritative
domain logic to one external schema. Flyway and Testcontainers address required
migration/reproducibility risks. Kafka, Redis, AI, and a new service are not
introduced.
