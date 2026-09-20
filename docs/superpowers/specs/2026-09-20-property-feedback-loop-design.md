# The property feedback loop — design (2026-09-20)

**Status:** implemented in `skreeep2` `master` (merged 2026-09-20, 15 commits ending `0b65d62`); this text carries the
implementation-time amendments inline.
**Origin:** Tier 1, item 2 of `wiki/llm-scala-fusion/roadmap-more-scala-less-llm.md` (ScalaCheck ⇄ LLM: shrunk counterexample as the
feedback).
**Evidence:** `wiki/llm-scala-fusion/where-types-and-properties-buy-most.md` §5 — property-oriented, structurally minimal
feedback (the Property-Generated Solver) outperforms I/O-example tests; PBT-Bench — generators are the weak spot.
**First consumer:** `skreeep2` and its `.dsh/` harness (the same engine `2026-09-19-test-protection-and-holdout-design.md`
extended). Migrates with Plan C.

## Goal

When a property (or any test) fails, the agent receives exactly what it can act on — the property's name, the failing
input (shrunk when the runner shrank it), one failure message — and nothing it cannot: no stack trace, no passing
tests, no ANSI. The raw output is never lost. Human-owned properties are the spec the agent inhabits; agent-drafted
properties have a place to run before a human promotes them.

## Decisions taken during brainstorming

| Question | Decision |
|---|---|
| Who writes properties, where | Both. Human-owned in frozen `<module>/test/src` (the spec); agent drafts in `<module>/test-draft/src` (writable, second source root of the test module); promotion is a human `git mv` |
| Where feedback is shaped | Combined: a PreToolUse rewrite wraps the agent's own `mill … test` runs; post-edit runs a configurable *fast* selection; turn-end runs a configurable full selection |
| Mechanism | PreToolUse `updatedInput` (the seam the guard already uses), not PostToolUse — PostToolUse can add context but cannot suppress the raw output the model already saw |
| Architecture | Generic normalizer: parser adapters → normalized failure record → renderer; never regex-to-text in one step |
| Raw output | Always preserved (tee to `.dsh/.state/feedback/<utc>.log`); summary names the path; `--raw` flag; infrastructure failures preserve more |
| Language | POSIX sh + awk adapters (dash-clean, zero startup), behind a stable TSV record contract; a Scala normalizer may replace adapter internals when roadmap item 8 lands |
| Fast split | Suite naming: property suites end in `Props`; `fastTests` selects `testOnly -- '*Props'` |

## §1 The normalizer: adapters → record → renderer

**Record.** One failure is a block of TAB-separated rows (a temp file; the sh "ADT"):

```
kind	property|test|infra|unreadable
suite	<fqn or ->
name	<test or property name>
arg	<label>	<value>          0..n rows; ScalaCheck's ARG_0…, shrunk when the runner shrank
message	<one line>
detail	<line>                   0..n; infra only — the exception line and the first five frames
```

A run also yields counts (`passed`, `failed`) from the `Total …` line when present, else from `+`/`-` tallies after a
suite header (the existing `dsh_weaver_failures` rule), and the raw line count.

**Adapters** (awk functions in `lib.sh`; ANSI stripped first, Mill `[n/m]` prefix removed, suite header = bare fqn or
`Running Test Class <fqn>` with an uppercase last segment):

- `weaver` — listing lines `- <name> <ms>`; in the FAILURES block the message line(s) and, for weaver-scalacheck,
  `ARG_0: …` / `ARG_0_ORIGINAL: …` → `arg` rows. Kind `property` when any `ARG_` row exists, else `test`.
- `scalacheck` (plain / munit-scalacheck) — `! <prop>: Falsified after N passed tests.` + `> ARG_0: …` (+ `_ORIGINAL`) →
  `property`.
- `munit` — `==> X <suite>.<name>` + message → `test`.
- `infra` — classified first over the whole stream: `Exception in thread`, `java.lang.OutOfMemoryError`,
  `NoClassDefFoundError`, `ClassNotFoundException`, a `NullPointerException` outside an assertion block, `compile
  failed`/compiler errors, `no tests found`/test framework not found → one `infra` record; the assertion adapters do not
  also fire.
- fallback — one `unreadable` record.

Shrinking: `ARG_0_ORIGINAL` present means the runner shrank; the renderer shows the shrunk value and appends
`(shrunk from <original>)` only when they differ. A runner that does not shrink still yields the failing input; post-hoc
shrinking is out of scope.

