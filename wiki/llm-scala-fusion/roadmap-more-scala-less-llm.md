# Roadmap: More Scala, Less LLM

> Sources: synthesis over the topic's sources — Blinn et al., 2024-09-02; Khati et al., 2026-01-27; Zhao et al. (SpecBench), 2026-05-20; Zhong et al. (ImpossibleBench), 2025-10-23; He et al., 2025-06-23; Lang et al. (FPEval), 2026-01-05; Anthropic Engineering, 2025-11-04; Bursuc et al., 2025-09-26; Mündler et al., 2025-04-12; Hassani et al., 2025-08-28
> Raw: [Typed holes (Hazel)](../../raw/llm-scala-fusion/2024-09-02-statically-contextualizing-llms-with-typed-holes.md); [Deterministic AST hallucination correction](../../raw/llm-scala-fusion/2026-01-27-deterministic-ast-hallucination-detection-correction.md); [SpecBench](../../raw/llm-scala-fusion/2026-05-20-specbench-reward-hacking-long-horizon-coding-agents.md); [ImpossibleBench](../../raw/llm-scala-fusion/2025-10-23-impossiblebench-llm-test-exploitation.md); [Property-Generated Solver](../../raw/llm-scala-fusion/2025-06-23-property-generated-solver-property-oriented-feedback.md); [Perish or Flourish (FPBench)](../../raw/llm-scala-fusion/2026-01-05-perish-or-flourish-llm-functional-programming-fpbench.md); [Code execution with MCP](../../raw/llm-scala-fusion/2025-11-04-anthropic-code-execution-with-mcp.md); [Vericoding benchmark](../../raw/llm-scala-fusion/2025-09-26-vericoding-benchmark-formally-verified-program-synthesis.md); [Type-constrained code generation](../../raw/llm-scala-fusion/2025-04-12-type-constrained-code-generation-pldi-2025.md); [From Law to Gherkin](../../raw/llm-scala-fusion/2025-08-28-from-law-to-gherkin-llm-behavioural-specifications.md)
> Updated: 2026-09-28

## Overview

The prioritised list of what to build or change, ranked by (evidence strength × defect classes closed) ÷ effort. Each
item names the defect class it attacks (see [the taxonomy](llm-code-defect-taxonomy.md)), the mechanism (see
[types and properties](where-types-and-properties-buy-most.md)) and the tool it lands in (see
[harness gaps](harness-and-tooling-gaps.md)). The governing principle, from the evidence: **the LLM's job is to
inhabit a type or satisfy a property; Scala's job is to state it, check it, and translate the failure.** Move work
leftward whenever a decidable check exists.

## Tier 1 — cheap, high evidence, do first

**1. Test-protection hooks + holdout module.** *Closes:* test gaming. *Evidence:* ImpossibleBench (agents delete or
edit failing tests; test access and feedback loop change cheating rates) and SpecBench (visible-suite saturation, gap
growing "by 28 percentage points for every tenfold increase in code size"). *Build:* a dsh/Claude hook denying writes
under test paths during implementation; a `holdout` test module in the template that only the gate runs, never the
agent. R-8/R-9 already state the rule; this makes it mechanical.

> **Status: Shipped** (2026-09-20) — landed in `skreeep2` on `master`: a `sealed` access token in `.dsh/frozen-paths` (reads
> and writes denied), every module test root frozen, the harness and its live hook wiring self-frozen, a `holdout` Mill
> module run only by the turn-end gate and `prePush` with names-only reporting, and six installation rules in
> `check-harness.sh`. Spec: `docs/superpowers/specs/2026-09-19-test-protection-and-holdout-design.md` (this repo). The
> two residuals from the final review — quoted or alternate Mill selectors (`./mill 'holdout.test'`, `_.test`,
> `__.test.<task>`, brace forms) reaching the suite, and the guard's stand-down hint naming the now-refused `__.compile` —
> were closed the same day. What stays outside the text-matching layer, by design and documented: `bash -c '…'`,
> variable-built selectors, and reads scoped at an ancestor (`grep -r .`, `git grep`). Plan C carries the engine into
> the profile.

