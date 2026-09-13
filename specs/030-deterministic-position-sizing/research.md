# Research: Deterministic Position Sizing

**Feature**: `030-deterministic-position-sizing`  
**Date**: 2026-09-13

## R-001 — Capability placement

**Decision**: Add a layered `positioning` module to the Spring monolith; keep
arithmetic in pure versioned `PositionSizingV1` and orchestration in its service.

**Rationale**: The interactive calculator and future backtest must reuse one
contract without putting authoritative finance in UI, controller, or prompt.

**Alternatives considered**: Portfolio module (couples standalone/backtest to
portfolio); stock strategy module (blurs scenario choice and signal facts); new
service (unjustified).

**Risks/validation**: Architecture tests forbid access to other modules' entity
or repository packages.

## R-002 — Version 1 formula

**Decision**: Take the minimum of separately floored risk, affordability,
optional symbol-concentration, and optional deployment candidates, then floor to
the standard lot. Use `contracts/position-sizing-v1.md` for exact equations.

**Rationale**: Each cap remains inspectable and the result cannot round above a
limit. The pure function is reusable by backtesting.

**Alternatives considered**: Stop distance alone; caps after lot rounding;
stochastic simulation. Each loses a required constraint or reproducibility.

**Risks/validation**: Independent fixtures and generated boundaries prove all
postconditions.

## R-003 — Precision and rounding

**Decision**: Decimal strings at transport, 34-significant-digit `HALF_EVEN`
decimal arithmetic internally, and whole-share/lot mathematical floor.
VND/price accept scale ≤6; decimal-fraction rates accept scale ≤8. Formatting
strips insignificant zeroes only after every authoritative calculation.

**Rationale**: Matches repository finance conventions and avoids binary-float
and upward-rounding errors.

**Alternatives considered**: IEEE floating point, integer VND only, nearest-lot
rounding.

**Risks/validation**: Exact lot boundaries, repeating divisions, scale and
large-value cases.

## R-004 — Costs and slippage

**Decision**: Require all five explicit rates or `excludeCosts=true`; infer no
broker/regulatory default in v1.

**Rationale**: Fees/slippage vary and hidden zero understates risk. This matches
the owner's clarified choice.

**Alternatives considered**: Versioned presets, mandatory positive values, and
silent zero.

**Risks/validation**: Reject partial/mixed shapes, invalid rates, and total exit
deductions ≥100%. Reject an all-zero declared set so it cannot bypass the
explicit exclusion warnings.

## R-005 — Standard market lot

**Decision**: `market-lot-v1` uses 100 shares for active cash equities on HOSE,
HNX, and UPCoM and excludes odd lots. Unknown venue/type/status withholds. The
rule baseline is the current 2026 exchange regime and must be release-reviewed.

**Rationale**: HOSE's February 2026 guide states a 100-security standard lot and
separate 1–99 odd lots. VNX listed and UPCoM rulebooks specify 100-share matched
orders:

- [HOSE guide, February 2026](https://staticfile.hsx.vn/Uploads/UploadDocuments/2453338/HTGD_Quy%20dinh%20can%20biet%20khi%20GDCK%20tren%20HOSE_T2.2026.pdf)
- [Official VNX legal publication index](https://vnx.vn/vi/van-ban-phap-ly/6):
  Decision 22/QĐ-HĐTV is issued/effective 2026-03-16 and Decision
  23/QĐ-HĐTV is issued/effective 2026-03-18.

**Alternatives considered**: Odd lots (execution/liquidity out of scope),
browser-supplied lots (untrusted), and a new rule table (unnecessary for v1).

**Risks/validation**: Primary VNX publication metadata and the current HOSE
guide were reviewed on 2026-09-13. The response discloses the applicable VNX
decision, effective date, review date, venue, and lot size. A rule change adds
a new rule version rather than mutating historical v1 semantics.

## R-006 — Portfolio snapshot

**Decision**: Portfolio publishes `PortfolioSizingDataService`, enforcing owner
access and returning cash, total value, current deployed/target-symbol value,
status/reasons, coherence key, and as-of in one immutable snapshot.

**Rationale**: Existing ledger replay owns these facts; one narrow application
read prevents mixed timestamps and cross-repository access.

**Alternatives considered**: Multiple internal HTTP calls, direct repository
queries, or browser-provided portfolio values.

**Risks/validation**: Deleted/foreign, concurrent-ledger, stale/missing-price
integration cases.

## R-007 — Signal import

**Decision**: Stock publishes `StockSizingDataService` to resolve a current long
signal by symbol, strategy/rule, and calculation time. The owner chooses
`ENTRY_LOW`, `MIDPOINT`, or `ENTRY_HIGH` and confirms; the server derives entry
and stop. Editing switches the request to manual while retaining the originating
selector only as removable, non-authoritative context.

**Rationale**: Feature 004 exposes an entry zone. Explicit server-resolved basis
preserves choice, provenance, and reproducibility.

**Alternatives considered**: Trust copied prices, silently choose the strongest
signal, or require a signal.

**Risks/validation**: Reject absent, stale, non-long, changed, or unconfirmed
selectors; manual mode stays available.

## R-008 — Stateless REST and degradation

**Decision**: One synchronous authenticated POST returns `CALCULATED` or
`WITHHELD`; invalid shape is 422 and foreign/missing portfolio is the same 404.
Persist nothing and retry no operation.

**Rationale**: The pure fast calculation has no side effects. Async work and
persistence add no first-slice value.

**Alternatives considered**: Persist scenarios, async jobs/events, or partial
positive quantities with missing facts.

**Risks/validation**: Contract, security, latency, and isolation tests.

## R-009 — Verification evidence

**Decision**: Pure independent fixtures/property invariants first, followed by
contract, ownership, adapters, frontend, accessibility, E2E, and performance.

**Rationale**: Numerical and authorization defects carry the highest risk; UI
snapshots cannot prove the formula.

**Alternatives considered**: Controller/UI-only testing or production-only
comparison.

**Risks/validation**: Quickstart records commands, counts, timings, and blockers.
