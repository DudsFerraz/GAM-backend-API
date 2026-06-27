# Services and Use Cases

Date: 2026-06-25

## 1. Purpose

This document defines how application workflow classes, read operation classes, repeated loading helpers, and input/output data objects must be named and structured.

The current codebase already has action-oriented names such as `RegisterMember`, `CreateEvent`, and `SearchMembers`. That direction is good. The problem is not the action name itself; the problem is the surrounding ceremony, especially one interface plus one Spring implementation for many single-implementation operations.

## 2. Final Naming Decisions

Use these naming decisions:

| Objective | Suffix | Example |
| --- | --- | --- |
| Represent an application workflow that performs an action | none | `RegisterMember` |
| Represent a read operation that intentionally does not mutate state | none | `SearchMembers` |
| Centralize repeated "load this required domain object or fail" behavior | `DomainLoader` | `MemberDomainLoader` |
| Centralize repeated "load this required JPA entity or fail" behavior | `EntityLoader` | `MemberEntityLoader` |
| Represent input data received by an application operation | `DTO` | `RegisterMemberDTO` |
| Represent output data returned by an application operation | `RDTO` | `RegisterMemberRDTO` |

Do not introduce `UseCase`, `Query`, `Lookup`, `Policy`, `Command`, or `Criteria` as default suffixes.

## 3. Action Workflow Classes

### Objective

Represent an application workflow that performs an action.

### Suffix

No suffix.

### Example

```text
RegisterMember
ActivateMember
CreateEvent
RegisterPresence
RefreshToken
```

### Typical Traits

- Named with a verb phrase.
- Represents a user/system action.
- Usually belongs in the application layer.
- May change system state.
- May be transactional.
- May call repositories.
- May call loaders.
- May call other application workflows.
- May coordinate rich domain methods.
- May enforce application-level rules.
- Should not become a generic bag of unrelated methods.

### Refactor Instructions

- Keep action-oriented class names without adding a `UseCase` suffix.
- Replace interface/implementation pairs such as `RegisterMember` + `SpringRegisterMember` with one concrete application class named `RegisterMember`.
- Keep the class name focused on the operation, not the framework.
- Do not create generic classes such as `MemberService` when the behavior is better represented by a specific action name.
- Keep the operation method name simple and consistent, such as `execute`, `register`, `activate`, or another verb that reads naturally for the class.

Example target shape:

```text
member/application/RegisterMember.java
member/application/ActivateMember.java
event/application/CreateEvent.java
presence/application/RegisterPresence.java
```

## 4. Read Operation Classes

### Objective

Represent a read operation that intentionally does not mutate state.

### Suffix

No suffix.

### Example

```text
GetMember
SearchMembers
GetEventPresences
GetRolePermissions
```

### Typical Traits

- Named with a read/search verb phrase.
- Represents information retrieval.
- Should not intentionally change system state.
- Usually belongs in the application layer.
- May use repositories/specifications.
- May use pagination, sorting, and filtering.
- Often returns `RDTO`, projections, pages, or read models.
- Usually should avoid loading rich domain models unless domain behavior is needed to answer the read.
- Should be easier to reason about than write workflows.

### Refactor Instructions

- Keep read operation class names without adding a `Query` suffix.
- Keep read classes separate from action workflow classes when the behavior is meaningfully different.
- Do not mutate state inside read operation classes.
- Prefer direct read models, `RDTO`s, projections, or pages when domain behavior is not required.
- Do not reconstruct rich domain models just to return read-only data.

Example target shape:

```text
member/application/GetMember.java
member/application/SearchMembers.java
event/application/GetEventPresences.java
rbac/application/GetRolePermissions.java
```

## 5. Loaders

### Objective

Centralize repeated "load this required object or fail" behavior.

### Suffix

Use `DomainLoader` or `EntityLoader`.

### Example

```text
MemberDomainLoader
MemberEntityLoader
AccountDomainLoader
AccountEntityLoader
EventDomainLoader
EventEntityLoader
```

### Typical Traits

- Loads required objects by ID or unique key.
- Throws a consistent not-found exception when the object does not exist.
- Exists only when repeated loading behavior is actually needed.
- Should be specific about the returned model shape.
- `DomainLoader` returns rich domain models.
- `EntityLoader` returns JPA entities or persistence references.
- A workflow should normally depend on only one loader type for a model.
- Injecting both domain and entity loaders for the same model into one workflow is a design smell and must be justified.
- Should not contain business workflows.
- Should not become a general-purpose service.

### Refactor Instructions

- Replace vague `GetXInstance` classes with explicit loaders.
- Create a `DomainLoader` only when repeated workflows need the rich domain model.
- Create an `EntityLoader` only when repeated workflows need the JPA entity or persistence reference.
- Do not create both loaders by default.
- Do not place domain-loading and entity-loading methods in the same class.
- Keep loader methods explicit and narrow.
- Avoid injecting both the domain loader and entity loader for the same model into one workflow.
- If both loaders are needed by the same workflow, document why the workflow must cross both model shapes.

