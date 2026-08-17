# Finvera Frontend Instructions

This file extends the repository-level `AGENTS.md` for `finvera-fe/`.

## Responsibility and Boundaries

- The frontend is a research workspace, not a trading terminal. Present
  evidence, assumptions, timestamps, uncertainty, and risk without promising
  returns or issuing unsupported buy/sell directives.
- Call only the Spring Boot public API. Never call `finvera-ai`, Qdrant,
  PostgreSQL, Redis, an LLM provider, or a market-data provider from the browser.
- Do not place secrets in `VITE_*` variables or client bundles. Every `VITE_*`
  variable is public at build time.
- Treat server responses and rich document/news content as untrusted input.

## React, Vite, and TypeScript

- Target versions pinned in `package.json`; do not introduce an SSR/BFF runtime
  without an approved feature need and ADR.
- Use client-side routes and call only same-origin Spring endpoints. Keep API
  access in typed feature clients rather than UI components.
- Keep API DTOs, domain display models, and chart view models explicit. Avoid
  `any`, unchecked casts, and duplicated calculation logic.
- Financial calculations and signal decisions belong to the backend. The UI may
  format values but MUST NOT independently recompute authoritative results.
- Every asynchronous screen needs explicit loading, empty, stale, partial, and
  error states. AI unavailability must not break non-AI analysis views.

## Financial UX and Accessibility

- Display data timestamp, source/provenance where relevant, unit, currency, and
  whether prices are adjusted. Do not imply live data when it is delayed.
- Do not rely on red/green or color alone for direction, risk, or confidence.
- Use locale-aware formatting for Vietnamese users while preserving exact
  values in accessible labels/tooltips where rounding could mislead.
- Charts need keyboard-accessible summaries or equivalent textual evidence.
- Preserve WCAG-compatible focus, semantics, contrast, and reduced-motion
  behavior for new components.

## Verification

- Add component tests for decision-heavy states and end-to-end tests for P1
  journeys when the testing stack is introduced.
- Run `npm run test`, `npm run lint`, and `npm run build` from
  `finvera-fe/` before handoff.
