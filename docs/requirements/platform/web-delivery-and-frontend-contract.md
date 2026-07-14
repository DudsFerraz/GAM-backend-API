# Requirement: Web Delivery and Frontend Contract

## Status
Accepted

## Context
GAM needs a stable public web boundary and a maintainable integration contract between independently versioned frontend and backend repositories.

The initial frontend is a static single-page application. The frontend and API share one browser origin, while a public proxy serves frontend assets and routes API traffic to a privately reachable backend. The proxy role is defined by GAM responsibilities rather than by a specific product such as Caddy or Nginx.

## Ubiquitous Language

- `API-relative path`: An endpoint path resolved against the public `/api` server base, such as `/auth/login` resolving to `/api/auth/login`.
- `compatible release pair`: One explicitly selected frontend artifact version and backend artifact version whose API contracts are known to work together.

## Functional requirements

### REQ-WEB-001: Same-origin public delivery
The initial production deployment shall serve the GAM frontend and API from one canonical public origin.

The frontend shall use `/` and non-API browser routes. The API shall use the public `/api` base path.

Cross-origin and cross-site production frontend deployments shall not be supported.

Rationale:
One browser origin avoids production CORS, simplifies cookie behavior, and gives the proxy one explicit browser security boundary.

Valid examples:
- `https://configured.example/` serves the SPA and `https://configured.example/api/accounts/me` serves the API.
- Frontend code calls relative `/api/*` URLs.

Invalid examples:
- The supported production frontend calls `https://api.other.example` directly from the browser.
- The frontend embeds a provider-specific backend hostname.

---

### REQ-WEB-002: Canonical public origin configuration
Production shall require exactly one `GAM_PUBLIC_ORIGIN` environment variable containing the canonical public origin.

The value shall contain an HTTPS scheme, host, and optional non-default port. It shall not contain a path, query, fragment, user information, or trailing slash.

Production startup shall fail clearly when the variable is missing, malformed, or uses an insecure scheme.

The backend shall use the value for public URL generation and origin validation. Deployment configuration shall use it to establish the proxy public host. The frontend shall continue using relative `/api/*` URLs and shall not embed the origin.

Rationale:
The official GAM domain is not yet known. One validated origin setting allows the domain to be selected later without changing the browser/API contract or duplicating configuration across frontend code.

Valid examples:
- `GAM_PUBLIC_ORIGIN=https://gam-controlled.example`
- A later domain change updates deployment configuration without rebuilding frontend URLs.

Invalid examples:
- `GAM_PUBLIC_ORIGIN=gam-controlled.example/api`
- Production silently defaults to `localhost`.

---

### REQ-WEB-003: Proxy request-routing goal
The proxy shall be the public entry point for GAM web traffic.

The proxy shall:

- redirect public HTTP requests to HTTPS;
- terminate public TLS;
- serve the static SPA for frontend routes;
- apply SPA fallback only to non-API routes;
- forward `/api/*` requests to the privately reachable backend;
- preserve the original public scheme, host, port, and request path through a documented trusted-forwarding contract; and
- avoid forwarding client-supplied trusted-proxy headers as authoritative values.

The backend shall trust forwarded public-request information only from the configured proxy boundary.

Rationale:
The proxy's GAM goal is to present one secure browser origin while separating frontend delivery from backend execution and keeping internal service addresses out of the public contract.

Valid examples:
- `/members/123` receives the SPA entry document while `/api/members/123` reaches the backend.
- Public URL generation sees the configured HTTPS origin even when the backend connection is private HTTP.

Invalid examples:
- `/api/auth/login` is handled by SPA fallback.
- A direct internet client can forge `X-Forwarded-Host` and influence generated URLs.

---

### REQ-WEB-004: Public API path convention
The public API base path shall be `/api` in production and in the supported frontend development workflow.

Requirement and OpenAPI path expressions such as `/auth/login` and `/accounts/{accountId}` shall be treated as API-relative unless explicitly identified as complete public paths.

The generated OpenAPI contract shall declare `/api` as its public server base.

Every emitted HTTP `Location` header shall contain a complete public absolute path beginning with `/api`. The proxy shall not be responsible for repairing an incorrect application `Location` header.

Rationale:
A stable base path lets the proxy route by path and keeps endpoint definitions independent from the public host. Complete `Location` paths remain directly followable by browser and API clients.

Valid examples:
- OpenAPI path `/accounts/{accountId}` is served publicly under `/api/accounts/{accountId}`.
- Account creation emits `Location: /api/accounts/{accountId}`.

Invalid examples:
- A response emits `Location: /accounts/{accountId}` and depends on the frontend to prepend `/api`.
- OpenAPI describes a different public base from the proxy.

