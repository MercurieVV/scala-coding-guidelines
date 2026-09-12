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
