-- Feature 023 (contract valuation-v3): the own-history percentile of a flow metric ranks a
-- fiscal-year-basis comparison value against a fiscal-year-basis series; the value that was
-- ranked and the basis it was ranked on are stored so the rank is reproducible and disclosed.
--
-- The contract invariant "percentile present => basis and comparison value present" is NOT a
-- SQL check on purpose: valuation-v2 rows carry percentiles with both columns null and must stay
-- readable, and the rule version that would scope such a check lives on valuation_assessment.
-- The invariant is enforced where v3 rows are constructed (ValuationMetricEntity).
alter table valuation_metric add column own_history_basis varchar(32);
alter table valuation_metric add column own_history_comparison_value numeric(24,12);
alter table valuation_metric add constraint valuation_metric_own_history_basis_check
    check (own_history_basis is null or own_history_basis in ('FISCAL_YEAR', 'LATEST_REPORT'));
