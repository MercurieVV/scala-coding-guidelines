# Typelevel FP: Notable Open-Source Resources & People

> Sources: Local research corpus, 2026-09-06; John A De Goes, 2019-06-18; Jakub Kozłowski, 2024-03-02; Bartosz Milewski, 2016-12-27; Daniel Beskin, 2023-12-06; Christian Hollinger
> Raw: [scala-coding-practices-research.md](../../raw/scala-typelevel-fp/scala-coding-practices-research.md); [The False Hope of Managing Effects with Tagless-Final in Scala](../../raw/scala-typelevel-fp/2019-06-18-tagless-final-false-hope.md); [Flavors of Shared State in Cats Effect](../../raw/scala-typelevel-fp/2024-03-02-flavors-of-shared-state-cats-effect.md); [Monads Categorically](../../raw/scala-typelevel-fp/2016-12-27-monads-categorically.md); [Scala 3: Type-Level Programming](../../raw/scala-typelevel-fp/2023-12-06-scala3-type-level-programming.md); [Functional Programming Concepts I Actually Like](../../raw/scala-typelevel-fp/functional-programming-concepts-i-actually-like-scala.md)
> Updated: 2026-09-07

## Overview

Reference index of people, projects, and writeups underpinning the Typelevel/cats-effect architecture conventions catalogued in this topic — useful as citation lookups rather than a standalone concept.

## People & works

- **Gabriel Volpe** — *Practical FP in Scala* + `gvolpe/pfps-shopping-cart` (Scala 3, second-edition branch): canonical tagless-final + Resource wiring; stack = cats-effect, fs2, http4s, skunk, redis4cats, refined, Ciris. Source of naming conventions and algebras/programs/interpreters split (see [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md)). Also `gvolpe/trading` (*Functional Event-Driven Architecture*, Scala 3) for multi-module layout (see [Project Structure & Testing](project-structure-and-testing.md)); blog `gvolpe.com` covers error handling with union types in Scala 3 (published 2022-02-08, see [Scala 3 Type-Level Features](scala3-type-level-features.md)).
- **John De Goes** — "The False Hope of Managing Effects with Tagless-Final in Scala" (2019-06-18, [raw](../../raw/scala-typelevel-fp/2019-06-18-tagless-final-false-hope.md)): the authoritative anti-pattern/alert source on premature indirection, untestable effects, and the parametric-reasoning nuance behind minimal typeclass constraints — see [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md).
- **Fabio Labella (SystemFw)** — "Composable concurrency with Ref + Deferred" (Scala Italy 2019, YouTube): concurrent state machines, shared state via regions-of-sharing (pass as arguments); foundational for cats-effect concurrency design. Companion: Inner Product "Concurrent state machines."
- **Rob Norris (tpolecat)** — skunk & doobie author; talks on functional Postgres access and tagless style.
- **Jakub Kozłowski (kubukoz)** — "Flavors of shared state in Cats Effect" (2024-03-02, [raw](../../raw/scala-typelevel-fp/2024-03-02-flavors-of-shared-state-cats-effect.md)): `Ref` vs `IOLocal` vs `IOLocal[Ref]` guidance for module state — see [Shared State in Cats Effect](shared-state-cats-effect.md).
- **Adam Warski / SoftwareMill** — tapir docs + "Bootzooka 2022: cats-effect 3, autowire & tapir", "Free and tagless compared", "Cats Effect vs ZIO": endpoint/route organization, autowire DI, free-vs-tagless decision guidance.
- **Typelevel org docs** — cats (Kleisli, Arrow, law testing), cats-effect (testing, TestControl, Resource DI), cats-mtl ("Custom Error Types Using Cats Effect and MTL"), weaver-test, scalacheck-effect, discipline.
- **Monocle docs + Rock the JVM** optics articles ("Lenses, Prisms and Optics"); **Iron** (opaque-type refinement, antonkw blog).
- **DevInsideYou** — "Diamond Architecture" (YouTube playlist; `DevInsideYou/diamond-architecture`): build-graph-topology architecture for fast parallel compiles. Credit DevInsideYou, not Volpe — a frequent conflation.
- **Bartosz Milewski** — "Monads Categorically" (Categories for Programmers, part 22, 2016-12-27, [raw](../../raw/scala-typelevel-fp/2016-12-27-monads-categorically.md)): the categorical `Monad = endofunctor + μ/η` definition and "monoid in the category of endofunctors" — see [Cats & Category-Theory Patterns](cats-category-theory-patterns.md).
- **Daniel Beskin (Rock the JVM)** — "Scala 3: Type-Level Programming" (2023-12-06, [raw](../../raw/scala-typelevel-fp/2023-12-06-scala3-type-level-programming.md)): `inline`/`Mirror`/match-type/`compiletime.ops` metaprogramming worked through a flat-JSON-codec example, with an explicit strengths/weaknesses tradeoff list — see [Scala 3 Type-Level Features](scala3-type-level-features.md).
- **Christian Hollinger** — "Functional Programming Concepts I Actually Like" ([raw](../../raw/scala-typelevel-fp/functional-programming-concepts-i-actually-like-scala.md)): a practitioner's account of which FP ideas (type-driven design, ADTs as domain models, `IO`, typeclasses via implicits) paid off in practice, contrasted with the OOP patterns they replaced; also flags category-theory jargon as a real accessibility cost, independent of the ideas' value.

## Caveats on this corpus

- "Diamond Architecture" is DevInsideYou's, not Gabriel Volpe's — it targets build-graph parallelism/compile speed, not domain layering; do not conflate with onion/hexagonal.
- Some `pfps-shopping-cart` wiring snippets originate from the first edition (Scala 2, `master`); the second edition (Scala 3) keeps the identical hand-wiring pattern but renames `Algebras`→`Services` and swaps Blaze→Ember. Package names are corroborated by README, commit diffs, and the book ToC, but exact second-edition `LiveXxx` source lines were not independently fetched.
- De Goes writes from a ZIO-advocacy position; his tagless-final critique is technically sound but read the effect-indirection arguments knowing that context. The Typelevel community continues to use tagless-final productively with discipline.
- Match types, comonads, and phantom/type-state encodings are advanced and rarely needed in ordinary services — flagged as optional/situational, not defaults.
- This corpus reflects conventions as of 2026; library specifics (cats-mtl `Handle` syntax, Iron, Monocle `Focus`) are Scala 3-era and may not apply to Scala 2 codebases. Version-specific dependency coordinates (e.g., `munit-cats-effect-3`) shift over time — verify current versions before use.
- Category-theory terminology (endofunctor, natural transformation, "monoid in the category of endofunctors") is precise but a real onboarding cost — Hollinger's independent observation matches this corpus's own framing (see [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)): the categorical shape explains *why* the laws take their form, but day-to-day module design should be driven by the weakest lawful abstraction needed, not by leading with the theory.

## See Also

- [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md)
- [Scala 3 Type-Level Features](scala3-type-level-features.md)
- [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)
- [Project Structure & Testing](project-structure-and-testing.md)
- [Shared State in Cats Effect](shared-state-cats-effect.md)
