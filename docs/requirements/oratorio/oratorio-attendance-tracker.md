# Requirement: Oratorio Attendance Tracker

## Status

Accepted

## Context

Coordinators currently mark Member and Oratoriano attendance in separate spreadsheet sheets as people arrive. The Oratorio module needs one reliable operational tracker while preserving the distinct meanings and histories of Member Presence and Oratoriano attendance.

The common Presence model remains the attendance resource for Members. Oratoriano attendance is a separate resource because an Oratoriano is not necessarily a Member.

## Ubiquitous Language

- `Oratoriano attendance`: The persisted, auditable fact that one Oratoriano attended one Oratorio occurrence.
- `combined tracker`: The Oratorio operational view containing separate Member and Oratoriano sections.
- `early attendance window`: The interval beginning 30 minutes before the fixed Oratorio start.

## Functional requirements

### REQ-ORATORIO-ATT-001: Separate attendance resources

Member attendance in an Oratorio shall use the common Presence resource and retain its Event/Member identity and history.

Oratoriano attendance shall use a distinct resource with:

- its own UUID v7;
- immutable Oratorio and Oratoriano relationships; and
- a registration timestamp.

It shall not contain an observation field. At most one active Oratoriano attendance shall exist for one Oratorio and Oratoriano pair. Removed records shall remain preserved and shall not reserve the active pair.

Only confirmed attendance shall be stored. The module shall not persist explicit absent or unmarked states.

---

### REQ-ORATORIO-ATT-002: Tracker read and mutation permissions

Reading the combined tracker shall require `ORATORIO_ATTENDANCE_GET`. Checking or unchecking either a Member or Oratoriano shall require `ORATORIO_ATTENDANCE_MANAGE`.

Baseline `COORD` and `ORATORIO_COORD` shall receive both permissions. Baseline `MEMBER` and `VISITOR` shall receive neither.

The common Member Presence roster remains separately visible under `EVENT_GET_PRESENCES`. A Member with that permission may view the Member roster but shall not thereby view the combined tracker or Oratoriano attendance.

---

### REQ-ORATORIO-ATT-003: Tracker roster

The combined tracker shall present separate sections for:

- active Members; and
- all non-deleted registered Oratorianos.

Each section shall expose an alphabetical roster paged at 50 people per page and an accent-insensitive, case-insensitive full-name search suitable for marking one arriving person at a time. The tracker shall keep a persistent summary of everyone already marked present regardless of the current page or search. Oratoriano exact-match evaluation shall use the human-equivalent name key defined by the Oratoriano Records specification.

An inactive Member or deleted Oratoriano with existing attendance shall remain visible, clearly marked with current status, in each occurrence where that attendance exists.

A deleted Oratoriano shall not be offered for new attendance. Member Presence eligibility shall retain the accepted Member active/inactive rules: an inactive Member is not part of the default active roster but may be deliberately found and marked through an authorized Member Presence workflow.

---

### REQ-ORATORIO-ATT-004: Attendance timing and lifecycle

For both sections, new attendance shall become eligible at 13:30 inclusive on the occurrence date in `America/Sao_Paulo`, exactly 30 minutes before the fixed 14:00 start.

Attendance may be added or corrected while the Event is `SCHEDULED` after that boundary and while it is `COMPLETED`. `LOCKED` and `FINALIZED` shall reject all attendance mutations. `CANCELLED` shall reject new attendance but permit removal of an existing mistaken attendance.

The early-window rule is an explicit Oratorio specialization of the common Member Presence rule; Generic Event and Missa Presence registration shall continue to begin at their Event `beginDate`.

---

### REQ-ORATORIO-ATT-005: Immediate checkbox persistence

Each tracker check or uncheck shall be persisted independently. The tracker shall not require or expose a full-roster replacement command.

Checking an already active attendance shall succeed as an idempotent no-op and return the existing fact. Unchecking a pair with no active attendance shall also succeed as an idempotent no-op. Only an actual state change shall emit an activity.

