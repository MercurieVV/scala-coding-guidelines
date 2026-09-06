# Tagless-Final Architecture & Hand-Wired DI (Typelevel/cats-effect)

> Sources: Local research corpus, 2026-09-06
> Raw: [scala-coding-practices-research.md](../../raw/scala-typelevel-fp/scala-coding-practices-research.md)
> Updated: 2026-09-06

## Overview

The dominant Typelevel-idiomatic architecture is a layered/onion variant built from tagless-final algebras and cats-effect `Resource`-based hand-wiring — not DI frameworks, not ZIO-style environment layers. Default to concrete `IO` with explicit constructor injection; reach for `F[_]` polymorphism only when a real trigger exists.

## Tagless Final for module boundaries

Encode a module's capabilities as a trait parameterized by a higher-kinded effect: `trait Items[F[_]] { def findAll: F[List[Item]]; def create(i: Item): F[Unit] }`. The trait is the *algebra*; a concrete class is the *interpreter*; code composing algebras is a *program*.

Apply when you want to (1) defer the concrete effect choice, (2) constrain capabilities via typeclass bounds (`F[_]: Monad`), (3) swap interpreters for tests. Best for higher-level business capabilities with a large instruction set. Canonical reference: Gabriel Volpe's `pfps-shopping-cart`.

**Rules:**
- Algebras must stay abstract. Volpe's rule (PFP §2.1): "If you find yourself needing to add a typeclass constraint, such as Monad, to your algebra, what you probably need is a program... Algebras should remain completely abstract." Constraints belong in programs/interpreters.
- Coherence: if an algebra is a typeclass (passed implicitly), there should be exactly one interpreter per `F`. Resolve implicitly only for infrastructural/lawful things (logging, `GenUUID`); pass business algebras explicitly.

**De Goes's warning** (John De Goes, "The False Hope of Managing Effects with Tagless-Final in Scala", degoes.net/articles/tagless-horror): "For applications, using tagless-final to guard against the possibility of changing effect types is usually overengineering (premature indirection)... In the case of tagless-final effects, you also deprive yourself of many lawful operations and added type safety."

## Free monad alternative

Free monads reify a program as an AST (`Free[F, A]`), interpreted later via a natural transformation `F ~> G`. Prefer over tagless-final for cross-cutting concerns, when the program must be inspected/optimized/serialized before running, or when combining many independently-developed algebras via `EitherK`/`InjectK` (Coproduct).

Adam Warski / SoftwareMill: "for expressing higher-level business concepts, where there's a large number of languages (instruction sets), the final tagless approach will be much more convenient... For more cross-cutting concerns, free might be a better choice."

Tradeoffs: more object allocation (Coproduct instances) → slower than tagless-final; more boilerplate; `Inject` machinery is opaque to newcomers; Free is stack-safe (trampolined), while tagless-final's stack-safety depends on the chosen monad.

## ZIO layers vs cats-effect Resource (contrast)

ZIO's `ZLayer` + environment `R` in `ZIO[R,E,A]` gives built-in, composable DI with automatic dependency-graph resolution and a typed error channel. cats-effect has no environment param — DI is done by hand with `Resource` composition and constructor injection (optionally `Kleisli`/`Reader` for reader-like environment).

Stay with `Resource` + hand-wiring for transparency and FILO resource release; reach for `Kleisli`/cats-mtl only for genuine reader semantics. Do not emulate `ZLayer` with heavyweight machinery in cats-effect. `Resource` releases in reverse acquisition order (FILO) automatically — lean on it rather than manual shutdown code.

## Hexagonal / ports-and-adapters

Core domain logic is pure and technology-agnostic; *ports* are traits (often tagless-final algebras); *adapters* are interpreters binding ports to skunk/doobie/http4s/redis4cats. Maps onto "functional core, imperative shell." Reference: `ygunayer/scala3-tagless-final-hexagonal-monorepo`. Watch for "mess in the middle" — over-layering with ceremony the pattern never required.

## Onion/clean layout

Package by concentric dependency direction: `domain` (innermost, no deps) ← `services`/`programs` (application) ← `http`/`clients`/`persistence` (outermost). Dependencies point inward only; domain never imports infrastructure. This matches Volpe's module layout.

## Kleisli / Reader / cats-mtl for framework-free DI

`Kleisli[F, Env, A]` (aka `ReaderT`) threads a read-only environment through a computation; cats-mtl's `Ask`/`Local` abstracts it (`Ask[F, Config]` instead of stacking transformers). Use for request-scoped context (trace IDs, auth principal) or a small global config passed everywhere — `http4s` itself is built this way: `type HttpRoutes[F] = Kleisli[OptionT[F, *], Request[F], Response[F]]`. Overusing Reader for *all* DI leads to `Kleisli` noise and worse inference than plain constructor injection; prefer constructor injection for wiring, reserve `Ask`/`Local` for genuinely ambient context.

