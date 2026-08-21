# Requirement: Oratoriano Additional Forms

## Status

Accepted

## Context

An Oratoriano can be registered and attend with only a name. More frequent Oratorianos may later provide a paper-based additional form containing identity, family, contact, health, declarations, image-and-voice authorization, and a handwritten signature.

The form remains optional for participation, but a form represented as completed must be trustworthy, immutable, traceable to the signed paper, and protected as sensitive information.

The browser may lose transient resource identifiers after a reload. Users must still be able to recover the exact immutable print snapshot that identified a document already printed or signed, and the active signed-attachment identifiers required for later downloads, without creating replacement artifacts or storing generated PDF bytes.

## Ubiquitous Language

- `additional form`: One versioned, sensitive information snapshot for an Oratoriano.
- `current completed form`: The only completed version currently valid as the trusted source for form information and authorization.
- `print snapshot`: An immutable capture of one draft revision and template used to render an identified PDF for handwritten confirmation.
- `signed attachment`: The complete PDF scan or ordered page images of the handwritten signed form.
- `signedOn`: The calendar date handwritten beside the signature on the physical form.

## Functional requirements

### REQ-ORATORIANO-FORM-001: Optional versioned form

An Oratoriano shall not require an additional form to register or attend.

An Oratoriano may have multiple form versions. Each version shall use its own UUID v7 and immutable Oratoriano relationship.

Starting transcription shall create a `DRAFT` with origin `PAPER_TRANSCRIPTION`, unless the returned printed form already identifies an existing draft UUID. Starting direct system entry shall create a `DRAFT` with origin `DIRECT_SYSTEM_ENTRY`.

The origin catalog shall contain exactly those two values. Actor Account and creation timestamp shall be recorded automatically. `OTHER` origin and operator free-text transcription/document observations shall not be supported.

---

### REQ-ORATORIANO-FORM-002: Lifecycle and current authority

The lifecycle shall allow:

- new form to `DRAFT`;
- `DRAFT` to `COMPLETED`;
- current `COMPLETED` to `REVOKED`; and
- current `COMPLETED` to `SUPERSEDED` automatically when a newer draft completes.

At most one current `COMPLETED` form shall exist per Oratoriano. The complete command shall always target a draft. New completion and prior-current supersession shall commit atomically; no separate supersede command shall exist.

`COMPLETED`, `SUPERSEDED`, and `REVOKED` versions shall be immutable and non-deletable through ordinary workflows. `SUPERSEDED` and `REVOKED` are terminal. A `DRAFT` may be soft-deleted with a required reason.

Draft soft deletion shall atomically soft-delete its print snapshots and physically delete its transient signed-attachment rows and bytes under `REQ-ORATORIANO-FORM-UPLOAD-004`. Those dependent records shall disappear from ordinary reads and downloads. Print snapshots shall remain preserved for audit and future retention decisions and shall not be restored automatically; physically deleted draft attachments shall not be restorable.

After revocation, no form is current until another draft completes.

---

### REQ-ORATORIANO-FORM-003: Draft flexibility and completion validation

A draft may contain any subset of fields and may be saved repeatedly.

Completion shall validate the complete field matrix, conditional rules, required declarations, signed attachment, print-snapshot relationship, and current Oratoriano identity in one transaction. A failure shall leave the form as a draft and shall not partially synchronize the ordinary profile.

No second-person review or approval step shall be required. The same authorized actor may enter, upload, and complete the form. Form lifecycle operations and retained attachment uploads shall preserve their accepted actor and timestamp attribution. Transient draft attachment mutations shall follow the no-activity rule in `REQ-ORATORIANO-FORM-UPLOAD-006`.

---

### REQ-ORATORIANO-FORM-004: Oratoriano identity and address fields

A completed form shall contain:

