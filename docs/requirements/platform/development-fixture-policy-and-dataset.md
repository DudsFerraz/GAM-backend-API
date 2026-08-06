# Requirement: Development Fixture Policy and Dataset

## Status

Accepted

## Context

Local development needs a realistic, repeatable dataset that lets Developers
exercise the GAM API as authenticated product Users without manually rebuilding
every prerequisite after each database recreation.

The development fixture is intentionally privileged. It creates Accounts with
authorization-bearing Roles and stores a synthetic signed-form attachment.
Executing it in production, staging, integration-test defaults, or another
non-development environment would create an unacceptable security and data
integrity risk.

The previous `db/dev-migration/afterMigrate.sql` callback used a committed
six-character password, incomplete personas, invalid Member lifecycle Role
projections, randomly generated fixture identities, and only a small subset of
the current domain. This specification replaces that fixture contract rather
than preserving it.

This specification governs development-only fixture isolation, credential
input, stable fixture ownership, repeatability, the canonical dataset, manual
endpoint readiness, and fixture maintenance. It does not change the business
behavior owned by the related Requirement Specifications.

## Ubiquitous Language

- `canonical fixture record`: A development-only persisted record whose stable
  UUID and intended state are declared by the fixture manifest.
- `fixture manifest`: The explicit catalog of canonical fixture UUIDs,
  identities, relationships, and intended lifecycle states maintained with the
  development callback.
- `fixture projection`: A resettable association or lifecycle state explicitly
  owned by the manifest, including lifecycle-owned Role assignments for a
  canonical fixture Account.
- `sacrificial record`: A canonical fixture record intended to be mutated,
  deleted, revoked, superseded, or otherwise consumed during manual testing.
- `endpoint readiness`: A documented initial dataset state from which a
  Developer can perform one successful request through a user-facing endpoint.
- `fixture reconciliation`: A development startup operation that converges
  canonical fixture records and fixture projections without replacing their
  stable identities or changing unrelated local data.

## Functional requirements

### REQ-DEV-FIXTURE-001: Development-only execution boundary

The development fixture callback shall execute only when both of these
independent gates are satisfied:

1. the active development configuration includes
   `classpath:db/dev-migration` in Flyway locations; and
2. an explicit, development-only fixture-execution marker is present and true.

The default application configuration, production-safe migration path,
integration-test default, maintenance profile, and OpenAPI generation profile
shall exclude `classpath:db/dev-migration`.

The callback shall validate the explicit execution marker before performing
any fixture read or write. A missing, blank, malformed, or false marker shall
abort the callback and application startup without creating or reconciling any
fixture data.

`afterMigrate.sql` and the development configuration shall contain prominent
warnings that the callback creates privileged Accounts and synthetic sensitive
data and must never be enabled outside local development.

Rationale:
Directory isolation prevents ordinary discovery of the callback. An executable
fail-closed marker protects against an accidental configuration change that
adds the development location to another environment.

Valid examples:

- The `dev` profile includes the development migration location and receives an
  explicit local `true` marker.
- The default application starts with only `classpath:db/migration`.

Invalid examples:

- Production includes the development migration directory because the callback
  contains its own warning comment.
- The callback treats an absent execution marker as enabled.
- Integration tests inherit the canonical development personas implicitly.

---

### REQ-DEV-FIXTURE-002: Local credential input and fail-closed validation

Every canonical fixture Account shall use one shared password hash supplied by
the Developer through ignored local configuration.

The repository shall not commit:

- a raw fixture password;
- a usable default fixture password;
- a concrete fixture password hash; or
- a fallback credential value.

The supplied hash shall use the application's current delegated PBKDF2 format.
A missing, blank, malformed, unsupported, or known legacy fixture hash shall
abort development startup before fixture mutation.

The project shall provide a local procedure for producing a compatible PBKDF2
hash from a Developer-selected password. The procedure shall enforce the
accepted 8-to-128-character password boundary and shall not print, log, commit,
or persist the raw password.

The committed local-properties example shall contain only empty fixture
configuration keys and explanatory instructions. The former `123456`
credential and its hash shall not remain usable.

Rationale:
Fixture Accounts need convenient shared login behavior, but a credential known
from repository contents would become exploitable if environment isolation
were ever misconfigured.

---

### REQ-DEV-FIXTURE-003: Stable fixture ownership and collision safety

Every canonical fixture resource and canonical relationship row shall use a
fixed valid UUID v7 declared in the fixture manifest. Canonical fixture rows
shall not receive a new `uuidv7()` identity on each execution.

A manifest UUID shall identify one fixture concept for its lifetime and shall
not later be reused for another persona, Event, form, attachment, or
relationship.

Fixture ownership shall be established by manifest identity, not by heuristic
matching on email, title, display name, personal name, or other mutable data.
The callback shall fail atomically rather than adopt or overwrite a row when:

- a manifest UUID exists for another resource meaning;
- a canonical unique key is owned by an unrecognized UUID;
- more than one row ambiguously represents one canonical fixture identity; or
- a required system reference cannot be resolved uniquely.

