# Implementation Plan: AI Analyst end-to-end on real data

**Feature**: `015-analyst-e2e-on-real-data` · 2026-08-31 · **Spec**: `spec.md` · **Research**: `research.md` (the first capture and its diagnosis) · **Contracts touched**: `specs/007-ai-analyst/contracts/{internal,public}-api.openapi.yaml` (additive: `synthesisMode`, `plannerMode` on the final event)

## Summary

The capture tool is the research instrument; the first run against the real
stack exposed four defects (Q-49 … Q-52, see research.md). Each is fixed at
its layer with a test, the capture is re-run, and the recording becomes a
golden fixture so the deterministic parts of the pipeline stay regression-
tested on real data without a model call.

## Constitution Check

| Principle | Assessment |
|---|---|
| I | No calculation touched; templates format numbers for display, claims keep raw values. ✔ |
| II | Degraded mode (template answer, keyword planner) is disclosed in the answer text and in `synthesisMode`/`plannerMode`; answers must state data date and basis (prompt rules 8–10). ✔ |
| III | Backend: transaction boundary + error mapping only; AI: adapter retry + templates + prompt; FE: one notice. ✔ |
| V | spec → research (capture) → plan → tasks → code → re-capture. ✔ |
| VI | Unit tests per fix; golden replay tests on the recording. ✔ |
| VIII | Smallest change per defect; no prompt redesign, no new tools. ✔ |

## Changes

### Backend (Q-49)
- `ToolDelegateService`: `getTechnical`, `getFundamentals`, `getValuation` → method-level `@Transactional` (writable) — these services materialise an idempotent revision chain on read; under the class-level read-only transaction the fundamentals tool silently dropped its writes (Hibernate MANUAL flush) and the valuation tool failed on `saveAndFlush`.
- `ProblemDetailsAdvice`: `ResponseStatusException` keeps its status; catch-all `Exception` → 500 `SERVER_ERROR` problem (correlation id, no internals). Previously an unhandled exception was forwarded to `/error`, which sits behind the OWNER rule, so internal callers saw **401 AUTHENTICATION_REQUIRED** instead of the real 500.
- `AskAnalystDto` / `AnalystService`: pass `synthesisMode`, `plannerMode` through to the public final event.

### finvera-ai (Q-50, Q-51, Q-52)
- `generation.py`: `quota_retry_delay_seconds` — one retry honouring the provider's `retryDelay` when ≤ 60 s (5-RPM free tier), for tool proposal and synthesis; anything else fails over immediately.
- `service.py`: templates for TECHNICAL (indicator values + `signals.length`), SCREENING (`totalMatches`, names), PORTFOLIO (`positions.length` / `totalValue`, honest "no positions"), FUNDAMENTAL (EPS TTM with `PROVIDER_TRAILING_EPS` wording, growth with `ANNUAL_BASIS` wording), VALUATION (P/B, `REDUCED_METRIC_SET` wording), STOCK/MARKET (trading date + data-status note); vi-VN number formatting (`fmt_vi`) for display, raw `claimedValue` for verification; `OFFLINE_TEMPLATE_DISCLOSURE` prepended when the provider was available but failed; `propose_tool_calls_with_mode`; `synthesisMode`/`plannerMode` on the result; synthesis rules 8–10 (data date, basis wording, no inferred conclusions).
- `attribution.py`: `get_nested_value` supports `list[i]`, `list.i`, `list.length`; result model carries the two modes.

### Frontend
- `analyst.ts` type; `AskAnalyst.tsx` "[Chế độ suy giảm]" notice when `synthesisMode === 'OFFLINE_TEMPLATE'`.

### Tooling / tests
- `tools/verification/analyst_e2e.py` (capture + fidelity + basis checks; both vi-VN and en-US number readings; date tokens masked).
- Golden fixture + replay tests in `finvera-ai/app/features/chat/tests/golden/`.

## Verification
Backend targeted + full suite; `uv run pytest`; FE vitest/eslint/tsc/build; re-run
`analyst_e2e.py` on the restarted stack and record the table in research.md and
`docs/REMEDIATION_PLAN.md`.
