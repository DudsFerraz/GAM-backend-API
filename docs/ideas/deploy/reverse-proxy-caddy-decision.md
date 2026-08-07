# Reverse Proxy Decision: Caddy

## Decision status

**Accepted.**

GAM will use **Caddy** as the initial production reverse proxy.

This file is a non-normative deployment idea for later deployment requirements and runbooks. The accepted Requirement Specifications remain the source of truth.

## Rationale

For GAM, the decision is not based on raw performance. Caddy, Nginx, and Traefik can all handle the expected traffic. The deciding factor is **operational simplicity and the probability of configuration mistakes**.

Caddy is preferred because it provides, in one component:

- Automatic TLS certificate issuance and renewal
- Automatic HTTP-to-HTTPS redirection
- Static frontend serving
- SPA fallback
- `/api/*` reverse proxying
- Safe forwarded-header defaults
- Structured access logs with sensitive-header redaction
- A compact and readable configuration

This directly matches GAM’s fixed single-VPS topology:

```text
Internet
   |
   v
Caddy
   |-- /api/*  -> private backend
   `-- /*      -> static React frontend
```

Only Caddy will expose public ports 80 and 443. The backend and PostgreSQL will remain private.

## Why not Nginx?

Nginx is mature and fully capable, but it normally requires more explicit configuration for:

- Certificate issuance and renewal
- Forwarded headers
- URI path preservation
- SPA fallback
- Sensitive log fields
- TLS settings

The additional control is not currently needed by GAM and increases the amount of security-sensitive configuration the team must maintain.

## Why not Traefik?

Traefik is strongest when many containers or services must be discovered and routed dynamically.

GAM has a small and fixed composition:

- One proxy
- One frontend
- One backend
- One PostgreSQL database

Its dynamic service-discovery model would add complexity without providing a meaningful initial benefit. It would also normally require a separate service to host the static frontend.

## Deployment model

Caddy will run as a container in the canonical Docker Compose composition.

Its configuration will be:

- Versioned
- Reviewed
- Validated before deployment
- Pinned to an explicit image version
- Tested during provisioning and recovery drills

Persistent storage will be provided for Caddy’s certificate and TLS state.

## Important configuration rules

The production configuration must ensure that:

- `/api/*` is forwarded without unintentionally removing the `/api` prefix.
- SPA fallback applies only to non-API routes.
- Only ports 80 and 443 are publicly exposed for application traffic.
- Backend and PostgreSQL ports are not published.
- Sensitive headers and cookies are not logged.
- Public client `X-Request-Id` values are stripped or overwritten before proxying to a backend configured in `TRUSTED_PROXY` mode.
- HSTS is enabled only after the official domain and HTTPS operation are verified.
- Better Stack monitors the canonical certificate externally, warns at 30 days remaining, and alerts immediately when the certificate is invalid, expired, hostname-mismatched, or unverifiable.
- The backend continues enforcing authentication, authorization, CSRF protection, and input validation.

Fresh production provisioning also enables an Ansible-controlled commissioning gate in Caddy. It admits only configured operator CIDRs and returns a static non-cacheable `503` to other HTTPS requests, including `/api/health`, until the Developer explicitly approves launch. The gate does not use HTTP Basic authentication. HTTP-to-HTTPS redirection and Caddy certificate automation remain active while the gate is enabled.

The future Caddy configuration and deployment tests should demonstrate the request-correlation boundary in [`REQ-WEB-012`](../../requirements/platform/web-delivery-and-frontend-contract.md#req-web-012-trusted-request-correlation-boundary) and [`REQ-ACTIVITY-007`](../../requirements/platform/activity-audit-log.md#req-activity-007-http-request-correlation-modes). This reminder does not define a competing header contract.

## Recommendation: run Caddy in a container

Containerizing it gives GAM:

* Versioned proxy configuration
* Reproducible deployment
* Consistent testing
* Simplified migration
* One canonical Compose composition
* No provider-specific installation procedure

Requirements:

* Only Caddy publishes host ports 80 and 443.
* Caddy’s data and configuration directories use persistent volumes.
* Backend and PostgreSQL expose no host ports.
* Static frontend files are mounted read-only.
* Caddy config is versioned and validated before deployment.
* Access logs exclude or redact sensitive headers and cookies.

Running the proxy directly on the host would slightly reduce dependency on Docker for the maintenance page and TLS boundary. That is defensible, but reproducibility is more valuable for GAM’s current priorities.


## Review triggers

This decision should be reviewed if:

- The team develops stronger operational expertise with another proxy.
- GAM adopts many independently deployed services.
- Dynamic service discovery becomes necessary.
- A specific Nginx or Traefik capability becomes a documented requirement.
- Caddy creates a material operational or compatibility limitation.

Until then, Caddy provides the best balance of simplicity, correctness, and maintainability for GAM’s initial production environment.
