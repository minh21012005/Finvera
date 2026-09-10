"""FR-009/FR-012–014: queue phục hồi mà không cần lệnh chạy lại."""
import argparse
import json
import time

import pytest

import export_all_symbols as crawl
import provider_retry


def args(tmp_path, **overrides):
    values = dict(start="2019-01-01", end="2026-09-09", period="quarter",
                  full_refresh=False, output=tmp_path, workers=2, lookback_days=90, unit_scale=1)
    return argparse.Namespace(**(values | overrides))


class Clock:
    now = 1000
    def __call__(self):
        return self.now
    def sleep(self, seconds):
        self.now += seconds


@pytest.mark.parametrize("status", [403, 404])
def test_http_client_failure_does_not_abort_queue(tmp_path, status):
    calls = []
    def worker(symbol, key, entry, options):
        calls.append((symbol, key))
        def action():
            if symbol == "A":
                raise ConnectionError(f"Failed to fetch data: {status} - denied")
        crawl.run_dataset(entry, key, symbol, action,
                          lambda: entry.update({key: "done"}), lambda: None)
        return entry
    checkpoint = {"symbols": {}}
    crawl.run_queue(["A", "B"], args(tmp_path), checkpoint, tmp_path / "checkpoint.json", worker=worker)
    assert len(calls) == 6
    assert checkpoint["status"] == "PARTIAL"
    assert checkpoint["summary"] == {"done": 3, "failed": 3, "unavailable": 0}


def test_filesystem_failure_still_propagates():
    def action():
        raise PermissionError("disk")
    with pytest.raises(PermissionError):
        crawl.run_dataset({}, "daily_bars", "A", action, lambda: None, lambda: None)


def test_profile_workers_overlap_are_bounded_and_deduplicate():
    import threading
    barrier, lock = threading.Barrier(2), threading.Lock()
    running, peak, calls = 0, 0, []
    def action(symbol):
        nonlocal running, peak
        with lock:
            calls.append(symbol)
            running += 1
            peak = max(peak, running)
        barrier.wait(timeout=5)
        with lock:
            running -= 1
        return symbol
    result = provider_retry.collect_available(["A", "A", "B", "C", "D"], action, workers=2)
    assert peak == 2
    assert sorted(calls) == ["A", "B", "C", "D"]
    assert result == {symbol: symbol for symbol in calls}


def test_permanent_outage_finishes_partial_keeps_file_and_reopens_next_run(tmp_path, monkeypatch, capsys):
    from types import SimpleNamespace
    clock, calls = Clock(), []
    monkeypatch.setattr(crawl, "time", SimpleNamespace(time=clock, sleep=clock.sleep))
    path = tmp_path / crawl.export_daily_bars.output_filename("A")
    original = '{"records": [], "rangeEnd": "2026-09-07"}'
    path.write_text(original)
    monkeypatch.setattr(crawl.export_daily_bars, "fetch_rows",
                        lambda *a: (_ for _ in ()).throw(TimeoutError()))
    def worker(symbol, key, entry, options):
        if key != "daily_bars":
            entry[key] = "done"
            return entry
        calls.append(key)
        return crawl.process_dataset(symbol, key, entry, options)
    checkpoint = {"symbols": {}}
    for run in (1, 2):
        started = clock()
        crawl.run_queue(["A"], args(tmp_path), checkpoint, tmp_path / "checkpoint.json",
                        clock=clock, sleep=clock.sleep, worker=worker)
        assert len(calls) == run * 3
        assert clock() - started == 240
        assert checkpoint["status"] == "PARTIAL"
        assert checkpoint["summary"]["failed"] == 1
        entry = checkpoint["symbols"]["A"]
        assert entry["daily_bars"] == "failed:NetworkError"
        assert "daily_bars_next_retry_at" not in entry
        assert not crawl.is_finished("A", entry, args(tmp_path))
        assert path.read_text() == original
        log = capsys.readouterr().out
        assert "status=WAITING symbols=0/1 symbols_remaining=1 datasets=2/3" in log
        assert "status=PARTIAL symbols=1/1 symbols_remaining=0 datasets=3/3" in log


@pytest.mark.parametrize("used,remaining", [(2, 1), (3, 0)])
def test_resume_keeps_consumed_budget(tmp_path, monkeypatch, used, remaining):
    from types import SimpleNamespace
    clock, calls = Clock(), []
    monkeypatch.setattr(crawl, "time", SimpleNamespace(time=clock, sleep=clock.sleep))
    window = ["2019-01-01", "2026-09-09", "quarter", False, 90, 1]
    checkpoint = {"status": "WAITING", "runWindow": window, "symbols": {"A": {
        "daily_bars": "failed:NetworkError", "daily_bars_retry_attempts": used,
        "daily_bars_retry_window": window}}}
    def worker(symbol, key, entry, options):
        if key == "daily_bars":
            calls.append(1)
            crawl.run_dataset(entry, key, symbol, lambda: (_ for _ in ()).throw(TimeoutError()),
                              lambda: None, lambda: None)
        else:
            entry[key] = "done"
        return entry
    crawl.run_queue(["A"], args(tmp_path), checkpoint, tmp_path / "checkpoint.json",
                    clock=clock, sleep=clock.sleep, worker=worker)
    assert len(calls) == remaining
    assert checkpoint["status"] == "PARTIAL"


