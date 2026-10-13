package harness.agent

import cats.effect.IO
import harness.config.{AgentSpec, ArmSpec}
import harness.core.{Gate, ToolCall, ToolKind, Transcript}
import scala.collection.mutable

/** DeepSeek Harness (spec §4): `dsh --profile <p> --patch <run>/dsh-overlay.yml "<prompt>"`, transcript from the persisted
  * session under the dsh home. Runs delete only the run copy's own session dir there and write nothing else.
  */
final class Dsh(val spec: AgentSpec) extends Agent:
  import Dsh.*

  private def str(k: String): Option[String] = spec.raw.value.get(k).flatMap(_.strOpt)
  private def required(k: String): String = str(k).getOrElse(throw new RuntimeException(s"agents.${spec.name}.$k: missing"))
  private def bin: String = str("bin").getOrElse("dsh")
  private def profile: String = required("profile")
  private def dshHome: os.Path = os.Path(required("dsh-home"), os.pwd)
  private def hooksFrom: String = str("hooks-from").getOrElse(".dsh/hooks.json")
  private def allowedMcp: List[String] =
    spec.raw.value.get("allowed-mcp").collect { case ujson.Arr(a) => a.toList.flatMap(_.strOpt) }.getOrElse(Nil)
  private def price(model: String): Option[ujson.Obj] =
    spec.raw.value.get("prices").collect { case o: ujson.Obj => o }.flatMap(_.value.get(model)).collect { case o: ujson.Obj => o }

  private def slug(ctx: RunCtx): String = dshSlug(ctx.repo.toNIO.toRealPath().toString)
  private def overlay(ctx: RunCtx): os.Path = ctx.runDir / "dsh-overlay.yml"
  private def hooksFile(ctx: RunCtx): os.Path = ctx.runDir / "dsh-hooks.json"

  override def validate: Either[String, Unit] = Agent.requireStr(spec, "profile").flatMap(_ => Agent.requireStr(spec, "dsh-home"))

  /** Reads the profile's row ids from `--dump-config`, writes the overlay; the hook file follows in [[afterSteps]]. */
  def setup(ctx: RunCtx): IO[Unit] = IO.blocking:
    val r = os.proc(bin, "--profile", profile, "--dump-config")
      .call(cwd = ctx.repo, env = Map("DSH_HOME" -> dshHome.toString), stdin = "", stderr = os.Pipe, check = false)
    if r.exitCode != 0 then
      throw new RuntimeException(s"dsh: --profile $profile --dump-config failed (exit ${r.exitCode}): ${r.err.text().trim}")
    val rows = Yaml.list(Yaml.load(r.out.text())).flatMap(Yaml.map)
    // the overlay patches one row by id: a second row of the same plugin would run unpatched (e.g. the user's own hooks)
    def row(name: String, unique: Boolean) =
      val named = rows.filter(m => Yaml.str(m, "name").contains(name))
      if unique && named.size > 1 then
        throw new RuntimeException(s"dsh: profile $profile has ${named.size} $name rows " +
          s"(${named.map(Yaml.str(_, "id").getOrElse("no id")).mkString(", ")}); a run's overlay retargets only one (see dsh --dump-config)")
      named.find(m => Yaml.str(m, "id").isDefined)
        .getOrElse(throw new RuntimeException(s"dsh: profile $profile has no $name row (see dsh --dump-config)"))
    val hooks = row(DshProfile.HooksBridge, unique = true)
    val model = row(DefaultModel, unique = false)
    val provider = Option(model.get("config")).flatMap(Yaml.map).flatMap(Yaml.str(_, "provider"))
    // a patch's `config` replaces the row's whole config: the provider is carried over, the model is the arm's
    val doc = java.util.ArrayList[Object](java.util.List.of(
      Yaml.jmap("id" -> Yaml.str(hooks, "id").get, "config" -> Yaml.jmap("configPath" -> hooksFile(ctx).toString)),
      Yaml.jmap("id" -> Yaml.str(model, "id").get,
        "config" -> Yaml.jmap((provider.map("provider" -> _).toList :+ ("model" -> ctx.arm.model))*))))
    os.write.over(overlay(ctx), Yaml.dump(doc))
    // the profile's MCP row runs `<cwd>/scalasemantic-mcp.sh serve .`; written before the arm's commit, so not in the diff
    str("scalasemantic-command").foreach { cmd =>
      val sh = ctx.repo / "scalasemantic-mcp.sh"
      os.write.over(sh, s"#!/bin/sh\nexec '${cmd.replace("'", "'\\''")}' \"$$@\"\n")
      os.perms.set(sh, "rwxr-xr-x")
      // final.diff runs `git add -A -N`: keep the launcher out of it, as skreeep2's .gitignore does for .mcp.json
      val exclude = ctx.repo / ".git" / "info" / "exclude"
      if os.isDir(ctx.repo / ".git") && !(os.exists(exclude) && os.read.lines(exclude).contains("/scalasemantic-mcp.sh")) then
        os.write.append(exclude, "/scalasemantic-mcp.sh\n", createFolders = true)
    }

  /** `{"hooks": <the run copy's hook map>}` for arms that allow hooks, else no hooks: read after the arm's setup steps. */
  override def afterSteps(ctx: RunCtx): IO[Unit] = IO.blocking:
    val hooks =
      if !ctx.arm.hooksAllowed then ujson.Obj()
      else
        val f = os.Path(hooksFrom, ctx.repo)
        if !os.isFile(f) then throw new RuntimeException(s"dsh: hooks-from $f missing")
        ujson.read(os.read(f))
    os.write.over(hooksFile(ctx), ujson.write(ujson.Obj("hooks" -> hooks)) + "\n")

  def command(ctx: RunCtx, prompt: String): List[String] =
    List(bin, "--profile", profile, "--patch", overlay(ctx).toString, prompt)

  def env(ctx: RunCtx): Map[String, Option[String]] =
    ClaudeCode.SessionVars.map(_ -> Option.empty[String]).toMap ++ Map(
      "DSH_PERMISSION_MODE" -> Some("danger-full-access"),
      "CLAUDE_PROJECT_DIR" -> Some(ctx.repo.toString),
      "DSH_HOME" -> Some(dshHome.toString))

  def wipeState(ctx: RunCtx): IO[Unit] = IO.blocking(os.remove.all(dshHome / "sessions" / slug(ctx)))

  /** The newest top-level session of the run copy created at or after the start, decompressed; none leaves the transcript empty. */
  def collect(ctx: RunCtx, startedAtMillis: Long): IO[Unit] = IO.blocking:
    val dir = dshHome / "sessions" / slug(ctx)
    val sessions =
      if !os.isDir(dir) then Nil
      else os.list(dir).filter(os.isDir(_)).flatMap(d => SessionFiles.map(d / _).find(os.isFile(_))).toList
    val picked = sessions.flatMap(f => topLevelCreatedAt(f).map(f -> _)).filter(_._2 >= startedAtMillis).sortBy(-_._2).headOption
    val dest = ctx.runDir / "transcript.jsonl"
    picked match
      case Some((f, _)) =>
        withZstd(f)(in => os.write.over(dest, in))
        // the run's child sessions go beside the transcript, so a rescore does not depend on the dsh home
        val kids = header(f).flatMap(s(_, "id")).toList.flatMap(id => descendants(sessions, id, Some(startedAtMillis)))
        if kids.nonEmpty then
          val keep = ctx.runDir / "dsh-sessions"
          os.makeDir.all(keep)
          kids.foreach(k => os.copy.over(k, keep / s"${(k / os.up).last}.${k.last}"))
      case None => os.write.over(dest, "")

  def parse(lines: Iterator[String]): Transcript = Dsh.parse(lines, price)

  /** A `subagent` call runs in a child session (own dir under the slug, `parentSession` = its parent's id). Every
    * session descending from the run's session (its `init` id), created at or after the start, adds its tokens and cost;
    * turns, tool calls and texts stay the run's own. Reads the dsh home only.
    */
  override def withDelegated(ctx: RunCtx, t: Transcript, startedAtMillis: Option[Long]): IO[Transcript] = IO.blocking:
    val top = t.init.flatMap(s(_, "id"))
    // the copies collect kept in the run dir; runs collected before that read the dsh home
    val kept = ctx.runDir / "dsh-sessions"
    val files =
      if os.isDir(kept) then Some(os.list(kept).filter(os.isFile(_)).toList)
      else if os.isDir(ctx.repo) then
        Some(dshHome / "sessions" / slug(ctx)).filter(os.isDir(_))
          .map(d => os.list(d).filter(os.isDir(_)).flatMap(x => SessionFiles.map(x / _).find(os.isFile(_))).toList)
      else None
    (top, files) match
      case (Some(id), Some(fs)) =>
        val kids = descendants(fs, id, startedAtMillis)
          .map(f => withZstd(f)(in => Dsh.parse(scala.io.Source.fromInputStream(in, "UTF-8").getLines(), price)))
        def add(a: Option[Long], b: Option[Long]) = if a.isEmpty && b.isEmpty then None else Some(a.getOrElse(0L) + b.getOrElse(0L))
        kids.foldLeft(t)((acc, k) => acc.copy(
          inputTokens = add(acc.inputTokens, k.inputTokens), outputTokens = add(acc.outputTokens, k.outputTokens),
          cacheReadTokens = add(acc.cacheReadTokens, k.cacheReadTokens),
          costUsd = acc.costUsd.map(_ + k.costUsd.getOrElse(0.0)),
          delegatedCalls = acc.delegatedCalls ++ k.toolCalls ++ k.delegatedCalls))
      case _ => t

  /** Session files descending from session `id` (transitively via `parentSession`), created at or after the start, in
    * path order; `id`'s own file excluded. */
  private def descendants(files: List[os.Path], id: String, startedAtMillis: Option[Long]): List[os.Path] =
    val headers = files.flatMap(f => header(f).map(f -> _))
      .filter((_, h) => startedAtMillis.forall(st => h.value.get("createdAt").flatMap(_.numOpt).exists(_ >= st)))
    @scala.annotation.tailrec
    def closure(ids: Set[String]): Set[String] =
      val more = headers.flatMap((_, h) => s(h, "id").filter(_ => s(h, "parentSession").exists(ids))).toSet ++ ids
      if more == ids then ids else closure(more)
    val all = closure(Set(id))
    headers.filter((_, h) => s(h, "id").exists(k => k != id && all(k))).map(_._1).sortBy(_.toString)

  /** From the request header: MCP servers beyond allowed-mcp; hooks that ran in an arm without hooks; arm t's turn-end. */
  def envLeaks(t: Transcript, arm: ArmSpec): List[String] =
    t.init match
      case None => if t.hasResult then List("no init event") else Nil
      case Some(init) =>
        val tools = init.value.get("tools").collect { case ujson.Arr(a) => a.toList.flatMap(_.strOpt) }.getOrElse(Nil)
        val servers = tools.collect { case McpTool(s) => s }.distinct.sorted
        val extra = servers.filterNot(allowedMcp.contains)
        val leaks = List.newBuilder[String]
        if extra.nonEmpty then leaks += ("mcp_servers=" + extra.mkString(","))
        if !arm.hooksAllowed && t.hookEvents.nonEmpty then leaks += ("hooks=" + t.hookEvents.distinct.mkString(","))
        if arm.turnEndForbidden && t.hookTexts.exists(Gate.isTurnEndText) then leaks += "turn-end in arm t"
        leaks.result()

  /** The dsh home's sessions (the run's own allowed), plus the overlay and hook map dsh reads from the run dir (final
    * review M1): the run dir is otherwise exempt, so an agent editing its own gate hook map would go unnoticed.
    */
  def privateStateDirs(ctx: RunCtx): (List[os.Path], List[os.Path]) =
    val sessions = os.Path(harness.core.Contamination.realpath((dshHome / "sessions").toString))
    (List(sessions, overlay(ctx), hooksFile(ctx)), List(sessions / slug(ctx)))

  def settingsArtefact(ctx: RunCtx): Option[os.Path] = Some(overlay(ctx))

