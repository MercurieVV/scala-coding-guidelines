package harness.config

import com.typesafe.config.ConfigFactory
import scala.jdk.CollectionConverters.*
import scala.util.Try

final case class TaskSpec(
    name: String,
    module: String,
    hiddenSpec: String,
    hiddenSpecSrc: Option[String],
    cutFiles: List[String],
    feature: String,
    referencePatch: Option[String],
    gateVisible: Boolean
)

object TaskSpec:
  private val ConfKeys = Set("module", "hidden-spec", "hidden-spec-src", "cut-files", "feature", "reference-patch", "gate-visible")
  private val EnvKeys = Set("TASK_MODULE", "HIDDEN_SPEC", "HIDDEN_SPEC_SRC", "CUT_FILES", "FEATURE", "REFERENCE_PATCH", "GATE_VISIBLE")

  def load(tasksDir: os.Path, name: String): Either[String, TaskSpec] =
    val conf = tasksDir / name / "task.conf"
    val env = tasksDir / name / "task.env"
    if os.exists(conf) then fromConf(name, conf)
    else if os.exists(env) then fromEnv(name, env)
    else Left(s"task $name: no task.conf or task.env in ${tasksDir / name}")

  private def fromConf(name: String, f: os.Path): Either[String, TaskSpec] =
    Try(ConfigFactory.parseFile(f.toIO).resolve()).toEither.left.map(e => s"$f: ${e.getMessage}").flatMap: c =>
      val keys = c.root().keySet().asScala.toSet
      val unknown = (keys -- ConfKeys).toList.sorted
      if unknown.nonEmpty then Left(s"$f: unknown keys [${unknown.mkString(", ")}]")
      else
        def opt(k: String): Option[String] = Option.when(c.hasPath(k))(c.getString(k))
        for
          module <- opt("module").toRight(s"$f: missing module")
          hidden <- opt("hidden-spec").toRight(s"$f: missing hidden-spec")
          cut = if c.hasPath("cut-files") then c.getStringList("cut-files").asScala.toList else Nil
          gate = c.hasPath("gate-visible") && c.getBoolean("gate-visible")
        yield TaskSpec(name, module, hidden, opt("hidden-spec-src"), cut, opt("feature").getOrElse(""), opt("reference-patch"), gate)

  private def fromEnv(name: String, f: os.Path): Either[String, TaskSpec] =
    val lines = os.read.lines(f).zipWithIndex.filter((l, _) => l.trim.nonEmpty && !l.trim.startsWith("#"))
    val parsed = lines.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)):
      case (Left(e), _) => Left(e)
      case (Right(m), (l, i)) =>
        parseEnvLine(l).left.map(e => s"task.env:${i + 1}: $e ($f)").flatMap: (k, v) =>
          if EnvKeys(k) then Right(m.updated(k, v)) else Left(s"task.env:${i + 1}: unknown key $k ($f)")
    parsed.flatMap: m =>
      def nonEmpty(k: String): Option[String] = m.get(k).filter(_.nonEmpty)
      for
        module <- nonEmpty("TASK_MODULE").toRight(s"$f: missing TASK_MODULE")
        hidden <- nonEmpty("HIDDEN_SPEC").toRight(s"$f: missing HIDDEN_SPEC")
      yield TaskSpec(
        name, module, hidden, nonEmpty("HIDDEN_SPEC_SRC"),
        m.getOrElse("CUT_FILES", "").split("\\s+").filter(_.nonEmpty).toList,
        m.getOrElse("FEATURE", ""), nonEmpty("REFERENCE_PATCH"), nonEmpty("GATE_VISIBLE").contains("1")
      )

  /** KEY=value with sh word rules limited to 'single quoted' segments, the '\'' idiom and bare [A-Za-z0-9_./-] words. */
  private[config] def parseEnvLine(line: String): Either[String, (String, String)] =
    val eq = line.indexOf('=')
    if eq <= 0 || !line.substring(0, eq).matches("[A-Z_][A-Z0-9_]*") then Left(s"not KEY=value: $line")
    else
      val rest = line.substring(eq + 1)
      val sb = new StringBuilder
      var i = 0
      var err: Option[String] = None
      while err.isEmpty && i < rest.length do
        val c = rest(i)
        if c == '\'' then
          val end = rest.indexOf('\'', i + 1)
          if end < 0 then err = Some(s"unterminated quote: $line")
          else
            sb.append(rest.substring(i + 1, end))
            i = end + 1
        else if rest.startsWith("\\'", i) then
          sb.append('\'')
          i += 2
        else if c.isLetterOrDigit || "_./-".contains(c) then
          sb.append(c)
          i += 1
        else err = Some(s"unsupported shell syntax '$c': $line")
      err.toLeft(eq).map(_ => line.substring(0, eq) -> sb.toString)
