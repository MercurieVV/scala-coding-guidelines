# Local measurement: gate-visible tasks — ungated screen (skreeep2)

> Source: local measurement, this repository's owner — scala-llm-template master b5aeea9 (`eval/gate-ab/`, tasks adsr, sine, by-offset, sines-gen); spec `docs/superpowers/specs/2026-10-09-gate-visible-tasks-design.md` (this repo); skreeep2 6bf6c1b; headless `claude -p`, model sonnet; run root `~/gate-eval-hard/`
> Collected: 2026-10-09
> Published: 2026-10-09

Measurement record, 2026-10-09. Numbers copied from `validation.txt`, `gate-visible.log` and the run records (`score.json`) as run.

## Why

The same-day control (`2026-10-09-local-gate-same-day-ungated-control.md`) solved the A/B's discriminating tasks 8 of 8
ungated. The gate's checks (turn-end downstream tests, the holdout) can only help where a wrong rebuild is visible to
them and not to the agent's own module tests. This study looked for such tasks first, with an ungated screen, before
spending gated runs.

## Tasks

Code at 6bf6c1b with no unit test in its own module, exercised by the holdout or downstream test modules:

- adsr — `ADSR.amplitudeAt` (envelope).
- sine — `Sine.sineCalc`, `Sine.advance` (partial oscillator sample and phase advance).
- by-offset — `ForTime.byOffset` (per-chunk sample clock).
- sines-gen — `SinesGenerator.createSines`, `fromRatios`, `detuned` (partial sets).

Hidden specs were written for the study (skreeep2 has none for these): 6, 5, 3 and 5 tests, each tied to a scaladoc
sentence (by-offset: `ForTime` has no scaladoc; its spec checks only `.time`, the field every caller reads). Each
passes on HEAD and fails on its cut.

## Validation

All 4 tasks: `cut_compiles=yes`, `hidden_fails_on_cut=yes`, `hidden_passes_on_head=yes`, `gate_visible_on_cut=yes`.
On the cut tree (every other test module run in one Mill invocation, then the holdout):

- adsr: app.test 2 tests failed, holdout 6 tests failed.
- sine: app.test 5, backend.cpu.test 6, holdout 6 tests failed.
- by-offset: app.test 9, backend.cpu.test 16, handoff.jdkAudio.test 1, handoff.openclHost.test 1, holdout 7 tests failed.
- sines-gen: app.test 5, holdout 6 tests failed.

## Screen

Pre-registered (spec §5, amended before any screen run): keep a task if ungated solved ≤ 1 of 2 and an unsolved screen
run shows a holdout failure or a downstream regression.

| run | solved | holdout | regressions | wall s | cost USD |
|---|---|---|---|---|---|
| adsr-u-1 | yes | pass | none | 121 | 0.13 |
| adsr-u-2 | yes | pass | none | 41 | 0.12 |
| sine-u-1 | yes | pass | none | 115 | 0.17 |
| sine-u-2 | yes | pass | none | 39 | 0.15 |
| by-offset-u-1 | yes | pass | none | 38 | 0.16 |
| by-offset-u-2 | yes | pass | none | 35 | 0.14 |
| sines-gen-u-1 | yes | pass | none | 26 | 0.11 |
| sines-gen-u-2 | yes | pass | none | 34 | 0.13 |

8 of 8 solved, hidden-spec pass ratio 1.0 in every run, `false_done` 0, median wall 38.5 s, median cost USD 0.14,
total USD 1.11. One contamination flag (adsr-u-2: a `grep -v "/target/"` pattern and a `sed` delete pattern on the
run's own file) cleared as a tripwire false positive.

No task kept. Verdict (spec §6): **no gate-visible failure found; ungated sonnet handles these.** The gated test phase
was not run.

## Limits

- 2 runs per task; a task failing ungated 1 time in 4 or rarer would pass the screen unseen.
- Remove-and-rebuild with scaladoc and signatures left in place; the scaladoc nearly states each formula. New work
  without such text is a different, harder setting.
- Sonnet only.
