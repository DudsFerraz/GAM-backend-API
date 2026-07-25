# Implement Native Agent O Orchestration for the GAM Workflow

You are working in the GAM repository.

Your task is to implement the approved redesign of the GAM agent workflow. The redesign introduces a human-started orchestration agent, Agent O, which uses native Codex subagents to coordinate Agent T, Agent D, and Agent R.

Read the complete instructions before modifying files.

## Primary objective

Replace the developer’s mechanical responsibility for transferring handoffs between Agent T, Agent D, and Agent R with a native Codex orchestration workflow.

The developer must remain responsible for:

1. Planning with Agent P.
2. Resolving escalated requirement, architecture, scope, permission, or workflow problems.
3. Reviewing the completed code.
4. Deciding whether to commit the changes.

Agent O must replace the developer only for routine orchestration between Agent T, Agent D, and Agent R.

## Required preliminary inspection

Before editing:

1. Read the repository’s root `AGENTS.md`.
2. Read all existing GAM workflow and role skills.
3. Read all existing handoff references.
4. Read `.codex/config.toml` if it exists.
5. Inspect existing `.codex/agents/` definitions if present.
6. Search the repository for every reference to:

   * `$gam-handoff`
   * `gam-handoff`
   * Fresh Agent T handoffs
   * Fresh Agent D handoffs
   * Return to Agent T handoffs
   * Return to Agent D handoffs
   * Fresh Agent R handoffs
   * Review Return handoffs
   * role transitions
   * instructions that tell a role to activate or invoke the next role
7. Identify any repository documentation, tests, examples, skill metadata, or prompts that will become inconsistent after the redesign.

Do not modify only the obvious files. Trace all affected references and preserve one coherent workflow model across the repository.

---

# Confirmed architecture

## 1. Native Codex orchestration only

The workflow must run through the Codex app and native Codex subagents.

The developer starts a fresh root Codex chat and explicitly invokes:

```text
$gam-orchestration
```

That root session becomes Agent O.

The following are out of scope:

* OpenAI Agents SDK
* Codex MCP server
* `codex()`
* `codex-reply()`
* external Python or TypeScript orchestrators
* external workflow databases or services
* custom MCP launchers
* custom Codex clients

Do not introduce placeholders, abstractions, dependencies, or documentation for those external approaches.

### Reason

The selected architecture must remain directly usable from the Codex app. External orchestration may be reconsidered only after real evidence shows that native orchestration is insufficient.

---

## 2. Agent O

Create a new `$gam-orchestration` skill.

Agent O is not a custom subagent. It is always the human-started root Codex session.

Agent O must:

* validate that planning is ready;
* inspect accepted Requirement Specifications and related durable artifacts;
* create the initial Agent T assignment;
* spawn one Agent T custom-agent thread;
* validate Agent T results;
* spawn one Agent D custom-agent thread when legally required;
* resume the existing Agent T and Agent D threads during their loop;
* spawn one fresh Agent R thread after the T/D completion criteria are met;
* validate structured role results;
* apply only legal workflow transitions;
* create agent-facing handoffs;
* maintain explicit workflow state in its own root context;
* maintain the T/D loop iteration count;
* stop on successful review completion;
* stop and escalate whenever safe automatic continuation is impossible.

Agent O must not:

* write tests;
* write production code;
* perform Agent R’s review;
* invent or reinterpret business requirements;
* silently establish architectural decisions;
* repair another role’s work;
* weaken tests or role boundaries;
* invent a transition not defined by `$gam-agent-workflow`;
* use `$gam-human-handoff`;
* automatically stage, commit, or push changes.

### Reason

Agent O is a transition authority and coordinator, not another engineering role. Keeping it narrow prevents orchestration decisions from becoming mixed with testing, implementation, or review work.

---

## 3. Custom Agent T, D, and R definitions

Create these project-scoped custom agents:

```text
.codex/agents/gam-agent-t.toml
.codex/agents/gam-agent-d.toml
.codex/agents/gam-agent-r.toml
```

Each custom-agent file must define at least:

* `name`
* `description`
* `developer_instructions`

Use the exact current Codex custom-agent TOML format supported by the repository’s Codex version.

