# ADR-0028: Complete the Initial Production Commissioning and Release Contracts

## Status
Accepted

The external-monitoring subsection is superseded by [ADR-0029](0029-align-better-stack-monitoring-with-provider-supported-contracts.md). All other decisions in this ADR remain accepted.

## Context
ADR-0005 assigns cross-repository deployment ownership to the backend repository. ADR-0006 establishes the single-VPS same-origin topology, ADR-0024 selects Hostinger KVM 2, Ubuntu 24.04, containerized Caddy, private GHCR, and Ansible, and ADR-0025 defines the independent AWS backup system.

Six implementation contracts remained open after those decisions: the public health operation, protection of KVM 2 during commissioning, external availability and host monitoring, the certificate-expiry threshold, the maintenance and rollback windows, and the transfer of a static frontend artifact from its repository into the backend-owned deployment workflow.

KVM 2 is both the production-like validation host and the future production host. The final contracts must therefore prevent accidental public launch, preserve human custody over disruptive changes, and remain reproducible without adding another infrastructure system or storing cross-repository credentials on the VPS.

## Decision

### Public health operation
Expose unauthenticated `GET /api/health` as the sole public readiness operation. Return only `{"status":"UP"}` with `200 OK` when the application and required database connectivity are ready, and only `{"status":"DOWN"}` with `503 Service Unavailable` when the application can answer but is not ready. Make both responses non-cacheable and expose no component or diagnostic details.

Keep container liveness private. External monitoring treats any public result other than the accepted `200` response as failure.

### Temporary commissioning gate
Provision Caddy with an Ansible-controlled commissioning gate enabled by default. Permit only configured operator CIDRs while it is enabled and return a static non-cacheable `503` to every other HTTPS request. Keep HTTP-to-HTTPS redirect and certificate automation active.

Do not use HTTP Basic authentication for commissioning. Explicit Developer approval after the readiness checklist is required to disable the gate. Record that transition, verify the health operation externally, and re-enable the gate when first-launch verification fails.

### External monitoring
This subsection is retained as historical context and is superseded by ADR-0029.

Use Better Stack for external availability, TLS, and host monitoring. Check `GET /api/health` every five minutes and alert through Better Stack-hosted email and mobile push after three consecutive failures.

Run a metrics-only Better Stack collector on KVM 2 for host, filesystem, network, container, proxy, backend, and PostgreSQL signals. Do not enable broad log, request-body, or distributed-trace export initially. Keep the AWS EventBridge, Lambda, and SNS path separate and authoritative for backup-object monitoring.

Warn when the canonical production certificate has 30 calendar days or less remaining. Alert immediately for an invalid, expired, hostname-mismatched, or unverifiable certificate. Resolve the expiry incident only after an externally observed replacement has more than 30 days remaining.

### Human-approved automated maintenance
Reserve Friday `08:30–10:30 America/Sao_Paulo` for announced disruptive maintenance. This is an allowed window, not a recurring deployment schedule. Planned disruption requires at least 72 hours' notice; emergency security or recovery work may occur outside the window with notice as soon as practicable.

The Developer selects the compatible pair and explicitly starts the backend-owned workflow. Automation performs locking, prerequisites, artifact verification, the maintenance response, migrations, rollout, health checks, recording, and a database-compatible application rollback. A database-incompatible failure remains in maintenance mode for a human forward-fix or restoration decision.

Keep the previous compatible pair for at least 14 days and through two subsequently verified production releases, whichever is longer. Retain its backend digest, frontend archive and checksum, manifest, and fingerprinted assets throughout that window.

### Cross-repository frontend artifact interface
Publish the static frontend as `gam-frontend-<tag>.tar.gz` with sidecar `gam-frontend-<tag>.tar.gz.sha256` on a published, non-prerelease, immutable GitHub Release. The sidecar contains one lowercase SHA-256 and the exact archive filename in standard `sha256sum` form.

The backend-owned deployment manifest pins the frontend repository, tag, filename, and SHA-256. The deployment workflow downloads the exact release assets using authenticated read-only GitHub access outside KVM 2. It verifies release immutability, the sidecar, the manifest digest, the computed digest, and safe archive structure before Ansible transfers the artifact. It never selects `latest`, a branch archive, or an expiring workflow artifact.

Record the release commit and artifact coordinates with the deployed compatible pair and retain them for rollback.

## Alternatives considered

### Option 1: Expose framework health details
Pros:
- Requires less response adaptation when a framework health endpoint already exists.
- Can aid interactive diagnosis.

Cons:
- Exposes internal components and deployment details publicly.
- Couples the public contract to framework-specific output.

