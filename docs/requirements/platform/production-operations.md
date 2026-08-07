# Requirement: Production Operations

## Status
Accepted

## Context
GAM's initial production deployment favors a small, understandable operational footprint over high availability. The static frontend, proxy, backend, and database will run on one provider-neutral VPS.

This specification makes the accepted single-host risk measurable through recovery, backup, monitoring, deployment, and rollback requirements. It defines readiness conditions but does not declare that GAM is currently production-ready.

The [Production Backup and Recovery](production-backup-and-recovery.md) Requirement Specification specializes the backup boundary, retention, encryption, WORM, monitoring, audit, and restoration cadence in this specification.

## Ubiquitous Language

- `production-ready`: Satisfying every required initial production safeguard in this specification in addition to application release criteria.
- `Recovery Point Objective (RPO)`: The maximum accepted age of recoverable persisted data after an outage.
- `Recovery Time Objective (RTO)`: The target maximum time to restore service after a recoverable outage is identified.
- `restoration drill`: A documented exercise that restores a production-compatible backup into an isolated environment and verifies usable data and application access.
- `commissioning gate`: The temporary Caddy source-address restriction that prevents KVM 2 from receiving general production traffic before explicit launch approval.
- `rollback window`: The minimum period and release-count boundary during which the previous compatible frontend/backend pair and its immutable artifacts remain available.

## Functional requirements

### REQ-OPS-001: Initial single-VPS topology
The initial production deployment shall use one provider-neutral VPS for the proxy, static frontend assets, backend service, and database service.

High availability, multi-host replication, and independent component scaling shall not be required in the initial production phase.

The selected VPS provider shall not be assumed to manage operating-system updates, firewall configuration, certificates, backups, monitoring, or restoration unless a specific managed service is purchased, configured, and verified.

Rationale:
One VPS minimizes initial cost and operational complexity while making the accepted single point of failure explicit.

Valid examples:
- One host runs the proxy and private backend/database services with documented resource limits.
- Provider snapshots are treated as one optional recovery layer rather than an undocumented backup guarantee.

Invalid examples:
- The architecture claims high availability because the VPS provider owns the physical hardware.
- Provider marketing is treated as proof that backups or restoration are configured.

---

### REQ-OPS-002: Public and private network boundary
Only the proxy shall receive public GAM application traffic on ports `80` and `443`.

The backend and database shall communicate through a private host or container network and shall not expose public application ports.

Administrative access such as SSH shall be a separate, restricted operational channel and shall not be represented as part of the public GAM web surface.

Rationale:
The network boundary limits public attack surface and ensures all browser traffic passes through the accepted TLS, routing, and header policy.

Valid examples:
- The proxy reaches the backend through a private service address.
- The database accepts connections only from the private application network and approved local administration.

Invalid examples:
- The backend's application port is reachable directly from the internet.
- PostgreSQL is exposed publicly for deployment convenience.

---

### REQ-OPS-003: Initial recovery objectives
The initial production deployment shall have an RPO of 24 hours and an RTO of 24 hours.

At least daily automated database backups shall be completed successfully to meet the RPO.

These objectives shall be treated as recovery targets and shall not be represented as high availability or uninterrupted-service guarantees.

Rationale:
The selected targets are measurable and achievable for the initial team and topology without requiring replication or continuous archive infrastructure.

Valid examples:
- A recoverable outage restores data from a backup no older than 24 hours.
- The operations report distinguishes planned downtime and recovery objectives from uptime guarantees.

Invalid examples:
- Weekly backups are considered sufficient for a 24-hour RPO.
- A 24-hour RTO is advertised as zero downtime.

---

### REQ-OPS-004: Backup retention and isolation
Production database backups shall be automated, encrypted before leaving the VPS and while in transit and at rest, stored outside the VPS in Brazil, and retained as at least 30 rolling daily, 12 weekly, and 12 monthly recovery points.

Production recovery points shall receive the formal WORM guarantee, classifications, retain-until periods, data boundary, identity separation, monitoring, and audit required by the Production Backup and Recovery Requirement Specification.

Backup credentials shall be separate from ordinary application credentials. A failure to create, validate, encrypt, transfer, or lock a scheduled backup shall generate an alert outside the VPS.

Rationale:
Backups on the failed or compromised host do not adequately address the single-VPS failure domain. Separate credentials limit the impact of an application compromise.

