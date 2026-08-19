# Implementation Plan: Deterministic Stock Screener

**Feature Directory**: `specs/003-stock-screener`
**Date**: 2026-08-19
**Spec**: [spec.md](spec.md)
**Status**: Implemented and Verified (Fixture Baseline). All 33 tasks in
`tasks.md` are complete with recorded evidence; see
`validation/fixture-acceptance.md` for the full command/result table and
scenario traceability. Backend: `.\mvnw.cmd test` 318/318 (0 regressions to
Features 001/002), including `ScreenerV1Tests` 22/22, `ScreenerServiceTests`
9/9, `ScreenerControllerTests` 5/5, `ScreenerSecurityTests` 1/1,
`ScreenerPerformanceTests` 1/1, `ScreenerFailureTests` 2/2,
`ScreenerReplayDeterminismTests` 1/1, and extended `FundamentalSummaryTests`
16/16. Frontend: `npm run test` 45/45, `npm run lint` clean, `npm run build`
clean, `npx playwright test` 42/42 (30 pre-existing + 12 new).

Two real defects were found and fixed, both by tests specifically designed
to exercise a real boundary rather than a mock of it:

1. (T028) The pass-1 daily-bar fetch was bounded by a fixed 90-calendar-day
   window, which silently dropped a stock whose last accepted session was
   older than that window from every Price/Market filter — an S-4
   violation, not just a test artifact. Fixed with
   `EquityDailyBarRepository.findLatestNCurrentByInstrumentIdIn` (a native
   per-instrument top-N query); see `research.md` R-002's 2026-08-19
   amendment.
2. (T030 follow-up) The frontend's `executeScreen` POST never attached a
   CSRF token, so every screen execution would fail with 403 against the
   real backend — invisible to every automated test because
   `ScreenerControllerTests` always supplied `.with(csrf())` explicitly and
   `stock-screener.spec.ts` mocks the network call before it reaches a real
   CSRF-checking server. Caught only by re-scrutinizing the SEC boundary
   after being asked directly whether the feature was truly complete, then
   confirmed with a new backend regression test AND a live smoke run
   (throwaway Postgres, real `spring-boot:run`, real `npm run dev`, zero
   mocking) exercising the actual login → CSRF → screener POST → JSON →
   render path end to end. Fixed by reusing the existing
   `auth/api/owner-access.ts` CSRF-fetch pattern.

This is the direct, uncomfortable lesson of both defects: a fully-mocked
test suite proves the code behaves correctly against the *shape* of its
dependencies, not against the *real* enforcement those dependencies apply.
Both fixes are backed by a test that reproduces the real failure without
mocking the boundary that hid it.

Research R-002's originally-stated Hibernate-statement-count verification
for the bulk repository methods was not implemented as a dedicated
assertion; correctness and the one-bulk-query-per-table property were
instead verified through `ScreenerServiceTests`/`ScreenerPerformanceTests`
passing within the NFR-001 latency baseline, a narrower form of the same
guarantee. No provider gate is opened or reopened by this feature; Feature
002's G-01 to G-04 and Feature 001's T051 ingress deferral remain unchanged
and still govern release readiness.

## Summary

Deliver Finvera's third vertical slice: a single deterministic screen that
narrows the supported Vietnamese stock universe by combining Market, Price,
Technical, and Fundamental filters, executed entirely against facts and
derived results Features 001/002 already accept, compute, and persist.

The screener adds no new aggregate, no new provider, and no new persisted
table. It is a new `screener` sub-package inside the existing `finvera-be`
`stock` module: a versioned filter-evaluation engine
([`screener-v1`](contracts/screener-v1.md)) reading current-revision rows
through existing repositories, exposed as one owner-only endpoint
(`POST /api/v1/screener/executions`), rendered by a new React SPA feature
folder that formats results and calculates nothing. The one substantive gap
this feature's research uncovered — Feature 002 never computed a revenue
growth metric — is closed as a small, additive extension to Feature 002's
own `fundamental-summary-v1` engine, not worked around inside the screener.

