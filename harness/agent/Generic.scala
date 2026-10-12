package harness.agent

import cats.effect.IO
import harness.config.{AgentSpec, ArmSpec}
import harness.core.Transcript

/** Any command-line agent (spec §4): argv from the conf's `command` template, `transcript = none`, so only the final diff
  * is scored. Turns, tokens and cost are null; a result is an exit code 0 (Score reads it from run.json).
  */
final class Generic(val spec: AgentSpec) extends Agent:
  private def template: List[String] =
    spec.raw.value.get("command").collect { case ujson.Arr(a) => a.toList.flatMap(_.strOpt) }.filter(_.nonEmpty)
      .getOrElse(throw new RuntimeException(s"agents.${spec.name}.command: missing"))

  override def resultFromExit: Boolean = true

  /** final review I3 / M2: `command` is a non-empty string list whose placeholders are all known. */
  override def validate: Either[String, Unit] =
    spec.raw.value.get("command") match
      case Some(ujson.Arr(a)) if a.nonEmpty && a.forall(_.strOpt.isDefined) =>
        a.toList.flatMap(_.strOpt).zipWithIndex.flatMap((s, i) => Generic.placeholders(s).filterNot(Generic.Placeholders.contains).map(i -> _))
          .headOption.fold(Right(())): (i, p) =>
            Left(s"agents.${spec.name}.command[$i]: unknown placeholder {$p} (one of ${Generic.Placeholders.map(x => s"{$x}").mkString(", ")})")
      case Some(ujson.Arr(_)) => Left(s"agents.${spec.name}.command: expected a non-empty list of strings")
      case _ => Left(s"agents.${spec.name}.command: missing")

  def setup(ctx: RunCtx): IO[Unit] = IO.unit

  def command(ctx: RunCtx, prompt: String): List[String] =
    template.map(_.replace("{prompt}", prompt).replace("{model}", ctx.arm.model).replace("{repo}", ctx.repo.toString))

  def env(ctx: RunCtx): Map[String, Option[String]] = ClaudeCode.SessionVars.map(_ -> Option.empty[String]).toMap

  def wipeState(ctx: RunCtx): IO[Unit] = IO.unit

  /** stdout is captured as-is; make sure the file exists */
  def collect(ctx: RunCtx, startedAtMillis: Long): IO[Unit] = IO.blocking:
    if !os.exists(ctx.runDir / "transcript.jsonl") then os.write(ctx.runDir / "transcript.jsonl", "")

  def parse(lines: Iterator[String]): Transcript =
    Transcript(None, None, None, None, None, Vector.empty, "", Vector.empty, Vector.empty, true, None, Vector.empty, None)

  def envLeaks(t: Transcript, arm: ArmSpec): List[String] = Nil

  def privateStateDirs(ctx: RunCtx): (List[os.Path], List[os.Path]) = (Nil, Nil)

  def settingsArtefact(ctx: RunCtx): Option[os.Path] = None

object Generic:
  val TranscriptModes = List("none")
  val Placeholders = List("prompt", "model", "repo")
  private val Placeholder = "\\{([A-Za-z_][A-Za-z0-9_-]*)\\}".r
  def placeholders(s: String): List[String] = Placeholder.findAllMatchIn(s).map(_.group(1)).toList
