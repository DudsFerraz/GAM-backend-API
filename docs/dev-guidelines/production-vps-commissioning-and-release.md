# Production VPS Commissioning and 1.0 Release

## Purpose

This runbook describes how to commission GAM's first production VPS, validate
release candidates on that host for approximately 10–15 days, initialize the
production database, run required maintenance jobs, and release version 1.0.

It is written for the developer operating GAM. It does not replace the accepted
requirements or architecture decisions linked at the end of this document.

## Readiness verdict

The accepted deployment architecture and focused production contracts are ready
to guide implementation. The repository is not yet ready for an actual
production deployment; implementation and verification begin with item 1.

Do not improvise missing infrastructure directly on the VPS. Implement it in
versioned automation first.

## Recommended implementation order

Focused planning is complete. Before implementation, validate `REQ-OPS-006`
through `REQ-OPS-012`, `REQ-WEB-013`, and ADR-0028 as the authoritative
planning baseline. Return to Agent P only if implementation exposes a missing
or contradictory contract.

The order is wave-based, not globally serial. Wave numbers define hard
dependencies. Every package within the same wave may and should run concurrently
in its own worktree, branch, and Agent O task. The role cycle inside one package
remains sequential.

An immutable compatible backend/frontend candidate pair is strictly required
only for Wave 4. Do not block Waves 1 or 2 on backend or frontend stabilization.
Use contract-valid fixture images and frontend archives until the real artifacts
become a hard verification dependency.

### Wave 1: immediate concurrent implementation

Start all five packages concurrently. Keep their ownership boundaries explicit
so that worktree isolation does not turn into merge conflicts.

#### 1. Production runtime seam and public health

- **Scope:** Implement production profile validation, secure public-origin and
  trusted-proxy behavior, and the minimal `GET /api/health` contract without
  internal details. Include tests and OpenAPI verification.
- **Suggested branch:** `codex/production-health-runtime`
- **Agent O prompt:**

  > Use `$gam-orchestration` for workflow `production-health-runtime`. Validate
  > `REQ-OPS-011`, `REQ-WEB-002`, `REQ-WEB-012`, and ADR-0028, then orchestrate
  > the complete T/D/R implementation cycle for only the production runtime seam
  > and public health contract.

#### 2. Production composition and deployment controls

- **Scope:** Implement production Docker Compose, Caddy, the commissioning and
  maintenance responses, the release-manifest model, deployment locking,
  backup-freshness and Flyway gates, verification, release recording, and
  database-aware rollback. Use fixture OCI digests and fixture frontend archives;
  do not wait for the real artifact pipelines.
- **Suggested branch:** `codex/production-compose-deploy`
- **Agent O prompt:**

  > Use `$gam-orchestration` for workflow `production-compose-deploy`. Validate
  > the Production Operations and Web Delivery Requirement Specifications plus
  > ADR-0024 and ADR-0028, then orchestrate the complete T/D/R cycle for Compose,
  > Caddy, manifest, deployment, verification, and rollback controls using
  > contract-valid fixture artifacts.

#### 3. Production backup, restoration, and AWS monitoring

- **Scope:** Implement the PostgreSQL dump, password-free role export, sanitized
  manifest, checksums, dual-recipient `age` encryption, upload and cleanup,
  restoration verification, S3 and IAM automation, Compliance retention,
  lifecycle, audit, budgets, EventBridge, Lambda, SNS, and Better Stack
  configuration. Parameterize the VPS source IP and defer only its binding and
  environment-specific verification.
- **Suggested branch:** `codex/production-backup-aws`
- **Agent O prompt:**

  > Use `$gam-orchestration` for workflow `production-backup-aws`. Validate the
  > Production Backup and Recovery and Production Operations Requirement
  > Specifications plus ADR-0025 and ADR-0028, then orchestrate the complete
  > T/D/R cycle for backup, secure restoration, AWS resources, and monitoring.
  > Keep the VPS IP parameterized and exclude human account and key-custody work.

#### 4. Member-information import

- **Scope:** Implement `REQ-MEMBER-IMPORT-010` through
  `REQ-MEMBER-IMPORT-015`, including validation-only execution, atomic apply,
  safe diagnostics, idempotency, minimized activity, tests, and the production
  one-shot maintenance wrapper.
- **Branch:** Continue the existing `codex/member-seed` branch and workflow. Do
  not start a duplicate Agent O cycle while that cycle is active.
- **Agent O prompt, only if the workflow must be restarted:**

  > Use `$gam-orchestration` for workflow `member-information-import`. Validate
  > the accepted Member Information Import and Account Linking Requirement
  > Specification and ADR-0026, then orchestrate the complete T/D/R cycle for
  > `REQ-MEMBER-IMPORT-010` through `REQ-MEMBER-IMPORT-015` only.

#### 5. Host baseline provisioning

- **Scope:** Implement the Ansible inventory and base Ubuntu 24.04 roles for SSH
  hardening, operations users, Docker, firewall integration, directories,
  recoverable secret inputs, and idempotency checks. Own the host baseline only;
  consume rather than duplicate the Compose, Caddy, backup, restoration, AWS, or
  monitoring components owned by packages 2 and 3.
- **Suggested branch:** `codex/production-host-baseline`
- **Agent O prompt:**

  > Use `$gam-orchestration` for workflow `production-host-baseline`. Validate
  > `REQ-OPS-001`, `REQ-OPS-002`, `REQ-OPS-010`, and ADR-0024, then orchestrate
  > the complete T/D/R cycle for the Ubuntu 24.04 Ansible host baseline. Exclude
  > package-owned Compose, deployment, backup, and AWS behavior.