Fixture projections shall be explicitly enumerated. They may include the
current lifecycle-owned Role state of canonical fixture Accounts and the
declared attendance, team, form, snapshot, attachment, and custom-role
relationships of canonical records.

A manual API mutation targeting an enumerated fixture projection is part of
that resettable fixture state. A locally created resource or relationship that
is neither a manifest identity nor an enumerated fixture projection is
Developer-created data and shall not be changed merely because it references a
canonical fixture record.

Rationale:
Stable identity makes reconciliation safe. Explicit projection ownership
allows a mutated persona to return to a valid lifecycle state without treating
all data near a fixture record as disposable.

---

### REQ-DEV-FIXTURE-004: Repeatable canonical reconciliation

Each successful development callback execution shall converge every canonical
fixture record and fixture projection to the accepted dataset.

Reconciliation shall:

- create a missing canonical record with its manifest UUID;
- preserve that UUID on every later execution;
- restore a soft-deleted canonical record when its accepted state is active;
- update changed canonical fields and relative dates;
- re-establish the exact accepted lifecycle-owned Role projection for each
  canonical persona Account;
- preserve active uniqueness for every declared relationship;
- remove or deactivate only conflicting rows inside an explicitly enumerated
  fixture projection;
- leave unrelated Developer-created rows unchanged; and
- commit the complete fixture reconciliation atomically.

When the canonical dataset is already converged for the current local date,
rerunning the callback shall not create duplicates, change stable fields,
change audit timestamps, or rewrite binary content.

The callback shall validate all required system Roles, Permissions, system
GamLocations, and dependency rows before applying partial fixture mutation.
Missing or inconsistent production-safe reference data shall fail
reconciliation rather than be recreated as development fixture data.

Rationale:
A Developer may deliberately consume sacrificial records or change a fixture
persona through the API. The next development startup must restore the
documented starting point without resetting the rest of the local database.

---

### REQ-DEV-FIXTURE-005: Bootstrap audit boundary

The callback shall populate row-audit fields consistently with the accepted
persistence contract.

For a no-op reconciliation, existing creation and update audit values shall
remain unchanged. A real reconciliation change shall update only the audit
fields required by that changed row state. Deleted-attribution fields shall
remain null when the corresponding row is active.

The callback shall not create, update, or delete product `activity_logs`
entries. Fixture bootstrap and reconciliation are infrastructure actions, not
Account-performed business workflows.

Manual API operations performed after startup shall continue to create their
normal product activities. A later fixture reconciliation shall not erase
those activities.

Rationale:
Row metadata must remain structurally valid, but fabricated activity history
would claim that product Users performed business actions that never occurred.

---

### REQ-DEV-FIXTURE-006: Fictional, valid, and safe data

All fixture people, contact data, documents, and narratives shall be fictional
and shall not be copied from real GAM Members, Oratorianos, families, or
accounts.

The dataset shall:

- use reserved `example.com` email addresses;
- use realistic Brazilian Portuguese names and GAM-relevant descriptions;
- satisfy the accepted `GamName`, `GamEmail`, `GamPhoneNumber`, `GamCPF`,
  `GamRG`, date, address, and lifecycle validation rules;
- contain correctly encoded UTF-8 accents and punctuation;
- avoid confidential, defamatory, or distressing health and family narratives;
  and
- identify synthetic content visibly where a Developer may download or inspect
  it.

The stored signed-form fixture shall be a small technically valid PDF with
matching MIME type, byte length, page count, order, and SHA-256 metadata. It
shall be visibly marked as synthetic development data and shall contain no real
signature or personal information.

The development fixture shall not duplicate or redefine production-safe system
Roles, Permissions, Role bundles, or system GamLocations.

---

### REQ-DEV-FIXTURE-007: Canonical authentication and authorization personas

The canonical fixture shall contain at least these discoverable Accounts. The
listed email addresses are stable fixture identities and all use the local hash
from `REQ-DEV-FIXTURE-002`.

| Email | Persona | Required initial projection and purpose |
| --- | --- | --- |
| `dev.sudo@example.com` | Technical SUDO | Active Account with only the active `SUDO` Role; no Member record. Supports unrestricted endpoint inspection and SUDO-authorized final-Coordinator scenarios. |
| `mariana.coord@example.com` | Primary Coordinator | Active Member with active `MEMBER` and `COORD`, no `VISITOR` or `ORATORIO_COORD`. Remains the stable ordinary Coordinator actor. |
| `rafael.coord.sandbox@example.com` | Sacrificial Coordinator | Active Member with active `MEMBER` and `COORD`. May be revoked or deactivated while the primary Coordinator preserves lockout safety. |
| `camila.oratorio@example.com` | Oratorio Coordinator | Active Member with active `MEMBER` and `ORATORIO_COORD`, no `VISITOR` or `COORD`. Exercises Oratorio operations without general Coordinator authority. |
| `lucas.member@example.com` | Active Member | Active Member with only `MEMBER`. Exercises Member-visible reads and authorization denials. |
| `helena.inactive@example.com` | Inactive Member | Inactive Member with only `VISITOR`. Exercises linked-Account access, reactivation, and hidden non-active data. |
| `beatriz.registration@example.com` | Direct-registration target | Active Account with no Member, pending solicitation, or lifecycle-owned Role. |
| `fernanda.solicitation@example.com` | Self-submission target | Active Account with no Member, pending solicitation, or lifecycle-owned Role. |
| `joao.approval@example.com` | Approval target | Active Account with one pending solicitation and no Member or lifecycle-owned Role. |
| `aline.rejection@example.com` | Rejection target | Active Account with one pending solicitation and no Member or lifecycle-owned Role. |
| `paulo.custom-role@example.com` | Custom-Role add target | Active Account without the fixture custom Role assignment. |
| `renata.custom-role@example.com` | Custom-Role drop target | Active Account with one active assignment to the fixture custom Role. |

