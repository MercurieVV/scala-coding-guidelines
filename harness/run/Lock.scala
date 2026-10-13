package harness.run

import cats.effect.{IO, Resource}
import cats.effect.std.Console
import cats.syntax.all.*
import scala.util.Try

object Lock:
  /** Records each dir's mode (`<octal> <path>` per line), chmods it 000, restores on release (success, error, cancel)
    * and when a chmod fails partway (GA: trap EXIT). Every dir is tried; each one that cannot be restored is reported
    * through `warn` (default: a stderr line), never silently left at 000. */
  def locked(dirs: List[os.Path], record: os.Path, warn: String => IO[Unit] = Console[IO].errorln(_)): Resource[IO, Unit] =
    Resource.make(IO.blocking(writeRecord(dirs, record)))(_ => IO.blocking(restore(record)).flatMap(_.traverse_(warn))) *>
      Resource.eval(IO.blocking(dirs.foreach(d => os.perms.set(d, 0))))

  private def writeRecord(dirs: List[os.Path], record: os.Path): Unit =
    val lines = dirs.map(d => s"${(os.perms(d).toInt() & 0xfff).toOctalString} $d")
    os.write.over(record, lines.map(_ + "\n").mkString, createFolders = true)

  // best effort per entry: a dir whose chmod never happened (and cannot be changed) must not stop the others;
  // returns one message per dir left unrestored
  private def restore(record: os.Path): List[String] =
    if !os.exists(record) then Nil
    else
      val failures = os.read.lines(record).filter(_.nonEmpty).toList.flatMap { line =>
        val i = line.indexOf(' ')
        val p = line.substring(i + 1)
        val mode = line.substring(0, i)
        Try { val d = os.Path(p); if os.exists(d) then os.perms.set(d, Integer.parseInt(mode, 8)) }.failed.toOption
          .map(e => s"gate-ab: lock restore failed for $p (mode $mode): ${e.getMessage}; run `harness unlock`")
      }
      os.remove(record)
      failures

  /** GA ga_unlock_all: chmod 755 base, head and every dir under runs. */
  def unlockAll(root: os.Path): IO[Unit] = IO.blocking {
    val runs = root / "runs"
    val runDirs = if os.isDir(runs) then os.list(runs).toList else Nil
    (List(root / "base", root / "head") ++ runDirs).filter(os.isDir(_)).foreach(os.perms.set(_, "rwxr-xr-x"))
  }
