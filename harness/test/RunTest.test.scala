package harness.run

import cats.effect.{IO, Ref}
import munit.CatsEffectSuite
import harness.agent.{Agent, ClaudeCode, RunCtx}
import harness.config.*
import harness.target.{MillScala, Target}

/** GA tests/shell-test.sh:183-370 (run.sh against fakes), case for case where the case is Run's. */
class RunTest extends CatsEffectSuite:
  private val harnessDir: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .find(d => os.exists(d / "experiments" / "gate-ab.conf")).getOrElse(fail("harness dir not found"))
  private val exp0: Experiment = Load.experiment(harnessDir / "experiments" / "gate-ab.conf").fold(fail(_), identity)

  private def git(repo: os.Path, args: String*): String =
    os.proc(Seq("git") ++ args).call(cwd = repo, stderr = os.Pipe).out.text().trim
  private def commitAll(dir: os.Path, msg: String): Unit =
    git(dir, "add", "-A"); git(dir, "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false", "commit", "-qm", msg)
  private def real(p: os.Path): String = p.toNIO.toRealPath().toString

  /** Fake Mill target: logs `<outDir> <args>`, emits a SemanticDB file into the out dir, fails on demand. */
  final class FakeTarget(m: MillScala, log: os.Path, fail: Boolean) extends Target:
    export m.{runBuild as _, *}
    def runBuild(tree: os.Path, args: Seq[String], outDir: String, log0: os.Path, env: Map[String, Option[String]],
                 cap: Option[Int], keepGoing: Boolean): IO[ProcResult] = IO.blocking {
      os.write.append(log, s"$outDir ${args.mkString(" ")} cap=${cap.getOrElse(-1)}\n", createFolders = true)
      os.write.over(log0, "fake mill\n")
      os.write.over(tree / outDir / "m" / "semanticdb" / "A.scala.semanticdb", "", createFolders = true)
      ProcResult(if fail then 1 else 0, timedOut = false)
    }

  /** Fake agent: Claude Code's setup / wipeState / env / settings, a /bin/sh one-liner as the agent process. */
  final class FakeAgent(cc: ClaudeCode, files: os.Path, probes: List[os.Path], sleep: Option[Int]) extends Agent:
    export cc.{command as _, env as _, collect as _, afterSteps as _, *}
    def command(ctx: RunCtx, prompt: String): List[String] =
      val f = files.toString
      val script =
        s"""ls -a . >"$f.ls"; find . -path '*/semanticdb/*.semanticdb' | head -n 1 >"$f.index"; env >"$f.env"
           |printf '%s\\n' "$$1" >"$f.args"; cat | wc -c | tr -d ' ' >"$f.stdin"
           |for p in ${probes.map(p => s"'$p'").mkString(" ")}; do if ls "$$p" >/dev/null 2>&1; then echo "readable $$p"; else echo "denied $$p"; fi; done >"$f.probed"
           |mkdir -p out out-agent; echo junk >out/junk.txt; echo junk >out-agent/junk.txt
           |echo '{"type":"system"}'; echo '{"type":"result","num_turns":1}'
           |echo 'agent wrote this' >>agent-note.txt
           |${sleep.fold("")(s => s"sleep $s")}
           |exit 0""".stripMargin
      List("/bin/sh", "-c", script, "fake", prompt)
    // HOME is always in the parent env: unsetting it proves Run hands the adapter's unsets to the agent process
    def env(ctx: RunCtx): Map[String, Option[String]] = cc.env(ctx) + ("FAKE_AGENT_ENV" -> Some("set")) + ("HOME" -> None)
    // records: the arm's setup steps already ran (contract rewritten), settings.json not yet copied
    override def afterSteps(ctx: RunCtx): IO[Unit] =
      IO.blocking(os.write.over(os.Path(s"$files.after"),
        s"${!os.read(ctx.repo / ".dsh" / "contract").contains("../x")} ${!os.exists(ctx.runDir / "settings.json")}"))
    def collect(ctx: RunCtx, startedAtMillis: Long): IO[Unit] =
      IO.blocking(os.write.over(os.Path(s"$files.collected"), s"${os.exists(ctx.runDir / "transcript.jsonl")} ${!os.isReadable(probes.head)}"))

  /** GA shell-test.sh:184-209: task t1, base tree with .dsh/contract, holdout source, gated snapshot + .sha256. */
  final class Env:
    val tmp: os.Path = os.temp.dir()
    val root: os.Path = tmp / "root"
    val tasks: os.Path = tmp / "tasks"
    val hsrc: os.Path = tmp / "hsrc"
    val cfg: os.Path = tmp / "cfg"
    val millLog: os.Path = tmp / "mill.log"
    val tree: os.Path = root / "base" / "t1" / "tree"
    val runs: os.Path = root / "runs"
    val snap: os.Path = root / "gated-settings.json"
    os.write(tasks / "t1" / "task.env", "TASK_MODULE='m.test'\nHIDDEN_SPEC='x'\nCUT_FILES='x'\nFEATURE='f'\n", createFolders = true)
    os.write(tasks / "t1" / "prompt.txt", "do the task\n")
    os.write(tree / ".dsh" / "contract", "holdoutDir = ../x\n", createFolders = true)
    os.write(tree / ".gitignore", ".claude/\nout/\nout-agent/\n")
    git(tree, "init", "-q"); commitAll(tree, "base")
    os.write(hsrc / "HSpec.scala", "spec\n", createFolders = true)
    git(hsrc, "init", "-q"); commitAll(hsrc, "h")
    def writeSnapshot(text: String): Unit =
      os.write.over(snap, text)
      os.write.over(os.Path(s"$snap.sha256"), Files.sha256(snap) + "\n")
    writeSnapshot(s"""{"denyRead":"$hsrc/bin/check","denyWrite":"$hsrc"}\n""")
    os.makeDir.all(root / "base" / "t1" / "hidden"); os.makeDir.all(root / "head"); os.makeDir.all(runs / "other-u-1")
    val probes: List[os.Path] = List(root / "base" / "t1" / "hidden", root / "head", runs / "other-u-1")
    val exp: Experiment = exp0.copy(
      root = root, tasksDir = tasks, capSeconds = 60, precompileCapSeconds = 77,
      target = exp0.target.copy(holdoutRepo = hsrc, holdoutCommit = "HEAD")
    )
    val printed: Ref[IO, List[String]] = Ref.unsafe(Nil)
    def run(arm: String, rep: Int, tag: String, seed: Option[os.Path] = None, millFail: Boolean = false,
            sleep: Option[Int] = None, e: Experiment = exp): IO[Unit] =
      val cc = ClaudeCode(e.agents("claude-code"), tmp / "home", Some(cfg.toString))
      val target = FakeTarget(MillScala(e.target), millLog, millFail)
      Run.withAdapters(e, "t1", arm, rep, seed, FakeAgent(cc, tmp / tag, probes, sleep), target, s => printed.update(_ :+ s))
    def f(tag: String, ext: String): os.Path = tmp / s"$tag.$ext"
    def json(id: String): ujson.Value = ujson.read(os.read(runs / id / "run.json"))
    def readable: Boolean = probes.forall(os.isReadable(_))

  private def failsWith(io: IO[Unit], msg: String): IO[Unit] =
    io.attempt.map {
      case Left(HarnessError(m, _)) => assertEquals(m, msg)
      case other                 => fail(s"expected HarnessError($msg), got $other")
    }

  private val precompile = exp0.target.precompile.mkString(" ")

  test("run g: layout, settings, contract, holdout, artefacts (GA run g …)"):
    val e = Env()
    e.run("g", 1, "g").flatMap(_ => e.printed.get).map { printed =>
      val rd = e.runs / "t1-g-1"
      val hd = real(rd / "holdout")
      val settings = os.read(rd / "repo" / ".claude" / "settings.json")
      assert(settings.contains(hd) && !settings.contains(e.hsrc.toString), "run g settings rewritten")
      assertEquals(hd.r.findAllMatchIn(settings).size, 2, "run g settings has two rewritten entries")
      assertEquals(os.read(rd / "repo" / ".dsh" / "contract"), s"holdoutDir = $hd\n", "run g contract holdoutDir")
      assert(os.isFile(rd / "holdout" / "HSpec.scala"), "run g holdout exported")
      assertEquals(os.read(rd / "settings.json"), settings, "run g settings copy")
      assert(os.read(rd / "final.diff").contains("agent wrote this"), "run g final.diff")
      assert(os.isFile(rd / "final-tree.tar.gz"), "run g tarball")
      assert(os.size(rd / "transcript.jsonl") > 0, "run g transcript")
      assertEquals(git(rd / "repo", "rev-parse", "-q", "--verify", "refs/tags/base").length, 40, "run g base tag")
      assertEquals(git(rd / "repo", "log", "-1", "--format=%an %s"), "gate-eval arm g")
      val ls = os.read.lines(e.f("g", "ls"))
      assert(!ls.contains("out") && ls.contains("out-agent"), s"run g only out-agent at start: $ls")
      assertEquals(os.read.lines(e.millLog).toList, List(s"out-agent $precompile cap=77"), "run g precompile into out-agent")
      assert(os.read(e.f("g", "index")).contains("semanticdb/A.scala.semanticdb"), "run g SemanticDB index present at agent start")
      val env = os.read.lines(e.f("g", "env"))
      assert(!env.exists(_.startsWith("CLAUDECODE=")), "run g outer Claude Code env not inherited")
      assert(sys.env.contains("HOME") && !env.exists(_.startsWith("HOME=")), "agent env unsets reach the agent process")
      assert(env.contains("FAKE_AGENT_ENV=set"), "agent env applied")
      assertEquals(os.read.lines(e.f("g", "args")).toList, List("do the task"), "agent receives prompt")
      assertEquals(os.read(e.f("g", "stdin")).trim, "0", "agent stdin is empty")
      assertEquals(os.read(e.f("g", "collected")), "true true", "collect runs inside the lock, after the transcript")
      assertEquals(os.read(e.f("g", "after")), "true true", "afterSteps runs after the arm's setup steps, before the settings copy")
      assert(os.read(rd / "repo" / ".mcp.json").contains("scalasemantic-mcp"), "run .mcp.json written")
      assertEquals(os.read(rd / "prompt.txt"), "do the task\n")
      assert(!os.exists(rd / "modes.txt"))
      assert(printed.size == 1 && printed.head.matches("""run t1-g-1: exit 0, \d+ s \(precompile \d+ s\)"""), printed.toString)
    }

  // GA run.sh:92-94, literally. Together with the HOME check in "run g" (a var surely present in the parent is gone in the
  // agent process), this holds whatever the outer env contains.
  test("run g outer Claude Code env not inherited: the adapter unsets every GA var"):
    val ga = List("CLAUDECODE", "CLAUDE_CODE_ENTRYPOINT", "CLAUDE_CODE_SESSION_ID", "CLAUDE_CODE_CHILD_SESSION", "CLAUDE_CODE_MESSAGING_SOCKET",
      "CLAUDE_CODE_MESSAGING_TOKEN", "CLAUDE_CODE_BRIDGE_SESSION_ID", "CLAUDE_CODE_SESSION_ATTENDED", "CLAUDE_CODE_EXECPATH", "CLAUDE_PID",
      "CLAUDE_JOB_DIR", "CLAUDE_EFFORT", "AI_AGENT")
    val e = Env()
    val cc = ClaudeCode(e.exp.agents("claude-code"), e.tmp / "home", Some(e.cfg.toString))
    val ctx = RunCtx(e.exp, e.exp.armSpecs("g"), TaskSpec("t1", "m", "x", None, Nil, "", None, false), "id", e.tmp, e.tmp, None, None)
    val fake = FakeAgent(cc, e.tmp / "x", e.probes, None)
    for a <- List[Agent](cc, fake) do
      assertEquals(ga.map(a.env(ctx).get), ga.map(_ => Some(None)), "adapter env maps every GA var to unset")

  test("run.json: GA fields in GA order, then agent and model"):
    val e = Env()
    e.run("g", 1, "g").map { _ =>
      val j = e.json("t1-g-1").obj
      assertEquals(j.keys.toList, List("run_id", "task", "arm", "rep", "start_epoch", "end_epoch", "exit_code", "timed_out",
        "precompile_seconds", "precompile_ok", "seed_sha256", "gated_settings_sha256", "agent", "model"))
      assertEquals(j("run_id").str, "t1-g-1"); assertEquals(j("task").str, "t1"); assertEquals(j("arm").str, "g")
      assertEquals(j("rep").num, 1.0); assertEquals(j("exit_code").num, 0.0)
      assertEquals(j("timed_out").bool, false, "run g not timed out")
      assert(j("precompile_seconds").num >= 0, "run g precompile_seconds recorded")
      assertEquals(j("precompile_ok").bool, true); assertEquals(j("seed_sha256"), ujson.Null)
      assertEquals(j("gated_settings_sha256").str, Files.sha256(e.snap), "run g snapshot hash recorded")
      assertEquals(j("agent").str, "claude-code"); assertEquals(j("model").str, "sonnet")
      assert(j("end_epoch").num >= j("start_epoch").num)
      val raw = os.read(e.runs / "t1-g-1" / "run.json")
      assert(raw.startsWith("""{"run_id":"t1-g-1","task":"t1","arm":"g","rep":1,"start_epoch":""") && raw.endsWith("}\n"), raw)
    }

  test("isolation: base, head, other runs unreadable during the run, readable after (g and u)"):
    val e = Env()
    for
      _ <- e.run("g", 1, "g")
      _ <- e.run("u", 1, "u")
    yield
      for a <- List("g", "u") do
        assertEquals(os.read.lines(e.f(a, "probed")).count(_.startsWith("denied")), 3, s"run $a isolation denied during run")
      assert(e.readable, "isolation restored after run")

  test("run removes its own Claude project dir only (M2)"):
    val e = Env()
    os.makeDir.all(e.runs / "t1-g-1" / "repo")
    val slug = ClaudeCode.claudeSlug(real(e.runs / "t1-g-1" / "repo"))
    os.remove.all(e.runs / "t1-g-1")
    os.write(e.cfg / "projects" / slug / "memory" / "MEMORY.md", "old", createFolders = true)
    os.makeDir.all(e.cfg / "projects" / "-other-project")
    e.run("g", 1, "g").map { _ =>
      assert(!os.exists(e.cfg / "projects" / slug)); assert(os.isDir(e.cfg / "projects" / "-other-project"))
    }

  test("run u: permissions-only settings, no holdout, out dir, no snapshot hash"):
    val e = Env()
    e.run("u", 1, "u").map { _ =>
      val rd = e.runs / "t1-u-1"
      assertEquals(os.read(rd / "repo" / ".claude" / "settings.json"), "{\"permissions\":{}}\n", "run u settings")
      assert(!os.exists(rd / "holdout"), "run u no holdout")
      val ls = os.read.lines(e.f("u", "ls"))
      assert(ls.contains("out") && !ls.contains("out-agent"), "run u only out at start")
      assertEquals(os.read.lines(e.millLog).toList, List(s"out $precompile cap=77"), "run u precompile into out")
      assertEquals(e.json("t1-u-1")("gated_settings_sha256"), ujson.Null, "run u no snapshot hash")
    }

  test("run g and u: out dirs excluded from artefacts"):
    val e = Env()
    (e.run("g", 1, "g") *> e.run("u", 1, "u")).map { _ =>
      for a <- List("g", "u") do
        val rd = e.runs / s"t1-$a-1"
        val diff = os.read(rd / "final.diff")
        val tar = os.proc("tar", "-tzf", rd / "final-tree.tar.gz").call().out.text()
        assert(diff.nonEmpty && !diff.contains("junk") && !tar.contains("repo/out"), s"run $a out dirs excluded from artefacts")
    }

  test("seeded run: previous step's work, fresh history, base tag, seed hash, u settings"):
    val e = Env()
    for
      _ <- e.run("g", 1, "g")
      seed = e.runs / "t1-g-1" / "final-tree.tar.gz"
      _ <- e.run("u", 2, "s", seed = Some(seed))
    yield
      val rd = e.runs / "t1-u-2"
      assertEquals(os.read.lines(rd / "repo" / "agent-note.txt").count(_ == "agent wrote this"), 2, "seeded run keeps the previous step's work")
      assertEquals(os.read.lines(rd / "final.diff").count(_ == "+agent wrote this"), 1, "seeded run diff holds only this step's work")
      assertEquals(git(rd / "repo", "rev-list", "--count", "HEAD"), "2", "seeded run history is fresh (base + arm)")
      assertEquals(git(rd / "repo", "rev-parse", "-q", "--verify", "refs/tags/base").length, 40, "seeded run base tag")
      assertEquals(os.read(rd / "repo" / ".claude" / "settings.json"), "{\"permissions\":{}}\n", "seeded u run settings rewritten")
      assertEquals(e.json("t1-u-2")("precompile_ok").bool, true, "seeded good build recorded")
      assertEquals(e.json("t1-u-2")("seed_sha256").str, Files.sha256(seed), "seeded run records its seed hash")

  test("missing seed tree fails"):
    val e = Env()
    failsWith(e.run("u", 3, "s2", seed = Some(e.tmp / "nope.tar.gz")), s"seed tree ${e.tmp / "nope.tar.gz"} missing")

  test("seeded run with a broken build: agent still runs, precompile_ok false"):
    val e = Env()
    for
      _ <- e.run("g", 1, "g")
      _ <- e.run("u", 4, "s3", seed = Some(e.runs / "t1-g-1" / "final-tree.tar.gz"), millFail = true)
    yield
      assertEquals(e.json("t1-u-4")("precompile_ok").bool, false, "seeded broken build recorded")
      assert(os.exists(e.f("s3", "ls")))

  test("seeded run drops gate state and .claude; .dsh from the base tree"):
    val e = Env()
    val sd = e.tmp / "seedx"
    os.write(sd / "repo" / ".dsh" / ".state" / "verdict", "v\n", createFolders = true)
    os.write(sd / "repo" / ".claude" / "junk.json", "{}\n", createFolders = true)
    os.write(sd / "repo" / ".dsh" / "contract", "holdoutDir = changed-by-agent\n")
    os.write(sd / "repo" / "agent-note.txt", "old\n")
    os.proc("tar", "-czf", e.tmp / "seedx.tar.gz", "-C", sd, "repo").call()
    e.run("u", 5, "s4", seed = Some(e.tmp / "seedx.tar.gz")).map { _ =>
      val rd = e.runs / "t1-u-5"
      assert(!os.exists(rd / "repo" / ".dsh" / ".state") && !os.exists(rd / "repo" / ".claude" / "junk.json"), "seeded run drops gate state and .claude")
      assertEquals(os.read(rd / "repo" / ".dsh" / "contract"), "holdoutDir = ../x\n", "seeded run .dsh from the base tree")
    }

  test("seed tree without repo/ fails"):
    val e = Env()
    os.write(e.tmp / "norepo" / "x", "x", createFolders = true)
    os.proc("tar", "-czf", e.tmp / "norepo.tar.gz", "-C", e.tmp / "norepo", "x").call()
    failsWith(e.run("u", 6, "s5", seed = Some(e.tmp / "norepo.tar.gz")), s"seed tree ${e.tmp / "norepo.tar.gz"} has no repo/")

  test("run timeout: timed_out true, exit 124, artefacts kept, isolation restored"):
    val e = Env()
    e.run("u", 2, "t", sleep = Some(20), e = e.exp.copy(capSeconds = 1)).flatMap(_ => e.printed.get).map { printed =>
      val j = e.json("t1-u-2")
      assertEquals(j("timed_out").bool, true, "run timeout recorded"); assertEquals(j("exit_code").num, 124.0)
      assert(os.isFile(e.runs / "t1-u-2" / "final.diff") && os.isFile(e.runs / "t1-u-2" / "final-tree.tar.gz"), "run timeout keeps artefacts")
      assert(e.readable, "isolation restored after timeout")
      assert(printed.head.startsWith("run t1-u-2: exit 124, "), printed.toString)
    }

  test("run t: no Stop hook, rest kept, holdout rewritten, run.json arm and hash"):
    val e = Env()
    e.writeSnapshot(
      s"""{"sandbox":{"denyRead":"${e.hsrc}/bin/check","denyWrite":"${e.hsrc}"},"env":{"MILL_OUTPUT_DIR":"out-agent"},
         | "hooks":{"PreToolUse":[{"matcher":"Bash","hooks":[{"type":"command","command":"tool-policy"}]}],
         |          "Stop":[{"hooks":[{"type":"command","command":"turn-end"}]}]}}
         |""".stripMargin)
    e.run("t", 1, "tt").map { _ =>
      val rd = e.runs / "t1-t-1"
      val text = os.read(rd / "repo" / ".claude" / "settings.json")
      val s = ujson.read(text)
      assert(!s("hooks").obj.contains("Stop") && s("hooks")("PreToolUse").arr.nonEmpty && s("env")("MILL_OUTPUT_DIR").str == "out-agent",
        "run t settings: no Stop, rest kept")
      val hd = real(rd / "holdout")
      assert(text.contains(hd) && !text.contains(e.hsrc.toString), "run t holdout paths rewritten")
      assertEquals(os.read(rd / "repo" / ".dsh" / "contract"), s"holdoutDir = $hd\n", "run t contract holdoutDir")
      assertEquals(os.read.lines(e.millLog).toList, List(s"out-agent $precompile cap=77"), "run t precompile into out-agent")
      assertEquals(e.json("t1-t-1")("arm").str, "t")
      assertEquals(e.json("t1-t-1")("gated_settings_sha256").str, os.read(os.Path(s"${e.snap}.sha256")).trim, "run t run.json arm and snapshot hash")
    }

  test("run t without Stop hook dies, agent not started"):
    val e = Env()
    failsWith(e.run("t", 2, "t2"), "arm t: cannot remove the Stop hook").map { _ =>
      assert(!os.exists(e.f("t2", "ls")), "run t without Stop hook: agent not started")
    }

  test("run aborts when holdoutDir rewrite fails"):
    val e = Env()
    os.write.over(e.tree / ".dsh" / "contract", "other = 1\n"); commitAll(e.tree, "nohd")
    failsWith(e.run("g", 2, "bad"), "contract rewrite failed (no holdoutDir line?)").map(_ => assert(!os.exists(e.f("bad", "ls"))))

  test("run aborts when precompile fails; isolation stays unlocked"):
    val e = Env()
    failsWith(e.run("u", 3, "pc", millFail = true), s"precompile failed (see ${e.runs / "t1-u-3"}/precompile.log)").map { _ =>
      assert(!os.exists(e.f("pc", "ls")), "run aborts when precompile fails")
      assert(e.readable, "precompile failure leaves isolation unlocked")
    }

  test("run g dies without settings snapshot"):
    val e = Env()
    os.remove(e.snap)
    failsWith(e.run("g", 3, "ns"), s"no gated settings snapshot ${e.snap} (campaign.sh writes it)").map(_ => assert(!os.exists(e.f("ns", "ls"))))

  test("run g dies when snapshot hash differs"):
    val e = Env()
    os.write.over(os.Path(s"${e.snap}.sha256"), "0000\n")
    failsWith(e.run("g", 3, "ns"), s"${e.snap} does not match ${e.snap}.sha256").map(_ => assert(!os.exists(e.f("ns", "ls"))))

  test("unprepared task and bad run id die before anything is written"):
    val e = Env()
    os.remove.all(e.tree / ".git")
    failsWith(e.run("g", 1, "np"), "task t1 not prepared (run prepare.sh t1)") *>
      failsWith(e.run("x", 1, "np"), "arm must be one of g, gf, gs, p, t, u, got x").map(_ => assert(!os.exists(e.runs / "t1-g-1")))

  // Task 18: the setup phase alone (arm-setup parity drives it): run copy, agent config, arm steps, settings artefact,
  // `arm <name>` commit and base tag; no precompile, no agent, no run.json
  test("setupOnly: run copy set up and tagged, nothing built or started"):
    val e = Env()
    val cc = ClaudeCode(e.exp.agents("claude-code"), e.tmp / "home", Some(e.cfg.toString))
    Run.setupOnly(e.exp, "t1", "g", 1, None, FakeAgent(cc, e.tmp / "so", e.probes, None)).map { s =>
      val rd = e.runs / "t1-g-1"
      assertEquals(s.ctx.runDir, rd)
      assertEquals(s.ctx.holdout, Some(os.Path(real(rd / "holdout"))))
      assertEquals(s.seedSha, ujson.Null)
      assertEquals(s.settingsSha, ujson.Str(Files.sha256(e.snap)))
      val hd = real(rd / "holdout")
      assertEquals(os.read(rd / "repo" / ".dsh" / "contract"), s"holdoutDir = $hd\n")
      assertEquals(os.read(rd / "settings.json"), os.read(rd / "repo" / ".claude" / "settings.json"))
      assert(os.read(rd / "repo" / ".mcp.json").contains("scalasemantic-mcp"))
      assertEquals(git(rd / "repo", "log", "-1", "--format=%an %s"), "gate-eval arm g")
      assertEquals(git(rd / "repo", "rev-parse", "base"), git(rd / "repo", "rev-parse", "HEAD"))
      assert(!os.exists(e.millLog), "no precompile")
      assert(!os.exists(e.f("so", "ls")), "agent not started")
      assert(!os.exists(rd / "run.json") && !os.exists(rd / "precompile.log") && !os.exists(rd / "final.diff"))
    }

  // Final review I1: an agent without mcp-servers writes no project config; setup must not copy a missing artefact
  test("setupOnly: codex and gemini without mcp-servers get through setup, no settings.json"):
    val e = Env()
    val codex = harness.agent.CodexCli(AgentSpec("codex", "codex-cli", ujson.Obj("adapter" -> "codex-cli")))
    val gemini = harness.agent.GeminiCli(AgentSpec("gemini", "gemini-cli",
      ujson.Obj("adapter" -> "gemini-cli", "gemini-home" -> (e.tmp / "gemini-home").toString)))
    Run.setupOnly(e.exp, "t1", "u", 1, None, codex).map { s =>
      assertEquals(git(s.ctx.repo, "rev-parse", "base"), git(s.ctx.repo, "rev-parse", "HEAD"))
      assert(!os.exists(s.ctx.runDir / "settings.json"))
    } *> Run.setupOnly(e.exp, "t1", "u", 2, None, gemini).map { s =>
      assertEquals(git(s.ctx.repo, "rev-parse", "base"), git(s.ctx.repo, "rev-parse", "HEAD"))
      assert(!os.exists(s.ctx.runDir / "settings.json"))
    }

  // PARITY.md (Task 9 ruling): GA ignores a failing `arm <name>` commit or base tag and goes on with no base tag (an
  // empty final.diff); the harness stops the run
  test("arm commit and base tag failures are fatal (GA continues)"):
    val tagged = Env()
    git(tagged.tree, "tag", "base")
    val hooked = Env()
    os.write(hooked.tree / ".git" / "hooks" / "pre-commit", "#!/bin/sh\necho no commit >&2\nexit 1\n", perms = "rwxr-xr-x")
    def setup(e: Env, arm: String) =
      Run.setupOnly(e.exp, "t1", arm, 1, None, ClaudeCode(e.exp.agents("claude-code"), e.tmp / "home", Some(e.cfg.toString))).void
    failsWith(setup(tagged, "u"), "arm u: tag base failed: fatal: tag 'base' already exists") *>
      failsWith(setup(hooked, "g"), "arm g: commit failed: no commit")
