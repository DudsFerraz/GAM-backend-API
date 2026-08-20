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

Accepted Requirement Specifications govern the orchestration behavior they
define. When an accepted requirement unambiguously conflicts with a
lower-priority artifact, apply the accepted requirement for routine routing and
preserve the artifact mismatch for correction. Escalate only when accepted
authoritative artifacts conflict, requirement silence would have to be treated
as removal, or another substantive blocker has no single authoritative
resolution.

When an accepted requirement explicitly changes, supersedes, or removes the
rule protected by an obsolete test, authorize Agent T to correct, replace, or
delete that obsolete test without developer approval. Agent T must preserve or
strengthen coverage of every behavior that remains required. Agent D never
changes or weakens tests. Requirement silence is not removal; escalate any
proposed deletion that depends on silence or materially reduces required
coverage.

## Fixed runtime policy

Use these project custom agents by exact `name`:

| Role | Custom agent |
|---|---|
| Agent T | `gam-agent-t` |
| Agent D | `gam-agent-d` |
| Agent R | `gam-agent-r` |

Follow the legal transition's target mode. After Agent R returns a validated
result or exhausts result-correction attempts, capture the result and apply the
legal transition without requiring closure. If the runtime exposes a supported
close operation, attempt it and record the native result. Otherwise record the
reviewer as retained completed and non-resumable, then continue without blocking
the transition. Every later review uses a fresh Agent R; do not reuse or resume
a completed reviewer.

Do not pass explicit model or reasoning overrides when spawning standard role
agents. They inherit the project `[agents]` defaults.

Spawn fresh role agents with the exact custom `agent_type` and
`fork_turns: "none"`; full-history forks cannot select a different custom role.
Store the target returned when spawning Agent T or D. For resumed transitions,
call `followup_task` with that target instead of spawning another agent.

Keep Agent T and Agent D sequential. Wait for the active writer to finish before
activating the other. Never run T and D write-heavy turns concurrently.

## Platform permission boundary

Agent O's routing authority does not grant native platform permission. Codex
Auto-review or the active native permission policy remains authoritative for
every sandbox, filesystem, network, application, and tool boundary. Agent O
shall not approve or claim to approve a native action. When approval is denied
and no materially safe alternative exists, report the precise permission blocker
(`permission_blocker`) with the observed native evidence.

## Start gate

Before spawning:

1. Validate the developer-referenced artifacts against `$gam-planning`'s
   readiness criteria without performing planning work.
2. Record the accepted planning scope restrictions without reinterpreting them.
3. Confirm that required runtime permissions are available through the native
   permission policy, including Agent R's non-writing runtime.
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
  "threads": [
    {
      "native_identity": "<exact target returned by native spawn>",
      "role": "<agent_t|agent_d|agent_r>",
      "current_phase": "<role phase>",
      "resumability": "<sticky|non_resumable|no_legal_future_use>",
      "latest_native_status": "<observed status>",
      "lifecycle_state": "<active|completed|interrupted|retained_completed|confirmed_closed>",
      "recovery_attempted": false,
      "result_re_emission_attempts": 0
    }
  ],
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
review pass. Preserve every exact native identity and latest native status. A
completed or interrupted agent remains open and consumes capacity until
closure is confirmed. Do not infer confirmed closure from a completed result,
an interruption response, disappearance from an active list, or a cleanup
request.

Completed or interrupted threads remain open until native closure is confirmed.

For each native identity, record its role, current phase, resumability, latest
native status, and lifecycle state. Lifecycle state distinguishes active,
completed, interrupted, retained completed, and confirmed closed. A retained
completed thread has no active turn, is non-resumable, remains open because
native closure is unavailable, and still occupies capacity.

`scope_restrictions` references the accepted planning exclusions that constrain
every role. Do not infer scope from repository status, directory proximity, or
unrelated worktree changes.

After validating each Agent T or Agent D result, accumulate its `created` and
`modified` artifacts in `workflow_artifacts` and append its exact verification
entries to `workflow_verification`. Reuse the result contract's item shapes,
keep one artifact entry per path, and preserve `created` when the workflow
created a path. Do not accumulate `consulted` artifacts.

