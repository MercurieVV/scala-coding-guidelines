package harness.target

import cats.effect.IO
import harness.core.Json
import harness.run.{Proc, ProcResult}

/** Mill + weaver adapter. Ports GA/score.py weaver_counts ... _chain. Open so a test fake can script `runBuild` and
  * still go through the real resolve / module parsing built on it.
  */
class MillScala(val spec: harness.config.TargetSpec) extends Target:
  import MillScala.*

  private def strip(s: String): String = Json.Ansi.replaceAllIn(s, "")

  /** Lines of a Mill/weaver log minus the `*****FAILURES*****` block (it repeats each failed `- name`) up to the next suite. */
  private def countedLines(text: String): List[String] =
    var skipping = false
    text.linesIterator.filter { ln =>
      if FailuresHeader.matches(ln) then skipping = true
      else if ln.startsWith("Running Test Class") then skipping = false
      !skipping
    }.toList

  def testCounts(log: String): Counts =
    val text = strip(log)
    if text.contains("Compilation failed") then Counts(0, 0, false)
    else
      var passed, failed = 0
      for ln <- countedLines(text) do
        WeaverLine.findFirstMatchIn(ln).foreach(m => if m.group(1) == "+" then passed += 1 else failed += 1)
      Counts(passed, failed, true)

  /** testCounts restricted to the output of suite `fq` (its `Running Test Class` section). */
  def hiddenCounts(log: String, suite: String): Counts =
    val text = strip(log)
    if text.contains("Compilation failed") then Counts(0, 0, false)
    else
      val secs = sections(text)
      if secs.isEmpty then testCounts(text)
      else
        val key = secs.keysIterator.find(nameMatches(_, suite))
        val c = testCounts(key.flatMap(secs.get).getOrElse(Nil).mkString("\n"))
        Counts(c.passed, c.failed, true)

  private def sections(text: String): Map[String, List[String]] =
    val out = scala.collection.mutable.LinkedHashMap.empty[String, List[String]]
    var cur: Option[String] = None
    for ln <- text.linesIterator do
      RunningClass.findFirstMatchIn(ln) match
        case Some(m) =>
          cur = Some(m.group(1))
          if !out.contains(m.group(1)) then out(m.group(1)) = Nil
        case None => cur.foreach(k => out(k) = out(k) :+ ln)
    scala.collection.immutable.ListMap.from(out)

  /** Mill truncates long suite names with a trailing `...`. */
  private def nameMatches(shown: String, fq: String): Boolean =
    shown == fq || (shown.endsWith("...") && fq.startsWith(shown.dropRight(3)))

  def suitePasses(log: String): Map[String, Int] =
    sections(strip(log)).map((k, v) => k -> testCounts(v.mkString("\n")).passed)

  def modulePasses(passes: Map[String, Int], suitesByModule: Map[String, List[String]]): Map[String, Int] =
    val out = scala.collection.mutable.LinkedHashMap.from(suitesByModule.keysIterator.map(_ -> 0))
    for (shown, n) <- passes do
      suitesByModule.find((_, fqs) => fqs.exists(nameMatches(shown, _))).foreach((m, _) => out(m) += n)
    out.toMap

  def suiteName(specText: String): Option[String] =
    val pkg = Package.findFirstMatchIn(specText)
    ObjectSuite.findFirstMatchIn(specText).map(o => pkg.fold("")(_.group(1) + ".") + o.group(1))

  def buildBroken(log: String): Boolean = BuildError.findFirstIn(strip(log)).isDefined

  def parseFailures(log: String, modules: Seq[String], allModules: Seq[String]): MillFailures =
    val text = strip(log)
    val mset = modules.toSet
    val aset = allModules.toSet
    val tests, compile = scala.collection.mutable.LinkedHashSet.empty[String]
    val other = scala.collection.mutable.ListBuffer.empty[String]
    if buildBroken(text) then compile += "<build>"
    for m <- ErrorLine.findAllMatchIn(text) do
      val line = m.group(1).trim
      val tok = m.group(2)
      val parent = tok.substring(0, tok.lastIndexOf('.'))
      if mset(tok) then tests += tok
      else if mset(parent) then tests += parent
      else if aset(parent) || aset(tok) then compile += s"<compile:${if aset(parent) then parent else tok}>"
      else if MillLineNo.findFirstIn(line).isEmpty then other += line.take(200)
    if text.contains("Compilation failed") && tests.isEmpty && compile.isEmpty then compile += "<compile>"
    val n = FailedN.findAllMatchIn(text).map(_.group(1).toInt).foldLeft(0)(math.max)
    MillFailures(tests.toSet, compile.toSet, other.toList, n)

  def isCompileCommand(cmd: String): Boolean = cmd.contains("./mill") && cmd.contains("compile")
  def isBuildFile(path: String): Boolean = path.endsWith(".mill")
  def isTestSource(path: String): Boolean = ("/" + path).contains("/test/src/")
  def outDirs(name: String): Boolean = name == "out" || name.startsWith("out-")

  def allSuites(tree: os.Path, module: String): IO[List[String]] = suitesIn(tree, module, None)
  def otherSuites(tree: os.Path, module: String, hidden: String): IO[List[String]] = suitesIn(tree, module, Some(hidden))

  /** Fully-qualified suite objects in <tree>/<module path>/src, minus the hidden one. */
  private def suitesIn(tree: os.Path, module: String, hidden: Option[String]): IO[List[String]] =
    IO.blocking {
      val root = tree / os.RelPath(module.replace('.', '/')) / "src"
      if !os.exists(root) then Nil
      else
        os.walk(root).filter(p => os.isFile(p) && p.last.endsWith(".scala"))
          .flatMap(p => suiteName(Json.readLenient(p))).filterNot(hidden.contains).distinct.sorted.toList
    }

  def runBuild(
      tree: os.Path, args: Seq[String], outDir: String, log: os.Path,
      env: Map[String, Option[String]], cap: Option[Int], keepGoing: Boolean
  ): IO[ProcResult] =
    val c = cap.getOrElse(600)
    val cmd = Seq(spec.mill, "--no-daemon", "-j", "1") ++ (if keepGoing then Seq("-k") else Nil) ++
      Seq("--ticker", "false", "--color", "false") ++ args
    Proc.run(cmd, tree, log, env + ("MILL_OUTPUT_DIR" -> Some(outDir)), Some(c)).flatMap { r =>
      if r.rc == 124 then
        IO.blocking(os.write.append(log, s"\nscore.py: TIMEOUT after $c s (counted as FAIL)\n")).as(r)
      else IO.pure(r)
    }

  private def lastLine(log: os.Path): String =
    val ls = strip(Json.readLenient(log)).linesIterator.filter(_.trim.nonEmpty).toList
    ls.lastOption.fold("(no output)")(_.trim.take(200))

  private def resolve(
      tree: os.Path, outDir: String, log: os.Path, task: String, failMsg: String
  ): IO[Either[BuildBroken, List[String]]] =
    runBuild(tree, Seq("resolve", task), outDir, log).flatMap { r =>
      val text = Json.readLenient(log)
      if r.rc != 0 && buildBroken(text) then IO.pure(Left(BuildBroken(lastLine(log))))
      else if r.rc != 0 then IO.raiseError(ScoreError(s"$failMsg failed: " + lastLine(log)))
      else IO.pure(Right(text.linesIterator.map(l => strip(l).trim).toList))
    }

  def testModules(tree: os.Path, outDir: String, log: os.Path): IO[Either[BuildBroken, List[String]]] =
    resolve(tree, outDir, log, "__.test", "resolve __.test").flatMap {
      case Left(b) => IO.pure(Left(b))
      case Right(ls) =>
        val mods = ls.filter(m => TestModule.matches(m) && !spec.excludedRegression.contains(m))
        if mods.isEmpty then IO.raiseError(ScoreError("no test modules")) else IO.pure(Right(mods))
    }

  /** Every Mill module, no tasks (`resolve __:Module`), to tell build breakage from infrastructure errors. */
  def allModules(tree: os.Path, outDir: String, log: os.Path): IO[Either[BuildBroken, List[String]]] =
    resolve(tree, outDir, log, "__:Module", "resolve __:Module").map(_.map(_.filter(AnyModule.matches)))

  /** Mill command line for several tasks: entries are arg lists, joined with `+`. */
  def joinTasks(entries: Seq[Seq[String]]): Seq[String] =
    entries.zipWithIndex.flatMap((e, i) => (if i > 0 then Seq("+") else Nil) ++ e)

  def hiddenRunArgs(module: String, suite: String): Seq[String] = Seq(module + ".testOnly", suite)
  def regressionEntry(module: String, onlySuites: Option[List[String]]): Seq[String] =
    onlySuites.fold(Seq(module))(s => (module + ".testOnly") +: s)
  def holdoutArgs: Seq[String] = Seq("holdout.test")
  def holdoutEnv(dir: os.Path): Map[String, Option[String]] = Map("DSH_HOLDOUT_DIR" -> Some(dir.toString))
  def precompileArgs: Seq[String] = spec.precompile
  def compileAllArgs: Seq[String] = Seq("__.compile")
  def headCompileArgs: Seq[String] = Seq("__.compile", "+", "__.test.compile")

object MillScala:
  def apply(spec: harness.config.TargetSpec): MillScala = new MillScala(spec)

  private val WeaverLine = """^([+-]) .+ \d+(?:\.\d+)?(?:ms|s|m)$""".r
  private val FailuresHeader = """^\*+\s*FAILURES\s*\*+$""".r
  private val RunningClass = """^Running Test Class (\S+)""".r
  private val Package = """(?m)^package\s+([\w.]+)""".r
  private val ObjectSuite = """(?m)^object\s+(\w+)\s+extends\s+[\w.]*Suite""".r
  private val BuildError = """\[error\]\s+(\S*\.mill:\d+|compile\s)""".r
  private val ErrorLine = """\[error\]\s+(([A-Za-z_]\w*(?:\.\w+)+)[^\n]*)""".r
  private val MillLineNo = """\.mill:\d+""".r
  private val FailedN = """(\d+) FAILED\]""".r
  private val TestModule = """[\w.]+\.test""".r
  private val AnyModule = """[\w.]+""".r
