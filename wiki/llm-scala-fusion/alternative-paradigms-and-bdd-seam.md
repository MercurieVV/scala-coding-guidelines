# Alternative Paradigms: Spec-First, Neurosymbolic Split, and the BDD Seam

> Sources: Bursuc et al., 2025-09-26; Lee et al., 2023-09-25; Hassani et al., 2025-08-28; Farrag, 2026-05-01; Helff et al., 2026-04-16; He et al., 2025-06-23; Ge et al. (vibe-coding survey), 2025-10-14; Feng et al., 2026-05-04; Alonso et al. (TDAD), 2026-03-18; Chen et al., 2026-02-08; Liang et al., 2026-07-28; Mughal et al., 2026-04-22
> Raw: [Vericoding benchmark](../../raw/llm-scala-fusion/2025-09-26-vericoding-benchmark-formally-verified-program-synthesis.md); [Guess & Sketch](../../raw/llm-scala-fusion/2023-09-25-guess-and-sketch-neurosymbolic-transpilation.md); [From Law to Gherkin](../../raw/llm-scala-fusion/2025-08-28-from-law-to-gherkin-llm-behavioural-specifications.md); [Productivity-Reliability Paradox](../../raw/llm-scala-fusion/2026-05-01-productivity-reliability-paradox-spec-driven-governance.md); [LLMs gaming verifiers](../../raw/llm-scala-fusion/2026-04-16-llms-gaming-verifiers-rlvr-reward-hacking.md); [Property-Generated Solver](../../raw/llm-scala-fusion/2025-06-23-property-generated-solver-property-oriented-feedback.md); [Survey of vibe coding](../../raw/llm-scala-fusion/2025-10-14-survey-of-vibe-coding-with-llms.md); [Structured spec-driven generation (Gherkin vs NL)](../../raw/llm-scala-fusion/2026-05-04-structured-spec-driven-repo-level-generation-gherkin.md); [TDAD](../../raw/llm-scala-fusion/2026-03-18-tdad-test-driven-agentic-development.md); [Agent-generated tests](../../raw/llm-scala-fusion/2026-02-08-rethinking-value-of-agent-generated-tests.md); [Do code LMs follow tests](../../raw/llm-scala-fusion/2026-07-28-do-code-lms-follow-tests-paired-interventions.md); [Duplicate Gherkin steps](../../raw/llm-scala-fusion/2026-04-22-duplicate-gherkin-steps-1-1m-step-benchmark.md)
> Updated: 2026-10-01

## Overview

Beyond "generate, compile, repair", four paradigms reassign the LLM's job. (1) **Spec-first / vericoding**: the LLM
never writes the spec, only the inhabitant. (2) **Neurosymbolic split**: the LLM guesses, a solver decides, routing by
confidence. (3) **Specification governance**: the process, not the tool, is fixed. (4) **BDD as the seam**: a
semi-formal natural-language layer (Gherkin) that the LLM is demonstrably good at, bound to typed Scala step
definitions that the compiler polices. The last is small, cheap and fits every existing tool in this repo.

## 1. Spec-first: the LLM writes inhabitants, not specifications

Vericoding turns code generation into search against a verifier: 12,504 formal specifications, success of 27% in
Lean, 44% in Verus/Rust and 82% in Dafny, and the finding that natural-language descriptions on top of the formal spec
"does not significantly improve performance". Two transferable ideas. First, *the spec is the human's artifact*; the
LLM's freedom is confined to the proof/implementation. Second, verifier strength predicts success — Dafny's automation
makes it tractable, Lean's does not. In Scala the ladder is: type signature (weakest spec, always available) →
ScalaCheck property → Stainless contract (strongest, kernel-only). The fusion strategy is to push each unit as high on
that ladder as the effort allows, then let the LLM inhabit it.

The vibe-coding survey names the same axis from the process side: its five models run from *Unconstrained Automation*
through *Planning-Driven* and *Test-Driven* to *Context-Enhanced*. Spec-first is the far end of that spectrum.

## 2. Neurosymbolic split with confidence routing

Guess & Sketch is the reference design: the LM proposes a translation and exposes alignment/confidence features; a
symbolic solver checks semantic equivalence and repairs the low-confidence spans, made feasible by cutting the program
into "shorter non-branching basic blocks amenable to the use of symbolic methods". Result: 57.6% more examples than
GPT-4 and 39.6% more than an engineered transpiler. The Scala reading: *decompose until each piece is decidable*.
Tagless-final algebras and arrow-shaped flows already give the blocks; the missing piece is a solver that checks
equivalence of a rewritten block (a scalafix "CatsEquivalent*" rule is a syntactic cousin; a Stainless equivalence
check on pure blocks is the semantic one).

