# Research: Reason-code presentation

Method: read-only inventory of the monorepo on 2026-08-31 (OpenAPI contracts,
Java emitters, Python emitters, FE render sites, FE label maps). No provider
calls.

## R-001 — `ReasonCode` is an open string in every contract

`market-overview.openapi.yaml:410`, `stock-detail.openapi.yaml:296`,
`stock-screener.openapi.yaml:118`, `portfolio-watchlist.openapi.yaml:569`,
`internal-api.openapi.yaml:583` all declare `pattern ^[A-Z][A-Z0-9_]*$` with
*examples*, not enums. Consequence: the authoritative list of codes is the set
of Java/Python literals, and the FE cannot rely on a generated enum. Decision:
the dictionary is a checked-in list maintained with the emitters; a vitest
asserts completeness against that list (SC-001), and the fallback rule (raw
code, never hidden) covers drift (SC-002).

## R-002 — UI-reachable codes (deduplicated)

Ingestion-only and error-envelope codes excluded (never displayed as text).

| Domain | Codes |
|---|---|
| Data status | `CURRENT DELAYED STALE PARTIAL UNAVAILABLE` |
| Market — index | `MISSING_INDEX MISSING_REFERENCE_LEVEL MISSING_INDEX_LEVEL NO_ACCEPTED_INDEX_DATA MULTIPLE_ACCEPTED_SOURCES VNSTOCK_PRIVATE_PACKAGE` |
| Market — breadth | `UNRESOLVED_IDENTITY MISSING_PRICE MISSING_REFERENCE_PRICE MISSING_PRIOR_CLOSE BREADTH_NOT_AVAILABLE NO_ACTIVE_COMMON_EQUITY_UNIVERSE NO_DAILY_BAR_HISTORY NO_DAILY_BAR_HISTORY_FOR_LATEST_SESSION PROVIDER_AGGREGATE_BREADTH` |
| Market — regime | `MANDATORY_INPUT_UNAVAILABLE REQUIRED_INPUT_NOT_TIMELY_AVAILABLE INSUFFICIENT_COMPONENT_COMPLETENESS TREND_COMPONENT_UNAVAILABLE AGGREGATE_BREADTH_COMPONENT_UNAVAILABLE SOURCE_CONFLICT REGIME_NOT_AVAILABLE REGIME_UNAVAILABLE ASSESSMENT_QUALITY_NOT_FORECAST_PROBABILITY QUANTITATIVE_DECISION_SUPPORT_NOT_INVESTMENT_ADVICE QUANTITATIVE_DECISION_SUPPORT` |
| Market — live / provider | `TCBS_STREAM_RECEIVE_TIME TCBS_STREAM_ORDERING_UNAVAILABLE LIVE_OVERLAY_DISABLED PROVIDER_AUTH_REQUIRED PROVIDER_CONNECTIVITY_FAILED TCBS_THESIS_LIVE_QUOTE` |
| Stock overview / price | `PRICE_UNAVAILABLE PRICE_STALE PRICE_DELAYED REFERENCE_PRICE_UNAVAILABLE REFERENCE_PRICE_INVALID PROFILE_UNAVAILABLE PRICE_LIMITS_UNAVAILABLE AT_CEILING AT_FLOOR ADJUSTMENT_BASIS_UNAVAILABLE` |
| Technical | `INSUFFICIENT_HISTORY NOT_APPLICABLE MISSING VOLUME_UNAVAILABLE NO_BARS_AVAILABLE` |
| Fundamentals | `FUNDAMENTALS_UNAVAILABLE FUNDAMENTALS_DELAYED FUNDAMENTALS_STALE ANNUAL_BASIS PROVIDER_TRAILING_EPS NO_DATA NOT_REPORTED NEGATIVE_OR_ZERO_PRIOR_EPS NEGATIVE_OR_ZERO_PRIOR_REVENUE PROVIDER_REPORTED kbs-trailing-ratio-as-annualized-v1 kbs-ebitda-margin-x-net-revenue-v1 kbs-fcf-ocf-plus-capex-v1 kbs-fcf-ocf-plus-capex-v2` |
| Valuation | `HISTORY_BASIS_INSUFFICIENT SECTOR_BASIS_INSUFFICIENT NO_COMPARISON_BASIS CORE_METRIC_UNAVAILABLE INSUFFICIENT_METRIC_COVERAGE REDUCED_METRIC_SET HISTORY_SHARES_OUTSTANDING_HELD_CURRENT MISSING_EPS NEGATIVE_OR_ZERO_EPS MISSING_BVPS NEGATIVE_OR_ZERO_BVPS MISSING_EBITDA NEGATIVE_OR_ZERO_EBITDA MISSING_EV_INPUTS PE_NOT_DEFINED MISSING_GROWTH NEGATIVE_OR_ZERO_GROWTH MISSING_DIVIDEND ZERO_PRICE SHARES_OUTSTANDING_UNAVAILABLE SHARES_OUTSTANDING_UNVERIFIED OWN_HISTORY SECTOR` |
| Screener | `SECTOR_UNCLASSIFIED SHARES_OUTSTANDING_MISSING VALUATION_WITHHELD NO_CANDIDATES` |
| Signal / risk | `SIGNAL NO_SIGNAL WITHHELD INSUFFICIENT_RISK_FACTORS INPUT_UNAVAILABLE TRAILING_AVERAGE_ATR_ZERO HIGHEST_CLOSE_INVALID` |
| Portfolio / watchlist | `POSITION_PRICE_UNAVAILABLE POSITION_PRICE_DELAYED POSITION_PRICE_STALE NO_POSITIONS NO_SIGNALS_FOR_POSITIONS BENCHMARK_UNAVAILABLE PARTIAL_DATA_GAP NET_CONTRIBUTED_CAPITAL_METHOD MISSING_SYMBOL` |
| Analyst tool bridge | `UNKNOWN_SYMBOL NO_FUNDAMENTAL_REPORT NO_VALUATION` |

