# Feature Specification: News and Financial-Report RAG

**Feature Directory**: `006-news-document-rag`
**Created**: 2026-08-20
**Status**: Draft
**SRS References**: Section 24 (News Aggregation), 25 (News Intelligence),
26 (Research Document Management), 27 (RAG System), 28 (Vector Database),
29 (RAG Retrieval), 33 (Structured Data vs RAG), 36.1 (performance), 36.4
(security), 36.7 (provenance/temporal integrity), 36.8 (privacy/retention),
47 (MVP-6), 54 (MVP success criteria), 58 (Requirements Index)
**SRS Requirement IDs**: SRS-NWS-01, SRS-NWS-02, SRS-DOC-01, SRS-RAG-01,
SRS-RAG-02, SRS-RAG-03.
Explicitly deferred: SRS-AIA-01/SRS-AIA-02 (Section 30/31, AI Analyst and
multi-tool orchestration combining this capability with Market/Stock/
Technical/Portfolio tools — MVP-7; this feature's question-answering uses
the Research/RAG capability alone, never combined with structured data
tools); SRS-JRN-01 (Section 23, Investment Journal/conversation
persistence — Post-MVP, so no multi-turn conversation history is stored
here); SRS-AIA-05 (Section 34, Daily Market Briefing — Post-MVP).
**Input**: User description: "News and Financial-Report RAG (Feature 6,
MVP-6). The owner privately curates a personal research corpus: uploads
financial report documents (PDF or pasted text) with structured metadata
(company, symbol, document type, year, quarter, source, publication date),
and manually submits news articles as pasted text (title, source,
published time, body) rather than through any automated crawler or paid
news API. The Python AI service asynchronously parses, cleans, chunks, and
embeds each item into Qdrant (a rebuildable index, never the source of
truth; PostgreSQL keeps the authoritative text and metadata, owner-scoped
using Feature 005's ownership pattern). The owner can retrieve ranked,
cited passages from their own corpus (filtering, semantic ranking,
reranking, page/section citation) and ask a question to get an
LLM-synthesized answer grounded in and citing those same passages,
refusing rather than fabricating when nothing relevant is found. News
articles are additionally classified (entities, mentioned symbols, sector,
sentiment, potential impact, category: Company/Sector/Market/Macro/
Regulation). Retrieved content is always treated as untrusted data, never
as instructions. No automated news aggregation/crawling, no multi-tool AI
Analyst orchestration combining this with structured stock/portfolio data
(Feature 7), no persisted multi-turn conversation history. Same private
single-owner deployment as Features 001-005; this is the first feature to
implement anything in finvera-ai."

## Scope Summary *(mandatory)*

In the same private single-owner deployment established by Features
001-005, Finvera's owner needs somewhere to keep and actually use their
own research material — annual reports, quarterly filings, investor
presentations, economic notes, and news they have read — instead of
letting it sit unsearched in a folder. The owner adds a document or a news
article once; from then on they can ask "what does this report actually
say about X" or "what have I read about Y" and get back the exact passages
that support the answer, with enough citation detail (document, page or
section, source) to go verify it themselves — never a plausible-sounding
answer that cannot be traced back to something the owner actually put in.

This is deliberately narrow. It answers questions **from the owner's own
curated corpus only** — it does not aggregate news automatically, does not
compute or restate structured financial values (price, indicators,
ratios), and does not combine this capability with the stock, portfolio,
or strategy data Features 001-005 already provide; that broader,
multi-tool conversational analyst is Feature 7. What this feature commits
to is narrower and more foundational: the ingestion pipeline, the vector
index, and a trustworthy, cited retrieval and answer path — the piece
every later AI capability will be built on top of, not replace.

This is also the first feature that puts anything into `finvera-ai`.
PostgreSQL remains the authoritative store of every document's and
article's full text and metadata, owned by `finvera-be`, reusing Feature
005's owner-scoping pattern exactly; Qdrant holds only a rebuildable vector
index that could be deleted and regenerated from PostgreSQL at any time.
The browser continues to talk to `finvera-be` only.

### In Scope

- Owner-uploaded research documents (PDF or pasted plain text) with
  structured metadata: company/symbol (optional), document type (annual
  report, quarterly report, financial report, economic report, investor
  presentation, corporate disclosure, other), year, quarter (optional),
  source, and publication date.
- Owner-submitted news articles as pasted text (not fetched or scraped by
  the system) with title, source, published time, body, and an optional
  reference URL stored for display only.
- Asynchronous processing (parse, clean, chunk, embed, index into Qdrant)
  for every submitted document or article, with an owner-visible status
  (`PENDING`, `PROCESSING`, `READY`, `FAILED` with a stated reason).
- Retrieval of ranked, cited passages from the owner's own corpus, with
  filtering by symbol, document type/category, and date range, and
  reranking before results are returned.
- Question answering: an LLM-synthesized answer grounded in and citing
  retrieved passages, refusing (stating no relevant information was
  found) rather than fabricating when the corpus has nothing relevant.
- News-specific classification: entities/mentioned symbols, sector,
  sentiment, potential impact, and one category (Company/Sector/Market/
  Macro/Regulation), shown as AI-derived interpretation with an
  undetermined state when confidence is insufficient.
- Browsing and deleting owned corpus items, with deletion removing derived
  chunks and vector-index entries, not only the metadata row.
- Treating every retrieved document/article as untrusted data: its content
  is never interpreted as an instruction to the system or the model.

### Out of Scope

- Automated news aggregation, crawling, or any paid/free news API
  integration — every article is manually entered by the owner (confirmed
  scope decision; a future feature may add live aggregation behind its own
  ADR, mirroring how Features 001/003/004 separated live TCBS from offline
  Vnstock bootstrap).
- Multi-tool AI Analyst orchestration that combines this capability with
  Market, Stock, Technical, Fundamental, Valuation, Portfolio, or
  Screening tools in one conversational experience (SRS §31, MVP-7).
- Persisted multi-turn conversation history; each retrieval or
  question-answering request is independent (SRS-JRN-01, Post-MVP).
- OCR for scanned-image PDFs and non-PDF/non-text document formats
  (DOCX, XLSX, images); a document with no extractable text fails
  ingestion with a stated reason rather than silently producing empty
  content.
- Structured financial computation or restatement (price, technical
  indicators, fundamental ratios, portfolio figures) from document text;
  this capability answers from document content only (SRS §33) and states
  when a question requires structured data it does not have.
- Daily market briefing (SRS §34, Post-MVP).
- Multi-user or public delivery; the same private single-owner deployment
  model as Features 001-005 applies unchanged.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ingest a Document and Find Cited Passages (Priority: P1)

As the owner, I want to upload a research document and later search my own
corpus for passages relevant to a question, each one showing exactly which
document and location it came from, so that I can find what I actually
have on a topic without rereading everything, and can verify any passage
against its source.

**Why this priority**: Nothing else in this feature is possible or
trustworthy without a working ingestion pipeline and a retrieval path that
proves its citations are real — this is the smallest slice that is both
independently valuable (grounded search over one's own documents) and the
foundation every later story builds on.

**Independent Test**: Upload a document with known content, wait for it to
reach `READY`, then run a retrieval query whose answer is known to be in
that document; verify the returned passages include the correct one with
an accurate document/page-or-section citation. Upload a document with no
extractable text and verify it reaches `FAILED` with a stated reason.

**Acceptance Scenarios**:

1. **Given** a valid PDF or pasted-text document with metadata, **When**
   the owner submits it, **Then** it appears immediately with `PENDING`
   status, transitions through `PROCESSING`, and reaches `READY` once
   parsed, chunked, and embedded.
2. **Given** a document that reached `READY`, **When** the owner runs a
   retrieval query relevant to its content, **Then** the response includes
   the correct passage(s) with the source document's identity, location
   (page or section), and the document's own metadata.
3. **Given** a document with no extractable text (e.g., a scanned image
   with no text layer), **When** it is submitted, **Then** it reaches
   `FAILED` with a stated reason, never a fabricated or empty `READY`
   result.
4. **Given** a retrieval query filtered by symbol, document type, or date
   range, **When** the owner runs it, **Then** only passages from matching
   documents are returned.
5. **Given** a retrieval query with no sufficiently relevant passage in
   the corpus, **When** the owner runs it, **Then** the response states
   truthfully that nothing relevant was found, never a fabricated passage.
6. **Given** a document the owner no longer wants, **When** they delete
   it, **Then** its metadata, chunks, and vector-index entries are all
   removed, and it no longer appears in any retrieval result.

---

### User Story 2 - Ask a Question and Get a Cited Answer (Priority: P2)

As the owner, I want to ask a plain-language question and receive a
written answer that is actually grounded in my documents, with citations
for its material claims, so that I get a synthesized answer instead of a
list of raw passages to read myself — but one I can still verify.

**Why this priority**: This is meaningfully more useful than raw passage
retrieval (US1), but only trustworthy once retrieval itself is proven
correct — synthesis is a layer on top of US1's grounded passages, not a
replacement for them.

**Independent Test**: Ask a question whose answer is supported by an
already-`READY` document and verify the answer's material claims each cite
a real, retrieved passage. Ask a question with no relevant content in the
corpus and verify the system refuses rather than answering from general
knowledge.

**Acceptance Scenarios**:

1. **Given** a corpus containing a document that answers a question,
   **When** the owner asks it, **Then** the synthesized answer's material
   claims each cite a specific retrieved passage's document and location.
2. **Given** a question with no sufficiently relevant passage in the
   corpus, **When** the owner asks it, **Then** the system states that no
   relevant information was found rather than generating an answer from
   the model's general knowledge.
3. **Given** a question that asks for a structured financial value (e.g.,
   current price, an indicator, a computed ratio), **When** the owner asks
   it, **Then** the response identifies that this is outside what document
   retrieval can answer, rather than approximating or fabricating a
   number from document text.
4. **Given** a document in the corpus whose text contains an embedded
   instruction aimed at the system or the model, **When** a question
   retrieves a passage from it, **Then** the system's behavior is
   unaffected by that embedded instruction — the content is treated as
   data, never as a command.
5. **Given** an unchanged corpus and rule version, **When** the same
   question is asked twice, **Then** both answers cite the same retrieved
   passage set, even if the generated wording differs.

---

### User Story 3 - Submit and Understand News (Priority: P3)

As the owner, I want to add a news article and see it automatically
classified — which companies/symbols it concerns, its sector, its
sentiment, and its potential impact — and be able to retrieve and ask
questions about it the same way as a document, so that news becomes part
of the same searchable, citable corpus instead of a separate unstructured
pile.

**Why this priority**: News classification and retrieval reuse the exact
same ingestion and retrieval engine US1/US2 already deliver, applied to a
second content type — genuinely useful, but meaningfully additive rather
than foundational.

**Independent Test**: Submit a news article about a known company; verify
it is classified with entities/symbols, sector, sentiment, impact, and
category, and that it becomes retrievable and citable exactly like a
document.

**Acceptance Scenarios**:

1. **Given** a submitted news article, **When** it finishes processing,
   **Then** it shows extracted entities/mentioned symbols, sector,
   sentiment, potential impact, and one category (Company/Sector/Market/
   Macro/Regulation), each labeled as AI-derived interpretation.
2. **Given** an article whose sentiment or impact the system cannot
   confidently determine, **When** the owner views it, **Then** that field
   shows an undetermined state rather than a guessed value.
3. **Given** a news article and a document both relevant to the same
   query, **When** the owner retrieves or asks about it, **Then** both
   appear in results, each correctly attributed to its own source and
   type.
4. **Given** the owner's corpus, **When** they browse/filter by symbol,
   category, sentiment, or date, **Then** the result set matches exactly
   the items satisfying every applied filter.

### Edge and Failure Cases *(mandatory)*

- A document/article submission is retried after a network failure —
  no duplicate ingestion job or corpus entry is created.
- The embedding provider or Qdrant is unavailable during ingestion —
  affected items remain `PENDING`/`FAILED` with a stated reason; other
  Finvera features (market, stock, screener, strategy, portfolio) remain
  fully usable (Constitution Principle VII).
- The embedding provider or Qdrant is unavailable during retrieval —
  the response states retrieval is degraded/unavailable rather than
  silently returning stale or empty results as if the corpus were empty.
- A document is deleted while a retrieval request that already read its
  chunks is in flight — the in-flight response completes from a coherent
  snapshot; the item is fully gone from any subsequent request.
- A document/article contains text designed to make the model ignore its
  instructions, reveal internal prompts, or perform an unintended action —
  treated as inert data (AI-003).
- A retrieval/answer request from an identity other than the configured
  owner, or for another owner's corpus item (not applicable in this
  single-owner deployment today, but enforced identically to Feature 005).
- Two documents contain conflicting statements about the same fact — both
  are retrievable and citable; the system does not silently prefer one or
  merge them into a single unsupported claim.
- A very large document exceeds practical chunking/processing limits —
  fails with a stated reason rather than partially ingesting without
  disclosure.

## Requirements *(mandatory)*

Requirements describe observable behavior. Accepted IDs are stable and MUST
not be renumbered; removed requirements are deprecated with a reason.

### Functional Requirements

- **FR-001**: The system MUST let the owner submit a research document
  (PDF or pasted plain text) with metadata: optional company/symbol,
  document type, year, optional quarter, source, and publication date.
- **FR-002**: The system MUST let the owner submit a news article as
  pasted text with title, source, published time, body, and an optional
  reference URL that is stored for display only and never fetched by the
  system.
- **FR-003**: Every submitted document or article MUST be processed
  asynchronously (parse, clean, chunk, embed, index) without blocking any
  other request; the owner MUST be able to see its current status
  (`PENDING`, `PROCESSING`, `READY`, `FAILED`) at any time.
- **FR-004**: A document or article that cannot be parsed into usable text
  MUST reach `FAILED` with a stated reason; it MUST NOT silently
  disappear or reach `READY` with empty or fabricated content.
- **FR-005**: The system MUST let the owner retrieve passages relevant to
  a query from their own corpus, each result stating its source document/
  article identity, location (page or section for documents; article
  identity for news), and relevant metadata.
- **FR-006**: Retrieval MUST support filtering by symbol, document type or
  news category, and date range.
- **FR-007**: Retrieved candidates MUST be reranked before being returned,
  so the most relevant passages are not merely the first vector-similarity
  matches.
- **FR-008**: A query with no sufficiently relevant passage in the corpus
  MUST return a truthful empty/insufficient-evidence result, never a
  fabricated passage.
- **FR-009**: The system MUST let the owner ask a question and receive an
  LLM-synthesized answer whose material claims are each grounded in and
  cite a specific retrieved passage.
- **FR-010**: When no sufficiently relevant passage exists for a question,
  the system MUST refuse to answer from the model's general knowledge and
  MUST instead state that no relevant information was found.
- **FR-011**: A synthesized answer MUST NOT state, compute, or imply a
  structured financial value (price, technical indicator, fundamental
  ratio, portfolio figure) as an authoritative calculation; a question
  requiring one MUST be identified as outside this capability rather than
  approximated from document text.
- **FR-012**: The system MUST treat all retrieved document/news content as
  untrusted data; text inside a document or article MUST NOT be
  interpreted as an instruction to the system or the model, regardless of
  its phrasing.
- **FR-013**: Every processed news article MUST be classified with
  entities/mentioned symbols, sector, sentiment, potential impact, and one
  category (Company, Sector, Market, Macro, Regulation), each shown as
  AI-derived interpretation, not an authoritative fact.
- **FR-014**: A classification the system cannot determine with sufficient
  confidence MUST show an undetermined state, never a default or guessed
  value.
- **FR-015**: The owner MUST be able to browse and filter their corpus
  (documents and news, together or separately) by symbol, type/category,
  sentiment, and date.
- **FR-016**: The owner MUST be able to delete a document or news article;
  deletion MUST remove its derived chunks and vector-index entries, not
  only its metadata row.
- **FR-017**: No document, article, chunk, retrieval request, or answer
  request MUST be readable, creatable, modifiable, or deletable by any
  identity other than the configured owner, reusing Feature 005's
  owner-scoping enforcement rather than a new pattern.
- **FR-018**: Given an unchanged corpus and an unchanged retrieval rule
  version, repeated execution of the same query MUST retrieve the same
  ranked passage set; a synthesized answer's exact wording MAY vary, but
  every citation MUST come from that same passage set.

### Data and Financial Semantics

- **DATA-001**: Every document/article MUST retain its source, submission
  (ingestion) time, and its own publication/published time as distinct
  values.
- **DATA-002**: PostgreSQL MUST remain the authoritative store of every
  accepted document's/article's full text and metadata; Qdrant entries
  MUST be rebuildable from PostgreSQL and MUST NOT be the only copy of any
  accepted content (Constitution Principle II).
- **DATA-003**: Every cited passage MUST reference a stable document/
  article identity and location sufficient to relocate it in the source.
- **DATA-004**: Deleting a document/article MUST remove its derived
  vector-index entries within the same operation, consistent with SRS
  §36.8's data-rights baseline.

### Security and Privacy

- **SEC-001**: Document, article, retrieval, and question-answering
  endpoints MUST be accessible only to the single configured owner
  identity, under the same authenticated server-side session and CSRF
  controls Features 001-005 established; the browser MUST continue to call
  only `finvera-be`, never `finvera-ai` directly.
- **SEC-002**: The system MUST send only the minimum content needed for
  the invoked capability to the embedding/LLM provider, and MUST NOT
  include secrets, tokens, or another owner's/feature's private data in
  any prompt, embedding request, log, or telemetry.

### AI and Retrieval Behavior

- **AI-001**: Every synthesized answer MUST carry a citation for each
  material claim, identifying the specific source document/article and
  location it came from.
- **AI-002**: When retrieval finds no sufficiently relevant passage, the
  system MUST refuse or state insufficient evidence rather than answering
  from the model's general knowledge.
- **AI-003**: Retrieved document/news content MUST be defended as
  untrusted data; the system MUST resist a document or article whose text
  attempts to redirect, override, or issue instructions to the model or
  system.
- **AI-004**: News classification MUST be labeled as AI-derived
  interpretation and MUST show an undetermined state, not a guess, when
  confidence is insufficient.

### Non-Functional Requirements

- **NFR-001**: At least 95% of corpus retrieval (non-synthesized) requests
  MUST return within 5 seconds under normal operating conditions,
  consistent with SRS §36.1's screening/filtering baseline.
- **NFR-002**: At least 95% of question-answering requests MUST begin
  streaming or return within 15 seconds, consistent with SRS §36.1's
  interactive-AI-answer baseline.
- **NFR-003**: Document/article ingestion MUST run asynchronously and MUST
  NOT block or degrade any other feature's request processing.
- **NFR-004**: Ingestion status, sentiment, and impact states MUST be
  understandable without relying on color alone.

### Key Entities

- **Research Document**: An owner-uploaded report with structured
  metadata, ingestion status, ownership, and its extracted text/chunks.
- **News Article**: An owner-submitted article with metadata,
  AI-derived classification, ingestion status, ownership, and its
  extracted text/chunks.
- **Document Chunk**: One parsed, embedded unit of text from a document or
  article, with location metadata (page/section or paragraph), linked to
  its parent item and its vector-index entry.
- **Retrieval Result**: A query-time set of ranked, cited passages, or a
  synthesized answer with its supporting citations; not persisted as
  conversation history in this feature.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- **News ingestion is manual text entry only** (confirmed scope decision):
  the owner pastes an article's title, source, published time, and body;
  the system does not fetch, crawl, or scrape any URL server-side. A
  submitted reference URL is stored only for the owner's own display,
  never dereferenced by the system. This mirrors how Features 001/003/004
  separated a live provider from an offline/manual path, without
  requiring a paid news API or its own licensing research before this
  feature can ship.
- **Supported document input for this feature is PDF and pasted plain
  text.** OCR for scanned images and other formats (DOCX, XLSX) are out of
  scope; a document with no extractable text fails ingestion with a
  stated reason (FR-004) rather than silently producing an empty result.
- **A document or article is not required to reference a supported
  symbol.** Unlike Feature 005's transactions, research content
  legitimately includes macro/economic material with no company symbol at
  all; symbol association is optional, used for filtering when present.
- **No persisted multi-turn conversation history.** Each retrieval or
  question-answering request is independent; conversation/journal
  persistence is Post-MVP (SRS-JRN-01).
- **Embedding model/provider, reranking approach, and chunking strategy
  are unresolved** and MUST be benchmarked and selected in this feature's
  `research.md`, independent of the Gemini LLM-provider decision
  (ADR-0002) — this is the exact gap `docs/PROJECT_CONTEXT.md` already
  names as belonging to "the first RAG feature." SRS §33 constrains *what*
  this feature answers; the embedding stack is a *how* decision reserved
  for planning.
- **This is the first feature to implement anything in `finvera-ai`.** No
  new architectural boundary is introduced — the browser continues to call
  only `finvera-be`, which calls `finvera-ai` over a new versioned
  internal API — this feature is the first to actually use boundaries the
  constitution and Features 001-005 already established.
- **Owner-scoping reuses Feature 005's pattern exactly** (an `owner_id`
  column checked at the service layer, a mismatch treated identically to
  "not found") rather than inventing a second ownership model.
