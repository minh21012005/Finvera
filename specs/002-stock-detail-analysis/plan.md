# Implementation Plan: Stock Detail and Analysis

**Feature Directory**: `specs/002-stock-detail-analysis`
**Date**: 2026-08-18
**Spec**: [spec.md](spec.md)
**Status**: Implemented (Fixture Baseline); automated verification passing. Phases 1 through 5 (US1, US2, US3) and the Final Phase (cross-cutting validation) are complete for everything an automated agent can verify. Provider activation gates G-01 to G-04 and Feature 001's T051 (Tailscale Serve-only ingress) remain open release blockers, and **T073/SC-001 (the owner usability trial) also remains open** — `validation/usability.md`'s evidence table was never filled in despite the task previously being marked done; it requires a live human trial and cannot be completed by an agent. A 2026-08-19 review pass found and fixed several Phase 5 defects that had shipped without being caught by any test: `ValuationV1`'s `confidence` used `double` arithmetic instead of decimal (U-1), `DIVIDEND_YIELD` conflated a missing price with a zero price (DATA-007), and — most materially — `FundamentalReportService`/`ValuationService` never persisted `fundamental_summary`/`valuation_assessment` at all (the repositories existed, unused), the own-history valuation basis used today's EPS applied retroactively to 750 days of price instead of each session's own effective fundamentals, price freshness and DATA-010 source-conflict detection were unwired for valuation, and `StockIngestionService` silently dropped every ingested fundamental-report metric value (the header row saved, the figures never did). See `tasks.md` T047, T049, T051, T052, T068, T073 for full evidence; two new integration test files (`FundamentalReportServiceTests`, `ValuationServiceTests`) and additions to `ValuationV1Tests`/`StockIngestionServiceTests` now cover what was previously untested. Full backend suite passes after remediation (`.\mvnw.cmd test`, 0 failures/errors); frontend suite was not re-run since no frontend file or API contract changed.

## Summary

Deliver the second vertical slice of Finvera: one supported Vietnamese stock's
current price and session context, its daily price history, its core technical
condition, its fundamental health, and a transparent valuation classification —
all carrying source, time, and freshness exactly as `001-market-overview`
established.

A new Spring Boot `stock` module ingests through the same accepted-observation
boundary Feature 001 built, persists immutable daily bars, fundamental reports,
and derived results in PostgreSQL, and exposes five independently retrievable
sections under `/api/v1/stocks`. The React SPA renders them without recalculating
any authoritative financial value. No AI service, LLM, embedding, vector store,
cache, or broker is required or introduced.

Two deterministic rule sets are the heart of this feature and are specified
normatively before any code:
[technical-indicators-v1](contracts/technical-indicators-v1.md) and
[valuation-v1](contracts/valuation-v1.md).

## Technical Context

**Affected projects**: `finvera-be` (new `stock` module), `finvera-fe` (new
`stock-detail` feature). No change to `finvera-ai`. No new deployable unit.

**Languages and versions**: unchanged — Java 21 with Spring Boot 4.1.x,
TypeScript 5 with React 19 and Vite, PostgreSQL 17.

**Primary dependencies**: unchanged. Spring MVC, Security, JPA, Flyway,
Testcontainers, Vitest, React Testing Library, Playwright. **No charting library
decision is made here**; the chart is served as data and the rendering choice is
a frontend task-level decision that must not introduce a runtime that recomputes
financial values.

**Storage and state**: PostgreSQL is authoritative. Forward-only Flyway
migrations from `V003`. No Redis, Qdrant, or Kafka state.

**Interfaces**: versioned owner-only REST under `/api/v1/stocks`; internal
outbound provider ports; no AI or event interface.

**Testing**: pure numerical unit and property tests for both rule sets, golden
vectors, boundary fixtures, Flyway and JPA integration tests on Testcontainers,
Spring MVC and security contract tests, Vitest component tests, Playwright P1 to
P3 end-to-end and accessibility checks.

**Performance goals**: NFR-001 — 95% of stock detail visits usable within 3
seconds. NFR-002 — 99% of accepted updates visible within the contracted delay
plus 30 seconds. Design target is p95 under 500 ms per section from precomputed
accepted results; indicators and valuations are computed at ingestion and
recalculation time, never per request.

**Availability and degradation**: each section degrades independently. Last
accepted facts stay visible with a truthful freshness state. AI outage has no
effect. Provider outage never produces a zero or a fabricated value.

**Scale and scope**: one stock at a time, daily timeframe only, bounded chart
window up to two years, nine indicators, five valuation metrics, one
classification. No screener, no peer comparison, no scoring, no intraday, weekly,
or monthly timeframe, no pattern detection.

