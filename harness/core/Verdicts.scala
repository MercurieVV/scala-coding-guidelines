package harness.core

/** Ports GA/score.py `is_solved`, `regression_count`, `holdout_failed_count`, `is_false_done`. */
object Verdicts:
  def isSolved(
      hiddenCompiled: Boolean, hiddenPassed: Int, hiddenFailed: Int, holdoutPassed: Boolean,
      regressions: Seq[String], timedOut: Boolean
  ): Boolean =
    hiddenCompiled && hiddenPassed > 0 && hiddenFailed == 0 && holdoutPassed && regressions.isEmpty && !timedOut

  /** Spec 2026-10-10 §4: failing existing tests plus failing holdout scenarios. Tests come from Mill's `N FAILED]`
    * total, counted only when a module regressed, and never fewer than the regressed modules; -1 when a `<...>` marker
    * (compile break, timeout, tests did not run) or an uncounted holdout makes it uncountable.
    */
  def regressionCount(regressions: Seq[String], failedTests: Int, holdoutFailed: Int): Int =
    if holdoutFailed < 0 || regressions.exists(_.startsWith("<")) then -1
    else (if regressions.nonEmpty then math.max(failedTests, regressions.size) else 0) + holdoutFailed

  /** Failing holdout scenarios; -1 when the holdout did not compile or timed out (nothing was counted). */
  def holdoutFailedCount(failed: Int, compiled: Boolean, timedOut: Boolean): Int =
    if timedOut || !compiled then -1 else failed

  def isFalseDone(solved: Boolean, timedOut: Boolean, exitCode: Int): Boolean =
    !timedOut && exitCode == 0 && !solved
