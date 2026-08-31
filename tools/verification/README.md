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
