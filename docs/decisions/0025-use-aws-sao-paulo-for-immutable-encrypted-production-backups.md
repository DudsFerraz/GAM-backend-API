# ADR-0025: Use AWS São Paulo for Immutable Encrypted Production Backups

## Status
Accepted

## Context
GAM's production PostgreSQL database and signed-form attachments reside on one Hostinger KVM 2 VPS in Brazil. The accepted 24-hour RPO and RTO require a complete off-host copy that survives VPS loss or compromise and remains in Brazil.

The current codebase has no production database from which to measure backup size. Development fixtures model 73 accounts, 65 members, 62 Oratorianos, and only tiny synthetic attachments. Operational planning therefore uses 100 Oratorianos at launch, no more than 150 over the next two years, a planning average of 5 MB of signed attachments per Oratoriano, and 25 percent additional database overhead. The application permits up to 40 MiB of image attachments per completed form, which supplies the conservative upper boundary.

The backup design must remain manageable by one developer. It must also protect sensitive personal, family, health, consent, signature, authentication, and audit data. Storing module-specific monthly attachment dumps would complicate relational restoration and could violate the 24-hour RPO after a late upload, correction, revocation, or soft deletion.

AWS S3 in São Paulo was selected after comparing Brazilian and non-Brazilian object-storage options. The developer will use an existing developer-owned AWS account and billing identity rather than a dedicated GAM account.

## Decision

### Commercial model
Use Amazon S3 pay-as-you-go storage in `sa-east-1` under the developer's existing AWS account and Brazilian billing identity.

Use AWS Basic Support. Business Support+, Enterprise Support, reserved capacity, Savings Plans, prepaid storage, and other paid support commitments are out of scope.

AWS prices remain USD-denominated and are converted to BRL on the Brazilian invoice. Before production and during cost reviews, verify current regional prices, exchange treatment, and applicable taxes against the AWS pricing and Brazil billing pages.

### Recovery artifact
Create one standalone PostgreSQL custom-format logical archive each day rather than a mutable deduplicating repository. Exclude only `refresh_tokens` table data from the durable application database boundary.

Add a password-free database-role export and a sanitized recovery manifest. Validate the logical archive before encryption, calculate cryptographic checksums, encrypt the package for the approved recovery recipients, upload it, and verify S3's resulting checksum, tags, size, and retention metadata before reporting success.

Use unique immutable keys with this conceptual layout:

```text
production/postgresql/YYYY/MM/DD/<UTC timestamp>-<classification>.dump.age
```

Never overwrite an existing key. Remove local plaintext and encrypted staging files after verified upload.

### Encryption and custody
Encrypt every artifact locally with `age` for two independent recipients:

- one private identity controlled by the developer; and
- one private identity controlled by the client and recoverable by two authorized client custodians.

Only public recipients and fingerprints may exist on the VPS or in versioned configuration. Private identities remain in separate MFA-protected custody outside AWS, Hostinger, Git, Ansible, CI/CD, email, and documentation.

Retain S3's automatic SSE-S3 encryption as a second layer. Do not introduce a customer-managed KMS key initially. KMS would add cost and centralize storage and decryption control in the same AWS account without replacing client-side custody.

### Backup bucket
Create one globally unique private bucket using this naming pattern:

```text
gam-production-backups-<aws-account-id>-sa-east-1
```

Configure:

- Region `sa-east-1`.
- S3 Block Public Access enabled at every bucket setting.
- Bucket-owner-enforced object ownership and ACLs disabled.
- Versioning enabled.
- Object Lock enabled.
- Default SSE-S3 encryption.
- HTTPS-only bucket policy.
- Project tags `Project=GAM`, `Environment=production`, and `Purpose=backup`.
- No cross-region replication.

The backup writer applies per-object Compliance-mode retention. Bucket policy conditions constrain allowable retention and the independent monitor verifies the exact class-specific retain-until timestamp.

### Recovery classification and lifecycle
Run the online backup from a persistent systemd timer at `03:15 America/Sao_Paulo`.

