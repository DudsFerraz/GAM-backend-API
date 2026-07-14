# ADR-0001: Use Cross-Site-Compatible Refresh Cookies with CSRF Protection

## Status
Superseded

Superseded by [ADR-0007: Use Same-Origin Browser Sessions with Layered CSRF Protection](0007-use-same-origin-browser-sessions-with-layered-csrf-protection.md).

## Context
GAM needs one refresh-token transport mechanism that works whether the frontend and backend are deployed on the same site or on different sites.

The current frontend and backend are not same-site. A future frontend refactor is planned, but the final deployment topology has not been decided and same-site hosting is not guaranteed.

Refresh tokens are long-lived session secrets. Keeping them out of JavaScript-readable storage reduces the impact of ordinary frontend token-handling mistakes and avoids requiring the frontend to persist refresh tokens directly.

Using cookies for refresh tokens creates CSRF risk because browsers automatically attach cookies to requests. A `SameSite=Strict` cookie would reduce that risk but would not be compatible with cross-site deployment. A `SameSite=None` cookie is cross-site-compatible, but it requires explicit CSRF protection and strict origin controls.

## Decision
Use a single refresh-token cookie mechanism for both same-site and cross-site deployments.

The refresh token shall be stored only in a `refreshToken` cookie with:

- `HttpOnly`
- `Secure`
- `SameSite=None`
- `Path=/`

Auth endpoints that set or consume the refresh-token cookie shall enforce CSRF protection compatible with a stateless API, such as a signed double-submit token or an equivalent framework-supported mechanism.

Credentialed browser requests shall use strict CORS allowlisting. Untrusted origins shall not be allowed to make credentialed auth requests.

Refresh tokens shall not be accepted from JSON request bodies, query parameters, or custom refresh-token headers.

## Alternatives considered

### Option 1: SameSite=Strict refresh cookie
Pros:
- Strong browser-level CSRF mitigation for same-site deployments.
- Simple server-side behavior.
- Keeps refresh tokens out of JavaScript-readable storage.

Cons:
- Does not support deployments where frontend and backend are cross-site.
- Would make authentication behavior depend on an undecided hosting topology.
- Could force a future deployment refactor or cookie-policy change.

### Option 2: SameSite=None; Secure refresh cookie with CSRF protection
Pros:
- Works for both same-site and cross-site frontend/backend deployments.
- Keeps the refresh token out of JavaScript-readable storage through `HttpOnly`.
- Provides one stable frontend/backend contract before deployment topology is finalized.
- Allows CSRF risk to be handled explicitly through token validation and strict origin controls.

Cons:
- Requires HTTPS-capable environments for realistic browser behavior.
- Requires CSRF protection from the beginning.
- Requires careful CORS configuration for credentialed requests.

### Option 3: Refresh tokens in response bodies or headers
Pros:
- Avoids browser auto-attachment of refresh tokens, which reduces classic CSRF exposure.
- Works naturally across sites.
- Has a simpler server-side CSRF story.

Cons:
- Makes refresh tokens reachable by frontend JavaScript.
- Increases the impact of XSS and frontend storage mistakes.
- Conflicts with the requirement to keep refresh tokens out of response bodies and JavaScript-accessible storage.

### Option 4: Backend-for-frontend or same-origin proxy
Pros:
- Can provide a strong browser security model.
- Can hide access tokens and refresh tokens from frontend JavaScript.
- Can isolate the public frontend origin from the core API.

Cons:
- Introduces a larger architecture and deployment decision.
- Requires another server/proxy layer.
- Is too broad for the current authentication Requirement Specification.

## Consequences

Positive consequences:
- GAM can deploy frontend and backend either same-site or cross-site without changing the refresh-token transport contract.
- Refresh tokens remain unavailable to JavaScript through the `HttpOnly` cookie.
- CSRF protection becomes an explicit part of the authentication contract instead of an implicit deployment assumption.
- The requirements can be tested consistently across deployment shapes.

Negative consequences:
- CSRF protection must be implemented before this authentication contract is complete.
- Local development and test environments must handle `SameSite=None` and `Secure` cookie behavior intentionally.
- CORS configuration becomes part of the authentication safety boundary.
- Future agents must understand that `SameSite=None` is safe only when paired with CSRF protection and strict origin allowlisting.

## Related requirements
- `docs/requirements/authentication/cross-site-refresh-cookie-compatibility.md`
- REQ-AUTH-012
- REQ-AUTH-013
- REQ-AUTH-014
- REQ-AUTH-017

## Superseding decision

- [ADR-0007: Use Same-Origin Browser Sessions with Layered CSRF Protection](0007-use-same-origin-browser-sessions-with-layered-csrf-protection.md)

## Related diagrams
- None.

## Related videos
- None.
