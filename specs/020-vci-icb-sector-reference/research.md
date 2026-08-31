# Research: Feature 020 — Sector Reference from VCI ICB

Date: 2026-08-31. Method: owner-authorised read-only probes of vnstock 4.0.7
`Listing(source="vci")` (3 calls), compared against Finvera's universe (1,524 current LISTED
profiles: UPCOM 820, HOSE 405, HNX 299) and the current `KBS_INDUSTRY` coverage in the database.

## R-001 What VCI publishes

| Frame | Rows | Columns | Notes |
|---|---|---|---|
| `symbols_by_industries()` | 8,186 | symbol, organ_name, com_type_code, icb_level, icb_code, icb_name | one row per (symbol, level) for levels 1–3; includes funds (`com_type_code = QU`) |
| `industries_icb()` | 177 | icb_name, en_icb_name, icb_code, level | 11 / 19 / 40 / 107 codes at levels 1–4 — the ICB structure with Vietnamese **and English** labels |
| `symbols_by_exchange()` | 3,586 | symbol, exchange, type, … | type ∈ STOCK 1,751, CW 1,535, BOND 184, FU 91, ETF 20, UNIT_TRUST 4; exchange ∈ HSX 723, HNX 313, UPCOM 818, DELISTED 1,637, BOND 95 |

Level 4 codes exist in the dictionary but are not attached to symbols → level 3 is the finest
usable tier.

## R-002 Coverage of Finvera's universe

| Level | Universe covered | Groups | Groups ≥ 8 | Instruments in groups ≥ 8 | Largest groups |
|---|---|---|---|---|---|
| ICB L2 | 1,524 / 1,524 | 19 | 19 | 1,524 | 2300 Xây dựng & VL 301, 2700 Hàng & DV công nghiệp 249, 3500 Thực phẩm & đồ uống 143, 7500 Tiện ích 140, 8600 Bất động sản 124 |
| **ICB L3** | **1,524 / 1,524** | **37** | **29** | **1,501** | 2350 Xây dựng & VL 301, 8630 BĐS 124, 2770 Vận tải 110, 3570 SX thực phẩm 100, 7570 Điện 88 |
| KBS_INDUSTRY (today) | 697 / 1,524 | 25 | — | — | HOSE/HNX only |

Decision: **level 3**. Level 2 merges banks, insurers and brokers into "8000/8700 Tài chính",
which would make the banking peer set meaningless; level 3 keeps 8350 Ngân hàng / 8530 Bảo hiểm /
8770 Dịch vụ tài chính apart and still leaves 29 groups above the comparability floor.
Live package 2026-08-31: 1,522 equities classified (HSX 405, HNX 299, UPCOM 818), 36 sectors,
7 below the floor (0530, 3740, 3780, 5330, 6530, 6570, 8570) — these stay own-history-only, as
today.

## R-003 Anomalies found and their rules

| Finding | Rule |
|---|---|
| 286 level-3 symbols are not in the exchange list (open-ended funds such as "A+ Fund", names with spaces/`-`) | S-1: classify only `type == STOCK` on HSX/HNX/UPCOM; symbols failing the importer's `[A-Z0-9]{1,32}` rule are skipped and listed in `audit.skippedSymbols` |
| ETF / UNIT_TRUST rows carry 8980 "Quỹ đầu tư" | S-1 excludes them (not peers of operating companies) |
| VNH (Thủy sản Việt Nhật) carries both 3570 and 8980; two funds carry 8980 twice | S-3: lowest code wins, symbol listed in `audit.duplicateClassifications` |
| A level-3 code on a symbol row may be missing from the dictionary | S-2: fall back to the row's `icb_name`, English name null, code listed in `audit.codesWithoutDictionaryName` (0 today) |
| A partial provider response | S-4: refuse to write below 90 % coverage of listed equities |

## R-004 Import path unchanged

`SectorReferenceImportService` validates tool/source/scheme/version, the SHA-256 of
`canonicalPayload`, symbol format and non-empty codes; it creates one `sector_reference` row per
(scheme, schemeVersion, code) and re-points `equity_profile.sector_reference_id`. It has no
hard-coded scheme. `Get-SectorReferencePackage` in `refresh-data.ps1` takes the newest
`sector-reference-*.json`, so the VCI file (`sector-reference-vci-icb-l3-<vnstock>.json`) is picked
up as long as `FINVERA_STOCK_IMPORT_SECTOR_REFERENCE_PACKAGE_PATH` is not pinned to the old file.

## R-005 Downstream effects

- `ValuationService.buildSectorSeries` groups peers by the profile's `sector_reference_id`; the
  next warmup recomputes sector percentiles under the ICB peer sets (new bars arrive daily, so
  no forced warmup is needed; `-ForceWarmup` remains available).
- Screener sector filter and stock-detail sector labels read `display_name_vi`; wording changes
  from KBS labels to ICB labels (e.g. "Bất động sản" stays; "Ngân hàng" stays; KBS "Chứng khoán" →
  ICB 8770 "Dịch vụ tài chính").
- API/FE expose `sectorScheme`/`sectorSchemeVersion` as opaque strings: no code change.
- Historical valuation rows keep their recorded scheme; the revision chain is untouched.
