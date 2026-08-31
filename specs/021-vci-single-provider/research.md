# Research: Feature 021 — Standardize All Market Data on VCI

Date: 2026-08-31. Trigger: owner directive ("vci là provider mặc định, chuẩn nhất … chuẩn hóa
toàn bộ … research, đánh giá và kiểm chứng, sau đó ta sẽ tiến hành refactor"). Method:
owner-authorized read-only probes of vnstock 4.0.7 (≈ 40 calls), cross-checks against the
KBS-sourced database, audited anchors from Features 011/018, and a code/DB survey of every
field each dataset actually consumes.

## R-001 Current provider map vs the VCI equivalent

| Dataset | Today | VCI equivalent (probed) | Verdict |
|---|---|---|---|
| Fundamentals (statements + derived ratios) | **VCI** (Feature 018/019) | — | done |
| Sector reference | **VCI ICB L3** (Feature 020) | — | done |
| Daily bars | KBS `Quote.history` | `Quote(source="vci").history`: OHLCV, thousand-VND, ~8-year rolling depth (VNM/VNINDEX first row 2018-08-30), UPCoM equities served (A32 from 2022-10) | equivalent+, deeper history |
| Index history | KBS via `export_history --market-overview` | VCI serves VNINDEX, HNXINDEX, VN30, HNX30 and **UPCOMINDEX** (KBS naming maps 1:1; levels match to 0.01) | equivalent |
| Instrument reference (listing) | KBS `symbols_by_exchange` | VCI `symbols_by_exchange`: 3,586 rows, type + exchange incl. UPCOM/DELISTED (already captured for Feature 020) | equivalent |
| Equity profile | KBS `Company.overview` (`outstanding_shares`, free-float, charter-capital fallback) | VCI `Company.overview`: `issue_share` (VNM 2,089,955,445; A32 6,800,000), `free_float`/`free_float_percentage`, `foreigner_percentage`, `state_percentage`, `listing_date`, `com_type_code`, organ names, company profile text | equivalent+ (adds foreign room / state share) |
| Live overlay (intraday) | TCBS iFlash (ADR-0003) | out of scope — not a vnstock dataset | unchanged |

## R-002 Price cross-check (the decisive correctness question)

- Last 6 sessions, 24 symbols + VN30: **0 mismatches**, VCI close ×1000 = stored KBS close to the tick
  (verify_calcs `provider` section, 2026-08-31).
- Full year (253 sessions × 5 symbols incl. the worst case MSB): all differences ≤ **0.22 %**,
  concentrated in pre-corporate-action history — both providers serve **adjusted** series and round
  the adjustment to tick size differently (VNM 207/253 sessions differ ≤ 0.18 %; FPT 25/253 ≤ 0.014 %;
  MBB 8/253). The last sessions always agree exactly. Neither side is "wrong"; they are different
  rounding conventions of the same adjustment.
- Consequence of switching: on the first VCI bar crawl, roughly this share of historical rows will be
  superseded by values a few ticks away (revision chains keep both). Indicators recompute in the
  warmup; valuation own-history percentile shifts are bounded by the same ≤ 0.25 %.
- VCI `history()` returns a few sessions **before** the requested start (buffer, seen +15 sessions);
  the exporter must filter to the requested range (KBS exporter already clamps by date).

## R-003 Field coverage — nothing consumed today is lost

- `equity_daily_bar.value_vnd` is **computed** by our exporter (close × volume), not a provider field
  → identical under VCI. `adjusted_close`/`adjustment_factor` are empty today (status RAW for all
  872,262 rows); VCI's series being adjusted means the status label should become the honest
  `PROVIDER_ADJUSTED` (contract note), not RAW.
- `index_snapshot.matched_value_vnd` is NULL for all 3,646 CLOSED rows today (KBS gives volume only);
  VCI also gives volume → no regression, UPCOMINDEX levels become available from the provider that
  also serves its constituents.
- Profile: VCI `issue_share` replaces KBS `outstanding_shares`; the KBS charter-capital÷par fallback
  (Feature 011 R-002) can stay as a cross-check. VNM: VCI 2,089,955,445 vs profile 2,089,955,092
  (0.00002 % — share buyback timing), A32 exact.

## R-004 Risks of a single provider

- **Quota concentration**: everything rides `trading.vietcap.com.vn`. Observed today: one read
  timeout during a heavy probe while the owner's fundamentals crawl was also running. Mitigation:
  Q-58 network-retry classes (already shipped), the same pacing budget, and the KBS exporters kept
  intact as documented fallbacks (a one-line source switch back).
- **Migration churn**: a full bar re-crawl (1,522 × 2 calls ≈ 1.5–2 h) + supersession of the
  sub-0.25 % rows roughly doubles `equity_daily_bar` row count (revision chain, nothing deleted);
  warmups recompute indicators/valuations once.
- **Convention continuity**: mixing KBS history with VCI increments day-by-day would embed the two
  rounding conventions inside one series — the refactor must re-crawl the whole window per symbol
  (full refresh semantics), not append.

## R-005 Recommendation

Adopt VCI for daily bars, index history, instrument reference and equity profile (ADR-0013),
executed dataset-by-dataset with the Feature 011/018 method: contract per dataset, anchored
exporter tests, verifier gates (the KBS↔VCI cross-check in `verify_calcs.py` simply flips its
reference direction), one full re-crawl, then the nightly refresh is single-provider.
TCBS live overlay unchanged. KBS code paths retained as fallback, no longer crawled.

## R-006 VCI no-trade filler bars (found by the post-refactor audit, 2026-08-31)

For thinly-traded symbols VCI serves a bar for **every** session: A32 2025-01→2026-08 has 399 bars
where KBS had 168. Of the 231 extras, 126 have volume 0 and flat OHLC equal to the previous close
— carry-forward fillers for sessions where nothing traded, all falling on real trading days (none
on weekends/holidays); the other 105 are flat bars with real volume (1, 6, 109 shares…): genuine
UPCoM odd-lot trades below KBS's radar. Liquid symbols (VNM) have no fillers.

**Rule (export_daily_bars 1.0.0, tested):** a bar with volume ≤ 0 or no volume is dropped — no
trade happened, and importing it would distort breadth (unchanged counts), RSI/ATR windows and
AVG_VOLUME20. A bar with any reported volume is kept: a 1-share trade is a real trade. The DB holds
0 zero-volume bars today, so the rule keeps the new series semantically identical to the audited
KBS baseline.

## R-007 Delisting propagation (found by the post-refactor audit, 2026-08-31)

DAN and DVT are `DELISTED` in VCI's listing but their profiles said `LISTED` (a pre-existing gap:
nothing ever wrote a delisted status). Fix: `export_equity_profile` 1.0.0 also emits
`listingStatus = DELISTED` records for provider-delisted stocks (no overview calls; symbols
Finvera never listed come back `UNKNOWN_INSTRUMENT`); `EquityProfileImportService` now revises on
a listing-status change and carries the last known share count (and its quality reason) forward —
an absent value means unknown, never removed.

## R-008 Post-refactor audit evidence (2026-08-31)

Live packages built into a scratch dir and compared against the database: VNM bars 65/65 sessions,
last 5 closes exact, 0 differences above the 0.5 % adjustment-rounding band, `valueVnd` exact,
range clamped; A32 same after the R-006 rule; instrument reference 1,522 symbols, venues map
HSX→HOSE, the 2 DB-only symbols are exactly the delisted DAN/DVT (R-007); market-overview package
carries all four index codes with levels equal to the stored CLOSED snapshots (VN30 2026-08-27 =
1979.23); profile shares verified against market-cap for VNM/MBB/SSI/BVH (A32 flagged UNVERIFIED —
provider's own market cap is 4 % stale, count kept and disclosed); fundamentals 1.1.0 live run for
VNM matches audited FY2025 REVENUE/NET_PROFIT/EPS to the đồng with all 16 derived ratios in
plausible ranges. Static sweep: no `source="kbs"` call remains outside the two retired fallback
exporters; every importer validation (contract version, source prefix, venue set, adjustment
status, checksum) accepts the new packages.
