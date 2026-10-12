package harness.agent

import harness.config.AgentSpec

/** The `mcp-servers { <name> { command, args, … } }` knob shared by the Codex and Gemini adapters. */
object McpServers:
  final case class Server(name: String, command: String, args: List[String], raw: ujson.Obj)

  def of(spec: AgentSpec): List[Server] =
    spec.raw.value.get("mcp-servers").collect { case o: ujson.Obj => o }.toList.flatMap(_.value.toList).collect:
      case (name, o: ujson.Obj) =>
        val command = o.value.get("command").flatMap(_.strOpt).getOrElse(throw new RuntimeException(s"agents.${spec.name}.mcp-servers.$name.command: missing"))
        val args = o.value.get("args").collect { case ujson.Arr(a) => a.toList.flatMap(_.strOpt) }.getOrElse(Nil)
        Server(name, command, args, o)

  /** every `mcp-servers.<name>` is an object with a string `command` */
  def validate(spec: AgentSpec): Either[String, Unit] =
    spec.raw.value.get("mcp-servers") match
      case None | Some(ujson.Null) => Right(())
      case Some(o: ujson.Obj) =>
        o.value.toList.collectFirst {
          case (n, s: ujson.Obj) if !s.value.get("command").exists(_.strOpt.isDefined) => s"agents.${spec.name}.mcp-servers.$n.command: missing"
          case (n, s) if !s.isInstanceOf[ujson.Obj] => s"agents.${spec.name}.mcp-servers.$n: expected an object"
        }.toLeft(())
      case Some(_) => Left(s"agents.${spec.name}.mcp-servers: expected an object")

  def allowed(spec: AgentSpec): List[String] =
    spec.raw.value.get("allowed-mcp").collect { case ujson.Arr(a) => a.toList.flatMap(_.strOpt) }.getOrElse(Nil)

  /** TOML basic string (JSON string escapes are valid TOML ones for the characters used here). */
  def tomlStr(s: String): String = ujson.write(ujson.Str(s))
  def tomlArr(xs: List[String]): String = xs.map(tomlStr).mkString("[", ", ", "]")
