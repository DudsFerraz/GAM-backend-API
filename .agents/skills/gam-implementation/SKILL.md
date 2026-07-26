---
name: gam-implementation
description: Implement GAM production behavior from documented requirements and Agent T's failing tests. Use only while acting as Agent D during initial implementation or correction phases.
---

# GAM Implementation

## Role gate

Use only in an Agent D role established by `$gam-agent-workflow`; follow its
authority map.

## Overview

Agent D owns production implementation.

Agent D starts from documented requirements and Agent T's failing tests,
implements the minimum correct production behavior, and returns a structured
result when the current implementation phase is complete.

## Workflow

### 1. Load the implementation context

- Read `AGENTS.md` and use its guideline routing to read only the software guidelines relevant to the files being changed.
- Read the relevant Requirement Specifications under `docs/requirements/`.
- Read related ADRs and diagrams when they constrain implementation.
- Read the failing tests and their observed failure output.

### 2. Validate the implementation signal

Before changing production code, confirm that:

- the tests exercise documented behavior;
- the observed failures represent missing or incorrect production behavior;
- the expected result is defined by the authoritative artifact for the
  assertion;
- no blocking requirement/test conflict prevents safe implementation.

If tests and requirements conflict, report the mismatch. Do not silently choose one or rewrite the tests.

### 3. Implement the minimum correct behavior

- Change only the production files required by the current documented scope.
- Prefer the smallest implementation that satisfies the documented contract without creating known architectural or data-integrity problems.
- Do not broaden scope beyond accepted requirements.
- Do not weaken, delete, skip, or rewrite tests merely to make them pass.

### 4. Run focused verification

During implementation:

- run the focused failing tests;
- inspect meaningful failures rather than only pass/fail counts;
- run additional targeted checks required by the changed boundary;
- run broad verification when cross-cutting API, security, persistence, build, or shared test infrastructure changes require it.

Do not claim a test passed unless it was run and observed.

### 5. Return after the initial implementation pass

When the initial functional tests pass:

1. Return `initial_implementation_satisfies_tests` with changed production
   paths and exact verification.
2. Stop the role turn.

### 6. Correction pass

In `d_correction`:

1. Read the new tests and their meaningful failure output.
2. Confirm that the issue belongs to production rather than to an unresolved requirement or incorrect test.
3. Fix the production behavior without weakening coverage.
4. Run focused and required broad verification.
5. Return `production_issue_fixed`.
6. For an escalation condition, return the matching result-contract outcome.
7. Stop after returning the result.

## Architectural blockers

If implementation requires a meaningful architecture or design decision not covered by an ADR:

- report the decision and alternatives;
- do not silently establish a durable architecture choice;
- wait for developer-directed resolution through the appropriate planning or documentation workflow.

## Boundaries

- Do not invent or redefine requirements.
- Do not write new test coverage as a substitute for Agent T.
