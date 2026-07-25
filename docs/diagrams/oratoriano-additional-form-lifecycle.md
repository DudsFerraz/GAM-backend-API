# Oratoriano Additional Form Lifecycle

```mermaid
stateDiagram-v2
    [*] --> DRAFT: start transcription or direct entry
    DRAFT --> DRAFT: edit, upload, or create print snapshot
    DRAFT --> Deleted: soft-delete with reason
    DRAFT --> COMPLETED: validate signed document and complete
    COMPLETED --> SUPERSEDED: newer version completes
    COMPLETED --> REVOKED: revoke with reason
    SUPERSEDED --> [*]
    REVOKED --> [*]
    Deleted --> [*]
```

Completion requires all structured and conditional data, affirmative declarations and image-and-voice authorization, a valid `signedOn`, and the complete signed attachment. `COMPLETED`, `SUPERSEDED`, and `REVOKED` are immutable; only `DRAFT` may be soft-deleted.

```mermaid
flowchart TD
    Start["Start form workflow"] --> Origin{"Origin"}
    Origin -- "PAPER_TRANSCRIPTION" --> Blank["Create or resume draft and print identified blank PDF"]
    Blank --> Handwritten["Family completes and signs paper"]
    Handwritten --> Transcribe["Transcribe fields and attach complete document"]
    Origin -- "DIRECT_SYSTEM_ENTRY" --> Enter["Enter draft beside Oratoriano or responsible"]
    Enter --> Prefill["Generate latest prefilled PDF"]
    Prefill --> Signed["Review and sign"]
    Signed --> Attach["Attach complete signed document"]
    Transcribe --> Validate{"Completion valid?"}
    Attach --> Validate
    Validate -- "No" --> Draft["Remain DRAFT"]
    Validate -- "Yes" --> Complete["Become current COMPLETED"]
    Complete --> Prior["Prior current version becomes SUPERSEDED"]
    Complete --> Profile["Synchronize ordinary profile with provenance"]
```

## Related requirements

- [Oratoriano Additional Forms](../requirements/oratorianos/oratoriano-additional-forms.md)
- [Oratoriano Records](../requirements/oratorianos/oratoriano-records.md)

## Related ADRs

- [ADR-0016: Store signed Oratoriano form attachments in PostgreSQL](../decisions/0016-store-signed-oratoriano-form-attachments-in-postgresql.md)
