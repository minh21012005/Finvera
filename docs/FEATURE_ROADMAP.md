# Finvera Feature Roadmap

**Status**: Living delivery roadmap

**Last reviewed**: 2026-09-13

**Authority**: The SRS defines product intent. Approved feature specifications,
contracts, and ADRs define accepted behavior. This roadmap records current
delivery status and a proposed implementation order; it does not approve scope
or override those sources.

## 1. Purpose and status meanings

Use this document to answer three questions:

1. Which user capabilities are available now?
2. Which SRS capabilities are still absent or only partly delivered?
3. What is the safest dependency order for implementing them?

| Status | Meaning |
|---|---|
| **Delivered** | A usable vertical slice exists in code and has feature documentation and automated validation. |
| **Partial** | A useful slice exists, but part of the cited SRS requirement remains intentionally unimplemented. |
| **Planned** | The SRS includes the capability, but no accepted implementation feature exists yet. |
| **Operational follow-up** | Product behavior exists, but a live-data, usability, deployment, or documentation check still needs owner evidence. |
| **Non-goal** | The current SRS or constitution excludes the capability from the active product scope. |

An unchecked task in an old `tasks.md` is not, by itself, evidence that a
product capability is missing. Code, contracts, current tests, and the task's
acceptance condition must be reconciled before changing status.

## 2. Current delivered baseline

The following capabilities have usable implementations. Their individual
feature artifacts remain the detailed source of behavior and limitations.

| Capability | Status | Primary feature evidence |
|---|---|---|
| Market overview, breadth, regime, and market-data refresh | **Delivered** | `specs/001-market-overview/`, `specs/026-refresh-automation/` |
| Stock detail, technical indicators, fundamentals, valuation, and normalized provider data | **Delivered** | `specs/002-stock-detail-analysis/`, `specs/008-provider-data-expansion/` through `specs/025-fundamental-summary-ttm-eligibility/` |
| Deterministic stock screening | **Delivered** | `specs/003-stock-screener/`, `specs/009-extended-fundamentals-screening/` |
| Strategy signals and risk classification | **Delivered** | `specs/004-strategy-signal-risk/` |
| Portfolio and watchlist | **Delivered** | `specs/005-portfolio-watchlist/` |
| News, documents, retrieval, and cited Q&A | **Delivered** | `specs/006-news-document-rag/` |
| AI Analyst with structured tools, attribution, graceful degradation, and discovery archetypes | **Delivered** | `specs/007-ai-analyst/`, `specs/015-analyst-e2e-on-real-data/`, `specs/027-analyst-discovery-expansion/` |
| Tabular peer comparison | **Delivered** | `specs/028-peer-comparison-tool/`; graphical comparison is outside the current product scope. |
| Owner-scoped AI conversation history with bounded sliding context | **Delivered** | `specs/029-ai-conversation-history/` |
| Deterministic position sizing | **Operational follow-up** | `specs/030-deterministic-position-sizing/`; implementation and automated feature gates pass, owner comprehension review remains pending. |
| Historical strategy backtesting | **Operational follow-up** | `specs/031-historical-strategy-backtesting/`; implementation and enabled worker configuration are complete; owner comprehension review remains pending. |

“Delivered” refers to the implemented feature slice, not a claim that every
future extension in the same SRS domain is complete.

## 3. Remaining product capabilities

### Wave A — complete decision workflows

These slices provide the strongest immediate user value and establish data
models needed by later personalization features.

| Order | Capability | SRS traceability | Current state | Minimum coherent first slice | Dependencies and correctness gates |
|---|---|---|---|---|---|
| A2 | Historical strategy backtesting | SRS-BKT-01, SRS-BKT-02 | **Implemented; owner review pending** in Feature 031. | Run any of eight supported deterministic strategies for one symbol and daily interval over an explicit period; configure capital, risk, fees, tax, and slippage; inspect immutable trades, equity, metrics, evidence, warnings, and rejected entries. | One bounded worker is enabled by default and can be disabled during maintenance or incident response. VCI provider-adjusted prices, current lot rules applied historically, survivorship, corporate actions, and suspension/delisting coverage remain explicit limitations. |

Backtesting should follow the position-sizing contract so the interactive
calculator and simulation engine do not develop conflicting sizing semantics.

### Wave B — ongoing use and research depth

