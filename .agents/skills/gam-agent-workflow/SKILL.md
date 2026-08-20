---
name: gam-agent-workflow
description: Define and apply the cross-role GAM workflow for Agents P, O, T, D, and R. Use when establishing role authority, validating role results, or selecting legal transitions.
---

# GAM Agent Workflow

## Authority

This package is authoritative for the cross-role workflow:

- `references/role-boundaries.md` defines role identity, ownership, and
  role-skill authority.
- `references/gam-role-result.schema.json` is the sole machine-readable
  `gam-role-result/v1` definition. It owns result shapes, role/phase outcomes,
  details, and `human_intervention_required` invariants.
- `references/role-result-contract.md` explains how roles and Agent O consume
  the canonical schema without redefining it.
- The legal-transition table below maps outcomes to targets.
- `references/agent-t-agent-d-loop.md` defines correction-cycle accounting and
  loop completion criteria.
- `references/developer-escalation-resolution.md` defines how an explicit
  developer reply resolves an escalation and resumes the same workflow.

Read the owner for every concern used in the current turn.

Accepted Requirement Specifications govern the orchestration behavior they
define. Skills, prompts, assignments, tests, and workflow state are conforming
artifacts. When an accepted requirement unambiguously conflicts with a
lower-priority artifact, apply the requirement and preserve the artifact
mismatch for correction; escalate conflicts among accepted authoritative
artifacts or genuinely ambiguous behavior.

Every T, D, or R assignment exposes an exact `contract_projection` containing
`schema_version: gam-role-result/v1`, target `role`, target `phase`,
`allowed_outcomes`, `required_common_fields`, success-detail requirements, and
applicable invariants. Only schema-valid results enter the transition table.

## Legal transitions

| Current phase | Validated role outcome | Next phase | Target |
|---|---|---|---|
| p_planning | planning_ready | orchestration_start | fresh Agent O |
| orchestration_start | planning_ready | t_initial | fresh Agent T |
| t_initial | expected_red_confirmed | d_initial | fresh Agent D |
| d_initial | initial_implementation_satisfies_tests | t_expanded | resumed Agent T |
| t_expanded | production_issue_exposed | d_correction | resumed Agent D |
| d_correction | production_issue_fixed | t_expanded | resumed Agent T |
| t_expanded | td_loop_complete | r_review | fresh Agent R |
| r_review | test_design_issue_found | t_expanded | resumed Agent T |
| r_review | implementation_issue_found | d_correction | resumed Agent D |
| r_review | no_actionable_findings | complete | none |
| any role phase | an escalation outcome defined by the result contract | escalated | none |
| escalated | developer_resolution_accepted | resolution-selected active phase | target selected by the developer-resolution procedure |

Every normal-continuation outcome maps to one target. Completion and unresolved
escalation map to none. A validated developer resolution maps to exactly one
target under `references/developer-escalation-resolution.md`.
