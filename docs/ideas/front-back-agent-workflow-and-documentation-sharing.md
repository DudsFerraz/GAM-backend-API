# Idea: Frontend and backend agent-workflow and documentation sharing

## Non-normative status

This document is an exploratory collection of ideas from the initial
frontend/backend agent-workflow discussion. It intentionally preserves
alternatives, concerns, disagreements, and suggestions that may later be
approved, rejected, split, or adapted.

This document is not a Requirement Specification, ADR, guideline, agent
instruction, implementation plan, or source of truth. It does not change the
current ownership defined by accepted requirements, ADRs, repository
instructions, or the OpenAPI contract.

No idea recorded here should be implemented merely because it appears in this
file. A later planning session must inspect the current state of both
repositories, resolve the open questions, identify conflicts with accepted
documentation, and route durable decisions to their proper artifacts.

## Context

GAM currently has separate backend and frontend repositories:

- backend: `gam-api`;
- frontend: `gam-frontend`;
- common local layout: neighboring directories under the same GAM directory.

Keeping them separate was a conscious architecture decision. Independent
repositories preserve separate histories, dependencies, pipelines, releases,
worktrees, and agent context, but make cross-repository documentation and agent
workflow consistency harder.

The neighboring checkout is convenient for local inspection, but it must not
be assumed to exist in CI, remote development, another developer's machine, or
an isolated Codex worktree.

The backend currently contains extensive agent and documentation material,
including:

- `.agents/skills/`;
- `.codex.example/` and developer-local `.codex/` configuration;
- `.worktreeinclude.example` and `.worktreeinclude`;
- `AGENTS.md`;
- `skills-lock.json`;
- project-wide Requirement Specifications, ADRs, diagrams, terminology, and
  documentation guidelines;
- backend-specific software and verification guidelines.

The frontend already has its own `AGENTS.md`, skills, `skills-lock.json`, and
frontend documentation. Its current documents link to backend-owned shared
contracts instead of maintaining editable copies.

## Desired outcomes

Ideas discussed for the desired outcome include:

- preserve the separate-repository decision;
- give agents in both repositories reliable access to shared GAM context;
- avoid manually editing the same shared skill or document twice;
- avoid creating competing sources of truth;
- keep each repository independently reproducible;
- make the organization and discovery of docs and skills consistent;
- allow backend and frontend rules to remain technology-specific;
- support future GAM repositories without redesigning everything again;
- keep cross-repository upgrades explicit and reviewable;
- distinguish desired product behavior from the transport capabilities exposed
  by a particular backend release.

## Possible classification models

### Original three-category model

The original proposal classified material as:

- **shared**: both repositories use the exact same file;
- **adapted**: useful to both repositories but requires repository-specific
  changes;
- **independent**: relevant to only one repository and not replicated.

This remains a possible simple vocabulary.

### Expanded five-category model

An expanded model was suggested because an exact copy can be authoritative,
generated, vendored, or developer-local for very different reasons:

1. **Canonical project source**: one authoritative editable copy. Consumers
   link to or derive from it.
2. **Versioned mirror**: an exact, generated, read-only consumer copy pinned to
   a source revision and protected against downstream editing.
3. **Repository profile**: a repository-specific implementation of a common
   GAM concern or interface.
4. **Repository-owned**: material that exists only because of the
   repository's technology or responsibility and has no required counterpart
   in other repositories.
5. **Developer-local**: machine- or developer-specific state that must not be
   synchronized as project truth.

The names and number of categories remain open for refinement.

### Repository profile versus repository-owned

A repository profile is analogous to a local implementation of a shared
interface. Both repositories need the concern, but the contents differ.

Examples:

- both repositories need an `AGENTS.md`, but each file routes to different
  guidelines and verification commands;
- both may have an implementation skill, but one implements Spring/Java
  behavior and the other implements React/TypeScript behavior;
- both may have review rules, but their architecture and verification evidence
  differ.

Repository-owned content has no required counterpart elsewhere.

Examples:

- Maven, Testcontainers, JPA, Flyway, and Spring rules are backend-owned;
- React component, accessibility, responsive UI, Tailwind, Vite, and Vitest
  rules are frontend-owned;
- frontend design and copywriting skills need not exist in the backend;
- backend database and persistence documentation need not exist in the
  frontend.

