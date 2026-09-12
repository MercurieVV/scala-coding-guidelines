# Droste Worked Examples: One Complete Program Per Scheme

> Source: https://github.com/higherkindness/droste (main branch, `modules/tests/src/test/scala/higherkindness/droste/examples/**`, plus `modules/tests/src/test/scala/higherkindness/droste/tests/SchemeEquivalence.scala`)
> Collected: 2026-09-12
> Published: Unknown

These are droste's own maintainer-written test/example programs, each demonstrating one named scheme end-to-end (data type + `Traverse`/`Basis` instance + algebra/coalgebra + scheme call + assertions). Kept as separate self-contained snippets, one per file of origin.

## `ana` — `examples/NuLookup.scala` (unfolding a possibly-infinite `Nu` from a lazy lookup table, monadically)

```scala
package higherkindness.droste.examples

import org.scalacheck.Properties
import org.scalacheck.Prop._
import cats.Applicative
import cats.Traverse
import cats.syntax.all._
import higherkindness.droste.CoalgebraM
import higherkindness.droste.scheme
import higherkindness.droste.data.Nu
import higherkindness.droste.util.DefaultTraverse
import scala.annotation.tailrec

final class NuLookup extends Properties("NuLookup") {
  import NuLookup._

  val lookup: Map[String, Result[String]] = Map(
    "a"          -> Ref("b"),
    "b"          -> Ref("c"),
    "this"       -> Ref("is"),
    "is"         -> Ref("broken"),
    "c"          -> Ref("d"),
    "d"          -> Value("d-value"),
    "work it"    -> Ref("harder"),
    "harder"     -> Ref("make it"),
    "make it"    -> Ref("better"),
    "better"     -> Ref("do it"),
    "do it"      -> Ref("faster"),
    "faster"     -> Ref("makes us"),
    "makes us"   -> Ref("more than"),
    "more than"  -> Ref("ever"),
    "ever"       -> Ref("hour after"),
    "hour after" -> Ref("hour"),
    "hour"       -> Ref("work is"),
    "work is"    -> Ref("never"),
    "never"      -> Value("over"),
    "loop a"     -> Ref("loop b"),
    "loop b"     -> Ref("loop a")
  )

  def catchAll[A](f: => A): Either[Throwable, A] =
    try Right(f)
    catch {
      case t: Throwable => Left(t)
    }

  property("Nu.project") = {

    // Note that we return our result in _one_ level of Option,
    // meaning that everything is unfolded to Nu when f is invoked
    val f: String => Option[Nu[Result]] =
      scheme.anaM[Option, Result, String, Nu[Result]](
        CoalgebraM(lookup.get(_: String))
      )

    @tailrec def unroll(nu: Nu[Result]): String = {
      Nu.un(nu) match {
        case Ref(a)   => unroll(a)
        case Value(v) => v
      }
    }

    val p1 = f("a").map(unroll) ?= Some("d-value")
    val p2 = f("z").map(unroll) ?= None
    val p3 = f("work it").map(unroll) ?= Some("over")
    val p4 = f("this").map(unroll) ?= None
    val p5 = catchAll(f("loop a")).isLeft // stackoverflow :(

    p1 && p2 && p3 && p4 && p5
  }

}

object NuLookup {
  sealed trait Result[A]
  final case class Ref[A](next: A)         extends Result[A]
  final case class Value[A](value: String) extends Result[A]

  object Result {
    implicit val resultTraverse: Traverse[Result] =
      new DefaultTraverse[Result] {
        def traverse[G[_]: Applicative, A, B](
            fa: Result[A]
        )(f: A => G[B]): G[Result[B]] =
          fa match {
            case Ref(next)              => f(next).map(Ref(_))
            case v: Value[B @unchecked] => (v: Result[B]).pure[G]
          }
      }
  }
}
```

Note the comment: unfolding to `Nu` (the greatest fixed point / coinductive type) with a cyclic lookup table (`"loop a" -> "loop b" -> "loop a"`) stack-overflows when fully unrolled — `Nu` supports laziness per-layer but this particular test still forces the whole structure via `unroll`, which is why the `p5` assertion expects `catchAll(...).isLeft`.

