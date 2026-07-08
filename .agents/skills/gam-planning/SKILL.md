---
name: gam-planning
description: Coordinate Agent P planning for GAM features and refactors before tests or implementation. Use when starting a fresh feature/refactor planning session, evaluating requirements, grilling the developer, modeling the domain, and producing Requirement Specifications, ADRs, diagrams, and open questions.
---

# GAM Planning

## Overview

Use this skill for Agent P: the planning agent responsible for turning an initial feature or refactor request into shared understanding and durable documentation. Stop before writing tests or implementation code.

## Workflow

1. Establish planning scope.
   - Read the user's feature or refactor request.
   - Read relevant context docs such as `docs/about-gam/` when the request depends on GAM domain knowledge.
   - Read `docs/documentation-guidelines/README.md` and the focused guideline files for the documentation artifacts that planning may produce, such as `docs/documentation-guidelines/requirements.md`, `docs/documentation-guidelines/adrs.md`, `docs/documentation-guidelines/diagrams.md`, or `docs/documentation-guidelines/agent-workflow.md`.
   - Locate existing related requirements, ADRs, and diagrams.
2. Grill until shared understanding is reached.
   - Use `$grilling` to ask one question at a time.
   - Explore the codebase instead of asking when the answer can be discovered locally.
   - Do not proceed to documentation until major ambiguities, tradeoffs, and boundaries are explicit.
3. Model the domain while grilling.
   - Use `$gam-domain-modeling` to sharpen terminology, identify canonical terms, stress-test edge cases, and decide which choices deserve ADRs.
   - Convert resolved answers into domain rules, examples, acceptance scenarios, diagrams, or ADR candidates.
   - Convert unresolved answers into open questions.
4. Consolidate planning documentation.
   - Use `$gam-requirements` to create or update Requirement Specifications with stable IDs.
   - Capture problem statement, solution intent, in-scope behavior, and out-of-scope boundaries inside the relevant Requirement Specification sections instead of creating a separate PRD.
   - Create or update ADRs only for decisions with meaningful consequences, tradeoffs, or future maintenance impact.
   - Add Mermaid diagrams when they clarify flow, state, architecture, or decisions.
5. End with a handoff.
   - Summarize the problem being solved and the intended user or domain outcome.
   - Summarize produced or updated docs.
   - List in-scope behavior and explicit out-of-scope boundaries.
   - List open questions and decisions still pending.
   - When useful, propose internal implementation slices for Agent T and Agent D:
     - Use thin, independently verifiable vertical slices through the relevant backend layers when the feature can be built incrementally.
     - Use an expand-contract sequence for wide refactors that cannot stay green as ordinary vertical slices.
     - For each slice, include a title, requirement IDs covered, expected behavior, acceptance signal, and blockers.
   - State that the next step is a fresh Agent T session using `$gam-test-design` to write functional tests from the accepted requirements.

## Boundaries

- Do not write tests.
- Do not implement production code.
- Do not create PRDs or publish issue-tracker items unless the developer explicitly asks for that external workflow.
- Do not treat implementation slices as replacement requirements; they are planning handoff notes derived from Requirement Specifications.
- Do not infer business rules from existing implementation.
- Do not mark Draft requirements as Accepted unless the developer explicitly approves.
- Do not hide unresolved ambiguity; preserve it as open questions.
