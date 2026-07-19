# Requirement: Membership Solicitations

## Status
Accepted

## Context
An authenticated Account needs a way to express interest in becoming a GAM Member without becoming a Member automatically. A Coordinator must review the submitted information and explicitly approve or reject the request.

A membership solicitation is separate from a Member. Only approval creates membership. This preserves the distinction between Account identity, requested membership, and accepted lifetime membership.

The current implementation does not define this intended workflow. Existing Member and Account behavior was used only as discovery material and conversation prompts.

## Ubiquitous Language
- `applicant justification`: The Account's explanation of why the person wants to become a Member.
- `review reason`: The Coordinator's required explanation for approving or rejecting a membership solicitation.
- `solicitation status`: The review state `PENDING`, `APPROVED`, or `REJECTED`.
- `reviewing Account`: The authenticated Coordinator Account that approves or rejects a solicitation.

## Functional requirements

### REQ-MEMBER-SOL-001: Separate solicitation identity and membership boundary
Each membership solicitation shall be a persisted resource with a UUID v7 identifier in accordance with `REQ-GAM-ID-001` through `REQ-GAM-ID-003`.

A membership solicitation shall belong to exactly one Account. Creating a solicitation shall not create a Member, assign `MEMBER`, remove `VISITOR`, or otherwise grant membership capabilities.

Only successful approval shall create a Member linked to the soliciting Account.

Rationale:
Requesting membership and becoming a lifetime Member are different domain events and require separate histories.

Valid examples:
- A pending solicitation exists while its Account has no Member.
- Approval creates a Member and records the resulting Member identifier on the solicitation.

Invalid examples:
- Submitting the form immediately grants the `MEMBER` role.
- A rejected solicitation leaves behind a Member record.

---

### REQ-MEMBER-SOL-002: Self-service submission and form contract
The system shall expose `POST /membership-solicitations` to authenticated Accounts. The Account identifier shall be derived from the authenticated caller and shall not be accepted in the request body.

The caller's Account shall be existing and non-soft-deleted, shall not already be linked to a Member, and shall not have another pending membership solicitation.

The request shall require:

```json
{
  "firstName": "Ana",
  "surname": "Silva",
  "birthDate": "2000-01-01",
  "phoneNumber": "+5519998877665",
  "justification": "I want to participate in GAM activities"
}
```

The name and phone number shall satisfy the accepted common primitive requirements. The birth date shall not be in the future, and the applicant shall be at least 17 years old on the submission date. The seventeenth birthday shall be accepted.

The system shall trim leading and trailing whitespace from `justification`. After trimming, it shall contain between 1 and 2,000 characters.

On success, the system shall return `201 Created`, set `Location` to `/api/membership-solicitations/{solicitationId}`, and return the solicitation record defined by `REQ-MEMBER-SOL-006`.

Rationale:
The solicitation collects Member information that is absent from an Account and captures the applicant's own intent without allowing one Account to apply for another.

Valid examples:
- An authenticated eligible Account submits its own information and justification.
- An Account submits a new solicitation after an earlier rejection.

Invalid examples:
- The request supplies a different `accountId`.
- A 16-year-old submits a solicitation.
- An existing Member submits another membership solicitation.
- The applicant justification contains only whitespace.

---

### REQ-MEMBER-SOL-003: Solicitation status and immutability
Solicitation status shall contain exactly `PENDING`, `APPROVED`, and `REJECTED`.

A newly submitted solicitation shall be `PENDING`. Submitted identity data and applicant justification shall become an immutable snapshot.

Only a pending solicitation may transition to approved or rejected. Approved and rejected solicitations shall be immutable historical records and shall not be reopened, reversed, edited, or withdrawn.

Rationale:
Immutable submissions and decisions preserve what the applicant requested and what the Coordinator reviewed.

Valid examples:
- A rejected solicitation retains its submitted data, applicant justification, and review reason.
- Corrected data is submitted in a new solicitation after rejection.

