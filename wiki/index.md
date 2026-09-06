# Knowledge Base Index

## scala-typelevel-fp

Scala 3 + Typelevel/cats-effect architecture, type-level design, and coding conventions.

| Article | Summary | Updated |
|---------|---------|---------|
| [Tagless-Final Architecture & Hand-Wired DI](scala-typelevel-fp/tagless-final-and-di-architecture.md) | Module boundaries via tagless-final algebras, Resource-based hand-wired DI, anti-patterns | 2026-09-06 |
| [Scala 3 Type-Level Features](scala-typelevel-fp/scala3-type-level-features.md) | Opaque types, abstract type members, given/using, union/intersection types, match/phantom types, inline/Mirror/compiletime metaprogramming | 2026-09-07 |
| [Cats & Category-Theory Patterns](scala-typelevel-fp/cats-category-theory-patterns.md) | Functor/Monad laws (incl. the categorical endofunctor+μ/η definition), Free structures, Semigroup/Monoid, Profunctor, Arrow, Monocle optics | 2026-09-07 |
| [Project Structure & Testing](scala-typelevel-fp/project-structure-and-testing.md) | Multi-module sbt/mill layout, http4s+tapir organization, testing, error handling | 2026-09-06 |
| [Typelevel FP References](scala-typelevel-fp/typelevel-fp-references.md) | Key people/projects (Volpe, De Goes, SystemFw, Milewski, etc.) and corpus caveats | 2026-09-07 |
| [Recursion Schemes and the Droste Library](scala-typelevel-fp/recursion-schemes-and-droste.md) | Catamorphism/anamorphism/hylomorphism/histomorphism zoo, droste's Algebra/Coalgebra/Fix/Basis, and heuristics for spotting recursion-scheme shape when the structure isn't an obvious tree | 2026-09-06 |
| [Shared State in Cats Effect](scala-typelevel-fp/shared-state-cats-effect.md) | Ref vs IOLocal vs IOLocal[Ref] tradeoffs for module/request state, and when each is the right default | 2026-09-06 |
| [Shapeless 3: Generic Type Class Derivation](scala-typelevel-fp/shapeless3-generic-derivation.md) | K0/K1 generic derivation (ProductInstances, CoproductInstances, Generic), a Monoid/Show derivation example, and when it earns its keep over plain `derives` | 2026-09-06 |
