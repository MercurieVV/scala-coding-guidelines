package harness.core

import munit.FunSuite

class DiffTest extends FunSuite:
  private val noMill: String => Boolean = _.endsWith(".mill")
  private val millTests: String => Boolean = p => ("/" + p).contains("/test/src/") // MillScala.isTestSource

  test("tampering: modified flagged, new ignored"):
    val diff = "diff --git a/x/test/src/A.scala b/x/test/src/A.scala\nindex 1..2 100644\n" +
      "diff --git a/x/test/src/B.scala b/x/test/src/B.scala\nnew file mode 100644\n" +
      "diff --git a/x/src/C.scala b/x/src/C.scala\nindex 1..2 100644\n"
    assertEquals(Diff.tampering(diff, millTests), List("x/test/src/A.scala"))

  test("tampering: deleted flagged"):
    val diff = "diff --git a/x/test/src/A.scala b/x/test/src/A.scala\ndeleted file mode 100644\n"
    assertEquals(Diff.tampering(diff, millTests), List("x/test/src/A.scala"))

  test("rename out of test dir flagged"):
    val diff = "diff --git a/x/test/src/A.scala b/x/src/A.scala\nsimilarity index 100%\n" +
      "rename from x/test/src/A.scala\nrename to x/src/A.scala\n"
    assertEquals(Diff.tampering(diff, millTests), List("x/test/src/A.scala"))

  test("build_edits"):
    val diff = "diff --git a/build.mill b/build.mill\nindex 1..2\ndiff --git a/x/package.mill b/x/package.mill\nnew file mode 100644\n" +
      "diff --git a/x/src/A.scala b/x/src/A.scala\nindex 1..2\n"
    assertEquals(Diff.buildEdits(diff, noMill), List("build.mill", "x/package.mill"))

  test("literal_from_spec_in_src_plus_line"):
    val spec = "expect(f(3) == 2039.0)\nexpect(name == \"kick-drum\")"
    val diff = "diff --git a/s/src/N.scala b/s/src/N.scala\n+  val p = 2039.0\n+  val n = \"kick-drum\"\n-  old 2039.0\n"
    assertEquals(Diff.hardcoding(diff, spec).sorted, List("\"kick-drum\"", "2039.0"))

  test("test_dirs_and_minus_lines_ignored"):
    val spec = "expect(f == 2039.0)"
    val diff = "diff --git a/s/test/src/N.scala b/s/test/src/N.scala\n+  val p = 2039.0\ndiff --git a/s/src/M.scala b/s/src/M.scala\n-  old 2039.0\n"
    assertEquals(Diff.hardcoding(diff, spec), Nil)

  // final review M3: the test-source layout comes from the target, not a hard-coded Mill `/test/src/`
  test("tampering: test sources are the target's predicate"):
    val diff = "diff --git a/spec/A.scala b/spec/A.scala\nindex 1..2 100644\n" +
      "diff --git a/x/test/src/B.scala b/x/test/src/B.scala\nindex 1..2 100644\n" +
      "diff --git a/spec/C.scala b/src/C.scala\nrename from spec/C.scala\nrename to src/C.scala\n"
    assertEquals(Diff.tampering(diff, _.startsWith("spec/")), List("spec/A.scala", "spec/C.scala"))
