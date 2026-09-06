# Feature Specification: Sector-basis consistency (research)

**Feature Directory**: `024-sector-basis-consistency`
**Created**: 2026-09-06 · **Status**: Closed 2026-09-06 — measured, owner decided **no change**
**SRS References**: Section 10 (valuation) · **SRS Requirement IDs**: SRS-VAL-02
**Input**: docs/REMEDIATION_PLAN.md **P2-11**, opened as the follow-up of Feature 023
(specs/023 research R-009): Basis B ranks the subject's headline multiple against peers' headline
multiples, and a headline is quarter-TTM for companies with quarterly EPS and annual for the rest —
the same mixed ruler Feature 023 removed from Basis A, but *between companies* instead of
*between dates*.

## Scope Summary *(mandatory)*

Measure — not change — how far a sector percentile moves when the whole cross-section is priced on
one basis, then decide between (a) keeping the freshest available ruler per constituent and
disclosing the composition, and (b) a `valuation-v4` rule that puts Basis B on the fiscal-year
basis the way Feature 023 did for Basis A. Tool:
`tools/verification/sector_basis_study.py` (read-only; the headline side is read from the persisted
`fundamental_summary`, i.e. the number Basis B actually consumes, not a re-derivation).

### In Scope
- The measurement and its record (research.md).
- The decision, with numbers.

### Out of Scope
- Any change to `valuation-v3` Basis A, to the weights, floors or bands.
- Fixing defects the measurement happens to expose — they get their own ids (see Findings 3).

## Findings (summary — details in research.md)

1. **Magnitude.** Across 26 qualifying sectors and 1,190 subjects, moving the whole cross-section
   to one fiscal-year ruler changes the P/E sector percentile by a median of **6.56 pp**
   (p90 27.25, max 100). 51 % of constituents are priced on quarter-TTM today, 49 % on an annual
   figure. Composite-score effect ≈ 0.8 point at the median, ≈ 2.3 at p90.
2. **No systematic tilt between the two groups.** Quarter-TTM members average percentile 49.9 and
   annual members 49.3 under today's mixed rule; 49.5 and 48.1 under a uniform fiscal-year rule.
   The mixed ruler does not systematically make either group look cheap or expensive — unlike
   Basis A, where the bias was directional for growing companies. What the mix produces is
   **per-company dispersion**, not a tilt.
3. **The measurement exposed a real defect, Q-60** (recorded separately, not fixed here):
   24 LISTED instruments are served an `EPS_TTM` summed from four quarters that are
   non-contiguous or older than that instrument's own newest annual report — up to seven years
   stale — while the summary labels its basis `2025` and its status `DELAYED`. SDY is served
   EPS_TTM −1,680 (so P/E is withheld as lossmaking) while its FY2025 report says +1,418.
   **Count corrected 2026-09-06**: 24 is the EPS-only figure. Scoping the fix (Feature 025,
   research R-001) showed the same bad window also feeds `NET_PROFIT_TTM`, `REVENUE_TTM`,
   `EBITDA_TTM` and `DIVIDEND_PER_SHARE_TTM` — **60 instruments** in all.

## Recommendation

**Option (a): keep the freshest ruler per constituent, disclose the composition. Do not rebuild
Basis B.** Three reasons, all from the numbers:

- The defect is dispersion without direction (Finding 2), so the argument that carried Feature 023
  — a systematic bias against growing companies — does not apply here.
- A cross-section answers "how expensive is this *now*". Uniform fiscal-year would discard the
  freshest earnings for the 51 % who have quarterly data, and it is demonstrably **worse** for the
  companies that move most: XPH's headline P/E is 1.71 on the provider's trailing earnings and
  4,833 on FY2025 EPS of 3 VND — a near-zero denominator. Uniform FY would rank XPH at percentile
  100 (most expensive in its sector) when the fresh figure makes it the cheapest.
- Basis A had no alternative: its history *was* mostly fiscal-year, so the subject had to meet it
  there. Basis B has both sides in the present and no such constraint.

What (a) would mean concretely: expose per assessment how many constituents were priced on each
ruler, so the sector percentile can be read for what it is.

## Decision (owner, 2026-09-06): no change

Option (b) is rejected on the numbers above. The disclosure half of option (a) was also declined:
with no directional bias between the two groups and a median composite-score effect under one
point, a line of extra UI text on every valuation buys the reader nothing they would act on, and
the owner's standing rule is "đủ và chính xác, không dư thừa". **Basis B therefore stays exactly
as it is; no code, no contract, no UI changes from this feature.**

What would reopen it: `tools/verification/sector_basis_study.py` is kept for exactly that. If a
provider change ever makes the mix *directional* — the two groups' mean percentiles pulling apart
in R-002's table rather than sitting together — the argument that closed this changes, and the
measurement is one command away.

## Success Criteria
| ID | Criterion |
|---|---|
| SC-001 | Study run on the VCI universe with the table recorded in research.md — **met 2026-09-06** |
| SC-002 | Decision (a) or (b) recorded with numbers; if (b), contract `valuation-v4` written before code — **met 2026-09-06**: neither; measured, no change made |
| SC-003 | Any defect the measurement exposes is recorded with its own id and evidence — **met**: Q-60 |
