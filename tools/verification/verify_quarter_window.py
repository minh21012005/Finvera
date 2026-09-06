"""Independent recomputation over EXACTLY the population Feature 025 changed.

verify_calcs.py samples ~24 symbols; the 60 instruments with an ineligible quarter window are almost
certainly not among them, so the rule that was written for them has not been checked by anything but
its own unit tests. This walks all 60: rebuild each aggregate from the raw reports with the contract's
rule, independently of the Java, and compare with what is served."""
import csv, io, os, re, subprocess

env = {}
for line in io.open(r"D:\Finvera\finvera-be\.env", encoding="utf-8"):
    m = re.match(r"\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$", line)
    if m and not line.lstrip().startswith("#"):
        env[m.group(1)] = m.group(2).strip().strip("\"'")


def q(sql):
    r = subprocess.run(
        [r"C:\Program Files\PostgreSQL\18\bin\psql.exe", "-h", "127.0.0.1", "-p", "5432",
         "-U", env["FINVERA_DATABASE_USERNAME"], "-d", "finvera", "-X", "--csv", "-c", sql],
        env={**os.environ, "PGPASSWORD": env["FINVERA_DATABASE_PASSWORD"], "PGCLIENTENCODING": "UTF8"},
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise SystemExit(r.stderr)
    return list(csv.DictReader(io.StringIO(r.stdout)))


AGG = {"EPS": "EPS_TTM", "NET_PROFIT": "NET_PROFIT_TTM", "REVENUE": "REVENUE_TTM",
       "EBITDA": "EBITDA_TTM", "DIVIDEND_PER_SHARE": "DIVIDEND_PER_SHARE_TTM"}

# Every current report of every instrument, with the metrics the aggregates need.
reports = {}
for r in q("""select i.symbol, r.id, r.period_type, r.fiscal_year, r.fiscal_quarter, r.period_end,
                     m.metric_code, m.value
              from fundamental_report r
              join market_instrument i on i.id = r.instrument_id
              join fundamental_report_metric m on m.report_id = r.id
              where r.is_current and m.applicability = 'DEFINED'
                and m.metric_code in ('EPS','NET_PROFIT','REVENUE','EBITDA','DIVIDEND_PER_SHARE','TRAILING_EPS')"""):
    rep = reports.setdefault(r["symbol"], {}).setdefault(
        r["id"], {"t": r["period_type"], "fy": int(r["fiscal_year"]),
                  "fq": r["fiscal_quarter"], "end": r["period_end"], "m": {}})
    rep["m"][r["metric_code"]] = float(r["value"])

served = {}
for r in q("""select i.symbol, m.metric_code, m.value, m.applicability, m.quality_reason
              from fundamental_summary s
              join market_instrument i on i.id = s.instrument_id
              join fundamental_summary_metric m on m.summary_id = s.id
              where s.rule_version = 'fundamental-summary-v3'
                and s.id = (select id from fundamental_summary s2 where s2.instrument_id = s.instrument_id
                            and s2.rule_version = 'fundamental-summary-v3'
                            order by as_of_trading_date desc, calculated_at desc limit 1)"""):
    served.setdefault(r["symbol"], {})[r["metric_code"]] = r

flagged = [r["symbol"] for r in q("""
    with top4 as (select instrument_id, period_end,
                         row_number() over (partition by instrument_id order by period_end desc) rn
                  from fundamental_report where is_current and period_type='QUARTER'),
    span as (select instrument_id, min(period_end) mn, max(period_end) mx, count(*) n
             from top4 where rn<=4 group by instrument_id),
    ann as (select instrument_id, max(period_end) a from fundamental_report
            where is_current and period_type='ANNUAL' group by instrument_id)
    select i.symbol from span sp join market_instrument i on i.id=sp.instrument_id
    left join ann a on a.instrument_id=sp.instrument_id
    where sp.n=4 and ((sp.mx - sp.mn) not between 260 and 285 or (a.a is not null and a.a > sp.mx))
    order by i.symbol""")]

print(f"instruments with an ineligible quarter window: {len(flagged)}\n")
checks = fails = 0
for sym in flagged:
    reps = list(reports.get(sym, {}).values())
    quarters = sorted([r for r in reps if r["t"] == "QUARTER"], key=lambda r: r["end"], reverse=True)
    annuals = sorted([r for r in reps if r["t"] == "ANNUAL"], key=lambda r: r["end"], reverse=True)

    # contract fundamental-summary-v3, recomputed here from scratch
    eligible = False
    if len(quarters) >= 4:
        w = quarters[:4]
        try:
            idx = [r["fy"] * 4 + int(r["fq"]) for r in w]
            eligible = all(idx[i] - idx[i + 1] == 1 for i in range(3)) and \
                       not any(a["end"] > w[0]["end"] for a in annuals)
        except (TypeError, ValueError):
            eligible = False
    assert not eligible, f"{sym} was flagged but recomputes as eligible"

    for src, tgt in AGG.items():
        row = served.get(sym, {}).get(tgt)
        if row is None:
            continue
        checks += 1
        newest = max(reps, key=lambda r: (r["end"], r["t"] == "QUARTER")) if reps else None
        trailing = newest["m"].get("TRAILING_EPS") if newest else None
        if annuals:
            want = annuals[0]["m"].get(src)
            if want is None:
                # The annual report exists but does not carry this metric. EPS_TTM may still fall
                # through to the provider's own trailing figure (a v2 rule Feature 025 left alone);
                # anything else must be withheld.
                if tgt == "EPS_TTM" and trailing is not None:
                    ok = row["quality_reason"] == "PROVIDER_TRAILING_EPS" and abs(float(row["value"]) - trailing) <= 1e-6
                    why = f"annual has no EPS; provider trailing {trailing} vs served {row['value']}/{row['quality_reason']}"
                else:
                    ok = row["applicability"] != "DEFINED"
                    why = f"no annual {src}; served {row['applicability']}/{row['quality_reason']}"
            else:
                ok = row["applicability"] == "DEFINED" and abs(float(row["value"]) - want) <= 1e-6 \
                     and row["quality_reason"] == "ANNUAL_BASIS"
                why = f"annual {want} vs served {row['value']}/{row['quality_reason']}"
        else:
            # no annual to fall back to: the aggregate must be withheld under the new cause,
            # except EPS_TTM which may still use the provider's own trailing figure.
            if tgt == "EPS_TTM" and trailing is not None:
                ok = row["quality_reason"] == "PROVIDER_TRAILING_EPS"
            else:
                ok = row["applicability"] == "MISSING" and row["quality_reason"] == "QUARTER_WINDOW_INELIGIBLE"
            why = f"no annual report; served {row['applicability']}/{row['quality_reason']}"
        if not ok:
            fails += 1
            print(f"  DIFF {sym} {tgt}: {why}")

print(f"\nchecked {checks} served aggregates across {len(flagged)} instruments; mismatches: {fails}")
