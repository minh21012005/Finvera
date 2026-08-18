-- Feature 002 (Stock Detail and Analysis). Additive only: no Feature 001
-- table is altered. See specs/002-stock-detail-analysis/data-model.md for the
-- normative field-by-field rationale.

create table sector_reference (
    id uuid primary key,
    scheme varchar(32) not null,
    scheme_version varchar(32) not null,
    sector_code varchar(32) not null,
    display_name_vi varchar(160) not null,
    display_name_en varchar(160),
    unique (scheme, scheme_version, sector_code)
);

create table equity_profile (
    id uuid primary key,
    instrument_id uuid not null references market_instrument (id),
    company_name_vi varchar(255) not null,
    company_name_en varchar(255),
    sector_reference_id uuid references sector_reference (id),
    shares_outstanding bigint check (shares_outstanding is null or shares_outstanding >= 0),
    free_float_ratio numeric(9,6) check (free_float_ratio is null or free_float_ratio between 0 and 1),
    listing_status varchar(32) not null check (listing_status in
        ('LISTED', 'SUSPENDED', 'HALTED', 'DELISTED', 'UNKNOWN')),
    effective_from date not null,
    effective_to date,
    source varchar(64) not null,
    source_revision varchar(128) not null,
    quality_reason varchar(64),
    check (effective_to is null or effective_to >= effective_from),
    check (sector_reference_id is not null or shares_outstanding is not null or quality_reason is not null),
    unique (instrument_id, effective_from)
);

create unique index uq_equity_profile_current
    on equity_profile (instrument_id) where effective_to is null;

create table equity_daily_bar (
    id uuid primary key,
    instrument_id uuid not null references market_instrument (id),
    ingestion_record_id uuid not null unique references ingestion_record (id),
    import_batch_id uuid references market_import_batch (id),
    trading_date date not null,
    open_price numeric(20,6) not null check (open_price >= 0),
    high_price numeric(20,6) not null check (high_price >= 0),
    low_price numeric(20,6) not null check (low_price >= 0),
    close_price numeric(20,6) not null check (close_price >= 0),
    adjusted_close numeric(20,6),
    adjustment_factor numeric(20,12),
    adjustment_status varchar(32) not null check (adjustment_status in
        ('ADJUSTED', 'RAW', 'NOT_APPLICABLE', 'UNKNOWN')),
    volume bigint check (volume is null or volume >= 0),
    value_vnd numeric(24,4) check (value_vnd is null or value_vnd >= 0),
    source varchar(64) not null,
    observed_at timestamptz not null,
    accepted_at timestamptz not null,
    revision integer not null check (revision >= 1),
    is_current boolean not null default true,
    supersedes_id uuid references equity_daily_bar (id),
    quality_reason varchar(64),
    check (high_price >= low_price),
    check (high_price >= open_price and high_price >= close_price),
    check (low_price <= open_price and low_price <= close_price),
    check (adjusted_close is null or adjustment_factor is not null),
    check (supersedes_id is null or supersedes_id <> id),
    unique (instrument_id, trading_date, source, revision)
);

create unique index uq_equity_daily_bar_current
    on equity_daily_bar (instrument_id, trading_date, source) where is_current;
create index ix_equity_daily_bar_window
    on equity_daily_bar (instrument_id, trading_date desc) where is_current;

create table corporate_action (
    id uuid primary key,
    instrument_id uuid not null references market_instrument (id),
    ingestion_record_id uuid not null unique references ingestion_record (id),
    action_type varchar(32) not null check (action_type in
        ('SPLIT', 'STOCK_DIVIDEND', 'CASH_DIVIDEND', 'RIGHTS_ISSUE', 'OTHER')),
    ex_date date not null,
    record_date date,
    payment_date date,
    ratio_numerator numeric(20,6) check (ratio_numerator is null or ratio_numerator > 0),
    ratio_denominator numeric(20,6) check (ratio_denominator is null or ratio_denominator > 0),
    cash_amount_vnd numeric(20,6) check (cash_amount_vnd is null or cash_amount_vnd >= 0),
    adjustment_factor numeric(20,12) not null check (adjustment_factor > 0),
    source varchar(64) not null,
    source_revision varchar(128) not null,
    accepted_at timestamptz not null,
    supersedes_id uuid references corporate_action (id),
    check (supersedes_id is null or supersedes_id <> id),
    unique (instrument_id, action_type, ex_date, source)
);

