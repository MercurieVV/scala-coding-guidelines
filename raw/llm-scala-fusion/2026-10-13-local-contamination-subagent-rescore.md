# Local measurement: contamination check reads dsh subagent sessions — rescore of the item-17 runs

> Source: local measurement, this repository's owner — scala-coding-guidelines `harness/` (commit 1dfda1e and its follow-up: subagent child sessions scanned for contamination; dsh `subagent` and `send_message` prose fields not read as paths); rescore of `~/agent-eval-cheap/runs/` and the dsh runs in `~/agent-eval-live/runs/`; follows `2026-10-13-local-gate-cheap-model.md`
> Collected: 2026-10-13
> Published: 2026-10-13

The item-17 audit found two gaps in the contamination check for dsh: a subagent's own tool calls (its child session)
were never read, and the prose of a `subagent` call was read as paths (`subagent: /`). After the fix, a child
session's calls are flagged as `subagent-session <tool>: <path>`, and the prose fields of `subagent` (description,
prompt) and of a child's `send_message` (message) are skipped.

Rescore of the 16 item-17 runs: contamination flags on 10 of 16 before, 6 of 16 after. No child session touched a
hidden spec, a reference patch, the HEAD tree or the holdout. The 6 that remain:

- fr-tuning-dsh-u-1: listed the run root and read `campaign.log`, `head-baseline.json`; `ls` of `base/` and `head/`
  denied by the isolation lock (as in the item-17 audit).
- fr-exclusive-solo-dsh-gs-1: listed the runs dir and read the run's own `dsh-hooks.json`.
- fr-mode-leds-dsh-gs-2: named the run's `holdout` path (blocked by tool-policy).
- fr-exclusive-solo-dsh-u-1, fr-pan-dsh-u-1, fr-tuning-dsh-gs-2: tripwire false positives from shell tokens
  (`bash: /out`, `bash: /target`, `bash: /20`, regex fragments such as `bash: /^@@`).

Shell-token false positives remain; changing the shared tokenizer would touch gate-ab parity and was left out.
