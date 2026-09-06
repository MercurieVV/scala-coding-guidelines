# Scala 3 + Typelevel Modularization: A Coding-Agent Reference Corpus

> Source: Local research corpus (compiled, no single origin URL)
> Collected: 2026-09-06
> Published: Unknown

## TL;DR
- **Default to hand-wired, Resource-based dependency injection with tagless-final algebras** organized into `domain → services (algebras) → programs → http`, wired explicitly in `Main` via `.make` smart constructors — this is the dominant, human-comprehensible convention in the Typelevel ecosystem (canonically demonstrated in Gabriel Volpe's `pfps-shopping-cart`).
- **Use Scala 3 native features aggressively**: opaque types + Iron for newtypes, `enum`/sealed ADTs for domain and error modeling, union types for error channels, `given/using` for typeclass wiring, and cats-mtl `Raise`/`Handle` instead of `EitherT` stacks.
- **Avoid over-abstraction**: `F[_]` polymorphism, tagless-final, Free monads, and MTL are tools with real costs; apply them only when a triggering condition is met, and prefer a concrete `IO` with hand-wiring when in doubt.

## Key Findings
- The Typelevel-idiomatic architecture is a layered/onion variant expressed through tagless-final algebras and cats-effect `Resource`, not through DI frameworks or ZIO-style environment layers.
- Dependency injection "by hand" (constructor injection wrapped in `Resource`/`F`, threaded through a `for`-comprehension in `Main`) is the community default; MacWire/`autowire` and cedi exist for boilerplate reduction but are optional.
- Free monads are now a minority choice; tagless-final won for effectful DSLs, but Free/`Inject`/`EitherK` remains superior for reified, inspectable, cross-cutting programs.
- John De Goes's critique ("premature indirection", untestable `Sync`/`Async` type classes) is the authoritative warning against blind tagless-final/`F[_]` proliferation and should be encoded as explicit alerts.
- Scala 3 changes the mechanics: opaque types replace value classes/`@newtype`; `enum` replaces sealed-trait ADTs; `given/using` replaces `implicit`; union types offer a lighter alternative to `Either`/Coproduct for errors.
- Law testing via `cats-laws` + `discipline-munit` is a concrete architecture-enforcement device for any custom typeclass instance at a module boundary.

---

## Details

### CATEGORY 1 — Module / Architecture Organization Patterns (Typelevel pure-FP)

#### 1.1 Tagless Final (final tagless) for module boundaries
- **(a) What it is:** Encode a module's capabilities as a trait parameterized by a higher-kinded effect: `trait Items[F[_]] { def findAll: F[List[Item]]; def create(i: Item): F[Unit] }`. The trait is the *algebra*; a concrete class is the *interpreter*; code that composes algebras is a *program*.
- **(b) When to apply:** When you want to (1) defer the choice of concrete effect, (2) constrain capabilities via typeclass bounds (`F[_]: Monad`), and (3) swap interpreters for tests. Best for higher-level business capabilities with a large "instruction set."
- **(c) Example/reference:** `trait Console[F[_]] { def putStrLn(s: String): F[Unit]; val getStrLn: F[String] }`; program: `def prog[F[_]: Console: Monad]: F[String]`. Canonical: Volpe `pfps-shopping-cart`, Baeldung "Tagless Final Pattern in Scala".
- **(d) Pitfalls/alerts:**
    - **Algebras must stay abstract.** Volpe's rule (PFP §2.1): *"If you find yourself needing to add a typeclass constraint, such as Monad, to your algebra, what you probably need is a program... Algebras should remain completely abstract."* Constraints belong in programs/interpreters.
    - **Coherence:** if an algebra is encoded as a typeclass (passed implicitly), there should be exactly one interpreter per `F`. Only resolve implicitly for infrastructural/lawful things (logging, `GenUUID`); pass business algebras explicitly.
    - **De Goes warning (verbatim):** *"For applications, using tagless-final to guard against the possibility of changing effect types is usually overengineering (premature indirection)... In the case of tagless-final effects, you also deprive yourself of many lawful operations and added type safety."*
- **(e) Source:** De Goes "The False Hope of Managing Effects with Tagless-Final" (degoes.net/articles/tagless-horror); becompany "Tagless Final Best Practices"; Volpe PFP.

#### 1.2 Free monad / Free applicative boundaries
- **(a) What it is:** Reify the program as a data structure (AST): define an ADT of operations, lift into `Free[F, A]`, interpret later via a natural transformation `F ~> G`. `FreeApplicative` for statically-analyzable/parallel programs.
- **(b) When to prefer over tagless-final:** cross-cutting concerns; when you need to *inspect/optimize/serialize* the program before running; when combining many independently-developed algebras via `EitherK`/`InjectK` (Coproduct). Adam Warski / SoftwareMill, "Free and tagless compared — how not to commit to a monad too early": *"for expressing higher-level business concepts, where there's a large number of languages (instruction sets), the final tagless approach will be much more convenient... For more cross-cutting concerns, free might be a better choice."*
- **(c) Example:** `type App[A] = EitherK[UserOp, LogOp, A]`; `InjectK` lets each algebra be lifted into the coproduct; interpret with `interpUser or interpLog`. Cats `cats.free.Free`, `cats.data.EitherK`, `cats.InjectK`.
- **(d) Pitfalls:** more object allocation (Coproduct instances) → slower than tagless-final; boilerplate; `Inject` machinery is opaque to newcomers; Free is stack-safe (trampolined) whereas tagless-final's stack-safety depends on the chosen monad.
- **(e) Source:** SoftwareMill "Free and tagless compared"; Underscore "Free Monad with Multiple Algebras."

#### 1.3 ZIO layers vs cats-effect Resource wiring (cross-ecosystem contrast)
- **ZIO:** `ZLayer` + environment type `R` in `ZIO[R,E,A]` gives built-in, composable DI with automatic dependency graph resolution (`ZLayer.make`, zio-magic historically) and a typed error channel `E`.
- **cats-effect:** no environment param; DI is done by hand with `Resource` composition, constructor injection, and (optionally) `Kleisli`/`Reader` for a reader-like environment. ZIO migration docs: *"ZIO has built-in dependency injection with ZLayer, while Cats Effect typically relies on external libraries like distage or constructor-based DI."*
- **When to choose which (Typelevel-focused):** stay with `Resource` + hand-wiring for transparency and FILO resource release; reach for `Kleisli`/cats-mtl if you genuinely need reader semantics. Do not emulate `ZLayer` with heavyweight machinery in cats-effect — the Resource `for`-comprehension is idiomatic and sufficient.
- **Alert:** `Resource` releases in reverse acquisition order (FILO) automatically — a feature Guice lacks; lean on it rather than manual shutdown code.

#### 1.4 Hexagonal / ports-and-adapters via typeclasses + cats-effect
- **(a) What it is:** Core domain logic is pure and technology-agnostic; *ports* are traits (often tagless-final algebras); *adapters* are interpreters that bind ports to skunk/doobie/http4s/redis4cats. Maps cleanly onto "functional core, imperative shell."
- **(b) When to apply:** whenever business logic is complex and stable but infrastructure may change; to keep the domain module free of Typelevel-library imports.
- **(c) Example/reference:** `ygunayer/scala3-tagless-final-hexagonal-monorepo` (Scala 3, cats-effect, tagless-final + hexagonal). Port `trait UserRepository[F[_]]`; adapters `SkunkUserRepository`, `InMemoryUserRepository`.
- **(d) Pitfalls:** "mess in the middle" — over-layering with ceremony the pattern never required; keep it to Adapter → driving port → domain → driven port → adapter.
- **(e) Source:** Cockburn ports-and-adapters; Gary Bernhardt "Boundaries."

#### 1.5 Onion / clean architecture idioms
- Package by concentric dependency direction: `domain` (innermost, no deps) ← `services`/`programs` (application) ← `http`/`clients`/`persistence` (outermost). Dependencies point inward only; the domain never imports infrastructure. In practice this is exactly the Volpe module layout (§4.1).

#### 1.6 Kleisli / Reader / cats-mtl for framework-free DI
- **(a) What it is:** `Kleisli[F, Env, A]` (aka `ReaderT`) threads a read-only environment through a computation; cats-mtl's `Ask`/`Local` abstracts it so you require `Ask[F, Config]` instead of stacking transformers.
- **(b) When to apply:** request-scoped context (trace IDs, auth principal), or a small global config passed everywhere. `http4s` itself uses this: `type HttpRoutes[F] = Kleisli[OptionT[F, *], Request[F], Response[F]]`.
- **(c) Example:** `val program: Kleisli[IO, Config, Unit] = for { db <- Kleisli.ask[IO, Config].map(_.db); _ <- Kleisli.liftF(run(db)) } yield ()`.
- **(d) Pitfalls:** overusing Reader for *all* DI leads to `Kleisli` noise and worse type inference than plain constructor injection; prefer constructor injection for wiring, reserve `Ask`/`Local` for genuinely ambient context.
- **(e) Source:** Typelevel Kleisli docs; cats-mtl docs.

#### 1.7 Effect-based DI: Resource composition + capability traits
- **Pattern:** every component exposes `def make[F[_]: ...](deps): Resource[F, Component[F]]` or `F[Component[F]]`; `Main` composes them. "Capability traits" (`GenUUID[F]`, `Time[F]`, `Logger[F]`) model ambient effects injected implicitly.
- **`Resource` DI example (idiomatic):**
```scala
object Dependencies:
  def make[F[_]: Async: Network]: Resource[F, Dependencies[F]] =
    for
      client <- EmberClientBuilder.default[F].build
      xa     <- transactorResource[F]
    yield Dependencies(HttpApi.make(services), emailService)
```
- **Alert:** avoid a single 200-line `for`-comprehension; group related resources into sub-objects (`AppResources`, `Services`, `Programs`, `HttpApi`) each with `.make`.

#### 1.8 Package/module boundary conventions
- **Algebra vs interpreter vs program:** algebras = abstract capability traits; interpreters/"live" = concrete implementations; programs = business logic composing algebras.
- **api/domain/infrastructure separation:** put pure models + newtypes in `domain`; capability traits in `effects`/`lib`; wiring objects in `modules`; HTTP in `http/routes` + `http/clients`.

---

### CATEGORY 2 — Abstract Types & Type-Level Design in Scala 3

#### 2.1 Path-dependent types; type members vs type parameters
- **Type parameters** (`trait Repo[F[_], A]`) when the caller must choose/vary the type freely and combine multiple instances. **Type members** (`trait Repo { type Entity; def get(id: Id): F[Entity] }`) when the abstract type is an implementation detail fixed per module instance and you want to hide it (path-dependent `repo.Entity`).
- **When:** use type members to seal module internals (e.g. an opaque `Session`/`Handle` tied to one module instance); use type parameters for reusable, composable abstractions.
- **Pitfall:** path-dependent types complicate cross-instance interop and inference; don't reach for them unless you specifically need the encapsulation.

#### 2.2 Opaque types for domain modeling (vs shapeless/refined/Iron)
- **(a) What it is:** `opaque type Logarithm = Double` — zero-overhead newtype; the equality `= Double` is known only inside the defining scope. Combine with extension methods and a companion `apply`/`from` smart constructor.
- **(b) When:** every domain scalar (`UserId`, `EmailAddress`, `Quantity`) to prevent argument-swap bugs; replaces Scala 2 value classes (which box on pattern-match/collections) and `@newtype` macro (unavailable in Scala 3).
- **(c) Example:**
```scala
object DomainObjects:
  opaque type CustomerId = Int
  object CustomerId:
    def apply(i: Int): CustomerId = i
  given CanEqual[CustomerId, CustomerId] = CanEqual.derived
```
For validation use **Iron**: `opaque type FirstName = String :| Not[Empty]` with `object FirstName extends RefinedTypeOps[...]`.
- **(d) Pitfalls:** opaque types are *not* full newtypes out of the box — you must hand-write `apply`/`value`/extension methods; Volpe drove the "improve opaque types" contributors discussion for this reason. Derive `CanEqual` to get multiversal-equality safety.
- **(e) Source:** Scala 3 book "Opaque Types"; Iron docs (antonkw); alvinalexander Cookbook 23.7.

#### 2.3 Higher-kinded types as the module abstraction boundary
- `F[_]` is the primary abstraction seam. **Constrain minimally** ("principle of least power"): require `Applicative` not `Monad` if you only need independent effects — the signature then *documents* that no effect depends on a prior result's value (De Goes's parametric-reasoning benefit).
- **Alert:** do not add `F[_]` "just in case." If the app will only ever run on `IO`, writing `IO`-specific code is legitimate and clearer (cats-mtl typed-errors skill guidance: *"F[_] is optional"*).