**Open technical unknowns**: four provider evidence gates, recorded as G-01 to
G-04 in [research.md](research.md) R-012. Deterministic domain, persistence,
contract, and UI work proceeds against fixtures; live adapters stay blocked and
disabled by default configuration until each gate closes with owner-accepted
evidence.

## Constitution Check

### Pre-research gate

- [x] **I. Deterministic finance core** — two versioned rule sets are specified
  before code, with exact formulas, windows, precision, and rounding.
- [x] **II. Evidence, provenance, temporal truth** — every fact and every derived
  result carries source, observation time, trading date or reporting period, and
  freshness.
- [x] **III. Explicit boundaries** — browser to Spring only; Spring owns provider
  access; PostgreSQL authoritative; `finvera-ai` untouched.
- [x] **IV. Security, privacy, responsible decision support** — no new auth path,
  no new external host, read-only ports, mandatory non-advice framing.
- [x] **V. Specification and traceability before code** — spec, research, data
  model, and four contracts exist; no unresolved clarification marker remains.
- [x] **VI. Risk-based testing** — numerical, boundary, provenance, degradation,
  security, and accessibility risks each have a named test level.
- [x] **VII. Resilience and observability** — per-dataset freshness, bounded
  timeouts, reconciliation, failure taxonomy defined below.
- [x] **VIII. Modular simplicity** — one new module, no new service, datastore,
  broker, or AI dependency.

**Result**: PASS for fixture-first design and implementation. No live provider
integration is authorized by this result.

### Post-design gate

- [x] Data model preserves immutability, revision chains, and the
  missing/zero/not-applicable distinction at the constraint level.
- [x] The API contract expresses degraded states as HTTP 200 with section status,
  never as a fabricated value.
- [x] Publishability is enforced in both the domain and a database check
  constraint, so a half-published valuation is unrepresentable.
- [x] Every requirement in `spec.md` maps to a design artifact and a test level
  in the traceability table below.
- [x] Decimals are transported as strings so declared precision survives the wire.

**Result**: PASS for fixture-scoped implementation. Tasks that enable a live
adapter remain blocked on their gate.

## System Context and Boundaries

```text
TCBS iFlash (quotes)        Vnstock offline package (bars, fundamentals)
        |                                    |
        +----------------+-------------------+
                         v
          finvera-be / stock / provider  (read-only ports, allowlisted)
                         |
                         v
        normalize -> validate -> immutable PostgreSQL facts
                         |
          +--------------+---------------+
          v                              v
 technical-indicators-v1          valuation-v1
          |                              |
          +--------------+---------------+
                         v
        stock service section assemblers (coherence key)
                         |
                         v
   GET /api/v1/stocks/{symbol}[/chart|/technical|/fundamentals|/valuation]
                         |
                         v
        React SPA stock-detail route (formats only, calculates nothing)

finvera-ai / Gemini / embeddings / Qdrant / Redis / Kafka: no path in this feature
```

### Ownership

| Concern | Owner | Reason |
|---|---|---|
| Provider authentication and DTO mapping | `finvera-be/.../stock/provider` | Keeps provider detail and secrets behind a replaceable port. |
| Indicator and valuation rules | `finvera-be/.../stock/domain` | Pure, deterministic, framework-independent. |
| Ingestion, recalculation, section assembly | `finvera-be/.../stock/service` | Transaction and orchestration boundary. |
| Accepted facts and derived results | PostgreSQL via `stock/repository` and `stock/entity` | Auditable transactional truth. |
| REST mapping and security | `finvera-be/.../stock/controller` and `stock/dto` | Spring remains the sole public boundary. |
| Presentation and formatting | `finvera-fe/src/features/stock-detail` | UI formats contract data; it never recalculates a financial value. |
| Instrument identity, calendar, session | Feature 001 `market` module | Reused, not duplicated. |
| AI and RAG | Not affected | Feature is deterministic and fully usable during AI outage. |

**Cross-module rule**: the `stock` module reads Feature 001 reference data
through a published service interface on the `market` module, never by reaching
into its repositories or entities. This keeps ADR-0007 layering intact and stops
the modular monolith from degrading into a shared-table free-for-all.

### Interface Changes

