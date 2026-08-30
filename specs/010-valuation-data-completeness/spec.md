# Feature Specification: Valuation Data Completeness

**Feature Directory**: `010-valuation-data-completeness`
**Created**: 2026-08-30 · **Status**: Clarified (research closes all inputs)
**SRS References**: Section 9.1 (fundamentals), Section 10 (valuation), 58
**SRS Requirement IDs**: SRS-FUN-01, SRS-VAL-01, SRS-STK-01 (making already-realized
capabilities actually deliver on real data).
**Input**: Owner review 2026-08-30 against the private database: `valuation-v1`
publishes for **7 of 3,050** current assessments (0.2 %). Root causes measured
(research R-001): `shares_outstanding` is null for all 1,524 profiles, bank
EPS is never imported (`earning_per_share_vnd` vs `earnings_per_share_vnd`),
and the accepted provider returns only **4 fiscal periods** per dataset on the
Community tier, so the 8-quarter growth rule and the 500-session own-history
basis are structurally unreachable. Owner instruction: use every fact the
provider does supply so deterministic outputs actually publish.

## Scope Summary *(mandatory)*

Valuation, PEG, growth screening and market capitalisation all exist in code
and pass their tests, yet almost never publish on real data because three
inputs are missing or unreachable. This feature fills the two data gaps
(outstanding shares, bank EPS) and adds an explicitly versioned fallback to
annual reports (`fundamental-summary-v2`) so TTM and growth are computable
from the four quarters plus four fiscal years the provider does return.

Nothing is estimated: a fallback value is labelled with the basis it came
from (`ANNUAL_BASIS`), and where neither basis is complete the metric stays
`MISSING`.

### In Scope

- `equity_profile.shares_outstanding` and `free_float_ratio` from the
  provider's company overview; existing profiles get a new effective-dated
  revision when the value arrives or changes.
- Bank EPS `item_id` mapped.
- `fundamental-summary-v2`: TTM from the latest annual report when fewer than
  four quarters are visible; growth from annual-over-prior-annual when eight
  quarters are not visible; metric-level basis disclosure.
- Valuation and screener consume v2 through the existing `RULE_VERSION`
  constant (no valuation rule change).

### Out of Scope

- Any change to `valuation-v1` formulas, weights, or the 500-point history floor.
- Buying a higher provider tier (owner decision; would simply reduce how often
  the fallback is used).
- Balance-sheet inputs (Feature 008 R-001: unavailable).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Valuation actually publishes (Priority: P1)

As the owner, when I open a mainstream stock (VNM, FPT, MBB), I want a
published valuation with PE/PB and, where the sector basis is enabled, a
sector percentile — instead of `NO_COMPARISON_BASIS` / `CORE_METRIC_UNAVAILABLE`.

**Independent Test**: after a refresh, the share of listed symbols with a
published valuation rises from 0.2 % to a measured majority; VNM's assessment
has `PE`, `PB` `DEFINED`, market cap present, and every fallback-derived input
disclosed.

**Acceptance Scenarios**:

1. **Given** a profile revision with `shares_outstanding`, **When** the overview
   is read, **Then** `marketCapVnd` is present and `PB` can be computed from
   `EQUITY_ATTRIBUTABLE_TO_PARENT/shares` or `BVPS`.
2. **Given** a bank whose EPS row is `earning_per_share_vnd`, **When** the
   fundamentals package is imported, **Then** `EPS` is `DEFINED`.
3. **Given** only 3 quarters visible as of a historical session but an annual
   report for the prior fiscal year, **When** the v2 summary is computed,
   **Then** `EPS_TTM` is `DEFINED` from the annual report with reason
   `ANNUAL_BASIS`.

### User Story 2 - Growth and PEG from annual data (Priority: P2)

As the owner, I want revenue/EPS growth and PEG to publish from year-over-year
annual figures when eight quarters are not available, clearly labelled.

**Acceptance Scenarios**:

1. **Given** 4 quarters and annual reports FY2025/FY2024, **When** the v2
   summary is computed, **Then** `EPS_GROWTH_PERCENT` = `(EPS_FY2025 / EPS_FY2024 − 1) × 100`
   with reason `ANNUAL_BASIS`.
2. **Given** 8 quarters visible, **Then** the quarterly TTM-vs-prior-TTM rule
   is used unchanged and no `ANNUAL_BASIS` reason appears.
3. **Given** prior-year EPS ≤ 0, **Then** `NOT_APPLICABLE` as today.

### Edge and Failure Cases

- Provider overview lacks `outstanding_shares` (or ≤ 0): profile keeps
  `SHARES_OUTSTANDING_UNAVAILABLE`; market cap stays `null`.
- Shares change between refreshes: new profile revision, old row closed with
  `effective_to`; valuation history keeps using the current value (documented
  `HISTORY_SHARES_OUTSTANDING_HELD_CURRENT`).
- Annual and quarterly reports disagree on basis period: summary basis remains
  the newest period; only the fallback metrics carry `ANNUAL_BASIS`.

## Requirements

- **FR-001**: Profiles MUST carry provider-reported outstanding shares (and
  free-float ratio when present) with an effective-dated revision on change.
- **FR-002**: Bank EPS MUST be imported under the same `EPS` code.
- **FR-003**: `fundamental-summary-v2` MUST compute TTM and growth from annual
  reports when the quarterly rule is unsatisfiable, labelling each such metric
  `ANNUAL_BASIS`; it MUST NOT mix a partial quarter set with annual figures.
- **FR-004**: Valuation, PEG and screener growth filters MUST consume v2 without
  formula change.
- **DATA-001**: All new facts carry source, observation time and unit; shares
  are an integer count, free float a percent.
- **DATA-002**: v2 results are stored under `rule_version = fundamental-summary-v2`
  alongside v1 history (never rewritten).
- **NFR-001**: Warmup recomputes every summary once after the version bump.

## Success Criteria

- **SC-001**: ≥ 95 % of listed profiles have `shares_outstanding` after refresh.
- **SC-002**: Published valuations ≥ 50 % of listed symbols (from 0.2 %),
  measured from `valuation_assessment`.
- **SC-003**: `EPS_GROWTH_PERCENT` `DEFINED` for ≥ 60 % of symbols with two
  annual reports.

## Requirement Traceability

| Requirement | Story | Verification |
|---|---|---|
| FR-001, DATA-001 | US1/1 | exporter + importer tests; SC-001 |
| FR-002 | US1/2 | exporter test |
| FR-003, DATA-002 | US1/3, US2 | `FundamentalSummaryV2Tests`; SC-003 |
| FR-004 | US1, US2 | existing valuation/screener tests on v2 |