### Wave 2: pre-artifact infrastructure integration

Start this package after packages 1, 2, 3, and 5 have completed review and their
branches have been integrated. Package 4 may still be running concurrently
because its result is not required for fixture-based infrastructure integration.

#### 6. Fixture-based host integration and restoration

- **Scope:** Connect the reviewed host baseline, Compose, Caddy, deployment,
  backup, restoration, AWS, and monitoring automation. Prove Ansible idempotency,
  deploy and rollback fixture artifacts, and complete the isolated synthetic-data
  portion of restoration testing. Record which checks remain blocked only by a
  real VPS or immutable candidate artifacts.
- **Suggested branch:** `codex/production-preartifact-integration`
- **Agent O prompt:**

  > Use `$gam-orchestration` for workflow `production-preartifact-integration`.
  > Validate the accepted production operations, backup, web-delivery, and
  > ADR-0024, ADR-0025, and ADR-0028 contracts, then orchestrate the complete
  > T/D/R cycle that integrates the reviewed infrastructure packages with
  > fixture artifacts. Do not add backend or frontend artifact pipelines.

Wave 2 is the pre-artifact ceiling. Do not begin artifact work merely because
other application development is unfinished. Begin Wave 3 only when the
remaining infrastructure checks specifically require immutable backend and
frontend artifacts.

Package 4 may continue concurrently through Waves 2, 3, and 4. It becomes a
hard dependency only before production database initialization in Wave 5.

### Wave 3: deferred artifact implementation

Packages 7 and 8 may run concurrently in their separate repositories. They do
not need to wait for each other.

#### 7. Backend production artifact

- **Scope:** Implement the production backend `Dockerfile` and CI workflow that
  runs the canonical Maven and OpenAPI gates, builds and scans the OCI image,
  publishes it to private GHCR, and records its immutable digest and source
  commit. Publication must not deploy production.
- **Suggested branch:** `codex/production-backend-artifact`
- **Agent O prompt:**

  > Use `$gam-orchestration` for workflow `production-backend-artifact`. Validate
  > `REQ-WEB-011`, `REQ-OPS-008`, `REQ-OPS-010`, ADR-0024, and ADR-0028, then
  > orchestrate the complete T/D/R cycle for the production Dockerfile and
  > verified private-GHCR publication workflow. Exclude production deployment.

#### 8. Frontend release artifact

- **Scope:** In the frontend repository, implement the build and verification
  pipeline that publishes the immutable versioned archive and checksum defined
  by `REQ-WEB-013`, records the supported backend contract, and preserves the
  previous compatible artifact.
- **Suggested branch:** `codex/production-frontend-artifact`
- **Agent O prompt:**

  > Use `$gam-orchestration` for workflow `production-frontend-artifact`.
  > Validate `REQ-WEB-011`, `REQ-WEB-013`, and ADR-0028, then orchestrate the
  > complete T/D/R cycle in the frontend repository for the immutable release
  > archive, checksum, verification, and retention interface. Exclude deployment.

### Wave 4: artifact-dependent release rehearsal

This is the first wave that requires a real immutable compatible candidate pair.
It starts only after packages 7 and 8 are reviewed and integrated. The selected
pair need not be named version 1.0 yet; the rehearsal and burn-in determine
whether it is suitable for that release.

#### 9. Final release-pair integration and isolated rehearsal

- **Scope:** Replace fixture references with the selected immutable backend
  digest and frontend release, verify download and checksums, exercise the real
  production runtime, deploy, migrate, roll back, back up, decrypt, restore, and
  verify with synthetic production-like data. Record the successful isolated
  restoration using the final backup format.
- **Suggested branch:** `codex/production-release-rehearsal`
- **Agent O prompt:**

  > Use `$gam-orchestration` for workflow `production-release-rehearsal`.
  > Validate all accepted production operations, backup, web-delivery, and
  > ADR-0024, ADR-0025, and ADR-0028 contracts, then orchestrate the complete
  > T/D/R cycle for the real immutable release pair and final isolated deployment,
  > rollback, backup, and restoration rehearsal. Exclude paid VPS actions.

### Wave 5: paid VPS commissioning

Purchase KVM 2 only after Wave 4 succeeds. Apply the reviewed automation, bind
and verify the AWS writer source-IP policy, activate external monitoring, run the
10–15-day gated burn-in, freeze the approved version 1.0 pair, recreate the clean
production database, validate and apply the Member-information import, verify
recovery again, and perform the approved public launch.

This is a human-controlled operational phase executed from reviewed `main`, not
an ordinary implementation branch. It has no standing Agent O prompt. If
commissioning exposes a defect, open a narrowly scoped `codex/production-...`
branch and start a new Agent O cycle for that defect; do not repair production
manually.

## Recommended GAM agent workflow

Use the GAM agent workflow for implementation, but apply it to cohesive work
packages rather than to every individual file or command.

The broad and focused deployment planning is complete. Do not repeat the previous
provider, capacity, backup, retention, custody, cost, health, commissioning,
monitoring, maintenance, rollback, or artifact-transfer interview. Agent O shall
validate the accepted artifacts; return to Agent P only if implementation exposes
a genuinely missing or contradictory contract.

For each package, a fresh Agent O validates the accepted planning artifacts and
orchestrates Agent T, Agent D, the expanded Agent T verification, and independent
Agent R review. Agent T and Agent D remain sequential inside that package.

