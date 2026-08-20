# ADR-0033: Use a Machine-Readable Agent Workflow Contract and Verified Thread Lifecycle

## Status

Accepted

## Context

GAM's native Agent O workflow coordinates sticky Agent T and Agent D writers
with fresh independent Agent R passes. The workflow currently describes role
results primarily through shared prose and tracks reviewer creation as a count
rather than as a complete native lifecycle.

Observed workflow failures expose two coupled design weaknesses:

- plausible but invalid result vocabulary, omitted fields, identity mistakes,
  and reporting typos all reach Agent O only after generation and use the same
  human-escalation path as substantive decisions; and
- Agent O can describe an Agent R as closed while the recorded native action is
  only interruption, leaving completed reviewers open until the configured
  thread limit is exhausted.

The accepted planning direction requires requirements to govern workflow
behavior, mechanical failures to recover without routine developer work, and
fresh review independence to remain intact. The supported Codex runtime exposes
no callable native close operation, so closure cannot remain a legal-transition
precondition.

## Decision

Adopt one repository-owned, machine-readable `gam-role-result/v1` schema as the
canonical role-result contract. The schema will define common fields and use
role- and phase-specific constraints for allowed outcomes, required details,
blocker behavior, and `human_intervention_required` invariants.

The defective prose definition will be replaced in place. No compatibility
layer, migration path, legacy alias, or dual schema will be introduced.
`human_decision_required` will be removed. Agent R will instead use precise
blocker outcomes for requirement/domain, architecture, scope, permission, and
verification decisions.

Role assignments and correction prompts will carry a projection of the
canonical contract containing only the target role and phase. Human-readable
workflow documentation will explain the contract but will not independently
own outcome vocabularies. Repository verification will exercise every legal
role/phase outcome and representative invalid results, including the observed
classes of wrong outcome, missing field, inconsistent intervention flag, and
invalid artifact reference.

Agent O will validate every role result before routing it. A mechanical defect
will return to the same role thread for at most two complete re-emission
attempts. Agent O will report validation errors but will not invent evidence or
rewrite substantive findings. Repeated invalidity or inconsistent facts will
be escalated.

Agent O's native state will track every spawned thread identity and distinguish
active, completed, interrupted, and confirmed-closed states. Agent R will be
closed after each review pass only when the runtime exposes a supported native
close operation. Otherwise, its completed thread will be recorded as retained
completed and the legal transition will continue. Interruption will be used
only to stop an active turn and will never be treated as closure. Every later
review will still use a fresh Agent R thread. Sticky T/D threads will remain
open while a legal transition can resume them; terminal closure is also best-
effort and is not a workflow-completion precondition.

The supported local Codex configuration will allow 20 open spawned-agent
threads per session, excluding the primary thread. This is fixed operational
headroom, not authorization for Agent O to change or bypass the limit during a
workflow. Retained completed agents remain part of capacity accounting, and
Agent O will escalate only when a required fresh spawn encounters actual native
capacity exhaustion or another spawn failure.

Unreliable continuation will require native evidence rather than elapsed time
alone. T/D will receive one same-thread recovery attempt; an unreliable R will
be interrupted when necessary, marked non-resumable, and replaced with a fresh
reviewer when capacity permits. Missing or failed native close capability will
be recorded accurately but will not block continuation or permit reviewer
reuse.

Accepted Requirement Specifications will govern orchestration behavior. Agent
skills, custom-agent prompts, handoff envelopes, and validators will be treated
as conforming implementation artifacts.

## Alternatives considered

### Option 1: Keep the shared Markdown contract and improve wording

Pros:
- Small documentation-only change.
- No new machine-readable artifact or validator.

Cons:
- Prompts, role tables, assignments, and validation can still drift.
- A role continues to see plausible outcomes belonging to other phases or
  roles.
- Repository verification cannot reliably enumerate the contract.
- The result vocabulary remains dependent on model interpretation.

