# Requirement: Member Information Import and Account Linking

## Status

Accepted

## Context

GAM has one reviewed 2026 dataset containing current Member information and
annual survey answers for real people who do not yet have GAM Accounts. The
data must enter production once without becoming a development fixture,
creating synthetic Accounts, or allowing contact emails to establish identity.

After production launch, an imported Member may independently create an
Account through ordinary public registration. A Coordinator or SUDO operator
then needs an explicit workflow to link the two existing resources and project
the Member lifecycle into authorization Roles.

This specification introduces a narrow Account-less Member exception for the one-time
maintenance import and a permanent explicit linking workflow. It
supersedes the absolute Account-link requirement in `REQ-MEMBER-001` while
preserving immutable one-to-one linkage after a link exists.

## Ubiquitous Language

- `approved import dataset`: A private, Developer-reviewed input document with
  status `APPROVED`, explicit identities, normalized importable values, and no
  unresolved duplicate or review issue.
- `source reference`: A private preparation-only locator that identifies an
  input record for review or safe validation diagnostics and is not persisted
  as Member data.

## Functional requirements

### REQ-MEMBER-IMPORT-001: Narrow Account-less Member boundary

A production Member may have a null Account link only when that Member was
created by the approved Member Information maintenance import and retains a
reference to its Member Information Import Batch. The isolated development
fixture may create fictional Account-less seam records under its accepted
fixture ownership; that exception shall not exist in an ordinary or production
product workflow.

Ordinary direct registration through `POST /members` shall continue to require
an eligible existing Account. Membership Solicitation approval shall continue
to create a Member linked to the submitting Account. No other HTTP, fixture,
startup, or ordinary application workflow shall create an Account-less Member.

An imported Account-less Member may be `ACTIVE` or `INACTIVE`. Before linking,
that Member has no linked Account on which lifecycle-owned Roles can be
projected.

Rationale:

The exception supports existing real-world membership without turning
Account-less creation into an ordinary product capability.

Valid examples:

- The approved production import creates an active Member with `account = null`.
- Ordinary direct registration rejects a request without `accountId`.

Invalid examples:

- A Coordinator creates an Account-less Member through a new public endpoint.
- The development fixture copies the real approved dataset.

---

### REQ-MEMBER-IMPORT-002: Account-less lifecycle behavior

Activation and deactivation shall remain available for an imported
Account-less Member under the accepted route, permission, reason, lockout, and
status rules.

While no Account is linked:

- activation shall change `INACTIVE` to `ACTIVE` without assigning or removing
  an Account Role;
- deactivation shall change `ACTIVE` to `INACTIVE` without assigning or
  removing an Account Role;
- the high-level `MEMBER_ACTIVATED` or `MEMBER_DEACTIVATED` activity shall omit
  linked-Account metadata and shall record empty Role-change collections; and
- Coordinator and Oratorio Coordinator grant shall return `409 Conflict`
  without mutation or activity because responsibility cannot be projected to
  an Account.

Once linked, all existing Member lifecycle and responsibility operations shall
use the accepted Account Role projections.

Rationale:

Real-world Member participation may change before Account creation, but no
authorization Role exists without an Account.

---

### REQ-MEMBER-IMPORT-003: Explicit existing-Account link API

The system shall expose:

```text
PATCH /members/{memberId}/account/link
```

The route shall require authentication and `MEMBER_ACCOUNT_LINK`. Its request
shall be:

```json
{
  "accountId": "<account UUID>",
  "reason": "Confirmed existing Member identity"
}
```

The reason shall use `REQ-ACTIVITY-008` normalization and shall contain from 1
through 2,000 Unicode code points.

The baseline `COORD` Role shall receive `MEMBER_ACCOUNT_LINK`. `SUDO` shall
receive it through the complete system permission registry. Baseline `MEMBER`,
`VISITOR`, and `ORATORIO_COORD` shall not receive it.

`MEMBER_ACCOUNT_LINK` shall be sufficient without additionally requiring
`MEMBER_MANAGE`, `MEMBER_GET`, `MEMBER_GET_NON_ACTIVE`, or
`ACCOUNT_ROLE_MANAGE`.

The permission registry metadata shall be:

| Permission code | Label | Description |
| --- | --- | --- |
| `MEMBER_ACCOUNT_LINK` | `Link Member accounts` | `Allows linking an existing Account to an existing Account-less Member` |

