# Data Model: Stock Detail and Analysis

**Feature**: `002-stock-detail-analysis`
**Owner**: `finvera-be` / `stock` module
**System of record**: PostgreSQL
**Migration**: forward-only Flyway, starting at `V003`

## Relationship to Feature 001

This feature **reuses without modification**:

| Existing table | Role here |
|---|---|
| `market_instrument` | The instrument identity every new table references. No new symbol registry is created. |
| `ingestion_record` | The accepted-observation envelope. Every new fact links to one accepted ingestion. |
| `equity_price_observation` | The current or latest intra-session price for the overview section. |
| `market_calendar_day`, `market_session_window` | Trading-day and session-state derivation. |
| `market_import_batch` | Provenance root for historical bars and fundamental reports arriving by offline package. |
| `source_reconciliation_audit` | Cross-source conflict evidence, extended to daily bars and fundamentals. |

No Feature 001 table is altered, and no Feature 001 row is migrated. Every change
below is additive, which keeps the Feature 001 acceptance evidence valid.

## Modeling rules

Inherited verbatim from Feature 001 and restated because they constrain every
table below:

- Transport and ingestion instants are UTC `timestamptz`; market dates and
  reporting periods are interpreted in `Asia/Ho_Chi_Minh`.
- Authoritative values are PostgreSQL `numeric` mapped to `BigDecimal`. Binary
  floating point never holds a financial value.
- Observations and derived results are immutable revisions. A correction creates
  a new row linked by `supersedes_id`; it never overwrites.
- Missing, zero, invalid, and not-applicable remain distinguishable. A nullable
  authoritative column always has a companion reason column.
- Derived results record enough input identity to be recomputed exactly.

## Enumerations

| Type | Values | Notes |
|---|---|---|
| `AdjustmentStatus` | `ADJUSTED`, `RAW`, `NOT_APPLICABLE`, `UNKNOWN` | Series-level; never mixed within one series (DATA-008). |
| `DataStatus` | `CURRENT`, `DELAYED`, `STALE`, `PARTIAL`, `UNAVAILABLE` | Inherited from Feature 001. `SOURCE_CONFLICT` is a reason, not a status. |
| `PeriodType` | `ANNUAL`, `QUARTER` | Reporting period grain. |
| `ReportKind` | `CONSOLIDATED`, `SEPARATE` | Vietnamese filings publish both; they are not interchangeable. |
| `AuditStatus` | `AUDITED`, `REVIEWED`, `UNAUDITED`, `UNKNOWN` | Quality signal on a reporting period. |
| `CorporateActionType` | `SPLIT`, `STOCK_DIVIDEND`, `CASH_DIVIDEND`, `RIGHTS_ISSUE`, `OTHER` | Drives the adjustment factor chain. |
| `MetricApplicability` | `DEFINED`, `NOT_APPLICABLE`, `MISSING` | The DATA-007 three-state rule made explicit. |
| `ValuationLabel` | `UNDER_VALUED`, `FAIR_VALUED`, `OVER_VALUED` | Present only on a publishable assessment. |
| `ComparisonBasis` | `OWN_HISTORY`, `SECTOR` | Disclosed on every published assessment. |

## Aggregate 1 — Equity reference

### `sector_reference`

Sector identity under a named, versioned classification scheme. A scheme version
is part of the key because reclassification is a real event and must not silently
rewrite the meaning of past assessments.

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `scheme` | varchar(32) | Classification scheme identifier. |
| `scheme_version` | varchar(32) | Immutable version of that scheme. |
| `sector_code` | varchar(32) | Code within the scheme. |
| `display_name_vi` | varchar(160) | Vietnamese display name. |
| `display_name_en` | varchar(160) nullable | Optional English name. |

Unique: `(scheme, scheme_version, sector_code)`.

### `equity_profile`

Effective-dated company reference data for one instrument. Company name, sector,
and share count all change over time, and a valuation computed last quarter must
still resolve the share count that was effective then.

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `instrument_id` | UUID | FK `market_instrument`. |
| `company_name_vi` | varchar(255) | Vietnamese legal or trading name. |
| `company_name_en` | varchar(255) nullable | Optional. |
| `sector_reference_id` | UUID nullable | FK `sector_reference`; null with a reason when unclassified. |
| `shares_outstanding` | bigint nullable | Non-negative; required for market cap and per-share metrics. |
| `free_float_ratio` | numeric(9,6) nullable | 0 to 1 inclusive. |
| `listing_status` | varchar(32) | `LISTED`, `SUSPENDED`, `HALTED`, `DELISTED`, `UNKNOWN`. |
| `effective_from` | date | Inclusive. |
| `effective_to` | date nullable | Inclusive; null means current. |
| `source` | varchar(64) | Provenance. |
| `source_revision` | varchar(128) | Provenance revision. |
| `quality_reason` | varchar(64) nullable | Required when `sector_reference_id` or `shares_outstanding` is null. |

