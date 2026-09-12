# Droste Macro Derivation: `@deriveFixedPoint`/`@deriveTraverse` (Scala 2) and `derives Functor/Foldable/Traverse` (Scala 3)

> Source: https://github.com/higherkindness/droste (main branch, `modules/macros/src/main/scala-2/higherkindness/droste/macros/annotations.scala`; `modules/tests/src/test/scala-2/higherkindness/droste/examples/deriveFixedPoint.scala`; `modules/tests/src/test/scala-2/higherkindness/droste/examples/deriveTraverse.scala`; `modules/macros/src/test/scala-3/higherkindness/droste/derivation/DerivedTests.scala`)
> Collected: 2026-09-13
> Published: Unknown

## Scala 2: the annotation declarations

```scala
package higherkindness.droste.macros

import scala.annotation.StaticAnnotation
import scala.annotation.compileTimeOnly
import impl.Macros

@compileTimeOnly("enable macro paradise to expand macro annotations")
class deriveFixedPoint extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Macros.deriveFixedPoint
}

@compileTimeOnly("enable macro paradise to expand macro annotations")
class deriveTraverse extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Macros.deriveTraverse
}
```

## `@deriveFixedPoint`: generate the pattern functor from a plain recursive ADT

```scala
package higherkindness.droste
package examples

import org.scalacheck.Properties
import org.scalacheck.Prop._
import higherkindness.droste.macros.deriveFixedPoint

@deriveFixedPoint sealed trait RecursiveExpr
object RecursiveExpr {
  final case class Dummy()

  final case class Const(value: BigDecimal)                extends RecursiveExpr
  final case class Add(x: RecursiveExpr, y: RecursiveExpr) extends RecursiveExpr
  final case class AddList(list: List[RecursiveExpr])      extends RecursiveExpr
}

final class RecursiveExprChecks extends Properties("deriveFixedPoint") {
  import RecursiveExpr._
  import RecursiveExpr.fixedpoint._

  val evaluateAlgebra: Algebra[RecursiveExprF, BigDecimal] = Algebra {
    case ConstF(v)   => v
    case AddF(x, y)  => x + y
    case AddListF(l) => l.reduce(_ + _)
  }

  val evaluate: RecursiveExpr => BigDecimal = scheme.cata(evaluateAlgebra)

  property("1") = evaluate(Const(1)) ?= 1

  property("1 + 1") = evaluate(Add(Const(1), Const(1))) ?= 2

  property("1 + 2 + 5") = evaluate(Add(Add(Const(1), Const(2)), Const(5))) ?= 8

  property("1 + 2 + 3 + 4 + 5") = evaluate(
    AddList(List(Const(1), Const(2), Const(3), Const(4), Const(5)))
  ) ?= 15
}
```

Annotating `sealed trait RecursiveExpr` with `@deriveFixedPoint` generates, inside `RecursiveExpr.fixedpoint`, the pattern functor `RecursiveExprF[A]` with one constructor per case class (`ConstF`, `AddF`, `AddListF`) — every recursive field replaced by the type parameter `A` — plus the `Basis[RecursiveExprF, RecursiveExpr]` wiring, so `scheme.cata` works directly on `RecursiveExpr` with no hand-written pattern functor or `Basis` instance at all. This is the macro-driven alternative to the hand-written `ExprF`/`Basis.Default` pattern shown in the `cata` worked example.

## `@deriveTraverse`: derive `Traverse` for an existing pattern functor

