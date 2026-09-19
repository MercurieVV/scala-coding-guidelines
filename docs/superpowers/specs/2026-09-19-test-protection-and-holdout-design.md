# Test protection and the holdout suite — design (2026-09-19)

**Status:** approved design, not yet implemented.
**Origin:** Tier 1, item 1 of `wiki/llm-scala-fusion/roadmap-more-scala-less-llm.md`.
**Evidence:** `wiki/llm-scala-fusion/llm-code-defect-taxonomy.md` §7 (ImpossibleBench: agents delete or weaken failing tests; SpecBench: visible-suite saturation, held-out gap grows with code size).
**First consumer:** `skreeep2` and its `.dsh/` harness. Migrates with Plan C (`docs/2026-09-19-reengineering-kit-plan-c-harness.md`); this design does not depend on Plan C landing first.

## Goal

Make "the tests pass" a trustworthy completion signal by removing the agent's two cheats: editing the tests it is
judged by, and reading a held-out suite well enough to special-case it. Both are enforced by the harness (hooks that
deny), not by steering text. The human writes tests, properties and scenarios; the agent inhabits them (R-8, R-9).

## Decisions taken during brainstorming

| Question | Decision |
|---|---|
| Foundation | Extend the existing `frozen-paths` engine in `skreeep2/.dsh/` (kind-tagged entries), not a standalone hook, not Plan C first |
| When may the agent write a test file | Never. All test roots are frozen; the human unfreezes by editing the committed `frozen-paths` |
| May the agent read the holdout suite | No. New `sealed` kind denies reads and writes |
| When does the holdout suite run, what does the agent see | Every dirty turn-end, after `preCommit`; agent sees pass/fail counts and failing test *names* only |
| Scope | `skreeep2` only. Template scaffold deferred to Plan C |
| Encoding | **A**: optional leading kind token in `frozen-paths`; single file, single review surface |

## §1 Freeze grammar and kinds

Entry: `[kind<TAB>]path<TAB or 2+ spaces>reason`.

- Kind absent → `frozen`: writes denied, reads allowed (today's semantics, byte-for-byte compatible).
- Kind `sealed` → writes **and** reads denied. Read denial covers the `read`/`grep`/`glob` tools, `annotated_source`,
  and the bash text-reader list the semantic guard already recognises (`grep|rg|ag|ack|cat|sed|awk|head|tail|less|more|nl`).
  The `# semantic-fallback:` marker does **not** release a sealed read.
- Unknown kind token → malformed entry → `check-harness.sh` fails (same treatment as a missing reason). Never skipped.
- Path shapes unchanged: `path`, `dir/`, `prefix/**`. Union of committed `.dsh/frozen-paths` and machine-local
  `.dsh/.state/frozen-paths` unchanged; the union takes the *stricter* kind per path, so a machine-local file can add a
  seal but never lift or weaken one.
- Denial text quotes kind and reason: `sealed: holdout suite — the gate runs it; you do not read it`.

`lib.sh`:
- `dsh_frozen_paths` emits `<kind> TAB <shape> TAB <path> TAB <reason>` (one new leading column).
- `dsh_frozen_reason <path> [mode]` — `mode` is `write` (default; matches every kind) or `read` (matches `sealed` only).
  Existing callers pass no mode and keep their behaviour.

`skreeep2/.dsh/frozen-paths` additions (reasons are illustrative; every entry needs one):

```
sealed	holdout/**	holdout suite: run by the gate at turn-end, never read or edited by the agent
sealed	.dsh/.state/holdout.log	full holdout output is the human's; the agent gets names only
engine/test/**	tests are the human's spec; ask in chat to unfreeze a file
wiring/test/**	tests are the human's spec; ask in chat to unfreeze a file
… one line per module test root the coverage map marks `test` …
```

## §2 Holdout module, contract, turn-end report

**Mill module.** `object holdout extends ScalaTests with <the same test mixin as the modules it exercises>` under
`holdout/src`, depending on the app/wiring modules it composes. Content is human-written, cross-feature scenarios
(SpecBench: compose the specified features as real usage would). It is **not** reachable from `preCommit`'s test
aggregate, so `./mill __.test` run by the agent never executes it; only the gate does.

**Contract.** One new line: `holdout = ./mill -j 1 --ticker false holdout.test`. Absent → the gate skips the step
silently; `check-harness.sh` warns (a project without a holdout suite is legal, only weaker).

**turn-end hook.** After `preCommit` (still only when the tree is dirty), run the `holdout` command. The hook filters
the runner output and prints to the agent exactly:

```
holdout: 14 passed, 2 failed
  - PlaybackHoldout.gain-then-seek-keeps-position
  - PlaybackHoldout.stop-releases-device
```

Names only — no assertion text, diff or stack trace. Full output is written to `.dsh/.state/holdout.log` (sealed, §1).
Name extraction is a per-runner regex (weaver, munit); if the output does not match, print
`holdout: N failed, names unavailable` and leak no raw line. A receipt line (`holdout <pass> <fail> <commit> <utc>`) is
appended to `.dsh/.state/receipts` for R-8.

Exit code: holdout failure is a **signal, not a block** at turn-end (exit 0). `prePush` — run by the human — is where a
failing holdout suite blocks.

## §3 Harness self-protection and installation checks

**Self-freeze** (committed `frozen-paths`; kind `frozen`, reads stay allowed so the agent can still read the rules):

```
.dsh/frozen-paths	the freeze list is the human's; ask in chat to unfreeze
.dsh/contract	the gate interface is the human's
.dsh/hooks.json	the hook wiring is the human's
.dsh/gate.sh	the engine is the human's
.dsh/lib.sh	the engine is the human's
.dsh/hooks/**	the engine is the human's
.dsh/check-harness.sh	the engine is the human's
```

`.dsh/.state/**` is not frozen: hooks write it, and an agent adding to `.dsh/.state/frozen-paths` can only tighten.

**Blind spots, stated:** `tool-policy` already denies `rm/mv/ln/truncate/patch/git checkout|restore|rm|mv|apply/perl -i/
find -delete` on frozen operands, and now on sealed ones too. `bash -c '…'`, `$(…)` and heredoc-to-interpreter writes
remain undetected — the README lists them today; the seal inherits the list. The `workspace-write` sandbox (Plan C) is
the second layer and is unchanged here.

**`check-harness.sh` new rules** (fail unless marked warn):

1. Every module the coverage map marks `test` has a `frozen` or `sealed` entry covering its test root; else fail,
   naming the module.
2. The path `contract.holdout` targets (default `holdout/**`) is `sealed`; else fail.
3. Each of the seven self-freeze paths is present; else fail.
4. Unknown kind token in any entry; fail.
5. `contract` has no `holdout` line; **warn**.
6. The holdout module is reachable from `preCommit`'s test aggregate; fail (the agent could otherwise run it and read
   the failure text).

