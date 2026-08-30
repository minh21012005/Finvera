import importlib.util
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1]))  # export_all_symbols imports sibling exporters
MODULE_PATH = Path(__file__).parents[1] / "export_all_symbols.py"
SPEC = importlib.util.spec_from_file_location("export_all_symbols", MODULE_PATH)
mod = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(mod)


def pkg(*period_ends):
    return {"records": [{"periodEnd": e} for e in period_ends]}


def test_quarter_package_is_current_until_next_quarter_disclosure_deadline():
    p = pkg("2026-03-31", "2026-06-30")
    assert mod.fundamentals_package_stale(p, date(2026, 8, 30), "quarter") is False   # Q3 not due yet
    assert mod.fundamentals_package_stale(p, date(2026, 11, 20), "quarter") is True   # Q3 due ~Nov 14


def test_annual_package_is_current_until_next_audited_deadline():
    p = pkg("2024-12-31", "2025-12-31")
    assert mod.fundamentals_package_stale(p, date(2026, 8, 30), "year") is False
    assert mod.fundamentals_package_stale(p, date(2027, 4, 15), "year") is True


def test_empty_package_is_always_stale():
    assert mod.fundamentals_package_stale({"records": []}, date(2026, 8, 30), "quarter") is True


def test_wait_for_quota_blocks_until_the_minute_window_has_room():
    windows = iter([
        {"usage": 58, "limit": 60, "remaining": 2, "reset_in_seconds": 12.0},
        {"usage": 0, "limit": 60, "remaining": 60, "reset_in_seconds": 59.0},
    ])
    slept = []
    waited = mod.wait_for_quota(10, status=lambda: next(windows), sleep=slept.append, log=lambda *_: None)
    assert slept == [12.5] and waited == 12.5
    assert mod.wait_for_quota(10, status=lambda: None, sleep=slept.append) == 0.0   # vnai absent -> no pacing


def test_rate_limit_failures_are_transient_and_retried_next_run():
    args = type("A", (), {"period": "quarter", "output": __import__("pathlib").Path("nonexistent"), "start": "2023-01-01", "end": "2026-08-30", "full_refresh": False})()
    assert mod.is_transient_failure("failed:RetryError") is True
    assert mod.is_transient_failure("failed:ValueError") is False
    assert mod.is_finished("X", {"daily_bars": "failed:RetryError", "fundamentals": "failed:ValueError", "fundamentals_annual": "failed:ValueError"}, args) is False
    assert mod.is_finished("X", {"daily_bars": "failed:ValueError", "fundamentals": "failed:ValueError", "fundamentals_annual": "failed:ValueError"}, args) is True


def test_run_dataset_retries_once_after_a_rate_limit_then_records_success(monkeypatch):
    monkeypatch.setattr(mod, "wait_for_quota", lambda *_a, **_k: 0.0)
    monkeypatch.setattr(mod.time, "sleep", lambda *_: None)
    monkeypatch.setattr(mod, "quota_status", lambda: {"reset_in_seconds": 1})
    calls = {"n": 0}

    class RetryError(Exception):
        pass

    def action():
        calls["n"] += 1
        if calls["n"] == 1:
            raise RetryError("provider limit")

    entry = {}
    mod.run_dataset(entry, "daily_bars", "daily_bars", action, lambda: entry.__setitem__("daily_bars", "done"), lambda: None)
    assert calls["n"] == 2 and entry["daily_bars"] == "done"
