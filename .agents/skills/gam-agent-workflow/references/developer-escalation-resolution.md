# Developer Escalation Resolution

Use this procedure when Agent O is in `escalated` and the developer replies in
the same root task.

## Accept the resolution

Treat an explicit, unambiguous plain-language developer instruction as a
resolution; do not require a command, JSON object, repeated
`$gam-orchestration` invocation, or fresh workflow.

Before accepting it:

1. Match the instruction to the recorded escalation and blocker.
2. Confirm that it resolves enough of the blocker to activate one role safely.
3. Identify the role that owns the authorized next work.
4. Preserve any part of the escalation that remains unresolved.

Permission, scope, test-authority, requirement, architecture, and environment
decisions may all arrive through chat. Apply the developer's explicit
instruction as task authority. If it conflicts with a durable project artifact,
report the conflict and preserve it as a workflow risk for eventual artifact
alignment; do not silently reinterpret either source.

If the reply is only a question, discussion, or partial answer that leaves no
safe target, remain in `escalated`. Ask only for the missing decision.

## Preserve and reopen state

Escalation pauses rather than discards the orchestration. Preserve:

- `workflow_id`, accepted scope, cumulative artifacts, and verification;
- Agent T and Agent D thread identities;
- correction-cycle count and the last validated role result;
- the phase and owner from which escalation occurred.

Record a faithful summary of the developer's instruction, the blockers it
resolves, its authorized actions, and any residual risk. Clear only resolved
blockers. Set `human_intervention_required` to `false` only when no blocking
decision remains.

Accepting the resolution produces `developer_resolution_accepted`. Select one
next phase by ownership:

| Authorized next work | Next phase | Target |
|---|---|---|
| Correct or replace tests before initial implementation completes | `t_initial` | resumed Agent T |
| Add or correct expanded coverage after initial implementation completes | `t_expanded` | resumed Agent T |
| Continue the interrupted initial production implementation | `d_initial` | resumed Agent D |
| Correct production after initial implementation completes | `d_correction` | resumed Agent D |
| Re-review the completed workflow | `r_review` | fresh Agent R |

When the developer only removes an external blocker, resume the interrupted
phase and its owner. A fresh Agent R is required because reviews are independent
passes. Never increment the correction-cycle count merely for resuming.

If the required owner thread does not yet exist, use the fresh target mode
already required for that role's first activation. Otherwise resume the stored
thread. Do not create a second Agent T or Agent D thread to recover from an
escalation.

When reopening, append the resolution to `developer_resolutions`, mark the
recorded `escalation` resolved, set `phase` and `current_owner` to the selected
target, and clear `suspended_phase` and `suspended_owner`. Preserve the resolved
escalation and its source result as history; do not treat them as active
blockers.

## Propagate the resolution

Construct a new assignment for the selected target. Include the developer
resolution and the escalation it resolves. The target must receive the new
authority even when its earlier thread predates the developer reply.

After the target returns a valid role result, continue through the ordinary
legal-transition table. Do not restart planning, reset workflow state, or make
the developer manually transport the resolution to a subagent.

## Example

Agent D returns `test_authority_conflict` in `d_initial`. The developer
authorizes Agent T to delete or replace the conflicting legacy assertions while
preserving requirement coverage. Accept the resolution, resume the existing
Agent T in `t_initial`, and include that authorization in its assignment. A
subsequent `expected_red_confirmed` result resumes the existing Agent D in
`d_initial`.