## `cata` — `examples/expr2/expr2.scala` (a plain, non-`Fix`-based recursive ADT with a hand-written `Basis`)

```scala
package higherkindness.droste
package examples

import org.scalacheck.Properties
import org.scalacheck.Prop._
import cats._
import cats.syntax.all._
import higherkindness.droste.util.DefaultTraverse

// demos recursion schemes between a regular AST and a fixed
// point AST without using Fix
final class Expr2Checks extends Properties("Expr2") {

  val evaluateAlgebra: Algebra[ExprF, BigDecimal] = Algebra {
    case ConstF(v)  => v
    case AddF(x, y) => x + y
  }

  val evaluate: Expr => BigDecimal = scheme.cata(evaluateAlgebra)

  property("1") = evaluate(Const(1)) ?= 1

  property("1 + 1") = evaluate(Add(Const(1), Const(1))) ?= 2

  property("1 + 2 + 5") = evaluate(Add(Add(Const(1), Const(2)), Const(5))) ?= 8
}

sealed trait Expr
final case class Const(value: BigDecimal) extends Expr
final case class Add(x: Expr, y: Expr)    extends Expr

sealed trait ExprF[A]
final case class ConstF[A](value: BigDecimal) extends ExprF[A]
final case class AddF[A](x: A, y: A)          extends ExprF[A]

object ExprF {
  implicit val traverseExprF: Traverse[ExprF] =
    new DefaultTraverse[ExprF] {
      def traverse[G[_]: Applicative, A, B](
          fa: ExprF[A]
      )(f: A => G[B]): G[ExprF[B]] =
        fa match {
          case c: ConstF[B] @unchecked => (c: ExprF[B]).pure[G]
          case AddF(x, y)              => (f(x), f(y)).mapN(AddF(_, _))
        }
    }

  val embedAlgebra: Algebra[ExprF, Expr] = Algebra {
    case ConstF(v)  => Const(v)
    case AddF(x, y) => Add(x, y)
  }

  val projectCoalgebra: Coalgebra[ExprF, Expr] = Coalgebra {
    case Const(v)  => ConstF(v)
    case Add(x, y) => AddF(x, y)
  }

  implicit val basisExprF: Basis[ExprF, Expr] =
    Basis.Default(embedAlgebra, projectCoalgebra)
}
```

This is the key example showing droste applied to an already-existing sealed-trait ADT (`Expr`/`Add`/`Const`) instead of `Fix[ExprF]` — you write the pattern functor `ExprF[A]`, a `Traverse[ExprF]`, and a `Basis[ExprF, Expr]` (an `embed`/`project` pair between `ExprF` and the real `Expr` type), and every scheme (`cata`, `ana`, `histo`, ...) then works directly over `Expr`.

## `apo` (monadic) — `examples/apoM/HeadEff.scala` (apply an effectful function to just the list head, short-circuiting the rest)

```scala
package higherkindness.droste.examples.apoM

import org.scalacheck.Properties
import org.scalacheck.Prop._
import cats.Monad
import cats.syntax.functor._
import higherkindness.droste.data.list._
import higherkindness.droste.RCoalgebraM
import higherkindness.droste.scheme

final class HeadEff extends Properties("HeadEff") {

  import HeadEff._

  def reciprocalHd(xs: List[Double]): Option[List[Double]] =
    mapHeadM[Option, Double](x => if (x != 0) Some(1.0 / x) else None).apply(xs)

  property("empty reciprocal") = reciprocalHd(Nil) ?= Some(Nil)

  property("non-empty reciprocal") =
    reciprocalHd(List(2.0, 3.0, 4.0)) ?= Some(List(0.5, 3.0, 4.0))

  property("failing reciprocal") = reciprocalHd(List(0.0, 3.0, 4.0)) ?= None

}

object HeadEff {

  // map the list head using an effectful function
  def mapHeadM[M[_], A](
      f: A => M[A]
  )(implicit M: Monad[M]): List[A] => M[List[A]] =
    scheme.zoo.apoM(
      RCoalgebraM[List[A], M, ListF[A, *], List[A]] {
        case Nil    => M.pure(NilF)
        case h :: t => f(h).map(ConsF(_, Left(t)))
      }
    )

}
```

