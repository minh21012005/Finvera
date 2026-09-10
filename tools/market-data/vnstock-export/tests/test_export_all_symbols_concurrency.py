"""NFR-003: queue song song không mất kết quả hoặc vượt số worker."""
import argparse
import json
import threading
import time

import pytest

import export_all_symbols as crawl


@pytest.mark.parametrize("workers", [1, 5])
def test_dataset_results_are_identical_and_checkpoint_atomic(tmp_path, workers):
    args = argparse.Namespace(workers=workers, output=tmp_path, period="quarter", full_refresh=False,
                              start="2019-01-01", end="2026-09-09", lookback_days=90, unit_scale=1)
    seen, running, peak = [], 0, 0
    lock = threading.Lock()
    writer_threads = []
    path = tmp_path / "checkpoint.json"
    checkpoint = {"symbols": {}}

    def worker(symbol, key, entry, options):
        nonlocal running, peak
        with lock:
            running += 1
            peak = max(peak, running)
            seen.append((symbol, key))
        time.sleep(0.002)
        entry[key] = "done"
        entry[key + "_proof"] = symbol + key
        with lock:
            running -= 1
        return entry

    original_save = crawl.save_checkpoint
    def save(target, state):
        writer_threads.append(threading.get_ident())
        original_save(target, state)
        assert json.loads(target.read_text())["symbols"] == state["symbols"]

    from unittest.mock import patch
    symbols = [f"S{i:03d}" for i in range(15)]
    with patch.object(crawl, "save_checkpoint", save):
        crawl.run_queue(symbols, args, checkpoint, path, worker=worker)
    assert 1 <= peak <= workers
    assert len(seen) == len(set(seen)) == 45
    assert set(writer_threads) == {threading.get_ident()}
    for symbol in symbols:
        for key in crawl.DATASETS:
            assert checkpoint["symbols"][symbol][key + "_proof"] == symbol + key


def test_worker_bug_is_not_reported_as_success(tmp_path):
    args = argparse.Namespace(workers=2, output=tmp_path, period="quarter", full_refresh=False,
                              start="2019-01-01", end="2026-09-09", lookback_days=90, unit_scale=1)
    def worker(*args):
        raise RuntimeError("bug")
    checkpoint = {"symbols": {}}
    with pytest.raises(RuntimeError, match="bug"):
        crawl.run_queue(["A"], args, checkpoint, tmp_path / "checkpoint.json", worker=worker)
    assert checkpoint.get("status") not in {"COMPLETE", "PARTIAL"}
