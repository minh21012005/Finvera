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
import time
from typing import Any, Callable, Iterable, TypeVar

T = TypeVar("T")

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


def is_network_failure(exc: BaseException) -> bool:
    """True when the exception, or anything in its cause/context chain, is a dropped connection.

    Behaviour preserved exactly from the version this replaces in `export_all_symbols.py`: the
    `isinstance` check catches subclasses whose *name* is not in the set, and the chain walk is
    depth-capped so a self-referential cause cannot spin.
    """
    seen: set[int] = set()
    current: BaseException | None = exc
    while current is not None and id(current) not in seen and len(seen) < 8:
        seen.add(id(current))
        if type(current).__name__ in NETWORK_EXCEPTION_NAMES or isinstance(current, (ConnectionError, TimeoutError)):
            return True
        if NETWORK_MESSAGE_PATTERN.search(str(current)):
            return True
        current = current.__cause__ or current.__context__
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
