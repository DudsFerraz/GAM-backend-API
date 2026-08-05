# 6. Repeatable provisioning: rebuild without undocumented manual configuration

GAM will operate one Hostinger KVM 2 production VPS initially, but the host must remain reproducible for recovery, replacement, or migration. The accepted automation choice is Ansible only; Terraform is deferred.

Structure the deployment repository into four layers:

```text
operations/
├── infrastructure/
│   ├── vultr/
│   ├── hostinger/
│   └── variables/
├── ansible/
│   ├── inventory/
│   ├── roles/
│   └── playbooks/
├── composition/
│   ├── compose.yaml
│   ├── caddy/
│   └── environment-templates/
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

### Provider-specific layer

Keep this thin:

* VPS size
* Region
* OS image
* Public IP
* Provider firewall
* DNS records
* Optional provider snapshot schedule

Use Ansible for the accepted automated configuration surface. Document the small number of provider-account, billing, MFA, purchase, region, operating-system image, initial SSH, firewall, and email-confirmation operations that remain manual. Do not introduce Terraform state for the initial deployment.

### Provider-neutral host configuration

Use Ansible or an equivalent idempotent system to configure:

* Operations user
* SSH keys
* SSH daemon hardening
* Docker Engine and Compose
* Firewall integration
* Automatic security updates
* Time synchronization
* Directories and permissions
* Log rotation
* Backup timers
* Monitoring agent
* Deployment scripts

### Application composition

The same Docker Compose and Caddy configuration should run on KVM 2 and any future isolated recovery or replacement environment, with only environment-specific secrets and origin values changing.

## Migration rehearsal

When GAM performs a future provider migration or full host replacement:

1. Create a backup.
2. Provision a completely new VPS.
3. Apply the host-configuration automation.
4. Restore production-like data.
5. Deploy explicit frontend/backend versions.
6. Verify using a temporary hostname or local host mapping.
7. Switch DNS.
8. Retain the previous server through the rollback window.
9. Destroy it only after successful verification.

This proves provider portability and disaster recovery simultaneously.
