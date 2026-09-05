# Feature 023: Own-History Percentile on One Basis (`valuation-v3`)

**Status**: Specified 2026-09-05 (owner decision (b) on specs/017 R-004/R-005)
**Closes**: docs/REMEDIATION_PLAN.md P2-05; specs/017 SC-002
**Contracts**: `valuation-v3` (this feature), supersedes `valuation-v2` for new assessments
**SRS References**: SRS-VAL-01 (valuation relative to own history), SRS-VAL-02 (basis disclosure)
**Predecessor research**: specs/017-history-basis-consistency (R-001…R-005)

## Problem

The stock detail page tells the user where today's P/E sits inside the stock's own three-year
history — "phân vị 30 %" means cheaper than 70 % of its past sessions. That sentence is built
from two numbers measured with **different rulers**:

- today's P/E divides price by **quarter-TTM EPS** (the four newest quarters);
- 65–100 % of the 750 historical points divide price by **fiscal-year EPS**, because quarterly
  EPS reaches back only eight quarters and 468 of 1,522 instruments report none at all.

For a growing company TTM EPS exceeds the last FY EPS, so its TTM P/E is systematically lower
than the FY-basis history it is ranked against — it looks "cheap versus itself" because the
ruler changed, not because the price did. Measured on the VCI universe (specs/017 R-004): the two
bases differ by a median 7.6 % on the same date (p90 31 %, max 73 %), and the resulting percentile
moves by up to 58 points (VCB). That is large enough to flip the verdict a user reads.

The universe splits (R-005): the 468 instruments without quarterly EPS already compare annual to
annual and are **correct**; the 1,054 with quarterly EPS are **biased**. Two stocks side by side on
the same screen carry percentiles of different quality, and nothing tells the user which is which.

## Decision (owner, 2026-09-05)

Option (b) from specs/017: rank today's value against the own-history series **on the series'
own basis**. Flow metrics (those built from period aggregates — P/E, PEG, EV/EBITDA, P/S, dividend
yield) get a fiscal-year-basis comparison value for the percentile; the headline value stays on
quarter-TTM where it exists. Point-in-time metrics (P/B) are unaffected. The basis is disclosed on
every assessment and every metric row. Rejected: (a) disclosure only — the numbers say the bias is
too large to footnote; (c) shortening the window to where quarter-TTM exists — ~260 sessions is
too short for a percentile and impossible for 31 % of the universe.

## Scope

In scope: the own-history basis (Basis A) of the valuation engine, its persistence, its API
representation, the frontend rendering and analyst-tool exposure of the new basis fields, the
independent verifier, and the rule-version rollout.

Out of scope, recorded deliberately:
- **Sector cross-section (Basis B).** Peers are compared on their headline values, which mix TTM
  and annual across companies. Same class of issue, different remedy (per-peer FY inputs from
  reports), separate measurement needed first → follow-up item in docs/REMEDIATION_PLAN.md.
- Any change to `fundamental-summary-v2`'s persisted output, formulas, weights, floors or bands.
- Deepening quarterly history (provider-bound; it accumulates on its own because nothing is deleted).

## Requirements

- **FR-001** For every flow metric, the own-history percentile MUST rank a comparison value
  computed on the fiscal-year basis against a series computed on the fiscal-year basis at every
  point; the headline `value` of the metric is unchanged from `valuation-v2`.
- **FR-002** For point-in-time metrics the series and comparison value MUST be the newest visible
  report's figure at each point (unchanged); the basis is disclosed as such.
- **FR-003** A metric whose headline is DEFINED but whose fiscal-year comparison value is not
  DEFINED MUST carry no own-history percentile, and the assessment MUST disclose that state with a
  reason code; nothing is defaulted to the headline value.
- **FR-004** Each metric row MUST expose the basis used for its own-history percentile and the
  comparison value that was ranked, so a reader can reproduce the percentile.
- **FR-005** Every published assessment that used Basis A MUST carry a reason code stating that
  flow-metric percentiles are on the fiscal-year basis.
- **FR-006** The frontend MUST render the percentile with its basis and comparison value
  (e.g. "Phân vị 42 % — theo P/E năm 16,1") and MUST accept `ruleVersion` `valuation-v3`.
- **FR-007** The analyst tool response MUST carry the same basis fields so explanations attribute
  the percentile to the right number.
- **DATA-001** Persisted `valuation_metric` rows MUST store the basis and comparison value with
  explicit precision; a stored own-history percentile without a stored basis and comparison value
  is a constraint violation.
- **DATA-002** Assessments MUST be written under `rule_version = 'valuation-v3'`; `valuation-v2`
  rows remain readable under their version and are never rewritten.
- **DATA-003** For an instrument with no quarterly aggregates at all, `valuation-v3` MUST produce
  the same percentiles as `valuation-v2` would for the same inputs (the fix changes nothing where
  nothing was wrong).
- **DATA-004** The provider's trailing EPS (`PROVIDER_TRAILING_EPS`, a TTM figure) MUST NOT enter
  a fiscal-year-basis series or comparison value.
- **NFR-001** Given identical accepted inputs, replaying an assessment MUST reproduce identical
  decimals (existing determinism rule; new fields included).
- **NFR-002** The independent verifier MUST recompute the fiscal-year-basis series and comparison
  value from raw reports and bars and agree with the stored percentile within the existing
  tolerance.

## Success criteria

- **SC-1** `ValuationV1Tests` vectors: growth company ranks the FY-basis P/E, not the TTM one;
  turnaround (TTM EPS > 0, FY EPS ≤ 0) yields no P/E percentile plus the disclosure code; annual-only
  company reproduces v2's percentile; P/B unchanged; replay identical; rule version reported.
- **SC-2** Integration test persists an assessment whose P/E row carries basis `FISCAL_YEAR` and a
  comparison value equal to price / latest FY EPS while `value` equals price / TTM EPS.
- **SC-3** FE renders the basis wording; lint, build and vitest green.
- **SC-4** Backend suite green; AI service tests green.
- **SC-5** After the owner's next refresh: `verify_calcs.py` valuation section green on the v3
  rows; `history_basis_study.py` shows the ranked value and the series on one basis.
- **SC-6** P2-05 closed in docs/REMEDIATION_PLAN.md with the measurement and the decision.

## Acceptance scenarios

1. **Given** VNM as stored on 2026-08-28 — quarterly EPS 1,369 + 1,051 + 1,224 + 1,084 = TTM 4,728,
   FY2025 EPS 4,028, close 62,300 — **when** the assessment is computed, **then** the P/E row shows
   `value` 13.18 (62,300 / 4,728, unchanged from v2), `ownHistoryComparisonValue` 15.47
   (62,300 / 4,028), `ownHistoryBasis` FISCAL_YEAR, and the percentile is the rank of 15.47 inside
   a series of FY-basis P/E points. (Under v2 the stored percentile was 14.2 — the TTM 13.18 ranked
   against a mostly-FY history; this is the bias being removed.)
2. **Given** MBB (no quarterly EPS), **when** the assessment is computed, **then** the P/E
   comparison value equals the headline value and the percentile equals what v2 produced.
3. **Given** a company with TTM EPS 1,000 and FY EPS −500, **when** computed, **then** P/E is
   DEFINED with no own-history percentile, the assessment carries
   `HISTORY_COMPARISON_UNAVAILABLE`, and P/B still carries its Basis A percentile.
4. **Given** a v3 response, **when** the frontend parses it, **then** it accepts the rule version
   and shows "theo P/E năm" beside the percentile; a v2 response is still parsed.
