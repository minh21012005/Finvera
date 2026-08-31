"""Manual, local-only Vnstock full-OHLCV daily-bar exporter for Feature 002's
stock detail chart (specs/002-stock-detail-analysis, R-002/R-004: a dedicated
daily-bar table, RAW/unadjusted only per the owner-accepted G-02 decision).

Sibling to export_history.py (Feature 001's close-only index/regime bootstrap,
already gated/approved) — kept as a separate script rather than extending that
one so Feature 001's already-approved contract and behavior are never touched.
Same safety posture: never connects to PostgreSQL, never logs credentials, and
the generated package is gitignored and must be reviewed before import.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import UTC, date, datetime, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any

CONTRACT_VERSION = "vnstock-daily-bar-v1"
SOURCE = "VNSTOCK_VCI"  # ADR-0013 (Feature 021): single provider; KBS path retained in git history as fallback
TOOL_VERSION = "1.0.0"  # 1.0.0: VCI source, PROVIDER_ADJUSTED label, range clamped on both ends
MIN_RECORDS = 20
BOARD_PRICE_MULTIPLIER = Decimal("1000")  # VCI quotes equity prices in thousand VND, same board unit as KBS
# Feature 011 research R-001 (KBS) / Feature 021 research R-002 (VCI): pad the requested end and
# cut back ourselves; VCI additionally returns a buffer of sessions BEFORE the requested start, so
# both ends are clamped in fetch_rows.
END_PADDING_DAYS = 3


def decimal_string(value: Any) -> str:
    decimal = Decimal(str(value))
    if decimal.is_nan() or decimal.is_infinite() or decimal < 0:
        raise ValueError("price/volume fields must be finite non-negative decimals")
    return format(decimal.quantize(Decimal("0.000001")), "f")


def normalize_board_price(value: Any) -> Decimal:
    """Provider OHLCV prices are quoted in Vietnamese board units (thousand VND).

    Finvera's stock data model and API expose equity prices in base VND/share.
    The provider-unit conversion belongs at the exporter/provider boundary so
    every downstream calculation receives a single canonical unit.
    """
    decimal = Decimal(str(value))
    if decimal.is_nan() or decimal.is_infinite() or decimal < 0:
        raise ValueError("price fields must be finite non-negative decimals")
    return decimal * BOARD_PRICE_MULTIPLIER


def canonical_json(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def package_records(rows: list[dict[str, Any]], symbol: str) -> list[dict[str, Any]]:
    records = []
    for row in rows:
        trading_date = str(row["time"]).split(" ", maxsplit=1)[0]
        open_price = normalize_board_price(row["open"])
        high_price = normalize_board_price(row["high"])
        low_price = normalize_board_price(row["low"])
        close_price = normalize_board_price(row["close"])
        volume = Decimal(str(row["volume"])) if row.get("volume") is not None else None
        record = {
            "adjustmentStatus": "PROVIDER_ADJUSTED",  # VCI serves corporate-action-adjusted series (specs/021 research R-002)
            "canonicalRecord": "",
            "close": decimal_string(close_price),
            "high": decimal_string(high_price),
            "low": decimal_string(low_price),
            "observedAt": f"{trading_date}T08:00:00Z",
            "open": decimal_string(open_price),
            "symbol": symbol.upper(),
            "tradingDate": trading_date,
            "valueVnd": decimal_string(close_price * volume) if volume is not None else None,
            "volume": decimal_string(volume) if volume is not None else None,
        }
        record["canonicalRecord"] = canonical_json({key: value for key, value in record.items() if key != "canonicalRecord"})
        records.append(record)
    return records


def build_package(records: list[dict[str, Any]], symbol: str, start: str, end: str, tool_version: str) -> dict[str, Any]:
    if len(records) < MIN_RECORDS:
        raise ValueError(f"at least {MIN_RECORDS} completed sessions are required")
    records = sorted(records, key=lambda item: item["tradingDate"])
    payload = {"records": records}
    payload_json = canonical_json(payload)
    return {
        "contractVersion": CONTRACT_VERSION, "toolName": "finvera-vnstock-exporter",
        "toolVersion": tool_version, "upstreamSource": SOURCE, "symbol": symbol.upper(),
        "generatedAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "rangeStart": start, "rangeEnd": end,
        "packageSha256": hashlib.sha256(payload_json.encode()).hexdigest(),
        "canonicalPayload": payload_json, "records": records,
    }


def fetch_rows(symbol: str, start: str, end: str) -> list[dict[str, Any]]:
    from vnstock import Quote

    padded_end = (date.fromisoformat(end) + timedelta(days=END_PADDING_DAYS)).isoformat()
    frame = Quote(symbol=symbol, source="vci").history(start=start, end=padded_end, interval="1D")
    required = {"time", "open", "high", "low", "close"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock OHLCV schema does not contain the required OHLC columns")
    # Historical time/OHLCV only; no similarly named quote/reference fields are read. EOD breadth
    # uses the prior accepted close as its comparison basis.
    columns = [c for c in (
        "time", "open", "high", "low", "close", "volume",
    ) if c in frame.columns]
    rows = frame.loc[:, columns].to_dict("records")
    # Clamp BOTH ends: padding must never let a bar after `end` through, and VCI returns a buffer
    # of sessions before the requested `start` (specs/021 research R-002).
    return [row for row in rows if start <= str(row["time"]).split(" ", maxsplit=1)[0] <= end]


def output_filename(symbol: str) -> str:
    """Stable per symbol -- deliberately does NOT include the date range. Re-running this exporter
    (e.g. daily, to pick up new trading sessions) must overwrite the same file with the latest full
    range rather than accumulating one dated file per run forever with no way to tell which one a
    consumer should import."""
    return f"daily-bars-{symbol.lower()}.json"


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a local-only canonical Vnstock daily-bar package")
    parser.add_argument("--symbol", required=True)
    parser.add_argument("--start", required=True)
    parser.add_argument("--end", required=True)
    parser.add_argument("--output", type=Path, default=Path("output"))
    args = parser.parse_args()
    records = package_records(fetch_rows(args.symbol, args.start, args.end), args.symbol)
    package = build_package(records, args.symbol, args.start, args.end, TOOL_VERSION)
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / output_filename(args.symbol)
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote canonical package: {path}")
    print(f"Package SHA-256: {package['packageSha256']}")


if __name__ == "__main__":
    main()
