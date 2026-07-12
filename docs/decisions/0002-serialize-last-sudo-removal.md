# ADR-0002: Serialize last-SUDO removal decisions

## Status
Accepted

## Context

`SUDO` is the developer-controlled unrestricted-access role. The system must always retain at least one active SUDO assignment so that a developer recovery path remains available.

SUDO removal is performed through a maintenance workflow and may be invoked more than once at nearly the same time. A check that merely counts active SUDO assignments without synchronizing the check with the removal can allow two concurrent operations to observe two SUDO assignments and both remove one, leaving none.

## Decision

The system shall enforce last-SUDO protection transactionally. A SUDO removal decision shall be serialized with other SUDO removal decisions while the system determines whether the target is the last active SUDO assignment and commits the removal.

If the target is the last active SUDO assignment, the operation shall be rejected. Concurrent operations shall be ordered so that at least one active SUDO assignment remains after every committed removal.

This decision applies to explicit SUDO role removal only. Account deactivation, disabling, deletion, and restoration are governed by separate requirements when planned.

## Alternatives considered

### Option 1: Count active SUDO assignments without synchronization

Pros:
- Simple to implement.
- Low coordination overhead in the uncontended case.

Cons:
- Vulnerable to a race between the count and the removal.
- Could permanently remove the developer recovery path.

### Option 2: Rely only on a database constraint requiring one SUDO assignment

Pros:
- Places enforcement close to persistence.
- Can protect callers that bypass the application workflow.

Cons:
- A minimum-row invariant is difficult to express as a normal row constraint.
- The database error would not clearly express the business safety rule.
- The workflow would still need a deliberate forbidden-operation outcome.

### Option 3: Transactionally serialize SUDO removal decisions

Pros:
- Preserves the recovery invariant under concurrent maintenance commands.
- Gives the application a clear business outcome for the rejected operation.
- Keeps the safety rule at the boundary that understands SUDO role semantics.

Cons:
- SUDO removal transactions may contend briefly with one another.
- Every future explicit SUDO removal path must use the same policy.

## Consequences

Positive consequences:
- At least one active SUDO assignment remains after every committed explicit SUDO removal.
- Concurrent maintenance commands cannot bypass the last-SUDO safety rule.
- The safety rationale is durable for future maintainers.

Negative consequences:
- Maintenance operations must participate in a transaction that can serialize the safety decision.
- Concurrency behavior requires dedicated verification in addition to ordinary success and rejection scenarios.
- Account lifecycle operations remain a separate scope and must not silently assume this ADR covers them.

## Related requirements

- `REQ-ACCOUNT-ROLE-008`
- `REQ-ACCOUNT-ROLE-013`
- `docs/requirements/rbac/account-role-management.md`

## Related diagrams

- The Account-role and SUDO maintenance flow in `docs/requirements/rbac/account-role-management.md`

## Related videos

- None.
