create temporary table tmp_invalid_live_regime_assessment_ids (
    id uuid primary key
) on commit drop;

insert into tmp_invalid_live_regime_assessment_ids (id)
select distinct assessment.id
from regime_assessment assessment
join regime_assessment_input input on input.assessment_id = assessment.id
join breadth_snapshot breadth on breadth.id = input.breadth_snapshot_id
where assessment.assessment_basis = 'LIVE'
  and breadth.calculation_basis = 'EOD';

delete from regime_factor factor
where factor.assessment_id in (select id from tmp_invalid_live_regime_assessment_ids);

delete from regime_assessment_input input
where input.assessment_id in (select id from tmp_invalid_live_regime_assessment_ids);

delete from regime_assessment assessment
where assessment.id in (select id from tmp_invalid_live_regime_assessment_ids);
