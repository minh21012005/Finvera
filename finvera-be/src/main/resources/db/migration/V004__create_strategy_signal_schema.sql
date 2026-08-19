-- Feature 004 (Strategy, Signal, and Risk Scenarios). Additive only: no
-- Feature 001/002/003 table is altered. See
-- specs/004-strategy-signal-risk/data-model.md for the normative
-- field-by-field rationale.

create table strategy_signal (
    id uuid primary key,
    instrument_id uuid not null references market_instrument (id),
    strategy_code varchar(32) not null check (strategy_code in
        ('TREND_FOLLOWING', 'MOMENTUM', 'BREAKOUT', 'PULLBACK', 'MEAN_REVERSION',
         'MA_CROSSOVER', 'MACD_BASED', 'RSI_BASED')),
    rule_version varchar(64) not null,
    as_of_trading_date date not null,
    direction varchar(16) not null check (direction in ('LONG')),
    entry_low numeric(20,6) not null,
    entry_high numeric(20,6) not null,
    stop_loss numeric(20,6) not null check (stop_loss > 0),
    target1 numeric(20,6) not null,
    target2 numeric(20,6) not null,
    risk_reward numeric(8,4) not null,
    risk_score smallint check (risk_score is null or risk_score between 0 and 100),
    risk_level varchar(16) check (risk_level is null or risk_level in ('LOW', 'MEDIUM', 'HIGH')),
    input_set_hash char(64) not null check (input_set_hash ~ '^[0-9a-f]{64}$'),
    calculated_at timestamptz not null,
    is_current boolean not null default true,
    supersedes_id uuid references strategy_signal (id),
    check (entry_low <= entry_high),
    check (stop_loss < entry_low),
    check (target1 < target2),
    check (target1 > entry_high and target2 > entry_high),
    check ((risk_score is null) = (risk_level is null)),
    check (supersedes_id is null or supersedes_id <> id),
    unique (instrument_id, strategy_code, as_of_trading_date, rule_version, calculated_at)
);

create unique index uq_strategy_signal_current
    on strategy_signal (instrument_id, strategy_code, as_of_trading_date, rule_version)
    where is_current;
create index ix_strategy_signal_scan
    on strategy_signal (strategy_code, as_of_trading_date desc, is_current);
create index ix_strategy_signal_instrument
    on strategy_signal (instrument_id, rule_version, as_of_trading_date desc) where is_current;

create table strategy_signal_risk_factor (
    signal_id uuid not null references strategy_signal (id),
    factor_code varchar(32) not null check (factor_code in
        ('VOLATILITY', 'ATR', 'DRAWDOWN', 'LIQUIDITY', 'STOP_DISTANCE', 'MARKET_REGIME')),
    input_value numeric(28,12),
    factor_score smallint check (factor_score is null or factor_score between 0 and 100),
    applicability varchar(32) not null check (applicability in ('DEFINED', 'NOT_APPLICABLE', 'MISSING')),
    quality_reason varchar(64),
    primary key (signal_id, factor_code),
    check ((applicability = 'DEFINED') = (factor_score is not null))
);

create table strategy_signal_input (
    signal_id uuid not null references strategy_signal (id),
    input_role varchar(64) not null,
    technical_indicator_result_id uuid references technical_indicator_result (id),
    daily_bar_id uuid references equity_daily_bar (id),
    regime_assessment_id uuid references regime_assessment (id),
    primary key (signal_id, input_role),
    check (num_nonnulls(technical_indicator_result_id, daily_bar_id, regime_assessment_id) = 1)
);
