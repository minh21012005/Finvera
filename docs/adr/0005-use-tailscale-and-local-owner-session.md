# ADR-0005: Use Tailscale and a Local Owner Session

**Status**: Accepted  
**Date**: 2026-08-17  
**Decision owners**: Finvera maintainer  
**Related feature**: `001-market-overview`

## Context

The private TCBS/Vnstock deployment must be reachable remotely by one owner
without becoming a public website. Network privacy alone does not satisfy the
constitution's Spring authorization boundary, while a full external identity
provider is unnecessary for the initial single-owner scope.

## Decision

- Tailscale Serve/private tailnet routing is the only ingress. Funnel and direct
  public frontend/backend ports remain disabled.
- Spring Security authenticates exactly one configured local owner using a
  stable UUID, normalized username, and offline-generated `{bcrypt}` hash with
  cost 12 stored in deployment secrets. No raw/bootstrap password is committed.
- Login rotates a server-side session. `FINVERA_SESSION` is Secure, HttpOnly,
  SameSite=Strict, path `/`, idle-expires after 30 minutes, and has an absolute
  eight-hour lifetime.
- State-changing requests require same-origin CSRF validation. Login uses
  bounded backoff/rate limiting and uniform credential errors.
- Tailscale identity headers are not an authorization substitute. Every
  protected request is authorized by Spring's local owner session.
- V1 has no self-registration, invitation, second user, password-reset email,
  long-lived API token, or public recovery flow.

## Consequences

The design provides two independent controls with no new identity service or
Redis. A process restart invalidates sessions, and password rotation requires
generating/replacing the secret hash and restarting the application. Those are
acceptable operational costs for one owner. Multi-user/public use requires a
dedicated authentication feature and migration, not extension of this shortcut.

## Alternatives

- Tailscale-only identity: rejected because Spring must authorize requests.
- External OIDC/Keycloak: deferred as excessive for one owner.
- HTTP Basic or long-lived bearer token: rejected for browser/session security.
- Public proxy with secret URL: rejected because secrecy of a URL is not auth.
