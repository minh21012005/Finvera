# Feature Specification: Fundamental statements from VCI

**Feature Directory**: `018-vci-fundamentals`
**Created**: 2026-08-31 · **Status**: Clarified — owner approved 2026-08-31 ("impl theo hướng chuẩn nhất, data cần chuẩn xác, chính xác và clean")
**SRS References**: Section 9 (fundamentals), Section 10 (valuation), Section 4 (transparency) · **SRS Requirement IDs**: SRS-FUN-01, SRS-FUN-02, SRS-VAL-01, SRS-TRN-02
**Input**: ADR-0011; Feature 011 research R-009 (Q-57: KBS statement pages carry another period's content — yearly mirrored, quarterly permuted, 20/20 symbols); Feature 017 R-001/R-002.

## Scope Summary *(mandatory)*

Replace the KBS financial-statement and ratio facts with facts built from the
VCI statements (income statement, balance sheet, cash flow; yearly and
quarterly; 8 periods each in the community edition), whose period labels were
verified against audited figures and against each other (FY2025 equals the sum
of its four quarters to the VND for VNM and MBB). Ratios that Finvera needs
(EPS TTM, BVPS, ROE/ROA, margins, leverage, FCF, EBITDA) are **derived in
Finvera from those statements under versioned, published formulas** — no
provider-computed ratio is ingested (the VCI ratio frame returned by vnstock
4.0.7 is malformed; the KBS ratio frames are period-shifted for quarters).
KBS remains the source for prices, indices, listing and profiles, all of which
are anchored and correct.

### In Scope

- Exporter `export_fundamentals_vci.py` (package contract
  `vnstock-fundamentals-v1`, `upstreamSource = VNSTOCK_VCI`), company-type
  aware mapping (non-financial, bank, insurer, broker), per-period share count
  from paid-in/charter capital at par 10,000 VND, derivations with rule ids
  (contract `vci-fundamentals-v1`).
- vnstock 4.0.6 → 4.0.7 in `tools/market-data/provider-poc` (VCI Finance is
  broken in 4.0.6).
- Backend: a newer accepted source supersedes the older source's current report
  for the same period (`SOURCE_SUPERSEDED`), so KBS rows retire as VCI rows land;
  no other calculation changes.
- `export_all_symbols.py` runs the VCI fundamentals pass (8 calls per symbol,
  quarter + year) with the existing checkpoint/quota pacing.
- Anchor test set checked on every export (fixtures captured 2026-08-31).
- Owner re-crawl of the universe, then re-verification (`verify_calcs.py` with
  audited anchors, `history_basis_study.py`, AI e2e).

### Out of Scope

- Provider ratios that Finvera does not derive in v1 (turnovers, P/S, ROCE,
  NIM, CIR, LDR, beta, provider growth rows): they become `MISSING /
  NOT_REPORTED` until a later rule derives them; the screener discloses this.
- Balance-sheet history before 2018 / more than 8 quarters (community limit).
- Any change to `fundamental-summary-v2` / `valuation-v2` formulas.

## User Scenarios & Testing *(mandatory)*

### US-1 — Period truth (P1)
Every stored statement fact belongs to the period it is labelled with.
**Acceptance**: for the anchor symbols, `REVENUE`/`NET_PROFIT` FY2022–FY2025
equal the audited figures; anchor quarter sums hold for VNM/MBB where the
research established the identity. Universe-wide quarter-vs-annual sums are
reported as a data-quality diagnostic because audited/restated annual facts do
not always equal provider quarterly rows.

### US-2 — Contiguous TTM (P1)
`EPS_TTM`, `NET_PROFIT_TTM`, `REVENUE_TTM` are sums over the four most recent
consecutive quarters; growth is TTM vs the TTM four quarters earlier (eight
quarters available).

### US-3 — Banks, insurers, brokers (P1)
MBB, BVH, SSI get `REVENUE` (total operating income / net insurance revenue /
operating revenue), `NET_PROFIT`, `EPS` (annual statement EPS; quarterly via
the trailing-EPS derivation), `BVPS`, `ROE`, with the company type recorded.

### US-4 — Provenance (P1)
Every derived fact carries its rule id as `quality_reason`; every statement fact
carries `sourceReport`; the FE dictionary words each rule id.

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-001 | Exporter maps VCI item ids to Finvera metric codes per company type exactly as contract `vci-fundamentals-v1` lists; unmapped items are never guessed. |
| FR-002 | Per-period shares = (paid-in / charter capital − treasury shares) / 10,000; used for BVPS and trailing EPS derivations; reported as a fact when it disagrees with the profile's shares by > 1 % (reason `SHARES_PERIOD_BASIS`). |
| FR-003 | Derivations (rule ids in the contract): TRAILING_EPS, BVPS, ROE, ROA, ROE_TTM, ROA_TTM, GROSS_MARGIN, NET_MARGIN, OPERATING_MARGIN, DEBT_TO_EQUITY, TOTAL_DEBT, FREE_CASH_FLOW, EBITDA. Each emitted only when every input exists for that period. |
| FR-004 | Package = existing contract `vnstock-fundamentals-v1` so the importer is unchanged except FR-005. |
| FR-005 | Ingestion: if a current report for the same (instrument, periodType, fiscalYear, fiscalQuarter, reportKind) exists from a **different** source, it is superseded (`SOURCE_SUPERSEDED`) and the incoming row is accepted as a restatement; the out-of-order guard applies only within one source. |
| FR-006 | Anchor test: the exporter's test suite replays the captured VCI fixtures and asserts the audited anchors (VNM/MBB/BVH/SSI FY2025 values, quarter sums, share counts). |
| FR-007 | `export_all_symbols.py`: VCI fundamentals pass replaces the KBS pass; `toolVersion` bump forces a full re-export; pacing per Q-39. |

## Success Criteria

| ID | Criterion | Measure |
|---|---|---|
| SC-001 | Anchors hold | exporter tests green; after re-import, `verify_calcs.py` provider check (rewritten to anchors) 0 differences. |
| SC-002 | Quarter-sum diagnostic | `verify_calcs.py` reports FY2025 annual-vs-quarter consistency for `REVENUE` and `NET_PROFIT`, including counts above 1 % and 10 % drift; audited anchors remain the hard period-truth gate. |
| SC-003 | KBS retired | 0 current `fundamental_report` rows with source `VNSTOCK_KBS` after the re-import. |
| SC-004 | Product green | backend/AI/FE suites; AI e2e 10/10; screener/valuation recomputed. |

## Constitution Check

I: formulas published per rule id, decimal arithmetic in the exporter (Decimal,
scale 6) and backend (scale 12). II: provenance on every fact; reduced ratio
set disclosed. III: exporter module boundary; backend change confined to the
ingestion supersession rule. V: spec → research → contract → plan → tasks →
code. VI: fixture-based tests with audited anchors. VIII: existing package
contract and calculators reused.
