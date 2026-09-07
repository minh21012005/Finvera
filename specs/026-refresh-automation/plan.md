# Plan: Feature 026 — Refresh that finishes by itself

## Approach

Three changes, in the order that reduces risk fastest: make the fragile calls survivable, then make
the orchestrator resumable, then make the long stage concurrent. Each is independently useful — if
only the first ships, the worst failure mode is already gone.

The retry classification is not re-invented: `export_all_symbols.py` already distinguishes a
transient network failure from a settled one, and that logic moves into a shared module so every
exporter uses the same definition rather than a second, drifting copy.

## Components

| # | Layer | Change |
|---|---|---|
| 1 | `tools/market-data/vnstock-export/provider_retry.py` (new) | `NETWORK_EXCEPTION_NAMES`, `NETWORK_MESSAGE_PATTERN`, `is_network_error`, and `call(label, fn)` which retries with the existing 5 s / 20 s ladder and raises a `ProviderCallFailed` naming the call when the ladder is exhausted. |
| 1 | `export_instrument_reference.py`, `export_sector_reference_vci.py`, `export_equity_profile.py`, `export_history.py`, `export_all_symbols.py` | Every bare provider call goes through `provider_retry.call`. `export_all_symbols.py` imports the classification from the shared module instead of defining it. |
| 2 | `refresh-data.ps1` | Stage state file (`output/refresh-state.json`): stage name, parameters hash, timestamp. Skip completed stages when the state matches and is < 12 h old, printing what is skipped. `Invoke-BackendStage` gains `-Attempts` (default 3) and a stall window (default 15 min of no new output). Log following reads from a byte offset instead of re-reading the file. |
| 3 | `export_all_symbols.py` | A worker pool over symbols (`--workers`, default 5) behind a token bucket (`--max-calls-per-minute`, default 40, below the provider's 60). Checkpoint writes take a lock and use atomic replace; progress printing takes a lock. The end-of-run transient retry pass keeps its current shape. |
| — | Tests | `tests/test_provider_retry.py`; a per-exporter test that a doubled timeout recovers; concurrency tests for the token bucket and for checkpoint atomicity. |
| — | Docs | `refresh-data.ps1` header; `tools/verification/README.md` untouched; REMEDIATION P2-12 closed; changelog. |

## Ordering and why

1 first because it removes the failure that wastes the most owner time for the least work. 2 second
because it turns any remaining failure into a resumable one. 3 last because it is the only change
that can alter *what* gets written if done carelessly, and it is worth the least if the run still
cannot finish unattended.

## Constitution check

- **I. Determinism** — concurrency changes timing, not content: per-symbol work is independent, the
  checkpoint is keyed by symbol, and the end-of-run pass iterates a list. A sampled serial-vs-parallel
  comparison is a success criterion (SC-4), not an assumption.
- **II. Provenance and honesty** — a retried call that finally fails still records a *settled*
  failure scoped to the exporter version; retry never converts a real failure into silence. Resume
  never reuses stale state silently (R-005).
- **IV. Responsible operation** — the rate limit is charged to the owner's account, so the guarantee
  is our own token bucket, not the provider library's unlocked counter (R-003).
- **VI. Risk-based testing** — a unit test per newly protected call, plus concurrency tests for the
  two shared resources.
- **VIII. Modular simplicity** — one small module, no new dependency, no new process model
  (threads, because the work is I/O-bound and the pacing must be shared in-process).
- Complexity tracking: none.

## Risks

- **Provider library thread-safety.** `vnstock`/`vnai` are not documented as thread-safe. Mitigation:
  threads only issue independent HTTP calls; our token bucket, not vnai's counter, enforces pacing;
  the worker count is configurable and defaults low (5). If a run shows library-level breakage,
  `--workers 1` restores exactly today's behaviour.
- **A resumed run hiding a real problem.** Bounded by the 12-hour window, the parameter match, and
  printing every skip.
- **Killing the backend on a retried stage.** `Invoke-BackendStage` already kills its process in
  `finally`; the retry loop must wait for the port to be free before the next attempt, or the next
  attempt fails on the port check.

## Rollout

Exporter and script changes are inert until the owner's next refresh. `--workers 1` and
`-Attempts 1` reproduce today's behaviour exactly, so the change can be backed out by flags without
a code revert.
