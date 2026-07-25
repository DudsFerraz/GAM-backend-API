# ADR-0016: Store signed Oratoriano form attachments in PostgreSQL

## Status

Accepted

## Context

Completed Oratoriano forms require the complete signed paper document as one PDF or ordered page images. These documents contain personal, family, health, consent, and signature data and must remain private, transactional, auditable, and inseparable from their metadata.

The initial deployment does not require an independent object-storage service. The Developer selected PostgreSQL byte arrays for the initial module.

## Decision

Store signed attachment bytes in PostgreSQL `bytea` columns owned by the form attachment model.

Persist each binary and its verified metadata transactionally. Metadata shall include form identity, original filename, verified MIME type, byte length, page order, and SHA-256 digest.

The module shall accept one PDF up to 20 MiB or up to ten ordered JPEG/PNG images, each up to 8 MiB and together up to 40 MiB.

Attachments shall have no public URL or filesystem path. Authorized application operations shall stream them from private database storage, and every download shall create a sensitive-read audit.

## Alternatives considered

### Option 1: Store files on the application filesystem

Pros:

- Simple local file access.
- Keeps large bytes outside ordinary database rows.

Cons:

- Couples data to one server filesystem.
- Makes transactional metadata/binary consistency, backup, deployment, and access control harder.
- Risks exposing paths as identifiers.

### Option 2: Use external object storage

Pros:

- Scales independently for large binary objects.
- Supports mature object lifecycle and streaming capabilities.

Cons:

- Introduces another service, credential boundary, backup policy, and consistency model.
- Is unnecessary for the accepted initial volume and limits.

### Option 3: Store byte arrays in PostgreSQL

Pros:

- Commits metadata and bytes atomically.
- Reuses existing authorization, backup, and transaction boundaries.
- Avoids public paths and an additional service.

Cons:

- Increases database size and backup volume.
- Requires careful streaming and query design to avoid loading binaries in metadata reads.
- May need reconsideration if attachment volume grows substantially.

## Consequences

Positive consequences:

- A completed form cannot reference a missing separately committed file.
- Private access remains behind the same application authorization boundary.
- Backups preserve relational metadata and signed documents together.
- Digest and page-order metadata remain close to the bytes they describe.

Negative consequences:

- PostgreSQL storage and backup growth must be monitored.
- Form history/search queries must exclude binary columns.
- Application memory use must respect the accepted upload/download limits.
- A later move to object storage will require a separate ADR and migration plan.

## Related requirements

- `REQ-ORATORIANO-FORM-013`
- `REQ-ORATORIANO-FORM-014`
- `REQ-ORATORIANO-FORM-015`
- `REQ-ORATORIANO-FORM-016`
- `REQ-ORATORIANO-FORM-018`

## Related diagrams

- [Oratoriano Additional Form Lifecycle](../diagrams/oratoriano-additional-form-lifecycle.md)

## Related videos

- None.