#### 2.4 given/using composition idioms (Scala 3)
- Use `given`/`using` for typeclass instances and capability traits; **context bounds** `[F[_]: Monad: Logger]` for concise requirements; **typeclass derivation** (`derives`) for `Eq`/`Show`/circe codecs; **extension methods** to attach syntax without wrappers.
- **Example:** `def program[F[_]: Monad](using items: Items[F], log: Logger[F]): F[Unit]`.
- **Pitfall:** excessive implicit/`given` resolution creates slow compiles and opaque error messages; keep given scopes shallow, prefer explicit params for business algebras, implicit only for lawful typeclasses/capabilities.

#### 2.5 Union & intersection types for capabilities / error channels
- **Union `A | B`** for error channels without Coproduct/nested `Either`: `def foo: F[DuplicateUser | UserNotFound | Unit]`; pattern-match at the boundary. Gabriel Volpe, "Scala 3: Error handling in FP land" (gvolpe.com/blog/error-handling-scala3/): *"Union types are the perfect feature to model error types,"* replacing `type Err = Either[Either[DuplicateStory, UserNotFound], Unit]`.
- **Intersection `A & B`** to combine capabilities: `def h(x: Namable & Randomable)`; covariant `C[A & B] <: C[A] & C[B]`.
- **Alert:** union-type exhaustiveness is *not* enforced as strictly as sealed ADTs; enable `-Wnon-exhaustive-match`. For a closed, stable error set prefer a sealed `enum`; use unions for ad-hoc/compositional error sets.