## Technical Context

**Affected projects**: `finvera-be` (extend existing `stock` module),
`finvera-fe` (new `stock-screener` feature). No change to `finvera-ai`. No
new deployable unit.

**Languages and versions**: unchanged — Java 21 with Spring Boot 4.1.x,
TypeScript 5 with React 19 and Vite, PostgreSQL 17.

**Primary dependencies**: unchanged. No new library. Filter evaluation is
plain Java over `BigDecimal`, reusing `DecimalMath`/`StockTypes` from
Feature 002.

**Storage and state**: PostgreSQL, read-only for this feature. No migration
(`data-model.md`). No Redis, Qdrant, or Kafka state.

**Interfaces**: one versioned owner-only REST endpoint under
`/api/v1/screener`; no AI or event interface; no provider port (this feature
calls no provider, live or fixture — it reads only already-accepted rows).

**Testing**: pure unit/property tests for `screener-v1` (including the two
new Breakout/Trend rules and their boundary cases), a two-pass query
integration test on Testcontainers, Spring MVC and security contract tests,
Vitest component tests, Playwright P1–P3 end-to-end.

**Performance goals**: NFR-001 — 95% of screen executions within 5 seconds
(SRS §36.1 screening baseline). Design target: one bounded pass-1 fetch of
the whole candidate universe's profile/price rows, filtered by `screener-v1`
in Java, then bulk (never per-row) pass-2 fetches scoped to the narrowed
candidate set and only the selected categories, per research R-002.

**Availability and degradation**: no provider dependency of its own to
degrade. A whole filter category with zero evaluable candidates is disclosed
as `UNAVAILABLE` rather than returned as an indistinguishable empty result
(NFR-002, research R-011).

**Scale and scope**: one screen at a time, daily timeframe only (matching
Feature 002), four filter categories, AND-only combination, no saved/named
screens, no natural-language input.

**Open technical unknowns**: none blocking. Every prior
`[NEEDS CLARIFICATION]` marker was resolved on 2026-08-19 (spec.md §
Resolved Clarifications) before this plan was written, per Constitution
Principle V.

## Constitution Check

### Pre-research gate

- [x] **I. Deterministic finance core** — `screener-v1` is specified before
  code, including the two new Breakout/Trend rules, with exact formulas,
  windows, and precision; every other filter reads an already-versioned
  engine's output unchanged.
- [x] **II. Evidence, provenance, temporal truth** — every match discloses
  `asOfTradingDate` and `dataStatus`; a category that cannot be evaluated is
  disclosed, not hidden (S-4, NFR-002).
- [x] **III. Explicit boundaries** — browser to Spring only; no new module,
  no new cross-module reach (screener reads only tables `stock` already
  owns); `finvera-ai` untouched.
- [x] **IV. Security, privacy, responsible decision support** — no new auth
  path, no new external host, no trading-adjacent capability; FR-014
  requires non-advice framing.
- [x] **V. Specification and traceability before code** — spec, research,
  data model, and two contracts exist; no unresolved clarification marker
  remains.
- [x] **VI. Risk-based testing** — numerical boundary, reproducibility,
  authorization, and degradation risks each have a named test level below.
- [x] **VII. Resilience and observability** — category-level degradation
  and a failure taxonomy are defined below; no provider dependency exists to
  time out.
- [x] **VIII. Modular simplicity** — no new module, datastore, broker,
  cache, or AI dependency; research R-001/R-002/R-006 each reject a more
  complex alternative in favor of extending what already exists.

**Result**: PASS. No live provider integration is introduced or authorized
by this feature.

### Post-design gate

- [x] `data-model.md` adds zero tables/columns/migrations; the one
  additive change (`REVENUE_GROWTH_PERCENT`) is a domain-code extension to
  an existing engine, explicitly justified against Architecture §4's
  versioned-rules rule (additive coverage, not a formula change).
