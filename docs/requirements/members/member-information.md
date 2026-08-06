# Requirement: Member Information

## Status

Accepted

## Context

GAM needs a current Member profile that supports coordination work without
mixing permanent Member facts with answers that describe one annual survey
cycle. Coordinators also need explicit, auditable update operations and
separate read surfaces so ordinary profile access does not automatically
disclose annual information.

This specification expands the Member contract with current contact and participation
information, experiences, sacraments, and a contribution profile. It models an
annual Member Information Response as a separate immutable resource.

This specification supersedes the conflicting portions of
`REQ-MEMBER-002`, `REQ-MEMBER-003`, `REQ-MEMBER-009`, `REQ-MEMBER-010`, and the
Member-editing exclusion in the accepted Member Records and Lifecycle
specification. It will also expand the submission, response, and approval-copy
contracts in `REQ-MEMBER-SOL-002`, `REQ-MEMBER-SOL-006`, and
`REQ-MEMBER-SOL-009`.

## Ubiquitous Language

- `Member core profile`: The current Member-owned identity and contact fields
  returned by the ordinary Member lookup.
- `Member experience`: The Member's current recorded participation status for
  one named GAM experience.
- `Member sacrament`: The Member's current recorded reception status for one
  Catholic sacrament.
- `survey cycle`: The four-digit calendar year identifying the annual
  information collection, such as `2026`.
- `information status`: One of `YES`, `NO`, or `NOT_INFORMED`.

## Functional requirements

### REQ-MEMBER-INFO-001: Information ownership and aggregate boundaries

The Member shall remain the aggregate root for:

- Member identity, lifecycle status, and optional Account linkage;
- the Member core profile;
- `gamEntryDate`;
- dietary restriction;
- Member experiences;
- Member sacraments; and
- the Member contribution profile.

The following shall remain separate aggregate roots with their own identities
and lifecycles:

- Annual Member Information Response;
- Membership Solicitation;
- Presence;
- Account; and
- Member Information Import Batch.

An Annual Member Information Response shall reference exactly one Member. It
shall not become a mutable component of the current Member profile merely
because it was collected from the same person.

Rationale:

Current Member facts need one consistency boundary, while time-bound survey
answers require independent identity, uniqueness, authorization, and
immutability.

Valid examples:

- Updating a Member's contribution profile changes the Member aggregate and
  does not rewrite the 2026 annual response.
- Deactivating a Member preserves that Member's annual responses.

Invalid examples:

- Storing annual comments as current Member core fields.
- Treating a Membership Solicitation as an owned Member row.

---

### REQ-MEMBER-INFO-002: Required Member core information

Every newly created Member shall require:

- `GamName` as `firstName` and `surname`;
- `birthDate`;
- `gamEntryDate`;
- `residentialCity`;
- `GamPhoneNumber` as `phoneNumber`; and
- `GamEmail` as `contactEmail`.

`contactEmail` shall be Member contact data. It shall be independent from an
Account login email, shall not imply Account ownership or linkage, and shall
not be unique among Members.

`birthDate` shall not be in the future. A person shall be at least 17 years old
on the operation date when a Member is created or when `birthDate` is changed.
The seventeenth birthday shall be accepted.

`gamEntryDate` shall represent the Member's self-reported date of entry into
GAM. It shall not represent database creation or Account registration and
shall have only one temporal constraint: it shall not be in the future.

`residentialCity` shall contain from 1 through 100 Unicode code points after
leading and trailing Unicode whitespace is removed and each internal
whitespace sequence is collapsed to one ordinary space. The system shall
preserve the supplied spelling, capitalization, and diacritics. The field
shall contain a city or locality rather than a state, country, or street
address; it shall not use a closed city catalog.

The name, email, and phone number shall satisfy their accepted common primitive
requirements. The capitalization extension in `REQ-GAM-NAME-008` shall apply.

Rationale:

The Member record needs durable contact and participation facts without
conflating a contact channel with authentication identity.

Valid examples:

- A person is created on their seventeenth birthday.
- `gamEntryDate` equals today even when it precedes or follows other historical
  assumptions.
- Two Members use the same family contact email.

