# ADR-0004: Make Member lifecycle own MEMBER and VISITOR roles

## Status
Superseded

Superseded by [ADR-0013](0013-make-member-lifecycle-own-coordinator-designation.md).

## Context
The `MEMBER` and `VISITOR` system Roles affect Account authorization, while `ACTIVE` and `INACTIVE` describe a Member's domain lifecycle. If generic Account-role administration and Member lifecycle workflows may change these values independently, an Account can gain Member capabilities without a Member or retain them after deactivation.

The accepted Account Role Management Requirement Specification originally treated `MEMBER` as an ordinary manually assignable Role. Planning for the Member lifecycle established a stronger domain invariant: membership Roles must be projections of Member state, while unrelated roles such as `COORD` remain independent.

This decision has cross-domain consequences for Member registration, membership-solicitation approval, reactivation, deactivation, Account-role administration, concurrency, and activity auditing.

## Decision
The Member lifecycle shall exclusively own the `MEMBER` and `VISITOR` system Role assignments for a Member's linked Account.

The projection shall be:

| Member state | Required active role | Role that shall not remain active |
| --- | --- | --- |
| `ACTIVE` | `MEMBER` | `VISITOR` |
| `INACTIVE` | `VISITOR` | `MEMBER` |

Direct Member registration, membership-solicitation approval, reactivation, and deactivation shall update the Member state, lifecycle-owned roles, and one high-level activity event in the same transaction.

Generic Account-role administration shall reject direct addition or removal of `MEMBER` and `VISITOR`. It may continue managing other allowed roles, including `COORD` and custom roles, subject to the Account Role Management requirements.

A Member workflow shall emit one event for its business intent and shall not emit additional Account-role events for the internal role synchronization.

## Alternatives considered

### Option 1: Manage Member status and Account roles independently
Pros:
- Keeps generic role administration unrestricted for ordinary roles.
- Avoids coupling the Member and RBAC workflows.

Cons:
- Allows an Account to hold `MEMBER` without an active Member.
- Allows an inactive Member to retain Member authorization.
- Requires operators to remember multiple manual changes for one business decision.
- Makes inconsistent states normal rather than exceptional.

### Option 2: Synchronize roles asynchronously after lifecycle changes
Pros:
- Reduces the immediate transaction boundary.
- Can reuse event-driven integration between domains.

Cons:
- Creates a period in which Member status and authorization disagree.
- Requires retries, reconciliation, and failure recovery for a security-sensitive projection.
- Makes the lifecycle response succeed before authorization state is reliable.

### Option 3: Let Member lifecycle own roles transactionally
Pros:
- Preserves status and authorization consistency at commit.
- Represents one Coordinator intention with one auditable workflow.
- Prevents generic role administration from bypassing membership rules.
- Leaves unrelated roles independent.

Cons:
- Couples Member lifecycle operations to the availability of current RBAC catalog roles.
- Requires generic Account-role APIs to distinguish lifecycle-owned roles.
- Requires concurrency protection across Member, solicitation, and role-assignment persistence.

## Consequences

Positive consequences:
- `ACTIVE` and `INACTIVE` have an unambiguous authorization projection.
- Solicitation approval cannot grant partial membership.
- Deactivation cannot leave baseline Member capabilities active.
- Activity history records one business action instead of low-level role noise.

Negative consequences:
- Member lifecycle operations fail atomically if required role synchronization cannot complete.
- Direct maintenance of `MEMBER` or `VISITOR` through the Account-role HTTP API is no longer supported.
- Existing implementation and tests that permit manual lifecycle-role changes or a pending Member state must be revised against accepted requirements later.

## Related requirements

- `REQ-MEMBER-003`
- `REQ-MEMBER-004`
- `REQ-MEMBER-005`
- `REQ-MEMBER-007`
- `REQ-MEMBER-012`
- `REQ-MEMBER-SOL-009`
- `REQ-MEMBER-SOL-011`
- `REQ-ACCOUNT-ROLE-003`
- `REQ-ACCOUNT-ROLE-004`
- `REQ-ACCOUNT-ROLE-007`

## Related diagrams

- [Member Lifecycle and Membership Solicitation](../diagrams/member-lifecycle-and-solicitation.md)

## Related videos

- None.
