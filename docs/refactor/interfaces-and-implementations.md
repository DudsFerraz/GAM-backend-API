# Interfaces and Implementations

Date: 2026-06-25

## 1. Purpose

This document defines which interfaces must remain, which interfaces must be removed, and when a new interface is justified.

The current codebase uses many interface/implementation pairs such as `RegisterMember` and `SpringRegisterMember`. Most of these interfaces have exactly one implementation and are only used inside the same Spring application. In those cases, the interface adds ceremony rather than flexibility.

## 2. Main Rule

An interface must represent a real boundary.

A single implementation inside the same Spring application is not enough to justify an interface.

Interfaces must not be created only because:

- interfaces are common in Java;
- interfaces may help testing;
- a class is annotated with `@Service`;
- a future second implementation might exist someday;
- every application operation should have a contract by default.

## 3. Interfaces To Remove

Remove application-service interfaces by default.

Examples:

```text
RegisterMember -> SpringRegisterMember
SearchMembers -> SpringSearchMembers
GetMember -> SpringGetMember
CreateEvent -> SpringCreateEvent
AddAccountRole -> SpringAddAccountRole
```

These interfaces must become concrete application classes:

```text
RegisterMember
SearchMembers
GetMember
CreateEvent
AddAccountRole
```

Rationale:

- They have one implementation.
- They are used inside the same Spring application.
- They do not isolate the application from an external system.
- They are not required for testing.
- They double the number of files for each operation.
- They create framework-oriented implementation names such as `SpringRegisterMember`.

## 4. Testing Is Not Enough To Justify These Interfaces

Application-service interfaces must not be kept only for testing.

Concrete application classes can be tested directly by constructing them with mocks, fakes, or test doubles for their dependencies.

Example:

```java
RegisterMember registerMember = new RegisterMember(
        memberRepository,
        memberMapper,
        accountDomainLoader
);
```

The class under test does not need its own interface for this to work.

Interfaces help tests only when the dependency has multiple meaningful substitutes. An interface with one implementation does not become valuable merely because tests exist.

## 5. Interfaces To Keep

### 5.1. Spring Data Repository Interfaces

Keep repository interfaces.

Examples:

```text
MemberRepository
AccountRepository
EventRepository
RoleRepository
PermissionRepository
```

Rationale:

- Spring Data expects repository abstractions.
- Spring generates proxy implementations at runtime.
- Repository method declarations are meaningful framework integration points.
- These interfaces are not application-service ceremony.

### 5.2. MapStruct Mapper Interfaces

Keep MapStruct mapper interfaces while MapStruct remains in the project.

Examples:

```text
MemberMapper
AccountMapper
EventMapper
RefreshTokenMapper
```

Rationale:

- MapStruct is designed around mapper interfaces or abstract mapper classes.
- Implementations are generated at compile time.
- The interface is the mapping contract consumed by the annotation processor.

Mapper methods may be removed when domain/entity simplification removes a mapping boundary, but the mapper interface type itself remains valid when mapping is still needed.

### 5.3. Shared Infrastructure Interfaces

Keep shared infrastructure interfaces when generic infrastructure depends on them.

Examples:

```text
BaseRepository
SoftDeletable
```

Rationale:

- `BaseRepository` extends Spring Data repository behavior and defines shared persistence operations.
- `SoftDeletable` expresses the contract required by custom soft-delete infrastructure.
- These interfaces serve shared infrastructure, not one application workflow.

If the soft-delete strategy changes completely, these interfaces can be reevaluated.

### 5.4. Framework Contracts

Keep implementations of external framework interfaces.

Examples:

```text
AccountDetails implements UserDetails
AccountDetailsService implements UserDetailsService
DelegatedAuthenticationEntryPoint implements AuthenticationEntryPoint
EmailConverter implements Converter<String, MyEmail>
EmailConverterJPA implements AttributeConverter<MyEmail, String>
```

Rationale:

- Spring Security, Spring MVC, and JPA require these contracts.
- The interface is provided by the framework.
- The implementation is how the application plugs into the framework.

These are not part of the application-service interface cleanup.

## 6. When A New Interface Is Valid

Create a new interface only when one of these conditions is true.

### 6.1. Framework-Generated Implementation

An interface is valid when a framework generates or supplies the implementation.

Examples:

```text
MemberRepository
MemberMapper
```

### 6.2. Framework Contract

An interface is valid when the application must implement a framework contract.

Examples:

```text
UserDetailsService
AuthenticationEntryPoint
AttributeConverter
Converter
```

### 6.3. Multiple Real Implementations

An interface is valid when multiple implementations actually exist or are being implemented immediately.

Example:

```text
NotificationSender
  EmailNotificationSender
  WhatsAppNotificationSender
```

Do not create the interface for a hypothetical future implementation.

### 6.4. External Infrastructure Boundary

An interface is valid when application code must be protected from an external provider or technology.

Example:

```text
PaymentGateway
  StripePaymentGateway
```

The interface represents a real boundary between application behavior and an external integration.

### 6.5. Strategy Or Algorithm Variation

An interface is valid when behavior is genuinely interchangeable.

Example:

```text
PresenceConflictResolver
  StrictPresenceConflictResolver
  LenientPresenceConflictResolver
```

Only use this shape when the variation is real and intentional.

### 6.6. Shared Infrastructure Contract

An interface is valid when generic infrastructure needs a stable contract across multiple classes.

Example:

```text
SoftDeletable
```

## 7. Refactor Instructions

For each application-service interface:

1. Confirm it has only one implementation.
2. Delete the interface.
3. Rename the implementation class by removing the `Spring` prefix.
4. Update constructor dependencies to depend on the concrete application class.
5. Keep behavior unchanged.
6. Keep tests focused on behavior, not the interface.

Example:

```text
RegisterMember.java
SpringRegisterMember.java
```

becomes:

```text
RegisterMember.java
```

The concrete class keeps the application operation name.

## 8. Refactor Order

Apply this cleanup feature by feature:

1. `member`
2. `account`
3. `event`
4. `presence`
5. `rbac`
6. `location`
7. `oratoriano`

Start with `member` because it contains action workflows, read operations, loaders, domain usage, persistence usage, and RBAC interaction.

## 9. Final Boundary Rule

An interface must prove that it represents a real boundary.

Valid boundaries include generated implementations, framework contracts, multiple real implementations, external infrastructure boundaries, strategy variation, and shared infrastructure contracts.

Application-service interfaces with one implementation inside the same Spring application must be deleted.

