# Local measurement: gate on feature requests — ungated screen, then same-day u vs gated full vs gated safety (skreeep2)

> Source: local measurement, this repository's owner — scala-llm-template branch gate-feature-tasks 7b09c68 (`eval/gate-ab/`, tasks fr-pan, fr-tuning, fr-mode-leds, fr-exclusive-solo); spec `docs/superpowers/specs/2026-10-10-gate-feature-tasks-design.md` (this repo); skreeep2 6bf6c1b; headless `claude -p`, model sonnet; run root `~/gate-eval-feature/`
> Collected: 2026-10-10
> Published: 2026-10-10

Measurement record, 2026-10-10. Numbers copied from `validation.txt`, `campaign.log` and the run records (`score.json`) as run.

## Why

Every earlier gate measurement used remove-and-rebuild tasks with scaladoc and signatures left in place, and ungated
sonnet solved the last 16 such runs. The feature-level literature says new behaviour from a short request is the harder
setting, with regressions in existing code dominating failures. The turn-end safety default (scala-llm-template 89b151c)
drops exactly the checks aimed at regressions. This study asked whether the gate, mode full or mode safety, reduces
unsolved runs or regressions on plain-language feature requests that must edit shared existing code.

## Tasks

Base is skreeep2 HEAD; nothing is cut. Each prompt is 2 to 4 plain sentences naming only the entry point the hidden spec
calls. Each task has an authored hidden weaver spec and a reference patch used only for validation.

- fr-pan — a V-Pot (`Control.Encoder`) pans its strip's instrument 0.05 per detent, starting at 0.0, clamped to -1..1;
  emits `Command.SetPan(instrument: String, pan: Double)` only on change; never changes a level. Hidden spec 5 tests.
- fr-tuning — `final class Notes[V](a4: Double)(using ConvertableTo[V], Field[V])`; `Notes[Double](440.0)` is today's
  tuning. Hidden spec 4 tests.
- fr-mode-leds — entering a mode lights its mode key and darkens the others. Hidden spec 4 tests.
- fr-exclusive-solo — `MixerMode.exclusive(strips, perBank)`: a solo clears other solos; `new MixerMode(strips, perBank)`
  keeps today's behaviour. Hidden spec 3 tests.

skreeep2's wartremover setting makes default arguments an error, so the first wording of fr-tuning and
fr-exclusive-solo (a defaulted parameter) failed validation; both requests were reworded before any run.

## Validation

All 4 tasks: `cut_compiles=yes`, `hidden_fails_on_cut=yes`, `hidden_passes_on_head=yes`, `reference_passes_all=yes`
(on HEAD plus the reference patch: hidden spec, every existing test module and the holdout pass).

## Screen (ungated, 2 runs per task)

Pre-registered: keep a task if ungated solved ≤ 1 of 2.

| run | solved | hidden pass ratio | regressions | false_done | wall s | cost USD |
|---|---|---|---|---|---|---|
| fr-pan-u-1 | no | 0.2 | 0 | yes | 51 | 0.16 |
| fr-pan-u-2 | no | 0.2 | 0 | yes | 46 | 0.17 |
| fr-tuning-u-1 | yes | 1.0 | 0 | no | 31 | 0.12 |
| fr-tuning-u-2 | yes | 1.0 | 0 | no | 29 | 0.12 |
| fr-mode-leds-u-1 | yes | 1.0 | 0 | no | 32 | 0.13 |
| fr-mode-leds-u-2 | yes | 1.0 | 0 | no | 29 | 0.10 |
| fr-exclusive-solo-u-1 | no | 0.67 | 0 | yes | 31 | 0.11 |
| fr-exclusive-solo-u-2 | yes | 1.0 | 0 | no | 37 | 0.14 |

5 of 8 solved. Kept: fr-pan, fr-exclusive-solo.

All 3 unsolved runs fail the same way: the agent edited a `.scala` file through Bash with a python `str.replace`
whose search text had 8-space indentation where the file has 6. The replace matched nothing and changed nothing, so
the new behaviour was never added; the code still compiled, existing tests still passed, and the agent reported done.
No regression in any run.