Different packages in the same wave may run concurrently because each has its
own worktree, branch, Agent O task, and file ownership. Do not run two package
workflows in one worktree. A dependent wave starts only after its prerequisite
branches have completed review and have been integrated into `main`.

Concurrent ownership is:

- package 1 owns backend runtime, health, tests, and OpenAPI changes;
- package 2 owns Compose, Caddy, release manifests, deployment, rollback, and
  their direct tests;
- package 3 owns backup, restoration, AWS, Better Stack configuration, and their
  related Ansible components;
- package 4 owns Member-information import behavior;
- package 5 owns the base host inventory, bootstrap, and operating-system roles;
- package 6 alone owns the first integration wiring among packages 1, 2, 3, and
  5;
- packages 7 and 8 own their respective repository artifact pipelines; and
- package 9 owns final cross-package rehearsal changes.

A package may return to Agent P only when implementation exposes a genuinely
missing or contradictory requirement or architecture decision.

Do not use the agent workflow to automate provider purchase, billing acceptance,
root or client MFA enrollment, recovery private-key custody, alert-email
confirmation, production-data approval, or the final release decision. Those are
human-controlled operations performed with this runbook after implementation is
reviewed.

## Commissioning model

Use the selected Hostinger KVM 2 as the final production host. KVM 1 is not part
of this process.

The recommended lifecycle is:

| Phase | Timing | Data allowed | Public availability |
| --- | --- | --- | --- |
| Implementation | Before buying or starting the VPS | Local synthetic data only | None |
| Host commissioning | About 15 days before 1.0 | Synthetic production-like data | Developer-only commissioning gate |
| Candidate burn-in | About 10–15 days | Synthetic production-like data | Developer-only commissioning gate |
| Production initialization | After code freeze | Approved production data | Still gated |
| Version 1.0 release | Release day | Production data | Client-facing |

The burn-in is not a second environment. It is a restricted commissioning phase
on the final host.

Development still occurs on the developer workstation and through normal review
and CI. Candidate builds run on the VPS only after they have passed verification
and been published as immutable artifacts. Do not edit source code, build JARs,
or create unversioned production configuration on the VPS.

Do not load the real Member-information dataset during the ordinary burn-in.
Load it only after code freeze, final database initialization, a successful
validation-only import, and confirmation of a current off-host backup.

## Required decisions and values

Collect these values before provisioning. Keep secrets in the approved password
manager or offline vault, not in this repository.

| Value | Example or rule |
| --- | --- |
| Hostinger account owner | Named person or client entity responsible for the subscription |
| Hostinger billing term | Record total prepaid price and renewal price; do not rely only on the advertised monthly equivalent |
| AWS account ID | Existing developer-owned AWS account |
| Public domain | One final HTTPS origin without a path or trailing slash |
| DNS provider | Provider that controls the public domain records |
| Developer public IP | Source address allowed to use SSH during commissioning |
| Developer alert email | Receives operational, backup, and billing alerts |
| Client custodian 1 | Named person with an individual recovery identity and MFA |
| Client custodian 2 | Named person with an individual recovery identity and MFA |
| Client alert emails | Receive the 12:00 unresolved-backup escalation |
| Password manager or vault | Holds recoverable operational secrets |
| External monitoring provider | Better Stack; five-minute `GET /api/health`; alert after three consecutive failures |
| Maintenance window | Friday 08:30–10:30 `America/Sao_Paulo`; at least 72 hours notice; human-approved and automation-executed |
| Rollback window | At least 14 days and through two subsequently verified production releases, whichever is longer |

Stop before public release if the domain, alert recipients, recovery custodians,
or secret custody are unresolved.

## Expected operations layout

Implementation should provide this versioned layout or an equivalently clear
layout:

```text
operations/
├── ansible/
│   ├── inventory/
│   │   └── production.yml
│   ├── requirements.yml
│   ├── roles/
│   └── playbooks/
│       ├── provision-host.yml
│       ├── configure-aws-backup.yml
│       ├── deploy.yml
│       ├── rollback.yml
│       ├── verify.yml
│       └── restore-test.yml
├── composition/
│   ├── compose.production.yml
│   ├── Caddyfile
│   └── environment-template
├── deployment/
│   ├── deploy
│   ├── rollback
│   ├── verify
│   └── release-manifest.schema.json
└── recovery/
    ├── backup
    ├── restore
    └── verify-restoration
```

The exact commands below assume these names. If implementation chooses another
layout, update this runbook in the same change.

## 1. Prepare the developer workstation

### 1.1 Use WSL for Ansible

Run Ansible from an Ubuntu WSL distribution on the Windows developer workstation.
Windows without WSL is not an Ansible control node.

Install and record fixed versions of:

- Ansible or `ansible-core`;
- Python and `pipx`;
- OpenSSH client;
- AWS CLI v2;
- `age`;
- Git; and
- JSON and YAML validation tools used by the operations project.

From WSL, verify the tools:

```bash
ansible --version
ansible-playbook --version
ssh -V
aws --version
age --version
git --version
```

Install the pinned Ansible collections from the repository:

```bash
cd /mnt/c/Users/Eduardo/GAM/gam-api
ansible-galaxy collection install -r operations/ansible/requirements.yml
```

### 1.2 Create a dedicated SSH key

Create a dedicated key for production administration. Do not reuse a personal
GitHub signing or authentication key.

```bash
ssh-keygen -t ed25519 -a 100 -f ~/.ssh/gam_production -C "gam-production"
```