Source lines for every code are in the emitters (grep the code); the list is
mirrored verbatim in `finvera-fe/src/shared/format/reason-codes.test.ts`.

## R-003 — Render sites that show a code raw (before this feature)

`market-overview/components/{index,breadth,regime}-overview.tsx` (unavailable
notices, quality notes, disclaimer code), `stock-detail/components/
{stock-overview,stock-technical,stock-fundamentals,stock-valuation,
stock-signals,stock-chart}.tsx` (meta notes, per-cell `(CODE)` suffixes,
withheld reasons), `stock-screener/components/screener-results.tsx`
(`— CODE`), `portfolio/components/{holdings-table,portfolio-list}.tsx`
(raw `dataStatus` token; reasons other than `POSITION_PRICE_UNAVAILABLE`
hidden), `stock-detail/format/explain-evidence.ts` (unmapped engine notes
silently dropped; `NOT_APPLICABLE` reason raw). `finvera-ai/app/features/chat/
service.py` interpolates the valuation tool's `reasonCodes` raw into the
offline sentence.

Existing maps: `VALUATION_NOTE_COPY` (4 keys), a one-key ternary in
`stock-valuation.tsx`, a one-key branch in `holdings-table.tsx` /
`portfolio-list.tsx`; `applicabilityReasonLabel` in `stock-format.ts` is a
pass-through imported by nothing; `statusLabel` duplicated in four
market-overview files; `dataStatusLabel`, `strategyLabel`,
`evaluationStatusLabel`, `indicatorLabel`, `componentLabel`, `riskFactorLabel`,
`categoryLabel` index without fallback.

## R-004 — Presentation rule (decision)

- **Wording, code retained.** Render the wording as text; keep the code in
  `data-reason-code` and `title`. Rationale: Constitution II wants provenance
  inspectable; a screen full of identifiers is not "disclosure" for a human
  reader, but removing the identifier entirely would make support/verification
  harder. Tests address codes through the attribute.
- **Never hide.** Unknown code → the code itself. Empty list where a reason is
  expected → "Dữ liệu chưa hoàn thiện" (explicit, not blank).
- **One sentence per code**, joined with "; ". Codes that name a *basis* rather
  than a *gap* (`ANNUAL_BASIS`, `PROVIDER_TRAILING_EPS`, `kbs-*`,
  `NET_CONTRIBUTED_CAPITAL_METHOD`) are worded as statements of basis, not as
  problems.
- **Derivation rule ids keep the id in the wording** ("… (quy tắc
  kbs-fcf-ocf-plus-capex-v2)") because the id is the pointer to the contract.
- Location: `src/shared/format/` — the first cross-feature module; features
  import it, it imports nothing from features.

## R-005 — What is deliberately *not* changed

Backend codes and semantics; error-envelope switch statements; the AI
synthesis path (tool JSON → model) — the model already receives structured
`reasonCodes` and the explain evidence, and P2-03 measures its faithfulness.
