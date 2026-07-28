# ADR-0022: Rebuild the pre-production Flyway baseline

## Status

Accepted

## Context

GAM is in pre-production development. It has no production deployment,
external users, or production data that must be preserved.

The current Flyway history contains 32 versioned SQL migrations. Later
migrations rename objects, backfill rows, add columns, replace constraints,
and remove obsolete columns from tables created by earlier migrations. For
example, V31 replaces audit foreign keys and adds deletion-attribution checks
to tables that can be created with those final definitions directly.

Replaying those intermediate states on every new database preserves
unreleased implementation history without preserving any accepted product
behavior. It also makes the current schema harder to review because the
definition of one table is spread across several migrations.

The normal rule that an applied versioned migration is immutable protects
databases whose history or data must survive. Applying that rule to this
one-time pre-production rebuild would prevent the requested cleanup. Replacing
the history therefore requires an explicit reset boundary for every database
that has applied the old versions.

## Decision

Replace the current V1-V32 SQL history in one coordinated change with a new
V1-V25 current-state baseline.

The rebuilt baseline shall:

- use versioned SQL migrations under `src/main/resources/db/migration`;
- start at V1 and use consecutive versions without retaining historical
  version placeholders;
- keep one table or one tightly owned table responsibility per migration;
- order migrations by database dependency;
- create every table, enum mirror, constraint, index, default, and foreign-key
  action in its accepted current form;
- contain no compatibility rename, historical-row backfill, constraint
  replacement, or other transition that exists only to move through an
  unreleased intermediate schema;
- preserve runtime-observable database object names unless an accepted
  requirement or a separately approved refactor changes the application code
  that depends on them; and
- leave production reference-data repeatable migrations and the isolated
  development callback outside the versioned schema baseline.

The baseline manifest is:

| Versioned migration | Current definitions folded into it |
| --- | --- |
| `V1__create_accounts_table.sql` | Current V1 plus the final account audit foreign keys and deletion-attribution check from V31 |
| `V2__create_activity_logs_table.sql` | The final typed, minimized, append-only shape produced by current V19 and V32, without an Account foreign key |
| `V3__create_roles_table.sql` | Current V2 plus the final audit foreign keys and deletion-attribution check from V31 |
| `V4__create_permissions_table.sql` | Current V3 plus the final audit foreign keys and deletion-attribution check from V31 |
| `V5__create_members_table.sql` | Current V6 plus the final audit foreign keys and deletion-attribution check from V31 |
| `V6__create_gam_locations_table.sql` | Current V7, the final GamLocation identity from V22, and the final audit rules from V31 |
| `V7__create_events_table.sql` | Current V8, the required `gam_location_id` relationship from V22, and the final audit rules from V31 |
| `V8__create_presences_table.sql` | Current V9 plus the final audit foreign keys and deletion-attribution check from V31 |
| `V9__create_oratorios_table.sql` | Current V10, occurrence-planning fields and uniqueness from V23, and the final audit rules from V31 |
| `V10__create_oratorianos_table.sql` | Current V14, the final name identity from V25, provenance fields from V29, and the final audit rules from V31 |
| `V11__create_missas_table.sql` | Current V16 plus the final audit foreign keys and deletion-attribution check from V31 |
| `V12__create_refresh_tokens_table.sql` | Current V18 |
| `V13__create_membership_solicitations_table.sql` | Current V21 plus the final audit foreign keys and deletion-attribution check from V31 |
| `V14__create_oratoriano_attendances_table.sql` | Current V26 plus the final audit foreign keys and deletion-attribution check from V31 |
| `V15__create_oratoriano_additional_forms_table.sql` | The form-specific part of current V27 plus the final audit rules from V31 |
| `V16__create_oratoriano_form_print_snapshots_table.sql` | The print-snapshot-specific part of current V27 plus the final audit rules from V31 |
| `V17__create_oratoriano_form_attachments_table.sql` | The attachment-specific part of current V27, active-page uniqueness from V28, page count from V30, and the final audit rules from V31 |
| `V18__create_role_permissions_table.sql` | Current V4 plus the final audit foreign keys and deletion-attribution check from V31 |
| `V19__create_account_roles_table.sql` | Current V5 plus the actor-cleanup behavior from V20 and the deletion-attribution check from V31 |
| `V20__create_oratorio_lanche_table.sql` | Current V11 |
| `V21__create_oratorio_bt_jovens_table.sql` | Current V12 |
| `V22__create_oratorio_bt_criancas_table.sql` | Current V13 |
| `V23__create_oratorio_presences_oratorianos_table.sql` | Current V15 |
| `V24__create_missa_acolhida_members_table.sql` | Current V17 |
| `V25__create_oratorio_team_assignments_table.sql` | Current V24 plus the final nullable audit-actor foreign key from V31 |

Enum mirrors used by only one table shall be created immediately before that
table in the same migration. The baseline shall preserve the exact accepted
enum labels. Java repeatable migrations shall run after the complete
versioned baseline and shall not be converted into historical SQL inserts.
`db/dev-migration/afterMigrate.sql` shall remain isolated to the development
profile and shall not be copied into the production-safe path.

### Reset boundary

Every database that has applied any replaced V1-V32 migration is incompatible
with the new history and must be recreated.

- Local project databases shall stop all GAM application instances, remove
  only the project-scoped PostgreSQL database or Compose volume, and start from
  an empty database.
- Shared development databases shall use an announced maintenance window.
  All connected GAM instances shall stop, the dedicated GAM database or schema
  shall be dropped and recreated by its owner, and only then may an instance
  using the new baseline start.
- Testcontainers and other ephemeral test databases shall continue to create
  an empty database for each test lifecycle.