- [x] The API contract expresses a degraded filter category as HTTP 200 with
  a category-level disclosure, never a fabricated or silently-empty result.
- [x] Every requirement in `spec.md` maps to a design artifact and a test
  level in the traceability table below.
- [x] Decimals are transported as strings (contract `Decimal` schema),
  matching Architecture §5.
- [x] `screener-v1.md`'s required-test-vector table covers every S-4
  exclusion reason and every new Breakout/Trend boundary.

**Result**: PASS.

## System Context and Boundaries

```text
                 owner (browser, existing session)
                              |
                              v
             POST /api/v1/screener/executions
                              |
                              v
        finvera-be / stock / screener (new sub-package)
                              |
        pass 1: bulk-fetch equity_profile + equity_daily_bar for the LISTED
                 universe; screener-v1 evaluates Market/Price filters in Java
                              |
                              v
        pass 2: bulk-fetch technical_indicator_result / fundamental_summary /
                 valuation_assessment (current rows) for the narrowed set
                              |
                              v
             screener-v1 engine (Java, pure, deterministic)
                              |
                              v
        ScreenResponse (matches, category disclosures, coherenceKey)
                              |
                              v
        React SPA stock-screener route (formats only, calculates nothing)

finvera-ai / TCBS / Vnstock / Redis / Qdrant / Kafka: no path in this feature
```

### Ownership

| Concern | Owner | Reason |
|---|---|---|
| Filter evaluation rules (`screener-v1`, including Breakout/Trend) | `finvera-be/.../stock/domain/screener` | Pure, deterministic, framework-independent — same pattern as `technical-indicators-v1`/`valuation-v1`. |
| Candidate narrowing and bulk fetch orchestration | `finvera-be/.../stock/service/screener` | Transaction/query boundary; owns the two-pass strategy (research R-002). |
| REST mapping and security | `finvera-be/.../stock/controller`, `stock/dto` | Spring remains the sole public boundary; reuses the existing owner-only rule. |
| `REVENUE_GROWTH_PERCENT` | `finvera-be/.../stock/domain/fundamentals/FundamentalSummaryCalculator.java` | Extends the Feature 002 engine that already owns TTM/growth math; not duplicated in the screener. |
| Presentation and formatting | `finvera-fe/src/features/stock-screener` | UI formats contract data; never recalculates a filter outcome. |
| Every read table | Feature 001/002 `stock`/`market` modules | Reused, not duplicated; no repository method reaches into another module. |
| AI and RAG | Not affected | No AI path exists in this feature. |

### Interface Changes

