# Project Architecture Review

Date: 2026-06-24

## 1. Purpose

This document evaluates the overall software architecture of `gam-api`, with special attention to whether the current object-oriented, Java, and Spring patterns are helping the project or creating unnecessary complexity.

Enterprise Java often tolerates more structure in exchange for explicit boundaries. This project currently leans strongly toward explicit boundaries. Some of that is valuable; some of it appears heavier than the current problem requires.

The main architectural problem is **accumulated ceremony**. The codebase applies several enterprise patterns at once: domain/entity separation, one interface per use case, one Spring implementation per interface, DTOs per operation, mappers per aggregate, custom exceptions per resource, custom repositories, custom specifications, and method-level security. Each pattern can be defensible in isolation, but together they create many files and indirections for relatively simple CRUD-oriented workflows.

This document intentionally does not define the testing strategy. Whenever architecture decisions need test coverage, this file only references the dedicated tests document: [`tests.md`](../software-guidelines/tests.md).

The target is a **feature-oriented Spring API**: internally modular, deployed as this backend application, independent from the frontend repository, with fewer redundant interfaces, clearer application services, and explicit domain rules where they actually exist.

## 2. Current Architectural Shape

The application is a Spring Boot 3.5 / Java 21 REST API using:

- Spring Web for controllers.
- Spring Security for authentication and authorization.
- Spring Data JPA for persistence.
- PostgreSQL with Flyway migrations.
- MapStruct for mapping.
- Lombok for persistence model boilerplate.
- Custom JWT access tokens and database-backed refresh tokens.
- Custom RBAC with roles and permissions.
- Custom soft-delete repository behavior.
- Custom dynamic search specifications.

Before the interface and loader cleanup, most features followed this shape:

```text
Controller
  -> use-case interface
    -> Spring use-case implementation
      -> repository / other use cases
      -> mapper
      -> domain object
      -> entity
      -> response DTO
```

Representative example:

```text
MemberController
  -> RegisterMember
    -> SpringRegisterMember
      -> MemberRepository
      -> GetAccountInstance
      -> MemberMapper
      -> Member
      -> MemberEntity
      -> RegisterMemberRDTO
```

This style resembles a partial clean architecture / hexagonal architecture approach. It is not wrong, but it is only worth its cost if the business rules and replaceable boundaries actually benefit from that separation.

## 3. A Feature-Oriented Spring Boot API

For this project, "a feature-oriented Spring Boot API" means:

- The backend API is one deployable Spring Boot application.
- The frontend remains a separate project and communicates through API contracts.
- The backend owns its own database and business rules.
- Backend features are internally separated into modules/packages.
- Features share one runtime, one deployment pipeline, and one database.
- Internal module boundaries are enforced by package structure, naming, dependency direction, and tests.
- The codebase avoids distributed-system complexity unless there is a real operational reason for it.

A modular backend package shape should look like:

```text
br.org.gam.api
  account
  member
  event
    Missa
    Oratorio
  location
  oratoriano
  presence
  rbac
    AccountRole
    Permission
    Role
    RolePermission
  security
  shared
```

This differs from a tangled monolith because each feature has clear internal boundaries. It differs from microservices because the modules are not independently deployed services.

## 4. Package Organization

The package structure must move toward feature-oriented packages with internal layer folders:

- feature packages use `application`, `domain`, `persistence`, and `web` layer folders when those layers exist;
- single-file application use cases live directly under `application/useCases`; use `application/useCases/<UseCaseName>` only when the use case has at least two co-located files;
- specialized event behavior lives under `event.Missa` and `event.Oratorio`;
- RBAC is split into `rbac.AccountRole`, `rbac.Permission`, `rbac.Role`, and `rbac.RolePermission`;
- cross-feature infrastructure moves to `security` or `shared`.

Detailed refactor instructions for this subject are defined in [`package-organization.md`](package-organization.md).

## 5. Domain Model vs JPA Entity Model

The project separates domain objects such as `Account`, `Member`, `Event`, and `Oratorio` from persistence objects such as `AccountEntity` and `MemberEntity`.

The domain/entity split will not be treated as a mandatory pattern for every persisted model. Each model must justify the split through business behavior, invariants, lifecycle rules, or important domain language.

Detailed refactor instructions for this subject are defined in [`domain-vs-jpa-entity-model.md`](domain-vs-jpa-entity-model.md).

## 6. Services and Use Cases

The current design uses small use-case services, often one package per operation.

Application services must keep expressive action/read names without unnecessary suffixes. Repeated required loading is handled by explicit domain/entity loaders instead of vague former `GetXInstance` classes.

Detailed refactor instructions for this subject are defined in [`services-and-use-cases.md`](services-and-use-cases.md).

## 7. Interfaces and Implementations

Before topic 7 was implemented, the codebase contained many application-service interfaces and Spring implementations, such as:

```text
RegisterMember -> SpringRegisterMember
GetAccount -> SpringGetAccount
GetAccountInstance -> SpringGetAccountInstance
SearchMembers -> SpringSearchMembers
CreateEvent -> SpringCreateEvent
```

There were about 64 Java interfaces in the main source tree before this cleanup.

Application-service interfaces must be removed by default. Repository interfaces, mapper interfaces, framework contracts, and shared infrastructure contracts remain valid when they represent real framework or infrastructure boundaries.

Detailed refactor instructions for this subject are defined in [`interfaces-and-implementations.md`](interfaces-and-implementations.md).

## 8. Mappers

The codebase uses MapStruct mappers, with about 13 mapper interfaces.

Mapper files must stay unified. A feature/model should use one `XMapper` file, and mapping methods must be organized by section inside that file.

Detailed refactor instructions for this subject are defined in [`mappers.md`](mappers.md).

## 9. Controllers and HTTP API

Controllers are mostly thin and delegate to services, which is good.

Controllers must stay thin, resource paths must be standardized, and `POST /search` remains valid for structured search bodies.

Detailed refactor instructions for this subject are defined in [`controllers-and-http-api.md`](controllers-and-http-api.md).

## 10. Dynamic Search Specifications

The project has a generic search model and specification factory that can build filters from field names and comparison methods.

Dynamic search must keep Spring Data Specifications internally, but public search filters must be explicitly defined per resource.

Detailed refactor instructions for this subject are defined in [`dynamic-search-specifications.md`](dynamic-search-specifications.md).

## 11. Persistence, Soft Delete, and Auditing

The project uses JPA entities, repositories, Flyway migrations, audit fields, soft-delete fields, and custom base repository behavior.

Soft delete is an internal security safeguard, not a user-facing feature. Administrators must not be able to hard delete, restore, or browse soft-deleted records through the application.

Detailed refactor instructions for soft delete are defined in [`persistence-soft-delete-policy.md`](persistence-soft-delete-policy.md).

Detailed refactor instructions for activity logging are defined in [`audit-log.md`](audit-log.md).

## 12. Custom Exceptions

The project has many custom exceptions, such as `AccountNotFoundException`, `MemberNotFoundException`, `PresenceConflictException`, and token exceptions.

Exception classes must be consolidated into a smaller hierarchy. Resource-specific not-found classes should disappear when they only repeat the same behavior.

Detailed refactor instructions for this subject are defined in [`custom-exceptions.md`](custom-exceptions.md).

## 13. Security and RBAC

RBAC is one of the more serious architectural areas in the project.

Security must stay permission-based. Roles are permission bundles, and system-managed roles/permissions must be protected from administrator edits.

Detailed refactor instructions for this subject are defined in [`security-and-rbac.md`](security-and-rbac.md).
