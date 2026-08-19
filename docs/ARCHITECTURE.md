# Finvera Architecture

**Status**: Living document
**Last updated**: 2026-08-18
**Applies to**: `finvera-fe`, `finvera-be`, `finvera-ai`, and the shared data
stores

## What this document is, and is not

This is the **cross-feature engineering design** of Finvera: the boundaries,
conventions, and invariants that every feature inherits so that each new
`specs/NNN-*/` directory does not re-derive them.

It is **not**:

- product intent or MVP scope — that is the SRS;
- a substitute for a feature's `plan.md` and `data-model.md`;
- a decision record — durable decisions live in `docs/adr/` and this document
  points at them rather than restating them.

**Authority**: this document ranks below the constitution, an approved feature
spec or contract, and an ADR. Where it disagrees with any of those, they win and
this document is the defect. Where it disagrees with the code, this document
wins and the code is the defect.

## 1. System context

```text
                    owner's browser (private tailnet only)
                                   |
                                   | HTTPS, session cookie, CSRF
                                   v
              +--------------------------------------------+
              |  finvera-be  (Spring Boot, modular monolith)|
              |  sole public API and authentication boundary|
              +--------------------------------------------+
                 |            |                  |
    outbound TLS  |            | internal HTTP    | JDBC
    (allowlisted) |            | (server to server)|
                 v            v                  v
        market data      finvera-ai         PostgreSQL
        providers        (FastAPI,          (transactional
        TCBS, Vnstock    RAG/LLM)            source of truth)
                                |
                                v
                        Qdrant (rebuildable index)
```

Redis is cache and ephemeral state only. Kafka is **not** a default dependency;
SRS section 43 governs whether it is ever adopted.

## 2. Boundary rules

These are invariants, not preferences. A change to any of them needs an ADR.

| # | Rule | Why |
|---|---|---|
| B-1 | The browser calls `finvera-be` only. It never calls `finvera-ai` or a market-data provider. | One authentication, authorization, and quota boundary; provider credentials never reach a client. |
| B-2 | `finvera-be` owns authentication, authorization, transactions, and the public API contract. | A second gatekeeper is a second place to get it wrong. |
| B-3 | `finvera-ai` is internal, stateless with respect to business truth, and independently deployable. | Keeps LLM latency, cost, and failure out of the deterministic path. |
| B-4 | Deterministic engines own every financial calculation. The LLM may retrieve, correlate, summarize, and explain their outputs; it may never produce or replace one. | Constitution Principle I. A model that computes an indicator is an unauditable indicator. |
| B-5 | Cross-module access inside `finvera-be` goes through a published application interface, never another module's repository, entity, or table. | Otherwise the modular monolith silently becomes a shared-mutable-core monolith. |
| B-6 | PostgreSQL is authoritative. Qdrant, Redis, and any future cache are rebuildable and disposable. | Recovery means rebuilding an index, never reconstructing truth. |
| B-7 | Every outbound host is explicitly allowlisted in configuration. | An adapter that can call anywhere is an exfiltration path. |

## 3. Backend module structure

```text
finvera-be/src/main/java/com/minhnb/finvera_be/
├── shared/       cross-cutting web concerns (correlation id, problem details)
├── auth/         owner session, CSRF, security configuration
├── market/       feature 001 — indices, calendar, instruments, breadth, regime
└── stock/        feature 002 — equity reference, bars, fundamentals, analysis
```

Each domain module uses the ADR-0007 layering:

```text
controller  ->  service  ->  repository  ->  entity
     |             |
    dto          domain          provider     config
```

| Layer | May depend on | Must not |
|---|---|---|
| `controller` | `service`, `dto` | Touch `repository` or `entity` directly |
| `dto` | nothing | Leak JPA or provider types |
| `service` | `domain`, `repository`, `provider` | Contain framework-independent business rules that belong in `domain` |
| `domain` | nothing framework-specific | Import Spring, JPA, or HTTP types |
| `repository`, `entity` | JPA | Contain business rules |
| `provider` | external SDK or HTTP client | Be referenced outside `service` |

`domain` being dependency-free is what makes the finance core testable as pure
arithmetic, with no container, no database, and no clock.

## 4. Data architecture

### Stores

| Store | Role | Recovery |
|---|---|---|
| PostgreSQL | Transactional source of truth for every accepted fact and derived result | Backup and restore; nothing else reconstructs it |
| Qdrant | Vector index for retrieval | Rebuilt from PostgreSQL documents |
| Redis | Cache and ephemeral state | Discarded and repopulated |
| Kafka | Not adopted | See SRS section 43 |

### Schema conventions

