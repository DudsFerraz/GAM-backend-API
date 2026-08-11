# Requirement: Oratorio Occurrences and Planning

## Status

Accepted

## Context

GAM needs a specialized Oratorio workflow that reuses the common Event identity, visibility, search, location, and lifecycle concepts while owning fixed schedule semantics, Oratorio content, Member responsibilities, and specialized mutations.

One Oratorio occurrence is one dated Oratorio activity. A recurring season is represented by multiple occurrences rather than a recurrence aggregate.

## Ubiquitous Language

- `Oratorio occurrence`: One specialized Oratorio and its one-to-one Event record for one local calendar date.
- `Equipe do Lanche`: Members assigned to organize, prepare, and distribute the Lanche for one Oratorio occurrence.
- `Equipe da Gincana`: Members assigned to plan, prepare, and conduct the Gincana for one Oratorio occurrence.
- `Boa Tarde das Crianças`: The children-focused Boa Tarde content planned for one occurrence.
- `Boa Tarde dos Jovens`: The youth-focused Boa Tarde content planned for one occurrence.
- `Equipe do Boa Tarde das Crianças`: Members assigned to conduct the children-focused Boa Tarde.
- `Equipe do Boa Tarde dos Jovens`: Members assigned to conduct the youth-focused Boa Tarde.

## Functional requirements

### REQ-ORATORIO-001: Shared Event identity

Each Oratorio occurrence shall specialize exactly one Event of immutable type `ORATORIO`. The Oratorio and Event shall share the same UUID as their public identity.

The specialized workflow shall create, mutate, close, reopen, cancel, and remove the Event and Oratorio data atomically. Generic Event mutation routes shall continue to reject the specialized Event.

---

### REQ-ORATORIO-002: Date-only creation and derived Event data

Creating an Oratorio shall require only one local calendar date interpreted in `America/Sao_Paulo`.

The system shall derive:

- title `Oratório`;
- empty description;
- begin instant at 14:00 local time;
- end instant at 17:00 local time;
- audience permission `EVENT_GET_MEMBER`;
- Event type `ORATORIO`; and
- the current system `GamLocation` selected by
  `gam.oratorio.location-code`.

`gam.oratorio.location-code` shall default to `DBSM`, making São Mário the
normal Oratorio location. An intentional deployment-wide override may select
`DBA` or `DBCA`. These three physical system locations are the complete
Oratorio location catalog. `REMOTE` shall not be accepted because an Oratorio
occurrence remains a physical activity. The configured value shall resolve by
immutable location code, not by mutable name or a hard-coded database UUID.

Application startup shall fail before serving requests when the configured code
is blank, unknown, `REMOTE`, retired, soft-deleted, not system-managed, or not
one of `DBSM`, `DBA`, and `DBCA`. Creation shall also fail atomically if the
validated configured location becomes unavailable before an occurrence
commits. The complete catalog and configuration lifecycle is governed by
`REQ-GAM-LOCATION-CATALOG-008`.

Past, present, and future dates are accepted without an artificial horizon. The common Event temporal rules determine whether the new occurrence is `SCHEDULED` or `COMPLETED`.

The local date and all derived Event fields shall be immutable after creation. Correcting a mistaken date requires removing active attendance, soft-deleting the erroneous occurrence, and creating the correct occurrence.

---

### REQ-ORATORIO-003: One active occurrence per local date

At most one non-deleted Oratorio occurrence shall exist for one `America/Sao_Paulo` calendar date.

Concurrent creation attempts for the same date shall produce one occurrence and one domain conflict. A soft-deleted occurrence shall release the date for a later occurrence.

---

### REQ-ORATORIO-004: Fixed schedule

Every occurrence shall use this fixed schedule:

| Local time | Activity |
| --- | --- |
| 14:00–15:30 | Recreação livre |
| 15:30–16:30 | Gincana |
| 16:30–17:00 | Boa Tarde das Crianças and Boa Tarde dos Jovens concurrently |
| 17:00 closing boundary | Lanche is distributed as Oratorianos leave |

The Lanche shall not be represented as a time interval. Clients shall not create, reorder, resize, or remove schedule blocks.

---

### REQ-ORATORIO-005: Optional content planning

One occurrence may hold:

