# purrism rewrites in the loop, and a memorisation check — design (2026-09-25)

**Status:** implemented; §2's escape amended after the live smoke (see §2). Two plans follow: one in `ScalaSemanticMCP` (§1), one in `skreeep2`
(§2–§4).
**Origin:** Tier 2, item 6 of `wiki/llm-scala-fusion/roadmap-more-scala-less-llm.md`.
**Evidence:** FPEval (`wiki/llm-scala-fusion/llm-code-defect-taxonomy.md`): GPT-5 emitted imperative patterns in "94%" of
its Scala output, and prompting alone did not fix it — the case for a deterministic rewrite over asking. SpecBench (same
article): an agent produced a 2,900-line hash-table "compiler" that memorised its test inputs — a decidable shape once
production code can be compared with the tests it is judged by.
**Depends on:** item 5 (`2026-09-24-diff-scoped-verification-design.md`) — its changed-line ranges, its `verify-diff.sh`
composer, its one-shot `scalasemantic-mcp call`, its finding/degradation rule at the commit boundary, and the classpath
metadata the server discovers.

## Goal

Two turn-end stages in the skreeep2 harness, both scoped to the lines this turn changed:

1. **Rewrite, don't only report.** purrism's rewrites are applied to the agent's changed lines, and the agent is made to
   review what changed before the turn may end.
2. **Refuse memorisation.** A production construct whose literals are largely the test suite's own inputs and expected
   outputs blocks the turn.

## Decisions taken during brainstorming

