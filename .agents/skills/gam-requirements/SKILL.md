---
name: gam-requirements
description: Create or refine GAM Requirement Specifications from resolved client or developer intent. Use when documenting behavior, scope, examples, terminology, or open questions under docs/requirements/.
---

# GAM Requirements

## Overview

This skill owns creating and updating GAM Requirement Specifications.

Requirements define expected behavior and business rules. They must not contain test implementation details, prescribe incidental code structure, or merely describe current implementation.

This is a supporting capability. It does not establish or change the active GAM workflow role.

## Workflow

### 1. Load the documentation standards and related artifacts

- Read `docs/documentation-guidelines/README.md`.
- Read `docs/documentation-guidelines/requirements.md`.
- Locate related Requirement Specifications under `docs/requirements/`.
- Read related ADRs under `docs/decisions/`.
- Read related diagrams under `docs/diagrams/`.
- Read `docs/ubiquitous-language.md` when global terminology is involved.

### 2. Preserve uncertainty

Before writing a rule, determine whether it is:

- explicitly resolved;
- already documented;
- constrained by an accepted ADR;
- still an open question.

Preserve unresolved behavior as an open question instead of inventing a business rule.

Do not infer business truth from current implementation.

### 3. Create or update the Requirement Specification

Follow the project template and focused guideline.

Use relevant sections such as:

- Status
- Context
- Ubiquitous Language
- Functional requirements with stable `REQ-<AREA>-<NUMBER>` IDs
- Valid examples
- Invalid examples
- Acceptance scenarios
- Diagrams
- Open questions
- Out of scope
- Related ADRs
- Related videos

Include only sections relevant to the specification.

### 5. Maintain stable requirement identity

- Preserve an existing requirement ID when wording is clarified without changing its meaning.
- Create a new requirement ID when the expected behavior changes materially.
- Do not recycle removed or superseded IDs for different behavior.
- Keep each requirement independently understandable and testable.

### 6. Link related durable artifacts

- Link ADRs when they constrain or explain architecture relevant to the requirement.
- Link diagrams when they clarify behavior or domain flow.
- Reference global ubiquitous-language terms instead of redefining them.
- Use the Requirement Specification's `Ubiquitous Language` section only for feature-local terms.

### 7. Validate readiness

Before considering the Requirement Specification ready for downstream use, check that:

- behavior is clear and testable;
- scope boundaries are explicit;
- examples do not contradict requirements;
- acceptance scenarios reflect resolved behavior;
- open questions are visible;
- implementation and test details have not leaked into the specification;
- status is accurate and has not been promoted without developer approval.

## Quality gates

- Requirements describe expected behavior and business rules.
- Requirements do not name test classes, test methods, mocks, fixtures, or implementation algorithms.
- Requirement Specification local terminology does not repeat or redefine global terms, aliases, synonyms, translations, or legacy names from `docs/ubiquitous-language.md`.
- Synonyms, translations, and legacy names that compete with a canonical global term are recorded in the global ubiquitous language.
- Explicit scope exclusions appear in `Out of scope` when they prevent scope creep or clarify deferred behavior.
- Architectural consequences are linked to an ADR or identified as a pending decision.
- Uncertainty remains visible as open questions.
- Draft requirements are not marked Accepted without explicit developer approval.

## Boundaries

- Do not implement production code.
- Do not write tests.
- Do not infer business rules from existing implementation.
- Do not allow several skills to create competing edits to the same Requirement Specification.
- Do not use a Requirement Specification as a substitute for an ADR when the concern is a durable architecture decision.
