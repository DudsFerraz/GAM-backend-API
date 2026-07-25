# Requirement: Oratorio Coordinator Designation

## Status

Accepted

## Context

Oratorio operations require a responsibility narrower than GAM-wide coordination. A Coordenador do Oratório must be able to operate the complete Oratorio module without receiving unrelated Coordinator authority, while every GAM Coordinator must retain Oratorio authority.

The role is an authorization projection of an active Member responsibility. It is not a separate person, Member status, or nested role.

## Functional requirements

### REQ-ORATORIO-COORD-001: System Role and Member identity

The RBAC catalog shall define the lifecycle-owned system Role `ORATORIO_COORD` with description `Oratorio operational responsibility for an active Member`.

A current Coordenador do Oratório shall be an active Member whose linked active Account has active `MEMBER` and `ORATORIO_COORD` assignments and no active `VISITOR` assignment. The same Member may also be a Coordinator and hold `COORD`.

`ORATORIO_COORD` shall not imply or assign `COORD`, and `COORD` shall not automatically assign `ORATORIO_COORD`.

---

### REQ-ORATORIO-COORD-002: Grant and revoke authority

The system shall expose:

| Method | Route | Purpose |
| --- | --- | --- |
| `PATCH` | `/members/{memberId}/oratorio-coordinator/grant` | Grant the designation |
| `PATCH` | `/members/{memberId}/oratorio-coordinator/revoke` | Revoke the designation |

Both operations shall require `ORATORIO_COORD_MANAGE` and shall not additionally require generic Account-role management.

Only the baseline `COORD` and `SUDO` bundles shall contain `ORATORIO_COORD_MANAGE`. The `ORATORIO_COORD`, `MEMBER`, and `VISITOR` bundles shall not contain it.

Granting shall require a consistent active-Member projection without an active `ORATORIO_COORD` assignment. Revoking shall require a current Coordenador do Oratório. Repeated or inconsistent transitions shall fail without repair.

---

### REQ-ORATORIO-COORD-003: Required reason and activity

Manual grant and revoke operations shall require a normalized reason containing 1 to 2,000 characters.

A successful grant shall emit one `ORATORIO_COORDINATOR_GRANTED` activity. A successful revoke shall emit one `ORATORIO_COORDINATOR_REVOKED` activity. Each activity shall target the affected Member and identify the linked Account, Role, transition, actor, and reason without emitting duplicate low-level Account-role activities.

The Role mutation and activity shall commit atomically.

---

### REQ-ORATORIO-COORD-004: Member deactivation and reactivation

Member deactivation shall remove an active `ORATORIO_COORD` assignment in the same transaction that changes the Member to inactive and projects `VISITOR`.

This automatic removal shall be represented by the existing high-level `MEMBER_DEACTIVATED` activity and shall not emit a separate Oratorio Coordinator revocation or Account-role removal activity.

Member reactivation shall restore `MEMBER` and remove `VISITOR`, but shall not restore a former `ORATORIO_COORD` designation.

---

### REQ-ORATORIO-COORD-005: Assignment cardinality and concurrency

GAM may have zero, one, or multiple current Coordenadores do Oratório. Revocation and Member deactivation shall not apply a final-holder protection.

Grant, revoke, activation, and deactivation operations affecting the same Member and Account shall serialize and leave one valid lifecycle projection. Concurrent duplicate grants or revokes shall produce at most one successful transition and one high-level activity.

---

### REQ-ORATORIO-COORD-006: Generic role-management boundary

Generic Account-role administration shall reject direct assignment or removal of `ORATORIO_COORD` because it is system-managed and lifecycle-owned. The dedicated Member-targeted designation workflow shall be its only ordinary owner.

---

### REQ-ORATORIO-COORD-007: Oratorio permission registry metadata

The RBAC registry shall add:

| Permission code | Label | Description |
| --- | --- | --- |
| `ORATORIO_GET` | `View Oratorios` | `Allows viewing specialized Oratorio details` |
| `ORATORIO_CREATE` | `Create Oratorios` | `Allows creating Oratorio occurrences` |
| `ORATORIO_MANAGE` | `Manage Oratorios` | `Allows managing Oratorio planning and lifecycle` |
| `ORATORIO_ATTENDANCE_GET` | `View Oratorio attendance` | `Allows viewing combined Member and Oratoriano attendance trackers` |
| `ORATORIO_ATTENDANCE_MANAGE` | `Manage Oratorio attendance` | `Allows recording and correcting Member and Oratoriano attendance` |
| `ORATORIO_COORD_MANAGE` | `Manage Oratorio coordinators` | `Allows granting and revoking Oratorio Coordinator designation` |
| `ORATORIANO_GET` | `View Oratorianos` | `Allows searching and viewing ordinary Oratoriano profiles` |
| `ORATORIANO_REGISTER` | `Register Oratorianos` | `Allows registering Oratorianos` |
| `ORATORIANO_MANAGE` | `Manage Oratorianos` | `Allows correcting, deleting, and restoring Oratoriano records` |
| `ORATORIANO_FORM_GET` | `View Oratoriano forms` | `Allows viewing sensitive Oratoriano form details` |
| `ORATORIANO_FORM_MANAGE` | `Manage Oratoriano forms` | `Allows creating and managing Oratoriano form versions` |
| `ORATORIANO_FORM_PDF_GENERATE` | `Generate Oratoriano form PDFs` | `Allows creating and rendering identified Oratoriano print snapshots` |
| `ORATORIANO_FORM_ATTACHMENT_GET` | `Download signed Oratoriano forms` | `Allows downloading signed Oratoriano form attachments` |

Permission codes shall be stable after acceptance. Registry and display metadata changes shall follow the accepted RBAC synchronization rules.

---

### REQ-ORATORIO-COORD-008: Reusable bundle composition

The code-defined `ORATORIO_READ` group shall contain only `ORATORIO_GET`.

The code-defined `ORATORIO_OPERATIONS` group shall contain `ORATORIO_READ` plus every permission in `REQ-ORATORIO-COORD-007` except `ORATORIO_COORD_MANAGE`.

Repeatable seeding shall flatten the groups into these direct role-permission links:

| Role | Oratorio permission composition |
| --- | --- |
| `SUDO` | Every accepted system permission |
| `COORD` | `ORATORIO_OPERATIONS` and `ORATORIO_COORD_MANAGE` |
| `ORATORIO_COORD` | `ORATORIO_OPERATIONS` |
| `MEMBER` | `ORATORIO_READ` |
| `VISITOR` | None |

The source groups shall not be persisted as roles and shall not create runtime role inheritance.

## Acceptance scenarios

```gherkin
Scenario: Coordinator grants Oratorio responsibility
  Given an active Member has MEMBER and does not have ORATORIO_COORD
  And the caller has ORATORIO_COORD_MANAGE
  When the caller grants the designation with a valid reason
  Then the Member's Account receives ORATORIO_COORD
  And one ORATORIO_COORDINATOR_GRANTED activity stores the reason

Scenario: Oratorio Coordinator cannot grant the role
  Given the caller has ORATORIO_COORD but lacks ORATORIO_COORD_MANAGE
  When the caller attempts to grant the designation
  Then the operation is forbidden

Scenario: Deactivation removes Oratorio responsibility
  Given an active Member has MEMBER and ORATORIO_COORD
  When the Member is deactivated
  Then MEMBER and ORATORIO_COORD are removed
  And VISITOR is assigned
  And only the MEMBER_DEACTIVATED activity represents the workflow

Scenario: Reactivation does not restore responsibility
  Given an inactive Member formerly held ORATORIO_COORD
  When the Member is reactivated
  Then MEMBER is restored
  And ORATORIO_COORD remains absent

Scenario: Final holder may be revoked
  Given exactly one current Coordenador do Oratório exists
  When an authorized Coordinator revokes the designation
  Then the revocation succeeds
  And zero current holders remain

Scenario: Seed flattens reusable groups
  Given the accepted Oratorio permission registry is configured
  When the repeatable RBAC seed runs
  Then COORD and ORATORIO_COORD receive every ORATORIO_OPERATIONS permission as direct links
  And only COORD and SUDO receive ORATORIO_COORD_MANAGE
  And MEMBER receives only ORATORIO_GET from the Oratorio groups
```

## Open questions

* None.

## Out of scope

* Nested roles or runtime role inheritance.
* A separate Oratorio Coordinator entity or Member status.
* Automatic restoration after Member reactivation.
* Final-holder protection for `ORATORIO_COORD`.
* Granting this designation through generic Account-role administration.

## Related ADRs

* [ADR-0014: Make Member lifecycle own Oratorio Coordinator designation](../../decisions/0014-make-member-lifecycle-own-oratorio-coordinator-designation.md)
* [ADR-0015: Compose Oratorio permission bundles in code](../../decisions/0015-compose-oratorio-permission-bundles-in-code.md)

## Related requirements

* [Member Records and Lifecycle](../members/member-records-and-lifecycle.md)
* [Account Role Management](../rbac/account-role-management.md)
* [RBAC Catalog](../rbac/rbac-catalog.md)

## Related videos

* None.
