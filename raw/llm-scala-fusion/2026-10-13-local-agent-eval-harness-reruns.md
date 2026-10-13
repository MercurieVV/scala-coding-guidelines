# Local measurement: agent eval harness — live follow-up reruns (dsh with ScalaSemantic, agy without global plugins)

> Source: local measurement, this repository's owner — scala-coding-guidelines `harness/` (commits 778480a, 196cf15 on master); results `harness/PARITY.md` (live section, rep 2), `harness/agents/NOTES.md`; run root `~/agent-eval-live/`; follows `2026-10-12-local-agent-eval-harness.md`
> Collected: 2026-10-13
> Published: 2026-10-13

Measurement record, 2026-10-13. Numbers copied from `harness/PARITY.md` and `harness/agents/NOTES.md` as committed.

## What changed before the reruns

- dsh runs get the ScalaSemantic MCP: setup writes the launcher the profile's MCP row runs into the run copy
  (`scalasemantic-command` in `agents/dsh.conf`), git-excluded so it never shows in the agent's diff.
- `prices.deepseek-flash` filled at DeepSeek's peak rates (an upper bound).
- agy runs with `--disable-slash-commands`.
- dsh-gs with `--set harness.cap-seconds=2700`.

## Results (fr-pan, rep 2)

| arm | solved | wall s | turns | tokens in / out / cache read | cost USD (peak) |
|---|---|---|---|---|---|
| dsh-u | yes | 526 | 48 | 78909 / 47076 / 3504256 | 0.1012 |
| dsh-gs | yes | 1060 | 97 | 149760 / 104093 / 13881088 | 0.2531 |
| agy-u | yes | 754 | — | — | unknown (subscription) |

Rep 1 dsh costs from rescoring with the same prices: dsh-u 0.0554, dsh-gs 0.2432.

- dsh-gs rep 2: hidden 100 %, no regressions, holdout passed; gate blocks semantic-guard 9, semantic-guard-sealed 2,
  turn-end 1 (a live Stop block). dsh-gs rep 2 called the ScalaSemantic tools 48 times; dsh-u rep 2 none.
- dsh-gs rep 2 finished inside the old 1200 s cap, so rep 1's timeout says little about the arm.
- agy-u rep 2 ran with `--disable-slash-commands` and solved fr-pan; its contamination, gate blocks and env leaks are
  unchecked (no transcript).

## Limits

- One task, two reps per dsh arm, one rep of agy without the plugin: adapter checks, not an agent comparison.
- Costs use peak rates; off-peak is half.