- valid `GamName`;
- required `birthDate`;
- required `GamCPF`;
- optional `GamRG`;
- required address line;
- required textual address number, allowing values such as `s/n`, `12A`, or `150 fundos`;
- required neighborhood;
- required CEP;
- required city; and
- personal `GamPhoneNumber` according to `REQ-ORATORIANO-FORM-006`.

Text fields shall be trimmed and reject blank required values. Address line, neighborhood, and city shall each contain at most 255 characters. Address number shall contain at most 100 characters.

CEP shall accept formatted or eight-digit input, store eight digits, and perform no address-authenticity or postal lookup. CPF shall reuse the accepted `GamCPF` behavior. RG shall reuse `GamRG`; a person identified only by CIN shall use CPF and shall not duplicate it into RG.

---

### REQ-ORATORIANO-FORM-005: Minor and adult classification

Minor/adult status shall be calculated from `birthDate` on `signedOn` and shall remain an immutable historical fact of the completed version.

For an Oratoriano under 18 on `signedOn`:

- `schoolName` and `schoolGrade` are required normalized text;
- the responsible relationship cannot be `SELF`; and
- the minor's personal phone is optional.

For an Oratoriano aged 18 or older:

- `schoolName` and `schoolGrade` are optional;
- `SELF` is allowed; and
- a self-responsible Oratoriano's phone is required.

School name shall contain at most 255 characters and school grade at most 100 characters after trimming. No school registry or controlled grade catalog shall be introduced initially.

---

### REQ-ORATORIANO-FORM-006: Responsible person

Every completed form shall identify one responsible person who affirms being at least 18.

The relationship catalog shall contain exactly:

- `SELF`;
- `MOTHER`;
- `FATHER`;
- `RELATIVE`; and
- `REFERENCE_ADULT`.

`RELATIVE` and `REFERENCE_ADULT` shall require a normalized text complement of at most 100 characters specifying the relationship. Other values shall reject that complement.

The responsible snapshot shall contain valid `GamName`, `GamCPF`, required `GamPhoneNumber`, optional `GamEmail`, and relationship. When relationship is `SELF`, name, CPF, and phone shall be derived from the adult Oratoriano's form fields rather than entered twice.

---

### REQ-ORATORIANO-FORM-007: Optional parent snapshots

Father and mother snapshots are independently optional except that:

- relationship `MOTHER` requires a mother snapshot derived from the responsible person's name and CPF; and
- relationship `FATHER` requires a father snapshot derived from the responsible person's name and CPF.

The other parent snapshot remains optional. For `SELF`, `RELATIVE`, or `REFERENCE_ADULT`, both parent snapshots are optional.

Whenever a parent snapshot exists, both `GamName` and `GamCPF` are required. A CPF shall not exist without that parent's name.

---

### REQ-ORATORIANO-FORM-008: Structured health answers

Each completed form shall answer every structured health question using exactly `YES`, `NO`, or `NOT_INFORMED`:

- medical follow-up;
- physical-activity restriction;
- medicine use;
- allergies;
- convulsions;
- frequent fainting;
- heart condition; and
- other health condition.

`YES` shall require the corresponding explanation where the question requests one. `NO` and `NOT_INFORMED` shall reject a contradictory explanation. Each health explanation and medicine instruction shall contain at most 2,000 characters after trimming.

Medicine use may additionally contain optional important instructions. One optional general other-care text of at most 5,000 characters may record information necessary for the Oratoriano's care and safety.

---

### REQ-ORATORIANO-FORM-009: Required declarations and authorization

Completion shall require affirmative confirmation that:

- the signer is the adult Oratoriano or has the recorded relationship;
- the supplied information is true;
- health information is true and current to the signer's knowledge;
- the signer understands how the information will be used;
- the signer reviewed the form; and
- the signer accepts the image-and-voice authorization.

Image-and-voice authorization is mandatory for a valid completed form. A refusal, missing answer, or undecided answer shall keep the form as a draft.