| Interface | Change | Version and compatibility | Artifact |
|---|---|---|---|
| Public REST | Add six owner-only stock endpoints | Additive under `/api/v1`; response contract 1.0 | [stock-detail.openapi.yaml](contracts/stock-detail.openapi.yaml) |
| Provider ports | Add reference, quote, history, corporate-action, fundamentals ports | Internal `stock-data-private-v1` | [stock-data-provider.md](contracts/stock-data-provider.md) |
| TCBS adapter | Extend index subjects to instrument subjects | Amendment to the Feature 001 adapter contract; same provider and license | Feature 001 `tcbs-iflash-adapter.md` |
| Calculation rules | Add two versioned rule sets | `technical-indicators-v1`, `valuation-v1` | [technical-indicators-v1.md](contracts/technical-indicators-v1.md), [valuation-v1.md](contracts/valuation-v1.md) |
| Database | Add equity reference, bars, corporate actions, fundamentals, derived results | Forward Flyway from `V003`; no existing table altered | [data-model.md](data-model.md) |

No existing endpoint, schema, or contract is broken. Feature 001 continues to
pass unchanged.

## Phase 0: Research

Complete in [research.md](research.md). Decisions cover the reused quote source,
the separate daily-bar table, the fixed 250-session evaluation window, the
no-splicing adjustment rule, the narrow fundamentals metric model, persisted
derived fundamentals, both rule sets, section-level endpoints with a coherence
key, per-dataset freshness, inherited access control, four open provider gates,
and the fixture strategy.

## Phase 1: Design and Contracts

### Data model

[data-model.md](data-model.md) defines fifteen additive tables across equity
reference, accepted price history, fundamentals, and derived analysis, with
precision, units, time semantics, revision chains, indexes, retention, and
migration behaviour.

Load-bearing design constraints:

- unrounded values drive every direction and classification decision;
- an accepted fact is never mutated; a correction is a new linked revision;
- missing, zero, and not-applicable are distinguishable at the row level;
- a valuation label, score, displayed score, and confidence are present together
  or absent together, enforced by a check constraint;
- every derived result records the exact input set needed to recompute it.

### Contracts

- **stock-detail.openapi.yaml** — six endpoints, section-level status, nullable
  exact decimals as strings, coherence key, reason codes, problem-details errors.
- **technical-indicators-v1.md** — nine indicators with exact formulas, minimum
  bar counts, precision, rounding, reason codes, and required test vectors.
- **valuation-v1.md** — five metrics, two comparison bases, the score, the bands,
  the publishability floor, confidence, disclosure, and required test vectors.
- **stock-data-provider.md** — five read-only ports, eleven acceptance checks,
  correction and reconciliation behaviour, secret rules, and the four gates.

### Security, privacy, and AI safety

- Reuse the Feature 001 owner session, CSRF, and secure cookie exactly. No new
  authentication path is introduced (SEC-004).
- Add no external host. Provider credentials stay in the server-side secret
  store and never reach a response, a log, a fixture, or the client bundle.
- Provider ports are read-only by construction. No trading, account, cash, or
  order operation is reachable (SEC-003).
- Acceptance check A-11 rejects any record carrying a credential-shaped field, so
  a provider mistake cannot become a readable database column.
- No prompt, model, embedding, retrieval, or untrusted-document path exists in
  this feature. Indicator and valuation copy must state quantitative decision
  support, never advice, forecast, or guarantee (FR-015).

### Observability and operations

**Counters**: records received, accepted, duplicate, rejected, corrected, by
source, dataset, and reason; indicator and valuation calculations attempted,
published, withheld, by reason; reconciliation conflicts.

**Gauges**: last accepted bar date per instrument; last accepted reporting period
age; count of instruments below each indicator's minimum bar count; count of
sectors below the constituent floor.

**Timers**: provider call, normalization, indicator calculation, valuation
calculation, section assembly, endpoint latency per section.

**Failure taxonomy** — these six must be distinguishable in monitoring without
exposing credentials or private data (NFR-006): provider unavailable, provider
auth expired, invalid or rejected record, insufficient accepted history,
calculation failure, delivery failure.

Every request and ingestion path carries a correlation identifier. Logs may carry
source, dataset, symbol, period label, reason code, and timings; they may not
carry credentials, tokens, provider payloads, or private data.

### Test and evaluation strategy

