# Research: Stock Detail and Analysis

**Feature**: `002-stock-detail-analysis`
**Date**: 2026-08-18
**Spec**: [spec.md](spec.md)
**Status**: Design decisions recorded; owner has closed G-01, G-02, and G-04
(2026-08-22, narrower-scope/RAW-only/KBS-taxonomy decisions below). G-03
remains OPEN pending an owner-run probe (TCBS OTP cannot be scripted).

## How to read this document

Each `R-` entry records a decision, its rationale, and the alternatives that were
rejected. Decisions marked **Inherited** were already settled by
`001-market-overview` and are restated only where this feature extends them.

Normative calculation formulas are **not** duplicated here. They live in
[contracts/technical-indicators-v1.md](contracts/technical-indicators-v1.md) and
[contracts/valuation-v1.md](contracts/valuation-v1.md) so that exactly one
artifact is authoritative for each versioned rule set.

**No live provider call was made while producing this document.** Every claim
about an external provider below is either sourced from an artifact already in
this repository, or explicitly labelled as an open gate to be closed with
sanitized evidence during implementation. Gates G-01 to G-04 in R-012 are the
only place where unverified provider behaviour is allowed to appear, and they
block production integration until closed.

---

## R-001 — Per-stock live quote source

**Decision**: Reuse the TCBS iFlash adapter and the accepted-observation boundary
established by ADR-0003 and
[001 contracts/tcbs-iflash-adapter.md](../001-market-overview/contracts/tcbs-iflash-adapter.md),
extended from index subjects to individual equity subjects. Feature 002
introduces no second live price provider.

**Rationale**: `001-market-overview` already ingests per-security price
observations into `equity_price_observation` for breadth classification. The
stock detail overview needs the same fact family — matched or close price,
official reference price, session volume — for one instrument instead of the
whole universe. Adding a provider would duplicate authentication, licensing,
reconciliation, and freshness policy for no new capability.

**Alternatives rejected**:

- A dedicated per-stock quote provider: multiplies license surface and creates a
  second reconciliation pair without solving a stated requirement.
- Deriving the current price from the daily bar series only: cannot satisfy
  FR-001 or NFR-002 during an open session, and would misreport freshness.

**Extends**: The 001 adapter contract must add the per-instrument quote subject
and its liquidity fields. This is a contract amendment, not a new ADR, because
the provider, license, and boundary are unchanged.

---

## R-002 — Daily OHLCV storage: new table instead of widening `equity_price_observation`

**Decision**: Introduce a separate `equity_daily_bar` table for accepted
completed-session daily OHLCV, and leave `equity_price_observation` unchanged.

**Rationale**: The two datasets have different identity, cardinality, and
lifecycle. `equity_price_observation` is an intra-session observation stream
keyed by `(instrument, trading_date, observed_at)`; many rows per instrument per
day are normal and expected. A daily bar is exactly one accepted revision per
`(instrument, trading_date)` describing a completed session, and it carries
`open`, `high`, `low`, `volume`, and `value` fields that are meaningless for a
mid-session observation. Widening the existing table would make more than half
its columns conditionally null, would weaken its existing check constraints, and
would require a uniqueness rule on a table that deliberately allows many rows per
instrument per day.

ATR and every candle-shaped indicator require `high` and `low`, which
`equity_price_observation` does not model at all, so a schema change is
unavoidable in either design. A new table is the additive, lower-risk one: it
does not migrate or revalidate any Feature 001 row.

**Alternatives rejected**:

- Add `open/high/low/volume` to `equity_price_observation`: mixes two grains in
  one table and forces Feature 001 constraints to be relaxed.
- Store bars only in the import package and recompute on read: violates DATA-009
  reproducibility and would make indicator results untraceable.

---

## R-003 — Historical depth and the technical evaluation window

**Decision**: Every recursively smoothed indicator (RSI, MACD/EMA, ATR) is
computed over a **fixed evaluation window of the last 250 completed accepted
sessions** ending at the as-of trading date, seeded deterministically at the
start of that window. Non-recursive indicators (SMA, Bollinger Bands, average
volume, relative volume) use only their own natural period.

**Rationale**: Recursive smoothing has unbounded memory, so "the RSI of a stock"
is not a well-defined value until the input window is pinned. Without a fixed
window, two runs over the same accepted data can differ merely because one had a
longer history loaded, which breaks FR-011 and SC-003. A single uniform window
for all recursive indicators is easier to test and explain than a per-indicator
warm-up rule, and 250 sessions is below the 271 completed sessions the Vnstock
historical bootstrap POC already demonstrated on 2026-08-17, so the window is
satisfiable by the existing bootstrap path.

