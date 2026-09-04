# Independent recomputation (owner tool, read-only)

`verify_calcs.py` recomputes Finvera's stored results from raw database facts with
plain textbook formulas — Wilder RSI/ATR, SMA-seeded EMA MACD, population-σ Bollinger,
TTM sums / YoY growth (`fundamental-summary-v2` rules), PE/PB/PEG, mid-rank percentile
over the rebuilt own-history series, advance/decline breadth — and compares them with
`technical_indicator_value`, `fundamental_summary_metric`, `valuation_metric`,
`breadth_snapshot`. It is deliberately independent of both the Java code and the
contract text, so a disagreement points at one of: the contract, the code, or the data.

Run (reads `finvera-be/.env` for the local database; never prints credentials):

```powershell
python tools/verification/verify_calcs.py
```

First run 2026-08-31: 607 checks, provider-vs-DB 74/74 exact; every genuine
difference traced to the technical warmup skipping revised/backfilled bars (Q-45,
fixed) and the undisclosed provider-trailing-EPS fallback (now `PROVIDER_TRAILING_EPS`).

## Lesson from Q-57 (2026-08-31)

`verify_calcs.py`'s provider re-fetch compared facts **by the provider's own period
labels**, so it could not see that KBS's yearly income/cash-flow columns were
mirrored (FY2022 under "2025-Năm"). Label-trusting checks prove transport, not
truth: anchor at least one figure per dataset to an audited number (VNM FY2022 net
revenue 59,956,247,197,000 VND) — see contract `kbs-yearly-statement-orientation-v1`.
`history_basis_study.py` (Feature 017) is the read-only tool that exposed it.
`verify_calcs.py` now ends with an `anchors` section (audited FY figures, anchor
share counts derived from `EQUITY_ATTRIBUTABLE_TO_PARENT / BVPS`, no current
`VNSTOCK_KBS` rows). FY2025 annual-vs-quarter sums are printed as diagnostics,
not hard failures, because VCI annual statements can be audited/restated while
quarterly rows remain preliminary or reclassified. Run it after every
fundamentals re-crawl; hard anchors must be 0 diffs.
`analyst_e2e.py` (Feature 015) exercises the AI Analyst on the live stack.

## `tcbs_live_capture.py` — the live provider anchor (Feature 013 / P2-01)

The only ingestion path that cannot be verified from a database dump is the TCBS
Thesis stream: outside a trading session it sends nothing. This tool captures one
session read-only and answers the question a code review cannot — *is the
provider still sending what the contract says?*

```powershell
# during 09:00-11:30 or 13:00-14:45 Asia/Ho_Chi_Minh; asks for the owner's OTP
uv run --project tools\market-data\provider-poc python tools\verification\tcbs_live_capture.py --symbols VNM,MBB,ACV --seconds 90
uv run --project tools\market-data\provider-poc python tools\verification\tcbs_live_capture.py --symbols VNM --rest-only
```

It subscribes only to the two allowlisted channels, inventories every field of
every frame type (JSON type, value range, samples), takes the `tickerCommons`
REST snapshot on the same token, and compares each consumed number with **VCI** —
an independent provider — so a unit change shows up as a ratio near 1000 instead
of hiding behind the provider's own labels. Output goes to the gitignored
`out/`; the committed record of a capture is the pinned fixture
`finvera-be/src/test/resources/fixtures/market/tcbs/tcbs-frame-fixture.json`.

Secrets: API key from `finvera-be/.env`, OTP via `getpass`, JWT memory-only;
neither stdout nor the evidence file ever contains one.

Two provider rules this tool learned the hard way, both now in the contract: the
server closes the connection with 1002 if it receives an RFC WebSocket ping (only
`d|p|||` counts as a heartbeat), and `s|4` reference frames are never replayed for
a symbol subscribed mid-session.