| Convention | Rule |
|---|---|
| Migrations | Flyway, forward-only, `V<NNN>__<snake_case_description>.sql`, never edited after being applied |
| Rollback | Deploy compatible application code; destructive reverse migrations are not automated |
| Evolution order | Add nullable column, backfill, then add constraint |
| Primary keys | UUID |
| Naming | `snake_case` tables and columns, singular table names |
| Instants | `timestamptz`, always UTC |
| Market dates | `date`, interpreted in `Asia/Ho_Chi_Minh` |
| Money and ratios | `numeric` with a declared scale, mapped to `BigDecimal` |
| Enumerations | `varchar` with a `check` constraint, not a PostgreSQL enum type, so a new value is a migration rather than a type alteration |

### Financial data invariants

These apply to every feature that stores a market or financial fact.

1. **No binary floating point.** `double` and `float` never hold an
   authoritative value, in Java, in SQL, or on the wire.
2. **Immutability.** An accepted observation is never updated in place. A
   correction is a new revision linked by `supersedes_id`, so what the user was
   shown yesterday remains reconstructible.
3. **Provenance.** Every accepted fact carries its source label, observation
   time, ingestion time, and the trading date or reporting period it describes.
4. **Distinct absence.** Missing, zero, invalid, and not-applicable are four
   different things and are never collapsed. A missing price is not zero.
5. **Reproducible derivation.** Every calculated result records its rule
   version and enough input identity — explicit foreign keys, or a hash of an
   immutable input set — to be recomputed exactly.
6. **Unrounded decisions.** Direction, classification, and threshold comparisons
   read unrounded values. Rounding happens once, at display.
7. **Versioned rules.** A formula change creates a new rule version and parallel
   results. It never rewrites history under the old version.

### Shared vocabulary

Established by feature 001 and inherited by every later feature:

| Vocabulary | Values |
|---|---|
| Data status | `CURRENT`, `DELAYED`, `STALE`, `PARTIAL`, `UNAVAILABLE` |
| Direction | `UP`, `DOWN`, `UNCHANGED` |
| Applicability | `DEFINED`, `NOT_APPLICABLE`, `MISSING` |
| Adjustment status | `ADJUSTED`, `RAW`, `NOT_APPLICABLE`, `UNKNOWN` |
| Session state | `PRE_OPEN`, `OPEN`, `BREAK`, `INTERRUPTED`, `CLOSED`, `NON_TRADING_DAY`, `UNKNOWN` |

A new feature reuses these rather than inventing a parallel set. `SOURCE_CONFLICT`
is a reason code, not a status.

## 5. API conventions

| Convention | Rule |
|---|---|
| Base path | `/api/v1`; a breaking change means `/api/v2` plus a migration decision in the feature plan |
| Contract artifact | An OpenAPI document under `specs/<feature>/contracts/` is the authority, not the generated code |
| Authentication | Session cookie `FINVERA_SESSION`, `Secure`, `HttpOnly`, `SameSite=Strict` |
| State changes | Require `X-CSRF-TOKEN`; a missing token is HTTP 403 with no state change |
| Errors | RFC 9457 `application/problem+json` with a stable `reasonCode` and a `correlationId` |
| Degraded success | HTTP 200 with a section-level status and reason codes; a partially available view is not an error |
| Decimals | Transported as JSON **strings**, so a client's IEEE-754 parse cannot silently alter declared precision |
| Nullability | An unavailable fact is `null` with a reason code; it is never `0`, `"-"`, or an empty string |
| Caching | `ETag` plus `304`; the validator is derived from the accepted revision the response was built from |
| Timestamps | ISO-8601 UTC instants; market dates as `YYYY-MM-DD` with an explicit `timezone` field |
| Provenance | Allowlisted source labels only; never raw provider metadata, headers, or payloads |

## 6. Frontend conventions

| Convention | Rule |
|---|---|
| Structure | `src/features/<feature-name>/` with `api/`, `components/`, `format/` |
| Calculation | The client formats; it never computes an authoritative financial value. If a number appears on screen, the server produced it. |
| Mapping | Explicit DTO mapping against the OpenAPI contract, not structural trust in the response |
| Precision | Decimals arrive as strings and are formatted without an intermediate `Number` conversion |
| Accessibility | Direction, freshness, trend, and assessment states always carry a text or icon cue; colour is never the only carrier of meaning |
| Authorization | UI visibility is presentation, never access control |

## 7. Security architecture

Current deployment posture is defined by ADR-0005: a **private, single-owner**
system.