A single file can contain both concepts. For example, backend `AGENTS.md` can
be a repository profile as a whole while its Maven and Docker sections contain
backend-owned rules.

## `AGENTS.md` organization

### Do not use one identical root file

One fixed `AGENTS.md` for both repositories would make guideline routing and
verification invalid because the referenced paths and tools do not exist in
both repositories.

Each repository should own its root `AGENTS.md`. A common structural contract
could be adopted without requiring identical content:

```text
AGENTS.md
  Repository role
  Shared GAM authority and terminology
  Source-of-truth and conflict policy
  Guideline routing
  Repository architecture and technology rules
  Verification policy
  Git and change-discipline rules
```

Under this idea:

- the backend guideline-routing table points to backend files such as Spring,
  JPA, OpenAPI, database, and Maven guidance;
- the frontend guideline-routing table points to frontend files such as React,
  TypeScript, UI presentation, accessibility, generated API use, Vitest, lint,
  and build guidance;
- both state how to find shared GAM requirements and terminology;
- neither references a path that does not exist in its own consumable context.

### Possible shared base policy

Options for common `AGENTS.md` policy include:

- repeat a very small manually maintained common section in each repository;
- generate a marked common section from one canonical template;
- keep the root file local and make it route agents to a pinned shared-context
  document;
- use developer-global Codex guidance as a convenience only.

Developer-global `~/.codex/AGENTS.md` should not be the project authority
because it is developer-local, may affect unrelated repositories, and is not
reproducibly versioned with GAM.

Nested repository `AGENTS.md` or override files remain an option for
subtree-specific rules when a root file becomes too broad.

## Skill disposition for `gam-frontend`

The following classification records the Developer's current direction. It is
still subject to validation during planning, especially where a backend skill
contains hard-coded paths or assumes backend-owned documentation.

### Backend skills to have in the frontend

Some may require adaptation:

- `.agents/skills/gam-git-commits`;
- `.agents/skills/gam-grill`;
- `.agents/skills/gam-planning`;
- `.agents/skills/gam-requirements`;
- `.agents/skills/gam-worktree-integration`;
- `.agents/skills/grilling`.

Possible adaptation concerns:

- `gam-git-commits` must follow the frontend's Git policy and verification
  vocabulary while preserving manual control of Git history if that remains a
  GAM-wide rule;
- `gam-grill` and `grilling` must route to the frontend's available docs and
  must not assume backend source paths;
- `gam-planning` must use frontend architecture, UI, accessibility, and
  verification guidance;
- `gam-requirements` must distinguish frontend-owned requirements from shared
  project requirements. It must not create a competing frontend copy of a
  backend- or future-governance-owned shared requirement;
- `gam-worktree-integration` may be reusable if its Git workflow is genuinely
  technology-neutral, but its verification and artifact expectations may need
  a frontend profile.

### Backend skills not to have in the frontend

- `.agents/skills/diagnosing-bugs`;
- `.agents/skills/gam-agent-handoff`;
- `.agents/skills/gam-agent-workflow`;
- `.agents/skills/gam-human-handoff`;
- `.agents/skills/gam-orchestration`;
- `.agents/skills/gam-test-design`;
- `.agents/skills/gam-implementation`;
- `.agents/skills/gam-review`.

The backend's `gam-agent-workflow`, including its custom TDD-review cycle, is
not necessary for `gam-frontend`. The backend custom Agent T, D, and R
configurations are also discardable for the frontend. The frontend should not
replicate `.codex.example/agents/gam-agent-t.toml`,
`gam-agent-d.toml`, or `gam-agent-r.toml` merely for structural symmetry.

Although backend `gam-implementation` and `gam-review` should not be copied,
the frontend should have equivalent capabilities with different rules.
Options include:

- keep the generic names `gam-implementation` and `gam-review` but provide
  frontend-specific repository profiles;
- use explicit names such as `gam-frontend-implementation` and
  `gam-frontend-review`;
- rely on a simpler frontend workflow without custom agents while retaining
  invokable implementation and review skills.

Frontend equivalents would need to cover React/TypeScript implementation,
generated API boundaries, user-facing Portuguese, responsive behavior,
accessibility, Vitest/Testing Library, lint, and build verification rather than
Spring, Maven, JPA, or Testcontainers.

