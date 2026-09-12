# Droste Source: Algebra/Coalgebra Type Taxonomy (`package.scala` + `algebras.scala`)

> Source: https://github.com/higherkindness/droste (main branch, `modules/core/src/main/scala/higherkindness/droste/package.scala` and `modules/core/src/main/scala/higherkindness/droste/algebras.scala`)
> Collected: 2026-09-12
> Published: Unknown

## `package.scala` — every algebra/coalgebra type alias droste defines

```scala
package higherkindness.droste

import cats.~>
import cats.Eq
import higherkindness.droste.data.Attr
import higherkindness.droste.data.Coattr
import higherkindness.droste.syntax.compose._

object `package` {

  type Algebra[F[_], A]   = GAlgebra[F, A, A]
  type Coalgebra[F[_], A] = GCoalgebra[F, A, A]

  type AlgebraM[M[_], F[_], A]   = GAlgebraM[M, F, A, A]
  type CoalgebraM[M[_], F[_], A] = GCoalgebraM[M, F, A, A]

  type RAlgebra[R, F[_], A]   = GAlgebra[F, (R, A), A]
  type RCoalgebra[R, F[_], A] = GCoalgebra[F, A, Either[R, A]]

  type RAlgebraM[R, M[_], F[_], A]   = GAlgebraM[M, F, (R, A), A]
  type RCoalgebraM[R, M[_], F[_], A] = GCoalgebraM[M, F, A, Either[R, A]]

  type CVAlgebra[F[_], A]   = GAlgebra[F, Attr[F, A], A]
  type CVCoalgebra[F[_], A] = GCoalgebra[F, A, Coattr[F, A]]

  type Gather[F[_], S, A]  = (A, F[S]) => S
  type Scatter[F[_], A, S] = S => Either[A, F[S]]

  object Algebra {
    def apply[F[_], A](f: F[A] => A): Algebra[F, A] = GAlgebra(f)
  }

  object Coalgebra {
    def apply[F[_], A](f: A => F[A]): Coalgebra[F, A] = GCoalgebra(f)
  }

  object AlgebraM {
    def apply[M[_], F[_], A](f: F[A] => M[A]): AlgebraM[M, F, A] = GAlgebraM(f)
  }

  object CoalgebraM {
    def apply[M[_], F[_], A](f: A => M[F[A]]): CoalgebraM[M, F, A] =
      GCoalgebraM(f)
  }

  object RAlgebra {
    def apply[R, F[_], A](f: F[(R, A)] => A): RAlgebra[R, F, A] = GAlgebra(f)
  }

  object RCoalgebra {
    def apply[R, F[_], A](f: A => F[Either[R, A]]): RCoalgebra[R, F, A] =
      GCoalgebra(f)
  }

  object RAlgebraM {
    def apply[R, M[_], F[_], A](f: F[(R, A)] => M[A]): RAlgebraM[R, M, F, A] =
      GAlgebraM(f)
  }

  object RCoalgebraM {
    def apply[R, M[_], F[_], A](
        f: A => M[F[Either[R, A]]]
    ): RCoalgebraM[R, M, F, A] =
      GCoalgebraM(f)
  }

  object CVAlgebra {
    def apply[F[_], A](f: F[Attr[F, A]] => A): CVAlgebra[F, A] = GAlgebra(f)
  }

  object CVCoalgebra {
    def apply[F[_], A](f: A => F[Coattr[F, A]]): CVCoalgebra[F, A] =
      GCoalgebra(f)
  }

  type Trans[F[_], G[_], A]        = GTrans[F, G, A, A]
  type TransM[M[_], F[_], G[_], A] = GTransM[M, F, G, A, A]

  object Trans {
    def apply[F[_], G[_], A](f: F[A] => G[A]): Trans[F, G, A] = GTrans(f)
  }

  object TransM {
    def apply[M[_], F[_], G[_], A](f: F[A] => M[G[A]]): TransM[M, F, G, A] =
      GTransM(f)
  }

  type Delay[F[_], G[_]] = F ~> (F ∘ G)#λ
}

object prelude {
  implicit def drosteDelayedEq[Z, F[_]](implicit
      p: Project[F, Z],
      delay: Delay[Eq, F]
  ): Eq[Z] = {
    lazy val knot: Eq[Z] =
      Eq.instance((x, y) => delay(knot).eqv(p.coalgebra(x), p.coalgebra(y)))
    knot
  }

  // todo: where should this live?
  implicit val drosteDelayEqOption: Delay[Eq, Option] =
    new (Eq ~> (Eq ∘ Option)#λ) {
      def apply[A](eq: Eq[A]): (Eq ∘ Option)#λ[A] =
        Eq.instance((x, y) =>
          x.fold(y.fold(true)(_ => false))(xx =>
            y.fold(false)(yy => eq.eqv(xx, yy))
          )
        )
    }
}
```

