# Gate on gate-visible tasks: screen, then same-day gated vs ungated (design)

Date: 2026-10-09. Status: approved in conversation; written for review. Budget: at most 20 agent runs.

## 1. Question

The A/B and the ablation read a correctness gain for the gate. The same-day ungated control
(`raw/llm-scala-fusion/2026-10-09-local-gate-same-day-ungated-control.md`) solved those 4 tasks 8 of 8, so on current
evidence the gap was day drift. The only task ungated fails reliably is noise, and it fails on hidden properties
that no task text states; no gate can fix that. The gate's checks (turn-end downstream tests, the holdout,
verify-diff) can only help where the agent's mistake is visible to them and not to the agent's own module tests. None
of the 8 A/B tasks was like that: the holdout decided no run. This study asks: on tasks where the gate can see the
mistake, does the gated arm solve more than the ungated arm, run on the same day?

## 2. Candidate tasks

Code at skreeep2 6bf6c1b that the holdout or downstream specs exercise and that has no unit test in its own module:

| task | cut members | gate-visible through |
|---|---|---|
| adsr | `signals/.../envelopes/ADSR.scala` `amplitudeAt` | holdout note timing (silence before and after the note); no spec references ADSR |
| sine | `signals/.../Sine.scala` `Sine.sineCalc`, `Sine.advance` | holdout pitch and sample-rate scenarios; downstream SymbolicVsBreezeSpec, TopologyBackendsSpec |
| by-offset | `core/.../Globals.scala` `ForTime.byOffset` | 11 downstream specs; holdout timing and sample-rate scenarios |
| sines-gen | `signals/.../Sine.scala` `SinesGenerator.createSines`, `fromRatios`, `detuned` | SymbolicVsBreezeSpec, UnderrunSpec; holdout pitch |

The exact cut (bodies to `???`, signatures and scaladoc kept) is fixed in the plan after reading each file. Prompt as
in the A/B.

## 3. Hidden specs

None of these has a spec file in skreeep2. For each task the plan writes a weaver spec that checks only behaviour the
scaladoc states (for adsr: the four segments' values at boundaries and midpoints, zero before start and after release,
a note shorter than attack + decay never reaching sustain; for sine: the sample formula and the wrapped advance; and so
on). The spec lives in the task folder (`tasks/<task>/hidden/<path>`), never in skreeep2.

Harness change: `task.env` gets an optional `HIDDEN_SPEC_SRC` (path inside the task folder). When set, `prepare.sh`
copies it to `base/<task>/hidden/$HIDDEN_SPEC` instead of moving the file out of the exported tree, and the HEAD copy
used for validation gets the spec at `$HIDDEN_SPEC`. Scoring is unchanged.

## 4. Validation (once per task, before any run)

As in the A/B: the cut tree compiles; the hidden spec fails on the cut tree; the hidden spec and the holdout pass on
HEAD. Added: on the cut tree, at least one downstream test (outside `TASK_MODULE`) or holdout scenario fails — the gate
*can* see a wrong rebuild. A task failing any check is replaced or dropped, not adjusted.

## 5. Phases

- Run root `~/gate-eval-hard/` with its own HEAD copy (from `~/gate-eval/head`) and settings snapshot copy; the A/B,
  ablation and control results stay untouched. Old run directories are made unreadable during campaigns, as in the
  control.
- Screen: each validated task × 2 ungated runs (at most 8). A task is kept if ungated solved ≤ 1 of 2.
- Test, same day as the screen where possible: each kept task (at most 3) × 2 gated + 2 fresh ungated runs (at most
  12). Screen runs are not counted in the comparison: they were selected for failing.

## 6. Decision rule (fixed before the test phase)

Over the kept tasks, n = 2 × kept runs per arm, audited:

- gated solved − fresh ungated solved ≥ n/2: "the gate pays on gate-visible tasks".
- difference ≤ 0: "no gate gain even where the gate can see the mistake".
- otherwise: "unclear".
- No task kept: "no gate-visible failure found; ungated sonnet handles these" (the test phase is not run).

Reported beside it: which check blocked in gated runs (turn-end downstream tests, holdout, verify-diff, format),
`false_done`, median wall and cost per arm.

## 7. Write-up

A raw measurement file and a roadmap item-1 update in this repository's wiki, as for the ablation and the control.

## 8. Risks

- Specs written for this study may demand more than the scaladoc says; they check documented behaviour only, and the
  plan lists each test against the scaladoc sentence it comes from.
- n is small (at most 6 per arm); the result is a pointer.
- Zero kept tasks is a likely, still informative outcome.
- Gate parts run on other days than the ablation; model drift between this study and earlier ones is not controlled,
  but within the test phase both arms run on the same day.
