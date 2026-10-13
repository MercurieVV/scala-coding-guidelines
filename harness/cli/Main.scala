package harness.cli

import cats.data.Validated
import cats.effect.std.Console
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import com.monovore.decline.{Command, Help, Opts}
import harness.agent.{Agent, DshProfile}
import harness.config.{Experiment, Load}
import harness.core.{ChainReport, PNone, Py, Report}
import harness.parity.{ArmParity, ErosionParity, ReportParity, StaticParity}
import harness.run.{Campaign, CampaignOps, ChainCheck, HarnessError, Lock, Prepare, Run, Score}
import harness.target.{ScoreError, Target}
import scala.util.control.NonFatal

/** The packaged launcher's entry point. Not a decline `CommandIOApp`: its `run` is final and exits 1 on a usage
  * error, the harness exits 2 (and prints `--help` to stdout).
  */
object Main extends IOApp:
  /** The packaged launcher runs on whatever `java` JAVA_HOME / PATH give; Json.pyFloat needs JDK 19+ repr semantics. */
  val MinJava = 21

  def run(args: List[String]): IO[ExitCode] =
    val v = Runtime.version()
    if v.feature < MinJava then Term.std.err(s"gate-ab: needs Java $MinJava+, running $v").as(ExitCode.Error)
    else Cli.run(args, Real(_, Term.std), Term.std)

/** `out` prints as is (no newline added); `err` prints one line. */
final case class Term(out: String => IO[Unit], err: String => IO[Unit]):
  def line(s: String): IO[Unit] = out(s + "\n")

object Term:
  val std: Term = Term(Console[IO].print(_), Console[IO].errorln(_))

/** Everything a subcommand drives besides loading and validating the experiment; [[Real]] wires the harness, tests
  * pass fakes.
  */
trait Commands extends CampaignOps:
  def warmHead: IO[Unit]
  def baselineCli: IO[Int]
  def campaign(smoke: Boolean): IO[Unit]
  def report: IO[Unit]
  def chainReport: IO[String]
  def unlock: IO[Unit]

/** The real wiring: GA's scripts, one method each. */
final class Real(exp: Experiment, term: Term) extends Commands:
  private def target: IO[Target] = Target.of(exp.target).fold(m => IO.raiseError(HarnessError(m)), IO.pure)
  private def baselineLine(m: String, ok: Boolean) = f"$m%-28s ${if ok then "pass" else "FAIL"}"

  def run(task: String, arm: String, rep: Int, seed: Option[os.Path]): IO[Unit] = Run(exp, task, arm, rep, seed)
  def score(runDir: os.Path): IO[Unit] = Score(exp, runDir).void
  def prepare(task: String): IO[Boolean] = Prepare.task(exp, task)
  // campaign's `score.py --baseline`: the module lines; a ScoreError reaches the campaign as GA's
  // `score.py --baseline: <msg>` line (score.py:913), then campaign dies `HEAD baseline failed`
  def baseline: IO[Unit] =
    Score.baseline(exp)
      .adaptError { case e: ScoreError => HarnessError(e.getMessage, "score.py --baseline") }
      .flatMap(_.toList.sortBy(_._1).traverse_((m, ok) => term.line(baselineLine(m, ok))))
  def prepareChain(steps: List[String]): IO[Boolean] = Prepare.chain(exp, steps)
  def chainCheck(runDir: os.Path, earlier: List[os.Path]): IO[Unit] =
    ChainCheck(exp, runDir, earlier) *>
      IO.blocking(Py.load(runDir / "chain.json")).flatMap(_.fold(m => IO.raiseError(HarnessError(m)), p => term.line(Cli.chainLine(p))))
  def warmHead: IO[Unit] = Prepare.head(exp)
  def baselineCli: IO[Int] = target.flatMap(Score.baselineCli(exp, _, term.line, term.err))
  def campaign(smoke: Boolean): IO[Unit] = Campaign(exp, smoke, this)
  def report: IO[Unit] = Report.main(exp.root / "runs", exp.report).void
  def chainReport: IO[String] = ChainReport.main(exp.root, exp.chain, exp.chainReport)
  def unlock: IO[Unit] = Lock.unlockAll(exp.root)

enum Cmd:
  case Prepare(task: String)
  case WarmHead
  case Baseline
  case Run(task: String, arm: String, rep: String, seed: Option[String])
  case Score(runDirs: List[String]) // `score <run-dir>` and `rescore <run-dir>…`
  case Campaign(smoke: Boolean)
  case ChainCheck(runDir: String, earlier: List[String])
  case PrepareChain
  case Report
  case ChainReport
  case Validate
  case Unlock
  case ParityStatic(roots: List[String], originals: Option[String], ga: Option[String])
  case ParityArms(work: String, task: String, baseTree: String, snapshot: String, ga: Option[String])
  case ParityReports(roots: List[String], work: String, ga: Option[String])
  case ParityErosion(work: String, recorded: String)

