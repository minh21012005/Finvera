# Tasks: Valuation v2 — metric coverage

Written before implementation (2026-08-31).

- [x] T001 [FR-001, FR-002, FR-004] `ValuationV1.java`: rule version `valuation-v2`, obtainable-weight coverage gate, `REDUCED_METRIC_SET`.
      Verify: `ValuationV1Tests` — loss-maker publishes on PB; real gap withheld; v1 vectors green; rule version constant.
      Evidence: `RULE_VERSION = valuation-v2`, `REDUCED_METRIC_SET`, `STRUCTURAL_EV_EBITDA_GAPS`, coverage = divide12(qualifying, obtainable); `v2LossMakerPublishesOnPriceToBookWithReducedMetricSet` (confidence 47), `v2RealDataGapStillWithholdsWithInsufficientMetricCoverage`; 25/25.
- [x] T002 [FR-004] OpenAPI `ruleVersion` const → `valuation-v2`; FE parser/type/test fixture → `valuation-v2`.
      Verify: FE vitest + lint/build.
      Evidence: OpenAPI const, `stock-detail.ts` type/parser, test fixture; three backend tests that spelled the literal now use the constant.
- [x] T003 Full backend suite; `docs/REMEDIATION_PLAN.md` Q-42 → DONE with plan for SC-001 measurement after the owner's next refresh.
      Evidence: see REMEDIATION_PLAN changelog 2026-08-31; SC-001 measured after the next stage-7 warmup.

| Requirement | Tasks |
|---|---|
| FR-001, FR-002 | T001 |
| FR-003 | T001 (confidence unchanged, asserted) |
| FR-004 | T001, T002 |