Store the private key only on the developer workstation and its approved backup.
Upload only the public key to Hostinger and the provisioned operations account.

### 1.3 Prepare secret input outside the repository

Create an ignored, permission-restricted input file outside the repository, for
example:

```text
~/.config/gam/production-secrets.yml
```

It may supply Ansible with the database password, JWT signing secret, GHCR pull
credential, AWS writer credential, and Better Stack monitoring token. The
commissioning operator CIDR allowlist belongs in versioned inventory rather than
in this secret file. The file must not contain either `age` recovery private key.

Set restrictive permissions:

```bash
chmod 600 ~/.config/gam/production-secrets.yml
```

Generate every secret independently. Never copy development passwords, fixture
passwords, JWT secrets, or database credentials into production.

## 2. Prepare recovery-key custody

Create two independent `age` recipients:

1. a developer-controlled recipient; and
2. a client-controlled recipient recoverable by both named client custodians.

Each owner generates and stores their private identity outside Hostinger, AWS,
Git, Ansible, CI/CD, email, and project documentation. Exchange and record only
the public recipients and fingerprints.

Before provisioning production backups:

- verify that the developer can decrypt a harmless test artifact;
- verify that a client custodian can decrypt the same harmless artifact;
- record the public fingerprints in the approved operations variables; and
- record where each private identity can be recovered without recording the
  private identity itself.

Do not proceed when only one recipient is usable.

## 3. Purchase Hostinger KVM 2

Hostinger changes promotional prices and commitment options. At checkout, record
the total charged amount, term, renewal amount, taxes, refund conditions, and
included services.

1. Sign in to the approved Hostinger account.
2. Select the ordinary VPS product, not a managed application panel.
3. Select **KVM 2**.
4. Confirm the displayed resources are at least 2 vCPU, 8 GB RAM, 100 GB NVMe,
   and 8 TB transfer. Stop if the product changed materially.
5. Select the approved billing term after comparing the total initial payment
   and renewal amount.
6. Select a Brazilian data-center location. Stop if Brazil is unavailable.
7. Choose the plain, minimal **Ubuntu Server 24.04 LTS** image.
8. Do not select a desktop image, CloudPanel, cPanel, Docker application template,
   or another preconfigured application stack.
9. Complete payment and save the invoice and renewal date.
10. Record the VPS public IPv4 address, IPv6 status, plan, region, operating-system
    image, and purchase date in the private operations record.

Hostinger snapshots and included backups are supplementary. They do not replace
the approved AWS database backups.

## 4. Establish initial Hostinger access

1. Add `~/.ssh/gam_production.pub` through hPanel during onboarding or under
   **VPS → Manage → Settings → SSH keys**.
2. Store the initial root password in the approved password manager.
3. Connect once using the hPanel browser terminal and confirm the installed OS:

   ```bash
   cat /etc/os-release
   uname -a
   timedatectl
   ```

4. From WSL, verify the server's SSH host fingerprint through an independent
   hPanel session, then connect:

   ```bash
   ssh -i ~/.ssh/gam_production root@<VPS_IPV4>
   ```

5. In the Hostinger managed firewall, allow:
   - TCP 22 only from the developer's current public IP;
   - TCP 80 from the internet; and
   - TCP 443 from the internet.
6. Deny every other unsolicited inbound connection. Do not expose PostgreSQL,
   the backend port, Docker API, metrics ports, or administrative dashboards.
7. Keep the hPanel browser terminal available until the operations account and
   host firewall have been verified, so an SSH mistake can be recovered.

The Ansible playbook, not a sequence of undocumented manual commands, performs
the permanent host configuration.

## 5. Configure DNS and the commissioning gate

1. Create an `A` record for the final GAM hostname pointing to `<VPS_IPV4>`.
2. Create an `AAAA` record only when IPv6 is intentionally configured and both
   provider and host firewalls enforce the same policy.
3. Use a short DNS TTL during commissioning, such as 300 seconds.
4. Confirm the record resolves to the VPS before asking Caddy to obtain a public
   certificate.
5. Keep ports 80 and 443 reachable so Caddy can complete public certificate
   issuance and renewal.

During burn-in, Caddy shall apply the Ansible-controlled commissioning gate to all
GAM routes. Only explicitly configured operator CIDRs may pass. Every other HTTPS
request, including `GET /api/health`, shall receive a static `503 Service
Unavailable` response with `Cache-Control: no-store` and no application or
infrastructure details. Caddy's HTTP-to-HTTPS redirect and certificate issuance
or renewal shall remain active. Do not use HTTP Basic authentication.

The gate is temporary access control, not a substitute for GAM authentication.
Disable it only through versioned automation after explicit Developer approval
and a passing readiness checklist. The launch procedure shall then verify
`GET /api/health` from outside the VPS; if verification fails, automation shall
re-enable the gate before remediation.

## 6. Secure the existing AWS account

Perform these manual account steps before running AWS automation:

1. Confirm the AWS account and billing identity are developer-owned as approved.
2. Confirm AWS Basic Support; do not purchase Business Support.
3. Confirm the root identity has no access keys.
4. Register two independent MFA methods for root.
5. Use a named, MFA-protected administrative identity for routine work.
6. Update and verify the account recovery email and telephone number.
7. Identify the two named client custodians and their individual email addresses.
8. Confirm the developer and client alert email addresses.

Never place root or developer-administrator credentials on the VPS.

## 7. Provision AWS backup resources

