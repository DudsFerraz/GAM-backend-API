# Requirement: Draft Signed-Attachment Collection Management

## Status

Accepted

## Context

The existing signed-attachment upload replaces a draft's entire active collection. Full replacement remains necessary when a user must correct page order, switch between PDF and image representations, or replace the complete document, but it forces a user who discovers another page later to upload every previously stored page again.

Clients can recover active attachment metadata but cannot safely reconstruct the existing multipart upload without downloading every private file. Draft users therefore need resource operations that add one or more files and remove one selected file without replacing unaffected files.

A draft attachment is transient working material until the form completes. Discarded draft bytes have no independent historical meaning and should not consume durable database storage. The active collection that supports successful completion crosses the historical boundary with the form and must then remain immutable and preserved.

This specification supplements and intentionally changes signed-attachment behavior in the [Oratoriano Additional Forms](oratoriano-additional-forms.md) Requirement Specification and the draft-owned-record exception in the [Persistence Auditing and Soft Delete](../platform/persistence-auditing-and-soft-delete.md) Requirement Specification. The existing full-collection replacement capability remains available.

## Functional requirements

### REQ-ORATORIANO-FORM-UPLOAD-001: Draft attachment collection operations

The signed-attachment API shall expose these draft mutation operations:

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments` | Atomically append one or more files |
| `PUT` | `/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments` | Atomically replace the complete active collection |
| `DELETE` | `/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments/{attachmentId}` | Remove one active file |

Each operation shall require `ORATORIANO_FORM_MANAGE` and shall apply the existing authentication, coarse authorization, target visibility, active-Oratoriano, and mutation-serialization rules.

Each operation shall mutate only a non-deleted `DRAFT`. `COMPLETED`, `SUPERSEDED`, and `REVOKED` attachment collections shall remain immutable through ordinary workflows.

`POST` shall accept one or more files in the multipart `files` part. An empty active collection shall be appendable; the posted files then become the initial active collection.

Valid examples:

- Post one JPEG to create the initial one-page image collection.
- Post two PNG files to append two pages to an active image collection.
- Delete one selected active image while preserving the other active images.
- Use `PUT` to correct the order of the complete image collection.

Invalid examples:

- Post an empty `files` part.
- Mutate the attachment collection of a completed, superseded, or revoked form.

---

### REQ-ORATORIANO-FORM-UPLOAD-002: Append validation, ordering, and response

Before committing an append, the system shall validate every new file and the resulting combination of existing active files plus new files against `REQ-ORATORIANO-FORM-013`.

The resulting active collection shall remain exactly one of:

- one PDF of at most 20 MiB; or
- one to ten ordered JPEG or PNG page images, each at most 8 MiB and together at most 40 MiB.

The append operation shall reject:

- a new file whose declared MIME type does not match its content;
- an unreadable, unsupported, or unparseable new file;
- a PDF appended to any non-empty collection;
- any file appended to an active PDF collection;
- a PDF mixed with image files in the same request; and
- a resulting collection above an accepted count, per-file size, or total-size limit.

A successful image append shall preserve every existing active attachment's identifier, original filename, verified MIME type, byte length, page order, page count, digest, bytes, and creation attribution. New images shall receive consecutive page-order values after the current final page in multipart request order. A PDF posted to an empty collection shall receive page order `1`.

A successful append shall return `201 Created`, set `Location` to the addressed signed-attachment collection URL, and return the complete resulting active collection as an unpaged array ordered by `pageOrder ASC`. Every item shall use the exact metadata representation in `REQ-ORATORIANO-FORM-021` and shall not expose bytes or digests.

Inserting a new page between existing pages and reordering existing pages shall remain full-collection replacement concerns.

---

### REQ-ORATORIANO-FORM-UPLOAD-003: Individual removal and order normalization

`DELETE /oratorianos/{oratorianoId}/forms/{formId}/signed-attachments/{attachmentId}` shall physically delete the addressed active draft attachment.

Deleting an image shall atomically decrement the `pageOrder` of every later active image so that the remaining collection retains consecutive ordering. Deleting the only active PDF or image shall leave an empty active collection, which remains valid draft state but cannot support completion.

Successful removal shall return `204 No Content` and shall require no reason. An attachment that is absent, does not belong to the addressed form, or is no longer active shall return `404 RESOURCE_NOT_FOUND` without revealing another collection.

The operation shall not remove any other active attachment or change retained identifiers, file metadata, bytes, digests, or creation attribution beyond the required page-order normalization.

---

### REQ-ORATORIANO-FORM-UPLOAD-004: Transient draft retention and historical completion boundary

Signed attachments shall be transient while their owning form remains a draft. The system shall physically delete draft attachment rows and bytes when they are discarded through:

- individual attachment removal;
- successful full-collection replacement; or
- soft deletion of the owning draft.

Full replacement shall validate the complete proposed replacement before deleting the prior active collection. A rejected or rolled-back replacement shall preserve the prior active collection unchanged.

Draft soft deletion shall continue to soft-delete the draft and its print snapshots while physically deleting its signed attachments. The existing high-level draft-deletion activity shall remain.

When a draft completes, its active signed-attachment collection shall become historical evidence owned by the immutable form version. Those attachment rows and bytes shall remain preserved through `COMPLETED`, `SUPERSEDED`, and `REVOKED` states and shall not be physically or soft-deleted through ordinary workflows.

Physical deletion of transient draft attachments is removal from the live relational dataset. It shall not be represented as guaranteed erasure from transaction logs, backups, prior snapshots, or physical media.

Rationale:
Files discarded before completion never formed part of the trusted signed record. Retaining their sensitive bytes would consume storage without preserving official evidence. Completion is the explicit boundary at which the active collection gains independent historical meaning.

---

### REQ-ORATORIANO-FORM-UPLOAD-005: Atomicity and concurrent mutation behavior

Each append, individual removal, and full replacement shall produce one atomic collection outcome.

If request validation, content validation, persistence, or transaction commit fails:

- no partial append or replacement batch shall commit;
- no requested individual removal or partial reordering shall commit; and
- the previously active attachment collection shall remain unchanged.

Append, individual removal, full replacement, form completion, draft deletion, and other mutations of the same Oratoriano shall use the accepted Oratoriano mutation boundary and re-evaluate the latest committed form and active collection after acquiring that boundary.

Concurrent successful mutations shall therefore produce one serialized ordered collection without lost files, duplicate active page-order values, or partial batches. If another mutation makes the form immutable or unavailable first, the later draft attachment mutation shall fail under the resulting current state.

---

### REQ-ORATORIANO-FORM-UPLOAD-006: Attribution without draft attachment activities

Every active attachment shall retain trusted `createdAt` and `createdBy` attribution. Attachments that survive completion shall preserve that original upload attribution as part of the completed form's historical evidence.

Append, individual removal, and full replacement while the form is a draft shall emit no high-level attachment activity. The existing `ORATORIANO_FORM_ATTACHMENTS_REPLACED` action shall remain readable but shall be marked `Deprecated` in the closed Activity Audit Log registry and shall not be emitted by new workflows. No append or individual-removal action shall be introduced.

Draft creation, structured-data update, print-snapshot creation, draft deletion, form completion, form revocation, protected reads, and attachment downloads shall retain their separately accepted activity behavior.

Rationale:
Draft attachment edits are transient working changes rather than official form events. Row creation attribution identifies the uploader of files that become historical, while form completion records the official lifecycle transition.

---

### REQ-ORATORIANO-FORM-UPLOAD-007: Retry and OpenAPI contract

Append `POST` shall not be idempotent and shall not silently deduplicate files by filename, byte length, or digest. After an ambiguous transport outcome, a client shall recover the active collection through `GET /oratorianos/{oratorianoId}/forms/{formId}/signed-attachments` before deciding whether another append is necessary.

The OpenAPI contract shall use:

- `appendOratorianoFormSignedAttachments` for `POST`; and
- `deleteOratorianoFormSignedAttachment` for `DELETE`.

The operations shall document their draft-only precondition, authorization, validation, ordering, hard-deletion behavior, atomicity, retry behavior where applicable, success response, relationship to full replacement, and expected error outcomes.

Request-token idempotency, collection ETags, and digest-based deduplication shall remain outside this feature.

## Acceptance scenarios

```gherkin
Scenario: Append image pages without replacing existing files
  Given a draft has two active ordered image attachments
  When an authorized user posts two valid image files
  Then the existing attachments retain their identities and metadata
  And the new attachments become pages three and four in request order
  And the response contains the complete four-item active collection
  And no high-level attachment activity is emitted

