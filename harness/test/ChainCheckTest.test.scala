package harness.run

import cats.effect.IO
import munit.CatsEffectSuite
import harness.config.*
import harness.core.{ErosionFigure, ErosionMeasure}
import harness.target.MillScala

/** GA tests/test_chaincheck.py (6 cases): Pure (own_passed, earlier_status, chain_regressions_ignore_na) and Main
  * (statuses_and_record, infrastructure_error_is_recorded, erosion_failure_keeps_the_regression_count). GA's fake Mill
  * launcher script becomes [[FakeMill]]: a spec file containing BROKEN does not compile, FAILS fails one test.
  */
class ChainCheckTest extends CatsEffectSuite:
  private val harnessDir: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .find(d => os.exists(d / "experiments" / "gate-ab.conf")).getOrElse(fail("harness dir not found"))
  private val exp0: Experiment = Load.experiment(harnessDir / "experiments" / "gate-ab.conf").fold(fail(_), identity)

  // ---- class Pure

  private def j(s: String): ujson.Value = ujson.read(s)

  test("own_passed"):
    assert(ChainCheck.ownPassed(j("""{"hidden_compiled": true, "hidden_pass_ratio": 1.0}""")))
    assert(!ChainCheck.ownPassed(j("""{"hidden_compiled": true, "hidden_pass_ratio": 0.67}""")))
    assert(!ChainCheck.ownPassed(j("""{"hidden_compiled": false, "hidden_pass_ratio": 1.0}""")))

  test("earlier_status"):
    assertEquals(ChainCheck.earlierStatus(compiled = true, 3, 0, timedOut = false), "pass")
    assertEquals(ChainCheck.earlierStatus(compiled = true, 2, 1, timedOut = false), "fail")
    assertEquals(ChainCheck.earlierStatus(compiled = false, 0, 0, timedOut = false), "fail") // spec no longer compiles
    assertEquals(ChainCheck.earlierStatus(compiled = true, 3, 0, timedOut = true), "fail") // timeout
    assertEquals(ChainCheck.earlierStatus(compiled = true, 0, 0, timedOut = false), "fail") // nothing ran

  test("chain_regressions_ignore_na"):
    assertEquals(ChainCheck.chainRegressions(List("fail", "na", "pass")), 1)

  // ---- class Main

  final case class Call(tree: os.Path, args: Seq[String], out: String, cap: Option[Int], hadAgentOut: Boolean)

  final class FakeMill(ts: TargetSpec) extends MillScala(ts):
    val calls = scala.collection.mutable.ListBuffer.empty[Call]
    override def runBuild(tree: os.Path, a: Seq[String], outDir: String, log: os.Path, env: Map[String, Option[String]],
                          cap: Option[Int], keepGoing: Boolean): IO[ProcResult] = IO.blocking {
      calls += Call(tree, a, outDir, cap, os.exists(tree / "out-agent"))
      val specs = os.walk(tree / "m" / "test").filter(os.isFile).map(os.read)
      val out = new StringBuilder
      val rc =
        if specs.exists(_.contains("BROKEN")) then { out.append("Compilation failed\n"); 1 }
        else
          out.append(s"Running Test Class ${a.last}\n")
          if specs.exists(_.contains("FAILS")) then { out.append("- one 1ms\n"); 1 } else { out.append("+ one 1ms\n"); 0 }
      os.write.over(log, out.toString)
      ProcResult(rc, timedOut = false)
    }

  private val Fig = ErosionFigure(erosion = 0.0, methods = 1, mass = 2.0)

  final class Env:
    val tmp: os.Path = os.temp.dir(prefix = "chaincheck-")
    val root: os.Path = tmp / "root"
    val exp: Experiment = exp0.copy(root = root, tasksDir = tmp / "tasks")
    val rd: os.Path = root / "runs" / "t3-u-1"
    val target = new FakeMill(exp.target)
    os.makeDir.all(rd / "repo" / "m" / "test" / "src")
    os.write(rd / "repo" / "out-agent" / "junk", "x", createFolders = true)
    val earlier: List[os.Path] =
      List(("t1", "object S1 extends SimpleIOSuite", true), ("t2", "object S2 extends SimpleIOSuite // BROKEN", true),
        ("t9", "object S9 extends SimpleIOSuite", false)).map { (task, body, passed) =>
        val ed = root / "runs" / s"$task-u-1"
        os.write(ed / "score.json", ujson.write(ujson.Obj("task" -> task, "hidden_compiled" -> true, "hidden_pass_ratio" -> (if passed then 1.0 else 0.5))), createFolders = true)
        os.write(root / "base" / task / "hidden" / "m" / "test" / "src" / s"${task}Spec.scala", s"package p\n$body\n", createFolders = true)
        os.write(exp.tasksDir / task / "task.env", s"TASK_MODULE='m.test'\nHIDDEN_SPEC='m/test/src/${task}Spec.scala'\nCUT_FILES=''\nFEATURE='f'\n", createFolders = true)
        ed
      }
    def check(erosion: ErosionMeasure = (_, _) => IO.pure(Right(Fig))): IO[ujson.Obj] = ChainCheck.applyWith(exp, rd, earlier, target, erosion)

  private def withEnv[A](f: Env => IO[A]): IO[A] =
    IO.blocking(Env()).flatMap(e => f(e).guarantee(IO.blocking(os.remove.all(e.tmp))))

  test("statuses_and_record"):
    withEnv { e =>
      e.check().map { out =>
        assertEquals(out("earlier").arr.map(x => (x("task").str, x("status").str)).toList, List(("t1", "pass"), ("t2", "fail"), ("t9", "na")))
        assertEquals(out("chain_regressions").num, 1.0)
        assertEquals(out("erosion"), ujson.Obj("methods" -> 1, "total_mass" -> 2.0, "erosion" -> 0.0): ujson.Value)
        assertEquals(out("error"), ujson.Null)
        assertEquals(out("run_id").str, "t3-u-1")
        assertEquals(out.value.keys.toList, List("run_id", "earlier", "chain_regressions", "erosion", "erosion_error", "error"))
        assertEquals(ujson.read(os.read(e.rd / "chain.json")), out: ujson.Value)
        assert(!os.exists(e.rd / "chain-tree"))
        assert(!os.exists(e.rd / "repo" / "m" / "test" / "src" / "t1Spec.scala")) // repo untouched
        // one spec at a time, in the scoring out dir, never with the agent's out-agent copied in
        assertEquals(e.target.calls.map(c => (c.tree, c.out, c.cap, c.hadAgentOut, c.args.takeRight(2))).toList,
          List("t1", "t2").map(t => (e.rd / "chain-tree", "out-chain", Some(e.exp.testCapSeconds), false, Seq("m.test.testOnly", s"p.S${t.last}"))))
        assert(os.read(e.rd / "chain-t1.log").contains("+ one"))
      }
    }

  test("chain.json is written like python json.dump(indent=2) with float repr"):
    withEnv { e =>
      e.check().map { _ =>
        val text = os.read(e.rd / "chain.json")
        assert(text.startsWith("{\n  \"run_id\": \"t3-u-1\",\n  \"earlier\": [\n    {\n      \"task\": \"t1\",\n      \"status\": \"pass\",\n      \"passed\": 1,\n      \"failed\": 0,\n      \"timed_out\": false\n    },"), text)
        assert(text.contains("\"chain_regressions\": 1,\n  \"erosion\": {\n    \"methods\": 1,\n    \"total_mass\": 2.0,\n    \"erosion\": 0.0\n  },\n  \"erosion_error\": null,\n  \"error\": null\n}"), text)
        assert(!text.endsWith("\n"))
      }
    }

  test("infrastructure_error_is_recorded"):
    withEnv { e =>
      os.remove.all(e.root / "base" / "t1")
      e.check().map { out =>
        assert(out("error") != ujson.Null)
        assert(out("error").str.startsWith("ScoreError: hidden spec "), out("error").str)
        assert(os.exists(e.rd / "chain.json"))
        assertEquals(out("chain_regressions"), ujson.Null) // final review I3: a partial check is not 0 regressions
        assert(!os.exists(e.rd / "chain-tree"))
      }
    }

  test("erosion_failure_keeps_the_regression_count"):
    withEnv { e =>
      e.check((_, _) => IO.pure(Left("parse"))).map { out =>
        assertEquals(out("error"), ujson.Null)
        assertEquals(out("chain_regressions").num, 1.0)
        assertEquals(out("erosion"), ujson.Null)
        assert(out("erosion_error").str.contains("parse"), out("erosion_error").str)
        assertEquals(out("erosion_error").str, "ErosionError: parse")
      }
    }

  test("erosion is measured on the run's repo against <root>/head"):
    withEnv { e =>
      var seen = Option.empty[(os.Path, String)]
      e.check((t, b) => IO { seen = Some((t, b)); Right(Fig) }).map(_ => assertEquals(seen, Some((e.rd / "repo", (e.root / "head").toString))))
    }

  test("a missing score.json is recorded as FileNotFoundError"):
    withEnv { e =>
      os.remove(e.earlier.head / "score.json")
      e.check().map { out =>
        assert(out("error").str.startsWith("FileNotFoundError: "), out("error").str)
        assertEquals(out("chain_regressions"), ujson.Null)
      }
    }

  // PARITY.md (Task 13): GA records `JSONDecodeError: <python json's message>`; the type name is kept, the message is ujson's
  test("an unparsable score.json is recorded as JSONDecodeError with ujson's message"):
    withEnv { e =>
      os.write.over(e.earlier.head / "score.json", "{")
      e.check().map { out =>
        val err = out("error").str
        assert(err.startsWith("JSONDecodeError: ") && !err.contains("Expecting"), err)
        assertEquals(out("chain_regressions"), ujson.Null)
      }
    }

  // PARITY.md (Task 13): GA catches every Exception as `<type name>: <message>`; any other non-fatal JVM failure is
  // recorded the same way under its JVM class name
  test("any other failure is recorded as `<class>: <message>`"):
    assertEquals(ChainCheck.pyError(new IllegalStateException("odd")), "IllegalStateException: odd")
