# Research: News and Financial-Report RAG

**Feature**: `006-news-document-rag`
**Status**: Complete for fixture-mode implementation. This is the first
feature to implement anything in `finvera-ai`, so several decisions here
are cross-feature and are also recorded as ADRs where durable.

Format: `Decision / Rationale / Alternatives considered / Risks and
validation`.

## R-001: Module and package placement

**Decision**: A new `finvera-be` module, `research` (matching SRS §26's
own name), layered per ADR-0007 (`domain`, `service`, `repository`,
`entity`, `controller`, `dto`, plus a `provider` package for the
`finvera-ai` HTTP client), owning `ResearchDocument`, `NewsArticle`, and
their metadata. In `finvera-ai`, use the package structure
`finvera-ai/AGENTS.md` already prescribes: `app/features/document/`
(parsing/chunking/ingestion orchestration), `app/features/rag/`
(retrieval, reranking, synthesis), `app/infrastructure/llm/` (Gemini
generation and embedding adapters), `app/infrastructure/qdrant/`
(collection access), `app/infrastructure/loaders/` (PDF/text extraction).

**Rationale**: Documents/articles are owner-written data with no existing
owner, exactly the same reasoning Feature 005's research R-001 used for
its own new `portfolio` module. `finvera-ai`'s structure is not a new
decision — it is already specified in its own `AGENTS.md`; this feature is
simply the first to populate it, so following that structure exactly
(rather than inventing a different one) is the correct default.

**Alternatives considered**: Extending `stock` with document/news data —
rejected; this is owner-curated content unrelated to `stock`'s reference
data, and Feature 005 already established that new owner-scoped data gets
its own module.

## R-002: Owner-scoping reuse

**Decision**: `research` reuses Feature 005's exact owner-scoping pattern
(`owner_id` column, service-layer comparison against the session's
authenticated owner id, mismatch treated identically to "not found" —
Feature 005 research R-002) rather than inventing a second pattern.

**Rationale**: SRS §36.4 explicitly lists "documents" alongside
portfolios/watchlists as private-by-default resources; the pattern that
already satisfies this for Feature 005 satisfies it here with zero new
design.

## R-003: Internal API boundary and authentication between `finvera-be` and `finvera-ai`

**Decision**: `finvera-be` is the only caller of `finvera-ai`'s internal
API; the browser never reaches it (SEC-001, Constitution Principle III,
unchanged from the existing boundary — this feature is simply the first to
exercise it). **The internal API is a bidirectional conversation, not a
one-directional client/server relationship** (research R-004's callback
design): `finvera-be` calls `finvera-ai` for ingestion submission,
retrieval, and synthesis, but `finvera-ai` also calls back into
`finvera-be`'s own `PATCH /internal/v1/ingestions/{id}/callback`.
**Every internal request, in either direction, MUST carry and have
validated the shared internal API key header** (`X-Internal-Api-Key`,
environment-sourced, no default value per `docs/ARCHITECTURE.md` §8) —
`finvera-be` validates it on the callback path exactly as `finvera-ai`
validates it on every path it hosts, never assuming a request is trusted
merely because it originated from the private network. Both `finvera-be`
and `finvera-ai` bind to `127.0.0.1`/the private network only, mirroring
the network posture ADR-0005 already established. The internal API is
versioned (`/internal/v1/...`) and defined in
`contracts/internal-api.openapi.yaml`.

**Rationale**: A shared internal key plus network-level restriction is the
simplest mechanism that still satisfies "no state change without
authorization" for a private, single-owner deployment with both services
on the same trusted network — mutual TLS or a full OAuth client-credentials
flow would be real added complexity (Constitution Principle VIII) with no
corresponding threat this deployment model actually faces yet (there is no
public ingress to `finvera-ai` regardless of the key, per ADR-0005).

**Alternatives considered**: mTLS between services — rejected as
disproportionate infrastructure for a private single-owner deployment;
revisit if `finvera-ai` is ever exposed beyond the private network. No
authentication at all, relying purely on network isolation — rejected;
defense in depth is cheap here and the key costs nothing extra to add.
Validating the key only on `finvera-ai`'s own endpoints and trusting the
callback path by network position alone — rejected; an unauthenticated
callback would let anything reachable on the private network forge an
ingestion result (fabricated "READY" chunk content) without ever touching
the real parsing/chunking/embedding pipeline, directly undermining the
citation-trust chain AI-001/AI-002 depend on. The key is validated
identically in both directions for exactly this reason.

