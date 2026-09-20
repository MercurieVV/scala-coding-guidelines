# Stainless language usage: contracts, ADTs, and proofs

> Sources: EPFL LARA (Stainless docs), Unknown
> Raw: [Tutorial: Sorting](../../raw/stainless-verification/2026-09-20-stainless-tutorial-sorting.md); [Pure Scala subset](../../raw/stainless-verification/2026-09-20-stainless-pure-scala-subset.md); [Proving Theorems](../../raw/stainless-verification/2026-09-20-stainless-proving-theorems.md); [Verification conditions](../../raw/stainless-verification/2026-09-20-stainless-verification-conditions.md)
> Updated: 2026-09-20

## Overview

Stainless verifies a purely functional subset of Scala called Pure Scala: two kinds of
top-level declarations (ADTs, objects/modules), no mutable data or unrestricted imperative
effects, no floating point (use `Real`). Specifications attach to functions as `require`
(precondition), `ensuring` (postcondition), and `assert`/`check` (body-level). This article
covers the usage patterns for writing verifiable code and proofs; [Integrating Stainless
verification into a Mill build](stainless-mill-integration.md) covers the build-tool wiring.

## Contracts: require / ensuring / assert

`require` states a precondition; `ensuring(res => P)` states a postcondition over the return
value `res`:

```scala
def max(x: Int, y: Int): Int = {
  require(0 <= x && 0 <= y)
  val d = x - y
  if d > 0 then x
  else y
}.ensuring(res =>
  x <= res && y <= res && (res == x || res == y)
)
```

Stainless proves preconditions at *call sites*, not inside the function itself — "Stainless
only assumes an open program model, where any function could be called from outside," so every
call to a `require`-guarded function must be shown to satisfy that precondition given the
caller's path condition.

`assert` checks an expression locally without exposing it to the enclosing postcondition's
proof; `check` does the same check but *does* propagate into the outer verification condition —
useful for isolating which sub-goal actually failed during debugging:

```scala
def foo(): Unit = {
  val x = {
    assert(b1)  // local verification condition
    check(b2)   // affects postcondition verification
  }
  assert(b3)    // b1 not visible to solver
}.ensuring(_ => b3)  // b2 visible to solver
```

## ADTs and invariants

ADT roots must be `abstract` (unless the ADT has only one case), and — unlike ordinary
Scala — abstract classes cannot carry fields or constructor arguments:

```scala
sealed trait List
case object Nil extends List
case class Cons(head: BigInt, tail: List) extends List
```

Case classes attach invariants via a `require` in the class body; every construction site gets
a verification condition that the invariant holds:

```scala
case class Positive(value: BigInt = 0) {
  require(value >= 0)
}

case class B(x: BigInt) extends A {
  require(x >= 50)
}
```

`AnyVal` value classes use `@invariant` on a predicate method instead:

```scala
case class Positive(value: BigInt) extends AnyVal {
  @invariant
  def isPositive: Boolean = value >= 0
}
```

`@inlineInvariant` on an ADT (or an ancestor) inlines the invariant check directly into the
generated VC rather than calling out to a separate `inv` method.

## Pattern matching and exhaustiveness

Stainless considers the active precondition when checking match exhaustiveness — a case Scala's
own compiler would flag as non-exhaustive can be accepted once a `require` rules out the
missing case:

```scala
def getHead(l: List): Int = {
  require(!l.isInstanceOf[Nil])
  l match {
    case Cons(x, _) => x
  }
}
```

Match arms can carry `if` guards and `@` bindings, same as ordinary Scala pattern matching.

## Recursive functions, sets, and invariant-preserving code

A recursive function proves properties of its own recursive shape via the postcondition, e.g.
`size` never returns negative, and a sorted-insert preserves both sortedness and multiset
content:

```scala
def size(l: List): BigInt = {
  l match
    case Nil => 0
    case Cons(x, rest) => 1 + size(rest)
}.ensuring(res => res >= 0)

def content(l: List): Set[BigInt] =
  l match
    case Nil => Set.empty
    case Cons(i, t) => content(t) ++ Set(i)

def sInsert(x: BigInt, l: List): List = {
  require(isSorted(l))
  l match
    case Nil => Cons(x, Nil)
    case Cons(e, rest) if (x == e) => l
    case Cons(e, rest) if (x < e) => Cons(x, Cons(e, rest))
    case Cons(e, rest) if (x > e) => Cons(e, sInsert(x, rest))
}.ensuring { res =>
  isSorted(res) && content(res) == content(l) ++ Set(x)
}
```

`Int` is bounded 32-bit; `BigInt` unbounded. Overflow-sensitive code that intentionally wants
wraparound semantics opts in via `stainless.math.wrapping`.

## Loops: invariant and decreases

Imperative loops (in the contexts Stainless permits them) carry an `invariant` clause, checked
on entry and after every iteration (`noReturnInvariant` covers invariants a `return` would
otherwise break), and a `decreases` measure that must stay non-negative and strictly shrink each
iteration — the termination proof obligation:

```scala
while(cond) {
  body
} invariant(inv)

while(cond) {
  decreases(expr)
  body
}
```

## Proving theorems: .holds, because, @induct

A `Boolean`-returning function ending in `.holds` asserts the predicate is a theorem — true for
all inputs satisfying any `require`:

```scala
import stainless.collection._
import stainless.lang._

def appendContent[A](l1: List[A], l2: List[A]): Boolean = {
  l1.content ++ l2.content == (l1 ++ l2).content
}.holds
```

`because` (from `stainless.lang`) supplies a proof hint — syntactic sugar for conjunction —
guiding the solver through a case split:

```scala
def rightUnitAppend[T](l1: List[T]): Boolean = {
  (l1 ++ Nil() == l1) because {
    l1 match {
      case Nil()       => true
      case Cons(x, xs) => rightUnitAppend(xs)
    }
  }
}.holds
```

`@induct` automates structural induction over an ADT argument, generating the base/inductive
case split Stainless would otherwise need spelled out by hand:

```scala
import stainless.annotation._

@induct
def rightUnitAppend[T](l1: List[T]): Boolean = {
  l1 ++ Nil() == l1
}.holds
```

For proofs the solver can't close unaided, the equational-reasoning DSL from `stainless.proof`
chains `==|`-annotated steps to `.qed`, each step justified by a lemma call or `trivial`:

```scala
def reverseAppend[T](l1: List[T], l2: List[T]): Boolean = {
  ((l1 ++ l2).reverse == l2.reverse ++ l1.reverse) because {
    l1 match {
      case Nil() => {
        (Nil() ++ l2).reverse         ==| trivial                     |
        l2.reverse                    ==| rightUnitAppend(l2.reverse) |
        l2.reverse ++ Nil()           ==| trivial                     |
        l2.reverse ++ Nil().reverse
      }.qed
      case Cons(x, xs) => {
        ((x :: xs) ++ l2).reverse         ==| trivial               |
        (x :: (xs ++ l2)).reverse         ==| trivial               |
        (xs ++ l2).reverse :+ x           ==| reverseAppend(xs, l2) |
        (l2.reverse ++ xs.reverse) :+ x   ==|
          snocAfterAppend(l2.reverse, xs.reverse, x)                |
        l2.reverse ++ (xs.reverse :+ x)   ==| trivial               |
        l2.reverse ++ (x :: xs).reverse
      }.qed
    }
  }
}.holds
```

Proof workflow: apply `@induct` first for straightforward structural cases; fall back to manual
`because`/DSL proofs for properties induction alone can't close; modularize repeated reasoning
into helper lemmas; use `check` liberally while debugging to isolate which sub-goal fails;
tune `--timeout` and `--debug=verification` on the CLI when a VC needs more solver time or more
visibility.

## See Also

- [Integrating Stainless verification into a Mill build](stainless-mill-integration.md)