Valid examples:
- Encrypted daily backups are stored in Brazilian external object storage with 30 daily, 12 weekly, and 12 monthly recovery points.
- A failed backup job notifies an independently hosted alerting channel.

Invalid examples:
- The only backup is a directory on the production VPS.
- The application database password also grants deletion of every external backup.

---

### REQ-OPS-005: Restoration readiness (superseded)
`REQ-OPS-005` is superseded by `REQ-BACKUP-010`, `REQ-BACKUP-011`, and `REQ-OPS-010`.

The historical rule required a successful restoration before production and at least quarterly afterward, together with versioned deployment configuration and separately recoverable secrets. The developer explicitly replaced the quarterly cadence with one pre-production restoration, annual scripted restoration, and restoration after material backup-system changes because quarterly manual work is disproportionate for a solo developer.

This historical quarterly rule shall not govern current production readiness.

---

### REQ-OPS-006: External availability monitoring
Better Stack shall check the production public HTTPS entry point from outside the VPS every five minutes by sending `GET /api/health`.

The check shall require HTTP `200 OK` and the exact healthy response defined by `REQ-OPS-011`. An alert shall be sent through Better Stack-hosted email and mobile push after three consecutive failed checks. The notification channel shall remain available when the VPS is unavailable.

The accepted AWS EventBridge, Lambda, and SNS monitor shall remain separate and authoritative for backup-object validation.

Rationale:
External checks detect total host, network, proxy, TLS, and application-entry-point failure without depending on the failed system to report itself.

Valid examples:
- Three failed five-minute checks notify an external alert channel.
- Better Stack detects that the public proxy can no longer reach a ready backend.

Invalid examples:
- Monitoring runs only as a container on the VPS it monitors.
- A Better Stack success is treated as evidence that the daily AWS recovery object is valid.

---

### REQ-OPS-007: Host and service alerting
Production shall use a metrics-only Better Stack collector on KVM 2 to monitor proxy, backend, database, CPU, memory, swap, filesystem, inode, network, container-health, and container-restart conditions. Broad application-log, request-body, and distributed-trace export shall remain disabled initially.

Filesystem usage shall generate a warning at 80 percent and a critical alert at 90 percent.

Better Stack shall warn when the canonical production origin's TLS certificate has 30 calendar days or less remaining. Automatic Caddy renewal shall not suppress this warning. An invalid, expired, hostname-mismatched, or otherwise unverifiable certificate shall alert immediately, and the expiry incident shall resolve only after Better Stack observes a valid replacement with more than 30 days remaining.

Unhealthy proxy, backend, or database services and impending TLS certificate expiry shall generate actionable Better Stack alerts. Backup failures shall continue to alert through the independent AWS monitoring path defined by the Production Backup and Recovery Requirement Specification.

Rationale:
A single host concentrates failure modes. Resource and service alerts provide time to intervene before disk exhaustion, certificate expiry, or container failure becomes a prolonged outage.

Valid examples:
- Disk usage crossing 80 percent warns before the 90 percent critical threshold.
- Certificate monitoring alerts even when automatic renewal was expected to succeed.

Invalid examples:
- Only CPU usage is monitored because all components share one host.
- Backup failures are discovered during a later restoration drill.
- Caddy's expected automatic renewal disables the external certificate-expiry alert.

---

### REQ-OPS-008: Controlled production deployment
Production deployment shall use immutable, identifiable frontend and backend versions selected as a compatible release pair.

Artifact publication shall not deploy automatically. Every production deployment and disruptive maintenance execution shall require explicit Developer approval and shall not be started by a recurring schedule.

After approval, the backend-owned automated workflow shall acquire an exclusive deployment lock, verify backup freshness when required, verify the selected artifacts, enable the maintenance response, apply the compatible migration and release sequence, execute health verification, record the result, and release the lock.

The deployment shall verify proxy routing, backend health, database connectivity, and `GET /api/health` before reporting success. When verification fails and the database remains compatible, automation shall restore the previous compatible application pair. When a migration makes that rollback unsafe, automation shall retain the maintenance response and stop for a Developer-selected forward fix or backup-based recovery.

A database-changing deployment shall confirm a recent successful backup before applying migrations.

Rationale:
Explicit approval and version selection keep independent pipelines from creating an accidental incompatible production pair or an untraceable deployment.

Valid examples:
- Deployment records the selected artifact versions and health-verification result.
- A migration is blocked when no recent successful backup exists.
- A database-incompatible failure remains in maintenance mode for an explicit recovery decision.

