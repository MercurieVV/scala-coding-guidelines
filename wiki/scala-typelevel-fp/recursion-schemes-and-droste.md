# Recursion Schemes and the Droste Library

> Sources: higherkindness/droste README and source (Unknown date); Ziyang Liu, "Curried Thoughts" blog, 2017-11-13; Claude Code synthesis, 2026-09-06, 2026-09-12; Wikipedia, F-algebra (Unknown date); ekmett/recursion-schemes README (Unknown date); precog/matryoshka README (Unknown date)
> Raw: [Droste Library Overview](../../raw/scala-typelevel-fp/droste-library-overview.md); [Recursion Schemes in Scala — Elementary Introduction](../../raw/scala-typelevel-fp/2017-11-13-recursion-schemes-scala-elementary-intro.md); [Applying Recursion Schemes When Structure Is Unclear](../../raw/scala-typelevel-fp/recursion-schemes-when-structure-unclear-notes.md); [F-Algebra (Wikipedia)](../../raw/scala-typelevel-fp/f-algebra-wikipedia.md); [Droste Source: Algebra/Coalgebra Type Taxonomy](../../raw/scala-typelevel-fp/droste-source-algebra-taxonomy.md); [Droste Source: Schemes and Kernel](../../raw/scala-typelevel-fp/droste-source-schemes-and-kernel.md); [Droste Source: Zoo, Gather, Scatter](../../raw/scala-typelevel-fp/droste-source-zoo-gather-scatter.md); [Droste Worked Examples Per Scheme](../../raw/scala-typelevel-fp/droste-worked-examples-per-scheme.md); [Droste Worked Example: postpro](../../raw/scala-typelevel-fp/droste-worked-examples-per-scheme-postpro.md); [Droste CHANGELOG](../../raw/scala-typelevel-fp/droste-changelog.md); [recursion-schemes (Haskell) README](../../raw/scala-typelevel-fp/recursion-schemes-haskell-readme.md); [Matryoshka README](../../raw/scala-typelevel-fp/matryoshka-readme.md)
> Updated: 2026-09-12

## Overview

Recursion schemes factor the *shape* of a recursive traversal (fold, unfold, fold-then-unfold, or something stranger) out from the per-node logic, by expressing a recursive type as the fixed point of a non-recursive "pattern functor." Droste is the Scala/cats library implementing this family of schemes, descending from Haskell's `recursion-schemes` and Scala's earlier Matryoshka library. This article is a complete usage reference: every algebra/coalgebra entity type droste defines, every named scheme, and a working code example for each, drawn directly from droste's own source and test suite.

## Core mechanics

- **Pattern functor `F[_]`**: one layer of a recursive structure with the recursive slot replaced by a type parameter `A`. E.g. a list's pattern functor has a no-`A` constructor (nil) and a one-`A` constructor (cons).
- **Fixpoint type**: `final case class Fix[F[_]](unfix: F[Fix[F]])` ties the pattern functor back into the actual recursive type — the same role the value-level fixpoint combinator (`fix(f) = f(fix(f))`) plays for recursive functions. Droste also provides `Mu[F]` (least fixed point — inductive/finite data) and `Nu[F]` (greatest fixed point — coinductive/potentially-infinite codata), mirroring the same distinction documented in Matryoshka.
- **`Basis[F, T]` / `Embed[F, T]` / `Project[F, T]`**: typeclasses supplying `embed` (`F[T] => T`) and `project` (`T => F[T]`) between the pattern functor `F` and an existing recursive type `T`, so schemes can run over a type that isn't literally `Fix[F]` — including a hand-written sealed-trait ADT you already have (see the `cata` example below).

