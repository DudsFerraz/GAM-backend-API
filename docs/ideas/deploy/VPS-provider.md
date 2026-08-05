# VPS Provider Decision: Hostinger

This file is supporting procurement and validation guidance. [ADR-0024](../../decisions/0024-deploy-production-directly-to-hostinger-kvm-2.md) is the accepted source of truth for the direct KVM 2 decision.

**Hostinger VPS with KVM virtualization** is the selected VPS provider for GAM’s initial production infrastructure.

## Selected plan and adoption sequence

GAM will acquire Hostinger KVM 2 directly for the initial production environment. KVM 1 will not be purchased as a disposable validation environment.

Validation, deployment rehearsal, backup testing, monitoring verification, load testing, and the pre-production restoration will run on KVM 2 before it receives real production traffic. Test data and temporary restored production data must remain isolated and be removed before launch.

This direct adoption replaces the older two-stage KVM 1-to-KVM 2 proposal. The accepted decision is recorded in [ADR-0024](../../decisions/0024-deploy-production-directly-to-hostinger-kvm-2.md).

Hostinger currently documents KVM 2 with:

* 2 vCPUs
* 8 GB RAM
* 100 GB NVMe storage
* 8 TB network transfer

Hostinger currently documents a 300 MB/s I/O limit for KVM 2. ([Hostinger][1])

These specifications are procurement facts that must be verified again when the plan is purchased because provider plans, prices, limits, and commercial terms may change. ([Hostinger][1])

KVM 2 is selected for initial production because its resources provide operational headroom for running all of the following on one host:

* Operating system services
* Docker Engine and Docker Compose
* Reverse proxy and TLS handling
* Java backend and JVM
* PostgreSQL
* Monitoring and logging
* Database backups
* Deployments and database migrations
* Temporary files and restoration procedures

The selection of KVM 2 is a conservative capacity decision rather than a conclusion derived directly from the number of registered or monthly users. GAM is expected to have approximately 300 users per month, normally 1–8 concurrent users, and an occasional monthly peak near 100 concurrent users. The actual resource requirement depends on request frequency, database query cost, response size, background processing, file handling, and JVM behavior.

KVM 2 must therefore still be validated using production-like data and representative workload tests.

## Capacity rationale

### vCPU

KVM 2's currently documented two vCPUs must be shared by all CPU-dependent activities, including:

* Backend request processing
* JVM garbage collection
* PostgreSQL query execution
* TLS handling
* Backup compression
* Database migrations
* Container startup and image extraction
* Monitoring processes

Two vCPUs allow the backend and PostgreSQL to make progress concurrently and reduce interference between normal requests and operational work.

The initial plans use regular virtual CPUs rather than dedicated CPU infrastructure. Dedicated CPU capacity is not currently justified by GAM’s expected average workload. A future move to additional or dedicated CPU resources must be based on evidence such as sustained CPU saturation, high CPU-steal time, unstable response latency, or unacceptable interference from backups and maintenance tasks.

### RAM

KVM 2's currently documented 8 GB RAM gives room for:

* JVM heap and native JVM memory
* PostgreSQL memory and filesystem cache
* Backup operations
* Database migrations
* Temporary deployment resource usage
* Operating-system cache
* Traffic bursts
* Protection against out-of-memory termination

The production configuration must not assume that all available memory can be assigned to the JVM or PostgreSQL. Explicit limits and operational safety margins are required.

### Storage

KVM 2's currently documented 100 GB is shared by the entire host, including:

* Operating system
* Installed packages
* Docker images and layers
* PostgreSQL data and indexes
* Frontend release directories
* Backend artifacts
* Logs
* Temporary files
* Locally staged database dumps
* Deployment and restoration working space

Long-term backups must not be stored only on the VPS.

The production host should normally maintain at least 20–30% free disk space. Disk alerts must be generated before PostgreSQL or Docker approaches filesystem exhaustion.

The following initial thresholds are recommended:

* Warning at 70% disk utilization
* Urgent alert at 80%
* Operational intervention before 85%
* Production must not intentionally operate near full capacity

If GAM stores user-uploaded documents or media, their expected growth must be estimated separately. Application media must not silently consume the capacity reserved for PostgreSQL, deployments, logs, and recovery operations.

### Network

Hostinger currently advertises a 1 Gbps network connection and 8 TB of transfer for KVM 2. ([Hostinger][2])

These allowances are expected to exceed GAM’s initial requirements unless the application begins serving large files or other bandwidth-intensive content.

The network evaluation must focus on more than advertised bandwidth. Production validation must also consider:

* Latency for the expected Brazilian user base
* Routing stability
* Packet loss
* Public IPv4 behavior
* IPv6 behavior
* Excess-bandwidth rules
* Provider firewall behavior
* DDoS and abuse-response policies
* DNS and TLS reliability