**Consequence**: A symbol with fewer than 250 accepted completed sessions shows
RSI, MACD, and ATR as unavailable with an `INSUFFICIENT_HISTORY` reason while
MA20, MA50, Bollinger Bands, and the volume indicators remain usable. This is
exactly the FR-006 behaviour.

**Alternatives rejected**:

- Per-indicator warm-up multiples such as ten times the period: defensible, but
  produces four different minimum-bar thresholds, four fixture families, and a
  worse failure explanation for the owner.
- Unbounded history: not reproducible, and unbounded query cost per view.

---

## R-004 — Adjusted versus raw price series

**Decision**: The chart and every technical indicator are computed from the
**adjusted** close series when an accepted adjustment basis exists, and the
series-level `adjustmentStatus` is always disclosed. A single series never mixes
adjusted and unadjusted values; if the adjustment basis is unavailable for part
of the window, the whole series is served as `RAW` with an
`ADJUSTMENT_BASIS_UNAVAILABLE` reason rather than being spliced.

**Rationale**: Splicing is the characteristic silent-corruption failure for this
data family. A 1:2 split inside a 200-session window shifts MA200 by roughly a
factor of two and produces a confidently wrong trend reading. DATA-008 already
forbids it. Serving a consistently raw series with a visible label is honest and
still useful; serving a spliced series is not recoverable by the reader.

**Corporate actions** are modelled explicitly in `corporate_action` with a stored
cumulative `adjustment_factor` per ex-date, so any adjusted value can be
re-derived from the raw value and the factor chain, satisfying DATA-009.

**Alternatives rejected**:

- Trusting the provider's pre-adjusted series without recording the actions:
  cheaper, but a provider re-adjustment silently rewrites history and no audit
  can detect it.
- Computing indicators on raw prices always: wrong across any split or stock
  dividend, and materially misleading for trend indicators.

---

## R-005 — Fundamental report grain, restatement, and metric storage

**Decision**: Model an accepted `fundamental_report` per
`(instrument, period_type, fiscal_year, fiscal_quarter, report_kind, source)` as
an immutable revision with a `supersedes_id` chain, and store its values in a
narrow `fundamental_report_metric` table keyed by an allowlisted `metric_code`
from a versioned `fundamental_metric_catalog`.

**Rationale**: Three properties are required, and a wide column-per-metric table
gives none of them cleanly.

1. *Missing, zero, and not-applicable must stay distinct* (DATA-007). In the
   narrow model an absent row means missing, a row with `value = 0` means a
   genuine zero, and a row with a null value and
   `quality_reason = 'NOT_APPLICABLE'` means the metric does not apply to that
   company type. A wide table collapses the first and third into the same null.
2. *Vietnamese statements differ by industry.* Banks, insurers, and securities
   firms do not report the same line items as manufacturers. A wide table forces
   a migration per industry variation; the catalog absorbs it as reference data.
3. *Restatements are normal.* A later filing that restates an earlier period
   creates a new revision linked by `supersedes_id`, never an in-place update,
   which is the same immutability rule Feature 001 already enforces.

**Consequence**: Read queries pivot metric rows in the application layer. This is
acceptable because the read set per stock and period is small and bounded by the
catalog size.

**Alternatives rejected**:

- One wide column-per-metric table: fastest to query, but loses the
  missing/zero/not-applicable distinction and needs a migration for every new
  line item.
- A `jsonb` blob of metrics: loses numeric typing and check constraints on
  authoritative financial values, which Constitution Principle I forbids.

---

## R-006 — Derived fundamentals are persisted, not computed on read

**Decision**: Persist an immutable `fundamental_summary` per
`(instrument, as_of, rule_version)` holding the trailing-twelve-month and growth
figures the UI displays, with explicit `fundamental_summary_input` links to every
contributing report revision.

**Rationale**: DATA-009 requires a displayed value to be reproducible from its
recorded inputs. TTM EPS is an aggregation over up to four quarterly reports, any
of which may later be restated. If TTM is recomputed on every read, the value
shown yesterday cannot be reconstructed after a restatement, and FR-014 cannot
demonstrate that a correction changed the number. Persisting the summary with
input links makes both the superseded and the corrected value auditable.

**Alternatives rejected**:

- Compute on read: cheaper storage, but non-reproducible after restatement and
  untestable against a fixed fixture.

