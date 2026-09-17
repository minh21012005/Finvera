# Research: Configurable Alerts

## R-001 — Evaluation transport

**Decision**: Poll due enabled definitions from PostgreSQL every 10 seconds in batches of 50, with a database lease and one worker thread by default.

**Rationale**: The feature permits at most 50 enabled alerts per owner and the accepted facts already live in PostgreSQL-backed modules. Polling satisfies the 30-second objective without Kafka, an outbox retrofit across four modules, or a new service.

**Alternatives considered**: Kafka or domain events would add a broker and reliable publication work without demonstrated scale. Request-time evaluation would miss facts imported while the owner is offline.

**Risks/validation**: A 50-alert fixture must complete within the budget. Due-age and duration metrics reveal when the assumption stops holding.

## R-002 — Source-module boundaries

**Decision**: Add narrow read-only application contracts to stock, market, portfolio, and research. Alerts never injects their repositories or entities.

**Rationale**: Existing stock references omit historical indicator fact metadata; regime references omit labels/prior observations; portfolio analytics is tied to authenticated HTTP use; research lacks an accepted-document cursor API. Additive interfaces close those gaps while preserving ownership.

**Alternatives considered**: Cross-module repository access violates the constitution. Copying source facts before evaluation creates a second source of truth. Calling public HTTP inside the monolith adds latency and authentication complexity.

**Risks/validation**: ArchUnit and contract tests verify dependency direction and fact metadata.

## R-003 — Deterministic condition representation

**Decision**: The public API and Java domain use a discriminated, allowlisted condition hierarchy. PostgreSQL stores canonical `condition_payload` JSONB plus `condition_type` and `condition_version=alert-condition-v1`; validation occurs before persistence.

**Rationale**: Eleven conditions have different fields. A typed hierarchy prevents arbitrary expressions while JSONB avoids a sparse table or eleven child tables. Canonical serialization provides an immutable summary and fingerprint.

**Alternatives considered**: Nullable columns produce difficult cross-column constraints. An untyped map weakens validation. One table per condition adds joins without independent lifecycles.

**Risks/validation**: Round-trip tests cover every subtype and reject unknown or mismatched fields.

## R-004 — Episode and deduplication semantics

**Decision**: State conditions keep `episode_state` (`UNKNOWN`, `FALSE`, `TRUE`). Only `FALSE/UNKNOWN -> TRUE` increments `episode_sequence`; `TRUE -> TRUE` does not deliver; an eligible `FALSE` rearms. Event conditions use immutable source event IDs. Unique indexes on `(alert_id, episode_sequence)` and `(alert_id, event_key)` enforce deduplication.

**Rationale**: This directly implements false/true/true/false/true behavior and lets PostgreSQL enforce the final invariant.

**Alternatives considered**: Cooldowns can duplicate or suppress real episodes. Fact-ID-only dedup still notifies on every true fact. In-memory locks fail across restarts.

**Risks/validation**: Transactional concurrency and crash-recovery tests attempt duplicate commits.

## R-005 — Enable baseline and historical replay

**Decision**: Creation or re-enable records `baseline_at` and clears episode state to `UNKNOWN`. State alerts evaluate only the latest eligible fact set and may notify once if true. Document alerts query accepted documents strictly after the baseline accepted time.

**Rationale**: The owner receives current relevance without a flood of old episodes or documents.

**Alternatives considered**: Starting all alerts as false would manufacture a transition. Full replay contradicts FR-013.

**Risks/validation**: Fixtures create old and new documents and already-true state facts around enable time.

## R-006 — Market and indicator time semantics

**Decision**: Price, RSI, MACD, moving averages, volume, and breakout use accepted completed daily observations. Crossovers require two distinct consecutive eligible trading dates. Volume and breakout windows use 20 prior eligible sessions excluding current. Regime uses two distinct accepted EOD assessments and derives labels through the market module's versioned rule.