### Backend skill with uncertain frontend disposition

- `.agents/skills/gam-domain-modeling`.

Reasons to include or adapt it:

- frontend work uses the same GAM domain concepts;
- it can prevent UI terminology and interaction models from inventing a
  competing domain language;
- it can route frontend discoveries back to the canonical ubiquitous language
  or Requirement Specifications.

Reasons not to copy it unchanged:

- it currently assumes canonical documents under local backend paths;
- it owns edits to the canonical backend ubiquitous language, which a frontend
  agent may not be authorized or able to modify;
- frontend presentation modeling is related to, but not identical to, domain
  modeling.

Possible adaptations include a read-only frontend mode, a handoff that proposes
canonical backend changes, or a shared skill that resolves authority through a
repository context manifest.

### Existing frontend-specific and external skills

The frontend currently selects external skills such as brainstorming,
copywriting, frontend design, requesting code review, token efficiency, and web
design guidance. These remain frontend-owned choices unless a later decision
makes one of them GAM-wide.

Upstream third-party skills should remain visibly pinned to their upstream
sources. Only GAM-modified forks need GAM-owned centralization.

## Other agent and configuration assets

### `.codex.example/`

Possible treatment:

- repository profile rather than an exact full copy;
- share only technology-neutral defaults or definitions that are genuinely
  used by both repositories;
- keep concurrency, enabled agents, sandbox expectations, and repository role
  configuration local;
- do not add custom T/D/R agents to the frontend when its workflow does not use
  them.

### `.codex/`

Treat as developer-local configuration. Do not make it a cross-repository
source of truth. A tracked example may be used to provision it.

### `.worktreeinclude.example`

Treat as a repository profile because the local Codex files and other
worktree-provisioned assets may differ.

### `.worktreeinclude`

Treat as developer-local unless a later decision explicitly establishes a
different policy.

### `skills-lock.json`

Keep one lockfile per repository because their selected skills differ. A
shared skill may appear in both lockfiles at the same or intentionally
different pinned versions. Do not copy the entire lockfile between
repositories.

## Shared documentation and source-of-truth ideas

### Shared requirements apply to both repositories

Accepted project-wide business and domain requirements should apply to both
frontend and backend when the concern crosses the repository boundary. The
frontend should not be isolated from business truth merely because it is in a
different repository.

OpenAPI should be authoritative for the machine-readable HTTP transport
boundary, including routes, request and response schemas, parameters, status
codes, and declared transport security. It should not replace requirements,
terminology, or frontend behavior documentation.

A concern-oriented authority matrix was suggested:

| Concern | Possible authority |
| --- | --- |
| Business behavior and domain rules | Accepted shared Requirement Specifications |
| GAM-wide terminology and translations | Global ubiquitous language |
| Cross-system architecture | Shared ADRs |
| Available HTTP transport for a backend version | Pinned released OpenAPI artifact |
| Frontend UX, accessibility, presentation, and client state | Frontend-owned requirements and guidelines |
| Backend implementation and persistence | Backend-owned software guidelines |
| Existing code | Evidence of current behavior, not business truth by itself |

Requirements can describe accepted target behavior while a pinned OpenAPI
artifact describes the currently consumable backend interface. If the
frontend's supported contract lacks an operation needed by an accepted
requirement, that is a cross-repository integration gap. The frontend should
not invent an endpoint or handwritten transport DTO to hide the gap.

### Frontend-only requirements

The frontend may own Requirement Specifications for frontend-only behavior,
including presentation, interaction, accessibility, responsive behavior, and
client-state rules. These must reference rather than redefine applicable
shared requirements.

A later plan must decide how requirement IDs distinguish project-wide,
backend-only, and frontend-only scopes without implying different business
truths for the same concern.

### Ubiquitous language and bilingual glossary

The English-Portuguese glossary has been accepted and added to the canonical
backend `docs/ubiquitous-language.md`.

The rationale is that backend code and technical documentation are primarily
English, while frontend product presentation is Brazilian Portuguese. The
frontend needs canonical translations for GAM-wide domain concepts.

The glossary establishes ideas such as:

- `Oratorio` to `Oratório`;
- `Member` to `Membro`;
- `Account` to `Conta`, not `Usuário`;
- `Coordinator` to `Coordenador`, while `Coordenação` describes a
  responsibility or access presentation;
