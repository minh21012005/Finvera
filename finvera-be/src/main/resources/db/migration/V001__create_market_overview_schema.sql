create table market_index (
    id uuid primary key,
    code varchar(32) not null unique,
    provider_symbol varchar(64) not null,
    display_name varchar(100) not null,
    venue varchar(16) not null check (venue in ('HOSE', 'HNX', 'UPCOM')),
    active_from date not null,
    active_to date,
    check (active_to is null or active_to >= active_from)
);

create table market_calendar_day (
    id uuid primary key,
    venue varchar(16) not null check (venue in ('HOSE', 'HNX', 'UPCOM')),
    trading_date date not null,
    is_trading_day boolean not null,
    policy_version varchar(64) not null,
    source_reference varchar(500) not null,
    reason_code varchar(64) not null,
    accepted_at timestamptz not null,
    unique (venue, trading_date, policy_version)
);

create table market_session_window (
    id uuid primary key,
    venue varchar(16) not null check (venue in ('HOSE', 'HNX', 'UPCOM')),
    state varchar(32) not null check (state in (
        'PRE_OPEN', 'OPEN', 'BREAK', 'INTERRUPTED', 'CLOSED',
        'NON_TRADING_DAY', 'UNKNOWN')),
    start_local time not null,
    end_local time not null,
    effective_from date not null,
    effective_to date,
    policy_version varchar(64) not null,
    source_reference varchar(500) not null,
    check (end_local > start_local),
    check (effective_to is null or effective_to >= effective_from),
    unique (venue, state, start_local, effective_from, policy_version)
);

create table market_instrument (
    id uuid primary key,
    isin varchar(32),
    venue varchar(16) not null check (venue in ('HOSE', 'HNX', 'UPCOM')),
    symbol varchar(32) not null,
    instrument_type varchar(32) not null,
    listed_from date not null,
    listed_to date,
    status varchar(32) not null check (status in ('ACTIVE', 'SUSPENDED', 'DELISTED', 'UNKNOWN')),
    source varchar(64) not null,
    source_revision varchar(128) not null,
    check (symbol = upper(symbol)),
    check (listed_to is null or listed_to >= listed_from),
    unique (venue, symbol, listed_from)
);

create unique index uq_market_instrument_active_isin
    on market_instrument (isin) where isin is not null and listed_to is null;

create table market_import_batch (
    id uuid primary key,
    contract_version varchar(64) not null,
    tool_name varchar(64) not null,
    tool_version varchar(32) not null,
    upstream_source varchar(64) not null,
    package_sha256 char(64) not null unique,
    range_start date not null,
    range_end date not null,
    generated_at timestamptz not null,
    received_at timestamptz not null,
    status varchar(32) not null check (status in ('RECEIVED', 'VALIDATED', 'ACCEPTED', 'REJECTED')),
    record_count bigint not null check (record_count >= 0),
    rejection_reason varchar(128),
    check (range_end >= range_start),
    check (upstream_source <> '' and upstream_source <> 'UNKNOWN')
);

create table ingestion_record (
    id uuid primary key,
    source varchar(64) not null,
    dataset varchar(64) not null,
    subject_key varchar(128) not null,
    trading_date date not null,
    observed_at timestamptz not null,
    effective_at timestamptz,
    ingested_at timestamptz not null,
    source_sequence varchar(128),
    payload_hash char(64) not null,
    status varchar(32) not null check (status in ('ACCEPTED', 'DUPLICATE', 'REJECTED', 'SUPERSEDED')),
    reason_code varchar(64),
    supersedes_id uuid references ingestion_record (id),
    check (supersedes_id is null or supersedes_id <> id),
    check (payload_hash ~ '^[0-9a-f]{64}$')
);

create unique index uq_ingestion_source_sequence
    on ingestion_record (source, dataset, subject_key, trading_date, source_sequence)
    where source_sequence is not null;
create unique index uq_ingestion_fallback
    on ingestion_record (source, dataset, subject_key, trading_date, observed_at, payload_hash)
    where source_sequence is null;
create index ix_ingestion_latest
    on ingestion_record (source, dataset, subject_key, trading_date, observed_at desc);

create table index_snapshot (
    id uuid primary key,
    index_id uuid not null references market_index (id),
    ingestion_record_id uuid not null unique references ingestion_record (id),
    trading_date date not null,
    observed_at timestamptz not null,
    accepted_at timestamptz not null,
    session_state varchar(32) not null check (session_state in (
        'PRE_OPEN', 'OPEN', 'BREAK', 'INTERRUPTED', 'CLOSED',
        'NON_TRADING_DAY', 'UNKNOWN')),
    index_level numeric(18,6) not null check (index_level >= 0),
    reference_level numeric(18,6) not null check (reference_level >= 0),
    absolute_change numeric(18,6) not null,
    percentage_change numeric(12,6) not null,
    matched_volume bigint check (matched_volume is null or matched_volume >= 0),
    matched_value_vnd numeric(24,4) check (matched_value_vnd is null or matched_value_vnd >= 0),
    source varchar(64) not null,
    revision integer not null check (revision >= 1),
    supersedes_id uuid references index_snapshot (id),
    check (supersedes_id is null or supersedes_id <> id)
);

create index ix_index_snapshot_latest
    on index_snapshot (index_id, trading_date, observed_at desc);
create index ix_index_snapshot_trading_date
    on index_snapshot (trading_date, observed_at desc);

