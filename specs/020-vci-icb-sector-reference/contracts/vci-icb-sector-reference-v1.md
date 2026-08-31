# Contract: vci-icb-sector-reference-v1

Package contract: `vnstock-sector-reference-v1` (unchanged — the importer is shared with the
retired KBS exporter). This document fixes the **scheme semantics** and the exporter rules for
scheme `VCI_ICB_L3`.

## Identity

| Field | Value |
|---|---|
| `contractVersion` | `vnstock-sector-reference-v1` |
| `upstreamSource` | `VNSTOCK_VCI` |
| `scheme` | `VCI_ICB_L3` |
| `schemeVersion` | the pinned vnstock version the frames were captured with (`4.0.7`), auto-detected from the installed package unless `--scheme-version` is given |
| `icbLevel` | `3` (informational) |
| `toolName` / `toolVersion` | `finvera-vnstock-exporter` / `export_sector_reference_vci.TOOL_VERSION` |
| file name | `output/sector-reference-vci-icb-l3-<schemeVersion>.json` |

## Record

`{ symbol, sectorCode, displayNameVi, displayNameEn, canonicalRecord }`

- `symbol`: upper-case, matches `[A-Z0-9]{1,32}`.
- `sectorCode`: the ICB level-3 code as a string, zero-padded as VCI publishes it (`0530`, `8350`).
- `displayNameVi` / `displayNameEn`: from `industries_icb` for that code (`icb_name`, `en_icb_name`).
- `canonicalRecord`: canonical JSON (sorted keys, no spaces, UTF-8) of the other four fields.
- Records sorted by (`sectorCode`, `symbol`); `canonicalPayload = {"records": [...]}` canonical
  JSON; `packageSha256 = sha256(canonicalPayload)`.

## Selection rules

| Rule | Statement |
|---|---|
| **S-1 universe** | A record is emitted only for symbols with `type == STOCK` and `exchange ∈ {HSX, HNX, UPCOM}` in `symbols_by_exchange`. Funds, ETFs, unit trusts, covered warrants, bonds, futures and delisted symbols are excluded. |
| **S-2 names** | Names come from the level-3 rows of `industries_icb`. A code absent from the dictionary uses the `icb_name` on the symbol row, `displayNameEn = null`, and is listed in `audit.codesWithoutDictionaryName`. |
| **S-3 one code per symbol** | If a symbol has several level-3 codes, the lowest code is used and the symbol is listed in `audit.duplicateClassifications` (`{symbol: [codes]}`). |
| **S-4 coverage floor** | If classified equities < 90 % of the listed equities in S-1, the exporter raises `SchemaMismatch` and writes nothing. |
| **S-5 schema** | Missing expected columns, or a dictionary without level-3 rows, raise `SchemaMismatch`. |
| **S-6 comparability floor** | `sectorsBelowComparabilityFloor` lists codes with fewer than 8 constituents in the package; valuation keeps those instruments own-history-only (valuation-v2 unchanged). |

## Audit block (`audit`)

`icbLevel, equitySymbols, classified, byExchange{exchange:{equities, classified}}, unclassifiedEquities[], duplicateClassifications{}, codesWithoutDictionaryName[], skippedSymbols[]` — reviewed by the owner in the exporter output; informational for the importer.

## Import semantics (unchanged, restated)

One `sector_reference` row per (`scheme`, `schemeVersion`, `sectorCode`), created on first sight;
every symbol in the package that has a current equity profile is re-pointed to that row; symbols
without an instrument or profile are reported (`UNKNOWN_INSTRUMENT`, `NO_EQUITY_PROFILE`). Rows of
earlier schemes are never deleted.

## Anchors (tests/fixtures/vci/listing_*.json, capture 2026-08-31)

| Symbol | Exchange | Code | Vi | En |
|---|---|---|---|---|
| A32 | UPCOM | 3760 | Hàng cá nhân | Personal Goods |
| YTC | UPCOM | 4530 | Thiết bị và Dịch vụ Y tế | — |
| ACV | UPCOM | 2770 | Vận tải | — |
| MBB | HSX | 8350 | Ngân hàng | Banks |
| VNM | HSX | 3570 | Sản xuất thực phẩm | Food Producers |
| HPG | HSX | 1750 | Kim loại | — |
| VNH | UPCOM | 3570 (also 8980) | S-3 duplicate | — |
| FUEVN50G (ETF), A+ Fund (fund), XDC (delisted) | — | excluded by S-1 | | |
