# Requirement: Account Records

## Status
Accepted

## Context
GAM needs a documented contract for reading Account records through direct lookup and structured search.

Account record reads support coordination and authorization administration without exposing credentials, sessions, or low-level audit metadata in the ordinary Account response. The current implementation and tests predate this Requirement Specification and were used only as discovery material; this document defines the intended behavior.

Account identity rules are governed by the common UUID and authentication requirements. Account creation, login, refresh, logout, and registration response behavior remain in the Authentication and Account Registration Requirement Specification.

## Ubiquitous Language
- `account record`: A read-oriented representation of an Account identity and its current active role assignments.
- `self-view`: The visibility exception that allows an authenticated Account to retrieve its own account record by Account identifier without the account-view permission.
- `active role assignment`: A non-deleted Account-to-Role assignment whose Account and Role are also visible through normal application reads.
- `row audit metadata`: Low-level auditing columns such as `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `deletedAt`, and `deletedBy`.

## Functional requirements

### REQ-ACCOUNT-001: Direct account lookup visibility
The system shall expose direct Account lookup at `GET /accounts/{accountId}`.

Direct Account lookup shall return an account record only when the caller is authenticated and either:

- has the `ACCOUNT_GET` permission; or
- is requesting the caller's own Account through the self-view rule.

Rationale:
Account records contain identity and authorization-facing role information. Coordinators need permissioned access to other Accounts, while an authenticated Account should be able to inspect its own record.

Valid examples:
- A Coordinator with `ACCOUNT_GET` retrieves another Account by identifier.
- An authenticated Account without `ACCOUNT_GET` retrieves its own account record by identifier.

Invalid examples:
- An authenticated Account without `ACCOUNT_GET` retrieves another Account.
- An unauthenticated request retrieves any Account record.

---

### REQ-ACCOUNT-002: Direct account lookup missing and forbidden responses
Direct Account lookup shall return `404 Not Found` when the requested Account does not exist or is soft-deleted.

Direct Account lookup shall return `403 Forbidden` when the caller is authenticated but neither has `ACCOUNT_GET` nor satisfies the self-view rule.

Rationale:
Missing and soft-deleted Accounts are not visible through ordinary reads. A caller who is authenticated but lacks the necessary visibility is failing an authorization rule.

Valid examples:
- `GET /accounts/{missingAccountId}` returns `404 Not Found` for a caller with `ACCOUNT_GET`.
- `GET /accounts/{otherAccountId}` returns `403 Forbidden` for an authenticated caller without `ACCOUNT_GET`.

Invalid examples:
- Returning a soft-deleted Account record through ordinary lookup.
- Returning another Account record because the caller is authenticated.

---

### REQ-ACCOUNT-003: Account search visibility
The system shall expose structured Account search at `POST /accounts/search`.

Account search shall require the `ACCOUNT_SEARCH` permission.

The self-view rule shall not apply to Account search.

Rationale:
Search is a discovery capability across Account records. It must remain separate from the narrow self-view exception used for direct lookup.

Valid examples:
- A caller with `ACCOUNT_SEARCH` searches Accounts by email.
- A caller with `ACCOUNT_SEARCH` searches Accounts by role.

Invalid examples:
- An Account without `ACCOUNT_SEARCH` searches for itself.
- Search returns only the caller's Account as a substitute for self-view.

---

### REQ-ACCOUNT-004: Empty account search filters
Account search with an empty filter list shall return a paginated page of all visible active Accounts for callers with `ACCOUNT_SEARCH`.

Account search shall not return soft-deleted Accounts through ordinary application reads.

Rationale:
The `ACCOUNT_SEARCH` permission is the explicit Account discovery permission. Empty filters represent browsing the authorized Account collection.

Valid examples:
- `POST /accounts/search` with `filters = []` returns the first requested page of active Accounts.
- A page may be empty when no visible active Accounts match the request.

Invalid examples:
- Empty filters are rejected only because no filter was supplied.
- Empty filters return soft-deleted Accounts.

---

### REQ-ACCOUNT-005: Account search filter contract
Account search shall accept only the following public filter fields and comparison methods:

| Public field | Allowed comparison methods |
| --- | --- |
| `id` | `EQUALS`, `IN` |
| `email` | `EQUALS`, `LIKE` |
| `displayName` | `EQUALS`, `LIKE` |
| `role` | `EQUALS`, `IN` |
| `createdAt` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` |
| `updatedAt` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` |

Errors for unsupported comparison methods or invalid filter values shall reference the public field name. Unknown filter fields shall return the generic message `Unknown filter field.` and shall not expose the submitted field name or internal persistence paths.

Rationale:
Account search needs a strict product contract. Public filter names must remain stable and must not require clients to know internal JPA paths or join structures.

Valid examples:
- Filtering by `email EQUALS "user@example.com"`.
- Filtering by `role IN ["COORD", "MEMBER"]`.
- Filtering by `createdAt GREATER_THAN_OR_EQUAL "2026-01-01T00:00:00Z"`.

Invalid examples:
- Filtering by the legacy or implementation-specific public field `roleName`.
- Filtering by internal paths such as `accountRoles.role.name`.
- Using `LIKE` with `createdAt`.

---

### REQ-ACCOUNT-006: Account record response shape
Account lookup and Account search shall return account records with this response shape:

```json
{
  "id": "<account UUID>",
  "email": "user@example.com",
  "displayName": "Eduardo",
  "roles": [
    {
      "id": "<role UUID>",
      "name": "COORD",
      "description": "Coordinator role",
      "systemManaged": true
    }
  ]
}
```

The `roles` field shall be a list of active role records, not a nested wrapper such as `roles.roles`.

When the Account has no active role assignments, `roles` shall be an empty list.

Rationale:
The response must expose the Account identity and current authorization-facing role summary in a shape that is easy for clients to consume.

Valid examples:
- An Account with two active roles returns two role objects in `roles`.
- An Account with no active roles returns `"roles": []`.

Invalid examples:
- Returning `"roles": { "roles": [] }`.
- Returning deleted or previous role assignments in `roles`.

---

### REQ-ACCOUNT-007: Account response data exclusions
Account record responses shall not expose passwords, password hashes, access tokens, refresh tokens, authentication sessions, soft-delete fields, or row audit metadata.

Rationale:
Ordinary Account records are for identity and current role visibility. Sensitive credentials, session artifacts, and audit metadata belong to separate capabilities with their own authorization rules.

Valid examples:
- The response includes `id`, `email`, `displayName`, and active `roles`.
- The response omits `createdAt`, `updatedAt`, `deletedAt`, and audit actor identifiers.

Invalid examples:
- Returning `passwordHash` in an Account response.
- Returning refresh-token identifiers or values in an Account response.
- Returning `deletedAt` in an ordinary Account response.

---

### REQ-ACCOUNT-008: Current Account context
The system shall expose `GET /accounts/me` for an authenticated caller to retrieve the caller's current Account context without requiring `ACCOUNT_GET`.

The public route shall resolve under the `/api` base as `GET /api/accounts/me` and shall return this shape:

```json
{
  "id": "<account UUID>",
  "email": "user@example.com",
  "displayName": "Eduardo",
  "roles": [
    {
      "id": "<role UUID>",
      "name": "MEMBER",
      "description": "Member role",
      "systemManaged": true
    }
  ],
  "permissions": [
    "ACCOUNT_GET",
    "EVENT_SEARCH"
  ]
}
```

`roles` shall contain the caller's active role records according to `REQ-ACCOUNT-006`. `permissions` shall contain distinct permission codes currently effective for the caller.

Role names shall be descriptive Account data and shall not be authorization authorities. The endpoint and frontend shall use effective permission codes for capability visibility, while every protected backend operation shall enforce its own authorization rule.

The response shall follow the exclusions in `REQ-ACCOUNT-007`. A missing, soft-deleted, or no-longer-authenticatable current Account shall produce an authentication failure and shall not return partial Account context.

Rationale:
The frontend needs one current server-derived identity and capability snapshot after session restoration without decoding JWT claims or requiring the Account UUID in a self-view route.

Valid examples:
- An authenticated Account without `ACCOUNT_GET` retrieves its own current context.
- A permission removed from the current RBAC state is absent from a later response.

Invalid examples:
- The response includes refresh-token or row-audit data.
- The frontend treats `COORD` in `roles` as an authorization authority.
- An unauthenticated request receives Account context.

## Acceptance scenarios

```gherkin
Scenario: Coordinator retrieves another Account
  Given an active Account exists
  And the caller has the ACCOUNT_GET permission
  When the caller requests GET /accounts/{accountId}
  Then the system returns the account record
  And the response contains id, email, displayName, and active roles

