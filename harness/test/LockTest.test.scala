package harness.run

import cats.effect.IO
import munit.CatsEffectSuite

class LockTest extends CatsEffectSuite:
  private def mode(p: os.Path): Int = os.perms(p).toInt() & 0xfff

  private def setup(): (os.Path, os.Path, os.Path) =
    val a = os.temp.dir(perms = "rwxr-xr-x"); val b = os.temp.dir(perms = "rwx------")
    os.perms.set(a, "rwxr-xr-x"); os.perms.set(b, "rwx------")
    (a, b, os.temp.dir() / "modes.txt")

  test("modes restored after success"):
    val (a, b, rec) = setup()
    Lock.locked(List(a, b), rec).use { _ =>
      IO {
        assertEquals(mode(a), 0); assertEquals(mode(b), 0)
        assertEquals(os.read.lines(rec).toList, List(s"755 $a", s"700 $b"))
      }
    }.map { _ =>
      assertEquals(mode(a), 0x1ed); assertEquals(mode(b), 0x1c0); assert(!os.exists(rec))
    }

  test("modes restored after an error"):
    val (a, b, rec) = setup()
    Lock.locked(List(a, b), rec).use(_ => IO.raiseError[Unit](new RuntimeException("boom"))).attempt.map { r =>
      assert(r.isLeft)
      assertEquals(mode(a), 0x1ed); assertEquals(mode(b), 0x1c0); assert(!os.exists(rec))
    }

  test("modes restored after fiber.cancel inside the resource"):
    val (a, b, rec) = setup()
    for
      inside <- IO.deferred[Unit]
      fib <- Lock.locked(List(a, b), rec).use(_ => inside.complete(()) *> IO.never[Unit]).start
      _ <- inside.get
      _ = { assertEquals(mode(a), 0); assertEquals(mode(b), 0) }
      _ <- fib.cancel
    yield
      assertEquals(mode(a), 0x1ed); assertEquals(mode(b), 0x1c0); assert(!os.exists(rec))

  test("unlockAll chmods base, head and runs/* to 755"):
    val root = os.temp.dir()
    val ds = List(root / "base", root / "head", root / "runs" / "r1", root / "runs" / "r2")
    ds.foreach { d => os.makeDir.all(d); os.perms.set(d, 0) }
    Lock.unlockAll(root).map(_ => ds.foreach(d => assertEquals(mode(d), 0x1ed)))

  test("unlockAll tolerates missing dirs"):
    Lock.unlockAll(os.temp.dir())

  test("partial chmod failure restores the dirs already locked and removes the record"):
    val (a, b, rec) = setup()
    // /usr is not ours: chmod fails after `a` was already locked
    val r = Lock.locked(List(a, os.Path("/usr"), b), rec).use(_ => IO.unit).attempt
    r.map { res =>
      assert(res.isLeft)
      assertEquals(mode(a), 0x1ed); assertEquals(mode(b), 0x1c0); assert(!os.exists(rec))
    }

  test("restore tries every dir after a failing chmod and reports each failure naming the dir"):
    val (a, b, rec) = setup()
    for
      warned <- IO.ref(Vector.empty[String])
      r <- Lock.locked(List(a, os.Path("/usr"), b), rec, m => warned.update(_ :+ m)).use(_ => IO.unit).attempt
      ws <- warned.get
    yield
      assert(r.isLeft)
      assertEquals(mode(a), 0x1ed); assertEquals(mode(b), 0x1c0); assert(!os.exists(rec))
      assertEquals(ws.size, 1, ws)
      assert(ws.head.startsWith("gate-ab: lock restore failed for /usr (mode 755): "), ws.head)

  test("restore reports nothing when every dir is restored"):
    val (a, b, rec) = setup()
    for
      warned <- IO.ref(Vector.empty[String])
      _ <- Lock.locked(List(a, b), rec, m => warned.update(_ :+ m)).use(_ => IO.unit)
      ws <- warned.get
    yield assertEquals(ws, Vector.empty)
