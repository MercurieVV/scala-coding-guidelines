# Scala 3 Type-Level Design Features

> Sources: Local research corpus, 2026-09-06
> Raw: [scala-coding-practices-research.md](../../raw/scala-typelevel-fp/scala-coding-practices-research.md)
> Updated: 2026-09-06

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

## given/using composition

Use `given`/`using` for typeclass instances and capability traits; context bounds (`[F[_]: Monad: Logger]`) for concise requirements; `derives` for typeclass derivation (`Eq`/`Show`/circe codecs); extension methods to attach syntax without wrappers. Example: `def program[F[_]: Monad](using items: Items[F], log: Logger[F]): F[Unit]`. Excessive implicit/`given` resolution creates slow compiles and opaque error messages — keep given scopes shallow, prefer explicit params for business algebras, implicit only for lawful typeclasses/capabilities.

## Union & intersection types

**Union `A | B`** models error channels without Coproduct/nested `Either`: `def foo: F[DuplicateUser | UserNotFound | Unit]`, pattern-matched at the boundary. Gabriel Volpe, "Scala 3: Error handling in FP land" (gvolpe.com/blog/error-handling-scala3/, published 2022-02-08): "Union types are the perfect feature to model error types," replacing `type Err = Either[Either[DuplicateStory, UserNotFound], Unit]`.

**Intersection `A & B`** combines capabilities: `def h(x: Namable & Randomable)`; covariant `C[A & B] <: C[A] & C[B]`.

Union-type exhaustiveness is not enforced as strictly as sealed ADTs — enable `-Wnon-exhaustive-match`. For a closed, stable error set prefer a sealed `enum`; use unions for ad-hoc/compositional error sets.

## Match types & compile-time computation

Type-level functions (`type Elem[X] = X match { case String => Char; case Array[t] => t }`) and `inline`/`compiletime` ops give compile-time guarantees. Useful for library-level API ergonomics (deriving return types) and compile-time literal validation (Iron uses inline + compiletime API) — rarely needed in application code. Match types degrade inference and error messages fast; keep them in library boundaries, not business modules.

## Phantom types & type-level state machines

A type parameter carrying no runtime value encodes protocol state so illegal call sequences fail to compile: `class Conn[S <: State]; def open: Conn[Open]; def send(c: Conn[Open]): Conn[Open]; def close(c: Conn[Open]): Conn[Closed]`. Useful for builders that must reach a valid state before `.build`, or resource protocols enforcing call order. Verbose — for effectful/runtime state prefer `Ref`+`Deferred` state machines (see [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)) over pure phantom encodings.

## ADTs: sum vs product

`enum`/sealed for sums (`enum PaymentError { case Declined; case Timeout }`), `case class` for products. "Make illegal states unrepresentable": prefer precise sums over boolean flags/`Option` soup. Sealed sums give exhaustiveness checking at boundaries.

## See Also

- [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md)
- [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)
- [Project Structure & Testing](project-structure-and-testing.md)
