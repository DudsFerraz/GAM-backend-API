# GAM ubiquitous language

## Purpose

This document defines project-wide GAM domain language. Use it to keep terminology consistent across Requirement Specifications, ADRs, diagrams, APIs, code, tests, and agent handoffs.

This document is not a Requirement Specification. It defines canonical terms, discouraged aliases, and relationships between terms. Business behavior still belongs in Requirement Specifications.

## Canonical terms

| Term               | Definition                                                                                                                                                                                   | Accepted short forms | Aliases to avoid                 |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------|----------------------------------|
| **Oratorio**       | A recurring Salesian activity where people, usually children and teenagers, are welcomed for recreation, friendship, care, and a moment of prayer or reflection.                             | None                 | Oratory                          |
| **Boa Tarde**      | The closing moment of welcome, prayer, reflection, and encouragement during an Oratorio.                                                                                                     | None                 | Good Afternoon                   |
| **Oratoriano**     | A person who attends an Oratorio. Oratorianos are usually between 5 and 20 years old, but adults can also be Oratorianos; an Oratoriano older than 25 is rare but expected at least monthly. | None                 | Oratorio frequenter, attendee    |
| **Member**         | A lifetime member of GAM Piracicaba. A Member may become active or inactive, but does not stop being a Member because of inactivity.                                                         | None                 | Participant                      |
| **Coordinator**    | A Member responsible for coordinating a GAM activity, team, responsibility area, or system capability. System authorization roles may reuse this domain term.                                | coord                | Admin, director                  |
| **Account**        | A persisted identity that can authenticate to GAM. An Account is not automatically a Member, Coordinator, or any other role-bearing domain person.                                           | None                 | User account                     |
| **displayName**    | It is how the user wants to be known. It is not a legal name, personal name, or GamName.                                                                                                     | None                 | name, full name, GamName         |
| **GamName**        | The common primitive for a person's required name components: `firstName` and `surname`.                                                                                                     | None                 | Name, full name                  |
| **firstName**      | The given or personal name component inside a **GamName**.                                                                                                                                   | None                 | first name                       |
| **surname**        | The family name component inside a **GamName**.                                                                                                                                              | None                 | lastName, last name              |
| **GamEmail**       | The common primitive for a normalized email address used by GAM accounts and other email-bearing features.                                                                                   | None                 | Email, MyEmail                   |
| **GamPhoneNumber** | The common primitive for a normalized, dialable phone number.                                                                                                                                | None                 | PhoneNumber, MyPhoneNumber       |
| **UUID**           | The convention that persisted GAM resources use UUID values as public and internal identifiers.                                                                                              | id                   | Numeric ID, database sequence ID |

## Relationships

- A **Member** may serve in an **Oratorio**.
- An **Oratoriano** attends an **Oratorio** but is not necessarily a **Member**.
- A **Boa Tarde** happens as part of an **Oratorio**.
- A **Coordinator** is a **Member** with coordination responsibility.
- A **Coordinator** may inactivate a **Member** in the system because of real-life inactivity in GAM actions and events.
- An **Account** may authenticate to GAM and may receive roles or permissions through RBAC workflows.
- An **Account** is not automatically a **Member** or **Coordinator**.
- A **displayName** belongs to an **Account** and must not be treated as a **GamName**.
- A **GamName** is composed of `firstName` and `surname`.
- **UUID** is used to identify persisted resources such as Accounts, Members, Oratorianos, Events, Locations, Roles, and Permissions.

## Usage rules

- Prefer canonical terms when naming domain concepts in documentation, APIs, code, tests, and agent handoffs.
- Use accepted short forms only when the context remains clear. `coord` is accepted as user-facing shorthand and as an informal internal abbreviation for **Coordinator**.
- Treat aliases to avoid as clarification aids, not as competing domain names.
- Feature-specific Requirement Specification `Ubiquitous Language` sections may introduce local terms, but must not repeat or redefine terms, aliases, synonyms, translations, or legacy names already defined here.
- If a term is ambiguous, preserve the ambiguity as an open question in the relevant Requirement Specification or planning handoff until the developer resolves it.

## Example dialogue

> **Dev:** "Should the attendance feature register a participant or an Oratoriano?"
>
> **Domain expert:** "Use **Oratoriano** when the person attends an **Oratorio**. A **Member** is part of GAM, and may serve at the Oratorio."
>
> **Dev:** "Can an adult be an **Oratoriano**?"
>
> **Domain expert:** "Yes. Most Oratorianos are young, but adults can attend too."
