# Package Organization

Date: 2026-06-25

## 1. Purpose

This document defines the target package organization for `gam-api`.

The codebase must move toward feature-oriented packages with internal layer folders. The goal is to make each business area easy to navigate while keeping framework, persistence, web, and domain responsibilities explicit.

## 2. Current Problem

The current package structure groups many concepts under `br.org.gam.api.Entities`.

Examples:

```text
br.org.gam.api.Entities.account
br.org.gam.api.Entities.member
br.org.gam.api.Entities.events.generic
br.org.gam.api.Entities.RBAC.permission
br.org.gam.api.common.auth
br.org.gam.api.common.persistence
```

This creates several problems:

- `Entities` is capitalized, which goes against Java package conventions.
- `Entities` is conceptually misleading because it contains controllers, services, DTOs, mappers, exceptions, security, repositories, and domain objects, not only entities.
- Some package names encode implementation details, such as `services.registerMember.SpringRegisterMember`.
- Package names are deeper than the behavior requires.
- Feature boundaries exist, but layer responsibilities are not consistently expressed.

## 3. Target Shape

The target root package remains:

```text
br.org.gam.api
```

The main feature packages must be:

```text
br.org.gam.api.account
br.org.gam.api.member
br.org.gam.api.event
br.org.gam.api.location
br.org.gam.api.oratoriano
br.org.gam.api.presence
br.org.gam.api.rbac
br.org.gam.api.security
br.org.gam.api.shared
```

Each feature package should use internal layer folders when the layer exists:

```text
br.org.gam.api.member
  application
  domain
  persistence
  web
```

Do not create empty layer folders just to satisfy the structure. A folder exists only when it contains meaningful code.

Application workflows live under `application/useCases`. Create a use-case subdirectory only when the use case has at least two co-located files, such as an implementation class plus DTO/RDTO files:

```text
br.org.gam.api.member
  application
    useCases
      RegisterMember
        RegisterMember.java
        RegisterMemberDTO.java
        RegisterMemberRDTO.java
      Activation.java
      GetMember.java
      SearchMembers.java
```

Shared application helpers for the feature, such as mappers, common RDTOs, feature-owned security helpers, and temporary feature exceptions, may remain directly under `application` until later roadmap topics simplify them.

## 4. Layer Responsibilities

### 4.1. application

The `application` package contains application workflows, read operations, loaders, and operation-specific input/output objects.

Single-file operation classes live directly under `application/useCases`. Operation-specific application files live under `application/useCases/<UseCaseName>` only when that use case owns at least two co-located files.

Examples:

```text
member/application/useCases/RegisterMember/RegisterMember.java
member/application/useCases/Activation.java
member/application/useCases/SearchMembers.java
member/application/MemberDomainLoader.java
member/application/MemberEntityLoader.java
member/application/useCases/RegisterMember/RegisterMemberDTO.java
member/application/useCases/RegisterMember/RegisterMemberRDTO.java
```

The naming rules for this layer are defined in [`services-and-use-cases.md`](services-and-use-cases.md).

### 4.2. domain

The `domain` package contains rich domain models and domain-specific enums or supporting classes.

Examples:

```text
member/domain/Member.java
member/domain/MemberStatus.java
event/domain/Event.java
event/domain/EventStatus.java
event/domain/EventType.java
```

Only models approved as rich domain models should live here. The domain/entity decisions are defined in [`domain-vs-jpa-entity-model.md`](domain-vs-jpa-entity-model.md).

### 4.3. persistence

The `persistence` package contains JPA entities, repositories, persistence specifications, persistence converters, and persistence-only mapping details.

Examples:

```text
member/persistence/MemberEntity.java
member/persistence/MemberRepository.java
member/persistence/MemberSpecifications.java
account/persistence/AccountEntity.java
account/persistence/AccountRepository.java
```

JPA annotations, table names, relationship mappings, lazy-loading concerns, and persistence-only classes belong here.

### 4.4. web

The `web` package contains HTTP controllers and web-specific adapters.

Examples:

```text
member/web/MemberController.java
account/web/AccountController.java
event/web/EventController.java
```

Controllers must stay thin. They receive HTTP input, delegate to application classes, and return `RDTO`s or response wrappers.

### 4.5. security