Scenario: Use append for an initial upload
  Given a draft has no active signed attachments
  When an authorized user posts one valid PDF
  Then the PDF becomes the active collection at page order one
  And the response is 201 Created with the collection Location

Scenario: Reject an invalid append atomically
  Given a draft has an active image collection
  When one requested new file is invalid or the result exceeds an aggregate limit
  Then the complete append request is rejected
  And the previously active collection remains unchanged

Scenario: Delete a middle image from a draft
  Given a draft has four active ordered image attachments
  When an authorized user deletes page two
  Then the selected attachment row and bytes are physically deleted
  And the former pages three and four become pages two and three
  And the response is 204 No Content
  And no high-level attachment activity is emitted

Scenario: Delete the only draft attachment
  Given a draft has one active signed attachment
  When an authorized user deletes that attachment
  Then the attachment row and bytes are physically deleted
  And the draft has an empty active collection
  And completion remains unavailable until a complete attachment is uploaded

Scenario: Replace a draft collection without retaining discarded bytes
  Given a draft has an active signed-attachment collection
  When an authorized user successfully replaces the complete collection
  Then the prior draft attachment rows and bytes are physically deleted
  And only the validated replacement collection remains active
  And no high-level attachment activity is emitted

Scenario: Replacement failure preserves the prior collection
  Given a draft has an active signed-attachment collection
  When replacement validation or persistence fails
  Then the prior collection remains active and unchanged
  And no replacement file is committed

