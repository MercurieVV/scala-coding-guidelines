# Local probe: a delete-only turn ended green at the Stop gate

> Source: local measurement, this repository's owner — the turn-end gate (scala-llm-template `kit/harness/hooks/turn-end.sh`, vendored into skreeep2 as `.dsh/engine/`) run in a detached skreeep2 worktree at d347476 (engine d122d6a), before and after scala-llm-template 834d4ea (skreeep2 d0b28f2)
> Collected: 2026-10-01
> Published: 2026-10-01

Measurement record, 2026-10-01. Outputs copied from the commands as run (ANSI colour codes removed).

## Setup

SessionStart baseline recorded on a clean tree (`recorded 0 pre-existing dirty path(s)`), then the session's only
change: `git rm core/src/com/github/mercurievv/skreeep2/RelativeTime.scala`. The module map row for `core`:

```
core	core	fmt,scalafix	app,backend.cpu,engine.details,engine.shape,handoff.arhitecture,handoff.hostdriven,handoff.jdkAudio,handoff.openclHost,plugin,signals,wiring.arhitecture,wiring.details
```

## Before the fix

Stop gate:

```
rc=0 secs=1
```

No output at all. The same tree, compiled by hand:

```
./mill -k app.compile
rc=1
value RelativeTime is not a member of com.github.mercurievv.skreeep2
Not found: type RelativeTime
[error] engine.details.compile Compilation failed
939/939, 1 FAILED] mill app.compile 22s
```

## Cause

`dsh_touched_files` (lib.sh) keeps only `.scala`/`.sc` files that still exist (`[ -f "$_root/$_path" ] || continue`),
so a deleted source file is no touched file, no module is mapped, and the gate takes its "nothing to run" exit. The
module map itself answers for the missing path (`module-map.sh core/src/.../RelativeTime.scala` printed
`core	fmt,scalafix`, rc=0). A second gap stood behind it: the errors a deletion causes name the files that USED the
deleted one, which the turn did not touch, so the attribution rule would have filed them as notes. The engine's own
fixture `turn-end-deleted-scala-ok` encoded the old behaviour: "A deleted path is nothing to check".

## The fix

- turn-end: every `.scala`/`.sc` path git reports as changed that no longer exists — plain or staged delete, the old
  name of a rename or move — is a deletion. Its owning module joins the touched modules (compile chain, then its
  dependents' turnEndTests), and the module's directory joins the attribution spellings. Messages name
  `<module> lost <path>`; the module's remedy adds "restore it … or update the code that used it".
- baseline: deleted `.scala`/`.sc` paths are recorded with hash `-`, so a file already gone at SessionStart is not the
  session's deletion.

## After the fix

```
rc=2 secs=15
BLOCKED by the DSH gate: this turn left failing the commit gate: app
Source files this turn deleted (an error anywhere in a module that lost a file is attributed to this turn):
core lost core/src/com/github/mercurievv/skreeep2/RelativeTime.scala

--- turnEndTests: app (downstream of core) failed (exit 1) with no failure records — see the log ---
FEEDBACK: 9 errors, 0 warnings (app.test)
DIAGNOSTIC: engine/details/src/com/github/mercurievv/skreeep2/engine/Perform.scala:76:14 [scalac.not-found] Not found: type RelativeTime
...
11 downstream modules not run — fix this one first: backend.cpu engine.details engine.shape handoff.arhitecture handoff.hostdriven handoff.jdkAudio handoff.openclHost plugin signals wiring.arhitecture wiring.details
```

## Tests

7 new fixture cases `turn-end-deleted-*` (same-module block, staged delete, downstream block, green allow, deleted
before the session, unmapped path, rename across modules); 5 failed before the fix. `turn-end-deleted-scala-ok`
became `turn-end-deleted-scala-checks-owner`. Suites after: run.sh 490 passed, lib-test 742, health 119, module-map
69, feedback 33, vocabulary 39, instructions 6, install 61; 0 failed under sh and dash. skreeep2 check-harness after
the re-vendor: 80 passed, 0 failed.
