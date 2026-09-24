# Wiki Log

## [2026-09-06] ingest | Tagless-Final Architecture & Hand-Wired DI
- Disposition: New
- Raw: raw/scala-typelevel-fp/scala-coding-practices-research.md
- Updated: Scala 3 Type-Level Features; Cats & Category-Theory Patterns; Project Structure & Testing; Typelevel FP References

## [2026-09-06] ingest | Recursion Schemes and the Droste Library
- Disposition: New
- Raw: raw/scala-typelevel-fp/droste-library-overview.md; raw/scala-typelevel-fp/2017-11-13-recursion-schemes-scala-elementary-intro.md; raw/scala-typelevel-fp/recursion-schemes-when-structure-unclear-notes.md
- Updated: Cats & Category-Theory Patterns

## [2026-09-06] lint | 0 issues found, 0 auto-fixed

## [2026-09-06] ingest | Shared State in Cats Effect
- Disposition: New; Update
- Raw: raw/scala-typelevel-fp/2019-06-18-tagless-final-false-hope.md; raw/scala-typelevel-fp/2024-03-02-flavors-of-shared-state-cats-effect.md
- Updated: Tagless-Final Architecture & Hand-Wired DI; Typelevel FP References

## [2026-09-06] ingest | Shapeless 3: Generic Type Class Derivation
- Disposition: New
- Raw: raw/scala-typelevel-fp/shapeless3-readme.md; raw/scala-typelevel-fp/2025-04-30-deriving-type-class-instances-shapeless3.md
- Updated: Scala 3 Type-Level Features

## [2026-09-07] ingest | Scala 3 Type-Level Features (research: type-level programming & category theory)
- Disposition: Update
- Raw: raw/scala-typelevel-fp/2023-12-06-scala3-type-level-programming.md; raw/scala-typelevel-fp/2016-12-27-monads-categorically.md; raw/scala-typelevel-fp/functional-programming-concepts-i-actually-like-scala.md
- Updated: Cats & Category-Theory Patterns; Typelevel FP References

## [2026-09-07] lint | 1 issues found, 1 auto-fixed

## [2026-09-07] ingest | Provider/Consumer Design: Least Power, Algebras, and Modular Composition
- Disposition: New; Update
- Raw: raw/scala-typelevel-fp/2016-03-21-applying-least-power-in-scala.md; raw/scala-typelevel-fp/f-algebra-wikipedia.md; raw/scala-typelevel-fp/2008-data-types-a-la-carte.md
- Updated: Tagless-Final Architecture & Hand-Wired DI; Scala 3 Type-Level Features; Cats & Category-Theory Patterns; Recursion Schemes and the Droste Library

## [2026-09-07] ingest | Integrating Stainless verification into a Mill build
- Disposition: New
- Raw: raw/stainless-verification/2026-09-07-scalasemanticmcp-stainless-mill-integration.md

## [2026-09-12] ingest | Recursion Schemes and the Droste Library
- Disposition: Update
- Raw: raw/scala-typelevel-fp/droste-source-algebra-taxonomy.md; raw/scala-typelevel-fp/droste-source-schemes-and-kernel.md; raw/scala-typelevel-fp/droste-source-zoo-gather-scatter.md; raw/scala-typelevel-fp/droste-worked-examples-per-scheme.md; raw/scala-typelevel-fp/droste-worked-examples-per-scheme-postpro.md; raw/scala-typelevel-fp/droste-changelog.md; raw/scala-typelevel-fp/recursion-schemes-haskell-readme.md; raw/scala-typelevel-fp/matryoshka-readme.md

## [2026-09-12] ingest | Recursion Schemes and the Droste Library (GitHub source/smoke-test links)
- Disposition: Update
- Raw: raw/scala-typelevel-fp/droste-worked-examples-per-scheme-postpro.md

## [2026-09-12] ingest | Integrating Stainless verification into a Mill build
- Disposition: Update
- Raw: raw/stainless-verification/2026-09-07-mill-stainless-verification-recipe.md

## [2026-09-12] lint | 0 issues found, 0 auto-fixed

