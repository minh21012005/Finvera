"""Owner-run, resumable full-universe exporter: loops export_daily_bars.py and
export_fundamentals_vci.py (statements from VCI, Feature 018) over every KBS-listed symbol, checkpointed so an
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
import concurrent.futures
import itertools
import json
import os
import sys
import threading
import time
from datetime import UTC, date, datetime, timedelta
from pathlib import Path
from typing import Any

import export_daily_bars
import export_fundamentals
import export_fundamentals_vci
import provider_retry

CHECKPOINT_FILE = "full-universe-checkpoint.json"
DONE = "done"


def load_checkpoint(path: Path) -> dict[str, Any]:
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    return {"generatedAt": None, "symbols": {}}


# Feature 026: with workers running in parallel the checkpoint is the one shared mutable thing.
# It is written after every symbol, so a crash mid-write used to be able to truncate hours of
# progress even in the serial version; writing to a sibling temp file and replacing makes the file
# either the old one or the new one, never half of either.
CHECKPOINT_LOCK = threading.Lock()
PRINT_LOCK = threading.Lock()


def save_checkpoint(path: Path, checkpoint: dict[str, Any]) -> None:
    with CHECKPOINT_LOCK:
        checkpoint["generatedAt"] = datetime.now(UTC).isoformat().replace("+00:00", "Z")
        payload = json.dumps(checkpoint, ensure_ascii=False, indent=2)
        temp = path.with_name(path.name + ".tmp")
        temp.write_text(payload, encoding="utf-8")
        os.replace(temp, path)


def say(message: str) -> None:
    """Progress from several workers, one line at a time."""
    with PRINT_LOCK:
        print(message, flush=True)


class TokenBucket:
    """Our own cap on provider calls per minute.

    vnai keeps its own usage counters, but they are plain lists mutated from any calling thread
    while `wait_for_quota` reads them, so a filter-and-reassign racing an append can *under*-count --
    the dangerous direction for a limit whose breach is charged to the owner's account (specs/026
    research R-003). This bucket is the guarantee; `wait_for_quota` stays as the second line.
    """

    def __init__(self, calls_per_minute: float) -> None:
        self.capacity = max(1.0, float(calls_per_minute))
        self.tokens = self.capacity
        self.refill_per_second = self.capacity / 60.0
        self.updated = time.monotonic()
        self.lock = threading.Lock()

    def take(self, count: int = 1, sleep=time.sleep) -> float:
        """Block until `count` tokens are available. Returns the seconds waited."""
        waited = 0.0
        while True:
            with self.lock:
                now = time.monotonic()
                self.tokens = min(self.capacity, self.tokens + (now - self.updated) * self.refill_per_second)
                self.updated = now
                if self.tokens >= count:
                    self.tokens -= count
                    return waited
                shortfall = count - self.tokens
                pause = min(5.0, max(0.05, shortfall / self.refill_per_second))
            sleep(pause)
            waited += pause


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

    frame = provider_retry.call("universe symbols_by_exchange",
                                lambda: Listing(source="vci").symbols_by_exchange())
    required = {"symbol", "type", "exchange"}
    if not required.issubset(frame.columns):
        raise ValueError("Vnstock symbols_by_exchange schema is missing an expected column")
    stocks = frame[(frame["type"] == "STOCK") & (frame["exchange"].isin(["HSX", "HNX", "UPCOM"]))]
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
            if existing_package.get("toolVersion") == export_daily_bars.TOOL_VERSION:
                existing_records = existing_package.get("records", [])
                recorded_start = existing_package.get("rangeStart")
                if recorded_start and recorded_start > start:
                    # Q-37: --start moved earlier than the range this file was fetched for; the
                    # incremental window could never reach the new gap, so re-fetch the whole range once.
                    existing_records = []
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

    package = export_daily_bars.build_package(combined, symbol, start, end, export_daily_bars.TOOL_VERSION)
    path.write_text(json.dumps(package, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def export_fundamentals_for(symbol: str, period: str, unit_scale: int, output: Path) -> None:
    """Feature 018 (ADR-0011, Q-57): fundamentals come from VCI statements; the KBS statement
    pages carry another period's content and are no longer crawled. Same file names, same
    package contract, so the import stage is unchanged."""
    export_fundamentals_vci.export_symbol(symbol, period, output, unit_scale)


FUNDAMENTALS_TOOL_VERSION = export_fundamentals_vci.TOOL_VERSION


def daily_bars_current(symbol: str, entry: dict[str, Any], args: argparse.Namespace) -> bool:
    """Current if the recorded coverage already reaches from at least as early as --start through
    at least as late as --end, AND the file it should have produced is still actually on disk --
    not just trusting the checkpoint blindly, since the output file is the thing
    StockImportConfiguration's directory scan (and the owner) actually reads. A later --end (e.g.
    re-running tomorrow to pick up a new trading day) makes an old entry stale again rather than
    silently staying short a day forever -- export_daily_bars_for then only re-fetches the recent
    lookback window plus the new gap, not the whole range (--full-refresh forces the whole range)."""
    path = args.output / export_daily_bars.output_filename(symbol)
    if not path.exists():
        return False
    try:
        package = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return False
    range_ = entry.get("daily_bars_range")
    records = package.get("records", [])
    latest_record_date = max((record.get("tradingDate") for record in records), default=None)
    return (entry.get("daily_bars") == DONE
            and range_ is not None
            and range_[0] <= args.start
            and range_[1] >= args.end
            and latest_record_date is not None
            and latest_record_date >= args.end
            and not args.full_refresh
            and package.get("toolVersion") == export_daily_bars.TOOL_VERSION)


ANNUAL_PERIOD = "year"

# Q-30: fiscal-period staleness. A quarterly package is stale once the NEXT quarter's
# disclosure deadline has passed (period end + 3 months, then Circular 96/2020 quarterly
# lag ~45 days); an annual package once the next fiscal year's audited deadline has passed
# (period end + 12 months + ~90 days). Mirrors daily bars' date-aware currency rule.
QUARTER_NEXT_PERIOD_DAYS = 92 + 45
ANNUAL_NEXT_PERIOD_DAYS = 366 + 90


def fundamentals_package_stale(package: dict[str, Any], today: date, period: str) -> bool:
    """True when a newer fiscal period should exist for this package's symbol by `today`."""
    ends = [r.get("periodEnd") for r in package.get("records", []) if r.get("periodEnd")]
    if not ends:
        return True
    latest_end = date.fromisoformat(max(ends))
    horizon = ANNUAL_NEXT_PERIOD_DAYS if period == ANNUAL_PERIOD else QUARTER_NEXT_PERIOD_DAYS
    return today > latest_end + timedelta(days=horizon)


