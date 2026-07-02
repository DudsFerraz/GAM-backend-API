# Domain Model vs JPA Entity Model

Date: 2026-06-24

## 1. Purpose

This document defines how the current domain model and JPA entity separation must be refactored.

The project currently separates several domain objects from their persistence entities, such as `Member` / `MemberEntity`, `Event` / `EventEntity`, and `Account` / `AccountEntity`. This separation must become intentional per model. A domain model must exist when it carries business behavior, invariants, lifecycle transitions, or important domain language. A separated domain model must not exist only to repeat the same fields as a JPA entity before saving or reading a row.

## 2. Final Decisions

Keep rich domain models for:

- `Member`
- `Event`
- `Account`
- `Missa`
- `Oratorio`
- `RefreshToken`
- `Oratoriano`

Simplify the remaining separated domain models:

- `Location`
- `Presence`
- `Role`
- `Permission`
- `AccountRole`
- `RolePermission`

## 3. General Refactor Instruction

Refactor the codebase so domain/persistence separation exists only for the models listed as rich domain models.

For rich models:

- Keep a domain class separated from the JPA entity.
- Move meaningful business rules into the domain class or a closely related domain policy.
- Keep persistence annotations out of the domain class.
- Keep JPA relationships, lazy-loading concerns, table mappings, and persistence-only details inside the entity.
- Keep mapper usage where it crosses a real domain/persistence boundary.
- Avoid turning rich domain classes into passive data containers.

For simplified models:

- Remove the separated domain class when it only duplicates entity fields.
- Use the JPA entity directly in persistence-oriented workflows.
- Keep JPA entities simple. Construct and populate simplified entities directly in the application layer instead of adding rich factory methods to the entity.
- Keep business rules in an application service or policy when a rich domain object is not justified.
- Remove domain-to-entity mapper methods that exist only to support the deleted domain class.
- Avoid mapping `Entity -> Domain -> Entity` when no domain behavior is used.

## 4. Rich Domain Models

### 4.1. Member

Keep `Member` as a rich domain model.

Rationale:

- `Member` has lifecycle behavior: registration, activation, deactivation, age calculation, and status transitions.
- Member activation is not just a status update. It also triggers an account role transition.
- The activation workflow links membership state with authorization state, which is a real business rule.

Refactor instructions:

- Keep `Member` separated from `MemberEntity`.
- Keep `activate()` and `deactivate()` as member state transitions.
- Make the account-role side effect explicit in the application layer.
- Do not leave the role transition hidden behind raw string literals like `"MEMBER"` and `"VISITOR"`.
- Introduce constants, enum values, or a dedicated policy for member activation role transitions.
- Keep the activation use case responsible for coordinating member status changes and account role changes.

### 4.2. Event

Keep `Event` as a rich domain model.

Rationale:

- `Event` owns scheduling rules.
- `Event` validates begin/end dates.
- `Event` initializes status based on time.
- `Event` supports cancellation.
- `Event` is the base concept behind specialized events such as `Missa` and `Oratorio`.

Refactor instructions:

- Keep `Event` separated from `EventEntity`.
- Keep date validation and event status initialization inside the domain model.
- Keep cancellation behavior in the domain model.
- Keep persistence-specific relationships and annotations inside `EventEntity`.
- Review specialized event creation so `Missa` and `Oratorio` cannot be created with an incompatible event type.

### 4.3. Account

Keep `Account` as a rich domain model.

Rationale:

- `Account` represents authentication identity.
- Account behavior can grow around credentials, login eligibility, disabled accounts, email verification, password changes, and session invalidation.
- Account is central to both authentication and authorization.

Refactor instructions:

- Keep `Account` separated from `AccountEntity`.
- Move account lifecycle rules into the domain model as they appear.
- Keep credential and login eligibility decisions close to the account domain.
- Keep persistence-only role collections and JPA loading behavior inside `AccountEntity`.

### 4.4. Missa

Keep `Missa` as a rich domain model.

Rationale:

- `Missa` represents a specialized event workflow.
- It carries liturgical role assignments.
- It has future room for member eligibility rules, duplicate assignment prevention, vacancy handling, and role-specific constraints.

Refactor instructions:

- Keep `Missa` separated from `MissaEntity`.
- Move assignment rules into the domain model as they become explicit.
- Keep role assignment methods expressive and domain-oriented.
- Ensure the `Missa` domain model protects its assignment collections and role slots.
- Keep database join table details inside `MissaEntity`.

### 4.5. Oratorio

Keep `Oratorio` as a rich domain model.

Rationale:

- `Oratorio` represents a specialized event workflow.
- It carries team assignments such as `lanche`, `btJovens`, and `btCriancas`.
- It also links `oratorianos`, which are expected to gain importance in future workflows.
- It already exposes controlled add/remove methods and unmodifiable sets.

Refactor instructions:

- Keep `Oratorio` separated from `OratorioEntity`.
- Keep team assignment behavior in the domain model.
- Preserve controlled collection mutation through domain methods.
- Move future eligibility or assignment rules into the domain model or a related domain policy.
- Keep join table mappings and JPA collection configuration inside `OratorioEntity`.

### 4.6. RefreshToken

Keep `RefreshToken` as a rich domain model.

Rationale:

- Refresh token behavior is security-sensitive.
- Token expiration, rotation, revocation, reuse detection, and account binding are domain concerns for authentication.
- The current model is simple, but the behavior deserves an explicit domain boundary as authentication hardening evolves.

