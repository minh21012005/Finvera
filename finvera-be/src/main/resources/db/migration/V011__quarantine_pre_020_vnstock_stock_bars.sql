-- Hard-delete current Vnstock/KBS daily bars from before exporter 0.2.0.
-- Previous packages stored prices in Vietnamese board units (thousand VND).
-- Stock daily-bar rows do not persist package toolVersion, so this private/local
-- migration deletes current VNSTOCK_KBS rows and their current derived outputs.
-- The next refresh writes corrected current rows in canonical VND/share units.

create temporary table pre_020_vnstock_stock_bars on commit drop as
select id, ingestion_record_id, instrument_id
from equity_daily_bar
where source = 'VNSTOCK_KBS'
  and is_current = true;

create temporary table pre_020_vnstock_stock_instruments on commit drop as
select distinct instrument_id
from pre_020_vnstock_stock_bars;

create temporary table pre_020_vnstock_technical_results on commit drop as
select id
from technical_indicator_result
where is_current = true
  and instrument_id in (select instrument_id from pre_020_vnstock_stock_instruments);

create temporary table pre_020_vnstock_valuation_assessments on commit drop as
select id
from valuation_assessment
where is_current = true
  and instrument_id in (select instrument_id from pre_020_vnstock_stock_instruments);

create temporary table pre_020_vnstock_strategy_signals on commit drop as
select id
from strategy_signal
where is_current = true
  and instrument_id in (select instrument_id from pre_020_vnstock_stock_instruments);

delete from strategy_signal_risk_factor
where signal_id in (select id from pre_020_vnstock_strategy_signals);

delete from strategy_signal_input
where signal_id in (select id from pre_020_vnstock_strategy_signals)
   or technical_indicator_result_id in (select id from pre_020_vnstock_technical_results)
   or daily_bar_id in (select id from pre_020_vnstock_stock_bars);

delete from strategy_signal
where id in (select id from pre_020_vnstock_strategy_signals);

delete from valuation_metric
where assessment_id in (select id from pre_020_vnstock_valuation_assessments);

delete from valuation_assessment_input
where assessment_id in (select id from pre_020_vnstock_valuation_assessments)
   or daily_bar_id in (select id from pre_020_vnstock_stock_bars);

delete from valuation_assessment
where id in (select id from pre_020_vnstock_valuation_assessments);

delete from technical_indicator_value
where result_id in (select id from pre_020_vnstock_technical_results);

delete from technical_indicator_result
where id in (select id from pre_020_vnstock_technical_results);

delete from source_reconciliation_audit
where tcbs_ingestion_record_id in (select ingestion_record_id from pre_020_vnstock_stock_bars)
   or vnstock_ingestion_record_id in (select ingestion_record_id from pre_020_vnstock_stock_bars);

delete from equity_daily_bar
where id in (select id from pre_020_vnstock_stock_bars);

delete from ingestion_record
where id in (select ingestion_record_id from pre_020_vnstock_stock_bars);