---

## R-007 — Technical indicator rule set `technical-indicators-v1`

**Decision**: Adopt the versioned rule set defined in
[contracts/technical-indicators-v1.md](contracts/technical-indicators-v1.md),
covering MA20/MA50/MA200, RSI(14) with Wilder smoothing, MACD(12,26,9),
Bollinger Bands(20, 2, population standard deviation), ATR(14) with Wilder
smoothing, average volume(20), and relative volume — each with an exact formula,
minimum bar count, precision, rounding mode, and unavailability reason code.

**Rationale**: Constitution Principle I requires the finance core to be
deterministic and versioned. The specific parameter choices — Wilder rather than
simple smoothing for RSI and ATR, population rather than sample standard
deviation for Bollinger Bands — are the conventional definitions, so a reader
comparing Finvera against a standard charting package sees matching numbers. A
non-standard choice would look like a defect even when internally consistent.

**Change policy**: A formula, parameter, rounding mode, or minimum-bar change
creates `technical-indicators-v2` and new parallel results. It never rewrites v1
history. This mirrors the `market-regime-v1` policy from Feature 001.

---

## R-008 — Valuation rule set `valuation-v1`

**Decision**: Adopt the versioned methodology defined in
[contracts/valuation-v1.md](contracts/valuation-v1.md): five metrics (P/E, P/B,
EV/EBITDA, PEG, dividend yield), two comparison bases — the stock's own accepted
ratio history and its sector cross-section — a 0-100 expensiveness score, and
exactly three published labels `UNDER_VALUED`, `FAIR_VALUED`, `OVER_VALUED`, with
a withheld state when publishability conditions fail.

**Rationale**: FR-009 requires exactly one classification from a deterministic,
versioned method with a disclosed comparison basis, and FR-010 requires the
classification to be withheld rather than guessed when inputs are insufficient.
This is structurally the same problem `market-regime-v1` solved: normalize
several heterogeneous inputs to a bounded score, band the score, and refuse to
publish under a completeness floor. Reusing that shape means the owner learns one
mental model, and the test strategy — boundary tests on each band edge, a
withheld-result test, a renormalization test — transfers directly.

**Explicit non-goal**: The score is a *relative expensiveness* measure, not a
prediction of return and not a recommendation. FR-015 governs the wording.

**Alternatives rejected**:

- Absolute thresholds such as "P/E above 15 is expensive": not defensible across
  Vietnamese sectors, and would be wrong for banks and utilities in opposite
  directions.
- A discounted-cash-flow intrinsic value: requires forecast inputs Finvera does
  not have and cannot ground in accepted facts. It would be a fabricated number
  wearing a precise-looking format.

---

## R-009 — Section-level endpoints with a shared coherence key

**Decision**: Expose the stock detail as five independently retrievable
resources — overview, chart, technical, fundamentals, valuation — plus a symbol
lookup. Every section response carries `asOf`, `dataStatus`, `reasonCodes`, and a
`coherenceKey` derived from the accepted revision vector its content was built
from.

**Rationale**: FR-012 requires one section's failure not to block the others. A
single aggregate response makes that awkward: a fundamentals outage either
degrades the whole payload's status or hides itself. Separate resources make
partial availability the natural outcome and let the chart, the largest payload,
be cached and re-fetched independently.

NFR-003 still requires internal consistency for a shared as-of snapshot, which is
why every section returns the same-shaped `coherenceKey`. The client compares
keys across sections and re-fetches the lagging one rather than rendering a mixed
snapshot silently. This keeps consistency verifiable without forcing one
transaction across three differently-refreshing datasets.

**Alternatives rejected**:

- One aggregate `GET /stocks/{symbol}`: matches Feature 001's shape, but Feature
  001 has one refresh rate and this feature has three — live quote, daily bar,
  quarterly report. Forcing them into one response lets the slowest dataset
  dictate the endpoint's latency budget, threatening NFR-001.
- Per-section websockets or streaming: unjustified complexity for a private
  single-owner deployment.

---

## R-010 — Freshness policy per dataset

**Decision**: Freshness is evaluated per dataset against its own thresholds, not
globally. The vocabulary (`CURRENT`, `DELAYED`, `STALE`, `PARTIAL`,
`UNAVAILABLE`) is inherited unchanged from Feature 001 R-004.

