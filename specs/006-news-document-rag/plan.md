# Implementation Plan: News and Financial-Report RAG

**Feature Directory**: `specs/006-news-document-rag`
**Date**: 2026-08-20
**Spec**: [spec.md](spec.md)
**Status**: Implemented — complete and fully validated across all three tiers (`finvera-ai`, `finvera-be`, `finvera-fe`). All 50 tasks in `tasks.md` verified.

## Summary

Deliver Finvera's sixth vertical slice, and the first to implement
anything in `finvera-ai`: an owner-curated research corpus (uploaded
financial-report documents and pasted news articles), asynchronously
parsed, chunked, and embedded into a rebuildable Qdrant index while
PostgreSQL keeps the authoritative text and metadata; ranked, cited
passage retrieval; an LLM-synthesized, citation-verified answer over that
corpus; and AI-derived news classification. News ingestion is owner-
curated text entry only — no crawler, no paid news API. Question answering
is scoped strictly to the owner's own corpus; the multi-tool AI Analyst
that combines this with structured stock/portfolio data is Feature 7.

This feature reuses Feature 005's owner-scoping pattern exactly (the
platform's first owner-scoped data outside `portfolio`) and follows
`finvera-ai/AGENTS.md`'s already-prescribed package structure — no new
architectural boundary is introduced, this is simply the first feature to
exercise the browser-to-Spring-to-AI boundary the constitution already
requires.

## Technical Context

**Affected projects**: `finvera-be` (new `research` module),
`finvera-ai` (first real implementation: `app/features/document`,
`app/features/rag`, `app/infrastructure/llm`, `app/infrastructure/qdrant`,
`app/infrastructure/loaders`), `finvera-fe` (new `research` feature
folder).

**Languages and versions**: unchanged — Java 21/Spring Boot 4.1.x,
TypeScript 5/React 19/Vite, Python 3.13/FastAPI, PostgreSQL 17, Qdrant
(version pinned in `finvera-ai`'s dependency manifest at implementation
time).

**Primary dependencies**: new to `finvera-ai`: a Qdrant client, `pypdf`
(research R-005), a Gemini SDK for generation and embedding (ADR-0002,
ADR-0008). New to `finvera-be`: an HTTP client for the internal API
(`contracts/internal-api.openapi.yaml`).

**Storage and state**: PostgreSQL (new `research` tables, forward Flyway
from `V006`) is authoritative; Qdrant (`research_chunks_v1`, research
R-006) is a rebuildable derived index. No existing table altered.

**Interfaces**: new owner-only public REST endpoints under
`/api/v1/research/*` (`contracts/public-api.openapi.yaml`); a new
internal, network-restricted API between `finvera-be` and `finvera-ai`
(`contracts/internal-api.openapi.yaml`); a new behavioral contract for
chunking/retrieval/citation (`contracts/rag-v1.md`).