The custom-agent files must be thin. They should define:

* stable role identity;
* fixed role boundaries;
* required authoritative skills;
* sandbox intent;
* structured result requirements;
* prohibitions against orchestration and adjacent-role responsibilities.

Do not copy the complete role skill into the custom-agent instructions.

### Required skill relationship

Agent T must explicitly load and follow:

* `$gam-agent-workflow`
* `$gam-test-design`

Agent D must explicitly load and follow:

* `$gam-agent-workflow`
* `$gam-implementation`

Agent R must explicitly load and follow:

* `$gam-agent-workflow`
* `$gam-review`

The role skill remains authoritative for detailed role-local behavior.

`$gam-agent-workflow` remains authoritative for cross-role rules, legal outcomes, transitions, blockers, and completion criteria.

### Role restrictions

Agent T must not:

* write production behavior;
* perform Agent R review;
* orchestrate other roles;
* generate or route agent handoffs;
* invoke `$gam-human-handoff`.

Agent D must not:

* invent requirements;
* own test design;
* weaken tests to make them pass;
* perform Agent R review;
* orchestrate other roles;
* generate or route agent handoffs;
* invoke `$gam-human-handoff`.

Agent R must not:

* implement fixes;
* write missing tests as Agent T;
* assume Agent D responsibilities;
* orchestrate other roles;
* select or activate the next role;
* generate agent handoffs;
* invoke `$gam-human-handoff`.

### Reason

Custom agents provide stable runtime identity and boundaries. Skills continue to provide the detailed reusable workflows, avoiding duplicated sources of truth.

---

## 4. Model configuration

The initial model policy for Agent T, Agent D, and Agent R is:

```toml
model = "gpt-5.6-sol"
model_reasoning_effort = "medium"
```

Configure this as a shared project-level subagent default rather than duplicating it in all three custom-agent files.

The intended configuration is equivalent to:

```toml
[agents]
default_subagent_model = "gpt-5.6-sol"
default_subagent_reasoning_effort = "medium"
max_concurrent_threads_per_session = 3
```

Before using these exact keys, verify them against the Codex configuration schema supported by the installed/current project environment.

Do not invent unsupported configuration keys.

If the supported configuration uses different names or structure:

1. Implement the correct equivalent.
2. Preserve the approved behavior.
3. Explain the discrepancy and resolution in the final inconsistency report.

The custom Agent T, D, and R files must initially omit model and reasoning overrides so they inherit the shared project defaults.

`$gam-orchestration` must instruct Agent O not to provide explicit model or reasoning overrides while spawning standard T, D, or R agents.

Role-specific overrides may be added later, but none should be introduced now.

Agent O does not need a custom-agent model configuration because it is the manually initiated root session. It uses the model and reasoning setting selected by the developer for that session.

### Reason

One project-level setting makes the initial policy predictable and easy to change. Preventing Agent O from overriding it avoids unexpected expensive or underpowered subagents.

---

## 5. Sandbox and execution ownership

Configure the role agents with this intended sandbox policy:

| Agent   | Sandbox intent    |
| ------- | ----------------- |
| Agent T | `workspace-write` |
| Agent D | `workspace-write` |
| Agent R | `read-only`       |

Verify the exact supported TOML values before implementing them.

Agent T and Agent D must never perform write-heavy work concurrently.

Only one role agent may actively own repository modifications at a time.

The execution sequence is logically:

```text
T → D → T → D → ... → R
```

The T and D threads may remain available for later resumption, but Agent O must wait for the current role to finish before activating the next one.

Agent R must be fresh and read-only.

Document any limitation caused by parent-session permission inheritance. Do not claim that a custom-agent sandbox is an absolute security boundary if native Codex inheritance does not guarantee that.

### Reason

The T/D workflow is sequential by design. Concurrent writers would create conflicts and undermine clear ownership. Agent R must remain independent and must not modify reviewed work.

---

# Workflow artifact redesign

## 6. Rename `$gam-handoff` to `$gam-human-handoff`

Rename the current skill:

```text
$gam-handoff
```

to:

```text
$gam-human-handoff
```

