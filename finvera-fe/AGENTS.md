<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

# Finvera Frontend Instructions

This section extends the repository-level `AGENTS.md` for `finvera-fe/`. Keep
the generated Next.js rule block above intact.

## Responsibility and Boundaries

- The frontend is a research workspace, not a trading terminal. Present
  evidence, assumptions, timestamps, uncertainty, and risk without promising
  returns or issuing unsupported buy/sell directives.
- Call only the Spring Boot public API. Never call `finvera-ai`, Qdrant,
  PostgreSQL, Redis, an LLM provider, or a market-data provider from the browser.
- Do not place secrets in `NEXT_PUBLIC_*` variables or client bundles.
- Treat server responses and rich document/news content as untrusted input.

## Next.js and TypeScript

- Target versions pinned in `package.json` and consult the installed Next.js
  documentation required by the generated block before changing framework APIs.
- Prefer Server Components. Add `"use client"` only at the smallest interactive
  boundary and keep data access/server secrets out of Client Components.
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
- Run `npm run lint` and `npm run build` from `finvera-fe/` before handoff.
