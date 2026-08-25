alter table equity_daily_bar
    add column reference_price numeric(20,6) check (reference_price is null or reference_price >= 0);

alter table regime_assessment
    add column assessment_basis varchar(16) not null default 'UNKNOWN'
        check (assessment_basis in ('LIVE', 'EOD', 'UNKNOWN'));
