---
name: gam-agent-workflow
description: Define and apply the cross-role GAM workflow for Agents P, O, T, D, and R. Use when establishing role authority, validating role results, or selecting legal transitions.
---

# GAM Agent Workflow

## Authority

This package is authoritative for the cross-role workflow:

- `references/role-boundaries.md` defines role identity, ownership, and
  role-skill authority.
- `references/role-result-contract.md` defines result shapes and outcomes.
- The legal-transition table below maps outcomes to targets.
- `references/agent-t-agent-d-loop.md` defines correction-cycle accounting and
  loop completion criteria.
- `references/developer-escalation-resolution.md` defines how an explicit
  developer reply resolves an escalation and resumes the same workflow.

Read the owner for every concern used in the current turn.

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