def test_bootstrap_and_profile_permanent_outage_are_bounded():
    clock, calls = Clock(), []
    def down(*a):
        calls.append(1)
        raise TimeoutError()
    with pytest.raises(provider_retry.ProviderCallFailed):
        provider_retry.wait_until_available("universe", down, clock=clock,
                                            sleep=clock.sleep, log=lambda _: None)
    assert len(calls) == 3 and clock() == 1240
    calls.clear()
    assert provider_retry.collect_available(["A"], down, clock=clock, sleep=clock.sleep) == {"A": None}
    assert len(calls) == 3 and clock() == 1480


def test_outage_recovery_skips_successes_and_records_terminal(tmp_path, monkeypatch):
    clock, calls = Clock(), []
    def worker(symbol, key, entry, options):
        calls.append((symbol, key))
        n = calls.count((symbol, key))
        if symbol == "BAD" and key == "daily_bars" and n < 3:
            entry[key] = "failed:NetworkError"
            entry[key + "_retry_attempts"] = n
            entry[key + "_next_retry_at"] = clock() + 120
        else:
            entry[key] = "done"
        return entry
    checkpoint = {"symbols": {}}
    path = tmp_path / "checkpoint.json"
    crawl.run_queue(["BAD", "GOOD"], args(tmp_path, full_refresh=True), checkpoint, path,
                    clock=clock, sleep=clock.sleep, worker=worker)
    assert calls.count(("BAD", "daily_bars")) == 3
    assert calls.count(("GOOD", "daily_bars")) == 1
    assert calls.index(("GOOD", "daily_bars")) < len(calls) - 1
    assert len(calls) == 8
    assert checkpoint["status"] == "COMPLETE"
    assert checkpoint["summary"] == {"done": 6, "unavailable": 0, "failed": 0}
    assert json.loads(path.read_text())["runWindow"][1] == "2026-09-09"


@pytest.mark.parametrize("due,expected", [(1100, 1100), (7200, 1120)])
def test_pending_checkpoint_respects_due_and_other_datasets(tmp_path, due, expected):
    clock = Clock()
    window = ["2019-01-01", "2026-09-09", "quarter", False, 90, 1]
    checkpoint = {"runWindow": window, "status": "WAITING", "symbols": {"A": {"daily_bars": "failed:NetworkError",
        "daily_bars_retry_window": window, "daily_bars_next_retry_at": due,
        "daily_bars_retry_attempts": 2}}}
    seen = []
    def worker(symbol, key, entry, options):
        seen.append((key, clock()))
        entry[key] = "done"
        return entry
    crawl.run_queue(["A"], args(tmp_path), checkpoint, tmp_path / "checkpoint.json",
                    clock=clock, sleep=clock.sleep, worker=worker)
    assert seen[-1] == ("daily_bars", expected)
    assert all(t == 1000 for k, t in seen if k != "daily_bars")


def test_fresh_unavailable_is_not_refetched_when_bars_stale(tmp_path):
    checkpoint = {"symbols": {"A": {
        "fundamentals": "failed:NoStatementsAvailable", "fundamentals_checked_at": "2026-09-09",
        "fundamentals_failed_tool_version": crawl.FUNDAMENTALS_TOOL_VERSION}}}
    seen = []
    def worker(symbol, key, entry, options):
        seen.append(key)
        entry[key] = "done"
        return entry
    crawl.run_queue(["A"], args(tmp_path), checkpoint, tmp_path / "checkpoint.json", worker=worker)
    assert "fundamentals" not in seen
    assert checkpoint["status"] == "PARTIAL"
    assert checkpoint["summary"]["unavailable"] == 1


def test_run_dataset_does_not_replay_successful_statements(monkeypatch):
    calls = []
    entry = {}
    def action():
        calls.append(1)
        raise TimeoutError()
    crawl.run_dataset(entry, "fundamentals", "A", action, lambda: None, lambda: None)
    assert calls == [1]
    assert entry["fundamentals_retry_attempts"] == 1
    assert entry["fundamentals_next_retry_at"] > time.time()


