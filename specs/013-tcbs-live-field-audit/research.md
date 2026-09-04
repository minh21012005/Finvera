# Research: Feature 013 — TCBS Thesis Live-Path Field Audit (P2-01)

Dates: 2026-08-31 (code-vs-contract pass, R-001…R-003) and 2026-09-04 (live capture during an
open session, R-004…R-011). Method: the Feature 011/018 field-by-field audit applied to the one
ingestion path not yet re-audited: contract `tcbs-thesis-private-live-v1`
(specs/001 contracts/tcbs-thesis-live-overlay.md) against `TcbsThesisFrameMapper`,
`TcbsLiveEquityQuoteService`, `TcbsLiveMarketIngestionService`, `TcbsStreamStockQuoteProvider`
and the repository exclusions.

## R-001 Frame mapper vs contract — conforming on every rule checked

| Contract rule | Implementation | Verdict |
|---|---|---|
| Envelope `s\|{8,4,6}\|JSON`, `d\|…` control frames | `map()` switch; unknown data codes become `UnsupportedDataFrame`, never guessed | ✓ |
| Index map 1→VN_INDEX, 2→VN30, 3→HNX_INDEX, 5→UPCOM_INDEX; others rejected | exact `switch`, `IllegalArgumentException` otherwise | ✓ |
| `reference = index − change`, both BigDecimal, derived reference must be positive | implemented with signum guards | ✓ |
| Values may be JSON strings or numbers; missing/invalid never become zero | `decimal()` accepts both; absent optional fields stay `null`; invalid → exception | ✓ |
| Breadth counters jointly present or absent | trio null-check enforces "jointly present" | ✓ |
| Symbol shape | `[A-Z0-9]{1,10}` upper-cased | ✓ |
| Non-negative guards (volume, value, prices) | `validateNonNegative` on every optional numeric | ✓ |

## R-002 Session semantics, ordering, dedupe — conforming

- `observedAt` = backend receive instant; trading date via the versioned Vietnam market clock
  (`resolveSession(venue, receivedAt)`), per the contract's "no provider timestamp" rule.
- Q-24 rule present: session facts (open/high/low/volume) reset when the stored facts belong to a
  different trading date; open = first match of the session, high/low fold `max`/`min`.
