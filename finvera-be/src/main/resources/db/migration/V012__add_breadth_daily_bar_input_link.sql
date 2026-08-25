alter table breadth_snapshot_input
    add column daily_bar_id uuid references equity_daily_bar (id);

alter table breadth_snapshot_input
    add constraint ck_breadth_snapshot_input_single_price_source
    check (price_observation_id is null or daily_bar_id is null);

create index ix_breadth_snapshot_input_daily_bar
    on breadth_snapshot_input (daily_bar_id)
    where daily_bar_id is not null;
