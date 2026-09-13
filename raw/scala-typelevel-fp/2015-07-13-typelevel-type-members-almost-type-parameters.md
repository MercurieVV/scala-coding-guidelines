# Type members are almost type parameters

> Source: https://typelevel.org/blog/2015/07/13/type-members-parameters.html
> Collected: 2026-09-13
> Published: 2015-07-13

Typelevel Blog. First of a series of articles on "Type Parameters and Type Members". Tested with Scala 2.11.7.

## Rule of thumb

"Type members like `Member`

```scala
class Blah {
  type Member
}
```

and parameters like `Param`

`class Blah2[Param]`

have more similarities than differences. The choice of which to use for a given situation is usually a matter of convenience. In brief, a rule of thumb: **a type parameter is usually more convenient and harder to screw up, but if you intend to use it existentially in most cases, changing it to a member is probably better**."

## Two lists, all alike (parameter version vs. member version)

```scala
sealed abstract class PList[T]
final case class PNil[T]() extends PList[T]
final case class PCons[T](head: T, tail: PList[T]) extends PList[T]
```

The member-based equivalent (`MList`) replaces the type parameter `T` with an abstract type member, so a plain `MList` (with the member left abstract) corresponds to the existential `PList[_]`, while `MList { type T = Int }` corresponds to `PList[Int]`.

"**In terms of type parameters, `MList` is like `PList[_]`, and `MList {type T = Int}` is like `PList[Int]`.** For the former, we say that the member, or parameter, is existential."

## When is existential OK?

Some functions can be written against the existential (unparameterized) version directly, e.g. computing length without touching the element type:

```scala
def mlength(xs: MList): Int =
  xs.uncons match {
    case None => 0
    case Some(c) => 1 + mlength(c.tail)
  }
```

The type-parameter equivalent typically "lifts" the parameter to the function:

```scala
def plengthT[T](xs: PList[T]): Int =
  xs match {
    case PNil() => 0
    case PCons(_, t) => 1 + plengthT(t)
  }
```

By the parameter/member conversion rules, an existential equivalent for `PList` also compiles:

```scala
def plengthE(xs: PList[_]): Int =
  xs match {
    case PNil() => 0
    case PCons(_, t) => 1 + plengthE(t)
  }
```

General rule for when a function can be written existentially: "When a type parameter appears only in one argument, and appears nowhere in the result type, we should always, ideally, be able to write the function in an existential manner."

"This hints at what is usually, though not always, **an advantage for type parameters: you have to ask for an existential, rather than silently getting one just because you forgot a refinement**."

## Why frame it this way (equivalence as a learning tool)

"My guess is that type parameters are much better understood than type members, but that existentials via type members are better understood than existentials introduced by `_` or `forSome`, though I'd wager that neither form of existential is particularly well understood."
