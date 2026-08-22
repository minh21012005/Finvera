# Finvera Market Provider POC

This directory contains non-production probes used to pass the provider gates
for `001-market-overview`. It is not a runtime service or production adapter.

## Safety

- Never pass a TCBS API key, iOTP, or token on the command line.
- The TCBS probe prompts with hidden input unless `TCBS_API_KEY` is already set
  in the local process environment. iOTP is always prompted and never read from
  an environment variable.
- Output contains field names, types, counts, dates, and capability results—not
  tokens, credentials, raw responses, or market values.
- The Vnstock probe suppresses the package's unrelated import-time generation
  of editor/AI-agent instruction files. The probe must not alter repository or
  user-profile agent configuration.
- `poc-output/` is gitignored and must not be committed or shared unchanged.

## Setup

```powershell
cd tools/market-data/provider-poc
uv sync
```

## Vnstock history probe

```powershell
uv run python poc_vnstock.py
```

The probe checks index history and representative HOSE/HNX/UPCOM equities. It
also inspects the reference universe when the pinned Vnstock API supports it.
Passing requires at least 271 completed VN-Index sessions and usable OHLCV
columns. Full-universe coverage is a separate gated POC; the bootstrap importer
remains blocked until T047 is fully approved.

### Bounded full-universe coverage probe

After the representative probe succeeds, use an explicit bounded batch first:

```powershell
uv run python poc_vnstock.py --full-universe --max-symbols 25 --requests-per-minute 30
```

Continue the same source/range/universe after reviewing the sanitized summary:

```powershell
uv run python poc_vnstock.py --full-universe --max-symbols 25 --requests-per-minute 30 --resume --skip-representative
```

Omit `--max-symbols` only when intentionally requesting every eligible common
equity. The tool never writes PostgreSQL or raw market values. Its checkpoint
and summary are gitignored; the checkpoint keeps only one-way symbol
fingerprints and aggregate status counts so interrupted batches can resume.
`--skip-representative` is allowed only on `--resume`; it avoids repeating the
eight sample calls and marks the summary as `NOT_RECHECKED`, never as fresh
representative-gate evidence.

To run bounded batches automatically until the checkpoint is complete:

```powershell
.\run-full-universe.ps1 -BatchSize 25 -RequestsPerMinute 30
```

The runner stops on provider failures, malformed summaries, or the batch limit.
If Vnstock changes the reference universe, it archives the old checkpoint under
`poc-output/checkpoint-archive/` and starts one new consistent checkpoint; pass
`-FailOnUniverseMismatch` to stop instead. It never deletes a checkpoint or
blindly retries a failed provider batch.

## TCBS interactive probe

Run this yourself in a trusted terminal. The default `totp` flow accepts a
current iOTP/TOTP from the TCInvest app:

```powershell
.\.venv\Scripts\python.exe .\poc_tcbs.py
```

If TCBS has registered your OpenAPI access for Email/SMS OTP instead, explicitly
choose that flow:

```powershell
.\.venv\Scripts\python.exe .\poc_tcbs.py --otp-method email-sms
```

For bounded timing/rate/order evidence during an active market session, use at
most five extra read-only calls:

```powershell
.\.venv\Scripts\python.exe .\poc_tcbs.py --otp-method totp --ws-seconds 90 --rate-probe-requests 5 --rate-probe-interval-seconds 1
```

The summary records only status counts, aggregate WebSocket update counts,
one-way message fingerprints, and observed field names. A missing timestamp,
ordering, or correction field is reported as unavailable; it is never inferred.

### G-03: per-stock quote coverage (Feature 002)

See [RUN_G03_PROBE.md](RUN_G03_PROBE.md) for a step-by-step owner walkthrough
(Vietnamese) and `run-g03-probe.ps1` for an automated wrapper with retry.

Confirms the same `tickerCommons` endpoint already approved for index subjects
also serves an individual equity's current price/reference price/session
volume via `tickers=` instead of `index=` (per TCBS's official OpenAPI docs —
no new endpoint, R-001's "reuse the Feature 001 adapter" decision):

```powershell
.\.venv\Scripts\python.exe .\poc_tcbs.py --quote-symbols VNM,TCB
```

At most 5 symbols per run (bounded probe, same spirit as `--rate-probe-requests`).
Review `poc-output/tcbs-capability-summary.json`'s new
`ticker_commons_quote_symbols` section and record the sanitized result in
`specs/002-stock-detail-analysis/research.md` R-012 G-03 to close the gate.

### TCBS Ouranos C001 breadth evidence

The documented Ouranos endpoint is a separate, per-equity stream. During an
active session, capture only a small explicit allowlist for at least 90 seconds:

```powershell
.\.venv\Scripts\python.exe .\poc_tcbs.py --otp-method totp --ouranos-symbols TCB,VNM --ouranos-seconds 90
```

This optional probe verifies only the C001 schema and its `timeSec`, reference,
and cumulative volume/value fields. Its output anonymizes symbols as
`symbol_1`, `symbol_2`, records field types/counts and one-way fingerprints,
and never writes prices or raw frames. It does not establish final index
reconciliation, correction semantics, public-display rights, or live-provider
approval.

The Email/SMS flow invokes only TCBS's documented `request-otp` endpoint, then
prompts for the code delivered by TCBS. Both flows exchange the token, check
documented read-only market endpoints, and listen briefly for WebSocket index
channel 8. They never invoke account, cash, portfolio, trading, or order
operations. Error summaries retain only the HTTP status, safe provider error
code, and field schema; response text is suppressed.

Review `poc-output/*-summary.json`. Do not send credentials or raw provider
responses. A summary can be reviewed only after you confirm it contains no
private value you do not want to share.