**2. Shrunk-counterexample feedback loop (ScalaCheck ⇄ LLM).** *Closes:* misinterpretation, missing corner cases,
self-deceiving I/O tests. *Evidence:* Property-Generated Solver — property-oriented, structurally minimal feedback,
"over 64% fix rate" on initially failed problems. *Build:* a convention that every algebra ships properties; a hook
that, on property failure, feeds the LLM *only* the shrunk counterexample and the property text. ScalaCheck shrinking
makes this nearly free.

> **Status: Shipped** (2026-09-20) — landed in `skreeep2` on `master`: a generic normaliser (weaver/weaver-scalacheck,
> ScalaCheck, munit, infra adapters → TAB failure records → a fixed `FEEDBACK:/PROPERTY:/TEST:/INFRA:` block), the
> `feedback.sh` wrapper (raw log kept, names and counterexample only shown), a PreToolUse rewrite of the agent's own
> `mill … test` runs, `fastTests` in post-edit (noted) and `turnEndTests` at turn-end (blocking; drafts under
> `test-draft/src` noted), five installation rules. Spec: `docs/superpowers/specs/2026-09-20-property-feedback-loop-design.md`
> (this repo). Caveat found at implementation: weaver-scalacheck reports the failing input but does not shrink it, so the
> "shrunk" half depends on the runner — plain ScalaCheck's `ARG_0_ORIGINAL` is rendered when present. Open: whether Claude
> Code honours the `updatedInput` rewrite live; the post-edit and turn-end stages do not depend on it.

**3. Diagnostic→instruction table.** *Closes:* idiom drift, compile-error loops that stall. *Evidence:* FPEval's repair
gains come from static-analysis feedback paired with "hand-crafted instructions for common types of issues"; raw
diagnostics alone repaired less. *Build:* purrism exports per-rule repair instructions; the build-result hook joins
compiler/scalafix diagnostics with them before the model sees the output.

