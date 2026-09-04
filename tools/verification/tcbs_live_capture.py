"""Feature 013 research probe (specs/013-tcbs-live-field-audit, R-004): capture live TCBS Thesis
price-board frames during an open trading session and compare the consumed numeric fields against
an independent provider (VCI via vnstock).

Why this exists: every guard in the live path (`TcbsThesisFrameMapper`, `TcbsLiveEquityQuoteService`)
is unit-agnostic, so the contract's "the Thesis stream publishes base VND/share" claim cannot be
proven by reading code. Only a live frame next to an independent anchor can prove it -- and only
while the market is open, because the stream is silent outside a session.

Safety posture (constitution + contract `tcbs-thesis-private-live-v1`):
  * read-only: subscribes to the allowlisted index/price-board channels only, never trading,
    account, order, cash or portfolio channels;
  * secrets never touch stdout or the capture file -- the API key is read from finvera-be/.env,
    the OTP is typed at the prompt (or passed with --otp), and the exchanged JWT stays in memory;
  * the capture file holds market data and frame envelopes only.

Usage (during an open session, 09:00-11:30 / 13:00-14:45 Asia/Ho_Chi_Minh):

    uv run --project ../../tools/market-data/provider-poc python tools/verification/tcbs_live_capture.py \
        --symbols VNM,MBB,ACV --seconds 90

  or, from the repo root with the provider-poc environment already active:

    python tools/verification/tcbs_live_capture.py --symbols VNM,MBB,ACV --seconds 90

The OTP is a TOTP code from the owner's authenticator and is valid for a few tens of seconds, so
the script asks for it last, immediately before the token exchange.
"""
from __future__ import annotations

import argparse
import asyncio
import base64
import getpass
import io
import json
import os
import re
import sys
from collections import Counter
from datetime import UTC, datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
VN = timezone(timedelta(hours=7))
TOKEN_PATH = "/gaia/v1/oauth2/openapi/token"
INDEX_SUBSCRIPTION = "d|s|si|rt|1,2,3,5"
HEARTBEAT_FRAME = "d|p|||"
HEARTBEAT_SECONDS = 2.0
INDEX_CODES = {1: "VN_INDEX", 2: "VN30", 3: "HNX_INDEX", 5: "UPCOM_INDEX"}
# The provider symbol each Thesis index number must be compared against on VCI, and the unit the
# comparison expects. Index levels are points on both sides; equity prices are base VND on the
# Thesis side and thousand VND on the VCI board (export_daily_bars.normalize_board_price).
INDEX_ANCHOR_SYMBOL = {1: "VNINDEX", 2: "VN30", 3: "HNXINDEX", 5: "UPCOMINDEX"}
VCI_BOARD_PRICE_MULTIPLIER = Decimal("1000")
MAX_RAW_FRAMES = 4000
MAX_RECONNECTS = 3


def read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in io.open(path, encoding="utf-8"):
        if line.lstrip().startswith("#"):
            continue
        match = re.match(r"\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$", line)
        if match:
            values[match.group(1)] = match.group(2).strip().strip("\"'")
    return values


def exchange_token(base_url: str, api_key: str, otp: str) -> str:
    import httpx

    response = httpx.post(
        base_url.rstrip("/") + TOKEN_PATH,
        json={"apiKey": api_key, "otp": otp},
        timeout=20.0,
    )
    if response.status_code != 200:
        raise SystemExit(f"token exchange rejected: HTTP {response.status_code}")
    token = (response.json() or {}).get("token")
    if not token:
        raise SystemExit("token exchange returned no token")
    return token


def classify(raw: str) -> tuple[str, dict[str, Any] | None]:
    """Split a Thesis text frame into (data code, payload). Mirrors TcbsThesisFrameMapper.map."""
    if raw.startswith("d|"):
        return ("control:" + raw.split("|", 2)[1], None)
    parts = raw.split("|", 2)
    if len(parts) != 3 or parts[0] != "s":
        return ("unparseable", None)
    try:
        payload = json.loads(parts[2])
    except ValueError:
        return ("s|" + parts[1] + ":invalid-json", None)
    if not isinstance(payload, dict):
        return ("s|" + parts[1] + ":non-object", None)
    return ("s|" + parts[1], payload)