| Requirement IDs | Test level | Fixtures | Expected evidence |
|---|---|---|---|
| FR-001, FR-002, FR-013, FR-016; DATA-001 to DATA-003, DATA-005 | Unit, API contract, DB integration, Playwright P1 | Complete, delayed, stale, closed-market, missing-reference, unknown-symbol | Exact facts with units and times; correct freshness; 404 without fabrication; change unavailable rather than inferred |
| FR-003, DATA-008 | Property, API, component | Split inside window; missing adjustment basis | One adjustment status per series; never spliced |
| FR-005, FR-006, FR-011; DATA-004, DATA-009 | Pure numerical unit, property, replay | Golden vectors; 19/20, 49/50, 199/200, 249/250 bars; flat and monotonic series | Exact values to scale 12; thresholds exact; replay reproduces stored decimals |
| FR-007, FR-014; DATA-006, DATA-007 | Unit, DB integration, API | Complete report, restated report, duplicate, out-of-order, not-applicable line items | Correct period identity; corrections create revisions; three-state applicability preserved |
| FR-008 to FR-010; DATA-004, DATA-009 | Boundary, property, API | Band edges 35.4/35.5/64.4/64.5; negative earnings; thin sector; stale fundamentals | Exact banding; withheld together; disclosure names the basis used |
| FR-012 | API, E2E | Each section failing alone | Remaining sections fully usable |
| FR-015 | Component, acceptance review | All published states | Non-advice framing present in technical and valuation sections |
| DATA-010 | Property, integration | Matching and conflicting cross-source bars | Both provenances retained; conflict withholds dependents |
| NFR-001 to NFR-003 | Integration, load smoke, timing | Representative accepted workload | p95 within target; coherence key detects mixed snapshots |
| NFR-004 | Integration, E2E fault | All AI dependencies disabled | Every journey usable, no AI error surfaced |
| NFR-005 | Component, Playwright, manual | All directional and status states | Non-colour indicator for every state |
| NFR-006 | Fault injection, telemetry assertions | Each of the six failure classes | Distinct safe signals, no credential or private data |
| NFR-007 | Contract, degraded-state | Expired provider auth | `PROVIDER_AUTH_REQUIRED`; history readable; nothing claims live |
| SEC-001 to SEC-004 | Security integration, negative adapter, build scan | Owner, non-owner, unauthenticated, missing CSRF, injected token field | Only owner succeeds; no leakage anywhere; forbidden operations unreachable |

### Rollout, migration, and rollback

1. Apply Flyway `V003` and seed the fundamental metric catalog and sector
   reference data.
2. Deploy the backend with every live flag off. Validate migrations, fixture
   adapters, both rule sets, the API contract, metrics, and security.
3. Deploy the frontend behind a server-side feature flag; validate P1 to P3
   against fixtures on loopback.
4. Close gates G-01 to G-04 individually with owner-accepted evidence. Each gate
   closure enables exactly one flag, in non-production first.
5. Reconcile overlapping completed-session facts between TCBS and Vnstock before
   enabling any dependent derived result.
6. Enable owner-visible delivery only after the quickstart acceptance path passes
   and T051 is satisfied.

**Rollback** disables the UI flag and provider flags first, preserving accepted
PostgreSQL facts. Application code rolls back only to a version compatible with
the applied forward migration; tables are not dropped automatically. A bad source
correction is superseded by a reviewed new revision, never deleted. A rule defect
produces a `v2` rule version and parallel results rather than a rewrite of `v1`
history.

### Quickstart acceptance

[quickstart.md](quickstart.md) defines prerequisites, configuration, commands,
the P1 to P3 acceptance paths, degraded and failure paths, authorization and
secret checks, accessibility checks, and the release gates that remain open. It
marks clearly which commands do not exist until implementation.

## Project Structure

### Feature documentation

```text
specs/002-stock-detail-analysis/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── stock-detail.openapi.yaml
│   ├── technical-indicators-v1.md
│   ├── valuation-v1.md
│   └── stock-data-provider.md
├── checklists/
│   └── requirements.md
└── tasks.md                         # generated; implementation not started
```

### Source code affected

```text
finvera-be/src/
├── main/
│   ├── java/com/minhnb/finvera_be/stock/
│   │   ├── domain/          # indicator and valuation rules, freshness, decimals
│   │   ├── service/         # ingestion, recalculation, section assembly
│   │   ├── controller/      # /api/v1/stocks mapping
│   │   ├── dto/             # contract types only
│   │   ├── entity/          # JPA mappings for the V003 tables
│   │   ├── repository/      # Spring Data interfaces
│   │   ├── provider/        # fixture adapters now; live adapters behind gates
│   │   └── config/          # feature flags and freshness configuration
│   └── resources/
│       ├── application.yaml            # new finvera.stock.* keys
│       ├── db/migration/V003__*.sql    # forward-only
│       └── fixtures/stock/
└── test/
    ├── java/com/minhnb/finvera_be/stock/
    └── resources/fixtures/stock/

finvera-fe/src/features/stock-detail/
├── api/          # explicit DTO mapping against the OpenAPI contract
├── components/   # overview, chart, technical, fundamentals, valuation sections
├── format/       # locale, unit, and precision formatting only
└── stock-detail-page.tsx
```

