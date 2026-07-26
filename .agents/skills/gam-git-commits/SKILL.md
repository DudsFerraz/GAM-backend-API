---
name: gam-git-commits
description: Draft safe GAM staging and Conventional Commit commands without executing Git changes. Use when the developer requests commit planning, commit messages, staging commands, push guidance, or a version-control diff summary.
---

# GAM Git Commits

## Overview

Use this skill to help the developer commit work while preserving manual control over Git history. Never run `git add`, `git commit`, or `git push`; only propose the exact PowerShell commands the developer should run.

## Workflow

1. Inspect the relevant Git state.
   - Use `rtk git status --short`.
   - Use `rtk git diff -- <path>` for tracked unstaged changes.
   - Use `rtk git diff --cached -- <path>` only to inspect already staged changes.
   - For untracked files, read or summarize only the files needed to understand the intended commit.
2. Identify the task scope.
   - Include only files changed by the current implementation scope.
   - Exclude unrelated developer changes.
   - If the diff contains distinct logical changes, split the proposal into multiple commits.
3. Draft Conventional Commit messages.
   - Use the format `type(scope): summary`.
   - Prefer `feat`, `fix`, `test`, `docs`, `refactor`, `chore`, or `build`.
   - Keep the subject imperative, concise, and under 72 characters when practical.
   - Use body lines to explain why the change exists and what important behavior changed.
4. Output commands only for the developer to run.
   - Provide at least one `git add ...` command and one `git commit ...` command for each proposed commit.
   - Use multiple `-m` arguments: one for the subject and one or more for the body.
   - Produce single-line PowerShell commands.
   - Do not use Bash line continuations with backslashes.
   - Do not run the commands.

## Output Shape

For one commit:

```powershell
git add <file-or-folder> <file-or-folder>
git commit -m "type(scope): concise summary" -m "Explain the change and its intent."
```

For multiple commits, group each command pair under a short label and explain why the split is useful.

## Safety Rules

- Never run `git add`, `git commit`, or `git push`.
- Never suggest `git add .` unless the diff has been proven to contain only files that belong in the commit.
- Never include unrelated developer changes in proposed staging commands.
- If unsure whether a file belongs to the implementation scope, call it out and leave it unstaged.
