# Plan: Feature 013 — TCBS Thesis Live-Path Field Audit

## Approach

Two passes, in this order, because they answer different questions:

1. **Code vs contract** (done 2026-08-31, research R-001…R-003): does the implementation obey the
   contract? Statically checkable and free of provider access. Verdict: conforming, no defect.
2. **Contract vs provider** (2026-09-04, research R-004…R-009): does the *contract* describe what
   the provider actually sends? Only answerable with the market open, and only credible against an
   **independent** provider — VCI, whose board unit and conversion are already pinned by
   `export_daily_bars.normalize_board_price`.

The audit's output is documentation plus a regression guard, not new behavior: the contract gains
the field table and the rules the session taught us, and a fixture pinned from real frames turns
"the provider changed something" from a silent data defect into a failing build.

## Components

| Layer | Change |
|---|---|
| `tools/verification/tcbs_live_capture.py` (new, owner tool) | Read-only capture: token exchange, allowlisted subscriptions only, app-level heartbeat, per-frame field inventory (type + range + samples), `tickerCommons` snapshot, VCI cross-provider comparison, JSON evidence file. Secrets never printed or written. |
| `specs/001-market-overview/contracts/tcbs-thesis-live-overlay.md` | Amended in place (clarifying, non-breaking — no consumer changes shape): per-field unit/type/semantics tables for `s|4`/`s|6`/`s|8` and `tickerCommons`; server control frames; the RFC-ping prohibition; the reference-price fallback chain; the mid-session `s|4` absence. |
| `finvera-be/src/test/resources/fixtures/market/tcbs/tcbs-frame-fixture.json` (new) | Real frames from the 2026-09-04 capture, verbatim envelopes. |
| `finvera-be/src/test/java/.../TcbsThesisFrameFixtureTests.java` (new) | Parses the fixture through the production mapper and asserts unit magnitude, JSON type tolerance, index mapping and the derived reference — a provider drift breaks the build. |
| `tools/verification/out/tcbs_live_capture_*.json` | Evidence, referenced by research.md and REMEDIATION_PLAN. |
| `docs/REMEDIATION_PLAN.md` | P2-01 closed with evidence; changelog row. |

## Why the contract is amended rather than versioned to v2

Every added clause documents behavior the provider already had and the code already handled; no
field changes meaning, no consumer changes shape, nothing persisted changes. A version bump would
imply a migration that does not exist. The amendment is dated and the audit evidence named, so the
provenance of each new clause is traceable — matching how `valuation-v2` took its dated PS
addendum in Feature 022.

## Constitution check

- *No provider behavior may be guessed* — this feature exists to replace two guessed clauses with
  captured evidence, and the fixture keeps it that way.
- *Provenance and units* — the outcome is a unit per consumed field, recorded in the contract.
- *Secrets* — API key from `.env`, OTP via `getpass`, JWT memory-only; the evidence file holds
  market data and frame envelopes only. Verified by reading the written file before committing.
- *Read-only* — the tool subscribes to the two allowlisted channels; trading/account/order/cash
  channels are never touched.
- *No `Q-` ids in production code* — new clauses cite `tcbs-thesis-private-live-v1` and
  specs/013 research ids.
- Complexity tracking: none — no new dependency (`websockets`/`httpx` already in provider-poc), no
  new runtime component, no schema change.

## Risks

- A capture is a sample of one session. It proves the unit *today*; the fixture test plus the
  existing `hasImplausibleValueScale` guard (average price vs match price, 0.02–50×) are what carry
  the claim forward. Noted in research as the residual risk.
- The OTP is owner-held and short-lived, so each live run costs one owner interaction; the tool is
  built to get everything from a single connection.
