# Droste Source: `basis.scala` (Embed/Project/Basis) + `data/Mu.scala` + `data/Nu.scala` + `data/Coattr.scala`

> Source: https://github.com/higherkindness/droste (main branch, `modules/core/src/main/scala/higherkindness/droste/basis.scala`, `modules/core/src/main/scala/higherkindness/droste/data/Mu.scala`, `modules/core/src/main/scala/higherkindness/droste/data/Nu.scala`, `modules/core/src/main/scala-3/higherkindness/droste/data/Coattr.scala`)
> Collected: 2026-09-13
> Published: Unknown

## `basis.scala` — `Embed`, `Project`, `Basis`, and `Basis.Solve`

```scala
package higherkindness.droste

import higherkindness.droste.data.Attr
import higherkindness.droste.data.AttrF
import higherkindness.droste.data.Coattr
import higherkindness.droste.data.CoattrF
import higherkindness.droste.data.Fix
import higherkindness.droste.util.newtypes._
import cats.Eval
import cats.Eq
import cats.Foldable
import cats.Functor
import cats.Monad
import cats.Monoid
import cats.free.Trampoline
import higherkindness.droste.data.list.ListF
import higherkindness.droste.data.list.NilF
import higherkindness.droste.data.list.ConsF
import higherkindness.droste.data.prelude._

trait Embed[F[_], R] {
  def algebra: Algebra[F, R]
}

object Embed extends FloatingBasisInstances[Embed] {
  def apply[F[_], R](implicit ev: Embed[F, R]): Embed[F, R] = ev
}

trait Project[F[_], R] { self =>
  def coalgebra: Coalgebra[F, R]

  implicit val tc: Project[F, R] = self

  def all(r: R)(p: R => Boolean)(implicit F: Foldable[F]): Boolean =
    Project[F, R].all(r)(p)

  def any(r: R)(p: R => Boolean)(implicit F: Foldable[F]): Boolean =
    Project.any[F, R](r)(p)

  def collect[U: Monoid, B](r: R)(
      pf: PartialFunction[R, B]
  )(implicit U: Basis[ListF[B, *], U], F: Foldable[F]): U =
    Project.collect[F, R, U, B](r)(pf)

  def contains(r: R, c: R)(implicit R: Eq[R], F: Foldable[F]): Boolean =
    Project.contains[F, R](r, c)(tc, R, F)

  def foldMap[Z: Monoid](r: R)(f: R => Z)(implicit F: Foldable[F]): Z =
    Project.foldMap[F, R, Z](r)(f)

  def foldMapM[M[_], Z](r: R)(
      f: R => M[Z]
  )(implicit M: Monad[M], Z: Monoid[Z], F: Foldable[F]): M[Z] =
    Project.foldMapM[F, M, R, Z](r)(f)(tc, M, Z, F)

}

object Project extends FloatingBasisInstances[Project] {
  def apply[F[_], R](implicit ev: Project[F, R]): Project[F, R] = ev

  def all[F[_], R](r: R)(
      p: R => Boolean
  )(implicit P: Project[F, R], F: Foldable[F]): Boolean =
    foldMap[F, R, Boolean @@ Tags.Conjunction](r)(p(_).conjunction).unwrap

  def any[F[_], R](r: R)(
      p: R => Boolean
  )(implicit P: Project[F, R], F: Foldable[F]): Boolean =
    foldMap[F, R, Boolean @@ Tags.Disjunction](r)(p(_).disjunction).unwrap

  def collect[F[_], R, U: Monoid, B](r: R)(
      pf: PartialFunction[R, B]
  )(implicit P: Project[F, R], U: Basis[ListF[B, *], U], F: Foldable[F]): U =
    foldMap[F, R, U](r)(
      pf.lift(_)
        .foldRight[U](U.algebra(NilF))((a, b) => U.algebra(ConsF(a, b)))
    )

  def contains[F[_], R](r: R, c: R)(implicit
      P: Project[F, R],
      R: Eq[R],
      F: Foldable[F]
  ): Boolean =
    any(r)(R.eqv(c, _))(P, F)

  def foldMap[F[_], R, Z: Monoid](r: R)(
      f: R => Z
  )(implicit P: Project[F, R], F: Foldable[F]): Z =
    foldMapM[F, Trampoline, R, Z](r)(x => Trampoline.done(f(x)))(
      P,
      Monad[Trampoline],
      Monoid[Z],
      F
    ).run

  def foldMapM[F[_], M[_], R, Z](r: R)(f: R => M[Z])(implicit
      P: Project[F, R],
      M: Monad[M],
      Z: Monoid[Z],
      F: Foldable[F]
  ): M[Z] = {
    def loop(z0: Z, term: R): M[Z] =
      M.flatMap(f(term)) { z1 =>
        F.foldLeftM(P.coalgebra(term), Z.combine(z0, z1))(loop(_, _))
      }

    loop(Z.empty, r)
  }
}

sealed trait Basis[F[_], R] extends Embed[F, R] with Project[F, R]

object Basis extends FloatingBasisInstances[Basis] {
  def apply[F[_], R](implicit ev: Basis[F, R]): Basis[F, R] = ev
  final case class Default[F[_], R](
      algebra: Algebra[F, R],
      coalgebra: Coalgebra[F, R]
  ) extends Basis[F, R]

  sealed trait Solve[PR[_[_]]] {
    type PatF[F[_], A]
    type PatR[F[_]] = PR[F]
  }

  object Solve extends FloatingBasisSolveInstances {
    type Aux[PR[_[_]], PF[_[_], _]] = Solve[PR] {
      type PatF[F[_], A] = PF[F, A]
    }
  }
}

private[droste] sealed trait FloatingBasisInstances[H[F[_], A] >: Basis[F, A]]
    extends FloatingBasisInstances0[H] {
  implicit def drosteBasisForListF[A]: H[ListF[A, *], List[A]] =
    Basis.Default[ListF[A, *], List[A]](
      ListF.toScalaListAlgebra,
      ListF.fromScalaListCoalgebra
    )

  implicit def drosteBasisForAttr[F[_], A]: H[AttrF[F, A, *], Attr[F, A]] =
    Basis.Default[AttrF[F, A, *], Attr[F, A]](Attr.algebra, Attr.coalgebra)

  implicit def drosteBasisForCoattr[F[_], A]: H[
    CoattrF[F, A, *],
    Coattr[F, A]
  ] =
    Basis
      .Default[CoattrF[F, A, *], Coattr[F, A]](Coattr.algebra, Coattr.coalgebra)
}

private[droste] sealed trait FloatingBasisInstances0[
    H[F[_], A] >: Basis[F, A]
] {
  implicit def drosteBasisForFix[F[_]]: H[F, Fix[F]] =
    Basis.Default[F, Fix[F]](Fix.algebra, Fix.coalgebra)

  implicit def drosteBasisForCatsCofree[F[_], A]: H[
    AttrF[F, A, *],
    cats.free.Cofree[F, A]
  ] =
    Basis.Default[AttrF[F, A, *], cats.free.Cofree[F, A]](
      Algebra(fa => cats.free.Cofree(fa.ask, Eval.now(fa.lower))),
      Coalgebra(a => AttrF(a.head, a.tailForced))
    )

  implicit def drosteBasisForCatsFree[F[_]: Functor, A]: H[
    CoattrF[F, A, *],
    cats.free.Free[F, A]
  ] =
    Basis.Default[CoattrF[F, A, *], cats.free.Free[F, A]](
      Algebra {
        CoattrF.un(_).fold(cats.free.Free.pure, cats.free.Free.roll)
      },
      Coalgebra {
        _.fold[CoattrF[F, A, cats.free.Free[F, A]]](CoattrF.pure, CoattrF.roll)
      }
    )
}

private[droste] sealed trait FloatingBasisSolveInstances {
  import Basis.Solve

  implicit val drosteSolveFix: Solve.Aux[Fix, ({ type L[F[_], A] = F[A] })#L] =
    null
  implicit def drosteSolveAttr[A]: Solve.Aux[
    ({ type L[F[_]] = Attr[F, A] })#L,
    ({ type L[F[_], B] = AttrF[F, A, B] })#L
  ] =
    null
  implicit def drosteSolveCatsCofree[A]: Solve.Aux[
    ({ type L[F[_]] = cats.free.Cofree[F, A] })#L,
    ({ type L[F[_], B] = AttrF[F, A, B] })#L
  ] =
    null

  implicit def drosteSolveCatsFree[A]: Solve.Aux[
    ({ type L[F[_]] = cats.free.Free[F, A] })#L,
    ({ type L[F[_], B] = CoattrF[F, A, B] })#L
  ] =
    null
}
```