The fixture shall also contain:

- one active custom Role named `EVENT_SUPPORT`, described as read-only Event
  support, with exactly `EVENT_SEARCH`, `EVENT_GET_MEMBER`,
  `EVENT_GET_PRESENCES`, and `GAM_LOCATION_GET`;
- one soft-deleted custom Role named `ARCHIVED_EVENT_SUPPORT` for ordinary
  visibility and ineligible-target scenarios;
- one active custom-role assignment for
  `renata.custom-role@example.com`; and
- stable assignment identities required by the Account-role lookup route.

The canonical projections shall follow the Member lifecycle ownership rules.
In particular, an inactive Member shall never retain `MEMBER`, `COORD`, or
`ORATORIO_COORD`, and an Account without a Member shall not begin with
`MEMBER`, `VISITOR`, `COORD`, or `ORATORIO_COORD`.

The fixture shall additionally provide the fictional Account-less Member,
eligible Account, and link-conflict personas required by
`REQ-MEMBER-INFO-FIXTURE-002` and `REQ-MEMBER-INFO-FIXTURE-003`. Synthetic
Account-less Members shall be owned by the isolated fixture manifest and shall
not pretend to have production import-batch provenance.

---

### REQ-DEV-FIXTURE-008: Dataset scale and searchable variation

The converged fixture shall contain at least:

- 60 active Members;
- 2 inactive Members;
- 60 non-deleted Oratorianos; and
- 1 soft-deleted, restorable Oratoriano without an immutable form.

The volume shall make a second page observable for the default Member search
and the fixed 50-person Oratorio attendance rosters.

Filler records shall remain deterministic canonical fixtures rather than
random data. Their names, emails, birth dates, phones, Roles, attendance
counts, accents, punctuation, and lifecycle states shall provide meaningful
variation for the accepted public search and sorting fields.

The dataset shall include distinct examples of:

- accented, hyphenated, and apostrophe-bearing names;
- active and inactive Member status;
- linked and Account-less Member state;
- Member core, dietary, experience, sacrament, and contribution-profile
  variation across every accepted catalog;
- immutable annual Member information covering every accepted answer catalog,
  nullability seam, and protected-read path;
- Account Role variation;
- public, Member-restricted, and Coordinator-restricted Events;
- Event types `GENERIC` and `ORATORIO`;
- Event and Oratorio lifecycle states;
- present and absent optional fields;
- Member Presences with and without observations; and
- Oratoriano attendance spanning multiple months and years.

The fixture shall not invent a specialized `MISSA` dataset until an Accepted
Missa Requirement Specification defines its source data and lifecycle.

---

### REQ-DEV-FIXTURE-009: Canonical workflow state catalog

The fixture shall provide separate baseline and sacrificial resources for these
initial states:

| Area | Required initial fixture states |
| --- | --- |
| Membership solicitations | At least two `PENDING` solicitations for independent approve and reject operations; at least one `REJECTED` historical solicitation; at least one `APPROVED` historical solicitation linked to its resulting active Member; and one eligible Account with no solicitation for self-submission. |
| Members | Active non-Coordinator, inactive, current Coordinator, current Oratorio Coordinator, active and inactive Account-less Members, eligible and conflicting Account-link targets, complete current-information catalog variation, annual responses with protected-read seams, a Member without an annual response, grantable and revocable responsibility targets, an Account eligible for direct registration, and personal Presence histories with both empty and non-empty results. |
| GamLocations | Current production-safe system locations; active ordinary linked and unlinked locations; an ordinary update target; an unused removal target; and a soft-deleted ordinary location hidden from normal reads. |
| Generic Events | Public, Member-restricted, and Coordinator-restricted Events; relative `SCHEDULED` and `COMPLETED` Events; explicit `LOCKED`, `FINALIZED`, and `CANCELLED` Events; editable and deletable sacrificial Events; an Event blocked from deletion by active Presence; and an Event with only removed Presence history. |
| Member Presences | Active Presences with null and non-null observations, an eligible missing pair for registration, a removed pair eligible for re-registration, an inactive Member's preserved Presence, and roster data suitable for name filtering and ordering. |
| Oratorios | Relative future and historical occurrences covering `SCHEDULED`, `COMPLETED`, `LOCKED`, `FINALIZED`, and `CANCELLED`; planning text variation; all four team types; occurrences with no attendance, active attendance, and only removed attendance; and an unused documented future date for manual creation. |
| Oratorianos | Minimal name-only records, enriched ordinary profiles, multi-period attendance histories, a non-deleted record with no attendance, a deletable draft-only record, a soft-deleted restorable record, and a record whose immutable form blocks deletion. |
| Additional forms | `DRAFT` records for create/update/delete/upload flows; a printable draft; a completion-ready draft with a current print snapshot and complete synthetic attachment; one current `COMPLETED` form; one `SUPERSEDED` form; one `REVOKED` form; multiple downloadable print snapshots across revisions; and a downloadable synthetic signed-attachment collection. |

