# Data Model: Market Overview

**Feature**: `001-market-overview`  
**Owner**: `finvera-be` / `market` module  
**System of record**: PostgreSQL

## Modeling Rules

- Persist transport/ingestion instants in UTC (`timestamptz`); interpret and
  display market dates in `Asia/Ho_Chi_Minh`.
- Use `BigDecimal`/PostgreSQL `numeric`; authoritative values never use binary
  floating point.
- Observations and assessments are immutable revisions. Corrections link to
  superseded records rather than overwriting them.
- PostgreSQL is authoritative. Any future Redis overview cache is disposable
  and keyed by the accepted snapshot/revision.
- Missing, invalid, zero, and not-applicable remain distinct. Nullable columns
  require a corresponding quality/reason code where ambiguity is possible.

## Enumerations and Value Objects

| Type | Values / fields | Rules |
|---|---|---|
| `Venue` | `HOSE`, `HNX`, `UPCOM` | Stable internal codes; provider codes map in the adapter. |
| `IndexCode` | `VN_INDEX`, `VN30`, `HNX_INDEX`, `UPCOM_INDEX` | Exactly four supported values in v1. |
| `SessionState` | `PRE_OPEN`, `OPEN`, `BREAK`, `INTERRUPTED`, `CLOSED`, `NON_TRADING_DAY`, `UNKNOWN` | Derived from accepted calendar plus provider observation. |
| `DataStatus` | `CURRENT`, `DELAYED`, `STALE`, `PARTIAL`, `UNAVAILABLE` | Severity ordering is defined in research R-004. `SOURCE_CONFLICT` is a reason, not another status. |
| `Direction` | `UP`, `DOWN`, `UNCHANGED` | Calculated from unrounded accepted values. |
| `RegimeLabel` | `BULL`, `EARLY_BULL`, `SIDEWAYS`, `EARLY_BEAR`, `BEAR` | Only present for publishable assessments. |
| `FactorDirection` | `POSITIVE`, `NEGATIVE`, `NEUTRAL` | Relative contribution to the regime score. |
| `ObservationKey` | source, dataset, subject, trading date, observed at, optional source sequence | Canonical idempotency identity. |
| `MoneyVnd` | amount `numeric(24,4)`, currency `VND`, scale label | API may display a labeled VND scale but retains base VND value. |
| `Percentage` | `numeric(12,6)` | Stored as percentage points, e.g. `1.250000` means 1.25%. |

## Aggregate 1 — Market Reference and Calendar

### `market_index`

Reference record for a supported index.

| Field | Type | Constraints / semantics |
|---|---|---|
| `id` | UUID | Primary key. |
| `code` | varchar(32) | Unique internal `IndexCode`. |
| `provider_symbol` | varchar(64) | Current adapter mapping; effective-dated changes use a mapping revision. |
| `display_name` | varchar(100) | Vietnamese-facing display name. |
| `venue` | varchar(16) | `Venue`; VN30 and VN-Index map to HOSE. |
| `active_from`, `active_to` | date | Inclusive effective period; `active_to` nullable. |

### `market_calendar_day`

One accepted trading-day decision per venue and calendar-policy version.

| Field | Type | Constraints / semantics |
|---|---|---|
| `id` | UUID | Primary key. |
| `venue` | varchar(16) | Part of unique key. |
| `trading_date` | date | Vietnam market date; part of unique key. |
| `is_trading_day` | boolean | Official accepted decision. |
| `policy_version` | varchar(64) | Part of unique key; immutable. |
| `source_reference` | varchar(500) | Official notice/document identifier or URL. |
| `reason_code` | varchar(64) | `NORMAL`, `HOLIDAY`, `EXTRA_SESSION`, `SUSPENSION`, etc. |
| `accepted_at` | timestamptz | UTC audit time. |

Unique: `(venue, trading_date, policy_version)`.

### `market_session_window`

Effective-dated schedule windows rather than hard-coded Java constants.

| Field | Type | Constraints / semantics |
|---|---|---|
| `id` | UUID | Primary key. |
| `venue` | varchar(16) | Venue. |
| `state` | varchar(32) | Session state represented by this window. |
| `start_local`, `end_local` | time | Interpreted in `Asia/Ho_Chi_Minh`; half-open interval. |
| `effective_from`, `effective_to` | date | Schedule validity. |
| `policy_version` | varchar(64) | Immutable schedule version. |
| `source_reference` | varchar(500) | Supporting rule/notice. |

