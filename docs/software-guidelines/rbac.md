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
* **Use `403 Forbidden`:** When the user knows the resource or action exists, but lacks the permission to perform the requested operation (e.g., an admin attempting to edit a system-managed role, or a user attempting to activate a member without the `MEMBER_ACTIVATE` permission).

## 3. Roles and Permissions Model

### 3.1. System-Managed Records

Roles and permissions are governed by a boolean `systemManaged` flag.

When `systemManaged = true`, the record is part of the application's immutable security contract (created via seed/migration).

* Administrators **cannot** rename it, edit its description, delete it, disable it, or modify its role-permission links.
* The `systemManaged` flag itself cannot be altered via the API.

### 3.2. System Roles

The application defines four baseline system-managed roles. Their definitions and permission bundles are owned entirely by the codebase.

| Role | Definition |
| --- | --- |
| `SUDO` | Developer role. Automatically receives every system permission that exists. |
| `COORD` | System administrator role (GAM coordinators). |
| `MEMBER` | Volunteer worker role. |
| `VISITOR` | Public/unauthenticated viewer role. |

### 3.3. System Permissions (Source of Truth)

System permissions are defined in the codebase (e.g., `PermissionEnum`) which serves as the ultimate source of truth. Seed logic synchronizes the database with the code registry.

A permission definition consists of:

* **`code`**: The stable machine identifier used by backend authorization (e.g., `MEMBER_GET`). **Codes can never be renamed.**
* **`label`**: A short, human-readable name for UI display (e.g., `View members`).
* **`description`**: A detailed explanation of the capability.

### 3.4. Custom Roles and Permissions

* **Custom Roles:** Are allowed (`systemManaged = false`). Administrators can create custom roles, assign them system permissions, and edit or delete them (subject to RBAC and soft-delete policies).
* **Custom Permissions:** Are **strictly forbidden**. The application does not support admin-created custom permissions at this stage.

## 4. Assignment Rules and Lockout Prevention

### 4.1. Account-Role Assignment

* Administrators can assign and remove ordinary roles (system or custom) to accounts based on their granted permissions.
* **`SUDO` Exception:** Administrators cannot assign or remove the `SUDO` role via the HTTP API. `SUDO` management is strictly developer-controlled and must be executed via the command-line `maintenance` Spring profile.

```bash
# Example: Assigning SUDO via developer maintenance CLI
mvn spring-boot:run -Dspring-boot.run.profiles=maintenance -Dspring-boot.run.arguments="--maintenance.job=sudo --maintenance.action=assign-sudo --maintenance.account-email=dev@example.com --maintenance.reason=developer-recovery-access"
```

### 4.2. Lockout Prevention (`RbacSafetyPolicy`)

The application enforces strict invariants to prevent accidental or malicious system lockouts. These checks are executed in the application layer (inside `RbacSafetyPolicy`) within the same transaction as the mutation.

**The Hard Invariant:** At least one active account must possess the `SUDO` role at all times.

The system will block and throw a `ForbiddenOperationException` for the following actions:

1. Removing the last active `SUDO` account role.
2. Deactivating or disabling the last active `SUDO` account.
3. An administrator attempting to remove their own last admin capability when no other active admin exists in the system.