Relative Event and Oratorio dates shall be recalculated from the current
`America/Sao_Paulo` local date on reconciliation. Fixture Events intended to be
future-scheduled shall begin no earlier than seven local days after
reconciliation. Fixture Events intended to be time-completed or explicitly
closed shall end no later than seven local days before reconciliation.

The documented unused Oratorio creation date shall be 60 local days after the
reconciliation date. Historical attendance shall include occurrences at least
35 and 370 local days before reconciliation so the dataset spans different
months and at least two calendar years.

---

### REQ-DEV-FIXTURE-010: User-facing endpoint readiness

Every current user-facing endpoint shall be represented by this readiness
matrix. The accepted controller surface contains 99 endpoint methods. Endpoint
readiness requires a successful starting
path; representative authorization, visibility, duplicate, in-use, and
invalid-lifecycle paths shall also exist per workflow family. Exhaustive
invalid-input permutations remain an automated-test responsibility.

#### Authentication, Accounts, and RBAC

| Endpoint | Fixture readiness |
| --- | --- |
| `GET /auth/csrf` | Requires no persisted fixture prerequisite. |
| `POST /auth/register` | A Developer may use a new reserved-example email not present in the manifest. No fixture Account shall reserve all convenient registration addresses. |
| `POST /auth/login` | Every canonical persona Account is login-ready with the locally selected password. |
| `POST /auth/refresh` | A successful persona login can create the required refresh-token session and CSRF state. |
| `POST /auth/logout` | A successful persona login can create the session to log out; the endpoint remains repeatable without a session. |
| `GET /accounts/me` | Every authenticated persona exposes a distinct current Account context. |
| `GET /accounts/{accountId}` | Self, other-account, role-varied, and unprivileged Accounts exist. |
| `POST /accounts/search` | Accounts vary by email, display name, and active Role assignment. |
| `GET /accounts/{accountId}/roles` | Accounts with zero, one, and multiple active Roles exist. |
| `POST /accounts/{accountId}/roles` | `paulo.custom-role@example.com` and active custom Role `EVENT_SUPPORT` provide an eligible missing pair. |
| `PATCH /accounts/{accountId}/roles/{roleId}/drop` | `renata.custom-role@example.com` has an active `EVENT_SUPPORT` assignment. |
| `GET /accounts/{accountId}/role-assignments/{assignmentId}` | The active custom-role assignment has a stable manifest UUID. |
| `GET /roles` | Current system Roles and active custom `EVENT_SUPPORT` exist; `SUDO` and the soft-deleted custom Role provide exclusion cases. |
| `GET /roles/{roleId}` | Stable current system and custom Role identifiers exist, including the known SUDO identifier through the production-safe catalog. |
| `GET /roles/{roleId}/permissions` | Roles with empty, small custom, and baseline system permission bundles exist. |
| `GET /permissions/{permissionId}` | The production-safe permission registry supplies stable current Permission records. |

#### Membership and Member workflows