def fundamentals_annual_current(symbol: str, entry: dict[str, Any], args: argparse.Namespace) -> bool:
    """Cash-flow facts (FREE_CASH_FLOW) exist only in the annual dataset (Feature 008 R-002/R-004),
    so every symbol also gets an annual package alongside the requested period."""
    path = args.output / export_fundamentals.output_filename(symbol, ANNUAL_PERIOD)
    if not path.exists():
        return False
    try:
        package = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return False
    return (entry.get("fundamentals_annual") == DONE
            and not args.full_refresh
            and package.get("toolVersion") == FUNDAMENTALS_TOOL_VERSION
            and not fundamentals_package_stale(package, date.fromisoformat(args.end), ANNUAL_PERIOD))


def fundamentals_current(symbol: str, entry: dict[str, Any], args: argparse.Namespace) -> bool:
    path = args.output / export_fundamentals.output_filename(symbol, args.period)
    if not path.exists():
        return False
    try:
        package = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return False
    return (entry.get("fundamentals") == DONE
            and entry.get("fundamentals_period") == args.period
            and not args.full_refresh
            and package.get("toolVersion") == FUNDAMENTALS_TOOL_VERSION
            and not fundamentals_package_stale(package, date.fromisoformat(args.end), args.period))


# ── Provider quota pacing (Q-39) ─────────────────────────────────────────────
# One symbol costs several provider calls. VCI statements (Feature 018) are three calls per
# period (income statement, balance sheet, cash flow; 2-3 s each, no per-minute cap observed
# on 2026-08-31), the KBS daily-bar pass is paced by vnai's 60/min window.
# A fixed per-symbol sleep therefore cannot keep the run under the limit; vnai's own
# retry (2 attempts, 1-2 s back-off) cannot either, and an exhausted retry surfaces as a
# tenacity RetryError that used to be recorded as a permanent failure. We read vnai's
# usage counters directly and wait for room BEFORE each dataset, and treat a rate-limit
# failure as transient (retried after the window resets, and again on the next run).
CALLS_PER_DATASET = {"daily_bars": 2, "fundamentals": 3, "fundamentals_annual": 3}
TRANSIENT_FAILURE_NAMES = ("RetryError", "RateLimitExceeded", "NetworkError")
# Feature 018 R-008: a dropped connection ("Connection aborted", ConnectionResetError 10054, read timeout...) is
# not a fact about the symbol. vnstock surfaces it as a ValueError, which used to settle the dataset
# as failed forever; it is now retried in-run and, if still failing, recorded as transient.
# Feature 026: the classification and the ladder now live in provider_retry so the four stage-1
# exporters share this exact definition instead of having none at all.
NETWORK_EXCEPTION_NAMES = provider_retry.NETWORK_EXCEPTION_NAMES
NETWORK_MESSAGE_PATTERN = provider_retry.NETWORK_MESSAGE_PATTERN
NETWORK_RETRY_WAITS_SECONDS = provider_retry.NETWORK_RETRY_WAITS_SECONDS
# Feature 018: "the provider has no statements for this symbol and period" (VCI serves only annual
# statements for many small UPCoM names — A32, ACE, AGX, APT, BBH, BCP...) is not a defect of the
# symbol but a state that changes when the company files: it is re-checked after this many days
# instead of being settled forever like a genuine failure.
UNAVAILABLE_RECHECK_DAYS = 35
# Feature 026 R-008 (Q-61): "the symbol has not traded enough sessions yet" is the same shape of
# fact — a newly listed symbol crosses MIN_RECORDS on its own, so settling it forever locked it out
# permanently (measured: 48 symbols, DMX already exportable while its 2026-09-01 failure still
# stood). Its window is shorter because 20 sessions is ~4 trading weeks, so a handful of re-checks
# covers the whole wait.
INSUFFICIENT_SESSIONS_RECHECK_DAYS = 7
RECHECK_DAYS_BY_FAILURE = {
    "NoStatementsAvailable": UNAVAILABLE_RECHECK_DAYS,
    "InsufficientSessions": INSUFFICIENT_SESSIONS_RECHECK_DAYS,
}
UNAVAILABLE_FAILURE_NAMES = tuple(RECHECK_DAYS_BY_FAILURE)
MAX_QUOTA_WAIT_SECONDS = 65.0
# Set in main() once the worker count is known; None keeps the serial behaviour exactly.
CALL_BUCKET: "TokenBucket | None" = None