| Dataset | `CURRENT` while | `DELAYED` while | `STALE` beyond |
|---|---|---|---|
| Live quote, session open | within contracted delay | contracted delay + 30 s | contracted delay + 15 min |
| Live quote, session closed | latest completed session accepted | previous session only | more than one full session behind |
| Daily bar series | last completed session present | one session missing | more than one session missing |
| Fundamental report | period end within 190 days | 190 to 280 days | beyond 280 days |
| Valuation assessment | inherits the worst state among its inputs | — | — |

**Rationale**: A quarterly report is not stale at 09:20 the way a quote is.
Applying the quote threshold to fundamentals would mark every correct report
stale within an hour; applying the fundamentals threshold to a quote would call a
two-hour-old price current. The 190-day boundary is one quarter of about 92 days
plus the Vietnamese statutory filing window plus margin, so an on-time quarterly
filer never appears stale; 280 days is roughly three quarters and indicates a
genuinely missing filing.

**Gate**: The exact contracted delay stays deployment configuration, never a
hard-coded constant, exactly as Feature 001 established.

---

## R-011 — Access, ingress, and secrets

**Inherited, unchanged.** ADR-0005 owner-only local session, CSRF on state
changes, the Secure/HttpOnly/SameSite=Strict cookie, and the Tailscale
Serve-only ingress invariant apply verbatim. This feature introduces **no new
authentication path** (SEC-004), no new outbound host beyond those the Feature
001 adapter allowlist already permits, and no export, share link, or public
endpoint (SEC-002).

The T051 local-development deferral remains in force: every process stays bound
to `127.0.0.1`, and no public DNS record, port forward, tunnel, or Tailscale
Funnel is created. T051 remains a mandatory pre-deployment gate for this feature
as well.

---

## R-012 — Open provider evidence gates

Production integration for each dataset below is **blocked** until its gate is
closed with sanitized, owner-reviewed evidence, following the same pattern as
Feature 001's T045 and T047. Deterministic domain, persistence, contract, and UI
work against fixtures may proceed in parallel; it must never be presented as
live data.

### G-01 — Fundamental report source (blocking for US3)

**What is already known from repository artifacts** — no network call was made:

- The TCBS iFlash adapter contract in this repository documents only
  `tickerCommons` and the Ouranos `C001` price channel. It contains **no**
  fundamentals operation. TCBS is therefore not a candidate for this dataset
  under the currently accepted contract.
- The pinned Vnstock package `4.0.6` present in the POC virtual environment
  exposes a `Finance` adapter with `balance_sheet`, `income_statement`,
  `cash_flow`, and `ratio` operations, and its constructor rejects any `source`
  other than `"vci"` or `"kbs"`.
- The historical-bootstrap POC already approved under ADR-0004 uses
  `source = "KBS"`. A fundamentals path would therefore reach the **same
  upstream already assessed for price history**, not a new provider
  relationship.

**What is not known and must be proven before production use**:

1. The exact response schema and Vietnamese or English field identity of each
   operation, captured as sanitized fixtures.
2. Reporting-period semantics: fiscal year and quarter identity, period start and
   end dates, consolidated versus separate statements, audited versus unaudited,
   and the currency and unit scale actually returned.
3. Restatement behaviour: whether a re-filed period returns a changed value under
   the same period key, and how a consumer detects it.
4. Industry variation: whether banks, insurers, and securities firms return a
   different line-item set, and which `metric_code` values are unavailable for
   them.
5. Request limits and failure behaviour under the owner's non-commercial use.

**Gate condition**: G-01 closes only when items 1 to 5 are recorded as sanitized
fixtures and a written finding in this file, and the owner accepts them. If the
finding materially changes the ADR-0004 decision — a different upstream, a
different license posture, or a new provider relationship — it requires an
ADR-0004 amendment or a new ADR before implementation, per `AGENTS.md`.

**Licensing constraint that does not change**: the Vnstock license permits
personal, non-commercial use only. Fundamentals obtained through it carry exactly
the same private-deployment restriction as price history, and any public or
multi-user delivery requires a commercially licensed replacement.

**Evidence gathered (2026-08-19)** — live sanitized probe against the pinned
Vnstock `4.0.6`, script `tools/market-data/provider-poc/poc_vnstock_fundamentals.py`,
sanitized output `tools/market-data/provider-poc/poc-output/{kbs,vci}/vnstock-fundamentals-capability-summary.json`
(gitignored, schema/field-name/count evidence only, no dollar amounts recorded
beyond the public line-item labels themselves, which are not secret):

