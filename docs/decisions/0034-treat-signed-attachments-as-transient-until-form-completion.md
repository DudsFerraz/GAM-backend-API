# ADR-0034: Treat signed attachments as transient until form completion

## Status

Accepted

## Context

Oratoriano signed attachments contain sensitive identity, health, consent, and signature data. ADR-0016 stores their bytes transactionally in PostgreSQL. Before this decision, ADR-0018 and the additional-form requirements treated every uploaded attachment row as soft-deletable history, including files replaced or discarded before form completion.

Incremental draft editing introduces explicit addition and individual removal of files. A discarded draft file never formed part of an official completed form, yet preserving its bytes indefinitely consumes database and backup storage and retains sensitive working material without protecting trusted evidence.

The system needs one explicit boundary between transient upload work and historical signed-form evidence.

## Decision

Treat a signed attachment as transient while its owning form is a `DRAFT` and as historical evidence when that draft completes successfully.

Physically delete transient attachment rows and bytes when they are discarded through individual removal, successful full-collection replacement, or soft deletion of their owning draft. These draft attachment mutations emit no high-level attachment activity. Active rows continue to record trusted creation actor and timestamp so attachments that survive completion retain their upload attribution.

Completion promotes the active collection into the immutable historical form version. Attachments belonging to `COMPLETED`, `SUPERSEDED`, or `REVOKED` forms remain preserved and unavailable to ordinary mutation or deletion.

This is an owning-domain exception to the general physical-deletion boundary in `REQ-PERSISTENCE-009`. It does not change soft deletion of the draft form or its print snapshots, the high-level form lifecycle activities, protected-download auditing, or developer-maintenance controls for historical records.

Physical deletion means removal from the live relational dataset. It does not claim guaranteed erasure from PostgreSQL transaction logs, backups, earlier snapshots, or physical media.

## Alternatives considered

### Option 1: Preserve every discarded draft attachment through soft deletion

Pros:

- Retains row-level history of every uploaded file.
- Matches the previous uniform soft-delete behavior.
- Requires no retention-rule change.

Cons:

- Retains sensitive bytes that never became official evidence.
- Database and backup storage grow with abandoned, replaced, and corrected draft uploads.
- Individual removal appears successful to users while the discarded bytes remain durably stored.

### Option 2: Hard-delete only files removed through the new individual endpoint

Pros:

- Minimizes the immediate endpoint change.
- Removes bytes when a user explicitly selects one file for deletion.

Cons:

- Gives equivalent user outcomes different retention behavior.
- Full replacement would preserve files that individual deletion would erase.
- Draft deletion would preserve all uploaded bytes despite the draft never becoming official.

### Option 3: Treat all draft attachments as transient until completion

Pros:

- Establishes one clear historical boundary.
- Applies consistent storage behavior to individual removal, replacement, and draft deletion.
- Preserves only the attachment collection that becomes trusted evidence.
- Reduces retained sensitive working data and database growth.

Cons:

- Discarded draft files cannot be inspected or restored through maintenance.
- Existing requirements, activity registration, fixtures, and tests must change.
- Physical deletion does not remove copies already present in logs, backups, or snapshots.

## Consequences

Positive consequences:

- Draft correction does not accumulate retired binary rows.
- Equivalent discard workflows have identical retention behavior.
- Form completion is the explicit, reviewable transition to historical retention.
- Current attachment attribution survives when the collection becomes official.

Negative consequences:

- The system retains no attachment-level history for discarded draft files.
- Historical analysis can prove the form lifecycle and completed collection but cannot recover draft bytes that were removed earlier.
- The persistence standard requires a narrowly documented lifecycle-dependent exception.
- PostgreSQL vacuuming and backup retention remain operational concerns distinct from live-row deletion.

## Related requirements

- `REQ-ORATORIANO-FORM-UPLOAD-003`
- `REQ-ORATORIANO-FORM-UPLOAD-004`
- `REQ-ORATORIANO-FORM-UPLOAD-006`
- `REQ-ORATORIANO-FORM-002`
- `REQ-ORATORIANO-FORM-003`
- `REQ-ORATORIANO-FORM-019`
- `REQ-ORATORIANO-FORM-021`
- `REQ-PERSISTENCE-009`

## Related diagrams

- None.

## Related videos

- None.
