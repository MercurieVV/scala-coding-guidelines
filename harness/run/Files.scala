package harness.run

import cats.effect.IO

object Files:
  def sha256(p: os.Path): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(os.read.bytes(p)).map("%02x".format(_)).mkString

  private def must(what: String, cmd: Seq[String], cwd: os.Path): IO[Unit] =
    Proc.capture(cmd, cwd).flatMap {
      case (0, _)       => IO.unit
      case (rc, out)    => IO.raiseError(new RuntimeException(s"$what failed (rc=$rc): ${out.trim}"))
    }

  def tarGz(src: os.Path, entry: String, dest: os.Path, exclude: Seq[String]): IO[Unit] =
    must("tar", Seq("tar", "-czf", dest.toString) ++ exclude.flatMap(e => Seq("--exclude", e)) ++ Seq("-C", src.toString, entry), src)

  def untarGz(archive: os.Path, dest: os.Path): IO[Unit] =
    IO.blocking(os.makeDir.all(dest)) *> must("untar", Seq("tar", "-xzf", archive.toString, "-C", dest.toString), dest)

  /** Copies src into dest, preserving symlinks; entries directly under src for which skipTopLevel is true are left out. */
  def copyTree(src: os.Path, dest: os.Path, skipTopLevel: String => Boolean): Unit =
    os.makeDir.all(dest)
    os.list(src).filterNot(c => skipTopLevel(c.last)).foreach(c => os.copy(c, dest / c.last, followLinks = false, copyAttributes = true))

  def gitInitCommit(dir: os.Path, message: String): IO[Unit] =
    must("git init", Seq("git", "init", "-q"), dir) *>
      must("git add", Seq("git", "add", "-A"), dir) *>
      must("git commit", Seq("git", "-c", "user.name=gate-eval", "-c", "user.email=gate-eval@local", "-c", "commit.gpgsign=false", "commit", "-qm", message), dir)

  def gitArchive(repo: os.Path, commit: String, dest: os.Path): IO[Unit] =
    IO.blocking { os.makeDir.all(dest); os.temp(suffix = ".tar") }.flatMap { tmp =>
      (must("git archive", Seq("git", "-C", repo.toString, "archive", "-o", tmp.toString, commit), repo) *>
        must("tar -x", Seq("tar", "-xf", tmp.toString, "-C", dest.toString), dest))
        .guarantee(IO.blocking(os.remove(tmp)))
    }