Successful linking shall return `204 No Content` after the immutable link,
Role projection, Member aggregate-version advancement, and activity entry
commit together.

The link command shall not require `If-Match`. Its explicit eligibility and
serialization rules shall govern concurrency, while its committed version
advancement shall make any previously loaded Member update ETag stale.

Rationale:

Linking existing identity and membership is a deliberate Coordinator decision
separate from Account registration and role grants.

---

### REQ-MEMBER-IMPORT-004: Link eligibility and lifecycle Role projection

Linking shall require:

- an existing, non-soft-deleted Member whose Account link is null;
- an existing, active, non-soft-deleted Account not linked to any Member;
- no pending Membership Solicitation for the Account; and
- no active `MEMBER`, `VISITOR`, `COORD`, or `ORATORIO_COORD` assignment on the
  Account.

Rejected Membership Solicitation history shall not prevent linking. No name,
email, phone, birth-date, or other personal-data match shall be required.

On success, the link workflow shall preserve every active custom Role and any
active `SUDO` Role and shall project exactly:

| Member status | Roles added or retained | Lifecycle-owned Roles absent after linking |
| --- | --- | --- |
| `ACTIVE` | `MEMBER` | `VISITOR`, `COORD`, `ORATORIO_COORD` |
| `INACTIVE` | `VISITOR` | `MEMBER`, `COORD`, `ORATORIO_COORD` |

Linking shall never grant Coordinator or Oratorio Coordinator designation.
Those responsibilities require their separate post-link workflows.

The link route shall fail closed with `409 Conflict` when any lifecycle Role
precondition is inconsistent. It shall not repair, adopt, remove, or reinterpret
the conflicting state.

Rationale:

Explicit eligibility prevents Account reuse and avoids legitimizing a Role
projection that could not validly exist before the link.

---

### REQ-MEMBER-IMPORT-005: Immutable link, concurrency, and activity

After a successful link, the Member-to-Account relationship shall be immutable.
The first release shall expose no unlink, relink, transfer, or repair endpoint.

Link decisions for the Member and Account shall serialize with competing link,
registration, solicitation-decision, Member lifecycle, and responsibility
operations. Exactly one competing eligible link may succeed. A repeated link,
a race loser, or a newly ineligible target shall return `409 Conflict` without
partial mutation.

Successful linking shall emit exactly one `MEMBER_ACCOUNT_LINKED` activity
entry with:

- actor kind `ACCOUNT` and the authenticated Account performing the operation;
- resource target type `MEMBER` and the affected Member UUID;
- reason mode `REQUIRED`; and
- metadata containing exactly `accountId` and `roles`.

`roles` shall contain only the final lifecycle-owned Role code assigned by the
link: `MEMBER` or `VISITOR`. Metadata shall contain no name, email, phone, birth date, survey answer,
or other personal value. The workflow shall not emit `ACCOUNT_ROLE_ADDED`,
`MEMBER_ACTIVATED`, `COORDINATOR_GRANTED`, or another low-level or implied
activity.

Rationale:

The immutable one-to-one identity boundary and one high-level audit record
must remain correct under concurrent operator actions.

---

### REQ-MEMBER-IMPORT-006: Account registration remains independent

Public Account registration shall not search for, reveal, or automatically
link an existing Member by `contactEmail`, name, phone number, birth date, or
any combination of personal data.

Registering an Account with an email equal to a Member's `contactEmail` shall
create the same ordinary unprivileged, unlinked Account that would otherwise
be created. Member contact email shall not be interpreted as an Account login
claim.

If an existing Member mistakenly submits a Membership Solicitation, the
Coordinator-facing operational guidance shall instruct a human Coordinator to:

1. search for an existing Member using ordinary authorized Member search;
2. reject the pending solicitation with a review reason when the person is
   confirmed as an existing Member; and
3. link the Account through the dedicated link endpoint.

The system shall not implement automatic PII matching, automatic rejection,
or a combined approve-and-link flow. A pending solicitation shall continue to
block the link until a human decision resolves it.

The OpenAPI operation descriptions for Membership Solicitation review and
Member Account linking shall state this human-resolution sequence and shall
warn that approval would create a second Member instead of linking an existing
one. Coordinator-facing clients shall use that contract to orient human
reviewers; no automatic flow shall be inferred from the guidance.

