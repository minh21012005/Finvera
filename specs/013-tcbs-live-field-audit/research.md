# Research: Feature 013 — TCBS Thesis Live-Path Field Audit (P2-01)

Date: 2026-08-31 (code-vs-contract pass; the live-frame anchor probe needs an open trading
session — see R-004). Method: the Feature 011/018 field-by-field audit applied to the one
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

## R-004 Open item — the one thing code reading cannot prove

**Price unit anchor.** The contract and R-015 assert the Thesis stream publishes base VND/share
(no ×1000 board conversion anywhere in the live path). Every audited guard is unit-agnostic, so a
provider-side unit change would flow through silently until the EOD reconciliation flagged it.
Verification requires one open trading session: capture a live `s|6` for VNM and compare
`matchPrice` against the board (~62,000-magnitude, not 62.3), and one `s|8` VN_INDEX level against
the exchange. **Owner action: run the spot-check during any trading session (or ask me while a
session is open); until then the live overlay's unit claim rests on R-015's original evidence.**

## Verdict

The live path conforms to its contract on every statically checkable rule; no defect found. P2-01
closes to `CODE AUDIT DONE — live unit anchor pending a trading session`.