| Endpoint | Fixture readiness |
| --- | --- |
| `POST /membership-solicitations` | `fernanda.solicitation@example.com` has no Member, lifecycle-owned Role, or pending solicitation. |
| `GET /membership-solicitations/{id}` | Pending, approved, and rejected stable solicitation identifiers exist. |
| `POST /membership-solicitations/search` | Status, applicant, reviewer, submission-time, and decision-time variation exists. |
| `PATCH /membership-solicitations/{id}/approve` | `joao.approval@example.com` has an independent pending solicitation and a valid pre-Member projection. |
| `PATCH /membership-solicitations/{id}/reject` | `aline.rejection@example.com` has an independent pending solicitation. |
| `POST /members` | `beatriz.registration@example.com` is an eligible Account without Member or pending solicitation. |
| `GET /members/{id}` | Own active, own inactive, other active, and other inactive Member records exist. |
| `POST /members/search` | More than one page of Members varies across every accepted public filter family. |
| `GET /members/{memberId}/experiences-and-sacraments` | A visible Member has varied current experience and sacrament statuses. |
| `GET /members/{memberId}/contribution-profile` | Visible Members have empty, fixed, and custom contribution profiles. |
| `PUT /members/{memberId}` | A dedicated core-profile target has a current ETag and valid replacement values. |
| `PUT /members/{memberId}/gam-entry-date` | A dedicated target has a non-future alternative date. |
| `PUT /members/{memberId}/dietary-restriction` | A dedicated target supports a valid conditional-details transition. |
| `PUT /members/{memberId}/experiences` | A dedicated target can replace the complete four-key map. |
| `PUT /members/{memberId}/sacraments` | A dedicated target can replace the complete three-key map. |
| `PUT /members/{memberId}/contribution-profile` | A dedicated target can replace fixed and custom collections. |
| `PATCH /members/{memberId}/account/link` | Independent active and inactive Account-less Members and eligible Accounts exist. |
| `GET /members/{memberId}/annual-information/{surveyCycle}` | A visible protected response exists for successful audited read, and another Member has no response. |
| `PATCH /members/{id}/coordinator/grant` | An active non-Coordinator Member has a valid `MEMBER`-only projection. |
| `PATCH /members/{id}/coordinator/revoke` | The sacrificial Coordinator may be revoked while the primary Coordinator remains. |
| `PATCH /members/{id}/oratorio-coordinator/grant` | An active Member without `ORATORIO_COORD` is reserved for the grant. |
| `PATCH /members/{id}/oratorio-coordinator/revoke` | The Oratorio Coordinator persona provides a revocable assignment. |
| `PATCH /members/{id}/activate` | An inactive Member has the valid `VISITOR`-only projection. |
| `PATCH /members/{id}/deactivate` | A sacrificial active Member can be deactivated without removing the final Coordinator. |
| `GET /members/{memberId}/presences` | Members with empty, single, multi-Event, and inactive personal histories exist. |

#### GamLocation, Event, and Presence workflows

| Endpoint | Fixture readiness |
| --- | --- |
| `POST /gam-locations` | Existing varied locations support duplicate checks while a unique ordinary place can be created. |
| `GET /gam-locations/{id}` | Current system, active ordinary, and soft-deleted ordinary identifiers exist. |
| `GET /gam-locations` | Current system and ordinary records provide deterministic multi-record listing. |
| `PUT /gam-locations/{id}` | A dedicated active ordinary update target is not required by another baseline workflow. |
| `DELETE /gam-locations/{id}` | Dedicated unused and Event-referenced ordinary locations support success and in-use conflict. |
| `POST /events` | Active ordinary/system locations and current audience Permission identifiers exist. |
| `GET /events/{id}` | Public, Member-restricted, Coordinator-restricted, and specialized Events exist. |
| `POST /events/search` | Event audience, type, status, date, title, description, location, and permission variation exists. |
| `PUT /events/{id}` | Dedicated editable `SCHEDULED`, `COMPLETED`, and `LOCKED` Generic Events exist. |
| `PATCH /events/{id}/lock` | A completed sacrificial Generic Event exists. |
| `PATCH /events/{id}/finalize` | Independent completed and locked sacrificial Generic Events exist. |
| `PATCH /events/{id}/reopen` | Independent locked and finalized Generic Events exist. |
| `PATCH /events/{id}/cancel` | A scheduled sacrificial Generic Event exists. |
| `DELETE /events/{id}` | Eligible no-Presence, active-Presence-blocked, and removed-Presence-only Generic Events exist. |
| `POST /events/{eventId}/presences` | A registration-eligible Event and Member pair without an active Presence exists. |
| `GET /events/{eventId}/presences` | A visible Event has a multi-Member active roster with name and registration-time variation. |
| `GET /events/{eventId}/presences/{memberId}` | A stable active Event/Member Presence pair exists. |
| `PATCH /events/{eventId}/presences/{memberId}` | A dedicated editable active Presence exists. |
| `DELETE /events/{eventId}/presences/{memberId}` | A dedicated removable active Presence exists. |

#### Oratorio workflows

