package harness.core

import munit.FunSuite

class ContaminationTest extends FunSuite:
  private val fx: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .map(_ / "test" / "fixtures" / "transcripts").find(os.exists(_)).getOrElse(fail("fixtures not found"))

  private def parseFx(name: String): Vector[ToolCall] =
    ClaudeTranscript.parse(os.read.lines(fx / name).iterator).toolCalls

  private def call(name: String, input: ujson.Obj): ToolCall =
    ToolCall(name, ClaudeTranscript.kindOf(name), input, "", false, 1)
  private def bash(cmd: String): ToolCall = call("Bash", ujson.Obj("command" -> cmd))

  private val Run = os.Path("/Users/someone/gate-eval/runs/noise-g-1")
  private val Skreeep2 = os.Path("/Users/viktorskalinins/IdeaProjects/skreeep2")

  /** The fence GA's contamination() derives from GA_ROOT / GA_SKREEEP2 / HOME / CLAUDE_CONFIG_DIR. */
  private def fence(run: os.Path = Run, root: os.Path = os.Path("/Users/someone/gate-eval"), home: os.Path = os.Path("/Users/someone")): Fence =
    val repo = run / "repo"
    val projects = home / ".claude" / "projects"
    val slug = repo.toString.replaceAll("[/.]", "-")
    Fence(
      runDir = run,
      repo = repo,
      forbidden = List(root / "base", root / "head", Skreeep2, projects, root / "runs",
        run / "holdout", repo / "out-holdout", repo / ".dsh" / ".state" / "holdout.log"),
      ownAllowed = List(projects / slug),
      allowedPrefixes = Contamination.defaultAllowedPrefixes ++ List(".cache", "Library/Caches", ".local/bin", ".mill").map(p => (home / os.RelPath(p)).toString),
      home = home)

  private def flagged(cmd: String, f: Fence = fence()): List[String] = Contamination.check(List(bash(cmd)), f)
  private def tool(name: String, kv: (String, ujson.Value)*): List[String] =
    Contamination.check(List(call(name, ujson.Obj.from(kv))), fence())

  // ---- class Contamination

  test("outside_read_flagged_tmp_allowed"):
    val c = Contamination.check(parseFx("contaminated.jsonl"), fence())
    assertEquals(c.length, 1)
    assert(c.head.contains("IdeaProjects/skreeep2"))

  test("inside_run_dir_allowed"):
    val calls = List(call("Read", ujson.Obj("file_path" -> "/r/run/repo/a.scala")))
    assertEquals(Contamination.check(calls, fence(run = os.Path("/r/run"))), Nil)

  test("relative_repo_path_ok"):
    assertEquals(flagged("cat src/a.scala ./b.scala", fence(home = os.home)), Nil)

  test("relative_base_path_flagged"):
    assertEquals(flagged("cat ../../../base/noise/hidden/x.scala", fence(home = os.home)),
      List("Bash: /Users/someone/gate-eval/base/noise/hidden/x.scala"))

  test("relative_in_read_tool"):
    val calls = List(call("Read", ujson.Obj("file_path" -> "../../../head/a")))
    assertEquals(Contamination.check(calls, fence(home = os.home)).length, 1)

  test("chmod_on_base"):
    assertEquals(flagged("chmod -R 755 /Users/someone/gate-eval/base", fence(home = os.home)), List("Bash: /Users/someone/gate-eval/base"))

  test("chmod_outside_run_even_in_tmp"):
    val c = flagged("chmod 755 /tmp/x", fence(home = os.home))
    assertEquals(c.length, 1)
    assert(c.head.endsWith("/tmp/x"))

  test("chmod_inside_run_ok"):
    assertEquals(flagged("chmod +x ./mill", fence(home = os.home)), Nil)

  test("sibling_run_dir"):
    val c = flagged("ls /Users/someone/gate-eval/runs/noise-u-1/repo", fence(home = os.home))
    assertEquals(c.length, 1)
    assert(c.head.contains("noise-u-1"))

  test("sibling_run_dir_relative"):
    assertEquals(flagged("ls ../../noise-u-1/repo", fence(home = os.home)).length, 1)

  test("skreeep2_path"):
    assertEquals(flagged("grep -r foo /Users/viktorskalinins/IdeaProjects/skreeep2/signals", fence(home = os.home)).length, 1)

  test("git_c_outside"):
    assertEquals(flagged("git -C /Users/viktorskalinins/IdeaProjects/skreeep2 log", fence(home = os.home)).length, 1)

  test("protected_even_under_allowed_prefix"):
    assertEquals(flagged("cat /tmp/ga-root/base/x", fence(root = os.Path("/tmp/ga-root"), home = os.home)).length, 1)

  test("mcp_tool_leaves_scanned"):
    val calls = List(
      call("mcp__scala-semantic__set_workspace_root", ujson.Obj("root" -> "../../../head")),
      call("mcp__x__y", ujson.Obj("nested" -> ujson.Obj("uri" -> "file:///Users/viktorskalinins/IdeaProjects/skreeep2/a.scala"))))
    assertEquals(Contamination.check(calls, fence(home = os.home)).length, 2)

  test("written_content_not_scanned"):
    val calls = List(call("Write", ujson.Obj("file_path" -> "x.txt", "content" -> "see /etc/foo and /Users/zzz/q")))
    assertEquals(Contamination.check(calls, fence(home = os.home)), Nil)

  test("toolchain_prefixes_allowed"):
    assertEquals(flagged(s"ls ${os.home}/.mill /Library/Java/x /System/y /etc/hosts", fence(home = os.home)), Nil)

  test("dev_null_and_urls_ok"):
    assertEquals(flagged("curl https://x.org/a/b 2>/dev/null", fence(home = os.home)), Nil)

  // ---- class RealGated

  test("real_scala_semantic_write_body_not_contamination"):
    val calls = ClaudeTranscript.parse(os.read.lines(fx / "real-gated.jsonl").iterator).toolCalls
    val run = os.Path("/Users/someone/gate-eval/runs/midi-synth-g-9")
    assertEquals(Contamination.check(calls, fence(run = run)), Nil)

  // ---- class ContaminationHardening (HOME=/Users/someone, no GA_ROOT)

  test("heredoc_scala_with_comments_not_flagged"):
    val cmd = "cat > signals/src/A.scala <<'EOF'\n/** Doc: see /Users/zzz/q and /etc/x.\n  * @param p /opt/thing\n  */\n" +
      "// https://example.org/a and /Users/elsewhere/b\nobject A { val p = \"/Users/q/r\" } /* c */\nEOF\n./mill signals.compile"
    assertEquals(flagged(cmd), Nil)

  test("heredoc_unquoted_and_dash"):
    assertEquals(flagged("cat <<EOF >a.txt\n/Users/x/y\nEOF"), Nil)
    assertEquals(flagged("cat <<-'END' >a.txt\n\t/Users/x/y\n\tEND\nls"), Nil)

  test("heredoc_target_outside_still_flagged"):
    val c = flagged("cat <<EOF > /Users/viktorskalinins/IdeaProjects/skreeep2/x\nbody\nEOF")
    assertEquals(c.length, 1, c.toString)

  test("here_string_is_not_heredoc"):
    assertEquals(flagged("grep x <<< foo\ncat /Users/viktorskalinins/IdeaProjects/skreeep2/a").length, 1)

  test("comment_tokens_ignored_outside_heredoc"):
    assertEquals(flagged("echo // not a path /* nor this */"), Nil)

  test("glob_tokens"):
    assertEquals(flagged("ls **/*.scala src/*/x"), Nil)
    assertEquals(flagged("ls /Users/viktorskalinins/IdeaProjects/skreeep2/*.scala").length, 1)

  test("python_heredoc_script_flagged"):
    val cmd = "python3 - <<'EOF'\nimport os\nfor d in os.walk('/Users/viktorskalinins/IdeaProjects/skreeep2'): print(d)\nEOF"
    assertEquals(flagged(cmd).length, 1, flagged(cmd).toString)

  test("sh_heredoc_script_flagged"):
    val cmd = "sh <<'EOF'\ncat /Users/viktorskalinins/IdeaProjects/skreeep2/signals/src/A.scala\nEOF"
    assertEquals(flagged(cmd).length, 1, flagged(cmd).toString)

  test("script_heredoc_comments_still_ignored"):
    assertEquals(flagged("python3 - <<'EOF' 2>/dev/null\n// x /** y */ **/*.scala\nprint(1)\nEOF"), Nil)

  test("heredoc_into_file_via_append_and_tee"):
    assertEquals(flagged("cat >> notes.txt <<EOF\n/Users/x/y\nEOF"), Nil)
    assertEquals(flagged("tee a.scala <<'EOF' >/dev/null\n/Users/x/y\nEOF"), Nil)
    assertEquals(flagged("cat <<'EOF' | tee a.scala\n/Users/x/y\nEOF"), Nil)

  test("bare_slash_arithmetic_next_to_walker"):
    assertEquals(flagged("echo $((3 / 4)); ls"), Nil)
    assertEquals(flagged("awk \"{print $1 / 2}\" f.txt | head"), Nil)
    assertEquals(flagged("grep -E \"/ \" src/a.txt"), Nil)

  test("bare_slash_root_in_segment"):
    assertEquals(flagged("cd src && du -sh / | sort"), List("Bash: /"))
    assertEquals(flagged("echo hi; grep -r Spec / 2>/dev/null"), List("Bash: /"))

  test("grep_pattern_is_not_a_path"):
    assertEquals(tool("Grep", "pattern" -> "/\\*\\*", "path" -> "signals"), Nil)
    assertEquals(tool("Grep", "pattern" -> "/Users/x", "path" -> "."), Nil)

  test("find_from_root"):
    assertEquals(flagged("find / -name MidiSynthSpec.scala"), List("Bash: /"))

  test("bare_slash_as_arithmetic_not_root"):
    assertEquals(flagged("python3 -c \"print(4 / 2)\"; echo $((6 / 3))"), Nil)

  test("grep_home"):
    for h <- List("~", "$HOME", "${HOME}", "~/") do
      assertEquals(flagged(s"grep -r MidiSynthSpec $h"), List("Bash: /Users/someone"), h)

  test("tilde_user"):
    val rootHome = os.proc("/bin/sh", "-c", "echo ~root").call().out.text().trim
    assertEquals(flagged("ls ~root"), List("Bash: " + Contamination.realpath(rootHome)))
    assertEquals(flagged("ls ~nosuchuser4711"), Nil)

  test("glob_and_grep_tool_root"):
    assertEquals(tool("Glob", "pattern" -> "**/MidiSynthSpec.scala", "path" -> "/"), List("Glob: /"))
    assertEquals(tool("Grep", "pattern" -> "MidiSynth", "path" -> "/Users/someone/gate-eval"), List("Grep: /Users/someone/gate-eval"))

  test("ancestor_under_allowed_prefix"):
    val f = fence(run = os.Path("/tmp/ga-root/runs/noise-g-1"), root = os.Path("/tmp/ga-root"))
    assertEquals(flagged("find /tmp -name '*Spec.scala'", f), List("Bash: " + Contamination.realpath("/tmp")))

  test("runs_parent_is_ancestor_of_other_runs"):
    assertEquals(flagged("ls ../.."), List("Bash: /Users/someone/gate-eval/runs"))

  test("repo_searches_not_flagged"):
    assertEquals(flagged("rg -n MidiSynth ./ && find ./signals -name '*.scala' && ls .. ../repo"), Nil)

  test("claude_projects_protected"):
    val c = flagged("cat ~/.claude/projects/-Users-someone-gate-eval-runs-noise-g-2-repo/x.jsonl")
    assertEquals(c.length, 1, c.toString)
    assertEquals(flagged("ls ~/.claude").length, 1)

  test("own_memory_dir_allowed"):
    assertEquals(tool("Write", "file_path" -> "/Users/someone/.claude/projects/-Users-someone-gate-eval-runs-noise-g-1-repo/memory/m.md", "content" -> "x"), Nil)

  test("holdout_inside_run_dir"):
    assertEquals(tool("Read", "file_path" -> (Run.toString + "/holdout/HSpec.scala")), List(s"Read: $Run/holdout/HSpec.scala"))
    assertEquals(flagged("cat ../holdout/HSpec.scala"), List(s"Bash: $Run/holdout/HSpec.scala"))
    assertEquals(flagged("ls out-holdout/holdout"), List(s"Bash: $Run/repo/out-holdout/holdout"))
    assertEquals(flagged("tail .dsh/.state/holdout.log"), List(s"Bash: $Run/repo/.dsh/.state/holdout.log"))

  test("holdout_bare_name_from_repo"):
    assertEquals(flagged("ls -la out-holdout"), List(s"Bash: $Run/repo/out-holdout"))

  // ---- final review I2: Codex / dsh / Gemini calls dispatch on ToolKind, not on Claude's tool names

  /** a Codex `command_execution` as `codex exec --json` reports it: the script wrapped in the login shell's `-lc` */
  private def codexCall(script: String): ToolCall =
    harness.agent.CodexCli.parse(Iterator(ujson.write(ujson.Obj("type" -> "item.completed", "item" -> ujson.Obj(
      "type" -> "command_execution", "command" -> s"/bin/zsh -lc '$script'", "aggregated_output" -> "", "exit_code" -> 0,
      "status" -> "completed"))))).toolCalls.head
  private def dshCall(name: String, kv: (String, ujson.Value)*): ToolCall =
    ToolCall(name, harness.agent.Dsh.kindOf(name), ujson.Obj.from(kv), "", false, 1)
  private def dshBash(cmd: String): ToolCall = dshCall("bash", "command" -> cmd, "description" -> "run it")
  private def checked(c: ToolCall): List[String] = Contamination.check(List(c), fence())

  private val HeredocBody = "cat > src/A.scala <<EOF\n/** see /Users/zzz/q and /etc/x */\nobject A { val p = \"/Users/q/r\" }\nEOF\n./mill m.compile"

  test("codex_heredoc_body_not_flagged"):
    assertEquals(checked(codexCall(HeredocBody)), Nil)

  test("codex_arithmetic_slash_not_root"):
    assertEquals(checked(codexCall("echo $((a / b))")), Nil)
    assertEquals(checked(codexCall("find / -name MidiSynthSpec.scala")), List("command_execution: /"))

  test("codex_chmod_check"):
    assertEquals(checked(codexCall("chmod -R 755 /Users/someone/gate-eval/base")), List("command_execution: /Users/someone/gate-eval/base"))
    assertEquals(checked(codexCall("chmod 755 /tmp/x")).map(_.takeRight(6)), List("/tmp/x"))
    assertEquals(checked(codexCall("chmod +x ./mill")), Nil) // the login-shell wrapper is not a chmod target

  test("dsh_bash_heredoc_and_arithmetic"):
    assertEquals(checked(dshBash(HeredocBody)), Nil)
    assertEquals(checked(dshBash("echo $((a / b)); ls")), Nil)
    assertEquals(checked(dshBash("cd src && du -sh / | sort")), List("bash: /"))

  test("dsh_chmod_check"):
    assertEquals(checked(dshBash("chmod -R 755 /Users/someone/gate-eval/base")), List("bash: /Users/someone/gate-eval/base"))
    assertEquals(checked(dshBash("chmod 755 /tmp/x")).map(_.takeRight(6)), List("/tmp/x"))
    assertEquals(checked(dshBash("chmod +x ./mill")), Nil)

  test("dsh_and_gemini_grep_pattern_is_not_a_path"):
    assertEquals(checked(dshCall("grep", "pattern" -> "/Users/x", "path" -> ".")), Nil)
    assertEquals(checked(dshCall("grep", "pattern" -> "a / b", "path" -> "src")), Nil)
    assertEquals(checked(ToolCall("grep_search", harness.agent.GeminiCli.kindOf("grep_search"),
      ujson.Obj("pattern" -> "/Users/x"), "", false, 1)), Nil)
    assertEquals(checked(dshCall("grep", "pattern" -> "x", "path" -> "/Users/someone/gate-eval")), List("grep: /Users/someone/gate-eval"))

  test("dsh_glob_pattern_still_a_path"):
    assertEquals(checked(dshCall("glob", "pattern" -> "/Users/someone/gate-eval/base/**")), List("glob: /Users/someone/gate-eval/base"))
