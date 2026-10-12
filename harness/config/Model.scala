package harness.config

enum JsonOp:
  case Delete(path: String, required: Boolean)
  case KeepOnly(path: String, keys: List[String])
  case Filter(path: String, contains: String, requireNonEmpty: Boolean)

enum SetupStep:
  case Run(argv: List[String], fail: Option[String] = None) // fail: error text (GA ga_die) instead of the generic one
  case Write(path: String, text: String)
  case AppendLine(path: String, text: String) // skipped when an identical line exists
  case ReplaceLine(path: String, prefix: String, text: String) // fails if no line starts with prefix (after optional spaces)
  case JsonEdit(path: String, ops: List[JsonOp])
  case Commit(message: String, paths: List[String], allowEmpty: Boolean, fail: Option[String] = None)

final case class ArmSpec(
    name: String,
    agent: String,
    model: String,
    outDir: String, // "out" (ungated) or "out-agent"
    holdout: Boolean, // export the holdout into <run>/holdout, {holdout} defined
    settingsSnapshot: Boolean, // agent config comes from the campaign's frozen snapshot
    hooksAllowed: Boolean, // env-leak check: hook events allowed
    turnEndForbidden: Boolean, // env-leak check: a turn-end block is a leak (GA arm t)
    setup: List[SetupStep]
)

final case class AgentSpec(name: String, adapter: String, raw: ujson.Obj) // adapter knobs, read by the adapter

final case class TargetSpec(
    adapter: String,
    repo: os.Path,
    commit: String,
    holdoutRepo: os.Path,
    holdoutCommit: String,
    holdoutExpected: Int,
    excludedRegression: List[String],
    precompile: List[String],
    mill: String,
    settingsSource: os.Path, // the file snapshotted for settingsSnapshot arms
    allTasks: List[String]
)

final case class ReportSpec(treatment: String, control: String, ablation: String)
final case class ChainReportSpec(control: String, treatment: String)

final case class Experiment(
    root: os.Path,
    tasksDir: os.Path,
    template: os.Path,
    capSeconds: Int,
    testCapSeconds: Int,
    regressCapSeconds: Int,
    precompileCapSeconds: Int,
    reps: List[Int],
    arms: List[String],
    tasks: List[String],
    chain: List[String],
    smokeTask: String,
    smokeRep: Int,
    report: ReportSpec,
    chainReport: ChainReportSpec,
    target: TargetSpec,
    agents: Map[String, AgentSpec],
    armSpecs: Map[String, ArmSpec]
)
