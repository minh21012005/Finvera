# Tests for `refresh-data.ps1` (Feature 026)

The refresh orchestrator is the one part of the pipeline with no unit tests behind it, and it is
also the part that decides whether a six-hour run survives a hiccup. These two harnesses cover it
without touching the database or the provider: everything above the `=== Finvera data refresh ===`
banner in `refresh-data.ps1` is definitions, so it can be dot-sourced on its own, and the backend is
replaced with a stub `mvnw.cmd` that prints what a real one would.

```powershell
powershell -NoProfile -File tools\refresh-tests\test-resume-state.ps1
powershell -NoProfile -File tools\refresh-tests\test-stage-runner.ps1
```

`test-resume-state.ps1` — the resume rules. Records and reloads stage completions, and proves the
three hazards all start clean and say why: different parameters, state older than 12 hours, and an
unreadable state file. Resuming into stale state would serve old data as new, so these matter more
than the happy path.

`test-stage-runner.ps1` — the stage runner against fake backends: markers detected (one and two of
them), a silent backend caught by the stall window instead of the full timeout, an early exit
reported, `APPLICATION FAILED TO START` recognised, a completed stage skipped, and a doomed stage
retried then thrown after its attempts without being recorded as done.

Two traps found while writing these, both worth remembering: the definitions region assigns
`$beDir`/`$exportDir`, so stubs must be installed **after** dot-sourcing; and `timeout /t` needs
stdin, which `Start-Process` redirects away, so a fake backend that should idle must use
`ping -n` instead.
