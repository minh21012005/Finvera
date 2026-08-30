# Data Model: Provider Data Expansion

**Feature**: `008-provider-data-expansion` · **Created**: 2026-08-30

No migration. Every new fact lands in an existing table under an existing
catalog code, or lives only in the live session cache.

## Persisted facts

### `fundamental_report_metric` (existing, V003)

| Column | New use |
|---|---|
| `metric_code` | `EBITDA` (per period, derived) and `FREE_CASH_FLOW` (annual, derived) — both already catalogued in V003 |
| `value` | base VND, `numeric(28,6)`; rounded once at the exporter to 6 dp |
| `applicability` | `DEFINED` only — a derivation with a missing input is never emitted (no `MISSING` row is fabricated by the exporter; the summary reports `NOT_REPORTED`) |
| `quality_reason` | **derivation rule id** on `DEFINED` rows: `kbs-ebitda-margin-x-net-revenue-v1` or `kbs-fcf-ocf-plus-capex-v1` (≤ 64 chars). Previously always `null` on `DEFINED` rows. |

Reproducibility (DATA-002): the rule id names the formula; the inputs are the
same report's `REVENUE` (net) row plus the provider ratio point recorded in the
package's checksummed `canonicalRecord`, or the two cash-flow items; the
exporter package and its `packageSha256` are the immutable input set.

### `fundamental_report` (existing)

Annual and quarterly reports coexist as distinct rows (`period_type` differs).
`FREE_CASH_FLOW` appears only on `ANNUAL` rows from the accepted provider.

### `fundamental_summary_metric` (existing)

`EBITDA_TTM` follows the existing four-quarter sum. `FREE_CASH_FLOW` is
selected as: newest period's `DEFINED` value, else the latest `ANNUAL`
report's `DEFINED` value, else `MISSING`/`NOT_REPORTED`. The summary's
`basis_period_*` are unchanged by the fallback.

## Package contract (`vnstock-fundamentals-v1`, additive)

Each record MAY carry `"derivation": "<rule-id>"`. It is inside the record's
`canonicalRecord` (checksummed) and is absent for directly mapped items.
Unknown to older importers: they ignore it (they only read named fields).

## Session context (not persisted)

`TcbsLiveEquityQuoteService` session cache, keyed by symbol and reset per
venue trading date (Q-24):

| Field | Source | Unit |
|---|---|---|
| `ceilingPrice`, `floorPrice` | `s|4` reference frame; `tickerCommons` snapshot | base VND |
| `foreignRoom` | `tickerCommons.room` | shares |

Exposed through `LiveQuote` → `QuoteObservation` → `StockOverviewResponse.price`
as `ceilingPrice`, `floorPrice`, `foreignRoom` (nullable decimals/integer) and
`limitState` (`AT_CEILING` / `AT_FLOOR` / `null`). Absent overlay →
all `null` + reason `PRICE_LIMITS_UNAVAILABLE`.
