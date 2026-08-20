---
name: gam-agent-handoff
description: Construct a structured Agent O assignment for a legal transition to Agent T, Agent D, or Agent R. Use only after $gam-agent-workflow selects the target; do not use for routing or manual handoffs.
---

# GAM Agent Handoff

## Inputs

Require Agent O to supply:

- the validated source result or `planning_ready` start condition;
- the legal transition selected by `$gam-agent-workflow`;
- `fresh` or `resumed` thread mode;
- Agent O's scope restrictions and relevant risks;
- Agent O's cumulative workflow artifacts and verification when targeting
  Agent R;
- the recorded escalation and developer resolution when the source outcome is
  `developer_resolution_accepted`.

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
  "risks": [],
  "developer_resolution": null,
  "contract_projection": {
    "schema_version": "gam-role-result/v1",
    "role": "<exact target role>",
    "phase": "<exact target phase>",
    "allowed_outcomes": [],
    "required_common_fields": [],
    "success_details": {},
    "invariants": []
  }
}
```

## Projection rules

- Preserve `workflow_id`, result identity, authoritative artifacts, relevant
  risks, and exact verification entries.
- Derive `contract_projection` from
  `$gam-agent-workflow/references/gam-role-result.schema.json`. Include only the
  target role and phase's allowed outcomes, required common fields, success
  detail shape, and applicable `human_intervention_required`, blocker,
  artifact, and verification invariants. Do not independently redefine
  `gam-role-result/v1` vocabulary.
- Reuse the result contract's `artifacts` and `verification` item shapes.
- Copy `scope_restrictions` from Agent O state unchanged.
- For Agent T or Agent D, project only relevant `created` or `modified`
  artifacts and verification from the validated source result.
- For Agent R, copy Agent O's complete `workflow_artifacts` and
  `workflow_verification`; do not expand them from repository status.
- For `developer_resolution_accepted`, replace `developer_resolution: null`
  with:

  ```json
  {
    "escalation": {
      "phase": "<phase that escalated>",
      "role": "<role that escalated>",
      "outcome": "<escalation outcome>",
      "blockers": ["<recorded blocker>"]
    },
    "instruction": "<faithful developer instruction>",
    "resolved_blockers": ["<resolved blocker>"],
    "authorized_actions": ["<explicitly authorized action>"],
    "residual_risks": ["<non-blocking risk>"]
  }
  ```

  Preserve the source role as `agent_o`; do not disguise the resolution as a
  role result.
- Do not construct an assignment when the result has blockers, scope
  deviations, or `human_intervention_required: true`. Resolved escalation
  blockers belong in `developer_resolution`, not in the assignment's active
  blocker state.

Return the envelope to Agent O.
