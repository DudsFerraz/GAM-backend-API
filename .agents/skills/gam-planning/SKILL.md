---
name: gam-planning
description: Turn GAM feature or refactor intent into implementation-ready planning artifacts. Use only while acting as Agent P before test design or production implementation.
---

# GAM Planning

## Role gate

Use only in an Agent P role established by `$gam-agent-workflow`; follow its
authority map.

## Overview

Agent P turns an initial feature or refactor request into shared understanding and durable project documentation.

## Workflow

### 1. Establish planning scope

- Read the developer's feature or refactor request.
- Read `AGENTS.md`.
- Read relevant context under `docs/about-gam/` when the request depends on GAM domain knowledge.
- Read `docs/ubiquitous-language.md` when GAM-wide domain terms are involved.
- Read `docs/documentation-guidelines/README.md` and the focused guideline files for every documentation artifact that planning may change.
- Locate related Requirement Specifications, ADRs, diagrams, and known open questions.
- Separate confirmed behavior from assumptions, implementation ideas, and unresolved decisions.

### 2. Grill and model the domain

Use `$gam-grill` to coordinate the grilling interview, domain modeling, and requirements capture.

Do not proceed to completed planning while relevant behavior, boundaries, tradeoffs, or dependencies remain implicit.

### 3. Consolidate durable planning artifacts

Use `$gam-requirements` to create or update Requirement Specifications with stable IDs.

Capture:

- context and intended outcome;
- in-scope behavior;
- explicit out-of-scope boundaries;
- business rules;
- valid and invalid examples when useful;
- acceptance scenarios;
- local ubiquitous-language terms;
- related ADRs and diagrams;
- unresolved questions.

Use `$gam-domain-modeling` for domain terminology, edge cases, relationship modeling, and ADR-worthiness analysis.

Create or update ADRs only for decisions with meaningful consequences, real alternatives, or future maintenance impact.

Add Mermaid diagrams when they materially clarify flow, state, architecture, or decision structure.

### 4. Evaluate readiness for test design

Planning is ready to transition only when:

- Agent T can derive tests without inventing business rules;
- blocking questions are resolved;
- required Requirement Specifications are Accepted;
- in-scope and out-of-scope boundaries are explicit enough to prevent accidental expansion;
- any required ADR or diagram exists or is clearly identified as pending.

Non-blocking open questions may remain when they do not affect the next test-design action.

If a blocking ambiguity remains, planning is not complete.

### 5. Report orchestration readiness

When planning is ready:

1. Report that the accepted planning artifacts are ready for implementation
   orchestration.
2. List the Requirement Specifications and any related accepted ADRs or
   diagrams Agent O must validate.
3. Identify non-blocking residual questions without turning them into role
   assignments.
4. When the developer requests a copy-and-paste handoff, use
   `$gam-human-handoff` to render the Fresh Agent O packet.
5. Stop.

The developer deliberately starts Agent O in a fresh Codex app chat. The Fresh
Agent O packet invokes `$gam-orchestration` with the accepted artifacts.

## Boundaries

- Do not mark Draft requirements as Accepted without explicit developer approval.
