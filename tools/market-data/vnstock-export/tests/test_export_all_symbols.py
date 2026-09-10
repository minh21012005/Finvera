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


def test_rate_limit_failures_are_transient_and_retried_next_run():
    args = type("A", (), {"period": "quarter", "output": __import__("pathlib").Path("nonexistent"), "start": "2023-01-01", "end": "2026-08-30", "full_refresh": False})()
    assert mod.is_transient_failure("failed:RetryError") is True
    assert mod.is_transient_failure("failed:ValueError") is False
    assert mod.is_finished("X", {"daily_bars": "failed:RetryError", "fundamentals": "failed:ValueError", "fundamentals_annual": "failed:ValueError"}, args) is False
    settled = {"daily_bars": "failed:ValueError", "fundamentals": "failed:ValueError", "fundamentals_annual": "failed:ValueError",
               "daily_bars_failed_tool_version": mod.export_daily_bars.TOOL_VERSION,
               "fundamentals_failed_tool_version": mod.FUNDAMENTALS_TOOL_VERSION,
               "fundamentals_annual_failed_tool_version": mod.FUNDAMENTALS_TOOL_VERSION}
    assert mod.is_finished("X", settled, args) is True


def test_run_dataset_schedules_rate_limit_without_inline_retry(monkeypatch):
    monkeypatch.setattr(mod.time, "sleep", lambda *_: None)
    calls = {"n": 0}

    class RetryError(Exception):
        pass

    def action():
        calls["n"] += 1
        if calls["n"] == 1:
            raise RetryError("provider limit")

    entry = {}
    mod.run_dataset(entry, "daily_bars", "daily_bars", action, lambda: entry.__setitem__("daily_bars", "done"), lambda: None)
    assert calls["n"] == 1 and entry["daily_bars"] == "failed:RetryError"
    assert entry["daily_bars_next_retry_at"] > 0


def test_vnai_rate_limit_system_exit_does_not_terminate_the_export(monkeypatch):
    monkeypatch.setattr(mod.time, "sleep", lambda *_: None)
    calls = {"n": 0}

    def action():
        calls["n"] += 1
        raise SystemExit("Rate limit exceeded. ... Process terminated.")

    entry = {}
    mod.run_dataset(entry, "fundamentals", "fundamentals", action, lambda: None, lambda: None)
    assert calls["n"] == 1  # Queue owns the next attempt.
    assert entry["fundamentals"] == "failed:RateLimitExceeded"  # transient -> retried at end of run / next run
    assert mod.is_transient_failure(entry["fundamentals"])


def test_provider_unavailable_statements_are_rechecked_after_the_window(tmp_path):
    # Feature 018: A32/ACE/... have annual statements on VCI but no quarterly ones; that is a state,
    # not a permanent failure -- settled for 35 days, then tried again (no --retry-failed needed).
    import argparse
    args = argparse.Namespace(start="2023-01-01", end="2026-08-31", period="quarter", output=tmp_path,
                              full_refresh=False, retry_failed=False, lookback_days=90, unit_scale=1)
    fresh = {"daily_bars": "failed:ValueError", "fundamentals": "failed:NoStatementsAvailable",
             "fundamentals_checked_at": "2026-08-30", "fundamentals_annual": "failed:ValueError",
             "daily_bars_failed_tool_version": mod.export_daily_bars.TOOL_VERSION,
             "fundamentals_failed_tool_version": mod.FUNDAMENTALS_TOOL_VERSION,
             "fundamentals_annual_failed_tool_version": mod.FUNDAMENTALS_TOOL_VERSION}
    stale = dict(fresh, fundamentals_checked_at="2026-07-01")
    missing = {k: v for k, v in fresh.items() if k != "fundamentals_checked_at"}
    assert mod.is_finished("X", fresh, args) is True
    assert mod.is_finished("X", stale, args) is False
    assert mod.is_finished("X", missing, args) is False
    assert mod.is_unavailable_failure("failed:NoStatementsAvailable") is True
    assert mod.is_unavailable_failure("failed:ValueError") is False
    assert mod.recheck_due("2026-07-27", mod.date(2026, 8, 31)) is True
    assert mod.recheck_due("2026-07-28", mod.date(2026, 8, 31)) is False