- Keep the rolling daily set locked for 31 days.
- Select Monday's successful artifact for the weekly set and lock it for 85 days.
- Select the first successful artifact of each calendar month for the monthly set and lock it for 370 days.
- When classes overlap, store one monthly-classified object with the longest retention.
- When a Monday or first-of-month attempt fails, assign the pending class to the next successful daily artifact.

Use lifecycle rules by classification:

- Daily: S3 Standard until eligible for expiration after Compliance retention.
- Weekly: S3 Standard for 30 days, then S3 Standard-IA until eligible for expiration.
- Monthly: S3 Standard for 30 days, S3 Standard-IA through day 90, then S3 Glacier Flexible Retrieval until eligible for expiration.
- Abort incomplete multipart uploads after seven days.

Lifecycle expiration becomes eligible only after the applicable WORM retention. Glacier Flexible Retrieval's restoration delay remains within the 24-hour RTO.

### Formal WORM guarantee
Use Amazon S3 Object Lock in Compliance mode for every production recovery point. The architecture and requirements deliberately claim a formal WORM guarantee: neither the backup writer, developer administrator, AWS root user, lifecycle policy, nor a compromised privileged identity can overwrite, delete, or shorten a retained object version before its retain-until timestamp.

This irreversibility is accepted. Incorrect uploads, accidental secrets, excessive objects, or mistakenly long class selection can remain billable and undeletable until retention expires.

### AWS identities
Create a non-console IAM user named `gam-production-backup-writer` for the Hostinger VPS. Keep one active access key in a root-owned mode-`0600` secret file and rotate it every 90 days. Restrict use to the VPS static public address and TLS.

Allow only the S3 actions required to list the production prefix where necessary, create an object, apply its tags and approved Compliance retention, and verify upload metadata and checksum. Deny deletion, retention bypass, bucket or lifecycle administration, IAM, billing, unrelated AWS resources, and other bucket prefixes.

Create two named, console-only, MFA-protected client IAM identities with list, metadata-inspection, and download access to the GAM backup prefix. They receive no write, delete, retention, billing, or administrative permission.

Keep the AWS root identity free of access keys, enroll two independent MFA methods, and use it only for root-required account operations. Use a named MFA-protected developer administrator for routine configuration.

### Monitoring and alerting
At `04:30 America/Sao_Paulo`, EventBridge Scheduler invokes a small Lambda function that verifies the current local-date artifact, nonzero size, checksum metadata, encrypted metadata, classification, and Compliance retention.

Publish an immediate SNS email to the developer when the artifact is missing or invalid. Run an unresolved-state check at `12:00 America/Sao_Paulo` and escalate to both client custodians. Publish a recovery notice after a later retry succeeds. Use a CloudWatch alarm to detect failure of the monitor itself.

These checks prove structural and storage conditions, not decryptability. Restoration testing remains separate.

### Audit
Create a separate bucket using this naming pattern:

```text
gam-production-backup-audit-<aws-account-id>-sa-east-1
```

Use a CloudTrail trail with log-file integrity validation and advanced event selectors for read and write data events on the GAM backup bucket and relevant write management events affecting S3, Object Lock, lifecycle, and IAM configuration. Exclude the audit destination from its own data-event selection.

Protect CloudTrail logs with default 400-day Compliance-mode Object Lock, SSE-S3, public-access blocking, versioning, and lifecycle archival. The audit bucket records metadata and does not store database plaintext.

### Cost controls and estimate
Activate the `Project` tag for cost allocation. Configure a GAM-filtered budget with US$5 actual-or-forecast warning and US$10 actual-or-forecast reassessment alert, plus a US$25 critical alert. Enable Cost Anomaly Detection at US$5 impact. Alerts do not disable uploads or alter retained objects.

At the current published S3 Standard São Paulo rate of approximately US$0.0405 per GB-month, a conservative model before lifecycle savings is:

| Scenario | Estimated encrypted daily artifact | Approximately 47 retained unique points | S3 Standard storage/month |
| --- | ---: | ---: | ---: |
| Development fixtures | Less than 0.1 GB | Less than 4.7 GB | Less than US$0.20 |
| 100 Oratorianos at 5 MB average plus 25% overhead | 0.63 GB | 29 GB | US$1.19 |
| 150 Oratorianos at 5 MB average plus 25% overhead | 0.94 GB | 44 GB | US$1.78 |
| 150 Oratorianos at the 40 MiB attachment ceiling plus 25% overhead | About 7.5 GB | About 353 GB | US$14.28 |

Lifecycle transitions should reduce the older weekly/monthly portion, while requests, scheduled monitoring, and normal CloudTrail volume should remain negligible at GAM's scale. The actual BRL invoice depends on retained bytes, request/retrieval activity, the invoice-time exchange rate, and Brazilian taxes.

Reassess the backup storage model when one encrypted artifact exceeds 5 GB or GAM AWS charges remain above US$10 per month. Do not split attachment backups merely to save the expected US$1-2 monthly storage cost.

### Restoration validation
Complete one isolated restoration before production. After launch, run the scripted isolated data restoration annually and after a PostgreSQL major-version, backup-format, encryption-scheme, or recovery-key change.

Test the client recipient before production and after client-key rotation. Do not require quarterly manual drills, alternating recipients every quarter, or scheduled annual full-Ubuntu reconstruction. The reduced cadence accepts that a decrypt/restore defect could remain undiscovered for up to one year, offset by daily structural validation and independent storage monitoring.

During disaster recovery, restore no refresh-token rows, rotate the JWT signing secret, and require universal sign-in. Keep restored data isolated until validation completes and securely remove temporary plaintext afterward.

### Provisioning
Manage GAM-specific AWS resources through the accepted Ansible-only provisioning model from the developer workstation. Keep account ownership, billing, root MFA, initial client MFA, recovery-key custody, and SNS email confirmation as explicit manual steps.

Do not store developer administrator credentials in the repository or on KVM 2. Use a temporary MFA-backed administrator session to run AWS provisioning.

## Alternatives considered

### Option 1: Hostinger backups or snapshots as the primary copy
Pros:
- Convenient whole-host recovery.
- Integrated with the VPS provider.

Cons:
- Shares the provider failure and account boundary.
- Does not provide the accepted independent database custody and formal WORM design.
- Provider snapshots remain supplementary and may not be portable.

### Option 2: AWS S3 outside Brazil
Pros:
- Some regions have lower storage prices.
- Broader cross-region options.

Cons:
- Violates the accepted Brazilian backup-residency decision.
- Adds cross-border privacy, billing, and recovery considerations for little absolute savings.

### Option 3: AWS S3 São Paulo
Pros:
- Keeps backup objects in Brazil.
- Provides multi-AZ durable object storage, lifecycle classes, Object Lock, IAM, CloudTrail, and serverless monitoring.
- Pay-as-you-go operation has no capacity purchase.

Cons:
- São Paulo storage and egress are more expensive than some foreign regions.
- The developer's shared AWS account creates a wider account-level blast radius than a dedicated account.

### Option 4: Dedicated developer-owned AWS account
Pros:
- Stronger account isolation and simpler GAM-only billing.
- Smaller impact from unrelated AWS projects.

Cons:
- Requires another root identity, MFA, recovery record, and account administration.
- Was rejected in favor of the developer's existing AWS account.

### Option 5: Governance-mode Object Lock
Pros:
- A break-glass administrator can correct retention mistakes or remove accidental objects.
- Lower operational risk while commissioning.

Cons:
- Privileged AWS or root compromise can bypass retention.
- Does not provide the approved formal WORM guarantee.

### Option 6: Compliance-mode Object Lock
Pros:
- Protects retained artifacts even from privileged AWS and root credentials.
- Provides the explicitly required formal WORM guarantee.

Cons:
- Retention mistakes, accidental secrets, corrupt objects, and storage costs cannot be corrected early.

### Option 7: Customer-managed KMS encryption
Pros:
- Central AWS key policy and API audit.
- Managed key rotation options.

