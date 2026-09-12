# Knowledge Base Index

## scala-typelevel-fp

Scala 3 + Typelevel/cats-effect architecture, type-level design, and coding conventions.

| Article | Summary | Updated |
|---------|---------|---------|
| [Tagless-Final Architecture & Hand-Wired DI](scala-typelevel-fp/tagless-final-and-di-architecture.md) | Module boundaries via tagless-final algebras, Resource-based hand-wired DI, anti-patterns | 2026-09-07 |
| [Scala 3 Type-Level Features](scala-typelevel-fp/scala3-type-level-features.md) | Opaque types, abstract type members, given/using, union/intersection types, match/phantom types, inline/Mirror/compiletime metaprogramming | 2026-09-07 |
| [Cats & Category-Theory Patterns](scala-typelevel-fp/cats-category-theory-patterns.md) | Functor/Monad laws (incl. the categorical endofunctor+μ/η definition), Free structures, Semigroup/Monoid, Profunctor, Arrow, Monocle optics | 2026-09-07 |
| [Project Structure & Testing](scala-typelevel-fp/project-structure-and-testing.md) | Multi-module sbt/mill layout, http4s+tapir organization, testing, error handling | 2026-09-06 |
| [Typelevel FP References](scala-typelevel-fp/typelevel-fp-references.md) | Key people/projects (Volpe, De Goes, SystemFw, Milewski, etc.) and corpus caveats | 2026-09-07 |
| [Recursion Schemes and the Droste Library](scala-typelevel-fp/recursion-schemes-and-droste.md) | Complete droste usage reference: full Algebra/Coalgebra/RAlgebra/RCoalgebra/CVAlgebra/CVCoalgebra/Gather/Scatter taxonomy, every named scheme (cata/ana/hylo/para/apo/histo/futu/dyna/chrono/zygo/prepro/postpro) with a worked code example each, and heuristics for spotting recursion-scheme shape when the structure isn't an obvious tree | 2026-09-12 |
| [Shared State in Cats Effect](scala-typelevel-fp/shared-state-cats-effect.md) | Ref vs IOLocal vs IOLocal[Ref] tradeoffs for module/request state, and when each is the right default | 2026-09-06 |
| [Shapeless 3: Generic Type Class Derivation](scala-typelevel-fp/shapeless3-generic-derivation.md) | K0/K1 generic derivation (ProductInstances, CoproductInstances, Generic), a Monoid/Show derivation example, and when it earns its keep over plain `derives` | 2026-09-06 |
| [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](scala-typelevel-fp/provider-consumer-least-power.md) | Bjarnason's least-power framing, F-algebra/F-coalgebra duality, and Data Types à la Carte's Coproduct/Inject — one throughline from "pass a function not an interface" to "combine independent algebras via EitherK" | 2026-09-07 |

## stainless-verification

Integrating the Stainless formal-verification tool into Scala builds — architecture, Mill wiring without an official plugin, and standalone-CLI wrapper-script pitfalls.

| Article | Summary | Updated |
|---------|---------|---------|
| [Integrating Stainless verification into a Mill build](stainless-verification/stainless-mill-integration.md) | Verify production code directly (no mirror) by pulling collection-free primitives into a dedicated object; hand-porting sbt-stainless to Mill (unmanaged jar, Task.Command, prePush); known issues/fixes in the standalone-CLI wrapper script (exit-code vs summary parsing, solver timeouts, platform detection, caching, timeout tuning) | 2026-09-07 |
