# Initial Deployment Ideas

This file is a non-normative collection of ideas for later deployment requirements and runbooks. Accepted Requirement Specifications remain the source of truth.

Current accepted deployment decisions are documented in [ADR-0024](../../decisions/0024-deploy-production-directly-to-hostinger-kvm-2.md), [ADR-0025](../../decisions/0025-use-aws-sao-paulo-for-immutable-encrypted-production-backups.md), and the [Production Backup and Recovery Requirement Specification](../../requirements/platform/production-backup-and-recovery.md). Where older ideas in this file conflict with those artifacts, the accepted artifacts win.

The priority should be **reproducibility, recovery and security**, not high availability.

---

# 1. Decisions already accepted

These should not be reopened during provider selection unless new evidence creates a direct contradiction:

* One VPS hosts proxy, frontend, backend and PostgreSQL.
* The VPS is an accepted single point of failure and compromise.
* High availability and zero-downtime deployment are out of scope.
* Browser traffic uses one canonical HTTPS origin.
* Only ports 80 and 443 receive public application traffic.
* The backend and database remain private.
* Frontend and backend artifacts are independently published and explicitly paired.
* Production never deploys `latest`.
* Planned deployment downtime is acceptable.
* RPO and RTO are both 24 hours.
* Provider snapshots are supplementary, not the primary backup mechanism.
* Production CORS and cross-origin frontend/API deployment are unsupported.
* Hostinger KVM 2 in Brazil is the direct production target; KVM 1 will not be acquired.
* Ubuntu Server 24.04 LTS is the host operating system.
* Caddy runs in the canonical Docker Compose composition.
* The backend is pulled from private GHCR by immutable OCI digest.
* Ansible is the only initial provisioning automation; Terraform is deferred.
* AWS S3 in `sa-east-1` is the selected off-host backup destination.
* Production recovery points use Compliance-mode Object Lock as a formal WORM guarantee.

Those decisions form a coherent initial-production model.

## One contradiction to correct

The statement that production requires “exactly one environment variable,” `GAM_PUBLIC_ORIGIN`, cannot literally apply to all production configuration.

The application or infrastructure will also need values such as:

* PostgreSQL password
* JWT or token-signing secret
* CSRF-related application secrets, where applicable
* Backup-storage credentials
* Monitoring credentials
* Possibly SMTP or third-party service credentials

A better requirement is:

> `GAM_PUBLIC_ORIGIN` is the only environment variable defining GAM’s public browser origin. Other production configuration and secrets may exist, but they must not introduce an alternative frontend or API origin.

Secrets can also be mounted as files instead of environment variables.

---

# 7. Operating system

Use **Ubuntu Server 24.04 LTS**.

Ubuntu 24.04 receives standard security maintenance through May 2029, providing a long, predictable maintenance period.

Reasons for selecting it:

* Mature Docker support
* Extensive operational documentation
* Broad provider availability
* Familiar package and security-update tooling
* Easier recruitment and troubleshooting than a niche distribution
* Long support window

Avoid using a control-panel-oriented OS image containing preinstalled Webmin, cPanel or unrelated software. Start from a minimal clean image.

---

# 8. Software installed on the host

Keep host software minimal:

* Docker Engine
* Docker Compose plugin
* OpenSSH server
* `unattended-upgrades`
* `fail2ban` only when SSH is exposed to restricted Internet addresses; it is not a replacement for firewall restrictions
* `curl`
* `jq`
* `rsync`
* PostgreSQL client backup tools
* `age`
* AWS CLI or the approved S3 upload client
* Monitoring agent
* Basic troubleshooting utilities
* Time synchronization
* Optional Tailscale or WireGuard

Do not install Java, Node.js or PostgreSQL directly on the host when they are deployed through containers.

Docker’s networking rules require special care: published container ports can bypass an incorrectly configured host firewall. Docker recommends applying custom restrictions through the `DOCKER-USER` chain and using a supported iptables backend. ([Docker Documentation][6])

---

# 11. Static frontend release model

Use versioned directories:

```text
/srv/gam/frontend/releases/1.4.0/
/srv/gam/frontend/releases/1.5.0/
/srv/gam/frontend/current -> releases/1.5.0/
```

Deployment should:

1. Download and verify the frontend artifact.
2. Extract it into a new versioned directory.
3. Verify all referenced fingerprinted assets exist.
4. Atomically change the `current` symlink.
5. Keep older assets available during the rollback window.

This directly supports the accepted requirement to publish assets before switching the SPA entry document.

---

# 12. Firewall and access-control model

## Provider firewall

Allow:

* TCP 80 from anywhere
* TCP 443 from anywhere
* SSH only from:

    * A Tailscale/WireGuard operations network, preferably; or
    * Explicit trusted administrative IP addresses

Deny all other inbound traffic.

## Host firewall

Repeat the restrictions locally. Provider firewalls and host firewalls protect against different mistakes.

## SSH

Adopt:

* Named operations users
* Individual SSH keys
* No shared administrator account
* `PermitRootLogin no`
* `PasswordAuthentication no`
* `PubkeyAuthentication yes`
* `AllowUsers <explicit-users>`
* Sudo only for approved operations users
* No SSH-agent forwarding
* Short session inactivity timeout
* Documented provider-console recovery procedure

Hostinger exposes SSH access information and supports SSH-related server administration, but GAM remains responsible for hardening the configuration. ([Hostinger][8])

## Provider account

Require:

* MFA
* Individual team accounts where supported
* Recovery codes stored offline
* Billing and resource alerts
* Restricted API tokens
* No API tokens in developer laptops unless required
* Audit of account access after staff changes

---

# 13. Secrets and backup credentials

Do not place production secrets in Git or in the Compose file.

For the initial small-team setup:

* Store canonical secrets in a team password manager.
* Transfer them during approved provisioning.
* Mount them as root-owned files with mode `0600`.
* Keep runtime containers from reading unrelated secrets.
* Use separate credentials for application, database, backup upload and backup restoration.
* Rotate secrets after suspected exposure and after administrator departure.
* Keep an offline recovery copy of essential credentials.

## Backup credentials

Use two different identities:

### Backup writer

* Can create backup objects
* Can list only the required prefix where necessary
* Ideally cannot permanently delete existing backups
* Lives on the VPS

### Recovery administrator

* Can read and restore backups
* Does not live on the VPS
* Is stored in the team password manager with MFA-protected provider access

Production recovery objects use Compliance-mode Object Lock, so no recovery administrator, including AWS root, can alter or shorten active retention. Two named client custodians receive individual read-only recovery identities.

This reduces the impact of complete VPS compromise.

---

# 14. Backup design

## Primary backup

Run a daily PostgreSQL logical backup using `pg_dump` in custom format, together with the required role/global metadata. PostgreSQL provides `pg_dump`, `pg_restore`, base-backup and point-in-time-recovery tooling; the simple daily logical-backup model is appropriate for the currently accepted 24-hour RPO. ([PostgreSQL][9])

Suggested process:

1. Create a consistent custom-format dump.
2. Exclude refresh-token rows while preserving the table schema.
3. Capture database roles without passwords and the required manifest metadata.
4. Verify that the dump completed and can be structurally inspected.
5. Encrypt it client-side to the independent developer and client `age` recipients.
6. Upload one standalone object to AWS S3 in `sa-east-1`.
7. Verify checksum, timestamp, size, classification, and Compliance retention.
8. Remove local plaintext and staging files.
9. Allow the independent AWS monitor to validate the current-day object.

## Suggested retention

* 30 daily backups
* 12 weekly backups selected from Monday's successful daily artifact
* 12 monthly backups
* 31/85/370-day Compliance-mode retain-until periods

Select weekly and monthly recovery points from the same daily artifacts instead of creating duplicate backup executions.

## Off-host destination

Use **AWS S3 in São Paulo (`sa-east-1`)** under the developer's existing AWS account. All recovery objects remain in Brazil.

Amazon S3 applies server-side encryption by default, and S3 Object Lock can prevent objects from being overwritten or deleted for a configured retention period. ([AWS Documentation][10])

Client-side encryption should still be used so that the object-storage provider does not possess the only encryption boundary.

When the VPS itself is on AWS, prefer a separate provider and account to reduce correlated account or platform failure. Backblaze B2 supports encryption, Object Lock, fine-grained application keys and an S3-compatible API, but its currently documented regions do not include Brazil. ([Backblaze][11])

## Provider snapshots

Use snapshots for:

* Before risky OS maintenance
* Before database-changing releases
* Weekly whole-machine recovery points
* Rapid recovery from accidental host corruption

Do not depend on them for long-term backup or provider migration.

Hostinger documents automatic weekly backups and manual snapshots, but its snapshots cannot be downloaded for use as migration backups. ([Hostinger][2])

Before purchasing any provider, test:

1. Snapshot creation
2. Restoration to a separate server
3. Boot behavior after restoration
4. Public-IP behavior
5. Whether firewall assignments persist
6. Whether the restored PostgreSQL state is actually usable
7. Snapshot deletion and retention behavior

---

# 15. Restore drills

A backup is not accepted as successful until it has been restored.

Before production, run the scripted restoration into an isolated environment, verify representative application behavior, record the result, and destroy temporary plaintext and restored data.

After production, perform the scripted restoration annually and after PostgreSQL major-version, backup-format, encryption-scheme, or recovery-key changes. Quarterly restoration and scheduled annual fresh-host reconstruction are not required for the solo-developer operating model.

Although the accepted RTO is 24 hours, the operational procedure should target restoring service in approximately four hours. That leaves margin for diagnosis and approval delays.

---

# 16. Monitoring and alerting

## External monitoring recommendation: Better Stack

Monitor:

* `https://<domain>/`
* `https://<domain>/api/health/live`
* `https://<domain>/api/health/ready`
* TLS certificate validity and expiry
* Domain expiry
* Daily backup heartbeat
* Optional deployment heartbeat