create index ix_corporate_action_window
    on corporate_action (instrument_id, ex_date);

create table fundamental_metric_catalog (
    metric_code varchar(64) not null,
    catalog_version varchar(32) not null,
    category varchar(32) not null check (category in
        ('INCOME', 'BALANCE', 'CASH_FLOW', 'RATIO', 'PER_SHARE')),
    unit_type varchar(32) not null check (unit_type in
        ('VND', 'SHARES', 'PERCENT', 'RATIO', 'COUNT')),
    scale smallint not null check (scale >= 0),
    sign_policy varchar(32) not null check (sign_policy in ('ANY', 'NON_NEGATIVE', 'POSITIVE')),
    display_name_vi varchar(160) not null,
    display_name_en varchar(160) not null,
    primary key (metric_code, catalog_version)
);

create table fundamental_report (
    id uuid primary key,
    instrument_id uuid not null references market_instrument (id),
    ingestion_record_id uuid not null unique references ingestion_record (id),
    period_type varchar(16) not null check (period_type in ('ANNUAL', 'QUARTER')),
    fiscal_year smallint not null,
    fiscal_quarter smallint check (fiscal_quarter is null or fiscal_quarter between 1 and 4),
    period_start date not null,
    period_end date not null,
    report_kind varchar(32) not null check (report_kind in ('CONSOLIDATED', 'SEPARATE')),
    audit_status varchar(32) not null check (audit_status in
        ('AUDITED', 'REVIEWED', 'UNAUDITED', 'UNKNOWN')),
    currency char(3) not null,
    unit_scale integer not null check (unit_scale >= 1),
    catalog_version varchar(32) not null,
    published_at timestamptz,
    observed_at timestamptz not null,
    accepted_at timestamptz not null,
    source varchar(64) not null,
    revision integer not null check (revision >= 1),
    is_current boolean not null default true,
    supersedes_id uuid references fundamental_report (id),
    restatement_reason varchar(64),
    check (period_end >= period_start),
    check ((period_type = 'ANNUAL') = (fiscal_quarter is null)),
    check (supersedes_id is null or supersedes_id <> id),
    check (supersedes_id is null or restatement_reason is not null),
    unique (instrument_id, period_type, fiscal_year, fiscal_quarter, report_kind, source, revision)
);

create unique index uq_fundamental_report_current
    on fundamental_report (instrument_id, period_type, fiscal_year, fiscal_quarter, report_kind, source)
    where is_current;
create index ix_fundamental_report_period
    on fundamental_report (instrument_id, period_end desc) where is_current;

create table fundamental_report_metric (
    report_id uuid not null references fundamental_report (id),
    metric_code varchar(64) not null,
    value numeric(28,6),
    applicability varchar(32) not null check (applicability in ('DEFINED', 'NOT_APPLICABLE', 'MISSING')),
    quality_reason varchar(64),
    primary key (report_id, metric_code),
    check ((applicability = 'DEFINED') = (value is not null))
);

create table fundamental_summary (
    id uuid primary key,
    instrument_id uuid not null references market_instrument (id),
    as_of_trading_date date not null,
    rule_version varchar(64) not null,
    basis_period_end date not null,
    basis_period_label varchar(32) not null,
    data_status varchar(32) not null check (data_status in
        ('CURRENT', 'DELAYED', 'STALE', 'PARTIAL', 'UNAVAILABLE')),
    calculated_at timestamptz not null,
    reason_codes text[] not null default '{}',
    supersedes_id uuid references fundamental_summary (id),
    check (supersedes_id is null or supersedes_id <> id)
);

create index ix_fundamental_summary_latest
    on fundamental_summary (instrument_id, rule_version, as_of_trading_date desc, calculated_at desc);

create table fundamental_summary_metric (
    summary_id uuid not null references fundamental_summary (id),
    metric_code varchar(64) not null,
    value numeric(28,6),
    applicability varchar(32) not null check (applicability in ('DEFINED', 'NOT_APPLICABLE', 'MISSING')),
    quality_reason varchar(64),
    primary key (summary_id, metric_code),
    check ((applicability = 'DEFINED') = (value is not null))
);

create table fundamental_summary_input (
    summary_id uuid not null references fundamental_summary (id),
    input_role varchar(64) not null,
    report_id uuid not null references fundamental_report (id),
    primary key (summary_id, input_role)
);

