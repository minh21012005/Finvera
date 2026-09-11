-- Feature 027 calibration: DEBT_TO_EQUITY's canonical unit is percent points.
-- KBS already emits percent points; VCI-derived rows before exporter 1.3.0
-- stored the raw debt/equity ratio and therefore need a one-time x100 repair.
-- The derivation predicate makes this safe if a database already contains a
-- post-1.3 VCI row; Flyway still records this migration as one atomic step.
update fundamental_report_metric metric
   set value = metric.value * 100,
       quality_reason = 'vci-debt-to-equity-percent-v2'
  from fundamental_report report
 where metric.report_id = report.id
   and metric.metric_code = 'DEBT_TO_EQUITY'
   and metric.applicability = 'DEFINED'
   and report.source = 'VNSTOCK_VCI'
   and metric.quality_reason = 'vci-debt-to-equity-v1';

-- Summary metric DEBT_TO_EQUITY is copied from CONTRIBUTING_REPORT_0 (newest
-- report). Repair materialized summaries that were built from a VCI report.
update fundamental_summary_metric metric
   set value = metric.value * 100,
       quality_reason = 'vci-debt-to-equity-percent-v2'
 where metric.metric_code = 'DEBT_TO_EQUITY'
   and metric.applicability = 'DEFINED'
   and exists (
       select 1
         from fundamental_summary_input input
         join fundamental_report report on report.id = input.report_id
        where input.summary_id = metric.summary_id
          and input.input_role = 'CONTRIBUTING_REPORT_0'
          and report.source = 'VNSTOCK_VCI'
          and metric.quality_reason = 'vci-debt-to-equity-v1'
   );