def quota_status() -> dict[str, Any] | None:
    """vnai's minute window {usage, limit, remaining, reset_in_seconds}, or None when unavailable."""
    try:
        from vnai.beam.quota import guardian
        return guardian.get_limit_status().get("minute_limit")
    except Exception:  # noqa: BLE001 -- pacing must never break the export
        return None


def wait_for_quota(needed: int, status=quota_status, sleep=time.sleep, log=print) -> float:
    """Block until the provider minute window has room for `needed` calls. Returns seconds waited."""
    waited = 0.0
    while True:
        window = status()
        if window is None or window.get("remaining", needed) >= needed:
            return waited
        pause = min(MAX_QUOTA_WAIT_SECONDS, max(1.0, float(window.get("reset_in_seconds", 5)) + 0.5))
        if waited == 0.0:
            log(f"  quota: {window.get('usage')}/{window.get('limit')} used, need {needed}; waiting {pause:.0f}s")
        sleep(pause)
        waited += pause


def is_unavailable_failure(value: str) -> bool:
    return any(value == f"failed:{name}" for name in UNAVAILABLE_FAILURE_NAMES)


def recheck_days_for(value: str) -> int:
    """The re-check window of a recorded `failed:<Name>` value, by failure class."""
    for name, days in RECHECK_DAYS_BY_FAILURE.items():
        if value == f"failed:{name}":
            return days
    return UNAVAILABLE_RECHECK_DAYS


def recheck_due(checked_at: str, today: date, days: int = UNAVAILABLE_RECHECK_DAYS) -> bool:
    """True when a provider-unavailable dataset should be tried again (no timestamp = due)."""
    try:
        checked = date.fromisoformat(checked_at)
    except ValueError:
        return True
    return (today - checked).days >= days


is_network_failure = provider_retry.is_network_failure


def classify_failure(exc: BaseException) -> str:
    """Failure class recorded in the checkpoint: the exception type, or NetworkError for dropped connections."""
    if isinstance(exc, SystemExit):
        return "RateLimitExceeded"
    if is_network_failure(exc):
        return "NetworkError"
    return type(exc).__name__


def failure_tool_version(key: str) -> str:
    return export_daily_bars.TOOL_VERSION if key == "daily_bars" else FUNDAMENTALS_TOOL_VERSION


def is_transient_failure(value: Any) -> bool:
    return any(str(value) == f"failed:{name}" for name in TRANSIENT_FAILURE_NAMES)


