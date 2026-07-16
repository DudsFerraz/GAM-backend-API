# GAM Frontend API Guide

This guide is the entry point for Frontend Developers integrating with the GAM backend. It explains the cross-cutting contract and links to the authoritative requirements for business behavior.

> **Implementation status:** The accepted OpenAPI requirements and ADR define the target contract. The documentation routes and release artifacts become operational after the corresponding production and build tooling is implemented.

## Contract locations

The public same-origin API base is:

```text
/api
```

Documentation is available at:

```text
/api/docs          Swagger UI
/api/openapi.json  Live OpenAPI 3.1 contract
```

Both routes are readable without authentication. Swagger UI can execute requests in development and is read-only in production.

The live JSON document and the release `openapi.yaml` express the same generated contract. The YAML form is published as an immutable backend GitHub Release artifact and is not committed or edited manually.

## Finding and understanding an operation

Operations are grouped by consumer-facing capabilities such as Authentication, Accounts, Members, Events, and RBAC.

Each operation documents:

- a stable `operationId`;
- its purpose;
- required authentication;
- parameters and request body;
- success and expected error responses;
- validation constraints; and
- synthetic examples.

Use Requirement Specifications for business behavior that extends beyond the HTTP shape.

## Authentication overview

Protected endpoints use an access token through:

```http
Authorization: Bearer <access-token>
```

The frontend keeps the access token only in memory. The browser stores the refresh token in an HTTP-only cookie; frontend JavaScript must never read or persist it.

The browser bootstrap flow is:

1. `GET /api/auth/csrf`
2. `POST /api/auth/refresh` with CSRF proof and the browser-managed refresh cookie
3. Store the returned access token in memory
4. `GET /api/accounts/me` with the bearer token

Login, refresh, and logout use the accepted CSRF proof and origin-validation contract. See [Browser Session and Frontend Integration](../requirements/authentication/browser-session-and-frontend-integration.md) for authoritative behavior.

## Common error response

JSON errors use one envelope:

```json
{
  "timestamp": "2026-07-14T17:30:00Z",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "message": "Member not found with the supplied identifier.",
  "details": {
    "resource": "Member",
    "identifier": "0190d5d4-52b3-7d30-a8d3-64b70d6c3142"
  }
}
```

Frontend behavior branches on the stable `code`, never by parsing `message`. Use `details` for structured context such as invalid fields or a conflicting resource.

## Pagination and sorting

Paged operations use:

```text
page=0             first page; zero-based
size=20            default; maximum 100
sort=field,asc     repeatable
```

Example:

```text
?page=0&size=20&sort=surname,asc&sort=firstName,asc
```

Each operation lists its allowed sort fields and default ordering. Unsupported fields, directions, or sizes above `100` return `400 Bad Request`.

Paged responses use:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

Filtering remains endpoint-specific. Read the operation's request schema instead of assuming that every filter applies to every resource.

## Common data formats

| Concept | API representation | Frontend rule |
| --- | --- | --- |
| UUID | String with UUID format | Treat as opaque |
| Calendar date | `2026-07-14` | Do not timezone-convert |
| Absolute timestamp | `2026-07-14T17:30:00Z` | Convert from UTC for display |
| Enum | Documented uppercase string | Never depend on an ordinal |

Requiredness and nullability are separate. An omitted property and explicit `null` have different meanings unless an operation explicitly documents otherwise.

## Generate TypeScript types

Use `openapi-typescript` to generate request and response types.

For local development:

```powershell
npx openapi-typescript http://<frontend-development-origin>/api/openapi.json -o src/api/generated/gam-api.ts
```

For a release or production frontend build, use the explicitly pinned `openapi.yaml` asset from the selected immutable backend GitHub Release.

Generated types provide compile-time checking. They do not send requests, validate responses at runtime, or choose the frontend's HTTP client.

## Development data

Use synthetic local fixture data. Documentation must not contain shared static credentials, real personal information, or secrets that work in production. Follow the backend's fixture or seed instructions when those are available.

## Authoritative workflow links

- [Authentication and Account Registration](../requirements/authentication/authentication-and-registration.md)
- [Browser Session and Frontend Integration](../requirements/authentication/browser-session-and-frontend-integration.md)
- [Web Delivery and Frontend Contract](../requirements/platform/web-delivery-and-frontend-contract.md)
- [OpenAPI and Frontend API Documentation](../requirements/platform/openapi-and-frontend-api-documentation.md)
- [OpenAPI Developer Workflow](../dev-guidelines/openapi-workflow.md)
- [Frontend-Backend Integration](../dev-guidelines/front-back/front-back-integration.md)