- `Membership Solicitation` to `Solicitação para se tornar membro`, with
  `solicitação` accepted once the context is clear;
- transport codes, enum values, permission codes, identifiers, and code symbols
  remain unchanged;
- a translation does not authorize exposing internal values such as UUIDs.

The frontend's existing phrase `solicitações de membresia` was identified as a
possible future reconciliation with the accepted glossary.

## How the frontend could consume shared documents without a third repository

### Option: links to backend documents

The frontend can link to backend-owned requirements, ADRs, and ubiquitous
language. This is simple and matches the current direction, but links alone
have limitations:

- mutable `main` links do not pin the context used by a frontend change;
- agents may lack network access or private-repository authentication;
- linked documents are unavailable in a fully isolated clone;
- the neighboring backend directory is not guaranteed to exist.

Links remain useful for human navigation even if another delivery mechanism is
added.

### Option: pinned read-only shared-context mirror

The backend could remain canonical while the frontend commits a generated,
read-only mirror of selected documents.

Possible backend assets:

```text
shared-context-manifest.json
docs/ubiquitous-language.md
docs/requirements/...
docs/decisions/...
docs/diagrams/...
```

Possible frontend assets:

```text
.gam/
  shared-context.lock.json
  context/
    ubiquitous-language.md
    requirements/...
    decisions/...
scripts/
  update-gam-context.ps1
```

The lock could record:

- source repository;
- immutable commit, tag, or release;
- allowlisted files;
- content hashes;
- context-bundle format version;
- generation timestamp only if reproducibility is not harmed.

The update workflow could fetch from the immutable source, optionally use the
adjacent checkout for local efficiency, mark mirrors as generated, and fail CI
if a consumer edits them manually.

This creates duplicate bytes but not a competing source of truth because the
consumer copy is generated, pinned, attributed, and read-only.

### Option: mirror shared documents under `docs/`

To maximize identical documentation structure, the generated mirror could
instead appear at familiar paths such as:

```text
docs/ubiquitous-language.md
docs/requirements/shared/...
docs/decisions/shared/...
```

This improves discovery and structural parity but increases the risk that a
consumer mistakes a mirror for an editable local authority. Generated headers,
a lockfile, directory ownership rules, and CI drift checks would be required.

The `.gam/context/` and `docs/.../shared/` layouts remain competing options for
later planning.

### Option: repository context manifest

Each repository could have a small committed manifest describing its role and
authorities without hard-coded absolute sibling paths. Example ideas:

```toml
repository_role = "frontend"
shared_contract_owner = "DudsFerraz/GAM-Backend-API"
shared_contract_revision = "<immutable revision>"
agent_kit_version = "<version>"
openapi_lock = "openapi-contract.lock.json"
```

A developer-local ignored override could point to an adjacent checkout. Shared
skills could resolve paths through this manifest rather than assuming
`docs/requirements/` always means the canonical project requirements.

### Option: MCP

A read-only GAM documentation MCP remains an option. Possible tools or
resources include:

```text
list_gam_documents(revision)
read_gam_document(revision, path)
search_gam_context(revision, query)
```

Potential advantages:

- centralized semantic search;
- one retrieval interface for multiple repositories or document systems;
- centralized authentication and access control;
- no committed mirror required;
- easier discovery once the project has many consumers.

Potential disadvantages:

- another service or local process to build, configure, secure, and maintain;
- agent work may fail when the server, network, or authentication is
  unavailable;
- a live MCP that always serves the latest `main` can destroy reproducibility;
- project `.codex/config.toml` and developer authentication still require
  rollout;
- static versioned Markdown may not justify a live protocol yet.

If adopted, the MCP should return source provenance and support immutable
revisions. Git should remain the source of truth; MCP should be a retrieval
mechanism, not an independent documentation authority.

MCP was not recommended as the initial mechanism, but it remains a candidate
when multiple repositories, semantic search, or access-control needs justify
the operational cost.

### Option: Git submodule

A pinned backend or governance submodule could expose shared context. Benefits
include an explicit Git revision and ordinary file access. Concerns include
pulling more of the source repository than needed, submodule onboarding,
worktree behavior, update friction, and skill discovery when files are not
materialized at expected `.agents/skills/<skill>/` paths.

### Option: Git subtree or vendored files

