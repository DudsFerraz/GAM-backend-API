# Security And RBAC

Date: 2026-06-27

## 1. Purpose

This document defines the long-term Security and RBAC direction.

Soft delete, restore, hard delete, and audit accountability are handled by:

```text
persistence-soft-delete-policy.md
audit-log.md
```

This document focuses on:

1. system-managed roles and permissions;
2. permission definitions;
3. role definitions;
4. role-permission rules;
5. account-role safety rules;
6. permission-based authorization;
7. lockout prevention;
8. `403` vs hidden `404` behavior.

## 2. Current Shape

The project currently uses:

```text
roles
permissions
account_roles
role_permissions
```

At authentication time, `AccountDetailsService` loads:

```text
ROLE_<roleName>
permissionName
```

as Spring authorities.

Most controller authorization uses permissions:

```java
@PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_GET + "')")
```

Event visibility also uses permissions dynamically:

```text
event.requiredPermission.name in userAuthorities
```

The real access model is:

```text
accounts have roles
roles have permissions
permissions grant actions
```

Roles are permission bundles. Permissions are the real capability layer.

## 3. Main Rule

Authorization must be permission-based.

Use:

```java
hasAuthority(PermissionEnum.Code.MEMBER_GET)
```

Do not use role-based authorization for business rules:

```java
hasRole("COORD")
hasAuthority("ROLE_COORD")
```

Role authorities must not be emitted during authentication after the refactor. `AccountDetailsService` must only emit permission authorities.

## 4. System-Managed Records

Roles and permissions must have a persistent `systemManaged` marker.

Database target:

```sql
roles.system_managed boolean not null default false;
permissions.system_managed boolean not null default false;
```

Entity target:

```java
private boolean systemManaged;
```

Objective:

```text
systemManaged = true
```

means:

```text
This role or permission is part of the application contract.
It was created by seed, migration, or code.
Administrators cannot rename it.
Administrators cannot edit its description.
Administrators cannot delete it.
Administrators cannot disable it.
Administrators cannot change systemManaged itself.
```

```text
systemManaged = false
```

means:

```text
This role was created through application administration.
Administrators may manage it according to RBAC, soft-delete, and audit-log rules.
```

All current permissions must be `systemManaged = true`.

Custom permissions are not supported at this stage, but the `systemManaged` flag must still exist in `permissions` so the model can support custom permissions later without changing the core protection concept.

## 5. System Roles

The following roles are system-managed.

| Role | Definition |
|---|---|
| `SUDO` | Role available only to system developers. It grants every permission that exists in the application, including special developer permissions that may exist in the future. |
| `COORD` | Role for system administrators. GAM coordinators are experienced members who coordinate the group, handle the harder responsibilities, usually stay in the role for 1 or 2 years, and select the next coordinator. |
| `MEMBER` | Role for the volunteer workers of the group. |
| `VISITOR` | Role for people who are not part of GAM but may view public parts of the system, such as presentation pages, marketing campaigns, and public content. |

Administrators must not rename, edit, delete, disable, or unprotect these roles.

## 6. Permission Definition

A permission must be defined as:

```text
code = stable machine identifier used by backend authorization
label = human-readable short name for UI/admin display
description = longer explanation of what it allows
```

Example:

```text
code = MEMBER_GET
label = View members
description = Allows viewing active members
```

The current `PermissionEnum` has:

```text
code/name = MEMBER_GET
description = Permite visualizar membros ativos
```

It does not yet have a separate `label`.

Target enum shape:

```java
MEMBER_GET(
        "MEMBER_GET",
        "View members",
        "Allows viewing active members"
)
```

Target database shape:

```text
permissions.code
permissions.label
permissions.description
permissions.system_managed
```

The current `permissions.name` column can either be renamed to `code` or treated as the permission code during the first refactor step.

## 7. Naming Policy

Role and permission codes must not be renamed.

If a permission or role with a different name is required, create a new permission or role.

Reasons:

1. permission codes are used by `@PreAuthorize`;
2. permission codes are stored in the database;
3. permission codes may be referenced by event visibility;
4. role and permission changes must remain understandable in audit logs;
5. renames blur historical meaning.

System-managed role labels/descriptions and permission labels/descriptions must also not be editable by administrators.

Custom role display fields may be editable later, but custom role codes/names must be treated carefully because they are used as identity.

## 8. Permission Source Of Truth

`PermissionEnum` or an equivalent code-owned permission registry must remain the source of truth for system permissions.

System permission flow:

```text
code registry -> seed/migration -> database -> authentication authorities -> @PreAuthorize/security checks
```

Administrators must not create, rename, edit, delete, or disable system permissions.

Custom permissions are not supported now. This avoids unnecessary complexity for the current project goals.

The design must remain open for future custom permissions, especially for data-driven access rules, but the current implementation must not build that feature prematurely.

## 9. Role-Permission Rules

System-managed role-permission links must not be editable by administrators.

Rules:

1. `SUDO` receives every system permission.
2. `SUDO` must automatically receive future system permissions.
3. `COORD`, `MEMBER`, and `VISITOR` receive the exact permissions defined by code-owned seed policy.
4. Administrators cannot edit system-managed role-permission links.
5. Custom roles can use system permissions.
6. Custom permissions do not exist at this stage.

This keeps the system simple while still allowing custom roles.

## 10. Seed Policy

Seed behavior must be idempotent.

Seed code must:

1. create missing system roles;
2. create missing system permissions;
3. mark system roles as `systemManaged = true`;
4. mark system permissions as `systemManaged = true`;
5. create missing system role-permission links;
6. avoid changing administrator-created custom roles;
7. avoid deleting database records automatically when code changes.

