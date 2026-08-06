# Requirement: GamName

## Status
Accepted

## Context
GAM stores personal names in multiple features, including Members and Oratorianos. Name handling must be consistent so that each feature does not invent its own validation, terminology, and normalization rules.

The `GamName` primitive defines the valid shape of a present personal name. Whether a feature requires a name, allows it to be absent, or displays it in a specific context belongs to that feature's Requirement Specification.

Canonical letter casing is governed by `REQ-GAM-NAME-008` in the accepted
[GamName Canonical Capitalization](gam-name-capitalization.md) specification.

## Ubiquitous Language
- `separator`: A single internal space, hyphen, or apostrophe used between letters.

## Functional requirements

### REQ-GAM-NAME-001: Required components
The `GamName` primitive shall require both `firstName` and `surname` when a `GamName` value is created.

Rationale:
A present personal name must be complete enough to identify a person consistently in GAM records.

Valid examples:
- `firstName = "Ana"`, `surname = "Silva"`
- `firstName = "Al"`, `surname = "Ng"`

Invalid examples:
- `firstName = null`, `surname = "Silva"`
- `firstName = "Ana"`, `surname = null`
- `firstName = ""`, `surname = "Silva"`
- `firstName = "Ana"`, `surname = "   "`

---

### REQ-GAM-NAME-002: Canonical component names
The `GamName` primitive shall use `firstName` and `surname` as its canonical component names.

Rationale:
Using one vocabulary prevents `surname`, `lastName`, and similar labels from drifting into separate concepts.

Valid examples:
- Documentation refers to the family-name component as `surname`.
- APIs and DTOs that expose the primitive use `surname` when naming the family-name field.

Invalid examples:
- A new Requirement Specification introduces `lastName` for the same component.
- A new API shape exposes both `surname` and `lastName` for the same value.

---

### REQ-GAM-NAME-003: Length limits
The `GamName` primitive shall require each component to contain at least 2 Unicode letters.

The `GamName` primitive shall reject `firstName` values longer than 32 characters.

The `GamName` primitive shall reject `surname` values longer than 64 characters.

Rationale:
The system needs simple and explicit data-quality bounds for personal names. Any future exception for one-letter names should be a deliberate requirement change.

Valid examples:
- `firstName = "Al"`, `surname = "Ng"`
- `firstName` with 32 characters
- `surname` with 64 characters

Invalid examples:
- `firstName = "A"`, `surname = "Silva"`
- `firstName = "A-"`, `surname = "Silva"`
- `firstName` with 33 characters
- `surname` with 65 characters

---

### REQ-GAM-NAME-004: Allowed characters
The `GamName` primitive shall accept Unicode letters and single internal separators.

The `GamName` primitive shall reject digits, symbols, emoji, punctuation outside the allowed separators, repeated separators, leading separators, trailing separators, and separator combinations.

Rationale:
GAM needs to support real names with common separators while rejecting input that is not a personal name.

Valid examples:
- `firstName = "Joao Pedro"`, `surname = "Silva"`
- `firstName = "Ana"`, `surname = "Oliveira-Santos"`
- `firstName = "Lia"`, `surname = "D'Avila"`

Invalid examples:
- `firstName = "Maria1"`
- `firstName = "12345"`
- `firstName = "Ana."`
- `firstName = "Ana, Maria"`
- `firstName = "Maria  Clara"`
- `firstName = "Maria--Clara"`
- `firstName = "Maria''Clara"`
- `firstName = "Maria -Clara"`
- `firstName = "-Maria"`
- `surname = "Silva-"`

---

### REQ-GAM-NAME-005: Strict input handling
The `GamName` primitive shall reject leading and trailing whitespace in either component.

The `GamName` primitive shall not silently trim, collapse, or otherwise reshape ordinary user input that violates name rules.

Rationale:
Strict rejection makes data-quality problems visible to the owning feature instead of storing silently altered names.

Valid examples:
- `firstName = "Maria"`, `surname = "Silva"`

Invalid examples:
- `firstName = " Maria"`, `surname = "Silva"`
- `firstName = "Maria "`, `surname = "Silva"`
- `firstName = "Maria"`, `surname = " Silva "`

---

### REQ-GAM-NAME-006: Equivalent representation normalization
The `GamName` primitive may normalize equivalent Unicode representations and typographic variants of allowed separators to their canonical stored representation before validation completes.

Rationale:
Equivalent character representations should not create different stored values when they mean the same name.

Valid examples:
- A decomposed accented letter is normalized to its composed Unicode form.
- A typographic apostrophe is normalized to `'`.
- A typographic dash is normalized to `-`.

Invalid examples:
- Normalization changes `Maria  Clara` into `Maria Clara`.
- Normalization changes ` Maria` into `Maria`.

---

### REQ-GAM-NAME-007: Full name rendering
The `GamName` primitive shall render its full-name form as `firstName`, one space, then `surname`.

Rationale:
A common full-name rendering prevents each feature from inventing its own simple display format.

Valid examples:
- `firstName = "Ana"`, `surname = "Silva"` renders as `Ana Silva`.

Invalid examples:
- `Silva, Ana`
- `Ana  Silva`

## Acceptance scenarios

```gherkin
Scenario: Accept name with supported separators
  Given the first name is "Joao Pedro"
  And the surname is "Oliveira-Santos"
  When a GamName value is created
  Then the creation succeeds
  And the full-name form is "Joao Pedro Oliveira-Santos"

Scenario: Reject a name with accidental whitespace
  Given the first name is " Maria"
  And the surname is "Silva"
  When a GamName value is created
  Then the creation fails

Scenario: Reject a name with repeated separators
  Given the first name is "Maria--Clara"
  And the surname is "Silva"
  When a GamName value is created
  Then the creation fails
```

## Open questions

* None.

## Out of scope

* Honorifics, titles, social names, nicknames, and preferred names.
* Name deduplication, matching, searching, or ordering.
* Feature-specific decisions about whether a name is required.

## Related ADRs

* None.

## Related requirements

* [GamName Canonical Capitalization](gam-name-capitalization.md)

## Related videos

* None.