Unique: `(instrument_id, effective_from)`. Check: `effective_to is null or
effective_to >= effective_from`. A partial unique index enforces at most one
current row per instrument.

## Aggregate 2 — Accepted price history

### `equity_daily_bar`

One accepted completed-session OHLCV revision per instrument and trading date.
Research R-002 records why this is a new table rather than a widening of
`equity_price_observation`.

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `instrument_id` | UUID | FK `market_instrument`. |
| `ingestion_record_id` | UUID | Unique FK to the accepted ingestion. |
| `import_batch_id` | UUID nullable | FK `market_import_batch` when it arrived by offline package. |
| `trading_date` | date | Vietnam market date. |
| `open_price`, `high_price`, `low_price`, `close_price` | numeric(20,6) | Non-negative. |
| `adjusted_close` | numeric(20,6) nullable | Null when no accepted adjustment basis exists. |
| `adjustment_factor` | numeric(20,12) nullable | Cumulative factor applied to reach `adjusted_close`. |
| `adjustment_status` | varchar(32) | `AdjustmentStatus`. |
| `volume` | bigint nullable | Non-negative matched volume. |
| `value_vnd` | numeric(24,4) nullable | Non-negative matched value. |
| `source` | varchar(64) | Accepted provider identity. |
| `observed_at`, `accepted_at` | timestamptz | UTC. |
| `revision` | integer | Starts at 1 per `(instrument, trading_date, source)`. |
| `supersedes_id` | UUID nullable | Correction chain; self-reference forbidden. |
| `quality_reason` | varchar(64) nullable | Required when any price or volume field is null. |

Checks: `high_price >= low_price`; `high_price >= open_price` and
`high_price >= close_price`; `low_price <= open_price` and
`low_price <= close_price`; `adjusted_close is null or adjustment_factor is not
null`.

Indexes: unique `(instrument_id, trading_date, source, revision)`; partial unique
on the current revision per `(instrument_id, trading_date, source)`; a covering
index on `(instrument_id, trading_date desc)` for the window reads that dominate
this feature's query load.

The OHLC ordering checks are cheap and catch the most common provider mapping
defect — swapped high and low — before it reaches an ATR calculation where it
would produce a plausible-looking wrong number.

### `corporate_action`

The accepted basis for price adjustment. Without these rows, `adjusted_close`
cannot be audited and research R-004 cannot be satisfied.

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `instrument_id` | UUID | FK `market_instrument`. |
| `ingestion_record_id` | UUID | Unique FK. |
| `action_type` | varchar(32) | `CorporateActionType`. |
| `ex_date` | date | The first date trading is adjusted. |
| `record_date`, `payment_date` | date nullable | When published. |
| `ratio_numerator`, `ratio_denominator` | numeric(20,6) nullable | For splits, stock dividends, rights issues. |
| `cash_amount_vnd` | numeric(20,6) nullable | For cash dividends. |
| `adjustment_factor` | numeric(20,12) | Multiplier applied to prices before `ex_date`; positive. |
| `source`, `source_revision` | varchar | Provenance. |
| `accepted_at` | timestamptz | UTC. |
| `supersedes_id` | UUID nullable | Correction chain. |

Unique: `(instrument_id, action_type, ex_date, source)`. Check:
`adjustment_factor > 0`; `ratio_denominator is null or ratio_denominator > 0`.

## Aggregate 3 — Fundamentals

### `fundamental_metric_catalog`

The versioned allowlist of metric codes. Reference data, not per-company data.
Research R-005 records why the metric set is reference data rather than columns.

| Field | Type | Constraints |
|---|---|---|
| `metric_code` | varchar(64) | Primary key together with `catalog_version`. |
| `catalog_version` | varchar(32) | Immutable version. |
| `category` | varchar(32) | `INCOME`, `BALANCE`, `CASH_FLOW`, `RATIO`, `PER_SHARE`. |
| `unit_type` | varchar(32) | `VND`, `SHARES`, `PERCENT`, `RATIO`, `COUNT`. |
| `scale` | smallint | Declared decimal scale for this metric. |
| `sign_policy` | varchar(32) | `ANY`, `NON_NEGATIVE`, `POSITIVE`. |
| `display_name_vi`, `display_name_en` | varchar(160) | Labels. |