`Embed[F, R]` supplies just an `algebra: Algebra[F, R]` (construct `R` from one `F` layer). `Project[F, R]` supplies just a `coalgebra: Coalgebra[F, R]` (deconstruct `R` into one `F` layer) plus derived traversal helpers (`all`, `any`, `collect`, `contains`, `foldMap`, `foldMapM` — all implemented via `foldMapM`'s trampolined loop, so they're stack-safe over arbitrarily deep structures). `Basis[F, R]` is simply `Embed[F, R] with Project[F, R]` — both directions at once, as produced by `Basis.Default(algebra, coalgebra)`.

Droste ships built-in `Basis` instances for `List` (via `ListF`), `Attr`, `Coattr`, `Fix`, `cats.free.Cofree` (via `AttrF`), and `cats.free.Free` (via `CoattrF`) — so those recursive types work with every scheme out of the box, with no explicit `Basis` needed.

`Basis.Solve[PR[_[_]]]` is what lets `scheme.apply[PatR[_[_]]]` (i.e. `scheme[Fix]`, `scheme[Mu]`, ...) infer the associated pattern-functor shape `PatF` for a given fixpoint constructor `PatR`, without the caller spelling it out — `Basis.Solve.Aux[Fix, ({type L[F[_],A]=F[A]})#L]` says "for `Fix`, the pattern functor of `F` applied to a carrier `A` is just `F[A]` itself," while the `Aux` for `Attr`/`Cofree` binds the pattern functor to `AttrF[F, A, *]` instead, reflecting that those types carry an extra annotation parameter `A`.

