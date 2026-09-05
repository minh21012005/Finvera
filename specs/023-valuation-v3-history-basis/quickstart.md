# Quickstart: Feature 023 — verify `valuation-v3`

## 1. Unit and integration (no data needed)

```powershell
cd D:\Finvera\finvera-be
mvn -Dtest=ValuationV1Tests,FundamentalSummaryCalculatorTests,ValuationServiceTests,ToolDelegateServiceTests test
mvn test                      # full suite; Docker Desktop must be up (Testcontainers)
cd ..\finvera-fe
npm run lint; npm run build; npx vitest run
```

## 2. Recompute the universe (owner)

```powershell
cd D:\Finvera
.\refresh-data.ps1            # stage 7 writes valuation-v3 rows for every LISTED instrument
```

## 3. Independent recomputation

```powershell
python tools\verification\verify_calcs.py          # valuation section must be green on v3 rows
python tools\verification\history_basis_study.py   # gap now informational; ranked value on FY basis
```

## 4. Spot-check one stock (VNM, 2026-08-28 figures)

```sql
select m.metric_code, m.value, m.own_history_comparison_value, m.own_history_basis, m.own_history_percentile
from valuation_assessment a join market_instrument i on i.id = a.instrument_id
join valuation_metric m on m.assessment_id = a.id
where i.symbol = 'VNM' and a.is_current and a.rule_version = 'valuation-v3'
order by a.as_of_trading_date desc, m.metric_code;
```

Expected shape: `PE.value ≈ 13.18` (62,300 / TTM 4,728), `PE.own_history_comparison_value ≈ 15.47`
(62,300 / FY2025 4,028), basis `FISCAL_YEAR`; `PB` basis `LATEST_REPORT` with comparison = value.

## 5. Classification drift after the first v3 warmup (record in research.md)

```sql
with v2 as (select instrument_id, classification from valuation_assessment where rule_version='valuation-v2' and is_current),
     v3 as (select instrument_id, classification from valuation_assessment where rule_version='valuation-v3' and is_current)
select count(*) total, count(*) filter (where v2.classification is distinct from v3.classification) changed
from v3 join v2 using (instrument_id);
```

## 6. UI

Open a stock with quarterly EPS (VNM): the P/E row shows "Vị thế lịch sử: Phân vị … — theo P/E năm
…"; a bank (MBB) shows the same wording with comparison = headline. Reason chips show
"Phân vị lịch sử tính trên số liệu năm tài chính". Hover/`title` still carries the raw code.
