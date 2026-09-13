# Position Sizing Financial Contract v1

**Rule version**: `position-sizing-v1`  
**Market rule version**: `market-lot-v1`  
**Scope**: Cash-funded long positions in supported HOSE/HNX/UPCoM equities

## Inputs

Rates are decimal fractions and `floor` means mathematical whole-share floor:

```text
C  available cash; B risk capital base; E entry; S stop (0 < S < E)
rf entry fee; xf exit fee; tx sell tax; es entry slippage; xs exit slippage
L  standard lot size
R  fixedRiskVnd OR B × riskRate
```

Explicit cost exclusion sets all five rates to zero and emits
`COSTS_EXCLUDED`. Manual percentage risk uses declared capital base; portfolio
percentage risk uses coherent total value while portfolio cash supplies `C`.
A declared-cost request with all five rates equal to zero is rejected; the owner
must use explicit exclusion so every omitted category is disclosed.

## Cost-adjusted values

```text
effectiveEntry          = E × (1 + es)
effectiveStop           = S × (1 − xs)
acquisitionUnitCost     = effectiveEntry × (1 + rf)
stopNetProceedsPerShare = effectiveStop × (1 − xf − tx)
lossPerShare            = acquisitionUnitCost − stopNetProceedsPerShare
```

Combined exit deductions must be below one and `lossPerShare` positive.

## Constraint candidates

```text
riskQty = floor(R / lossPerShare)
cashQty = floor(C / acquisitionUnitCost)
```

Optional maximum symbol concentration `sc`, portfolio value `V`, and current
symbol market value `SMV`:

```text
symbolHeadroomVnd = max(0, V × sc − SMV)
symbolQty         = floor(symbolHeadroomVnd / effectiveEntry)
```

Optional maximum deployment `dc` and current deployed market value `DMV`:

```text
deploymentHeadroomVnd = max(0, V × dc − DMV)
deploymentQty         = floor(deploymentHeadroomVnd / effectiveEntry)
```

Omitted optional caps are `NOT_APPLIED`; zero headroom is an applied zero cap.

## Selection and lot floor

```text
rawPermittedQty   = min(all applied candidate quantities)
finalQty          = floor(rawPermittedQty / L) × L
roundingRemainder = rawPermittedQty − finalQty
```

Every candidate equal to the minimum is binding. If `finalQty < L`, return
`WITHHELD` with no positive quantity and a precise reason. Lot rounding never
changes which pre-lot constraints are binding.

## Result postconditions

For `Q = finalQty`:

```text
requiredCapital              = Q × acquisitionUnitCost
estimatedLossAtStop          = Q × lossPerShare
remainingCash                = C − requiredCapital
newPositionGrossMarketValue  = Q × effectiveEntry
projectedSymbolMarketValue = SMV + newPositionGrossMarketValue
projectedSymbolExposureRate = projectedSymbolMarketValue / V (when V known)
projectedDeploymentRate     = (DMV + newPositionGrossMarketValue) / V (when V known)
```

Before `CALCULATED`, verify capital, risk, and every applied exposure cap. An
invariant failure is internal failure, never a positive rounded result.

These are projected gross exposure ratios on the coherent **pre-trade**
portfolio value `V`; they are not an accounting claim about portfolio value
after fees, slippage, or later market-price movement. The same disclosed basis
is used both to cap and report the scenario.

## Precision

- Input money/price: precision 20, scale ≤6; rates scale ≤8.
- Intermediate and derived values: decimal precision 34 with `HALF_EVEN` for
  non-terminating operations; no transport/display rounding feeds a candidate.
- Candidate/final quantities: exact mathematical floor.
- Output strips insignificant zeroes and never uses scientific notation.
- Display formatting never feeds back into calculation.

## Signal selector

For a server-resolved current long signal:

```text
ENTRY_LOW -> entryLow
MIDPOINT  -> (entryLow + entryHigh) / 2
ENTRY_HIGH -> entryHigh
stop -> stopLoss
```

Symbol, strategy, rule version, and calculated-at must match and import must be
confirmed. Editing imported values switches the authoritative prices to manual
source. The originating selector may remain as `SIGNAL_CONTEXT` evidence but
never affects entry, stop, a constraint, or any financial output.

## Stable reasons

```text
INVALID_REQUEST, INVALID_INPUT, INVALID_RISK_BUDGET,
INCOMPLETE_COST_POLICY, INVALID_PRICE_RELATIONSHIP,
SIGNAL_NOT_CONFIRMED, MARKET_LOT_RULE_UNAVAILABLE,
PORTFOLIO_DATA_UNAVAILABLE, SIGNAL_NOT_CURRENT, BELOW_STANDARD_LOT,
COSTS_EXCLUDED, ENTRY_FEE_EXCLUDED, EXIT_FEE_EXCLUDED,
SELL_TAX_EXCLUDED, ENTRY_SLIPPAGE_EXCLUDED, EXIT_SLIPPAGE_EXCLUDED
```

Malformed/mutually inconsistent requests are rejected. Missing/stale trusted
facts return no positive quantity. The first six values are problem reasons,
the next four are withholding reasons, and the remaining six are warnings.
