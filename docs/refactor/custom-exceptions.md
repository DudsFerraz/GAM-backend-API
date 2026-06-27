# Custom Exceptions

Date: 2026-06-27

## 1. Purpose

This document defines how custom exceptions must be refactored.

The project currently has many resource-specific exception classes, such as:

```text
AccountNotFoundException
MemberNotFoundException
EventNotFoundException
LocationNotFoundException
PresenceNotFoundException
RoleNotFoundException
PermissionNotFoundException
AccountConflictException
MemberAccountConflictException
PresenceConflictException
```

Most of these classes only extend `RuntimeException` and add no behavior. They make the codebase larger without adding meaningful domain distinction.

The goal is to keep exception semantics clear while reducing one-class-per-resource boilerplate.

## 2. Main Rule

Exception type must represent the error category.

Structured exception data must represent the affected resource.

Use:

```text
NotFoundException(resource = "Member", identifier = memberId)
NotFoundException(resource = "Event", identifier = eventId)
```

Do not create separate classes by default:

```text
MemberNotFoundException
EventNotFoundException
LocationNotFoundException
```

## 3. Exception Hierarchy

Use a small application exception hierarchy:

```text
ApplicationException
  NotFoundException
  ConflictException
  ForbiddenOperationException
  InvalidCommandException
```

Add new exception categories only when they map to a distinct handling rule, HTTP status, metadata shape, or domain meaning.

## 4. ApplicationException

Use `ApplicationException` as the base class for application-level failures that must be translated into predictable API errors.

Target shape:

```java
public abstract class ApplicationException extends RuntimeException {

    private final String code;
    private final String resource;
    private final Object identifier;

    protected ApplicationException(String code, String message, String resource, Object identifier) {
        super(message);
        this.code = code;
        this.resource = resource;
        this.identifier = identifier;
    }

    public String getCode() {
        return code;
    }

    public String getResource() {
        return resource;
    }

    public Object getIdentifier() {
        return identifier;
    }
}
```

The exact implementation can evolve, but the exception must carry structured data instead of relying only on message strings.

## 5. NotFoundException

Use `NotFoundException` for missing resources.

Target shape:

```java
public class NotFoundException extends ApplicationException {

    private NotFoundException(String resource, Object identifier) {
        super(
                "RESOURCE_NOT_FOUND",
                "%s not found with identifier %s".formatted(resource, identifier),
                resource,
                identifier
        );
    }

    public static NotFoundException resource(String resource, Object identifier) {
        return new NotFoundException(resource, identifier);
    }
}
```

Usage:

```java
throw NotFoundException.resource("Member", memberId);
```

```java
throw NotFoundException.resource("Event", eventId);
```

`Member` and `Event` remain distinguishable through structured data:

```text
resource = Member
identifier = memberId
```

```text
resource = Event
identifier = eventId
```

The difference must not require separate exception classes.

## 6. ConflictException

Use `ConflictException` when the request cannot be completed because it conflicts with current state.

Examples:

```text
email already registered
account already has role
member already exists for account
presence already registered for member/event
role already has permission
```

Target shape:

```java
public class ConflictException extends ApplicationException {

    private ConflictException(String code, String message, String resource, Object identifier) {
        super(code, message, resource, identifier);
    }

    public static ConflictException resource(String resource, Object identifier, String message) {
        return new ConflictException("RESOURCE_CONFLICT", message, resource, identifier);
    }

    public static ConflictException reason(String message) {
        return new ConflictException("RESOURCE_CONFLICT", message, null, null);
    }
}
```

The current duplicate presence behavior must use conflict semantics, not not-found semantics.

Use:

```java
throw ConflictException.resource(
        "Presence",
        "%s:%s".formatted(memberId, eventId),
        "Presence already registered for this member and event"
);
```

Do not use:

```java
throw new PresenceNotFoundException("Presence already registered");
```

## 7. ForbiddenOperationException

Use `ForbiddenOperationException` when the authenticated user exists but the operation is not allowed.

