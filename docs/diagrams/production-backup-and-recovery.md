# Production Backup and Recovery

This diagram supports the accepted production backup requirements and AWS backup ADR. It distinguishes write-only production automation, independent monitoring, immutable audit, and human recovery custody.

```mermaid
flowchart LR
    subgraph Hostinger["Hostinger KVM 2 - Brazil"]
        DB[("PostgreSQL 18")]
        Timer["systemd timer - 03:15 BRT"]
        Build["Dump, validate, manifest, checksums"]
        Encrypt["age encryption for two recipients"]
        Writer["gam-production-backup-writer"]

        Timer --> Build
        DB --> Build
        Build --> Encrypt
        Encrypt --> Writer
    end

    subgraph AWS["Existing developer AWS account - sa-east-1"]
        Backup[("Private backup bucket\nCompliance WORM 31/85/370 days")]
        Scheduler["EventBridge - 04:30 and 12:00 BRT"]
        Monitor["Lambda metadata validator"]
        Alerts["SNS alerts and recovery notices"]
        Trail["CloudTrail object-access events"]
        Audit[("Private audit bucket\nCompliance WORM 400 days")]

        Scheduler --> Monitor
        Monitor --> Backup
        Monitor --> Alerts
        Trail --> Audit
    end

    Writer -- "Encrypted upload only" --> Backup
    Backup -. "Read and write events" .-> Trail

    subgraph Custody["Independent recovery custody"]
        Developer["Developer private age identity"]
        Client["Client private age identity\nTwo named custodians"]
        ClientIAM["Two named read-only IAM identities"]
    end

    Backup -- "Encrypted recovery artifact" --> Restore["Isolated restore environment"]
    ClientIAM --> Backup
    Developer --> Restore
    Client --> Restore
    Restore --> Verify["Integrity and application verification"]
    Verify --> Destroy["Destroy temporary plaintext and restored data"]
```

Related documentation:

- [Production Backup and Recovery Requirement Specification](../requirements/platform/production-backup-and-recovery.md)
- [ADR-0025: Use AWS São Paulo for immutable encrypted production backups](../decisions/0025-use-aws-sao-paulo-for-immutable-encrypted-production-backups.md)