This file is where every named algebra/coalgebra type in droste is defined as a specialization of the two generalized types `GAlgebra[F, S, A]` (`F[S] => A`) and `GCoalgebra[F, A, S]` (`A => F[S]`):

- `Algebra[F, A] = GAlgebra[F, A, A]` — the plain F-algebra, `F[A] => A`.
- `Coalgebra[F, A] = GCoalgebra[F, A, A]` — the plain F-coalgebra, `A => F[A]`.
- `AlgebraM` / `CoalgebraM` — monadic (Kleisli-style) versions returning `M[A]` / `M[F[A]]`.
- `RAlgebra[R, F, A] = GAlgebra[F, (R, A), A]` — an algebra that additionally receives the original recursive value `R` alongside the folded result `A` at each position (used for paramorphisms).
- `RCoalgebra[R, F, A] = GCoalgebra[F, A, Either[R, A]]` — a coalgebra that can short-circuit a branch by returning `Left[R]` instead of continuing to unfold (used for apomorphisms).
- `RAlgebraM` / `RCoalgebraM` — monadic versions of the above.
- `CVAlgebra[F, A] = GAlgebra[F, Attr[F, A], A]` — an algebra that sees the full `Attr` (cofree comonad / annotated tree) of previously-computed results at every child, not just the immediate one (used for histomorphisms).
- `CVCoalgebra[F, A] = GCoalgebra[F, A, Coattr[F, A]]` — a coalgebra producing a `Coattr` (free monad) that can either continue unfolding or stop with a final value part-way through a single step (used for futumorphisms).
- `Gather[F, S, A] = (A, F[S]) => S` and `Scatter[F, A, S] = S => Either[A, F[S]]` — the building blocks used by the *generalized* hylomorphism (`scheme.ghylo`) to plug in the "how do I merge/split one step" behavior for any named scheme (`Gather.cata`, `Gather.para`, `Gather.zygo`, `Gather.histo`; `Scatter.ana`, `Scatter.apo`, `Scatter.gapo`).
- `Trans` / `TransM` — natural-transformation-shaped operations (`F[A] => G[A]`), used by `prepro`/`postpro`.

Note the `Attr`/`Coattr` types used in `CVAlgebra`/`CVCoalgebra` — `Attr[F, A]` is droste's cofree-comonad-like annotated-tree type (a value of type `A` at the root plus an `F[Attr[F, A]]` of annotated children), and `Coattr[F, A]` is droste's free-monad-like type (either a raw `A` leaf or one more `F` layer to unfold).

## `algebras.scala` — the two generalized primitives all the named types specialize

