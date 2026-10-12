package harness.core

import java.nio.file.{Files, Paths}
import java.util.regex.Pattern
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

/** Where an agent run may and may not look.
  *
  * @param forbidden       roots the agent must not touch (GA: root/base, root/head, target repo, the runs dir, the agent's
  *                        private state dirs, and the holdout material inside the run dir, e.g. `runDir/holdout`,
  *                        `repo/out-holdout`). Forbidden paths under `runDir` win over the run-dir exemption.
  * @param ownAllowed      exempt from `forbidden` (GA: the run's own Claude project dir)
  * @param allowedPrefixes absolute path prefixes anything may touch (GA ALLOWED_PREFIXES plus home caches)
  * @param home            what `~` and `$HOME` expand to
  */
final case class Fence(
    runDir: os.Path,
    repo: os.Path,
    forbidden: List[os.Path],
    ownAllowed: List[os.Path],
    allowedPrefixes: List[String],
    home: os.Path = os.Path(sys.env.getOrElse("HOME", os.home.toString)))

/** Ports GA/score.py:410-606 (`_TOKEN`, `_leaves`, `_strip_heredocs`, `_root_arg`, `_bash_tokens`, `_path_part`,
  * `_candidate_paths`, `_resolve`, `contamination`).
  */
object Contamination:
  /** GA ALLOWED_PREFIXES (score.py:16-17). */
  val defaultAllowedPrefixes: List[String] =
    List("/tmp", "/private/tmp", "/var/folders", "/private/var/folders", "/dev/null", "/opt/homebrew", "/usr", "/bin",
      "/Library/Java", "/System", "/etc")

  private val Token = Pattern.compile("(?U)[^\\s\"'`;|&<>(){}=,]+")
  private val ContentKeys = Set("content", "new_string", "old_string", "new_source", "text", "edits", "write")
  private val Heredoc = Pattern.compile("(?<!<)<<(?!<)(-?)\\s*(['\"]?)([A-Za-z_][A-Za-z0-9_]*)\\2")
  private val ToFile = Pattern.compile("(?<![<>&0-9])>>?\\s*[^\\s&|;>]|\\btee\\b")
  private val Walkers = Set("find", "grep", "egrep", "fgrep", "rg", "ag", "ls", "du", "tree", "fd", "locate", "cd", "cat",
    "head", "tail", "less", "stat", "file")
  private val Ops = Set("|", "||", "&", "&&", ";", ";;", "|&")
  private val Assign = Pattern.compile("(?U)\\w+=.*")
  private val UserName = Pattern.compile("(?U)[A-Za-z_][\\w.-]*")
  private val ChmodChown = Pattern.compile("(?U)\\b(chmod|chown)\\b")

  private def findAll(p: Pattern, s: String): List[String] =
    p.matcher(s).results().iterator().asScala.map(_.group()).toList

  private def under(path: String, prefix: String): Boolean =
    path == prefix || path.startsWith(prefix.stripSuffix("/").replaceAll("/+$", "") + "/")

  /** posixpath.realpath: resolves symlinks of the existing prefix, normalises the rest, also for missing paths. */
  def realpath(path: String): String =
    var links = 0
    @tailrec def go(done: List[String], rest: List[String]): List[String] = rest match
      case Nil => done
      case ("" | ".") :: t => go(done, t)
      case ".." :: t => go(done.dropRight(1), t)
      case c :: t =>
        val cand = done :+ c
        val jp = Paths.get("/" + cand.mkString("/"))
        if Files.isSymbolicLink(jp) && links < 40 then
          links += 1
          val target = Files.readSymbolicLink(jp).toString
          val comps = target.split("/", -1).toList
          if target.startsWith("/") then go(Nil, comps ++ t) else go(done, comps ++ t)
        else go(cand, t)
    val abs = if path.startsWith("/") then path else os.pwd.toString + "/" + path
    "/" + go(Nil, abs.split("/", -1).toList).mkString("/")

  private def leaves(v: ujson.Value, key: Option[String]): Iterator[(Option[String], String)] = v match
    case ujson.Str(s)     => Iterator.single((key, s))
    case ujson.Obj(m)     => m.iterator.flatMap((k, x) => leaves(x, Some(k)))
    case ujson.Arr(items) => items.iterator.flatMap(leaves(_, key))
    case _                => Iterator.empty

  /** Drop the bodies of here-documents redirected into a file (`cat > A.scala <<'EOF'`, `>>`, `tee`): written content.
    * A here-document fed to an interpreter is a script and stays. The `<<EOF` line itself always stays.
    */
  def stripHeredocs(cmd: String): String =
    val out = scala.collection.mutable.ListBuffer.empty[String]
    val pending = scala.collection.mutable.Queue.empty[(String, Boolean, Boolean)] // (delim, dash, drop)
    for ln <- cmd.split("\n", -1) do
      if pending.nonEmpty then
        val (delim, dash, drop) = pending.head
        if (if dash then ln.dropWhile(_ == '\t') else ln) == delim then pending.dequeue()
        else if !drop then out += ln
      else
        out += ln
        val docs = Heredoc.matcher(ln).results().iterator().asScala.toList
        if docs.nonEmpty then
          val drop = ToFile.matcher(Heredoc.matcher(ln).replaceAll(" ")).find()
          docs.foreach(m => pending.enqueue((m.group(3), m.group(1) == "-", drop)))
    out.mkString("\n")

  /** Python `shlex.shlex(line, posix=True, punctuation_chars="|&;")` with `whitespace_split`; None on ValueError. */
  private[harness] def shlexWords(line: String): Option[List[String]] =
    val punct = "|&;"
    val ws = " \t\r\n"
    val words = scala.collection.mutable.ListBuffer.empty[String]
    val tok = new StringBuilder
    var i = 0
    var state = ' ' // ' ' between tokens, 'a' in a word, 'c' in a punctuation run, '\'' / '"' in quotes, '\\' escape
    var escapedState = 'a'
    var quoted = false
    def emit(): Unit =
      if tok.nonEmpty || quoted then words += tok.toString
      tok.clear(); quoted = false
    while i < line.length do
      val ch = line.charAt(i)
      state match
        case '\'' | '"' =>
          quoted = true
          if ch == state then state = 'a'
          else if ch == '\\' && state == '"' then { escapedState = state; state = '\\' }
          else tok += ch
          i += 1
        case '\\' =>
          if (escapedState == '\'' || escapedState == '"') && ch != '\\' && ch != escapedState then tok += '\\'
          tok += ch
          state = escapedState
          i += 1
        case ' ' =>
          if ws.indexOf(ch) >= 0 then i += 1
          else if ch == '#' then i = line.length
          else if ch == '\\' then { escapedState = 'a'; state = '\\'; i += 1 }
          else if punct.indexOf(ch) >= 0 then { tok += ch; state = 'c'; i += 1 }
          else if ch == '\'' || ch == '"' then { state = ch; i += 1 }
          else { tok += ch; state = 'a'; i += 1 }
        case 'a' =>
          if ws.indexOf(ch) >= 0 then { state = ' '; emit(); i += 1 }
          else if ch == '#' then { state = ' '; emit(); i = line.length }
          else if ch == '\'' || ch == '"' then { state = ch; i += 1 }
          else if ch == '\\' then { escapedState = 'a'; state = '\\'; i += 1 }
          else if punct.indexOf(ch) >= 0 then { state = ' '; emit() }
          else { tok += ch; i += 1 }
        case _ => // 'c'
          if ws.indexOf(ch) >= 0 then { state = ' '; emit(); i += 1 }
          else if ch == '#' then { state = ' '; emit(); i = line.length }
          else if punct.indexOf(ch) >= 0 then { tok += ch; i += 1 }
          else { state = ' '; emit() }
    if state == '\'' || state == '"' || state == '\\' then None
    else
      emit()
      Some(words.toList)

  /** True when a bare `/` is a standalone (unquoted) argument of a walker command in its own pipeline segment. */
  def rootArg(body: String): Boolean =
    body.split("\n", -1).exists: line =>
      shlexWords(line) match
        case None => false
        case Some(words) =>
          var cmd: Option[String] = None
          var found = false
          for w <- words if !found do
            if Ops.contains(w) then cmd = None
            else cmd match
              case None =>
                if Assign.matcher(w).matches() || Set("sudo", "env", "time", "command").contains(w) then ()
                else cmd = Some(w.substring(w.lastIndexOf('/') + 1))
              case Some(c) => if w == "/" && Walkers.contains(c) then found = true
          found

  /** Tokens of a Bash command, file-bound here-document bodies dropped. A bare `/` is the filesystem root only as an
    * unquoted argument of a walker; otherwise it is arithmetic or a regex.
    */
  def bashTokens(cmd: String): List[String] =
    val body = stripHeredocs(cmd).replace("${HOME}", "$HOME")
    val toks = findAll(Token, body)
    if rootArg(body) then toks else toks.filter(_ != "/")

  /** Comment markers are no paths; a glob token stands for its literal directory prefix. */
  private def pathPart(tok: String): Option[String] =
    if tok.startsWith("//") || tok.startsWith("/*") then None
    else if tok.contains("*") then
      val pre = tok.substring(0, tok.indexOf('*'))
      if !pre.contains("/") then None
      else Some(Some(pre.substring(0, pre.lastIndexOf('/'))).filter(_.nonEmpty).getOrElse("/"))
    else Some(tok)

  /** A search tool's `pattern` is a regex, except a glob tool's (`Glob`, dsh / Gemini `glob`), whose pattern is a path. */
  private def regexPattern(tool: String, kind: ToolKind): Boolean =
    kind == ToolKind.Search && !tool.equalsIgnoreCase("glob")

  /** Path-like strings in every string leaf of a tool call's input (written content, here-document bodies and search
    * regexes excluded). Dispatches on the call's [[ToolKind]] (final review I2): a shell call's `command` goes through
    * [[bashTokens]] for every agent, not only Claude's `Bash`.
    */
  def candidatePaths(tool: String, kind: ToolKind, inp: ujson.Value): List[String] =
    val out = scala.collection.mutable.ListBuffer.empty[String]
    for (key, v) <- leaves(inp, None) do
      if key.exists(ContentKeys.contains) then ()
      else if regexPattern(tool, kind) && key.contains("pattern") then ()
      else if kind == ToolKind.Shell && key.contains("command") then out ++= bashTokens(v)
      else if v.exists(c => Character.isWhitespace(c) || Character.isSpaceChar(c)) then out ++= findAll(Token, v)
      else out += v
    out.toList.flatMap(pathPart).filter(_.nonEmpty)

  private def expandUser(tok: String): String =
    val user = tok.drop(1).takeWhile(_ != '/')
    val rest = tok.drop(1 + user.length)
    val r = os.proc("/bin/sh", "-c", "echo ~" + user).call(check = false, stderr = os.Pipe)
    val home = if r.exitCode == 0 then r.out.text().stripSuffix("\n") else ""
    if home.isEmpty || home == "~" + user then tok else home.stripSuffix("/") + rest

  /** Absolute/relative path-like token -> normalised absolute path, or None if it is not path-like. */
  def resolve(token: String, repo: String, home: String): Option[String] =
    var tok = if token.startsWith("file://") then token.drop("file://".length) else token
    if tok.isEmpty || tok.contains("://") then None
    else
      if tok == "~" || tok == "$HOME" || tok.startsWith("~/") || tok.startsWith("$HOME/") then
        tok = home + tok.drop(if tok.startsWith("~") then 1 else 5)
      else if tok.startsWith("~") then
        val user = tok.drop(1).takeWhile(_ != '/')
        if !UserName.matcher(user).matches() then tok = ""
        else
          val expanded = expandUser(tok)
          tok = if expanded == tok then "" else expanded
      if tok.isEmpty then None
      else if tok.startsWith("/") then Some(realpath(tok))
      else if tok.length < 2 then None
      else if tok.contains("/") || tok == ".." then Some(realpath(repo + "/" + tok))
      else None

  /** Paths in tool inputs the agent must not touch: anything under `forbidden` (the own-allowed dirs excepted), any
    * ANCESTOR of those (a search rooted at /, ~, the experiment root ...), and anything outside the run dir and the
    * allowed prefixes.
    */
  def check(calls: Seq[ToolCall], fence: Fence): List[String] =
    val run = realpath(fence.runDir.toString)
    val repo = realpath(fence.repo.toString)
    val home = fence.home.toString
    val forbidden = fence.forbidden.map(p => realpath(p.toString))
    val inside = forbidden.filter(under(_, run)) // holdout material inside the run dir: wins over the run-dir exemption
    val ownAllowed = fence.ownAllowed.map(p => realpath(p.toString))
    val allowed = fence.allowedPrefixes ++ fence.allowedPrefixes.map(realpath)

    def inRun(p: String) = under(p, run)
    def hit(p: String): Boolean =
      if inside.exists(under(p, _)) then true
      else if inRun(p) || ownAllowed.exists(under(p, _)) then false
      else if forbidden.exists(under(p, _)) then true
      else if forbidden.exists(under(_, p)) then true // an ancestor: a search rooted above protected material
      else !allowed.exists(under(p, _))

    calls.toList.flatMap: c =>
      val flagged = scala.collection.mutable.SortedSet.empty[String]
      for tok <- candidatePaths(c.name, c.kind, c.input) do
        resolve(tok, repo, home) match
          case None =>
            val bare = repo + "/" + tok // `ls out-holdout` from the repo
            if !tok.contains("/") && inside.exists(under(bare, _)) then flagged += bare
          case Some(p) => if hit(p) then flagged += p
      if c.kind == ToolKind.Shell then
        val cmd = c.command
        if ChmodChown.matcher(cmd).find() then
          for tok <- bashTokens(cmd) do
            resolve(tok, repo, home).foreach(p => if !inRun(p) then flagged += p)
      flagged.toList.map(p => s"${c.name}: $p")
