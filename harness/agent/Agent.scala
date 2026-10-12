package harness.agent

import cats.effect.IO

final case class RunCtx(
    exp: harness.config.Experiment,
    arm: harness.config.ArmSpec,
    task: harness.config.TaskSpec,
    runId: String,
    runDir: os.Path,
    repo: os.Path,
    holdout: Option[os.Path],
    settingsSnapshot: Option[os.Path] // the campaign's frozen snapshot when arm.settingsSnapshot
)

trait Agent:
  def spec: harness.config.AgentSpec

  /** Writes the agent's own configuration into ctx.repo before arm setup steps run. */
  def setup(ctx: RunCtx): IO[Unit]

  /** after the arm's setup steps, before the settings artefact is copied (dsh: the hook file from the run copy). */
  def afterSteps(@scala.annotation.unused ctx: RunCtx): IO[Unit] = IO.unit

  /** argv of one headless run; cwd is ctx.repo. */
  def command(ctx: RunCtx, prompt: String): List[String]

  /** env changes for the agent process (None = unset). */
  def env(ctx: RunCtx): Map[String, Option[String]]

  /** per-cwd memory / session state wiped before the run (GA M2). */
  def wipeState(ctx: RunCtx): IO[Unit]

  /** after the run: make ctx.runDir/transcript.jsonl exist (Claude: stdout already is it). */
  def collect(ctx: RunCtx, startedAtMillis: Long): IO[Unit]

  def parse(lines: Iterator[String]): harness.core.Transcript

  def envLeaks(t: harness.core.Transcript, arm: harness.config.ArmSpec): List[String]

  /** dirs the agent keeps state in that a run must not read (contamination fence), minus the run's own. */
  def privateStateDirs(ctx: RunCtx): (List[os.Path], List[os.Path]) // (forbidden, ownAllowed)

  /** true when the transcript cannot show a result (generic, transcript = none): the run's exit code 0 is the result. */
  def resultFromExit: Boolean = false

  /** spec §6, final review I3: the adapter's required knobs, checked at load; the error names `agents.<name>.<key>`. */
  def validate: Either[String, Unit] = Right(())

  /** file names copied into the run dir as settings.json (GA artefact). */
  def settingsArtefact(ctx: RunCtx): Option[os.Path]

object Agent:
  private val Known = List("claude-code", "dsh", "codex-cli", "gemini-cli", "generic")

  /** The adapter for `spec`, its required knobs checked ([[Agent.validate]]). */
  def of(spec: harness.config.AgentSpec): Either[String, Agent] = construct(spec).flatMap(a => a.validate.map(_ => a))

  /** `agents.<name>.<key>` must be a string. */
  def requireStr(spec: harness.config.AgentSpec, key: String): Either[String, Unit] =
    spec.raw.value.get(key).flatMap(_.strOpt).toRight(s"agents.${spec.name}.$key: missing").map(_ => ())

  private def construct(spec: harness.config.AgentSpec): Either[String, Agent] = spec.adapter match
    case "claude-code" => Right(ClaudeCode(spec))
    case "dsh" => Right(Dsh(spec))
    case "codex-cli" => Right(CodexCli(spec))
    case "gemini-cli" => Right(GeminiCli(spec))
    case "generic" =>
      val mode = spec.raw.value.get("transcript").flatMap(_.strOpt).getOrElse("none")
      Either.cond(Generic.TranscriptModes.contains(mode), Generic(spec),
        s"agents.${spec.name}.transcript: unsupported $mode (one of ${Generic.TranscriptModes.mkString(", ")})")
    case a if Known.contains(a) => Left(s"agents.${spec.name}: adapter $a is not implemented yet")
    case a => Left(s"agents.${spec.name}: unknown adapter $a (one of ${Known.mkString(", ")})")
