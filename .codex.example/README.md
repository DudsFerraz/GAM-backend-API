# Codex configuration example

Copy this directory to `.codex/` to use the repository's example Codex
configuration:

```powershell
Copy-Item -Recurse .codex.example .codex
```

The `.codex/` directory is ignored by Git so that each developer can customize
their local configuration without changing the shared example.
