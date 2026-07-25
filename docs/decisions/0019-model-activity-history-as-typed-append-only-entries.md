# ADR-0019: Model activity history as typed append-only entries

## Status

Accepted

## Context

GAM needs activity history that explains meaningful business and security outcomes without duplicating low-level row audit metadata or retaining unnecessary personal and request data.

The activity contract crosses feature boundaries. It must attribute authenticated, anonymous, automated, and Developer actions; identify either one resource or a meaningful resource scope; support action-specific reason rules; correlate HTTP work; and keep metadata safe and interpretable over time.

A generic row containing a nullable Account actor, arbitrary action and target strings, free-text summary, raw request fingerprinting, and unrestricted JSON would be easy to extend but would allow ambiguous actors, synthetic targets, inconsistent reasons, privacy leaks, and undocumented schemas.

## Decision

Model every activity as a typed, append-only entry governed by closed action and target-type registries.

Use discriminated actor attribution:

- `ACCOUNT` with an Account UUID from authenticated security context;
- `ANONYMOUS` with no actor identifier;
- `SYSTEM` with a trusted stable actor reference; or
- `DEVELOPER` with a trusted stable actor reference.

Use discriminated targeting:

- a resource target with a registered domain type and real resource UUID; or
- a scope target with a registered domain type and documented stable scope reference.

Each owning feature Requirement Specification shall define the action's workflow trigger, target assignment, reason mode, and closed metadata schema. The cross-domain Activity Audit Log Requirement Specification shall own the envelope and registry invariants.

Activity metadata shall contain documented identifiers, codes, states, field-name indicators, booleans, counts, and comparable minimized context. User-authored text shall be prohibited by default, but an owning feature may explicitly allow a named bounded snapshot of an already-normalized non-sensitive domain value when it documents the historical purpose and append-only retention implications. The persisted contract shall omit free-text summaries, arbitrary or sensitive personal text, raw network addresses, and User-Agent values.

HTTP correlation shall use an explicitly configured application-generated or trusted-proxy mode. Local development without a proxy shall ignore inbound request identifiers and generate UUID version 7 values. A trusted deployment proxy shall strip client-supplied values before forwarding a validated request identifier.

State-changing activity shall commit atomically with its mutation. Designated sensitive-read activity shall commit before disclosure. Once committed, an activity shall not be edited, soft-deleted, or physically deleted through ordinary application or administration workflows.

## Alternatives considered

### Option 1: Generic free-form activity rows

Pros:

- New actions and metadata require little central coordination.
- Existing implementations can persist arbitrary context.

Cons:

- Action spelling and meaning drift over time.
- Consumers cannot rely on stable metadata schemas.
- Personal data and secrets can be copied accidentally.
- Nullable fields do not clearly distinguish anonymous, missing, System, and Developer actors.
- Generic targets encourage table names and synthetic identifiers.

### Option 2: Database-trigger audit history

Pros:

- Captures writes from every database client.
- Database changes and audit records are naturally atomic.

Cons:

- Records row changes rather than high-level business intent.
- Cannot naturally resolve authenticated actors, feature reason modes, or request correlation.
- Multi-row workflows produce noisy low-level records.
- Designated sensitive reads are invisible.

### Option 3: Mutable administrative activity records

Pros:

- Mistakes can be corrected in place.
- Retention cleanup is simple.

Cons:

- Corrections erase what was originally recorded.
- Ordinary administration can rewrite accountability history.
- Actor deletion can silently remove historical attribution.

### Option 4: Typed append-only entries with feature-owned semantics

Pros:

- The envelope is consistent across domains.
- Feature owners retain responsibility for their own business workflows.
- Actor and target forms are unambiguous.
- Closed metadata schemas support privacy review and stable tests.
- Append-only corrections preserve history.

Cons:

- New actions and target types require coordinated documentation changes.
- Existing free-form schemas and foreign keys may require migration.
- Display text must be derived from typed data rather than stored as a summary.
- Operational request fingerprinting requires a separate logging policy.

## Consequences

Positive consequences:

- Agent T can derive structural and behavioral tests from one cross-domain contract and the owning feature contract.
- Activity history remains interpretable after resource or actor lifecycle changes.
- Sensitive reads can fail closed when their audit cannot be recorded.
- Local development works without a proxy while production can preserve trusted end-to-end correlation.
- Implementation details cannot silently expand the audit vocabulary.
- Feature owners can preserve necessary human-readable historical identification without opening metadata to arbitrary text.

Negative consequences:

- The current persistence schema and event types may not satisfy the actor, target, request-id, metadata, or append-only contract.
- Actor Account references cannot use a foreign-key design that erases the recorded UUID when an Account is physically deleted.
- Registry changes require explicit review even for small feature additions.
- Every text-snapshot exception requires explicit purpose, schema, sensitivity, length, and retention review.
- A later retention or legal-erasure policy will need a separate decision that reconciles append-only history with applicable obligations.

## Related requirements

- `REQ-ACTIVITY-001`
- `REQ-ACTIVITY-002`
- `REQ-ACTIVITY-003`
- `REQ-ACTIVITY-004`
- `REQ-ACTIVITY-005`
- `REQ-ACTIVITY-006`
- `REQ-ACTIVITY-007`
- `REQ-ACTIVITY-008`
- `REQ-ACTIVITY-009`
- `REQ-ACTIVITY-010`
- `REQ-ACTIVITY-011`
- `REQ-ACTIVITY-012`
- `REQ-ACTIVITY-013`
- `REQ-ACTIVITY-014`
- `REQ-PERSISTENCE-009`
- `REQ-PERSISTENCE-012`
- `REQ-WEB-012`

## Related diagrams

- Inline validation and commit flow in [Activity Audit Log](../requirements/platform/activity-audit-log.md#diagrams)

## Related videos

- None.