Invalid examples:

- A contact email automatically links the Member to an Account with the same
  login email.
- A future `gamEntryDate`.
- Rejecting `gamEntryDate` only because it precedes `birthDate`; that redundant
  rule is not part of this contract.

---

### REQ-MEMBER-INFO-003: Direct registration and solicitation field expansion

Ordinary direct Member registration shall continue to use `POST /members`,
require `MEMBER_MANAGE`, require an eligible existing Account, and apply the
accepted lifecycle behavior. Its request shall contain:

```json
{
  "accountId": "<account UUID>",
  "firstName": "Ana",
  "surname": "Silva",
  "birthDate": "2000-01-01",
  "gamEntryDate": "2023-01-01",
  "residentialCity": "Piracicaba",
  "phoneNumber": "+5519998877665",
  "contactEmail": "ana.contato@example.com",
  "reason": "Accepted as a GAM Member"
}
```

Membership Solicitation submission shall add `gamEntryDate`,
`residentialCity`, and `contactEmail` to its immutable submitted snapshot. The
solicitation response shall return those submitted fields.

Approval shall copy the submitted `GamName`, `birthDate`, `gamEntryDate`,
`residentialCity`, `phoneNumber`, and `contactEmail` into the new Member in the
same transaction as the accepted solicitation decision, lifecycle Role
projection, and high-level activity entry. A Coordinator shall not edit the
immutable submitted values during approval.

Rationale:

Both ordinary creation paths need the complete required Member profile and
must preserve the solicitation's historical submission boundary.

Valid examples:

- A solicitation approval copies its submitted contact email even when it
  differs from the Account login email.

Invalid examples:

- Direct registration omits `gamEntryDate`.
- Approval substitutes the Account login email for submitted `contactEmail`.

---

### REQ-MEMBER-INFO-004: Information-status fields and creation defaults

`information status` shall contain exactly `YES`, `NO`, and `NOT_INFORMED`.

Every newly created Member shall begin with:

- dietary restriction `{ "status": "NOT_INFORMED", "details": null }`;
- every Member experience set to `NOT_INFORMED`;
- every Member sacrament set to `NOT_INFORMED`;
- an empty fixed contribution-area collection; and
- an empty custom contribution-area collection.

The defaults shall apply to direct registration, Membership Solicitation
approval, and the one-time import unless the approved import input explicitly
contains another valid value.

Dietary restriction shall be ordinary Member data and shall not be classified
as sensitive by this feature. `YES` shall require normalized `details`
containing from 1 through 2,000 Unicode code points. `NO` and `NOT_INFORMED`
shall require `details = null`.

Rationale:

Explicit `NOT_INFORMED` distinguishes an unanswered question from a negative
answer while keeping a valid complete Member representation.

---

### REQ-MEMBER-INFO-005: Member experience and sacrament catalogs

The Member experience type catalog shall contain exactly:

- `JORNADA_MISSIONARIA`;
- `CURSO_DE_LIDERANCA`;
- `PASCOA_JUVENIL`; and
- `ACAMPABOSCO`.

The Member sacrament type catalog shall contain exactly:

- `BATISMO`;
- `PRIMEIRA_COMUNHAO`; and
- `CRISMA`.

Every Member shall have exactly one current information status for each type.
Sacrament information shall be ordinary Member data. A Coordinator-authorized
update may correct any current status, including changing `YES` back to `NO` or
`NOT_INFORMED`.

Unknown, missing, duplicated, or aliased catalog values shall be invalid.
`MISSIONARY_JOURNEY`, `CURSO_DE_LIDERANCAS`, and other translations or spelling
variants shall not be accepted transport values.

Rationale:

Closed named catalogs prevent survey labels and event proper names from
drifting into competing transport values.

---

### REQ-MEMBER-INFO-006: Member contribution profile

The fixed contribution-area catalog shall contain exactly:

- `GAME_REFEREE`;
- `CRAFTS`;
- `MUSIC`;
- `PRAYER_LEADERSHIP`;
- `BOA_TARDE_STORYTELLING`;
- `DANCE`;
- `BALLOON_SCULPTURE`;
- `FOOTBALL`;
- `VOLLEYBALL`;
- `BASKETBALL`;
- `HANDBALL`;
- `PHOTOGRAPHY_AND_VIDEO`;
- `PUBLIC_READING`;
- `FACE_PAINTING`;
- `FIRST_AID`;
- `GINCANA_LEADERSHIP`;
- `TECHNOLOGY`; and
- `TERERE`.

The fixed catalog's canonical Portuguese labels and duplicate-detection aliases
shall be:

| Code | Canonical label | Additional aliases |
| --- | --- | --- |
| `GAME_REFEREE` | `Apitar jogo` | `Arbitragem de jogos` |
| `CRAFTS` | `Artesanato` | None |
| `MUSIC` | `Cantar ou tocar instrumento` | `Cantar/tocar instrumento`, `Música` |
| `PRAYER_LEADERSHIP` | `Conduzir momentos de oração` | `Condução de oração` |
| `BOA_TARDE_STORYTELLING` | `Contar histórias no Boa Tarde` | `Contar histórias (boa tarde)` |
| `DANCE` | `Dança` | None |
| `BALLOON_SCULPTURE` | `Escultura de bexiga` | `Escultura de balões` |
| `FOOTBALL` | `Futebol` | None |
| `VOLLEYBALL` | `Vôlei` | `Voleibol` |
| `BASKETBALL` | `Basquete` | `Basketball` |
| `HANDBALL` | `Handebol` | `Handball` |
| `PHOTOGRAPHY_AND_VIDEO` | `Fotografia e vídeo` | `Fotografia/vídeos (marketing)` |
| `PUBLIC_READING` | `Leitura em público` | None |
| `FACE_PAINTING` | `Pintura facial` | None |
| `FIRST_AID` | `Primeiros socorros` | None |
| `GINCANA_LEADERSHIP` | `Puxar gincana` | `Condução de gincanas`, `Repertórios de gincanas` |
| `TECHNOLOGY` | `Tecnologia` | None |
| `TERERE` | `Tererê` | `Terere` |

There shall be no umbrella `SPORTS` value. A Member may hold any combination
of the individual fixed sports.

`otherContributionAreas` shall support future Coordinator-authored values. It
shall contain at most 10 values. Each value shall contain from 1 through 100
Unicode code points after leading and trailing Unicode whitespace is removed
and internal whitespace is collapsed to one ordinary space.

The system shall reject:

- duplicate fixed values;
- case-insensitive duplicate custom values;
- a custom value that case-insensitively matches a fixed label or documented
  alias; and
- unknown fixed values.

Both collections shall have set semantics. Response `contributionAreas` shall
use the fixed catalog declaration order above. Response
`otherContributionAreas` shall use ascending case-insensitive order of their
normalized Unicode values. Reordering an otherwise equal request shall be a
no-op.

Rationale:

A reviewed fixed catalog provides reliable coordination filters while bounded
custom text preserves room for future abilities that have not earned a catalog
code.

---

### REQ-MEMBER-INFO-007: Ordinary Member response

`GET /members/{memberId}`, Member search results, and successful direct
registration shall use this ordinary Member shape:

```json
{
  "id": "<member UUID>",
  "firstName": "Ana",
  "surname": "Silva",
  "birthDate": "2000-06-29",
  "gamEntryDate": "2023-01-01",
  "residentialCity": "Piracicaba",
  "phoneNumber": "+5519998877665",
  "contactEmail": "ana.contato@example.com",
  "dietaryRestriction": {
    "status": "NOT_INFORMED",
    "details": null
  },
  "status": "ACTIVE",
  "account": null
}
```

When linked, `account` shall use the accepted Account summary. When no Account
has yet been linked under the import exception, `account` shall be `null`.

The ordinary response shall not include Member experiences, sacraments,
contribution profile, annual answers, import provenance, Account Roles,
credentials, sessions, soft-delete fields, or row audit metadata.

Rationale:

Ordinary lookup needs the complete current core profile without turning every
Member read into an annual-information or capability-profile disclosure.

---

### REQ-MEMBER-INFO-008: Separate current-information lookup endpoints

