# ADR-0026: Use an isolated Member-information import

## Status

Accepted

## Context

GAM needs to load one Developer-reviewed 2026 dataset for existing real-world
Members who do not yet have Accounts. Existing accepted Member requirements
currently require every Member to be linked to an Account at creation and make
the link immutable.

Creating Accounts from source contact emails would confuse contact data with
authentication identity, manufacture identities without the people's consent,
and make credential ownership unclear. Automatically matching later Accounts
to Members by personal data would introduce false-match and disclosure risks.

The data is real production data, so it cannot enter the fictional development
fixture. The import must be deliberate, private, repeatable only as a safe
no-op, all-or-nothing, and auditable without copying personal values into logs
or activity metadata.

## Decision

Use one isolated Spring Boot `maintenance` profile job to validate and
atomically apply an externally mounted approved input document.

The job shall:

- run only when explicitly selected as `member-info-import`;
- support validation-only and atomic apply actions;
- consume an already reviewed input with explicit UUID v7 identities;
- verify a canonical payload checksum;
- reject partial, ambiguous, duplicate, or heuristic adoption outcomes;
- persist one immutable non-sensitive batch record only after successful apply;
- create one high-level Developer-maintenance activity; and
- exit without starting the ordinary web application.

The imported production Members may temporarily have no Account link. This is
a narrow provenance-backed exception; ordinary direct registration and
Membership Solicitation approval still require an Account. Development may
create fictional Account-less fixture seams only inside its already isolated
fixture boundary.

An imported Member's `contactEmail` shall remain independent from Account login
email. Public Account registration shall not inspect or link Member records.

Provide a permanent explicit
`PATCH /members/{memberId}/account/link` workflow for a human Coordinator or
SUDO-authorized operator to link one existing eligible Account to one existing
Account-less Member. The link shall be immutable, require a reason, enforce
one-to-one and lifecycle preconditions transactionally, project only the
Member's current `MEMBER` or `VISITOR` Role, preserve custom and SUDO Roles, and
never grant Coordinator responsibility.

If the Account has a pending Membership Solicitation, a human Coordinator must
first decide it. The product shall provide operational guidance but no
automatic PII matching, rejection, or combined approve-and-link workflow.

## Alternatives considered

### Option 1: Create Accounts from contact emails during import

Pros:

- Preserves the current non-null Member-to-Account schema.
- Immediately enables lifecycle Role projection.

Cons:

- Treats a contact address as consent to create an authentication identity.
- Creates credential and Account-ownership ambiguity.
- May collide with future independent registration.
- Contradicts the agreed distinction between Member contact email and Account
  login email.

### Option 2: Automatically match new Accounts to imported Members

Pros:

- Reduces Coordinator work after launch.
- Could make registration appear seamless for existing Members.

Cons:

- Names, phones, emails, and birth dates are not reliable identity proofs.
- False matches could disclose or transfer a lifetime Member record.
- Matching rules would create a hidden identity-reconciliation subsystem.
- A pending solicitation could be resolved without a human decision.

### Option 3: Insert data through an ad hoc SQL script

Pros:

- Minimal application implementation.
- Direct control over the one-time database write.

Cons:

- Duplicates domain validation and normalization in SQL.
- Makes checksum, safe diagnostics, aggregate invariants, and typed activity
  handling harder to enforce.
- Encourages partial or environment-specific execution.
- Provides no reusable validation action before production mutation.

### Option 4: Use an isolated validated import and explicit later link

Pros:

- Preserves real-world Members without manufacturing Accounts.
- Reuses domain rules and an atomic application transaction.
- Keeps the private source out of migrations and development fixtures.
- Makes identity linkage a deliberate auditable human decision.
- Supports safe validation and idempotent rerun behavior.

Cons:

- The Member-to-Account relationship becomes nullable for a narrow state.
- Lifecycle behavior must explicitly handle Account-less Members.
- Coordinators need an operational process after Account registration.
- A dedicated maintenance entry point and batch provenance model are required.

## Consequences

Positive consequences:

- Real Member data can be imported without creating credentials or Accounts.
- The approved dataset remains private and absent from application packages and
  development fixtures.
- Contact and authentication emails have unambiguous meanings.
- Batch checksum, explicit UUIDs, and atomic collision checks make reruns safe.
- Link concurrency and Role projection remain within the Member lifecycle
  consistency boundary.
- Audit history records one import intention and one later link intention
  without storing personal values.

Negative consequences:

- Existing schema and domain invariants that assume a non-null Account require
  deliberate revision.
- Some active or inactive Members will temporarily have no authorization Role
  projection.
- Coordinator and Oratorio Coordinator designation cannot be granted before
  linking.
- Unlink, mistaken-link repair, and later import cycles require future
  decisions rather than hidden compatibility behavior.

## Related requirements

- [Member Information Import and Account Linking](../requirements/members/member-information-import-and-account-linking.md)
- [Member Information](../requirements/members/member-information.md)
- [Member Records and Lifecycle](../requirements/members/member-records-and-lifecycle.md)
- [Membership Solicitations](../requirements/members/membership-solicitations.md)
- [Development Fixture Policy and Dataset](../requirements/platform/development-fixture-policy-and-dataset.md)
- [Activity Audit Log](../requirements/platform/activity-audit-log.md)

## Related diagrams

- [Member Information Import and Account Linking](../diagrams/member-information/import-and-account-linking.md)

## Related videos

- None.
