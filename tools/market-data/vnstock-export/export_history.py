"""Manual, local-only Vnstock historical-package exporter.

This tool never connects to PostgreSQL and never logs credentials or prices.
The generated package is intentionally gitignored and must be reviewed before
being supplied to Finvera's internal import boundary.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import UTC, date, datetime, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any

import provider_retry

CONTRACT_VERSION = "vnstock-history-private-bootstrap-v1"
MARKET_PACKAGE_CONTRACT_VERSION = "vnstock-market-private-package-v1"
SOURCE = "VNSTOCK_VCI"  # ADR-0013 (Feature 021)
DEFAULT_INDEXES = (
    ("VN_INDEX", "VNINDEX", "HOSE"),
    ("VN30", "VN30", "HOSE"),
    ("HNX_INDEX", "HNXINDEX", "HNX"),
    ("UPCOM_INDEX", "UPCOMINDEX", "UPCOM"),
)
MARKET_OVERVIEW_FETCH_REFERENCE_DAYS = 10


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
            "adjustmentStatus": "PROVIDER_ADJUSTED", "canonicalRecord": "", "closePrice": close,
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


def market_overview_filename() -> str:
    """Stable market-overview package filename.

    Re-running this exporter must behave like daily-bar packages: merge new
    records into one known local package instead of accumulating dated files that
    the backend operator then has to choose between.
    """
    return "market-overview.json"


def latest_market_overview_package(output: Path, start: str) -> Path | None:
    stable = output / market_overview_filename()
    if stable.exists():
        return stable
    candidates = sorted(output.glob(f"market-overview-{start}-*.json"))
    return candidates[-1] if candidates else None


def existing_index_range_start(output: Path, start: str) -> str | None:
    path = latest_market_overview_package(output, start)
    if path is None:
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8")).get("rangeStart")
    except (json.JSONDecodeError, OSError):
        return None


def load_existing_index_records(output: Path, start: str) -> list[dict[str, Any]]:
    path = latest_market_overview_package(output, start)
    if path is None:
        return []
    try:
        package = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return []
    if package.get("upstreamSource") != SOURCE:
        return []  # FR-002 (ADR-0013): a package captured from another provider is re-fetched in full, never extended
    if package.get("contractVersion") != MARKET_PACKAGE_CONTRACT_VERSION:
        return []
    records = package.get("indexRecords", [])
    if not isinstance(records, list):
        return []
    return [record for record in records if isinstance(record, dict)]


def incremental_market_index_records(
    start: str, end: str, output: Path, lookback_days: int, full_refresh: bool
) -> list[dict[str, Any]]:
    if lookback_days < 0:
        raise ValueError("lookback-days must be non-negative")
    existing_records = [] if full_refresh else load_existing_index_records(output, start)
    if existing_records and (existing_index_range_start(output, start) or start) > start:
        existing_records = []  # Q-37: --start moved earlier than the stored range; re-fetch once
    fetch_start = start
    if existing_records:
        existing_end = max(str(record["tradingDate"]) for record in existing_records)
        cutoff = date.fromisoformat(existing_end) - timedelta(days=lookback_days)
        reference_start = cutoff - timedelta(days=MARKET_OVERVIEW_FETCH_REFERENCE_DAYS)
        fetch_start = max(start, reference_start.isoformat())

    new_records: list[dict[str, Any]] = []
    for code, provider_symbol, _venue in DEFAULT_INDEXES:
        new_records.extend(index_records(fetch_index_rows(provider_symbol, fetch_start, end), code, provider_symbol))

    merged = {(record["code"], record["tradingDate"]): record for record in existing_records}
    merged.update({(record["code"], record["tradingDate"]): record for record in new_records})
    return sorted(merged.values(), key=lambda item: (item["code"], item["tradingDate"]))


def fetch_rows(symbol: str, start: str, end: str) -> list[dict[str, Any]]:
    from vnstock import Quote

    frame = provider_retry.call(f"history {symbol}",
                                lambda: Quote(symbol=symbol, source="vci").history(
                                    start=start, end=padded_end(end), interval="1D"))
    required = {"time", "close"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock OHLCV schema does not contain time and close")
    return cut_to_range(frame.loc[:, ["time", "close"]].to_dict("records"), start, end)


def fetch_index_rows(symbol: str, start: str, end: str) -> list[dict[str, Any]]:
    from vnstock import Quote

    frame = provider_retry.call(f"index history {symbol}",
                                lambda: Quote(symbol=symbol, source="vci").history(
                                    start=start, end=padded_end(end), interval="1D"))
    required = {"time", "close"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock index OHLCV schema does not contain time and close")
    columns = ["time", "close"] + (["volume"] if "volume" in frame.columns else [])
    return cut_to_range(frame.loc[:, columns].to_dict("records"), start, end)


# Feature 011 research R-001: KBS treats `end` as non-inclusive (end=Fri -> last bar Thu). Same
# padding as export_daily_bars; nothing after the requested end is ever kept.
KBS_END_PADDING_DAYS = 3


def padded_end(end: str) -> str:
    return (date.fromisoformat(end) + timedelta(days=KBS_END_PADDING_DAYS)).isoformat()


def cut_to_range(rows: list[dict[str, Any]], start: str, end: str) -> list[dict[str, Any]]:
    """Clamp both ends: VCI returns a buffer of sessions before the requested start (research R-002)."""
    return [row for row in cut_to_end(rows, end) if str(row["time"]).split(" ", maxsplit=1)[0] >= start]


def cut_to_end(rows: list[dict[str, Any]], end: str) -> list[dict[str, Any]]:
    return [row for row in rows if str(row["time"]).split(" ", maxsplit=1)[0] <= end]


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a local-only canonical Vnstock history package")
    parser.add_argument("--symbol")
    parser.add_argument("--venue", choices=("HOSE", "HNX", "UPCOM"))
    parser.add_argument("--start", required=True)
    parser.add_argument("--end", required=True)
    parser.add_argument("--output", type=Path, default=Path("output"))
    parser.add_argument("--market-overview", action="store_true",
                        help="Export the four configured market indices as indexRecords")
    parser.add_argument("--lookback-days", type=int, default=90,
                        help="For --market-overview incremental runs, re-fetch this many days before "
                             "the latest existing package date and merge with older records")
    parser.add_argument("--full-refresh", action="store_true",
                        help="For --market-overview, ignore existing packages and re-fetch the full range")
    args = parser.parse_args()
    if args.market_overview:
        index_snapshot_records = incremental_market_index_records(
            args.start, args.end, args.output, args.lookback_days, args.full_refresh)
        package = build_market_package([], index_snapshot_records, args.start, args.end, "1.0.0")
        filename = market_overview_filename()
    else:
        if not args.symbol or not args.venue:
            parser.error("--symbol and --venue are required unless --market-overview is set")
        records = package_records(fetch_rows(args.symbol, args.start, args.end), args.venue, args.symbol, args.start)
        package = build_package(records, args.start, args.end, "1.0.0")
        filename = f"{args.venue.lower()}-{args.symbol.lower()}-{args.start}-{args.end}.json"
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / filename
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote canonical package: {path}")
    print(f"Package SHA-256: {package['packageSha256']}")


if __name__ == "__main__":
    main()
