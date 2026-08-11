# GAM ubiquitous language

## Purpose

This document defines project-wide GAM domain language. Use it to keep terminology consistent across Requirement Specifications, ADRs, diagrams, APIs, code, tests, and agent handoffs.

This document is not a Requirement Specification. It defines canonical terms, discouraged aliases, and relationships between terms. Business behavior still belongs in Requirement Specifications.

The English–Portuguese glossary in this document also defines the canonical
Brazilian Portuguese (`pt-BR`) presentation term for GAM-wide concepts. It is
not a general localization catalog: feature-specific interface copy and closed
transport-value mappings remain owned by the frontend feature that presents
them.

## Canonical terms

| Term               | Definition                                                                                                                                                                                   | Accepted short forms | Aliases to avoid                 |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------|----------------------------------|
| **Oratorio**       | A recurring Salesian activity where people, usually children and teenagers, are welcomed for recreation, friendship, care, and a moment of prayer or reflection.                             | None                 | Oratory                          |
| **Boa Tarde**      | The closing moment of welcome, prayer, reflection, and encouragement during an Oratorio.                                                                                                     | None                 | Good Afternoon                   |
| **Lanche**         | The food and beverages distributed to Oratorianos at the Oratorio's 17:00 closing boundary as they leave. It is a closing act, not a scheduled time interval.                                | None                 | Snack interval                   |
| **Gincana**        | Organized recreational games and challenges conducted during a GAM activity.                                                                                                                 | None                 | Competition                      |
| **Oratoriano**     | A person who attends an Oratorio. Oratorianos are usually between 5 and 20 years old, but adults can also be Oratorianos; an Oratoriano older than 25 is rare but expected at least monthly. | None                 | Oratorio frequenter, attendee    |
| **Member**         | A lifetime member of GAM Piracicaba. A Member may become active or inactive, but does not stop being a Member because of inactivity.                                                         | None                 | Participant                      |
| **Account-less Member** | A Member whose identity is not currently linked to an Account. In production, this state may be created only by the accepted one-time Member-information import; the isolated development fixture may also exercise the seam with fictional data. | None | Unregistered user, orphan Member |
| **Member contribution profile** | The Member-owned current collection of fixed and custom areas in which the person can contribute to GAM. | contribution profile | Skills list |
| **Annual Member Information Response** | One immutable set of survey answers associated with exactly one Member and one annual survey cycle. | annual response | Member profile, current Member information |
| **Member Information Import Batch** | The immutable, non-sensitive provenance record for one successfully applied approved Member-information dataset. | import batch | CSV import, seed batch |
| **Membership Solicitation** | An Account's immutable request to become a Member, submitted for Coordinator review. The Account does not become a Member until the solicitation is approved. | solicitation | Membership application |
| **Coordinator**    | An active Member whose linked active Account has the current active `COORD` lifecycle-owned Role. Coordinator designation represents responsibility for coordinating a GAM activity, team, responsibility area, or system capability. | coord                | Admin, director                  |
| **Coordenador do Oratório** | An active Member with current responsibility for Oratorio operations whose linked active Account has the `ORATORIO_COORD` lifecycle-owned Role. | oratorio coord, coordenador de oratório | Oratorio responsible, Mr Oratorio, Oratorio steward |
| **Account**        | A persisted identity that can authenticate to GAM. An Account is not automatically a Member, Coordinator, or any other role-bearing domain person.                                           | None                 | User account                     |
| **User**           | A person who uses the GAM application or appears as an actor in product-facing workflows. Do not use this term for the developer interacting with an LLM agent.                              | None                 | Developer                        |
| **Developer**      | The person working on the GAM project, preparing prompts, making decisions, and interacting with LLM agents. Do not use `User` for this role.                                                | Dev                  | User                             |
| **displayName**    | It is how the User wants to be known. It is not a legal name, personal name, or GamName.                                                                                                     | None                 | name, full name, GamName         |
| **GamName**        | The common primitive for a person's required name components: `firstName` and `surname`.                                                                                                     | None                 | Name, full name                  |
| **firstName**      | The given or personal name component inside a **GamName**.                                                                                                                                   | None                 | first name                       |
| **surname**        | The family name component inside a **GamName**.                                                                                                                                              | None                 | lastName, last name              |
| **GamEmail**       | The common primitive for a normalized email address used by GAM accounts and other email-bearing features.                                                                                   | None                 | Email, MyEmail                   |
| **GamPhoneNumber** | The common primitive for a normalized, dialable phone number.                                                                                                                                | None                 | PhoneNumber, MyPhoneNumber       |
| **GamCPF**         | The common primitive for a checksum-valid Brazilian CPF stored as eleven digits.                                                                                                             | None                 | CPF primitive, GamCpf            |
| **GamRG**          | The common primitive for a bounded textual legacy Brazilian RG number without state-specific validation.                                                                                     | None                 | RG primitive, GamRg              |
| **GamLocation**    | An independently persisted, reusable location that an Event may reference. A GamLocation is normally a physical place; the system catalog also contains the single non-physical **Remote GamLocation**. | None                 | Location                         |
| **Remote GamLocation** | The single system-managed GamLocation with code `REMOTE` and name `Remoto`, used when an Event has no physical venue. It contains no address, coordinates, or meeting URL. | remote location | User-managed remote location, online venue |
| **Event**          | The shared persisted record for a GAM activity, including its identity, time range, GamLocation, type, audience restriction, and lifecycle state.                                            | None                 | Activity record                  |
| **Presence**       | The persisted historical fact that a Member's attendance at an Event was confirmed. At most one active Presence may exist for one Event and Member pair.                                    | None                 | RSVP, planned attendance         |
| **Generic Event**  | An Event created through the common Event workflow because it requires no specialized Oratorio, Missa, or other type-specific data.                                                          | None                 | Generic activity                 |
| **UUID**           | The convention that persisted GAM resources use UUID values as public and internal identifiers.                                                                                              | id                   | Numeric ID, database sequence ID |
| **system reference data** | Application-owned persisted records that an Accepted Requirement Specification declares mandatory in every applicable runtime environment and identifies through stable domain keys. | None | Seed data when ownership or environment is unclear |
| **database enum mirror** | A PostgreSQL enum type that constrains a persisted closed value catalog and exactly mirrors the catalog owned by an Accepted Requirement Specification. | None | Database-owned enum catalog |
| **lifecycle-owned Role** | A system Role whose assignment is controlled exclusively by its owning Member-domain workflow. The current catalog contains `MEMBER`, `VISITOR`, `COORD`, and `ORATORIO_COORD`. | None | None |
| **Proxy** | GAM's public HTTP entry point that terminates TLS, serves the static frontend, routes `/api` requests to the private backend, and preserves trustworthy public request information. | None | Caddy or Nginx when no product has been selected |
| **Canonical Public Origin** | The one configured scheme, host, and effective port from which the GAM browser frontend and public API are served. | public origin | domain when scheme or port also matters |
| **row audit metadata** | Low-level persisted creation, latest non-deletion update, and deletion timestamps and actor identifiers. It describes row state and does not replace an activity entry that records business or security intent. | None | activity log, action history |
| **activity entry** | An immutable append-only record of one meaningful business or security outcome, designated sensitive read, or exceptional Developer-maintenance operation. | activity | row audit, database-write log |

