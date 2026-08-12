# ADR-0032: Serialize Event, Presence, and Missa Assignment Mutations

## Status

Accepted

## Context

ADR-0012 established the active Event row as the transaction-scoped
serialization boundary for Event editing, lifecycle, deletion, and common
Presence registration, editing, and removal.

The accepted Missa workflow adds coordinator-managed liturgical assignments
with two cross-resource invariants:

- creating an assignment must create or reuse one active common Presence for
  the same Event and Member; and
- while a Missa is `SCHEDULED` or `COMPLETED`, Presence removal must not leave
  an active assignment without its required Presence.

Missa assignment mutation can race with lifecycle closure, deletion, Member
deactivation, Presence removal, duplicate assignment requests, and another
request filling the same single-member responsibility. Database foreign keys
and ordinary prechecks cannot evaluate the latest Event lifecycle or active
relationship state across application instances.

This decision supersedes ADR-0012 by retaining its Event/Presence guarantees
and extending the same boundary to Missa assignments.

## Decision

Event editing, lifecycle transition, and deletion workflows shall acquire a
database row-level lock on the active Event row inside their business
transaction before revalidating type, effective status, audience visibility,
and command-specific rules.

Common Presence registration, observation editing, and removal shall acquire
and revalidate the same active Event row lock before reading or changing active
Presence state.

Missa editing, lifecycle, deletion, assignment addition, and assignment
removal shall acquire and revalidate that same Event row lock before reading or
changing Missa, assignment, Member, or Presence state. Each workflow shall
evaluate one latest committed state after acquiring the lock:

1. Event and specialized Event mutations revalidate active visibility, type,
   effective status, request rules, and related resources.
2. Presence registration revalidates Event audience visibility, effective
   status, the Member, and active pair uniqueness before inserting a new UUID.
3. Presence observation editing and removal revalidate Event audience
   visibility, effective status, the latest active Presence, and any Missa
   assignment dependency defined by `REQ-MISSA-019`.
4. Missa assignment addition revalidates Missa status, responsibility
   cardinality and exact-assignment idempotency, then locks and revalidates the
   target Member before evaluating active Presence state and committing the
   assignment with its one high-level activity.
5. Missa assignment removal revalidates Missa status and the exact
   responsibility/Member pair before committing an actual change or returning
   an idempotent no-op.
6. Event deletion counts only active Presences while holding the Event lock.
   Any active count rejects deletion; removed Presences do not.
7. Every changed workflow persists its owning high-level activity in the same
   transaction. A normalized or idempotent no-op emits none.

The active Event and Member pair shall retain the database uniqueness
safeguard that ignores removed Presence rows. The application shall translate
concurrent uniqueness loss into the owning domain outcome rather than exposing
a persistence error.

Single-member Missa responsibilities shall have a database-backed current
cardinality safeguard or an equivalent constraint whose authoritative conflict
is translated to `MISSA_RESPONSIBILITY_ALREADY_ASSIGNED`. Multi-member
responsibilities shall prevent duplicate current Event/responsibility/Member
relationships.

The lock shall be database-backed and transaction-scoped. If a future workflow
locks multiple Event rows, it shall acquire them in deterministic UUID order.
The assignment workflow shall acquire its Event lock before its target Member
lock. Event relinking to a GamLocation shall additionally follow ADR-0010.

The serialized boundary permits these deliberate correction sequences:

- an assignment is removed first, after which its Presence may be removed
  while the Missa remains `SCHEDULED` or `COMPLETED`;
- a cancelled Missa preserves frozen assignments while common Presence
  removal remains allowed under the cancellation exception;
- Presence removal commits first when no active dependency exists, after which
  eligible Event deletion may commit; or
- Event deletion commits first when no active Presence exists, after which
  later mutations fail because the Event is no longer active.

No active Presence shall ever commit a relationship to a soft-deleted Event.
Removed Presence rows may continue referencing a soft-deleted Event because
both sides remain preserved history.

