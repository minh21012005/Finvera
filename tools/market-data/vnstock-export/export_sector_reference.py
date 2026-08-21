"""Manual, local-only Vnstock sector-classification exporter for Feature 002
(specs/002-stock-detail-analysis, research.md R-012 gate G-04: owner-accepted
KBS taxonomy).

Captures `vnstock.Listing(source="kbs").symbols_by_industries()` as a
canonical package. The KBS taxonomy is proprietary/Vietnamese-labelled and not
confirmed to be a standard ICB/GICS scheme (research.md R-012 G-04) -- it is
recorded as scheme "KBS_INDUSTRY" with the pinned package version, never
represented as a standard scheme it was not confirmed to be. Same safety
posture as export_history.py: never touches PostgreSQL, never logs
credentials, output is gitignored and must be reviewed before import.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

CONTRACT_VERSION = "vnstock-sector-reference-v1"
SOURCE = "VNSTOCK_KBS"
MIN_CONSTITUENTS = 8  # valuation-v1's own N_min floor -- below this a sector stays own-history-only


def canonical_json(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def build_records(frame) -> list[dict[str, Any]]:
    required = {"symbol", "industry_code", "industry_name"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock industry-classification schema is missing an expected column")
    records = []
    for _, row in frame.iterrows():
        record = {
            "canonicalRecord": "",
            "displayNameEn": None,
            "displayNameVi": str(row["industry_name"]),
            "sectorCode": str(row["industry_code"]),
            "symbol": str(row["symbol"]).upper(),
        }
        record["canonicalRecord"] = canonical_json({k: v for k, v in record.items() if k != "canonicalRecord"})
        records.append(record)
    return records


def build_package(records: list[dict[str, Any]], scheme_version: str, tool_version: str) -> dict[str, Any]:
    if not records:
        raise ValueError("no classified symbols were returned")
    counts: dict[str, int] = {}
    for record in records:
        counts[record["sectorCode"]] = counts.get(record["sectorCode"], 0) + 1
    below_floor = sorted(code for code, count in counts.items() if count < MIN_CONSTITUENTS)
    records = sorted(records, key=lambda r: (r["sectorCode"], r["symbol"]))
    payload = {"records": records}
    payload_json = canonical_json(payload)
    return {
        "contractVersion": CONTRACT_VERSION, "toolName": "finvera-vnstock-exporter",
        "toolVersion": tool_version, "upstreamSource": SOURCE,
        "scheme": "KBS_INDUSTRY", "schemeVersion": scheme_version,
        "sectorConstituentCounts": counts, "sectorsBelowComparabilityFloor": below_floor,
        "generatedAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "packageSha256": hashlib.sha256(payload_json.encode()).hexdigest(),
        "canonicalPayload": payload_json, "records": records,
    }


def fetch_classifications():
    import vnstock

    return vnstock.Listing(source="kbs").symbols_by_industries()


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a local-only canonical Vnstock sector-classification package")
    parser.add_argument("--scheme-version", required=True,
                         help="The pinned vnstock package version this classification was captured with, "
                              "e.g. 4.0.6 -- run `uv pip show vnstock` to confirm.")
    parser.add_argument("--output", type=Path, default=Path("output"))
    args = parser.parse_args()
    records = build_records(fetch_classifications())
    package = build_package(records, args.scheme_version, "0.1.0")
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / f"sector-reference-{args.scheme_version}.json"
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote canonical package: {path} ({len(records)} symbol classifications)")
    print(f"Package SHA-256: {package['packageSha256']}")
    print(f"Sectors below the {MIN_CONSTITUENTS}-constituent comparability floor: "
          f"{package['sectorsBelowComparabilityFloor']}")


if __name__ == "__main__":
    main()
