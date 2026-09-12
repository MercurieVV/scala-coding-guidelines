# Droste Recursion Scheme Zoo and Worked Examples

> Sources: higherkindness/droste README and source (Unknown date); Claude Code synthesis, 2026-09-12, 2026-09-13
> Raw: [Droste Source: Schemes and Kernel](../../raw/scala-typelevel-fp/droste-source-schemes-and-kernel.md); [Droste Source: Zoo, Gather, Scatter](../../raw/scala-typelevel-fp/droste-source-zoo-gather-scatter.md); [Droste Worked Examples Per Scheme](../../raw/scala-typelevel-fp/droste-worked-examples-per-scheme.md); [Droste Worked Example: postpro](../../raw/scala-typelevel-fp/droste-worked-examples-per-scheme-postpro.md); [Droste's athema Module](../../raw/scala-typelevel-fp/droste-athema-example.md); [Droste CHANGELOG](../../raw/scala-typelevel-fp/droste-changelog.md)
> Updated: 2026-09-13

## Overview

Every named scheme in `scheme.zoo` is proven equivalent to `scheme.ghylo`/`scheme.gcata` plus a specific `Gather`/`Scatter` — droste's own `SchemeEquivalence` test asserts `scheme.zoo.histo(alg) == scheme.gcata(alg.gather(Gather.histo)) == scheme.ghylo(alg.gather(Gather.histo), coalgebra.scatter(Scatter.ana))`. This article covers the full scheme table plus a working code example for each, all drawn directly from droste's own source and test suite; for the algebra/coalgebra type definitions each scheme consumes, see [Droste Algebra/Coalgebra Type Taxonomy](droste-algebra-coalgebra-taxonomy.md).

## The zoo of schemes

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
| **Chronomorphism** (`scheme.zoo.chrono`) | `CVAlgebra` + `CVCoalgebra` | futu then histo, fused | Need both time-traveling unfold and fold in one pass | [zoo.scala](https://github.com/higherkindness/droste/blob/main/modules/core/src/main/scala/higherkindness/droste/zoo.scala) | no dedicated example/test in droste's suite as of 2026-09-13 — compose from the `histo`/`futu` examples below |
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

## Worked examples

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

## `athema`: a real application combining three schemes

Droste's README describes its own `athema` module as "a math expression parser/processor, as a more extensive example of recursion schemes." It builds a small computer-algebra system over `Fix[Expr[V, *]]` (`Var`/`Const`/`Neg`/`Add`/`Sub`/`Prod`/`Div`) and applies three different schemes to three different problems over the *same* pattern functor:

- **`Evaluate.evaluate`** — a plain `cataM` over `Either[String, *]`: the algebra can fail (`unknown variable: $name`), so folding bottom-up produces either a numeric result or the first error.
- **`Differentiate.differentiate`** — a **paramorphism**, reached via `scheme.gcata(algebra)(Gather.para)` rather than `scheme.zoo.para` directly. Its `RAlgebra[Expr.Fixed[V], Expr[V, *], Expr.Fixed[V]]` shows each child arriving as a `(originalSubexpr, derivative)` pair; the product rule needs both the original operand *and* its derivative simultaneously (`Prod((x, xx), (y, yy)) => Add(Prod(x, yy), Prod(xx, y))`) — the textbook reason a paramorphism, not a plain catamorphism, is required for symbolic differentiation.
- **`Simplify.simplify`** — an ordinary rewrite-rule pattern match (`Prod(Const(Zero), _) => Const(Zero)`, `Add(x, y) if x == y => Prod(Const(Two).fix, x)`, ...) organized as a same-functor `Trans[Expr[V,*], Expr[V,*], Expr.Fixed[V]]`, then exposed as a plain `Algebra` via `.algebra` for `scheme.cata` — showing `Trans` used to structure a local-rewrite-rule algebra, not just to convert between two different pattern functors.

Full source (pattern functor, `Traverse` instance, and all three algebras) is in [Droste's athema Module](../../raw/scala-typelevel-fp/droste-athema-example.md).

Code: [algebras.scala](https://github.com/higherkindness/droste/blob/main/athema/src/main/scala/higherkindness/athema/algebras.scala); [expr.scala](https://github.com/higherkindness/droste/blob/main/athema/src/main/scala/higherkindness/athema/expr.scala)

## See Also

- [Recursion Schemes and the Droste Library](recursion-schemes-and-droste.md)
- [Droste Algebra/Coalgebra Type Taxonomy](droste-algebra-coalgebra-taxonomy.md)
