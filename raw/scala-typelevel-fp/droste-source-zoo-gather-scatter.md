# Droste Source: `zoo.scala` + `gather.scala` + `scatter.scala` (the named morphism zoo)

> Source: https://github.com/higherkindness/droste (main branch, `modules/core/src/main/scala/higherkindness/droste/zoo.scala`, `gather.scala`, `scatter.scala`)
> Collected: 2026-09-12
> Published: Unknown

## `zoo.scala` — every named scheme beyond cata/ana/hylo: apo, para, histo, futu, chrono, dyna, prepro, postpro, zygo

```scala
package higherkindness.droste

import cats.~>
import cats.Functor
import cats.Traverse
import cats.Monad
import cats.free.Yoneda
import cats.syntax.functor._
import higherkindness.droste.data.Attr
import higherkindness.droste.data.Coattr
import higherkindness.droste.data.prelude._

private[droste] trait Zoo {

  /** A variation of an anamorphism that lets you terminate any point of the
    * recursion using a value of the original input type.
    *
    * One use case is to return cached/precomputed results during an unfold.
    *
    * @group unfolds
    */
  def apo[F[_]: Functor, A, R](
      coalgebra: RCoalgebra[R, F, A]
  )(implicit embed: Embed[F, R]): A => R =
    kernel.hyloC(
      embed.algebra.run.compose((frr: F[(R Either R)]) => frr.map(_.merge)),
      coalgebra.run
    )

  /** A monadic version of an apomorphism.
    *
    * @group unfolds
    */
  def apoM[M[_]: Monad, F[_]: Traverse, A, R](
      coalgebraM: RCoalgebraM[R, M, F, A]
  )(implicit embed: Embed[F, R]): A => M[R] =
    kernel.hyloMC(
      embed.algebra
        .lift[M]
        .run
        .compose((frr: F[(R Either R)]) => frr.map(_.merge)),
      coalgebraM.run
    )

  /** A variation of a catamorphism that gives you access to the input value at
    * every point in the computation.
    *
    * A paramorphism "eats its argument and keeps it too."
    *
    * This means each step has access to both the computed result value as well
    * as the original value.
    *
    * @group folds
    */
  def para[F[_]: Functor, R, B](
      algebra: RAlgebra[R, F, B]
  )(implicit project: Project[F, R]): R => B =
    kernel.hyloC(algebra.run, project.coalgebra.run.andThen(_.map(r => (r, r))))

  /** A monadic version of a paramorphism.
    *
    * @group folds
    */
  def paraM[M[_]: Monad, F[_]: Traverse, R, B](
      algebraM: RAlgebraM[R, M, F, B]
  )(implicit project: Project[F, R]): R => M[B] =
    kernel.hyloMC(
      algebraM.run,
      project.coalgebra.lift[M].run.andThen(_.map(_.map(r => (r, r))))
    )

  /** Histomorphism
    *
    * @group folds
    */
  def histo[F[_]: Functor, R, B](
      algebra: CVAlgebra[F, B]
  )(implicit project: Project[F, R]): R => B =
    kernel.hylo[F, R, Attr[F, B]](
      fb => Attr(algebra(fb), fb),
      project.coalgebra.run
    ) andThen (_.head)

  /** Futumorphism
    *
    * @group unfolds
    */
  def futu[F[_]: Functor, A, R](
      coalgebra: CVCoalgebra[F, A]
  )(implicit embed: Embed[F, R]): A => R =
    kernel.hylo[F, Coattr[F, A], R](
      embed.algebra.run,
      _.fold(coalgebra.run, identity)
    ) compose (Coattr.pure(_))

  /** A fusion refold of a futumorphism followed by a histomorphism
    *
    * @group refolds
    */
  def chrono[F[_]: Functor, A, B](
      algebra: CVAlgebra[F, B],
      coalgebra: CVCoalgebra[F, A]
  ): A => B =
    kernel.hylo[F, Coattr[F, A], Attr[F, B]](
      fb => Attr(algebra(fb), fb),
      _.fold(coalgebra.run, identity)
    ) andThen (_.head) compose (Coattr.pure(_))

  /** A fusion refold of an anamorphism followed by a histomorphism
    *
    * @group refolds
    */
  def dyna[F[_]: Functor, A, B](
      algebra: CVAlgebra[F, B],
      coalgebra: Coalgebra[F, A]
  ): A => B =
    kernel.hylo[F, A, Attr[F, B]](
      fb => Attr(algebra(fb), fb),
      coalgebra.run
    ) andThen (_.head)

  /** A variation of a catamorphism that applies a natural transformation before
    * its algebra.
    *
    * This allows one to preprocess the input structure.
    *
    * @group folds
    */
  def prepro[F[_]: Functor, R, B](
      natTrans: F ~> F,
      algebra: Algebra[F, B]
  )(implicit project: Project[F, R]): R => B =
    kernel.hylo[Yoneda[F, *], R, B](
      yfb => algebra.run(yfb.mapK(natTrans).run),
      project.coalgebra.run.andThen(Yoneda.apply[F, R])
    )

  /** A variation of an anamorphism that applies a natural transformation after
    * its coalgebra.
    *
    * This allows one to postprocess the output structure.
    *
    * @group unfolds
    */
  def postpro[F[_]: Functor, A, R](
      coalgebra: Coalgebra[F, A],
      natTrans: F ~> F
  )(implicit embed: Embed[F, R]): A => R =
    kernel.hylo[Yoneda[F, *], A, R](
      yfb => embed.algebra.run(yfb.run),
      coalgebra.run.andThen(fa => Yoneda.apply[F, A](fa).mapK(natTrans))
    )

  /** A catamorphism built from two semi-mutually recursive functions.
    *
    * This allows the second algebra to depend on the result of the first one.
    *
    * @group folds
    */
  def zygo[F[_]: Functor, R, A, B](
      algebra: Algebra[F, A],
      ralgebra: RAlgebra[A, F, B]
  )(implicit project: Project[F, R]): R => B =
    kernel.hylo[F, R, (A, B)](
      fab => (algebra.run(fab.map(_._1)), ralgebra.run(fab)),
      project.coalgebra.run
    ) andThen (_._2)
}
```