| Endpoint | Fixture readiness |
| --- | --- |
| `POST /oratorios` | A documented unused future local date exists and the configured system location code resolves. |
| `GET /oratorios/{oratorioId}` | Specialized occurrences exist in every accepted lifecycle state. |
| `PUT /oratorios/{oratorioId}/planning` | A scheduled sacrificial occurrence has independently varied optional planning fields. |
| `PUT /oratorios/{oratorioId}/teams/{teamType}/members/{memberId}` | Each accepted team type has an eligible active Member not yet assigned. |
| `DELETE /oratorios/{oratorioId}/teams/{teamType}/members/{memberId}` | Each accepted team type has at least one existing assignment. |
| `PATCH /oratorios/{oratorioId}/lock` | A completed sacrificial occurrence exists. |
| `PATCH /oratorios/{oratorioId}/finalize` | Independent completed and locked sacrificial occurrences exist. |
| `PATCH /oratorios/{oratorioId}/cancel` | A scheduled sacrificial occurrence exists. |
| `PATCH /oratorios/{oratorioId}/reopen` | Independent locked and finalized occurrences exist. |
| `DELETE /oratorios/{oratorioId}` | Eligible no-attendance, active-attendance-blocked, and removed-attendance-only occurrences exist. |
| `GET /oratorios/{oratorioId}/attendance/members` | More than 50 active Members provide multiple pages, name search, and an existing present subset. |
| `GET /oratorios/{oratorioId}/attendance/oratorianos` | More than 50 non-deleted Oratorianos provide multiple pages, name search, and an existing present subset. |
| `GET /oratorios/{oratorioId}/attendance/present` | One occurrence has both Member and Oratoriano attendance. |
| `PUT /oratorios/{oratorioId}/attendance/members/{memberId}` | A completed attendance-open occurrence has an eligible unmarked Member and an already marked Member. |
| `DELETE /oratorios/{oratorioId}/attendance/members/{memberId}` | A completed occurrence has a marked Member and supports the required correction reason. |
| `PUT /oratorios/{oratorioId}/attendance/oratorianos/{oratorianoId}` | A completed attendance-open occurrence has an eligible unmarked Oratoriano and an already marked Oratoriano. |
| `DELETE /oratorios/{oratorioId}/attendance/oratorianos/{oratorianoId}` | A completed occurrence has a marked Oratoriano and supports the required correction reason. |
| `POST /oratorios/{oratorioId}/attendance/oratorianos/register-and-mark` | A completed attendance-open occurrence exists and the Developer can supply a new unique complete fictional name. |

#### Oratoriano and additional-form workflows

| Endpoint | Fixture readiness |
| --- | --- |
| `POST /oratorianos` | The Developer can supply a new unique complete fictional name; the dataset provides similar-name prompts without reserving every useful name. |
| `GET /oratorianos/{oratorianoId}` | Minimal and enriched non-deleted ordinary profiles exist. |
| `PUT /oratorianos/{oratorianoId}` | A dedicated active Oratoriano without immutable form blockers is available for correction. |
| `DELETE /oratorianos/{oratorianoId}` | A dedicated draft-only Oratoriano is deletable, while another immutable-form record supplies the conflict path. |
| `PATCH /oratorianos/{oratorianoId}/restore` | A dedicated soft-deleted Oratoriano has a reserved name and no immutable form. |
| `GET /oratorianos/{oratorianoId}/attendances` | Oratorianos with empty and multi-period active attendance histories exist. |
| `GET /oratorianos/{oratorianoId}/attendance-summary` | One Oratoriano has attendance in multiple months and at least two years. |
| `POST /oratorianos/search` | More than 60 varied names and attendance counts support filtering, sorting, and pagination. |
| `POST /oratorianos/{oratorianoId}/forms` | An active Oratoriano is available for an additional draft version. |
| `GET /oratorianos/{oratorianoId}/forms` | One Oratoriano has multiple lifecycle versions for metadata-only history. |
| `GET /oratorianos/{oratorianoId}/forms/{formId}` | Stable draft and immutable form identifiers support audited detail reads. |
| `PUT /oratorianos/{oratorianoId}/forms/{formId}` | A dedicated editable draft exists. |
| `DELETE /oratorianos/{oratorianoId}/forms/{formId}` | A dedicated draft and its draft-owned artifacts are sacrificial. |
| `PATCH /oratorianos/{oratorianoId}/forms/{formId}/complete` | A completion-ready draft has valid structured data, current print-snapshot correspondence, and a complete synthetic attachment. |
| `PATCH /oratorianos/{oratorianoId}/forms/{formId}/revoke` | A current completed form exists independently of the completion-ready draft. |
| `POST /oratorianos/{oratorianoId}/forms/{formId}/print-snapshots` | A dedicated draft exists for snapshot creation. |
| `GET /oratorianos/{oratorianoId}/forms/{formId}/print-snapshots` | A stable form has multiple non-deleted snapshots across draft revisions for pagination and deterministic newest-first ordering. |
| `GET /oratorianos/{oratorianoId}/forms/{formId}/print-snapshots/{printSnapshotId}/pdf` | A stable printable snapshot exists for audited PDF rendering. |
| `PUT /oratorianos/{oratorianoId}/forms/{formId}/signed-attachments` | A dedicated draft without an immutable attachment collection is available. |
| `GET /oratorianos/{oratorianoId}/forms/{formId}/signed-attachments` | A stable form has an active ordered synthetic attachment collection, while replaced files remain excluded from ordinary reads. |
| `GET /oratorianos/{oratorianoId}/forms/{formId}/signed-attachments/{attachmentId}` | A stable synthetic PDF attachment exists for audited download. |

---

### REQ-DEV-FIXTURE-011: Sacrificial workflow isolation

A state-changing endpoint shall not depend on consuming the only canonical
record required by another endpoint family during the same development
session.

The fixture shall use distinct sacrificial records for independent destructive
or terminal operations when one operation would otherwise prevent another.
This includes, at minimum:

- solicitation approval and rejection;
- Coordinator and Oratorio Coordinator grant and revoke;
- Member activation and deactivation;
- Member core, GAM-entry-date, dietary, experience, sacrament, and
  contribution-profile replacement;