**Risks/validation**: `tasks.md`'s security tests must exercise a missing/
invalid `X-Internal-Api-Key` against `finvera-be`'s own callback endpoint,
not only against `finvera-ai`'s endpoints — a one-directional test suite
would not have caught this gap.

## R-004: Asynchronous ingestion without a broker

**Decision**: `finvera-be` persists a submitted document/article as
`PENDING` and calls `POST /internal/v1/ingestions` on `finvera-ai`, which
accepts the job, returns immediately, and processes it on a background
task (FastAPI `BackgroundTasks`, no queue/broker process). When processing
finishes (or fails), `finvera-ai` calls back
`PATCH /internal/v1/ingestions/{researchItemId}/callback` on `finvera-be`
with the terminal state and either the chunk/embedding results summary or
a failure reason. `finvera-be` never polls; `finvera-ai` never writes
PostgreSQL directly (it only calls back through `finvera-be`'s own API).

**Rationale**: SRS §43 keeps Kafka non-default absent a demonstrated
event-driven requirement, and this deployment's scale (one owner,
realistically low document/article volume) does not create one. A
callback avoids polling overhead and keeps `finvera-ai` stateless with
respect to business truth (Constitution Principle III's own framing of
`finvera-ai`), since it never writes the authoritative status itself.

**Alternatives considered**: `finvera-be` polling a `finvera-ai` job-status
endpoint — rejected; adds unnecessary request volume and latency for no
benefit over a callback at this scale. A message broker — rejected per
SRS §43 absent a demonstrated need.

**Risks/validation**: A callback that never arrives (e.g., `finvera-ai`
crashes mid-job) must not leave an item silently stuck in `PROCESSING`
forever — a bounded processing timeout in `finvera-be`
(`finvera.research.ingestion-timeout`, default **10 minutes** — generous
enough for a large PDF's parse/chunk/embed/classify sequence at this
deployment's realistic document sizes, while still short enough that the
owner is never left wondering for hours) marks a job `FAILED` with a
`PROCESSING_TIMEOUT` reason if no callback arrives within that window.
Fixture/integration tests must cover this path explicitly (T012 in
`tasks.md`).

## R-005: Embedding model, chunking, and PDF extraction

**Decision**:

- **Embedding**: a Gemini-family embedding model (ADR-0008), accessed via
  `finvera-ai/app/infrastructure/llm/`, using its document-embedding task
  type for indexed chunks and its query-embedding task type for retrieval
  queries (asymmetric embedding). The exact generally-available model
  version and output vector dimensionality are pinned in
  `contracts/rag-v1.md` at implementation time (this file records the
  decision to use the model family; the plan's contract records the exact
  pin, per ADR-0001's own precedent of separating "which platform" from
  "which exact verified version").
- **PDF extraction**: `pypdf` (pure Python, permissive license, minimal
  dependency footprint) for born-digital PDF text extraction. A PDF that
  yields no extractable text (e.g., scanned images with no text layer)
  fails ingestion per FR-004 — OCR is explicitly out of scope
  (spec.md Assumptions).
- **Chunking**: fixed-size chunking by token count (target ~500-800
  tokens per chunk, ~15% overlap between consecutive chunks), tracking the
  source page number (documents, from PDF page boundaries) or paragraph
  index (news articles, which have no pages) as the chunk's location
  metadata for citation (DATA-003).

**Rationale**: `pypdf` is the simplest library that satisfies "extract
text from a born-digital PDF," with no C-extension/system-dependency
footprint to manage in the `finvera-ai` container, consistent with
Constitution Principle VIII. Fixed-size overlapping chunking is the
standard, well-understood RAG default; overlap prevents a fact from being
silently split across a chunk boundary with no chunk containing the whole
statement. Page/paragraph tracking is what SRS §29's own citation example
("FPT Annual Report 2025, Page 87, Business Results") requires.

**Alternatives considered**: `pdfplumber`/`unstructured` for
layout-aware extraction (tables, columns) — rejected for v1 as materially
heavier dependencies for a capability (structured table extraction) this
feature's requirements do not demand; revisit if plain financial-report
tables prove unreadable as flattened text. Semantic/recursive chunking
(splitting on section headers) — rejected for v1 as more complex to
implement correctly across heterogeneous report formats; fixed-size
chunking with overlap is the defensible simpler default.