No overlapping windows for the same venue/effective schedule.

## Aggregate 2 — Instruments and Accepted Observations

### `market_instrument`

The breadth identity and eligibility record.

| Field | Type | Constraints / semantics |
|---|---|---|
| `id` | UUID | Primary key. |
| `isin` | varchar(32) nullable | Preferred cross-source identity; unique when present and active. |
| `venue` | varchar(16) | Venue. |
| `symbol` | varchar(32) | Normalized uppercase symbol. |
| `instrument_type` | varchar(32) | `COMMON_EQUITY` required for v1 breadth. |
| `listed_from`, `listed_to` | date | Eligibility effective period. |
| `status` | varchar(32) | `ACTIVE`, `SUSPENDED`, `DELISTED`, `UNKNOWN`. |
| `source`, `source_revision` | varchar | Provenance. |

Unique fallback identity: `(venue, symbol, listed_from)`; deduplicate by ISIN
when present.

### `ingestion_record`

Audit envelope for every received provider observation, including rejected
records. Full raw payload retention is disabled by default; retain a canonical
hash and sanitized diagnostic excerpt only when licensing permits.

| Field | Type | Constraints / semantics |
|---|---|---|
| `id` | UUID | Primary key. |
| `source` | varchar(64) | Provider/feed identifier. |
| `dataset` | varchar(64) | Index, quote, reference, calendar, etc. |
| `subject_key` | varchar(128) | Index code or instrument identity. |
| `trading_date` | date | Vietnam market date. |
| `observed_at` | timestamptz | Provider observation time normalized to UTC. |
| `effective_at` | timestamptz nullable | When the fact becomes applicable. |
| `ingested_at` | timestamptz | Finvera receipt time. |
| `source_sequence` | varchar(128) nullable | Provider sequence/version if supplied. |
| `payload_hash` | char(64) | SHA-256 of canonical normalized payload. |
| `status` | varchar(32) | `ACCEPTED`, `DUPLICATE`, `REJECTED`, `SUPERSEDED`. |
| `reason_code` | varchar(64) nullable | Stable validation/rejection code. |
| `supersedes_id` | UUID nullable | Previous corrected ingestion record. |

Unique idempotency index on the complete natural key where source sequence is
present; fallback unique index on source/dataset/subject/trading date/observed
time/payload hash.

### `market_import_batch`

Audit root for an operator-supplied canonical historical package. It authorizes
no data by itself; all contained observations still pass normal validation.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key. |
| `contract_version` | varchar(64) | Initially `vnstock-history-private-bootstrap-v1`. |
| `tool_name` | varchar(64) | `VNSTOCK`. |
| `tool_version` | varchar(32) | Exact pinned package version. |
| `upstream_source` | varchar(64) | Exact Vnstock-selected source, never generic/unknown. |
| `package_sha256` | char(64) | Unique; idempotency and audit. |
| `range_start`, `range_end` | date | Completed-session requested range. |
| `generated_at`, `received_at` | timestamptz | UTC instants. |
| `status` | varchar(32) | `RECEIVED`, `VALIDATED`, `ACCEPTED`, `REJECTED`. |
| `record_count` | bigint | Non-negative and reconciles with manifest. |
| `rejection_reason` | varchar(128) nullable | Safe reason code; no raw row data. |

The canonical package is an ephemeral transfer artifact and is deleted after
successful validation/import according to the operator runbook. Accepted
normalized observations remain immutable under their retention policy.

### `index_snapshot`

Immutable accepted index observation.

| Field | Type | Constraints / semantics |
|---|---|---|
| `id` | UUID | Primary key. |
| `index_id` | UUID | FK `market_index`. |
| `ingestion_record_id` | UUID | Unique FK to accepted ingestion. |
| `trading_date` | date | Vietnam trading date. |
| `observed_at`, `accepted_at` | timestamptz | Source and Finvera times in UTC. |
| `session_state` | varchar(32) | Accepted state at observation. |
| `index_level` | numeric(18,6) | Non-negative. |
| `reference_level` | numeric(18,6) | Official comparison basis. |
| `absolute_change` | numeric(18,6) | `index_level - reference_level`; validated. |
| `percentage_change` | numeric(12,6) | Percentage points; validated with declared rounding tolerance. |
| `matched_volume` | bigint | Non-negative units; nullable only with reason. |
| `matched_value_vnd` | numeric(24,4) | Non-negative base VND; excludes put-through. |
| `source` | varchar(64) | Accepted provider/feed. |
| `revision` | integer | Starts at 1 for a source observation identity. |
| `supersedes_id` | UUID nullable | Corrected snapshot. |

