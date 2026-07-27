# Recommended Worktree Workflow

Follow this process for one feature from creation through cleanup.

## 1. Prepare the primary checkout

In the primary `gam-api` checkout:

```powershell
rtk git status --porcelain=v1
rtk git switch main
rtk git pull --ff-only origin main
```

The status command must produce no output. Stop if the primary checkout is
dirty.

## 2. Create the managed worktree

1. Select the original `gam-api` project in Codex.
2. Start a new task with **Worktree** selected.
3. Select the updated `main` branch as the starting state.
4. Submit the scoped feature prompt.
5. Use **Create branch here** before the first commit.
6. Name the branch after one outcome, for example:

   ```text
   codex/persistence-soft-delete-restoration
   ```

## 3. Complete the feature

In the feature worktree:

1. Finish the scoped implementation.
2. Run the required canonical broad verification.
3. Commit every intended change without modifying the verified tree.
4. Confirm that the worktree is clean:

   ```powershell
   rtk git status --porcelain=v1
   ```

Do not continue while this command produces output.

## 4. Invoke Agent W

Leave the feature branch checked out in its managed worktree.

Start a new **Local** task from the original `gam-api` project. The primary
checkout must be clean and on `main`. Invoke:

```text
Use $gam-worktree-integration to integrate <feature-branch> from <managed-worktree-path>.
```

Agent W validates both worktrees, updates local `main`, rebases the feature when
required, runs only verification required by a new integration state, and
fast-forwards local `main`.

Do not switch the primary checkout to the feature branch.

## 5. Handle the preparation result

- For `integration_escalation`, perform only the requested developer action and
  resume the same Agent W task.
- If resolving the blocker changes feature content, rerun the canonical broad
  verification, commit the correction, and leave the feature worktree clean
  before resuming.
- For `ready_to_push`, continue to publication. The feature worktree and branch
  remain recovery anchors.

## 6. Publish `main`

The developer runs:

```powershell
rtk git push origin main
```

Never force-push `main`.

- If accepted, report the successful push in the same Agent W task.
- If rejected, report the rejection in that task. Do not reset `main`, delete
  the branch, or remove the worktree. Agent W returns an escalation with the
  required recovery decision.

## 7. Finalize

After an accepted push, Agent W:

1. Fetches `origin` and proves that `origin/main` contains the prepared commit.
2. Fast-forwards clean local `main` when remote `main` advanced afterward.
3. Removes the feature worktree through Git without force.
4. Verifies the worktree is absent from the Git registry.
5. Safely deletes the integrated feature branch.

If Agent W returns `finalization_escalation`, preserve every remaining resource
and resolve the reported blocker.

After `integration_complete`, archive the original feature task and the Agent W
task.

The lifecycle is complete when `origin/main` contains the feature, the managed
worktree has been removed, the obsolete feature branch has been deleted, and
the completed Codex tasks have been archived.
