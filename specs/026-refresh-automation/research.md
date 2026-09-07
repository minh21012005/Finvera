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

## R-008 "Finished" conflated two states the exporter cannot tell apart (2026-09-07)

Reviewing the tail of the owner's crawl — `Checkpoint total attempted: 889/1522`, 275 symbols with a
failed dataset — against the checkpoint and the packages on disk:

| daily_bars | fundamentals | fundamentals_annual |
|---|---|---|
| 1,473 ok · 48 `failed:ValueError` · 1 `failed:RetryError` | 1,267 ok · 255 `failed:NoStatementsAvailable` | 1,516 ok · 6 `failed:NoStatementsAvailable` |

Neither number explains `889/1522`. Two separate defects do.

**(a) A newly listed symbol was settled as a permanent failure (Q-61 — fixed here).**
`export_daily_bars.build_package` raised a bare `ValueError` below `MIN_RECORDS = 20`, so
`classify_failure` recorded `failed:ValueError` and `settled_failure` treated it as final. Of the 48
symbols holding that value, **42 were settled on 2026-09-01 and never asked again** — the version
guard from Feature 018 R-008 does not help, because the failure *was* written by the current
version. Twenty of the 48 have `fundamentals: done`: the company files statements but the product
has no price series for it.

Probing the provider directly settled what the exporter could not distinguish:

- **DMX** exported cleanly — 20 sessions, `2026-08-06` → `2026-09-07` — *while its 2026-09-01
  failure still stood*. It had healed itself and the pipeline would never have noticed.
- **LPS** returned **12 sessions**: genuinely not exportable yet, and on course to be locked out the
  same way once it crosses 20.

This is the shape of fact the code already handles correctly one dataset over: `NoStatementsAvailable`
is documented as "not a defect of the symbol but a state that changes when the company files" and is
re-checked after 35 days. "Not enough sessions yet" is the same kind of state, on a shorter clock —
20 sessions is about four trading weeks — so it gets its own class and a 7-day window. It is
recorded rather than retried in-run: unlike a rate limit, waiting 65 seconds does not make a session
appear.

Rejected: bumping `export_daily_bars.TOOL_VERSION` to re-open the 48 already-settled entries. The
version describes *package content*, and `daily_bars_current` compares it, so bumping it would force
a re-export of all 1,473 healthy packages to fix 48 checkpoint entries. One `--retry-failed` run
(~880 provider calls, ~22 min) does the same job without corrupting what the version means.

**(b) `is_finished` demands a bar dated `--end` (Q-62 — measured here, not fixed).**
`daily_bars_current` requires `latest_record_date >= args.end`. **603 of 1,522** symbols hold a
complete package whose newest session predates `--end` (ART `2022-11-18`, TTZ `2022-12-02`, NDF
`2023-02-24`), against 870 that carry the current session. A delisted or untraded symbol can never
satisfy the condition, so it is never finished, is re-fetched on every run (~1,200 provider calls,
~30 min), and the exporter's closing promise — *"it exits immediately once nothing is left"* — can
never come true. No stored data is wrong; this is convergence, not correctness. Left as Q-62 with a
candidate fix (settle on "the provider was asked on `--end`", not "the symbol traded on `--end`")
rather than folded into this change, so the two are verifiable separately.
