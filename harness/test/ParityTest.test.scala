package harness.parity

import munit.FunSuite

class ParityTest extends FunSuite:
  test("static diff: numbers numerically (ujson 1 = python 1.0), values, lengths"):
    assertEquals(StaticParity.diff("f", ujson.read("""{"a":1,"b":[1.0,"x"]}"""), ujson.read("""{"a":1.0,"b":[1,"x"]}""")), Nil)
    assertEquals(StaticParity.diff("f", ujson.Num(0.5), ujson.Num(0.25)), List("f: GA 0.5, harness 0.25"))
    assertEquals(StaticParity.diff("f", ujson.Arr("a", "b"), ujson.Arr("b", "a")),
      List("f[0]: GA \"a\", harness \"b\"", "f[1]: GA \"b\", harness \"a\""))
    assertEquals(StaticParity.diff("f", ujson.Arr("a"), ujson.Arr()),
      List("f: length GA 1, harness 0; first difference at [0]: GA \"a\", harness (none)"))
    assertEquals(StaticParity.diff("f", ujson.Null, ujson.Str("x")), List("f: GA null, harness \"x\""))

  test("static diff: object key order is checked once the values agree"):
    val ga = ujson.read("""{"tool-policy":2,"turn-end":1}""")
    assertEquals(StaticParity.diff("g", ga, ujson.read("""{"turn-end":1,"tool-policy":2}""")),
      List("g: key order GA [tool-policy, turn-end], harness [turn-end, tool-policy]"))
    assertEquals(StaticParity.diff("g", ga, ujson.read("""{"tool-policy":2}""")),
      List("g: keys GA [tool-policy, turn-end], harness [tool-policy]"))

  test("static run dirs: runs/ and invalid-runs/ at any depth, never inside a run's repo"):
    val root = os.temp.dir(prefix = "parity-test")
    try
      List(root / "runs" / "a-g-1", root / "invalid-runs" / "batch" / "b-u-2", root / "runs" / "a-g-1" / "repo" / "x")
        .foreach(d => os.write(d / "run.json", "{}", createFolders = true))
      os.write(root / "base" / "t" / "run.json", "{}", createFolders = true)
      val refs = StaticParity.runDirs(root, os.Path("/orig/gate-eval"))
      assertEquals(refs.map(r => (r.dir.relativeTo(root).toString, r.orig.toString)),
        List("invalid-runs/batch/b-u-2" -> "/orig/gate-eval/invalid-runs/batch/b-u-2", "runs/a-g-1" -> "/orig/gate-eval/runs/a-g-1"))
    finally os.remove.all(root)

  test("arm setup script: GA run.sh from the argument check through the base tag, lib.sh sourced"):
    val ga = os.temp.dir(prefix = "parity-ga")
    try
      os.write(ga / "run.sh", List("#!/bin/sh", "set -u", "GA_LIB_PATH=x; . \"$GA_LIB_PATH\"", "[ $# -eq 3 ] || ga_die usage",
        "task=$1", "( cd r && git commit -qam a && git tag base )", "pok=true").mkString("\n") + "\n")
      assertEquals(ArmParity.gaSetupScript(ga),
        List("set -u", s"GA_LIB_PATH='${ga / "lib.sh"}'; . \"$$GA_LIB_PATH\"", "[ $# -eq 3 ] || ga_die usage", "task=$1",
          "( cd r && git commit -qam a && git tag base )").mkString("\n") + "\n")
    finally os.remove.all(ga)

  test("arm compare: byte differences and files on one side only"):
    val b = "x".getBytes.toSeq
    assertEquals(ArmParity.compare("g", Map("a" -> b, "b" -> b), Map("a" -> "y".getBytes.toSeq, "c" -> b)),
      List("arm g: a differs", "arm g: b only in GA's setup", "arm g: c only in the harness setup"))
