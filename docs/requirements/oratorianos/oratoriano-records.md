# Requirement: Oratoriano Records

## Status

Accepted

## Context

GAM must register an arriving Oratoriano quickly, distinguish people reliably for attendance and form history, and expose useful attendance-frequency information without turning attendance into an explicit ranking.

Initial registration requires a name and nothing more. Optional profile data and additional forms may enrich the record later.

## Ubiquitous Language

- `ordinary Oratoriano profile`: The current non-sensitive name, optional birth date, and optional phone number outside immutable form versions.
- `human-equivalent name key`: The hidden comparison form used to enforce Oratoriano name uniqueness without changing the stored display spelling.
- `reserved name`: A canonical Oratoriano name that remains unavailable for new registration even when its record is soft-deleted.

## Functional requirements

### REQ-ORATORIANO-001: Identity and minimal registration

Every Oratoriano shall use a UUID v7 identifier.

Registration shall require exactly one valid `GamName` with `firstName` and `surname`. Birth date, phone number, CPF, RG, address, family information, health information, consent, and attachments shall not be initial-registration prerequisites.

Registration interfaces shall prominently advise the coordinator to ask for the person's complete name.

---

### REQ-ORATORIANO-002: Unique human-equivalent names

One human-equivalent full name shall identify at most one Oratoriano across both active and soft-deleted records.

The comparison key shall:

- flatten `firstName` and `surname` into one full-name sequence so field-boundary changes do not bypass uniqueness;
- apply the whitespace normalization accepted by `GamName`;
- compare case-insensitively;
- compare diacritic-insensitively; and
- preserve meaningful punctuation and letter differences.

Examples considered equal:

- `João Silva` and `JOAO SILVA`;
- `Ana Maria` / `Souza` and `Ana` / `Maria Souza`;
- whitespace variants of the same words.

Examples considered different:

- `Ana Luiza` and `Ana Luísa`;
- `Souza` and `Sousa`;
- `Ana-Luiza` and `Ana Luiza`;
- `D'Ávila` and `Davila`.

The original accepted spelling shall remain preserved for display. A concurrent uniqueness conflict shall be translated into a domain conflict.

---

### REQ-ORATORIANO-003: Ordinary profile

The ordinary profile shall contain:

- immutable UUID;
- current `GamName`;
- optional current `birthDate`; and
- optional current `GamPhoneNumber`.

CPF, RG, address, family, health, consent, and attachment data shall exist only inside additional-form versions.

Oratoriano active/inactive status and activation lifecycle shall not exist initially.

---

### REQ-ORATORIANO-004: Permissions

The permission catalog shall define:

| Permission | Capability |
| --- | --- |
| `ORATORIANO_GET` | Search and read the ordinary profile and derived attendance counts |
| `ORATORIANO_REGISTER` | Register an Oratoriano, including tracker quick registration |
| `ORATORIANO_MANAGE` | Correct the ordinary profile, soft-delete, and restore |

Baseline `COORD` and `ORATORIO_COORD` shall receive all three. Baseline `MEMBER` and `VISITOR` shall receive none.

Additional-form and combined-tracker permissions remain separate and shall not be implied by ordinary profile read access.

---

### REQ-ORATORIANO-005: Manual profile correction

An authorized user may update the current name, birth date, and phone number.

Every manual name correction shall require a normalized 1-to-2,000-character reason and one activity. Birth-date and phone changes shall be audited but shall not require a reason.

An update that would collide with any reserved human-equivalent name shall be rejected. A normalized no-op shall not mutate or emit an activity.

Activity metadata shall identify changed field names and target UUIDs without copying old or new personal values.

---

### REQ-ORATORIANO-006: Form-driven profile synchronization

Completing an additional form shall fill a missing current name, birth date, or phone value automatically.

For each synchronized field, the ordinary profile shall record provenance containing the source form UUID and its effective `signedOn` date.

A completed form may replace a current value when its `signedOn` is later than that value's recorded source date. If a differing profile value was recorded after the form was signed, completion shall require an explicit authorized choice rather than silently overwriting the newer value.

An approved form is authoritative for the chosen synchronization. A name collision shall block form completion. Revoking the source form shall not automatically roll back synchronized profile values.

---

### REQ-ORATORIANO-007: Derived attendance methods

The Oratoriano model shall expose these derived, nonnegative counts without persisted counter columns:

- `oratorioAttendances()`;
- `oratorioYearAttendances(year)`;
- `oratorioMonthAttendances(year, month)`;
- `oratorioDistinctMonthsAttendances()`;
- `oratorioYearDistinctMonthsAttendances(year)`; and
- `oratorioDistinctYearsAttendances()`.

Year and month boundaries shall use `America/Sao_Paulo`. Only active confirmed Oratoriano attendance shall contribute to the counts.

---

### REQ-ORATORIANO-008: Search and attendance ordering

`POST /oratorianos/search` shall require `ORATORIANO_GET` and return only non-deleted Oratorianos by default.

Ordinary search shall support UUID and human-equivalent name lookup and return the ordinary profile plus requested derived attendance counts.

Clients may optionally sort by `oratorioYearAttendances(year)`. Ordering shall append normalized name and UUID tie-breakers. The response shall not contain a rank, score, “most frequent” flag, threshold classification, or persisted ranking.

The current calendar year in `America/Sao_Paulo` shall be used when attendance-year sorting is requested without an explicit year.

---

### REQ-ORATORIANO-009: Soft deletion

An authorized user may soft-delete an erroneous Oratoriano with a required normalized reason.

Attendance—active or removed—shall not block deletion and shall not be removed by it. Completed, superseded, or revoked additional-form versions shall permanently block ordinary deletion. Draft forms shall not block deletion and shall be soft-deleted atomically using the Oratoriano deletion reason.

