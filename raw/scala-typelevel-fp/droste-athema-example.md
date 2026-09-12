# Droste's `athema` Module: a Real Symbolic-Math Application (Expr, Evaluate, Differentiate, Simplify)

> Source: https://github.com/higherkindness/droste (main branch, `athema/src/main/scala/higherkindness/athema/expr.scala`, `athema/src/main/scala/higherkindness/athema/algebras.scala`, `athema/src/main/scala/higherkindness/athema/package.scala`)
> Collected: 2026-09-13
> Published: Unknown

Droste's README describes `athema` as "a math expression parser/processor, as a more extensive example of recursion schemes." It builds a small computer-algebra system over `Fix[Expr[V, *]]` and demonstrates `cataM`, `para` (via `gcata` + `Gather.para`), and a `Trans`-based simplification pass all applied to the same pattern functor.

## `expr.scala` — the pattern functor and its `Fix`-based expression type

```scala
package higherkindness.athema

import cats.Applicative
import cats.Traverse
import cats.syntax.all._
import higherkindness.droste.data._
import higherkindness.droste.util.DefaultTraverse

sealed trait Expr[V, A]
final case class Var[V, A](name: String) extends Expr[V, A]
final case class Const[V, A](value: V)   extends Expr[V, A]
final case class Neg[V, A](x: A)         extends Expr[V, A]
final case class Add[V, A](x: A, y: A)   extends Expr[V, A]
final case class Sub[V, A](x: A, y: A)   extends Expr[V, A]
final case class Prod[V, A](x: A, y: A)  extends Expr[V, A]
final case class Div[V, A](x: A, y: A)   extends Expr[V, A]

object Expr extends ExprInstances {
  type Fixed[V] = Fix[Expr[V, *]]
}

object Var {
  def fix[V](name: String): Expr.Fixed[V] = Fix(Var(name))
}

object Const {
  def fix[V](value: V): Expr.Fixed[V] = Fix(Const(value))
}

object Neg {
  def apply[V](x: Expr.Fixed[V]): Expr.Fixed[V] = Fix(Neg(x))
}

object Add {
  def apply[V](x: Expr.Fixed[V], y: Expr.Fixed[V]): Expr.Fixed[V] =
    Fix(Add(x, y))
}

object Sub {
  def apply[V](x: Expr.Fixed[V], y: Expr.Fixed[V]): Expr.Fixed[V] =
    Fix(Sub(x, y))
}

object Prod {
  def apply[V](x: Expr.Fixed[V], y: Expr.Fixed[V]): Expr.Fixed[V] =
    Fix(Prod(x, y))
}

object Div {
  def apply[V](x: Expr.Fixed[V], y: Expr.Fixed[V]): Expr.Fixed[V] =
    Fix(Div(x, y))
}

private[athema] sealed trait ExprInstances {
  implicit def traverseExpr[V]: Traverse[Expr[V, *]] =
    new DefaultTraverse[Expr[V, *]] {
      def traverse[G[_]: Applicative, A, B](
          fa: Expr[V, A]
      )(f: A => G[B]): G[Expr[V, B]] = fa match {
        case v: Var[V, A]   => (v.asInstanceOf[Expr[V, B]]).pure[G]
        case c: Const[V, A] => (c.asInstanceOf[Expr[V, B]]).pure[G]
        case e: Neg[V, A]   => f(e.x) map (Neg(_))
        case e: Add[V, A]   => (f(e.x), f(e.y)) mapN (Add(_, _))
        case e: Sub[V, A]   => (f(e.x), f(e.y)) mapN (Sub(_, _))
        case e: Prod[V, A]  => (f(e.x), f(e.y)) mapN (Prod(_, _))
        case e: Div[V, A]   => (f(e.x), f(e.y)) mapN (Div(_, _))
      }
    }
}
```

## `algebras.scala` — `Evaluate` (cataM), `Differentiate` (para via gcata), `Simplify` (Trans)