The `security` package contains application security configuration and authentication/authorization infrastructure that is not owned by one business feature.

Examples:

```text
security/SecurityConfig.java
security/SecurityUtils.java
security/jwt/JwtAuthFilter.java
security/jwt/JwtService.java
```

Authentication workflows that behave like application features can use subpackages under `security` or a dedicated `auth` package if the boundary becomes clearer later.

### 4.6. shared

The `shared` package contains cross-feature technical and domain-support code.

Examples:

```text
shared/auditing
shared/persistence
shared/specification
shared/exception
```

Code belongs in `shared` only when more than one feature genuinely uses it. Feature-specific code must stay inside the feature package.

## 5. Feature Mapping

Use this current-to-target mapping:

| Current package | Target package |
| --- | --- |
| `Entities.account` | `account` |
| `Entities.member` | `member` |
| `Entities.events.generic` | `event` |
| `Entities.events.missa` | `event.Missa` |
| `Entities.events.oratorio` | `event.Oratorio` |
| `Entities.location` | `location` |
| `Entities.oratoriano` | `oratoriano` |
| `Entities.presence` | `presence` |
| `Entities.RBAC.accountRole` | `rbac.AccountRole` |
| `Entities.RBAC.permission` | `rbac.Permission` |
| `Entities.RBAC.role` | `rbac.Role` |
| `Entities.RBAC.rolePermission` | `rbac.RolePermission` |
| `common.auth` | `security` |
| `common.security` | `security` |
| `common.auditing` | `shared.auditing` |
| `common.persistence` | `shared.persistence` |
| `common.specification` | `shared.specification` |
| `exception` | `shared.exception` |

## 6. Event Package Decision

The event area currently has:

```text
Entities.events.generic
Entities.events.missa
Entities.events.oratorio
```

The target must keep event-related concepts close together while giving specialized event types their own package structure.

Generic event behavior uses:

```text
event
  application
  domain
  persistence
  web
```

Specialized event behavior uses dedicated subfeature packages:

```text
event
  Missa
    application
      useCases
    domain
    persistence
  Oratorio
    application
      useCases
    domain
    persistence
```

Do not introduce `event.missa` or `event.oratorio` lowercase package names in this refactor. The accepted package segments for the specialized event subfeatures are `event.Missa` and `event.Oratorio`.

## 7. RBAC Package Decision

RBAC must be lowercase:

```text
rbac
```

Target shape:

```text
rbac
  AccountRole
    application
      useCases
    domain
    persistence
  Permission
    application
      useCases
    domain
    persistence
  Role
    application
      useCases
    domain
    persistence
    web
  RolePermission
    application
    domain
    persistence
```

Do not keep `RBAC` as an uppercase Java package segment.

## 8. Naming Rules

Apply these package naming rules:

- Use lowercase package names.
- Exception: package segments that intentionally mirror domain subfeatures may use the exact approved segment names `Missa`, `Oratorio`, `AccountRole`, `Permission`, `Role`, and `RolePermission`.
- Do not use `Entities` as a grouping package.
- Do not include framework names like `Spring` in package names.
- Do not organize packages around implementation suffixes.
- Keep feature names singular when the package represents a domain area, such as `member`, `account`, and `event`.
- Use `shared` for cross-feature support code, not as a dumping ground.
- Keep package depth readable.

## 9. Refactor Instructions

Refactor one vertical slice at a time.

For each feature:

1. Create the target feature package.
2. Move controllers into `web`.
3. Move application workflows and read operations into `application/useCases`; create `application/useCases/<UseCaseName>` only when the use case has at least two co-located files, such as an implementation class plus DTO/RDTO files.
4. Move rich domain models into `domain`.
5. Move JPA entities, repositories, and specifications into `persistence`.
6. Update imports.
7. Keep behavior unchanged during the move.
8. Only simplify architecture after the package move is stable.

Do not perform a repository-wide package rename in one large step. The package migration must happen feature by feature.

## 10. Refactor Order

Use this order:

1. `member`
2. `account`
3. `rbac`
4. `event`
5. `presence`
6. `location`
7. `oratoriano`
8. `security`
9. `shared`

`member` is the best first slice because it exercises controllers, application workflows, rich domain models, persistence entities, search, activation, and RBAC interaction.
