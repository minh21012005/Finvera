"""One definition of "the provider blinked" for every exporter (Feature 026, closes the stage-1 half
of P2-12).

`export_all_symbols.py` has always distinguished a dropped connection from a real failure and
retried the former, which is why a six-hour crawl settles with roughly one failed symbol. The four
exporters that run *before* it had no exception handling at all, so a single 30-second read timeout
on `symbols_by_exchange()` ended the whole refresh in its first minute — with
`$ErrorActionPreference = "Stop"` and `Assert-NativeSuccess`, a non-zero exit aborts everything.

This module holds the classification and the retry ladder once, so the two halves of the pipeline
cannot drift apart into two opinions about what "transient" means.

What is deliberately NOT retried: a schema or data problem (`SchemaMismatch`, a `ValueError` whose
message is not a network message, a missing column). Those are facts about the provider's response,
and repeating the request only wastes the window.
"""
from __future__ import annotations

import re
import math
import threading
import time
from typing import Any, Callable, Iterable, TypeVar

T = TypeVar("T")
ACTIVE = False
RETRY_WAIT_SECONDS = 120
MAX_DATASET_ATTEMPTS = 3

# Feature 018 R-008: a dropped connection ("Connection aborted", ConnectionResetError 10054, read
# timeout ...) is not a fact about the data. vnstock often surfaces it as a bare ValueError, so the
# message is matched as well as the type.
NETWORK_EXCEPTION_NAMES = frozenset({
    "ConnectionError", "ConnectionResetError", "ConnectionAbortedError", "ConnectTimeout", "ReadTimeout",
    "Timeout", "TimeoutError", "ChunkedEncodingError", "ProtocolError", "RemoteDisconnected", "SSLError",
    "MaxRetryError", "NewConnectionError", "IncompleteRead",
})
NETWORK_MESSAGE_PATTERN = re.compile(
    r"connection aborted|connection reset|forcibly closed|max retries exceeded|timed out|read timeout|"
    r"remote end closed|temporarily unavailable|api request failed|bad gateway|gateway time-?out|"
    r"service unavailable|name resolution|getaddrinfo", re.IGNORECASE)
NETWORK_RETRY_WAITS_SECONDS = (5.0, 20.0)


class ProviderCallFailed(RuntimeError):
    """A provider call that stayed broken through the whole ladder, naming the call it was."""


def is_provider_http_failure(exc: BaseException) -> bool:
    """Client SDK ném builtin ConnectionError cho HTTP status, cũng là OSError."""
    return isinstance(exc, ConnectionError) and bool(
        re.search(r"Failed to fetch data:\s*\d{3}", str(exc)))


def is_network_failure(exc: BaseException) -> bool:
    """True when the exception, or anything in its cause/context chain, is a dropped connection.

    Behaviour preserved exactly from the version this replaces in `export_all_symbols.py`: the
    `isinstance` check catches subclasses whose *name* is not in the set, and the chain walk is
    depth-capped so a self-referential cause cannot spin.
    """
    seen: set[int] = set()
    current: BaseException | None = exc
    chain = []
    while current is not None and id(current) not in seen and len(seen) < 8:
        seen.add(id(current))
        chain.append(current)
        current = current.__cause__ or current.__context__
    # Client vnstock 4.0.7 bọc HTTP status vào ConnectionError.
    statuses = [int(code) for item in chain for code in
                re.findall(r"Failed to fetch data:\s*(\d{3})", str(item))]
    if any(400 <= code < 500 and code not in {408, 429} for code in statuses):
        return False
    for current in chain:
        if type(current).__name__ in NETWORK_EXCEPTION_NAMES or isinstance(current, (ConnectionError, TimeoutError)):
            return True
        if NETWORK_MESSAGE_PATTERN.search(str(current)):
            return True
    return False


def call(
    label: str,
    action: Callable[[], T],
    waits: Iterable[float] = NETWORK_RETRY_WAITS_SECONDS,
    sleep: Callable[[float], Any] | None = None,
    log: Callable[[str], Any] | None = None,
) -> T:
    """Run `action`, retrying only transient network failures, then giving up with the call named.

    The waits are the same ladder the universe crawl uses, on top of whatever the provider library
    retries internally. A caller that wants today's fail-fast behaviour passes `waits=()`.
    """
    # Resolved here rather than as a default argument: a default binds `time.sleep` at import
    # time, which no caller or test can then substitute.
    sleep = sleep or time.sleep
    log = log or print
    if ACTIVE:
        return wait_until_available(label, action, sleep=sleep, log=log)
    attempts = list(waits)
    for index in range(len(attempts) + 1):
        try:
            return action()
        except (KeyboardInterrupt, SystemExit):
            raise
        except Exception as exc:  # noqa: BLE001 -- classified immediately below
            if not is_network_failure(exc) or index == len(attempts):
                if is_network_failure(exc):
                    raise ProviderCallFailed(
                        f"{label}: provider unreachable after {len(attempts) + 1} attempts "
                        f"({type(exc).__name__}: {exc})") from exc
                raise
            pause = attempts[index]
            log(f"  {label}: provider blinked ({type(exc).__name__}), retrying in {pause:.0f}s")
            sleep(pause)
    raise AssertionError("unreachable")


