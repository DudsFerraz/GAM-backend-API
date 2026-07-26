---
name: gam-orchestration
description: Run the human-started Agent O root workflow that validates accepted GAM planning, sequentially coordinates native Agent T and Agent D custom-agent threads, starts fresh Agent R reviews, validates structured role results, applies only legal transitions, enforces loop limits, and escalates unsafe continuation. Use only when the developer explicitly invokes $gam-orchestration in a fresh Codex app chat for implementation-ready planning artifacts.
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

Before each Agent R turn, record a content-aware repository baseline in root
state covering the staged and unstaged tracked diffs plus every non-ignored
untracked file path and content hash. Exclude ignored files so verification
outputs may change. Recompute it before validating Agent R's result. If it
differs, reject the result as a role-boundary violation, report changed paths,
and escalate without restoring files automatically.

## Start gate

Before spawning:

1. Validate the developer-referenced artifacts against `$gam-planning`'s
   readiness criteria without performing planning work.
2. Confirm required runtime permissions.
3. Establish a stable `workflow_id`.
4. Stop and escalate when any gate fails.

## Maintain explicit root state

Keep this concise state record in the Agent O root context; do not write it to a
repository file:

```json
{
  "workflow_id": "<id>",
  "authoritative_artifacts": ["<path>"],
  "phase": "<orchestration_start|t_initial|d_initial|t_expanded|d_correction|r_review|complete|escalated>",
  "current_owner": "<agent_o|agent_t|agent_d|agent_r|developer>",
  "spawned": {"agent_t": false, "agent_d": false, "agent_r_count": 0},
  "thread_identities": {"agent_t": null, "agent_d": null},
  "agent_r_repository_baseline": null,
  "td_correction_cycles": 0,
  "last_validated_result": null,
  "last_legal_transition": null,
  "unresolved_blockers": [],
  "human_intervention_required": false
}
```

Update it after every spawn, result validation, transition, escalation, and
review pass. Preserve native returned thread identities exactly.

## Execute the workflow

1. Construct the initial assignment and spawn Agent T.
2. After every role turn, validate exactly one result against
   `$gam-agent-workflow` reference `role-result-contract.md`.
3. Apply the single matching row in `$gam-agent-workflow`'s legal-transition
   table.
4. For a target transition, construct its assignment and spawn or resume the
   configured thread. Wait for its result before continuing.
5. For completion or escalation, stop without constructing an assignment.

Set `MAX_TD_CORRECTION_CYCLES = 6`. Maintain the counter as defined in
`$gam-agent-workflow` reference `agent-t-agent-d-loop.md`; stop at the maximum
and never increase it silently.

## Human escalation

Do not repair an invalid role result. Stop for any escalation outcome,
validation failure, unavailable permission, cycle limit, or unreliable native
continuation. Report the current state, latest evidence, blocker, and decision
needed.

## Complete

When the workflow reaches `complete`, report:

- implemented scope and authoritative artifacts;
- focused and broad verification evidence, including anything not run;
- Agent R's result;
- non-blocking residual risks;
- that the developer must inspect the diff and decide whether to commit.

Set the root state to `complete` and stop.
