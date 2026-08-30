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
import time
from datetime import UTC, datetime, timedelta
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


def fetch_overview(symbol: str) -> dict[str, Any] | None:
    """Feature 010 R-003: Company(kbs).overview() carries outstanding_shares / free_float_percentage.
    A failed call returns None so the profile keeps SHARES_OUTSTANDING_UNAVAILABLE rather than a guess."""
    try:
        from vnstock import Company
        frame = Company(symbol=symbol, source="kbs").overview()
        if frame is None or len(frame) == 0:
            return None
        row = frame.iloc[0]
        return {k: row[k] for k in frame.columns}
    except (Exception, SystemExit) as exc:  # noqa: BLE001 -- one symbol's overview failure must not stop the batch
        # vnai ends its rate-limit handling with sys.exit(...); treat it like any other miss.
        if isinstance(exc, SystemExit) and "rate limit" not in str(exc).lower():
            raise
        time.sleep(65)
        return None


UNVERIFIED_REASON = "SHARES_OUTSTANDING_UNVERIFIED"


def share_fields(overview: dict[str, Any] | None) -> tuple[int | None, str | None]:
    """(sharesOutstanding, qualityReason). Feature 011 research R-002: vnstock's
    `free_float_percentage` is really shares x par value and `free_float` is the par value, so no
    free-float figure is ever emitted. The share count is cross-checked against
    charter_capital (bn VND) / par_value; a > 1 % gap keeps the count but flags it."""
    if not overview:
        return None, QUALITY_REASON
    shares = None
    try:
        raw = overview.get("outstanding_shares")
        if raw is not None and raw == raw and int(raw) > 0:
            shares = int(raw)
    except (TypeError, ValueError):
        shares = None
    if shares is None:
        return None, QUALITY_REASON
    try:
        charter = float(overview.get("charter_capital"))
        par = float(overview.get("par_value"))
        if charter > 0 and par > 0:
            implied = charter * 1e9 / par
            # Outstanding can legitimately be BELOW charter/par (treasury shares: AAM 10.45M vs
            # 12.3M charter), never materially above it.
            if shares > implied * 1.01:
                return shares, UNVERIFIED_REASON
    except (TypeError, ValueError):
        pass
    return shares, None


TOOL_VERSION = "0.2.0"  # 0.2.0: outstanding shares (Feature 010); free float never emitted (Feature 011)
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