- Reference/trade pairing: a reference from another trading date is not used; a quote publishes
  only with a session-valid reference (Q-25's toFact rule sits downstream in StockIngestionService).
- Dedupe by (source, dataset, symbol, date, observedAt, payload hash) before accept; per-source
  OUT_OF_ORDER guard.

## R-003 Consolidation and legacy exclusion — conforming

- Consolidated breadth sums **index numbers 1, 3, 5 only** (`TcbsLiveMarketIngestionService`,
  `List.of(1, 3, 5)`); VN30 never contributes, per the contract's overlap rule.
- Retired `TCBS_IFLASH_MARKET_DATA` rows are excluded in the overview repository queries
  (`source <> 'TCBS_IFLASH_MARKET_DATA'`), matching the contract's legacy-exclusion clause;
  `TCBS_IFLASH_THESIS` and Vnstock sources untouched.
- Bar ingestion rejects the deprecated TCBS stock source outright
  (`DEPRECATED_PROVIDER_INVALID_PRICE_UNIT`) — live frames can never rewrite historical bars.
- Logs: mapper and client carry reason codes only; no token/payload logging found in the audited
  classes (contract's secret-handling clause).

## R-004 Price unit anchor — RESOLVED 2026-09-04, base VND/share confirmed

The contract and R-015 assert the Thesis stream publishes base VND/share (no ×1000 board
conversion anywhere in the live path). Every audited guard is unit-agnostic, so a provider-side
unit change would flow through silently. Settled by an owner-run capture during the morning
session of 2026-09-04 (11:06:54–11:08:24 ICT, 90 s, authenticated, 0 disconnects, 55 `s|6` +
52 `s|8` frames; evidence `tools/verification/out/tcbs_live_capture_2026-09-04T1106.json`).

Anchored against **VCI** — an independent provider whose board unit is thousand VND and whose
×1000 conversion is already pinned by `export_daily_bars.normalize_board_price` — sampled within
the same two minutes:

| Instrument | Thesis field | Thesis value | VCI (base VND / points) | ratio |
|---|---|---|---|---|
| VNM | `s\|6.matchPrice` | 61900 | 61900 | 1.000000 |
| MBB | `s\|6.matchPrice` | 20650 | 20650 | 1.000000 |
| indexNumber 1 | `s\|8.index` | 1846.52 | VNINDEX 1846.55 | 0.999984 |
| indexNumber 2 | `s\|8.index` | 1976.48 | VN30 1976.48 | 1.000000 |
| indexNumber 3 | `s\|8.index` | 282.04 | HNXINDEX 282.04 | 1.000000 |
| indexNumber 5 | `s\|8.index` | 127.57 | UPCOMINDEX 127.57 | 1.000000 |

Equity prices are base VND/share and index levels are points, as claimed. The residual 1.6e-5 on
VN_INDEX is intraday drift between the two samples, not a unit difference — a unit defect would
show as ~1000× or ~0.001×, which no drift can produce. The capture also **proves the index-number
mapping from data** (1→VN_INDEX, 2→VN30, 3→HNX_INDEX, 5→UPCOM_INDEX); it previously rested on
provider documentation alone.

A second, provider-internal arithmetic anchor agrees: `totalValue / totalVolume` is a plausible
session VWAP against `matchPrice` in the same unit — VNM 50,127,920,000 / 813,600 = 61,613 VND vs
match 61,900; MBB 61,957,425,000 / 2,993,800 = 20,695 VND vs match 20,650. So `totalValue` is base
VND and `totalVolume` is **shares, not lots** (a lots reading would put the quotient at ~6.2 M).

ACV produced no `s|6` in the window — a thin symbol with no match for 90 s, which is the expected
"no trade, no frame" behavior and not a gap.

## R-005 `s|4` is never replayed on a mid-session subscription

Zero `s|4` frames arrived in 90 s for three symbols subscribed at 11:07, mid-session. The provider
pushes the reference/limit frame on its own schedule (session open) and does **not** replay it for
a subscription that starts later. The contract's "a quote becomes publishable only after both a
positive reference price and a non-negative matched price are known" therefore describes a
condition that `s|4` alone would rarely satisfy for on-demand symbols.

No defect: `TcbsLiveEquityQuoteService` already carries the two fallbacks that actually do the
work — the derived reference `matchPrice − change` (`change` was present in 54 of 55 `s|6` frames)
and the `tickerCommons` REST snapshot (`fetchSnapshotIfMissing`, also the only source of
ceiling/floor/foreign room for such a symbol). The contract is silent on both, so it under-describes
the mechanism the live quote depends on. Contract amendment, not a code change.

## R-006 Mixed JSON typing within a single frame

`s|6` sends `matchPrice`, `matchQtty`, `totalVolume`, `totalValue` as JSON **strings** and `change`,
`changePercent` as JSON **numbers**; `s|8` sends every numeric as a number. The mapper's
`decimal()` accepts both, so nothing breaks — but this is live proof that the contract's "provider
values may be JSON strings or numbers" is load-bearing and must never be "tidied up" into one type.
`matchQtty` values are multiples of 100 with a minimum of 100 (shares, not lots) and are not
consumed by Finvera.

## R-007 `changePercent` is already a percentage

VNM: `change` 700 (VND), `changePercent` 1.1437908496732025 — that is 700/61,200 expressed as
percent, not a fraction. Index frames agree (VN30 `change` 14.58 on ~1961 → `changePercent` 0.74).

No ×100 risk anywhere: `MarketIngestionService` **recomputes** the percentage from level and
reference (`absoluteChange × 100 / reference`) and discards the provider's value, and
`TcbsLiveEquityQuoteService` never reads `percentageChange` at all. Worth stating in the contract
so nobody later "saves work" by trusting the provider field as a fraction.

## R-008 Undocumented server control frames

The server sends `d|33|15` once immediately after the socket opens, and `d|34|…` roughly every
5 s (18 times in 90 s) — a server-side heartbeat. Neither appears in the contract.
`TcbsThesisFrameMapper` maps every `d|`-prefixed frame to `ControlFrame`, treating only `d|0|` as
the authentication response, so both are ignored safely. Documentation gap only.

## R-009 The provider closes the connection on an RFC WebSocket ping

The first capture attempt died with `1002 (protocol error)` **after** a successful authentication.
The cause was the Python client's automatic RFC ping frames (`websockets` defaults to one every
20 s); with `ping_interval=None` — sending only the application-level `d|p|||` heartbeat — the same
run completed 90 s with zero disconnects.

The production Java client is already correct by construction (`java.net.http.WebSocket` never
sends unsolicited pings), so this is not a defect — but the contract's sentence "RFC WebSocket ping
is not used as a substitute" reads as a Finvera style choice when it is in fact a provider
requirement whose violation kills the connection. Restated as a provider constraint.

## R-010 `tickerCommons` REST unit — RESOLVED 2026-09-04, base VND

`fetchSnapshotIfMissing` stores `refPrice`, `ceilPrice`, `floorPrice`, `open`, `high`, `low` and
`room` from `/tartarus/v1/tickerCommons` with **no unit conversion**, and per R-005 it is the only
source of ceiling/floor for a mid-session symbol. Anchored on the same token during the closing
session of 2026-09-04 (14:49 ICT; evidence
`tools/verification/out/tcbs_live_capture_2026-09-04T1444.json`): all 21 price fields across three
symbols compare SAME_UNIT against VCI, and the REST `matchPrice` equals the stream's to the VND
(VNM 61,900; MBB 20,550; ACV 41,000).

A second anchor comes free and owes nothing to any provider label: `ceilPrice`/`floorPrice` sit
exactly on the venue's price-limit band around `refPrice`, rounded to the tick —

| Symbol | Venue band | refPrice | ceilPrice | floorPrice |
|---|---|---|---|---|
| VNM | HOSE ±7 % | 61,200 | 65,400 (+6.86 %) | 57,000 (−6.86 %) |
| MBB | HOSE ±7 % | 20,600 | 22,000 (+6.80 %) | 19,200 (−6.80 %) |
| ACV | UPCoM ±15 % | 41,300 | 47,400 (+14.77 %) | 35,200 (−14.77 %) |

That identity can only hold if reference, ceiling and floor share one unit, and the band widths
identify the venue correctly (ACV on UPCoM). `room` is a share count (VNM 1,048,052,955), not a
percentage; `totalVal`/`totalVol` are base VND and shares. `tickerCommons` also carries an
`indexNumber` per symbol that agrees with the `s|8` numbering (VNM/MBB 1, ACV 5).

Fields Finvera does not read: the three-level bid/offer ladder, `avg`, `buyForeignQtty`,
`sellForeignQtty`, `matchQtty`, `change`, `changePercent`, `indexNumber`. `matchQtty` arrives here
as a JSON **number** while the stream sends it as a **string** — the same field, two types, two
transports (R-006 again).

## R-011 Live-vs-EOD reconciliation — 2/3 exact, the third explained by the UPCoM calendar

Closing capture 14:44:05–14:49:05 ICT (authenticated, 0 disconnects, 468 `s|6` + 58 `s|8`), last
matched price per symbol against the independent VCI EOD close for the same date:

| Symbol | Live last match | VCI EOD close | Diff | Session volume (live) | (VCI EOD) |
|---|---|---|---|---|---|
| VNM | 61,900 | 61,900 | 0.0000 % | 1,906,600 | 1,906,600 |
| MBB | 20,550 | 20,550 | 0.0000 % | 8,857,700 | 8,857,700 |
| ACV | 41,000 | 40,900 | +0.2445 % | 263,200 | 263,800 |

VNM and MBB reconcile exactly on **both** price and cumulative volume — the strongest form of the
unit claim, since volume agreement rules out a lots/shares confusion at the same time.

ACV is not a discrepancy: it trades on UPCoM, whose continuous session runs to 15:00 while the HOSE
ATC print lands at 14:45. The capture ended at 14:49, and the snapshot's own `totalVol` (263,200)
is 600 shares short of the EOD figure (263,800) — trading simply continued after we stopped
listening. The lesson for any future run: **a capture that ends at 14:49 holds HOSE's close but not
UPCoM's**; reconcile UPCoM names from a capture ending after 15:00, or from the EOD import.

## Verdict

The live path conforms to its contract on every statically checkable rule (R-001…R-003), and every
unit the contract asserts is now proven against an independent provider and against arithmetic
identities that owe nothing to provider labels: the stream (R-004), the REST snapshot (R-010) and
the session's own close and volume (R-011). **No defect was found in the code** — the audit changed
no production line.

What the sessions exposed is a **contract that under-described the provider**: six behaviors the
implementation already handled correctly but that nobody had written down (R-005…R-010). Those are
now contract clauses plus a pinned fixture, so the next provider change fails a test instead of
reaching the database. P2-01 closes.

## Evidence and reproducibility

The raw capture (`tools/verification/out/tcbs_live_capture_2026-09-04T1106.json`, 47 KB) stays
local: `tools/verification/.gitignore` excludes `out/`, as it does for every other private
market-data artifact. It was scanned before use — it contains no API key, no JWT and no
`Authorization` header, only market data and frame envelopes (SEC-001).

The committed, durable part of the evidence is the pinned fixture
`finvera-be/src/test/resources/fixtures/market/tcbs/tcbs-frame-fixture.json` (8 verbatim frames)
and its test. Re-running the audit is one command during any open session:
`python tools/verification/tcbs_live_capture.py --symbols VNM,MBB,ACV --seconds 90`.

**Residual risk.** A capture proves the unit for the session it sampled. What carries the claim
forward is the fixture test (a 1000× change fails the build), the ingestion guard on
`totalValue / totalVolume` vs `matchPrice` (rejects a ratio outside 0.02–50×), and the EOD
reconciliation. A provider that changed the unit of *every* field at once, consistently, would
still need the EOD reconciliation to catch it — which is why DATA-007 is part of this feature.
