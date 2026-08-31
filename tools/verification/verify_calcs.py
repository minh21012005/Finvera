"""Independent recomputation of Finvera's stored calculations from raw DB facts.
Textbook definitions (Wilder RSI/ATR, SMA-seeded EMA MACD, population-sd Bollinger,
TTM sums, YoY growth, PE/PB/PEG, mid-rank percentile, advance/decline breadth) in plain
float arithmetic -- deliberately NOT the Java code nor the contract text -- compared
against what the system persisted. Read-only."""
import re, io, subprocess, os, csv, sys, math, json, random
from collections import defaultdict
from datetime import date, datetime, timedelta, timezone

env = {}
for line in io.open(r"D:\Finvera\finvera-be\.env", encoding="utf-8"):
    m = re.match(r'^([A-Z_]+)=(.*)$', line.strip())
    if m: env[m.group(1)] = m.group(2).strip().strip('"')
PSQL = r"C:\Program Files\PostgreSQL\18\bin\psql.exe"

def q(sql):
    r = subprocess.run([PSQL, "-h", "127.0.0.1", "-p", "5432", "-U", env["FINVERA_DATABASE_USERNAME"], "-d", "finvera",
                        "-X", "--csv", "-c", sql],
                       env={**os.environ, "PGPASSWORD": env["FINVERA_DATABASE_PASSWORD"], "PGCLIENTENCODING": "UTF8"},
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError(r.stderr[:500])
    return list(csv.DictReader(io.StringIO(r.stdout)))

def f(x):
    return None if x in (None, "") else float(x)

REPORT = []
def note(section, ok, msg):
    REPORT.append((section, ok, msg))
    print(("OK   " if ok else "DIFF ") + f"[{section}] {msg}")

# ---------------- universe sample ----------------
fixed = ["VNM", "MBB", "SSI", "BVH", "HPG", "VIC", "FPT", "ACV", "PVS", "VCB", "MWG", "GAS"]
rows = q("""select i.symbol from market_instrument i join equity_daily_bar b on b.instrument_id=i.id and b.is_current
            group by i.symbol having count(*) >= 300 order by random() limit 12""")
symbols = fixed + [r["symbol"] for r in rows if r["symbol"] not in fixed]
print("symbols:", symbols)

# ---------------- technical indicators ----------------
def sma(v, p): return sum(v[-p:]) / p
def wilder(vals, p):
    avg = sum(vals[:p]) / p
    for x in vals[p:]:
        avg = (avg * (p - 1) + x) / p
    return avg
def ema_series(v, p):
    k = 2 / (p + 1); out = [sum(v[:p]) / p]
    for x in v[p:]:
        out.append(x * k + out[-1] * (1 - k))
    return out

def technical_checks(sym, bars, stored):
    closes = [b["c"] for b in bars]; highs = [b["h"] for b in bars]; lows = [b["l"] for b in bars]; vols = [b["v"] for b in bars]
    n = len(bars)
    def cmp(code, comp, mine, tol_rel=1e-6, tol_abs=1e-6):
        key = (code, comp)
        if key not in stored:
            note("technical", False, f"{sym} {code}.{comp}: no stored value"); return
        s = stored[key]
        if s is None:
            note("technical", mine is None, f"{sym} {code}.{comp}: stored null, mine={mine}"); return
        ok = abs(s - mine) <= max(tol_abs, tol_rel * abs(mine))
        note("technical", ok, f"{sym} {code}.{comp}: stored={s:.6f} mine={mine:.6f}")
    for p, code in ((20, "MA20"), (50, "MA50"), (200, "MA200")):
        if n >= p: cmp(code, "VALUE", sma(closes, p))
    if n >= 250:
        w = closes[-250:]
        d = [w[i] - w[i-1] for i in range(1, len(w))]
        g = [max(x, 0) for x in d]; l = [max(-x, 0) for x in d]
        ag, al = wilder(g, 14), wilder(l, 14)
        rsi = 50 if ag == 0 and al == 0 else (100 if al == 0 else 100 - 100 / (1 + ag / al))
        cmp("RSI14", "VALUE", rsi, tol_abs=1e-4)
        e12, e26 = ema_series(w, 12), ema_series(w, 26)
        macd = [e12[j - 11] - e26[j - 25] for j in range(25, len(w))]
        sig = sum(macd[:9]) / 9
        for x in macd[9:]:
            sig = x * (2 / 10) + sig * (1 - 2 / 10)
        cmp("MACD", "MACD_LINE", macd[-1], tol_abs=1e-3); cmp("MACD", "SIGNAL", sig, tol_abs=1e-3); cmp("MACD", "HISTOGRAM", macd[-1] - sig, tol_abs=1e-3)
        hw, lw, cw = highs[-250:], lows[-250:], closes[-250:]
        tr = [max(hw[i] - lw[i], abs(hw[i] - cw[i-1]), abs(lw[i] - cw[i-1])) for i in range(1, 250)]
        atr = wilder(tr, 14)
        cmp("ATR14", "VALUE", atr, tol_abs=1e-3); cmp("ATR14", "PERCENT_OF_CLOSE", atr / closes[-1] * 100, tol_abs=1e-4)
    if n >= 20:
        w = closes[-20:]; mid = sum(w) / 20; sd = math.sqrt(sum((x - mid) ** 2 for x in w) / 20)
        cmp("BBANDS", "MIDDLE", mid); cmp("BBANDS", "UPPER", mid + 2 * sd, tol_abs=1e-3); cmp("BBANDS", "LOWER", mid - 2 * sd, tol_abs=1e-3)
        cmp("BBANDS", "BANDWIDTH", (4 * sd) / mid * 100, tol_abs=1e-4)
        cmp("AVG_VOLUME20", "VALUE", sum(vols[-20:]) / 20)
    if n >= 21:
        cmp("RELATIVE_VOLUME", "VALUE", vols[-1] / (sum(vols[-21:-1]) / 20), tol_abs=1e-4)

# ---------------- fundamentals summary v2 (independent) ----------------
def summary_v2(reports, as_of):
    """reports: list of dict(period_type, fy, fq, period_end, observed_at, metrics{code:value})"""
    visible = [r for r in reports]  # caller pre-filters visibility
    quarters = sorted([r for r in visible if r["period_type"] == "QUARTER"], key=lambda r: r["period_end"], reverse=True)
    annuals = sorted([r for r in visible if r["period_type"] == "ANNUAL"], key=lambda r: r["period_end"], reverse=True)
    out = {}
    def ttm(code):
        if len(quarters) >= 4:
            vals = [r["metrics"].get(code) for r in quarters[:4]]
            return (sum(vals), None) if all(v is not None for v in vals) else (None, None)
        if annuals:
            v = annuals[0]["metrics"].get(code)
            return (v, "ANNUAL_BASIS") if v is not None else (None, None)
        return (None, None)
    for code, tgt in (("EPS", "EPS_TTM"), ("NET_PROFIT", "NET_PROFIT_TTM"), ("REVENUE", "REVENUE_TTM"), ("DIVIDEND_PER_SHARE", "DIVIDEND_PER_SHARE_TTM")):
        out[tgt] = ttm(code)
    if out["EPS_TTM"][0] is None:
        # fundamental-summary-v2: quarterly EPS absent (banks, securities, some industrials) ->
        # the provider's own trailing EPS from the newest report, disclosed as PROVIDER_TRAILING_EPS.
        # newest = period_end desc, QUARTER before ANNUAL on a tie (fundamental-summary-v2, Q-47)
        newest = sorted(visible, key=lambda r: (r["period_end"], 1 if r["period_type"] == "QUARTER" else 0), reverse=True)
        if newest and newest[0]["metrics"].get("TRAILING_EPS") is not None:
            out["EPS_TTM"] = (newest[0]["metrics"]["TRAILING_EPS"], "PROVIDER_TRAILING_EPS")
    def growth(code):
        if len(quarters) >= 8:
            cur = [r["metrics"].get(code) for r in quarters[:4]]; prior = [r["metrics"].get(code) for r in quarters[4:8]]
            if all(v is not None for v in cur + prior):
                p = sum(prior); c = sum(cur)
                return ("NA", None) if p <= 0 else ((c / p - 1) * 100, None)
            return (None, None)
        if len(annuals) >= 2:
            c, p = annuals[0]["metrics"].get(code), annuals[1]["metrics"].get(code)
            if c is None or p is None: return (None, None)
            return ("NA", "ANNUAL_BASIS") if p <= 0 else ((c / p - 1) * 100, "ANNUAL_BASIS")
        return (None, None)
    out["EPS_GROWTH_PERCENT"] = growth("EPS"); out["REVENUE_GROWTH_PERCENT"] = growth("REVENUE")
    return out

def load_reports(sym):
    rows = q(f"""select r.id, r.period_type, r.fiscal_year, r.fiscal_quarter, r.period_end, r.observed_at, m.metric_code, m.value, m.applicability
                 from fundamental_report r join fundamental_report_metric m on m.report_id=r.id join market_instrument i on i.id=r.instrument_id
                 where i.symbol='{sym}' and r.is_current""")
    by = {}
    for r in rows:
        rep = by.setdefault(r["id"], {"period_type": r["period_type"], "fy": int(r["fiscal_year"]), "fq": r["fiscal_quarter"],
                                      "period_end": r["period_end"], "observed_at": r["observed_at"], "metrics": {}})
        if r["applicability"] == "DEFINED" and r["value"] not in ("", None):
            rep["metrics"][r["metric_code"]] = float(r["value"])
    return list(by.values())

def fundamentals_checks(sym, reports):
    stored = {r["metric_code"]: r for r in q(f"""select m.metric_code, m.value, m.applicability, m.quality_reason from fundamental_summary_metric m
        join fundamental_summary s on s.id=m.summary_id join market_instrument i on i.id=s.instrument_id
        where i.symbol='{sym}' and s.rule_version='fundamental-summary-v2' and s.id=(select id from fundamental_summary s2 where s2.instrument_id=s.instrument_id and s2.rule_version='fundamental-summary-v2' order by as_of_trading_date desc, calculated_at desc limit 1)""")}
    mine = summary_v2(reports, None)
    for code, (val, basis) in mine.items():
        s = stored.get(code)
        if s is None:
            note("summary", False, f"{sym} {code}: not stored"); continue
        if val is None:
            note("summary", s["applicability"] == "MISSING", f"{sym} {code}: mine MISSING, stored {s['applicability']}:{s['quality_reason']}")
        elif val == "NA":
            note("summary", s["applicability"] == "NOT_APPLICABLE", f"{sym} {code}: mine NOT_APPLICABLE, stored {s['applicability']}:{s['quality_reason']}")
        else:
            sv = f(s["value"])
            ok = s["applicability"] == "DEFINED" and sv is not None and abs(sv - val) <= max(1e-4, 1e-8 * abs(val)) and (s["quality_reason"] or None) == basis
            note("summary", ok, f"{sym} {code}: mine={val:.6f}/{basis} stored={s['value']}/{s['quality_reason']} ({s['applicability']})")

# ---------------- valuation (independent) ----------------
def valuation_checks(sym, bars, reports, shares):
    va = q(f"""select a.id, a.as_of_trading_date, a.classification, a.score, a.displayed_score, a.confidence, a.history_point_count, a.used_own_history, a.used_sector, a.reason_codes
               from valuation_assessment a join market_instrument i on i.id=a.instrument_id where i.symbol='{sym}' and a.is_current and a.rule_version='valuation-v2'
               order by a.as_of_trading_date desc, a.calculated_at desc limit 1""")
    if not va:
        note("valuation", True, f"{sym}: no v2 assessment"); return
    a = va[0]
    metrics = {r["metric_code"]: r for r in q(f"select metric_code, value, applicability, own_history_percentile, sector_percentile, effective_weight, quality_reason from valuation_metric where assessment_id='{a['id']}'")}
    price = bars[-1]["c"]
    cur = summary_v2(reports, None)
    eps_ttm = cur["EPS_TTM"][0]; growth = cur["EPS_GROWTH_PERCENT"][0]
    # BVPS: latest report carrying BVPS (newest by period_end)
    bv = [r for r in sorted(reports, key=lambda r: r["period_end"], reverse=True) if "BVPS" in r["metrics"]]
    bvps = bv[0]["metrics"]["BVPS"] if bv else None
    def cmpv(code, mine, tol=1e-6):
        s = metrics.get(code)
        if s is None: note("valuation", False, f"{sym} {code}: not stored"); return
        if mine is None or mine == "NA":
            note("valuation", s["applicability"] != "DEFINED", f"{sym} {code}: mine {mine}, stored {s['applicability']}:{s['quality_reason']}")
        else:
            sv = f(s["value"]); ok = sv is not None and abs(sv - mine) <= max(tol, 1e-9 * abs(mine))
            note("valuation", ok, f"{sym} {code}: mine={mine:.6f} stored={s['value']} ({s['applicability']})")
    cmpv("PE", None if eps_ttm is None else ("NA" if eps_ttm <= 0 else price / eps_ttm), tol=1e-4)
    cmpv("PB", None if bvps is None else ("NA" if bvps <= 0 else price / bvps), tol=1e-4)
    pe = None if eps_ttm is None or eps_ttm <= 0 else price / eps_ttm
    cmpv("PEG", None if pe is None or growth is None else ("NA" if growth == "NA" or growth <= 0 else pe / growth), tol=1e-4)
    # Feature 022: PS (informational, marketCap / REVENUE_TTM) and DIVIDEND_YIELD (DPS_TTM / price)
    revenue_ttm = cur["REVENUE_TTM"][0]
    if shares:
        cmpv("PS", None if revenue_ttm is None else ("NA" if revenue_ttm <= 0 else price * shares / revenue_ttm), tol=1e-4)
    dps_ttm = cur.get("DIVIDEND_PER_SHARE_TTM", (None, None))[0]
    cmpv("DIVIDEND_YIELD", None if dps_ttm is None or price <= 0 else dps_ttm * 100 / price, tol=1e-4)
    # market cap sanity via shares
    if shares:
        note("valuation", True, f"{sym}: marketCap = {price:.0f} x {shares} = {price*shares/1e9:,.0f} bn VND (informational)")
    # own-history percentile of PE: rebuild series with visibility by observed_at
    if a["used_own_history"] == "t" and pe is not None and metrics.get("PE", {}).get("own_history_percentile"):
        series = []
        for b in bars[-750:]:
            boundary = datetime.combine(date.fromisoformat(b["d"]), datetime.max.time()).replace(tzinfo=timezone(timedelta(hours=7)))
            vis = [r for r in reports if r["observed_at"] and datetime.fromisoformat(r["observed_at"].replace(" ", "T")) <= boundary]
            if not vis: continue
            e = summary_v2(vis, None)["EPS_TTM"][0]
            if e is not None and e > 0:
                series.append(b["c"] / e)
        if len(series) >= 500:
            less = sum(1 for x in series if x < pe); eq = sum(1 for x in series if x == pe)
            pct = 100 * (less + 0.5 * eq) / len(series)
            sp = f(metrics["PE"]["own_history_percentile"])
            note("valuation", abs(sp - pct) <= 0.6, f"{sym} PE own-history percentile: mine={pct:.3f} (n={len(series)}) stored={sp} (hist_points={a['history_point_count']})")
        else:
            note("valuation", True, f"{sym}: rebuilt PE history has {len(series)} points (<500) -- informational")
    # score / band
    if a["classification"]:
        sc = f(a["score"]); band = "UNDER_VALUED" if sc < 35.5 else ("OVER_VALUED" if sc >= 64.5 else "FAIR_VALUED")
        note("valuation", band == a["classification"] and int(a["displayed_score"]) == round(sc + 1e-12), f"{sym} band: score={sc} -> {band}, stored {a['classification']} / displayed {a['displayed_score']}")

# ---------------- breadth ----------------
def breadth_check(trading_date):
    rows = q(f"""with cur as (select instrument_id, close_price from equity_daily_bar where is_current and trading_date='{trading_date}'),
                 prev as (select distinct on (instrument_id) instrument_id, close_price from equity_daily_bar where is_current and trading_date<'{trading_date}' order by instrument_id, trading_date desc),
                 uni as (select p.instrument_id from equity_profile p join market_instrument i on i.id=p.instrument_id where p.effective_to is null and i.status in ('ACTIVE','UNKNOWN'))
                 select count(*) eligible,
                        count(*) filter (where c.close_price > pv.close_price) adv,
                        count(*) filter (where c.close_price < pv.close_price) dec,
                        count(*) filter (where c.close_price = pv.close_price) unch,
                        count(*) filter (where c.close_price is null or pv.close_price is null) uncl
                 from uni u left join cur c on c.instrument_id=u.instrument_id left join prev pv on pv.instrument_id=u.instrument_id""")[0]
    st = q(f"select advancing, declining, unchanged, unclassified, eligible from breadth_snapshot where trading_date='{trading_date}' order by calculated_at desc limit 1")
    if not st: note("breadth", False, f"no snapshot for {trading_date}"); return
    s = st[0]
    ok = all(int(s[k]) == int(rows[v]) for k, v in (("advancing","adv"),("declining","dec"),("unchanged","unch"),("unclassified","uncl"),("eligible","eligible")))
    note("breadth", ok, f"{trading_date}: mine adv/dec/unch/uncl/elig = {rows['adv']}/{rows['dec']}/{rows['unch']}/{rows['uncl']}/{rows['eligible']}; stored {s['advancing']}/{s['declining']}/{s['unchanged']}/{s['unclassified']}/{s['eligible']}")

# ---------------- fundamentals anchors (audited figures, Feature 018 / Q-57) ----------------
# Labels are never trusted: a stored statement fact must equal an audited figure or satisfy an
# arithmetic identity of the data itself. Values in VND as filed (VCI keeps the VND; banks are
# reported to the thousand).
AUDITED = {
    # symbol: {(metric, fiscal_year): value}
    "VNM": {("REVENUE", 2022): 59956247197418, ("REVENUE", 2023): 60368915512000, ("REVENUE", 2024): 61782609528445,
            ("REVENUE", 2025): 63645886756227, ("NET_PROFIT", 2025): 9413589732469, ("EPS", 2022): 3632, ("EPS", 2025): 4028},
    "MBB": {("EPS", 2022): 3856, ("REVENUE", 2025): 67693015000000, ("NET_PROFIT", 2025): 27382978000000},
    "BVH": {("REVENUE", 2025): 40948251401814, ("EPS", 2025): 3821},
    "SSI": {("NET_PROFIT", 2025): 4106880733899},
}
ANCHOR_TOLERANCE = 0.0005  # 0.05 %: rounding to thousands in bank statements


def anchors_check():
    for sym, facts in AUDITED.items():
        rows = q(f"""select r.fiscal_year, m.metric_code, m.value, r.source from fundamental_report r
                     join fundamental_report_metric m on m.report_id=r.id join market_instrument i on i.id=r.instrument_id
                     where i.symbol='{sym}' and r.is_current and r.period_type='ANNUAL' and m.applicability='DEFINED'
                       and m.metric_code in ('REVENUE','NET_PROFIT','EPS')""")
        stored = {(r["metric_code"], int(r["fiscal_year"])): (f(r["value"]), r["source"]) for r in rows}
        for (metric, year), expected in facts.items():
            got = stored.get((metric, year))
            if got is None:
                note("anchors", False, f"{sym} {metric} FY{year}: not stored (expected {expected:,})"); continue
            value, source = got
            ok = abs(value - expected) <= max(1.0, ANCHOR_TOLERANCE * abs(expected))
            note("anchors", ok, f"{sym} {metric} FY{year}: stored={value:,.0f} ({source}) audited={expected:,}")
    # identity: FY2025 = sum of its four quarters, universe-wide (REVENUE and NET_PROFIT)
    rows = q("""with y as (select r.instrument_id, m.metric_code, m.value fy from fundamental_report r join fundamental_report_metric m on m.report_id=r.id
                          where r.is_current and r.period_type='ANNUAL' and r.fiscal_year=2025 and m.applicability='DEFINED' and m.metric_code in ('REVENUE','NET_PROFIT')),
                     qs as (select r.instrument_id, m.metric_code, sum(m.value) s, count(*) n from fundamental_report r join fundamental_report_metric m on m.report_id=r.id
                            where r.is_current and r.period_type='QUARTER' and r.fiscal_year=2025 and m.applicability='DEFINED' and m.metric_code in ('REVENUE','NET_PROFIT')
                            group by 1,2 having count(*)=4)
                select y.metric_code, count(*) n, sum(case when abs(y.fy - qs.s) <= greatest(1000, 0.0005*abs(y.fy)) then 1 else 0 end) matching
                from y join qs using(instrument_id, metric_code) group by 1""")
    for r in rows:
        n, matching = int(r["n"]), int(r["matching"])
        note("anchors", n > 0 and matching / n >= 0.99, f"FY2025 = sum of 4 quarters for {r['metric_code']}: {matching}/{n} instruments")
    # source retirement: no current statement facts may still come from the mislabelled KBS pages
    rows = q("select source, count(*) n from fundamental_report where is_current group by 1")
    for r in rows:
        if r["source"] == "VNSTOCK_KBS":
            note("anchors", False, f"{r['n']} current fundamental_report rows still sourced from VNSTOCK_KBS (Q-57: mislabelled periods)")
        else:
            note("anchors", True, f"{r['n']} current fundamental_report rows from {r['source']}")
    # shares identity: NET_PROFIT / EPS from the FY2025 VCI statements must land near the profile's shares
    # (unit sanity for EPS and shares: a x1000 error in either shows up here at once; +/-10% allows
    # minority interests and corporate actions after the fiscal year-end)
    rows = q("""select i.symbol, p.shares_outstanding sh, np.value np, eps.value eps from equity_profile p join market_instrument i on i.id=p.instrument_id
                join fundamental_report r on r.instrument_id=i.id and r.is_current and r.period_type='ANNUAL' and r.fiscal_year=2025 and r.source='VNSTOCK_VCI'
                join fundamental_report_metric np on np.report_id=r.id and np.metric_code='NET_PROFIT' and np.applicability='DEFINED'
                join fundamental_report_metric eps on eps.report_id=r.id and eps.metric_code='EPS' and eps.applicability='DEFINED' and eps.value <> 0
                where p.effective_to is null and p.shares_outstanding > 0""")
    ratios = [(r["symbol"], (f(r["np"]) / f(r["eps"])) / f(r["sh"])) for r in rows]
    within = [sym for sym, x in ratios if 0.9 <= x <= 1.1]
    worst = sorted(ratios, key=lambda t: abs(t[1] - 1), reverse=True)[:5]
    note("anchors", len(ratios) > 0 and len(within) / len(ratios) >= 0.9,
         f"shares identity: NET_PROFIT/EPS (FY2025, VCI) within +/-10% of profile shares for {len(within)}/{len(ratios)}; "
         f"worst {[(sym, round(x, 2)) for sym, x in worst]}")


# ---------------- sector coverage (Feature 020) ----------------
def sector_coverage_check():
    total = int(q("select count(*) n from equity_profile where effective_to is null and listing_status='LISTED'")[0]["n"])
    rows = q("""select s.scheme, s.scheme_version, i.venue, count(*) n from equity_profile p join sector_reference s on s.id=p.sector_reference_id
                join market_instrument i on i.id=p.instrument_id where p.effective_to is null and p.listing_status='LISTED'
                group by 1,2,3 order by 1,2,3""")
    schemes = sorted({(r["scheme"], r["scheme_version"]) for r in rows})
    classified = sum(int(r["n"]) for r in rows)
    by_venue = {}
    for r in rows:
        by_venue[r["venue"]] = by_venue.get(r["venue"], 0) + int(r["n"])
    ok = total > 0 and classified / total >= 0.95 and len(schemes) == 1
    note("sector", ok, f"{classified}/{total} LISTED profiles have a sector under {schemes}, by venue {by_venue} "
                       f"(Feature 020 gate: >= 95% under one scheme)")


# ---------------- provider cross-check: stored KBS closes vs VCI (independent provider) ----------------
INDEX_PROVIDER_SYMBOLS = {"VN_INDEX": "VNINDEX", "HNX_INDEX": "HNXINDEX", "UPCOM_INDEX": "HNXUPCOMINDEX", "VN30": "VN30", "HNX30": "HNX30"}

def vci_closes(provider_symbols, start, end, source="vci"):
    """Closing prices from VCI through the exporter environment (vnstock 4.0.7); one call per symbol."""
    script = (
        "import json, sys, warnings\n"
        "warnings.simplefilter('ignore')\n"
        "from vnstock import Quote\n"
        "out = {}\n"
        "for s in sys.argv[3:]:\n"
        "    try:\n"
        "        df = Quote(symbol=s, source='vci').history(start=sys.argv[1], end=sys.argv[2], interval='1D')\n"
        "        out[s] = {str(r['time'])[:10]: float(r['close']) for _, r in df.iterrows()}\n"
        "    except BaseException as e:\n"
        "        out[s] = {'error': type(e).__name__}\n"
        "print('@@' + json.dumps(out))\n")
    r = subprocess.run(["uv", "run", "--project", "../provider-poc", "python", "-c", script, start, end, source, *provider_symbols],
                       cwd=r"D:\Finvera\tools\market-data\vnstock-export", capture_output=True, text=True,
                       encoding="utf-8", errors="replace", env={**os.environ, "PYTHONIOENCODING": "utf-8"})
    for line in r.stdout.splitlines():
        if line.startswith("@@"):
            return json.loads(line[2:])
    raise RuntimeError("VCI quote probe produced no result: " + r.stderr[-300:])

def provider_crosscheck(symbols, days=10):
    end = date.today()
    start = end - timedelta(days=days)
    # ADR-0013: the reference is always the OTHER provider than the one the stored bars come from,
    # so this stays a genuine two-provider check before and after the VCI migration.
    stored_sources = {r["source"] for r in q("select distinct source from equity_daily_bar where is_current")}
    reference = "kbs" if "VNSTOCK_VCI" in stored_sources else "vci"
    indices = {r["code"]: INDEX_PROVIDER_SYMBOLS.get(r["code"], r["code"]) for r in q("select code from market_index where active_to is null")}
    closes = vci_closes(list(symbols) + list(indices.values()), start.isoformat(), end.isoformat(), reference)
    for sym in symbols:
        stored = {r["trading_date"]: f(r["close_price"]) for r in q(f"""select b.trading_date, b.close_price from equity_daily_bar b
                  join market_instrument i on i.id=b.instrument_id where i.symbol='{sym}' and b.is_current and b.trading_date >= '{start}'""")}
        got = closes.get(sym, {})
        if not got or "error" in got:
            note("provider", True, f"{sym}: {reference} quote unavailable ({got.get('error', 'empty') if got else 'empty'}) -- informational"); continue
        common = sorted(set(stored) & set(got))
        # exact match expected; providers can differ by tick-rounding of corporate-action adjustments
        # (seen 2026-08: MSB 13,270 KBS vs 13,250 VCI on pre-ex-date sessions, 0.15%) -- those are
        # conventions, not errors, so only relative differences above 0.5% count as mismatches.
        diffs_all = [(d, stored[d], got[d] * 1000) for d in common if abs(stored[d] - got[d] * 1000) > 0.5]
        bad = [(d, a, b) for d, a, b in diffs_all if abs(a - b) > 0.005 * max(abs(a), abs(b))]
        rounding = len(diffs_all) - len(bad)
        if len(common) < 3 and not bad:
            note("provider", True, f"{sym}: only {len(common)} overlapping sessions (thinly traded) -- informational"); continue
        note("provider", not bad, f"{sym}: close stored vs {reference} on {len(common)} sessions, {len(bad)} mismatches"
             + (f" {bad[:3]}" if bad else "") + (f"; {rounding} adjustment-rounding diffs <=0.5%" if rounding else ""))
    for code, psym in indices.items():
        # the EOD fact is the CLOSED snapshot's latest revision; intraday UNKNOWN ticks are not closes
        stored = {r["trading_date"]: f(r["index_level"]) for r in q(f"""select s.trading_date, s.index_level from index_snapshot s
                  join market_index m on m.id=s.index_id where m.code='{code}' and s.trading_date >= '{start}' and s.session_state='CLOSED'
                  and not exists (select 1 from index_snapshot n where n.supersedes_id = s.id)""")}
        got = closes.get(psym, {})
        if not got or "error" in got:
            note("provider", True, f"index {code}: {reference} symbol {psym} unavailable -- informational"); continue
        common = sorted(set(stored) & set(got))
        bad = [(d, stored[d], got[d]) for d in common if abs(stored[d] - got[d]) > 0.02]
        note("provider", not bad and len(common) >= 3, f"index {code}: level stored vs {reference} on {len(common)} sessions, {len(bad)} mismatches {bad[:3]}")


# ---------------- run ----------------
for sym in symbols:
    bars = [{"d": r["trading_date"], "c": f(r["close_price"]), "h": f(r["high_price"]), "l": f(r["low_price"]), "v": f(r["volume"]) or 0.0}
            for r in q(f"select b.trading_date, b.close_price, b.high_price, b.low_price, b.volume from equity_daily_bar b join market_instrument i on i.id=b.instrument_id where i.symbol='{sym}' and b.is_current order by b.trading_date")]
    if not bars: note("data", False, f"{sym}: no bars"); continue
    last = bars[-1]["d"]
    stored = {}
    for r in q(f"""select r.indicator_code, v.component_code, v.value from technical_indicator_result r join technical_indicator_value v on v.result_id=r.id
                   join market_instrument i on i.id=r.instrument_id where i.symbol='{sym}' and r.is_current and r.as_of_trading_date='{last}'"""):
        stored[(r["indicator_code"], r["component_code"])] = f(r["value"])
    if stored:
        technical_checks(sym, bars, stored)
    else:
        note("technical", True, f"{sym}: no stored indicators for {last} (informational)")
    reports = load_reports(sym)
    if reports:
        fundamentals_checks(sym, reports)
        sh = q(f"select p.shares_outstanding from equity_profile p join market_instrument i on i.id=p.instrument_id where i.symbol='{sym}' and p.effective_to is null")
        valuation_checks(sym, bars, reports, int(sh[0]["shares_outstanding"]) if sh and sh[0]["shares_outstanding"] else None)

breadth_check("2026-08-28")

anchors_check()
sector_coverage_check()
provider_crosscheck(symbols)
diffs = [r for r in REPORT if not r[1]]
print(f"\nTOTAL checks={len(REPORT)} diffs={len(diffs)}")
for d in diffs: print("  DIFF", d[0], d[2])
json.dump(REPORT, io.open(os.path.join(os.path.dirname(__file__), "verify_calcs_report.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