#### 2.6 Match types & compile-time computation
- **(a) What it is:** type-level functions (`type Elem[X] = X match { case String => Char; case Array[t] => t }`) and `inline`/`compiletime` ops for compile-time guarantees.
- **(b) When:** library-level API ergonomics (deriving return types), compile-time validation of literals (Iron uses inline + compiletime API). **Rarely needed in application code.**
- **(d) Pitfall:** match types degrade inference and error messages fast; keep them in library boundaries, not business modules.

#### 2.7 Phantom types & type-level state machines
- **(a) What it is:** a type parameter carrying no runtime value that encodes protocol state, so illegal call sequences fail to compile: `class Conn[S <: State]; def open: Conn[Open]; def send(c: Conn[Open]): Conn[Open]; def close(c: Conn[Open]): Conn[Closed]`.
- **(b) When:** builders that must reach a valid state before `.build`; resource protocols (must `open` before `send`); enforcing "call these in order."
- **(c) Reference:** classic type-state builder pattern; also runtime analog = cats-effect concurrent state machine (Ref + Deferred, §4-runtime).
- **(d) Pitfall:** verbose; for effectful/runtime state prefer `Ref`+`Deferred` state machines over pure phantom encodings.

---

### CATEGORY 3 — Category-Theory / Math-Driven Design via Cats