1. **`balance_sheet()` returns zero rows for every symbol tried under
   `source="kbs"`** (`VNM`, `VCB`, `SSI`, `HPG`; both `period="year"` and
   `period="quarter"`). `show_log=True` confirms this is a genuine upstream
   response, not a client bug: the library issues a real HTTP GET to
   `https://kbbuddywts.kbsec.com.vn/.../finance-info/{symbol}?type=CDKT...` and
   logs `Không tìm thấy bảng cân đối kế toán cho {symbol}` ("balance sheet not
   found"). `income_statement`, `cash_flow`, and `ratio` all return real,
   nonempty data for the same symbols and periods, so this is specific to the
   balance-sheet report type, not a blanket API failure.
2. **`source="vci"` cannot be used as a fallback**: constructing
   `Finance(source="vci", ...)` raises `UnboundLocalError` from inside
   vnstock's own `core/utils/env.py` (`get_hosting_service`) for every symbol
   tried — a defect in the pinned package version itself, not a data
   availability question.
3. **Consequence for FR-007/valuation-v1**: `EQUITY_ATTRIBUTABLE_TO_PARENT`,
   `TOTAL_DEBT`, and `CASH_AND_EQUIVALENTS` are balance-sheet line items.
   Without them, `PB` and `EV_EBITDA` can never be computed from a live feed
   (`valuation-v1`'s own formulas require `bvps`/`ev`, both balance-sheet
   derived), and FR-007's `debt-to-equity` display metric loses its two
   underlying inputs (though `ratio()` separately exposes a pre-computed
   `debt_to_equity` figure — see point 5).
4. **Schema shape differs from what the domain model assumed**: `income_statement`,
   `cash_flow`, and `ratio` are wide tables (one row per line item, one column
   per fiscal period, e.g. `2026-Q2`, `2025-Q4`), not one row per period as
   `FundamentalReportAcceptance`/`IncomingFundamentalReport` currently assume.
   An adapter would need a wide-to-long transform per period column, which is
   new, unresearched work, not a simple field rename.
5. **Field identity is workable**: the `item_id` column is already an English
   snake_case key (`revenue`, `gross_profit`, `net_profit`,
   `earnings_per_share_vnd`, `roe`, `roa`, `debt_to_equity`, `dividend_yield`,
   `ev_ebitda`, …), not a numeric or Vietnamese-only code — mapping most of it
   to `FundamentalReportAcceptance.ALLOWED_METRIC_CODES` looks feasible. Two
   open questions surfaced, not yet resolved: (a) `income_statement` returns
   **two** `revenue`-labelled rows per symbol (`item` "1. Doanh thu bán hàng…"
   gross revenue vs. "3. Doanh thu thuần…" net revenue) — which one is
   Finvera's `REVENUE` is undecided; (b) no `item_id` corresponds to
   `FREE_CASH_FLOW` directly — it would need to be derived (e.g. operating
   cash flow minus capex), a new formula not yet specified anywhere.
6. **`ratio()` already returns pre-computed `pe_ratio`/`pb_ratio`/`ev_ebitda`/`roe`/`roa`**
   for the symbol. Per Constitution Principle I these can never be trusted as
   Finvera's authoritative `valuation-v1` outputs (which must be computed by
   Finvera's own versioned domain code), but they are a legitimate reference
   value for a future cross-check test against the domain engine's own result.
7. Restatement behaviour (item 3) and request-limit behaviour (item 5) were
   **not** established by this pass — a single point-in-time probe cannot
   observe whether a re-filed period changes under the same key, and no
   sustained-load test was run.

**Gate status: CLOSED (owner decision, 2026-08-22).** The owner accepted the
narrower scope: live fundamentals ship from `income_statement()`,
`cash_flow()`, and `ratio()` only (`source="kbs"`). `PB`, `EV_EBITDA`,
`TOTAL_DEBT`, and `CASH_AND_EQUIVALENTS` — all balance-sheet-derived — stay
permanently unavailable/withheld under `valuation-v1`'s existing
insufficient-data handling (not fabricated, not approximated) until a working
balance-sheet source is found. `ratio()`'s pre-computed `pe_ratio`/`pb_ratio`/
`ev_ebitda`/`roe`/`roa` (point 6 above) are cross-check-only references and
are never persisted as Finvera's authoritative `valuation-v1` output, per
Constitution Principle I.

T058 implements this as an owner-operated, offline export/import boundary
(`tools/market-data/vnstock-export/export_fundamentals.py` →
`FundamentalReportImportService`), consistent with ADR-0004: Vnstock never
runs live/scheduled/per-request, only as a bounded local tool whose output
Spring validates and imports atomically. The open item-5(a) revenue-row
ambiguity (gross vs. net revenue both labelled "revenue") and item-5(b)
(`FREE_CASH_FLOW` has no direct `item_id`) are resolved the same way: map
only the unambiguous line items per `FundamentalReportAcceptance`'s allowed
metric codes, and drop anything ambiguous rather than guess — a dropped
metric is a smaller, honest gap than a wrongly-mapped one.

### G-02 — Corporate action and adjustment basis (blocking for the US1 chart and US2)

The split, stock dividend, cash dividend, and rights-issue history needed for
R-004 has no accepted source in this repository yet. The gate requires a
sanitized fixture containing at least one split and one stock dividend for a real
symbol, the ex-date and ratio field identity, and confirmation of whether the
provider's historical series is already adjusted. Until G-02 closes, the chart
and technical indicators serve `RAW` series with the disclosed reason code.

**Evidence gathered (2026-08-19)** — live probe of `vnstock.Company(source="kbs")`
for `VNM`, `HPG`, `SSI`:

1. **`Company.events()` returns zero rows for every symbol tried.** This is
   the API surface a dedicated corporate-action feed would be expected to use
   (as opposed to a derived inference); it returned nothing via KBS for all
   three symbols probed.
2. **`Company.capital_history()` returns real data** (`date`, `charter_capital`,
   `currency` columns; 14–34 rows per symbol) showing charter-capital step
   changes over time — e.g. VNM's charter capital rose from ~17.4T to ~20.9T
   VND on 2020-10-28, consistent with a stock dividend or bonus share issue —
   but **it does not carry the fields G-02 requires**: no explicit action
   `type` (split vs. stock dividend vs. rights issue vs. M&A), no explicit
   `ratio`, and no ex-date (`date` is presumably an effective/announcement
   date, unconfirmed; 5 of VNM's 14 rows have `date = NaT`, i.e. missing
   entirely). A ratio could be *inferred* from consecutive capital values, but
   that is a derivation Finvera would own, not a field the provider supplies —
   exactly the kind of guessed provider semantics `AGENTS.md` prohibits.
3. Whether the OHLCV history returned by `Market.equity(...).ohlcv(...)`
   (used for price bars) is already split-adjusted or raw was **not**
   established — confirming this needs a symbol with a known, dated split and
   comparing the price series across that date, not attempted in this pass.

**Gate status: CLOSED (owner decision, 2026-08-22) — RAW-only, permanently.**
The owner declined both remaining options (further `events()` probing, and
deriving actions from `capital_history` deltas as an approximation Finvera
would own). No live `CorporateActionProvider` is built, so there is nothing
for a `finvera.stock.provider.corporate-actions-enabled` flag to gate — it
was removed as dead configuration (2026-08-22) rather than kept as an
always-`false` no-op. The chart and every technical indicator continue
serving the `RAW` series with the `ADJUSTMENT_BASIS_UNAVAILABLE` reason code
unconditionally — exactly R-004's already-designed fallback for "the
adjustment basis is unavailable," now the permanent state for this dataset
rather than a transitional one. T060 is closed as documentation-only: there
is no new adapter to implement, since
implementing one was the option declined.

**Follow-on mitigation (2026-08-22) — item 3's open question resurfaced building
the bulk exporter.** Whether `ohlcv()` is raw or pre-adjusted still is not
established, and now matters operationally too: if `export_all_symbols.py`
only fetched newly-missing trading days on each incremental re-run, a
retroactive rewrite of recent history by an undisclosed provider-side
adjustment would go undetected forever. Mitigation, not a resolution of the
underlying unknown: every incremental re-run re-fetches and merges a rolling
`--lookback-days` window (default 90) of already-written history, not just the
new gap, so a *recent* silent rewrite self-heals within one run. A rewrite
reaching further back than the lookback window would not be caught until an
owner-run `--full-refresh` (full re-fetch of the whole configured range).
This is a bounded, disclosed limitation, not a claim that the underlying
raw-vs-adjusted question is resolved.

### G-03 — Per-stock quote coverage (blocking for the US1 live path)

Extends the Feature 001 TCBS gate to per-instrument subjects: confirmation that
the accepted adapter returns the current price, official reference price, and
session volume for an arbitrary supported symbol, and that the request cost per
symbol is compatible with NFR-001.

**Not attempted (2026-08-19).** `tools/market-data/provider-poc/poc_tcbs.py`'s
own safety design requires a live TCBS API key and an interactive iOTP
(one-time password) prompt — by design, "iOTP is always prompted and never
read from an environment variable" (`provider-poc/README.md`), so this probe
cannot be run by an unattended agent. No `TCBS_API_KEY` is present in
`finvera-be/.env` either. This gate can only be closed by the owner
running `poc_tcbs.py` themselves and recording sanitized results here.

**Update (2026-08-22) — narrower remaining question, via TCBS's official
OpenAPI documentation (developers.tcbs.com.vn), no network call made:** the
same `GET /tartarus/v1/tickerCommons` endpoint already approved for index
subjects under T045 (`specs/001-market-overview/contracts/tcbs-iflash-
adapter.md`) documents a `tickers=<comma-separated symbols>` query parameter
as an alternative to `index=`, returning the identical per-item schema
(`matchPrice`, `refPrice`, `totalVol`, `totalVal`, …) already proven for
indices. This means G-03 does not require discovering a new endpoint — R-001's
"reuse the Feature 001 adapter" decision was correct. `poc_tcbs.py` has been
extended with a `--quote-symbols SYMBOL[,SYMBOL...]` option (reusing the
existing `safe_rest_probe` helper) so the owner can close this gate with one
additional bounded, owner-run probe: confirm `tickers=` returns real
current-price/reference-price/session-volume data for an arbitrary supported
equity symbol, and that the per-symbol request cost stays compatible with
NFR-001.

**Gate status: CLOSED (owner-run probe, 2026-08-22).** Sanitized evidence
(`tools/market-data/provider-poc/poc-output/tcbs-capability-summary.json`,
`rest.ticker_commons_quote_symbols`) for `tickers=VNM,TCB,HPG`:

1. **`status: "PASS"`, `data_count: 3`** — all three requested symbols
   returned, `has_trading_date: true`.
2. **Item schema is a superset of what T045 already proved for indices**:
   `matchPrice`, `refPrice`, `totalVol`, `totalVal`, `change`,
   `changePercent`, `open`, `high`, `low`, `avg` all present as `number` —
   plus per-instrument-only fields indices didn't carry: `ceilPrice`/
   `floorPrice` (price limits), three levels of `bidPrice`/`bidQtty`/
   `offerPrice`/`offerQtty` (order book), `room`/`buyForeignQtty`/
   `sellForeignQtty` (foreign ownership), `matchQtty`, `nextCeilPrice`/
   `nextFloorPrice`/`nextRefPrice`. Only the five already used by
   `StockQuoteProvider.QuoteObservation` (`matchPrice`, `refPrice`,
   `totalVol`, `totalVal`, plus `open`/`high`/`low` for bar ingestion) are
   consumed; the rest are confirmed present but intentionally unused (no
   order-book/foreign-ownership feature is in scope).
3. **`bounded_rate_probe`: 3 requests at 1 s intervals, all HTTP 200** —
   compatible with NFR-001 at this cadence; matches the Feature 001 index
   probe's own 5-req/1s finding.
4. **`gate_passed: false` at the top level is not a G-03 failure** — that
   field also reflects the unrelated index WebSocket stream, which reported
   `PARTIAL` (auth/subscribe succeeded, no index ticks observed in the
   capture window — plausible outside an active matching session). G-03
   depends only on `rest.ticker_commons_quote_symbols`, which independently
   passed.

`TcbsStockQuoteProvider` (T062 TCBS half) is implemented:
`finvera-be/src/main/java/com/minhnb/finvera_be/stock/provider/tcbs/
TcbsStockQuoteProvider.java`, reusing `TcbsHttpRestClient`/
`TcbsHttpSessionState` from Feature 001 Phase 9 (no second provider, per
R-001). Wired into `StockOverviewService` behind
`finvera.stock.provider.quote-live-enabled` (default `false`): a live quote
is fetched and ingested as today's `equity_daily_bar` row before every read,
never served directly from the provider call (PostgreSQL stays the source of
truth, Constitution Principle II); any failure degrades to the last accepted
bar (Principle VII).

### G-04 — Sector reference and constituent coverage (blocking for the sector basis)

The sector comparison basis needs a sector classification per instrument and
enough classified constituents per sector for a median to be meaningful.
`valuation-v1` sets that floor at 8 comparable constituents. The gate requires
evidence of the classification scheme, its version, and its coverage of the
supported universe. Until G-04 closes, valuation publishes on the own-history
basis alone with the basis disclosed, exactly as `valuation-v1` specifies.

**Evidence gathered (2026-08-19)** — `vnstock.Listing(source="kbs").symbols_by_industries()`:

1. Returns 696 symbols classified into 25 `industry_code`/`industry_name`
   groups (a KBS-proprietary numeric taxonomy in Vietnamese — e.g. `3` =
   "Bất động sản" (real estate), `11` = "Ngân hàng" (banking); not confirmed
   to be standard ICB or GICS, and the API exposes no explicit scheme-version
   field).
2. **22 of 25 industries (88%) already clear the `N_min = 8` comparable-constituent
   floor** `valuation-v1` requires (largest: real estate 83, construction 66,
   transport/warehousing 62; smallest passing: manufacturing-equipment 8,
   accommodation/food-service 8). Three industries fall short (rubber
   products 4, other-finance 3, consulting/support 7).
3. `Listing.industries_icb()` — the method whose name suggests an actual ICB
   standard mapping — **fails** (`RetryError` wrapping a `NotImplementedError`
   inside the pinned package), so no confirmed standard-scheme identity exists
   yet, only the proprietary `symbols_by_industries()` taxonomy above.

**Gate status: CLOSED (owner decision, 2026-08-22).** The owner accepted the
KBS taxonomy (`symbols_by_industries()`) as the classification scheme of
record, scheme identity recorded as `KBS_INDUSTRY` with `schemeVersion` set to
the pinned Vnstock package version at export time (per `SectorReferenceEntity.
scheme`/`schemeVersion`, already modelled for exactly this). The three
industries below the `N_min = 8` floor (rubber products, other-finance,
consulting/support) stay unclassified; their constituents fall back to the
own-history valuation basis automatically, exactly as `valuation-v1` already
specifies for a thin sector. `industries_icb()` is not retried — the KBS
taxonomy is accepted as-is rather than continuing to chase a standard-scheme
mapping that the pinned package version cannot produce.

T063/T064 implement this as an owner-operated, offline export/import boundary
(`tools/market-data/vnstock-export/export_sector_reference.py` →
`SectorReferenceImportService`), consistent with ADR-0004.

**Addendum (2026-08-22) — the 697-symbol count above is a classification
subset, not the tradable universe.** While building `export_all_symbols.py`
(the owner-run bulk exporter that loops daily-bar/fundamentals export over
every symbol), a live sanitized probe of `vnstock.Listing(source="kbs")`
found `symbols_by_industries()` — the G-04 evidence call above — only returns
symbols KBS has industry-classified. `symbols_by_exchange()` filtered to
`type == "stock"` on `HOSE`/`HNX`/`UPCOM` returns **1,525** symbols (HOSE 405,
HNX 299, UPCOM 821), matching `all_symbols()`'s count exactly — the actual
common-equity universe, about 2.2x larger than the classified subset. This
does not change G-04's own finding (sector classification coverage is
genuinely 697/1,525, not a bug) but means `export_all_symbols.py` uses
`symbols_by_exchange()` as its universe source, not `symbols_by_industries()`,
so bulk daily-bar/fundamentals export covers every tradable symbol rather than
only the ~46% KBS has classified into an industry.

