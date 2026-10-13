package harness.run

import cats.effect.{Deferred, IO}
import munit.CatsEffectSuite
import harness.agent.{Agent, ClaudeCode, Generic}
import harness.config.*
import harness.core.{ClaudeTranscript, ToolCall}
import harness.target.{MillScala, ScoreError}

/** GA tests/test_score.py: every case that drives `main` / `baseline` (classes MainWithFakeMill, BaselineMode), the two
  * `first_clean_compile` cases, and Review Focus 4 (an old run.json without agent / model). GA's fake Mill launcher
  * (FAKE_MILL, test_score.py:412-471) becomes [[FakeMill]], a Target whose runBuild prints the same scripted logs.
  */
class ScoreTest extends CatsEffectSuite:
  private val harnessDir: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .find(d => os.exists(d / "experiments" / "gate-ab.conf")).getOrElse(fail("harness dir not found"))
  private val exp0: Experiment = Load.experiment(harnessDir / "experiments" / "gate-ab.conf").fold(fail(_), identity)
  private val fx: os.Path = harnessDir / "test" / "fixtures"
  private def transcript(name: String): String = os.read(fx / "transcripts" / name)

  private val Spec = "package com.github.mercurievv.skreeep2\n\nobject NoiseSpec extends SimpleIOSuite { \"secret-name\" }\n"
  private val Fq = "com.github.mercurievv.skreeep2.NoiseSpec"
  private val SpecPath = "signals/test/src/com/github/mercurievv/skreeep2/NoiseSpec.scala"

  final case class Call(cwd: os.Path, args: Seq[String], out: String, holdout: Option[String], cap: Option[Int], keepGoing: Boolean)

  /** GA FAKE_MILL; `modes` stands for its FAKE_* environment variables. A `hang` mode times out when the cap is 1 s
    * (GA tests set GA_TEST_CAP / GA_REGRESS_CAP=1 for them); the timeout line is the one MillScala.runBuild appends.
    */
  final class FakeMill(ts: TargetSpec, modes: Map[String, String], beforeCall: os.Path => Unit) extends MillScala(ts):
    val calls: scala.collection.mutable.ListBuffer[Call] = scala.collection.mutable.ListBuffer.empty
    private def mode(k: String): Option[String] = modes.get(k)

    override def runBuild(tree: os.Path, a: Seq[String], outDir: String, log: os.Path, env: Map[String, Option[String]],
                          cap: Option[Int], keepGoing: Boolean): IO[ProcResult] = IO.blocking {
      beforeCall(tree)
      calls += Call(tree, a, outDir, env.get("DSH_HOLDOUT_DIR").flatten, cap, keepGoing)
      val out = new StringBuilder
      def p(s: String): Unit = out.append(s).append('\n')
      def hang: Boolean = cap.contains(1)
      val inTree = tree.toString.contains("score-tree")
      val rc: Int =
        if inTree && mode("FAKE_BUILD_BROKEN").isDefined then { out.append(os.read(os.Path(mode("FAKE_BUILD_BROKEN").get))); 1 }
        else if a.contains("resolve") then
          if mode("FAKE_RESOLVE_FAIL").isDefined then { p("resolve exploded"); 1 }
          else if a.contains("__:Module") then
            if mode("FAKE_ALL_RESOLVE_FAIL").isDefined then { p("resolve exploded"); 1 }
            else { p(mode("FAKE_ALL_MODULES").getOrElse("core,signals,control,backpressure").split(",").mkString("\n")); 0 }
          else { p(mode("FAKE_MODULES").getOrElse("signals.test,holdout.test,control.test,backend.opencl.test,bench.test").split(",").mkString("\n")); 0 }
        else if a.last == "holdout.test" then
          if !env.get("DSH_HOLDOUT_DIR").flatten.exists(d => os.isDir(os.Path(d))) then 9
          else mode("FAKE_HOLDOUT").getOrElse("pass") match
            case "millfail" => p("java.lang.OutOfMemoryError: boom"); 1
            case "hang" if hang => 124
            case _ => (0 until 7).foreach(i => p(s"+ h$i 1ms")); 0
        else if !keepGoing && a.contains("signals.test.testOnly") && a(a.indexOf("signals.test.testOnly") + 1).endsWith("NoiseSpec") then
          val fq = a(a.indexOf("signals.test.testOnly") + 1)
          mode("FAKE_HIDDEN").getOrElse("pass") match
            case "millfail"  => p("java.lang.OutOfMemoryError: boom"); 1
            case "norun"     => p("No tests were executed"); 0
            case "nocompile" => p("[error] Compilation failed"); 1
            case "hang" if hang => p("Running Test Class " + fq); p("+ a 1ms"); 124
            case h =>
              p("Running Test Class com.other.OtherSuite"); p("- unrelated 1ms")
              p("Running Test Class " + fq)
              h match
                case "fail"  => p("+ a 1ms"); p("- b 1ms"); p("*****FAILURES*****"); p("- b 1ms"); 1
                case "class" => p("+ a 1ms"); p("- Unexpected failure 0ms"); 1
                case _       => p("+ a 1ms"); p("+ b 1ms"); 0
        else if keepGoing then
          mode("FAKE_REG_MODE") match
            case Some("silent") => p("weird crash"); 1
            case Some("notests") => 0
            case Some("hang") if hang => 124
            case rm =>
              val mods = scala.collection.mutable.ListBuffer.empty[List[String]]
              var cur = List.empty[String]
              for x <- a do
                if x == "+" then { mods += cur; cur = Nil }
                else if x.endsWith(".test") || x.contains(".testOnly") || cur.nonEmpty then cur = cur :+ x
              if cur.nonEmpty then mods += cur
              val failing = mode(if inTree then "FAKE_FAIL_MODS" else "FAKE_HEAD_FAIL_MODS").getOrElse("").split(",").toSet
              var nf = 0
              for c <- mods do
                val mod = c.head.replace(".testOnly", "")
                p("Running Test Class pk." + mod.replace(".", "_") + "Spec"); p("+ ok 1ms")
                if failing(mod) then { p(s"[3] [error] $mod.testForked 1 tests failed:"); nf += 1 }
              rm match
                case Some("oom")              => p("[error] java.lang.OutOfMemoryError: Java heap space"); nf = math.max(nf, 1)
                case Some("compile")          => p("[error] core.compile boom"); nf = math.max(nf, 1)
                case Some("compile-nomodule") => p("[error] backpressure.compile task failed"); nf = math.max(nf, 1)
                case Some("build")            => p("[error] build.mill:3:1: boom"); nf = math.max(nf, 1)
                case _                        => ()
              if nf > 0 then { p(s"9/9, $nf FAILED] mill 3s"); 1 } else 0
        else 0
      if rc == 124 then p(s"\nscore.py: TIMEOUT after ${cap.getOrElse(600)} s (counted as FAIL)")
      os.write.over(log, out.toString)
      ProcResult(rc, rc == 124)
    }

  /** GA FakeBase.setUp. */
  final class Env:
    val tmp: os.Path = os.temp.dir()
    val root: os.Path = tmp / "ga"
    val rd: os.Path = root / "runs" / "noise-g-1"
    os.write(rd / "repo" / "out" / "junk", "x", createFolders = true)
    os.makeDir(rd / "repo" / "out-score")
    os.write(rd / "repo" / "a.txt", "a")
    os.write(root / "base" / "noise" / "hidden" / os.RelPath(SpecPath), Spec, createFolders = true)
    os.makeDir(root / "head")
    def writeBaseline(json: String): Unit = os.write.over(root / "head-baseline.json", json)
    writeBaseline("""{"modules": {"signals.test": true, "control.test": true}, "passes": {"signals.test": 1, "control.test": 1}}""")
    val ho: os.Path = tmp / "holdout"
    os.write(ho / "f", "x", createFolders = true)
    private def g(args: String*): Unit =
      os.proc(Seq("git", "-C", ho.toString, "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false") ++ args)
        .call(stderr = os.Pipe)
    g("init", "-q"); g("add", "-A"); g("commit", "-qm", "h")
    def writeRun(json: ujson.Obj): Unit = os.write.over(rd / "run.json", ujson.write(json))
    val run0: ujson.Obj = ujson.Obj("run_id" -> "noise-g-1", "task" -> "noise", "arm" -> "g", "rep" -> 1,
      "start_epoch" -> 100, "end_epoch" -> 160, "exit_code" -> 0, "timed_out" -> false)
    writeRun(run0)
    def patchRun(kv: (String, ujson.Value)*): Unit =
      val r = ujson.read(os.read(rd / "run.json")).obj
      kv.foreach((k, v) => r(k) = v)
      writeRun(ujson.Obj.from(r))
    def setTranscript(text: String): Unit = os.write.over(rd / "transcript.jsonl", text)
    setTranscript(transcript("real-init-isolated.jsonl") + transcript("clean.jsonl"))
    os.write(rd / "final.diff", "")
    val exp: Experiment = exp0.copy(root = root, testCapSeconds = 600, regressCapSeconds = 1200,
      target = exp0.target.copy(holdoutRepo = ho, holdoutCommit = "HEAD"))
    val agentNames: scala.collection.mutable.ListBuffer[String] = scala.collection.mutable.ListBuffer.empty
    def agentOf(e: Experiment)(name: String): Either[String, Agent] =
      agentNames += name
      e.agents.get(name).toRight(s"no agent $name").map(s => ClaudeCode(s, tmp / "home", Some((tmp / "cfg").toString)))
    var seenErrors: List[ujson.Value] = Nil
    private def spy(tree: os.Path): Unit =
      val sj = tree / os.up / "score.json"
      if tree.last == "score-tree" && os.exists(sj) then seenErrors :+= ujson.read(os.read(sj))("error")
    var target: FakeMill = FakeMill(exp.target, Map.empty, spy)
    def calls: List[Call] = target.calls.toList
    def regCalls: List[Call] = calls.filter(_.keepGoing)
    def score(modes: (String, String)*): IO[ujson.Obj] = scoreE(exp, modes*)
    def scoreE(e: Experiment, modes: (String, String)*): IO[ujson.Obj] =
      target = FakeMill(e.target, modes.toMap, spy)
      Score.withAdapters(e, rd, target, agentOf(e)).flatMap { rec =>
        IO.blocking(assertEquals(ujson.read(os.read(rd / "score.json")), rec: ujson.Value, "score.json is the returned record")).as(rec)
      }
    def baseline(modes: (String, String)*): IO[Map[String, Boolean]] =
      target = FakeMill(exp.target, modes.toMap, spy)
      Score.baselineWith(exp, target)
    def headBaseline: ujson.Value = ujson.read(os.read(root / "head-baseline.json"))

  private def withEnv[A](f: Env => IO[A]): IO[A] =
    IO(Env()).flatMap(e => f(e).guarantee(IO.blocking(os.remove.all(e.tmp))))

  private def err(s: ujson.Obj): Option[String] = s("error").strOpt
  private def errStr(s: ujson.Obj): String = s("error").strOpt.getOrElse(fail(s"error is null: $s"))
  private def strs(v: ujson.Value): List[String] = v.arr.map(_.str).toList

  // ---- class Transcript: first_clean_compile (Task 7 deferred these two; they need the transcript fold)

  private val mill = MillScala(exp0.target)
  test("first_clean_compile"):
    val t = ClaudeTranscript.parse(transcript("clean.jsonl").linesIterator)
    assertEquals(Score.firstCleanCompile(t.toolCalls, mill.isCompileCommand), Some(1))

  test("compile_with_error_not_clean"):
    val c = ToolCall("Bash", ClaudeTranscript.kindOf("Bash"), ujson.Obj("command" -> "./mill signals.compile"), "[error] boom", false, 1)
    assertEquals(Score.firstCleanCompile(List(c), mill.isCompileCommand), None)

  test("first_clean_compile: only shell calls count, errors skipped"):
    def call(name: String, cmd: String, err: Boolean, turn: Int) =
      ToolCall(name, ClaudeTranscript.kindOf(name), ujson.Obj("command" -> cmd), "", err, turn)
    val calls = List(call("Read", "./mill a.compile", false, 1), call("Bash", "./mill a.compile", true, 2), call("Bash", "./mill a.compile", false, 3))
    assertEquals(Score.firstCleanCompile(calls, mill.isCompileCommand), Some(3))

  // ---- class MainWithFakeMill

  test("solved_and_hidden_runs_only_its_suite"):
    withEnv { e =>
      e.score().map { s =>
        assert(s("solved").bool, ujson.write(s))
        assertEquals(s("hidden_pass_ratio").num, 1.0)
        assertEquals((s("turns").num, s("wall_seconds").num, strs(s("regressions"))), (2.0, 60.0, Nil))
        assert(s("holdout_passed").bool); assert(!s("false_done").bool); assertEquals(err(s), None)
        assertEquals(s("first_clean_compile_turn").num, 1.0)
        val st = e.rd / "score-tree"
        assert(!os.exists(st / "out")); assert(!os.exists(st / "out-score")); assert(os.exists(st / "a.txt"))
        assert(e.calls.map(_.args.takeRight(2)).contains(Seq("signals.test.testOnly", Fq)))
        val reg = e.regCalls
        assertEquals(reg.length, 1)
        assert(reg.head.args.contains("control.test")); assert(!reg.head.args.contains("holdout.test"))
        assert(!reg.head.args.contains("backend.opencl.test")); assert(!reg.head.args.exists(_.startsWith("signals.test")))
        // GA: `-j 1 -k` on the command line; here keepGoing (MillScala.runBuild adds -k, MillScalaTest) and the regress cap
        assertEquals((reg.head.keepGoing, reg.head.cap), (true, Some(1200)))
        assert(e.calls.forall(c => c.cwd == e.rd / "score-tree" && c.out == "out-score"))
        val hidden = e.calls.find(_.args.contains(Fq)).get
        assertEquals(hidden.cap, Some(600))
        val ho = e.calls.find(_.args.last == "holdout.test").get
        assertEquals(ho.cap, Some(600))
        assert(ho.holdout.exists(_.contains("gate-holdout-")), ho)
        assert(!ho.holdout.exists(d => os.exists(os.Path(d))), "temp holdout removed")
      }
    }

  test("score.json: GA fields in GA order"):
    withEnv { e =>
      e.score().map { s =>
        assertEquals(s.value.keys.toList, List("run_id", "task", "arm", "rep", "solved", "hidden_pass_ratio",
          "hidden_compiled", "holdout_passed", "holdout_failed", "regressions", "regressed_modules", "regression_count",
          "test_tampering", "hardcoding_suspects", "build_edits", "false_done", "timed_out", "contaminated", "contamination",
          "gate_blocks", "wall_seconds", "turns", "input_tokens", "output_tokens", "cache_read_tokens", "cost_usd",
          "first_clean_compile_turn", "score_timeouts", "error"))
        assertEquals((s("run_id").str, s("task").str, s("arm").str, s("rep").num), ("noise-g-1", "noise", "g", 1.0))
        assertEquals((s("input_tokens").num, s("output_tokens").num, s("cache_read_tokens").num, s("cost_usd").num),
          (1000.0, 200.0, 5000.0, 0.42))
        assertEquals((s("holdout_failed").num, s("regression_count").num), (0.0, 0.0))
        assertEquals(s("gate_blocks"), ujson.Obj())
      }
    }

  test("other_suites_of_task_module_scored_as_regressions"):
    withEnv { e =>
      os.write(e.rd / "repo" / "signals" / "test" / "src" / "Other.scala", "package pk\nobject OtherSpec extends SimpleIOSuite\n", createFolders = true)
      e.score("FAKE_FAIL_MODS" -> "signals.test").map { s =>
        val reg = e.regCalls.head.args
        assertEquals(reg(reg.indexOf("signals.test.testOnly") + 1), "pk.OtherSpec")
        assertEquals(strs(s("regressions")), List("signals.test")); assert(!s("solved").bool)
        assertEquals(s("hidden_pass_ratio").num, 1.0)
      }
    }

  test("leaked_agent_environment_is_an_error"):
    withEnv { e =>
      e.setTranscript(transcript("real-init-leaked-g.jsonl") + transcript("clean.jsonl"))
      e.score().map(s => assert(errStr(s).startsWith("agent environment leaked: mcp_servers="), s))
    }

  test("ungated_hook_event_is_an_error"):
    withEnv { e =>
      e.patchRun("arm" -> "u")
      val hook = """{"type": "system", "subtype": "hook_started", "hook_name": "Stop", "hook_event": "Stop"}"""
      e.setTranscript(transcript("real-init-isolated.jsonl") + hook + "\n" + transcript("clean.jsonl"))
      e.score().map(s => assertEquals(err(s), Some("agent environment leaked: hooks=Stop")))
    }

  test("regression_attributed_per_module"):
    withEnv { e =>
      e.score("FAKE_FAIL_MODS" -> "control.test").map { s =>
        assertEquals(strs(s("regressions")), List("control.test")); assert(!s("solved").bool)
        assertEquals(s("regression_count").num, 1.0)
      }
    }

  test("module_failing_at_head_is_not_a_regression"):
    withEnv { e =>
      e.writeBaseline("""{"modules": {"signals.test": true, "control.test": false}, "passes": {"signals.test": 1, "control.test": 1}}""")
      e.score("FAKE_FAIL_MODS" -> "control.test").map { s =>
        assertEquals(strs(s("regressions")), Nil); assert(s("solved").bool)
      }
    }

  test("unattributed_regression_failure"):
    withEnv { e =>
      e.score("FAKE_REG_MODE" -> "silent").map { s =>
        assert(!s("solved").bool); assert(strs(s("regressions")).nonEmpty); assert(errStr(s).contains("regression"))
        assertEquals(strs(s("regressions")), List("<unattributed>"))
        assertEquals(err(s), Some("mill failed (regressions): weird crash"))
      }
    }

  test("other_error_line_is_mill_failure"):
    withEnv { e =>
      e.score("FAKE_REG_MODE" -> "oom").map { s =>
        assert(!s("solved").bool); assert(errStr(s).startsWith("mill failed (regressions):"), s)
        assert(errStr(s).contains("OutOfMemoryError"))
      }
    }

  test("agent_broke_a_module_without_tests_is_a_regression_not_an_error"):
    withEnv { e =>
      e.score("FAKE_REG_MODE" -> "compile-nomodule").map { s =>
        assert(!s("solved").bool); assertEquals(err(s), None); assertEquals(strs(s("regressions")), List("<compile:backpressure>"))
        assertEquals(s("regression_count").num, -1.0)
      }
    }

  test("agent_broke_the_build_file"):
    withEnv { e =>
      e.score("FAKE_REG_MODE" -> "build").map { s =>
        assert(!s("solved").bool); assertEquals(err(s), None); assertEquals(strs(s("regressions")), List("<build>"))
      }
    }

  test("agent_broke_the_build_file_real_mill_shape"):
    withEnv { e =>
      e.score("FAKE_BUILD_BROKEN" -> (fx / "mill" / "build-broken-resolve.txt").toString).map { s =>
        assert(!s("solved").bool); assertEquals(err(s), None, ujson.write(s)); assertEquals(strs(s("regressions")), List("<build>"))
        assert(!s("hidden_compiled").bool); assert(!s("holdout_passed").bool)
        assertEquals(e.regCalls, Nil) // nothing left to run once resolve shows the build is broken
      }
    }

  test("module_listing_failure_is_recorded"):
    withEnv { e =>
      e.score("FAKE_FAIL_MODS" -> "control.test", "FAKE_ALL_RESOLVE_FAIL" -> "1").map { s =>
        assert(!s("solved").bool); assertEquals(strs(s("regressions")), List("control.test"))
        assert(errStr(s).contains("resolve __:Module failed"))
        assertEquals(err(s), Some("resolve __:Module failed: resolve exploded"))
      }
    }

  test("module_listing_only_when_attribution_needed"):
    withEnv { e =>
      e.score("FAKE_ALL_RESOLVE_FAIL" -> "1").map { s =>
        assert(s("solved").bool, ujson.write(s)); assertEquals(err(s), None)
      }
    }

  test("old_format_baseline_is_an_error"):
    withEnv { e =>
      e.writeBaseline("""{"modules": {"signals.test": true, "control.test": true}}""")
      e.score().map { s =>
        assert(!s("solved").bool); assert(errStr(s).contains("baseline outdated"))
        assertEquals(err(s), Some("baseline outdated, re-run --baseline"))
      }
    }

  test("regression_timeout_is_an_outcome_not_an_error"):
    withEnv { e =>
      e.scoreE(e.exp.copy(regressCapSeconds = 1), "FAKE_REG_MODE" -> "hang").map { s =>
        assert(!s("solved").bool); assert(strs(s("score_timeouts")).contains("regressions"))
        assertEquals(strs(s("regressions")), List("<timeout>")); assertEquals(err(s), None)
      }
    }

  test("regression_run_that_ran_no_tests"):
    withEnv { e =>
      e.score("FAKE_REG_MODE" -> "notests").map { s =>
        assert(!s("solved").bool); assertEquals(strs(s("regressions")), List("<tests did not run>")); assertEquals(err(s), None)
      }
    }

  test("bench_test_excluded_from_regressions"):
    withEnv { e =>
      e.score("FAKE_FAIL_MODS" -> "bench.test").map { s =>
        assert(!e.regCalls.head.args.contains("bench.test")); assertEquals(strs(s("regressions")), Nil); assert(s("solved").bool, ujson.write(s))
      }
    }

  test("holdout_mill_failure_is_an_error"):
    withEnv { e =>
      e.score("FAKE_HOLDOUT" -> "millfail").map { s =>
        assert(!s("solved").bool); assert(errStr(s).startsWith("mill failed (holdout):"), s)
      }
    }

  test("holdout_timeout_is_unsolved_not_error"):
    withEnv { e =>
      e.scoreE(e.exp.copy(testCapSeconds = 1), "FAKE_HOLDOUT" -> "hang", "FAKE_HIDDEN" -> "pass").map { s =>
        assert(!s("solved").bool); assert(!s("holdout_passed").bool); assert(strs(s("score_timeouts")).contains("holdout.test"))
        assertEquals(err(s), None)
        assertEquals(s("holdout_failed").num, -1.0)
      }
    }

  test("failed_test_ratio_and_false_done"):
    withEnv { e =>
      e.score("FAKE_HIDDEN" -> "fail").map { s =>
        assert(!s("solved").bool); assertEquals(s("hidden_pass_ratio").num, 0.5); assert(s("false_done").bool)
      }
    }

  test("class_level_failure_is_not_solved"):
    withEnv { e =>
      e.score("FAKE_HIDDEN" -> "class").map { s =>
        assert(!s("solved").bool); assert(s("hidden_pass_ratio").num < 1.0)
      }
    }

  test("no_compile"):
    withEnv { e =>
      e.score("FAKE_HIDDEN" -> "nocompile").map { s =>
        assert(!s("hidden_compiled").bool); assertEquals(s("hidden_pass_ratio").num, 0.0); assert(!s("solved").bool)
      }
    }

  test("hidden_suite_did_not_run"):
    withEnv { e =>
      e.score("FAKE_HIDDEN" -> "norun").map { s =>
        assert(!s("solved").bool); assert(s("hidden_compiled").bool); assert(errStr(s).contains("hidden suite did not run"))
      }
    }

  test("mill_infrastructure_failure"):
    withEnv { e =>
      e.score("FAKE_HIDDEN" -> "millfail").map { s =>
        assert(!s("solved").bool); assert(errStr(s).startsWith("mill failed:")); assert(errStr(s).contains("OutOfMemoryError"))
      }
    }

  test("hang_counts_as_fail"):
    withEnv { e =>
      e.scoreE(e.exp.copy(testCapSeconds = 1), "FAKE_HIDDEN" -> "hang").map { s =>
        assert(!s("solved").bool); assert(strs(s("score_timeouts")).contains("signals.test"))
      }
    }

  test("no_baseline"):
    withEnv { e =>
      os.remove(e.root / "head-baseline.json")
      e.score().map { s =>
        assert(!s("solved").bool); assert(errStr(s).contains("no baseline"))
        assertEquals(err(s), Some("no baseline (run score.py --baseline)"))
      }
    }

  test("empty_module_list"):
    withEnv { e =>
      e.score("FAKE_MODULES" -> "holdout.test").map { s =>
        assert(!s("solved").bool); assert(errStr(s).contains("no test modules"))
      }
    }

  test("agent_timeout_not_solved_not_false_done"):
    withEnv { e =>
      e.patchRun("timed_out" -> true, "exit_code" -> 124)
      e.score().map { s =>
        assert(!s("solved").bool); assert(!s("false_done").bool); assert(s("timed_out").bool)
      }
    }

  test("agent_timeout_with_empty_transcript_has_no_error"):
    withEnv { e =>
      e.patchRun("timed_out" -> true, "exit_code" -> 124); e.setTranscript("")
      e.score().map { s =>
        assert(!s("solved").bool); assertEquals(err(s), None); assertEquals(s("turns"), ujson.Null)
      }
    }

  test("api_error_result_is_infra_error"):
    withEnv { e =>
      e.setTranscript(transcript("real-init-isolated.jsonl") + transcript("real-api-error.jsonl"))
      e.score().map(s => assert(errStr(s).contains("agent api error: 429 You've hit your weekly limit"), s))
    }

  test("api_error_result_on_timeout_is_no_error"):
    withEnv { e =>
      e.patchRun("timed_out" -> true, "exit_code" -> 124)
      e.setTranscript(transcript("real-init-isolated.jsonl") + transcript("real-api-error.jsonl"))
      e.score().map(s => assertEquals(err(s), None))
    }

  test("empty_transcript"):
    withEnv { e =>
      e.setTranscript("")
      e.score().map { s =>
        assertEquals(err(s), Some("no result event")); assertEquals(s("turns"), ujson.Null)
      }
    }

  private def genericScore(e: Env, rc: Int): IO[ujson.Obj] =
    val g = Generic(AgentSpec("gen", "generic", ujson.Obj("adapter" -> "generic", "transcript" -> "none", "command" -> ujson.Arr("x"))))
    e.patchRun("agent" -> "gen", "exit_code" -> rc)
    e.setTranscript("")
    val exp = e.exp.copy(agents = e.exp.agents + ("gen" -> g.spec))
    e.target = FakeMill(exp.target, Map.empty, _ => ())
    Score.withAdapters(exp, e.rd, e.target, _ => Right(g))

  // deferred minor (Task 20): Score adds the dsh child sessions' tokens (Agent.withDelegated) to the transcript's
  test("dsh agent: child session tokens are in score.json, turns are the run's own"):
    withEnv { e =>
      val home = e.tmp / "dsh-home"
      val d = harness.agent.Dsh(AgentSpec("dsh", "dsh", ujson.Obj("adapter" -> "dsh", "profile" -> "p", "dsh-home" -> home.toString)))
      def lines(id: String, parent: Option[String], in: Int) = Seq(
        ujson.write(ujson.Obj.from(Seq("type" -> ujson.Str("session"), "id" -> ujson.Str(id), "createdAt" -> ujson.Num(200000)) ++
          parent.map("parentSession" -> ujson.Str(_)))),
        """{"type":"request/header","data":{"header":{"config":{"model":"m"},"tools":[]}}}""",
        """{"type":"step/start","data":{}}""",
        s"""{"type":"assistant/message","data":{"message":{"content":[]},"usage":{"inputTokens":$in,"outputTokens":1,"cacheReadTokens":1}}}""",
        """{"type":"turn/end","data":{"reason":{"kind":"completed"}}}""")
      val slug = harness.agent.Dsh.dshSlug((e.rd / "repo").toNIO.toRealPath().toString)
      val f = home / "sessions" / slug / "child" / "session.v3.jsonl.zstd"
      os.makeDir.all(f / os.up)
      val out = com.github.luben.zstd.ZstdOutputStream(java.io.FileOutputStream(f.toIO))
      try out.write(lines("child", Some("top"), 7).map(_ + "\n").mkString.getBytes("UTF-8")) finally out.close()
      e.patchRun("agent" -> "dsh", "model" -> "m")
      e.setTranscript(lines("top", None, 100).map(_ + "\n").mkString)
      val exp = e.exp.copy(agents = e.exp.agents + ("dsh" -> d.spec))
      e.target = FakeMill(exp.target, Map.empty, _ => ())
      Score.withAdapters(exp, e.rd, e.target, _ => Right(d)).map { s =>
        assertEquals((s("input_tokens"), s("output_tokens"), s("cache_read_tokens")), (ujson.Num(107), ujson.Num(2), ujson.Num(2)))
        assertEquals(s("turns"), ujson.Num(1))
      }
    }

  // gate-cheap audit (2026-10-13): a subagent's own tool calls run in its child session, which contamination never read
  test("dsh agent: a child session's tool call on forbidden material is contamination, labelled subagent-session"):
    withEnv { e =>
      val home = e.tmp / "dsh-home"
      val d = harness.agent.Dsh(AgentSpec("dsh", "dsh", ujson.Obj("adapter" -> "dsh", "profile" -> "p", "dsh-home" -> home.toString)))
      val forbidden = (e.root / "head" / "x.scala").toString
      def lines(id: String, parent: Option[String], calls: Seq[String]) = Seq(
        ujson.write(ujson.Obj.from(Seq("type" -> ujson.Str("session"), "id" -> ujson.Str(id), "createdAt" -> ujson.Num(200000)) ++
          parent.map("parentSession" -> ujson.Str(_)))),
        """{"type":"request/header","data":{"header":{"config":{"model":"m"},"tools":[]}}}""",
        """{"type":"step/start","data":{}}""") ++ calls ++ Seq(
        """{"type":"assistant/message","data":{"message":{"content":[]},"usage":{"inputTokens":1,"outputTokens":1,"cacheReadTokens":1}}}""",
        """{"type":"turn/end","data":{"reason":{"kind":"completed"}}}""")
      val read = ujson.write(ujson.Obj("type" -> "tool/call", "data" -> ujson.Obj("turn" -> 1, "callId" -> "c1", "name" -> "read",
        "arguments" -> ujson.write(ujson.Obj("file_path" -> forbidden)))))
      val slug = harness.agent.Dsh.dshSlug((e.rd / "repo").toNIO.toRealPath().toString)
      val f = home / "sessions" / slug / "child" / "session.v3.jsonl.zstd"
      os.makeDir.all(f / os.up)
      val out = com.github.luben.zstd.ZstdOutputStream(java.io.FileOutputStream(f.toIO))
      try out.write(lines("child", Some("top"), Seq(read)).map(_ + "\n").mkString.getBytes("UTF-8")) finally out.close()
      e.patchRun("agent" -> "dsh", "model" -> "m")
      e.setTranscript(lines("top", None, Nil).map(_ + "\n").mkString)
      val exp = e.exp.copy(agents = e.exp.agents + ("dsh" -> d.spec))
      e.target = FakeMill(exp.target, Map.empty, _ => ())
      Score.withAdapters(exp, e.rd, e.target, _ => Right(d)).map { s =>
        assertEquals(s("contamination").arr.map(_.str).toList, List(s"subagent-session read: ${harness.core.Contamination.realpath(forbidden)}"))
        assertEquals(s("contaminated"), ujson.True)
      }
    }

  test("generic agent: exit_code 0 is a result"):
    withEnv { e => genericScore(e, 0).map(s => assertNotEquals(err(s), Some("no result event"))) }

  test("generic agent: exit_code 127 is no result event"):
    withEnv { e => genericScore(e, 127).map(s => assertEquals(err(s), Some("no result event"))) }

  // Final review I4: no transcript, so contamination, env leaks and gate blocks were not checked: null, not clean
  test("generic agent: contamination, env_leaks, gate_blocks are null (unchecked)"):
    withEnv { e =>
      genericScore(e, 0).map { s =>
        val disk = ujson.read(os.read(e.rd / "score.json")).obj
        for o <- List(s.value, disk) do
          assertEquals((o("contaminated"), o("contamination"), o("env_leaks"), o("gate_blocks")),
            (ujson.Null, ujson.Null, ujson.Null, ujson.Null))
        assertEquals(s.value.keys.toList.slice(17, 21), List("contaminated", "contamination", "gate_blocks", "env_leaks"))
      }
    }

  test("exception_writes_record"):
    withEnv { e =>
      os.remove(e.rd / "run.json")
      e.score().map { s =>
        assert(!s("solved").bool); assert(errStr(s).contains("FileNotFoundError"), s)
        assertEquals(s("run_id").str, "noise-g-1")
      }
    }

  test("empty_task_env_values_rejected"):
    withEnv { e =>
      os.write(e.tmp / "tasks" / "noise" / "task.env", "TASK_MODULE=''\nHIDDEN_SPEC=''\n", createFolders = true)
      e.scoreE(e.exp.copy(tasksDir = e.tmp / "tasks")).map { s =>
        assert(!s("solved").bool); assert(errStr(s).contains("task.env"))
        assertEquals(err(s), Some("ScoreError('task.env for noise has empty TASK_MODULE/HIDDEN_SPEC')"))
      }
    }

  test("partial_record_says_incomplete"):
    withEnv { e =>
      e.score().map { s =>
        assertEquals(e.seenErrors.headOption, Some(ujson.Str("scoring incomplete"))); assertEquals(err(s), None)
      }
    }

  test("keyboard_interrupt_recorded_and_reraised (fiber cancel)"):
    withEnv { e =>
      Deferred[IO, Unit].flatMap { started =>
        val hangs = new MillScala(e.exp.target):
          override def runBuild(tree: os.Path, args: Seq[String], outDir: String, log: os.Path, env: Map[String, Option[String]],
                       cap: Option[Int], keepGoing: Boolean): IO[ProcResult] = started.complete(()) *> IO.never
        for
          fib <- Score.withAdapters(e.exp, e.rd, hangs, e.agentOf(e.exp)).start
          _ <- started.get
          _ <- fib.cancel
          oc <- fib.join
        yield
          assert(oc.isCanceled, oc)
          val s = ujson.read(os.read(e.rd / "score.json"))
          assert(!s("solved").bool); assert(s("error").str.contains("KeyboardInterrupt"), s)
      }
    }

  test("task_env_sourced_in_sh"):
    assertEquals(TaskSpec.load(exp0.tasksDir, "noise").map(_.module), Right("signals.test"))

  test("export_holdout_failure_raises"):
    withEnv { e =>
      Score.exportHoldout(os.Path(e.tmp.toString + "/nonexistent"), "HEAD", e.tmp / "dest").attempt.map {
        case Left(x: ScoreError) => assert(x.getMessage.startsWith("holdout export failed: "), x.getMessage)
        case other               => fail(s"expected ScoreError, got $other")
      }
    }

  // ---- Review Focus 4: run.json of an old root (no agent / model) scores exactly like GA

  test("old run.json without agent / model scores like GA (claude-code, record unchanged)"):
    withEnv { e =>
      for
        old <- e.score()
        _ = assert(!ujson.read(os.read(e.rd / "run.json")).obj.contains("agent"))
        _ = e.patchRun("agent" -> "claude-code", "model" -> "sonnet")
        cur <- e.score()
      yield
        assertEquals(e.agentNames.toList, List("claude-code", "claude-code"))
        def noWall(o: ujson.Obj) = ujson.Obj.from(o.value.toList.filterNot(_._1 == "wall_seconds"))
        assertEquals(noWall(old), noWall(cur))
        assert(old("solved").bool); assertEquals(err(old), None)
        assert(!old.obj.contains("agent") && !old.obj.contains("model"), "score.json keeps GA's field set")
    }

  test("arm missing from the experiment: GA arm semantics (u: hooks leak, g: hooks allowed, t: turn-end leak)"):
    withEnv { e =>
      val noArms = e.exp.copy(armSpecs = Map.empty)
      val hook = """{"type": "system", "subtype": "hook_started", "hook_name": "Stop", "hook_event": "Stop"}"""
      val turnEnd = """{"type": "user", "message": {"content": [{"type": "text", "text": "Stop hook feedback: x"}]}}"""
      e.setTranscript(transcript("real-init-isolated.jsonl") + hook + "\n" + turnEnd + "\n" + transcript("clean.jsonl"))
      for
        g <- e.scoreE(noArms)
        _ = e.patchRun("arm" -> "u")
        u <- e.scoreE(noArms)
        _ = e.patchRun("arm" -> "t")
        t <- e.scoreE(noArms)
        _ = e.patchRun("arm" -> "zz")
        zz <- e.scoreE(noArms)
      yield
        assertEquals(err(g), None)
        assertEquals(err(u), Some("agent environment leaked: hooks=Stop"))
        assertEquals(err(t), Some("agent environment leaked: turn-end in arm t"))
        assertEquals(err(zz), Some("agent environment leaked: hooks=Stop"))
    }

  test("contamination fence: base, head, target repo, runs, agent projects, inside-holdout; own project allowed"):
    withEnv { e =>
      val repo = e.rd / "repo"
      def bash(cmd: String) = s"""{"type":"assistant","message":{"content":[{"type":"tool_use","id":"x","name":"Bash","input":{"command":"$cmd"}}]}}"""
      val projects = e.tmp / "cfg" / "projects"
      val own = projects / ClaudeCode.claudeSlug(repo.toNIO.toRealPath().toString)
      val cmds = List(s"cat ${e.root}/base/x", s"ls ${e.root}/head", s"cat ${e.exp.target.repo}/build.mill",
        s"ls ${e.root}/runs/other-u-1", s"ls $projects/other", s"cat $own/m.jsonl", s"ls ${e.rd}/holdout",
        "ls out-holdout", s"cat $repo/.dsh/.state/holdout.log", s"ls ${e.rd}/repo/src")
      e.setTranscript(transcript("real-init-isolated.jsonl") + cmds.map(bash).mkString("\n") + "\n" + transcript("clean.jsonl"))
      e.score().map { s =>
        val real = (p: os.Path) => harness.core.Contamination.realpath(p.toString)
        assertEquals(strs(s("contamination")).toSet, Set(
          s"Bash: ${real(e.root / "base" / "x")}", s"Bash: ${real(e.root / "head")}",
          s"Bash: ${real(e.exp.target.repo / "build.mill")}", s"Bash: ${real(e.root / "runs" / "other-u-1")}",
          s"Bash: ${real(projects / "other")}", s"Bash: ${real(e.rd / "holdout")}", s"Bash: ${real(repo)}/out-holdout",
          s"Bash: ${real(repo / ".dsh" / ".state" / "holdout.log")}"))
        assert(s("contaminated").bool)
      }
    }

  // ---- class BaselineMode

  private def withBase[A](f: Env => IO[A]): IO[A] = withEnv(e => IO.blocking(os.remove(e.root / "head-baseline.json")) *> f(e))

  private def loud(io: IO[?], msg: String => Boolean = _ => true): IO[Unit] = io.attempt.map {
    case Left(x: ScoreError) => assert(msg(x.getMessage), x.getMessage)
    case other               => fail(s"expected ScoreError, got $other")
  }

  test("writes_baseline"):
    withBase { e =>
      e.baseline().map { res =>
        val b = e.headBaseline
        assertEquals(b("modules"), ujson.Obj("signals.test" -> true, "control.test" -> true))
        assert(!b("modules").obj.contains("bench.test"))
        val reg = e.regCalls.head
        assert(reg.cwd.toString.endsWith("/head")); assertEquals(reg.out, "out")
        assertEquals(res, Map("signals.test" -> true, "control.test" -> true))
        assertEquals(b.obj.keys.toList, List("modules", "passes", "wall_seconds", "rc", "computed_epoch"))
        assertEquals(reg.cap, Some(1200))
      }
    }

  test("failing_module_recorded"):
    withBase { e =>
      e.baseline("FAKE_HEAD_FAIL_MODS" -> "control.test").map { _ =>
        assertEquals(e.headBaseline("modules"), ujson.Obj("signals.test" -> true, "control.test" -> false))
      }
    }

  test("per_module_pass_counts"):
    withBase { e =>
      for m <- List("signals.test", "control.test") do
        os.write(e.root / "head" / os.RelPath(m.replace(".", "/")) / "src" / "S.scala",
          s"package pk\nobject ${m.replace(".", "_")}Spec extends SimpleIOSuite\n", createFolders = true)
      e.baseline().map(_ => assertEquals(e.headBaseline("passes"), ujson.Obj("signals.test" -> 1, "control.test" -> 1)))
    }

  test("oom_is_loud"):
    withBase { e =>
      loud(e.baseline("FAKE_REG_MODE" -> "oom")).map(_ => assert(!os.exists(e.root / "head-baseline.json")))
    }

  test("compile_failure_at_head_is_loud"):
    withBase { e =>
      loud(e.baseline("FAKE_REG_MODE" -> "compile"), _.startsWith("HEAD baseline failed for a non-test reason: build failure in <compile:core>"))
    }

  test("no_tests_at_head_is_loud"):
    withBase(e => loud(e.baseline("FAKE_REG_MODE" -> "notests"), _.startsWith("HEAD baseline executed no tests (see ")))

  test("resolve_failure_is_loud"):
    withBase { e =>
      loud(e.baseline("FAKE_RESOLVE_FAIL" -> "1"), _ == "resolve __.test failed: resolve exploded")
        .map(_ => assert(!os.exists(e.root / "head-baseline.json")))
    }

  test("empty_list_is_loud"):
    withBase(e => loud(e.baseline("FAKE_MODULES" -> "holdout.test"), _ == "no test modules"))

  test("non_test_failure_is_loud"):
    withBase { e =>
      loud(e.baseline("FAKE_REG_MODE" -> "silent"), _.startsWith("HEAD baseline failed for a non-test reason: weird crash (see "))
        .map(_ => assert(!os.exists(e.root / "head-baseline.json")))
    }

  test("timeout_is_loud"):
    withBase { e =>
      e.target = FakeMill(e.exp.target, Map("FAKE_REG_MODE" -> "hang"), _ => ())
      loud(Score.baselineWith(e.exp.copy(regressCapSeconds = 1), e.target), _.startsWith("HEAD baseline timed out (see "))
    }

  test("no HEAD tree is loud"):
    withBase { e =>
      os.remove.all(e.root / "head")
      loud(e.baseline(), _ == s"no HEAD tree at ${e.root / "head"}")
    }

  test("cli_exit_code"):
    withBase { e =>
      val out, errs = scala.collection.mutable.ListBuffer.empty[String]
      e.target = FakeMill(e.exp.target, Map("FAKE_RESOLVE_FAIL" -> "1"), _ => ())
      Score.baselineCli(e.exp, e.target, s => IO(out += s), s => IO(errs += s)).map { rc =>
        assertNotEquals(rc, 0); assertEquals(errs.toList, List("score.py --baseline: resolve __.test failed: resolve exploded"))
      }
    }

  test("cli prints one line per module, sorted"):
    withBase { e =>
      val out = scala.collection.mutable.ListBuffer.empty[String]
      e.target = FakeMill(e.exp.target, Map("FAKE_HEAD_FAIL_MODS" -> "control.test"), _ => ())
      Score.baselineCli(e.exp, e.target, s => IO(out += s), _ => IO.unit).map { rc =>
        assertEquals(rc, 0)
        assertEquals(out.toList, List(f"${"control.test"}%-28s FAIL", f"${"signals.test"}%-28s pass"))
      }
    }

  test("pyRepr of exceptions (GA repr(e))"):
    assertEquals(Score.pyRepr(ScoreError("a 'b'")), "ScoreError(\"a 'b'\")")
    assertEquals(Score.pyRepr(ScoreError("plain")), "ScoreError('plain')")
    assertEquals(Score.pyRepr(new java.nio.file.NoSuchFileException("/x")), "FileNotFoundError(2, 'No such file or directory')")
    assertEquals(Score.pyRepr(new NoSuchElementException("key not found: task")), "KeyError('task')")
    // a corrupt run.json: GA json.load raises JSONDecodeError (its message differs, PARITY.md)
    for bad <- List("{", "{x}") do
      val e = scala.util.Try(ujson.read(bad)).failed.get
      assertEquals(Score.pyRepr(e), s"JSONDecodeError('${e.getMessage}')")