Cons:
- Adds monthly key and request cost.
- Keeps storage and decryption control inside the same AWS account.
- Does not replace developer/client recovery separation.

### Option 8: Module-specific or change-conditioned attachment backups
Pros:
- Avoids repeatedly storing unchanged compressed PDF and image bytes.
- Could reduce storage at the maximum attachment boundary.

Cons:
- PostgreSQL relationships and attachment metadata require coordinated restore points.
- Monthly-only attachment capture can violate the 24-hour RPO.
- Custom delta, content-addressed, or table-specific restoration creates disproportionate complexity for the expected US$1-2 bill.

### Option 9: Incremental physical backup or mutable deduplicating repository
Pros:
- Avoids recopying unchanged database pages or attachment chunks.
- Can support more frequent recovery points.

Cons:
- Adds WAL, PostgreSQL-version coupling, repository maintenance, pruning, and restore complexity.
- Mutable repository cleanup conflicts with straightforward per-object Compliance retention.
- Is not justified by the current size or 24-hour RPO.

## Consequences

Positive consequences:
- Production receives off-host Brazilian recovery points with a formal WORM guarantee.
- Client-side dual-recipient encryption separates storage access from plaintext access.
- A compromised VPS identity cannot delete retained backups or decrypt them.
- Client custodians can retrieve and decrypt a recovery artifact when the developer is unavailable.
- Full daily artifacts keep restoration simpler than module-specific or incremental designs.
- Lifecycle and budget controls bound expected operational cost.
- CloudTrail supplies immutable evidence of object access.

Negative consequences:
- Compliance-mode mistakes are irreversible until retention expiry.
- Long-lived external-workload credentials must be rotated and protected on KVM 2.
- A compromised writer can upload unwanted objects and create cost until its key is revoked.
- The existing developer AWS account shares administrator and billing risk with unrelated projects.
- Client-side private-key loss can make otherwise healthy artifacts unusable.
- Annual rather than quarterly restoration accepts a longer defect-detection interval.
- Glacier-class monthly restoration includes retrieval delay and possible retrieval charges.

## Related requirements
- `REQ-OPS-003`
- `REQ-OPS-004`
- `REQ-BACKUP-001`
- `REQ-BACKUP-002`
- `REQ-BACKUP-003`
- `REQ-BACKUP-004`
- `REQ-BACKUP-005`
- `REQ-BACKUP-006`
- `REQ-BACKUP-007`
- `REQ-BACKUP-008`
- `REQ-BACKUP-009`
- `REQ-BACKUP-010`
- `REQ-BACKUP-011`
- `REQ-BACKUP-012`

## Related ADRs
- [ADR-0016: Store signed Oratoriano form attachments in PostgreSQL](0016-store-signed-oratoriano-form-attachments-in-postgresql.md)
- [ADR-0024: Deploy production directly to Hostinger KVM 2](0024-deploy-production-directly-to-hostinger-kvm-2.md)

## Related diagrams
- [`docs/diagrams/production-backup-and-recovery.md`](../diagrams/production-backup-and-recovery.md)

## References
- [Amazon S3 pricing](https://aws.amazon.com/s3/pricing/)
- [Amazon S3 Object Lock](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock.html)
- [Amazon S3 lifecycle transitions](https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-transition-general-considerations.html)
- [AWS root-user best practices](https://docs.aws.amazon.com/IAM/latest/UserGuide/root-user-best-practices.html)
- [AWS Support pricing](https://aws.amazon.com/premiumsupport/pricing/)
- [AWS Budgets pricing](https://aws.amazon.com/aws-cost-management/aws-budgets/pricing/)
- [AWS CloudTrail pricing](https://aws.amazon.com/cloudtrail/pricing/)
- [PostgreSQL 18 `pg_dump`](https://www.postgresql.org/docs/18/app-pgdump.html)
- [PostgreSQL 18 `pg_dumpall`](https://www.postgresql.org/docs/18/app-pg-dumpall.html)

## Related videos
- None.
