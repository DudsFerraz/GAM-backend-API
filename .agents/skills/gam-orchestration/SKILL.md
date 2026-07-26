---
name: gam-orchestration
description: Coordinate and resume the developer-started Agent O workflow across Agent T, Agent D, and Agent R. Use only when $gam-orchestration is explicitly invoked with accepted planning artifacts or in the existing Agent O task after an escalation is resolved.
---

# GAM Orchestration

## Agent O boundary

Act as Agent O only in the developer-started root Codex session. Coordinate
native role agents without performing or repairing role-local work or
reinterpreting requirements or architecture.

Load `$gam-agent-workflow` and follow its authority routing. Use
`$gam-agent-handoff` only after a validated target transition.

## Fixed runtime policy

Use these project custom agents by exact `name`:

| Role | Custom agent |
|---|---|
| Agent T | `gam-agent-t` |
| Agent D | `gam-agent-d` |
| Agent R | `gam-agent-r` |

Follow the legal transition's target mode. Close Agent R before applying a
review-return transition.

Do not pass explicit model or reasoning overrides when spawning standard role
agents. They inherit the project `[agents]` defaults.

Spawn fresh role agents with the exact custom `agent_type` and
`fork_turns: "none"`; full-history forks cannot select a different custom role.
Store the target returned when spawning Agent T or D. For resumed transitions,
call `followup_task` with that target instead of spawning another agent.

Keep Agent T and Agent D sequential. Wait for the active writer to finish before
activating the other. Never run T and D write-heavy turns concurrently.

## Start gate

Before spawning:

1. Validate the developer-referenced artifacts against `$gam-planning`'s
   readiness criteria without performing planning work.
2. Record the accepted planning scope restrictions without reinterpreting them.
3. Confirm required runtime permissions, including Agent R's non-writing
   runtime.
4. Establish a stable `workflow_id`.
5. Stop and escalate when any gate fails.

## Maintain explicit root state

Keep this concise state record in the Agent O root context; do not write it to a
repository file:

```json
{
  "workflow_id": "<id>",
  "authoritative_artifacts": ["<path>"],
  "scope_restrictions": ["<accepted planning reference>"],
  "workflow_artifacts": [],
  "workflow_verification": [],
  "phase": "<orchestration_start|t_initial|d_initial|t_expanded|d_correction|r_review|complete|escalated>",
  "current_owner": "<agent_o|agent_t|agent_d|agent_r|developer>",
  "spawned": {"agent_t": false, "agent_d": false, "agent_r_count": 0},
  "thread_identities": {"agent_t": null, "agent_d": null},
  "td_correction_cycles": 0,
  "last_validated_result": null,
  "last_legal_transition": null,
  "suspended_phase": null,
  "suspended_owner": null,
  "escalation": null,
  "developer_resolutions": [],
  "unresolved_blockers": [],
  "human_intervention_required": false
}
```

Update it after every spawn, result validation, transition, escalation, and
review pass. Preserve native returned thread identities exactly.

`scope_restrictions` references the accepted planning exclusions that constrain
every role. Do not infer scope from repository status, directory proximity, or
unrelated worktree changes.

After validating each Agent T or Agent D result, accumulate its `created` and
`modified` artifacts in `workflow_artifacts` and append its exact verification
entries to `workflow_verification`. Reuse the result contract's item shapes,
keep one artifact entry per path, and preserve `created` when the workflow
created a path. Do not accumulate `consulted` artifacts.

## Execute the workflow

1. Construct the initial assignment and spawn Agent T.
2. After every role turn, validate exactly one result against
   `$gam-agent-workflow` reference `role-result-contract.md`.
3. Update the cumulative workflow state from a validated Agent T or Agent D
   result.
4. Apply the single matching row in `$gam-agent-workflow`'s legal-transition
   table.
5. For a target transition, construct its assignment and spawn or resume the
   configured thread. Wait for its result before continuing.
6. For completion or unresolved escalation, stop without constructing an
   assignment.

Set `MAX_TD_CORRECTION_CYCLES = 10`. Maintain the counter as defined in
`$gam-agent-workflow` reference `agent-t-agent-d-loop.md`; stop at the maximum
and never increase it silently.

## Human escalation

Do not repair an invalid role result. Stop for any escalation outcome,
validation failure, unavailable permission, cycle limit, or unreliable native
continuation. Report the current state, latest evidence, blocker, and decision
needed. Record the active phase and owner as `suspended_phase` and
`suspended_owner` before setting the visible phase to `escalated`.

Escalation pauses the current orchestration; it does not invalidate its state
or thread identities. When the developer replies in this Agent O task, load
`$gam-agent-workflow` reference `developer-escalation-resolution.md`. If the
reply resolves the blocker, accept it without requiring special syntax or a
fresh `$gam-orchestration` invocation, construct the selected assignment, and
continue the same workflow. If it does not resolve the blocker, remain
escalated and ask only for the missing decision.

## Complete

When the workflow reaches `complete`, report:

- implemented scope and authoritative artifacts;
- focused and broad verification evidence, including anything not run;
- Agent R's result;
- non-blocking residual risks;
- that the developer must inspect the diff and decide whether to commit.

Set the root state to `complete` and stop.
