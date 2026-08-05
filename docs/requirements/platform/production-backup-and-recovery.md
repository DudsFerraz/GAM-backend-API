# Requirement: Production Backup and Recovery

## Status
Accepted

## Context
GAM stores all durable production state, including signed Oratoriano form attachments, in PostgreSQL on one Hostinger VPS. A host failure or compromise can therefore affect the application and its local database at the same time.

This specification defines the accepted off-host recovery boundary, recovery-point schedule, formal immutability guarantee, encryption custody, monitoring, audit, and restoration-validation cadence. It specializes the backup and restoration safeguards introduced by the Production Operations Requirement Specification.

## Ubiquitous Language
- `recovery artifact`: One encrypted, independently verifiable package containing a production-compatible PostgreSQL logical archive, password-free database-role metadata, and its recovery manifest.
- `daily recovery point`: A successful recovery artifact selected for the rolling daily retention set.
- `weekly recovery point`: Monday's successful daily recovery artifact, or the next successful daily artifact when Monday's attempt fails.
- `monthly recovery point`: The first successful daily recovery artifact in a calendar month.
- `formal WORM guarantee`: Compliance-mode Write Once Read Many retention that prevents an object version from being overwritten or deleted before its retain-until timestamp, including by the AWS root user.
- `backup writer`: The non-human AWS identity used only by the production backup process to create and verify recovery artifacts.
- `recovery custodian`: A named person authorized to retrieve an encrypted recovery artifact and use one approved private recovery key.

## Functional requirements

### REQ-BACKUP-001: Daily off-host recovery artifact
Production shall create one transactionally consistent PostgreSQL recovery artifact every day at `03:15 America/Sao_Paulo` while the application remains available.

The scheduler shall catch up after a host reboot. A failed scheduled attempt shall not satisfy the daily recovery point until a later attempt completes validation and off-host upload successfully.

Rationale:
One successful recovery artifact per day supports the accepted 24-hour RPO without introducing continuous archive infrastructure.

Valid examples:
- A backup missed while the VPS is rebooting runs after the host returns.
- An online logical archive completes without requiring application downtime.

Invalid examples:
- A locally created dump is counted as successful before encrypted off-host upload completes.
- A failed Monday attempt is treated as the week's successful recovery point.

---

### REQ-BACKUP-002: Durable-data recovery boundary
Each recovery artifact shall contain the complete PostgreSQL schema and all durable business, authorization, audit, historical, and attachment data required to reconstruct production.

The artifact shall include account password hashes, signed-form attachment bytes, form versions, print snapshots, activity records, soft-deleted rows, sequence state, Flyway history, and database-role definitions without role passwords.

The artifact shall exclude refresh-token rows, reusable infrastructure or application secrets, raw database-volume files, source-controlled deployment assets, release artifacts retained by their registries, routine host or container logs, caches, temporary files, and development or fixture data.

The recovery manifest shall identify the PostgreSQL version, migration state, source commit, backend image digest, frontend release, creation timestamp, sizes, and cryptographic checksums without containing reusable secrets.

Rationale:
Durable application state must be recoverable, while ephemeral sessions, independently reproducible artifacts, and reusable credentials create recovery risk without adding business value.

Valid examples:
- Restored users retain their password hashes and can authenticate with their existing passwords after signing in again.
- A password-free role inventory helps reconstruct database ownership without embedding the production database password.

Invalid examples:
- The backup omits signed attachment bytes because they are large.
- AWS credentials or private recovery keys are placed inside the recovery artifact.

---

### REQ-BACKUP-003: Rolling recovery-point classifications
GAM shall retain at least 30 daily, 12 weekly, and 12 monthly recovery points selected from the same once-daily successful artifacts.

Monday's successful artifact shall be the weekly recovery point. When Monday's attempt fails, the next successful daily artifact shall inherit the weekly classification.

The first successful artifact of each calendar month shall be the monthly recovery point. When an artifact satisfies more than one classification, it shall remain one object and receive the longest applicable retention.

