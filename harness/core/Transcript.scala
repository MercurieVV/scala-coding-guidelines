package harness.core

import scala.collection.mutable

enum ToolKind:
  case Shell, Edit, Write, Read, Search, Mcp, Other

final case class ToolCall(name: String, kind: ToolKind, input: ujson.Obj, resultText: String, isError: Boolean, turn: Int):
  def command: String =
    if kind == ToolKind.Shell then input.value.get("command").flatMap(_.strOpt).getOrElse("") else ""

final case class Transcript(
    turns: Option[Int],
    inputTokens: Option[Long],
    outputTokens: Option[Long],
    cacheReadTokens: Option[Long],
    costUsd: Option[Double],
    toolCalls: Vector[ToolCall],
    finalText: String,
    texts: Vector[String],
    hookTexts: Vector[String],
    hasResult: Boolean,
    init: Option[ujson.Obj],
    hookEvents: Vector[String],
    apiError: Option[String])

object ClaudeTranscript:
  def kindOf(toolName: String): ToolKind = toolName match
    case "Bash"                 => ToolKind.Shell
    case "Edit" | "MultiEdit"   => ToolKind.Edit
    case "Write"                => ToolKind.Write
    case "Read"                 => ToolKind.Read
    case "Grep" | "Glob"        => ToolKind.Search
    case n if n.startsWith("mcp__") => ToolKind.Mcp
    case _                      => ToolKind.Other

  private def resultText(content: ujson.Value): String = content match
    case ujson.Str(s) => s
    case ujson.Arr(items) =>
      items.collect { case o: ujson.Obj if o.value.get("text").exists(_.strOpt.isDefined) => o.value("text").str }.mkString("\n")
    case _ => ""

  private def stringLeaves(v: ujson.Value): Iterator[String] = v match
    case ujson.Str(s)     => Iterator.single(s)
    case ujson.Obj(m)     => m.valuesIterator.flatMap(stringLeaves)
    case ujson.Arr(items) => items.iterator.flatMap(stringLeaves)
    case _                => Iterator.empty

  private def truthy(v: ujson.Value): Boolean = v match
    case ujson.Null | ujson.False => false
    case ujson.True               => true
    case ujson.Num(d)             => d != 0
    case ujson.Str(s)             => s.nonEmpty
    case ujson.Arr(a)             => a.nonEmpty
    case ujson.Obj(o)             => o.nonEmpty

  private def pyStr(v: ujson.Value): String = v match
    case ujson.Null   => "None"
    case ujson.Str(s) => s
    case ujson.Num(d) => if d == d.toLong.toDouble then d.toLong.toString else d.toString
    case ujson.True   => "True"
    case ujson.False  => "False"
    case other        => ujson.write(other)

  def parse(lines: Iterator[String]): Transcript =
    var turns: Option[Int] = None
    var inTok, outTok, cacheTok: Option[Long] = None
    var cost: Option[Double] = None
    val calls = mutable.ArrayBuffer.empty[ToolCall]
    var finalText = ""
    val texts = mutable.ArrayBuffer.empty[String]
    val hookTexts = mutable.ArrayBuffer.empty[String]
    var hasResult = false
    var init: Option[ujson.Obj] = None
    val hookEvents = mutable.ArrayBuffer.empty[String]
    var apiError: Option[String] = None
    val byId = mutable.HashMap.empty[ujson.Value, Int]
    var turn = 0
    var prevHooks = List.empty[String] // hook texts of the immediately preceding event: the same block reported twice in a row counts once

    def hook(evTexts: Seq[String]): Unit =
      val distinct = evTexts.distinct
      hookTexts ++= distinct.filterNot(prevHooks.contains)
      prevHooks = distinct.toList

    for raw <- lines do
      val line = raw.trim
      if line.nonEmpty then
        scala.util.Try(ujson.read(line)).toOption.collect { case o: ujson.Obj => o }.foreach: ev =>
          val m = ev.value
          val t = m.get("type").flatMap(_.strOpt)
          val content: Option[ujson.Value] = m.get("message") match
            case Some(msg: ujson.Obj) => msg.value.get("content")
            case _                    => None
          val blocks: Seq[ujson.Obj] = content match
            case Some(ujson.Arr(items)) => items.toSeq.collect { case o: ujson.Obj => o }
            case _                      => Nil
          val evHooks = mutable.ArrayBuffer.empty[String]
          t match
            case Some("assistant") =>
              turn += 1
              for b <- blocks do
                val bt = b.value.get("type").flatMap(_.strOpt)
                if bt.contains("tool_use") then
                  val name = b.value.get("name").map(pyStr).getOrElse("")
                  val input = b.value.get("input") match
                    case Some(o: ujson.Obj) if o.value.nonEmpty => o
                    case _                                       => ujson.Obj()
                  calls += ToolCall(name, kindOf(name), input, "", false, turn)
                  byId(b.value.getOrElse("id", ujson.Null)) = calls.size - 1
                else if bt.contains("text") then
                  b.value.get("text").flatMap(_.strOpt).foreach: s =>
                    texts += s
                    finalText = s
            case Some("user") =>
              content.flatMap(_.strOpt).foreach: s =>
                texts += s
                evHooks += s
              for b <- blocks do
                val bt = b.value.get("type").flatMap(_.strOpt)
                if bt.contains("tool_result") then
                  val txt = resultText(b.value.getOrElse("content", ujson.Null))
                  texts += txt
                  byId.get(b.value.getOrElse("tool_use_id", ujson.Null)).foreach: i =>
                    calls(i) = calls(i).copy(resultText = txt, isError = b.value.get("is_error").exists(truthy))
                else if bt.contains("text") then
                  b.value.get("text").flatMap(_.strOpt).foreach: s =>
                    texts += s
                    evHooks += s
            case Some("system") if m.get("subtype").flatMap(_.strOpt).contains("init") =>
              init = Some(ev) // configuration, not hook output: kept out of texts
            case Some("system") =>
              val name = m.get("hook_name").filter(truthy).orElse(m.get("hook_event").filter(truthy))
              name.foreach(n => hookEvents += pyStr(n))
              val leaves = stringLeaves(ujson.Obj.from(m.iterator.filter(_._1 != "type"))).filter(_.nonEmpty).toSeq
              texts ++= leaves
              evHooks ++= leaves
            case Some("result") =>
              hasResult = true
              if m.get("is_error").exists(truthy) then // Ruling 26: API error / usage limit, an infrastructure failure
                val status = pyStr(m.getOrElse("api_error_status", ujson.Null))
                val res = m.get("result").filter(truthy).map(pyStr).getOrElse("")
                apiError = Some(s"$status $res".trim)
              val u = m.get("usage") match
                case Some(o: ujson.Obj) => o.value
                case _                  => mutable.LinkedHashMap.empty[String, ujson.Value]
              turns = m.get("num_turns").flatMap(_.numOpt).map(_.toInt)
              cost = m.get("total_cost_usd").flatMap(_.numOpt)
              inTok = u.get("input_tokens").flatMap(_.numOpt).map(_.toLong)
              outTok = u.get("output_tokens").flatMap(_.numOpt).map(_.toLong)
              cacheTok = u.get("cache_read_input_tokens").flatMap(_.numOpt).map(_.toLong)
              m.get("result").flatMap(_.strOpt).foreach(s => finalText = s)
            case _ => ()
          hook(evHooks.toSeq)

    Transcript(turns, inTok, outTok, cacheTok, cost, calls.toVector, finalText, texts.toVector, hookTexts.toVector,
      hasResult, init, hookEvents.toVector, apiError)