def run_dataset(entry: dict[str, Any], key: str, label: str, action, on_success, on_failure) -> None:
    """Run one dataset export with quota pacing; a rate-limit failure is retried once after the window resets."""
    rate_limit_retried = False
    network_retries = 0
    while True:
        if CALL_BUCKET is not None:
            CALL_BUCKET.take(CALLS_PER_DATASET[key])
        wait_for_quota(CALLS_PER_DATASET[key])
        try:
            action()
            on_success()
            entry.pop(f"{key}_failed_tool_version", None)
            print(f"  {label}: OK")
            return
        except (Exception, SystemExit) as exc:  # noqa: BLE001 -- one bad symbol must not stop the batch
            # vnai ends its rate-limit handling with sys.exit("Rate limit exceeded ..."), which is a
            # SystemExit (not an Exception) and would otherwise terminate the whole export.
            if isinstance(exc, SystemExit) and "rate limit" not in str(exc).lower():
                raise
            name = classify_failure(exc)
            if name == "NetworkError" and network_retries < len(NETWORK_RETRY_WAITS_SECONDS):
                pause = NETWORK_RETRY_WAITS_SECONDS[network_retries]
                network_retries += 1
                print(f"  {label}: connection dropped ({type(exc).__name__}), retrying in {pause:.0f}s")
                time.sleep(pause)
                continue
            if name in TRANSIENT_FAILURE_NAMES and name != "NetworkError" and not rate_limit_retried:
                # The provider enforces the limit server-side; the local counter can lag it, so
                # wait a full window (not just the remainder of this one) before the retry.
                rate_limit_retried = True
                print(f"  {label}: rate-limited, retrying in {MAX_QUOTA_WAIT_SECONDS:.0f}s")
                time.sleep(MAX_QUOTA_WAIT_SECONDS)
                continue
            entry[key] = f"failed:{name}"
            entry[f"{key}_checked_at"] = date.today().isoformat()
            # Feature 018 R-008: a settled failure belongs to the exporter version that produced it; a failure
            # recorded by an older version (or before this field existed) is retried once.
            entry[f"{key}_failed_tool_version"] = failure_tool_version(key)
            on_failure()
            if name in UNAVAILABLE_FAILURE_NAMES:
                print(f"  {label}: UNAVAILABLE at provider ({name}); "
                      f"re-checked after {RECHECK_DAYS_BY_FAILURE[name]} days")
            else:
                print(f"  {label}: FAILED ({name})")
            return


def process_symbol(
    symbol: str, args: argparse.Namespace, checkpoint: dict[str, Any], checkpoint_path: Path
) -> None:
    entry = checkpoint["symbols"].setdefault(symbol, {})

    if not daily_bars_current(symbol, entry, args):
        def bars_ok():
            entry["daily_bars"] = DONE
            entry["daily_bars_range"] = [args.start, args.end]
        run_dataset(entry, "daily_bars", "daily_bars",
                    lambda: export_daily_bars_for(symbol, args.start, args.end, args.output, args.lookback_days, args.full_refresh),
                    bars_ok, lambda: entry.pop("daily_bars_range", None))
        save_checkpoint(checkpoint_path, checkpoint)

    if not fundamentals_current(symbol, entry, args):
        def fundamentals_ok():
            entry["fundamentals"] = DONE
            entry["fundamentals_period"] = args.period
        run_dataset(entry, "fundamentals", "fundamentals",
                    lambda: export_fundamentals_for(symbol, args.period, args.unit_scale, args.output),
                    fundamentals_ok, lambda: entry.pop("fundamentals_period", None))
        save_checkpoint(checkpoint_path, checkpoint)

    if args.period != ANNUAL_PERIOD and not fundamentals_annual_current(symbol, entry, args):
        run_dataset(entry, "fundamentals_annual", "fundamentals(annual)",
                    lambda: export_fundamentals_for(symbol, ANNUAL_PERIOD, args.unit_scale, args.output),
                    lambda: entry.__setitem__("fundamentals_annual", DONE), lambda: None)
        save_checkpoint(checkpoint_path, checkpoint)


def is_finished(symbol: str, entry: dict[str, Any], args: argparse.Namespace) -> bool:
    """A symbol counts as finished once each dataset is current for this run's parameters, or has
    failed -- failures are recorded, not silently retried forever (without --retry-failed), so the
    run still terminates on symbols Vnstock genuinely cannot serve (e.g. some banks' fundamentals
    shape differs, per research.md G-01), rather than retrying them every single run."""
    def settled_failure(key: str) -> bool:
        value = str(entry.get(key, ""))
        # Q-39: a rate-limit failure is transient and is always retried on the next run.
        if not value.startswith("failed") or is_transient_failure(value):
            return False
        # Feature 018 R-008: a failure recorded by an older exporter version (or before the version was recorded)
        # is not evidence about this version -- retry it once, then it settles with the version.
        if entry.get(f"{key}_failed_tool_version") != failure_tool_version(key):
            return False
        # Feature 018: provider-unavailable statements are re-checked once the recheck window passed.
        if is_unavailable_failure(value):
            return not recheck_due(str(entry.get(f"{key}_checked_at", "")),
                                   date.fromisoformat(args.end), recheck_days_for(value))
        return True

    daily_bars_settled = daily_bars_current(symbol, entry, args) or settled_failure("daily_bars")
    fundamentals_settled = fundamentals_current(symbol, entry, args) or settled_failure("fundamentals")
    annual_settled = (args.period == ANNUAL_PERIOD
                      or fundamentals_annual_current(symbol, entry, args)
                      or settled_failure("fundamentals_annual"))
    return daily_bars_settled and fundamentals_settled and annual_settled


