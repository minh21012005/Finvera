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
| `REDUCED_METRIC_SET` | A **core** metric (`PE` or `PB`) was `NOT_APPLICABLE` and excluded from the coverage denominator; the classification rests on the other core multiple. `PEG` being `NOT_APPLICABLE` (growth ≤ 0) is routine and is disclosed only on its metric row (clarified 2026-08-31 after the first v2 run flagged 236 profitable companies for PEG alone). | No |

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

## Amendment 2026-08-31 (Q-55) — sector-peer inputs

Peer inputs for the sector basis are read from each peer's **persisted current
fundamental summary** (`fundamental_summary` / `fundamental_summary_metric`,
rule `fundamental-summary-v2`, latest `as_of_trading_date` then latest
`calculated_at`; a tie between sibling revisions is resolved by the smallest id)
and the peer's **latest current daily bar**, both fetched in bulk. The figures
are identical to what the calculator would produce for that peer at the time the
summary was (re)persisted; a peer without a persisted summary still goes through
the computing path. Measured on the owner's machine: MBB (24 peers) 8.5 s →
after the amendment see docs/REMEDIATION_PLAN.md Q-55. No change to the formulas,
weights, bases or floors.

## Addendum 2026-08-31 (Feature 022): PS as an informational metric

`PS = marketCap / REVENUE_TTM` joins the metric list beside DIVIDEND_YIELD as an **informational**
metric: weight 0, excluded from the composite score, the coverage denominator and the PE/PB core
gate. Purpose: a defensible price multiple for loss-making companies where PE is NOT_APPLICABLE.
Applicability: `MISSING_REVENUE` when REVENUE_TTM is absent, `NEGATIVE_OR_ZERO_REVENUE`
(NOT_APPLICABLE) when it is <= 0, `MISSING_MARKET_CAP_INPUTS` when price or shares are missing.
Percentile bases accrue for PS exactly like the other metrics as history builds. DIVIDEND_YIELD's
input chain is restored by `vci-dps-cash-dividends-over-shares-v1` (contract
vci-derived-ratios-v1): DIVIDEND_PER_SHARE -> summary DIVIDEND_PER_SHARE_TTM -> yield vs price.
BETA stays deferred: no anchored definition adds decision value for a personal investor (owner
scope decision 2026-08-31, "đủ và chính xác, không thừa").

