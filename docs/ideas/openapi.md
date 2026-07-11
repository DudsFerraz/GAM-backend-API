# Swagger/OpenAPI documentation

## Purpose

Swagger/OpenAPI documents the backend API contract.

Use OpenAPI to document:

- Endpoints
- Request bodies
- Response bodies
- HTTP status codes
- Validation constraints visible to API consumers
- Authentication requirements
- Error response formats
- Examples

OpenAPI does not replace requirements. Requirements explain the business rule. OpenAPI exposes the API contract.

Example relationship:

```text
docs/requirements/common/name.md
    defines REQ-NAME-002 and REQ-NAME-003

Name.java
    implements the rule

NameFunctionalTest.java
    verifies the rule

OpenAPI schema
    documents the rule for API consumers
````

---

## OpenAPI documentation rule

Whenever an API request or response exposes a field governed by a requirement, the OpenAPI schema should reflect the externally visible rule. Example:

```yaml
firstName:
  type: string
  minLength: 2
  maxLength: 32
  example: Eduardo
```

---

**OpenAPI alone is not complete API documentation**. A robust documentation system normally has two complementary parts:

1. **API reference:** exact technical contract for every endpoint.
2. **Developer guide:** concepts, workflows, recommendations, examples, and integration policies.

## Recommended documentation architecture

```text
docs/
├── getting-started.md
├── authentication.md
├── domain-concepts.md
├── common-conventions.md
├── errors.md
├── pagination-filtering-sorting.md
├── frontend-workflows/
│   ├── user-registration.md
│   ├── authentication-flow.md
│   └── project-management.md
├── versioning-and-deprecation.md
└── changelog.md

src/main/java/
└── controllers and DTOs with OpenAPI annotations

Generated artifact:
└── openapi.yaml
```

### The developer guide should explain

* What the API is responsible for.
* Base URLs and environments.
* Authentication and authorization.
* Main domain concepts and terminology.
* Typical multi-endpoint workflows.
* Error format and recovery behavior.
* Pagination, filtering and sorting.
* Dates, time zones, money and decimal conventions.
* Nullability and optional fields.
* File upload and download behavior.
* Rate limiting, retry and idempotency rules.
* Versioning, deprecation and breaking-change policy.
* Test accounts, sample data and development environment.
* Changelog and migration guides.

### The endpoint reference should explain

For each operation, document:

* Purpose.
* When the frontend should use it.
* When it should not use it.
* Required authentication and roles.
* Preconditions.
* Path, query and body parameters.
* Validation rules.
* Business rules that are not obvious from the schema.
* Successful responses.
* Every expected error response.
* Side effects.
* Whether it is safe to retry.
* Example request and response.
* Related endpoints.

# Recommended stack

| Responsibility                    | Recommended tool        |
| --------------------------------- | ----------------------- |
| Formal API contract               | OpenAPI                 |
| Generate OpenAPI from Spring Boot | `springdoc-openapi`     |
| Interactive development reference | Swagger UI or Scalar    |
| Polished official reference       | Redoc/Redocly           |
| Specification linting             | Redocly CLI or Spectral |
| Detect breaking changes           | `oasdiff`               |
| Frontend TypeScript types         | `openapi-typescript`    |
| Contract/property testing         | Schemathesis            |
| Event or message documentation    | AsyncAPI                |

## Example of a properly documented endpoint

```java
@RestController
@RequestMapping("/api/projects")
@Tag(
    name = "Projects",
    description = "Create and manage projects owned by the authenticated user."
)
public class ProjectController {

