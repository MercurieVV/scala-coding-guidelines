# Where Types and Properties Buy the Most: Compiler-Guided Generation

> Sources: Mündler et al., 2025-04-12; Blinn et al., 2024-09-02; Bursuc et al., 2025-09-26; Zhang & Kothari, 2026-06-16; He et al., 2025-06-23; Jing et al., 2026-05-13; Khati et al., 2026-01-27; Lang et al. (FPEval), 2026-01-05; Lee et al., 2023-09-25
> Raw: [Type-constrained code generation](../../raw/llm-scala-fusion/2025-04-12-type-constrained-code-generation-pldi-2025.md); [Typed holes (Hazel)](../../raw/llm-scala-fusion/2024-09-02-statically-contextualizing-llms-with-typed-holes.md); [Vericoding benchmark](../../raw/llm-scala-fusion/2025-09-26-vericoding-benchmark-formally-verified-program-synthesis.md); [Iterative feedback loops](../../raw/llm-scala-fusion/2026-06-16-llm-code-correction-iterative-feedback-loops.md); [Property-Generated Solver](../../raw/llm-scala-fusion/2025-06-23-property-generated-solver-property-oriented-feedback.md); [PBT-Bench](../../raw/llm-scala-fusion/2026-05-13-pbt-bench-agents-property-based-testing.md); [Deterministic AST hallucination correction](../../raw/llm-scala-fusion/2026-01-27-deterministic-ast-hallucination-detection-correction.md); [Perish or Flourish (FPBench)](../../raw/llm-scala-fusion/2026-01-05-perish-or-flourish-llm-functional-programming-fpbench.md); [Guess & Sketch](../../raw/llm-scala-fusion/2023-09-25-guess-and-sketch-neurosymbolic-transpilation.md)
> Updated: 2026-09-19

## Overview

The literature on making LLM code correct splits into five mechanisms, ordered here from "most deterministic, least
available to us" to "least deterministic, most available": (1) constrained decoding by the type system, (2) static
context from typed holes, (3) deterministic post-processing of the output, (4) compiler/verifier-in-the-loop repair,
(5) property-based feedback. Each maps onto a Scala-side capability. The closing section turns them into a decision
map: which sub-tasks Scala should own outright, which the LLM should own, and where the seam is.

## 1. Type-constrained decoding (the ideal we cannot host)

Mündler et al. (PLDI 2025) constrain the *sampler*: a token survives only if the prefix can still be completed to a
well-typed program, implemented with prefix automata and a search over inhabitable types, formalised for a simply-typed
core and extended to TypeScript. It "reduces compilation errors by more than half" and raises functional correctness on
synthesis, translation and repair, including open-weight models "with more than 30B parameters". The catch for this
project: it requires logit access, so it is unavailable behind hosted APIs; it is only reachable via a local open-weight
model driven by a harness such as dsh. Scala's type system (implicits, HKTs, path-dependent types) is also far harder
to run as a prefix automaton than TypeScript's. File under *long-range*; the near-term substitute is (2)+(3)+(4).

## 2. Typed holes: static context before generation

Blinn et al. (Hazel, OOPSLA 2024) attack the *input* rather than the sampler. Their thesis — "AIs need IDEs, too!" —
is that hallucination comes from missing context, especially for definitions "not in the training data nor near the
cursor". The language server identifies the type and typing context of the hole being filled, "even in the presence of
errors", and the prompt carries "codebase-wide contextual information not lexically local to the cursor". Their key
empirical result: "contextualization with type definitions is particularly impactful". They propose ChatLSP, a
conservative LSP extension exposing this to any completion system.

Scala already has first-class holes (`???`) and a presentation compiler that knows the expected type at a position.
The gap is that no agent-facing tool exposes "expected type of this hole + in-scope bindings of compatible type +
applicable given instances" as one call. That is the single most direct upgrade ScalaSemantic could make (see
[Harness and Tooling Gaps](harness-and-tooling-gaps.md)).

## 3. Deterministic post-processing

Khati et al. show a non-LLM fix for Knowledge-Conflicting Hallucinations (non-existent API parameters and identifiers):
parse the output to an AST and validate against a knowledge base built by library introspection. On 200 Python
snippets: "100% precision and 87.6% recall (0.934 F1-score)", auto-correcting "77.0%" of the hallucinations found. The
authors position this explicitly as an alternative to "non-deterministic LLM-in-the-loop repair". In Scala the compiler
already refuses unknown symbols, but a SemanticDB/classpath check on a *diff* is cheaper than a full compile and can
also cover things the compiler does not see until later — build coordinates that do not resolve, or a symbol used
outside the module's allowlist (R-2).

## 4. Compiler / verifier in the loop