**Structure decision**: one new domain-oriented Spring module following the same
ADR-0007 layering Feature 001 uses, and one new frontend feature folder. Domain
logic stays framework-independent; JPA, provider, and web types stay in their
adapter layers. The frontend formats contract data and calculates nothing
authoritative. No Python is added, and `finvera-ai` is untouched.

## Traceability Summary

| Requirement IDs | Design artifact | Test evidence | Planned task group |
|---|---|---|---|
| FR-001, FR-002, FR-013, FR-016 | `equity_profile`, overview read model, `/stocks`, `/stocks/{symbol}` | Unit, API, P1 E2E | Foundation then US1 |
| FR-003, DATA-008 | `equity_daily_bar`, `corporate_action`, chart endpoint, research R-004 | Property, API, component | Foundation then US1 |
| FR-004, FR-013, DATA-005 | Research R-010 freshness table, `SectionMeta` | Unit boundary, component | Foundation |
| FR-005, FR-006, FR-011 | `technical-indicators-v1`, `technical_indicator_result` and values | Golden vectors, thresholds, replay | US2 |
| FR-007, FR-014 | `fundamental_report`, metrics, summary, restatement chain | DB integration, API | US3 |
| FR-008, FR-009, FR-010 | `valuation-v1`, `valuation_assessment` and metrics | Boundary, withheld, disclosure | US3 |
| FR-012 | Section-level endpoints, research R-009 | API, E2E per-section failure | Cross-cutting |
| FR-015 | `disclaimerCode` in both sections | Component, acceptance review | US2 and US3 |
| DATA-001 to DATA-004, DATA-007, DATA-009 | Data model rules, applicability enums, input hashes | Unit, DB constraint tests | Foundation |
| DATA-006, DATA-010 | Provider contract corrections and reconciliation | Property, integration | Foundation |
| NFR-001 to NFR-003 | Precomputed results, coherence key | Timing, consistency assertions | Cross-cutting performance |
| NFR-004 | No AI path exists | AI-disabled integration and E2E | US1 acceptance |
| NFR-005 | Non-colour status contract | Component, Playwright, manual | Each user story |
| NFR-006 | Failure taxonomy and safe logging | Fault injection, telemetry assertions | Operations hardening |
| NFR-007 | `PROVIDER_AUTH_REQUIRED` handling | Contract, degraded-state tests | Operations hardening |
| SEC-001 to SEC-004 | Inherited session, read-only ports, allowlist, A-11 | Security integration, negative, build scan | Security foundation |

## Complexity Tracking

| Addition | Why required now | Simpler alternative rejected | Approval | Removal or review trigger |
|---|---|---|---|---|
| A new `stock` module rather than extending `market` | The two modules own different aggregates, different refresh rates, and different provider datasets. Merging them would give one module the calendar, breadth, regime, bars, fundamentals, and valuation, which is exactly the shared-mutable-core that ADR-0007 layering exists to prevent. | Adding stock tables and services to `market` | ADR-0007 layering; no new ADR needed because no new deployable unit or datastore is added | Review if the two modules end up sharing more than the published reference-data interface |
| Fifteen new tables | Each aggregate — reference, bars, corporate actions, report metrics, derived summaries, indicator results, valuation — needs its own immutability and revision chain. Collapsing them would either lose the missing/zero/not-applicable distinction or lose reproducibility. | A wide denormalized stock table | Data model review | Review if any table has no reader after US3 ships |
| Fixture-first implementation while four gates stay open | Feature 001 established this pattern and it is the only way to build deterministic rules without depending on unresolved provider licensing. Fixtures are never presented as live data and live flags default to off. | Blocking all work until provider evidence exists | Same owner approval basis as Feature 001, 2026-08-17 | Exception ends at each gate closure; live delivery stays blocked until then |

No new service, broker, datastore, ML model, cache, or AI dependency is
introduced. The two rule sets are versioned specifications rather than
configurable engines, which is the minimum machinery that satisfies Constitution
Principle I without building a rules platform nobody asked for.

## Open Items Carried Into Tasks

1. Gates G-01 to G-04 need evidence tasks analogous to Feature 001's T045 and
   T047, each blocking exactly one live flag.
2. The `market` module must publish a small reference-data interface for the
   `stock` module to consume; that interface is a foundation task.
3. The chart rendering approach in the frontend is an implementation decision at
   task level, constrained to formatting only.
4. T051 remains a mandatory pre-deployment gate for this feature.
