# Local probe: fixStrict passed when scalafix could not run

> Source: local measurement, this repository's owner — `fixStrict` (`GateLayer` in scala-llm-template `kit/recipes/mill/Gate.mill`, copied into skreeep2 as `buildKit/Gate.mill`) run in a detached skreeep2 worktree at 4009fc0, before and after scala-llm-template d122d6a (skreeep2 d347476)
> Collected: 2026-10-01
> Published: 2026-10-01

Measurement record. Outputs copied from the commands as run (ANSI colour codes removed).

## Cause

`fixStrict` calls scalafix's `ScalafixArguments#evaluate()`. Before the fix it logged `eval.getErrorMessage` and then
returned `Success` whenever the list of diagnostics was empty. A run scalafix could not start (an unknown rule, a bad
config) returns no file evaluations, so no diagnostics, so `Success`.

## Before the fix

`.scalafix.conf` with the rule `OrganizeImports` renamed to `NoSuchRuleXyz`:

```
score.fixStrict [error] Unknown rule 'NoSuchRuleXyz'
266/266, SUCCESS] mill score.fixStrict 21s
exit=0
```

## The fix

- A run-level error fails the task: `fixStrict: scalafix could not run: <msg>`.
- A file scalafix could not evaluate fails the task: `fixStrict: N file(s) failed:` followed by
  `could not evaluate <file>: <why>`, appended to the diagnostics message when both occur — the same handling
  `fixRanges` already had.
- The engine's feedback parser (`lib.sh`) recognises both lines as scalafix output, so the agent reads an INFRA
  record naming the cause instead of `UNREADABLE: runner output not recognised`.

## After the fix

Same broken config, run as the gate runs it (`./mill --ticker false score.fixStrict`):

```
[error] score.fixStrict fixStrict: scalafix could not run: Unknown rule 'NoSuchRuleXyz'
exit=1
```

Rendered by the engine:

```
FEEDBACK: 0 errors, 0 warnings (score.fixStrict)
INFRA: score.fixStrict fixStrict: scalafix could not run: Unknown rule 'NoSuchRuleXyz'
```

Controls:

```
clean config:            266/266, SUCCESS] mill score.fixStrict 5s   exit=0
real lint finding:       [error] score.fixStrict scalafix found 1 diagnostic(s):
                         [WARNING][PreferHKTTypeclasses.concrete-constructor-match] .../ProbeLint.scala:6:10 ...
                         exit=1
```

The file with a lint finding was not reported as unevaluated. The unevaluated-file path (stale SemanticDB, a parse
error) was not reproduced live; it is covered by a parser test only.

## Tests

3 new lib-test cases, failing before the `lib.sh` change. On the merged template master, under both sh and dash:
lib-test 742 passed, 0 failed; run.sh 483 passed, 0 failed; the other suites 0 failed. In skreeep2 after the
re-vendor: check-harness 80 passed, 0 failed; `mill preCommit` SUCCESS across all modules.