**Rationale**: These semantics prevent intraday instability and look-ahead leakage and match existing deterministic engines.

**Alternatives considered**: Live facts and calendar-day windows do not meet the approved spec. Deriving labels inside Alerts duplicates market policy.

**Risks/validation**: Holiday gaps, incomplete windows, equality, corrected facts, and one-observation cases have explicit tests.

## R-007 — Precision, units, and adjustment

**Decision**: Use `BigDecimal`; persist thresholds and observed values as canonical decimal strings, calculate at scale 12, and use `HALF_UP` only for division. Prices are VND/share and must match the selected adjustment basis. RSI and concentration are percentages in `[0,100]`; volume multipliers are ratios greater than zero.

**Rationale**: Decimal strings preserve exact user input and avoid JSON binary floating point. Explicit unit and basis prevent invalid comparisons.

**Alternatives considered**: `double`, implicit percentage fractions, or mixed price bases are ambiguous or irreproducible.

**Risks/validation**: Tests include exact equality, repeating division, and mismatched basis.

## R-008 — Portfolio concentration

**Decision**: The portfolio module returns largest positive position market value divided by total positive invested position market value, as a percentage. It validates ownership and returns source bar metadata, a coherence key, status, and reasons. Empty, deleted, missing-price, or partial portfolios are withheld.

**Rationale**: Portfolio already owns transaction replay and price coherence. Cash is excluded because the requirement specifies invested position value.

**Alternatives considered**: Including cash changes the approved ratio. Recomputing in Alerts crosses module boundaries.

**Risks/validation**: Empty, partial-price, deleted, and concentrated fixtures are covered.

## R-009 — Document eligibility

**Decision**: A document is eligible only when ingestion is `READY`; `processed_at` is accepted time and document ID is the event key. Matching uses owner, the required selected symbol, optional `DocumentType`, and `processed_at > baseline/cursor`.

**Rationale**: `PENDING` is not accepted and `FAILED` cannot be relied on. Existing metadata supplies stable category, symbol, source, publication date, and accepted time.

**Alternatives considered**: Submission-time triggers can announce failed ingestion. Document contents are unnecessary and private.

**Risks/validation**: Pending/failed, missing association, duplicate callback, and category mismatch fixtures are covered.

## R-010 — Retry and delivery

**Decision**: An evaluation gets at most two total attempts (initial plus one retry) with a 60-second lease. Missing/stale/partial data is a terminal `WITHHELD` outcome for that fact. In-app delivery is the notification insert in the same transaction and records one `DELIVERED` attempt.

**Rationale**: There is no external delivery dependency. Retrying unavailable source data wastes time; a newer fact is evaluated by a later poll.

**Alternatives considered**: Unbounded retry can prevent completion. A separate in-app queue creates failure states without value.

**Risks/validation**: Injected transient exceptions, stale leases, and duplicate claims verify bounded behavior.

## R-011 — Retention and deletion

**Decision**: Alert deletion is soft (`deleted_at`) and disables evaluation. Prior evaluations and notifications remain; notifications store immutable condition/evidence snapshots. No automatic retention is introduced.

**Rationale**: Historical inbox evidence must survive alert deletion and retention is deferred.

**Alternatives considered**: Cascade deletion violates DATA-009/010. Hard delete loses auditability.

**Risks/validation**: Deletion tests confirm no new evaluation and unchanged old notifications.

## R-012 — Frontend and delivery scope

**Decision**: Add an `/alerts` page with a condition-aware creation form, definition list, evaluation history, inbox, unread count, and read controls. In-app is the only channel.

**Rationale**: This delivers the approved journey in the existing SPA without provider credentials or privacy work.

**Alternatives considered**: Email, Telegram, SMS, push, arbitrary expressions, and AI-authored rules are separate features.

**Risks/validation**: Component tests verify keyboard access, text alternatives, validation, polling refresh, and stable error states.