Rename its directory and update all internal metadata and references accordingly.

The new skill must:

* preserve the human-readable Markdown handoff capability;
* remain suitable for manual copy-and-paste between independent sessions;
* be explicitly invoked only by the developer;
* use `allow_implicit_invocation: false`;
* state clearly that it must never participate in Agent O orchestration;
* state clearly that automated role agents must not invoke it;
* read workflow rules only when needed to validate or render a manual handoff;
* remain outside the natural automated workflow.

Do not retain `$gam-handoff` as an alias unless an existing repository compatibility requirement makes removal unsafe. If such a compatibility issue exists, resolve it deliberately and report it.

### Reason

The current handoff skill is specifically a manual transport adapter. Keeping it isolated prevents natural-language Markdown packets from being mistaken for Agent O’s machine-oriented communication protocol.

---

## 7. Create `$gam-agent-handoff`

Create a new `$gam-agent-handoff` skill.

This skill is used by Agent O to construct a structured assignment for the receiving role.

It must:

* accept or derive a validated legal transition;
* identify the source role;
* identify the target role;
* identify whether the target thread is fresh or resumed;
* reference authoritative Requirement Specifications, ADRs, diagrams, or other durable artifacts;
* reference changed test, production, or documentation files;
* include exact relevant verification commands and observed results;
* distinguish expected red signals from unrelated failures;
* include blockers, scope restrictions, and risks only when relevant;
* avoid duplicating full requirements or role instructions;
* avoid human copy-and-paste formatting conventions;
* avoid selecting a transition independently;
* avoid activating the target role itself.

`$gam-agent-handoff` must assume that Agent O has already validated transition legality through `$gam-agent-workflow`.

### Reason

Machine-to-machine communication and human-readable manual transport have different requirements. Separating them reduces ambiguity and misuse.

---

## 8. Structured role results

Agent T, Agent D, and Agent R must return structured role results to Agent O.

A role result reports what the role established. It must not select, spawn, or activate the next role.

Define one coherent result contract. Keep the contract centralized rather than duplicating slightly different definitions across every skill.

The contract must include, where applicable:

* schema or contract version;
* feature or workflow identity;
* role identity;
* current workflow phase;
* outcome;
* created or changed artifacts;
* authoritative artifacts consulted;
* verification commands;
* observed verification results;
* blockers;
* risks or uncertainty;
* scope deviations;
* whether human intervention is required.

Use a machine-readable fenced format, such as JSON, if that is the most reliable native Codex convention for Agent O to parse.

Do not require role agents to persist runtime state in repository files.

### Agent T outcomes must cover at least

* initial expected red signal confirmed;
* expanded coverage exposes a production issue;
* T/D loop complete;
* requirement ambiguity;
* an existing test outside safe Agent T self-correction conflicts with its
  authoritative artifact;
* no valid existing test seam;
* verification or environment blocker.

### Agent D outcomes must cover at least

* initial implementation satisfies the functional tests;
* production issue fixed;
* a supplied test conflicts with its authoritative artifact;
* architecture or durable design decision required;
* verification or environment blocker.

### Agent R results must classify at least

* no actionable findings;
* missing or misleading coverage;
* defect without adequate failing coverage;
* production defect already protected by adequate coverage;
* unambiguous production or documentation implementation issue;
* requirement, domain-model, scope, or architecture gap;
* verification concern;
* deterministic aggregation of mixed findings.

Agent R reports findings and classifications. Agent R does not route them.

### Reason

The engineering role is best positioned to report facts and evidence. Agent O must remain the sole authority that converts those facts into a legal transition.

---

# Workflow skill changes

## 9. Refactor `$gam-agent-workflow`

Keep `$gam-agent-workflow` as the source of truth for:

* active-role identity;
* sticky role boundaries;
* valid role outcomes;
* legal transitions;
* Agent T/Agent D alternation;
* loop completion criteria;
* Agent R finding classification and routing rules;
* blockers;
* human escalation conditions;
* final workflow completion.

Remove transport-specific assumptions.

It must no longer instruct role agents to:

* invoke a handoff-rendering skill;
* produce a Markdown handoff;
* copy content into another chat;
* become the target role;
* activate the next role.