- active-Member link, inactive-Member link, and Account-less lifecycle changes;
- custom Role add and drop;
- GamLocation update and removal;
- Generic Event lock, finalize, reopen, cancel, and delete;
- common Presence edit and removal;
- Oratorio lifecycle, team, attendance, and deletion operations;
- Oratoriano update, deletion, and restoration; and
- additional-form deletion, completion, revocation, snapshot creation,
  attachment upload, and attachment download.

The primary Coordinator, technical SUDO, production-safe reference data, and
baseline records used for discovery shall not be the default sacrificial
targets.

---

### REQ-DEV-FIXTURE-012: Fixture maintenance rules

The fixture manifest, callback, this Requirement Specification, and focused
fixture verification shall be updated together when a change affects:

- a user-facing endpoint or route family;
- an accepted Role, Permission, or role-permission bundle;
- a Member, Event, Oratorio, Oratoriano, form, attendance, or soft-delete
  lifecycle;
- a Member-information catalog, annual-response contract, Account-link
  workflow, or Member aggregate ETag;
- database columns, constraints, enum mirrors, audit fields, or relationship
  ownership;
- a fixture persona, stable fixture UUID, relative-date rule, or sacrificial
  workflow; or
- the application password encoder.

Adding a user-facing endpoint requires either:

1. adding a successful pre-seeded readiness path to
   `REQ-DEV-FIXTURE-010`; or
2. explicitly documenting why that endpoint requires no persisted fixture
   prerequisite.

Removing an endpoint shall remove its readiness entry. A fixture UUID or stable
persona email shall not be silently repurposed after removal.

The callback shall remain organized in database dependency order and shall
state the manifest purpose of each canonical record group. It shall use
assertions for required reference data instead of allowing null foreign keys or
partial fixture construction.

Focused verification shall establish environment isolation, credential
failure, fresh creation, repeatability, stable UUIDs, lifecycle projection
consistency, unrelated-row preservation, time-relative state, scale thresholds,
synthetic attachment integrity, and endpoint-matrix prerequisites.

---

### REQ-DEV-FIXTURE-013: Initial replacement rollout

Adopting this specification shall require one clean recreation of each existing
local development database.

The rebuilt callback shall not contain heuristic cleanup that deletes the
legacy fixture by matching its old Gmail addresses, titles, display names, or
random UUIDs. Existing local development data is disposable under the current
pre-production project state, but the repository shall not guess which
similarly named rows a Developer intended to keep.

After the clean recreation establishes the new manifest identities, later
executions shall follow the reconciliation contract in
`REQ-DEV-FIXTURE-003` and `REQ-DEV-FIXTURE-004`.

---

### REQ-DEV-FIXTURE-014: Oratorio system-location prerequisite

Oratorio fixture readiness shall depend on the production-safe current system
GamLocation catalog and `gam.oratorio.location-code` required by
`REQ-GAM-LOCATION-CATALOG-008`.

The fixture shall not seed an ordinary GamLocation with a legacy name merely to
satisfy name-based Oratorio lookup. It shall not redefine `DBSM`, `DBA`, or
`DBCA`.

Before Oratorio endpoint readiness is considered satisfied, the application
shall resolve the configured immutable location code and shall default to
`DBSM`. The currently implemented `gam.oratorio.location-name` behavior
conflicts with the Accepted system-location and Oratorio requirements and shall
be corrected rather than preserved in the fixture.

## Acceptance scenarios

```gherkin
Scenario: Default migration path excludes development fixtures
  Given the application does not run with the development fixture configuration
  When Flyway resolves its migration locations
  Then classpath:db/dev-migration is absent
  And no canonical fixture Account exists

Scenario: Explicit marker is required
  Given the development migration location is configured
  But the fixture-execution marker is missing or false
  When the development callback starts
  Then startup fails before fixture mutation

Scenario: Local password hash is required
  Given both development execution gates are enabled
  But the local PBKDF2 fixture hash is missing, malformed, or the legacy hash
  When the development callback starts
  Then startup fails before any fixture Account is created or changed

Scenario: Fresh development database receives the complete fixture
  Given production-safe migrations and reference-data synchronizers succeed
  And valid local fixture configuration is present
  When the development callback runs on a clean database
  Then every manifest UUID and canonical projection exists
  And at least 60 active Members and 60 non-deleted Oratorianos exist
  And every endpoint readiness prerequisite is satisfied

Scenario: Repeated reconciliation preserves stable identity
  Given the fixture is converged for the current local date
  When the callback runs again with unchanged configuration
  Then no canonical UUID changes
  And no duplicate canonical row or active relationship is created
  And no no-op audit timestamp changes

Scenario: Mutated sacrificial fixture state is restored
  Given a Developer used API workflows to consume sacrificial records
  When the next development callback reconciles the fixture
  Then every enumerated fixture projection returns to its accepted state
  And product activities from the manual operations remain preserved

Scenario: Unrelated local records survive reconciliation
  Given a Developer created records whose UUIDs and relationships are outside the fixture manifest and projections
  When the development callback reconciles canonical fixtures
  Then those unrelated records remain unchanged

Scenario: Relative dates remain useful
  Given the local calendar date has advanced
  When the callback reconciles temporal fixture records
  Then future scenarios remain scheduled
  And historical scenarios remain completed or explicitly closed
  And attendance history still spans multiple months and years

Scenario: Synthetic sensitive content is downloadable
  Given the fixture is converged
  When an authorized persona requests the canonical synthetic signed attachment
  Then the stored bytes are a valid PDF
  And the verified metadata matches the bytes
  And the document is visibly synthetic and contains no real personal data

Scenario: Fixture bootstrap does not fabricate product activity
  Given product activity entries may already exist from manual API operations
  When the callback creates or reconciles fixture rows
  Then it creates no fixture-bootstrap product activity
  And it does not remove or rewrite existing product activities

Scenario: Oratorio creation uses the accepted system location code
  Given gam.oratorio.location-code is omitted
  And the system GamLocation catalog is converged
  When the application starts and an authorized persona creates an Oratorio
  Then DBSM is selected by immutable code
  And no development-only duplicate of the system location is required

Scenario: Existing local database uses the clean replacement path
  Given a local database was initialized with the superseded random fixture
  When the Developer adopts this fixture specification
  Then the local database is recreated once
  And the callback does not heuristically delete legacy rows
```