#### 3.1 Functor/Applicative/Monad laws as interface constraints
- Require the *weakest* lawful abstraction a module needs. Laws (identity, composition, associativity) are design constraints: a lawful `Semigroup`/`Monad` instance guarantees composability across module boundaries. Enforce with `cats-laws` (§4.3).

#### 3.2 Free structures for composing independent algebras
- `Free`/`FreeApplicative` + `EitherK` (Coproduct) + `InjectK` let independently-developed modules be combined into one program and interpreted together. Use when modules are developed separately and you want one interpretation point. (See §1.2.)

#### 3.3 Comonad-based architectures
- **(a) What it is:** `Comonad` (`extract`/`coflatMap`) models context-dependent reads; `Cokleisli[F, A, B]` = `F[A] => B`. **(b) When:** streaming/windowed computations, spreadsheet-like dataflow, UIs — niche. **(d) Alert:** rarely warranted in typical services; document as advanced/optional.

#### 3.4 Semigroup / Monoid composition
- **(a) What it is:** `combine`/`|+|` and `empty` to merge configs, aggregate results, and compose modules. `Kleisli[F, A, B]` has a `Monoid` when `F[B]` does; `HttpRoutes` combine via `SemigroupK`'s `<+>`/`combineK`.
- **(b) When:** merging layered configuration, fold-aggregating validation results (`NonEmptyChain` with `Validated`), combining routes.
- **(c) Example:** `val allRoutes = itemRoutes <+> brandRoutes <+> orderRoutes` (tapir/http4s route composition relies on `SemigroupK[Kleisli]`; `type HttpRoutes[F] = Kleisli[OptionT[F,*], Request[F], Response[F]]`, tried in order until one returns non-`None`).