| Interface | Change | Version and compatibility | Contract Artifact |
|---|---|---|---|
| Public REST | Add one owner-only screener endpoint | Additive under `/api/v1`; response contract 1.0 | [stock-screener.openapi.yaml](contracts/stock-screener.openapi.yaml) |
| Calculation rules | Add one versioned rule set | `screener-v1` | [screener-v1.md](contracts/screener-v1.md) |
| Existing engine | Additive metric on `fundamental-summary-v1` | Same rule version (additive, not a formula change — research R-005) | `FundamentalSummaryCalculator.java`; no contract file changes (Feature 002's `research.md`/`plan.md` are not amended by this feature) |
| Database | None | No migration | [data-model.md](data-model.md) |

No existing endpoint, schema, or contract is broken. Features 001 and 002
continue to pass unchanged; `FundamentalSummaryTests` gains new cases but no
existing assertion in it changes (research R-005).

## Phase 0: Research

Complete in [research.md](research.md). Twelve decisions: module placement,
the two-pass query strategy, the two new Breakout/Trend rules, MACD/MA/
Volume filter semantics, the discovered revenue-growth gap and its fix,
no persisted screen state, the candidate universe rule, the coherence-key
reuse, the API shape, contradictory-filter validation, inherited gate
posture, and the fixture strategy.

## Phase 1: Design and Contracts

### Data model

[data-model.md](data-model.md): zero new tables. Documents which existing
tables the screener reads, the one additive `REVENUE_GROWTH_PERCENT` summary
metric and why it needs no migration, and the transient
`ScreenCriteria`/`ScreenCandidate`/`ScreenMatch`/`CategoryDisclosure` request/
response shapes.

### Contracts

- **screener-v1.md** — every filter category/field/operator, the S-4
  exclusion rule, and the two new Breakout/Trend formulas with required test
  vectors.
- **stock-screener.openapi.yaml** — one endpoint, request/response schema,
  pagination, sorting, category disclosure, coherence key, problem-details
  errors.

### Security, privacy, and AI safety

- Reuse the existing owner session and CSRF controls exactly; no new
  authentication path (SEC-001).
- No new external host; this feature calls no provider at all, live or
  fixture (S-1).
- No prompt, model, embedding, retrieval, or untrusted-document path exists.
  Result copy must state quantitative filtering output, never a ranked
  recommendation (FR-014).

### Observability and operations

**Amendment (2026-08-19, found during the post-implementation analyze
pass):** this section originally specified bespoke counters/gauges/timers
for the screener. They were never built, and — on review — building them
would have made the screener inconsistent with every other read path in
this codebase rather than more observable: Feature 002's five read-only
stock-detail sections (`StockOverviewService`, `StockChartService`,
`TechnicalIndicatorService`'s read path, `FundamentalReportService`,
`ValuationService`) carry none either. `StockObservabilityService`
(counters/gauges/timers) is scoped to the ingestion/write boundary
(`StockIngestionService`) only, by established precedent, because that is
where the six-class failure taxonomy (provider unavailable, invalid record,
etc.) actually applies — a pure read endpoint has no provider call to fail.
The corrected baseline below is what the screener actually has, matching
every sibling read endpoint:

- **Correlation**: every request — including
  `POST /screener/executions` — carries `X-Correlation-ID` via the global
  `CorrelationIdFilter` (`shared.api`), verified directly against the real
  running backend during the live-stack smoke test (T030 follow-up).
- **Health and errors**: standard Spring Boot Actuator health, and RFC 9457
  `application/problem+json` errors via the shared `ProblemDetailsAdvice`
  for the 400/401/403/500 cases (confirmed by `ScreenerControllerTests`/
  `ScreenerSecurityTests`).
- **Failure-class visibility (NFR-002)**: is carried in the response body
  itself, not a separate telemetry channel — `categoryDisclosures[].status`
  distinguishes a category with zero evaluable candidates (`UNAVAILABLE`)
  from one that was genuinely evaluated and matched nothing (`CURRENT`),
  the exact distinction `ScreenerFailureTests` asserts. The owner sees this
  directly; no log correlation is needed to diagnose it.

If a future need arises to monitor screener load/latency in aggregate
(e.g., after the single-owner assumption changes), add counters/timers to
`ScreenerService` at that time — and, per the same reasoning, consider
adding them to Feature 002's read services too, rather than leaving the
screener as the only instrumented read path for no principled reason.

Logs (from the shared filter chain and `ProblemDetailsAdvice`) may carry
the correlation id, HTTP method/path, and status; never a per-instrument
filter value, a matched result, or a credential.

### Test and evaluation strategy

| Requirement IDs | Test level | Fixtures | Expected evidence |
|---|---|---|---|
| FR-001 to FR-003, FR-008, FR-013; DATA-002 | Unit, property, API contract, Playwright P1 | Known-value multi-instrument universe | Exact intersection matches; empty result is HTTP 200; matchedValues correct to declared precision |
| FR-004; DATA-001, DATA-003 | Pure unit, property, replay | `screener-v1` required-test-vector table (RSI/MACD/MA/Volume/Breakout/Trend boundaries) | Exact category outcomes; 20-vs-21-bar Breakout boundary; MACD histogram=0 NEUTRAL; trend tie SIDEWAYS |
| FR-005; DATA-001 | Unit (extended `FundamentalSummaryTests`), API | `REVENUE_GROWTH_PERCENT` mirrors every `EPS_GROWTH_PERCENT` boundary | New metric matches an independently computed expected value; existing EPS growth assertions unchanged |
| FR-006; S-4 | Unit, integration | One fixture per exclusion reason (insufficient history, no fundamentals, withheld valuation, null sector, null shares outstanding) | Excluded with the exact reason, never silently passed/failed |
| FR-007 | Unit, integration | N/A (structural) | No provider/repository call other than existing read repositories occurs during evaluation |
| FR-009, FR-010; DATA-004 | Property, replay | Repeated identical requests | Identical match set, values, and coherence key |
| FR-011, FR-012, FR-013 | API, component | Sort fields; zero-match and full-universe requests | Correct ordering; specific empty/full states, not errors; navigation to stock detail |
| FR-014 | Component, acceptance review | All result states | Non-advice framing present |
| SEC-001, SEC-002 | Security integration, negative | Owner, non-owner, unauthenticated | Only owner succeeds; no leakage |
| NFR-001 | Integration, timing | Representative fixture universe | p95 within 5 seconds |
| NFR-002 | Integration, fault | Category with zero accepted upstream rows | Disclosed `UNAVAILABLE`, distinct from a zero-match category |
| NFR-003 | Component, Playwright, manual | All match/exclusion states | Non-colour indicator for every state |

### Rollout, migration, and rollback

1. Deploy the backend change (no migration to apply). Validate the
   `screener-v1` engine, the `REVENUE_GROWTH_PERCENT` extension against
   extended `FundamentalSummaryTests`, the API contract, and security.
2. Deploy the frontend behind the existing build; validate P1 to P3 against
   fixtures on loopback.
3. No gate closure is needed or affected by this feature; Feature 002's
   G-01 to G-04 posture is unchanged (research R-011).

**Rollback**: this feature has no migration to roll back. Removing the code
change reverts `fundamental_summary_metric` to simply never containing a
`REVENUE_GROWTH_PERCENT` row for future summaries — no destructive cleanup
of already-written rows is required, since an unrecognized-by-old-code extra
metric row is inert to Feature 002's existing readers (they read by explicit
`metric_code`, never `SELECT *`-and-assume-shape).

