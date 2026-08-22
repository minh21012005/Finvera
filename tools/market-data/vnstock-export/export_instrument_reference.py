"""Owner-run, one-time-ish bulk instrument-reference exporter.

Feature 002's ingestion (StockIngestionService.ingestDailyBar/ingestFundamentalReport) requires a
symbol to already exist as an active market_instrument row -- it rejects UNKNOWN_INSTRUMENT
otherwise (this is Feature 001's reference table; Feature 002 only reads it, per plan.md's
cross-module rule). Bulk daily-bar/fundamentals import therefore needs the whole symbol universe
registered first. Same call already used and proven by export_all_symbols.py's universe fetch
(live probe, 2026-08-22): `symbols_by_exchange()` filtered to type == "stock" on
HOSE/HNX/UPCOM.

`listedFrom` is NOT a confirmed real listing/IPO date -- Vnstock's exchange listing does not carry
one -- it is set to today, the same "earliest date we actually have evidence for" convention
export_history.py already uses for the same reason (never guess a date the provider doesn't
supply). `instrumentStatus` is "UNKNOWN" for the same reason: being in this listing call is not a
confirmed lifecycle status field.

Usage:
    uv run --project ../provider-poc python export_instrument_reference.py
"""
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

CONTRACT_VERSION = "vnstock-instrument-reference-v1"
SOURCE = "VNSTOCK_KBS"


def canonical_json(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def fetch_universe():
    from vnstock import Listing

    frame = Listing(source="kbs").symbols_by_exchange()
    required = {"symbol", "type", "exchange"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock symbols_by_exchange schema is missing an expected column")
    return frame[(frame["type"] == "stock") & (frame["exchange"].isin(["HOSE", "HNX", "UPCOM"]))]


def build_records(frame, listed_from: str) -> list[dict[str, Any]]:
    records = []
    seen_symbols = set()
    for _, row in frame.iterrows():
        symbol = str(row["symbol"]).upper()
        if symbol in seen_symbols:
            continue  # defensive: keep the exporter correct even if a future Vnstock response
        seen_symbols.add(symbol)  # ever lists the same symbol on two rows
        record = {
            "canonicalRecord": "",
            "instrumentStatus": "UNKNOWN",
            "isin": None,
            "listedFrom": listed_from,
            "symbol": symbol,
            "venue": str(row["exchange"]).upper(),
        }
        record["canonicalRecord"] = canonical_json({k: v for k, v in record.items() if k != "canonicalRecord"})
        records.append(record)
    return records


def build_package(records: list[dict[str, Any]], tool_version: str) -> dict[str, Any]:
    if not records:
        raise ValueError("no symbols were returned")
    records = sorted(records, key=lambda r: (r["venue"], r["symbol"]))
    payload = {"records": records}
    payload_json = canonical_json(payload)
    return {
        "contractVersion": CONTRACT_VERSION, "toolName": "finvera-vnstock-exporter",
        "toolVersion": tool_version, "upstreamSource": SOURCE,
        "generatedAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "packageSha256": hashlib.sha256(payload_json.encode()).hexdigest(),
        "canonicalPayload": payload_json, "records": records,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a local-only canonical Vnstock instrument-reference package")
    parser.add_argument("--output", type=Path, default=Path("output"))
    args = parser.parse_args()
    listed_from = datetime.now().date().isoformat()
    records = build_records(fetch_universe(), listed_from)
    package = build_package(records, "0.1.0")
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / "instrument-reference.json"
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote canonical package: {path} ({len(records)} symbols)")
    print(f"Package SHA-256: {package['packageSha256']}")


if __name__ == "__main__":
    main()