```scala
package higherkindness.droste
package examples

import cats.syntax.traverse._
import org.scalacheck.Properties
import org.scalacheck.Prop._
import higherkindness.droste.data._
import higherkindness.droste.macros.deriveTraverse

@deriveTraverse sealed trait ExprDerivingTraverse[A]
object ExprDerivingTraverse {
  @deriveTraverse final case class Box[A](name: String, a: A)
  final case class Dummy()

  final case class Const[A](value: BigDecimal) extends ExprDerivingTraverse[A]
  final case class Add[A](x: Box[A], y: A)     extends ExprDerivingTraverse[A]
  final case class AddList[A](list: List[Box[Option[A]]])
      extends ExprDerivingTraverse[A]
}

final class DeriveTraverseChecks extends Properties("deriveTraverse") {
  import ExprDerivingTraverse._

  val summingAlgebraM: AlgebraM[Option, ExprDerivingTraverse, BigDecimal] =
    AlgebraM {
      case Const(value)                           => Some(value)
      case Add(ExprDerivingTraverse.Box(_, x), y) => Some(x + y)
      case AddList(list) => list.map(_.a).sequence.map(_.reduce(_ + _))
    }

  val evaluate: Fix[ExprDerivingTraverse] => Option[BigDecimal] =
    scheme.cataM(summingAlgebraM)

  property("1") = evaluate(Fix(Const(1))) ?= Some(1)

  property("1 + 1") =
    evaluate(Fix(Add(Box("qwer", Fix(Const(1))), Fix(Const(1))))) ?= Some(2)

  property("1 + 2 + 5") = evaluate(
    Fix(
      Add(
        Box("qwer", Fix(Add(Box("qwer", Fix(Const(1))), Fix(Const(2))))),
        Fix(Const(5))
      )
    )
  ) ?= Some(8)

  property("1 + 2 + 3 + 4 + 5") = evaluate(
    Fix(
      AddList(
        List(
          Box("asdf", Option(Fix(Const(1)))),
          Box("asdf", Option(Fix(Const(2)))),
          Box("asdf", Option(Fix(Const(3)))),
          Box("asdf", Option(Fix(Const(4)))),
          Box("asdf", Option(Fix(Const(5))))
        )
      )
    )
  ) ?= Some(15)

}
```

`@deriveTraverse` derives `Traverse[ExprDerivingTraverse]` even though `ExprDerivingTraverse`'s recursive positions are nested inside another annotated type (`Box[A]`, itself carrying a derived `Traverse`) and inside `List`/`Option` — showing the derivation handles nested/wrapped recursive positions, not just a direct `A` field, which is needed for `scheme.cataM` (a monadic fold) to traverse the structure correctly.

## Scala 3: `derives Functor / Foldable / Traverse`

Scala 3 replaces the macro-annotation approach with the standard `derives` clause, backed by droste's shapeless3-based derivation machinery (`modules/macros/src/main/scala-3/higherkindness/droste/derivation/Derived.scala`, itself a credited port of the `kittens` project's derivation core):

```scala
package higherkindness.droste.derivation

import cats.{Foldable, Functor, Traverse}

class FoldableTests {

  case class Box[A](value: A) derives Foldable

  sealed trait Maybe[+A] derives Foldable
  case object Nufin extends Maybe[Nothing]
  case class Just[A](value: A) extends Maybe[A]

  sealed trait CList[A] derives Foldable
  case object CNil extends CList[Nothing]
  case class CCons[A](head: A, tail: CList[A]) extends CList[A]
}

class FunctorTests {

  case class Box[A](value: A) derives Functor

  sealed trait Maybe[+A] derives Functor
  case object Nufin extends Maybe[Nothing]
  case class Just[A](value: A) extends Maybe[A]

  sealed trait CList[A] derives Functor
  case object CNil extends CList[Nothing]
  case class CCons[A](head: A, tail: CList[A]) extends CList[A]

}

class TraverseTests {

  case class Box[A](value: A) derives Traverse

  sealed trait Maybe[+A] derives Traverse
  case object Nufin extends Maybe[Nothing]
  case class Just[A](value: A) extends Maybe[A]

  sealed trait CList[A] derives Traverse
  case object CNil extends CList[Nothing]
  case class CCons[A](head: A, tail: CList[A]) extends CList[A]
}
```

On Scala 3, a pattern functor gets its `Functor`/`Foldable`/`Traverse` instance for free by adding `derives Functor` (etc.) to its definition — no separate macro-annotation import needed, and it composes with ordinary sealed-trait ADT hierarchies (`Maybe`/`Just`, `CList`/`CNil`/`CCons`) the same way `cats.Show`/`cats.Eq` derivation does elsewhere in the Typelevel ecosystem.