**Renderer** (`dsh_render_feedback`), the fixed shape the agent learns:

```
FEEDBACK: 12 passed, 2 failed (app.test)
PROPERTY: PhraseProps.strikes are strictly increasing
  ARG_0: List(Strike(0.0), Strike(0.0))
  FAILURE: expected next - at > 0
TEST: ArrangementSpec.empty arrangement renders silence
  FAILURE: expected 0 but got 1 (ArrangementSpec.scala:41)
Raw output suppressed: 84 lines — .dsh/.state/feedback/2026-09-20T10-12-03Z.log
```

- A record whose source file lies under `test-draft/` renders as `DRAFT PROPERTY:` / `DRAFT TEST:`.
- `infra` renders `INFRA: <exception line>` plus the kept frames and ends `Raw output kept: N lines — <path>` (nothing
  suppressed).
- `unreadable` prints one `UNREADABLE: <message>` line and `Raw output kept: N lines — <path>` — never raw lines
  (amended 2026-09-20 at implementation: the original "last 40 raw lines" was dropped in favour of a leak-free default; the
  log is one read away). Real Mill/Scala compile failures are classified `infra` (the `-- [E…] … Error:` line as message).
- An all-green run is the single `FEEDBACK: N passed, 0 failed (<selector>)` line.

## §2 The wrapper, the rewrite, and where each stage runs

**Wrapper** `.dsh/hooks/feedback.sh [--raw] [--adapter weaver|scalacheck|munit] -- <command…>`: runs the command from
`$root`; tees combined stdout+stderr to `.dsh/.state/feedback/<utc>.log` (keeps the newest 20); feeds the log to the
adapters (auto-detected by markers unless `--adapter`); renders; **exits with the command's own status**. `--raw` prints
everything (still logs). An already-wrapped command is never wrapped again.

**Rewrite** (PreToolUse `updatedInput`, in `tool-policy.sh` *after* the holdout denial so refused selectors never reach
the wrapper): a bash line whose command word is `mill` (`./mill`) and whose selector matches `*.test`, `*.test.*`,
`*testOnly*`, `*testForked*`, `*testCached*`, with no `|`, `>`, `&&`, `;` following the Mill call, becomes
`sh .dsh/hooks/feedback.sh -- <original>`. A line the agent already composed into a pipeline is left alone (documented).
The hook prints one stand-down line on stdout — "shaped by feedback.sh; raw at .dsh/.state/feedback/" — so the agent knows.

**Contract keys** (both optional; `<module>` substituted as `checkFormat`/`fix` already do):

```
fastTests    = ./mill -j 1 --ticker false <module>.test.testOnly '*Props'
turnEndTests = ./mill -j 1 --ticker false <module>.test
```

(The selector precedes `--`: weaver ignores a glob passed after `--` — verified 2026-09-20.)

- **post-edit** (fast): after its existing checks for the edited file's module, when the coverage map grants `tests` and
  `fastTests` is set, run it through the wrapper and render into the hook's report. A failing property is **noted, not
  blocking** at post-edit (a red property mid-edit is TDD); the block stays with compile and lint.
- **turn-end**: for each touched module with `tests`, run `turnEndTests` through the wrapper; a failing human-owned
  property or test **blocks** the turn — this is where "the tests pass" becomes the completion signal (R-8). A failing
  *draft* never blocks. The holdout step is unchanged and runs after. `turnEndTests` absent → today's behaviour,
  byte-identical output.
- Budget: modules run sequentially inside the Stop hook's 600 s; stop at the first failing module and say
  `N modules not run — fix this one first`.

**Escape hatch.** The log path is printed every time; `.dsh/.state/feedback/**` is neither frozen nor sealed;
`sh .dsh/hooks/feedback.sh --raw -- ./mill app.test` is allowed (already wrapped).

## §3 Conventions, drafts, installation rules

**`*Props` convention.** A property suite is a weaver `SimpleIOSuite with Checkers` (or a `FunSuite` + ScalaCheck where
weaver is not used) whose object name ends in `Props`, one file per algebra or type it covers. Human-owned properties
live in `<module>/test/src/**/…Props.scala` (frozen); `fastTests` selects them by name.

