# Feature 020: Sector Reference from VCI ICB (all exchanges)

**Status**: Implemented 2026-08-31 (code + package); activates on the owner's next `.\refresh-data.ps1`  
**Closes**: docs/REMEDIATION_PLAN.md Q-38 (UPCoM instruments have no sector basis)  
**Decision record**: docs/adr/0012-use-vci-icb-for-sector-reference.md  
**Related**: Feature 002 (sector reference, research R-012 gate G-04), Feature 012 (valuation-v2 sector basis), Feature 018 (VCI as fundamentals provider)

## Problem

The sector reference Finvera uses for peer comparison (`sector_reference`, scheme
`KBS_INDUSTRY` 4.0.6) classifies **697 of 1,524** listed instruments: KBS publishes
its 25 industry groups for HOSE and HNX only, so every UPCoM equity (820 instruments,
54 % of the universe) has no sector, no sector percentile in valuation, no peer table
and no "same sector" answer in the AI Analyst. The KBS taxonomy is also proprietary
and unlabelled in English (R-012 G-04 accepted it as a stop-gap).

VCI (already Finvera's fundamentals provider, ADR-0011) publishes the **ICB**
classification for every listed symbol on HOSE, HNX and UPCoM, with an
ICB code dictionary in Vietnamese and English.

## User scenarios

1. A user opens an UPCoM stock (e.g. ACV, VEA, MCH, A32): the profile shows its sector,
   the valuation shows a sector percentile when the sector has ≥ 8 constituents, and
   the peer table lists same-sector names — exactly as for a HOSE stock today.
2. A user asks the AI Analyst "ACV so với ngành thì sao?": the analyst has a sector basis to cite.
3. A user opens a HOSE stock: the sector name may change wording (ICB Vietnamese label instead of
   the KBS label) but the stock keeps a sector; percentiles are recomputed against the ICB peer set.
4. Screener sector filter lists ICB level-3 sectors and includes UPCoM constituents.

## Functional requirements

- **FR-001** The sector package MUST classify every listed equity (type STOCK on HSX/HNX/UPCOM)
  that VCI classifies, under scheme `VCI_ICB_L3` (ICB level 3, the ICB *sector* tier).
- **FR-002** Funds, ETFs, covered warrants, bonds and delisted symbols MUST NOT receive a sector.
- **FR-003** Display names MUST come from VCI's ICB dictionary (`industries_icb`), Vietnamese
  and English; a code missing from the dictionary falls back to the name on the symbol row and is
  reported in the package audit.
- **FR-004** A symbol carrying more than one level-3 code MUST be classified deterministically
  (lowest code) and listed in the package audit; never silently.
- **FR-005** The package MUST keep the `vnstock-sector-reference-v1` contract (checksum over the
  canonical payload, one record per symbol, `sectorConstituentCounts`,
  `sectorsBelowComparabilityFloor`), so `SectorReferenceImportService` needs no change.
- **FR-006** The exporter MUST refuse to write a package when fewer than 90 % of listed equities
  are classified (a half-empty provider response must not unclassify the universe).
- **FR-007** The default refresh MUST re-export and import the package on every run (3 provider
  calls); no owner flag.
- **FR-008** `verify_calcs.py` MUST report sector coverage: share of current LISTED profiles with a
  sector, by scheme and venue; the gate is ≥ 95 % under a single scheme.
- **FR-009** Nothing is deleted: `KBS_INDUSTRY` sector rows stay in `sector_reference`; profiles
  are re-pointed to the VCI rows by the existing import backfill.

## Success criteria

- **SC-1** After the next refresh: ≥ 1,500 of 1,524 LISTED profiles carry a `VCI_ICB_L3` sector
  (measured 2026-08-31 from the live package: 1,522 / 1,522 equities classified, HSX 405, HNX 299,
  UPCOM 818).
- **SC-2** Valuation sector basis available for every instrument whose sector has ≥ 8 constituents:
  29 of 37 level-3 groups in the universe, 1,501 instruments (measured from the VCI listing frames
  restricted to Finvera's universe).
- **SC-3** Exporter unit tests anchored on a trimmed 2026-08-31 capture (A32 → 3760 Personal Goods,
  MBB → 8350 Banks, VNM → 3570 Food Producers, VNH duplicate → 3570, ETF/fund/delisted excluded).
- **SC-4** `verify_calcs.py` sector-coverage check passes after the refresh.

## Out of scope

- ICB level 4 (sub-sector): VCI does not attach it to symbols in vnstock 4.0.7.
- Re-deriving historical valuation percentiles under the new scheme: valuation rows are recomputed
  forward by the daily warmup (new bars each day); prior rows keep their recorded scheme/version.
- Changing the comparability floor (8) or the valuation-v2 sector rules.

## Assumptions

- ICB level 3 is the right peer granularity for a 1,524-instrument market: level 2 (19 groups)
  merges banks with insurers and brokers; level 3 separates them (8350 / 8530 / 8770).
- VCI's ICB assignment is the exchanges' own ICB assignment republished (the HOSE/HNX ICB
  scheme); the exporter records VCI as the upstream source and does not claim more.
