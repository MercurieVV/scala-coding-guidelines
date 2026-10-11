# Chained feature work: does the agent decay on its own code, and does the gate help? (design)

Date: 2026-10-11. Status: approved in conversation; written for review. Budget: 16 agent runs. Roadmap item 14.

## 1. Question

Item 11 found sonnet solving small feature requests ungated; the only failure seen was a silent no-op edit followed by a
false "done". Long-horizon studies (SlopCodeBench; false-success studies; raw files of 2026-10-11) say failure grows
when the agent extends its own earlier code: regressions, false success, structural erosion. This study asks, on
skreeep2: when four feature requests land one after another on the same tree, do regressions, false "done" or erosion
appear, and does gated `safety` reduce them? The answer decides whether roadmap items 15 (claim-versus-diff check)
and 16 (erosion signal) are built.

## 2. Chain

Steps, in order, reusing item 11's tasks unchanged (prompts, hidden specs, reference patches):

1. fr-pan
2. fr-exclusive-solo
3. fr-mode-leds
4. fr-tuning

Each step is a fresh headless `claude -p` session (a new ticket on the same codebase). Step 1 starts from skreeep2
6bf6c1b, as in item 11; step k starts from step k−1's final tree in the same arm and chain. A failed step does not
stop the chain: the next step builds on whatever the previous one left.

Arms `u` (ungated) and `gs` (gated, template engine, turn-end mode safety), 2 chains each: 16 runs, same day, run root
`~/gate-eval-chain/` (own HEAD copy, settings snapshot and base trees). Model sonnet, cap and flags as item 11.

## 3. Harness changes (`eval/gate-ab`, scala-llm-template)

- **`prepare-chain.sh`** — validation of the chain itself: on HEAD with the 4 reference patches applied in chain order
  (`git apply -3`, since fr-exclusive-solo shares `MixerMode.scala` with fr-pan), all 4 hidden specs pass, every
  existing test module passes, the holdout passes. Writes `chain-validation.txt`; a failure stops the campaign. Each
  step's own base is still prepared by `prepare.sh` (its hidden spec and validation record are reused).
- **`run.sh`** — optional `GA_SEED_TREE=<final-tree.tar.gz>`: the run's repository is unpacked from that archive
  instead of copied from the task's base tree (build output dirs excluded, as the archive already does), then
  committed as `base` as today. For `gs` the engine and settings are installed fresh in each step, exactly as in a
  normal `gs` run (any `.dsh/` and `.claude/` left by the previous step are replaced). Without `GA_SEED_TREE`,
  behaviour is unchanged.
- **`chain.sh <u|gs> <rep>`** — runs the 4 steps in order through `run.sh` and `score.py`, passing step k−1's
  `final-tree.tar.gz` as step k's seed, then runs the chain checks (below). Run ids stay `<task>-<arm>-<rep>`, unique
  inside the chain run root.
- **Chain regression check** — after step k is scored, each earlier step j's hidden spec is copied into step k's final
  tree and its test module run (holdout-style, own out dir, time cap). Recorded per (k, j): pass, fail, or not
  applicable (step j's own spec did not pass at the end of step j). A chain regression is a (k, j) that fails where
  step j's spec had passed.
- **Erosion figure (`erosion.sc`, scala-cli + scalameta)** — for the main (non-test) `.scala` files changed between
  skreeep2 6bf6c1b and step k's final tree: for each method, cyclomatic complexity CC (1 + one per `if`, `case`,
  `while`, `for` generator guard, `catch` case, `&&`, `||`) and SLOC (non-blank, non-comment lines); mass = CC × √SLOC;
  erosion = the share of total mass in methods with CC > 10 (SlopCodeBench's definition). Reported per step with the
  method count and total mass. The script is run on the 4 reference states too (HEAD + patches 1..k), as the
  reference line.
- **`report.py`** — a chain table: per arm and chain, per step: solved, false_done, regressions in existing tests,
  chain regressions, erosion, wall, cost.

Tests for every new script follow the harness's existing style (`tests/shell-test.sh`, `tests/test_*.py`); the erosion
script gets fixture files with known CC.

## 4. Decision rules (fixed before any run)

n = 8 step-runs per arm (2 chains × 4 steps).

- **Gate.** Per arm, failures = unsolved steps + chain regressions + regressions in existing tests. u − gs ≥ 2: "gate
  pays on chains"; ≤ 0: "no gain"; otherwise "unclear".
- **Item 15 (claim-versus-diff check).** Build it if any step in either arm has false_done through a route the
  tool-policy interpreter rule does not block (read from the transcript). Otherwise recorded as not needed here.
- **Item 16 (erosion signal).** Build it if erosion rises from step 1 to step 4 in at least 3 of the 4 chains, and
  ends above the reference line's step 4. Otherwise not built.

Reported beside the rules: per-step solved rate by position (does step 4 fail more than step 1?), median wall and cost
per arm, gate blocks in `gs`.

## 5. Write-up

A raw measurement file and a roadmap update in this repository's wiki: item 14 with its result; items 15 and 16 marked
by the rules above.

## 6. Risks

- n is small; read as a pointer, like the earlier studies.
- These tasks were mostly solved in item 11, and they are small; the chain may show nothing. That is an answer too: no
  decay on short chains of small features.
- fr-tuning touches `score`/`song`, not `control`, so it overlaps least with the earlier steps.
- A chain whose step fails early carries its damage forward; chain regressions are counted only against specs that
  passed, so one failure is not counted twice.
- Erosion on 8 small changed files may not move at all; the reference line shows what the intended solutions do.
