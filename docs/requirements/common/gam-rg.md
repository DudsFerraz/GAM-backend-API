# Requirement: GamRG

## Status
Accepted

## Context
GAM may need to retain legacy Brazilian Registro Geral (RG) numbers in multiple features. Legacy RG values were issued by identification authorities in Brazilian states and the Federal District and do not have one national format or check-digit rule.

The `GamRG` primitive defines safe, bounded handling for a present legacy RG number without claiming state-specific validation. Whether a feature requires, allows, or prohibits an RG belongs to that feature's Requirement Specification.

The Carteira de Identidade Nacional (CIN) is not an RG-number variant for this primitive. Its national identifier is the CPF and shall be represented by `GamCPF`.

## Ubiquitous Language
- `legacy RG`: An identity number issued under the state or Federal District RG system that preceded the CPF-based national identifier used by the CIN.

## Functional requirements

### REQ-GAM-RG-001: Required present value
The `GamRG` primitive shall reject null and blank values when a `GamRG` value is created.

Rationale:
A present RG primitive must represent an actual textual value, not absence.

Valid examples:
- `12.345.678-9`
- `12.345.678-X`

Invalid examples:
- `null`
- `""`
- `"   "`

---

### REQ-GAM-RG-002: Surrounding-whitespace normalization
The `GamRG` primitive shall trim surrounding whitespace before validation, storage, and exposure.

The primitive shall preserve accepted characters, their case, and internal spacing after surrounding whitespace is removed. It shall not remove punctuation, collapse internal whitespace, or otherwise reformat the value.

Rationale:
Surrounding whitespace is an ordinary input artifact, but state-issued RG representations must not be silently rewritten according to a format that is not nationally uniform.

Valid examples:
- `" 12.345.678-X "` becomes `12.345.678-X`.
- `12 345 678 X` remains `12 345 678 X`.

Invalid examples:
- Storing `" 12.345.678-X "` with the surrounding spaces.
- Silently changing `12.345.678-X` into `12345678X`.

---

### REQ-GAM-RG-003: Bounded textual representation
After surrounding whitespace is removed, the `GamRG` primitive shall require between 1 and 20 characters.

The primitive shall accept ASCII letters, ASCII digits, ordinary internal spaces, periods, hyphens, and forward slashes.

The primitive shall require at least one ASCII letter or digit and shall reject every other character, including tabs, line breaks, control characters, and non-ASCII digits.

Rationale:
A 20-character textual boundary accommodates legacy RG numbers and their ordinary formatting while preventing unbounded or structurally unsafe values. Letters are required for legitimate alphanumeric complements such as `X`.

Valid examples:
- `123456789`
- `12.345.678-X`
- `12 345 678 X`
- `12/345678-9`

Invalid examples:
- A value longer than 20 characters after trimming
- `---`
- `12.345.678-9` followed by a line break
- `12.345.678-😀`

---

### REQ-GAM-RG-004: No state-specific format or check-digit validation
The `GamRG` primitive shall not select or apply a state-specific RG format, length, or check-digit algorithm.

The primitive shall not require an issuing state or issuing authority.

Rationale:
Legacy RG numbers lack one national validation rule. Validating a state-specific rule without authoritative issuer context could reject legitimate identity numbers.

Valid examples:
- `12.345.678-X` may be represented without a state-specific checksum decision.
- A bounded value from a Brazilian state other than Sao Paulo may be represented.

Invalid examples:
- Rejecting a bounded RG solely because it does not satisfy the Sao Paulo check-digit algorithm.
- Assuming an RG was issued in Sao Paulo because GAM operates in that state.

---

### REQ-GAM-RG-005: CIN and CPF boundary
The `GamRG` primitive shall represent only a legacy RG number.

A CPF used as the national identifier of a Carteira de Identidade Nacional shall be represented by `GamCPF`, not by `GamRG`.

The primitive shall not infer whether a numeric value was presented as a legacy RG or as a CIN identifier from its characters alone; the feature collecting the value owns that semantic distinction.

Rationale:
The CIN uses the CPF as its national identifier. Treating that identifier as both `GamCPF` and `GamRG` would create two primitive meanings for the same document number.

Valid examples:
- A feature maps an old state-issued RG number to `GamRG`.
- A feature maps the identifier shown on a CIN to `GamCPF`.

