# Quickstart: Provider Data Expansion

**Feature**: `008-provider-data-expansion` · 2026-08-30

## 1. Re-run the probe (owner, read-only)

```powershell
cd tools\market-data\provider-poc
uv run python probe_balance_sheet.py --symbols VNM,FPT,HPG,MBB --period year --output ..\..\..\tmp\probe-year.json
uv run python probe_balance_sheet.py --symbols VNM,FPT,HPG,MBB --period quarter --output ..\..\..\tmp\probe-quarter.json
```

Expected: `balance_sheet=0` for every symbol (research R-001), `cash_flow=50`
only for `--period year`, `ratio` contains `ebitda_net_revenue`.

## 2. Export fundamentals (quarter + annual)

`export_all_symbols.py` now writes `fundamentals-<sym>-quarter.json` **and**
`fundamentals-<sym>-year.json` for every symbol (tool version `0.4.0` forces a
re-export of packages produced by older versions). Single symbol:

```powershell
cd tools\market-data\vnstock-export
uv run --project ..\provider-poc python export_fundamentals.py --symbol VNM --period quarter
uv run --project ..\provider-poc python export_fundamentals.py --symbol VNM --period year
```

Review the printed samples: `EBITDA` records carry
`derivation=kbs-ebitda-margin-x-net-revenue-v1`; the annual package carries
`FREE_CASH_FLOW` with `derivation=kbs-fcf-ocf-plus-capex-v1`.

## 3. Import

Point `FINVERA_STOCK_IMPORT_FUNDAMENTALS_PACKAGE_PATH` at the output
directory (the scan imports every `fundamentals-*.json`) and start the backend
once with `FINVERA_STOCK_IMPORT_FUNDAMENTALS_ENABLED=true` — the normal
`refresh-data.ps1` stage does this.

## 4. Verify

- `GET /api/v1/stocks/VNM/fundamentals`: `EBITDA_TTM` `DEFINED` once four
  consecutive quarterly `EBITDA` rows exist; `FREE_CASH_FLOW` `DEFINED` from
  the latest annual report with `qualityReason` = rule id.
- `GET /api/v1/stocks/VNM/valuation`: `EV_EBITDA` still `MISSING` with
  `MISSING_EV_INPUTS` (no balance sheet) — expected, disclosed.
- With the TCBS overlay enabled, `GET /api/v1/stocks/VNM`: `price.ceilingPrice`,
  `price.floorPrice`, `price.foreignRoom`, `price.limitState`; without it:
  `null` + `PRICE_LIMITS_UNAVAILABLE`.

## 5. Automated checks

```powershell
cd tools\market-data\vnstock-export; uv run --project ..\provider-poc pytest tests
cd finvera-be; .\mvnw.cmd test
cd finvera-fe; npm run test; npm run lint; npm run build
```
