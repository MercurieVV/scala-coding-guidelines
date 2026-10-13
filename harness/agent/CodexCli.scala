package harness.agent

import cats.effect.IO
import harness.config.{AgentSpec, ArmSpec}
import harness.core.{ToolCall, ToolKind, Transcript}
import scala.collection.mutable

/** Codex CLI (spec §4): `codex exec --json`, whose stdout JSONL is the transcript. Runs are `--ephemeral`, so Codex writes
  * no session files and there is no per-cwd state to wipe or collect. Event schema: see agents/NOTES.md (unverified live).
  */
final class CodexCli(val spec: AgentSpec) extends Agent:
  import CodexCli.*

  private def bin: String = spec.raw.value.get("bin").flatMap(_.strOpt).getOrElse("codex")
  private def codexHome: os.Path =
    spec.raw.value.get("codex-home").flatMap(_.strOpt).map(os.Path(_, os.pwd)).getOrElse(os.home / ".codex")
  private def servers = McpServers.of(spec)

  override def validate: Either[String, Unit] = McpServers.validate(spec)

  /** `<repo>/.codex/config.toml` with the conf's MCP servers (the `-c` overrides in [[command]] carry them for untrusted projects). */
  def setup(ctx: RunCtx): IO[Unit] = IO.blocking:
    if servers.nonEmpty then
      val toml = servers.map: s =>
        s"[mcp_servers.${s.name}]\ncommand = ${McpServers.tomlStr(s.command)}\nargs = ${McpServers.tomlArr(s.args)}\n"
      os.write.over(ctx.repo / ".codex" / "config.toml", toml.mkString("\n"), createFolders = true)

  def command(ctx: RunCtx, prompt: String): List[String] =
    List(bin, "exec", "--json", "--ephemeral", "--skip-git-repo-check", "--ignore-user-config", "--ignore-rules",
      "--dangerously-bypass-approvals-and-sandbox", "-C", ctx.repo.toString, "-m", ctx.arm.model) ++
      servers.flatMap(s => List("-c", s"mcp_servers.${s.name}.command=${McpServers.tomlStr(s.command)}",
        "-c", s"mcp_servers.${s.name}.args=${McpServers.tomlArr(s.args)}")) ++
      List("--", prompt) // clap: a prompt starting with `-` (or naming a subcommand) stays the PROMPT

  def env(ctx: RunCtx): Map[String, Option[String]] = ClaudeCode.SessionVars.map(_ -> Option.empty[String]).toMap

  def wipeState(ctx: RunCtx): IO[Unit] = IO.unit // --ephemeral: nothing persisted

  def collect(ctx: RunCtx, startedAtMillis: Long): IO[Unit] = IO.unit // stdout is the transcript

  def parse(lines: Iterator[String]): Transcript = CodexCli.parse(lines)

  def envLeaks(t: Transcript, arm: ArmSpec): List[String] = mcpLeaks(t, McpServers.allowed(spec))

  def privateStateDirs(ctx: RunCtx): (List[os.Path], List[os.Path]) =
    (List(os.Path(harness.core.Contamination.realpath((codexHome / "sessions").toString))), Nil)

  /** only when [[setup]] wrote it (servers configured); Run copies it as settings.json */
  def settingsArtefact(ctx: RunCtx): Option[os.Path] = Option.when(servers.nonEmpty)(ctx.repo / ".codex" / "config.toml")