**Testing**: unit tests for chunking/rerank-scoring/citation-verification
(`rag-v1`), integration tests for ingestion persistence and the
callback/timeout path, Spring MVC/security contract tests (including the
platform's first internal-API-key negative tests), Vitest component
tests, Playwright P1-P3, and a versioned AI evaluation dataset (research
R-010) for retrieval quality, groundedness, citation validity, refusal,
and prompt-injection resistance (Constitution Principle VI).

**Performance goals**: NFR-001 — 95% of retrieval requests within 5
seconds. NFR-002 — 95% of question-answering requests begin streaming
within 15 seconds. NFR-003 — ingestion is asynchronous and never blocks
other request processing.

**Availability and degradation**: an unavailable embedding provider or
Qdrant degrades ingestion (item stays `PENDING`/reaches `FAILED` with a
reason) and retrieval (`503`, never a silent empty-corpus result) without
affecting Features 001-005 (Constitution Principle VII).

**Scale and scope**: one owner, an unbounded number of documents/
articles realistically in the low hundreds for a private single-owner
deployment, English/Vietnamese mixed text, no multi-user delivery.

**Open technical unknowns**: none blocking — `research.md` resolves
module placement, the internal API/auth boundary, the async/callback
architecture, embedding/chunking/PDF-extraction, the Qdrant schema,
reranking, grounded-synthesis/citation-verification, news classification,
raw-content storage, and the public API shape before this plan was
written, per Constitution Principle V. ADR-0008 records the embedding
provider decision as a durable, cross-feature choice.

## Constitution Check

### Pre-research gate

- [x] **I. Deterministic finance core** — this feature computes no
  financial value; `rag-v1` explicitly scopes the LLM to explanation/
  synthesis over document text, never a computed indicator/ratio/P&L
  (FR-011), consistent with Constitution Principle I's LLM boundary.
- [x] **II. Evidence, provenance, temporal truth** — every citation
  references a stable document/article identity and location (DATA-003);
  Qdrant is explicitly rebuildable and never the only copy of accepted
  content (DATA-002, research R-006).
- [x] **III. Explicit boundaries** — the browser calls only `finvera-be`
  (SEC-001); `finvera-ai` remains internal, reached only over the new
  versioned internal API (research R-003), never given direct PostgreSQL
  access (research R-004's callback design).
- [x] **IV. Security, privacy, responsible decision support** — owner-only
  access reuses Feature 005's enforcement (research R-002); answers are
  labeled decision support, never a guarantee or instruction (spec.md
  Assumptions).
- [x] **V. Specification and traceability before code** — spec, research
  (12 decisions), data model, and three contracts exist; no unresolved
  clarification marker; ADR-0008 records the embedding provider choice.
- [x] **VI. Risk-based testing** — retrieval quality, citation validity,
  groundedness, refusal, and prompt-injection resistance each have a named
  evaluation approach (research R-010) before implementation, as this
  principle requires for AI functionality specifically.
- [x] **VII. Resilience and observability** — degradation paths (provider/
  Qdrant unavailable, missing callback/timeout) are defined; ingestion is
  explicitly asynchronous so heavy AI work cannot block other features
  (NFR-003).
- [x] **VIII. Modular simplicity** — no message broker (research R-004,
  consistent with SRS §43); no dedicated reranking model (research R-007);
  no OCR/second document-format pipeline (spec.md Out of Scope); no object
  storage beyond PostgreSQL `bytea` (research R-011); the new `research`
  module and `finvera-ai` package structure are the minimum needed, the
  latter already specified by `finvera-ai/AGENTS.md` rather than invented
  here.

**Result**: PASS.

### Post-design gate

Completed after Phase 1 design (2026-08-20), reviewed against the actual
data model and contracts produced:

- [x] **I. Deterministic finance core** — confirmed unchanged; `rag-v1`
  U-4 explicitly scopes its one floating-point exception (the rerank
  score) to a non-authoritative ranking signal, never a financial value.
- [x] **II. Evidence, provenance, temporal truth** — `data-model.md`'s
  `research_chunk.vector_point_id` makes the PostgreSQL-to-Qdrant link
  explicit and rebuildable; `rag-v1`'s citation-verification algorithm
  (steps 1-5) is the concrete mechanism, not merely a stated intent.
- [x] **III. Explicit boundaries** — `contracts/internal-api.openapi.yaml`
  confirms `finvera-ai` never reads/writes PostgreSQL directly in any
  path (ingestion results and retrieval both flow back through
  `finvera-be`); the public contract confirms the browser never receives
  a `finvera-ai` URL, credential, or direct reference.
- [x] **IV. Security, privacy, responsible decision support** — the
  internal API requires `X-Internal-Api-Key` with no default value
  (research R-003); `SEC-002` and `rag-v1` U-3 together bound what content
  reaches the external LLM/embedding provider and how it is framed.
- [x] **V. Specification and traceability before code** — `data-model.md`
  and all three contracts trace every FR/DATA/AI requirement to a
  concrete field, rule, or endpoint.
- [x] **VI. Risk-based testing** — the Test and Evaluation Strategy table
  below names a level for every requirement group, including the
  citation-verification boundary and prompt-injection fixtures.
- [x] **VII. Resilience and observability** — the processing-timeout
  design (research R-004) closes the one real failure mode a callback
  pattern introduces (a callback that never arrives); this was designed
  up front, not discovered after implementation.
- [x] **VIII. Modular simplicity** — the design introduces exactly the
  module/package structure already specified, one new Qdrant collection,
  no new broker, no second AI provider, no dedicated reranker or OCR
  pipeline. `Complexity Tracking` below records the one genuinely new
  infrastructure class this feature adds (a second deployable service
  actually doing work, and a vector database actually storing data) and
  why it is justified now rather than deferred.

**Result**: PASS.

## System Context and Boundaries

```text
                 owner (browser, existing session)
                              |
      POST/GET/DELETE /research/documents, /research/news
      POST /research/retrieve, /research/ask (streamed)
                              |
                              v
              finvera-be / research (new module)
                              |
      write: research_document, news_article, research_chunk
      read: market_instrument (optional symbol association)
                              |
        internal API, X-Internal-Api-Key, private network only
                              v
              finvera-ai (first real implementation)
        app/features/document  -> parse (pypdf) / chunk / embed
        app/features/rag       -> retrieve / rerank / synthesize
        app/infrastructure/llm -> Gemini generation + embedding (ADR-0002/0008)
        app/infrastructure/qdrant -> research_chunks_v1 collection
                              |
                              v
        Qdrant (rebuildable index) <-- callback --> finvera-be
                              |
                              v
     React SPA: new "Research" feature (documents, news, ask/retrieve)

TCBS / Vnstock / Redis / Kafka: no path in this feature
```

### Ownership

| Concern | Owner | Reason |
|---|---|---|
| Document/article metadata, ownership, ingestion status | `finvera-be/.../research/entity`, `.../service` | PostgreSQL remains the authoritative store (Constitution Principle III); reuses Feature 005's ownership pattern. |
| Parsing, chunking, embedding, classification, retrieval, reranking, synthesis (`rag-v1`) | `finvera-ai/app/features/document`, `app/features/rag` | The AI/RAG boundary `finvera-ai/AGENTS.md` already assigns this to `finvera-ai`. |
| Provider adapters (Gemini generation/embedding) | `finvera-ai/app/infrastructure/llm` | Keeps SDK objects out of feature code (`finvera-ai/AGENTS.md`). |
| Vector storage | Qdrant, accessed only via `finvera-ai/app/infrastructure/qdrant` | Rebuildable index, never authoritative (DATA-002). |
| REST mapping, security, internal-API client | `finvera-be/.../research/controller`, `.../provider` | Spring remains the sole public boundary. |
| Presentation and formatting | `finvera-fe/src/features/research` | UI formats contract data; never recomputes a citation or score. |
| Instrument identity | Feature 001/002 | Reused for optional symbol association. |

### Interface Changes

| Interface | Change | Version and compatibility | Contract Artifact |
|---|---|---|---|
| Public REST | Add owner-only document/news/retrieve/ask endpoints | Additive under `/api/v1` | [public-api.openapi.yaml](contracts/public-api.openapi.yaml) |
| Internal REST (new) | `finvera-be` <-> `finvera-ai` ingestion, retrieval, synthesis, vector deletion | New, versioned under `/internal/v1`, network-restricted | [internal-api.openapi.yaml](contracts/internal-api.openapi.yaml) |
| Behavioral rules | Add one versioned rule set | `rag-v1` | [rag-v1.md](contracts/rag-v1.md) |
| Database | Add `research_document`, `news_article`, `news_article_symbol`, `research_chunk` | Forward Flyway from `V006`; no existing table altered | [data-model.md](data-model.md) |
| Vector index (new) | Qdrant collection `research_chunks_v1` | Rebuildable from PostgreSQL at any time | [data-model.md](data-model.md) |
| Decision record | Embedding provider | New | [ADR-0008](../../docs/adr/0008-use-gemini-embeddings-for-rag-v1.md) |

## Phase 0: Research

Complete in [research.md](research.md). Thirteen decisions: module/package
placement, owner-scoping reuse, the internal API boundary and
**bidirectional** authentication (research R-003, hardened during
pre-implementation analysis — see below), asynchronous ingestion without a
broker (callback pattern, with a 10-minute default processing timeout),
embedding/chunking/PDF-extraction, the Qdrant collection schema (with
`page_or_section` demoted to informational-only, Postgres kept as the
sole location authority), reranking without a second model, grounded
synthesis and citation verification (including which service resolves a
citation's public-facing shape), news classification, the
evaluation-fixture strategy, raw-content storage, the public API shape,
and idempotent submission (research R-013, reusing Feature 005's R-011
pattern).

## Phase 1: Design and Contracts

### Data model

[data-model.md](data-model.md): four additive PostgreSQL tables (all
owner-scoped) plus one Qdrant collection. Load-bearing constraints: a
document/article's `bytea`/text content is never duplicated as the only
copy anywhere; every `research_chunk` links to exactly one parent and one
Qdrant point; deleting a parent cascades to its chunks and triggers a
Qdrant point deletion in the same operation.

### Contracts

- **rag-v1.md** — chunking rules, the deterministic rerank formula,
  citation-verification algorithm, refusal rule, and required test
  vectors.
- **internal-api.openapi.yaml** — the `finvera-be`/`finvera-ai`
  conversation: ingestion submission and callback, retrieval, streamed
  synthesis, bulk vector deletion.
- **public-api.openapi.yaml** — the browser-facing document/news/
  retrieve/ask endpoints.

### Security, privacy, and AI safety

- Reuse the existing owner session and CSRF controls exactly (SEC-001);
  the browser never reaches `finvera-ai` (Constitution Principle III,
  `docs/ARCHITECTURE.md` B-1).
- **New**: an internal API key (`X-Internal-Api-Key`, research R-003)
  plus network restriction gate **every** internal call **in both
  directions** — `finvera-be`-to-`finvera-ai` and `finvera-ai`'s own
  callback into `finvera-be` — the platform's first internal
  service-to-service authentication requirement. A pre-implementation
  analysis pass found the callback direction initially undocumented and
  untested; research R-003 and `tasks.md` T006/T012/T045 now cover it
  explicitly, closing what would otherwise have let anything on the
  private network forge a fabricated "READY" ingestion result.
- Idempotent submission (research R-013): a required `Idempotency-Key`
  header plus a `(owner_id, idempotency_key)` unique constraint prevents a
  retried upload/paste from creating a duplicate corpus entry or
  ingestion job, reusing Feature 005's exact pattern.
- Retrieved document/news content is always framed as untrusted, inert
  data (`rag-v1` U-3); citation verification (`rag-v1` steps 1-5) is a
  deterministic, code-level check, not merely a prompt instruction — this
  is the concrete implementation of AI-001/AI-002/AI-003.
- SEC-002/`rag-v1` U-1 bound what reaches the external provider: only
  chunk text already accepted into this owner's own corpus, never a
  secret, token, or another owner's/feature's data.

### Observability and operations

Matches Features 002-005's established baseline (global
`CorrelationIdFilter`, shared `ProblemDetailsAdvice`, failure-class
visibility in the response itself) on the public API. **New for this
feature**: `finvera-ai` records model/prompt/retrieval rule version and
latency/token/error metrics per `finvera-ai/AGENTS.md`'s own mandate,
without logging query or answer content (SEC-002) — a distinct
requirement from Features 001-005's read-only endpoints, since this is
the first feature calling an external generative model. A stuck
`PROCESSING` item past the configured timeout is a health signal, not
merely a UI state (research R-004).

### Test and evaluation strategy

| Requirement IDs | Test level | Fixture/dataset | Expected evidence |
|---|---|---|---|
| FR-001 to FR-004 | Unit, integration | Valid PDF, valid pasted text, unparseable PDF | Correct terminal status; `FAILED` reason never fabricated |
| FR-005 to FR-008 | Unit, integration | `rag-v1` required test-vector fixtures | Correct ranked/reranked/filtered/empty results |
| FR-009, FR-010, AI-001, AI-002 | Unit, eval | Research R-010 evaluation dataset | Grounded citations; refusal on no-evidence queries |
| FR-011 | Unit, acceptance review | Structured-value question fixtures | Identified as out of scope, never approximated |
| FR-012, AI-003 | Eval | Prompt-injection fixtures | 0% behavior deviation (SC-004) |
| FR-013, FR-014, AI-004 | Unit, eval | News fixtures with known/ambiguous classification | Correct labels; `MISSING` on low confidence |
| FR-016, DATA-004 | Integration | Delete-then-query | Zero remaining chunks/vectors |
| FR-018, `rag-v1` U-6 | Property, replay | Identical corpus/query run twice | Identical retrieved set; wording may vary |
| DATA-001 to DATA-003 | Unit, integration | Ingestion + citation fixtures | Distinct timestamps; PostgreSQL-authoritative; stable citation identity |
| SEC-001, SEC-002 | Security integration, negative (first internal-API tests, both directions) | Missing session/CSRF/internal key on both `finvera-ai`-hosted and `finvera-be`-hosted (callback) internal endpoints | Only the owner/authorized service succeeds |
| FR-001, FR-002, SC-001 (idempotency, research R-013) | Integration | Same `Idempotency-Key` replayed vs. a different key with identical fields | Replay rejected `DUPLICATE_SUBMISSION`, zero additional corpus entry/job; different key accepted as genuine |
| NFR-001, NFR-002, NFR-003 | Integration, timing | Representative fixture corpus | p95 within baseline; ingestion never blocks other requests |
| NFR-004 | Component, Playwright, manual | All status/sentiment/impact states | Non-colour indicator |

Dataset version: `rag-eval-v1` (research R-010). Groundedness/citation
threshold: 100% (SC-003). Retrieval quality threshold: 80% top-3 relevance
(SC-002). Refusal threshold: 100% on no-evidence fixtures. Injection
resistance threshold: 0% deviation (SC-004). Model variability is
controlled by testing the retrieved/cited passage **set**, not exact
generated wording (`rag-v1` U-6) — human review is required only if an
automated eval run's citation-validity rate regresses below 100%.

### Rollout, migration, and rollback

1. Apply Flyway `V006`. Stand up the Qdrant `research_chunks_v1`
   collection.
2. Deploy `finvera-ai` behind the private network only, confirm
   `X-Internal-Api-Key` is required end to end, then deploy `finvera-be`'s
   new endpoints and the frontend.
3. Gate: `finvera-ai`'s private-network-only reachability must be
   confirmed (mirroring Feature 001's T051 Tailscale runbook) before
   `/research/retrieve`/`/research/ask` are enabled for real use.

**Rollback**: no destructive migration to reverse; application code rolls
back to a version compatible with `V006`. Qdrant's collection can be
dropped and fully rebuilt from PostgreSQL at any time (research R-006) —
it is never a rollback risk. A defective `rag-v1` rule creates `rag-v2`
with a new `embedding_version`/rule version, requiring re-indexing, never
rewriting existing chunk history.

### Quickstart acceptance

[quickstart.md](quickstart.md) defines prerequisites (including the new
`finvera-ai` runtime and Qdrant dependency), commands, P1-P3 acceptance
paths, degraded paths, the platform's first internal-API authorization
checks, and the AI evaluation gate.

## Project Structure

### Feature documentation

```text
specs/006-news-document-rag/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── rag-v1.md
│   ├── internal-api.openapi.yaml
│   └── public-api.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md   (generated by /speckit-tasks)
```

### Source code affected

```text
finvera-be/src/
├── main/java/com/minhnb/finvera_be/research/    # new module
│   ├── service/       # ResearchDocumentService, NewsArticleService,
│   │                  # RetrievalService, AskService, ingestion callback handler
│   ├── provider/      # finvera-ai internal API HTTP client
│   ├── controller/    # ResearchController(s)
│   ├── dto/
│   ├── entity/, repository/   # 4 new tables
│   └── resources/db/migration/V006__*.sql
└── test/...

finvera-ai/app/                       # first real implementation
├── api/                              # FastAPI routers (internal/v1)
├── core/                             # settings, internal-API-key auth dependency
├── features/
│   ├── document/                     # parse, chunk, ingestion orchestration
│   └── rag/                          # retrieve, rerank, synthesize, citation verification
├── infrastructure/
│   ├── llm/                          # Gemini generation + embedding adapters
│   ├── qdrant/                       # collection access
│   └── loaders/                      # pypdf-based extraction
└── main.py

finvera-fe/src/features/
└── research/    # documents, news, retrieve, ask (new)
```

**Structure decision**: a new `finvera-be` module (research R-001)
because no existing module owns owner-written research content; the
`finvera-ai` package layout is not a new decision but the first real use
of the structure `finvera-ai/AGENTS.md` already specifies. One new
frontend feature folder, consistent with Features 002-005's own
one-folder-per-surface convention.

## Traceability Summary

| Requirement IDs | Design artifact | Test evidence | Planned task group |
|---|---|---|---|
| FR-001 to FR-004 | `data-model.md` `research_document`/`news_article`; research R-004/R-005 | Unit, integration | Foundation, US1 |
| FR-005 to FR-008 | `rag-v1.md` retrieval/rerank section | Unit, integration | US1 |
| FR-009 to FR-012 | `rag-v1.md` citation verification; `internal-api.openapi.yaml` `/synthesize` | Unit, eval | US2 |
| FR-013, FR-014 | `internal-api.openapi.yaml` `NewsClassification`; research R-009 | Unit, eval | US3 |
| FR-015 | `public-api.openapi.yaml` list/filter endpoints | Integration | US3 |
| FR-016, DATA-004 | `data-model.md` cascade delete + `/vectors` endpoint | Integration | Foundation |
| FR-017 | research R-002 (Feature 005 reuse) | Security integration | Foundation |
| FR-018 | `rag-v1.md` U-6 | Property, replay | US2 |
| DATA-001 to DATA-003 | `data-model.md` | Unit, integration | Foundation |
| SEC-001, SEC-002 | research R-002/R-003; both contracts | Security integration, negative | Foundation |
| AI-001 to AI-004 | `rag-v1.md` | Eval | US2, US3 |
| NFR-001 to NFR-003 | research R-004/R-006/R-007 | Timing | Cross-cutting |
| NFR-004 | Non-colour status contract | Component, Playwright, manual | Each story |

## Complexity Tracking

| Addition | Why Required Now | Simpler Alternative Rejected | Approval/ADR | Removal or Review Trigger |
|---|---|---|---|---|
| First real workload in `finvera-ai` (a second deployable service actually doing work) | The constitution already reserves the AI/RAG boundary for `finvera-ai`; this is the first feature whose scope requires it, not a new architectural decision | Implementing parsing/embedding inside `finvera-be` | Constitution Principle III; `finvera-ai/AGENTS.md` (pre-existing) | N/A — this is the boundary's intended first use, not a deviation |
| A new Qdrant collection actually storing vectors | FR-005/SRS-RAG-02/03 require a vector retrieval index; Qdrant was already the constitution's designated store, unused until now | A PostgreSQL full-text/trigram search substitute | Constitution §3, `docs/ARCHITECTURE.md` decision index (pre-existing) | Revisit only if retrieval quality never approaches SC-002 with a vector approach, which would itself require a new ADR |
| Callback-based async ingestion (research R-004) rather than a broker | SRS §43 requires a demonstrated event-driven need before Kafka; this deployment's scale does not create one | Kafka or another broker | research R-004 | Revisit if ingestion volume or multi-consumer needs ever materialize |
| Deterministic score-combination reranking rather than a dedicated model (research R-007) | Satisfies FR-007 without new model-hosting infrastructure | A cross-encoder reranker or LLM-based reranking | research R-007 | Revisit if SC-002's 80% top-3 target is not met |

No message broker, second LLM/embedding provider, dedicated reranker
model, OCR pipeline, or object-storage system is introduced.

## Open Items Carried Into Tasks

1. The exact generally-available Gemini generation and embedding model
   versions (ADR-0008 records the family; the plan's contract needs the
   exact pin) are a foundation task, mirroring ADR-0001's own
   "verified generally available patch" discipline.
2. ~~The processing-timeout configuration value~~ — **resolved**:
   `finvera.research.ingestion-timeout`, default 10 minutes (research
   R-004). The exact `finvera.research.max-upload-size-bytes` value
   remains a task-level decision, constrained to: bounded, never
   unbounded, consistent with Feature 005's own precedent for configurable
   bounds (`max-performance-history-span-days`).
3. The exact frontend navigation placement for the new "Research" section
   is a task-level UI decision, constrained to: formatting only, never
   recomputing a citation, score, or classification.
4. Whether `finvera-ai` needs its own Alembic-equivalent schema
   versioning for the Qdrant collection (e.g., a documented recreation
   script) versus purely code-driven collection setup is a task-level
   implementation decision, constrained to: the collection must be fully
   reconstructable from PostgreSQL alone (research R-006), by whatever
   mechanism.
