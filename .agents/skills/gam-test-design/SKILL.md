---
name: gam-test-design
description: Derive GAM test suites from requirements and testing guidelines. Use when writing, planning, reviewing, or expanding functional, structural, unit, integration, API, security, persistence, or regression tests before or after implementation.
---

# GAM Test Design

## Overview

Use this skill to design tests from documented behavior. Tests must protect the intended domain contract, not merely preserve accidental current implementation behavior.

## Workflow

1. Read `docs/documentation-guidelines/agent-workflow.md` to understand the Agent T role and the Agent T / Agent D alternation.
2. Read `docs/software-guidelines/testing.md`.
3. Read the related Requirement Specification under `docs/requirements/`.
4. Read related ADRs or diagrams when they affect behavior, architecture, or flow.
5. Stop and ask for clarification when the requirement is incomplete, ambiguous, or missing important constraints.
6. Derive functional tests first:
   - Select the narrowest public test seam that protects the requirement behavior.
   - Map equivalence classes.
   - Map boundary values.
   - Cover valid behavior, invalid behavior, and error outputs.
   - Avoid duplicate cases that produce the same behavioral signal.
7. Derive structural tests when source code is available and the functional contract is clear:
   - Map decisions and individual boolean conditions.
   - Select the narrowest meaningful public or intentionally exposed seam that can exercise the mapped decisions.
   - Cover true and false outcomes for relevant conditions.
   - Apply loop boundary adequacy when loops matter.
8. Choose the right execution level:
   - Unit tests for isolated domain/application behavior.
   - Integration, API, security, or persistence tests when behavior crosses boundaries.
9. Use project test organization rules:
   - Prefer custom test annotations over raw `@Tag`.
   - Use clear `@DisplayName` values.
   - Use `@Nested` only when it improves readability.
   - Use parameterized tests for repeated behavior over varied values.
10. Apply behavior-focused test design rules:
   - Test through public seams rather than private implementation details for functional and integration tests.
   - For structural tests, derive cases from code decisions but execute them through public or intentionally exposed seams whenever possible.
   - Prefer tests that survive internal refactors when behavior is unchanged.
   - Avoid implementation-coupled tests that mock internal collaborators or assert call counts as the main signal.
   - Avoid tautological assertions whose expected value repeats the production calculation.
   - Mock external system boundaries when needed; do not mock project-owned internals merely because they are collaborators.

## Quality Gates

- Tests must trace to requirement behavior or an explicitly documented defect.
- Tests must not encode accidental implementation behavior as business truth.
- Functional and integration tests must use an appropriate public seam for the behavior under test.
- Structural tests may be implementation-aware in case selection, but should still avoid direct private-method testing.
- Expected values must come from requirements, worked examples, known literals, or another independent source of truth.
- API/security tests must distinguish authentication failures from authorization failures.
- Shared API test support, authentication, authorization, token behavior, or security configuration changes require broad verification such as full `mvn verify`.
- If a test reveals a production defect, fix production behavior instead of weakening the test.
- Regression tests for documented defects must fail for the original symptom before Agent D fixes it and pass after the fix.
- If no correct regression-test boundary exists, report the gap instead of writing a misleading test.
- Continuation notes, including chat handoffs produced by `$handoff`, may summarize test context for another session, but they must reference Requirement Specifications and test files instead of replacing them.
