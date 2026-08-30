# Feature Specification: Provider Ingestion Normalization

**Feature Directory**: `011-provider-ingestion-normalization`
**Created**: 2026-08-30 · **Status**: Clarified (research.md closes all inputs)
**SRS References**: Section 9.1 (fundamentals), 9.2 (price history), 10 (valuation)
**SRS Requirement IDs**: SRS-FUN-01, SRS-STK-01, SRS-MKT-02
**Input**: Owner instruction 2026-08-30 — "chuẩn hóa ngay từ luồng lấy data;
không tin toàn bộ; gọi provider để xem data, mapping, tận dụng đúng chưa" —
after Feature 010 showed that a wrong field name (`earning_per_share_vnd`)
silently removed bank EPS for months.

## Scope Summary *(mandatory)*

A field-by-field audit of everything the accepted provider returns
(research.md) found seven ingestion defects: the daily-bar `end` date is not
inclusive so the newest session is never imported; the overview "free float"
is a mislabelled charter-capital figure that would break the profile import;
quarter-column flow ratios (ROE, ROA, ROCE, NIM, turnovers, P/S, dividend
yield) are single-quarter values presented as annualized; trailing ratios in
the annual dataset are `0.0` placeholders; the dividend-yield ×100 rule was
wrong; insurance and securities companies use different statement ids for
revenue and profit; and the free-cash-flow derivation only knew the
non-financial ids. This feature fixes each at the exporter (canonical
package) or import boundary, versions every reinterpretation, and adds a
regression fixture built from the probe so a provider drift is caught.

### In Scope

- Daily-bar end-date padding with post-filter.
- Profile exporter: no free float; outstanding-share consistency check.
- `provider-ratio-facts-v2`: period-scoped ratio rules, dividend yield as
  reported (annual), trailing ratios as the annualized `ROE`/`ROA` in quarter
  packages.
- Statement id mapping for insurance and securities revenue/profit.
- `kbs-fcf-ocf-plus-capex-v2`.
- `fundamental-summary-v2` amended: annual-only codes read with annual
  fallback and `ANNUAL_BASIS`.
- Provider-schema regression fixture (item ids per company type) in the
  exporter tests.

### Out of Scope

- Balance-sheet or quarterly cash-flow data (confirmed null at the source).
- Bank revenue / bank operating profit / bank FCF (no comparable concept).
- Provider growth rows (basis undocumented, disagrees with statements).
- Any valuation or screener formula change.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Latest session is present (P1)

As the owner, after a routine refresh on day D, I want the bars of the last
completed session (D or D−1) in the database, not two sessions behind.

**Acceptance**: with a provider returning bars through 2026-08-28 and
`--end 2026-08-30`, the package contains 2026-08-28 and nothing after
2026-08-30.

### User Story 2 - Ratios mean what their labels say (P1)

As an investor, when I screen "ROE ≥ 15 %" or read ROE on the detail page, I
want an annualized figure, and when a metric is taken from the annual report
I want that disclosed.

**Acceptance**: VNM quarter package carries `ROE = 26.37` (trailing) and no
`ROCE`/`NIM`/`PS`/`DIVIDEND_YIELD` quarter rows; summary `DIVIDEND_YIELD =
7.92` with `ANNUAL_BASIS`; no `ROE_TTM = 0` fact from an annual package.

### User Story 3 - Financial companies have revenue and profit (P2)

**Acceptance**: BVH (insurance) package has `NET_PROFIT` from
`profit_after_tax` and `REVENUE` from `total_net_revenue_from_insurance_business`;
SSI (securities) has `REVENUE`, `OPERATING_PROFIT`, and an annual
`FREE_CASH_FLOW` from `net_cash_flows_from_securities_trading_activities`.

### Edge and Failure Cases

- Profile whose overview shares disagree with charter capital / par by > 1 %:
  count kept, `SHARES_OUTSTANDING_UNVERIFIED`.
- A frame with both `net_profit` and `profit_after_tax`: first mapped row per
  (code, period) wins; never two facts for one code.
- Annual ratio `roe_trailling = 0.0`: not emitted (no `MISSING` row either —
  the annual report simply lacks a trailing figure).

## Requirements

- **FR-001** Bars exporter MUST request `end + 3 days` and drop rows after
  `end`.
- **FR-002** Profile exporter MUST NOT emit a free-float value; MUST verify
  outstanding shares against charter capital and par value.
- **FR-003** Ratio mapping MUST follow `provider-ratio-facts-v2` period rules;
  reinterpretations carry a rule id in `derivation`.
- **FR-004** Statement mapping MUST include the insurance/securities ids in
  research R-006 and dedupe by (metric, period).
- **FR-005** FCF derivation MUST be `kbs-fcf-ocf-plus-capex-v2` (id set per
  R-006; banks excluded).
- **FR-006** `fundamental-summary-v2` MUST read annual-only codes with annual
  fallback labelled `ANNUAL_BASIS`.
- **FR-007** Exporter tests MUST pin the observed provider item-id set per
  company type so a rename (like `earning_per_share_vnd`) fails a test
  instead of silently dropping data.
- **DATA-001** Fundamentals tool version bumps (0.6.0) so every package is
  re-exported under the new rules; bars tool version unchanged.
- **NFR-001** No provider call is added to the routine refresh beyond what
  exists today (padding changes a parameter, not call count).

## Success Criteria

- **SC-001** After the next refresh, `max(trading_date)` in `stock_daily_bar`
  equals the last completed session for ≥ 99 % of listed instruments.
- **SC-002** No current `fundamental_report_metric` row has `ROE_TTM = 0`
  from an annual report; `DIVIDEND_YIELD` values equal the provider's annual
  figures.
- **SC-003** Insurance/securities instruments have `NET_PROFIT` and
  `REVENUE` `DEFINED` in their newest summary.

## Requirement Traceability

| Requirement | Story | Verification |
|---|---|---|
| FR-001 | US1 | `test_export_daily_bars` end-padding case |
| FR-002 | edge | `test_export_equity_profile` |
| FR-003, FR-006 | US2 | exporter ratio tests; `FundamentalSummaryTests` |
| FR-004, FR-005 | US3 | exporter statement/FCF tests |
| FR-007 | all | `test_provider_schema_fixture` |
