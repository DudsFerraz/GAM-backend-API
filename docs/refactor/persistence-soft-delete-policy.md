# Persistence And Soft Delete Policy

Date: 2026-06-26

## 1. Purpose

This document defines the persistence and soft-delete policy for the project.

Soft delete is not a user-facing feature. It is an internal security and safety mechanism. Its purpose is to protect the system from mistaken deletes, abusive administration, and irreversible data loss.

Administrators can manage the system, but they must not be able to destroy historical facts.

Restore, hard delete, and direct access to soft-deleted rows must stay outside the normal application API. These operations belong to developer-controlled maintenance work.

## 2. Audit-Log Boundary

Activity/audit logging is handled as a separate refactor subject in [`audit-log.md`](audit-log.md).

This document covers:

1. which entities are soft-deletable;
2. which user-facing actions can trigger a soft delete;
3. which records must only be deactivated, cancelled, or corrected;
4. restore and hard-delete restrictions;
5. unique-value behavior after soft delete;
6. repository and persistence rules.

The audit-log subject defines how user actions are recorded, including actor, target, reason, metadata, and immutable activity history.

## 3. Current Shape

Most persisted business entities already contain soft-delete columns through `FullAuditableEntity` or `JunctionAuditableEntity`.

Soft-deletable repositories extend `BaseRepository`, whose default implementation overrides `delete`:

```java
entity.setDeletedAt(now);
entity.setDeletedBy(deletedBy);
save(entity);
```

`RefreshTokenEntity` is the exception. It does not have soft-delete fields and uses plain `JpaRepository`.

Current consistency issue:

| Entity | Has soft-delete columns | Uses `BaseRepository` | Has `@SQLRestriction` |
|---|---:|---:|---:|
| `AccountEntity` | yes | yes | yes |
| `RoleEntity` | yes | yes | yes |
| `PermissionEntity` | yes | yes | yes |
| `AccountRoleEntity` | yes | yes | yes |
| `RolePermissionEntity` | yes | yes | yes |
| `LocationEntity` | yes | yes | no |
| `EventEntity` | yes | yes | no |
| `MemberEntity` | yes | yes | no |
| `PresenceEntity` | yes | yes | no |
| `OratorioEntity` | yes | yes | no |
| `MissaEntity` | yes | yes | no |
| `OratorianoEntity` | yes | yes | no |
| `RefreshTokenEntity` | no | no | no |

Every soft-deletable entity must have consistent normal-read visibility. A soft-deleted row must not appear in ordinary application reads.

## 4. Global Rules

Use these rules for every business entity:

1. Soft delete is internal behavior, not UI language.
2. Administrators must not hard delete records.
3. Administrators must not restore records.
4. Administrators must not browse soft-deleted records.
5. Developer-controlled maintenance can restore records when needed.
6. Developer-controlled maintenance can hard delete records only in exceptional technical or legal situations.
7. Normal users and administrators must interact through domain actions: deactivate, cancel, remove mistaken assignment, or disable.
8. Historical facts must not be destroyed through ordinary UI actions.
9. Corrective actions must require a reason and must be logged by the activity-log system.
10. Soft delete must not cascade automatically into historical child records unless a specific policy explicitly says so.

## 5. User-Facing Actions vs Technical Soft Delete

The application must avoid exposing soft-delete terminology.

Do not expose:

```text
soft delete
restore deleted record
view deleted records
hard delete
```

Expose domain actions instead:

```text
deactivate member
cancel event
remove mistaken presence
disable custom role
remove role from account
remove permission from role
```

The technical implementation may soft-delete a row underneath, but that must not become a general-purpose admin capability to erase history.

## 6. Unique Values After Soft Delete

Some tables use partial unique indexes:

```sql
UNIQUE (email) WHERE deleted_at IS NULL
```

This means only active rows must be unique. Soft-deleted rows do not reserve the value.

Example:

| id | email | deleted_at |
|---|---|---|
| `A` | `x@gmail.com` | `2026-01-01T10:00:00Z` |
| `B` | `x@gmail.com` | null |

This is valid because only `B` is active.

If the same value is deleted, reused, deleted again, and reused again, the database can look like:

| id | email | deleted_at |
|---|---|---|
| `A` | `x@gmail.com` | `2026-01-01T10:00:00Z` |
| `B` | `x@gmail.com` | `2026-03-01T10:00:00Z` |
| `C` | `x@gmail.com` | null |

This is also valid.

