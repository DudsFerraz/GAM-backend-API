# Requirement: Member Information Development Fixture Extension

## Status

Accepted

## Context

The accepted development fixture predates the accepted Member-information,
Account-linking, and annual-response surfaces. Developers need fictional,
repeatable records for the new endpoint families and concurrency seams without
copying the reviewed real production dataset.

This specification extends the canonical dataset and endpoint readiness owned
by the Development Fixture Policy and Dataset specification.

## Functional requirements

### REQ-MEMBER-INFO-FIXTURE-001: Synthetic isolation

Every Member profile, annual answer, Account, import provenance record, and
free-text value added for this feature shall be fictional and shall satisfy
`REQ-DEV-FIXTURE-006`.

The development fixture shall not read, copy, derive, package, or approximate
records from the private 2026 production dataset. Similar coverage shall be
constructed from deterministic fictional personas with reserved `example.com`
contact and Account emails.

The development callback shall not execute the production maintenance import
or create a production Member Information Import Batch. Fixture-owned
Account-less Members shall remain identifiable as synthetic fixture records,
not as applied production-import provenance.

Rationale:

Development needs the same behavioral seams, not the identities or answers of
real GAM Members.

---

### REQ-MEMBER-INFO-FIXTURE-002: Member-information catalog coverage

The converged canonical fixture shall include deterministic fictional examples
covering:

- active and inactive Account-less Members;
- linked and Account-less ordinary Member response shapes;
- every `YES`, `NO`, and `NOT_INFORMED` information status;
- every Member experience and sacrament type;
- every fixed contribution-area code;
- empty and populated fixed contribution profiles;
- empty and populated valid `otherContributionAreas`;
- dietary restriction with `YES` and details, `NO`, and `NOT_INFORMED`;
- every occupation, mass-attendance-frequency, and coordination-interest code;
- annual responses with null and non-null optional text;
- annual responses with known and null `submittedAt`;
- an active Member with no response for the documented not-found path; and
- current and stale Member aggregate ETags through sacrificial updates.

No single persona shall be required to contain every catalog seam. The fixture
may distribute coverage across focused baseline and sacrificial records.

---

### REQ-MEMBER-INFO-FIXTURE-003: Account-linking conflict coverage

The fixture shall provide separate fictional targets for:

- a successful active-Member link;
- a successful inactive-Member link;
- a Member that is already linked;
- an Account that is already linked;
- an eligible Account with rejected solicitation history;
- an Account blocked by a pending solicitation;
- an Account blocked by each inconsistent lifecycle-owned Role family;
- a missing or soft-deleted Member or Account visibility path; and
- two concurrent link attempts for one eligible Member or Account.

Successful-link targets shall begin with no active `MEMBER`, `VISITOR`,
`COORD`, or `ORATORIO_COORD`. At least one shall hold an unrelated custom Role
to verify that linking preserves it.

Account-less Member activation and deactivation shall use separate sacrificial
records from link success so either workflow remains manually exercisable in
one development session.

---

### REQ-MEMBER-INFO-FIXTURE-004: New endpoint readiness

The accepted endpoint-readiness matrix shall be expanded with successful
starting paths for:

| Endpoint | Fixture readiness |
| --- | --- |
| `GET /members/{memberId}/experiences-and-sacraments` | A visible Member has varied current statuses. |
| `GET /members/{memberId}/contribution-profile` | Visible Members have empty, fixed, and custom profiles. |
| `PUT /members/{memberId}` | A dedicated core-profile target has a current ETag and valid replacement values. |
| `PUT /members/{memberId}/gam-entry-date` | A dedicated target has a non-future alternative date. |
| `PUT /members/{memberId}/dietary-restriction` | A dedicated target supports a valid conditional-details transition. |
| `PUT /members/{memberId}/experiences` | A dedicated target can replace the complete four-key map. |
| `PUT /members/{memberId}/sacraments` | A dedicated target can replace the complete three-key map. |
| `PUT /members/{memberId}/contribution-profile` | A dedicated target can replace fixed and custom collections. |
| `PATCH /members/{memberId}/account/link` | Independent active and inactive Account-less Members and eligible Accounts exist. |
| `GET /members/{memberId}/annual-information/{surveyCycle}` | A visible protected response exists for successful audited read, and another Member has no response. |

The fixture shall support current-ETag success, stale-ETag rejection, valid
no-op update, authorization denial, inactive visibility, and protected-read
audit failure seams without consuming the only record required by another
endpoint family.

## Acceptance scenarios

```gherkin
Scenario: Development data is synthetic
  Given the private production dataset exists outside version control
  When the development fixture reconciles
  Then every new profile and annual response is deterministic fictional data
  And no production identity, contact value, or answer is copied

Scenario: Link workflows remain independently testable
  Given the fixture is converged
  When a Developer links the sacrificial active Account-less Member
  Then the inactive link target and Account-less lifecycle targets remain available

Scenario: Annual read has success and absence seams
  Given the fixture is converged
  Then one Member has a complete fictional annual response
  And another visible Member has no response for the same cycle
```

## Open questions

* None.

## Out of scope

* Importing or anonymizing the real production dataset for development.
* Creating production import-batch provenance through the development
  callback.
* Exhaustive invalid-input permutations that belong to automated tests.

## Related requirements

* [Development Fixture Policy and Dataset](development-fixture-policy-and-dataset.md)
* [Member Information](../members/member-information.md)
* [Member Information Import and Account Linking](../members/member-information-import-and-account-linking.md)

## Related ADRs

* [ADR-0026: Use an isolated atomic Member-information import with explicit later Account linking](../../decisions/0026-use-isolated-member-information-import-with-explicit-account-linking.md)
* [ADR-0027: Model Member information as normalized aggregate components and immutable annual responses](../../decisions/0027-model-member-information-as-normalized-components-and-immutable-annual-responses.md)

## Related videos

* None.