#### 3.5 Profunctor / contravariant functors at boundaries
- **(a) What it is:** `Profunctor` (`dimap`), `Contravariant` (`contramap`) adapt inputs/outputs at a boundary without changing the core. tapir codecs/`Schema` and circe `Encoder`/`Decoder` are contravariant/covariant boundary encoders; use `contramap`/`map` to derive one codec from another.
- **(b) When:** adapt a domain type to a wire type at the edge; keep the domain clean.
- **(c) Example:** `Encoder[Money] = Encoder[BigDecimal].contramap(_.amount)`.

#### 3.6 Arrow / Category / Compose for pipelines
- **(a) What it is:** `cats.arrow.{Arrow, Compose, Category}` formalize composable pipelines. `Kleisli[F, A, B]` is an `Arrow` when `F` is a `Monad`; compose effectful stages tip-to-tail.
- **(b) When:** building processing pipelines from small effectful stages that compose cleanly (`andThen`/`compose`/`>>>`), splitting/fanning inputs (`***`, `&&&`).
- **(c) Example:**
```scala
val pipeline: Kleisli[IO, Unit, Boolean] =
  Kleisli(getFromDb) andThen process andThen writeToDb
// fan-out with Arrow:
val headPlusLast = (headK &&& lastK) >>> Arrow[Kleisli[Option, *, *]].lift((_ + _).tupled)
```
- **(d) Pitfall:** for genuinely streaming/backpressured pipelines use fs2 `Stream`/`Pipe` (`Pipe[F,A,B] = Stream[F,A] => Stream[F,B]`) rather than raw Kleisli arrows.
- **(e) Source:** Typelevel Arrow & Kleisli docs; eed3si9n "herding cats."

#### 3.7 Optics (Monocle) across module boundaries
- **(a) What it is:** `Lens` (product focus), `Prism` (sum focus), `Optional`, `Iso`, `Traversal` — composable, purely-functional getters/setters for immutable nested data.
- **(b) When:** update deeply nested domain state without leaking internal structure across modules; modify one branch of an ADT (`Prism`); `modifyF` to run effectful updates inside a structure.
- **(c) Example (Scala 3 `Focus`):** `Focus[User](_.address.street.name).modify(_.capitalize)(user)`; `GenLens`, `composePrism`.
- **(d) Pitfall:** optics that "break the contract" (e.g. filtered traversals) violate composition laws (`modify a` then `modify b` ≠ `modify (a∘b)`); keep optics lawful. Don't use optics where a simple `copy` suffices.
- **(e) Source:** Monocle docs; Rock the JVM "Lenses, Prisms and Optics."

#### 3.8 ADTs for domain boundaries; sum vs product guidance
- **enum/sealed** for sums (choice: `enum PaymentError { case Declined; case Timeout }`), **case class** for products (records). "Make illegal states unrepresentable": prefer precise sums over boolean flags/`Option` soup. Sealed sums give exhaustiveness checking at boundaries.

---

### CATEGORY 4 — Practical Project Structuring & Tooling