---

### REQ-WEB-005: Static SPA delivery and caching
The initial frontend shall be produced as a versioned static artifact and served by the proxy.

Fingerprint-named static assets shall receive long-lived immutable caching. The SPA entry document shall be revalidated and shall not receive immutable caching.

`/api/*` responses shall not be handled by the static-file cache. API caching shall occur only when defined by the API contract.

A deployment shall publish referenced fingerprinted assets before switching the entry document to reference them. Previous fingerprinted assets shall remain available through the rollback window.

Rationale:
Static delivery avoids a production frontend runtime while cache separation prevents stale entry documents and missing assets during deployment or rollback.

Valid examples:
- `app.a1b2c3.js` is cached immutably while `index.html` is revalidated.
- A client with an older entry document can still load its fingerprinted assets during the rollback window.

Invalid examples:
- `index.html` is cached immutably for one year.
- The proxy caches authenticated API responses as static files.

---

### REQ-WEB-006: Proxy-owned TLS and browser-delivery policy
The proxy shall own the public TLS lifecycle and baseline browser-delivery security headers.

Certificate expiry shall be monitored independently of automatic issuance or renewal.

HSTS shall not be enabled until the official domain is controlled and HTTPS operation has been verified. Content Security Policy shall be defined with the implemented frontend asset and integration needs and shall not default to a generic permissive policy.

The proxy shall apply baseline MIME-sniffing and clickjacking protections and shall preserve backend security headers unless a documented proxy policy deliberately replaces them.

Proxy logs shall not record `Authorization`, `Cookie`, `Set-Cookie`, access-token, refresh-token, or CSRF-token values.

Rationale:
The proxy is the browser-facing security boundary, but domain-locking and frontend-specific policies must be introduced only when their real constraints are known.

Valid examples:
- Certificate renewal is automated and expiry is still monitored externally.
- CSP is reviewed against the actual frontend build before acceptance.

Invalid examples:
- HSTS is enabled for an unconfirmed domain.
- Access logs capture refresh-cookie values.

---

### REQ-WEB-007: Same-origin frontend development
The supported local frontend workflow shall use one browser-visible development origin and shall proxy relative `/api/*` calls to the local backend.

The development origin and port shall be configurable and shall not be tied to a specific frontend tool.

In an explicit development profile, `GAM_PUBLIC_ORIGIN` may be an HTTP loopback origin and refresh/CSRF cookies may use `Secure=false`. This exception shall be rejected outside the development profile and for non-loopback origins.

CORS shall not be required by the supported local browser workflow.

Rationale:
The development proxy reproduces the production browser boundary without requiring locally trusted HTTPS or a fixed Vite, Node.js, or other development-server port.

Valid examples:
- A frontend dev server on a configured loopback port forwards `/api` to the backend.
- Production rejects the development insecure-cookie setting.

Invalid examples:
- A deployed environment permits `Secure=false` because its profile was not validated.
- Browser development directly calls the backend port through credentialed CORS.

---

### REQ-WEB-008: Separate repository boundary
The GAM frontend and backend shall remain in separate repositories.

Migration to a monorepo shall not be part of the initial architecture. Introducing a monorepo or a third infrastructure repository shall require a future architecture decision based on demonstrated need.

The backend repository shall own shared API, browser-integration, deployment, and operations source-of-truth documents. The frontend repository shall own frontend source, build behavior, and frontend-only documentation and shall link to shared contracts rather than duplicate them.

Rationale:
Separate repositories preserve independent histories, pipelines, releases, and agent context while one explicit ownership boundary prevents conflicting shared documentation.

Valid examples:
- A frontend document links to the accepted browser-session requirement in the backend repository.
- A future infrastructure-repository proposal evaluates the coordination cost through an ADR.

Invalid examples:
- The repositories maintain competing copies of cookie or API requirements.
- A monorepo migration is performed without a new accepted decision.

---

### REQ-WEB-009: Backend-owned OpenAPI contract
The backend repository shall generate the authoritative machine-readable OpenAPI contract from the implemented API and shall version the exported artifact with the backend release.

The frontend shall generate API types from that artifact and shall identify the backend contract version it supports. Frontend code shall not maintain competing handwritten copies of backend DTO shapes as its API contract.

The backend release pipeline shall detect and report breaking OpenAPI changes before artifact publication.

Rationale:
A generated, versioned contract lets independent repositories align without manually maintaining two machine-readable sources of truth.

Valid examples:
- A backend release publishes its image and matching OpenAPI artifact under the same version.
- The frontend generates TypeScript types from the selected artifact.