    @PostMapping
    @Operation(
        operationId = "createProject",
        summary = "Create a project",
        description = """
            Creates a new draft project for the authenticated user.

            Recommended use:
            Call this endpoint when the user finishes the initial project form.

            Do not use:
            Do not call this endpoint for autosaving an existing project.
            Use PATCH /api/projects/{projectId} instead.

            The project is initially created with DRAFT status.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Project created successfully",
            content = @Content(
                schema = @Schema(implementation = ProjectResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "The request failed validation",
            content = @Content(
                schema = @Schema(implementation = ApiErrorDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Authentication is missing or invalid",
            content = @Content(
                schema = @Schema(implementation = ApiErrorDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "A conflicting project already exists",
            content = @Content(
                schema = @Schema(implementation = ApiErrorDTO.class)
            )
        )
    })
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request
    ) {
        // Implementation
    }
}
```

And the request DTO:

```java
public record CreateProjectRequest(

    @Schema(
        description = "Human-readable project name.",
        example = "Community food collection",
        minLength = 3,
        maxLength = 100
    )
    @NotBlank
    @Size(min = 3, max = 100)
    String name,

    @Schema(
        description = """
            Optional detailed description shown on the project page.
            Plain text only; HTML is not accepted.
            """,
        example = "Collect food donations for local families."
    )
    @Size(max = 2000)
    String description
) {}
```

Validation annotations such as `@NotNull`, `@Min`, `@Max` and `@Size` are understood by Springdoc and contribute to the generated schemas. Additional semantic information should be supplied with annotations such as `@Operation`, `@ApiResponse`, `@Parameter`, `@Schema`, `@Tag` and `@SecurityRequirement`.

# Code-first, design-first or hybrid?

## Code-first

You implement controllers and DTOs, annotate them, and generate OpenAPI from the application.

Advantages:

* Low maintenance overhead.
* Routes and Java types stay close to the documentation.
* Good fit for an existing Spring Boot project.
* Bean validation contributes automatically to the schema.

Main risk:

* Developers may document only types and status codes while omitting business meaning and recommended usage.

## Design-first

You write `openapi.yaml` before implementing the endpoint. The specification becomes the contract between backend and frontend.

Advantages:

* Frontend development can begin using mocks before backend implementation.
* API changes are explicitly reviewed.
* Stronger governance for public APIs or several independent teams.

Main risk:

* The implementation and specification can diverge unless contract validation is automated.

## Hybrid—which

For your existing Spring Boot backend:

* Generate structural information from the code with Springdoc.
* Explicitly annotate business behavior and errors.
* Keep long-form guides as Markdown.
* Export the generated OpenAPI file during CI.
* Treat the generated file as an artifact—not a second manually edited source.
* Generate TypeScript types for the frontend from that artifact.

This gives you low-friction maintenance while still producing documentation that can be used without backend implementation knowledge.

The important rule is:

> Never manually maintain two competing sources of truth.

Either the OpenAPI file drives the implementation, or the application generates the OpenAPI file.

# How to prevent outdated documentation

Documentation freshness should be enforced by the development workflow rather than relying on developers remembering to update a website.

A robust pull-request pipeline should:

1. Build and test the application.
2. Start the application or run the Springdoc generation plugin.
3. Export `openapi.yaml`.
4. Lint the specification.
5. Compare it with the version from the main branch.
6. Report breaking changes.
7. Run contract tests against the generated specification.
8. Generate frontend types.
9. Publish the documentation with the backend release.

Redocly CLI and Spectral can validate OpenAPI documents and enforce organizational rules such as requiring operation descriptions, examples, tags and error responses.

Example lint command:

```bash
npx @redocly/cli lint openapi.yaml
```

`oasdiff` can compare two OpenAPI descriptions and identify changes, including potentially breaking changes, which makes it appropriate for pull-request checks.

Schemathesis can generate test cases from an OpenAPI schema and exercise the implementation against the documented contract.

## Add documentation to your definition of done

An endpoint should not be considered complete until it has:

* A stable `operationId`.
* Summary and purpose.
* Recommended and discouraged usage.
* Authentication requirements.
* Complete request schema.
* Complete success response schema.
* Documented expected errors.
* Realistic examples.
* Business rules and side effects.
* Tests.
* Changelog entry when externally relevant.

You can enforce several of these requirements through custom linting rules.

# Frontend integration

The frontend should consume the generated OpenAPI contract directly.

For example, `openapi-typescript` converts OpenAPI 3.0/3.1 schemas into TypeScript types. ([openapi-ts.dev][8])

```bash
npx openapi-typescript ./openapi.yaml \
  --output ./frontend/src/api/generated/api-types.ts
```

Then frontend code can use contract-derived types rather than manually duplicating backend DTOs:

```typescript
import type { paths } from "./generated/api-types";

type CreateProjectRequest =
  paths["/api/projects"]["post"]["requestBody"]["content"]["application/json"];

type CreateProjectResponse =
  paths["/api/projects"]["post"]["responses"]["201"]["content"]["application/json"];
```

This does not replace runtime validation or frontend models, but it catches many accidental incompatibilities during compilation.

For WebSockets, Kafka, RabbitMQ or other message-driven interfaces, OpenAPI is not the appropriate complete description. AsyncAPI provides a protocol-independent machine-readable format for channels, messages and message-driven APIs. ([asyncapi.com][9])

# A practical final setup

For your project, I would adopt:

```text
Spring controllers and DTOs
        ↓
springdoc-openapi
        ↓
generated openapi.yaml
        ├── Swagger UI/Scalar for development
        ├── Redoc for official reference
        ├── openapi-typescript for the frontend
        ├── Redocly/Spectral linting
        ├── oasdiff breaking-change checks
        └── Schemathesis contract tests

Handwritten Markdown
        ├── quickstart
        ├── concepts
        ├── frontend workflows
        ├── errors and conventions
        └── versioning/changelog
```

The result is not merely “Swagger documentation.” It is a versioned **API contract and frontend integration portal** that is generated, tested and released together with the backend.


all of the content contained in the "idea" file are just ideas, which should all be reviewd and questioned about. Much of what is written above might be out of scope for this project (consider other scope decisions already made along the docs), the main goal is to make sure that a frontend developer can easily interact with the backend API.
