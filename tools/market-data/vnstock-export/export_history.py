"""Manual, local-only Vnstock historical-package exporter.

This tool never connects to PostgreSQL and never logs credentials or prices.
The generated package is intentionally gitignored and must be reviewed before
being supplied to Finvera's internal import boundary.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import UTC, datetime
from decimal import Decimal
from pathlib import Path
from typing import Any

CONTRACT_VERSION = "vnstock-history-private-bootstrap-v1"
SOURCE = "VNSTOCK_KBS"


def decimal_string(value: Any) -> str:
    decimal = Decimal(str(value))
    if decimal.is_nan() or decimal.is_infinite() or decimal < 0:
        raise ValueError("close must be a finite non-negative decimal")
    return format(decimal.quantize(Decimal("0.000001")), "f")


def canonical_json(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def package_records(rows: list[dict[str, Any]], venue: str, symbol: str, listed_from: str) -> list[dict[str, Any]]:
    records = []
    for row in rows:
        trading_date = str(row["time"]).split(" ", maxsplit=1)[0]
        close = decimal_string(row["close"])
        record = {
            "adjustmentStatus": "RAW", "canonicalRecord": "", "closePrice": close,
            "instrumentStatus": "UNKNOWN", "isin": None, "listedFrom": listed_from,
            "observedAt": f"{trading_date}T08:00:00Z", "sourceSequence": None,
            "symbol": symbol.upper(), "tradingDate": trading_date, "venue": venue,
        }
        record["canonicalRecord"] = canonical_json({key: value for key, value in record.items() if key != "canonicalRecord"})
        records.append(record)
    return records


def build_package(records: list[dict[str, Any]], start: str, end: str, tool_version: str) -> dict[str, Any]:
    if len(records) < 271:
        raise ValueError("at least 271 completed sessions are required")
    records = sorted(records, key=lambda item: (item["venue"], item["symbol"], item["tradingDate"]))
    payload = {"records": records}
    payload_json = canonical_json(payload)
    return {
        "contractVersion": CONTRACT_VERSION, "toolName": "finvera-vnstock-exporter",
        "toolVersion": tool_version, "upstreamSource": SOURCE,
        "generatedAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "rangeStart": start, "rangeEnd": end,
        "packageSha256": hashlib.sha256(payload_json.encode()).hexdigest(),
        "canonicalPayload": payload_json, "records": records,
    }


def fetch_rows(symbol: str, start: str, end: str) -> list[dict[str, Any]]:
    from vnstock import Market

    frame = Market().equity(symbol).ohlcv(start=start, end=end, interval="1D", count=1000, source="kbs")
    required = {"time", "close"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock OHLCV schema does not contain time and close")
    return frame.loc[:, ["time", "close"]].to_dict("records")


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a local-only canonical Vnstock history package")
    parser.add_argument("--symbol", required=True)
    parser.add_argument("--venue", required=True, choices=("HOSE", "HNX", "UPCOM"))
    parser.add_argument("--start", required=True)
    parser.add_argument("--end", required=True)
    parser.add_argument("--output", type=Path, default=Path("output"))
    args = parser.parse_args()
    records = package_records(fetch_rows(args.symbol, args.start, args.end), args.venue, args.symbol, args.start)
    package = build_package(records, args.start, args.end, "0.1.0")
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / f"{args.venue.lower()}-{args.symbol.lower()}-{args.start}-{args.end}.json"
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote canonical package: {path}")
    print(f"Package SHA-256: {package['packageSha256']}")


if __name__ == "__main__":
    main()