Invalid examples:
- A manually edited OpenAPI file and generated controller contract compete as sources of truth.
- The frontend silently assumes compatibility with every backend release.

---

### REQ-WEB-010: Cross-repository compatibility policy
While GAM remains pre-production, coordinated frontend and backend changes may replace unreleased contracts directly without legacy compatibility layers.

After the first production release, contract-compatible releases may deploy independently. A breaking API change shall require an explicit coordinated deployment plan across both repositories. Compatibility shall be retained only for the required transition window and shall not become indefinite legacy support by default.

This requirement shall not itself declare GAM production-ready or override the current pre-production lifecycle guideline.

Rationale:
The policy respects the current prohibition on hypothetical compatibility code while ensuring real production clients are not broken accidentally after launch.

Valid examples:
- A pre-production API rename updates implementation, tests, generated OpenAPI, and frontend together without an alias.
- A post-launch breaking change records a coordinated rollout sequence.

Invalid examples:
- An unreleased route is retained forever solely because it once existed in code.
- A production breaking change deploys independently without a frontend compatibility plan.

---

### REQ-WEB-011: Version-pinned artifact and deployment ownership
The frontend pipeline shall build, verify, and publish a versioned static artifact. The backend pipeline shall build, verify, and publish a versioned backend image and its matching OpenAPI artifact.

Artifact publication shall not silently change production.

The backend repository shall own the canonical production composition, proxy template, database service definition, deployment runbook, backup and restore runbooks, and deployment workflow.

The deployment workflow shall select, record, and require explicit Developer approval for one compatible frontend/backend version pair. The previously deployed compatible pair shall remain identifiable for rollback.

Rationale:
Independent build pipelines preserve repository autonomy, while one controlled and reproducible deployment source prevents the VPS from becoming an undocumented integration environment.

Valid examples:
- Production records frontend `1.4.0` with backend `2.1.0` as the deployed pair.
- Publishing frontend `1.4.1` does not automatically replace production assets.

Invalid examples:
- Production consumes mutable `latest` artifacts.
- Deployment configuration exists only as manually edited files on the VPS.

## Acceptance scenarios

```gherkin
Scenario: Serve frontend and API from one origin
  Given GAM has a configured canonical public origin
  When a browser requests a frontend route
  Then the proxy serves the SPA
  When the browser requests /api/accounts/me
  Then the proxy forwards the request to the private backend

Scenario: Emit a followable resource Location
  Given the public API base is /api
  When the API creates an Account resource
  Then Location begins with /api
  And following Location reaches the created API resource rather than the SPA

Scenario: Develop without browser CORS
  Given the frontend development server is the browser-visible loopback origin
  When frontend code calls a relative /api route
  Then the development proxy forwards it to the backend
  And the browser does not perform a cross-origin API call

Scenario: Publish independently and deploy deliberately
  Given verified frontend and backend artifacts exist
  When production deployment is approved
  Then the deployment records the selected compatible versions
  And artifact publication alone has not changed production
```

## Diagrams

* [Initial Production Topology](../../diagrams/initial-production-topology.md)

## Open questions

* What official domain will GAM control and assign to `GAM_PUBLIC_ORIGIN`?
* Which proxy product and packaging model will implement the accepted proxy responsibilities?
* Which frontend build tool and development-server port will be used?
* Which exact OpenAPI generation, breaking-change detection, and TypeScript generation tools will be accepted when `docs/ideas/openapi.md` is promoted into official documentation?

## Out of scope

* Monorepo migration.
* A dedicated infrastructure repository.
* Cross-origin production frontend hosting.
* Server-side rendering, a production Node.js frontend server, or a backend-for-frontend.
* Selecting Caddy, Nginx, or another proxy product.
* Selecting the official domain.
* Defining the complete future OpenAPI documentation and toolchain policy.

## Related ADRs

* [ADR-0005: Keep frontend and backend in separate repositories](../../decisions/0005-keep-frontend-and-backend-in-separate-repositories.md)
* [ADR-0006: Use a single-VPS same-origin proxy topology](../../decisions/0006-use-a-single-vps-same-origin-proxy-topology.md)
* [ADR-0007: Use same-origin browser sessions with layered CSRF protection](../../decisions/0007-use-same-origin-browser-sessions-with-layered-csrf-protection.md)

## Related requirements

* [Browser Session and Frontend Integration](../authentication/browser-session-and-frontend-integration.md)
* [Production Operations](production-operations.md)

## Related ideas

* [`docs/ideas/openapi.md`](../../ideas/openapi.md) — non-authoritative planning material. Replace this reference with the future official OpenAPI documentation when that material is reviewed and accepted.

## Related videos

* None.