If authorization is later withdrawn, the current completed form shall be revoked with an effective system timestamp, actor, and required reason. The immutable signed version shall remain historical but shall no longer authorize future reliance.

---

### REQ-ORATORIANO-FORM-010: Signed date

`signedOn` shall be required for completion and shall match the date visible beside the handwritten signature on the attached document, as confirmed by the completing actor.

It may precede system transcription or upload, shall not be later than the current date in `America/Sao_Paulo`, and shall have no artificial past limit.

`signedOn` determines minor/adult status and the effective date used for ordinary-profile synchronization. Signing location shall be fixed PDF text `Piracicaba` and shall not be stored as an editable field. A physical-document `receivedOn` field is out of scope.

---

### REQ-ORATORIANO-FORM-011: Print snapshots and generated PDF modes

The system shall generate a print-ready PDF in two modes:

- an identified blank form for paper completion and later transcription; and
- a prefilled snapshot of the current draft for review and signature after direct system entry.

Creating a print snapshot shall immutably capture the draft revision, rendered structured data, and template version. The system shall render disposable PDF bytes from that snapshot and return them without storing the PDF bytes.

Every PDF shall contain the Oratoriano identity, form UUID, print-snapshot UUID, generation timestamp, mode, all applicable fields and declarations, fixed signing location, full signature field, and page number on every page.

Confirmation checkboxes shall become handwritten initials fields beside each declaration and authorization. One full handwritten signature field shall appear at the end.

A print snapshot shall always belong to a draft. Generating an identified blank form creates or uses its `PAPER_TRANSCRIPTION` draft. Generating a prefilled form creates or uses its `DIRECT_SYSTEM_ENTRY` draft.

---

### REQ-ORATORIANO-FORM-012: Print-snapshot correspondence

For `DIRECT_SYSTEM_ENTRY`, the signed attachment used for completion shall correspond to the latest generated prefilled print snapshot. Editing the draft after generation shall invalidate that snapshot and require a new print snapshot, generated PDF, and signature.

For `PAPER_TRANSCRIPTION`, later transcription of an identified blank form shall not invalidate the print snapshot because handwriting is the source. Completion shall still require the form UUID, print-snapshot UUID, all declared pages, structured transcription, and required signatures/initials.

Each print snapshot shall contain its UUID, form UUID, draft revision, mode, generation timestamp, template version, page count, captured structured data, and a fingerprint of that data. The snapshot UUID shall appear on every generated page.

Generated PDF bytes shall be disposable renderings and shall not be persisted. A PDF may be regenerated from its immutable print snapshot. A draft or template change shall create a new snapshot UUID rather than changing an existing snapshot.

---

### REQ-ORATORIANO-FORM-013: Complete signed attachment

Completion shall require one complete signed attachment in exactly one of these forms:

- one PDF of at most 20 MiB; or
- one to ten ordered JPEG or PNG page images, each at most 8 MiB and together at most 40 MiB.

Every declared page shall be present. A cropped signature image is insufficient.

The system shall validate declared MIME type against file content, apply the size and page-count limits, and record original filename, verified MIME type, byte length, page order, and SHA-256 digest.

OCR, handwriting recognition, and automatic legibility judgment are out of scope. The authorized completing actor is responsible for confirming that the complete document is legible and matches the structured data.

---

### REQ-ORATORIANO-FORM-014: Private binary storage

Signed attachment bytes and metadata shall be persisted transactionally in PostgreSQL private binary storage according to ADR-0016.

No public URL, filesystem path, or unauthenticated download location shall represent an attachment. Metadata and binary persistence shall commit together.

The system shall return attachment bytes only through an authorized, audited download operation and shall not expose binary data in form search or history responses.

---

### REQ-ORATORIANO-FORM-015: Permissions and sensitive-read auditing

The permission catalog shall define:

| Permission | Capability |
| --- | --- |
| `ORATORIANO_FORM_GET` | Read sensitive form detail |
| `ORATORIANO_FORM_MANAGE` | Create, edit, complete, revoke, and soft-delete drafts; add, replace, and remove draft attachments |
| `ORATORIANO_FORM_PDF_GENERATE` | Generate identified printable PDFs |
| `ORATORIANO_FORM_ATTACHMENT_GET` | Download signed attachments |

Baseline `COORD` and `ORATORIO_COORD` shall receive all four. Baseline `MEMBER` and `VISITOR` shall receive none.

Every form-detail read, print-snapshot creation, generated-PDF rendering/download, and signed-attachment download shall emit a sensitive-read audit containing actor Account, Oratoriano UUID, form UUID, action, and timestamp. Print operations shall also identify the print-snapshot UUID. The audit shall never copy sensitive field values or bytes.

For a form-detail read, generated-PDF rendering/download, or signed-attachment download, the required activity shall commit before any protected form details, PDF bytes, or attachment bytes are returned. If activity validation or persistence fails, the operation shall return none of that protected content.

Print-snapshot creation and its activity shall commit atomically before the created snapshot response or rendered content is returned. If its activity cannot commit, snapshot creation shall roll back and no protected response or content shall be returned.

Ordinary Oratorio, attendance, Oratoriano search, and metadata-history reads shall not emit sensitive-read audits.

---

### REQ-ORATORIANO-FORM-016: Metadata-only history

Form history and search responses shall expose only:

- form UUID and version;
- lifecycle status;
- `signedOn`;
- origin;
- creation, completion, and revocation actors and timestamps;
- whether an attachment exists; and
- attachment page count.

They shall not expose CPF, RG, address, contact, family, health, consent text, attachment filenames, digests, or bytes. Sensitive detail and download operations remain separate and audited.

---

### REQ-ORATORIANO-FORM-017: Completion and profile synchronization

Completion shall make the new version current, supersede any prior current form, and synchronize the ordinary Oratoriano profile under `REQ-ORATORIANO-006`.

If the trusted form name conflicts with another reserved Oratoriano name, completion shall be blocked. The system shall not silently skip the name update or violate uniqueness.

A draft cannot be reassigned to another Oratoriano. A draft started under the wrong record must be soft-deleted and recreated under the correct Oratoriano.

---

### REQ-ORATORIANO-FORM-018: Revocation and retention

Revocation shall require `ORATORIANO_FORM_MANAGE`, a normalized 1-to-2,000-character reason, and one transactional high-level activity. It shall take effect at the recorded system timestamp.

Revocation shall not automatically revert name, birth date, or phone values previously synchronized into the ordinary profile.

Completed, superseded, and revoked forms and their signed attachments shall not be hard-deleted through ordinary workflows. Permanent retention cleanup shall remain outside the application until GAM adopts a formal retention policy.

---

### REQ-ORATORIANO-FORM-019: Route catalog

