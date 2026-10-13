# Local measurement: dsh live runs recounted with subagent sessions

> Source: local measurement, this repository's owner — scala-coding-guidelines `harness/` (commit a013c5f: dsh child-session tokens counted at scoring; rescore of `~/agent-eval-live/runs/`); results `harness/PARITY.md` live section; corrects token and cost figures in `2026-10-13-local-agent-eval-harness-reruns.md`
> Collected: 2026-10-13
> Published: 2026-10-13

The dsh adapter counted only the run's top-level session. Sessions started by a subagent of the run (same slug,
descending from the run's session, created at or after the run's start) are now added to tokens and cost; turns stay the
top-level count. Rescored with DeepSeek peak rates:

| run | tokens in / out / cache read (incl. subagent sessions) | cost USD (peak) |
|---|---|---|
| dsh-gs rep 1 | 179266 / 133476 / 14859776 | 0.3031 |
| dsh-gs rep 2 | 208481 / 132954 / 15138816 | 0.3129 |
| dsh-u rep 2 | 109981 / 61862 / 4123648 | 0.1320 |

Before the recount: dsh-gs rep 1 0.2432, dsh-gs rep 2 0.2531, dsh-u rep 2 0.1012. dsh-u rep 1 has no subagent session.
