# Implementation Plan: Portfolio and Watchlist Management

**Feature Directory**: `specs/005-portfolio-watchlist`
**Date**: 2026-08-19 (Delivered: 2026-08-20)
**Spec**: [spec.md](spec.md)
**Status**: Delivered & Verified. All phases (Foundation, US1, US2, US3, Phase 5) completed with full test coverage.

## Summary

Deliver Finvera's fifth vertical slice: an owner-scoped, immutable
transaction ledger (`BUY`/`SELL`/`DEPOSIT`/`WITHDRAW`/`VOID`) per portfolio,
from which holdings (FIFO cost basis, unrealized/realized P/L, allocation),
portfolio totals, and analytics (return, drawdown, performance history,
concentration, risk exposure, VN-Index benchmark comparison) are always
computed on demand — never stored as a separately editable value. A second,
independent capability, watchlists, lets the owner track research
candidates whose items show live price/trend/signal/risk context reused
exactly from Features 001-004.

This is the platform's first feature to persist **owner-written** data
(`portfolio`, `portfolio_transaction`, `watchlist`, `watchlist_item`) and
its first to need row-level ownership enforcement (research R-002) — every
prior table is global reference data with no owner column at all. No new
formula recomputes anything Features 001-004 already compute; this
feature's own new rules (`portfolio-analytics-v1`) are FIFO matching, P/L,
return, drawdown, concentration, and risk rollup — combination logic over
already-accepted values, the same discipline Feature 004 applied to
strategy/signal/risk.

## Technical Context

**Affected projects**: `finvera-be` (new `portfolio` module — research
R-001), `finvera-fe` (new `portfolio` and `watchlist` feature folders). No
change to `finvera-ai`. No new deployable unit.

**Languages and versions**: unchanged — Java 21/Spring Boot 4.1.x,
TypeScript 5/React 19/Vite, PostgreSQL 17.

**Primary dependencies**: unchanged. Reuses `DecimalMath`, `StockTypes`
(`Applicability`), `CoherenceKeys`, Feature 001's market calendar and
VN-Index snapshot read interfaces, Feature 002's accepted daily
bars/sector classification, and Feature 004's current `strategy_signal`
read path (via a new published interface method, mirroring Feature 004's
own extension of `MarketReferenceDataService`).

**Storage and state**: PostgreSQL. Forward-only Flyway from `V005`: four
new tables (`portfolio`, `portfolio_transaction`, `watchlist`,
`watchlist_item`). No existing table altered. No position, total, or
analytics value is ever stored (research R-004).

**Interfaces**: new owner-only REST endpoints under `/api/v1/portfolios`
and `/api/v1/watchlists` — see
[contracts/portfolio-watchlist.openapi.yaml](contracts/portfolio-watchlist.openapi.yaml).

**Testing**: pure unit/property tests for `portfolio-analytics-v1` (FIFO
matching, P/L, return, drawdown, concentration, risk rollup formulas), DB
integration tests (ledger persistence, void semantics, backdated replay,
row-level ownership), Spring MVC/security contract tests (including the
first negative "not my portfolio" authorization tests this platform has
needed), Vitest component tests, Playwright P1-P3.

**Performance goals**: NFR-001 — 95% of portfolio holdings/watchlist views
within 3 seconds. NFR-002 — 95% of portfolio analytics views within 3
seconds, at the bounded period research R-006 requires.

**Availability and degradation**: a stale/withheld price withholds only
the affected position's unrealized P/L and the totals that depend on it,
never the whole view; an uncovered position (no current Feature 004
signal) is excluded from the risk-exposure score with its proportion
stated, never assumed risk-free; a data gap in performance history is
flagged `PARTIAL`, never zero-filled.

