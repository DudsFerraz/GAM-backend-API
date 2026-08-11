# ADR-0030: Remove the Public API Prefix at the Proxy Boundary

## Status
Accepted

## Context
Before this decision, accepted GAM documentation established one public origin,
`/api` as the public API base, API-relative endpoint paths, and `servers: /api`
in OpenAPI. It also required the proxy to forward `/api/*` and preserve original
public request information, but did not decide whether the forwarded request
target kept or removed the `/api` segment.

The distinction has architectural consequences:

- preserving `/api` requires the backend runtime to own the same prefix;
- removing `/api` keeps the public namespace at the proxy boundary;
- mixing the two models can send `/api/members` to a controller mapped only to
  `/members`; and
- including any path that repeats `/api` under OpenAPI server `/api` produces a
  doubled client path; the current health route also belongs outside OpenAPI
  under `REQ-OPENAPI-002`.

Current implementation is evidence of the ambiguity, not the source of truth.
Most controllers use API-relative routes, the health controller and Springdoc
routes currently include `/api`, and the production Caddy configuration
currently preserves the incoming path.

ADR-0006 remains the accepted source for the single-VPS same-origin topology.
This ADR clarifies its previously unspecified path-transformation boundary; it
does not replace the topology itself.

## Decision
Treat `/api` exclusively as the public API prefix owned by the browser-visible
proxy boundary.

The production proxy and supported frontend development proxy shall match
`/api` as a complete leading path segment and remove exactly one occurrence
before forwarding a request. They shall preserve the remaining path and query
string and shall never recursively remove a second `/api` segment.

The backend route space shall be API-relative:

```text
Public request          Backend request
/api/members            /members
/api/auth/login         /auth/login
/api/health             /health
/api/docs               /docs
/api/openapi.json       /openapi.json
```

Exact public `/api` and `/api/` requests remain within the API routing boundary
and are forwarded to backend `/`; they must not fall through to the SPA.

The trusted-forwarding contract shall distinguish the original public request
identity from the transformed backend request target. Trustworthy public
scheme, host, port, and original path information may be conveyed as documented
proxy metadata, but client-supplied forwarding metadata is not authoritative.
Preserving public request information does not mean preserving `/api` in the
upstream request target.

The OpenAPI contract shall declare server URL `/api`, and every operation path
included in that contract shall be API-relative and shall not begin with
`/api`. The public readiness route shall remain excluded from OpenAPI in
accordance with `REQ-OPENAPI-002`; it shall appear as neither `/health` nor
`/api/health` in the Paths Object. Public documentation remains reachable at
`/api/docs` and `/api/openapi.json` while the backend exposes the corresponding
relative documentation routes.

The backend remains responsible for emitting complete public `Location` paths
beginning with `/api`. The proxy shall not rewrite response `Location` headers.

Public monitoring shall continue using `/api/health` through the proxy. A
health check that addresses the backend directly shall use `/health`.

## Alternatives considered

### Option 1: Remove `/api` at the proxy boundary
Pros:
- Matches the accepted API-relative path model directly.
- Composes naturally with OpenAPI `servers: /api`.
- Keeps public deployment namespace out of backend controller mappings.
- Makes production and frontend development use the same browser-visible
  contract.
- Allows a future public-prefix change to remain primarily a boundary concern.

Cons:
- Every supported proxy must implement and verify the same one-segment rewrite.
- Backend-local documentation and health URLs differ from their public URLs.
- Trusted public-path metadata must be documented separately from the rewritten
  request target when the backend needs both.

### Option 2: Preserve `/api` and give the backend a `/api` context path
Pros:
- The proxy can forward the request target without rewriting it.
- Controllers may remain written with relative mappings under one framework
  context-path configuration.
- Public and backend-local paths have the same textual prefix.

Cons:
- Makes a public proxy namespace part of the backend runtime topology.
- Requires careful OpenAPI and Springdoc configuration to avoid duplicating the
  context path in `servers` or operation paths.
- Private health and documentation routes retain a public-delivery concern.
- A future public-prefix change requires coordinated backend runtime changes.

### Option 3: Put `/api` in every backend controller and documentation route
Pros:
- Makes the preserved proxy path match explicit application mappings.
- Does not require proxy rewriting.

Cons:
- Repeats a cross-cutting delivery prefix throughout application routes.
- Contradicts the accepted API-relative path convention.
- Creates broad, error-prone route churn.
- Still requires special care to prevent OpenAPI `/api` duplication.

### Option 4: Keep mixed route shapes or add compatibility aliases
Pros:
- Minimizes immediate edits to currently working exceptional routes.

Cons:
- Leaves routing behavior endpoint-specific and difficult to reason about.
- Permits doubled public paths and ambiguous generated-client behavior.
- Adds legacy aliases before the first production contract requires them.
- Prevents one deterministic proxy and contract rule from governing all API
  routes.

## Consequences

Positive consequences:
- Every public API URL is derived by one composition rule: public `/api` plus
  one API-relative backend path.
- Controller mappings and OpenAPI Paths Object keys share the same relative
  vocabulary.
- `/api/health` remains the sole public readiness URL and remains outside the
  frontend-facing OpenAPI contract.
- Production and supported frontend development have the same public-to-private
  transformation.
- Incorrect doubled prefixes remain visible instead of being silently repaired.

Negative consequences:
- Current proxy, health, Springdoc, and any other prefix-bearing backend routes
  must be aligned during implementation.
- Direct backend callers must use backend-relative routes rather than public
  routes.
- Verification must cover query preservation, the exact `/api` boundary,
  single-removal behavior, SPA exclusion, OpenAPI composition, health routing,
  and application-owned `Location` headers.
- Accepted requirements cannot rely on the previously ambiguous phrase
  "preserve the request path"; they must distinguish public request metadata
  from the upstream routing target.

## Related requirements

- [Public API Prefix Routing](../requirements/platform/public-api-prefix-routing.md)
- `REQ-WEB-003`
- `REQ-WEB-004`
- `REQ-WEB-007`
- `REQ-OPENAPI-002`
- `REQ-OPS-011`
- `REQ-WEB-014`
- `REQ-WEB-015`
- `REQ-OPENAPI-013`
- `REQ-OPS-015`

## Related diagrams

- [`docs/diagrams/initial-production-topology.md`](../diagrams/initial-production-topology.md)

## Related ADRs

- [ADR-0006: Use a Single-VPS Same-Origin Proxy Topology](0006-use-a-single-vps-same-origin-proxy-topology.md)
- [ADR-0008: Generate and Govern OpenAPI from Backend Code](0008-generate-and-govern-openapi-from-backend-code.md)
- [ADR-0028: Complete the Initial Production Commissioning and Release Contracts](0028-complete-initial-production-commissioning-and-release-contracts.md)

## Related videos

- None.
