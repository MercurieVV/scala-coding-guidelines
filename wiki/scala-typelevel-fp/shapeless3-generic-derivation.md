# Shapeless 3: Generic Type Class Derivation

> Sources: typelevel/shapeless-3 README (Unknown date); Javier Martínez, 2025-04-30 (updated)
> Raw: [shapeless3-readme.md](../../raw/scala-typelevel-fp/shapeless3-readme.md); [How to Derive Type Class Instances With Shapeless 3](../../raw/scala-typelevel-fp/2025-04-30-deriving-type-class-instances-shapeless3.md)
> Updated: 2026-09-13

## Overview

Shapeless 3 is a Typelevel generic-programming library for Scala 3, a from-scratch successor to shapeless 2 built jointly with EPFL's LAMP group as part of bringing generic-programming support directly into the language. It derives type class instances for products (case classes, tuples) and coproducts (sealed traits, enums) mechanically, covering more type kinds than Scala 3's own built-in `derives`/`Mirror` alone easily reaches for higher-kinded type classes.

## Why it exists next to Scala 3's built-in derivation

Scala 3 ships `derives` + `scala.deriving.Mirror` for basic ADT derivation, but shapeless 3 extends coverage across a kind hierarchy that plain `Mirror` derivation doesn't organize for you:

| Kind | Examples |
|---|---|
| `*` | `Monoid`, `Eq`, `Show` |
| `* -> *` | `Functor`, `Traverse`, `Monad` |
| `(* -> *) -> *` | `FunctorK` (higher-kinded functors) |
| `* -> * -> *` | `Bifunctor` |

Compared to shapeless 2, compile- and runtime performance are "dramatically improved," and the binary footprint pulled into client applications is significantly smaller.

## Core abstractions

- **`K0.Generic`** — high-level entry point that determines whether a type is a product or a sum and routes to the matching derivation logic automatically.
- **`K0.ProductInstances`** — derivation primitives for product types: `construct` (build a value field-by-field), `map2`/`foldLeft`-style combinators, and `project` (select one field by index and transform it via a polymorphic function).
- **`K0.CoproductInstances`** — the sum-type counterpart; its `fold` applies the matching type class instance to whichever variant a value actually is.
- **Labelling and Annotations** — metadata support for field/constructor names and annotations, used e.g. to render field names in a derived `Show`.

## Example: deriving Monoid over a product

```scala
import shapeless3.deriving.*

trait Monoid[A]:
  def empty: A
  def combine(x: A, y: A): A
  extension (x: A) def |+| (y: A): A = combine(x, y)

object Monoid:
  given monoidGen[A](using inst: K0.ProductInstances[Monoid, A]): Monoid[A] with
    def empty: A =
      inst.construct([t] => (ma: Monoid[t]) => ma.empty)
    def combine(x: A, y: A): A =
      inst.map2(x, y)([t] => (mt: Monoid[t], t0: t, t1: t) => mt.combine(t0, t1))

case class ISB(i: Int, s: String, b: Boolean) derives Monoid
val a = ISB(23, "foo", true)
val b = ISB(13, "bar", false)
val c = a |+| b // == ISB(36, "foobar", true)
```

The instance is derived once via `K0.ProductInstances[Monoid, A]`; `construct` builds the empty value field-by-field, `map2` combines two values field-by-field — neither touches `ISB`'s fields by name, just by shape.

## Example shape: deriving Show over products and sums

For a `Show[A]` typeclass, the product side iterates field labels and uses `K0.ProductInstances.project` to stringify each value (e.g. producing `"Foo(x = 1, y = s, z = true)"`); the sum side uses `K0.CoproductInstances.fold` to dispatch to whichever variant's instance applies (e.g. `"Blue()"` for a no-field enum case). A single `derived` method coordinates both paths so a type can just write `derives Show` regardless of whether it's a product or a sum.

## Install

```
libraryDependencies ++= Seq("org.typelevel" %% "shapeless3-deriving" % "3.4.0")
```
Scala 3.0.0+ (JVM and Scala.js 1.5.0+).

## When to reach for it vs. plain `derives`

Plain Scala 3 `derives` + hand-written `Mirror.Of[A]` logic is enough for a single one-off typeclass derivation. Shapeless 3's `K0`/`K1` machinery earns its keep once you're deriving several typeclasses generically (Monoid, Show, Eq, ...) and want the product/coproduct traversal logic itself factored out and reused, rather than re-deriving `Mirror`-walking boilerplate per typeclass.

## See Also

- [Scala 3 Type-Level Features](scala3-type-level-features.md)
- [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)
- [Droste Algebra/Coalgebra Type Taxonomy](droste-algebra-coalgebra-taxonomy.md)