The system shall expose:

| Method | Route | Response responsibility |
| --- | --- | --- |
| `GET` | `/members/{memberId}/experiences-and-sacraments` | Current experience and sacrament status maps. |
| `GET` | `/members/{memberId}/contribution-profile` | Current fixed and custom contribution areas. |

The experiences-and-sacraments response shall be:

```json
{
  "experiences": {
    "JORNADA_MISSIONARIA": "NOT_INFORMED",
    "CURSO_DE_LIDERANCA": "NOT_INFORMED",
    "PASCOA_JUVENIL": "NOT_INFORMED",
    "ACAMPABOSCO": "NOT_INFORMED"
  },
  "sacraments": {
    "BATISMO": "NOT_INFORMED",
    "PRIMEIRA_COMUNHAO": "NOT_INFORMED",
    "CRISMA": "NOT_INFORMED"
  }
}
```

The contribution response shall be:

```json
{
  "contributionProfile": {
    "contributionAreas": [],
    "otherContributionAreas": []
  }
}
```

Both routes shall use the ordinary Member visibility contract:

- the linked Account may read its own Member regardless of active or inactive
  status;
- another caller needs `MEMBER_GET` for an active Member;
- another caller needs both `MEMBER_GET` and `MEMBER_GET_NON_ACTIVE` for an
  inactive Member; and
- a missing, soft-deleted, or status-hidden Member returns `404 Not Found`.

These ordinary current-information reads shall not require a dedicated
sensitive-read permission and shall not emit activity entries.

---

### REQ-MEMBER-INFO-009: Coordinator-managed Member update routes

The system shall expose these full-replacement operations:

| Method | Route | Request-owned fields |
| --- | --- | --- |
| `PUT` | `/members/{memberId}` | `firstName`, `surname`, `birthDate`, `residentialCity`, `phoneNumber`, `contactEmail`, `reason` |
| `PUT` | `/members/{memberId}/gam-entry-date` | `gamEntryDate`, `reason` |
| `PUT` | `/members/{memberId}/dietary-restriction` | `status`, `details`, `reason` |
| `PUT` | `/members/{memberId}/experiences` | complete `experiences` map, `reason` |
| `PUT` | `/members/{memberId}/sacraments` | complete `sacraments` map, `reason` |
| `PUT` | `/members/{memberId}/contribution-profile` | `contributionAreas`, `otherContributionAreas`, `reason` |

Every route shall require authentication and `MEMBER_MANAGE`. That permission
shall be sufficient without additionally requiring `MEMBER_GET` or
`MEMBER_GET_NON_ACTIVE`, and an authorized caller may update an active or
inactive non-soft-deleted Member. Every request
shall require a reason normalized under `REQ-ACTIVITY-008` and containing from
1 through 2,000 Unicode code points.

`PUT /members/{memberId}` shall update only the six listed core fields. It
shall not accept or change `gamEntryDate`, dietary restriction, experience,
sacrament, contribution, lifecycle status, Account linkage, annual answers, or
row audit fields.

The experience request shall contain exactly all four accepted experience
keys. The sacrament request shall contain exactly all three accepted sacrament
keys. Missing or unknown keys shall return `400 Bad Request`.

The dedicated request shapes shall be:

```json
{
  "gamEntryDate": "2023-01-01",
  "reason": "Corrected from the reviewed Member record"
}
```

```json
{
  "status": "YES",
  "details": "Lactose",
  "reason": "Dietary information confirmed with the Member"
}
```

```json
{
  "experiences": {
    "JORNADA_MISSIONARIA": "YES",
    "CURSO_DE_LIDERANCA": "NO",
    "PASCOA_JUVENIL": "NOT_INFORMED",
    "ACAMPABOSCO": "YES"
  },
  "reason": "Participation history reviewed"
}
```

```json
{
  "sacraments": {
    "BATISMO": "YES",
    "PRIMEIRA_COMUNHAO": "YES",
    "CRISMA": "NOT_INFORMED"
  },
  "reason": "Sacrament information reviewed"
}
```