## Test phase (same day, kept tasks × 3 arms × 2)

Arms: `u` ungated; `gf` gated, template engine, turn-end mode full; `gs` gated, mode safety. The gated arms install
the template engine into the run copy and freeze `control/test`.

| run | solved | regressions | gate blocks | wall s | cost USD |
|---|---|---|---|---|---|
| fr-pan-gf-3 | yes | 0 | semantic-guard 1, tool-policy 1, turn-end 1 | 242 | 0.35 |
| fr-pan-gf-4 | yes | 0 | semantic-guard 3, tool-policy 1, turn-end 1 | 286 | 0.47 |
| fr-pan-gs-3 | yes | 0 | semantic-guard 3 | 149 | 0.53 |
| fr-pan-gs-4 | yes | 0 | semantic-guard 6 | 137 | 0.48 |
| fr-pan-u-3 | yes | 0 | — | 55 | 0.17 |
| fr-pan-u-4 | yes | 0 | — | 56 | 0.28 |
| fr-exclusive-solo-gf-3 | yes | 0 | semantic-guard 2, turn-end 2 | 174 | 0.34 |
| fr-exclusive-solo-gf-4 | yes | 0 | semantic-guard 2, turn-end 2 | 103 | 0.30 |
| fr-exclusive-solo-gs-3 | yes | 0 | semantic-guard 1, turn-end 2 | 72 | 0.19 |
| fr-exclusive-solo-gs-4 | yes | 0 | semantic-guard 3 | 97 | 0.23 |
| fr-exclusive-solo-u-3 | yes | 0 | — | 41 | 0.15 |
| fr-exclusive-solo-u-4 | yes | 0 | — | 36 | 0.14 |

Per arm: gf 4 of 4, gs 4 of 4, u 4 of 4 solved; regressions 0 everywhere; `false_done` 0. Median wall: gf 208 s,
gs 117 s, u 48 s. Median cost: gf USD 0.348, gs USD 0.355, u USD 0.161. Total cost: gf USD 1.46, gs USD 1.42,
u USD 0.74.

Decision (spec §7, n = 4 per arm): gf − u = 0, gs − u = 0: **no gain for either mode**; gf − gs = 0, so the safety
default stands.

## Audit

- Contamination flags (grep and sed patterns such as `/if`, `/perBank:`, `/val`, and the run's own `MixerSurface.scala`
  path) cleared as tripwire false positives.
- Test-tampering flags on `MixerModeSpec.scala` in all 4 u test runs: added tests only, 0 removed lines. The gated arms
  could not add tests (`control/test` frozen).
- Scorer defect found and fixed (7b09c68): the environment-leak check accepted hook events only in arms g and t, so the
  engine's own SessionStart hooks in gf and gs were flagged; the 8 gated runs were rescored, errors cleared, no other
  field changed.
- Turn-end blocks in gf on fr-pan were `control.midi.checkFormat` (formatting of a touched file).
- Gate false positive: in 3 of 8 gated runs (fr-exclusive-solo gf-3, gf-4, gs-3) the turn-end diff-scoped semantic
  check reported a line using `!` or `!=` as "did not resolve (no near-miss in the index)", costing an extra turn.

## Edit method (observed, not part of the decision rule)

Runs whose agent edited `.scala` files with the Edit tool vs with python through Bash, test phase plus screen on the
kept tasks: gated runs used the Edit tool in 7 of 8 and python on `.scala` in 2 of 8; ungated runs on the kept tasks
used python on `.scala` in 8 of 8. The 3 screen failures were all python edits whose search text matched nothing.

## Limits

- n = 4 per arm; the kept tasks were chosen because they failed in the screen, and they did not fail again in the test
  phase (regression to the mean); u's 3 screen failures are outside the decision rule.
- The failure class seen (a silent no-op text replace) is an edit-tool failure, not a Scala-type failure; no run broke
  existing code.
- Sonnet only; small features (one shared ADT case, one constructor parameter).
