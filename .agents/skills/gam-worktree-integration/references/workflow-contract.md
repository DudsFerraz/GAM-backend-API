# Agent W Workflow Contract

## Invocation authority

Only an explicit developer invocation in a root task may start this workflow.
Reject delegated invocation. No standard workflow outcome starts Agent W.

## Authority

Agent W may only:

- inspect Git and worktree state;
- fetch `origin`;
- fast-forward local `main`;
- rebase a private feature branch;
- abort only a rebase that Agent W started;
- run repository-authorized verification;
- fast-forward merge the verified feature into local `main`;
- remove the feature worktree through Git after remote inclusion is proven;
- safely delete the integrated local feature branch.

Do not change feature content, resolve conflicts, delegate, push, use force or
hard-reset operations, or clean up before remote inclusion is proven.

## Root state

Keep this state in the Agent W root task; do not persist runtime values:

```json
{
  "schema_version": "gam-worktree-integration-state/v1",
  "workflow_id": "<stable integration identifier>",
  "phase": "<prepare|ready_to_push|finalize|complete|escalated>",
  "feature_worktree": "<absolute path>",
  "feature_branch": "<branch>",
  "primary_worktree": "<absolute path>",
  "remote": "origin",
  "target_branch": "main",
  "entry_feature_tip": "<sha>",
  "entry_merge_base": "<sha>",
  "fetched_origin_main": "<sha>",
  "rebased_feature_tip": "<sha-or-null>",
  "verified_feature_tip": "<sha-or-null>",
  "integrated_main_tip": "<sha-or-null>",
  "upstream_change_classification": [],
  "verification_required": null,
  "verification_reason": "<reason-or-null>",
  "verification": [],
  "publish_attempt": "<not_reported|accepted|rejected>",
  "developer_attestation": {
    "feature_complete": true,
    "worktree_clean": true,
    "broad_gate_passed_before_commit": true,
    "branch_private_and_rebase_safe": true
  },
  "suspended_phase": null,
  "blockers": [],
  "human_intervention_required": false
}
```

Record every commit identifier before the associated mutation. Never continue
when observed state disagrees with recorded state.

## Prepare preconditions

Preparation requires:

- the feature directory is a registered linked worktree of the primary
  repository, confirmed by the shared Git common directory and worktree
  registry;
- the feature has a named, non-`main` branch;
- Agent W is running from the primary worktree, not the removal target;
- the feature and primary worktrees are clean;
- neither worktree has a merge, rebase, cherry-pick, or revert in progress;
- the primary worktree is on local `main`;
- local `main` has no unpublished commits and can fast-forward to
  `origin/main`;
- the feature branch is private and safe to rebase;
- the feature history after its merge base is linear;
- the developer's verification attestation is uncontradicted.

Any failed precondition produces `integration_escalation` without mutation,
except that a rebase Agent W started may be aborted to restore the recorded
entry state.

## Finalize preconditions

Finalization requires:

- the recorded phase is `ready_to_push`;
- the developer reported an accepted push;
- a fresh fetch proves `origin/main` contains `integrated_main_tip`;
- the feature branch still equals `integrated_main_tip`;
- the primary worktree is clean, on local `main`, and can fast-forward to
  `origin/main`;
- the feature worktree is registered, clean, still checks out
  `feature_branch`, and has no Git operation in progress.

If any precondition fails, preserve the worktree and branch and produce
`finalization_escalation`.

## Outcomes

| Phase | Outcome | Meaning |
|---|---|---|
| prepare | `ready_to_push` | Local `main` equals the verified feature tip; feature recovery anchors remain |
| prepare | `integration_escalation` | Safe local integration cannot continue |
| ready_to_push | `integration_escalation` | The push was rejected; preserve recovery anchors and require developer resolution |
| finalize | `integration_complete` | Remote inclusion is proven and cleanup completed |
| finalize | `finalization_escalation` | Push inclusion or safe cleanup cannot be proven |

For a rejected push, set `publish_attempt` to `rejected`,
`suspended_phase` to `ready_to_push`, and do not change local `main`, the feature
branch, or the feature worktree. Resume preparation only after the developer
resolves the recorded blocker and the prepare preconditions pass again.

## Result

End each active turn with a concise human summary and one fenced `json` object:

```json
{
  "schema_version": "gam-worktree-integration-result/v1",
  "workflow_id": "<id>",
  "role": "agent_w",
  "phase": "<prepare|ready_to_push|finalize>",
  "outcome": "<allowed outcome>",
  "feature_worktree": "<absolute path>",
  "feature_branch": "<branch>",
  "commits": {
    "entry_feature_tip": "<sha>",
    "origin_main": "<sha>",
    "verified_feature_tip": "<sha-or-null>",
    "integrated_main_tip": "<sha-or-null>"
  },
  "upstream_change_classification": [
    "<editorial_only|normative_or_procedural|build_or_runtime_relevant>"
  ],
  "verification_reason": "<why the broad gate ran or was not repeated>",
  "publish_attempt": "<not_reported|accepted|rejected>",
  "verification": [
    {
      "command": "<exact command-or-invariant>",
      "status": "<passed|failed|blocked|not_run>",
      "observed": "<evidence>"
    }
  ],
  "cleanup": {
    "worktree_removed": false,
    "branch_deleted": false
  },
  "blockers": [],
  "human_intervention_required": false,
  "developer_action": "<push command, resolution needed, or none>"
}
```

For `ready_to_push`, cleanup fields must remain false. For either escalation,
`human_intervention_required` must be true and `developer_action` must identify
one concrete next decision or action.

## Recovery invariants

- Before merge, failure leaves local `main` unchanged.
- A conflicted Agent W rebase is aborted before escalation whenever Git can
  restore the recorded state safely.
- After merge but before push, the feature worktree and branch remain available.
- Cleanup begins only after `origin/main` contains `integrated_main_tip`.
- Remove the worktree without force and verify it is absent from the worktree
  registry before deleting the branch.
- Safe branch deletion must succeed without force; otherwise preserve the branch
  and escalate.