Zhang & Kothari's study of iterative refinement (compiler errors + test feedback per attempt, four models, two
languages) finds that "syntactic and runtime errors are far more tractable than logical or algorithmic failures" and
that reasoning models exploit feedback much better than non-reasoning ones. FPEval adds that the feedback works better
when it is *translated*: they pair static-analysis findings with "hand-crafted instructions for common types of issues"
and get partial repair of both correctness and style.

Vericoding is the strongest form of this loop: generate code from a *formal* spec until the verifier accepts. Bursuc et
al.'s benchmark (12,504 specifications) reports success of 27% in Lean, 44% in Verus/Rust and 82% in Dafny with
off-the-shelf LLMs, pure Dafny verification rising from 68% to 96% in a year — and, notably, "Adding natural-language
descriptions does not significantly improve performance". The verifier, not the prose, carries the signal. For Scala
the analogue is Stainless on a collection-free core (see
[Stainless in a Mill build](../stainless-verification/stainless-mill-integration.md)); the vericoding numbers say the
approach is realistic for small, pure kernels and unrealistic for whole modules.

Guess & Sketch (Lee et al.) is the neurosymbolic template for splitting work: the LM "guesses", and a symbolic solver
resolves semantic equivalence, using LM confidence to decide where to invoke the solver; it transpiled "57.6% more
examples than GPT-4 and 39.6% more examples than an engineered transpiler". The pattern generalises: LLM proposes,
deterministic tool disposes, and the *routing* is by confidence or by decidability (R-11's boundary).

## 5. Properties as the feedback channel

Two results make property-based testing the best seam between the statistical and the strict side.

He et al.'s Property-Generated Solver replaces I/O-example tests with checked *properties* plus the "simplest failing
counterexample" — i.e. shrinking — as feedback. It gains "up to 13.4% in pass@1" over TDD-style methods, an "over 64%
fix rate" on initially-failed problems and a bug-fix rate "1.4x-1.6x higher than the strongest debugging-based
approaches". Their v1 framing names the disease: I/O tests generated alongside the code share its misconceptions, a
"cycle of self-deception". Properties break the cycle because they state *what*, not *which*.

PBT-Bench (Jing et al., 100 problems, 40 real Python libraries) measures whether agents can *write* properties and
targeted generators: recall of 42.1% to 83.4% with PBT scaffolding versus 31.4% to 76.7% open-ended, with scaffolding
lifting mid-capability models "by over 20 percentage points" but sometimes degrading the strongest ones. Reading: LLMs
can produce properties given structure, the hard part is the generator that reaches the bug — and that is where a typed
`Arbitrary`/`Gen` with domain-shaped opaque types helps, because the type already excludes the uninteresting inputs.

ScalaCheck shrinks natively, so the PGS loop is nearly free: property fails → shrunk counterexample → single, minimal
feedback message to the LLM.

## Decision map: Scala owns / LLM owns / the seam

| Sub-task | Owner | Why (evidence) |
|---|---|---|
| Symbol/API existence, arity, types | **Scala** (compiler, SemanticDB pre-check) | Deterministic AST check: 100% precision; hallucinated objects/attributes are a top defect class |
| Idiom (pure, polymorphic, arrow-shaped) | **Scala** (scalafix rewrite) | 94% imperative patterns in Scala output; prompting moves it only from 77% → 57% |
| Absence, totality, exhaustiveness | **Scala** (types, R-5/R-6) | Missing-corner-case bugs are semantic; making them unrepresentable is the only static answer |
| Module boundary / allowlist | **Scala** (AllowedShape rule, R-2) | Decidable; no judgement needed |
| Verifying what the diff claims | **Scala + independent lane** (R-8, R-9) | Reward hacking scales with code size; author's summary is not evidence |
| Invariants on a pure kernel | **Scala** (Stainless) | Vericoding works when the spec is formal and the kernel is small |
| Choosing the algebra / shape | **LLM + human** | Design judgement; R-11 forbids letting a machine rule stand in for it |
| Writing properties from a spec | **LLM**, scaffolded | PBT-Bench: feasible with scaffolding; generator quality is the weak spot |
| Filling a typed hole | **LLM**, given hole context | Hazel: type definitions in context are the most impactful signal |
| Interpreting an ambiguous requirement | **LLM + human** | Misinterpretation is uncatchable statically |

The seam is always the same shape: Scala states the *type* or *property*, the LLM produces an *inhabitant*, Scala checks
it, and the counterexample or diagnostic — translated to an instruction — goes back. Everything that can be moved from
the right column to the left is a win; the literature says the left column is far from exhausted.

## See Also

- [What Goes Wrong in LLM-Generated Code](llm-code-defect-taxonomy.md)
- [Harness and Tooling Gaps](harness-and-tooling-gaps.md)
- [Alternative Paradigms and the BDD Seam](alternative-paradigms-and-bdd-seam.md)
- [The Decidable Boundary](../reengineering/decidable-vs-judgement.md) — R-10/R-11 as the routing rule
- [Integrating Stainless verification into a Mill build](../stainless-verification/stainless-mill-integration.md)
