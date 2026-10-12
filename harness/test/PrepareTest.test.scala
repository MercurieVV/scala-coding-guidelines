package harness.run

import cats.effect.IO
import harness.config.{Experiment, Load, TargetSpec}
import harness.core.{ErosionFigure, ErosionMeasure, Json}
import harness.target.MillScala
import munit.CatsEffectSuite
import scala.collection.mutable.ListBuffer

/** GA tests/shell-test.sh sections `prepare.sh against a fake skreeep2`, `prepare.sh: authored hidden spec …` and
  * `prepare-chain.sh`, case for case (same names), plus warm-cache.sh (HEAD tree) and the FR reference-patch branch
  * of prepare.sh, which GA leaves untested. GA's fake `./mill` scripts become [[FakeMill]] scripts; temp git repos are
  * the target and the holdout.
  */
class PrepareTest extends CatsEffectSuite:
  private val harnessDir: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .find(d => os.exists(d / "experiments" / "gate-ab.conf")).getOrElse(fail("harness dir not found"))
  private val exp0: Experiment = Load.experiment(harnessDir / "experiments" / "gate-ab.conf").fold(fail(_), identity)

  final case class Call(tree: os.Path, args: Seq[String], outDir: String, holdout: Option[String], cap: Option[Int], keepGoing: Boolean)

  /** A Mill target whose runBuild runs a Scala script (GA's fake `./mill`): (tree, args, env) => (rc, output). */
  final class FakeMill(ts: TargetSpec, script: (os.Path, Seq[String], Map[String, Option[String]]) => (Int, String)) extends MillScala(ts):
    val calls: ListBuffer[Call] = ListBuffer.empty
    override def runBuild(tree: os.Path, args: Seq[String], outDir: String, log: os.Path, env: Map[String, Option[String]],
                          cap: Option[Int], keepGoing: Boolean): IO[ProcResult] = IO.blocking {
      calls += Call(tree, args, outDir, env.get("DSH_HOLDOUT_DIR").flatten, cap, keepGoing)
      val (rc, out) = script(tree, args, env)
      os.write.over(log, out)
      ProcResult(rc, timedOut = rc == 124)
    }

  private def git(dir: os.Path, args: String*): String =
    os.proc(Seq("git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false") ++ args)
      .call(cwd = dir, stderr = os.Pipe).out.text().trim
  private def initCommit(dir: os.Path, msg: String): String =
    git(dir, "init", "-q"); git(dir, "add", "-A"); git(dir, "commit", "-qm", msg); git(dir, "rev-parse", "HEAD")

  private def holdoutOk(env: Map[String, Option[String]]): Boolean =
    env.get("DSH_HOLDOUT_DIR").flatten.exists(d => os.isDir(os.Path(d)))

  /** Runs `io`; Left(message) when it dies with a HarnessError. */
  private def attempt[A](io: IO[A]): IO[Either[String, A]] =
    io.attempt.map(_.left.map { case HarnessError(m, _) => m; case e => fail(s"not a HarnessError: $e") })

  private def lines(p: os.Path): String = if os.exists(p) then os.read(p).replace('\n', ' ') else "<missing>"

  // ---------------------------------------------------------------- prepare.sh against a fake skreeep2

  /** GA: fake skreeep2 with m/src/A.scala and m/test/src/ASpec.scala; task t1 hides ASpec and stubs A. */
  final class Simple:
    val tmp: os.Path = os.temp.dir()
    val fake: os.Path = tmp / "skreeep2"
    val home: os.Path = tmp / "home"
    val root: os.Path = tmp / "root"
    os.write(fake / "m" / "src" / "A.scala", "object A { def f = 1 }\n", createFolders = true)
    os.write(fake / "m" / "test" / "src" / "ASpec.scala", "class ASpec\n", createFolders = true)
    val head: String = initCommit(fake, "init")
    os.makeDir.all(root / "head")
    os.write(home / "tasks" / "t1" / "task.env",
      "TASK_MODULE='m.test'\nHIDDEN_SPEC='m/test/src/ASpec.scala'\nCUT_FILES='m/src/A.scala'\nFEATURE='f'\n", createFolders = true)
    os.write(home / "tasks" / "t1" / "stub" / "m" / "src" / "A.scala", "object A { def f = ??? }\n", createFolders = true)
    val exp: Experiment = exp0.copy(root = root, tasksDir = home / "tasks", target = exp0.target.copy(repo = fake, commit = head))
    // GA fake mill: head passes; __.compile passes; elsewhere fails iff the hidden spec is present
    val mill: FakeMill = FakeMill(exp.target, (tree, args, _) =>
      if tree.last == "head" then (0, "")
      else if args.lastOption.contains("__.compile") then (0, "")
      else if os.exists(tree / "m" / "test" / "src" / "ASpec.scala") then (1, "")
      else (0, ""))

  test("prepare exit 0, prepare validation, prepare one commit, prepare hides spec, prepare applies stub, prepare never creates cache"):
    val e = Simple()
    val out = ListBuffer.empty[String]
    Prepare.taskWith(e.exp, "t1", e.mill, s => IO(out += s)).map { ok =>
      val b = e.root / "base" / "t1"
      assert(ok, "prepare exit 0")
      assertEquals(lines(b / "validation.txt"), "cut_compiles=yes hidden_fails_on_cut=yes hidden_passes_on_head=yes ", "prepare validation")
      assertEquals(out.toList, List("cut_compiles=yes\nhidden_fails_on_cut=yes\nhidden_passes_on_head=yes"), "cat validation.txt")
      assertEquals(os.proc("git", "rev-list", "--count", "HEAD").call(cwd = b / "tree").out.text().trim, "1", "prepare one commit")
      assert(!os.exists(b / "tree" / "m" / "test" / "src" / "ASpec.scala") && os.isFile(b / "hidden" / "m" / "test" / "src" / "ASpec.scala"),
        "prepare hides spec")
      assert(os.read(b / "tree" / "m" / "src" / "A.scala").contains("???"), "prepare applies stub")
      assert(!os.exists(e.root / "cache"), "prepare never creates cache")
      assert(!os.exists(b / "validate"), "validate copy removed")
      // GA order and logs: cut compile (1800 s), hidden on cut (600 s), hidden on head (600 s), every one with out
      assertEquals(e.mill.calls.toList.map(c => (c.tree.last, c.args.toList, c.outDir, c.cap)), List(
        ("validate", List("__.compile"), "out", Some(1800)),
        ("validate", List("m.test"), "out", Some(600)),
        ("head", List("m.test"), "out", Some(600))))
      List("cut-compile.log", "hidden-on-cut.log", "hidden-on-head.log").foreach(l => assert(os.isFile(b / l), l))
    }.guarantee(IO.blocking(os.remove.all(e.tmp)))

  test("prepare without a HEAD tree dies after the cut checks; an unknown task dies"):
    val e = Simple()
    os.remove.all(e.root / "head")
    (for
      r <- attempt(Prepare.taskWith(e.exp, "t1", e.mill, _ => IO.unit, _ => IO.unit))
      _ = assertEquals(r, Left(s"no ${e.root / "head"} — run warm-cache.sh first"))
      _ = assertEquals(e.mill.calls.size, 2, "cut compile and hidden-on-cut ran first")
      r2 <- attempt(Prepare.taskWith(e.exp, "nope", e.mill, _ => IO.unit, _ => IO.unit))
    yield assertEquals(r2, Left(s"no task nope (${e.home / "tasks" / "nope" / "task.env"})"))).guarantee(IO.blocking(os.remove.all(e.tmp)))

  test("prepare: a hidden spec that is not in the tree dies; a missing stub dies"):
    val e = Simple()
    val env = e.home / "tasks" / "t1" / "task.env"
    (for
      _ <- IO.blocking(os.write.over(env, "TASK_MODULE='m.test'\nHIDDEN_SPEC='m/test/src/BSpec.scala'\nCUT_FILES='m/src/A.scala'\nFEATURE='f'\n"))
      r1 <- attempt(Prepare.taskWith(e.exp, "t1", e.mill, _ => IO.unit, _ => IO.unit))
      _ = assertEquals(r1, Left("hidden spec m/test/src/BSpec.scala not in tree"))
      _ = os.write.over(env, "TASK_MODULE='m.test'\nHIDDEN_SPEC='m/test/src/ASpec.scala'\nCUT_FILES='m/src/Z.scala'\nFEATURE='f'\n")
      r2 <- attempt(Prepare.taskWith(e.exp, "t1", e.mill, _ => IO.unit, _ => IO.unit))
    yield assertEquals(r2, Left("stub m/src/Z.scala"))
    ).guarantee(IO.blocking(os.remove.all(e.tmp)))

  // ---------------------------------------------------------------- prepare.sh: authored hidden spec and gate-visible

  /** GA fake mill t2: compile ok; on head everything passes; elsewhere m.test fails iff the hidden spec is present,
    * d.test fails iff A.scala is cut (unless NO_DOWN), holdout.test fails iff FAIL_HOLDOUT; several tasks need `+`.
    */
  final class Authored:
    val tmp: os.Path = os.temp.dir()
    val fake: os.Path = tmp / "skreeep2"
    val home: os.Path = tmp / "home"
    val root: os.Path = tmp / "root"
    val hsrc: os.Path = tmp / "hsrc"
    os.write(fake / "m" / "src" / "A.scala", "object A { def f = 1 }\n", createFolders = true)
    os.write(fake / "d" / "src" / "D.scala", "object D\n", createFolders = true)
    val head: String = initCommit(fake, "init")
    os.write(hsrc / "x", "x\n", createFolders = true)
    val hhead: String = initCommit(hsrc, "h")
    os.makeDir.all(root / "head")
    os.write(root / "head-baseline.json", """{"modules": {"m.test": true, "d.test": true, "e.test": true}}""" + "\n")
    val t2: os.Path = home / "tasks" / "t2"
    os.write(t2 / "task.env",
      "TASK_MODULE='m.test'\nHIDDEN_SPEC='m/test/src/ASpec.scala'\nHIDDEN_SPEC_SRC='hidden/m/test/src/ASpec.scala'\nGATE_VISIBLE=1\nCUT_FILES='m/src/A.scala'\nFEATURE='f'\n",
      createFolders = true)
    os.write(t2 / "stub" / "m" / "src" / "A.scala", "object A { def f = ??? }\n", createFolders = true)
    os.write(t2 / "hidden" / "m" / "test" / "src" / "ASpec.scala", "class ASpec\n", createFolders = true)
    def exp(commit: String = head): Experiment = exp0.copy(root = root, tasksDir = home / "tasks",
      target = exp0.target.copy(repo = fake, commit = commit, holdoutRepo = hsrc, holdoutCommit = hhead))
    def mill(modes: String*): FakeMill = FakeMill(exp().target, (tree, args, env) =>
      if tree.last == "head" || tree.last == "head-validate" then (0, "")
      else
        val nt = args.count(a => a != "+" && !a.startsWith("-") && a.contains('.'))
        if nt > 1 && !args.contains("+") then (2, "several tasks without +\n")
        else if modes.contains("MILL_CRASH") && nt > 0 && args.contains("d.test") then (3, "")
        else
          val rc = args.foldLeft(0) { (rc, a) => a match
            case "m.test" if os.exists(tree / "m" / "test" / "src" / "ASpec.scala") => 1
            case "d.test" if !modes.contains("NO_DOWN") && os.read(tree / "m" / "src" / "A.scala").contains("???") => 1
            case "holdout.test" if !holdoutOk(env) => 9
            case "holdout.test" if modes.contains("FAIL_HOLDOUT") => 1
            case _ => rc
          }
          (rc, if rc == 0 then "" else "[error] fake.test 1 tests failed:\n"))
    val valid: os.Path = root / "base" / "t2" / "validation.txt"

  test("prepare authored exit 0, prepare authored validation, prepare authored spec hidden, prepare leaves head untouched"):
    val e = Authored()
    val m = e.mill()
    Prepare.taskWith(e.exp(), "t2", m, _ => IO.unit, _ => IO.unit).map { ok =>
      val b = e.root / "base" / "t2"
      assert(ok, "prepare authored exit 0")
      assertEquals(lines(e.valid), "cut_compiles=yes hidden_fails_on_cut=yes hidden_passes_on_head=yes gate_visible_on_cut=yes ",
        "prepare authored validation")
      assert(os.isFile(b / "hidden" / "m" / "test" / "src" / "ASpec.scala") && !os.exists(b / "tree" / "m" / "test" / "src" / "ASpec.scala"),
        "prepare authored spec hidden")
      assert(!os.exists(e.root / "head" / "m" / "test" / "src" / "ASpec.scala"), "prepare leaves head untouched")
      // the gate-visible run: every other baseline module joined with +, -k; then the holdout with its physical dir
      val gv = m.calls.toList.filter(c => c.tree.last == "validate" && c.keepGoing)
      assertEquals(gv.map(c => (c.args.toList, c.cap)), List((List("d.test", "+", "e.test"), Some(1800))))
      val ho = m.calls.toList.filter(_.args == Seq("holdout.test"))
      assertEquals(ho.map(c => (c.tree.last, c.cap)), List(("validate", Some(600))))
      assert(ho.head.holdout.exists(_.endsWith("/holdout-validate")))
      assert(os.read(b / "gate-visible.log").contains("tests failed"))
      assert(!os.exists(b / "gate-visible.log.holdout") && !os.exists(b / "holdout-validate") && !os.exists(b / "head-validate"))
      // hidden-on-head ran in a throwaway HEAD copy, 1800 s
      assertEquals(m.calls.toList.filter(_.tree.last == "head-validate").map(c => (c.args.toList, c.cap)), List((List("m.test"), Some(1800))))
    }.guarantee(IO.blocking(os.remove.all(e.tmp)))

  test("prepare not gate-visible fails, prepare not gate-visible line, prepare holdout-visible exit 0"):
    val e = Authored()
    (for
      ok1 <- Prepare.taskWith(e.exp(), "t2", e.mill("NO_DOWN"), _ => IO.unit, _ => IO.unit)
      _ = assert(!ok1, "prepare not gate-visible fails")
      _ = assertEquals(os.read.lines(e.valid).lift(3), Some("gate_visible_on_cut=no"), "prepare not gate-visible line")
      ok2 <- Prepare.taskWith(e.exp(), "t2", e.mill("NO_DOWN", "FAIL_HOLDOUT"), _ => IO.unit, _ => IO.unit)
    yield assert(ok2, "prepare holdout-visible exit 0")).guarantee(IO.blocking(os.remove.all(e.tmp)))

  test("prepare gate-visible crash dies, prepare crash leaves no validation"):
    val e = Authored()
    attempt(Prepare.taskWith(e.exp(), "t2", e.mill("MILL_CRASH"), _ => IO.unit, _ => IO.unit)).map { r =>
      assert(r.left.exists(_.contains("without a test failure")), s"prepare gate-visible crash dies: $r")
      assertEquals(r, Left(s"gate-visible check failed without a test failure (see ${e.root / "base" / "t2" / "gate-visible.log"})"))
      assert(!os.exists(e.valid), "prepare crash leaves no validation")
      assert(!os.exists(e.root / "base" / "t2" / "validate"))
    }.guarantee(IO.blocking(os.remove.all(e.tmp)))

  test("prepare gate-visible timeout dies"):
    val e = Authored()
    val m = FakeMill(e.exp().target, (tree, args, _) =>
      if tree.last == "validate" && args.contains("holdout.test") then (124, "") else (0, ""))
    attempt(Prepare.taskWith(e.exp(), "t2", m, _ => IO.unit, _ => IO.unit)).map { r =>
      assertEquals(r, Left(s"gate-visible check timed out (see ${e.root / "base" / "t2" / "gate-visible.log"})"))
      assert(!os.exists(e.valid))
    }.guarantee(IO.blocking(os.remove.all(e.tmp)))

  test("prepare missing authored spec dies, prepare missing spec leaves no validation, prepare authored shadow dies"):
    val e = Authored()
    os.remove(e.t2 / "hidden" / "m" / "test" / "src" / "ASpec.scala")
    (for
      r1 <- attempt(Prepare.taskWith(e.exp(), "t2", e.mill(), _ => IO.unit, _ => IO.unit))
      _ = assert(r1.left.exists(_.contains("hidden/m/test/src/ASpec.scala")), s"prepare missing authored spec dies: $r1")
      _ = assertEquals(r1, Left(s"authored hidden spec ${e.t2 / "hidden" / "m" / "test" / "src" / "ASpec.scala"} missing"))
      _ = assert(!os.exists(e.valid), "prepare missing spec leaves no validation")
      _ = assert(!os.exists(e.root / "base" / "t2"), "base removed")
      _ = os.write(e.t2 / "hidden" / "m" / "test" / "src" / "ASpec.scala", "class ASpec\n")
      _ = os.write(e.fake / "m" / "test" / "src" / "ASpec.scala", "class ASpec\n", createFolders = true)
      _ = git(e.fake, "add", "-A")
      _ = git(e.fake, "commit", "-qm", "real")
      r2 <- attempt(Prepare.taskWith(e.exp(git(e.fake, "rev-parse", "HEAD")), "t2", e.mill(), _ => IO.unit, _ => IO.unit))
    yield
      assert(r2.left.exists(_.contains("already in the tree")), s"prepare authored shadow dies: $r2")
      assertEquals(r2, Left("hidden spec m/test/src/ASpec.scala already in the tree; HIDDEN_SPEC_SRC would shadow it"))
    ).guarantee(IO.blocking(os.remove.all(e.tmp)))

  // ---------------------------------------------------------------- prepare.sh: FR reference patch (GA has no shell test)

  /** Authored task whose HEAD is a git tree and which carries a reference patch (spec 2026-10-10 §3). */
  final class Fr:
    val a: Authored = Authored()
    val h: os.Path = a.root / "head"
    os.write(h / "m" / "src" / "A.scala", "object A {\n  def f = 1\n}\n", createFolders = true)
    initCommit(h, "head")
    os.write.over(h / "m" / "src" / "A.scala", "object A {\n  def f = 2\n}\n")
    os.write(a.t2 / "ref.patch", git(h, "diff") + "\n")
    git(h, "checkout", "--", ".")
    os.write.over(a.t2 / "task.env",
      "TASK_MODULE='m.test'\nHIDDEN_SPEC='m/test/src/ASpec.scala'\nHIDDEN_SPEC_SRC='hidden/m/test/src/ASpec.scala'\nREFERENCE_PATCH='ref.patch'\nCUT_FILES='m/src/A.scala'\nFEATURE='f'\n")
    os.write(h / "out" / "junk", "x", createFolders = true)
    var seen: List[String] = Nil
    def mill(fail: Boolean): FakeMill = FakeMill(a.exp().target, (tree, args, env) =>
      if tree.last == "head-validate" then
        seen :+= s"${os.read(tree / "m" / "src" / "A.scala").linesIterator.toList(1).trim} out=${os.exists(tree / "out")} spec=${os.exists(tree / "m" / "test" / "src" / "ASpec.scala")}"
        if args.contains("holdout.test") && !holdoutOk(env) then (9, "")
        else if fail && args.contains("holdout.test") then (1, "[error] h 1 tests failed:\n")
        else (0, "")
      else if tree.last == "validate" && args.contains("m.test") && os.exists(tree / "m" / "test" / "src" / "ASpec.scala") then (1, "")
      else (0, ""))

  test("prepare FR: reference patch applied to a HEAD copy, every module and the holdout pass"):
    val f = Fr()
    val m = f.mill(fail = false)
    Prepare.taskWith(f.a.exp(), "t2", m, _ => IO.unit, _ => IO.unit).map { ok =>
      assert(ok)
      assertEquals(lines(f.a.valid), "cut_compiles=yes hidden_fails_on_cut=yes hidden_passes_on_head=yes reference_passes_all=yes ")
      val hv = m.calls.toList.filter(_.tree.last == "head-validate")
      assertEquals(hv.map(c => (c.args.toList, c.keepGoing, c.cap)), List(
        (List("m.test"), false, Some(1800)),
        (List("d.test", "+", "e.test", "+", "m.test"), true, Some(1800)),
        (List("holdout.test"), false, Some(600))))
      assert(hv.last.holdout.exists(_.endsWith("/holdout-ref")))
      assertEquals(f.seen.distinct, List("def f = 2 out=false spec=true"))
      assertEquals(os.read(f.h / "m" / "src" / "A.scala"), "object A {\n  def f = 1\n}\n", "HEAD stays as exported")
      assert(!os.exists(f.a.root / "base" / "t2" / "head-validate") && !os.exists(f.a.root / "base" / "t2" / "holdout-ref"))
    }.guarantee(IO.blocking(os.remove.all(f.a.tmp)))

  test("prepare FR: a failing holdout records reference_passes_all=no"):
    val f = Fr()
    Prepare.taskWith(f.a.exp(), "t2", f.mill(fail = true), _ => IO.unit, _ => IO.unit).map { ok =>
      assert(!ok)
      assertEquals(os.read.lines(f.a.valid).last, "reference_passes_all=no")
      assert(os.read(f.a.root / "base" / "t2" / "reference-all.log").contains("tests failed"), "holdout output appended")
    }.guarantee(IO.blocking(os.remove.all(f.a.tmp)))

  test("prepare FR: ga_check_patch, missing and not applying"):
    val f = Fr()
    val p = f.a.t2 / "ref.patch"
    (for
      _ <- IO.blocking(os.move(p, f.a.t2 / "gone.patch"))
      r1 <- attempt(Prepare.taskWith(f.a.exp(), "t2", f.mill(fail = false), _ => IO.unit, _ => IO.unit))
      _ = assertEquals(r1, Left(s"reference patch $p missing"))
      _ = os.write(p, os.read(f.a.t2 / "gone.patch").replace("def f = 1", "def f = 7"))
      r2 <- attempt(Prepare.taskWith(f.a.exp(), "t2", f.mill(fail = false), _ => IO.unit, _ => IO.unit))
    yield assertEquals(r2, Left(s"reference patch $p does not apply to ${f.a.root / "base" / "t2" / "head-validate"}"))
    ).guarantee(IO.blocking(os.remove.all(f.a.tmp)))

  // ---------------------------------------------------------------- warm-cache.sh (GA has no shell test)

  test("head: HEAD exported, one commit, compiled once with out; failure dies"):
    val e = Simple()
    os.write(e.root / "head" / "stale", "x")
    val out = ListBuffer.empty[String]
    val m = FakeMill(e.exp.target, (_, _, _) => (0, ""))
    (for
      _ <- Prepare.headWith(e.exp, m, s => IO(out += s))
      h = e.root / "head"
      _ = assert(!os.exists(h / "stale"), "rm -rf head first")
      _ = assert(os.isFile(h / "m" / "test" / "src" / "ASpec.scala"))
      _ = assertEquals(os.proc("git", "log", "--format=%s %an <%ae>").call(cwd = h).out.text().trim, "head gate-eval <gate-eval@local>")
      _ = assertEquals(m.calls.toList.map(c => (c.tree, c.args.toList, c.outDir)), List((h, List("__.compile", "+", "__.test.compile"), "out")))
      _ = assertEquals(out.toList, List(s"head reference tree: $h"))
      r <- attempt(Prepare.headWith(e.exp, FakeMill(e.exp.target, (_, _, _) => (1, "boom\n")), _ => IO.unit, _ => IO.unit))
      _ = assertEquals(r, Left("HEAD does not compile"))
      r2 <- attempt(Prepare.headWith(e.exp.copy(target = e.exp.target.copy(commit = "nope")), m, _ => IO.unit, _ => IO.unit))
    yield assertEquals(r2, Left("git archive failed"))
    ).guarantee(IO.blocking(os.remove.all(e.tmp)))

  // ---------------------------------------------------------------- prepare-chain.sh (spec 2026-10-11 §3)

  final class Chain:
    val tmp: os.Path = os.temp.dir()
    val proot: os.Path = tmp / "root"
    val home: os.Path = tmp / "home"
    val hsrc: os.Path = tmp / "hsrc"
    val h: os.Path = proot / "head"
    os.write(h / "m" / "src" / "A.scala", "object A {\n  def f = 1\n}\n", createFolders = true)
    initCommit(h, "head")
    os.write(proot / "head-baseline.json", """{"modules": {"m.test": true, "n.test": true}}""" + "\n")
    os.write(hsrc / "HSpec.scala", "spec\n", createFolders = true)
    initCommit(hsrc, "h")
    /** GA mkpatch: a patch on HEAD + prev changing old to new in A.scala, task.env and the prepared hidden spec. */
    private def mkpatch(task: String, prev: Option[os.Path], from: String, to: String): Unit =
      val w = tmp / s"w-$task"
      os.copy(h, w)
      prev.foreach(p => git(w, "apply", p.toString))
      git(w, "add", "-A"); git(w, "commit", "-qm", "p", "--allow-empty")
      os.write.over(w / "m" / "src" / "A.scala", os.read(w / "m" / "src" / "A.scala").replace(from, to))
      os.write(home / "tasks" / task / "ref.patch", git(w, "diff") + "\n", createFolders = true)
      os.remove.all(w)
      os.write(home / "tasks" / task / "task.env",
        s"TASK_MODULE='m.test'\nHIDDEN_SPEC='m/test/src/${task}Spec.scala'\nREFERENCE_PATCH='ref.patch'\nFEATURE='f'\n")
      os.write(proot / "base" / task / "hidden" / "m" / "test" / "src" / s"${task}Spec.scala", s"object ${task}Spec\n", createFolders = true)
    mkpatch("c1", None, "def f = 1", "def f = 2")
    mkpatch("c2", Some(home / "tasks" / "c1" / "ref.patch"), "def f = 2", "def f = 3")
    val exp: Experiment = exp0.copy(root = proot, tasksDir = home / "tasks",
      target = exp0.target.copy(holdoutRepo = hsrc, holdoutCommit = "HEAD"))
    val ero: ErosionMeasure = (_, _) => IO.pure(Right(ErosionFigure(erosion = 0.0, methods = 1, mass = 1.0)))
    var spy: List[String] = Nil
    def mill(fail: Boolean): FakeMill = FakeMill(exp.target, (tree, args, env) =>
      spy :+= s"${args.mkString(" ")} | ${os.read(tree / "m" / "src" / "A.scala").linesIterator.toList(1).trim} | ${
        List("c1", "c2").filter(t => os.exists(tree / "m" / "test" / "src" / s"${t}Spec.scala")).mkString(",")}"
      if args.contains("holdout.test") && !holdoutOk(env) then (9, "")
      else if fail then (1, "[error] m.test 1 tests failed:\n")
      else (0, ""))
    val cvf: os.Path = proot / "chain-validation.txt"
    val ref: os.Path = proot / "chain-reference-erosion.json"

  test("prepare-chain exit 0, validation, reference erosion per step, runs all modules and the holdout, leaves head untouched"):
    val c = Chain()
    val out = ListBuffer.empty[String]
    val m = c.mill(fail = false)
    Prepare.chainWith(c.exp, List("c1", "c2"), m, c.ero, s => IO(out += s)).map { ok =>
      assert(ok, "prepare-chain exit 0")
      assertEquals(os.read(c.cvf), "patches_apply=yes\nreference_passes_all=yes\n", "prepare-chain validation")
      assertEquals(out.toList, List("patches_apply=yes\nreference_passes_all=yes"))
      assertEquals(ujson.read(os.read(c.ref)).arr.map(_("task").str).toList, List("c1", "c2"), "prepare-chain reference erosion per step")
      assertEquals(os.read(c.ref),
        """[{"task": "c1", "methods": 1, "total_mass": 1.0, "erosion": 0.0}, {"task": "c2", "methods": 1, "total_mass": 1.0, "erosion": 0.0}]""" + "\n")
      assertEquals(m.calls.toList.map(c => (c.args.toList, c.keepGoing, c.cap, c.outDir)), List(
        (List("m.test", "+", "n.test"), true, Some(2400), "out"),
        (List("holdout.test"), false, Some(600), "out")), "prepare-chain runs all modules and the holdout")
      assertEquals(c.spy.map(_.split(" \\| ").drop(1).mkString(" | ")).distinct, List("def f = 3 | c1,c2"), "both patches and specs")
      assert(m.calls.last.holdout.exists(_.endsWith("/chain-holdout")))
      assertEquals(os.read(c.h / "m" / "src" / "A.scala").linesIterator.toList(1), "  def f = 1", "prepare-chain leaves head untouched")
      assert(!os.exists(c.proot / "chain-validate") && !os.exists(c.proot / "chain-holdout") && !os.exists(os.Path(s"${c.ref}.tmp")))
      assert(os.isFile(c.proot / "chain-apply-c1.log") && os.isFile(c.proot / "chain-reference-all.log"))
    }.guarantee(IO.blocking(os.remove.all(c.tmp)))

  test("prepare-chain failing tests exit 1, prepare-chain failing tests recorded"):
    val c = Chain()
    Prepare.chainWith(c.exp, List("c1", "c2"), c.mill(fail = true), c.ero, _ => IO.unit, _ => IO.unit).map { ok =>
      assert(!ok, "prepare-chain failing tests exit 1")
      assertEquals(os.read.lines(c.cvf).lift(1), Some("reference_passes_all=no"), "prepare-chain failing tests recorded")
      assert(os.isFile(c.ref), "the reference line is written when the patches apply")
    }.guarantee(IO.blocking(os.remove.all(c.tmp)))

  test("prepare-chain conflicting order exit 1, prepare-chain conflicting order recorded"):
    val c = Chain()
    val m = c.mill(fail = false)
    Prepare.chainWith(c.exp, List("c2", "c1"), m, c.ero, _ => IO.unit, _ => IO.unit).map { ok =>
      assert(!ok, "prepare-chain conflicting order exit 1")
      assertEquals(os.read(c.cvf), "patches_apply=no\nreference_passes_all=no\n", "prepare-chain conflicting order recorded")
      assert(!os.exists(c.ref) && !os.exists(os.Path(s"${c.ref}.tmp")), "no reference erosion")
      assert(m.calls.isEmpty, "no mill run")
    }.guarantee(IO.blocking(os.remove.all(c.tmp)))

  test("prepare-chain dies: no args, no head, no baseline, no reference patch, unprepared step, erosion failure"):
    val c = Chain()
    def run(steps: List[String], ero: ErosionMeasure = c.ero) = attempt(Prepare.chainWith(c.exp, steps, c.mill(false), ero, _ => IO.unit, _ => IO.unit))
    (for
      r0 <- run(Nil)
      _ = assertEquals(r0, Left("usage: prepare-chain.sh <task>..."))
      rz <- run(List("c1", "zz"))
      _ = assertEquals(rz, Left(s"no task zz (${c.home / "tasks" / "zz" / "task.env"})"))
      r1 <- run(List("c1", "c2"), (_, _) => IO.pure(Left("erosion.py: boom")))
      _ = assertEquals(r1, Left("erosion on the reference after c1 failed"))
      _ = os.remove.all(c.proot / "base" / "c2")
      r2 <- run(List("c1", "c2"))
      _ = assertEquals(r2, Left(s"no ${c.proot / "base" / "c2" / "hidden" / "m" / "test" / "src" / "c2Spec.scala"} — run prepare.sh c2 first"))
      _ = os.write.over(c.home / "tasks" / "c1" / "task.env", "TASK_MODULE='m.test'\nHIDDEN_SPEC='m/test/src/c1Spec.scala'\n")
      r3 <- run(List("c1"))
      _ = assertEquals(r3, Left("task c1 has no reference patch"))
      _ = os.remove(c.proot / "head-baseline.json")
      r4 <- run(List("c1"))
      _ = assertEquals(r4, Left(s"no ${c.proot / "head-baseline.json"} — run score.py --baseline first"))
      _ = os.remove.all(c.h / ".git")
      r5 <- run(List("c1"))
    yield assertEquals(r5, Left(s"no ${c.h} — run warm-cache.sh first"))
    ).guarantee(IO.blocking(os.remove.all(c.tmp)))

  // ---------------------------------------------------------------- the reference erosion line's numbers (python json.dumps)

  test("ErosionFigure.pyJson renders as erosion.py's json.dumps"):
    assertEquals(ErosionFigure(0.0, 0, 0.0).pyJson, """{"methods": 0, "total_mass": 0.0, "erosion": 0.0}""")
    assertEquals(ErosionFigure(0.1235, 12, 345.679).pyJson, """{"methods": 12, "total_mass": 345.679, "erosion": 0.1235}""")
    assertEquals(List(1.0, 0.0001, 0.00012, 1e-05, 12.346, 123456.0, 1e16, 1.5e17, -2.5).map(Json.pyFloat),
      List("1.0", "0.0001", "0.00012", "1e-05", "12.346", "123456.0", "1e+16", "1.5e+17", "-2.5"))