## English–Portuguese glossary

The Portuguese entries below are canonical base terms for Brazilian Portuguese
product presentation. Interface copy may apply ordinary Portuguese inflection,
pluralization, articles, and sentence capitalization without creating a new
domain term. Transport names, enum values, permission codes, identifiers, and
source-code symbols remain unchanged at the API boundary.

| Canonical GAM term | Canonical `pt-BR` term | Scope and usage |
| --- | --- | --- |
| **Oratorio** | **Oratório** | User-facing domain term. Keep `Oratorio` in code and transport identifiers. |
| **Boa Tarde** | **Boa Tarde** | User-facing cultural term; do not translate it literally. |
| **Lanche** | **Lanche** | User-facing cultural term. |
| **Gincana** | **Gincana** | User-facing cultural term. |
| **Oratoriano** | **Oratoriano** | User-facing person term. Use **Oratorianos** as the regular plural. |
| **Member** | **Membro** | User-facing person term. |
| **Account-less Member** | **Membro sem conta vinculada** | User-facing person state. It does not mean that the Member is incomplete or inactive. |
| **Member contribution profile** | **perfil de contribuição do membro** | User-facing capability-profile term. |
| **Annual Member Information Response** | **resposta anual de informações do membro** | Protected annual-response term. |
| **Member Information Import Batch** | **lote de importação de informações de membros** | Technical provenance term; not ordinary interface copy. |
| **Membership Solicitation** | **Solicitação para se tornar membro** | Use the complete expression when the kind of solicitation is not already clear from the surrounding context; **solicitação** is acceptable after that context is established. |
| **Coordinator** | **Coordenador** | Refers to the person. **Coordenação** may describe the corresponding responsibility or access presentation, but must not replace the person term. |
| **Coordenador do Oratório** | **Coordenador do Oratório** | User-facing person term. **Coordenação do Oratório** describes the responsibility or access presentation. |
| **Account** | **Conta** | User-facing identity term. Do not translate it as **usuário**. |
| **User** | **Usuário** | Product-facing actor term; it does not mean **Conta** or **Desenvolvedor**. |
| **Developer** | **Desenvolvedor** | Agent- and contributor-facing term, not ordinary product-interface copy. |
| **displayName** | **nome de exibição** | User-facing field label when the concept must be named. Keep `displayName` in code and transport identifiers. |
| **GamName** | **nome da pessoa** | Use only when the whole name concept must be described. Interfaces normally label its components separately and must not display `GamName`. |
| **firstName** | **nome** | User-facing field label. Keep `firstName` in code and transport identifiers. |
| **surname** | **sobrenome** | User-facing field label. Keep `surname` in code and transport identifiers. |
| **GamEmail** | **e-mail** | User-facing field label. Keep `GamEmail` in technical domain documentation and code. |
| **GamPhoneNumber** | **telefone** | User-facing field label. Keep `GamPhoneNumber` in technical domain documentation and code. |
| **GamCPF** | **CPF** | User-facing Brazilian document term. |
| **GamRG** | **RG** | User-facing Brazilian document term. |
| **GamLocation** | **local** | User-facing place term. Keep `GamLocation` in technical domain documentation and code. |
| **Remote GamLocation** | **Remoto** | User-facing name of the single system-managed non-physical location. Keep `REMOTE` as its technical catalog code. |
| **Event** | **Evento** | User-facing domain term. |
| **Presence** | **Presença** | User-facing attendance-record term; do not translate it as RSVP or planned attendance. |
| **Generic Event** | **Evento genérico** | User-facing specialization of **Evento**. |
| **UUID** | **identificador técnico** | Internal-only presentation description. The UUID value must not be requested from or displayed to ordinary users. |
| **system reference data** | **dados de referência do sistema** | Technical documentation term; normally not interface copy. |
| **database enum mirror** | **espelho de enumeração do banco de dados** | Backend technical term; not interface copy. |
| **lifecycle-owned Role** | **tipo de acesso controlado pelo ciclo de vida** | Technical and agent-facing term. Product interfaces present the specific access type, not this classification. |
| **Proxy** | **proxy** | Architecture term; use the selected product name only in product-specific implementation contexts. |
| **Canonical Public Origin** | **origem pública canônica** | Architecture and deployment term; not ordinary interface copy. |
| **row audit metadata** | **metadados de auditoria do registro** | Technical and audit documentation term; not ordinary interface copy. |
| **activity entry** | **registro de atividade** | Audit-domain term. Use a more specific approved label when a feature presents an activity history to users. |

