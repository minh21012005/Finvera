# Research: Feature 026 — Refresh that finishes by itself

Date: 2026-09-06. Measured against the owner's live refresh and by reading the pipeline end to end
(`refresh-data.ps1`, the five exporters, the backend stage runner).

## R-001 Where the time actually goes (measured mid-run)

| | Measured |
|---|---|
| Throughput | ~199 symbols/hour (~18 s per symbol) |
| Provider call rate | **7.3 calls/min against a 60/min quota — 12 %** |
| Fixed inter-symbol sleep | 2 s (`--requests-per-minute 30` → `60/30`), ≈ 39 min over 1,164 symbols |
| Read timeouts | frequent (`trading.vietcap.com.vn`, 30 s each, vnstock's default) |
| Settled failures from all that | **1** of 1,473 daily-bar symbols |

The run is **latency-bound, not quota-bound**: it leaves 88 % of the allowance unused while waiting
on one request at a time. The retry ladder works (1 settled failure) and must not be weakened; the
waiting simply needs to overlap.

## R-002 The fragile half is the short half

`export_all_symbols.py` — hours long — has a checkpoint, a transient/settled classification and a
retry ladder. The four exporters that run before it, and the universe fetch inside it, make at least
seven provider calls with **no exception handling at all** (spec table). With
`$ErrorActionPreference = "Stop"` plus `Assert-NativeSuccess`, any one of them ends the refresh
before the resilient part starts. Given the observed timeout frequency, surviving stage 1 is luck,
not design. This is the highest-value fix in the feature and the cheapest.

## R-003 The provider library's quota counter cannot be the concurrency guarantee

`vnai.beam.quota.Guardian` keeps `usage_counters` as a `defaultdict` of lists. Its `threading.Lock`
guards the singleton, not the counters: the code does read-modify-write on those lists
(`self.usage_counters[t]["min"] = [x for x in ... if ...]` next to `.append(...)`) from any thread
that makes a call. Under CPython's GIL each individual operation is atomic, but a filter-and-reassign
racing an append can drop a tick — i.e. the counter can **under**-count, which is the dangerous
direction for a rate limit.

Decision: our own token bucket, sized conservatively below the provider's limit, is the guarantee;
`wait_for_quota` stays as a second line of defence. We never rely on a counter we do not own for a
limit whose breach is charged to the owner's account.

## R-004 What concurrency may and may not touch

Safe to parallelise: the per-symbol work. Each symbol reads its own package file, fetches its own
data, writes its own file. There is no shared mutable state except the checkpoint and stdout.

Must be serialised: the checkpoint (a single dict written to one file) and the progress printing.
The checkpoint is already written after every symbol; under concurrency it needs a lock and an
atomic replace (write to a temp file in the same directory, then `os.replace`) so a crash can never
leave it truncated — today a crash mid-write would corrupt it, which is a pre-existing risk that
concurrency would make more likely.

Ordering is not a correctness property here: the checkpoint is keyed by symbol, and the retry pass
at the end iterates a list, so results do not depend on completion order.

## R-005 Stage-level resume: what may be skipped, and what must never be

The backend import stages are idempotent (ingestion dedupes by source/dataset/symbol/date/hash and
supersedes rather than duplicates), so re-running one is safe — merely slow. That makes a stage
checkpoint a pure time optimisation, with one hazard: **resuming into a stale state**. If the owner
starts a fresh refresh a week later, skipping "already completed" stages would silently serve old
data as new.

Rule adopted: the state file records the stage list, the run's parameters and a timestamp. Resume
happens only when the parameters match and the state is younger than a bounded age (12 hours, i.e.
inside one refresh session); otherwise the state is discarded and the run starts clean. Either way
the decision is printed, never implicit.

## R-006 Blind waiting and the log re-read

`Invoke-BackendStage` polls every 3 s with `Get-Content $stdoutLog`, which re-reads the entire file
each time; over a six-hour stage that is quadratic in the log size and is itself a load on the
machine during the heaviest stage. Reading from a remembered byte offset fixes both that and makes
a stall detector natural: if no bytes arrive for the stall window, the stage is stuck, and failing
it at that point costs minutes instead of the six-hour timeout.

## R-007 What is deliberately not changed

- **The retry ladder's patience** (5 s then 20 s, on top of vnai's two attempts). It is what keeps
  the settled-failure count at 1 in 1,473. Concurrency removes the *cost* of that patience by
  overlapping it; shortening it would trade completeness for speed, which is the wrong trade for
  this project.
- **The 30 s read timeout.** It is vnstock's default and lowering it would abort legitimately slow
  requests.
- **Import incrementality** (P2-04 / Q-41) — still unmeasured, still conditional on measuring stage
  6/7 as slow.
