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
- relevant scope restrictions and risks.

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
- Include only relevant `created` or `modified` artifacts; do not copy
  `consulted` entries.
- Take `scope_restrictions` from accepted planning or Agent O state, not from a
  role's scope deviations.
- Do not construct an assignment when the result has blockers, scope
  deviations, or `human_intervention_required: true`.

Return the envelope to Agent O.