The selected backup-specific monitor is AWS EventBridge Scheduler plus Lambda and SNS. It checks at 04:30 São Paulo time, alerts the developer immediately, and escalates an unresolved failure to the client custodians at 12:00. Better Stack remains only a non-normative candidate for public availability, host, TLS, and domain monitoring.

Better Stack’s HTTP monitoring checks expected HTTP responses and creates incidents on failure. It also supports SSL certificate and domain-expiration monitoring. ([Better Stack][12])

Alerts must go through a channel independent of the VPS:

* Email plus Telegram, SMS or phone escalation
* At least two responsible people where possible
* Test alerts before production

## Host and application metrics

Monitor:

* CPU utilization and steal time
* RAM and swap
* Disk usage and inode usage
* Disk latency and I/O wait
* Network traffic and errors
* Container restart count
* JVM heap and garbage collection
* HTTP request latency and error rate
* PostgreSQL connections
* Slow queries and locks
* Database size
* Backup age
* Certificate expiry
* System reboot requirement

Better Stack can collect metrics and logs through OpenTelemetry tooling. Grafana Cloud is another viable metrics option with a free tier. ([Better Stack][13])

Initially, prefer metrics over broad external log or trace export. Authentication systems can accidentally place sensitive information in headers, spans or structured logs.

---

# 17. Safe deployment

Use a single deployment command with an exclusive lock, such as `flock`, to prevent overlapping deployments.

Recommended sequence:

1. Acquire the deployment lock.
2. Record the current release manifest.
3. Validate the requested frontend/backend pair.
4. Verify artifact signatures or checksums.
5. Confirm backup freshness.
6. Pre-pull the backend image and download the frontend.
7. Validate Caddy and Compose configuration.
8. Enable a static maintenance response at the proxy.
9. Allow existing requests a short drain period.
10. Run Flyway as an explicit one-shot operation.
11. Start the selected backend image.
12. Wait for readiness.
13. Install frontend assets and switch the frontend symlink.
14. Run HTTPS, routing, database and representative API checks.
15. Disable maintenance mode.
16. Record versions, digests, migration and verification result.
17. Release the deployment lock.

## What happens to running requests?

When a backend container is stopped:

* Existing connections may terminate.
* Requests in progress may fail.
* Uncommitted database transactions should roll back.
* Clients may see network errors or 502/503 responses.

Because planned downtime is accepted, the safest initial behavior is not to pretend deployment is seamless. Activate a controlled maintenance page and return `503 Service Unavailable` with an appropriate retry indication.

---

# 18. Rollback

Rollback has two distinct cases.

## Application-only or backward-compatible change

Rollback can:

* Restore the previous backend image digest
* Restore the previous frontend symlink
* Restart the selected composition
* Verify the previous compatible pair

## Incompatible database migration

A previous application version may no longer work with the migrated schema. In that situation, “redeploy the old image” is not a safe rollback.

Every migration must be classified as:

* Backward-compatible
* Requires forward correction
* Requires database restoration for rollback

Prefer expand-and-contract migrations even though zero downtime is not required:

1. Add compatible schema.
2. Deploy code using it.
3. Remove obsolete schema in a later release.

This greatly reduces rollback risk without introducing a second server.

---

# 19. Maintenance and rollback windows

Recommended starting policy:

* **Maintenance window:** Sunday, 07:00–09:00 America/Sao_Paulo, subject to confirmation with coordinators.
* Announce potentially disruptive maintenance in advance.
* Keep the previous frontend/backend pair for at least:

    * 14 days, or
    * Two verified production releases,
      whichever is longer.
* Keep pre-migration database backups for at least 30 days.
* Retain old fingerprinted frontend assets throughout the rollback window.

The exact weekday should ultimately follow actual coordinator usage rather than generic industry convention.

---

# 20. Production-readiness gates

Do not declare GAM production-ready until all of the following are demonstrated:

* Official domain is controlled.
* `GAM_PUBLIC_ORIGIN` validation works.
* TLS issuance and renewal work.
* HSTS remains disabled until HTTPS is verified.
* Public scans confirm that only intended ports are exposed.
* Backend and PostgreSQL are unreachable publicly.
* Origin, CSRF, refresh-cookie and proxy-forwarding tests pass.
* Request-correlation tests prove that direct local development uses application-generated UUID version 7 values and that the production Proxy prevents public clients from selecting persisted request identifiers, as required by `REQ-WEB-012` and `REQ-ACTIVITY-007`.
* Load test meets the agreed service thresholds.
* A fresh backup has been restored successfully.
* Deployment and rollback have been rehearsed.
* An incompatible-migration recovery has been discussed and documented.
* Monitoring and alerts have been triggered deliberately.
* Certificate-expiry monitoring works.
* SSH emergency recovery has been tested.
* OS and dependency patch ownership is assigned.
* Capacity thresholds and upgrade procedure are documented.
* Release manifests identify the current and previous compatible pairs.
* Someone has explicitly approved the system as production-ready.

---
