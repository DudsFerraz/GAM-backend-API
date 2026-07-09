---
name: handoff
description: Compact the current conversation into a chat-ready handoff for another agent to copy into a fresh session.
argument-hint: "What will the next session be used for?"
disable-model-invocation: true
---

Write a handoff directly in the chat response so the user can copy and paste it into a fresh agent session.

Do not save the handoff to the repository, the OS temporary directory, or any other file unless the developer explicitly asks for a saved artifact.

Make the handoff self-identifying. A fresh agent reading it should immediately recognize:

- The intended next agent role.
- The suggested skills to invoke.
- The durable source-of-truth artifacts to read.
- The current status.
- The exact next action.
- Any blockers, open questions, risks, or verification state.

Keep the handoff compact. Do not restate rules that the next agent will already load from `AGENTS.md`, the suggested skill, or project documentation. Include only session-specific facts, deviations, decisions, unresolved issues, and durable artifact references.

Include a "suggested skills" section in the document, which suggests skills that the agent should invoke.

Do not duplicate content already captured in other artifacts (PRDs, plans, ADRs, issues, commits, diffs). Reference them by path or URL instead.

Redact any sensitive information, such as API keys, passwords, or personally identifiable information.

If the user passed arguments, treat them as a description of what the next session will focus on and tailor the doc accordingly.