### Option 2: Use HTTP Basic authentication for commissioning
Pros:
- Works for operators without stable source addresses.
- Can be configured entirely at the proxy.

Cons:
- Adds a shared public credential and can interfere with application `Authorization` behavior.
- Requires special credential handling in browser and monitoring flows.

### Option 3: Use a Caddy CIDR commissioning gate
Pros:
- Is deny-by-default and does not alter application authentication headers.
- Is reproducible through the already accepted Ansible and Caddy configuration.

Cons:
- Operator address changes require an allowlist update.
- General external monitoring cannot exercise the route until launch or an explicit probe address is allowed.

### Option 4: Split availability and host monitoring across providers
Pros:
- Each provider can be selected for a narrower specialty.
- Reduces reliance on one monitoring vendor.

Cons:
- Adds credentials, alert channels, billing surfaces, and runbook coordination for one VPS.
- Provides little initial benefit while the independent AWS backup monitor already supplies a separate failure domain.

### Option 5: Use one Better Stack monitoring provider
Pros:
- Covers external HTTP, TLS, alerting, and host telemetry in one external service.
- Keeps the initial operational surface manageable for one Developer.

Cons:
- Availability and host telemetry share one vendor.
- An on-host collector is still unable to send metrics during total host failure, so the external health check remains necessary.

### Option 6: Transfer an expiring workflow artifact or mutable latest release
Pros:
- Requires little release-management configuration.
- Makes the most recent build easy to discover.

Cons:
- Does not provide durable rollback identity.
- Can change or expire independently of the backend deployment manifest.

### Option 7: Transfer an immutable GitHub Release asset pinned by SHA-256
Pros:
- Gives separate repositories a durable, inspectable artifact interface.
- Supports exact rollback and detects accidental or malicious byte replacement.
- Keeps frontend-repository credentials off the VPS.

Cons:
- Requires immutable-release configuration and a cross-repository read credential at the deployment origin.
- Requires release naming, checksum, and archive-safety validation.

## Consequences

Positive consequences:
- Agent T can derive exact public health, commissioning, monitoring, maintenance, and artifact-verification tests without inventing policy.
- KVM 2 cannot become public merely because application configuration changes.
- Disruptive work remains explicitly approved while routine steps are reproducible and automated.
- The external monitor and AWS backup monitor remain independent of the single VPS and of each other's validation purpose.
- Frontend rollback does not depend on mutable or expiring cross-repository artifacts.

Negative consequences:
- Operator CIDR changes can interrupt commissioning access.
- Better Stack becomes an additional external account, credential, and operational dependency.
- The Friday morning window requires coordination and may still interrupt active users.
- Immutable frontend releases cannot be repaired in place; a bad asset requires a new release tag.
- Database-incompatible failures still require a human recovery decision.

## Related requirements
- `REQ-WEB-011`
- `REQ-WEB-013`
- `REQ-OPS-006`
- `REQ-OPS-014`
- `REQ-OPS-007`
- `REQ-OPS-008`
- `REQ-OPS-009`
- `REQ-OPS-010`
- `REQ-OPS-011`
- `REQ-OPS-012`

## Related ADRs
- [ADR-0005: Keep frontend and backend in separate repositories](0005-keep-frontend-and-backend-in-separate-repositories.md)
- [ADR-0006: Use a single-VPS same-origin proxy topology](0006-use-a-single-vps-same-origin-proxy-topology.md)
- [ADR-0024: Deploy production directly to Hostinger KVM 2](0024-deploy-production-directly-to-hostinger-kvm-2.md)
- [ADR-0025: Use AWS São Paulo for immutable encrypted production backups](0025-use-aws-sao-paulo-for-immutable-encrypted-production-backups.md)
- [ADR-0029: Align Better Stack monitoring with provider-supported contracts](0029-align-better-stack-monitoring-with-provider-supported-contracts.md)

## Related diagrams
- [`docs/diagrams/initial-production-topology.md`](../diagrams/initial-production-topology.md)
- [`docs/diagrams/production-release-and-commissioning.md`](../diagrams/production-release-and-commissioning.md)

## References
- [Better Stack monitoring](https://betterstack.com/docs/uptime/monitoring-start/)
- [Better Stack SSL certificate checks](https://betterstack.com/docs/uptime/ssl-certificate-checks/)
- [GitHub immutable releases](https://docs.github.com/en/enterprise-cloud@latest/code-security/concepts/supply-chain-security/immutable-releases)
- [GitHub CLI release download](https://cli.github.com/manual/gh_release_download)

## Related videos
- None.
