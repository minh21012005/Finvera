# Data Model: Strategy, Signal, and Risk Scenarios

**Feature**: `004-strategy-signal-risk`
**Owner**: `finvera-be` / `stock` module (extended — research R-001)
**System of record**: PostgreSQL
**Migration**: forward-only Flyway, starting at `V004`

## Relationship to Features 001-003

This feature **reuses without modification**:

| Existing table/interface | Role here |
|---|---|
| `market_instrument`, `equity_profile` | Instrument identity; candidate universe for the strategy scan (research R-007), same `LISTED` rule as Feature 003 R-007. |
| `equity_daily_bar` | Latest accepted close and the prior trading date's bar, for level calculation (research R-003) and crossing-strategy comparison. |
| `technical_indicator_result` / `technical_indicator_value` | Every strategy condition and every risk factor's `ATR14`/`RELATIVE_VOLUME`/`RSI14`/etc. input (research R-002, R-004) — current **and** the immediately preceding trading date's row, for the three crossing strategies. |
| Feature 001's regime assessment (via its existing published read interface) | The market-regime risk factor (research R-004). |

No Feature 001/002/003 table is altered. `screener-v1`'s Breakout/Trend
derivation logic (Feature 003, `stock.domain.screener.ScreenerV1`) is
called directly, not reimplemented (FR-012).

## Enumerations

| Type | Values | Notes |
|---|---|---|
| `StrategyCode` | `TREND_FOLLOWING`, `MOMENTUM`, `BREAKOUT`, `PULLBACK`, `MEAN_REVERSION`, `MA_CROSSOVER`, `MACD_BASED`, `RSI_BASED` | Fixed, code-defined (a Java enum, not a DB reference table — eight values needing no display-name/versioning machinery `fundamental_metric_catalog` needed; Constitution Principle VIII). |
| `Direction` | `LONG` | Single value for this feature (research spec.md Assumptions); modeled as an enum, not a boolean, so a future value is additive. |
| `RiskLevel` | `LOW`, `MEDIUM`, `HIGH` | research R-004 bands. |
| `SignalStrength` | `WEAK`, `MODERATE`, `STRONG` | Derived from `RiskLevel`, research R-005; not independently stored (computed at read time from the persisted `risk_level`). |
| `RiskFactorCode` | `VOLATILITY`, `ATR`, `DRAWDOWN`, `LIQUIDITY`, `STOP_DISTANCE`, `MARKET_REGIME` | research R-004. |
| `MetricApplicability` | `DEFINED`, `NOT_APPLICABLE`, `MISSING` | Reused from Feature 002 `stock.domain.model.StockTypes`, not redefined. |

## `strategy_signal`