Replace handoff-oriented workflow language with result and transition language.

For example, the logical behavior should become:

```text
Agent T returns expected_red_confirmed.
Agent O validates the result.
Agent O applies the T-initial → D-initial transition.
Agent O invokes $gam-agent-handoff.
Agent O spawns Agent D.
```

Do not duplicate detailed role-local procedures inside `$gam-agent-workflow`.

### Preserve these semantics

* Each role thread has exactly one sticky role.
* Reading another role’s skill does not authorize executing that role.
* T owns test design.
* D owns production behavior.
* R owns independent review.
* An unprotected defect must not go directly to D.
* Requirement or planning gaps must not be automatically routed to P.
* A role remains in its current identity when it returns its result.
* Agent O alone activates another role.

### Reason

Workflow legality and transport mechanics are separate concerns. `$gam-agent-workflow` should model the state machine independently of how information is carried.

---

## 10. Refactor `$gam-test-design`

Update Agent T’s workflow so it no longer invokes a handoff skill.

After the initial functional red signal:

* return the corresponding structured Agent T result;
* include test files and the meaningful observed red signal;
* stop the current role turn.

After expanded testing:

* return a structured production-gap result when valid tests expose a production issue;
* return a loop-complete result when test-design work and required verification are complete;
* return an escalation result for requirement ambiguity, invalid seams,
  conflicting authoritative artifacts, or blockers;
* stop after returning the result.

Agent T must not name or activate Agent D or Agent R except where a target-role label is unavoidable in a standardized outcome name. Prefer outcome semantics over routing commands.

---

## 11. Refactor `$gam-implementation`

Update Agent D’s workflow so it no longer invokes a handoff skill.

After the initial implementation:

* return a structured result containing changed production files and observed verification;
* stop.

After fixing production issues exposed by expanded tests:

* return a structured fixed result;
* return escalation results for test/requirement conflict, missing architecture decisions, or blockers;
* stop after returning the result.

Every successful Agent D result resumes Agent T. Only Agent T may declare the
T/D loop complete after evaluating coverage and the latest correction.

Agent D must not activate Agent T or Agent R.

---

## 12. Refactor `$gam-review`

Update Agent R so it:

* reports structured findings;
* classifies each finding;
* identifies evidence and affected artifacts;
* reports verification concerns;
* does not select or activate Agent T or Agent D;
* does not invoke either handoff skill;
* does not implement fixes;
* stops after returning its result.

Agent O must apply the review-routing rules.

Preserve these routing semantics in `$gam-agent-workflow`:

* missing coverage, misleading test seams, or unprotected defects → Agent T;
* production defects already exposed by adequate coverage → Agent D;
* unambiguous implementation issues supported by authoritative artifacts and
  evidence → Agent D;
* requirement, domain, scope, or architecture gaps → developer escalation;
* no actionable findings → orchestration completion;
* mixed findings involving an unprotected defect → Agent T first.

---

## 13. Refactor `$gam-planning`

Agent P remains a human-directed planning role.

When planning is ready:

* Agent P must report that the accepted planning artifacts are ready for implementation orchestration;
* Agent P must not automatically invoke `$gam-human-handoff`;
* Agent P must not invoke `$gam-agent-handoff`;
* Agent P must not spawn Agent T;
* Agent P must not become Agent O.

The developer then starts a fresh Codex app chat and invokes `$gam-orchestration`, referencing the relevant accepted planning artifacts.

### Reason

Planning remains collaborative and human-governed. The implementation orchestration begins through a deliberate new root session, preserving a clear boundary between planning and execution.

---

# Agent O behavior

## 14. Thread lifecycle

For each feature workflow, Agent O must:

1. Spawn one fresh Agent T thread.
2. Validate its initial result.
3. Spawn one fresh Agent D thread after the expected red signal is valid.
4. Resume the original Agent T thread after D’s implementation pass.
5. Resume the original Agent D thread when expanded tests expose a production issue.
6. Continue alternating through the same T and D threads.
7. Spawn a fresh Agent R thread only after the T/D completion conditions are satisfied.
8. Never reuse the T or D thread as Agent R.
9. Never create duplicate T or D threads unless the original thread is unusable and the developer approves recovery.

