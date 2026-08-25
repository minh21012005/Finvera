alter table breadth_snapshot
    add column calculation_basis varchar(16) not null default 'UNKNOWN'
        check (calculation_basis in ('LIVE', 'EOD', 'UNKNOWN'));