### Option 2: Let Agent O repair invalid results heuristically

Pros:
- Avoids many developer interruptions.
- Requires little change to role prompts.

Cons:
- Moves evidence ownership from the reporting role to Agent O.
- Can turn a reporting defect into fabricated verification or findings.
- Does not correct the ambiguous source contract.
- Makes validation depend on unrecorded Agent O interpretation.

### Option 3: Reuse completed reviewers when capacity is exhausted

Pros:
- Continues within a session that cannot spawn another thread.
- Avoids depending on native close support.

Cons:
- Weakens the fresh independent-review rule.
- Retains earlier reviewer context and assumptions.
- Hides the lifecycle leak instead of detecting it.
- Makes the configured capacity limit fail unpredictably later.

### Option 4: Replace native orchestration with an external state machine

Pros:
- Could enforce schemas and thread lifecycle outside model context.
- Could persist workflow state independently from a root task.

Cons:
- Conflicts with the accepted native Codex workflow direction.
- Adds deployment, authentication, maintenance, and synchronization concerns.
- Does not guarantee access to native close semantics.
- Broadens the correction far beyond the identified gaps.

### Option 5: Use a canonical schema with capacity-aware best-effort lifecycle

Pros:
- Provides one enumerable contract for agents, assignments, and verification.
- Prevents role and phase vocabulary leakage.
- Preserves role ownership through bounded same-agent re-emission.
- Makes thread capacity and cleanup observable.
- Retains fresh independent reviewers and sticky writer continuity.
- Continues on runtimes that expose no callable close operation.

Cons:
- Requires schema-aware contract projections and repository verification.
- Agent O must maintain more explicit lifecycle state.
- Retained completed threads may eventually exhaust even the increased limit.
- Existing skill and prompt prose must be reconciled with the schema.

### Option 6: Require native closure before every transition

Pros:
- Releases capacity immediately when the runtime supports closure.
- Keeps native lifecycle state minimal.

Cons:
- Makes a missing platform operation an unsatisfiable workflow precondition.
- Escalates after successful engineering work even when ample capacity remains.
- Cannot be implemented reliably by repository prompts or requirements alone.

## Consequences

Positive consequences:
- Invalid result causes become testable instead of anecdotal.
- Role agents receive smaller, unambiguous outcome sets.
- Mechanical defects normally recover without developer intervention.
- Requirement-directed stale-test correction becomes routine orchestration.
- Missing close capability no longer blocks valid workflow transitions.
- Fixed capacity headroom supports repeated fresh independent reviews.
- Capacity failures report actual native exhaustion rather than an absent
  optional lifecycle operation.
- Agent O cannot silently convert interruption into closure.

Negative consequences:
- The schema and its projections become critical workflow infrastructure.
- Repository checks must prevent generated or documented projections from
  drifting.
- Agent O requires bounded retry and lifecycle-reconciliation behavior.
- Retained completed threads continue occupying native session capacity.
- Very long workflows may still exhaust the fixed limit and require a new
  session.

## Related requirements

- `REQ-AGENT-001`
- `REQ-AGENT-002`
- `REQ-AGENT-003`
- `REQ-AGENT-004`
- `REQ-AGENT-005`
- `REQ-AGENT-006`
- `REQ-AGENT-007`
- `REQ-AGENT-008`
- `REQ-AGENT-009`
- `REQ-AGENT-010`
- `REQ-AGENT-011`
- `REQ-AGENT-012`
- `REQ-AGENT-013`
- `REQ-AGENT-014`
- `REQ-AGENT-015`
- `REQ-AGENT-016`
- `REQ-AGENT-017`
- `REQ-AGENT-018`

## Related diagrams

- Inline role-result validation and lifecycle flow in
  [`docs/requirements/platform/agent-orchestration-workflow.md`](../requirements/platform/agent-orchestration-workflow.md)

## Related videos

- None.
