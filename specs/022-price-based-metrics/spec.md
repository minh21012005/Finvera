# Feature 022: Price-Based Metrics Restored (DIVIDEND_YIELD, PS) + History Depth

**Status**: Implemented 2026-08-31; activates with the owner's from-scratch crawl  
**Closes**: docs/REMEDIATION_PLAN.md P2-09  
**Contracts**: `vci-derived-ratios-v1` (DIVIDEND_PER_SHARE row), `valuation-v2` addendum 2026-08-31 (PS)

## Problem and scope decisions (owner, 2026-08-31)

The KBS ratio frame used to supply DIVIDEND_YIELD, PS and BETA; all three need a price and went
dark with the VCI switch. Owner philosophy: "mọi thông tin, data, logic, tính toán cần phải đầy đủ
và chính xác, nhưng cũng không nên dư thừa" — so:

- **DIVIDEND_YIELD: restore.** Income is decision-critical. The whole downstream chain already
  existed (summary `DIVIDEND_PER_SHARE_TTM` → valuation yield = DPS_TTM / price); only the
  `DIVIDEND_PER_SHARE` report metric was missing. The exporter (1.2.0) now derives it:
  `|dividends_paid| / shares` per period, rule `vci-dps-cash-dividends-over-shares-v1`
  (caveat documented: the consolidated cash-flow line includes minority dividends).
- **PS: add, as an informational valuation metric.** `PS = marketCap / REVENUE_TTM`, weight 0,
  outside the composite/coverage/core gates — it exists to price loss-makers where PE is
  NOT_APPLICABLE, not to move the score. Migration V017 admits 'PS' into `valuation_metric`.
- **BETA: deferred deliberately.** No anchored definition (window? index?) adds decision value for
  a personal investor; adding an ambiguous number violates "không dư thừa".
- **History depth: 2023 → 2019-01-01.** The own-history valuation percentile needs a full market
  cycle (2020 crash, 2021 bubble, 2022 bear) to mean anything; VCI serves ~8 rolling years in the
  same call, so the deeper window costs zero extra requests. 2019 (not the rolling 2018 window
  edge) keeps ~4 months of headroom before the rolling window (today 2018-09-01) passes
  2019-01-01; after that a re-crawl simply starts at the window edge and the already-imported
  older rows stay (nothing is ever deleted), so no history is lost.
- **Provider tier: stays free** (owner decision); pacing already sized for it.

## Functional requirements

- FR-001 Exporter emits `DIVIDEND_PER_SHARE` for every company type and period where the
  `dividends_paid` cash-flow line resolves and shares are known; a present 0 is a real "no
  dividend"; an absent line stays absent.
- FR-002 ValuationV1 computes PS with reasons `MISSING_REVENUE` / `NEGATIVE_OR_ZERO_REVENUE`
  (NOT_APPLICABLE) / `MISSING_MARKET_CAP_INPUTS`; PS never enters `allScored()`, BASE_WEIGHTS,
  the coverage denominator or the PE/PB core gate.
- FR-003 `REVENUE_TTM` joins the valuation input codes on both the subject and the persisted-peers
  paths (Q-55 bulk path included).
- FR-004 FE renders PS with its own label and the three new reason codes; dictionary inventory
  stays complete.
- FR-005 `verify_calcs.py` recomputes PS and DIVIDEND_YIELD alongside PE/PB/PEG post-crawl.

## Success criteria

- SC-1 Exporter tests: DPS equals |dividends_paid|/shares from fixture lines for all four company
  types (76/76 green).
- SC-2 ValuationV1Tests: loss-maker gets PE NOT_APPLICABLE **and** a DEFINED PS; PS absent from
  the scored set (27/27 green).
- SC-3 ValuationService integration tests green with 'PS' persisted (V017).
- SC-4 Post-crawl: VNM valuation shows DIVIDEND_YIELD ≈ DPS_TTM/price (~6 % at 2026 prices) and a
  DEFINED PS; verifier recomputation matches.
