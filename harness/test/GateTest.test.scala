package harness.core

import munit.FunSuite

class GateTest extends FunSuite:
  test("isTurnEndText"):
    assert(Gate.isTurnEndText("Stop hook feedback: x"))
    assert(Gate.isTurnEndText("turn-end gate: BLOCKED"))
    assert(!Gate.isTurnEndText("turn-end only"))
    assert(!Gate.isTurnEndText("BLOCKED only"))

  test("gateBlocks ignores non-error calls and counts BLOCKED by name"):
    def call(r: String, err: Boolean) = ToolCall("Bash", ToolKind.Shell, ujson.Obj(), r, err, 1)
    val b = Gate.gateBlocks(Seq(call("BLOCKED by tool-policy: x", false), call("BLOCKED by turn-end: y", true), call("BLOCKED by a.b-c_1", true)), Nil)
    assertEquals(b, Map("a.b-c_1" -> 1))

  test("gateBlocks keeps first-seen key order for more than four hooks"):
    def call(r: String) = ToolCall("Bash", ToolKind.Shell, ujson.Obj(), r, true, 1)
    val names = List("zeta", "alpha", "mid", "beta", "omega", "gamma")
    val b = Gate.gateBlocks(names.map(n => call(s"BLOCKED by $n")) :+ call("BLOCKED by zeta"), Nil)
    assertEquals(b.keys.toList, names)
    assertEquals(b("zeta"), 2)
