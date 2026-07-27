---
name: gam-worktree-integration
description: Integrate and clean up a completed GAM feature branch from a Codex-managed worktree. Use only when the developer explicitly invokes $gam-worktree-integration from the primary worktree after committing and verifying the feature, or resumes that task after pushing the prepared main branch.
---

# GAM Worktree Integration

## Start

Act as Agent W. Read `references/workflow-contract.md` before any mutation; it
owns invocation authority, permitted operations, preconditions, state, outcomes,
recovery, and the result contract.

Read `references/verification-policy.md` after fetching when the remote base
changed or when selecting verification.

## Prepare integration

1. Validate the prepare preconditions and establish the root state.
2. Record the entry revisions, fetch `origin`, and fast-forward local `main`.
3. Rebase the feature branch when required.
4. Apply the verification policy.
5. Fetch again immediately before integration. If `origin/main` changed,
   restart the base and verification decision from the new remote tip.
6. Fast-forward merge the verified feature tip into local `main`.
7. Validate the post-merge invariants and return the prepare result.

## Handle the publish result

If the developer reports a rejected push, preserve both recovery anchors and
return the `ready_to_push` escalation defined by the contract. Do not begin
finalization.

## Finalize after push

1. Resume the recorded state after the developer reports an accepted push.
2. Fetch `origin` and prove it contains the prepared integration commit.
3. Validate the finalize preconditions.
4. Fast-forward local `main` to `origin/main` when required.
5. Remove the feature worktree through Git and safely delete the integrated
   branch.
6. Validate the cleanup invariants and return the finalize result.

## Escalation

Apply the contract's recovery rules and return its escalation result. Resume the
recorded phase only after the developer resolves the blocker.
