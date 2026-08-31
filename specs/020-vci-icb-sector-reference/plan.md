# Plan: Feature 020 — Sector Reference from VCI ICB

## Approach

Keep the package contract and the importer; replace the *producer*. A new exporter
`export_sector_reference_vci.py` writes a `vnstock-sector-reference-v1` package under scheme
`VCI_ICB_L3` from three VCI listing frames; the refresh script exports it on every run before the
import stage; the verifier gates coverage. No backend, API or FE code changes are needed because
`sectorScheme`/`sectorSchemeVersion` are opaque strings and the importer has no scheme knowledge.

## Components

| Layer | Change |
|---|---|
| `tools/market-data/vnstock-export/export_sector_reference_vci.py` | new: frames → records (S-1…S-5) → package; audit block; coverage floor |
| `tools/market-data/vnstock-export/tests/test_export_sector_reference_vci.py` + `tests/fixtures/vci/listing_*.json` | anchored tests on a trimmed 2026-08-31 capture |
| `refresh-data.ps1` | stage 1 runs the VCI sector exporter (3 calls) after the instrument-reference export; help text and the missing-package message point to the new exporter |
| `tools/verification/verify_calcs.py` | `sector_coverage_check()`: classified share by scheme and venue, ≥ 95 % under one scheme |
| `export_sector_reference.py` (KBS) | kept as documentation of the retired scheme; not run by the refresh |
| docs | ADR-0012, REMEDIATION_PLAN Q-38 → CODE DONE (owner refresh pending) |

## Constitution check

- Provenance: `upstreamSource`, `scheme`, `schemeVersion`, checksum, per-record canonical JSON — unchanged contract.
- Missing ≠ zero: an unclassified equity has no sector row (null), never a default sector.
- Revision chain: old `KBS_INDUSTRY` rows stay; profiles are re-pointed, not rewritten.
- Deterministic: S-3 lowest-code rule, sorted records, coverage floor instead of silent partial packages.
- Module boundaries: only the market-data tooling and the verifier change.

## Risks

- ICB labels differ from KBS labels users saw (wording only; the same instruments group together
  more finely). Accepted — labels come from the exchanges' scheme.
- The owner's `.env` may pin `FINVERA_STOCK_IMPORT_SECTOR_REFERENCE_PACKAGE_PATH` to the KBS file;
  the refresh script now warns when the pinned file is not the VCI package (checked at stage 4).
- VCI listing endpoints could change shape: `SchemaMismatch` stops the export with a clear message
  and the previous package stays in place (refresh fails loudly rather than importing garbage).

## Rollout

1. Owner's next `.\refresh-data.ps1`: stage 1 exports the VCI package; stage 4 imports it;
   the following warmup recomputes sector percentiles.
2. `python tools/verification/verify_calcs.py` → `sector` section ≥ 95 % under `VCI_ICB_L3`.
3. Spot-check in the UI: ACV, A32 (UPCoM) show a sector; MBB peers are banks only.
