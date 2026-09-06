# Cats & Category-Theory-Driven Design Patterns

> Sources: Local research corpus, 2026-09-06; Bartosz Milewski, 2016-12-27
> Raw: [scala-coding-practices-research.md](../../raw/scala-typelevel-fp/scala-coding-practices-research.md); [Monads Categorically](../../raw/scala-typelevel-fp/2016-12-27-monads-categorically.md)
> Updated: 2026-09-07

## Overview

Cats provides category-theory abstractions (Functor/Monad laws, Free structures, Comonad, Semigroup/Monoid, Profunctor, Arrow, optics) used as design constraints at module boundaries — each with a narrow, situational trigger rather than blanket applicability.

## Functor/Applicative/Monad laws as interface constraints

Require the weakest lawful abstraction a module needs. Laws (identity, composition, associativity) are design constraints: a lawful `Semigroup`/`Monad` instance guarantees composability across module boundaries. Enforce with `cats-laws` (see [Project Structure & Testing](project-structure-and-testing.md)).

**The categorical shape behind `Monad`** (Bartosz Milewski, "Monads Categorically," 2016-12-27): a monad is an endofunctor `T` equipped with two natural transformations — `μ` (`join`, component `T(T a) -> T a`) and `η` (`pure`/`return`, component `a -> T a`) — satisfying associativity and unit laws. The famous compression of this: "monad is just a monoid in the category of endofunctors" (attributed to Saunders Mac Lane) — function composition is the tensor product on endofunctors, and `μ`/`η` are exactly a monoid's combine/identity in that category. Monads also arise from an adjunction `L ⊣ R` as the composite `R ∘ L`, with `μ = R ∘ ε ∘ L`; cats-effect's `IO`/`State`-style monads fit this same shape, `flatMap` being `map` followed by `μ`. This is background for *why* `Monad` laws take the form they do — day-to-day module design should still be driven by the weakest lawful abstraction a module actually needs, not by reaching for the categorical framing itself.

## Free structures for composing independent algebras

`Free`/`FreeApplicative` + `EitherK` (Coproduct) + `InjectK` let independently-developed modules be combined into one program and interpreted together — used when modules are developed separately and one interpretation point is wanted. Full tradeoff discussion in [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md); the underlying Coproduct/Inject mechanism and the provider/consumer framing behind it are in [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](provider-consumer-least-power.md).

`Free` and its dual `Cofree` also underpin two recursion schemes — futumorphism and histomorphism, respectively — see [Recursion Schemes and the Droste Library](recursion-schemes-and-droste.md).

## Comonad-based architectures

`Comonad` (`extract`/`coflatMap`) models context-dependent reads; `Cokleisli[F, A, B]` = `F[A] => B`. Fits streaming/windowed computations, spreadsheet-like dataflow, UIs — niche. Rarely warranted in typical services; treat as advanced/optional.

## Semigroup / Monoid composition

`combine`/`|+|` and `empty` merge configs, aggregate results, and compose modules. `Kleisli[F, A, B]` has a `Monoid` when `F[B]` does; `HttpRoutes` combine via `SemigroupK`'s `<+>`/`combineK`. Used for merging layered configuration, fold-aggregating validation results (`NonEmptyChain` with `Validated`), and combining routes:

```scala
val allRoutes = itemRoutes <+> brandRoutes <+> orderRoutes
```

tapir/http4s route composition relies on `SemigroupK[Kleisli]`; `type HttpRoutes[F] = Kleisli[OptionT[F,*], Request[F], Response[F]]`, tried in order until one returns non-`None`.

## Profunctor / contravariant functors at boundaries

`Profunctor` (`dimap`), `Contravariant` (`contramap`) adapt inputs/outputs at a boundary without changing the core. tapir codecs/`Schema` and circe `Encoder`/`Decoder` are contravariant/covariant boundary encoders — use `contramap`/`map` to derive one codec from another, e.g. `Encoder[Money] = Encoder[BigDecimal].contramap(_.amount)`. Use to adapt a domain type to a wire type at the edge while keeping the domain clean.

## Arrow / Category / Compose for pipelines

`cats.arrow.{Arrow, Compose, Category}` formalize composable pipelines. `Kleisli[F, A, B]` is an `Arrow` when `F` is a `Monad`; compose effectful stages tip-to-tail (`andThen`/`compose`/`>>>`), split/fan inputs (`***`, `&&&`):

```scala
val pipeline: Kleisli[IO, Unit, Boolean] =
  Kleisli(getFromDb) andThen process andThen writeToDb
// fan-out with Arrow:
val headPlusLast = (headK &&& lastK) >>> Arrow[Kleisli[Option, *, *]].lift((_ + _).tupled)
```

For genuinely streaming/backpressured pipelines use fs2 `Stream`/`Pipe` (`Pipe[F,A,B] = Stream[F,A] => Stream[F,B]`) rather than raw Kleisli arrows.

## Optics (Monocle) across module boundaries

`Lens` (product focus), `Prism` (sum focus), `Optional`, `Iso`, `Traversal` are composable, purely-functional getters/setters for immutable nested data. Use to update deeply nested domain state without leaking internal structure across modules, modify one branch of an ADT (`Prism`), or run effectful updates inside a structure (`modifyF`).

Scala 3 `Focus`: `Focus[User](_.address.street.name).modify(_.capitalize)(user)`; also `GenLens`, `composePrism`.

Optics that "break the contract" (e.g. filtered traversals) violate composition laws (`modify a` then `modify b` ≠ `modify (a∘b)`) — keep optics lawful, and don't reach for them where a simple `copy` suffices.

## See Also

- [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md)
- [Scala 3 Type-Level Features](scala3-type-level-features.md)
- [Project Structure & Testing](project-structure-and-testing.md)
- [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](provider-consumer-least-power.md)
