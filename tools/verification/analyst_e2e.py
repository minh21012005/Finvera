"""Owner-operated end-to-end check of the AI Analyst on the real stack (Feature 015).

Runs ten scripted questions through finvera-ai (/internal/v1/analyst/ask, SSE), re-fetches every
successful tool response directly from finvera-be with the same arguments, and checks:
  * number fidelity  - every number in the answer exists in a tool response (vi-VN formats,
                       rounding tolerance; small counts < 10 ignored as in explain.py)
  * basis disclosure - ANNUAL_BASIS / PROVIDER_TRAILING_EPS / REDUCED_METRIC_SET / withheld
                       valuation / non-CURRENT data status are worded in the answer
Writes <out_dir>/analyst_e2e_<date>.json (raw recording, usable as a golden fixture) and prints a
markdown table. Secrets are read from finvera-be/.env and finvera-ai/.env and never printed.

Usage (both services running):  python tools/verification/analyst_e2e.py [--out DIR] [--only 3,7]
"""
from __future__ import annotations

import argparse
import datetime as dt
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
AI_URL = "http://127.0.0.1:8000/internal/v1"
BE_URL = "http://127.0.0.1:8080/internal/v1"


def read_env(path: str) -> dict:
    out = {}
    for line in io.open(path, encoding="utf-8"):
        m = re.match(r"\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$", line)
        if m and not line.lstrip().startswith("#"):
            v = m.group(2).strip()
            if len(v) >= 2 and v[0] == v[-1] and v[0] in "\"'":
                v = v[1:-1]
            out[m.group(1)] = v
    return out


QUESTIONS = [
    # id, symbol, question, what it probes
    ("Q01", "VNM", "Giá cổ phiếu VNM hôm nay bao nhiêu và biến động thế nào?", "STOCK: price/change/asOf; PRICE not fabricated"),
    ("Q02", "VNM", "Giải thích định giá của VNM: P/E, P/B, so với lịch sử của chính nó.", "VALUATION: classification, score, percentiles, notes"),
    ("Q03", None, "So sánh định giá của MBB và VCB, ngân hàng nào đang rẻ hơn?", "two VALUATION calls; PROVIDER_TRAILING_EPS basis"),
    ("Q04", "HPG", "Các chỉ báo kỹ thuật của HPG (MA, RSI, MACD) đang nói gì?", "TECHNICAL: values + signals"),
    ("Q05", "AAA", "Định giá của AAA có đáng tin không? Công ty này đang lỗ phải không?", "loss-maker: REDUCED_METRIC_SET, PE NOT_APPLICABLE"),
    ("Q06", "ABB", "ABB thuộc ngành nào và định giá hiện tại ra sao?", "UPCoM, no sector: OWN_HISTORY only, SECTOR_BASIS_INSUFFICIENT"),
    ("Q07", "MBB", "Doanh thu và lợi nhuận của MBB tăng trưởng thế nào trong 12 tháng qua?", "bank fundamentals: revenue basis, ANNUAL_BASIS growth, EPS_TTM provider trailing"),
    ("Q08", None, "Lọc các cổ phiếu có ROE trên 20% và P/E dưới 10.", "SCREENING: counts + names from tool only"),
    ("Q09", None, "Thị trường hôm nay thế nào? Độ rộng và trạng thái thị trường ra sao?", "MARKET: breadth counts, regime label, dataStatus"),
    ("Q10", None, "Danh mục của tôi hiện có những rủi ro gì?", "PORTFOLIO: owner has no positions -> honest no-data"),
]

NUM_TOKEN = re.compile(r"(?<![\w/\-])[-+]?\d+(?:[.,]\d+)*(?![\w/\-])")
DATE_LIKE = re.compile(r"\d{4}-\d{2}-\d{2}(?:T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?)?|\d{4}-Q\d|\d{1,2}/\d{1,2}/\d{2,4}|\d{1,2}/\d{4}|\d{1,2}/\d{1,2}|\d{2}:\d{2}(?::\d{2})?|(?<![\d.,])(?:19|20)\d{2}(?![\d.,])|(?m:^[ \t]*\d{1,2}\.[ \t])")
COUNT_UNITS = re.compile(r"^\s*(?:tháng|quý|phiên|năm|mã|yếu tố|cổ phiếu|ngày|chiến lược|công cụ|bậc|lần)(?![\w])", re.IGNORECASE)
SMALL = 10.0


def readings(token: str) -> list[float]:
    """Every defensible reading of a numeric token: vi-VN ('62.300', '1.832,12'), en-US
    ('125,426,841,000', '924.17'), and plain. Explain.py uses the same idea (Q-43)."""
    t = token.replace(" ", "")
    out = set()
    try:
        out.add(float(t))
    except ValueError:
        pass
    if "," in t and "." in t:
        last = max(t.rfind(","), t.rfind("."))
        dec = t[last]
        thou = "." if dec == "," else ","
        try:
            out.add(float(t.replace(thou, "").replace(dec, ".")))
        except ValueError:
            pass
    else:
        for sep in (",", "."):
            if sep in t:
                groups = t.split(sep)
                if all(len(g) == 3 for g in groups[1:]) and len(groups) > 1:
                    try:
                        out.add(float(t.replace(sep, "")))          # thousands reading
                    except ValueError:
                        pass
                if groups.count("") == 0 and len(groups) == 2:
                    try:
                        out.add(float(t.replace(sep, ".")))         # decimal reading
                    except ValueError:
                        pass
    return sorted(out)


