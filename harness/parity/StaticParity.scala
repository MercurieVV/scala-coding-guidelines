package harness.parity

import cats.effect.IO
import cats.syntax.all.*
import harness.agent.{Agent, RunCtx}
import harness.config.{Experiment, TaskSpec}
import harness.core.{Contamination, Diff, Gate, Json, ToolCall}
import harness.run.{HarnessError, Score}
import harness.target.{MillFailures, Target}

/** Parity spec §7.1: GA score.py's pure functions (python, run against the copied run dirs) vs the harness core, field
  * by field. Parsed-JSON equality: numbers compare numerically, object key order is checked too.
  */
object StaticParity:
  final case class Result(runs: Int, fields: Int, mismatches: List[String]):
    def summary: String = s"$runs runs, $fields fields, ${mismatches.size} mismatches"

  /** One run dir to compare: `dir` is the scratch copy (files read) inside `copyRoot`, `orig` where it lives in the
    * original root (the path contamination resolves the run against, as at run time).
    */
  final case class RunRef(dir: os.Path, orig: os.Path, copyRoot: os.Path)

  /** Run dirs of a copied root: every dir holding a run.json under runs/ or invalid-runs/ (sorted). */
  def runDirs(copyRoot: os.Path, origRoot: os.Path): List[RunRef] =
    List("runs", "invalid-runs").map(copyRoot / _).filter(os.isDir(_)).flatMap { top =>
      os.walk(top, skip = p => Set("repo", "score-tree", "holdout")(p.last)).filter(p => p.last == "run.json" && os.isFile(p))
        .map(_ / os.up).map(d => RunRef(d, origRoot / d.relativeTo(copyRoot), copyRoot))
    }.sortBy(_.dir.toString)

  // ---------------------------------------------------------------- reference side (GA python)

  /** stdin: [{"dir", "orig"}]; argv: GA dir, copied root. One JSON line per run: {"dir", "fields"} or {"dir", "error"}. */
  val PythonSnippet: String =
    """import json, os, re, sys
ga, root = sys.argv[1], sys.argv[2]
sys.path.insert(0, ga)
import score

def mods(path, pat, excluded=()):
    ls = (score.ANSI.sub("", l).strip() for l in score._read(path).splitlines())
    return [m for m in ls if re.fullmatch(pat, m) and m not in excluded]

def fields(rd, orig):
    run = json.load(open(os.path.join(rd, "run.json")))
    t = score.parse_transcript(score._read(os.path.join(rd, "transcript.jsonl")).splitlines())
    out = {}
    for k, v in t.items():
        if k != "init":
            out["transcript." + k] = v
    out["gate_blocks"] = score.gate_blocks(t["tool_calls"], t["hook_texts"])
    out["env_leaks"] = score.env_leaks(t, run["arm"])
    out["first_clean_compile"] = score.first_clean_compile(t["tool_calls"])
    out["contamination"] = score.contamination(t["tool_calls"], orig)
    _m, hidden_spec, _c, _f = score.read_task_env(os.path.join(ga, "tasks", run["task"], "task.env"))
    hidden = score._read(os.path.join(root, "base", run["task"], "hidden", hidden_spec))
    diff = score._read(os.path.join(rd, "final.diff"))
    out["tampering"] = score.tampering(diff)
    out["build_edits"] = score.build_edits(diff)
    out["hardcoding"] = score.hardcoding(diff, hidden)
    fq = score.suite_fq(hidden)
    out["suite_fq"] = fq
    tm = mods(os.path.join(rd, "resolve-score.log"), r"[\w.]+\.test", score.EXCLUDED_REGRESSION)
    am = mods(os.path.join(rd, "resolve-all.log"), r"[\w.]+")
    out["resolve_modules"] = [tm, am]
    for fn in sorted(os.listdir(rd)):
        if not fn.endswith("-score.log"):
            continue
        text = score._read(os.path.join(rd, fn))
        p = "log." + fn + "."
        out[p + "weaver_counts"] = list(score.weaver_counts(text))
        out[p + "hidden_counts"] = list(score.hidden_counts(text, fq)) if fq else None
        out[p + "suite_passes"] = score.suite_passes(text)
        out[p + "build_broken"] = score.build_broken(text)
        r = score.parse_mill_failures(text, tm, am)
        out[p + "parse_mill_failures"] = dict(tests=sorted(r["tests"]), compile=sorted(r["compile"]), other=r["other"], failed=r["failed"])
    return out

for req in json.load(sys.stdin):
    try:
        print(json.dumps(dict(dir=req["dir"], fields=fields(req["dir"], req["orig"]))))
    except Exception as e:
        print(json.dumps(dict(dir=req["dir"], error=repr(e))))
"""

  /** GA side for every run of one root: {dir -> Right(fields) | Left(error)}. */
  def reference(ga: os.Path, copyRoot: os.Path, runs: List[RunRef], env: Map[String, String]): IO[Map[String, Either[String, ujson.Obj]]] =
    IO.blocking {
      val req = ujson.Arr.from(runs.map(r => ujson.Obj("dir" -> r.dir.toString, "orig" -> r.orig.toString)))
      val res = os.proc("python3", "-c", PythonSnippet, ga.toString, copyRoot.toString)
        .call(stdin = ujson.write(req), env = env, propagateEnv = false, check = false, stderr = os.Pipe)
      if res.exitCode != 0 then throw HarnessError(s"parity-static: GA python failed on $copyRoot: ${res.err.text().trim.take(500)}")
      res.out.lines().filter(_.nonEmpty).map { l =>
        val o = ujson.read(l).obj
        o("dir").str -> o.get("error").fold(Right(o("fields").obj): Either[String, ujson.Obj])(e => Left(e.str))
      }.toMap
    }

  // ---------------------------------------------------------------- harness side

  private def strs(xs: Iterable[String]): ujson.Arr = ujson.Arr.from(xs.map(ujson.Str(_)))
  private def optNum(x: Option[Double]): ujson.Value = x.fold(ujson.Null: ujson.Value)(ujson.Num(_))
  private def counts(c: harness.target.Counts): ujson.Arr = ujson.Arr(c.passed, c.failed, c.compiled)

  private def call(c: ToolCall): ujson.Obj =
    ujson.Obj("name" -> c.name, "input" -> c.input, "result_text" -> c.resultText, "is_error" -> c.isError, "turn" -> c.turn)

  private def failures(f: MillFailures): ujson.Obj =
    ujson.Obj("tests" -> strs(f.tests.toList.sorted), "compile" -> strs(f.compile.toList.sorted), "other" -> strs(f.other),
      "failed" -> f.failed)

  /** The harness core on one run; `modules` = (test modules, all modules) as GA derived them from resolve-*.log. */
  def harnessFields(exp: Experiment, target: Target, run: RunRef, modules: (List[String], List[String])): Either[String, ujson.Obj] =
    scala.util.Try {
      val rd = run.dir
      val rj = ujson.read(os.read(rd / "run.json"))
      val agentName = rj.obj.get("agent").flatMap(_.strOpt).getOrElse("claude-code")
      val agent = exp.agents.get(agentName).toRight(s"agent $agentName not in the experiment").flatMap(Agent.of)
        .fold(m => throw HarnessError(m), identity)
      val taskName = rj("task").str
      val ts = TaskSpec.load(exp.tasksDir, taskName).fold(m => throw HarnessError(m), identity)
      val arm = Score.armSpec(exp, rj("arm").str, agentName, rj.obj.get("model").flatMap(_.strOpt).getOrElse(""))
      val t = agent.parse(Json.readLenient(rd / "transcript.jsonl").linesIterator)
      val out = ujson.Obj(
        "transcript.turns" -> optNum(t.turns.map(_.toDouble)),
        "transcript.input_tokens" -> optNum(t.inputTokens.map(_.toDouble)),
        "transcript.output_tokens" -> optNum(t.outputTokens.map(_.toDouble)),
        "transcript.cache_read_tokens" -> optNum(t.cacheReadTokens.map(_.toDouble)),
        "transcript.cost_usd" -> optNum(t.costUsd),
        "transcript.tool_calls" -> ujson.Arr.from(t.toolCalls.map(call)),
        "transcript.final_text" -> t.finalText,
        "transcript.texts" -> strs(t.texts),
        "transcript.hook_texts" -> strs(t.hookTexts),
        "transcript.has_result" -> t.hasResult,
        "transcript.hook_events" -> strs(t.hookEvents),
        "transcript.api_error" -> t.apiError.fold(ujson.Null: ujson.Value)(ujson.Str(_)))
      out("gate_blocks") = ujson.Obj.from(Gate.gateBlocks(t.toolCalls, t.hookTexts).map((k, v) => k -> ujson.Num(v)))
      out("env_leaks") = strs(agent.envLeaks(t, arm))
      out("first_clean_compile") = optNum(Score.firstCleanCompile(t.toolCalls, target.isCompileCommand).map(_.toDouble))
      val ctx = RunCtx(exp, arm, ts, run.orig.last, run.orig, run.orig / "repo", None, None)
      out("contamination") = strs(Contamination.check(t.toolCalls, Score.fence(exp, run.orig, agent.privateStateDirs(ctx))))
      val hiddenText = Json.readLenient(run.copyRoot / "base" / taskName / "hidden" / os.RelPath(ts.hiddenSpec))
      val diff = Json.readLenient(rd / "final.diff")
      out("tampering") = strs(Diff.tampering(diff))
      out("build_edits") = strs(Diff.buildEdits(diff, target.isBuildFile))
      out("hardcoding") = strs(Diff.hardcoding(diff, hiddenText))
      val fq = target.suiteName(hiddenText)
      out("suite_fq") = fq.fold(ujson.Null: ujson.Value)(ujson.Str(_))
      for log <- os.list(rd).filter(p => p.last.endsWith("-score.log") && os.isFile(p)).sortBy(_.last) do
        val text = Json.readLenient(log)
        val p = s"log.${log.last}."
        out(p + "weaver_counts") = counts(target.testCounts(text))
        out(p + "hidden_counts") = fq.fold(ujson.Null: ujson.Value)(s => counts(target.hiddenCounts(text, s)))
        out(p + "suite_passes") = ujson.Obj.from(target.suitePasses(text).map((k, v) => k -> ujson.Num(v)))
        out(p + "build_broken") = target.buildBroken(text)
        out(p + "parse_mill_failures") = failures(target.parseFailures(text, modules._1, modules._2))
      out
    }.toEither.left.map(e => s"${e.getClass.getSimpleName}: ${e.getMessage}")

  // ---------------------------------------------------------------- comparison

  /** Differences between the GA value `a` and the harness value `b` at `path`; numbers numerically, key order checked. */
  def diff(path: String, a: ujson.Value, b: ujson.Value): List[String] = (a, b) match
    case (ujson.Num(x), ujson.Num(y)) => if x == y then Nil else List(s"$path: GA $x, harness $y")
    case (ujson.Obj(x), ujson.Obj(y)) =>
      if x.keySet != y.keySet then List(s"$path: keys GA [${x.keys.mkString(", ")}], harness [${y.keys.mkString(", ")}]")
      else
        val inner = x.keys.toList.flatMap(k => diff(s"$path.$k", x(k), y(k)))
        if inner.isEmpty && x.keys.toList != y.keys.toList then
          List(s"$path: key order GA [${x.keys.mkString(", ")}], harness [${y.keys.mkString(", ")}]")
        else inner
    case (ujson.Arr(x), ujson.Arr(y)) =>
      if x.size != y.size then List(s"$path: length GA ${x.size}, harness ${y.size}${firstDiff(x, y)}")
      else x.indices.toList.flatMap(i => diff(s"$path[$i]", x(i), y(i)))
    case _ => if a == b then Nil else List(s"$path: GA ${clip(a)}, harness ${clip(b)}")

  private def firstDiff(x: collection.Seq[ujson.Value], y: collection.Seq[ujson.Value]): String =
    x.indices.find(i => i >= y.size || diff("", x(i), y(i)).nonEmpty)
      .fold("")(i => s"; first difference at [$i]: GA ${clip(x(i))}, harness ${y.lift(i).fold("(none)")(clip)}")

  private def clip(v: ujson.Value): String =
    val s = ujson.write(v)
    if s.length > 300 then s.take(300) + "…" else s

  // ---------------------------------------------------------------- driver

  /** `roots`: copied run roots; the original of each is `originals / <its name>`. */
  def apply(exp: Experiment, ga: os.Path, roots: List[os.Path], originals: os.Path, say: String => IO[Unit]): IO[Result] =
    Target.of(exp.target).fold(m => IO.raiseError(HarnessError(m)), IO.pure).flatMap { target =>
      roots.traverse { copyRoot =>
        val origRoot = originals / copyRoot.last
        val runs = runDirs(copyRoot, origRoot)
        val e = exp.copy(root = origRoot)
        val env = sys.env.removed("CLAUDE_CONFIG_DIR") ++ Map(
          "GA_ROOT" -> origRoot.toString, "GA_SKREEEP2" -> exp.target.repo.toString,
          "HOME" -> sys.env.getOrElse("HOME", os.home.toString),
          "PYTHONDONTWRITEBYTECODE" -> "1") // GA is read-only: no __pycache__ from `import score`
        for
          _ <- if os.isDir(copyRoot / "runs") then IO.unit else IO.raiseError(HarnessError(s"parity-static: no runs/ in $copyRoot"))
          ref <- reference(ga, copyRoot, runs, env)
          res <- IO.blocking {
            runs.map { r =>
              val key = r.dir.toString
              val rel = r.dir.relativeTo(copyRoot / os.up).toString
              ref.get(key) match
                case None => (0, List(s"$rel: GA python printed nothing"))
                case Some(Left(err)) => (0, List(s"$rel: GA error $err"))
                case Some(Right(all)) =>
                  // GA's module lists parsed from resolve-*.log are an input of parse_mill_failures on both sides, not a field
                  val mods = all.value.get("resolve_modules").collect { case ujson.Arr(a) if a.size == 2 =>
                    (a(0).arr.map(_.str).toList, a(1).arr.map(_.str).toList) }.getOrElse((Nil, Nil))
                  val gaFields = ujson.Obj.from(all.value.iterator.filter(_._1 != "resolve_modules"))
                  harnessFields(e, target, r, mods) match
                    case Left(err) => (gaFields.value.size, List(s"$rel: harness error $err"))
                    case Right(mine) => (gaFields.value.size, diff(rel, gaFields, mine))
            }
          }
          _ <- say(s"${copyRoot.last}: ${runs.size} runs, ${res.map(_._1).sum} fields, ${res.flatMap(_._2).size} mismatches")
        yield Result(runs.size, res.map(_._1).sum, res.flatMap(_._2))
      }.map(rs => Result(rs.map(_.runs).sum, rs.map(_.fields).sum, rs.flatMap(_.mismatches)))
    }
