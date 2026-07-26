---
name: diagnosing-bugs
description: Diagnose rare hard bugs, flaky failures, and performance regressions without implementing fixes. Use only when the developer explicitly invokes $diagnosing-bugs or requests deep diagnosis mode; do not use for ordinary bug reports or implementation work.
---

# Diagnosing Bugs

## Purpose and boundary

This is an exceptional diagnosis-only workflow for difficult defects.

Ordinary bug reports and failing tests remain in the standard GAM workflow governed by `$gam-agent-workflow`.

Diagnosis mode grants no Agent P, T, D, or R authority, and its summary is not
project source of truth.

When exploring the codebase, read `CONTEXT.md` if it exists and inspect relevant Requirement Specifications, ADRs, diagrams, and software guidelines.

Skip phases only when the reason is explicit and evidence-based.

## Phase 1 — Build a feedback loop

This is the core of the skill.

A tight pass/fail signal for the reported symptom makes bisection, hypothesis testing, and instrumentation effective. Without one, reading code tends to produce ungrounded theories.

Spend disproportionate effort here. Be aggressive, creative, and persistent.

### Ways to construct the loop

Try these in roughly this order:

1. A failing test at the seam that reaches the symptom: unit, integration, API, or end-to-end.
2. A curl or HTTP script against a running development server.
3. A CLI invocation with a fixture input and a diff against a known-good output.
4. A headless browser script that drives the UI and asserts on DOM, console, or network behavior.
5. Replay of a captured request, payload, trace, or event log.
6. A throwaway harness that starts the smallest useful subset of the system.
7. A property or fuzz loop for intermittent incorrect output.
8. A bisection harness suitable for `git bisect run`.
9. A differential loop comparing old versus new versions or configurations.
10. The human-in-the-loop script under `scripts/hitl-loop.template.sh` as a last resort.

A test created here is a diagnostic loop, not automatically the project's durable regression test. Agent T later decides whether it uses the correct seam and should be adopted.

### Tighten the loop

Treat the loop as a product:

- Make it faster by caching setup, narrowing scope, or skipping unrelated initialization.
- Make the signal sharper by asserting the exact symptom.
- Make it deterministic by pinning time, seeding randomness, isolating the filesystem, or freezing external dependencies.
- Make it agent-runnable without manual intervention whenever possible.

A slow or flaky loop is only a partial improvement. Continue tightening it.

### Non-deterministic defects

The immediate goal is a high enough reproduction rate to distinguish hypotheses.

Loop the trigger, run it in parallel, add stress, narrow timing windows, or inject controlled delays. Record the observed reproduction rate and keep improving it until the loop is useful.

### When no loop can be built

Stop and say so explicitly.

List what was attempted and request one or more of:

- access to the environment that reproduces the issue;
- a captured HAR file, log dump, trace, core dump, or screen recording with timestamps;
- permission to add temporary production instrumentation.

Do not proceed to causal claims without a usable feedback loop.

### Completion criterion

Phase 1 is complete when one named command has already been run and is:

- **Red-capable**: it drives the actual defect path and can detect the exact reported symptom.
- **Deterministic enough**: it gives a stable verdict or a documented high reproduction rate.
- **Fast enough**: it supports repeated hypothesis testing.
- **Agent-runnable**: it runs unattended, except for the structured HITL fallback.

Record the command and observed output.

No red-capable command means no Phase 2.

## Phase 2 — Reproduce and minimize

Run the loop and confirm that it produces the reported failure mode rather than a nearby but different failure.

Confirm:

- the exact symptom;
- repeated reproduction or a sufficiently high reproduction rate;
- the relevant error, wrong output, timing, trace, or state difference.

Then minimize the scenario.

Remove inputs, callers, configuration, data, dependencies, and steps one at a time. Re-run the loop after every reduction.

Stop when every remaining element is load-bearing: removing any one of them makes the signal disappear or materially changes the failure.

The minimized loop should remain connected to the original symptom.

## Phase 3 — Generate hypotheses

Create three to five ranked hypotheses before testing them.

Each hypothesis must be falsifiable and include a prediction.

Use this form:

> If `<cause>` is responsible, then `<controlled change or observation>` will make the symptom disappear, become stronger, or produce a specific differentiating result.

Discard or sharpen hypotheses that do not make a testable prediction.

Show the ranked list to the developer before testing when interaction is available. Domain knowledge may immediately re-rank or eliminate hypotheses.

Do not block indefinitely waiting for feedback. Continue with the stated ranking when necessary.

## Phase 4 — Instrument and discriminate

Each probe must map to a prediction from Phase 3.

Change one variable at a time.

Prefer:

1. Debugger or REPL inspection.
2. Targeted logs at boundaries that distinguish hypotheses.
3. Focused traces, counters, or measurements.

Do not “log everything and grep.”

Tag every temporary debug log with a unique prefix such as `[DEBUG-a4f2]` so cleanup is reliable.

### Performance regressions

For performance problems:

- establish a baseline first;
- use a timing harness, profiler, query plan, allocation measurement, or equivalent evidence;
- compare controlled states;
- bisect when a bounded change range exists;
- measure before drawing conclusions.

## Phase 5 — Establish the diagnosis

Use the loop and discriminating probes to identify the explanation best supported by evidence.

A diagnosis is complete only when it includes:

- the original reported symptom;
- the named reproduction command;
- the observed red signal or reproduction rate;
- the minimized scenario;
- the causal chain connecting the responsible behavior to the symptom;
- the hypothesis that survived testing;
- the important hypotheses that were falsified and the evidence that rejected them;
- the affected files, boundaries, configuration, data, or environment;
- the expected next step in the standard GAM workflow.

Do not claim certainty beyond the evidence.

When multiple causes remain plausible, state the remaining uncertainty and the next discriminating probe.

### No fix in diagnosis mode

Do not implement the production fix.

Do not convert the diagnostic harness into durable regression coverage inside this workflow.

If a candidate fix is useful to validate causality, prefer a reversible experimental change or controlled configuration toggle. Revert it after the probe and record the result.

The production repository must not be left in a partially fixed state.

## Phase 6 — Cleanup and return to the standard workflow

Before declaring the diagnosis complete:

- remove all temporary `[DEBUG-...]` instrumentation;
- grep for the unique prefix and record that cleanup was verified;
- revert experimental production changes;
- delete disposable prototypes that provide no future diagnostic value;
- preserve only useful reproduction artifacts, clearly marked and referenced;
- record commands and observed outputs;
- state explicitly that the defect has been diagnosed but not fixed.

The diagnosis output is an ephemeral evidence packet, not a Requirement Specification, ADR, regression test, implementation, or review result.

Return control to the developer without activating a role or presenting the
diagnosis packet as a structured T/D/R result.

The developer decides whether to start or resume `$gam-orchestration`, whose
workflow determines the next legal role. Report a planning gap when the
expected behavior lacks an authoritative artifact.

Do not invoke or reference a nonexistent architecture-improvement skill.