**Risks/validation**: `contracts/rag-v1.md`'s required test-vector table
must cover a multi-page PDF whose known-answer passage spans two adjacent
chunks (proving overlap prevents information loss) and a scanned/no-text
PDF (proving `FAILED` with a reason, never a fabricated empty `READY`).

## R-006: Qdrant collection schema

**Decision**: One Qdrant collection, `research_chunks_v1`, cosine
distance, one point per chunk. Payload (filterable metadata, mirroring
`finvera-ai/AGENTS.md`'s required fields): `owner_id`, `research_item_id`,
`item_type` (`DOCUMENT`/`NEWS_ARTICLE`), `symbol` (nullable), `document_type`
or `news_category`, `publication_date`/`published_at`, `page_or_section`
(informational only — see below), `content_hash`, `embedding_version`.
The point's vector is the only retrieval-relevant payload beyond the
filter fields; chunk text itself is **not** duplicated into Qdrant's
payload — retrieval returns chunk ids, and `finvera-be` reads the
authoritative text from PostgreSQL (DATA-002, Constitution Principle II:
Qdrant is never the only copy).

**`page_or_section` is Qdrant-side informational metadata only, never the
value a response is built from.** `research_chunk.page_number`/
`paragraph_index` (integers, `data-model.md`) are the single authoritative
source for a citation's location; `finvera-ai`'s `/internal/v1/retrieve`
response does **not** return a formatted location string at all —
`finvera-be` always formats `location` itself from its own
already-authoritative `research_chunk` row (the same row it fetches for
`content_text` anyway, so this costs nothing extra). This avoids two
parallel representations of the same fact (a Qdrant-formatted string and a
Postgres integer) ever being able to drift apart.

**Rationale**: `owner_id` in the payload lets every query be filtered to
one owner's own points at the vector-search layer itself, not only after
retrieval — a defense-in-depth complement to SEC-001, not a replacement
for it (PostgreSQL-side ownership checks still gate every read). The
remaining fields are exactly what FR-006's filtering (symbol, type/
category, date range) and DATA-003's citation requirement need — no extra
field is speculative.

**Alternatives considered**: Storing chunk text in Qdrant's payload for a
faster read path — rejected; it would create a second, driftable copy of
content PostgreSQL already owns, violating Constitution Principle II for
no measured benefit at this corpus size. Returning `page_or_section` as a
formatted string from `/internal/v1/retrieve` and letting `finvera-be` use
it directly — rejected; it would create exactly the same class of
drift risk (two representations of one fact) for zero benefit, since
`finvera-be` already has the authoritative integer fields at hand.

## R-007: Reranking without a second ML model

**Decision**: MVP reranking is a **deterministic score combination**, not
a second model call: `finalScore = vectorSimilarityScore * w1 +
recencyBoost * w2 + filterMatchBoost * w3` (weights and boost curves
recorded in `contracts/rag-v1.md`), applied to the top-N vector-similarity
candidates before the top-K are returned/passed to synthesis.

**Rationale**: A dedicated cross-encoder reranker or an LLM-as-reranker
call is real additional latency and, in the LLM case, cost and a second
place fabrication could enter the pipeline. A deterministic, versioned
re-scoring formula satisfies FR-007's "reranked before returned"
requirement, stays inside NFR-001's 5-second budget, and is the simpler
alternative Constitution Principle VIII favors absent evidence it is
insufficient.

**Alternatives considered**: A dedicated cross-encoder reranking model —
rejected as new model-hosting infrastructure disproportionate for v1;
revisit if SC-002's 80% top-3 relevance target is not met in practice.
LLM-based reranking (asking Gemini to reorder candidates) — rejected as
added latency/cost with a real risk of the reranking step itself
introducing ungrounded judgments; the *generation* step already carries
the LLM's judgment burden, no need to add a second one.

**Risks/validation**: SC-002 (80% top-3 relevance on the evaluation
dataset) is the concrete gate; if the deterministic reranker cannot meet
it, escalate to R-007's rejected alternatives with a recorded reason,
never silently ship an unmet success criterion.