Note: droste's `zoo` does **not** define separate `mutu` (mutumorphism) or `elgot`/`coelgot` combinators as named functions — `zygo` (two semi-mutually-recursive algebras fused into one pass) plays the role a mutumorphism would, and the CHANGELOG (`https://raw.githubusercontent.com/higherkindness/droste/main/CHANGELOG.md`) confirms the scheme set was built up incrementally: apomorphism & paramorphism (PR #5), futu/histo/chrono (PR #29), `scheme.dyna` (PR #34), pre/postpromorphisms (PR #61), zygomorphism (PR #88) — with no `mutu`/`elgot` PR ever merged. The generalized `scheme.ghylo` plus `Gather`/`Scatter` gives the same expressive power as an Elgot-style combinator without a dedicated named entry point.

## `gather.scala` — the "fold side" strategies plugged into `ghylo`/`gcata`

```scala
package higherkindness.droste

import cats.Functor
import cats.syntax.functor._
import higherkindness.droste.data.Attr

object Gather {

  def cata[F[_], A]: Gather[F, A, A] =
    (a, _) => a

  def zygo[F[_]: Functor, A, B](algebra: Algebra[F, B]): Gather[F, (B, A), A] =
    (a, fa) => (algebra(fa.map(_._1)), a)

  def para[F[_]: Functor, A, B](implicit
      embed: Embed[F, B]
  ): Gather[F, (B, A), A] =
    zygo(embed.algebra)

  def histo[F[_], A]: Gather[F, Attr[F, A], A] =
    Attr(_, _)

  def zip[F[_]: Functor, Ax, Ay, Sx, Sy](
      x: Gather[F, Sx, Ax],
      y: Gather[F, Sy, Ay]
  ): Gather[F, (Sx, Sy), (Ax, Ay)] =
    (a, fs) => (x(a._1, fs.map(_._1)), y(a._2, fs.map(_._2)))

}
```

`Gather[F, S, A] = (A, F[S]) => S` describes how to combine a freshly-computed algebra result `A` with the `F[S]` of not-yet-folded children into the "carrier" `S` that gets threaded through the fold. `Gather.cata` discards the children entirely (`S = A`, plain fold). `Gather.zygo` pairs the result with an auxiliary `Algebra[F, B]` computed simultaneously. `Gather.para` is `zygo` specialized so the auxiliary computation is just re-embedding the original structure (giving access to the untouched child). `Gather.histo` builds an `Attr` (the annotated-cofree carrier), which is why a histomorphism sees the *entire* history of computed results, not just the immediate child.

## `scatter.scala` — the "unfold side" strategies plugged into `ghylo`/`gana`

```scala
package higherkindness.droste

import cats.Functor
import cats.syntax.functor._

object Scatter {

  def ana[F[_], A]: Scatter[F, A, A] =
    Left(_)

  def gapo[F[_]: Functor, A, B](
      coalgebra: Coalgebra[F, B]
  ): Scatter[F, A, Either[B, A]] = {
    case Left(b)  => Right(coalgebra(b).map(Left(_)))
    case Right(a) => Left(a)
  }

  def apo[F[_]: Functor, A, B](implicit
      project: Project[F, B]
  ): Scatter[F, A, Either[B, A]] =
    gapo(project.coalgebra)
}
```

`Scatter[F, A, S] = S => Either[A, F[S]]` describes how to decide, at a given carrier value `S`, whether to short-circuit with a final `Left[A]` (feeding that back into the coalgebra's continuation) or keep going by returning `Right[F[S]]` (one more layer to unfold). `Scatter.ana` always continues (`S = A`, plain unfold — never short-circuits). `Scatter.apo`/`Scatter.gapo` let a branch terminate early by embedding an already-known/precomputed structure `B`, which is exactly the apomorphism's "eat your input and keep a shortcut" behavior on the unfold side.