Rationale:

Human confirmation avoids false identity matches while preserving the
immutable solicitation history and explicit intent of each workflow.

---

### REQ-MEMBER-IMPORT-007: Source-field ownership map

Preparation of the approved 2026 dataset shall apply this complete source-field
decision table:

| Source field | Durable owner and target | Classification |
| --- | --- | --- |
| `Carimbo de data/hora` | Annual Member Information Response `submittedAt` | Survey-cycle data |
| `Nome completo` | Member `GamName` split into `firstName` and `surname` | Permanent Member data |
| `Data de nascimento` | Member `birthDate` | Permanent Member data |
| `Quando entrou no GAM?` | Member `gamEntryDate` | Permanent Member data |
| `Em que cidade está morando?` | Member `residentialCity` | Permanent Member data |
| `Trabalha ou faz faculdade?` | Annual response `occupations` | Survey-cycle data |
| `Telefone para contato` | Member `phoneNumber` | Permanent Member data |
| `E-mail para contato` | Member `contactEmail` | Permanent Member data; not Account linkage |
| `Você tem alguma questão de saúde...` | Annual response `healthCondition` | Protected survey-cycle data |
| `Você tem alguma restrição alimentar...` | Member `dietaryRestriction` | Permanent ordinary Member data |
| `Você já participou da Jornada Missionária?` | Member experience `JORNADA_MISSIONARIA` | Permanent ordinary Member data |
| `Você já participou do Curso de Lideranças?` | Member experience `CURSO_DE_LIDERANCA` | Permanent ordinary Member data |
| `Você já participou da Páscoa Juvenil?` | Member experience `PASCOA_JUVENIL` | Permanent ordinary Member data |
| `Você já participou do Acampabosco?` | Member experience `ACAMPABOSCO` | Permanent ordinary Member data |
| `Já pensou em seguir vocação religiosa?` | Annual response `religiousVocationConsidered` | Protected survey-cycle data |
| `Você é batizado na Igreja Católica?` | Member sacrament `BATISMO` | Permanent ordinary Member data |
| `Você fez a Primeira Comunhão?` | Member sacrament `PRIMEIRA_COMUNHAO` | Permanent ordinary Member data |
| `Você fez Crisma?` | Member sacrament `CRISMA` | Permanent ordinary Member data |
| `Com qual frequência você costuma ir à missa?` | Annual response `massAttendanceFrequency` | Protected survey-cycle data |
| `Você tem algum impedimento aos sábados...` | Annual response `saturdayOratorioImpediment` | Protected survey-cycle data |
| `O que você gostaria de ver mais nas formações/reuniões...` | Annual response `formationAndMeetingInterests` | Protected survey-cycle data |
| `Assinale quais das opções... interesse e habilidade` | Member `contributionProfile` | Permanent ordinary Member data |
| `Você já pensou ou tem vontade de ser da coordenação...` | Annual response `coordinationInterest` | Protected survey-cycle data; no Role effect |
| `Você tem algum desabafo, sugestão ou informação importante...` | Annual response `additionalComments` | Protected survey-cycle data |
| `Sugestões de atividades/brincadeiras... ORATÓRIO` | Annual response `oratorioActivitySuggestions` | Protected survey-cycle data |
| `Sugestões de posts para o INSTAGRAM` | Annual response `instagramPostSuggestions` | Protected survey-cycle data |

No source field shall be silently omitted, duplicated into both ownership
boundaries, or used to create or link an Account.

---

### REQ-MEMBER-IMPORT-008: Approved dataset preparation rules

The approved dataset preparation shall:

- interpret source timestamps in `America/Sao_Paulo`;
- use reviewed `firstName` and `surname` splits;
- normalize every name to `REQ-GAM-NAME-008` canonical capitalization before
  domain validation;
- normalize phone numbers to canonical E.164;
- normalize contact emails through `GamEmail` without comparing them to
  Account emails;
- map source occupations to `WORK`, `UNIVERSITY`, `PREP_COURSE`, and `OTHER`;
- adapt the two separately supplied Member records as if they had submitted
  the same 2026 form, using `null` or `NOT_INFORMED` for unavailable answers
  and `submittedAt = null`;
