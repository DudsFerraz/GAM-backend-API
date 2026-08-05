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
The production public HTTPS entry point shall be checked from outside the VPS at least every five minutes.

An alert shall be sent after three consecutive failed checks through a channel that remains available when the VPS is unavailable.

Any public health response shall expose only minimal availability status and shall not disclose database details, environment variables, dependency addresses, secrets, or application version details.

Rationale:
External checks detect total host, network, proxy, TLS, and application-entry-point failure without depending on the failed system to report itself.

Valid examples:
- Three failed five-minute checks notify an external alert channel.
- The public health response indicates availability without listing internal components.

Invalid examples:
- Monitoring runs only as a container on the VPS it monitors.
- The public health response returns database connection strings.

---

### REQ-OPS-007: Host and service alerting
Production shall monitor proxy, backend, database, CPU, memory, filesystem, backup-job, container-health, and certificate-expiry conditions.

Filesystem usage shall generate a warning at 80 percent and a critical alert at 90 percent.

Unhealthy proxy, backend, or database services; failed backups; and impending TLS certificate expiry shall generate actionable alerts through the independently available alerting channel.

Rationale:
A single host concentrates failure modes. Resource and service alerts provide time to intervene before disk exhaustion, certificate expiry, or container failure becomes a prolonged outage.

Valid examples:
- Disk usage crossing 80 percent warns before the 90 percent critical threshold.
- Certificate monitoring alerts even when automatic renewal was expected to succeed.

Invalid examples:
- Only CPU usage is monitored because all components share one host.
- Backup failures are discovered during a later restoration drill.

---

### REQ-OPS-008: Controlled production deployment
Production deployment shall use immutable, identifiable frontend and backend versions selected as a compatible release pair.

Artifact publication shall not deploy automatically. Initial production deployments shall require explicit Developer approval.

The deployment shall verify proxy routing, backend health, database connectivity, and a minimal end-to-end public health signal before reporting success.

A database-changing deployment shall confirm a recent successful backup before applying migrations.

Rationale:
Explicit approval and version selection keep independent pipelines from creating an accidental incompatible production pair or an untraceable deployment.

Valid examples:
- Deployment records the selected artifact versions and health-verification result.
- A migration is blocked when no recent successful backup exists.

Invalid examples:
- Publishing a frontend artifact immediately overwrites production.
- Deployment reports success before the public route reaches a healthy backend.

---

### REQ-OPS-009: Maintenance-window rollback model
Short, announced maintenance windows shall be acceptable for initial production deployments. Zero-downtime deployment shall be out of scope.

The previously deployed compatible frontend/backend version pair shall remain available for application rollback through the defined rollback window.

Database migrations shall be treated as forward changes. Rollback planning shall use compatible migration sequencing and verified backups and shall not assume an automatic database downgrade.

Rationale:
One VPS does not provide duplicate runtime capacity. A truthful maintenance and recovery model is safer than claiming zero downtime without the supporting architecture.

Valid examples:
- A failed application release restores the previous compatible artifact pair during the maintenance window.
- A database migration includes a forward recovery plan and a verified pre-migration backup.

Invalid examples:
- Rollback depends on a mutable `latest` image.
- An arbitrary database downgrade is assumed to be safe.

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

## Open questions

* Which external availability and host-monitoring products will complement the accepted AWS backup monitor?
* How far before certificate expiry shall the impending-expiry alert fire?
* What rollback-window duration and planned maintenance schedule will be adopted before the first production deployment?

## Out of scope

* Declaring the current application production-ready.
* High availability, database replication, multi-host deployment, and zero-downtime rollout.
* Reopening the accepted Hostinger KVM 2, Ubuntu 24.04, Caddy, Ansible, GHCR, AWS São Paulo, or formal WORM decisions without new contradictory evidence.
* Defining tighter recovery objectives than the accepted initial 24-hour RPO and RTO.

## Related ADRs

* [ADR-0006: Use a single-VPS same-origin proxy topology](../../decisions/0006-use-a-single-vps-same-origin-proxy-topology.md)
* [ADR-0005: Keep frontend and backend in separate repositories](../../decisions/0005-keep-frontend-and-backend-in-separate-repositories.md)
* [ADR-0024: Deploy production directly to Hostinger KVM 2](../../decisions/0024-deploy-production-directly-to-hostinger-kvm-2.md)
* [ADR-0025: Use AWS São Paulo for immutable encrypted production backups](../../decisions/0025-use-aws-sao-paulo-for-immutable-encrypted-production-backups.md)

## Related requirements

* [Web Delivery and Frontend Contract](web-delivery-and-frontend-contract.md)
* [Production Backup and Recovery](production-backup-and-recovery.md)

## Related videos

* None.