def test_dropped_connections_are_transient_and_retried_in_run(monkeypatch):
    # Feature 018 R-008: DCH 2026-08-31 -- vnstock raised ValueError("API request failed: ('Connection aborted.',
    # ConnectionResetError(10054, ...))"); that is a network event, not a fact about the symbol.
    wrapped = ValueError("API request failed: ('Connection aborted.', ConnectionResetError(10054, 'forcibly closed'))")
    assert mod.classify_failure(wrapped) == "NetworkError"
    chained = ValueError("no data")
    chained.__cause__ = ConnectionResetError(10054, "An existing connection was forcibly closed by the remote host")
    assert mod.classify_failure(chained) == "NetworkError"
    assert mod.classify_failure(ValueError("symbol has no bars")) == "ValueError"
    assert mod.is_transient_failure("failed:NetworkError") is True

    monkeypatch.setattr(mod, "NETWORK_RETRY_WAITS_SECONDS", (0.0, 0.0))
    monkeypatch.setattr(mod.time, "sleep", lambda seconds: None)
    calls = {"n": 0}

    def flaky():
        calls["n"] += 1
        if calls["n"] < 3:
            raise wrapped

    entry = {}
    mod.run_dataset(entry, "daily_bars", "daily_bars", flaky, lambda: entry.__setitem__("daily_bars", "done"), lambda: None)
    assert calls["n"] == 1 and entry["daily_bars"] == "failed:NetworkError"
    assert entry["daily_bars_retry_attempts"] == 1

    def always_down():
        raise wrapped

    entry = {}
    mod.run_dataset(entry, "daily_bars", "daily_bars", always_down, lambda: None, lambda: None)
    assert entry["daily_bars"] == "failed:NetworkError"
    assert entry["daily_bars_failed_tool_version"] == mod.export_daily_bars.TOOL_VERSION


def test_failures_recorded_by_another_exporter_version_do_not_settle(tmp_path):
    # Feature 018 R-008: 153 checkpoint entries carried failed:ValueError written by the exporter before the VCI
    # switch / before NoStatementsAvailable existed; they must be retried once, not skipped forever.
    import argparse
    args = argparse.Namespace(start="2023-01-01", end="2026-08-31", period="quarter", output=tmp_path,
                              full_refresh=False, retry_failed=False, lookback_days=90, unit_scale=1)
    legacy = {"daily_bars": "failed:ValueError", "fundamentals": "failed:ValueError", "fundamentals_annual": "failed:ValueError"}
    assert mod.is_finished("X", legacy, args) is False
    current = dict(legacy,
                   daily_bars_failed_tool_version=mod.export_daily_bars.TOOL_VERSION,
                   fundamentals_failed_tool_version=mod.FUNDAMENTALS_TOOL_VERSION,
                   fundamentals_annual_failed_tool_version=mod.FUNDAMENTALS_TOOL_VERSION)
    assert mod.is_finished("X", current, args) is True
    stale_version = dict(current, fundamentals_failed_tool_version="0.7.0")
    assert mod.is_finished("X", stale_version, args) is False


def test_newly_listed_symbols_are_rechecked_instead_of_settled_forever(tmp_path):
    """Feature 026 R-008 (Q-61): `InsufficientSessions` is a state that changes with every session.

    Before the fix it arrived as a bare ValueError and `settled_failure` treated it as permanent:
    48 symbols on the 2026-09-07 checkpoint, 42 of them settled on 2026-09-01 and never asked
    again -- including DMX, which was already exportable when this was measured.
    """
    import argparse
    args = argparse.Namespace(start="2019-01-01", end="2026-09-07", period="quarter", output=tmp_path,
                              full_refresh=False, retry_failed=False, lookback_days=90, unit_scale=1)
    base = {"fundamentals": "failed:NoStatementsAvailable", "fundamentals_checked_at": "2026-09-07",
            "fundamentals_annual": "failed:NoStatementsAvailable", "fundamentals_annual_checked_at": "2026-09-07",
            "fundamentals_failed_tool_version": mod.FUNDAMENTALS_TOOL_VERSION,
            "fundamentals_annual_failed_tool_version": mod.FUNDAMENTALS_TOOL_VERSION,
            "daily_bars": "failed:InsufficientSessions",
            "daily_bars_failed_tool_version": mod.export_daily_bars.TOOL_VERSION}

    fresh = dict(base, daily_bars_checked_at="2026-09-06")       # 1 day old -> still settled
    due = dict(base, daily_bars_checked_at="2026-08-31")         # 7 days old -> asked again
    assert mod.is_finished("LPS", fresh, args) is True
    assert mod.is_finished("DMX", due, args) is False

    # Its own, shorter window -- 20 sessions is ~4 trading weeks, not a reporting cycle.
    assert mod.recheck_days_for("failed:InsufficientSessions") == 7
    assert mod.recheck_days_for("failed:NoStatementsAvailable") == 35
    assert mod.is_unavailable_failure("failed:InsufficientSessions") is True
    # A 30-day-old "no statements" is still settled on the same day the 7-day bars entry re-opens.
    assert mod.recheck_due("2026-08-08", mod.date(2026, 9, 7), mod.recheck_days_for("failed:NoStatementsAvailable")) is False
    assert mod.recheck_due("2026-08-31", mod.date(2026, 9, 7), mod.recheck_days_for("failed:InsufficientSessions")) is True

    # It is recorded, not retried inside the run: nothing about waiting 65 s makes a session appear.
    assert mod.is_transient_failure("failed:InsufficientSessions") is False