- Answers are decision support, not investment advice; a synthesized
  answer confers no fiduciary authority and does not instruct the owner to
  buy or sell (Constitution Principle IV), the same disclosure discipline
  Feature 004 established for signals.

### Dependencies

- Feature 005's owner-scoping enforcement pattern, reused rather than
  reimplemented.
- Feature 001-003's supported instrument/symbol universe, reused for
  optional symbol association and retrieval filtering.
- ADR-0002 (Gemini as the initial LLM provider) for answer synthesis.
- A new internal, versioned API contract between `finvera-be` and
  `finvera-ai`, an embedding/reranking provider decision, and a Qdrant
  collection schema — all to be resolved in this feature's `research.md`/
  `contracts/` before implementation, consistent with Constitution
  Principle I/V.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Across a versioned ingestion fixture set (valid PDF, valid
  pasted text, unparseable/no-text input), 100% of submissions reach the
  correct terminal status (`READY` or `FAILED` with a stated reason).
- **SC-002**: Across a versioned retrieval evaluation dataset with known
  correct source passages, at least 80% of evaluation queries return a
  correctly relevant passage among their top-3 results.
- **SC-003**: Across the same evaluation dataset, 100% of synthesized
  answers cite only passages that were actually retrieved for that query
  (no fabricated citation), and 100% of no-relevant-content cases produce
  a refusal rather than a fabricated answer.
