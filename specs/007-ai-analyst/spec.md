# Feature Specification: AI Analyst

**Feature Directory**: `007-ai-analyst`
**Created**: 2026-08-20
**Status**: Draft
**SRS References**: Section 14 (Screener natural-language conversion), 30 (AI
Analyst), 31 (AI Orchestration), 32 (AI Explanation), 33 (Structured Data vs
RAG), 36.1 (performance), 36.4 (security), 36.7 (provenance/temporal
integrity), 36.8 (privacy/retention), 47 (MVP-7), 54 (MVP success criteria),
58 (Requirements Index)
**SRS Requirement IDs**: SRS-AIA-01, SRS-AIA-02, SRS-AIA-03, SRS-AIA-04,
SRS-SCR-03.
Explicitly deferred: SRS-AIA-05 (Section 34, Daily Market Briefing —
Post-MVP); SRS-JRN-01 (Section 23, Investment Journal and persisted
conversation history — Post-MVP, so this feature stores no multi-turn
conversation thread).
**Input**: User description: "AI Analyst (Feature 7, MVP-7). A
tool-orchestrating conversational assistant that answers natural-language
investment questions (e.g. 'Phân tích FPT hiện tại', 'FPT đang overbought
không?', 'So sánh FPT và CMG', 'Rủi ro lớn nhất của FPT là gì?') by selecting
from an allowlisted set of tools — Market, Stock, Technical Analysis,
Fundamental Analysis, Valuation, Portfolio, News, Research/RAG, and
Screening — calling Features 001-006's existing deterministic
services/capabilities for every structured value, and Feature 006's RAG
capability for document/news content, then having the LLM synthesize and
explain the results in natural language without ever recomputing or
replacing a deterministic calculation. Every numeric/structured claim is
attributed to the tool call that produced it; every document/news claim
keeps Feature 006's citation. The AI Analyst also explains existing
deterministic outputs (signals, indicators, valuation classifications, risk
factors) in plain language using only the evidence the deterministic engine
already produced, and converts a natural-language screening criterion into
the structured filters Feature 003's deterministic screener engine actually
runs (SRS-SCR-03) — the LLM never filters or ranks stocks itself. No new
data capability, no persisted multi-turn conversation/journal (Post-MVP), no
daily briefing (Post-MVP), same private single-owner deployment as Features
001-006."

## Scope Summary *(mandatory)*

Features 001-006 already give the owner deterministic prices, indicators,
fundamentals, valuation, screening, strategy signals, risk factors,
portfolio analytics, and a cited document/news corpus — but reaching any of
it still means knowing which page or endpoint answers which question, and
combining two of them (e.g., a signal and a report's stated risk) means
doing it by hand. This feature adds a single conversational entry point that
understands a plain-language question, decides which of those existing
capabilities actually answer it, calls them, and returns one coherent,
attributed answer — never a second calculation engine, only a coordinator
and an explainer over the ones that already exist.

This is deliberately a thin orchestration layer, not a new source of truth.
Every structured number in an answer MUST come from calling the exact same
deterministic service Features 001-005 already expose, unmodified; every
document or news claim MUST come from Feature 006's Research/RAG capability,
citations included. The LLM's only jobs are choosing which tool(s) a
question needs, converting a natural-language screening criterion into the
structured filters Feature 003's engine already accepts, and turning
results/evidence into readable prose — never inventing, approximating, or
recomputing a financial value itself (Constitution Principle I).

The browser continues to talk only to `finvera-be`. `finvera-ai`'s
orchestrator — new in this feature — calls back into `finvera-be` through a
new internal, versioned, key-authenticated tool API for structured data
(extending Feature 006's bidirectional `X-Internal-Api-Key` pattern), and
reuses Feature 006's own internal retrieve/synthesize endpoints for the
Research/RAG tool. No new database, cache, or authoritative store is
introduced.

### In Scope

- A single conversational endpoint accepting a natural-language investment
  question and returning a streamed, synthesized answer.
- An orchestration layer that selects tools only from an explicit allowlist
  (Market, Stock, Technical Analysis, Fundamental Analysis, Valuation,
  Portfolio, News, Research/RAG, Screening) based on the question, calls
  them with validated arguments, and records auditable tool-call metadata
  for every answer.
- Per-claim attribution: every structured/numeric claim traces to the exact
  tool call and value that produced it; every document/news claim keeps
  Feature 006's citation shape; the two are never blended into one
  unattributed statement.
- Natural-language explanation of an existing deterministic output (signal,
  indicator reading, valuation classification, risk factor) using only the
  evidence factors the deterministic engine already produced.
- Natural-language-to-structured-filter conversion for the screener
  (SRS-SCR-03): the converted filters are shown to the owner and executed
  by Feature 003's existing deterministic screener engine, never by the LLM.
- Explicit refusal/limitation statements when a question needs data or a
  capability outside the allowlisted tools.
- Bounded tool-call steps per question, with a disclosed partial answer if
  the bound is reached rather than an indefinite wait.
- Streamed responses, reusing Feature 006's SSE contract pattern.
- Owner-only access under the existing session/CSRF/private-deployment
  model.

### Out of Scope

- Daily market briefing (SRS §34, Post-MVP).
- Persisted multi-turn conversation history or an investment journal
  (SRS-JRN-01, Post-MVP); this feature introduces no server-side
  conversation/thread store.
- Any new market, fundamental, technical, valuation, portfolio, screening,
  or document/news data capability — this feature only orchestrates what
  Features 001-006 already expose; it owns no new authoritative data.
- Any LLM-computed or LLM-approximated indicator, valuation, risk, signal,
  backtest, position size, or portfolio P/L value (Constitution Principle
  I) — every such value MUST come from an existing deterministic tool call.
- Automated order execution or autonomous trading (SRS §48).
- Personalized/adaptive analysis beyond what the owner's own existing
  portfolio/watchlist data already scopes (Phase 4 Personalization).
- Multi-user or public delivery; the same private single-owner deployment
  model as Features 001-006 applies unchanged.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ask a Question and Get a Tool-Grounded Answer (Priority: P1)

As the owner, I want to ask a plain-language question about a stock (e.g.
"Phân tích FPT hiện tại", "FPT đang overbought không?") and get back an
answer whose every structured claim matches what Finvera's own deterministic
tools actually computed, so that I get one coherent answer instead of
visiting several pages, and can trust it as faithfully reflecting real
system output rather than a plausible-sounding guess.

**Why this priority**: Nothing else in this feature matters unless
orchestration reliably selects the right tools and every attributed claim is
provably correct — this is the smallest slice that is independently useful
(single-source-grounded synthesis) and the foundation every later story
builds on.

**Independent Test**: Ask a question answerable entirely from structured
tools (e.g. current price, a technical reading, a valuation classification)
for a known symbol; verify the answer's structured claims exactly match
calling the underlying tool/endpoint directly, each attributed to its
source. Ask a question needing a tool outside the allowlist and verify the
system states the limitation rather than fabricating an answer.

**Acceptance Scenarios**:

1. **Given** a question answerable from one or more allowlisted structured
   tools for a supported symbol, **When** the owner asks it, **Then** the
   orchestrator calls only the needed tool(s) and the answer's structured
   claims exactly match those tools' actual output, each attributed to the
   tool it came from.
2. **Given** a question that needs data or a capability outside the
   allowlisted tools, **When** the owner asks it, **Then** the system states
   this is outside its current capability rather than approximating or
   inventing an answer.
3. **Given** an allowlisted tool call fails or times out while answering a
   question, **When** the answer is returned, **Then** the affected part is
   disclosed as degraded/unavailable, never silently omitted as if the data
   did not exist.
4. **Given** a question requiring more tool calls than the configured
   per-question bound, **When** the bound is reached, **Then** the system
   returns a disclosed partial answer rather than continuing indefinitely.

---

### User Story 2 - Combine Structured Data and Document Retrieval (Priority: P2)

As the owner, I want to ask a question that needs both a computed value and
something from a report or news article (e.g. "Rủi ro lớn nhất của FPT là
gì?", "Tóm tắt báo cáo tài chính gần nhất") and get one answer that clearly
shows which parts came from a calculation and which came from a cited
document, so that I never mistake an AI-synthesized document summary for an
authoritative number, or vice versa.

**Why this priority**: This is the harder, more valuable case built on top
of US1's attribution and Feature 006's citation guarantees — it only makes
sense once both underlying sources are proven individually trustworthy.

**Independent Test**: Ask a question whose correct answer requires both a
structured tool value and a retrieved document/news passage; verify the
answer contains both, each correctly and distinctly attributed (tool-sourced
vs document-sourced, with Feature 006's citation shape), never merged into
one unattributed statement.

**Acceptance Scenarios**:

1. **Given** a question needing both a structured value and document/news
   content, **When** the owner asks it, **Then** the answer includes both,
   each attributed to its own source (tool call vs document citation).
2. **Given** a structured-tool value and a retrieved document that disagree
   on the same fact, **When** the answer is returned, **Then** both are
   presented distinctly rather than silently reconciled, averaged, or one
   preferred without disclosure.
3. **Given** a retrieved document/news passage used to answer the question
   contains text designed to redirect the model or system, **When** the
   answer is produced, **Then** system/orchestrator behavior is unaffected,
   consistent with Feature 006's AI-003.
4. **Given** the same combined question and an unchanged underlying data
   snapshot, **When** asked twice, **Then** both answers cite the same
   retrieved passage set and the same structured tool outputs, even if
   generated wording differs.

---

### User Story 3 - Get a Deterministic Output Explained in Plain Language (Priority: P3)

As the owner, I want an existing signal, indicator reading, valuation
classification, or risk factor explained in plain language, so that I
understand *why* the system reached that conclusion without having to
interpret raw factor codes myself.

**Why this priority**: Genuinely useful and lower-risk than open-ended
Q&A — it explains data that already exists rather than deciding which tools
to call — but it is still narrower than US1/US2's general capability.

**Independent Test**: Request an explanation of an existing deterministic
output; verify the explanation references only the evidence factors the
deterministic engine actually produced for it, with no added, altered, or
selectively omitted factor.

**Acceptance Scenarios**:

1. **Given** an existing deterministic output with its evidence factors
   (e.g. a Bullish signal with "Price above MA50", "MACD bullish crossover",
   "Volume expansion"), **When** the owner requests an explanation, **Then**
   the response is a natural-language explanation using exactly those
   factors, attributing the conclusion to the deterministic engine, never
   presenting itself as a new or different calculation.
2. **Given** a deterministic output the owner requests an explanation for,
   **When** the explanation is generated, **Then** it does not state a
   different indicator value, classification, or factor than the
   deterministic engine actually produced.

---

### User Story 4 - Describe a Screening Criterion in Plain Language (Priority: P4)

As the owner, I want to describe what I am looking for in plain language
(e.g. "Những cổ phiếu nào đang có momentum tốt?") and have it converted into
the same structured filters I could have entered by hand, so that I do not
need to already know the screener's filter vocabulary to use it.

**Why this priority**: The lowest-risk, most narrowly additive story — the
screener (Feature 003) is already fully functional through its structured
UI; this only adds a natural-language entry point that must reduce to the
exact same deterministic filtering, never a new ranking mechanism.

**Independent Test**: Submit a natural-language criterion with an
unambiguous structured-filter interpretation; verify the converted filters
are shown to the owner and that running them produces identical results to
entering those same filters directly into Feature 003's screener.

**Acceptance Scenarios**:

1. **Given** a natural-language screening criterion with an unambiguous
   structured-filter interpretation, **When** the owner submits it, **Then**
   the system shows the converted structured filters and returns the exact
   same matching stocks Feature 003's screener engine would return for
   those filters entered directly.
2. **Given** an ambiguous natural-language criterion, **When** the owner
   submits it, **Then** the system either states the specific interpretation
   it used before returning results, or asks for clarification, rather than
   silently guessing.

### Edge and Failure Cases *(mandatory)*

- A question needs a tool/capability not on the allowlist — stated as
  outside current capability, never approximated (US1 Scenario 2).
- An allowlisted tool call fails, times out, or the underlying feature
  (001-006) is degraded — disclosed as degraded in the answer; other
  features remain fully usable (Constitution Principle VII).
- Two sources (a structured tool and a retrieved document) disagree on the
  same fact — both surfaced distinctly, never merged or silently preferred.
- A retrieved document/news passage or a tool result contains text
  attempting to redirect the model or system — treated as inert data,
  consistent with Feature 006's AI-003.
- Excessive or looping tool-selection behavior — bounded step count per
  question, terminating with a disclosed partial answer.
- A request from an identity other than the configured owner, or an attempt
  to reach another owner's portfolio/document data through a tool call (not
  applicable in this single-owner deployment today, but enforced
  identically to Features 005/006).
- An ambiguous natural-language screening criterion — resolved to a
  disclosed interpretation or a clarification request, never a hidden guess.
- The orchestrator or an underlying tool is unavailable entirely — the
  AI Analyst endpoint states unavailability; Features 001-006 remain fully
  usable independent of this feature (Constitution Principle VII).

## Requirements *(mandatory)*

Requirements describe observable behavior. Accepted IDs are stable and MUST
not be renumbered; removed requirements are deprecated with a reason.

### Functional Requirements

- **FR-001**: The system MUST let the owner submit a natural-language
  investment question and receive a synthesized answer.
- **FR-002**: The orchestrator MUST select tools only from an explicit
  allowlist (Market, Stock, Technical Analysis, Fundamental Analysis,
  Valuation, Portfolio, News, Research/RAG, Screening); it MUST NOT execute
  arbitrary code or call any capability outside this allowlist.
- **FR-003**: Every structured/numeric claim in an answer (price, indicator,
  ratio, valuation classification, signal, risk factor, portfolio figure)
  MUST be attributed to the specific tool call that produced it and MUST
  match that tool's actual output exactly; the LLM MUST NOT recompute,
  round, or approximate it independently.
- **FR-004**: Every claim drawn from document or news content MUST carry a
  citation in the same shape Feature 006 defines (source identity and
  location), reusing the Research/RAG tool rather than a separate retrieval
  path.
- **FR-005**: When a question requires data or a capability outside the
  allowlisted tools, the system MUST state that the question is outside its
  current capability rather than approximating or inventing an answer.
- **FR-006**: The system MUST let the owner request a natural-language
  explanation of an existing deterministic output (signal, indicator
  reading, valuation classification, risk factor), using only the evidence
  factors that output's deterministic engine actually produced, without
  adding, altering, or selectively omitting factors.
- **FR-007**: The system MUST let the owner describe a screening criterion
  in natural language and receive matching stocks, where the
  natural-language input is converted to structured filters that MUST be
  executed by Feature 003's existing deterministic screener engine; the LLM
  MUST NOT itself filter or rank stocks.
- **FR-008**: The structured filters converted from a natural-language
  screening criterion MUST be shown to the owner (or otherwise inspectable)
  so they can verify what was actually searched for.
- **FR-009**: An ambiguous natural-language screening criterion MUST be
  resolved to a stated, disclosed interpretation, or the system MUST ask for
  clarification, rather than silently guessing.
- **FR-010**: When an answer combines structured-tool data and
  document/news content, each claim's attribution MUST remain distinct;
  the system MUST NOT blend a document-sourced claim and a tool-sourced
  claim into one unattributed statement.
- **FR-011**: The system MUST bound the number of tool calls per question
  and MUST terminate with a disclosed partial answer, never an indefinite
  wait, if that bound is reached.
- **FR-012**: A tool call that fails or times out while answering a question
  MUST be disclosed in the answer as degraded/unavailable, never silently
  omitted as if the underlying data did not exist.
- **FR-013**: The system MUST retain no server-side memory of previous
  questions across requests; each question MUST be answered independently
  unless the client explicitly supplies prior turns as part of the current
  request.
- **FR-014**: Responses MUST stream to the browser as they are produced,
  reusing Feature 006's SSE contract pattern.
- **FR-015**: No question, tool call, or answer MUST access or reveal data
  belonging to any identity other than the single configured owner, reusing
  Features 005/006's owner-scoping enforcement rather than a new pattern.

### Data and Financial Semantics

- **DATA-001**: The system MUST NOT persist a new authoritative copy of any
  financial value; every structured claim in an answer MUST be sourced live
  from Features 001-005's existing authoritative services at answer time.
- **DATA-002**: Every structured tool-call result used in an answer MUST
  retain the as-of/effective time (price time, report period, computed-at
  time) the underlying deterministic engine already attaches to it, so the
  owner can judge freshness.
- **DATA-003**: When structured-tool data and retrieved-document content
  disagree on the same fact, the system MUST present both without silently
  reconciling, averaging, or preferring one without disclosure.

### Security and Privacy

- **SEC-001**: The AI Analyst endpoint MUST be accessible only to the single
  configured owner identity, under the same authenticated server-side
  session and CSRF controls Features 001-006 established; the browser MUST
  continue to call only `finvera-be`, never `finvera-ai` directly.
- **SEC-002**: Every new internal endpoint this feature adds — both the
  structured-data tool endpoints `finvera-ai`'s orchestrator calls back
  into `finvera-be` for, and the orchestration/explanation endpoints
  `finvera-be` calls on `finvera-ai` — MUST require the same bidirectional
  `X-Internal-Api-Key` validation Feature 006 established (research R-003);
  no new endpoint in either hosting direction may be reachable by network
  position alone.
- **SEC-003**: The system MUST send any LLM/embedding provider only the
  minimum content needed for the invoked tool/question, and MUST NOT include
  secrets, tokens, or another owner's/feature's private data in any prompt,
  tool-call payload, log, or telemetry.

### AI and Retrieval Behavior

- **AI-001**: The system MUST NOT allow the LLM to compute, restate, or
  approximate an indicator, valuation, risk, signal, backtest, position
  size, or portfolio P/L value as authoritative; every such value MUST come
  from a deterministic tool call (Constitution Principle I).
- **AI-002**: The orchestrator MUST validate tool-call arguments before
  invocation and MUST record auditable tool-call metadata (which tools, in
  what order, with what arguments, and their outcome) for every answer,
  without logging sensitive prompt or response content beyond what
  observability requires.
- **AI-003**: Document/news content reached through the Research/RAG tool
  MUST remain untrusted data per Feature 006's AI-003, unaffected by
  embedded instructions, even when combined with structured-tool results in
  the same answer.
- **AI-004**: When no allowlisted tool can supply information relevant to a
  question, the system MUST refuse or state the limitation rather than
  answering from the model's general knowledge alone.

### Non-Functional Requirements

- **NFR-001**: At least 90% of AI Analyst responses MUST begin streaming
  within 20 seconds under normal operating conditions, consistent with SRS
  §36.1's interactive-AI-answer baseline extended for multi-tool
  orchestration.
- **NFR-002**: `finvera-ai`'s orchestrator or any allowlisted tool being
  unavailable MUST leave Features 001-006 fully usable independent of this
  feature (Constitution Principle VII); the AI Analyst endpoint MUST state
  unavailability rather than hang or silently degrade.
- **NFR-003**: The per-question tool-call bound MUST be enforced and its
  outcome (reached or not) MUST be observable in tool-call metadata (AI-002).
- **NFR-004**: Attribution, citation, and degraded/limitation states MUST be
  understandable without relying on color alone.

### Key Entities

- **AI Analyst Query**: One natural-language question, its resulting
  answer, and the ordered set of tool calls that produced it; not persisted
  as reusable conversation/thread state, though its tool-call metadata may
  be retained for observability without sensitive content (AI-002).
- **Tool Call**: One orchestrator-issued, allowlisted request to a specific
  tool (Market, Stock, Technical, Fundamental, Valuation, Portfolio, News,
  Research/RAG, or Screening) with validated arguments, its result, and
  outcome (success, failure, timeout) — the audit trail underlying an
  answer's attribution.
- **Answer**: Synthesized natural-language text plus per-claim attribution:
  tool-sourced claims linked to their originating tool call, document/news
  claims carrying Feature 006's citation shape.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- **No persisted multi-turn conversation or journal** (SRS-JRN-01,
  Post-MVP): this feature introduces no server-side conversation/thread
  store. The frontend MAY display a running chat-style transcript for the
  current browser session (client-side state only), and MAY resend prior
  turns as explicit context in a request, but the orchestrator MUST treat
  each submitted question as independent otherwise (FR-013).
- **Tool-call audit/observability logging is in scope and distinct from
  conversation persistence.** Constitution Principle VII and
  `finvera-ai/AGENTS.md` already require storing model/prompt/retrieval
  versions and latency/token/error metrics without logging sensitive
  content; this feature's tool-call metadata (AI-002) is that kind of
  operational record, not a reusable conversation thread.
- **Tool taxonomy maps directly onto Features 001-006**, per SRS §31's nine
  named tools: Market → Feature 001; Stock, Technical Analysis,
  Fundamental Analysis, Valuation → Feature 002; Portfolio → Feature 005;
  News, Research/RAG → Feature 006; Screening → Feature 003, with this
  feature adding only the natural-language entry point (SRS-SCR-03).
  Feature 004's signal/risk-factor data is exposed through the Technical
  Analysis tool (and Fundamental/Valuation where applicable) rather than as
  a tenth, separately named tool, since SRS §31 does not enumerate a
  distinct Strategy/Signal tool — a deliberate interpretation recorded here
  rather than left implicit.
- **The orchestrator reaches `finvera-be`'s structured data through a new
  internal, versioned, key-authenticated tool API**, never direct database
  access, extending Feature 006's bidirectional `X-Internal-Api-Key`
  pattern (research R-003) rather than inventing a new trust mechanism;
  this is required by Constitution Principle III's browser/AI-service/data
  boundary and is resolved concretely in this feature's `research.md`/
  `contracts/`.
- **This feature adds no new authoritative data of its own.** Every
  structured value it surfaces is sourced live from Features 001-005 at
  answer time (DATA-001); every document/news value is sourced live from
  Feature 006. This feature owns no new PostgreSQL table beyond a
  tool-call/audit log.
- Answers are decision support, not investment advice; a synthesized answer
  confers no fiduciary authority and does not instruct the owner to buy or
  sell (Constitution Principle IV), the same disclosure discipline Features
  004 and 006 already established.
- Deployment model is unchanged: the same private single-owner deployment
  as Features 001-006.

### Dependencies

- Every capability Features 001-006 already expose (market overview; stock
  detail, technical, fundamental, and valuation; screener; strategy/signal/
  risk; portfolio/watchlist analytics; research document and news RAG) as
  the tool surface this feature orchestrates.
- Feature 006's bidirectional internal-API authentication pattern (research
  R-003) and its Research/RAG internal endpoints, reused for the
  Research/RAG tool rather than reimplemented.
- ADR-0002 (Gemini as the LLM provider) for orchestration and synthesis;
  ADR-0008 (Gemini embeddings) transitively via the reused Research/RAG
  tool.
- A new internal, versioned tool-invocation API contract between
  `finvera-be` and `finvera-ai`, and an orchestration/tool-selection design
  (model choice for orchestration, tool-call bound, argument-validation
  approach) — resolved in this feature's `research.md`/`contracts/` before
  implementation, consistent with Constitution Principles I and V.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Across a versioned fixture set of structured-only questions,
  100% of structured/numeric claims in answers exactly match the
  corresponding tool's actual output.
- **SC-002**: Across a versioned fixture set of combined structured +
  document questions, 100% of claims carry correct, distinct attribution
  (tool-sourced vs document-sourced), with zero blended/unattributed claims.
- **SC-003**: Across a versioned fixture set of out-of-capability questions,
  100% produce a stated-limitation response rather than a fabricated or
  approximated answer.
- **SC-004**: Across a versioned fixture set of unambiguous
  natural-language screening criteria, 100% of conversions produce results
  identical to entering the equivalent structured filters directly into
  Feature 003's screener.
- **SC-005**: Across prompt-injection fixtures reachable via the
  Research/RAG tool, 0% alter orchestrator tool-selection or system
  behavior.
- **SC-006**: At least 90% of responses begin streaming within 20 seconds
  under normal operating conditions.
- **SC-007**: Authorization tests confirm only the configured owner can
  reach the AI Analyst endpoint or trigger any tool call; no response, log,
  or export contains another owner's data, a credential, or a token.
- **SC-008**: Accessibility review confirms 100% of attribution, citation,
  and degraded/limitation states have a non-color indicator.

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001, FR-002, FR-003 | US1 / Scenario 1 | SC-001 |
| FR-005, AI-004 | US1 / Scenario 2 | SC-003 |
| FR-012 | US1 / Scenario 3 | SC-001, SC-003 |
| FR-011, NFR-003 | US1 / Scenario 4 | SC-006 |
| FR-004, FR-010, DATA-003 | US2 / Scenario 1, 2 | SC-002 |
| AI-003 | US2 / Scenario 3 | SC-005 |
| FR-013 | US2 / Scenario 4 | SC-002 |
| FR-006 | US3 / Scenario 1, 2 | SC-001 |
| FR-007, FR-008 | US4 / Scenario 1 | SC-004 |
| FR-009 | US4 / Scenario 2 | SC-004 |
| DATA-001, DATA-002 | US1-US4 | SC-001 |
| SEC-001, SEC-002, SEC-003 | Edge case (owner-only, internal-API auth, provider data minimization) | SC-007 |
| AI-001 | US1-US4 | SC-001 |
| AI-002 | US1-US4 | SC-005, SC-007 |
| FR-014 | US1-US4 | SC-006 |
| FR-015 | Edge case (cross-owner access) | SC-007 |
| NFR-001, NFR-002 | US1-US4 timing; edge cases | SC-006 |
| NFR-004 | US1-US4 | SC-008 |
