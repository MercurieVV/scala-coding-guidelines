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

## [2026-09-25] update | Roadmap: More Scala, Less LLM
- Disposition: Update
- Tier 2 item 5 now fully shipped: `dsh verify-diff` merged to skreeep2 master, joining `resolve_references` on ScalaSemanticMCP master. Status block records the three silent-pass defects measurement caught and the finding-vs-degradation rule at the commit boundary.

## [2026-09-26] update | Roadmap: More Scala, Less LLM
- Disposition: Update
- Tier 2 item 6 shipped: purrism rewrites applied at turn-end with one review block, and a memorisation check keyed on overlap with test literals (literal_tokens in ScalaSemanticMCP, the judge in the skreeep2 harness). Records the scalafix:ok escape measured not to work for purrism.

## [2026-09-27] update | Verification and the Gate
- Disposition: Update
- Where the gate lives: the engine's home is scala-llm-template `kit/harness`, vendored per project as `.dsh/engine/` with a stamp (spec 2026-09-26-harness-engine-vendoring-design); the "gate never learns their names" claim marked Outdated for Mill.
- Updated: Roadmap: More Scala, Less LLM (a Reengineering Kit status line)

## [2026-09-28] update | Roadmap: More Scala, Less LLM
- Disposition: Update
- Tier 3 item 9 shipped: `contract_clauses` in ScalaSemanticMCP, the Stainless recipe + kernel registry + turn-end vericoding stage + skill in scala-llm-template, and the skreeep2 pilot (kernels verified in place, bodies filled in a live gated session). Records what the real tool changed: `==>` precedence, a NaN-comparing postcondition revised by the human, and runtime contracts unless `StaticChecks` is imported.