One immutable result per instrument, strategy, and as-of trading date. A
row exists **only when the strategy's entry condition was satisfied**
(research R-006) — a non-trigger is not persisted (see "Why non-triggers
are not persisted" below), matching FR-004's "no current signal is not a
failure" framing: there is no canonical "the pullback result" the way
there is a canonical "the valuation," so persisting eight negative rows
per instrument per day for every non-triggering strategy would be
unbounded, zero-information growth.

| Field | Type | Constraints |
|---|---|---|
| `id` | UUID | Primary key. |
| `instrument_id` | UUID | FK `market_instrument`. |
| `strategy_code` | varchar(32) | `StrategyCode`. |
| `rule_version` | varchar(64) | `strategy-signal-v1`. |
| `as_of_trading_date` | date | The trading date whose accepted data triggered this signal. |
| `direction` | varchar(16) | `Direction`. |
| `entry_low`, `entry_high` | numeric(20,6) | `entry_low <= entry_high`. |
| `stop_loss` | numeric(20,6) | `> 0`; `< entry_low` for `LONG`. |
| `target1`, `target2` | numeric(20,6) | `target1 < target2`; both `> entry_high` for `LONG`. |
| `risk_reward` | numeric(8,4) | `target1`'s R-multiple; `2.0000` by construction (research R-003), stored rather than hard-coded so a future rule version can vary it without a schema change. |
| `risk_score` | smallint nullable | `0`-`100`; null when withheld (fewer than four of six risk factors available). |
| `risk_level` | varchar(16) nullable | `RiskLevel`; null exactly when `risk_score` is null. |
| `input_set_hash` | char(64) | SHA-256 over the canonical set of contributing input values (indicator results, prior-day row where applicable, daily bars). |
| `calculated_at` | timestamptz | UTC. |
| `is_current` | boolean | Partial-unique per `(instrument_id, strategy_code, as_of_trading_date, rule_version)`, mirroring Feature 002's revision-chain pattern. |
| `supersedes_id` | UUID nullable | Recalculation chain (research FR-014: a correction recomputes and links). |

Checks: `(risk_score is null) = (risk_level is null)`; `entry_low <=
entry_high`; `target1 < target2`.

Indexes: unique `(instrument_id, strategy_code, as_of_trading_date,
rule_version, calculated_at)`; partial unique on the current revision per
`(instrument_id, strategy_code, as_of_trading_date, rule_version)`; an
index on `(strategy_code, as_of_trading_date, is_current)` for the
strategy-scan read path (research R-007).

## `strategy_signal_risk_factor`

One row per risk factor per signal — always six rows (one per
`RiskFactorCode`) even when a factor is unavailable, so "this factor was
evaluated and found undefined" stays distinguishable from "this factor was
never assessed at all," the same three-state discipline Feature 002 uses
for fundamental metrics.

| Field | Type | Constraints |
|---|---|---|
| `signal_id` | UUID | FK `strategy_signal`; part of primary key. |
| `factor_code` | varchar(32) | Part of primary key: `RiskFactorCode`. |
| `input_value` | numeric(28,12) nullable | The factor's own raw accepted value (e.g., `ATR14` `PERCENT_OF_CLOSE`, drawdown percent). |
| `factor_score` | smallint nullable | `0`-`100` on that factor's own scale. |
| `applicability` | varchar(32) | `MetricApplicability`. |
| `quality_reason` | varchar(64) nullable | Required when `applicability != DEFINED`. |

Check: `(applicability = 'DEFINED') = (factor_score is not null)`.

## `strategy_signal_input`

Exactly one target per row, mirroring Feature 002's
`valuation_assessment_input` pattern (a typed reference, not an untyped
polymorphic one) so a signal's exact reproducibility inputs are explicit.

| Field | Type | Constraints |
|---|---|---|
| `signal_id` | UUID | FK `strategy_signal`; part of primary key. |
| `input_role` | varchar(64) | Part of primary key, e.g. `TECHNICAL_RESULT_CURRENT`, `TECHNICAL_RESULT_PRIOR`, `DAILY_BAR_CURRENT`, `REGIME_ASSESSMENT`. |
| `technical_indicator_result_id` | UUID nullable | FK `technical_indicator_result`. |
| `daily_bar_id` | UUID nullable | FK `equity_daily_bar`. |
| `regime_assessment_id` | UUID nullable | FK to Feature 001's regime assessment table. |

Check: `num_nonnulls(technical_indicator_result_id, daily_bar_id,
regime_assessment_id) = 1`.

## Why non-triggers are not persisted

A `strategy_signal` row is created only on a genuine trigger. The API read
model for "does stock X currently have a signal from strategy Y" is
therefore: look for a current row; if none exists, determine **why** at
read time (not from a persisted row) by re-running strategy Y's entry
condition check — if the required indicators are insufficient, the read
model reports `INSUFFICIENT_HISTORY`; if they are sufficient but the
condition is false, it reports "no current signal," per FR-004/FR-005. This
re-check is cheap (the same in-memory boolean evaluation `screener-v1`
already performs for a candidate, not a new provider call or a
recalculation of any indicator — FR-012 still holds) and avoids inventing
a second table just to store a negative result.

## Read models

| Read model | Composition | Endpoint |
|---|---|---|
| `StockStrategySignals` | Current `strategy_signal` rows for one instrument (all triggered strategies) + `strategy_signal_risk_factor` + live re-check for non-triggered/insufficient strategies | `GET /stocks/{symbol}/signals` |
| `StrategyScanResult` | Current `strategy_signal` rows for one strategy across the candidate universe, computed on demand (research R-007) | `POST /strategies/{strategyCode}/scan` |

**Coherence key**: reuses Feature 002's `CoherenceKeys` helper (already
widened to `public` by Feature 003), computed over the contributing
`strategy_signal`/`strategy_signal_risk_factor` row ids for the response.