def parse_vi_numbers(text: str) -> list[tuple[str, list[float]]]:
    """(token, readings) for every numeric token that is not part of a date/period/time."""
    masked = DATE_LIKE.sub(lambda m: " " * len(m.group(0)), text)
    out = []
    for m in NUM_TOKEN.finditer(masked):
        raw = m.group(0)
        if COUNT_UNITS.match(masked[m.end():m.end() + 14]):
            continue  # "12 tháng", "4 quý", "20 phiên": counting words, not financial figures
        rs = readings(raw)
        if rs:
            out.append((raw, rs))
    return out


def numeric_leaves(obj, acc: list[float]):
    if isinstance(obj, dict):
        for v in obj.values():
            numeric_leaves(v, acc)
    elif isinstance(obj, list):
        for v in obj:
            numeric_leaves(v, acc)
    elif isinstance(obj, bool):
        return
    elif isinstance(obj, (int, float)):
        acc.append(float(obj))
    elif isinstance(obj, str):
        s = obj.strip()
        if re.fullmatch(r"[-+]?\d+(\.\d+)?", s):
            try:
                acc.append(float(s))
            except ValueError:
                pass


def supported(value: float, candidates: list[float]) -> bool:
    """Equal, or a rounding of a candidate (half unit of the value's shown precision), or ×1000/÷1000
    (VND vs thousand VND), or a percentage of a fraction."""
    value = abs(value)  # the sign is carried by words ("âm", "giảm"); tools store signed values

    def close(a: float, b: float) -> bool:
        if a == b:
            return True
        tol = max(0.5 * 10 ** -_decimals(value), 1e-9)
        return abs(a - b) <= tol + 1e-9
    for c in candidates:
        c = abs(c)
        for cand in (c, c * 100.0, c / 100.0, c * 1000.0, c / 1000.0, c * 1e9, c / 1e9, c * 1e6, c / 1e6):
            if close(value, cand) or (cand != 0 and abs(value - cand) / abs(cand) <= 5e-4):
                return True
    return False


def _decimals(v: float) -> int:
    s = repr(v)
    return len(s.split(".")[1]) if "." in s and not s.endswith(".0") else 0


def sse_ask(api_key: str, owner_id: str, question: str, symbol: str | None) -> dict:
    body = {"ownerId": owner_id, "question": question}
    if symbol:
        body["symbol"] = symbol
    req = urllib.request.Request(f"{AI_URL}/analyst/ask", data=json.dumps(body).encode("utf-8"), method="POST",
                                 headers={"Content-Type": "application/json", "X-Internal-Api-Key": api_key})
    events = []
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=180) as resp:
        for raw in resp:
            line = raw.decode("utf-8").rstrip("\n")
            if line.startswith("data: "):
                events.append(json.loads(line[6:]))
    return {"events": events, "elapsedMs": int((time.time() - t0) * 1000)}


def fetch_tool(api_key: str, owner_id: str, tool: str, args: dict):
    headers = {"X-Internal-Api-Key": api_key, "Content-Type": "application/json"}
    q = f"ownerId={owner_id}"
    sym = str(args.get("symbol", "")).upper()
    if tool == "MARKET":
        url, data = f"{BE_URL}/tools/market/overview?{q}", None
    elif tool == "STOCK":
        url, data = f"{BE_URL}/tools/stocks/{sym}?{q}", None
    elif tool == "TECHNICAL":
        url, data = f"{BE_URL}/tools/stocks/{sym}/technical?{q}", None
    elif tool == "FUNDAMENTAL":
        url, data = f"{BE_URL}/tools/stocks/{sym}/fundamentals?{q}", None
    elif tool == "VALUATION":
        url, data = f"{BE_URL}/tools/stocks/{sym}/valuation?{q}", None
    elif tool == "PORTFOLIO":
        ep = "analytics" if str(args.get("sub_type", "POSITIONS")).upper() == "ANALYTICS" else "positions"
        url, data = f"{BE_URL}/tools/portfolios/{ep}?{q}", None
    elif tool == "SCREENING":
        url, data = f"{BE_URL}/tools/screener/executions?{q}", json.dumps(args.get("filters", {})).encode("utf-8")
    elif tool == "NEWS":
        extra = "".join(f"&{k}={v}" for k, v in (("symbol", sym or None), ("limit", args.get("limit"))) if v)
        url, data = f"{BE_URL}/tools/research/news?{q}{extra}", None
    else:
        return {"_unsupportedInCapture": tool}
    req = urllib.request.Request(url, data=data, method="POST" if data is not None else "GET", headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"_httpError": e.code, "_body": e.read().decode("utf-8", "replace")[:500]}


