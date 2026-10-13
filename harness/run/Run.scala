package harness.run

import cats.effect.IO
import cats.effect.std.Console
import cats.syntax.all.*
import harness.agent.{Agent, RunCtx}
import harness.config.{Experiment, TaskSpec}
import harness.target.Target

/** One evaluation run: a port of GA run.sh, in its order. */
object Run:
  /** GA ga_run_id: the arm list is the experiment's arms, reps 1..9. */
  def runId(task: String, arm: String, rep: Int, arms: Set[String]): Either[String, String] =
    if !arms(arm) then Left(s"arm must be one of ${arms.toList.sorted.mkString(", ")}, got $arm")
    else if rep < 1 || rep > 9 then Left(s"rep must be 1..9, got $rep")
    else Right(s"$task-$arm-$rep")

  def apply(exp: Experiment, task: String, arm: String, rep: Int, seed: Option[os.Path]): IO[Unit] =
    val adapters =
      for
        _ <- runId(task, arm, rep, exp.armSpecs.keySet)
        spec = exp.armSpecs(arm)
        agent <- exp.agents.get(spec.agent).toRight(s"arms.$arm.agent: unknown agent ${spec.agent}").flatMap(Agent.of)
        target <- Target.of(exp.target)
      yield (agent, target)
    adapters.fold(die, (agent, target) => withAdapters(exp, task, arm, rep, seed, agent, target))

  private def die[A](msg: String): IO[A] = IO.raiseError(HarnessError(msg))
  private def check(ok: Boolean, msg: => String): IO[Unit] = if ok then IO.unit else die(msg)
  // GA: the failing tool's own stderr reaches the console, then ga_die's text
  private def orDie[A](io: IO[A], msg: => String): IO[A] =
    io.handleErrorWith(e => Console[IO].errorln(e.getMessage) *> die(msg))
  private def lockedDir(d: os.Path): Boolean =
    os.isDir(d) && !(java.nio.file.Files.isReadable(d.toNIO) && java.nio.file.Files.isExecutable(d.toNIO))
  private def epoch: IO[Long] = IO.realTime.map(_.toSeconds)
  private val Gate = Seq("-c", "user.name=gate-eval", "-c", "user.email=gate-eval@local", "-c", "commit.gpgsign=false")
  private val OutDirs = List("out", "out-agent", "out-holdout")

  /** The run copy after setup (GA run.sh:7-56): what the build-and-agent phase needs. */
  final case class Setup(ctx: RunCtx, seedSha: ujson.Value, settingsSha: ujson.Value)

  /** GA run.sh:7-56: run copy, prompt, snapshot check, holdout, agent config, the arm's setup steps, settings artefact,
    * `arm <name>` commit and base tag. Nothing is built and no agent starts (arm-setup parity drives this alone).
    */
  def setupOnly(exp: Experiment, task: String, arm: String, rep: Int, seed: Option[os.Path], agent: Agent): IO[Setup] =
    for
      id <- runId(task, arm, rep, exp.armSpecs.keySet).fold(die, IO.pure)
      ts <- TaskSpec.load(exp.tasksDir, task).fold(die, IO.pure)
      spec = exp.armSpecs(arm)
      base = exp.root / "base" / task
      // final review M4: a hard crash (no finalizer) leaves base/ at 000, which would read as "not prepared"
      _ <- check(!List(exp.root / "base", base).exists(lockedDir), "base is locked (a run crashed?): run `harness unlock`")
      _ <- check(os.isDir(base / "tree" / ".git"), s"task $task not prepared (run prepare.sh $task)")
      rd = exp.root / "runs" / id
      repo = rd / "repo"
      _ <- IO.blocking { os.remove.all(rd); os.makeDir.all(rd) }
      ssha <- seed.fold(IO.blocking(Files.copyTree(base / "tree", repo, _ => false)).as(ujson.Null: ujson.Value))(fromSeed(_, base, rd))
      _ <- IO.blocking(os.copy.over(exp.tasksDir / task / "prompt.txt", rd / "prompt.txt"))
      // Ruling 22: the campaign's snapshot (campaign writes it once), never the live file
      snap = exp.root / "gated-settings.json"
      gsha <- if !spec.settingsSnapshot then IO.pure(ujson.Null: ujson.Value) else snapshotSha(snap)
      hd <- if !spec.holdout then IO.pure(None) else exportHoldout(exp, rd).map(Some(_))
      ctx = RunCtx(exp, spec, ts, id, rd, repo, hd.map(os.Path(_)), Option.when(spec.settingsSnapshot)(snap))
      _ <- agent.setup(ctx).adaptError { case e if !e.isInstanceOf[HarnessError] => HarnessError(e.getMessage) }
      vars = Map("repo" -> repo.toString, "template" -> exp.template.toString, "model" -> spec.model, "home" -> os.home.toString) ++
        hd.map("holdout" -> _)
      _ <- Steps(spec.setup, vars, repo, arm)
      _ <- agent.afterSteps(ctx).adaptError { case e if !e.isInstanceOf[HarnessError] => HarnessError(e.getMessage) }
      _ <- IO.blocking(agent.settingsArtefact(ctx).foreach(p => os.copy.over(p, rd / "settings.json")))
      _ <- gitOk(Seq("git") ++ Gate ++ Seq("commit", "-qam", s"arm $arm", "--allow-empty"), repo, s"arm $arm: commit failed")
      _ <- gitOk(Seq("git", "tag", "base"), repo, s"arm $arm: tag base failed")
    yield Setup(ctx, ssha, gsha)

  def withAdapters(
      exp: Experiment, task: String, arm: String, rep: Int, seed: Option[os.Path],
      agent: Agent, target: Target, say: String => IO[Unit] = IO.println
  ): IO[Unit] =
    for
      setup <- setupOnly(exp, task, arm, rep, seed, agent)
      Setup(ctx, ssha, gsha) = setup
      id = ctx.runId
      spec = ctx.arm
      rd = ctx.runDir
      repo = ctx.repo
      // Ruling 19: compile the run's own tree into the arm's out dir before the agent starts (outside wall time, before locking)
      p0 <- epoch
      pr <- target.runBuild(repo, target.precompileArgs, spec.outDir, rd / "precompile.log", cap = Some(exp.precompileCapSeconds))
      pok = pr.rc == 0
      // Final review C1: a chained step whose inherited tree does not build still gets its agent
      _ <- check(pok || seed.isDefined, s"precompile failed (see $rd/precompile.log)")
      pcs <- epoch.map(_ - p0)
      // Isolation: while the agent runs, base/ (hidden specs), head/ (the solution) and other runs are unreadable
      lockDirs <- IO.blocking {
        val runs = exp.root / "runs"
        val others = if os.isDir(runs) then os.list(runs).filterNot(_.last.startsWith(".")).sortBy(_.last).toList else Nil
        (List(exp.root / "base", exp.root / "head") ++ others).filter(os.isDir(_)).filterNot(_ == rd)
      }
      prompt <- IO.blocking(os.read(rd / "prompt.txt").reverse.dropWhile(_ == '\n').reverse) // "$(cat prompt.txt)"
      times <- Lock.locked(lockDirs, rd / "modes.txt").use { _ =>
        for
          _ <- agent.wipeState(ctx)
          start <- epoch
          startMs <- IO.realTime.map(_.toMillis)
          r <- Proc.run(agent.command(ctx, prompt), repo, rd / "transcript.jsonl", agent.env(ctx), Some(exp.capSeconds),
            stderrTo = Some(rd / "agent-stderr.log"))
          end <- epoch
          _ <- agent.collect(ctx, startMs)
        yield (start, end, r.rc)
      }
      (start, end, rc) = times
      _ <- IO.blocking(os.write.over(rd / "run.json", ujson.write(ujson.Obj(
        "run_id" -> id, "task" -> task, "arm" -> arm, "rep" -> rep, "start_epoch" -> ujson.Num(start.toDouble), "end_epoch" -> ujson.Num(end.toDouble),
        "exit_code" -> rc, "timed_out" -> (rc == 124), "precompile_seconds" -> ujson.Num(pcs.toDouble), "precompile_ok" -> pok,
        "seed_sha256" -> ssha, "gated_settings_sha256" -> gsha, "agent" -> spec.agent, "model" -> spec.model
      )) + "\n"))
      _ <- finalDiff(repo, rd / "final.diff")
      _ <- Files.tarGz(rd, "repo", rd / "final-tree.tar.gz", OutDirs.map(d => s"repo/$d"))
        .handleErrorWith(e => Console[IO].errorln(e.getMessage))
      _ <- say(s"run $id: exit $rc, ${end - start} s (precompile $pcs s)")
    yield ()

  /** GA run.sh:12-22: the previous step's final tree, re-based as a fresh repository. */
  private def fromSeed(seed: os.Path, base: os.Path, rd: os.Path): IO[ujson.Value] =
    val repo = rd / "repo"
    for
      _ <- check(os.isFile(seed), s"seed tree $seed missing")
      _ <- orDie(Files.untarGz(seed, rd), s"unpack $seed")
      _ <- check(os.isDir(repo), s"seed tree $seed has no repo/")
      // Final review I1: the previous step's gate state, caches and .claude stay behind; .dsh and .claude are the task base's
      _ <- IO.blocking(List(".git", ".dsh", ".claude").foreach(d => os.remove.all(repo / d)))
      _ <- List(".dsh", ".claude").traverse_ { d =>
        val src = base / "tree" / d
        if !os.isDir(src) then IO.unit
        else orDie(IO.blocking(Files.copyTree(src, repo / d, _ => false)), s"restore $d from ${base / "tree"}")
      }
      h <- orDie(IO.blocking(Files.sha256(seed)), s"hash $seed")
      _ <- orDie(Files.gitInitCommit(repo, "base"), "seed tree: git init failed")
    yield ujson.Str(h)

  /** GA run.sh:34-38. */
  private def snapshotSha(snap: os.Path): IO[ujson.Value] =
    val shaFile = os.Path(s"$snap.sha256")
    for
      _ <- check(os.isFile(snap), s"no gated settings snapshot $snap (campaign.sh writes it)")
      h <- orDie(IO.blocking(Files.sha256(snap)), s"hash $snap")
      want <- IO.blocking(Option.when(os.isFile(shaFile))(os.read(shaFile).reverse.dropWhile(_ == '\n').reverse))
      _ <- check(want.forall(_ == h), s"$snap does not match $snap.sha256")
    yield ujson.Str(h)

  /** GA run.sh:39-40: git archive of the holdout commit into <run>/holdout; returns its physical path. */
  private def exportHoldout(exp: Experiment, rd: os.Path): IO[String] =
    orDie(Files.gitArchive(exp.target.holdoutRepo, exp.target.holdoutCommit, rd / "holdout"), "holdout export") *>
      IO.blocking((rd / "holdout").toNIO.toRealPath().toString)

  private def gitOk(cmd: Seq[String], repo: os.Path, msg: String): IO[Unit] =
    Proc.capture(cmd, repo).flatMap((rc, out) => check(rc == 0, s"$msg: ${out.trim}"))

  /** GA run.sh:107: `git add -A -N . && git diff base -- . ':!out' ':!out-agent' ':!out-holdout' > final.diff`. */
  private def finalDiff(repo: os.Path, dest: os.Path): IO[Unit] = IO.blocking {
    val add = os.proc("git", "add", "-A", "-N", ".").call(cwd = repo, stdin = os.Path("/dev/null"), stderr = os.Inherit, check = false)
    if add.exitCode == 0 then
      os.proc(Seq("git", "diff", "base", "--", ".") ++ OutDirs.map(d => s":!$d"))
        .call(cwd = repo, stdin = os.Path("/dev/null"), stdout = dest, stderr = os.Inherit, check = false)
    else os.write.over(dest, "")
    ()
  }