**Unfreeze workflow.** Only a committed edit to `.dsh/frozen-paths` lifts a freeze — a reviewed diff. The agent asks in
chat; every denial message says so.

## §4 Testing and verification

Harness suite: `.dsh/tests/run.sh`, `dash`-clean, cases under `.dsh/tests/cases/`. New cases:

*Parser (`lib.sh`)*
- Untagged entry → kind `frozen`; all existing cases stay green (regression).
- `sealed<TAB>path<TAB>reason` → kind `sealed`.
- Unknown kind → surfaced as malformed by `check-harness`, not skipped.
- Union: machine-local `sealed` over committed `frozen` → `sealed`; committed `sealed` over machine-local untagged → `sealed`.

*Guards*
- `write`/`edit` on sealed and on frozen path → exit 2; message quotes kind + reason.
- `read`/`grep`/`glob`/`annotated_source` on sealed path → exit 2; on frozen path → exit 0.
- Bash `cat holdout/src/X.scala`, `sed -n … holdout/…`, `cd holdout && cat src/X.scala` → exit 2.
- `# semantic-fallback:` marker on a sealed read → exit 2.
- Bash `rm -rf holdout`, `git checkout -- engine/test` → exit 2 via tool-policy.

*turn-end*
- Stub `holdout` command emitting canned weaver output with 2 failures → agent-facing stdout is exactly the names-only
  block; `.dsh/.state/holdout.log` holds the full text; receipt appended; exit 0.
- Unparseable runner output → `holdout: N failed, names unavailable`; no raw line on stdout.
- Contract without `holdout` → step skipped, nothing printed.

*check-harness*
- One negative fixture per rule 1–6 (rule 5 asserts a warning, not a failure).

*Live smoke* (recorded in the implementation report, not automated): in a `.dsh/dsh.sh` session — read a holdout file
(denial), edit `.dsh/frozen-paths` (denial), end a dirty turn (names-only holdout block appears).

## Out of scope

Sandbox mode changes; Plan C migration; template (`scala-llm-template`) scaffold; phase markers / TDD-phase-aware
unfreezing; baseline-diff rules; changes to the semantic guard's index coverage.

## Files touched (skreeep2)

- `.dsh/lib.sh` — kind column, `dsh_frozen_reason` mode.
- `.dsh/hooks/semantic-guard.sh` — read denial for `sealed`.
- `.dsh/hooks/tool-policy.sh` — sealed operands (reuses frozen path).
- `.dsh/hooks/turn-end.sh` — holdout step, filtering, log, receipt.
- `.dsh/check-harness.sh` — rules 1–6.
- `.dsh/frozen-paths`, `.dsh/contract` — entries and the `holdout` line.
- `.dsh/README.md` — grammar, kinds, holdout, unfreeze workflow, blind spots.
- `build.mill` — `holdout` module, excluded from `preCommit`'s test aggregate.
- `holdout/src/**` — first human-written scenarios (content is the human's).
- `.dsh/tests/cases/**` — cases in §4.