Before accepting a result, verify that every created or modified artifact path exists,
is a normalized repository-relative reference, and falls within the reporting
role's ownership under `role-boundaries.md` and the assignment. Reject the
result as mechanically invalid when any artifact fails existence,
normalization, or ownership validation; do not add it to cumulative state.

## Execute the workflow

1. Construct the initial assignment and spawn Agent T.
2. After every role turn, validate exactly one result against the sole
   canonical `gam-role-result/v1` definition at `$gam-agent-workflow` reference
   `gam-role-result.schema.json`, plus active workflow identity and ownership.
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

Every assignment contains a schema-derived `contract_projection` with the exact
target `role`, `phase`, `allowed_outcomes`, `required_common_fields`, required
success details, and applicable invariants. Never expose outcomes from another
role or phase.

## Mechanical result recovery

When validation finds only a mechanical result defect, ask the same role
thread to re-emit one complete result. Provide the validation errors and the
exact role/phase contract projection. Preserve the role's engineering facts;
Agent O does not fabricate missing evidence, rewrite findings, or silently
accept the invalid result.

Allow at most two complete re-emission attempts after the original invalid
result. Validate each re-emission from scratch. Escalate with
`human_intervention_required: true` only after both correction attempts fail,
the same role thread cannot be recovered, the reports contain inconsistent
facts, or a substantive blocker appears. Record the repeated errors and exact
unresolved decision.

## Thread lifecycle and capacity

Set `SUPPORTED_MAX_CONCURRENT_THREADS_PER_SESSION = 20`, matching the supported
local configurations. Before spawning any role, reconcile every lifecycle
record with native thread state and include retained completed threads in
capacity accounting. Do not dynamically change or bypass the configured limit
during an active workflow, and do not reuse a completed Agent R as a capacity
workaround.

For a completed Agent R pass, including one that exhausted result-correction
attempts, use native closure only as best-effort cleanup. When a supported close
operation exists, attempt it and record the observed result. When no supported
close operation exists or the attempt fails, mark the reviewer retained
completed and non-resumable and continue without blocking the transition.
Interruption is not closure. Every subsequent independent review requires a
fresh Agent R.

Agent T and Agent D remain sticky only while a current or future legal
transition can resume them, including during a resolvable workflow escalation.
Preserve their original native identities and resumability while that route
exists. When the workflow completes, is abandoned, or makes resumption illegal,
perform best-effort cleanup of Agent T and Agent D. If a supported close
operation exists, attempt it and record the result; otherwise mark each open
thread retained completed. Agent O may declare the workflow complete after this
accurate accounting without misreporting closure.

Missing close capability alone shall not cause escalation. Report a platform
capacity blocker only when the configured native capacity is actually exhausted
for a required fresh agent or the spawn operation fails. The blocker must list
all capacity-occupying thread identities, their observed states, and the required
transition that could not start.

## Quiet work and unreliable continuation

Elapsed time or lack of streamed output alone never establishes an unreliable
continuation. Check whether a native command, tool call, Maven verification,
infrastructure startup, or role turn is still active, and request bounded
progress checkpoints. Do not duplicate or interrupt live work merely because
it is quiet.

Classify a continuation as unreliable only when native state shows no live work
capable of finishing and repeated progress checks produce no trustworthy result
or status. For Agent T or Agent D, interrupt an active turn when necessary and
attempt one recovery on the same sticky thread using the preserved assignment
and workflow state; escalate if that recovery fails. For Agent R, interrupt its
active turn when necessary, preserve the validated review assignment, mark the
thread non-resumable, and start one fresh independent review when capacity
permits. Closure of the prior reviewer is best-effort and is not a replacement
precondition.

## Human escalation

Reserve human escalation for a substantive blocker without one accepted
resolution, an unavailable required platform permission or non-deferred
lifecycle capability after safe native handling, exhausted mechanical correction,
failed sticky-thread recovery, the correction-cycle limit, or another decision
explicitly reserved for the developer. Report the current state, latest
evidence, blocker, and exact decision needed. Record the active phase and owner
as `suspended_phase` and `suspended_owner` before setting the visible phase to
`escalated`.

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

After best-effort terminal cleanup and accurate retained-thread accounting, set
the root state to `complete` and stop.
