# Skill Guidelines

## Goal

Produce the most reliable agent behavior with the least necessary text.

Every instruction must change behavior, resolve ambiguity, identify authority, or
prevent a plausible error. Omit it otherwise.

## Authority

Assign one owner to each concern.

- Define each rule, term, contract, schema, outcome, or mapping in one place.
- Refer consumers to the owner instead of restating its content.
- Keep references one-way unless each direction changes required behavior.
- Do not summarize a table or contract in prose that can become a second
  definition.
- Keep exceptional workflows out of standard workflows unless a legal
  transition connects them.

When two files define the same concern, choose the owner and delete the other
definition.

## Skill scope

Give each skill one clear job and boundary.

- Keep role-local behavior in the role skill.
- Keep shared contracts, workflow state, and routing in their respective
  cross-role owners.
- Put detailed or conditional material in references and keep `SKILL.md` as the
  routing and procedural entry point.
- Tell the agent which reference to read for a concern and when to read it.
- Do not mention unrelated skills merely to say that they are out of scope.

## Triggering

Make the frontmatter description the owner of when a skill triggers.

- State what the skill does and the requests that should activate it.
- State important exclusions when accidental activation is plausible.
- Do not repeat trigger rules in the body.
- Use `allow_implicit_invocation: false` only to prevent automatic selection.
  It requires explicit `$skill` invocation; it does not establish who may
  invoke the skill.
- Define human-only or role-only authority separately from invocation policy.

## Contracts and vocabulary

Use one representation and one vocabulary for each concept.

- Publish a structured result or assignment shape in one authoritative file.
- Keep every fenced JSON example valid JSON.
- Do not publish alternative shapes as examples in consumer skills.
- Define outcomes separately from the table that maps them to transitions.
- Use the same verification, artifact, phase, and outcome terms everywhere.
- Define non-obvious terms and fields where they are owned.
- Avoid broad fields whose meaning changes by context.
- Give comparable role outcomes a comparable structure unless their semantics
  require a visible difference.

## Writing

- Prefer direct normative sentences.
- Prefer a table when it is the authoritative mapping.
- Prefer a reference over copied explanation.
- Remove rationale after it has been captured by a clear rule.
- Do not explain behavior the model can infer reliably.
- Add detail in proportion to the cost of misunderstanding.

Conciseness must remove redundancy, not required constraints.

## Review

Before accepting a skill change, verify:

1. Every concern has one identifiable owner.
2. No consumer repeats the owner's normative text.
3. Every reference changes what the agent reads or does.
4. Triggering and execution authority are not conflated.
5. Contracts, examples, vocabulary, and transitions agree.
6. Removing any remaining paragraph would lose necessary behavior or clarity.
