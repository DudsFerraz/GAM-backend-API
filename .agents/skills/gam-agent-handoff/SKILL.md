---
name: gam-agent-handoff
description: Construct a structured Agent O assignment envelope for a validated GAM workflow transition to Agent T, Agent D, or Agent R. Use only after $gam-agent-workflow maps a validated role result to exactly one legal transition; do not use it to select a route, spawn a thread, or render a manual handoff.
---

# GAM Agent Handoff

## Inputs

Require Agent O to supply:

- the validated source result or `planning_ready` start condition;
- the legal transition selected by `$gam-agent-workflow`;
- `fresh` or `resumed` thread mode;
- Agent O's scope restrictions and relevant risks;
- Agent O's cumulative workflow artifacts and verification when targeting
  Agent R.

Reject target selection, result reinterpretation, or a blocker carried into an
assignment.

## Construct the assignment

Return one fenced `json` object with this shape:

```json
{
  "schema_version": "gam-agent-assignment/v1",
  "workflow_id": "<stable feature or workflow identifier>",
  "source_role": "agent_o",
  "source_result": {
    "role": "<agent_t|agent_d|agent_r|agent_o>",
    "outcome": "<validated outcome>"
  },
  "target": {
    "role": "<agent_t|agent_d|agent_r>",
    "thread_mode": "<fresh|resumed>",
    "phase": "<target phase>"
  },
  "authoritative_artifacts": [],
  "artifacts": [],
  "verification": [],
  "scope_restrictions": [],
  "risks": []
}
```

## Projection rules

- Preserve `workflow_id`, result identity, authoritative artifacts, relevant
  risks, and exact verification entries.
- Reuse the result contract's `artifacts` and `verification` item shapes.
- Copy `scope_restrictions` from Agent O state unchanged.
- For Agent T or Agent D, project only relevant `created` or `modified`
  artifacts and verification from the validated source result.
- For Agent R, copy Agent O's complete `workflow_artifacts` and
  `workflow_verification`; do not expand them from repository status.
- Do not construct an assignment when the result has blockers, scope
  deviations, or `human_intervention_required: true`.

Return the envelope to Agent O.
