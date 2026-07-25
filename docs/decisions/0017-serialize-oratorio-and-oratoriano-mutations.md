# ADR-0017: Serialize Oratorio and Oratoriano mutations

## Status

Accepted

## Context

The Oratorio module coordinates one shared Event specialization, common Member Presences, specialized Oratoriano attendance, protected deletion, lifecycle closure, unique Oratoriano identity, and a single current completed form.

Database uniqueness constraints prevent duplicate rows but cannot alone ensure that lifecycle, deletion, quick registration, form completion, and audit activities evaluate one latest domain state. ADR-0012 already uses the Event mutation boundary to coordinate Generic Event and Member Presence races.

## Decision

Extend the ADR-0012 coordination pattern for specialized Oratorio workflows.

Mutations scoped to one occurrence shall acquire the shared Event/Oratorio identity as their first serialized boundary before evaluating planning, team, Member Presence, Oratoriano attendance, lifecycle, or deletion state.

Mutations scoped to one Oratoriano shall acquire that Oratoriano as their first serialized boundary before evaluating profile changes, deletion, restoration, form completion, form supersession, or current-form uniqueness.

The atomic quick-registration workflow shall coordinate the occurrence boundary and then the new or matching Oratoriano identity in a consistent order. Human-equivalent name uniqueness and one-active-attendance invariants shall also be enforced in persistence and translated into domain outcomes.

Every mutation shall re-evaluate the latest committed state after acquiring its boundary. The business mutation and its one high-level activity shall commit together.

## Alternatives considered

### Option 1: Rely only on database constraints

Pros:

- Minimal explicit coordination.
- Duplicate rows can be prevented.

Cons:

- Does not serialize lifecycle and deletion decisions.
- Can expose constraint failures instead of domain outcomes.
- Does not guarantee one matching high-level activity per committed intent.

### Option 2: Use only optimistic version retries

Pros:

- Avoids blocking in uncontended cases.
- Detects some lost updates.

Cons:

- Cross-resource Presence, attendance, form, and deletion decisions still need a shared boundary.
- Retry behavior and side-effect publication become more complex.

### Option 3: Serialize through aggregate identity boundaries

Pros:

- Extends the accepted Event/Presence model.
- Gives lifecycle and attendance one deterministic latest-state order.
- Gives form completion, supersession, and deletion one Oratoriano order.
- Supports stable domain conflicts and transactional activities.

Cons:

- Requires consistent lock ordering across workflows.
- Concurrent mutations of the same occurrence or Oratoriano wait for one another.

## Consequences

Positive consequences:

- Attendance cannot commit against a deleted, locked, finalized, or otherwise ineligible occurrence.
- Occurrence deletion cannot commit while active attendance survives.
- Concurrent form completions leave at most one current completed form.
- Profile deletion and form completion cannot bypass one another's eligibility checks.
- Persistence failures are translated into stable domain outcomes.

Negative consequences:

- Application services must use the same coordination order.
- Long binary upload work must finish before entering the short form-completion transaction.
- Concurrency tests must cover both occurrence-scoped and Oratoriano-scoped races.

## Related requirements

- `REQ-ORATORIO-003`
- `REQ-ORATORIO-010`
- `REQ-ORATORIO-ATT-005`
- `REQ-ORATORIO-ATT-007`
- `REQ-ORATORIO-ATT-009`
- `REQ-ORATORIANO-002`
- `REQ-ORATORIANO-006`
- `REQ-ORATORIANO-009`
- `REQ-ORATORIANO-011`
- `REQ-ORATORIANO-FORM-002`
- `REQ-ORATORIANO-FORM-003`
- `REQ-ORATORIANO-FORM-017`

## Related diagrams

- [Oratorio Module Domain](../diagrams/oratorio-module-domain.md)
- [Oratoriano Additional Form Lifecycle](../diagrams/oratoriano-additional-form-lifecycle.md)

## Related videos

- None.