**Drafts.** `<module>/test-draft/src` is a second source root of the test submodule, added once in `build.mill`'s
`LayerTests` (`sources ++ test-draft/src`). Not frozen. Drafts compile and run with the suite, so the agent gets §1
feedback on its own property; the renderer tags them `DRAFT`. Promotion is the human's `git mv` into `test/src`; the
agent asks in chat. Stale drafts are an installation-check note.

**Generators** (recommended, not enforced): `Arbitrary` instances for opaque types live beside the type in `test/src`
under `…/gen/`, so a property can reach the interesting inputs; the README says so.

**`check-harness.sh` rules** (a `dsh_feedback_checks` lib function, rows `ok|FAIL|note`, same pattern as
`dsh_holdout_checks`):

1. `.dsh/hooks/feedback.sh` present and `dash -n` clean → else FAIL.
2. Every `*Props.scala` under a frozen test root references `forall` or `Checkers` → else FAIL naming the file.
3. `fastTests` / `turnEndTests` absent → note (that stage is skipped).
4. Any `test-draft/` non-empty → note listing the files ("drafts awaiting promotion").
5. More than 20 logs under `.dsh/.state/feedback/` → note (the wrapper's prune failed).

**Freezes.** `.dsh/hooks/feedback.sh` is already covered by `.dsh/hooks/**`. Nothing else changes.

## §4 Testing and verification

*Adapters (`lib-test.sh`)* — each fed a captured real transcript (`cat -v`, ANSI kept) and synthetic edges:
weaver + weaver-scalacheck property failure (`ARG_0`/`ARG_0_ORIGINAL`) → `property` record with exact args/message;
plain `expect` failure → `test`; all green → no records, counts right; mixed suite → records in listing order;
scalacheck `! prop: Falsified …` + `> ARG_0:` → `property`; munit `==>` → `test`; infra (`NoClassDefFoundError`, OOM,
compile error, no tests found) → one `infra` record, `detail` ≤ 6 lines, no assertion record; garbage → one
`unreadable`. Renderer: golden output per kind; `(shrunk from …)` only when ORIGINAL differs; suppression/kept lines.

*Wrapper (fixtures, subcommand `feedback`)* — a stub emitting a canned transcript and exiting 1: rendered stdout equals
the golden block; exit 1 propagated; log written in full; `--raw` prints everything; the 21st run prunes to 20 logs.

*Rewrite (tool-policy fixtures)* — `./mill app.test` → allow with `updatedInput.command` =
`sh .dsh/hooks/feedback.sh -- ./mill app.test`; `./mill app.test | tail -5` untouched; an already-wrapped line not
re-wrapped; `./mill holdout.test` still denied first; `./mill app.compile` untouched.

*post-edit / turn-end fixtures* — with `fastTests`: post-edit on a `.scala` under a `tests` module runs the stub, renders,
exits 0 with a failing property (noted). With `turnEndTests`: a failing property → exit 2, the rendered block and
`N modules not run`; a failing draft only → exit 0 with `DRAFT PROPERTY:`; without the keys → byte-identical to today
(existing fixtures green).

*check-harness* — one negative case per `dsh_feedback_checks` rule.

*Live smoke (recorded)* — `./mill backend.cpu.test` through the wrapper (the module with weaver-scalacheck): a throwaway
failing property in `backend/cpu/test-draft/src` yields the `DRAFT PROPERTY:` block and the log path; delete the draft.

## Out of scope

Post-hoc shrinking when the runner does not shrink; a Mill JUnit-XML adapter; a Scala normalizer (item 8); Plan C
migration; template changes; weaver tags as a fast/slow lever.

## Files touched (skreeep2)

- `.dsh/lib.sh` — adapters, `dsh_render_feedback`, `dsh_feedback_checks`, contract-key substitution helper.
- `.dsh/hooks/feedback.sh` — new wrapper.
- `.dsh/hooks/tool-policy.sh` — the rewrite (after the holdout denial).
- `.dsh/hooks/post-edit.sh`, `.dsh/hooks/turn-end.sh` — `fastTests` / `turnEndTests` stages.
- `.dsh/check-harness.sh` — rules 1–5.
- `.dsh/contract` — the two keys; `.dsh/README.md` — conventions, drafts, escape hatch.
- `build.mill` — `LayerTests` gains the `test-draft/src` root.
- `.dsh/tests/**` — cases and lib-test blocks per §4.