The `Left(t)` in the `ConsF(_, Left(t))` branch is the apomorphism's short-circuit: the tail `t` is embedded back into the result list as-is (no further recursive unfolding), which is exactly why only the head gets the effectful transformation.

## `para` (monadic) — `examples/paraM/PartOrder.scala` (insert into a list, comparing against both the folded tail *and* the original remaining sublist)

```scala
package higherkindness.droste.examples.paraM

import org.scalacheck.Properties
import org.scalacheck.Prop._
import cats.Monad
import cats.syntax.functor._
import higherkindness.droste.data.list._
import higherkindness.droste.RAlgebraM
import higherkindness.droste.scheme

final class PartOrder extends Properties("PartOrder") {

  import PartOrder._

  def insertTuple(
      x: (Int, Int),
      l: List[(Int, Int)]
  ): Option[List[(Int, Int)]] =
    insertM[Option, (Int, Int)](
      { case ((x1, y1), (x2, y2)) =>
        if ((x1 <= x2) && (y1 <= y2)) Some(true)
        else if ((x1 > x2) && (y1 > y2)) Some(false)
        else None
      },
      x
    ).apply(l)

  property("empty insert") = insertTuple((0, 0), Nil) ?= Some(List((0, 0)))

  property("non-empty insert") =
    insertTuple((2, 2), List((0, 0), (1, 1), (3, 3))) ?= Some(
      List((0, 0), (1, 1), (2, 2), (3, 3))
    )

  property("failing insert") =
    insertTuple((1, 1), List((0, 0), (0, 2), (2, 2))) ?= None

}

object PartOrder {

  // insert a value into a list using an effectful comparison
  def insertM[M[_], A](cmp: (A, A) => M[Boolean], x: A)(implicit
      M: Monad[M]
  ): List[A] => M[List[A]] =
    scheme.zoo.paraM(
      RAlgebraM[List[A], M, ListF[A, *], List[A]] {
        case NilF => M.pure(List(x))
        case ConsF(h, (l, rec)) =>
          cmp(x, h).map(if (_) x :: h :: l else h :: rec)
      }
    )

}
```

`ConsF(h, (l, rec))` shows the paramorphism's characteristic shape: `l` is the original (un-folded) tail, `rec` is the already-folded result for that tail — the algebra can use either or both.

## `histo` + `dyna` — `examples/histo/change.scala` (classic dynamic-programming "make change" problem)

