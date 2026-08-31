# ADR-0013: Standardize All Vnstock Market Data on VCI

**Status**: Accepted — owner directive 2026-08-31 ("vci là provider mặc định, chuẩn nhất … chuẩn hóa toàn bộ … data chính xác, đồng bộ hóa"); implementation staged as Feature 021  
**Date**: 2026-08-31  
**Decision owners**: Finvera maintainer  
**Related**: ADR-0011 (VCI fundamentals), ADR-0012 (VCI ICB sectors), ADR-0003 (TCBS live overlay — unchanged), specs/021-vci-single-provider/research.md, docs/REMEDIATION_PLAN.md

## Context

After Features 018/020, fundamentals and sector data come from VCI while daily bars, index
history, listing and equity profiles still come from KBS — two providers, two adjustment-rounding
conventions (MSB closes differ by ≤ 0.22 % on pre-ex-date history), two shares-outstanding readings,
and twice the provider surface to audit. VCI is vnstock's default and best-maintained source; the
2026-08-31 probes verified VCI equivalents for every remaining dataset: identical last-session
closes to the tick across 24 symbols, index levels equal to 0.01 (plus UPCOMINDEX, which KBS also
serves but VCI serves alongside its constituents), ~8-year price depth, listing with
type/exchange, and a company overview carrying `issue_share`, free float, foreign room and state
ownership. Nothing the product consumes today exists only at KBS (research R-003).

## Decision

VCI becomes the single vnstock provider for **daily bars, index history, instrument reference and
equity profiles**, joining fundamentals (ADR-0011) and sectors (ADR-0012). The TCBS intraday
overlay (ADR-0003) is out of scope. Execution is dataset-by-dataset (Feature 021) with the
established method: a contract per dataset with audited anchors, exporter tests on captured
fixtures, ingestion via the existing revision chains (superseded, never deleted), verifier gates,
and one full re-crawl per dataset — never mixing KBS history with VCI increments inside one series
(research R-004). The KBS exporters remain in the tree as documented fallbacks but are no longer
crawled.

## Consequences

- One provider, one adjustment convention, aligned with the statements the ratios are derived from;
  cross-provider seams (MSB-type tick differences) disappear from the product's own data.
- First VCI bar crawl supersedes the ≤ 0.25 % adjustment-rounding rows (~doubles the bar table via
  revision chains) and triggers one full indicator/valuation warmup.
- Bars get the honest adjustment label (`PROVIDER_ADJUSTED` instead of RAW) in the new contract.
- All request volume concentrates on one host: Q-58's network-retry classes and the existing pacing
  budget apply; a KBS fallback is a one-line source switch plus a full re-crawl.
- `verify_calcs.py` keeps the two-provider close/index cross-check, with KBS as the *independent
  reference* instead of the source of record.

## Validation

Per dataset: anchored fixture tests; post-crawl `verify_calcs.py` green (technical, valuation,
breadth recomputation on VCI bars; cross-check vs KBS; sector + anchors sections); AI e2e spot run.