## Open questions

* None.

## Out of scope

* Production, staging, demonstration, or integration-test-default fixture
  seeding.
* A compatibility or in-place upgrade path for the superseded random local
  fixture.
* Copies of real GAM people, contact data, health data, family data, documents,
  signatures, or credentials.
* A standard committed fixture password.
* Exhaustive manual coverage of every invalid input, boundary, concurrency
  race, or error response.
* Replacing automated tests with the manual endpoint-readiness dataset.
* Fabricated product activity history for infrastructure-created records.
* A specialized Missa fixture before an Accepted Missa Requirement
  Specification exists.
* Product behavior changes owned by the related Requirement Specifications,
  except correction of the already-Accepted Oratorio location-code contract.

## Related requirements

* [Authentication and Account Registration](../authentication/authentication-and-registration.md)
* [Account Records](../accounts/account-records.md)
* [Account Role Management](../rbac/account-role-management.md)
* [RBAC Catalog](../rbac/rbac-catalog.md)
* [Member Records and Lifecycle](../members/member-records-and-lifecycle.md)
* [Membership Solicitations](../members/membership-solicitations.md)
* [Member Information](../members/member-information.md)
* [Member Information Import and Account Linking](../members/member-information-import-and-account-linking.md)
* [Member Information Development Fixture Extension](member-information-development-fixture.md)
* [GamLocation Records](../gam-locations/gam-location-records.md)
* [System GamLocation Catalog](../gam-locations/system-gam-location-catalog.md)
* [Event Records and Generic Event Lifecycle](../events/event-records-and-generic-lifecycle.md)
* [Member Event Presences](../presences/member-event-presences.md)
* [Oratorio Occurrences and Planning](../oratorio/oratorio-occurrences-and-planning.md)
* [Oratorio Attendance Tracker](../oratorio/oratorio-attendance-tracker.md)
* [Oratorio Coordinator Designation](../oratorio/oratorio-coordinator-designation.md)
* [Oratoriano Records](../oratorianos/oratoriano-records.md)
* [Oratoriano Additional Forms](../oratorianos/oratoriano-additional-forms.md)
* [Persistence Auditing and Soft Delete](persistence-auditing-and-soft-delete.md)
* [Activity Audit Log](activity-audit-log.md)
* [Search and Filter Framework](search-and-filter-framework.md)

## Related ADRs

* [ADR-0013: Make Member lifecycle own Coordinator designation](../../decisions/0013-make-member-lifecycle-own-coordinator-designation.md)
* [ADR-0014: Make Member lifecycle own Oratorio Coordinator designation](../../decisions/0014-make-member-lifecycle-own-oratorio-coordinator-designation.md)
* [ADR-0015: Compose Oratorio permission bundles in code](../../decisions/0015-compose-oratorio-permission-bundles-in-code.md)
* [ADR-0026: Use an isolated Member-information import with explicit Account linking](../../decisions/0026-use-isolated-member-information-import-with-explicit-account-linking.md)
* [ADR-0027: Model Member information as normalized components and immutable annual responses](../../decisions/0027-model-member-information-as-normalized-components-and-immutable-annual-responses.md)
* [ADR-0016: Store signed Oratoriano form attachments in PostgreSQL](../../decisions/0016-store-signed-oratoriano-form-attachments-in-postgresql.md)
* [ADR-0018: Standardize persistence auditing, soft deletion, and relationship enforcement](../../decisions/0018-standardize-persistence-auditing-soft-deletion-and-relationship-enforcement.md)
* [ADR-0021: Use Flyway repeatable migrations for code-owned system reference data](../../decisions/0021-use-flyway-repeatable-migrations-for-system-reference-data.md)

## Related videos

* None.
