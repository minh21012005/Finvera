# Feature 013: TCBS Thesis Live-Path Field Audit

**Status**: Code audit done 2026-08-31; live capture done 2026-09-04 (session open)
**Closes**: docs/REMEDIATION_PLAN.md P2-01
**Contracts**: `tcbs-thesis-private-live-v1`
(specs/001-market-overview/contracts/tcbs-thesis-live-overlay.md), `tcbs-iflash-adapter.md`
**SRS References**: SRS-MKT-05 (live market overview), SRS-MKT-08 (stock live quote)

## Problem

Every other ingestion path was re-audited field by field against the provider's real payloads
(Feature 011 for KBS, Features 018/020/021 for VCI). The TCBS Thesis live overlay — the only
streaming path, and the one that feeds the market overview and the stock detail quote during a
session — was never given the same treatment. Its contract was written from provider
documentation plus the original R-015 evidence, and the code was reviewed against that contract,
which proves internal consistency and nothing about the provider.

That gap matters because **every guard in the live path is unit-agnostic**. `TcbsThesisFrameMapper`
rejects negatives, missing required fields and unknown index numbers, but a provider that started
publishing prices in thousand VND (the Vietnamese board unit that KBS and VCI both use, and that
`export_daily_bars.normalize_board_price` exists to convert) would pass every check and quietly
put 61.9 next to a 61,900 VND historical close. The same is true of the `tickerCommons` REST
snapshot, whose price fields are stored with no conversion at all.

Only an open trading session can settle this: outside a session the stream is silent.

## Scope

In scope: the live path's provider boundary — WS frames `s|4`, `s|6`, `s|8`, the control frames the
server actually sends, the transport rules the server enforces, and the REST `tickerCommons`
snapshot; the contract text that describes them; a pinned fixture so drift is caught by tests.

Out of scope: changing live-overlay behavior, the retired `TCBS_IFLASH_MARKET_DATA` adapter beyond
its existing exclusion, and any provider tier or subscription change.

## Requirements

- **DATA-001** Every field the live path consumes carries a unit, a JSON type and a semantic
  definition in the contract, each backed by an observed live value — not by provider
  documentation and not by our own code's assumption.
- **DATA-002** The base-VND/share claim for equity prices and the points claim for index levels are
  verified during one open session against an **independent provider** (VCI), per the working rule
  that provider labels are never their own anchor.
- **DATA-003** Frames and behaviors the provider exhibits but the contract does not describe are
  added to the contract: server control frames, whether `s|4` is replayed on a mid-session
  subscription, and the mixed string/number typing of numeric fields.
- **DATA-004** The reference-price chain that actually makes a quote publishable is documented in
  the order it is attempted: `s|4.refPrice` → derived `matchPrice − change` → `tickerCommons`.
- **DATA-005** `tickerCommons` price fields (`refPrice`, `ceilPrice`, `floorPrice`, `open`, `high`,
  `low`) are confirmed to be in the same base-VND unit as the stream, or a conversion with a rule
  id is added at the boundary. `room` carries a documented unit.
- **DATA-006** A fixture pinned from real captured frames is asserted by tests, so a provider unit
  or type change fails the build instead of reaching the database.
- **DATA-007** The live overlay's last matched price of a session reconciles with the EOD close for
  three symbols within the contract's tolerance.
- **NFR-001** Transport constraints the provider enforces are documented and honored by the client,
  including the fact that an RFC WebSocket ping frame gets the connection closed with 1002.
- **SEC-001** The capture tool reads the API key from `finvera-be/.env`, takes the OTP without
  echoing it, keeps the exchanged JWT in memory only, and writes neither to stdout nor to the
  capture file.

## Success criteria

- **SC-1** A live capture of at least 60 seconds with successful authentication, covering `s|6` for
  a liquid symbol and `s|8` for all four subscribed indexes, is stored as evidence.
- **SC-2** Every captured price/level compares to the independent anchor within intraday drift
  (ratio in [0.9, 1.1]); no field is off by a factor near 1000.
- **SC-3** The contract lists every consumed field with unit, type and semantics, plus the control
  frames, the transport rules and the reference-price chain.
- **SC-4** A fixture built from real frames is asserted by a backend test that fails if a value's
  unit magnitude or JSON type changes; the backend suite stays green.
- **SC-5** Live-vs-EOD reconciliation recorded for three symbols.
- **SC-6** P2-01 closes in docs/REMEDIATION_PLAN.md with the evidence file named.

## Acceptance scenarios

1. **Given** an open trading session and a valid owner OTP, **when** the capture tool subscribes to
   the allowlisted index and price-board channels for VNM/MBB/ACV, **then** it records every frame,
   inventories every field with type and value range, and compares the consumed numbers with VCI.
2. **Given** a captured `s|6` for a liquid symbol, **when** its `matchPrice` is divided by the same
   symbol's VCI price converted to base VND, **then** the ratio is ~1, not ~1000 or ~0.001.
3. **Given** a symbol first subscribed in the middle of a session, **when** no `s|4` frame arrives,
   **then** the documented fallback still yields a positive reference and the contract says so.
4. **Given** the pinned fixture, **when** a future provider change alters a field's type or unit,
   **then** the fixture test fails before any such value can be persisted.