```scala
package higherkindness.droste
package examples.histo

import org.scalacheck.Properties
import org.scalacheck.Prop._
import cats.Applicative
import cats.Traverse
import cats.syntax.all._
import higherkindness.droste.data.Attr
import higherkindness.droste.data.Fix
import higherkindness.droste.util.DefaultTraverse
import higherkindness.droste.data.prelude._

/** Making change with histomorphisms.
  *
  * Ported from the blog post "Recursion Schemes, Part IV: Time is of the
  * Essence" by Patrick Thomson.
  *
  * https://blog.sumtypeofway.com/recursion-schemes-part-iv-time-is-of-the-essence/
  */
final class MakeChange extends Properties("MakeChange") {
  import MakeChange._

  property("toNat") = {
    val ZeroFixed = Fix(Zero: Nat[Fix[Nat]])

    val a = toNat(0) ?= ZeroFixed
    val b = toNat(1) ?= Fix(Next(ZeroFixed))
    val c = toNat(2) ?= Fix(Next(Fix(Next(ZeroFixed))))
    val d = toNat(3) ?= Fix(Next(Fix(Next(Fix(Next(ZeroFixed))))))

    a && b && c && d
  }

  val solve = scheme.zoo.histo(makeChangeAlgebra)

  // dyna is the same as an ana followed by a histo
  val solveFused = scheme.zoo.dyna(makeChangeAlgebra, toNatCoalgebra)

  property("1 cent solutions") = solve(toNat(1)) ?= Set(Penny :: Nil)

  property("2 cent solutions") = solve(toNat(2)) ?= Set(Penny :: Penny :: Nil)

  property("3 cent solutions") =
    solve(toNat(3)) ?= Set(Penny :: Penny :: Penny :: Nil)

  property("4 cent solutions") =
    solve(toNat(4)) ?= Set(Penny :: Penny :: Penny :: Penny :: Nil)

  property("5 cent solutions") = solve(toNat(5)) ?= Set(
    Penny :: Penny :: Penny :: Penny :: Penny :: Nil,
    Nickle :: Nil
  )

  property("6 cent solutions") = solve(toNat(6)) ?= Set(
    Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Nil,
    Penny :: Nickle :: Nil
  )

  property("7 cent solutions") = solve(toNat(7)) ?= Set(
    Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Nil,
    Penny :: Penny :: Nickle :: Nil
  )

  property("8 cent solutions") = solve(toNat(8)) ?= Set(
    Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Nil,
    Penny :: Penny :: Penny :: Nickle :: Nil
  )

  property("9 cent solutions") = solve(toNat(9)) ?= Set(
    Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Nil,
    Penny :: Penny :: Penny :: Penny :: Nickle :: Nil
  )

  property("10 cent solutions") = solve(toNat(10)) ?= Set(
    Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Nil,
    Penny :: Penny :: Penny :: Penny :: Penny :: Nickle :: Nil,
    Nickle :: Nickle :: Nil,
    Dime :: Nil
  )

  property("11 cent solutions") = solve(toNat(11)) ?= Set(
    Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Nil,
    Penny :: Penny :: Penny :: Penny :: Penny :: Penny :: Nickle :: Nil,
    Penny :: Nickle :: Nickle :: Nil,
    Penny :: Dime :: Nil
  )

  property("num 10 cent solutions") = solveFused(10).size ?= 4

  property("num 25 cent solutions") = solveFused(25).size ?= 13

  property("num 50 cent solutions") = solveFused(50).size ?= 50

  property("num 100 cent solutions") = solveFused(100).size ?= 293

  property("num 200 cent solutions") = solveFused(200).size ?= 2728
}

object MakeChange {

  sealed abstract class Coin(val value: Int)
  case object Penny      extends Coin(1)
  case object Nickle     extends Coin(5)
  case object Dime       extends Coin(10)
  case object Quarter    extends Coin(25)
  case object HalfDollar extends Coin(50)
  case object Dollar     extends Coin(100)

  object Coin {
    implicit val coinOrdering: Ordering[Coin] = Ordering.by(_.value)
  }

  val allCoins: List[Coin] =
    List(Penny, Nickle, Dime, Quarter, HalfDollar, Dollar)

  sealed trait Nat[+A]
  object Nat {
    implicit val traverseForNat: Traverse[Nat] =
      new DefaultTraverse[Nat] {
        def traverse[G[_]: Applicative, A, B](
            fa: Nat[A]
        )(f: A => G[B]): G[Nat[B]] =
          fa match {
            case Zero    => (Zero: Nat[B]).pure[G]
            case Next(a) => f(a).map(Next(_))
          }
      }
  }
  case object Zero               extends Nat[Nothing]
  final case class Next[A](a: A) extends Nat[A]

  val toNatCoalgebra: Coalgebra[Nat, Int] =
    Coalgebra(n => if (n > 0) Next(n - 1) else Zero)

  val toNat: Int => Fix[Nat] =
    scheme.ana(toNatCoalgebra)

  val fromNat: Fix[Nat] => Int =
    scheme[Fix].cata[Nat, Int](Algebra {
      case Next(n) => n + 1
      case Zero    => 0
    })

  def lookup(cache: Attr[Nat, Set[List[Coin]]], n: Int): Set[List[Coin]] =
    if (n == 0) cache.head
    else
      cache.tail match {
        case Next(inner) => lookup(inner, n - 1)
        case Zero        => Set.empty
      }

  val makeChangeAlgebra: CVAlgebra[Nat, Set[List[Coin]]] = CVAlgebra {
    case Next(attr) =>
      val _given = fromNat(attr.forget) + 1
      val validCoins = allCoins
        .takeWhile(_.value <= _given)
        .map(coin => coin -> (_given - coin.value))
      val (zeros, toProcess) = validCoins.span(_._2 == 0)
      val zeroSolutions      = zeros.map(_._1 :: Nil).toSet
      val chainSolutions = toProcess
        .map(tp => lookup(attr, tp._1.value - 1).map(ps => tp._1 :: ps))
        .flatten
        .map(_.sorted)
      zeroSolutions ++ chainSolutions
    case Zero => Set(List.empty)
  }

}
```