| Order | Capability | SRS traceability | Current state | Minimum coherent first slice | Dependencies and correctness gates |
|---|---|---|---|---|---|
| B1 | Configurable alerts | SRS-ALR-01 | **Implemented; final environment validation pending** in Feature 032. | Create, enable, disable, list, and delete a bounded set of deterministic price, indicator, signal, portfolio-risk, or document-event alerts; inspect accepted evidence and operate a durable in-app inbox. | The default-enabled worker polls every 10 seconds in batches of 50, uses database leases and deduplication, withholds stale/missing facts, enforces owner quotas, and makes at most two total attempts. External delivery channels remain outside this slice. |
| B2 | Sector analytics | SRS-MKT-03, SRS-MKT-04 | **Planned**. Sector reference data exists, but sector performance, momentum, liquidity, relative strength, and leader/laggard analysis do not. | Compare supported sectors over explicit windows and identify leading/weak stocks using deterministic, exposed formulas. | Requires stable point-in-time sector membership, coverage thresholds, aggregation/weighting rules, adjustment basis, liquidity units, and missing-member handling. |
| B3 | Investment journal | SRS-JRN-01 | **Planned** | Owner-scoped create/read/update/delete journal entries linked optionally to a symbol, portfolio, strategy signal, and conversation; capture thesis, assumptions, decision, tags, and event time. | Reuse conversation ownership where linked. Define retention, export/deletion, timezone, immutable audit fields, and the boundary between user-authored facts and AI summaries. |

### Removed from the product roadmap

The 2026-09-11 product-scope decision removed the following planned extensions:

- AI-generated daily market briefing;
- personalized analytics derived from journal or conversation history;
- a composite/overall stock score;
- graphical peer-comparison views or additional peer-comparison depth beyond
  the delivered tabular comparison;
- advanced price-structure, candlestick-pattern, Fibonacci, and
  multi-timeframe analysis.

Existing core technical indicators, deterministic breakout strategies,
separate risk/valuation classifications, and the delivered tabular comparison
remain supported. Deprecated SRS identifiers are preserved for traceability and
must not be reused.

## 4. Proposed implementation sequence

Unless user value or newly discovered dependencies justify a change, start
features in this order:

1. Complete the position-sizing owner comprehension review.
2. Complete the historical-backtesting owner review.
3. Complete the configurable-alerts owner/environment review.
4. Sector analytics.
5. Investment journal.

This ordering is a planning recommendation, not a reservation of feature
numbers. The next capability that enters development should receive the next
available `specs/NNN-short-name/` directory and complete the repository's
`specify -> clarify -> plan/research/contracts -> tasks -> analyze -> implement
-> validate` workflow. Do not create empty feature directories for later waves.

## 5. Operational follow-ups separate from the feature roadmap

These items should be closed or explicitly re-baselined, but they do not mean
that an entire product feature is absent:

| Area | Status | Required follow-up |
|---|---|---|
| Market overview/live operation | **Operational follow-up** | Reconcile and complete the private-ingress/runbook, full-gate/live validation, and refresh configuration tasks recorded as T051, T077, and T079 in `specs/001-market-overview/tasks.md`. |
| Stock-detail usability | **Operational follow-up** | Run and record the three owner usability trials required by T073 in `specs/002-stock-detail-analysis/tasks.md`. |
| VCI fundamentals normalization | **Operational follow-up** | Run the owner re-crawl/re-import and real-data checks required by T007 in `specs/018-vci-fundamentals/tasks.md`, including the migration from legacy VCI debt-to-equity ratios to percentage units. |
| AI Analyst task ledger | **Documentation reconciliation** | T051-T060 in `specs/007-ai-analyst/tasks.md` appear unchecked even though corresponding attribution, coverage, fallback, explanation-grounding, DTO/UI, and evaluation code/tests exist. Verify acceptance evidence and update task status before treating them as missing implementation. Remove or replace template placeholder T001 entries during that reconciliation. |

Defects discovered while implementing these follow-ups belong in
`docs/REMEDIATION_PLAN.md` with stable `Q-` identifiers. Product additions
belong in a feature specification; the remediation backlog must not become a
second product roadmap.

## 6. Current non-goals

The following remain outside active scope unless the constitution and SRS are
deliberately amended:

- automated broker order placement or automatic investment execution;
- autonomous AI trading or guaranteed-return behavior;
- high-frequency trading;
- complex predictive machine-learning models without a separately approved use
  case and evaluation plan;
- a mobile application;
- large-scale microservice decomposition or event infrastructure without
  measured need and an ADR.

Broker connectivity may be researched later for read-only data or explicitly
approved workflows, but it must not be inferred from the backtesting, sizing,
alert, or AI roadmap.
