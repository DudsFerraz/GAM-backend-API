# 6. Repeatable provisioning: you should not configure it manually twice

You will operate two separate VPS instances, but you should not perform two separate manual configurations.

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

Use Terraform where the provider support is dependable. Where it is not, document the small number of manual provider-console operations.

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

The same Docker Compose and Caddy configuration should run on both VPSs with only environment-specific secrets and origin values changing.

## Migration rehearsal

At the end of the validation month:

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
