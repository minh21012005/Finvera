"""Feature 017 research (specs/017-history-basis-consistency): how much do own-history PE/PB
points computed on the ANNUAL basis (fewer than four quarters visible at that date,
or an ineligible quarter window since `fundamental-summary-v3`) differ from what the quarterly-TTM basis would give, and how many of
the 750 history points per symbol are annual-basis at all?

Read-only against the local database (credentials from finvera-be/.env, never printed).
Rebuilds the own-history series exactly like ValuationService.buildOwnHistorySeries:
for each of the last 750 current bars, the reports observed on or before 23:59:59 VN of the
bar's trading date are visible; EPS_TTM = sum of the 4 newest visible quarters, else the
newest visible annual EPS (ANNUAL_BASIS); BVPS = newest visible report carrying BVPS.

Usage:  python tools/verification/history_basis_study.py [--symbols VNM,MBB,...] [--n 20]

2026-09-05 (Feature 023, contract valuation-v3): the own-history percentile now ranks a
fiscal-year-basis comparison value against a fiscal-year-basis series, so the FY-vs-TTM gap this
tool measures no longer enters the rank. The gap is still worth knowing -- it is how far the
percentile's denominator lags the headline's -- and the table stays as the record of why the
basis was unified (specs/017 R-004/R-005). The "percentile shift if annual dropped" column
describes the pre-v3 mixed comparison and is informational only.
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import io
import os
import re
import statistics
import subprocess
from datetime import datetime, timedelta, timezone

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PSQL = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"
VN = timezone(timedelta(hours=7))


def read_env(path: str) -> dict:
    out = {}
    for line in io.open(path, encoding="utf-8"):
        m = re.match(r"\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$", line)
        if m and not line.lstrip().startswith("#"):
            out[m.group(1)] = m.group(2).strip().strip("\"'")
    return out


ENV = read_env(os.path.join(ROOT, "finvera-be", ".env"))


def q(sql: str) -> list[dict]:
    r = subprocess.run([PSQL, "-h", "127.0.0.1", "-p", "5432", "-U", ENV["FINVERA_DATABASE_USERNAME"], "-d", "finvera",
                        "-X", "--csv", "-c", sql],
                       env={**os.environ, "PGPASSWORD": ENV["FINVERA_DATABASE_PASSWORD"], "PGCLIENTENCODING": "UTF8"},
                       capture_output=True, text=True, encoding="utf-8")
    if r.returncode != 0:
        raise RuntimeError(r.stderr.strip())
    return list(csv.DictReader(io.StringIO(r.stdout)))


def f(v):
    return None if v in (None, "") else float(v)


def load(sym: str):
    bars = q(f"""select b.trading_date, b.close_price from equity_daily_bar b join market_instrument i on i.id=b.instrument_id
                 where i.symbol='{sym}' and b.is_current order by b.trading_date""")
    rows = q(f"""select r.id, r.period_type, r.period_end, r.observed_at, m.metric_code, m.value
                 from fundamental_report r join fundamental_report_metric m on m.report_id=r.id
                 join market_instrument i on i.id=r.instrument_id
                 where i.symbol='{sym}' and r.is_current and m.applicability='DEFINED' and m.metric_code in ('EPS','BVPS')""")
    reports = {}
    for r in rows:
        rep = reports.setdefault(r["id"], {"type": r["period_type"], "end": r["period_end"], "obs": r["observed_at"], "m": {}})
        rep["m"][r["metric_code"]] = f(r["value"])
    reps = list(reports.values())
    for rep in reps:
        rep["obs_dt"] = datetime.fromisoformat(rep["obs"].replace(" ", "T")) if rep["obs"] else None
    return bars, reps


def series(bars, reps, n=750):
    """Per bar: (date, close, eps_q, eps_a, bvps, basis_used) using v2 rules."""
    out = []
    for b in bars[-n:]:
        boundary = datetime.combine(dt.date.fromisoformat(b["trading_date"]), dt.time(23, 59, 59)).replace(tzinfo=VN)
        vis = [r for r in reps if r["obs_dt"] is not None and r["obs_dt"] <= boundary]
        if not vis:
            continue
        # tie-break like fundamental-summary-v2 (Q-47): period end desc, QUARTER before ANNUAL
        vis.sort(key=lambda r: (r["end"], 1 if r["type"] == "QUARTER" else 0), reverse=True)
        quarters = [r for r in vis if r["type"] == "QUARTER"]
        annuals = [r for r in vis if r["type"] == "ANNUAL"]
        eps_q = None
        if len(quarters) >= 4 and all("EPS" in r["m"] for r in quarters[:4]):
            eps_q = sum(r["m"]["EPS"] for r in quarters[:4])
        eps_a = annuals[0]["m"].get("EPS") if annuals else None
        used = "QUARTER_TTM" if eps_q is not None else ("ANNUAL_BASIS" if eps_a is not None else "NONE")
        bv = next((r["m"]["BVPS"] for r in vis if "BVPS" in r["m"]), None)
        out.append((b["trading_date"], f(b["close_price"]), eps_q, eps_a, bv, used))
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--symbols", default="")
    ap.add_argument("--n", type=int, default=20)
    args = ap.parse_args()
    if args.symbols:
        symbols = [s.strip().upper() for s in args.symbols.split(",") if s.strip()]
    else:
        fixed = ["VNM", "MBB", "SSI", "BVH", "HPG", "VIC", "FPT", "ACV", "PVS", "VCB", "MWG", "GAS"]
        extra = q(f"""select i.symbol from market_instrument i join equity_daily_bar b on b.instrument_id=i.id and b.is_current
                      group by i.symbol having count(*) >= 750 order by random() limit {max(0, args.n - len(fixed))}""")
        symbols = fixed + [r["symbol"] for r in extra if r["symbol"] not in fixed]

    print("| symbol | points | annual-basis points | % annual | both-basis points | median abs PE diff | p90 abs PE diff | max abs PE diff | PE percentile shift |")
    print("|---|---|---|---|---|---|---|---|---|")
    agg_diffs, agg_share, agg_shift = [], [], []
    for sym in symbols:
        bars, reps = load(sym)
        s = series(bars, reps)
        if not s:
            print(f"| {sym} | 0 | – | – | – | – | – | – | – |")
            continue
        annual_pts = [p for p in s if p[5] == "ANNUAL_BASIS"]
        both = [p for p in s if p[2] and p[3] and p[2] > 0 and p[3] > 0]
        diffs = [abs((p[1] / p[3]) / (p[1] / p[2]) - 1) * 100 for p in both]   # |PE_annual / PE_quarter - 1| in %
        # percentile shift of today's PE if annual-basis points were dropped from the series
        pe_series_mixed = [p[1] / (p[2] if p[2] else p[3]) for p in s if (p[2] or p[3]) and (p[2] or p[3]) > 0]
        pe_series_q_only = [p[1] / p[2] for p in s if p[2] and p[2] > 0]
        shift = None
        if pe_series_mixed and pe_series_q_only and s[-1][2]:
            pe_now = s[-1][1] / s[-1][2]
            def pct(series_, v):
                less = sum(1 for x in series_ if x < v); eq = sum(1 for x in series_ if x == v)
                return 100 * (less + 0.5 * eq) / len(series_)
            shift = pct(pe_series_q_only, pe_now) - pct(pe_series_mixed, pe_now)
        share = 100 * len(annual_pts) / len(s)
        agg_share.append(share); agg_diffs.extend(diffs)
        if shift is not None:
            agg_shift.append(shift)
        med = f"{statistics.median(diffs):.1f} %" if diffs else "–"
        p90 = f"{sorted(diffs)[int(0.9 * (len(diffs) - 1))]:.1f} %" if diffs else "–"
        mx = f"{max(diffs):.1f} %" if diffs else "–"
        sh = f"{shift:+.1f} pp" if shift is not None else "–"
        print(f"| {sym} | {len(s)} | {len(annual_pts)} | {share:.0f} % | {len(both)} | {med} | {p90} | {mx} | {sh} |")
    if agg_diffs:
        print(f"\nAggregate over {len(symbols)} symbols: annual-basis share median {statistics.median(agg_share):.0f} % "
              f"(max {max(agg_share):.0f} %); |PE_annual/PE_quarter-1| median {statistics.median(agg_diffs):.1f} %, "
              f"p90 {sorted(agg_diffs)[int(0.9 * (len(agg_diffs) - 1))]:.1f} %, max {max(agg_diffs):.1f} %; "
              f"today's PE percentile shift if annual points were dropped: median {statistics.median(agg_shift):+.1f} pp, "
              f"max |{max(abs(x) for x in agg_shift):.1f}| pp")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
