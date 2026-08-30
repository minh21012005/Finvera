# Feature Specification: Extended Fundamentals and Screening

**Feature Directory**: `009-extended-fundamentals-screening`
**Created**: 2026-08-30
**Status**: Clarified 2026-08-30 (research closes units and scope; no open markers)
**SRS References**: Section 9.1 (fundamental metrics), Section 13 (screener),
58 (Requirements Index — Fundamentals, Screening)
**SRS Requirement IDs**: SRS-FUN-01, SRS-SCR-01, SRS-SCR-02 (extension of
realized capabilities; no new SRS capability claimed).
**Input**: User description: "Owner decision 2026-08-30: where the accepted
provider already returns a fact, Finvera should use it. Feature 008 mapped the
derivable statement facts; the KBS ratio dataset still has ~40 confirmed,
unit-known rows that no screen shows and no screener filter can use —
profitability margins, liquidity, efficiency, leverage, beta, P/S, and the
bank-specific NIM/CIR/LDR set. The owner wants them visible on the
fundamentals section and usable as screener filters, with the same provenance
and honesty rules as every other fact. Provider-computed valuation ratios
(EV/EBIT, EV/EBITDA) and cash-flow-derived ratios that the provider returns
as literal zeros stay out."

## Scope Summary *(mandatory)*

The fundamentals section shows fifteen or so metrics while the accepted
provider reports fifty-eight ratio rows per company, in known units. An
investor comparing two companies today cannot see gross or net margin,
liquidity cover, interest coverage, asset turnover, leverage against assets,
beta, or price-to-sales, and cannot screen the universe by any of them. For
banks the standard NIM / cost-to-income / loan-to-deposit trio is likewise
invisible.

This feature maps the confirmed ratio rows into the existing fundamentals
catalog as provider-reported facts (each row carries its source, period and
unit), displays them by category on the stock page, and adds the most
decision-relevant ones as screener filters. It also corrects a unit defect the
probe surfaced: the provider's dividend yield is a fraction (0.04), while
Finvera's catalog declares percent, so today it displays as 0,04 %.

Nothing is recalculated: a provider ratio is stored as observed. Ratios that
Finvera itself defines (`PE`, `PB`, `EV_EBITDA`, `PEG`) remain computed by
`valuation-v1` at Finvera's current price; provider valuation ratios are not
imported so two definitions never coexist under one name.

### In Scope

- Catalog, import allowlist, and exporter mapping for the confirmed ratio
  rows listed in `research.md` R-002 (profitability, liquidity, efficiency,
  leverage, market: beta and P/S, bank-specific).
- Fundamentals summary carries each new metric from the newest accepted
  period; the stock page groups them by category.
- Screener fundamental filters: `psMin/Max`, `betaMin/Max`,
  `grossMarginMin/Max`, `netMarginMin/Max`, `currentRatioMin/Max`,
  `interestCoverageMin/Max`, `debtToAssetsMin/Max` (inclusive ranges, same
  AND semantics as `screener-v1`).
- Dividend-yield unit correction at the exporter boundary (fraction → percent).

### Out of Scope

- Provider-computed `ev_ebit`, `ev_ebitda`, `pe_ratio`, `pb_ratio` (Finvera
  computes its own under `valuation-v1`; importing the provider's would create
  a second definition at a different price basis).
- Cash-flow-derived ratios the provider returns as literal `0.0` for every
  probed symbol (`accrual_ratio_*`, `cash_return_*`, `debt_coverage`,
  `cash_flow_per_share_cps`, …): a provider zero is not a fact.
- Days-based turnover mirrors (`days_of_sales_outstanding`, …) — derivable
  from the turnover we do store; no story needs both.
- Strategy/risk rule changes (using beta in `strategy-signal-v1` would be a
  rule version change — separate feature).
- TCBS foreign buy/sell quantities and order book (Feature 008 left them out;
  unchanged).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Fuller fundamentals section (Priority: P1)

As the owner, when I open a stock's fundamentals, I want to see profitability
margins, liquidity and coverage, efficiency, leverage against assets, beta and
P/S — and for a bank, NIM, cost-to-income and loan-to-deposit — each with its
period and provenance, so I can judge quality without leaving Finvera.

**Why this priority**: the facts already arrive in every fundamentals package;
showing them is the smallest slice with the widest daily use.

**Independent Test**: import a fundamentals package for VNM and MBB; the
fundamentals section shows `GROSS_MARGIN`, `NET_MARGIN`, `CURRENT_RATIO`,
`INTEREST_COVERAGE`, `TOTAL_ASSET_TURNOVER`, `DEBT_TO_ASSETS`, `BETA`, `PS`
for VNM as `DEFINED` with the period label, and `NIM`, `COST_INCOME_RATIO`,
`LOAN_TO_DEPOSIT` for MBB; a metric the provider does not report for that
company is `MISSING`/`NOT_REPORTED`, never zero.

**Acceptance Scenarios**:

1. **Given** an accepted quarterly report with `gross_margin = 41.8`, **When**
   the summary is read, **Then** `GROSS_MARGIN` is `DEFINED`, value `41.8`,
   unit `PERCENT`.
2. **Given** a bank report without `gross_margin`, **When** the summary is
   read, **Then** `GROSS_MARGIN` is `MISSING` with `NOT_REPORTED`, and `NIM`
   is `DEFINED`.
