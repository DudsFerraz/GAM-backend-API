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

## 2. Create the Codex-managed worktree

1. Select the original `gam-api` project in Codex.
2. Start a new task.
3. Select **Worktree**.
4. Select the updated `main` branch as the starting state.
5. Submit the scoped feature prompt.
6. Use **Create branch here** before the first commit.
7. Name the branch after one outcome, for example:

   ```text
   codex/persistence-soft-delete-restoration
   ```

## 3. Implement and commit the feature

1. Commit every intended change on the feature branch.
2. Confirm that the feature worktree is clean:

   ```powershell
   rtk git status --porcelain=v1
   ```

Do not continue while this command produces output.

## 4. Rebase and verify the feature

In the feature worktree:

```powershell
rtk git fetch origin
rtk git rebase origin/main
```

Resolve conflicts on the feature branch, then:

1. Run the required focused and final feature verification.
2. Confirm that the feature worktree remains clean.

Do not rebase a shared branch without coordinating with its other developers.

## 5. Prepare local `main`

In the primary checkout:

```powershell
rtk git status --porcelain=v1
rtk git switch main
rtk git pull --ff-only origin main
```

Stop if the primary checkout is dirty.

If `main` advanced, return to the feature worktree, rebase onto the new
`origin/main`, rerun affected verification, and repeat this step.

## 6. Integrate locally

In the clean primary checkout:

```powershell
rtk git merge --ff-only codex/feature-name
```

The merge must succeed as a fast-forward. If it refuses:

1. Do not perform a normal merge.
2. Do not create a merge commit.
3. Update and rebase the feature branch.
4. Rerun affected verification.
5. Retry the fast-forward merge.

## 7. Verify integrated `main`

Run the required final integration verification in the primary checkout.

If verification fails:

1. Do not push `main`.
2. Correct the failure in the feature worktree.
3. Commit and verify the correction.
4. Fast-forward local `main` again.
5. Repeat final integration verification.

## 8. Publish `main`

After final verification passes:

```powershell
rtk git push origin main
```

Never force-push `main`.

If the push is rejected because remote `main` advanced:

1. Keep the named feature branch and worktree.
2. Fetch the remote state:

   ```powershell
   rtk git fetch origin
   ```

3. Confirm that the primary checkout is clean.
4. Confirm that local `main` and the feature branch identify the same commit:

   ```powershell
   rtk git rev-parse main
   rtk git rev-parse codex/feature-name
   ```

5. Continue only if the two commit identifiers match.
6. Restore local `main` to the remote baseline:

   ```powershell
   rtk git reset --hard origin/main
   ```

7. In the feature worktree, rebase onto the new `origin/main`.
8. Rerun verification.
9. Repeat the local integration and publish steps.

If the primary checkout is dirty or the commit identifiers do not match, stop
and inspect the repository state instead of resetting.

## 9. Clean up

After `origin/main` contains the integrated feature:

1. Archive the Codex task.
2. Allow Codex to remove its managed worktree.
3. Confirm that the feature branch is no longer checked out:

   ```powershell
   rtk git worktree list
   ```

4. Delete the merged local feature branch:

   ```powershell
   rtk git branch -d codex/feature-name
   ```

5. Delete an optional remote feature branch when it is no longer required.

The feature lifecycle is complete only when remote `main` contains the work,
the managed worktree has been released, and the obsolete feature branch has
been removed.