A developer restore can fail when another active row already uses the same unique value. In that case, the developer must resolve the conflict manually.

Valid developer resolutions:

1. do not restore the old row;
2. restore the old row with a changed unique value;
3. change or remove the conflicting active row first;
4. restore only the data needed for investigation outside the live application flow.

Non-partial unique constraints behave differently. `oratorios.event_id` and `missas.event_id` are currently plain unique constraints, so a soft-deleted row still reserves the `event_id`. If the product must allow recreating a `Missa` or `Oratorio` for the same event after soft delete, those constraints must become partial unique indexes.

## 7. Account Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | Yes, internally |
| Can be restored? | Developer only |
| Who can see deleted records? | Developer only |
| Is hard delete allowed? | Developer only, exceptional |
| Can unique values be reused after soft delete? | Yes, email reuse is currently allowed by partial unique index |

User-facing behavior:

- Do not expose account hard delete.
- Do not expose account restore.
- Prefer disabling/locking an account over deleting it from the UI.

Rationale:

Accounts are audit actors through `created_by`, `updated_by`, and `deleted_by`. Hard deleting them can damage historical meaning across the system.

Restore conflict rule:

- If the email has already been reused by another active account, developer restore must fail until the conflict is manually resolved.

## 8. Member Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | Yes, internally, exceptional |
| Can be restored? | Developer only |
| Who can see deleted records? | Developer only |
| Is hard delete allowed? | Developer only, exceptional |
| Can unique values be reused after soft delete? | Yes, account reuse is currently allowed by partial unique index |

User-facing behavior:

- Do not expose normal member deletion.
- Use member deactivation for members who should no longer participate.
- A member with presences must not be deletable through the UI.

Rationale:

A member can be part of presence history, event assignments, and audit meaning. Deleting a real member would risk rewriting facts. Deactivation expresses current state without destroying history.

If a member was created by mistake, developer-controlled soft delete can be used as an exceptional correction. Related presences must not be automatically deleted. Any dependent correction must be deliberate.

## 9. Event Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | Yes, for mistaken records |
| Can be restored? | Developer only |
| Who can see deleted records? | Developer only |
| Is hard delete allowed? | Developer only, exceptional |
| Can unique values be reused after soft delete? | Not currently relevant for generic events |

User-facing behavior:

- Deletion may exist only as a correction action for clearly mistaken events.
- Cancellation must be used for real events that should remain part of history.
- Deletion must not replace cancellation.

An event can be deleted from the UI only when all of these are true:

1. it is inside a short configured correction window after creation;
2. it has not started;
3. it has no presences;
4. it has no operational `Missa` or `Oratorio` data;
5. it is not locked or finalized;
6. a reason is provided.

If any condition fails, the user-facing action must be event cancellation.

## 10. Presence Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | Yes, for correction |
| Can be restored? | Developer only |
| Who can see deleted records? | Developer only |
| Is hard delete allowed? | Developer only, exceptional |
| Can unique values be reused after soft delete? | Yes, member/event pair reuse is currently allowed by partial unique index |

User-facing behavior:

- Presence removal is a correction action, not a history-erasing tool.
- Presence removal must require a reason.
- Presence removal must be blocked after the event is locked or finalized.

Rationale:

A wrongly logged presence should be correctable. A real presence should not be silently erased after the fact. The line must be defined by event state, correction window, and activity logging.

## 11. Location Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | Yes |
| Can be restored? | Developer only |
| Who can see deleted records? | Developer only |
| Is hard delete allowed? | Developer only, exceptional |
| Can unique values be reused after soft delete? | Not currently relevant |

User-facing behavior:

- A location can be removed from normal use only when it is not needed by historical events.
- If a location is referenced by events, do not allow ordinary UI deletion.

Rationale:

Locations are referenced by events with restrictive foreign keys. Removing a location that belongs to event history damages the event record.

## 12. Role Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | Yes, only for user-created roles |
| Can be restored? | Developer only |
| Who can see deleted records? | Developer only |
| Is hard delete allowed? | Developer only, exceptional |
| Can unique values be reused after soft delete? | Yes, role name reuse is currently allowed by partial unique index |

User-facing behavior:

- Seeded baseline roles must not be deletable by administrators.
- User-created roles can be disabled or deleted only when doing so does not break active assignments.
- Role deletion/removal must require a reason and activity log entry.

Persistence requirement:

- Add a persistent marker such as `systemManaged` or `protectedRole`.