Example target shape:

```text
member/application/MemberDomainLoader.java
member/application/MemberEntityLoader.java
account/application/AccountDomainLoader.java
account/application/AccountEntityLoader.java
```

Example method shape:

```java
public class MemberDomainLoader {
    public Member requiredById(UUID id) {
        ...
    }
}
```

```java
public class MemberEntityLoader {
    public MemberEntity requiredById(UUID id) {
        ...
    }
}
```

## 6. DTO

### Objective

Represent input data received by an application operation.

### Suffix

Use `DTO`.

### Example

```text
RegisterMemberDTO
CreateEventDTO
SearchMembersDTO
LoginAccountDTO
```

### Typical Traits

- Carries input data into a workflow or read operation.
- Usually mirrors the request body or request parameters.
- May contain validation annotations.
- Should be simple and data-oriented.
- Should not contain business behavior.
- Should not contain persistence logic.
- Can be used by controllers to receive HTTP input.
- Can be passed to application operations when no separate application input model is needed.
- Should be named after the operation when operation-specific.

### Refactor Instructions

- Keep the `DTO` suffix for input objects.
- Do not introduce `Command`, `Criteria`, `Input`, or `Request` as default suffixes for application input.
- Keep DTOs simple.
- Keep business rules out of DTOs.
- Keep persistence behavior out of DTOs.
- Use operation-specific DTOs when the input shape belongs to one operation.

## 7. RDTO

### Objective

Represent output data returned by an application operation.

### Suffix

Use `RDTO`.

### Example

```text
RegisterMemberRDTO
CreateEventRDTO
MemberRDTO
LoginAccountRDTO
```

### Typical Traits

- Carries output data from a workflow or read operation.
- Usually shapes the API response.
- Should expose only data that the client is allowed to receive.
- Should be simple and data-oriented.
- Should not contain business behavior.
- Should not expose JPA entities directly.
- Can be operation-specific when the response shape is unique.
- Can be shared when multiple operations return the same representation.
- Often returned by controllers directly as response bodies.

### Refactor Instructions

- Keep the `RDTO` suffix for output objects.
- Do not introduce `Response`, `View`, or `Result` as default suffixes for operation output.
- Keep RDTOs simple.
- Keep business rules out of RDTOs.
- Do not expose JPA entities directly through controllers.
- Reuse an existing RDTO when multiple operations truly return the same representation.
- Create operation-specific RDTOs when the response shape is unique.

## 8. Suffixes Not Adopted By Default

### UseCase

`UseCase` is not adopted as a default suffix because action names such as `RegisterMember` and `ActivateMember` already communicate that a workflow is happening.

Use:

```text
RegisterMember
```

Not:

```text
RegisterMemberUseCase
```

### Query

`Query` is not adopted as a default suffix because read names such as `SearchMembers` and `GetMember` already communicate that data is being read.

Use:

```text
SearchMembers
```

Not:

```text
SearchMembersQuery
```

### Lookup

`Lookup` is not adopted. Required loading must use `DomainLoader` or `EntityLoader` so the returned model shape is explicit.

Use:

```text
MemberDomainLoader
MemberEntityLoader
```

Not:

```text
MemberLookup
GetMemberInstance
```

### Policy

`Policy` is not adopted by default.

Business decisions can stay inside the action workflow while they are simple and local to that workflow. Extract a separate rule class only when the decision becomes reused, branch-heavy, or conceptually important enough to name independently.

When such extraction happens, the class name must be chosen at that time based on the actual rule. It must not be created by default as part of the application service structure.

### Command And Criteria

`Command` and `Criteria` are not adopted as default suffixes because they mostly overlap with the current `DTO` convention.

Use:

```text
RegisterMemberDTO
SearchMembersDTO
```

Not:

```text
RegisterMemberCommand
MemberSearchCriteria
```

Only introduce a separate input model later if HTTP input and application input must intentionally diverge.

## 9. Target Application Package Example

A member application package should move toward this style:

```text
member/application
  RegisterMember.java
  ActivateMember.java
  GetMember.java
  SearchMembers.java
  MemberDomainLoader.java
  MemberEntityLoader.java
  RegisterMemberDTO.java
  RegisterMemberRDTO.java
  MemberRDTO.java
  SearchMembersDTO.java
```

This keeps operation names direct, removes redundant suffixes, separates domain/entity loading explicitly, and preserves the existing `DTO` / `RDTO` convention.

## 10. Refactor Order

Apply this subject in small slices:

1. Pick one feature package, preferably `member`.
2. Replace interface/implementation pairs with one concrete action/read class.
3. Rename `SpringX` implementations to the operation name.
4. Replace `GetXInstance` with the strictly necessary `DomainLoader` or `EntityLoader`.
5. Keep existing `DTO` and `RDTO` suffixes.
6. Repeat the pattern in the next feature only after the first feature is clear.