The selected VPS region should be in Brazil when Hostinger makes a suitable Brazilian location available for the purchased plan.

## Repeatable provisioning

The VPS must not depend on undocumented manual configuration. The system must remain rebuildable because a host may need to be replaced after:

* Operating-system corruption
* Security compromise
* Accidental deletion
* Failed maintenance
* Disk failure
* Provider support intervention
* Migration to a different Hostinger VPS
* Future migration away from Hostinger

The provisioning model will be divided into two layers.

### Hostinger-specific provisioning

The Hostinger-specific layer may initially contain a small number of documented manual or API-assisted actions:

1. Purchase or create the VPS.
2. Select the plan and region.
3. Install the approved clean operating-system image.
4. Register the initial administrative SSH public key.
5. Configure the Hostinger-managed firewall.
6. Record the public IP address.
7. Configure DNS after the official domain is selected.
8. Enable or configure provider backup features where appropriate.

Hostinger advertises a VPS API that may later be used to automate applicable provider-level operations. ([Hostinger][2])

Terraform support is not required for the initial phase unless it provides clear value. The number of provider-specific steps should remain small and documented.

### Provider-independent host configuration

Host configuration must be automated with an idempotent provisioning tool such as Ansible.

The automated configuration should include:

* Administrative user creation
* SSH hardening
* Host firewall configuration
* Operating-system updates
* Automatic security-update policy
* Docker Engine installation
* Docker Compose installation
* Application directories and permissions
* Reverse-proxy configuration
* Log rotation
* Backup scripts and schedules
* Monitoring agents
* Deployment scripts
* Rollback scripts
* Restoration scripts
* Resource limits
* Production configuration templates

The application composition should remain portable and versioned. Hostinger-specific information must not be embedded in frontend URLs, backend artifacts, database schema, or application code.

## KVM 2 replacement or future plan change

Hostinger supports upgrades between eligible KVM plans. Any future KVM 2 replacement or upgrade may therefore use an in-place provider operation or a newly provisioned VPS. ([Hostinger][3])

However, the availability of an in-place upgrade must not be treated as a substitute for reproducible provisioning.

Before upgrading:

1. Confirm a recent successful PostgreSQL backup.
2. Upload the backup to encrypted off-host storage.
3. Verify the current deployment manifest.
4. Create a provider snapshot when appropriate.
5. Announce a maintenance window.
6. Confirm that administrative recovery access is available.
7. Record current CPU, RAM, disk, and application health.

After upgrading:

1. Verify the operating system and filesystem.
2. Verify Docker and all containers.
3. Verify HTTPS and certificate handling.
4. Verify frontend delivery.
5. Verify API routing.
6. Verify PostgreSQL connectivity.
7. Execute representative application workflows.
8. Confirm monitoring and backup operation.
9. Record the new plan and verification results.

The plan upgrade must be treated as a controlled infrastructure change with possible downtime.

## Validation-to-production transition

The KVM 2 host used for pre-production validation must not receive real production traffic merely because environment variables are changed.

Before launch, the accepted provisioning automation must produce and verify the complete production configuration. Temporary validation data and access must be removed. A clean operating-system reinstall is required only when the developer cannot prove that validation state and informal configuration have been removed.

The clean production preparation must ensure that the server does not retain:

* Development data
* Test user accounts
* Development secrets
* Unapproved SSH keys
* Temporary firewall exceptions
* Debug tools
* Test certificates
* Development logs
* Unverified images or artifacts
* Unnecessary packages
* Informal configuration changes

Reinstalling a Hostinger VPS operating system deletes the server’s existing data and may also delete an existing snapshot. A verified off-host backup is therefore required before any reinstall operation involving data that must be preserved. ([Hostinger][4])

Because GAM currently has no production data, validation should use generated production-like data and preserve the option to reinstall KVM 2 cleanly before the first production release when isolation cannot otherwise be demonstrated.

## Hostinger backups and snapshots

Hostinger’s VPS backup and snapshot facilities are accepted as a secondary recovery layer.

Hostinger currently provides management of automatic backups and manually created snapshots through hPanel. A snapshot captures the VPS state and can be used to restore the server to that point. ([Hostinger][5])

Provider backups and snapshots may be used for:

* Recovery from an unsuccessful operating-system change
* Recovery from host-level configuration damage
* A recovery point before a risky deployment
* A recovery point before a plan upgrade
* Rapid restoration of the whole VPS

They do not replace GAM’s required primary backup strategy.

Before production, GAM must independently implement:

* Automated PostgreSQL backups
* Client-side backup encryption
* Off-host storage outside the VPS
* Documented retention
* Backup-age monitoring
* Failure alerts
* The isolated pre-production restoration and accepted annual restoration procedure
* Verification of restored application access and data