Invalid examples:
- Publishing a frontend artifact immediately overwrites production.
- Deployment reports success before the public route reaches a healthy backend.

---

### REQ-OPS-009: Maintenance-window rollback model
The reserved disruptive-maintenance window shall be Friday from `08:30` through `10:30 America/Sao_Paulo`. The window shall be used only for announced work and shall not cause recurring downtime by itself.

Planned disruptive maintenance shall be announced to coordinators at least 72 hours in advance. Emergency security or recovery work may occur outside the reserved window when notice is provided as soon as practicable.

Failed release verification shall trigger a compatible application rollback during the same maintenance window. The previously deployed compatible frontend/backend version pair shall remain available for application rollback for at least 14 days and through two subsequently verified production releases, whichever period is longer. Its backend digest, frontend archive, frontend checksum, release manifest, and fingerprinted frontend assets shall remain available for that complete rollback window.

Database migrations shall be treated as forward changes. Rollback planning shall use compatible migration sequencing and verified backups and shall not assume an automatic database downgrade.

Rationale:
One VPS does not provide duplicate runtime capacity. A truthful maintenance and recovery model is safer than claiming zero downtime without the supporting architecture.

Valid examples:
- A failed application release restores the previous compatible artifact pair during the maintenance window.
- A database migration includes a forward recovery plan and a verified pre-migration backup.
- A Friday morning deployment is announced by Tuesday morning and begins only after the Developer starts the workflow.

Invalid examples:
- Rollback depends on a mutable `latest` image.
- An arbitrary database downgrade is assumed to be safe.
- A cron schedule starts a production deployment automatically at `08:30` every Friday.

---

### REQ-OPS-010: Recoverable deployment configuration
Host, proxy, composition, deployment, rollback, backup, monitoring, and restoration configuration shall be versioned and reproducible through the accepted Ansible-only provisioning model.

Provider-account creation, billing, root MFA, initial client MFA enrollment, recovery-key custody, and alert-subscription confirmation may remain documented manual actions.

Production secrets shall use a separate recoverable secret-management process and shall not be committed, embedded in images, or included in database backups.

Rationale:
Database recovery alone cannot restore service when host configuration, release identity, or required secrets cannot be reconstructed.

Valid examples:
- Ansible recreates the Caddy, backend, PostgreSQL, backup, and monitoring configuration on a replacement Ubuntu 24.04 host.
- A recovery operator supplies secrets from approved external custody during provisioning.

Invalid examples:
- The only copy of a production secret exists on KVM 2.
- A manual firewall or Compose change is required but absent from versioned configuration and the runbook.

---

### REQ-OPS-011: Public production health contract
The sole public production readiness endpoint shall be unauthenticated `GET /api/health`.

When the application and its required database connectivity are ready, the endpoint shall return `200 OK`, `Content-Type: application/json`, and the single-property JSON response `{"status":"UP"}`. When the application can answer but readiness is unavailable, it shall return `503 Service Unavailable` and the single-property JSON response `{"status":"DOWN"}`.

Both responses shall include `Cache-Control: no-store`. The endpoint shall not expose component names, dependency addresses, environment values, versions, timestamps, uptime, diagnostics, stack traces, secrets, or failure causes.

The `GET` operation shall require neither authentication, permission authorization, nor CSRF proof. Other methods on the path shall receive the normal `405 Method Not Allowed` response. Network, proxy, or process failure may prevent the application from producing the `DOWN` body; external monitoring shall treat any response other than the accepted `200` response as failure.

The commissioning gate defined by `REQ-OPS-012` may temporarily intercept this path before the first approved launch. Once that gate is disabled, the health operation shall be publicly reachable without credentials.

Rationale:
A fixed, dependency-aware readiness contract lets external monitoring verify the public proxy-to-database path without disclosing the infrastructure model.

Valid examples:
- An unauthenticated `GET /api/health` returns `200` and `{"status":"UP"}` when the application and database are ready.
- A reachable backend with unavailable required database connectivity returns `503` and `{"status":"DOWN"}`.

Invalid examples:
- The public response lists PostgreSQL, Caddy, Hostinger, or application-version details.
- The endpoint requires an Account session or a monitoring credential after commissioning.

---

### REQ-OPS-012: Temporary commissioning gate
Fresh production provisioning shall default an Ansible-controlled Caddy commissioning gate to enabled before KVM 2 receives production traffic.

