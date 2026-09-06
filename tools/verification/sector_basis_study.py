"""Feature 024 research (specs/024-sector-basis-consistency, P2-11): how far does a sector
percentile move when every peer in the cross-section is priced on ONE basis?

Basis B (contract valuation-v1 "sector cross-section") ranks the subject's headline P/E against
its peers' headline P/E. A headline is quarter-TTM where four quarters are visible and fiscal-year
otherwise (`fundamental-summary-v3`), so one cross-section can mix both rulers *between companies*
— the same defect Feature 023 removed from Basis A *between dates*. Whether that matters is an
empirical question, and this tool answers it before any code is written (the specs/017 discipline).

For every sector with at least 9 current LISTED members (so each subject has >= 8 peers, the
contract's N_min floor) it computes, for each member:

  * `pct_mixed` — percentile of the member's **headline** P/E inside the pool of peers' headline
    P/Es. This is exactly what Finvera serves today.
  * `pct_fy`    — percentile of the member's **fiscal-year** P/E inside the pool of peers'
    fiscal-year P/Es (price / latest annual EPS on both sides).
  * the shift between them, in percentile points.

Read-only against the local database (credentials from finvera-be/.env, never printed). The
headline EPS is read from the persisted `fundamental_summary` exactly as `ValuationService`'s
Q-55 bulk path reads it, so the "today" column is the served number, not a re-derivation.

Usage:  python tools/verification/sector_basis_study.py [--sectors 5] [--json out.json]
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import os
import re
import statistics
import subprocess
import sys
from datetime import date

if hasattr(sys.stdout, "reconfigure"):  # sector names are Vietnamese; the Windows console is cp1252
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PSQL = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"
MIN_CONSTITUENTS = 8  # contract valuation-v1 Basis B floor


def read_env(path: str) -> dict:
    out = {}
    for line in io.open(path, encoding="utf-8"):
        m = re.match(r"\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$", line)
        if m and not line.lstrip().startswith("#"):
            out[m.group(1)] = m.group(2).strip().strip("\"'")
    return out


ENV = read_env(os.path.join(ROOT, "finvera-be", ".env"))


def q(sql: str) -> list[dict]:
    r = subprocess.run(
        [PSQL, "-h", "127.0.0.1", "-p", "5432", "-U", ENV["FINVERA_DATABASE_USERNAME"], "-d", "finvera",
         "-X", "--csv", "-c", sql],
        env={**os.environ, "PGPASSWORD": ENV["FINVERA_DATABASE_PASSWORD"], "PGCLIENTENCODING": "UTF8"},
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError((r.stderr or "").strip())
    return list(csv.DictReader(io.StringIO(r.stdout)))


def f(v):
    return None if v in (None, "") else float(v)


def percentile_rank(x: float, series: list[float]) -> float:
    """contract valuation-v1: 100 * (strictly_less + 0.5 * equal) / n."""
    less = sum(1 for s in series if s < x)
    equal = sum(1 for s in series if s == x)
    return 100.0 * (less + 0.5 * equal) / len(series)


def load() -> dict[str, dict]:
    """Per instrument: sector, price, headline EPS (as served) and fiscal-year EPS."""
    members = {}
    for row in q("""select p.instrument_id, i.symbol, p.sector_reference_id, s.sector_code,
                           s.display_name_vi, s.scheme
                    from equity_profile p
                    join market_instrument i on i.id = p.instrument_id
                    join sector_reference s on s.id = p.sector_reference_id
                    where p.effective_to is null and p.listing_status = 'LISTED'"""):
        members[row["instrument_id"]] = {
            "symbol": row["symbol"], "sector_code": row["sector_code"],
            "sector_name": row["display_name_vi"], "scheme": row["scheme"],
            "price": None, "eps_headline": None, "eps_headline_reason": None, "eps_fy": None,
        }

    # Latest current bar per instrument — the price Basis B uses for each peer.
    for row in q("""select distinct on (instrument_id) instrument_id, close_price
                    from equity_daily_bar where is_current
                    order by instrument_id, trading_date desc"""):
        if row["instrument_id"] in members:
            members[row["instrument_id"]]["price"] = f(row["close_price"])

    # Headline EPS_TTM: the persisted current summary, i.e. the number Basis B actually consumes
    # (ValuationService.persistedCurrentMetrics). Its quality_reason says which ruler produced it.
    for row in q("""select s.instrument_id, m.value, m.quality_reason
                    from fundamental_summary s
                    join fundamental_summary_metric m on m.summary_id = s.id
                    where m.metric_code = 'EPS_TTM' and m.applicability = 'DEFINED'
                      and s.rule_version = 'fundamental-summary-v3'
                      and s.id = (select id from fundamental_summary s2
                                  where s2.instrument_id = s.instrument_id
                                    and s2.rule_version = 'fundamental-summary-v3'
                                  order by as_of_trading_date desc, calculated_at desc limit 1)"""):
        if row["instrument_id"] in members:
            members[row["instrument_id"]]["eps_headline"] = f(row["value"])
            members[row["instrument_id"]]["eps_headline_reason"] = row["quality_reason"] or None

    # Q-60: a quarter-summed EPS_TTM is only meaningful when its four quarters are contiguous AND not
    # older than the newest annual report. `fundamental-summary-v2` checked neither, which served 60
    # instruments a "TTM" built from quarters years old; Feature 025 (`fundamental-summary-v3`) fixed
    # it. The flag stays as a regression signal — after a refresh under v3 this should print zero,
    # and anything it does flag moved for that reason, not because of the basis mix under study.
    spans = {}
    for row in q("""select instrument_id, min(period_end) q_oldest, max(period_end) q_newest, count(*) n
                    from (select instrument_id, period_end,
                                 row_number() over (partition by instrument_id order by period_end desc) rn
                          from fundamental_report where is_current and period_type = 'QUARTER') ranked
                    where rn <= 4 group by instrument_id"""):
        spans[row["instrument_id"]] = (row["q_oldest"], row["q_newest"], int(row["n"]))
    newest_annual = {row["instrument_id"]: row["a_newest"] for row in
                     q("""select instrument_id, max(period_end) a_newest from fundamental_report
                          where is_current and period_type = 'ANNUAL' group by instrument_id""")}

    # Fiscal-year EPS: the newest current ANNUAL report carrying EPS (valuation-v3 FISCAL_YEAR mode).
    for row in q("""select distinct on (r.instrument_id) r.instrument_id, m.value
                    from fundamental_report r
                    join fundamental_report_metric m on m.report_id = r.id
                    where r.is_current and r.period_type = 'ANNUAL'
                      and m.metric_code = 'EPS' and m.applicability = 'DEFINED'
                    order by r.instrument_id, r.period_end desc"""):
        if row["instrument_id"] in members:
            members[row["instrument_id"]]["eps_fy"] = f(row["value"])

    for m in members.values():
        price = m["price"]
        m["pe_headline"] = (price / m["eps_headline"]
                            if price and m["eps_headline"] and m["eps_headline"] > 0 else None)
        m["pe_fy"] = price / m["eps_fy"] if price and m["eps_fy"] and m["eps_fy"] > 0 else None
        # A headline whose EPS came from an annual report or the provider's trailing figure is
        # already "annual-ish"; only a null reason means a genuine four-quarter TTM sum.
        m["headline_basis"] = ("QUARTER_TTM" if m["eps_headline"] is not None and m["eps_headline_reason"] is None
                               else m["eps_headline_reason"])
        m["ttm_suspect"] = None
    for instrument_id, m in members.items():
        if m["headline_basis"] != "QUARTER_TTM":
            continue
        span = spans.get(instrument_id)
        if span is None or span[2] < 4:
            continue
        oldest, newest, _ = span
        annual = newest_annual.get(instrument_id)
        gap_days = (date.fromisoformat(newest) - date.fromisoformat(oldest)).days
        if not 260 <= gap_days <= 285:
            m["ttm_suspect"] = "NON_CONTIGUOUS_QUARTERS"
        elif annual is not None and annual > newest:
            m["ttm_suspect"] = "STALE_QUARTERS_WITH_FRESHER_ANNUAL"
    return members


def study(members: dict[str, dict]) -> tuple[list[dict], list[dict]]:
    by_sector: dict[str, list[dict]] = {}
    for m in members.values():
        by_sector.setdefault(m["sector_code"], []).append(m)

    sector_rows, subject_rows = [], []
    for code, group in sorted(by_sector.items()):
        if len(group) < MIN_CONSTITUENTS + 1:
            continue
        mixed_pool = {id(m): m["pe_headline"] for m in group if m["pe_headline"] is not None}
        fy_pool = {id(m): m["pe_fy"] for m in group if m["pe_fy"] is not None}
        if len(mixed_pool) < MIN_CONSTITUENTS + 1 or len(fy_pool) < MIN_CONSTITUENTS + 1:
            continue

        shifts, pool_shifts, subject_shifts = [], [], []
        for m in group:
            if m["pe_headline"] is None or m["pe_fy"] is None:
                continue
            peers_mixed = [v for k, v in mixed_pool.items() if k != id(m)]
            peers_fy = [v for k, v in fy_pool.items() if k != id(m)]
            if len(peers_mixed) < MIN_CONSTITUENTS or len(peers_fy) < MIN_CONSTITUENTS:
                continue
            pct_mixed = percentile_rank(m["pe_headline"], peers_mixed)
            pct_fy = percentile_rank(m["pe_fy"], peers_fy)
            shift = pct_fy - pct_mixed
            # Decomposition: the total shift has two independent causes and they call for different
            # remedies. Holding the subject fixed and swapping only the pool isolates the effect
            # THIS feature is about (peers measured on mixed rulers); holding the pool fixed and
            # swapping only the subject is the Basis A effect Feature 023 already addressed.
            pool_only = percentile_rank(m["pe_headline"], peers_fy) - pct_mixed
            subject_only = percentile_rank(m["pe_fy"], peers_mixed) - pct_mixed
            shifts.append(shift)
            pool_shifts.append(pool_only)
            subject_shifts.append(subject_only)
            subject_rows.append({
                "sector_code": code, "symbol": m["symbol"], "headline_basis": m["headline_basis"],
                "pe_headline": round(m["pe_headline"], 4), "pe_fy": round(m["pe_fy"], 4),
                "pct_mixed": round(pct_mixed, 3), "pct_fy": round(pct_fy, 3), "shift_pp": round(shift, 3),
                "pool_only_pp": round(pool_only, 3), "subject_only_pp": round(subject_only, 3),
                "ttm_suspect": m["ttm_suspect"],
            })
        if not shifts:
            continue
        quarter_basis = sum(1 for m in group if m["headline_basis"] == "QUARTER_TTM")
        absolute = sorted(abs(s) for s in shifts)
        sector_rows.append({
            "sector_code": code,
            "sector_name": group[0]["sector_name"],
            "members": len(group),
            "quarter_ttm_members": quarter_basis,
            "annual_members": len(group) - quarter_basis,
            "mixed_pool": len(mixed_pool),
            "fy_pool": len(fy_pool),
            "compared": len(shifts),
            "median_abs_shift": round(statistics.median(absolute), 2),
            "p90_abs_shift": round(absolute[min(len(absolute) - 1, int(0.9 * len(absolute)))], 2),
            "max_abs_shift": round(absolute[-1], 2),
            "over_10pp": sum(1 for s in absolute if s > 10),
            "over_20pp": sum(1 for s in absolute if s > 20),
            "median_abs_pool_only": round(statistics.median(sorted(abs(s) for s in pool_shifts)), 2),
            "median_abs_subject_only": round(statistics.median(sorted(abs(s) for s in subject_shifts)), 2),
        })
    return sector_rows, subject_rows


def main() -> None:
    parser = argparse.ArgumentParser(description="Sector-basis consistency measurement (P2-11)")
    parser.add_argument("--sectors", type=int, default=5, help="how many sectors to detail (largest first)")
    parser.add_argument("--json", default=None)
    args = parser.parse_args()

    members = load()
    sector_rows, subject_rows = study(members)
    if not sector_rows:
        print("No sector qualifies (>= 9 members with a computable P/E on both bases).")
        return

    detailed = sorted(sector_rows, key=lambda r: -r["members"])[: args.sectors]
    print(f"| sector | members | quarter-TTM | annual | pool mixed/FY | compared | median |shift| | p90 | max | >10pp | >20pp |")
    print("|---|---|---|---|---|---|---|---|---|---|---|")
    for r in detailed:
        print(f"| {r['sector_code']} {r['sector_name']} | {r['members']} | {r['quarter_ttm_members']} | "
              f"{r['annual_members']} | {r['mixed_pool']}/{r['fy_pool']} | {r['compared']} | "
              f"{r['median_abs_shift']} pp | {r['p90_abs_shift']} | {r['max_abs_shift']} | "
              f"{r['over_10pp']} | {r['over_20pp']} |")

    every = [abs(s["shift_pp"]) for s in subject_rows]
    every.sort()
    total_members = sum(r["members"] for r in sector_rows)
    quarter_members = sum(r["quarter_ttm_members"] for r in sector_rows)
    print(f"\nAll {len(sector_rows)} qualifying sectors, {len(subject_rows)} subjects compared:")
    print(f"  basis mix across those sectors: {quarter_members}/{total_members} members on quarter-TTM "
          f"({100.0 * quarter_members / total_members:.1f} %), the rest on an annual figure")
    print(f"  |percentile shift|: median {statistics.median(every):.2f} pp, "
          f"p90 {every[int(0.9 * (len(every) - 1))]:.2f} pp, max {every[-1]:.2f} pp")
    print(f"  subjects moving > 10 pp: {sum(1 for s in every if s > 10)} "
          f"({100.0 * sum(1 for s in every if s > 10) / len(every):.1f} %); "
          f"> 20 pp: {sum(1 for s in every if s > 20)}")
    dropped = sum(r["mixed_pool"] - r["fy_pool"] for r in sector_rows if r["mixed_pool"] > r["fy_pool"])
    print(f"  constituents a uniform FY rule would drop from the pools (no annual EPS): {dropped}")

    pool_only = sorted(abs(s["pool_only_pp"]) for s in subject_rows)
    subject_only = sorted(abs(s["subject_only_pp"]) for s in subject_rows)
    print("\nDecomposition (which half of the total shift comes from where):")
    print(f"  peers' mixed rulers alone (subject held on its headline): median {statistics.median(pool_only):.2f} pp, "
          f"p90 {pool_only[int(0.9 * (len(pool_only) - 1))]:.2f} pp, max {pool_only[-1]:.2f} pp, "
          f"> 10 pp: {sum(1 for s in pool_only if s > 10)} ({100.0 * sum(1 for s in pool_only if s > 10) / len(pool_only):.1f} %)")
    print(f"  subject's own ruler alone (pool held mixed):              median {statistics.median(subject_only):.2f} pp, "
          f"p90 {subject_only[int(0.9 * (len(subject_only) - 1))]:.2f} pp, max {subject_only[-1]:.2f} pp, "
          f"> 10 pp: {sum(1 for s in subject_only if s > 10)} ({100.0 * sum(1 for s in subject_only if s > 10) / len(subject_only):.1f} %)")

    suspects = [s for s in subject_rows if s["ttm_suspect"]]
    clean = sorted(abs(s["shift_pp"]) for s in subject_rows if not s["ttm_suspect"])
    print(f"\nQ-60 contamination: {len(suspects)} of {len(subject_rows)} subjects carry a quarter-summed "
          f"EPS_TTM that is non-contiguous or older than their own newest annual report.")
    if clean:
        print(f"  |percentile shift| EXCLUDING them: median {statistics.median(clean):.2f} pp, "
              f"p90 {clean[int(0.9 * (len(clean) - 1))]:.2f} pp, max {clean[-1]:.2f} pp, "
              f"> 10 pp: {sum(1 for s in clean if s > 10)} ({100.0 * sum(1 for s in clean if s > 10) / len(clean):.1f} %)")

    worst = sorted(subject_rows, key=lambda s: -abs(s["shift_pp"]))[:10]
    print("\nLargest individual moves:")
    for s in worst:
        print(f"  {s['symbol']:<6} sector {s['sector_code']:<4} {s['headline_basis']:<20} "
              f"PE {s['pe_headline']:>9.2f} -> FY {s['pe_fy']:>9.2f}   "
              f"percentile {s['pct_mixed']:>6.2f} -> {s['pct_fy']:>6.2f}  ({s['shift_pp']:+.2f} pp)"
              f"{'   [Q-60 ' + s['ttm_suspect'] + ']' if s['ttm_suspect'] else ''}")

    if args.json:
        with io.open(args.json, "w", encoding="utf-8") as fh:
            json.dump({"sectors": sector_rows, "subjects": subject_rows}, fh, ensure_ascii=False, indent=2)
        print(f"\nWrote {args.json}")


if __name__ == "__main__":
    main()
