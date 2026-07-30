# Exception Handling Guidelines

## 1. Purpose

This document defines the custom exception hierarchy and error handling rules for `gam-api`.

The application keeps its internal exception hierarchy small and relies on
structured data to translate failures into the public contract. The Accepted
[API Error and Authorization Contract](../requirements/platform/api-error-and-authorization-contract.md)
and any explicit owning feature override determine the public HTTP status,
top-level `code`, and `details` shape.

## 2. Core Architecture Rules

### 2.1. Public Classification Does Not Come from the Exception Class

An exception class is an internal transport for a failure. Its class name shall
not automatically become or select the public top-level error code.

Before translating an exception, classify the failure from the Accepted
contract and operation context:

| Failure semantics | Public default |
| --- | --- |
| Well-formed, correctly typed input violates requiredness, size, range, format, allowed-value, or relational constraints | `400 VALIDATION_ERROR` with `details.violations` |
| An owning Accepted Requirement Specification defines a more specific semantic input failure | That feature-owned `400` code and safe details, such as `INVALID_SEARCH_FILTER` |
| The request conflicts with current business state | The owning feature's `409 Conflict` contract |
| The caller lacks coarse or visible-target authority | `403 ACCESS_DENIED` |
| The caller is authorized to attempt the operation but a business or security invariant prohibits it | `403 FORBIDDEN_OPERATION` |
| The target is missing, soft-deleted, or deliberately hidden | `404 RESOURCE_NOT_FOUND` |

Do not create a new exception class for every entity.

* **Valid:** Throwing a shared `NotFoundException` or `ConflictException` with
  the structured context required by the owning contract.
* **Forbidden:** Creating `GamLocationNotFoundException`,
  `PresenceConflictException`, or `AccountAlreadyHasRoleException` merely to
  obtain a public code.

### 2.2. Structured Data Over String Messages

Exceptions or validation results must carry structured data sufficient for the
public response. Do not rely exclusively on free-form string messages.

Common validation failures must identify public `location`, public `field`,
semantic violation `code`, and a safe diagnostic `message`. Body fields use
documented JSON Pointers; request-wide or cross-field failures use `$`.
Implementations must not expose Java property paths, annotation names, class
names, rejected values, or raw exception text.

**Correct Usage:**

```java
throw NotFoundException.resource("Member", memberId);
```

## 3. The Exception Hierarchy

Business-level exceptions may extend the shared `ApplicationException` base and
carry structured context. Any stored internal code must already be selected
from an Accepted public contract; handlers shall not derive a public code from
the Java class name.

### 3.1. `NotFoundException` (404 Not Found)

**Usage:** Thrown when the owning visibility policy classifies a requested
resource as missing, soft-deleted, or deliberately hidden.

```java
// Replaces MemberNotFoundException, EventNotFoundException, etc.
throw NotFoundException.resource("Event", eventId);
```

### 3.2. `ConflictException` (409 Conflict)

**Usage:** Thrown when a request cannot be completed because it conflicts with the current state of the system.

* *Examples:* Email is already registered, an Account already has a specific
  Role, or a Presence is already registered for a Member/Event pair.

A state conflict shall not become `VALIDATION_ERROR` merely because submitted
input initiated the operation.

```java
throw ConflictException.resource(
        "Presence",
        "%s:%s".formatted(memberId, eventId),
        "Presence already registered for this member and event"
);
```

### 3.3. `ForbiddenOperationException` (403 Forbidden)

**Usage:** Thrown after authentication and applicable authorization succeed,
when a business or security invariant prohibits the requested transition.

* *Examples:* Trying to mutate a system-managed Role or a non-SUDO operation
that would remove the final current Coordinator.

Do not use `ForbiddenOperationException` for a missing permission or for a
hidden target. Those failures are `ACCESS_DENIED` and `RESOURCE_NOT_FOUND`,
respectively.

```java
throw ForbiddenOperationException.reason("System-managed roles cannot be edited, deleted, or disabled.");
```

### 3.4. `InvalidCommandException` (400 Bad Request)

`InvalidCommandException` is not a generic bucket for every application-layer
`400` and does not establish a public code by itself.

Use the common validation representation for well-formed, correctly typed input
constraints even when they are detected after Bean Validation. This includes:

* a missing or invalid required reason;
* an end date that violates its relationship to a begin date;
* underage or other domain-input constraints; and
* a correctly typed value that violates a documented format or allowed set.

Those failures produce `400 VALIDATION_ERROR`, `details.violations`, public
field paths, and semantic violation codes.

Use a feature-specific semantic `400` only when an owning Accepted Requirement
Specification explicitly defines it. For example, structured-search semantic
failures use `INVALID_SEARCH_FILTER` under `REQ-SEARCH-009` and
`REQ-SEARCH-010`. The exception or result must carry the safe details that the
owning specification requires.

Use `ConflictException` for business-state conflicts and
`ForbiddenOperationException` for forbidden business or security invariants.

## 4. API Error Response Shape

The `GlobalExceptionHandler` and Spring Security error handlers translate
failures into the stable `ApiErrorDTO` response.

Errors must include stable, machine-readable `details` populated from the structured exception data.

The fields have these responsibilities:

* `timestamp`: UTC occurrence time for diagnostics.
* `status`: Numeric HTTP status repeated in the body.
* `code`: Stable machine-readable discriminator used by clients.
* `message`: Human-readable explanation that clients must not parse for behavior.
* `details`: Structured error-specific context such as resource identifiers or invalid fields.

Do not add a redundant `error` field containing the generic HTTP reason phrase.

For `VALIDATION_ERROR`, the handler shall aggregate all detected violations,
deduplicate them, and order them deterministically by `location`, `field`, then
violation `code`. Security-filter failures must use the same envelope rather
than a framework-default response.

**Target JSON Shape:**

```json
{
  "timestamp": "2026-06-27T10:00:00Z",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "message": "Member not found with identifier 3f7...",
  "details": {
    "resource": "Member",
    "identifier": "3f7..."
  }
}
```

## 5. Allowed Custom Exceptions

While resource-specific exceptions (e.g., `MemberNotFoundException`) are
forbidden, highly specific custom exception classes are allowed only when they
carry distinct internal handling or deep domain meaning that the shared
hierarchy cannot accommodate.

**Valid Exceptions to Keep:**

* `InvalidPhoneNumberException`: Phone number parsing is a specialized value-object concern.
* `InvalidTokenFormatException` / `RefreshTokenExpiredException`: Token failures require specific authentication/session handling and have a completely different meaning than a missing business resource.

Keeping a specific class does not grant it a same-named public code.
`InvalidPhoneNumberException` still maps to the applicable common validation
contract when it represents a documented input constraint. Refresh-token
failures map to `INVALID_REFRESH_TOKEN` because `REQ-API-ERROR-008` defines
that public category, not because of the exception class name.
