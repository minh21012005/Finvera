-- The retired TCBS REST/Ouranos adapter misclassified equity rows as index
-- observations. Preserve its ingestion audit trail, but remove the invalid
-- materialized index facts and any deterministic assessments derived from them.

create temporary table deprecated_tcbs_index_snapshots on commit drop as
select id, ingestion_record_id
from index_snapshot
where source = 'TCBS_IFLASH_MARKET_DATA';

create temporary table deprecated_tcbs_regime_assessments on commit drop as
select distinct input.assessment_id
from regime_assessment_input input
join deprecated_tcbs_index_snapshots snapshot
  on snapshot.id = input.index_snapshot_id;

-- Preserve valid assessment chains if a later assessment superseded one that
-- depended on an invalid legacy snapshot.
update regime_assessment
set supersedes_id = null
where supersedes_id in (select assessment_id from deprecated_tcbs_regime_assessments)
  and id not in (select assessment_id from deprecated_tcbs_regime_assessments);

delete from regime_factor
where assessment_id in (select assessment_id from deprecated_tcbs_regime_assessments);

delete from regime_assessment_input
where assessment_id in (select assessment_id from deprecated_tcbs_regime_assessments);

delete from regime_assessment
where id in (select assessment_id from deprecated_tcbs_regime_assessments);

-- A valid observation may have been appended after a legacy row. Detach that
-- valid chain before deleting only the deprecated materialized snapshots.
update index_snapshot
set supersedes_id = null
where supersedes_id in (select id from deprecated_tcbs_index_snapshots)
  and id not in (select id from deprecated_tcbs_index_snapshots);

delete from index_snapshot
where id in (select id from deprecated_tcbs_index_snapshots);

-- Keep immutable provenance without allowing the retired records to continue
-- claiming acceptance. No raw provider payload is stored in this table.
update ingestion_record
set status = 'REJECTED',
    reason_code = 'DEPRECATED_PROVIDER_INVALID_INDEX'
where id in (select ingestion_record_id from deprecated_tcbs_index_snapshots);
