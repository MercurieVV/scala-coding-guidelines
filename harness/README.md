# harness

Scala 3 (scala-cli) agent-eval harness: runs coding agents against a target repo, scores the results and records them
as JSON. A port of the gate-ab shell/python scripts (`scala-llm-template/eval/gate-ab`, retired 2026-10-12; last at commit `98f5652`): same run root layout, same
`run.json` / `score.json` / `chain.json` fields, same `campaign.log` lines, same `gate-ab: <msg>` error texts.

## Build, test, launcher

```bash
scala-cli test harness                                   # the suite (JDK 21, pinned in project.scala)
scala-cli run harness -- --help                          # run without packaging (compiles on first call)
scala-cli --power package harness --main-class harness.cli.Main -o harness/out/harness -f
harness/out/harness validate --experiment harness/experiments/gate-ab.conf
```

The packaged launcher (`harness/out/`, git-ignored, never committed) avoids a recompile per call. It is a bootstrap
script that runs `$JAVA_HOME/bin/java`, else `java` on `PATH`; that JVM must be 21 or newer (score floats are printed
with Python's shortest repr, which needs JDK 19+ `Double.toString`), and the launcher refuses to start on an older one.
JDK 24+ prints `sun.misc.Unsafe` warnings from the Scala 3 runtime on stderr; `JAVA_OPTS=--sun-misc-unsafe-memory-access=allow`
silences them (do not set it on JDK 21, which rejects the flag). JVM flags can also be passed as `-J<flag>`.

**Root builds of this repository** must exclude the harness explicitly:

```bash
scala-cli compile . --exclude 'harness/**'
```

The root `project.scala` has `//> using exclude "harness/**"`, but it has no effect while `harness/project.scala`
exists, so a plain `scala-cli compile .` at the root picks up the harness sources.

## Commands

Every subcommand but `dsh-profile` takes `--experiment <file.conf>` (required) and any number of `--set path=value` overrides.

| command | does | GA |
|---|---|---|
| `validate` | load the experiment, resolve every adapter, print root, target, agents, arms (with their setup), campaign arms / reps / tasks / chain | — |
| `warm-head` | export the target at its commit to `<root>/head`, compile everything once | `warm-cache.sh` |
| `prepare <task>` | `<root>/base/<task>`: cut tree, hidden spec, `validation.txt`; exit 1 unless every line is `yes` | `prepare.sh` |
| `baseline` | run every test module at HEAD, write `head-baseline.json`, print `module pass/FAIL` | `score.py --baseline` |
| `run <task> <arm> <rep> [--seed <final-tree.tar.gz>]` | one run into `<root>/runs/<task>-<arm>-<rep>` | `run.sh` |
| `score <run-dir>` | write the run's `score.json` (prints nothing) | `score.py <run-dir>` |
| `rescore <run-dir>…` | score again, score only (e.g. old run roots or copies) | — |
| `campaign [--smoke]` | all runs in the fixed interleaved order, resumable; chain mode when `harness.chain` is set | `campaign.sh` |
| `prepare-chain` | validate `harness.chain`: `chain-validation.txt`, `chain-reference-erosion.json`; exit 1 unless both `yes` | `prepare-chain.sh` |
| `chain-check <run-dir> [<earlier-run-dir>…]` | write `chain.json`, print `chain <id>: regressions <n>, erosion <e>` | `chaincheck.py` |
| `report` | write `<root>/runs/results.csv` and `summary.md` (prints nothing) | `report.py <root>/runs` |
| `chain-report` | print the chained-feature-work table for `harness.chain` | `chain_report.py` |
| `unlock` | chmod 755 `base/`, `head/` and every run dir (after an interrupted run) | `ga_unlock_all` |
| `parity-static <old-root>…` | GA `score.py` pure functions vs the harness core on copied run roots, field by field (see `PARITY.md`) | — |
| `parity-arms --work --task --base-tree --snapshot` | GA run.sh's setup vs `Run.setupOnly` for arms u, g, t, gf, gs, p; settings, `.mcp.json`, `.dsh/` byte for byte | — |
| `parity-reports --work <old-root>…` | GA `report.py` / `chain_report.py` vs the harness reports on copies of each root | — |
| `parity-erosion --work <chain-reference-erosion.json>` | the chain's reference erosion line vs GA's recorded one | — |
| `dsh-profile --from <p>` | derive the headless dsh profile `<dsh-home>/profiles/<p>-headless` from `<p>` (`<p>`'s bundles minus the TUI-bound ones, plus dsh-headless; `<p>`'s patch layer minus TUI rows and the browser MCP), print the `dsh plugin --profile <p>-headless install` command; refuses an existing target. The only command that writes under the dsh home (`$DSH_HOME`, default `~/.dsh`) | — |

Exit codes: 0 ok; 1 on a harness error (one line `gate-ab: <msg>` on stderr) or a failed validation; 2 on a usage
error (help on stderr). `--help` prints the usage on stdout. Ctrl-C cancels the running command and its finalizers
run (isolation locks restored, temp dirs removed) before the process exits.

`PARITY.md`: how the parity subcommands were run, their results, and every intended difference from GA.

## Config layout

HOCON (typesafe config), validated at load; every error names the HOCON path. Right after loading, every subcommand
resolves the target and every configured agent (not only the ones an arm uses) and checks each adapter's required knobs
(`claude-code`: `mcp-json`; `dsh`: `profile`, `dsh-home`; `codex-cli` / `gemini-cli`: `mcp-servers.<n>.command`;
`generic`: a non-empty `command` whose placeholders are only `{prompt}`, `{model}`, `{repo}`), so a typo fails as
`gate-ab: agents.<name>.<key>: …` before any prepare, baseline or run work.

```
experiments/<name>.conf   harness { root, tasks-dir, template, caps, reps, arms, tasks, chain, smoke, report, chain-report }
                          + arms { … }, includes one target and the agents it uses
targets/<name>.conf       target { adapter, repo, commit, holdout-*, excluded-regression, precompile, mill, settings-source, all-tasks }
agents/<name>.conf        agents.<name> { adapter, … adapter knobs }
tasks/<task>/             task.env (or task.conf), prompt.txt, stub/<path>, hidden/<path>, reference.patch
```

Relative paths resolve against the experiment file. Values from the environment use HOCON substitution
(`root = ${?GA_ROOT}`). `experiments/chain.conf` shows an experiment that includes another and changes a few keys.

`--set` overrides one value before resolution: a path starting with `target.`, `agents.` or `arms.` is taken as is,
anything else is under `harness.` (`--set root=/tmp/x`, `--set reps=[2]`, `--set tasks=[noise]`). To replace the
campaign's arm list write `--set harness.arms=[g,u]` (`arms=…` alone replaces the arm definitions).

## Arms are data

An arm is an agent, a model and a list of setup steps applied to the run's repo copy after the agent's own setup:

```hocon
arms.g = ${gated-base} { setup = [ { replace-line = ".dsh/contract", prefix = "holdoutDir", text = "holdoutDir = {holdout}" } ] }
```

Step kinds: `run = [argv…]` (optional `fail` text), `write` + `text`, `append-line` + `text` (skipped if present),
`replace-line` + `prefix` + `text`, `json-edit` + `ops` (`delete`, `keep-only`, `filter`), `commit` + `paths`
(`allow-empty`, `fail`). Variables `{repo}`, `{holdout}` (holdout arms only), `{template}`, `{model}`, `{home}`.
Arm flags: `out-dir`, `holdout`, `settings-snapshot`, `hooks-allowed`, `turn-end-forbidden`. Adding an arm is a config
change only; `validate` shows the result.

## Adding an agent or a target

- **Agent**: implement `harness.agent.Agent` (setup, command, env, wipeState, collect, parse, envLeaks,
  privateStateDirs, settingsArtefact, and `validate` for its required knobs) in `agent/`, add its adapter name to `Agent.of`, and add
  `agents/<name>.conf` with `adapter = <name>` plus its knobs (read from `AgentSpec.raw`). Arms then select it with
  `agent = <name>`.
- **Target**: implement `harness.target.Target` (build invocation, test-log parsing, module discovery) in `target/`,
  add it to `Target.of`, and add `targets/<name>.conf`. `MillScala` is the reference implementation.
