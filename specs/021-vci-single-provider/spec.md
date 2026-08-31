# Feature 021: Single Provider — VCI for All Market Data

**Status**: Research + ADR-0013 accepted 2026-08-31; implementation in progress  
**Decision record**: docs/adr/0013-standardize-market-data-on-vci.md  
**Related**: ADR-0011/Feature 018 (fundamentals), ADR-0012/Feature 020 (sectors), Q-58 (network retry classes), specs/021 research.md

## Problem

Daily bars, index history, instrument reference and equity profiles still come from KBS while
fundamentals and sectors come from VCI: two adjustment-rounding conventions inside one product,
two shares-outstanding readings, double the provider surface to audit. Owner directive: one
provider, one convention ("chuẩn hóa toàn bộ, data chính xác, đồng bộ").

## Functional requirements

- **FR-001** Daily bars, index history, instrument reference and equity profiles are exported from
  VCI (`source="vci"`), with per-dataset contracts anchored the Feature 011/018 way; upstream
  source recorded as `VNSTOCK_VCI` on every package and row.
- **FR-002** A switched series is re-crawled **in full** for its window — KBS history is never
  extended with VCI increments (two rounding conventions must not meet inside one series).
  Existing KBS rows are superseded by the revision chain where values differ; nothing is deleted.
- **FR-003** Bars carry the honest adjustment label: VCI serves adjusted series →
  `adjustmentStatus = PROVIDER_ADJUSTED` (KBS packages said RAW; the DB keeps both honestly per row).
- **FR-004** The exporter clamps VCI responses to the requested date range (VCI returns a buffer
  before `start` — research R-002).
- **FR-005** Index coverage includes UPCOMINDEX; index codes keep Finvera's names
  (VN_INDEX ↔ VNINDEX, HNX_INDEX ↔ HNXINDEX, UPCOM_INDEX ↔ UPCOMINDEX, VN30, HNX30).
- **FR-006** Profiles read `issue_share` from VCI `Company.overview`; the charter-capital ÷ par
  cross-check stays; a > 1 % gap keeps the count and flags it (Feature 011 R-002 rule unchanged).
- **FR-007** `verify_calcs.py` keeps the dual-provider cross-check with KBS as the independent
  reference; all recomputation sections must be green on VCI bars.
- **FR-008** KBS exporter code stays in the tree as a documented fallback; the refresh script no
  longer crawls KBS for any dataset.
- **FR-009** No `Q-` ids in production code; provenance via contracts and rule ids.

## Success criteria

- SC-1 Exporter tests green on VCI fixtures for every switched dataset.
- SC-2 After the owner's re-crawl: `verify_calcs.py` fully green (technical/valuation/breadth
  recomputed from VCI bars; provider cross-check vs KBS within the 0.5 % adjustment tolerance;
  anchors, sector, shares identity).
- SC-3 The nightly `.\refresh-data.ps1` makes zero KBS calls.

## Out of scope

TCBS intraday overlay (ADR-0003); provider tier upgrades (P2-07); backfilling deeper history than
the configured start (VCI would allow 2018+ — a separate owner decision, noted in research).
