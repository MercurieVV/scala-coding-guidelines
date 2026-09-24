# Diff-scoped deterministic verification — design (2026-09-24)

**Status:** approved design, not yet implemented. Two plans follow: one in `ScalaSemanticMCP` (§1), one in `skreeep2` (§2–§4).
**Origin:** Tier 2, item 5 of `wiki/llm-scala-fusion/roadmap-more-scala-less-llm.md`.
**Evidence:** Khati et al. (`wiki/llm-scala-fusion/harness-and-tooling-gaps.md` §3): an AST-vs-knowledge-base check reached
"100% precision" and auto-corrected "77.0%" of the hallucinations it found, with no LLM in the loop — a deterministic
validator on the output is more reliable than asking the model to repair.
**Depends on:** ScalaSemanticMCP's SemanticDB index and presentation-compiler backend; the skreeep2 harness's turn-end
baseline (spec `2026-09-19-test-protection-and-holdout-design.md`) and its section/verdict rendering (spec
`2026-09-20-diagnostic-instruction-table-design.md`).

## Goal

Before a turn may end, the harness checks — deterministically, without a compile and without an LLM — that everything the
turn introduced actually exists: every name the changed lines reference resolves against SemanticDB or the classpath, every
resolved symbol is one the module's policy permits, and every dependency coordinate added to the build resolves. A failure
blocks with a diff-scoped message and near-miss suggestions, before any repair prompt and before the expensive compile.

## Decisions taken during brainstorming

| Question | Decision |
|---|---|
| Where it runs | Turn-end, **harness-owned and mandatory** — the complete change set exists there and a git diff makes the check genuinely diff-scoped. "Cannot be skipped" is about who invokes it: an agent-called tool is optional in practice, a hook-called one is not. A second, stricter run guards the repository boundary (`git commit` / `mill prePush`) |
| Why not PreToolUse | It sees one edit, while correctness is a property of a *set* of edits: multi-file renames, skeleton-first bodies, typed holes and API-first refactors are legitimately invalid between edits, and a pre-edit gate has no third verdict |
| Why not post-edit | The gate already runs `compile` and `fixStrict` after every Scala write, so full symbol resolution there duplicates the compiler and scalafix; its only gain would be latency |
| Split of responsibility | **Harness:** git diff / turn baseline, module and build context, project policy (`allowedTypes`), dependency checks, the PASS / NOTE / FAIL decision. **ScalaSemanticMCP:** Scala semantic facts only — symbol resolution, SemanticDB/classpath lookup, correction suggestions. No policy argument crosses the boundary |
| Naming | The MCP tool is `resolve_references` (it answers "what does this code refer to, and does it resolve?"); `dsh verify-diff` is the harness command that composes it with the dependency resolver and the project policy. A git diff is an orchestration concern, so the MCP never parses one |
| Diff-scoping | The harness passes explicit line ranges (from `git diff -U0` hunk headers) and the tool answers only within them — old breakage is never re-reported, and the filtering rule lives with git |
| Facts per reference | The name as written, the resolved symbol, its origin, kind and role, or `resolved: false` with deterministic near-miss suggestions |

## §1 The MCP tool: `resolve_references`

`resolve_references(files, …)` where `files` is a list of `{uri, ranges, source?}` — `uri` is the SemanticDB-form path
relative to the project root, `ranges` a list of `[startLine, endLine]` (0-based, end exclusive) as `git diff -U0` yields,
`source` the buffer's current text (absent → the on-disk file is read, and the result is marked `pcFallback`). Batched
input is deliberate: a turn-end sweep is one JVM spin-up, not one per file. The presentation compiler does the typing, so
the server must have been started with a classpath; without one the tool returns the existing no-classpath error.

**How a row is produced.** The PC regenerates SemanticDB for the buffer (error-tolerant, so a file that does not compile
still answers) and yields an occurrence per *resolved* name; scalameta parses the same text for *every* name token inside
the ranges. Joining the two is what makes "unresolved" observable: a name token in a range with no occurrence covering it
is a name the compiler could not resolve. Neither source alone can say that.