def observe_field(inventory: dict[str, dict[str, Any]], field: str, value: Any) -> None:
    entry = inventory.setdefault(
        field, {"count": 0, "nulls": 0, "jsonTypes": Counter(), "min": None, "max": None, "samples": []}
    )
    entry["count"] += 1
    if value is None:
        entry["nulls"] += 1
        entry["jsonTypes"]["null"] += 1
        return
    entry["jsonTypes"][type(value).__name__] += 1
    if len(entry["samples"]) < 5 and value not in entry["samples"]:
        entry["samples"].append(value)
    numeric: Decimal | None = None
    if isinstance(value, bool):
        numeric = None
    elif isinstance(value, (int, float)):
        numeric = Decimal(str(value))
    elif isinstance(value, str):
        try:
            numeric = Decimal(value)
        except Exception:  # noqa: BLE001 - a non-numeric string is a legitimate field value
            numeric = None
    if numeric is not None:
        entry["min"] = numeric if entry["min"] is None else min(entry["min"], numeric)
        entry["max"] = numeric if entry["max"] is None else max(entry["max"], numeric)


def jsonable(value: Any) -> Any:
    if isinstance(value, Decimal):
        return format(value, "f")
    if isinstance(value, Counter):
        return dict(value)
    if isinstance(value, dict):
        return {key: jsonable(item) for key, item in value.items()}
    if isinstance(value, list):
        return [jsonable(item) for item in value]
    return value


async def capture(websocket_url: str, token: str, symbols: list[str], seconds: float) -> dict[str, Any]:
    """Capture until the deadline, reconnecting with the same token if the stream drops."""
    raw_frames: list[dict[str, Any]] = []
    counts: Counter = Counter()
    inventory: dict[str, dict[str, dict[str, Any]]] = {}
    latest_equity: dict[str, dict[str, dict[str, Any]]] = {"s|4": {}, "s|6": {}}
    latest_index: dict[str, dict[str, Any]] = {}
    state = {
        "raw_frames": raw_frames, "counts": counts, "inventory": inventory,
        "latest_equity": latest_equity, "latest_index": latest_index,
        "auth_ok": False, "disconnects": [],
    }
    deadline = asyncio.get_running_loop().time() + seconds
    for attempt in range(MAX_RECONNECTS + 1):
        if asyncio.get_running_loop().time() >= deadline:
            break
        if attempt:
            print(f"  reconnecting (attempt {attempt}/{MAX_RECONNECTS})...")
            await asyncio.sleep(1)
        try:
            await stream_session(websocket_url, token, symbols, deadline, state)
        except Exception as error:  # noqa: BLE001 - a dropped stream must not lose the capture
            state["disconnects"].append(f"{type(error).__name__}: {error}")
            print(f"  stream dropped: {type(error).__name__}: {error}")

    return {
        "authenticated": state["auth_ok"],
        "disconnects": state["disconnects"],
        "frameCounts": dict(counts),
        "fieldInventory": inventory,
        "latestByFrame": {"s|4": latest_equity["s|4"], "s|6": latest_equity["s|6"], "s|8": latest_index},
        "rawFrames": raw_frames,
    }


async def stream_session(websocket_url: str, token: str, symbols: list[str], deadline: float,
                         state: dict[str, Any]) -> None:
    from websockets.asyncio.client import connect

    raw_frames = state["raw_frames"]
    counts = state["counts"]
    inventory = state["inventory"]
    latest_equity = state["latest_equity"]
    latest_index = state["latest_index"]

    # Transport settings mirror the production Java client (java.net.http.WebSocket): no
    # permessage-deflate and no automatic RFC ping frames. The Thesis server closes the connection
    # with 1002 (protocol error) when it receives an RFC ping -- it expects the application-level
    # `d|p|||` heartbeat instead, exactly as contract tcbs-thesis-private-live-v1 states.
    async with connect(websocket_url, open_timeout=15, close_timeout=5, max_queue=2048,
                       compression=None, ping_interval=None) as socket:
        await socket.send("d|a|||" + base64.b64encode(token.encode("utf-8")).decode("ascii"))

        async def heartbeat() -> None:
            while True:
                await asyncio.sleep(HEARTBEAT_SECONDS)
                await socket.send(HEARTBEAT_FRAME)

        heartbeat_task: asyncio.Task | None = None
        try:
            while True:
                remaining = deadline - asyncio.get_running_loop().time()
                if remaining <= 0:
                    break
                try:
                    raw = await asyncio.wait_for(socket.recv(), timeout=remaining)
                except TimeoutError:
                    break
                if isinstance(raw, bytes):
                    raw = raw.decode("utf-8", "replace")
                received_at = datetime.now(UTC)

                if raw.startswith("d|0|"):
                    try:
                        auth_ok = bool(json.loads(raw[4:]).get("success"))
                    except ValueError:
                        auth_ok = False
                    state["auth_ok"] = state["auth_ok"] or auth_ok
                    if not auth_ok:
                        raise SystemExit("TCBS authentication rejected (control frame d|0|)")
                    await socket.send(INDEX_SUBSCRIPTION)
                    if symbols:
                        await socket.send("d|s|tk|bp+tm|" + ",".join(symbols))
                    heartbeat_task = asyncio.create_task(heartbeat())
                    counts["control:0"] += 1
                    continue

                code, payload = classify(raw)
                counts[code] += 1
                if len(raw_frames) < MAX_RAW_FRAMES:
                    raw_frames.append({"receivedAtUtc": received_at.isoformat().replace("+00:00", "Z"), "raw": raw})
                if payload is None:
                    continue
                fields = inventory.setdefault(code, {})
                for field, value in payload.items():
                    observe_field(fields, field, value)
                if code in latest_equity:
                    symbol = str(payload.get("symbol", "")).upper()
                    if symbol:
                        latest_equity[code][symbol] = {
                            "receivedAtUtc": received_at.isoformat().replace("+00:00", "Z"),
                            "payload": payload,
                        }
                elif code == "s|8":
                    number = payload.get("indexNumber")
                    if number is not None:
                        latest_index[str(number)] = {
                            "receivedAtUtc": received_at.isoformat().replace("+00:00", "Z"),
                            "payload": payload,
                        }
        finally:
            if heartbeat_task is not None:
                heartbeat_task.cancel()