Seeded codes include `REVENUE`, `GROSS_PROFIT`, `OPERATING_PROFIT`,
`NET_PROFIT`, `EPS`, `ROE`, `ROA`, `DEBT_TO_EQUITY`, `OPERATING_MARGIN`,
`FREE_CASH_FLOW`, `DIVIDEND_PER_SHARE`, `EQUITY_ATTRIBUTABLE_TO_PARENT`,
`TOTAL_DEBT`, `CASH_AND_EQUIVALENTS`, and `EBITDA`, covering FR-007 and every
`valuation-v1` input.

### `fundamental_report`

One accepted reporting-period revision.

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `instrument_id` | UUID | FK `market_instrument`. |
| `ingestion_record_id` | UUID | Unique FK. |
| `period_type` | varchar(16) | `PeriodType`. |
| `fiscal_year` | smallint | Four-digit year. |
| `fiscal_quarter` | smallint nullable | 1 to 4; null exactly when `period_type = 'ANNUAL'`. |
| `period_start`, `period_end` | date | Inclusive; `period_end >= period_start`. |
| `report_kind` | varchar(32) | `ReportKind`. |
| `audit_status` | varchar(32) | `AuditStatus`. |
| `currency` | char(3) | `VND` for v1. |
| `unit_scale` | integer | Power-of-ten multiplier the source used; normalized to 1 on acceptance. |
| `catalog_version` | varchar(32) | The catalog version its metric rows conform to. |
| `published_at` | timestamptz nullable | Filing time when the source supplies it. |
| `observed_at`, `accepted_at` | timestamptz | UTC. |
| `source` | varchar(64) | Provenance. |
| `revision` | integer | Starts at 1. |
| `supersedes_id` | UUID nullable | Restatement chain; self-reference forbidden. |
| `restatement_reason` | varchar(64) nullable | Required when `supersedes_id` is present. |

Unique: `(instrument_id, period_type, fiscal_year, fiscal_quarter, report_kind,
source, revision)`, with a partial unique index on the current revision.
Check: `(period_type = 'ANNUAL') = (fiscal_quarter is null)`.

`unit_scale` is recorded and then normalized because Vietnamese statements are
commonly published in millions or billions of đồng. Storing the raw number
without its scale is the single most likely way to be wrong by a factor of one
billion, so the scale is captured explicitly and the stored value is always base
VND.

### `fundamental_report_metric`

| Field | Type | Constraints |
|---|---|---|
| `report_id` | UUID | FK `fundamental_report`; part of primary key. |
| `metric_code` | varchar(64) | Part of primary key; must exist in the report's catalog version. |
| `value` | numeric(28,6) nullable | Base unit per the catalog. |
| `applicability` | varchar(32) | `MetricApplicability`. |
| `quality_reason` | varchar(64) nullable | Required when `value` is null. |

Check: `(applicability = 'DEFINED') = (value is not null)`. An absent row means
`MISSING`; a present row with `NOT_APPLICABLE` means the line item does not exist
for this company type. This is the storage-level expression of DATA-007.

### `fundamental_summary`

The immutable derived view the UI reads — trailing-twelve-month aggregates and
growth figures. Research R-006 records why this is persisted rather than computed
per request.

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `instrument_id` | UUID | FK `market_instrument`. |
| `as_of_trading_date` | date | The date this summary is effective from. |
| `rule_version` | varchar(64) | `fundamental-summary-v1`. |
| `basis_period_end` | date | `period_end` of the newest contributing report. |
| `basis_period_label` | varchar(32) | Human-readable identity such as `2026-Q2`. |
| `data_status` | varchar(32) | `DataStatus` per research R-010. |
| `calculated_at` | timestamptz | UTC. |
| `reason_codes` | text[] | Stable codes; non-empty when `data_status` is not `CURRENT`. |
| `supersedes_id` | UUID nullable | Recalculation chain. |

Values live in `fundamental_summary_metric`, mirroring the report shape:
`(summary_id, metric_code, value numeric(28,6) nullable, applicability,
quality_reason)`.

### `fundamental_summary_input`

| Field | Type | Constraints |
|---|---|---|
| `summary_id` | UUID | FK; part of primary key. |
| `input_role` | varchar(64) | Part of primary key, such as `TTM_Q1`, `PRIOR_YEAR_TTM_Q1`. |
| `report_id` | UUID | FK `fundamental_report`. |

