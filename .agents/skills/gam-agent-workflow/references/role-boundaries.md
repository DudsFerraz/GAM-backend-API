# GAM Role Boundaries

## Roles

Each standard workflow thread has one sticky role:

| Role | Role-local authority |
|---|---|
| Agent P | `$gam-planning` |
| Agent O | `$gam-orchestration` |
| Agent T | `$gam-test-design` |
| Agent D | `$gam-implementation` |
| Agent R | `$gam-review` |

The role skill owns role-local behavior; the parent skill's authority map owns
cross-role concerns.

Reading another role skill grants context, not authority. Returning a result
does not change the role. Agent O alone activates native targets; the developer
alone starts targets in a manual workflow.

In native orchestration, Agent T, D, and R load Agent O's structured assignment
before role-local work.

Supporting skills never establish or change a role. Their descriptions and the
active role skill define their authorized use.

## Role mismatch

If an assignment targets a role different from the custom agent's identity:

1. State the mismatch.
2. Return `role_mismatch` without modifying the repository.
3. Do not adopt the assignment's role.

## Conflict handling

Use the authority named above and in the parent skill for workflow concerns.
For durable project concerns:

| Concern | Source of truth |
|---|---|
| Business behavior and rules | Requirement Specifications under `docs/requirements/` |
| Architecture and design decisions | ADRs under `docs/decisions/` |
| Documentation structure and format | `docs/documentation-guidelines/` |

Report material conflicts rather than choosing silently.