#### 4.1 sbt/mill multi-module structure mirroring architecture
- **Rule:** module boundaries in the build mirror architecture boundaries; `dependsOn` encodes allowed dependency direction (compiler-enforced). Single `build.sbt`, `aggregate` for build-all, `dependsOn` for code deps.
- **Canonical layout (Volpe `trading`, Scala 3):** `modules/{domain, lib, core, feed, alerts, forecasts, processor, snapshots, ws-server, ws-client, tracing, it}`. Per the `gvolpe/trading` README: `domain` = *"Commands, events, state, and all business-related data modeling"*; `lib` = *"Capability traits such as Logger, Time, GenUUID... library abstractions such as Consumer and Producer, which abstract over different implementations such as Kafka and Pulsar"*; `core` = *"Core functionality that needs to be shared across different modules such as snapshots, AppTopic, and TradeEngine."*
- **`pfps-shopping-cart` (Scala 3, single core module) package layout under `modules/core/src/main/scala/shop/`:** `config/` (Ciris `data.scala`/`loader.scala`), `domain/` (newtypes + refined models), `services/` (algebras + live interpreters), `programs/` (Checkout business logic), `http/routes/` + `http/clients/`, `effects/` (capability traits `GenUUID`, `Time`, `Background`), `modules/` (wiring objects), `ext/` (orphan instances), `resources.scala` (`AppResources`), `Main.scala`.
- **Alert:** put the pure `domain` module at the bottom of the graph with no Typelevel-library deps so it compiles fast and stays portable. DevInsideYou's "Diamond Architecture" (NOT Volpe's) specifically targets build-graph parallelism by splitting the `core` choke-point module.

#### 4.2 http4s + tapir organization
- **Separate:** (1) tapir `Endpoint` descriptions (pure I/O contract, in one place — often companion objects), (2) server logic (`serverLogic` binding `I => F[Either[E,O]]`), (3) business logic (algebras/programs). Interpret endpoints to `HttpRoutes[F]` via `Http4sServerInterpreter`.
- **Compose routes** with `<+>`/`combineK` (SemigroupK on `Kleisli`); routes are matched in order.
- **Decision rule (ClearScore/dev.to):** use tapir for large REST APIs (dedup paths, free OpenAPI/Swagger, type-safe); use raw http4s DSL for a handful of endpoints. tapir-managed and hand-written http4s routes can co-exist.
- **Alert:** keep server logic referentially transparent — the `???` logic plug-ins should call programs/algebras, never contain wiring.

#### 4.3 Testing module boundaries
- **Effectful unit tests:** `munit-cats-effect` (`CatsEffectSuite`, tests return `IO`, run in parallel) or **weaver-test**. Per the `typelevel/weaver-test` README, Weaver *"was built for integration/end-to-end tests... Tests are run concurrently by default... Logs are only displayed when a test fails... 'beforeAll' and 'afterAll' logic is represented using a cats.effect.Resource"* — making it the best fit for integration/e2e with shared resources (HTTP clients, connection pools).
- **Never call `unsafeRunSync` in tests**; return `IO` like production code.
- **Property tests:** scalacheck; effectful properties via `scalacheck-effect` (`PropF.forAllF`).
- **Law testing as architecture enforcement:** `cats-laws` + `discipline-munit`: `checkAll("Tree.Functor", FunctorTests[Tree].functor[Int,Int,String])`. Verify every custom typeclass instance at a module boundary is lawful (needs `Eq` + `Arbitrary`). This makes typeclass compliance a CI gate.
- **Time:** use cats-effect `TestControl.executeEmbed` to test time-dependent logic instantly instead of real sleeps.

#### 4.4 Error-handling architecture
- **Typed errors via ADTs + `MonadError`/`ApplicativeError`:** model domain errors as sealed `enum`; raise with `raiseError`, handle with `handleErrorWith`/`recoverWith`.
- **Prefer cats-mtl `Raise`/`Handle` over `EitherT` stacks (cats-mtl ≥1.6, Scala 3):**
```scala
def parse[F[_]](s: String)(using Raise[F, ParseError], Monad[F]): F[Result] = ???
val program: IO[Unit] = Handle.allow[ParseError]:
  for { x <- parse[IO](a); y <- parse[IO](b) } yield ()
.rescue: case ParseError.Other(m) => IO.println(s"error: $m")
```
This stays in the mono-functor error channel — preserving cats-effect concurrency/resource semantics — and infers far better than `EitherT`.
- **Pitfalls of `EitherT[IO, E, A]`:** double error channel (the `Left` *and* a failed `IO`), double short-circuiting, painful inference when stacking transformers, and it can break cancellation/concurrency reasoning. Pure functions returning `Either[E,A]` are fine at API boundaries (parsing/validation); avoid `EitherT` over `IO`.
- **Alert:** `recoverWith`-style adaptation can *lose the original error* if not logged — always include failure-path tests, not just happy paths.