```json
{
  "contributionAreas": ["FOOTBALL", "TERERE"],
  "otherContributionAreas": ["Culinária para eventos"],
  "reason": "Contribution profile reviewed"
}
```

Every valid state-changing update shall return `204 No Content`. A request
whose normalized values already equal the current owned state shall also
return `204 No Content`, but shall perform no write, advance no row timestamp or
aggregate version, and emit no activity entry.

Rationale:

Dedicated replacement boundaries prevent a general profile request from
silently clearing independently managed Member information.

---

### REQ-MEMBER-INFO-010: Member-update activity contract

Each committed state-changing update shall emit exactly one activity entry:

| Route responsibility | Activity action |
| --- | --- |
| Core profile | `MEMBER_PROFILE_UPDATED` |
| GAM entry date | `MEMBER_GAM_ENTRY_DATE_UPDATED` |
| Dietary restriction | `MEMBER_DIETARY_RESTRICTION_UPDATED` |
| Experiences | `MEMBER_EXPERIENCES_UPDATED` |
| Sacraments | `MEMBER_SACRAMENTS_UPDATED` |
| Contribution profile | `MEMBER_CONTRIBUTION_PROFILE_UPDATED` |

Each action shall use:

- actor kind `ACCOUNT` and the authenticated Account;
- resource target type `MEMBER` and the affected Member UUID;
- reason mode `REQUIRED`; and
- metadata containing exactly `changedFields`.

`changedFields` shall be a non-empty bounded list containing only the public
field names or fixed type codes changed by that operation. It shall not contain
old values, new values, names, contact data, dietary details, or custom
contribution text.

The Member mutation and activity entry shall commit together. A failed,
rejected, stale, or no-op update shall emit no activity entry. Internal owned
row changes shall not emit lower-level activities.

---

### REQ-MEMBER-INFO-011: Member aggregate ETag and lost-update protection

The ordinary Member lookup, experiences-and-sacraments lookup, and contribution
profile lookup shall return the same current strong opaque `ETag` for the
Member aggregate. The value shall derive from the Member's persistence version
and shall not hash or encode names, contact information, response content, or
other personal data.

Every `PUT` route in `REQ-MEMBER-INFO-009` shall require exactly one strong
`If-Match` value previously returned for that Member. A missing precondition
shall return `428 Precondition Required`. A well-formed but non-current value
shall return `412 Precondition Failed`. A malformed, weak, wildcard, or
multi-value precondition shall return `400 Bad Request`.

The precondition shall be checked before no-op evaluation. Consequently, a
stale request shall return `412 Precondition Failed` even when its submitted
values happen to equal the current values.

Every committed state-changing operation on the Member aggregate shall advance
the shared version and the Member aggregate's `updatedAt`. A valid no-op shall
leave both unchanged. Every successful `204 No Content` Member update shall
return the resulting `ETag` header.

Member writes shall serialize their aggregate decision. Two requests carrying
the same current ETag shall not both commit: after one advances the version,
the other shall return `412 Precondition Failed`. Domain uniqueness and
eligibility conflicts that are not stale-representation failures shall remain
`409 Conflict`.

Rationale:

Database transaction serialization prevents overlapping writes from corrupting
storage, while the client precondition prevents a full replacement from
overwriting changes made after the client's screen was loaded.

---

### REQ-MEMBER-INFO-012: Expanded Member search catalog

`POST /members/search` shall retain the accepted shared search grammar and
status visibility and shall expose exactly these public fields:

| Public field | Allowed comparison methods | Product meaning |
| --- | --- | --- |
| `id` | `EQUALS`, `IN` | Member UUID. |
| `name` | `LIKE` | Current canonical full-name rendering. |
| `birthDate` | `EQUALS`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | Current birth date. |
| `gamEntryDate` | `EQUALS`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | Current self-reported GAM entry date. |
| `residentialCity` | `EQUALS`, `LIKE` | Current normalized city or locality. |
| `phoneNumber` | `EQUALS`, `LIKE` | Current canonical Member phone number. |
| `contactEmail` | `EQUALS`, `LIKE` | Current Member contact email. |
| `status` | `EQUALS`, `IN` | Current Member status. |
| `accountId` | `EQUALS` | Currently linked Account UUID. |
| `accountEmail` | `EQUALS`, `LIKE` | Current linked active Account login email. |
| `hasLinkedAccount` | `EQUALS` | Whether the Member currently has an Account link. |
| `role` | `EQUALS`, `IN` | Current Role on the linked active Account. |
| `jornadaMissionaria` | `EQUALS`, `IN` | Current `JORNADA_MISSIONARIA` information status. |
| `cursoDeLideranca` | `EQUALS`, `IN` | Current `CURSO_DE_LIDERANCA` information status. |
| `pascoaJuvenil` | `EQUALS`, `IN` | Current `PASCOA_JUVENIL` information status. |
| `acampabosco` | `EQUALS`, `IN` | Current `ACAMPABOSCO` information status. |
| `batismo` | `EQUALS`, `IN` | Current `BATISMO` information status. |
| `primeiraComunhao` | `EQUALS`, `IN` | Current `PRIMEIRA_COMUNHAO` information status. |
| `crisma` | `EQUALS`, `IN` | Current `CRISMA` information status. |
| `contributionArea` | `EQUALS`, `IN` | Current fixed contribution-area code. |
| `createdAt` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | Member creation instant. |
| `updatedAt` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` | Latest non-deletion Member update instant. |

The former ambiguous `email` field shall be removed without a compatibility
alias. `contactEmail` and `accountEmail` shall retain the accepted email
equality and partial-search normalization rules.

An Account-less Member may match `contactEmail` and `hasLinkedAccount = false`,
but shall not match `accountId`, `accountEmail`, or `role`.

`contributionArea IN` shall match when the Member has any supplied fixed code.
Separate `contributionArea EQUALS` filters shall compose with the shared `AND`
rule and therefore require every separately supplied code.

The search catalog shall not expose:

- dietary-restriction status or details;
- custom contribution text;
- any Annual Member Information Response answer;
- import provenance; or
- internal persistence paths.

Rationale:

Coordinators need structured current-profile discovery, but search must not
become a general survey-answer or free-text disclosure mechanism.

---

### REQ-MEMBER-INFO-013: Annual response identity and immutability

Each Annual Member Information Response shall have a UUID v7 identifier and
shall reference exactly one existing Member and one survey cycle.

There shall be at most one response for a Member and survey-cycle pair. The
invariant shall be enforced under concurrency.

An imported response shall be immutable after it is created. It shall have no
status, draft state, superseded state, edit endpoint, replacement endpoint,
delete endpoint, restore endpoint, or ordinary correction workflow.

The one-time maintenance import shall be the only Annual Member Information
Response creation workflow in the first release.

`submittedAt` shall be the source submission instant when known and may be
`null` only when the approved import source did not provide one. The source
2026 timestamps shall be interpreted with the `America/Sao_Paulo` offset before
being stored as instants.

Rationale:

One immutable response preserves what was supplied for a particular cycle and
prevents current Member updates from rewriting historical answers.

---

### REQ-MEMBER-INFO-014: Annual response contract and answer catalogs

The annual response shall use this shape:

```json
{
  "id": "<response UUID>",
  "surveyCycle": 2026,
  "submittedAt": "2026-02-01T22:28:11-03:00",
  "occupations": {
    "values": ["UNIVERSITY"],
    "details": null
  },
  "healthCondition": {
    "status": "NO",
    "details": null
  },
  "religiousVocationConsidered": "NOT_INFORMED",
  "massAttendanceFrequency": "WEEKLY",
  "saturdayOratorioImpediment": {
    "status": "NO",
    "details": null
  },
  "formationAndMeetingInterests": null,
  "coordinationInterest": "MAYBE",
  "additionalComments": null,
  "oratorioActivitySuggestions": null,
  "instagramPostSuggestions": null
}
```

The occupation catalog shall contain exactly `WORK`, `UNIVERSITY`,
`PREP_COURSE`, and `OTHER`. A response may contain zero through four distinct
values; an empty list means that occupation was not informed. When `OTHER` is
present, `occupations.details` shall contain from 1 through 2,000 normalized
Unicode code points. When `OTHER` is absent, `occupations.details` shall be
`null`.

Occupation values shall have set semantics and shall be returned in the catalog
order declared above.

`religiousVocationConsidered` shall use information status.

The mass-attendance-frequency catalog shall contain exactly:

- `WEEKLY`;
- `THREE_TIMES_PER_MONTH`;
- `TWICE_PER_MONTH`;
- `MONTHLY`; and
- `NOT_INFORMED`.

The coordination-interest catalog shall contain exactly `YES`, `NO`, `MAYBE`,
and `NOT_INFORMED`. It shall not grant, revoke, suggest, or otherwise change
Coordinator designation.

`healthCondition` and `saturdayOratorioImpediment` shall use information
status. `YES` shall require non-null details; `NO` and `NOT_INFORMED` shall
require null details.

Every annual free-text or details field shall contain at most 2,000 Unicode
code points after leading and trailing Unicode whitespace is removed and
equivalent Unicode representations are normalized. An optional value that is
blank after normalization shall become `null`. Internal text shall otherwise
be preserved.

The complete Annual Member Information Response shall be treated as protected
survey information. This classification does not change the agreed ordinary
classification of Member-owned dietary restriction or sacraments.

---

### REQ-MEMBER-INFO-015: Annual response lookup, authorization, and audit

The system shall expose:

```text
GET /members/{memberId}/annual-information/{surveyCycle}
```

The route shall require authentication and `MEMBER_INFORMATION_GET`. The
baseline `COORD` Role shall receive that permission, and `SUDO` shall receive
it through the complete system permission registry. `MEMBER` and `VISITOR`
shall not receive it.

The permission registry metadata shall be:

| Permission code | Label | Description |
| --- | --- | --- |
| `MEMBER_INFORMATION_GET` | `View annual Member information` | `Allows viewing protected annual Member information` |

The first release shall not provide linked-Account self-view. The route shall
also apply Member status visibility:

- an active target requires `MEMBER_INFORMATION_GET`;
- an inactive target requires both `MEMBER_INFORMATION_GET` and
  `MEMBER_GET_NON_ACTIVE`; and
- a missing, soft-deleted, status-hidden, or no-response target returns
  `404 Not Found`.

Before disclosing the response, the system shall persist exactly one
`MEMBER_ANNUAL_INFORMATION_READ` activity entry with:

- actor kind `ACCOUNT` and the authenticated Account;
- resource target type `MEMBER_ANNUAL_INFORMATION_RESPONSE` and the response
  UUID;
- reason mode `NONE`; and
- metadata containing exactly `memberId` and `surveyCycle`.

The activity shall contain no answer values, personal text, contact data, or
submission timestamp. If activity persistence fails, the protected response
shall not be disclosed. Failed or hidden reads shall emit no activity entry.

There shall be no annual-response collection or search endpoint in the first
release.

---

### REQ-MEMBER-INFO-016: Error and atomicity contract

Member-information routes shall use these outcomes:

| Condition | Response |
| --- | --- |
| Malformed fields, invalid catalog values, invalid conditional details, invalid reason, invalid ETag syntax, future dates, or under-17 core update | `400 Bad Request` |
| Missing `If-Match` on a Member update | `428 Precondition Required` |
| Current Member version does not match `If-Match` | `412 Precondition Failed` |
| Unauthenticated protected request | `401 Unauthorized` |
| Authenticated caller lacks a required permission | `403 Forbidden` |
| Member or annual response is missing, soft-deleted, or status-hidden | `404 Not Found` |
| A non-version domain uniqueness or concurrent eligibility decision loses | `409 Conflict` |

Validation, version checks, Member mutation, aggregate-version advancement,
and activity persistence shall follow one atomic operation. Failed requests
shall not partially change Member-owned rows, versions, timestamps, or activity
history.

## Acceptance scenarios

```gherkin
Scenario: Ordinary Member lookup keeps information surfaces separate
  Given an active Account-less Member has experiences, sacraments, contribution areas, and a 2026 annual response
  And the caller may view the active Member
  When the caller requests GET /members/{memberId}
  Then the response contains the ordinary core profile and account is null
  And it contains no experience, sacrament, contribution, annual, or import data

