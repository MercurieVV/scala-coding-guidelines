package harness.cli

import cats.effect.{Deferred, ExitCode, IO, Ref}
import harness.config.Experiment
import harness.core.Py
import harness.run.HarnessError
import munit.CatsEffectSuite
import scala.collection.mutable.ListBuffer

/** Main's parsing and dispatch, in-process: a fake [[Commands]] records calls, the terminal is two buffers. */
class MainTest extends CatsEffectSuite:
  private val harnessDir: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .find(d => os.exists(d / "experiments" / "gate-ab.conf")).getOrElse(fail("harness dir not found"))
  private val gateAb = (harnessDir / "experiments" / "gate-ab.conf").toString

  class Fake(val exp: Experiment) extends Commands:
    val calls = ListBuffer.empty[String]
    var prepareResult = true
    var failWith: Option[Throwable] = None
    var block: Option[IO[Unit]] = None
    private def rec(s: String): IO[Unit] =
      IO(calls += s) *> block.getOrElse(IO.unit) *> failWith.fold(IO.unit)(IO.raiseError)
    def run(task: String, arm: String, rep: Int, seed: Option[os.Path]): IO[Unit] =
      rec(s"run $task $arm $rep ${seed.fold("-")(_.toString)}")
    def score(runDir: os.Path): IO[Unit] = rec(s"score $runDir")
    def prepare(task: String): IO[Boolean] = rec(s"prepare $task").as(prepareResult)
    def baseline: IO[Unit] = rec("baseline")
    def prepareChain(steps: List[String]): IO[Boolean] = rec(s"prepare-chain ${steps.mkString(",")}").as(prepareResult)
    def chainCheck(runDir: os.Path, earlier: List[os.Path]): IO[Unit] =
      rec(s"chain-check $runDir ${earlier.mkString(",")}")
    def warmHead: IO[Unit] = rec("warm-head")
    def baselineCli: IO[Int] = rec("baseline-cli").as(0)
    def campaign(smoke: Boolean): IO[Unit] = rec(s"campaign smoke=$smoke")
    def report: IO[Unit] = rec("report")
    def chainReport: IO[String] = rec("chain-report").as("# Chained feature work\n\nlast line")
    def unlock: IO[Unit] = rec("unlock")

  final case class Res(code: Int, out: String, err: String, fakes: List[Fake])

  private def cli(args: String*)(tweak: Fake => Unit = _ => ()): IO[Res] =
    for
      out <- Ref[IO].of("")
      err <- Ref[IO].of("")
      fakes = ListBuffer.empty[Fake]
      term = Term(s => out.update(_ + s), s => err.update(_ + s + "\n"))
      code <- Cli.run(args.toList, e => { val f = Fake(e); tweak(f); fakes += f; f }, term)
      o <- out.get
      e <- err.get
    yield Res(code.code, o, e, fakes.toList)

  private def calls(r: Res): List[String] = r.fakes.flatMap(_.calls)

  test("validate prints the six arms and exits 0"):
    cli("validate", "--experiment", gateAb)().map { r =>
      assertEquals(r.code, 0, r.err)
      assert(r.out.linesIterator.contains("arms: g, gf, gs, p, t, u"), r.out)
      List("g", "gf", "gs", "p", "t", "u").foreach(a => assert(r.out.contains(s"arm $a: agent claude-code"), r.out))
      assert(r.out.contains("tasks: noise, mixer-modes, mcu"), r.out)
      assert(r.out.contains("agent claude-code: adapter claude-code"), r.out)
    }

  test("validate applies --set overrides"):
    cli("validate", "--experiment", gateAb, "--set", "root=/tmp/elsewhere", "--set", "reps=[2]")().map { r =>
      assertEquals(r.code, 0, r.err)
      assert(r.out.linesIterator.contains("root: /tmp/elsewhere"), r.out)
      assert(r.out.linesIterator.contains("reps: 2"), r.out)
    }

  test("an unknown subcommand exits 2 with the usage on stderr"):
    cli("frobnicate", "--experiment", gateAb)().map { r =>
      assertEquals(r.code, 2)
      assertEquals(r.out, "")
      assert(r.err.contains("Usage"), r.err)
    }

  test("a missing --experiment exits 2"):
    cli("validate")().map(r => assertEquals(r.code, 2))

  test("a missing --experiment file exits 1 with harness: experiment file <path> not found"):
    val f = os.temp.dir() / "nope.conf"
    cli("validate", "--experiment", f.toString)().map { r =>
      assertEquals(r.code, 1)
      assertEquals(r.err, s"harness: experiment file $f not found\n")
    }

  test("a --set without '=' exits 2"):
    cli("validate", "--experiment", gateAb, "--set", "root")().map { r =>
      assertEquals(r.code, 2)
      assert(r.err.contains("path=value"), r.err)
    }

  test("--help exits 0 and lists the subcommands on stdout"):
    cli("--help")().map { r =>
      assertEquals(r.code, 0)
      List("prepare", "warm-head", "baseline", "run", "score", "campaign", "chain-check", "prepare-chain", "report",
        "chain-report", "rescore", "validate", "unlock").foreach(c => assert(r.out.contains(c), c))
    }

  test("an experiment that does not load exits 1 with gate-ab: <msg>"):
    cli("validate", "--experiment", gateAb, "--set", "harness.arms=[g, zz]")().map { r =>
      assertEquals(r.code, 1)
      assertEquals(r.err, "gate-ab: harness.arms: unknown arm zz\n")
    }

  // Final review I3 + M2 (spec §6): the target and every configured agent are checked right after loading, before any
  // prepare / baseline / run work
  private val liveSmoke = (harnessDir / "experiments" / "live-smoke.conf").toString
  private def failsAtLoad(msg: String, args: String*): IO[Unit] =
    cli(args*)().map { r =>
      assertEquals(r.code, 1, r.out)
      assertEquals(r.err, s"gate-ab: $msg\n")
      assertEquals(calls(r), Nil)
    }

  test("config validated at load: an unknown agent adapter fails before any work"):
    failsAtLoad("agents.claude-code: unknown adapter codex (one of claude-code, dsh, codex-cli, gemini-cli, generic)",
      "campaign", "--experiment", gateAb, "--set", "agents.claude-code.adapter=codex") *>
      failsAtLoad("agents.claude-code: unknown adapter codex (one of claude-code, dsh, codex-cli, gemini-cli, generic)",
        "run", "t1", "u", "1", "--experiment", gateAb, "--set", "agents.claude-code.adapter=codex")

  test("config validated at load: an unknown target adapter fails before any work"):
    failsAtLoad("target.adapter: unknown target adapter sbt (one of mill-scala)",
      "prepare", "noise", "--experiment", gateAb, "--set", "target.adapter=sbt")

  test("config validated at load: a missing required agent key names agents.<name>.<key>"):
    failsAtLoad("agents.claude-code.mcp-json: missing", "campaign", "--experiment", gateAb, "--set", "agents.claude-code.mcp-json=null") *>
      failsAtLoad("agents.dsh.profile: missing", "campaign", "--experiment", liveSmoke, "--set", "agents.dsh.profile=null") *>
      failsAtLoad("agents.dsh.dsh-home: missing", "campaign", "--experiment", liveSmoke, "--set", "agents.dsh.dsh-home=null") *>
      failsAtLoad("agents.codex.mcp-servers.scala-semantic.command: missing", "campaign", "--experiment", liveSmoke,
        "--set", "agents.codex.mcp-servers.scala-semantic.command=null") *>
      failsAtLoad("agents.agy.command: missing", "campaign", "--experiment", liveSmoke, "--set", "agents.agy.command=null")

  test("config validated at load: the generic command template has only known placeholders"):
    failsAtLoad("agents.agy.command[2]: unknown placeholder {holdout} (one of {prompt}, {model}, {repo})",
      "campaign", "--experiment", liveSmoke, "--set", """agents.agy.command=[agy, "-p", "--dir={holdout}"]""")

  test("config validated at load: live-smoke's five adapters pass"):
    cli("validate", "--experiment", liveSmoke)().map(r => assertEquals(r.code, 0, r.err))

  test("run with an unknown arm exits 1 with gate-ab: arm must be one of …, never reaching the runner"):
    cli("run", "noise", "zz", "1", "--experiment", gateAb)().map { r =>
      assertEquals(r.code, 1)
      assertEquals(r.err, "gate-ab: arm must be one of g, gf, gs, p, t, u, got zz\n")
      assertEquals(calls(r), Nil)
    }

  test("run with a non-numeric rep exits 1 with GA's rep text"):
    cli("run", "noise", "g", "x", "--experiment", gateAb)().map { r =>
      assertEquals(r.code, 1)
      assertEquals(r.err, "gate-ab: rep must be 1..9, got x\n")
    }

  test("run dispatches task, arm, rep and an absolute seed"):
    cli("run", "noise", "g", "2", "--seed", "seed.tar.gz", "--experiment", gateAb)().map { r =>
      assertEquals(r.code, 0, r.err)
      assertEquals(calls(r), List(s"run noise g 2 ${os.pwd / "seed.tar.gz"}"))
    }

  test("a HarnessError from a command prints gate-ab: <msg> and exits 1"):
    cli("score", "runs/noise-g-1", "--experiment", gateAb)(_.failWith = Some(HarnessError("boom"))).map { r =>
      assertEquals(r.code, 1)
      assertEquals(r.err, "gate-ab: boom\n")
    }

  test("any other failure also prints gate-ab: <msg> and exits 1"):
    cli("unlock", "--experiment", gateAb)(_.failWith = Some(new java.io.IOException("denied"))).map { r =>
      assertEquals(r.code, 1)
      assertEquals(r.err, "gate-ab: denied\n")
    }

  test("score and rescore score every run dir, resolved against the working dir"):
    for
      a <- cli("score", "/abs/noise-g-1", "--experiment", gateAb)()
      b <- cli("rescore", "x-g-1", "/abs/y-u-2", "--experiment", gateAb)()
    yield
      assertEquals(calls(a), List("score /abs/noise-g-1"))
      assertEquals(calls(b), List(s"score ${os.pwd / "x-g-1"}", "score /abs/y-u-2"))
      assertEquals(a.out + b.out, "")

  test("prepare exits 1 silently when validation is not all yes, 0 when it is"):
    for
      bad <- cli("prepare", "noise", "--experiment", gateAb)(_.prepareResult = false)
      ok <- cli("prepare", "noise", "--experiment", gateAb)()
    yield
      assertEquals((bad.code, bad.err), (1, ""))
      assertEquals(ok.code, 0)
      assertEquals(calls(ok), List("prepare noise"))

  test("prepare-chain runs the experiment's chain"):
    cli("prepare-chain", "--experiment", gateAb, "--set", "chain=[adsr, noise]")().map { r =>
      assertEquals(r.code, 0, r.err)
      assertEquals(calls(r), List("prepare-chain adsr,noise"))
    }

  test("prepare-chain with an empty chain exits 1"):
    cli("prepare-chain", "--experiment", gateAb)().map { r =>
      assertEquals(r.code, 1)
      assertEquals(r.err, "gate-ab: harness.chain is empty\n")
      assertEquals(calls(r), Nil)
    }

  test("chain-report with an empty chain exits 1"):
    cli("chain-report", "--experiment", gateAb)().map { r =>
      assertEquals((r.code, r.out, r.err), (1, "", "gate-ab: harness.chain is empty\n"))
    }

  test("chain-check passes the run dir and the earlier ones"):
    cli("chain-check", "/r/b-gs-1", "/r/a-gs-1", "--experiment", gateAb)().map { r =>
      assertEquals(calls(r), List("chain-check /r/b-gs-1 /r/a-gs-1"))
    }

  test("campaign, --smoke, warm-head, baseline, unlock dispatch"):
    for
      c <- cli("campaign", "--experiment", gateAb)()
      s <- cli("campaign", "--smoke", "--experiment", gateAb)()
      w <- cli("warm-head", "--experiment", gateAb)()
      b <- cli("baseline", "--experiment", gateAb)()
      u <- cli("unlock", "--experiment", gateAb)()
    yield
      assertEquals(List(c, s, w, b, u).flatMap(calls),
        List("campaign smoke=false", "campaign smoke=true", "warm-head", "baseline-cli", "unlock"))
      assertEquals(List(c, s, w, b, u).map(_.code), List(0, 0, 0, 0, 0))

  test("report prints nothing; chain-report prints its text with no extra newline"):
    for
      r <- cli("report", "--experiment", gateAb)()
      c <- cli("chain-report", "--experiment", gateAb, "--set", "chain=[adsr]")()
    yield
      assertEquals((r.code, r.out), (0, ""))
      assertEquals((c.code, c.out), (0, "# Chained feature work\n\nlast line"))

  test("Ctrl-C cancels the running command and its finalizers run"):
    for
      started <- Deferred[IO, Unit]
      finalized <- Deferred[IO, Unit]
      blk = (started.complete(()) *> IO.never[Unit]).onCancel(finalized.complete(()).void)
      fib <- cli("run", "noise", "g", "1", "--experiment", gateAb)(_.block = Some(blk)).start
      _ <- started.get
      _ <- fib.cancel
      done <- finalized.tryGet
      oc <- fib.join
    yield
      assert(done.isDefined)
      assert(oc.isCanceled, oc.toString)

  test("chainLine is GA chaincheck.py's summary line"):
    val ok = Py.parse("""{"run_id": "a-gs-2", "chain_regressions": 1,
      "erosion": {"methods": 3, "total_mass": 1.5, "erosion": 0.25}, "error": null}""")
    assertEquals(Cli.chainLine(ok), "chain a-gs-2: regressions 1, erosion {'methods': 3, 'total_mass': 1.5, 'erosion': 0.25}")
    val bad = Py.parse("""{"run_id": "a-gs-2", "chain_regressions": null, "erosion": null, "error": "ScoreError: x"}""")
    assertEquals(Cli.chainLine(bad), "chain a-gs-2: regressions None, erosion None, ERROR ScoreError: x")

  test("campaign: a HEAD baseline ScoreError prints GA's two lines (score.py --baseline: <msg>, gate-ab: HEAD baseline failed)"):
    val root = os.temp.dir()
    os.write(root / "base" / "midi-synth" / "validation.txt",
      "cut_compiles=yes\nhidden_fails_on_cut=yes\nhidden_passes_on_head=yes\n", createFolders = true)
    os.write(root / "gated-settings.json", "{}") // snapshot present: the target's settings are never read
    for
      out <- Ref[IO].of("")
      err <- Ref[IO].of("")
      term = Term(s => out.update(_ + s), s => err.update(_ + s + "\n"))
      backend = (e: Experiment) =>
        new Fake(e):
          override def baseline: IO[Unit] = Real(e, term).baseline
          override def campaign(smoke: Boolean): IO[Unit] = harness.run.Campaign.run(e, smoke, this, term.line, term.err)
      code <- Cli.run(List("campaign", "--smoke", "--experiment", gateAb, "--set", s"root=$root"), backend, term)
      e <- err.get
    yield
      assertEquals(code.code, 1)
      assertEquals(e, s"score.py --baseline: no HEAD tree at ${root / "head"}\ngate-ab: HEAD baseline failed\n")

  test("ExitCode values"):
    assertEquals(Cli.Usage, ExitCode(2))
