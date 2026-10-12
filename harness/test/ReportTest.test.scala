package harness.core

import cats.effect.IO
import munit.CatsEffectSuite
import harness.config.ReportSpec

/** GA tests/test_report.py (27 cases): Stats (3), Decide (11), EndToEnd (1), AblationVerdict (2), AblationSection (9),
  * UnknownArmTest (1). Plus: a GA-shaped root (run.json without agent / model, Review Focus 4) whose summary.md and
  * results.csv are byte-identical to what GA report.py wrote for it (test/fixtures/report/expected), Python `%.Nf`
  * ties, and a non-default arm spec.
  */
class ReportTest extends CatsEffectSuite:
  private val fixtures: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .map(_ / "test" / "fixtures" / "report").find(d => os.exists(d / "expected")).getOrElse(fail("fixtures not found"))

  private val spec = ReportSpec(treatment = "g", control = "u", ablation = "t")

  private def near(a: Double, b: Double, places: Int = 7): Unit =
    assert(math.abs(BigDecimal(a - b).setScale(places, BigDecimal.RoundingMode.HALF_EVEN).toDouble) == 0.0, s"$a != $b")

  // ---- class Stats

  test("wilson_known_value"):
    val (lo, hi) = Stats.wilson(12, 24)
    near(lo, 0.314, 3); near(hi, 0.686, 3)

  test("wilson_zero_n"):
    assertEquals(Stats.wilson(0, 0), (0.0, 1.0))

  test("sign_test"):
    near(Stats.signTest(List(1, 1, 1, 1, 1, 1, 1, 1)), 2.0 / 256)
    near(Stats.signTest(List(1, -1, 0)), 1.0)

  // ---- class Decide

  private def d(gs: Double, us: Double, gf: Int = 0, uf: Int = 0, gw: Option[Double] = Some(100), uw: Option[Double] = Some(100),
                gn: Int = 24, un: Int = 24): String =
    Report.decide(Report.ArmFigures(gs, gf, gw, gn), Report.ArmFigures(us, uf, uw, un))

  test("pays_off_on_solve_rate")(assertEquals(d(0.8, 0.5), "pays off"))
  test("pays_off_on_false_done")(assertEquals(d(0.6, 0.6, gf = 2, uf = 6), "pays off"))
  test("too_slow")(assertEquals(d(0.8, 0.5, gw = Some(300), uw = Some(100)), "does not pay off"))
  test("inconclusive")(assertEquals(d(0.6, 0.5), "inconclusive"))

  test("missing_wall_blocks_pays_off"):
    for (gw, uw) <- List((None, Some(100.0)), (Some(100.0), None), (None, None)) do
      assertEquals(d(0.8, 0.5, gw = gw, uw = uw), "inconclusive")

  test("exact_gain_boundary"):
    // GA passes no "n": fd_rate is None, so only the solve-rate term can fire
    assertEquals(Report.decide(Report.ArmFigures(12.0 / 20, 0, Some(100)), Report.ArmFigures(8.0 / 20, 0, Some(100))), "pays off")
    assertEquals(d(0.6, 0.4), "pays off")

  test("exact_quarter_not_inconclusive"):
    assertEquals(d(0.25, 0.5), "does not pay off")
    assertEquals(d(0.75, 0.5), "pays off")
    assertEquals(d(0.4, 0.65), "does not pay off")

  test("no_false_done_win_when_ungated_zero")(assertEquals(d(0.6, 0.6, gf = 0, uf = 0), "inconclusive"))

  test("false_done_needs_three_ungated"):
    assertEquals(d(0.6, 0.6, gf = 0, uf = 1), "inconclusive")
    assertEquals(d(0.6, 0.6, gf = 1, uf = 2), "inconclusive")
    assertEquals(d(0.6, 0.6, gf = 1, uf = 3), "pays off")

  test("false_done_compared_as_rates"):
    assertEquals(d(0.6, 0.6, gf = 2, uf = 4, gn = 12, un = 24), "inconclusive")
    assertEquals(d(0.6, 0.6, gf = 2, uf = 4, gn = 24, un = 12), "pays off")
    assertEquals(d(0.6, 0.6, gf = 2, uf = 3, gn = 24, un = 24), "inconclusive")

  test("worse")(assertEquals(d(0.2, 0.6), "does not pay off"))

  // ---- rec (GA test helper): a score.json dict in GA's key order

  private def rec(rid: String, task: String, arm: String, rep: Int, kw: (String, Py)*): PDict =
    val base = Vector[(String, Py)](
      "run_id" -> PStr(rid), "task" -> PStr(task), "arm" -> PStr(arm), "rep" -> PInt(rep), "solved" -> PBool(false),
      "regressions" -> PList(Vector()), "test_tampering" -> PList(Vector()), "hardcoding_suspects" -> PList(Vector()),
      "build_edits" -> PList(Vector()), "false_done" -> PBool(false), "timed_out" -> PBool(false),
      "contaminated" -> PBool(false), "contamination" -> PList(Vector()), "gate_blocks" -> PDict(Vector()),
      "wall_seconds" -> PInt(100), "turns" -> PInt(10), "cost_usd" -> PFloat(1.0), "score_timeouts" -> PList(Vector()),
      "error" -> PNone)
    kw.foldLeft(PDict(base))((r, kv) => r.updated(kv._1, kv._2))

  private def strs(xs: String*): PList = PList(xs.map(PStr(_)).toVector)
  private val T = PBool(true)

  private def writeRuns(d: os.Path, runs: Seq[PDict]): Unit =
    for r <- runs do
      val rid = r.get("run_id").asInstanceOf[PStr].s
      os.write(d / rid / "score.json", Py.dumps(r), createFolders = true)

  private def tmp(): os.Path = os.temp.dir(prefix = "report-test")

  private def parseCsv(text: String): List[Map[String, String]] =
    // the fixtures here never quote; csv.DictReader equivalent for unquoted rows
    val rows = text.split("\r\n").toList.map(_.split(",", -1).toList)
    rows.tail.map(r => rows.head.zip(r).toMap)

  // ---- class EndToEnd

  test("counts"):
    val runs = List(
      rec("a-g-1", "a", "g", 1, "solved" -> T, "gate_blocks" -> PDict(Vector("turn-end" -> PInt(2))), "build_edits" -> strs("build.mill")),
      rec("a-u-1", "a", "u", 1, "turns" -> PNone, "cost_usd" -> PNone, "regressions" -> strs("<compile:core>")),
      rec("a-g-9", "a", "g", 9, "solved" -> T),
      rec("a-u-2", "a", "u", 2, "error" -> PStr("scoring incomplete")),
      rec("a-g-2", "a", "g", 2, "solved" -> T, "contaminated" -> T, "contamination" -> strs("read holdout")),
      rec("a-u-3", "a", "u", 3, "timed_out" -> T, "regressions" -> strs("<timeout>"))
    )
    val dir = tmp()
    writeRuns(dir, runs)
    Report.main(dir, spec).map { _ =>
      val rows = parseCsv(os.read(dir / "results.csv"))
      assertEquals(rows.length, 5)
      assert(!rows.map(_("run_id")).contains("a-g-9"))
      val by = rows.map(r => r("run_id") -> r).toMap
      assertEquals(by("a-g-1")("gate_blocks_total"), "2")
      assertEquals(by("a-g-1")("build_edits"), "build.mill")
      assertEquals(by("a-g-1")("build_edits_count"), "1")
      assertEquals(by("a-u-2")("counted"), "no")
      assertEquals(by("a-u-3")("counted"), "yes")
      val s = os.read(dir / "summary.md")
      assert(s.contains("\nVerdict: pays off\n"))
      assert(s.contains("a-g-9"))
      assert(!s.contains("raw counts"))
      assert(s.contains("| false_done (rate) | 0 (0.00) | 0 (0.00) |"))
      assert(s.contains("u.false_done >= 3"))
      assert(s.contains("1 smoke runs"))
      assert(s.contains("| n (headline) | 1 | 2 |"))
      assert(s.contains("| timeouts (counted, unsolved) | 0 | 1 |"))
      assert(s.contains("| contaminated (excluded) | 1 | 0 |"))
      assert(s.contains("| error (excluded) | 0 | 1 |"))
      assert(s.contains("- a-u-2 (error: scoring incomplete)"))
      assert(s.contains("- a-g-2 (contaminated: read holdout)"))
      assert(s.contains("| <timeout> | 0 | 1 |"))
      assert(s.contains("| turn-end | 2 | 1 |"))
      assert(s.contains("| median turns (n) | 10.0 (1) | 10.0 (1) |"))
    }

  // Final review I4: a generic (transcript-less) run's contamination is unchecked, shown as such, not counted clean
  test("unchecked contamination (no transcript) is reported, not counted clean"):
    val unchecked = List[(String, Py)]("contaminated" -> PNone, "contamination" -> PNone, "gate_blocks" -> PNone,
      "env_leaks" -> PNone, "turns" -> PNone, "cost_usd" -> PNone)
    val runs = List(
      rec("a-g-1", "a", "g", 1, "solved" -> T),
      rec("a-u-1", "a", "u", 1, (("solved" -> T) :: unchecked)*),
      rec("b-u-1", "b", "u", 1, unchecked*))
    val dir = tmp()
    writeRuns(dir, runs)
    Report.main(dir, spec).map { s =>
      assert(s.contains("| contaminated (excluded) | 0 | 0 |"), s)
      assert(s.contains("| contamination unchecked (no transcript) | 0 | 2 |"), s)
      assert(s.contains("Gate blocks unchecked (no transcript): 0 runs."), s)
      val by = parseCsv(os.read(dir / "results.csv")).map(r => r("run_id") -> r).toMap
      assertEquals((by("a-u-1")("contaminated"), by("a-u-1")("gate_blocks_total")), ("unchecked", "unchecked"))
      assertEquals(by("a-g-1")("contaminated"), "False")
    }

  test("GA-shaped runs: no unchecked rows"):
    val dir = tmp()
    writeRuns(dir, List(rec("a-g-1", "a", "g", 1), rec("a-u-1", "a", "u", 1)))
    Report.main(dir, spec).map(s => assert(!s.contains("unchecked"), s))

  // ---- class AblationVerdict

  test("bands"):
    assertEquals(Report.ablationVerdict(0, 8), "turn-end carries the gain")
    assertEquals(Report.ablationVerdict(5, 8), "turn-end carries the gain")
    assertEquals(Report.ablationVerdict(6, 8), "unclear")
    assertEquals(Report.ablationVerdict(7, 8), "unclear")
    assertEquals(Report.ablationVerdict(8, 8), "the gain comes without turn-end")

  test("incomplete"):
    assertEquals(Report.ablationVerdict(7, 7), "incomplete (7 of 8)")
    assertEquals(Report.ablationVerdict(0, 0), "incomplete (0 of 8)")

  // ---- class AblationSection

  private def write(runs: Seq[PDict], audit: Option[String] = None): IO[String] =
    val dir = tmp()
    audit.foreach(a => os.write(dir / "audit-cleared.txt", a))
    writeRuns(dir, runs)
    Report.main(dir, spec).map(_ => os.read(dir / "summary.md"))

  private def base: List[PDict] = List(
    rec("a-g-1", "a", "g", 1, "solved" -> T, "wall_seconds" -> PInt(200)), rec("a-u-1", "a", "u", 1, "wall_seconds" -> PInt(50)),
    rec("b-g-1", "b", "g", 1, "solved" -> T), rec("b-u-1", "b", "u", 1, "solved" -> T))

  private def t8: List[PDict] = (1 to 8).toList.map(i => rec(s"a-t-$i", "a", "t", i, "solved" -> T))

  test("no_t_runs_no_section"):
    write(base).map { s =>
      assert(!s.contains("Ablation"))
      assert(s.contains("| metric | gated | ungated |"))
      assert(s.stripSuffix("\n").linesIterator.toList.last.startsWith("false_done: gated"))
    }

  test("section_restricted_to_t_tasks"):
    val tRuns = (1 to 8).map(i => rec(s"a-t-$i", "a", "t", i, "solved" -> PBool(i <= 6), "wall_seconds" -> PInt(60)))
    write(base ++ tRuns).map { s =>
      assert(s.contains("## Ablation: gate without turn-end (t)"))
      assert(s.contains("Tasks: a"))
      assert(s.contains("| n (headline) | 1 | 8 | 1 |"))
      assert(s.contains("| solved | 1 | 6 | 0 |"))
      assert(s.contains("| median wall s | 200 | 60 | 50 |"))
      assert(s.contains("\nAblation verdict (strict): unclear\n"))
      assert(s.contains("Strict (contamination flags excluded):"))
      assert(s.contains("\nVerdict: "))
    }

  test("contaminated_t_run_makes_incomplete"):
    val tRuns = t8.updated(0, t8.head.updated("contaminated", T))
    write(base ++ tRuns).map(s => assert(s.contains("\nAblation verdict (strict): incomplete (7 of 8)\n")))

  test("audit_clears_contaminated_run"):
    val tRuns = t8.updated(0, t8.head.updated("contaminated", T))
    write(base ++ tRuns, Some("a-t-1\n")).map { s =>
      assert(s.contains("\nAblation verdict (strict): incomplete (7 of 8)\n"))
      assert(s.contains("Audited (cleared: a-t-1):"))
      assert(s.contains("\nAblation verdict (audited): the gain comes without turn-end\n"))
    }

  test("audit_never_clears_errors"):
    val tRuns = t8.updated(0, t8(0).updated("error", PStr("boom"))).updated(1, t8(1).updated("contaminated", T))
    write(base ++ tRuns, Some("a-t-1\na-t-2\n")).map { s =>
      assert(s.contains("Audited (cleared: a-t-2):"))
      assert(s.contains("\nAblation verdict (audited): incomplete (7 of 8)\n"))
    }

  test("no_audit_file"):
    write(base ++ t8).map { s =>
      assert(s.contains("Audited: no audit-cleared.txt; the strict verdict stands."))
      assert(!s.contains("Ablation verdict (audited)"))
    }

  test("audit_comments_blanks_and_noncontaminated_ignored"):
    val tRuns = t8.updated(0, t8.head.updated("contaminated", T))
    write(base ++ tRuns, Some("# note\n\n   \n  a-t-1  \n# a-t-9\na-t-5\n")).map { s =>
      assert(s.contains("Audited (cleared: a-t-1):"))
      assert(s.contains("\nAblation verdict (audited): the gain comes without turn-end\n"))
    }

  test("audit_file_without_matches_says_none"):
    write(base ++ t8, Some("# nothing\n")).map(s => assert(s.contains("Audited (cleared: none):")))

  test("ablation_g_u_restricted_to_t_reps"):
    val tRuns = List(1, 2).map(i => rec(s"a-t-$i", "a", "t", i, "solved" -> T))
    val extra = List(rec("a-g-3", "a", "g", 3, "solved" -> T))
    write(base ++ tRuns ++ extra).map { s =>
      assert(s.contains("| n (headline) | 1 | 2 | 1 |"))
      assert(s.contains("| solved | 1 | 2 | 0 |"))
    }

  // ---- class UnknownArmTest

  test("feature_study_arms_are_refused_not_bucketed_silently"):
    val runs = Vector(PDict(Vector("run_id" -> PStr("fr-pan-gf-3"), "arm" -> PStr("gf"), "task" -> PStr("fr-pan"), "rep" -> PInt(3))))
    val e = intercept[IllegalArgumentException](Report.buildSummary(runs, Vector(), Vector(), None, spec))
    assert(e.getMessage.contains("gf"), e.getMessage)

  // ---- additions

  test("GA-shaped root (run.json without agent/model) reports byte-identical to GA report.py"):
    val dir = tmp() / "root"
    os.copy(fixtures / "report-root", dir)
    assert(os.list(dir).forall(d => !os.exists(d / "run.json") || !os.read(d / "run.json").contains("\"agent\"")))
    Report.main(dir, spec).map { text =>
      assertEquals(os.read(dir / "summary.md"), os.read(fixtures / "expected" / "summary.md"))
      assertEquals(text, os.read(fixtures / "expected" / "summary.md"))
      assertEquals(os.read(dir / "results.csv"), os.read(fixtures / "expected" / "results.csv"))
    }

  test("fmt is python %.Nf: exact binary value, ties to even"):
    assertEquals(Report.fmt(Some(0.125), 2), "0.12")
    assertEquals(Report.fmt(Some(2.675), 2), "2.67")
    assertEquals(Report.fmt(Some(174.5), 0), "174")
    assertEquals(Report.fmt(Some(175.5), 0), "176")
    assertEquals(Report.fmt(Some(-0.001), 2), "-0.00")
    assertEquals(Report.fmt(Some(10.0), 1), "10.0")
    assertEquals(Report.fmt(None, 1), "n/a")

  test("arm names come from the spec"):
    val runs = List(
      rec("a-x-1", "a", "x", 1, "solved" -> T), rec("a-y-1", "a", "y", 1),
      rec("a-z-1", "a", "z", 1, "solved" -> T))
    val dir = tmp()
    writeRuns(dir, runs)
    Report.main(dir, ReportSpec(treatment = "x", control = "y", ablation = "z")).map { s =>
      assert(s.contains("| n (headline) | 1 | 1 |"), s)
      assert(s.contains("| solved | 1 | 0 |"), s)
      assert(s.contains("## Ablation: gate without turn-end (z)"), s)
      assert(s.contains("| solved | 1 | 1 | 0 |"), s)
      assert(s.contains("Rule: pays off if (x.solve_rate - y.solve_rate >= 0.20"), s)
    }

  // Task 18 report parity: GA main opens summary.md for writing before build_summary raises, so an arm outside g/u/t
  // leaves results.csv written and summary.md empty (an older summary does not survive)
  test("unknown arm: csv written, summary.md emptied, GA's error"):
    val dir = tmp()
    writeRuns(dir, List(rec("a-g-1", "a", "g", 1), rec("a-gs-1", "a", "gs", 1)))
    os.write(dir / "summary.md", "old summary\n")
    Report.main(dir, spec).attempt.map { r =>
      assertEquals(r.left.toOption.map(_.getMessage), Some("report.py summarises arms g, u, t only; found gs"))
      assert(os.read(dir / "results.csv").contains("a-gs-1"))
      assertEquals(os.read(dir / "summary.md"), "")
    }

  // PARITY.md (Task 14): GA's wording only for blank files and Errno 2/13/21; a syntax error carries ujson's message, and
  // NaN / Infinity (python json loads them) make the file unreadable
  test("unreadable score.json: ujson wording for syntax errors, NaN rejected"):
    val dir = tmp()
    os.write(dir / "a-g-1" / "score.json", "{", createFolders = true)
    os.write(dir / "a-g-2" / "score.json", """{"run_id":"a-g-2","cost_usd":NaN}""", createFolders = true)
    os.write(dir / "a-g-3" / "score.json", "", createFolders = true)
    val (runs, _, unreadable) = Report.loadRuns(dir)
    assertEquals(runs, Vector.empty)
    assertEquals(unreadable.map(_.takeWhile(_ != ':')), Vector("a-g-1", "a-g-2", "a-g-3"))
    assert(unreadable.take(2).forall(u => !u.contains("Expecting")), unreadable.toString)
    assertEquals(unreadable(2), "a-g-3: Expecting value: line 1 column 1 (char 0)")