- one optional normalized Lanche description;
- one optional normalized Gincana description;
- one optional normalized planning text for Boa Tarde das Crianças; and
- one optional normalized planning text for Boa Tarde dos Jovens.

Each field is independent and may exist without an assigned team. Empty structured inventory, scoring, script, attachment, supplier, cost, or quantity models shall not be implied by these text fields.

Each supplied planning text shall be trimmed, normalize blank text to absent, and contain at most 10,000 characters.

---

### REQ-ORATORIO-006: Standard Member teams

One occurrence shall expose exactly four standardized Member-assignment sets:

- Equipe do Lanche;
- Equipe da Gincana;
- Equipe do Boa Tarde das Crianças; and
- Equipe do Boa Tarde dos Jovens.

Only active Members may receive a new assignment. A Member may belong to multiple teams but at most once in each team. Teams may remain empty, including when the occurrence is finalized.

If an assigned Member later becomes inactive, the assignment shall remain as part of the plan and shall display the Member's current inactive status until an authorized user deliberately removes or replaces it. Team assignment shall not imply attendance.

---

### REQ-ORATORIO-007: Read visibility and specialized detail

Common discovery shall remain available through `POST /events/search`, including filtering by Event type `ORATORIO`. No separate `/oratorios/search` endpoint is required initially.

`GET /oratorios/{id}` shall return the specialized occurrence detail. It shall require `ORATORIO_GET`.

Baseline `MEMBER`, `COORD`, `ORATORIO_COORD`, and `SUDO` shall receive `ORATORIO_GET`. The specialized detail visible to a Member may include the common Event data, fixed schedule, content planning, and Member team assignments. It shall not include the combined attendance tracker, Oratoriano records, additional forms, health/family data, or signed attachments.

Anonymous callers and `VISITOR` shall not view an Oratorio because its Event audience is `EVENT_GET_MEMBER`.

---

### REQ-ORATORIO-008: Specialized mutation authority

Creation shall require `ORATORIO_CREATE`. Content, team, lifecycle, correction, and deletion operations shall require `ORATORIO_MANAGE`.

Specialized mutations shall not additionally require generic `EVENT_CREATE` or `EVENT_MANAGE`. Baseline `COORD` and `ORATORIO_COORD` shall receive the specialized operational permissions; `MEMBER` and `VISITOR` shall not.

---

### REQ-ORATORIO-009: Lifecycle behavior

The occurrence shall reuse the common Event statuses with these specialized effects:

| Status | Planning | Attendance |
| --- | --- | --- |
| `SCHEDULED` | Editable | Open without an earliest time boundary under `REQ-ORATORIO-ATT-012` |
| `COMPLETED` | Editable | Open without a latest time boundary under `REQ-ORATORIO-ATT-012` |
| `LOCKED` | Editable for authorized corrections | Closed |
| `FINALIZED` | Closed | Closed |
| `CANCELLED` | Closed | New attendance closed; mistaken attendance may be removed |

Specialized commands shall support the common allowed cancellation, lock, finalize, and reopen transitions. Cancellation and reopening shall require a reason under the common bounded-reason rule.

---

### REQ-ORATORIO-010: Protected deletion

An Oratorio may be soft-deleted only while `SCHEDULED`, `COMPLETED`, or `CANCELLED`, with a required normalized reason, and only when it has no active Member Presence or active Oratoriano attendance.

`LOCKED` and `FINALIZED` occurrences shall be reopened before deletion. Removed attendance records shall not block deletion and shall remain preserved with the deleted occurrence.

Deletion shall atomically remove the Event and specialization from ordinary visibility while preserving their shared identity and history.

---

### REQ-ORATORIO-011: Transactional activity history

Every changed creation, planning, team, lifecycle, and deletion operation shall emit one high-level activity describing its business intent. A normalized no-op shall not mutate or emit an activity.

Activities shall identify changed field or team names without copying sensitive Oratoriano or form data. The business mutation and activity shall commit atomically.

---

### REQ-ORATORIO-012: Specialized route catalog

