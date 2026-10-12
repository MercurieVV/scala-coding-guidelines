package harness.run

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import scala.util.Try

object Lock:
  /** Records each dir's mode (`<octal> <path>` per line), chmods it 000, restores on release (success, error, cancel)
    * and when a chmod fails partway (GA: trap EXIT). */
  def locked(dirs: List[os.Path], record: os.Path): Resource[IO, Unit] =
    Resource.make(IO.blocking(writeRecord(dirs, record)))(_ => IO.blocking(restore(record))) *>
      Resource.eval(IO.blocking(dirs.foreach(d => os.perms.set(d, 0))))

  private def writeRecord(dirs: List[os.Path], record: os.Path): Unit =
    val lines = dirs.map(d => s"${(os.perms(d).toInt() & 0xfff).toOctalString} $d")
    os.write.over(record, lines.map(_ + "\n").mkString, createFolders = true)

  // best effort per entry: a dir whose chmod never happened (and cannot be changed) must not stop the others
  private def restore(record: os.Path): Unit =
    if os.exists(record) then
      os.read.lines(record).filter(_.nonEmpty).foreach { line =>
        val i = line.indexOf(' ')
        val p = os.Path(line.substring(i + 1))
        Try(if os.exists(p) then os.perms.set(p, Integer.parseInt(line.substring(0, i), 8)))
      }
      os.remove(record)

  /** GA ga_unlock_all: chmod 755 base, head and every dir under runs. */
  def unlockAll(root: os.Path): IO[Unit] = IO.blocking {
    val runs = root / "runs"
    val runDirs = if os.isDir(runs) then os.list(runs).toList else Nil
    (List(root / "base", root / "head") ++ runDirs).filter(os.isDir(_)).foreach(os.perms.set(_, "rwxr-xr-x"))
  }
