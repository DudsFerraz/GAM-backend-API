# GitHub Actions Automation Ideas

## Status
Idea

## Context
This repository is currently maintained by a single developer. GitHub Actions should therefore avoid team-process ceremony and focus on:

- Fast feedback after pushes.
- Deeper verification when the developer is not waiting on the result.
- Security and dependency visibility.
- Repeatable release and packaging chores.
- Easier debugging of failed CI runs through GitHub MCP tools.

The current Maven build already supports a useful split:

- `mvn test` runs the fast test suite through Surefire and excludes tests tagged as `IntegrationTest`, `ApiTest`, or `PersistenceTest`.
- `mvn verify` runs the deeper Failsafe phase and includes `IntegrationTest`, `ApiTest`, and `PersistenceTest`.

This makes the repository a good fit for separate fast, deep, security, container, and release workflows.

## Current workflow assessment
The repository already has `.github/workflows/ci-testes.yml`.

Current strengths:

- Runs on pushes to `main` and `master`.
- Runs on pull requests to `main` and `master`.
- Uses Ubuntu runners.
- Starts a PostgreSQL service.
- Configures JDK 21 with `actions/setup-java@v4`.
- Enables Maven dependency caching.
- Runs `mvn clean test`.
- Uploads the JaCoCo HTML report as an artifact even when tests fail.

Concerns and improvement opportunities:

- For a solo repository, `pull_request` may be less important than branch pushes and manual runs. It is still harmless if occasional PRs are used.
- The workflow currently runs only for `main` and `master` pushes. If feature branches are pushed directly, they will not run unless they target a pull request.
- The job name and comments contain mojibake text where Portuguese accents were probably intended. The workflow should be normalized to valid UTF-8 or rewritten with ASCII-only labels.
- There is a stray comment line: `#dasjhdsaid`.
- The workflow name says "Testes e Cobertura Java", but it only uploads coverage. It does not enforce coverage thresholds.
- `mvn clean test` is good for fast feedback, but it intentionally skips integration, API, and persistence tests because of the Maven Surefire configuration.
- PostgreSQL may be unnecessary for the fast `mvn test` workflow if the excluded deeper tests are the only tests that need a real database. If fast tests still need database access, keeping PostgreSQL is fine.
- `JWT_SECRET_KEY` is hardcoded in the workflow. Even if it is only a test secret, it is cleaner to use a clearly fake test-only value and document that it has no production meaning.
- The workflow does not currently expose a `workflow_dispatch` manual trigger.
- The workflow does not currently include a `concurrency` rule, so multiple pushes can run redundant older jobs.

Suggested near-term cleanup:

```yaml
name: Java CI

on:
  push:
    branches:
      - main
      - master
      - "feature/**"
      - "codex/**"
  workflow_dispatch:

concurrency:
  group: java-ci-${{ github.ref }}
  cancel-in-progress: true
```

Keep `pull_request` only if pull requests remain part of the personal workflow.

## Push CI
Purpose:
Provide quick confidence after every meaningful push.

Recommended command:

```text
mvn test
```

Recommended triggers:

```yaml
on:
  push:
    branches:
      - main
      - master
      - "feature/**"
      - "codex/**"
  workflow_dispatch:
```

Recommended implementation notes:

- Use JDK 21 with Temurin.
- Keep Maven cache enabled through `actions/setup-java`.
- Upload JaCoCo artifacts with `if: always()`.
- Add `concurrency` to cancel older runs on the same branch.
- Keep this workflow fast. Do not turn it into the full verification workflow unless the full suite stays fast.

Possible command:

```yaml
- name: Run fast tests
  run: mvn clean test
```

## Manual Full Verification
Purpose:
Give the developer a button for deeper verification before a release, important merge, or risky refactor.

Recommended command:

```text
mvn verify
```

Recommended trigger:

```yaml
on:
  workflow_dispatch:
```

Recommended implementation notes:

- Reuse the same JDK and Maven cache setup as Push CI.
- Start PostgreSQL if integration, API, or persistence tests need it.
- Upload Surefire, Failsafe, and JaCoCo artifacts with `if: always()`.
- Prefer manual trigger first. Add branch pushes later only if the suite remains fast enough.

Possible command:

```yaml
- name: Run full verification
  run: mvn clean verify
```

## Nightly Deep Check
Purpose:
Find slow failures, test order assumptions, dependency drift, and migration issues while the developer is not waiting.