Every contributing revision is linked, so a restatement can be traced to the
exact summary rows it invalidates.

## Aggregate 4 — Derived analysis

### `technical_indicator_result`

One immutable result per instrument, indicator, as-of date, and rule version.
Field semantics are normative in
[contracts/technical-indicators-v1.md](contracts/technical-indicators-v1.md).

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `instrument_id` | UUID | FK `market_instrument`. |
| `indicator_code` | varchar(32) | `MA20`, `MA50`, `MA200`, `RSI14`, `MACD`, `BBANDS`, `ATR14`, `AVG_VOLUME20`, `RELATIVE_VOLUME`. |
| `rule_version` | varchar(64) | `technical-indicators-v1`. |
| `as_of_trading_date` | date | Trading date of the final bar. |
| `window_start_date`, `window_end_date` | date | Inclusive bounds of consumed bars. |
| `input_bar_count` | integer | Non-negative. |
| `input_set_hash` | char(64) | SHA-256 over the canonical consumed-bar list. |
| `adjustment_status` | varchar(32) | Basis actually used. |
| `data_status` | varchar(32) | `DataStatus`. |
| `quality_reason` | varchar(64) nullable | Required when no value component exists. |
| `calculated_at` | timestamptz | UTC. |
| `supersedes_id` | UUID nullable | Recalculation chain. |

Unique: `(instrument_id, indicator_code, as_of_trading_date, rule_version,
calculated_at)`, with a partial unique index on the current revision per
`(instrument_id, indicator_code, as_of_trading_date, rule_version)`.

### `technical_indicator_value`

Component values are separate rows rather than a `jsonb` blob so that every
authoritative number keeps its `numeric` type and its check constraints.

| Field | Type | Constraints |
|---|---|---|
| `result_id` | UUID | FK; part of primary key. |
| `component_code` | varchar(32) | Part of primary key: `VALUE`, `UPPER`, `MIDDLE`, `LOWER`, `BANDWIDTH`, `MACD_LINE`, `SIGNAL`, `HISTOGRAM`, `PERCENT_OF_CLOSE`. |
| `value` | numeric(28,12) nullable | Unrounded, scale 12 per contract rule U-3. |
| `unit` | varchar(16) | `VND`, `PERCENT`, `RATIO`, `SHARES`, `POINTS`. |
| `applicability` | varchar(32) | `MetricApplicability`. |
| `quality_reason` | varchar(64) nullable | Required when `value` is null. |

Check: `(applicability = 'DEFINED') = (value is not null)`.

### `valuation_assessment`

One immutable assessment per instrument, as-of date, and rule version. Semantics
are normative in [contracts/valuation-v1.md](contracts/valuation-v1.md).

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `instrument_id` | UUID | FK `market_instrument`. |
| `as_of_trading_date` | date | Vietnam market date. |
| `as_of` | timestamptz | Latest input instant admitted. |
| `rule_version` | varchar(64) | `valuation-v1`. |
| `classification` | varchar(32) nullable | `ValuationLabel`; null when withheld. |
| `score` | numeric(6,3) nullable | Unrounded 0 to 100. |
| `displayed_score` | smallint nullable | `HALF_UP` integer of `score`. |
| `confidence` | smallint nullable | 0 to 100 data-quality measure. |
| `used_own_history` | boolean | Basis A participation. |
| `used_sector` | boolean | Basis B participation. |
| `sector_reference_id` | UUID nullable | FK; present when `used_sector` is true. |
| `sector_constituent_count` | integer nullable | Comparable constituents used. |
| `history_point_count` | integer nullable | Defined Basis A points used. |
| `data_status` | varchar(32) | `DataStatus`. |
| `reason_codes` | text[] | Non-empty when withheld. |
| `calculated_at` | timestamptz | UTC. |
| `supersedes_id` | UUID nullable | Recalculation chain. |

Checks, enforcing the contract's all-or-nothing publication rule:

```text
(classification is null and score is null and displayed_score is null
   and confidence is null)
or (classification is not null and score is not null
   and displayed_score is not null and confidence is not null)

score is null or score between 0 and 100
confidence is null or confidence between 0 and 100
used_own_history or used_sector or classification is null
used_sector = (sector_reference_id is not null)
```

The database refuses a half-published assessment. A constraint is the right place
for this rule because it is the one invariant whose violation would show the
owner a valuation label with no supporting number.

### `valuation_metric`