If a system permission is removed from code, do not automatically hard delete or soft delete it. Permission removal must be a deliberate developer-controlled change.

Current issue to fix:

```java
private static final Set<PermissionEnum> VISITOR_PERMISSIONS = EnumSet.noneOf(PermissionEnum.class);
```

exists, but the Java seed currently grants `MEMBER_PERMISSIONS` to `VISITOR`.

Target:

```java
if (visitorId != null && VISITOR_PERMISSIONS.contains(permission)) {
    linkPermissionToRole(visitorId, permissionId, now, checkRolePermStmt, insertRolePermStmt);
}
```

## 11. Custom Roles

Custom roles are allowed.

Rules:

1. custom roles have `systemManaged = false`;
2. custom roles can receive system permissions;
3. custom roles cannot receive custom permissions because custom permissions do not exist yet;
4. custom roles can be edited or deleted only when RBAC, soft-delete, and audit-log policies allow it;
5. custom role changes must be audited.

Custom roles must not be allowed to break system invariants such as admin lockout prevention.

## 12. Custom Permissions

Custom permissions are not supported at this stage.

Rationale:

1. current code authorization depends on system permission constants;
2. admin-created permissions would not affect `@PreAuthorize` unless code knows how to consume them;
3. dynamic custom permissions are not needed for current project goals;
4. implementing them now would be overengineering.

The model remains open for future custom permissions by keeping:

```text
permissions.systemManaged
permissions.code
permissions.label
permissions.description
```

Future custom permissions should be considered only when a real feature needs data-driven permissions.

## 13. Account-Role Assignment Rules

Rules:

1. administrators may assign and remove ordinary roles according to their permissions;
2. administrators must not assign or remove `SUDO`;
3. `SUDO` assignment is developer-controlled only;
4. removing a role from an account must be audited;
5. adding a role to an account must be audited;
6. member activation/deactivation can still change roles as part of one high-level member workflow.

When a workflow causes role changes as side effects, log the high-level workflow and include role changes as metadata.

Example:

```text
MEMBER_ACTIVATED
metadata.roleAdded = MEMBER
metadata.roleRemoved = VISITOR
```

## 14. Lockout Prevention

RBAC mutations must not allow the system to lock out all developers or administrators.

Minimum hard invariant:

```text
At least one active account must have the SUDO role.
```

Operational admin invariant:

```text
At least one active account should have the COORD role.
```

`SUDO` is strict because it is the developer recovery path. `COORD` may be enforced strictly if the product requires an active coordinator at all times.

Block these actions:

1. removing the last active `SUDO` account role;
2. deactivating/disabling the last active `SUDO` account;
3. deleting, disabling, editing, or unprotecting the `SUDO` role;
4. removing system permissions from `SUDO`;
5. removing system-managed role-permission links from `SUDO`;
6. assigning or removing `SUDO` through the normal admin UI;
7. removing the actor's own last admin capability when no other active admin remains.

Implement these checks in application services, not only in the frontend.

Use a dedicated policy/service:

```java
RbacSafetyPolicy
```

Example:

```java
public void assertCanRemoveRoleFromAccount(UUID actorId, UUID targetAccountId, UUID roleId) {
    if (isLastActiveHolder(targetAccountId, "SUDO")) {
        throw ForbiddenOperationException.reason("Cannot remove the last SUDO account.");
    }

    if (isRemovingOwnAdminCapability(actorId, targetAccountId, roleId)
            && !anotherActiveAdminExists(actorId)) {
        throw ForbiddenOperationException.reason("Cannot remove your own last admin capability.");
    }
}
```

Run these checks in the same transaction as the mutation. Use locking where needed to avoid concurrent updates bypassing the invariant.

## 15. Authorization Responses

Use this policy:

```text
Use 404 when revealing resource existence is sensitive.
Use 403 when the user knows the resource exists but lacks permission to perform the action.
```

Examples:

```text
GET /events/{id} without visibility -> 404
GET /members/{id} hidden by member visibility -> 404
PATCH /members/{id}/activate without permission -> 403
DELETE system role -> 403
editing system-managed permission -> 403
```

This keeps sensitive resource discovery controlled while still returning clear authorization errors for known administrative actions.

## 16. Refactor Instructions

For RBAC:

1. Add `systemManaged` to `RoleEntity` and `PermissionEntity`.
2. Add database columns for `roles.system_managed` and `permissions.system_managed`.
3. Mark all system roles as `systemManaged = true`.
4. Mark all current permissions as `systemManaged = true`.
5. Add `label` to permissions or define when `description` is enough for the current step.
6. Make `PermissionEnum` expose `code`, `label`, and `description`.
7. Keep `PermissionEnum` as source of truth for system permissions.
8. Update seed code to create/sync system-managed roles and permissions.
9. Fix the `VISITOR_PERMISSIONS` seed behavior.
10. Stop emitting `ROLE_<roleName>` authorities.
11. Use only permission authorities for authorization.
12. Block admin edits/deletes/disables for system-managed roles and permissions.
13. Block admin edits to system-managed role-permission links.
14. Allow custom roles to use system permissions.
15. Do not implement custom permissions at this stage.
16. Add `RbacSafetyPolicy` for lockout prevention.
17. Apply the `403` vs hidden `404` policy consistently.

## 17. Refactor Order

Apply this subject in this order:

1. Add `systemManaged` columns and entity fields.
2. Update seed logic and fix `VISITOR` permissions.
3. Add permission `label` support or formalize `description` as temporary display text.
4. Stop emitting role authorities.
5. Add system-managed edit/delete guards.
6. Add custom role management rules.
7. Add lockout prevention policy.
8. Review existing `@PreAuthorize` and security components for permission-code consistency.
