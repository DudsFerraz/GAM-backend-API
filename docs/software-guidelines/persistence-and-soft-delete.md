# Persistence and Soft Delete Guidelines

## 1. Purpose

**Soft delete is an internal security and safety mechanism, not a user-facing feature.** Its purpose is to protect the system from mistaken deletions, abusive administration, and irreversible data loss. Normal users and administrators interact with the system using domain actions (e.g., *deactivate*, *cancel*), while historical facts are preserved in the database.

Cross-domain behavioral defaults are defined by [`REQ-PERSISTENCE-001` through `REQ-PERSISTENCE-012`](../requirements/platform/persistence-auditing-and-soft-delete.md). An owning Accepted Requirement Specification may define an explicit exception. [ADR-0018](../decisions/0018-standardize-persistence-auditing-soft-deletion-and-relationship-enforcement.md) documents the supporting persistence strategy.

---

## 2. Core Architecture Rules

### 2.1. Soft Delete Visibility and Repositories

Soft-deleted rows must be strictly hidden from normal application reads.

* **Mechanism:** Every soft-deletable entity uses `@SQLRestriction` (or the standardized framework equivalent) to automatically filter out deleted rows.
* **Repository API:** Normal application repositories (`BaseRepository`) do **not** expose dangerous physical-delete, batch-delete, unrestricted deleted-row query, or generic restore operations. A narrowly scoped persistence port may bypass active-row filtering only when an owning Accepted Requirement Specification defines a historical view or restoration workflow.
* **Cascading:** Soft delete does not automatically cascade to historical child records unless explicitly defined by a specific domain policy.

### 2.2. Unique Values After Soft Delete

By default, only active rows reserve unique values.

* **Mechanism:** Tables use partial unique indexes (e.g., `UNIQUE (email) WHERE deleted_at IS NULL`).
* This allows a user to reuse an email address, role name, or event pairing if the previous record was soft-deleted.
* **Explicit exceptions:** An owning Accepted Requirement Specification may require deleted rows to keep reserving an identity. For example, `REQ-ORATORIANO-002` reserves a human-equivalent Oratoriano name across active and deleted records, while `REQ-MEMBER-001` preserves one lifetime Member identity per Account.

### 2.3. Developer Maintenance Tooling

Generic restoration, hard deletion, and unrestricted browsing of soft-deleted records are inaccessible through the HTTP API. These actions are restricted to developer-controlled maintenance jobs using the `maintenance` Spring profile.

An owning Accepted Requirement Specification may define a narrowly authorized domain restoration workflow. Such a workflow is an explicit exception, not a generic deleted-row API. `REQ-ORATORIANO-010` defines one such restoration workflow.

```powershell
# Example: Inspect deleted rows
mvn spring-boot:run "-Dspring-boot.run.profiles=maintenance" "-Dspring-boot.run.arguments=--maintenance.action=inspect-soft-deleted --maintenance.table=members"

# Example: Restore a row (requires manual resolution if a unique constraint conflicts)
mvn spring-boot:run "-Dspring-boot.run.profiles=maintenance" "-Dspring-boot.run.arguments=--maintenance.action=restore --maintenance.table=members --maintenance.id=<uuid> --maintenance.reason='Restored after developer review'"
```

---

## 3. User-Facing Lifecycle and Removal Actions

The UI and application workflows must use the owning domain action. Deactivation and cancellation are lifecycle transitions and must not be implemented as soft deletion. Soft deletion represents deliberate removal from ordinary identity and visibility only when an Accepted Requirement Specification defines that workflow.

| Persistence or lifecycle mechanism | Domain action exposed to the User |
|------------------------------------|-----------------------------------|
| Member lifecycle transition        | **Deactivate** or **reactivate** a Member |
| Event lifecycle transition         | **Cancel** or **reopen** an Event |
| Soft-delete an Event               | **Remove** a mistaken Event when its owning requirement permits |
| Soft-delete a Presence             | **Remove** mistaken attendance |
| Soft-delete a custom Role          | **Remove** the custom Role when its owning requirement permits |

