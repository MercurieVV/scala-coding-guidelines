# Scala 3 Type-Level Design Features

> Sources: Local research corpus, 2026-09-06; Daniel Beskin (Rock the JVM), 2023-12-06; Rúnar Bjarnason (via Edd Steel), 2016-03-21
> Raw: [scala-coding-practices-research.md](../../raw/scala-typelevel-fp/scala-coding-practices-research.md); [Scala 3: Type-Level Programming](../../raw/scala-typelevel-fp/2023-12-06-scala3-type-level-programming.md); [Applying Least Power in Scala](../../raw/scala-typelevel-fp/2016-03-21-applying-least-power-in-scala.md)
> Updated: 2026-09-07

## Overview

Scala 3 changes the mechanics of abstract type design: opaque types replace value classes/`@newtype`, `enum` replaces sealed-trait ADTs, `given/using` replaces `implicit`, and union types offer a lighter alternative to `Either`/Coproduct for error channels.

## Abstract type members vs type parameters

Use **type parameters** (`trait Repo[F[_], A]`) when the caller must choose/vary the type freely and combine multiple instances. Use **abstract type members** (`trait Repo { type Entity; def get(id: Id): F[Entity] }`) when the type is an implementation detail fixed per module instance and should be hidden behind a path-dependent type (`repo.Entity`) — e.g. sealing an opaque `Session`/`Handle` tied to one module instance. Path-dependent types complicate cross-instance interop and inference; don't reach for them without a specific encapsulation need.

## Opaque types for domain modeling

`opaque type Logarithm = Double` is a zero-overhead newtype — the equality `= Double` is known only inside the defining scope. Combine with extension methods and a companion `apply`/`from` smart constructor:

```scala
object DomainObjects:
  opaque type CustomerId = Int
  object CustomerId:
    def apply(i: Int): CustomerId = i
  given CanEqual[CustomerId, CustomerId] = CanEqual.derived
```

Use for every domain scalar (`UserId`, `EmailAddress`, `Quantity`) to prevent argument-swap bugs — replaces Scala 2 value classes (which box on pattern-match/collections) and the `@newtype` macro (unavailable in Scala 3). For validation, use **Iron**: `opaque type FirstName = String :| Not[Empty]` with `object FirstName extends RefinedTypeOps[...]`.

Opaque types are not full newtypes out of the box — `apply`/`value`/extension methods must be hand-written (Volpe drove the "improve opaque types" contributors discussion for this reason). Derive `CanEqual` for multiversal-equality safety.

## Higher-kinded types as the abstraction boundary

`F[_]` is the primary abstraction seam. Constrain minimally ("principle of least power"): require `Applicative` not `Monad` when only independent effects are needed — the signature then documents that no effect depends on a prior result's value (De Goes's parametric-reasoning benefit). Do not add `F[_]` "just in case" — if the app only ever runs on `IO`, writing `IO`-specific code is legitimate and clearer.

"Power" here means unconstrained capability, not usefulness (Rúnar Bjarnason, "Constraints Liberate, Liberties Constrain"): "the more a system *can* do, the less we can predict what it *will* do." A weaker typeclass bound, or a plain function passed instead of a multi-method interface, is *less powerful* by this definition and therefore preferable — it rules out entire classes of behavior a caller would otherwise have to check for by reading the implementation. See [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](provider-consumer-least-power.md).

## given/using composition

Use `given`/`using` for typeclass instances and capability traits; context bounds (`[F[_]: Monad: Logger]`) for concise requirements; `derives` for typeclass derivation (`Eq`/`Show`/circe codecs); extension methods to attach syntax without wrappers. Example: `def program[F[_]: Monad](using items: Items[F], log: Logger[F]): F[Unit]`. Excessive implicit/`given` resolution creates slow compiles and opaque error messages — keep given scopes shallow, prefer explicit params for business algebras, implicit only for lawful typeclasses/capabilities.

Plain `derives` + hand-written `Mirror.Of[A]` logic covers a one-off typeclass; **shapeless 3**'s `K0`/`K1` machinery factors out the product/coproduct traversal itself, worth reaching for once several typeclasses (Monoid, Show, Eq, ...) need generic derivation — see [Shapeless 3: Generic Type Class Derivation](shapeless3-generic-derivation.md).

## Union & intersection types

**Union `A | B`** models error channels without Coproduct/nested `Either`: `def foo: F[DuplicateUser | UserNotFound | Unit]`, pattern-matched at the boundary. Gabriel Volpe, "Scala 3: Error handling in FP land" (gvolpe.com/blog/error-handling-scala3/, published 2022-02-08): "Union types are the perfect feature to model error types," replacing `type Err = Either[Either[DuplicateStory, UserNotFound], Unit]`.

**Intersection `A & B`** combines capabilities: `def h(x: Namable & Randomable)`; covariant `C[A & B] <: C[A] & C[B]`.

Union-type exhaustiveness is not enforced as strictly as sealed ADTs — enable `-Wnon-exhaustive-match`. For a closed, stable error set prefer a sealed `enum`; use unions for ad-hoc/compositional error sets.

## Match types & compile-time computation

Type-level functions (`type Elem[X] = X match { case String => Char; case Array[t] => t }`) and `inline`/`compiletime` ops give compile-time guarantees. Useful for library-level API ergonomics (deriving return types) and compile-time literal validation (Iron uses inline + compiletime API) — rarely needed in application code. Match types degrade inference and error messages fast; keep them in library boundaries, not business modules.

The full toolbox for this style of metaprogramming (Rock the JVM, "Scala 3: Type-Level Programming", 2023-12-06): `inline def` for compile-time evaluation and specialization at call sites; `summonInline` for implicit resolution during inlining; Scala 3 tuple recursion (`*:`/`EmptyTuple` pattern matching) for compile-time iteration; `Mirror`-derived `MirroredElemTypes`/`MirroredElemLabels` for reflection-free access to a case class's field types/names; `erasedValue` to pattern-match on a type with no corresponding value; `transparent inline` to preserve precise inferred types through a chain of transformations; `scala.compiletime.ops` for type-level boolean/integer/equality computation; and the `error` function for custom compile-time error messages. A worked example (flat-JSON codecs with compile-time duplicate-field detection) combines all of these.

**Tradeoffs, from the same source:** compile-time safety and elimination of runtime/reflection cost are real, but the type-level "language" this builds is primitive and loosely typed (heavy tuple encoding), benign-looking edits can silently break type precision, IDE support and match-type-reduction error messages are sparse, and using it well requires a working model of the compiler's inlining/erasure passes. Treat this as a library-author's toolbox for a specific, well-bounded problem — not a default style for application code.

## Phantom types & type-level state machines

A type parameter carrying no runtime value encodes protocol state so illegal call sequences fail to compile: `class Conn[S <: State]; def open: Conn[Open]; def send(c: Conn[Open]): Conn[Open]; def close(c: Conn[Open]): Conn[Closed]`. Useful for builders that must reach a valid state before `.build`, or resource protocols enforcing call order. Verbose — for effectful/runtime state prefer `Ref`+`Deferred` state machines (see [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)) over pure phantom encodings.

## ADTs: sum vs product

`enum`/sealed for sums (`enum PaymentError { case Declined; case Timeout }`), `case class` for products. "Make illegal states unrepresentable": prefer precise sums over boolean flags/`Option` soup. Sealed sums give exhaustiveness checking at boundaries.

## See Also

- [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md)
- [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)
- [Shapeless 3: Generic Type Class Derivation](shapeless3-generic-derivation.md)
- [Project Structure & Testing](project-structure-and-testing.md)
- [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](provider-consumer-least-power.md)
