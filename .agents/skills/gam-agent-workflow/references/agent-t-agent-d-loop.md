# Agent T / Agent D Loop

This reference defines correction-cycle accounting and completion criteria.
Follow the parent skill's authority map for all other concerns.

## Correction-cycle count

Increment once for each valid `production_issue_fixed` result. No other outcome
increments the count.

## Completion criteria

The Agent T / Agent D loop is complete only when:

- the relevant documented requirements have test coverage appropriate to their risk;
- the agreed focused tests pass;
- required broad verification has passed;
- no known production defect remains hidden by weakened, skipped, or misleading tests;
- no unresolved blocker or requirement ambiguity remains.

Agent T alone evaluates these criteria and returns `td_loop_complete`.
