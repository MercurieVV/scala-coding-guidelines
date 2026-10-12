package harness.core

import cats.effect.IO
import scala.meta.*

/** One `def`'s cyclomatic complexity and source lines (GA erosion.scala's JSON row minus file and line). */
final case class MethodMetric(name: String, cc: Int, sloc: Int)

/** GA erosion.py + erosion.scala (spec 2026-10-11 chained feature work §3): the erosion figure of the main .scala files
  * that differ from the base tree. Per-method CC and SLOC come from scalameta, in-process now (GA ran it through
  * scala-cli); mass = CC * sqrt(SLOC); erosion = share of mass in methods with CC > 10 (SlopCodeBench).
  */
object Erosion extends ErosionMeasure:
  val CcEroded = 10

  private given Dialect = dialects.Scala3

  // ---------------------------------------------------------------- erosion.scala

  private def own(t: Tree): Int = t match
    case _: Term.If | _: Case | _: Term.While | _: Enumerator.Guard     => 1
    case a: Term.ApplyInfix if a.op.value == "&&" || a.op.value == "||" => 1
    case _                                                              => 0

  /** A nested def is its own method: its branches are not the enclosing method's. */
  private def branches(t: Tree): Int = t match
    case _: Defn.Def => 0
    case _           => own(t) + t.children.map(branches).sum

  private def sloc(lines: Array[String], d: Defn.Def): Int =
    (d.pos.startLine to d.pos.endLine).count { i =>
      val l = lines(i).trim
      l.nonEmpty && !l.startsWith("//") && !l.startsWith("/*") && !l.startsWith("*")
    }

  /** Every `def` of the source (nested ones included), in source order. Left: the parse error. */
  def methodMetrics(source: String): Either[String, List[MethodMetric]] =
    val lines = source.split("\n", -1)
    Input.VirtualFile("source.scala", source).parse[Source] match
      case Parsed.Success(tree) =>
        Right(tree.collect { case d: Defn.Def => MethodMetric(d.name.value, 1 + branches(d.body), sloc(lines, d)) })
      case e: Parsed.Error => Left(e.message)

  // ---------------------------------------------------------------- erosion.py

  /** Python `round(x, n)`: the exact binary value, correctly rounded, ties to even. */
  private def pyRound(x: Double, n: Int): Double =
    new java.math.BigDecimal(x).setScale(n, java.math.RoundingMode.HALF_EVEN).doubleValue

  def aggregate(ms: List[MethodMetric]): ErosionFigure =
    var total, eroded = 0.0
    for m <- ms do
      val mass = m.cc * math.sqrt(m.sloc.toDouble)
      total += mass
      if m.cc > CcEroded then eroded += mass
    ErosionFigure(erosion = if total != 0.0 then pyRound(eroded / total, 4) else 0.0, methods = ms.length, mass = pyRound(total, 3))

  private def skippedDir(name: String): Boolean =
    name.startsWith(".") || name == "out" || name.startsWith("out-") || name == "test"

  /** The main .scala files under `tree` that are new or differ byte for byte from the same path under `base`, sorted. */
  def changedMainFiles(tree: os.Path, base: String): IO[List[os.Path]] =
    IO.blocking {
      val head = os.Path(base)
      os.walk(tree, skip = p => skippedDir(p.last) && os.isDir(p, followLinks = false), includeTarget = false)
        .filter(p => p.last.endsWith(".scala") && os.isFile(p))
        .filter { p =>
          val h = head / p.relativeTo(tree)
          !os.isFile(h) || !java.util.Arrays.equals(os.read.bytes(p), os.read.bytes(h))
        }
        .sortBy(_.toString).toList
    }

  def measure(tree: os.Path, base: String): IO[Either[String, ErosionFigure]] =
    changedMainFiles(tree, base).flatMap { files =>
      IO.blocking {
        files.foldLeft[Either[String, List[MethodMetric]]](Right(Nil)) { (acc, f) =>
          acc.flatMap(done => methodMetrics(new String(os.read.bytes(f), "UTF-8")).left.map(m => s"$f: $m").map(done ++ _))
        }.map(aggregate)
      }
    }