The additional-form API shall expose:

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/oratorianos/{oratorianoId}/forms` | Create a draft with one accepted origin |
| `GET` | `/oratorianos/{oratorianoId}/forms` | Read paged metadata-only history |
| `GET` | `/oratorianos/{oratorianoId}/forms/{formId}` | Read audited sensitive detail |
| `PUT` | `/oratorianos/{oratorianoId}/forms/{formId}` | Fully replace editable draft data |
| `DELETE` | `/oratorianos/{oratorianoId}/forms/{formId}` | Soft-delete a draft with a required reason |
| `PATCH` | `/oratorianos/{oratorianoId}/forms/{formId}/complete` | Complete the draft and automatically supersede any prior current form |
| `PATCH` | `/oratorianos/{oratorianoId}/forms/{formId}/revoke` | Revoke the current completed form with a required reason |
| `POST` | `/oratorianos/{oratorianoId}/forms/{formId}/print-snapshots` | Create immutable print-snapshot data and return `201 Created` |
| `GET` | `/oratorianos/{oratorianoId}/forms/{formId}/print-snapshots` | Read paged recoverable print-snapshot metadata |
| `GET` | `/oratorianos/{oratorianoId}/forms/{formId}/print-snapshots/{printSnapshotId}/pdf` | Render and download the disposable PDF |
| `POST` | `/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments` | Atomically append one or more files to a draft's active collection |
| `PUT` | `/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments` | Atomically replace a draft's complete PDF or ordered image collection |
| `GET` | `/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments` | Read the active signed-attachment collection's metadata |
| `GET` | `/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments/{attachmentId}` | Download one stored file with sensitive-read auditing |
| `DELETE` | `/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments/{attachmentId}` | Physically remove one active draft attachment and normalize image order |

The print-snapshot mode shall derive from the immutable form origin; clients shall not choose a conflicting mode. Completing a form shall make the signed-attachment collection immutable.

---

### REQ-ORATORIANO-FORM-020: Recoverable print-snapshot metadata

`GET /oratorianos/{oratorianoId}/forms/{formId}/print-snapshots` shall return a GAM-owned paged response containing every non-deleted print snapshot that belongs to the addressed non-deleted form, including snapshots from older draft revisions.

Each item shall contain exactly:

- `id`;
- `draftRevision`;
- `mode`;
- `generatedAt`;
- `templateVersion`; and
- `pageCount`.

The metadata response shall not expose captured structured data, the data fingerprint, generated PDF bytes, or render-result filename, MIME type, or byte length. An empty result shall be represented as an empty page.

Paging shall follow `REQ-OPENAPI-007`. The only client-selectable sort field shall be `generatedAt`; the default order shall be `generatedAt DESC`, with snapshot UUID descending as the stable tie-breaker. Unknown sort fields or directions and a requested size above 100 shall return `400 Bad Request`.

The operation shall require `ORATORIANO_FORM_PDF_GENERATE`. It shall be available for non-deleted forms in `DRAFT`, `COMPLETED`, `SUPERSEDED`, and `REVOKED` state. Reading this metadata shall not emit a sensitive-read activity; rendering the selected snapshot's PDF remains separately authorized and audited under `REQ-ORATORIANO-FORM-015`.

Rationale:
Creating another snapshot would produce a different identified document. Recovering existing metadata allows a client that lost transient state to render the exact snapshot already printed or signed, while preserving disposable PDF rendering.

---

### REQ-ORATORIANO-FORM-021: Recoverable signed-attachment metadata

`GET /oratorianos/{oratorianoId}/forms/{formId}/signed-attachments` shall return the active signed-attachment collection for the addressed non-deleted form as an unpaged array ordered by `pageOrder ASC`. An absent collection shall return an empty array.

Each item shall contain exactly:

- `id`;
- `originalFilename`;
- `verifiedMimeType`;
- `byteLength`;
- `pageOrder`; and
- `pageCount`.

Successful `POST` and `PUT /oratorianos/{oratorianoId}/forms/{formId}/signed-attachments` responses shall use that same item representation for the complete resulting active collection. None of these operations shall expose the SHA-256 digest or attachment bytes in their JSON responses.

The list operation shall require `ORATORIANO_FORM_ATTACHMENT_GET`. It shall be available for non-deleted forms in `DRAFT`, `COMPLETED`, `SUPERSEDED`, and `REVOKED` state. Reading this metadata shall not emit a sensitive-read activity; downloading a selected attachment remains separately authorized and audited under `REQ-ORATORIANO-FORM-015`.

Attachments discarded while their form is a draft shall be physically deleted according to `REQ-ORATORIANO-FORM-UPLOAD-004`. Attachments that survive completion shall remain preserved with their immutable form version and shall continue to appear in ordinary metadata lists and authorized downloads.

Rationale:
The upload response is transient. Recovering the same active identifiers after a client reload allows later download of the previously uploaded files without replacing the collection.

---

### REQ-ORATORIANO-FORM-022: Typed structured form data response

The `data` property of `FormRDTO` shall use `FormDraftDTO` as its API and OpenAPI schema rather than an untyped object or map. This shall apply consistently to create, draft-replacement, and sensitive-detail responses.

Fields inside `FormDraftDTO` shall remain nullable so a `DRAFT` can represent any accepted partial subset under `REQ-ORATORIANO-FORM-003`. The typed response shall preserve the stored structured values and shall not add validation requirements to ordinary draft reads.

Rationale:
The frontend must be able to regenerate a type-safe client and repopulate the editable form without treating every structured value as `unknown`.

---

### REQ-ORATORIANO-FORM-023: Human-readable form actor references

When present, `createdBy`, `completedBy`, and `revokedBy` actor references in additional-form detail and metadata-history responses shall contain exactly:

- `id`, the stable Account UUID; and
- `displayName`, the Account's current `displayName` at response time.

The UUID shall remain available for identity and traceability, while product interfaces shall use `displayName` as the human-readable label. Changing an Account's `displayName` shall change the label returned for earlier form actions without changing their actor UUID. The form module shall not persist a historical copy of `displayName` for those actions.

Rationale:
An actor UUID alone does not support labels such as “Created by Maria”, while a stable identifier remains necessary to distinguish Accounts.

## Acceptance scenarios

```gherkin
Scenario: Registration and attendance do not require a form
  Given an Oratoriano has only a valid name
  When the Oratoriano attends an Oratorio
  Then attendance may be recorded
  And no additional form is required

