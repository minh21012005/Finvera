# Data Model: Deterministic Position Sizing

**Feature**: `030-deterministic-position-sizing`  
**Persistence**: None; immutable request, snapshot, and result values only

## Value conventions

- Decimal transport uses plain strings; scientific notation is rejected.
- VND/prices use precision 20, scale ≤6. Rates are decimal fractions (`0.01`
  means 1%), scale ≤8, range `[0,1]` unless stricter below.
- Quantities are non-negative whole shares; timestamps are UTC instants and
  trading dates are venue-local.
- `OWNER_ENTERED`, `PORTFOLIO`, `SIGNAL`, and `MARKET_RULE` identify authority.

## `SizingRequest`

| Field | Rule |
|---|---|
| `mode` | `MANUAL` or `PORTFOLIO` |
| `symbol` | Required supported cash-equity symbol |
| `portfolioId` | Required only for `PORTFOLIO`; forbidden for `MANUAL` |
| `manualCapital` | Required only for `MANUAL`; exposure fields become required when their cap is supplied |
| `riskBudget` | Exactly one positive `FIXED_VND` amount or `PERCENT` rate in `(0,1]` |
| `priceInput` | Exactly one manual pair or confirmed signal selector |
| `costPolicy` | Exactly one explicit exclusion or complete declared cost set |
| `exposureLimits` | Optional symbol concentration and deployment rates in `(0,1]` |

## `ManualCapitalInput`

| Field | Rule |
|---|---|
| `capitalBaseVnd` | Positive percentage-risk base |
| `availableCashVnd` | Non-negative affordability cap |
| `portfolioValueVnd` | Required if any exposure cap is supplied |
| `existingSymbolMarketValueVnd` | Required with symbol cap; non-negative |
| `currentDeployedMarketValueVnd` | Required with deployment cap; non-negative |

## `PriceInput`

`MANUAL` contains positive entry and stop with stop below entry. When it
overrides a previously imported signal, it may also contain the originating
strategy code, rule version, calculated-at, and trading date as non-authority
context. Those context fields never feed the formula. `SIGNAL` contains
strategy code, rule version, calculated-at, entry basis (`ENTRY_LOW`,
`MIDPOINT`, `ENTRY_HIGH`), and `confirmed=true`. The server-resolved signal is
authoritative. Editing an imported value creates manual input while preserving
the originating selector as `SIGNAL_CONTEXT` evidence until the owner removes it.

## `CostPolicy`

`EXCLUDED` contains only `excludeCosts=true`. `DECLARED` contains all five
non-negative rates: entry fee, exit fee, sell tax, entry slippage, exit
slippage. Each is `<1`; exit fee plus sell tax is `<1`. A declared set in which
all five rates are zero is invalid because it would bypass the explicit
cost-exclusion disclosure. Mixed or partial shapes are invalid.

## `PortfolioSizingSnapshot`

Produced by portfolio after owner enforcement: portfolio ID, available cash,
total value, current deployed market value, target-symbol quantity/value,
data status/reasons, coherence key, and as-of. Required values must be coherent;
the portfolio ledger remains the source of truth. Owner ID is not returned.

## `SignalSizingSnapshot`

Produced by stock: symbol, strategy/rule identity, direction, entry low/high,
stop, trading date, calculated-at, data status, and coherence key. Only a
current long signal resolves a signal request. Midpoint is `(low + high) / 2`.

## `MarketLotRule`

Venue, instrument type/status, lot size, rule/source version, effective date,
and reviewed-at. `market-lot-v1` supports active HOSE/HNX/UPCoM cash equities
with standard lot 100; unknown/inapplicable rules withhold.

## `SizingResult`

| Field group | Meaning |
|---|---|
| Status | `CALCULATED` or `WITHHELD`; positive quantity only when calculated |
| Quantity audit | Raw permitted quantity, lot, final quantity, rounding remainder |
| Risk/cost | Risk budget, acquisition unit cost, stop net proceeds, loss/share |
| Final effects | Required capital, estimated stop loss, remaining cash, and projected gross exposures on the disclosed pre-trade value basis |
| Constraints | One result per quantity cap with `APPLIED`, `NOT_APPLIED`, or `WITHHELD`, candidate, and binding flag; lot size/rounding are reported separately |
| Evidence | Field source/unit/as-of/coherence metadata; `SIGNAL_CONTEXT` is explicitly non-authoritative |
| Disclosure | Bounded reason codes and warnings |
| Version/time | Sizing rule, market rule, calculated-at |

## Lifecycle and storage

```text
request -> validate/resolve coherent input -> calculate or withhold -> return
```

Nothing persists. There is no migration, backfill, retention, or deletion.
Metrics contain bounded mode, outcome, reason, version, and latency only.
