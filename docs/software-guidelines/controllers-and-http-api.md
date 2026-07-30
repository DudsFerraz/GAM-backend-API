# Controllers and HTTP API Guidelines

## 1. Purpose

This document defines the structure, naming conventions, and responsibilities of HTTP controllers and API routes in `gam-api`.

Controllers act strictly as the entry point to the application. They are kept as thin as possible, acting only as HTTP translators that delegate actual work to the application layer.

## 2. Controller Responsibilities

Controllers enforce a strict boundary between the web layer and the application layer.

**Controllers Must:**

* Define HTTP routes, HTTP methods, and status codes.
* Receive and map HTTP input (Request DTOs, path variables, query parameters, and pagination arguments).
* Trigger input validation (e.g., using `@Valid`).
* Delegate execution exclusively to application classes (use cases or read operations).
* Return mapped `RDTO`s or HTTP response wrappers.
* Keep any controller `@PreAuthorize` annotation aligned with the authoritative
  pre-MVC coarse route policy and use it only as defense in depth.

**Controllers Must NOT:**

* Implement business workflows or domain rules.
* Mutate domain models or entities directly.
* Call multiple repositories to orchestrate a process.
* Build complex database queries or dynamic persistence specifications.
* Expose JPA entities as response bodies.

## 3. Route Naming and Structure

API routes follow RESTful conventions using standard HTTP verbs and predictable resource naming.

### 3.1. Plural Resource Paths

Resource paths always use plural nouns. Singular resource paths are strictly forbidden.

**Valid:**

```text
GET    /accounts
POST   /members
DELETE /events/{id}
```

**Forbidden:**

```text
GET    /account
POST   /member
DELETE /event/{id}
```

### 3.2. Nested Routes

Nested routes are used when a route represents a natural, dependent sub-resource.

**Example:**

```text
GET /members/{id}/presences
GET /events/{id}/presences
GET /roles/{id}/permissions
```

### 3.3. Intentional Action Routes

For operations that do not map cleanly to standard CRUD actions but represent specific business intents, use a descriptive verb at the end of the path using the `PATCH` or `POST` method.

**Example:**

```text
PATCH /members/{id}/activate
PATCH /members/{id}/deactivate
```

## 4. Search Endpoints

Search endpoints adapt their HTTP method based on the complexity of the criteria.

### 4.1. Structured Search (`POST /search`)

Use `POST /resource/search` when the endpoint receives a complex, structured search body (e.g., a payload containing multiple filters, nested fields, and dynamic comparison methods like `GREATER_THAN_OR_EQUAL` or `LIKE`).

**Example:**

```text
POST /members/search
POST /events/search
```

*Note: The controller only receives the `SearchDTO`. The parsing, field aliasing, and specification construction must happen in the application or infrastructure layer, never inside the controller.*

### 4.2. Simple Search (`GET`)

Use `GET` with query parameters only for simple, flat, and stable filters. Do not force complex or dynamic criteria trees into URL query parameters.

**Example:**

```text
GET /members?status=ACTIVE
```

## 5. HTTP Responses

Controllers must be explicit and type-safe about what they return.

### 5.1. Strictly Typed `ResponseEntity`

Always use typed `ResponseEntity` declarations. Raw `ResponseEntity` types (without generics) are forbidden.

**Valid Examples:**

```java
ResponseEntity<MemberRDTO>
ResponseEntity<Page<MemberRDTO>>
ResponseEntity<Void>
```

### 5.2. Empty Responses

For commands that succeed but return no body, return a `200 OK` or `204 No Content` with a `Void` type signature.

```java
public ResponseEntity<Void> deactivateMember(...) {
    // ...
    return ResponseEntity.ok().build();
}
```

### 5.3. Creation Endpoints (`201 Created`)

Endpoints that create a new resource must return a `201 Created` status code. They should include a `Location` header pointing to the URI where the newly created resource can be retrieved.

```java
public ResponseEntity<MemberRDTO> registerMember(...) {
    // ...
    return ResponseEntity.created(locationUri).body(responseDTO);
}
```

## 6. Authorization

Authorization follows the precedence in
[`REQ-API-ERROR-007`](../requirements/platform/api-error-and-authorization-contract.md#req-api-error-007-authentication-authorization-and-validation-precedence)
and the architecture in
[ADR-0023](../decisions/0023-enforce-coarse-route-authorization-before-mvc-parsing.md).

* **Authentication and Coarse Route Authorization:** Enforce protected-route
  authentication and coarse method/path permission policy in the Spring
  Security filter chain before Spring MVC parses request parameters or bodies.
  The policy may use request matchers or a custom `AuthorizationManager`, but
  it must not parse input or load a target resource.
* **Controller Method Security:** Retain `@PreAuthorize` only as defense in
  depth. It must not be the sole mechanism for coarse authorization, must not
  be stricter than or diverge from the pre-MVC policy, and must not prevent a
  documented target-specific alternative such as self-view.
* **Visibility and Target-Specific Authorization:** When a decision depends on
  a parsed identifier, visible target, ownership, lifecycle state, or
  relationship to the caller, delegate it to an application-layer policy after
  parsing and lookup. Controllers must not implement or orchestrate that
  policy.
* **Business and Security Invariants:** Evaluate target-specific authorization
  before application-layer invariants so that missing or hidden targets,
  visible forbidden targets, conflicts, and forbidden transitions retain their
  Accepted `404`, `403`, or `409` outcomes.
