package harness.parity

import cats.effect.IO
import cats.syntax.all.*
import harness.config.{Experiment, TaskSpec}
import harness.core.Erosion
import harness.run.{Files, HarnessError, Proc}

/** The chain's reference erosion line (GA prepare-chain.sh): HEAD (the target at its commit, `git archive`d, as GA
  * warm-cache.sh builds it) plus the chain's reference patches 1..k applied with `git apply -3` in order; [[Erosion.measure]]
  * after each step against GA's recorded `chain-reference-erosion.json` (task, methods, total_mass, erosion).
  */
object ErosionParity:
  private val Id = Seq("-c", "user.name=gate-eval", "-c", "user.email=gate-eval@local", "-c", "commit.gpgsign=false")

  private def git(dir: os.Path, args: String*): IO[Unit] =
    Proc.capture(Seq("git") ++ args, dir).flatMap {
      case (0, _)   => IO.unit
      case (_, out) => IO.raiseError(HarnessError(s"parity-erosion: git ${args.mkString(" ")} failed: ${out.trim}"))
    }

  /** -> (steps compared, mismatches). `recorded`: GA's chain-reference-erosion.json. */
  def apply(exp: Experiment, work: os.Path, recorded: os.Path, say: String => IO[Unit]): IO[(Int, List[String])] =
    val (head, cv) = (work / "head", work / "chain-validate")
    for
      want <- IO.blocking(ujson.read(os.read(recorded)).arr.toList.map(_.obj))
      _ <- IO.blocking(os.remove.all(work))
      _ <- Files.gitArchive(exp.target.repo, exp.target.commit, head)
      _ <- Files.gitInitCommit(head, "head")
      _ <- IO.blocking(Files.copyTree(head, cv, _ => false))
      // GA: a copied index carries the source tree's stat data; git apply -3 would see every file as changed
      _ <- Proc.capture(Seq("git", "update-index", "-q", "--refresh"), cv).void
      rows <- exp.chain.zipWithIndex.traverse { (task, i) =>
        for
          ts <- TaskSpec.load(exp.tasksDir, task).fold(m => IO.raiseError(HarnessError(m)), IO.pure)
          patch <- ts.referencePatch.fold(IO.raiseError(HarnessError(s"task $task has no reference patch")))(p => IO.pure(exp.tasksDir / task / p))
          _ <- git(cv, "apply", "-3", patch.toString)
          _ <- git(cv, "add", "-A")
          _ <- git(cv, (Id ++ Seq("commit", "-qm", task))*)
          fig <- Erosion.measure(cv, head.toString).flatMap(_.fold(m => IO.raiseError(HarnessError(m)), IO.pure))
          got = ujson.Obj("task" -> task, "methods" -> fig.methods, "total_mass" -> fig.mass, "erosion" -> fig.erosion)
          ms = want.lift(i).fold(List(s"step ${i + 1} $task: not in $recorded")) { w =>
            StaticParity.diff(s"step ${i + 1}", ujson.Obj.from(w), got)
          }
          _ <- say(s"step ${i + 1} $task: erosion ${fig.erosion}, methods ${fig.methods}, mass ${fig.mass}" +
            (if ms.isEmpty then " (equal)" else s" (${ms.size} differ)"))
        yield ms
      }
    yield (exp.chain.size, rows.flatten ++ Option.when(want.size != exp.chain.size)(s"$recorded has ${want.size} steps, chain ${exp.chain.size}"))
