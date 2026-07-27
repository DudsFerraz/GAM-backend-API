# Developer Guidelines

This folder contains practical guides for human developers working on the GAM API.

Use these documents for day-to-day GAM development: running the backend, using Docker, checking dependencies, understanding the web topology, working with the API contract, and integrating a frontend.

This folder is exclusively for human developers. Normative agent behavior lives
under `.agents/skills/` and the repository agent instruction files.
Documentation standards live under `docs/documentation-guidelines/`.

## Guides

- [Running the System](running-the-system/README.md): how to run the backend locally, manage Docker Compose, execute Maven commands, inspect dependencies, and run OWASP dependency-check.
- [Prompting the Agent Workflow](prompting-agent-workflow.md): how to prepare a focused task brief for agent-assisted work.
- [Worktree Management](worktree-management/README.md): how to develop in Codex-managed worktrees and integrate locally through Agent W.
- [OpenAPI Developer Workflow](openapi-workflow.md): when and how to browse Swagger UI, generate and validate the contract, review changes, and generate frontend TypeScript types.
- [Frontend–Backend Integration](front-back/front-back-integration.md): practical same-origin browser, authentication, CSRF, and API integration guidance.
- [Frontend–Backend Concepts](front-back/front-back-concepts.md): explanations of CSRF, origins, tokens, refresh, authentication bootstrap, and CORS.