- Staging or production data preservation is not part of this decision. If a
  database is discovered whose data must survive, the rebuild shall stop for
  that database and require a new decision.

No environment shall use `flyway repair`, `baselineOnMigrate`, out-of-order
migration, checksum editing, or a synthetic schema-history row to make the old
database appear compatible. Those mechanisms would retain an old physical
schema while claiming that the new baseline created it.

Development data loss is intentional. A dump may be kept temporarily for
diagnosis, but importing historical rows into the rebuilt database is not part
of the cutover and must not be required for application startup.

### Verification and cutover

Before the versioned SQL files are rewritten, Agent T shall add structural
coverage that fails against the current history and protects this decision.
The coverage shall verify at least:

- the exact V1-V25 manifest and consecutive version sequence;
- the absence of compatibility-only `ALTER TABLE`, `UPDATE`, constraint
  replacement, and rename operations from the baseline;
- creation of the final table, enum, column, constraint, index, default, and
  foreign-key-action contracts on an empty PostgreSQL database;
- exact database enum-mirror labels required by accepted specifications;
- successful execution of the production repeatable migrations after the
  versioned baseline;
- exclusion of development fixtures from the default migration path; and
- successful, repeatable execution of the development callback when the
  development migration path is explicitly selected.

The historical V22 legacy-row backfill test in
`GamLocationMigrationPersistenceIT` shall be replaced with current-state
baseline coverage for the canonical GamLocation schema. The canonical identity
and required Event-to-GamLocation relationship remain protected; the
unreleased V21-to-V22 transition does not.

Agent D shall then replace the SQL files according to the manifest without
changing business behavior, entity mappings, repeatable-migration ownership,
or fixture policy. If reproducing the current end schema exposes a conflict
between accepted documentation and the existing migration chain, the accepted
documentation wins and the conflict must be reported rather than silently
copied.

Verification shall use a fresh PostgreSQL database. Focused persistence checks
shall run before the repository-wide Maven `verify` gate. This database-only
refactor does not require the OpenAPI profile unless another change in the same
work touches the HTTP contract.

## Alternatives considered

### Option 1: Keep V1-V32 and continue appending migrations

Pros:

- Existing development databases continue without reset.
- Applied checksums remain valid.

Cons:

- New databases replay obsolete pre-production transitions.
- Current table definitions remain fragmented across historical files.
- The project keeps compatibility work that the pre-production policy rejects.

### Option 2: Mutate old migrations but keep all 32 version numbers

Pros:

- File numbering changes less.
- Each old conceptual step still has a named file.

Cons:

- Empty or misleading migration placeholders preserve obsolete history.
- Later version names no longer describe a real transition.
- Existing databases still fail checksum validation and still require reset.

### Option 3: Add one all-in-one baseline migration

Pros:

- The schema can be inspected in one file.
- The migration count is minimal.

Cons:

- It violates the project's granular, coherent-responsibility migration rule.
- Dependency and feature ownership become harder to review.
- Small future changes would be compared against an oversized schema dump.

### Option 4: Rebuild a granular current-state V1-V25 baseline and reset
development databases

Pros:

- New databases create only the accepted current schema.
- Each table has one reviewable creation responsibility.
- Obsolete backfills and constraint replacements disappear.
- Reference data and development fixtures retain their distinct Flyway
  mechanisms.

Cons:

- Every database with the old history must be destroyed and recreated.
- Developers must coordinate one disruptive shared-database cutover.
- Historical development rows are not preserved.

## Consequences

Positive consequences:

- The migration directory becomes a direct description of the current
  pre-production schema.
- Fresh database startup no longer performs obsolete renames, backfills, or
  constraint replacement.
- Accepted audit, soft-delete, activity-log, enum, and reference-data
  invariants remain explicit.
- Future migrations start from a smaller, coherent baseline.
- Flyway validation fails loudly if an old database is accidentally reused.

Negative consequences:

- Local and shared development databases require a destructive reset.
- Any developer data not recreated by accepted fixtures is lost.
- The replacement and shared reset must land as one coordinated operation.
- After this cutover, every new applied migration is again immutable unless a
  later explicit pre-production baseline decision authorizes another reset.

## Related requirements

- [Persistence Auditing and Soft Delete](../requirements/platform/persistence-auditing-and-soft-delete.md)
- [Activity Audit Log](../requirements/platform/activity-audit-log.md)
- [Database Reference Data and Enum Mirrors](../requirements/platform/database-reference-data-and-enum-mirrors.md)
- [Development Fixture Policy and Dataset](../requirements/platform/development-fixture-policy-and-dataset.md)
- [Account Records](../requirements/accounts/account-records.md)
- [Authentication and Registration](../requirements/authentication/authentication-and-registration.md)
- [RBAC Catalog](../requirements/rbac/rbac-catalog.md)
- [Account Role Management](../requirements/rbac/account-role-management.md)
- [Member Records and Lifecycle](../requirements/members/member-records-and-lifecycle.md)
- [Membership Solicitations](../requirements/members/membership-solicitations.md)
- [GamLocation Records](../requirements/gam-locations/gam-location-records.md)
- [Event Records and Generic Lifecycle](../requirements/events/event-records-and-generic-lifecycle.md)
- [Member Event Presences](../requirements/presences/member-event-presences.md)
- [Oratorio Occurrences and Planning](../requirements/oratorio/oratorio-occurrences-and-planning.md)
- [Oratorio Attendance Tracker](../requirements/oratorio/oratorio-attendance-tracker.md)
- [Oratoriano Records](../requirements/oratorianos/oratoriano-records.md)
- [Oratoriano Additional Forms](../requirements/oratorianos/oratoriano-additional-forms.md)

## Related diagrams

- None.

## Related videos

- None.
