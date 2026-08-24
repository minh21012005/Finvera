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
MARKET_PACKAGE_CONTRACT_VERSION = "vnstock-market-private-package-v1"
SOURCE = "VNSTOCK_KBS"
DEFAULT_INDEXES = (
    ("VN_INDEX", "VNINDEX", "HOSE"),
    ("VN30", "VN30", "HOSE"),
    ("HNX_INDEX", "HNXINDEX", "HNX"),
    ("UPCOM_INDEX", "UPCOMINDEX", "UPCOM"),
)


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


def integer_string(value: Any) -> str | None:
    if value is None:
        return None
    decimal = Decimal(str(value))
    if decimal.is_nan() or decimal.is_infinite() or decimal < 0:
        return None
    integral = decimal.to_integral_value()
    return str(int(integral)) if integral == decimal else None


def index_records(rows: list[dict[str, Any]], code: str, provider_symbol: str) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    sorted_rows = sorted(rows, key=lambda row: str(row["time"]))
    previous_close: str | None = None
    for row in sorted_rows:
        trading_date = str(row["time"]).split(" ", maxsplit=1)[0]
        close = decimal_string(row["close"])
        if previous_close is None:
            previous_close = close
            continue
        record = {
            "canonicalRecord": "",
            "code": code,
            "dataStatus": "CURRENT",
            "level": close,
            "matchedValueVnd": None,
            "matchedVolume": integer_string(row.get("volume")),
            "observedAt": f"{trading_date}T08:00:00Z",
            "providerSymbol": provider_symbol.upper(),
            "reasonCodes": ["VNSTOCK_DAILY_CLOSE_REFERENCE_DERIVED"],
            "referenceLevel": previous_close,
            "sessionState": "CLOSED",
            "tradingDate": trading_date,
        }
        record["canonicalRecord"] = canonical_json({key: value for key, value in record.items() if key != "canonicalRecord"})
        records.append(record)
        previous_close = close
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


def build_market_package(
    equity_records: list[dict[str, Any]], index_snapshot_records: list[dict[str, Any]],
    start: str, end: str, tool_version: str
) -> dict[str, Any]:
    if not equity_records and not index_snapshot_records:
        raise ValueError("at least one equity or index record is required")
    equity_records = sorted(equity_records, key=lambda item: (item["venue"], item["symbol"], item["tradingDate"]))
    index_snapshot_records = sorted(index_snapshot_records, key=lambda item: (item["code"], item["tradingDate"]))
    payload = {"indexRecords": index_snapshot_records, "records": equity_records}
    payload_json = canonical_json(payload)
    return {
        "contractVersion": MARKET_PACKAGE_CONTRACT_VERSION,
        "toolName": "finvera-vnstock-exporter",
        "toolVersion": tool_version,
        "upstreamSource": SOURCE,
        "generatedAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "rangeStart": start,
        "rangeEnd": end,
        "packageSha256": hashlib.sha256(payload_json.encode()).hexdigest(),
        "canonicalPayload": payload_json,
        "records": equity_records,
        "indexRecords": index_snapshot_records,
    }


def fetch_rows(symbol: str, start: str, end: str) -> list[dict[str, Any]]:
    from vnstock import Market

    frame = Market().equity(symbol).ohlcv(start=start, end=end, interval="1D", count=1000, source="kbs")
    required = {"time", "close"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock OHLCV schema does not contain time and close")
    return frame.loc[:, ["time", "close"]].to_dict("records")


def fetch_index_rows(symbol: str, start: str, end: str) -> list[dict[str, Any]]:
    from vnstock import Market

    frame = Market().index(symbol).ohlcv(start=start, end=end, interval="1D", count=1000, source="kbs")
    required = {"time", "close"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock index OHLCV schema does not contain time and close")
    columns = ["time", "close"] + (["volume"] if "volume" in frame.columns else [])
    return frame.loc[:, columns].to_dict("records")


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a local-only canonical Vnstock history package")
    parser.add_argument("--symbol")
    parser.add_argument("--venue", choices=("HOSE", "HNX", "UPCOM"))
    parser.add_argument("--start", required=True)
    parser.add_argument("--end", required=True)
    parser.add_argument("--output", type=Path, default=Path("output"))
    parser.add_argument("--market-overview", action="store_true",
                        help="Export the four configured market indices as indexRecords")
    args = parser.parse_args()
    if args.market_overview:
        index_snapshot_records: list[dict[str, Any]] = []
        for code, provider_symbol, _venue in DEFAULT_INDEXES:
            index_snapshot_records.extend(index_records(fetch_index_rows(provider_symbol, args.start, args.end), code, provider_symbol))
        package = build_market_package([], index_snapshot_records, args.start, args.end, "0.2.0")
        filename = f"market-overview-{args.start}-{args.end}.json"
    else:
        if not args.symbol or not args.venue:
            parser.error("--symbol and --venue are required unless --market-overview is set")
        records = package_records(fetch_rows(args.symbol, args.start, args.end), args.venue, args.symbol, args.start)
        package = build_package(records, args.start, args.end, "0.1.0")
        filename = f"{args.venue.lower()}-{args.symbol.lower()}-{args.start}-{args.end}.json"
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / filename
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote canonical package: {path}")
    print(f"Package SHA-256: {package['packageSha256']}")


if __name__ == "__main__":
    main()