Run the AWS playbook from WSL using the named administrative identity. Review the
plan or check-mode output before applying it.

```bash
cd /mnt/c/Users/Eduardo/GAM/gam-api
ansible-playbook -i operations/ansible/inventory/production.yml operations/ansible/playbooks/configure-aws-backup.yml --check --diff --extra-vars "@$HOME/.config/gam/production-secrets.yml"
ansible-playbook -i operations/ansible/inventory/production.yml operations/ansible/playbooks/configure-aws-backup.yml --extra-vars "@$HOME/.config/gam/production-secrets.yml"
```

The automation must create and verify:

- `gam-production-backups-<account-id>-sa-east-1`;
- `gam-production-backup-audit-<account-id>-sa-east-1`;
- `sa-east-1` placement with no cross-region replication;
- public-access blocking and bucket-owner-enforced ownership;
- S3 Versioning and Object Lock;
- SSE-S3 default encryption;
- Compliance retention of 31, 85, or 370 days on recovery objects;
- 400-day Compliance retention on CloudTrail audit objects;
- lifecycle transitions to Standard-IA and Glacier Flexible Retrieval without
  leaving Brazil;
- incomplete multipart-upload cleanup after seven days;
- CloudTrail data events for the recovery bucket with log integrity validation;
- a dedicated non-console `gam-production-backup-writer` identity;
- source-IP, TLS, bucket, prefix, action, and retention restrictions for the
  writer;
- two named, MFA-protected, console-only client recovery identities;
- EventBridge, Lambda, SNS, and CloudWatch monitoring for the 04:30 and 12:00
  backup checks;
- the approved cost-allocation tags; and
- no delete, retention-bypass, IAM, billing, or decryption permission for the
  writer.

Object Lock Compliance retention cannot be shortened, even by AWS root. Review
bucket names, region, lifecycle, and retention values before the first production
object is uploaded.

Manually complete and verify:

1. client password setup and MFA enrollment;
2. SNS email-subscription confirmations;
3. a monthly GAM-filtered budget with US$5 warning, US$10 reassessment, and
   US$25 critical notifications;
4. Cost Anomaly Detection with a US$5 impact alert; and
5. cost-allocation-tag activation.

Cost alerts must notify only. They must not stop backups or delete retained data.

## 8. Provision the VPS with Ansible

Add the VPS IP and public hostname to the production inventory. First validate
connectivity:

```bash
cd /mnt/c/Users/Eduardo/GAM/gam-api
ansible production -i operations/ansible/inventory/production.yml -m ping
```

Review and apply the host playbook:

```bash
ansible-playbook -i operations/ansible/inventory/production.yml operations/ansible/playbooks/provision-host.yml --check --diff --extra-vars "@$HOME/.config/gam/production-secrets.yml"
ansible-playbook -i operations/ansible/inventory/production.yml operations/ansible/playbooks/provision-host.yml --extra-vars "@$HOME/.config/gam/production-secrets.yml"
```

The playbook must configure and verify:

- a non-root operations user with `sudo` and the dedicated SSH key;
- root SSH login disabled after the operations user is proven;
- password SSH login disabled;
- SSH restricted by provider and host firewall policy;
- current OS security updates and automatic security updates;
- time synchronization and `America/Sao_Paulo` operational scheduling;
- Docker Engine from Docker's official Ubuntu repository;
- the Docker Compose plugin, not the legacy standalone binary;
- Docker firewall integration so published container ports cannot bypass policy;
- only Caddy publishing host ports 80 and 443;
- production directories, ownership, permissions, and persistent volumes;
- container resource limits, health checks, restart policy, and log rotation;
- Caddy configuration and persistent certificate state;
- production-only environment files with mode `0600`;
- GHCR pull credentials with read-only package scope;
- the AWS writer credential in a root-owned mode-`0600` file;
- backup, restore, deployment, rollback, maintenance, and verification commands;
- the persistent 03:15 backup timer;
- the metrics-only Better Stack collector for host and service monitoring; and
- an exclusive deployment lock.

Rerun the playbook. The second successful run should report no unexplained
changes.

After the operations account is verified, stop using root for routine work.

## 9. Build and publish candidate artifacts

### 9.1 Backend

For every candidate deployed during burn-in:

1. merge or select a reviewed source commit;
2. run the canonical backend verification, including OpenAPI generation;
3. build the JAR;
4. build the non-root production OCI image;
5. scan the image and its dependencies;
6. publish it to private GHCR using CI's repository-scoped `GITHUB_TOKEN`;
7. record the source commit, version, image digest, Java runtime, and scan result;
8. keep the previous compatible digest available; and
9. deploy by digest, never by `latest` or another mutable tag.

Before approving an image build, run the backend gate from PowerShell with Docker
available:

```powershell
rtk test .\mvnw.cmd verify -Popenapi
```

Require a successful Maven result and inspect `target/openapi/openapi.yaml`.
Publishing automation must repeat the required verification in CI; a local pass
alone does not authorize publication.

The resulting reference has this form:

```text
ghcr.io/<namespace>/gam-api@sha256:<digest>
```

The VPS receives only a GHCR credential with `read:packages` access.

### 9.2 Frontend

From the separate frontend repository:

1. select the compatible source commit and run its tests and production build;
2. publish a non-prerelease immutable GitHub Release whose tag is the frontend
   version;
3. attach exactly `gam-frontend-<tag>.tar.gz` and
   `gam-frontend-<tag>.tar.gz.sha256`;
4. write the checksum sidecar as one lowercase SHA-256, two spaces, the exact
   archive filename, and a terminating newline;
