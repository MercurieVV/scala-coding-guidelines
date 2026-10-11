# Agent Eval Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A scala-cli harness in `scala-coding-guidelines/harness/` that does everything scala-llm-template's `eval/gate-ab` does, for any agent (Claude Code, DeepSeek Harness, Codex CLI, Gemini CLI, generic command), any model and a pluggable target project, with no shell or python scripts.

**Architecture:** Pure core (parsers, verdicts, stats, reports, erosion) as plain Scala 3 functions; orchestration (processes, timeouts, isolation lock, campaign) in cats-effect `IO`; `Agent` and `Target` traits with adapters; experiments, arms, agents and targets in HOCON. Run-directory layout identical to gate-ab, so old run roots rescore and report unchanged.

**Tech Stack:** Scala 3.8.4, scala-cli 1.15.0, cats-effect 3.6.3, decline-effect 2.5.0, typesafe config 1.4.4 (HOCON), upickle/ujson 4.3.2, os-lib 0.11.5, scalameta 4.17.3, zstd-jni 1.5.7-4, munit 1.2.0, munit-cats-effect 2.1.0.

**Spec:** `docs/superpowers/specs/2026-10-11-agent-eval-harness-design.md`

**Source being ported (read-only):** `/Users/viktorskalinins/IdeaProjects/my/scala-llm-template/eval/gate-ab/` — called `GA/` below. Never modify it.

## How port tasks are written

The Python and sh sources are the behavioural spec for the port tasks. Port tasks give the Scala signatures, the
source location (`GA/score.py:53-131`) and the test file whose cases must be ported **case for case** (same inputs,
same expected values, test names kept as `test_x` → `"x"`). The executor reads the named source lines and ports them
line for line, keeping every error string verbatim (old reports and campaign retry logic match on them). New code
(abstractions, config, adapters, CLI) is given in full in the task.

## Global Constraints

- Location `harness/` inside `scala-coding-guidelines`; work on branch `agent-eval-harness`.
- Never stage `.gitignore`, `.claude/`, `raw/stainless-verification/*` in this repo; explicit paths in `git add`, never `-A`.
- Commits end with `Claude-Session: https://claude.ai/code/session_01NYhZujY9dqgFF2eCQLXzKw`; never `Co-Authored-By`.
- Never modify `GA/`, skreeep2 (`/Users/viktorskalinins/IdeaProjects/skreeep2`), skreeep2-holdout, or any `~/gate-eval*` run root (parity works on copies in the scratchpad).
- Runs never write `~/.dsh`; only `harness dsh-profile` writes there, and only when the user runs it.
- No `.sh`, `.py` files in `harness/` (test fixtures that are data, e.g. a captured transcript, are fine). Tests may start `/bin/sh -c` one-liners as fake external binaries.
- Error strings, `score.json` / `run.json` / `chain.json` field names and `campaign.log` line formats identical to GA.
- Live agent runs (Task 20) cost money: ask the user for the budget before the first one.
- Every pure function from GA keeps its GA name in camelCase (`parse_transcript` → `parseTranscript`).
- Root `project.scala` must exclude `harness/**`.
- Scala style: direct style for pure code and simple scripts; cats-effect `IO` + `Resource` for process, lock and temp-dir lifetimes.

## Deviation from the spec (recorded for review)

- Spec §2 names pureconfig. The plan reads HOCON with typesafe config, renders the resolved tree to JSON and decodes
  with hand-written ujson decoders, because setup steps are key-discriminated objects (`{run=…}`, `{write=…}`) that
  pureconfig's Scala 3 derivation does not express without adapters. Cost if wrong: the decoder layer (Task 3) is
  swapped; nothing else depends on the library.

## Review Focus

1. Ctrl-C or a crash during a run must restore the original modes of `base/`, `head/` and other run dirs (Task 2 `Lock` test with cancellation).
2. A timeout must kill the agent's grandchildren (a Mill daemon or MCP server the agent spawned), not only the direct child (Task 2 `Proc` test with a sleeping grandchild).
3. A HOCON variable with no value (`${holdout}` in an arm without holdout) must fail at config load naming the HOCON path, never reach a command line as a literal `${holdout}` (Task 3 test).
4. Old run roots whose `run.json` has no `agent` / `model` fields must score, rescore and report exactly as before (Task 10 and Task 14 tests on a GA-shaped `run.json`).
5. dsh: when the session dir holds several sessions (a crashed earlier attempt), the adapter must take the one created after the run started; none found → `no result event`, as a missing Claude result does (Task 16 test).

---

### Task 1: Scaffold the scala-cli project

**Files:**
- Create: `harness/project.scala`, `harness/core/Json.scala`, `harness/test/ScaffoldTest.test.scala`, `harness/README.md`
- Modify: `project.scala` (repo root)

**Interfaces:**
- Produces: package roots `harness.core`, `harness.config`, `harness.agent`, `harness.target`, `harness.run`, `harness.cli`; `harness.core.Json` helpers used by every later task:
  - `def readJson(p: os.Path): Option[ujson.Value]` (None on missing/unreadable/invalid)
  - `def writeJsonAtomic(p: os.Path, v: ujson.Value): Unit` (indent 2, write `p.tmp` then move, as GA `_write_json_atomic`)
  - `def readLenient(p: os.Path): String` (missing → `""`, invalid UTF-8 replaced, as GA `_read`)
  - `val Ansi: scala.util.matching.Regex` = `"\u001b\\[[0-9;]*m"`

- [ ] **Step 1: Branch**

```bash
cd /Users/viktorskalinins/IdeaProjects/my/scala-coding-guidelines && git checkout -b agent-eval-harness
```

- [ ] **Step 2: Write `harness/project.scala`**

```scala
//> using scala 3.8.4
//> using options -deprecation -feature -Wunused:all
//> using dep org.typelevel::cats-effect:3.6.3
//> using dep com.monovore::decline-effect:2.5.0
//> using dep com.typesafe:config:1.4.4
//> using dep com.lihaoyi::upickle:4.3.2
//> using dep com.lihaoyi::os-lib:0.11.5
//> using dep org.scalameta::scalameta:4.17.3
//> using dep com.github.luben:zstd-jni:1.5.7-4
//> using test.dep org.scalameta::munit:1.2.0
//> using test.dep org.typelevel::munit-cats-effect:2.1.0
```

- [ ] **Step 3: Write the failing test `harness/test/ScaffoldTest.test.scala`**

```scala
package harness.core

class ScaffoldTest extends munit.FunSuite:
  test("writeJsonAtomic then readJson round-trips"):
    val d = os.temp.dir()
    Json.writeJsonAtomic(d / "a.json", ujson.Obj("x" -> 1))
    assertEquals(Json.readJson(d / "a.json"), Some(ujson.Obj("x" -> 1)))
    assert(!os.exists(d / "a.json.tmp"))
  test("readJson is None on invalid json"):
    val d = os.temp.dir(); os.write(d / "b.json", "{")
    assertEquals(Json.readJson(d / "b.json"), None)
  test("readLenient is empty on a missing file"):
    assertEquals(Json.readLenient(os.temp.dir() / "none"), "")
```

- [ ] **Step 4: Run, expect compile failure**

Run: `cd harness && scala-cli test . 2>&1 | tail -5`
Expected: FAIL — `Not found: Json`

- [ ] **Step 5: Write `harness/core/Json.scala`**

```scala
package harness.core

import scala.util.Try

object Json:
  val Ansi: scala.util.matching.Regex = "\u001b\\[[0-9;]*m".r

  def readJson(p: os.Path): Option[ujson.Value] =
    Try(ujson.read(os.read(p))).toOption

  def writeJsonAtomic(p: os.Path, v: ujson.Value): Unit =
    val tmp = p / os.up / (p.last + ".tmp")
    os.write.over(tmp, ujson.write(v, indent = 2), createFolders = true)
    os.move(tmp, p, replaceExisting = true, atomicMove = true)

  def readLenient(p: os.Path): String =
    Try {
      val bytes = os.read.bytes(p)
      val dec = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
      dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString
    }.getOrElse("")
```

- [ ] **Step 6: Run, expect pass**

Run: `cd harness && scala-cli test . 2>&1 | tail -5`
Expected: 3 passed. If a dependency version does not resolve, use the newest release of the same major line that does (`cs complete-dep <org>::<name>:`) and ledger the ruling.

- [ ] **Step 7: Exclude `harness/` from the root project**

Append to root `project.scala`:

```scala
//> using exclude "harness/**"
```