Scenario: Draft deletion removes transient attachment storage
  Given a draft has print snapshots and signed attachments
  When the draft is soft-deleted with a valid reason
  Then the draft and print snapshots are soft-deleted atomically
  And the signed attachment rows and bytes are physically deleted
  And the existing draft-deletion activity is committed

Scenario: Completion establishes historical attachment retention
  Given a draft has a valid active attachment collection
  When the form completes successfully
  Then that collection retains its upload attribution
  And it becomes immutable historical evidence
  And later supersession or revocation does not delete its rows or bytes

Scenario: Serialize append with another collection mutation
  Given append and another attachment mutation target the same draft concurrently
  When one mutation commits first
  Then the other evaluates the resulting latest collection
  And each committed outcome is atomic
  And active page-order values remain unique and consecutive

Scenario: Recover before retrying an ambiguous append
  Given an append response is lost after the server may have committed it
  When the client determines whether to retry
  Then the client first reads the active attachment metadata
  And repeating the POST is not promised to be a no-op
```

## Open questions

* None.

## Out of scope

* Removing the existing full-collection replacement capability.
* Inserting a new page at an arbitrary position or reordering existing attachments through the incremental API.
* Mixing PDF and image representations in one active collection.
* Mutating completed, superseded, revoked, or soft-deleted form attachments.
* Chunked, resumable, or parallel upload protocols for one file.
* Request-token idempotency, collection ETags, or attachment deduplication.
* Guaranteed erasure from transaction logs, backups, prior snapshots, or physical media.
* Changing accepted file formats, count limits, size limits, private database storage, protected download auditing, or completed-form retention.

## Related ADRs

* [ADR-0016: Store signed Oratoriano form attachments in PostgreSQL](../../decisions/0016-store-signed-oratoriano-form-attachments-in-postgresql.md)
* [ADR-0017: Serialize Oratorio and Oratoriano mutations](../../decisions/0017-serialize-oratorio-and-oratoriano-mutations.md)
* [ADR-0018: Standardize persistence auditing, soft deletion, and relationship enforcement](../../decisions/0018-standardize-persistence-auditing-soft-deletion-and-relationship-enforcement.md)
* [ADR-0034: Treat signed attachments as transient until form completion](../../decisions/0034-treat-signed-attachments-as-transient-until-form-completion.md)

## Related requirements

* [Oratoriano Additional Forms](oratoriano-additional-forms.md)
* [Activity Audit Log](../platform/activity-audit-log.md)
* [Persistence Auditing and Soft Delete](../platform/persistence-auditing-and-soft-delete.md)
* [API Error and Authorization Contract](../platform/api-error-and-authorization-contract.md)
* [OpenAPI and Frontend API Documentation](../platform/openapi-and-frontend-api-documentation.md)

## Related videos

* None.
