# Refactoring Kleisli to Arrow: When and How

> Sources: Cats docs, "Arrow" (Unknown date); typelevel/cats source (Unknown date); Wikipedia, "Kleisli category" (Unknown date); MercurieVV/scala-purrism docs (Unknown date); Scala 3 Reference, "Rules for Operators" (Unknown date); Cats docs, "FunctionK" (Unknown date); Oron Port, SIP-33 (2017-02-07); Arthur C. Codex (reintech.io), 2023-09-16; Claude Code synthesis, 2026-09-13
> Raw: [Cats Docs: Arrow Typeclass](../../raw/scala-typelevel-fp/cats-arrow-typeclass-docs.md); [Cats Source: Kleisli ArrowChoice Instances](../../raw/scala-typelevel-fp/cats-kleisli-arrowchoice-source.md); [Kleisli Category (Wikipedia)](../../raw/scala-typelevel-fp/kleisli-category-wikipedia.md); [scala-purrism: Kleisli → Arrow rewrite catalogue](../../raw/scala-typelevel-fp/scala-purrism-arrow-patterns.md); [scala-purrism: Engineering Rules](../../raw/scala-typelevel-fp/scala-purrism-engineering-rules.md); [scala-purrism: README](../../raw/scala-typelevel-fp/scala-purrism-readme.md); [Scala 3 Reference: Rules for Operators](../../raw/scala-typelevel-fp/scala3-rules-for-operators-infix-types.md); [Cats Docs: FunctionK](../../raw/scala-typelevel-fp/cats-functionk-natural-transformation-docs.md); [SIP-33: Priority-based infix type precedence](../../raw/scala-typelevel-fp/sip33-priority-based-infix-type-precedence.md); [A Guide to Scala's Infix Types (reintech.io)](../../raw/scala-typelevel-fp/2023-09-16-reintech-guide-to-infix-types-in-scala.md); [Suggestion: Arrow-String Infix Type Aliases](../../raw/scala-typelevel-fp/suggestion-arrow-string-infix-type-aliases.md)
> Updated: 2026-09-13

## Overview

`Kleisli[F, A, B]` (a wrapper over `A => F[B]`) and `cats.arrow.Arrow` are not competing tools — `Kleisli` is one of several types (alongside plain `Function1` and `Cokleisli`) that *has* an `Arrow` instance. The guideline in this article is: once you're **composing** two or more existing Kleislis (or any two arrows), reach for `Arrow`'s combinators (`>>>`, `.map`, `&&&`, `|||`) instead of hand-threading `.run`/`flatMap`/`for` — especially in code that's already polymorphic over the arrow type. Kleisli itself remains the right tool for *introducing* an effectful, data-in/data-out function in the first place; the refactor is about how you *combine* Kleislis you already have, not about avoiding Kleisli altogether.

## Category-theory background

A **Kleisli category** is the category naturally associated with a monad `T`: same objects as the base category, but a morphism `X → Y` is a base-category morphism `X → TY`, composition uses the monad's `μ` (join) to flatten the nested `T`, and identity is the monad's `η` (pure) (Wikipedia, "Kleisli category"). `cats.data.Kleisli[F, A, B]` is exactly this construction reified as a Scala type: it *is* the Kleisli category's hom-set for the monad `F`, one `A`/`B` pair at a time.

`Arrow` is the more general abstraction: "a type class for modeling composable relationships between two types" (cats docs). An arrow `F[A, B]` represents a computation from `A` to `B` in some context, the same way a functor `F[A]` represents a value in some context. Its four core operations:

- **`lift`**: `Arrow[F].lift(ab: A => B): F[A, B]` — embed an ordinary function.
- **`compose`/`>>>`**: `fab: F[A, B]`, `fbc: F[B, C]` → `fac: F[A, C]` (`fab >>> fbc`, or `Arrow[F].compose(fbc, fab)`).
- **`split`/`***`**: `fab: F[A, B]`, `fcd: F[C, D]` → `F[(A, C), (B, D)]` — process two separate inputs with two separate arrows.
- **`first`/`second`**: `fab: F[A, B]` → `F[(A, C), (B, C)]` (or the symmetric `(C, A) → (C, B)`) — transform one half of a pair, pass the other through untouched.

`Function1` has an `Arrow` instance where `>>>` is just an alias for `andThen`. `Kleisli` has one "as long as `F` is a monad" (cats docs) — precisely because `Kleisli`, cats source confirms, only picks up `Compose`/`Category` under `FlatMap[F]`/`Monad[F]`, `Strong` (`first`/`second`) under plain `Functor[F]`, and the full `ArrowChoice` (which extends `Arrow` with `Either`-branching) under `Monad[F]`. `split`'s definition — `F.flatMap(f.run(a))(b => F.map(g.run(c))(d => (b, d)))` — is exactly the same left-to-right effect sequencing a hand-written `for`-comprehension over two Kleislis produces; `Arrow` composition is not a different runtime behavior, it is the same behavior with the threading written once, generically, in the typeclass instance rather than by hand in every call site.

## Cats' own worked comparison

From the cats docs' `Arrow` page: given `headK = Kleisli((_: List[Int]).headOption)` and `lastK = Kleisli((_: List[Int]).lastOption)`, you get their sum via
```scala
val headPlusLast = combine(headK, lastK) >>> Arrow[Kleisli[Option, *, *]].lift(((_: Int) + (_: Int)).tupled)
```
where `combine` is written once, generically, against `Arrow[F]` — and works unchanged whether `F` is `Kleisli[Option, *, *]` or plain `Function1`:
```scala
def combine[F[_, _]: Arrow, A, B, C](fab: F[A, B], fac: F[A, C]): F[A, (B, C)] =
  Arrow[F].lift((a: A) => (a, a)) >>> (fab *** fac)
```
This is the concrete shape of "prefer Arrow especially in abstract places": a function written against `F[_, _]: Arrow` works over `Kleisli`, `Function1`, `Cokleisli`, or any future arrow-shaped type without modification, whereas the same function written against `Kleisli[F, A, B]` directly is locked to that one type constructor.

## Refactoring patterns (Kleisli → Arrow)

These three shapes are drawn from `scala-purrism`'s `PreferArrow` rewrite catalogue, which specifies and mechanically verifies them (fixture-tested, effect-ordering proven — see below).

### Pattern A — linear chain → `>>>`/`andThen`

Before (hand-threaded, either desugared or `for`):
```scala
def enrich(id: UserId): F[Report] =
  loadUser.run(id).flatMap(user => loadOrders.run(user).flatMap(orders => summarise.run(orders)))

def enrichFor(id: UserId): F[Report] =
  for {
    user   <- loadUser.run(id)
    orders <- loadOrders.run(user)
    report <- summarise.run(orders)
  } yield report
```
After:
```scala
def enrich: Kleisli[F, UserId, Report] =
  loadUser.andThen(loadOrders).andThen(summarise)
```
Applies for any chain length ≥2; each intermediate binding must be used exactly once, as the sole argument of the next step — any other use of it in the body means it isn't a pure pipe and the rewrite doesn't apply.

### Pattern B — `map` after `run`

Before:
```scala
def name(id: UserId): F[String] =
  loadUser.run(id).map(_.name)
```
After:
```scala
def name: Kleisli[F, UserId, String] =
  loadUser.map(_.name)
```
This is an equality, not an approximation: `Kleisli.map` is defined as `Kleisli(a => F.map(run(a))(f))`. The mapping function must not capture the outer input parameter, or the rewrite silently drops a capture.

### Pattern C — fan-out → `&&&`

Before:
```scala
def profile(id: UserId): F[(User, Settings)] =
  for {
    user     <- loadUser.run(id)
    settings <- loadSettings.run(id)
  } yield (user, settings)
```
After:
```scala
def profile: Kleisli[F, UserId, (User, Settings)] =
  loadUser &&& loadSettings
```
Requires `Monad[F]`, not merely `FlatMap[F]` — `&&&` (`Arrow.merge`) needs `split`, and `Kleisli`'s `ArrowChoice`/`split` instance is only available under `Monad[F]`. Tuple order must match generator order; `yield (settings, user)` is `(loadUser &&& loadSettings).map(_.swap)`, not `&&&` directly.

### Effect ordering is preserved, not just readability

`&&&`'s default implementation is `andThen(lift(x => (x, x)), split(f, g))`, and `Kleisli`'s `split` is `Kleisli { case (a, c) => F.flatMap(f.run(a))(b => F.map(g.run(c))(d => (b, d))) }`. Substituting, `(k1 &&& k2).run(x)` reduces to `k1.run(x).flatMap(b => k2.run(x).map(c => (b, c)))` — **the exact desugaring of the `for`-comprehension in pattern C**, with `k1` strictly sequenced before `k2`. So refactoring to `&&&`:

- Preserves left-to-right ordering, exactly as written.
- Preserves short-circuiting: if `F` is `Either`-like / `OptionT` / `MonadError`, a failure in `k1` still prevents `k2` from running (it's a `flatMap` on `F`, nothing is made eager).
- Is **not** `Parallel`/`parTupled` — `&&&` never runs `k1`/`k2` concurrently, and `Kleisli`'s `CommutativeArrow` instance (needing a `CommutativeMonad[F]`) is a claim about the effect commuting, never license for `Arrow`'s own operations to reorder anything.

## When Kleisli composition is fine as-is (don't force Arrow)

Not every Kleisli chain should become point-free. Two shapes are deliberately *not* automated, and the reasoning generalizes past any specific tool:

- **A later step needs both the original input and an intermediate result** (would need `(Kleisli.ask &&& k1).andThen(k2)`). The `&&&`/`.local`/`.first` alternatives each produce a genuinely different shape with no single "more readable" winner — a hand-threaded `for` that names the input and the intermediate explicitly is often clearer than any point-free encoding. Reach for it directly instead of forcing an arrow-combinator form.
- **`Either`/`Option` branching** is the one shape that justifies `ArrowChoice` (`|||`, `choice`, `.left`, `.right`) at all, but only in the narrow case of two arms, each a lone Kleisli application of the scrutinee (`k.run(x).flatMap { case Left(l) => kl.run(l); case Right(r) => kr.run(r) } → k >>> (kl ||| kr)`); a guard, a third case, a non-Kleisli arm, or an `Option`/`Boolean` scrutinee falls outside it and should stay as ordinary pattern matching.

And two things this guideline explicitly does **not** ask for:

- **Don't swap `Function1.andThen`/`.compose` for `>>>`/`<<<` cosmetically.** `f andThen g` is already point-free and readable; `>>>` buys no compositional power there, it's a pure style substitution with a large blast radius (fires on every `Function1` in a codebase) for zero semantic gain. That's a scalafmt/style-guide concern, not a refactor.
- **Don't blanket-generalize every `Kleisli[F, A, B]`-typed signature to an abstract `Arrow[=>:]` type parameter.** A codebase that deliberately chose `Kleisli` for a given signature has a reason to; over-generalizing every occurrence needs whole-program knowledge of intent that no local rule (or reviewer skimming one file) actually has. Generalize a *specific* signature to `Arrow`/`Category`/`Compose` when you're writing something that's genuinely meant to work over more than one arrow-shaped type (as cats' own `combine` example does) — not as a blanket policy applied file by file.

## Automate it: the `scala-purrism` scalafix rules

[`scala-purrism`](https://github.com/MercurieVV/scala-purrism) ships two Scalafix semantic rules under its `TypelevelPurrism` umbrella that encode this exact refactor:

- **`PreferKleisli`** — introduces `Kleisli`: turns an effectful data-in/data-out method (`def load(id: Long): F[String]`) into a `Kleisli[F, Long, String]` value, and re-splits direct call sites.
- **`PreferArrow`** — the composition half: rewrites hand-threaded `Kleisli` bodies into Arrow composition (`>>>`, `.map`, `&&&`), implementing patterns A/B/C above via a small "arrow-IR" compiler (parse a monadic body into an IR, normalize to a fixpoint, gate on a readability budget, render the combinators) rather than syntactic pattern matching alone.

Install (sbt):
```scala
// project/plugins.sbt
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.7")

// build.sbt
ThisBuild / scalacOptions += "-Ysemanticdb"
ThisBuild / scalafixDependencies += "io.github.mercurievv" %% "scala-purrism-scalafix" % "0.9.0"
```
```hocon
// .scalafix.conf
rules = [ TypelevelPurrism ]
```
```bash
sbt scalafix
```
(Mill, Scala CLI, and the bare Scalafix CLI are also supported — see the raw source for exact snippets.)

**Configuration and limits worth knowing before turning it on:**

- `PreferArrow.aggressive = false` (default) keeps rewrites conservative — it only fires where the point-free form is at least as readable as the source. Setting it `true` additionally lifts plain `F`-returning calls into ad hoc Kleislis so independent `for` generators can still fan out via `&&&`, retains an unused-but-referenced input via `Kleisli.ask`, and handles discarded generators (`_ <- log(...)`) via `*>`/`flatTap` rather than tupling-and-discarding them. Effect ordering and short-circuiting are unchanged in aggressive mode too (proven the same way as the conservative patterns); the output is simply busier, which is why it's opt-in.
- `PreferArrow.reportSkips = false` controls whether declined rewrites get a diagnostic explaining why.
- The rule declines outright — never guesses — on unsafe public-API signature changes, cross-file call sites it can't see (a known structural gap: a Scala 3 classfile carries its signature as TASTy, which the classfile→SemanticDB converter can't decode, so a callee declared in another file is invisible unless its own `.semanticdb` payload is folded in separately), missing Cats evidence (e.g. only `FlatMap[F]` in scope where `&&&` needs `Monad[F]`), or any shape that would change behavior.
- The engineering rule behind all of this: **`PreferKleisli` → recompile → `PreferArrow` is a pipeline, not one invocation listing both rules** — a rule that consumes another rule's signature-changing output needs a fresh SemanticDB pass in between, because the payload describes the code as it was compiled *before* the first rewrite.
- Two shapes were deliberately dropped rather than automated, for the same reasons given in "When Kleisli composition is fine as-is" above: cosmetic `Function1` `andThen`→`>>>` swaps, and blanket `Kleisli[F, A, B]` → abstract-`Arrow` signature generalization (this one needs whole-program knowledge of intent that a single-file rule structurally cannot have).

## Suggestion: symbolic infix aliases for arrow-shaped types (`-->`, `==>`, `~~>`)

Scala 3's `infix` feature makes it possible to give an arrow-shaped type constructor a short symbolic alias and write it infix at use sites instead of always spelling out `SomeArrow[A, B]`:

```scala
type -->[A, B] = Kleisli[F, A, B]        // an effectful arrow A --> B
type ~~>[F[_], G[_]] = FunctionK[F, G]   // a natural transformation F ~~> G

def validate: User --> ValidatedUser     // reads as "User to ValidatedUser"
```

This is not a novel hack — it generalizes a pattern cats already ships. Cats provides `type ~>[F[_], G[_]] = FunctionK[F, G]`, and its own docs frame the motivation directly: "being able to use `~>` as an alias for `FunctionK` parallels being able to use `=>` as an alias for `Function1`." Symbolic type names don't even need the `infix` modifier in Scala 3 — "infix operations involving symbolic operators are always allowed" (Scala 3 Reference, "Rules for Operators") — only alphanumeric infix types/methods need it (`infix type or[X, Y]`).

`-->` specifically is not a fresh proposal for this codebase's tooling: `scala-purrism`'s own `fix.arrow.KleisliType` discriminator already "dealiases through `-->`, `Flow`, fully-qualified and inferred types via SemanticDB" when recognizing Kleisli-shaped code, and its fixtures deliberately distinguish that alias from an unrelated type parameter of the same spelling (`ProgramArrows[-->[_, _]]`). Adopting `-->` (and `~~>`/`==>` for other arrow families) as a house convention just makes deliberate what the refactoring tool already treats as the canonical spelling.

**Caveats before adopting this broadly:**

- **Infix type precedence is not expression precedence.** SIP-33 documents that (pre-fix) infix *types* don't share term operators' precedence rules — mixing multiple different symbolic infix type operators in one unparenthesized type expression can parse surprisingly. A lone `A --> B` relationship is unaffected; avoid chaining distinct infix type operators without parentheses.
- **IDE/discoverability cost.** Symbolic infix type aliases get weaker go-to-definition/hover support than named types, per the reintech.io infix-types guide. Keep the alias set small and consistently meaningful (mirror cats: one alias, `~>`, for one concept) rather than inventing a new symbol per use case.
- **Guard against spelling collisions.** `scala-purrism`'s own fixtures needed explicit logic to avoid confusing the `-->` Kleisli alias with an unrelated type parameter of the same name — pick one canonical meaning per symbol per module and don't reuse it for a structurally different relationship.
- Reach for this only where the aliased type appears often enough, in signature position, to make the infix spelling a readability win (e.g. a pipeline-heavy module chaining several `-->`s) — not as a blanket replacement for every two-parameter type.

## See Also

- [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)
- [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md)
- [Droste Algebra/Coalgebra Type Taxonomy](droste-algebra-coalgebra-taxonomy.md)
- [Abstracting Over Abstract Types: Type Members vs. Type Parameters](abstract-types-vs-type-parameters.md)