#### 4.5 DI without frameworks — Resource composition, wire-by-hand
- **Canonical `Main` wiring (Volpe pattern):**
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
Everything is threaded explicitly through the `for`-comprehension — no DI framework, no macros, pure constructor injection wrapped in `Resource`/`F`. `AppResources` acquires/safely releases external resources (http4s `Client`, skunk `Session` pool, redis4cats `RedisCommands`) as one `Resource`, consumed with `.use`.
- **Naming conventions (Volpe PFP §2.1.1):** algebras = plural domain nouns, **no suffix** (`Items`, `Brands`, `Orders`) — or a consistent `Alg`/`Service` suffix (Scala Steward uses `Alg`); smart constructors named **`.make`** returning `F[Component]` or `Resource[F, Component]` (never a public constructor); interpreters are the "live" implementations (commonly `object Live`/`class LiveXxx`).
- **Optional tooling:** MacWire (compile-time, zero runtime, `autowire` returns `Resource[IO,_]`), SoftwareMill Bootzooka's `autowire[Dependencies](...)`, or cedi — use only to cut boilerplate on large graphs.

#### 4.6 Anti-patterns & alerts (large Typelevel codebases)
- **Tagless-final overuse / premature indirection (De Goes):** *"only library authors have a compelling argument for effect type indirection. In order to maximize market share... they need to support all major effect types... this is completely inapplicable to the closed source applications that make up the majority of Scala software development."* The refactor cost from one effect type to another is a one-time cost driven by *semantic* differences, while indirection cost is paid forever.
- **`F[_]` proliferation without benefit:** if you never instantiate `F` to anything but `IO`, the polymorphism is dead weight and hides the concrete `IO` API (hundreds of useful ops), degrades inference, and worsens error messages.
- **"Untestable effects" myth (De Goes, verbatim):** *"testability is not a property of tagless-final code... tagless-final programs are not inherently testable. In fact, they are testable only to the degree their tagless-final type classes are testable."* `Sync`/`Async`-based type classes capturing arbitrary side effects are inherently untestable — don't claim testability you don't have.
- **Excessive implicit/given complexity:** deep implicit chains → slow compiles, cryptic errors. Pass business algebras explicitly; reserve implicits for lawful typeclasses and capability traits.
- **`EitherT`/monad-transformer stacks:** avoid stacking over `IO`; use cats-mtl or concrete `IO` + `Either` at edges.
- **God `for`-comprehension wiring:** split into `.make` sub-modules.
- **Overusing Reader/Kleisli for all DI:** prefer constructor injection; Kleisli only for ambient request context.
- **Leaking interpreters:** never expose the demo/insecure admin endpoint or internal interpreters publicly (Volpe README warns the didactic admin route "SHOULD NEVER BE MADE PUBLIC").

---

### CATEGORY 5 — Notable Open-Source Resources (real code & idioms)

