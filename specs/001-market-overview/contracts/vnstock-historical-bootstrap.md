# Provider Contract: Vnstock Historical Bootstrap

**Contract version**: `vnstock-history-private-bootstrap-v1`  
**Feature**: `001-market-overview`  
**Status**: Technical coverage gate passed on 2026-08-17; upstream-use and full-universe gates remain blocking

## Purpose and boundary

Vnstock is used only as an owner-operated, non-commercial historical bootstrap
and gap-recovery tool. It supplies completed-session OHLCV/reference datasets
needed by `market-regime-v1`; it is not Finvera's live feed, public API, or
runtime source-of-truth service.

The importer runs offline from an operator command, produces a canonical
versioned import package, and submits that package to Spring Boot's validated
internal import boundary. It does not run inside `finvera-ai`, does not write
PostgreSQL directly, and does not introduce a new deployable service.

## Required datasets

- Completed daily VN-Index history sufficient for SMA200, momentum, liquidity,
  and 252-session volatility plus warm-up.
- Completed daily equity OHLCV history sufficient to calculate the percentage
  of the eligible HOSE/HNX/UPCOM universe above SMA50.
- Symbol, exchange, trading date, adjustment status, and source/provider
  identity needed to reconcile the imported observations.

At least 271 completed sessions are required for the initial bootstrap. More
history may be imported only when retention and source terms permit it.

## Canonical import package

Each package contains:

- contract version, Vnstock package version, selected upstream source code,
  generated-at time, requested date range, and package checksum;
- one manifest entry per dataset/subject with record count and minimum/maximum
  trading date;
- decimal values serialized as strings, explicit VND/unit metadata, exchange,
  adjustment status, and missing-value reason; and
- no account credential, API key, raw HTTP payload, cookie, token, or PII.

Spring validates the package schema, checksum, subject identities, trading
calendar, decimal bounds, duplicate dates, monotonic date order, and provenance
before creating immutable accepted observations. Invalid packages are rejected
atomically and never partially imported.

## Fixture and license gate

Before implementation is approved, the owner MUST prove with sanitized output:

1. the pinned Vnstock version returns the required index and equity history;
2. the selected upstream source is explicit and stable for every dataset;
3. at least 271 completed sessions are available with usable OHLCV and volume;
4. missing, suspended, newly listed, adjusted, and corrected observations can
   be represented without replacing missing data with zero;
5. request limits permit the bootstrap and bounded gap recovery; and
6. the owner accepts the personal/non-commercial Vnstock license and verifies
   that upstream-source terms permit the intended private storage and analysis.

Vnstock is an extraction tool rather than the owner of the underlying data.
Its software license does not by itself grant public display or redistribution
rights for upstream data.

### Sanitized technical evidence

The non-production probe pinned Vnstock `4.0.6` with explicit source `KBS` and
passed the representative history gate on 2026-08-17:

- VNINDEX, HNXINDEX, and UPCOMINDEX: 652 daily rows each;
- VNM (HOSE) and SHS (HNX): 652 daily rows each;
- MCH (UPCOM): 647 daily rows;
- required OHLCV columns present with no nulls in those samples; and
- stock reference coverage: 404 HOSE, 299 HNX, and 823 UPCOM symbols.

The local summary intentionally contains schema/count/date evidence only and is
gitignored. Its SHA-256 is
`B15DA17601D8C7D529D50BCC09315918C7115BFB9CD9F57532E41F4E5C9EC864`.

This supplies representative evidence toward items 1-3 but does not prove
source stability for every dataset. Items 4-6, a bounded full-universe run,
upstream KBS private storage/automation rights, request limits, adjustment
semantics, and correction behavior remain unresolved.

The observed history uses `float64` price columns. Production code MUST parse
canonical decimal strings into exact decimals at the import boundary and MUST
NOT use the observed binary floats for authoritative calculations. The tested
OHLCV shape has no matched-value field; missing historical liquidity follows
the versioned regime missing-component rule and is never inferred or zeroed.

## Reconciliation with TCBS

For an overlapping completed session, compare Vnstock close/reference facts
against the final accepted TCBS observation using exact decimals and a
versioned tolerance policy. Preserve both sources. A material conflict creates
`SOURCE_CONFLICT`; it is never silently overwritten or averaged, and the
affected regime assessment is withheld until an approved source wins through
an auditable correction/reconciliation decision.

## Operational restrictions

- No automatic high-frequency scraping or unbounded retry.
- No browser, frontend, or public endpoint invokes Vnstock.
- No raw response retention after canonical package verification.
- Secrets and Vnstock user credentials stay outside source, packages, logs,
  telemetry, and PostgreSQL.
- Public or multi-user use requires replacement with a commercially licensed
  provider and a new ADR.

## Evidence

- [Vnstock repository](https://github.com/thinh-vu/vnstock) documents equity
  and index OHLCV APIs, request tiers, personal/non-commercial licensing, and
  its status as a data-extraction tool rather than a data owner.
