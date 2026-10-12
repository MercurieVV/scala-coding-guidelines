package harness.run

import cats.effect.std.Console
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import harness.config.Experiment
import harness.core.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What a campaign drives; the real implementation wires Run, Score, Prepare and ChainCheck, tests pass fakes. */
trait CampaignOps:
  def run(task: String, arm: String, rep: Int, seed: Option[os.Path]): IO[Unit]
  def score(runDir: os.Path): IO[Unit]
  def prepare(task: String): IO[Boolean]
  def baseline: IO[Unit]
  def prepareChain(steps: List[String]): IO[Boolean]
  def chainCheck(runDir: os.Path, earlier: List[os.Path]): IO[Unit]

/** All runs in a fixed interleaved order; resumable — a run with score.json is done, a run dir without one is redone.
  * A port of GA campaign.sh, in its order. */
object Campaign:
  type Out = String => IO[Unit]

  def apply(exp: Experiment, smoke: Boolean, ops: CampaignOps): IO[Unit] =
    caffeinated(onPath("caffeinate", sys.env.getOrElse("PATH", "")), Console[IO].errorln(_))(
      run(exp, smoke, ops, IO.println(_), Console[IO].errorln(_))
    )

  // ---- caffeinate (GA: re-exec under `caffeinate -dims`; here a sibling `caffeinate -dims -w <pid>` for our lifetime) ----

  def onPath(bin: String, path: String): Option[os.Path] =
    path.split(':').iterator.filter(_.nonEmpty).map(d => scala.util.Try(os.Path(d) / bin).toOption)
      .collectFirst { case Some(p) if os.isFile(p) && java.nio.file.Files.isExecutable(p.toNIO) => p }

  def caffeinated[A](bin: Option[os.Path], err: Out)(io: IO[A]): IO[A] = bin match
    case None => err("gate-ab: caffeinate not found, running unwrapped") *> io
    case Some(b) =>
      val pid = ProcessHandle.current().pid()
      Resource.make(IO.blocking {
        import java.lang.ProcessBuilder.Redirect
        new ProcessBuilder(b.toString, "-dims", "-w", pid.toString).redirectInput(Redirect.from(new java.io.File("/dev/null")))
          .redirectOutput(Redirect.DISCARD).redirectError(Redirect.INHERIT).start()
      })(p => IO.blocking { p.destroy(); p.waitFor(); () }).surround(io)

  // ---- the campaign ----

  private def die[A](msg: String): IO[A] = IO.raiseError(HarnessError(msg))
  private def errLine(e: Throwable): String = e match
    case h: HarnessError => h.line
    case other           => s"gate-ab: ${Option(other.getMessage).getOrElse(other.toString)}"
  private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  private def date: IO[String] = IO(LocalDateTime.now().format(Stamp)) // `date '+%F %T'`

  /** Python `print(v)` of a json.load'ed value. */
  private def pyStr(v: ujson.Value): String = v match
    case ujson.Null                                   => "None"
    case ujson.True                                   => "True"
    case ujson.False                                  => "False"
    case ujson.Str(s)                                 => s
    case ujson.Num(d) if d.isWhole && math.abs(d) < 1e16 => d.toLong.toString
    case ujson.Num(d)                                 => Json.pyFloat(d)
    case other                                        => ujson.write(other)

  /** `python3 -c "print(json.load(open(f)).get(key))" f 2>/dev/null || echo <fallback>` */
  private def field(f: os.Path, key: String, fallback: String): IO[String] = IO.blocking {
    Json.readJson(f).collect { case o: ujson.Obj => pyStr(o.value.getOrElse(key, ujson.Null)) }.getOrElse(fallback)
  }

  /** score_error: "" when score.json has error null, "none" when absent, else the error text (unreadable counts as an error). */
  private def scoreError(root: os.Path, id: String): IO[String] =
    val f = root / "runs" / id / "score.json"
    IO.blocking(os.isFile(f)).flatMap { e =>
      if !e then IO.pure("none")
      else field(f, "error", "unreadable").map(s => if s == "None" then "" else s)
    }

  private def append(f: os.Path, line: String): IO[Unit] = IO.blocking(os.write.append(f, line + "\n"))

  /** GA `grep` lines of a file: split on newlines, no trailing empty line. */
  private def grepLines(s: String): List[String] = s.split("\n", -1).toList match
    case ls if s.endsWith("\n") || s.isEmpty => ls.init
    case ls                                  => ls

  def run(exp: Experiment, smoke: Boolean, ops: CampaignOps, say: Out, err: Out): IO[Unit] =
    val root = exp.root
    val runs = root / "runs"
    val log = root / "campaign.log"
    val knownArms = exp.armSpecs.keySet

    def id(task: String, arm: String, rep: Int): IO[String] = Run.runId(task, arm, rep, knownArms).fold(die, IO.pure)

    // a failing step reports its own error (GA: the script's stderr) and the campaign goes on
    def attempt(io: IO[Unit]): IO[Boolean] = io.as(true).handleErrorWith(e => err(errLine(e)).as(false))

    /** GA one(): skip / run / rerun / rescore; returns whether the agent ran. A score.json with a non-null error is not
      * done. One automatic retry per run per invocation: an error containing "no result event" or "agent api error",
      * or no run.json -> redo the run; otherwise rescore only. */
    def one(task: String, arm: String, rep: Int, seed: Option[os.Path]): IO[Boolean] =
      for
        i <- id(task, arm, rep)
        rd = runs / i
        e <- scoreError(root, i)
        how <-
          if e == "none" then IO.pure(Some("run"))
          else if e.isEmpty then say(s"skip $i").as(None)
          else IO.blocking(os.isFile(rd / "run.json")).map { hasRun =>
            if !hasRun then Some("rerun")
            else if e.contains("no result event") || e.contains("agent api error") then Some("rerun")
            else Some("rescore")
          }
        ran <- how match
          case None => IO.pure(false)
          case Some(h) =>
            for
              rc <- if h == "rescore" then IO.pure("-") else attempt(ops.run(task, arm, rep, seed)).map(ok => if ok then "0" else "1")
              _ <- attempt(ops.score(rd))
              s <- field(rd / "score.json", "solved", "none")
              note = if h == "run" then "" else s" retry=$h(was: $e)"
              d <- date
              _ <- append(log, s"$d $i exit=$rc solved=$s$note")
            yield h != "rescore"
      yield ran

    def check(ok: Boolean, msg: => String): IO[Unit] = if ok then IO.unit else die(msg)
    def want(xs: List[String]): String = xs match
      case Nil       => ""
      case x :: Nil  => x
      case _         => xs.init.mkString(", ") + " or " + xs.last

    val smokeRun = smoke && exp.chain.isEmpty
    val armsUsed = if smokeRun then List(exp.report.treatment, exp.report.control) else exp.arms

    for
      _ <- IO.blocking(os.makeDir.all(runs))
      // Ruling 27: reps, each 1..4 (4: room for a test phase's fresh reps)
      _ <- check(exp.reps.nonEmpty, "GA_REPS is empty")
      _ <- exp.reps.traverse_(r => check(r >= 1 && r <= 4, s"GA_REPS: invalid rep '$r' (want 1..4)"))
      _ <- check(exp.arms.nonEmpty, "GA_ARMS is empty")
      _ <- exp.arms.traverse_(a => check(knownArms(a), s"GA_ARMS: invalid arm '$a' (want ${want(knownArms.toList.sorted)})"))
      all = exp.target.allTasks.toSet
      tasks <-
        if exp.chain.nonEmpty then
          exp.chain.traverse_(t => check(all(t), s"GA_CHAIN: unknown task '$t'")).as(exp.chain)
        else
          check(exp.tasks.nonEmpty, "GA_TASKS is empty") *>
            exp.tasks.traverse_(t => check(all(t), s"GA_TASKS: unknown task '$t'")).as(if smoke then List(exp.smokeTask) else exp.tasks)
      // Preflight: readable dirs, every needed task prepared and validated, HEAD baseline cached, gated settings snapshotted.
      _ <- Lock.unlockAll(root)
      _ <- if armsUsed.exists(a => exp.armSpecs.get(a).exists(_.settingsSnapshot)) then snapshot(exp, say) else IO.unit
      _ <- tasks.traverse_ { task =>
        val v = root / "base" / task / "validation.txt"
        IO.blocking(if os.isFile(v) then Some(os.read(v)) else None).flatMap { txt =>
          // 3 lines, or 4 for a GATE_VISIBLE task (gate_visible_on_cut); every line must be yes.
          val ok = txt.exists { t =>
            val n = t.count(_ == '\n')
            grepLines(t).count(_.endsWith("=yes")) == n && (n == 3 || n == 4)
          }
          if ok then IO.unit
          else
            say(s"prepare $task") *>
              ops.prepare(task).handleErrorWith(e => err(errLine(e)).as(false))
                .flatMap(check(_, s"prepare $task failed (see ${root / "base" / task})"))
        }
      }
      _ <- IO.blocking(os.exists(root / "head-baseline.json")).flatMap { has =>
        if has then IO.unit else attempt(ops.baseline).flatMap(check(_, "HEAD baseline failed"))
      }
      _ <-
        if exp.chain.nonEmpty then chain(exp, ops, tasks, one, id, err)
        else if smoke then one(exp.smokeTask, exp.report.treatment, exp.smokeRep, None) *> one(exp.smokeTask, exp.report.control, exp.smokeRep, None).void
        else
          (for rep <- exp.reps; task <- tasks; arm <- exp.arms yield (task, arm, rep)).traverse_((t, a, r) => one(t, a, r, None))
    yield ()

  /** Ruling 22: the target's settings are live, untracked state; every snapshot arm of this campaign uses one frozen copy. */
  private def snapshot(exp: Experiment, say: Out): IO[Unit] =
    val snap = exp.root / "gated-settings.json"
    val tmp = exp.root / "gated-settings.json.tmp"
    val src = exp.target.settingsSource
    IO.blocking(os.exists(snap)).flatMap { has =>
      if has then IO.unit
      else
        for
          _ <- IO.blocking(os.copy.over(src, tmp)).handleErrorWith(_ => die(s"cannot snapshot $src"))
          sha <- IO.blocking { val h = Files.sha256(tmp); os.write.over(exp.root / "gated-settings.json.sha256", h + "\n"); h }
            .handleErrorWith(_ => die("hash"))
          _ <- IO.blocking(os.move(tmp, snap, replaceExisting = true))
          _ <- say(s"gated settings snapshot: $snap ($sha)")
        yield ()
    }

  /** Chain mode (spec 2026-10-11 chained feature work): the chain's steps, in order, one tree per (arm, rep). */
  private def chain(
      exp: Experiment, ops: CampaignOps, tasks: List[String],
      one: (String, String, Int, Option[os.Path]) => IO[Boolean], id: (String, String, Int) => IO[String],
      err: Out
  ): IO[Unit] =
    val root = exp.root
    val runs = root / "runs"
    val cv = root / "chain-validation.txt"
    def rm(i: String): IO[Unit] = IO.blocking(os.remove.all(runs / i))
    // 0 when chain.json is missing or carries an error
    def chainCheckNeeded(i: String): IO[Boolean] = IO.blocking {
      val f = runs / i / "chain.json"
      Json.readJson(f) match
        case Some(o: ujson.Obj) if o.value.get("error").exists(_ != ujson.Null) => true
        case _                                                                => !os.exists(f)
    }
    final case class St(prev: Option[String], earlier: List[os.Path], redo: Boolean)
    def step(st: St, task: String, arm: String, rep: Int): IO[St] =
      for
        i <- id(task, arm, rep)
        rd = runs / i
        _ <- if st.redo then rm(i) else IO.unit
        // Final review I2: a step seeded from a tree that has since been replaced is stale, whatever its score says
        _ <- st.prev.fold(IO.unit) { p =>
          IO.blocking(os.isFile(rd / "run.json")).flatMap { has =>
            if !has then IO.unit
            else
              for
                w <- IO.blocking(scala.util.Try(Files.sha256(runs / p / "final-tree.tar.gz")).getOrElse("none"))
                g <- IO.blocking(Json.readJson(rd / "run.json").collect { case o: ujson.Obj =>
                  pyStr(o.value.getOrElse("seed_sha256", ujson.Null)) }.getOrElse("unreadable"))
                _ <- if w == g then IO.unit else rm(i)
              yield ()
          }
        }
        ran <- one(task, arm, rep, st.prev.map(p => runs / p / "final-tree.tar.gz"))
        need <- if ran then IO.pure(true) else chainCheckNeeded(i)
        _ <-
          if !need then IO.unit
          else
            for
              _ <- ops.chainCheck(rd, st.earlier).handleErrorWith(e => err(errLine(e)))
              n <- IO.blocking(Json.readJson(rd / "chain.json").collect { case o: ujson.Obj =>
                pyStr(o.value.getOrElse("chain_regressions", ujson.Null)) }.getOrElse("none"))
              d <- date
              _ <- append(root / "campaign.log", s"$d chain $i regressions=$n")
            yield ()
      yield St(Some(i), st.earlier :+ rd, st.redo || ran)

    for
      // missing -> validate now; any line not =yes -> refuse (a failed validation is never silently re-run)
      has <- IO.blocking(os.isFile(cv) && os.size(cv) > 0)
      _ <-
        if has then IO.unit
        else ops.prepareChain(exp.chain).handleErrorWith(e => err(errLine(e)).as(false))
          .flatMap(ok => if ok then IO.unit else die(s"prepare-chain failed (see $cv)"))
      lines <- IO.blocking(if os.isFile(cv) then Some(grepLines(os.read(cv))) else None)
      _ <- if lines.exists(_.forall(_.endsWith("=yes"))) then IO.unit else die(s"chain validation failed (see $cv)")
      _ <- (for rep <- exp.reps; arm <- exp.arms yield (arm, rep)).traverse_ { (arm, rep) =>
        tasks.foldLeftM(St(None, Nil, redo = false))((st, task) => step(st, task, arm, rep))
      }
    yield ()