For the Member section, this specialized tracker contract intentionally overrides the common Presence API's duplicate-registration and missing-removal outcomes without changing those outcomes on generic Presence routes.

---

### REQ-ORATORIO-ATT-006: Conditional removal reason

Unchecking attendance shall apply the same reason policy to Member and Oratoriano attendance:

| Event status | Reason |
| --- | --- |
| `SCHEDULED` | Not required |
| `COMPLETED` | Required, normalized, 1 to 2,000 characters |
| `CANCELLED` | Not required |
| `LOCKED` or `FINALIZED` | Removal rejected |

The reason shall be stored only in the removal activity, not as an attendance column.

---

### REQ-ORATORIO-ATT-007: Inline registration and attendance

The Oratoriano section shall offer an atomic “register new Oratoriano and mark present” operation requiring only `firstName` and `surname`.

The interface shall prominently advise the coordinator to ask for the Oratoriano's complete name. It shall search before enabling new registration. Similar substring results shall remain visible as identity-review prompts but shall not block registration; only an exact human-equivalent name match shall block it.

An existing match shall never be marked automatically. The coordinator must explicitly confirm that the arriving person is the existing Oratoriano before using the ordinary check operation.

The combined registration operation shall be allowed only while new attendance is eligible under `REQ-ORATORIO-ATT-004`. It shall:

- create both the new Oratoriano and attendance, or neither;
- create no attendance when name uniqueness fails, including a concurrent conflict;
- reject a name reserved by a deleted Oratoriano and require restoration first; and
- never infer that an existing name identifies the arriving person.

Outside the attendance window, ordinary Oratoriano registration remains available independently.

---

### REQ-ORATORIO-ATT-008: Attendance activity history

An actual check shall emit one attendance-registered activity. An actual uncheck shall emit one attendance-removed activity and store the reason when required or supplied.

The activity shall identify the occurrence and person UUIDs without copying health, family, form, or contact data. Mutation and activity shall commit atomically.

The atomic quick-registration workflow shall emit one high-level activity for its combined business intent rather than unrelated duplicate low-level activities.

---

### REQ-ORATORIO-ATT-009: Concurrency safety

Attendance mutation, occurrence lifecycle mutation, occurrence deletion, Oratoriano soft deletion, and quick registration shall evaluate and commit against serialized latest state.

Concurrent checks for one pair shall leave exactly one active attendance. A lifecycle closure may commit first and block attendance, or attendance may commit first while the occurrence remains open. An occurrence with an active attendance shall not be deleted.

Domain outcomes shall be returned instead of exposing persistence constraint or locking failures.

---

### REQ-ORATORIO-ATT-010: Historical reporting

Member and Oratoriano attendance counts shall be derived from preserved attendance resources rather than stored counter columns.

An occurrence's historical attendance shall continue to include preserved records linked to Members or Oratorianos that later become inactive or soft-deleted. A deleted Oratoriano shall remain excluded from new attendance. New Member Presence eligibility shall continue to follow the accepted Member active/inactive Presence rules.

---

### REQ-ORATORIO-ATT-011: Tracker route and response catalog