- **SC-004**: Across prompt-injection fixture cases (documents/articles
  containing embedded instructions), 0% cause the system to deviate from
  its normal retrieval/answer behavior.
- **SC-005**: At least 95% of retrieval requests return within 5 seconds,
  and at least 95% of question-answering requests begin streaming or
  return within 15 seconds.
- **SC-006**: Deletion tests confirm 100% of deleted documents/articles
  have zero remaining chunks or vector-index entries afterward.
- **SC-007**: Authorization tests show only the configured owner can reach
  any document/article/retrieval/answer endpoint; no response, log, or
  export contains another owner's content, a credential, a token, or more
  provider-bound content than the invoked capability required.
- **SC-008**: Accessibility review confirms 100% of ingestion-status,
  sentiment, and impact states have a non-color indicator.

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001, FR-003 | US1 / Scenario 1 | SC-001 |
| FR-004 | US1 / Scenario 3 | SC-001 |
| FR-005, DATA-003 | US1 / Scenario 2 | SC-002 |
| FR-006 | US1 / Scenario 4 | SC-002 |
| FR-007 | US1 / Scenario 2 | SC-002 |
| FR-008 | US1 / Scenario 5 | SC-003 |
| FR-016, DATA-004 | US1 / Scenario 6 | SC-006 |
| FR-009, AI-001 | US2 / Scenario 1 | SC-003 |
| FR-010, AI-002 | US2 / Scenario 2 | SC-003 |
| FR-011 | US2 / Scenario 3 | SC-003 |
| FR-012, AI-003 | US2 / Scenario 4 | SC-004 |
| FR-018 | US2 / Scenario 5 | SC-002 |
| FR-002, FR-013, AI-004 | US3 / Scenario 1 | SC-001 |
| FR-014 | US3 / Scenario 2 | SC-001 |
| US3 dual-source attribution | US3 / Scenario 3 | SC-002 |
| FR-015 | US3 / Scenario 4 | SC-002 |
| DATA-001, DATA-002 | US1-US3 | SC-001, SC-006 |
| SEC-001, SEC-002 | Edge case (owner-only, provider data minimization) | SC-007 |
| NFR-001, NFR-002, NFR-003 | US1-US3 timing; edge cases | SC-005 |
| NFR-004 | US1-US3 | SC-008 |