A subtree or vendored copy provides ordinary files in the consumer repository.
It is easier for tools to discover but needs clear provenance, update commands,
and drift checking. Manual subtree merges may still be more complex than a
small versioned bundle.

### Option: sibling paths, symlinks, or junctions

Direct references to a neighboring checkout, symlinks, or Windows junctions
offer immediate local sharing but are fragile across independent clones, CI,
remote environments, operating systems, and Codex worktrees. They may remain a
developer-local optimization but should not be the only project mechanism.

### Option: developer-global skills

Installing shared skills only in a developer's global Codex home avoids
repository copies but loses repository-level version pinning, contributor
reproducibility, and CI validation. It can be a personal convenience, not the
sole GAM distribution model.

## Updating shared skills

A centralized source does not eliminate adoption work in each independent
repository. It should eliminate editing the shared content twice.

A possible release and consumption workflow is:

1. Edit the canonical shared skill once.
2. Validate it and publish an immutable tag, release, archive, or commit.
3. Pin that version in each consuming repository.
4. Materialize it under the required `.agents/skills/` location.
5. Verify hashes and reject downstream manual edits.
6. Open automated consumer upgrade pull requests.
7. Run repository-specific verification before accepting each upgrade.

Backend and frontend may intentionally use different bundle versions during a
coordinated rollout. Automatically following `main` or `latest` avoids version
bumps but risks silent agent-behavior changes and was discouraged.

Possible automation includes a bot, scheduled workflow, or release-triggered
consumer pull requests. Consumer updates should remain reviewable rather than
mutating both repositories invisibly.

## Common documentation organization and structure

The Developer proposed that frontend and backend follow the same broad docs and
skills organization pattern even when their contents differ.

Candidate common documentation locations include:

- `docs/about-gam/`;
- `docs/agents-guidelines/` or the singular naming
  `docs/agent-guidelines/`, to be resolved consistently;
- `docs/decisions/`;
- `docs/diagrams/`;
- `docs/documentation-guidelines/`;
- `docs/ideas/`;
- `docs/requirements/`;
- `docs/software-guidelines/`;
- `docs/ubiquitous-language.md`;
- `docs/api/`, where relevant;
- `docs/architecture/`, where relevant;
- `docs/integration/`, where relevant;
- `docs/testing/`, where relevant;
- `docs/deploy/`, where relevant;
- other directories accepted during planning.

Structural consistency could improve:

- agent discovery and guideline routing;
- transfer of workflows between repositories;
- contributor onboarding;
- predictable placement of new artifacts;
- future automation that validates documentation topology.

Structural parity must not imply duplicated authority or identical content.
Possible distinctions include:

- backend `docs/software-guidelines/` contains backend software rules;
- frontend `docs/software-guidelines/` contains frontend software rules;
- backend `docs/requirements/` currently contains shared and backend-owned
  requirements;
- frontend `docs/requirements/` could contain frontend-owned requirements plus
  an explicitly generated shared mirror or links;
- each repository's `docs/decisions/` can contain repository-owned ADRs, while
  cross-project ADRs remain canonical in one owner and may be mirrored under a
  marked `shared/` subtree;
- `docs/ubiquitous-language.md` in the frontend could be a generated mirror,
  not an independently editable document;
- documentation-guideline cores may be shared while OpenAPI, frontend UI, and
  technology-specific guidance remain local.

Open questions include whether every repository should contain every directory
even when empty, whether a `README.md` should explain intentionally absent
categories, and whether shared mirrors should live under normal `docs/` paths
or a dedicated `.gam/context/` namespace.

The same pattern principle may apply to `.agents/skills/`: stable names and
folder conventions can coexist with repository-specific skill selections and
implementations.

## Dedicated GAM governance or agent-kit repository

### Option: do not create it yet

One suggestion was to keep the backend as the current canonical owner, extract
a small technology-neutral bundle there, and prove pinned frontend consumption
before adding another repository.

Possible signals that later justify extraction include:

- a third GAM repository needs the same context or skills;
- shared agent material releases independently of backend releases;
- backend ownership creates permissions or review bottlenecks;
- repeated coordinated upgrades demonstrate real maintenance cost;
- shared governance needs separate CODEOWNERS or approval rules.

This option reduces immediate repository and release overhead while keeping a
future extraction path open.

