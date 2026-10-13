package harness.run

import cats.effect.IO
import munit.CatsEffectSuite
import harness.config.*

class StepsTest extends CatsEffectSuite:
  private val harnessDir: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .find(d => os.exists(d / "experiments" / "gate-ab.conf")).getOrElse(fail("harness dir not found"))
  private val fx = harnessDir / "test" / "fixtures" / "settings"
  private val exp: Experiment = Load.experiment(harnessDir / "experiments" / "gate-ab.conf").fold(fail(_), identity)

  private def git(repo: os.Path, args: String*): String =
    os.proc(Seq("git") ++ args).call(cwd = repo, stderr = os.Pipe).out.text().trim

  /** A temp git repo with one commit. */
  private def repo(): os.Path =
    val r = os.temp.dir()
    os.write(r / "x", "x\n")
    git(r, "init", "-q"); git(r, "add", "-A")
    git(r, "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false", "commit", "-qm", "r")
    r

  private val vars0 = Map("model" -> "sonnet")
  private def run(steps: List[SetupStep], r: os.Path, arm: String = "x", vars: Map[String, String] = vars0): IO[Unit] =
    Steps(steps, vars + ("repo" -> r.toString), r, arm)
  private def failsWith(io: IO[Unit], msg: String): IO[Unit] =
    io.attempt.map {
      case Left(HarnessError(m, _)) => assertEquals(m, msg)
      case other                 => fail(s"expected HarnessError($msg), got $other")
    }
  private def jsonSteps(arm: String): List[SetupStep] = exp.armSpecs(arm).setup.collect { case j: SetupStep.JsonEdit => j }

  // ---- one test per step kind ----
  test("run: argv expanded, cwd is the repo"):
    val r = repo()
    run(List(SetupStep.Run(List("/bin/sh", "-c", "echo {model} > m.txt; pwd -P > cwd.txt"))), r).map { _ =>
      assertEquals(os.read(r / "m.txt"), "sonnet\n")
      assertEquals(os.read(r / "cwd.txt").trim, r.toNIO.toRealPath().toString)
    }

  test("run: a failing command raises"):
    val r = repo()
    failsWith(run(List(SetupStep.Run(List("/bin/sh", "-c", "exit 3"))), r, "gs"), "arm gs: /bin/sh -c exit 3 failed (rc=3)")

  test("an undefined placeholder raises"):
    val r = repo()
    failsWith(run(List(SetupStep.Write("a.txt", "{holdout}")), r), "unknown or undefined variable {holdout} in '{holdout}'")

  test("write: creates folders, text verbatim"):
    val r = repo()
    run(List(SetupStep.Write(".dsh/policy/turn-end.tsv", "# gate-eval arm\nmode\tsafety\n")), r).map { _ =>
      assertEquals(os.read(r / ".dsh" / "policy" / "turn-end.tsv"), "# gate-eval arm\nmode\tsafety\n")
    }

  test("append-line: creates a missing file, skips when a line starts with the key prefix"):
    val r = repo()
    val line = ".dsh/policy/turn-end.tsv\tthe turn-end mode is the human's"
    val step = SetupStep.AppendLine(".dsh/frozen-paths", line)
    for
      _ <- run(List(step), r)
      _ = assertEquals(os.read(r / ".dsh" / "frozen-paths"), line + "\n")
      _ = os.write.over(r / ".dsh" / "frozen-paths", "a\tb\n.dsh/policy/turn-end.tsv\tsomething else\n")
      _ <- run(List(step), r)
      _ = assertEquals(os.read(r / ".dsh" / "frozen-paths"), "a\tb\n.dsh/policy/turn-end.tsv\tsomething else\n")
      _ = os.write.over(r / ".dsh" / "frozen-paths", "a\tb\n")
      _ <- run(List(step), r)
    yield assertEquals(os.read(r / ".dsh" / "frozen-paths"), "a\tb\n" + line + "\n")

  test("replace-line: replaces the key line, keeps the rest"):
    val r = repo()
    os.makeDir.all(r / ".dsh"); os.write(r / ".dsh" / "contract", "a = 1\nholdoutDir   = ../x\nb = 2\n")
    run(List(SetupStep.ReplaceLine(".dsh/contract", "holdoutDir", "holdoutDir = {holdout}")), r, vars = vars0 + ("holdout" -> "/h")).map { _ =>
      assertEquals(os.read(r / ".dsh" / "contract"), "a = 1\nholdoutDir = /h\nb = 2\n")
    }

  test("replace-line: GA text for .dsh/contract without the line, generic text otherwise"):
    val r = repo()
    os.makeDir.all(r / ".dsh"); os.write(r / ".dsh" / "contract", "other = 1\n"); os.write(r / "f.conf", "x\n")
    failsWith(run(List(SetupStep.ReplaceLine(".dsh/contract", "holdoutDir", "holdoutDir = /h")), r), "contract rewrite failed (no holdoutDir line?)") *>
      failsWith(run(List(SetupStep.ReplaceLine("f.conf", "key", "key = v")), r), "replace-line: no line starting with key in f.conf") *>
      failsWith(run(List(SetupStep.ReplaceLine("missing.conf", "key", "key = v")), r), "replace-line: no line starting with key in missing.conf")

  test("commit: gate-eval identity, only the named paths, allow-empty"):
    val r = repo()
    os.makeDir.all(r / ".dsh"); os.write(r / ".dsh" / "a", "1"); os.write(r / "other", "o")
    val c = SetupStep.Commit("engine, turn-end mode safety", List(".dsh"), allowEmpty = true)
    run(List(c, c), r).map { _ =>
      assertEquals(git(r, "log", "-1", "--format=%an <%ae> %s"), "gate-eval <gate-eval@local> engine, turn-end mode safety")
      assertEquals(git(r, "rev-list", "--count", "HEAD"), "3")
      assertEquals(git(r, "status", "--porcelain"), "?? other")
    }

  test("commit: without allow-empty an unchanged tree raises"):
    val r = repo()
    failsWith(run(List(SetupStep.Commit("m", List("x"), allowEmpty = false)), r, "gs"), s"arm gs: commit m in $r failed")

  // GA shell-test.sh:31-37 "engine install twice": seeded gs re-installs an identical engine; the commit must not fail
  test("engine install twice"):
    val r = repo()
    val tpl = os.temp.dir()
    os.write(tpl / "kit" / "harness" / "install.sh",
      "#!/bin/sh\nmkdir -p \"$1/.dsh/policy\"; : >\"$1/.dsh/frozen-paths\"; echo engine >\"$1/.dsh/engine.txt\"\n", createFolders = true)
    val steps = exp.armSpecs("gs").setup.filterNot(_.isInstanceOf[SetupStep.ReplaceLine])
    val vars = vars0 + ("template" -> tpl.toString)
    (run(steps, r, "gs", vars) *> run(steps, r, "gs", vars)).map { _ =>
      assertEquals(os.read(r / ".dsh" / "policy" / "turn-end.tsv"), "# gate-eval arm\nmode\tsafety\n")
      assertEquals(os.read(r / ".dsh" / "frozen-paths"), ".dsh/policy/turn-end.tsv\tthe turn-end mode is the human's\n")
      assertEquals(git(r, "log", "-1", "--format=%s"), "engine, turn-end mode safety")
    }

  // ---- GA lib.sh:26,30-31 failure texts for the engine install (optional `fail` text on run / commit steps) ----
  private def engineSteps(arm: String) = exp.armSpecs(arm).setup.filterNot(_.isInstanceOf[SetupStep.ReplaceLine])
  private def failingTemplate(): os.Path =
    val tpl = os.temp.dir()
    os.write(tpl / "kit" / "harness" / "install.sh", "#!/bin/sh\nexit 1\n", createFolders = true); tpl

  test("engine install failure raises GA's text"):
    val r = repo()
    failsWith(run(engineSteps("gs"), r, "gs", vars0 + ("template" -> failingTemplate().toString)), s"engine install into $r failed") *>
      failsWith(run(engineSteps("gf"), r, "gf", vars0 + ("template" -> failingTemplate().toString)), s"engine install into $r failed")

  test("engine commit failure raises GA's text"):
    val r = repo() // no .dsh: `git add .dsh` fails
    val commit = exp.armSpecs("gs").setup.collect { case c: SetupStep.Commit => c }
    failsWith(run(commit, r, "gs"), s"commit engine in $r failed")

  test("run: a command that cannot be launched raises the step's text, generic text without one"):
    val r = repo()
    failsWith(run(List(SetupStep.Run(List("/nonexistent/install"), Some("engine install into {repo} failed"))), r, "gs"), s"engine install into $r failed") *>
      run(List(SetupStep.Run(List("/nonexistent/install"))), r, "gs").attempt.map {
        case Left(HarnessError(m, _)) => assert(m.startsWith("arm gs: /nonexistent/install failed"), m)
        case other                 => fail(s"expected HarnessError, got $other")
      }

  // deferred minor (Task 9): a run step's command was not killed on fiber cancel
  test("run: cancel kills the step's command and its process group"):
    val r = repo()
    val (pidFile, gcFile) = (r / "pid", r / "gc.pid")
    def ready(p: os.Path) = os.exists(p) && os.read(p).trim.nonEmpty
    def pids = List(pidFile, gcFile).filter(ready).map(os.read(_).trim.toLong)
    def alive(p: Long) = ProcessHandle.of(p).map(_.isAlive).orElse(false)
    def poll(c: => Boolean, n: Int): IO[Unit] = IO(c).flatMap(ok => if ok || n <= 0 then IO.unit else IO.sleep(scala.concurrent.duration.DurationInt(100).millis) *> poll(c, n - 1))
    val step = SetupStep.Run(List("/bin/sh", "-c", s"/bin/sh -c 'sleep 300 & echo $$! > $gcFile'; echo $$$$ > $pidFile; exec sleep 100"))
    (for
      fib <- run(List(step), r).start
      _ <- poll(ready(pidFile) && ready(gcFile), 100)
      ps = pids
      _ <- fib.cancel
      _ <- poll(ps.forall(!alive(_)), 50)
    yield ps.foreach(p => assert(!alive(p), s"$p survived cancel"))).guarantee(IO(pids.foreach(p => ProcessHandle.of(p).ifPresent(_.destroyForcibly()))))

  test("arm p filter looks only at hooks[].command (a matcher holding the text does not count)"):
    val r = repo()
    val p = settingsCopy(r,
      """{"hooks":{"PreToolUse":[{"matcher":"gate.sh\" tool-policy","hooks":[{"type":"command","command":"other"}]},
        |{"matcher":"Bash","hooks":[{"type":"command","command":"sh gate.sh\" tool-policy"}]}]}}""".stripMargin)
    run(jsonSteps("p"), r, "p").map { _ =>
      val pre = ujson.read(os.read(p))("hooks")("PreToolUse").arr
      assertEquals(pre.map(_("matcher").str).toList, List("Bash"))
    } >> {
      settingsCopy(r, """{"hooks":{"PreToolUse":[{"matcher":"gate.sh\" tool-policy","hooks":[{"type":"command","command":"x"}]}]}}""")
      failsWith(run(jsonSteps("p"), r, "p"), s"arm p: no tool-policy PreToolUse entry in $p")
    }

  // ---- json-edit, byte-identical to GA python (fixtures produced by GA lib.sh:54-63 and run.sh:44-47) ----
  private def settingsCopy(r: os.Path, text: String): os.Path =
    val p = r / ".claude" / "settings.json"
    os.write.over(p, text, createFolders = true); p

  test("json-edit arm p equals GA ga_policy_only_settings"):
    val r = repo(); val p = settingsCopy(r, os.read(fx / "gated-settings.json"))
    run(jsonSteps("p"), r, "p").map(_ => assertEquals(os.read(p), os.read(fx / "arm-p-settings.json")))

  test("json-edit arm t equals GA Stop removal"):
    val r = repo(); val p = settingsCopy(r, os.read(fx / "gated-settings.json"))
    run(jsonSteps("t"), r, "t").map(_ => assertEquals(os.read(p), os.read(fx / "arm-t-settings.json")))

  // GA shell-test.sh:38-51
  test("policy-only settings keep only tool-policy"):
    val r = repo()
    val p = settingsCopy(r,
      """{"env":{"MILL_OUTPUT_DIR":"out-agent"},"sandbox":{"enabled":true},"permissions":{"deny":["Read(./holdout/**)"]},
        | "hooks":{"SessionStart":[{"hooks":[{"type":"command","command":"sh \"$CLAUDE_PROJECT_DIR/.dsh/engine/gate.sh\" baseline"}]}],
        |  "PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":"sh \"$CLAUDE_PROJECT_DIR/.dsh/engine/gate.sh\" tool-policy","timeout":30}]},
        |   {"matcher":"Read|Bash","hooks":[{"type":"command","command":"sh \"$CLAUDE_PROJECT_DIR/.dsh/engine/gate.sh\" semantic-guard"}]}],
        |  "Stop":[{"hooks":[{"type":"command","command":"sh \"$CLAUDE_PROJECT_DIR/.dsh/engine/gate.sh\" turn-end"}]}]}}""".stripMargin)
    run(jsonSteps("p"), r, "p").map { _ =>
      val s = ujson.read(os.read(p)); val h = s("hooks").obj
      assertEquals(h.keys.toList, List("PreToolUse"))
      assertEquals(h("PreToolUse").arr.size, 1)
      assert(h("PreToolUse")(0)("hooks")(0)("command").str.contains("tool-policy"))
      assert(!s.obj.contains("sandbox"))
      assertEquals(s("env")("MILL_OUTPUT_DIR").str, "out-agent")
      assertEquals(s("permissions")("deny")(0).str, "Read(./holdout/**)")
    }

  test("policy-only settings refuse a snapshot without tool-policy"):
    val r = repo(); val p = settingsCopy(r, """{"hooks":{"Stop":[]}}""")
    failsWith(run(jsonSteps("p"), r, "p"), s"arm p: no tool-policy PreToolUse entry in $p")

  test("arm t: a snapshot without a Stop hook raises GA's text"):
    val r = repo(); settingsCopy(r, """{"hooks":{"PreToolUse":[]}}""")
    failsWith(run(jsonSteps("t"), r, "t"), "arm t: cannot remove the Stop hook") >>
      { settingsCopy(r, "not json"); failsWith(run(jsonSteps("t"), r, "t"), "arm t: cannot remove the Stop hook") }

  // ---- run ids (GA shell-test.sh:12-29, ga_run_id) ----
  private val arms = exp.armSpecs.keySet
  test("run id"):
    assertEquals(Run.runId("noise", "g", 1, arms), Right("noise-g-1"))
  test("run id rejects arm x"):
    assertEquals(Run.runId("noise", "x", 1, arms), Left("arm must be one of g, gf, gs, p, t, u, got x"))
  test("run id rejects rep 0"):
    assertEquals(Run.runId("noise", "g", 0, arms), Left("rep must be 1..9, got 0"))
    assertEquals(Run.runId("noise", "g", 10, arms), Left("rep must be 1..9, got 10"))
  test("run id arm t"):
    assertEquals(Run.runId("noise", "t", 1, arms), Right("noise-t-1"))
  test("run id arm gf"):
    assertEquals(Run.runId("fr-pan", "gf", 1, arms), Right("fr-pan-gf-1"))
  test("run id arm gs"):
    assertEquals(Run.runId("fr-pan", "gs", 2, arms), Right("fr-pan-gs-2"))
  test("run id arm p"):
    assertEquals(Run.runId("fr-pan", "p", 1, arms), Right("fr-pan-p-1"))

  test("HarnessError renders with GA's ga_die prefix"):
    assertEquals(HarnessError("x y").line, "gate-ab: x y")