def is_transient(exc):
    """Quota và mạng cần lịch thử lại, không áp dụng cho schema."""
    return (is_network_failure(exc)
            or type(exc).__name__ == "RateLimitExceeded"
            or isinstance(exc, SystemExit) and "rate limit" in str(exc).lower())


def wait_until_available(label, action, sleep=None, log=None, clock=None):
    """Bootstrap bắt buộc: tối đa ba lượt, không chờ provider vô hạn."""
    sleep, log, clock = sleep or time.sleep, log or print, clock or time.time
    attempt = 0
    while True:
        try:
            return action()
        except (Exception, SystemExit) as exc:
            if not is_transient(exc):
                raise
            attempt += 1
            if attempt >= MAX_DATASET_ATTEMPTS:
                raise ProviderCallFailed(f"{label}: hết {attempt} lượt; prerequisite chưa có") from exc
            due = clock() + RETRY_WAIT_SECONDS
            while clock() < due:
                log(f"{label}: WAITING round={attempt} remaining={due - clock():.0f}s")
                sleep(min(30, due - clock()))


def collect_available(subjects, action, sleep=None, clock=None, workers=1):
    """Hồ sơ từng mã: lỗi mạng nhường lượt, tự quay lại, giữ kết quả đã lấy."""
    import heapq
    import itertools
    import concurrent.futures
    if workers < 1:
        raise ValueError("workers phải dương")
    sleep, clock = sleep or time.sleep, clock or time.time
    sequence = itertools.count()
    pending = [(0, next(sequence), symbol, 0) for symbol in dict.fromkeys(subjects)]
    heapq.heapify(pending)
    result = {}
    running = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        while pending or running:
            while pending and len(running) < workers and pending[0][0] <= clock():
                _, _, symbol, attempt = heapq.heappop(pending)
                running[pool.submit(action, symbol)] = (symbol, attempt)
            for future in [f for f in running if f.done()]:
                symbol, attempt = running.pop(future)
                try:
                    result[symbol] = future.result()
                except (Exception, SystemExit) as exc:
                    if not is_transient(exc):
                        raise
                    attempt += 1
                    if attempt >= MAX_DATASET_ATTEMPTS:
                        result[symbol] = None
                        print(f"symbol={symbol} dataset=equity-profile status=EXHAUSTED rounds={attempt}", flush=True)
                        continue
                    due = clock() + RETRY_WAIT_SECONDS
                    print(f"symbol={symbol} dataset=equity-profile status=WAITING retry_at={due:.0f}", flush=True)
                    heapq.heappush(pending, (due, next(sequence), symbol, attempt))
            if running:
                concurrent.futures.wait(running, timeout=0.2,
                                        return_when=concurrent.futures.FIRST_COMPLETED)
            elif pending:
                delay = max(0, pending[0][0] - clock())
                if delay:
                    print(f"equity-profile: WAITING done={len(result)} pending={len(pending)} remaining={delay:.0f}s", flush=True)
                    sleep(min(30, delay))
    return result


def configure_sdk(classes, calls_per_minute=40, clock=None, sleep=None):
    """Đặt retry ngay tại adapter SDK; giữ nguyên lớp quota và HTTP của SDK."""
    from tenacity import retry_if_exception, stop_after_attempt, wait_none
    if not math.isfinite(calls_per_minute) or calls_per_minute <= 0:
        raise ValueError("calls_per_minute phải dương")
    clock, sleep = clock or time.monotonic, sleep or time.sleep
    lock, next_call = threading.Lock(), [0.0]

    def pace(_state):
        # Pacing attempts SDK, không tuyên bố là số HTTP request thực tế.
        with lock:
            delay = next_call[0] - clock()
            if delay > 0:
                sleep(delay)
            next_call[0] = clock() + 60 / calls_per_minute

    configured = set()
    for cls in classes:
        for base in cls.__mro__:
            for member in vars(base).values():
                seen = set()
                while callable(member) and id(member) not in seen:
                    seen.add(id(member))
                    retry = getattr(member, "retry", None)
                    if retry is not None and id(retry) not in configured:
                        # Queue sở hữu ngân sách retry, SDK không nhân thêm số lần gọi.
                        retry.stop = stop_after_attempt(1)
                        retry.retry = retry_if_exception(is_transient)
                        retry.wait = wait_none()
                        retry.before = pace
                        retry.reraise = True
                        configured.add(id(retry))
                    member = getattr(member, "__wrapped__", None)


def install(calls_per_minute=40):
    """Cấu hình trước worker trong process exporter, không sửa file thư viện."""
    global ACTIVE
    if ACTIVE:
        return
    from vnstock import Quote, Finance, Listing, Company
    configure_sdk((Quote, Finance, Listing, Company), calls_per_minute)
    ACTIVE = True