> **Status: Shipped** (2026-09-21) — two halves. *scalafix-purrism v0.9.1:* a finding catalog (31 stable codes, one per
> finding kind, `code = Rule.kind` = scalafix's lint id so Mill/scalafix print `[Rule.kind]` natively), every diagnostic
> text carries the explanation for humans, `purrism/findings.tsv` in the jar (`code rule title explanation instruction doc`,
> header `# purrism <version>`), a docs "Findings" page, `findingsCheck` in CI. *skreeep2 `master`:* `scalafix` and `scalac`
> adapters in the feedback normaliser (`kind diagnostic` records), two frozen tables (`.dsh/instructions/purrism.tsv` vendored
> by `instructions-sync.sh`; project-owned `local.tsv` for scalac/wart codes), `DIAGNOSTIC:` + `INSTRUCTION:` + `DOC:`
> rendering with a `No instruction for:` gap line and a `.dsh/.state/instructions-missing` backlog, post-edit/turn-end
> `compile`/`fix --check`/`fixStrict` routed through the wrapper (blocking and F53 attribution still read the raw log), the
> PreToolUse rewrite extended to those tasks, five installation rules. Spec:
> `docs/superpowers/specs/2026-09-20-diagnostic-instruction-table-design.md` (this repo). Found at implementation: Mill's
> compile reporter prints no Scala 3 error ids, so scalac codes are `scalac.<kind>` classified from the message headline by a
> small project table; warts are `wartremover.<Wart>`. Open for the human: the instruction prose is a first draft (codes are
> stable, prose edits are non-breaking); `Layer.mill`'s `fixStrict` still swallows scalafix config errors as success.

## Tier 2 — moderate effort, direct tool upgrades

**4. Typed-hole context tool in ScalaSemantic.** *Closes:* hallucinated APIs/objects, wrong types, missing context.
*Evidence:* Hazel — "contextualization with type definitions is particularly impactful"; hallucination arises for
definitions "not in the training data nor near the cursor". *Build:* `hole_context(uri, pos)` → expected type,
compatible in-scope bindings, applicable givens, definitions of involved types; a skill that makes the agent write the
`???` skeleton first, compile, then fill holes one at a time with this context.

> **Status: Shipped** (2026-09-24) — `ScalaSemanticMCP` master: a `hole_context(uri, line, character, source?)` MCP tool
> answering from the Scala 3 compiler (a dotty `InteractiveDriver` per module backend, error-tolerant, so an uncompiled
> buffer still answers): the expected type with `confidence` (`exact` when the enclosing tree prescribes it,
> `unconstrained` when nothing does — then no candidate is guessed) and a `why`; `bindings` (in-scope values that
> conform under `<:<`), `producers` (methods and companion members whose result fits, `match` `exact` or `shape`),
> `givens` (instances the compiler would pick), `types` (the expected type's definitions; project types carry a source
> excerpt), with per-list bounds and `omitted` counts. Candidates are filtered for accessibility from the hole and named
> the way the hole's own scope names them (alias, short name when unshadowed, else fully qualified) — never a name that
> would not compile there. The `typed-holes` skill makes the agent write `???` skeletons first, compile, then fill one
> hole at a time. Spec: `docs/superpowers/specs/2026-09-21-typed-hole-context-design.md` (this repo). Deferred with
> reasons in the branch's final review: scope-distance ordering, producers'-`using`-clause givens, non-parameterised
> given types. Open for the human: a live smoke needs classpath metadata wired in the target project.

**5. Diff-scoped deterministic verification.** *Closes:* hallucinated symbols, allowlist breaches, unresolvable build
coordinates. *Evidence:* Khati et al.'s AST-vs-knowledge-base check reached "100% precision" and auto-corrected
"77.0%" of found hallucinations without an LLM. *Build:* `verify_diff` in ScalaSemantic resolving every introduced
symbol against SemanticDB/classpath and the AllowedShape allowlist; a coursier resolve check for new dependencies.
Deterministic reject before any repair prompt.

> **Status: Shipped** (2026-09-25) — both halves are on master. Brainstorming split the item in two: *what* is verified is a
> Scala semantic fact, *where* it runs is harness policy, and no policy argument crosses the boundary. Shipped:
> `resolve_references(files)` — batched `{uri, ranges, source?}` input, since a turn-end sweep should be one JVM
> spin-up rather than one per file. Every name token scalameta finds inside the given line ranges is joined against the
> occurrences the presentation compiler emits for the same buffer; a token with no occurrence covering it is the one
> thing neither source can report alone — a name the compiler could not resolve. Each row carries the name as written,
> `kind`, `role`, `resolved`, the resolved `symbol` and its `origin` (`project` | `stdlib` | `classpath`), or, when
> unresolved, deterministic near-miss `suggestions` ranked by edit distance. The 200-row cap never drops an unresolved
> row, so a hallucination past the 200th name still reaches the caller. Also shipped: a one-shot
> `scalasemantic-mcp call <tool> '<json>' [root] [classpath]` mode, because a shell hook cannot reuse the agent
> session's MCP server. Spec: `docs/superpowers/specs/2026-09-24-diff-scoped-verification-design.md` (this repo), §1.
> Found at implementation: a handful of correct Scala 3 shapes carry no compiler occurrence and would read as
> hallucinations — `derives` clauses, named `export` selectors, macro splices, backticked definitions, and an annotated
> ascription used as a match scrutinee in a val's right-hand side. Each is suppressed rather than reported, and the tool
> documents the resulting blind spots; four review rounds went into making each suppression exactly as wide as the
> compiler's real hole, because one too wide silently passes a hallucinated name.
>
> The skreeep2 half (§2–§4) is `dsh verify-diff`: `git diff -U0` hunk headers become 0-based half-open line ranges, so
> the filtering rule that makes the check diff-scoped lives with git and old breakage is never re-reported; coordinates
> added to a build file go to `cs resolve`; resolved symbols are matched against the human-owned, frozen
> `.dsh/policy/allowed-types.tsv`. The turn-end gate runs it before the compile chain — the gate invokes it, not the
> agent, which is the whole difference between mandatory and optional — and `git commit` / `./mill prePush` run it again
> against the index, where the rules are stricter.
>
> What the review rounds changed, and why it is worth recording: the gate's cardinal invariant is that it never silently
> passes, and three separate violations of it survived the fixtures and were caught only by measurement. A `printf` whose
> format string began with `---` was parsed as an option by both `/bin/sh` and `dash`, so one degradation note never
> printed at all. A launcher predating the one-shot `call` mode falls through to `serve`, reads EOF on the hook's closed
> stdin and exits 0 printing nothing — which the gate read as "nothing unresolved" until a live smoke caught it. And a
> file answered with an empty `references` list — the ordinary answer for a comment-only change — produced a false
> "not checked" note. The opposite failure appeared too: at the commit boundary, degradation notes promoted to blocks, so
> on a machine without `timeout` every commit was denied with a reason no edit of the agent's could clear. The rule that
> settled it is that a *finding* promotes and a *degradation* never does — "I could not check" must not become "you may
> not commit".
>
> Verified live in skreeep2 on 2026-09-25, end to end against the installed launcher, once `./mill
> scalaSemanticWriteClasspath` wrote the classpath metadata the server discovers: a hallucinated name blocks the turn at
> its exact line, a typo of a real method blocks with a "did you mean" suggestion, and correct code passes silently.
> Found in that run, left for ScalaSemanticMCP: when a method does not resolve, names inside its lambda are reported too
> (a cascade that only ever accompanies a real error), and suggestion ranking can place a `cats` `map` above `List`'s.
> Open for the human: `.dsh/policy/allowed-types.tsv` ships with
> its header and no rule rows, because a wrong row blocks real work; and a prose name is never verified, since the
> semantic tool answers only about Scala files — a change set with no Scala file says so rather than implying otherwise.

**6. purrism as a loop stage, plus a memorisation-shape rule.** *Closes:* the "94%" imperative-pattern rate in Scala
output; SpecBench-style test-memorising code. *Build:* run the rewrite rules on the agent's diff before the agent
reviews it; add a rule flagging large literal-keyed tables and constant returns in production sources.

> **Status: Shipped** (2026-09-26) — both halves on master. purrism already ran in the loop as a *check* (items 1–3), so
> the new part is *applying* it: at turn-end the skreeep2 harness applies purrism's rewrites to the lines the session
> changed (`<module>.fixRanges`, which keeps only patches whose every edit is in range) and blocks once so the agent
> reviews the diff; a rewrite that breaks the compile is undone. The memorisation half ended up in the harness rather
> than as a purrism rule: the signal is *overlap with test literals*, not table size, because in an audio app a
> legitimate MIDI note table and a memorising table look alike by size. A new syntax-only ScalaSemanticMCP tool,
> `literal_tokens`, returns each literal with a normalised value and its enclosing construct; the harness blocks a
> construct when at least 8 of its non-trivial literals, and at least half of them, also appear in the visible tests,
> judging the whole construct once touched so a table grown a few rows a turn is still caught. Exemptions live only in a
> frozen, human-owned table. Spec: `docs/superpowers/specs/2026-09-25-purrism-loop-and-memorisation-design.md` (this repo).
>
> What measurement changed: every task review found a silent path, and a live run against real purrism found a
> spec-level defect the fixtures could not — the escape the design promised, `// scalafix:ok <Rule>`, does not suppress
> purrism's rewrites, because scalafix honours suppression only for patches a rule marks atomic and purrism marks almost
> none. The block message now tells the agent to restructure or to report to the human. The rewrite stage also turned
> out to rewrite a human's pre-session edits until it was confined to a session baseline and to files clean at
> SessionStart. Open: purrism marking its rewrites atomic, which would restore a per-line opt-out; recording a
> `git stash create` snapshot at SessionStart so a human's mid-session lines can be told from the agent's.

## Tier 3 — new surfaces, higher payoff and risk

**7. BDD step algebra as the text/type seam.** *Closes:* misinterpretation at the requirement level; gives the human a
readable spec. *Evidence:* LLM-generated Gherkin rated relevance 95%, clarity 100% by human judges, with omissions and
hallucinations that need review. *Build:* in the template, a cucumber-scala (or specs2) layout where step signatures
are typed holes over opaque domain types, Then-clauses are properties, and scenarios split into visible/holdout. Detail
in [Alternative Paradigms and the BDD Seam](alternative-paradigms-and-bdd-seam.md).

**8. Code-execution surface for the semantic tools.** *Closes:* context bloat and multi-step tool fragility.
*Evidence:* Anthropic's example fell "from 150,000 tokens to 2,000 tokens"; Felendler et al. confirm the gain and the
enlarged attack surface. *Build:* expose ScalaSemantic and purrism as a Scala library a scala-cli script calls, executed
in the `workspace-write` sandbox. Side benefit: the compiler now type-checks the agent's *tool use*.

**9. Stainless on the pure kernel.** *Closes:* invariant violations in the core. *Evidence:* vericoding succeeds where
the verifier is strong and the spec formal (82% in Dafny), and prose adds nothing. *Build:* extend the existing Mill
Stainless integration with a skill: the human writes `require`/`ensuring`, the LLM fills the body until Stainless
accepts.

> **Status: Shipped** (2026-09-28) — in three repositories. *ScalaSemanticMCP:* a syntax-only `contract_clauses` tool
> lists every def's `require`/`ensuring`/`decreases` with a shape hash of each clause's syntax tree, so reformatting
> never reads as a change and any edit to a clause does. *scala-llm-template:* a Stainless Mill recipe, a frozen
> kernel registry (`.dsh/policy/kernels.tsv`), and a turn-end vericoding stage in the vendored engine — the contracts
> of every registered kernel are snapshotted at SessionStart and are the human's: a changed, removed or added clause, a
> missing snapshot, or a new `assume`/`@extern`/`@ignore` blocks; then Stainless runs, and an invalid verification
> condition, a body Stainless cannot compile, or an unimplemented body blocks, while a solver timeout is only a note;
> plus a `vericoding` skill. *skreeep2:* the first kernels, three audio/time helpers, verified in place; an agent
> filled the bodies in a live gated session, and the existing callers delegate to them with unchanged behaviour.
> Spec: `docs/superpowers/specs/2026-09-27-stainless-vericoding-design.md` (this repo).
>
> What measurement changed: the real tool, not the design, settled three things. An implication written `a ==> r == c`
> does not type-check (`==>` binds like `==`). One human-written postcondition compared its input with NaN itself, so no
> body could satisfy it — the live session surfaced it, the agent stopped and asked instead of editing the contract, and
> the human revised the contract. And `require`/`ensuring` in production code are not erased by default: they run on
> every call unless Stainless's `StaticChecks` is imported. The live session also proved the guard's point — a contract
> edit the human asked for in chat was still blocked until the human reviewed it and started a new session. Open: an
> escape written as script-side file writes is not caught by the engine; the turn-end tests have no time cap of their
> own.

## Tier 4 — long range

**10. Type-constrained decoding via a local model.** Sampler-level enforcement halved compile errors for TypeScript
(Mündler et al.), but needs logit access and a type system tractable as a prefix automaton; Scala's is not, and hosted
models expose no logits. Revisit if dsh runs an open-weight model locally and a Scala-subset grammar is worth building.

## Reengineering Kit status

> **Status: Shipped** (2026-09-26) — the harness engine every shipped item above lives in (the gate, its hooks,
> `verify-diff`, the rewrite stage, the memorisation check) moved out of skreeep2 into `scala-llm-template`'s
> `kit/harness`, its one editable home, and is vendored into each project as `.dsh/engine/` with a stamp the
> installation check verifies; skreeep2 is its first consumer, and a scratch second project is gated end to end by
> the kit's own recipe. The Mill tasks the engine calls by name ship as a Mill recipe beside it. This replaces the kit
> design's plan to host the engine in the DSH profile (Plan C, now marked superseded). Spec:
> `docs/superpowers/specs/2026-09-26-harness-engine-vendoring-design.md` (this repo); where the gate lives is in
> [Verification and the Gate](../reengineering/verification-and-the-gate.md). Open: the engine supports Mill only —
> sbt and scala-cli projects get a named stand-down, not checks.

## What not to do

- Do not add more prose rules to CLAUDE.md for security or style: prompting did not reduce vulnerability counts
  (Kharma et al.), and moved idiom only partway (FPEval). Put the check in the gate.
- Do not let the agent's own tests be the completion signal (Tier 1, item 1).
- Do not try to make the whole module formally verified; the vericoding numbers support small pure kernels only.

## See Also

- [What Goes Wrong in LLM-Generated Code](llm-code-defect-taxonomy.md)
- [Where Types and Properties Buy the Most](where-types-and-properties-buy-most.md)
- [Harness and Tooling Gaps](harness-and-tooling-gaps.md)
- [Alternative Paradigms and the BDD Seam](alternative-paradigms-and-bdd-seam.md)
- [The Reengineering Contract](../reengineering/reengineering-contract.md)