5. retain the archive, checksum, release metadata, and referenced fingerprinted
   assets through the complete rollback window.

The backend deployment manifest shall pin the frontend repository identifier,
tag, exact artifact filename, and expected lowercase SHA-256. The deployment
workflow shall use authenticated read-only GitHub access outside KVM 2 to verify
that the release is published, non-prerelease, and immutable; that the sidecar,
manifest, and computed checksums agree; and that the archive has `index.html` at
its root with no unsafe paths or entries. Ansible shall transfer only the verified
archive. Do not build the frontend on the VPS or select `latest`, branch archives,
or expiring GitHub Actions artifacts.

### 9.3 Release manifest

Create a release manifest containing at least:

- release-candidate identifier;
- backend source commit and immutable image digest;
- frontend source commit, version, and artifact checksum;
- expected Flyway migration version;
- `GAM_PUBLIC_ORIGIN`;
- database-migration rollback classification;
- creation and approval timestamps; and
- the previous compatible release manifest.

## 10. Perform the first candidate deployment

Production runtime configuration must include:

```text
SPRING_PROFILES_ACTIVE=production
GAM_PUBLIC_ORIGIN=https://<PUBLIC_HOSTNAME>
GAM_REQUEST_CORRELATION_MODE=TRUSTED_PROXY
GAM_ORATORIO_LOCATION_CODE=DBSM
APP_VERSION=<candidate-version>
SPRING_DATASOURCE_URL=<private PostgreSQL service URL>
SPRING_DATASOURCE_USERNAME=<production database user>
SPRING_DATASOURCE_PASSWORD=<production database password>
JWT_SECRET_KEY=<independent production signing secret>
```

Do not activate the `dev` profile, development migrations, development fixture,
Swagger write operations, or insecure cookies.

Run the deployment playbook with the selected release manifest:

```bash
ansible-playbook -i operations/ansible/inventory/production.yml operations/ansible/playbooks/deploy.yml --extra-vars "release_manifest=<approved-manifest-path>" --extra-vars "@$HOME/.config/gam/production-secrets.yml"
```

The deployment command must:

1. acquire the exclusive deployment lock;
2. record the currently deployed release;
3. validate the candidate manifest and compatible artifact pair;
4. confirm a current successful backup before any database-changing deployment;
5. pull the backend by digest and stage the frontend only after the release,
   sidecar, manifest, computed checksum, and archive-safety checks agree;
6. validate Docker Compose and Caddy configuration;
7. enable the maintenance response;
8. run Flyway as an explicit one-shot operation;
9. start PostgreSQL, backend, Caddy, and the selected frontend release;
10. wait for private health and readiness checks;
11. verify public HTTPS, routing, cookies, trusted proxy behavior, database access,
    representative API behavior through Caddy, and the exact `GET /api/health`
    status, JSON body, content type, and no-store policy;
12. leave the commissioning gate enabled;
13. record the release, migration, checks, and result; and
14. release the deployment lock.

After deployment, run the independent verification playbook:

```bash
ansible-playbook -i operations/ansible/inventory/production.yml operations/ansible/playbooks/verify.yml --extra-vars "release_manifest=<approved-manifest-path>"
```

## 11. Run the 10–15-day burn-in

Keep the commissioning gate enabled and use only synthetic production-like data.

For each candidate deployment:

- require explicit developer approval;
- deploy a tested immutable frontend/backend pair;
- record the release manifest and result;
- confirm migrations are backward-compatible or document the forward recovery;
- preserve the previous compatible pair;
- verify public HTTPS and private backend/database boundaries; and
- investigate failures through versioned corrections rather than manual VPS edits.

During the burn-in, demonstrate all of the following:

- normal developer login and representative browser flows;
- public `/` and `/api/*` routing through Caddy;
- production origin, secure cookie, CSRF, and trusted-proxy behavior;
- backend and PostgreSQL are not reachable directly from the internet;
- a host reboot preserves volumes, timers, Caddy state, and service startup;
- a database-changing deployment blocks when backup freshness is absent;
- application rollback restores the previous compatible release pair;
- resource use remains safe under representative load;
- filesystem warnings trigger at 80 percent and critical alerts at 90 percent;
- Better Stack sends `GET /api/health` every five minutes, requires `200 OK` and
  exactly `{"status":"UP"}`, and alerts after three consecutive failures;
- backup upload runs at 03:15 São Paulo time;
- AWS validates the backup at 04:30 and escalates an unresolved failure at 12:00;
- recovery notification follows a successful retry;
- certificate issuance and renewal storage work, Better Stack warns at 30 days
  remaining, and an invalid certificate alerts immediately;
- logs rotate and contain no credentials, tokens, cookies, or personal data;
- Ansible can reapply without unexplained drift; and
- an encrypted backup restores successfully into an isolated environment.

Reset synthetic test state before production initialization. If the developer
cannot prove that all temporary data, informal access, and manual configuration
have been removed, reinstall Ubuntu 24.04 and reprovision from Ansible.

## 12. Freeze the release and initialize production

Before introducing real data:

1. declare a code freeze for the version 1.0 candidate;
2. stop deploying unfinished feature work to the VPS;
3. confirm all migrations and the production Compose model are final for 1.0;
4. remove the synthetic database and recreate the production database using only
   production Flyway migrations;
5. deploy the frozen candidate by immutable digest and frontend checksum;
6. keep the commissioning gate enabled;
7. verify the empty production state;
8. create and verify a current encrypted off-host backup;
9. complete the required isolated restoration evidence; and
10. confirm both recovery recipients and every alert subscription.

