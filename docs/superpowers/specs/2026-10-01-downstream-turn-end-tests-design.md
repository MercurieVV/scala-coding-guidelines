# Running the touched modules' dependents' tests at turn-end

**Status:** approved design, 2026-10-01. Engine: `scala-llm-template/kit/harness` (vendored into projects as `.dsh/engine/`);
Mill side: `kit/recipes/mill/Gate.mill` (copied into projects as `buildKit/Gate.mill`). Roadmap: the TDAD test-map
follow-up to Tier 3 item 7.

## Context (measured)

- **Evidence.** TDAD cut SWE-bench Verified regressions from 6.08% to 1.82% by telling the agent which tests a change puts
  at risk; generic TDD instructions without that context raised them to 9.94%. Caveats from its full text: small local
  models only, Phase 1 resolution 31% → 29% (more empty patches), the Phase 2 gain is 6/25 → 8/25 with 0% regressions in
  both arms, Python only. TestPrune (independent): a minimised relevant regression subset gave an 8.0%-12.9% relative
  resolution gain across Agentless, SWE-Agent and Trae. TensorBench (frontier agents): 16.1% (Claude 4.7) to 45.2% (Qwen3)
  of applied patches break at least one pre-existing test.
- **The gap in our gate.** `turn-end.sh` builds `$tmpd/mods` from the modules that *own* a touched file and runs only
  their `turnEndTests`. Modules that depend on them are never tested. `tool-policy.sh` denies the agent `__.test` and
  `prePush` (they reach the sealed holdout), so the agent cannot easily run the whole suite either.