create table equity_price_observation (
    id uuid primary key,
    instrument_id uuid not null references market_instrument (id),
    ingestion_record_id uuid not null unique references ingestion_record (id),
    trading_date date not null,
    observed_at timestamptz not null,
    matched_or_close_price numeric(20,6) check (matched_or_close_price is null or matched_or_close_price >= 0),
    official_reference_price numeric(20,6) check (official_reference_price is null or official_reference_price >= 0),
    raw_close_price numeric(20,6) check (raw_close_price is null or raw_close_price >= 0),
    adjusted_close_price numeric(20,6) check (adjusted_close_price is null or adjusted_close_price >= 0),
    adjustment_status varchar(32) not null check (adjustment_status in (
        'RAW', 'PROVIDER_ADJUSTED', 'NOT_APPLICABLE', 'UNKNOWN')),
    quality_reason varchar(64),
    check (matched_or_close_price is not null or quality_reason is not null)
);

create index ix_equity_price_latest
    on equity_price_observation (instrument_id, trading_date, observed_at desc);
create index ix_equity_price_trading_date
    on equity_price_observation (trading_date, observed_at desc);

create table breadth_snapshot (
    id uuid primary key,
    trading_date date not null,
    as_of timestamptz not null,
    calculated_at timestamptz not null,
    universe_policy_version varchar(64) not null,
    universe_revision_hash char(64) not null,
    advancing integer not null check (advancing >= 0),
    declining integer not null check (declining >= 0),
    unchanged integer not null check (unchanged >= 0),
    eligible integer not null check (eligible >= 0),
    unclassified integer not null check (unclassified >= 0),
    data_status varchar(32) not null check (data_status in ('CURRENT', 'DELAYED', 'STALE', 'PARTIAL', 'UNAVAILABLE')),
    reason_codes text[] not null default '{}',
    source_summary jsonb not null default '{}',
    calculation_version varchar(64) not null,
    supersedes_id uuid references breadth_snapshot (id),
    check (advancing + declining + unchanged + unclassified = eligible),
    check (supersedes_id is null or supersedes_id <> id),
    check (universe_revision_hash ~ '^[0-9a-f]{64}$')
);

create table breadth_snapshot_input (
    breadth_snapshot_id uuid not null references breadth_snapshot (id),
    instrument_id uuid not null references market_instrument (id),
    price_observation_id uuid references equity_price_observation (id),
    classification varchar(32) not null check (classification in (
        'ADVANCING', 'DECLINING', 'UNCHANGED', 'UNCLASSIFIED')),
    reason_code varchar(64),
    primary key (breadth_snapshot_id, instrument_id),
    check ((classification = 'UNCLASSIFIED' and reason_code is not null)
        or classification <> 'UNCLASSIFIED')
);

create table regime_assessment (
    id uuid primary key,
    trading_date date not null,
    as_of timestamptz not null,
    calculated_at timestamptz not null,
    rule_version varchar(64) not null,
    label varchar(32) check (label is null or label in ('BULL', 'EARLY_BULL', 'SIDEWAYS', 'EARLY_BEAR', 'BEAR')),
    score smallint check (score is null or score between 0 and 100),
    confidence smallint check (confidence is null or confidence between 0 and 100),
    data_status varchar(32) not null check (data_status in ('CURRENT', 'DELAYED', 'STALE', 'PARTIAL', 'UNAVAILABLE')),
    completeness numeric(5,2) not null check (completeness between 0 and 100),
    factor_agreement numeric(5,2) check (factor_agreement is null or factor_agreement between 0 and 100),
    boundary_distance numeric(5,2) check (boundary_distance is null or boundary_distance between 0 and 100),
    renormalized boolean not null,
    reason_codes text[] not null default '{}',
    supersedes_id uuid references regime_assessment (id),
    check ((label is null and score is null and confidence is null)
        or (label is not null and score is not null and confidence is not null)),
    check (supersedes_id is null or supersedes_id <> id)
);

create table regime_factor (
    id uuid primary key,
    assessment_id uuid not null references regime_assessment (id),
    factor_code varchar(64) not null check (factor_code in ('TREND', 'BREADTH', 'MOMENTUM', 'LIQUIDITY', 'VOLATILITY')),
    direction varchar(16) not null check (direction in ('POSITIVE', 'NEGATIVE', 'NEUTRAL')),
    raw_observations jsonb not null,
    normalized_score numeric(8,4) check (normalized_score is null or normalized_score between 0 and 100),
    weight numeric(8,6) not null check (weight >= 0 and weight <= 1),
    effective_weight numeric(8,6) not null check (effective_weight >= 0 and effective_weight <= 1),
    contribution numeric(10,6),
    description_code varchar(64) not null,
    unique (assessment_id, factor_code)
);

create table regime_assessment_input (
    assessment_id uuid not null references regime_assessment (id),
    input_role varchar(64) not null,
    index_snapshot_id uuid references index_snapshot (id),
    breadth_snapshot_id uuid references breadth_snapshot (id),
    price_observation_id uuid references equity_price_observation (id),
    input_set_hash char(64),
    primary key (assessment_id, input_role),
    check (num_nonnulls(index_snapshot_id, breadth_snapshot_id, price_observation_id, input_set_hash) = 1),
    check (input_set_hash is null or input_set_hash ~ '^[0-9a-f]{64}$')
);

insert into market_index (id, code, provider_symbol, display_name, venue, active_from) values
    ('00000000-0000-0000-0001-000000000001', 'VN_INDEX', 'VNINDEX', 'VN-Index', 'HOSE', '2000-07-28'),
    ('00000000-0000-0000-0001-000000000002', 'VN30', 'VN30', 'VN30-Index', 'HOSE', '2012-02-06'),
    ('00000000-0000-0000-0001-000000000003', 'HNX_INDEX', 'HNXINDEX', 'HNX-Index', 'HNX', '2005-07-14'),
    ('00000000-0000-0000-0001-000000000004', 'UPCOM_INDEX', 'UPCOMINDEX', 'UPCoM-Index', 'UPCOM', '2009-06-24');
