# Package Organization Guidelines

## 1. Purpose

This document defines the package organization guidelines for `gam-api`.

The codebase uses feature-oriented packages with internal layer folders. Each business area is easy to navigate, and framework, persistence, web, and domain responsibilities are explicit.

## 2. Main Packages

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

Feature packages represent business areas. Cross-feature technical infrastructure belongs in `security` or `shared`.

## 3. Feature Package Shape

Each feature package uses internal layer folders when the layer exists:

```text
br.org.gam.api.member
  application
  domain
  persistence
  web
```

There are no empty layer folders just to satisfy the structure. A folder exists only when it contains meaningful code.

Application workflows live under `application/useCases`. Single-file application use cases live directly under `application/useCases`. A use-case subdirectory exists only when the use case has at least two co-located files, such as an operation class plus DTO/RDTO files:

```text
br.org.gam.api.member
  application
    useCases
      registerMember
        RegisterMember.java
        RegisterMemberDTO.java
        RegisterMemberRDTO.java
      Activation.java
      GetMember.java
      SearchMembers.java
```

Shared application helpers for one feature, such as mappers, common RDTOs, feature-owned security helpers, and feature-specific exceptions, stay inside that feature's `application` package unless they are genuinely used across features.

## 4. Layer Responsibilities

### 4.1. application

The `application` package contains application workflows, read operations, loaders, and operation-specific input/output objects.

Examples:

```text
member/application/useCases/registerMember/RegisterMember.java
member/application/useCases/Activation.java
member/application/useCases/SearchMembers.java
member/application/MemberDomainLoader.java
member/application/MemberEntityLoader.java
member/application/useCases/registerMember/RegisterMemberDTO.java
member/application/useCases/registerMember/RegisterMemberRDTO.java
```

Application classes use expressive action/read names. Required loading is represented by explicit domain/entity loaders.

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

Only rich domain models live in `domain`. A model belongs here when it represents business behavior, invariants, lifecycle rules, or important domain language.

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

Controllers are thin. They receive HTTP input, delegate to application classes, and return RDTOs or response wrappers.

### 4.5. security

The `security` package contains application security configuration and authentication/authorization infrastructure that is not owned by one business feature.

Examples:

```text
security/SecurityConfig.java
security/SecurityUtils.java
security/jwt/JwtAuthFilter.java
security/jwt/JwtService.java
```

Authentication workflows that behave like application features use subpackages under `security`, or a dedicated `auth` package when that boundary is clearer.

### 4.6. shared

The `shared` package contains cross-feature technical and domain-support code.

Examples:

```text
shared/auditing
shared/persistence
shared/specification
shared/exception
```

Code belongs in `shared` only when more than one feature genuinely uses it. Feature-specific code stays inside the feature package.

## 5. Event Packages

Event-related concepts stay close together under `event`.

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
  missa
  oratorio
```

Each event subfeature follows the same layer responsibilities defined in this document.

`missa` and `oratorio` are the existing event subfeatures. New event subfeatures can be added under `event` when they represent specialized event behavior and follow the same package organization rules.

## 6. RBAC Packages

```text
rbac
  accountRole
  permission
  role
  rolePermission
```

Each RBAC subfeature follows the same layer responsibilities defined in this document.

## 7. Naming Rules

Package segments use lower camel case. The first word is lowercase, and additional words keep camel-case boundaries, such as `useCases`, `accountRole`, and `rolePermission`.

The `shared` package is reserved for cross-feature support code.

Package depth stays readable.
