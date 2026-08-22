"""Owner-run, one-time-ish bulk equity-profile exporter.

Feature 002's stock overview/screener/valuation code all read `equity_profile` (company name,
sector link, shares outstanding) via `EquityProfileRepository` -- but nothing in this codebase
ever wrote a real row there outside test fixtures (`EquityProfileEntity` is only constructed in
`src/test/**`). Without it, every real symbol degrades gracefully (`PROFILE_UNAVAILABLE`,
`NO_EQUITY_PROFILE` from the sector-reference importer) but never actually shows a company name,
market cap, or sector-based valuation.

Same `symbols_by_exchange()` call already proven live in export_instrument_reference.py (2026-08-22)
also carries `organ_name`/`en_organ_name` -- this script reuses it for company names.

`shares_outstanding`/`free_float_ratio` are NOT available from this listing call, so they are left
null; `listing_status` is left "UNKNOWN" for the same reason instrument_status was in
export_instrument_reference.py (being listed in this call is not a confirmed lifecycle status
field). `equity_profile`'s own check constraint requires at least one of
sector_reference_id/shares_outstanding/quality_reason to be non-null when the other two are absent
here, so quality_reason states plainly what is missing and why, rather than fabricating a number.

Usage:
    uv run --project ../provider-poc python export_equity_profile.py
"""
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

CONTRACT_VERSION = "vnstock-equity-profile-v1"
SOURCE = "VNSTOCK_KBS"
QUALITY_REASON = "SHARES_OUTSTANDING_UNAVAILABLE"


def canonical_json(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def fetch_universe():
    from vnstock import Listing

    frame = Listing(source="kbs").symbols_by_exchange()
    required = {"symbol", "type", "exchange", "organ_name"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock symbols_by_exchange schema is missing an expected column")
    return frame[(frame["type"] == "stock") & (frame["exchange"].isin(["HOSE", "HNX", "UPCOM"]))]


def build_records(frame, effective_from: str) -> list[dict[str, Any]]:
    records = []
    seen_symbols = set()
    for _, row in frame.iterrows():
        symbol = str(row["symbol"]).upper()
        if symbol in seen_symbols:
            continue
        seen_symbols.add(symbol)
        name_vi = str(row["organ_name"]).strip() if row.get("organ_name") not in (None, "") else None
        if not name_vi:
            continue  # company_name_vi is not-null in the schema; skip rather than fabricate a name
        name_en_raw = row.get("en_organ_name")
        name_en = str(name_en_raw).strip() if name_en_raw not in (None, "") else None
        record = {
            "canonicalRecord": "",
            "companyNameEn": name_en,
            "companyNameVi": name_vi,
            "effectiveFrom": effective_from,
            "listingStatus": "UNKNOWN",
            "qualityReason": QUALITY_REASON,
            "symbol": symbol,
        }
        record["canonicalRecord"] = canonical_json({k: v for k, v in record.items() if k != "canonicalRecord"})
        records.append(record)
    return records


def build_package(records: list[dict[str, Any]], tool_version: str) -> dict[str, Any]:
    if not records:
        raise ValueError("no symbols were returned")
    records = sorted(records, key=lambda r: r["symbol"])
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
    parser = argparse.ArgumentParser(description="Create a local-only canonical Vnstock equity-profile package")
    parser.add_argument("--output", type=Path, default=Path("output"))
    args = parser.parse_args()
    effective_from = datetime.now().date().isoformat()
    records = build_records(fetch_universe(), effective_from)
    package = build_package(records, "0.1.0")
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / "equity-profile.json"
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote canonical package: {path} ({len(records)} symbols)")
    print(f"Package SHA-256: {package['packageSha256']}")


if __name__ == "__main__":
    main()