Do not copy a development database or development fixture into production.

## 13. Run the Member-information import

The Member-information import is a one-time maintenance operation, not a scheduled
job. It must not run until the implementation satisfies the accepted import
requirements and the approved private input document exists.

### 13.1 Prepare the private input

Confirm the document:

- uses schema `gam-member-information-import/v1`;
- has `documentStatus = APPROVED`;
- has no unresolved review issue;
- contains the reviewed explicit UUID v7 identifiers;
- contains the declared canonical SHA-256 checksum; and
- is stored outside Git, images, CI artifacts, email, and application resources.

Transfer it through the approved encrypted administrative channel into a
temporary root- or operations-owned directory such as `/run/gam-private/`.
Set directory mode `0700` and file mode `0400`. The file must be mounted read-only
into the one-shot maintenance container and must never enter ordinary backend or
PostgreSQL logs.

### 13.2 Validate without mutation

The implementation must provide this stable operator interface or update this
runbook with its replacement:

```bash
sudo /opt/gam/bin/gam-maintenance member-info-import validate --file /run/gam-private/member-information-2026.json
```

Require exit code zero and a safe summary containing only the permitted batch,
checksum, count, action, and outcome information. Confirm no Member, annual
response, import batch, timestamp, or activity row changed.

Stop on any validation warning or collision. Correct the approved input or the
database state through its governed process, then validate again. Never skip a
record or edit production rows to force the import.

### 13.3 Apply atomically

Confirm the current off-host backup is successful and locked. Then run:

```bash
sudo /opt/gam/bin/gam-maintenance member-info-import apply --file /run/gam-private/member-information-2026.json --actor-reference <DEVELOPER_REFERENCE> --reason <NORMALIZED_MAINTENANCE_REASON>
```

Require exit code zero. Verify:

- exactly one immutable import batch was created;
- imported Member and annual-response counts match the approved document;
- every imported Member is Account-less;
- exactly one `MEMBER_INFORMATION_IMPORTED` activity exists;
- no personal value, path, checksum, or per-record data entered activity metadata;
- ordinary web application ports were never opened by the maintenance container;
  and
- a repeated apply would be a verified no-op under the accepted idempotency rules.

Remove the temporary private file and maintenance container immediately after
verification. Confirm it is absent from shell history, logs, container layers,
volumes, backups outside the database, and the repository.

Create and verify the next production recovery artifact so the imported data is
included in the accepted off-host recovery set.

## 14. Create initial operational access

If the version 1.0 operating model needs a developer SUDO Account:

1. register the intended Account through the normal production registration
   flow while the commissioning gate is enabled;
2. verify control of its login email;
3. use the one-shot maintenance interface to assign SUDO with an explicit reason;
4. verify the resulting role and activity record; and
5. do not leave a general administration endpoint or reusable maintenance
   container running.

Expected operator interface:

```bash
sudo /opt/gam/bin/gam-maintenance sudo assign-sudo --account-email <DEVELOPER_LOGIN_EMAIL> --reason <NORMALIZED_MAINTENANCE_REASON>
```

Other maintenance jobs, including soft-delete inspection, restoration, and hard
deletion, are not deployment steps and must run only for their documented
operational purpose.

## 15. Release version 1.0

Release only when every production-readiness check is green.

1. Confirm the exact frozen backend digest and frontend checksum.
2. Confirm the public domain, TLS certificate, and DNS records.
3. Confirm a successful, locked recovery point no older than 24 hours.
4. Confirm the isolated restoration evidence.
5. Confirm the 04:30 backup monitor, 12:00 escalation, external availability
   monitor, host alerts, and billing alerts.
6. Confirm the previous compatible release pair and rollback command.
7. Confirm the database-migration rollback classification.
8. Run the final deployment and verification playbooks.
9. Record explicit Developer launch approval and disable the temporary Caddy
   commissioning gate through versioned Ansible configuration.
10. From outside the VPS, verify unauthenticated `GET /api/health` returns
    `200 OK`, `Content-Type: application/json`, `Cache-Control: no-store`, and
    exactly `{"status":"UP"}`; then verify the application from a clean browser
    and an external network.
11. Verify the backend and PostgreSQL remain unreachable directly.
12. Verify registration, login, refresh, logout, CSRF, representative reads, and
    authorized writes.
13. Verify the imported Member counts and a non-sensitive representative lookup.
14. Record explicit developer approval of the production release.
15. Announce availability to the client.

Enable HSTS only after public HTTPS and renewal behavior are proven and the
versioned Caddy configuration includes the intended policy.

## 16. Roll back or stop a failed release

If verification fails before the commissioning gate is disabled, keep the gate
enabled and do not announce the release. If first-launch verification fails after
the gate is disabled, automation shall re-enable it before remediation.

For an application-only or backward-compatible change:

```bash
ansible-playbook -i operations/ansible/inventory/production.yml operations/ansible/playbooks/rollback.yml --extra-vars "release_manifest=<previous-approved-manifest>" --extra-vars "@$HOME/.config/gam/production-secrets.yml"
```

For an incompatible database migration, do not merely redeploy the old image.
Use the documented forward correction or isolated backup-based recovery plan.

After rollback:

- verify public and private health;
- record the failed and restored release manifests;
- preserve relevant non-sensitive logs;
- confirm backup and monitoring timers remain active; and
- create a normal reviewed correction before another deployment.

