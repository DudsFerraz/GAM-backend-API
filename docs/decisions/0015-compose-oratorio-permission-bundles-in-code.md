# ADR-0015: Compose Oratorio permission bundles in code

## Status

Accepted

## Context

Every GAM Coordinator and Coordenador do Oratório needs the complete Oratorio operational permission set, while ordinary Members receive only Oratorio read access. Copying every permission into two independent role definitions would require coordinated edits whenever the module changes.

The persisted RBAC model stores flat role-permission links and the project intentionally has no nested roles. Authorization remains permission-based rather than role-based.

## Decision

Define reusable code-level permission groups:

- `ORATORIO_READ`, containing `ORATORIO_GET`; and
- `ORATORIO_OPERATIONS`, containing `ORATORIO_READ` plus all accepted operational Oratorio, attendance, Oratoriano, and additional-form permissions.

Compose baseline system-role bundles from those groups:

- `MEMBER` receives `ORATORIO_READ`;
- `ORATORIO_COORD` receives `ORATORIO_OPERATIONS`;
- `COORD` receives `ORATORIO_OPERATIONS` and `ORATORIO_COORD_MANAGE`;
- `SUDO` receives every accepted permission; and
- `VISITOR` receives none.

These groups are source-level catalog composition only. Repeatable seeding shall flatten them into ordinary direct role-permission links. Runtime authorization shall continue checking permissions, and persisted roles shall not inherit other roles.

## Alternatives considered

### Option 1: Copy permissions into every role bundle

Pros:

- Flat definitions mirror flat persistence directly.

Cons:

- Every operational permission change has multiple edit points.
- Drift can leave COORD and ORATORIO_COORD with inconsistent module capabilities.

### Option 2: Introduce nested roles

Pros:

- Expresses bundle reuse directly at runtime.

Cons:

- Expands RBAC semantics beyond the module.
- Requires inheritance, cycle, catalog, persistence, and authorization rules.
- Was explicitly excluded from the current scope.

### Option 3: Code-level composition flattened by seeding

Pros:

- Gives one operational permission-maintenance point.
- Preserves the current flat persisted and runtime permission model.
- Avoids nested-role semantics.

Cons:

- Seed expansion must be deterministic and documented.
- Source groups must not be mistaken for persisted roles or authorization authorities.

## Consequences

Positive consequences:

- Adding or removing an Oratorio operational permission changes one reusable group.
- COORD and ORATORIO_COORD remain aligned by construction.
- MEMBER retains a deliberately smaller read-only capability.
- Existing flat role-permission queries and authorization checks remain valid.

Negative consequences:

- Registry code gains an additional composition layer.
- Documentation and tests must verify both group contents and flattened role links.
- `ORATORIO_COORD_MANAGE` remains deliberately outside the operational group.

## Related requirements

- `REQ-ORATORIO-COORD-002`
- `REQ-ORATORIO-008`
- `REQ-ORATORIO-ATT-002`
- `REQ-ORATORIANO-004`
- `REQ-ORATORIANO-FORM-015`
- `REQ-RBAC-002`
- `REQ-RBAC-003`
- `REQ-RBAC-004`

## Related diagrams

- [Oratorio Module Domain](../diagrams/oratorio-module-domain.md)

## Related videos

- None.
