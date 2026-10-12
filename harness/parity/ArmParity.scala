package harness.parity

import cats.effect.IO
import cats.syntax.all.*
import harness.agent.Agent
import harness.config.Experiment
import harness.run.{Files, HarnessError, Run}

/** Parity spec §7.4: for each arm, the run copy after setup through GA (run.sh's own setup lines, sourced with lib.sh,
  * stopped after the base tag) and through [[Run.setupOnly]], in the same run dir one after the other; the agent's
  * settings, `.mcp.json`, the `.dsh/` tree (minus `.dsh/.state`) and the run dir's settings artefact compared byte for byte.
  */
object ArmParity:
  val Arms: List[String] = List("u", "g", "t", "gf", "gs", "p")

  /** GA run.sh from its argument check through `git tag base` (the setup phase), with lib.sh sourced as run.sh does. */
  def gaSetupScript(ga: os.Path): String =
    val lines = os.read.lines(ga / "run.sh").toList
    val start = lines.indexWhere(_.startsWith("[ $# -eq 3 ]"))
    val end = lines.indexWhere(_.contains("&& git tag base )"))
    if start < 0 || end < start then throw HarnessError(s"parity-arms: cannot find the setup phase in ${ga / "run.sh"}")
    (List("set -u", s"GA_LIB_PATH='${ga / "lib.sh"}'; . \"$$GA_LIB_PATH\"") ++ lines.slice(start, end + 1)).mkString("\n") + "\n"

  /** The compared files of one set-up run dir, relative path -> bytes. */
  def artefacts(rd: os.Path): Map[String, Seq[Byte]] =
    val repo = rd / "repo"
    val dsh = repo / ".dsh"
    val dshFiles =
      if !os.isDir(dsh) then Nil
      else os.walk(dsh, skip = _ == dsh / ".state", followLinks = false).filter(os.isFile(_, followLinks = false))
    val files = List(repo / ".claude" / "settings.json", repo / ".mcp.json", rd / "settings.json") ++ dshFiles
    files.filter(os.exists(_, followLinks = false)).map(p => p.relativeTo(rd).toString -> os.read.bytes(p).toSeq).toMap

  def compare(arm: String, ga: Map[String, Seq[Byte]], mine: Map[String, Seq[Byte]]): List[String] =
    (ga.keySet ++ mine.keySet).toList.sorted.flatMap { k =>
      (ga.get(k), mine.get(k)) match
        case (Some(a), Some(b)) => if a == b then Nil else List(s"arm $arm: $k differs")
        case (Some(_), None)    => List(s"arm $arm: $k only in GA's setup")
        case (None, Some(_))    => List(s"arm $arm: $k only in the harness setup")
        case (None, None)       => Nil
    }

  /** `baseTree`: a prepared task tree (a copy of `<root>/base/<task>/tree`); `snapshot`: a copy of the gated settings
    * snapshot. Everything is built under `work`. -> (arms compared, files compared, mismatches).
    */
  def apply(exp0: Experiment, ga: os.Path, work: os.Path, task: String, baseTree: os.Path, snapshot: os.Path,
            say: String => IO[Unit]): IO[(Int, Int, List[String])] =
    val root = work / "root"
    val exp = exp0.copy(root = root)
    val prepare = IO.blocking {
      os.remove.all(work)
      Files.copyTree(baseTree, root / "base" / task / "tree", _ => false)
      os.copy(snapshot, root / "gated-settings.json", createFolders = true)
      os.write(root / "gated-settings.json.sha256", Files.sha256(root / "gated-settings.json") + "\n")
    }
    val env = Map("GA_ROOT" -> root.toString)
    def gaSetup(arm: String): IO[Map[String, Seq[Byte]]] = IO.blocking {
      val r = os.proc("sh", "-c", gaSetupScript(ga), "run.sh", task, arm, "1")
        .call(cwd = work, env = env, check = false, stdout = os.Pipe, stderr = os.Pipe, stdin = os.Path("/dev/null"))
      if r.exitCode != 0 then throw HarnessError(s"parity-arms: GA setup of arm $arm failed (rc ${r.exitCode}): ${r.err.text().trim}")
      artefacts(root / "runs" / s"$task-$arm-1")
    }
    def harnessSetup(arm: String): IO[Map[String, Seq[Byte]]] =
      for
        spec <- exp.armSpecs.get(arm).fold(IO.raiseError(HarnessError(s"parity-arms: arm $arm not in the experiment")))(IO.pure)
        agent <- exp.agents.get(spec.agent).toRight(s"unknown agent ${spec.agent}").flatMap(Agent.of)
          .fold(m => IO.raiseError(HarnessError(m)), IO.pure)
        s <- Run.setupOnly(exp, task, arm, 1, None, agent)
      yield artefacts(s.ctx.runDir)
    for
      _ <- prepare
      res <- Arms.traverse { arm =>
        for
          a <- gaSetup(arm)
          b <- harnessSetup(arm)
          ms = compare(arm, a, b)
          _ <- say(s"arm $arm: ${a.size} GA files, ${b.size} harness files, ${if ms.isEmpty then "equal" else s"${ms.size} mismatches"}")
        yield (a.size max b.size, ms)
      }
    yield (Arms.size, res.map(_._1).sum, res.flatMap(_._2))
