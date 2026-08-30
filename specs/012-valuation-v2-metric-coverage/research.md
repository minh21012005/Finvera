# Research: Valuation v2 — metric coverage

**Feature**: `012-valuation-v2-metric-coverage` · 2026-08-31

## R-001 — Measured cause (private database, after the 2026-08-30 refresh)

```
latest assessment per instrument: 1,524; published 650 (42.7 %)
with own-history basis:            888; published 650; withheld 238
  withheld reasons: INSUFFICIENT_METRIC_COVERAGE 181, PRICE_STALE 78, CORE_METRIC_UNAVAILABLE 4
valuation_metric (latest):  PE DEFINED 1,239 / NOT_APPLICABLE 222 (negative EPS)
                            PB DEFINED 1,380 / NOT_APPLICABLE 77
                            EV_EBITDA MISSING 1,491 (MISSING_EBITDA 1,149, MISSING_EV_INPUTS 342), NOT_APPLICABLE 33, DEFINED 0
                            PEG DEFINED 605 / NOT_APPLICABLE 765 / MISSING 154
```

With EV/EBITDA never `DEFINED`, v1's gate `Σ baseWeight(DEFINED ∧ has basis)
≥ 0.50` can only be met by PE (0.40) plus at least one more metric, or PB +
PEG (0.40) — never. A loss-making company therefore cannot publish regardless
of how complete its data is.

## R-002 — Why "not applicable" is not a data gap

`NOT_APPLICABLE` is a computed fact about the company (EPS ≤ 0, growth ≤ 0),
carried with its own reason on the metric row. Relative valuation practice
values such companies on P/B (and sales multiples); the honest treatment is to
score the metrics that apply and disclose the reduced set, not to withhold as
if inputs were missing. A `MISSING` metric (`MISSING_EPS`, `MISSING_BVPS`,
`MISSING_GROWTH`, `MISSING_PRICE`) remains a data gap and keeps counting
against coverage.

## R-003 — EV/EBITDA is structurally unobtainable (Feature 008 R-001, 011 R-005)

The provider returns no balance sheet, so `TOTAL_DEBT`/`CASH_AND_EQUIVALENTS`
never exist and EV cannot be formed; `EBITDA_TTM` exists (derived) for
non-financials only. Both `MISSING_EBITDA` and `MISSING_EV_INPUTS` are
therefore excluded from the obtainable set. If a future provider supplies the
inputs, EV/EBITDA becomes `DEFINED` and participates with no code change.

## R-004 — Decision (rule `valuation-v2`)

```
obtainable = { m ∈ scored : applicability(m) ≠ NOT_APPLICABLE
                            ∧ ¬(m = EV_EBITDA ∧ MISSING ∧ reason ∈ {MISSING_EBITDA, MISSING_EV_INPUTS}) }
qualifying = { m ∈ obtainable : DEFINED ∧ percentile available in ≥ 1 basis }
coverage   = Σ w(qualifying) / Σ w(obtainable)           (scale-12 division)
gate       : coverage ≥ 0.50   else INSUFFICIENT_METRIC_COVERAGE
REDUCED_METRIC_SET when a CORE metric (PE/PB) is NOT_APPLICABLE (non-blocking; PEG alone does not raise it)
confidence : metricCoverage = Σ w(qualifying)   (absolute, unchanged from v1)
```

Everything else — CORE_METRIC_UNAVAILABLE, basis floors, per-basis weight
renormalisation, bands, confidence weights — is v1 verbatim.

Worked example (loss-maker): PE N/A, PB DEFINED with history, PEG N/A,
EV/EBITDA MISSING_EBITDA → obtainable = {PB} (0.30), qualifying = {PB},
coverage = 1.0 → published; PB effective weight 1.0; confidence =
100 × (0.45 × 0.30 + 0.35 × 0.5 + 0.20 × historyDepth).

Data-gap example: PE MISSING_EPS, PB DEFINED, PEG MISSING_GROWTH, EV/EBITDA
MISSING_EBITDA → obtainable = {PE, PB, PEG} (0.80), qualifying = {PB} (0.30),
coverage 0.375 → withheld.

**Rejected**: dropping EV/EBITDA from the base weights (would need a third
version when a balance-sheet source appears); lowering the floor to 0.30
(publishes on PB even when PE data is merely missing — hides gaps).

## Constitution check

I ✔ new rule version, v1 rows retained, every factor disclosed; II ✔
`REDUCED_METRIC_SET` and per-metric reasons; VI ✔ two new test vectors with
decimal expectations; VIII ✔ minimal change to the gate only.