```json
{ "files": [ { "uri": "core/src/a/B.scala",
               "references": [
                 { "line": 41, "character": 12, "name": "SecretThing", "kind": "type", "role": "use",
                   "resolved": true, "symbol": "foo/internal/SecretThing#", "origin": "project" },
                 { "line": 44, "character": 8, "name": "barr", "kind": "term", "role": "use",
                   "resolved": false, "suggestions": ["foo/domain/User#bar()."] }
               ],
               "omitted": 0, "pcFallback": false } ] }
```

- `kind`: `type` | `term` | `method` | `package`. `role`: `use` | `definition`.
- `origin`: `project` when the symbol's document is in the SemanticDB index, `stdlib` for `scala/` and `java/` symbols,
  `classpath` otherwise. Enough for the harness's policy without the tool knowing any policy.
- `suggestions`: deterministic near-misses only — same owner and simple name within edit distance 2, or an exact
  simple-name match under a different owner (the "you meant the other `Path`" case). Empty when nothing is close. Never a
  guess, never an applied fix.
- Bound: 200 rows per file; `omitted` counts what the bound cut.
- No policy, no allowlist, no git, no diff parsing.

**One-shot CLI.** The harness runs in a hook process and cannot reuse the agent session's MCP server, so the installed
launcher gains a one-shot mode — `scalasemantic-mcp call resolve_references '<json>'` — which starts the server, issues
one `tools/call`, prints the result and exits.

## §2 The harness command: `dsh verify-diff`

`.dsh/verify-diff.sh`, run by the turn-end gate **before** the per-module compile chain: it is cheap, and a failure blocks
with a precise diff-scoped message instead of paying for a compile that says the same thing less clearly.

**Change set.** `git diff -U0` against the turn baseline the gate already computes (the same baseline `turn-end.sh` uses
for F53 attribution); untracked new files count whole. Hunk headers give `[startLine, endLine]` per file. Files split three
ways: Scala sources → the semantic check; build files (`build.mill`, `buildKit/*.mill`, `*.sbt`) → the dependency check;
everything else (`.md`, comment-only diffs) → the prose check.

**Policy, harness-side.** `.dsh/policy/allowed-types.tsv` — `<module dir> TAB <allowed symbol prefix> TAB <reason>`,
human-owned and frozen like the instruction tables. A module with no rows has no policy. Each resolved `symbol` is matched
against its module's prefixes; `origin: stdlib` is always allowed unless a row says otherwise. This duplicates what
skreeep2's `AllowedShape` scalafix rule enforces from `build.mill`, deliberately: the check must work in projects that do
not run that rule, and the harness never parses a build file for policy.

**Dependencies.** Coordinates on `+` lines of build files (`mvn"org::name:ver"`, `"org" %% "name" % "ver"`) → `cs resolve
<coord>`; unresolvable → FAIL naming the coordinate. No `cs` on PATH → NOTE, never a silent pass.

**Verdicts**, rendered in the turn-end gate's existing section format:

| Finding | Verdict |
|---|---|
| unresolved name in a Scala `+` line | FAIL — blocks, lists `file:line name`, plus `suggestions` when the tool found near-misses |
| resolved symbol outside the module's allowed prefixes | FAIL — names the symbol, the module and the policy row's reason |
| coordinate that does not resolve | FAIL — names the coordinate |
| unresolved name in prose (`.md`, comment-only lines) | NOTE — a hallucinated API name in docs misleads, but never blocks a turn |
| `cs` missing, no policy file, tool unavailable | NOTE — the gate says what it could not check |

## §3 The repository boundary, and degradation

**Commit / prePush.** `tool-policy.sh` gains one rule: a `git commit` or `./mill prePush` runs `verify-diff.sh --staged`
first — ranges from `git diff --cached -U0`, and *every* finding is FAIL, including the prose ones that only NOTE at
turn-end. A turn is a work-in-progress checkpoint; a commit is what the repository keeps. Denial uses the existing
`BLOCKED by tool-policy:` shape.