Rationale:
Selecting longer-lived points from the daily artifacts provides short- and long-term recovery history without creating redundant backup executions.

Valid examples:
- A successful Monday that is also the first successful day of the month produces one monthly-classified object.
- Tuesday inherits the weekly classification after all Monday attempts fail.

Invalid examples:
- The system creates separate copies of identical daily, weekly, and monthly artifacts.
- A calendar schedule expires the last valid weekly point before its replacement succeeds.

---

### REQ-BACKUP-004: Formal WORM retention
Every production recovery artifact shall receive an Amazon S3 Object Lock Compliance-mode retain-until timestamp that provides a formal WORM guarantee.

Daily artifacts shall remain locked for at least 31 days, weekly artifacts for at least 85 days, and monthly artifacts for at least 370 days. Lifecycle expiration shall not occur before the applicable retain-until timestamp.

The one-, one-, and five-day margins above the nominal 30-day, 12-week, and 12-month sets shall protect the minimum recovery-point counts from scheduling delay, UTC boundary, and leap-year effects.

Rationale:
Compliance mode protects retained backups from privileged deletion, ransomware, administrator error, and AWS root-user compromise for the full approved retention period.

Valid examples:
- An AWS administrator cannot shorten a monthly artifact's 370-day retention.
- A lifecycle rule removes an artifact only after its Compliance retention expires.

Invalid examples:
- Governance bypass permission can delete a production recovery point early.
- The root user can remove a locked artifact to reduce an unexpected bill.

---

### REQ-BACKUP-005: Independent encryption and key custody
Every recovery artifact shall be encrypted on the VPS before it leaves Hostinger and shall remain encrypted in transit and at rest.

Client-side encryption shall address two independent recipients: one developer-controlled recipient and one client-controlled emergency recipient. The corresponding private keys shall remain outside the VPS, AWS, Git, Ansible, CI/CD, email, and project documentation.

The client-controlled private key shall be recoverable by two authorized client custodians. Public recovery keys and fingerprints may be versioned or placed in the recovery manifest.

Rationale:
Independent recipients remove the developer as a single recovery dependency while ensuring that compromise of AWS storage credentials does not reveal database plaintext.

Valid examples:
- Either approved recipient can decrypt the same artifact without sharing a private key.
- The VPS stores public encryption recipients but no private recovery key.

Invalid examples:
- One private key is copied between the developer and client through email.
- AWS server-side encryption is treated as the only encryption boundary.

---

### REQ-BACKUP-006: Separated AWS identities
The production backup process shall use a dedicated non-console backup-writer identity restricted to the GAM backup bucket and the VPS public source address.

The writer may create an artifact, apply its approved classification and Compliance retention, and verify its uploaded metadata and checksum. It shall not delete objects, bypass retention, manage buckets or lifecycle rules, administer IAM, access billing, or decrypt recovery artifacts.

The writer access key shall rotate at least every 90 days. Two named client custodians shall have individual, MFA-protected, console-only identities limited to listing, inspecting, and downloading GAM recovery artifacts and manifests.

The AWS root identity shall have no access keys, shall use two independent MFA methods, and shall not be used for routine administration. Routine developer administration shall use a named MFA-protected identity.

Rationale:
Identity separation limits the effect of VPS, client, or administrator credential compromise and preserves individual accountability.

Valid examples:
- A stolen writer key can be revoked without changing client recovery access.
- Each client custodian uses an individual identity instead of a shared AWS login.

Invalid examples:
- The backup process uses the developer's administrator access key.
- The writer can delete older recovery points.

---

### REQ-BACKUP-007: Brazilian off-host storage and lifecycle
Recovery artifacts shall be stored in Amazon S3 in `sa-east-1` and shall not be replicated or transitioned outside Brazil.

The backup bucket shall be private, block all public access, use bucket-owner-enforced object ownership, enable versioning and Object Lock, and use S3 server-side encryption in addition to client-side encryption.

