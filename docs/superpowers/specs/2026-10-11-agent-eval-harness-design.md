# Agent eval harness: gate-ab generalized and ported to scala-cli (design)

Date: 2026-10-11. Status: approved in conversation; written for review.

## 1. Goal

scala-llm-template's `eval/gate-ab` (sh + python, ~3,900 lines with tests) measures coding agents on hidden-spec tasks,
but hardcodes Claude Code (`claude -p`, stream-json, `.claude/settings.json`, `.mcp.json`), skreeep2 (Mill, weaver/munit
logs, holdout repo, commit) and the dsh engine (arms g/t/gf/gs/p in code). This project:

1. extracts a harness that works for any agent, model and target project, configured per experiment;
2. replaces every shell and python script with Scala run by scala-cli.

Location: `scala-coding-guidelines/harness/` (own scala-cli project). `eval/gate-ab` stays untouched; retiring it is a
separate decision after parity (§7).

Success: every feature of gate-ab works in the new harness (single runs, resumable campaigns, prepare and validation,
hidden-spec scoring, regressions, holdout, isolation lock, tamper / contamination / hardcoding / env-leak tripwires,
gate blocks, chain mode, erosion, reports and verdicts); the parity checks of §7 pass; Claude Code, Codex CLI, Gemini CLI
and a generic command agent each run through one interface.

## 2. Style

Chosen per component:

- **Pure core** — plain Scala 3 functions, no effect library: transcript parsing, log parsers, diff checks,
  contamination, verdicts, stats, reports, erosion.
- **Orchestration** — cats-effect `IO`: process spawning with timeouts that kill the process tree (exit 124 kept),
  the isolation lock and temp trees as `Resource` (released on error, timeout, Ctrl-C), sequential campaign loop.
  fs2 process I/O where streaming is needed.
- **CLI** — decline-effect. **Config** — HOCON, read with pureconfig into case classes.
- **Libraries** — os-lib for paths where no effect is involved, upickle or circe for JSON (one of them, picked in the
  plan), scalameta 4.17.3 for erosion, munit (+ munit-cats-effect) for tests.

## 3. Layout

```
harness/
  project.scala            scala-cli directives (Scala 3.8.4, deps)
  core/                    pure: Transcript, TestLog, MillLog, Diff, Contamination, Verdicts, Stats,
                           Report, ChainReport, Erosion
  config/                  Experiment, AgentSpec, TargetSpec, ArmSpec, TaskSpec, SetupStep
  agent/                   Agent trait; ClaudeCode, CodexCli, GeminiCli, Generic
  target/                  Target trait; MillScala
  run/                     Prepare, Run, Score, Campaign, Chain, Rescore, Validate (IO)
  cli/                     Main
  test/                    munit suites, fixtures
  agents/ targets/ experiments/   HOCON
  tasks/                   copied from eval/gate-ab/tasks (task.env read as-is; task.conf accepted too)
  README.md, PARITY.md
```

The repository root `project.scala` gets `//> using exclude "harness/**"` so `scala-cli compile .` at the root does not
pick up the harness.

CLI: `harness <prepare|run|score|campaign|chain|report|chain-report|rescore|validate> --experiment <file.conf> [args]`.
A packaged launcher (`scala-cli package`) avoids recompiling per call.

## 4. Abstractions

**Agent** (adapter code + HOCON knobs):

- `setup(repo, arm)` — writes the agent's own config into the run copy (Claude Code: `.claude/settings.json`,
  `.mcp.json`; Codex: its config; Gemini: its settings).
- `command(prompt, model)` — argv for a headless run.
- `isolation` — env vars to unset, per-cwd memory or transcript dirs to wipe before a run (Claude Code: today's
  `CLAUDECODE…` list and `~/.claude/projects/<slug>`).
- `parse(transcript)` — a normalized result: turns, input / output / cache tokens, cost (each `Option`; Codex reports no
  USD cost), final text, tool calls `(kind: shell|edit|write|read|mcp|other, name, input, result, isError)`, hook
  messages. Gate blocks, contamination, env leaks and false_done read only the normalized form.

Adapters: `claude-code`, `codex-cli`, `gemini-cli`, `generic` (argv template; `transcript = none` scores the diff only,
turns and cost null). CLI flags and event shapes of Codex and Gemini are verified against the installed CLIs
(`/opt/homebrew/bin/codex`, `/opt/homebrew/bin/gemini`) with one live smoke run each (budget asked first); fixture
transcripts back their parser tests.

