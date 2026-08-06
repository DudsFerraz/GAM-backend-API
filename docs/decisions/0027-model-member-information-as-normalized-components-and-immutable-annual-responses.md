# ADR-0027: Model Member information as normalized components and immutable annual responses

## Status

Accepted

## Context

The 2026 Member-information source combines current Member facts, repeatable
closed catalogs, multi-valued contribution and occupation answers, and
time-bound survey text. The API also needs separate read and update surfaces,
structured search over current information, protected access to annual
answers, and concurrency protection for full replacements.

Putting every field directly on the Member row would blur current and annual
ownership and make multi-valued data awkward. Persisting the survey or Member
profile as JSON would preserve the source shape quickly but weaken relational
constraints, searchability, catalog enforcement, foreign-key provenance, and
schema review.

Full-replacement `PUT` operations create a lost-update risk when a Coordinator
submits a representation loaded before another Coordinator's change. Database
locking alone cannot detect that stale client representation.

## Decision

Keep Member as the aggregate root for current Member-owned information:

- core profile;
- GAM entry date;
- dietary restriction;
- experiences;
- sacraments; and
- contribution profile.

Persist single-valued current components with the Member or a dedicated
one-to-one owned table according to cohesive schema responsibility. Persist
experiences, sacraments, fixed contribution areas, and custom contribution
areas as normalized owned relational rows with database uniqueness and foreign
keys. Do not store Member-owned information as an opaque JSON blob.

The relational responsibilities are:

| Persistence responsibility | Owned data |
| --- | --- |
| `members` | Core profile, GAM entry date, dietary-restriction status and details, lifecycle status, nullable Account link, nullable import-batch provenance, and aggregate version. |
| `member_experiences` | Exactly one status row per Member and experience type. |
| `member_sacraments` | Exactly one status row per Member and sacrament type. |
| `member_contribution_areas` | Distinct fixed contribution codes owned by one Member. |
| `member_other_contribution_areas` | Distinct normalized custom contribution text owned by one Member. |
| `annual_member_information_responses` | One response root per Member and survey cycle with scalar and bounded text answers. |
| `annual_member_occupations` | Distinct occupation codes owned by one annual response. |
| `member_information_import_batches` | Immutable non-sensitive import provenance. |

Model Annual Member Information Response as a separate aggregate root with its
own UUID and a database-enforced unique Member-and-survey-cycle identity.
Persist multi-valued occupations as normalized response-owned rows. Store
bounded scalar and text answers in the response's relational schema. An
imported response is immutable and has no lifecycle status or supersession
state.

Model Member Information Import Batch as a separate immutable provenance root.
Imported Members and responses may reference its UUID, but preparation notes,
source paths, and raw source values are not persisted.

Use PostgreSQL enum mirrors for every persisted closed catalog after its owning
Requirement Specification is accepted. The mirrors are:

- `member_information_status_enum` for information status;
- `member_experience_type_enum` for Member experience type;
- `member_sacrament_type_enum` for Member sacrament type;
- `member_contribution_area_enum` for fixed contribution area;
- `member_occupation_enum` for occupation;
- `member_mass_attendance_frequency_enum` for mass-attendance frequency; and
- `member_coordination_interest_enum` for coordination interest.

Expose separate HTTP representations for ordinary Member information,
experiences and sacraments, contribution profile, and one protected annual
response. Preserve the resource-specific public search-field boundary rather
than exposing table paths.

Use one shared persistent version for the complete Member aggregate. Every
Member representation used for updating returns the same strong opaque ETag,
and every full-replacement Member `PUT` requires the current value through
`If-Match`. Serialize Member writes and advance the shared version only for a
real committed aggregate change. This intentionally allows a harmless
precondition failure after an unrelated Member-component change in exchange
for one simple consistency token.

## Alternatives considered

### Option 1: Add every field to the Member table

Pros:

- Simple direct reads for scalar fields.
- Fewer persistence types initially.

Cons:

- Annual answers become current Member state.
- Repeated survey cycles require more columns or overwriting history.
- Multi-valued experiences, sacraments, contributions, and occupations become
  denormalized.
- Authorization and immutability boundaries remain unclear.

### Option 2: Store the imported source and profiles as JSON

Pros:

- Closely resembles the prepared document.
- New answer fields can be added without immediate table changes.
- Reduces the initial number of migrations and mappings.

Cons:

- Closed catalogs and conditional details become application-only checks.
- Structured current-profile search depends on JSON paths and indexes.
- Source review metadata can leak into domain storage.
- Foreign keys, per-value uniqueness, and schema ownership are weaker.
- Ordinary API changes can accidentally mirror source-document structure.

### Option 3: Keep all information on one mutable Member aggregate, including annual answers

Pros:

- One aggregate and service boundary.
- Current UI could fetch everything in one response.

Cons:

- Updating current contact data could contend with or rewrite historical
  survey information.
- Every ordinary Member reader would approach protected annual data.
- One-response-per-cycle uniqueness and immutable history become artificial
  component rules.

### Option 4: Use normalized Member components and a separate immutable annual aggregate

Pros:

- Ownership matches the agreed field-by-field classification.
- Database constraints can mirror closed catalogs and relational uniqueness.
- Search and authorization remain explicit.
- Later survey cycles can coexist without changing current Member state.
- Import provenance remains available without retaining raw source data.

Cons:

- More tables, mappings, and migrations are required.
- Aggregate updates spanning owned tables need explicit transactional handling.
- Protected annual reads require a separate audited endpoint.

### Option 5: Omit client concurrency control and rely on transaction locks

Pros:

- Simpler request and response headers.
- No stale-version behavior for clients to handle.

Cons:

- A later full replacement can overwrite a field changed after the screen was
  loaded.
- The database cannot distinguish intentional replacement from stale payload.

### Option 6: Use one aggregate ETag for conditional replacement

Pros:

- Prevents stale full replacements across all Member-owned components.
- Uses standard HTTP conditional-request concepts.
- One token reflects one aggregate consistency boundary.

Cons:

- A change to one component may force another editor to reload an unrelated
  component.
- Clients must handle `428` and `412` outcomes.

## Consequences

Positive consequences:

- Current Member state and historical annual responses have explicit owners.
- Database schema, domain catalogs, API representations, and search fields can
  be verified independently but consistently.
- Annual information can be protected and audit-read without classifying every
  ordinary Member lookup as sensitive.
- Full replacement cannot silently erase a newer Member change.
- No-op updates remain observable no-ops with stable versions and timestamps.

Negative consequences:

- The Member aggregate requires coordinated persistence across several owned
  relations.
- Every accepted catalog change requires coordinated domain, database enum,
  contract, fixture, and stored-data disposition work.
- API clients must retain and submit ETags for updates.
- The single version may reject an update after an unrelated aggregate change;
  the client must reload and reapply its intent.

## Related requirements

- [Member Information](../requirements/members/member-information.md)
- [Member Information Import and Account Linking](../requirements/members/member-information-import-and-account-linking.md)
- [Database Reference Data and Enum Mirrors](../requirements/platform/database-reference-data-and-enum-mirrors.md)
- [Search and Filter Framework](../requirements/platform/search-and-filter-framework.md)
- [Activity Audit Log](../requirements/platform/activity-audit-log.md)
- [Persistence Auditing and Soft Delete](../requirements/platform/persistence-auditing-and-soft-delete.md)

## Related diagrams

- [Member Information Ownership](../diagrams/member-information/ownership.md)
- [Member Information Update Concurrency](../diagrams/member-information/update-concurrency.md)

## Related videos

- None.
