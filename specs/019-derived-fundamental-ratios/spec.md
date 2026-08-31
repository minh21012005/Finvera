# Feature 019: Derived Fundamental Ratios from VCI Statements

**Status**: Spec + research + contract 2026-08-31; implementation in `export_fundamentals_vci.py` (tool version bump)  
**Related**: Feature 018 (ADR-0011, VCI statements), Feature 009 (contract provider-ratio-facts-v1, catalog V015), docs/REMEDIATION_PLAN.md Q-57 follow-up

## Problem

The KBS yearly *ratio* frame (the one KBS endpoint that was correct) supplied 18 ratio metrics
that the product renders and screens on: liquidity (current/quick/cash), leverage
(debt-to-assets, liabilities-to-equity, equity-to-assets, interest coverage), efficiency
(asset/inventory/receivables turnover, ROCE), balance growth (total assets, equity), and bank
ratios (NIM, CIR, LDR). After the switch to VCI statements (Feature 018) those codes are no longer
ingested — the summary shows them as missing for every newly-crawled instrument, a visible
coverage regression. The VCI ratio frame is malformed in vnstock 4.0.7 (ADR-0011), but every one
of these ratios is plain arithmetic over statement lines VCI serves correctly — and deriving them
in Finvera with explicit rule ids is *more* trustworthy than ingesting a provider's opaque number.

## Functional requirements

- **FR-001** The VCI exporter MUST derive the ratio codes listed in contract
  `vci-derived-ratios-v1`, per company type, from statement lines only — never from the broken
  VCI ratio frame, never from another provider.
- **FR-002** Every derived value MUST carry a `derivation` rule id; averages that fall back to a
  period-end balance MUST carry the `-end` suffix (same convention as ROE/ROA).
- **FR-003** Quarterly values of flow-based ratios (turnovers, interest coverage, ROCE, NIM, CIR)
  MUST be TTM (4 consecutive quarters of flows over average balances); a broken quarter window
  means the metric is absent for that period — missing, never zero, never a single-quarter flow
  annualised.
- **FR-004** Units MUST match the catalog (V015): percent-point codes ×100
  (ROCE, DEBT_TO_ASSETS, LIABILITIES_TO_EQUITY, EQUITY_TO_ASSETS, growths, NIM,
  COST_INCOME_RATIO, LOAN_TO_DEPOSIT); plain ratios unscaled (CURRENT/QUICK/CASH_RATIO,
  INTEREST_COVERAGE, turnovers).
- **FR-005** A ratio whose inputs do not exist for the company type (liquidity for banks, NIM for
  non-banks, inventory turnover without an inventory line) is NOT emitted — the summary reports it
  missing; no proxy inputs.
- **FR-006** No backend change: all codes are already in `ALLOWED_METRIC_CODES`,
  `PROVIDER_RATIO_CODES` (unscaled) and the metric catalog; the summary calculator already
  aggregates them (annual-scoped set unchanged).
- **FR-007** Anchored tests: every derivation exercised on the VNM/MBB/BVH/SSI fixtures with
  hand-computed expected values from the fixture numbers.

## Out of scope (recorded, not silently dropped)

- **DIVIDEND_YIELD, PS, BETA** need a price and do not belong in a statements exporter. The KBS
  path stored them as report metrics; they stay absent until a price-aware derivation is designed
  (valuation layer — follow-up P2-09). `DIVIDENDS_PAID` exists in the VCI cash flow if that work
  wants a statements-side input.
- Bank/insurer INTEREST_COVERAGE (interest is operating, not financing, for them).
- Screener/FE changes: codes, units and grouping already exist.

## Success criteria

- SC-1 Exporter tests: each ratio equals the hand-computed value from fixture statement lines.
- SC-2 After the owner's next full crawl, summary coverage for these codes on VNM/MBB/SSI is
  restored (spot-checked via API), with `-end`/ANNUAL_BASIS disclosure where applicable.
- SC-3 Magnitude sanity on anchors: MBB NIM in 3–6 %, CIR in 25–45 %, LDR in 60–120 %; VNM
  current ratio in 1–3, inventory turnover in 3–8 (documented in research; guards against
  swapped numerator/denominator, not used as ingestion filters).