def run_symbols(symbols, args, checkpoint, checkpoint_path, interval_seconds, label="") -> None:
    """Process symbols, `--workers` at a time.

    Concurrency changes when work happens, not what is written: each symbol reads and writes its own
    package files, the checkpoint is keyed by symbol and guarded, and the end-of-run transient pass
    iterates a list, so nothing depends on completion order (specs/026 research R-004).
    `--workers 1` reproduces the previous serial order and, at default settings, its pacing --
    see `--workers`'s own help text for the one narrow case where it does not.
    """
    total = len(symbols)
    prefix = f"{label} " if label else ""
    done = itertools.count(1)

    def one(symbol: str) -> None:
        index = next(done)
        say(f"[{prefix}{index}/{total}] {symbol}")
        process_symbol(symbol, args, checkpoint, checkpoint_path)

    if args.workers <= 1:
        for position, symbol in enumerate(symbols, start=1):
            one(symbol)
            if position < total:
                time.sleep(interval_seconds)
        return

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers,
                                               thread_name_prefix="crawl") as pool:
        futures = [pool.submit(one, symbol) for symbol in symbols]
        for future in concurrent.futures.as_completed(futures):
            # process_symbol records its own per-symbol failures; anything escaping it is a bug in
            # this file, and must not be swallowed into a silently short crawl.
            future.result()


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
                              "3 fundamentals calls, all back-to-back, then this pause). Only takes "
                              "effect with --workers 1 -- with the default --workers 5, pacing comes "
                              "entirely from --max-calls-per-minute's token bucket instead, and this "
                              "flag is ignored.")
    parser.add_argument("--max-symbols", type=int, default=None,
                         help="Process at most this many remaining symbols this run -- do a small "
                              "bounded run first (e.g. 5) before letting this run unattended for hours.")
    parser.add_argument("--output", type=Path, default=Path("output"))
    parser.add_argument("--workers", type=int, default=5,
                        help="How many symbols to fetch at once (default 5). The run is latency-bound, "
                             "not quota-bound: measured 2026-09-06 it used 7.3 of 60 allowed calls per "
                             "minute. --workers 1 reproduces the old strictly-serial ORDER and pacing "
                             "for default settings; note that --max-calls-per-minute's token bucket is "
                             "created either way, so with --workers 1 plus an aggressively raised "
                             "--requests-per-minute it now also caps at that ceiling, which the old code "
                             "did not enforce in serial mode.")
    parser.add_argument("--max-calls-per-minute", type=float, default=40.0,
                        help="Our own ceiling on provider calls per minute across all workers, kept "
                             "below the provider's 60 (specs/026 R-003: vnai's own counter can "
                             "under-count under concurrency, so it cannot be the guarantee).")
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
            for key in ("daily_bars", "fundamentals", "fundamentals_annual"):
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
    global CALL_BUCKET
    CALL_BUCKET = TokenBucket(args.max_calls_per_minute)
    run_symbols(remaining, args, checkpoint, checkpoint_path, interval_seconds)

    # Q-39: one more pass over symbols whose only problem was the rate limit, so a run
    # normally finishes clean instead of leaving them for the next run.
    transient = [s for s in remaining if any(is_transient_failure(checkpoint["symbols"].get(s, {}).get(k))
                                             for k in ("daily_bars", "fundamentals", "fundamentals_annual"))]
    if transient:
        print()
        print(f"Retrying {len(transient)} rate-limited symbols after a full window...")
        time.sleep(MAX_QUOTA_WAIT_SECONDS)
        for symbol in transient:
            entry = checkpoint["symbols"][symbol]
            for key in ("daily_bars", "fundamentals", "fundamentals_annual"):
                if is_transient_failure(entry.get(key)):
                    del entry[key]
        run_symbols(transient, args, checkpoint, checkpoint_path, interval_seconds, label="retry")

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
