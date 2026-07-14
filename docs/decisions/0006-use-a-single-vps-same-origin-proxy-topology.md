# ADR-0006: Use a Single-VPS Same-Origin Proxy Topology

## Status
Accepted

## Context
GAM needs an initial production topology that a small team can understand, afford, reproduce, monitor, back up, and restore.

The browser frontend and backend API need a stable integration boundary. Hosting them on different origins would require production CORS and cross-site cookie behavior. Hosting them behind one public proxy lets the browser use one origin and relative `/api` URLs.

One VPS concentrates cost and operations, but it also concentrates failure. A host outage, compromise, resource exhaustion, or failed deployment can affect the proxy, frontend, backend, and database together. Provider ownership of the physical infrastructure does not by itself provide application backups, monitoring, or restoration.

No official GAM-controlled domain or proxy product has been selected.

## Decision
Use one provider-neutral VPS for the initial production proxy, static frontend assets, backend service, and database service.

Use one configured canonical public origin. The exact value is supplied through required production environment variable `GAM_PUBLIC_ORIGIN` after GAM controls a domain.

Use `/` for the static SPA and `/api` as the public API base. The proxy is GAM's public HTTP entry point and shall:

- redirect HTTP to HTTPS and terminate public TLS;
- serve the static SPA and apply fallback only to non-API routes;
- forward `/api/*` to the privately reachable backend;
- preserve trustworthy original public request information;
- apply the accepted public browser-delivery policy; and
- avoid logging credentials, session cookies, or security tokens.

The proxy product may be Caddy, Nginx, or another implementation that satisfies the requirements. Product selection and host-versus-container packaging are deferred.

Only the proxy receives public GAM application traffic. Backend and database application ports remain private. Restricted administrative access is a separate operations channel.

Accept the VPS as a single point of failure and do not claim high availability or zero-downtime deployment. Mitigate recoverable failure through the accepted 24-hour RPO/RTO, off-host encrypted backups, restoration drills, external monitoring, service alerts, immutable version pairs, and maintenance-window deployment.

## Alternatives considered

### Option 1: Cross-origin managed frontend and backend hosting
Pros:
- Components can scale or fail independently.
- Managed platforms may reduce some host administration.
- Static frontend delivery may use a CDN naturally.

Cons:
- Requires production CORS and cross-site authentication decisions.
- Splits logs, networking, deployment, and provider concerns.
- Managed services still require explicit backup and recovery verification.

### Option 2: Multiple self-managed hosts
Pros:
- Backend and database failures can be isolated from frontend delivery.
- Components can scale independently.
- A single host compromise has a smaller immediate blast radius.

Cons:
- Higher initial cost and operational complexity.
- Requires more networking, firewall, monitoring, and deployment coordination.
- Does not create high availability without replication and failover design.

### Option 3: One VPS without a public proxy
Pros:
- Fewer configured components.
- Backend can listen directly on a public port.

Cons:
- No single same-origin path router for SPA and API traffic.
- Exposes more application surface directly.
- Distributes TLS, static delivery, and public-header responsibilities into application services.

### Option 4: One VPS behind a same-origin proxy
Pros:
- Lowest initial infrastructure complexity among the considered complete topologies.
- One browser origin avoids production CORS.
- Backend and database ports remain private.
- Centralizes public TLS, static delivery, routing, and browser headers.

Cons:
- The VPS is a single point of failure and compromise.
- Resource exhaustion affects all components.
- The team owns host maintenance, backup, monitoring, and recovery unless verified managed services are added.

## Consequences

Positive consequences:
- The browser has one stable origin and public `/api` boundary.
- Frontend code can use relative API URLs in production and development.
- The proxy has a clear GAM workflow goal independent of product choice.
- The initial infrastructure is small enough to reproduce and learn operationally.

Negative consequences:
- Host failure can make the entire system unavailable.
- Planned maintenance may cause downtime.
- Backups, restoration, monitoring, firewalling, and capacity management are mandatory operational work.
- Moving to multiple hosts or managed services will require a future architecture decision and migration plan.

## Related requirements

- `REQ-WEB-001`
- `REQ-WEB-002`
- `REQ-WEB-003`
- `REQ-WEB-004`
- `REQ-WEB-005`
- `REQ-WEB-006`
- `REQ-WEB-007`
- `REQ-OPS-001`
- `REQ-OPS-002`
- `REQ-OPS-003`
- `REQ-OPS-004`
- `REQ-OPS-005`
- `REQ-OPS-006`
- `REQ-OPS-007`
- `REQ-OPS-008`
- `REQ-OPS-009`

## Related diagrams

- [`docs/diagrams/initial-production-topology.md`](../diagrams/initial-production-topology.md)

## Related videos

- None.