- assign explicit stable UUID v7 identifiers to every Member, annual response,
  and batch before approval; and
- leave every imported Member Account-less and without Coordinator
  designation.

Duplicate candidates shall be identified by normalized canonical full name.
Within a resolved duplicate group, the record with the latest non-null
`submittedAt` shall be retained and every older record shall be excluded before
approval. The final dataset shall contain no duplicate normalized name and no
`SUPERSEDED` record or status.

The approved 2026 dataset shall contain 74 records: 76 CSV candidates plus two
separately supplied candidates, less four excluded older duplicate entries.
All 74 records shall have approved name splits and no unresolved review issue.
All 74 Members shall be `ACTIVE`, Account-less, and ordinary Members without
Coordinator or Oratorio Coordinator designation. Informal source function
labels shall not become Accounts, Roles, permissions, or responsibility
designations.

Rationale:

The one-time review owns ambiguous source adaptation. The production importer
must consume an already resolved dataset rather than repeat heuristic identity
or name decisions.

---

### REQ-MEMBER-IMPORT-009: Contribution preparation catalog

Contribution preparation shall map every useful source skill to the fixed
catalog in `REQ-MEMBER-INFO-006`.

In particular:

- football, volleyball, basketball, and handball shall become the separate
  fixed sports codes;
- `Repertórios de gincanas` shall map to `GINCANA_LEADERSHIP`;
- tererê shall map to `TERERE`;
- beach tennis shall be ignored because it is not useful in the current GAM
  contribution context; and
- punctuation-only text such as `.` shall be discarded.

The final approved 2026 dataset shall contain no custom contribution value.
This does not remove future ordinary support for `otherContributionAreas`.

Rationale:

The reviewed mapping prevents equivalent source labels from being imported as
duplicated custom abilities while preserving the future custom-text feature.

---

### REQ-MEMBER-IMPORT-010: Approved input-document contract and checksum

The maintenance importer shall accept only schema version
`gam-member-information-import/v1` with top-level `documentStatus = APPROVED`.
A Draft or unresolved document shall be rejected.

The document shall contain:

- batch UUID, survey cycle, and declared dataset checksum;
- a required top-level `preparation` object containing the preparation review
  summary and source headers;
- records whose `reviewStatus` is `APPROVED`, whose `reviewIssues` is empty,
  and whose importable payload contains one Member and one annual response; and
- explicit UUID v7 identifiers and complete accepted value catalogs.

For schema version `v1`, `preparation` shall be a non-null object containing
exactly `reviewSummary` and `sourceHeaders`:

```json
{
  "preparation": {
    "reviewSummary": {
      "csvCandidateCount": 76,
      "additionalCandidateCount": 2,
      "excludedDuplicateCount": 4,
      "approvedRecordCount": 74,
      "unresolvedIssueCount": 0
    },
    "sourceHeaders": [
      "<first original source header>",
      "<second original source header>"
    ]
  }
}
```

`reviewSummary` shall be a non-null object containing exactly the five
properties shown above. Every property shall be a JSON integer with the exact
`v1` value shown. The summary shall also satisfy:

```text
csvCandidateCount + additionalCandidateCount - excludedDuplicateCount
    = approvedRecordCount
    = records.length
```

`sourceHeaders` shall be a non-empty ordered JSON array of strings. Every entry
shall contain at least one non-Unicode-whitespace code point, and no two entries
shall be identical. The document shall preserve the original source-header
spelling and order. The importer shall validate this structure without
requiring the values to match a fixed public header catalog and shall not trim,
replace, reorder, or otherwise reinterpret them.

Unknown properties inside `preparation` or `reviewSummary` shall be rejected.
This closed-object rule shall not change the accepted unknown-property behavior
of other `v1` document objects.

Missing, null, malformed, or inconsistent preparation metadata shall reject
the complete operation under `REQ-MEMBER-IMPORT-012` before any database
mutation. The importer shall not infer, synthesize, or repair preparation
metadata from the records or from a private source document.

Preparation-only source references, review notes, source headers, candidate
counts, and duplicate-review metadata shall not be persisted as Member,
response, batch, or activity data.

The complete `preparation` object shall be excluded from the declared checksum.
Operational logs shall not serialize that object, expose its source-header
strings, or report its candidate-source, excluded-duplicate, or unresolved-issue
counts. This does not prohibit logging `records.length` as the operational
record count allowed by `REQ-MEMBER-IMPORT-012`.

