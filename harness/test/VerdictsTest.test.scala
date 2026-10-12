package harness.core

import munit.FunSuite

class VerdictsTest extends FunSuite:
  private def solved(
      hiddenCompiled: Boolean = true, hiddenPassed: Int = 5, hiddenFailed: Int = 0,
      holdoutPassed: Boolean = true, regressions: Seq[String] = Nil, timedOut: Boolean = false
  ) = Verdicts.isSolved(hiddenCompiled, hiddenPassed, hiddenFailed, holdoutPassed, regressions, timedOut)

  test("solved requires everything"):
    assert(solved())
    assert(!solved(hiddenCompiled = false))
    assert(!solved(hiddenPassed = 0))
    assert(!solved(regressions = List("control.test")))
    assert(!solved(timedOut = true))

  test("false done"):
    assert(Verdicts.isFalseDone(solved = false, timedOut = false, exitCode = 0))
    assert(!Verdicts.isFalseDone(solved = false, timedOut = true, exitCode = 124))
    assert(!Verdicts.isFalseDone(solved = true, timedOut = false, exitCode = 0))

  test("counts failing tests not modules"):
    assertEquals(Verdicts.regressionCount(List("control.test"), 7, 2), 9)

  test("unparsed test count falls back to modules"):
    assertEquals(Verdicts.regressionCount(List("control.test", "app.test"), 0, 2), 4)

  test("baseline failures do not count without a regression"):
    assertEquals(Verdicts.regressionCount(Nil, 3, 0), 0)

  test("clean"):
    assertEquals(Verdicts.regressionCount(Nil, 0, 0), 0)

  test("marker is uncountable"):
    assertEquals(Verdicts.regressionCount(List("<build>"), 0, 0), -1)
    assertEquals(Verdicts.regressionCount(List("app.test", "<tests did not run>"), 0, 1), -1)

  test("uncounted holdout is uncountable"):
    assertEquals(Verdicts.regressionCount(Nil, 0, -1), -1)

  test("holdout failed count"):
    assertEquals(Verdicts.holdoutFailedCount(3, true, false), 3)
    assertEquals(Verdicts.holdoutFailedCount(0, false, false), -1)
    assertEquals(Verdicts.holdoutFailedCount(0, true, true), -1)