- **Gabriel Volpe — *Practical FP in Scala* + `gvolpe/pfps-shopping-cart`** (Scala 3, second-edition branch): canonical tagless-final + Resource wiring; stack = cats-effect, fs2, http4s, skunk, redis4cats, refined, Ciris. Naming conventions, algebras/programs/interpreters. Also `gvolpe/trading` (*Functional Event-Driven Architecture*, Scala 3) for multi-module layout; and his blog `gvolpe.com` (error handling with union types in Scala 3, published 2022-02-08).
- **John De Goes — "The False Hope of Managing Effects with Tagless-Final in Scala"** (degoes.net/articles/tagless-horror): the authoritative anti-pattern/alert source; premature indirection, untestable effects, parametric-reasoning nuance.
- **Fabio Labella (SystemFw) — "Composable concurrency with Ref + Deferred"** (Scala Italy 2019, YouTube): concurrent state machines, shared state via regions-of-sharing (pass as arguments); foundational for cats-effect concurrency design. Companion: Inner Product "Concurrent state machines."
- **Rob Norris (tpolecat)** — skunk & doobie author; talks on functional Postgres access and tagless style.
- **Jakub Kozłowski (kubukoz) — "Flavors of shared state in Cats Effect"** (blog.kubukoz.com): `Ref` vs `IOLocal` vs `IOLocal[Ref]` guidance for module state.
- **Adam Warski / SoftwareMill — tapir docs + "Bootzooka 2022: cats-effect 3, autowire & tapir"**, "Free and tagless compared", "Cats Effect vs ZIO": endpoint/route organization, autowire DI, free-vs-tagless decision guidance.
- **Typelevel org docs:** cats (Kleisli, Arrow, law testing), cats-effect (testing, TestControl, Resource DI), cats-mtl ("Custom Error Types Using Cats Effect and MTL"), weaver-test, scalacheck-effect, discipline.
- **Monocle docs + Rock the JVM optics articles**; **Iron** (opaque-type refinement, antonkw blog).
- **DevInsideYou — "Diamond Architecture"** (YouTube playlist; `DevInsideYou/diamond-architecture`): build-graph-topology architecture for fast parallel compiles — *credit DevInsideYou, not Volpe*.

---

## Recommendations

**Stage 1 — Default skeleton (any new small/medium Typelevel project):**
1. Multi-module sbt build: `domain` (no Typelevel deps) → `services`/`core` → `http`/`app`. Encode direction with `dependsOn`.
2. Model every domain scalar as an opaque type (Iron for validated ones); model choices as `enum`, records as `case class`.
3. Write algebras as tagless-final traits **only where you need swappable interpreters**; otherwise write concrete `IO`. Keep algebras abstract (no `Monad` bound).
4. Wire by hand in `Main` with `Resource` + `.make` smart constructors grouped into `AppResources`/`Services`/`Programs`/`HttpApi`.
5. Errors: sealed `enum` + cats-mtl `Raise`/`Handle` or `MonadThrow`; never `EitherT` over `IO`.
6. Tests: `munit-cats-effect` or weaver; add `discipline` law tests for every custom typeclass instance.

**Stage 2 — Scale-up triggers:**
- Build too slow / `core` is a choke point → split modules (DevInsideYou Diamond idea); push persistence into an isolated module.
- Many independently-developed algebras needing one interpretation point → consider `Free` + `EitherK`/`InjectK`.
- Genuine multi-effect-backend requirement (library, or real ZIO+CE interop) → keep `F[_]` polymorphism; otherwise drop it.
- Large REST surface → adopt tapir (OpenAPI, dedup) over raw http4s DSL.
- Deep nested-state updates crossing modules → introduce Monocle optics (keep them lawful).

**Thresholds that change the recommendation:**
- If you never instantiate `F` beyond `IO` after 2+ months → **remove `F[_]`**, use concrete `IO`.
- If implicit-resolution compile times or error messages degrade → make business algebras explicit params.
- If an `EitherT` stack appears → refactor to cats-mtl or `IO` + `Either` at the edge.

## Caveats
- **"Diamond Architecture" is DevInsideYou's, not Gabriel Volpe's.** It targets build-graph parallelism/compile speed, not domain layering; do not conflate with onion/hexagonal.
- Some `pfps-shopping-cart` wiring snippets originate from the first edition (Scala 2, `master`); the second edition (Scala 3) keeps the identical hand-wiring pattern but renames `Algebras`→`Services` and swaps Blaze→Ember. Package names are corroborated by README, commit diffs, and the book ToC, but exact second-edition `LiveXxx` source lines were not independently fetched.
- De Goes writes from a ZIO-advocacy position; his tagless-final critique is technically sound but read the effect-indirection arguments knowing that context. The Typelevel community continues to use tagless-final productively with discipline.
- Match types, comonads, and phantom/type-state encodings are advanced and rarely needed in ordinary services — flagged as optional/situational, not defaults.
- This corpus reflects conventions as of 2026; library specifics (cats-mtl `Handle` syntax, Iron, Monocle `Focus`) are Scala 3-era and may not apply to Scala 2 codebases. Version-specific dependency coordinates (e.g., `munit-cats-effect-3`) shift over time; verify current versions before use.