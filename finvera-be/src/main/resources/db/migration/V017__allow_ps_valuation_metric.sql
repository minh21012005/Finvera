-- Feature 022 (valuation-v2 addendum): PS joins the valuation metric list as an informational
-- metric (weight 0). The V003 check predates it.
alter table valuation_metric drop constraint if exists valuation_metric_metric_code_check;
alter table valuation_metric add constraint valuation_metric_metric_code_check
    check (metric_code in ('PE', 'PB', 'EV_EBITDA', 'PEG', 'DIVIDEND_YIELD', 'PS'));