This walks a Peano-encoded `Nat` (`Zero`/`Next`), and at each `Next(attr)` step the `CVAlgebra` receives an `Attr[Nat, Set[List[Coin]]]` — the entire chain of previously-computed solution-sets, reachable by walking `.tail` — so it can look back arbitrarily far (`lookup(attr, tp._1.value - 1)`) to find the solution set for `n - coin.value`, not just the immediately preceding one. `scheme.zoo.dyna(makeChangeAlgebra, toNatCoalgebra)` fuses the `Int => Nat` anamorphism with this histomorphism into one pass with no intermediate `Fix[Nat]` ever built.

## `futu` — `examples/futu/ListExchange.scala` (pairwise-swap a list by unfolding two elements at a time)

```scala
package higherkindness.droste
package examples.futu

import org.scalacheck.Properties
import org.scalacheck.Prop._
import higherkindness.droste.data.list._
import higherkindness.droste.data.Coattr

final class ListExchange extends Properties("ListExchange") {

  // TODO: make this more ergonomic to write
  val exchangeCoalgebra: CVCoalgebra[ListF[String, *], List[String]] =
    CVCoalgebra {
      case Nil => NilF
      case head :: tail =>
        tail match {
          case Nil => ConsF(head, Coattr.pure(tail))
          case tailHead :: tailTail =>
            ConsF(
              tailHead,
              Coattr.roll(
                ConsF(
                  head,
                  Coattr.pure[ListF[String, *], List[String]](tailTail)
                )
              )
            )
        }
    }

  val f: List[String] => List[String] =
    scheme.zoo.futu(exchangeCoalgebra)

  property("simple pair wise swap check") = {
    val in: List[String]       = List("a", "b", "c", "d", "e", "f")
    val expected: List[String] = List("b", "a", "d", "c", "f", "e")
    f(in) ?= expected
  }

  property("pair wise swap") = forAll((in: List[String]) =>
    f(in) ?= in.sliding(2, 2).map(_.reverse).toList.flatten
  )

}
```

`Coattr.roll(...)` produces one *extra* layer of unfolding within the same coalgebra step (emitting both swapped elements at once), while `Coattr.pure(tail)` stops early with a plain value — this is exactly the futumorphism's "flexible control over how many layers get unfolded per step" behavior.

## `zygo` — `examples/zygo/PlusMinus.scala` (alternating plus/minus fold, needing a simultaneous even/odd-position algebra)

```scala
package higherkindness.droste.examples.zygo

import org.scalacheck.Properties
import org.scalacheck.Prop._
import higherkindness.droste.Algebra
import higherkindness.droste.RAlgebra
import higherkindness.droste.scheme
import higherkindness.droste.data.list._

final class PlusMinus extends Properties("PlusMinus") {

  val evenAlgebra = Algebra[ListF[Int, *], Boolean] {
    case NilF           => false
    case ConsF(_, bool) => !bool
  }

  val calcRAlgebra = RAlgebra[Boolean, ListF[Int, *], Int] {
    case NilF             => 0
    case ConsF(n, (b, x)) => if (b) n + x else n - x
  }

  // plusMinus(List(a,b,c,d,e)) = a - (b + (c - (d + e)))
  val plusMinus = scheme.zoo.zygo[ListF[Int, *], List[Int], Boolean, Int](
    evenAlgebra,
    calcRAlgebra
  )

  property("plus-minus of empty list") = plusMinus(List()) ?= 0

  property("plus-minus of increasing list") =
    plusMinus(List(1, 2, 3, 4, 5)) ?= 5

  property("plus-minus of alternating list") =
    plusMinus(List(1, -1, 1, -1, 1, -1)) ?= 0

}
```