The specialized API shall expose:

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/oratorios` | Create from one local date |
| `GET` | `/oratorios/{oratorioId}` | Read specialized detail |
| `PUT` | `/oratorios/{oratorioId}/planning` | Fully replace the four optional planning texts |
| `PUT` | `/oratorios/{oratorioId}/teams/{teamType}/members/{memberId}` | Assign one Member idempotently |
| `DELETE` | `/oratorios/{oratorioId}/teams/{teamType}/members/{memberId}` | Remove one assignment idempotently |
| `PATCH` | `/oratorios/{oratorioId}/lock` | Lock attendance |
| `PATCH` | `/oratorios/{oratorioId}/finalize` | Finalize |
| `PATCH` | `/oratorios/{oratorioId}/reopen` | Reopen with required reason and target status |
| `PATCH` | `/oratorios/{oratorioId}/cancel` | Cancel with required reason |
| `DELETE` | `/oratorios/{oratorioId}` | Protected soft deletion with required reason |

The accepted `teamType` catalog shall contain exactly `LANCHE`, `GINCANA`, `BOA_TARDE_CRIANCAS`, and `BOA_TARDE_JOVENS`.

## Acceptance scenarios

```gherkin
Scenario: Create an occurrence from a date
  Given gam.oratorio.location-code selects current system location DBSM
  And no active Oratorio exists on the requested local date
  And the caller has ORATORIO_CREATE
  When the caller creates the Oratorio using only that date
  Then one ORATORIO Event and specialization share one UUID
  And the Event runs from 14:00 to 17:00 in America/Sao_Paulo
  And its audience permission is EVENT_GET_MEMBER

Scenario: Invalid configured location blocks startup
  Given gam.oratorio.location-code is blank, REMOTE, or does not identify an accepted physical Oratorio location
  When the application starts
  Then startup fails before requests are served

Scenario: Concurrent duplicate dates produce one occurrence
  Given no active occurrence exists for a local date
  When two authorized creations for that date commit concurrently
  Then exactly one occurrence is created
  And the other request returns a domain conflict

Scenario: Occurrence date is immutable
  Given an Oratorio occurrence exists
  When an authorized user attempts to change its local date or derived Event range
  Then the mutation is rejected

Scenario: Member reads an Oratorio plan
  Given a Member can view the ORATORIO Event
  When the Member reads its specialized detail
  Then the fixed schedule, content, and Member teams are visible
  And Oratoriano attendance and forms are absent

Scenario: Lanche is a closing act
  Given an Oratorio occurrence exists
  When its schedule is represented
  Then Lanche appears at the 17:00 closing boundary
  And no Lanche time interval is present

Scenario: Inactive assigned Member remains in the plan
  Given an active Member was assigned to Equipe da Gincana
  When that Member becomes inactive
  Then the assignment remains
  And the Member is displayed as inactive

Scenario: Removed attendance does not block deletion
  Given a COMPLETED occurrence has only removed Member and Oratoriano attendance
  When an authorized user deletes it with a valid reason
  Then the Event and specialization become hidden
  And the removed attendance history remains preserved
```

## Open questions

* None.

## Out of scope

* Recurrence rules or an Oratorio-season aggregate.
* Arbitrary schedule blocks or configurable Oratorio times.
* Lanche inventories, quantities, costs, suppliers, or serving intervals.
* Gincana rounds, scoring, winners, Oratoriano teams, or materials inventory.
* Structured Boa Tarde scripts, templates, or attachments.
* Custom occurrence-specific responsibilities beyond the four standard teams.
* A separate Oratorio search endpoint.
* Per-occurrence location selection.

## Related ADRs

* [ADR-0015: Compose Oratorio permission bundles in code](../../decisions/0015-compose-oratorio-permission-bundles-in-code.md)
* [ADR-0012: Serialize Event and Presence mutations](../../decisions/0012-serialize-event-and-presence-mutations.md)
* [ADR-0017: Serialize Oratorio and Oratoriano mutations](../../decisions/0017-serialize-oratorio-and-oratoriano-mutations.md)
* [ADR-0031: Model remote attendance as a single system GamLocation](../../decisions/0031-model-remote-attendance-as-a-single-system-gam-location.md)

## Related requirements

* [Event Records and Generic Event Lifecycle](../events/event-records-and-generic-lifecycle.md)
* [Oratorio Attendance Tracker](oratorio-attendance-tracker.md)
* [Oratorio Coordinator Designation](oratorio-coordinator-designation.md)
* [RBAC Catalog](../rbac/rbac-catalog.md)
* [System GamLocation Catalog](../gam-locations/system-gam-location-catalog.md)

## Related videos

* None.
