# ADR-0018: Standardize persistence auditing, soft deletion, and relationship enforcement

## Status

Accepted

## Context

`REQ-PERSISTENCE-001` through `REQ-PERSISTENCE-012` define cross-domain guarantees for row audit metadata, soft-deleted-row visibility, uniqueness, relationship targets, restoration, and physical deletion.

These guarantees cross the application and database boundaries:

- Actor metadata depends on the authenticated application security context.
- Audit timestamps require a trusted, testable clock.
- Ordinary queries and relationship traversal must exclude soft-deleted rows consistently.
- Concurrent requests require database-enforced uniqueness.
- Foreign keys protect physical relationships but do not understand active soft-delete state.
- Low-level row attribution and append-only business activity history have different retention needs.

Relying only on repository naming conventions or application prechecks permits accidental deleted-row exposure and concurrency races. Relying only on database triggers cannot naturally resolve authenticated actors or feature-specific domain outcomes.

## Decision

Use a standardized hybrid persistence strategy.

### Application-owned row auditing

The application persistence layer shall populate row audit metadata from a trusted application clock and the authenticated Account security context.

Full row-audit records shall use creation, latest non-deletion update, and deletion metadata. Rich relationship audit records without an edit lifecycle shall use creation and deletion metadata only.

Creation shall initialize update metadata. Ordinary content changes and restoration shall advance update metadata. Soft deletion shall set deletion metadata without advancing update metadata.

Row audit metadata shall commit with its state mutation. Developer-maintenance operations shall supply the same metadata explicitly when they operate outside an authenticated request.

### Standardized ordinary deleted-row filtering

Every soft-deletable persistence entity shall use one standardized persistence-level active-row restriction that applies automatically to ordinary queries and relationship loading.

Ordinary repositories shall not expose physical-delete, batch-delete, restore, or unrestricted deleted-row query operations.

An explicitly documented historical view, restoration workflow, or maintenance operation shall use a narrowly scoped persistence port that deliberately bypasses the active-row restriction. Ordinary application services shall not receive that capability.

### Database-enforced structural invariants

Flyway migrations shall define every uniqueness rule, foreign key, and audit-metadata check needed by the persistence contract.

Active-only uniqueness shall use a partial unique index or equivalent database constraint whose predicate selects active rows. Reserved uniqueness shall use an unconditional unique constraint or index. Additional owning lifecycle predicates may be combined when a feature requirement defines conditional uniqueness.

Deletion metadata shall be protected so `deletedBy` cannot be populated without `deletedAt`.

The application may perform friendly prechecks, but it shall translate the authoritative database conflict into the owning domain outcome.

### Foreign-key physical-delete behavior

Foreign-key deletion behavior shall follow relationship meaning:

1. Independent domain and historical references prevent physical deletion by default.
2. Wholly owned dependents and ephemeral security artifacts may be physically deleted with their owner when the owning requirement defines no independent history.
3. Low-level row-audit actor references use nullable attribution and may become null after physical actor deletion.
4. Append-only activity entries preserve their recorded actor UUID as immutable historical data without requiring the actor to remain ordinarily visible or physically present.

Soft deletion shall not rely on a foreign-key delete action. The owning application workflow shall preserve, block, or explicitly soft-delete related rows according to its Requirement Specification.

### Relationship target activity and concurrency

Application workflows shall validate that a new or changed relationship target is active at commit time.

When relationship creation or reassignment can race with target soft deletion, the owning workflow shall use a shared database-backed serialization boundary or equivalent commit-time revalidation. A physical foreign key remains required but is not sufficient.

### Maintenance boundary

Developer maintenance uses separate, narrowly authorized persistence capabilities. Restoration and physical deletion require an explicit reason and an immutable maintenance activity that commits with the mutation.

## Alternatives considered

### Option 1: Application conventions and repository prechecks only

Pros:

- Minimal database-specific schema work.
- Friendly duplicate and relationship errors can be detected before writing.
- Repository code appears straightforward for small features.

Cons:

- Concurrent requests across application instances can both pass a precheck.
- Every repository author must remember deleted-row predicates.
- Relationship traversal can expose deleted targets accidentally.
- Physical-delete behavior depends on implicit database defaults.

### Option 2: Database triggers and cascading behavior for all persistence rules

Pros:

- Every database client receives the same timestamp and deletion behavior.
- Integrity is centralized below the application.
- Application persistence code contains less audit plumbing.

Cons:

- Database triggers do not naturally resolve the authenticated Account actor.
- Feature-specific deletion and restoration rules become hidden in schema behavior.
- Broad cascades can erase independent history.
- Translating constraint and trigger failures into stable domain outcomes becomes harder.
- Testing actor and trusted-clock semantics requires database-specific setup.

### Option 3: Standardized hybrid application and database enforcement

Pros:

- The application owns authenticated actors, trusted time, domain visibility, and stable errors.
- The database owns concurrency-safe uniqueness and physical referential integrity.
- A central active-row restriction prevents query-by-query omissions.
- Relationship-specific deletion actions preserve history without forbidding legitimate owned cleanup.
- Explicit bypass ports make restoration and maintenance reviewable.

Cons:

- Application and migration changes must remain aligned.
- Soft deletion needs a dedicated operation so update metadata does not advance.
- Concurrency-sensitive relationships may need explicit locking or revalidation.
- PostgreSQL partial indexes are database-specific.

### Option 4: Preserve every lifecycle change only in generic row columns

Pros:

- All audit information appears beside the current record.
- No separate activity persistence is needed for simple inspection.

Cons:

- One set of columns cannot preserve repeated delete-and-restore cycles.
- Free-text reasons would be duplicated across domain tables.
- Low-level writes would be conflated with meaningful business intent.
- Restoring a row would either erase deletion metadata or require increasingly complex current-state rules.

## Consequences

Positive consequences:

- Ordinary reads have one deleted-row visibility rule.
- Audit metadata has consistent meaning across full records and rich relationships.
- Soft deletion preserves the latest non-deletion update metadata.
- Active-only and reserved uniqueness remain atomic under concurrency.
- Physical deletion preserves independent history and removes only deliberate dependents.
- Row attribution can survive actor cleanup without blocking physical maintenance.
- Append-only activity history retains business actor identity and delete-and-restore history.
- Agent T can derive structural, persistence, concurrency, and acceptance tests without inventing persistence policy.

Negative consequences:

- Existing repositories that inherit dangerous physical or batch deletion operations require narrowing.
- Existing soft-delete saves that advance update metadata require correction.
- Existing audit-actor foreign keys may require new migrations to match nullable attribution policy.
- Every new persisted relationship must be classified as independent, owned, ephemeral, or attribution-only.
- Historical views and restoration require dedicated persistence paths rather than ordinary repositories.

## Related requirements

- `REQ-PERSISTENCE-001`
- `REQ-PERSISTENCE-002`
- `REQ-PERSISTENCE-003`
- `REQ-PERSISTENCE-004`
- `REQ-PERSISTENCE-005`
- `REQ-PERSISTENCE-006`
- `REQ-PERSISTENCE-007`
- `REQ-PERSISTENCE-008`
- `REQ-PERSISTENCE-009`
- `REQ-PERSISTENCE-010`
- `REQ-PERSISTENCE-011`
- `REQ-PERSISTENCE-012`

## Related diagrams

- Inline foreign-key and soft-delete decision flow in [`docs/requirements/platform/persistence-auditing-and-soft-delete.md`](../requirements/platform/persistence-auditing-and-soft-delete.md#diagrams)

## Related videos

- None.
