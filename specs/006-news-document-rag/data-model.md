# Data Model: News and Financial-Report RAG

**Feature**: `006-news-document-rag`
**Owner**: `finvera-be` / `research` module (new — research R-001) for
metadata and text; `finvera-ai` / Qdrant collection `research_chunks_v1`
(research R-006) for the rebuildable vector index.
**System of record**: PostgreSQL. Qdrant is a derived, rebuildable index
(Constitution Principle II) and MUST NOT hold any content not also in
PostgreSQL.
**Migration**: forward-only Flyway, starting at `V006`.

## Relationship to Features 001-005

This feature **reuses without modification**:

| Existing table/interface | Role here |
|---|---|
| `market_instrument` (Feature 001/002) | Optional symbol association for a document; the many-to-many set of symbols a news article mentions. |
| `OwnerProperties.id` (auth module) | The configured owner identity every `owner_id` column is checked against (Feature 005 research R-002, reused unmodified). |
| Feature 005's owner-scoping enforcement pattern | Reused directly for every `research` table (research R-002) — no new ownership model. |

No Feature 001-005 table is altered.

## Enumerations

| Type | Values | Notes |
|---|---|---|
| `IngestionStatus` | `PENDING`, `PROCESSING`, `READY`, `FAILED` | FR-003. |
| `DocumentType` | `ANNUAL_REPORT`, `QUARTERLY_REPORT`, `FINANCIAL_REPORT`, `ECONOMIC_REPORT`, `INVESTOR_PRESENTATION`, `CORPORATE_DISCLOSURE`, `OTHER` | SRS §26. |
| `NewsCategory` | `COMPANY`, `SECTOR`, `MARKET`, `MACRO`, `REGULATION` | SRS §24. |
| `Sentiment` | `POSITIVE`, `NEUTRAL`, `NEGATIVE` | SRS §25; paired with `Applicability` for the undetermined case (FR-014). |
| `ImpactLevel` | `LOW`, `MEDIUM`, `HIGH` | SRS §25; paired with `Applicability`. |
| `Applicability` | `DEFINED`, `NOT_APPLICABLE`, `MISSING` | Reused from Feature 002 `stock.domain.model.StockTypes`, not redefined. A classification field the AI could not confidently determine (FR-014) is `MISSING` with a `LOW_CONFIDENCE` reason, never a guessed value. |
| `ResearchItemType` | `DOCUMENT`, `NEWS_ARTICLE` | Discriminates chunk parentage and Qdrant payload `item_type` (research R-006). |

## `research_document`

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `owner_id` | UUID | Research R-002; indexed. |
| `symbol_id` | UUID nullable | FK `market_instrument`; optional (spec.md Assumptions — a document need not name a supported symbol, e.g. a macro report). |
| `title` | varchar(300) | Not blank. |
| `document_type` | varchar(32) | `DocumentType`. |
| `year` | smallint | Reporting year. |
| `quarter` | smallint nullable | `1`-`4`; null for annual/non-quarterly documents. |
| `source` | varchar(200) | Owner-declared publisher/source name. |
| `publication_date` | date | The document's own stated publication date. |
| `original_filename` | varchar(300) nullable | Null for a pasted-text-only submission (no file). |
| `original_content` | bytea nullable | The uploaded file's raw bytes (research R-011); null for pasted text. |
| `content_mime_type` | varchar(100) nullable | E.g. `application/pdf`; null for pasted text. |
| `extracted_text` | text nullable | Populated once parsing succeeds; the working corpus text chunking reads. |
| `ingestion_status` | varchar(16) | `IngestionStatus`. |
| `ingestion_failure_reason` | varchar(200) nullable | Required when `ingestion_status = FAILED`. |
| `submitted_at` | timestamptz | UTC; owner submission time. |
| `processed_at` | timestamptz nullable | UTC; set when `ingestion_status` reaches `READY` or `FAILED`. |
| `idempotency_key` | varchar(100) | Not null (research R-013); unique per `(owner_id, idempotency_key)` — a repeat is rejected as `DUPLICATE_SUBMISSION` before any ingestion job is submitted. |

Checks: `(ingestion_status = 'FAILED') = (ingestion_failure_reason IS NOT NULL)`;
`quarter IS NULL OR quarter BETWEEN 1 AND 4`;
`(original_content IS NOT NULL) = (original_filename IS NOT NULL)`.

Indexes: `(owner_id, idempotency_key)` unique (research R-013).

## `news_article`

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `owner_id` | UUID | Research R-002; indexed. |
| `title` | varchar(300) | Not blank. |
| `source` | varchar(200) | Owner-declared source name. |
| `published_at` | timestamptz | UTC; the article's own stated published time. |
| `body` | text | Owner-pasted article body. |
| `reference_url` | varchar(2000) nullable | Display-only (FR-002); never fetched by the system. |
| `category` | varchar(16) nullable | `NewsCategory`; AI-derived (FR-013). |
| `category_applicability` | varchar(32) | `Applicability`; `MISSING` when undetermined. |
| `sentiment` | varchar(16) nullable | `Sentiment`; AI-derived. |
| `sentiment_applicability` | varchar(32) | `Applicability`. |
| `impact_level` | varchar(16) nullable | `ImpactLevel`; AI-derived. |
| `impact_applicability` | varchar(32) | `Applicability`. |
| `sector` | varchar(100) nullable | AI-derived; `NULL` when undetermined (paired conceptually with `Applicability` at the API layer). |
| `ingestion_status` | varchar(16) | `IngestionStatus`. |
| `ingestion_failure_reason` | varchar(200) nullable | Required when `ingestion_status = FAILED`. |
| `submitted_at` | timestamptz | UTC. |
| `processed_at` | timestamptz nullable | UTC. |
| `idempotency_key` | varchar(100) | Not null (research R-013); unique per `(owner_id, idempotency_key)`. |

