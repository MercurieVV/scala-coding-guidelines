# Cats & Category-Theory-Driven Design Patterns

> Sources: Local research corpus, 2026-09-06
> Raw: [scala-coding-practices-research.md](../../raw/scala-typelevel-fp/scala-coding-practices-research.md)
> Updated: 2026-09-06

## Overview

Cats provides category-theory abstractions (Functor/Monad laws, Free structures, Comonad, Semigroup/Monoid, Profunctor, Arrow, optics) used as design constraints at module boundaries — each with a narrow, situational trigger rather than blanket applicability.

## Functor/Applicative/Monad laws as interface constraints

Require the weakest lawful abstraction a module needs. Laws (identity, composition, associativity) are design constraints: a lawful `Semigroup`/`Monad` instance guarantees composability across module boundaries. Enforce with `cats-laws` (see [Project Structure & Testing](project-structure-and-testing.md)).

## Free structures for composing independent algebras

`Free`/`FreeApplicative` + `EitherK` (Coproduct) + `InjectK` let independently-developed modules be combined into one program and interpreted together — used when modules are developed separately and one interpretation point is wanted. Full tradeoff discussion in [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md).

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
