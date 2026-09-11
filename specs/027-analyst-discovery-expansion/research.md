# Research: Analyst Archetype Calibration

**Feature**: `027-analyst-discovery-expansion`  
**Decision date**: 2026-09-10

## R-001 — Presets are versioned discovery heuristics

Natural-language archetypes are deterministic candidate screens, not claims that
the selected stocks are suitable investments. Each response discloses the exact
filters and that the preset is a heuristic. Explicit user constraints override
preset values.

Fixed P/E and P/B cut-offs are not used for the Value preset. Finvera's
`valuation-v3` classification already compares each multiple with the stock's
own history and available sector evidence. This follows the existing valuation
contract, which deliberately rejects universal absolute valuation cut-offs.

## R-002 — Canonical metric meanings

- ROE, revenue growth and EPS growth are percent points.
- `EPS_GROWTH_PERCENT` and `REVENUE_GROWTH_PERCENT` are current TTM versus prior
  TTM and require the existing eight-quarter data policy.
- `DEBT_TO_EQUITY` is canonical percent points. A value of `100` means total
  debt equals equity. KBS already supplies percent points; VCI's derived
  `debt / equity` ratio must be multiplied by 100 at export.
- Relative volume is a ratio; `1.2` means 120% of its declared reference volume.

Debt/Equity is not part of the generic Growth preset because leverage has very
different meanings for banks, securities firms and non-financial companies and
the current screener has no company-type exclusion filter. It remains available
when the user explicitly requests it.

## R-003 — Archetype v2 defaults

| Archetype | Deterministic filters | Purpose and limitation |
|---|---|---|
| `GROWTH` | Revenue growth >= 10%, EPS growth >= 10%, ROE >= 15% | Requires both top-line and earnings growth; does not assert multi-year persistence. |
| `LONG_TERM_QUALITY` | Revenue growth >= 5%, EPS growth >= 5%, ROE >= 15%, market cap >= 1,000B VND | Broad quality/liquidity candidate screen, separate from Value. |
| `VALUE` | valuation classification `UNDER_VALUED`, ROE >= 12%, market cap >= 1,000B VND | Uses the published relative valuation engine; withheld valuations are excluded. |
| `DIVIDEND` | Dividend yield >= 3%, defined positive P/E, market cap >= 2,000B VND | Current-yield candidate screen; payout durability is not yet available. |
| `MOMENTUM_SCREEN` | Price above MA20, RSI 50–68, relative volume >= 1.2 | Broad pre-filter, distinct from `MOMENTUM_SIGNAL`. |

The numeric profitability, growth, yield and liquidity bounds are conservative
v2 discovery defaults, not universal market truths. A later calibration feature
may replace them with sector-aware percentiles after a versioned Vietnam-market
evaluation dataset exists.

## R-004 — Momentum terminology

`MOMENTUM_SCREEN` is a broad candidate filter. `MOMENTUM_SIGNAL` is the existing
`StrategySignalV1` rule (`RSI14 >= 60` and positive MACD histogram). UI and AI
explanations must not describe a screen match as a triggered strategy signal.

## R-005 — External method references

- MSCI Quality uses ROE, Debt/Equity and earnings variability with normalized
  scores rather than claiming one absolute threshold fits every company.
- S&P style methodologies use multiple relative value/growth factors.
- Damodaran's January 2026 emerging-market regressions show valuation multiples
  vary with growth, profitability, payout and risk.

These references support relative multi-factor treatment. They do not validate
the exact Finvera v2 cut-offs; those remain disclosed product heuristics.
