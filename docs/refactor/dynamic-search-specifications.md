# Dynamic Search Specifications

Date: 2026-06-26

## 1. Purpose

This document defines how dynamic search specifications must be refactored.

Dynamic search is useful because it avoids creating repository methods for every filter combination. The current implementation already has a generic `SearchDTO`, comparison methods, and a `SpecificationFactory`. The problem is that public API filters currently behave too much like direct JPA field paths.

The search API must expose an explicit product contract. Internal JPA paths, joins, parser details, and custom predicate behavior must stay behind the resource-specific filter definitions.

## 2. Current Shape

The current search request uses:

```java
public record SearchDTO(
        @Valid List<SpecificationFilterDTO> filters
) {
}
```

Each filter uses:

```java
public record SpecificationFilterDTO(
        @NotNull @NotBlank String field,
        @NotNull @NotBlank String value,
        @NotNull ComparationMethods comparationMethod
) {
}
```

The current comparison methods are:

```text
EQUALS
LIKE
GREATER_THAN_OR_EQUAL
LESS_THAN_OR_EQUAL
IN
```

Current public search endpoints exist for:

```text
Account
Member
Event
```

`Presence` and `RolePermission` use specifications internally, but they are not public dynamic search contracts at this stage.

## 3. Main Rule

The public filter name must be separated from the internal persistence target.

Use this flow:

```text
public field -> validated method -> parsed value -> internal target or custom predicate
```

Do not expose nested persistence paths through the API.

Do not expose:

```text
account.accountRoles.role.name
location.id
requiredPermission.id
```

Expose public aliases instead:

```text
role
roleName
locationId
requiredPermissionId
```

## 4. Filter Definition Shape

Each searchable resource must define its own allowed filters.

Each allowed filter must define:

1. public field name;
2. internal JPA path or custom predicate;
3. allowed comparison methods;
4. parser/converter rules;
5. validation rules when needed.

The implementation can use a structure similar to:

```java
public record FilterDefinition(
        String publicField,
        String internalTarget,
        Set<ComparationMethods> allowedMethods,
        Map<ComparationMethods, Function<JsonNode, Object>> parsers
) {
}
```

The exact implementation can vary, but method-specific parsing must be possible. Some fields need different parsing for `EQUALS` and `LIKE`.

## 5. DTO Value Shape

The current `SpecificationFilterDTO.value` is a `String`. This is not enough for every approved search method.

`IN` needs collection support. The request DTO must allow scalar and collection values.

Target JSON shape:

```json
{
  "filters": [
    {
      "field": "status",
      "value": ["ACTIVE", "PENDENT"],
      "comparationMethod": "IN"
    }
  ]
}
```

Use a value type that can represent both scalar and collection input. `JsonNode` is a practical option because parsing stays under the filter definition instead of being guessed generically.

## 6. Parser Rules

Parser output must match the persistence attribute type used by the specification.

Use:

```text
UUID fields -> UUID
enum fields -> enum type
LocalDate fields -> LocalDate
Instant fields -> Instant
email EQUALS -> MyEmail
phoneNumber EQUALS -> MyPhoneNumber
```

Audit and event datetime fields must parse to `Instant`, not `OffsetDateTime`.

## 7. Custom Predicate Rules

Some approved filters must not be implemented as simple field-path mapping.

Use custom predicates for:

```text
Member name LIKE
email LIKE
phoneNumber LIKE
```

`Member name LIKE` must search both:

```text
name.firstName
name.surname
```

The API must expose only:

```text
name
```

It must not expose the internal `Name` structure as required frontend knowledge.

## 8. Partial Email Search

Partial email search is intentionally supported.

`email EQUALS` and `email LIKE` must use different parsing behavior:

```text
email EQUALS -> MyEmail
email LIKE -> normalized String
```

For `email LIKE`, apply these rules:

1. trim and lowercase the value;
2. reject blank values;
3. reject values shorter than 3 characters;
4. reject values starting with `@`;
5. reject domain-only searches like `@gmail`, `gmail.com`, and `hotmail.com`;
6. if the value contains `@`, require at least 2 characters before `@`.

The `LIKE` predicate must search the stored database email string.

## 9. Partial Phone Number Search

Partial phone number search is intentionally supported.

`phoneNumber EQUALS` and `phoneNumber LIKE` must use different parsing behavior:

```text
phoneNumber EQUALS -> MyPhoneNumber
phoneNumber LIKE -> normalized digit String
```

For `phoneNumber LIKE`, apply these rules:

1. remove spaces, hyphens, parentheses, plus signs, and formatting;
2. reject values with fewer than 4 digits.

The `LIKE` predicate must search the stored E.164 phone number string.

## 10. Account Filters

Approved public filters for `Account`:

| Public field | Internal target | Methods | Notes |
|---|---|---|---|
| `id` | `id` | `EQUALS`, `IN` | UUID |
| `displayName` | `displayName` | `LIKE`, `EQUALS` | Public field |
| `email` | `email` | `EQUALS`, `LIKE` | `EQUALS` parses to `MyEmail`; `LIKE` uses normalized string rules |
| `roleName` | `accountRoles.role.name` | `EQUALS`, `IN` | Replaces exposed nested path |
| `createdAt` | `createdAt` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | Parse to `Instant` |
| `updatedAt` | `updatedAt` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | Parse to `Instant` |

## 11. Member Filters

Approved public filters for `Member`:

| Public field | Internal target | Methods | Notes |
|---|---|---|---|
| `id` | `id` | `EQUALS`, `IN` | UUID |
| `name` | custom predicate | `LIKE` | Searches `name.firstName` and `name.surname` |
| `birthDate` | `birthDate` | `EQUALS`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | LocalDate |
| `phoneNumber` | `phoneNumber` | `EQUALS`, `LIKE` | `EQUALS` parses to `MyPhoneNumber`; `LIKE` uses normalized digit string rules |
| `status` | `status` | `EQUALS`, `IN` | `PENDENT`, `ACTIVE`, `INACTIVE` |
| `accountId` | `account.id` | `EQUALS` | Replaces `account.id` |
| `email` | `account.email` | `EQUALS`, `LIKE` | Public alias for account email |
| `role` | `account.accountRoles.role.name` | `EQUALS`, `IN` | Public alias for role name |
| `createdAt` | `createdAt` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | Parse to `Instant` |
| `updatedAt` | `updatedAt` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | Parse to `Instant` |

## 12. Event Filters

Approved public filters for `Event`:

| Public field | Internal target | Methods | Notes |
|---|---|---|---|
| `id` | `id` | `EQUALS`, `IN` | UUID |
| `title` | `title` | `LIKE`, `EQUALS` | Public field |
| `description` | `description` | `LIKE` | Public field |
| `locationId` | `location.id` | `EQUALS` | Replaces `location.id` |
| `requiredPermissionId` | `requiredPermission.id` | `EQUALS` | Public alias |
| `requiredPermissionName` | `requiredPermission.name` | `EQUALS`, `IN` | Public alias |
| `type` | `type` | `EQUALS`, `IN` | `GENERIC`, `ORATORIO`, `MISSA` |
| `status` | `status` | `EQUALS`, `IN` | `SCHEDULED`, `COMPLETED`, `CANCELLED` |
| `beginDate` | `beginDate` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | Parse to `Instant` |
| `endDate` | `endDate` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | Parse to `Instant` |
| `createdAt` | `createdAt` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | Parse to `Instant` |
| `updatedAt` | `updatedAt` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | Parse to `Instant` |

## 13. Validation Behavior

Invalid filters must fail fast.

Reject:

1. unknown public fields;
2. unsupported comparison methods for a field;
3. invalid scalar values;
4. invalid `IN` values;
5. blank values when the operation requires text;
6. partial email and phone searches that fail their field-specific rules.

Errors must name the public field, not the internal JPA path.

Use:

```text
Invalid filter value for email.
```

Do not expose:

```text
Invalid filter value for account.email.
```

## 14. Specification Construction

The generic specification builder can remain, but it must receive already-approved internal filters.

The conversion boundary must happen before specification construction:

```text
SearchDTO
  -> ResourceFilterConverter
    -> allowed filter definition
      -> parsed internal SpecificationFilter or custom Specification
        -> SpecificationBuilder
```

If a filter uses a custom predicate, the filter converter can produce a `Specification` directly, or the internal filter model can support both path-based and custom-predicate filters.

Do not let `SpecificationBuilder` accept arbitrary public field strings as JPA paths.

## 15. Refactor Instructions

For each public search resource:

1. Create a resource-specific allowed filter definition.
2. Replace raw parser maps with filter definitions that include public field, internal target, allowed methods, and parser rules.
3. Update `SpecificationFilterDTO.value` so it can represent scalar and collection values.
4. Add method-specific parsing for fields where `EQUALS` and `LIKE` behave differently.
5. Add custom predicates for `Member name`, `email LIKE`, and `phoneNumber LIKE`.
6. Parse datetime filters as `Instant`.
7. Reject unknown fields and unsupported methods before building specifications.
8. Keep controllers thin; controllers must not know internal filter paths.
9. Return validation errors using public field names.

## 16. Refactor Order

Apply dynamic search cleanup in this order:

1. `member`
2. `account`
3. `event`

Start with `member` because it contains the most important custom cases: `name`, `email`, `phoneNumber`, `role`, and joined account fields.
