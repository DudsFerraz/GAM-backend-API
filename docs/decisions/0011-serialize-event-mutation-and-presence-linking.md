# ADR-0011: Serialize Event Mutation and Presence Linking

## Status

Superseded

Superseded by [ADR-0012: Serialize Event and Presence Mutations](0012-serialize-event-and-presence-mutations.md).

## Context

`REQ-EVENT-018` requires Event editing, lifecycle commands, deletion, and Presence linking to evaluate the latest committed Event state without lost updates. It also forbids Presence creation and Event soft deletion from racing into a committed Presence reference to a deleted Event.

A foreign key protects against hard deletion but does not protect the Event's soft-delete or lifecycle state. Independent application prechecks can both observe an active Event and then commit incompatible outcomes. Process-local coordination would not protect deployments with more than one API instance.

The solution must also preserve one transactional activity event for each successful Event mutation and no activity for a rejected mutation.

## Decision

Event edit, lifecycle transition, and deletion workflows shall acquire a database row-level lock on the active Event row inside their business transaction before revalidating type, effective status, audience visibility, and command-specific rules.

Presence creation shall acquire and revalidate the same active Event row lock before persisting the Event relationship. Event deletion shall hold the Event lock while counting every Presence reference through a persistence path that includes active and soft-deleted rows.

After acquiring the lock, every workflow shall evaluate the latest committed state:

1. Event mutation revalidates active visibility, type, status, request rules, and related resources before mutation.
2. Event deletion rejects any Presence reference and otherwise soft-deletes and audits in one transaction.
3. Presence creation rejects a missing or soft-deleted Event and otherwise persists its relationship in its owning transaction.
4. A failed validation or conflict emits no activity.

The lock shall be database-backed and transaction-scoped. If a future workflow locks multiple Event rows, it shall acquire them in deterministic UUID order. Event relinking to a GamLocation shall additionally follow the GamLocation lock boundary in ADR-0010.

## Alternatives considered

### Option 1: Foreign key plus ordinary reads

Pros:
- No explicit locking paths.
- The database continues to protect physical Event rows from hard deletion while referenced.

Cons:
- Does not protect Event soft-delete or lifecycle state.
- Presence creation and Event deletion can both pass active-state prechecks and commit.
- Concurrent Event commands can overwrite decisions based on stale status.

### Option 2: Process-local mutex

Pros:
- Straightforward for a single application process.
- Avoids explicit database locking queries.

Cons:
- Fails across multiple API instances.
- Adds in-memory key lifecycle and cleanup concerns.
- Does not coordinate developer or maintenance transactions that bypass the same process.

### Option 3: Optimistic Event version checks

Pros:
- Avoids blocking when conflicts are rare.
- Detects concurrent writes to the Event row.

Cons:
- Presence creation may not update the Event row and therefore may not conflict with Event deletion.
- Requires retries or explicit version-conflict behavior across several workflows.
- Historical Presence counting still needs coordination with a concurrent insert.

### Option 4: Database row-level serialization

Pros:
- Coordinates every API instance through the authoritative persistence boundary.
- Serializes only workflows targeting the same Event.
- Gives status transitions, deletion checks, Presence linking, and activity persistence one consistent transaction boundary.
- Mirrors the accepted GamLocation/Event coordination strategy in ADR-0010.

Cons:
- Conflicting operations may wait for the current Event transaction.
- Persistence code needs explicit active-row locking and historical Presence-count paths.
- Lock order must be maintained if future workflows coordinate multiple Event or related-resource rows.

## Consequences

Positive consequences:
- Event mutations evaluate the latest committed status and avoid lost updates.
- A committed Presence never acquires a concurrently soft-deleted Event.
- Event deletion observes historical Presence references hidden from ordinary repositories.
- Activity events remain aligned one-for-one with committed user-intent mutations.

Negative consequences:
- Mutations and Presence creation for the same Event have lower concurrency.
- Integration tests must exercise real transaction and lock behavior.
- Long-running Event transactions can delay other commands targeting the same Event.

## Related requirements

- `REQ-EVENT-014`
- `REQ-EVENT-015`
- `REQ-EVENT-016`
- `REQ-EVENT-018`

## Related diagrams

- Inline lifecycle diagram in `docs/requirements/events/event-records-and-generic-lifecycle.md`

## Related videos

- None.