While enabled, only source addresses in the explicitly configured operator CIDR allowlist shall reach GAM routes. Every other HTTPS request, including `GET /api/health`, shall receive a static `503 Service Unavailable` response with `Cache-Control: no-store` and no application or infrastructure details. Caddy's HTTP-to-HTTPS redirect and certificate issuance or renewal shall remain active.

The gate shall not use HTTP Basic authentication or inject credentials into application requests. Disabling it shall require explicit Developer approval after the production-readiness checklist passes. The automated launch procedure shall record the approval and configuration transition and then verify `GET /api/health` from outside the VPS.

If first-launch verification fails, automation shall re-enable the gate before remediation. The gate is a temporary commissioning control rather than the production authentication model or a substitute for the separately controlled maintenance response.

Rationale:
KVM 2 is both the validation host and future production host. A reproducible deny-by-default proxy gate prevents accidental public commissioning without altering backend authentication behavior.

Valid examples:
- A configured operator address can rehearse the production route while another public address receives the static `503` response.
- Explicit launch approval disables the gate and immediately exercises the public health monitor.

Invalid examples:
- Changing an application environment label silently exposes the host.
- A shared Basic-authentication password is forwarded through the proxy during commissioning.

## Acceptance scenarios

```gherkin
Scenario: Production readiness requires off-host recovery
  Given GAM is preparing the first production deployment
  When production readiness is evaluated
  Then the accepted daily, weekly, and monthly encrypted recovery points exist outside the VPS in Brazil
  And the retained recovery points have the formal WORM guarantee
  And a successful pre-production restoration is documented
  And the 24-hour RPO and RTO are supported by the runbooks

Scenario: Detect a public outage independently
  Given the VPS public HTTPS entry point is unavailable
  When three consecutive external checks fail
  Then an alert is delivered through infrastructure outside the VPS

Scenario: Expose only minimal public readiness
  Given the commissioning gate is disabled
  And the application and database are ready
  When an unauthenticated client sends GET /api/health
  Then the response is 200 with only {"status":"UP"}
  And the response cannot be cached

Scenario: Keep an uncommissioned host closed
  Given fresh production provisioning has completed
  And the Developer has not approved launch
  When a public address outside the operator allowlist requests GAM
  Then Caddy returns the static commissioning response with status 503
  And the request does not reach the application

Scenario: Block unsafe database deployment
  Given a release contains a database migration
  And no recent successful backup is confirmed
  When production deployment is attempted
  Then migration does not begin

Scenario: Roll back a failed application pair
  Given a new compatible frontend/backend pair fails health verification
  When the deployment is rolled back
  Then the previous identifiable compatible pair is restored
  And database recovery follows the documented forward or backup-based plan
```

## Diagrams

* [Initial Production Topology](../../diagrams/initial-production-topology.md)
* [Production Release and Commissioning](../../diagrams/production-release-and-commissioning.md)

## Out of scope

* Declaring the current application production-ready.
* High availability, database replication, multi-host deployment, and zero-downtime rollout.
* Reopening the accepted Hostinger KVM 2, Ubuntu 24.04, Caddy, Ansible, GHCR, AWS São Paulo, or formal WORM decisions without new contradictory evidence.
* Defining tighter recovery objectives than the accepted initial 24-hour RPO and RTO.
* Treating the temporary commissioning gate as permanent user authentication or as the ordinary maintenance mechanism.

## Related ADRs

* [ADR-0006: Use a single-VPS same-origin proxy topology](../../decisions/0006-use-a-single-vps-same-origin-proxy-topology.md)
* [ADR-0005: Keep frontend and backend in separate repositories](../../decisions/0005-keep-frontend-and-backend-in-separate-repositories.md)
* [ADR-0024: Deploy production directly to Hostinger KVM 2](../../decisions/0024-deploy-production-directly-to-hostinger-kvm-2.md)
* [ADR-0025: Use AWS São Paulo for immutable encrypted production backups](../../decisions/0025-use-aws-sao-paulo-for-immutable-encrypted-production-backups.md)
* [ADR-0028: Complete the initial production commissioning and release contracts](../../decisions/0028-complete-initial-production-commissioning-and-release-contracts.md)

## Related requirements

* [Web Delivery and Frontend Contract](web-delivery-and-frontend-contract.md)
* [Production Backup and Recovery](production-backup-and-recovery.md)

## Related videos

* None.