Invalid examples:
- A Coordinator edits the applicant's phone number before approval.
- An applicant overwrites a rejected solicitation.
- An approved decision is changed to rejected.

---

### REQ-MEMBER-SOL-004: One pending solicitation and preserved reapplication history
An Account shall have at most one pending membership solicitation.

The system shall enforce this invariant under concurrent submissions so that at most one request succeeds. A duplicate or concurrent losing submission shall return `409 Conflict` without creating another solicitation or activity event.

An Account with a rejected solicitation may submit a new solicitation. The new solicitation shall have a new UUID and shall not overwrite, restore, or reopen the rejected record.

A pending solicitation shall block direct Member registration for the same Account. The Coordinator shall approve or reject the existing solicitation instead.

Rationale:
One pending request avoids conflicting reviews while immutable reapplication history preserves earlier decisions.

Valid examples:
- An Account with two rejected solicitations later has one new pending solicitation.
- A direct registration attempt is rejected while a pending solicitation exists.

Invalid examples:
- Two concurrent submissions create two pending solicitations for one Account.
- Direct registration silently bypasses or closes a pending solicitation.

---

### REQ-MEMBER-SOL-005: Solicitation read authorization
The system shall expose `GET /membership-solicitations/{solicitationId}` and `POST /membership-solicitations/search`.

An authenticated Account may read and search only its own solicitation history. A caller with `MEMBER_MANAGE` may read and search all solicitations.

A caller without `MEMBER_MANAGE` shall remain restricted to its own Account even when it submits filters that identify another Account. A direct lookup of another Account's solicitation shall return `404 Not Found` rather than reveal that the solicitation exists.

Rationale:
Applications contain personal data, applicant intent, and review decisions that must not be visible to unrelated Accounts.

Valid examples:
- An applicant reads the rejection reason for its own solicitation.
- A Coordinator with `MEMBER_MANAGE` searches all pending solicitations.

Invalid examples:
- An Account discovers another Account's solicitation through its UUID.
- An `accountId` filter bypasses applicant scoping.

---

### REQ-MEMBER-SOL-006: Solicitation response contract
Solicitation creation, lookup, search, approval, and rejection shall return this shape:

```json
{
  "id": "<solicitation UUID>",
  "account": {
    "id": "<account UUID>",
    "email": "ana@example.com",
    "displayName": "Ana"
  },
  "firstName": "Ana",
  "surname": "Silva",
  "birthDate": "2000-01-01",
  "phoneNumber": "+5519998877665",
  "justification": "I want to participate in GAM activities",
  "status": "PENDING",
  "submittedAt": "2026-07-13T12:00:00Z",
  "reviewedBy": null,
  "decidedAt": null,
  "reviewReason": null,
  "memberId": null
}
```

For a decided solicitation, `reviewedBy` shall contain only the reviewing Account's `id`, `email`, and `displayName`; `decidedAt` and `reviewReason` shall contain the decision data. An approved solicitation shall contain the created `memberId`. A rejected solicitation shall have a null `memberId`.

The response shall not expose Account roles, credentials, tokens, sessions, soft-delete fields, or row audit metadata.

Rationale:
Applicants and Coordinators need the complete immutable submission and decision outcome without receiving authorization internals or persistence metadata.

Valid examples:
- A rejected solicitation includes its review reason and no Member identifier.
- An approved solicitation identifies the resulting Member.

Invalid examples:
- A pending solicitation invents decision values.
- The response embeds Account role collections.

---

### REQ-MEMBER-SOL-007: Solicitation search contract
`POST /membership-solicitations/search` shall accept only these public filter fields and comparison methods:

| Public field | Allowed comparison methods |
| --- | --- |
| `id` | `EQUALS`, `IN` |
| `accountId` | `EQUALS` |
| `email` | `EQUALS`, `LIKE` |
| `name` | `LIKE` across submitted `firstName` and `surname` |
| `status` | `EQUALS`, `IN` |
| `submittedAt` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` |
| `decidedAt` | `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL` |
| `reviewedByAccountId` | `EQUALS` |

Empty filters shall return a paginated page of all solicitations visible to the caller. Applicant visibility from `REQ-MEMBER-SOL-005` shall be enforced in addition to caller-supplied filters.

Unsupported methods and invalid filter values shall identify the public field. Unknown fields shall return the generic message `Unknown filter field.` and shall not expose submitted field names or persistence paths.

Rationale:
The search API needs a stable public vocabulary while preserving ownership-based privacy.

Valid examples:
- A Coordinator searches `status EQUALS "PENDING"`.
- An applicant uses empty filters and receives only its own history.

Invalid examples:
- An applicant filters by another Account and receives that Account's solicitations.
- A filter uses an internal reviewing-Account persistence path.

---

### REQ-MEMBER-SOL-008: Coordinator review authorization and reason
The system shall expose:

| Method | Route | Required permission | Purpose |
| --- | --- | --- | --- |
| `PATCH` | `/membership-solicitations/{solicitationId}/approve` | `MEMBER_MANAGE` | Approve a pending solicitation. |
| `PATCH` | `/membership-solicitations/{solicitationId}/reject` | `MEMBER_MANAGE` | Reject a pending solicitation. |

Each request shall require a `reason`. The system shall trim leading and trailing whitespace before validation and audit logging. After trimming, the review reason shall contain between 1 and 2,000 characters.

A null, empty, whitespace-only, or over-2,000-character review reason shall return `400 Bad Request` before solicitation, Member, role, or activity-log mutation.

The system shall record the reviewing Account and decision timestamp. Decision information shall be immutable after the decision commits.

Rationale:
Approval establishes lifetime membership and rejection denies the current request; both outcomes require explicit Coordinator intent.

---

### REQ-MEMBER-SOL-009: Atomic approval (lifecycle Role precondition superseded)

The lifecycle Role precondition and projection portions of this requirement are superseded by `REQ-MEMBER-SOL-014`. Its remaining approval, response, atomicity, and audit contract remains accepted.

Approval shall revalidate that:

- the solicitation is `PENDING`;
- its Account exists and is not soft-deleted;
- the Account is not already linked to a Member; and
- the submitted birth date remains valid and the applicant satisfies the minimum age requirement.

Successful approval shall atomically:

1. create one UUID v7 `ACTIVE` Member linked permanently to the soliciting Account using the submitted name, birth date, and phone number;
2. assign `MEMBER` and remove `VISITOR` while preserving all other Account roles;
3. mark the solicitation `APPROVED` with the reviewing Account, decision timestamp, normalized review reason, and Member identifier; and
4. emit exactly one `MEMBERSHIP_SOLICITATION_APPROVED` activity event.

The approval reason shall also be the Member's initial activation reason. The endpoint shall return `200 OK` with the decided solicitation.

The workflow shall not emit separate `MEMBER_REGISTERED`, `ACCOUNT_ROLE_ADDED`, or `ACCOUNT_ROLE_REMOVED` activity events.

Rationale:
Approval is one business decision and must not leave partial membership, decision, authorization, or audit state.

Valid examples:
- Approval creates one active Member and returns its identifier in the solicitation.
- Existing custom Roles remain assigned after approval.

Invalid examples:
- The solicitation becomes approved but Member creation fails.
- The Member is created without its `MEMBER` role.
- One approval produces multiple high-level activity events.

---

### REQ-MEMBER-SOL-010: Atomic rejection
Only a `PENDING` solicitation may be rejected.

Successful rejection shall atomically mark the solicitation `REJECTED`, record the reviewing Account, decision timestamp, and normalized review reason, and emit exactly one `MEMBERSHIP_SOLICITATION_REJECTED` activity event.

Rejection shall not create a Member or change Account roles. The endpoint shall return `200 OK` with the decided solicitation.

Rationale:
Rejection preserves the reviewed request and its explanation without changing membership or authorization state.

Valid examples:
- An applicant can later read the Coordinator's rejection reason.
- A rejected applicant submits a new solicitation with corrected information.

Invalid examples:
- Rejection creates an inactive Member.
- Rejection edits the applicant's original justification.

---

### REQ-MEMBER-SOL-011: Decision and registration concurrency
Only one valid decision or competing Member-creation outcome shall commit for an Account.

Concurrent approval, rejection, or direct Member registration shall preserve:

- at most one lifetime Member for the Account;
- one immutable outcome for each solicitation;
- Member status and lifecycle-role consistency; and
- exactly one high-level activity event for the committed workflow.

A request that loses the race or targets an already decided solicitation shall return `409 Conflict` without partial changes or duplicate activity events.

Rationale:
Coordinator actions and registration attempts may overlap, but the domain outcome must remain singular and auditable.

---

### REQ-MEMBER-SOL-012: Solicitation API error semantics
Membership-solicitation routes shall use these outcomes:

| Condition | Response |
| --- | --- |
| Malformed fields, invalid common primitives, underage applicant, or invalid justification/review reason | `400 Bad Request` |
| Unauthenticated protected request | `401 Unauthorized` |
| Authenticated caller lacks `MEMBER_MANAGE` for a review operation | `403 Forbidden` |
| Solicitation is missing, soft-deleted, or hidden by ownership; required Account is missing or soft-deleted | `404 Not Found` |
| Account already has a Member, another solicitation is pending, solicitation is already decided, approval finds an inconsistent lifecycle Role projection, or concurrent operation loses | `409 Conflict` |

Failed requests shall not create or change solicitations, Members, lifecycle roles, or activity logs.

Rationale:
Clients need stable distinctions between invalid data, authorization, hidden resources, and conflicting workflow state.

---

### REQ-MEMBER-SOL-013: Solicitation activity audit
Successful solicitation workflows shall emit exactly one high-level activity event:

| Workflow | Activity action | Activity reason |
| --- | --- | --- |
| Submission | `MEMBERSHIP_SOLICITATION_SUBMITTED` | null; applicant justification remains on the solicitation record |
| Approval | `MEMBERSHIP_SOLICITATION_APPROVED` | normalized review reason |
| Rejection | `MEMBERSHIP_SOLICITATION_REJECTED` | normalized review reason |

Events shall capture the actor Account, solicitation identifier, applicant Account identifier, solicitation status transition, relevant Member and lifecycle-role identifiers when approval succeeds, and request metadata according to the activity-audit policy.

The workflow mutation and activity-log row shall commit together. Activity metadata shall not duplicate unnecessary personal data or the applicant justification. Failed operations shall emit no activity event.

Rationale:
The audit history should capture submission and Coordinator decisions without turning the activity log into a second store of application-form data.

---

### REQ-MEMBER-SOL-014: Approval requires a consistent pre-Member Role projection

This requirement supersedes the lifecycle Role precondition and projection portions of `REQ-MEMBER-SOL-009`.

Approval shall require the soliciting Account to have no linked Member and no active `MEMBER`, `VISITOR`, or `COORD` assignment. An Account without a Member that has any of those lifecycle-owned Roles is inconsistent and approval shall return `409 Conflict` without repairing Roles, creating a Member, deciding the solicitation, or emitting an activity event.

Successful approval shall create one active Member without Coordinator designation, assign `MEMBER`, keep `VISITOR` and `COORD` absent, and preserve every active custom Role. The Member, Role projection, solicitation decision, and one `MEMBERSHIP_SOLICITATION_APPROVED` activity event shall commit atomically under `REQ-MEMBER-016`.

Rationale:
Approval must not legitimize or silently repair a lifecycle-owned Role assignment that could not validly exist before membership.

Valid examples:
- Approval preserves an existing custom Role while creating an active non-Coordinator Member.

Invalid examples:
- Approval preserves a pre-existing COORD assignment on an Account with no Member.
- Approval silently removes an inconsistent VISITOR assignment and succeeds.

## Acceptance scenarios

```gherkin
Scenario: Eligible Account submits a membership solicitation
  Given an authenticated Account has no Member and no pending solicitation
  And the applicant is at least 17 years old
  When the Account submits valid Member information and a justification
  Then the system creates a PENDING solicitation for the authenticated Account
  And no Member or lifecycle-role change occurs
  And the response is 201 Created with the solicitation Location
  And exactly one MEMBERSHIP_SOLICITATION_SUBMITTED activity event is recorded

