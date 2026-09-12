# Droste Algebra/Coalgebra Type Taxonomy

> Sources: higherkindness/droste source (Unknown date); Claude Code synthesis, 2026-09-12, 2026-09-13
> Raw: [Droste Source: Algebra/Coalgebra Type Taxonomy](../../raw/scala-typelevel-fp/droste-source-algebra-taxonomy.md); [Droste Source: Basis and Fixpoints](../../raw/scala-typelevel-fp/droste-source-basis-and-fixpoints.md); [Droste Macro Derivation](../../raw/scala-typelevel-fp/droste-macro-derivation.md); [Droste CHANGELOG](../../raw/scala-typelevel-fp/droste-changelog.md); [Matryoshka README](../../raw/scala-typelevel-fp/matryoshka-readme.md)
> Updated: 2026-09-13

## Overview

Every named algebra/coalgebra type droste defines is a specialization of two generalized primitives, and every recursive type droste can run schemes over is wired up through the `Embed`/`Project`/`Basis` typeclasses. This article covers both, plus how to get a pattern functor and its instances without hand-writing them (macro annotations on Scala 2, `derives` on Scala 3). See [Recursion Schemes and the Droste Library](recursion-schemes-and-droste.md) for the conceptual overview and [Droste Recursion Scheme Zoo and Worked Examples](droste-scheme-zoo-examples.md) for how these types get used by each named scheme.

## The two generalized primitives

- `GAlgebra[F[_], S, A]` wraps `F[S] => A` — "given one layer of `F` filled with carrier type `S`, produce `A`."
- `GCoalgebra[F[_], A, S]` wraps `A => F[S]` — "given `A`, produce one layer of `F` filled with carrier type `S`."

`package.scala` specializes these into every named type. "Code" links go straight to droste's GitHub source; "Smoke test" links to the maintainer-written scalacheck test/example that exercises the type:

| Type | Definition | Shape | Used by | Code | Smoke test |
|---|---|---|---|---|---|
| `Algebra[F, A]` | `GAlgebra[F, A, A]` | `F[A] => A` | `cata`, `hylo` | [package.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/package.scala) | [expr2.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/expr2/expr2.scala) |
| `Coalgebra[F, A]` | `GCoalgebra[F, A, A]` | `A => F[A]` | `ana`, `hylo` | [package.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/package.scala) | [NuLookup.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/NuLookup.scala) |
| `AlgebraM[M, F, A]` | `GAlgebraM[M, F, A, A]` | `F[A] => M[A]` | `cataM`, `hyloM` | [package.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/package.scala) | [PartOrder.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/paraM/PartOrder.scala) (`RAlgebraM`, a specialization) |
| `CoalgebraM[M, F, A]` | `GCoalgebraM[M, F, A, A]` | `A => M[F[A]]` | `anaM`, `hyloM` | [package.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/package.scala) | [NuLookup.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/NuLookup.scala) (`scheme.anaM`) |
| `RAlgebra[R, F, A]` | `GAlgebra[F, (R, A), A]` | `F[(R, A)] => A` | `para` — sees original child `R` *and* its folded result `A` | [package.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/package.scala) | [PlusMinus.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/zygo/PlusMinus.scala) |
| `RCoalgebra[R, F, A]` | `GCoalgebra[F, A, Either[R, A]]` | `A => F[Either[R, A]]` | `apo` — can short-circuit a branch with `Left[R]` | [package.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/package.scala) | [HeadEff.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/apoM/HeadEff.scala) (`RCoalgebraM`, a specialization) |
| `RAlgebraM` / `RCoalgebraM` | monadic versions of the above | | `paraM`, `apoM` | [package.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/package.scala) | [PartOrder.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/paraM/PartOrder.scala); [HeadEff.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/apoM/HeadEff.scala) |
| `CVAlgebra[F, A]` | `GAlgebra[F, Attr[F, A], A]` | `F[Attr[F, A]] => A` | `histo` — sees the *entire history* of computed results via `Attr` (cofree comonad) | [package.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/package.scala) | [change.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/histo/change.scala) |
| `CVCoalgebra[F, A]` | `GCoalgebra[F, A, Coattr[F, A]]` | `A => F[Coattr[F, A]]` | `futu` — can unfold multiple layers per step via `Coattr` (free monad) | [package.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/package.scala) | [ListExchange.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/futu/ListExchange.scala) |
| `Gather[F, S, A]` | `(A, F[S]) => S` | | plugs into `ghylo`/`gcata` to fuse a result with not-yet-folded children | [gather.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/gather.scala) | [SchemeEquivalence.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/tests/SchemeEquivalence.scala) |
| `Scatter[F, A, S]` | `S => Either[A, F[S]]` | | plugs into `ghylo`/`gana` to decide short-circuit vs. continue unfolding | [scatter.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/scatter.scala) | [SchemeEquivalence.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/tests/SchemeEquivalence.scala) |
| `Trans[F, G, A]` / `TransM` | `F[A] => G[A]` / `F[A] => M[G[A]]` | | rewrites between two *different* pattern functors over the same carrier, or organizes a same-functor local rewrite as a plain `Algebra` | [package.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/package.scala) | [demo.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/trans/demo.scala) |