Run: `scala-cli compile . 2>&1 | tail -3` from the repo root.
Expected: success, and `scala-cli compile . -v 2>&1 | grep -c harness/` prints `0`. If the glob form is rejected, use `//> using exclude "./harness"` and ledger it.

- [ ] **Step 8: README stub, commit**

`harness/README.md`: one paragraph (what it is, `scala-cli test harness`, `scala-cli run harness -- --help`); later tasks extend it.

```bash
git add project.scala harness/project.scala harness/core/Json.scala harness/test/ScaffoldTest.test.scala harness/README.md
git commit -m "harness: scaffold scala-cli project, JSON helpers

Claude-Session: https://claude.ai/code/session_01NYhZujY9dqgFF2eCQLXzKw"
```

---

### Task 2: Processes, timeouts, isolation lock, file utilities

**Files:**
- Create: `harness/run/Proc.scala`, `harness/run/Lock.scala`, `harness/run/Files.scala`
- Test: `harness/test/ProcTest.test.scala`, `harness/test/LockTest.test.scala`, `harness/test/FilesTest.test.scala`

**Interfaces:**
- Produces:
  - `final case class ProcResult(rc: Int, timedOut: Boolean)`
  - `object Proc { def run(cmd: Seq[String], cwd: os.Path, log: os.Path, env: Map[String, Option[String]] = Map.empty, cap: Option[Int] = None, stdin: Option[os.Path] = None, appendLog: Boolean = false, stderrTo: Option[os.Path] = None): IO[ProcResult] }` — stdout (and stderr unless `stderrTo`) to `log`; `env` value `None` unsets; on cap expiry kills the process **and all descendants** (`ProcessHandle.descendants`, SIGTERM, 5 s grace, SIGKILL) and returns `rc = 124, timedOut = true`.
  - `object Proc { def capture(cmd: Seq[String], cwd: os.Path): IO[(Int, String)] }` — small helper for git and similar.
  - `object Lock { def locked(dirs: List[os.Path], record: os.Path): Resource[IO, Unit] }` — records each dir's mode to `record` (`<octal> <path>` per line, GA `modes.txt` format), chmods 000, restores on release (success, error, cancel), deletes `record`.
  - `object Lock { def unlockAll(root: os.Path): IO[Unit] }` — GA `ga_unlock_all`: chmod 755 `base`, `head`, every `runs/*` dir.
  - `object Files { def sha256(p: os.Path): String; def tarGz(src: os.Path, entry: String, dest: os.Path, exclude: Seq[String]): IO[Unit]; def untarGz(archive: os.Path, dest: os.Path): IO[Unit]; def copyTree(src: os.Path, dest: os.Path, skipTopLevel: String => Boolean): Unit; def gitInitCommit(dir: os.Path, message: String): IO[Unit]; def gitArchive(repo: os.Path, commit: String, dest: os.Path): IO[Unit] }` — tar and git run as processes (`tar`, `git`); `gitInitCommit` uses `-c user.name=gate-eval -c user.email=gate-eval@local -c commit.gpgsign=false` exactly as GA.

- [ ] **Step 1: Port `GA/tests/test_timeout.py` (3 cases) to `ProcTest`, add the Review Focus 2 case**

```scala
package harness.run

import cats.effect.IO
import munit.CatsEffectSuite

class ProcTest extends CatsEffectSuite:
  test("timeout kills grandchildren"):
    val d = os.temp.dir()
    val pidFile = d / "gc.pid"
    // child shell starts a grandchild sleep and records its pid, then waits
    val cmd = Seq("/bin/sh", "-c", s"sleep 300 & echo $$! > $pidFile; wait")
    Proc.run(cmd, d, d / "log", cap = Some(1)).map { r =>
      assertEquals(r, ProcResult(124, true))
      val pid = os.read(pidFile).trim.toLong
      Thread.sleep(500)
      assert(!ProcessHandle.of(pid).map(_.isAlive).orElse(false), "grandchild still alive")
    }
  // + the 3 cases of GA/tests/test_timeout.py, same commands and expected rc / timing
```

- [ ] **Step 2: Run, expect failure** — `cd harness && scala-cli test . --test-only 'harness.run.ProcTest' 2>&1 | tail -5` → FAIL `Not found: Proc`.

- [ ] **Step 3: Implement `Proc.scala`** with `java.lang.ProcessBuilder` in `IO.blocking`, `IO.timeout` → on timeout `IO.blocking` kill descendants then the process (destroy, 5 s, destroyForcibly), append nothing to the log (callers write their own TIMEOUT lines, as GA `run_mill` does). `IO.onCancel` performs the same kill.

- [ ] **Step 4: Run, expect pass.**

- [ ] **Step 5: `LockTest` (failing first)** — three cases: modes restored after success; restored after an `IO.raiseError`; restored after `fiber.cancel` while inside the resource (Review Focus 1). Each creates two temp dirs with mode 755 and 700, asserts 000 inside, original after.

- [ ] **Step 6: Implement `Lock.scala`** (`Resource.make(acquire)(release)` where acquire writes the record then chmods; release reads the record). Run, expect pass.

- [ ] **Step 7: `FilesTest` (failing first)** — `sha256` of `"abc"` = `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`; `tarGz` excludes `repo/out` and `repo/out-agent` and keeps `repo/a.txt`; `copyTree` skipping `out`/`out-*` at top level only (GA `score.py` `skip`); `gitInitCommit` produces one commit with message `base`.

- [ ] **Step 8: Implement `Files.scala`, run all tests, expect pass.**

- [ ] **Step 9: Commit** `harness: process runner with tree-kill timeouts, isolation lock resource, file utilities`.

---

### Task 3: Configuration (HOCON) and task specs

**Files:**
- Create: `harness/config/Model.scala`, `harness/config/Load.scala`, `harness/config/TaskSpec.scala`, `harness/targets/skreeep2.conf`, `harness/agents/claude-code.conf`, `harness/experiments/gate-ab.conf`, `harness/experiments/chain.conf`
- Copy: `GA/tasks/` → `harness/tasks/` (unchanged; `cp -R`)
- Test: `harness/test/ConfigTest.test.scala`

**Interfaces:**
- Produces (Model.scala):

```scala
package harness.config

enum JsonOp:
  case Delete(path: String, required: Boolean)
  case KeepOnly(path: String, keys: List[String])
  case Filter(path: String, contains: String, requireNonEmpty: Boolean)

enum SetupStep:
  case Run(argv: List[String])
  case Write(path: String, text: String)
  case AppendLine(path: String, text: String)          // skipped when an identical line exists
  case ReplaceLine(path: String, prefix: String, text: String) // fails if no line starts with prefix (after optional spaces)
  case JsonEdit(path: String, ops: List[JsonOp])
  case Commit(message: String, paths: List[String], allowEmpty: Boolean)

final case class ArmSpec(
  name: String, agent: String, model: String,
  outDir: String,               // "out" (ungated) or "out-agent"
  holdout: Boolean,             // export the holdout into <run>/holdout, ${holdout} defined
  settingsSnapshot: Boolean,    // agent config comes from the campaign's frozen snapshot
  hooksAllowed: Boolean,        // env-leak check: hook events allowed
  turnEndForbidden: Boolean,    // env-leak check: a turn-end block is a leak (GA arm t)
  setup: List[SetupStep])

final case class AgentSpec(name: String, adapter: String, raw: ujson.Obj) // adapter knobs, read by the adapter

final case class TargetSpec(
  adapter: String, repo: os.Path, commit: String,
  holdoutRepo: os.Path, holdoutCommit: String, holdoutExpected: Int,
  excludedRegression: List[String], precompile: List[String], mill: String,
  settingsSource: os.Path,      // the file snapshotted for settingsSnapshot arms
  allTasks: List[String])

final case class ReportSpec(treatment: String, control: String, ablation: String)
final case class ChainReportSpec(control: String, treatment: String)

final case class Experiment(
  root: os.Path, tasksDir: os.Path, template: os.Path,
  capSeconds: Int, testCapSeconds: Int, regressCapSeconds: Int, precompileCapSeconds: Int,
  reps: List[Int], arms: List[String], tasks: List[String], chain: List[String],
  smokeTask: String, smokeRep: Int,
  report: ReportSpec, chainReport: ChainReportSpec,
  target: TargetSpec, agents: Map[String, AgentSpec], armSpecs: Map[String, ArmSpec])
```