- **Size of the gap in skreeep2** (dependents read from Mill's own `__.compileClasspath`): 21 of 29 mapped modules have
  at least one dependent module with tests that turn-end never runs. Editing `core` leaves 13 suites unrun, `signals` 12,
  `handoff.arhitecture` 11.
- **Cost in skreeep2** (one `./mill -j 1 --ticker false <m>.test` per suite, compiled): 15 suites take 1.2 s-4.7 s each;
  `bench` takes about 32 s. Worst case (`core`): 13 suites, 56.9 s; 24.4 s without `bench`. The Stop budget is 540 s.
- **`bench` is nondeterministic.** Red in 3 of 3 runs, each time with a different 1-3 of `UnderrunSpec`'s
  `cnt=… — real-time budget 6s` cases failing. Gating every `core` edit on it would block the agent on noise.
- **TDAD's own mechanism (test-level, symbol → tests) is not feasible yet:** of 46 test files in skreeep2, few have
  SemanticDB output — most test modules do not emit it. Out of scope here (see below).

## Decisions

The build declares each module's dependents; the gate runs their tests at turn-end and **blocks** on red, inside the
existing time cap, budget and attempt cap. Rejected: notes only (TDAD's advisory mode, measured only on small models — the
harness enforces wherever it can); running every suite every turn (63.2 s regardless of the change, `bench` included).

## §1 The build declares dependents (`Gate.moduleMapRows`)

- `moduleMapRows` gains a parameter `downstreamExcluded: Seq[Module] = Nil` and a **fourth column**: the ids of the
  modules whose `test` suite depends on this module, comma-separated, sorted, possibly empty.
- Module `D` is listed for module `M` when `D` has the `tests` capability, `D != M`, `D` is not in `downstreamExcluded`,
  and `M` is among the transitive module dependencies of `D.test` as Mill resolves them for compilation
  (`moduleDeps` and `compileModuleDeps`, recursively; the test module's own outer module counts as a dependency of the
  test module, so `M`'s own suite is excluded by `D != M`, not by omission).
- A project excludes nondeterministic or slow suites at the call site; skreeep2 passes `Seq(bench)`.
- Every reader of the map (below) keeps reading fields 1-3 by position; the fourth column is additive.

## §2 The map in the engine (`module-map.sh`)

- `map_clean` accepts rows of **three or four** fields. Field 4, when present, must match
  `^([A-Za-z0-9._-]+(,[A-Za-z0-9._-]+)*)?$`. A four-field row with any other field 4 is malformed (exit 3, as today).
- Default output is unchanged: `<module id> TAB <capabilities>`, or `-`. Readers in `semantic-guard.sh`, `post-edit.sh`,
  `lib.sh` (`dsh_*` helpers reading `modules.tsv` by `$1`/`$2`/`$3`) are audited and stay correct with four fields.
- New query: `module-map.sh --dependents <module id>` prints the module's dependents, one id per line, and exits 0
  (nothing printed when the row has no fourth column or an empty one). Exit 3 under the same conditions as a path
  lookup (missing or stale map that cannot be rebuilt, malformed map). An id with no row: exit 0, nothing printed.
- The cache rule is unchanged: a map older than `build.mill` is rebuilt, so the column appears once the build emits it.

## §3 Turn-end: downstream stage (`turn-end.sh`)

Runs immediately after the owners' `turnEndTests` loop, before the vericoding stage, with the same shared budget.

1. **Which modules.** For each module in `$tmpd/mods` that had at least one touched file *outside* `<module dir>/test/`
   (from `$tmpd/modfiles`; a test-only change cannot break a dependent), collect `module-map.sh --dependents`. Union,
   minus every module already in `$tmpd/mods`, sorted by id, each recorded with the first touched module (by id) that
   pulled it in.
2. **When.** Only if the owners' loop ended without a block (`te_stopped=0`). If an owner blocked, the downstream modules
   are not run and one note says `downstream turnEndTests not run — <owner> failed first (<n> modules waiting)`.
3. **How.** Each dependent runs its `turnEndTests` contract command exactly as an owner does: `dsh_bounded_probe`, cap
   `min(DSH_TESTS_CAP, what is left of DSH_STOP_BUDGET)`, same draft/non-draft verdict rule, same feedback adapters.
   Stop at the first blocking module; the rest are counted and named in one note.
4. **Headings.** Block: `turnEndTests: <D> (downstream of <M>) (failed)`. Green: `turnEndTests: <D> (downstream of <M>)
   green`. Cap expiry, no-budget and not-started notes reuse the owners' wording with `(downstream of <M>)` inserted,
   on the same `vk_note` channel.
5. **Remedy line** on a downstream block: "your change to `<M>` broke `<D>`'s tests — fix `<M>` (or update `<D>` if the
   change is intended); run `./mill <D>.test` to reproduce".
6. **Fail closed.** `--dependents` exit 3 for a touched module → block with its reason (the owners' lookup already fails
   closed on the same map; this keeps the two consistent).
7. **Attempt cap.** A downstream block counts toward `max_attempts` like any other block.

## §4 Health check (`check-harness.sh`)

When `modules.tsv` exists and **no** row has a fourth column: `WARN downstream tests disabled — the module map has no
dependents column; update buildKit/Gate.mill (moduleMapRows) and re-run ./mill moduleMap`. Not a FAIL: a project on the
old recipe gets today's behaviour, not a broken gate.

## §5 Testing and delivery

- Engine fixtures (`tests/cases`), test-first, with a stub map and stub contract commands:
  - a touched module whose dependent's stub fails → block with the downstream heading and remedy;
  - dependent green → allow, green line names it;
  - the touched file is under `<dir>/test/` only → dependents not run;
  - an owner fails → downstream not run, one note with the count;
  - two touched modules sharing a dependent → it runs once;
  - a dependent already among the owners → not run twice;
  - three-column map → no downstream stage, no block;
  - malformed fourth column → exit 3 path, block;
  - budget exhausted mid-downstream → remaining dependents noted, no block;
  - a dependent whose render carries only `DRAFT` records → note, not block.
- `lib-test`/`module-map-test` rows for `map_clean` (3 and 4 fields, bad field 4) and `--dependents`.
- `Gate.mill`: the recipe's own test (or a `mill.md` example build) shows the fourth column, the exclusion parameter, and
  that a module's own suite is never its own dependent.
- `check-harness` row for §4.
- Delivery: one plan in scala-llm-template (subagent-driven). Then skreeep2: copy the recipe into `buildKit/Gate.mill`,
  pass `Seq(bench)` at the `moduleMap` call site, re-vendor the engine, regenerate the map, and a live gated check: an
  agent change to a `core` symbol that breaks a dependent's test is blocked naming that dependent.

## Invariants

The gate never silently passes (a map it cannot read blocks; a map without the column is a health-check WARN); what the
gate checks only grows; the gate's interface (contract keys, hook names, default `module-map.sh` output) is unchanged.

## Out of scope

The test-level map (symbol → test classes through ScalaSemantic `find_usages`), which first needs SemanticDB in test
modules; baselining suites that are already red at session start (today's owners have the same property; projects
exclude nondeterministic suites at the call site); making `bench` deterministic.