---

## R-013 — Testing and fixture strategy

**Decision**: Every rule set ships with a versioned fixture family, and no test
depends on a live provider.

| Risk | Test level | Fixture |
|---|---|---|
| Indicator arithmetic | Pure unit and property | Hand-computed golden vectors per indicator, plus a 250-bar synthetic series |
| Insufficient history | Unit and API contract | Symbols with 19, 20, 21, 49, 199, 200, 249, and 250 bars |
| Adjustment integrity | Property | A split inside the window; asserts no spliced series is ever produced |
| Valuation banding | Boundary | Unrounded scores at 35.4, 35.5, 64.4, 64.5 and their labels |
| Withheld valuation | Unit and API | Negative EPS, missing book value, sector below the constituent floor |
| Restatement | Integration | A quarterly report superseded by a restated revision |
| Cross-source conflict | Property | Matching and conflicting TCBS and Vnstock bars for one date |
| Freshness per dataset | Unit | Each boundary in the R-010 table, both sides |
| Section independence | API and E2E | Each of the five sections failing alone |
| Authorization | Security integration | Owner, non-owner, unauthenticated, missing CSRF token |

**Determinism check**: a replay test recomputes every persisted indicator and
valuation result from its recorded inputs and rule version and asserts exact
equality of the stored decimal values. This is the direct evidence for SC-003.