### Option: create a dedicated repository now

A dedicated source could prevent the backend from remaining the accidental
owner of every cross-project concern and prepare for future GAM repositories.

Possible names include:

- `gam-project-governance`;
- `gam-agent-kit` if it contains only agent tooling;
- a contracts-and-agent-kit name if both concerns are intentionally combined.

A governance repository could contain:

- project-wide requirements;
- global ubiquitous language and the bilingual glossary;
- cross-system ADRs and diagrams;
- shared documentation-authoring rules;
- technology-neutral skills and agent workflow contracts;
- schemas and manifests;
- synchronization and validation scripts;
- changelog, versioning policy, CODEOWNERS, licensing, and provenance.

It should generally exclude:

- backend and frontend implementation guidelines;
- repository root `AGENTS.md` profiles;
- developer-local `.codex/` and `.worktreeinclude` files;
- repository-specific skill lockfiles;
- secrets and environment files;
- generated OpenAPI, which remains produced by the backend;
- technology-specific test, implementation, and review rules.

An alternative is to separate domain/project contracts from agent tooling into
different repositories or independently versioned packages. This has cleaner
ownership boundaries but may be excessive for the current project size. One
repository with distinct top-level packages is another option.

Creating a new repository or moving shared docs would need later official
decisions. Current accepted documentation assigns shared contract ownership to
the backend and requires architecture justification for additional repository
boundaries. A later plan must identify which accepted Requirement
Specifications, ADRs, source-of-truth rules, and links would need to be amended
or superseded.

## OpenAPI consumption and provenance

The backend remains the producer of the machine-readable OpenAPI contract. The
frontend should generate transport types from an explicitly selected immutable
backend contract version rather than maintain handwritten DTO copies.

An identified current gap is that the checked-in generated frontend contract
does not clearly identify its immutable source artifact version.

Ideas include an `openapi-contract.lock.json` recording:

- backend release or commit;
- immutable artifact URL or release asset;
- artifact hash;
- OpenAPI version;
- generator and generator version;
- generated output path.

CI could regenerate or verify the TypeScript artifact against that lock. This
is related to, but separate from, versioning agent skills and shared Markdown
context.

## Possible file-by-file disposition

This table preserves suggestions made during the discussion and is not an
approved migration matrix.

| Asset | Possible treatment |
| --- | --- |
| Root `AGENTS.md` | Repository profile; common structure, local routing and rules |
| Shared requirements | One canonical project source; links or pinned read-only mirror for consumers |
| Global ubiquitous language | One canonical project source; pinned frontend mirror; bilingual glossary applies to both repositories |
| Cross-system ADRs and diagrams | Canonical project source with links or marked mirrors |
| Backend software guidelines | Backend-owned; do not copy as frontend rules |
| Frontend software and presentation guidelines | Frontend-owned; do not copy as backend rules |
| Shared documentation-guideline core | Candidate for exact sharing or versioned mirroring |
| Backend OpenAPI documentation guidelines | Backend-owned or adapted consumer guidance where appropriate |
| Technology-neutral GAM skills | Candidate for pinned exact distribution |
| Test, implementation, and review skills | Repository profiles or independent repository-specific skills |
| Generic upstream skills | Independently selected and pinned unless GAM forks them |
| `.codex.example/config.toml` | Repository profile |
| `.codex.example/agents/*.toml` | Share only agents actually used by both repositories |
| `.codex/` | Developer-local |
| `.worktreeinclude.example` | Repository profile |
| `.worktreeinclude` | Developer-local |
| `skills-lock.json` | Repository-owned dependency lock |
| Generated frontend TypeScript API types | Versioned mirror of pinned OpenAPI, never manually edited |
| Repository workflows and scripts | Repository-owned, with optional calls to shared reusable automation |

## Cross-repository change coordination

Separate repositories cannot make cross-layer changes atomic. Ideas for
mitigation include:

- linked backend and frontend pull requests;
- a compatibility or deployment matrix;
- explicit frontend-supported backend/OpenAPI versions;
- coordinated deployment plans for breaking changes;
- pre-production replacement of unreleased contracts when explicitly
  coordinated;
- transition compatibility after production only for the required window;
- shared pull-request templates or checklists;
- an integration environment that tests a selected frontend/backend artifact
  pair;
