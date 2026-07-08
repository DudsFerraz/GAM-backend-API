---
name: gam-implementation
description: Implement GAM production code against documented requirements and failing tests. Use when acting as Agent D to satisfy Agent T's tests, fix bugs exposed by expanded test suites, or implement feature/refactor behavior while following AGENTS.md and software guidelines.
---

# GAM Implementation

## Overview

Use this skill for Agent D: the implementation agent responsible for production code changes. Start from documented requirements and Agent T's failing tests, and stop when the agreed tests pass or a requirement/test blocker is found.

## Workflow

1. Read `docs/documentation-guidelines/agent-workflow.md` to understand the Agent D role and the Agent T / Agent D alternation.
2. Read `AGENTS.md`.
3. Read the relevant Requirement Specifications under `docs/requirements/`.
4. Read the failing tests and their failure output.
5. Use `AGENTS.md` guideline routing to read only the relevant software guidelines for the files being changed.
6. Implement the minimum production behavior needed to satisfy the failing tests and documented requirements.
7. Run focused tests during iteration.
8. When Agent T later adds structural or integration tests, resume in the same Agent D context and fix bugs exposed by the expanded suite.

## Boundaries

- Do not invent missing business rules.
- Do not weaken, delete, skip, or rewrite tests merely to make the suite pass.
- Do not broaden scope beyond the current Requirement Specifications without developer approval.
- Do not invoke `$diagnosing-bugs` during ordinary implementation. That is a separate workflow used only when the developer explicitly requests diagnosis mode.
- If tests and requirements conflict, report the mismatch and ask for clarification.
- If implementation requires an architectural decision not covered by an ADR, ask whether an ADR should be created or updated.
- Continuation notes, including handoff documents produced by `$handoff`, may summarize implementation context for another session, but they must reference requirements, tests, changed files, and verification output instead of replacing them.