## `data/Mu.scala` — least fixed point (inductive/finite data)

```scala
package higherkindness.droste
package data

import cats.~>
import cats.Functor
import cats.Id
import cats.syntax.functor._

/** Mu is the least fixed point of a functor `F`. It is a computation that can
  * consume a inductive noninfinite structure in one go.
  *
  * In Haskell this can more aptly be expressed as: `data Mu f = Mu (forall x .
  * (f x -> x) -> x)`
  */
sealed abstract class Mu[F[_]] extends Serializable {
  def apply[A](fold: Algebra[F, A]): A

  def toFunctionK: Algebra[F, *] ~> Id =
    new (Algebra[F, *] ~> Id) {
      def apply[A](fa: Algebra[F, A]): Id[A] = Mu.this.apply(fa)
    }
}

object Mu {
  def algebra[F[_]: Functor]: Algebra[F, Mu[F]] =
    Algebra(fmf => Default(fmf))

  def coalgebra[F[_]: Functor]: Coalgebra[F, Mu[F]] =
    Coalgebra[F, Mu[F]](mf => mf[F[Mu[F]]](Algebra(_ map algebra.run)))

  def apply[F[_]: Functor](fmf: F[Mu[F]]): Mu[F] = algebra[F].apply(fmf)
  def un[F[_]: Functor](mf: Mu[F]): F[Mu[F]]     = coalgebra[F].apply(mf)

  def unapply[F[_]: Functor](mf: Mu[F]): Some[F[Mu[F]]] = Some(un(mf))

  private final case class Default[F[_]: Functor](fmf: F[Mu[F]]) extends Mu[F] {
    def apply[A](fold: Algebra[F, A]): Id[A] =
      fold(fmf map (mf => mf(fold)))

    override def toString: String = s"Mu($fmf)"
  }

  implicit def drosteBasisForMu[F[_]: Functor]: Basis[F, Mu[F]] =
    Basis.Default[F, Mu[F]](Mu.algebra, Mu.coalgebra)

  implicit val drosteBasisSolveForMu: Basis.Solve.Aux[
    Mu,
    ({ type L[F[_], A] = F[A] })#L
  ] = null
}
```

`Mu[F]` represents "a value together with the fold that consumes it" (Church-encoded: `Mu[F]` literally *is* a function `Algebra[F, A] => A` for any `A`) — because the encoding forces total consumption, `Mu` can only represent finite/inductive structures.

## `data/Nu.scala` — greatest fixed point (coinductive/potentially-infinite codata)

