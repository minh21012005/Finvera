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
