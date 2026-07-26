---
name: gam-agent-workflow
description: Define the standard GAM Agent P, Agent O, Agent T, Agent D, and Agent R state machine. Use to establish sticky role identity, validate structured role results, enforce role boundaries, map outcomes to legal transitions, coordinate the Agent T / Agent D loop, classify Agent R findings, or determine completion and human escalation.
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

Every normal-continuation outcome maps to one target; completion and escalation
map to none.