**The gate never silently passes.** Each of these emits a NOTE naming what was not checked, and the turn continues:

- the launcher is missing, or the one-shot call exits non-zero or exceeds its 30 s cap → "the semantic check did not run: `<reason>`"
- the project has no SemanticDB yet → the PC still answers for the buffer, but `origin: project` is unknowable, so policy
  findings are downgraded to NOTE and the gate says so
- `git` unreadable or no baseline → fall back to the gate's touched-files list, whole files instead of ranges, all findings
  downgraded to NOTE (old breakage would otherwise read as new)
- more than 50 changed Scala files, or more than 200 rows in a file → check the largest changes first and NOTE the truncation

**Cost.** One JVM spin-up per turn-end (and per commit), small against what turn-end already spends on Mill, and the gate
runs it first so a FAIL saves the compile entirely.

**Deliberately not done:** no LLM in the loop; no auto-fix written to the tree (`suggestions` are printed, never applied);
no verification of semantics the compiler owns — types, exhaustivity and effect discipline stay with `compile`,
`fixStrict` and purrism.

## §4 Testing and verification

*ScalaSemanticMCP:* a `pc`/`analysis` suite over fixtures — a resolved project symbol (`origin: project`), a classpath
symbol, a stdlib symbol, an unresolved name inside a range (`resolved: false` with a near-miss suggestion), an unresolved
name *outside* the ranges (absent from the result), a `definition` vs a `use` role, a buffer with a type error elsewhere
still answering, the 200-row cap with `omitted`, multi-file batch input, and `pcFallback` when `source` is omitted. An
`McpPcSuite` test for the JSON envelope and the no-classpath error; the tool-count and `--describe` goldens updated.

*Harness `lib-test.sh`:* hunk-header → ranges parsing (including `@@ -0,0 +1,5 @@` for a new file and `-U0` single-line
hunks), coordinate extraction from `+` lines of a build file, the policy matcher (prefix match, stdlib default, a module
with no rows), and the verdict mapping.

*Harness fixtures (`run.sh`):* turn-end with a stubbed launcher returning an unresolved row → blocks naming `file:line`; a
policy violation → blocks with the row's reason; a prose-only finding → note, not blocking; launcher missing → note and the
turn continues; `git commit` through tool-policy with a staged unresolved row → denied. The launcher and `cs` are stubbed in
`.dsh/tests/bin/`, exactly as `mill` is today.

*Live smoke (recorded):* a scratch file in skreeep2 naming a symbol that does not exist, plus a bogus coordinate in
`build.mill`; the turn-end gate must block naming both; then revert.

*Order across repos:* ScalaSemanticMCP first (the tool and its one-shot CLI, released or locally published), then the
harness — its fixtures stub the launcher, so it can be built and tested before the real one exists.

## Out of scope

Applying suggested corrections automatically; verifying prose beyond name resolution (no link checking, no spell check);
parsing `build.mill` for the `AllowedShape` policy; Scala 2 projects; any check that duplicates the compiler beyond name
resolution.

## Files touched

*ScalaSemanticMCP:* `analysis/…/analysis/Analyzer.scala` (the reference join), `analysis/…/model/ReferenceResult.scala`
(new), `pc/…/pc/PresentationCompilerBackend.scala` (reuse), `mcp/…/mcp/McpTools.scala` (the tool), the launcher's one-shot
`call` mode, tests, docs (`docs/reference/tools.md`, `docs/usage/tool-examples.md`, README).

*skreeep2:* `.dsh/verify-diff.sh` (new), `.dsh/lib.sh` (ranges parsing, policy matcher, verdict mapping, `dsh_verify_checks`),
`.dsh/hooks/turn-end.sh`, `.dsh/hooks/tool-policy.sh`, `.dsh/policy/allowed-types.tsv` (new), `.dsh/frozen-paths`,
`.dsh/check-harness.sh`, `.dsh/README.md`, tests and fixtures.
