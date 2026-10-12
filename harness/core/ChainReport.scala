package harness.core

import cats.effect.IO
import harness.config.ChainReportSpec
import scala.collection.mutable

/** Port of GA/chain_report.py: the chained-feature-work table and the pre-registered verdicts (spec 2026-10-11 chained
  * feature work §4). Writes `<root>/chain-report.md`. Item 15 needs the transcripts; the report lists the false_done
  * steps. GA's arms u / gs are `spec.control` / `spec.treatment`; with the default spec the text is byte-identical.
  */
object ChainReport:
  def stepFailures(score: Py, chain: Py): Option[Long] =
    def regressionCountUnknown =
      val rc = score match
        case d: PDict if !d.contains("regression_count") => PInt(-1)
        case _                                           => Py.get(score, "regression_count")
      Py.numOrFail(rc, "<") < 0
    if chain == PNone || Py.get(chain, "error") != PNone || Py.get(chain, "chain_regressions") == PNone || regressionCountUnknown
    then None
    else
      Some((if Py.truthy(Py.get(score, "solved")) then 0L else 1L) + Py.toLong(Py.get(score, "regression_count")) +
        Py.toLong(Py.get(chain, "chain_regressions")))

  def gateVerdict(failControl: Long, failTreatment: Long): String =
    val d = failControl - failTreatment
    if d >= 2 then "gate pays on chains" else if d <= 0 then "no gain" else "unclear"

  def gateLine(failControl: Long, failTreatment: Long, unknown: Seq[String], spec: ChainReportSpec): String =
    val label = s"${spec.control} - ${spec.treatment} = ${failControl - failTreatment}"
    if unknown.nonEmpty then s"Gate: incomplete — unscored steps ${unknown.mkString(", ")} ($label so far)"
    else s"Gate: ${gateVerdict(failControl, failTreatment)} ($label)"

  def erosionRises(chains: Seq[Seq[Option[Double]]], refLast: Double): (Int, Int) =
    var counted, rising = 0
    for c <- chains do
      (c.headOption.flatten, c.lastOption.flatten) match
        case (Some(first), Some(last)) =>
          counted += 1
          if last > first && last > refLast then rising += 1
        case _ => ()
    (rising, counted)

  def item16Verdict(rising: Int, @scala.annotation.unused counted: Int): String = if rising >= 3 then "build item 16" else "do not build item 16"

  /** `statistics.median` keeping the Python type: an odd count returns the middle element itself (an int stays an int),
    * an even count the float mean of the two middle ones.
    */
  def median(xs: Seq[Py]): Py =
    val s = xs.sortBy(Py.numOrFail(_, "<"))(using Ordering.Double.TotalOrdering)
    val n = s.length
    if n % 2 == 1 then s(n / 2) else PFloat((Py.numOrFail(s(n / 2 - 1), "+") + Py.numOrFail(s(n / 2), "+")) / 2)

  /** Python `round(x, 3)`: ints unchanged, floats the exact binary value correctly rounded, ties to even. */
  private def round3(v: Py): Py = v match
    case PBool(b)                                => PInt(if b then 1 else 0)
    case PFloat(d) if !d.isNaN && !d.isInfinite =>
      PFloat(new java.math.BigDecimal(d).setScale(3, java.math.RoundingMode.HALF_EVEN).doubleValue)
    case PFloat(_) | PInt(_)                     => v
    case o                                       => throw Py.PyError(s"type ${Py.typeName(o)} doesn't define __round__ method")

  private def load(p: os.Path): Py = Py.load(p).getOrElse(PNone)

  private val Empty = PDict(Vector())

  private def reps(runs: os.Path): List[BigInt] =
    os.list(runs).map(_.last).toList.flatMap { n =>
      val last = n.substring(n.lastIndexOf('-') + 1)
      if last.nonEmpty && last.forall(Character.isDigit) then
        if !n.contains('-') then throw Py.PyError("list index out of range")
        Some(BigInt(last))
      else None
    }.distinct.sorted

  def main(root: os.Path, steps: List[String], spec: ChainReportSpec): IO[String] = IO.blocking {
    val arms = List(spec.control, spec.treatment)
    val lines = mutable.ArrayBuffer("# Chained feature work", "",
      "| arm | chain | step | task | solved | false_done | regressions | chain regressions | erosion | wall s | cost USD |",
      "|---|---|---|---|---|---|---|---|---|---|---|")
    val fails = mutable.Map(arms.map(_ -> 0L)*)
    val unknown, falseDone = mutable.ArrayBuffer.empty[String]
    val eroChains = mutable.ArrayBuffer.empty[Seq[Py]]
    val walls, costs = arms.map(_ -> mutable.ArrayBuffer.empty[Py]).toMap
    val repList = reps(root / "runs")
    for arm <- arms; rep <- repList do
      val chainEro = mutable.ArrayBuffer.empty[Py]
      for (t, k) <- steps.zip(LazyList.from(1)) do
        val rid = s"$t-$arm-$rep"
        val s = Py.or(load(root / "runs" / rid / "score.json"), Empty)
        val c = load(root / "runs" / rid / "chain.json")
        val f = if Py.truthy(s) && Py.get(s, "error") == PNone then stepFailures(s, c) else None
        f match
          case None    => unknown += rid
          case Some(x) => fails(arm) += x
        if Py.truthy(Py.get(s, "false_done")) then falseDone += rid
        val e = Py.get(Py.or(Py.get(Py.or(c, Empty), "erosion"), Empty), "erosion")
        chainEro += e
        val wall = Py.get(s, "wall_seconds")
        if wall != PNone then
          walls(arm) += wall
          costs(arm) += Py.or(Py.get(s, "cost_usd"), PFloat(0.0))
        val cost = Py.get(s, "cost_usd") match
          case PNone => PNone
          case x     => round3(x)
        lines += s"| $arm | $rep | $k | $t | ${Py.str(Py.get(s, "solved"))} | ${Py.str(Py.get(s, "false_done"))} | " +
          s"${Py.str(Py.get(s, "regression_count"))} | ${Py.str(Py.get(Py.or(c, Empty), "chain_regressions"))} | ${Py.str(e)} | " +
          s"${Py.str(wall)} | ${Py.str(cost)} |"
      eroChains += chainEro.toList
    val ref = Py.or(load(root / "chain-reference-erosion.json"), PList(Vector()))
    val (refLen, refLast) = ref match
      case PList(xs) if xs.nonEmpty => (xs.length, xs.last match
          case d: PDict if d.contains("erosion") => d.get("erosion")
          case _: PDict                          => throw Py.PyError("KeyError: 'erosion'")
          case o                                 => throw Py.PyError(s"'${Py.typeName(o)}' object is not subscriptable"))
      case PList(_) => (0, PFloat(0.0))
      case o        => throw Py.PyError(s"'${Py.typeName(o)}' object is not subscriptable by -1")
    val asOpt = (v: Py) => if v == PNone then None else Some(Py.numOrFail(v, ">"))
    val (rising, counted) = erosionRises(eroChains.toList.map(_.map(asOpt)), Py.numOrFail(refLast, ">"))
    def orNone(xs: Seq[String]) = if xs.isEmpty then "none" else xs.mkString(", ")
    lines ++= List("", s"${arms(0)}: failures ${fails(arms(0))}", s"${arms(1)}: failures ${fails(arms(1))}",
      gateLine(fails(arms(0)), fails(arms(1)), unknown.toList, spec),
      s"Item 16: ${item16Verdict(rising, counted)} (erosion rising in $rising of $counted chains; reference step $refLen erosion ${Py.str(refLast)})",
      s"Item 15: read the transcripts of false_done: ${orNone(falseDone.toList)}",
      s"Unscored or errored steps: ${orNone(unknown.toList)}")
    for a <- arms if walls(a).nonEmpty do
      lines += s"$a: median wall ${Py.str(median(walls(a).toList))} s, median cost USD ${Report.fixed(Py.numOrFail(median(costs(a).toList), "%f"), 3)}"
    val text = lines.mkString("\n") + "\n"
    os.write.over(root / "chain-report.md", text)
    text
  }
