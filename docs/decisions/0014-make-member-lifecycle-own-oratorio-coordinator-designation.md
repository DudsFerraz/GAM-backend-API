# ADR-0014: Make Member lifecycle own Oratorio Coordinator designation

## Status

Accepted

## Context

The Oratorio module needs a system responsibility that grants its full operational capabilities without granting unrelated GAM-wide Coordinator authority. Every holder must remain an active Member, and Member deactivation must remove the authority atomically.

The project has no nested roles and generic Account-role management accepts only custom Roles. Modeling `ORATORIO_COORD` as an independently assignable system Role would allow it to drift from Member state and bypass the dedicated responsibility audit.

## Decision

Define `ORATORIO_COORD` as a lifecycle-owned system Role representing the Coordenador do Oratório responsibility.

The Member domain shall own its dedicated grant and revoke operations. Grant and revoke require `ORATORIO_COORD_MANAGE`, which belongs to baseline `COORD` and `SUDO`, not to `ORATORIO_COORD`.

Member deactivation shall remove `ORATORIO_COORD` in the same transaction as the Member state and `MEMBER`/`VISITOR` projection. Reactivation shall not restore it. Deactivation shall emit only its high-level Member activity; manual grant and revoke shall emit their own high-level Oratorio Coordinator activities.

Generic Account-role administration shall reject direct mutation because `ORATORIO_COORD` is system-managed. Zero active holders are allowed, and no final-holder protection applies.

## Alternatives considered

### Option 1: Make COORD automatically imply or assign ORATORIO_COORD

Pros:

- Makes GAM-wide Coordinators operationally capable.

Cons:

- Introduces role inheritance or duplicate assignments.
- Records an Oratorio responsibility for Coordinators who do not actually hold it.
- Does not solve lifecycle ownership for non-COORD holders.

### Option 2: Copy every Oratorio permission into COORD and ORATORIO_COORD independently

Pros:

- Requires no lifecycle coupling between the roles.

Cons:

- Creates two permission-maintenance points and shotgun-surgery risk.
- Still leaves `ORATORIO_COORD` assignment disconnected from active Member state.

### Option 3: Member-owned ORATORIO_COORD designation

Pros:

- Enforces active membership.
- Gives grant, revoke, deactivation, concurrency, and audit one owner.
- Preserves a distinct real-world responsibility.
- Works without nested roles.

Cons:

- Broadens Member lifecycle synchronization.
- Requires dedicated permission and operations.

## Consequences

Positive consequences:

- An inactive Member cannot retain Oratorio operational authority.
- GAM Coordinators can grant or revoke the responsibility without generic system-role administration.
- A Member may hold both `COORD` and `ORATORIO_COORD` when that reflects reality.
- Role history remains preserved through Account-role assignments and high-level activities.

Negative consequences:

- Member activation/deactivation projections gain another lifecycle-owned Role.
- Existing Account-role and Member lifecycle requirements must be extended before this ADR is accepted.
- Projection inconsistency must fail closed until explicitly repaired.

## Related requirements

- `REQ-ORATORIO-COORD-001`
- `REQ-ORATORIO-COORD-002`
- `REQ-ORATORIO-COORD-003`
- `REQ-ORATORIO-COORD-004`
- `REQ-ORATORIO-COORD-005`
- `REQ-ORATORIO-COORD-006`

## Related diagrams

- [Oratorio Module Domain](../diagrams/oratorio-module-domain.md)

## Related videos

- None.
