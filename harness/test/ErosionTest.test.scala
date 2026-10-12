package harness.core

import cats.effect.IO
import munit.CatsEffectSuite

/** GA tests/test_erosion.py (7 cases): Aggregate (3), ChangedFiles (3), ScalaMetric (1). The scala-cli subprocess is gone
  * (scalameta runs in-process), so "scala-cli not called" becomes "no parse for an unchanged tree" and "scala-cli
  * failure" becomes "an unparsable changed file is a Left". Plus the pyFloat parity pin the controller asked for.
  */
class ErosionTest extends CatsEffectSuite:
  private val fixture: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .find(d => os.exists(d / "test" / "fixtures" / "erosion" / "F.scala")).getOrElse(fail("fixtures not found")) /
      "test" / "fixtures" / "erosion" / "F.scala"

  // ---- class Aggregate

  test("empty"):
    assertEquals(Erosion.aggregate(Nil), ErosionFigure(erosion = 0.0, methods = 0, mass = 0.0))

  test("share_of_mass_above_cc_10"):
    val r = Erosion.aggregate(List(MethodMetric("a", 12, 4), MethodMetric("b", 2, 9)))
    val big = 12 * 2.0
    val small = 2 * 3.0
    assertEquals(r.methods, 2)
    assertEquals(r.mass, big + small)
    assertEquals(r.erosion, 0.8)
    assertEquals(r.erosion, BigDecimal(big / (big + small)).setScale(4, BigDecimal.RoundingMode.HALF_EVEN).toDouble)

  test("cc_exactly_10_is_not_eroded"):
    assertEquals(Erosion.aggregate(List(MethodMetric("a", 10, 1))).erosion, 0.0)

  test("aggregate rounds mass to 3 and erosion to 4 places like python round()"):
    val r = Erosion.aggregate(List(MethodMetric("a", 11, 2), MethodMetric("b", 3, 5)))
    assertEquals(r.mass, 22.265) // 11*sqrt(2) + 3*sqrt(5) = 15.556349... + 6.708203...
    assertEquals(r.erosion, 0.6987) // 15.5563 / 22.2645

  // ---- class ChangedFiles

  private def withTrees[A](f: (os.Path, os.Path) => IO[A]): IO[A] =
    IO.blocking(os.temp.dir(prefix = "erosion-")).flatMap { d =>
      val head = d / "head"
      val tree = d / "tree"
      IO.blocking {
        for root <- List(head, tree); rel <- List("m/src/A.scala", "m/src/B.scala", "m/test/src/ASpec.scala", ".dsh/x/C.scala", "out/m/D.scala") do
          os.write.over(root / os.RelPath(rel), "object X\n", createFolders = true)
      } *> f(tree, head).guarantee(IO.blocking(os.remove.all(d)))
    }

  test("only_changed_or_new_main_files"):
    withTrees { (tree, head) =>
      IO.blocking {
        os.write.over(tree / "m/src/A.scala", "object Changed\n")
        os.write.over(tree / "m/src/New.scala", "object New\n")
        List("m/test/src/ASpec.scala", ".dsh/x/C.scala", "out/m/D.scala").foreach(r => os.write.over(tree / os.RelPath(r), "object Changed\n"))
        os.write.over(tree / "out-agent/m/E.scala", "x", createFolders = true)
      } *> Erosion.changedMainFiles(tree, head.toString).map { got =>
        assertEquals(got.map(_.relativeTo(tree).toString), List("m/src/A.scala", "m/src/New.scala"))
      }
    }

  test("no_changes_measures_zero_without_parsing"):
    withTrees { (tree, head) =>
      // an unparsable file identical to head's is never parsed
      IO.blocking { os.write.over(tree / "m/src/A.scala", "object {{{", createFolders = true); os.write.over(head / "m/src/A.scala", "object {{{") } *>
        Erosion.measure(tree, head.toString).map(r => assertEquals(r, Right(ErosionFigure(erosion = 0.0, methods = 0, mass = 0.0))))
    }

  test("scala_cli_failure_raises"):
    withTrees { (tree, head) =>
      IO.blocking(os.write.over(tree / "m/src/A.scala", "object {{{")) *>
        Erosion.measure(tree, head.toString).map { r =>
          assert(r.isLeft, r.toString)
          assert(r.left.exists(_.contains("A.scala")), r.toString)
        }
    }

  test("measure aggregates the changed files' methods"):
    withTrees { (tree, head) =>
      IO.blocking(os.copy.over(fixture, tree / "m/src/A.scala")) *> Erosion.measure(tree, head.toString).map {
        case Right(f) => assertEquals(f.methods, 4); assert(f.erosion > 0.0 && f.erosion < 1.0, f.toString)
        case Left(m)  => fail(m)
      }
    }

  // ---- class ScalaMetric

  test("cc_and_sloc_per_method"):
    val ms = Erosion.methodMetrics(os.read(fixture)).fold(fail(_), _.map(m => m.name -> m).toMap)
    assertEquals(ms("small").cc, 5) // 1 + if + && + 2 cases
    assertEquals(ms("small").sloc, 6) // scalafmt puts the match on its own line
    assertEquals(ms("big").cc, 12) // 1 + 11 ifs
    assertEquals(ms("outer").cc, 1) // inner's if is inner's
    assertEquals(ms("outer").sloc, 3) // the comment line does not count
    assertEquals(ms("inner").cc, 2)

  test("methodMetrics: a parse error is a Left"):
    assert(Erosion.methodMetrics("object {{{").isLeft)

  // ---- erosion JSON uses python float repr

  test("pyFloat matches python repr for the erosion figures"):
    // python3: repr(0.5425) repr(0.0) repr(1.0) repr(1e-05) repr(0.1+0.2) repr(30.0) repr(123456.789) repr(0.0001) repr(5.5e-05)
    assertEquals(
      List(0.5425, 0.0, 1.0, 1e-05, 0.1 + 0.2, 30.0, 123456.789, 0.0001, 5.5e-05).map(Json.pyFloat),
      List("0.5425", "0.0", "1.0", "1e-05", "0.30000000000000004", "30.0", "123456.789", "0.0001", "5.5e-05"))

  test("ErosionFigure.pyJson is erosion.py's json.dumps order"):
    assertEquals(ErosionFigure(erosion = 0.5425, methods = 3, mass = 30.0).pyJson, """{"methods": 3, "total_mass": 30.0, "erosion": 0.5425}""")

  // PARITY.md (Task 13): GA fails with `erosion.scala exit N` (scala-cli's exit); in process the text names the file
  // and carries scalameta's parse message
  test("measure failure text is `<file>: <scalameta message>`"):
    withTrees { (tree, head) =>
      IO.blocking(os.write.over(tree / "m/src/A.scala", "object {{{")) *>
        Erosion.measure(tree, head.toString).map { r =>
          val msg = Erosion.methodMetrics("object {{{").left.getOrElse(fail("expected a parse error"))
          assertEquals(r, Left(s"${tree / "m" / "src" / "A.scala"}: $msg"))
        }
    }
