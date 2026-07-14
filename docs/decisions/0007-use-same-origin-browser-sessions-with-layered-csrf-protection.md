# ADR-0007: Use Same-Origin Browser Sessions with Layered CSRF Protection

## Status
Accepted

Supersedes [ADR-0001: Use Cross-Site-Compatible Refresh Cookies with CSRF Protection](0001-use-cross-site-compatible-refresh-cookies-with-csrf-protection.md).

## Context
ADR-0001 selected a `SameSite=None` refresh cookie, credentialed CORS, and explicit CSRF protection because the frontend/backend production topology was undecided and cross-site deployment needed to remain possible.

GAM has now selected a same-origin static SPA and API behind one proxy. Cross-origin production frontend deployment and non-browser authentication clients are outside the initial scope. Retaining `SameSite=None` and credentialed CORS would preserve complexity and trusted-origin surface for an unsupported deployment shape.

Refresh remains cookie-authenticated and login remains vulnerable to login CSRF if a malicious site can submit credentials into the victim's browser context. `SameSite=Lax` is therefore a defense-in-depth control, not the complete CSRF defense.

The frontend also needs a deterministic session-restoration flow without persisting bearer tokens in JavaScript-readable storage.

## Decision
Use a same-origin browser authentication contract for the GAM SPA.

In production, store the refresh token only in a host-only `refreshToken` cookie with:

- `HttpOnly`;
- `Secure`;
- `SameSite=Lax`;
- `Path=/api/auth`; and
- `Max-Age` matching the configured refresh-token lifetime.

Do not enable production CORS for the supported browser workflow.

Protect login, refresh, and logout with all of these layers:

- a framework-supported CSRF cookie-to-header proof;
- exact source-origin validation against `GAM_PUBLIC_ORIGIN`, using `Origin` first and `Referer` as fallback;
- fail-closed behavior for mismatched, opaque, `null`, or absent source-origin evidence; and
- `SameSite=Lax` refresh and CSRF cookies.

Expose `GET /api/auth/csrf` as the explicit, non-cacheable CSRF bootstrap contract. It returns the token and expected header name and establishes the matching JavaScript-readable `XSRF-TOKEN` cookie.

Keep access tokens only in frontend memory. Restore a session by obtaining CSRF proof, refreshing, then loading `GET /api/accounts/me`. Coordinate refresh and logout across tabs through ephemeral browser mechanisms. Reload current effective permissions after refresh and do not use role names as authorization authorities.

Allow `Secure=false` only in an explicit development profile with a loopback HTTP canonical origin. Production requires HTTPS and fails startup for insecure origin or cookie configuration.

## Alternatives considered

### Option 1: Retain the cross-site-compatible ADR-0001 contract
Pros:
- Preserves flexibility for separately hosted frontend and backend origins.
- Avoids changing already implemented cookie and CORS behavior.
- Continues using explicit CSRF protection.

Cons:
- Keeps `SameSite=None` and credentialed CORS for an unsupported topology.
- Expands configuration and testing surface.
- Conflicts with the accepted same-origin production boundary.

### Option 2: SameSite=Strict without explicit CSRF proof
Pros:
- Strong browser-level restriction on cross-site cookie sending.
- Removes the frontend CSRF token handshake.

Cons:
- Treats browser cookie policy as the sole control.
- Does not address every same-site, client-side, legacy-browser, or login-CSRF scenario.
- Can create navigation usability surprises.

### Option 3: SameSite=Lax with origin validation only
Pros:
- Simpler frontend integration than a CSRF token.
- Protects common cross-site unsafe requests and rejects untrusted source origins.

Cons:
- Removes the framework-supported intentional-client proof.
- Relies more heavily on request-header presence and proxy target-origin configuration.
- Provides less defense in depth for a security-sensitive session endpoint.

### Option 4: SameSite=Lax with CSRF proof and exact origin validation
Pros:
- Uses independent browser, token, and source-origin controls.
- Protects login, refresh, and logout through one explicit SPA workflow.
- Removes production credentialed CORS.
- Keeps the refresh secret out of frontend JavaScript.

Cons:
- Requires a CSRF bootstrap and token lifecycle in the frontend.
- Requires correct canonical-origin and trusted-proxy configuration.
- Requires cross-tab refresh and logout coordination because the refresh cookie is shared.

## Consequences

Positive consequences:
- Authentication matches the accepted same-origin deployment.
- Production no longer carries cross-site cookie and CORS flexibility it does not support.
- Refresh tokens remain unavailable to frontend JavaScript.
- CSRF behavior becomes an explicit, testable frontend/backend contract.
- Reload can restore authentication without persistent bearer-token storage.

Negative consequences:
- Cross-origin browser clients cannot use this flow.
- The frontend must implement CSRF bootstrap, in-memory token storage, and cross-tab coordination.
- An official domain must be configured before production can validate exact origins.
- Tests and existing implementation referencing `REQ-AUTH-012/013`, `SameSite=None`, `Path=/`, and credentialed CORS must change in later Agent T and Agent D sessions.

## Related requirements

- `REQ-BROWSER-AUTH-001`
- `REQ-BROWSER-AUTH-002`
- `REQ-BROWSER-AUTH-003`
- `REQ-BROWSER-AUTH-004`
- `REQ-BROWSER-AUTH-005`
- `REQ-BROWSER-AUTH-006`
- `REQ-BROWSER-AUTH-007`
- `REQ-BROWSER-AUTH-008`
- `REQ-BROWSER-AUTH-009`
- `REQ-BROWSER-AUTH-010`
- `REQ-ACCOUNT-008`
- `REQ-WEB-001`
- `REQ-WEB-002`
- `REQ-WEB-007`

## Related diagrams

- [`docs/diagrams/initial-production-topology.md`](../diagrams/initial-production-topology.md)

## Related videos

- None.
