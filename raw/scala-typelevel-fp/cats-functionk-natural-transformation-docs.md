# FunctionK

> Source: https://typelevel.org/cats/datatypes/functionk.html
> Collected: 2026-09-13
> Published: Unknown

Cats documentation, Data Types — FunctionK.

## Definition

"A `FunctionK` transforms values from one first-order-kinded type (a type that takes a single type parameter, such as `List` or `Option`) into another first-order-kinded type. This transformation is universal, meaning that a `FunctionK[List, Option]` will translate all `List[A]` values into an `Option[A]` value for all possible types of `A`."

## From ordinary functions to FunctionK

Ordinary method:

```scala
def first(l: List[Int]): Option[Int] = l.headOption
```

As a function value: `val first: List[Int] => Option[Int] = l => l.headOption`. `=>` "is really just some syntactic sugar for `Function1`":

```scala
val first: Function1[List[Int], Option[Int]] = l => l.headOption
```

`Function1` "is just a trait that looks something like this":

```scala
// we are calling this `MyFunction1` so we don't collide with the actual `Function1`
trait MyFunction1[A, B] {
  def apply(a: A): B
}
```

## Generalizing with higher kinds

Generic version: `def first[A](l: List[A]): Option[A] = l.headOption`. "We are looking for something like a type of `List[A] => Option[A] forAll A`, but this isn't valid scala syntax. `Function1` isn't quite the right fit, because its `apply` method doesn't take a generic type parameter."

"It turns out that we can represent our universal List to Option transformation with something that looks a bit like `Function1` but that adds a type parameter to the `apply` method and utilizes higher kinds":

```scala
trait MyFunctionK[F[_], G[_]] {
  def apply[A](fa: F[A]): G[A]
}
```

"Cats provides this type as `FunctionK` (we used `MyFunctionK` for our example type to avoid confusion)":

```scala
import cats.arrow.FunctionK

val first: FunctionK[List, Option] = new FunctionK[List, Option] {
  def apply[A](l: List[A]): Option[A] = l.headOption
}
```

## Syntactic sugar: the `~>` infix alias

"If the example above looks a bit too verbose for you, the kind-projector compiler plugin provides a more concise syntax." With kind-projector:

```scala
val first: FunctionK[List, Option] = λ[FunctionK[List, Option]](_.headOption)
```

"Cats also provides a `~>` type alias for `FunctionK`, so an even more concise version would be:

```scala
import cats.~>

val first: List ~> Option = λ[List ~> Option](_.headOption)
```

**Being able to use `~>` as an alias for `FunctionK` parallels being able to use `=>` as an alias for `Function1`.**"

## Use-cases

"`FunctionK` tends to show up when there is abstraction over higher-kinds. For example, interpreters for free monads and free applicatives are represented as `FunctionK` instances."

## Types with more than one type parameter

"It's still possible to use `FunctionK` with types that would normally take more than one type parameter (such as `Either`) if we fix all of the type parameters except for one":

```scala
type ErrorOr[A] = Either[String, A]

val errorOrFirst: FunctionK[List, ErrorOr] =
  λ[FunctionK[List, ErrorOr]](_.headOption.toRight("ERROR: the list was empty!"))
```

## Natural Transformation

"In category theory, a natural transformation provides a morphism between Functors while preserving the internal structure. It's one of the most fundamental notions of category theory.

If we have two Functors F and G, `FunctionK[F, G]` is a natural transformation via parametricity. That is, given `fk: FunctionK[F, G]`, for all functions `A => B` and all `fa: F[A]` the following are equivalent:

```
fk(F.map(fa)(f)) <-> G.map(fk(fa))(f)
```

We don't need to write a law to test the implementation of the `fk` for the above to be true. It's automatically given by parametricity.

Thus natural transformation can be implemented in terms of `FunctionK`. This is why a parametric polymorphic function `FunctionK[F, G]` is sometimes referred as a natural transformation. However, they are two different concepts that are not isomorphic."
