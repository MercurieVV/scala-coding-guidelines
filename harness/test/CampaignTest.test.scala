package harness.run

import cats.effect.IO
import harness.config.{Experiment, Load}
import munit.CatsEffectSuite
import scala.collection.mutable.ListBuffer

/** GA tests/shell-test.sh `campaign.sh against fakes` and `campaign.sh chain mode`, plus the two arm checks near the
  * top (`campaign rejects arm x beside gf/p`), case for case (GA names kept inside the test names). GA's fake run /
  * score / prepare / baseline / chaincheck scripts become [[FakeOps]], recording calls; GA_* overrides become
  * Experiment copies.
  */
class CampaignTest extends CatsEffectSuite:
  private val harnessDir: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .find(d => os.exists(d / "experiments" / "gate-ab.conf")).getOrElse(fail("harness dir not found"))
  private val exp0: Experiment = Load.experiment(harnessDir / "experiments" / "gate-ab.conf").fold(fail(_), identity)
  private val Eight = List("noise", "mixer-modes", "mcu", "note-ring", "backpressure", "scope-view", "modes", "midi-synth")
  private val Valid = "cut_compiles=yes\nhidden_fails_on_cut=yes\nhidden_passes_on_head=yes\n"
  private val LogLine = """\d{4}-\d\d-\d\d \d\d:\d\d:\d\d (\S+) exit=(\S+) solved=(\S+)(.*)""".r

  /** GA fake-run / fake-score / fake-prep / fake-base / chk. */
  class FakeOps(root: os.Path, scoreJson: String = """{"solved": true}""", prepFail: Option[String] = None,
                      chainRun: Boolean = false) extends CampaignOps:
    val calls = ListBuffer.empty[String]
    val runlog = ListBuffer.empty[String]
    val scores = ListBuffer.empty[os.Path]
    val preps = ListBuffer.empty[String]
    val bases = ListBuffer.empty[Unit]
    val chainPreps = ListBuffer.empty[List[String]]
    val checks = ListBuffer.empty[(os.Path, List[os.Path])]
    private var n = 0
    def run(task: String, arm: String, rep: Int, seed: Option[os.Path]): IO[Unit] = IO.blocking {
      calls += s"$task-$arm-$rep"
      runlog += s"$task $arm $rep seed=${seed.fold("")(_.toString)}"
      if chainRun then
        val d = root / "runs" / s"$task-$arm-$rep"
        os.remove.all(d); os.makeDir.all(d)
        val s = seed.fold("null")(p => "\"" + Files.sha256(p) + "\"")
        os.write(d / "run.json", s"""{"seed_sha256":$s}""")
        n += 1
        os.write(d / "final-tree.tar.gz", s"$task $arm $rep $n\n")
    }
    def score(runDir: os.Path): IO[Unit] = IO.blocking {
      scores += runDir
      os.write.over(runDir / "score.json", scoreJson, createFolders = true)
    }
    def prepare(task: String): IO[Boolean] = IO.blocking {
      preps += task
      if prepFail.contains(task) then false
      else
        os.write.over(root / "base" / task / "validation.txt", Valid, createFolders = true)
        true
    }
    def baseline: IO[Unit] = IO.blocking { bases += (); os.write.over(root / "head-baseline.json", "{}") }
    def prepareChain(steps: List[String]): IO[Boolean] = IO.blocking { chainPreps += steps; false }
    def chainCheck(runDir: os.Path, earlier: List[os.Path]): IO[Unit] = IO.blocking {
      checks += (runDir -> earlier)
      os.write.over(runDir / "chain.json", """{"chain_regressions": 0, "error": null}""")
    }

  final class Env:
    val tmp: os.Path = os.temp.dir(prefix = "campaign-test")
    val root: os.Path = tmp / "root"
    val skr: os.Path = tmp / "skr" / ".claude" / "settings.json"
    os.makeDir.all(root / "runs")
    os.write(skr, """{"hooks":"v1"}""" + "\n", createFolders = true)
    val out = ListBuffer.empty[String]
    val err = ListBuffer.empty[String]
    def exp: Experiment = exp0.copy(root = root, target = exp0.target.copy(settingsSource = skr))
    def prepared(): Unit =
      Eight.foreach(t => os.write.over(root / "base" / t / "validation.txt", Valid, createFolders = true))
      os.write.over(root / "head-baseline.json", "{}")
    def campaign(ops: FakeOps, e: Experiment = exp, smoke: Boolean = false): IO[Either[Throwable, Unit]] =
      Campaign.run(e, smoke, ops, s => IO(out += s), s => IO(err += s)).attempt
    def log: List[String] = if os.exists(root / "campaign.log") then os.read.lines(root / "campaign.log").toList else Nil
    def logFor(id: String): Option[String] = log.find { case LogLine(i, _, _, _) => i == id; case _ => false }
    def mkr(id: String, score: String, norun: Boolean = false): Unit =
      os.write.over(root / "runs" / id / "score.json", score + "\n", createFolders = true)
      if !norun then os.write.over(root / "runs" / id / "run.json", "{}\n")

  private def waitFor(cond: => Boolean, tries: Int = 100): IO[Unit] =
    IO.blocking(cond).flatMap(ok => if ok || tries == 0 then IO.unit else IO.sleep(scala.concurrent.duration.DurationInt(50).millis) *> waitFor(cond, tries - 1))

  private def dies(r: Either[Throwable, Unit], msg: String): Unit = r match
    case Left(HarnessError(m, _)) => assertEquals(m, msg)
    case other                 => fail(s"wanted HarnessError($msg), got $other")

  test("campaign exit 0, 48 runs, order, last, log lines, log format, no prepare when ready, no baseline when cached, snapshots gated settings, snapshot sha256") {
    val e = Env(); e.prepared(); val ops = FakeOps(e.root)
    e.campaign(ops).map { r =>
      assertEquals(r, Right(()), "campaign exit 0")
      assertEquals(ops.calls.size, 48, "campaign 48 runs")
      assertEquals(ops.calls.take(3).toList, List("noise-g-1", "noise-u-1", "mixer-modes-g-1"), "campaign order")
      assertEquals(ops.calls.last, "midi-synth-u-3", "campaign last")
      assertEquals(e.log.size, 48, "campaign log lines")
      assert(e.log.forall(LogLine.matches), e.log.mkString("\n"))
      assert(e.log.exists(_.contains("noise-g-1 exit=0 solved=True")), "campaign log format")
      assertEquals(e.log.head.drop(20), "noise-g-1 exit=0 solved=True")
      assertEquals(ops.preps.size, 0, "campaign no prepare when ready")
      assertEquals(ops.bases.size, 0, "campaign no baseline when cached")
      assertEquals(os.read(e.root / "gated-settings.json"), os.read(e.skr), "campaign snapshots gated settings")
      val sha = Files.sha256(e.skr)
      assertEquals(os.read(e.root / "gated-settings.json.sha256"), sha + "\n", "campaign snapshot sha256")
      assert(!os.exists(e.root / "gated-settings.json.tmp"))
      assertEquals(e.out.toList, List(s"gated settings snapshot: ${e.root / "gated-settings.json"} ($sha)"))
      assertEquals(e.err.toList, Nil)
    }
  }

  test("campaign resume 47 runs, keeps an existing snapshot, redoes half-run, skips done") {
    val e = Env(); e.prepared()
    for
      _ <- e.campaign(FakeOps(e.root))
      _ = os.write.over(e.skr, """{"hooks":"v2"}""")
      _ = { os.remove(e.root / "campaign.log"); os.remove.all(e.root / "runs") }
      _ = os.makeDir.all(e.root / "runs" / "noise-u-1")
      _ = os.write(e.root / "runs" / "noise-g-1" / "score.json", """{"solved":false}""", createFolders = true)
      ops = FakeOps(e.root)
      _ <- e.campaign(ops)
    yield
      assertEquals(ops.calls.size, 47, "campaign resume 47 runs")
      assert(os.read(e.root / "gated-settings.json").contains("v1"), "campaign keeps an existing snapshot")
      assert(ops.calls.contains("noise-u-1") && !ops.calls.contains("noise-g-1"), "campaign resume redoes half-run, skips done")
      assert(e.out.contains("skip noise-g-1"))
  }

  test("retry rescore-only skips run, rescore retry logged, no result event / agent api error / missing run.json / combined error rerun, error null skipped and not logged") {
    val e = Env(); e.prepared()
    e.mkr("noise-g-1", """{"solved":false,"error":"scoring incomplete"}""")
    e.mkr("noise-u-1", """{"solved":false,"error":"no result event"}""")
    e.mkr("mixer-modes-g-1", """{"solved":true,"error":null}""")
    e.mkr("mixer-modes-u-1", """{"solved":false,"error":"HEAD baseline missing"}""", norun = true)
    e.mkr("midi-synth-g-1", """{"solved":false,"error":"agent api error: 429 You have hit your weekly limit"}""")
    e.mkr("mcu-g-1", """{"solved":false,"error":"no result event; agent environment leaked: no init event"}""")
    val ops = FakeOps(e.root)
    e.campaign(ops).map { _ =>
      assert(!ops.calls.contains("noise-g-1"), "retry rescore-only skips run.sh")
      assertEquals(e.logFor("noise-g-1").map(_.drop(20)), Some("noise-g-1 exit=- solved=True retry=rescore(was: scoring incomplete)"), "rescore retry logged")
      assert(ops.calls.contains("noise-u-1") && e.logFor("noise-u-1").exists(_.endsWith(" retry=rerun(was: no result event)")), "no result event reruns the whole run")
      assert(ops.calls.contains("midi-synth-g-1") && e.logFor("midi-synth-g-1").exists(_.contains("retry=rerun")), "agent api error reruns the whole run")
      assert(ops.calls.contains("mixer-modes-u-1") && e.logFor("mixer-modes-u-1").exists(_.endsWith(" exit=0 solved=True retry=rerun(was: HEAD baseline missing)")), "missing run.json reruns the whole run")
      assert(ops.calls.contains("mcu-g-1") && e.logFor("mcu-g-1").exists(_.contains("retry=rerun")), "combined no-result error reruns the whole run")
      assert(!ops.calls.contains("mixer-modes-g-1"), "error null is skipped")
      assertEquals(e.logFor("mixer-modes-g-1"), None, "skipped run not logged")
    }
  }

  test("errored retry: score.py once for the retried run, loop continues to all other runs") {
    val e = Env(); e.prepared()
    e.mkr("noise-g-1", """{"solved":false,"error":"scoring incomplete"}""")
    val ops = FakeOps(e.root, scoreJson = """{"solved": false, "error": "scoring incomplete"}""")
    e.campaign(ops).map { _ =>
      assertEquals(ops.scores.count(_ == e.root / "runs" / "noise-g-1"), 1, "errored retry: score.py once for the retried run")
      assertEquals(ops.calls.size, 47, "errored retry: loop continues to all other runs")
      assertEquals(e.logFor("noise-g-1").map(_.drop(20)), Some("noise-g-1 exit=- solved=False retry=rescore(was: scoring incomplete)"))
    }
  }

  test("GA_REPS=2: 16 runs, order; GA_REPS=4: 16 runs") {
    val e = Env(); e.prepared()
    val o2 = FakeOps(e.root); val o4 = FakeOps(e.root)
    for
      _ <- e.campaign(o2, e.exp.copy(reps = List(2)))
      _ = os.remove.all(e.root / "runs")
      _ <- e.campaign(o4, e.exp.copy(reps = List(4)))
    yield
      assertEquals(o2.calls.size, 16, "GA_REPS=2: 16 runs")
      assertEquals(o2.calls.take(3).toList, List("noise-g-2", "noise-u-2", "mixer-modes-g-2"), "GA_REPS=2: order")
      assertEquals(o4.calls.size, 16, "GA_REPS=4: 16 runs")
  }

  test("GA_REPS invalid rep dies, no runs; GA_REPS empty dies") {
    val e = Env(); e.prepared(); val ops = FakeOps(e.root)
    for
      r1 <- e.campaign(ops, e.exp.copy(reps = List(2, 5)))
      r2 <- e.campaign(ops, e.exp.copy(reps = Nil))
    yield
      dies(r1, "GA_REPS: invalid rep '5' (want 1..4)")
      assert(ops.calls.isEmpty, "GA_REPS invalid: no runs")
      dies(r2, "GA_REPS is empty")
  }

  test("ablation campaign: 8 runs, order") {
    val e = Env(); e.prepared(); val ops = FakeOps(e.root)
    e.campaign(ops, e.exp.copy(arms = List("t"), reps = List(1, 2), tasks = List("backpressure", "mcu", "mixer-modes", "scope-view"))).map { _ =>
      assertEquals(ops.calls.size, 8, "ablation campaign: 8 runs")
      assertEquals(ops.calls.toList, List("backpressure-t-1", "mcu-t-1", "mixer-modes-t-1", "scope-view-t-1",
        "backpressure-t-2", "mcu-t-2", "mixer-modes-t-2", "scope-view-t-2"), "ablation campaign: order")
    }
  }

  test("GA_ARMS invalid arm dies, no runs; GA_ARMS empty dies; campaign rejects arm x beside gf; campaign rejects arm x beside p") {
    val e = Env(); e.prepared(); val ops = FakeOps(e.root)
    val want = "(want g, gf, gs, p, t or u)"
    for
      r1 <- e.campaign(ops, e.exp.copy(arms = List("g", "x")))
      r2 <- e.campaign(ops, e.exp.copy(arms = Nil))
      r3 <- e.campaign(ops, e.exp.copy(arms = List("gf", "x")))
      r4 <- e.campaign(ops, e.exp.copy(arms = List("p", "x")))
    yield
      dies(r1, s"GA_ARMS: invalid arm 'x' $want")
      assert(ops.calls.isEmpty, "GA_ARMS invalid: no runs")
      dies(r2, "GA_ARMS is empty")
      dies(r3, s"GA_ARMS: invalid arm 'x' $want")
      dies(r4, s"GA_ARMS: invalid arm 'x' $want")
  }

  test("GA_TASKS unknown task dies, no runs; GA_TASKS empty dies") {
    val e = Env(); e.prepared(); val ops = FakeOps(e.root)
    for
      r1 <- e.campaign(ops, e.exp.copy(tasks = List("mcu", "nosuch")))
      r2 <- e.campaign(ops, e.exp.copy(tasks = Nil))
    yield
      dies(r1, "GA_TASKS: unknown task 'nosuch'")
      assert(ops.calls.isEmpty, "GA_TASKS invalid: no runs")
      dies(r2, "GA_TASKS is empty")
  }

  test("campaign smoke: smoke task and rep, report treatment then control") {
    val e = Env(); e.prepared(); val ops = FakeOps(e.root)
    val ops2 = FakeOps(e.root)
    val ex = e.exp.copy(smokeTask = "noise", smokeRep = 7, report = e.exp.report.copy(treatment = "gs", control = "t"))
    for
      _ <- e.campaign(ops, smoke = true)
      _ = os.remove.all(e.root / "runs")
      _ <- e.campaign(ops2, ex, smoke = true)
    yield
      assertEquals(ops.calls.toList, List("midi-synth-g-9", "midi-synth-u-9"), "campaign smoke")
      assertEquals(ops2.calls.toList, List("noise-gs-7", "noise-t-7"))
  }

  test("preflight unlocked base, prepares missing and failed only, baseline once, then runs all") {
    val e = Env(); e.prepared()
    os.remove(e.root / "head-baseline.json"); os.remove.all(e.root / "base" / "mcu")
    os.write.over(e.root / "base" / "noise" / "validation.txt", "cut_compiles=yes\nhidden_fails_on_cut=no\nhidden_passes_on_head=yes\n")
    os.write.over(e.root / "base" / "modes" / "validation.txt", Valid + "gate_visible_on_cut=yes\n")
    os.write.over(e.root / "base" / "note-ring" / "validation.txt", Valid + "gate_visible_on_cut=no\n")
    os.perms.set(e.root / "base", 0)
    val ops = FakeOps(e.root)
    e.campaign(ops).map { r =>
      assertEquals(r, Right(()))
      assertEquals(os.perms(e.root / "base").toString, "rwxr-xr-x", "preflight unlocked base")
      assertEquals(ops.preps.toList.sorted, List("mcu", "noise", "note-ring"), "preflight prepares missing and failed only")
      assertEquals(ops.bases.size, 1, "preflight baseline once")
      assertEquals(ops.calls.size, 48, "preflight then runs all")
      assertEquals(e.out.filter(_.startsWith("prepare ")).toList, List("prepare noise", "prepare mcu", "prepare note-ring"))
    }
  }

  test("validation with 2 or 5 lines all yes is prepared again") {
    val e = Env(); e.prepared()
    os.write.over(e.root / "base" / "noise" / "validation.txt", "a=yes\nb=yes\n")
    os.write.over(e.root / "base" / "mcu" / "validation.txt", Valid + "d=yes\ne=yes\n")
    val ops = FakeOps(e.root)
    e.campaign(ops).map(_ => assertEquals(ops.preps.toList, List("noise", "mcu")))
  }

  test("smoke preflight only midi-synth") {
    val e = Env(); e.prepared()
    os.remove.all(e.root / "base" / "midi-synth"); os.remove.all(e.root / "base" / "noise")
    val ops = FakeOps(e.root)
    e.campaign(ops, smoke = true).map(_ => assertEquals(ops.preps.toList, List("midi-synth"), "smoke preflight only midi-synth"))
  }

  test("preflight aborts before any run") {
    val e = Env(); e.prepared(); os.remove.all(e.root / "base" / "mcu")
    val ops = FakeOps(e.root, prepFail = Some("mcu"))
    e.campaign(ops).map { r =>
      dies(r, s"prepare mcu failed (see ${e.root / "base" / "mcu"})")
      assert(ops.calls.isEmpty, "preflight aborts before any run")
    }
  }

  test("a prepare that raises prints its error, then the campaign dies") {
    val e = Env(); e.prepared(); os.remove.all(e.root / "base" / "mcu")
    val ops = new FakeOps(e.root):
      override def prepare(task: String): IO[Boolean] = IO.raiseError(HarnessError("no task mcu"))
    e.campaign(ops).map { r =>
      dies(r, s"prepare mcu failed (see ${e.root / "base" / "mcu"})")
      assertEquals(e.err.toList, List("gate-ab: no task mcu"))
    }
  }

  test("HEAD baseline failure dies before any run") {
    val e = Env(); e.prepared(); os.remove(e.root / "head-baseline.json")
    val ops = new FakeOps(e.root):
      override def baseline: IO[Unit] = IO.raiseError(HarnessError("boom"))
    e.campaign(ops).map { r =>
      dies(r, "HEAD baseline failed")
      assert(ops.calls.isEmpty)
    }
  }

  test("no snapshot when no selected arm has settingsSnapshot; snapshot failure dies") {
    val e = Env(); e.prepared()
    val ops = FakeOps(e.root)
    for
      _ <- e.campaign(ops, e.exp.copy(arms = List("u")))
      _ = assert(!os.exists(e.root / "gated-settings.json"), "arm u alone takes no snapshot")
      _ = os.remove(e.skr)
      r <- e.campaign(ops, e.exp.copy(arms = List("g")))
    yield
      dies(r, s"cannot snapshot ${e.skr}")
      assert(!os.exists(e.root / "gated-settings.json"))
  }

  test("a run that fails is logged with its exit code and the loop continues") {
    val e = Env(); e.prepared()
    val ops = new FakeOps(e.root):
      override def run(task: String, arm: String, rep: Int, seed: Option[os.Path]): IO[Unit] =
        super.run(task, arm, rep, seed) *> (if task == "noise" && arm == "g" then IO.raiseError(HarnessError("precompile failed")) else IO.unit)
    e.campaign(ops, e.exp.copy(reps = List(1))).map { r =>
      assertEquals(r, Right(()))
      assertEquals(e.logFor("noise-g-1").map(_.drop(20)), Some("noise-g-1 exit=1 solved=True"))
      assertEquals(ops.calls.size, 16)
      assertEquals(e.err.toList, List("gate-ab: precompile failed"))
    }
  }

  test("solved None when score.json lacks it, none when score.json is missing after scoring") {
    val e = Env(); e.prepared()
    val ops = new FakeOps(e.root, scoreJson = """{"error": null}"""):
      override def score(runDir: os.Path): IO[Unit] =
        if runDir.last == "noise-u-1" then IO.unit else super.score(runDir)
    e.campaign(ops, e.exp.copy(reps = List(1), tasks = List("noise"))).map { _ =>
      assertEquals(e.log.map(_.drop(20)), List("noise-g-1 exit=0 solved=None", "noise-u-1 exit=0 solved=none"))
    }
  }

  test("campaign spawns caffeinate -dims -w <own pid> once, for the campaign's lifetime") {
    val e = Env(); e.prepared()
    val args = e.tmp / "cafs"
    val caf = e.tmp / "fake-caf"
    os.write(caf, s"#!/bin/sh\necho \"$$*\" >>'$args'\nexec sleep 30\n")
    os.perms.set(caf, "rwxr-xr-x")
    val ops = FakeOps(e.root)
    val pid = ProcessHandle.current().pid()
    Campaign.caffeinated(Some(caf), s => IO(e.err += s))(
      waitFor(os.exists(args)) *> Campaign.run(e.exp, true, ops, _ => IO.unit, _ => IO.unit)
    ).map { _ =>
      assertEquals(os.read.lines(args).toList, List(s"-dims -w $pid"), "campaign re-execs under caffeinate once")
      assertEquals(ops.calls.toList, List("midi-synth-g-9", "midi-synth-u-9"), "campaign under caffeinate ran smoke")
      assertEquals(e.err.toList, Nil)
      val alive = ProcessHandle.current().children().filter(_.info().command().orElse("").endsWith("sleep")).count()
      assertEquals(alive, 0L, "caffeinate stopped with the campaign")
    }
  }

  test("caffeinate not found, running unwrapped") {
    val e = Env()
    Campaign.caffeinated(None, s => IO(e.err += s))(IO.pure(1)).map { r =>
      assertEquals(r, 1)
      assertEquals(e.err.toList, List("gate-ab: caffeinate not found, running unwrapped"))
    }
  }

  test("caffeinate lookup finds a binary on PATH only") {
    val d = os.temp.dir(prefix = "campaign-path")
    os.write(d / "caffeinate", "#!/bin/sh\n"); os.perms.set(d / "caffeinate", "rwxr-xr-x")
    assertEquals(Campaign.onPath("caffeinate", s"/nonexistent:$d"), Some(d / "caffeinate"))
    assertEquals(Campaign.onPath("caffeinate", "/nonexistent"), None)
  }

  // ---- chain mode ----

  private val Chain = List("fr-pan", "fr-exclusive-solo", "fr-mode-leds", "fr-tuning")

  final class ChainEnv:
    val e: Env = Env()
    val croot: os.Path = e.root
    Chain.foreach(t => os.write.over(croot / "base" / t / "validation.txt", "a=yes\nb=yes\nc=yes\n", createFolders = true))
    os.write(croot / "gated-settings.json", "{}\n"); os.write(croot / "head-baseline.json", "{}\n")
    os.write(croot / "chain-validation.txt", "patches_apply=yes\nreference_passes_all=yes\n")
    val exp: Experiment = e.exp.copy(chain = Chain, arms = List("u", "gs"), reps = List(1))
    def ops(): FakeOps = new FakeOps(croot, scoreJson = """{"solved": true, "error": null}""", chainRun = true):
      override def prepare(task: String): IO[Boolean] = IO.pure(false)
      override def baseline: IO[Unit] = IO.raiseError(HarnessError("false"))
    def r(id: String): os.Path = croot / "runs" / id

  test("chain campaign exit 0, runs in order with seeds, chain check gets the earlier steps, step 1 has no earlier steps") {
    val c = ChainEnv(); val ops = c.ops()
    c.e.campaign(ops, c.exp).map { res =>
      assertEquals(res, Right(()), "chain campaign exit 0")
      def seed(id: String) = s"seed=${c.r(id) / "final-tree.tar.gz"}"
      assertEquals(ops.runlog.toList, List(
        "fr-pan u 1 seed=", s"fr-exclusive-solo u 1 ${seed("fr-pan-u-1")}", s"fr-mode-leds u 1 ${seed("fr-exclusive-solo-u-1")}",
        s"fr-tuning u 1 ${seed("fr-mode-leds-u-1")}", "fr-pan gs 1 seed=", s"fr-exclusive-solo gs 1 ${seed("fr-pan-gs-1")}",
        s"fr-mode-leds gs 1 ${seed("fr-exclusive-solo-gs-1")}", s"fr-tuning gs 1 ${seed("fr-mode-leds-gs-1")}"),
        "chain runs in order with seeds")
      assertEquals(ops.checks(2), c.r("fr-mode-leds-u-1") -> List(c.r("fr-pan-u-1"), c.r("fr-exclusive-solo-u-1")), "chain check gets the earlier steps")
      assertEquals(ops.checks(0), c.r("fr-pan-u-1") -> Nil, "chain check on step 1 has no earlier steps")
      assertEquals(ops.checks.size, 8)
      val chainLines = c.e.log.filter(_.contains(" chain "))
      assertEquals(chainLines.size, 8)
      assert(chainLines.forall(_.matches("""\d{4}-\d\d-\d\d \d\d:\d\d:\d\d chain \S+ regressions=0""")), chainLines.mkString("\n"))
      assertEquals(c.e.log.take(2).map(_.drop(20)), List("fr-pan-u-1 exit=0 solved=True", "chain fr-pan-u-1 regressions=0"))
      assert(ops.chainPreps.isEmpty)
    }
  }

  test("chain resume redoes the rest of that chain only; chain check redone after an error; redoes steps seeded from a replaced tree") {
    val c = ChainEnv()
    for
      _ <- c.e.campaign(c.ops(), c.exp)
      _ = os.remove(c.r("fr-exclusive-solo-u-1") / "score.json")
      o1 = c.ops()
      _ <- c.e.campaign(o1, c.exp)
      _ = assertEquals(o1.runlog.map(_.split(' ').take(3).mkString(" ")).toList,
        List("fr-exclusive-solo u 1", "fr-mode-leds u 1", "fr-tuning u 1"), "chain resume redoes the rest of that chain only")
      _ = os.write.over(c.r("fr-tuning-gs-1") / "chain.json", """{"error": "boom"}""")
      o2 = c.ops()
      _ <- c.e.campaign(o2, c.exp)
      _ = assertEquals((o2.runlog.toList, o2.checks.map(_._1).toList), (Nil, List(c.r("fr-tuning-gs-1"))), "chain check redone after an error")
      _ = os.write.over(c.r("fr-exclusive-solo-gs-1") / "final-tree.tar.gz", "different\n")
      o3 = c.ops()
      _ <- c.e.campaign(o3, c.exp)
    yield assertEquals(o3.runlog.map(_.split(' ').take(3).mkString(" ")).toList,
      List("fr-mode-leds gs 1", "fr-tuning gs 1"), "chain redoes steps seeded from a replaced tree")
  }

  test("chain check runs when chain.json is missing; regressions none when chain check writes nothing") {
    val c = ChainEnv()
    for
      _ <- c.e.campaign(c.ops(), c.exp)
      _ = os.remove(c.r("fr-pan-gs-1") / "chain.json")
      o = new FakeOps(c.croot, chainRun = true):
        override def chainCheck(runDir: os.Path, earlier: List[os.Path]): IO[Unit] = IO(checks += (runDir -> earlier))
      _ <- c.e.campaign(o, c.exp)
    yield
      assertEquals(o.checks.toList, List(c.r("fr-pan-gs-1") -> Nil))
      assert(c.e.log.last.endsWith(" chain fr-pan-gs-1 regressions=none"), c.e.log.last)
  }

  test("chain refuses an invalid chain, refusal ran nothing") {
    val c = ChainEnv()
    os.write.over(c.croot / "chain-validation.txt", "patches_apply=no\n")
    val ops = c.ops()
    c.e.campaign(ops, c.exp).map { r =>
      dies(r, s"chain validation failed (see ${c.croot / "chain-validation.txt"})")
      assert(ops.runlog.isEmpty, "chain refusal ran nothing")
    }
  }

  test("chain validation missing runs prepare-chain; its failure dies") {
    val c = ChainEnv()
    os.remove(c.croot / "chain-validation.txt")
    val ops = c.ops()
    c.e.campaign(ops, c.exp).map { r =>
      dies(r, s"prepare-chain failed (see ${c.croot / "chain-validation.txt"})")
      assertEquals(ops.chainPreps.toList, List(Chain))
      assert(ops.runlog.isEmpty)
    }
  }

  // PARITY.md (Task 12 ruling): GA's grep of a missing file exits 2 and the chain runs on; the harness refuses
  test("prepare-chain succeeding without chain-validation.txt dies chain validation failed, runs nothing") {
    val c = ChainEnv()
    os.remove(c.croot / "chain-validation.txt")
    val ops = new FakeOps(c.croot, scoreJson = """{"solved": true, "error": null}""", chainRun = true):
      override def prepare(task: String): IO[Boolean] = IO.pure(false)
      override def prepareChain(steps: List[String]): IO[Boolean] = IO.pure(true)
    c.e.campaign(ops, c.exp).map { r =>
      dies(r, s"chain validation failed (see ${c.croot / "chain-validation.txt"})")
      assert(ops.runlog.isEmpty)
    }
  }

  test("chain with an unknown task dies; chain mode wins over smoke") {
    val c = ChainEnv(); val ops = c.ops()
    for
      r <- c.e.campaign(ops, c.exp.copy(chain = List("fr-pan", "nosuch")))
      _ = dies(r, "GA_CHAIN: unknown task 'nosuch'")
      r2 <- c.e.campaign(ops, c.exp, smoke = true)
    yield
      assertEquals(r2, Right(()))
      assertEquals(ops.runlog.size, 8)
  }