object CodexCli:
  private val LoginShells = Set("sh", "bash", "zsh")

  /** `codex exec` runs every command as `<login shell> -lc '<script>'`; the call's command is the script, so contamination
    * tokenizes what the agent wrote (final review I2: heredocs, `$((a / b))`, chmod targets), not the wrapper.
    */
  def unwrapShell(cmd: String): String =
    harness.core.Contamination.shlexWords(cmd) match
      case Some(List(sh, flag, script)) if LoginShells(sh.substring(sh.lastIndexOf('/') + 1)) && flag.matches("-[a-z]*c") => script
      case _ => cmd

  /** MCP calls named `mcp__<server>__<tool>` to servers outside the allow-list. */
  def mcpLeaks(t: Transcript, allowed: List[String]): List[String] =
    val servers = t.toolCalls.filter(_.kind == ToolKind.Mcp).flatMap(_.name.stripPrefix("mcp__").split("__", 2).headOption).distinct.sorted
    val extra = servers.filterNot(allowed.contains)
    if extra.isEmpty then Nil else List("mcp_servers=" + extra.mkString(","))

  private def s(o: ujson.Obj, k: String): Option[String] = o.value.get(k).flatMap(_.strOpt)
  private def obj(o: ujson.Obj, k: String): Option[ujson.Obj] = o.value.get(k).collect { case x: ujson.Obj => x }
  private def present(o: ujson.Obj, k: String): Boolean = o.value.get(k).exists(_ != ujson.Null)

  def parse(lines: Iterator[String]): Transcript =
    var inTok, outTok, cacheTok: Option[Long] = None
    val calls = mutable.ArrayBuffer.empty[ToolCall]
    var finalText = ""
    val texts = mutable.ArrayBuffer.empty[String]
    var hasResult = false
    var apiError: Option[String] = None
    def add(acc: Option[Long], v: Option[Long]): Option[Long] = v.map(_ + acc.getOrElse(0L)).orElse(acc)

    for raw <- lines do
      val line = raw.trim
      if line.nonEmpty then
        scala.util.Try(ujson.read(line)).toOption.collect { case o: ujson.Obj => o }.foreach: ev =>
          s(ev, "type") match
            case Some("item.completed") =>
              obj(ev, "item").foreach: item =>
                val turn = calls.size + 1
                s(item, "type") match
                  case Some("agent_message") =>
                    val text = s(item, "text").getOrElse("")
                    texts += text
                    finalText = text
                  case Some("command_execution") =>
                    val out = s(item, "aggregated_output").getOrElse("")
                    val code = item.value.get("exit_code").flatMap(_.numOpt)
                    val err = s(item, "status").contains("failed") || code.exists(_ != 0)
                    texts += out
                    calls += ToolCall("command_execution", ToolKind.Shell, ujson.Obj("command" -> unwrapShell(s(item, "command").getOrElse(""))), out, err, turn)
                  case Some("file_change") =>
                    val changes = item.value.get("changes").collect { case ujson.Arr(a) => a.toSeq.collect { case o: ujson.Obj => o } }.getOrElse(Nil)
                    val kind = if changes.nonEmpty && changes.forall(c => s(c, "kind").contains("add")) then ToolKind.Write else ToolKind.Edit
                    calls += ToolCall("file_change", kind, ujson.Obj("changes" -> ujson.Arr.from(changes)), "", s(item, "status").contains("failed"), turn)
                  case Some("mcp_tool_call") =>
                    val args = obj(item, "arguments").getOrElse(ujson.Obj())
                    val result = obj(item, "result").flatMap(_.value.get("content")).collect {
                      case ujson.Arr(a) => a.toSeq.collect { case o: ujson.Obj => s(o, "text") }.flatten.mkString("\n") }.getOrElse("")
                    val errMsg = obj(item, "error").flatMap(s(_, "message"))
                    val text = errMsg.getOrElse(result)
                    texts += text
                    val err = present(item, "error") || s(item, "status").contains("failed")
                    calls += ToolCall(s"mcp__${s(item, "server").getOrElse("")}__${s(item, "tool").getOrElse("")}", ToolKind.Mcp, args, text, err, turn)
                  case Some("web_search") =>
                    calls += ToolCall("web_search", ToolKind.Search, ujson.Obj("query" -> s(item, "query").getOrElse("")), "", false, turn)
                  case _ => ()
            case Some("turn.completed") =>
              hasResult = true
              obj(ev, "usage").foreach: u =>
                def n(k: String) = u.value.get(k).flatMap(_.numOpt).map(_.toLong)
                inTok = add(inTok, n("input_tokens"))
                outTok = add(outTok, n("output_tokens"))
                cacheTok = add(cacheTok, n("cached_input_tokens"))
            case Some("turn.failed") =>
              hasResult = true
              apiError = Some(obj(ev, "error").flatMap(s(_, "message")).getOrElse("turn failed"))
            case _ => ()

    // OpenAI usage: input_tokens includes cached_input_tokens; Transcript.inputTokens is the uncached part (Claude's meaning)
    val uncached = inTok.map(i => math.max(0L, i - cacheTok.getOrElse(0L)))
    Transcript(None, uncached, outTok, cacheTok, None, calls.toVector, finalText, texts.toVector, Vector.empty, hasResult, None,
      Vector.empty, apiError)
