# ADR-0012: Use VCI's ICB Classification (level 3) as the Sector Reference

**Status**: Accepted — 2026-08-31, under the owner's standing instruction that data must be complete and correct ("data cần chuẩn xác, chính xác và clean"); implemented by Feature 020  
**Date**: 2026-08-31  
**Decision owners**: Finvera maintainer  
**Related**: ADR-0003 (KBS taxonomy accepted as scheme `KBS_INDUSTRY`, Feature 002 R-012 G-04), ADR-0011 (VCI for fundamentals), specs/020-vci-icb-sector-reference, docs/REMEDIATION_PLAN.md Q-38

## Context

`KBS_INDUSTRY` (25 proprietary groups, Vietnamese labels only) classifies 697 of Finvera's
1,524 listed instruments: KBS publishes it for HOSE and HNX only. Every UPCoM equity (820) has no
sector, hence no sector percentile in valuation, no peer table, no sector answer in the AI Analyst
(Q-38). VCI — already the fundamentals provider — publishes the ICB classification for all three
exchanges with an ICB code dictionary in Vietnamese and English (`industries_icb`, 177 codes at
levels 1–4; levels 1–3 attached to symbols). Probe 2026-08-31: level 3 covers 1,524/1,524
instruments, 37 groups in the universe, 29 with ≥ 8 constituents (1,501 instruments).

## Decision

Sector reference scheme becomes **`VCI_ICB_L3`** (ICB level 3, the ICB *sector* tier), produced by
`export_sector_reference_vci.py` under the unchanged `vnstock-sector-reference-v1` package contract
and imported by the unchanged `SectorReferenceImportService`. Universe rule: listed equities only
(type STOCK on HSX/HNX/UPCOM). Level 2 was rejected because it merges banks, insurers and brokers;
level 4 is not attached to symbols. `KBS_INDUSTRY` rows remain in `sector_reference` (never
deleted); profiles are re-pointed by the import backfill. The refresh exports the package on every
run (3 calls) — no owner flag.

## Consequences

- Every listed instrument, UPCoM included, gets a sector; valuation sector percentiles and peer
  tables exist wherever the group has ≥ 8 constituents (29 of 37 groups); 7 small groups remain
  own-history-only, reported in `sectorsBelowComparabilityFloor`.
- Sector labels change wording from KBS to ICB (e.g. KBS "Chứng khoán" → ICB 8770 "Dịch vụ tài
  chính"); English labels become available.
- Historical valuation rows keep the scheme they were computed under (`sectorScheme`,
  `sectorSchemeVersion` are recorded per row); forward rows use ICB after the next warmup.
- The exporter refuses partial provider responses (< 90 % coverage) and schema drift, so a broken
  provider day cannot unclassify the universe.
- Rollback: re-run the KBS exporter and import its package; the importer re-points profiles back.