BASIS_CHECKS = [
    # (predicate on tool response, wording fragments any of which must appear in the answer, label)
    (lambda r: json.dumps(r, ensure_ascii=False).find("ANNUAL_BASIS") >= 0,
     ["số liệu năm", "cơ sở năm", "theo năm", "báo cáo năm", "annual"], "ANNUAL_BASIS"),
    (lambda r: json.dumps(r, ensure_ascii=False).find("PROVIDER_TRAILING_EPS") >= 0,
     ["trailing", "nhà cung cấp", "12 tháng"], "PROVIDER_TRAILING_EPS"),
    (lambda r: json.dumps(r, ensure_ascii=False).find("REDUCED_METRIC_SET") >= 0,
     ["thu hẹp", "không áp dụng", "chỉ dựa", "chỉ số lõi", "P/B"], "REDUCED_METRIC_SET"),
    (lambda r: isinstance(r, dict) and "classification" in r and r.get("classification") is None,
     ["chưa được công bố", "chưa công bố", "không công bố", "tạm giữ", "chưa đủ", "không đủ", "không có định giá"], "VALUATION_WITHHELD"),
    (lambda r: isinstance(r, dict) and str(r.get("dataStatus", "CURRENT")).upper() not in ("CURRENT", ""),
     ["chậm", "trễ", "cũ", "một phần", "không có dữ liệu", "chưa đầy đủ", "theo phiên", "delayed", "stale", "partial"], "DATA_STATUS"),
]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "tools", "verification", "out"))
    ap.add_argument("--only", default="")
    args = ap.parse_args()
    ai_env = read_env(os.path.join(ROOT, "finvera-ai", ".env"))
    be_env = read_env(os.path.join(ROOT, "finvera-be", ".env"))
    api_key = ai_env["INTERNAL_API_KEY"]
    owner_id = be_env["FINVERA_OWNER_ID"]
    only = {f"Q{int(x):02d}" for x in args.only.split(",") if x.strip()} if args.only else None

    os.makedirs(args.out, exist_ok=True)
    today = dt.date.today().isoformat()
    record = {"capturedAt": dt.datetime.now().isoformat(timespec="seconds"), "questions": []}
    rows = []
    for qid, symbol, question, probe in QUESTIONS:
        if only and qid not in only:
            continue
        print(f"[{qid}] {question}", flush=True)
        run = sse_ask(api_key, owner_id, question, symbol)
        final = next((e["final"] for e in run["events"] if e.get("type") == "final"), None)
        tool_events = [e["toolCall"] for e in run["events"] if e.get("type") == "tool_call"]
        responses = []
        for tc in tool_events:
            if tc.get("status") == "SUCCEEDED":
                responses.append({"sequenceNo": tc["sequenceNo"], "toolName": tc["toolName"], "arguments": tc["arguments"],
                                  "response": fetch_tool(api_key, owner_id, tc["toolName"], tc.get("arguments") or {})})
        answer = (final or {}).get("answer", "")
        # number fidelity
        cands: list[float] = []
        for r in responses:
            numeric_leaves(r["response"], cands)
        for _raw, rs in parse_vi_numbers(question):   # thresholds the owner asked for may be restated
            cands.extend(rs)
        unsupported = [raw for raw, rs in parse_vi_numbers(answer)
                       if all(abs(v) >= SMALL for v in rs) and not any(supported(v, cands) for v in rs)]
        # basis disclosure
        misses = []
        low = answer.lower()
        for r in responses:
            for pred, words, label in BASIS_CHECKS:
                try:
                    hit = pred(r["response"])
                except Exception:
                    hit = False
                if hit and not any(w.lower() in low for w in words):
                    misses.append(f"{label}@T{r['sequenceNo']}:{r['toolName']}")
        entry = {"id": qid, "symbol": symbol, "question": question, "probe": probe, "elapsedMs": run["elapsedMs"],
                 "toolCalls": tool_events, "toolResponses": responses, "final": final,
                 "check": {"unsupportedNumbers": unsupported, "basisMisses": sorted(set(misses)),
                           "verifiedClaims": len((final or {}).get("structuredClaims", [])), "refused": (final or {}).get("refused")}}
        record["questions"].append(entry)
        rows.append((qid, ",".join(f"{t['toolName']}({t.get('arguments', {}).get('symbol', '') or ''})" for t in tool_events),
                     (final or {}).get("refused"), len((final or {}).get("structuredClaims", [])), len(unsupported), len(set(misses)),
                     run["elapsedMs"]))
        print(f"      tools={rows[-1][1]} refused={rows[-1][2]} claims={rows[-1][3]} unsupported={unsupported} basisMisses={sorted(set(misses))} {run['elapsedMs']} ms", flush=True)

    path = os.path.join(args.out, f"analyst_e2e_{today}.json")
    io.open(path, "w", encoding="utf-8").write(json.dumps(record, ensure_ascii=False, indent=1))
    print("\n| Q | tools | refused | verified claims | unsupported numbers | basis misses | ms |")
    print("|---|---|---|---|---|---|---|")
    for r in rows:
        print("| " + " | ".join(str(x) for x in r) + " |")
    print(f"\nrecording: {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
