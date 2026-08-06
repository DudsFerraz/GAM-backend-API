# Requirement: GamName Canonical Capitalization

## Status

Accepted

## Context

The accepted `GamName` contract defines required components, supported
characters, strict whitespace handling, equivalent Unicode normalization, and
full-name rendering. It does not yet define canonical letter casing.

Member-information preparation exposed inconsistent casing in real-world name
input. The capitalization rule must belong to the shared `GamName` primitive so
Members, Oratorianos, and later name-bearing features do not normalize the same
person differently.

This specification adds a new accepted rule without changing the meaning or
identity of `REQ-GAM-NAME-001` through `REQ-GAM-NAME-007`.

## Functional requirements

### REQ-GAM-NAME-008: Canonical capitalization

Each `firstName` and `surname` word shall use canonical capitalization:

- the first Unicode letter of an ordinary word shall be uppercase;
- the remaining Unicode letters of that word shall be lowercase;
- each letter segment separated by a hyphen or apostrophe shall follow the
  same uppercase-first, lowercase-remainder rule; and
- the complete internal Portuguese particles `de`, `da`, `do`, `das`, `dos`,
  and `e` shall remain lowercase when they occur after the first word of a
  component.

An ordinary API or domain input whose casing is not already canonical shall be
rejected. The `GamName` primitive shall not silently repair its casing.

A reviewed Developer-maintenance preparation process may convert source text
to canonical capitalization before constructing a `GamName`. The resulting
value shall still satisfy every accepted `GamName` requirement.

Rationale:

One canonical representation improves data quality and stable rendering while
preserving the accepted strict-input boundary for ordinary product workflows.
The reviewed maintenance exception allows a deliberately approved legacy
dataset to be prepared without weakening normal API validation.

Valid examples:

- `firstName = "Eduardo"`, `surname = "Oliveira Ferraz de Campos"`
- `firstName = "Ana-Maria"`, `surname = "D'Avila dos Santos"`
- A reviewed maintenance preparation converts `EDUARDO OLIVEIRA` to
  `Eduardo Oliveira` before constructing the value.

Invalid examples:

- An ordinary API accepts `firstName = "eduardo"` and silently stores
  `Eduardo`.
- `surname = "Oliveira Ferraz De Campos"`
- `firstName = "ANA-MARIA"`

## Acceptance scenarios

```gherkin
Scenario: Accept a canonically capitalized multipart surname
  Given firstName is "Eduardo"
  And surname is "Oliveira Ferraz de Campos"
  When a GamName value is created
  Then creation succeeds

Scenario: Reject noncanonical ordinary input
  Given firstName is "eduardo"
  And surname is "Oliveira"
  When a GamName value is created through an ordinary product workflow
  Then creation fails
  And the input is not silently rewritten

Scenario: Normalize a reviewed maintenance source before domain validation
  Given a Developer-approved import source contains "EDUARDO OLIVEIRA"
  When the maintenance preparation converts it to canonical capitalization
  And a GamName value is created from "Eduardo" and "Oliveira"
  Then creation succeeds
```

## Open questions

* None.

## Out of scope

* Locale-specific exceptions beyond the listed internal Portuguese particles.
* Honorifics, initials, titles, preferred names, or name deduplication.
* Silent casing repair in ordinary APIs.

## Related requirements

* [GamName](gam-name.md)
* [Member Information](../members/member-information.md)
* [Member Information Import and Account Linking](../members/member-information-import-and-account-linking.md)

## Related ADRs

* None.

## Related videos

* None.
