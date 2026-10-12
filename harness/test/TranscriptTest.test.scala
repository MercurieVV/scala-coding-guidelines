package harness.core

import munit.FunSuite

class TranscriptTest extends FunSuite:
  private val fx: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .map(_ / "test" / "fixtures" / "transcripts").find(os.exists(_)).getOrElse(fail("fixtures not found"))

  private def lines(name: String): Vector[String] = os.read.lines(fx / name).toVector
  private def parse(ls: Seq[String]): Transcript = ClaudeTranscript.parse(ls.iterator)
  private def textEv(t: String): String =
    ujson.write(ujson.Obj("type" -> "user", "message" -> ujson.Obj("content" -> ujson.Arr(ujson.Obj("type" -> "text", "text" -> t)))))

  test("clean_metrics"):
    val t = parse(lines("clean.jsonl"))
    assertEquals(t.turns, Some(2))
    assertEquals(t.costUsd, Some(0.42))
    assertEquals((t.inputTokens, t.outputTokens, t.cacheReadTokens), (Some(1000L), Some(200L), Some(5000L)))

  test("empty_transcript"):
    val t = parse(Nil)
    assertEquals(t.turns, None)
    assertEquals(t.toolCalls, Vector.empty[ToolCall])

  test("empty_fixture"):
    assert(!parse(lines("empty.jsonl")).hasResult)

  test("tool call kind, command, result, turn"):
    val c = parse(lines("clean.jsonl")).toolCalls.head
    assertEquals((c.name, c.kind, c.command, c.resultText, c.isError, c.turn),
      ("Bash", ToolKind.Shell, "./mill -j 1 signals.compile", "compiling 3 Scala sources\n", false, 1))

  test("kindOf"):
    import ToolKind.*
    val m = List("Bash" -> Shell, "Edit" -> Edit, "MultiEdit" -> Edit, "Write" -> Write, "Read" -> Read,
      "Grep" -> Search, "Glob" -> Search, "mcp__scala-semantic__find_symbol" -> Mcp, "Task" -> Other)
    m.foreach((n, k) => assertEquals(ClaudeTranscript.kindOf(n), k, n))

  test("texts_collect_all_sources"):
    val lns = Vector(
      ujson.write(ujson.Obj("type" -> "system", "subtype" -> "hook_response", "output" -> "hook says hi")),
      textEv("Stop hook feedback: x")) ++ lines("clean.jsonl")
    val t = parse(lns)
    assert(t.texts.contains("hook says hi"))
    assert(t.texts.contains("Stop hook feedback: x"))
    assert(t.texts.contains("Done."))
    assert(t.texts.contains("compiling 3 Scala sources\n"))

  test("counts_by_hook"):
    val t = parse(lines("blocked.jsonl"))
    val b = Gate.gateBlocks(t.toolCalls, t.texts)
    assertEquals(b.get("tool-policy"), Some(1))
    assertEquals(b.get("semantic-guard-sealed"), Some(1))

  test("turn_end_from_text"):
    assertEquals(Gate.gateBlocks(Nil, Seq("Stop hook feedback: fix it", "turn-end gate: BLOCKED", "fine")), Map("turn-end" -> 2))

  test("same_block_in_adjacent_events_counted_once"):
    val t = parse(Seq(textEv("Stop hook feedback: x"), textEv("Stop hook feedback: x")))
    assertEquals(Gate.gateBlocks(Nil, t.hookTexts), Map("turn-end" -> 1))

  test("same_text_duplicated_inside_one_system_event_counted_once"):
    val t = parse(Seq(ujson.write(ujson.Obj("type" -> "system", "output" -> "Stop hook feedback: x", "stdout" -> "Stop hook feedback: x"))))
    assertEquals(Gate.gateBlocks(Nil, t.hookTexts), Map("turn-end" -> 1))

  test("identical_blocks_in_separate_turns_counted_twice"):
    val asst = ujson.write(ujson.Obj("type" -> "assistant", "message" -> ujson.Obj("content" -> ujson.Arr(ujson.Obj("type" -> "text", "text" -> "retrying")))))
    val t = parse(Seq(textEv("Stop hook feedback: x"), asst, textEv("Stop hook feedback: x")))
    assertEquals(Gate.gateBlocks(Nil, t.hookTexts), Map("turn-end" -> 2))

  test("assistant_and_tool_result_text_not_counted"):
    val lns = Seq(
      ujson.write(ujson.Obj("type" -> "assistant", "message" -> ujson.Obj("content" -> ujson.Arr(ujson.Obj("type" -> "text", "text" -> "Stop hook feedback: I quote it"))))),
      ujson.write(ujson.Obj("type" -> "assistant", "message" -> ujson.Obj("content" -> ujson.Arr(ujson.Obj("type" -> "tool_use", "id" -> "a", "name" -> "Bash", "input" -> ujson.Obj("command" -> "echo")))))),
      ujson.write(ujson.Obj("type" -> "user", "message" -> ujson.Obj("content" -> ujson.Arr(ujson.Obj("type" -> "tool_result", "tool_use_id" -> "a", "content" -> "Stop hook feedback: in output"))))),
      textEv("Stop hook feedback: real"))
    val t = parse(lns)
    assertEquals(Gate.gateBlocks(t.toolCalls, t.hookTexts), Map("turn-end" -> 1))

  test("init event kept out of texts, hook events recorded, api error captured"):
    val t = parse(Seq(
      """{"type":"system","subtype":"init","model":"m"}""",
      """{"type":"system","subtype":"hook_started","hook_name":"Stop"}""",
      "not json",
      """{"type":"result","is_error":true,"api_error_status":429,"result":"limit"}"""))
    assertEquals(t.init.map(_("model").str), Some("m"))
    assert(!t.texts.contains("m"))
    assertEquals(t.hookEvents, Vector("Stop"))
    assertEquals(t.apiError, Some("429 limit"))
    assert(t.hasResult)

  test("real_hook_texts_counted"):
    val t = parse(lines("real-gated.jsonl"))
    assertEquals(Gate.gateBlocks(t.toolCalls, t.hookTexts), Map("ScalaSemantic" -> 1, "turn-end" -> 1))

  test("parse_flags_error_result"):
    val t = parse(lines("real-api-error.jsonl"))
    assert(t.hasResult)
    assertEquals(t.apiError, Some("429 You've hit your weekly limit · resets Oct 8 at 4am (Europe/Riga)"))

  test("clean_result_has_no_api_error"):
    assertEquals(parse(lines("clean.jsonl")).apiError, None)

  test("init_event_not_hook_text"):
    val t = parse(lines("real-init-isolated.jsonl"))
    assertEquals(t.hookTexts, Vector.empty[String])
    assert(!t.texts.contains("scala-semantic"))

  // Task 18 static parity: GA iterates the system event's dict in key order (120 old transcripts differed)
  test("system event leaves in the event's key order"):
    val ev = """{"type":"system","subtype":"hook_started","hook_id":"h1","hook_name":"SessionStart:startup","hook_event":"SessionStart","uuid":"u1","session_id":"s1"}"""
    val want = Vector("hook_started", "h1", "SessionStart:startup", "SessionStart", "u1", "s1")
    val t = parse(List(ev))
    assertEquals(t.texts, want)
    assertEquals(t.hookTexts, want)
