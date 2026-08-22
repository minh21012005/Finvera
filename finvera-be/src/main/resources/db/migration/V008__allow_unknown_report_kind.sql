-- G-01 (research.md R-012): vnstock's KBS fundamentals source never confirmed whether reports are
-- consolidated or separate, so export_fundamentals.py deliberately emits reportKind=UNKNOWN rather
-- than fabricating one -- the same "state it, don't guess it" treatment audit_status already had.
alter table fundamental_report drop constraint fundamental_report_report_kind_check;
alter table fundamental_report add constraint fundamental_report_report_kind_check
    check (report_kind in ('CONSOLIDATED', 'SEPARATE', 'UNKNOWN'));