Indexes: `(index_id, trading_date, observed_at desc)`, `(trading_date,
observed_at desc)`. Check constraints validate non-negative levels/liquidity and
valid correction linkage.

### `equity_price_observation`

Accepted per-security price/reference needed for breadth and historical factors.

| Field | Type | Constraints / semantics |
|---|---|---|
| `id` | UUID | Primary key. |
| `instrument_id` | UUID | FK `market_instrument`. |
| `ingestion_record_id` | UUID | Unique accepted input. |
| `trading_date` | date | Vietnam trading date. |
| `observed_at` | timestamptz | UTC. |
| `matched_or_close_price` | numeric(20,6) nullable | Raw official price used for breadth. |
| `official_reference_price` | numeric(20,6) nullable | Same-session official reference. |
| `raw_close_price` | numeric(20,6) nullable | Unadjusted official close when completed. |
| `adjusted_close_price` | numeric(20,6) nullable | Separate historical analytical series. |
| `adjustment_status` | varchar(32) | `RAW`, `PROVIDER_ADJUSTED`, `NOT_APPLICABLE`, `UNKNOWN`. |
| `quality_reason` | varchar(64) nullable | Explains missing/invalid fields; may be `PROVIDER_AUTH_REQUIRED` when TCBS renewal is required. |

Indexes: `(instrument_id, trading_date, observed_at desc)` and `(trading_date,
observed_at desc)`.

## Aggregate 3 — Derived Overview

### `breadth_snapshot`

Immutable result over a declared universe revision and coherent as-of boundary.

| Field | Type | Constraints / semantics |
|---|---|---|
| `id` | UUID | Primary key. |
| `trading_date` | date | Vietnam date. |
| `as_of` | timestamptz | Latest observation admitted to this calculation. |
| `calculated_at` | timestamptz | UTC calculation time. |
| `universe_policy_version` | varchar(64) | `breadth-universe-v1` initially. |
| `universe_revision_hash` | char(64) | Canonical eligible instrument set hash. |
| `advancing`, `declining`, `unchanged` | integer | Each non-negative. |
| `eligible` | integer | Non-negative total. |
| `unclassified` | integer | Non-negative and visible when nonzero. |
| `data_status` | varchar(32) | `PARTIAL` if unclassified > 0 or a venue is incomplete. |
| `reason_codes` | text[] | Stable quality reason codes. |
| `source_summary` | jsonb | Allowlisted sources and times only; no raw payloads. |
| `calculation_version` | varchar(64) | `breadth-v1`. |
| `supersedes_id` | UUID nullable | Recomputed correction chain. |

Check: `advancing + declining + unchanged + unclassified = eligible`.

### `breadth_snapshot_input`

Join table from breadth result to exact equity observations.

| Field | Type | Constraints / semantics |
|---|---|---|
| `breadth_snapshot_id` | UUID | FK, part of PK. |
| `instrument_id` | UUID | FK, part of PK; one security once. |
| `price_observation_id` | UUID nullable | Exact accepted observation; null only for unclassified absence. |
| `classification` | varchar(32) | `ADVANCING`, `DECLINING`, `UNCHANGED`, `UNCLASSIFIED`. |
| `reason_code` | varchar(64) nullable | Required for unclassified. |

### `regime_assessment`

Immutable deterministic `market-regime-v1` output.

| Field | Type | Constraints / semantics |
|---|---|---|
| `id` | UUID | Primary key. |
| `trading_date` | date | Vietnam date. |
| `as_of`, `calculated_at` | timestamptz | UTC. |
| `rule_version` | varchar(64) | Immutable methodology version. |
| `label` | varchar(32) nullable | Null unless publishable. |
| `score` | smallint nullable | Integer 0–100; null with unavailable/partial result. |
| `confidence` | smallint nullable | Quality score 0–100, not probability. |
| `data_status` | varchar(32) | Publication/quality state. |
| `completeness` | numeric(5,2) | 0–100. |
| `factor_agreement` | numeric(5,2) nullable | 0–100. |
| `boundary_distance` | numeric(5,2) nullable | 0–100. |
| `renormalized` | boolean | True when one allowed component was missing. |
| `reason_codes` | text[] | Required when label is null. |
| `supersedes_id` | UUID nullable | Recalculation/correction chain. |