## [2026-09-13] ingest | Droste Algebra/Coalgebra Type Taxonomy
- Disposition: New; Update
- Raw: raw/scala-typelevel-fp/droste-source-basis-and-fixpoints.md; raw/scala-typelevel-fp/droste-macro-derivation.md
- Updated: Recursion Schemes and the Droste Library

## [2026-09-13] ingest | Droste Recursion Scheme Zoo and Worked Examples
- Disposition: New; Update
- Raw: raw/scala-typelevel-fp/droste-athema-example.md
- Updated: Recursion Schemes and the Droste Library

## [2026-09-13] lint | 0 issues found, 0 auto-fixed

## [2026-09-13] lint | 3 issues found, 0 auto-fixed
- Full-wiki lint (both topics), cross-topic focus. Safe-fix categories (index consistency, internal links, Raw references, See Also) all clean, nothing to fix. check_evidence.py: 0 evidence errors, 0 unreferenced raw files, 37 fidelity suspects all verified as false positives (line-wrap/formatting artifacts, or self-evidencing filename-derived titles/dates).
- Judgment findings (not auto-fixed): (1) stainless-verification topic is a single orphan page with zero inbound links and zero cross-topic references to/from scala-typelevel-fp; (2) droste-algebra-coalgebra-taxonomy.md's Scala 3 `derives Functor/Foldable/Traverse` section names shapeless3 but doesn't cross-link the existing dedicated article; (3) Project Structure & Testing's cats-laws/CI-gate section and the Stainless article's prePush verification gate are the same "correctness as a build gate" pattern with no cross-link between them.

