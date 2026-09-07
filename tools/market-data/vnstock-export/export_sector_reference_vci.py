"""VCI ICB sector-classification exporter (Feature 020, ADR-0012; closes Q-38).

Captures the ICB classification VCI publishes for every listed equity (HOSE, HNX **and UPCoM**)
through vnstock ``Listing(source="vci")`` and writes it as a ``vnstock-sector-reference-v1``
package -- the same contract the KBS exporter produced, so the importer is unchanged and the
package is validated the same way (checksum over the canonical payload, one record per symbol).

Scheme ``VCI_ICB_L3``: ICB level 3 (the ICB *sector* tier, 40 codes such as 8350 Banks,
3570 Food Producers), the finest level VCI attaches to symbols. Display names come from VCI's
own ICB dictionary (``industries_icb``: Vietnamese + English). Only ``type == STOCK`` symbols on
HSX/HNX/UPCOM are classified: funds, ETFs, covered warrants, bonds and delisted symbols are not
equities Finvera compares peers across.

Same safety posture as the other exporters: never touches PostgreSQL, never logs credentials,
output is gitignored and reviewed by the owner before import.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import warnings
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import provider_retry

CONTRACT_VERSION = "vnstock-sector-reference-v1"
SOURCE = "VNSTOCK_VCI"
SCHEME = "VCI_ICB_L3"
ICB_LEVEL = 3
TOOL_VERSION = "1.0.0"
MIN_CONSTITUENTS = 8  # valuation's own N_min floor -- below this a sector stays own-history-only
EQUITY_TYPES = frozenset({"STOCK"})
EXCHANGES = frozenset({"HSX", "HNX", "UPCOM"})
# contract rule S-4: a half-empty provider response must not silently unclassify the universe
MIN_COVERAGE = 0.90
SYMBOL_PATTERN = re.compile(r"[A-Z0-9]{1,32}")  # the importer's own symbol rule


class SchemaMismatch(ValueError):
    """The VCI listing frames do not have the shape this exporter was audited against."""


def canonical_json(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def require_columns(frame, required: set[str], name: str) -> None:
    missing = sorted(required - set(map(str, frame.columns)))
    if missing:
        raise SchemaMismatch(f"VCI {name} frame is missing columns {missing}")


def clean(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return None if text in ("", "nan", "None", "NaN") else text


def sector_names(industries) -> dict[str, tuple[str, str | None]]:
    """ICB level-3 code -> (Vietnamese name, English name) from VCI's ICB dictionary."""
    require_columns(industries, {"icb_code", "icb_name", "en_icb_name", "level"}, "industries_icb")
    names: dict[str, tuple[str, str | None]] = {}
    for _, row in industries.iterrows():
        if clean(row["level"]) != str(ICB_LEVEL):
            continue
        code, vi = clean(row["icb_code"]), clean(row["icb_name"])
        if code and vi:
            names[code] = (vi, clean(row["en_icb_name"]))
    if not names:
        raise SchemaMismatch(f"VCI industries_icb has no level-{ICB_LEVEL} sectors")
    return names


def equity_symbols(by_exchange) -> dict[str, str]:
    """symbol -> exchange for listed equities only (STOCK on HSX/HNX/UPCOM)."""
    require_columns(by_exchange, {"symbol", "exchange", "type"}, "symbols_by_exchange")
    equities: dict[str, str] = {}
    for _, row in by_exchange.iterrows():
        symbol, exchange, kind = clean(row["symbol"]), clean(row["exchange"]), clean(row["type"])
        if symbol and kind in EQUITY_TYPES and exchange in EXCHANGES:
            equities[symbol.upper()] = exchange
    return equities