Examples:

```text
trying to delete a system-managed role
trying to delete a system-managed permission
trying to remove a presence after the event is locked
trying to delete a member with historical presences
trying to access a resource hidden by security rules when the API should return 403
```

Security-sensitive endpoints may still choose to return 404 instead of 403 to avoid revealing resource existence. That decision belongs to the controller/security policy for that specific workflow.

## 8. InvalidCommandException

Use `InvalidCommandException` when the command is structurally valid JSON but violates application rules.

Examples:

```text
event end date is before begin date
birth date is in the future
partial email search value is too broad
phone search value has too few digits
required deletion reason is missing
```

This exception is different from Bean Validation failures. Bean Validation still handles annotation-level request validation.

## 9. GlobalExceptionHandler

`GlobalExceptionHandler` must handle the exception hierarchy by category.

Target handling:

```text
NotFoundException -> 404
ConflictException -> 409
ForbiddenOperationException -> 403
InvalidCommandException -> 400
```

Example handler:

```java
@ExceptionHandler(NotFoundException.class)
public ResponseEntity<ApiErrorDTO> handleNotFound(NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorDTO.from(ex)
    );
}
```

The error response must include stable machine-readable information.

Example:

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "Member not found with identifier 3f7...",
  "details": {
    "resource": "Member",
    "identifier": "3f7..."
  }
}
```

## 10. ApiErrorDTO

`ApiErrorDTO` should evolve toward a stable response shape.

Target shape:

```text
timestamp
status
error
code
message
details
```

`details` can be empty, but it must support structured values such as:

```json
{
  "resource": "Member",
  "identifier": "3f7..."
}
```

Do not rely only on free-form message text for application errors.

## 11. Classes To Remove By Default

Remove resource-specific exception classes when they only represent an error category already covered by the shared hierarchy.

Candidates:

```text
AccountNotFoundException
MemberNotFoundException
EventNotFoundException
LocationNotFoundException
PresenceNotFoundException
RoleNotFoundException
PermissionNotFoundException
RolePermissionNotFoundException
AccountRoleNotFoundException
MissaNotFoundException
OratorioNotFoundException
OratorianoNotFoundException
AccountConflictException
MemberAccountConflictException
PresenceConflictException
AccountAlreadyHasRoleException
```

Replace them with:

```text
NotFoundException
ConflictException
ForbiddenOperationException
InvalidCommandException
```

## 12. Classes That Can Stay

Keep custom exception classes only when they carry distinct handling, metadata, or domain meaning.

Examples that may remain separate:

```text
InvalidPhoneNumberException
InvalidTokenFormatException
RefreshTokenExpiredException
```

Rationale:

- Phone number parsing is a specific value-object concern.
- Token failures may need specific authentication/session responses.
- Expired refresh tokens have a different meaning from a missing business resource.

These classes can still extend `ApplicationException` later if that improves error handling consistency.

## 13. Refactor Instructions

For each exception use:

1. identify the error category;
2. replace resource-specific not-found classes with `NotFoundException.resource(resource, identifier)`;
3. replace duplicate/existing-state failures with `ConflictException`;
4. replace blocked operations with `ForbiddenOperationException`;
5. replace application rule failures with `InvalidCommandException`;
6. update `GlobalExceptionHandler` to handle shared exception categories;
7. update `ApiErrorDTO` to expose stable error codes and structured details;
8. delete empty resource-specific exception classes after all usages are migrated.

## 14. Refactor Order

Apply exception cleanup in this order:

1. Introduce `ApplicationException` and shared exception classes.
2. Update `GlobalExceptionHandler`.
3. Update `ApiErrorDTO`.
4. Migrate not-found exceptions.
5. Migrate conflict exceptions.
6. Migrate forbidden operation cases.
7. Migrate invalid command cases.
8. Delete unused resource-specific exception classes.

Start with not-found exceptions because they are the most repetitive and easiest to migrate safely.
