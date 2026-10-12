package harness.agent

import cats.effect.IO
import harness.config.{AgentSpec, ArmSpec}
import harness.core.{ClaudeTranscript, Gate, Transcript}

/** Ports GA run.sh:26-48, 77-91 and score.py:138-156, 546-561.
  *
  * @param home         what `~` is (tests pass a fake one)
  * @param configDirEnv value of CLAUDE_CONFIG_DIR, when set
  */
final class ClaudeCode(val spec: AgentSpec, home: os.Path = os.home, configDirEnv: Option[String] = sys.env.get("CLAUDE_CONFIG_DIR").filter(_.nonEmpty))
    extends Agent:
  import ClaudeCode.*

  private def strs(key: String): List[String] =
    spec.raw.value.get(key).collect { case ujson.Arr(a) => a.toList.map(_.str) }.getOrElse(Nil)

  private def bin: String = spec.raw.value.get("bin").flatMap(_.strOpt).getOrElse("claude")

  private def configDir: os.Path = configDirEnv.map(os.Path(_)).getOrElse(home / ".claude")

  override def validate: Either[String, Unit] = Agent.requireStr(spec, "mcp-json")

  def setup(ctx: RunCtx): IO[Unit] = IO.blocking:
    os.makeDir.all(ctx.repo / ".claude")
    val mcp = spec.raw.value.get("mcp-json").flatMap(_.strOpt).getOrElse(throw new RuntimeException(s"agents.${spec.name}.mcp-json: missing"))
    os.write.over(ctx.repo / ".mcp.json", mcp + "\n")
    val settings = ctx.repo / ".claude" / "settings.json"
    if ctx.arm.settingsSnapshot then
      val snap = ctx.settingsSnapshot.getOrElse(throw new RuntimeException("no gated settings snapshot (campaign writes it)"))
      val src = ctx.exp.target.holdoutRepo.toString
      val hd = ctx.holdout.getOrElse(throw new RuntimeException("holdout export")).toNIO.toRealPath().toString
      val text = os.read(snap).replace(src, hd)
      os.write.over(settings, text)
      if text.contains(src) then throw new RuntimeException(s"settings still mention $src")
    else os.write.over(settings, "{\"permissions\":{}}\n")

  def command(ctx: RunCtx, prompt: String): List[String] =
    List(bin, "-p", prompt, "--model", ctx.arm.model, "--output-format", "stream-json", "--verbose",
      "--setting-sources", "project", "--strict-mcp-config", "--mcp-config", ".mcp.json",
      "--permission-mode", "acceptEdits", "--allowedTools") ++ strs("tools")

  def env(ctx: RunCtx): Map[String, Option[String]] = SessionVars.map(_ -> Option.empty[String]).toMap

  def wipeState(ctx: RunCtx): IO[Unit] = IO.blocking:
    os.remove.all(configDir / "projects" / claudeSlug(ctx.repo.toNIO.toRealPath().toString))

  def collect(ctx: RunCtx, startedAtMillis: Long): IO[Unit] = IO.unit

  def parse(lines: Iterator[String]): Transcript = ClaudeTranscript.parse(lines)

  /** GA env_leaks: what of the operator's own Claude config reached the agent, from the transcript's init event. */
  def envLeaks(t: Transcript, arm: ArmSpec): List[String] =
    t.init match
      case None => if t.hasResult then List("no init event") else Nil
      case Some(init) =>
        val leaks = List.newBuilder[String]
        def dicts(key: String): List[ujson.Obj] =
          init.value.get(key).collect { case ujson.Arr(a) => a.toList.collect { case o: ujson.Obj => o } }.getOrElse(Nil)
        def name(o: ujson.Obj): String = o.value.get("name") match
          case Some(ujson.Str(s)) => s
          case Some(ujson.Null) | None => "None"
          case Some(other) => ujson.write(other)
        val mcp = dicts("mcp_servers").map(name).sorted
        if mcp != strs("allowed-mcp").sorted then leaks += ("mcp_servers=" + mcp.mkString(","))
        val plugins = dicts("plugins").filterNot(_.value.get("path").contains(ujson.Str("builtin"))).map(name)
        if plugins.nonEmpty then leaks += ("plugins=" + plugins.mkString(","))
        if !arm.hooksAllowed && t.hookEvents.nonEmpty then leaks += ("hooks=" + t.hookEvents.distinct.mkString(","))
        if arm.turnEndForbidden && t.hookTexts.exists(Gate.isTurnEndText) then leaks += "turn-end in arm t"
        leaks.result()

  def privateStateDirs(ctx: RunCtx): (List[os.Path], List[os.Path]) =
    val projects = os.Path(harness.core.Contamination.realpath((configDir / "projects").toString))
    val own = projects / claudeSlug(ctx.repo.toNIO.toRealPath().toString)
    (List(projects), List(own))

  def settingsArtefact(ctx: RunCtx): Option[os.Path] = Some(ctx.repo / ".claude" / "settings.json")

object ClaudeCode:
  /** GA run.sh:90-92: a campaign started inside a Claude Code session must not hand that session's identity to the agent. */
  val SessionVars: List[String] = List(
    "CLAUDECODE", "CLAUDE_CODE_ENTRYPOINT", "CLAUDE_CODE_SESSION_ID", "CLAUDE_CODE_CHILD_SESSION",
    "CLAUDE_CODE_MESSAGING_SOCKET", "CLAUDE_CODE_MESSAGING_TOKEN", "CLAUDE_CODE_BRIDGE_SESSION_ID",
    "CLAUDE_CODE_SESSION_ATTENDED", "CLAUDE_CODE_EXECPATH", "CLAUDE_PID", "CLAUDE_JOB_DIR", "CLAUDE_EFFORT", "AI_AGENT")

  /** GA _claude_slug: the cwd with `/` and `.` replaced by `-`. */
  def claudeSlug(path: String): String = path.replaceAll("[/.]", "-")