Scenario: Account retrieves itself without ACCOUNT_GET
  Given an authenticated Account exists
  And the caller does not have the ACCOUNT_GET permission
  When the caller requests GET /accounts/{ownAccountId}
  Then the system returns the caller's account record

Scenario: Account cannot retrieve another Account without ACCOUNT_GET
  Given another active Account exists
  And the caller is authenticated
  And the caller does not have the ACCOUNT_GET permission
  When the caller requests GET /accounts/{otherAccountId}
  Then the system rejects the request with 403 Forbidden

Scenario: Missing Account is not found
  Given no visible Account exists with the requested identifier
  And the caller has the ACCOUNT_GET permission
  When the caller requests GET /accounts/{missingAccountId}
  Then the system rejects the request with 404 Not Found

Scenario: Search requires ACCOUNT_SEARCH
  Given the caller is authenticated
  And the caller does not have the ACCOUNT_SEARCH permission
  When the caller requests POST /accounts/search
  Then the system rejects the request with 403 Forbidden

Scenario: Empty search filters browse active Accounts
  Given the caller has the ACCOUNT_SEARCH permission
  When the caller searches Accounts with an empty filter list
  Then the system returns a paginated page of visible active Account records

Scenario: Search by role uses the public role field
  Given the caller has the ACCOUNT_SEARCH permission
  When the caller searches Accounts with role EQUALS "COORD"
  Then the system returns matching visible active Account records

