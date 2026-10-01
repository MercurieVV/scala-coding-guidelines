# Local measurement: skreeep2 dependents' tests at turn-end

> Source: local measurement, this repository's owner — skreeep2 Mill build (`./mill show '__.compileClasspath'`, per-suite `./mill -j 1 --ticker false <m>.test` timings), the harness engine's turn-end code, and the shipped rollout (scala-llm-template ee79514, skreeep2 c0be344)
> Collected: 2026-10-01
> Published: 2026-10-01

Measurement record for the TDAD test-map follow-up to roadmap item 7. Outputs copied from the commands as run.

## 1. The gap before the change

`kit/harness/hooks/turn-end.sh` built its module list from the modules that own a touched file and ran `turnEndTests`
only for those. `tool-policy.sh` denies the agent `__.test` and `prePush` (they reach the sealed holdout).

Dependents with tests per module, derived from Mill's own `__.compileClasspath` (a `<m>.test` classpath containing
`out/<M>/compile.dest/classes` makes `m` a dependent of `M`):

```
core                   13 app backend.cpu bench engine.details engine.shape handoff.arhitecture handoff.hostdriven handoff.jdkAudio handoff.openclHost plugin signals wiring.arhitecture wiring.details
signals                12 app backend.cpu bench engine.details engine.shape handoff.arhitecture handoff.hostdriven handoff.jdkAudio handoff.openclHost plugin wiring.arhitecture wiring.details
handoff.arhitecture    11 app backend.cpu bench engine.details engine.shape handoff.hostdriven handoff.jdkAudio handoff.openclHost plugin wiring.arhitecture wiring.details
modules with >=1 unrun downstream suite: 21 of 29
```

## 2. Cost

Per-suite wall time (compiled), seconds:

```
app 4.7 / backend.cpu 2.6 / bench 32.5 / control 1.5 / control.midi 1.4 / engine.details 1.2 / engine.shape 1.6
handoff.arhitecture 1.5 / handoff.hostdriven 2.2 / handoff.jdkAudio 1.6 / handoff.openclHost 2.4 / plugin 1.8
scope 3.4 / signals 1.9 / wiring.arhitecture 1.2 / wiring.details 1.7
```

```
core 13 56.9
signals 12 55.0
handoff.arhitecture 11 53.5
suite total 63.2 max downstream 56.9 without bench 24.4
```

## 3. bench is nondeterministic

`bench.test` was red in 3 of 3 runs, each run failing a different 1-3 of `UnderrunSpec`'s `cnt=… — real-time budget 6s`
cases (run 1: cnt=800; run 2: cnt=200, cnt=400, cnt=800; run 3: cnt=800).

## 4. Test-level map not available

Of 46 test files in skreeep2, most test modules emit no SemanticDB (e.g. `app` 8 files / 0 semanticdb, `backend/cpu`
12 / 0; `engine/shape` 4 / 4).

## 5. What shipped (scala-llm-template ee79514, skreeep2 c0be344)

- `Gate.moduleMapRows` writes a fourth map column — each module's dependents with tests, from Mill's
  `transitiveModuleCompileModuleDeps` of `<id>.test` — minus `downstreamExcluded`; skreeep2 passes `Seq(bench)`.
- `module-map.sh --dependents <id>`; turn-end runs the touched modules' dependents' `turnEndTests` after the
  vericoding stage, only when nothing earlier blocked, under the shared cap and budget; a red dependent blocks.
- Oracle against Mill's compile classpaths in skreeep2: `rows 30 mismatches 0`; `core` lists 12 dependents.
- Engine suites: 446 passed, 0 failed under both sh and dash; skreeep2 check-harness 80 passed, 0 failed.
