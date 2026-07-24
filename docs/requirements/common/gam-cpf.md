# Requirement: GamCPF

## Status
Accepted

## Context
GAM may store Brazilian Cadastro de Pessoas Físicas (CPF) numbers in multiple features. CPF handling must be centralized so that input validation, persistence, comparison, and output use one canonical representation.

The `GamCPF` primitive defines the valid shape and canonical representation of a present CPF value. Whether a feature requires, allows, or prohibits a CPF belongs to that feature's Requirement Specification.

## Ubiquitous Language
- `canonical CPF`: The eleven ASCII digits of a CPF, including its two check digits, without punctuation or whitespace.

## Functional requirements

### REQ-GAM-CPF-001: Required present value
The `GamCPF` primitive shall reject null and blank values when a `GamCPF` value is created.

Rationale:
A present CPF primitive must represent an actual CPF value, not absence.

Valid examples:
- `52998224725`
- `529.982.247-25`

Invalid examples:
- `null`
- `""`
- `"   "`

---

### REQ-GAM-CPF-002: Accepted input forms
The `GamCPF` primitive shall trim surrounding whitespace before validation.

After trimming, the primitive shall accept either exactly eleven ASCII digits or the standard `NNN.NNN.NNN-NN` CPF format.

The primitive shall reject partial formatting, internal whitespace, letters, non-ASCII digits, and separators in positions other than those of the standard format.

Rationale:
Supporting the two ordinary CPF representations makes manual entry practical without accepting ambiguous or malformed syntax.

Valid examples:
- `52998224725`
- `529.982.247-25`
- `" 529.982.247-25 "` becomes `52998224725`

Invalid examples:
- `529.982247-25`
- `529 982 247 25`
- `529/982/247-25`
- `5299822472A`
- A value containing fewer or more than eleven digits

---

### REQ-GAM-CPF-003: Canonical representation
The `GamCPF` primitive shall store and expose only the canonical CPF.

Formatted and unformatted inputs that contain the same CPF digits shall produce the same canonical value.

Rationale:
One representation makes persistence, comparison, and API output deterministic.

Valid examples:
- `52998224725` is stored as `52998224725`.
- `529.982.247-25` is stored as `52998224725`.

Invalid examples:
- Storing `529.982.247-25` as the primitive value.
- Treating `52998224725` and `529.982.247-25` as different CPF values.

---

### REQ-GAM-CPF-004: Check-digit validation
The `GamCPF` primitive shall validate both CPF check digits.

For the first check digit, the primitive shall multiply the first nine digits by weights from 10 down to 2, sum the products, and calculate the digit as zero when the sum remainder modulo 11 is less than 2 or as 11 minus that remainder otherwise.

For the second check digit, the primitive shall apply the same rule to the first nine digits and the validated first check digit, using weights from 11 down to 2.

The primitive shall reject a value when either supplied check digit differs from its calculated check digit.

Rationale:
CPF check-digit validation prevents malformed and mistyped values from becoming valid primitive values.

Valid examples:
- `52998224725`
- `529.982.247-25`

Invalid examples:
- `52998224724`
- `529.982.247-24`

---

### REQ-GAM-CPF-005: Repeated-digit rejection
The `GamCPF` primitive shall reject a value whose eleven digits are all identical.

Rationale:
All-identical sequences are not useful CPF values and must not be accepted merely because a basic check-digit calculation produces the same trailing digits.

Invalid examples:
- `00000000000`
- `111.111.111-11`
- `99999999999`

---

### REQ-GAM-CPF-006: Deterministic validation boundary
The `GamCPF` primitive shall validate syntax and check digits without consulting an external registry.

Creating a `GamCPF` value shall not assert that the CPF is registered, has a particular cadastral status, belongs to a person, or was supplied by its owner.

Rationale:
Primitive validation must remain deterministic. Registration status and ownership require separate, authorized workflows and cannot be inferred from a check digit.

Valid examples:
- A syntactically valid, checksum-valid value can create a `GamCPF` without network access.

Invalid examples:
- Rejecting a CPF because the Receita Federal service is unavailable.
- Treating successful primitive creation as proof of ownership.

---

### REQ-GAM-CPF-007: Reusable JPA persistence
The `GamCPF` primitive shall provide reusable JPA persistence support that is not owned or redefined by an individual feature.

A non-null `GamCPF` shall be persisted as its eleven-digit canonical CPF. A database null shall map to a null primitive reference and a null primitive reference shall map to a database null.

Rehydrating a non-null persisted value shall apply the same `GamCPF` validation rules used for ordinary primitive creation. Invalid persisted data shall fail rehydration instead of producing an invalid primitive or being silently treated as absence.

Rationale:
Every persistence-owning feature must store and restore the primitive consistently without introducing a second CPF representation or validation policy.

Valid examples:
- `GamCPF` with value `52998224725` is persisted as `52998224725`.
- Persisted `52998224725` rehydrates to an equal `GamCPF`.
- A null primitive reference and a database null round-trip as null.

Invalid examples:
- Persisting `529.982.247-25`.
- Rehydrating `52998224724` without applying check-digit validation.
- Treating a blank persisted string as null.
- Requiring each CPF-bearing feature to define its own conversion rules.

## Acceptance scenarios

```gherkin
Scenario: Normalize a standard formatted CPF
  Given the raw CPF is " 529.982.247-25 "
  When a GamCPF value is created
  Then the creation succeeds
  And the canonical value is "52998224725"

Scenario: Reject a CPF with an incorrect check digit
  Given the raw CPF is "52998224724"
  When a GamCPF value is created
  Then the creation fails

Scenario: Reject an all-identical CPF
  Given the raw CPF is "111.111.111-11"
  When a GamCPF value is created
  Then the creation fails

Scenario: Reject nonstandard CPF formatting
  Given the raw CPF is "529 982 247 25"
  When a GamCPF value is created
  Then the creation fails

Scenario: Persist and restore a canonical CPF
  Given a GamCPF has canonical value "52998224725"
  When the value is persisted and rehydrated through the shared JPA support
  Then the database value is "52998224725"
  And the rehydrated GamCPF equals the original value

Scenario: Reject an invalid persisted CPF
  Given the persisted CPF value is "52998224724"
  When the value is rehydrated through the shared JPA support
  Then rehydration fails
```

## Reference basis

* [Receita Federal: CPF check-digit calculation](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/manuais/sped/manuais-e-financeira/versoes-anteriores/manual-e-financeira-anexo-v-versao-2-0-leiaute-modulo-de-repasse.pdf)
* [Brazilian Ministry of Social Security: CPF validation rules, including rejection of all-identical digits](https://www.gov.br/previdencia/pt-br/outros/imagens/2015/07/rgrva_RegrasValidacao.pdf)
* [Hibernate Validator: mature Java CPF validation constraint](https://docs.hibernate.org/stable/validator/api/org/hibernate/validator/constraints/br/CPF.html)

## Open questions

* None.

## Out of scope

* Feature-specific CPF requiredness or optionality.
* CPF uniqueness.
* Receita Federal registration or cadastral-status lookup.
* CPF ownership or identity verification.
* Person registries, deduplication, or matching.
* Authorization, masking, logging, and display policies for CPF-bearing features.
* Feature-specific database column names, nullability, indexes, constraints, and schema migrations.

## Related ADRs

* None.

## Related videos

* None.