def build_records(by_industries, industries, by_exchange) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """One level-3 record per listed equity plus an audit block the owner reviews before import."""
    require_columns(by_industries, {"symbol", "icb_level", "icb_code", "icb_name"}, "symbols_by_industries")
    names = sector_names(industries)
    equities = equity_symbols(by_exchange)
    if not equities:
        raise SchemaMismatch("VCI symbols_by_exchange lists no listed equities")

    codes_by_symbol: dict[str, dict[str, str]] = {}
    for _, row in by_industries.iterrows():
        if clean(row["icb_level"]) != str(ICB_LEVEL):
            continue
        symbol, code = clean(row["symbol"]), clean(row["icb_code"])
        if not symbol or not code:
            continue
        symbol = symbol.upper()
        if symbol not in equities:
            continue  # fund, ETF, warrant, bond, delisted -- not a peer-comparable equity
        codes_by_symbol.setdefault(symbol, {})[code] = clean(row["icb_name"]) or code

    records: list[dict[str, Any]] = []
    duplicates: dict[str, list[str]] = {}
    skipped: list[str] = []
    unnamed: set[str] = set()
    for symbol in sorted(codes_by_symbol):
        if not SYMBOL_PATTERN.fullmatch(symbol):
            skipped.append(symbol)
            continue
        codes = sorted(codes_by_symbol[symbol])
        if len(codes) > 1:
            # contract rule S-3: deterministic pick (lowest code), reported for review, never silent
            duplicates[symbol] = codes
        code = codes[0]
        if code in names:
            vi, en = names[code]
        else:
            vi, en = codes_by_symbol[symbol][code], None
            unnamed.add(code)
        record = {"canonicalRecord": "", "displayNameEn": en, "displayNameVi": vi, "sectorCode": code, "symbol": symbol}
        record["canonicalRecord"] = canonical_json({k: v for k, v in record.items() if k != "canonicalRecord"})
        records.append(record)

    classified = {r["symbol"] for r in records}
    by_exchange_counts: dict[str, dict[str, int]] = {}
    for symbol, exchange in equities.items():
        bucket = by_exchange_counts.setdefault(exchange, {"equities": 0, "classified": 0})
        bucket["equities"] += 1
        bucket["classified"] += symbol in classified
    audit = {
        "icbLevel": ICB_LEVEL,
        "equitySymbols": len(equities),
        "classified": len(records),
        "byExchange": dict(sorted(by_exchange_counts.items())),
        "unclassifiedEquities": sorted(s for s in equities if s not in classified),
        "duplicateClassifications": duplicates,
        "codesWithoutDictionaryName": sorted(unnamed),
        "skippedSymbols": skipped,
    }
    coverage = len(records) / len(equities)
    if coverage < MIN_COVERAGE:
        raise SchemaMismatch(f"VCI classified only {len(records)}/{len(equities)} listed equities "
                             f"({coverage:.1%} < {MIN_COVERAGE:.0%}); refusing to write a package")
    return records, audit


def build_package(records: list[dict[str, Any]], audit: dict[str, Any], scheme_version: str,
                  tool_version: str = TOOL_VERSION) -> dict[str, Any]:
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
        "scheme": SCHEME, "schemeVersion": scheme_version, "icbLevel": ICB_LEVEL,
        "sectorConstituentCounts": dict(sorted(counts.items())), "sectorsBelowComparabilityFloor": below_floor,
        "audit": audit,
        "generatedAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "packageSha256": hashlib.sha256(payload_json.encode()).hexdigest(),
        "canonicalPayload": payload_json, "records": records,
    }


def fetch_frames():
    import vnstock

    listing = vnstock.Listing(source="vci")

    def fetch():
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", FutureWarning)
            return listing.symbols_by_industries(), listing.industries_icb(), listing.symbols_by_exchange()

    # Three calls behind one retry: a blink on any of them used to end the whole refresh (Feature 026).
    return provider_retry.call("sector-reference listing frames", fetch)


def installed_vnstock_version() -> str:
    from importlib.metadata import version

    return version("vnstock")


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a local-only canonical VCI ICB sector-classification package")
    parser.add_argument("--scheme-version", default=None,
                        help="Pinned vnstock version the classification is captured with (default: the installed one)")
    parser.add_argument("--output", type=Path, default=Path("output"))
    args = parser.parse_args()
    scheme_version = args.scheme_version or installed_vnstock_version()
    by_industries, industries, by_exchange = fetch_frames()
    records, audit = build_records(by_industries, industries, by_exchange)
    package = build_package(records, audit, scheme_version)
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / f"sector-reference-vci-icb-l3-{scheme_version}.json"
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote canonical package: {path} ({len(records)} equity classifications, "
          f"{len(package['sectorConstituentCounts'])} ICB level-{ICB_LEVEL} sectors)")
    print(f"Package SHA-256: {package['packageSha256']}")
    print(f"Coverage by exchange: {audit['byExchange']}")
    print(f"Sectors below the {MIN_CONSTITUENTS}-constituent comparability floor: "
          f"{package['sectorsBelowComparabilityFloor']}")
    if audit["duplicateClassifications"]:
        print(f"Symbols with more than one level-{ICB_LEVEL} code (lowest kept): {audit['duplicateClassifications']}")
    if audit["unclassifiedEquities"]:
        print(f"Listed equities without a level-{ICB_LEVEL} code: {audit['unclassifiedEquities']}")


if __name__ == "__main__":
    main()