3. **Given** `dividend_yield = 0.04` from the provider, **When** imported,
   **Then** `DIVIDEND_YIELD` is stored and displayed as `4` percent.

---

### User Story 2 - Screen by quality and liquidity (Priority: P2)

As the owner, I want to screen the universe by gross/net margin, current
ratio, interest coverage, debt-to-assets, beta and P/S, combined with the
existing filters, so I can shortlist by quality rather than price action
alone.

**Why this priority**: turns US1's facts into a decision tool; depends on US1.

**Independent Test**: run a screen `netMarginMin=10, currentRatioMin=1.5,
betaMax=1.0`; every match has all three metrics `DEFINED` and inside range;
the disclosure counts instruments excluded for `MISSING` metrics separately
from those that did not match.

**Acceptance Scenarios**:

1. **Given** a stock whose `NET_MARGIN` is `DEFINED = 14.81`, **When**
   `netMarginMin = 10`, **Then** it matches and `matchedValues.netMargin` is
   `14.81`.
2. **Given** a stock whose `CURRENT_RATIO` is `MISSING`, **When**
   `currentRatioMin` is set, **Then** it is excluded with reason
   `NOT_REPORTED`, not treated as `0`.
3. **Given** `betaMin > betaMax`, **When** the screen is submitted, **Then**
   the request is rejected `INVALID_FILTER_RANGE` (existing rule).

### Edge and Failure Cases *(mandatory)*

- Provider row present with value `0.0` for a ratio the company genuinely
  reports (e.g. `dividend_yield` for a non-payer): stored as `0` — only the
  cash-flow family, where the probe shows unconditional zeros across all
  companies, is excluded from mapping.
- A percent-scale ratio must never inherit statement `unitScale`; ratios and
  per-share values stay in the unscaled set.
- Bank vs non-bank row sets differ; absence is `NOT_REPORTED`, never a guess.
- Ratio columns arrive unordered with pandas `_1` duplicates; the exporter's
  period parser ignores them (Feature 008 R-003).
- Restatements supersede prior revisions as today.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept, store and display every ratio metric in
  research R-002 with its Vietnamese/English display name, unit and period.
- **FR-002**: The fundamentals section MUST group metrics by category
  (profitability, liquidity, efficiency, leverage, market, bank) and show
  `NOT_REPORTED` rather than a number for an absent metric.
- **FR-003**: The screener MUST accept the seven new inclusive-range filter
  pairs and evaluate them under `screener-v1`'s existing AND / unavailable
  semantics without recalculating any metric.
- **FR-004**: `DIVIDEND_YIELD` MUST be stored and displayed in percent.

### Data and Financial Semantics

- **DATA-001**: Every mapped field's `item_id`, label, unit and sample value
  are recorded in `research.md` from a probe before mapping.
- **DATA-002**: Provider ratios are stored as observed (`numeric(28,6)`),
  flagged `PROVIDER_REPORTED` in `quality_reason`, and never recomputed.
- **DATA-003**: Percent-scale ratios are stored in percent points; unitless
  ratios as plain ratios; neither inherits `unitScale`.
- **DATA-004**: A provider zero for the excluded cash-flow ratio family is
  not imported; for mapped fields, zero is stored as zero.

### Security and Privacy

- **SEC-001**: No new endpoint, host, or credential; logs carry codes and counts only.

### Non-Functional Requirements

- **NFR-001**: Screener pass-2 fundamental fetch remains one bulk query per
  screen (new codes ride the existing summary-metric fetch).
- **NFR-002**: Re-import is idempotent (existing `packageSha256` guard).

### Key Entities

- **Fundamental metric catalog entry**: adds ~20 codes with category
  (`RATIO`), unit (`PERCENT`/`RATIO`), scale, sign policy.

## Assumptions and Dependencies *(mandatory)*

### Assumptions

- Percent-scale detection per field is taken from the probe magnitudes and
  labels (research R-002 table), not inferred at runtime.

### Dependencies

- Feature 008 exporter (`derivation`, annual pass) and catalog version
  `fundamental-metric-catalog-v1` (new rows added under the same version —
  additive).

## Success Criteria *(mandatory)*

- **SC-001**: After refresh, ≥ 90 % of listed non-bank symbols have
  `GROSS_MARGIN`, `NET_MARGIN`, `CURRENT_RATIO` `DEFINED`; ≥ 90 % of banks have
  `NIM` `DEFINED` (measured from the summary table).
- **SC-002**: A screen combining two new filters returns only instruments
  whose values satisfy both, verified by unit tests with mixed
  `DEFINED`/`MISSING` fixtures.
- **SC-003**: `DIVIDEND_YIELD` for VNM displays in the 3–6 % band, not
  0,04 %.

## Requirement Traceability *(mandatory)*

| Requirement | User Story / Scenario | Success or Verification Measure |
|---|---|---|
| FR-001, DATA-001..003 | US1 / 1-2 | SC-001, catalog + acceptance tests |
| FR-002 | US1 | FE grouping test |
| FR-003 | US2 / 1-3 | SC-002, `ScreenerV1Tests` |
| FR-004 | US1 / 3 | SC-003, exporter test |
| DATA-004 | edge cases | exporter test (zero family excluded) |
| NFR-001 | US2 | unchanged fetch path (`ScreenerService`) |