```scala
package higherkindness.droste

import cats.Applicative
import cats.CoflatMap
import cats.Comonad
import cats.FlatMap
import cats.Functor
import cats.Monad
import cats.Semigroupal
import cats.arrow.Arrow
import cats.data.Cokleisli
import cats.data.Kleisli
import cats.syntax.all._

final class GAlgebra[F[_], S, A](val run: F[S] => A) extends AnyVal {
  def apply(fs: F[S]): A =
    run(fs)

  def zip[T, B](that: GAlgebra[F, T, B])(implicit
      ev: Functor[F]
  ): GAlgebra[F, (S, T), (A, B)] =
    GAlgebra.zip(this, that)

  def gather(gather: Gather[F, S, A]): GAlgebra.Gathered[F, S, A] =
    GAlgebra.Gathered(this, gather)

  def lift[M[_]](implicit M: Applicative[M]): GAlgebraM[M, F, S, A] =
    GAlgebraM(fs => run(fs).pure[M])

  def compose[Z](f: GAlgebra[F, Z, S])(implicit
      F: CoflatMap[F]
  ): GAlgebra[F, Z, A] =
    GAlgebra(fz => run(fz coflatMap f.run))

  def andThen[B](f: GAlgebra[F, A, B])(implicit
      F: CoflatMap[F]
  ): GAlgebra[F, S, B] =
    f compose this

  def toCokleisli: Cokleisli[F, S, A] =
    Cokleisli(run)
}

object GAlgebra extends GAlgebraInstances {

  def apply[F[_], S, A](run: F[S] => A): GAlgebra[F, S, A] =
    new GAlgebra(run)

  def zip[F[_]: Functor, Sx, Sy, Ax, Ay](
      x: GAlgebra[F, Sx, Ax],
      y: GAlgebra[F, Sy, Ay]
  ): GAlgebra[F, (Sx, Sy), (Ax, Ay)] =
    GAlgebra(fz => (x(fz.map(v => v._1)), y(fz.map(v => v._2))))

  final case class Gathered[F[_], S, A](
      algebra: GAlgebra[F, S, A],
      gather: Gather[F, S, A]
  ) {
    def zip[B, T](
        that: Gathered[F, T, B]
    )(implicit ev: Functor[F]): Gathered[F, (S, T), (A, B)] =
      Gathered(
        GAlgebra.zip(algebra, that.algebra),
        Gather.zip(gather, that.gather)
      )
  }
}

final class GAlgebraM[M[_], F[_], S, A](val run: F[S] => M[A]) extends AnyVal {
  def apply(fs: F[S]): M[A] =
    run(fs)

  def zip[T, B](that: GAlgebraM[M, F, T, B])(implicit
      M: Semigroupal[M],
      F: Functor[F]
  ): GAlgebraM[M, F, (S, T), (A, B)] =
    GAlgebraM.zip(this, that)

  def gather(gather: Gather[F, S, A]): GAlgebraM.Gathered[M, F, S, A] =
    GAlgebraM.Gathered(this, gather)
}

object GAlgebraM {
  def apply[M[_], F[_], S, A](run: F[S] => M[A]): GAlgebraM[M, F, S, A] =
    new GAlgebraM(run)

  def zip[M[_]: Semigroupal, F[_]: Functor, Sx, Sy, Ax, Ay](
      x: GAlgebraM[M, F, Sx, Ax],
      y: GAlgebraM[M, F, Sy, Ay]
  ): GAlgebraM[M, F, (Sx, Sy), (Ax, Ay)] =
    GAlgebraM(fz => x(fz.map(v => v._1)) product y(fz.map(v => v._2)))

  final case class Gathered[M[_], F[_], S, A](
      algebra: GAlgebraM[M, F, S, A],
      gather: Gather[F, S, A]
  ) {
    def zip[B, T](that: Gathered[M, F, T, B])(implicit
        M: Semigroupal[M],
        F: Functor[F]
    ): Gathered[M, F, (S, T), (A, B)] =
      Gathered(
        GAlgebraM.zip(algebra, that.algebra),
        Gather.zip(gather, that.gather)
      )
  }
}

final class GCoalgebra[F[_], A, S](val run: A => F[S]) extends AnyVal {
  def apply(a: A): F[S] =
    run(a)

  def scatter(scatter: Scatter[F, A, S]): GCoalgebra.Scattered[F, A, S] =
    GCoalgebra.Scattered(this, scatter)

  def lift[M[_]](implicit M: Applicative[M]): GCoalgebraM[M, F, A, S] =
    GCoalgebraM(a => run(a).pure[M])

  def compose[Z](f: GCoalgebra[F, Z, A])(implicit
      F: FlatMap[F]
  ): GCoalgebra[F, Z, S] =
    GCoalgebra(z => f(z) flatMap run)

  def andThen[T](f: GCoalgebra[F, S, T])(implicit
      F: FlatMap[F]
  ): GCoalgebra[F, A, T] =
    f compose this

  def toKleisli: Kleisli[F, A, S] =
    Kleisli(run)
}

object GCoalgebra extends GCoalgebraInstances {
  def apply[F[_], A, S](run: A => F[S]): GCoalgebra[F, A, S] =
    new GCoalgebra(run)

  final case class Scattered[F[_], A, S](
      coalgebra: GCoalgebra[F, A, S],
      scatter: Scatter[F, A, S]
  )
}

final class GCoalgebraM[M[_], F[_], A, S](val run: A => M[F[S]])
    extends AnyVal {
  def apply(a: A): M[F[S]] =
    run(a)

  def scatter(scatter: Scatter[F, A, S]): GCoalgebraM.Scattered[M, F, A, S] =
    GCoalgebraM.Scattered(this, scatter)
}

object GCoalgebraM {
  def apply[M[_], F[_], S, A](run: A => M[F[S]]): GCoalgebraM[M, F, A, S] =
    new GCoalgebraM(run)

  final case class Scattered[M[_], F[_], A, S](
      coalgebra: GCoalgebraM[M, F, A, S],
      scatter: Scatter[F, A, S]
  )
}

// instances

private[droste] sealed trait GAlgebraInstances {

  implicit def drosteArrowForGAlgebra[F[_]: Comonad]: Arrow[GAlgebra[F, *, *]] =
    new GAlgebraArrow
}

private[droste] class GAlgebraArrow[F[_]: Comonad]
    extends Arrow[GAlgebra[F, *, *]] {
  def lift[A, B](f: A => B): GAlgebra[F, A, B] =
    GAlgebra(fa => f(fa.extract))
  def compose[A, B, C](
      f: GAlgebra[F, B, C],
      g: GAlgebra[F, A, B]
  ): GAlgebra[F, A, C] =
    f compose g
  override def andThen[A, B, C](
      f: GAlgebra[F, A, B],
      g: GAlgebra[F, B, C]
  ): GAlgebra[F, A, C] =
    f andThen g
  def first[A, B, C](f: GAlgebra[F, A, B]): GAlgebra[F, (A, C), (B, C)] =
    GAlgebra(fac => (f.run(fac.map(_._1)), fac.map(_._2).extract))
}

private[droste] sealed trait GCoalgebraInstances {
  implicit def drosteArrowForGCoalgebra[F[_]: Monad]: Arrow[
    GCoalgebra[F, *, *]
  ] =
    new GCoalgebraArrow
}

private[droste] class GCoalgebraArrow[F[_]: Monad]
    extends Arrow[GCoalgebra[F, *, *]] {
  def lift[A, B](f: A => B): GCoalgebra[F, A, B] =
    GCoalgebra(a => f(a).pure[F])
  def compose[A, B, C](
      f: GCoalgebra[F, B, C],
      g: GCoalgebra[F, A, B]
  ): GCoalgebra[F, A, C] =
    f compose g
  override def andThen[A, B, C](
      f: GCoalgebra[F, A, B],
      g: GCoalgebra[F, B, C]
  ): GCoalgebra[F, A, C] =
    f andThen g
  def first[A, B, C](fa: GCoalgebra[F, A, B]): GCoalgebra[F, (A, C), (B, C)] =
    GCoalgebra(ac => fa.run(ac._1).fproduct(_ => ac._2))
}
```

Key operations on `GAlgebra`/`GCoalgebra` usable regardless of which named scheme you're building:

- `.zip` — fuse two algebras (or two coalgebras) operating on the same functor layer into one that produces/consumes a tuple, so two independent recursive computations run in a single pass.
- `.gather` / `.scatter` — attach a `Gather`/`Scatter` strategy to turn a plain algebra/coalgebra into the `Gathered`/`Scattered` wrapper consumed by `scheme.ghylo`/`scheme.gcata`/`scheme.gana`.
- `.lift[M]` — turn a pure algebra/coalgebra into its monadic (`AlgebraM`/`CoalgebraM`) counterpart via `Applicative[M].pure`.
- `.compose` / `.andThen` — Kleisli/Cokleisli-style composition of algebras (via `CoflatMap[F]`) and coalgebras (via `FlatMap[F]`).
- `.toCokleisli` / `.toKleisli` — interop with `cats.data.Cokleisli`/`Kleisli`.
