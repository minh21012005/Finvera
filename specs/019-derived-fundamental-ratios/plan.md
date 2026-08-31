# Plan: Feature 019 — Derived Fundamental Ratios

## Approach

One layer changes: `export_fundamentals_vci.py`. A new `DERIVATION_INPUTS` table (per company
type: the balance/flow line ids the contract names) and a `derive_ratios()` step appended to
`build_metric_records`, reusing the existing helpers (`resolve`, `window`, `average`, `record`,
`END_SUFFIX`) and conventions (TTM windows, `-end` fallback, missing ≠ zero). Tool version bumps
(minor), so Q-58's version-scoped settlement re-exports symbols whose fundamentals settled under
the old version on the owner's next crawl — no flag, no manual retry.

Backend, FE, AI: no changes. All codes are in `ALLOWED_METRIC_CODES` + `PROVIDER_RATIO_CODES`
(unscaled) + catalog V015 with display names; `FundamentalSummaryCalculator` already reads them
(annual-scoped set unchanged); FE groups them in `stock-fundamentals.tsx`; `reason-codes.ts` gets
the new `vci-*` rule ids (with `-end` variants) so the dictionary inventory test stays complete.

## Steps

1. Extend the four per-type fixture files with the statement lines the contract needs (values
   verbatim from the 2026-08-31 full capture — the same capture research R-003/R-004 cites).
2. Implement `DERIVATION_INPUTS` + `derive_ratios()`; bump `TOOL_VERSION` 1.0.0 → 1.1.0.
3. Tests: per-type presence/absence matrix + exact hand-computed values (contract anchors),
   TTM window break → absent, `-end` fallback path.
4. FE `reason-codes.ts`: add the 12 new rule ids (+`-end` variants where averages exist); run the
   inventory test.
5. Docs: REMEDIATION_PLAN changelog + P2-09 row (price-based DIVIDEND_YIELD/PS/BETA follow-up).

## Risks

- NIM's earning-asset definition varies across published sources → the rule id names the
  definition; research R-004 pins the anchor magnitude (≈ 3.3 % for MBB FY2025).
- Some non-financials lack a line (e.g. no inventories) → the matrix tests assert absence, and
  absence is already rendered honestly by the FE.
- First crawl after the version bump re-exports settled-failure symbols only (Q-58 rule); already
  successful symbols re-export because the package `toolVersion` no longer matches — this is the
  intended full ratio backfill (~6 h, same as the Feature 018 pass, and it can ride the same
  nightly refresh).