Scenario: Starting transcription creates a draft
  Given a returned paper form has no existing draft UUID
  When an authorized user starts transcription
  Then a PAPER_TRANSCRIPTION draft is created

Scenario: Minor completion requires school and responsible adult
  Given the Oratoriano is under 18 on signedOn
  When completion is requested without school data or a non-SELF adult responsible
  Then completion is rejected

Scenario: Mother relationship derives parent data
  Given the responsible relationship is MOTHER
  When the form is completed
  Then the mother snapshot is present
  And its name and CPF equal the responsible snapshot

Scenario: Image authorization is mandatory
  Given every other required field and attachment is valid
  But image-and-voice authorization is refused or missing
  When completion is requested
  Then the form remains a draft

Scenario: Direct-entry edit invalidates a print snapshot
  Given a prefilled DIRECT_SYSTEM_ENTRY PDF was generated
  When the draft changes before signature
  Then that print snapshot cannot support completion
  And a new print snapshot, PDF, and signature are required

Scenario: Complete form requires the whole signed document
  Given only a cropped signature image was uploaded
  When completion is requested
  Then completion is rejected

Scenario: New completion supersedes the prior current form
  Given one current COMPLETED form exists
  When another valid draft completes
  Then the previous form becomes SUPERSEDED
  And the new form is the only current COMPLETED version

Scenario: Draft deletion removes draft-owned artifacts from ordinary access
  Given a draft has print snapshots and uploaded signed-attachment files
  When the draft is soft-deleted with a valid reason
  Then the draft and print snapshots are soft-deleted atomically
  And the signed-attachment rows and bytes are physically deleted
  And none of those artifacts are available through ordinary reads or downloads

Scenario: Sensitive download is audited
  Given an authorized user downloads a signed attachment
  When the bytes are returned
  Then one sensitive-read audit has already committed and identifies the actor and target UUIDs
  And no sensitive values or bytes are copied into the audit

Scenario: Prefilled PDF rendering is audited
  Given an authorized user renders a prefilled print snapshot
  When the PDF is returned
  Then one sensitive-read audit has already committed and identifies the actor, form, Oratoriano, and print snapshot
  And no form values or PDF bytes are copied into the audit

