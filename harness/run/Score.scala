package harness.run

import cats.effect.{IO, Resource}
import harness.agent.Agent
import harness.config.{ArmSpec, Experiment, TaskSpec}
import harness.core.{Contamination, Diff, Fence, Gate, Json, ToolCall, ToolKind, Verdicts}
import harness.target.{ScoreError, Target}
import scala.collection.immutable.ListMap
import scala.collection.mutable

/** One run's verdict and the HEAD baseline. Ports GA/score.py:684-901 (`baseline`, `_export_holdout`, `_read_baseline`,
  * `main`, `errors_have`) plus `first_clean_compile` and the glue to the already-ported pure parts.
  */
object Score:
  /** GA arms whose hook events are allowed (env_leaks). Used only for an arm the experiment does not define. */
  private val GatedArms = Set("g", "t", "gf", "gs", "p")
  private val DefaultAgent = "claude-code"

  def apply(exp: Experiment, runDir: os.Path): IO[ujson.Obj] =
    Target.of(exp.target).fold(m => IO.raiseError(HarnessError(m)), withAdapters(exp, runDir, _, agentOf(exp)))

  def baseline(exp: Experiment): IO[Map[String, Boolean]] =
    Target.of(exp.target).fold(m => IO.raiseError(HarnessError(m)), baselineWith(exp, _))

  private def agentOf(exp: Experiment)(name: String): Either[String, Agent] =
    exp.agents.get(name).toRight(s"run.json agent $name: not in the experiment").flatMap(Agent.of)

  // ---------------------------------------------------------------- pure helpers

  /** GA first_clean_compile: the turn of the first shell call that compiles cleanly. */
  def firstCleanCompile(calls: Seq[ToolCall], isCompileCommand: String => Boolean): Option[Int] =
    calls.find(c => c.kind == ToolKind.Shell && isCompileCommand(c.command) && !c.isError && !c.resultText.contains("[error]"))
      .map(_.turn)

  def errorsHave(errors: Seq[String], prefixes: String*): Boolean =
    errors.exists(e => prefixes.exists(e.startsWith))

  /** GA _last_line: the last non-blank line of a log, ANSI stripped, at most 200 chars. */
  private def lastLine(log: os.Path): String =
    val ls = Json.Ansi.replaceAllIn(Json.readLenient(log), "").linesIterator.filter(_.trim.nonEmpty).toList
    ls.lastOption.fold("(no output)")(_.trim.take(200))

  /** Python repr of a str. */
  private def pyStrRepr(s: String): String =
    val q = if s.contains('\'') && !s.contains('"') then '"' else '\''
    val b = new StringBuilder().append(q)
    s.foreach {
      case '\\'                 => b.append("\\\\")
      case c if c == q          => b.append('\\').append(c)
      case '\n'                 => b.append("\\n")
      case '\r'                 => b.append("\\r")
      case '\t'                 => b.append("\\t")
      case c if c < ' ' || c == '\u007f' => b.append(f"\\x${c.toInt}%02x")
      case c                    => b.append(c)
    }
    b.append(q).toString

  /** GA `repr(e)` in the catch-all record: the Python exception the same failure raises there. */
  def pyRepr(e: Throwable): String = e match
    case _: ScoreError                                => s"ScoreError(${pyStrRepr(e.getMessage)})"
    case _: (ujson.ParseException | ujson.IncompleteParseException) => s"JSONDecodeError(${pyStrRepr(e.getMessage)})"
    case _: java.nio.file.NoSuchFileException         => "FileNotFoundError(2, 'No such file or directory')"
    case x: NoSuchElementException if Option(x.getMessage).exists(_.startsWith("key not found: ")) =>
      s"KeyError(${pyStrRepr(x.getMessage.stripPrefix("key not found: "))})"
    case _ => s"${e.getClass.getSimpleName}(${pyStrRepr(Option(e.getMessage).getOrElse(""))})"

  private def truthy(v: ujson.Value): Boolean = v match
    case ujson.Null | ujson.False => false
    case ujson.True               => true
    case ujson.Num(d)             => d != 0
    case ujson.Str(s)             => s.nonEmpty
    case ujson.Arr(a)             => a.nonEmpty
    case ujson.Obj(o)             => o.nonEmpty

  /** Python int() of a run.json number. */
  private def pyInt(v: ujson.Value): Long = v match
    case ujson.Num(d) => d.toLong
    case ujson.Str(s) => s.trim.toLong
    case ujson.True   => 1
    case ujson.False  => 0
    case other        => throw new IllegalArgumentException(s"int() argument must be a number, not $other")

  private def num(v: Option[Double]): ujson.Value = v.fold(ujson.Null: ujson.Value)(ujson.Num(_))
  private def strArr(xs: Seq[String]): ujson.Arr = ujson.Arr.from(xs.map(ujson.Str(_)))

  /** GA's arm semantics for an arm the experiment does not define (an old root's arm). */
  private[harness] def armSpec(exp: Experiment, arm: String, agent: String, model: String): ArmSpec =
    exp.armSpecs.getOrElse(arm, ArmSpec(arm, agent, model, "out", holdout = false, settingsSnapshot = false,
      hooksAllowed = GatedArms(arm), turnEndForbidden = arm == "t", setup = Nil))

  /** GA _read_baseline: (modules, passes) or None. */
  private def readBaseline(root: os.Path): Option[(mutable.Map[String, ujson.Value], ujson.Value)] =
    Json.readJson(root / "head-baseline.json").collect { case b: ujson.Obj => b }.flatMap { b =>
      b.value.get("modules").collect { case m: ujson.Obj if m.value.nonEmpty => (m.value, b.value.getOrElse("passes", ujson.Null)) }
    }

  // ---------------------------------------------------------------- effects

  /** GA _export_holdout: `git archive <commit>` of the holdout repo into dest. */
  def exportHoldout(src: os.Path, commit: String, dest: os.Path): IO[Unit] =
    Resource.make(IO.blocking(os.temp(suffix = ".tar")))(t => IO.blocking(os.remove(t))).use { tar =>
      def step(cmd: Seq[String], cwd: os.Path): IO[Unit] = Proc.capture(cmd, cwd).flatMap {
        case (0, _)   => IO.unit
        case (_, out) => IO.raiseError(ScoreError("holdout export failed: " + out.trim.take(200)))
      }
      IO.blocking(os.makeDir.all(dest)) *>
        step(Seq("git", "-C", src.toString, "archive", "-o", tar.toString, commit), dest) *>
        step(Seq("tar", "-x", "-f", tar.toString, "-C", dest.toString), dest)
    }

  private def tempHoldout: Resource[IO, os.Path] =
    Resource.make(IO.blocking(os.temp.dir(prefix = "gate-holdout-", deleteOnExit = false)))(d => IO.blocking(os.remove.all(d)))

  private def task(exp: Experiment, name: String): IO[TaskSpec] =
    TaskSpec.load(exp.tasksDir, name) match
      case Right(t) => IO.pure(t)
      case Left(m) if List("TASK_MODULE", "HIDDEN_SPEC", "module", "hidden-spec").exists(k => m.endsWith(s": missing $k")) =>
        IO.raiseError(ScoreError(s"task.env for $name has empty TASK_MODULE/HIDDEN_SPEC"))
      case Left(m) => IO.raiseError(ScoreError(m))

  /** The fence GA's contamination() derives from GA_ROOT / GA_SKREEEP2 / HOME / CLAUDE_CONFIG_DIR. */
  private[harness] def fence(exp: Experiment, rd: os.Path, agentDirs: (List[os.Path], List[os.Path])): Fence =
    val repo = rd / "repo"
    val home = os.Path(sys.env.getOrElse("HOME", os.home.toString))
    val root = exp.root
    Fence(
      runDir = rd,
      repo = repo,
      forbidden = List(root / "base", root / "head", exp.target.repo, root / "runs") ++ agentDirs._1 ++
        List(rd / "holdout", repo / "out-holdout", repo / ".dsh" / ".state" / "holdout.log"),
      ownAllowed = agentDirs._2,
      allowedPrefixes = Contamination.defaultAllowedPrefixes ++
        List(".cache", "Library/Caches", ".local/bin", ".mill").map(p => (home / os.RelPath(p)).toString),
      home = home)

  /** Final review I4: a run without a transcript (`resultFromExit`) has no tool calls to check, so `contaminated`,
    * `contamination`, `gate_blocks` and `env_leaks` (only such runs carry it, after `gate_blocks`) are null: unchecked,
    * not clean. Report shows them as unchecked.
    */
  private[harness] def uncheckedFacts(rec: ujson.Obj): Unit =
    val later = rec.value.keys.toList.dropWhile(_ != "gate_blocks").drop(1).filter(_ != "env_leaks")
    val saved = later.map(k => k -> rec(k))
    later.foreach(rec.value.remove)
    Unchecked.foreach(rec(_) = ujson.Null) // existing keys keep their place; env_leaks lands after gate_blocks
    saved.foreach((k, v) => rec(k) = v)

  val Unchecked = List("contaminated", "contamination", "gate_blocks", "env_leaks")

  /** GA main(run_dir): judge one finished run, write <run-dir>/score.json (partially first, then final). */
  def withAdapters(exp: Experiment, runDir: os.Path, target: Target, agentFor: String => Either[String, Agent]): IO[ujson.Obj] =
    IO.defer {
      val rd = runDir
      val rec = ujson.Obj(
        "run_id" -> rd.last, "task" -> ujson.Null, "arm" -> ujson.Null, "rep" -> ujson.Null, "solved" -> false,
        "hidden_pass_ratio" -> 0.0, "hidden_compiled" -> false, "holdout_passed" -> false, "holdout_failed" -> 0,
        "regressions" -> ujson.Arr(), "regressed_modules" -> ujson.Arr(), "regression_count" -> 0,
        "test_tampering" -> ujson.Arr(), "hardcoding_suspects" -> ujson.Arr(), "build_edits" -> ujson.Arr(),
        "false_done" -> false, "timed_out" -> false, "contaminated" -> false, "contamination" -> ujson.Arr(),
        "gate_blocks" -> ujson.Obj(), "wall_seconds" -> 0, "turns" -> ujson.Null, "input_tokens" -> ujson.Null,
        "output_tokens" -> ujson.Null, "cache_read_tokens" -> ujson.Null, "cost_usd" -> ujson.Null,
        "first_clean_compile_turn" -> ujson.Null, "score_timeouts" -> ujson.Arr(), "error" -> "scoring incomplete")
      val errors = mutable.ListBuffer.empty[String]
      val write = IO.blocking(Json.writeJsonAtomic(rd / "score.json", rec))
      main(exp, rd, target, agentFor, rec, errors, write)
        .handleError { e =>
          rec("solved") = false
          rec("error") = (errors.toList :+ pyRepr(e)).mkString("; ")
        }
        .onCancel(IO { // GA KeyboardInterrupt: recorded, then re-raised
          rec("solved") = false
          rec("error") = (errors.toList :+ "KeyboardInterrupt()").mkString("; ")
        })
        .guarantee(write)
        .as(rec)
    }

  private def main(
      exp: Experiment, rd: os.Path, target: Target, agentFor: String => Either[String, Agent],
      rec: ujson.Obj, errors: mutable.ListBuffer[String], writePartial: IO[Unit]
  ): IO[Unit] =
    val root = exp.root
    val repo = rd / "repo"
    val tree = rd / "score-tree"
    for
      run <- IO.blocking(ujson.read(os.read(rd / "run.json")))
      _ <- IO { // GA rec.update(...): every value read before any is set
        val vs = List("run_id" -> run("run_id"), "task" -> run("task"), "arm" -> run("arm"), "rep" -> run("rep"),
          "timed_out" -> ujson.Bool(truthy(run("timed_out"))),
          "wall_seconds" -> ujson.Num((pyInt(run("end_epoch")) - pyInt(run("start_epoch"))).toDouble))
        vs.foreach((k, v) => rec(k) = v)
      }
      timedOut = rec("timed_out").bool
      taskName = run("task").str
      ts <- task(exp, taskName)
      // Review Focus 4: an old run.json has no agent / model; GA's agent is claude-code
      agentName = run.obj.get("agent").flatMap(_.strOpt).getOrElse(DefaultAgent)
      agent <- agentFor(agentName).fold(m => IO.raiseError(ScoreError(m)), IO.pure)
      arm = armSpec(exp, run("arm").str, agentName, run.obj.get("model").flatMap(_.strOpt).getOrElse(""))

      // transcript-derived: never lost to a later build failure
      t0 <- IO.blocking(agent.parse(Json.readLenient(rd / "transcript.jsonl").linesIterator))
      ctx = harness.agent.RunCtx(exp, arm, ts, rd.last, rd, repo, None, None)
      // dsh: tokens of the run's subagent sessions (none for the other agents)
      t <- agent.withDelegated(ctx, t0, run.obj.get("start_epoch").flatMap(_.numOpt).map(e => (e * 1000).toLong))
      contamination <- IO.blocking {
        val f = fence(exp, rd, agent.privateStateDirs(ctx))
        Contamination.check(t.toolCalls, f) ++ Contamination.check(t.delegatedCalls, f).map("subagent-session " + _)
      }
      diff <- IO.blocking(Json.readLenient(rd / "final.diff"))
      hiddenSrc = root / "base" / taskName / "hidden" / os.RelPath(ts.hiddenSpec)
      hiddenText <- IO.blocking(Json.readLenient(hiddenSrc))
      _ <- IO {
        rec("turns") = num(t.turns.map(_.toDouble)); rec("input_tokens") = num(t.inputTokens.map(_.toDouble))
        rec("output_tokens") = num(t.outputTokens.map(_.toDouble)); rec("cache_read_tokens") = num(t.cacheReadTokens.map(_.toDouble))
        rec("cost_usd") = num(t.costUsd)
        val hasResult = if agent.resultFromExit then exitCode(run("exit_code")) == 0 else t.hasResult
        if !hasResult && !timedOut then errors += "no result event" // a timeout is an outcome, not a scorer error (Ruling 14)
        t.apiError.filter(_ => !timedOut).foreach(a => errors += "agent api error: " + a.take(120)) // Ruling 26
        val leaks = agent.envLeaks(t, arm)
        if leaks.nonEmpty then errors += "agent environment leaked: " + leaks.mkString("; ") // Ruling 20
        rec("first_clean_compile_turn") = num(firstCleanCompile(t.toolCalls, target.isCompileCommand).map(_.toDouble))
        if agent.resultFromExit then uncheckedFacts(rec) // final review I4: no transcript, nothing was checked
        else
          rec("gate_blocks") = ujson.Obj.from(Gate.gateBlocks(t.toolCalls, t.hookTexts).map((k, v) => k -> ujson.Num(v)))
          rec("contamination") = strArr(contamination); rec("contaminated") = contamination.nonEmpty
        rec("test_tampering") = strArr(Diff.tampering(diff, target.isTestSource))
        rec("build_edits") = strArr(Diff.buildEdits(diff, target.isBuildFile))
        rec("hardcoding_suspects") = strArr(Diff.hardcoding(diff, hiddenText))
      }
      _ <- writePartial // error stays "scoring incomplete" until the end
      fq <- target.suiteName(hiddenText).fold(IO.raiseError(ScoreError("no `object X extends ...Suite` in the hidden spec " + ts.hiddenSpec)))(IO.pure)

      // scoring copy: no build output (any top-level out dir), compiled cold under its own out-score
      _ <- IO.blocking {
        os.remove.all(tree)
        Files.copyTree(repo, tree, target.outDirs)
        os.copy.over(hiddenSrc, tree / os.RelPath(ts.hiddenSpec), createFolders = true)
      }

      // hidden: ONLY the hidden spec's suite (Ruling 11)
      hlog = rd / "hidden-score.log"
      hr <- target.runBuild(tree, target.hiddenRunArgs(ts.module, fq), "out-score", hlog, cap = Some(exp.testCapSeconds))
      hc <- IO.blocking(target.hiddenCounts(Json.readLenient(hlog), fq))
      failed <- IO {
        if hr.timedOut then rec("score_timeouts").arr += ts.module
        hiddenFailed(hc, hr, lastLine(hlog), errors)
      }
      compiled = hc.compiled
      passed = hc.passed
      _ <- IO {
        rec("hidden_compiled") = compiled
        rec("hidden_pass_ratio") = if compiled && passed + failed > 0 then passed.toDouble / (passed + failed) else 0.0
      }
      hiddenOk = compiled && passed > 0 && !errorsHave(errors.toList, "mill failed", "hidden suite did not run")

      // holdout
      hoLog = rd / "holdout-score.log"
      ho <- tempHoldout.use { d =>
        exportHoldout(exp.target.holdoutRepo, exp.target.holdoutCommit, d) *>
          target.runBuild(tree, target.holdoutArgs, "out-score", hoLog,
            target.holdoutEnv(os.Path(Contamination.realpath(d.toString))), Some(exp.testCapSeconds))
      }
      hcnt <- IO.blocking(target.testCounts(Json.readLenient(hoLog)))
      _ <- IO {
        if ho.timedOut then rec("score_timeouts").arr += "holdout.test"
        if ho.rc != 0 && !ho.timedOut && hcnt.compiled && hcnt.passed + hcnt.failed == 0 then
          errors += "mill failed (holdout): " + lastLine(hoLog)
        rec("holdout_passed") = ho.rc == 0 && hcnt.compiled && hcnt.failed == 0 && hcnt.passed == exp.target.holdoutExpected
        rec("holdout_failed") = Verdicts.holdoutFailedCount(hcnt.failed, hcnt.compiled, ho.timedOut)
      }

      // regressions: ONE invocation, keep going, every test module; the task module via its other suites (Ruling 12)
      reg <- regressions(exp, rd, target, ts.module, fq, rec, errors)
      _ <- IO {
        val (regressOk, regFailed) = reg
        rec("regressed_modules") = ujson.Arr.from(rec("regressions").arr)
        val regs = rec("regressions").arr.map(_.str).toList
        rec("regression_count") = Verdicts.regressionCount(regs, regFailed, rec("holdout_failed").num.toInt)
        rec("solved") = hiddenOk && regressOk && Verdicts.isSolved(compiled, passed, failed, rec("holdout_passed").bool, regs, timedOut)
        rec("false_done") = Verdicts.isFalseDone(rec("solved").bool, timedOut, exitCode(run("exit_code")))
        rec("error") = if errors.isEmpty then ujson.Null else ujson.Str(errors.mkString("; "))
      }
    yield ()

  /** GA main's hidden-suite verdict: failed count after GA's corrections; appends GA's errors. */
  private def hiddenFailed(c: harness.target.Counts, r: ProcResult, last: => String, errors: mutable.ListBuffer[String]): Int =
    val ran = c.passed + c.failed
    var failed = c.failed
    if c.compiled && r.rc != 0 && ran == 0 && !r.timedOut then errors += "mill failed: " + last
    else if c.compiled && ran == 0 && !r.timedOut then errors += "hidden suite did not run"
    else if c.compiled && r.rc != 0 then failed = math.max(failed, 1) // a hang or class-level failure may print no `-` line
    if c.compiled && r.timedOut then failed = math.max(failed, 1)
    failed

  /** Python `run["exit_code"] == 0`, as the Int Verdicts.isFalseDone compares. */
  private def exitCode(v: ujson.Value): Int = v match
    case ujson.Num(d) => if d == 0 then 0 else 1
    case ujson.False  => 0
    case _            => 1

  /** GA main's regression block -> (regress_ok, reg_failed); sets rec.regressions / score_timeouts, appends errors. */
  private def regressions(
      exp: Experiment, rd: os.Path, target: Target, taskModule: String, fq: String,
      rec: ujson.Obj, errors: mutable.ListBuffer[String]
  ): IO[(Boolean, Int)] =
    val tree = rd / "score-tree"
    IO.blocking(readBaseline(exp.root)).flatMap {
      case None =>
        errors += "no baseline (run score.py --baseline)"
        IO.pure((false, 0))
      case Some((_, passes)) if !passes.objOpt.exists(_.nonEmpty) =>
        errors += "baseline outdated, re-run --baseline"
        IO.pure((false, 0))
      case Some((base, passes)) =>
        target.testModules(tree, "out-score", rd / "resolve-score.log").attempt.flatMap {
          case Left(e: ScoreError) =>
            errors += e.getMessage
            IO.pure((false, 0))
          case Left(e) => IO.raiseError(e)
          case Right(Left(_)) => // the agent broke the build definition: every task fails, resolve included
            rec("regressions") = strArr(List("<build>"))
            IO.pure((true, 0))
          case Right(Right(mods)) =>
            for
              entries <- mods.foldLeft(IO.pure(List.empty[(String, Seq[String])])) { (acc, m) =>
                acc.flatMap { es =>
                  if m == taskModule then
                    target.otherSuites(tree, m, fq).map(o => if o.nonEmpty then es :+ (m -> target.regressionEntry(m, Some(o))) else es)
                  else IO.pure(es :+ (m -> target.regressionEntry(m, None)))
                }
              }
              failed <- if entries.isEmpty then IO.pure(0) else regressRun(exp, rd, target, mods, entries, passes, base, rec, errors)
            yield (true, failed)
        }
    }

  private def regressRun(
      exp: Experiment, rd: os.Path, target: Target, mods: List[String], entries: List[(String, Seq[String])],
      passes: ujson.Value, base: mutable.Map[String, ujson.Value], rec: ujson.Obj, errors: mutable.ListBuffer[String]
  ): IO[Int] =
    val tree = rd / "score-tree"
    val rlog = rd / "regress-score.log"
    def baseOk(i: String) = base.get(i).forall(truthy)
    for
      r <- target.runBuild(tree, target.joinTasks(entries.map(_._2)), "out-score", rlog, cap = Some(exp.regressCapSeconds), keepGoing = true)
      text <- IO.blocking(Json.readLenient(rlog))
      // module list only needed to attribute failures
      all <- if r.rc != 0 && !r.timedOut then
        target.allModules(tree, "out-score", rd / "resolve-all.log").attempt.flatMap {
          case Right(Right(ms))       => IO.pure((ms, false))
          case Right(Left(_))         => IO.pure((Nil, true))
          case Left(e: ScoreError)    => IO { errors += e.getMessage }.as((Nil, false))
          case Left(e)                => IO.raiseError(e)
        }
      else IO.pure((Nil, false))
      (allMods, broken) = all
      f = target.parseFailures(text, mods, allMods)
      ids = f.tests ++ f.compile ++ (if broken then Set("<build>") else Set.empty)
      attributed = ids.toList.filter(baseOk).sorted
    yield
      if r.timedOut then
        rec("score_timeouts").arr += "regressions"
        rec("regressions") = strArr(List("<timeout>"))
        0
      else if f.other.nonEmpty || (r.rc != 0 && ids.isEmpty) then
        errors += "mill failed (regressions): " + f.other.headOption.getOrElse(lastLine(rlog))
        rec("regressions") = strArr(if attributed.nonEmpty then attributed else List("<unattributed>"))
        0
      else
        val invoked = entries.map(_._1)
        val expected = invoked.map(m => passes.obj.get(m).flatMap(_.numOpt).getOrElse(0.0)).sum
        val ran = target.suitePasses(text).values.sum
        rec("regressions") = strArr(if expected != 0 && ran < 0.5 * expected then attributed :+ "<tests did not run>" else attributed)
        f.failed

  // ---------------------------------------------------------------- baseline

  /** GA baseline(ga_root): run every test module once at HEAD and cache pass/fail + pass counts in head-baseline.json.
    * Loud (ScoreError) on any doubt.
    */
  def baselineWith(exp: Experiment, target: Target): IO[Map[String, Boolean]] =
    val root = exp.root
    val head = root / "head"
    val log = root / "baseline.log"
    def loud[A](msg: String): IO[A] = IO.raiseError(ScoreError(msg))
    def orLoud(e: Either[harness.target.BuildBroken, List[String]]): IO[List[String]] = e.fold(b => loud(b.lastLine), IO.pure)
    for
      _ <- if os.isDir(head) then IO.unit else loud("no HEAD tree at " + head)
      t0 <- IO.realTime
      mods <- target.testModules(head, "out", root / "baseline-resolve.log").flatMap(orLoud)
      r <- target.runBuild(head, target.joinTasks(mods.map(target.regressionEntry(_, None))), "out", log,
        cap = Some(exp.regressCapSeconds), keepGoing = true)
      text <- IO.blocking(Json.Ansi.replaceAllIn(Json.readLenient(log), ""))
      _ <- if r.timedOut then loud(s"HEAD baseline timed out (see $log)") else IO.unit
      _ <- if text.contains("Compilation failed") then loud(s"HEAD does not compile (see $log)") else IO.unit
      all <- target.allModules(head, "out", root / "baseline-all.log").flatMap(orLoud)
      f = target.parseFailures(text, mods, all)
      _ <- if f.compile.nonEmpty || f.other.nonEmpty then
        loud(s"HEAD baseline failed for a non-test reason: ${f.other.headOption.getOrElse("build failure in " + f.compile.toList.sorted.mkString(", "))} (see $log)")
      else IO.unit
      _ <- if r.rc != 0 && f.tests.isEmpty then loud(s"HEAD baseline failed for a non-test reason: ${lastLine(log)} (see $log)") else IO.unit
      _ <- if target.testCounts(text).passed == 0 then loud(s"HEAD baseline executed no tests (see $log)") else IO.unit
      suites <- mods.foldLeft(IO.pure(ListMap.empty[String, List[String]]))((acc, m) => acc.flatMap(a => target.allSuites(head, m).map(s => a + (m -> s))))
      passes = target.modulePasses(target.suitePasses(text), suites)
      result = ListMap.from(mods.map(m => m -> !f.tests(m)))
      t1 <- IO.realTime
      _ <- IO.blocking(Json.writeJsonAtomic(root / "head-baseline.json", ujson.Obj(
        "modules" -> ujson.Obj.from(result.map((m, ok) => m -> ujson.Bool(ok))),
        "passes" -> ujson.Obj.from(mods.map(m => m -> ujson.Num(passes.getOrElse(m, 0).toDouble))),
        "wall_seconds" -> ujson.Num((t1 - t0).toSeconds.toDouble),
        "rc" -> r.rc,
        "computed_epoch" -> ujson.Num(t1.toSeconds.toDouble))))
    yield result

  /** GA `score.py --baseline` (__main__): module lines on success, `score.py --baseline: <msg>` and rc 1 on ScoreError. */
  def baselineCli(exp: Experiment, target: Target, out: String => IO[Unit], err: String => IO[Unit]): IO[Int] =
    baselineWith(exp, target).attempt.flatMap {
      case Left(e: ScoreError) => err(s"score.py --baseline: ${e.getMessage}").as(1)
      case Left(e)             => IO.raiseError(e)
      case Right(res) =>
        res.toList.sortBy(_._1).foldLeft(IO.unit)((io, mo) => io *> out(f"${mo._1}%-28s ${if mo._2 then "pass" else "FAIL"}")).as(0)
    }
