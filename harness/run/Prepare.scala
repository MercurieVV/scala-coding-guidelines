package harness.run

import cats.effect.IO
import cats.effect.std.Console
import cats.syntax.all.*
import harness.config.{Experiment, TaskSpec}
import harness.core.{Erosion, ErosionMeasure, Json}
import harness.target.Target

/** The trees every run starts from, and their validation. Ports GA warm-cache.sh (HEAD reference tree), prepare.sh
  * (a task's cut tree, hidden spec and validation.txt) and prepare-chain.sh (chain-validation.txt and the reference
  * erosion line), in their order, with their files, output and ga_die texts.
  *
  * `say` is the script's stdout, `err` the failing tools' stderr that GA lets through before ga_die.
  */
object Prepare:
  private type Out = String => IO[Unit]
  private val stderr: Out = Console[IO].errorln(_)

  /** GA warm-cache.sh runs Mill with no timeout. */
  private val NoCap = 7 * 24 * 3600
  private val Gate = Seq("-c", "user.name=gate-eval", "-c", "user.email=gate-eval@local", "-c", "commit.gpgsign=false")

  def head(exp: Experiment): IO[Unit] = target(exp).flatMap(headWith(exp, _))
  def task(exp: Experiment, task: String): IO[Boolean] = target(exp).flatMap(taskWith(exp, task, _))
  def chain(exp: Experiment, steps: List[String], erosion: ErosionMeasure = Erosion): IO[Boolean] =
    target(exp).flatMap(chainWith(exp, steps, _, erosion))

  private def target(exp: Experiment): IO[Target] = Target.of(exp.target).fold(die, IO.pure)
  private def die[A](msg: String): IO[A] = IO.raiseError(HarnessError(msg))
  private def check(ok: Boolean, msg: => String): IO[Unit] = if ok then IO.unit else die(msg)
  private def orDie[A](io: IO[A], msg: => String, err: Out): IO[A] =
    io.handleErrorWith(e => err(Option(e.getMessage).getOrElse(e.toString)) *> die(msg))
  /** GA ga_task_env: `no task <t> (<tasks>/<t>/task.env)` when the task has no definition. */
  private def loadTask(exp: Experiment, task: String): IO[TaskSpec] =
    val d = exp.tasksDir / task
    if !os.isFile(d / "task.conf") && !os.isFile(d / "task.env") then die(s"no task $task (${d / "task.env"})")
    else TaskSpec.load(exp.tasksDir, task).fold(die, IO.pure)
  private def yn(b: Boolean): String = if b then "yes" else "no"
  private def rm(p: os.Path): IO[Unit] = IO.blocking(os.remove.all(p))

  // ---------------------------------------------------------------- warm-cache.sh

  /** GA warm-cache.sh: export the target at its commit to <root>/head, git init it, compile everything there once.
    * Mill output is never copied between trees (it records absolute paths).
    */
  def headWith(exp: Experiment, target: Target, say: Out = IO.println, err: Out = stderr): IO[Unit] =
    val h = exp.root / "head"
    for
      _ <- IO.blocking { os.remove.all(h); os.makeDir.all(h) }
      _ <- orDie(Files.gitArchive(exp.target.repo, exp.target.commit, h), "git archive failed", err)
      _ <- orDie(Files.gitInitCommit(h, "head"), "git init", err)
      r <- target.runBuild(h, target.headCompileArgs, "out", exp.root / "head-compile.log", cap = Some(NoCap))
      _ <- check(r.rc == 0, "HEAD does not compile")
      _ <- say(s"head reference tree: $h")
    yield ()

  // ---------------------------------------------------------------- shared steps

  /** GA lib.sh ga_check_patch: the reference patch must apply to <dir> (a HEAD copy) before any Mill run. */
  private def checkPatch(exp: Experiment, task: String, patch: String, dir: os.Path): IO[Unit] =
    val p = exp.tasksDir / task / os.RelPath(patch)
    check(os.isFile(p), s"reference patch $p missing") *>
      Proc.capture(Seq("git", "-C", dir.toString, "apply", "--check", p.toString), dir).flatMap { (rc, _) =>
        check(rc == 0, s"reference patch $p does not apply to $dir")
      }

  /** GA `python3 -c '…sorted(json.load(open(f))["modules"])…'` over head-baseline.json. */
  private def baselineModules(root: os.Path): IO[List[String]] =
    val f = root / "head-baseline.json"
    IO.blocking(Json.readJson(f)).flatMap {
      case Some(o: ujson.Obj) =>
        o.value.get("modules") match
          case Some(m: ujson.Obj)                            => IO.pure(m.value.keys.toList.sorted)
          case Some(a: ujson.Arr) if a.value.forall(_.strOpt.isDefined) => IO.pure(a.value.map(_.str).toList.sorted)
          case _                                             => die(s"read $f")
      case _ => die(s"read $f")
    }

  /** git archive of the holdout commit into dir; its physical path (GA `pwd -P`). */
  private def holdout(exp: Experiment, dir: os.Path, err: Out): IO[os.Path] =
    rm(dir) *> IO.blocking(os.makeDir.all(dir)) *>
      orDie(Files.gitArchive(exp.target.holdoutRepo, exp.target.holdoutCommit, dir), "holdout export", err) *>
      IO.blocking(os.Path(dir.toNIO.toRealPath()))

  /** The holdout run, its output appended to log (GA `( export DSH_HOLDOUT_DIR=…; mill_in 600 … holdout.test ) >>log`). */
  private def holdoutRun(target: Target, tree: os.Path, hod: os.Path, log: os.Path): IO[Int] =
    val part = os.Path(s"$log.holdout")
    target.runBuild(tree, target.holdoutArgs, "out", part, target.holdoutEnv(hod), Some(600)).flatMap { r =>
      IO.blocking { os.write.append(log, os.read.bytes(part)); os.remove(part) }.as(r.rc)
    }

  /** GA `mill_in <cap> <tree> -k $mods >log && ( holdout ) >>log`. */
  private def allThenHoldout(target: Target, tree: os.Path, mods: List[String], cap: Int, hod: os.Path, log: os.Path): IO[Boolean] =
    target.runBuild(tree, target.joinTasks(mods.map(Seq(_))), "out", log, cap = Some(cap), keepGoing = true).flatMap { r =>
      if r.rc != 0 then IO.pure(false) else holdoutRun(target, tree, hod, log).map(_ == 0)
    }

  // ---------------------------------------------------------------- prepare.sh

  /** GA prepare.sh <task>: <root>/base/<task>/ with the cut tree (no history), the hidden spec outside it and
    * validation.txt; true iff every validation line is yes.
    */
  def taskWith(exp: Experiment, task: String, target: Target, say: Out = IO.println, err: Out = stderr): IO[Boolean] =
    val root = exp.root
    val base = root / "base" / task
    val tree = base / "tree"
    val hidden = base / "hidden"
    val v = base / "validate"
    for
      ts <- loadTask(exp, task)
      hs = os.RelPath(ts.hiddenSpec)
      _ <- IO.blocking { os.remove.all(base); os.makeDir.all(tree); os.makeDir.all(hidden) }
      _ <- orDie(Files.gitArchive(exp.target.repo, exp.target.commit, tree), "git archive failed", err)
      _ <- IO.blocking(os.makeDir.all((hidden / hs) / os.up))
      _ <- ts.hiddenSpecSrc match
        case Some(s) =>
          // Authored for the study: the spec comes from the task folder; the exported tree must not already have one there.
          val src = exp.tasksDir / task / os.RelPath(s)
          if !os.isFile(src) then rm(base) *> die(s"authored hidden spec $src missing")
          else if os.exists(tree / hs) then rm(base) *> die(s"hidden spec ${ts.hiddenSpec} already in the tree; HIDDEN_SPEC_SRC would shadow it")
          else orDie(IO.blocking(os.copy.over(src, hidden / hs)), s"copy $src", err)
        case None =>
          orDie(IO.blocking(os.move(tree / hs, hidden / hs)), s"hidden spec ${ts.hiddenSpec} not in tree", err)
      _ <- ts.cutFiles.traverse_ { f =>
        orDie(IO.blocking(os.copy.over(exp.tasksDir / task / "stub" / os.RelPath(f), tree / os.RelPath(f))), s"stub $f", err)
      }
      _ <- orDie(Files.gitInitCommit(tree, "base"), "git init", err)
      _ <- IO.blocking { os.remove.all(v); Files.copyTree(tree, v, _ => false) }
      c <- target.runBuild(v, target.compileAllArgs, "out", base / "cut-compile.log", cap = Some(1800)).map(_.rc == 0)
      _ <- IO.blocking { os.makeDir.all((v / hs) / os.up); os.copy.over(hidden / hs, v / hs) }
      f <- target.runBuild(v, Seq(ts.module), "out", base / "hidden-on-cut.log", cap = Some(600)).map(_.rc != 0)
      h = root / "head"
      _ <- check(os.isDir(h), s"no $h — run warm-cache.sh first")
      prp <- if ts.hiddenSpecSrc.isDefined then headValidate(exp, ts, base, h, target, err)
             else target.runBuild(h, Seq(ts.module), "out", base / "hidden-on-head.log", cap = Some(600)).map(r => (r.rc == 0, None))
      (p, rp) = prp
      g <- if ts.gateVisible then gateVisible(exp, ts, base, v, target, err).map(Some(_)) else IO.pure(None)
      _ <- rm(v)
      text = s"cut_compiles=${yn(c)}\nhidden_fails_on_cut=${yn(f)}\nhidden_passes_on_head=${yn(p)}\n" +
        g.fold("")(b => s"gate_visible_on_cut=${yn(b)}\n") + rp.fold("")(b => s"reference_passes_all=${yn(b)}\n")
      _ <- IO.blocking(os.write.over(base / "validation.txt", text))
      _ <- say(text.stripSuffix("\n"))
    yield c && f && p && g.forall(identity) && rp.forall(identity)

  /** GA prepare.sh, authored spec: a throwaway copy of HEAD (+ the reference patch for FR tasks) with the spec added;
    * HEAD itself stays as exported. FR (spec 2026-10-10 §3): with the reference applied, every existing test module and
    * the holdout pass. Returns (hidden passes on head, reference passes all when FR).
    */
  private def headValidate(
      exp: Experiment, ts: TaskSpec, base: os.Path, h: os.Path, target: Target, err: Out
  ): IO[(Boolean, Option[Boolean])] =
    val hv = base / "head-validate"
    val hs = os.RelPath(ts.hiddenSpec)
    for
      _ <- IO.blocking { os.remove.all(hv); Files.copyTree(h, hv, _ == "out") } // GA: cp -R then rm -rf out
      _ <- ts.referencePatch.traverse_ { rp =>
        val p = exp.tasksDir / ts.name / os.RelPath(rp)
        checkPatch(exp, ts.name, rp, hv) *>
          Proc.capture(Seq("git", "-C", hv.toString, "apply", p.toString), hv).flatMap { (rc, out) =>
            if rc == 0 then IO.unit else err(out.stripSuffix("\n")) *> die("apply reference patch")
          }
      }
      _ <- IO.blocking { os.makeDir.all((hv / hs) / os.up); os.copy.over(base / "hidden" / hs, hv / hs) }
      p <- target.runBuild(hv, Seq(ts.module), "out", base / "hidden-on-head.log", cap = Some(1800)).map(_.rc == 0)
      rp <- ts.referencePatch.traverse { _ =>
        for
          mods <- baselineModules(exp.root)
          ho = base / "holdout-ref"
          hod <- holdout(exp, ho, err)
          ok <- allThenHoldout(target, hv, mods, 1800, hod, base / "reference-all.log")
          _ <- rm(ho)
        yield ok
      }
      _ <- rm(hv)
    yield (p, rp)

  /** GA prepare.sh GATE_VISIBLE (spec 2026-10-09 §4): on the cut tree, some test module other than TASK_MODULE, or the
    * holdout, must fail. Only a failing test is evidence; a timeout, crash or build failure says nothing about visibility.
    */
  private def gateVisible(exp: Experiment, ts: TaskSpec, base: os.Path, v: os.Path, target: Target, err: Out): IO[Boolean] =
    val gl = base / "gate-visible.log"
    val ho = base / "holdout-validate"
    for
      mods <- baselineModules(exp.root).map(_.filter(_ != ts.module))
      _ <- IO.blocking(os.remove(v / os.RelPath(ts.hiddenSpec), checkExists = false))
      hod <- holdout(exp, ho, err)
      drc <- target.runBuild(v, target.joinTasks(mods.map(Seq(_))), "out", gl, cap = Some(1800), keepGoing = true).map(_.rc)
      hrc <- holdoutRun(target, v, hod, gl)
      _ <- rm(ho)
      _ <- if drc == 124 || hrc == 124 then rm(v) *> die(s"gate-visible check timed out (see $gl)") else IO.unit
      g <- if drc == 0 && hrc == 0 then IO.pure(false)
           else IO.blocking("tests? failed".r.findFirstIn(Json.readLenient(gl)).isDefined).flatMap { failed =>
             if failed then IO.pure(true) else rm(v) *> die(s"gate-visible check failed without a test failure (see $gl)")
           }
    yield g

  // ---------------------------------------------------------------- prepare-chain.sh

  private final case class ChainAcc(ap: Boolean, specs: List[(String, os.Path)], tmods: List[String], sep: String)

  /** GA prepare-chain.sh <task>... (spec 2026-10-11 chained feature work §3): on HEAD + the chain's reference patches in
    * order (git apply -3), every step's hidden spec, every existing test module and the holdout pass
    * (chain-validation.txt). Also the reference erosion line, HEAD + patches 1..k (chain-reference-erosion.json).
    */
  def chainWith(
      exp: Experiment, steps: List[String], target: Target, erosion: ErosionMeasure,
      say: Out = IO.println, err: Out = stderr
  ): IO[Boolean] =
    val root = exp.root
    val h = root / "head"
    val cvf = root / "chain-validation.txt"
    val ref = root / "chain-reference-erosion.json"
    val refTmp = os.Path(s"$ref.tmp")
    val cv = root / "chain-validate"

    def loop(rest: List[String], acc: ChainAcc): IO[ChainAcc] = rest match
      case Nil => IO.pure(acc)
      case task :: more =>
        for
          ts <- loadTask(exp, task)
          patch <- ts.referencePatch.fold(die(s"task $task has no reference patch"))(IO.pure)
          src = root / "base" / task / "hidden" / os.RelPath(ts.hiddenSpec)
          _ <- check(os.isFile(src), s"no $src — run prepare.sh $task first")
          // a copied index carries the source tree's stat data; git apply -3 would see every file as changed
          _ <- Proc.capture(Seq("git", "-C", cv.toString, "update-index", "-q", "--refresh"), cv).void.handleError(_ => ())
          applied <- applyStep(cv, exp.tasksDir / task / os.RelPath(patch), task, root / s"chain-apply-$task.log")
          next <-
            if !applied then IO.pure(acc.copy(ap = false))
            else
              erosion.measure(cv, h.toString).flatMap {
                case Left(m) => err(m) *> die(s"erosion on the reference after $task failed")
                case Right(fig) =>
                  IO.blocking(os.write.append(refTmp, s"""${acc.sep}{"task": "$task", ${fig.pyJson.drop(1)}""")) *>
                    loop(more, acc.copy(
                      specs = acc.specs :+ (ts.hiddenSpec -> src),
                      tmods = if acc.tmods.contains(ts.module) then acc.tmods else acc.tmods :+ ts.module,
                      sep = ", "))
              }
        yield next

    for
      _ <- check(steps.nonEmpty, "usage: prepare-chain.sh <task>...")
      _ <- check(os.isDir(h / ".git"), s"no $h — run warm-cache.sh first")
      _ <- check(os.isFile(root / "head-baseline.json"), s"no ${root / "head-baseline.json"} — run score.py --baseline first")
      _ <- IO.blocking {
        os.remove(cvf, checkExists = false); os.remove(ref, checkExists = false)
        os.remove.all(cv); Files.copyTree(h, cv, _ == "out") // GA: cp -R then rm -rf out
        os.write.over(refTmp, "[")
      }
      acc <- loop(steps, ChainAcc(ap = true, Nil, Nil, ""))
      _ <- IO.blocking(os.write.append(refTmp, "]\n"))
      rp <-
        if !acc.ap then IO.blocking(os.remove(refTmp, checkExists = false)).as(false)
        else
          for
            _ <- IO.blocking {
              os.move(refTmp, ref, replaceExisting = true)
              acc.specs.foreach { (hs, src) =>
                val dst = cv / os.RelPath(hs)
                os.makeDir.all(dst / os.up); os.copy.over(src, dst)
              }
            }
            mods <- baselineModules(root).map(m => (m.toSet ++ acc.tmods).toList.sorted)
            ho = root / "chain-holdout"
            hod <- holdout(exp, ho, err)
            ok <- allThenHoldout(target, cv, mods, 2400, hod, root / "chain-reference-all.log")
            _ <- rm(ho)
          yield ok
      _ <- rm(cv)
      text = s"patches_apply=${yn(acc.ap)}\nreference_passes_all=${yn(rp)}\n"
      _ <- IO.blocking(os.write.over(cvf, text))
      _ <- say(text.stripSuffix("\n"))
    yield acc.ap && rp

  /** GA `( cd cv && git apply -3 <patch> && git add -A && git commit -qm <task> ) >log 2>&1`. */
  private def applyStep(cv: os.Path, patch: os.Path, task: String, log: os.Path): IO[Boolean] =
    val cmds = List(Seq("git", "apply", "-3", patch.toString), Seq("git", "add", "-A"), Seq("git") ++ Gate ++ Seq("commit", "-qm", task))
    cmds.zipWithIndex.foldLeft(IO.pure(true)) { case (acc, (cmd, i)) =>
      acc.flatMap(ok => if !ok then IO.pure(false) else Proc.run(cmd, cv, log, appendLog = i > 0).map(_.rc == 0))
    }
