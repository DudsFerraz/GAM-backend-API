# Mappers

Date: 2026-06-25

## 1. Purpose

This document defines how mapper files must be organized.

The project uses MapStruct. Mapper interfaces remain valid because MapStruct generates their implementations. The goal is not to split mapper files by technical purpose. The goal is to keep one mapper file per feature/model and make the internal method organization clear.

## 2. Main Rule

Use one mapper file per feature/model.

Examples:

```text
MemberMapper
EventMapper
AccountMapper
PresenceMapper
RoleMapper
```

Do not split mapper files into:

```text
MemberPersistenceMapper
MemberRDTOMapper
MemberReadMapper
```

The mapper file must be organized internally with clear sections.

## 3. Mapper Sections

Use only the sections that are actually needed.

The standard section order is:

1. `Domain <-> Persistence`
2. `Persistence -> RDTO`
3. `Helpers`

Do not add a projection/read-model section while the codebase does not use projections or query-specific read models.

## 4. Domain <-> Persistence

### Objective

Map rich domain models to JPA entities and JPA entities back to rich domain models.

### When To Use

Use this section only when the model keeps a rich domain class.

Examples:

```text
Member <-> MemberEntity
Event <-> EventEntity
Account <-> AccountEntity
Missa <-> MissaEntity
Oratorio <-> OratorioEntity
RefreshToken <-> RefreshTokenEntity
Oratoriano <-> OratorianoEntity
```

Do not use this section for simplified models.

### Typical Methods

```java
MemberEntity domainToEntity(Member member);

Member entityToDomain(MemberEntity entity);
```

### Refactor Instructions

- Keep this section for approved rich domain models.
- Remove this section when the separated domain model is deleted.
- Do not map `Entity -> Domain -> Entity` when no domain behavior is used.
- Keep audit-ignore annotations here when mapping domain objects into entities.

## 5. Persistence -> RDTO

### Objective

Map JPA entities to response DTOs.

### When To Use

Use this section when an entity must be converted into an API response shape.

This section applies to both rich and simplified models.

Examples:

```text
MemberEntity -> MemberRDTO
MemberEntity -> RegisterMemberRDTO
PresenceEntity -> PresenceRDTO
RoleEntity -> RoleRDTO
```

### Typical Methods

```java
MemberRDTO entityToRDTO(MemberEntity entity);

RegisterMemberRDTO entityToRegisterMemberRDTO(MemberEntity entity);
```

### Refactor Instructions

- Keep RDTO mapping methods here.
- Do not expose JPA entities directly through controllers.
- Keep nested RDTO conversions in this section when they are part of the response shape.
- Reuse RDTO mapping methods when the same response shape is shared.

## 6. Helpers

### Objective

Keep MapStruct helper methods, named conversions, default methods, and small conversion helpers separated from the main mapping methods.

### When To Use

Use this section when a mapper needs custom conversion logic.

Examples:

```java
@Named("nameToString")
default String nameToString(Name name) {
    if (name == null) return null;
    return name.toString();
}
```

### Refactor Instructions

- Place `@Named` methods in this section.
- Place default helper methods in this section.
- Keep helpers small.
- Move complex conversion behavior out of the mapper if it becomes business logic.

## 7. Rich Model Mapper Shape

For a rich model, use this structure:

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

    // @Named/default methods here when needed.
}
```

## 8. Simplified Model Mapper Shape

For a simplified model, remove the domain/persistence section:

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

    // @Named/default methods here when needed.
}
```

## 9. Projection And Read Model Mapping

The current codebase does not use projection/read-model mapping.

There are no current mapper sections for:

```text
Projection -> RDTO
ReadModel -> RDTO
QueryRow -> RDTO
```

Do not add a section for this until the codebase actually introduces projections or query-specific read models.

If projections are introduced later, add a new section to the same mapper file at that time. Do not create a separate mapper file by default.

## 10. Domain Model Simplification Impact

The domain/entity decisions directly affect mapper methods.

When a domain model is removed, remove its domain/entity mapping methods too.

Example:

```java
PresenceEntity domainToEntity(Presence presence);

Presence entityToDomain(PresenceEntity entity);
```

These methods must disappear when `Presence` is simplified.

The RDTO methods may remain:

```java
PresenceRDTO entityToRDTO(PresenceEntity entity);

RegisterPresenceRDTO entityToRegisterPresenceRDTO(PresenceEntity entity);
```

## 11. Refactor Instructions

For each mapper:

1. Keep one mapper file per feature/model.
2. Add the standard section comments.
3. Place domain/entity methods under `Domain <-> Persistence`.
4. Place entity/RDTO methods under `Persistence -> RDTO`.
5. Place `@Named` and default methods under `Helpers`.
6. Remove domain/entity methods for simplified models.
7. Remove mappers that no longer have any useful methods.
8. Do not split mapper files by mapper type.

## 12. Refactor Order

Apply mapper cleanup after the related domain/entity decision is implemented for that feature.

Use this order:

1. `RolePermission` mapper cleanup; remove `RolePermissionMapper` when it has no useful methods
2. `AccountRoleMapper`
3. `RoleMapper`
4. `PermissionMapper`
5. `LocationMapper`
6. `PresenceMapper`
7. Rich model mappers such as `MemberMapper`, `EventMapper`, `AccountMapper`, `MissaMapper`, `OratorioMapper`, `RefreshTokenMapper`, and `OratorianoMapper`

Start with simplified models because their domain/entity mapper methods are the clearest removals.
