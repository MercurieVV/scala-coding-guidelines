package harness.agent

import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import harness.config.*
import harness.core.ToolKind
import munit.CatsEffectSuite

/** Codex CLI, Gemini CLI and generic adapters against temp homes: never read or write the real ~/.codex or ~/.gemini. */
class OtherAgentsTest extends CatsEffectSuite:
  // the confs render ${HOME} from the environment
  private val Home = sys.env("HOME")
  private val harnessDir: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .find(d => os.exists(d / "experiments" / "gate-ab.conf")).getOrElse(fail("harness dir not found"))
  private val fx = harnessDir / "test" / "fixtures"
  private val exp0: Experiment = Load.experiment(harnessDir / "experiments" / "gate-ab.conf").fold(fail(_), identity)
  private val task: TaskSpec = TaskSpec.load(exp0.tasksDir, exp0.tasks.head).fold(fail(_), identity)
  private def arm(n: String, model: String = "m-1"): ArmSpec = exp0.armSpecs(n).copy(model = model)

  private def conf(file: String, name: String): ujson.Obj =
    val c = ConfigFactory.parseFile((harnessDir / "agents" / file).toIO).withFallback(ConfigFactory.systemEnvironment()).resolve()
    ujson.read(c.getObject(s"agents.$name").render(ConfigRenderOptions.concise().setJson(true))) match
      case o: ujson.Obj => o
      case other        => fail(s"agents.$name: $other")

  private def spec(file: String, name: String, extra: (String, ujson.Value)*): AgentSpec =
    val c = conf(file, name)
    AgentSpec(name, c("adapter").str, ujson.Obj.from(c.value.toSeq ++ extra))

  private def ctx(a: String = "t", model: String = "m-1", repo: os.Path = os.temp.dir() / "repo"): RunCtx =
    os.makeDir.all(repo)
    RunCtx(exp0, arm(a, model), task, s"t-$a-1", repo / os.up, repo, None, None)

  private def lines(p: os.Path): Vector[String] = os.read.lines(p).toVector
  private def real(p: os.Path): String = p.toNIO.toRealPath().toString

  private def codex(extra: (String, ujson.Value)*) = CodexCli(spec("codex.conf", "codex", extra*))
  private def gemini(home: os.Path = os.temp.dir(), extra: Seq[(String, ujson.Value)] = Nil) =
    GeminiCli(spec("gemini.conf", "gemini", (Seq("gemini-home" -> ujson.Str(home.toString)) ++ extra)*))
  private def generic(extra: (String, ujson.Value)*) = Generic(spec("generic.conf", "generic", extra*))

  // ---- conf + Agent.of ----
  test("confs: adapters and knobs"):
    assertEquals(conf("codex.conf", "codex")("adapter").str, "codex-cli")
    assertEquals(conf("gemini.conf", "gemini")("adapter").str, "gemini-cli")
    assertEquals(conf("generic.conf", "generic")("transcript").str, "none")
    assertEquals(conf("codex.conf", "codex")("allowed-mcp").arr.map(_.str).toList, List("scala-semantic"))

  test("Agent.of: codex-cli, gemini-cli, generic resolve"):
    assert(Agent.of(spec("codex.conf", "codex")).exists(_.isInstanceOf[CodexCli]))
    assert(Agent.of(spec("gemini.conf", "gemini")).exists(_.isInstanceOf[GeminiCli]))
    assert(Agent.of(spec("generic.conf", "generic")).exists(_.isInstanceOf[Generic]))

  test("Agent.of: generic with an unsupported transcript mode is rejected"):
    assertEquals(Agent.of(spec("generic.conf", "generic", "transcript" -> ujson.Str("claude"))),
      Left("agents.generic.transcript: unsupported claude (one of none)"))

  // ---- codex ----
  test("codex parse: documented exec --json events"):
    val t = codex().parse(lines(fx / "codex" / "exec-json-sample.jsonl").iterator)
    assertEquals(t.toolCalls.map(_.name), Vector("command_execution", "mcp__scala-semantic__find_symbol", "file_change", "file_change",
      "command_execution", "mcp__other__x"))
    assertEquals(t.toolCalls.map(_.kind), Vector(ToolKind.Shell, ToolKind.Mcp, ToolKind.Edit, ToolKind.Write, ToolKind.Shell, ToolKind.Mcp))
    assertEquals(t.toolCalls.map(_.isError), Vector(false, false, false, false, true, true))
    assertEquals(t.toolCalls(0).command, "ls src") // the login-shell wrapper `/bin/zsh -lc '…'` dropped (final review I2)
    assertEquals(t.toolCalls(0).resultText, "Main.scala\n")
    assertEquals(t.toolCalls(1).input("name").str, "Main")
    assertEquals(t.toolCalls(1).resultText, "src/Main.scala:3")
    assertEquals(t.toolCalls(2).input("changes")(0)("path").str, "src/Main.scala")
    assertEquals(t.toolCalls(5).resultText, "boom")
    assertEquals(t.toolCalls.map(_.turn), Vector(1, 2, 3, 4, 5, 6))
    assert(t.hasResult)
    assertEquals(t.apiError, None)
    assertEquals(t.finalText, "<final answer>")
    assertEquals((t.inputTokens, t.outputTokens, t.cacheReadTokens), (Some(24763L - 24448L), Some(122L), Some(24448L)))
    assertEquals(t.costUsd, None)
    assertEquals(t.turns, None)
    assertEquals(t.hookEvents, Vector.empty)

  // Task 20: lines captured from the live fr-pan-codex-u-1 run (codex-cli 0.148.0, gpt-5.5), texts shortened
  test("codex parse: live exec --json capture"):
    val t = codex().parse(lines(fx / "codex" / "exec-json-live.jsonl").iterator)
    assertEquals(t.toolCalls.map(_.name), Vector("command_execution", "mcp__scala-semantic__set_workspace_root", "file_change",
      "command_execution"))
    assertEquals(t.toolCalls.map(_.kind), Vector(ToolKind.Shell, ToolKind.Mcp, ToolKind.Edit, ToolKind.Shell))
    assertEquals(t.toolCalls.map(_.isError), Vector(false, false, false, true))
    assertEquals(t.toolCalls(0).command, "sed -n '1,220p' SCALA_SEMANTIC_RULES.md")
    assert(t.toolCalls(1).resultText.startsWith("""{"root":"""), t.toolCalls(1).resultText)
    assertEquals(t.toolCalls(2).input("changes").arr.size, 4)
    assert(t.hasResult)
    assertEquals(t.apiError, None)
    // OpenAI usage counts cached tokens inside input_tokens; Transcript.inputTokens is the uncached part (as Claude's)
    assertEquals((t.inputTokens, t.outputTokens, t.cacheReadTokens), (Some(2612966L - 2513152L), Some(6506L), Some(2513152L)))

  test("codex unwrapShell: the -lc script; anything else unchanged"):
    assertEquals(CodexCli.unwrapShell("/bin/zsh -lc 'ls src'"), "ls src")
    assertEquals(CodexCli.unwrapShell("bash -c \"cat <<'EOF' > a\nx\nEOF\""), "cat <<'EOF' > a\nx\nEOF")
    assertEquals(CodexCli.unwrapShell("/bin/zsh -lc 'a' && b"), "/bin/zsh -lc 'a' && b")
    assertEquals(CodexCli.unwrapShell("python3 -c 'print(1)'"), "python3 -c 'print(1)'")

  test("codex parse: turn.failed is an api error, still a result"):
    val t = codex().parse(lines(fx / "codex" / "exec-json-failed.jsonl").iterator)
    assert(t.hasResult)
    assertEquals(t.apiError, Some("unexpected status 401 Unauthorized"))
    assertEquals(t.toolCalls, Vector.empty)

  test("codex parse: no turn event, no result; junk lines skipped"):
    val t = codex().parse(Iterator("not json", "", """{"type":"thread.started"}"""))
    assert(!t.hasResult)
    assertEquals(t.finalText, "")

  test("codex command: exec --json, full access, cwd, model, mcp overrides, prompt last"):
    val c = ctx()
    assertEquals(codex().command(c, "do it"),
      List("codex", "exec", "--json", "--ephemeral", "--skip-git-repo-check", "--ignore-user-config", "--ignore-rules",
        "--dangerously-bypass-approvals-and-sandbox", "-C", c.repo.toString, "-m", "m-1",
        "-c", s"""mcp_servers.scala-semantic.command="$Home/.local/bin/scalasemantic-mcp"""",
        "-c", """mcp_servers.scala-semantic.args=["serve", "."]""", "--", "do it"))

  test("codex command: no mcp-servers, no overrides"):
    val c = ctx()
    assertEquals(codex("mcp-servers" -> ujson.Obj()).command(c, "p").takeRight(4), List("-m", "m-1", "--", "p"))

  // deferred minor (Task 17): clap read a prompt starting with `-` as an option (`codex exec -x`: "unexpected argument
  // '-x' found ... use '-- -x'"); `--` makes it the PROMPT (checked read-only: `codex exec -- -x extra` rejects only `extra`)
  test("codex command: `--` before the prompt, so a prompt starting with - stays the prompt"):
    assertEquals(codex().command(ctx(), "-x do it").takeRight(2), List("--", "-x do it"))

  test("codex setup: project-level .codex/config.toml with the MCP server"):
    val c = ctx()
    codex().setup(c).unsafeRunSync()
    val toml = os.read(c.repo / ".codex" / "config.toml")
    assert(toml.contains("[mcp_servers.scala-semantic]"), toml)
    assert(toml.contains(s"""command = "$Home/.local/bin/scalasemantic-mcp""""), toml)
    assert(toml.contains("""args = ["serve", "."]"""), toml)

  test("codex setup: no mcp-servers writes nothing"):
    val c = ctx()
    codex("mcp-servers" -> ujson.Obj()).setup(c).unsafeRunSync()
    assert(!os.exists(c.repo / ".codex"))

  test("codex env: Claude session vars unset"):
    val e = codex().env(ctx())
    assert(ClaudeCode.SessionVars.forall(v => e.get(v).contains(None)), e.toString)

  test("codex wipeState/collect: ephemeral run, nothing on disk"):
    val c = ctx()
    codex().wipeState(c).unsafeRunSync()
    codex().collect(c, 0L).unsafeRunSync()
    assert(!os.exists(c.runDir / "transcript.jsonl"))

  test("codex envLeaks: MCP servers beyond allowed-mcp"):
    val t = codex().parse(lines(fx / "codex" / "exec-json-sample.jsonl").iterator)
    assertEquals(codex().envLeaks(t, arm("t")), List("mcp_servers=other"))
    assertEquals(codex().envLeaks(codex().parse(Iterator.empty), arm("t")), Nil)

  test("codex privateStateDirs: the sessions dir, none of the run's own"):
    val home = os.temp.dir()
    val (forbidden, own) = codex("codex-home" -> ujson.Str(home.toString)).privateStateDirs(ctx())
    assertEquals(forbidden.map(_.toString), List(real(home) + "/sessions"))
    assertEquals(own, Nil)

  test("codex settingsArtefact: the project config"):
    val c = ctx()
    assertEquals(codex().settingsArtefact(c), Some(c.repo / ".codex" / "config.toml"))
    assertEquals(codex("mcp-servers" -> ujson.Obj()).settingsArtefact(c), None) // final review I1: setup wrote none

  // ---- gemini ----
  test("gemini parse: stream-json events"):
    val t = gemini().parse(lines(fx / "gemini" / "stream-json-sample.jsonl").iterator)
    assertEquals(t.toolCalls.map(_.name), Vector("run_shell_command", "replace", "write_file", "read_file", "grep_search",
      "mcp_scala-semantic_find_symbol", "mcp_other_x"))
    assertEquals(t.toolCalls.map(_.kind), Vector(ToolKind.Shell, ToolKind.Edit, ToolKind.Write, ToolKind.Read, ToolKind.Search,
      ToolKind.Mcp, ToolKind.Mcp))
    assertEquals(t.toolCalls.map(_.isError), Vector(false, false, false, true, false, false, false))
    assertEquals(t.toolCalls(0).command, "ls src")
    assertEquals(t.toolCalls(0).resultText, "Main.scala")
    assertEquals(t.toolCalls(3).resultText, "File not found")
    assertEquals(t.toolCalls(4).input("pattern").str, "Main")
    assertEquals(t.toolCalls.map(_.turn), Vector(1, 2, 3, 4, 5, 6, 7))
    assert(t.hasResult)
    assertEquals(t.apiError, None)
    assertEquals(t.finalText, "<final answer>")
    assert(t.texts.contains("Looking at the sources."), t.texts.toString)
    assertEquals((t.inputTokens, t.outputTokens, t.cacheReadTokens), (Some(1000L), Some(200L), Some(400L)))
    assertEquals(t.costUsd, None)
    assertEquals(t.init.map(_("model").str), Some("gemini-2.5-pro"))

  test("gemini parse: error result is an api error"):
    val t = gemini().parse(lines(fx / "gemini" / "stream-json-error.jsonl").iterator)
    assert(t.hasResult)
    assertEquals(t.apiError, Some("ApiError: quota exceeded"))

  test("gemini parse: no result event, no result"):
    val t = gemini().parse(lines(fx / "gemini" / "stream-json-sample.jsonl").iterator.filterNot(_.contains(""""type":"result"""")))
    assert(!t.hasResult)

  test("gemini command: headless yolo stream-json, model, no extensions, allowed MCP servers"):
    assertEquals(gemini().command(ctx(), "do it"),
      List("gemini", "-m", "m-1", "--output-format", "stream-json", "--approval-mode", "yolo", "-e", "none",
        "--allowed-mcp-server-names", "scala-semantic", "-p", "do it"))

  test("gemini command: no configured servers still passes the sentinel allow-list"):
    val argv = gemini(extra = Seq("mcp-servers" -> ujson.Obj(), "allowed-mcp" -> ujson.Arr())).command(ctx(), "p")
    assert(argv.containsSlice(List("-e", "none", "--allowed-mcp-server-names", "none")), argv.toString)

  test("generic: result comes from the exit code"):
    assert(generic().resultFromExit)
    assert(!codex().resultFromExit)
    assert(!gemini().resultFromExit)

  test("gemini setup: project .gemini/settings.json with mcpServers"):
    val c = ctx()
    gemini().setup(c).unsafeRunSync()
    val j = ujson.read(os.read(c.repo / ".gemini" / "settings.json"))
    assertEquals(j("mcpServers")("scala-semantic")("command").str, "" + Home + "/.local/bin/scalasemantic-mcp")
    assertEquals(j("mcpServers")("scala-semantic")("args").arr.map(_.str).toList, List("serve", "."))
    assertEquals(j("mcpServers")("scala-semantic")("timeout").num, 60000.0)

  test("gemini env: Claude session vars unset"):
    val e = gemini().env(ctx())
    assert(ClaudeCode.SessionVars.forall(v => e.get(v).contains(None)), e.toString)

  test("gemini wipeState: only the run copy's project dir under tmp/"):
    val home = os.temp.dir()
    val c = ctx()
    val repoReal = real(c.repo)
    os.write(home / "projects.json", ujson.write(ujson.Obj("projects" -> ujson.Obj(repoReal -> "run-copy", "/elsewhere" -> "other"))), createFolders = true)
    os.write(home / "tmp" / "run-copy" / "chats" / "s.json", "{}", createFolders = true)
    os.write(home / "tmp" / "other" / "chats" / "s.json", "{}", createFolders = true)
    gemini(home).wipeState(c).unsafeRunSync()
    assert(!os.exists(home / "tmp" / "run-copy"))
    assert(os.exists(home / "tmp" / "other" / "chats" / "s.json"))
    assert(os.exists(home / "projects.json"))

  test("gemini wipeState: no registry entry deletes nothing"):
    val home = os.temp.dir()
    os.write(home / "tmp" / "other" / "x", "1", createFolders = true)
    gemini(home).wipeState(ctx()).unsafeRunSync()
    assert(os.exists(home / "tmp" / "other" / "x"))

  test("gemini envLeaks: MCP servers beyond allowed-mcp"):
    val t = gemini().parse(lines(fx / "gemini" / "stream-json-sample.jsonl").iterator)
    assertEquals(gemini().envLeaks(t, arm("t")), List("mcp_servers=other"))

  // deferred minor (Task 17): tool names are mcp_<server>_<tool>; a server name with `_` was cut at its first `_`
  test("gemini envLeaks: server names with `_` are matched against configured / allowed names, not cut at the first `_`"):
    def use(n: String) = s"""{"type":"tool_use","tool_name":"$n","tool_id":"$n","parameters":{}}"""
    val g = gemini(extra = Seq(
      "mcp-servers" -> ujson.Obj("scala-semantic" -> ujson.Obj("command" -> "x"), "my_srv" -> ujson.Obj("command" -> "y")),
      "allowed-mcp" -> ujson.Arr("scala-semantic", "ok_srv")))
    val t = g.parse(Iterator(use("mcp_my_srv_find"), use("mcp_ok_srv_x"), use("mcp_scala-semantic_a_b"), use("mcp_other_x"),
      """{"type":"result","status":"success","stats":{}}"""))
    assertEquals(g.envLeaks(t, arm("t")), List("mcp_servers=my_srv,other"))

  test("gemini privateStateDirs: tmp/, own project dir from the registry"):
    val home = os.temp.dir()
    val c = ctx()
    os.write(home / "projects.json", ujson.write(ujson.Obj("projects" -> ujson.Obj(real(c.repo) -> "run-copy"))))
    val (forbidden, own) = gemini(home).privateStateDirs(c)
    assertEquals(forbidden.map(_.toString), List(real(home) + "/tmp"))
    assertEquals(own.map(_.toString), List(real(home) + "/tmp/run-copy"))

  test("gemini collect: stdout is the transcript"):
    val c = ctx()
    gemini().collect(c, 0L).unsafeRunSync()
    assert(!os.exists(c.runDir / "transcript.jsonl"))

  // ---- generic ----
  test("generic command: template placeholders substituted"):
    val c = ctx(model = "big")
    assertEquals(generic().command(c, "do it"), List("my-agent", "--model", "big", "--prompt", "do it"))
    assertEquals(generic("command" -> ujson.Arr("x", "--cd={repo}")).command(c, "p"), List("x", s"--cd=${c.repo}"))

  test("generic parse: transcript = none yields an empty transcript that has a result"):
    val t = generic().parse(Iterator("anything", "at all"))
    assert(t.hasResult)
    assertEquals(t.toolCalls, Vector.empty)
    assertEquals(t.finalText, "")
    assertEquals((t.turns, t.inputTokens, t.outputTokens, t.cacheReadTokens, t.costUsd), (None, None, None, None, None))
    assertEquals(t.apiError, None)
    assertEquals(t.texts, Vector.empty)

  test("generic collect: ensures an empty transcript.jsonl when stdout produced none"):
    val c = ctx()
    generic().collect(c, 0L).unsafeRunSync()
    assertEquals(os.read(c.runDir / "transcript.jsonl"), "")

  test("generic: env unsets Claude session vars; no setup, state, leaks, settings"):
    val c = ctx()
    val g = generic()
    assert(ClaudeCode.SessionVars.forall(v => g.env(c).get(v).contains(None)))
    g.setup(c).unsafeRunSync()
    g.wipeState(c).unsafeRunSync()
    assertEquals(os.list(c.repo).toList, Nil)
    assertEquals(g.envLeaks(g.parse(Iterator.empty), arm("t")), Nil)
    assertEquals(g.privateStateDirs(c), (Nil, Nil))
    assertEquals(g.settingsArtefact(c), None)

  test("generic command: missing template is an error"):
    val bad = Generic(AgentSpec("generic", "generic", ujson.Obj("adapter" -> "generic", "transcript" -> "none")))
    intercept[RuntimeException](bad.command(ctx(), "p"))
