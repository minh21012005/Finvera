-- Feature 031: durable owner-scoped historical strategy backtesting.

CREATE TABLE backtest_run (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    idempotency_key VARCHAR(100),
    status VARCHAR(16) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    strategy_code VARCHAR(32) NOT NULL,
    strategy_rule_version VARCHAR(40) NOT NULL,
    symbol VARCHAR(10) NOT NULL,
    instrument_id UUID NOT NULL,
    venue VARCHAR(16) NOT NULL,
    reporting_start DATE NOT NULL,
    reporting_end DATE NOT NULL,
    initial_capital_vnd NUMERIC(20,6) NOT NULL,
    risk_per_tranche_rate NUMERIC(20,8) NOT NULL,
    max_aggregate_open_risk_rate NUMERIC(20,8) NOT NULL,
    costs_excluded BOOLEAN NOT NULL,
    entry_fee_rate NUMERIC(20,8) NOT NULL,
    exit_fee_rate NUMERIC(20,8) NOT NULL,
    sell_tax_rate NUMERIC(20,8) NOT NULL,
    entry_slippage_rate NUMERIC(20,8) NOT NULL,
    exit_slippage_rate NUMERIC(20,8) NOT NULL,
    engine_rule_version VARCHAR(40) NOT NULL,
    metrics_rule_version VARCHAR(40) NOT NULL,
    pyramiding_rule_version VARCHAR(40) NOT NULL,
    sizing_rule_version VARCHAR(40) NOT NULL,
    lot_rule_version VARCHAR(40) NOT NULL,
    data_cutoff_accepted_at TIMESTAMPTZ NOT NULL,
    input_fingerprint VARCHAR(64),
    processed_sessions INTEGER NOT NULL DEFAULT 0,
    total_sessions INTEGER,
    attempt_count SMALLINT NOT NULL DEFAULT 0,
    heartbeat_at TIMESTAMPTZ,
    reason_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_backtest_run_owner_id UNIQUE (id, owner_id),
    CONSTRAINT chk_backtest_run_status CHECK (status IN ('QUEUED','RUNNING','COMPLETED','WITHHELD','FAILED')),
    CONSTRAINT chk_backtest_run_strategy CHECK (strategy_code IN ('TREND_FOLLOWING','MOMENTUM','BREAKOUT','PULLBACK','MEAN_REVERSION','MA_CROSSOVER','MACD_BASED','RSI_BASED')),
    CONSTRAINT chk_backtest_run_dates CHECK (reporting_start <= reporting_end),
    CONSTRAINT chk_backtest_run_money CHECK (initial_capital_vnd > 0),
    CONSTRAINT chk_backtest_run_risk CHECK (risk_per_tranche_rate > 0 AND risk_per_tranche_rate <= 1 AND max_aggregate_open_risk_rate >= risk_per_tranche_rate AND max_aggregate_open_risk_rate <= 1),
    CONSTRAINT chk_backtest_run_costs CHECK (entry_fee_rate >= 0 AND entry_fee_rate < 1 AND exit_fee_rate >= 0 AND exit_fee_rate < 1 AND sell_tax_rate >= 0 AND sell_tax_rate < 1 AND entry_slippage_rate >= 0 AND entry_slippage_rate < 1 AND exit_slippage_rate >= 0 AND exit_slippage_rate < 1 AND exit_fee_rate + sell_tax_rate < 1 AND (NOT costs_excluded OR entry_fee_rate + exit_fee_rate + sell_tax_rate + entry_slippage_rate + exit_slippage_rate = 0) AND (costs_excluded OR entry_fee_rate + exit_fee_rate + sell_tax_rate + entry_slippage_rate + exit_slippage_rate > 0)),
    CONSTRAINT chk_backtest_run_progress CHECK (processed_sessions >= 0 AND (total_sessions IS NULL OR (total_sessions >= 0 AND processed_sessions <= total_sessions))),
    CONSTRAINT chk_backtest_run_attempt CHECK (attempt_count BETWEEN 0 AND 2),
    CONSTRAINT chk_backtest_run_terminal CHECK ((status IN ('QUEUED','RUNNING') AND completed_at IS NULL AND reason_code IS NULL) OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND reason_code IS NULL AND input_fingerprint IS NOT NULL) OR (status IN ('WITHHELD','FAILED') AND completed_at IS NOT NULL AND reason_code IS NOT NULL))
);
CREATE UNIQUE INDEX uq_backtest_run_owner_idempotency ON backtest_run(owner_id,idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_backtest_run_owner_page ON backtest_run(owner_id,created_at DESC,id DESC);
CREATE INDEX ix_backtest_run_claim ON backtest_run(status,created_at) WHERE status='QUEUED';
CREATE INDEX ix_backtest_run_stale ON backtest_run(status,heartbeat_at) WHERE status='RUNNING';

CREATE TABLE backtest_trade (
    id UUID PRIMARY KEY, run_id UUID NOT NULL REFERENCES backtest_run(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL, signal_date DATE NOT NULL, entry_date DATE NOT NULL, exit_date DATE NOT NULL,
    strategy_code VARCHAR(32) NOT NULL, strategy_rule_version VARCHAR(40) NOT NULL,
    signal_atr14_vnd NUMERIC(34,12) NOT NULL, stop_price_vnd NUMERIC(34,12) NOT NULL, target_price_vnd NUMERIC(34,12) NOT NULL,
    quantity BIGINT NOT NULL, lot_size BIGINT NOT NULL,
    raw_entry_price_vnd NUMERIC(34,12) NOT NULL, effective_entry_price_vnd NUMERIC(34,12) NOT NULL,
    effective_exit_price_vnd NUMERIC(34,12) NOT NULL, entry_fee_vnd NUMERIC(34,12) NOT NULL,
    exit_fee_vnd NUMERIC(34,12) NOT NULL, sell_tax_vnd NUMERIC(34,12) NOT NULL,
    acquisition_cost_vnd NUMERIC(34,12) NOT NULL, net_exit_proceeds_vnd NUMERIC(34,12) NOT NULL,
    net_pnl_vnd NUMERIC(34,12) NOT NULL, trade_return_rate NUMERIC(34,8) NOT NULL,
    exit_reason VARCHAR(24) NOT NULL, entry_bar_id UUID NOT NULL, exit_bar_id UUID NOT NULL,
    UNIQUE(run_id,sequence_no),
    CHECK(entry_date <= exit_date AND quantity > 0 AND lot_size > 0 AND quantity % lot_size = 0),
    CHECK(exit_reason IN ('STOP_LOSS','TARGET1','END_OF_PERIOD'))
);

CREATE TABLE backtest_equity_point (
    id UUID PRIMARY KEY, run_id UUID NOT NULL REFERENCES backtest_run(id) ON DELETE CASCADE,
    trading_date DATE NOT NULL, cash_vnd NUMERIC(34,12) NOT NULL,
    open_position_value_vnd NUMERIC(34,12) NOT NULL, total_equity_vnd NUMERIC(34,12) NOT NULL,
    open_tranche_count SMALLINT NOT NULL, daily_return_rate NUMERIC(34,8), daily_bar_id UUID NOT NULL,
    UNIQUE(run_id,trading_date), CHECK(total_equity_vnd = cash_vnd + open_position_value_vnd),
    CHECK(open_tranche_count BETWEEN 0 AND 4)
);

CREATE TABLE backtest_metric (
    id UUID PRIMARY KEY, run_id UUID NOT NULL REFERENCES backtest_run(id) ON DELETE CASCADE,
    metric_code VARCHAR(32) NOT NULL, metric_value NUMERIC(34,8), unit VARCHAR(8) NOT NULL,
    availability VARCHAR(16) NOT NULL, reason_code VARCHAR(64), metric_rule_version VARCHAR(40) NOT NULL,
    UNIQUE(run_id,metric_code),
    CHECK(metric_code IN ('TOTAL_RETURN','CAGR','WIN_RATE','PROFIT_FACTOR','MAXIMUM_DRAWDOWN','SHARPE_RATIO','AVERAGE_TRADE_RETURN','TRADE_COUNT')),
    CHECK(unit IN ('RATE','RATIO','COUNT')), CHECK(availability IN ('DEFINED','UNAVAILABLE')),
    CHECK((availability='DEFINED' AND metric_value IS NOT NULL AND reason_code IS NULL) OR (availability='UNAVAILABLE' AND metric_value IS NULL AND reason_code IS NOT NULL))
);

CREATE TABLE backtest_entry_event (
    id UUID PRIMARY KEY, run_id UUID NOT NULL REFERENCES backtest_run(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL, signal_date DATE NOT NULL, execution_date DATE,
    outcome VARCHAR(16) NOT NULL, reason_code VARCHAR(64) NOT NULL,
    open_tranche_count SMALLINT NOT NULL, available_cash_vnd NUMERIC(34,12), remaining_risk_vnd NUMERIC(34,12),
    UNIQUE(run_id,sequence_no), CHECK(outcome IN ('REJECTED','CANCELLED')),
    CHECK(open_tranche_count BETWEEN 0 AND 4)
);

CREATE TABLE backtest_evidence (
    id UUID PRIMARY KEY, run_id UUID NOT NULL REFERENCES backtest_run(id) ON DELETE CASCADE,
    evidence_key VARCHAR(64) NOT NULL, evidence_value TEXT NOT NULL, unit VARCHAR(16) NOT NULL,
    UNIQUE(run_id,evidence_key), CHECK(unit IN ('TEXT','DATE','INSTANT','VND','RATE','COUNT'))
);

CREATE INDEX ix_backtest_trade_page ON backtest_trade(run_id,sequence_no);
CREATE INDEX ix_backtest_equity_page ON backtest_equity_point(run_id,trading_date);
CREATE INDEX ix_backtest_event_page ON backtest_entry_event(run_id,sequence_no);