Scenario: Account response excludes audit metadata
  Given an active Account exists
  And the caller may view the Account
  When the caller retrieves the account record
  Then the response does not contain row audit metadata

Scenario: Authenticated Account loads current context
  Given an authenticated active Account exists
  And the caller does not have ACCOUNT_GET
  When the caller requests GET /api/accounts/me
  Then the system returns the caller's Account record
  And the response contains distinct current permission codes
  And the response contains no credentials, tokens, sessions, or audit metadata
```

## Open questions

* Should a future cross-domain audit visibility Requirement Specification define a generic permission such as `AUDIT_GET` for row audit metadata across domain models?
* Should activity-log or action-history visibility use a separate generic permission from row audit metadata visibility?
* What exact endpoint shape should expose row audit metadata for resources that support it, such as `GET /accounts/{accountId}/audit`, `GET /audit/{resourceType}/{resourceId}`, or another cross-domain route?

## Out of scope

* Account registration, login, refresh, logout, and password management.
* Email verification and invitation flows.
* Account mutation, deactivation, restoration, or deletion workflows.
* Role assignment and removal workflows; see [Account Role Management](../rbac/account-role-management.md).
* Activity-log or action-history reads.
* Row audit metadata reads for Account records or other domain models.
* Developer maintenance access to soft-deleted Accounts.

## Related ADRs

* [ADR-0007: Use Same-Origin Browser Sessions with Layered CSRF Protection](../../decisions/0007-use-same-origin-browser-sessions-with-layered-csrf-protection.md)

## Related requirements

* [Browser Session and Frontend Integration](../authentication/browser-session-and-frontend-integration.md)

## Related videos

* None.
