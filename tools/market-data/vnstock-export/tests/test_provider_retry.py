"""Feature 026: the shared retry that stops a 30-second read timeout from ending a six-hour refresh
in its first minute, and the wiring of it into every previously bare provider call."""
from __future__ import annotations

import pytest

import export_equity_profile
import export_history
import export_instrument_reference
import export_sector_reference_vci
import provider_retry


class Blink(Exception):
    """What vnstock raises for a dropped connection: often a bare error carrying the message."""


def timeout_then(values, times=2, exc=None):
    """A callable that fails `times` times with a network-looking error, then returns `values`."""
    state = {"calls": 0}

    def action():
        state["calls"] += 1
        if state["calls"] <= times:
            raise (exc or Blink("HTTPSConnectionPool(host='trading.vietcap.com.vn', port=443): Read timed out."))
        return values

    action.state = state
    return action


# ── the module itself ────────────────────────────────────────────────────────

def test_transient_failure_is_retried_then_succeeds():
    action = timeout_then("frame")
    waited: list[float] = []

    assert provider_retry.call("probe", action, sleep=waited.append, log=lambda _m: None) == "frame"
    assert action.state["calls"] == 3          # two blinks, then the answer
    assert waited == [5.0, 20.0]               # the ladder the crawl already used


def test_exhausted_ladder_names_the_call():
    action = timeout_then("never", times=99)

    with pytest.raises(provider_retry.ProviderCallFailed) as raised:
        provider_retry.call("universe symbols_by_exchange", action, sleep=lambda _s: None, log=lambda _m: None)

    assert "universe symbols_by_exchange" in str(raised.value)
    assert action.state["calls"] == 3          # first attempt plus the two ladder steps


def test_a_data_problem_is_not_retried():
    # A schema failure is a fact about the response; repeating the request only burns the window.
    calls = {"n": 0}

    def action():
        calls["n"] += 1
        raise ValueError("Vnstock symbols_by_exchange schema is missing an expected column")

    with pytest.raises(ValueError):
        provider_retry.call("probe", action, sleep=lambda _s: None, log=lambda _m: None)
    assert calls["n"] == 1


def test_network_failure_is_recognised_through_a_cause_chain():
    inner = TimeoutError("read timeout")
    outer = ValueError("api request failed")
    outer.__cause__ = inner
    assert provider_retry.is_network_failure(outer)
    assert not provider_retry.is_network_failure(ValueError("no symbols were returned"))


def test_no_retry_when_the_caller_asks_for_none():
    action = timeout_then("frame")
    with pytest.raises(provider_retry.ProviderCallFailed):
        provider_retry.call("probe", action, waits=(), sleep=lambda _s: None, log=lambda _m: None)
    assert action.state["calls"] == 1


# ── every call that used to be bare ──────────────────────────────────────────

class _Listing:
    """Stands in for vnstock.Listing, timing out twice per method before answering."""

    def __init__(self, frames, times=2):
        self._actions = {name: timeout_then(frame, times=times) for name, frame in frames.items()}

    def __getattr__(self, name):
        action = self._actions.get(name)
        if action is None:
            raise AttributeError(name)
        return action

    def calls(self, name):
        return self._actions[name].state["calls"]


@pytest.mark.parametrize("module, function, expected_rows", [
    # Each of these used to be a bare call: one 30-second timeout ended the whole refresh.
    (export_instrument_reference, "fetch_universe", 2),   # both listed stocks
    (export_equity_profile, "fetch_universe", 2),
    (export_equity_profile, "fetch_delisted", 0),         # nothing in the frame is DELISTED
])
def test_stage_one_universe_calls_survive_two_timeouts(monkeypatch, module, function, expected_rows, pandas_frame):
    listing = _Listing({"symbols_by_exchange": pandas_frame})
    monkeypatch.setattr(provider_retry, "time", _NoSleep)
    monkeypatch.setitem(__import__("sys").modules, "vnstock", _FakeVnstock(listing))

    result = getattr(module, function)()

    assert listing.calls("symbols_by_exchange") == 3      # two blinks, then the answer
    assert len(result) == expected_rows


def test_index_history_survives_two_timeouts(monkeypatch):
    import pandas as pd

    frame = pd.DataFrame([{"time": "2026-09-04", "close": 1846.55, "volume": 1_000}])
    action = timeout_then(frame)

    class _Quote:
        def __init__(self, symbol=None, source=None):
            self.symbol = symbol

        def history(self, **_kwargs):
            return action()

    monkeypatch.setattr(provider_retry, "time", _NoSleep)
    monkeypatch.setitem(__import__("sys").modules, "vnstock", _FakeVnstockQuote(_Quote))

    rows = export_history.fetch_index_rows("VNINDEX", "2026-09-01", "2026-09-04")

    assert action.state["calls"] == 3
    assert rows and rows[0]["close"] == 1846.55


class _NoSleep:
    @staticmethod
    def sleep(_seconds):
        return None


class _FakeVnstockQuote:
    def __init__(self, quote_cls):
        self.Quote = quote_cls


class _FakeVnstock:
    def __init__(self, listing):
        self._listing = listing

    def Listing(self, source=None):  # noqa: N802 -- mirrors vnstock's class name
        return self._listing

    def __getattr__(self, name):
        raise AttributeError(name)


@pytest.fixture
def pandas_frame():
    import pandas as pd

    return pd.DataFrame([
        {"symbol": "VNM", "type": "STOCK", "exchange": "HSX", "organ_name": "Vinamilk"},
        {"symbol": "MBB", "type": "STOCK", "exchange": "HSX", "organ_name": "MB Bank"},
    ])
