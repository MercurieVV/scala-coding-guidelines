# SIP-33 - Priority-based infix type precedence

> Source: https://docs.scala-lang.org/sips/priority-based-infix-type-precedence.html
> Collected: 2026-09-13
> Published: 2017-02-07 (initial draft; updated through 2017-11-29 per the SIP's own history table)

By Oron Port. Scala Improvement Process (SIP) document, "Language" category.

## History (as given in the document)

| Date | Version |
|---|---|
| Feb 7th 2017 | Initial Draft |
| Feb 9th 2017 | Updates from feedback |
| Feb 10th 2017 | Updates from feedback |
| Aug 8th 2017 | Numbered SIP, improve view, fixed example, and added related issues |
| Oct 20th 2017 | Added implementation link |
| Oct 25th 2017 | Moved prefix types to another SIP, changed title and PR |
| Nov 29th 2017 | Updated SIP according to feedback in the PR |

## Introduction

"Currently scala allows symbol operators (`-`, `*`, `~~>`, etc.) for both type names and definition names. Unfortunately, there is a 'surprise' element since the two differ in behavior. While infix types are 'mostly' left-associative, the expression operation precedence is determined by the operator's first character (e.g., `/` is precedent to `+`)."

## Infix expression precedence vs. infix type precedence example

```scala
object InfixExpressionPrecedence {
  case class Nummy(expand: String) {
    def +(that: Nummy): Nummy = Nummy(s"Plus[$this,$that]")
    def /(that: Nummy): Nummy = Nummy(s"Div[$this,$that]")
  }
  object N1 extends Nummy("N1")
  object N2 extends Nummy("N2")
  object N3 extends Nummy("N3")
  object N4 extends Nummy("N4")
  //Both expand to Plus[Plus[N1,Div[N2,N3]],N4]
  assert((N1 + N2 / N3 + N4).expand == (N1 + (N2 / N3) + N4).expand)
}
object InfixTypePrecedence {
  trait Plus[N1, N2]
  trait Div[N1, N2]
  type +[N1, N2] = Plus[N1, N2]
  type /[N1, N2] = Div[N1, N2]
  trait N1
  trait N2
  trait N3
  trait N4
  //Error!
  //Left expands to Plus[Div[Plus[N1,N2],N3],N4] (Surprising)
  //Right expands to Plus[Plus[N1,Div[N2,N3]],N4]
  implicitly[(N1 + N2 / N3 + N4) =:= (N1 + (N2 / N3) + N4)]
}
```

## Motivation

"It is easier to reason about the language when mathematical and logical operations for both terms and types are expressed the same."

### Dotty infix type similarity

"Dotty infix type associativity and precedence seem to act the same as expressions. No documentation available to prove this, but the infix example above works perfectly in dotty. Dotty has no prefix types, same as Scalac."

### Singleton-ops library example

```scala
import singleton.ops._

val four1 : 4 = implicitly[2 + 2]
val four2 : 2 + 2 = 4
val four3 : 1 + 3 = implicitly[2 + 2]

class MyVec[L] {
  def doubleSize = new MyVec[2 * L]
  def nSize[N] = new MyVec[N * L]
}
object MyVec {
  implicit def apply[L](implicit check : Require[L > 0]) : MyVec[L] = new MyVec[L]()
}
val myVec : MyVec[10] = MyVec[4 + 1].doubleSize
val myBadVec = MyVec[-1] //fails compilation, as required
```

"We currently loose some of the intuitive appeal due to the precedence issue:

```scala
val works : 1 + (2 * 3) + 4 = 11
val fails : 1 + 2 * 3 + 4 = 11 //left associative:(((1+2)*3)+4))) = 13
```"

## Proposal

"Make infix types conform to the same precedence and associativity traits as term operations."

## Implementation notes

"Changing infix type associativity and precedence affects code that uses type operations and conforms to the current specification. ... Note: changing the infix precedence didn't fail any scalac test."
