package harness.target

import cats.effect.IO

final case class MillFailures(tests: Set[String], compile: Set[String], other: List[String], failed: Int)
final case class Counts(passed: Int, failed: Int, compiled: Boolean)

/** The build does not compile. In a run's scoring tree an agent outcome (regression `<build>`, no error);
  * at HEAD (baseline) as loud as any ScoreError.
  */
final case class BuildBroken(lastLine: String)
final class ScoreError(msg: String) extends Exception(msg)

trait Target:
  def spec: harness.config.TargetSpec
  // pure
  def testCounts(log: String): Counts // GA weaver_counts
  def hiddenCounts(log: String, suite: String): Counts // GA hidden_counts
  def suiteName(specText: String): Option[String] // GA suite_fq
  def suitePasses(log: String): Map[String, Int] // GA suite_passes
  def modulePasses(passes: Map[String, Int], suitesByModule: Map[String, List[String]]): Map[String, Int]
  def buildBroken(log: String): Boolean // GA build_broken
  def parseFailures(log: String, modules: Seq[String], allModules: Seq[String]): MillFailures
  def isCompileCommand(cmd: String): Boolean
  def isBuildFile(path: String): Boolean
  def isTestSource(path: String): Boolean // a diff path the tampering check guards (GA tampering: `/test/src/`)
  def outDirs(name: String): Boolean
  // effectful
  def allSuites(tree: os.Path, module: String): IO[List[String]]
  def otherSuites(tree: os.Path, module: String, hidden: String): IO[List[String]]
  def runBuild(
      tree: os.Path, args: Seq[String], outDir: String, log: os.Path,
      env: Map[String, Option[String]] = Map.empty, cap: Option[Int] = None, keepGoing: Boolean = false
  ): IO[harness.run.ProcResult]
  def testModules(tree: os.Path, outDir: String, log: os.Path): IO[Either[BuildBroken, List[String]]] // raises ScoreError
  def allModules(tree: os.Path, outDir: String, log: os.Path): IO[Either[BuildBroken, List[String]]]
  def joinTasks(entries: Seq[Seq[String]]): Seq[String] // GA _chain
  def hiddenRunArgs(module: String, suite: String): Seq[String]
  def regressionEntry(module: String, onlySuites: Option[List[String]]): Seq[String]
  def holdoutArgs: Seq[String]
  def holdoutEnv(dir: os.Path): Map[String, Option[String]]
  def precompileArgs: Seq[String]
  def compileAllArgs: Seq[String]
  def headCompileArgs: Seq[String] // GA warm-cache.sh: main and test sources of every module, once at HEAD

object Target:
  def of(spec: harness.config.TargetSpec): Either[String, Target] =
    spec.adapter match
      case "mill-scala" => Right(MillScala(spec))
      case other        => Left(s"target.adapter: unknown target adapter $other (one of mill-scala)")
