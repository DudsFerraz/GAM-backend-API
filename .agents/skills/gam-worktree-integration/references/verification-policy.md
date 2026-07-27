# Agent W Verification Policy

## Objective

Run verification only for an integration state not covered by the developer's
recorded pre-commit verification attestation.

## Verification decision

If the remote base advanced, inspect the upstream delta and classify it by
effect, not file extension.

| Integration state or upstream delta | Agent W action |
|---|---|
| Feature already contains current `origin/main` | Do not repeat verification |
| `editorial_only` | Rebase without repeating verification |
| `normative_or_procedural` | Reload affected authority and re-evaluate the workflow or feature validity; escalate when renewed developer or reviewer acceptance is required |
| `build_or_runtime_relevant` | Rebase and run the current required canonical broad gate once |
| Rebase conflicts | Abort and escalate; do not verify |
| Verification requirement or pre-commit attestation is uncertain | Escalate before merge |
| Developer changes the branch after Agent W starts | Invalidate prior verification and restart from entry |

### Classification rules

Classify as `editorial_only` only when the change cannot affect:

- compiled or packaged application behavior;
- tests, fixtures, generated contracts, or verification infrastructure;
- feature requirements or architecture;
- Agent W's authority, commands, or required gates.

A file extension alone does not establish this category.

Classify requirements, ADRs, repository instructions, agent skills, and
guidelines as `normative_or_procedural`.

- Reload changed instructions and apply the current version.
- Escalate requirement or ADR changes for renewed acceptance.
- Escalate a changed instruction that contradicts the recorded workflow or
  requires an action outside Agent W's authority.
- Continue without broad verification only when re-evaluation establishes that
  the change has no bearing on the feature or integration process.

Classify production or test sources, dependencies, build files, application
configuration, migrations, verification infrastructure, fixtures, and generated
contract inputs as `build_or_runtime_relevant`.

For a mixed upstream delta, apply all relevant actions in this order:

1. re-evaluate normative or procedural changes;
2. escalate immediately if they invalidate safe continuation;
3. otherwise run the canonical broad gate when any build or runtime-relevant
   change remains;
4. never let an editorial component lower the required action.

If classification is uncertain, use `build_or_runtime_relevant` unless the
uncertainty concerns requirements, architecture, or authority; escalate those
instead.

## Placement

Run any required broad gate on the rebased feature before moving local `main`.
After a fast-forward merge, do not repeat it. Validate instead that:

- local `main` equals the verified feature tip;
- their tree objects are identical;
- the merge used `--ff-only`;
- both worktrees are clean;
- a final fetch shows that the tested base is still current.

Failure of any invariant blocks readiness to push.

## Remote race

Fetch again after verification and immediately before merge.

If `origin/main` advanced while verification ran:

1. do not merge the previously verified feature tip;
2. rebase onto the new remote tip;
3. apply this verification decision again.
