-- Feature 029: durable, owner-scoped AI Analyst conversation history.

CREATE TABLE analyst_conversation (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    title_source VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    next_sequence_no BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT uq_analyst_conversation_id_owner UNIQUE (id, owner_id),
    CONSTRAINT chk_analyst_conversation_title CHECK (length(btrim(title)) BETWEEN 1 AND 120),
    CONSTRAINT chk_analyst_conversation_title_source CHECK (title_source IN ('AUTO', 'OWNER')),
    CONSTRAINT chk_analyst_conversation_next_sequence CHECK (next_sequence_no >= 1)
);

CREATE INDEX idx_analyst_conversation_owner_activity
    ON analyst_conversation (owner_id, last_activity_at DESC, id DESC);

CREATE TABLE analyst_conversation_exchange (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    sequence_no BIGINT NOT NULL,
    client_request_id UUID NOT NULL,
    question VARCHAR(2000) NOT NULL,
    symbol VARCHAR(20),
    status VARCHAR(16) NOT NULL,
    answer TEXT,
    response_schema_version VARCHAR(40),
    response_metadata JSONB,
    failure_code VARCHAR(64),
    context_rule_version VARCHAR(40) NOT NULL,
    context_included_count SMALLINT NOT NULL DEFAULT 0,
    context_omitted_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT fk_analyst_conversation_exchange_owner
        FOREIGN KEY (conversation_id, owner_id)
        REFERENCES analyst_conversation(id, owner_id) ON DELETE CASCADE,
    CONSTRAINT uq_analyst_conversation_exchange_sequence UNIQUE (conversation_id, sequence_no),
    CONSTRAINT uq_analyst_conversation_exchange_request UNIQUE (owner_id, client_request_id),
    CONSTRAINT chk_analyst_conversation_exchange_question CHECK (length(btrim(question)) BETWEEN 1 AND 2000),
    CONSTRAINT chk_analyst_conversation_exchange_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_analyst_conversation_exchange_context CHECK (
        context_included_count BETWEEN 0 AND 5 AND context_omitted_count >= 0
    ),
    CONSTRAINT chk_analyst_conversation_exchange_state CHECK (
        (status = 'PROCESSING' AND answer IS NULL AND response_schema_version IS NULL
            AND response_metadata IS NULL AND failure_code IS NULL AND completed_at IS NULL)
        OR
        (status = 'COMPLETED' AND answer IS NOT NULL AND response_schema_version IS NOT NULL
            AND response_metadata IS NOT NULL AND failure_code IS NULL AND completed_at IS NOT NULL)
        OR
        (status IN ('FAILED', 'CANCELLED') AND answer IS NULL AND response_schema_version IS NULL
            AND response_metadata IS NULL AND failure_code IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_analyst_conversation_exchange_page
    ON analyst_conversation_exchange (conversation_id, sequence_no DESC, id DESC);
CREATE UNIQUE INDEX uq_analyst_conversation_exchange_processing
    ON analyst_conversation_exchange (conversation_id) WHERE status = 'PROCESSING';

ALTER TABLE analyst_query
    ADD COLUMN conversation_exchange_id UUID
        REFERENCES analyst_conversation_exchange(id) ON DELETE CASCADE;

CREATE UNIQUE INDEX uq_analyst_query_conversation_exchange
    ON analyst_query (conversation_exchange_id)
    WHERE conversation_exchange_id IS NOT NULL;
