# shapeless: Generic Programming for Scala (shapeless 3)

> Source: https://github.com/typelevel/shapeless-3 (README.md, https://raw.githubusercontent.com/typelevel/shapeless-3/main/README.md)
> Collected: 2026-09-06
> Published: Unknown

**shapeless** is described as "a type class and dependent type based generic programming library for Scala." The project targets Scala 3, with shapeless 2 maintained in a separate repository. It represents a collaboration between the Typelevel community and Martin Odersky's EPFL LAMP group to integrate language-level support for generic programming into Scala 3.

## Key differences from shapeless 2

- **Performance**: "Compile- and runtime performance are dramatically improved over shapeless 2"
- **Binary footprint**: significantly reduced code size in client applications
- **Scope**: extends beyond shapeless 2's capabilities by supporting additional type kinds beyond basic ADTs

## Key modules and versions

The current stable version is **3.4.0**, available via Maven Central:
```
libraryDependencies ++= Seq("org.typelevel" %% "shapeless3-deriving" % "3.4.0")
```

The library supports Scala 3.0.0+ for JVM and Scala.js 1.5.0+.

## Supported kind hierarchy

shapeless 3 provides type class derivation across multiple kinds:

| Kind | Examples |
|------|----------|
| `*` | Monoid, Eq, Show |
| `* -> *` | Functor, Traverse, Monad |
| `(* -> *) -> *` | FunctorK (HFunctor) |
| `* -> * -> *` | Bifunctor |

(Kind `*` corresponds to shapeless 2's `Generic`; kind `* -> *` corresponds to shapeless 2's `Generic1`.)

## Core abstractions

- **`K0.ProductInstances`**: for deriving type classes over product types
- **`K0.ProductGeneric`**: generic representation of ADTs
- **Labelling and Annotations**: metadata support for fields and constructors

## Example: Monoid derivation

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

## Community

shapeless is part of the Typelevel ecosystem, hosted on GitHub under the Apache License v2, with discussion centered on the Typelevel Discord community.
