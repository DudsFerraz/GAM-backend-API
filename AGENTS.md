# AGENTS.md

## Shell command policy

- RTK is mandatory for shell commands in this repository.
- Before running the first shell command in a task, read `C:\Users\Eduardo\.codex\RTK.md` and follow it.
- Summary: prefix shell commands with `rtk`; in command chains, prefix each relevant command segment.

## Long-running Maven verification

- Maven verification commands in this repository may legitimately be quiet for extended periods because RTK filters output while Maven, Spring Boot, Hibernate, Flyway, Testcontainers, and Docker initialize. Lack of streamed output alone is not evidence of a hang.
- Agents must give Maven verification a timeout appropriate to its scope instead of relying on a shell tool's short default timeout:
  - focused unit or structural tests: at least 2 minutes;
  - focused integration, API, security, or persistence tests: at least 5 minutes;
  - full `verify` or `-Popenapi verify`: at least 10 minutes.
- A long process timeout does not authorize a blocking wait longer than 60 seconds. Start the command with the full process timeout, then follow a yielded/running command in increments of at most 60 seconds while keeping the developer informed.
- Do not start a duplicate Maven verification because the first invocation is quiet. Check or wait on the existing process first.
- Treat a runner or tool timeout as inconclusive, not as a test failure. Inspect available Maven output and test reports, then continue waiting or rerun once with a sufficient timeout. Report a test failure only when Maven or a test report records one.
- During focused iteration, do not add `clean` unless a clean build is required by the task. Repeated clean builds discard incremental compilation and materially increase feedback time.
- A focused Failsafe invocation must compile current main and test sources before running the selected integration test. Use this shape:

  ```powershell
  rtk test .\mvnw.cmd test-compile failsafe:integration-test failsafe:verify "-Dit.test=ExampleApiIT"
  ```

- Ensure every quoted Maven `-D` argument has a closing quote. An unterminated quote can leave an interactive PowerShell terminal waiting for continuation input and look like a hung verification.
- Prefer batching related integration-test classes in one Maven invocation when they share Spring and Testcontainers infrastructure. Separate invocations cannot reuse the previous Maven JVM, Spring test-context cache, or static Testcontainers container.

## Docker-dependent Maven verification

- Integration, API, security, and persistence tests based on `BaseApiIntegrationTest` start PostgreSQL through Testcontainers and require Docker named-pipe access in the top-level Maven process and its child processes.
- Run Docker-dependent Maven verification through `rtk test`, not through a sandboxed fallback such as `rtk proxy`. If the workspace shell reports access denied for Docker's named pipe or Docker configuration, request the Docker-capable/elevated execution path for the top-level Maven command and rerun it once there.
- Elevating only a separate `docker` probe is insufficient. Do not interpret Docker access denial, Testcontainers environment discovery failure, or the resulting class-initialization errors as an application or contract failure.

- The canonical OpenAPI contract-generation command is `.\mvnw.cmd -Popenapi verify`. With the repository's RTK wrapper, agents should run `rtk test .\mvnw.cmd verify -Popenapi`.
- The `openapi` Maven profile starts Spring Boot with Docker Compose before exporting `target/openapi/openapi.yaml`; Docker named-pipe access is therefore required by the top-level Maven process and its child processes.
- When an agent is asked to generate or verify the OpenAPI contract, it must attempt the command itself. If the normal workspace shell reports access denied for Docker's named pipe or Docker config, request the Docker-capable/elevated execution path for the top-level Maven command instead of treating the result as an application or contract failure.
- After the command completes, inspect `target/openapi/openapi.yaml` and report the actual Maven/test result. Do not claim contract generation succeeded merely because the Maven profile resolves.

## Guideline routing

`docs/dev-guidelines/` is exclusively human-oriented documentation. Do not read, apply, or treat files in that directory as instructions for LLMs or coding agents. Use the agent-facing instructions under `docs/documentation-guidelines/` and the repository agent instruction files instead.

Before changing code or tests, read only the guideline files relevant to the work:

| If touching | Read |
| --- | --- |
| Documentation structure, vocabulary, or general documentation rules | `docs/documentation-guidelines/README.md` |
| Requirements or Requirement Specifications | `docs/documentation-guidelines/README.md`, `docs/documentation-guidelines/requirements.md` |
| ADRs or architecture decision records | `docs/documentation-guidelines/README.md`, `docs/documentation-guidelines/adrs.md` |
| Mermaid diagrams | `docs/documentation-guidelines/README.md`, `docs/documentation-guidelines/diagrams.md` |
| Swagger/OpenAPI documentation structure or prose | `docs/documentation-guidelines/README.md`, `docs/documentation-guidelines/openapi.md` |
| Video documentation | `docs/documentation-guidelines/README.md`, `docs/documentation-guidelines/videos.md` |
| Agent-facing workflow, role boundaries, or handoffs | The relevant skill under `.agents/skills/`; do not read `docs/dev-guidelines/` |
| Source-of-truth priority or documentation conflicts | `docs/documentation-guidelines/README.md`, `docs/documentation-guidelines/source-of-truth.md` |
| Package placement, layer boundaries, or feature/module structure | `docs/software-guidelines/package-organization.md` |
| Controllers, routes, request/response shapes, HTTP status codes, or endpoint authorization | `docs/software-guidelines/controllers-and-http-api.md`, `docs/software-guidelines/openapi-documentation.md` |
| OpenAPI annotations, configuration, generation, schemas, examples, or contract changes | `docs/documentation-guidelines/openapi.md`, `docs/software-guidelines/openapi-documentation.md` |
| Application services, use cases, loaders, DTOs, or RDTOs | `docs/software-guidelines/application-layer.md` |
| Domain models, value objects, aggregates, entities, or rich vs. simplified model decisions | `docs/software-guidelines/domain-models-and-jpa-entities.md` |
| JPA repositories, persistence behavior, soft delete, restore behavior, or deleted-row visibility | `docs/software-guidelines/persistence-and-soft-delete.md` |
| Database schema, Flyway migrations, seed data, fixture data, or enum mirrors | `docs/software-guidelines/database-and-migrations.md` |
| Pre-production lifecycle state, migration scope, backward compatibility, or legacy support | `docs/software-guidelines/pre-production-development-state.md` |
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

## Test preservation policy

- Never delete, disable, skip, or weaken tests merely to make the test suite pass.
- If a test fails, fix the production code, test fixture, or documented requirement mismatch so the intended behavior is preserved.
- Removing or materially weakening a test requires explicit developer approval before the change is made.
- When a test appears obsolete or incorrect, report the rationale and wait for approval instead of deleting it.

## Git policy

- Never run `git add`, `git commit`, or `git push`.
- When asked to commit, stage changes, push, or write a commit message, analyze the relevant diff and output the exact PowerShell commands the developer should run.
- Use Conventional Commits for commit messages.
- Use multiple `-m` arguments for commit subject and body.
- Produce single-line PowerShell commands. Do not use Bash line continuations with backslashes.
- Split the work into multiple proposed commits when the diff contains distinct logical changes.
