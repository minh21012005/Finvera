"""Retry tập trung tại SDK; không thay HTTP hay cần provider thật."""
from functools import wraps
import sys
import types

import pytest
import requests
from tenacity import retry, stop_after_attempt

import provider_retry as policy


class Clock:
    now = 1000.0
    def __call__(self):
        return self.now
    def sleep(self, seconds):
        self.now += seconds


@pytest.mark.parametrize("failure,count", [
    (TimeoutError(), 1), (ValueError("schema"), 1),
    (ConnectionError("Failed to fetch data: 403 - Forbidden"), 1),
    (ConnectionError("Failed to fetch data: 503 - Service Unavailable"), 1),
])
def test_attempt_budget_and_quota_wrapper_preserved(failure, count):
    calls, quota = [], []
    clock = Clock()
    def gate(fn):
        @wraps(fn)
        def wrapped(*a, **kw):
            quota.append(1)
            return fn(*a, **kw)
        return wrapped
    class Adapter:
        @gate
        @retry(stop=stop_after_attempt(3))
        def history(self):
            calls.append(clock())
            raise failure
    policy.configure_sdk((Adapter,), clock=clock, sleep=clock.sleep)
    Adapter.history.retry.sleep = clock.sleep
    with pytest.raises(type(failure)):
        Adapter().history()
    assert len(calls) == count
    assert quota == [1]


def test_pacing_counts_attempts_across_methods():
    clock, calls = Clock(), []
    class Adapter:
        @retry(stop=stop_after_attempt(3))
        def first(self):
            calls.append(clock())
        @retry(stop=stop_after_attempt(3))
        def second(self):
            calls.append(clock())
    policy.configure_sdk((Adapter,), calls_per_minute=40, clock=clock, sleep=clock.sleep)
    for _ in range(5):
        Adapter().first()
        Adapter().second()
    assert all(b - a >= 1.5 for a, b in zip(calls, calls[1:]))


def test_real_adapter_one_attempt_without_http_hook(monkeypatch):
    def offline(*a, **kw):
        response = requests.Response()
        response.status_code = 200
        response._content = b"{}"
        return response
    monkeypatch.setattr(requests.sessions.Session, "request", offline)
    import vnai
    monkeypatch.setattr(vnai, "setup", lambda: None)
    monkeypatch.setitem(sys.modules, "vnstock.core.utils.agents",
                        types.SimpleNamespace(init_agent_environment=lambda **kw: True))
    from vnstock import Quote, Finance, Listing, Company
    # Khôi phục cấu hình SDK sau test, kể cả method thừa kế từ BaseAdapter.
    for cls in (Quote, Finance, Listing, Company):
        for base in cls.__mro__:
            for member in vars(base).values():
                controller = getattr(member, "retry", None)
                if controller is not None:
                    for key in ("stop", "retry", "wait", "before", "reraise", "sleep"):
                        monkeypatch.setattr(controller, key, getattr(controller, key))
    monkeypatch.setattr(policy, "ACTIVE", False)
    policy.install()
    assert requests.sessions.Session.request is offline
    method = Quote.history.__wrapped__
    clock, calls = Clock(), []
    policy.configure_sdk((Quote,), clock=clock, sleep=clock.sleep)
    method.retry.sleep = clock.sleep
    adapter = object.__new__(Quote)
    def fail(*a, **kw):
        calls.append(1)
        raise requests.ReadTimeout()
    monkeypatch.setattr(adapter, "_delegate_to_provider", fail, raising=False)
    with pytest.raises(requests.ReadTimeout):
        method(adapter, start="2026-06-01", end="2026-09-09")
    assert calls == [1]


def test_queue_and_sdk_have_three_total_attempts():
    clock, calls = Clock(), []
    class Adapter:
        @retry(stop=stop_after_attempt(3))
        def history(self):
            calls.append(clock())
            raise TimeoutError()
    policy.configure_sdk((Adapter,), clock=clock, sleep=clock.sleep)
    Adapter.history.retry.sleep = clock.sleep
    result = policy.collect_available(["ACB"], lambda symbol: Adapter().history(),
                                      clock=clock, sleep=clock.sleep)
    assert result == {"ACB": None}
    assert calls == [1000.0, 1120.0, 1240.0]