### Quickstart acceptance

[quickstart.md](quickstart.md) defines prerequisites, commands, the P1 to P3
acceptance paths, degraded paths, authorization checks, and accessibility
checks.

## Project Structure

### Feature documentation

```text
specs/003-stock-screener/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── screener-v1.md
│   └── stock-screener.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md                         # generated; implementation not started
```

### Source code affected

```text
finvera-be/src/
├── main/java/com/minhnb/finvera_be/stock/
│   ├── domain/
│   │   ├── screener/         # screener-v1: filters, Breakout, Trend (new)
│   │   └── fundamentals/
│   │       └── FundamentalSummaryCalculator.java   # extended: REVENUE_GROWTH_PERCENT
│   ├── service/
│   │   └── screener/         # ScreenerService: two-pass query orchestration (new)
│   ├── controller/           # StockController or new ScreenerController addition
│   ├── dto/                  # ScreenRequest/ScreenResponse contract types (new)
│   └── repository/           # new bulk-fetch finder methods on existing repositories
└── test/
    ├── java/com/minhnb/finvera_be/stock/domain/screener/
    ├── java/com/minhnb/finvera_be/stock/service/screener/
    ├── java/com/minhnb/finvera_be/stock/controller/
    └── resources/fixtures/stock/screener/   # new: small known-value universe

finvera-fe/src/features/stock-screener/
├── api/          # explicit DTO mapping against the OpenAPI contract
├── components/   # filter form, result table, category disclosure banner
├── format/       # locale, unit, precision formatting only
└── stock-screener-page.tsx
```

**Structure decision**: extend the existing `stock` module (research R-001)
rather than add a module; one new frontend feature folder mirroring
`stock-detail`'s shape. No Python is added, and `finvera-ai` is untouched.