Scenario: Update a contribution profile with the current ETag
  Given a Coordinator has MEMBER_MANAGE
  And the Member aggregate ETag is "member-41"
  When the Coordinator replaces the contribution profile with If-Match "member-41" and a valid reason
  Then the response is 204 No Content with a new ETag
  And exactly one MEMBER_CONTRIBUTION_PROFILE_UPDATED activity exists
  And its metadata contains changed field names but no contribution text

Scenario: Reject a stale full replacement
  Given two Coordinators loaded the same Member ETag
  And the first Coordinator committed a Member change
  When the second Coordinator submits a full replacement with the old ETag
  Then the response is 412 Precondition Failed
  And no Member or activity change is committed for the stale request

Scenario: No-op update preserves version and activity history
  Given a Coordinator submits the current core values with the current ETag and a valid reason
  When the update is evaluated
  Then the response is 204 No Content with the unchanged ETag
  And no row timestamp, aggregate version, or activity entry changes

Scenario: Read protected annual information
  Given an active Member has one immutable response for survey cycle 2026
  And the caller has MEMBER_INFORMATION_GET
  When the caller requests the response
  Then one MEMBER_ANNUAL_INFORMATION_READ activity commits before disclosure
  And the response contains the full annual answer contract
  And the activity contains no answer or contact value

