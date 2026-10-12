package harness.core

import cats.effect.IO
import harness.config.ReportSpec
import scala.collection.mutable

/** Port of GA/report.py: aggregate `<root>/<run>/score.json` into results.csv and summary.md with the pre-registered
  * verdict. GA's arms g / u / t are `spec.treatment` / `spec.control` / `spec.ablation`; with the default spec the text
  * is byte-identical to GA's.
  *
  * Counting rules: rep 9 = smoke run, skipped everywhere. Runs with a non-null `error` or contaminated=true are excluded
  * from the headline (counted and listed separately). Timeouts without error are outcomes (unsolved, counted).
  */
object Report:
  val SmokeRep = 9
  val FalseDoneMin = 3 // Ruling 21: fewer ungated false_done runs cannot show a halving
  val AblationRuns = 8 // gate ablation spec §4: 4 tasks x 2 reps of arm t
  private val Eps = 1e-9 // exact-boundary tolerance for rates derived from k/n floats

  def rule(spec: ReportSpec): String =
    val g = spec.treatment
    val u = spec.control
    s"pays off if ($g.solve_rate - $u.solve_rate >= 0.20 or ($u.false_done >= 3 and $g.false_done_rate <= $u.false_done_rate / 2)) " +
      s"and $g.median_wall <= 2 * $u.median_wall; else inconclusive if |$g.solve_rate - $u.solve_rate| < 0.25; " +
      "else does not pay off. false_done_rate = false_done / headline runs of the arm; timeouts are reported beside it " +
      "(a timeout is neither solved nor false_done)"

  def ablationRule(spec: ReportSpec): String =
    s"over $AblationRuns counted runs of arm ${spec.ablation} (gate without the turn-end Stop hook): solved <= 5 -> turn-end carries the gain; " +
      s"solved = 8 -> the gain comes without turn-end; 6 or 7 -> unclear; fewer than $AblationRuns counted runs -> incomplete"

  def ablationVerdict(solved: Int, n: Int): String =
    if n != AblationRuns then s"incomplete ($n of $AblationRuns)"
    else if solved <= 5 then "turn-end carries the gain"
    else if solved == AblationRuns then "the gain comes without turn-end"
    else "unclear"

  /** GA `decide`'s argument dict; `n = 0` is GA's missing / zero "n" (no false_done rate). */
  final case class ArmFigures(solveRate: Double, falseDone: Int, medianWall: Option[Double], n: Int = 0)

  private def fdRate(a: ArmFigures): Option[Double] = if a.n != 0 then Some(a.falseDone.toDouble / a.n) else None

  /** false_done compared as rates (Ruling 21). */
  def decide(treatment: ArmFigures, control: ArmFigures): String =
    val diff = treatment.solveRate - control.solveRate
    val fewerFalseDone = control.falseDone >= FalseDoneMin && ((fdRate(treatment), fdRate(control)) match
      case (Some(gr), Some(ur)) => gr <= ur / 2 + Eps
      case _                    => false)
    val better = diff >= 0.20 - Eps || fewerFalseDone
    val wall: Option[String] =
      if !better then None
      else (treatment.medianWall, control.medianWall) match
        case (Some(gw), Some(uw)) => if gw <= 2 * uw then Some("pays off") else None
        case _                    => Some("inconclusive") // mandatory wall-time term cannot be checked
    wall.getOrElse(if math.abs(diff) < 0.25 - Eps then "inconclusive" else "does not pay off")

  /** Python `"%.*f" % (nd, x)`: the exact binary value, correctly rounded, ties to even. */
  def fixed(x: Double, nd: Int): String =
    if x.isNaN then "nan"
    else if x.isInfinite then (if x > 0 then "inf" else "-inf")
    else
      val s = new java.math.BigDecimal(x).setScale(nd, java.math.RoundingMode.HALF_EVEN).toPlainString
      if (x < 0 || (x == 0.0 && 1 / x < 0)) && !s.startsWith("-") then "-" + s else s

  def fmt(x: Option[Double], nd: Int = 1): String = x.fold("n/a")(fixed(_, nd))

  /** `", ".join(xs) or "none"`. */
  private def orNone(xs: Seq[String]): String =
    val j = xs.mkString(", ")
    if j.isEmpty then "none" else j

  private def plusD(d: Int): String = if d >= 0 then s"+$d" else d.toString

  private def asDict(v: Py): PDict = v match
    case d: PDict => d
    case o        => throw Py.PyError(s"'${Py.typeName(o)}' object has no attribute 'get'")

  private def items(v: Py): Vector[(String, Py)] = v match
    case PDict(kv) => kv
    case o         => throw Py.PyError(s"'${Py.typeName(o)}' object has no attribute 'items'")

  /** (runs, smoke run ids, unreadable lines) from every `<root>/<run>/score.json` in GA's glob order. */
  def loadRuns(root: os.Path): (Vector[PDict], Vector[String], Vector[String]) =
    val runs = Vector.newBuilder[PDict]
    val smoke, unreadable = Vector.newBuilder[String]
    val found =
      if !os.isDir(root) then Vector.empty
      else os.list(root).filter(d => !d.last.startsWith(".") && os.exists(d / "score.json", followLinks = false)).toVector
    for d <- found.sortWith((a, b) => Py.codePointLess(a.last + "/score.json", b.last + "/score.json")) do
      Py.load(d / "score.json") match
        case Left(e) => unreadable += s"${d.last}: $e"
        case Right(v) =>
          val r = asDict(v)
          if Py.eq(r.get("rep"), PInt(SmokeRep)) then smoke += Py.str(Py.or(r.get("run_id"), PStr(d.last)))
          else runs += r
    (runs.result(), smoke.result(), unreadable.result())

  def counted(r: PDict): Boolean = r.get("error") == PNone && !Py.truthy(r.get("contaminated"))

  /** Final review I4: a transcript-less run's score.json has `contaminated: null`, nothing was checked. GA's always has a
    * bool, so GA-shaped roots have no unchecked run and their report text is unchanged.
    */
  def unchecked(r: PDict): Boolean = r.kv.exists((k, v) => k == "contaminated" && v == PNone)
  private val UncheckedKeys = Set("contaminated", "contamination", "gate_blocks", "env_leaks")

  private def gateTotal(r: PDict): Py = Py.sum(items(Py.or(r.get("gate_blocks"), PDict(Vector()))).map(_._2))

  /** Python `str.splitlines()` separators. */
  private val LineBreaks = "\r\n|[\n\r\u000b\u000c\u001c\u001d\u001e\u0085  ]".r

  /** Python `str.strip()`: Unicode whitespace. */
  private def pyStrip(s: String): String =
    def ws(c: Char) = Character.isWhitespace(c) || Character.isSpaceChar(c)
    s.dropWhile(ws).reverse.dropWhile(ws).reverse

  /** `<root>/audit-cleared.txt` -> set of run ids, or None when the file does not exist. */
  def loadAuditCleared(root: os.Path): Option[Set[String]] =
    val p = root / "audit-cleared.txt"
    if !os.exists(p) then None
    else
      val lines = LineBreaks.split(os.read(p)).toList
      Some(lines.map(pyStrip).filter(l => l.nonEmpty && !l.startsWith("#")).toSet)

  final case class ArmStats(
      n: Int, solved: Int, solveRate: Double, ci: (Double, Double), falseDone: Int, tampering: Int, buildEditRuns: Int,
      timeouts: Int, contaminated: Int, errors: Int, medianWall: Option[Double], medianTurns: Option[Double], nTurns: Int,
      medianCost: Option[Double], nCost: Int, totalCost: Double, unchecked: Int = 0
  )

  private def med(vals: Seq[Py]): Option[Double] = Stats.median(vals.filter(_ != PNone).map(Py.numOrFail(_, "<")))

  def armStats(rs: Seq[PDict], isCounted: PDict => Boolean = counted): ArmStats =
    val c = rs.filter(isCounted)
    val n = c.length
    def cnt(rows: Seq[PDict], k: String) = rows.count(r => Py.truthy(r.get(k)))
    val solved = cnt(c, "solved")
    ArmStats(
      n = n, solved = solved, solveRate = if n != 0 then solved.toDouble / n else 0.0, ci = Stats.wilson(solved, n),
      falseDone = cnt(c, "false_done"),
      tampering = cnt(c, "test_tampering"),
      buildEditRuns = cnt(c, "build_edits"),
      timeouts = cnt(c, "timed_out"),
      contaminated = cnt(rs, "contaminated"),
      errors = rs.count(_.get("error") != PNone),
      medianWall = med(c.map(_.get("wall_seconds"))),
      medianTurns = med(c.map(_.get("turns"))), nTurns = c.count(_.get("turns") != PNone),
      medianCost = med(c.map(_.get("cost_usd"))), nCost = c.count(_.get("cost_usd") != PNone),
      totalCost = c.map(_.get("cost_usd")).filter(_ != PNone).foldLeft(0.0)(_ + Py.numOrFail(_, "+")),
      unchecked = rs.count(unchecked)
    )

  /** Python csv.writer (excel dialect, QUOTE_MINIMAL, `\r\n`). */
  private def csvRow(fields: Seq[String]): String =
    fields.map { f =>
      if f.exists(c => c == ',' || c == '"' || c == '\r' || c == '\n') then "\"" + f.replace("\"", "\"\"") + "\"" else f
    }.mkString("", ",", "\r\n")

  def writeCsv(runs: Seq[PDict], path: os.Path): Unit =
    val scalar = mutable.ArrayBuffer.empty[String]
    for r <- runs; (k, _) <- r.kv do if !scalar.contains(k) then scalar += k
    val cols = scalar.toList ++ List("gate_blocks_total", "build_edits_count", "counted")
    val out = new StringBuilder(csvRow(cols))
    for r <- runs do
      val row = scalar.toList.map { k =>
        r.get(k) match
          case PNone if unchecked(r) && UncheckedKeys(k) => "unchecked"
          case PNone     => ""
          case PList(xs) => xs.map(Py.str).mkString(";")
          case PDict(kv) => kv.sortWith((a, b) => Py.codePointLess(a._1, b._1)).map((a, b) => s"$a=${Py.str(b)}").mkString(";")
          case v         => Py.str(v)
      }
      out ++= csvRow(row ++ List(if unchecked(r) then "unchecked" else Py.str(gateTotal(r)), Py.len(Py.or(r.get("build_edits"), PList(Vector()))).toString,
        if counted(r) then "yes" else "no"))
    os.write.over(path, out.toString)

  def buildSummary(runs: Vector[PDict], smoke: Vector[String], unreadable: Vector[String], auditCleared: Option[Set[String]],
                   spec: ReportSpec): String =
    val (g, u, t) = (spec.treatment, spec.control, spec.ablation)
    val unknown = runs.map(r => Py.str(r.get("arm"))).distinct.filterNot(Set(g, u, t)).sortWith(Py.codePointLess)
    if unknown.nonEmpty then // gf/gs (feature study, 2026-10-10) have no verdict here; tabulate them by hand from score.json
      throw new IllegalArgumentException(s"report.py summarises arms $g, $u, $t only; found " + unknown.mkString(", "))
    def ofArm(a: String)(r: PDict) = r.get("arm") == PStr(a)
    val arms = List(g, u).map(a => a -> runs.filter(ofArm(a))).toMap
    val st = arms.map((a, rs) => a -> armStats(rs))
    val out = mutable.ArrayBuffer("# Gate A/B results", "")
    out += s"Runs: ${runs.length} scored; ${smoke.length} smoke runs (rep $SmokeRep) skipped: ${orNone(smoke)}."
    if unreadable.nonEmpty then out += "Unreadable score.json skipped: " + unreadable.mkString("; ")
    out ++= List("", "## Per arm (g = gated, u = ungated)", "", "| metric | gated | ungated |", "|---|---|---|")

    def row(label: String, f: ArmStats => String): Unit = out += s"| $label | ${f(st(g))} | ${f(st(u))} |"
    def rate(s: ArmStats) = s"${fixed(s.solveRate, 2)} [${fixed(s.ci._1, 2)}, ${fixed(s.ci._2, 2)}]"
    row("n (headline)", _.n.toString)
    row("solved", _.solved.toString)
    row("solve rate [Wilson 95%]", rate)
    row("false_done (rate)", s => s"${s.falseDone} (${fixed(if s.n != 0 then s.falseDone.toDouble / s.n else 0.0, 2)})")
    row("timeouts (counted, unsolved)", _.timeouts.toString)
    row("tampering runs", _.tampering.toString)
    row("runs with build_edits", _.buildEditRuns.toString)
    row("contaminated (excluded)", _.contaminated.toString)
    val anyUnchecked = runs.exists(unchecked)
    if anyUnchecked then row("contamination unchecked (no transcript)", _.unchecked.toString)
    row("error (excluded)", _.errors.toString)
    row("median wall s", s => fmt(s.medianWall, 0))
    row("median turns (n)", s => s"${fmt(s.medianTurns)} (${s.nTurns})")
    row("median cost USD (n)", s => s"${fmt(s.medianCost, 2)} (${s.nCost})")
    row("total cost USD", s => fixed(s.totalCost, 2))

    val excluded = runs.filterNot(counted)
    out ++= List("", "## Excluded from headline", "")
    if excluded.isEmpty then out += "none"
    for r <- excluded do
      val why = mutable.ArrayBuffer.empty[String]
      if r.get("error") != PNone then why += s"error: ${Py.str(r.get("error"))}"
      if Py.truthy(r.get("contaminated")) then
        why += "contaminated: " + Py.iter(Py.or(r.get("contamination"), PList(Vector()))).map(Py.str).mkString(";")
      out += s"- ${Py.str(r.get("run_id"))} (${why.mkString(" | ")})"

    out ++= List("", "## Regression markers (headline runs; runs per kind)", "", "| kind | gated | ungated |", "|---|---|---|")
    val kinds = mutable.Map.empty[String, mutable.Map[String, Int]]
    for a <- List(g, u); r <- arms(a) if counted(r) do
      val ks = Py.iter(Py.or(r.get("regressions"), PList(Vector()))).map(Py.str)
        .map(x => if !x.startsWith("<") then "test/compile id" else x).distinct
      for k <- ks do
        val e = kinds.getOrElseUpdate(k, mutable.Map(g -> 0, u -> 0))
        e(a) += 1
    for k <- kinds.keys.toList.sortWith(Py.codePointLess) do out += s"| $k | ${kinds(k)(g)} | ${kinds(k)(u)} |"
    if kinds.isEmpty then out += "| none | 0 | 0 |"

    val tasks = Py.sortedSet(runs.filter(counted).map(_.get("task")))
    out ++= List("", "## Per task (headline runs)", "", "| task | gated solved | ungated solved | diff |", "|---|---|---|---|")
    val diffs = mutable.ArrayBuffer.empty[Int]
    for tk <- tasks do
      val c = List(g, u).map(a => a -> arms(a).filter(r => Py.eq(r.get("task"), tk) && counted(r))).toMap
      val sc = c.map((a, rs) => a -> rs.count(r => Py.truthy(r.get("solved"))))
      val d = sc(g) - sc(u)
      diffs += d
      out += s"| ${Py.str(tk)} | ${sc(g)}/${c(g).length} | ${sc(u)}/${c(u).length} | ${plusD(d)} |"
    out ++= List("", s"Sign test over per-task solved-count differences (ties dropped): p = ${fixed(Stats.signTest(diffs.toList), 4)} (n tasks = ${diffs.length})")

    out ++= List("", "## Gate blocks (gated arm, headline runs)", "", "| hook | blocks | runs fired in |", "|---|---|---|")
    val hooks = mutable.Map.empty[String, (Py, Int)]
    for r <- arms(g) if counted(r); (h, c) <- items(Py.or(r.get("gate_blocks"), PDict(Vector()))) do
      val (blocks, fired) = hooks.getOrElse(h, (PInt(0), 0))
      hooks(h) = (Py.sum(List(blocks, c)), fired + (if Py.truthy(c) then 1 else 0))
    for h <- hooks.keys.toList.sortWith(Py.codePointLess) do out += s"| $h | ${Py.toLong(hooks(h)._1)} | ${hooks(h)._2} |"
    if hooks.isEmpty then out += "| none | 0 | 0 |"
    if anyUnchecked then
      out ++= List("", s"Gate blocks unchecked (no transcript): ${arms(g).count(r => counted(r) && unchecked(r))} runs.")

    def figures(s: ArmStats) = ArmFigures(s.solveRate, s.falseDone, s.medianWall, s.n)
    val verdict = if !(st(g).n != 0 && st(u).n != 0) then "inconclusive" else decide(figures(st(g)), figures(st(u)))
    out ++= List("", s"Verdict: $verdict", "", "Rule: " + rule(spec), "",
      s"false_done: gated ${st(g).falseDone}/${st(g).n}, ungated ${st(u).falseDone}/${st(u).n}; timeouts: gated ${st(g).timeouts}, ungated ${st(u).timeouts}", "")

    val tRuns = runs.filter(ofArm(t))
    if tRuns.nonEmpty then
      val tTasks = Py.sortedSet(tRuns.map(_.get("task")))
      val tReps = tRuns.map(_.get("rep"))
      val on = List(g, t, u).map(a => a -> runs.filter(r =>
        ofArm(a)(r) && tTasks.exists(Py.eq(_, r.get("task"))) && tReps.exists(Py.eq(_, r.get("rep"))))).toMap
      out ++= List(s"## Ablation: gate without turn-end ($t)", "", "Tasks: " + tTasks.map(Py.str).mkString(", "), "")

      def table(isCounted: PDict => Boolean): String =
        val ab = on.map((a, rs) => a -> armStats(rs, isCounted))
        out ++= List("| metric | gated | no turn-end | ungated |", "|---|---|---|---|")
        def arow(label: String, f: ArmStats => String): Unit = out += s"| $label | ${f(ab(g))} | ${f(ab(t))} | ${f(ab(u))} |"
        arow("n (headline)", _.n.toString)
        arow("solved", _.solved.toString)
        arow("solve rate [Wilson 95%]", rate)
        arow("false_done", _.falseDone.toString)
        arow("timeouts (counted, unsolved)", _.timeouts.toString)
        arow("contaminated (excluded)", _.contaminated.toString)
        if anyUnchecked then arow("contamination unchecked (no transcript)", _.unchecked.toString)
        arow("error (excluded)", _.errors.toString)
        arow("median wall s", s => fmt(s.medianWall, 0))
        arow("median cost USD", s => fmt(s.medianCost, 2))
        ablationVerdict(ab(t).solved, ab(t).n)

      out ++= List("Strict (contamination flags excluded):", "")
      val v = table(counted)
      out ++= List("", s"Ablation verdict (strict): $v", "")
      auditCleared match
        case None => out ++= List("Audited: no audit-cleared.txt; the strict verdict stands.", "")
        case Some(ac) =>
          def isCleared(r: PDict) = r.get("run_id") match
            case PStr(id) => ac.contains(id)
            case _        => false
          val cleared = (on(t) ++ on(g) ++ on(u)).filter(r => isCleared(r) && Py.truthy(r.get("contaminated")))
            .map(r => Py.str(r.get("run_id"))).sortWith(Py.codePointLess)
          out ++= List(s"Audited (cleared: ${orNone(cleared)}):", "")
          val va = table(r => r.get("error") == PNone && (!Py.truthy(r.get("contaminated")) || isCleared(r)))
          out ++= List("", s"Ablation verdict (audited): $va", "")
      out ++= List("Ablation rule: " + ablationRule(spec), "")
    out.mkString("\n")

  /** GA `main`: writes `<root>/results.csv` and `<root>/summary.md`; returns the summary text. */
  def main(root: os.Path, spec: ReportSpec): IO[String] = IO.blocking {
    val (runs, smoke, unreadable) = loadRuns(root)
    writeCsv(runs, root / "results.csv")
    os.write.over(root / "summary.md", "") // GA opens summary.md ("w") before build_summary can raise
    val text = buildSummary(runs, smoke, unreadable, loadAuditCleared(root), spec)
    os.write.over(root / "summary.md", text)
    text
  }
