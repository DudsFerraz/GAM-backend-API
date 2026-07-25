# GAM Role Result Contract

## Contract

Agent T, Agent D, and Agent R must end each completed turn with exactly one
fenced `json` object. Do not persist this runtime result in the repository.

```json
{
  "schema_version": "gam-role-result/v1",
  "workflow_id": "<stable feature or workflow identifier>",
  "role": "<agent_t|agent_d|agent_r>",
  "phase": "<t_initial|t_expanded|d_initial|d_correction|r_review>",
  "outcome": "<allowed outcome for role and phase>",
  "artifacts": [
    {
      "path": "<repository-relative path>",
      "kind": "<test|production|documentation|configuration>",
      "change": "<created|modified|consulted>"
    }
  ],
  "authoritative_artifacts": ["<Requirement Specification, ADR, or diagram>"],
  "verification": [
    {
      "command": "<exact command>",
      "status": "<passed|failed|blocked|not_run>",
      "observed": "<meaningful observed result>",
      "signal": "<expected_red|unexpected_failure|pass|not_applicable>"
    }
  ],
  "blockers": [],
  "risks": [],
  "scope_deviations": [],
  "human_intervention_required": false,
  "details": {}
}
```

Use empty arrays instead of omitting common fields. Add only the role-specific
`details` fields defined below. The role remains unchanged after returning the
result.

Use `risks` for non-blocking uncertainty. Any blocker or scope deviation
requires an escalation outcome and `human_intervention_required: true`.

An authoritative artifact is the source of truth for the disputed concern under
`role-boundaries.md`. Existing code, assignments, role results, and tests are
not authoritative merely because they exist.

## Outcomes

| Role | Phase | Outcome | Flow | Meaning |
|---|---|---|---|---|
| Agent T, D, or R | any active phase | `role_mismatch` | escalation | Assignment targets another role |
| Agent T | t_initial | `expected_red_confirmed` | transition | Functional tests fail for the expected missing behavior |
| Agent T | t_expanded | `production_issue_exposed` | transition | Valid expanded coverage exposes production behavior |
| Agent T | t_expanded | `td_loop_complete` | transition | Test design and required verification meet completion criteria |
| Agent T | t_initial or t_expanded | `requirement_ambiguity` | escalation | Accepted behavior is missing or contradictory |
| Agent T | t_initial or t_expanded | `test_authority_conflict` | escalation | An existing test that Agent T cannot safely correct conflicts with the authoritative artifact for its assertion |
| Agent T | t_initial or t_expanded | `no_valid_test_seam` | escalation | The behavior cannot be protected through an existing valid seam without changing production design or writing a misleading test |
| Agent T | t_initial or t_expanded | `verification_blocker` | escalation | Environment, permissions, or unrelated verification prevents a safe conclusion |
| Agent D | d_initial | `initial_implementation_satisfies_tests` | transition | Initial production behavior satisfies the functional tests |
| Agent D | d_correction | `production_issue_fixed` | transition | The exposed production issue is fixed and the required implementation verification passed |
| Agent D | d_initial or d_correction | `test_authority_conflict` | escalation | A supplied test conflicts with the authoritative artifact for its assertion |
| Agent D | d_initial or d_correction | `architecture_decision_required` | escalation | A durable design decision lacks an accepted ADR or direction |
| Agent D | d_initial or d_correction | `verification_blocker` | escalation | Environment, permissions, or unrelated verification prevents a safe conclusion |
| Agent R | r_review | `no_actionable_findings` | completion | No actionable findings remain |
| Agent R | r_review | `test_design_issue_found` | transition | At least one finding requires Agent T-owned test-design work |
| Agent R | r_review | `implementation_issue_found` | transition | All actionable findings can be corrected within Agent D-owned implementation work |
| Agent R | r_review | `human_decision_required` | escalation | Safe continuation requires a requirement, domain, scope, architecture, permission, or verification decision |

### Agent T details

For `expected_red_confirmed` and `production_issue_exposed`, set
`details.expected_red_signal` and distinguish unrelated failures in
`verification`. For `td_loop_complete`, set
`details.completion_criteria_satisfied` to `true`.

### Agent D details

For successful implementation outcomes, list changed production artifacts.

## Agent R finding details

Agent R lists findings in `details.findings`. Each finding has:

```json
{
  "classification": "<classification>",
  "evidence": ["<file:line or artifact>"],
  "affected_artifacts": ["<path>"],
  "summary": "<actionable statement>"
}
```

Allowed classifications:

- `requirement_domain_scope_architecture_gap`
- `verification_concern`
- `missing_or_misleading_coverage`
- `defect_without_adequate_failing_coverage`
- `production_defect_with_adequate_coverage`
- `unambiguous_implementation_issue`

Select one aggregate outcome by precedence:

1. Use `human_decision_required` for
   `requirement_domain_scope_architecture_gap` or a blocking
   `verification_concern`.
2. Otherwise, use `test_design_issue_found` for any
   `missing_or_misleading_coverage` or
   `defect_without_adequate_failing_coverage`.
3. Otherwise, use `implementation_issue_found` when all actionable findings are
   `production_defect_with_adequate_coverage` or
   `unambiguous_implementation_issue`.
4. Otherwise, use `no_actionable_findings`.

A non-blocking `verification_concern` remains a risk under the outcome selected
for the other findings. Mixed findings are represented by the findings array
and the precedence above; do not duplicate them in another field.

Escalation outcomes require `human_intervention_required: true`.

## Validation

Agent O must reject the result and escalate when:

- the JSON is malformed or uses another schema version;
- role, phase, workflow identifier, or outcome does not match the active state;
- required evidence, artifacts, or verification observations are absent;
- a claimed pass or expected red was not observed;
- `human_intervention_required` conflicts with the outcome;
- a role reports work outside its ownership;
- blockers or scope deviations appear without escalation.

`$gam-agent-workflow` alone maps valid results to transitions.