The declared checksum shall be `sha256:` followed by the lowercase SHA-256 of
compact UTF-8 canonical JSON containing exactly:

- `schemaVersion`;
- `batch.id` and `batch.surveyCycle`; and
- the importable `{ member, annualResponse }` payloads sorted by Member UUID.

Canonical JSON shall sort every object key lexicographically, preserve array
order, and contain no insignificant whitespace. The operator-supplied
maintenance reason, file path, review metadata, and declared checksum itself
shall not participate in the digest.

The importer shall recompute and verify the checksum before any database
mutation.

Rationale:

The checksum binds the reviewed identities and importable values without
allowing a file path, review note, or later operator reason to change dataset
identity.

---

### REQ-MEMBER-IMPORT-011: Isolated maintenance execution

The import shall run as an explicit Spring Boot `maintenance` profile job with:

```text
maintenance.job=member-info-import
maintenance.action=validate | apply
maintenance.file=<explicit mounted private file path>
maintenance.actor-reference=<trusted Developer reference; apply only>
maintenance.reason=<normalized maintenance reason; apply only>
```

The normal production runtime, default migration path, development fixture,
integration-test default, and ordinary application startup shall not discover
or execute the private input.

The input shall be supplied only for the invocation and shall remain ignored
from version control. The application shall not copy the file or source CSV
into its database, package, logs, activity metadata, or committed resources.

`validate` shall run every input and database compatibility check used by
`apply`, create no batch or domain row, emit no activity, and require no
maintenance reason.

`apply` shall additionally require a trusted Developer actor reference and a
separately supplied maintenance reason satisfying `REQ-ACTIVITY-008`. The
input document shall not authorize its own execution; any preparation-only
`maintenanceReason` field shall be absent or null and shall not be persisted.

The process shall exit after reporting a safe success or failure outcome. It
shall not continue as the ordinary web application.

---

### REQ-MEMBER-IMPORT-012: Fail-closed validation and safe diagnostics

Before applying any row, the importer shall validate at least:

- supported schema and approved document status;
- checksum agreement;
- UUID v7 syntax and uniqueness across the complete document;
- one complete valid Member and one annual response per record;
- matching survey cycles and Member relationships;
- accepted `GamName`, date, age, city, phone, email, information-status,
  experience, sacrament, contribution, occupation, mass-frequency, and
  coordination-interest rules;
- no linked Account identifier;
- no duplicate canonical full name;
- no different existing Member with the same canonical full name;
- no unresolved review issue or non-approved record;
- Member-and-cycle uniqueness for annual responses; and
- every existing-database collision and idempotency condition in
  `REQ-MEMBER-IMPORT-014`.

Any failure shall reject the complete operation. The importer shall not skip an
invalid record, partially apply valid records, generate replacement UUIDs,
adopt a similar existing person, or resolve a duplicate heuristically.

A different existing Member with the same canonical full name shall produce a
safe review collision and fail validation. The importer shall not decide
whether the existing row is the same person or a distinct person. A human must
resolve the approved input or database state before another apply attempt.

Validation diagnostics may contain only record index, a validated non-PII
source reference, resource UUID, public field name, and a safe error code. For
schema version `v1`, a printable source reference shall match exactly
`CSV_ROW_<positive integer>` or `ADDITIONAL_RECORD_<positive integer>`;
otherwise diagnostics shall use only the record index. They shall not print a
name, email, phone number, date of birth, health value, free text, complete
record, or source line.

Operational logs may contain only batch UUID, declared checksum, record counts,
action, and success or failure outcome. They shall not contain the input path
or personal data.

---

### REQ-MEMBER-IMPORT-013: Atomic apply and persisted provenance

A successful new `apply` shall create in one transaction:

- one immutable Member Information Import Batch;
- every approved Account-less Member and all Member-owned information;
- one immutable annual response for each imported Member;
- the internal batch provenance references; and
- exactly one high-level maintenance activity.

The batch shall persist only after successful apply and shall contain exactly:

- batch UUID;
- survey cycle;
- verified dataset checksum;
- imported Member count;
- imported annual-response count;
- trusted execution instant; and
- normalized maintenance reason.