create table technical_indicator_result (
    id uuid primary key,
    instrument_id uuid not null references market_instrument (id),
    indicator_code varchar(32) not null check (indicator_code in
        ('MA20', 'MA50', 'MA200', 'RSI14', 'MACD', 'BBANDS', 'ATR14',
         'AVG_VOLUME20', 'RELATIVE_VOLUME')),
    rule_version varchar(64) not null,
    as_of_trading_date date not null,
    window_start_date date not null,
    window_end_date date not null,
    input_bar_count integer not null check (input_bar_count >= 0),
    input_set_hash char(64) not null check (input_set_hash ~ '^[0-9a-f]{64}$'),
    adjustment_status varchar(32) not null check (adjustment_status in
        ('ADJUSTED', 'RAW', 'NOT_APPLICABLE', 'UNKNOWN')),
    data_status varchar(32) not null check (data_status in
        ('CURRENT', 'DELAYED', 'STALE', 'PARTIAL', 'UNAVAILABLE')),
    quality_reason varchar(64),
    calculated_at timestamptz not null,
    is_current boolean not null default true,
    supersedes_id uuid references technical_indicator_result (id),
    check (window_end_date >= window_start_date),
    check (supersedes_id is null or supersedes_id <> id),
    unique (instrument_id, indicator_code, as_of_trading_date, rule_version, calculated_at)
);

create unique index uq_technical_indicator_result_current
    on technical_indicator_result (instrument_id, indicator_code, as_of_trading_date, rule_version)
    where is_current;
create index ix_technical_indicator_result_symbol
    on technical_indicator_result (instrument_id, rule_version, as_of_trading_date desc) where is_current;

create table technical_indicator_value (
    result_id uuid not null references technical_indicator_result (id),
    component_code varchar(32) not null check (component_code in
        ('VALUE', 'UPPER', 'MIDDLE', 'LOWER', 'BANDWIDTH', 'MACD_LINE', 'SIGNAL',
         'HISTOGRAM', 'PERCENT_OF_CLOSE')),
    value numeric(28,12),
    unit varchar(16) not null check (unit in ('VND', 'PERCENT', 'RATIO', 'SHARES', 'POINTS')),
    applicability varchar(32) not null check (applicability in ('DEFINED', 'NOT_APPLICABLE', 'MISSING')),
    quality_reason varchar(64),
    primary key (result_id, component_code),
    check ((applicability = 'DEFINED') = (value is not null))
);

create table valuation_assessment (
    id uuid primary key,
    instrument_id uuid not null references market_instrument (id),
    as_of_trading_date date not null,
    as_of timestamptz not null,
    rule_version varchar(64) not null,
    classification varchar(32) check (classification is null or classification in
        ('UNDER_VALUED', 'FAIR_VALUED', 'OVER_VALUED')),
    score numeric(6,3) check (score is null or score between 0 and 100),
    displayed_score smallint check (displayed_score is null or displayed_score between 0 and 100),
    confidence smallint check (confidence is null or confidence between 0 and 100),
    used_own_history boolean not null,
    used_sector boolean not null,
    sector_reference_id uuid references sector_reference (id),
    sector_constituent_count integer check (sector_constituent_count is null or sector_constituent_count >= 0),
    history_point_count integer check (history_point_count is null or history_point_count >= 0),
    data_status varchar(32) not null check (data_status in
        ('CURRENT', 'DELAYED', 'STALE', 'PARTIAL', 'UNAVAILABLE')),
    reason_codes text[] not null default '{}',
    calculated_at timestamptz not null,
    is_current boolean not null default true,
    supersedes_id uuid references valuation_assessment (id),
    check ((classification is null and score is null and displayed_score is null and confidence is null)
        or (classification is not null and score is not null and displayed_score is not null and confidence is not null)),
    check (used_own_history or used_sector or classification is null),
    check (used_sector = (sector_reference_id is not null)),
    check (supersedes_id is null or supersedes_id <> id)
);

create unique index uq_valuation_assessment_current
    on valuation_assessment (instrument_id, rule_version, as_of_trading_date) where is_current;
create index ix_valuation_assessment_latest
    on valuation_assessment (instrument_id, rule_version, as_of_trading_date desc) where is_current;

