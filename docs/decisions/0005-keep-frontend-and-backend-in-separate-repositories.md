# ADR-0005: Keep Frontend and Backend in Separate Repositories

## Status
Accepted

## Context
GAM needs independent frontend and backend development while preserving a reliable API contract and one reproducible production deployment.

A monorepo could simplify atomic cross-layer changes, but it would also combine histories, pipelines, dependencies, and agent context before the project has demonstrated that this coupling is valuable. Separate repositories introduce compatibility and documentation risks that need explicit ownership rather than duplicated contracts.

The current project is pre-production, so unreleased API changes do not require legacy compatibility layers. Real compatibility obligations begin with the first production release.

## Decision
Keep the GAM frontend and backend in separate repositories. A monorepo is not part of the initial architecture.

The backend repository owns:

- the generated and versioned OpenAPI artifact;
- shared browser/API Requirement Specifications and ADRs;
- the canonical production composition and proxy template;
- deployment, backup, restore, and operations runbooks; and
- the controlled workflow that deploys a compatible frontend/backend artifact pair.

The frontend repository owns frontend source, build behavior, frontend-only documentation, and its versioned static artifact. It consumes the backend OpenAPI artifact to generate API types and links to shared contracts rather than duplicating them.

Frontend and backend pipelines publish immutable artifacts independently. Artifact publication does not silently deploy production. The backend-owned deployment workflow records and requires explicit approval for the compatible pair selected for production.

During pre-production, coordinated changes may replace unreleased contracts directly. After production begins, breaking API changes require an explicit coordinated deployment plan and only the necessary transition compatibility.

Introducing a monorepo or a dedicated infrastructure repository requires a future ADR based on demonstrated coordination needs.

## Alternatives considered

### Option 1: One monorepo
Pros:
- Frontend and backend contract changes can be reviewed and merged atomically.
- Shared tooling and one deployment workflow are straightforward to discover.
- Cross-layer refactors can use one worktree.

Cons:
- Histories, issues, pipelines, dependencies, and agent context become coupled.
- Repository migration adds work without a demonstrated current benefit.
- Independent release ownership becomes less explicit.

### Option 2: Separate repositories with duplicated contracts
Pros:
- Each repository is self-contained.
- Developers can read a local copy without following links.

Cons:
- Requirement, DTO, cookie, and deployment rules can diverge.
- There is no clear source of truth during a compatibility dispute.
- Manual synchronization creates recurring maintenance work.

### Option 3: Separate repositories with backend-owned shared contracts
Pros:
- Preserves independent histories and pipelines.
- Gives OpenAPI and shared integration rules one authoritative owner.
- Supports independently published artifacts and deliberate compatible deployment pairs.
- Avoids a third repository during the initial operational phase.

Cons:
- Cross-repository changes require coordination.
- Frontend Developers must follow links to authoritative shared documentation.
- The backend repository carries deployment concerns that span both artifacts.

## Consequences

Positive consequences:
- Frontend and backend can evolve and publish artifacts independently.
- Generated OpenAPI replaces handwritten DTO duplication as the machine-readable contract.
- Shared deployment and operational configuration remains reproducible and versioned.
- A later repository restructuring must be justified by observed costs.

Negative consequences:
- Breaking changes require coordinated pull requests and release planning.
- The frontend pipeline depends on a published backend contract artifact.
- The backend repository becomes the owner of cross-cutting deployment documentation.

## Related requirements

- `REQ-WEB-008`
- `REQ-WEB-009`
- `REQ-WEB-010`
- `REQ-WEB-011`
- `REQ-OPS-008`

## Related diagrams

- [`docs/diagrams/initial-production-topology.md`](../diagrams/initial-production-topology.md)

## Related ideas

- [`docs/ideas/openapi.md`](../ideas/openapi.md) — non-authoritative planning material pending future official OpenAPI documentation.

## Related videos

- None.