Scenario: Applicant cannot submit two pending solicitations
  Given the Account already has a PENDING solicitation
  When the Account submits another solicitation
  Then the system returns 409 Conflict
  And no second solicitation or activity event is created

Scenario: Coordinator approves a pending solicitation
  Given an eligible Account has a PENDING solicitation
  And the caller has MEMBER_MANAGE
  When the caller approves with a valid review reason
  Then the solicitation becomes APPROVED
  And one ACTIVE Member is created from the submitted data
  And the Account has MEMBER and does not have VISITOR
  And the response identifies the created Member
  And exactly one MEMBERSHIP_SOLICITATION_APPROVED activity event is recorded

Scenario: Coordinator rejects a pending solicitation
  Given an Account has a PENDING solicitation
  And the caller has MEMBER_MANAGE
  When the caller rejects with a valid review reason
  Then the solicitation becomes REJECTED
  And no Member or role change occurs
  And exactly one MEMBERSHIP_SOLICITATION_REJECTED activity event is recorded

Scenario: Rejected applicant submits again
  Given an Account has a REJECTED solicitation and no Member
  When the Account submits corrected valid data and a justification
  Then a new PENDING solicitation with a new UUID is created
  And the rejected solicitation remains unchanged

Scenario: Applicant search is ownership-scoped
  Given solicitations exist for multiple Accounts
  And the caller does not have MEMBER_MANAGE
  When the caller searches solicitations with empty filters
  Then only the caller's solicitation history is returned