```scala
package higherkindness.droste
package data

import cats.Functor
import cats.syntax.functor._

/** Nu is the greatest fixed point of a functor `F`. It is a computation that
  * can generate a coinductive infinite structure on demand.
  *
  * In Haskell this can more aptly be expressed as: `data Nu g = forall s . Nu
  * (s -> g s) s`
  */
sealed abstract class Nu[F[_]] extends Serializable {
  type A
  def unfold: Coalgebra[F, A]
  def a: A

  override final def toString: String = s"Nu($unfold, $a)"
}

object Nu {
  def apply[F[_], A](unfold0: Coalgebra[F, A], a0: A): Nu[F] =
    new Default(unfold0, a0)

  def algebra[F[_]: Functor]: Algebra[F, Nu[F]] =
    Algebra(t => Nu(Coalgebra[F, F[Nu[F]]](_ map coalgebra.run), t))

  def coalgebra[F[_]: Functor]: Coalgebra[F, Nu[F]] =
    Coalgebra(nf => nf.unfold(nf.a).map(Nu(nf.unfold, _)))

  def apply[F[_]: Functor](fnf: F[Nu[F]]): Nu[F] = algebra[F].apply(fnf)
  def un[F[_]: Functor](nf: Nu[F]): F[Nu[F]]     = coalgebra[F].apply(nf)

  def unapply[F[_]: Functor](nf: Nu[F]): Some[F[Nu[F]]] = Some(un(nf))

  private final class Default[F[_], A0](val unfold: Coalgebra[F, A0], val a: A0)
      extends Nu[F] {
    type A = A0
  }

  implicit def drosteBasisForNu[F[_]: Functor]: Basis[F, Nu[F]] =
    Basis.Default[F, Nu[F]](Nu.algebra, Nu.coalgebra)

  implicit val drosteBasisSolveForNu: Basis.Solve.Aux[
    Nu,
    ({ type L[F[_], A] = F[A] })#L
  ] = null
}
```

`Nu[F]` bundles an existential seed `a: A` with a `Coalgebra[F, A]` — it represents "a value together with the unfold that can regenerate it on demand, one layer at a time," which is why it can model infinite/coinductive structures without ever needing to fully materialize them (the droste `NuLookup` worked example unfolds a `Nu[Result]` from a lazy string-keyed lookup table).

## `data/Coattr.scala` (Scala 3 variant) — the free-monad-like carrier behind `CVCoalgebra`/`futu`

```scala
package higherkindness.droste
package data

import cats.Functor
import cats.syntax.functor._

object Coattr {
  def apply[F[_], A](f: Either[A, F[Coattr[F, A]]]): Coattr[F, A] =
    f.asInstanceOf
  def un[F[_], A](f: Coattr[F, A]): Either[A, F[Coattr[F, A]]] =
    f.asInstanceOf

  def pure[F[_], A](a: A): Coattr[F, A]                = apply(Left(a))
  def roll[F[_], A](fa: F[Coattr[F, A]]): Coattr[F, A] = apply(Right(fa))

  def algebra[F[_], A]: Algebra[CoattrF[F, A, *], Coattr[F, A]] =
    Algebra(fa => Coattr(CoattrF.un(fa)))

  def coalgebra[F[_], A]: Coalgebra[CoattrF[F, A, *], Coattr[F, A]] =
    Coalgebra(a => CoattrF(Coattr.un(a)))

  def fromCats[F[_]: Functor, A](free: cats.free.Free[F, A]): Coattr[F, A] =
    free.fold(pure, ffree => roll(ffree.map(fromCats(_))))

  object Pure {
    def unapply[F[_], A](f: Coattr[F, A]): Option[A] = un(f) match {
      case Left(a) => Some(a)
      case _       => None
    }
  }

  object Roll {
    def unapply[F[_], A](f: Coattr[F, A]): Option[F[Coattr[F, A]]] =
      un(f) match {
        case Right(fa) => Some(fa)
        case _         => None
      }
  }
}

trait CoattrImplicits {
  implicit final class CoattrOps[F[_], A](coattr: Coattr[F, A]) {
    def fold[B](f: A => B, fffa: F[Coattr[F, A]] => B): B =
      Coattr.un(coattr).fold(f, fffa)

    def toCats(implicit ev: Functor[F]): cats.free.Free[F, A] =
      fold(
        cats.free.Free.pure,
        fcoattr => cats.free.Free.roll(fcoattr.map(_.toCats))
      )
  }
}
```

`Coattr[F, A]` is literally `Either[A, F[Coattr[F, A]]]` under the hood: `Coattr.pure(a)` (`Left(a)`) stops right there with a final value, `Coattr.roll(fa)` (`Right(fa)`) continues with one more `F` layer to unfold later. This is exactly the two moves a `CVCoalgebra`'s `futu` step gets to make at every position — stop early with `.pure`, or unfold an extra layer immediately with `.roll` (as seen in the `ListExchange` futumorphism worked example, which uses both in the same coalgebra).