```scala
package higherkindness.athema

import algebra.ring.Field
import algebra.ring.Ring
import cats.syntax.all._
import higherkindness.droste._
import higherkindness.droste.syntax.all._

object Evaluate {
  def evaluate[V: Field]: Expr.Fixed[V] => Either[String, V] =
    scheme.cataM(algebraM(Map.empty))

  def algebraM[V](
      variables: Map[String, V]
  )(implicit V: Field[V]): AlgebraM[Either[String, *], Expr[V, *], V] =
    AlgebraM {
      case Var(name)  => variables.get(name).toRight(s"unknown variable: $name")
      case Const(v)   => v.asRight
      case Neg(x)     => V.negate(x).asRight
      case Add(x, y)  => V.plus(x, y).asRight
      case Sub(x, y)  => V.plus(x, V.negate(y)).asRight
      case Prod(x, y) => V.times(x, y).asRight
      case Div(x, y)  => V.div(x, y).asRight
    }
}

object Differentiate {

  def differentiate[V: Ring](wrt: String): Expr.Fixed[V] => Expr.Fixed[V] =
    scheme.gcata(algebra[V](wrt))(Gather.para)

  def algebra[V](
      wrt: String
  )(implicit V: Ring[V]): RAlgebra[Expr.Fixed[V], Expr[V, *], Expr.Fixed[V]] =
    RAlgebra {
      case Var(`wrt`)             => Const(V.one).fix
      case _: Var[_, _]           => Const(V.zero).fix
      case _: Const[_, _]         => Const(V.zero).fix
      case Neg((_, xx))           => Neg(xx)
      case Add((_, xx), (_, yy))  => Add(xx, yy)
      case Sub((_, xx), (_, yy))  => Sub(xx, yy)
      case Prod((x, xx), (y, yy)) => Add(Prod(x, yy), Prod(xx, y))
      case Div((x, xx), (y, yy)) =>
        Div(Sub(Prod(xx, y), Prod(x, yy)), Prod(y, y))
    }
}

object Simplify {
  def simplify[V: Field]: Expr.Fixed[V] => Expr.Fixed[V] =
    scheme.cata(algebra[V])

  def trans[V](implicit
      V: Field[V]
  ): Trans[Expr[V, *], Expr[V, *], Expr.Fixed[V]] =
    Trans { fa =>
      val Zero = V.zero
      val One  = V.one
      val Two  = V.plus(V.one, V.one)

      fa match {

        case Prod(Const(Zero), _) => Const(Zero)
        case Prod(_, Const(Zero)) => Const(Zero)
        case Prod(Const(One), v)  => v.unfix
        case Prod(v, Const(One))  => v.unfix

        case Sub(Const(Zero), v) => Neg(v)

        case Add(Const(Zero), v) => v.unfix
        case Add(v, Const(Zero)) => v.unfix
        case Add(x, y) if x == y => Prod(Const(Two).fix, x)
        case Add(x, Prod(Const(n: V @unchecked), y)) if x == y =>
          Prod(Const(V.plus(n, V.one)).fix, x)

        case other => other
      }
    }

  def algebra[V](implicit V: Field[V]): Algebra[Expr[V, *], Expr.Fixed[V]] =
    trans.algebra

}
```

Three schemes solving three different problems over the same `Expr[V, *]` pattern functor:

- **`Evaluate.evaluate`** is a plain `cataM` over `Either[String, *]` — the algebra can fail (`unknown variable`), so it returns `Either` instead of a bare value, folding bottom-up to a single numeric result or the first error encountered.
- **`Differentiate.differentiate`** is a **paramorphism**, but reached through the generalized `scheme.gcata(algebra)(Gather.para)` form rather than `scheme.zoo.para` directly — `RAlgebra[Expr.Fixed[V], Expr[V, *], Expr.Fixed[V]]`'s `case Add((_, xx), (_, yy)) => Add(xx, yy)` pattern shows each child arriving as a `(originalSubexpr, derivative)` pair, exactly the paramorphism shape, and the product rule (`Prod((x, xx), (y, yy)) => Add(Prod(x, yy), Prod(xx, y))`) needs both the original operand `x`/`y` *and* its derivative `xx`/`yy` simultaneously — the textbook reason a paramorphism (not a plain catamorphism) is required here.
- **`Simplify.simplify`** wraps an ordinary rewrite-rule pattern match (`Prod(Const(Zero), _) => Const(Zero)`, `Add(x, y) if x == y => Prod(Const(Two).fix, x)`, ...) as a `Trans[Expr[V,*], Expr[V,*], Expr.Fixed[V]]` (endofunctor rewrite, same pattern functor in and out), then exposes it as a plain `Algebra` via `.algebra` for `scheme.cata` — showing `Trans` used not to convert between two *different* pattern functors (as in the `demo.scala` `List`↔`NonEmptyList` example) but to organize a same-functor local-rewrite-rule algebra.