The batch shall have no status field because every persisted batch is
necessarily applied. Validation-only and failed apply runs shall create no
batch row. The batch shall expose no HTTP endpoint and shall not be ordinarily
updated, deleted, restored, or replaced.

Member and response rows may retain their internal batch UUID for provenance.
They shall not retain source path, source reference, CSV row, review note, or
raw source value. Row audit actor fields may be null because no Account actor
performed the Developer-maintenance operation.

Any validation, persistence, activity, or commit failure shall roll back the
batch and every imported row.

---

### REQ-MEMBER-IMPORT-014: Import idempotency and collision behavior

Import identity shall use only explicit approved UUIDs and the verified batch
checksum. Name, email, phone, birth date, or other similarity shall never adopt
an existing row.

The outcomes shall be:

| Existing state | Outcome |
| --- | --- |
| No batch or imported identifier exists | Apply the complete batch atomically. |
| Same batch UUID and checksum, with the complete expected Members, responses, provenance, and one activity already present | Successful no-op with no write or new activity. |
| Same batch UUID with another checksum | Fail the complete operation. |
| Same batch UUID and checksum but any expected imported state is missing, extra, or inconsistent | Fail as a partial or corrupted batch; do not repair. |
| New batch UUID collides with any Member, response, or other reserved identifier | Fail the complete operation. |

A no-op shall not change timestamps, versions, batch data, Member data,
responses, or activity history.

Rationale:

Stable explicit identity makes a deliberate rerun safe without converting the
importer into an undocumented reconciliation or person-matching mechanism.

---

### REQ-MEMBER-IMPORT-015: Import activity contract

Successful first-time apply shall emit exactly one
`MEMBER_INFORMATION_IMPORTED` activity entry with:

- actor kind `DEVELOPER` and a trusted non-secret Developer actor reference;
- resource target type `MEMBER_INFORMATION_IMPORT_BATCH` and the persisted
  batch UUID;
- reason mode `REQUIRED` and the same normalized reason persisted on the batch;
- `requestId = null`; and
- metadata containing exactly `surveyCycle`, `memberCount`, and
  `responseCount`.

The activity shall not contain the checksum, file path, source references,
names, contact values, annual answers, review notes, or per-record data.

The import shall not emit `MEMBER_REGISTERED`, Member profile-update,
Account-role, or annual-sensitive-read activities for its internal row
creation. Validation, failure, rollback, and complete idempotent no-op shall
emit no activity.

Rationale:

One minimized maintenance activity records Developer intent without turning
the append-only log into a second copy of the private dataset.

---

### REQ-MEMBER-IMPORT-016: Account-link error semantics

The Account-link route shall use these outcomes:

| Condition | Response |
| --- | --- |
| Malformed body, invalid Account UUID, or invalid reason | `400 Bad Request` |
| Unauthenticated request | `401 Unauthorized` |
| Authenticated caller lacks `MEMBER_ACCOUNT_LINK` | `403 Forbidden` |
| Required Member or Account is missing or soft-deleted | `404 Not Found` |
| Member or Account is already linked, Account is inactive, a pending solicitation exists, lifecycle-owned Roles are present, or a concurrent eligibility decision loses | `409 Conflict` |

Failed requests shall not change Member, Account, solicitation, Role,
aggregate-version, row-audit, or activity state. A rejected solicitation
history alone shall not be a conflict.

## Acceptance scenarios