**Scale and scope**: one owner, an unbounded number of named portfolios and
watchlists, ledger size realistically in the low thousands of transactions
per portfolio (ADR-0005's private single-owner deployment), no
multi-currency, no broker import.

**Open technical unknowns**: none blocking — research.md resolves the
owner-scoping model (R-002), the ledger/void model (R-003), the
on-demand-only derivation decision (R-004), the return methodology (R-005),
and the drawdown/performance-history reconstruction algorithm (R-006)
before this plan was written, per Constitution Principle V.

## Constitution Check

### Pre-research gate

- [x] **I. Deterministic finance core** — `portfolio-analytics-v1`
  specifies FIFO matching, P/L, return, drawdown, concentration, and risk
  rollup formulas before code, each versioned; no LLM path exists in this
  feature.
- [x] **II. Evidence, provenance, temporal truth** — every transaction
  retains `executedAt` and `entryAt` as distinct values (DATA-002); every
  computed figure is reproducible from the ledger plus already-accepted
  facts, never a second stored copy that could drift (research R-004).
- [x] **III. Explicit boundaries** — browser to Spring only; a new
  `portfolio` module is justified because no existing module owns
  owner-written data (research R-001); reads Feature 004's signals only
  through a published interface extension, never its repository.
- [x] **IV. Security, privacy, responsible decision support** — this is
  the platform's first owner-scoped data, so ownership enforcement
  (research R-002) is designed up front, not retrofitted; a mismatch is
  indistinguishable from 404 (SEC-002); no signal/risk figure implies a
  guarantee (reused Feature 004 framing).
- [x] **V. Specification and traceability before code** — spec, research,
  data model, and two contracts exist; no unresolved clarification marker.
- [x] **VI. Risk-based testing** — numerical boundary (FIFO edge cases, the
  void-validity rule U-6), persistence, the platform's first negative
  ownership tests, and degradation risks each have a named test level
  below.
- [x] **VII. Resilience and observability** — degradation paths (stale
  price, uncovered risk, data-gap performance points) are defined; this
  feature's read endpoints follow the same observability baseline Features
  002-004 already established: global `CorrelationIdFilter` + response-
  carried failure-class visibility, not a separate telemetry channel.
- [x] **VIII. Modular simplicity** — one new module, not two (research
  R-001); no materialized position/analytics table (research R-004); no
  true time-weighted-return engine for a first version (research R-005);
  no separately maintained valuation-snapshot job (research R-006);
  position sizing (SRS §18) deliberately excluded even though its blocking
  inputs now exist (spec.md Out of Scope).

**Result**: PASS.

### Post-design gate

Completed after Phase 1 design (2026-08-19), reviewed against the actual
data model and contracts produced, not only the pre-research intent:

- [x] **I. Deterministic finance core** — `portfolio-analytics-v1.md`
  specifies every formula (FIFO lots, cash balance, totals, return,
  drawdown, concentration, risk rollup, benchmark) with a required
  test-vector table; all arithmetic is `BigDecimal` (U-2).
- [x] **II. Evidence, provenance, temporal truth** — `portfolio_transaction`
  carries `executedAt`/`entryAt` and `sequenceNo` for deterministic replay
  order (data-model.md); a `VOID` never deletes, it links (U-5); a data
  gap in performance history is `PARTIAL` with the affected symbol named,
  never silently dropped.
- [x] **III. Explicit boundaries** — `portfolio` reads `market`/`stock`
  reference data and Feature 004's signals only through published
  interfaces (data-model.md "Relationship to Features 001-004"); no table
  from an earlier feature is altered.
- [x] **IV. Security, privacy, responsible decision support** — every
  `portfolio`/`watchlist` row carries `owner_id` (research R-002); the
  OpenAPI contract's `Forbidden`/`PortfolioNotFound`/`WatchlistNotFound`
  responses are explicitly documented as indistinguishable (SEC-002); every
  state-changing endpoint requires `X-CSRF-TOKEN` from the contract's first
  draft, closing the exact gap class Feature 003's T030 finding discovered
  after the fact and Feature 004 already avoided by deciding it up front.
- [x] **V. Specification and traceability before code** — `data-model.md`
  and both contracts trace every FR/DATA requirement to a concrete field,
  formula, or endpoint; no deviation from spec.md was needed during design.
- [x] **VI. Risk-based testing** — the Test and Evaluation Strategy table
  below names a level for every requirement group, including the void-
  validity boundary (U-6) and the platform's first negative
  cross-ownership authorization tests.
- [x] **VII. Resilience and observability** — matches Features 002-004's
  established baseline; no bespoke counters planned, consistent with that
  precedent unless a real operational need emerges later.
- [x] **VIII. Modular simplicity** — the design introduces exactly four
  tables and one module, no materialized derived table, no new provider,
  no new datastore, no AI dependency. `Complexity Tracking` below records
  the one genuinely new pattern this feature adds (owner-scoping) and why
  it is justified now rather than deferred.

**Result**: PASS.

## System Context and Boundaries

```text
                 owner (browser, existing session)
                              |
      POST/GET/PATCH/DELETE /portfolios, /portfolios/{id}/transactions,
      /portfolios/{id}/transactions/{id}/void, /portfolios/{id}/positions,
      /portfolios/{id}/analytics
      POST/GET/PATCH/DELETE /watchlists, /watchlists/{id}/items
                              |
                              v
              finvera-be / portfolio (new module)
                              |
        read: market_instrument, equity_profile, equity_daily_bar
              (Feature 001/002), market calendar + VN-Index snapshot
              (Feature 001), current strategy_signal (Feature 004)
        write: portfolio, portfolio_transaction, watchlist, watchlist_item
                              |
                              v
     portfolio-analytics-v1 engine (Java, pure, deterministic):
       FIFO replay -> positions/totals -> return/drawdown/concentration/
       risk rollup -> benchmark comparison
                              |
                              v
     React SPA: new "Portfolio" feature (holdings, ledger, analytics)
                and new "Watchlist" feature

finvera-ai / TCBS / Vnstock / Redis / Qdrant / Kafka: no path in this feature
```

### Ownership

| Concern | Owner | Reason |
|---|---|---|
| FIFO cost basis, P/L, return, drawdown, concentration, risk rollup (`portfolio-analytics-v1`) | `finvera-be/.../portfolio/domain/analytics` | Pure, deterministic, framework-independent — same pattern as `technical-indicators-v1`/`screener-v1`/`strategy-signal-v1`. |
| Ledger persistence, void validity, ownership enforcement | `finvera-be/.../portfolio/service` | Transaction boundary; the platform's first owner-scoped write path. |
| Watchlist membership and live-context assembly | `finvera-be/.../portfolio/service` | Reuses Feature 004's "live re-check, no second table" pattern (research R-009). |
| REST mapping and security | `finvera-be/.../portfolio/controller`, `portfolio/dto` | Spring remains the sole public boundary. |
| Presentation and formatting | `finvera-fe/src/features/portfolio`, `finvera-fe/src/features/watchlist` | UI formats contract data; never recomputes a position, total, or analytics figure. |
| Every read table/derivation from earlier features | Features 001, 002, 004 | Reused, not duplicated. |
| AI and RAG | Not affected | No AI path exists in this feature. |

### Interface Changes

| Interface | Change | Version and compatibility | Contract Artifact |
|---|---|---|---|
| Public REST | Add portfolio, transaction, position, analytics, and watchlist owner-only endpoints | Additive under `/api/v1` | [portfolio-watchlist.openapi.yaml](contracts/portfolio-watchlist.openapi.yaml) |
| Calculation rules | Add one versioned rule set | `portfolio-analytics-v1` | [portfolio-analytics-v1.md](contracts/portfolio-analytics-v1.md) |
| Feature 004 published interface | Add a "current signals for a set of instruments" read method (mirroring Feature 004's own addition to `MarketReferenceDataService`) | Additive | Recorded in `tasks.md` at implementation time |
| Database | Add `portfolio`, `portfolio_transaction`, `watchlist`, `watchlist_item` | Forward Flyway from `V005`; no existing table altered | [data-model.md](data-model.md) |

## Phase 0: Research

Complete in [research.md](research.md). Eleven decisions: module
placement, the owner-scoping model (the platform's first), the
transaction-ledger/`VOID` model and FIFO replay, the on-demand-only
derivation decision (no materialized position/analytics table), the
net-contributed-capital return methodology, the bounded single-pass
drawdown/performance-history reconstruction (with its 730-day default
span and inception-date clamping), concentration, the portfolio
risk-exposure rollup reusing Feature 004, watchlist's live-read model,
the fixture strategy, and idempotent transaction recording via a required
per-attempt `Idempotency-Key` (R-011).

## Phase 1: Design and Contracts

### Data model

[data-model.md](data-model.md): four additive tables, all owner-scoped.
Load-bearing constraints: a transaction is never edited or deleted, only
voided by a new typed entry (`VOID`); the FIFO/cash/analytics derivation
has no storage of its own and cannot drift from the ledger it replays;
`(portfolio_id, idempotency_key)` is unique (research R-011), so a
retried submission cannot create a second ledger row even under
concurrent requests — the constraint, not application logic alone, is
what makes this safe.

### Contracts

- **portfolio-analytics-v1.md** — FIFO cost basis and P/L, cash balance,
  totals, return (since-inception and period), drawdown and performance
  history (including inception-date clamping and its
  `periodClampedToInception` signal), concentration, risk-exposure rollup,
  benchmark comparison, and required test vectors.
- **portfolio-watchlist.openapi.yaml** — portfolio/transaction/position/
  analytics/watchlist endpoints, a required `Idempotency-Key` header on
  every ledger-writing call (research R-011), void semantics,
  ownership-indistinguishable 404s, a single `400` vs. `409` convention
  (invalid input reference vs. ledger-state conflict) applied consistently
  across every endpoint, and problem-details errors.

### Security, privacy, and AI safety

- Reuse the existing owner session and CSRF controls exactly (SEC-001);
  every state-changing operation in the contract requires `X-CSRF-TOKEN`
  from the first draft — Feature 003's T030 follow-up finding is not
  repeated a third time.
- **New**: row-level ownership enforcement (research R-002) — the
  platform's first. Every service-layer read/write compares the resource's
  `owner_id` against the session's authenticated owner id; a mismatch is
  treated identically to "not found" (SEC-002), verified by this feature's
  first negative cross-ownership authorization tests.
- No new external host; this feature calls no provider, live or fixture.
- Risk-exposure and return figures reuse Feature 004's calibrated,
  non-guarantee framing; the return methodology's own distortion
  limitation is disclosed via `returnMethodDisclosureCode`, never hidden.

### Observability and operations

Matches Features 002-004's established baseline: global
`CorrelationIdFilter` on every request, shared `ProblemDetailsAdvice` for
errors, failure-class visibility carried in the response itself
(`dataStatus`, `Applicability`, `reasonCode`) rather than a separate
telemetry channel. No bespoke counters are planned; if a real operational
need for them emerges later, add them across every read endpoint at once,
consistent with Feature 004's own decision.

### Test and evaluation strategy

| Requirement IDs | Test level | Fixture/dataset | Expected evidence |
|---|---|---|---|
| FR-001, FR-002, FR-006, FR-007 | Unit, integration | Deposit + buy + partial sell fixture ledger | Correct quantity, FIFO cost basis, unrealized/realized P/L, allocation |
| FR-003, FR-004 | Unit, boundary | Over-sell, over-withdraw fixtures | Rejected with the correct reason code; zero ledger/state change |
| FR-005 | Integration | A `VOID` of a valid, an already-voided, and a lot-consumed transaction | Correct accept/reject per `portfolio-analytics-v1` U-6; original always queryable |
| FR-002, SC-007 (idempotency, research R-011) | Integration | Same `Idempotency-Key` replayed vs. a different key with identical fields | Replay rejected `DUPLICATE_SUBMISSION`, zero additional change; different key accepted as a genuine second transaction |
| FR-011, FR-012 (inception clamping, F6) | Unit, boundary | Analytics `from` before vs. at/after the first transaction | `periodFrom`/`periodClampedToInception` correct in both cases |
| FR-016 (reproducibility) | Property, replay | Identical ledger/prices run twice | Identical positions, totals, analytics |
| FR-008, FR-009, FR-010 | Unit, integration | Watchlist items with/without a current Feature 004 signal, and with insufficient history | Live values match the source feature's own view; truthful unavailable states |
| FR-011, FR-012 | Unit, property | Multi-day ledger with a known independently-computed return/drawdown | Matches `portfolio-analytics-v1`'s formulas exactly |
| FR-013 | Unit | Multi-sector holdings fixture | Stock/sector concentration sums correctly |
| FR-014 | Unit, boundary | Mixed covered/uncovered positions | Score from covered only; `coverageRatio` correct |
| FR-015 | Integration | Known VN-Index snapshot series | Benchmark return matches independently computed value |
| FR-017 | Integration | A corrected price/sector after a value was computed | Recomputed, never stale |
| FR-018 | Unit | Unsupported-symbol transaction attempt | Rejected, `UNSUPPORTED_INSTRUMENT` |
| DATA-001 to DATA-005 | Unit, integration | Precision/rounding, `executedAt`/`entryAt` distinction, currency | No floating point; distinct times retained; VND-only enforced |
| SEC-001, SEC-002 | Security integration, negative (first cross-ownership tests) | Unauthenticated, missing CSRF, wrong-owner resource id | Only the owning owner succeeds; wrong-owner indistinguishable from 404 |
| NFR-001, NFR-002 | Integration, timing | Realistic-scale fixture ledger (research R-004 risk) | p95 within baseline |
| NFR-003 | Component, Playwright, manual | All P/L/allocation/drawdown/risk states | Non-colour indicator |

### Rollout, migration, and rollback

1. Apply Flyway `V005`. Validate the engine, persistence, contracts, and
   ownership enforcement against fixtures.
2. Deploy the frontend behind the existing build.
3. No gate closure needed; this feature opens none.

**Rollback**: no migration to reverse destructively; application code
rolls back to a version compatible with `V005`. A defective rule version
produces `portfolio-analytics-v2` and parallel results, never rewriting
`v1` history; a ledger row is never rewritten under any version.

### Quickstart acceptance

[quickstart.md](quickstart.md) defines prerequisites, commands, P1-P3
acceptance paths, degraded paths, and the platform's first cross-ownership
authorization checks.

## Project Structure

### Feature documentation

```text
specs/005-portfolio-watchlist/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── portfolio-analytics-v1.md
│   └── portfolio-watchlist.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md   (generated by /speckit-tasks)
```

### Source code affected

```text
finvera-be/src/
├── main/java/com/minhnb/finvera_be/portfolio/    # new module
│   ├── domain/analytics/     # portfolio-analytics-v1 engine (new)
│   ├── service/               # PortfolioService, TransactionService,
│   │                          # WatchlistService, PortfolioAnalyticsService (new)
│   ├── controller/            # PortfolioController, WatchlistController (new)
│   ├── dto/                   # request/response DTOs (new)
│   ├── entity/, repository/   # 4 new tables (new)
│   └── resources/db/migration/V005__*.sql
└── test/...

finvera-fe/src/features/
├── portfolio/    # holdings, ledger entry, analytics (new)
└── watchlist/    # watchlist management and item context (new)
```

**Structure decision**: a new `portfolio` module (research R-001) because
this is genuinely new, owner-written data that no existing module owns;
two new frontend feature folders, mirroring the one-folder-per-surface
convention Features 002-004 already use (e.g., `stock-detail` vs.
`stock-strategy`), since holdings/analytics and watchlist management are
materially different UI surfaces sharing only reused display data. No
Python is added, and `finvera-ai` is untouched.

## Traceability Summary

| Requirement IDs | Design artifact | Test evidence | Planned task group |
|---|---|---|---|
| FR-001 to FR-007, FR-016 (holdings) | `portfolio-analytics-v1.md` FIFO/cash/totals sections | Unit, integration, property | Foundation, US1 |
| FR-008 to FR-010 | research R-009 live-read model | Unit, integration | US2 |
| FR-011 to FR-015, FR-016 (analytics) | `portfolio-analytics-v1.md` return/drawdown/concentration/risk/benchmark sections | Unit, property, integration | US3 |
| FR-017 | research R-004 on-demand derivation | Integration | Foundation |
| FR-018 | data-model.md instrument-support check | Unit | Foundation |
| DATA-001 to DATA-005 | `portfolio_transaction` schema, U-2 precision rule | Unit, integration | Foundation |
| SEC-001, SEC-002 | research R-002 owner-scoping model | Security integration, negative | Foundation |
| NFR-001, NFR-002 | research R-004/R-006 on-demand and bounded-scan design | Timing | Cross-cutting |
| NFR-003 | Non-colour status contract | Component, Playwright, manual | Each story |

## Complexity Tracking

| Addition | Why Required Now | Simpler Alternative Rejected | Approval/ADR | Removal or Review Trigger |
|---|---|---|---|---|
| A new `portfolio` top-level module | No existing module owns owner-written data; extending `stock` or `market` would misattribute ownership | Extending `stock` | Constitution Principle III; research R-001; no ADR needed | Revisit if Watchlist grows independent complexity (e.g., its own alerts) justifying a split |
| First owner-scoping (`owner_id`) pattern | SEC-001/SEC-002 require enforced, not assumed, ownership; this is the first feature with owner-written rows | No `owner_id` column, relying on "only one session can exist" | research R-002; Constitution Principle IV | Revisit only if the identity model changes (multi-user), which itself requires a new ADR per docs/ARCHITECTURE.md §7 |
| `VOID`-typed ledger entries rather than generic reversing transactions | Simpler, explicit, avoids inventing per-type inverse logic | A generic reversal that negates the original's fields | research R-003 | Revisit if a future feature needs partial (not whole-transaction) correction |
| Net-contributed-capital return instead of true time-weighted return | Simpler, fully reproducible from the ledger, matches common retail-tracker practice; disclosed limitation | Modified Dietz / XIRR | research R-005 | Revisit if real usage shows the distortion is materially misleading |

No new service, broker, datastore, ML model, cache, or AI dependency is
introduced.

## Open Items Carried Into Tasks

1. Feature 004's `MarketReferenceDataService`-style read interface needs
   one additive method returning current signals for a set of instrument
   ids (for the watchlist and risk-exposure read paths) — a foundation
   task, mirroring Feature 004's own addition for Feature 001's regime
   assessment.
2. ~~The exact configuration key name and default value for the
   performance-history span bound~~ — **resolved**: `finvera.portfolio.
   max-performance-history-span-days`, default `730` (research R-006).
   Remains configurable per deployment; only the name and default were an
   open item.
3. The exact frontend navigation placement (a new top-level "Portfolio"
   and "Watchlist" nav entry vs. nesting under an existing section) is a
   task-level UI decision, constrained to: formatting only, never
   recomputing a position, total, or analytics figure.
4. The exact `Idempotency-Key` generation strategy on the frontend (a UUID
   generated on first submit attempt, held in the transaction-form
   component's own state and reused only for that form's own retries) is a
   task-level implementation detail of T012/T014, constrained to: one key
   per logical submit action, never reused across two separately confirmed
   owner decisions (research R-011).
