package harness.run

import cats.effect.IO
import cats.syntax.all.*
import harness.config.{JsonOp, SetupStep, Vars}
import scala.util.Try

/** A fatal harness error; printed as `gate-ab: <msg>` (GA ga_die). `prefix` names another GA script's own error
  * line (e.g. `score.py --baseline: <msg>`). */
final case class HarnessError(msg: String, prefix: String = "gate-ab") extends Exception(msg):
  def line: String = s"$prefix: $msg"

/** Executes an arm's setup steps in the run copy, in order (GA run.sh:42-52, lib.sh:24-32 and 54-63). */
object Steps:
  def apply(steps: List[SetupStep], vars: Map[String, String], repo: os.Path, armName: String): IO[Unit] =
    steps.traverse_(step(_, vars, repo, armName))

  private def die(msg: String): IO[Nothing] = IO.raiseError(HarnessError(msg))

  private def ex(s: String, vars: Map[String, String]): IO[String] =
    Vars.expand(s, vars).fold(die, IO.pure)

  private def step(s: SetupStep, vars: Map[String, String], repo: os.Path, arm: String): IO[Unit] =
    def x(v: String) = ex(v, vars)
    s match
      case SetupStep.Run(argv, fail) =>
        (argv.traverse(x), fail.traverse(x)).flatMapN((a, f) => runCmd(a, f, repo, arm))
      case SetupStep.Write(p, text) =>
        (x(p), x(text)).flatMapN((p, t) => IO.blocking(os.write.over(os.Path(p, repo), t, createFolders = true)))
      case SetupStep.AppendLine(p, text) =>
        (x(p), x(text)).flatMapN((p, t) => IO.blocking(appendLine(os.Path(p, repo), t)))
      case SetupStep.ReplaceLine(p, prefix, text) =>
        (x(p), x(prefix), x(text)).flatMapN((p, pre, t) => replaceLine(repo, p, pre, t))
      case SetupStep.JsonEdit(p, ops) =>
        x(p).flatMap(p => jsonEdit(os.Path(p, repo), ops, vars, arm))
      case SetupStep.Commit(msg, paths, allowEmpty, fail) =>
        (x(msg), paths.traverse(x), fail.traverse(x)).flatMapN((m, ps, f) => commit(repo, m, ps, allowEmpty, f, arm))

  // GA `sh install.sh <repo> >/dev/null || ga_die ...`: stdout discarded, stderr to the console
  // a command that cannot be launched (missing argv[0]) fails the same way as one that exits non-zero
  private def runCmd(argv: List[String], fail: Option[String], repo: os.Path, arm: String): IO[Unit] =
    IO.interruptible {
      import java.lang.ProcessBuilder.Redirect
      import scala.jdk.CollectionConverters.*
      new ProcessBuilder(argv.asJava).directory(repo.toIO).redirectInput(Redirect.from(new java.io.File("/dev/null")))
        .redirectOutput(Redirect.DISCARD).redirectError(Redirect.INHERIT).start().waitFor()
    }.attempt.flatMap {
        case Right(0)  => IO.unit
        case Right(rc) => die(fail.getOrElse(s"arm $arm: ${argv.mkString(" ")} failed (rc=$rc)"))
        case Left(e)   => die(fail.getOrElse(s"arm $arm: ${argv.mkString(" ")} failed (${e.getMessage})"))
      }

  /** GA lib.sh:28-29: skip when a line starts with the text's key (up to and including its first tab), `>>` otherwise. */
  private def appendLine(p: os.Path, text: String): Unit =
    val key = text.indexOf('\t') match
      case -1 => text
      case i  => text.take(i + 1)
    val present = os.isFile(p) && os.read.lines(p).exists(_.startsWith(key))
    if !present then os.write.append(p, text + "\n", createFolders = true)

  /** GA run.sh:51-52: sed `s|^<prefix> *=.*|<text>|` over every line, then the text must be a whole line. */
  private def replaceLine(repo: os.Path, path: String, prefix: String, text: String): IO[Unit] =
    val failMsg =
      if path == ".dsh/contract" then "contract rewrite failed (no holdoutDir line?)"
      else s"replace-line: no line starting with $prefix in $path"
    val re = ("^" + java.util.regex.Pattern.quote(prefix) + " *=.*").r
    IO.blocking {
      val f = os.Path(path, repo)
      if !os.isFile(f) then false
      else
        val lines = os.read(f).split("\n", -1)
        val out = lines.map(l => if re.matches(l) then text else l)
        os.write.over(f, out.mkString("\n"))
        out.contains(text)
    }.flatMap(ok => if ok then IO.unit else die(failMsg))

  private def commit(repo: os.Path, msg: String, paths: List[String], allowEmpty: Boolean, fail: Option[String], arm: String): IO[Unit] =
    val id = Seq("-c", "user.name=gate-eval", "-c", "user.email=gate-eval@local", "-c", "commit.gpgsign=false")
    val c = Seq("git") ++ id ++ Seq("commit", "-qm", msg) ++ (if allowEmpty then Seq("--allow-empty") else Nil)
    Proc.capture(Seq("git", "add") ++ paths, repo).flatMap {
      case (0, _) => Proc.capture(c, repo)
      case r      => IO.pure(r)
    }.flatMap {
      case (0, _) => IO.unit
      case _      => die(fail.getOrElse(s"arm $arm: commit $msg in $repo failed"))
    }

  // ---- json-edit: python json.load / json.dump(indent=2) round trip ----

  private def jsonEdit(f: os.Path, ops: List[JsonOp], vars: Map[String, String], arm: String): IO[Unit] =
    ops.traverse(expandOp(_, vars)).flatMap { ops =>
      IO.blocking {
        val first = ops.headOption.fold("json-edit failed")(opFailure(_, f))
        Try(ujson.read(os.read(f))).toOption match
          case None => Left(first)
          case Some(doc) =>
            ops.foldLeft[Either[String, Unit]](Right(()))((acc, op) => acc.flatMap(_ => applyOp(doc, op, f))).map { _ =>
              os.write.over(f, ujson.write(doc, indent = 2, escapeUnicode = true))
            }
      }.flatMap(_.fold(m => die(s"arm $arm: $m"), IO.pure))
    }

  private def expandOp(op: JsonOp, vars: Map[String, String]): IO[JsonOp] = op match
    case JsonOp.Delete(p, r)      => ex(p, vars).map(JsonOp.Delete(_, r))
    case JsonOp.KeepOnly(p, ks)   => (ex(p, vars), ks.traverse(ex(_, vars))).mapN(JsonOp.KeepOnly(_, _))
    case JsonOp.Filter(p, c, r)   => (ex(p, vars), ex(c, vars)).mapN(JsonOp.Filter(_, _, r))

  /** Failure texts: GA's for arms t (`cannot remove the Stop hook`) and p (`no tool-policy PreToolUse entry in <file>`). */
  private def opFailure(op: JsonOp, f: os.Path): String = op match
    case JsonOp.Delete(p, _) =>
      val segs = p.split('.').toList
      if segs.size == 2 && segs.head == "hooks" then s"cannot remove the ${segs(1)} hook" else s"cannot remove $p"
    case JsonOp.KeepOnly(p, _)  => s"no object at $p in $f"
    case JsonOp.Filter(p, c, _) => s"no ${c.trim.split("\\s+").last} ${p.split('.').last} entry in $f"

  /** (parent object, last key) of a dotted path, when every intermediate is an object. */
  private def parent(doc: ujson.Value, path: String): Option[(ujson.Obj, String)] =
    val segs = path.split('.').toList
    segs.init.foldLeft[Option[ujson.Value]](Some(doc))((cur, k) => cur.flatMap(objOf).flatMap(_.value.get(k)))
      .flatMap(objOf).map(_ -> segs.last)

  private def objOf(v: ujson.Value): Option[ujson.Obj] = v match
    case o: ujson.Obj => Some(o)
    case _            => None

  /** GA lib.sh:58-59: `any(text in h.get("command", "") for h in e.get("hooks", []))`. */
  private def hookCommands(e: ujson.Value): List[String] =
    objOf(e).flatMap(_.value.get("hooks")).collect { case ujson.Arr(hs) => hs.toList }.getOrElse(Nil)
      .flatMap(objOf).flatMap(_.value.get("command")).collect { case ujson.Str(c) => c }

  private def applyOp(doc: ujson.Value, op: JsonOp, f: os.Path): Either[String, Unit] =
    val fail = Left(opFailure(op, f))
    op match
      case JsonOp.Delete(p, required) =>
        parent(doc, p) match
          case Some((o, k)) if o.value.contains(k) => o.value.remove(k); Right(())
          case _                                   => if required then fail else Right(())
      case JsonOp.KeepOnly(p, keys) =>
        parent(doc, p).flatMap((o, k) => o.value.get(k).flatMap(objOf)) match
          case Some(o) => o.value.keys.toList.filterNot(keys.contains).foreach(o.value.remove); Right(())
          case None    => fail
      case JsonOp.Filter(p, contains, requireNonEmpty) =>
        parent(doc, p) match
          case Some((o, k)) =>
            o.value.get(k) match
              case Some(ujson.Arr(a)) =>
                val kept = a.filter(e => hookCommands(e).exists(_.contains(contains)))
                o.value(k) = ujson.Arr(kept)
                if requireNonEmpty && kept.isEmpty then fail else Right(())
              case Some(_) => fail
              case None    => if requireNonEmpty then fail else Right(())
          case None => if requireNonEmpty then fail else Right(())