Scenario: Concurrent decisions preserve one outcome
  Given a solicitation is PENDING
  When approval and rejection are attempted concurrently
  Then exactly one decision commits
  And the other request returns 409 Conflict
  And exactly one decision activity event is recorded

Scenario: Approval rejects an inconsistent lifecycle Role projection
  Given a pending solicitation belongs to an Account with no Member
  And the Account has MEMBER, VISITOR, or COORD
  And the caller has MEMBER_MANAGE
  When the caller approves with a valid review reason
  Then the system returns 409 Conflict
  And no Role is repaired or mutated
  And no Member, decision, or activity event is created
```

## Diagrams

* [Member Lifecycle and Membership Solicitation](../../diagrams/member-lifecycle-and-solicitation.md)

## Open questions

* None.

## Out of scope

* Editing submitted solicitation data or applicant justification.
* Withdrawing, cancelling, reopening, or reversing a solicitation.
* Invitations or automatic approval.
* Creating an Account through a solicitation.
* Member profile editing, deletion, restoration, or Account relinking.
* Account deactivation, deletion, or restoration workflows.
* Reading activity-log history through solicitation endpoints.
* Test structure or implementation strategy.

## Related ADRs

* [ADR-0013: Make Member lifecycle own Coordinator designation](../../decisions/0013-make-member-lifecycle-own-coordinator-designation.md)

## Related requirements

* [Member Records and Lifecycle](member-records-and-lifecycle.md)
* [GamName](../common/gam-name.md)
* [GamPhoneNumber](../common/gam-phone-number.md)
* [UUID Identity](../common/uuid.md)
* [Account Role Management](../rbac/account-role-management.md)

## Related videos

* None.