| Question | Decision |
|---|---|
| What is actually new | purrism already runs in the loop as a *check*: post-edit and turn-end run `fix --check` (a rewrite is pending) and `fixStrict` (any lint), shipped with roadmap items 1–3. What item 6 adds is *applying* rewrites and the memorisation check |
| When rewrites are applied | Turn-end, not post-edit — rewriting a file after every edit changes it under the agent and costs a forced re-read each time; turn-end does not disturb the edit sequence |
| Who reviews a rewrite | The agent, via one block: a Stop hook that allows ends the turn, so a rewrite applied on an allowing turn-end would never be seen by the agent that wrote the original |
| What rewrite scope | Changed lines only (item 5's ranges); code the agent did not write is never rewritten without review |
| The memorisation signal | Overlap between production literals and test literals — not table size. A 128-entry MIDI note table and a memorising table look alike by size; what distinguishes them is whether the keys are the test inputs |
| Where literal extraction lives | A syntax fact in ScalaSemanticMCP (`literal_tokens`, a sibling of `NameTokens`); which files are tests, the thresholds and the verdict stay in the harness — the split item 5 established. Rejected: a skreeep2-only Mill task (fact and policy fused, one project only); `sh`/`awk` extraction (strings, escapes and interpolation make it unreliable) |

## §1 The fact: `literal_tokens` in ScalaSemanticMCP

Syntax only: scalameta parses the text, so no presentation compiler and **no classpath** is needed — it answers where
`resolve_references` cannot.

`literal_tokens(files)` where `files` is a list of `{uri, ranges?, source?}` with `resolve_references`'s conventions:
`uri` is the SemanticDB-form path relative to the project root, `ranges` a list of `[startLine, endLine]` (0-based, end
exclusive), `source` the buffer's current text (absent → the on-disk file). **`ranges` absent means the whole file** —
how the harness reads test sources.

```json
{ "files": [ { "uri": "core/src/a/B.scala",
               "literals": [
                 { "line": 41, "character": 12, "kind": "string", "value": "C4",
                   "enclosing": { "kind": "val", "name": "noteTable", "line": 38 } } ],
               "omitted": 0 } ] }
```

- `kind`: `int` | `long` | `double` | `float` | `string` | `char`. Booleans, `null` and `()` are excluded — they carry no
  memorisation signal.
- `value`: normalised so equal literals compare equal across files — `0x10` and `16` are both `16`, `1_000` is `1000`,
  string escapes are resolved.
- String interpolations contribute their literal parts (`s"note $n"` yields `"note "`): an interpolated key is still a key.
- `enclosing`: the nearest enclosing definition — `def` | `val` | `var` | `object` — with its name and line. It is what lets
  the harness judge a *construct*: a memorising table is one `val` or one `def`.
- Bound: 2,000 literals per file, larger than `resolve_references`' 200 because test sources are read whole; `omitted`
  counts what the bound cut.
- A buffer that does not parse answers that file with an `error`, never a partial list.
- No policy: the tool does not know which files are tests, sets no threshold, and drops no "trivial" literal.

The one-shot mode needs nothing new: `scalasemantic-mcp call literal_tokens '<json>' <root>`.

## §2 Part (a): the rewrite stage

**The task.** `fixRanges <ranges.tsv>` beside `fixStrict` in `buildKit/Layer.mill`, which already drives scalafix through
its interfaces API. It reads item 5's `path TAB start TAB end` rows, runs `evaluate()` on those files, and keeps a patch
only when every one of its text edits falls inside a changed range; an `isAtomic()` patch that reaches outside the ranges
is skipped whole. It applies the kept subset with `applyPatches(ScalafixPatch[])` and prints the unified diff of exactly
what it applied (`previewPatches(ScalafixPatch[])`). scalafix does not attribute a patch to a rule, so the report is the
diff — which is what a reviewer needs.

**Turn-end order:** `verify-diff` (item 5) → **rewrite stage** → the existing compile chain, so the chain checks the
rewritten code.

| Outcome | Behaviour |
|---|---|
| Rewrites applied, affected modules still compile | **Block once**: "purrism rewrote N sites on lines you changed", the diff, and "confirm the rewritten code still does what you meant, then end the turn again". Through the gate's existing attempt counter and cap |
| Rewrites applied, a module no longer compiles | Restore the saved originals and NOTE "rewrite reverted: it did not compile" — a purrism bug (as the known `PreferEffectIdioms` try/finally rewrite), never the agent's problem. The turn continues on the agent's own code |
| Nothing to apply | Silent. The turn-end after a review ends normally |
| The task crashes, scalafix cannot load | NOTE; the existing `fix --check` still gates. Never a silent pass |

**Escape — amended at implementation (2026-09-26).** An agent that disagrees with a rewrite and merely reverts it has it
reapplied at every turn-end. This spec first named scalafix's own suppression, `// scalafix:ok <Rule>`, as the way out.
The live smoke measured that it does not work for purrism: every suppression form left `getOrElse(0)` → `.orEmpty`
applied, and `fix --check` warned `UnusedScalafixSuppression`, because scalafix honours suppression only for patches a
rule marks atomic, and in scalafix-purrism only `KleisliScope` does. Honouring the comment harness-side cannot work
either: the chain's whole-file `fix --check` still blocks that line. So the block message says instead: confirm the
rewritten code still means what you meant; if a rewrite is wrong, restructure the code so the rule no longer matches
(with a comment saying why), or report the rule, file:line and reason to the human. Until purrism marks its rewrites
atomic there is no per-line opt-out, so a wrong rewrite that cannot be restructured ends at the attempt cap and is
logged in `unverified.log` — loud and bounded. Marking purrism's rewrites atomic is a follow-up in that repository.

**Not at the commit boundary.** Rewriting the index during `git commit` would change what is being committed behind the
author's back. Commits keep today's `fix --check` gate.

**An existing behaviour, unchanged.** Turn-end's `fix --check` stays whole-file: a pending rewrite on a line the agent
did not change still blocks, with the remedy `./mill <module>.fix`, exactly as today. The rewrite stage only avoids
rewriting code the agent did not write without a review; the repository's rule that a touched file ends fully fixed
stands.

## §3 Part (b): the memorisation check

A third check family in `verify-diff.sh`, beside names and dependencies — that script already owns the change set and
the launcher.

**Inputs — harness policy:**

- *Production literals*: `literal_tokens` over each production Scala file this turn changed, **whole file**, then kept
  only for the constructs this turn touched — a construct is touched when any of its literals lies in a changed range.
  "Production" is outside test directories; "test directory" is a module's `test/` directory per the module map, the
  same classification turn-end's `is_test` uses.
- *Test literals*: `literal_tokens` over every visible test source, whole files. The sealed holdout is excluded — the
  threat is memorising what the agent can see. The set is cached in `.dsh/.state/`, keyed by the test files' hashes.
- *Trivial literals* are dropped before comparing: numbers with absolute value ≤ 10, the empty string, one-character
  strings. Otherwise every `0` and `1` is overlap.

**The rule, per construct.** For each `enclosing` construct this turn touched: **n** = **all** its non-trivial literals,
**k** = those whose value also occurs among the test literals. Flag when **k ≥ 8 and k/n ≥ 0.5**.

The whole construct is judged, not only this turn's additions. Counting only the new lines would let a memorising table
grow by a few rows a turn and never reach the threshold; judging the construct once it is touched closes that, at the
cost that a turn editing one line of a large pre-existing table re-judges the table.

- A table of 10 keys that are all test inputs is flagged; a function sharing one constant with its test is not.
- Constant returns (`if x == 3 then 42 …`) are covered by the same rule: a test asserting `f(3) == 42` puts both literals
  in the test set.

**Verdict.** A **finding**: it blocks at turn-end and, under item 5's rule, promotes at `--staged` to block a commit. The
message names the construct, "k of n literals also appear in the tests", and up to 5 overlapping values with the test
file each came from.

**Policy table.** `.dsh/policy/memorisation.tsv`, human-owned and frozen like `allowed-types.tsv`, holds the thresholds
(`min_overlap`, `min_ratio`, `trivial_abs`) and **allow rows** (`path TAB construct TAB reason`). The escape is human-only:
the agent cannot suppress this with a comment, because an agent able to exempt its own reward hacking makes the check
pointless. A legitimate table that trips it gets an allow row from the human; until then the gate's attempt cap releases
a stuck turn after two attempts, loudly.

**Degradation.** The tool unavailable → NOTE, as for names. No test sources at all → silent: with nothing to memorise the
check genuinely passes, rather than failing to run.

## §4 Testing and verification

*Order across repos:* ScalaSemanticMCP first (the tool, then `./mill installLocal`), then skreeep2. The harness fixtures
stub the launcher, so skreeep2 can be built before the real tool is installed.

*ScalaSemanticMCP — `LiteralTokensSuite`:* each `kind` with its normalised `value` (`0x10` → `16`, escapes resolved);
booleans, `null` and `()` excluded; interpolation parts extracted; `enclosing` right for a literal in a `val` table, in a
`match` inside a `def`, and in a nested `object`; `ranges` absent → whole file, present → scoped; an unparseable buffer →
an error, not a partial list; the 2,000 cap with `omitted`. Plus an envelope test in the style of `McpPcSuite`, and the
tool-count and `--describe` goldens.

*skreeep2 — `lib-test.sh`:* the trivial-literal filter; per-construct k/n counting over the whole construct; each
threshold alone is not enough (k ≥ 8 with a low ratio, a high ratio with k < 8); a table grown in small steps is still
flagged once it crosses the threshold; an untouched construct is never judged; allow-row matching; the test-directory
classification.

*skreeep2 — fixtures (`run.sh`), launcher and Mill stubbed:* (a) a rewrite applied → blocks with the diff; a rewrite that
breaks the compile → reverted, NOTE, not blocking; nothing to apply → silent; the task crashes → NOTE; a patch outside the
changed ranges is never applied. (b) a planted memorising table → blocks; the same table with an allow row → passes;
shared trivial constants → silent; the finding also blocks a `--staged` commit.

*Live smoke — required, recorded in the final report.* Item 5's fixtures passed while three silent-pass defects got
through, each caught only by measurement. So, against the real launcher and real purrism: an agent-style line that
purrism really rewrites is rewritten and blocked for review; a planted table whose keys are test inputs is blocked; an
audio-style table whose keys are not test inputs passes.

**Cost:** turn-end gains two launcher calls (test literals, cached; production literals) and one scalafix evaluation per
touched module for `fixRanges`. The compile runs twice only in the rare turn where a rewrite was applied.

## Out of scope

Applying rewrites at post-edit or at the commit boundary; rewriting lines outside this turn's changes; attributing a patch
to its rule; a size-only table rule in purrism; including holdout sources in the test literal set; any agent-side
suppression of the memorisation check.

## Files touched

*ScalaSemanticMCP:* `analysis/…/analysis/LiteralTokens.scala` (new), `analysis/…/model/LiteralResult.scala` (new),
`mcp/…/mcp/McpTools.scala` (the tool), tests, compat goldens, docs (`docs/reference/tools.md`,
`docs/usage/tool-examples.md`, README).

*skreeep2:* `buildKit/Layer.mill` (`fixRanges`), `.dsh/verify-diff.sh` (the memorisation family),
`.dsh/hooks/turn-end.sh` (the rewrite stage), `.dsh/lib.sh` (overlap, thresholds, allow rows, installation rules),
`.dsh/policy/memorisation.tsv` (new), `.dsh/frozen-paths`, `.dsh/check-harness.sh`, `.dsh/README.md`, tests and fixtures.