final case class Invocation(experiment: String, overrides: Map[String, String], cmd: Cmd)

object Cli:
  val Usage: ExitCode = ExitCode(2)

  // ---------------------------------------------------------------- parsing

  private val experiment = Opts.option[String]("experiment", "Experiment HOCON file.", metavar = "file")
  private val overrides: Opts[Map[String, String]] =
    Opts.options[String]("set", "Override a config value (a path under harness unless it starts with target., agents. or arms.).",
      metavar = "path=value").orEmpty.mapValidated { xs =>
      xs.traverse { s =>
        s.indexOf('=') match
          case i if i > 0 => Validated.validNel(s.take(i).trim -> s.drop(i + 1).trim)
          case _          => Validated.invalidNel(s"--set wants path=value, got '$s'")
      }.map(_.toMap)
    }

  private def sub(name: String, help: String)(cmd: Opts[Cmd]): Opts[Invocation] =
    Opts.subcommand(name, help)((experiment, overrides, cmd).mapN(Invocation.apply))

  private val runDirs = Opts.arguments[String]("run-dir").map(_.toList)
  private val gaDir = Opts.option[String]("ga", "GA gate-ab dir (default <template>/eval/gate-ab; retired, check out scala-llm-template 98f5652 and pass its eval/gate-ab).", metavar = "dir").orNone

  private val commands: Opts[Invocation] = List(
    sub("prepare", "Build <root>/base/<task> (cut tree, hidden spec, validation.txt); exit 1 unless every line is yes.")(
      Opts.argument[String]("task").map(Cmd.Prepare(_))),
    sub("warm-head", "Build and compile the HEAD reference tree <root>/head.")(Opts(Cmd.WarmHead)),
    sub("baseline", "Run every test module at HEAD, cache head-baseline.json.")(Opts(Cmd.Baseline)),
    sub("run", "One run: <root>/runs/<task>-<arm>-<rep>.")(
      (Opts.argument[String]("task"), Opts.argument[String]("arm"), Opts.argument[String]("rep"),
        Opts.option[String]("seed", "Start from a final-tree.tar.gz instead of the task's base tree.", metavar = "tar.gz").orNone)
        .mapN(Cmd.Run(_, _, _, _))),
    sub("score", "Score one run dir (writes score.json).")(Opts.argument[String]("run-dir").map(d => Cmd.Score(List(d)))),
    sub("rescore", "Score run dirs again (score only, e.g. on copies).")(runDirs.map(Cmd.Score(_))),
    sub("campaign", "All runs in order, resumable.")(
      Opts.flag("smoke", "Only the smoke task, treatment then control, at the smoke rep.").orFalse.map(Cmd.Campaign(_))),
    sub("chain-check", "Chain regressions and erosion of a chained run (writes chain.json).")(
      (Opts.argument[String]("run-dir"), Opts.arguments[String]("earlier-run-dir").orEmpty).mapN(Cmd.ChainCheck(_, _))),
    sub("prepare-chain", "Validate the experiment's chain (chain-validation.txt, reference erosion).")(Opts(Cmd.PrepareChain)),
    sub("report", "Write <root>/runs/results.csv and summary.md.")(Opts(Cmd.Report)),
    sub("chain-report", "Print the chained-feature-work table.")(Opts(Cmd.ChainReport)),
    sub("validate", "Load the experiment and print its arms, tasks and agents.")(Opts(Cmd.Validate)),
    sub("unlock", "Make base/, head/ and every run dir readable again.")(Opts(Cmd.Unlock)),
    sub("parity-static", "GA score.py pure functions vs the harness core on copied run roots.")(
      (Opts.arguments[String]("old-root").map(_.toList),
        Opts.option[String]("originals", "Where the copied roots live originally (default $HOME).", metavar = "dir").orNone,
        gaDir).mapN(Cmd.ParityStatic(_, _, _))),
    sub("parity-arms", "GA run.sh setup vs Run.setupOnly for every GA arm, byte for byte.")(
      (Opts.option[String]("work", "Scratch dir (wiped).", metavar = "dir"),
        Opts.option[String]("task", "Task whose base tree is given.", metavar = "task"),
        Opts.option[String]("base-tree", "Copy of <root>/base/<task>/tree.", metavar = "dir"),
        Opts.option[String]("snapshot", "Copy of the gated settings snapshot.", metavar = "file"),
        gaDir).mapN(Cmd.ParityArms(_, _, _, _, _))),
    sub("parity-reports", "GA report.py / chain_report.py vs the harness reports on copied run roots.")(
      (Opts.arguments[String]("old-root").map(_.toList), Opts.option[String]("work", "Scratch dir.", metavar = "dir"),
        gaDir).mapN(Cmd.ParityReports(_, _, _))),
    sub("parity-erosion", "The chain's reference erosion line (HEAD + reference patches) vs GA's chain-reference-erosion.json.")(
      (Opts.option[String]("work", "Scratch dir (wiped).", metavar = "dir"),
        Opts.argument[String]("chain-reference-erosion.json")).mapN(Cmd.ParityErosion(_, _)))
  ).reduce(_ orElse _)

  /** The one subcommand without an experiment: writes `<dsh-home>/profiles/<p>-headless`. */
  private val dshProfile: Opts[String] =
    Opts.subcommand("dsh-profile", "Derive the headless dsh profile <p>-headless from <p> (writes under the dsh home).")(
      Opts.option[String]("from", "Source dsh profile.", metavar = "profile"))

  val command: Command[Either[String, Invocation]] =
    Command("harness", "agent eval harness")(commands.map(Right(_)) orElse dshProfile.map(Left(_)))

  // ---------------------------------------------------------------- dispatch

  def run(args: List[String], backend: Experiment => Commands, term: Term, dshHome: => os.Path = DshProfile.defaultHome): IO[ExitCode] =
    command.parse(args, sys.env) match
      case Left(help) => usage(help, term)
      case Right(cmd) =>
        val io = cmd match
          case Right(inv)  => execute(inv, backend, term)
          case Left(from) =>
            DshProfile.derive(dshHome, from).flatMap(dir =>
              term.line(s"wrote $dir") *> term.line(s"install its packages: ${DshProfile.installCommand(from)}")).as(ExitCode.Success)
        io.handleErrorWith {
          case h: HarnessError => term.err(h.line).as(ExitCode.Error)
          case NonFatal(e)     => term.err(s"gate-ab: ${Option(e.getMessage).getOrElse(e.toString)}").as(ExitCode.Error)
          case e               => IO.raiseError(e)
        }

  private def usage(help: Help, term: Term): IO[ExitCode] =
    if help.errors.isEmpty then term.line(help.toString).as(ExitCode.Success)
    else term.err(help.toString).as(Usage)

  private def die[A](msg: String): IO[A] = IO.raiseError(HarnessError(msg))
  private def path(s: String): os.Path = os.Path(s, os.pwd)
  private def exitIf(ok: Boolean): ExitCode = if ok then ExitCode.Success else ExitCode.Error
  private def nonEmptyChain(exp: Experiment): IO[Unit] = if exp.chain.isEmpty then die("harness.chain is empty") else IO.unit

  private def execute(inv: Invocation, backend: Experiment => Commands, term: Term): IO[ExitCode] =
    val file = path(inv.experiment)
    for
      _ <- IO.blocking(os.isFile(file)).ifM(IO.unit, IO.raiseError(HarnessError(s"experiment file $file not found", "harness")))
      exp <- IO.blocking(Load.experiment(file, inv.overrides)).flatMap(_.fold(die, IO.pure))
      _ <- adapters(exp).fold(die, IO.pure)
      cmds = backend(exp)
      code <- inv.cmd match
        case Cmd.Validate => validate(exp, inv.experiment, term).as(ExitCode.Success)
        case Cmd.Prepare(task) => cmds.prepare(task).map(exitIf)
        case Cmd.WarmHead => cmds.warmHead.as(ExitCode.Success)
        case Cmd.Baseline => cmds.baselineCli.map(ExitCode(_))
        case Cmd.Run(task, arm, rep, seed) =>
          val r = rep.toIntOption.getOrElse(-1)
          Run.runId(task, arm, r, exp.armSpecs.keySet)
            .left.map(m => if m.startsWith("rep must") then s"rep must be 1..9, got $rep" else m)
            .fold(die, _ => cmds.run(task, arm, r, seed.map(path)).as(ExitCode.Success))
        case Cmd.Score(dirs) => dirs.traverse_(d => cmds.score(path(d))).as(ExitCode.Success)
        case Cmd.Campaign(smoke) => cmds.campaign(smoke).as(ExitCode.Success)
        case Cmd.ChainCheck(rd, earlier) => cmds.chainCheck(path(rd), earlier.map(path)).as(ExitCode.Success)
        case Cmd.PrepareChain => nonEmptyChain(exp) *> cmds.prepareChain(exp.chain).map(exitIf)
        case Cmd.Report => cmds.report.as(ExitCode.Success)
        case Cmd.ChainReport => nonEmptyChain(exp) *> cmds.chainReport.flatMap(term.out).as(ExitCode.Success)
        case Cmd.Unlock => cmds.unlock.as(ExitCode.Success)
        case p: (Cmd.ParityStatic | Cmd.ParityArms | Cmd.ParityReports | Cmd.ParityErosion) => parity(exp, p, term)
    yield code

  /** Spec §6 "validated at load" (final review I3): the target and every configured agent resolve, each adapter's
    * required knobs present, before any command does work.
    */
  private def adapters(exp: Experiment): Either[String, Unit] =
    Target.of(exp.target).flatMap(_ => exp.agents.values.toList.sortBy(_.name).traverse_(Agent.of(_).void))

  private def parity(exp: Experiment, cmd: Cmd, term: Term): IO[ExitCode] =
    def ga(o: Option[String]) = o.fold(exp.template / "eval" / "gate-ab")(path)
    def report(summary: String, ms: List[String]) =
      term.line(summary) *> ms.traverse_(m => term.line("  " + m)).as(exitIf(ms.isEmpty))
    cmd match
      case Cmd.ParityStatic(roots, originals, g) =>
        val orig = originals.fold(os.Path(sys.env.getOrElse("HOME", os.home.toString)))(path)
        StaticParity(exp, ga(g), roots.map(path), orig, term.line).flatMap(r => report(r.summary, r.mismatches))
      case Cmd.ParityArms(work, task, tree, snap, g) =>
        ArmParity(exp, ga(g), path(work), task, path(tree), path(snap), term.line)
          .flatMap((n, files, ms) => report(s"$n arms, $files files, ${ms.size} mismatches", ms))
      case Cmd.ParityReports(roots, work, g) =>
        ReportParity(exp, ga(g), roots.map(path), path(work), term.line)
          .flatMap((n, ms) => report(s"$n outputs, ${ms.size} differ", ms))
      case Cmd.ParityErosion(work, recorded) =>
        nonEmptyChain(exp) *> ErosionParity(exp, path(work), path(recorded), term.line)
          .flatMap((n, ms) => report(s"$n steps, ${ms.size} mismatches", ms))
      case _ => die("not a parity command")

  private def list(xs: Iterable[String]): String = if xs.isEmpty then "(none)" else xs.mkString(", ")
  private def yn(b: Boolean): String = if b then "yes" else "no"

  /** Loads and resolves every adapter (both done by the caller), prints what the experiment would run. */
  private def validate(exp: Experiment, file: String, term: Term): IO[Unit] =
    val t = exp.target
    val arms = exp.armSpecs.values.toList.sortBy(_.name)
    val lines = List(
      s"experiment: ${path(file)}",
      s"root: ${exp.root}",
      s"target: ${t.adapter} ${t.repo} @ ${t.commit}",
      s"tasks dir: ${exp.tasksDir}") ++
      exp.agents.values.toList.sortBy(_.name).map(a => s"agent ${a.name}: adapter ${a.adapter}") ++
      List(s"arms: ${list(arms.map(_.name))}") ++
      arms.map(a => s"arm ${a.name}: agent ${a.agent}, model ${a.model}, out-dir ${a.outDir}, holdout ${yn(a.holdout)}, " +
        s"settings-snapshot ${yn(a.settingsSnapshot)}, ${a.setup.size} setup steps") ++
      List(
        s"campaign arms: ${list(exp.arms)}",
        s"reps: ${list(exp.reps.map(_.toString))}",
        s"tasks: ${list(exp.tasks)}",
        s"chain: ${list(exp.chain)}",
        s"smoke: ${exp.smokeTask} rep ${exp.smokeRep}",
        s"report: treatment ${exp.report.treatment}, control ${exp.report.control}, ablation ${exp.report.ablation}",
        s"chain-report: control ${exp.chainReport.control}, treatment ${exp.chainReport.treatment}")
    lines.traverse_(term.line)

  /** GA chaincheck.py `__main__`: `chain <id>: regressions <n>, erosion <e>[, ERROR <error>]`, Python `%s`. */
  def chainLine(r: Py): String =
    val err = Py.get(r, "error")
    s"chain ${Py.str(Py.get(r, "run_id"))}: regressions ${Py.str(Py.get(r, "chain_regressions"))}, " +
      s"erosion ${Py.str(Py.get(r, "erosion"))}${if err == PNone then "" else ", ERROR " + Py.str(err)}"
