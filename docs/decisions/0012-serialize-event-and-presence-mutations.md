# ADR-0012: Serialize Event and Presence Mutations

## Status

Accepted

## Context

`REQ-EVENT-018`, `REQ-EVENT-019`, and the Member Event Presences requirements coordinate several workflows around one Event:

- Event editing, lifecycle commands, and soft deletion;
- Presence registration and re-registration;
- Presence observation editing; and
- Presence removal.

These workflows must evaluate the latest committed Event lifecycle and active Presence state. An ordinary foreign key prevents physical Event deletion while referenced, but it does not prevent an active Presence from racing with Event soft deletion or a Presence mutation from racing with Event lock or finalization.

Active Presence uniqueness also applies only to the current Event and Member pair. A removed Presence is retained historically but releases the pair so a new Presence UUID may be registered. Event deletion must reject active Presences while allowing a mistakenly duplicated Event to be soft-deleted after its active Presences are removed.

ADR-0011 selected Event-row serialization for Event mutations and Presence creation, but it required Event deletion to count active and removed Presences and did not cover Presence editing or removal. The expanded behavior supersedes that decision.

## Decision

Event editing, lifecycle transition, and deletion workflows shall acquire a database row-level lock on the active Event row inside their business transaction before revalidating type, effective status, audience visibility, and command-specific rules.

Presence registration, observation editing, and removal shall acquire and revalidate the same active Event row lock before reading or changing the active Presence state. Each workflow shall evaluate one latest committed state after acquiring the lock:

1. Event mutations revalidate active visibility, type, effective status, request rules, and related resources before mutation.
2. Presence registration revalidates Event audience visibility, `beginDate`, effective status, the Member, and active pair uniqueness before inserting a new UUID.
3. Presence observation editing and removal revalidate Event audience visibility, effective status, and the latest active Presence for the Event and Member pair.
4. Event deletion counts only active Presences while holding the Event lock. Any active count rejects deletion; removed Presences do not.
5. Every changed workflow persists its one matching activity in the same transaction. A normalized Presence observation no-op emits no activity.

The active Event and Member pair shall have a database uniqueness safeguard that ignores removed Presence rows. The application shall translate a concurrent uniqueness loss into `PRESENCE_ALREADY_REGISTERED` rather than exposing a database error.

The lock shall be database-backed and transaction-scoped. If a future workflow locks multiple Event rows, it shall acquire them in deterministic UUID order. Event relinking to a GamLocation shall additionally follow ADR-0010.

This ordering permits both valid correction sequences:

- Presence removal commits first, after which eligible Event deletion may commit because no active Presence remains; or
- Event deletion commits first when no active Presence exists, after which a Presence mutation fails because the Event is no longer active.

No active Presence shall ever commit a relationship to a soft-deleted Event. Removed Presence rows may continue referencing a soft-deleted Event because both sides remain preserved history.

## Alternatives considered

### Option 1: Foreign keys and ordinary reads

Pros:
- No explicit application locking paths.
- The database continues protecting physical Event rows.

Cons:
- Does not protect Event soft-delete or lifecycle state.
- Registration and Event deletion can both pass stale prechecks.
- Observation editing or removal can commit after Event attendance closes.
- Concurrent duplicate registration may leak a persistence constraint error.

### Option 2: Process-local coordination

Pros:
- Straightforward within one API process.
- Avoids explicit database locking queries.

Cons:
- Fails across multiple API instances.
- Does not coordinate maintenance or other database clients.
- Adds in-memory key lifecycle and cleanup concerns.

### Option 3: Optimistic Event version checks

Pros:
- Avoids blocking when conflicts are rare.
- Detects concurrent writes to the Event row.

Cons:
- Presence mutations need not update the Event and therefore may not trigger a version conflict.
- Requires retry and conflict policies across several workflows.
- Does not by itself coordinate active Presence counting with concurrent registration or removal.

### Option 4: Serialize through the Event and count every historical Presence

Pros:
- Preserves the strategy selected by ADR-0011.
- Makes any historical attendance reference permanently protect its Event from normal deletion.

Cons:
- Prevents correction of a mistakenly duplicated Event after even one Presence was recorded and removed.
- Treats soft deletion as if history were physically erased, even though both records remain preserved.
- Makes the user-facing Presence removal workflow insufficient to correct a duplicate Event.

### Option 5: Serialize every Event and Presence mutation and count only active Presences

Pros:
- Coordinates every API instance through the authoritative persistence boundary.
- Preserves latest-state Event lifecycle checks for all attendance mutations.
- Allows deliberate duplicate-Event correction without hard-deleting history.
- Retains one active Presence per pair while supporting removal and re-registration.
- Keeps activity events aligned one-for-one with committed business intent.

Cons:
- Mutations targeting the same Event have lower concurrency.
- Persistence paths need explicit active-row locking and active-only Presence counting.
- Integration tests must exercise real transaction, uniqueness, and lock behavior.

## Consequences

Positive consequences:
- Event lifecycle closure and Presence mutation cannot race into incompatible committed states.
- An active Presence never references a concurrently soft-deleted Event.
- Removed Presences release active pair uniqueness without losing history.
- Coordinators can remove mistaken attendance and then soft-delete a duplicate Event.
- Concurrent duplicate registration produces a stable domain conflict.
- Activity events remain aligned with committed mutations.

Negative consequences:
- Presence operations for the same Event serialize even when they target different Members.
- Long-running Event transactions can delay attendance operations.
- Event deletion no longer treats removed Presence history as an absolute deletion barrier.
- The application needs explicit domain-error translation for uniqueness races.

## Related requirements

- `REQ-EVENT-018`
- `REQ-EVENT-019`
- `REQ-PRESENCE-003`
- `REQ-PRESENCE-005`
- `REQ-PRESENCE-011`
- `REQ-PRESENCE-013`
- `REQ-PRESENCE-015`

## Related diagrams

- Inline Presence lifecycle diagram in `docs/requirements/presences/member-event-presences.md`

## Related videos

- None.
