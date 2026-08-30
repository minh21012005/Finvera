# Feature Specification: Valuation v2 — metric coverage over obtainable metrics

**Feature Directory**: `012-valuation-v2-metric-coverage`
**Created**: 2026-08-31 · **Status**: Clarified
**SRS References**: Section 10 (valuation) · **SRS Requirement IDs**: SRS-VAL-01
**Input**: Post-refresh measurement 2026-08-31 (docs/REMEDIATION_PLAN.md Q-42):
888 instruments have a comparison basis but 181 of them are withheld with
`INSUFFICIENT_METRIC_COVERAGE`. Every one is a loss-making company: P/E is
`NOT_APPLICABLE`, EV/EBITDA can never be `DEFINED` with the accepted provider
(no balance sheet), so P/B's 0.30 alone never reaches the 0.50 floor. Owner
instruction: "làm chuẩn logic".

## Scope Summary *(mandatory)*

`valuation-v1` treats "the provider cannot supply this input" and "this ratio is
meaningless for this company" the same way it treats a genuine data gap. v2
separates them: a metric that is `NOT_APPLICABLE` (negative earnings, negative
growth) or structurally unobtainable (EV/EBITDA without balance-sheet facts)
leaves the coverage denominator; only real gaps (`MISSING`) count against the
floor. A published assessment built on a reduced metric set says so
(`REDUCED_METRIC_SET`) and its confidence — which stays on the absolute
weight scale — is correspondingly lower.

Formulas, bands, bases, percentile rank, confidence weights and every other
rule of v1 are unchanged. v1 rows stay in the database under their own
`rule_version`.

### In Scope

- `valuation-v2` rule version; coverage floor computed over obtainable metrics.
- Reason code `REDUCED_METRIC_SET` (non-blocking, disclosed).
- Contract `valuation-v2.md`; OpenAPI/FE accept `valuation-v2`.
- Warmup recomputes the universe under v2.

### Out of Scope

- Any change to weights, bands, basis floors (500 / 8), confidence formula.
- Publishing when a core metric is genuinely missing (`CORE_METRIC_UNAVAILABLE`
  stays blocking).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A loss-making company gets a P/B-based assessment (P1)

As an investor, when a company has negative EPS but a defined P/B with 500+
history points, I want a classification based on P/B, clearly marked as
built on a reduced metric set with low confidence, instead of "insufficient
metric coverage".

**Acceptance**: PE `NOT_APPLICABLE`, PB `DEFINED`, PEG `NOT_APPLICABLE`,
EV/EBITDA `MISSING` (structural), own history 600 points → published,
`REDUCED_METRIC_SET` present, effective weight of PB = 1.0, confidence
computed with metricCoverage = 0.30.

### User Story 2 - A real data gap still withholds (P1)

**Acceptance**: PE `MISSING` (`MISSING_EPS`), PB `DEFINED`, PEG `MISSING` →
obtainable weight = 0.40 + 0.30 + 0.10 = 0.80, qualifying = 0.30, ratio 0.375
< 0.50 → `INSUFFICIENT_METRIC_COVERAGE`, not published.

### Edge cases

- All scored metrics `NOT_APPLICABLE` → `CORE_METRIC_UNAVAILABLE` (unchanged).
- PB `DEFINED` but with no basis for PB while PE has one → PB counts as a gap
  (v1 behaviour retained).
- EV/EBITDA `DEFINED` (future provider) → it simply participates; nothing
  special-cased.

## Requirements

- **FR-001** Coverage ratio = qualifying weight / obtainable weight, floor 0.50;
  obtainable excludes `NOT_APPLICABLE` metrics and EV/EBITDA when `MISSING`
  for lack of inputs (`MISSING_EBITDA`, `MISSING_EV_INPUTS`).
- **FR-002** `REDUCED_METRIC_SET` is added whenever a scored metric was
  excluded as `NOT_APPLICABLE`; it never blocks publication.
- **FR-003** Confidence keeps `metricCoverage` = absolute qualifying weight.
- **FR-004** `rule_version = valuation-v2` on every new assessment; v1 rows are
  never rewritten; API and FE accept `valuation-v2`.
- **DATA-001** Effective weights are renormalised over qualifying metrics per
  basis exactly as v1 (the change is only in the gate).

## Success Criteria

- **SC-001** After warmup, published assessments ≥ 50 % of listed instruments
  (from 42.7 %).
- **SC-002** No assessment publishes with a `MISSING` core metric.
- **SC-003** Every reduced-set assessment carries `REDUCED_METRIC_SET` and
  confidence ≤ the same instrument's full-set confidence would be.

## Requirement Traceability

| Requirement | Verification |
|---|---|
| FR-001, FR-002 | `ValuationV1Tests` new vectors (US1, US2) |
| FR-003 | confidence worked example |
| FR-004 | service/FE tests on rule version; OpenAPI const |