## Relationships

- A **Member** may serve in an **Oratorio**.
- An **Oratoriano** attends an **Oratorio** but is not necessarily a **Member**.
- A **Boa Tarde** happens as part of an **Oratorio**.
- A **Lanche** is distributed at an **Oratorio**'s closing boundary.
- A **Gincana** may happen as part of an **Oratorio** or another GAM activity.
- A **Coordinator** is an active **Member** with coordination responsibility whose linked active **Account** has the current active `MEMBER` and `COORD` lifecycle-owned Roles and does not have an active `VISITOR` Role.
- A **Coordenador do Oratório** is an active **Member** whose linked active **Account** has the current active `MEMBER` and `ORATORIO_COORD` lifecycle-owned Roles and does not have an active `VISITOR` Role.
- A **Coordinator** may deactivate a **Member** in the system because of real-life inactivity in GAM actions and events.
- An **Account** may submit a **Membership Solicitation** only for itself.
- An approved **Membership Solicitation** creates the lifetime **Member** linked to its submitting **Account**.
- An **Account-less Member** may later be linked to one eligible existing **Account** through the explicit accepted linking workflow; contact information never creates that link automatically.
- A **Member** with a linked **Account** requires the `MEMBER` **lifecycle-owned Role** while active and `VISITOR` while inactive, and cannot remain a **Coordinator** or **Coordenador do Oratório** while inactive.
- A **Member contribution profile** is current Member-owned information and is independent from every **Annual Member Information Response**.
- An **Annual Member Information Response** references exactly one **Member** and belongs to one annual survey cycle.
- A **Member Information Import Batch** records successful import provenance without retaining source rows or ordinary Member information.
- An **Account** may authenticate to GAM and may receive roles or permissions through RBAC workflows.
- An **Account** is not automatically a **Member** or **Coordinator**.
- A **User** may have an **Account**, but product-facing User language must not be used for the **Developer** interacting with agents.
- A **displayName** belongs to an **Account** and must not be treated as a **GamName**.
- A **GamName** is composed of `firstName` and `surname`.
- A **GamLocation** may be referenced by multiple Events and may exist without an Event reference.
- The **Remote GamLocation** is the only non-physical **GamLocation**; every other GamLocation represents a physical place.
- An **Event** references one **GamLocation**.
- A **Presence** references exactly one **Member** and one **Event**.
- Removing a **Presence** ends its active identity without erasing its preserved historical row; the same Member and Event may later receive a new Presence with a new UUID.
- A **Generic Event** is an **Event** whose type is `GENERIC`.
- **UUID** is used to identify persisted resources such as Accounts, Members, Oratorianos, Events, Presences, GamLocations, Roles, and Permissions.
- **system reference data** is distinct from user-managed domain data, one-time data transformations, and development or demonstration fixtures.
- A **database enum mirror** follows its owning Accepted Requirement Specification; the database type does not define or expand the domain catalog.
- The **Proxy** serves the frontend and API from the **Canonical Public Origin** while keeping backend and database application ports private.
- **row audit metadata** records low-level persisted state, while an **activity entry** records meaningful business and security intent, reason, and minimized non-sensitive context.

