# ADR-0024: Deploy Production Directly to Hostinger KVM 2

## Status
Accepted

## Context
GAM needs an initial production platform that one developer can provision, operate, patch, deploy, back up, and recover without introducing multi-host orchestration.

ADR-0006 accepts a single provider-neutral VPS and same-origin proxy topology but intentionally deferred the provider, plan, operating system, proxy product, packaging, registry, and provisioning tool. Deployment ideas considered acquiring Hostinger KVM 1 as a disposable validation environment before moving to KVM 2. The client subsequently selected and confirmed KVM 2 directly, so a paid KVM 1 rehearsal host would add cost and operational work without changing the production target.

The backend build produces a JAR, but directly installing Java and managing that JAR on the host would create a second production runtime model. Terraform and Ansible together would divide a small provisioning surface between two tools.

## Decision
Use Hostinger KVM 2 as GAM's first and direct production VPS. Do not acquire KVM 1 as a disposable validation or staging environment.

Select a Hostinger location in Brazil and install a clean minimal Ubuntu Server 24.04 LTS image. Verify the plan's then-current vCPU, RAM, NVMe, transfer, region, renewal, backup, snapshot, and support terms at procurement and renewal rather than treating current marketing values as permanent architecture facts.

Run the production composition with Docker Engine and the Docker Compose plugin:

- Caddy runs as the only public application service on ports 80 and 443.
- Static versioned frontend files are mounted read-only and served by Caddy.
- The backend runs from a private GHCR OCI image selected by immutable digest.
- PostgreSQL 18 runs on the private composition network with persistent local storage.
- Backend and PostgreSQL ports are not published publicly.

Use Ansible as the only infrastructure-configuration automation for the initial deployment. The versioned Ansible content owns host hardening, operations users, Docker, firewall integration, directories, Compose and Caddy configuration, secret-file placement, resource controls, logs, backup and restore scripts, systemd timers, monitoring, deployment, rollback, and verification.

Bootstrap SSH access in two phases. The developer installs a dedicated production-administration public key through Hostinger and uses the initial `root` path only to create the locked-password `gamops` account, install the same public key, and configure its validated passwordless privilege escalation. Automation must prove a new `gamops` SSH connection and privileged execution before it enables the deny-by-default host firewall or disables root and password-based SSH access. A failed verification stops before hardening and preserves the initial root path and Hostinger browser terminal for recovery. Steady-state Ansible runs use `gamops`, not root.

Provisioning verification uses a successful real apply followed by a successful replay with no unexplained changes. Failure to inspect or enforce the firewall is fatal and cannot be converted into skipped configuration.

Use the same Ansible approach from the developer workstation to configure GAM-specific AWS backup resources where supported. Provider-account creation, billing, root MFA, initial Hostinger console actions, client MFA enrollment, and email-subscription confirmation remain documented manual actions.

Terraform is deferred. Hostinger- or AWS-specific APIs may be introduced through Ansible modules or narrowly scoped commands without creating a second infrastructure state model.

Production deploys the backend only as the tested OCI image from private GitHub Container Registry by immutable digest. Direct JAR deployment and mutable image references such as `latest` are not production paths. Publishing an artifact does not deploy it; deployment requires explicit developer approval of a compatible frontend/backend release pair.

Validation occurs on KVM 2 before it receives production traffic. Tests use production-like data without real client data and cover provisioning idempotence, public/private network boundaries, representative load, backup under load, deployment, rollback, monitoring, and the required pre-production restoration.

## Alternatives considered

### Option 1: Acquire KVM 1 and later move to KVM 2
Pros:
- Provides a disposable host for early infrastructure mistakes.
- Can compare capacity between plans.

Cons:
- Adds purchase, provisioning, migration, and cleanup work for a solo developer.
- Delays validation of the actual production plan.
- Was explicitly rejected after KVM 2 was selected and confirmed.

### Option 2: Deploy directly to KVM 2
Pros:
- Tests the exact production capacity and provider environment.
- Avoids a temporary subscription and migration.
- Provides more operational headroom for the JVM, PostgreSQL, backups, and deployments.

Cons:
- Validation and production preparation must be isolated carefully on the same purchased host.
- There is no disposable provider environment for destructive rehearsal.

### Option 3: Use Terraform for provider resources and Ansible for the host
Pros:
- Separates infrastructure-resource state from host configuration.
- Could help if the deployment later grows to many cloud resources.