The implementation must use native Codex subagent spawning and follow-up/resumption capabilities supported by the Codex app.

Do not document or implement MCP-based thread identifiers.

---

## 15. Explicit orchestration state

Agent O must maintain a concise explicit state record in its root-session context.

The state must include at least:

* feature or workflow identifier;
* authoritative planning artifacts;
* current phase;
* current owner;
* whether T has been spawned;
* whether D has been spawned;
* whether R has been spawned;
* resumable T and D thread identities as available to native Codex;
* T/D correction-cycle count;
* last validated role result;
* last legal transition;
* unresolved blockers;
* whether human intervention is required.

Do not create a repository JSON state file in this implementation.

### Reason

Explicit state helps Agent O avoid skipped or duplicate transitions without turning transient orchestration data into project documentation.

---

## 16. T/D loop limit

Set the normal maximum to four T/D correction cycles.

Define precisely what increments the correction-cycle counter. The definition must be consistent and should avoid counting the initial T-red/D-implementation exchange as several ambiguous iterations.

When the configured limit is reached without satisfying completion criteria:

* Agent O must stop;
* summarize the unresolved cycle;
* report the latest evidence;
* request developer intervention;
* not silently increase the limit.

Make the limit easy to change in the orchestration skill.

### Reason

A bounded loop protects usage and prevents an apparently legal but unproductive workflow from continuing indefinitely.

---

## 17. Human escalation

Agent O must stop and return control to the developer when:

* requirements are missing, contradictory, or insufficient;
* expected behavior lacks an authoritative source;
* a durable architecture or design decision is required;
* Agent T cannot establish a meaningful red signal;
* Agent T identifies no valid existing test seam;
* Agent D reports a valid test/authority conflict;
* verification fails for unexpected or unrelated reasons that prevent safe continuation;
* required permissions are unavailable;
* work expands beyond the approved scope;
* a role violates its ownership boundary;
* the T/D cycle limit is reached;
* Agent R reports a planning or architecture gap;
* a structured result is malformed or lacks required evidence;
* an outcome cannot be mapped to exactly one legal transition;
* native thread continuation becomes unreliable or ambiguous.

Agent O must not resolve these conditions by guessing.

---

## 18. Completion and developer authority

When Agent R reports no actionable findings:

* Agent O must report that orchestration is complete;
* summarize the implemented scope;
* summarize verification evidence;
* summarize Agent R’s review result;
* identify any non-blocking residual risks;
* return control to the developer.

The developer remains responsible for reviewing the diff and deciding whether to commit.

No orchestration or role skill may automatically:

* run `git add`;
* create a commit;
* push changes;
* merge changes.

Preserve the current `$gam-git-commits` safety boundary.

---

# Inconsistency detection and resolution

You are explicitly required to identify and resolve inconsistencies introduced or exposed by this redesign.

Examples include, but are not limited to:

* old references to `$gam-handoff`;
* workflow diagrams that still show manual handoff generation;
* role skills that still select their own next role;
* review documents that assign routing authority to Agent R;
* contradictory statements about whether a role may invoke another role;
* duplicate transition definitions in several skills;
* custom-agent instructions that conflict with role skills;
* unsupported Codex configuration keys;
* inconsistent custom-agent names;
* skill descriptions that could trigger implicit invocation incorrectly;
* manual handoff templates that incorrectly appear authoritative in Agent O mode;
* references to developer copy-and-paste as part of the standard workflow;
* diagnosis-mode return instructions that assume the old handoff mechanism;
* examples or diagrams that conflict with structured role results;
* role completion rules that have no corresponding structured outcome;
* circular dependencies among workflow, orchestration, and handoff skills;
* references to Agents SDK, MCP, `codex()`, or `codex-reply()` as part of the approved implementation.

For every inconsistency:

1. Identify the conflicting files or instructions.
2. Determine the correct source of truth.
3. Resolve the conflict in the repository when it is within scope.
4. Avoid creating a second competing rule.
5. Document the resolution in the final report.

