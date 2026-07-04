# Mappers Guidelines

## 1. Purpose

This document defines the organization and structure of MapStruct mapper files within `gam-api`.

Because MapStruct generates implementations at compile time, mapper interfaces remain valid boundaries in the application. The primary goal is to maintain **one unified mapper file per feature or model**, utilizing internal sections to organize different mapping concerns rather than splitting files by technical purpose.

## 2. Unified Mapper Files

Each feature or model must use exactly one mapper interface.

Do not fragment mapper files based on the direction of the mapping or the layers involved.

**Valid Examples:**

```text
MemberMapper
EventMapper
AccountMapper
PresenceMapper
```

**Forbidden Splitting:**

```text
MemberPersistenceMapper  // Anti-pattern
MemberRDTOMapper         // Anti-pattern
MemberReadMapper         // Anti-pattern
```

## 3. Internal Mapper Sections

Mapper files must be internally organized using explicit comment blocks. Use only the sections that are actually required by the model.

The standard section order is:

1. `Domain <-> Persistence`
2. `Persistence -> RDTO`
3. `Helpers`

### 3.1. Domain <-> Persistence

**Usage:** Exclusively for **rich domain models** (e.g., `Member`, `Event`, `Account`).
**Purpose:** Maps rich domain classes to JPA entities, and vice versa.
**Rule:** This section is strictly forbidden for simplified models. Do not map `Entity -> Domain -> Entity` when no domain behavior is utilized. Audit-ignore annotations belong here when mapping domain objects into entities.

### 3.2. Persistence -> RDTO

**Usage:** For **both** rich and simplified models.
**Purpose:** Maps JPA entities into Response Data Transfer Objects (`RDTO`s) to shape API responses.
**Rule:** JPA entities are never exposed directly through controllers. This section handles all entity-to-response transformations, including nested RDTO conversions.

### 3.3. Helpers

**Usage:** When MapStruct requires custom conversion logic.
**Purpose:** Houses `@Named` methods, default interface methods, and small data transformation helpers.
**Rule:** Helpers must remain small and focused purely on data conversion. If a conversion requires business logic, that logic belongs in an application service or domain model, not a mapper.

---

## 4. Expected Mapper Structures

The structure of the mapper changes depending on whether the model is a rich domain model or a simplified model.

### 4.1. Rich Model Mapper Shape

Rich models require bidirectional domain mapping alongside RDTO transformations.

```java
@Mapper(componentModel = "spring")
public interface MemberMapper {

    // =====================================================================================
    // Domain <-> Persistence
    // =====================================================================================

    @IgnoreFullAuditFields
    MemberEntity domainToEntity(Member member);

    Member entityToDomain(MemberEntity entity);

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    MemberRDTO entityToRDTO(MemberEntity entity);

    RegisterMemberRDTO entityToRegisterMemberRDTO(MemberEntity entity);

    // =====================================================================================
    // Helpers
    // =====================================================================================

    // @Named or default helper methods here
}
```

### 4.2. Simplified Model Mapper Shape

Simplified models interact directly with entities in the application layer. Therefore, the `Domain <-> Persistence` section is entirely omitted.

```java
@Mapper(componentModel = "spring")
public interface PresenceMapper {

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    PresenceRDTO entityToRDTO(PresenceEntity entity);

    RegisterPresenceRDTO entityToRegisterPresenceRDTO(PresenceEntity entity);

    // =====================================================================================
    // Helpers
    // =====================================================================================

    // @Named or default helper methods here
}
```