**Target** (adapter `mill-scala`; skreeep2 is a config of it): `export(commit)`, `compile`, `testModules`,
`allModules`, `runTests(modules, keepGoing)`, `parseFailures(log)`, `suites(tree, module)`, `buildBroken(log)`,
`holdout(dir)`, `precompile`. Repo path, commit, holdout repo and commit, excluded regression modules, expected holdout
test count, precompile tasks and caps live in the target's HOCON.

**Arm** (data): agent, model, holdout on/off, optional settings snapshot (frozen per campaign with a sha256, as today),
and `setup` steps run on the run copy in order:

- `run` (argv; any command, including a scala-cli script),
- `write` (path, text), `append-line` (path, text; skipped if present), `replace-line` (path, prefix, text),
- `json-edit` (path, ops: `delete`, `keep-only`, `filter` by substring),
- `commit` (message).

Variables: `${repo}`, `${holdout}`, `${template}`, `${model}`, `${home}`. Today's arms u, g, t, gf, gs, p are written
as HOCON in `experiments/` and must reproduce today's run copies (§7).

**Task**: prompt, hidden spec (moved from the tree or authored), cut files + stubs, reference patch, test module,
gate-visible flag — today's `task.env` fields.

## 5. Run directory

Unchanged from gate-ab: `run.json`, `prompt.txt`, `settings.json`, `transcript.jsonl`, `agent-stderr.log`,
`precompile.log`, `final.diff`, `final-tree.tar.gz`, `score.json`, `chain.json`; run root with `base/`, `head/`,
`head-baseline.json`, `gated-settings.json(.sha256)`, `runs/`, `chain-validation.txt`, `chain-reference-erosion.json`.
`run.json` gains `agent` and `model`. Old run roots stay readable by the new report and rescore.

## 6. Errors

- Config is validated at load: unknown adapter, arm, task or missing path fails before any run, naming the HOCON path.
- Runtime failures are a `HarnessError` ADT; prepare and run failures end that run, the campaign records and continues
  as today; scoring problems go to `score.json` `errors` with today's strings.
- The isolation lock (chmod 000 on `base/`, `head/`, other runs) is a `Resource`: always restored.
- The harness never writes to the target's source repo or holdout repo (read-only export only).

## 7. Parity

1. **Static tier** — all runs in `~/gate-eval`, `-control`, `-feature`, `-hard`, `-interp`, `-chain` (copies in scratch):
   old score.py pure functions vs the new core on the saved transcripts, diffs and logs (transcript fields, gate blocks,
   env leaks, contamination, tampering, hardcoding, build edits, weaver / hidden counts, Mill failure parsing). Exact
   match.
2. **Build tier** — ~12 runs covering each arm and outcome (solved, unsolved, false_done, timeout, regression, seeded
   chain step): two copies of each run dir, rescored the same day by old score.py and by the new scorer; `score.json`
   equal except durations and timestamps.
3. **Reports** — old `report.py`, `chain_report.py` vs new on the same roots: text diff empty.
4. **Arm setup** — for each of u, g, t, gf, gs, p: the run copy's settings, `.mcp.json` and `.dsh/` after setup equal
   gate-ab's (no agent started).
5. Every intended difference (a bug fixed in the port) is listed in `PARITY.md` with its reason.

Originals are never written.

## 8. Tests

- Every case in `tests/test_*.py` ported to munit.
- `tests/shell-test.sh` (run / campaign behaviour with fake claude and fake mill) becomes munit with in-process fake
  Agent and Target adapters, plus temp-dir integration tests of Run, Campaign (resume, skip, rerun, rescore) and Chain
  (seeding, downstream invalidation, seed sha mismatch).
- Erosion keeps its scalameta fixtures; Codex and Gemini parsers get fixture transcripts.

## 9. Out of scope

Retiring `eval/gate-ab`; new metrics; non-Mill target adapters (the Target trait allows them; sbt / scala-cli adapters
are later work); new studies.

## 10. Risks

- Size: score.py (921 lines) carries most of the logic and edge cases; the static parity tier is what keeps the port
  honest.
- Codex and Gemini live runs cost money and need logged-in CLIs; without them those adapters ship as "unverified live".
- JVM start-up per CLI call; mitigated by the packaged launcher.
- Mill behaviour can drift between rescore days; the build tier rescoring both sides the same day cancels it.
