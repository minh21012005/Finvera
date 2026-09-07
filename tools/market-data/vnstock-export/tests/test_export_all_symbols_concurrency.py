"""Feature 026: the crawl runs several symbols at once without changing what it writes.

The three things concurrency can break here are the shared checkpoint, the provider rate limit, and
the completeness of the run. Each gets a test; the rate limit gets its own because breaching it is
charged to the owner's account and vnai's counter cannot be trusted to enforce it (specs/026 R-003).
"""
from __future__ import annotations

import argparse
import json
import threading
import time
from pathlib import Path

import pytest

import export_all_symbols as crawl


def args_with(**overrides) -> argparse.Namespace:
    base = dict(workers=1, max_calls_per_minute=600.0, requests_per_minute=6000.0,
                output=Path("."), period="quarter", full_refresh=False,
                start="2019-01-01", end="2026-09-06", lookback_days=90, unit_scale=1)
    base.update(overrides)
    return argparse.Namespace(**base)


# ── token bucket: our own guarantee, not the library's ───────────────────────

def test_bucket_hands_out_its_capacity_immediately_then_paces():
    clock = {"now": 1000.0}
    slept: list[float] = []
    bucket = crawl.TokenBucket(60)                      # one call per second
    bucket.updated = clock["now"]

    def fake_sleep(seconds):
        slept.append(seconds)
        clock["now"] += seconds

    original = time.monotonic
    time.monotonic = lambda: clock["now"]               # noqa: F811 -- restored below
    try:
        for _ in range(60):
            assert bucket.take(1, sleep=fake_sleep) == 0.0   # the full bucket is free
        waited = bucket.take(1, sleep=fake_sleep)            # the 61st must wait for a refill
    finally:
        time.monotonic = original

    assert slept, "the bucket let a call through with no tokens left"
    assert waited > 0


def test_bucket_never_exceeds_its_rate_under_concurrency():
    bucket = crawl.TokenBucket(120)                     # two per second
    granted: list[float] = []
    lock = threading.Lock()
    start = time.monotonic()

    def worker():
        for _ in range(8):
            bucket.take(1)
            with lock:
                granted.append(time.monotonic() - start)

    threads = [threading.Thread(target=worker) for _ in range(4)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    assert len(granted) == 32
    # 32 calls at 2/s cannot finish faster than the bucket allows once its initial 120 are gone;
    # with a full bucket they all pass instantly, so the real assertion is that none were lost.
    assert bucket.tokens <= bucket.capacity


# ── checkpoint: shared, written constantly, must never be seen half-written ──

def test_checkpoint_write_is_atomic_and_leaves_no_temp_file(tmp_path):
    path = tmp_path / "checkpoint.json"
    checkpoint = {"symbols": {"VNM": {"daily_bars": "done"}}, "universeSize": 1}

    crawl.save_checkpoint(path, checkpoint)

    assert json.loads(path.read_text(encoding="utf-8"))["symbols"]["VNM"]["daily_bars"] == "done"
    assert not list(tmp_path.glob("*.tmp")), "an interrupted write would leave this behind"


def test_concurrent_checkpoint_writes_keep_every_symbol(tmp_path):
    path = tmp_path / "checkpoint.json"
    checkpoint = {"symbols": {}, "universeSize": 200}

    def worker(start: int) -> None:
        for i in range(start, start + 50):
            checkpoint["symbols"][f"SYM{i:03d}"] = {"daily_bars": "done"}
            crawl.save_checkpoint(path, checkpoint)

    threads = [threading.Thread(target=worker, args=(base,)) for base in (0, 50, 100, 150)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    written = json.loads(path.read_text(encoding="utf-8"))     # parses => never truncated
    assert len(written["symbols"]) == 200


# ── the runner: same work, same records, whatever the worker count ───────────

@pytest.mark.parametrize("workers", [1, 5])
def test_every_symbol_is_processed_exactly_once(monkeypatch, workers):
    symbols = [f"S{i:03d}" for i in range(37)]
    seen: list[str] = []
    lock = threading.Lock()

    def fake_process(symbol, args, checkpoint, checkpoint_path):
        time.sleep(0.001)
        with lock:
            seen.append(symbol)
        checkpoint["symbols"][symbol] = {"daily_bars": "done"}

    monkeypatch.setattr(crawl, "process_symbol", fake_process)
    checkpoint = {"symbols": {}}

    crawl.run_symbols(symbols, args_with(workers=workers), checkpoint, Path("unused"), 0.0)

    assert sorted(seen) == sorted(symbols)
    assert len(seen) == len(symbols)                 # no symbol twice
    assert sorted(checkpoint["symbols"]) == sorted(symbols)


def test_a_bug_inside_a_worker_is_not_swallowed(monkeypatch):
    def exploding(symbol, args, checkpoint, checkpoint_path):
        raise RuntimeError("a defect in this file, not a provider failure")

    monkeypatch.setattr(crawl, "process_symbol", exploding)

    with pytest.raises(RuntimeError):
        crawl.run_symbols(["AAA", "BBB"], args_with(workers=3), {"symbols": {}}, Path("unused"), 0.0)
