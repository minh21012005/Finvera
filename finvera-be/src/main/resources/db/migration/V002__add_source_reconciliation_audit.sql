create table source_reconciliation_audit (
    id uuid primary key,
    instrument_id uuid not null references market_instrument (id),
    trading_date date not null,
    tcbs_ingestion_record_id uuid not null references ingestion_record (id),
    vnstock_ingestion_record_id uuid not null references ingestion_record (id),
    decision varchar(32) not null check (decision in ('SOURCE_CONFLICT', 'NON_COMPARABLE')),
    policy_version varchar(64) not null,
    detected_at timestamptz not null,
    unique (tcbs_ingestion_record_id, vnstock_ingestion_record_id, policy_version)
);

create index ix_source_reconciliation_audit_instrument_date
    on source_reconciliation_audit (instrument_id, trading_date, detected_at desc);