## [2026-09-29] update | Roadmap: More Scala, Less LLM
- Disposition: Update
- Item 9 follow-up shipped: the engine closes frozen-path bypasses (post-hoc turn-end check over git's changed paths, SessionStart hash record for git-ignored frozen files, a tool-policy rule for interpreters and inline shells) and caps turn-end tests inside the Stop budget; `.claude/settings.local.json` changes are a note by human decision. Records the skreeep2 live check (inline write denied; the agent refused the computed-path route; a human-side edit proved the post-hoc block and the attempt cap).

## [2026-10-01] ingest | Roadmap: More Scala, Less LLM
- Disposition: Update; Disputed
- Raw: raw/llm-scala-fusion/2026-05-04-structured-spec-driven-repo-level-generation-gherkin.md
- Raw: raw/llm-scala-fusion/2026-03-18-tdad-test-driven-agentic-development.md
- Raw: raw/llm-scala-fusion/2026-02-08-rethinking-value-of-agent-generated-tests.md
- Raw: raw/llm-scala-fusion/2026-07-28-do-code-lms-follow-tests-paired-interventions.md
- Raw: raw/llm-scala-fusion/2026-04-22-duplicate-gherkin-steps-1-1m-step-benchmark.md
- Tier 3 item 7 investigated and not pursued: Gherkin as agent input measured below natural language; typed signatures, a source-to-test map and property feedback carry the measured gains. Follow-up noted: TDAD's test map via the engine's module map.
- Updated: Alternative Paradigms: Spec-First, Neurosymbolic Split, and the BDD Seam (Status: Disputed on the BDD seam)

## [2026-10-01] ingest | no material: raw/llm-scala-fusion/2025-04-09-acceptance-test-generation-llm-industrial-case.md
- Disposition: No material

## [2026-10-01] ingest | no material: raw/llm-scala-fusion/2025-10-21-atomic-llm-acceptance-tests-mobile-industrial.md
- Disposition: No material

## [2026-10-01] ingest | no material: raw/llm-scala-fusion/2026-04-07-spec-kit-agents-context-grounded-workflows.md
- Disposition: No material

## [2026-10-01] ingest | no material: raw/llm-scala-fusion/2026-05-14-given-when-then-again-bdd-subscenario-refactoring.md
- Disposition: No material

## [2026-10-01] ingest | Roadmap: More Scala, Less LLM
- Disposition: Update; Disputed
- Raw: raw/llm-scala-fusion/2024-02-01-codeact-executable-code-actions-llm-agents.md
- Raw: raw/llm-scala-fusion/2026-08-06-bitter-lesson-of-tool-calling.md
- Raw: raw/llm-scala-fusion/2025-11-24-anthropic-advanced-tool-use.md
- Raw: raw/llm-scala-fusion/2026-02-17-mcp-design-choices-results-excerpts.md
- Raw: raw/llm-scala-fusion/bifrost-code-mode-token-savings.md
- Raw: raw/llm-scala-fusion/2026-10-01-local-probe-scalasemantic-code-execution.md
- Tier 3 item 8 investigated and not pursued as a new surface: code-as-tool-use wins in proportion to tool count and result size, with measured losses (parallel calls, three-server orchestration); for ScalaSemantic the tool schemas are already deferred, symbol results are small, and a local probe measured a small saving. The published library already lets an agent script queries through scala-cli.
- Updated: Harness and Tooling Gaps (Status: Disputed on code execution over tool-by-tool orchestration)

## [2026-10-01] ingest | Roadmap: More Scala, Less LLM
- Disposition: Update
- Raw: raw/llm-scala-fusion/2025-10-21-testprune-old-tests-new-tricks-swe-issues.md
- Raw: raw/llm-scala-fusion/2026-06-04-tensorbench-coding-agents-tensor-framework.md
- Raw: raw/llm-scala-fusion/2026-03-18-tdad-results-and-limitations.md
- Raw: raw/llm-scala-fusion/2026-10-01-local-skreeep2-downstream-tests-measurement.md
- Item 7's TDAD test-map follow-up shipped at module level: the build writes each module's dependents with tests into the module map; turn-end runs their turnEndTests (after vericoding, only when nothing earlier blocked) and blocks on red. Evidence: frontier break-rates (TensorBench), relevant-subset gains (TestPrune), TDAD's own caveats; skreeep2 gap and cost measured; oracle 0 mismatches.

## [2026-10-01] ingest | Roadmap: More Scala, Less LLM
- Disposition: Update
- Raw: raw/llm-scala-fusion/2026-10-01-local-gate-wrapper-bypass-probe.md
- Item 1 follow-up: the holdout seal and sbt deny were bypassable through wrapper options and `eval` / `sh -c` (probe in skreeep2); the hook's parser now sees through both, and run-time-computed inline commands are refused. Item 1's "`bash -c` stays outside the layer" no longer holds.

## [2026-10-01] ingest | Roadmap: More Scala, Less LLM
- Disposition: Update; Outdated
- Raw: raw/llm-scala-fusion/2026-10-01-local-live-gated-check-skreeep2.md
- Live gated check in a skreeep2 worktree: downstream turnEndTests block confirmed (item 7 follow-up). Item 1's "wrapper bypass closed" marked Outdated: wrappers with option arguments in front of `eval` / `sh -c` still passed; closed in scala-llm-template b1c8ac4, with the turn-end closing line and clean-compile feedback fixed alongside.

## [2026-10-01] ingest | Roadmap: More Scala, Less LLM
- Disposition: Update
- Raw: raw/llm-scala-fusion/2026-10-01-local-fixstrict-config-error-probe.md
- Item 3 follow-up: `fixStrict` passed (`exit=0`) when scalafix could not run (unknown rule in `.scalafix.conf`, probe in skreeep2); it now fails by name and the engine renders an INFRA record. Closes item 3's open note on swallowed config errors.

## [2026-10-01] ingest | Roadmap: More Scala, Less LLM
- Disposition: Update
- Raw: raw/llm-scala-fusion/2026-10-01-local-delete-only-turn-probe.md
- Item 7 test-map follow-up: a delete-only turn ended green at the Stop gate (probe in skreeep2); turn-end now checks the module that owned a deleted source file and its dependents, and blames errors anywhere in that module on the turn. Closed in scala-llm-template 834d4ea.

## [2026-10-01] ingest | Roadmap: More Scala, Less LLM
- Disposition: Update
- Raw: raw/llm-scala-fusion/2026-10-01-local-holdout-cap-probe.md
- Item 1 follow-up: the turn-end holdout run had no time cap (probe: `sleep 40` under a 20 s budget held the hook 40 s); now capped by `DSH_HOLDOUT_CAP` and the Stop budget. Closed in scala-llm-template 7d23e59.

## [2026-10-02] ingest | Roadmap: More Scala, Less LLM
- Disposition: Update
- Raw: raw/llm-scala-fusion/2026-10-02-local-script-file-seal-probe.md
- Item 1 follow-up: shell script files (and `sh < f`, `cat f | sh`, `xargs sh`, a script written on the same line) reached the sealed holdout suite; tool-policy now reads them and holds their text to the sealed rules. Two live gated runs; residual: run-time-built selectors and non-shell interpreters. Closed in scala-llm-template 709d0ba.

## [2026-10-03] ingest | Roadmap: More Scala, Less LLM
- Disposition: Update
- Raw: raw/llm-scala-fusion/2026-10-03-local-structural-holdout-seal.md
- Item 1 follow-up: structural holdout seal — holdout tests outside the repository (`holdoutDir`, `HoldoutModule`), gate Mill without a daemon into `out-agent`, holdout run into `out-holdout`, Claude Code sandbox denies them to the agent; verified-names report. Live: 11 probes held, the run-time-built selector stopped by the sandbox. Shipped in scala-llm-template 05c0f00 (+0868142), skreeep2 6bf6c1b.

## [2026-10-08] ingest | Roadmap: More Scala, Less LLM
- Disposition: Update
- Raw: raw/llm-scala-fusion/2026-10-08-local-gate-ab-evaluation.md
- Item 1 evaluation: gated vs ungated agent on 8 remove-and-rebuild skreeep2 tasks, 16 runs per arm. Strict report inconclusive (6 tripwire false positives excluded); audited all-32: gated 14/16 vs 10/16 solved, false_done 2 vs 6, median wall 3.9×; pre-registered rule says "does not pay off" on the 2× wall bound. Holdout decided no run. Harness: scala-llm-template branch gate-evaluation (f1d5f62).
