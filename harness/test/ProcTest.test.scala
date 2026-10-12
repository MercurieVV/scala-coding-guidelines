package harness.run

import cats.effect.IO
import munit.CatsEffectSuite
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import cats.syntax.all.*

class ProcTest extends CatsEffectSuite:
  override def munitIOTimeout: Duration = 60.seconds

  private def alive(pid: Long): Boolean = ProcessHandle.of(pid).map(_.isAlive).orElse(false)

  private def pollUntil(cond: => Boolean, tries: Int): IO[Unit] =
    IO(cond).flatMap { ok =>
      if ok || tries <= 0 then IO.unit else IO.sleep(100.millis) *> pollUntil(cond, tries - 1)
    }

  private def pidReady(p: os.Path): Boolean = os.exists(p) && os.read(p).trim.nonEmpty

  private def readPid(p: os.Path): Long = os.read(p).trim.toLong

  test("timeout kills grandchildren"):
    val d = os.temp.dir()
    val pidFile = d / "gc.pid"
    val cmd = Seq("/bin/sh", "-c", s"sleep 300 & echo $$! > $pidFile; wait")
    Proc.run(cmd, d, d / "log", cap = Some(1)).map { r =>
      assertEquals(r, ProcResult(124, true))
      val pid = readPid(pidFile)
      Thread.sleep(500)
      assert(!alive(pid), "grandchild still alive")
    }

  test("cancel kills the child tree"):
    val d = os.temp.dir()
    val pidFile = d / "pid"
    val cmd = Seq("/bin/sh", "-c", s"echo $$$$ > $pidFile; exec sleep 100")
    for
      fib <- Proc.run(cmd, d, d / "log").start
      _ <- pollUntil(pidReady(pidFile), 100)
      pid = readPid(pidFile)
      _ <- fib.cancel
      _ <- pollUntil(!alive(pid), 50)
    yield assert(!alive(pid), "child survived cancel")

  test("cancel during grace still SIGKILLs a TERM-ignoring child"):
    val d = os.temp.dir()
    val pidFile = d / "pid"
    val cmd = Seq("/bin/sh", "-c", s"""trap "" TERM; echo $$$$ > $pidFile; while :; do sleep 1; done""")
    for
      fib <- Proc.run(cmd, d, d / "log").start
      _ <- pollUntil(pidReady(pidFile), 100)
      pid = readPid(pidFile)
      _ <- fib.cancel
      _ <- pollUntil(!alive(pid), 50)
    yield assert(!alive(pid), "TERM-ignoring child survived cancel")

  test("normal exit reports rc and writes the log, env unset/set, stdin, stderrTo"):
    val d = os.temp.dir()
    val in = d / "in"; os.write(in, "hello\n")
    val cmd = Seq("/bin/sh", "-c", "cat; echo \"v=$FOO u=${BAR-unset}\"; echo err >&2; exit 3")
    Proc.run(cmd, d, d / "log", env = Map("FOO" -> Some("1"), "BAR" -> None),
      stdin = Some(in), stderrTo = Some(d / "err")).map { r =>
      assertEquals(r, ProcResult(3, false))
      assertEquals(os.read(d / "log"), "hello\nv=1 u=unset\n")
      assertEquals(os.read(d / "err"), "err\n")
    }

  test("appendLog appends"):
    val d = os.temp.dir(); os.write(d / "log", "A\n")
    Proc.run(Seq("/bin/sh", "-c", "echo B"), d, d / "log", appendLog = true).map { _ =>
      assertEquals(os.read(d / "log"), "A\nB\n")
    }

  test("capture returns rc and output"):
    val d = os.temp.dir()
    Proc.capture(Seq("/bin/sh", "-c", "echo hi; exit 2"), d).map(r => assertEquals(r, (2, "hi\n")))

  test("timeout kills a reparented background grandchild (process group)"):
    val d = os.temp.dir()
    val pidFile = d / "gc.pid"
    // inner sh backgrounds a sleep and exits: the sleep is reparented, invisible to ProcessHandle.descendants
    val cmd = Seq("/bin/sh", "-c", s"""/bin/sh -c 'sleep 300 & echo $$! > $pidFile'; sleep 300""")
    Proc.run(cmd, d, d / "log", cap = Some(2)).map { r =>
      assertEquals(r, ProcResult(124, true))
      val pid = readPid(pidFile)
      Thread.sleep(500)
      assert(!alive(pid), "reparented grandchild still alive")
    }

  test("second cancel during the grace window still ends with SIGKILL, promptly"):
    // GA: a second SIGINT/SIGTERM while timeout.py is in its grace must not skip the SIGKILL.
    // IO mapping: the kill finalizer is uncancelable, so a second fiber.cancel (and a cancelled canceller) cannot skip it.
    val d = os.temp.dir()
    val pidFile = d / "pid"
    val cmd = Seq("/bin/sh", "-c", s"""trap "" TERM; echo $$$$ > $pidFile; while :; do sleep 1; done""")
    for
      fib <- Proc.run(cmd, d, d / "log").start
      _ <- pollUntil(pidReady(pidFile), 100)
      pid = readPid(pidFile)
      t0 <- IO.monotonic
      c1 <- fib.cancel.start
      _ <- IO.sleep(1.second)
      c2 <- fib.cancel.start
      _ <- IO.sleep(200.millis)
      _ <- c2.cancel
      _ <- c1.join
      _ <- fib.cancel.timeout(15.seconds)
      t1 <- IO.monotonic
    yield
      assert(!alive(pid), "child survived second cancel during grace")
      assert((t1 - t0) < 12.seconds, s"took ${t1 - t0}")

  test("cancel racing process start never orphans the child"):
    val d = os.temp.dir()
    val marker = d / "m"
    val cmd = Seq("/bin/sh", "-c", s"echo $$$$ > $marker; exec sleep 100")
    (1 to 20).toList.traverse_ { _ =>
        for
          fib <- Proc.run(cmd, d, d / "log").start
          _ <- fib.cancel
        yield ()
      } *> IO.sleep(1500.millis).map { _ =>
        val sleepers = ProcessHandle.allProcesses().iterator().asScala
          .filter(h => h.info.commandLine.orElse("").contains("sleep 100") && h.parent.map(_.pid) == java.util.Optional.of(ProcessHandle.current.pid)).toList
        assertEquals(sleepers.size, 0)
      }