def test_storage_failure_propagates():
    with pytest.raises(PermissionError):
        crawl.run_dataset({}, "daily_bars", "A", lambda: (_ for _ in ()).throw(PermissionError()), lambda: None, lambda: None)


def test_bootstrap_waits_through_multiple_rounds_and_can_cancel(monkeypatch):
    clock, calls = Clock(), []
    def action():
        calls.append(clock())
        if len(calls) < 3:
            raise TimeoutError()
        return "OK"
    assert provider_retry.wait_until_available("universe", action, clock=clock,
        sleep=clock.sleep, log=lambda _: None) == "OK"
    assert len(calls) == 3
    assert clock() == 1000 + 2 * 120
    with pytest.raises(KeyboardInterrupt):
        provider_retry.wait_until_available("universe", lambda: (_ for _ in ()).throw(KeyboardInterrupt()))


def test_profile_failure_yields_to_next_symbol():
    clock, calls = Clock(), []
    def action(symbol):
        calls.append(symbol)
        if symbol == "A" and calls.count("A") == 1:
            raise TimeoutError()
        return symbol
    result = provider_retry.collect_available(["A", "B"], action, clock=clock, sleep=clock.sleep)
    assert calls == ["A", "B", "A"]
    assert result == {"A": "A", "B": "B"}


def test_full_refresh_restart_preserves_completed_dataset(tmp_path):
    window = ["2019-01-01", "2026-09-09", "quarter", True, 90, 1]
    entry = {"daily_bars": "done", "daily_bars_completed_window": window}
    package = {"toolVersion": crawl.export_daily_bars.TOOL_VERSION,
               "rangeStart": window[0], "rangeEnd": window[1],
               "records": [{"tradingDate": "2026-09-09"}]}
    path = tmp_path / crawl.export_daily_bars.output_filename("A")
    path.write_text(json.dumps(package))
    checkpoint = {"runWindow": window, "status": "WAITING", "symbols": {"A": entry}}
    seen = []
    def worker(symbol, key, entry, options):
        seen.append(key)
        entry[key] = "done"
        return entry
    crawl.run_queue(["A"], args(tmp_path, full_refresh=True), checkpoint,
                    tmp_path / "checkpoint.json", worker=worker)
    assert "daily_bars" not in seen
    # Mất file thì checkpoint không đủ bằng chứng để skip.
    path.unlink()
    assert not crawl.acknowledged("A", "daily_bars", entry, args(tmp_path), window)


def test_schema_failure_completes_as_partial_not_retry_forever(tmp_path):
    def worker(symbol, key, entry, options):
        crawl.run_dataset(entry, key, "A", lambda: (_ for _ in ()).throw(ValueError("schema")),
                          lambda: None, lambda: None)
        return entry
    checkpoint = {"symbols": {}}
    crawl.run_queue(["A"], args(tmp_path), checkpoint, tmp_path / "checkpoint.json", worker=worker)
    assert checkpoint["status"] == "PARTIAL"
    assert checkpoint["summary"]["failed"] == 3


def test_main_recovers_without_restart_and_keeps_end_date(tmp_path, monkeypatch):
    """Đường CLI thật + scheduler thật; provider và thời gian chờ được giả lập."""
    import sys
    from types import SimpleNamespace
    clock, calls = Clock(), []
    monkeypatch.setattr(crawl, "time", SimpleNamespace(time=clock, sleep=clock.sleep))
    monkeypatch.setattr(provider_retry, "install", lambda *_: None)
    monkeypatch.setattr(crawl, "fetch_symbol_universe", lambda: ["A", "B"])
    def bars(symbol, start, end, output, lookback, full):
        calls.append((symbol, end))
        if symbol == "A" and calls.count((symbol, end)) < 3:
            raise TimeoutError()
        (output / crawl.export_daily_bars.output_filename(symbol)).write_text(json.dumps({
            "toolVersion": crawl.export_daily_bars.TOOL_VERSION,
            "rangeStart": start, "rangeEnd": end, "records": [{"tradingDate": end}]}))
    monkeypatch.setattr(crawl, "export_daily_bars_for", bars)
    monkeypatch.setattr(crawl, "export_fundamentals_for", lambda *a: None)
    monkeypatch.setattr(sys, "argv", ["export_all_symbols.py", "--start", "2019-01-01",
        "--end", "2026-09-09", "--output", str(tmp_path)])
    assert crawl.main() == 0
    checkpoint = json.loads((tmp_path / crawl.CHECKPOINT_FILE).read_text())
    assert checkpoint["status"] == "COMPLETE"
    assert calls.count(("A", "2026-09-09")) == 3
    assert calls.count(("B", "2026-09-09")) == 1
    assert "daily_bars_next_retry_at" not in checkpoint["symbols"]["A"]
    assert checkpoint["summary"]["done"] == 6