A Hostinger backup or snapshot must not be considered valid solely because it appears in hPanel. Its restoration process must be tested before production.

## Provider responsibilities and GAM responsibilities

Hostinger is responsible for the purchased VPS infrastructure according to its applicable service terms.

The GAM team remains responsible for:

* Operating-system configuration
* Operating-system patching
* SSH security
* Firewall rules
* Docker and container security
* Reverse-proxy configuration
* TLS behavior and monitoring
* PostgreSQL administration
* Application deployment
* Production secrets
* Database backups
* Off-host backup storage
* Restoration testing
* Monitoring
* Alert response
* Capacity management
* Incident response
* Compromise recovery
* Release rollback

The VPS must be treated as self-managed infrastructure. Provider tooling, including management assistance or AI-based features, does not transfer operational responsibility from the GAM team unless a specific managed service explicitly provides that responsibility and has been evaluated and accepted.

## Validation criteria

KVM 2 must be tested with production-like data and representative user behavior before production traffic is accepted.

The test should include:

* Gradual ramp-up to 100 active users
* Realistic pauses between user actions
* Common read operations
* Common write operations
* Authentication and token refresh
* Coordinator workflows
* Searches and filters
* Relevant exports or reports
* A database backup during application load
* Backend restart behavior
* Deployment and rollback
* Database restoration in an isolated environment

At minimum, the test must measure:

* CPU utilization
* CPU-steal time where available
* RAM utilization
* Swap use
* Out-of-memory events
* Disk utilization
* Disk I/O wait
* PostgreSQL connections
* Query latency
* JVM heap and garbage collection
* API response latency
* HTTP error rate
* Container restarts
* Backup duration
* Application behavior during backup

The KVM 2 selection is accepted. Measurements determine safe resource limits and whether a future upgrade is needed; they do not reopen KVM 1 as a temporary environment.

The project must avoid assuming that a particular plan is sufficient merely because its advertised resources appear large relative to the number of monthly users.

## Procurement verification

Immediately before purchasing or renewing a Hostinger VPS, the following facts must be verified against the current checkout terms and provider documentation:

* vCPU allocation and applicable CPU limits
* RAM
* NVMe capacity
* I/O limits
* Included network transfer
* Region availability
* Public IPv4 inclusion
* IPv6 support
* Initial contract price
* Total prepaid amount
* Renewal price
* Taxes and payment terms
* Upgrade restrictions
* Downgrade restrictions
* Cancellation and refund terms
* Backup frequency
* Backup retention
* Snapshot limitations
* Restore behavior
* Firewall capabilities
* API capabilities
* Service-level agreement
* Support channels
* Abuse and account-suspension policies

Promotional prices and current plan specifications must not be recorded as permanent architectural assumptions.

## Final decision summary

Hostinger KVM VPS is accepted as GAM’s initial infrastructure provider.

The adopted sequence is:

1. Acquire Hostinger KVM 2 directly in a Brazilian location with Ubuntu Server 24.04 LTS.
2. Apply the accepted Ansible-only provisioning model.
3. Use generated production-like load and recovery testing on KVM 2 before launch.
4. Remove all validation data and informal access before accepting real production traffic.
5. Keep all host configuration, application composition, deployment procedures, and recovery procedures versioned and reproducible.
6. Treat Hostinger backups and snapshots as supplementary recovery mechanisms.
7. Maintain the accepted AWS São Paulo encrypted WORM PostgreSQL backups and restoration procedure.
8. Preserve the ability to rebuild the complete system and migrate away from Hostinger if required.

This can serve as the provider-decision section in the backend-owned operations or production architecture documentation.

[1]: https://www.hostinger.com/br/support/6976044-parametros-e-limites-dos-planos-de-hospedagem-na-hostinger/?utm_source=chatgpt.com "Parâmetros e Limites dos Planos de Hospedagem na ..."
[2]: https://www.hostinger.com/br/servidor-vps?utm_source=chatgpt.com "Hospedagem VPS | VPS KVM gerenciado por IA com até ..."
[3]: https://www.hostinger.com/br/support/1583229-como-fazer-o-upgrade-do-seu-servidor-vps-na-hostinger/?utm_source=chatgpt.com "Como Fazer o Upgrade do Seu Servidor VPS na Hostinger"
[4]: https://www.hostinger.com/support/4965922-how-to-change-the-operating-system-of-your-vps-at-hostinger/?utm_source=chatgpt.com "How to change the operating system of your VPS at ..."
[5]: https://www.hostinger.com/br/support/1583232-como-fazer-backup-ou-restaurar-um-servidor-vps-hostinger/?utm_source=chatgpt.com "Como fazer backup ou restaurar um VPS na Hostinger"