| Field | Type | Constraints |
|---|---|---|
| `assessment_id` | UUID | FK; part of primary key. |
| `metric_code` | varchar(32) | Part of primary key: `PE`, `PB`, `EV_EBITDA`, `PEG`, `DIVIDEND_YIELD`. |
| `value` | numeric(24,12) nullable | Unrounded metric value. |
| `applicability` | varchar(32) | `MetricApplicability`. |
| `own_history_percentile` | numeric(6,3) nullable | 0 to 100; null when Basis A unused for this metric. |
| `sector_percentile` | numeric(6,3) nullable | 0 to 100; null when Basis B unused for this metric. |
| `effective_weight` | numeric(13,12) nullable | Renormalized weight; null for unscored metrics. |
| `quality_reason` | varchar(64) nullable | Required when `value` is null. |

`DIVIDEND_YIELD` always has a null `effective_weight` — the contract displays it
without scoring it.

### `valuation_assessment_input`

Exactly one target per row, following the Feature 001 `regime_assessment_input`
pattern rather than an untyped polymorphic reference.

| Field | Type | Constraints |
|---|---|---|
| `assessment_id` | UUID | FK; part of primary key. |
| `input_role` | varchar(64) | Part of primary key, such as `PRICE_CURRENT`, `FUNDAMENTAL_SUMMARY`, `SECTOR_CROSS_SECTION`, `OWN_HISTORY_SERIES`. |
| `price_observation_id` | UUID nullable | FK `equity_price_observation`. |
| `daily_bar_id` | UUID nullable | FK `equity_daily_bar`. |
| `fundamental_summary_id` | UUID nullable | FK `fundamental_summary`. |
| `equity_profile_id` | UUID nullable | FK `equity_profile`. |
| `input_set_hash` | char(64) nullable | For large immutable sets such as the history series or the sector cross-section. |

Check: `num_nonnulls(price_observation_id, daily_bar_id,
fundamental_summary_id, equity_profile_id, input_set_hash) = 1`.

## Read models

Read models are assembled in the application layer and are not additional sources
of truth.

| Read model | Composition | Endpoint |
|---|---|---|
| `StockOverview` | `equity_profile` + latest `equity_price_observation` or `equity_daily_bar` + session state | `GET /stocks/{symbol}` |
| `StockChart` | `equity_daily_bar` window + series-level adjustment status | `GET /stocks/{symbol}/chart` |
| `StockTechnical` | Current `technical_indicator_result` rows and their components | `GET /stocks/{symbol}/technical` |
| `StockFundamentals` | Current `fundamental_report` plus current `fundamental_summary` | `GET /stocks/{symbol}/fundamentals` |
| `StockValuation` | Current `valuation_assessment` with metrics and disclosure | `GET /stocks/{symbol}/valuation` |

**Coherence key.** Every read model computes
`coherenceKey = SHA-256( ordered list of contributing row ids and revisions )`,
truncated to 32 hex characters for transport. It is a change detector for the
client, per research R-009, and never a security token or a cache authorization.

## State transitions

```text
provider record
  -> RECEIVED
  -> ACCEPTED | DUPLICATE | REJECTED
  -> SUPERSEDED            (only on an accepted correction or restatement)

accepted bar set
  -> technical_indicator_result CURRENT
  -> superseded result         (after correction or a newer as-of date)

accepted report revision
  -> fundamental_summary CURRENT
  -> superseded summary        (after restatement)

valuation candidate
  -> PUBLISHED  (classification, score, displayed_score, confidence all present)
  -> WITHHELD   (all four null, reason_codes non-empty)
```

No transition mutates a historical value.

## Retention, rebuild, and migration

- Accepted bars, reports, and corporate actions are retained for as long as any
  displayed derived result must remain reproducible. The exact production
  duration is a licensing and configuration decision, not guessed here.
- `technical_indicator_result` and `valuation_assessment` are rebuildable from
  accepted inputs and their rule version, but are retained for audit and to prove
  what the owner was actually shown on a given date.
- Migrations are forward-only Flyway starting at `V003`. New nullable columns and
  backfills precede constraints.
- Adding an indicator or a valuation metric is reference-data or code change plus
  a new rule version. It is not a schema migration, because the component and
  metric rows are already generic.
- A new rule version creates parallel results. It never rewrites `v1` history.

## Privacy and secrets

These tables hold market and company facts only. No personal data, no provider
credential, no token, and no raw provider payload is stored in any column
described above. `source` holds an allowlisted source label, not provider
response metadata — the same rule Feature 001 established.
