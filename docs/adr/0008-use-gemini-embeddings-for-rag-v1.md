# ADR-0008: Use Gemini Embeddings for RAG v1

**Status**: Accepted
**Date**: 2026-08-20
**Decision owners**: Finvera maintainer
**Related feature**: `006-news-document-rag`

## Context

ADR-0002 selects Gemini as Finvera's initial LLM provider but explicitly
leaves embedding model/provider undecided, requiring "the first RAG feature
plan" to evaluate and select it independently. Feature 006 is that feature:
it is the first to implement anything in `finvera-ai`, and its retrieval
quality, indexing cost, vector dimensionality, and Vietnamese financial-text
recall all depend on this choice before a Qdrant collection schema can be
finalized (`data-model.md`).

Embedding is a separate capability from generation. A provider can be a good
LLM and a mediocre embedder, or vice versa, so this decision is made on its
own evidence, not inherited from ADR-0002.

## Decision Drivers

- Retrieval quality for mixed Vietnamese/English financial text (SRS §29,
  FR-005 to FR-008).
- Minimizing new vendor/credential surface for a private, single-owner,
  cost-sensitive deployment (ADR-0005).
- Constitution Principle VIII (Modular Simplicity): prefer the least complex
  design that still satisfies correctness.
- Replaceability: `finvera-ai/AGENTS.md` requires provider SDK objects stay
  in `infrastructure/`, behind a typed port, so the embedding provider can be
  swapped without touching feature code.

## Considered Options

### Option A: Google's Gemini-family embedding model (`gemini-embedding-*`)

Uses the same Google AI vendor relationship, credentials, and billing account
ADR-0002 already established for generation — no second provider contract,
API key, or data-processing agreement to review. Google's Gemini embedding
family is trained for multilingual retrieval, including Vietnamese, and
supports task-typed embeddings (a distinct mode for indexing documents versus
embedding a query), which materially helps asymmetric retrieval quality.

### Option B: A self-hosted open-source multilingual embedding model

Full control, no per-call cost or external data transfer, but requires GPU or
CPU inference infrastructure, model hosting, and ongoing maintenance that is
disproportionate for a private single-owner MVP with a small corpus —
directly against Constitution Principle VIII, and against SRS §41's own
framing of embedding as an external-integration decision, not an
infrastructure-build decision.

### Option C: A third-party embedding API (e.g., OpenAI, Cohere, Voyage)

Some of these are purpose-built, well-regarded embedding providers. Rejected
for v1 because it adds a second external vendor relationship, credential,
rate limit, and data-processing/privacy review (SRS §36.8) on top of Gemini,
for a benefit that is not evidenced to be necessary at this feature's scale
and language mix. Revisit if Gemini embedding quality proves insufficient on
Feature 006's own evaluation dataset (`research.md` R-005, SC-002/SC-003).

## Decision

Finvera MUST use a **Gemini-family embedding model**, accessed through the
same Google AI provider adapter boundary `finvera-ai` already establishes for
generation (ADR-0002), as the embedding provider for RAG v1.

- The exact generally-available model version, output dimensionality, and
  task-type usage (document vs. query embedding) are recorded in Feature
  006's `research.md`/`contracts/rag-v1.md`, not fixed by this ADR.
- The embedding call MUST go through a typed port/protocol in
  `finvera-ai/app/infrastructure/llm/`, never called directly from feature
  code, so a future provider change does not require a Qdrant collection
  rebuild's logic to change beyond the adapter.
- A model or provider change that alters output dimensionality or semantics
  creates a new embedding rule version and requires re-indexing existing
  content; it MUST NOT silently reinterpret already-stored vectors under a
  new model (Constitution Principle I — versioned, reproducible rules).
- Embedding requests MUST NOT include secrets, credentials, or content
  belonging to a different owner (SEC-002).

## Consequences

### Positive

- One vendor relationship for both generation and embedding simplifies
  credential management, quota planning, and privacy review for the private
  MVP.
- Task-typed embeddings support better asymmetric (query-vs-document)
  retrieval than a generic single-mode embedding model.
- The adapter boundary keeps the door open to a provider change later
  without a feature-code rewrite.

### Negative / Trade-offs

- Ties embedding quality and availability to Google's roadmap and pricing
  alongside generation; a Gemini outage can degrade both capabilities
  simultaneously rather than only one.
- No independent embedding benchmark has been run against Vietnamese
  financial-report text specifically; Feature 006's evaluation dataset
  (SC-002/SC-003) is the first real evidence and may prompt a future
  reconsideration if recall is insufficient.
- Re-indexing is required if a future model/version change alters vector
  semantics or dimensionality.

## Reference

- ADR-0002 (Gemini as initial LLM provider)
- `specs/006-news-document-rag/research.md` R-005 (embedding model/version
  pin), `contracts/rag-v1.md` (grounding/citation rules)
