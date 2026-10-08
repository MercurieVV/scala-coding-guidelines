# Local measurement: gated vs ungated agent on skreeep2 (gate A/B evaluation)

> Source: local measurement, this repository's owner — scala-llm-template branch `gate-evaluation` (`eval/gate-ab/`, head f1d5f62); skreeep2 at 6bf6c1b; holdout `../skreeep2-holdout` at 8207d2f; spec `docs/superpowers/specs/2026-10-03-gate-evaluation-design.md` (this repo); headless `claude -p`, model sonnet
> Collected: 2026-10-08
> Published: 2026-10-08

Measurement record, 2026-10-08. Numbers copied from `report.py` output and `results.csv` as run.

## Setup

- 8 remove-and-rebuild tasks cut from skreeep2 6bf6c1b (noise, mixer-modes, mcu, note-ring, backpressure, scope-view,
  modes, midi-synth): the feature's members become `???`, its spec file is deleted, the tree is a fresh one-commit
  repository. Both arms get the same prompt, tools, `scala-semantic` MCP only, and a 45-minute cap.
- Gated arm: skreeep2's real hooks (semantic-guard, tool-policy, turn-end with the holdout) and Claude Code sandbox.
  Ungated arm: permissions only.
- Scoring after the run, never visible to the agent: the hidden spec (testOnly), the 7 rendered-audio holdout
  scenarios, and other modules' tests against a HEAD baseline. `solved` = every hidden test and every holdout scenario
  passes.
- Planned: 3 repeats (48 runs). Rep 1 ran 2026-10-03. A weekly usage limit cut reps 2 and 3 short (API 429 results
  scored as unsolved); those runs were discarded and the runner now treats an agent API error as an infrastructure
  error. Rep 2 re-ran 2026-10-08 with 0 errors and no 429. Rep 3 was not run: 16 runs per arm, 2 per task.

## Results (strict: report.py, contamination-flagged runs excluded)

```
| n (headline) | 15 | 11 |
| solve rate [Wilson 95%] | 0.87 [0.62, 0.96] | 0.73 [0.43, 0.90] |
| false_done (rate) | 2 (0.13) | 3 (0.27) |
| median wall s | 197 | 44 |
| median cost USD (n) | 0.31 (15) | 0.15 (11) |
| total cost USD | 4.58 | 1.73 |
Sign test over per-task solved-count differences (ties dropped): p = 0.0625 (n tasks = 8)
Verdict: inconclusive
```

## Contamination audit

The tripwire flagged 6 runs (1 gated, 5 ungated). Every flagged command stays inside its own run directory: Python
here-doc bodies (`/import`), a grep regex `^./out`, a filter `grep -v "/View.scala"`, and `ls ../../../../../../../test*`
from a source directory, which resolves to the run's own `repo/`. All 6 are false positives of the tripwire's path
parser.

## Results (audited: all 32 runs)

- Gated: 14 of 16 solved, 0.875, Wilson 95% [0.64, 0.97]. Ungated: 10 of 16, 0.625, [0.39, 0.82]. Difference 0.25.
- `false_done`: gated 2 of 16, ungated 6 of 16.
- Per task, gated vs ungated solved: backpressure 2–1, mcu 2–1, mixer-modes 2–1, scope-view 2–1, midi-synth 2–2,
  modes 2–2, note-ring 2–2, noise 0–0. Gated never lost a task. Sign test: 4 positive, 0 negative, p = 0.125.
- Median wall time: gated 181.5 s, ungated 46.5 s (3.9×). Median turns 17.5 vs 8.5. Median cost USD 0.308 vs 0.156.
- Pre-registered rule applied to the audited numbers: the correctness branch holds (difference 0.25 ≥ 0.20; and
  `false_done` 2/16 ≤ (6/16)/2), the wall-time bound fails (3.9× > 2×), and the difference is not under 0.25, so the
  rule's verdict is "does not pay off".

## What failed

Every unsolved run passed all 7 holdout scenarios; none timed out, tampered with tests or broke another module.
Outside noise, each unsolved run failed exactly one hidden-spec test (failing names, verbatim):

- backpressure-u-2: "pipe: sleep branch causes IO.sleep with correct duration"
- scope-view-u-2: "degenerate ranges are repaired rather than dividing by zero"
- mixer-modes-u-2: "mute silences its own instrument and lights its lamp; a second press restores the fader's level"
- mcu-u-1: "notes are the buttons a mode knows, and a velocity of zero is a release"
- noise, all 4 runs in both arms unsolved, failing generator-quality tests: "the seed is deterministic and runs a
  full period" (noise-g-2, noise-u-1, noise-u-2), "consecutive samples are uncorrelated" and "the spectrum is flat,
  which one round of multiply-mod would not be" (noise-g-1, noise-u-2), "samples fill [-1, 1) with the mean and spread
  of a uniform distribution" (noise-g-2)

Final diff sizes (changed lines) are similar across arms for every task (for example mcu 62–68, scope-view 70–83).

