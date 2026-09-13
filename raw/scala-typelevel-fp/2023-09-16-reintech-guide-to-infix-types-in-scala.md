# A Guide to Scala's Infix Types

> Source: https://reintech.io/blog/guide-to-infix-types-in-scala
> Collected: 2026-09-13
> Published: 2023-09-16

By Arthur C. Codex (reintech.io blog).

## Core concept

"Infix types allow you to write two-parameter type constructors using an infix notation, placing the type operator between its arguments rather than in front of them."

Transformation pattern: infix form `A TypeConstructor B` vs. standard form `TypeConstructor[A, B]`.

## Code examples

Basic `Either` alias used infix:

```scala
type or[A, B] = Either[A, B]
val result2: String or Int = Right(42)
```

Error handling built on the same alias:

```scala
type Validated[A] = ValidationError or A
def validateUser(email: String, age: Int): Validated[User]
```

Map-related alias:

```scala
type ->[K, V] = (K, V)
type Cache = Map[String -> List[Int]]
```

Natural transformations (same `~>` alias documented in the cats FunctionK docs):

```scala
trait ~>[F[_], G[_]] { def apply[A](fa: F[A]): G[A] }
implicit val optionToList = new (Option ~> List)
```

Custom tuple-like structure:

```scala
type :=[K, V] = Entry[K, V]
val auditLog: List[String := String]
```

## Limitations noted

Limited IDE support, team-familiarity/readability concerns for readers unfamiliar with the convention, and the restriction that infix notation requires exactly two type parameters.
