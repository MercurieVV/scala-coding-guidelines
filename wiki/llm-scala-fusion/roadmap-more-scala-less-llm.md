# Roadmap: More Scala, Less LLM

> Sources: synthesis over the topic's sources — Blinn et al., 2024-09-02; Khati et al., 2026-01-27; Zhao et al. (SpecBench), 2026-05-20; Zhong et al. (ImpossibleBench), 2025-10-23; He et al., 2025-06-23; Lang et al. (FPEval), 2026-01-05; Anthropic Engineering, 2025-11-04; Bursuc et al., 2025-09-26; Mündler et al., 2025-04-12; Hassani et al., 2025-08-28
> Raw: [Typed holes (Hazel)](../../raw/llm-scala-fusion/2024-09-02-statically-contextualizing-llms-with-typed-holes.md); [Deterministic AST hallucination correction](../../raw/llm-scala-fusion/2026-01-27-deterministic-ast-hallucination-detection-correction.md); [SpecBench](../../raw/llm-scala-fusion/2026-05-20-specbench-reward-hacking-long-horizon-coding-agents.md); [ImpossibleBench](../../raw/llm-scala-fusion/2025-10-23-impossiblebench-llm-test-exploitation.md); [Property-Generated Solver](../../raw/llm-scala-fusion/2025-06-23-property-generated-solver-property-oriented-feedback.md); [Perish or Flourish (FPBench)](../../raw/llm-scala-fusion/2026-01-05-perish-or-flourish-llm-functional-programming-fpbench.md); [Code execution with MCP](../../raw/llm-scala-fusion/2025-11-04-anthropic-code-execution-with-mcp.md); [Vericoding benchmark](../../raw/llm-scala-fusion/2025-09-26-vericoding-benchmark-formally-verified-program-synthesis.md); [Type-constrained code generation](../../raw/llm-scala-fusion/2025-04-12-type-constrained-code-generation-pldi-2025.md); [From Law to Gherkin](../../raw/llm-scala-fusion/2025-08-28-from-law-to-gherkin-llm-behavioural-specifications.md)
> Updated: 2026-09-20

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

**3. Diagnostic→instruction table.** *Closes:* idiom drift, compile-error loops that stall. *Evidence:* FPEval's repair
gains come from static-analysis feedback paired with "hand-crafted instructions for common types of issues"; raw
diagnostics alone repaired less. *Build:* purrism exports per-rule repair instructions; the build-result hook joins
compiler/scalafix diagnostics with them before the model sees the output.

## Tier 2 — moderate effort, direct tool upgrades

**4. Typed-hole context tool in ScalaSemantic.** *Closes:* hallucinated APIs/objects, wrong types, missing context.
*Evidence:* Hazel — "contextualization with type definitions is particularly impactful"; hallucination arises for
definitions "not in the training data nor near the cursor". *Build:* `hole_context(uri, pos)` → expected type,
compatible in-scope bindings, applicable givens, definitions of involved types; a skill that makes the agent write the
`???` skeleton first, compile, then fill holes one at a time with this context.

**5. Diff-scoped deterministic verification.** *Closes:* hallucinated symbols, allowlist breaches, unresolvable build
coordinates. *Evidence:* Khati et al.'s AST-vs-knowledge-base check reached "100% precision" and auto-corrected
"77.0%" of found hallucinations without an LLM. *Build:* `verify_diff` in ScalaSemantic resolving every introduced
symbol against SemanticDB/classpath and the AllowedShape allowlist; a coursier resolve check for new dependencies.
Deterministic reject before any repair prompt.

**6. purrism as a loop stage, plus a memorisation-shape rule.** *Closes:* the "94%" imperative-pattern rate in Scala
output; SpecBench-style test-memorising code. *Build:* run the rewrite rules on the agent's diff before the agent
reviews it; add a rule flagging large literal-keyed tables and constant returns in production sources.

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

## Tier 4 — long range

**10. Type-constrained decoding via a local model.** Sampler-level enforcement halved compile errors for TypeScript
(Mündler et al.), but needs logit access and a type system tractable as a prefix automaton; Scala's is not, and hosted
models expose no logits. Revisit if dsh runs an open-weight model locally and a Scala-subset grammar is worth building.

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
