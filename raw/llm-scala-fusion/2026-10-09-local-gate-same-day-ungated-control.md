# Local measurement: same-day ungated control for the gate ablation (skreeep2)

> Source: local measurement, this repository's owner — scala-llm-template master e85d36d (`eval/gate-ab/`, no code change); skreeep2 6bf6c1b tasks; headless `claude -p`, model sonnet; run root `~/gate-eval-control/`
> Collected: 2026-10-09
> Published: 2026-10-09

Measurement record, 2026-10-09. Numbers copied from the run records (`score.json`) and `report.py`'s `summary.md` as run.

## Why

The ablation (`2026-10-09-local-gate-ablation-turn-end.md`) found arm `t` (gate without turn-end) solving 8 of 8 on the
4 tasks where the A/B arms differed, against the A/B's ungated 4 of 8 — but `t` ran on 2026-10-09 and the ungated runs
on 2026-10-03 and 2026-10-08. This control re-runs the ungated arm on the same day as `t`.

## Setup

- Arm `u` (permissions only: no hooks, no sandbox), tasks backpressure, mcu, mixer-modes, scope-view, reps 1 and 2:
  8 runs, 2026-10-09 19:00–19:28, 0 errors.
- Separate run root `~/gate-eval-control/` (base/ and head/ linked from `~/gate-eval/`, settings snapshot and HEAD
  baseline copied), so the A/B and ablation results stay untouched. The old run directories (`~/gate-eval/runs`,
  `~/gate-eval/invalid-runs`) were made unreadable for the campaign's duration.
- Pre-registered rule (written to `~/gate-eval-control/RULE.md` before any run), over 8 counted `u` runs, audited:
  solved ≤ 5 → "gate gain holds same-day"; 8 → "gap was day drift; gate gain not shown"; 6 or 7 → "unclear";
  fewer than 8 counted → "incomplete".

## Contamination audit

Two runs flagged, both path-heuristic false positives:

- backpressure-u-2, `Bash: /target/\` — the text is a `grep -v "/target/\|\.git/"` exclusion pattern, not a read.
- mcu-u-2, `Bash: /import` — the text is a `sed -i 's/import …/import …/'` replacement on the run's own `Mcu.scala`.

## Result

| run | solved | false_done | wall s | cost USD |
|---|---|---|---|---|
| backpressure-u-1 | yes | no | 53 | 0.16 |
| backpressure-u-2 | yes | no | 141 | 0.22 |
| mcu-u-1 | yes | no | 51 | 0.14 |
| mcu-u-2 | yes | no | 69 | 0.20 |
| mixer-modes-u-1 | yes | no | 62 | 0.17 |
| mixer-modes-u-2 | yes | no | 42 | 0.13 |
| scope-view-u-1 | yes | no | 54 | 0.17 |
| scope-view-u-2 | yes | no | 36 | 0.15 |

Audited: 8 of 8 counted, 8 of 8 solved, `false_done` 0, median wall 53.5 s, median cost USD 0.16, total cost USD 1.34.
Strict (flags not cleared): 6 of 6 solved, verdict "incomplete" (6 of 8 counted).

Verdict (audited, pre-registered): **gap was day drift; gate gain not shown.**

## Same-day comparison (2026-10-09, same 4 tasks, reps 1 and 2)

| | no turn-end (`t`) | ungated (`u`) |
|---|---|---|
| solved | 8 of 8 | 8 of 8 |
| false_done | 0 | 0 |
| median wall s | 80 | 53.5 |
| median cost USD | 0.23 | 0.16 |

## Limits

- n = 8; the ungated A/B runs' 4 of 8 on these tasks (2026-10-03, 2026-10-08) and today's 8 of 8 differ only by day.
  Whether the cause is model-side drift or run-to-run noise is not separable here.
- No same-day full-gate (`g`) runs; the gate's turn-end was not re-run today.
- The 4 tasks were chosen because the arms differed in the A/B; selecting on a past difference favours regression to
  the mean.
