-- V005: Create portfolio and watchlist schema (Feature 005)
-- Conforms to data-model.md and Constitution principles

CREATE TABLE portfolio (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_portfolio_owner_name ON portfolio (owner_id, name) WHERE deleted_at IS NULL;
CREATE INDEX idx_portfolio_owner_id ON portfolio (owner_id);

CREATE TABLE portfolio_transaction (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES portfolio(id),
    sequence_no BIGSERIAL NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(16) NOT NULL,
    instrument_id UUID REFERENCES market_instrument(id),
    quantity NUMERIC(20,4),
    price NUMERIC(20,6),
    fee NUMERIC(20,6) NOT NULL DEFAULT 0,
    amount NUMERIC(20,6),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    executed_at TIMESTAMPTZ NOT NULL,
    entry_at TIMESTAMPTZ NOT NULL,
    voids_transaction_id UUID REFERENCES portfolio_transaction(id),
    void_reason VARCHAR(200),
    CONSTRAINT chk_tx_type CHECK (transaction_type IN ('BUY', 'SELL', 'DEPOSIT', 'WITHDRAW', 'VOID')),
    CONSTRAINT chk_tx_buy_sell CHECK (
        transaction_type NOT IN ('BUY', 'SELL') OR
        (instrument_id IS NOT NULL AND quantity IS NOT NULL AND quantity > 0 AND price IS NOT NULL AND price > 0)
    ),
    CONSTRAINT chk_tx_cash CHECK (
        transaction_type NOT IN ('DEPOSIT', 'WITHDRAW') OR
        (amount IS NOT NULL AND amount > 0)
    ),
    CONSTRAINT chk_tx_void CHECK (
        transaction_type <> 'VOID' OR
        (voids_transaction_id IS NOT NULL AND void_reason IS NOT NULL)
    ),
    CONSTRAINT chk_tx_fee_non_negative CHECK (fee >= 0),
    CONSTRAINT uq_portfolio_tx_seq UNIQUE (portfolio_id, sequence_no),
    CONSTRAINT uq_portfolio_tx_idempotency UNIQUE (portfolio_id, idempotency_key)
);

CREATE INDEX idx_portfolio_tx_executed_at ON portfolio_transaction (portfolio_id, executed_at);
CREATE UNIQUE INDEX uq_portfolio_tx_voids ON portfolio_transaction (voids_transaction_id) WHERE voids_transaction_id IS NOT NULL;

CREATE TABLE watchlist (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_watchlist_owner_name UNIQUE (owner_id, name)
);

CREATE INDEX idx_watchlist_owner_id ON watchlist (owner_id);

CREATE TABLE watchlist_item (
    watchlist_id UUID NOT NULL REFERENCES watchlist(id) ON DELETE CASCADE,
    instrument_id UUID NOT NULL REFERENCES market_instrument(id),
    added_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (watchlist_id, instrument_id)
);
