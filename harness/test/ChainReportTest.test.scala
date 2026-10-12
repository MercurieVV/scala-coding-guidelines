package harness.core

import munit.CatsEffectSuite
import harness.config.ChainReportSpec

/** GA tests/test_chain_report.py (7 cases): Pure (6), Main (1). Plus: a GA-shaped chain root (run.json without agent /
  * model, Review Focus 4) whose chain-report.md is byte-identical to what GA chain_report.py wrote for it, and the
  * `round(x, 3)` / int-vs-float rendering of the cost and wall columns.
  */
class ChainReportTest extends CatsEffectSuite:
  private val fixtures: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .map(_ / "test" / "fixtures" / "report").find(d => os.exists(d / "expected")).getOrElse(fail("fixtures not found"))

  private val spec = ChainReportSpec(control = "u", treatment = "gs")
  private def j(s: String): Py = Py.parse(s)

  // ---- class Pure

  test("step_failures"):
    assertEquals(ChainReport.stepFailures(j("""{"solved": true, "regression_count": 0}"""), j("""{"chain_regressions": 0}""")), Some(0L))
    assertEquals(ChainReport.stepFailures(j("""{"solved": false, "regression_count": 2}"""), j("""{"chain_regressions": 1}""")), Some(4L))

  test("step_failures_unknown_regression_count_is_none"):
    assertEquals(ChainReport.stepFailures(j("""{"solved": true, "regression_count": -1}"""), j("""{"chain_regressions": 0}""")), None)
    assertEquals(ChainReport.stepFailures(j("""{"solved": true, "regression_count": 0}"""), PNone), None)
    assertEquals(ChainReport.stepFailures(j("""{"solved": true, "regression_count": 0}"""), j("""{"chain_regressions": 0, "error": "boom"}""")), None)

  test("gate_verdict_withheld_while_steps_are_unknown"):
    assertEquals(ChainReport.gateLine(3, 1, List("a-u-1"), spec), "Gate: incomplete — unscored steps a-u-1 (u - gs = 2 so far)")
    assertEquals(ChainReport.gateLine(3, 1, Nil, spec), "Gate: gate pays on chains (u - gs = 2)")

  test("gate_verdict"):
    assertEquals(ChainReport.gateVerdict(3, 1), "gate pays on chains")
    assertEquals(ChainReport.gateVerdict(1, 1), "no gain")
    assertEquals(ChainReport.gateVerdict(1, 2), "no gain")
    assertEquals(ChainReport.gateVerdict(2, 1), "unclear")

  test("erosion_rises"):
    val chains = List(List(0.0, 0.1, 0.2, 0.3), List(0.0, 0.0, 0.0, 0.0), List(0.1, 0.2, 0.2, 0.4), List(0.0, 0.2, 0.1, 0.1))
      .map(_.map(Option(_)))
    assertEquals(ChainReport.erosionRises(chains, 0.05), (3, 4))
    assertEquals(ChainReport.erosionRises(chains, 0.35), (1, 4))
    assertEquals(ChainReport.erosionRises(List(List(Some(0.0), None, Some(0.1), None)), 0.0), (0, 0))

  test("item16_verdict"):
    assertEquals(ChainReport.item16Verdict(3, 4), "build item 16")
    assertEquals(ChainReport.item16Verdict(2, 4), "do not build item 16")

  // ---- class Main

  test("report_written"):
    val d = os.temp.dir(prefix = "chain-report-test")
    val tasks = List("a", "b")
    for arm <- List("u", "gs"); rep <- List(1, 2); (t, i) <- tasks.zipWithIndex do
      val rd = d / "runs" / s"$t-$arm-$rep"
      val bad = arm == "u" && i == 1
      os.write(rd / "score.json", s"""{"solved": ${!bad}, "false_done": $bad, "regression_count": 0, "wall_seconds": 10, "cost_usd": 0.1, "error": null}""",
        createFolders = true)
      os.write(rd / "chain.json", s"""{"chain_regressions": 0, "erosion": {"erosion": ${Json.pyFloat(0.1 * i)}}, "error": null}""")
    os.write(d / "chain-reference-erosion.json", """[{"task": "a", "erosion": 0.0}, {"task": "b", "erosion": 0.0}]""")
    ChainReport.main(d, tasks, spec).map { text =>
      assert(text.contains("u: failures 2"))
      assert(text.contains("gs: failures 0"))
      assert(text.contains("Gate: gate pays on chains"))
      assert(text.contains("false_done: b-u-1, b-u-2"))
      assert(os.exists(d / "chain-report.md"))
    }

  // ---- additions

  test("GA-shaped chain root (run.json without agent/model) reports byte-identical to GA chain_report.py"):
    val d = os.temp.dir(prefix = "chain-report-test") / "root"
    os.copy(fixtures / "chain-root", d)
    ChainReport.main(d, List("fr-pan", "adsr", "noise"), spec).map { text =>
      val want = os.read(fixtures / "expected" / "chain-report.md")
      assertEquals(text, want)
      assertEquals(os.read(d / "chain-report.md"), want)
    }

  test("median keeps the python type: odd count an element, even count a float"):
    assertEquals(Py.str(ChainReport.median(List(PInt(10), PInt(30), PInt(20)))), "20")
    assertEquals(Py.str(ChainReport.median(List(PInt(10), PInt(20)))), "15.0")
    assertEquals(Py.str(ChainReport.median(List(PFloat(1.5)))), "1.5")