---

## 4. Entity Policy Archetypes

Different entities follow different lifecycle rules regarding deletion.

### 4.1. People and Identity (`Account`, `Member`, `Oratoriano`)

**Rule: Preserve lifetime identity and follow the owning lifecycle.**

* Accounts and Members use their owning activation or deactivation lifecycle instead of soft deletion to represent current participation.
* Oratoriano active/inactive status is currently out of scope under [`REQ-ORATORIANO-003`](../requirements/oratorianos/oratoriano-records.md#req-oratoriano-003-ordinary-profile).
* [`REQ-ORATORIANO-009`](../requirements/oratorianos/oratoriano-records.md#req-oratoriano-009-soft-deletion) explicitly permits correction of an erroneous Oratoriano through protected soft deletion and defines which attendance and form relationships are preserved, removed, or blocking.
* [`REQ-ORATORIANO-010`](../requirements/oratorianos/oratoriano-records.md#req-oratoriano-010-restoration) owns the corresponding domain restoration workflow.

### 4.2. Events (`Event`, `Missa`, `Oratorio`)

**Rule: Cancel real Events; use owning correction rules for mistaken records.**

* Cancellation and soft deletion are distinct: cancellation records that an Event will not proceed, while protected soft deletion corrects an Event record that should not remain in ordinary identity and visibility.
* Deletion eligibility, lifecycle state, required reason, relationship blockers, and historical projections shall come from the owning Accepted Requirement Specification. This guideline does not impose a generic correction window.
* [`REQ-EVENT-019`](../requirements/events/event-records-and-generic-lifecycle.md#req-event-019-protected-generic-event-deletion-after-presence-correction) defines protected Generic Event deletion after Presence correction.
* [`REQ-ORATORIO-010`](../requirements/oratorio/oratorio-occurrences-and-planning.md#req-oratorio-010-protected-deletion) defines protected Oratorio deletion, including its accepted lifecycle states, active-attendance blockers, and preservation of removed attendance history.

### 4.3. RBAC & Configuration (`Role`, `Permission`, `GamLocation`)

**Rule: Protect system data; allow removal of unused custom data.**

* Seeded baseline roles and permissions contain a `systemManaged` marker and can **never** be deleted.
* User-created roles and permissions can be soft-deleted only if no active assignments depend on them.
* GamLocations can only be soft-deleted if they are not referenced by any Event, including historical or soft-deleted Events.

### 4.4. Security Artifacts (`RefreshToken`)

**Rule: Hard delete always.**

* Refresh tokens are security/session artifacts, not business history. They do not possess soft-delete columns. They are hard-deleted upon logout, rotation, or expiration.

### 4.5. Join Tables and Assignments

**Rule: Lifecycle is tied to the aggregate.**

* Simple join tables without entities (e.g., `oratorio_lanche`) do not use soft delete. Their lifecycle is managed entirely by the owning aggregate.
* Rich assignment entities (e.g., `AccountRole`, `RolePermission`, `Presence`) use soft delete to preserve security and attendance history. Re-adding the same relationship later creates a completely new row rather than reusing the old one.

### 4.6. Draft Signed Attachments

**Rule: Transient while draft; historical after completion.**

* Signed attachments owned by an Oratoriano additional-form `DRAFT` are transient working records under [`REQ-ORATORIANO-FORM-UPLOAD-004`](../requirements/oratorianos/incremental-signed-attachment-uploads.md#req-oratoriano-form-upload-004-transient-draft-retention-and-historical-completion-boundary) and [ADR-0034](../decisions/0034-treat-signed-attachments-as-transient-until-form-completion.md).
* Individual removal, successful full replacement, and owning-draft deletion physically delete the affected draft attachment rows and bytes through the authorized aggregate workflow. This exception must not expose a generic repository hard-delete API.
* The active collection gains historical meaning when the form completes. Attachments owned by `COMPLETED`, `SUPERSEDED`, or `REVOKED` forms remain immutable and protected from ordinary physical or soft deletion.