Artifacts shall begin in S3 Standard. Eligible weekly and monthly artifacts shall transition to S3 Standard-IA after 30 days. Eligible monthly artifacts shall transition to S3 Glacier Flexible Retrieval after 90 days, preserving the 24-hour RTO.

Rationale:
The São Paulo region satisfies the accepted Brazilian residency boundary. Lifecycle transitions reduce cost without weakening the approved recovery objectives or WORM retention.

Valid examples:
- A monthly artifact older than 90 days remains in São Paulo while using Glacier Flexible Retrieval.
- An archive retrieval taking several hours still completes within the 24-hour RTO.

Invalid examples:
- Cross-region replication sends a copy to a non-Brazilian AWS region.
- One Zone-IA is used for the only off-host recovery copy.

---

### REQ-BACKUP-008: Backup completion monitoring and escalation
An AWS-hosted check independent of the VPS shall validate the current day's recovery artifact at `04:30 America/Sao_Paulo`.

The check shall verify that the expected object exists with nonzero size, checksum metadata, encrypted-content metadata, the correct classification, and the applicable Compliance retention.

A missing or invalid artifact shall alert the developer after the 04:30 check. If unresolved, the failure shall escalate to both client custodians at `12:00 America/Sao_Paulo`. A later successful retry shall generate a recovery notification.

Failure of the independent monitor itself shall generate an external alert.

Rationale:
The VPS cannot reliably report its own outage. The completion window avoids false alerts while detecting a missed recovery point well before the RPO is breached.

Valid examples:
- The 04:30 check alerts when the 03:15 upload never completes.
- A successful 05:00 catch-up sends a recovery notice after the earlier alert.

Invalid examples:
- Monitoring runs only as another container on KVM 2.
- Object existence alone is accepted without checking its lock and checksum metadata.

---

### REQ-BACKUP-009: Immutable object-access audit
GAM shall record read and write data events for the production backup bucket, including upload, download, retention, tagging, and attempted deletion operations.

Audit logs shall be delivered to a separate S3 bucket, use log-file integrity validation, and receive 400-day Compliance-mode WORM retention. The audit destination shall be excluded from its own object-level data-event selector.

Rationale:
A formal WORM design needs durable evidence of who accessed recovery artifacts or attempted to change them without recursively logging CloudTrail's own deliveries.

Valid examples:
- A client custodian's recovery download appears in the immutable audit history.
- An unsuccessful deletion attempt remains auditable.

Invalid examples:
- Object-level data events are assumed to appear in the default 90-day management-event history.
- CloudTrail writes into the monitored backup bucket and recursively records its own delivery.

---

### REQ-BACKUP-010: Restoration validation cadence
A complete isolated restoration shall succeed before the first production deployment.

After production begins, the developer shall run the scripted isolated database-restoration procedure at least annually and after a PostgreSQL major-version change, backup-format change, encryption-scheme change, or recovery-key rotation.

The client recovery recipient shall be validated before production and after client-key rotation. A scheduled quarterly restoration drill and a scheduled annual fresh-host reconstruction are not required.

Rationale:
This cadence preserves direct evidence of recoverability while keeping ongoing manual work proportionate for a solo developer. Daily structural and off-host checks continue between restoration exercises.

Valid examples:
- The annual procedure downloads, decrypts, restores, validates, records the result, and destroys an isolated temporary database.
- Rotating the client recipient triggers validation without waiting for the annual date.

Invalid examples:
- Daily checksum checks are represented as the pre-production restoration.
- A PostgreSQL major upgrade occurs without restoring a newly produced artifact.

---

### REQ-BACKUP-011: Secure restoration behavior
A restored environment shall remain isolated from public traffic until integrity and representative application access are verified.

Disaster recovery shall start with an empty refresh-token table, rotate the JWT signing secret, and require every user to authenticate again. Temporary plaintext dumps, decrypted archives, and restored databases shall be destroyed after validation or controlled production recovery.

Restoration evidence shall record the selected recovery point, checksum, duration, structural results, representative row or invariant checks, attachment sampling, and corrective actions without recording plaintext personal data or secrets.