Checks: `(ingestion_status = 'FAILED') = (ingestion_failure_reason IS NOT NULL)`;
`(category IS NOT NULL) = (category_applicability = 'DEFINED')`;
`(sentiment IS NOT NULL) = (sentiment_applicability = 'DEFINED')`;
`(impact_level IS NOT NULL) = (impact_applicability = 'DEFINED')`.

Indexes: `(owner_id, idempotency_key)` unique (research R-013).

## `news_article_symbol`

Many-to-many: one article may mention several symbols (entity extraction,
FR-013); one symbol may be mentioned by many articles.

| Field | Type | Constraints |
|---|---|---|
| `news_article_id` | UUID | FK `news_article`; part of primary key. |
| `instrument_id` | UUID | FK `market_instrument`; part of primary key. |

## `research_chunk`

One row per parsed, embedded unit of text from exactly one document or
article (the same "exactly one target" discipline Feature 004's
`strategy_signal_input` and Feature 006's own design both rely on for
clarity over an untyped polymorphic reference).

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `owner_id` | UUID | Denormalized from the parent, for query convenience and defense-in-depth filtering; MUST always match the parent's `owner_id`. |
| `item_type` | varchar(16) | `ResearchItemType`. |
| `research_document_id` | UUID nullable | FK `research_document`. |
| `news_article_id` | UUID nullable | FK `news_article`. |
| `chunk_index` | smallint | 0-based order within the parent item. |
| `page_number` | smallint nullable | Documents only (research R-005); null for news. |
| `paragraph_index` | smallint nullable | News only; null for documents. |
| `content_text` | text | The chunk's own text (duplicated from `extracted_text`/`body` for direct citation display without re-slicing the parent). |
| `content_hash` | char(64) | SHA-256 of `content_text`, for change detection on reprocessing. |
| `vector_point_id` | UUID | The corresponding Qdrant point id (research R-006) — the explicit link between the authoritative row and its derived vector. |
| `embedding_version` | varchar(64) | E.g. `gemini-embedding-v1`; a future model change is a new version, not a silent reinterpretation (ADR-0008). |
| `created_at` | timestamptz | UTC. |

Checks: `num_nonnulls(research_document_id, news_article_id) = 1`;
`(item_type = 'DOCUMENT') = (research_document_id IS NOT NULL)`;
`(item_type = 'NEWS_ARTICLE') = (news_article_id IS NOT NULL)`;
`(item_type = 'DOCUMENT') = (page_number IS NOT NULL)`;
`(item_type = 'NEWS_ARTICLE') = (paragraph_index IS NOT NULL)`.

Indexes: `(research_document_id, chunk_index)`, `(news_article_id,
chunk_index)`; unique `(vector_point_id)`; `(owner_id)` for the
defense-in-depth filter research R-006 describes.

**Deleting a `research_document`/`news_article` cascades to its
`research_chunk` rows** (`ON DELETE CASCADE`), and the delete
service call collects the affected `vector_point_id`s and issues a Qdrant
point-delete for all of them in the same operation (DATA-004, FR-016) —
never leaving an orphaned vector behind.

## Qdrant collection `research_chunks_v1` (not a PostgreSQL table)

| Field | Role |
|---|---|
| Point id | Equals `research_chunk.vector_point_id`. |
| Vector | The embedding (ADR-0008), dimensionality per the pinned model version. |
| Payload: `owner_id` | Defense-in-depth query filter (research R-006). |
| Payload: `research_item_id`, `item_type` | Points back to `research_document.id`/`news_article.id`. |
| Payload: `symbol`, `document_type`/`news_category` | FR-006 filtering. |
| Payload: `publication_date`/`published_at` | FR-006 date-range filtering. |
| Payload: `page_or_section`, `content_hash`, `embedding_version` | Informational/reconciliation support only — **not** the source a citation's `location` is built from (research R-006's F4 resolution); `research_chunk.page_number`/`paragraph_index` are the sole authoritative source. Never the chunk text itself. |

Rebuild procedure: for any/all `research_chunk` rows, recompute the
embedding from `content_text` and upsert the point at the same
`vector_point_id` — the collection can be dropped and fully reconstructed
from PostgreSQL alone at any time.

## Read models (never stored beyond the request/response)

| Read model | Composition | Endpoint |
|---|---|---|
| `RetrievalResult` | Ranked, reranked `research_chunk` matches for a query, each joined to its parent's citation metadata | `POST /api/v1/research/retrieve` |
| `AnswerResult` | An LLM-synthesized answer plus its verified citations (research R-008), computed from one `RetrievalResult` | `POST /api/v1/research/ask` (streamed) |

Neither is persisted as conversation history (spec.md Assumptions);
observability/telemetry (latency, token counts, model/prompt/retrieval
version) is recorded separately per `finvera-ai/AGENTS.md`, without
logging the query or answer content itself (SEC-002).