The tracker shall expose:

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/oratorios/{oratorioId}/attendance/members` | Read a 50-person Member roster page using `page` and optional `name` |
| `GET` | `/oratorios/{oratorioId}/attendance/oratorianos` | Read a 50-person Oratoriano roster page using `page` and optional `name` |
| `GET` | `/oratorios/{oratorioId}/attendance/present` | Read the unpaged persistent present summary |
| `PUT` | `/oratorios/{oratorioId}/attendance/members/{memberId}` | Idempotently mark a Member present |
| `DELETE` | `/oratorios/{oratorioId}/attendance/members/{memberId}` | Idempotently uncheck a Member |
| `PUT` | `/oratorios/{oratorioId}/attendance/oratorianos/{oratorianoId}` | Idempotently mark an existing Oratoriano present |
| `DELETE` | `/oratorios/{oratorioId}/attendance/oratorianos/{oratorianoId}` | Idempotently uncheck an Oratoriano |
| `POST` | `/oratorios/{oratorioId}/attendance/oratorianos/register-and-mark` | Atomically register a unique Oratoriano and mark present |

The persistent summary shall contain separate `members` and `oratorianos` collections. It shall identify each person and attendance resource compactly, expose current inactive/deleted state where applicable, and contain no additional-form or other sensitive profile data.

A newly created check shall return `201 Created`. Checking an already active pair shall return `200 OK` with the existing attendance. Unchecking shall return `204 No Content` whether or not an active attendance existed, subject to lifecycle and reason validation.

## Acceptance scenarios

```gherkin
Scenario: Check attendance at the early boundary
  Given an Oratorio begins at 14:00 local time
  And the occurrence is SCHEDULED
  When an authorized coordinator checks a person present at 13:30
  Then attendance is recorded

Scenario: Reject attendance before the early boundary
  Given an Oratorio begins at 14:00 local time
  When an authorized coordinator checks attendance at 13:29:59 local time
  Then the operation is rejected

Scenario: Repeated check is idempotent
  Given an active attendance already exists for a person and occurrence
  When an authorized coordinator checks the same person again
  Then the existing attendance is returned
  And no new resource or activity is created

Scenario: Completed occurrence requires a correction reason
  Given an active attendance belongs to a COMPLETED Oratorio
  When an authorized coordinator unchecks it without a reason
  Then the operation is rejected

Scenario: Cancelled occurrence allows removal but not addition
  Given an Oratorio is CANCELLED
  When an authorized coordinator checks new attendance
  Then the check is rejected
  When the coordinator unchecks an existing mistaken attendance
  Then the attendance is removed without requiring a reason

Scenario: Quick registration is atomic
  Given no Oratoriano has the submitted canonical name
  And attendance is open
  When an authorized coordinator registers the name from the tracker
  Then one Oratoriano and one attendance are created
  And neither can commit without the other

Scenario: Existing name is never marked automatically
  Given an existing Oratoriano is named Erik Garcia
  And a different arriving person reports the same abbreviated name
  When the coordinator attempts quick registration
  Then no attendance is created
  And the interface requires identity resolution or a distinct complete name

Scenario: Present summary survives roster navigation
  Given several people have been marked present
  When the coordinator changes roster page or performs another name search
  Then the persistent present summary continues to show every marked person

Scenario: Deleted attendee remains historically visible
  Given an Oratoriano attended an occurrence and was later soft-deleted
  When an authorized coordinator reads that occurrence's tracker
  Then the preserved attendance is visible with the Oratoriano marked deleted
  And no new attendance can be added for that record
```

## Open questions

* None.

## Out of scope

* Explicit absence, RSVP, planned attendance, or attendance reservations.
* Bulk roster replacement or bulk checkbox persistence.
* Spreadsheet, CSV, or Excel import.
* Attendance observations for Oratorianos.
* Attendance rates, streaks, averages, or absence counts.

## Future consideration

Legacy spreadsheet import should be planned as a separate feature. It must resolve date parsing, identity matching against unique normalized Oratoriano names, Member/Oratoriano sheet separation, duplicate rows, and auditable import correction before implementation.

## Related ADRs

* [ADR-0012: Serialize Event and Presence mutations](../../decisions/0012-serialize-event-and-presence-mutations.md)
* [ADR-0017: Serialize Oratorio and Oratoriano mutations](../../decisions/0017-serialize-oratorio-and-oratoriano-mutations.md)

## Related requirements

* [Oratorio Occurrences and Planning](oratorio-occurrences-and-planning.md)
* [Oratoriano Records](../oratorianos/oratoriano-records.md)
* [Member Event Presences](../presences/member-event-presences.md)
* [RBAC Catalog](../rbac/rbac-catalog.md)

## Related videos

* None.
