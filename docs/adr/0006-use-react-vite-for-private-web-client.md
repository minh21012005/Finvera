# ADR-0006: Use React and Vite for the Private Web Client

**Status**: Accepted  
**Date**: 2026-08-17  
**Decision owners**: Finvera maintainer  
**Related specs**: `specs/001-market-overview/`

## Context

The SRS and initial scaffold selected Next.js. The clarified first deployment is
an authenticated research workspace reached through private ingress, while
Spring Boot already owns authentication, authorization, and every public API.
The frontend has no SEO, public-content rendering, React Server Component, or
frontend server/BFF requirement. At decision time the existing Next.js project
was an unmodified starter, so the migration had negligible cost. The repository
now contains the Vite SPA; Next.js runtime/build artifacts are no longer part
of the client.

This ADR refines the SRS technology baseline for implementation without changing
the product scope or the browser-to-Spring security boundary.

## Decision Drivers

- Operate the private deployment with the fewest required runtime processes.
- Keep authentication, CSRF, provider credentials, and business logic in Spring.
- Support an interactive dashboard, charts, screening, portfolio views, and AI
  conversations without requiring server-rendered HTML.
- Preserve a path to a future multi-user application without coupling it to a
  public marketing site.

## Considered Options

### Option A: React SPA built with Vite

Build static assets and serve them behind the same private origin as Spring.
Client-side routing and data fetching use only versioned Spring APIs.

### Option B: Keep Next.js

Retain the existing App Router and a Node.js frontend runtime. This remains a
valid choice if public SEO, server rendering, or frontend-owned server behavior
becomes an approved requirement, but none is required now.

## Decision

Use React 19, TypeScript, Tailwind CSS, and Vite for `finvera-fe`. Deploy the
compiled SPA as static assets behind the private reverse proxy. Route `/api/*`
to Spring Boot on the same origin. Spring remains responsible for sessions,
CSRF, authorization, and public API behavior.

A future public content/marketing surface may use a separate application. A
return to SSR or Next.js requires evidence from an approved feature and a new
ADR; it must not move financial truth or authorization into the frontend.

## Consequences

### Positive

- Removes the unnecessary Node.js production server and Server Component model.
- Simplifies same-origin session-cookie, CSRF, deployment, and failure behavior.
- Keeps the client focused on presentation and interaction.

### Negative / Trade-offs

- Initial HTML is not server-rendered and public SEO is not optimized.
- Client-side route fallback and static-asset cache policy must be configured.
- Any later SSR requirement needs a separately designed migration.

### Risks and Mitigations

- **Oversized client bundle**: use route-level lazy loading and enforce build
  visibility for chunk sizes.
- **Stale deployment assets**: use hashed assets and `no-cache` for `index.html`.
- **Authentication drift**: keep all enforcement in Spring and cover owner,
  non-owner, CSRF, and expiry paths with integration/E2E tests.

## Migration and Rollback

The untouched Next.js starter was replaced at the manifest, configuration,
entry-file, and agent-guidance boundaries while preserving React and Tailwind.
Rollback requires a new reviewed frontend decision because Feature 001 is now
implemented and tested on Vite; no database migration is involved.

## Validation

- `npm run test`, `npm run lint`, and `npm run build` pass.
- The built SPA contains no provider/API secrets and calls only same-origin
  Spring endpoints.
- Feature 001 Playwright P1-P3 fixture and owner-denial scenarios pass on the
  loopback topology. Remote private-origin topology remains gated by T051.
