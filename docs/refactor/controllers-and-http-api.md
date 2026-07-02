# Controllers and HTTP API

Date: 2026-06-25

## 1. Purpose

This document defines how controllers and HTTP routes must be refactored.

Controllers must stay thin. They receive HTTP input, delegate to application classes, and return response DTOs. Business workflows, persistence decisions, domain rules, and search construction must not live in controllers.

## 2. Current Shape

The current API has controllers such as:

```text
AccountController
MemberController
EventController
LocationController
RoleController
AuthController
```

Several resource paths are singular:

```text
/account
/member
```

Search endpoints currently use:

```text
POST /account/search
POST /member/search
POST /event/search
```

These search endpoints receive a structured request body:

```java
public record SearchDTO(
        @Valid List<SpecificationFilterDTO> filters
) {
}
```

Each filter contains:

```text
field
value
comparationMethod
```

The current comparison methods include:

```text
EQUALS
LIKE
GREATER_THAN_OR_EQUAL
LESS_THAN_OR_EQUAL
IN
```

## 3. Controller Responsibilities

Controllers must:

- define HTTP routes;
- receive request DTOs, path variables, query parameters, and pagination arguments;
- trigger validation;
- delegate to application classes;
- return `RDTO`s or HTTP response wrappers;
- create `Location` headers for creation endpoints when relevant;
- expose authorization annotations when method-level security is used.

Controllers must not:

- implement business workflows;
- mutate domain models directly;
- call multiple repositories to orchestrate use cases;
- build complex persistence specifications directly;
- contain domain rules;
- expose JPA entities as response bodies.

## 4. Resource Path Shape

Resource paths must use plural nouns.

Use:

```text
/accounts
/members
/events
/locations
/roles
/permissions
/presences
```

Do not use:

```text
/account
/member
/event
```

Nested routes are valid when the route represents a natural sub-resource.

Examples:

```text
GET /members/{id}/presences
GET /events/{id}/presences
GET /roles/{id}/permissions
```

## 5. Search Endpoints

`POST /search` is valid when the endpoint receives a structured search body.

The current codebase does not fail this rule merely because it uses `POST /search`. The current search body supports multiple filters, comparison methods, nested fields, and typed conversions. That is complex enough to justify a request body.

Keep this shape for structured search:

```text
POST /members/search
POST /accounts/search
POST /events/search
```

Use `GET` with query parameters only for simple and stable filters.

Example:

```text
GET /members?status=ACTIVE
```

Do not force complex structured filters into query parameters.

## 6. Search Concern Boundary

The main problem with the current search endpoints is not the HTTP method.

The main problem is that the generic search model exposes persistence-like field paths, such as:

```text
account.accountRoles.role.name
```

That concern belongs to the dynamic search specification refactor, not to the controller refactor.

Controllers may receive the search DTO, but the allowed filters, field aliases, parser rules, and specification construction must live outside the controller.

## 7. ResponseEntity Usage

Use typed `ResponseEntity` declarations.

Use:

```java
ResponseEntity<MemberRDTO>
ResponseEntity<Page<MemberRDTO>>
ResponseEntity<Void>
```

Do not use raw `ResponseEntity`.

For commands that return no body, use:

```java
return ResponseEntity.ok().build();
```

with:

```java
ResponseEntity<Void>
```

## 9. Creation Endpoints

Creation endpoints should return `201 Created` when a resource is created.

Use:

```text
POST /members
```

with:

```java
return ResponseEntity.created(location).body(response);
```

The `Location` header should point to the created resource when the created resource can be retrieved by URL.

## 10. Authorization

Method-level authorization can remain visible in controllers through `@PreAuthorize`.

Controller annotations are acceptable for endpoint-level authorization, but complex authorization decisions must not be implemented inside controllers.

If an authorization decision depends on domain state, use a dedicated security component or application-layer decision point.

## 11. Target Examples

Member routes should move toward:

```text
POST   /members
GET    /members/{id}
POST   /members/search
PATCH  /members/{id}/activate
PATCH  /members/{id}/deactivate
GET    /members/{id}/presences
```

Account routes should move toward:

```text
GET    /accounts/{id}
POST   /accounts/search
```

Event routes should move toward:

```text
POST   /events
GET    /events/{id}
POST   /events/search
GET    /events/{id}/presences
```

## 12. Refactor Instructions

For each controller:

1. Move the controller into the feature `web` package.
2. Change singular resource paths to plural paths.
3. Do not add `/api/v1` in this refactor.
4. Keep controller methods thin.
5. Delegate workflows to application classes.
6. Keep structured search endpoints as `POST /search`.
7. Use typed `ResponseEntity`.
8. Return `201 Created` with `Location` for creation endpoints where appropriate.
9. Do not expose JPA entities.
10. Do not implement business rules inside controllers.

## 13. Refactor Order

Apply this subject feature by feature:

1. `member`
2. `account`
3. `event`
4. `location`
5. `role`
6. `auth`

Start with `member` because it contains creation, retrieval, search, activation/deactivation commands, nested presence routes, validation, pagination, and authorization.