`Algebra[F, A]` and `Coalgebra[F, A]` are exactly an **F-algebra** (`F(A) -> A`) and its dual **F-coalgebra** (`A -> F(A)`) from category theory (Wikipedia, "F-algebra"). An F-algebra is a *constructor*: given one layer of `F`-structure, produce a value — the initial F-algebra is the datatype itself. An F-coalgebra is a *destructor/observer*: given a value, reveal its next layer of structure — the terminal F-coalgebra is what lets potentially-infinite structures be consumed one step at a time. Catamorphism = repeatedly apply the algebra (fold, provider side); anamorphism = repeatedly apply the coalgebra (unfold, consumer side). See [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](provider-consumer-least-power.md) for the same duality applied to module boundaries generally.

## The algebra/coalgebra type taxonomy

Every named algebra/coalgebra type droste defines is a specialization of two generalized primitives (`droste/algebras.scala`):

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
| `Trans[F, G, A]` / `TransM` | `F[A] => G[A]` / `F[A] => M[G[A]]` | | rewrites between two *different* pattern functors over the same carrier (see `demo.scala` example) | [package.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/package.scala) | [demo.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/trans/demo.scala) |

droste does **not** define a separate named `mutu` (mutumorphism) or `elgot`/`coelgot` combinator — the CHANGELOG shows the scheme set built up incrementally (apo/para in PR #5, futu/histo/chrono in PR #29, `dyna` in PR #34, pre/postpro in PR #61, zygo in PR #88) with no mutu/elgot ever merged. `zygo` (two semi-mutually-recursive algebras fused into one pass) covers the mutumorphism use case, and the generalized `scheme.ghylo` + custom `Gather`/`Scatter` gives Elgot-style expressive power without a dedicated entry point. (Matryoshka, by contrast, does expose `elgot`/`coelgot`/`elgotZygo` — see the Generalization section of its raw README — as one of several axes of "G.../...M/Elgot.../GElgot...M" generalization; droste instead collapses everything into `ghylo`/`Gather`/`Scatter`.)

Every algebra/coalgebra carries useful combinators regardless of scheme: `.zip` fuses two algebras (or coalgebras) over the same functor into one producing/consuming a tuple, letting two independent computations run in a single pass; `.gather`/`.scatter` attach a strategy for use with `ghylo`; `.lift[M]` turns a pure algebra/coalgebra into its monadic form; `.compose`/`.andThen` give Kleisli/Cokleisli-style composition.

## The zoo of schemes, with a worked example each

All code below is droste's own maintainer-written source/tests (full text in the linked raw files). Every named scheme in `scheme.zoo` is proven equivalent to `scheme.ghylo`/`scheme.gcata` plus a specific `Gather`/`Scatter` — droste's own `SchemeEquivalence` test asserts `scheme.zoo.histo(alg) == scheme.gcata(alg.gather(Gather.histo)) == scheme.ghylo(alg.gather(Gather.histo), coalgebra.scatter(Scatter.ana))`.

| Scheme | Type used | Shape | When to reach for it | Code | Smoke test |
|---|---|---|---|---|---|
| **Catamorphism** (`scheme.cata`) | `Algebra[F, B]` | fold, `F[A] => A` | Only need already-folded child results | [scheme.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/scheme.scala) | [expr2.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/expr2/expr2.scala) |
| **Anamorphism** (`scheme.ana`) | `Coalgebra[F, A]` | unfold, `A => F[A]` | Building a structure from a seed, no source structure yet | [scheme.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/scheme.scala) | [NuLookup.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/NuLookup.scala) |
| **Hylomorphism** (`scheme.hylo`) | `Algebra` + `Coalgebra` | ana then cata, fused | Build-then-consume where materializing the intermediate structure is wasteful | [scheme.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/scheme.scala); [kernel.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/kernel.scala) | [SchemeEquivalence.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/tests/SchemeEquivalence.scala) (no dedicated `hylo` example; exercised via `ghylo` equivalence) |
| **Paramorphism** (`scheme.zoo.para`) | `RAlgebra[R, F, B]` | fold + original substructure | Need the folded result *and* the un-folded child, not just one | [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) | [PartOrder.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/paraM/PartOrder.scala) |
| **Apomorphism** (`scheme.zoo.apo`) | `RCoalgebra[R, F, A]` | dual of para | Unfolding but need to terminate early with a final value | [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) | [HeadEff.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/apoM/HeadEff.scala) |
| **Histomorphism** (`scheme.zoo.histo`) | `CVAlgebra[F, B]` | fold over `Attr` (cofree), sees any prior computed value | Later steps depend on more than just the immediate child (e.g. dynamic programming) | [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) | [change.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/histo/change.scala) |
| **Dynamorphism** (`scheme.zoo.dyna`) | `CVAlgebra` + `Coalgebra` | ana then histo, fused | Same shape as hylo but the fold side needs histomorphism's lookback | [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) | [change.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/histo/change.scala) (`solveFused`) |
| **Futumorphism** (`scheme.zoo.futu`) | `CVCoalgebra[F, A]` | dual of histo, via `Coattr` (free) | Need flexible control over how many layers get unfolded per step | [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) | [ListExchange.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/futu/ListExchange.scala) |
| **Chronomorphism** (`scheme.zoo.chrono`) | `CVAlgebra` + `CVCoalgebra` | futu then histo, fused | Need both time-traveling unfold and fold in one pass | [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) | no dedicated example/test in droste's suite as of 2026-09-12 — compose from the `histo`/`futu` examples above |
| **Zygomorphism** (`scheme.zoo.zygo`) | `Algebra[F, A]` + `RAlgebra[A, F, B]` | two simultaneous folds, second depends on first | Two+ recursive computations over one structure, second needs the first's result at each step | [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) | [PlusMinus.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/zygo/PlusMinus.scala) |
| **Prepromorphism** (`scheme.zoo.prepro`) | natural transformation `F ~> F` + `Algebra[F, B]` | preprocess each layer before folding | Need to rewrite/prune the structure on the way down during a fold | [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) | [SmallPre.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/pro/SmallPre.scala) |
| **Postpromorphism** (`scheme.zoo.postpro`) | `Coalgebra[F, A]` + natural transformation `F ~> F` | postprocess each layer after unfolding | Need to rewrite the structure on the way up during an unfold | [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) | [SmallPost.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala-2.13+/higherkindness/droste/tests/SmallPost.scala) |

Droste additionally exposes the generalized combinator `scheme.ghylo`, which composes an arbitrary "gather" (fold side, e.g. `Gather.histo`) with an arbitrary "scatter" (unfold side, e.g. `Scatter.ana`) — droste's own README example computes Fibonacci by unfolding natural numbers via `ana` and folding via `histo` in one fused pass:

```scala
val fib: BigDecimal => BigDecimal = scheme.ghylo(
  fibAlgebra.gather(Gather.histo),
  natCoalgebra.scatter(Scatter.ana))
```

Algebras can also be `.zip`ped together to run multiple computations (e.g. Fibonacci and sum-of-squares) over the same traversal in a single pass.

### `cata` on a plain existing ADT (no `Fix` required)

```scala
sealed trait Expr
final case class Const(value: BigDecimal) extends Expr
final case class Add(x: Expr, y: Expr)    extends Expr

sealed trait ExprF[A]
final case class ConstF[A](value: BigDecimal) extends ExprF[A]
final case class AddF[A](x: A, y: A)          extends ExprF[A]

implicit val basisExprF: Basis[ExprF, Expr] =
  Basis.Default(
    Algebra[ExprF, Expr] { case ConstF(v) => Const(v); case AddF(x, y) => Add(x, y) },
    Coalgebra[ExprF, Expr] { case Const(v) => ConstF(v); case Add(x, y) => AddF(x, y) }
  )

val evaluate: Expr => BigDecimal = scheme.cata(Algebra[ExprF, BigDecimal] {
  case ConstF(v)  => v
  case AddF(x, y) => x + y
})
```

This is the pattern for applying droste to *your own* recursive type: define the pattern functor `ExprF`, a `Traverse[ExprF]` instance, and a `Basis[ExprF, Expr]` (`embed`/`project` pair). Every scheme then works directly over `Expr` — full source in the [worked examples raw file](../../raw/scala-typelevel-fp/droste-worked-examples-per-scheme.md).

Code: [scheme.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/scheme.scala) · Smoke test: [expr2.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/expr2/expr2.scala)

### `histo` + `dyna`: dynamic programming (make change)

```scala
val makeChangeAlgebra: CVAlgebra[Nat, Set[List[Coin]]] = CVAlgebra {
  case Next(attr) =>
    // attr: Attr[Nat, Set[List[Coin]]] — walk attr.tail repeatedly to look
    // back arbitrarily far, not just at the immediately preceding result
    ???
  case Zero => Set(List.empty)
}

val solve      = scheme.zoo.histo(makeChangeAlgebra)
val solveFused = scheme.zoo.dyna(makeChangeAlgebra, toNatCoalgebra) // ana + histo, no intermediate Fix[Nat]
```

This is droste's port of Patrick Thomson's "Recursion Schemes, Part IV: Time is of the Essence" classic example — the full algebra (with the `lookup` helper walking the `Attr` chain) is in the [worked examples raw file](../../raw/scala-typelevel-fp/droste-worked-examples-per-scheme.md).

Code: [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) · Smoke test: [change.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/histo/change.scala)

### `apo` (monadic): transform only the head, short-circuit the rest

```scala
def mapHeadM[M[_]: Monad, A](f: A => M[A]): List[A] => M[List[A]] =
  scheme.zoo.apoM(RCoalgebraM[List[A], M, ListF[A, *], List[A]] {
    case Nil    => Monad[M].pure(NilF)
    case h :: t => f(h).map(ConsF(_, Left(t))) // Left(t): embed tail as-is, stop unfolding it
  })
```

Code: [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) · Smoke test: [HeadEff.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/apoM/HeadEff.scala)

### `para` (monadic): insert into a list using both the folded tail and the original remaining sublist

```scala
def insertM[M[_]: Monad, A](cmp: (A, A) => M[Boolean], x: A): List[A] => M[List[A]] =
  scheme.zoo.paraM(RAlgebraM[List[A], M, ListF[A, *], List[A]] {
    case NilF               => Monad[M].pure(List(x))
    case ConsF(h, (l, rec)) => cmp(x, h).map(if (_) x :: h :: l else h :: rec)
    // l = original un-folded tail, rec = its already-folded result
  })
```

Code: [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) · Smoke test: [PartOrder.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/paraM/PartOrder.scala)

### `futu`: unfold two elements at a time (pairwise list swap)

```scala
val exchangeCoalgebra: CVCoalgebra[ListF[String, *], List[String]] = CVCoalgebra {
  case Nil => NilF
  case head :: tailHead :: tailTail =>
    ConsF(tailHead, Coattr.roll(ConsF(head, Coattr.pure(tailTail)))) // Coattr.roll = one extra unfold layer now
  case head :: Nil => ConsF(head, Coattr.pure(Nil))                 // Coattr.pure = stop early with a value
}
val swap: List[String] => List[String] = scheme.zoo.futu(exchangeCoalgebra)
```

Code: [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) · Smoke test: [ListExchange.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/futu/ListExchange.scala)

### `zygo`: two simultaneous folds where the second depends on the first (alternating plus/minus)

```scala
val evenAlgebra  = Algebra[ListF[Int, *], Boolean] { case NilF => false; case ConsF(_, b) => !b }
val calcRAlgebra = RAlgebra[Boolean, ListF[Int, *], Int] {
  case NilF             => 0
  case ConsF(n, (b, x)) => if (b) n + x else n - x  // b comes from evenAlgebra at the same step
}
val plusMinus = scheme.zoo.zygo(evenAlgebra, calcRAlgebra) // List(a,b,c,d,e) => a - (b + (c - (d + e)))
```

Code: [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) · Smoke test: [PlusMinus.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/zygo/PlusMinus.scala)

### `prepro`: filter/preprocess the structure before folding

```scala
def filterNT(lim: Int): ListF[Int, *] ~> ListF[Int, *] = new (ListF[Int, *] ~> ListF[Int, *]) {
  def apply[A](l: ListF[Int, A]): ListF[Int, A] = l match {
    case ConsF(h, _) if h > lim => NilF
    case other                  => other
  }
}
val smallSum = scheme.zoo.prepro(filterNT(10), Algebra[ListF[Int, *], Int] {
  case ConsF(h, t) => h + t; case NilF => 0
})
```

Code: [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) · Smoke test: [SmallPre.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala/higherkindness/droste/examples/pro/SmallPre.scala)

### `postpro`: postprocess the structure after each unfold step

```scala
def filterNT(lim: Int): StreamF[Int, *] ~> StreamF[Int, *] = new (StreamF[Int, *] ~> StreamF[Int, *]) {
  def apply[A](s: StreamF[Int, A]): StreamF[Int, A] = s match {
    case t @ PrependF(h, _) if h <= lim => t
    case _                              => EmptyF
  }
}
val infiniteCoalg = Coalgebra[StreamF[Int, *], Int](n => PrependF(n, Eval.later(n + 1)))
val smallStream = scheme.zoo.postpro(infiniteCoalg, filterNT(10)).andThen(_.toList)
// smallStream(7) == List(7, 8, 9, 10); smallStream(11) == Nil
```

Code: [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) · Smoke test: [SmallPost.scala](https://github.com/higherkindness/droste/blob/main/modules/tests/src/test/scala-2.13+/higherkindness/droste/tests/SmallPost.scala)

Full runnable source for every example above (including `ana` over `Nu` with a lazy lookup table, and `Trans`/`TransM` for rewriting between two different pattern functors) is in [Droste Worked Examples Per Scheme](../../raw/scala-typelevel-fp/droste-worked-examples-per-scheme.md) and [Droste Worked Example: postpro](../../raw/scala-typelevel-fp/droste-worked-examples-per-scheme-postpro.md).

## Recognizing recursion-scheme shape when the structure isn't an obvious tree

The key move is to look for the pattern functor before looking for the recursion: ask "what does one step look like, with the recursive part blanked out as `A`?" If that's expressible as a single covariant `F[_]`, an `Algebra`/`Coalgebra` applies — regardless of whether the original type visually resembles a tree. This generalizes past ASTs/expression trees to:

- API pagination: `Coalgebra[Option, Cursor]`, `None` signaling done.
- Directory walks fused via hylomorphism (e.g. aggregate total size) without materializing the whole tree.
- A parser combinator's single grammar step, or a state machine's per-state transition set.

Choose the specific scheme by what information the step needs (see table above) — e.g. "only needs folded children" → catamorphism, "needs to see any earlier computed value" → histomorphism, "would otherwise materialize a throwaway intermediate structure" → hylomorphism.

**When not to bother:** a single fold direction, one or two recursion sites total, no plan to compose schemes later — plain recursion or `Functor`/`Traverse` is simpler than pulling in droste's `Fix`/`Basis`/macro machinery for one call site.

## Caveats

A from-scratch toy `Fix`/cata/ana implementation (as in introductory tutorials) is typically not stack-safe and can't handle infinite structures — droste (or historically Matryoshka) exists precisely to provide production-grade, stack-safe implementations of these schemes. Even droste isn't immune to stack overflow when a genuinely-infinite/cyclic coinductive structure (`Nu`) is fully forced — droste's own `NuLookup` test demonstrates a deliberately cyclic lookup table stack-overflowing when unrolled to completion.

## See Also

- [Cats & Category-Theory Patterns](cats-category-theory-patterns.md)
- [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](provider-consumer-least-power.md)
