# Gate on feature requests: screen, then same-day ungated vs gated full vs gated safety (design)

Date: 2026-10-10. Status: approved in conversation; written for review. Budget: at most 26 agent runs.

## 1. Question

Every gate measurement so far used remove-and-rebuild tasks with scaladoc and signatures left in place; ungated sonnet
solved the last 16 such runs (same-day control 8 of 8, gate-visible screen 8 of 8). The feature-level literature
(roadmap Tier 5, taxonomy class 8) says the hard setting is different: new behaviour from a short request, where
regressions in existing code dominate failures. The turn-end safety default (scala-llm-template 89b151c) drops exactly
the checks aimed at regressions (downstream turnEndTests). This study asks: on features requested in plain language
that require edits to shared existing code, does the gate — full or safety — reduce unsolved runs or regressions
against ungated, run on the same day?

## 2. Task family "feature-from-request" (FR)

- New behaviour skreeep2 6bf6c1b does not have. The base is HEAD; nothing is cut.
- Each feature must change existing code that other modules use (a new case in a shared ADT, a new parameter on a
  shared function, a changed shared behaviour), so a careless edit can break other modules.
- Prompt: 2–4 sentences of plain language naming only the entry-point signature the hidden spec calls. No scaladoc,
  no stubs.
- Per task, in the task folder, never in the run tree:
  - `hidden/<path>` — an authored weaver spec checking only the requested behaviour; the plan lists each test against
    the request sentence it comes from.
  - `reference.patch` — a reference solution, used only for validation.
- The plan picks 4 features after reading the code; candidates that fail validation are replaced, not adjusted.

## 3. Validation (once per task, before any run)

On HEAD: compiles; the hidden spec fails (or does not compile against the missing entry point — counted as fail).
On HEAD + `reference.patch`: the hidden spec passes, every existing test module passes, the holdout passes.

## 4. Scoring

Per run, on the agent's final tree:

- **solved** — compiles, hidden spec passes, every existing test module passes, holdout passes.
- **regressions** — count of existing tests plus holdout scenarios that fail; reported separately.
- **unsolved** — hidden spec fails.

`score.json` gains `regressions` (int) and `regressed_modules` (list). `false_done` as before.

## 5. Arms

- `u` — ungated (permissions only, no hooks, no sandbox), as in earlier studies.
- `gf` — gated, current template engine, `.dsh/policy/turn-end.tsv` mode `full`.
- `gs` — gated, current template engine, mode `safety`.

Gated run trees: the harness runs `kit/harness/install.sh` from the current scala-llm-template master into the exported
run copy, writes the arm's mode and commits it in the run copy before the agent starts (the file is frozen). skreeep2
and skreeep2-holdout stay read-only.

## 6. Phases

- Run root `~/gate-eval-feature/`, its own HEAD copy and settings snapshot; earlier run directories made unreadable
  during campaigns, as before.
- Screen: 4 FR tasks × `u` × 2 = 8 runs. A task is kept if ungated solved ≤ 1 of 2 (unsolved or any regression).
- Test, same day: kept tasks (at most 3) × (`gf`, `gs`, fresh `u`) × 2 = at most 18 runs. Screen runs not counted.

## 7. Decision rule (fixed before the test phase)

Per gated arm over kept tasks, n = 2 × kept runs per arm, audited:

- arm solved − fresh `u` solved ≥ n/2: "the gate (that mode) pays on feature requests".
- difference ≤ 0: "no gain for that mode".
- otherwise: "unclear".
- `gf` minus `gs` reported the same way; if `gf` pays and `gs` does not, the safety default is revisited.
- No task kept: "ungated sonnet handles feature requests at this size"; test phase not run.

Reported beside it: regressions per arm, which turn-end check blocked in `gf`, `false_done`, median wall and cost per
arm.

## 8. Harness changes (`eval/gate-ab`)

- `task.env` `KIND=FR`: no cut; base is HEAD; `reference.patch` applied only in validation.
- FR prompt template.
- Arms `gf`, `gs` with the engine install and mode commit step.
- Regression count in `score.json`.

## 9. Write-up

A raw measurement file and a roadmap item-11 update in this repository's wiki.

## 10. Risks

- Hidden spec demands more than the request says — each test tied to a request sentence.
- n small (at most 6 per arm); the result is a pointer.
- All 4 features too easy — the screen ends it cheaply (8 runs).
- The new engine on skreeep2 may raise health or frozen notes unrelated to the task; validation runs one gated
  no-op turn per mode first, and any block there is fixed in the harness before runs.