droste does **not** define a separate named `mutu` (mutumorphism) or `elgot`/`coelgot` combinator — the CHANGELOG shows the scheme set built up incrementally (apo/para in PR #5, futu/histo/chrono in PR #29, `dyna` in PR #34, pre/postpro in PR #61, zygo in PR #88) with no mutu/elgot ever merged. `zygo` (two semi-mutually-recursive algebras fused into one pass) covers the mutumorphism use case, and the generalized `scheme.ghylo` + custom `Gather`/`Scatter` gives Elgot-style expressive power without a dedicated entry point. (Matryoshka, by contrast, does expose `elgot`/`coelgot`/`elgotZygo` — see the Generalization section of its raw README — as one of several axes of "G.../...M/Elgot.../GElgot...M" generalization; droste instead collapses everything into `ghylo`/`Gather`/`Scatter`.)

Every algebra/coalgebra carries useful combinators regardless of scheme: `.zip` fuses two algebras (or coalgebras) over the same functor into one producing/consuming a tuple, letting two independent computations run in a single pass; `.gather`/`.scatter` attach a strategy for use with `ghylo`; `.lift[M]` turns a pure algebra/coalgebra into its monadic form; `.compose`/`.andThen` give Kleisli/Cokleisli-style composition.

## `Embed`/`Project`/`Basis`: wiring a pattern functor to an existing recursive type

```scala
trait Embed[F[_], R] {
  def algebra: Algebra[F, R]      // construct R from one F layer
}

trait Project[F[_], R] {
  def coalgebra: Coalgebra[F, R]  // deconstruct R into one F layer
  // + derived traversal helpers: all, any, collect, contains, foldMap, foldMapM
  // (all implemented via a trampolined foldMapM loop, so they're stack-safe)
}

sealed trait Basis[F[_], R] extends Embed[F, R] with Project[F, R]
// constructed via Basis.Default(algebra, coalgebra)
```

Droste ships built-in `Basis` instances for `List` (via `ListF`), `Attr`, `Coattr`, `Fix`, `Mu`, `Nu`, `cats.free.Cofree` (via `AttrF`), and `cats.free.Free` (via `CoattrF`) — so those recursive types work with every scheme out of the box, with no explicit `Basis` needed. `Basis.Solve[PR[_[_]]]` is what lets `scheme.apply[PatR[_[_]]]` (i.e. `scheme[Fix]`, `scheme[Mu]`, ...) infer the associated pattern-functor shape without the caller spelling it out at every call site — e.g. for `Fix` the pattern functor of `F` applied to carrier `A` is just `F[A]` itself, while for `Attr`/`Cofree` it's bound to `AttrF[F, A, *]` instead, reflecting that those types carry an extra annotation parameter.

## Fixpoint types: `Fix`, `Mu`, `Nu`

| Type | Encoding | Represents | Source |
|---|---|---|---|
| `Fix[F]` | `final case class Fix[F[_]](unfix: F[Fix[F]])` | general recursion, the simplest fixpoint | [Fix.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala-3/higherkindness/droste/data/Fix.scala) |
| `Mu[F]` | Church-encoded: `Mu[F]` *is* a function `Algebra[F, A] => A` for any `A` | least fixed point — inductive/finite data ("a value together with the fold that consumes it") | [Mu.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/data/Mu.scala) |
| `Nu[F]` | existential: bundles a seed `a: A` with a `Coalgebra[F, A]` | greatest fixed point — coinductive/potentially-infinite codata ("a seed together with the unfold that regenerates it") | [Nu.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/data/Nu.scala) |

Because `Mu`'s Church encoding forces total consumption to produce a value, it can only represent finite/inductive structures. `Nu` never needs to fully materialize its structure — it can regenerate one more layer on demand — which is why it's the right carrier for a lazy/infinite unfold (droste's own `NuLookup` worked example unfolds a `Nu[Result]` from a lazy string-keyed lookup table, and deliberately demonstrates a stack overflow when a cyclic table is forced to completion).

## `Attr` and `Coattr`: the carriers behind `histo`/`futu`

`Attr[F, A]` (cofree-comonad-like: a value `A` at the root plus an `F[Attr[F, A]]` of annotated children) is what a `CVAlgebra` sees at every step of a histomorphism — walking `.tail` repeatedly reaches any previously-computed result, not just the immediate child. `Coattr[F, A]` is, under the hood, literally `Either[A, F[Coattr[F, A]]]`: `Coattr.pure(a)` stops right there with a final value, `Coattr.roll(fa)` continues with one more `F` layer to unfold immediately — exactly the two moves a `CVCoalgebra`'s `futu` step gets to make at every position (droste's `ListExchange` futumorphism worked example uses both in the same coalgebra). Full source for both is in [Droste Source: Basis and Fixpoints](../../raw/scala-typelevel-fp/droste-source-basis-and-fixpoints.md).

## Getting a pattern functor without hand-writing it

Every worked example so far hand-writes its pattern functor (`ExprF`, `ListF`, `Nat`, ...) and its `Traverse`/`Basis` instances. Droste also supports deriving both:

**Scala 2 — macro annotations:**

```scala
@deriveFixedPoint sealed trait RecursiveExpr
object RecursiveExpr {
  final case class Const(value: BigDecimal)                extends RecursiveExpr
  final case class Add(x: RecursiveExpr, y: RecursiveExpr) extends RecursiveExpr
  final case class AddList(list: List[RecursiveExpr])      extends RecursiveExpr
}
// generates RecursiveExpr.fixedpoint.{RecursiveExprF, ConstF, AddF, AddListF}
// plus the Basis[RecursiveExprF, RecursiveExpr] wiring — no hand-written pattern
// functor or Basis instance needed.
import RecursiveExpr.fixedpoint._
val evaluate: RecursiveExpr => BigDecimal = scheme.cata(Algebra[RecursiveExprF, BigDecimal] {
  case ConstF(v) => v; case AddF(x, y) => x + y; case AddListF(l) => l.reduce(_ + _)
})
```

`@deriveTraverse` derives `Traverse` for an already-defined pattern functor, including one nested inside another `@deriveTraverse`-annotated type and inside `List`/`Option` wrappers — needed wherever a monadic scheme (`cataM`, `anaM`, ...) has to traverse the structure.

**Scala 3 — `derives Functor / Foldable / Traverse`:**

```scala
case class Box[A](value: A) derives Functor

sealed trait CList[A] derives Functor
case object CNil extends CList[Nothing]
case class CCons[A](head: A, tail: CList[A]) extends CList[A]
```

Scala 3 replaces the macro-annotation approach with the standard `derives` clause, backed by droste's shapeless3-based derivation core (a credited port of the `kittens` project's derivation machinery) — a pattern functor gets `Functor`/`Foldable`/`Traverse` for free, composing with ordinary sealed-trait ADT hierarchies the same way `cats.Show`/`cats.Eq` derivation does elsewhere in the Typelevel ecosystem.

Code: [annotations.scala](https://github.com/higherkindness/droste/blob/main/modules/macros/src/main/scala-2/higherkindness/droste/macros/annotations.scala) (Scala 2); [Derived.scala](https://github.com/higherkindness/droste/blob/main/modules/macros/src/main/scala-3/higherkindness/droste/derivation/Derived.scala) (Scala 3) · Smoke test: [deriveFixedPoint.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala-2/higherkindness/droste/examples/deriveFixedPoint.scala); [deriveTraverse.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala-2/higherkindness/droste/examples/deriveTraverse.scala); [DerivedTests.scala](https://github.com/higherkindness/droste/blob/main/modules/macros/src/test/scala-3/higherkindness/droste/derivation/DerivedTests.scala)

## See Also

- [Recursion Schemes and the Droste Library](recursion-schemes-and-droste.md)
- [Droste Recursion Scheme Zoo and Worked Examples](droste-scheme-zoo-examples.md)
- [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)
