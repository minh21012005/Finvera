# Implementation Plan: Reason-code presentation

**Feature**: `014-reason-code-presentation` · 2026-08-31 · **Spec**: `spec.md` · **Research**: `research.md` · **Contract**: `contracts/reason-code-presentation-v1.md`

## Summary

Frontend-only (plus one sentence in `finvera-ai`). One shared dictionary
module and one small component; every render site that printed a code is
routed through them. No API, rule or persistence change.

## Constitution Check

| Principle | Assessment |
|---|---|
| I | No calculation touched. ✔ |
| II | Codes stay on the element (`data-reason-code`, `title`); wording never claims more than the code; unknown codes never hidden. ✔ |
| III | New `src/shared/format` module imports nothing from features; features import it. ✔ |
| V | spec → research → contract → plan → tasks → code. ✔ |
| VI | Module unit tests (completeness, fallback, notes); component tests updated to assert wording + attribute. ✔ |
| VIII | Mechanical routing; no redesign of any card. ✔ |

## Changes

### New
- `finvera-fe/src/shared/format/reason-codes.ts` — `REASON_CODE_LABELS`,
  `reasonCodeLabel`, `isKnownReasonCode`, `describeReasonCodes`,
  `applicabilityNote`, `dataStatusLabel`.
- `finvera-fe/src/shared/components/reason-codes.tsx` — `<ReasonCodes codes prefix fallback>`,
  `<ReasonCode code>`; each code → `<span data-reason-code title>`.
- `finvera-fe/src/shared/format/reason-codes.test.ts` — inventory list (research R-002)
  completeness; fallback; note wording.

### Modified (FE)
- `stock-detail/format/stock-format.ts`: `dataStatusLabel` re-exported from shared;
  `applicabilityReasonLabel` → delegates to `applicabilityNote`.
- `stock-detail/format/explain-evidence.ts`: `VALUATION_NOTE_COPY` removed; every
  engine note → `reasonCodeLabel`; `NOT_APPLICABLE` reason → wording.
- `stock-detail/components/stock-{overview,technical,fundamentals,valuation,signals,chart}.tsx`,
  `stock-screener/components/screener-results.tsx`,
  `market-overview/components/{index,breadth,regime}-overview.tsx`,
  `market-overview/market-overview-page.tsx`,
  `portfolio/components/{holdings-table,portfolio-list}.tsx`: use the module.
- Tests: assertions on raw code text → wording + `data-reason-code`.

### Modified (AI)
- `finvera-ai/app/features/chat/service.py`: offline valuation-withheld sentence uses
  a five-entry Vietnamese map (same wording as the FE contract); unknown → raw code.

## Verification
`npm test`, `npm run lint`, `npm run build` (finvera-fe); `uv run pytest` (finvera-ai);
grep SC-003.
