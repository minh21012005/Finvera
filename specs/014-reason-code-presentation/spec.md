# Feature Specification: Reason-code presentation

**Feature Directory**: `014-reason-code-presentation`
**Created**: 2026-08-31 · **Status**: Implemented (2026-08-31)
**SRS References**: Section 4 (transparency / provenance), Section 12 (UI) · **SRS Requirement IDs**: SRS-UI-03, SRS-TRN-02
**Input**: docs/REMEDIATION_PLAN.md Phase 2 item **P2-02**; inventory of every
reason / quality / status code the APIs emit (research.md). Owner instruction
after the data audit: "Tiến hành impl".

## Scope Summary *(mandatory)*

Every section of the product already discloses *why* a number is missing,
withheld or computed on a different basis — but it discloses it as the engine's
own identifier (`ANNUAL_BASIS`, `PROVIDER_TRAILING_EPS`, `NO_COMPARISON_BASIS`,
`kbs-fcf-ocf-plus-capex-v2`, `REGIME_UNAVAILABLE`, …). The audit found ~90
distinct codes reaching the screen raw at 20+ render sites, three tiny ad-hoc
maps (4 keys, 1 key, 1 key), one dead pass-through helper, and four verbatim
copies of the data-status label map. Several label maps index a `Record`
without a fallback, so a new backend enum value renders as an empty string.

This feature introduces **one presentation dictionary** (code → Vietnamese
wording) with a **never-hide** rule, and routes every render site through it.
The code itself stays attached to the rendered element (`data-reason-code`,
`title`) so provenance is inspectable and tests can still address codes.

### In Scope

- Shared module `finvera-fe/src/shared/format/reason-codes.ts`: dictionary,
  `reasonCodeLabel`, `describeReasonCodes`, `applicabilityNote`,
  `dataStatusLabel` (with fallback) and a `<ReasonCodes>` component.
- Every FE render site listed in research.md §B routed through the module.
- Dedupe of the four `statusLabel` copies in `market-overview`.
- Explain-evidence: engine notes handed to the AI use the same wording for
  *every* code (today unmapped codes are silently dropped from the evidence).
- `finvera-ai` offline valuation-withheld sentence: the same wording for the
  five valuation withhold codes instead of the raw list.
- Completeness test: every code in the checked-in inventory has a label; the
  fallback for an unknown code is the raw code, never an empty string.

### Out of Scope

- Changing which codes the backend emits, or their semantics.
- Error-envelope `reasonCode`s that are switched on but never displayed
  (`DUPLICATE_PORTFOLIO_NAME`, …) — already have bespoke copy.
- Ingestion-only / observability codes that never reach the FE.
- Anything the AI Analyst synthesises from tool JSON (P2-03 golden tests).

## User Scenarios & Testing *(mandatory)*

### US-1 — Fundamentals card (P1)
As the owner reading MBB's fundamentals I see "EPS 12 tháng … *EPS 12 tháng
lấy theo số trailing của nhà cung cấp*" instead of `(PROVIDER_TRAILING_EPS)`;
hovering shows the code.
**Acceptance**: no `[A-Z][A-Z0-9_]{3,}` token from the inventory is visible as
text in the card; `data-reason-code="PROVIDER_TRAILING_EPS"` is present.

### US-2 — Withheld valuation (P1)
The withheld notice lists "Chưa đủ cơ sở so sánh; Không có chỉ số lõi…" — one
sentence per code, joined with "; ".

### US-3 — Unknown code (P1)
A code the dictionary does not know (e.g. a future `RENORMALIZED_…`) is still
shown, as itself. Nothing is ever hidden.

### US-4 — Market overview (P2)
Index/breadth/regime unavailability and quality notes read as sentences; the
regime disclaimer reads as a sentence; the status pill wording is identical
across the four cards.

### US-5 — Portfolio / watchlist (P2)
`⚠ PARTIAL: …` becomes `⚠ Một phần: …`; the portfolio list explains every
reason code, not only `POSITION_PRICE_UNAVAILABLE`.

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-001 | One dictionary module maps every UI-reachable code in research.md §A to Vietnamese wording; the module is the only place such wording lives. |
| FR-002 | Unknown codes render as the raw code (never empty, never dropped). Known codes render the wording; the code is retained as `data-reason-code` and `title` on the element. |
| FR-003 | `applicabilityNote(applicability, reasonCode)` yields "Không áp dụng — <why>" / "Không có dữ liệu — <why>" and is used by every metric/indicator/factor cell. |
| FR-004 | Every render site in research.md §B uses the module; no component keeps a private reason map or a private data-status map. |
| FR-005 | `dataStatusLabel` and every enum label helper return the raw value as fallback, never `undefined`. |
| FR-006 | Explain evidence hands the AI the wording for every engine note (no silent drop) and for `NOT_APPLICABLE` metrics. |
| FR-007 | The `finvera-ai` offline valuation-withheld sentence uses the same wording for `NO_COMPARISON_BASIS`, `CORE_METRIC_UNAVAILABLE`, `INSUFFICIENT_METRIC_COVERAGE`, `PRICE_UNAVAILABLE`, `FUNDAMENTALS_UNAVAILABLE`. |

## Success Criteria

| ID | Criterion | Measure |
|---|---|---|
| SC-001 | Dictionary completeness | vitest: every code in the checked-in inventory list resolves to wording ≠ code. |
| SC-002 | Never hidden | vitest: unknown code → raw code text; empty list → explicit fallback sentence. |
| SC-003 | No raw code in components | grep over `src/features/**/*.tsx` (tests excluded) finds no `reasonCodes.join(` / `` (${…reasonCode}) `` interpolation. |
| SC-004 | Existing behaviour tests green | FE suite passes with assertions moved from code text to wording + `data-reason-code`. |

## Constitution Check

II (provenance): the code is kept on the element; wording never claims more
than the code means. VIII (smallest change): one module, mechanical routing,
no backend change. VI: unit tests for the module + updated component tests.