## 17. Recurring production operations

| Frequency | Operation |
| --- | --- |
| Every 5 minutes | Better Stack sends `GET /api/health`, requires `200 OK` and exactly `{"status":"UP"}`, and alerts after three consecutive failures |
| Daily 03:15 São Paulo | Online PostgreSQL recovery artifact, encryption, upload, validation, and Compliance retention |
| Daily 04:30 São Paulo | Independent AWS backup-object validation and developer alert |
| Daily 12:00 São Paulo | Escalate unresolved backup failure to both client custodians |
| Continuously | Better Stack metrics-only proxy, backend, database, container, CPU, memory, swap, filesystem, inode, network, and TLS monitoring; warn at 30 certificate days and alert immediately on invalidity |
| Each deployment | Explicit Developer approval, fresh-backup check when applicable, immutable release manifest, automated verification, and rollback evidence |
| Each disruptive maintenance | Friday 08:30–10:30 `America/Sao_Paulo`, announced at least 72 hours ahead and started only by explicit Developer approval |
| At least every 90 days | Rotate the dedicated AWS writer access key |
| After security updates | Reboot when required and verify automatic service recovery |
| Annually | Scripted isolated data restoration |
| After PostgreSQL major, backup-format, encryption, or recovery-key change | Immediate isolated restoration validation |
| After client-key rotation | Validate the client recovery recipient |

The Member-information import is deliberately absent from this schedule because
it is a one-time approved maintenance action.

## 18. Evidence to retain

Keep a sanitized private operations record containing:

- Hostinger plan, billing term, invoice, renewal date, region, and VPS identifiers;
- OS, Docker, Compose, Ansible, Caddy, PostgreSQL, and image versions;
- SSH host fingerprints and authorized public-key fingerprints;
- public domain and DNS record values;
- AWS bucket, trail, monitor, Lambda, SNS, budget, and IAM identity identifiers;
- public `age` recipient fingerprints;
- Ansible run results without secret values;
- deployment and rollback manifests;
- backup object key, version ID, checksum, classification, and retain-until date;
- restoration result and duration without personal data;
- alert-delivery test evidence;
- Member-information import batch UUID, permitted counts, and outcome;
- version 1.0 approval time; and
- known risks and follow-up actions.

Never place passwords, private keys, access keys, JWT secrets, database passwords,
refresh tokens, private Member data, or decrypted backups in this record.

## Stop conditions

Stop deployment or release when any of these conditions is true:

- required infrastructure exists only as a manual VPS change;
- the backend or PostgreSQL is publicly reachable;
- the deployment uses a mutable image tag;
- `dev` profile or development migrations are active;
- TLS, secure-cookie, origin, CSRF, or trusted-proxy verification fails;
- no current successful off-host backup exists before a risky database change;
- Object Lock, encryption recipients, audit logging, or backup monitoring is not
  verified;
- the isolated restoration has not succeeded;
- client recovery custody is incomplete;
- the Member-information input is not approved or validation reports a collision;
- production data was used during ordinary burn-in;
- the previous compatible release cannot be identified; or
- the developer has not explicitly approved version 1.0.

## Authoritative project references

- [Production Operations](../requirements/platform/production-operations.md)
- [Production Backup and Recovery](../requirements/platform/production-backup-and-recovery.md)
- [Web Delivery and Frontend Contract](../requirements/platform/web-delivery-and-frontend-contract.md)
- [Member Information Import and Account Linking](../requirements/members/member-information-import-and-account-linking.md)
- [ADR-0024: Deploy Production Directly to Hostinger KVM 2](../decisions/0024-deploy-production-directly-to-hostinger-kvm-2.md)
- [ADR-0025: Use AWS São Paulo for Immutable Encrypted Production Backups](../decisions/0025-use-aws-sao-paulo-for-immutable-encrypted-production-backups.md)
- [ADR-0026: Use an Isolated Member-information Import](../decisions/0026-use-isolated-member-information-import-with-explicit-account-linking.md)
- [ADR-0028: Complete the Initial Production Commissioning and Release Contracts](../decisions/0028-complete-initial-production-commissioning-and-release-contracts.md)

## Official operational references

- [Hostinger VPS plans](https://www.hostinger.com/br/servidor-vps)
- [Hostinger VPS dashboard](https://www.hostinger.com/support/5726606-how-to-use-the-vps-dashboard-in-hostinger/)
- [Hostinger SSH keys](https://www.hostinger.com/support/4792364-how-to-use-ssh-keys-at-hostinger-vps/)
- [Hostinger managed VPS firewall](https://www.hostinger.com/support/8172641-how-to-use-a-managed-vps-firewall-at-hostinger/)
- [Ansible installation and control-node requirements](https://docs.ansible.com/projects/ansible-core/devel/installation_guide/intro_installation.html)
- [Docker Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/)
- [Caddy HTTPS prerequisites](https://caddyserver.com/docs/quick-starts/https)
- [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [Amazon S3 Object Lock](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock.html)
- [CloudTrail S3 object data events](https://docs.aws.amazon.com/AmazonS3/latest/userguide/enable-cloudtrail-logging-for-s3.html)
- [AWS root-user security](https://docs.aws.amazon.com/IAM/latest/UserGuide/root-user-best-practices.html)
- [AWS cost budgets](https://docs.aws.amazon.com/cost-management/latest/userguide/create-cost-budget.html)
- [AWS Cost Anomaly Detection](https://docs.aws.amazon.com/cost-management/latest/userguide/getting-started-ad.html)