## Usage rules

- Prefer canonical terms when naming domain concepts in documentation, APIs, code, tests, and agent handoffs.
- Use accepted short forms only when the context remains clear. `coord` is accepted as user-facing shorthand and as an informal internal abbreviation for **Coordinator**.
- In agent-facing documentation, use **Developer** for the person prompting or directing agents. Reserve **User** for product-facing GAM actors.
- Treat aliases to avoid as clarification aids, not as competing domain names.
- Use the English–Portuguese glossary for GAM-wide `pt-BR` domain terminology in frontend-authored copy. Do not translate transport codes, source-code symbols, or technical identifiers at the API boundary.
- A glossary translation does not authorize rendering an otherwise internal value. Terms marked internal or technical remain subject to the frontend presentation boundary.
- Feature-owned presentation maps may translate closed contract values that are not GAM-wide domain terms, but they must not redefine a glossary entry.
- Feature-specific Requirement Specification `Ubiquitous Language` sections may introduce local terms, but must not repeat or redefine terms, aliases, synonyms, translations, or legacy names already defined here.
- If a term is ambiguous, preserve the ambiguity as an open question in the relevant Requirement Specification or planning handoff until the developer resolves it.
- Use **Proxy** in architecture-neutral documentation. Use Caddy, Nginx, or another product name only when discussing a selected implementation or a product-specific example.

## Example dialogue

> **Dev:** "Should the attendance feature register a participant or an Oratoriano?"
>
> **Domain expert:** "Use **Oratoriano** when the person attends an **Oratorio**. A **Member** is part of GAM, and may serve at the Oratorio."
>
> **Dev:** "Can an adult be an **Oratoriano**?"
>
> **Domain expert:** "Yes. Most Oratorianos are young, but adults can attend too."
