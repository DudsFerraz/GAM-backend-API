---
name: gam-human-handoff
description: Render a compact Markdown handoff for a developer-managed GAM role transition between independent chats. Use only when the developer explicitly invokes $gam-human-handoff for manual copy-and-paste transport; never use it in Agent O orchestration or from an automated Agent T, Agent D, or Agent R thread.
---

# GAM Human Handoff

## Boundary

A manual handoff is ephemeral transport, not project documentation, business
truth, a transcript, or permission to assume the receiving role.

## Workflow

1. When legality is not already established, use `$gam-agent-workflow` to
   validate the source, outcome, and target.
2. Select the matching reference below and read only that reference.
3. Render the handoff directly in Markdown for developer copy-and-paste.
4. Stop without activating the target role.

## Handoff references

| Packet | Required sections | Reference |
|---|---|---|
| Fresh Agent T | `Context` | `references/fresh-agent-t.md` |
| Fresh Agent D | `Context`, `Current Status`, `Changes`, `Verification` | `references/fresh-agent-d.md` |
| Return to Agent T | `Current Status`, `Verification` | `references/return-agent-t.md` |
| Return to Agent D | `Current Status`, `Changes`, `Verification` | `references/return-agent-d.md` |
| Fresh Agent R | `Context`, `Current Status`, `Changes`, `Verification` | `references/fresh-agent-r.md` |
| Review return to Agent T | `Current Status`, `Review Findings`, `Verification` | `references/review-return.md` |
| Review return to Agent D | `Current Status`, `Review Findings`, `Changes`, `Verification` | `references/review-return.md` |

## Format

Use applicable sections in this order:

| Section | Content |
|---|---|
| `Context` | brief focus and authoritative artifacts |
| `Current Status` | transition-relevant state or delta |
| `Review Findings` | Agent R evidence requiring follow-up |
| `Changes` | artifact references and why they matter |
| `Verification` | exact commands and observed results |
| `Scope`, `Risks`, `Open Questions`, `Decision` | session-specific information only |

The selected reference defines role-specific constraints. Omit all other empty
sections and instructions already owned by the receiving role skill. Fresh
handoffs include enough context for an independent chat; return handoffs include
only new information.
