# Feature Specification: AI Analyst end-to-end on real data

**Feature Directory**: `015-analyst-e2e-on-real-data`
**Created**: 2026-08-31 · **Status**: Implemented (2026-08-31)
**SRS References**: Section 11 (AI Analyst), Section 4 (transparency) · **SRS Requirement IDs**: SRS-AI-01, SRS-AI-03, SRS-TRN-02
**Input**: docs/REMEDIATION_PLAN.md Phase 2 item **P2-03**. The Analyst path
(`finvera-ai` orchestration → `finvera-be /internal/v1/tools` → Gemini
synthesis → attribution verification) was hardened in Group E and Q-43/Q-44 but
never exercised end-to-end against the post-refresh database (ROE now TTM,
growth `ANNUAL_BASIS`, valuation v2 codes, `PROVIDER_TRAILING_EPS`, reason-code
wording from Feature 014). Owner instruction: "tiếp tục".

## Scope Summary *(mandatory)*

Run ten scripted questions through the real stack on the owner's machine,
record the tool calls, the tool responses (fetched again directly from the
backend with the same arguments), the final answer and the verified claims;
check every number in every answer against the tool responses and, for the
headline numbers, against the database; check that every basis disclosure the
tools carry (`ANNUAL_BASIS`, `REDUCED_METRIC_SET`, `PROVIDER_TRAILING_EPS`,
withheld reasons) is either present in the answer or absent for a reason the
answer states. Freeze the recordings as **golden fixtures** in `finvera-ai`
so the deterministic parts of the pipeline (attribution verification, offline
synthesis, planner fallback, fabrication guard) are regression-tested on real
data without a model call.

### In Scope

- Owner-operated capture (`tools/verification/analyst_e2e.py`): the ten
  questions, SSE parsing, direct tool re-fetch, number-fidelity check, basis
  disclosure check, report JSON + markdown table.
- Golden fixture `finvera-ai/app/features/chat/tests/golden/analyst_e2e_2026-08-31.json`
  (tool responses, answers, verified claims) and tests replaying it.
- Any defect found in the pipeline is fixed under its own `Q-` id.

### Out of Scope

- Changing the prompts or the model; measuring model quality beyond fidelity
  (fidelity = no number or basis the tools did not supply).
- RAG/news questions (no documents ingested in the owner database).

## User Scenarios & Testing *(mandatory)*

### US-1 — Numeric fidelity (P1)
Every number the Analyst states for a symbol equals (after Vietnamese
formatting/rounding) a value present in a tool response of that answer.
**Acceptance**: 0 unsupported numbers ≥ 10 in absolute value across the ten
answers (small counts such as "4 quý" excluded, as in `explain.py`).

### US-2 — Basis honesty (P1)
Where a tool reports a basis or a withhold (`ANNUAL_BASIS`,
`PROVIDER_TRAILING_EPS`, `REDUCED_METRIC_SET`, `classification: null` +
reason codes), the answer says so in words; it never presents a withheld
valuation as a classification or an annual-basis growth as quarterly.

### US-3 — Refusal / no data (P1)
Portfolio question on an owner with no positions → the answer states there are
no positions; no invented holdings or returns.

### US-4 — Golden replay (P2)
`uv run pytest` replays the fixture: attribution verification reproduces the
recorded verified claims exactly; the offline synthesizer produces numbers that
all exist in the fixture responses; the fabrication guard finds 0 unsupported
numbers in the recorded answers.

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-001 | The capture tool records, per question: proposed/dispatched tool calls (name, arguments, status, latency), the direct re-fetch of each successful tool response, the final answer, `structuredClaims`, `refused`, `toolCallBoundReached`. |
| FR-002 | Number-fidelity check: numbers parsed from the answer (vi-VN formats) are matched against every numeric leaf in the tool responses with rounding tolerance; unsupported numbers are listed. |
| FR-003 | Basis check: for each tool response carrying `ANNUAL_BASIS`, `PROVIDER_TRAILING_EPS`, `REDUCED_METRIC_SET`, `classification == null`, `dataStatus != CURRENT`, the answer text must contain the corresponding wording (contract reason-code-presentation-v1) or an equivalent phrase; misses are listed. |
| FR-004 | Golden tests never call a model or the network; they load the fixture and exercise `verify_attribution`, `_offline_synthesize`, `plan_tools`, and the number guard. |
| FR-005 | The capture never prints credentials; secrets are read from the two `.env` files at run time. |

## Success Criteria

| ID | Criterion | Measure |
|---|---|---|
| SC-001 | 10/10 answers numerically faithful | FR-002 unsupported list empty (or every entry explained and fixed under a `Q-`). |
| SC-002 | 10/10 answers basis-disclosed | FR-003 miss list empty (or fixed). |
| SC-003 | Golden tests green | `uv run pytest` passes with the fixture; ≥ 4 new tests. |
| SC-004 | Evidence recorded | `docs/REMEDIATION_PLAN.md` P2-03 → DONE with the per-question table. |

## Constitution Check

II: the check is against the tools' own facts, never against the model's
opinion. V: spec → research (the capture is the research) → plan → tasks →
tests. VI: golden fixtures are real recorded data, not hand-written.
