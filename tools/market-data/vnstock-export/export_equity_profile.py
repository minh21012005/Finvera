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
null; `listing_status` is "LISTED" -- unlike instrument_status in export_instrument_reference.py,
this call's entire purpose IS the exchange's official current-listing directory, so appearing in it
is direct evidence of being listed, not a guess. `equity_profile`'s own check constraint requires
at least one of
sector_reference_id/shares_outstanding/quality_reason to be non-null when the other two are absent
here, so quality_reason states plainly what is missing and why, rather than fabricating a number.

Usage:
    uv run --project ../provider-poc python export_equity_profile.py
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import time
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

CONTRACT_VERSION = "vnstock-equity-profile-v1"
SOURCE = "VNSTOCK_VCI"  # ADR-0013 (Feature 021)
QUALITY_REASON = "SHARES_OUTSTANDING_UNAVAILABLE"
SYMBOL_PATTERN = re.compile(r"[A-Z0-9]{1,32}")  # the import layer's symbol shape


def canonical_json(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def fetch_universe():
    from vnstock import Listing

    frame = Listing(source="vci").symbols_by_exchange()
    required = {"symbol", "type", "exchange", "organ_name"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock symbols_by_exchange schema is missing an expected column")
    # VCI labels (ADR-0013): type is upper-case STOCK and HOSE appears as HSX.
    return frame[(frame["type"] == "STOCK") & (frame["exchange"].isin(["HSX", "HNX", "UPCOM"]))]


def fetch_delisted():
    from vnstock import Listing

    frame = Listing(source="vci").symbols_by_exchange()
    required = {"symbol", "type", "exchange", "organ_name"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock symbols_by_exchange schema is missing an expected column")
    return frame[(frame["type"] == "STOCK") & (frame["exchange"] == "DELISTED")]


def build_delisted_records(frame, effective_from: str) -> list[dict[str, Any]]:
    """ADR-0013 / Feature 021 (specs/021 research R-007): symbols VCI marks DELISTED (type STOCK).
    Importing one revises an existing profile's listing status; the importer carries the last known
    share count forward (absent means unknown, never removed), and symbols Finvera never listed
    come back UNKNOWN_INSTRUMENT. Found via DAN/DVT: delisted at the provider, still LISTED in the DB."""
    records = []
    seen: set[str] = set()
    for _, row in frame.iterrows():
        symbol = str(row["symbol"]).upper()
        if symbol in seen or not SYMBOL_PATTERN.fullmatch(symbol):
            continue
        seen.add(symbol)
        name_vi = str(row["organ_name"]).strip() if row.get("organ_name") not in (None, "") else None
        if not name_vi:
            continue
        record = {
            "canonicalRecord": "",
            "companyNameEn": None,
            "companyNameVi": name_vi,
            "effectiveFrom": effective_from,
            "listingStatus": "DELISTED",
            "qualityReason": QUALITY_REASON,
            "sharesOutstanding": None,
            "symbol": symbol,
        }
        record["canonicalRecord"] = canonical_json({k: v for k, v in record.items() if k != "canonicalRecord"})
        records.append(record)
    return records


def fetch_overview(symbol: str) -> dict[str, Any] | None:
    """VCI Company.overview() (ADR-0013): `issue_share` appears in several duplicated columns of
    which some are empty -- the first non-null occurrence is the share count; `market_cap` and
    `current_price` provide an independent implied count for the cross-check in share_fields."""
    from vnstock import Company

    try:
        frame = Company(symbol=symbol, source="vci").overview()
        if frame is None or len(frame) == 0:
            return None
        overview: dict[str, Any] = {}
        for position, column in enumerate(frame.columns):
            value = frame.iloc[0, position]
            if column not in overview or overview[column] is None or (isinstance(overview[column], float) and overview[column] != overview[column]):
                overview[column] = value
        return overview
    except (Exception, SystemExit) as exc:  # noqa: BLE001 -- one symbol's overview failure must not stop the batch
        if isinstance(exc, SystemExit) and "rate limit" not in str(exc).lower():
            raise
        return None


UNVERIFIED_REASON = "SHARES_OUTSTANDING_UNVERIFIED"


def share_fields(overview: dict[str, Any] | None) -> tuple[int | None, str | None]:
    """(sharesOutstanding, qualityReason). VCI `issue_share` is the outstanding count; it is
    cross-checked against market_cap / current_price (both from the same page). A > 1 % gap keeps
    the count but flags it UNVERIFIED (Feature 011 R-002 rule, VCI inputs per ADR-0013)."""
    if not overview:
        return None, "SHARES_OUTSTANDING_UNAVAILABLE"
    shares = None
    try:
        raw = overview.get("issue_share")
        if raw is not None and raw == raw and float(raw) > 0:
            shares = int(float(raw))
    except (TypeError, ValueError):
        shares = None
    if shares is None:
        return None, "SHARES_OUTSTANDING_UNAVAILABLE"
    try:
        market_cap = float(overview.get("market_cap"))
        price = float(overview.get("current_price"))
        if market_cap > 0 and price > 0:
            implied = market_cap / price
            if abs(shares - implied) > implied * 0.01:
                return shares, UNVERIFIED_REASON
    except (TypeError, ValueError):
        pass
    return shares, None


TOOL_VERSION = "1.0.0"  # 1.0.0: VCI source (ADR-0013); issue_share + market-cap cross-check; free float still never emitted
DEFAULT_MAX_AGE_DAYS = 30


def reusable_share_facts(output: Path, max_age_days: int, full_refresh: bool) -> dict[str, tuple[int | None, str | None]]:
    """{symbol: (shares, reason)} from the package already on disk when it was produced by this tool
    version within `max_age_days`. Share counts change only on corporate actions, so re-calling the
    provider ~1,500 times per refresh is waste; new listings are still fetched because they are absent."""
    if full_refresh:
        return {}
    path = output / "equity-profile.json"
    if not path.exists():
        return {}
    try:
        package = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}
    if package.get("toolVersion") != TOOL_VERSION:
        return {}
    try:
        generated = datetime.fromisoformat(str(package.get("generatedAt", "")).replace("Z", "+00:00"))
    except ValueError:
        return {}
    if datetime.now(UTC) - generated > timedelta(days=max_age_days):
        return {}
    return {r["symbol"]: (r.get("sharesOutstanding"), r.get("qualityReason")) for r in package.get("records", [])}


def build_records(frame, effective_from: str, overview_lookup=fetch_overview, share_lookup=None) -> list[dict[str, Any]]:
    """`share_lookup(symbol) -> (shares, reason) | None` short-circuits the provider call when the
    fact is already known (reuse); None means "fetch it"."""
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
        known = share_lookup(symbol) if share_lookup is not None else None
        shares, reason = known if known is not None else share_fields(overview_lookup(symbol))
        record = {
            "canonicalRecord": "",
            "companyNameEn": name_en,
            "companyNameVi": name_vi,
            "effectiveFrom": effective_from,
            "listingStatus": "LISTED",
            "qualityReason": reason,
            "sharesOutstanding": shares,
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
    parser.add_argument("--requests-per-minute", type=float, default=30.0,
                        help="Pacing for the per-symbol overview calls (Community tier ~60/min).")
    parser.add_argument("--full-refresh", action="store_true",
                        help="Re-fetch every symbol's overview even when a recent package exists.")
    parser.add_argument("--max-age-days", type=int, default=DEFAULT_MAX_AGE_DAYS,
                        help="Reuse share counts from an existing package younger than this (default 30).")
    args = parser.parse_args()
    reusable = reusable_share_facts(args.output, args.max_age_days, args.full_refresh)
    effective_from = datetime.now().date().isoformat()
    interval_seconds = 60.0 / max(1, args.requests_per_minute)
    progress = {"n": 0}

    def paced_overview(symbol: str):
        if progress["n"]:
            time.sleep(interval_seconds)
        progress["n"] += 1
        overview = fetch_overview(symbol)
        shares, reason = share_fields(overview)
        outcome = f"shares={shares:,}" if shares is not None else "no shares"
        if reason:
            outcome += f" ({reason})"
        print(f"[{progress['n']}/{progress['total']}] {symbol}: {outcome}", flush=True)
        return overview

    universe = fetch_universe()
    symbols = [str(r["symbol"]).upper() for _, r in universe.iterrows()]
    to_fetch = [s for s in symbols if s not in reusable]
    progress["total"] = len(to_fetch)
    print(f"Universe: {len(symbols)} stocks; {len(symbols) - len(to_fetch)} reused from the existing package, "
          f"{len(to_fetch)} overview calls at {args.requests_per_minute:g}/min "
          f"(~{len(to_fetch) / max(1.0, args.requests_per_minute):.0f} min).", flush=True)
    records = build_records(universe, effective_from, paced_overview, share_lookup=reusable.get)
    listed_symbols = {r["symbol"] for r in records}
    delisted = [r for r in build_delisted_records(fetch_delisted(), effective_from) if r["symbol"] not in listed_symbols]
    records = records + delisted
    print(f"Delisted at provider: {len(delisted)} symbols recorded with listingStatus=DELISTED")
    with_shares = sum(1 for r in records if r["sharesOutstanding"] is not None)
    print(f"Outstanding shares present for {with_shares}/{len(records)} symbols")
    package = build_package(records, TOOL_VERSION)
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / "equity-profile.json"
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote canonical package: {path} ({len(records)} symbols)")
    print(f"Package SHA-256: {package['packageSha256']}")


if __name__ == "__main__":
    main()