## Resource-based DI: the canonical wiring pattern

Every component exposes `def make[F[_]: ...](deps): Resource[F, Component[F]]` (or `F[Component[F]]`); `Main` composes them via a `for`-comprehension. "Capability traits" (`GenUUID[F]`, `Time[F]`, `Logger[F]`) model ambient effects injected implicitly.

```scala
config.load[IO].flatMap { cfg =>
  AppResources.make[IO](cfg).use { res =>
    for
      security <- Security.make[IO](cfg, res.psql, res.redis)
      services <- Services.make[IO](res.redis, res.psql, cfg.cartExpiration)
      clients  <- HttpClients.make[IO](cfg.paymentConfig, res.client)
      programs <- Programs.make[IO](cfg.checkoutConfig, services, clients)
      api      <- HttpApi.make[IO](services, programs, security)
      _        <- EmberServerBuilder.default[IO]...withHttpApp(api.httpApp).build.useForever
    yield ExitCode.Success
  }
}
```

Everything threaded explicitly — no DI framework, no macros, pure constructor injection wrapped in `Resource`/`F`. `AppResources` acquires/releases external resources (http4s `Client`, skunk `Session` pool, redis4cats `RedisCommands`) as one `Resource`, consumed with `.use`. Avoid a single 200-line `for`-comprehension — group related resources into sub-objects (`AppResources`, `Services`, `Programs`, `HttpApi`), each with its own `.make`.

**Naming conventions** (Volpe PFP §2.1.1): algebras = plural domain nouns, no suffix (`Items`, `Brands`, `Orders`) or a consistent `Alg`/`Service` suffix (Scala Steward uses `Alg`); smart constructors named `.make` returning `F[Component]` or `Resource[F, Component]` (never a public constructor); interpreters are the "live" implementations (`object Live`/`class LiveXxx`).

Optional boilerplate-reduction tooling: MacWire (compile-time, zero runtime, `autowire` returns `Resource[IO,_]`), SoftwareMill Bootzooka's `autowire[Dependencies](...)`, or cedi — use only on large graphs.

## Package/module boundary conventions

Algebra vs interpreter vs program: algebras = abstract capability traits; interpreters/"live" = concrete implementations; programs = business logic composing algebras. api/domain/infrastructure separation: pure models + newtypes in `domain`; capability traits in `effects`/`lib`; wiring objects in `modules`; HTTP in `http/routes` + `http/clients`.

## Anti-patterns & alerts

- **Tagless-final overuse / premature indirection** (De Goes): "only library authors have a compelling argument for effect type indirection. In order to maximize market share... they need to support all major effect types... this is completely inapplicable to the closed source applications that make up the majority of Scala software development." The refactor cost between effect types is one-time and semantically driven; indirection cost is paid forever.
- **`F[_]` proliferation without benefit:** if `F` is never instantiated to anything but `IO`, the polymorphism is dead weight, hides the concrete `IO` API, degrades inference, and worsens error messages.
- **"Untestable effects" myth** (De Goes, verbatim): "testability is not a property of tagless-final code... tagless-final programs are not inherently testable. In fact, they are testable only to the degree their tagless-final type classes are testable." `Sync`/`Async`-based type classes capturing arbitrary side effects are inherently untestable.
- **Excessive implicit/given complexity:** deep implicit chains → slow compiles, cryptic errors. Pass business algebras explicitly; reserve implicits for lawful typeclasses and capability traits.
- **`EitherT`/monad-transformer stacks:** avoid stacking over `IO`; use cats-mtl or concrete `IO` + `Either` at edges (see [Cats & Category-Theory Patterns](cats-category-theory-patterns.md), [Project Structure & Testing](project-structure-and-testing.md)).
- **God `for`-comprehension wiring:** split into `.make` sub-modules.
- **Overusing Reader/Kleisli for all DI:** prefer constructor injection; Kleisli only for ambient request context.
- **Leaking interpreters:** never expose demo/insecure admin endpoints or internal interpreters publicly (Volpe README: the didactic admin route "SHOULD NEVER BE MADE PUBLIC").

## See Also

- [Scala 3 Type-Level Features](scala3-type-level-features.md)
- [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)
- [Project Structure & Testing](project-structure-and-testing.md)
- [Typelevel FP References](typelevel-fp-references.md)