Scenario: Reload recovers an earlier identified print snapshot
  Given a non-deleted form has print snapshots from multiple draft revisions
  And the client no longer holds the identifier of a previously printed snapshot
  When an authorized user lists the form's print snapshots
  Then the response includes the previously printed snapshot and the newer snapshots
  And the newest generation appears first by default
  And each item contains only the accepted print-snapshot metadata
  And no sensitive-read activity is emitted for the metadata list

Scenario: Reload recovers the active signed-attachment identifiers
  Given a non-deleted form has an active ordered signed-attachment collection
  And the client loses the identifiers returned by the upload
  When an authorized user lists the form's signed attachments
  Then the same active attachment identifiers and accepted metadata are returned in page order
  And replaced attachment records are not returned
  And no sensitive-read activity is emitted for the metadata list

Scenario: Signed-attachment upload and listing share one metadata shape
  Given an authorized user appends or replaces a valid signed-attachment collection
  When the upload response and a later metadata-list response are compared
  Then both represent each active file with id, original filename, verified MIME type, byte length, page order, and page count
  And neither response contains attachment bytes or a digest

Scenario: Form detail exposes typed draft data
  Given a form contains a partial structured draft
  When the API contract and form response are read
  Then FormRDTO data uses the FormDraftDTO schema
  And its nullable structured fields preserve the draft values

Scenario: Form history identifies actors by display name
  Given a form action records an Account actor
  When an authorized user reads additional-form detail or metadata history
  Then the actor reference contains the Account UUID and current displayName
  And no Account email is added to the actor reference

Scenario: Sensitive content is withheld when auditing fails
  Given an authorized user requests form detail, generated PDF bytes, or signed-attachment bytes
  When the required sensitive-read activity cannot commit
  Then no protected form details, PDF bytes, or attachment bytes are returned

Scenario: Revocation does not roll back the ordinary profile
  Given a completed form synchronized profile fields
  When the form is revoked with a valid reason
  Then the form is no longer current or valid for consent
  And the current ordinary profile values are not automatically reverted
```

## Open questions

* None.

## Out of scope

* Making the form mandatory for registration or attendance.
* A second-person review or approval workflow.
* Draft reassignment between Oratorianos.
* `receivedOn` physical-document tracking.
* OCR, handwriting recognition, or automated legibility decisions.
* Reusable guardian, parent, family, household, school, or address entities.
* Object storage, public attachment URLs, or filesystem attachment paths.
* New application-level field/binary encryption, live-volume encryption, or key-management requirements beyond the accepted platform operations contract.
* Automatic rollback of ordinary-profile values after revocation.
* Ordinary deletion of immutable form versions.
* A permanent retention-cleanup policy.
* Persisting generated PDF bytes or render-result filename, MIME type, or byte length.
* Persisting historical copies of Account `displayName` for form actor references.

## Related ADRs

* [ADR-0016: Store signed Oratoriano form attachments in PostgreSQL](../../decisions/0016-store-signed-oratoriano-form-attachments-in-postgresql.md)
* [ADR-0015: Compose Oratorio permission bundles in code](../../decisions/0015-compose-oratorio-permission-bundles-in-code.md)
* [ADR-0017: Serialize Oratorio and Oratoriano mutations](../../decisions/0017-serialize-oratorio-and-oratoriano-mutations.md)
* [ADR-0034: Treat signed attachments as transient until form completion](../../decisions/0034-treat-signed-attachments-as-transient-until-form-completion.md)

## Related requirements

* [Oratoriano Records](oratoriano-records.md)
* [Draft Signed-Attachment Collection Management](incremental-signed-attachment-uploads.md)
* [GamCPF](../common/gam-cpf.md)
* [GamRG](../common/gam-rg.md)
* [GamName](../common/gam-name.md)
* [GamPhoneNumber](../common/gam-phone-number.md)
* [GamEmail](../common/gam-email.md)
* [RBAC Catalog](../rbac/rbac-catalog.md)
* [OpenAPI and Frontend API Documentation](../platform/openapi-and-frontend-api-documentation.md)

## Related videos

* None.
