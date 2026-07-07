# AGENTS.md

## Shell command policy

- RTK is mandatory for shell commands in this repository.
- Before running the first shell command in a task, read `C:\Users\Eduardo\.codex\RTK.md` and follow it.
- Summary: prefix shell commands with `rtk`; in command chains, prefix each relevant command segment.

## Guideline routing

Before changing code or tests, read only the guideline files relevant to the work:

| If touching | Read |
| --- | --- |
| Requirements, ADRs, diagrams, OpenAPI docs, or agent workflow | `docs/documentation-guidelines.md` |
| Package placement, layer boundaries, or feature/module structure | `docs/software-guidelines/package-organization.md` |
| Controllers, routes, request/response shapes, HTTP status codes, or endpoint authorization | `docs/software-guidelines/controllers-and-http-api.md` |
| Application services, use cases, loaders, DTOs, or RDTOs | `docs/software-guidelines/application-layer.md` |
| Domain models, value objects, aggregates, entities, or rich vs. simplified model decisions | `docs/software-guidelines/domain-models-and-jpa-entities.md` |
| JPA repositories, persistence behavior, soft delete, restore behavior, or deleted-row visibility | `docs/software-guidelines/persistence-and-soft-delete.md` |
| Database schema, Flyway migrations, seed data, fixture data, or enum mirrors | `docs/software-guidelines/database-and-migrations.md` |
| Mapper files or domain/persistence/API transformations | `docs/software-guidelines/mappers.md` |
| Exceptions, validation failures, API error shape, or custom exception choices | `docs/software-guidelines/exception-handling.md` |
| Interfaces, ports, abstractions, test doubles, or strategy contracts | `docs/software-guidelines/interfaces.md` |
| Search endpoints, filters, specifications, parsing, or JPA criteria predicates | `docs/software-guidelines/search-and-jpa-specifications.md` |
| Authentication, authorization, RBAC, permissions, roles, or lockout prevention | `docs/software-guidelines/security-and-rbac.md` |
| Audit events, activity logs, user intent logging, action names, or reason policies | `docs/software-guidelines/activity-audit-log.md` |
| Unit, functional, structural, integration, API, security, or persistence tests | `docs/software-guidelines/testing.md` |

## Conflict policy

- Treat project documentation as the initial source of truth for this repository.
- If instructions from `.agents/skills`, user-level skills, global guidance, or external community material conflict with `docs/`, report the conflict before applying the conflicting guidance.
- When reporting a conflict, include the conflicting sources, the text or rule that should currently win, and the rationale.
- Prefer the current user instruction when it explicitly overrides project documentation for the active task.
- If the conflict exposes a durable project decision, ask whether to update the relevant Requirement Specification, ADR, guideline, or skill.
- Do not silently rewrite project conventions to match an external skill.

## Git policy

- Never run `git add`, `git commit`, or `git push`.
- When asked to commit, stage changes, push, or write a commit message, analyze the relevant diff and output the exact PowerShell commands the developer should run.
- Use Conventional Commits for commit messages.
- Use multiple `-m` arguments for commit subject and body.
- Produce single-line PowerShell commands. Do not use Bash line continuations with backslashes.
- Split the work into multiple proposed commits when the diff contains distinct logical changes.
