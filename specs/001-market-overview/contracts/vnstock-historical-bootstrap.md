# Provider Contract: Vnstock Historical Bootstrap

**Contract version**: `vnstock-history-private-bootstrap-v1`  
**Feature**: `001-market-overview`  
**Status**: Private KBS upstream-use accepted by owner on 2026-08-18; full-universe and adjustment/correction gates remain blocking

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

The provider-neutral Spring import core may be built and unit-tested before the
external Vnstock gate closes. It accepts only a reviewed canonical package:
the exporter supplies the exact canonical payload used for `package_sha256`,
while the core records an `ACCEPTED` import batch plus immutable instrument,
ingestion, and price facts in one transaction. It neither invokes Vnstock nor
accepts raw provider responses. Wiring an exporter to that boundary, and using
it with real data, remains blocked by the gates below.

The internal import bean is disabled by default and is enabled only by the
explicit `finvera.market.import.enabled=true` operator configuration after the
relevant gate and operator runbook are approved.

## Owner-approved local-only exception

On 2026-08-17, the owner explicitly accepted the residual upstream-use risk for
a manually operated, local-only and non-commercial Vnstock/KBS bootstrap. On
2026-08-18, the owner confirmed that KBS accepts the intended private use. This
authorizes private storage and analysis for this contract only; it is not a
right to public display, redistribution, multi-user deployment, or remote
operation. The restrictions remain mandatory: no public endpoint, scheduled or
unbounded collection, credential persistence, or raw-response retention. This
approval may be revoked before any run and is invalid for a public or remote
deployment.

## Fixture and license gate

Before a general or public provider activation is approved, the owner MUST prove with sanitized output:

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
gitignored. The latest rerun used the owner's Vnstock Community entitlement
(60 requests/minute) and produced SHA-256
`2ded0d53a27c2e2319a3dcab65ab42e8981b0196c60885b36b9e6024d186344c`.

This supplies representative evidence toward items 1-3 but does not prove
source stability for every dataset. The owner has since confirmed the limited
private KBS use described above. A bounded full-universe run, request-limit
evidence, adjustment semantics, and correction behavior remain unresolved.

### Bounded full-universe probe

`tools/market-data/provider-poc/poc_vnstock.py --full-universe` is the
non-production technical probe for this remaining coverage evidence. It is
explicitly opt-in, defaults to 30 requests/minute (below the owner's Community
limit), and can be bounded with `--max-symbols`. A checkpoint stores only
one-way symbol fingerprints, aggregate status counts, and safe failure types;
it stores neither prices nor raw provider responses. `--resume` continues a
matching source/range/universe checkpoint without re-requesting completed
symbols. The output summary contains aggregate counts by exchange and no symbol
identifiers or market values.

After a recorded representative success, a resumed batch may use
`--skip-representative` to avoid consuming quota on the eight sample requests.
That summary is explicitly marked `NOT_RECHECKED`; it cannot be used as new
representative-gate evidence.

The probe is evidence gathering only. A partial run, a successful probe, or a
Vnstock Community entitlement does not by itself close the adjustment or
correction gates and does not expand the owner's limited KBS private-use
approval.

**Initial bounded-run evidence (2026-08-17):** the checkpointed batches have
processed 56 of 1,526 eligible equities at 30 requests/minute: 42 have usable
271-session history and 14 are recorded as `INSUFFICIENT_HISTORY`. There were
no provider-failure attempts and 1,470 candidates remain. Its latest sanitized
summary SHA-256 is
`a118e2cb6565fca08dab61747c42eb441c96091ac6ec7cb4da918a79131812fd`.
This validates rate limiting, sanitized checkpoint migration/resume, and
explicit unavailable-history reporting only; it is deliberately not
full-universe coverage.

The observed history uses `float64` price columns. Production code MUST parse
canonical decimal strings into exact decimals at the import boundary and MUST
NOT use the observed binary floats for authoritative calculations. The tested
OHLCV shape has no matched-value field; missing historical liquidity follows
the versioned regime missing-component rule and is never inferred or zeroed.

## Reconciliation with TCBS

`tcbs-vnstock-reconciliation-v1` treats TCBS iFlash as the priority source for
live/final observations and corrections; Vnstock is historical bootstrap and
gap recovery only. Compare only equally adjusted, normalized raw facts. Values
must match exactly at the persisted six-decimal scale: any difference greater
than `0.000001` is `SOURCE_CONFLICT`. Preserve both provenances; never average
or overwrite. A conflict or non-comparable adjustment status makes dependent
breadth/regime output `PARTIAL` or `UNAVAILABLE` until an auditable correction
selects a source. When both agree, TCBS is the canonical input. Vnstock may be
accepted alone while TCBS is unavailable, but is labeled historical bootstrap,
never live/final.

`source_reconciliation_audit` stores immutable links to the two accepted
ingestion records whenever the result is `SOURCE_CONFLICT` or `NON_COMPARABLE`.
The reconciliation boundary is idempotent for the same pair and policy version;
it records no implicit source selection and never averages values. A future
TCBS adapter must invoke this boundary with normalized raw facts, and any
operator-approved correction remains a separate auditable workflow.

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