| Control | Implementation |
|---|---|
| Identity | One configured owner UUID, normalized username, and offline-generated `{bcrypt}` hash. No registration, invitation, sharing, or recovery flow exists. |
| Ingress | Tailscale Serve only. Spring binds `127.0.0.1`. No Funnel, port forward, public DNS record, or public tunnel. See `docs/runbooks/private-market-overview.md`. |
| Session | Server-side, rotated on login, 30-minute idle and 8-hour absolute expiry, invalidated on logout. |
| Secrets | Environment or secret store only. Never in source, fixtures, logs, API responses, database columns, or the client bundle. |
| Provider access | Read-only ports with an operation allowlist. Trading, account, cash, and order operations are unreachable by construction. |
| Untrusted content | Provider payloads and documents are data, never instructions. Prompt-injection handling is mandatory wherever an LLM path exists. |
| Logging | Permitted: source, dataset, subject, reason code, correlation id, timings, counts. Forbidden: credentials, tokens, cookies, full payloads, private user data. |

**Current limitation, stated plainly**: TCBS and Vnstock data are licensed for
private, non-commercial use. Public or multi-user delivery requires a
commercially licensed provider, a new adapter contract, an ADR, and a revised
ingress and authentication design. No feature may quietly assume otherwise.

## 8. Configuration conventions

| Convention | Rule |
|---|---|
| Location | `application.yaml` with `${ENV_VAR}` placeholders; no secret literal in the repository |
| Namespace | `finvera.<module>.*` for feature configuration |
| Secrets | No default value. A missing secret must fail startup, not fall back to something weak |
| Feature flags | Live provider paths default to **off**; enabling one requires its evidence gate to be closed |
| Thresholds | Contracted delays, tolerances, and limits are configuration, never hard-coded constants |
| Time zone | Application runs UTC-safe; `Asia/Ho_Chi_Minh` is applied explicitly at market-semantics boundaries |

## 9. Observability

Every request and ingestion path carries a correlation identifier end to end.

| Signal | Examples |
|---|---|
| Counters | Records received, accepted, duplicate, rejected, corrected, by source, dataset, and reason; calculations published versus withheld |
| Gauges | Last provider success, last accepted observation, ingestion lag, data-quality state per dataset |
| Timers | Provider call, normalization, calculation, database transaction, endpoint latency |
| Health | Provider connectivity, data freshness per dataset, database, calculation pipeline |

**Failure classes** must be distinguishable in monitoring without exposing
credentials or private data: provider unavailable, provider auth expired,
invalid or rejected record, insufficient accepted data, calculation failure,
delivery failure.

## 10. Decision index

| Area | Decision | ADR |
|---|---|---|
| Backend framework | Spring Boot 4 | [ADR-0001](adr/0001-use-spring-boot-4.md) |
| LLM provider | Gemini as the initial provider | [ADR-0002](adr/0002-use-gemini-as-initial-llm-provider.md) |
| Live market data | TCBS iFlash, private use | [ADR-0003](adr/0003-use-tcbs-for-private-market-data-v1.md) |
| Historical bootstrap | Pinned Vnstock, offline, owner-operated | [ADR-0004](adr/0004-use-vnstock-for-private-historical-bootstrap.md) |
| Access and ingress | Tailscale plus local owner session | [ADR-0005](adr/0005-use-tailscale-and-local-owner-session.md) |
| Web client | React with Vite, not Next.js | [ADR-0006](adr/0006-use-react-vite-for-private-web-client.md) |
| Module internals | Layered architecture within modules | [ADR-0007](adr/0007-use-layered-architecture-within-backend-modules.md) |
| Embedding provider | Gemini-family embeddings for RAG v1 | [ADR-0008](adr/0008-use-gemini-embeddings-for-rag-v1.md) |

SRS section 57 maps the SRS's original architectural baseline to these ADRs where
the two differ. The ADR is the engineering authority; the SRS remains the product
authority.

## 11. Deliberately absent

Named here so no feature treats their absence as an oversight or as permission.

| Not present | Condition for adding it |
|---|---|
| Kafka or any broker | SRS section 43 plus a demonstrated event-driven requirement and an ADR |
| A second deployable backend service | Measurable need and an ADR; the default answer is a module |
| Redis as required state | Only as a disposable cache; never as truth |
| Multi-user accounts, registration, sharing | A licensed provider and a new authentication design |
| Order execution or brokerage integration | Out of product scope; Finvera is decision support, not a trading system |
| An LLM in any calculation path | Never. Constitution Principle I |

## 12. Changing this document

Update it in the same change that alters a boundary, a shared convention, or the
module map. A convention introduced by one feature and reused by the next belongs
here; a choice that is genuinely local to one feature stays in that feature's
`plan.md`. A durable decision with alternatives and consequences belongs in an
ADR, with a pointer added to the section 10 table.