Refactor instructions:

- Keep `RefreshToken` separated from `RefreshTokenEntity`.
- Move expiration and revocation checks toward the domain model as token behavior grows.
- Model token lifecycle explicitly instead of treating refresh tokens as passive rows.
- Keep persistence details inside `RefreshTokenEntity`.

### 4.7. Oratoriano

Keep `Oratoriano` as a rich domain model.

Rationale:

- `Oratoriano` will play an important role in future workflows.
- Even if the current class is simple, the domain concept is expected to become more than a passive record.
- It should remain available as a domain concept while the real behavior is clarified.

Refactor instructions:

- Keep `Oratoriano` separated from `OratorianoEntity`.
- Move future rules into the domain model instead of scattering them through services.
- Clarify the distinction between `Member` and `Oratoriano` as the feature grows.
- Keep persistence mappings inside `OratorianoEntity`.

## 5. Simplified Models

### 5.1. Location

Simplify `Location`.

Rationale:

- `Location` is currently mostly structured data.
- The current domain class mainly validates required fields, trims strings, generates an ID, and maps to `LocationEntity`.
- There is no meaningful lifecycle or state transition in the domain class.

Refactor instructions:

- Remove the separated `Location` domain class unless new location-specific behavior appears.
- Use `LocationEntity` directly for persistence workflows.
- Keep location creation rules in the application layer or request validation.
- Remove unnecessary `Location` domain mapper methods.

### 5.2. Presence

Simplify `Presence`.

Rationale:

- `Presence` currently behaves as an association between member and event plus observations.
- The important uniqueness rule is enforced outside the domain class through repository/database behavior.
- The current domain object mostly trims observations, generates an ID, and maps to `PresenceEntity`.

Refactor instructions:

- Remove the separated `Presence` domain class unless attendance behavior becomes richer.
- Use `PresenceEntity` directly for persistence workflows.
- Keep duplicate-presence prevention in the application service, repository constraint, or database constraint.
- Keep future attendance policies outside the entity if they become more complex.

### 5.3. Role

Simplify `Role`.

Rationale:

- `Role` is currently mostly configurable data with name and description.
- Future role CRUD does not automatically require a rich domain entity.
- Role-specific rules can live in an RBAC policy or application service.

Refactor instructions:

- Remove the separated `Role` domain class if it remains only a name/description wrapper.
- Use `RoleEntity` directly for role persistence.
- Keep role management rules in an RBAC application service or policy.
- Preserve the hybrid RBAC model defined in [`project-refactor-roadmap.md`](project-refactor-roadmap.md), [`security-and-rbac.md`](security-and-rbac.md), and this document.

### 5.4. Permission

Simplify `Permission`.

Rationale:

- `Permission` is currently mostly seeded/configurable data with name and description.
- Seeded permissions are important, but that does not require a rich domain class.
- System-protected permission behavior can live in an RBAC policy or seed management service.

Refactor instructions:

- Remove the separated `Permission` domain class if it remains only a name/description wrapper.
- Use `PermissionEntity` directly for permission persistence.
- Keep seeded permission reconciliation in the RBAC seed or migration workflow.
- Keep protected-permission rules in an RBAC policy or application service.

### 5.5. AccountRole

Simplify `AccountRole`.

Rationale:

- `AccountRole` is currently a join/assignment record.
- The important behavior is assignment eligibility and duplicate prevention, which lives outside the domain class.
- A separated domain class adds mapping cost without adding behavior.

Refactor instructions:

- Remove the separated `AccountRole` domain class unless role assignment becomes behavior-rich.
- Use `AccountRoleEntity` directly for persistence.
- Keep role assignment rules in an account-role application service or RBAC policy.
- Keep duplicate assignment protection in service logic and database constraints.

### 5.6. RolePermission

Simplify `RolePermission`.

Rationale:

- `RolePermission` is the clearest simplification candidate.
- The current domain class depends directly on `RoleEntity` and `PermissionEntity`.
- That dependency defeats the purpose of separating domain and persistence models.
- The class is effectively already a persistence join model.

Refactor instructions:

- Remove the separated `RolePermission` domain class.
- Use `RolePermissionEntity` directly for persistence.
- Remove mapper methods that convert `RolePermissionEntity` to/from `RolePermission`.
- Keep role-permission assignment rules in an RBAC application service or policy.

## 6. Mapper Cleanup

After the domain model decisions are applied:

- Keep mappers for rich domain/persistence boundaries.
- Remove mapper methods for simplified domain classes.
- Remove generated mapping paths that only exist because of deleted domain classes.
- Avoid mapping simplified models through a deleted domain layer.

Expected mapper impact:

- Keep rich-boundary mapping for `Member`, `Event`, `Account`, `Missa`, `Oratorio`, `RefreshToken`, and `Oratoriano`.
- Simplify or remove domain mapping for `Location`, `Presence`, `Role`, `Permission`, `AccountRole`, and `RolePermission`.

## 7. Refactor Order

Apply this subject in small slices:

1. Start with `RolePermission`, because it already violates the intended separation.
2. Simplify `AccountRole`, `Role`, and `Permission` together as part of RBAC cleanup.
3. Simplify `Location`.
4. Simplify `Presence`.
5. Strengthen `Member` activation boundaries.
6. Strengthen `Event`, `Missa`, and `Oratorio` domain rules.
7. Strengthen `RefreshToken` lifecycle behavior.
8. Keep `Oratoriano` separated and wait for future workflow rules before adding more behavior.
