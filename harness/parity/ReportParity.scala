package harness.parity

import cats.effect.IO
import cats.syntax.all.*
import harness.config.Experiment
import harness.core.{ChainReport, Report}
import harness.run.{Files, HarnessError}

/** Parity spec §7.3: GA report.py and chain_report.py vs [[Report]] and [[ChainReport]] on two copies of each copied
  * root; every written file and chain_report's stdout must be equal as text.
  */
object ReportParity:
  /** A root gets the chain report when it has the reference erosion or any chain.json. */
  def isChainRoot(root: os.Path): Boolean =
    os.isFile(root / "chain-reference-erosion.json") ||
      (os.isDir(root / "runs") && os.list(root / "runs").exists(d => os.isFile(d / "chain.json")))

  private def text(p: os.Path): Option[String] = Option.when(os.isFile(p))(os.read(p))

  private def same(what: String, a: Option[String], b: Option[String]): List[String] =
    if a == b then Nil
    else
      val (la, lb) = (a.getOrElse("").linesIterator.toList, b.getOrElse("").linesIterator.toList)
      val i = la.zipAll(lb, "(none)", "(none)").indexWhere((x, y) => x != y)
      val at = if a.isEmpty || b.isEmpty then s"GA ${a.fold("no file")(_ => "a file")}, harness ${b.fold("no file")(_ => "a file")}"
        else if i < 0 then "trailing newline" else s"line ${i + 1}: GA `${la.lift(i).getOrElse("(none)")}`, harness `${lb.lift(i).getOrElse("(none)")}`"
      List(s"$what differs ($at)")

  /** stdout, or the exception message of a failed GA script (its traceback's last line, exception type dropped). */
  private def python(args: String*): IO[Either[String, String]] = IO.blocking {
    val r = os.proc(Seq("python3") ++ args).call(check = false, stderr = os.Pipe)
    if r.exitCode == 0 then Right(r.out.text())
    else Left(r.err.text().linesIterator.filter(_.trim.nonEmpty).toList.lastOption.getOrElse("").replaceFirst("^\\w+: ", ""))
  }

  private def harness[A](io: IO[A]): IO[Either[String, A]] =
    io.attempt.map(_.left.map(e => Option(e.getMessage).getOrElse(e.toString)))

  /** Both sides fail with the same message, or both succeed. */
  private def outcome(what: String, ga: Either[String, ?], mine: Either[String, ?]): List[String] =
    (ga, mine) match
      case (Left(a), Left(b)) => if a == b then Nil else List(s"$what: GA failed `$a`, harness failed `$b`")
      case (Left(a), Right(_)) => List(s"$what: GA failed `$a`, harness succeeded")
      case (Right(_), Left(b)) => List(s"$what: harness failed `$b`, GA succeeded")
      case _ => Nil

  /** -> (reports compared, mismatches). */
  def apply(exp: Experiment, ga: os.Path, roots: List[os.Path], work: os.Path, say: String => IO[Unit]): IO[(Int, List[String])] =
    roots.traverse { root =>
      val (a, b) = (work / root.last / "ga", work / root.last / "harness")
      val chain = isChainRoot(root) && exp.chain.nonEmpty
      for
        _ <- if os.isDir(root / "runs") then IO.unit else IO.raiseError(HarnessError(s"parity-reports: no runs/ in $root"))
        _ <- IO.blocking {
          os.remove.all(work / root.last)
          List(a, b).foreach { d =>
            Files.copyTree(root, d, _ => false)
            List(d / "runs" / "results.csv", d / "runs" / "summary.md", d / "chain-report.md").foreach(os.remove(_))
          }
        }
        gr <- python((ga / "report.py").toString, (a / "runs").toString)
        hr <- harness(Report.main(b / "runs", exp.report))
        rep = outcome(s"${root.last}: report", gr, hr) ++ same(s"${root.last}: results.csv", text(a / "runs" / "results.csv"), text(b / "runs" / "results.csv")) ++
          same(s"${root.last}: summary.md", text(a / "runs" / "summary.md"), text(b / "runs" / "summary.md"))
        ch <- if !chain then IO.pure(Nil)
          else
            for
              out <- python(Seq((ga / "chain_report.py").toString, a.toString) ++ exp.chain*)
              mine <- harness(ChainReport.main(b, exp.chain, exp.chainReport))
            yield outcome(s"${root.last}: chain report", out, mine) ++
              same(s"${root.last}: chain_report stdout", out.toOption, mine.toOption) ++
              same(s"${root.last}: chain-report.md", text(a / "chain-report.md"), text(b / "chain-report.md"))
        n = if chain then 6 else 3
        failed = gr.left.toOption.fold("")(m => s"GA report.py failed: $m")
        _ <- say(s"${root.last}: $n outputs compared, ${if rep.isEmpty && ch.isEmpty then "equal" else s"${(rep ++ ch).size} differ"}" +
          (if failed.isEmpty then "" else s" ($failed)"))
      yield (n, rep ++ ch)
    }.map(rs => (rs.map(_._1).sum, rs.flatMap(_._2)))
