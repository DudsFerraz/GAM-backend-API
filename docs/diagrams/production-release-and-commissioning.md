# Production Release and Commissioning

This diagram shows the accepted cross-repository artifact, approval, commissioning, verification, and rollback boundaries. It supplements the written requirements; it does not replace their exact status, checksum, timing, or access rules.

```mermaid
flowchart LR
    Developer["Developer<br/>select pair and approve"]
    FrontendRepo["Frontend repository"]
    FrontendRelease["Immutable GitHub Release<br/>archive and SHA-256"]
    BackendRepo["Backend repository<br/>deployment manifest and Ansible"]
    GHCR["Private GHCR<br/>backend image by digest"]
    Verify{"Release, checksum,<br/>archive, and backup checks pass?"}

    subgraph Hostinger["Hostinger KVM 2 - Ubuntu 24.04"]
        Caddy["Caddy<br/>commissioning gate and maintenance response"]
        Static["Versioned static frontend releases"]
        Backend["Backend image by digest"]
        Database[("PostgreSQL 18")]
        Collector["Better Stack metrics-only collector"]

        Caddy --> Static
        Caddy -->|"/api/*"| Backend
        Backend --> Database
    end

    BetterStack["Better Stack<br/>external health, TLS, host alerts"]
    Previous["Previous compatible pair<br/>14 days and two verified releases"]

    FrontendRepo --> FrontendRelease
    BackendRepo --> Verify
    FrontendRelease --> Verify
    GHCR --> Verify
    Developer --> BackendRepo
    Verify -->|"No"| Stop["Stop before changing production"]
    Verify -->|"Yes, through Ansible"| Static
    Verify -->|"Yes, selected digest"| Backend
    Developer -->|"readiness approval disables gate"| Caddy
    BetterStack -->|"GET /api/health every five minutes"| Caddy
    Collector --> BetterStack
    Static -. "retain" .-> Previous
    Backend -. "retain" .-> Previous
```

Related documentation:

- [Web Delivery and Frontend Contract](../requirements/platform/web-delivery-and-frontend-contract.md)
- [Production Operations](../requirements/platform/production-operations.md)
- [ADR-0028: Complete the Initial Production Commissioning and Release Contracts](../decisions/0028-complete-initial-production-commissioning-and-release-contracts.md)