- a release record identifying the deployed compatible pair.

## Governance and operational considerations

The following topics should be included in later planning:

### Reproducibility and versioning

- never consume mutable `main` or `latest` as the only pinned agent or contract
  source;
- define semantic or explicit versioning for shared workflow bundles;
- version the role/result-contract schema when one exists;
- publish changelogs and migration notes;
- define upgrade, rollback, deprecation, and removal procedures;
- allow independent consumer adoption when compatibility permits.

### Security and supply chain

- treat skills and their scripts as executable supply-chain inputs;
- pin source revisions and hashes;
- preserve upstream licenses and provenance;
- review changes through CODEOWNERS or equivalent ownership;
- avoid automatically executing unreviewed remote scripts;
- separate secrets and local authentication from tracked examples;
- decide whether signed tags or releases are warranted.

### Availability and portability

- support isolated clones without the neighboring repository;
- support Windows, CI, remote development, and Codex worktrees;
- account for line endings and executable file metadata;
- avoid committed absolute paths;
- provide an offline or materialized context path when practical;
- define behavior when remote context cannot be fetched.

### Discovery and context size

- keep skill discovery paths compatible with the agent platform;
- avoid loading all shared requirements into every prompt;
- let routing identify only the relevant shared documents;
- account for `AGENTS.md` instruction-size limits;
- keep generated mirrors discoverable but visibly non-authoritative;
- namespace GAM skills to avoid collisions with external skills.

### Validation and drift prevention

- validate manifests and locks in CI;
- verify generated mirror hashes;
- reject downstream edits to generated material;
- smoke-test shared skill metadata and referenced files;
- detect broken shared documentation links;
- test repository-specific adaptations independently;
- provide a single bootstrap or update command for contributors.

### Ownership and review

- identify owners for shared domain docs, agent workflow protocols, backend
  rules, and frontend rules;
- decide who approves cross-project terminology and translations;
- distinguish changing a shared interface from changing one repository profile;
- document how a frontend discovery proposes a change to a backend- or
  governance-owned source;
- avoid giving a frontend agent silent write authority over a neighboring
  backend repository.

## Questions for a later planning session

1. Which exact documents are project-wide, backend-only, and frontend-only?
2. Should structural parity be mandatory, recommended, or only a discovery
   convention?
3. Should mirrors live under `.gam/context/` or familiar `docs/` paths?
4. Which shared files may be edited only at their canonical source?
5. How should agents identify generated read-only mirrors?
6. Should the backend publish a shared-context release bundle before any third
   repository exists?
7. Which backend commit, tag, or release should a frontend branch pin during
   pre-production?
8. Should shared context upgrades be manual, bot-proposed, scheduled, or
   release-triggered?
9. Should `gam-requirements` in the frontend edit only frontend-owned
   requirements and produce handoffs for shared ones?
10. Should `gam-domain-modeling` be omitted, adapted to read-only use, or made
    authority-aware through a context manifest?
11. What are the exact rules and names for frontend implementation and review
    skills?
12. Is a simple frontend workflow sufficient without custom agents and the
    backend TDD-review cycle?
13. Which common documentation directories must exist immediately, and which
    should be created only when they contain an artifact?
14. Should common directory names be renamed for consistency, including
    `agents-guidelines` versus `agent-guidelines`?
15. When would MCP provide enough value to replace or supplement pinned local
    mirrors?
16. What evidence threshold justifies a dedicated GAM governance repository?
17. If a third repository is created, should business contracts and agent
    tooling share it or be independently versioned?
18. How will existing accepted requirements and ADRs be superseded or amended
    if ownership moves?
19. How will frontend releases identify their supported backend contract and
    shared-context versions?
20. What CI checks are necessary to prevent drift without coupling the two
    repositories' pipelines too tightly?

## Possible next planning artifacts

Without treating this list as authorization to create them, a later planning
session may produce:

- a complete inventory and disposition matrix for both repositories;
- an ADR for shared context and agent-tooling ownership;
- amendments or replacements for affected cross-repository requirements;
- a repository-profile contract for `AGENTS.md`;
- a shared-context manifest and lock schema;
- an agent-kit versioning and release policy;
- frontend-specific implementation and review skill specifications;
- a documentation topology proposal;
- an OpenAPI provenance lock design;
- a phased migration and rollback plan.
