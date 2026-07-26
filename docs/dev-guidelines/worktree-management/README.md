# Worktree Management

**Audience:** GAM developers using Codex

## Purpose

Use Codex-managed worktrees to isolate feature development while keeping the
primary `main` checkout stable and ready for local integration.

## Documents

* [Recommended workflow](recommended-workflow.md)
* [Workflow diagram](workflow-diagram.md)

## Scope

This guideline covers Codex-managed disposable worktrees and local integration
into `main`.

Manual and permanent worktrees are currently out of scope.

## Terminology

| Term | Meaning |
| --- | --- |
| Primary checkout | The normal `gam-api` directory, with `main` checked out. |
| Managed worktree | A disposable worktree created by starting a Codex task with **Worktree** selected. |
| Feature branch | The named branch containing one feature's committed work. |
| Clean worktree | A worktree with no staged, unstaged, or untracked files. |
| Integration boundary | A rebase, final verification, merge, publish, archive, or removal. |

Branches are not clean or dirty; worktrees are.

## Default policy

1. Use one Codex-managed worktree per independent feature, fix, or
   investigation.
2. Keep managed worktrees under `$CODEX_HOME`; treat their generated paths as
   internal details.
3. Keep the primary checkout on `main`.
4. Do not implement features, fixes, experiments, or refactors in the primary
   checkout.
5. Create a named feature branch in the managed worktree before the first
   commit.
6. Keep the primary checkout clean whenever it is used to update, merge,
   verify, or push `main`.
7. Keep the feature worktree clean at every integration boundary.
8. Rebase the feature branch onto the latest `origin/main`.
9. Integrate locally with `merge --ff-only`.
10. Verify the integrated `main` before pushing it.
11. Never force-push `main`.
12. Archive the Codex task only after `origin/main` contains the feature.

## Cleanliness

Check either worktree with:

```powershell
rtk git status --porcelain=v1
```

No output means the worktree is clean.

The feature worktree may be dirty during implementation. Before integration,
all intended changes must be committed and unrelated files must be moved or
removed.

If the primary checkout is dirty, stop the integration and preserve its changes
on an appropriate branch or worktree. Do not use stashing as the routine way to
make `main` appear clean.

## Naming

Codex-managed paths are opaque and must not be renamed or moved manually.
Identify work through related task, branch, and commit names:

```text
Task:    Persistence — add soft-delete restoration
Branch:  codex/persistence-soft-delete-restoration
Commit:  feat(persistence): add soft-delete restoration
```

## Codex lifecycle

Create the worktree by selecting the original `gam-api` project and choosing
**Worktree** for the new task. Use **Create branch here** before the first
commit.

After local integration, verification, and publication of `main`, archive the
task and allow Codex to remove the managed worktree.

A Codex recovery snapshot is not a replacement for a commit, branch, or remote
copy.

## Local files

Tracked files are available in every worktree. Use `.worktreeinclude` only for
ignored files that a managed worktree requires. Dependencies, build output,
containers, generated files, and IDE state are not assumed to be shared.
