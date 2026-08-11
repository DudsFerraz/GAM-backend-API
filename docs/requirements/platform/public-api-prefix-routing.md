# Requirement: Public API Prefix Routing

## Status
Accepted

## Context
GAM exposes the frontend and API through one public origin. The web contract
assigns `/api` as the public API base and treats controller and OpenAPI paths as
API-relative. Before this specification, the accepted documents did not state
whether a proxy must remove that public prefix before forwarding a request to
the backend.

That omission permitted incompatible runtime and contract compositions. A
proxy could preserve `/api` while ordinary backend routes remained
API-relative, and an OpenAPI path could repeat `/api` even though the contract
already declared `servers: /api`.

This specification establishes one explicit boundary for production and for
the supported same-origin frontend development workflow. The related accepted
web, OpenAPI, and operations requirements use this routing model.

## Ubiquitous Language

- `public API prefix`: The leading `/api` path segment exposed at the
  browser-visible origin.
- `backend-relative path`: The request path received by the backend after the
  proxy removes the public API prefix.

The existing `API-relative path` term remains defined by the accepted [Web
Delivery and Frontend Contract](web-delivery-and-frontend-contract.md).

## Functional requirements

### REQ-WEB-014: Remove exactly one public API prefix
The production proxy and the supported frontend development proxy shall treat
`/api` as a complete leading path segment and shall remove exactly one such
segment before forwarding the request to the backend.

The proxy shall preserve the path remainder and query string. It shall not
remove a second `/api` segment, match a partial segment such as `/apiary`, or
apply SPA fallback to a request whose first complete path segment is `/api`.

The exact public paths `/api` and `/api/` shall remain inside the API routing
boundary and shall be forwarded to backend path `/`. The backend may return its
ordinary response for an unmapped root path.

The same transformation shall apply in production and in the supported
same-origin frontend development workflow.

Rationale:
One explicit transformation keeps the public namespace at the proxy boundary
while controllers and backend-local operational routes remain independent from
that namespace. Removing exactly one segment avoids ambiguous recursive
rewriting and makes malformed doubled prefixes observable.

Valid examples:
- Public `/api/members` is forwarded to backend `/members`.
- Public `/api/auth/login?returnTo=%2Fmembers` is forwarded to backend
  `/auth/login?returnTo=%2Fmembers`.
- Public `/api/health` is forwarded to backend `/health`.
- Public `/api/docs` is forwarded to backend `/docs`.
- Public `/api/openapi.json` is forwarded to backend `/openapi.json`.
- Public `/api/api/health` is forwarded to backend `/api/health`; it is not
  rewritten a second time to `/health`.

Invalid examples:
- Public `/api/members` reaches backend `/api/members`.
- Public `/api/api/health` is silently repaired to backend `/health`.
- Public `/apiary` is treated as though it began with the `/api` path segment.
- A request under `/api/*` is handled by SPA fallback.

---

### REQ-WEB-015: Preserve public request identity without preserving the upstream path
The trusted-forwarding contract shall distinguish the original public request
identity from the request target delivered to the backend.

The proxy shall preserve trustworthy public scheme, host, port, and original
public path information through documented trusted metadata while forwarding
the backend-relative request target required by `REQ-WEB-014`. Client-supplied
trusted-proxy metadata shall not become authoritative.

The backend shall continue emitting every resource `Location` as a complete
public absolute path beginning with `/api`, as required by `REQ-WEB-004`. The
proxy shall neither prepend `/api` to nor otherwise repair an incorrect
application `Location` header.

Rationale:
Path transformation and public-request reconstruction are different concerns.
The backend may need trustworthy public context for URL generation, security,
correlation, or diagnostics without requiring its internal route table to
contain the public proxy prefix.

Valid examples:
- Backend route `/members/{memberId}` emits
  `Location: /api/members/{memberId}` when it creates a public resource.
- Trusted request information identifies the public request as
  `/api/members/{memberId}` while backend routing evaluates
  `/members/{memberId}`.

Invalid examples:
- The backend emits `Location: /members/{memberId}` and relies on the proxy to
  repair it.
- The proxy claims to preserve the public request path only because the
  rewritten backend request target is available.
- An Internet client can choose the trusted original public path metadata.

---

### REQ-OPENAPI-013: Compose each documented public OpenAPI path exactly once
The generated OpenAPI contract shall declare one server with URL `/api` and
shall express every included operation path as an API-relative path that does
not begin with `/api`.

The public readiness route shall remain excluded from the OpenAPI contract as
required by `REQ-OPENAPI-002`. It shall appear as neither `/health` nor
`/api/health` in the Paths Object. Private actuator, metrics, and other private
operational routes shall also remain excluded.