## [2026-09-13] lint | 3 issues found, 3 auto-fixed
- Added the three cross-references from the prior lint pass (user-confirmed): See Also links between droste-algebra-coalgebra-taxonomy.md and shapeless3-generic-derivation.md (bidirectional); and between scala-typelevel-fp/project-structure-and-testing.md and stainless-verification/stainless-mill-integration.md (bidirectional, resolves the stainless topic's orphan-page finding). Updated dates refreshed on all four touched articles and in index.md.

## [2026-09-13] research | Refactoring Kleisli to Arrow: When and How
- Disposition: New
- Raw: raw/scala-typelevel-fp/cats-arrow-typeclass-docs.md; raw/scala-typelevel-fp/cats-kleisli-arrowchoice-source.md; raw/scala-typelevel-fp/kleisli-category-wikipedia.md; raw/scala-typelevel-fp/scala-purrism-arrow-patterns.md; raw/scala-typelevel-fp/scala-purrism-engineering-rules.md; raw/scala-typelevel-fp/scala-purrism-readme.md
- Updated: Cats & Category-Theory Patterns; Tagless-Final Architecture & Hand-Wired DI; Droste Algebra/Coalgebra Type Taxonomy

## [2026-09-13] lint | 0 issues found, 0 auto-fixed
- Full-wiki lint (both topics), cross-topic focus, re-run after the Kleisli-to-Arrow ingest. Safe-fix categories (index consistency, internal links, Raw references, See Also) all clean. check_evidence.py: 0 evidence errors, 0 unreferenced raw files, 40 fidelity suspects — same set as the prior pass plus new false positives from kleisli-to-arrow-refactoring.md (backtick-adjacent word breaks, e.g. "as long as `F` is a monad" verified verbatim in raw/scala-typelevel-fp/cats-arrow-typeclass-docs.md), none real.
- Orphan check: no article has zero inbound links; stainless-mill-integration.md (1 inbound, from project-structure-and-testing.md) remains its topic's only article but is no longer isolated. No new missing cross-topic references found — kleisli-to-arrow-refactoring.md's tie-in to droste's Kleisli/Cokleisli-style `.compose`/`.andThen` is already cross-linked from the prior ingest.

## [2026-09-13] ingest | Abstracting Over Abstract Types: Type Members vs. Type Parameters
- Disposition: New
- Raw: raw/scala-typelevel-fp/odersky-zenger-scalable-component-abstractions-oopsla2005.md; raw/scala-typelevel-fp/2009-10-07-venners-abstract-type-members-vs-generics.md; raw/scala-typelevel-fp/2015-07-13-typelevel-type-members-almost-type-parameters.md; raw/scala-typelevel-fp/ghik-opinionated-scala-generics-and-type-members.md; raw/scala-typelevel-fp/2024-04-08-orford-di-and-the-cake-pattern.md
- Updated: Scala 3 Type-Level Features; Tagless-Final Architecture & Hand-Wired DI; Provider/Consumer Design: Least Power, Algebras, and Modular Composition

## [2026-09-13] lint | 2 issues found, 0 auto-fixed

## [2026-09-13] lint | 2 issues found, 2 auto-fixed
- Added See Also link pair: Scala 3 Type-Level Features <-> Integrating Stainless verification into a Mill build
- Added See Also links from Shared State in Cats Effect to Tagless-Final Architecture & Hand-Wired DI and Project Structure & Testing

## [2026-09-13] ingest | Refactoring Kleisli to Arrow: When and How (suggestion: arrow-string infix type aliases)
- Disposition: Update
- Raw: raw/scala-typelevel-fp/scala3-rules-for-operators-infix-types.md; raw/scala-typelevel-fp/cats-functionk-natural-transformation-docs.md; raw/scala-typelevel-fp/sip33-priority-based-infix-type-precedence.md; raw/scala-typelevel-fp/2023-09-16-reintech-guide-to-infix-types-in-scala.md; raw/scala-typelevel-fp/suggestion-arrow-string-infix-type-aliases.md
- Updated: Scala 3 Type-Level Features; Abstracting Over Abstract Types: Type Members vs. Type Parameters

## [2026-09-18] ingest | Named Shape Traits: Do Not Spell a Type Member's Shape at the Use Site
- Disposition: New
- Raw: raw/scala-typelevel-fp/2026-09-18-named-shape-traits.md
- Updated: Abstracting Over Abstract Types: Type Members vs. Type Parameters (See Also link)

## [2026-09-19] ingest | The Reengineering Contract: Rules R-1 … R-14
- Disposition: New
- Raw: raw/reengineering/2026-09-19-kit-design.md

## [2026-09-19] ingest | Absence Unrepresentable: Guarantees in the Type, Contracts on the Member
- Disposition: New
- Raw: raw/reengineering/2026-09-19-kit-design.md

## [2026-09-19] ingest | The Decidable Boundary: What a Machine Decides, What a Human Rules
- Disposition: New
- Raw: raw/reengineering/2026-09-19-kit-design.md

## [2026-09-19] ingest | Verification and the Gate: Receipts, the Build Contract, and the Independent Lane
- Disposition: New
- Raw: raw/reengineering/2026-09-19-kit-design.md

## [2026-09-19] ingest | Recording a Migration: Target Lists, Receipts, and Rulings
- Disposition: New
- Raw: raw/reengineering/2026-09-19-kit-design.md

## [2026-09-19] lint | 0 issues found, 0 auto-fixed

## [2026-09-19] lint | fixed: the final review's findings — R-5's evidence, the record's two roles, the five layers named, two softened inferences, and the plan's cascade and citation defects

## [2026-09-19] ingest | What Goes Wrong in LLM-Generated Code: The Opposite Vector
- Disposition: New (new topic `llm-scala-fusion`; research ingest of 25 web sources; five articles created: defect taxonomy, types-and-properties, harness gaps, alternative paradigms + BDD seam, roadmap)
- Raw: raw/llm-scala-fusion/2024-03-13-bugs-in-llm-generated-code-empirical-study.md
- Raw: raw/llm-scala-fusion/2025-12-04-survey-of-bugs-in-ai-generated-code.md
- Raw: raw/llm-scala-fusion/2026-04-09-static-analysis-for-code-library-hallucinations.md
- Raw: raw/llm-scala-fusion/2025-11-02-systematic-review-code-hallucinations-in-llms.md
- Raw: raw/llm-scala-fusion/2025-03-08-taxonomy-of-inefficiencies-in-llm-generated-python.md
- Raw: raw/llm-scala-fusion/2026-05-22-llm-code-security-across-prompting-methods.md
- Raw: raw/llm-scala-fusion/2026-05-07-agent-generated-code-maintenance-empirical-study.md
- Raw: raw/llm-scala-fusion/2026-01-05-perish-or-flourish-llm-functional-programming-fpbench.md
- Raw: raw/llm-scala-fusion/2026-05-20-specbench-reward-hacking-long-horizon-coding-agents.md
- Raw: raw/llm-scala-fusion/2025-10-23-impossiblebench-llm-test-exploitation.md
- Raw: raw/llm-scala-fusion/2026-04-16-llms-gaming-verifiers-rlvr-reward-hacking.md
- Raw: raw/llm-scala-fusion/2026-05-01-productivity-reliability-paradox-spec-driven-governance.md
- Raw: raw/llm-scala-fusion/2025-04-12-type-constrained-code-generation-pldi-2025.md
- Raw: raw/llm-scala-fusion/2024-09-02-statically-contextualizing-llms-with-typed-holes.md
- Raw: raw/llm-scala-fusion/2025-09-26-vericoding-benchmark-formally-verified-program-synthesis.md
- Raw: raw/llm-scala-fusion/2026-06-16-llm-code-correction-iterative-feedback-loops.md
- Raw: raw/llm-scala-fusion/2025-06-23-property-generated-solver-property-oriented-feedback.md
- Raw: raw/llm-scala-fusion/2026-05-13-pbt-bench-agents-property-based-testing.md
- Raw: raw/llm-scala-fusion/2026-01-27-deterministic-ast-hallucination-detection-correction.md
- Raw: raw/llm-scala-fusion/2023-09-25-guess-and-sketch-neurosymbolic-transpilation.md
- Raw: raw/llm-scala-fusion/2026-02-17-mcp-design-choices-tool-orchestration-vs-code-execution.md
- Raw: raw/llm-scala-fusion/2025-11-04-anthropic-code-execution-with-mcp.md
- Raw: raw/llm-scala-fusion/serena-mcp-semantic-coding-toolkit.md
- Raw: raw/llm-scala-fusion/2025-10-14-survey-of-vibe-coding-with-llms.md
- Raw: raw/llm-scala-fusion/2025-08-28-from-law-to-gherkin-llm-behavioural-specifications.md
- Updated: The Reengineering Contract: Rules R-1 … R-14 (See Also)
- Updated: Verification and the Gate (See Also)

## [2026-09-20] update | Roadmap: More Scala, Less LLM
- Disposition: Update (Tier 1 item 1 marked Shipped with residuals; no new raw source — the record is the spec in docs/ and skreeep2's git history)

## [2026-09-20] update | Roadmap: More Scala, Less LLM
- Disposition: Update (item 1's two residuals closed; the documented out-of-layer limits stated instead)

## [2026-09-20] ingest | Stainless language usage: contracts, ADTs, and proofs
- Disposition: New
- Raw: raw/stainless-verification/2026-09-20-stainless-tutorial-sorting.md; raw/stainless-verification/2026-09-20-stainless-pure-scala-subset.md; raw/stainless-verification/2026-09-20-stainless-proving-theorems.md; raw/stainless-verification/2026-09-20-stainless-verification-conditions.md
- Updated: Integrating Stainless verification into a Mill build

## [2026-09-20] lint | 58 issues found, 0 auto-fixed

## [2026-09-20] update | Roadmap: More Scala, Less LLM
- Disposition: Update (Tier 1 item 2 marked Shipped; runner-shrinking caveat and the open updatedInput check recorded)

## [2026-09-21] update | Roadmap: More Scala, Less LLM
- Disposition: Update (Tier 1 item 3 diagnostic→instruction table marked Shipped — scalafix-purrism v0.9.1 finding catalog + skreeep2 harness adapters/tables; implementation findings recorded)

## [2026-09-24] update | Roadmap: More Scala, Less LLM
- Disposition: Update (Tier 2 item 4 typed-hole context tool marked Shipped — ScalaSemanticMCP `hole_context` + typed-holes skill; implementation findings and deferrals recorded)

## [2026-09-24] update | Roadmap: More Scala, Less LLM
- Disposition: Update
- Tier 2 item 5 marked half shipped: `resolve_references` + one-shot `call` mode on ScalaSemanticMCP master; the skreeep2 `dsh verify-diff` half (spec §2–§4) is still unbuilt.
