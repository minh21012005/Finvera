# Implementation Plan: Valuation v2 — metric coverage

**Feature**: `012-valuation-v2-metric-coverage` · 2026-08-31 · **Spec**: `spec.md` · **Research**: `research.md` · **Contract**: `contracts/valuation-v2.md`

## Summary

One gate changes inside the valuation rule; the rule version is bumped so
stored v1 assessments stay attributable. API/FE accept the new version string.

## Constitution Check

| Principle | Assessment |
|---|---|
| I | New `rule_version`; formulas untouched; decimal scale-12 division for the ratio; every factor disclosed (`REDUCED_METRIC_SET`, per-metric reasons, effective weights). ✔ |
| II | Reduced-set assessments carry the reason code; confidence reflects it. ✔ |
| III | Domain-only change plus version string in service/API/FE. ✔ |
| V | spec → research → contract → plan → tasks → code. ✔ |
| VI | Two new decimal test vectors; existing v1 vectors retained (outcomes re-checked against v2). ✔ |
| VIII | Smallest change that separates "not applicable" from "missing". ✔ |

## Changes

- `ValuationV1.java`: `RULE_VERSION = "valuation-v2"`; compute `obtainableWeight`;
  gate on `divide12(qualifying, obtainable) >= 0.50`; add `REDUCED_METRIC_SET`
  when any scored metric is `NOT_APPLICABLE`. Javadoc points to the v2 contract.
- `ValuationV1Tests`: `RULE_VERSION` from the constant; new vectors.
- `stock-detail.openapi.yaml`: `ruleVersion` const `valuation-v2`.
- FE `stock-detail.ts` + `fundamentals-valuation.test.tsx`: accept/emit `valuation-v2`.
- Warmup: the version bump makes every instrument's latest assessment
  non-current for v2 → recomputed on the next stage 7.

## Rollout

Next `.\refresh-data.ps1` (stage 7) recomputes; measurement query for SC-001 is
the one in docs/REMEDIATION_PLAN.md Evidence.
