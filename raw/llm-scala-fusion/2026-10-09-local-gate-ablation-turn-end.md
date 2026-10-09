# Local measurement: gate ablation — the gated arm without the turn-end Stop hook (skreeep2)

> Source: local measurement, this repository's owner — scala-llm-template branch `gate-ablation` (`eval/gate-ab/`, head 38a9d12); spec `docs/superpowers/specs/2026-10-08-gate-ablation-turn-end-design.md` (this repo); skreeep2 6bf6c1b tasks; headless `claude -p`, model sonnet
> Collected: 2026-10-09
> Published: 2026-10-09

Measurement record, 2026-10-09. Numbers copied from `report.py`'s `summary.md` and the run records as run.

## Setup

- Arm `t`: the gated settings snapshot with `hooks.Stop` (turn-end) deleted. SessionStart (health, baseline),
  PreToolUse (tool-policy, semantic-guard), PostToolUse (post-edit), the sandbox and `out-agent` stay.
- Tasks: backpressure, mcu, mixer-modes, scope-view — the 4 where the gate A/B arms differed. Reps 1 and 2: 8 runs,
  2026-10-09 16:53–17:27, 0 errors, no API 429.
- Comparison: the A/B's existing gated (`g`) and ungated (`u`) runs, reps 1 and 2, on the same 4 tasks (run
  2026-10-03 and 2026-10-08).
- Pre-registered rule over 8 counted `t` runs: solved ≤ 5 → "turn-end carries the gain"; 8 → "the gain comes
  without turn-end"; 6 or 7 → "unclear".

## Contamination audit

One `t` run was flagged: mcu-t-2, `Bash: /import`. The flagged command is a here-doc writing `$TMPDIR/body.scala`,
the agent's own scratch file; nothing outside the run was read. Cleared as a tripwire false positive, like the 6 A/B
flags (recorded in `~/gate-eval/runs/audit-cleared.txt`).

## Result

```
Ablation verdict (strict): incomplete (7 of 8)
Audited (cleared: mcu-t-2, mcu-u-1, mixer-modes-u-2, scope-view-g-2, scope-view-u-2):
| n (headline) | 8 | 8 | 8 |
| solved | 8 | 8 | 4 |
| solve rate [Wilson 95%] | 1.00 [0.68, 1.00] | 1.00 [0.68, 1.00] | 0.50 [0.22, 0.78] |
| false_done | 0 | 0 | 4 |
| median wall s | 200 | 80 | 52 |
| median cost USD | 0.33 | 0.23 | 0.18 |
Ablation verdict (audited): the gain comes without turn-end
```

Columns: gated, no turn-end, ungated. Strict (flags not cleared): gated 7 of 7, no turn-end 7 of 7, ungated 4 of 5.

## Per run (arm t)

| run | solved | wall s | gate blocks |
|---|---|---|---|
| backpressure-t-1 | yes | 139 | semantic-guard 3 |
| backpressure-t-2 | yes | 111 | semantic-guard 4 |
| mcu-t-1 | yes | 66 | semantic-guard 1 |
| mcu-t-2 | yes | 87 | semantic-guard 2 |
| mixer-modes-t-1 | yes | 83 | none |
| mixer-modes-t-2 | yes | 78 | none |
| scope-view-t-1 | yes | 77 | none |
| scope-view-t-2 | yes | 70 | none |

Post-edit returned no feedback text in any `t` run; the only hook events were 5 SessionStart events per run.

## Behaviour (Bash test runs `t`, MCP calls `m`, per rep 1 | rep 2; S solved, x not)

```
backpressure g S t0 m10 | S t2 m11
backpressure t S t1 m12 | S t0 m4
backpressure u S t3 m2 | x t3 m1
mcu          g S t2 m7 | S t5 m3
mcu          t S t1 m3 | S t2 m12
mcu          u x t3 m1 | S t2 m4
mixer-modes  g S t3 m10 | S t6 m8
mixer-modes  t S t2 m0 | S t2 m1
mixer-modes  u S t1 m0 | x t1 m0
scope-view   g S t0 m6 | S t0 m3
scope-view   t S t0 m1 | S t0 m0
scope-view   u S t0 m0 | x t0 m0
```

## Limits

- n = 8 per arm; the intervals overlap. `t` ran on a different day from `g` and `u`; model drift between days is
  not controlled, and no same-day ungated control was run.
- On mixer-modes and scope-view the `t` runs solved with no gate block, no post-edit feedback and, in scope-view-t-2,
  no test run and no MCP call — the same visible behaviour as the ungated failures. This study finds no mechanism
  for the remaining gain; part of the ungated failures may be noise.
- Turn-end's cost on these tasks: median wall 200 s with it, 80 s without.
