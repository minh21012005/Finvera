-- ADR-0013 (Feature 021): VCI serves corporate-action-adjusted daily series; the honest label is
-- PROVIDER_ADJUSTED. The stock-history import contract accepted the value since Feature 011, but
-- these two checks (V003) predate it and only allowed ADJUSTED/RAW/NOT_APPLICABLE/UNKNOWN.
alter table equity_daily_bar drop constraint if exists equity_daily_bar_adjustment_status_check;
alter table equity_daily_bar add constraint equity_daily_bar_adjustment_status_check
    check (adjustment_status in ('ADJUSTED', 'PROVIDER_ADJUSTED', 'RAW', 'NOT_APPLICABLE', 'UNKNOWN'));

alter table technical_indicator_result drop constraint if exists technical_indicator_result_adjustment_status_check;
alter table technical_indicator_result add constraint technical_indicator_result_adjustment_status_check
    check (adjustment_status in ('ADJUSTED', 'PROVIDER_ADJUSTED', 'RAW', 'NOT_APPLICABLE', 'UNKNOWN'));