```gherkin
Scenario: Validate the approved private dataset without mutation
  Given the maintenance profile receives the supported approved document
  And preparation.reviewSummary contains the exact consistent v1 counters
  And preparation.sourceHeaders is a non-empty ordered array of unique nonblank strings
  And every input and database compatibility rule passes
  When maintenance.action is validate
  Then the process reports validation success and exits
  And no batch, Member, annual response, row audit timestamp, or activity is created

Scenario: Reject invalid preparation metadata without mutation
  Given the supported document has missing, malformed, unknown, or inconsistent preparation metadata
  When the operator validates or applies the document
  Then the complete operation fails with a safe preparation-field diagnostic
  And no batch, Member, annual response, row audit timestamp, or activity is created
  And no source-header string is logged

Scenario: Apply the complete approved dataset once
  Given validation succeeds
  And the operator supplies a trusted Developer reference and valid reason
  When maintenance.action is apply
  Then one batch, all Members, all annual responses, and one import activity commit atomically
  And every imported Member is Account-less
  And no personal value is copied into logs or activity metadata

Scenario: Fail an invalid record without partial import
  Given one record has an unsupported contribution code
  When the operator validates or applies the document
  Then the complete operation fails with a safe record and field diagnostic
  And no imported row or activity commits

Scenario: Repeat a complete applied batch
  Given the same batch UUID, checksum, and complete imported projection already exist
  When apply is run again
  Then the operation is a successful no-op
  And no timestamp, version, or activity changes

Scenario: Link an active imported Member to a newly registered Account
  Given an active imported Member is Account-less
  And an active Account has no Member, pending solicitation, or lifecycle-owned Role
  And the caller has MEMBER_ACCOUNT_LINK
  When the caller links the Account with a valid reason
  Then the immutable link and MEMBER Role commit together
  And COORD, ORATORIO_COORD, and VISITOR remain absent
  And exactly one MEMBER_ACCOUNT_LINKED activity is created

Scenario: Contact email does not auto-link public registration
  Given an imported Member has a contact email
  When a person registers an Account with the same login email
  Then an ordinary unlinked Account is created
  And no Member link or lifecycle-owned Role is created

Scenario: Pending solicitation requires human resolution before link
  Given an existing Member's Account has a pending Membership Solicitation
  When a Coordinator attempts to link it
  Then the link returns 409 Conflict
  And the Coordinator must reject the solicitation before retrying the explicit link
```

## Diagrams

* [Member Information Ownership](../../diagrams/member-information/ownership.md)
* [Member Information Import and Account Linking](../../diagrams/member-information/import-and-account-linking.md)

## Open questions

* None.

## Out of scope

* A public or Coordinator-facing Account-less Member creation endpoint.
* Automatic Account creation, invitation, or login-email claim from imported
  contact information.
* Automatic PII matching or automatic solicitation resolution.
* Unlink, relink, link transfer, or historical linkage repair.
* Automatically granting Coordinator or Oratorio Coordinator designation.
* Modeling Treasurer or another coordination sub-role from an informal source
  function label.
* A general-purpose import framework, recurring synchronization, or importer
  for later survey cycles.
* Generating the approved dataset through a committed command.
* Partial import, row skipping, similarity adoption, or runtime duplicate
  winner selection.
* Import batch HTTP lookup, update, deletion, or restoration.
* Post-import annual-response correction.
* Data-retention, legal-erasure, or physical-deletion policy.
* Committing, packaging, logging, or using the real dataset as a development
  fixture.
* Test structure or production implementation details beyond the required
  isolated maintenance boundary.

## Related requirements

* [Member Information](member-information.md)
* [Member Records and Lifecycle](member-records-and-lifecycle.md)
* [Membership Solicitations](membership-solicitations.md)
* [Authentication and Account Registration](../authentication/authentication-and-registration.md)
* [Account Records](../accounts/account-records.md)
* [GamName Canonical Capitalization](../common/gam-name-capitalization.md)
* [RBAC Catalog](../rbac/rbac-catalog.md)
* [Activity Audit Log](../platform/activity-audit-log.md)
* [Development Fixture Policy and Dataset](../platform/development-fixture-policy-and-dataset.md)
* [Member Information Development Fixture Extension](../platform/member-information-development-fixture.md)
* [Database Reference Data and Enum Mirrors](../platform/database-reference-data-and-enum-mirrors.md)

## Related ADRs

* [ADR-0026: Use an isolated atomic Member-information import with explicit later Account linking](../../decisions/0026-use-isolated-member-information-import-with-explicit-account-linking.md)
* [ADR-0013: Make Member lifecycle own Coordinator designation](../../decisions/0013-make-member-lifecycle-own-coordinator-designation.md)
* [ADR-0014: Make Member lifecycle own Oratorio Coordinator designation](../../decisions/0014-make-member-lifecycle-own-oratorio-coordinator-designation.md)
* [ADR-0018: Standardize persistence auditing, soft deletion, and relationship enforcement](../../decisions/0018-standardize-persistence-auditing-soft-deletion-and-relationship-enforcement.md)
* [ADR-0019: Model activity history as typed append-only entries](../../decisions/0019-model-activity-history-as-typed-append-only-entries.md)

## Related videos

* None.
