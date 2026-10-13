package harness.agent

import cats.effect.IO
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import harness.config.*
import harness.core.{Gate, ToolKind}
import munit.CatsEffectSuite

/** The dsh adapter against a temp dsh home: never reads or writes the real ~/.dsh. */
class DshTest extends CatsEffectSuite:
  private val harnessDir: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .find(d => os.exists(d / "experiments" / "gate-ab.conf")).getOrElse(fail("harness dir not found"))
  private val fx = harnessDir / "test" / "fixtures" / "dsh"
  private val exp0: Experiment = Load.experiment(harnessDir / "experiments" / "gate-ab.conf").fold(fail(_), identity)
  private val task: TaskSpec = TaskSpec.load(exp0.tasksDir, exp0.tasks.head).fold(fail(_), identity)
  private def arm(n: String): ArmSpec = exp0.armSpecs(n).copy(agent = "dsh", model = "deepseek-pro")

  /** agents/dsh.conf as Load decodes it: the resolved `agents.dsh` object. */
  private val conf: ujson.Obj =
    val c = ConfigFactory.parseFile((harnessDir / "agents" / "dsh.conf").toIO).withFallback(ConfigFactory.systemEnvironment()).resolve()
    ujson.read(c.getObject("agents.dsh").render(ConfigRenderOptions.concise().setJson(true))) match
      case o: ujson.Obj => o
      case other        => fail(s"agents.dsh: $other")

  private def spec(home: os.Path, extra: (String, ujson.Value)*): AgentSpec =
    val o = ujson.Obj.from(conf.value.toSeq ++ Seq("dsh-home" -> ujson.Str(home.toString)) ++ extra)
    AgentSpec("dsh", "dsh", o)
  private def agent(home: os.Path = os.temp.dir(), extra: Seq[(String, ujson.Value)] = Nil): Dsh = Dsh(spec(home, extra*))

  private def ctx(a: String, repo: os.Path = os.temp.dir() / "repo"): RunCtx =
    os.makeDir.all(repo)
    RunCtx(exp0, arm(a), task, s"t-$a-1", repo / os.up, repo, None, None)

  private def sample: Vector[String] = os.read.lines(fx / "session-sample.jsonl").toVector
  private def real(p: os.Path): String = p.toNIO.toRealPath().toString

  // ---- conf ----
  test("agents/dsh.conf: brief's knobs"):
    assertEquals(conf("adapter").str, "dsh")
    assertEquals(conf("bin").str, "dsh")
    assertEquals(conf("profile").str, "scala-headless")
    assertEquals(conf("hooks-from").str, ".dsh/hooks.json")
    assertEquals(conf("prices"), ujson.Obj("deepseek-flash" -> ujson.Obj(
      "input-per-mtok" -> 0.30, "output-per-mtok" -> 1.20, "cache-read-per-mtok" -> 0.006)))
    assertEquals(conf("dsh-home").str, sys.env.getOrElse("DSH_HOME", s"${sys.env("HOME")}/.dsh"))

  test("Agent.of: dsh resolves"):
    assert(Agent.of(spec(os.temp.dir())).exists(_.isInstanceOf[Dsh]))

  // ---- slug ----
  test("slug: the real cwd with / as -, wrapped in --"):
    assertEquals(Dsh.dshSlug("/Users/viktorskalinins/IdeaProjects/skreeep2"), "--Users-viktorskalinins-IdeaProjects-skreeep2--")
    assertEquals(Dsh.dshSlug("/Users/viktorskalinins"), "--Users-viktorskalinins--")
    assertEquals(Dsh.dshSlug("/a/skreeep2/.worktrees/x_y"), "--a-skreeep2-.worktrees-x_y--")

  // ---- parse ----
  test("parse: real session sample"):
    val t = agent(extra = Seq("prices" -> ujson.Obj())).parse(sample.iterator)
    assertEquals(t.toolCalls.map(_.name), Vector("bash", "ask_user_question", "grep"))
    assertEquals(t.toolCalls.map(_.kind), Vector(ToolKind.Shell, ToolKind.Other, ToolKind.Search))
    assertEquals(t.toolCalls.map(_.isError), Vector(true, true, false))
    assert(t.toolCalls(0).command.contains(".dsh/policy/kernels.tsv"), t.toolCalls(0).input.toString)
    assertEquals(t.toolCalls(0).resultText, "Error: BLOCKED by tool-policy: <frozen path reason>")
    assertEquals(t.toolCalls(2).input("pattern").str, "# probe")
    assertEquals(t.toolCalls.map(_.turn), Vector(1, 2, 3))
    assertEquals(Gate.gateBlocks(t.toolCalls, t.hookTexts), Map("tool-policy" -> 1, "turn-end" -> 1))
    assert(t.hasResult)
    assertEquals(t.apiError, None)
    assertEquals(t.finalText, "<final answer>")
    assertEquals(t.turns, Some(5))
    assertEquals((t.inputTokens, t.outputTokens, t.cacheReadTokens), (Some(17714L), Some(4047L), Some(111744L)))
    assertEquals(t.costUsd, None)
    assertEquals(t.hookEvents.distinct, Vector("PreToolUse", "Stop"))
    assertEquals(t.init.map(_("model").str), Some("deepseek-flash"))

  // Task 20: captured from the live fr-pan-dsh-gs-1 run (texts shortened). A PreToolUse block reaches the agent as the tool
  // result (as in Claude Code), so it is not a hook text; a tool-policy text that mentions turn-end is no turn-end block.
  test("parse: live PreToolUse blocks count once per blocked call, never as turn-end"):
    val t = agent().parse(os.read.lines(fx / "session-gs-blocks.jsonl").iterator)
    assertEquals(t.toolCalls.map(_.isError), Vector(true, true))
    assertEquals(t.hookTexts, Vector.empty)
    assertEquals(Gate.gateBlocks(t.toolCalls, t.hookTexts), Map("tool-policy" -> 2))
    assertEquals(agent().envLeaks(t, arm("t")), Nil) // no turn-end leak in arm t either

  test("parse: cost from prices(model) of the request header's model"):
    val p = ujson.Obj("deepseek-flash" -> ujson.Obj("input-per-mtok" -> 1.0, "output-per-mtok" -> 2.0, "cache-read-per-mtok" -> 0.5))
    val t = agent(extra = Seq("prices" -> p)).parse(sample.iterator)
    assertEqualsDouble(t.costUsd.getOrElse(-1.0), (17714 * 1.0 + 4047 * 2.0 + 111744 * 0.5) / 1e6, 1e-12)
    val other = ujson.Obj("deepseek-pro" -> ujson.Obj("input-per-mtok" -> 1.0, "output-per-mtok" -> 2.0, "cache-read-per-mtok" -> 0.5))
    assertEquals(agent(extra = Seq("prices" -> other)).parse(sample.iterator).costUsd, None)

  test("parse: tool kinds"):
    assertEquals(List("bash", "edit", "write", "read", "grep", "glob", "mcp__scalasemantic__find_symbol", "lsp").map(Dsh.kindOf),
      List(ToolKind.Shell, ToolKind.Edit, ToolKind.Write, ToolKind.Read, ToolKind.Search, ToolKind.Search, ToolKind.Mcp, ToolKind.Other))

  private def ev(tpe: String, data: ujson.Obj): String = ujson.write(ujson.Obj("type" -> tpe, "data" -> data))
  private def call(id: String, name: String, args: String) = ev("tool/call", ujson.Obj("turn" -> 1, "step" -> 1, "callId" -> id, "name" -> name, "arguments" -> args))
  private def result(id: String, text: String, isError: Boolean, error: Boolean = false) =
    val d = ujson.Obj("turn" -> 1, "step" -> 1, "message" -> ujson.Obj("content" -> ujson.Arr(ujson.Obj("type" -> "tool-result", "toolCallId" -> id,
      "content" -> ujson.Arr(ujson.Obj("type" -> "text", "text" -> text)), "isError" -> isError))))
    if error then d("error") = ujson.Obj("name" -> "X", "code" -> "Y")
    ev("tool/result", d)

  test("parse: tool/result error from Error: prefix or an error flag; bad arguments keep an empty input"):
    val t = agent().parse(Iterator(
      call("a", "read", """{"file_path":"x"}"""), result("a", "Error: no such file", isError = false),
      call("b", "bash", "not json"), result("b", "partial", isError = false, error = true),
      call("c", "write", """{"file_path":"y"}"""), result("c", "ok", isError = false)))
    assertEquals(t.toolCalls.map(_.isError), Vector(true, true, false))
    assertEquals(t.toolCalls(1).input, ujson.Obj())
    assertEquals(t.toolCalls(0).resultText, "Error: no such file")

  test("parse: turn/end error is an api error; no turn/end is no result"):
    val err = ev("turn/end", ujson.Obj("turn" -> 1, "reason" -> ujson.Obj("kind" -> "error",
      "error" -> ujson.Obj("message" -> "Insufficient Balance", "code" -> "QUOTA", "status" -> 402))))
    val t = agent().parse(Iterator(err))
    assert(t.hasResult)
    assertEquals(t.apiError, Some("402 QUOTA: Insufficient Balance"))
    val none = agent().parse(sample.filterNot(_.contains("\"turn/end\"")).iterator)
    assert(!none.hasResult)
    val empty = agent().parse(Iterator.empty)
    assert(!empty.hasResult); assertEquals(empty.turns, None); assertEquals(empty.inputTokens, None)

  // ---- envLeaks ----
  test("envLeaks: hooks only leak in ungated arms; turn-end block leaks in arm t"):
    val t = agent().parse(sample.iterator)
    assertEquals(agent().envLeaks(t, arm("g")), Nil)
    assertEquals(agent().envLeaks(t, arm("u")), List("hooks=PreToolUse,Stop"))
    assertEquals(agent().envLeaks(t, arm("t")), List("turn-end in arm t"))

  test("envLeaks: MCP servers other than allowed-mcp; no request header"):
    val extra = sample.map(l => if l.contains("\"request/header\"") then l.replace("\"name\":\"bash\"", "\"name\":\"mcp__browser__navigate\"") else l)
    assertEquals(agent().envLeaks(agent().parse(extra.iterator), arm("g")), List("mcp_servers=browser"))
    val noHeader = sample.filterNot(_.contains("\"request/header\""))
    assertEquals(agent().envLeaks(agent().parse(noHeader.iterator), arm("g")), List("no init event"))

  // ---- state ----
  test("wipeState removes only the run copy's own session dir"):
    val home = os.temp.dir(); val c = ctx("u")
    val mine = home / "sessions" / Dsh.dshSlug(real(c.repo))
    val other = home / "sessions" / "--other--"
    os.makeDir.all(mine / "s1"); os.makeDir.all(other / "s2"); os.write(home / "profiles" / "p" / "f", "x", createFolders = true)
    agent(home).wipeState(c).map: _ =>
      assert(!os.exists(mine)); assert(os.exists(other / "s2")); assert(os.exists(home / "profiles" / "p" / "f"))

  test("privateStateDirs: sessions forbidden, own slug allowed"):
    val home = os.temp.dir(); val c = ctx("u")
    val (forb, own) = agent(home).privateStateDirs(c)
    val sessions = os.Path(real(home)) / "sessions"
    assertEquals(forb.head, sessions)
    assertEquals(own, List(sessions / Dsh.dshSlug(real(c.repo))))

  // Final review M1: the run dir is exempt from the fence, but dsh reads its overlay and hook map from there; both are
  // forbidden, so a tool call that edits (or reads) either is contamination
  test("privateStateDirs: the run dir's dsh-overlay.yml and dsh-hooks.json are fenced; an edit of either is flagged"):
    val home = os.temp.dir(); val c = ctx("gs")
    val a = agent(home)
    val (forb, _) = a.privateStateDirs(c)
    assert(forb.contains(c.runDir / "dsh-overlay.yml") && forb.contains(c.runDir / "dsh-hooks.json"), forb.toString)
    val fence = harness.run.Score.fence(c.exp, c.runDir, (forb, a.privateStateDirs(c)._2))
    def call(name: String, kv: (String, ujson.Value)*) = harness.core.ToolCall(name, Dsh.kindOf(name), ujson.Obj.from(kv), "", false, 1)
    val rd = harness.core.Contamination.realpath(c.runDir.toString)
    assertEquals(harness.core.Contamination.check(List(
      call("bash", "command" -> "sed -i '' 's/turn-end/x/' ../dsh-hooks.json"),
      call("edit", "path" -> (c.runDir / "dsh-overlay.yml").toString, "old_string" -> "a", "new_string" -> "b"),
      call("bash", "command" -> "cat src/A.scala ../final.diff")), fence),
      List(s"bash: $rd/dsh-hooks.json", s"edit: $rd/dsh-overlay.yml"))

  private def zst(file: os.Path, lines: String*): Unit =
    os.makeDir.all(file / os.up)
    val out = com.github.luben.zstd.ZstdOutputStream(java.io.FileOutputStream(file.toIO))
    try out.write(lines.map(_ + "\n").mkString.getBytes("UTF-8")) finally out.close()
  private def header(id: String, at: Long) = ujson.write(ujson.Obj("type" -> "session", "version" -> 3, "id" -> id, "createdAt" -> ujson.Num(at.toDouble)))

  test("collect: newest session created after start (Review Focus 5)"):
    val home = os.temp.dir(); val c = ctx("g"); val start = 1_790_000_000_000L
    val dir = home / "sessions" / Dsh.dshSlug(real(c.repo))
    zst(dir / "old" / "session.v3.jsonl.zstd", header("old", start - 1000), """{"type":"x","n":"old"}""")
    zst(dir / "b4e7" / "session.v3.jsonl.zstd", header("mid", start + 10), """{"type":"x","n":"mid"}""")
    zst(dir / "session-new" / "session.jsonl.zstd", header("new", start + 20), """{"type":"x","n":"new"}""")
    os.write(dir / "session-new" / "session.lock", "")
    os.write.over(c.runDir / "transcript.jsonl", "final answer on stdout\n")
    agent(home).collect(c, start).map: _ =>
      assertEquals(os.read(c.runDir / "transcript.jsonl"), header("new", start + 20) + "\n" + """{"type":"x","n":"new"}""" + "\n")
      assert(os.exists(dir / "old"))

  test("collect: a delegated child session (parentSession, delegationDepth 1) is never the run's session"):
    val home = os.temp.dir(); val c = ctx("g"); val start = 1_790_000_000_000L
    val dir = home / "sessions" / Dsh.dshSlug(real(c.repo))
    zst(dir / "parent" / "session.v3.jsonl.zstd", header("parent", start + 10), """{"type":"x","n":"parent"}""")
    val child = ujson.write(ujson.Obj("type" -> "session", "version" -> 3, "id" -> "child", "createdAt" -> ujson.Num((start + 50).toDouble),
      "delegationDepth" -> 1, "parentSession" -> "parent"))
    zst(dir / "child" / "session.v3.jsonl.zstd", child, """{"type":"x","n":"child"}""")
    agent(home).collect(c, start).map: _ =>
      assertEquals(os.read(c.runDir / "transcript.jsonl"), header("parent", start + 10) + "\n" + """{"type":"x","n":"parent"}""" + "\n")

  // deferred minor (Task 20): a `subagent` call's child session (own dir under the slug) was not in the run's tokens
  private def sess(id: String, at: Long, parent: Option[String], in: Long, out: Long, cache: Long): Seq[String] =
    Seq(ujson.write(ujson.Obj.from(Seq("type" -> ujson.Str("session"), "version" -> ujson.Num(3), "id" -> ujson.Str(id),
        "createdAt" -> ujson.Num(at.toDouble)) ++ parent.toSeq.flatMap(p => Seq("parentSession" -> ujson.Str(p), "delegationDepth" -> ujson.Num(1))))),
      """{"type":"request/header","data":{"header":{"config":{"provider":"p","model":"deepseek-flash"},"tools":[]}}}""",
      """{"type":"step/start","data":{}}""",
      s"""{"type":"assistant/message","data":{"message":{"content":[]},"usage":{"inputTokens":$in,"outputTokens":$out,"cacheReadTokens":$cache}}}""",
      """{"type":"turn/end","data":{"reason":{"kind":"completed"}}}""")

  test("withDelegated: child sessions of the run's session (transitively, created at/after start) add their tokens, not turns"):
    val home = os.temp.dir(); val c = ctx("g"); val start = 1_790_000_000_000L
    val dir = home / "sessions" / Dsh.dshSlug(real(c.repo))
    val top = sess("top", start + 10, None, 100, 10, 1000)
    zst(dir / "top" / "session.v3.jsonl.zstd", top*)
    zst(dir / "kid" / "session.v3.jsonl.zstd", sess("kid", start + 20, Some("top"), 7, 3, 50)*)
    zst(dir / "grandkid" / "session.v3.jsonl.zstd", sess("grandkid", start + 30, Some("kid"), 1, 1, 1)*)
    zst(dir / "other-kid" / "session.v3.jsonl.zstd", sess("other-kid", start + 20, Some("other"), 1000, 1000, 1000)*)
    zst(dir / "early-kid" / "session.v3.jsonl.zstd", sess("early-kid", start - 1, Some("top"), 1000, 1000, 1000)*)
    val priced = agent(home, Seq("prices" -> ujson.Obj("deepseek-flash" -> ujson.Obj(
      "input-per-mtok" -> 1.0, "output-per-mtok" -> 1.0, "cache-read-per-mtok" -> 1.0))))
    val t0 = priced.parse(top.iterator)
    priced.withDelegated(c, t0, Some(start)).map: t =>
      assertEquals((t.inputTokens, t.outputTokens, t.cacheReadTokens), (Some(108L), Some(14L), Some(1051L)))
      assertEquals(t.turns, t0.turns)
      assertEquals(t.costUsd.map(c => math.round(c * 1e9)), Some(1173L * 1000L))
      assertEquals(t.toolCalls, t0.toolCalls)

  test("withDelegated: no child sessions, no init or no slug dir leave the transcript as it is"):
    val home = os.temp.dir(); val c = ctx("g")
    val t0 = agent(home).parse(sess("top", 5, None, 1, 1, 1).iterator)
    for
      a <- agent(home).withDelegated(c, t0, None)
      b <- agent(home).withDelegated(c, agent(home).parse(Iterator.empty), None)
    yield
      assertEquals(a, t0)
      assertEquals(b, agent(home).parse(Iterator.empty))

  test("collect: no session after start leaves an empty transcript (no result event)"):
    val home = os.temp.dir(); val c = ctx("g"); val start = 1_790_000_000_000L
    val dir = home / "sessions" / Dsh.dshSlug(real(c.repo))
    zst(dir / "old" / "session.v3.jsonl.zstd", header("old", start - 1), """{"type":"turn/end","data":{"reason":{"kind":"completed"}}}""")
    os.write.over(c.runDir / "transcript.jsonl", "final answer on stdout\n")
    for
      _ <- agent(home).collect(c, start)
      t1 <- IO.blocking(os.read(c.runDir / "transcript.jsonl"))
      c2 = ctx("g")
      _ <- agent(home).collect(c2, start) // no slug dir at all
      t2 <- IO.blocking(os.read(c2.runDir / "transcript.jsonl"))
    yield
      assertEquals(t1, ""); assertEquals(t2, "")
      assert(!agent(home).parse(t1.linesIterator).hasResult)

  // ---- setup / afterSteps / command / env ----
  private val dump: String =
    """# == @deepseek-ai/dsh-base
      |- id: agent
      |  name: '@deepseek-ai/dsh-agent'
      |- id: agent-default-model
      |  name: '@deepseek-ai/dsh-agent-default-model'
      |  config:
      |    provider: deepseek-official
      |    model: deepseek-flash
      |- id: sandbox-policy
      |  name: '@deepseek-ai/dsh-sandbox-policy'
      |  config:
      |    mode: !!js process.env.DSH_PERMISSION_MODE ?? 'danger-full-access'
      |    workspaceRoot: !!js process.cwd()
      |# == /tmp/home/profiles/scala-headless/cordis.patch.yml
      |- name: '@deepseek-ai/dsh-mcp-client'
      |  config:
      |    serverName: scalasemantic
      |    command: !!js process.cwd() + '/scalasemantic-mcp.sh'
      |- id: claude-code-hooks
      |  name: '@deepseek-ai/dsh-hooks-claude-code'
      |  config:
      |    configPath: /Users/x/.claude/settings.json
      |""".stripMargin

  /** A fake dsh: records argv and DSH_HOME, prints the dump. */
  private def fakeBin(dumpText: String): (os.Path, os.Path) =
    val d = os.temp.dir()
    os.write(d / "dump.yml", dumpText)
    val script = d / "dsh"
    os.write(script, s"#!/bin/sh\nprintf '%s\\n' \"$$@\" \"$$DSH_HOME\" \"$$(pwd -P)\" >'$d/argv'\ncat '$d/dump.yml'\n")
    os.perms.set(script, "rwxr-xr-x")
    (script, d / "argv")

  private val hooksMap = """{"PreToolUse":[{"matcher":"bash","hooks":[{"type":"command","command":"sh gate.sh tool-policy"}]}]}"""

  test("setup + afterSteps, gated arm: overlay names the profile's rows, hooks file is the run copy's map"):
    val home = os.temp.dir(); val c = ctx("g")
    val (bin, argv) = fakeBin(dump)
    val a = agent(home, Seq("bin" -> ujson.Str(bin.toString)))
    for
      _ <- a.setup(c)
      before = os.exists(c.runDir / "dsh-hooks.json")
      _ <- IO.blocking(os.write(c.repo / ".dsh" / "hooks.json", hooksMap, createFolders = true)) // arm setup steps write it
      _ <- a.afterSteps(c)
    yield
      assertEquals(os.read.lines(argv).toList, List("--profile", "scala-headless", "--dump-config", home.toString, real(c.repo)))
      assertEquals(os.read(c.runDir / "dsh-overlay.yml"),
        s"""- id: claude-code-hooks
           |  config:
           |    configPath: ${c.runDir}/dsh-hooks.json
           |- id: agent-default-model
           |  config:
           |    provider: deepseek-official
           |    model: deepseek-pro
           |""".stripMargin)
      assert(!before, "hooks file is written after the arm's setup steps")
      assertEquals(ujson.read(os.read(c.runDir / "dsh-hooks.json")), ujson.Obj("hooks" -> ujson.read(hooksMap)))
      assertEquals(a.settingsArtefact(c), Some(c.runDir / "dsh-overlay.yml"))

  test("setup + afterSteps, arm u: empty hook map even when the run copy has one"):
    val home = os.temp.dir(); val c = ctx("u")
    val (bin, _) = fakeBin(dump)
    val a = agent(home, Seq("bin" -> ujson.Str(bin.toString)))
    os.write(c.repo / ".dsh" / "hooks.json", hooksMap, createFolders = true)
    (a.setup(c) *> a.afterSteps(c)).map: _ =>
      assertEquals(os.read(c.runDir / "dsh-hooks.json"), """{"hooks":{}}""" + "\n")
      assert(os.read(c.runDir / "dsh-overlay.yml").contains(s"configPath: ${c.runDir}/dsh-hooks.json"))

  test("setup writes the run copy's scalasemantic-mcp.sh launcher when scalasemantic-command is set, none otherwise"):
    val home = os.temp.dir(); val c = ctx("u"); val c2 = ctx("u")
    val (bin, _) = fakeBin(dump)
    val tool = os.temp.dir() / "fake-mcp"
    os.write(tool, "#!/bin/sh\nprintf '%s|' \"$@\"\n"); os.perms.set(tool, "rwxr-xr-x")
    val withCmd = agent(home, Seq("bin" -> ujson.Str(bin.toString), "scalasemantic-command" -> ujson.Str(tool.toString)))
    val without = Dsh(AgentSpec("dsh", "dsh", ujson.Obj.from(spec(home, "bin" -> ujson.Str(bin.toString)).raw.value.toSeq
      .filterNot(_._1 == "scalasemantic-command"))))
    os.proc("git", "init", "-q").call(cwd = c.repo)
    for
      _ <- withCmd.setup(c)
      _ <- without.setup(c2)
    yield
      val sh = c.repo / "scalasemantic-mcp.sh"
      assert(os.perms(sh).toString.startsWith("rwx"), "launcher is executable")
      // the profile runs `<cwd>/scalasemantic-mcp.sh serve .`: the launcher passes its args through
      assertEquals(os.proc(sh, "serve", ".").call(cwd = c.repo).out.text(), "serve|.|")
      assert(!os.exists(c2.repo / "scalasemantic-mcp.sh"))
      // final.diff runs `git add -A -N`: the launcher must stay out of it, as .mcp.json does via skreeep2's .gitignore
      assertEquals(os.proc("git", "status", "--porcelain", "--", "scalasemantic-mcp.sh").call(cwd = c.repo).out.text(), "")

  test("setup fails when the profile lacks the hooks or the default-model row"):
    val c = ctx("g")
    val noHooks = fakeBin(dump.replace("- id: claude-code-hooks\n  name", "- name"))._1 // the source profile's id-less bridge row
    val noModel = fakeBin(dump.replace("'@deepseek-ai/dsh-agent-default-model'", "'@other/model'"))._1
    for
      r1 <- agent(extra = Seq("bin" -> ujson.Str(noHooks.toString))).setup(c).attempt
      r2 <- agent(extra = Seq("bin" -> ujson.Str(noModel.toString))).setup(c).attempt
    yield
      assertEquals(r1.left.map(_.getMessage), Left("dsh: profile scala-headless has no @deepseek-ai/dsh-hooks-claude-code row (see dsh --dump-config)"))
      assertEquals(r2.left.map(_.getMessage), Left("dsh: profile scala-headless has no @deepseek-ai/dsh-agent-default-model row (see dsh --dump-config)"))

  // deferred minor (Task 16): only the first bridge row was retargeted; a second one would still load its own hook file
  test("setup fails when the profile has more than one hooks bridge row"):
    val two = fakeBin(dump + "- id: more-hooks\n  name: '@deepseek-ai/dsh-hooks-claude-code'\n  config:\n    configPath: /x.json\n")._1
    agent(extra = Seq("bin" -> ujson.Str(two.toString))).setup(ctx("g")).attempt.map: r =>
      assertEquals(r.left.map(_.getMessage), Left("dsh: profile scala-headless has 2 @deepseek-ai/dsh-hooks-claude-code rows " +
        "(claude-code-hooks, more-hooks); a run's overlay retargets only one (see dsh --dump-config)"))

  test("afterSteps fails when a gated arm's run copy has no hook map"):
    val c = ctx("g")
    agent().afterSteps(c).attempt.map: r =>
      assertEquals(r.left.map(_.getMessage), Left(s"dsh: hooks-from ${c.repo / ".dsh" / "hooks.json"} missing"))

  test("command and env"):
    val home = os.temp.dir(); val c = ctx("g")
    val a = agent(home)
    assertEquals(a.command(c, "do it"), List("dsh", "--profile", "scala-headless", "--patch", (c.runDir / "dsh-overlay.yml").toString, "do it"))
    val e = a.env(c)
    assertEquals(e("DSH_PERMISSION_MODE"), Some("danger-full-access"))
    assertEquals(e("CLAUDE_PROJECT_DIR"), Some(c.repo.toString))
    assertEquals(e("DSH_HOME"), Some(home.toString))
    ClaudeCode.SessionVars.foreach(v => assertEquals(e(v), None, v))

  // ---- DshProfile ----
  private def homeWithScala(): os.Path =
    val home = os.temp.dir()
    os.makeDir.all(home / "profiles" / "scala")
    List("package.json", "cordis.patch.yml").foreach(f => os.copy(fx / "profile-scala" / f, home / "profiles" / "scala" / f))
    home

  test("DshProfile.derive: headless bundles, TUI rows and the browser MCP dropped, the rest kept"):
    val home = homeWithScala()
    DshProfile.derive(home, "scala").map: dir =>
      assertEquals(dir, home / "profiles" / "scala-headless")
      val pkg = ujson.read(os.read(dir / "package.json"))
      assertEquals(pkg("dsh")("profile")("bundles"), ujson.Arr("@deepseek-ai/dsh-base", "dsh-cc-skills", "@jasonfreelab/dsh-superpowers",
        "oh-my-dsh-slim", "@deepseek-ai/dsh-headless"))
      val deps = pkg("dependencies").obj.keySet
      assert(!deps.exists(d => d.startsWith("@deepseek-harness-tui/") || d == "dsh-tui-beep" || d == "dsh-deepseek-budget"), deps.toString)
      List("@deepseek-ai/dsh-lsp-stdio", "dsh-cc-skills", "@jasonfreelab/dsh-superpowers", "oh-my-dsh-slim").foreach(d => assert(deps.contains(d), deps.toString))
      assertEquals(os.read(dir / "cordis.yml").trim, "[]")
      val text = os.read(dir / "cordis.patch.yml")
      val rows = DshProfile.rows(Yaml.load(text))
      def has(p: Yaml.Row => Boolean) = rows.exists(p)
      assert(has(_.id.contains("lsp-stdio")))
      assert(has(r => r.name.contains("@deepseek-ai/dsh-mcp-client") && r.serverName.contains("scalasemantic")))
      assert(has(r => r.name.contains("@deepseek-ai/dsh-hooks-claude-code") && r.id.contains("claude-code-hooks")))
      assert(has(_.id.contains("sandbox-policy"))); assert(has(_.id.contains("permission")))
      assert(!has(_.serverName.contains("browser")))
      assert(!has(r => r.id.exists(Set("tui-beep", "deepseek-budget")) || r.name.exists(Set("dsh-tui-beep", "dsh-deepseek-budget"))))
      // !!js round-trips: written with its tag (the emitter quotes explicitly tagged scalars), read back as the same expression
      def tagged(o: Object): List[Yaml.Tagged] = o match
        case t: Yaml.Tagged         => List(t)
        case l: java.util.List[?]   => l.toArray.toList.flatMap(x => tagged(x.asInstanceOf[Object]))
        case m: java.util.Map[?, ?] => m.values.toArray.toList.flatMap(x => tagged(x.asInstanceOf[Object]))
        case _                      => Nil
      val Js = "tag:yaml.org,2002:js"
      val js = tagged(Yaml.load(text)).map(t => t.tag -> t.value)
      assertEquals(js, tagged(Yaml.load(os.read(fx / "profile-scala" / "cordis.patch.yml"))).map(t => t.tag -> t.value))
      assert(js.contains(Js -> "process.cwd() + '/scalasemantic-mcp.sh'"), js.toString)
      assert(js.contains(Js -> "process.env.DSH_PERMISSION_MODE ?? 'danger-full-access'"), js.toString)
      assert(text.contains("command: !!js 'process.cwd() + ''/scalasemantic-mcp.sh'''"), text)

  test("DshProfile.derive refuses a source with more than one hooks bridge row"):
    val home = homeWithScala()
    val src = home / "profiles" / "scala" / "cordis.patch.yml"
    os.write.append(src, "- name: '@deepseek-ai/dsh-hooks-claude-code'\n  config:\n    configPath: /x.json\n")
    DshProfile.derive(home, "scala").attempt.map: r =>
      assertEquals(r.left.map(_.getMessage),
        Left(s"dsh-profile: $src has 2 @deepseek-ai/dsh-hooks-claude-code rows; a run's overlay retargets only one"))
      assert(!os.exists(home / "profiles" / "scala-headless"))

  test("DshProfile.derive refuses an existing target"):
    val home = homeWithScala()
    os.makeDir.all(home / "profiles" / "scala-headless")
    os.write(home / "profiles" / "scala-headless" / "keep", "x")
    DshProfile.derive(home, "scala").attempt.map: r =>
      assertEquals(r.left.map(_.getMessage), Left(s"dsh-profile: ${home / "profiles" / "scala-headless"} exists (remove it first)"))
      assertEquals(os.read(home / "profiles" / "scala-headless" / "keep"), "x")

  test("DshProfile.derive: missing source profile"):
    val home = os.temp.dir()
    DshProfile.derive(home, "nope").attempt.map: r =>
      assertEquals(r.left.map(_.getMessage), Left(s"dsh-profile: ${home / "profiles" / "nope" / "package.json"} missing"))

  test("cli: dsh-profile --from derives into the dsh home and prints the install command"):
    val home = homeWithScala()
    for
      out <- cats.effect.Ref[IO].of("")
      err <- cats.effect.Ref[IO].of("")
      term = harness.cli.Term(s => out.update(_ + s), s => err.update(_ + s + "\n"))
      code <- harness.cli.Cli.run(List("dsh-profile", "--from", "scala"), _ => fail("no experiment"), term, home)
      o <- out.get
      e <- err.get
    yield
      assertEquals(code.code, 0, e)
      assert(os.isFile(home / "profiles" / "scala-headless" / "cordis.patch.yml"))
      assertEquals(o, s"wrote ${home / "profiles" / "scala-headless"}\ninstall its packages: dsh plugin --profile scala-headless install\n")