Swagger UI and the live machine-readable contract shall remain publicly
available at `/api/docs` and `/api/openapi.json`. Their backend-relative paths
shall be `/docs` and `/openapi.json` respectively.

Rationale:
OpenAPI composes a server URL with each Paths Object key. Keeping the public
prefix only in `servers` prevents generated clients from constructing doubled
paths, while excluding readiness preserves the accepted boundary between the
frontend API contract and operational routes.

Valid examples:
- `servers[0].url` is `/api` and the Member collection path is `/members`.
- The public readiness route exists operationally but is absent from the
  OpenAPI Paths Object.

Invalid examples:
- `servers[0].url` is `/api` and the Member collection path is `/api/members`.
- The OpenAPI Paths Object contains `/health` or `/api/health`.
- The contract omits the public server base and requires each operation to
  repeat `/api`.
- Swagger UI is public at `/api/docs` but the proxy forwards it to backend
  `/api/docs` under the prefix-removal model.

---

### REQ-OPS-015: Separate public and backend readiness paths
The public production readiness contract shall remain unauthenticated
`GET /api/health` with the response semantics defined by `REQ-OPS-011`.

The backend-relative readiness route shall be `GET /health`. A health check
that connects directly to the backend shall use `/health`; a health check that
validates the complete public proxy-to-backend path shall use `/api/health`.

No other readiness route shall become public as a consequence of this
distinction.

Rationale:
Public monitoring must validate the accepted public boundary, while private
container and backend checks should address the backend route actually exposed
inside the private application network.

Valid examples:
- Better Stack checks public `GET /api/health` through Caddy.
- A private backend check calls `GET /health` on the backend service.

Invalid examples:
- Better Stack bypasses Caddy and treats private `/health` as the public
  availability contract.
- A direct backend check calls `/api/health` after prefix removal is adopted.
- Both `/health` and `/api/health` are exposed as backend controller aliases.

## Acceptance scenarios

```gherkin
Scenario: Route a public API request to an API-relative backend route
  Given the browser-visible origin receives GET /api/members
  When the proxy forwards the request
  Then the backend receives GET /members
  And the request is not handled by SPA fallback

Scenario: Remove only one public prefix
  Given the browser-visible origin receives GET /api/api/health
  When the proxy forwards the request
  Then the backend receives GET /api/health
  And the proxy does not rewrite it to GET /health

Scenario: Compose an application URL from OpenAPI
  Given the generated contract declares server /api
  And the Member collection operation path is /members
  When a client resolves the operation against the server
  Then the resulting public path is /api/members
  And the resulting public path is not /api/api/members

Scenario: Exclude readiness from OpenAPI
  Given public readiness is available at /api/health
  When the OpenAPI contract is generated
  Then the Paths Object contains neither /health nor /api/health

Scenario: Keep public and private readiness checks distinct
  Given public readiness is available through the proxy
  When an external monitor checks readiness
  Then it requests /api/health
  When a private backend health check checks readiness directly
  Then it requests /health

Scenario: Keep application-owned Location public
  Given a backend operation creates a resource under /members
  When the operation returns Location
  Then Location begins with /api/members
  And the proxy does not rewrite the header
```

## Out of scope

* Selecting the concrete rewrite directive for Caddy or any frontend
  development-server product.
* Adding an API version segment such as `/v1`.
* Changing the response body, status, cache policy, or authorization semantics
  of the accepted public readiness contract.
* Documenting the readiness route in the OpenAPI contract.
* Asking the proxy to rewrite response `Location` headers.
* Providing compatibility aliases for the current unreleased mixed route
  shapes.

## Related ADRs

* [ADR-0006: Use a Single-VPS Same-Origin Proxy Topology](../../decisions/0006-use-a-single-vps-same-origin-proxy-topology.md)
* [ADR-0008: Generate and Govern OpenAPI from Backend Code](../../decisions/0008-generate-and-govern-openapi-from-backend-code.md)
* [ADR-0028: Complete the Initial Production Commissioning and Release Contracts](../../decisions/0028-complete-initial-production-commissioning-and-release-contracts.md)
* [ADR-0030: Remove the Public API Prefix at the Proxy Boundary](../../decisions/0030-remove-the-public-api-prefix-at-the-proxy-boundary.md)

## Related requirements

* [Web Delivery and Frontend Contract](web-delivery-and-frontend-contract.md)
* [OpenAPI and Frontend API Documentation](openapi-and-frontend-api-documentation.md)
* [Production Operations](production-operations.md)

## Related diagrams

* [Initial Production Topology](../../diagrams/initial-production-topology.md)

## Related videos

* None.
