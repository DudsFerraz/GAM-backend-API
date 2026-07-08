---
name: gam-review
description: Review GAM code, tests, and documentation against requirements and project guidelines. Use when asked to review a diff, branch, implementation, tests, Requirement Specification, ADR, or agent-produced change before commit or PR.
---

# GAM Review

## Overview

Use this skill to perform a project-aware review. Prioritize bugs, requirement mismatches, guideline violations, missing tests, documentation drift, and unsafe assumptions.

## Workflow

1. Establish review scope.
   - Inspect the diff and changed files.
   - Identify whether the review covers code, tests, docs, or all of them.
   - Ignore unrelated user changes unless they affect the reviewed behavior.
2. Load the correct project context.
   - Read `docs/documentation-guidelines/agent-workflow.md` to understand the Agent R role and review boundary.
   - Read related Requirement Specifications under `docs/requirements/`.
   - Read related ADRs under `docs/decisions/` when architecture or design choices are involved.
   - Use `AGENTS.md` guideline routing to read only the relevant software guidelines.
3. Review behavior against requirements.
   - Check that implementation satisfies accepted requirements.
   - Check that tests exercise the intended behavior, boundaries, and failure modes.
   - Report missing or ambiguous requirements instead of guessing.
4. Review guideline compliance.
   - Check layer boundaries, naming, package organization, exception shape, mapper structure, persistence rules, security/RBAC, audit logging, or API conventions as relevant.
   - Report conflicts between skills, docs, and implementation using the project conflict policy.
5. Review verification.
   - Identify focused tests that should be run.
   - Identify when full verification is required.
   - Report tests that were not run or could not be run.

## Output Shape

Lead with findings, ordered by severity. Include file and line references when available.

Use this structure:

1. Findings
2. Open questions or assumptions
3. Verification gaps
4. Brief change summary only when useful

If no issues are found, say that clearly and mention remaining residual risk or unrun tests.

## Review Priorities

- Requirement mismatches
- Security, authorization, persistence, or data integrity risks
- Broken API contracts or error response shape
- Missing functional, structural, integration, API, security, or persistence tests
- Bug fixes without a reproduced symptom, regression test or documented test-boundary gap, cleanup of debug instrumentation, or verified cause
- Guideline violations that make future work harder
- Documentation drift or missing ADRs for durable decisions

Continuation notes, including handoff documents produced by `$handoff`, may help another review session resume context, but they are not review findings or source-of-truth artifacts. Review findings must still be reported directly with file and line references when possible.