Invalid examples:
- A feature stores a CIN's CPF-based identifier as `GamRG` merely because the CIN is colloquially called an RG.

---

### REQ-GAM-RG-006: Deterministic validation boundary
Creating a `GamRG` value shall not assert that the RG was issued, remains valid, belongs to a person, or was supplied by its owner.

Rationale:
The primitive has neither a national check-digit rule nor registry access. Authenticity and ownership require separate, authorized workflows.

Valid examples:
- A bounded textual legacy RG can create a `GamRG` without network access.

Invalid examples:
- Treating successful primitive creation as proof that the document exists.
- Treating successful primitive creation as identity verification.

---

### REQ-GAM-RG-007: Reusable JPA persistence
The `GamRG` primitive shall provide reusable JPA persistence support that is not owned or redefined by an individual feature.

A non-null `GamRG` shall be persisted as its normalized value after surrounding whitespace has been removed. A database null shall map to a null primitive reference and a null primitive reference shall map to a database null.

Rehydrating a non-null persisted value shall apply the same `GamRG` validation rules used for ordinary primitive creation. Invalid persisted data shall fail rehydration instead of producing an invalid primitive or being silently treated as absence.

Rationale:
Every persistence-owning feature must store and restore the primitive consistently without introducing a second RG representation or validation policy.

Valid examples:
- `GamRG` with value `12.345.678-X` is persisted as `12.345.678-X`.
- Persisted `12.345.678-X` rehydrates to an equal `GamRG`.
- A null primitive reference and a database null round-trip as null.

Invalid examples:
- Persisting surrounding whitespace that the primitive removed.
- Rehydrating an overlong or structurally invalid value without applying `GamRG` validation.
- Treating a blank persisted string as null.
- Requiring each RG-bearing feature to define its own conversion rules.

## Acceptance scenarios

```gherkin
Scenario: Trim and preserve a formatted RG
  Given the raw RG is " 12.345.678-X "
  When a GamRG value is created
  Then the creation succeeds
  And the stored value is "12.345.678-X"

Scenario: Accept a bounded RG without state-specific validation
  Given the raw RG is "12 345 678 X"
  And the value does not exceed 20 characters
  When a GamRG value is created
  Then the creation succeeds
  And no state-specific check digit is required

Scenario: Reject an overlong RG
  Given the raw RG contains more than 20 characters after trimming
  When a GamRG value is created
  Then the creation fails

Scenario: Reject unsafe characters
  Given the raw RG contains a line break
  When a GamRG value is created
  Then the creation fails

Scenario: Persist and restore a normalized RG
  Given a GamRG has normalized value "12.345.678-X"
  When the value is persisted and rehydrated through the shared JPA support
  Then the database value is "12.345.678-X"
  And the rehydrated GamRG equals the original value

Scenario: Reject an invalid persisted RG
  Given the persisted RG value is blank
  When the value is rehydrated through the shared JPA support
  Then rehydration fails
```

## Reference basis

* [Federal guidance: legacy RG issuance by Brazilian states and the Federal District](https://www.gov.br/funai/pt-br/atuacao/povos-indigenas/direitos-sociais/documentacao-civil/carteira-de-identidade-rg)
* [Governo Digital: the CIN uses CPF as its single national identifier](https://www.gov.br/governodigital/pt-br/identidade/identificacao-do-cidadao-e-carteira-de-identidade-nacional)
* [EBSERH patient-system manual: RG represented as text with a 20-character maximum](https://www.gov.br/hubrasil/pt-br/governanca/plataformas-e-tecnologias/aghu/modulos/pacientes-e-prontuario-online/manual-do-usuario/manual-usuario-modulo-pacientes-prontuario-online-aghu.pdf/%40%40download/file)

## Open questions

* None.

## Out of scope

* Feature-specific RG requiredness or optionality.
* Issuing state, issuing authority, issue date, or other document metadata.
* State-specific format and check-digit validation.
* RG uniqueness.
* Document authenticity, validity, or ownership verification.
* CIN representation beyond its CPF-based identifier.
* Person registries, deduplication, or matching.
* Authorization, masking, logging, and display policies for RG-bearing features.
* Feature-specific database column names, nullability, indexes, constraints, and schema migrations.

## Related ADRs

* None.

## Related videos

* None.