## Gate activity (gated arm, report.py headline runs)

```
| semantic-guard | 29 | 14 |
| tool-policy | 2 | 2 |
| turn-end | 19 | 13 |
```

(hook, blocks, runs fired in; 15 headline runs).

## Per-run table

| run | solved | hidden pass | holdout | false_done | flagged | wall s | turns | USD | gate blocks |
|---|---|---|---|---|---|---|---|---|---|
| backpressure-g-1 | yes | 1.00 | pass | no | no | 287 | 26 | 0.39 | semantic-guard=3;turn-end=2 |
| backpressure-u-1 | yes | 1.00 | pass | no | no | 44 | 14 | 0.18 | - |
| backpressure-g-2 | yes | 1.00 | pass | no | no | 200 | 30 | 0.52 | semantic-guard=3;turn-end=2 |
| backpressure-u-2 | no | 0.89 | pass | yes | no | 56 | 11 | 0.15 | - |
| mcu-g-1 | yes | 1.00 | pass | no | no | 199 | 21 | 0.38 | semantic-guard=4;turn-end=1 |
| mcu-u-1 | no | 0.86 | pass | yes | yes | 56 | 10 | 0.18 | - |
| mcu-g-2 | yes | 1.00 | pass | no | no | 288 | 17 | 0.31 | semantic-guard=2;turn-end=2 |
| mcu-u-2 | yes | 1.00 | pass | no | no | 57 | 11 | 0.18 | - |
| midi-synth-g-1 | yes | 1.00 | pass | no | no | 166 | 16 | 0.29 | semantic-guard=2;tool-policy=1;turn-end=1 |
| midi-synth-u-1 | yes | 1.00 | pass | no | no | 25 | 8 | 0.10 | - |
| midi-synth-g-2 | yes | 1.00 | pass | no | no | 150 | 10 | 0.22 | semantic-guard=1;turn-end=1 |
| midi-synth-u-2 | yes | 1.00 | pass | no | no | 37 | 9 | 0.11 | - |
| mixer-modes-g-1 | yes | 1.00 | pass | no | no | 106 | 22 | 0.32 | semantic-guard=1;turn-end=2 |
| mixer-modes-u-1 | yes | 1.00 | pass | no | no | 49 | 7 | 0.18 | - |
| mixer-modes-g-2 | yes | 1.00 | pass | no | no | 213 | 23 | 0.33 | semantic-guard=2;tool-policy=1;turn-end=2 |
| mixer-modes-u-2 | no | 0.91 | pass | yes | yes | 57 | 8 | 0.18 | - |
| modes-g-1 | yes | 1.00 | pass | no | no | 197 | 16 | 0.22 | semantic-guard=1;turn-end=1 |
| modes-u-1 | yes | 1.00 | pass | no | yes | 84 | 6 | 0.12 | - |
| modes-g-2 | yes | 1.00 | pass | no | no | 154 | 14 | 0.19 | semantic-guard=3;turn-end=1 |
| modes-u-2 | yes | 1.00 | pass | no | no | 42 | 8 | 0.13 | - |
| noise-g-1 | no | 0.40 | pass | yes | no | 235 | 24 | 0.45 | semantic-guard=3 |
| noise-u-1 | no | 0.80 | pass | yes | no | 59 | 13 | 0.24 | - |
| noise-g-2 | no | 0.60 | pass | yes | no | 264 | 22 | 0.31 | semantic-guard=2;turn-end=1 |
| noise-u-2 | no | 0.40 | pass | yes | no | 77 | 13 | 0.22 | - |
| note-ring-g-1 | yes | 1.00 | pass | no | no | 110 | 9 | 0.19 | semantic-guard=1 |
| note-ring-u-1 | yes | 1.00 | pass | no | yes | 40 | 12 | 0.16 | - |
| note-ring-g-2 | yes | 1.00 | pass | no | no | 135 | 10 | 0.17 | turn-end=1 |
| note-ring-u-2 | yes | 1.00 | pass | no | no | 40 | 6 | 0.10 | - |
| scope-view-g-1 | yes | 1.00 | pass | no | no | 99 | 18 | 0.30 | semantic-guard=1;turn-end=2 |
| scope-view-u-1 | yes | 1.00 | pass | no | no | 31 | 8 | 0.13 | - |
| scope-view-g-2 | yes | 1.00 | pass | no | yes | 130 | 17 | 0.31 | semantic-guard=1;turn-end=2 |
| scope-view-u-2 | no | 0.93 | pass | yes | yes | 36 | 8 | 0.13 | - |

## Limits

- n = 16 per arm, 2 repeats per task; Wilson intervals overlap.
- The holdout never decided a run: every scored run passed it. The gated arm's gain came from somewhere else in the
  gate; this study cannot say which part (no per-hook ablation).
- Remove-and-rebuild with scaladoc left is easier than new work. Sonnet only.
- Wall time includes the gate's own Mill runs (turn-end compiles and tests); the absolute cost is minutes per task.
