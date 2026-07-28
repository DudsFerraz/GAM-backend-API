# ADR-0021: Use Flyway repeatable migrations for code-owned system reference data

## Status

Accepted

## Context

GAM has mandatory persisted catalogs, currently system Roles, system
Permissions, and their baseline links, whose accepted definitions evolve with
application code.

These catalogs must exist in every applicable runtime environment, preserve
stable identifiers referenced by domain records, repair missing current data,
and reject ownership collisions. They must also remain separate from one-time
data transformations and development fixtures.

The project already uses Flyway as the exclusive owner of database schema
creation and evolution. The architecture needs one mechanism for reconciling
evolving code-owned reference catalogs without allowing application requests
to run against partially initialized data.

## Decision

Use production-path Flyway Java repeatable migrations to synchronize evolving,
code-owned system reference catalogs.

Each repeatable synchronizer shall:

- derive its desired registry from the application-owned catalog;
- expose a deterministic checksum derived from the complete accepted registry
  so Flyway schedules it whenever that registry changes;
- preflight stable-key ownership and collisions;
- reconcile the complete registry in one migration transaction;
- preserve unique application-owned identities;
- update only changed application-owned metadata;
- perform no writes when already converged; and
- fail migration and application startup when synchronization cannot complete.

Flyway remains the migration lifecycle boundary. Do not add an independent
application startup initializer for the same reference data.

After Flyway completes, perform read-only validation of the complete current
system reference catalog on every application startup. Persisted drift with an
unchanged accepted-registry checksum shall fail startup and require explicit
Developer repair or deliberate migration reapplication; startup shall not
silently mutate the catalog outside Flyway.

Use versioned SQL migrations for:

- schema creation and evolution;
- PostgreSQL enum mirror creation and changes; and
- one-time semantic data transformations.

Use Flyway lifecycle callbacks only from explicitly isolated development or
test fixture locations. The default production-safe path shall not load those
locations and shall never seed human Accounts, credentials, secrets, sessions,
tokens, or privileged bootstrap assignments.

Owning Accepted Requirement Specifications define catalog contents and
feature-specific removal, restoration, stale-data, and cleanup behavior. A
repeatable synchronizer does not infer those semantics.

## Alternatives considered

### Option 1: Add every catalog change through a versioned SQL migration

Pros:

- Every historical catalog change has an explicit ordered migration.
- SQL changes are visible without loading application classes.

Cons:

- The current code registry and accumulated inserts can drift.
- Repairing missing current records becomes separate from defining the current
  registry.
- Stable identity reuse, ownership collisions, and feature-specific
  restoration require increasingly complex SQL.
- New environments replay obsolete intermediate catalog states before reaching
  the accepted current state.

### Option 2: Synchronize through an application startup initializer

Pros:

- Domain catalog types are directly available.
- The implementation is independent of Flyway migration discovery.

Cons:

- Schema readiness and reference-data readiness have separate lifecycle
  boundaries.
- Application startup could proceed far enough to initialize request-serving
  components before reference reconciliation fails.
- Migration history would not identify reference synchronization as part of
  database readiness.
- Multiple initializers could compete with or duplicate migration behavior.

### Option 3: Use Flyway lifecycle callbacks for mandatory reference data

Pros:

- Callbacks naturally run around the migration lifecycle.
- SQL callbacks are simple for fixed fixture inserts.

Cons:

- The same mechanism is used for environment-specific fixtures, increasing the
  risk of loading sample identities or data in production.
- Callback intent is less explicit than a named repeatable synchronizer.
- Evolving code-owned catalogs can drift from duplicated callback SQL.
- Collision, identity-preservation, and owned-field logic is difficult to keep
  aligned with application catalogs.

### Option 4: Use Flyway Java repeatable migrations for code-owned catalogs

Pros:

- Reference synchronization remains part of database readiness.
- The synchronizer can consume the same application-owned registry that
  authorization and domain code use.
- Stable-key collision checks and identity preservation can be explicit.
- New databases converge directly to the accepted current registry.
- Development fixture callbacks remain isolated by purpose and location.

Cons:

- Repeatable migration code must maintain strict transactional and no-op
  behavior.
- The migration must calculate a deterministic checksum from the complete
  accepted registry rather than inheriting a checksum that cannot detect
  registry changes.
- Persistence-focused verification must protect collision, restoration, and
  convergence behavior independently from the production registry code.
- Read-only startup validation is still required to detect persisted drift when
  the accepted registry has not changed.

## Consequences

Positive consequences:

- Application instances serve requests only after mandatory reference data is
  ready.
- Code-owned catalogs and persisted current records have one synchronization
  path.
- Accepted registry changes deterministically schedule repeatable
  synchronization.
- Unexplained database drift is visible and blocks startup instead of being
  silently repaired.
- Repeated startup does not manufacture updates or duplicate relationships.
- Development fixture mechanisms remain outside the production-safe path.
- Versioned migrations retain a focused role for schema and one-time semantic
  transitions.

Negative consequences:

- Repeatable synchronizers become security- and availability-sensitive code.
- An ownership collision deliberately prevents startup until a Developer
  resolves it.
- Persisted drift without a registry change requires explicit Developer repair
  or migration reapplication.
- Removing or renaming a stable key requires an explicit feature lifecycle
  decision rather than automatic cleanup.
- PostgreSQL enum mirror changes still require coordinated schema migrations;
  repeatable data synchronization cannot repair enum-schema drift.

## Related requirements

- [Database Reference Data and Enum Mirrors](../requirements/platform/database-reference-data-and-enum-mirrors.md)
- `REQ-DATA-002`
- `REQ-DATA-003`
- `REQ-DATA-004`
- `REQ-DATA-006`
- `REQ-DATA-007`
- [RBAC Catalog](../requirements/rbac/rbac-catalog.md)

## Related diagrams

- [Database reference-data classification flow](../requirements/platform/database-reference-data-and-enum-mirrors.md#diagrams)

## Related videos

* None.
