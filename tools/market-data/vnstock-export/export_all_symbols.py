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
the checkpoint file does the rest). Daily bars are fetched INCREMENTALLY: only
a rolling `--lookback-days` window (default 90) before the last previously
fetched date, plus whatever is new, is re-fetched and merged into the existing
file -- not the whole range every time. The lookback window (rather than just
the exact new gap) exists because whether Vnstock's OHLCV series is raw or
already split/dividend-adjusted was never confirmed (research.md R-012 G-02),
so a recent corporate action could silently rewrite recent history; this
re-verifies a bounded recent window on every run as a defensive check for that
specific unresolved unknown. It does NOT protect a rewrite older than
`--lookback-days` -- run occasionally with `--full-refresh` for that:
    uv run --project ../provider-poc python export_all_symbols.py --start 2024-01-01
    uv run --project ../provider-poc python export_all_symbols.py --start 2024-01-01 --full-refresh

Bounded test run first (recommended before letting it run unattended for hours):
    uv run --project ../provider-poc python export_all_symbols.py --start 2024-01-01 --max-symbols 5
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from datetime import UTC, date, datetime, timedelta
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


def export_daily_bars_for(
    symbol: str, start: str, end: str, output: Path, lookback_days: int, full_refresh: bool
) -> None:
    """Incremental by default: only (re-)fetches the last `lookback_days` of the already-written
    file plus whatever is genuinely new, then merges that with the older records already on disk,
    instead of re-downloading the whole [start, end] range every run.

    The lookback window exists because whether Vnstock's OHLCV series is raw or already
    split/dividend-adjusted was never established (research.md R-012 G-02, still open) -- if a
    recent corporate action silently rewrote recent history, an incremental fetch that only asked
    for "new" days would never notice and would keep serving stale values for the rewritten dates.
    Re-verifying a rolling recent window on every run is a bounded, honest mitigation for that
    unresolved unknown; it does NOT protect a rewrite reaching further back than `lookback_days`,
    which `--full-refresh` exists to catch periodically.
    """
    path = output / export_daily_bars.output_filename(symbol)
    existing_records: list[dict[str, Any]] = []
    effective_start = start

    if not full_refresh and path.exists():
        try:
            existing_package = json.loads(path.read_text(encoding="utf-8"))
            existing_records = existing_package.get("records", [])
        except (json.JSONDecodeError, OSError):
            existing_records = []
        if existing_records:
            existing_end = max(r["tradingDate"] for r in existing_records)
            cutoff = (date.fromisoformat(existing_end) - timedelta(days=lookback_days)).isoformat()
            effective_start = max(start, min(cutoff, end))

    new_rows = export_daily_bars.fetch_rows(symbol, effective_start, end)
    new_records = export_daily_bars.package_records(new_rows, symbol)
    new_dates = {r["tradingDate"] for r in new_records}
    kept_old = [r for r in existing_records if r["tradingDate"] < effective_start and r["tradingDate"] not in new_dates]
    combined = kept_old + new_records

    package = export_daily_bars.build_package(combined, symbol, start, end, "0.1.0")
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def export_fundamentals_for(symbol: str, period: str, unit_scale: int, output: Path) -> None:
    income_statement, ratio, cash_flow = export_fundamentals.fetch_tables(symbol, period)
    records = export_fundamentals.build_metric_records(symbol, income_statement, ratio, cash_flow)
    package = export_fundamentals.build_package(records, symbol, "0.1.0", unit_scale)
    path = output / export_fundamentals.output_filename(symbol, period)
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def daily_bars_current(symbol: str, entry: dict[str, Any], args: argparse.Namespace) -> bool:
    """Current if the recorded coverage already reaches from at least as early as --start through
    at least as late as --end, AND the file it should have produced is still actually on disk --
    not just trusting the checkpoint blindly, since the output file is the thing
    StockImportConfiguration's directory scan (and the owner) actually reads. A later --end (e.g.
    re-running tomorrow to pick up a new trading day) makes an old entry stale again rather than
    silently staying short a day forever -- export_daily_bars_for then only re-fetches the recent
    lookback window plus the new gap, not the whole range (--full-refresh forces the whole range)."""
    range_ = entry.get("daily_bars_range")
    return (entry.get("daily_bars") == DONE
            and range_ is not None
            and range_[0] <= args.start
            and range_[1] >= args.end
            and not args.full_refresh
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
            export_daily_bars_for(
                symbol, args.start, args.end, args.output, args.lookback_days, args.full_refresh)
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
    parser.add_argument("--lookback-days", type=int, default=90,
                         help="On an incremental re-run, always re-verify this many days before "
                              "the last previously-fetched date (default 90, ~one fiscal quarter), "
                              "not just fetch the new gap -- protects against a recent corporate "
                              "action retroactively rewriting recent history (whether Vnstock's "
                              "series is raw or pre-adjusted is unresolved, research.md G-02).")
    parser.add_argument("--full-refresh", action="store_true",
                         help="Ignore existing files/checkpoint for daily bars and re-fetch the "
                              "entire [--start, --end] range for every symbol. Costly (same as the "
                              "first-ever run) -- use occasionally (e.g. monthly) as a safety net "
                              "against a corporate action older than --lookback-days, not every run.")
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