def vci_anchor(symbols: list[str], index_numbers: list[int]) -> dict[str, Any]:
    """Independent anchor: the same instruments read from VCI (a different provider) right after
    the capture. Equity closes are converted to base VND exactly like the daily-bar exporter."""
    from vnstock import Quote

    today = datetime.now(VN).date().isoformat()
    out: dict[str, Any] = {"providedBy": "vnstock/VCI", "asOfVnDate": today, "equities": {}, "indexes": {}}

    def last_close(provider_symbol: str, interval: str) -> dict[str, Any] | None:
        try:
            frame = Quote(symbol=provider_symbol, source="vci").history(
                start=today, end=today, interval=interval
            )
        except Exception as error:  # noqa: BLE001 - anchor is best-effort; capture still stands
            return {"error": f"{type(error).__name__}: {error}"}
        if frame is None or len(frame) == 0 or "close" not in frame.columns:
            return None
        row = frame.iloc[-1]
        return {"time": str(row["time"]), "close": Decimal(str(row["close"]))}

    for symbol in symbols:
        point = last_close(symbol, "1m") or last_close(symbol, "1D")
        if point and "close" in point:
            point["closeBaseVnd"] = point["close"] * VCI_BOARD_PRICE_MULTIPLIER
        out["equities"][symbol] = point
    for number in index_numbers:
        provider_symbol = INDEX_ANCHOR_SYMBOL.get(number)
        if provider_symbol is None:
            continue
        out["indexes"][str(number)] = {"providerSymbol": provider_symbol} | (
            last_close(provider_symbol, "1m") or last_close(provider_symbol, "1D") or {}
        )
    return out


def ticker_commons(base_url: str, token: str, symbols: list[str]) -> dict[str, Any]:
    """The REST snapshot `TcbsLiveEquityQuoteService.fetchSnapshotIfMissing` consumes. Its fields
    (refPrice, ceilPrice, floorPrice, open/high/low, room) are stored with NO unit conversion, so
    they must be in the same base-VND unit as the stream's matchPrice."""
    import httpx

    path = "/tartarus/v1/tickerCommons?tickers=" + ",".join(symbols)
    try:
        response = httpx.get(
            base_url.rstrip("/") + path,
            headers={"Authorization": "Bearer " + token, "Accept": "application/json"},
            timeout=20.0,
        )
    except Exception as error:  # noqa: BLE001 - the stream capture must survive a REST failure
        return {"error": f"{type(error).__name__}: {error}"}
    if response.status_code != 200:
        return {"error": f"HTTP {response.status_code}", "path": path}
    body = response.json()
    return {"path": path, "tradingDate": (body or {}).get("tradingDate"), "data": (body or {}).get("data")}