If an inconsistency cannot be safely resolved without a product or architecture decision:

* do not guess;
* leave the repository internally safe;
* report the unresolved decision clearly;
* explain why it requires developer input.

---

# Quality requirements

The completed workflow must have clear ownership:

| Concern                              | Owner                           |
| ------------------------------------ | ------------------------------- |
| Business requirements                | Requirement Specifications      |
| Architecture decisions               | ADRs                            |
| Cross-role workflow legality         | `$gam-agent-workflow`           |
| Native orchestration execution       | `$gam-orchestration`            |
| Agent-facing assignment envelope     | `$gam-agent-handoff`            |
| Manual human-readable rendering      | `$gam-human-handoff`            |
| Test design                          | `$gam-test-design` / Agent T    |
| Production implementation            | `$gam-implementation` / Agent D |
| Independent review                   | `$gam-review` / Agent R         |
| Final acceptance and commit decision | Developer                       |

Do not allow the same concern to be authoritatively defined in several places.

Keep skills focused and use references for detailed contracts when necessary.

Ensure every renamed or new skill has valid front matter and corresponding `agents/openai.yaml` metadata where the repository convention requires it.

Use `allow_implicit_invocation: false` for skills that must be explicit-only, especially `$gam-human-handoff`.

Determine whether `$gam-orchestration` should also be explicit-only. The approved workflow requires the developer to invoke it deliberately, so configure it accordingly unless a documented Codex limitation prevents this.

---

# Validation

After editing, perform appropriate validation.

At minimum:

1. Search again for obsolete `$gam-handoff` references.
2. Search for role instructions that still automatically invoke a handoff.
3. Search for instructions that allow T, D, or R to activate the next role.
4. Search for references that assign review routing execution to Agent R.
5. Search for accidental `$gam-human-handoff` dependencies in automated workflow files.
6. Validate all TOML files.
7. Validate all YAML files.
8. Validate Markdown front matter.
9. Confirm that custom-agent names used by `$gam-orchestration` exactly match the custom-agent definitions.
10. Confirm that the shared model configuration is valid for the current Codex configuration format.
11. Confirm that T and D are configured for write access and R for read-only intent, subject to documented parent permission inheritance.
12. Confirm that the workflow defines every structured outcome it expects.
13. Confirm that every outcome maps to zero or one legal transition:

    * zero only for completion or escalation;
    * exactly one for normal continuation.
14. Confirm that no external Agents SDK or MCP implementation was introduced.
15. Inspect the final diff for unrelated modifications.

Run any repository-specific checks that apply to skills or configuration files.

Do not claim validation succeeded unless the commands were actually executed and their results observed.

Do not stage, commit, or push.

---

# Required final output

Provide a final report with these sections.

## 1. Implementation summary

Summarize:

* new files;
* renamed files;
* major modified files;
* the resulting Agent O workflow.

## 2. Confirmed behavior

State how the implementation now handles:

* starting Agent O;
* spawning T, D, and R;
* resuming T and D;
* structured role results;
* legal transition validation;
* agent-facing handoffs;
* manual human handoffs;
* loop limits;
* escalation;
* final developer review.

## 3. Inconsistency report

This section is mandatory.

For each inconsistency found, include:

* affected files;
* conflicting rules or assumptions;
* why it was inconsistent;
* the selected source of truth;
* the change made to resolve it.

Also include a subsection titled `Unresolved inconsistencies`.

Write `None` only when every identified inconsistency was safely resolved.

## 4. Configuration report

State:

* the exact model configuration keys used;
* why they are valid for the current Codex version;
* the custom-agent names;
* their sandbox settings;
* whether any approved configuration had to be adapted.

## 5. Validation evidence

List every command executed and its observed result.

Distinguish:

* successful checks;
* warnings;
* unrelated pre-existing failures;
* checks that could not be run.

## 6. Remaining risks

Report any native Codex limitations, especially:

* parent permission inheritance;
* reliance on Agent O following a skill-defined state machine;
* transient state being kept in the root session;
* native thread-resumption assumptions.

## 7. Developer review checklist

Provide a concise checklist for the developer to inspect before accepting the implementation.