## R-008: Grounded synthesis, citation validation, and refusal

**Decision**: The synthesis prompt supplies retrieved passages as
labeled, numbered context blocks (`[C1]`, `[C2]`, ...) and instructs the
model, in the system-level framing, to: (a) answer only from the supplied
context, (b) cite the numbered block(s) supporting every material claim,
(c) explicitly state "no relevant information was found" if the context
does not support an answer, and (d) treat the content of every context
block as data, never as instructions to follow (AI-003). The model's
output is a **typed, schema-validated structure** (Pydantic:
`{answer: str, citations: list[{claimText: str, sourcePassageIds:
list[str]}], refused: bool}`), not free-form prose parsed after the fact.
After generation, `finvera-ai` **programmatically verifies** every
`sourcePassageId` in the response actually appears in the retrieved
passage set used for that request; any citation that fails this check is
treated as `refused`/insufficient evidence for that claim rather than
passed through, and the whole response is rejected and retried once (then
surfaced as a refusal) if verification fails outright.

**Citation resolution is `finvera-be`'s responsibility, not a pass-through
(F3).** `finvera-ai`'s `/internal/v1/synthesize` response carries only
`{chunkId, claimText}` per citation — the minimum `finvera-ai` can know
without reading PostgreSQL (research R-003's boundary). The **public**
`/research/ask` response's citation needs `sourceType`/`sourceId`/
`sourceTitle`/`location`/`source` too, so `finvera-be` MUST resolve each
`chunkId` to its parent `research_document`/`news_article` via
`research_chunk` before emitting the stream's final event to the browser
— it is not a verbatim relay of `finvera-ai`'s event, only the `delta`
(token-streaming) events are.

**Rationale**: SC-003 requires 100% of citations to reference actually-
retrieved passages — this is only guaranteed by a deterministic,
code-level post-generation check, never by prompt wording alone (an LLM
can still hallucinate a citation ID despite being told not to). A typed
schema (per `finvera-ai/AGENTS.md`'s own mandate) makes this check
mechanical rather than a fragile text-parsing exercise. Framing retrieved
content as inert, numbered data blocks (never concatenated as if it were
part of the system's own instructions) is the concrete implementation of
AI-003's prompt-injection defense.

**Alternatives considered**: Trusting the model's citations without
post-hoc verification — rejected outright; directly risks failing SC-003
and AI-001's grounding requirement, the single most safety-critical
guarantee this feature makes.

**Risks/validation**: `contracts/rag-v1.md`'s required test-vector table
must include a fixture where the model is coerced (via an adversarial
context block) into citing a passage id that was not actually retrieved,
proving the post-hoc check strips or refuses it rather than passing it
through.

## R-009: News classification

**Decision**: Entity/symbol extraction, sector, sentiment, potential
impact, and category (Company/Sector/Market/Macro/Regulation) are produced
by the same Gemini generation call used for synthesis infrastructure
(a separate, structured, schema-validated request per article — not a
second model/provider), run as part of the async ingestion pipeline
(R-004) after chunking/embedding. Each field carries its own confidence;
a field below a stated confidence floor is stored as `UNDETERMINED`
(FR-014), never a guessed value.

**Rationale**: No deterministic NLP classifier exists in this codebase,
and building/hosting one is exactly the kind of infrastructure Constitution
Principle VIII asks to avoid absent a demonstrated need; Gemini is already
the approved LLM provider (ADR-0002) and classification/sentiment
labeling is calibrated interpretation, not a "critical financial
calculation" Constitution Principle I forbids the LLM from doing (unlike
signals, risk scores, or P/L, which Feature 004/005 correctly keep
deterministic).

**Alternatives considered**: A dedicated sentiment-classification model —
rejected as disproportionate infrastructure for v1; revisit only if
Gemini-based classification proves measurably unreliable.

## R-010: Fixture and evaluation strategy

**Decision**: A small, versioned, hand-curated evaluation set (mirroring
Feature 004 R-009's direct-construction approach, not scraped real data):
5-10 short fixture documents/articles with known content, a matching set
of evaluation queries with their independently-verified correct source
passage(s) (for SC-002/SC-003), 2-3 "no relevant content" queries (for
FR-008/FR-010/AI-002 refusal testing), and 2-3 adversarial fixture
documents containing embedded prompt-injection attempts (for SC-004).

**Rationale**: Constitution Principle VI requires AI functionality be
evaluated on versioned datasets for retrieval quality, citation validity,
groundedness, refusal, and prompt-injection resistance — this is that
dataset, sized appropriately for a private single-owner MVP rather than a
production-scale benchmark.

## R-011: Raw content storage

**Decision**: `finvera-be`'s PostgreSQL stores, per document: the original
uploaded bytes (`bytea`, for audit and future reprocessing with a better
parser) and the extracted plain text (`text`, the actual working corpus
chunking/embedding operate on). Per news article, only the owner-pasted
body text is stored (there is no "original file"). No object storage
(S3-compatible or otherwise) is introduced.

**Rationale**: PostgreSQL is already the authoritative store for every
other feature's data (Constitution Principle III); a private single-owner
deployment's document volume does not justify a second storage system
(Constitution Principle VIII). Keeping the original bytes alongside the
extracted text means a future parser improvement can reprocess without
asking the owner to re-upload.

**Alternatives considered**: Object storage (S3-compatible) for original
files — rejected as unjustified new infrastructure at this scale; revisit
if document volume or size ever makes `bytea` storage impractical.
Discarding the original bytes after extraction — rejected; it would make
a future re-parse (e.g., after fixing a `pypdf` extraction bug) require
the owner to re-upload everything, which is unnecessary friction the
`bytea` column avoids for a small marginal storage cost.

## R-012: Public API shape

**Decision**: `finvera-be` exposes owner-only REST endpoints under
`/api/v1/research/documents`, `/api/v1/research/news`,
`/api/v1/research/retrieve`, and `/api/v1/research/ask`, following the
same session/CSRF/problem-details conventions Features 001-005 already
established (`docs/ARCHITECTURE.md` §5). `/ask` is the only endpoint that
may stream (Server-Sent Events or chunked response) to satisfy NFR-002's
"begin streaming... within 15 seconds"; `/retrieve` is a plain synchronous
JSON response bounded by NFR-001.

**Rationale**: Matches the existing API convention set exactly; no new
pattern is introduced beyond the SSE/streaming shape, which is the
established way to satisfy "begin streaming... within N seconds" for a
generation endpoint without holding the whole response until completion.

## R-013: Idempotent submission

**Decision**: `POST /research/documents` and `POST /research/news` both
require a client-supplied `Idempotency-Key` header (the same mechanism
Feature 005's research R-011 established for transaction recording, reused
rather than reinvented). `research_document`/`news_article` each carry an
`idempotency_key` column, unique per `(owner_id, idempotency_key)`
(`data-model.md`). Replaying an already-used key for the same owner is
rejected with `409 DUPLICATE_SUBMISSION`, referencing the original item's
id, before any ingestion job is submitted — never silently creating a
second corpus entry and never silently treating the retry as a no-op
success. The frontend client generates one key per logical submit action
(one upload/paste attempt) and reuses it only across that action's own
retries.

**Rationale**: spec.md's own edge case ("A document/article submission is
retried after a network failure — no duplicate ingestion job or corpus
entry is created") and `quickstart.md`'s matching expectation have no
achievable mechanism without a client-supplied key: nothing about a
document's or article's own fields (title, body, source) is guaranteed
unique — the owner may legitimately submit two genuinely different items
that happen to share a title, or (for news) may want to log the same
real-world article twice deliberately. Matching on content fields would
therefore either miss real retries or reject legitimate resubmissions;
only an explicit per-attempt key resolves the ambiguity, the same
reasoning Feature 005's research R-011 already established for this exact
class of problem.

**Alternatives considered**: Content-hash deduplication (rejecting a
submission whose extracted text hashes identically to an existing item) —
rejected; it cannot run until *after* parsing completes (asynchronously),
so it cannot prevent the duplicate *ingestion job* itself, only clean up
after the fact, and it would still incorrectly block a deliberate
legitimate re-log of the same real-world article. No idempotency
mechanism, relying on the owner to notice and delete duplicates manually —
rejected; directly contradicts the explicit spec.md edge case.

**Risks/validation**: `contracts/public-api.openapi.yaml`'s submission
endpoints and `data-model.md`'s unique constraint must both be exercised
by a fixture that replays the same key (rejected) and a different key with
otherwise-identical fields (accepted as a genuine second item).