- `object Load { def experiment(file: os.Path, overrides: Map[String, String] = Map.empty): Either[String, Experiment] }` — typesafe `ConfigFactory.parseFile(...).withFallback(ConfigFactory.systemEnvironment()).resolve()`; overrides are HOCON paths (CLI `--set root=/x`); error strings name the HOCON path (`arms.gs.setup[2]: unknown step keys [foo]`). Run-time variables are written as `{repo}`, `{holdout}`, `{template}`, `{model}`, `{home}` (braces without `$`, so HOCON does not try to resolve them; the spec's `${repo}` notation maps to these) and and expanded by `Vars.expand(s: String, vars: Map[String, String]): Either[String, String]`, which fails on an unknown or undefined placeholder (Review Focus 3). `Load` checks at load time that every placeholder in an arm's steps is known and that `{holdout}` is used only by arms with `holdout = true`.
- `TaskSpec(name, module, hiddenSpec, hiddenSpecSrc: Option[String], cutFiles: List[String], feature, referencePatch: Option[String], gateVisible: Boolean)`; `object TaskSpec { def load(tasksDir: os.Path, name: String): Either[String, TaskSpec] }` — reads `task.conf` (HOCON) if present, else `task.env` parsed as `KEY='value'` lines with sh single-quote rules including `'\''` (GA values use only that form; anything else → error naming the line).

- [ ] **Step 1: Write the failing tests** — cases:
  1. `gate-ab.conf` loads; arms `u, g, t, gf, gs, p` exist; `u.outDir == "out"`, others `"out-agent"`; `t.turnEndForbidden`; `u.hooksAllowed == false`, the others true.
  2. an arm using `{holdout}` with `holdout = false` → `Left` containing `arms.x.setup[0]` (Review Focus 3).
  3. unknown step key → `Left` naming the path.
  4. unknown agent name in an arm → `Left("arms.x.agent: unknown agent y")`.
  5. `--set reps=[1]` override applies.
  6. `TaskSpec.load` on every folder in `harness/tasks/` succeeds; `fr-pan` → `module == "control.test"`, `referencePatch == Some("reference.patch")`, `hiddenSpecSrc` defined; `noise` → `cutFiles == List("signals/src/com/github/mercurievv/skreeep2/signals/Noise.scala")`.
  7. `Vars.expand("{repo}/x", Map("repo" -> "/r")) == Right("/r/x")`; unknown placeholder → `Left`.

- [ ] **Step 2: Run, expect failure.**

- [ ] **Step 3: Write the HOCON files.** `targets/skreeep2.conf` from GA `lib.sh` defaults and `score.py` constants:

```hocon
target {
  adapter = mill-scala
  repo = /Users/viktorskalinins/IdeaProjects/skreeep2
  commit = 6bf6c1b
  holdout-repo = /Users/viktorskalinins/IdeaProjects/skreeep2-holdout
  holdout-commit = 8207d2f
  holdout-expected = 7
  excluded-regression = [backend.opencl.test, holdout.test, bench.test]
  precompile = ["__.compile", "+", "__.test.compile", "+", "__.semanticDbData", "+", "scalaSemanticWriteClasspath"]
  mill = ./mill
  settings-source = ${target.repo}/.claude/settings.json
  all-tasks = [noise, mixer-modes, mcu, note-ring, backpressure, scope-view, modes, midi-synth, adsr, sine, by-offset, sines-gen, fr-pan, fr-tuning, fr-mode-leds, fr-exclusive-solo]
}
```

`agents/claude-code.conf`:

```hocon
agents.claude-code {
  adapter = claude-code
  bin = claude
  bin = ${?CLAUDE_BIN}
  tools = [Bash, Edit, Write, Read, Grep, Glob, "mcp__scala-semantic"]
  mcp-servers { scala-semantic { command = /Users/viktorskalinins/.local/bin/scalasemantic-mcp, args = [serve, "."] } }
  allowed-mcp = [scala-semantic]
}
```

`experiments/gate-ab.conf` (GA defaults; `include` the two files):

```hocon
include "../targets/skreeep2.conf"
include "../agents/claude-code.conf"
harness {
  root = ${HOME}/gate-eval
  root = ${?GA_ROOT}
  tasks-dir = ../tasks
  template = /Users/viktorskalinins/IdeaProjects/my/scala-llm-template
  cap-seconds = 2700
  test-cap-seconds = 600
  regress-cap-seconds = 1200
  precompile-cap-seconds = 1800
  reps = [1, 2, 3]
  arms = [g, u]
  tasks = [noise, mixer-modes, mcu, note-ring, backpressure, scope-view, modes, midi-synth]
  chain = []
  smoke { task = midi-synth, rep = 9 }
  report { treatment = g, control = u, ablation = t }
  chain-report { control = u, treatment = gs }
}
gated-base {
  agent = claude-code, model = sonnet, out-dir = out-agent, holdout = true, settings-snapshot = true
  hooks-allowed = true, turn-end-forbidden = false
}
contract-step = { replace-line = ".dsh/contract", prefix = "holdoutDir", text = "holdoutDir = {holdout}" }
engine-full = [
  { run = [sh, "{template}/kit/harness/install.sh", "{repo}"] }
  { write = ".dsh/policy/turn-end.tsv", text = "# gate-eval arm\nmode\tfull\n" }
  { append-line = ".dsh/frozen-paths", text = ".dsh/policy/turn-end.tsv\tthe turn-end mode is the human's" }
  { commit = "engine, turn-end mode full", paths = [.dsh], allow-empty = true }
]
engine-safety = [
  { run = [sh, "{template}/kit/harness/install.sh", "{repo}"] }
  { write = ".dsh/policy/turn-end.tsv", text = "# gate-eval arm\nmode\tsafety\n" }
  { append-line = ".dsh/frozen-paths", text = ".dsh/policy/turn-end.tsv\tthe turn-end mode is the human's" }
  { commit = "engine, turn-end mode safety", paths = [.dsh], allow-empty = true }
]
arms {
  u  { agent = claude-code, model = sonnet, out-dir = out, holdout = false, settings-snapshot = false,
       hooks-allowed = false, turn-end-forbidden = false, setup = [] }
  g  = ${gated-base} { setup = [${contract-step}] }
  t  = ${gated-base} { turn-end-forbidden = true,
       setup = [{ json-edit = ".claude/settings.json", ops = [{ delete = hooks.Stop, required = true }] }, ${contract-step}] }
  gf = ${gated-base} { setup = ${engine-full} [${contract-step}] }
  gs = ${gated-base} { setup = ${engine-safety} [${contract-step}] }
  p  = ${gated-base} { setup = ${engine-safety} [
         { json-edit = ".claude/settings.json", ops = [
             { filter = hooks.PreToolUse, contains = "gate.sh\" tool-policy", require-non-empty = true }
             { keep-only = hooks, keys = [PreToolUse] }
             { delete = sandbox, required = false } ] }
         ${contract-step} ] }
}
```

Order inside `p` matches GA `ga_policy_only_settings` (filter, then keep only PreToolUse, then drop sandbox); its failure message must be GA's: `arm p: no tool-policy PreToolUse entry in <file>`. The GA `t` failure message `arm t: cannot remove the Stop hook` likewise (step error text per arm is the `JsonOp` failure wrapped with `arm <name>: `; Task 9 pins it).

`experiments/chain.conf`: `include "gate-ab.conf"`, `harness.root = ${HOME}/gate-eval-chain`, `harness.arms = [u, gs]`, `harness.reps = [1, 2]`, `harness.chain = [fr-pan, fr-exclusive-solo, fr-mode-leds, fr-tuning]`.

- [ ] **Step 4: Implement `Model.scala`, `Load.scala` (incl. `Vars`), `TaskSpec.scala`;** `cp -R GA/tasks harness/tasks`.

- [ ] **Step 5: Run, expect pass.**

- [ ] **Step 6: Commit** `harness: HOCON experiment config, arms as data, task specs; tasks copied from gate-ab`.

---

### Task 4: Normalized transcript, Claude Code parser, gate blocks

**Files:**
- Create: `harness/core/Transcript.scala`, `harness/core/Gate.scala`
- Test: `harness/test/TranscriptTest.test.scala`, `harness/test/GateTest.test.scala`

**Interfaces:**
- Produces:

```scala
package harness.core

enum ToolKind { case Shell, Edit, Write, Read, Search, Mcp, Other }

final case class ToolCall(name: String, kind: ToolKind, input: ujson.Obj, resultText: String, isError: Boolean, turn: Int):
  def command: String = if kind == ToolKind.Shell then input.value.get("command").flatMap(_.strOpt).getOrElse("") else ""

final case class Transcript(
  turns: Option[Int], inputTokens: Option[Long], outputTokens: Option[Long], cacheReadTokens: Option[Long],
  costUsd: Option[Double], toolCalls: Vector[ToolCall], finalText: String, texts: Vector[String],
  hookTexts: Vector[String], hasResult: Boolean, init: Option[ujson.Obj], hookEvents: Vector[String],
  apiError: Option[String])

object ClaudeTranscript:
  def parse(lines: Iterator[String]): Transcript            // GA/score.py:34-131 (_result_text, _string_leaves, parse_transcript)
  def kindOf(toolName: String): ToolKind                     // Bash→Shell, Edit/MultiEdit→Edit, Write→Write, Read→Read, Grep/Glob→Search, mcp__*→Mcp

object Gate:
  def isTurnEndText(text: String): Boolean                   // GA/score.py:134-135
  def gateBlocks(calls: Seq[ToolCall], hookTexts: Seq[String]): Map[String, Int] // GA/score.py:167-187
```

`first_clean_compile` moves to the Target (Task 7, it matches `./mill`). `env_leaks` moves to the Claude Code adapter (Task 8).

- [ ] **Step 1: Port the test cases** of `GA/tests/test_score.py` that call `parse_transcript`, `gate_blocks`, `is_turn_end_text` (grep `def test_` and the function names; port every one, same JSON lines, same expectations; Python `None` → `None`, dict counts → `Map`).
- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Port `GA/score.py:34-131` and `:134-135`, `:167-187`.** Keep the dedup rule for consecutive identical hook texts (`prev_hooks`).
- [ ] **Step 4: Run, expect pass.**
- [ ] **Step 5: Commit** `harness: normalized transcript, Claude Code transcript parser, gate blocks`.

---

### Task 5: Diff checks (tampering, build edits, hardcoding)

**Files:**
- Create: `harness/core/Diff.scala`
- Test: `harness/test/DiffTest.test.scala`

**Interfaces:**
- Produces: `object Diff { def sections(diff: String): List[(String, String)]; def tampering(diff: String): List[String]; def buildEdits(diff: String, isBuildFile: String => Boolean): List[String]; def hardcoding(diff: String, hiddenSpecText: String): List[String] }` — ports `GA/score.py:362-407` (`_diff_sections`, `tampering`, `build_edits`, `_STR`, `_NUM`, `hardcoding`). `buildEdits` takes the predicate so the Mill target passes `_.endsWith(".mill")`.

- [ ] **Step 1: Port every `test_score.py` case for `tampering`, `build_edits`, `hardcoding`.**
- [ ] **Step 2: Run, expect failure.**
- [ ] **Step 3: Port; Python regexes translate to `java.util.regex` (check lookbehinds `(?<![\w.])` compile unchanged).**
- [ ] **Step 4: Run, expect pass.**
- [ ] **Step 5: Commit** `harness: diff checks — tampering, build edits, hardcoding`.

---

### Task 6: Contamination tripwire

**Files:**
- Create: `harness/core/Contamination.scala`
- Test: `harness/test/ContaminationTest.test.scala`

**Interfaces:**
- Produces:

```scala
final case class Fence(
  runDir: os.Path, repo: os.Path,
  forbidden: List[os.Path],   // GA: root/base, root/head, target repo, other runs, the agent's private state dirs
  ownAllowed: List[os.Path],  // GA: the run's own Claude project dir
  allowedPrefixes: List[String]) // GA ALLOWED_PREFIXES + home caches (score.py:16-17, :566)

object Contamination:
  def check(calls: Seq[ToolCall], fence: Fence): List[String]  // GA/score.py:410-606 (tokens, heredocs, walkers, _resolve, contamination)
```

The GA function computes the forbidden roots inside; here the caller builds the `Fence` (Task 10: from experiment root, target repo, other run dirs, and `Agent.privateStateDirs`). `_claude_slug` moves to the Claude Code adapter (Task 8).

- [ ] **Step 1: Port every `test_score.py` case for `contamination`, `_bash_tokens`, `_strip_heredocs`, `_root_arg`, `_candidate_paths`, `_resolve`.** Where a GA test sets `GA_ROOT`/`GA_SKREEEP2`/`HOME` env vars, build the equivalent `Fence` instead.
- [ ] **Step 2: Run, expect failure.** - [ ] **Step 3: Port.** - [ ] **Step 4: Run, expect pass.**
- [ ] **Step 5: Commit** `harness: contamination tripwire with an explicit fence`.

---

### Task 7: Target trait, Mill+weaver adapter (pure parsers), verdicts

**Files:**
- Create: `harness/target/Target.scala`, `harness/target/MillScala.scala`, `harness/core/Verdicts.scala`
- Test: `harness/test/MillScalaTest.test.scala`, `harness/test/VerdictsTest.test.scala`

**Interfaces:**
- Produces:

```scala
package harness.target

final case class MillFailures(tests: Set[String], compile: Set[String], other: List[String], failed: Int)
final case class Counts(passed: Int, failed: Int, compiled: Boolean)

trait Target:
  def spec: harness.config.TargetSpec
  // pure
  def testCounts(log: String): Counts                              // GA weaver_counts
  def hiddenCounts(log: String, suite: String): Counts             // GA hidden_counts
  def suiteName(specText: String): Option[String]                  // GA suite_fq
  def suitePasses(log: String): Map[String, Int]                   // GA suite_passes
  def modulePasses(passes: Map[String, Int], suitesByModule: Map[String, List[String]]): Map[String, Int]
  def buildBroken(log: String): Boolean                            // GA build_broken
  def parseFailures(log: String, modules: Seq[String], allModules: Seq[String]): MillFailures
  def isCompileCommand(cmd: String): Boolean                       // GA first_clean_compile predicate ("./mill" and "compile")
  def isBuildFile(path: String): Boolean                           // ".mill"
  def outDirs(name: String): Boolean                               // "out" or "out-*"
  // effectful
  def allSuites(tree: os.Path, module: String): IO[List[String]]
  def otherSuites(tree: os.Path, module: String, hidden: String): IO[List[String]]
  def runBuild(tree: os.Path, args: Seq[String], outDir: String, log: os.Path, env: Map[String, Option[String]] = Map.empty, cap: Option[Int] = None, keepGoing: Boolean = false): IO[harness.run.ProcResult] // GA run_mill incl. the TIMEOUT log line
  def testModules(tree: os.Path, outDir: String, log: os.Path): IO[Either[BuildBroken, List[String]]] // raises ScoreError
  def allModules(tree: os.Path, outDir: String, log: os.Path): IO[Either[BuildBroken, List[String]]]
  def joinTasks(entries: Seq[Seq[String]]): Seq[String]            // GA _chain
  def hiddenRunArgs(module: String, suite: String): Seq[String]    // Seq(module + ".testOnly", suite)
  def regressionEntry(module: String, onlySuites: Option[List[String]]): Seq[String]
  def holdoutArgs: Seq[String]                                     // Seq("holdout.test")
  def holdoutEnv(dir: os.Path): Map[String, Option[String]]        // DSH_HOLDOUT_DIR
  def precompileArgs: Seq[String]
  def compileAllArgs: Seq[String]                                  // Seq("__.compile")

final case class BuildBroken(lastLine: String)
final class ScoreError(msg: String) extends Exception(msg)

object Target:
  def of(spec: harness.config.TargetSpec): Either[String, Target]   // "mill-scala" → MillScala; else Left("unknown target adapter x")
```

```scala
package harness.core
object Verdicts:
  def isSolved(hiddenCompiled: Boolean, hiddenPassed: Int, hiddenFailed: Int, holdoutPassed: Boolean, regressions: Seq[String], timedOut: Boolean): Boolean // GA :337
  def regressionCount(regressions: Seq[String], failedTests: Int, holdoutFailed: Int): Int // GA :342
  def holdoutFailedCount(failed: Int, compiled: Boolean, timedOut: Boolean): Int // GA :351
  def isFalseDone(solved: Boolean, timedOut: Boolean, exitCode: Int): Boolean // GA :356
```

Sources: `GA/score.py:190-356` and `:616-627`, `:652-681`.

- [ ] **Step 1: Port every `test_score.py` case for the functions above** (weaver_counts, hidden_counts, _sections, _name_matches, suite_passes, module_passes, all_suites, suite_fq, other_suites, build_broken, parse_mill_failures, is_solved, regression_count, holdout_failed_count, is_false_done, first_clean_compile → `isCompileCommand` + the first-turn fold in Task 10).
- [ ] **Step 2: Run, expect failure.** - [ ] **Step 3: Port.** `runBuild` uses `Proc.run(Seq(mill, "--no-daemon", "-j", "1") ++ (if keepGoing then Seq("-k") else Nil) ++ Seq("--ticker", "false", "--color", "false") ++ args, …, env + ("MILL_OUTPUT_DIR" -> Some(outDir)))` and appends `\nscore.py: TIMEOUT after <cap> s (counted as FAIL)\n` on 124 (string kept verbatim for old-log parity).
- [ ] **Step 4: Run, expect pass.**
- [ ] **Step 5: Commit** `harness: Target trait, Mill+weaver adapter, verdict rules`.

---

### Task 8: Agent trait and Claude Code adapter

**Files:**
- Create: `harness/agent/Agent.scala`, `harness/agent/ClaudeCode.scala`
- Test: `harness/test/ClaudeCodeTest.test.scala`

**Interfaces:**
- Consumes: `ArmSpec`, `AgentSpec`, `Experiment` (Task 3), `ClaudeTranscript` (Task 4).
- Produces:

```scala
package harness.agent

final case class RunCtx(
  exp: harness.config.Experiment, arm: harness.config.ArmSpec, task: harness.config.TaskSpec,
  runId: String, runDir: os.Path, repo: os.Path, holdout: Option[os.Path],
  settingsSnapshot: Option[os.Path]) // the campaign's frozen snapshot when arm.settingsSnapshot

trait Agent:
  def spec: harness.config.AgentSpec
  /** Writes the agent's own configuration into ctx.repo before arm setup steps run. */
  def setup(ctx: RunCtx): IO[Unit]
  /** argv of one headless run; cwd is ctx.repo. */
  def command(ctx: RunCtx, prompt: String): List[String]
  /** env changes for the agent process (None = unset). */
  def env(ctx: RunCtx): Map[String, Option[String]]
  /** per-cwd memory / session state wiped before the run (GA M2). */
  def wipeState(ctx: RunCtx): IO[Unit]
  /** after the run: make ctx.runDir/transcript.jsonl exist (Claude: stdout already is it). */
  def collect(ctx: RunCtx, startedAtMillis: Long): IO[Unit]
  def parse(lines: Iterator[String]): harness.core.Transcript
  def envLeaks(t: harness.core.Transcript, arm: harness.config.ArmSpec): List[String]
  /** dirs the agent keeps state in that a run must not read (contamination fence), minus the run's own. */
  def privateStateDirs(ctx: RunCtx): (List[os.Path], List[os.Path]) // (forbidden, ownAllowed)
  /** file names copied into the run dir as settings.json (GA artefact). */
  def settingsArtefact(ctx: RunCtx): Option[os.Path]

object Agent:
  def of(spec: harness.config.AgentSpec): Either[String, Agent] // claude-code | dsh | codex-cli | gemini-cli | generic
```

ClaudeCode specifics (from GA `run.sh:26-48, 77-91` and `score.py:138-156, 546-561`):
- `setup`: write `.mcp.json` `{ "mcpServers": <spec mcp-servers> }` (same JSON as GA heredoc, key order kept); `.claude/settings.json` = the snapshot with every occurrence of `target.holdoutRepo` replaced by the real holdout dir path (`pwd -P` form, i.e. `toNIO.toRealPath()`), or `{"permissions":{}}` when the arm has no snapshot. Fail `settings still mention <holdoutRepo>` if any occurrence remains.
- `command`: `List(bin, "-p", prompt, "--model", model, "--output-format", "stream-json", "--verbose", "--setting-sources", "project", "--strict-mcp-config", "--mcp-config", ".mcp.json", "--permission-mode", "acceptEdits", "--allowedTools") ++ tools`.
- `env`: unset the 14 vars in GA `run.sh:90-92`.
- `wipeState`: delete `${CLAUDE_CONFIG_DIR:-~/.claude}/projects/<slug>` where slug = realpath of repo with `/` and `.` → `-`.
- `collect`: no-op (stdout was written to `transcript.jsonl`).
- `envLeaks`: GA `env_leaks`, with `arm not in (g,t,gf,gs,p)` → `!arm.hooksAllowed` and `arm == "t"` → `arm.turnEndForbidden`; allowed MCP list from `allowed-mcp`.
- `privateStateDirs`: forbidden `~/.claude/projects` (config dir aware), own allowed `projects/<slug>`.

- [ ] **Step 1: Tests (failing first)** — port every `test_score.py` case of `env_leaks` and `_claude_slug`; plus: `command` equals the GA argv for model `sonnet` and the 7 tools; `setup` with a snapshot containing the holdout repo path twice writes both replaced; `setup` without snapshot writes `{"permissions":{}}`; `.mcp.json` parses to the GA object.
- [ ] **Step 2: Run, expect failure.** - [ ] **Step 3: Implement.** - [ ] **Step 4: Run, expect pass.**
- [ ] **Step 5: Commit** `harness: Agent trait, Claude Code adapter`.

---

### Task 9: Run — one evaluation run

**Files:**
- Create: `harness/run/Steps.scala`, `harness/run/Run.scala`
- Test: `harness/test/StepsTest.test.scala`, `harness/test/RunTest.test.scala`

**Interfaces:**
- Consumes: Tasks 2, 3, 7, 8.
- Produces:
  - `object Steps { def apply(steps: List[SetupStep], vars: Map[String, String], repo: os.Path, armName: String): IO[Unit] }` — executes in order; `JsonEdit` failures raise `HarnessError("arm <name>: …")` with GA's texts for t and p (`arm t: cannot remove the Stop hook`, `arm p: no tool-policy PreToolUse entry in <file>`); `ReplaceLine` with no match raises `contract rewrite failed (no holdoutDir line?)` when the path is `.dsh/contract`, else `replace-line: no line starting with <prefix> in <path>`; `Commit` uses the gate-eval identity.
  - `final case class HarnessError(msg: String) extends Exception(msg)`; messages printed as `gate-ab: <msg>` to keep GA's `ga_die` prefix (campaign logs are compared in parity).
  - `object Run { def runId(task: String, arm: String, rep: Int, arms: Set[String]): Either[String, String]; def apply(exp: Experiment, task: String, arm: String, rep: Int, seed: Option[os.Path]): IO[Unit] }`

`Run.apply` is a port of `GA/run.sh` in the same order: run dir reset; seed handling (unpack, drop `.git .dsh .claude`, restore `.dsh` `.claude` from the task base, sha256, git init + commit `base`) or copy of `base/<task>/tree`; `prompt.txt`; holdout export when `arm.holdout`; `Agent.setup`; settings snapshot sha check against `gated-settings.json.sha256`; `Steps`; copy settings artefact to `<run>/settings.json`; commit `arm <name>` with `--allow-empty` and tag `base`; precompile into `arm.outDir` with `precompile-cap-seconds` (failure fatal unless seeded); `Lock.locked(base, head, other runs)` around `Agent.wipeState`, the agent process (cap `cap-seconds`, stdin `/dev/null`, stdout → `transcript.jsonl`, stderr → `agent-stderr.log`) and `Agent.collect`; `run.json` with GA's fields in GA's order plus `"agent"` and `"model"` appended; `final.diff` (`git add -A -N . && git diff base -- . ':!out' ':!out-agent' ':!out-holdout'`); `final-tree.tar.gz` excluding the three out dirs; the console line `run <id>: exit <rc>, <s> s (precompile <p> s)`.

Run ids: `runId` accepts any arm name present in the experiment (GA's fixed list becomes `arms.keySet`) and reps `1..9`; message texts as GA (`arm must be one of …`, `rep must be 1..9, got x`).

- [ ] **Step 1: `StepsTest` (failing first)** — one test per step kind on a temp git repo; `p`'s json-edit on the real `~/gate-eval/gated-settings.json` **copied to a temp file** yields exactly what GA `ga_policy_only_settings` yields (compute GA's expected output in the test by reading a fixture produced once with GA's python and stored in `harness/test/fixtures/arm-p-settings.json` — produce it in this step with `python3 - <<'PY' … PY` from `GA/lib.sh:52-60` against the copy, and save the fixture); same for `t` (`arm-t-settings.json`, GA `run.sh:38-43`).
- [ ] **Step 2: `RunTest` (failing first)** with a fake agent (`Agent` implemented in the test: `command` = `/bin/sh -c 'echo "{\"type\":\"result\",\"num_turns\":1}"; echo hi > new.txt'`) and a fake target whose `runBuild` writes a log and returns 0. Cases ported from `GA/tests/shell-test.sh` run sections (lines ~222-370): run dir layout and artefacts; `run.json` fields; base/head/other runs unreadable during the agent (the fake agent's command `test -r <base>` writes the result to a file) and readable after; seeded run: `.dsh` from base, `seed_sha256` set, precompile failure tolerated; unseeded precompile failure → error `precompile failed (see …)`; timeout → `timed_out: true`, exit 124; the Claude projects slug dir wiped; env vars unset (the fake agent prints `env`).
- [ ] **Step 3: Run, expect failure.** - [ ] **Step 4: Implement.** - [ ] **Step 5: Run, expect pass.**
- [ ] **Step 6: Commit** `harness: setup steps executor and single-run orchestration`.

---

### Task 10: Score — one run's verdict, and the HEAD baseline

**Files:**
- Create: `harness/run/Score.scala`
- Test: `harness/test/ScoreTest.test.scala`

**Interfaces:**
- Consumes: Tasks 4–9.
- Produces: `object Score { def apply(exp: Experiment, runDir: os.Path): IO[ujson.Obj]; def baseline(exp: Experiment): IO[Map[String, Boolean]] }` — ports `GA/score.py:684-901` (`baseline`, `_export_holdout`, `_read_baseline`, `main`, `errors_have`). The record starts with GA's field set and order (`score.py:740-745`); written partially then finally with `Json.writeJsonAtomic`. Agent chosen from `run.json` `agent` field, default `claude-code` when absent (Review Focus 4); arm spec from the experiment by `run.json` `arm`; when the arm is not in the experiment (an old root's arm), a synthetic `ArmSpec` with GA's arm semantics: `hooksAllowed = arm in {g,t,gf,gs,p}`, `turnEndForbidden = arm == "t"`. `first_clean_compile_turn` = first `ToolCall` with `kind == Shell`, `target.isCompileCommand(command)`, not error, result without `[error]`. Contamination fence: forbidden `root/base`, `root/head`, `target.repo`, other `runs/*` dirs, the agent's private dirs; own allowed from the agent.

- [ ] **Step 1: Port every `test_score.py` case that drives `main` / `baseline`** (they use fake Mill launchers via `GA_MILL`; here a fake `Target` whose `runBuild` returns scripted logs per args), and add: a `run.json` without `agent` / `model` scores like GA (Review Focus 4).
- [ ] **Step 2: Run, expect failure.** - [ ] **Step 3: Port.** - [ ] **Step 4: Run, expect pass.**
- [ ] **Step 5: Commit** `harness: scoring and HEAD baseline`.

---

### Task 11: Prepare, HEAD tree, task and chain validation

**Files:**
- Create: `harness/run/Prepare.scala`
- Test: `harness/test/PrepareTest.test.scala`

**Interfaces:**
- Produces: `object Prepare { def head(exp: Experiment): IO[Unit]; def task(exp: Experiment, task: String): IO[Boolean]; def chain(exp: Experiment, steps: List[String]): IO[Boolean] }` — ports `GA/warm-cache.sh`, `GA/prepare.sh`, `GA/prepare-chain.sh` (incl. `ga_check_patch`, `git update-index -q --refresh` before each `git apply -3`, the reference erosion file via `Erosion` from Task 13 — `chain` is completed in Task 13; here it is written against `trait ErosionMeasure { def measure(tree: os.Path, base: String): IO[Either[String, Double]] }` passed in). Output files and line formats identical (`validation.txt`, `chain-validation.txt`, `chain-reference-erosion.json`, logs).

- [ ] **Step 1: Port the prepare and prepare-chain sections of `GA/tests/shell-test.sh`** (around lines 98-220 and 578-602) to munit with a temp git repo as the target and a fake `Target`.
- [ ] **Step 2: Run, expect failure.** - [ ] **Step 3: Port.** - [ ] **Step 4: Run, expect pass.**
- [ ] **Step 5: Commit** `harness: prepare, HEAD tree, task and chain validation`.

---

### Task 12: Campaign and chain mode

**Files:**
- Create: `harness/run/Campaign.scala`
- Test: `harness/test/CampaignTest.test.scala`

**Interfaces:**
- Produces: `object Campaign { def apply(exp: Experiment, smoke: Boolean, ops: CampaignOps): IO[Unit] }` with

```scala
trait CampaignOps:  // real implementation wires Run, Score, Prepare, Chain; tests pass fakes
  def run(task: String, arm: String, rep: Int, seed: Option[os.Path]): IO[Unit]
  def score(runDir: os.Path): IO[Unit]
  def prepare(task: String): IO[Boolean]
  def baseline: IO[Unit]
  def prepareChain(steps: List[String]): IO[Boolean]
  def chainCheck(runDir: os.Path, earlier: List[os.Path]): IO[Unit]
```

Port of `GA/campaign.sh`: caffeinate (re-exec is replaced by spawning `caffeinate -dims -w <own pid>` as a background process for the campaign's lifetime when the binary exists; message `gate-ab: caffeinate not found, running unwrapped` otherwise); validation of reps (1..4), arms (known to the experiment), tasks (in `target.all-tasks`); settings snapshot (`gated-settings.json` + `.sha256`, from `target.settings-source`, only when some selected arm has `settingsSnapshot`); preflight prepare when `validation.txt` is not all `=yes` with 3 or 4 lines; baseline when missing; chain mode (validation file, per (rep, arm) step loop, `redo`, seed sha mismatch removal, chain check when the agent ran or `chain.json` is missing or has an error, `chain <id> regressions=<n>` log line); `one()` with skip / run / rerun / rescore and the `campaign.log` line `<date> <id> exit=<rc> solved=<s>[ retry=<how>(was: <e>)]`; smoke (`smoke.task`, `smoke.rep`, report arms treatment then control).

- [ ] **Step 1: Port the campaign sections of `GA/tests/shell-test.sh`** (around lines 375-575) to munit with fake `CampaignOps` that record calls; assert the same call sequences and `campaign.log` lines.
- [ ] **Step 2: Run, expect failure.** - [ ] **Step 3: Port.** - [ ] **Step 4: Run, expect pass.**
- [ ] **Step 5: Commit** `harness: resumable campaign and chain mode`.

---

### Task 13: Chain check and erosion

**Files:**
- Create: `harness/core/Erosion.scala`, `harness/run/ChainCheck.scala`
- Copy: `GA/tests/fixtures/erosion/` → `harness/test/fixtures/erosion/`
- Test: `harness/test/ErosionTest.test.scala`, `harness/test/ChainCheckTest.test.scala`
- Modify: `harness/run/Prepare.scala` (wire the real erosion measure)

**Interfaces:**
- Produces: `object Erosion { def methodMetrics(source: String): Either[String, List[MethodMetric]]; def aggregate(ms: List[MethodMetric]): ErosionFigure; def changedMainFiles(tree: os.Path, base: String): IO[List[os.Path]]; def measure(tree: os.Path, base: String): IO[Either[String, ErosionFigure]] }` with `MethodMetric(name: String, cc: Int, sloc: Int)` and `ErosionFigure(erosion: Double, methods: Int, mass: Double)`. Ports `GA/erosion.scala` (in-process scalameta now, no subprocess) and `GA/erosion.py`. `object ChainCheck { def apply(exp: Experiment, runDir: os.Path, earlier: List[os.Path]): IO[ujson.Obj] }` ports `GA/chaincheck.py` (writes `chain.json`, `chain_regressions` None unless complete, `erosion_error` separately).

- [ ] **Step 1: Port `GA/tests/test_erosion.py` and `GA/tests/test_chaincheck.py`** (7 + 6 cases).
- [ ] **Step 2: Run, expect failure.** - [ ] **Step 3: Port.** - [ ] **Step 4: Run, expect pass.**
- [ ] **Step 5: Commit** `harness: chain regression check and erosion figure (scalameta in-process)`.

---

### Task 14: Reports

**Files:**
- Create: `harness/core/Stats.scala`, `harness/core/Report.scala`, `harness/core/ChainReport.scala`
- Test: `harness/test/ReportTest.test.scala`, `harness/test/ChainReportTest.test.scala`

**Interfaces:**
- Produces: `object Stats { def wilson(k: Int, n: Int, z: Double = 1.96): (Double, Double); def signTest(diffs: Seq[Int]): Double; def median(xs: Seq[Double]): Option[Double] }`; `object Report { def main(root: os.Path, spec: ReportSpec): IO[String] }` (writes the same files GA `report.py` writes and returns the summary text); `object ChainReport { def main(root: os.Path, steps: List[String], spec: ChainReportSpec): IO[String] }`. `decide(g, u)` becomes `decide(treatment, control)`; the ablation rule uses `spec.ablation`; with the default spec (`g`, `u`, `t`; `u`, `gs`) output text is byte-identical to GA's.

- [ ] **Step 1: Port `GA/tests/test_report.py` (27) and `GA/tests/test_chain_report.py` (7).** Add one case: a run root whose `run.json` files lack `agent` / `model` (Review Focus 4).
- [ ] **Step 2: Run, expect failure.** - [ ] **Step 3: Port `GA/report.py`, `GA/chain_report.py`.** Python float formatting (`%.1f`, `%.2f`, `round`) must match: use `BigDecimal(x).setScale(n, RoundingMode.HALF_EVEN)` where GA uses `round`, `String.format(java.util.Locale.ROOT, …)` where GA uses `%`.
- [ ] **Step 4: Run, expect pass.**
- [ ] **Step 5: Commit** `harness: campaign and chain reports`.

---

### Task 15: CLI and packaging

**Files:**
- Create: `harness/cli/Main.scala`
- Modify: `harness/README.md`
- Test: `harness/test/MainTest.test.scala`

**Interfaces:**
- Produces: `object Main extends CommandIOApp("harness", "agent eval harness")` with subcommands, each taking `--experiment <file>` and repeatable `--set path=value`:
  - `prepare <task>`, `warm-head`, `baseline`, `run <task> <arm> <rep> [--seed <tar.gz>]`, `score <run-dir>`, `campaign [--smoke]`, `chain-check <run-dir> [<earlier-run-dir>…]`, `prepare-chain`, `report`, `chain-report`, `rescore <run-dir>…` (score only, for copies), `validate` (load the config, print arms / tasks / agents, exit 0), `dsh-profile --from <profile>` (Task 16), `unlock` (`Lock.unlockAll`).
  - Exit codes: 0 ok, 1 `HarnessError` / validation failure (message `gate-ab: <msg>` on stderr), 2 usage.

- [ ] **Step 1: Test (failing first)** — `validate --experiment harness/experiments/gate-ab.conf` exits 0 and prints the 6 arms; an unknown subcommand exits 2; `run` with an unknown arm exits 1 with `gate-ab: arm must be one of …`.
- [ ] **Step 2: Run, expect failure.** - [ ] **Step 3: Implement.** - [ ] **Step 4: Run, expect pass.**
- [ ] **Step 5: Package** — `cd harness && scala-cli --power package . --main-class harness.cli.Main -o out/harness -f` → `harness/out/harness validate --experiment experiments/gate-ab.conf` works. Add `harness/out/` to `harness/.gitignore` (new file inside harness; the root `.gitignore` is never staged).
- [ ] **Step 6: README** — commands, config layout, arms as data, how to add an agent / target, parity status pointer.
- [ ] **Step 7: Commit** `harness: CLI, launcher packaging, README`.

---

### Task 16: DeepSeek Harness (dsh) adapter

**Files:**
- Create: `harness/agent/Dsh.scala`, `harness/agent/DshProfile.scala`, `harness/agents/dsh.conf`, `harness/test/fixtures/dsh/session-sample.jsonl`
- Test: `harness/test/DshTest.test.scala`

**Interfaces:**
- Consumes: `Agent`, `RunCtx` (Task 8).
- Produces: `Dsh` adapter (`adapter = dsh`) and `object DshProfile { def derive(home: os.Path, from: String): IO[os.Path] }`.

`agents/dsh.conf`:

```hocon
agents.dsh {
  adapter = dsh
  bin = dsh
  profile = scala-headless          # derived by `harness dsh-profile --from scala`
  dsh-home = ${HOME}/.dsh
  dsh-home = ${?DSH_HOME}
  hooks-from = ".dsh/hooks.json"    # gated arms: hook map file in the run copy (Claude Code hook dialect)
  prices {}                         # model -> { input-per-mtok, output-per-mtok, cache-read-per-mtok } (USD); optional
}
```

Behaviour:
- `setup`: writes `<runDir>/dsh-hooks.json` = `{"hooks": <map>}`, where `<map>` is the JSON at `hooks-from` in the run copy when `arm.hooksAllowed` (read **after** arm setup steps — so `setup` registers a deferred write performed by `Run` after `Steps`; add `def afterSteps(ctx: RunCtx): IO[Unit]` to `Agent` with a no-op default, ClaudeCode keeps the no-op) else `{}`; writes `<runDir>/dsh-overlay.yml`:

```yaml
- id: claude-code-hooks
  config:
    configPath: <runDir>/dsh-hooks.json
- id: agent-default-model
  config:
    model: <arm.model>
```

  The exact row ids are read from the derived profile (`dsh --profile <p> --dump-config`, run once in `setup`, parsed for the rows named `@deepseek-ai/dsh-hooks-claude-code` and the default-model row); if either row is not found, fail `dsh: profile <p> has no <row> row (see dsh --dump-config)`. The settings artefact is `dsh-overlay.yml`.
- `command`: `List(bin, "--profile", profile, "--patch", (runDir / "dsh-overlay.yml").toString, prompt)`.
- `env`: `DSH_PERMISSION_MODE -> danger-full-access`, `CLAUDE_PROJECT_DIR -> repo`, and unset GA's Claude vars.
- `wipeState`: delete `<dsh-home>/sessions/<slug>` where slug = `"--" + realpath(repo).drop(1).replace('/', '-') + "--"` (check against an existing dir name such as `--Users-viktorskalinins-IdeaProjects-skreeep2--`, and make the test assert that mapping).
- `collect`: in `<dsh-home>/sessions/<slug>/`, pick the `session-*/session.v3.jsonl.zstd` (or `session.jsonl.zstd`) whose `session` header `createdAt >= startedAtMillis`, newest first (Review Focus 5); decompress with zstd-jni into `<runDir>/transcript.jsonl`; none → leave `transcript.jsonl` empty (scoring then reports `no result event`).
- `parse` (events seen in real sessions): `session` → `init`-like record; `step/start` counts turns (max `turn` × steps → `turns` = number of `step/start`); `tool/call` (`name`, `arguments` JSON string → input object; kind: `bash`→Shell, `edit`→Edit, `write`→Write, `read`→Read, `grep`/`glob`→Search, `mcp__*`→Mcp) keyed by `callId`; `tool/result` (`message.content[].content[].text` joined; `isError` when the text starts with `Error:` or the result carries an error flag); `hook/result` with `decision == "block"` → `hookTexts += stderrSummary` and `hookEvents += point`; `assistant/message` text parts → `texts`, last non-empty → `finalText`; `turn/end` → `hasResult = true`, `reason.kind == "error"` → `apiError`; usage fields (find them in the sample; `None` if absent) → tokens; cost = tokens × `prices(model)` when configured.
- `envLeaks`: from the `request/header` event's tool list and MCP servers: MCP servers other than the configured ones, or hook events present when `!arm.hooksAllowed`.
- `privateStateDirs`: forbidden `<dsh-home>/sessions`, own allowed `<dsh-home>/sessions/<slug>`.

`DshProfile.derive(home, from)`: reads `<home>/profiles/<from>/package.json` and `cordis.patch.yml`; writes `<home>/profiles/<from>-headless/` with `package.json` (bundles `["@deepseek-ai/dsh-base", "@deepseek-ai/dsh-headless"]`, dependencies = the source's minus `@deepseek-harness-tui/*`, `dsh-tui-beep`, `dsh-deepseek-budget`), `cordis.yml` (`[]`), `cordis.patch.yml` = the source's entries minus those whose `name`/`id` is `tui-beep`, `deepseek-budget`, or an MCP client with `serverName: browser`; then prints the command the user runs to install its packages (`dsh plugin --profile <from>-headless install` — confirm the exact subcommand from `dsh plugin --help` in this step). Refuses to overwrite an existing target dir.

- [ ] **Step 1: Capture the fixture** — decompress the newest skreeep2 session (`~/.dsh/sessions/--Users-viktorskalinins-IdeaProjects-skreeep2--/*/session.v3.jsonl.zstd`) with `zstd -dc` into the scratchpad, keep ≤ 60 lines covering `session`, `step/start`, `tool/call`, `tool/result`, `hook/result` (block), `assistant/message`, `turn/end`, `request/header`; replace any secret-looking value; save as `harness/test/fixtures/dsh/session-sample.jsonl`. Note which events carry token usage.
- [ ] **Step 2: Tests (failing first)** — parse of the fixture (tool call count, kinds, one gate block counted by `Gate.gateBlocks` as `tool-policy`, `hasResult`, final text); slug mapping; `collect` picks the newest session created after start among three (one before start, two after) and handles none; overlay YAML for a gated arm and for `u`; `DshProfile.derive` on a temp `home` with a copy of `~/.dsh/profiles/scala/{package.json,cordis.patch.yml}` drops the three rows and keeps `lsp-stdio`, the scalasemantic MCP client, the hooks bridge and the permission rows; refuses an existing target.
- [ ] **Step 3: Run, expect failure.** - [ ] **Step 4: Implement** (YAML: read/write with snakeyaml-engine `//> using dep org.snakeyaml:snakeyaml-engine:2.9` — `!!js` tags must round-trip: register a constructor that keeps unknown tags as tagged scalars and a representer that writes them back). - [ ] **Step 5: Run, expect pass.**
- [ ] **Step 6: Commit** `harness: DeepSeek Harness (dsh) adapter and headless profile derivation`.

---

### Task 17: Codex CLI, Gemini CLI and generic adapters

**Files:**
- Create: `harness/agent/CodexCli.scala`, `harness/agent/GeminiCli.scala`, `harness/agent/Generic.scala`, `harness/agents/codex.conf`, `harness/agents/gemini.conf`, `harness/agents/generic.conf`, `harness/test/fixtures/codex/*.jsonl`, `harness/test/fixtures/gemini/*.jsonl`
- Test: `harness/test/OtherAgentsTest.test.scala`

**Interfaces:**
- Produces: adapters `codex-cli`, `gemini-cli`, `generic`.

- [ ] **Step 1: Read the installed CLIs' contracts** — `codex exec --help`, `gemini --help` (no model call). Record the headless flags (Codex: `exec --json`, model flag, sandbox / approval flags for unattended full access, cwd; Gemini: `-p`, `--output-format stream-json` or `json`, `--yolo`, model flag), their config / MCP file locations, and session dirs, in `harness/agents/NOTES.md`.
- [ ] **Step 2: Fixtures** — if earlier sessions exist on disk (`~/.codex/sessions`, `~/.gemini/tmp`), copy one short session per CLI into fixtures (strip secrets); otherwise write a minimal fixture from the documented event schema and mark the adapter `unverified live` in `NOTES.md` until Task 20.
- [ ] **Step 3: Tests (failing first)** — each parser maps its fixture to `Transcript` (tool calls with kinds, final text, `hasResult`, tokens; cost `None` for Codex); `generic` with `transcript = none` yields an empty `Transcript` with `hasResult = true` when the process exits 0 (so scoring reports no `no result event`); `command` argv for each.
- [ ] **Step 4: Run, expect failure.** - [ ] **Step 5: Implement.** MCP for Codex / Gemini: write their project-level config with the scala-semantic server when the agent conf lists it. - [ ] **Step 6: Run, expect pass.**
- [ ] **Step 7: Commit** `harness: Codex CLI, Gemini CLI and generic command adapters`.

---

### Task 18: Parity — static tier, arm setup, reports

**Files:**
- Create: `harness/parity/StaticParity.scala`, `harness/parity/ArmParity.scala`, `harness/parity/ReportParity.scala` (main scope, package `harness.parity`; CLI subcommands `parity-static <old-root>…`, `parity-arms`, `parity-reports <old-root>…`)
- Create: `harness/PARITY.md`

**Interfaces:**
- Produces: CLI subcommands above. The reference side calls GA's python functions via `python3 -c` with `sys.path.insert(0, GA)` and `import score` (the reference implementation stays python; the harness itself ships no python file — the snippets are strings in `StaticParity.scala`).

- [ ] **Step 1: Copy run roots** — `rsync -a --exclude 'repo/out*' --exclude 'score-tree' ~/gate-eval{,-control,-feature,-hard,-interp,-chain} <scratchpad>/parity-roots/` (read-only originals). Check the size first with `du -sh`; if above 20 GB, copy only `run.json transcript.jsonl final.diff score.json chain.json *.log` per run plus `base/*/hidden`.
- [ ] **Step 2: `parity-static`** — for every run dir: GA python prints a JSON with `parse_transcript` fields (minus `init`), `gate_blocks`, `env_leaks(arm)`, `first_clean_compile`, `contamination` (with `GA_ROOT`, `GA_SKREEEP2`, `HOME` set as at run time), `tampering`, `build_edits`, `hardcoding`, and the log parsers on each saved `*-score.log` (`weaver_counts`, `hidden_counts`, `suite_passes`, `parse_mill_failures` with the modules from `resolve-score.log`); the new core computes the same; diff field by field. Output: `<n> runs, <m> fields, <k> mismatches` + a list. Expected: 0 mismatches, or each one explained in `PARITY.md` (a GA bug fixed on purpose, with a test).
- [ ] **Step 3: `parity-arms`** — for each arm u, g, t, gf, gs, p: build the run copy setup in a temp dir twice, once through GA (`sh GA/run.sh` cannot be run partially; instead replicate with GA's own `lib.sh` functions via `sh -c '. GA/lib.sh; …'` for engine install and policy-only settings, and GA's `sed` / python lines for settings and the Stop hook) and once through `Run`'s setup phase (`Run.setupOnly`, a function extracted in this step from `Run.apply` with a test); compare `.claude/settings.json`, `.mcp.json`, `.dsh/` trees (excluding `.dsh/.state`) byte for byte.
- [ ] **Step 4: `parity-reports`** — run GA `report.py` and `chain_report.py` and the new reports on the copied roots; text diff must be empty.
- [ ] **Step 5: Run all three; fix mismatches with a failing test first (TDD) in the owning task's test file; re-run until clean.**
- [ ] **Step 6: Write `PARITY.md`** — command lines, counts, every intended difference with its reason and test name.
- [ ] **Step 7: Commit** `harness: parity — static tier, arm setup, reports (results in PARITY.md)`.

---

### Task 19: Parity — build tier

**Files:**
- Modify: `harness/PARITY.md`

- [ ] **Step 1: Pick 12 runs** from the copied roots, covering arms u, g, t, gf, gs, p and outcomes solved, unsolved, false_done, timed_out, a regression, a seeded chain step (pick by reading `score.json`/`run.json`; list the ids in `PARITY.md`). Copy each run dir twice (`A`, `B`) inside a copied run root that has `base/`, `head/`, `head-baseline.json`.
- [ ] **Step 2: Rescore** `A` with `GA_ROOT=<copied root> python3 GA/score.py <A>` and `B` with `harness rescore --set root=<copied root> <B>`, same day, one after the other.
- [ ] **Step 3: Compare** `score.json` of A and B ignoring `wall_seconds`; expected equal. Mismatch → failing test in Task 10's suite, fix, rescore both again.
- [ ] **Step 4: Record in `PARITY.md`, commit** `harness: parity — build tier (12 runs rescored by both scorers)`.

---

### Task 20: Live agent runs (ask the budget first)

**Files:**
- Create: `harness/experiments/live-smoke.conf`
- Modify: `harness/PARITY.md`, `harness/agents/NOTES.md`

- [ ] **Step 1: Ask the user** for the budget and confirm: dsh `u` + `gs`, Codex `u`, Gemini `u`, Claude Code `u`, all on `fr-pan`, rep 1, in a fresh root `~/agent-eval-live`. Wait for the answer.
- [ ] **Step 2: dsh profile** — the user runs `harness dsh-profile --from scala` (it writes `~/.dsh`); then install its packages with the command it prints.
- [ ] **Step 3: `live-smoke.conf`** — includes `gate-ab.conf`, `agents/dsh.conf`, `agents/codex.conf`, `agents/gemini.conf`; root `~/agent-eval-live`; arms `dsh-u`, `dsh-gs` (= `gs` with `agent = dsh`), `codex-u`, `gemini-u`, `claude-u`; models: dsh `deepseek-flash` (any model works; this one is in the user's sessions), Codex and Gemini their CLI defaults unless the user names one.
- [ ] **Step 4: Run** `harness campaign --experiment experiments/live-smoke.conf --set harness.tasks=[fr-pan] --set harness.reps=[1]`.
- [ ] **Step 5: Check by hand** — for dsh: transcript tool calls vs the session log, gate blocks in `dsh-gs` vs `hook/result` block events, `env_leaks` empty, the user's global hooks absent from the session's `request/header`; for each agent: `score.json` has no `error`; record turns, tokens, cost, solved.
- [ ] **Step 6: Fix** any adapter defect with a failing fixture test first; re-run only the affected arm.
- [ ] **Step 7: Record** results in `PARITY.md` (live section) and flip `unverified live` notes; commit `harness: live smoke — dsh, Codex, Gemini, Claude Code on fr-pan`.