object Dsh:
  val DefaultModel = "@deepseek-ai/dsh-agent-default-model"
  private val SessionFiles = List("session.v3.jsonl.zstd", "session.jsonl.zstd")
  private val McpTool = "mcp__([^_]+(?:_[^_]+)*?)__.*".r

  /** dsh's session dir name: the real cwd with `/` as `-`, wrapped in `--`. */
  def dshSlug(realPath: String): String = "--" + realPath.drop(1).replace('/', '-') + "--"

  def kindOf(name: String): ToolKind = name match
    case "bash"                     => ToolKind.Shell
    case "edit"                     => ToolKind.Edit
    case "write"                    => ToolKind.Write
    case "read"                     => ToolKind.Read
    case "grep" | "glob"            => ToolKind.Search
    case n if n.startsWith("mcp__") => ToolKind.Mcp
    case _                          => ToolKind.Other

  private def withZstd[A](f: os.Path)(use: java.io.InputStream => A): A =
    val in = com.github.luben.zstd.ZstdInputStream(java.io.BufferedInputStream(java.io.FileInputStream(f.toIO)))
    try use(in) finally in.close()

  /** the `session` header (first line); None when unreadable */
  private def header(f: os.Path): Option[ujson.Obj] =
    scala.util.Try(withZstd(f) { in =>
      ujson.read(java.io.BufferedReader(java.io.InputStreamReader(in, "UTF-8")).readLine()).obj
    }).toOption.map(ujson.Obj.from(_))

  /** the `session` header's createdAt (first line); None when unreadable or a delegated (subagent) child session */
  private def topLevelCreatedAt(f: os.Path): Option[Long] =
    scala.util.Try(withZstd(f) { in =>
      val h = ujson.read(java.io.BufferedReader(java.io.InputStreamReader(in, "UTF-8")).readLine()).obj
      val child = h.get("parentSession").exists(truthy) || h.get("delegationDepth").flatMap(_.numOpt).exists(_ > 0)
      if child then None else h.get("createdAt").flatMap(_.numOpt).map(_.toLong)
    }).toOption.flatten

  private def obj(v: Option[ujson.Value]): Option[ujson.Obj] = v.collect { case o: ujson.Obj => o }
  private def arr(v: Option[ujson.Value]): Seq[ujson.Obj] = v.collect { case ujson.Arr(a) => a.toSeq.collect { case o: ujson.Obj => o } }.getOrElse(Nil)
  private def s(o: ujson.Obj, k: String): Option[String] = o.value.get(k).flatMap(_.strOpt)
  private def truthy(v: ujson.Value): Boolean = v match
    case ujson.Null | ujson.False => false
    case _                        => true

  def parse(lines: Iterator[String], price: String => Option[ujson.Obj]): Transcript =
    var steps = 0
    val usage = mutable.HashMap.empty[String, Long]
    val calls = mutable.ArrayBuffer.empty[ToolCall]
    val byId = mutable.HashMap.empty[String, Int]
    var finalText = ""
    val texts = mutable.ArrayBuffer.empty[String]
    val hookTexts = mutable.ArrayBuffer.empty[String]
    val hookEvents = mutable.ArrayBuffer.empty[String]
    var hasResult = false
    var session: Option[ujson.Obj] = None
    var init: Option[ujson.Obj] = None
    var apiError: Option[String] = None

    for raw <- lines do
      val line = raw.trim
      if line.nonEmpty then
        scala.util.Try(ujson.read(line)).toOption.collect { case o: ujson.Obj => o }.foreach: ev =>
          val data = obj(ev.value.get("data")).getOrElse(ujson.Obj())
          s(ev, "type") match
            case Some("session") => session = Some(ev)
            case Some("request/header") if init.isEmpty =>
              val header = obj(data.value.get("header")).getOrElse(ujson.Obj())
              val cfg = obj(header.value.get("config")).getOrElse(ujson.Obj())
              val rec = ujson.Obj.from(session.map(_.value.toSeq).getOrElse(Nil))
              rec("provider") = cfg.value.getOrElse("provider", ujson.Null)
              rec("model") = cfg.value.getOrElse("model", ujson.Null)
              rec("tools") = ujson.Arr.from(arr(header.value.get("tools")).flatMap(s(_, "name")).map(ujson.Str(_)))
              init = Some(rec)
            case Some("step/start") => steps += 1
            case Some("assistant/message") =>
              val parts = obj(data.value.get("message")).toSeq.flatMap(m => arr(m.value.get("content")))
              val ts = parts.filter(p => s(p, "type").contains("text")).flatMap(s(_, "text"))
              texts ++= ts
              if ts.mkString.nonEmpty then finalText = ts.mkString
              obj(data.value.get("usage")).foreach(u =>
                for (k, v) <- u.value; n <- v.numOpt do usage(k) = usage.getOrElse(k, 0L) + n.toLong)
            case Some("tool/call") =>
              val name = s(data, "name").getOrElse("")
              val input = s(data, "arguments").flatMap(a => scala.util.Try(ujson.read(a)).toOption).collect { case o: ujson.Obj => o }
                .getOrElse(ujson.Obj())
              calls += ToolCall(name, kindOf(name), input, "", false, steps)
              s(data, "callId").foreach(id => byId(id) = calls.size - 1)
            case Some("tool/result") =>
              val msg = obj(data.value.get("message")).getOrElse(ujson.Obj())
              for part <- arr(msg.value.get("content")) if s(part, "type").contains("tool-result") do
                val text = arr(part.value.get("content")).flatMap(s(_, "text")).mkString("\n")
                texts += text
                val id = s(part, "toolCallId").orElse(obj(msg.value.get("source")).flatMap(s(_, "callId")))
                val err = part.value.get("isError").exists(truthy) || text.startsWith("Error:") || data.value.get("error").exists(truthy)
                id.flatMap(byId.get).foreach(i => calls(i) = calls(i).copy(resultText = text, isError = err))
            case Some("hook/invoked") => s(data, "point").foreach(hookEvents += _)
            // a blocking PreToolUse hook reaches the agent as the call's tool result (counted from there, as in Claude Code)
            case Some("hook/result") if s(data, "decision").contains("block") && !s(data, "point").contains("PreToolUse") =>
              val text = s(data, "stderrSummary").getOrElse("")
              // a blocking Stop hook is Claude Code's "Stop hook feedback" (Gate counts it as turn-end)
              val hookText = if s(data, "point").contains("Stop") then s"Stop hook feedback:\n$text" else text
              hookTexts += hookText
              texts += hookText
            case Some("turn/end") =>
              hasResult = true
              val reason = obj(data.value.get("reason")).getOrElse(ujson.Obj())
              if s(reason, "kind").contains("error") then
                val e = obj(reason.value.get("error")).getOrElse(ujson.Obj())
                val status = e.value.get("status").flatMap(v => v.numOpt.map(_.toLong.toString).orElse(v.strOpt))
                val head = (status.toList ++ s(e, "code").map(_ + ":").toList).mkString(" ")
                apiError = Some(s"$head ${s(e, "message").getOrElse("")}".trim)
            case _ => ()

    def tok(k: String): Option[Long] = usage.get(k)
    val (in, out, cache) = (tok("inputTokens"), tok("outputTokens"), tok("cacheReadTokens"))
    val cost =
      for
        model <- init.flatMap(s(_, "model"))
        p <- price(model)
        if in.isDefined || out.isDefined || cache.isDefined
      yield
        def rate(k: String) = p.value.get(k).flatMap(_.numOpt).getOrElse(0.0)
        (in.getOrElse(0L) * rate("input-per-mtok") + out.getOrElse(0L) * rate("output-per-mtok") +
          cache.getOrElse(0L) * rate("cache-read-per-mtok")) / 1e6

    Transcript(Option.when(steps > 0)(steps), in, out, cache, cost, calls.toVector, finalText, texts.toVector,
      hookTexts.toVector, hasResult, init, hookEvents.toVector, apiError)