Recommended commands:

```text
mvn clean verify
```

Optional additions:

```text
mvn dependency:tree
mvn -DskipTests package
```

Recommended triggers:

```yaml
on:
  schedule:
    - cron: "0 6 * * 1-5"
  workflow_dispatch:
```

Notes:

- GitHub scheduled workflows use UTC cron.
- The example above runs at 06:00 UTC, Monday through Friday.
- Keep a manual trigger so the same deep workflow can be run on demand.
- Do not over-notify. A solo repository benefits from signal, not noise.

## CodeQL and Security Scanning
Purpose:
Catch security-sensitive patterns that ordinary tests may not expose.

Recommended implementation:

- Use GitHub CodeQL for Java.
- Run on pushes to stable branches.
- Run on a weekly schedule.
- Allow manual dispatch.

Recommended triggers:

```yaml
on:
  push:
    branches:
      - main
      - master
  schedule:
    - cron: "0 7 * * 1"
  workflow_dispatch:
```

Recommended notes:

- CodeQL complements tests. It does not replace them.
- For a Spring Boot API with authentication, authorization, persistence, and JWT handling, CodeQL is worth having even in a solo repository.
- Dependabot alerts and dependency updates should be enabled separately in repository settings or `.github/dependabot.yml`.

## Database Migration Check
Purpose:
Prove that the database can be created from scratch using Flyway migrations.

Repository context:

- Flyway is enabled in `src/main/resources/application.properties`.
- Migration files exist under `src/main/resources/db/migration`.
- Hibernate uses `ddl-auto=validate`, so schema mismatch should be visible when the application context starts.

Implementation options:

### Option 1: Use `mvn verify`
If persistence tests already start the application and validate Flyway migrations against PostgreSQL, the migration check can simply be part of the full verification workflow.

Pros:

- Least maintenance.
- Uses the same test path as the application.
- Avoids duplicate migration logic.

Cons:

- Migration failures are mixed with the full test suite.

### Option 2: Add a dedicated migration smoke test profile
Create a small test or Maven profile that starts the Spring context against an empty PostgreSQL database and exits after Flyway and Hibernate validation.

Pros:

- Clear failure meaning.
- Faster than the full integration suite if implemented narrowly.

Cons:

- Requires a small test/profile design.

### Option 3: Run Flyway directly
Add and configure the Flyway Maven plugin, then run a migration-only command against the GitHub Actions PostgreSQL service.

Pros:

- Very explicit migration check.

Cons:

- Adds plugin configuration.
- May duplicate application datasource configuration.
- Does not automatically verify Hibernate mapping compatibility unless paired with an application startup check.

Recommended first step:
Use `mvn verify` in the nightly workflow and only split out a dedicated migration check if migration failures become common or hard to diagnose.

## Docker Build Smoke Test
Purpose:
Prove the application can be packaged into a container image.

Current repository context:

- No `Dockerfile` was found in the repository at the time this idea document was written.
- The project includes `spring-boot-docker-compose` as a runtime optional dependency, but no Docker build workflow exists yet.

Implementation options:

### Option 1: Add a Dockerfile and run `docker build`
Recommended when deployment will use container images.

Possible workflow command:

```yaml
- name: Build Docker image
  run: docker build -t gam-api:${{ github.sha }} .
```

### Option 2: Use Spring Boot image building
Spring Boot can build OCI images with buildpacks:

```text
mvn spring-boot:build-image
```

Pros:

- No Dockerfile needed.
- Fits Spring Boot conventions.

Cons:

- Can be slower.
- Requires Docker availability on the runner.
- Buildpack behavior may feel less explicit than a Dockerfile.

Recommended first step:
Delay this workflow until there is a real container packaging target. Once that exists, add a build-only smoke test before adding registry publishing.

## Release Button
Purpose:
Create a repeatable manual path for release verification and packaging.

Recommended trigger:

```yaml
on:
  workflow_dispatch:
    inputs:
      version:
        description: "Release version"
        required: true
        type: string
```

Possible responsibilities:

- Run `mvn clean verify`.
- Build the application JAR.
- Optionally build a Docker image.
- Create or validate a Git tag.
- Create a GitHub Release.
- Attach build artifacts.

Recommended first version:

- Manual trigger only.
- Run full verification.
- Build the JAR.
- Upload the JAR as an artifact.
- Do not publish externally until the release process is stable.

Possible command:

```yaml
- name: Build release artifact
  run: mvn clean verify package
```

## CI Failure MCP Loop
Purpose:
Use GitHub MCP tools to shorten the time between "CI failed" and "I know what to fix".

This is not a GitHub Action by itself. It is a workflow between the developer, an AI agent, GitHub MCP tools, and GitHub Actions results.

Without MCP, the usual loop is:

```text
Open GitHub
Find the workflow run
Open the failed job
Expand logs
Search for the real failure
Copy the stack trace
Paste it into the coding agent
Ask for help
```

With MCP, the loop becomes:

```text
Ask the agent to inspect the latest failed run
Agent lists workflow runs
Agent opens the failed run
Agent reads failed job logs
Agent extracts the failing command, test, stack trace, or Maven error
Agent maps the failure back to files in the local checkout
Agent proposes or implements the fix
Agent reruns the relevant local command
```

Useful prompts:

```text
Inspect the latest failed GitHub Actions run for this branch and explain the root cause.
```

```text
Read the failed job logs, identify the first meaningful Maven failure, and tell me which local test command should reproduce it.
```

```text
Use the CI logs to find the failing test, then inspect the related production code and propose the smallest fix.
```

```text
Compare the failed CI command with the local Maven configuration and tell me whether the workflow or the code is wrong.
```

How this relates to GitHub Actions:

- GitHub Actions creates the workflow run, job, logs, artifacts, and status.
- GitHub MCP gives the agent structured access to those runs and logs.
- The local checkout gives the agent the source code needed to diagnose and fix the failure.

What the MCP loop is good at:

- Finding the first relevant failure in long Maven logs.
- Distinguishing real test failures from setup failures.
- Noticing missing environment variables.
- Identifying differences between local and CI commands.
- Recommending whether to rerun `mvn test`, `mvn verify`, or a focused test class locally.
- Connecting an Actions failure to a recent code change.

What it should not do automatically:

- Blindly rerun expensive workflows repeatedly.
- Treat flaky tests as acceptable without investigation.
- Weaken tests to make CI pass.
- Publish releases without explicit developer intent.

Recommended Actions support for the MCP loop:

- Upload Surefire and Failsafe reports as artifacts.
- Upload JaCoCo reports as artifacts.
- Use clear job names such as `fast-tests`, `full-verification`, `codeql`, `docker-build`.
- Keep commands explicit in workflow steps.
- Use `workflow_dispatch` so the agent can ask the developer to trigger the right workflow when needed.

## Suggested Workflow Roadmap
### Phase 1: Clean up existing fast CI
Update `.github/workflows/ci-testes.yml` or replace it with a clearer `java-ci.yml`.

Recommended behavior:

- Run `mvn clean test`.
- Trigger on pushes to active branches and manual dispatch.
- Keep Maven cache.
- Upload coverage and test reports.
- Add concurrency cancellation.
- Fix encoding and stray comments.

### Phase 2: Add full verification
Create a manual and nightly workflow that runs:

```text
mvn clean verify
```

Recommended behavior:

- Start PostgreSQL.
- Upload Surefire and Failsafe reports.
- Upload coverage artifacts.
- Run nightly on weekdays.
- Allow manual dispatch.

### Phase 3: Add CodeQL
Create a security scanning workflow.

Recommended behavior:

- Run on stable branch pushes.
- Run weekly.
- Allow manual dispatch.

### Phase 4: Add migration clarity
Start by relying on `mvn verify`.

Split out a dedicated migration check only if:

- Migration failures need clearer diagnosis.
- Full verification becomes too slow.
- Migrations become a frequent source of risk.

### Phase 5: Add container smoke test
Add this after choosing a container packaging strategy.

Recommended behavior:

- Build only at first.
- Do not publish images until the release workflow is stable.

### Phase 6: Add release button
Add a manual release workflow after CI, verification, and packaging are stable.

Recommended behavior:

- Manual input for version.
- Run full verification.
- Build artifacts.
- Upload artifacts.
- Add GitHub Release creation only after the manual artifact flow is trusted.

## Open questions
- Should feature branches follow a naming convention such as `feature/**` or `codex/**`?
- Should `pull_request` triggers remain, even though the repository usually has one developer?
- Should `mvn verify` be required before every push to stable branches, or is nightly/manual enough?
- Should release artifacts be only GitHub artifacts for now, or should GitHub Releases be created immediately?
- Will this project deploy as a Docker image, a JAR, or both?
