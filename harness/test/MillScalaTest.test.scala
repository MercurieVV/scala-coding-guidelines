package harness.target

import cats.effect.IO
import munit.CatsEffectSuite
import scala.concurrent.duration.*

class MillScalaTest extends CatsEffectSuite:
  override def munitIOTimeout: Duration = 60.seconds

  private val fx: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .map(_ / "test" / "fixtures" / "mill").find(os.exists(_)).getOrElse(fail("fixtures not found"))
  private def fixture(name: String): String = os.read(fx / name)

  private def spec(mill: String = "./mill") = harness.config.TargetSpec(
    adapter = "mill-scala", repo = os.pwd, commit = "c", holdoutRepo = os.pwd, holdoutCommit = "h", holdoutExpected = 7,
    excludedRegression = List("backend.opencl.test", "holdout.test", "bench.test"),
    precompile = List("__.compile", "+", "__.test.compile"), mill = mill, settingsSource = os.pwd, allTasks = Nil
  )
  private val t: Target = MillScala(spec())

  // ---- weaver_counts
  test("counts"):
    val log = "\u001b[32m+ \u001b[0ma passes 1ms\n\u001b[31m- \u001b[0mb fails 2ms\n+ c 3ms\n"
    assertEquals(t.testCounts(log), Counts(2, 1, true))

  test("anchoring and duration"):
    val log = "+ no duration here\n  + indented 1ms\n- also no duration\ndiff - x\n+ ok 4ms\n- bad 1s\n"
    assertEquals(t.testCounts(log), Counts(1, 1, true))

  test("compile failure"):
    assertEquals(t.testCounts("[error] x.test.compile Compilation failed\n"), Counts(0, 0, false))

  // ---- suite_fq
  test("suite_fq"):
    val s = "package com.a.b\n\nimport x\n\nobject FooSpec extends SimpleIOSuite {\n}\n"
    assertEquals(t.suiteName(s), Some("com.a.b.FooSpec"))
    assertEquals(t.suiteName("class X"), None)

  // ---- hidden_counts
  test("hidden counts only that suite"):
    val log = fixture("hidden-cut.txt")
    assertEquals(t.hiddenCounts(log, "com.github.mercurievv.skreeep2.plugin.MidiSynthSpec"), Counts(0, 1, true))
    assertEquals(t.hiddenCounts(log, "com.github.mercurievv.skreeep2.plugin.HostEventsSpec"), Counts(2, 0, true))
    assertEquals(t.hiddenCounts(log, "com.nope.Spec"), Counts(0, 0, true))

  private val Mods = List("control.test", "plugin.test", "core.test", "bench.test")
  private val All = List("core", "backpressure", "control", "plugin", "bench")

  // ---- parse_mill_failures
  test("parse mill failures real format"):
    val r = t.parseFailures(fixture("regress-failed.txt"), Mods, All)
    assertEquals(r.tests, Set("control.test", "plugin.test"))
    assertEquals(r.compile, Set("<compile:core>"))
    assertEquals((r.other, r.failed), (Nil, 4))

  test("module without test module failing to compile"):
    val r = t.parseFailures("[error] backpressure.compile task failed\n", Mods, All)
    assertEquals((r.compile, r.other), (Set("<compile:backpressure>"), Nil))

  test("build file error"):
    for line <- List("[error] build.mill:12:3: not found: value x", "[error] /r/x/package.mill:7:1: boom") do
      val r = t.parseFailures(line + "\n", Mods, All)
      assertEquals((r.compile, r.other), (Set("<build>"), Nil), line)

  test("real broken build regression log"):
    val r = t.parseFailures(fixture("build-broken-regress.txt"), Mods, All)
    assertEquals((r.tests, r.compile, r.other), (Set.empty[String], Set("<build>"), Nil))

  // ---- build_broken
  test("build_broken detects real meta build failures"):
    for f <- List("build-broken-resolve.txt", "build-broken-package-resolve.txt", "build-broken-regress.txt") do
      assert(t.buildBroken(fixture(f)), f)
    assert(t.buildBroken("[error] compile Compilation failed\n"))
    assert(t.buildBroken("[2] [error] build.mill:9:19\n"))
    for f <- List("regress-failed.txt", "baseline-excerpt.txt", "hidden-cut.txt") do
      assert(!t.buildBroken(fixture(f)), f)
    assert(!t.buildBroken("resolve exploded\n"))

  test("compilation failed without a module"):
    val r = t.parseFailures("[error] Compilation failed\n", Mods, All)
    assertEquals((r.compile, r.other), (Set("<compile>"), Nil))

  test("parse mill failures unattributed error line"):
    val r = t.parseFailures("[error] java.lang.OutOfMemoryError: Java heap space\n[error] 1 tests failed:\n", Mods, All)
    assertEquals((r.tests, r.compile), (Set.empty[String], Set.empty[String]))
    assertEquals(r.other, List("java.lang.OutOfMemoryError: Java heap space"))

  test("parse mill failures clean"):
    val r = t.parseFailures("+ a 1ms\n[10/10] mill x 3s\n", Mods, All)
    assertEquals((r.tests, r.compile, r.other, r.failed), (Set.empty[String], Set.empty[String], Nil, 0))

  private val Excerpt = fixture("baseline-excerpt.txt")

  test("real baseline excerpt counts one failure"):
    assertEquals(t.testCounts(Excerpt), Counts(12, 1, true))
    assertEquals(t.hiddenCounts(Excerpt, "com.github.mercurievv.skreeep2.UnderrunSpec"), Counts(4, 1, true))

  test("real baseline excerpt failures attributed"):
    val r = t.parseFailures(Excerpt, List("bench.test", "control.midi.test"), List("bench"))
    assertEquals(r.tests, Set("bench.test"))
    assertEquals((r.compile, r.other), (Set.empty[String], Nil))

  test("failures block not counted"):
    val log = "Running Test Class a.B\n+ ok 1ms\n- bad 1ms\n*****FAILURES*****\n- bad 1ms\nRunning Test Class a.C\n+ fine 1ms\n"
    assertEquals(t.testCounts(log), Counts(2, 1, true))
    assertEquals(t.hiddenCounts(log, "a.B"), Counts(1, 1, true))

  // ---- suite_passes, module_passes
  test("suite_passes and module attribution"):
    val p = t.suitePasses(Excerpt)
    assertEquals(p, Map(
      "com.github.mercurievv.skreeep2.UnderrunSpec" -> 4,
      "com.github.mercurievv.skreeep2.control.midi.ControlSurfaceSpec" -> 3,
      "com.github.mercurievv.skreeep2.control.midi.McuSpec" -> 2,
      "com.github.mercurievv.skreeep2.wiring.WiringDetailsSpec" -> 3))
    val by = Map(
      "bench.test" -> List("com.github.mercurievv.skreeep2.UnderrunSpec"),
      "control.midi.test" -> List("com.github.mercurievv.skreeep2.control.midi.ControlSurfaceSpec", "com.github.mercurievv.skreeep2.control.midi.McuSpec"))
    assertEquals(t.modulePasses(p, by), Map("bench.test" -> 4, "control.midi.test" -> 5))

  test("truncated suite names"):
    val log = "Running Test Class a.b.LongNameSp...\n+ x 1ms\n"
    assertEquals(t.hiddenCounts(log, "a.b.LongNameSpec"), Counts(1, 0, true))
    assertEquals(t.modulePasses(t.suitePasses(log), Map("m.test" -> List("a.b.LongNameSpec"))), Map("m.test" -> 1))

  // ---- other_suites / all_suites
  private def withTree[A](f: os.Path => IO[A]): IO[A] =
    IO.blocking(os.temp.dir()).flatMap(d => f(d).guarantee(IO.blocking(os.remove.all(d))))

  private def seedTree(d: os.Path): Unit =
    val p = d / "signals" / "test" / "src" / "pk"
    os.write(p / "NoiseSpec.scala", "package pk\nobject NoiseSpec extends SimpleIOSuite\n", createFolders = true)
    os.write(p / "Other.scala", "package pk\nobject OtherSpec extends IOSuite\nobject Helper\n")

  test("other_suites"):
    withTree { d =>
      IO(seedTree(d)) >> t.otherSuites(d, "signals.test", "pk.NoiseSpec").assertEquals(List("pk.OtherSpec"))
    }

  test("all_suites"):
    withTree { d =>
      IO(seedTree(d)) >> t.allSuites(d, "signals.test").assertEquals(List("pk.NoiseSpec", "pk.OtherSpec"))
    }

  // ---- first_clean_compile predicate
  test("isCompileCommand"):
    assert(t.isCompileCommand("./mill signals.compile"))
    assert(t.isCompileCommand("cd x && ./mill --no-daemon __.compile"))
    assert(!t.isCompileCommand("./mill signals.test"))
    assert(!t.isCompileCommand("sbt compile"))

  test("isTestSource: Mill's test/src layout (GA tampering)"):
    assert(t.isTestSource("x/test/src/A.scala"))
    assert(t.isTestSource("test/src/A.scala"))
    assert(!t.isTestSource("x/src/A.scala"))
    assert(!t.isTestSource("x/test/resources/a.txt"))

  test("isBuildFile and outDirs"):
    assert(t.isBuildFile("build.mill"))
    assert(t.isBuildFile("x/package.mill"))
    assert(!t.isBuildFile("x/Foo.scala"))
    assert(t.outDirs("out"))
    assert(t.outDirs("out-agent"))
    assert(!t.outDirs("outer"))
    assert(!t.outDirs("src"))

  // ---- _chain and argument builders
  test("joinTasks"):
    assertEquals(t.joinTasks(List(List("a.test"), List("b.testOnly", "x", "y"), List("c.test"))),
      List("a.test", "+", "b.testOnly", "x", "y", "+", "c.test"))
    assertEquals(t.joinTasks(Nil), Nil)

  test("argument builders"):
    assertEquals(t.hiddenRunArgs("signals.test", "pk.NoiseSpec"), List("signals.test.testOnly", "pk.NoiseSpec"))
    assertEquals(t.regressionEntry("a.test", None), List("a.test"))
    assertEquals(t.regressionEntry("a.test", Some(List("p.X", "p.Y"))), List("a.test.testOnly", "p.X", "p.Y"))
    assertEquals(t.holdoutArgs, List("holdout.test"))
    assertEquals(t.holdoutEnv(os.root / "h"), Map("DSH_HOLDOUT_DIR" -> Some("/h")))
    assertEquals(t.precompileArgs, List("__.compile", "+", "__.test.compile"))
    assertEquals(t.compileAllArgs, List("__.compile"))

  test("Target.of"):
    assert(Target.of(spec()).isRight)
    assertEquals(Target.of(spec().copy(adapter = "x")).left.toOption, Some("target.adapter: unknown target adapter x (one of mill-scala)"))

  // ---- run_mill / test_modules / all_modules with a fake mill
  private def fakeTree(d: os.Path, script: String): MillScala =
    os.write.over(d / "mill", script, perms = "rwxr-xr-x")
    MillScala(spec())

  test("runBuild passes GA flags, MILL_OUTPUT_DIR and extra env"):
    withTree { d =>
      val m = fakeTree(d, "#!/bin/sh\necho \"args=$*\"\necho \"out=$MILL_OUTPUT_DIR holdout=$DSH_HOLDOUT_DIR\"\n")
      m.runBuild(d, List("x.test"), "out-score", d / "l.log", m.holdoutEnv(d), keepGoing = true).map { r =>
        assertEquals(r.rc, 0)
        val log = os.read(d / "l.log")
        assert(log.contains("args=--no-daemon -j 1 -k --ticker false --color false x.test"), log)
        assert(log.contains(s"out=out-score holdout=$d"), log)
      }
    }

  test("runBuild timeout appends the GA TIMEOUT line"):
    withTree { d =>
      val m = fakeTree(d, "#!/bin/sh\necho started\nsleep 30\n")
      m.runBuild(d, List("x"), "out", d / "l.log", cap = Some(1)).map { r =>
        assertEquals((r.rc, r.timedOut), (124, true))
        assert(os.read(d / "l.log").endsWith("\nscore.py: TIMEOUT after 1 s (counted as FAIL)\n"))
      }
    }

  test("testModules filters to test modules minus excluded"):
    withTree { d =>
      val m = fakeTree(d, "#!/bin/sh\nprintf 'signals.test\\nholdout.test\\n\\033[32mcontrol.test\\033[0m\\nbench.test\\ncore\\n'\n")
      m.testModules(d, "out", d / "l.log").assertEquals(Right(List("signals.test", "control.test")))
    }

  test("testModules: build broken, other failure, none"):
    withTree { d =>
      for
        a <- fakeTree(d, "#!/bin/sh\necho '[error] build.mill:9:19'\nexit 1\n").testModules(d, "out", d / "l.log")
        b <- fakeTree(d, "#!/bin/sh\necho 'resolve exploded'\nexit 1\n").testModules(d, "out", d / "l.log").attempt
        c <- fakeTree(d, "#!/bin/sh\necho 'core'\n").testModules(d, "out", d / "l.log").attempt
      yield
        assertEquals(a, Left(BuildBroken("[error] build.mill:9:19")))
        assertEquals(b.left.toOption.map(_.getMessage), Some("resolve __.test failed: resolve exploded"))
        assertEquals(c.left.toOption.map(_.getMessage), Some("no test modules"))
    }

  test("allModules"):
    withTree { d =>
      for
        ok <- fakeTree(d, "#!/bin/sh\nprintf 'core\\nplugin.sub\\n'\n").allModules(d, "out", d / "l.log")
        bad <- fakeTree(d, "#!/bin/sh\necho boom\nexit 1\n").allModules(d, "out", d / "l.log").attempt
      yield
        assertEquals(ok, Right(List("core", "plugin.sub")))
        assertEquals(bad.left.toOption.map(_.getMessage), Some("resolve __:Module failed: boom"))
    }
