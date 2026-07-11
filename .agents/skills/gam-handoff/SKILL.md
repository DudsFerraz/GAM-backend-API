---
name: gam-handoff
description: Compact the current conversation into a chat-ready handoff for another agent chat or role transition.
---

Write the handoff directly in the chat response so the Developer can copy and paste it into the intended next agent chat.

## Core model

A handoff is an ephemeral delta packet, not a replacement for project documentation, a requirements summary, or a transcript.

Select the handoff type before writing:

1. **Fresh Agent T handoff**: start a new Agent T chat after planning, usually to write functional tests.
2. **Fresh Agent D handoff**: start a new Agent D chat after Agent T creates failing functional tests.
3. **Return to Agent T handoff**: resume the existing Agent T chat after Agent D completes the initial implementation.
4. **Return to Agent D handoff**: resume the existing Agent D chat after Agent T expands the test suite.
5. **Fresh Agent R handoff**: start a new Agent R chat after the Agent T / Agent D loop is ready for review.

The surrounding prompt identifies the receiving role and skill. Do not repeat `Role`, `Suggested Skills`, `Next Role`, or instructions already supplied by `AGENTS.md`, the relevant skill, or project documentation.

Use these sections only when they contain useful information:

- `Context`: for fresh-agent handoffs, identify the feature in one sentence and reference the durable Requirement Specifications, ADRs, or diagrams that define it. Do not create a `Read First` checklist.
- `Current Status`: state only session-specific facts that are not already clear from the repository or referenced artifacts. Do not repeat requirement or ADR statuses.
- `Changes`: reference relevant files, tests, documentation updates, or a diff/commit. Do not reproduce their contents.
- `Verification`: give the command and observed result when verification affects the next step. Distinguish expected red tests from unrelated failures.
- `Scope`: include only an unexpected scope boundary or constraint. Normal feature scope belongs in the Requirement Specification and is omitted.
- `Risks`: include only session-specific risks that matter to the receiving agent.
- `Open Questions`: include only unresolved questions that affect the next action. If the question belongs in a durable artifact, update or reference that artifact rather than restating its whole discussion.
- `Review Focus`: use only for a Fresh Agent R handoff when a specific review focus was requested.

Omit empty sections. Do not use a standard `Important Decisions` section. When a durable decision is made during the session, first record it in the appropriate Requirement Specification or ADR. Only when the receiving agent would otherwise miss a necessary transition, add an optional `Decision` section with the artifact path and one-line impact; do not repeat the decision rationale.

Do not duplicate content already captured in Requirement Specifications, ADRs, issues, commits, diffs, test output, or project guidelines. Redact sensitive information such as API keys, passwords, tokens, and personally identifiable information.

## Handoff models

### Fresh Agent T

Use when a new Agent T chat must derive tests from planning output.

Required:

- `Context`

Include `Current Status` only for non-obvious planning or discovery facts. Include `Changes`, `Verification`, `Scope`, `Risks`, or `Open Questions` only when they affect test design.

```md
# Fresh Agent T Handoff: <feature>

## Context
- <brief feature focus>
- `<Requirement Specification>`
- `<Relevant ADR or diagram, if needed>`

## Current Status
- <non-obvious session-specific fact>

## Risks
- <only if it affects test design>
```

### Fresh Agent D

Use when a new Agent D chat must implement documented behavior against Agent T's failing functional tests.

Required:

- `Context`
- `Current Status`
- `Changes`
- `Verification`

`Changes` should identify the test files Agent D must satisfy. `Verification` should state whether failures are the expected red signal or an unrelated blocker.

```md
# Fresh Agent D Handoff: <feature>

## Context
- <brief feature focus>
- `<Requirement Specification>`
- `<Relevant ADR, if needed>`

## Current Status
- <functional tests exist; implementation status>

## Changes
- `<test file>` — <relevant coverage or purpose>

## Verification
- `<command>` — <expected failures and unrelated failures, if any>
```

### Return to Agent T

Use when Agent D's initial implementation is ready and the existing Agent T chat should add structural, integration, API, security, or persistence coverage.

Normally include only:

- `Current Status`
- `Changes`, when production or documentation files changed in a way Agent T needs to inspect
- `Verification`
- Any conditional `Scope`, `Risks`, or `Open Questions`

Omit `Context` unless a new or surprising artifact must be loaded by the existing chat.

```md
# Return to Agent T Handoff: <feature>

## Current Status
- <initial implementation status and next test-design phase>

## Changes
- `<production file>` — <relevant implementation change>

## Verification
- `<functional test command>` — <result>
```

### Return to Agent D

Use when Agent T's expanded tests expose a production issue and the existing Agent D chat should fix it.

Normally include only:

- `Current Status`
- `Changes`
- `Verification`
- Any conditional `Scope`, `Risks`, or `Open Questions`

```md
# Return to Agent D Handoff: <feature>

## Current Status
- <expanded tests expose the remaining production issue>

## Changes
- `<test file>` — <new test category or behavior>

## Verification
- `<command>` — <failure count and meaningful failure signal>

## Risks
- <only if the failure may indicate a requirement gap>
```

### Fresh Agent R

Use when the implementation and agreed test loop are ready for independent review in a new chat.

Required:

- `Context`
- `Current Status`
- `Changes`
- `Verification`

Include `Review Focus`, `Scope`, `Risks`, or `Open Questions` only when they are specific to this review.

```md
# Fresh Agent R Handoff: <feature>

## Context
- <brief feature focus>
- `<Requirement Specification>`
- `<Relevant ADR or diagram, if needed>`

## Current Status
- <implementation and test-loop status>

## Changes
- `<relevant changed files or diff/commit reference>`

## Verification
- `<focused command>` — <result>
- `<full verification command>` — <result>

## Review Focus
- <only if a specific focus was requested>
```

If the Developer passes arguments, treat them as the description of what the next session will focus on and tailor the handoff to that focus.
