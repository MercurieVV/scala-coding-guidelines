package harness.config

import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import scala.collection.mutable
import scala.util.Try

/** Run-time variables are written `{repo}` (no `$`), so HOCON leaves them alone. */
object Vars:
  val known: Set[String] = Set("repo", "holdout", "template", "model", "home")
  private val Placeholder = """\{([A-Za-z][A-Za-z0-9_-]*)\}""".r

  def names(s: String): List[String] = Placeholder.findAllMatchIn(s).map(_.group(1)).toList

  def expand(s: String, vars: Map[String, String]): Either[String, String] =
    names(s).find(n => !vars.contains(n)) match
      case Some(n) => Left(s"unknown or undefined variable {$n} in '$s'")
      case None => Right(Placeholder.replaceAllIn(s, m => scala.util.matching.Regex.quoteReplacement(vars(m.group(1)))))

object Load:
  private val Sections = Set("harness", "target", "agents", "arms")

  def experiment(file: os.Path, overrides: Map[String, String] = Map.empty): Either[String, Experiment] =
    for
      _ <- Either.cond(os.isFile(file), (), s"experiment file $file not found")
      cfg <- loadConfig(file, overrides)
      root = cfg.root()
      harness <- section(root, "harness")
      target <- section(root, "target")
      agents <- section(root, "agents")
      arms <- section(root, "arms")
      base = file / os.up
      exp <- decode(base, harness, target, agents, arms)
    yield exp

  private def loadConfig(file: os.Path, overrides: Map[String, String]) =
    Try:
      val ov = overrides.toList.map((k, v) => s"${if Sections(k.takeWhile(_ != '.')) then k else s"harness.$k"} = $v").mkString("\n")
      ConfigFactory.parseString(ov)
        .withFallback(ConfigFactory.parseFile(file.toIO))
        .withFallback(ConfigFactory.systemEnvironment())
        .resolve()
    .toEither.left.map(e => s"$file: ${e.getMessage}")

  private def section(root: com.typesafe.config.ConfigObject, key: String): Either[String, ujson.Obj] =
    Option(root.get(key)) match
      case None => Left(s"$key: missing")
      case Some(v) =>
        Try(ujson.read(v.render(ConfigRenderOptions.concise().setJson(true)))).toEither.left.map(e => s"$key: ${e.getMessage}").flatMap:
          case o: ujson.Obj => Right(o)
          case _ => Left(s"$key: expected an object")

  // ---- typed field access, errors name the HOCON path ----

  private def get(o: ujson.Obj, path: String, k: String): Either[String, ujson.Value] =
    o.value.get(k).toRight(s"$path$k: missing")

  private def str(o: ujson.Obj, path: String, k: String): Either[String, String] =
    get(o, path, k).flatMap:
      case ujson.Str(s) => Right(s)
      case ujson.Num(n) => Right(if n.isWhole then n.toLong.toString else n.toString)
      case _ => Left(s"$path$k: expected a string")

  private def optStr(o: ujson.Obj, path: String, k: String): Either[String, Option[String]] =
    if o.value.contains(k) then str(o, path, k).map(Some(_)) else Right(None)

  private def int(o: ujson.Obj, path: String, k: String): Either[String, Int] =
    get(o, path, k).flatMap:
      case ujson.Num(n) if n.isWhole => Right(n.toInt)
      case ujson.Str(s) if s.toIntOption.isDefined => Right(s.toInt)
      case _ => Left(s"$path$k: expected an integer")

  private def bool(o: ujson.Obj, path: String, k: String, default: Option[Boolean] = None): Either[String, Boolean] =
    o.value.get(k) match
      case Some(ujson.Bool(b)) => Right(b)
      case Some(ujson.Str("true")) => Right(true)
      case Some(ujson.Str("false")) => Right(false)
      case Some(_) => Left(s"$path$k: expected a boolean")
      case None => default.toRight(s"$path$k: missing")

  private def obj(o: ujson.Obj, path: String, k: String): Either[String, ujson.Obj] =
    get(o, path, k).flatMap:
      case x: ujson.Obj => Right(x)
      case _ => Left(s"$path$k: expected an object")

  private def list[A](o: ujson.Obj, path: String, k: String)(f: (ujson.Value, String) => Either[String, A]): Either[String, List[A]] =
    get(o, path, k).flatMap:
      case ujson.Arr(xs) => seq(xs.toList.zipWithIndex.map((x, i) => f(x, s"$path$k[$i]")))
      case _ => Left(s"$path$k: expected a list")

  private def strings(o: ujson.Obj, path: String, k: String): Either[String, List[String]] =
    list(o, path, k):
      case (ujson.Str(s), _) => Right(s)
      case (ujson.Num(n), _) => Right(if n.isWhole then n.toLong.toString else n.toString)
      case (_, p) => Left(s"$p: expected a string")

  private def ints(o: ujson.Obj, path: String, k: String): Either[String, List[Int]] =
    list(o, path, k):
      case (ujson.Num(n), _) if n.isWhole => Right(n.toInt)
      case (_, p) => Left(s"$p: expected an integer")

  private def seq[A](xs: List[Either[String, A]]): Either[String, List[A]] =
    xs.foldRight[Either[String, List[A]]](Right(Nil))((x, acc) => for a <- x; r <- acc yield a :: r)

  private def path(base: os.Path, s: String): os.Path = os.Path(s, base)

  // ---- decoding ----

  private def decode(base: os.Path, h: ujson.Obj, t: ujson.Obj, agentsObj: ujson.Obj, armsObj: ujson.Obj): Either[String, Experiment] =
    for
      target <- decodeTarget(t)
      agents <- seq(agentsObj.value.toList.map((n, v) => decodeAgent(n, v))).map(_.map(a => a.name -> a).toMap)
      armSpecs <- seq(armsObj.value.toList.map((n, v) => decodeArm(n, v, agents))).map(_.map(a => a.name -> a).toMap)
      root <- str(h, "harness.", "root")
      tasksDir <- str(h, "harness.", "tasks-dir")
      template <- str(h, "harness.", "template")
      cap <- int(h, "harness.", "cap-seconds")
      testCap <- int(h, "harness.", "test-cap-seconds")
      regCap <- int(h, "harness.", "regress-cap-seconds")
      preCap <- int(h, "harness.", "precompile-cap-seconds")
      reps <- ints(h, "harness.", "reps")
      arms <- strings(h, "harness.", "arms")
      tasks <- strings(h, "harness.", "tasks")
      chain <- strings(h, "harness.", "chain")
      smoke <- obj(h, "harness.", "smoke")
      smokeTask <- str(smoke, "harness.smoke.", "task")
      smokeRep <- int(smoke, "harness.smoke.", "rep")
      rep <- obj(h, "harness.", "report")
      repT <- str(rep, "harness.report.", "treatment")
      repC <- str(rep, "harness.report.", "control")
      repA <- str(rep, "harness.report.", "ablation")
      crep <- obj(h, "harness.", "chain-report")
      crC <- str(crep, "harness.chain-report.", "control")
      crT <- str(crep, "harness.chain-report.", "treatment")
      _ <- known("harness.arms", arms, armSpecs.keySet, "arm")
      _ <- known("harness.report.treatment", List(repT), armSpecs.keySet, "arm")
      _ <- known("harness.report.control", List(repC), armSpecs.keySet, "arm")
      _ <- known("harness.report.ablation", List(repA), armSpecs.keySet, "arm")
      _ <- known("harness.chain-report.control", List(crC), armSpecs.keySet, "arm")
      _ <- known("harness.chain-report.treatment", List(crT), armSpecs.keySet, "arm")
      _ <- known("harness.tasks", tasks, target.allTasks.toSet, "task")
      _ <- known("harness.chain", chain, target.allTasks.toSet, "task")
      _ <- known("harness.smoke.task", List(smokeTask), target.allTasks.toSet, "task")
    yield Experiment(
      path(base, root), path(base, tasksDir), path(base, template), cap, testCap, regCap, preCap,
      reps, arms, tasks, chain, smokeTask, smokeRep, ReportSpec(repT, repC, repA), ChainReportSpec(crC, crT),
      target, agents, armSpecs
    )

  private def known(at: String, xs: List[String], ok: Set[String], what: String): Either[String, Unit] =
    xs.find(!ok(_)).fold(Right(()))(x => Left(s"$at: unknown $what $x"))

  private def decodeTarget(t: ujson.Obj): Either[String, TargetSpec] =
    val p = "target."
    for
      adapter <- str(t, p, "adapter")
      repo <- absPath(t, p, "repo")
      commit <- str(t, p, "commit")
      hRepo <- absPath(t, p, "holdout-repo")
      hCommit <- str(t, p, "holdout-commit")
      hExp <- int(t, p, "holdout-expected")
      excl <- strings(t, p, "excluded-regression")
      pre <- strings(t, p, "precompile")
      mill <- str(t, p, "mill")
      ss <- absPath(t, p, "settings-source")
      all <- strings(t, p, "all-tasks")
    yield TargetSpec(adapter, repo, commit, hRepo, hCommit, hExp, excl, pre, mill, ss, all)

  private def absPath(o: ujson.Obj, path: String, k: String): Either[String, os.Path] =
    str(o, path, k).flatMap(s => Try(os.Path(s)).toOption.toRight(s"$path$k: expected an absolute path, got $s"))

  private def decodeAgent(name: String, v: ujson.Value): Either[String, AgentSpec] =
    v match
      case o: ujson.Obj => str(o, s"agents.$name.", "adapter").map(AgentSpec(name, _, o))
      case _ => Left(s"agents.$name: expected an object")

  private def decodeArm(name: String, v: ujson.Value, agents: Map[String, AgentSpec]): Either[String, ArmSpec] =
    val p = s"arms.$name."
    v match
      case o: ujson.Obj =>
        for
          agent <- str(o, p, "agent")
          _ <- Either.cond(agents.contains(agent), (), s"arms.$name.agent: unknown agent $agent")
          model <- str(o, p, "model")
          outDir <- str(o, p, "out-dir")
          holdout <- bool(o, p, "holdout")
          snap <- bool(o, p, "settings-snapshot")
          hooks <- bool(o, p, "hooks-allowed")
          tef <- bool(o, p, "turn-end-forbidden")
          setup <- list(o, p, "setup")((s, sp) => decodeStep(s, sp))
          _ <- checkVars(setup, holdout, s"${p}setup")
        yield ArmSpec(name, agent, model, outDir, holdout, snap, hooks, tef, setup)
      case _ => Left(s"arms.$name: expected an object")

  private val StepKeys = List("run", "write", "append-line", "replace-line", "json-edit", "commit")
  private val Companions = Map(
    "run" -> Set("run", "fail"), "write" -> Set("write", "text"), "append-line" -> Set("append-line", "text"),
    "replace-line" -> Set("replace-line", "prefix", "text"), "json-edit" -> Set("json-edit", "ops"),
    "commit" -> Set("commit", "paths", "allow-empty", "fail")
  )

  private def decodeStep(v: ujson.Value, p: String): Either[String, SetupStep] =
    v match
      case o: ujson.Obj =>
        o.value.keys.filter(StepKeys.contains).toList match
          case Nil => Left(s"$p: no step key (one of ${StepKeys.mkString(", ")})")
          case disc :: Nil =>
            val unknown = o.value.keys.filterNot(Companions(disc)).toList.sorted
            if unknown.nonEmpty then Left(s"$p: unknown step keys [${unknown.mkString(", ")}]")
            else
              val q = s"$p."
              disc match
                case "run" => for a <- strings(o, q, "run"); f <- optStr(o, q, "fail") yield SetupStep.Run(a, f)
                case "write" => for a <- str(o, q, "write"); t <- str(o, q, "text") yield SetupStep.Write(a, t)
                case "append-line" => for a <- str(o, q, "append-line"); t <- str(o, q, "text") yield SetupStep.AppendLine(a, t)
                case "replace-line" =>
                  for a <- str(o, q, "replace-line"); pr <- str(o, q, "prefix"); t <- str(o, q, "text") yield SetupStep.ReplaceLine(a, pr, t)
                case "json-edit" =>
                  for a <- str(o, q, "json-edit"); ops <- list(o, q, "ops")(decodeOp) yield SetupStep.JsonEdit(a, ops)
                case _ =>
                  for
                    m <- str(o, q, "commit")
                    ps <- strings(o, q, "paths")
                    ae <- bool(o, q, "allow-empty", Some(false))
                    f <- optStr(o, q, "fail")
                  yield SetupStep.Commit(m, ps, ae, f)
          case several => Left(s"$p: several step keys [${several.sorted.mkString(", ")}]")
      case _ => Left(s"$p: expected an object")

  private def decodeOp(v: ujson.Value, p: String): Either[String, JsonOp] =
    v match
      case o: ujson.Obj =>
        val q = s"$p."
        val ops = List("delete", "keep-only", "filter")
        val comp = Map("delete" -> Set("delete", "required"), "keep-only" -> Set("keep-only", "keys"),
          "filter" -> Set("filter", "contains", "require-non-empty"))
        o.value.keys.filter(ops.contains).toList match
          case disc :: Nil =>
            val unknown = o.value.keys.filterNot(comp(disc)).toList.sorted
            if unknown.nonEmpty then Left(s"$p: unknown op keys [${unknown.mkString(", ")}]")
            else disc match
              case "delete" => for a <- str(o, q, "delete"); r <- bool(o, q, "required", Some(true)) yield JsonOp.Delete(a, r)
              case "keep-only" => for a <- str(o, q, "keep-only"); ks <- strings(o, q, "keys") yield JsonOp.KeepOnly(a, ks)
              case _ =>
                for a <- str(o, q, "filter"); c <- str(o, q, "contains"); r <- bool(o, q, "require-non-empty", Some(false))
                yield JsonOp.Filter(a, c, r)
          case _ => Left(s"$p: expected exactly one of ${ops.mkString(", ")}")
      case _ => Left(s"$p: expected an object")

  private def strs(s: SetupStep): List[String] = s match
    case SetupStep.Run(a, f) => a ++ f
    case SetupStep.Write(a, b) => List(a, b)
    case SetupStep.AppendLine(a, b) => List(a, b)
    case SetupStep.ReplaceLine(a, b, c) => List(a, b, c)
    case SetupStep.JsonEdit(a, ops) =>
      a :: ops.flatMap:
        case JsonOp.Delete(x, _) => List(x)
        case JsonOp.KeepOnly(x, ks) => x :: ks
        case JsonOp.Filter(x, c, _) => List(x, c)
    case SetupStep.Commit(m, ps, _, f) => m :: ps ++ f

  /** Every placeholder in a step must be known; {holdout} only in arms with holdout = true. */
  private def checkVars(setup: List[SetupStep], holdout: Boolean, at: String): Either[String, Unit] =
    val errs = mutable.ListBuffer.empty[String]
    setup.zipWithIndex.foreach: (s, i) =>
      strs(s).flatMap(Vars.names).distinct.foreach: n =>
        if !Vars.known(n) then errs += s"$at[$i]: unknown variable {$n}"
        else if n == "holdout" && !holdout then errs += s"$at[$i]: {holdout} is undefined in an arm with holdout = false"
    errs.headOption.fold(Right(()))(Left(_))