## Alternatives considered

### Option 1: Keep ADR-0012 and use an independent Missa lock

Pros:

- Leaves the existing Event and Presence paths unchanged.
- Keeps Missa persistence coordination local to the specialization.

Cons:

- Two lock roots can be acquired in inconsistent order.
- Presence removal cannot safely inspect Missa assignments without joining a
  second coordination protocol.
- Lifecycle, assignment, and deletion races can pass stale prechecks.

### Option 2: Foreign keys, uniqueness constraints, and ordinary reads only

Pros:

- No explicit transaction lock path.
- Database constraints still protect physical references and duplicates.

Cons:

- Foreign keys do not protect soft deletion or lifecycle state.
- A Presence removal and assignment addition can both pass stale prechecks.
- Assignment and lifecycle closure can commit incompatible outcomes.
- Constraint failures may leak instead of producing stable domain conflicts.

### Option 3: Process-local coordination

Pros:

- Straightforward inside one API process.
- Avoids database row-lock queries.

Cons:

- Fails across multiple API instances.
- Does not coordinate maintenance or other database clients.
- Requires in-memory key lifecycle and cleanup.

### Option 4: Optimistic Event version checks

Pros:

- Avoids blocking when conflicts are rare.
- Detects concurrent writes to the Event row.

Cons:

- Presence and assignment mutations need not update Event state and therefore
  may not trigger a version conflict.
- Requires retry policies across several workflows.
- Does not by itself coordinate active Presence counting with registration,
  removal, or assignment dependencies.

### Option 5: Serialize every related mutation through the Event row

Pros:

- Coordinates every API instance through one authoritative persistence
  boundary.
- Preserves latest-state lifecycle, assignment, and Presence validation.
- Makes lock ordering explicit and uniform.
- Keeps activities aligned one-for-one with committed business intent.

Cons:

- Mutations targeting the same Event have lower concurrency even when they
  concern different Members.
- Persistence paths need explicit active-row locking and domain-error
  translation.
- Integration tests must exercise real transaction, uniqueness, and lock
  behavior.

## Consequences

Positive consequences:

- Event lifecycle closure and related mutations cannot race into incompatible
  committed states.
- A single-member Missa responsibility has at most one current assignee.
- An assignment creates or reuses Presence atomically.
- Open Missas cannot lose a Presence still required by an assignment.
- Cancelled Missas preserve the accepted correction behavior without silently
  changing assignments.
- Event deletion cannot race with Presence or assignment workflows into
  invalid history.
- Activity entries remain aligned with committed business intent.

Negative consequences:

- Assignment and Presence operations for the same Event serialize even when
  they target different Members.
- Long-running Event transactions can delay assignment and attendance work.
- Common Presence removal must know whether the Event has Missa assignment
  dependencies.
- The application needs explicit domain-error translation for uniqueness and
  occupancy races.

## Related requirements

- `REQ-EVENT-018`
- `REQ-EVENT-019`
- `REQ-PRESENCE-005`
- `REQ-PRESENCE-011`
- `REQ-PRESENCE-013`
- `REQ-PRESENCE-015`
- `REQ-PRESENCE-017`
- `REQ-PRESENCE-018`
- `REQ-MISSA-006`
- `REQ-MISSA-007`
- `REQ-MISSA-008`
- `REQ-MISSA-009`
- `REQ-MISSA-016`
- `REQ-MISSA-018`
- `REQ-MISSA-019`

## Related diagrams

- Inline lifecycle and assignment flow diagrams in
  [`docs/requirements/missa/missa-workflow-and-liturgical-assignments.md`](../requirements/missa/missa-workflow-and-liturgical-assignments.md)
- Inline Presence lifecycle diagram in
  [`docs/requirements/presences/member-event-presences.md`](../requirements/presences/member-event-presences.md)

## Related videos

- None.
