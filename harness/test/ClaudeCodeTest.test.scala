package harness.agent

import munit.CatsEffectSuite
import harness.config.*

class ClaudeCodeTest extends CatsEffectSuite:
  private val harnessDir: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .find(d => os.exists(d / "experiments" / "gate-ab.conf")).getOrElse(fail("harness dir not found"))
  private val fx = harnessDir / "test" / "fixtures" / "transcripts"
  private val exp0: Experiment = Load.experiment(harnessDir / "experiments" / "gate-ab.conf").fold(fail(_), identity)
  private val taskName = exp0.tasks.head
  private val task: TaskSpec = TaskSpec.load(exp0.tasksDir, taskName).fold(fail(_), identity)
  private def arm(n: String): ArmSpec = exp0.armSpecs(n)
  private def agent(home: os.Path = os.temp.dir(), cfg: Option[String] = None): ClaudeCode =
    ClaudeCode(exp0.agents("claude-code"), home, cfg)

  private def ctx(a: String, repo: os.Path = os.temp.dir(), holdout: Option[os.Path] = None, snap: Option[os.Path] = None,
                  holdoutRepo: Option[os.Path] = None): RunCtx =
    val exp = holdoutRepo.fold(exp0)(h => exp0.copy(target = exp0.target.copy(holdoutRepo = h)))
    RunCtx(exp, arm(a), task, s"$taskName-$a-1", repo / os.up, repo, holdout, snap)

  private def lines(name: String): Vector[String] = os.read.lines(fx / name).toVector
  private def t(ls: Seq[String]) = agent().parse(ls.iterator)
  private val hookEv = ujson.write(ujson.Obj("type" -> "system", "subtype" -> "hook_started", "hook_name" -> "PreToolUse:Bash", "hook_event" -> "PreToolUse"))
  private val sessionHook = ujson.write(ujson.Obj("type" -> "system", "subtype" -> "hook_started", "hook_name" -> "SessionStart:startup", "hook_event" -> "SessionStart"))
  private def leaks(ls: Seq[String], a: String) = agent().envLeaks(t(ls), arm(a))

  // ---- GA EnvLeak (test_score.py:134-188), case for case ----
  test("real_leaked_ungated"):
    val l = leaks(lines("real-init-leaked-u.jsonl"), "u")
    assertEquals(l.size, 3, l.toString)
    assert(l(0).contains("playwright")); assert(l(1).contains("caveman")); assert(l(2).contains("SessionStart"))

  test("real_leaked_gated_hooks_allowed"):
    val l = leaks(lines("real-init-leaked-g.jsonl"), "g")
    assertEquals(l.size, 2, l.toString)
    assert(l(0).startsWith("mcp_servers=")); assert(l(1).startsWith("plugins="))

  test("isolated_init_clean_in_both_arms"):
    val ls = lines("real-init-isolated.jsonl")
    assertEquals(leaks(ls, "u"), Nil); assertEquals(leaks(ls, "g"), Nil)

  test("hook_event_leaks_only_ungated"):
    val ls = lines("real-init-isolated.jsonl") :+ hookEv
    assertEquals(leaks(ls, "g"), Nil); assertEquals(leaks(ls, "u"), List("hooks=PreToolUse:Bash"))

  test("arm_p_hooks_are_not_a_leak"):
    val ls = lines("real-init-isolated.jsonl") :+ hookEv
    assertEquals(leaks(ls, "p"), Nil); assertEquals(leaks(ls, "u"), List("hooks=PreToolUse:Bash"))

  test("arms_gf_gs_hooks_are_not_a_leak"):
    val ls = lines("real-init-isolated.jsonl") :+ sessionHook
    assertEquals(leaks(ls, "gf"), Nil); assertEquals(leaks(ls, "gs"), Nil)
    assertEquals(leaks(ls, "u"), List("hooks=SessionStart:startup"))

  test("arm_t_hooks_are_not_a_leak"):
    val ls = lines("real-init-isolated.jsonl") :+ hookEv
    assertEquals(leaks(ls, "t"), Nil); assertEquals(leaks(ls, "u"), List("hooks=PreToolUse:Bash"))

  test("arm_t_turn_end_is_a_leak"):
    val ev = ujson.write(ujson.Obj("type" -> "user", "message" -> ujson.Obj("content" -> ujson.Arr(ujson.Obj("type" -> "text", "text" -> "Stop hook feedback: fix it")))))
    val ls = lines("real-init-isolated.jsonl") :+ ev
    assertEquals(leaks(ls, "t"), List("turn-end in arm t"))
    assertEquals(leaks(ls, "g"), Nil)
    assertEquals(leaks(lines("real-init-isolated.jsonl"), "t"), Nil)

  test("missing_init_with_result"):
    assertEquals(leaks(lines("clean.jsonl"), "u"), List("no init event"))
    assertEquals(leaks(Nil, "u"), Nil)

  // is_turn_end_text and init_event_not_hook_text are ported in GateTest / TranscriptTest (they test core, not the adapter).

  // ---- _claude_slug (no GA test exists; own cases) ----
  test("claudeSlug replaces / and . with -"):
    assertEquals(ClaudeCode.claudeSlug("/Users/a.b/gate-eval/runs/x/repo"), "-Users-a-b-gate-eval-runs-x-repo")
    assertEquals(ClaudeCode.claudeSlug("plain"), "plain")

  // ---- command / env ----
  test("command equals the GA argv for model sonnet and the 7 tools"):
    val a = agent()
    val c = ctx("u")
    val bin = exp0.agents("claude-code").raw("bin").str
    assertEquals(c.arm.model, "sonnet")
    assertEquals(a.command(c, "do it"),
      List(bin, "-p", "do it", "--model", "sonnet", "--output-format", "stream-json", "--verbose",
        "--setting-sources", "project", "--strict-mcp-config", "--mcp-config", ".mcp.json",
        "--permission-mode", "acceptEdits", "--allowedTools",
        "Bash", "Edit", "Write", "Read", "Grep", "Glob", "mcp__scala-semantic"))

  test("env unsets exactly the GA session vars"):
    val e = agent().env(ctx("u"))
    assertEquals(e.size, 13)
    assert(e.values.forall(_.isEmpty))
    assert(e.contains("CLAUDECODE") && e.contains("AI_AGENT") && e.contains("CLAUDE_PID"))

  // ---- setup ----
  test("setup without snapshot writes permissions-only settings and the GA .mcp.json"):
    val repo = os.temp.dir()
    agent().setup(ctx("u", repo)).map: _ =>
      assertEquals(ujson.read(os.read(repo / ".claude" / "settings.json")), ujson.read("""{"permissions":{}}"""))
      assertEquals(os.read(repo / ".claude" / "settings.json"), "{\"permissions\":{}}\n")
      assertEquals(ujson.read(os.read(repo / ".mcp.json")),
        ujson.read("""{ "mcpServers": { "scala-semantic": { "command": "/Users/viktorskalinins/.local/bin/scalasemantic-mcp", "args": ["serve", "."] } } }"""))

  test(".mcp.json is byte-identical to the GA run.sh:29 heredoc"):
    val repo = os.temp.dir()
    val ga = "{ \"mcpServers\": { \"scala-semantic\": { \"command\": \"/Users/viktorskalinins/.local/bin/scalasemantic-mcp\", \"args\": [\"serve\", \".\"] } } }\n"
    agent().setup(ctx("u", repo)).map(_ => assertEquals(os.read(repo / ".mcp.json"), ga))

  test("setup with snapshot replaces every holdout repo path by the real holdout dir"):
    val repo = os.temp.dir(); val hd = os.temp.dir(); val src = os.temp.dir()
    val snap = os.temp(s"""{"a":"$src/x","b":["$src"],"c":"$src/y"}""")
    val real = hd.toNIO.toRealPath().toString
    agent().setup(ctx("g", repo, Some(hd), Some(snap), Some(src))).map: _ =>
      val s = os.read(repo / ".claude" / "settings.json")
      assertEquals(s, s"""{"a":"$real/x","b":["$real"],"c":"$real/y"}""")
      assert(!s.contains(src.toString))

  test("setup fails when settings still mention the holdout repo"):
    val repo = os.temp.dir(); val src = os.temp.dir(); val hd = os.temp.dir()
    val snap = os.temp(s"""{"a":"$src"}""")
    // a holdout dir whose real path itself contains the source path leaves an occurrence behind
    val nested = src / "hd"; os.makeDir(nested)
    agent().setup(ctx("g", repo, Some(nested), Some(snap), Some(src))).attempt.map: r =>
      assertEquals(r.left.map(_.getMessage), Left(s"settings still mention $src"))
      assert(os.exists(hd))

  // ---- state ----
  test("wipeState removes the per-cwd project dir under the config dir only"):
    val home = os.temp.dir(); val repo = os.temp.dir()
    val slug = ClaudeCode.claudeSlug(repo.toNIO.toRealPath().toString)
    val mine = home / ".claude" / "projects" / slug
    val other = home / ".claude" / "projects" / "-other"
    os.makeDir.all(mine); os.write(mine / "m.md", "x"); os.makeDir.all(other)
    agent(home).wipeState(ctx("u", repo)).map: _ =>
      assert(!os.exists(mine)); assert(os.exists(other))

  test("wipeState honours CLAUDE_CONFIG_DIR"):
    val home = os.temp.dir(); val cfg = os.temp.dir(); val repo = os.temp.dir()
    val mine = cfg / "projects" / ClaudeCode.claudeSlug(repo.toNIO.toRealPath().toString)
    os.makeDir.all(mine)
    agent(home, Some(cfg.toString)).wipeState(ctx("u", repo)).map(_ => assert(!os.exists(mine)))

  test("privateStateDirs: projects forbidden, own slug allowed"):
    val cfg = os.temp.dir(); val repo = os.temp.dir()
    val (forb, own) = agent(os.temp.dir(), Some(cfg.toString)).privateStateDirs(ctx("u", repo))
    val projects = os.Path(cfg.toNIO.toRealPath().toString) / "projects"
    assertEquals(forb, List(projects))
    assertEquals(own, List(projects / ClaudeCode.claudeSlug(repo.toNIO.toRealPath().toString)))

  test("collect is a no-op, settingsArtefact is repo .claude/settings.json"):
    val repo = os.temp.dir()
    agent().collect(ctx("u", repo), 0L).map: _ =>
      assertEquals(agent().settingsArtefact(ctx("u", repo)), Some(repo / ".claude" / "settings.json"))

  test("Agent.of: claude-code, other known adapters resolve, unknown rejected"):
    val s = exp0.agents("claude-code")
    assert(Agent.of(s).isRight)
    assert(Agent.of(s.copy(adapter = "codex-cli")).isRight)
    assert(Agent.of(s.copy(adapter = "gemini-cli")).isRight)
    // final review I3: Agent.of checks the adapter's required knobs; claude-code's conf has no generic `command`
    assertEquals(Agent.of(s.copy(adapter = "generic")).left.toOption, Some("agents.claude-code.command: missing"))
    assertEquals(Agent.of(s.copy(raw = ujson.Obj("adapter" -> "claude-code"))).left.toOption, Some("agents.claude-code.mcp-json: missing"))
    assert(Agent.of(s.copy(adapter = "nope")).left.exists(_.contains("unknown adapter nope")))
