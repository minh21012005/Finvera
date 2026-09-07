# Feature 026: Refresh that finishes by itself

**Status**: Specified 2026-09-06
**Closes**: docs/REMEDIATION_PLAN.md **P2-12**; the owner's requirement that a refresh "must run
through, or pick itself back up, without me restarting it"
**SRS References**: SRS-NFR-07 (operability) · **Measured**: during the owner's 2026-09-06 refresh

## Problem

A full refresh is seven stages over roughly six hours. Two things are wrong with it.

**It can die in the first minute, and then nothing has happened.** `refresh-data.ps1` runs with
`$ErrorActionPreference = "Stop"` and calls `Assert-NativeSuccess` after each exporter, so any
non-zero exit aborts the whole script. Stage 1 makes **at least seven provider calls with no retry
at all**:

| File | Call | Protected |
|---|---|---|
| `export_instrument_reference.py:40` | `Listing(vci).symbols_by_exchange()` | no |
| `export_sector_reference_vci.py:182` | `symbols_by_industries()`, `industries_icb()`, `symbols_by_exchange()` | no |
| `export_equity_profile.py:48,59` | two universe calls | no (its per-symbol loop *is* protected) |
| `export_history.py` | index history for four indexes | no |
| `export_all_symbols.py:75` | `fetch_symbol_universe()` | no |

The irony is exact: `export_all_symbols.py`, the stage that takes hours, is the only one with a
checkpoint, a transient/settled failure classification and a retry ladder. The seven short calls
that gate it have none — and the provider times out several times a minute
(`trading.vietcap.com.vn`, 30 s read timeout). The 2026-09-06 run survived stage 1 by luck.

**Nothing resumes.** Stages 2–7 keep no record of what completed. A failure in stage 7 means
re-running stages 2–7; the imports are idempotent so nothing corrupts, but hours are re-spent. And
the wait is blind: stage 6a's timeout is 21,600 s, so a backend that hangs without dying is waited
on for six hours before the script gives up.

Separately, the run is slower than it needs to be: measured mid-run, **7.3 provider calls per
minute against a 60/min quota — 12 % of the allowance** — because symbols are fetched strictly one
at a time, so every round-trip and every 30 s timeout is paid in series (~18 s per symbol,
~199 symbols/hour).

## Scope

In scope: the resilience and pacing of the refresh pipeline — retry coverage on provider calls,
stage-level resume and retry in the orchestrator, stall detection, and concurrency in the
universe exporter.

Out of scope, deliberately: what any exporter *fetches* or *writes* (no package contract changes),
the import/warmup logic in the backend, and P2-04's import incrementality (still unmeasured).

## Requirements

- **FR-001** Every provider call in the export stage MUST retry transient network failures using
  one shared classification, and MUST NOT retry a genuine data/schema failure.
- **FR-002** A transient failure that survives all retries MUST fail that exporter with a message
  naming the call, so the cause is never guessed from an exit code alone.
- **FR-003** The orchestrator MUST record each completed stage and, on a re-run of the same refresh,
  skip the stages already completed.
- **FR-004** A stage that fails MUST be retried automatically before the run is abandoned, and the
  number of attempts MUST be visible in the output.
- **FR-005** The orchestrator MUST detect a stalled stage — no new log output for a bounded period —
  and fail it then, rather than waiting out the full timeout.
- **FR-006** Resume MUST NOT silently reuse stale state: state older than a bounded age, or from a
  run with different parameters, starts fresh, and the decision is printed.
- **NFR-001** Following a stage's log MUST cost time proportional to new output, not to the whole
  log re-read on every poll.
- **NFR-002** The universe exporter MUST fetch several symbols concurrently while keeping the
  provider call rate under the quota, and MUST NOT rely on the provider library's own counter for
  that guarantee (its usage counters are mutated from several threads without a lock).
- **NFR-003** Concurrency MUST NOT change what is written: the same packages, the same checkpoint
  content, and a checkpoint that is never observed half-written.
- **DATA-001** Retry and concurrency MUST NOT weaken the existing failure classification: a symbol
  whose dataset genuinely fails is still recorded as failed, scoped to the exporter version.

## Success criteria

- **SC-1** Every provider call listed in the table above is covered by the shared retry, proven by a
  unit test per exporter that makes the provider raise a timeout twice and then succeed.
- **SC-2** Killing a stage mid-run and re-running the script resumes at that stage; the output names
  the skipped stages.
- **SC-3** A stage whose backend produces no output for the stall window fails within that window
  rather than at the full timeout.
- **SC-4** A full crawl of the 1,522-symbol universe completes materially faster than the measured
  199 symbols/hour, with the same package and checkpoint content as a serial run for a sampled
  subset, and a measured call rate under the quota.
- **SC-5** Exporter suite green; the crawl's own resilience tests cover a timeout that recovers and
  one that settles as failed.

## Acceptance scenarios

1. **Given** the provider times out on `symbols_by_exchange()` twice and then answers, **when**
   stage 1 runs, **then** the exporter waits, retries, succeeds, and the refresh continues.
2. **Given** the provider is down for the whole retry ladder, **when** stage 1 runs, **then** the
   exporter exits non-zero with a message naming the call, and re-running the script later starts
   from stage 1 without having damaged anything.
3. **Given** stage 6a failed and the script was re-run within the resume window, **when** it starts,
   **then** stages 2–5 are reported skipped and the run begins at 6a.
4. **Given** a backend that starts but emits nothing for the stall window, **when** its stage runs,
   **then** the stage fails at the stall window and is retried, not waited on for six hours.