Rationale:
Historical sessions must not be resurrected, and restoration testing must not create an unmanaged copy of sensitive production data.

Valid examples:
- Existing password hashes let users sign in again after global session invalidation.
- The restoration report records attachment checksum validation without copying the attachment into the report.

Invalid examples:
- Restored refresh tokens become valid when the service starts.
- A decrypted annual-test archive remains on a developer workstation indefinitely.

---

### REQ-BACKUP-012: Cost governance and reassessment
GAM AWS resources shall be identifiable through project, environment, and purpose cost-allocation tags within the developer's existing AWS account.

The developer shall receive an actual-or-forecast warning at US$5, a mandatory design and cost reassessment at US$10, and a critical alert at US$25. Cost Anomaly Detection shall alert on an estimated US$5 impact.

The design shall also be reassessed when one encrypted daily artifact exceeds 5 GB. Cost alerts shall not automatically stop backup creation, weaken retention, or delete recovery points.

Rationale:
Expected costs are low, but the shared account and Compliance retention require early visibility without allowing cost automation to undermine recovery.

Valid examples:
- A US$10 forecast starts a review while daily backups continue.
- Attachment growth beyond 5 GB per artifact triggers consideration of a different storage model.

Invalid examples:
- A budget action disables the writer when spending reaches US$10.
- A locked monthly artifact is expected to be removed early to correct a cost overrun.

## Acceptance scenarios

```gherkin
Scenario: Select one artifact for overlapping retention classes
  Given Monday is the first successful backup day of the month
  When the daily recovery artifact is uploaded
  Then exactly one object is created
  And it is retained in Compliance mode for at least 370 days

Scenario: Preserve the formal WORM guarantee
  Given a monthly recovery point is still within its retain-until period
  When an AWS administrator or root user attempts to delete it
  Then Amazon S3 rejects the deletion

Scenario: Detect a missed daily backup independently
  Given no valid artifact exists for the current local date
  When the independent check runs at 04:30 America/Sao_Paulo
  Then the developer receives an alert
  And both client custodians receive an escalation at 12:00 if the failure remains unresolved

Scenario: Recover without restoring sessions
  Given an approved recovery artifact is restored
  When the recovered application is opened to users
  Then durable business data and attachment bytes are available
  And the refresh-token table is empty
  And every user must authenticate again

Scenario: Validate recoverability with proportionate manual work
  Given the pre-production restoration has succeeded
  When one year passes without a material backup-system change
  Then the developer runs the scripted isolated restoration
  And records the result without retaining plaintext production data
```

## Diagrams
- [Production Backup and Recovery](../../diagrams/production-backup-and-recovery.md)

## Open questions
- Which two named client representatives will be recovery custodians?
- Which approved password-manager or offline-vault products will hold the two private recovery identities?
- Which developer and client email addresses will subscribe to operational and escalation notifications?

These are provisioning inputs. They do not change the accepted architecture or block implementation of parameterized automation.

## Out of scope
- Continuous WAL archiving, point-in-time recovery, physical base backups, or database replication.
- Module-specific or attachment-specific recovery points.
- Moving signed attachment bytes out of PostgreSQL.
- Cross-region or non-Brazilian backup replication.
- Paid AWS support plans, reserved capacity, Savings Plans, or storage commitments.
- Customer-managed AWS KMS keys.
- A dedicated AWS account used only for GAM.
- Scheduled quarterly restoration drills or scheduled annual fresh-host reconstruction.
- Treating Hostinger snapshots as the primary database backup.

## Related ADRs
- [ADR-0016: Store signed Oratoriano form attachments in PostgreSQL](../../decisions/0016-store-signed-oratoriano-form-attachments-in-postgresql.md)
- [ADR-0025: Use AWS São Paulo for immutable encrypted production backups](../../decisions/0025-use-aws-sao-paulo-for-immutable-encrypted-production-backups.md)

## Related requirements
- [Production Operations](production-operations.md)
- [Oratoriano Additional Forms](../oratorianos/oratoriano-additional-forms.md)

## Related videos
- None.
