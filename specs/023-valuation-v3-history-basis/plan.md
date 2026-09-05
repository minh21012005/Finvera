# Plan: Feature 023 — Own-History Percentile on One Basis (`valuation-v3`)

## Approach

Change the **comparison**, not the metrics. Every formula, weight, band and floor of `valuation-v2`
stays; what changes is which number is ranked against which series. The own-history series is
rebuilt in a fiscal-year aggregate mode of the existing summary calculator (the annual fallback
path it already has, forced on), and a second, fiscal-year-mode computation at today's price yields
the comparison value per metric. `ValuationV1.classify` ranks that comparison value for flow
metrics and the headline value for point-in-time metrics, and reports the basis and the ranked
value on every metric row. Rule version bumps to `valuation-v3`; the warmup recomputes the universe
on the owner's next refresh, exactly as v2 rolled out.

## Components

| Layer | Change |
|---|---|
| `stock/domain/fundamentals/FundamentalSummaryCalculator.java` | `AggregateBasis { PREFER_QUARTERS, FISCAL_YEAR }`; 3-arg `calculate`; FISCAL_YEAR forces annual aggregates + annual growth and disables the trailing-EPS fallback. 2-arg `calculate` unchanged (delegates). |
| `stock/domain/valuation/ValuationV1.java` | `RULE_VERSION = "valuation-v3"`; `Inputs.ownHistoryComparison`; flow/point-in-time classification; percentile on `C_A(m)`; `MetricResult` +basis +comparison; reason codes `HISTORY_FISCAL_YEAR_BASIS`, `HISTORY_COMPARISON_UNAVAILABLE`. |
| `stock/service/ValuationService.java` | `buildOwnHistorySeries` uses FISCAL_YEAR mode; new `buildOwnHistoryComparison(...)` (FISCAL_YEAR summary over all accepted reports at current price → `computeMetrics` → map); persists the two new columns; `ValuationMetric` record +2 fields. |
| `stock/entity/ValuationMetricEntity.java` | two new columns; constructor enforces the percentile ⇒ basis+comparison invariant. |
| `db/migration/V019__valuation_metric_own_history_basis.sql` | add columns + enum check (see data-model.md for why the invariant is not a SQL check). |
| `stock/dto/StockValuationResponse.java` | `MetricResponse` +2 fields. |
| `analyst/dto/ToolResponseDtos.java`, `analyst/service/ToolDelegateService.java` | `MetricFactDto` +2 fields, mapped. |
| `specs/002-stock-detail-analysis/contracts/stock-detail.openapi.yaml` | additive metric properties; `ruleVersion` accepts `valuation-v3`. |
| `finvera-fe/src/features/stock-detail/api/stock-detail.ts` | accept both rule versions; parse the two fields. |
| `finvera-fe/.../components/stock-valuation.tsx`, `format/explain-evidence.ts` | render "Phân vị X % — theo P/E năm 15,47 (750 phiên)"; explain factor names the ranked value. |
| `finvera-fe/src/shared/format/reason-codes.ts` | labels for the two new codes. |
| `tools/verification/verify_calcs.py` | valuation section: FY-basis series + comparison value; asserts stored comparison value and percentile; targets `rule_version='valuation-v3'`. |
| `tools/verification/history_basis_study.py` | header note: after v3 the gap is informational. |
| Tests | `ValuationV1Tests` (7 vectors from the contract), `ValuationServiceTests` (persisted basis/comparison on a quarters+annual fixture), `FundamentalSummaryCalculatorTests` (FISCAL_YEAR mode), `ToolDelegateServiceTests` arity, FE vitest (parser both versions, rendering, explain wording). |
| Docs | specs/017 spec closed with the decision; REMEDIATION P2-05 → done after implementation, new follow-up for Basis B; ARCHITECTURE §10 decision index row; changelog. |

## Constitution check (before design and after)

- **I. Deterministic finance core** — the rank is a pure function of accepted inputs; new fields
  are decimals with declared scale; replay vector required. ✔
- **II. Evidence, provenance, temporal truth** — the visibility boundary per session is unchanged;
  the basis of every percentile is disclosed on the row and the assessment; the comparison value
  is stored so the rank is reproducible; `PROVIDER_TRAILING_EPS` is kept out of the FY basis
  rather than silently blended. ✔
- **III. Boundaries** — controller/DTO/service/domain layering unchanged; the FE maps the DTO
  explicitly against the amended OpenAPI; the analyst tool DTO mirrors it. ✔
- **IV. Responsible decision support** — the change makes a user-facing verdict *less* misleading
  and says so; disclaimer code unchanged. ✔
- **V. Spec before code** — this plan, the contract and tasks precede any code; requirement ids
  map to tasks and tests. ✔
- **VI. Risk-based testing** — unit vectors for every contract row, an integration test for
  persistence, FE tests for parsing both versions, verifier recomputation. ✔
- **VIII. Modular simplicity** — one enum parameter on an existing calculator instead of a second
  calculator; no new service, dependency or table. ✔
- Complexity tracking: none.

## Why not a SQL invariant for percentile ⇒ basis

`valuation-v2` rows carry percentiles with no basis and must stay readable; a column check would
reject them. The invariant is enforced where v3 rows are made (entity constructor) and asserted by
the integration test (data-model.md).

## Risks and mitigations

- **Score drift on rollout.** Percentiles move for the 1,054 instruments with quarterly EPS; the
  displayed classification may change for some. That is the correction, not a regression, and the
  reason code makes it legible. Measure on the first v3 warmup: count of instruments whose
  classification changed vs their last v2 row (query in quickstart.md) — recorded in research.
- **FE outage window.** The FE throws on unknown rule versions; backend and FE ship together
  (research R-005).
- **Staler denominator.** FY EPS can trail TTM by up to ~9–15 months; exposed via
  `ownHistoryComparisonValue` and `HISTORY_FISCAL_YEAR_BASIS`, never implied.

## Rollout

1. Ship backend + FE + verifier; suites green.
2. Owner: `.\refresh-data.ps1` — stage 7 recomputes 1,522 assessments under `valuation-v3` (also
   picks up the sessions missing since 2026-08-28).
3. Post-refresh: `python tools/verification/verify_calcs.py` valuation section green;
   `history_basis_study.py` re-run; classification-drift count recorded; P2-05 closed.
