# ADR-0013: Make Member lifecycle own Coordinator designation

## Status
Accepted

## Context
The accepted Member lifecycle already owns the `MEMBER` and `VISITOR` Role projection for a Member's linked Account. ADR-0004 deliberately left `COORD` independent and allowed generic Account-role administration to assign or remove it.

That separation permits an inactive Member to retain Coordinator authority and permits generic role administration to create a Coordinator without expressing a Member-domain responsibility. GAM instead defines a Coordinator as a Member with active coordination responsibility. Coordinator designation, Member state, authorization projection, lockout prevention, concurrency, and activity auditing therefore require one lifecycle owner.

The role catalog must still expose current system roles for inspection and frontend workflows, while ordinary Account-role management must remain available for custom Roles.

## Decision
The Member lifecycle shall exclusively own the `MEMBER`, `VISITOR`, and `COORD` system Role assignments for a Member's linked Account.

A current Coordinator is an active Member whose linked active Account has active assignments to the current `MEMBER` and `COORD` system Roles and no active assignment to `VISITOR`. Coordinator identity shall not be represented by a separate entity or a third Member status.

Coordinator designation shall be granted and revoked through Member-targeted lifecycle operations. Granting requires a consistent active-Member role projection. Revoking preserves the active Member and `MEMBER` Role. Deactivating a Coordinator shall remove both `MEMBER` and `COORD` while assigning `VISITOR` in the same transaction. Reactivation shall restore `MEMBER` but shall not restore `COORD` automatically.

Generic Account-role administration shall accept only active custom Roles with `systemManaged: false`. It shall reject every system-managed Role, including current and future system Roles.

Coordinator grant, revoke, activation, and deactivation decisions affecting the same Member and Account shall serialize. A non-SUDO operation shall not remove the final active Coordinator. An Account with active `SUDO` may remove the final active Coordinator to preserve the existing developer recovery exception.

Each successful Member or Coordinator lifecycle operation shall emit one high-level activity event for its business intent. Internal Role changes shall not emit additional Account-role events.

This decision supersedes ADR-0004.

## Alternatives considered

### Option 1: Keep COORD independently assignable
Pros:
- Preserves the existing generic Account-role workflow.
- Avoids coupling Coordinator changes to Member state.

Cons:
- Allows inactive Members to retain Coordinator authority.
- Allows Coordinator authority without a valid active-Member projection.
- Splits one domain responsibility across generic RBAC and Member lifecycle workflows.

### Option 2: Add a separate Coordinator entity and status lifecycle
Pros:
- Gives Coordinator designation an independent persisted identity.
- Could hold future Coordinator-specific attributes.

Cons:
- Introduces identity and lifecycle state that current requirements do not need.
- Duplicates authorization-facing state already represented by the `COORD` assignment.
- Requires synchronization among Member, Coordinator, Account, and Role records.

### Option 3: Make Coordinator designation part of Member lifecycle
Pros:
- Enforces that every Coordinator is an active Member.
- Keeps Member state and authorization projection consistent at commit.
- Gives Coordinator changes dedicated permission, audit, concurrency, and lockout rules.
- Leaves generic Account-role administration focused on custom Roles.

Cons:
- Broadens Member lifecycle responsibility into Coordinator management.
- Requires new Member endpoints and a new system permission.
- Makes Member deactivation coordinate removal of three lifecycle-owned Roles.

## Consequences

Positive consequences:
- An inactive Member cannot remain a Coordinator.
- Generic role administration cannot bypass Member and Coordinator lifecycle rules.
- Coordinator history remains available through preserved Account-role assignments and high-level activity events without a new entity.
- Frontend role discovery can distinguish custom assignment targets through `systemManaged: false`.

Negative consequences:
- Existing generic `COORD` assignment and removal behavior, tests, and implementation must change.
- A SUDO workflow is required to bootstrap the first Coordinator.
- Projection inconsistency blocks Coordinator changes until explicit maintenance or reconciliation resolves it.

## Related requirements

- `REQ-MEMBER-016`
- `REQ-MEMBER-017`
- `REQ-MEMBER-018`
- `REQ-MEMBER-019`
- `REQ-MEMBER-020`
- `REQ-MEMBER-SOL-014`
- `REQ-ACCOUNT-ROLE-016`
- `REQ-ACCOUNT-ROLE-017`
- `REQ-ACCOUNT-ROLE-018`
- `REQ-RBAC-012`
- `REQ-RBAC-013`
- `REQ-RBAC-002`
- `REQ-RBAC-003`

## Related diagrams

- [Member Lifecycle and Membership Solicitation](../diagrams/member-lifecycle-and-solicitation.md)

## Related videos

- None.