Cons:
- Adds another state file, toolchain, credential path, and workflow for a small environment.
- Hostinger-specific creation has few accepted automated steps.
- Provides insufficient initial value to justify two provisioning systems.

### Option 4: Use only Ansible initially
Pros:
- One automation model covers the host and the small AWS backup surface.
- Idempotent configuration remains versioned and reviewable.
- Avoids Terraform state and coordination overhead.

Cons:
- Some provider/account operations remain manual.
- Ansible is less specialized than Terraform for large infrastructure graphs.

### Option 5: Deploy the JAR directly
Pros:
- Avoids a backend application container.
- Uses familiar JVM service management.

Cons:
- Requires Java installation, patching, and runtime matching on the VPS.
- Creates a different runtime from the tested OCI artifact.
- Adds host-level service, logging, resource, deployment, and rollback configuration.

### Option 6: Deploy an OCI image by digest from GHCR
Pros:
- Packages the tested Java runtime with the application.
- Supports immutable artifact identity and reproducible rollback.
- Keeps Java off the host and preserves provider portability.

Cons:
- Requires Docker and private-registry credentials.
- Image cleanup, scanning, and base-image updates remain operational work.

### Option 7: Harden SSH and the firewall in one initial connection
Pros:
- Uses one inventory identity and one provisioning phase.
- Requires fewer explicit access-verification steps.

Cons:
- The steady-state operations account may not exist when Ansible first connects.
- Missing keys or privilege escalation can become visible only after root access is disabled.
- A firewall or SSH mistake can make the new production host unreachable.

### Option 8: Separate root bootstrap from steady-state operations access
Pros:
- Proves the replacement administrative path before removing initial access.
- Makes the transition from provider-created root access to versioned GAM operations access explicit and testable.
- Preserves Hostinger console recovery when bootstrap verification fails.

Cons:
- Requires separate bootstrap and steady-state entry points.
- The passwordless privileged operations key becomes security-critical and must remain protected and recoverable.

## Consequences

Positive consequences:
- Production begins on the client-confirmed plan without a temporary KVM 1 migration.
- Ubuntu 24.04 LTS provides a predictable supported host baseline.
- Caddy, backend, PostgreSQL, and static frontend delivery share one reproducible Compose model.
- Immutable GHCR digests identify the exact backend artifact.
- Ansible is the single initial provisioning system.
- SSH and firewall hardening cannot precede proof of the replacement operations path.
- The design remains portable to a replacement VPS despite the Hostinger selection.

Negative consequences:
- KVM 2 is still a single point of failure and compromise.
- Validation mistakes occur on the future production host and require careful data separation or clean re-provisioning before launch.
- The developer owns patching, firewalling, Docker, Caddy, PostgreSQL, backups, monitoring, recovery, and capacity management.
- Hostinger console and account operations are not fully automated.
- Compromise of the dedicated operations private key grants passwordless privileged host access until that key is revoked.
- Adding substantial cloud infrastructure may justify a future Terraform decision.

## Related requirements
- `REQ-OPS-001`
- `REQ-OPS-002`
- `REQ-OPS-003`
- `REQ-OPS-014`
- `REQ-OPS-007`
- `REQ-OPS-008`
- `REQ-OPS-009`
- `REQ-OPS-010`
- `REQ-OPS-011`
- `REQ-OPS-012`
- `REQ-OPS-013`
- `REQ-WEB-013`

## Related ADRs
- [ADR-0005: Keep frontend and backend in separate repositories](0005-keep-frontend-and-backend-in-separate-repositories.md)
- [ADR-0006: Use a single-VPS same-origin proxy topology](0006-use-a-single-vps-same-origin-proxy-topology.md)
- [ADR-0025: Use AWS São Paulo for immutable encrypted production backups](0025-use-aws-sao-paulo-for-immutable-encrypted-production-backups.md)
- [ADR-0028: Complete the initial production commissioning and release contracts](0028-complete-initial-production-commissioning-and-release-contracts.md)

## Related diagrams
- [`docs/diagrams/initial-production-topology.md`](../diagrams/initial-production-topology.md)
- [`docs/diagrams/production-backup-and-recovery.md`](../diagrams/production-backup-and-recovery.md)

## Related videos
- None.
