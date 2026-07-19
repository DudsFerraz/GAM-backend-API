# Security and RBAC Guidelines

## 1. Purpose

This document defines the Security and Role-Based Access Control (RBAC) architecture for `gam-api`.

Security in this application is strictly **permission-based**. Roles act purely as bundles of permissions. The system enforces rigid protections around system-managed roles and permissions to prevent administrative lockouts and maintain the integrity of the application's core security contract.

## 2. Core Architecture Rules

### 2.1. Permission-Based Authorization

Authorization is enforced exclusively through permissions, never through roles.

* **Authentication Phase:** The `AccountDetailsService` emits only permission authorities (e.g., `MEMBER_GET`) into the security context. It does **not** emit role authorities (e.g., `ROLE_COORD`).
* **Authorization Phase:** Controller methods and business rules use `@PreAuthorize` to check for specific permission codes.

**Correct Usage:**

```java
@PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_GET + "')")
```

**Forbidden Usage:**

```java
@PreAuthorize("hasRole('COORD')")
@PreAuthorize("hasAuthority('ROLE_COORD')")
```

### 2.2. Authorization HTTP Responses (`403` vs `404`)

The API handles unauthorized access dynamically based on the sensitivity of the resource's existence.

* **Use `404 Not Found`:** When revealing the mere existence of a resource is sensitive (e.g., fetching a specific `Member` or `Event` that the user has no visibility over).
* **Use `403 Forbidden`:** When the user knows the resource or action exists, but lacks the permission to perform the requested operation (e.g., an Account attempting to edit a system-managed role without the required permission, or a user attempting to activate a member without the `MEMBER_ACTIVATION` permission).

## 3. Roles and Permissions Model

### 3.1. System-Managed Records

Roles and permissions are governed by a boolean `systemManaged` flag.

When `systemManaged = true`, the record is part of the application's immutable security contract (created via seed/migration).

* Administrators **cannot** rename it, edit its description, delete it, disable it, or modify its role-permission links.
* The `systemManaged` flag itself cannot be altered via the API.

### 3.2. System Roles

The application defines four baseline system-managed roles. Their definitions and permission bundles are contracted by the Accepted RBAC Catalog Requirement Specification and implemented by the codebase registry.

| Role | Definition |
| --- | --- |
| `SUDO` | Developer role. Automatically receives every system permission that exists. |
| `COORD` | System role that reuses the GAM domain term and receives only the explicit permission allowlist accepted in the RBAC Catalog Requirement Specification. |
| `MEMBER` | Volunteer worker role. |
| `VISITOR` | Public/unauthenticated viewer role. |

A newly accepted system permission is added automatically only to `SUDO`. It shall not expand `COORD`, `MEMBER`, or `VISITOR` authority unless the corresponding accepted allowlist is deliberately updated.

### 3.3. System Permissions

The Accepted [RBAC Catalog Requirement Specification](../requirements/rbac/rbac-catalog.md) is the behavior and metadata contract for system permissions and baseline role bundles. The codebase registry (for example, `PermissionEnum`) implements that contract and is the operational input used by seed logic to synchronize the database.

If the accepted specification and code registry disagree, the accepted specification wins under the project source-of-truth policy and the implementation must be corrected. Registry changes and requirement changes shall be made together.

Persisted registry data that is absent from the accepted contract is stale and fail-closed: it grants no authority and is excluded from ordinary catalog reads. The lifecycle and synchronization strategy are defined by `REQ-RBAC-004`, `REQ-RBAC-005`, and [ADR-0003](../decisions/0003-keep-stale-rbac-registry-data-fail-closed.md).

A permission definition consists of:

* **`code`**: The stable machine identifier used by backend authorization (e.g., `MEMBER_GET`). **Codes can never be renamed.**
* **`label`**: A short, human-readable name for UI display (e.g., `View members`).
* **`description`**: A detailed explanation of the capability.

### 3.4. Custom Roles and Permissions

* **Custom Roles:** Are allowed (`systemManaged = false`). Authorized Accounts can create custom roles, assign them current system permissions, and edit or delete them (subject to RBAC and soft-delete policies).
* **Custom Permissions:** Are **strictly forbidden**. The application does not support admin-created custom permissions at this stage.

## 4. Assignment Rules and Lockout Prevention

### 4.1. Account-Role Assignment

* Accounts with `ACCOUNT_ROLE_MANAGE` can assign and remove only active custom Roles with `systemManaged = false`, subject to the Account-role requirements.
* The Member lifecycle exclusively owns `MEMBER`, `VISITOR`, and `COORD`. Coordinator grant and revoke use the dedicated `COORDINATOR_MANAGE` permission and Member-targeted lifecycle endpoints.
* Generic Account-role administration rejects every system-managed Role, including future system Roles.
* **`SUDO` Exception:** Ordinary HTTP callers cannot assign or remove the `SUDO` role. `SUDO` management is strictly developer-controlled and must be executed via the command-line `maintenance` Spring profile.

```bash
# Example: Assigning SUDO via developer maintenance CLI
mvn spring-boot:run -Dspring-boot.run.profiles=maintenance -Dspring-boot.run.arguments="--maintenance.job=sudo --maintenance.action=assign-sudo --maintenance.account-email=dev@example.com --maintenance.reason=developer-recovery-access"
```

The supported flags, selector rules, output, validation precedence, and process exit codes are defined by `REQ-ACCOUNT-ROLE-015` in the [Account Role Management Requirement Specification](../requirements/rbac/account-role-management.md).

### 4.2. Lockout Prevention (`RbacSafetyPolicy`)

The application enforces strict invariants to prevent accidental or malicious system lockouts. These checks are executed in the application layer (inside `RbacSafetyPolicy`) within the same transaction as the mutation.

**The current hard invariant for explicit SUDO role removal:** At least one active Account must possess the `SUDO` role after every committed SUDO role removal.

The system will block and throw a `ForbiddenOperationException` for the following actions:

1. Removing the last active `SUDO` account role.
2. A Coordinator revoke or Member deactivation removing the final current Coordinator when the actor does not have an active `SUDO` assignment.

An Account with an active `SUDO` assignment may remove the final current Coordinator through the owning Member lifecycle workflow.

Account deactivation, disabling, deletion, and restoration while an Account has SUDO are outside the current Account-role requirements. They require a separate accepted Requirement Specification before any protection rule is inferred or implemented.