`evenAlgebra` computes, at every position, whether that position is even/odd (a simple bottom-up boolean fold); `calcRAlgebra` is the *dependent* second algebra — its `ConsF(n, (b, x))` pattern receives both that boolean `b` (from the first algebra, at the same recursive step) and the second algebra's own already-folded result `x`, letting it alternate `+`/`-` based on position parity in a single traversal pass.

## `prepro` — `examples/pro/SmallPre.scala` (preprocess/filter the structure with a natural transformation before folding)

```scala
package higherkindness.droste.examples.pro

import org.scalacheck.Properties
import org.scalacheck.Prop._
import cats.~>
import higherkindness.droste.Algebra
import higherkindness.droste.scheme
import higherkindness.droste.data.list._

final class SmallPre extends Properties("SmallPre") {

  def filterNT(lim: Int): ListF[Int, *] ~> ListF[Int, *] =
    new (ListF[Int, *] ~> ListF[Int, *]) {
      def apply[A](l: ListF[Int, A]): ListF[Int, A] = l match {
        case NilF                        => NilF
        case t @ ConsF(h, _) if h <= lim => t
        case ConsF(_, _)                 => NilF
      }
    }

  val sumAlg = Algebra[ListF[Int, *], Int] {
    case ConsF(h, t) => h + t
    case NilF        => 0
  }

  val smallSum =
    scheme.zoo.prepro[ListF[Int, *], List[Int], Int](filterNT(10), sumAlg)

  property("empty sum") = smallSum(Nil) ?= 0

  property("small sum") = smallSum(List(1, 2, 3)) ?= 6

  property("mixed sum") = smallSum((1 to 100).toList) ?= 55

}
```

`filterNT` truncates the list (turns any element `> lim` into `NilF`) before `sumAlg` ever sees it — the natural transformation `F ~> F` runs at every layer during the fold, ahead of the algebra, so it can prune/rewrite structure on the way down without a separate traversal pass.

## `Trans`/`TransM` (natural-transformation-shaped, not a full scheme by itself) — `examples/trans/demo.scala` (convert `List` <-> `NonEmptyList` pattern functors)

```scala
package higherkindness.droste
package examples.trans

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Properties
import org.scalacheck.Prop._
import cats.Applicative
import cats.Traverse
import cats.data.NonEmptyList
import cats.syntax.all._
import higherkindness.droste.data.Fix
import higherkindness.droste.data.list._
import higherkindness.droste.util.DefaultTraverse

final class TransDemo extends Properties("TransDemo") {
  import TransDemo._

  property("empty list to NelF fails") =
    toNelF(Fix[ListF[Int, *]](NilF)) ?= None

  property("round trip NelF") = {
    forAll { (nel: NonEmptyList[Int]) =>
      val listF = ListF.fromScalaList(nel.toList)
      toNelF(listF).map(fromNelF) ?= Some(listF)
    }
  }

}

object TransDemo {

  // non empty variant of ListF

  sealed trait NeListF[A, B]
  final case class NeLastF[A, B](value: A)         extends NeListF[A, B]
  final case class NeConsF[A, B](head: A, tail: B) extends NeListF[A, B]

  implicit def drosteTraverseForNeListF[A]: Traverse[NeListF[A, *]] =
    new DefaultTraverse[NeListF[A, *]] {
      def traverse[F[_]: Applicative, B, C](
          fb: NeListF[A, B]
      )(f: B => F[C]): F[NeListF[A, C]] =
        fb match {
          case NeConsF(head, tail) => f(tail).map(NeConsF(head, _))
          case NeLastF(value)      => (NeLastF(value): NeListF[A, C]).pure[F]
        }
    }

  // converting a list to a non-empty list can fail, so we use TransM
  def transListToNeList[A]: TransM[Option, ListF[A, *], NeListF[A, *], Fix[
    ListF[A, *]
  ]] = TransM {
    case ConsF(head, tail) =>
      Fix.un(tail) match {
        case NilF => NeLastF(head).some
        case _    => NeConsF(head, tail).some
      }
    case NilF => None
  }

  def toNelF[A]: Fix[ListF[A, *]] => Option[Fix[NeListF[A, *]]] =
    scheme.anaM(transListToNeList[A].coalgebra)

  // converting a non-empty list to a list can't fail, so we use Trans
  def transNeListToList[A]: Trans[NeListF[A, *], ListF[A, *], Fix[
    ListF[A, *]
  ]] = Trans {
    case NeConsF(head, tail) => ConsF(head, tail)
    case NeLastF(last)       => ConsF(last, Fix[ListF[A, *]](NilF))
  }

  def fromNelF[A]: Fix[NeListF[A, *]] => Fix[ListF[A, *]] =
    scheme.cata(transNeListToList[A].algebra)

  // misc

  implicit def arbitraryNEL[A: Arbitrary]: Arbitrary[NonEmptyList[A]] =
    Arbitrary(for {
      head <- arbitrary[A]
      tail <- arbitrary[List[A]]
    } yield NonEmptyList.of(head, tail: _*))

}
```