A deleted Oratoriano shall be excluded from ordinary search and new tracker selection. The reserved human-equivalent name shall remain unavailable for new registration.

Existing attendance shall remain visible in each related occurrence with the Oratoriano marked deleted and may be corrected under the occurrence's normal rules.

---

### REQ-ORATORIANO-010: Restoration

An authorized user may restore a soft-deleted Oratoriano using a required normalized reason.

Restoration shall reuse the same UUID and retained attendance history, return the record to ordinary search and tracker selection, and leave attendance and form history unchanged. Drafts automatically removed during Oratoriano deletion shall not be restored automatically.

Because deleted names remain reserved, ordinary registration shall direct an authorized user toward restoration rather than creating another record.

---

### REQ-ORATORIANO-011: Concurrency and activity

Registration, name correction, deletion, restoration, form synchronization, and quick attendance registration shall serialize conflicting identity decisions.

Every changed operation shall emit exactly one high-level activity in the same transaction. Failed operations and normalized no-ops shall emit none. Sensitive values shall not be copied into activity metadata.

---

### REQ-ORATORIANO-012: Route catalog and attendance views

The Oratoriano API shall expose:

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/oratorianos` | Register an Oratoriano |
| `GET` | `/oratorianos/{oratorianoId}` | Read the ordinary profile |
| `PUT` | `/oratorianos/{oratorianoId}` | Fully replace current name, birth date, and phone |
| `DELETE` | `/oratorianos/{oratorianoId}` | Soft-delete with a required reason |
| `PATCH` | `/oratorianos/{oratorianoId}/restore` | Restore with a required reason |
| `GET` | `/oratorianos/{oratorianoId}/attendances` | Read paged active confirmed attendance history |
| `GET` | `/oratorianos/{oratorianoId}/attendance-summary` | Read derived attendance counts |
| `POST` | `/oratorianos/search` | Structured ordinary search |

Attendance history shall default to newest occurrence first and return compact Oratorio date and status information without forms, health data, or other attendees. The ordinary endpoint shall apply to non-deleted Oratorianos. Attendance linked to a deleted Oratoriano remains visible through the related occurrence tracker.

`attendance-summary` shall always return values from `oratorioAttendances()`, `oratorioDistinctMonthsAttendances()`, and `oratorioDistinctYearsAttendances()`. Optional `year` shall additionally return `oratorioYearAttendances(year)` and `oratorioYearDistinctMonthsAttendances(year)`. Optional `month` requires `year` and additionally returns `oratorioMonthAttendances(year, month)`.

Structured search shall follow the project paging and filter contract. `attendanceYear` shall select the year used by `sort=oratorioYearAttendances,desc`; when omitted for that sort, the current `America/Sao_Paulo` year shall apply. The selected derived count may be returned, but no ranking representation shall be introduced.

## Acceptance scenarios

```gherkin
Scenario: Register with only a name
  Given the caller has ORATORIANO_REGISTER
  And no reserved human-equivalent name matches
  When the caller submits a valid firstName and surname
  Then one Oratoriano is created
  And birth date and phone are absent

Scenario: Accent variant conflicts
  Given João Silva is already registered
  When an authorized caller attempts to register JOAO SILVA
  Then registration is rejected as a name conflict

Scenario: Punctuation remains meaningful
  Given Ana-Luiza Silva is registered
  When an authorized caller registers Ana Luiza Silva
  Then the names do not conflict solely because of the hyphen difference

Scenario: Manual name correction requires a reason
  Given an Oratoriano has a unique current name
  When an authorized caller changes the name without a reason
  Then the update is rejected

Scenario: Attendance ordering is not a ranking
  Given several Oratorianos have different current-year attendance counts
  When an authorized caller sorts search by yearly attendance
  Then results are ordered by the derived count with deterministic ties
  And no rank, score, threshold, or ranking flag is returned

Scenario: Attendance does not block deletion
  Given an erroneous Oratoriano has preserved attendance but no completed, superseded, or revoked form
  When an authorized caller deletes the record with a valid reason
  Then the Oratoriano is soft-deleted
  And every attendance remains preserved

Scenario: Completed form blocks deletion
  Given an Oratoriano has a completed or historical immutable form
  When an authorized caller attempts ordinary deletion
  Then deletion is rejected

Scenario: Restore a deleted Oratoriano
  Given a soft-deleted Oratoriano's name remains reserved
  When an authorized caller restores the record with a valid reason
  Then the same UUID and attendance history return to ordinary visibility
  And automatically deleted drafts remain deleted

Scenario: Read a requested attendance summary
  Given an Oratoriano has attendance in multiple months and years
  When an authorized caller requests an attendance summary with year and month
  Then all-time, selected-year, and selected-month derived counts are returned
  And no persisted counter or rank is returned
```

## Open questions

* None.

## Out of scope

* Oratoriano active/inactive lifecycle.
* Homonyms or duplicate human-equivalent names.
* Automatic merging, record deduplication, or attendance/form history transfer.
* Explicit rankings, attendance scores, thresholds, rates, streaks, averages, or absence counts.
* CPF, RG, address, family, health, or consent fields in the ordinary profile.
* Hard deletion through ordinary application workflows.

## Related requirements

* [GamName](../common/gam-name.md)
* [GamPhoneNumber](../common/gam-phone-number.md)
* [Oratorio Attendance Tracker](../oratorio/oratorio-attendance-tracker.md)
* [Oratoriano Additional Forms](oratoriano-additional-forms.md)
* [RBAC Catalog](../rbac/rbac-catalog.md)

## Related ADRs

* [ADR-0017: Serialize Oratorio and Oratoriano mutations](../../decisions/0017-serialize-oratorio-and-oratoriano-mutations.md)

## Related videos

* None.
