-- V007: Create analyst query and tool call audit tables (Feature 007 - AI Analyst)
-- Strictly audit-only metadata; contains no financial state or conversation persistence.

CREATE TABLE analyst_query (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    request_type VARCHAR(16) NOT NULL,
    question_preview VARCHAR(300) NOT NULL,
    question_hash CHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    tool_call_bound_reached BOOLEAN NOT NULL DEFAULT FALSE,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_analyst_query_request_type CHECK (request_type IN ('ASK', 'EXPLAIN')),
    CONSTRAINT chk_analyst_query_outcome CHECK (outcome IN ('COMPLETED', 'PARTIAL', 'REFUSED', 'FAILED'))
);

CREATE INDEX idx_analyst_query_owner_requested ON analyst_query (owner_id, requested_at DESC);

CREATE TABLE analyst_tool_call (
    id UUID PRIMARY KEY,
    analyst_query_id UUID NOT NULL REFERENCES analyst_query(id) ON DELETE CASCADE,
    sequence_no SMALLINT NOT NULL,
    tool_name VARCHAR(32) NOT NULL,
    arguments JSONB NOT NULL,
    status VARCHAR(16) NOT NULL,
    failure_reason VARCHAR(200),
    latency_ms INTEGER NOT NULL,
    called_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_analyst_tool_call_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_analyst_tool_call_failure_reason CHECK (
        (status = 'FAILED' AND failure_reason IS NOT NULL) OR
        (status = 'SUCCEEDED' AND failure_reason IS NULL)
    ),
    CONSTRAINT uq_analyst_tool_call_query_seq UNIQUE (analyst_query_id, sequence_no)
);

CREATE INDEX idx_analyst_tool_call_query_id ON analyst_tool_call (analyst_query_id);