## Traceability Summary

| Requirement IDs | Design artifact | Test evidence | Planned task group |
|---|---|---|---|
| FR-001, FR-002, FR-003 | `screener-v1.md` Market/Price sections, `MarketFilter`/`PriceFilter` | Unit, API, P1 E2E | Foundation then US1 |
| FR-004 | `screener-v1.md` Technical section, Breakout/Trend formulas | Golden vectors, boundary tests | US2 |
| FR-005 | `screener-v1.md` Fundamental section, `REVENUE_GROWTH_PERCENT` extension | Extended `FundamentalSummaryTests`, US3 API | US3 |
| FR-006 | `screener-v1.md` S-4 | Unit, integration per exclusion reason | Foundation, all stories |
| FR-007 | research R-001/R-002 | Structural/architecture test | Foundation |
| FR-008, FR-009 | `ScreenMatch.matchedValues`, `CategoryDisclosure` | API, component | Each story |
| FR-010 | research R-008 coherence key | Property, replay | Cross-cutting |
| FR-011, FR-013 | `ScreenRequest` sort/pagination, empty-result handling | API, component | US1 |
| FR-012 | Existing stock detail route | E2E navigation | US1 |
| FR-014 | Result copy | Component, acceptance review | Each story |
| DATA-001 to DATA-004 | `screener-v1.md` S-1 to S-5 | Unit, property, replay | Foundation |
| SEC-001, SEC-002 | Inherited session, no new host | Security integration, negative | Foundation |
| NFR-001 | research R-002 two-pass strategy | Timing | Cross-cutting performance |
| NFR-002 | `CategoryDisclosure` | Integration, fault | Foundation |
| NFR-003 | Non-colour status contract | Component, Playwright, manual | Each story |

## Complexity Tracking

> Fill only for a constitution violation or material complexity addition.

| Addition | Why Required Now | Simpler Alternative Rejected | Approval/ADR | Removal or Review Trigger |
|---|---|---|---|---|
| A new `screener` sub-package rather than a new module | Every aggregate it reads is already owned by `stock`; a new module would need a published cross-module interface for data `stock` already owns internally, for no ownership benefit. | A new top-level `screener` module | Constitution Principle VIII; research R-001; no ADR needed (no new deployable unit or datastore) | Revisit if a future feature needs the same evaluation primitives against non-`stock` data |
| Two new deterministic rules (Breakout, Trend) inside `screener-v1` rather than in `technical-indicators-v1` | SRS §13 names them explicitly as screener filters and Feature 002's contract does not define them; adding them to the already-shipped `technical-indicators-v1` would touch a contract this feature does not otherwise need to change. | Deferring Breakout/Trend out of scope until Feature 002 adds them | Research R-003; owner-confirmed 2026-08-19 (AskUserQuestion) | Promote into `technical-indicators-v1` v2 if a future feature needs them as persisted, displayed indicators |
| Extending `fundamental-summary-v1` with `REVENUE_GROWTH_PERCENT` | Feature 002's own spec named "revenue growth" in scope but the engine never computed it; the screener needs it and must not recompute a fundamental metric independently (FR-005). | Computing revenue growth ad hoc inside the screener (rejected: duplicates engine-of-record logic, DATA-001) | Research R-005; owner-confirmed 2026-08-19 | None — this closes a genuine Feature 002 delivery gap under its existing rule version |

No new service, broker, datastore, ML model, cache, or AI dependency is
introduced.

## Open Items Carried Into Tasks

1. `FundamentalSummaryTests` (Feature 002) needs new cases for
   `REVENUE_GROWTH_PERCENT` before the Fundamental filter category can be
   considered implemented (research R-005).
2. The exact bulk-fetch repository method signatures (pass 2, research
   R-002) are a task-level implementation decision, constrained to
   "one query per table per screen, never one query per instrument."
3. The frontend filter form's exact layout/interaction design is a
   task-level decision, constrained to: non-color status, and never
   recomputing a filter outcome client-side.