def compare_rest(rest: dict[str, Any], captured: dict[str, Any], anchor: dict[str, Any]) -> list[dict[str, Any]]:
    """Every tickerCommons price field against the same instrument's base-VND anchor."""
    rows: list[dict[str, Any]] = []
    for item in rest.get("data") or []:
        symbol = str(item.get("symbol", "")).upper()
        reference = (anchor.get("equities") or {}).get(symbol) or {}
        anchor_price = reference.get("closeBaseVnd")
        stream = (captured["latestByFrame"]["s|6"].get(symbol) or {}).get("payload") or {}
        stream_price = stream.get("matchPrice")
        basis = anchor_price if anchor_price is not None else (
            Decimal(str(stream_price)) if stream_price is not None else None
        )
        for field in ("matchPrice", "refPrice", "ceilPrice", "floorPrice", "open", "high", "low"):
            value = item.get(field)
            row: dict[str, Any] = {
                "kind": "rest",
                "instrument": f"{symbol}.{field}",
                "thesisField": f"tickerCommons.{field}",
                "thesisValue": value,
                "anchorBaseVnd": basis,
            }
            if value is None or basis in (None, 0):
                row["verdict"] = "NO_DATA"
            else:
                ratio = (Decimal(str(value)) / basis).quantize(Decimal("0.000001"))
                row["ratioThesisOverAnchor"] = ratio
                # ceil/floor sit up to +-7% (HOSE), +-10% (HNX), +-15% (UPCoM) from the reference,
                # so a same-unit verdict here is about magnitude, not equality.
                row["verdict"] = unit_verdict(ratio) if field in ("matchPrice", "refPrice") \
                    else magnitude_verdict(ratio)
            rows.append(row)
    return rows


def magnitude_verdict(ratio: Decimal) -> str:
    if Decimal("0.7") <= ratio <= Decimal("1.3"):
        return "SAME_UNIT"
    if Decimal("700") <= ratio <= Decimal("1300"):
        return "REST_IS_1000x_ANCHOR"
    if Decimal("0.0007") <= ratio <= Decimal("0.0013"):
        return "REST_IS_ANCHOR/1000"
    return "UNEXPECTED_RATIO"


