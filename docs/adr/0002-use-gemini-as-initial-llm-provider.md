# ADR-0002: Use Gemini as the Initial LLM Provider

**Status**: Accepted  
**Date**: 2026-08-17  
**Decision owners**: Finvera maintainers  
**Related specs**: Future AI Analyst and RAG feature specifications

## Context

Finvera requires an LLM for grounded research assistance, document summarization,
and explanation of deterministic market outputs. The SRS previously allowed
"Gemini or another LLM provider", leaving the initial provider unresolved.

The product decision is to use Gemini as the initial LLM provider. The exact
model, model version, region, quota, pricing tier, prompt design, and SDK are
not chosen by this ADR. Finvera's Python AI service remains the internal
integration boundary; browser clients never call an LLM provider directly.

Embedding is intentionally a separate decision. An embedding model determines
retrieval quality, vector dimensions, indexing cost, re-indexing strategy, and
Vietnamese financial-document recall. It cannot be selected solely because the
LLM provider is Gemini.

## Decision Drivers

- Establish one initial LLM integration target for AI/RAG feature planning.
- Preserve Finvera's deterministic-analysis-first and evidence-based AI
  principles.
- Avoid coupling the browser or Spring Boot public API to a provider SDK.
- Keep embedding selection evidence-driven rather than provider-driven.

## Considered Options

### Option A: Leave the LLM Provider Unspecified

This preserves maximum flexibility but blocks concrete provider research,
security review, quota planning, and contract design for the first AI feature.

### Option B: Gemini as the Initial LLM Provider

This gives the project a concrete initial target while retaining a provider
adapter boundary for future replacement. It requires careful provider-specific
research before implementation, including data handling, regional availability,
rate limits, cost, and model capability evaluation.

## Decision

Finvera MUST use **Gemini as its initial LLM provider** for the internal Python
AI service.

This decision does not select a Gemini model or version. A feature plan using
Gemini MUST select a generally available model version and record its quality,
cost, latency, quota, privacy, and fallback rationale in `research.md`.

All LLM access MUST remain behind an AI-service provider adapter with typed,
validated request and response contracts. Spring Boot remains the public API,
authorization, and quota boundary; the browser MUST NOT receive provider
credentials or invoke Gemini directly.

**Embedding provider/model: undecided.** The first RAG feature plan MUST
evaluate and select it separately; this ADR neither requires nor excludes a
Gemini embedding model.

## Consequences

### Positive

- AI/RAG planning has a concrete initial LLM target.
- The provider boundary preserves future replacement or multi-provider routing.
- The choice aligns with the SRS's evidence-based AI architecture and keeps
  financial calculations outside the LLM.

### Negative / Trade-offs

- Gemini-specific limits, availability, pricing, and data-handling terms become
  implementation dependencies that must be monitored.
- A provider adapter and controlled evaluation process are required before user
  features can rely on LLM output.
- Embedding selection remains a deliberate open decision and blocks final RAG
  index/schema implementation until resolved.

### Risks and Mitigations

- **Ungrounded or unsafe output**: use allowlisted tools, typed outputs,
  evidence/citation requirements, prompt-injection controls, and evaluation
  datasets.
- **Provider outage or quota exhaustion**: define timeout, bounded retry,
  graceful degradation, and user-visible unavailable states in each AI feature
  plan.
- **Sensitive data disclosure**: minimize data sent to Gemini, exclude secrets
  and private user data unless authorized, and document retention/data-handling
  decisions before integration.
- **Poor Vietnamese financial retrieval**: benchmark embeddings separately on
  versioned Vietnamese financial-report and news queries before selection.

## Migration and Rollback

No code migration is required because no provider integration has been
implemented. If Gemini fails the approved quality, privacy, availability, or
cost criteria, create a superseding ADR before replacing it; preserve the
provider adapter and versioned AI contracts to minimize impact on callers.

Changing the embedding model is a separate migration: it requires a versioned
index, backfill/re-index plan, retrieval evaluation, and rollback strategy in
the RAG feature plan.

## Validation

- The first Gemini feature plan MUST verify the selected model is generally
  available and document capabilities, quotas, latency, pricing, regional
  availability, privacy/data handling, and fallback behavior.
- AI evaluations MUST measure groundedness, citation validity, structured-output
  validity, refusal behavior, and prompt-injection/tool-abuse resistance.
- The embedding decision MUST compare candidates on a versioned Vietnamese
  financial retrieval dataset using retrieval quality, latency, cost, vector
  dimension/storage impact, filtering compatibility, and re-indexing effort.
- Review this ADR before direct provider SDK use outside `finvera-ai/` or any
  browser-to-provider integration.

