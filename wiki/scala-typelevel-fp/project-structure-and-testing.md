# Project Structuring, Testing & Error Handling (Typelevel)

> Sources: Local research corpus, 2026-09-06
> Raw: [scala-coding-practices-research.md](../../raw/scala-typelevel-fp/scala-coding-practices-research.md)
> Updated: 2026-09-13

## Overview

Practical conventions for laying out multi-module sbt/mill Typelevel builds, organizing http4s+tapir services, testing module boundaries, and handling errors without `EitherT` stacks.

## sbt/mill multi-module structure

Module boundaries in the build mirror architecture boundaries; `dependsOn` encodes allowed dependency direction (compiler-enforced). Single `build.sbt`, `aggregate` for build-all, `dependsOn` for code deps.

**`gvolpe/trading` layout** (Scala 3): `modules/{domain, lib, core, feed, alerts, forecasts, processor, snapshots, ws-server, ws-client, tracing, it}`. Per the README: `domain` = "Commands, events, state, and all business-related data modeling"; `lib` = "Capability traits such as Logger, Time, GenUUID... library abstractions such as Consumer and Producer, which abstract over different implementations such as Kafka and Pulsar"; `core` = "Core functionality that needs to be shared across different modules such as snapshots, AppTopic, and TradeEngine."

**`pfps-shopping-cart` layout** (Scala 3, single core module) under `modules/core/src/main/scala/shop/`: `config/` (Ciris `data.scala`/`loader.scala`), `domain/` (newtypes + refined models), `services/` (algebras + live interpreters), `programs/` (Checkout business logic), `http/routes/` + `http/clients/`, `effects/` (capability traits `GenUUID`, `Time`, `Background`), `modules/` (wiring objects), `ext/` (orphan instances), `resources.scala` (`AppResources`), `Main.scala`.

Put the pure `domain` module at the bottom of the graph with no Typelevel-library deps so it compiles fast and stays portable. DevInsideYou's "Diamond Architecture" (not Volpe's — see [Typelevel FP References](typelevel-fp-references.md)) specifically targets build-graph parallelism by splitting the `core` choke-point module.

## http4s + tapir organization

Separate: (1) tapir `Endpoint` descriptions (pure I/O contract, often in companion objects), (2) server logic (`serverLogic` binding `I => F[Either[E,O]]`), (3) business logic (algebras/programs). Interpret endpoints to `HttpRoutes[F]` via `Http4sServerInterpreter`. Compose routes with `<+>`/`combineK` (SemigroupK on `Kleisli`, see [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)) — routes are matched in order.

Decision rule (ClearScore/dev.to): use tapir for large REST APIs (dedup paths, free OpenAPI/Swagger, type-safe); use raw http4s DSL for a handful of endpoints. tapir-managed and hand-written http4s routes can co-exist. Keep server logic referentially transparent — the binding logic should call programs/algebras, never contain wiring.

## Testing module boundaries

Effectful unit tests: `munit-cats-effect` (`CatsEffectSuite`, tests return `IO`, run in parallel) or **weaver-test**. Per the `typelevel/weaver-test` README: weaver "was built for integration/end-to-end tests... Tests are run concurrently by default... Logs are only displayed when a test fails... 'beforeAll' and 'afterAll' logic is represented using a cats.effect.Resource" — best fit for integration/e2e with shared resources (HTTP clients, connection pools).

Never call `unsafeRunSync` in tests — return `IO` like production code.

Property tests: scalacheck; effectful properties via `scalacheck-effect` (`PropF.forAllF`).

**Law testing as architecture enforcement:** `cats-laws` + `discipline-munit`: `checkAll("Tree.Functor", FunctorTests[Tree].functor[Int,Int,String])`. Verify every custom typeclass instance at a module boundary is lawful (needs `Eq` + `Arbitrary`) — makes typeclass compliance a CI gate.

Time-dependent logic: use cats-effect `TestControl.executeEmbed` to test instantly instead of real sleeps.

## Error-handling architecture

Typed errors via ADTs + `MonadError`/`ApplicativeError`: model domain errors as sealed `enum`; raise with `raiseError`, handle with `handleErrorWith`/`recoverWith`.

**Prefer cats-mtl `Raise`/`Handle` over `EitherT` stacks** (cats-mtl ≥1.6, Scala 3):

```scala
def parse[F[_]](s: String)(using Raise[F, ParseError], Monad[F]): F[Result] = ???
val program: IO[Unit] = Handle.allow[ParseError]:
  for { x <- parse[IO](a); y <- parse[IO](b) } yield ()
.rescue: case ParseError.Other(m) => IO.println(s"error: $m")
```

This stays in the mono-functor error channel — preserving cats-effect concurrency/resource semantics — and infers far better than `EitherT`.

**Pitfalls of `EitherT[IO, E, A]`:** double error channel (the `Left` and a failed `IO`), double short-circuiting, painful inference when stacking transformers, and it can break cancellation/concurrency reasoning. Pure functions returning `Either[E,A]` are fine at API boundaries (parsing/validation); avoid `EitherT` over `IO`.

`recoverWith`-style adaptation can lose the original error if not logged — always include failure-path tests, not just happy paths.

## See Also

- [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md)
- [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)
- [Typelevel FP References](typelevel-fp-references.md)
- [Integrating Stainless verification into a Mill build](../stainless-verification/stainless-mill-integration.md)
