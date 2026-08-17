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
frontend server/BFF requirement. The existing Next.js project is still an
unmodified starter, so changing it now has negligible migration cost.

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

Replace only the untouched Next.js starter: manifests, configuration, entry
files, and agent guidance. Preserve React and Tailwind. If the Vite baseline
cannot pass lint/build and the Feature 001 contract tests, restore the previous
starter files before feature UI implementation; no data migration is involved.

## Validation

- `npm run lint`, `npm run test -- --run`, and `npm run build` pass.
- The built SPA contains no provider/API secrets and calls only same-origin
  Spring endpoints.
- Feature 001 Playwright owner and denial scenarios pass through the deployed
  private-origin topology.

