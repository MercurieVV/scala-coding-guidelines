package harness.run

import cats.effect.IO
import java.lang.ProcessBuilder.Redirect
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

final case class ProcResult(rc: Int, timedOut: Boolean)

object Proc:
  private val GraceMs = 5000L
  // child becomes leader of its own process group (pgid = its pid), like GA timeout.py start_new_session; a command
  // that cannot be exec'd exits 127 (as a shell would) instead of perl's 0
  private val GroupLeader =
    Seq("/usr/bin/perl", "-e", """setpgrp(0,0); exec { $ARGV[0] } @ARGV or do { print STDERR "$ARGV[0]: $!\n"; exit 127 }""", "--")

  def run(
      cmd: Seq[String],
      cwd: os.Path,
      log: os.Path,
      env: Map[String, Option[String]] = Map.empty,
      cap: Option[Int] = None,
      stdin: Option[os.Path] = None,
      appendLog: Boolean = false,
      stderrTo: Option[os.Path] = None
  ): IO[ProcResult] =
    supervised(start(cmd, cwd, log, env, stdin, appendLog, stderrTo), cap)

  /** stdout + stderr merged, stdin /dev/null; in its own process group, killed on cancel (as [[run]]). The output goes
    * through a temp file, so no pipe read blocks the cancel. */
  def capture(cmd: Seq[String], cwd: os.Path): IO[(Int, String)] =
    cats.effect.Resource.make(IO.blocking(os.temp(prefix = "harness-capture")))(f => IO.blocking(os.remove(f)).void)
      .use { f =>
        run(cmd, cwd, f).flatMap(r =>
          IO.blocking((r.rc, new String(os.read.bytes(f), java.nio.charset.StandardCharsets.UTF_8))))
      }

  /** stdout discarded, stderr to the console, stdin /dev/null (GA `cmd >/dev/null`); process group, killed on cancel. */
  def quiet(cmd: Seq[String], cwd: os.Path): IO[Int] =
    supervised(builder(cmd, cwd, None).redirectOutput(Redirect.DISCARD).redirectError(Redirect.INHERIT).start(), None).map(_.rc)

  // started inside uncancelable: a cancel racing the start still kills the child
  private def supervised(start: => Process, cap: Option[Int]): IO[ProcResult] =
    IO.uncancelable { poll =>
      IO.blocking(start).flatMap { p =>
        val wait = IO.interruptible(p.waitFor()).map(rc => ProcResult(rc, timedOut = false))
        val bounded = cap match
          case None => wait
          case Some(s) =>
            wait.timeout(s.seconds).recoverWith { case _: TimeoutException =>
              IO.blocking(killTree(p)).as(ProcResult(124, timedOut = true))
            }
        poll(bounded).onCancel(IO.blocking(killTree(p)))
      }
    }

  private def builder(cmd: Seq[String], cwd: os.Path, stdin: Option[os.Path]): ProcessBuilder =
    new ProcessBuilder((GroupLeader ++ cmd).asJava).directory(cwd.toIO)
      .redirectInput(Redirect.from(stdin.fold(new java.io.File("/dev/null"))(_.toIO)))

  private def start(
      cmd: Seq[String], cwd: os.Path, log: os.Path, env: Map[String, Option[String]],
      stdin: Option[os.Path], appendLog: Boolean, stderrTo: Option[os.Path]
  ): Process =
    val pb = builder(cmd, cwd, stdin)
    env.foreach {
      case (k, Some(v)) => pb.environment().put(k, v)
      case (k, None)    => pb.environment().remove(k)
    }
    pb.redirectOutput(if appendLog then Redirect.appendTo(log.toIO) else Redirect.to(log.toIO))
    stderrTo match
      case Some(e) => pb.redirectError(Redirect.to(e.toIO))
      case None    => pb.redirectErrorStream(true)
    pb.start()

  private def signalGroup(sig: String, pgid: Long): Int =
    new ProcessBuilder("/bin/sh", "-c", s"kill -$sig -- -$pgid").redirectErrorStream(true)
      .redirectOutput(Redirect.DISCARD).start().waitFor()

  private def groupAlive(pgid: Long): Boolean = signalGroup("0", pgid) == 0

  /** SIGTERM the process group (and every descendant), wait up to 5 s, then SIGKILL the group and survivors. */
  private def killTree(p: Process): Unit =
    val pgid = p.pid
    val all = p.descendants().iterator().asScala.toList :+ p.toHandle
    signalGroup("TERM", pgid)
    all.foreach(_.destroy())
    val deadline = System.currentTimeMillis() + GraceMs
    while (groupAlive(pgid) || all.exists(_.isAlive)) && System.currentTimeMillis() < deadline do Thread.sleep(50)
    signalGroup("KILL", pgid)
    (p.descendants().iterator().asScala.toList ++ all).distinct.filter(_.isAlive).foreach(_.destroyForcibly())
    p.waitFor()
    ()
