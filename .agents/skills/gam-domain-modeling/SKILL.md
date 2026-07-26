---
name: gam-domain-modeling
description: Clarify and document GAM domain terminology, boundaries, relationships, scenarios, and durable decisions. Use when domain ambiguity must be resolved before requirements, tests, or implementation.
---

# GAM Domain Modeling

## Overview

Actively sharpen the project's domain language as requirements and designs evolve.

Challenge vague terms, force concrete scenarios, expose invalid states and record durable decisions when they crystallize.

This is a supporting capability. It does not establish or change the active GAM workflow role.

## Artifact ownership

This skill owns domain-modeling analysis.

It may directly create or update:

- `docs/ubiquitous-language.md` for GAM-wide canonical terms and aliases;
- ADRs under `docs/decisions/` when a durable architecture or design decision meets the ADR threshold;
- diagrams under `docs/diagrams/` when they clarify the model.

`$gam-requirements` owns creating and updating Requirement Specifications.

When domain modeling produces requirement rules, examples, acceptance scenarios, local terms, or open questions, pass those resolved outcomes to `$gam-requirements` instead of independently editing the Requirement Specification.

## Workflow

### 1. Identify the domain area and existing documentation

- Read `docs/documentation-guidelines/README.md`.
- Read the focused documentation guideline for every artifact that may change.
- Read `docs/ubiquitous-language.md` when the task involves GAM-wide terminology or a term that may cross feature boundaries.
- Read relevant Requirement Specifications under `docs/requirements/`.
- Read related ADRs under `docs/decisions/`.
- Read related diagrams under `docs/diagrams/`.
- Inspect code only to discover contradictions, hidden behavior, or existing terminology.

### 2. Challenge fuzzy or overloaded language

- Ask whether a term means one domain concept or another when ambiguity matters.
- Propose one canonical term and list discouraged alternatives when useful.
- Record aliases to avoid when translations, legacy names, or competing words refer to the same concept.
- Describe relationships between terms when ownership, lifecycle, role, or cardinality matters.
- Separate entities, value objects, capabilities, events, policies, and implementation components when conflation causes ambiguity.

### 3. Stress-test the model

Create concrete scenarios and edge cases that expose unclear:

- boundaries;
- lifecycle transitions;
- permissions;
- ownership;
- cardinality;
- ordering;
- concurrency;
- invalid states;
- cross-feature interactions.

Prefer scenarios that `$gam-requirements` can later turn into valid examples, invalid examples, or acceptance scenarios.

### 4. Separate domain truth from implementation facts

Use code to detect contradictions or hidden current behavior.

Do not treat existing implementation as the source of business truth unless accepted project documentation explicitly makes it authoritative for that concern.

Label findings as one of:

- documented domain rule;
- accepted architecture decision;
- current implementation behavior;
- inferred possibility;
- unresolved question.

### 5. Evaluate durable outcomes

#### Ubiquitous language

Use `docs/ubiquitous-language.md` for GAM-wide:

- canonical terms;
- aliases to avoid;
- translations or legacy names that compete with the canonical term;
- relationships between global concepts.

Feature-local terms belong in the relevant Requirement Specification and must be passed to `$gam-requirements`.

#### Requirement content

When a rule, example, acceptance scenario, local term, scope boundary, or open question becomes clear:

1. State the resolved modeling outcome.
2. Identify the relevant Requirement Specification.
3. Use or return the outcome to `$gam-requirements` for the actual file change.

Do not independently create competing Requirement Specification edits.

#### ADRs

Create or propose an ADR only when all are true:

1. The decision has meaningful consequences or future maintenance impact.
2. A future maintainer needs to understand why the choice was made.
3. Real alternatives or tradeoffs existed.

Follow `docs/documentation-guidelines/adrs.md`.

#### Diagrams

Use diagrams when they clarify state, flow, ownership, relationships, or architecture more effectively than prose.

Follow `docs/documentation-guidelines/diagrams.md`.

## Conflict handling

When this skill, another skill, existing code, external guidance, or project documentation conflicts:

1. Report the conflicting sources.
2. Identify the source of truth for the affected concern.
3. Explain the impact.
4. Identify the durable artifact that should be updated.

Do not silently overwrite accepted project documentation with community convention or current code behavior.

## Boundaries

- Do not implement production code.
- Do not write tests.
- Do not independently own Requirement Specification editing.
- Do not redefine a global term inside a feature-local ubiquitous-language section.
- Do not create ADRs for trivial, reversible, or implementation-local choices.
- Do not hide uncertainty behind a polished model.