create table valuation_metric (
    assessment_id uuid not null references valuation_assessment (id),
    metric_code varchar(32) not null check (metric_code in ('PE', 'PB', 'EV_EBITDA', 'PEG', 'DIVIDEND_YIELD')),
    value numeric(24,12),
    applicability varchar(32) not null check (applicability in ('DEFINED', 'NOT_APPLICABLE', 'MISSING')),
    own_history_percentile numeric(6,3) check (own_history_percentile is null or own_history_percentile between 0 and 100),
    sector_percentile numeric(6,3) check (sector_percentile is null or sector_percentile between 0 and 100),
    effective_weight numeric(13,12) check (effective_weight is null or effective_weight between 0 and 1),
    quality_reason varchar(64),
    primary key (assessment_id, metric_code),
    check ((applicability = 'DEFINED') = (value is not null))
);

create table valuation_assessment_input (
    assessment_id uuid not null references valuation_assessment (id),
    input_role varchar(64) not null,
    price_observation_id uuid references equity_price_observation (id),
    daily_bar_id uuid references equity_daily_bar (id),
    fundamental_summary_id uuid references fundamental_summary (id),
    equity_profile_id uuid references equity_profile (id),
    input_set_hash char(64) check (input_set_hash is null or input_set_hash ~ '^[0-9a-f]{64}$'),
    primary key (assessment_id, input_role),
    check (num_nonnulls(price_observation_id, daily_bar_id, fundamental_summary_id,
        equity_profile_id, input_set_hash) = 1)
);

insert into fundamental_metric_catalog
    (metric_code, catalog_version, category, unit_type, scale, sign_policy, display_name_vi, display_name_en)
values
    ('REVENUE', 'fundamental-metric-catalog-v1', 'INCOME', 'VND', 0, 'NON_NEGATIVE', 'Doanh thu', 'Revenue'),
    ('GROSS_PROFIT', 'fundamental-metric-catalog-v1', 'INCOME', 'VND', 0, 'ANY', 'Lợi nhuận gộp', 'Gross profit'),
    ('OPERATING_PROFIT', 'fundamental-metric-catalog-v1', 'INCOME', 'VND', 0, 'ANY', 'Lợi nhuận hoạt động', 'Operating profit'),
    ('NET_PROFIT', 'fundamental-metric-catalog-v1', 'INCOME', 'VND', 0, 'ANY', 'Lợi nhuận sau thuế', 'Net profit'),
    ('EPS', 'fundamental-metric-catalog-v1', 'PER_SHARE', 'VND', 2, 'ANY', 'Lãi cơ bản trên cổ phiếu', 'Earnings per share'),
    ('ROE', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 2, 'ANY', 'Tỷ suất sinh lời trên vốn chủ sở hữu', 'Return on equity'),
    ('ROA', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 2, 'ANY', 'Tỷ suất sinh lời trên tổng tài sản', 'Return on assets'),
    ('DEBT_TO_EQUITY', 'fundamental-metric-catalog-v1', 'RATIO', 'RATIO', 4, 'NON_NEGATIVE', 'Nợ trên vốn chủ sở hữu', 'Debt to equity'),
    ('OPERATING_MARGIN', 'fundamental-metric-catalog-v1', 'RATIO', 'PERCENT', 2, 'ANY', 'Biên lợi nhuận hoạt động', 'Operating margin'),
    ('FREE_CASH_FLOW', 'fundamental-metric-catalog-v1', 'CASH_FLOW', 'VND', 0, 'ANY', 'Dòng tiền tự do', 'Free cash flow'),
    ('DIVIDEND_PER_SHARE', 'fundamental-metric-catalog-v1', 'PER_SHARE', 'VND', 0, 'NON_NEGATIVE', 'Cổ tức trên cổ phiếu', 'Dividend per share'),
    ('EQUITY_ATTRIBUTABLE_TO_PARENT', 'fundamental-metric-catalog-v1', 'BALANCE', 'VND', 0, 'ANY', 'Vốn chủ sở hữu công ty mẹ', 'Equity attributable to parent'),
    ('TOTAL_DEBT', 'fundamental-metric-catalog-v1', 'BALANCE', 'VND', 0, 'NON_NEGATIVE', 'Tổng nợ vay', 'Total debt'),
    ('CASH_AND_EQUIVALENTS', 'fundamental-metric-catalog-v1', 'BALANCE', 'VND', 0, 'NON_NEGATIVE', 'Tiền và tương đương tiền', 'Cash and equivalents'),
    ('EBITDA', 'fundamental-metric-catalog-v1', 'INCOME', 'VND', 0, 'ANY', 'Lợi nhuận trước lãi vay, thuế và khấu hao', 'EBITDA');
