# Abstract Type Members versus Generic Type Parameters in Scala

> Source: https://www.artima.com/weblogs/viewpost.jsp?thread=270195
> Collected: 2026-09-13
> Published: 2009-10-07

By Bill Venners (Artima Weblogs, "Angle Brackets and Curly Braces").

## Summary

"In this blog, I attempt to answer a common question among Scala programmers: when would I opt to use an abstract type member instead of a generic type parameter in Scala API design?"

## Basic distinction

Type parameter version:

```scala
trait Collection[T] {
  // ...
}
```

Type member version:

```scala
trait Collection {
  type T
  // ...
}
```

Both can be made concrete in a subtype:

```scala
// Type parameter version
trait StringCollection extends Collection[String] {
  // ...
}

// Type member version
trait StringCollection extends Collection {
  type T = String
  // ...
}
```

## Orthogonality (Odersky interview quote)

Venners asked Martin Odersky this question in an interview. Odersky's answer on orthogonality:

"There have always been two notions of abstraction: parameterization and abstract members. In Java you also have both, but it depends on what you are abstracting over. In Java you have abstract methods, but you can't pass a method as a parameter. You don't have abstract fields, but you can pass a value as a parameter. And similarly you don't have abstract type members, but you can specify a type as a parameter. So in Java you also have all three of these, but there's a distinction about what abstraction principle you can use for what kinds of things. And you could argue that this distinction is fairly arbitrary.

What we did in Scala was try to be more complete and orthogonal. We decided to have the same construction principles for all three sorts of members. So you can have abstract fields as well as value parameters. You can pass methods (or "functions") as parameters, or you can abstract over them. You can specify types as parameters, or you can abstract over them. And what we get conceptually is that we can model one in terms of the other. At least in principle, we can express every sort of parameterization as a form of object-oriented abstraction. So in a sense you could say Scala is a more orthogonal and complete language."

## The quadratic-blowup quote

"But in practice, when you [use type parameterization] with many different things, it leads to an explosion of parameters, and usually, what's more, in bounds of parameters. At the 1998 ECOOP, Kim Bruce, Phil Wadler, and I had a paper where we showed that as you increase the number of things you don't know, the typical program will grow quadratically. So there are very good reasons not to do parameters, but to have these abstract members, because they don't give you this quadratic blow up."

## Worked example: ScalaTest FixtureSuite (why Venners chose type members)

Problem: provide traits in ScalaTest letting users pass fixture objects into tests (functional style vs. JUnit's `setUp`/`tearDown`). Candidate designs:

```scala
// Type parameter version
trait FixtureSuite[F] {
  // ...
}

// Type member version
trait FixtureSuite {
  type F
  // ...
}
```

Both look equivalent for a single concrete suite:

```scala
// Type parameter version
class MySuite extends FixtureSuite[StringBuilder] {
  // ...
}

// Type member version
class MySuite extends FixtureSuite {
  type F = StringBuilder
  // ...
}
```

The difference shows up once mixin traits that concretize the fixture type enter the picture. Type-parameter version needs a self-type and the subclass must repeat the parameter:

```scala
// Type parameter version
trait StringBuilderFixture { this: FixtureSuite[StringBuilder] =>
  // ...
}

class MySuite extends FixtureSuite[StringBuilder] with StringBuilderFixture {
  // ...
}
```

Type-member version needs no repetition when mixing in:

```scala
// Type member version
trait StringBuilderFixture { this: FixtureSuite =>
  type F = StringBuilder
  // ...
}

class MySuite extends FixtureSuite with StringBuilderFixture {
  // ...
}
```

"Because of this difference, I chose to model the fixture parameter type as an abstract type member, not a generic type parameter, in ScalaTest. I named the type member `FixtureParam`, to make it more obvious to readers of the code what the type is used for. (This feature is in ScalaTest 1.0.)"

He notes the type-parameter approach gets worse as the number of fixture types grows (e.g. a hypothetical `FixtureSuite3[StringBuilder, ListBuffer, Stack]` vs. `FixtureSuite3` with the three types fixed as members and picked up automatically via mixed-in traits) — this is the concrete, small-scale instance of the "quadratic blowup" Odersky described.

## Minor readability point

With a type parameter, readers of `class MySuite extends FixtureSuite[StringBuilder] with StringBuilderFixture` do not see the parameter's name in context. With a type member (`type FixtureParam = StringBuilder`), the name is written out at the point of concretization, which can aid readability.
