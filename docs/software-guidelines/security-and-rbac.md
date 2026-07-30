# Security and RBAC Guidelines

## 1. Purpose

This document defines the Security and Role-Based Access Control (RBAC) architecture for `gam-api`.

Security in this application is strictly **permission-based**. Roles act purely as bundles of permissions. The system enforces rigid protections around system-managed roles and permissions to prevent administrative lockouts and maintain the integrity of the application's core security contract.

## 2. Core Architecture Rules

### 2.1. Permission-Based Authorization

Authorization is enforced exclusively through permissions, never through roles.

* **Authentication Phase:** The `AccountDetailsService` emits only permission authorities (e.g., `MEMBER_GET`) into the security context. It does **not** emit role authorities (e.g., `ROLE_COORD`).
* **Coarse Authorization Phase:** The Spring Security filter chain uses HTTP
  method/path request matchers or a custom `AuthorizationManager` to enforce
  coarse permission policy before Spring MVC parses request input. This policy
  is authoritative for protected-route `401` and coarse-permission `403`
  precedence.
* **Target Authorization Phase:** Application-layer policies evaluate visible
  target ownership, lifecycle, and caller relationships after parsing and
  lookup. They authorize through permission authorities or an explicitly
  Accepted target-specific alternative such as self-view.
* **Defense in Depth:** Controller `@PreAuthorize` checks may mirror the coarse
  policy, but they are not its authoritative enforcement point and must not
  replace application-layer target policies.

**Correct Usage:**

```java
@PreAuthorize("hasAuthority('" + PermissionEnum.Code.MEMBER_GET + "')")
```

The example is a permission check, not authorization to rely on controller
method security for pre-parsing precedence.

**Forbidden Usage:**

```java
@PreAuthorize("hasRole('COORD')")
@PreAuthorize("hasAuthority('ROLE_COORD')")
```

### 2.2. Authorization Layers and HTTP Responses

Follow
[`REQ-API-ERROR-007`](../requirements/platform/api-error-and-authorization-contract.md#req-api-error-007-authentication-authorization-and-validation-precedence)
and
[ADR-0023](../decisions/0023-enforce-coarse-route-authorization-before-mvc-parsing.md)
in this order:

1. Require valid authentication for a protected operation.
2. Apply coarse method/path authorization without parsing request input or
   loading a target.
3. Apply required CSRF or canonical-origin proof.
4. Parse and validate request input.
5. Resolve the target and apply its visibility rule.
6. Apply target-specific authorization to a visible target.
7. Apply business and security invariants.

The public outcomes are:

* **Use `401 AUTHENTICATION_REQUIRED`:** A protected operation has no valid
  authentication.
* **Use `403 ACCESS_DENIED`:** An authenticated caller lacks coarse route
  permission or target-specific authority for a visible operation.
* **Use `404 RESOURCE_NOT_FOUND`:** A referenced target is missing,
  soft-deleted, or deliberately hidden by the owning Accepted Requirement
  Specification.
* **Use `403 FORBIDDEN_OPERATION`:** The caller may invoke the operation, but a
  business or security invariant prohibits this transition.

Security entry points and denial handlers must use the common error envelope,
safe details, headers, and non-cacheable transport defined by the
[API Error and Authorization Contract](../requirements/platform/api-error-and-authorization-contract.md).

## 3. Roles and Permissions Model

### 3.1. System-Managed Records

Roles and permissions are governed by a boolean `systemManaged` flag.

When `systemManaged = true`, the record is part of the application's immutable security contract (created via seed/migration).

* Administrators **cannot** rename it, edit its description, delete it, disable it, or modify its role-permission links.
* The `systemManaged` flag itself cannot be altered via the API.

### 3.2. System Roles

The application defines five baseline system-managed roles. Their definitions
and permission bundles are contracted by the Accepted RBAC Catalog Requirement
Specification and implemented by the codebase registry.

| Role | Definition |
| --- | --- |
| `SUDO` | Developer role. Automatically receives every system permission that exists. |
| `COORD` | System role that reuses the GAM domain term and receives only the explicit permission allowlist accepted in the RBAC Catalog Requirement Specification. |
| `ORATORIO_COORD` | Lifecycle-owned Oratorio operational role with the explicit permission allowlist accepted in the RBAC Catalog Requirement Specification. |
| `MEMBER` | Volunteer worker role. |
| `VISITOR` | Lifecycle-owned inactive-Member role with no baseline permissions; it does not represent anonymous access. |

Public Event visibility is represented by a null `requiredPermissionId`, not by
the `VISITOR` Role or an implicit visitor permission.

A newly accepted system permission is added automatically only to `SUDO`. It
shall not expand `COORD`, `ORATORIO_COORD`, `MEMBER`, or `VISITOR` authority
unless the corresponding accepted allowlist is deliberately updated.

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
* The Member lifecycle exclusively owns `MEMBER`, `VISITOR`, `COORD`, and `ORATORIO_COORD`. Coordinator grant and revoke use the dedicated `COORDINATOR_MANAGE` permission and Member-targeted lifecycle endpoints. Oratorio Coordinator grant and revoke use `ORATORIO_COORD_MANAGE` and the owning Oratorio Coordinator lifecycle endpoints.
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
