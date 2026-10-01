# Local probe: the turn-end holdout run had no time cap

> Source: local measurement, this repository's owner — the turn-end gate (scala-llm-template `kit/harness/hooks/turn-end.sh`) run against a scratch repository with the engine's stub Mill, before and after scala-llm-template 7d23e59 (skreeep2 691d49a); skreeep2's real holdout timed at d0b28f2
> Collected: 2026-10-01
> Published: 2026-10-01

Measurement record, 2026-10-01. Outputs copied from the commands as run.

## Cost of the real holdout in skreeep2

`./mill -j 1 --ticker false holdout.test` (the contract's `holdout` command), output discarded (sealed):

```
run1 rc=0 secs=6
run2 rc=0 secs=1
```

## Before the fix

Scratch repository, checks green, contract `holdout = sleep 40`, `DSH_STOP_BUDGET=20`:

```
rc=0 secs=40
holdout: result unreadable
```

## Cause

`turn-end.sh` ran the holdout as `( cd "$root" && sh -c "$holdout_cmd" )` with no cap, after the turnEndTests and
vericoding stages had spent their share of the 540 s default `DSH_STOP_BUDGET`. turnEndTests (`DSH_TESTS_CAP`) and
Stainless (`DSH_STAINLESS_CAP`) were already capped by that budget; the holdout was not. Claude Code kills a Stop
hook at its 600 s timeout, and the turn then ends with no verdict.

## The fix

The run goes through `dsh_bounded_probe`, capped at `min(DSH_HOLDOUT_CAP (default 120), what is left of
DSH_STOP_BUDGET)`. Killed at the cap: `holdout: did not finish within <N> s — not verified`. With 10 s or less left
it is not started: `holdout: not started — the Stop hook's budget`. It stays a signal, never a block; the receipt row
records `-` counts.

## After the fix

Same scratch repository and settings:

```
rc=0 secs=20
holdout: did not finish within 19 s — not verified (DSH_HOLDOUT_CAP, default 120, bounded by what was left of DSH_STOP_BUDGET); the partial output is the human's .dsh/.state/holdout.log
holdout	-	-	74dec7d
no sleep left
```

## Tests

2 new fixture cases (`turn-end-holdout-cap-killed`, `turn-end-holdout-budget-not-started`), both failing before the
fix. Suites after: run.sh 492 passed, lib-test 742, health 119, module-map 69, feedback 33, vocabulary 39,
instructions 6, install 61; 0 failed under sh and dash. skreeep2 check-harness after the re-vendor: 80 passed,
0 failed.
