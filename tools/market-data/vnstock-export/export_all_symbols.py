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
import heapq
import itertools
import json
import math
import os
import sys
import threading
import time
from datetime import UTC, date, datetime, timedelta
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

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
    lookback window plus the new gap, not the whole range (--full-refresh forces the whole range).

    Feature 026 R-009 (Q-62): coverage is what we *asked* for, never "the symbol traded on --end".
    The old rule also required `latest_record_date >= args.end`, which a delisted, suspended or
    illiquid symbol can never satisfy -- 603 of 1,522 symbols held a complete package whose newest
    session predates --end (ART 2022-11-18, TTZ 2022-12-02), so they were never `is_finished`, were
    re-fetched on every re-run of the same command, and the exporter's "nothing left to do" could
    never be reached. `range_[1] >= args.end` already states that this file was fetched for this
    end date; the provider returning nothing after 2022 is its answer, not a reason to ask again.

    Consequence, stated rather than hidden: within one --end, a session that the provider has not
    published yet is not picked up by a second run on the same day. The next day's run moves --end,
    the entry goes stale again, and the 90-day lookback fetches the missed session -- so the gap is
    bounded by one day and self-healing. `--full-refresh` bypasses this check entirely when the
    owner wants the whole range re-pulled now."""
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
            # The file must agree with the checkpoint about the window it was fetched for, and must
            # actually hold sessions -- the checkpoint alone is never the evidence (Q-62).
            and package.get("rangeEnd") is not None
            and package.get("rangeEnd") >= args.end
            and latest_record_date is not None
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
    """Gọi dataset một lượt; SDK không retry lồng, queue tối đa ba lượt."""
    try:
        action()
    except (Exception, SystemExit) as exc:
        if isinstance(exc, SystemExit) and "rate limit" not in str(exc).lower():
            raise
        if (isinstance(exc, OSError) and not is_network_failure(exc)
                and not provider_retry.is_provider_http_failure(exc)):
            raise  # Lỗi ghi đĩa không phải dữ liệu thiếu.
        name = classify_failure(exc)
        entry[key] = f"failed:{name}"
        entry[f"{key}_checked_at"] = datetime.now(ZoneInfo("Asia/Ho_Chi_Minh")).date().isoformat()
        entry[f"{key}_failed_tool_version"] = failure_tool_version(key)
        on_failure()
        if is_transient_failure(entry[key]):
            attempt = entry.get(f"{key}_retry_attempts", 0) + 1
            entry[f"{key}_retry_attempts"] = attempt
            if attempt >= provider_retry.MAX_DATASET_ATTEMPTS:
                entry.pop(f"{key}_next_retry_at", None)
                say(f"{label}: EXHAUSTED reason={name} rounds={attempt}; retry_next_refresh=true")
                return
            entry[f"{key}_next_retry_at"] = time.time() + provider_retry.RETRY_WAIT_SECONDS
            say(f"{label}: WAITING reason={name} round={attempt} retry_at={entry[f'{key}_next_retry_at']:.0f}")
        else:
            entry.pop(f"{key}_next_retry_at", None)
            say(f"{label}: {'UNAVAILABLE' if name in UNAVAILABLE_FAILURE_NAMES else 'FAILED'} reason={name}")
        return
    on_success()
    for suffix in ("failed_tool_version", "retry_attempts", "next_retry_at", "retry_window", "checked_at"):
        entry.pop(f"{key}_{suffix}", None)
    say(f"{label}: OK")


DATASETS = ("daily_bars", "fundamentals", "fundamentals_annual")


def settled_failure(entry, key, args):
    """Một quy tắc chung cho skip và hoàn tất, kể cả dataset thiếu dữ liệu."""
    value = str(entry.get(key, ""))
    if not value.startswith("failed") or is_transient_failure(value):
        return False
    if entry.get(f"{key}_failed_tool_version") != failure_tool_version(key):
        return False
    if is_unavailable_failure(value):
        return not recheck_due(str(entry.get(f"{key}_checked_at", "")),
                               date.fromisoformat(args.end), recheck_days_for(value))
    return True


def dataset_current(symbol, entry, key, args):
    if key == "fundamentals_annual" and args.period == ANNUAL_PERIOD:
        return True
    current = {"daily_bars": daily_bars_current, "fundamentals": fundamentals_current,
               "fundamentals_annual": fundamentals_annual_current}[key]
    return current(symbol, entry, args) or settled_failure(entry, key, args)


def process_dataset(symbol, key, entry, args):
    """Worker chỉ sửa bản sao dataset; luồng điều phối ghi checkpoint."""
    if key == "daily_bars":
        def success():
            entry[key] = DONE
            entry["daily_bars_range"] = [args.start, args.end]
        action = lambda: export_daily_bars_for(symbol, args.start, args.end, args.output,
                                               args.lookback_days, args.full_refresh)
        failure = lambda: entry.pop("daily_bars_range", None)
    else:
        period = ANNUAL_PERIOD if key == "fundamentals_annual" else args.period
        action = lambda: export_fundamentals_for(symbol, period, args.unit_scale, args.output)
        def success():
            entry[key] = DONE
            if key == "fundamentals":
                entry["fundamentals_period"] = args.period
        failure = lambda: entry.pop("fundamentals_period", None) if key == "fundamentals" else None
    run_dataset(entry, key, f"symbol={symbol} dataset={key}", action, success, failure)
    return entry


def run_queue(symbols, args, checkpoint, checkpoint_path, clock=None, sleep=None, worker=None):
    """Queue hữu hạn; lỗi hết ngân sách vẫn giữ transient cho đợt sau."""
    clock, sleep = clock or time.time, sleep or time.sleep
    worker = worker or process_dataset
    window = [args.start, args.end, args.period, bool(args.full_refresh),
              args.lookback_days, args.unit_scale]
    resuming = checkpoint.get("runWindow") == window and checkpoint.get("status") in {"RUNNING", "WAITING"}
    checkpoint["runWindow"] = window
    queue, sequence = [], itertools.count()
    counts = {"done": 0, "unavailable": 0, "failed": 0}
    for symbol in symbols:
        entry = checkpoint["symbols"].setdefault(symbol, {})
        for key in DATASETS:
            if key == "fundamentals_annual" and args.period == ANNUAL_PERIOD:
                continue
            if dataset_current(symbol, entry, key, args) or (resuming and acknowledged(symbol, key, entry, args, window)):
                state = entry.get(key)
                counts["done" if state == DONE else "unavailable" if is_unavailable_failure(state) else "failed"] += 1
                continue
            if not resuming or entry.get(f"{key}_retry_window") != window:
                entry.pop(f"{key}_next_retry_at", None)
                entry.pop(f"{key}_retry_attempts", None)
            if is_transient_failure(entry.get(key)) and entry.get(f"{key}_retry_attempts", 0) >= provider_retry.MAX_DATASET_ATTEMPTS:
                entry.pop(f"{key}_next_retry_at", None)
                counts["failed"] += 1
                continue
            # Không giữ cooldown dài của bản cũ sau khi nâng sang lịch 2 phút.
            due = min(entry.get(f"{key}_next_retry_at", 0), clock() + provider_retry.RETRY_WAIT_SECONDS)
            if f"{key}_next_retry_at" in entry:
                entry[f"{key}_next_retry_at"] = due
            heapq.heappush(queue, (due, next(sequence), symbol, key))
    running = {}
    total_symbols = len(set(symbols))

    def progress():
        # Mã chỉ xong khi không còn dataset chạy hoặc đang chờ retry.
        remaining = {item[2] for item in queue} | {symbol for symbol, _ in running.values()}
        completed = sum(counts.values())
        total_datasets = completed + len(queue) + len(running)
        return (f"symbols={total_symbols - len(remaining)}/{total_symbols} "
                f"symbols_remaining={len(remaining)} datasets={completed}/{total_datasets}")

    last_heartbeat = -float("inf")
    pool = concurrent.futures.ThreadPoolExecutor(max_workers=args.workers, thread_name_prefix="dataset")
    try:
        while queue or running:
            while queue and len(running) < args.workers and queue[0][0] <= clock():
                _, _, symbol, key = heapq.heappop(queue)
                entry = dict(checkpoint["symbols"][symbol])
                entry[f"{key}_retry_window"] = window
                future = pool.submit(worker, symbol, key, entry, args)
                running[future] = (symbol, key)
            finished = [f for f in running if f.done()]
            for future in finished:
                symbol, key = running.pop(future)
                result = future.result()
                entry = checkpoint["symbols"][symbol]
                # Chỉ merge namespace dataset: hai worker cùng mã không ghi đè nhau.
                for field in list(entry):
                    if field == key or field.startswith(key + "_"):
                        if key == "fundamentals" and field.startswith("fundamentals_annual"):
                            continue
                        del entry[field]
                for field, value in result.items():
                    if field == key or field.startswith(key + "_"):
                        if key == "fundamentals" and field.startswith("fundamentals_annual"):
                            continue
                        entry[field] = value
                state = entry.get(key)
                if is_transient_failure(state) and entry.get(f"{key}_retry_attempts", 0) < provider_retry.MAX_DATASET_ATTEMPTS:
                    heapq.heappush(queue, (entry[f"{key}_next_retry_at"], next(sequence), symbol, key))
                else:
                    entry.pop(f"{key}_next_retry_at", None)
                    entry[f"{key}_completed_window"] = window
                    counts["done" if state == DONE else "unavailable" if is_unavailable_failure(state) else "failed"] += 1
                checkpoint["status"] = "RUNNING"
                save_checkpoint(checkpoint_path, checkpoint)
            if clock() - last_heartbeat >= 30:
                checkpoint["status"] = "RUNNING" if running else "WAITING"
                say(f"status={checkpoint['status']} {progress()} done={counts['done']} unavailable={counts['unavailable']} "
                    f"failed={counts['failed']} running={len(running)} pending={len(queue)} "
                    f"next_retry_at={queue[0][0] if queue else 0:.0f}")
                save_checkpoint(checkpoint_path, checkpoint)
                last_heartbeat = clock()
            if running:
                concurrent.futures.wait(running, timeout=0.2, return_when=concurrent.futures.FIRST_COMPLETED)
            elif queue:
                sleep(min(30, max(0.01, queue[0][0] - clock())))
        checkpoint["status"] = "PARTIAL" if counts["unavailable"] or counts["failed"] else "COMPLETE"
        checkpoint["summary"] = counts
        save_checkpoint(checkpoint_path, checkpoint)
        say(f"status={checkpoint['status']} {progress()} done={counts['done']} unavailable={counts['unavailable']} failed={counts['failed']} pending=0")
    finally:
        pool.shutdown(wait=True, cancel_futures=True)


def acknowledged(symbol, key, entry, args, window):
    """Resume cùng đợt: full-refresh/BCTC cũ đã fetch không cần tải lần nữa."""
    if entry.get(key) != DONE or entry.get(f"{key}_completed_window") != window:
        return False
    name = (export_daily_bars.output_filename(symbol) if key == "daily_bars" else
            export_fundamentals.output_filename(symbol, ANNUAL_PERIOD if key == "fundamentals_annual" else args.period))
    try:
        package = json.loads((args.output / name).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return False
    if not package.get("records") or package.get("toolVersion") != failure_tool_version(key):
        return False
    if key == "daily_bars":
        return package.get("rangeStart", "9999") <= args.start and package.get("rangeEnd", "") >= args.end
    return True


def is_finished(symbol: str, entry: dict[str, Any], args: argparse.Namespace) -> bool:
    """Hoàn tất khi từng dataset có kết quả hoặc thiếu dữ liệu đã được ghi rõ."""
    return all(dataset_current(symbol, entry, key, args) for key in DATASETS)


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
                        help="Legacy option; HTTP pacing uses --max-calls-per-minute.")
    parser.add_argument("--max-symbols", type=int, default=None,
                         help="Process at most this many remaining symbols this run -- do a small "
                              "bounded run first (e.g. 5) before letting this run unattended for hours.")
    parser.add_argument("--output", type=Path, default=Path("output"))
    parser.add_argument("--workers", type=int, default=5,
                        help="Maximum concurrent datasets (default 5).")
    parser.add_argument("--max-calls-per-minute", type=float, default=40.0,
                        help="Pacing attempts SDK, gồm retry; không phải số HTTP chính xác (mặc định 40).")
    parser.add_argument("--retry-failed", action="store_true",
                         help="Clear previously recorded failures so this run retries them instead "
                              "of treating them as finished (use after a transient outage; a symbol "
                              "that fails for a structural reason, e.g. an unsupported fundamentals "
                              "shape, will just fail again).")
    args = parser.parse_args()
    if args.end is None:
        args.end = datetime.now(ZoneInfo("Asia/Ho_Chi_Minh")).date().isoformat()

    if args.workers < 1 or any(not math.isfinite(n) or n <= 0 for n in
                              (args.max_calls_per_minute, args.requests_per_minute)):
        parser.error("workers và tốc độ phải dương")
    if args.max_symbols is not None and args.max_symbols < 1:
        parser.error("max-symbols phải dương")
    if date.fromisoformat(args.start) > date.fromisoformat(args.end) or args.lookback_days < 0:
        parser.error("cửa sổ ngày/lookback không hợp lệ")
    provider_retry.install(args.max_calls_per_minute)
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
    scope = universe if args.max_symbols is None else remaining
    run_queue(scope, args, checkpoint, checkpoint_path)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
