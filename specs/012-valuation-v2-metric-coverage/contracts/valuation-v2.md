# Calculation Contract: `valuation-v2`

**Feature**: `012-valuation-v2-metric-coverage` · Accepted 2026-08-31
**Supersedes** `valuation-v1` (specs/002 contracts/valuation-v1.md) for all
assessments calculated after adoption; v1 rows remain readable under their
`rule_version`. Every section of v1 applies unchanged **except** the two
amended below. The normative implementation stays in
`ValuationV1.java` (class name historical; `RULE_VERSION` is the authority).

## Publishability — amended condition

| Condition | Reason code when it fails |
|---|---|
| Coverage ratio of scored metrics ≥ 0.50, where coverage = Σ baseWeight(qualifying) / Σ baseWeight(obtainable) | `INSUFFICIENT_METRIC_COVERAGE` |

- **qualifying**: `DEFINED` and a percentile is available in at least one basis (v1 definition).
- **obtainable**: every scored metric except (a) those `NOT_APPLICABLE` for this
  company, and (b) `EV_EBITDA` when `MISSING` with reason `MISSING_EBITDA` or
  `MISSING_EV_INPUTS` (provider supplies no balance sheet, research R-003).
- A `MISSING` metric for any other reason stays in the denominator (a data gap).
- If the obtainable set is empty the assessment is withheld by
  `CORE_METRIC_UNAVAILABLE` (unchanged).

All other publishability conditions of v1 remain blocking as written.

## Reason codes — added

| Code | Meaning | Blocking |
|---|---|---|
| `REDUCED_METRIC_SET` | At least one scored metric was `NOT_APPLICABLE` and excluded from the coverage denominator; the classification rests on fewer metrics than the full set. | No |

## Confidence — clarified

`metricCoverage` remains the **absolute** sum of base weights over qualifying
metrics (0..1). A P/B-only assessment therefore publishes with
`metricCoverage = 0.30`, i.e. a visibly lower confidence than a full-set one.

## Required test vectors (in addition to v1's)

| Test | Assertion |
|---|---|
| Loss-maker on P/B | PE N/A, PB DEFINED (600 history points), PEG N/A, EV/EBITDA `MISSING_EBITDA` → published, `REDUCED_METRIC_SET`, PB effective weight `1.000000000000`, no `INSUFFICIENT_METRIC_COVERAGE`. |
| Real gap still withheld | PE `MISSING_EPS`, PB DEFINED, PEG `MISSING_GROWTH`, EV/EBITDA `MISSING_EBITDA` → coverage 0.375 → `INSUFFICIENT_METRIC_COVERAGE`, not published. |
| Only PEG defined (v1 vector, unchanged outcome) | PE and PB present as gaps → withheld. |
| Rule version | Every result reports `valuation-v2`. |