def compare(captured: dict[str, Any], anchor: dict[str, Any]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for symbol, entry in captured["latestByFrame"]["s|6"].items():
        thesis = entry["payload"].get("matchPrice")
        reference = anchor["equities"].get(symbol) or {}
        vci = reference.get("closeBaseVnd")
        row: dict[str, Any] = {
            "kind": "equity",
            "instrument": symbol,
            "thesisField": "s|6.matchPrice",
            "thesisValue": thesis,
            "anchorBaseVnd": vci,
        }
        if thesis is not None and vci is not None:
            try:
                thesis_decimal = Decimal(str(thesis))
                row["ratioThesisOverAnchor"] = (
                    (thesis_decimal / vci).quantize(Decimal("0.000001")) if vci != 0 else None
                )
                row["verdict"] = unit_verdict(row["ratioThesisOverAnchor"])
            except Exception as error:  # noqa: BLE001
                row["verdict"] = f"UNCOMPARABLE ({type(error).__name__})"
        else:
            row["verdict"] = "NO_DATA"
        rows.append(row)
    for number, entry in captured["latestByFrame"]["s|8"].items():
        thesis = entry["payload"].get("index")
        reference = anchor["indexes"].get(number) or {}
        level = reference.get("close")
        row = {
            "kind": "index",
            "instrument": f"{number} ({INDEX_CODES.get(int(number), 'UNKNOWN')})",
            "thesisField": "s|8.index",
            "thesisValue": thesis,
            "anchorBaseVnd": level,
        }
        if thesis is not None and level is not None:
            thesis_decimal = Decimal(str(thesis))
            row["ratioThesisOverAnchor"] = (
                (thesis_decimal / level).quantize(Decimal("0.000001")) if level != 0 else None
            )
            row["verdict"] = unit_verdict(row["ratioThesisOverAnchor"])
        else:
            row["verdict"] = "NO_DATA"
        rows.append(row)
    return rows


def unit_verdict(ratio: Decimal | None) -> str:
    """Intraday values move, so a same-unit pair is 'close to 1', never exactly 1. A unit defect
    shows up as a factor of ~1000 or ~0.001, which no amount of intraday drift can produce."""
    if ratio is None:
        return "NO_DATA"
    if Decimal("0.9") <= ratio <= Decimal("1.1"):
        return "SAME_UNIT"
    if Decimal("900") <= ratio <= Decimal("1100"):
        return "THESIS_IS_1000x_ANCHOR"
    if Decimal("0.0009") <= ratio <= Decimal("0.0011"):
        return "THESIS_IS_ANCHOR/1000"
    return "UNEXPECTED_RATIO"


def main() -> None:
    parser = argparse.ArgumentParser(description="Read-only live capture of TCBS Thesis frames (Feature 013 R-004)")
    parser.add_argument("--symbols", default="VNM,MBB,ACV")
    parser.add_argument("--seconds", type=float, default=90.0)
    parser.add_argument("--otp", default=None, help="TOTP code; omitted means an interactive prompt")
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument("--no-anchor", action="store_true", help="skip the VCI cross-provider anchor")
    parser.add_argument("--no-rest", action="store_true", help="skip the tickerCommons REST snapshot")
    parser.add_argument("--rest-only", action="store_true", help="tickerCommons only; no stream capture")
    args = parser.parse_args()

    env = read_env(ROOT / "finvera-be" / ".env")
    api_key = env.get("FINVERA_TCBS_API_KEY", "")
    base_url = env.get("FINVERA_TCBS_BASE_URL") or "https://openapi.tcbs.com.vn"
    websocket_url = env.get("FINVERA_TCBS_WEBSOCKET_URL") or "wss://openapi.tcbs.com.vn/ws/thesis/v1/stream/normal"
    if not api_key:
        raise SystemExit("FINVERA_TCBS_API_KEY is not set in finvera-be/.env")

    symbols = [s.strip().upper() for s in args.symbols.split(",") if s.strip()]
    for symbol in symbols:
        if not re.fullmatch(r"[A-Z0-9]{1,10}", symbol):
            raise SystemExit(f"invalid ticker: {symbol}")

    now_vn = datetime.now(VN)
    print(f"Local market time: {now_vn.isoformat()} (capture {args.seconds:.0f}s, symbols {','.join(symbols)})")
    otp = args.otp or getpass.getpass("TCBS OTP (not echoed, not logged): ")
    token = exchange_token(base_url, api_key, otp.strip())
    print("Token exchanged; connecting to the Thesis stream...")

    if args.rest_only:
        captured = {"authenticated": True, "disconnects": [], "frameCounts": {}, "fieldInventory": {},
                    "latestByFrame": {"s|4": {}, "s|6": {}, "s|8": {}}, "rawFrames": []}
    else:
        captured = asyncio.run(capture(websocket_url, token, symbols, args.seconds))

    rest = {} if args.no_rest else ticker_commons(base_url, token, symbols)
    del token

    anchor = {} if args.no_anchor else vci_anchor(symbols, [int(n) for n in captured["latestByFrame"]["s|8"]])
    comparison = compare(captured, anchor) if anchor else []
    if rest and not rest.get("error"):
        comparison += compare_rest(rest, captured, anchor)

    document = {
        "probe": "specs/013-tcbs-live-field-audit R-004",
        "capturedAtVn": now_vn.isoformat(),
        "captureSeconds": args.seconds,
        "websocketUrl": websocket_url,
        "subscriptions": [INDEX_SUBSCRIPTION, "d|s|tk|bp+tm|" + ",".join(symbols)],
        "authenticated": captured["authenticated"],
        "disconnects": captured["disconnects"],
        "frameCounts": captured["frameCounts"],
        "fieldInventory": captured["fieldInventory"],
        "latestByFrame": captured["latestByFrame"],
        "tickerCommons": rest,
        "anchor": anchor,
        "comparison": comparison,
        "rawFrames": captured["rawFrames"],
    }
    output = args.output or (
        ROOT / "tools" / "verification" / "out" / f"tcbs_live_capture_{now_vn.strftime('%Y-%m-%dT%H%M')}.json"
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(jsonable(document), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"\nFrames: {captured['frameCounts']}")
    for code, fields in sorted(captured["fieldInventory"].items()):
        print(f"\n{code} fields ({len(fields)}):")
        for field, entry in sorted(fields.items()):
            span = ""
            if entry["min"] is not None:
                span = f"  range=[{format(entry['min'], 'f')} .. {format(entry['max'], 'f')}]"
            print(f"  {field:<18} n={entry['count']:<6} types={dict(entry['jsonTypes'])}{span}")
    if comparison:
        print("\nCross-provider unit check (TCBS Thesis vs VCI):")
        print(f"  {'instrument':<18}{'thesis':>16}{'anchor':>16}{'ratio':>14}  verdict")
        for row in comparison:
            ratio = row.get("ratioThesisOverAnchor")
            print(
                f"  {str(row['instrument']):<18}{str(row['thesisValue']):>16}"
                f"{('' if row['anchorBaseVnd'] is None else format(row['anchorBaseVnd'], 'f')):>16}"
                f"{('' if ratio is None else format(ratio, 'f')):>14}  {row['verdict']}"
            )
    print(f"\nWrote capture: {output}")
    if not captured["authenticated"]:
        print("WARNING: no successful authentication frame was observed.", file=sys.stderr)


if __name__ == "__main__":
    main()
