# Requirement: Cross-Site Refresh Cookie Compatibility

## Status
Superseded

## Context
This specification preserves the historical cross-site-compatible cookie and CSRF requirements formerly identified as `REQ-AUTH-012` and `REQ-AUTH-013` in [Authentication and Account Registration](authentication-and-registration.md).

The requirements were accepted while the frontend/backend deployment topology was undecided. They were superseded after GAM selected one same-origin browser workflow. They are retained so stable requirement IDs are not reused or silently redefined.

Current behavior is defined by [Browser Session and Frontend Integration](browser-session-and-frontend-integration.md) and ADR-0007.

## Ubiquitous Language

* None.

## Superseded functional requirements

### REQ-AUTH-012: Cross-site-compatible refresh-cookie contract
The system was required to use a single refresh-cookie mechanism compatible with same-site and cross-site frontend/backend deployments.

The `refreshToken` cookie was required to be `HttpOnly`, `Secure`, `SameSite=None`, and `Path=/`.

The system was required to enforce strict CORS allowlisting for credentialed browser requests.

Superseded by:
- `REQ-BROWSER-AUTH-001`
- `REQ-BROWSER-AUTH-002`
- `REQ-BROWSER-AUTH-010`

---

### REQ-AUTH-013: CSRF protection for cross-site cookie-authenticated auth endpoints
The system was required to enforce stateless CSRF protection for auth endpoints that consumed or set the cross-site refresh-token cookie.

Superseded by:
- `REQ-BROWSER-AUTH-003`
- `REQ-BROWSER-AUTH-004`

## Acceptance scenarios

* Superseded; see the replacement requirements.

## Diagrams

* None.

## Open questions

* None.

## Out of scope

* Current behavior.

## Related ADRs

* [ADR-0001: Use Cross-Site-Compatible Refresh Cookies with CSRF Protection](../../decisions/0001-use-cross-site-compatible-refresh-cookies-with-csrf-protection.md) — superseded.
* [ADR-0007: Use same-origin browser sessions with layered CSRF protection](../../decisions/0007-use-same-origin-browser-sessions-with-layered-csrf-protection.md)

## Related videos

* None.