Checks: label, score, and confidence are either all present or all absent;
numeric scores are in 0–100.

### `regime_factor`

| Field | Type | Constraints / semantics |
|---|---|---|
| `id` | UUID | Primary key. |
| `assessment_id` | UUID | FK `regime_assessment`. |
| `factor_code` | varchar(64) | `TREND`, `BREADTH`, `MOMENTUM`, `LIQUIDITY`, `VOLATILITY`. |
| `direction` | varchar(16) | Positive/negative/neutral. |
| `raw_observations` | jsonb | Typed allowlisted values, units, and input IDs. |
| `normalized_score` | numeric(8,4) nullable | 0–100. |
| `weight` | numeric(8,6) | Original weight. |
| `effective_weight` | numeric(8,6) | After permitted renormalization. |
| `contribution` | numeric(10,6) nullable | Score contribution. |
| `description_code` | varchar(64) | UI maps stable code to localized copy. |

Unique: `(assessment_id, factor_code)`.

### `regime_assessment_input`

Polymorphic references are represented as explicit nullable FKs plus a check
that exactly one target is populated.

| Field | Type | Constraints / semantics |
|---|---|---|
| `assessment_id` | UUID | FK, part of PK. |
| `input_role` | varchar(64) | Stable role, e.g. `VN_INDEX_CURRENT`, `BREADTH_CURRENT`, `VN_INDEX_HISTORY`. |
| `index_snapshot_id` | UUID nullable | Exact input. |
| `breadth_snapshot_id` | UUID nullable | Exact input. |
| `price_observation_id` | UUID nullable | Exact input. |
| `input_set_hash` | char(64) nullable | For a large immutable historical set; membership remains queryable in an associated manifest. |

## Read Model

`MarketOverview` is assembled in the application layer from one coherent
accepted revision boundary. It is not initially persisted as another source of
truth. It contains:

- overview `tradingDate`, `generatedAt`, session summary, and overall status;
- exactly four index sections in stable display order;
- one consolidated breadth section;
- one regime section, which may deliberately contain no assessment;
- warnings/reason codes and owner-visible allowlisted source labels.

A future Redis cache may serialize this read model using a key containing
contract version plus the latest accepted revision vector. Cache invalidation
occurs after a committed accepted observation or recalculation.

## Private Owner Session State

No user or session table is introduced for the single-owner v1. The stable
owner UUID, normalized username, and `{bcrypt}` hash are deployment secrets/
configuration. The authenticated session is ephemeral server memory and is
invalidated on logout, 30 minutes of idle time, eight hours absolute age, or
process restart. It is never a market-data source of truth and does not require
Redis. General users, registration, invitations, password reset, and recovery
workflows require a later auth feature and migration.

## State Transitions

```text
provider record
  -> RECEIVED
  -> ACCEPTED | DUPLICATE | REJECTED
  -> SUPERSEDED (only when a valid correction is accepted)

accepted input set
  -> derived CURRENT/DELAYED/STALE/PARTIAL result
  -> superseded derived result after correction or newer coherent boundary

regime candidate
  -> PUBLISHED (label/score/confidence all present)
  -> WITHHELD (PARTIAL or UNAVAILABLE with reason codes)
```

No transition mutates an observation's historical values.

## Retention, Rebuild, and Migration

- Retain accepted observations and derivation input links for the period needed
  to reproduce displayed assessments; exact production duration is a licensing
  gate and configuration decision, not guessed here.
- Rejected ingestion metadata may have a shorter operational retention period;
  raw payload retention is off by default.
- Breadth and regime records are rebuildable from accepted observations and
  versioned policies but retained for audit/reproducibility.
- Flyway creates forward-only schema migrations. Rollback deploys compatible
  application code; destructive reverse migrations are not automated. New
  nullable columns and backfills precede constraints for later evolution.
- A new regime formula creates a new `rule_version` and parallel assessments;
  it never rewrites v1 history.
