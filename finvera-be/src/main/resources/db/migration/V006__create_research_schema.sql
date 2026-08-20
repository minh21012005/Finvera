-- V006__create_research_schema.sql
-- Schema for Feature 006: News and Financial-Report RAG (FR-001, FR-002, DATA-001..DATA-004)

CREATE TABLE research_document (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    symbol_id UUID NULL REFERENCES market_instrument(id),
    title VARCHAR(300) NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    year SMALLINT NOT NULL,
    quarter SMALLINT NULL,
    source VARCHAR(200) NOT NULL,
    publication_date DATE NOT NULL,
    original_filename VARCHAR(300) NULL,
    original_content BYTEA NULL,
    content_mime_type VARCHAR(100) NULL,
    extracted_text TEXT NULL,
    ingestion_status VARCHAR(16) NOT NULL,
    ingestion_failure_reason VARCHAR(200) NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    CONSTRAINT chk_rd_ingestion_failure CHECK ((ingestion_status = 'FAILED') = (ingestion_failure_reason IS NOT NULL)),
    CONSTRAINT chk_rd_quarter CHECK (quarter IS NULL OR quarter BETWEEN 1 AND 4),
    CONSTRAINT chk_rd_original_content CHECK ((original_content IS NOT NULL) = (original_filename IS NOT NULL))
);

CREATE UNIQUE INDEX idx_rd_owner_idempotency ON research_document (owner_id, idempotency_key);
CREATE INDEX idx_rd_owner_id ON research_document (owner_id);
CREATE INDEX idx_rd_symbol_id ON research_document (symbol_id);

CREATE TABLE news_article (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    title VARCHAR(300) NOT NULL,
    source VARCHAR(200) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    body TEXT NOT NULL,
    reference_url VARCHAR(2000) NULL,
    category VARCHAR(16) NULL,
    category_applicability VARCHAR(32) NOT NULL DEFAULT 'MISSING',
    sentiment VARCHAR(16) NULL,
    sentiment_applicability VARCHAR(32) NOT NULL DEFAULT 'MISSING',
    impact_level VARCHAR(16) NULL,
    impact_applicability VARCHAR(32) NOT NULL DEFAULT 'MISSING',
    sector VARCHAR(100) NULL,
    ingestion_status VARCHAR(16) NOT NULL,
    ingestion_failure_reason VARCHAR(200) NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    CONSTRAINT chk_na_ingestion_failure CHECK ((ingestion_status = 'FAILED') = (ingestion_failure_reason IS NOT NULL)),
    CONSTRAINT chk_na_category CHECK ((category IS NOT NULL) = (category_applicability = 'DEFINED')),
    CONSTRAINT chk_na_sentiment CHECK ((sentiment IS NOT NULL) = (sentiment_applicability = 'DEFINED')),
    CONSTRAINT chk_na_impact CHECK ((impact_level IS NOT NULL) = (impact_applicability = 'DEFINED'))
);

CREATE UNIQUE INDEX idx_na_owner_idempotency ON news_article (owner_id, idempotency_key);
CREATE INDEX idx_na_owner_id ON news_article (owner_id);

CREATE TABLE news_article_symbol (
    news_article_id UUID NOT NULL REFERENCES news_article(id) ON DELETE CASCADE,
    instrument_id UUID NOT NULL REFERENCES market_instrument(id),
    PRIMARY KEY (news_article_id, instrument_id)
);

CREATE INDEX idx_nas_instrument_id ON news_article_symbol (instrument_id);

CREATE TABLE research_chunk (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    item_type VARCHAR(16) NOT NULL,
    research_document_id UUID NULL REFERENCES research_document(id) ON DELETE CASCADE,
    news_article_id UUID NULL REFERENCES news_article(id) ON DELETE CASCADE,
    chunk_index SMALLINT NOT NULL,
    page_number SMALLINT NULL,
    paragraph_index SMALLINT NULL,
    content_text TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    vector_point_id UUID NOT NULL UNIQUE,
    embedding_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_rc_parent_count CHECK (num_nonnulls(research_document_id, news_article_id) = 1),
    CONSTRAINT chk_rc_item_type_doc CHECK ((item_type = 'DOCUMENT') = (research_document_id IS NOT NULL)),
    CONSTRAINT chk_rc_item_type_news CHECK ((item_type = 'NEWS_ARTICLE') = (news_article_id IS NOT NULL)),
    CONSTRAINT chk_rc_page_number CHECK ((item_type = 'DOCUMENT') = (page_number IS NOT NULL)),
    CONSTRAINT chk_rc_paragraph_index CHECK ((item_type = 'NEWS_ARTICLE') = (paragraph_index IS NOT NULL))
);

CREATE INDEX idx_rc_doc_chunk ON research_chunk (research_document_id, chunk_index);
CREATE INDEX idx_rc_news_chunk ON research_chunk (news_article_id, chunk_index);
CREATE INDEX idx_rc_owner_id ON research_chunk (owner_id);