## 13. Permission Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | Yes, only for user-created permissions |
| Can be restored? | Developer only |
| Who can see deleted records? | Developer only |
| Is hard delete allowed? | Developer only, exceptional |
| Can unique values be reused after soft delete? | Yes for user-created permissions; system permission names must remain reserved |

User-facing behavior:

- Seeded baseline permissions must not be deletable by administrators.
- User-created permissions can be disabled or deleted only when no active role depends on them.
- Permission deletion/removal must require a reason and activity log entry.

Persistence requirement:

- Add a persistent marker such as `systemManaged` or `protectedPermission`.

Rationale:

Permissions are code-sensitive because `@PreAuthorize` can depend on them. Runtime CRUD must not let administrators delete system capabilities required by application flows.

## 14. AccountRole Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | Yes |
| Can be restored? | Developer only |
| Who can see deleted records? | Developer only |
| Is hard delete allowed? | Developer only, exceptional |
| Can unique values be reused after soft delete? | Yes, account/role pair reuse is currently allowed by partial unique index |

User-facing behavior:

- Removing a role from an account is allowed as a domain action.
- The technical implementation can soft-delete the `AccountRoleEntity`.
- Re-adding the same role later creates or reactivates an active assignment according to the implementation chosen during refactor.

Rationale:

Role assignment history is useful for security investigations.

## 15. RolePermission Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | Yes |
| Can be restored? | Developer only |
| Who can see deleted records? | Developer only |
| Is hard delete allowed? | Developer only, exceptional |
| Can unique values be reused after soft delete? | Yes, role/permission pair reuse is currently allowed by partial unique index |

User-facing behavior:

- Removing a permission from a user-created role can be allowed.
- Removing a permission from a system-managed role must be blocked unless explicitly allowed by developer-controlled migration.
- The action must require a reason and activity log entry.

Rationale:

Permission assignment history is part of security history.

## 16. Oratorio Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | Yes, internally |
| Can be restored? | Developer only |
| Who can see deleted records? | Developer only |
| Is hard delete allowed? | Developer only, exceptional |
| Can unique values be reused after soft delete? | No with the current plain `event_id` unique constraint |

User-facing behavior:

- Do not expose independent ordinary deletion if the `Oratorio` represents real event history.
- Corrections must follow the event deletion/cancellation policy.

Persistence requirement:

- If recreating an `Oratorio` for the same event after soft delete must be allowed, replace the plain `event_id` unique constraint with a partial unique index.

## 17. Missa Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | Yes, internally |
| Can be restored? | Developer only |
| Who can see deleted records? | Developer only |
| Is hard delete allowed? | Developer only, exceptional |
| Can unique values be reused after soft delete? | No with the current plain `event_id` unique constraint |

User-facing behavior:

- Do not expose independent ordinary deletion if the `Missa` represents real event history.
- Corrections must follow the event deletion/cancellation policy.

Persistence requirement:

- If recreating a `Missa` for the same event after soft delete must be allowed, replace the plain `event_id` unique constraint with a partial unique index.

## 18. Oratoriano Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | Yes, internally, exceptional |
| Can be restored? | Developer only |
| Who can see deleted records? | Developer only |
| Is hard delete allowed? | Developer only, exceptional |
| Can unique values be reused after soft delete? | Not currently relevant |

User-facing behavior:

- Do not expose normal oratoriano deletion.
- Use oratoriano deactivation for frequenters who should no longer be active.
- Use oratoriano reactivation when a frequenter returns.
- Allow linking an oratoriano to an account optionally.
- Do not require an account to register an oratoriano.

Rationale:

`Oratoriano` represents a person who frequents the oratory. This person may be a child or teenager and must not be forced to have a system account. An account is an authentication identity; an oratoriano is a person record. When an account exists, the relationship can be linked optionally.

Like `Member`, a real oratoriano must not be deleted through the UI. Deactivation expresses current state without destroying historical participation in `Oratorio` records.

## 19. RefreshToken Policy

Policy:

| Question | Decision |
|---|---|
| Can be soft-deleted? | No |
| Can be restored? | No |
| Who can see deleted records? | Nobody; deleted tokens do not exist |
| Is hard delete allowed? | Yes |
| Can unique values be reused after delete? | Token values are unique while present |

Rationale:

Refresh tokens are security/session artifacts, not business history. Hard deletion on logout, refresh rotation, and expiration is correct.

## 20. Join Tables Without Entities

These tables are not currently modeled as entities:

```text
oratorio_lanche
oratorio_bt_jovens
oratorio_bt_criancas
oratorio_presences_oratorianos
missa_acolhida_members
```

Policy:

1. Do not add soft delete to these tables by default.
2. Treat their lifecycle as part of the owning aggregate.
3. If assignment history becomes important, promote the relationship into an explicit entity with its own soft-delete and audit policy.

## 21. Repository Rules

The normal application repository contract must not expose dangerous maintenance operations.

Refactor instructions:

1. Remove `hardDelete` from the ordinary `BaseRepository` API, or move it behind a developer-only maintenance boundary.
2. Remove `findAllDeleted` from ordinary repositories, or move it behind a developer-only maintenance boundary.
3. Do not expose restore through normal repositories used by application services.
4. Keep normal `delete` as soft delete for soft-deletable entities.
5. Add consistent normal-read filtering for every soft-deletable entity.
6. Review derived repository methods after filtering is standardized.

Normal application services must not call:

```text
hardDelete
findAllDeleted
restore
```

## 22. Visibility Rules

Every soft-deletable entity must be hidden from ordinary JPA reads after deletion.

Use one consistent mechanism. The current code already uses `@SQLRestriction` on some entities. Either apply that consistently to every soft-deletable entity or replace it with another consistent filtering strategy.

Soft-deleted rows must not appear in:

1. `findById`;
2. `findAll`;
3. dynamic search;
4. derived `findBy...` methods;
5. derived `existsBy...` methods;
6. relationship collections used by normal application flows.

Developer maintenance tooling may use separate native queries or dedicated internal code to inspect and restore deleted rows.

## 23. Refactor Instructions

Resolved implementation decisions:

1. Developer-only maintenance is implemented as command-line/admin tooling outside the public API.
2. Re-adding a previously removed `AccountRole` or `RolePermission` creates a new row, preserving each assignment period separately.
3. `Missa` and `Oratorio` `event_id` uniqueness must allow recreation after soft delete by using partial unique indexes.
4. Event lock/finalization is modeled explicitly.
5. The event correction window must be configurable when event correction workflows are implemented. The default must be 15 minutes.
6. Event cancellation reason belongs on the generic `Event` model/table.
7. Seeded roles and permissions are protected with a shared `system_managed` column on both tables.

Maintenance command usage:

The maintenance job is intentionally command-line only. It runs under the `maintenance` Spring profile, performs one operation, writes a developer maintenance activity log, and exits the application.

Inspect soft-deleted rows:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=maintenance" "-Dspring-boot.run.arguments=--maintenance.action=inspect-soft-deleted --maintenance.table=members"
```

Restore one soft-deleted row:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=maintenance" "-Dspring-boot.run.arguments=--maintenance.action=restore --maintenance.table=members --maintenance.id=<uuid> --maintenance.reason=""Restored after developer review"""
```

Hard-delete one soft-deleted row:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=maintenance" "-Dspring-boot.run.arguments=--maintenance.action=hard-delete --maintenance.table=members --maintenance.id=<uuid> --maintenance.reason=""Exceptional legal cleanup"""
```

This maintenance runner must not be exposed through controllers.

Allowed tables:

```text
accounts
roles
permissions
account_roles
role_permissions
locations
events
members
presences
oratorios
missas
oratorianos
```

When event correction workflows are added, define the runtime property as:

```properties
app.event.correction-window=15m
```

Spring Boot can bind duration values such as `15m`, `30m`, `1h`, or ISO-8601 values such as `PT15M`.

Apply this subject in the following order:

1. Decide and implement a consistent soft-delete visibility mechanism for all soft-deletable entities.
2. Remove or isolate `hardDelete` from normal repository usage.
3. Remove or isolate `findAllDeleted` from normal repository usage.
4. Add developer-only restore/inspection tooling outside the public application API.
5. Add domain-level delete/deactivate/cancel rules for `Member`, `Event`, and `Presence`.
6. Add system-protection markers for seeded `Role` and `Permission`.
7. Review partial unique indexes and plain unique constraints.
8. Add tests for the persistence behavior in the dedicated test strategy document.

## 24. Refactor Order

Apply the policy in this order:

1. Repository safety boundary.
2. Visibility consistency for soft-deletable entities.
3. `Member` deactivation vs deletion rules.
4. `Event` cancellation vs deletion rules.
5. `Presence` correction rules.
6. RBAC system-protection rules.
7. `Missa` and `Oratorio` unique constraint decisions.
8. Remaining soft-deletable entities.