`Trans[F, G, A] = F[A] => G[A]` (and `TransM` its monadic form) is droste's tool for rewriting *between two different pattern functors* over the same carrier type, reusing `.coalgebra`/`.algebra` to plug straight into `scheme.anaM`/`scheme.cata`.

## `SchemeEquivalence.scala` — proof that `zoo.histo`, `gcata` + `Gather.histo`, and `ghylo` + `Gather.histo`/`Scatter.ana` are the same function

```scala
package higherkindness.droste
package tests

import cats.Functor
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalacheck.Properties
import org.scalacheck.Prop._
import examples.histo.MakeChange

final class SchemeEquivalence extends Properties("SchemeEquivalence") {

  // TODO: see about generalizing this for testing more scheme equivalences
  trait AlgebraFamily {
    type F[_]
    type R
    type B

    object implicits {
      implicit def implicitFunctorF: Functor[F]     = functorF
      implicit def implicitProjectFR: Project[F, R] = projectFR
      implicit def implicitArbitraryR: Arbitrary[R] = arbitraryR
    }

    def functorF: Functor[F]
    def projectFR: Project[F, R]
    def arbitraryR: Arbitrary[R]

    def cvalgebra: CVAlgebra[F, B]
  }

  object AlgebraFamily {
    case class Default[FF[_], RR, BB](
        genR: Gen[RR],
        cvalgebra: CVAlgebra[FF, BB]
    )(implicit
        val functorF: Functor[FF],
        val projectFR: Project[FF, RR]
    ) extends AlgebraFamily {
      type F[A] = FF[A]
      type R    = RR
      type B    = BB

      val arbitraryR = Arbitrary(genR)
    }
  }

  implicit val arbitraryAlgebraFamily: Arbitrary[AlgebraFamily] =
    Arbitrary(
      Gen.const(
        AlgebraFamily.Default(
          Gen.choose(0, 50).map(MakeChange.toNat),
          MakeChange.makeChangeAlgebra
        )
      )
    )

  property("histo") = {

    forAll { (z: AlgebraFamily) =>
      import z.implicits._

      val f = scheme.zoo.histo(z.cvalgebra)
      val g = scheme.gcata(z.cvalgebra.gather(Gather.histo))
      val h = scheme.ghylo(
        z.cvalgebra.gather(Gather.histo),
        z.projectFR.coalgebra.scatter(Scatter.ana)
      )

      forAll { (r: z.R) =>
        val x = f(r)
        val y = g(r)
        val z = h(r)

        (x ?= y) && (x ?= z)
      }
    }
  }

}
```

This is direct evidence, from droste's own test suite, that every named scheme in `scheme.zoo` is nothing more than a convenience wrapper around `scheme.ghylo`/`scheme.gcata`/`scheme.gana` with a specific `Gather`/`Scatter` plugged in — `scheme.zoo.histo(alg) == scheme.gcata(alg.gather(Gather.histo)) == scheme.ghylo(alg.gather(Gather.histo), coalgebra.scatter(Scatter.ana))`.
