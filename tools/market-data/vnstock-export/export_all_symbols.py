"""Owner-run, resumable full-universe exporter: loops export_daily_bars.py and
export_fundamentals.py over every KBS-listed symbol, checkpointed so an
interrupted run (Ctrl+C, network blip) resumes exactly where it left off, and
stops automatically once every symbol has been attempted.

Reuses the already-approved per-symbol exporters directly (same package/checksum
conventions, same ADR-0004 "owner-operated, offline, never live/scheduled"
posture) rather than duplicating their logic. Sector reference is intentionally
NOT looped here -- export_sector_reference.py already fetches the whole
universe's classification in one call.

Usage (no --end needed -- defaults to today, so re-running later to pick up new
trading days just works, it does not silently stay stuck at the first run's date):
    uv run --project ../provider-poc python export_all_symbols.py --start 2024-01-01

Resume an interrupted run, or refresh with newer trading days (same command --
the checkpoint file does the rest; an entry fetched through an earlier date is
re-fetched automatically, not skipped):
    uv run --project ../provider-poc python export_all_symbols.py --start 2024-01-01

Bounded test run first (recommended before letting it run unattended for hours):
    uv run --project ../provider-poc python export_all_symbols.py --start 2024-01-01 --max-symbols 5
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import export_daily_bars
import export_fundamentals

CHECKPOINT_FILE = "full-universe-checkpoint.json"
DONE = "done"


def load_checkpoint(path: Path) -> dict[str, Any]:
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    return {"generatedAt": None, "symbols": {}}


def save_checkpoint(path: Path, checkpoint: dict[str, Any]) -> None:
    checkpoint["generatedAt"] = datetime.now(UTC).isoformat().replace("+00:00", "Z")
    path.write_text(json.dumps(checkpoint, ensure_ascii=False, indent=2), encoding="utf-8")


def fetch_symbol_universe() -> list[str]:
    """The full common-equity universe (research.md R-012 evidence, 2026-08-22 live probe):
    `symbols_by_industries()` -- the call the already-approved G-04 sector-classification evidence
    used -- only covers instruments KBS has industry-classified (697 of them), not every listed
    stock. `symbols_by_exchange()` filtered to type == "stock" on HOSE/HNX/UPCOM returns 1,525 --
    matching `all_symbols()`'s count exactly -- and is the actual tradable common-equity universe.
    Sector classification (export_sector_reference.py) deliberately keeps using
    symbols_by_industries() -- that gate is specifically about classification coverage, and a
    symbol with no KBS industry yet is a real, already-documented limitation (G-04), not something
    to paper over here."""
    from vnstock import Listing

    frame = Listing(source="kbs").symbols_by_exchange()
    required = {"symbol", "type", "exchange"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock symbols_by_exchange schema is missing an expected column")
    stocks = frame[(frame["type"] == "stock") & (frame["exchange"].isin(["HOSE", "HNX", "UPCOM"]))]
    symbols = sorted({str(s).upper() for s in stocks["symbol"].tolist() if str(s).strip()})
    return symbols


def export_daily_bars_for(symbol: str, start: str, end: str, output: Path) -> None:
    rows = export_daily_bars.fetch_rows(symbol, start, end)
    records = export_daily_bars.package_records(rows, symbol)
    package = export_daily_bars.build_package(records, symbol, start, end, "0.1.0")
    path = output / export_daily_bars.output_filename(symbol)
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def export_fundamentals_for(symbol: str, period: str, unit_scale: int, output: Path) -> None:
    income_statement, ratio, cash_flow = export_fundamentals.fetch_tables(symbol, period)
    records = export_fundamentals.build_metric_records(symbol, income_statement, ratio, cash_flow)
    package = export_fundamentals.build_package(records, symbol, "0.1.0", unit_scale)
    path = output / export_fundamentals.output_filename(symbol, period)
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def daily_bars_current(symbol: str, entry: dict[str, Any], args: argparse.Namespace) -> bool:
    """Done only for THIS run's exact [start, end] range, AND only if the file it should have
    produced is still actually on disk -- not just trusting the checkpoint blindly, since the
    output file is the thing StockImportConfiguration's directory scan (and the owner) actually
    reads. A later --end (e.g. re-running to pick up a new trading day) makes an old entry stale
    again rather than silently staying short a day forever."""
    return (entry.get("daily_bars") == DONE
            and entry.get("daily_bars_range") == [args.start, args.end]
            and (args.output / export_daily_bars.output_filename(symbol)).exists())


def fundamentals_current(symbol: str, entry: dict[str, Any], args: argparse.Namespace) -> bool:
    return (entry.get("fundamentals") == DONE
            and entry.get("fundamentals_period") == args.period
            and (args.output / export_fundamentals.output_filename(symbol, args.period)).exists())


def process_symbol(
    symbol: str, args: argparse.Namespace, checkpoint: dict[str, Any], checkpoint_path: Path
) -> None:
    entry = checkpoint["symbols"].setdefault(symbol, {})

    if not daily_bars_current(symbol, entry, args):
        try:
            export_daily_bars_for(symbol, args.start, args.end, args.output)
            entry["daily_bars"] = DONE
            entry["daily_bars_range"] = [args.start, args.end]
            print(f"  daily_bars: OK")
        except Exception as exc:  # noqa: BLE001 -- one bad symbol must not stop the batch
            entry["daily_bars"] = f"failed:{type(exc).__name__}"
            entry.pop("daily_bars_range", None)
            print(f"  daily_bars: FAILED ({type(exc).__name__})")
        save_checkpoint(checkpoint_path, checkpoint)

    if not fundamentals_current(symbol, entry, args):
        try:
            export_fundamentals_for(symbol, args.period, args.unit_scale, args.output)
            entry["fundamentals"] = DONE
            entry["fundamentals_period"] = args.period
            print(f"  fundamentals: OK")
        except Exception as exc:  # noqa: BLE001
            entry["fundamentals"] = f"failed:{type(exc).__name__}"
            entry.pop("fundamentals_period", None)
            print(f"  fundamentals: FAILED ({type(exc).__name__})")
        save_checkpoint(checkpoint_path, checkpoint)


def is_finished(symbol: str, entry: dict[str, Any], args: argparse.Namespace) -> bool:
    """A symbol counts as finished once each dataset is current for this run's parameters, or has
    failed -- failures are recorded, not silently retried forever (without --retry-failed), so the
    run still terminates on symbols Vnstock genuinely cannot serve (e.g. some banks' fundamentals
    shape differs, per research.md G-01), rather than retrying them every single run."""
    daily_bars_settled = (daily_bars_current(symbol, entry, args)
                           or str(entry.get("daily_bars", "")).startswith("failed"))
    fundamentals_settled = (fundamentals_current(symbol, entry, args)
                             or str(entry.get("fundamentals", "")).startswith("failed"))
    return daily_bars_settled and fundamentals_settled


def main() -> int:
    # Line-buffer stdout even when redirected to a file (e.g. a background run) so progress is
    # visible while the run is still in progress, not only once it exits.
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except (AttributeError, ValueError):
        pass

    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--start", required=True, help="Daily-bar range start, e.g. 2024-01-01")
    parser.add_argument("--end", default=None,
                         help="Daily-bar range end, e.g. 2026-08-20. Defaults to today (recommended: "
                              "omit it and just re-run this same command later to pick up new trading "
                              "days -- a symbol already fetched through an earlier --end is "
                              "automatically re-fetched, not skipped as already done).")
    parser.add_argument("--period", choices=("year", "quarter"), default="quarter")
    parser.add_argument("--unit-scale", type=int, default=1)
    parser.add_argument("--requests-per-minute", type=float, default=30.0,
                         help="Pace between symbols (each symbol issues ~4 requests: 1 OHLCV + "
                              "3 fundamentals calls, all back-to-back, then this pause).")
    parser.add_argument("--max-symbols", type=int, default=None,
                         help="Process at most this many remaining symbols this run -- do a small "
                              "bounded run first (e.g. 5) before letting this run unattended for hours.")
    parser.add_argument("--output", type=Path, default=Path("output"))
    parser.add_argument("--retry-failed", action="store_true",
                         help="Clear previously recorded failures so this run retries them instead "
                              "of treating them as finished (use after a transient outage; a symbol "
                              "that fails for a structural reason, e.g. an unsupported fundamentals "
                              "shape, will just fail again).")
    args = parser.parse_args()
    if args.end is None:
        args.end = datetime.now().date().isoformat()

    args.output.mkdir(parents=True, exist_ok=True)
    checkpoint_path = args.output / CHECKPOINT_FILE
    checkpoint = load_checkpoint(checkpoint_path)
    checkpoint.setdefault("symbols", {})

    if args.retry_failed:
        for entry in checkpoint["symbols"].values():
            for key in ("daily_bars", "fundamentals"):
                if str(entry.get(key, "")).startswith("failed"):
                    del entry[key]

    universe = fetch_symbol_universe()
    checkpoint["universeSize"] = len(universe)
    save_checkpoint(checkpoint_path, checkpoint)
    remaining = [s for s in universe if not is_finished(s, checkpoint["symbols"].get(s, {}), args)]
    print(f"Universe: {len(universe)} symbols. Already finished: {len(universe) - len(remaining)}. "
          f"Remaining: {len(remaining)}.")

    if args.max_symbols is not None:
        remaining = remaining[: args.max_symbols]
        print(f"Bounded to {len(remaining)} symbols this run (--max-symbols).")

    if not remaining:
        print("Nothing left to do -- every symbol already has a recorded outcome. Stopping.")
        return 0

    interval_seconds = 60.0 / args.requests_per_minute
    for index, symbol in enumerate(remaining, start=1):
        print(f"[{index}/{len(remaining)}] {symbol}")
        process_symbol(symbol, args, checkpoint, checkpoint_path)
        if index < len(remaining):
            time.sleep(interval_seconds)

    done_count = sum(1 for s, e in checkpoint["symbols"].items() if is_finished(s, e, args))
    failed_count = sum(
        1 for e in checkpoint["symbols"].values()
        if str(e.get("daily_bars", "")).startswith("failed") or str(e.get("fundamentals", "")).startswith("failed")
    )
    print(f"\nDone this run. Checkpoint total attempted: {done_count}/{len(universe)}. "
          f"Symbols with at least one failed dataset: {failed_count} (see {checkpoint_path}).")
    print("Run the exact same command again to resume/retry remaining or failed symbols; "
          "it exits immediately once nothing is left.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
