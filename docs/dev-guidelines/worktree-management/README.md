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
| Agent W | The explicitly invoked `$gam-worktree-integration` workflow that prepares and finalizes local integration. |
| Ready to push | Agent W has verified the integration state and fast-forwarded local `main`; the worktree and branch remain available. |
| Integration boundary | Pre-commit verification, commit, Agent W preparation, publish, finalization, or cleanup. |

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
6. Run the required canonical broad verification immediately before committing
   the completed feature.
7. Keep both worktrees clean at every integration boundary.
8. Invoke Agent W explicitly from a new Local task on the primary checkout.
9. Let Agent W rebase, conditionally verify the new integration state, and
   fast-forward local `main`.
10. Do not repeat broad verification after an identity-preserving fast-forward.
11. Push `main` only after Agent W returns `ready_to_push`; never force-push it.
12. Resume the same Agent W task after the push so it can prove remote inclusion
    and remove the worktree and branch.
13. Archive the completed Codex tasks after Agent W returns
    `integration_complete`.

## Cleanliness

Check either worktree with:

```powershell
rtk git status --porcelain=v1
```

No output means the worktree is clean.

The feature worktree may be dirty during implementation. Before integration,
the required broad verification must pass, all intended changes must be
committed, and unrelated files must be moved or removed.

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

After the verified feature is committed, start a new Local task on the primary
checkout and invoke `$gam-worktree-integration` with the feature worktree path
and branch. Push only after `ready_to_push`, then resume Agent W for cleanup.

After `integration_complete`, archive the feature task and the Agent W task.

A Codex recovery snapshot is not a replacement for a commit, branch, or remote
copy.

## Local files

Tracked files are available in every worktree. Use `.worktreeinclude` only for
ignored files that a managed worktree requires. Dependencies, build output,
containers, generated files, and IDE state are not assumed to be shared.
