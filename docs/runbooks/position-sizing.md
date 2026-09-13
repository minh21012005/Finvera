# Deterministic Position Sizing Runbook

**Feature**: `specs/030-deterministic-position-sizing/`  
**Rule versions**: `position-sizing-v1`, `market-lot-v1`  
**Last rule review**: 2026-09-13

## Operating boundary

`POST /api/v1/position-sizing/calculate` is an authenticated, CSRF-protected,
stateless decision-support calculation. It creates no order and persists no
scenario. Manual mode remains usable if portfolio or signal data is unavailable.
When the user explicitly selects trusted portfolio or signal input and that
input cannot be resolved, the endpoint withholds a positive quantity.

The engine uses decimal precision 34 with `HALF_EVEN`, exact whole-share floor,
then floors once more to a standard lot. Every applied cap is evaluated before
lot rounding. Tied minimum caps are all reported as binding.

## Market-lot rule review

`market-lot-v1` accepts active cash equities on HOSE, HNX, and UPCoM with a
100-share standard lot. Odd-lot execution is outside v1. Its release evidence is:

- HOSE February 2026 trading guide: standard lot 100 securities and odd lot
  1–99 securities.
- Official VNX legal index: Decision 22/QĐ-HĐTV for listed securities,
  issued/effective 2026-03-16; Decision 23/QĐ-HĐTV for UPCoM,
  issued/effective 2026-03-18.

Each calculated response includes venue, lot size, applicable decision,
effective date, review/acceptance time, resolved symbol/status, and
`market-lot-v1`. Recheck the official exchange
publications before a release after any trading-rule announcement. A changed
lot rule requires a new version; do not alter historical v1 semantics.

## Failure interpretation

| Result | Operator meaning |
|---|---|
| HTTP 422 | Request shape, precision, cost policy, risk budget, confirmation, or entry/stop relationship is invalid. |
| `MARKET_LOT_RULE_UNAVAILABLE` | Symbol is absent, inactive, not an equity, or its venue has no approved v1 lot rule. |
| `PORTFOLIO_DATA_UNAVAILABLE` | The coherent portfolio snapshot cannot support the calculation. No browser value substitutes for it. |
| `SIGNAL_NOT_CURRENT` | The exact strategy/rule/time selector no longer resolves to a current LONG signal. |
| `BELOW_STANDARD_LOT` | Applied caps permit fewer than 100 shares; no positive standard-lot quantity is returned. |
| `COSTS_EXCLUDED` | The user explicitly chose zero for all five cost/slippage categories. Inspect the five category warnings. |

Unexpected failures are isolated to this stateless endpoint. Do not retry a
malformed request. A user may switch from unavailable portfolio/signal input to
manual input by explicitly entering assumptions.

## Observability and privacy

Monitor `finvera.position_sizing.calculation` and
`finvera.position_sizing.duration`. Allowed tags are bounded `mode`, `outcome`,
`reason`, and `version`. Logs and metrics must not contain symbol, portfolio ID,
capital, prices, rates, quantity, request body, or response body. Request and
response string representations redact financial and identifying values.

Performance targets after warmup are p95 at most one second for manual mode and
two seconds for portfolio mode. The release check exercises authentication,
CSRF, JSON decoding, controller, orchestration, and the owner-scoped portfolio
application adapter; production database/network latency must still be watched
through the same duration metric. Alert on sustained `FAILED`, unusual withholding
rate, or target breach; investigate upstream market/portfolio/signal status
before changing the formula.

## Rollout and rollback

Deploy the additive Spring endpoint before enabling frontend navigation. No
database migration or backfill exists. Rollback removes/hides the frontend route
and endpoint; portfolio, market, and signal records require no reversal.

The feature release remains pending until the owner completes the ten-scenario
comprehension review in
`specs/030-deterministic-position-sizing/validation/owner-review.md`.
