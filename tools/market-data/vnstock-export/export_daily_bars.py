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
from datetime import UTC, datetime
from decimal import Decimal
from pathlib import Path
from typing import Any

CONTRACT_VERSION = "vnstock-daily-bar-v1"
SOURCE = "VNSTOCK_KBS"
MIN_RECORDS = 20


def decimal_string(value: Any) -> str:
    decimal = Decimal(str(value))
    if decimal.is_nan() or decimal.is_infinite() or decimal < 0:
        raise ValueError("price/volume fields must be finite non-negative decimals")
    return format(decimal.quantize(Decimal("0.000001")), "f")


def canonical_json(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def package_records(rows: list[dict[str, Any]], symbol: str) -> list[dict[str, Any]]:
    records = []
    for row in rows:
        trading_date = str(row["time"]).split(" ", maxsplit=1)[0]
        record = {
            "adjustmentStatus": "RAW",
            "canonicalRecord": "",
            "close": decimal_string(row["close"]),
            "high": decimal_string(row["high"]),
            "low": decimal_string(row["low"]),
            "observedAt": f"{trading_date}T08:00:00Z",
            "open": decimal_string(row["open"]),
            "symbol": symbol.upper(),
            "tradingDate": trading_date,
            "valueVnd": decimal_string(row["close"] * row["volume"]) if row.get("volume") else None,
            "volume": decimal_string(row["volume"]) if row.get("volume") is not None else None,
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
    from vnstock import Market

    frame = Market().equity(symbol).ohlcv(start=start, end=end, interval="1D", count=1000, source="kbs")
    required = {"time", "open", "high", "low", "close"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock OHLCV schema does not contain the required OHLC columns")
    columns = [c for c in ("time", "open", "high", "low", "close", "volume") if c in frame.columns]
    return frame.loc[:, columns].to_dict("records")


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a local-only canonical Vnstock daily-bar package")
    parser.add_argument("--symbol", required=True)
    parser.add_argument("--start", required=True)
    parser.add_argument("--end", required=True)
    parser.add_argument("--output", type=Path, default=Path("output"))
    args = parser.parse_args()
    records = package_records(fetch_rows(args.symbol, args.start, args.end), args.symbol)
    package = build_package(records, args.symbol, args.start, args.end, "0.1.0")
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / f"daily-bars-{args.symbol.lower()}-{args.start}-{args.end}.json"
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote canonical package: {path}")
    print(f"Package SHA-256: {package['packageSha256']}")


if __name__ == "__main__":
    main()