Scenario: Linked Member has no automatic annual self-view
  Given the authenticated Account is linked to the target Member
  But it lacks MEMBER_INFORMATION_GET
  When it requests that Member's annual response
  Then the response is 403 Forbidden
  And no sensitive-read activity is created

Scenario: Search distinguishes contact and Account emails
  Given an Account-less Member has a contact email
  When an authorized caller searches by contactEmail
  Then the Member may match
  And the same Member cannot match accountEmail or accountId
```

## Diagrams

* [Member Information Ownership](../../diagrams/member-information/ownership.md)
* [Member Information Update Concurrency](../../diagrams/member-information/update-concurrency.md)

## Open questions

* None.

## Out of scope

* Member-facing annual form submission, draft, or editing workflows.
* Ordinary annual-response correction, replacement, supersession, deletion, or
  restoration.
* Annual-response collection or search endpoints.
* Annual-response self-view in the first release.
* Searching annual answers, dietary restriction, or custom contribution text.
* Automatic Coordinator designation based on coordination interest.
* Retention, legal erasure, or redaction policy for annual responses.
* Member deletion, restoration, or merge behavior.
* ETag-based conditional GET or general HTTP response caching.
* Test structure or production implementation strategy.

## Related requirements

* [Member Records and Lifecycle](member-records-and-lifecycle.md)
* [Membership Solicitations](membership-solicitations.md)
* [Member Information Import and Account Linking](member-information-import-and-account-linking.md)
* [GamName](../common/gam-name.md)
* [GamName Canonical Capitalization](../common/gam-name-capitalization.md)
* [GamEmail](../common/gam-email.md)
* [GamPhoneNumber](../common/gam-phone-number.md)
* [RBAC Catalog](../rbac/rbac-catalog.md)
* [Activity Audit Log](../platform/activity-audit-log.md)
* [Search and Filter Framework](../platform/search-and-filter-framework.md)
* [Database Reference Data and Enum Mirrors](../platform/database-reference-data-and-enum-mirrors.md)

## Related ADRs

* [ADR-0027: Model Member information as normalized aggregate components and immutable annual responses](../../decisions/0027-model-member-information-as-normalized-components-and-immutable-annual-responses.md)
* [ADR-0018: Standardize persistence auditing, soft deletion, and relationship enforcement](../../decisions/0018-standardize-persistence-auditing-soft-deletion-and-relationship-enforcement.md)
* [ADR-0019: Model activity history as typed append-only entries](../../decisions/0019-model-activity-history-as-typed-append-only-entries.md)
* [ADR-0020: Use shared search grammar with resource-specific public fields](../../decisions/0020-use-shared-search-grammar-with-resource-specific-public-fields.md)

## Related videos

* None.
