package harness.agent

import cats.effect.IO
import harness.config.{AgentSpec, ArmSpec}
import harness.core.{ToolCall, ToolKind, Transcript}
import scala.collection.mutable
import scala.util.chaining.*

/** Gemini CLI (spec §4): `gemini -p … --output-format stream-json --approval-mode yolo`, stdout JSONL is the transcript.
  * Per-cwd sessions live under `<gemini-home>/tmp/<id>/chats`, `<id>` from `<gemini-home>/projects.json`.
  */
final class GeminiCli(val spec: AgentSpec) extends Agent:

  private def bin: String = spec.raw.value.get("bin").flatMap(_.strOpt).getOrElse("gemini")
  private def geminiHome: os.Path =
    spec.raw.value.get("gemini-home").flatMap(_.strOpt).map(os.Path(_, os.pwd)).getOrElse(os.home / ".gemini")
  private def servers = McpServers.of(spec)

  /** the project-registry id of the run copy's real path (its dir under tmp/), when registered */
  private def projectDir(ctx: RunCtx): Option[os.Path] =
    val reg = geminiHome / "projects.json"
    if !os.isFile(reg) then None
    else
      val projects = scala.util.Try(ujson.read(os.read(reg))("projects").obj).toOption.getOrElse(mutable.LinkedHashMap.empty[String, ujson.Value])
      List(ctx.repo.toNIO.toRealPath().toString, ctx.repo.toString).flatMap(projects.get).flatMap(_.strOpt)
        .find(id => id.matches("[A-Za-z0-9._-]+") && id != "." && id != "..").map(geminiHome / "tmp" / _)

  override def validate: Either[String, Unit] = McpServers.validate(spec)

  /** `<repo>/.gemini/settings.json` with the conf's MCP servers. */
  def setup(ctx: RunCtx): IO[Unit] = IO.blocking:
    if servers.nonEmpty then
      val m = ujson.Obj.from(servers.map(sv => sv.name -> (sv.raw: ujson.Value)))
      os.write.over(ctx.repo / ".gemini" / "settings.json", ujson.write(ujson.Obj("mcpServers" -> m), indent = 2) + "\n", createFolders = true)

  def command(ctx: RunCtx, prompt: String): List[String] =
    List(bin, "-m", ctx.arm.model, "--output-format", "stream-json", "--approval-mode", "yolo", "-e", "none",
      "--allowed-mcp-server-names") ++ McpServers.allowed(spec).pipe(a => if a.isEmpty then List("none") else a) ++
      List("-p", prompt)

  def env(ctx: RunCtx): Map[String, Option[String]] = ClaudeCode.SessionVars.map(_ -> Option.empty[String]).toMap

  /** only the run copy's own project dir under tmp/ */
  def wipeState(ctx: RunCtx): IO[Unit] = IO.blocking(projectDir(ctx).foreach(os.remove.all(_)))

  def collect(ctx: RunCtx, startedAtMillis: Long): IO[Unit] = IO.unit // stdout is the transcript

  def parse(lines: Iterator[String]): Transcript = GeminiCli.parse(lines)

  def envLeaks(t: Transcript, arm: ArmSpec): List[String] =
    val allowed = McpServers.allowed(spec)
    val extra = t.toolCalls.filter(_.kind == ToolKind.Mcp).map(_.name).filterNot(n => allowed.exists(a => n.startsWith(s"mcp_${a}_")))
      .map(_.stripPrefix("mcp_").takeWhile(_ != '_')).distinct.sorted
    if extra.isEmpty then Nil else List("mcp_servers=" + extra.mkString(","))

  def privateStateDirs(ctx: RunCtx): (List[os.Path], List[os.Path]) =
    val tmp = os.Path(harness.core.Contamination.realpath((geminiHome / "tmp").toString))
    (List(tmp), projectDir(ctx).map(d => tmp / d.last).toList)

  /** only when [[setup]] wrote it (servers configured); Run copies it as settings.json */
  def settingsArtefact(ctx: RunCtx): Option[os.Path] = Option.when(servers.nonEmpty)(ctx.repo / ".gemini" / "settings.json")

object GeminiCli:
  def kindOf(name: String): ToolKind = name match
    case "run_shell_command"                                     => ToolKind.Shell
    case "replace"                                               => ToolKind.Edit
    case "write_file"                                            => ToolKind.Write
    case "read_file" | "read_many_files"                         => ToolKind.Read
    case "grep_search" | "search_file_content" | "glob" | "list_directory" => ToolKind.Search
    case n if n.startsWith("mcp_")                               => ToolKind.Mcp
    case _                                                       => ToolKind.Other

  private def s(o: ujson.Obj, k: String): Option[String] = o.value.get(k).flatMap(_.strOpt)
  private def obj(o: ujson.Obj, k: String): Option[ujson.Obj] = o.value.get(k).collect { case x: ujson.Obj => x }

  def parse(lines: Iterator[String]): Transcript =
    var inTok, outTok, cacheTok: Option[Long] = None
    val calls = mutable.ArrayBuffer.empty[ToolCall]
    val byId = mutable.HashMap.empty[String, Int]
    var finalText = ""
    val texts = mutable.ArrayBuffer.empty[String]
    var hasResult = false
    var init: Option[ujson.Obj] = None
    var apiError: Option[String] = None
    val buf = StringBuilder() // assistant text deltas of the current message
    def flush(): Unit =
      if buf.nonEmpty then
        texts += buf.toString
        finalText = buf.toString
        buf.clear()

    for raw <- lines do
      val line = raw.trim
      if line.nonEmpty then
        scala.util.Try(ujson.read(line)).toOption.collect { case o: ujson.Obj => o }.foreach: ev =>
          s(ev, "type") match
            case Some("init") => init = Some(ev)
            case Some("message") =>
              if s(ev, "role").contains("assistant") then buf ++= s(ev, "content").getOrElse("")
              else flush()
            case Some("tool_use") =>
              flush()
              val name = s(ev, "tool_name").getOrElse("")
              calls += ToolCall(name, kindOf(name), obj(ev, "parameters").getOrElse(ujson.Obj()), "", false, calls.size + 1)
              s(ev, "tool_id").foreach(byId(_) = calls.size - 1)
            case Some("tool_result") =>
              val err = !s(ev, "status").contains("success")
              val text = if err then obj(ev, "error").flatMap(s(_, "message")).getOrElse(s(ev, "output").getOrElse("")) else s(ev, "output").getOrElse("")
              texts += text
              s(ev, "tool_id").flatMap(byId.get).foreach(i => calls(i) = calls(i).copy(resultText = text, isError = err))
            case Some("result") =>
              flush()
              hasResult = true
              if !s(ev, "status").contains("success") then
                val e = obj(ev, "error")
                apiError = Some(List(e.flatMap(s(_, "type")), e.flatMap(s(_, "message"))).flatten.mkString(": ").trim)
              obj(ev, "stats").foreach: st =>
                def n(k: String) = st.value.get(k).flatMap(_.numOpt).map(_.toLong)
                inTok = n("input_tokens")
                outTok = n("output_tokens")
                cacheTok = n("cached")
            case _ => ()
    flush()

    Transcript(None, inTok, outTok, cacheTok, None, calls.toVector, finalText, texts.toVector, Vector.empty, hasResult, init,
      Vector.empty, apiError)
