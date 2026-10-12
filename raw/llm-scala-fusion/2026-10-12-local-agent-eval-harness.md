# Local measurement: agent eval harness — gate-ab generalized to any agent and ported to scala-cli

> Source: local measurement, this repository's owner — scala-coding-guidelines `harness/` (squash commit f1b1a62 on master); spec `docs/superpowers/specs/2026-10-11-agent-eval-harness-design.md`, plan `docs/superpowers/plans/2026-10-11-agent-eval-harness.md` (this repo); results `harness/PARITY.md`, `harness/agents/NOTES.md`; live run root `~/agent-eval-live/`
> Collected: 2026-10-12
> Published: 2026-10-12

Measurement record, 2026-10-12. Numbers copied from `harness/PARITY.md` and `harness/agents/NOTES.md` as committed.

## Why

scala-llm-template's `eval/gate-ab` (sh + python) measured coding agents on hidden-spec tasks but hardcoded Claude Code,
skreeep2 and the dsh engine. Every roadmap measurement since item 1's A/B ran through it, and only with Claude Code
(sonnet). The new harness extracts it so any agent, model and target project can be measured, and replaces every shell
and python script with Scala run by scala-cli.

## What was built

`harness/` in this repository: a scala-cli project (Scala 3.8.4, cats-effect, decline, ujson, os-lib, scalameta).
Commands: prepare, run, score, campaign, chain, report, chain-report, rescore, validate, unlock, dsh-profile.
Agent adapters: claude-code, dsh, codex-cli, gemini-cli, generic. Target adapter: mill-scala (skreeep2 is a config of
it). Arms (u, g, t, gf, gs, p) are HOCON data with setup steps. 491 tests.

## Parity with gate-ab

- Static tier (old score.py functions vs the new core on saved runs): 131 runs, 5240 fields, 0 mismatches. The first
  pass found 6491 mismatches, all from one port bug (system event leaves in hash order), fixed.
- Arm setup: 6 arms, 27705 files, 0 mismatches.
- Reports: 21 outputs, 0 differ.
- Chain reference erosion: 0.0, 0.5425, 0.4275, 0.2509 — all four rows equal GA's in every field.
- Build tier (both scorers rescoring copies of the same run, Mill builds included): 12/12 equal. No run in the six
  roots timed out, so the timeout case is a synthetic variant.
- Four port bugs found by parity, each fixed with a failing test first. Ten intended differences listed with reasons.

## Live smoke (fr-pan, rep 1, cap-seconds = 1200)

| arm | agent / model | solved | wall s | turns | tokens in / out / cache read | cost USD |
|---|---|---|---|---|---|---|
| claude-u | Claude Code 2.1.296 / sonnet | yes | 65 | 17 | 32 / 4996 / 485601 | 0.2257 (reported) |
| codex-u | codex-cli 0.148.0 / gpt-5.5 | yes | 307 | — | 99814 / 6506 / 2513152 | none reported; ≈0.50 at GPT-5 list prices |
| dsh-u | dsh 0.1.5-rc.1 / deepseek-flash | yes | 241 | 30 | 51868 / 26349 / 1378432 | null |
| dsh-gs | dsh 0.1.5-rc.1 / deepseek-flash | no (timed out at the 1200 s cap, not false_done) | 1200 | 107 | 128993 / 100906 / 13899648 | null |
| agy-u | Gemini via agy (generic, diff-only) / gemini-3.1-pro-high | no (false_done) | 358 | — | — | unknown (subscription) |
| gemini-u | gemini-cli 0.38.1 / gemini-2.5-pro | no | 5 | — | — | 0 |

- dsh-gs: hidden 100 %, no regressions, holdout passed: the agent had finished and the cap hit during its final Stop
  (turn-end) hook. Gate blocks semantic-guard 7, tool-policy 3.
- gemini-u: the account login is refused before any model call (IneligibleTierError); unverified live.
- agy-u: the user's global superpowers plugin made it stop for design approval: empty diff, exit 0.
- Two adapter defects found live, each fixed with a failing test first: Codex `input_tokens` counted the cached tokens
  too; the dsh adapter counted a tool-policy block as a turn-end block.

## Isolation: what each agent brings from the user's global setup

- Claude Code: the user's global `~/.claude/settings.json` hooks are kept out (gate-ab's mechanism, kept).
- Codex: global `~/.codex/AGENTS.md` and `~/.codex/skills` loaded; the skills sent the agent to read this repo's
  `wiki/index.md`. The scorer flags it (`contamination`). Full isolation needs a separate `CODEX_HOME`; the user
  declined it (a token refresh in a copy could break the ChatGPT login).
- dsh: none of the user's global `~/.claude/settings.json` hooks ran. dsh arms ran without ScalaSemantic, unlike the
  Claude Code and Codex arms (the profile's `scalasemantic-mcp.sh` is missing in the tree). The agent can read the
  user's `~/.dsh` (dsh-gs read settings.yaml and presets).
- agy: global MCP and plugins load, state written under `~/.gemini/antigravity-cli`.
- Runs with no transcript (generic adapter) record contamination, gate blocks and env leaks as null ("unchecked"),
  not as clean.

## Limits

- One task, rep 1 per arm: a smoke test of the adapters, not a comparison of agents.
- Gemini was not measured on the gemini-cli path; the agy path is diff-only.
- dsh-gs's unsolved verdict is the wall cap, not the agent's work; gate-ab's own cap was longer.
- Cost is comparable only for Claude Code; Codex reports none and dsh has no prices configured.
