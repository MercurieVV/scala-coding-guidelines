package harness.config

import munit.FunSuite

class ConfigTest extends FunSuite:
  private val harnessDir: os.Path =
    LazyList.iterate(os.pwd)(_ / os.up).take(6).flatMap(d => List(d, d / "harness"))
      .find(d => os.exists(d / "experiments" / "gate-ab.conf")).getOrElse(fail("harness dir not found"))
  private val gateAb = harnessDir / "experiments" / "gate-ab.conf"

  private def tempConf(body: String): os.Path =
    val d = os.temp.dir()
    os.write(d / "t.conf", s"include file(\"$gateAb\")\n$body\n")
    d / "t.conf"

  private def armX(arm: String): os.Path = tempConf(s"arms.x = $${gated-base} $arm")

  test("gate-ab.conf loads with the six arms"):
    val e = Load.experiment(gateAb).fold(fail(_), identity)
    assertEquals(e.armSpecs.keySet, Set("u", "g", "t", "gf", "gs", "p"))
    assertEquals(e.armSpecs("u").outDir, "out")
    assert(List("g", "t", "gf", "gs", "p").forall(a => e.armSpecs(a).outDir == "out-agent"))
    assert(e.armSpecs("t").turnEndForbidden)
    assert(!e.armSpecs("u").hooksAllowed)
    assert(List("g", "t", "gf", "gs", "p").forall(a => e.armSpecs(a).hooksAllowed))
    assertEquals(e.reps, List(1, 2, 3))
    assertEquals(e.target.holdoutExpected, 7)
    assertEquals(e.tasksDir, harnessDir / "tasks")
    assertEquals(e.armSpecs("gs").setup.size, 5)
    assertEquals(e.armSpecs("gs").setup.head, SetupStep.Run(List("sh", "{template}/kit/harness/install.sh", "{repo}"), Some("engine install into {repo} failed")))
    assertEquals(e.armSpecs("gs").setup(3), SetupStep.Commit("engine, turn-end mode safety", List(".dsh"), true, Some("commit engine in {repo} failed")))
    assertEquals(e.armSpecs("gf").setup(3), SetupStep.Commit("engine, turn-end mode full", List(".dsh"), true, Some("commit engine in {repo} failed")))
    assertEquals(e.smokeRep, 9)

  test("chain.conf overrides root, arms, reps, chain"):
    val e = Load.experiment(harnessDir / "experiments" / "chain.conf").fold(fail(_), identity)
    assertEquals(e.arms, List("u", "gs"))
    assertEquals(e.reps, List(1, 2))
    assertEquals(e.chain, List("fr-pan", "fr-exclusive-solo", "fr-mode-leds", "fr-tuning"))
    assert(e.root.last == "gate-eval-chain")

  test("{holdout} in an arm without holdout fails naming the path"):
    val r = Load.experiment(armX("""{ holdout = false, setup = [{ write = a, text = "{holdout}/x" }] }"""))
    assert(r.left.exists(_.contains("arms.x.setup[0]")), r.toString)

  test("unknown step key fails naming the path"):
    val r = Load.experiment(armX("""{ setup = [{ run = [a] }, { run = [b] }, { run = [c], foo = 1 }] }"""))
    assert(r.left.exists(m => m.contains("arms.x.setup[2]") && m.contains("unknown step keys [foo]")), r.toString)

  test("unknown placeholder fails naming the path"):
    val r = Load.experiment(armX("""{ setup = [{ run = ["{nope}"] }] }"""))
    assert(r.left.exists(m => m.contains("arms.x.setup[0]") && m.contains("{nope}")), r.toString)

  test("unknown agent"):
    val r = Load.experiment(armX("""{ agent = y }"""))
    assertEquals(r, Left("arms.x.agent: unknown agent y"))

  test("--set reps=[1] override applies"):
    val e = Load.experiment(gateAb, Map("reps" -> "[1]")).fold(fail(_), identity)
    assertEquals(e.reps, List(1))

  test("--set root=/x override applies"):
    val e = Load.experiment(gateAb, Map("root" -> "/x")).fold(fail(_), identity)
    assertEquals(e.root, os.Path("/x"))

  test("TaskSpec.load succeeds on every task folder"):
    val names = os.list(harnessDir / "tasks").filter(os.isDir).map(_.last)
    assertEquals(names.size, 16)
    names.foreach(n => assert(TaskSpec.load(harnessDir / "tasks", n).isRight, n))

  test("TaskSpec fr-pan and noise"):
    val pan = TaskSpec.load(harnessDir / "tasks", "fr-pan").fold(fail(_), identity)
    assertEquals(pan.module, "control.test")
    assertEquals(pan.referencePatch, Some("reference.patch"))
    assert(pan.hiddenSpecSrc.isDefined)
    assertEquals(pan.cutFiles, Nil)
    val noise = TaskSpec.load(harnessDir / "tasks", "noise").fold(fail(_), identity)
    assertEquals(noise.cutFiles, List("signals/src/com/github/mercurievv/skreeep2/signals/Noise.scala"))
    assert(!noise.gateVisible)
    assert(TaskSpec.load(harnessDir / "tasks", "adsr").exists(_.gateVisible))

  test("TaskSpec.load reads task.conf and parses sh quotes"):
    val d = os.temp.dir()
    os.write(d / "a" / "task.env", "TASK_MODULE='m'\nHIDDEN_SPEC='h'\nFEATURE='it'\\''s'\nCUT_FILES=''\n", createFolders = true)
    assertEquals(TaskSpec.load(d, "a").map(_.feature), Right("it's"))
    os.write(d / "b" / "task.conf", "module = m\nhidden-spec = h\nfeature = f\ncut-files = [x, y]\ngate-visible = true\n", createFolders = true)
    assertEquals(TaskSpec.load(d, "b").map(t => (t.cutFiles, t.gateVisible)), Right((List("x", "y"), true)))
    os.write(d / "c" / "task.env", "TASK_MODULE=\"m\"\n", createFolders = true)
    assert(TaskSpec.load(d, "c").left.exists(_.contains("task.env:1")))

  test("Vars.expand"):
    assertEquals(Vars.expand("{repo}/x", Map("repo" -> "/r")), Right("/r/x"))
    assert(Vars.expand("{nope}/x", Map("repo" -> "/r")).isLeft)

  // deferred minor (Task 3): a relative path threw (os.Path) instead of a config error naming the key
  test("relative target.repo / holdout-repo / settings-source are config errors naming the key"):
    def load(set: String) = Load.experiment(tempConf(set))
    assertEquals(load("target.repo = rel/skreeep2\ntarget.settings-source = /abs/s.json"),
      Left("target.repo: expected an absolute path, got rel/skreeep2"))
    assertEquals(load("target.holdout-repo = rel/h"), Left("target.holdout-repo: expected an absolute path, got rel/h"))
    assertEquals(load("target.settings-source = s.json"), Left("target.settings-source: expected an absolute path, got s.json"))

  test("a missing experiment file is a config error naming the file"):
    val f = os.temp.dir() / "nope.conf"
    assertEquals(Load.experiment(f), Left(s"experiment file $f not found"))
