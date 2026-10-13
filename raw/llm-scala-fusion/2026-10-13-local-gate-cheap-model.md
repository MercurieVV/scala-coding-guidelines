# Local measurement: the gate on a cheaper model — dsh ungated vs gated safety, deepseek-flash, feature requests (skreeep2)

> Source: local measurement, this repository's owner — scala-coding-guidelines `harness/` (experiment `harness/experiments/gate-cheap.conf`, pre-registered in its header and committed before any run); dsh 0.1.5-rc.1, profile scala-headless with ScalaSemantic; model deepseek-flash; run root `~/agent-eval-cheap/`
> Collected: 2026-10-13
> Published: 2026-10-13

Measurement record, 2026-10-13. Numbers copied from the runs' `score.json` and the campaign's `summary.md`.

## Why

Every gate measurement so far (items 1, 11, 14) ran Claude Code with sonnet, and sonnet solved nearly every task
ungated. A ceiling hides any gain. This study asks whether the gate pays where the model is weaker: dsh with
deepseek-flash, ungated (`dsh-u`) vs gated with turn-end mode safety (`dsh-gs`).

## Design (pre-registered)

Tasks fr-pan, fr-exclusive-solo, fr-mode-leds, fr-tuning (item 11's feature requests); 2 reps; 16 runs; cap 2700 s;
same day, interleaved. Failures per arm = unsolved + false_done + runs with regressions (n = 8 runs per arm).
u − gs ≥ 2 "the gate pays on a cheaper model", ≤ 0 "no gain", else "unclear". dsh-u 8 of 8 = the model is saturated on
these tasks; next step harder tasks. Budget USD 5 of DeepSeek at peak rates.

## Results

| arm | rep | task | solved | false_done | regressions | turns | wall s | cost USD | gate blocks |
|---|---|---|---|---|---|---|---|---|---|
| dsh-u | 1 | fr-pan | True | False | 0 | 80 | 669 | 0.2327 | 0 |
| dsh-u | 2 | fr-pan | True | False | 0 | 66 | 1536 | 0.2482 | 0 |
| dsh-gs | 1 | fr-pan | True | False | 0 | 57 | 686 | 0.1503 | 8 |
| dsh-gs | 2 | fr-pan | True | False | 0 | 67 | 1153 | 0.2664 | 8 |
| dsh-u | 1 | fr-exclusive-solo | True | False | 0 | 44 | 673 | 0.1847 | 0 |
| dsh-u | 2 | fr-exclusive-solo | True | False | 0 | 40 | 533 | 0.1507 | 0 |
| dsh-gs | 1 | fr-exclusive-solo | True | False | 0 | 50 | 756 | 0.1679 | 5 |
| dsh-gs | 2 | fr-exclusive-solo | True | False | 0 | 50 | 960 | 0.2187 | 4 |
| dsh-u | 1 | fr-mode-leds | True | False | 0 | 25 | 204 | 0.0418 | 0 |
| dsh-u | 2 | fr-mode-leds | True | False | 0 | 18 | 131 | 0.0314 | 0 |
| dsh-gs | 1 | fr-mode-leds | True | False | 0 | 62 | 467 | 0.1521 | 4 |
| dsh-gs | 2 | fr-mode-leds | True | False | 0 | 33 | 361 | 0.0613 | 3 |
| dsh-u | 1 | fr-tuning | True | False | 0 | 36 | 213 | 0.0517 | 0 |
| dsh-u | 2 | fr-tuning | True | False | 0 | 30 | 210 | 0.0572 | 0 |
| dsh-gs | 1 | fr-tuning | True | False | 0 | 91 | 1225 | 0.2545 | 8 |
| dsh-gs | 2 | fr-tuning | True | False | 0 | 63 | 924 | 0.1882 | 5 |

Failures: dsh-u 0, dsh-gs 0. Median wall: dsh-u 373.0 s, dsh-gs 840.0 s. Median cost: dsh-u USD 0.104, dsh-gs
USD 0.1781. Total cost: dsh-u USD 0.9985, dsh-gs USD 1.4594 (peak rates, incl. subagent sessions).

Decisions:

- Gate on a cheaper model: **no gain** (u − gs = 0).
- Saturation: dsh-u solved 8 of 8 — deepseek-flash is saturated on these feature requests; next step harder tasks.

## Audit

- The campaign report flags 10 of 16 runs as contaminated and drops them from its headline. Checked by hand: none
  reached a hidden spec, a reference patch or the HEAD tree. fr-tuning-dsh-u-1 listed the run root and read
  `campaign.log`, `head-baseline.json` and `baseline-resolve.log`; its `ls` of `base/` and `head/` returned
  "Permission denied" (the run's isolation lock). The other flags are tripwire false positives: `subagent: /` (a path
  token in a subagent prompt), `bash: /out`, regex fragments such as `bash: /^@@`, and the run's own `holdout` path
  (blocked by tool-policy).
- A subagent's own tool calls run in a child session that the contamination check does not read.
- One gate block was attributed to a gate called "the": the engine's post-edit check blocks with "BLOCKED by the DSH
  gate: …". Fixed in the harness (counted as `post-edit`) and fr-tuning-dsh-gs-1 rescored; no gate-ab run had that key.

## Limits

- n = 8 runs per arm, four small features, one cheap model.
- All 16 solved, so the study cannot show a gain; it shows the ceiling is not specific to sonnet.
- The gate cost time: median wall 2.3× and median cost 1.7× of ungated.
