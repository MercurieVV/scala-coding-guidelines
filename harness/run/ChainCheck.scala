package harness.run

import cats.effect.IO
import cats.syntax.all.*
import harness.config.{Experiment, TaskSpec}
import harness.core.{Erosion, ErosionMeasure, Json}
import harness.target.{ScoreError, Target}
import scala.collection.mutable
import scala.util.control.NonFatal

/** GA chaincheck.py <run-dir> [<earlier-run-dir>...]: chain checks for one chained step (spec 2026-10-11 chained feature
  * work §3). Each earlier step's hidden spec runs on this step's final tree (status na when that spec had not passed at
  * the end of its own step), then the erosion figure of the tree. Writes <run-dir>/chain.json; infrastructure trouble
  * goes to its "error" field, never to a status.
  */
object ChainCheck:
  def ownPassed(rec: ujson.Value): Boolean =
    val o = rec.objOpt.getOrElse(mutable.LinkedHashMap.empty[String, ujson.Value])
    val compiled = o.get("hidden_compiled").exists {
      case ujson.Bool(b) => b
      case ujson.Num(n)  => n != 0
      case ujson.Str(s)  => s.nonEmpty
      case ujson.Null    => false
      case _             => true
    }
    compiled && o.get("hidden_pass_ratio").flatMap(_.numOpt).contains(1.0)

  def earlierStatus(compiled: Boolean, passed: Int, failed: Int, timedOut: Boolean): String =
    if compiled && !timedOut && failed == 0 && passed > 0 then "pass" else "fail"

  def chainRegressions(statuses: Seq[String]): Int = statuses.count(_ == "fail")

  def apply(exp: Experiment, runDir: os.Path, earlier: List[os.Path]): IO[ujson.Obj] =
    Target.of(exp.target).fold(m => IO.raiseError(HarnessError(m)), applyWith(exp, runDir, earlier, _, Erosion))

  /** GA main(rd, earlier_dirs). */
  def applyWith(exp: Experiment, rd: os.Path, earlierDirs: List[os.Path], target: Target, erosion: ErosionMeasure): IO[ujson.Obj] =
    val repo = rd / "repo"
    val tree = rd / "chain-tree"
    val earlier = ujson.Arr()
    val out = ujson.Obj(
      "run_id" -> rd.last, "earlier" -> earlier, "chain_regressions" -> ujson.Null, "erosion" -> ujson.Null,
      "erosion_error" -> ujson.Null, "error" -> ujson.Null)

    def one(ed: os.Path): IO[Unit] =
      for
        rec <- IO.blocking(ujson.read(os.read(ed / "score.json")))
        task <- IO.blocking(rec.obj.getOrElse("task", throw new NoSuchElementException("key not found: task")).str)
        _ <-
          if !ownPassed(rec) then
            IO(earlier.arr += ujson.Obj("task" -> task, "status" -> "na", "passed" -> 0, "failed" -> 0, "timed_out" -> false)).void
          else runSpec(task)
      yield ()

    def runSpec(task: String): IO[Unit] =
      for
        ts <- TaskSpec.load(exp.tasksDir, task).fold(m => IO.raiseError(ScoreError(m)), IO.pure)
        src = exp.root / "base" / task / "hidden" / os.RelPath(ts.hiddenSpec)
        _ <- IO.raiseUnless(os.isFile(src))(ScoreError(s"hidden spec $src missing"))
        text = Json.readLenient(src)
        fq <- target.suiteName(text).fold(IO.raiseError[String](ScoreError(s"no suite object in $src")))(IO.pure)
        dst = tree / os.RelPath(ts.hiddenSpec)
        log = rd / s"chain-$task.log"
        _ <- IO.blocking { os.makeDir.all(dst / os.up); os.copy.over(src, dst) }
        r <- target.runBuild(tree, target.hiddenRunArgs(ts.module, fq), "out-chain", log, cap = Some(exp.testCapSeconds))
        c <- IO.blocking(target.hiddenCounts(Json.readLenient(log), fq))
        _ <- IO(earlier.arr += ujson.Obj(
          "task" -> task, "status" -> earlierStatus(c.compiled, c.passed, c.failed, r.timedOut),
          "passed" -> c.passed, "failed" -> c.failed, "timed_out" -> r.timedOut))
        _ <- IO.blocking(os.remove(dst)) // one spec at a time: a spec that no longer compiles must not mask the next
      yield ()

    val checks =
      for
        _ <- IO.blocking {
          os.remove.all(tree)
          Files.copyTree(repo, tree, target.outDirs)
        }
        _ <- earlierDirs.traverse_(one)
        _ <- IO { // only a complete check counts (final review I3)
          out("chain_regressions") = chainRegressions(earlier.arr.map(_("status").str).toList)
        }
      yield ()

    val measured = erosion.measure(repo, (exp.root / "head").toString).attempt.map {
      case Right(Right(f)) => out("erosion") = erosionJson(f)
      case Right(Left(m))  => out("erosion_error") = s"ErosionError: $m"
      case Left(e)         => out("erosion_error") = pyError(e)
    }

    for
      _ <- checks.handleError(e => out("error") = pyError(e)).guarantee(IO.blocking(os.remove.all(tree)))
      _ <- measured
      _ <- IO.blocking(os.write.over(rd / "chain.json", dump(out)))
    yield out

  private def erosionJson(f: harness.core.ErosionFigure): ujson.Obj =
    ujson.Obj("methods" -> f.methods, "total_mass" -> f.mass, "erosion" -> f.erosion)

  /** GA `"%s: %s" % (type(e).__name__, e)`: the Python exception each failure corresponds to. */
  private[run] def pyError(e: Throwable): String = e match
    case _: ScoreError => s"ScoreError: ${e.getMessage}"
    case x: java.nio.file.NoSuchFileException => s"FileNotFoundError: [Errno 2] No such file or directory: '${x.getFile}'"
    case x: NoSuchElementException if Option(x.getMessage).exists(_.startsWith("key not found: ")) =>
      s"KeyError: '${x.getMessage.stripPrefix("key not found: ")}'"
    case x: (ujson.ParseException | ujson.IncompleteParseException) => s"JSONDecodeError: ${x.getMessage}"
    case x: ujson.Value.InvalidData => s"TypeError: ${x.getMessage}"
    case NonFatal(x) => s"${x.getClass.getSimpleName}: ${Option(x.getMessage).getOrElse("")}"
    case x => throw x

  // ---------------------------------------------------------------- python json.dump(obj, fh, indent=2)

  private val FloatKeys = Set("total_mass", "erosion")

  /** Python json.dump with indent=2 (ensure_ascii): chain.json is read back by chain_report; floats in repr form. */
  private[run] def dump(v: ujson.Value): String =
    val sb = new StringBuilder
    def str(s: String): Unit =
      sb.append('"')
      s.foreach {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case c if c < ' ' || c > '~' => sb.append(f"\\u${c.toInt}%04x")
        case c => sb.append(c)
      }
      sb.append('"')
    def go(x: ujson.Value, depth: Int, float: Boolean): Unit =
      val pad = "  " * (depth + 1)
      x match
        case ujson.Null => sb.append("null")
        case ujson.Bool(b) => sb.append(b)
        case ujson.Num(n) => sb.append(if float then Json.pyFloat(n) else n.toLong.toString)
        case ujson.Str(s) => str(s)
        case a: ujson.Arr =>
          if a.arr.isEmpty then sb.append("[]")
          else
            sb.append("[\n")
            a.arr.zipWithIndex.foreach { (e, i) =>
              if i > 0 then sb.append(",\n")
              sb.append(pad); go(e, depth + 1, float)
            }
            sb.append('\n').append("  " * depth).append(']')
        case o: ujson.Obj =>
          if o.value.isEmpty then sb.append("{}")
          else
            sb.append("{\n")
            o.value.zipWithIndex.foreach { case ((k, e), i) =>
              if i > 0 then sb.append(",\n")
              sb.append(pad); str(k); sb.append(": "); go(e, depth + 1, FloatKeys(k))
            }
            sb.append('\n').append("  " * depth).append('}')
    go(v, 0, float = false)
    sb.toString