Helff et al. add the rule for what the solver must check: **isomorphic**, not merely extensional, verification.
Extensional checks (does it give the right answers on these inputs) admit shortcuts and, in RLVR training, *induce*
them; checks that demand invariance under logically equivalent reformulations do not. Properties, laws (Functor/Monad
laws in cats-laws) and parametricity are Scala's native isomorphic checks.

## 3. Specification governance

Farrag's Productivity-Reliability Paradox review (67 sources) argues the productivity gains (20-56% on well-scoped
tasks) and losses (a 19% slowdown in the most rigorous RCT; 98% more pull requests but 91% longer review times) are two
faces of the same cause: non-deterministic generators without specification discipline. His Specification Governance
Model, piloted with Spec Kit and TDAD, moves the fixed point from code to spec. This is the argument for the
reengineering topic's R-12/R-13 (record and target list) and for keeping *superpowers* brainstorm → spec → plan as the
outer loop.

## 4. BDD as the seam between text and types

The user's own hypothesis — BDD as the place where the textual LLM world meets strict Scala — is supported on both
sides.

*LLMs are good at Gherkin.* Hassani et al. had ten participants rate 60 LLM-generated Gherkin specifications derived from
food-safety regulations: relevance 95%, clarity 100%, completeness 94.2%, singularity 93.4%, time savings 91.7%, with no
reliable difference between Claude and Llama — but with "occasional omissions, hallucinations, and mixed intents" that
require human review. Gherkin's Given/When/Then is "semi-formal": structured enough to be executable, loose enough that
the model's language strength applies. The related agent work (LLM agents executing Gherkin against a live system)
succeeds on simple scenarios and struggles with failure scenarios and fault detection — so *execution* should stay
deterministic, *authoring* can be LLM.

*Types can police the glue.* In a Scala BDD stack (cucumber-scala or specs2 Given/When/Then), step definitions are
ordinary typed functions. The seam works as follows:

1. Human (or LLM, human-reviewed) writes scenarios — the spec, in the vericoding sense.
2. Scala declares the step *signatures*: each Given/When/Then step is a typed hole, e.g. a function from a domain
   opaque type to an effectful state transition. Parameters are parsed into opaque types, so an ill-typed scenario fails
   to bind before anything runs.
3. The LLM fills the step bodies — inhabitants of the signatures — with the typed-hole context from
   [Types and Properties](where-types-and-properties-buy-most.md).
4. Scenarios run as the *visible* suite; a held-out set of scenarios (SpecBench pattern) and ScalaCheck properties
   over the same step algebra form the gate.

The property connection is the payoff: He et al.'s result that property-oriented, minimal counterexample feedback
outperforms I/O tests means a scenario's *Then* is best expressed as a property over the state, with the concrete
Given/When as one generator among many. A Gherkin scenario is then an *example* of a property, readable by the human,
while the property is the thing the LLM is actually held to.

*Limits.* Gherkin cannot express types or totality; the seam covers behaviour, not shape. It complements, not replaces,
the type-level rules (R-2…R-6). And the model's known omissions mean scenario *completeness* is the human's job.

## What is genuinely new here

None of the four paradigms is Scala-specific; what is new is the composition: a typed BDD step algebra whose step
signatures are typed holes, whose Then-clauses are properties with shrinking feedback, whose scenarios split into
visible and held-out suites, and whose pure core can be lifted to Stainless. Each piece has evidence; the composition
has none yet — it is the experiment to run.

> **Status: Disputed**
> Later measurements split the seam in two. The *textual* half does not help the agent: Gherkin given as the coding
> agent's spec scored 6.8% lower TPR than natural language on average, winning 14 out of the 30 combinations (Feng et
> al.), and models follow test-specified rules inconsistently (mean switching rates from 11.1% to 65.8%) while explicit
> descriptions elicit the rule on every instance (Liang et al.). The *executable and typed* half does: the typed
> signature model improved average TPR (+7.82%) (Feng et al.); a source-to-test map cut agent regressions from 6.08% to
> 1.82%, while generic TDD instructions without it raised them to 9.94% (TDAD). Gherkin suites also carry a measured
> duplication cost: 80.2% of 1,113,616 public steps are exact duplicates (Mughal et al.). The roadmap therefore keeps
> typed step signatures, properties and the holdout, and drops the Gherkin/BDD layer — see
> [Roadmap: More Scala, Less LLM](roadmap-more-scala-less-llm.md), item 7.

## See Also

- [Where Types and Properties Buy the Most](where-types-and-properties-buy-most.md)
- [What Goes Wrong in LLM-Generated Code](llm-code-defect-taxonomy.md)
- [Roadmap: More Scala, Less LLM](roadmap-more-scala-less-llm.md)
- [Recording a Migration](../reengineering/recording-a-migration.md) — R-12/R-13 as specification governance
- [Cats & Category-Theory Patterns](../scala-typelevel-fp/cats-category-theory-patterns.md) — laws as isomorphic checks
