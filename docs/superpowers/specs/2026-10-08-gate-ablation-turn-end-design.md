# Gate ablation: is turn-end the part that pays? (design)

Date: 2026-10-08. Status: approved in conversation; written for review. Exploratory: 8 new runs.

## 1. Question

The gate A/B (`raw/llm-scala-fusion/2026-10-08-local-gate-ab-evaluation.md`) found the gated arm solving more, with
the holdout deciding no run. Which gate part buys that? This study tests one part: the turn-end Stop hook. The result
says whether turn-end is worth its wall time (it is the gate's main time cost) or whether the gain comes from the
parts that act during the turn (semantic-guard, tool-policy, post-edit, the sandbox).

## 2. Evidence that shaped it

From the 16 gated transcripts of the A/B: turn-end blocked 20 times in 14 runs: 11 "name does not resolve" (some are
false positives on the `-` operator), 7 format/scalafix, 1 failing downstream test (mixer-modes-g-2, fixed by the
agent). Only 1 of the 4 tasks the gated arm won traces to a test catch; the others saw only name and format blocks.
Semantic-guard fired in 14 of 16 gated runs.

## 3. Design

- Tasks: the 4 where the arms differed in the A/B — backpressure, mcu, mixer-modes, scope-view (gated 8/8, ungated
  4/8). The other 4 tasks cannot discriminate (noise unsolved by both arms; midi-synth, modes, note-ring solved by
  both).
- New arm `t`: the gated settings snapshot (`~/gate-eval/gated-settings.json`) with the `Stop` hook entry removed.
  Everything else of the gated arm stays: SessionStart (health, baseline), PreToolUse (tool-policy, semantic-guard),
  PostToolUse (post-edit), sandbox, `env.MILL_OUTPUT_DIR=out-agent`, precompile into `out-agent`. Removing Stop also
  removes the turn-end holdout run.
- Runs: 4 tasks × 2 reps (reps 1 and 2) of arm `t` = 8 runs. Arms `g` and `u` are not re-run; their reps 1 and 2 on
  the same 4 tasks (8 runs each) are the comparison. Same harness, model sonnet, 45-minute cap, prompt, tools, MCP set.
- Scoring: unchanged `score.py`. Contamination flags are audited by hand as in the A/B (Ruling 28 there); strict and
  audited numbers both reported.

## 4. Decision rule (fixed before the runs)

Over the 8 `t` runs, audited (g = 8/8 and u = 4/8 on these tasks):

- `t` solved ≤ 5: "turn-end carries the gain".
- `t` solved = 8: "the gain comes without turn-end".
- `t` solved 6 or 7: "unclear".

Reported beside it: Wilson intervals for all three arms, `false_done` counts, and median wall time `t` vs `g` (the
time turn-end costs). With 8 runs per arm the result is a pointer, not proof; it says which part to study or tune
next, not what to remove.

## 5. Components

In scala-llm-template `eval/gate-ab/`:

- `lib.sh`: accept arm `t`.
- `run.sh`: arm `t` writes the gated snapshot minus `hooks.Stop`; precompiles into `out-agent` like `g`.
- `campaign.sh`: arm list configurable (`GA_ARMS`, default `g u`), so this campaign runs `t` only.
- `report.py`: a third column for `t` when present, and the §4 verdict line; the A/B verdict stays as it is.
- Tests for each change beside the existing ones.

## 6. Preserved results and write-up

Per run the same artefacts as the A/B (`transcript.jsonl`, `final.diff`, final tree, `score.json`, settings). A raw
measurement file and a roadmap update in this repository's wiki.

## 7. Risks

- n = 8: a single run moves `t` across a band. The middle band absorbs noise.
- `g` and `u` ran on 2026-10-03 and 2026-10-08; `t` runs later. Model drift between days is not controlled.
- Arm `t` still runs post-edit, whose feedback may overlap turn-end's; the study measures removing the Stop hook only.
